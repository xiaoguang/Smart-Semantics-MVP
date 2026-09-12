package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;

/** Read-only local acceptance against the user-approved frozen jshERP source projection. */
class JdtRealSourceCollectionTest {

  private static final Path FIXTURE =
      Path.of(".workspace", "jdtls-source-navigation-feasibility", "projection", "src");
  private static final Path JDT =
      Path.of(".workspace", "jdtls-source-navigation-feasibility", "tools", "selected");
  private static final Path TOOL_JAVA =
      Path.of("/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home");
  private static final Path ACTUAL_OUTPUT = Path.of(".workspace", "jdt-production-navigation");

  @Test
  void jdtCollectsRegistrationAndFinancialImplementationsInOneProjectIndex() throws Exception {
    Assumptions.assumeTrue(Files.isDirectory(FIXTURE));
    Assumptions.assumeTrue(Files.isDirectory(JDT));
    Assumptions.assumeTrue(Files.isExecutable(TOOL_JAVA.resolve("bin/java")));
    Assumptions.assumeTrue(Files.isRegularFile(JdtSyntaxHelperArtifact.locate()));
    VerifiedJavaProject project = frozenProject();
    EffectiveEngineConfiguration configuration =
        new EffectiveEngineConfiguration(
            EffectiveEngineConfiguration.JDT,
            new EffectiveEngineConfiguration.JdtConfiguration(
                JDT.toAbsolutePath(),
                TOOL_JAVA,
                Duration.ofSeconds(90),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)));

