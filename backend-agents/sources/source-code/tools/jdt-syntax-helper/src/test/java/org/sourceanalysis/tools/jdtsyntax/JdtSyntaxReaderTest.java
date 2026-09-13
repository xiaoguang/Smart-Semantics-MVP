package org.sourceanalysis.tools.jdtsyntax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtSyntaxReaderTest {

  @Test
  void describesCompleteMethodParametersCallsControlsAndExitsFromOriginalSource() {
    String source =
        "package example;\r\n"
            + "import java.util.List;\r\n"
            + "class RegistrationService {\r\n"
            + "  @Transactional\r\n"
            + "  User register(@Valid String name, String... roles) {\r\n"
            + "    if (name.isBlank()) { throw new IllegalArgumentException(name); }\r\n"
            + "    repository.save(name);\r\n"
            + "    repository.save(name);\r\n"
            + "    return user;\r\n"
            + "  }\r\n"
            + "}\r\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader().describe(request("req-1", "source:registration", "17", source));

    assertThat(response.protocolVersion()).isEqualTo(JdtSyntaxProtocol.VERSION);
    assertThat(response.requestId()).isEqualTo("req-1");
    assertThat(response.sourceSha256()).isEqualTo(sha256(source));
    assertThat(response.packageName()).isEqualTo("example");
    assertThat(response.imports())
        .extracting(JdtSyntaxProtocol.ImportView::text)
        .containsExactly("import java.util.List;");

    JdtSyntaxProtocol.Declaration method =
        response.declarations().stream()
            .filter(item -> item.kind().equals("METHOD") && item.name().equals("register"))
            .findFirst()
            .orElseThrow();
    assertThat(method.sourceText()).startsWith("@Transactional").contains("return user;");
    assertThat(method.navigationRange()).isNotNull();
    assertThat(
            source.substring(
                method.navigationRange().startOffsetUtf16(),
                method.navigationRange().endOffsetUtf16()))
        .isEqualTo("register");
    assertThat(method.bodyPresent()).isTrue();
    assertThat(method.parameters()).hasSize(2);
    assertThat(method.parameters().get(0).annotationTexts()).containsExactly("@Valid");
    assertThat(method.parameters().get(1).varArgs()).isTrue();

    assertThat(response.callSites())
        .filteredOn(call -> call.expression().equals("repository.save(name)"))
        .hasSize(2);
    assertThat(response.controls())
        .anyMatch(
            control ->
                control.kind().equals("IF") && control.expression().contains("name.isBlank"));
    assertThat(response.exits())
        .extracting(JdtSyntaxProtocol.ExitView::kind)
        .contains("THROW", "RETURN");
    assertThat(method.sourceRange().lengthUtf16()).isEqualTo(method.sourceText().length());
  }

  @Test
  void keepsNestedLambdaCallsOutOfTheEnclosingMethodAndDescribesReferences() {
    String source =
        "class Example {\n"
            + "  void run() {\n"
            + "    Runnable r = () -> service.deferred();\n"
            + "    items.forEach(service::accept);\n"
            + "    factory = Widget::new;\n"
            + "  }\n"
            + "}\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader().describe(request("req-2", "source:lambda", "17", source));

    JdtSyntaxProtocol.Declaration lambda =
        response.declarations().stream()
            .filter(item -> item.kind().equals("LAMBDA"))
            .findFirst()
            .orElseThrow();
    assertThat(response.callSites())
        .anySatisfy(
            call -> {
              if (call.expression().equals("service.deferred()")) {
                assertThat(call.ownerDeclarationId()).isEqualTo(lambda.localId());
                assertThat(call.deferred()).isTrue();
              }
            });
    assertThat(response.callSites())
        .extracting(JdtSyntaxProtocol.CallSiteView::kind)
        .contains("METHOD_REFERENCE", "CONSTRUCTOR_REFERENCE");
  }

  @Test
  void rangesUseUtf16OffsetsWithoutNormalizingCrLfOrSupplementaryCharacters() {
    String source = "class 文档 {\r\n  String value() { return \"😀\"; }\r\n}\r\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader().describe(request("req-3", "source:utf16", "17", source));

    JdtSyntaxProtocol.Declaration method =
        response.declarations().stream()
            .filter(item -> item.kind().equals("METHOD"))
            .findFirst()
            .orElseThrow();
    assertThat(
            source.substring(
                method.sourceRange().startOffsetUtf16(), method.sourceRange().endOffsetUtf16()))
        .isEqualTo(method.sourceText());
    assertThat(method.sourceText()).contains("😀");
    assertThat(method.sourceRange().startLine()).isEqualTo(2);
  }

  @Test
  void describesDeclarationKindsFieldsAndStructuredAnnotationsForSharedDiscovery() {
    String source =
        "package example;\n"
            + "import org.springframework.web.bind.annotation.*;\n"
            + "@RequestMapping(value = \"/users\", method = {})\n"
            + "class UserController {\n"
            + "  @Resource private UserService users;\n"
            + "  @PostMapping(path = \"/register\")\n"
            + "  Object register(@RequestBody User user) { return users.register(user); }\n"
            + "}\n"
            + "interface UserMapper { User find(Long id); }\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader().describe(request("req-catalog", "source:catalog", "17", source));

    assertThat(response.declarations())
        .filteredOn(declaration -> "CLASS".equals(declaration.kind()))
        .extracting(JdtSyntaxProtocol.Declaration::name)
        .containsExactly("UserController");
    assertThat(response.declarations())
        .filteredOn(declaration -> "INTERFACE".equals(declaration.kind()))
        .extracting(JdtSyntaxProtocol.Declaration::name)
        .containsExactly("UserMapper");
    assertThat(response.declarations())
        .filteredOn(declaration -> "FIELD".equals(declaration.kind()))
        .singleElement()
        .satisfies(field -> assertThat(field.sourceText()).contains("UserService users"));

    JdtSyntaxProtocol.AnnotationView requestMapping =
        response.annotations().stream()
            .filter(annotation -> annotation.nameText().equals("RequestMapping"))
            .findFirst()
            .orElseThrow();
    assertThat(requestMapping.ownerDeclarationId()).startsWith("decl:class:");
    assertThat(requestMapping.staticValues().get("value").kind()).isEqualTo("STRING");
    assertThat(requestMapping.staticValues().get("value").value()).isEqualTo("/users");
    assertThat(requestMapping.staticValues().get("method").kind()).isEqualTo("ARRAY");
    assertThat(requestMapping.staticValues().get("method").elements()).isEmpty();
    assertThat(
            source.substring(
                requestMapping.nameSelection().startOffsetUtf16(),
                requestMapping.nameSelection().endOffsetUtf16()))
        .isEqualTo("RequestMapping");

    assertThat(response.annotations())
        .filteredOn(annotation -> annotation.nameText().equals("PostMapping"))
        .singleElement()
        .satisfies(
            mapping -> {
              assertThat(mapping.ownerDeclarationId()).startsWith("decl:method:");
              assertThat(mapping.staticValues().get("path").value()).isEqualTo("/register");
            });
  }

  @Test
  void resolvesWildcardImportedAnnotationIdentityWithJdtBindings(@TempDir Path sourceRoot)
      throws Exception {
    Path annotation = sourceRoot.resolve("framework/HttpPost.java");
    Files.createDirectories(annotation.getParent());
    Files.writeString(annotation, "package framework; public @interface HttpPost {}\n");
    String source =
        "package example;\n"
            + "import framework.*;\n"
            + "class Controller { @HttpPost void register() {} }\n";
    JdtSyntaxProtocol.Request request =
        new JdtSyntaxProtocol.Request(
            JdtSyntaxProtocol.VERSION,
            JdtSyntaxProtocol.DESCRIBE_COMPILATION_UNIT,
            "req-binding",
            "example/Controller.java",
            "17",
            sha256(source),
            java.util.List.of(sourceRoot.toString()),
            java.util.List.of(),
            source);

    JdtSyntaxProtocol.Response response = new JdtSyntaxReader().describe(request);

    assertThat(response.annotations())
        .filteredOn(value -> "HttpPost".equals(value.nameText()))
        .singleElement()
        .satisfies(value -> assertThat(value.qualifiedName()).isEqualTo("framework.HttpPost"));
  }

  @Test
  void doesNotPresentARecoveredLocalAnnotationBindingAsResolvedIdentity() {
    String source =
        "package example;\n"
            + "import org.springframework.web.bind.annotation.RequestMapping;\n"
            + "@RequestMapping(\"/orders\") class OrderController {}\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader()
            .describe(request("req-recovered-binding", "source:orders", "17", source));

    assertThat(response.annotations())
        .filteredOn(value -> "RequestMapping".equals(value.nameText()))
        .singleElement()
        .satisfies(
            value ->
                assertThat(value.qualifiedName())
                    .as("an unresolved import must remain available for catalog-level recovery")
                    .isNull());
  }

  @Test
  void exposesElseAndFinallyBranchesWithoutInventingControlFlow() {
    String source =
        "class Example {\n"
            + "  int load(boolean allowed) {\n"
            + "    try {\n"
            + "      if (allowed) { return repository.load(); }\n"
            + "      else { audit.denied(); return -1; }\n"
            + "    } finally { audit.finished(); }\n"
            + "  }\n"
            + "}\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader().describe(request("req-branches", "source:branches", "17", source));

    assertThat(response.controls())
        .extracting(JdtSyntaxProtocol.ControlView::kind)
        .containsExactly("TRY", "IF", "ELSE", "FINALLY");
    JdtSyntaxProtocol.ControlView elseBranch =
        response.controls().stream()
            .filter(control -> control.kind().equals("ELSE"))
            .findFirst()
            .orElseThrow();
    JdtSyntaxProtocol.ControlView finallyBranch =
        response.controls().stream()
            .filter(control -> control.kind().equals("FINALLY"))
            .findFirst()
            .orElseThrow();
    assertThat(elseBranch.parentControlIndex()).isEqualTo(1);
    assertThat(finallyBranch.parentControlIndex()).isEqualTo(0);
    assertThat(
            source.substring(
                elseBranch.sourceRange().startOffsetUtf16(),
                elseBranch.sourceRange().endOffsetUtf16()))
        .isEqualTo("{ audit.denied(); return -1; }");
  }

  @Test
  void keepsOverloadsConstructorsInitializersAndCompactRecordConstructorsDistinct() {
    String source =
        "class Example {\n"
            + "  static { bootstrap(); }\n"
            + "  Example() { this(1); }\n"
            + "  Example(int count) { configure(count); }\n"
            + "  void run() {}\n"
            + "  void run(String value) {}\n"
            + "}\n"
            + "record Pair(String left, String right) {\n"
            + "  Pair { java.util.Objects.requireNonNull(left); }\n"
            + "}\n";

    JdtSyntaxProtocol.Response response =
        new JdtSyntaxReader()
            .describe(request("req-declarations", "source:declarations", "17", source));

    assertThat(response.declarations())
        .filteredOn(declaration -> "CONSTRUCTOR".equals(declaration.kind()))
        .hasSize(3)
        .allSatisfy(declaration -> assertThat(declaration.bodyPresent()).isTrue());
    assertThat(response.declarations())
        .filteredOn(
            declaration -> "METHOD".equals(declaration.kind()) && "run".equals(declaration.name()))
        .extracting(declaration -> declaration.parameters().size())
        .containsExactly(0, 1);
    assertThat(response.declarations())
        .filteredOn(declaration -> "INITIALIZER".equals(declaration.kind()))
        .singleElement()
        .satisfies(declaration -> assertThat(declaration.navigationRange()).isNull());
    assertThat(response.declarations())
        .filteredOn(declaration -> "LAMBDA".equals(declaration.kind()))
        .allSatisfy(declaration -> assertThat(declaration.navigationRange()).isNull());
    assertThat(response.callSites())
        .extracting(JdtSyntaxProtocol.CallSiteView::kind)
        .contains("THIS_CONSTRUCTOR", "METHOD");
  }

  @Test
  void rejectsWrongFingerprintAndUnsupportedProtocolInsteadOfReturningEmptySyntax() {
    String source = "class Example {}\n";
    JdtSyntaxReader reader = new JdtSyntaxReader();

    assertThatThrownBy(
            () ->
                reader.describe(
                    new JdtSyntaxProtocol.Request(
                        JdtSyntaxProtocol.VERSION,
                        "DESCRIBE_COMPILATION_UNIT",
                        "req-4",
                        "source:bad",
                        "17",
                        "0".repeat(64),
                        java.util.List.of(),
                        java.util.List.of(),
                        source)))
        .isInstanceOf(JdtSyntaxProtocol.ProtocolException.class)
        .hasMessageContaining("fingerprint");
    assertThatThrownBy(
            () ->
                reader.describe(
                    new JdtSyntaxProtocol.Request(
                        "future",
                        "DESCRIBE_COMPILATION_UNIT",
                        "req-5",
                        "source:bad",
                        "17",
                        sha256(source),
                        java.util.List.of(),
                        java.util.List.of(),
                        source)))
        .isInstanceOf(JdtSyntaxProtocol.ProtocolException.class)
        .hasMessageContaining("protocol");
  }

  private static JdtSyntaxProtocol.Request request(
      String requestId, String sourceKey, String languageLevel, String source) {
    return new JdtSyntaxProtocol.Request(
        JdtSyntaxProtocol.VERSION,
        "DESCRIBE_COMPILATION_UNIT",
        requestId,
        sourceKey,
        languageLevel,
        sha256(source),
        java.util.List.of(),
        java.util.List.of(),
        source);
  }

  private static String sha256(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
