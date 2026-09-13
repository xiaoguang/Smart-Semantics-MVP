package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
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
class JdtRealSourceCollectionIT {

  private static final String TEST_JAVA_HOME = "sourceanalysis.jdt.testJavaHome";
  private static final String TEST_PROJECT = "sourceanalysis.jdt.testProject";
  private static final String TEST_DISTRIBUTION = "sourceanalysis.jdt.testDistribution";
  private static final String TEST_DEPENDENCIES = "sourceanalysis.jdt.testDependencies";
  private static final Path ACTUAL_OUTPUT = Path.of("target", "jdt-production-navigation");

  @Test
  void jdtCollectsRegistrationAndFinancialImplementationsInOneProjectIndex() throws Exception {
    RealJdtPrerequisites prerequisites = prerequisites();
    VerifiedJavaProject project = frozenProject(prerequisites);
    EffectiveEngineConfiguration configuration =
        new EffectiveEngineConfiguration(
            EffectiveEngineConfiguration.JDT,
            new EffectiveEngineConfiguration.JdtConfiguration(
                prerequisites.distribution(),
                prerequisites.toolJavaHome(),
                Duration.ofSeconds(90),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)));

    try (JavaCodeSession session = new JdtCodeEngine(configuration).open(project)) {
      JavaDeclarationCatalog catalog = session.catalog();
      JavaDeclarationCatalog.MethodDeclarationView entry =
          catalog.methods().stream()
              .filter(method -> method.sourcePath().endsWith("controller/UserController.java"))
              .filter(method -> method.name().equals("registerUser"))
              .findFirst()
              .orElseThrow();
      assertThat(catalog.types())
          .filteredOn(type -> "com.jsh.erp.controller.UserController".equals(type.qualifiedName()))
          .singleElement()
          .satisfies(type -> assertThat(type.kind()).isEqualTo("CLASS"));
      assertThat(catalog.annotations())
          .filteredOn(annotation -> entry.annotationKeys().contains(annotation.annotationKey()))
          .filteredOn(annotation -> "PostMapping".equals(annotation.nameText()))
          .singleElement()
          .satisfies(
              annotation ->
                  assertThat(annotation.qualifiedName())
                      .isEqualTo("org.springframework.web.bind.annotation.PostMapping"));

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
          catalog.methods().stream()
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

  private static RealJdtPrerequisites prerequisites() {
    Path project = requiredDirectory(TEST_PROJECT, "frozen project");
    Path distribution = requiredDirectory(TEST_DISTRIBUTION, "JDT distribution");
    Path toolJavaHome = requiredDirectory(TEST_JAVA_HOME, "tool Java home");
    if (!Files.isExecutable(toolJavaHome.resolve("bin").resolve("java"))) {
      throw missing(TEST_JAVA_HOME, "bin/java is not executable");
    }
    if (!Files.isExecutable(distribution.resolve("bin").resolve("jdtls"))) {
      throw missing(TEST_DISTRIBUTION, "bin/jdtls is not executable");
    }
    List<Path> dependencies = requiredDependencies();
    if (!Files.isRegularFile(JdtSyntaxHelperArtifact.locate())) {
      throw missing("tools/jdt-syntax-helper", "syntax helper artifact is absent");
    }
    return new RealJdtPrerequisites(
        project.toAbsolutePath(),
        distribution.toAbsolutePath(),
        toolJavaHome.toAbsolutePath(),
        dependencies);
  }

  private static Path requiredDirectory(String property, String description) {
    Path value = requiredPath(property, description);
    if (!Files.isDirectory(value)) {
      throw missing(property, description + " is not a directory");
    }
    return value;
  }

  private static Path requiredPath(String property, String description) {
    String configured = System.getProperty(property);
    if (configured == null || configured.isBlank()) {
      throw missing(property, description + " property is blank");
    }
    try {
      return Path.of(configured);
    } catch (RuntimeException malformed) {
      throw new IllegalStateException(
          "Missing required real-jdt-it prerequisite: " + property + " is not a valid path",
          malformed);
    }
  }

  private static List<Path> requiredDependencies() {
    String configured = System.getProperty(TEST_DEPENDENCIES);
    if (configured == null || configured.isBlank()) {
      throw missing(TEST_DEPENDENCIES, "dependency list property is blank");
    }
    List<Path> dependencies =
        List.of(configured.split(java.util.regex.Pattern.quote(File.pathSeparator))).stream()
            .filter(value -> !value.isBlank())
            .map(Path::of)
            .toList();
    if (dependencies.isEmpty()) {
      throw missing(TEST_DEPENDENCIES, "dependency list is empty");
    }
    for (Path dependency : dependencies) {
      if (!Files.isRegularFile(dependency)) {
        throw missing(TEST_DEPENDENCIES, "dependency is not a file: " + dependency);
      }
    }
    return dependencies;
  }

  private static IllegalStateException missing(String property, String detail) {
    throw new IllegalStateException(
        "Missing required real-jdt-it prerequisite: " + property + " (" + detail + ")");
  }

  private static VerifiedJavaProject frozenProject(RealJdtPrerequisites prerequisites)
      throws IOException {
    List<VerifiedSourceTextDocument> documents;
    try (var paths = Files.walk(prerequisites.project())) {
      documents =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .sorted(Comparator.comparing(Path::toString))
              .map(path -> document(prerequisites.project(), path))
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
        sourceTexts, List.of("src/main/java"), prerequisites.dependencies(), "17");
  }

  private static VerifiedSourceTextDocument document(Path project, Path source) {
    try {
      byte[] bytes = Files.readAllBytes(source);
      String relative = project.relativize(source).toString().replace('\\', '/');
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

  private record RealJdtPrerequisites(
      Path project, Path distribution, Path toolJavaHome, List<Path> dependencies) {
    private RealJdtPrerequisites {
      dependencies = List.copyOf(dependencies);
    }
  }
}