    try (JavaCodeSession session = new JdtCodeEngine(configuration).open(project)) {
      JavaDeclarationCatalog.MethodDeclarationView entry =
          session.catalog().methods().stream()
              .filter(method -> method.sourcePath().endsWith("controller/UserController.java"))
              .filter(method -> method.name().equals("registerUser"))
              .findFirst()
              .orElseThrow();

      EntryCodeContext context =
          session.collect(
              new EntrySeed(
                  "entry:jsh-user-register", entry.methodKey(), entry.sourceRange(), "HTTP POST"));

      assertThat(context.methods())
          .as("collected context:%n%s", diagnostic(context))
          .filteredOn(method -> "UserService".equals(method.declaringType()))
          .extracting(EntryCodeContext.MethodCode::name)
          .contains("validateCaptcha", "checkLoginName", "registerUser");
      assertThat(context.methods())
          .filteredOn(
              method ->
                  "UserService".equals(method.declaringType())
                      && "registerUser".equals(method.name()))
          .singleElement()
          .satisfies(
              method ->
                  assertThat(method.source().text())
                      .contains("userMapper.insertSelective(ue)")
                      .contains("tenantMapper.insertSelective(tenant)"));
      assertThat(context.calls())
          .filteredOn(call -> call.callerMethodKey().equals(entry.methodKey()))
          .extracting(EntryCodeContext.CallSite::expression)
          .contains(
              "userService.validateCaptcha(ue.getCode(), ue.getUuid())",
              "userService.checkLoginName(ue)",
              "userService.registerUser(ue,manageRoleId,request)");
      writePacket("registration-context.json", context);

      JavaDeclarationCatalog.MethodDeclarationView financialEntry =
          session.catalog().methods().stream()
              .filter(
                  method -> method.sourcePath().endsWith("controller/AccountHeadController.java"))
              .filter(method -> method.name().equals("getFinancialBillNoByBillId"))
              .findFirst()
              .orElseThrow();
      EntryCodeContext financial =
          session.collect(
              new EntrySeed(
                  "entry:jsh-financial-bill-query",
                  financialEntry.methodKey(),
                  financialEntry.sourceRange(),
                  "HTTP GET"));

      assertThat(financial.methods())
          .as("financial context:%n%s", diagnostic(financial))
          .filteredOn(
              method ->
                  "AccountHeadService".equals(method.declaringType())
                      && "getFinancialBillNoByBillId".equals(method.name()))
          .singleElement()
          .satisfies(
              method ->
                  assertThat(method.source().text())
                      .contains("accountHeadMapperEx.getFinancialBillNoByBillId(billId)"));
      assertThat(financial.methods())
          .filteredOn(
              method ->
                  "AccountHeadMapperEx".equals(method.declaringType())
                      && "getFinancialBillNoByBillId".equals(method.name()))
          .singleElement()
          .satisfies(
              method -> {
                assertThat(method.bodyPresent()).isFalse();
                assertThat(method.parameters())
                    .singleElement()
                    .satisfies(parameter -> assertThat(parameter.name()).isEqualTo("billId"));
              });
      assertThat(financial.calls())
          .filteredOn(
              call ->
                  call.expression().equals("accountHeadService.getFinancialBillNoByBillId(billId)"))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.actualArguments())
                    .extracting(EntryCodeContext.ActualArgument::expression)
                    .containsExactly("billId");
                assertThat(call.targets()).isNotEmpty();
              });
      assertThat(financial.calls())
          .filteredOn(
              call ->
                  call.expression()
                      .equals("accountHeadMapperEx.getFinancialBillNoByBillId(billId)"))
          .singleElement()
          .satisfies(
              call -> {
                assertThat(call.actualArguments())
                    .extracting(EntryCodeContext.ActualArgument::expression)
                    .containsExactly("billId");
                assertThat(call.targets())
                    .anySatisfy(
                        target -> assertThat(target.expansion()).isEqualTo("DECLARATION_ONLY"));
              });
      writePacket("financial-context.json", financial);
    }
  }

  private static VerifiedJavaProject frozenProject() throws IOException {
    List<VerifiedSourceTextDocument> documents;
    try (var paths = Files.walk(FIXTURE)) {
      documents =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .sorted(Comparator.comparing(Path::toString))
              .map(JdtRealSourceCollectionTest::document)
              .toList();
    }
    String seed =
        "8c30ce7861570458920175e200bb2a6442713580:"
            + documents.stream().map(VerifiedSourceTextDocument::path).reduce("", String::concat);
    VerifiedSourceTextSet sourceTexts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest(seed),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", seed),
            reference("source-inventory", seed),
            reference("verified-snapshot", seed),
            controls(seed),
            documents);
    return VerifiedJavaProject.fromVerifiedSourceTextSet(
        sourceTexts, List.of("src/main/java"), List.of(), "17");
  }

  private static VerifiedSourceTextDocument document(Path source) {
    try {
      byte[] bytes = Files.readAllBytes(source);
      String relative = FIXTURE.relativize(source).toString().replace('\\', '/');
      String path = "src/main/java/" + relative;
      return new VerifiedSourceTextDocument(
          new ArtifactId("file:" + digest(path)),
          path,
          "100644",
          "text/x-java-source",
          bytes.length,
          new Sha256Digest(digest(bytes)),
          ImmutableBytes.copyOf(bytes));
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static ArtifactReference reference(String prefix, String seed) {
    return new ArtifactReference(
        new ArtifactId(prefix + ':' + digest(seed + prefix)),
        new Sha256Digest(digest(seed + "-sha")));
  }

  private static ArtifactControls controls(String seed) {
    return new ArtifactControls(
        new Sha256Digest(digest(seed + "-toolchain")),
        new Sha256Digest(digest(seed + "-profile")),
        new Sha256Digest(digest(seed + "-schema")),
        null,
        new ArtifactPolicyRegistryReference(
            new ArtifactId("artifact-policy-registry:" + digest(seed + "-registry")),
            new Sha256Digest(digest(seed + "-registry-sha"))));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void writePacket(String fileName, EntryCodeContext context) throws IOException {
    Files.createDirectories(ACTUAL_OUTPUT);
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(ACTUAL_OUTPUT.resolve(fileName).toFile(), context);
  }

  private static String diagnostic(EntryCodeContext context) {
    String methods =
        context.methods().stream()
            .map(method -> method.declaringType() + "." + method.name())
            .reduce("", (left, right) -> left + "\nmethod=" + right);
    String calls =
        context.calls().stream()
            .map(
                call ->
                    call.expression()
                        + " status="
                        + call.resolution()
                        + " targets="
                        + call.targets())
            .reduce("", (left, right) -> left + "\ncall=" + right);
    return methods + calls + "\nlimitations=" + context.limitations();
  }
}
