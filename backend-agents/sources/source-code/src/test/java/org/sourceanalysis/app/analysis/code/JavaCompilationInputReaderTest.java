package org.sourceanalysis.app.analysis.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/** Direct RED contracts for the external Maven classpath handoff. */
class JavaCompilationInputReaderTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String COMMON_POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>"
          + "<artifactId>common</artifactId><version>1</version></project>\n";
  private static final String WEB_POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>"
          + "<artifactId>web</artifactId><version>1</version></project>\n";
  private static final String WEB_WITH_COMMON_POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>"
          + "<artifactId>web-with-common</artifactId><version>1</version>"
          + "<dependencies><dependency><groupId>fixture</groupId>"
          + "<artifactId>common</artifactId><version>1</version><scope>compile</scope>"
          + "</dependency></dependencies></project>\n";
  private static final String READER_TYPE =
      "org.sourceanalysis.app.analysis.code.JavaCompilationInputReader";

  @TempDir Path temporaryDirectory;

  @Test
  void readsTheActualClasspathExportPerModuleInDeclaredOrderAndBindsItToTheSelectedR0()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonFirst = writeJar("common-first.jar");
    Path commonSecond = writeJar("common-second.jar");
    Path webFirst = writeJar("web-first.jar");
    Path webSecond = writeJar("web-second.jar");
    Path commonClasspath = writeClasspath("common.classpath", commonFirst, commonSecond);
    Path webClasspath = writeClasspath("web.classpath", webFirst, webSecond);
    Path compilationInput =
        writeCompilationInput(
            selectedSource,
            List.of(
                module("modules/common", "modules/common/src/main/java", commonClasspath),
                module("modules/web", "modules/web/src/main/java", webClasspath)));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("READY");
    Object environment = access(result, "environment");
    assertThat(environment).isNotNull();
    assertThat(access(environment, "sourceSnapshotId")).isEqualTo(sourceTexts.snapshotId());
    List<?> modules = (List<?>) access(environment, "modules");
    assertThat(modules).hasSize(2);
    List<String> modulePaths = new java.util.ArrayList<>();
    for (Object module : modules) {
      modulePaths.add(access(module, "modulePath").toString());
    }
    assertThat(modulePaths).containsExactly("modules/common", "modules/web");
    assertThat(classpath(modules.get(0)))
        .containsExactly(
            commonFirst.toAbsolutePath().normalize(), commonSecond.toAbsolutePath().normalize());
    assertThat(classpath(modules.get(1)))
        .containsExactly(
            webFirst.toAbsolutePath().normalize(), webSecond.toAbsolutePath().normalize());
  }

  @Test
  void derivesRootsCompilerSettingsAndDirectSourceEdgesFromV2OfficialOutputs() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonClasspath = writeClasspath("v2-common.classpath", writeJar("v2-common.jar"));
    Path webClasspath = writeClasspath("v2-web.classpath", writeJar("v2-web.jar"));
    EffectivePomFixture commonPom =
        writeEffectivePomFixture(
            "modules/common", "modules/common/src/main/java", javaVersion(), javaVersion());
    EffectivePomFixture webPom =
        writeEffectivePomFixture(
            "modules/web-with-common",
            "modules/web-with-common/src/main/java",
            javaVersion(),
            javaVersion());
    Path projectDirectory = temporaryDirectory.resolve("maven-project");
    JavaReadinessPreparation.CompilationInput compilationInput =
        new JavaReadinessPreparation.CompilationInput(
            projectDirectory,
            List.of(
                new JavaReadinessPreparation.ModuleInput(
                    "modules/common",
                    commonClasspath,
                    java.io.File.pathSeparator,
                    commonPom.effectivePomFile(),
                    Path.of(System.getProperty("java.home"))),
                new JavaReadinessPreparation.ModuleInput(
                    "modules/web-with-common",
                    webClasspath,
                    java.io.File.pathSeparator,
                    webPom.effectivePomFile(),
                    Path.of(System.getProperty("java.home")))));
    JavaReadinessPreparation.Result result =
        new JavaReadinessPreparation()
            .prepare(
                new JavaReadinessPreparation.V2Request(
                    sourceTexts, selectedSource, compilationInput));

    assertThat(result.status()).isEqualTo(JavaReadinessPreparation.Status.READY);
    JavaCompilationEnvironment environment = result.environment();
    List<JavaCompilationModuleEnvironment> modules = environment.modules();
    assertThat(modules).hasSize(2);
    JavaCompilationModuleEnvironment common = modules.get(0);
    JavaCompilationModuleEnvironment web = modules.get(1);
    assertThat(common.modulePath()).isEqualTo("modules/common");
    assertThat(web.modulePath()).isEqualTo("modules/web-with-common");

    assertThat(common.project().sourceRoots())
        .as("source roots must come from the effective POM, not the caller")
        .isEqualTo(List.of("modules/common/src/main/java"));
    assertThat(common.compilationTarget().source()).isEqualTo(javaVersion());
    assertThat(common.compilationTarget().target()).isEqualTo(javaVersion());

    List<SourceModuleDependency> sourceEdges = web.sourceModuleDependencies();
    assertThat(sourceEdges).hasSize(1);
    assertThat(sourceEdges.get(0).modulePath()).isEqualTo("modules/common");
    assertThat(sourceEdges.get(0).kind()).isEqualTo("SOURCE_MODULE");
  }

  @Test
  void blocksClasspathOnlyHandoffWithoutMavenEvaluatedProjectSettings() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath = writeClasspath("classpath-only.classpath", writeJar("classpath-only.jar"));
    Path compilationInput =
        writeCompilationInput(
            selectedSource,
            List.of(
                moduleWithoutMavenEvaluatedSettings(
                    "modules/common", "modules/common/src/main/java", classpath)));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result)).contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
    assertThat(problemDetails(result))
        .anySatisfy(detail -> assertThat(detail.toLowerCase()).contains("maven", "setting"));
  }

  @Test
  void acceptsMavenEvaluatedProjectSettingsThatMatchTheExternalModuleDeclaration()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath = writeClasspath("effective-pom.classpath", writeJar("effective-pom.jar"));
    ObjectNode module =
        moduleWithEvaluatedSettings(
            "modules/common", "modules/common/src/main/java", classpath, "8", "8");
    Path mavenProjectDirectory = Path.of(module.path("mavenProjectDirectory").asText());
    Path effectivePomFile = Path.of(module.path("effectivePomFile").asText());
    assertThat(Files.readString(mavenProjectDirectory.resolve("pom.xml"))).isEqualTo(COMMON_POM);
    assertThat(sha256(Files.readAllBytes(effectivePomFile)))
        .isEqualTo(module.path("effectivePomSha256").asText());
    Path compilationInput = writeCompilationInput(selectedSource, List.of(module));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("READY");
    Object environment = access(result, "environment");
    assertThat(environment).isNotNull();
    List<?> modules = (List<?>) access(environment, "modules");
    assertThat(modules).hasSize(1);
    Object compilationTarget = access(modules.get(0), "compilationTarget");
    assertThat(access(compilationTarget, "source")).isEqualTo("8");
    assertThat(access(compilationTarget, "target")).isEqualTo("8");
  }

  @Test
  void acceptsTopLevelCompilerSourceTargetWhenDefaultExecutionsRepeatTheSameJava8Settings()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath =
        writeClasspath(
            "effective-pom-default-executions.classpath", writeJar("default-executions.jar"));
    ObjectNode module =
        moduleWithCompilerExecutionSettings(
            "modules/common",
            "modules/common/src/main/java",
            classpath,
            "1.8",
            "1.8",
            "1.8",
            "1.8");
    JavaCompilationEnvironmentResult result = readV2(module, selectedSource, sourceTexts);

    assertThat(result.status()).isEqualTo(JavaCompilationEnvironmentStatus.READY);
    JavaCompilationEnvironment environment = result.environment();
    assertThat(environment).isNotNull();
    JavaCompilationModuleEnvironment moduleEnvironment = environment.modules().get(0);
    assertThat(moduleEnvironment.compilationTarget().source()).isEqualTo("8");
    assertThat(moduleEnvironment.compilationTarget().target()).isEqualTo("8");
  }

  @Test
  void blocksCompilerExecutionSourceTargetThatDiffersFromTopLevelSettingsWithClearReason()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath =
        writeClasspath(
            "effective-pom-conflicting-execution.classpath", writeJar("conflicting-execution.jar"));
    ObjectNode module =
        moduleWithCompilerExecutionSettings(
            "modules/common", "modules/common/src/main/java", classpath, "1.8", "1.8", "17", "17");
    JavaCompilationEnvironmentResult result = readV2(module, selectedSource, sourceTexts);

    assertThat(result.status()).isEqualTo(JavaCompilationEnvironmentStatus.BLOCKED);
    assertThat(result.environment()).isNull();
    assertThat(result.problems())
        .extracting(JavaCompilationEnvironmentProblem::code)
        .contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
    assertThat(result.problems())
        .anySatisfy(
            problem ->
                assertThat(problem.detail().toLowerCase())
                    .contains("compiler-plugin", "execution"));
  }

  @Test
  void blocksWhenCompilerSettingsDisagreeWithTheMavenEvaluatedPom() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath = writeClasspath("mismatched-effective-pom.classpath", writeJar("mismatch.jar"));
    ObjectNode module =
        moduleWithEvaluatedSettings(
            "modules/common", "modules/common/src/main/java", classpath, "17", "17", "8", "8");
    Path compilationInput = writeCompilationInput(selectedSource, List.of(module));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result)).contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
  }

  @Test
  void blocksSourceModuleEdgesNotVerifiedByTheMavenEvaluatedPom() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonClasspath =
        writeClasspath("dependency-common.classpath", writeJar("dependency-common.jar"));
    Path webClasspath = writeClasspath("dependency-web.classpath", writeJar("dependency-web.jar"));
    ObjectNode common = module("modules/common", "modules/common/src/main/java", commonClasspath);
    ObjectNode web = module("modules/web", "modules/web/src/main/java", webClasspath);
    ObjectNode dependency = web.withArray("sourceModuleDependencies").addObject();
    dependency.put("modulePath", "modules/common");
    dependency.put("kind", "SOURCE_MODULE");
    Path compilationInput = writeCompilationInput(selectedSource, List.of(common, web));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result)).contains("JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED");
    assertThat(problemDetails(result))
        .anySatisfy(detail -> assertThat(detail.toLowerCase()).contains("maven", "depend"));
  }

  @Test
  void retainsModuleEdgeWhenTheEffectivePomDeclaresTheMatchingDirectDependency() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonClasspath =
        writeClasspath("verified-common.classpath", writeJar("verified-common.jar"));
    Path webClasspath = writeClasspath("verified-web.classpath", writeJar("verified-web.jar"));
    ObjectNode common = module("modules/common", "modules/common/src/main/java", commonClasspath);
    ObjectNode web =
        module("modules/web-with-common", "modules/web-with-common/src/main/java", webClasspath);
    addSourceModuleDependency(web, "modules/common");
    Path compilationInput = writeCompilationInput(selectedSource, List.of(common, web));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("READY");
    List<?> environmentModules = (List<?>) access(access(result, "environment"), "modules");
    Object webEnvironment = environmentModules.get(1);
    assertThat(access(webEnvironment, "modulePath")).isEqualTo("modules/web-with-common");
    List<?> edges = (List<?>) access(webEnvironment, "sourceModuleDependencies");
    assertThat(edges).hasSize(1);
    assertThat(access(edges.get(0), "modulePath")).isEqualTo("modules/common");
    assertThat(access(edges.get(0), "kind")).isEqualTo("SOURCE_MODULE");
  }

  @Test
  void blocksEffectiveModuleDependencyThatIsMissingFromTheDeclaredSourceEdges() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonClasspath =
        writeClasspath("omitted-common.classpath", writeJar("omitted-common.jar"));
    Path webClasspath = writeClasspath("omitted-web.classpath", writeJar("omitted-web.jar"));
    ObjectNode common = module("modules/common", "modules/common/src/main/java", commonClasspath);
    ObjectNode web =
        module("modules/web-with-common", "modules/web-with-common/src/main/java", webClasspath);
    Path compilationInput = writeCompilationInput(selectedSource, List.of(common, web));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    String projectSettingsProblem = "JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED";
    assertThat(problemCodes(result)).contains(projectSettingsProblem);
    assertThat(problemModulePaths(result, projectSettingsProblem))
        .contains("modules/web-with-common");
  }

  @Test
  void rejectsAnExternalInputBoundToAnotherR0BeforeOpeningItsClasspathFile() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath = temporaryDirectory.resolve("classpath-never-opened.txt");
    Path compilationInput =
        writeCompilationInput(
            selectedSource,
            List.of(module("modules/common", "modules/common/src/main/java", classpath)),
            Map.of("sourceVersionId", "snapshot:" + "f".repeat(64)));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result)).contains("JAVA_COMPILATION_INPUT_SOURCE_MISMATCH");
    assertThat(classpath).doesNotExist();
  }

  @Test
  void rejectsAnExternalInputWithAnotherEffectiveScopeDigestBeforeOpeningItsClasspathFile()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path classpath = temporaryDirectory.resolve("scope-mismatch.classpath");
    Path compilationInput =
        writeCompilationInput(
            selectedSource,
            List.of(module("modules/common", "modules/common/src/main/java", classpath)),
            Map.of("effectiveScopeDigest", "f".repeat(64)));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result)).contains("JAVA_COMPILATION_INPUT_SCOPE_MISMATCH");
    assertThat(classpath).doesNotExist();
  }

  @Test
  void reportsMissingClasspathFileAndTargetJdkAsIndependentInputBlockers() throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path missingClasspath = temporaryDirectory.resolve("missing.classpath");
    Path missingJdk = temporaryDirectory.resolve("missing-jdk");
    Path compilationInput =
        writeCompilationInput(
            selectedSource,
            List.of(
                module(
                    "modules/common",
                    "modules/common/src/main/java",
                    missingClasspath,
                    Map.of("targetJdkHome", missingJdk.toString()))));

    Object result = read(compilationInput, selectedSource, sourceTexts);

    assertThat(access(result, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(result, "environment")).isNull();
    assertThat(problemCodes(result))
        .contains("JAVA_COMPILATION_INPUT_FILE_MISSING", "JAVA_TARGET_PLATFORM_INVALID");
  }

  @Test
  void blocksAnInputWhenTheBuildFileDigestOrExportStatusCannotProveSameSourcePreparation()
      throws Exception {
    VerifiedSourceTextSet sourceTexts = sourceTexts();
    SelectedSourceBasis selectedSource = selectedSourceBasis(sourceTexts.snapshotId());
    Path commonClasspath = writeClasspath("common.classpath", writeJar("common.jar"));
    ObjectNode commonModule =
        module("modules/common", "modules/common/src/main/java", commonClasspath);

    Path digestMismatch =
        writeCompilationInput(
            selectedSource, List.of(commonModule), Map.of("buildFileDigest", "f".repeat(64)));

    Object digestResult = read(digestMismatch, selectedSource, sourceTexts);

    assertThat(access(digestResult, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(digestResult, "environment")).isNull();
    assertThat(problemCodes(digestResult)).contains("JAVA_COMPILATION_INPUT_BUILD_MISMATCH");

    Path failedExport =
        writeCompilationInput(
            selectedSource,
            List.of(commonModule),
            Map.of(
                "buildFileDigest",
                sha256(COMMON_POM.getBytes(StandardCharsets.UTF_8)),
                "exportStatus",
                "FAILED"));

    Object exportResult = read(failedExport, selectedSource, sourceTexts);

    assertThat(access(exportResult, "status").toString()).isEqualTo("BLOCKED");
    assertThat(access(exportResult, "environment")).isNull();
    assertThat(problemCodes(exportResult)).contains("JAVA_COMPILATION_INPUT_EXPORT_FAILED");
  }

  private Object read(
      Path compilationInput, SelectedSourceBasis source, VerifiedSourceTextSet texts)
      throws Exception {
    final Class<?> readerType;
    try {
      readerType = Class.forName(READER_TYPE);
    } catch (ClassNotFoundException missing) {
      fail(
          "INTERFACE_MISSING: expected "
              + READER_TYPE
              + " to read one named external compilation-input file",
          missing);
      return null;
    }
    try {
      Constructor<?> constructor = readerType.getDeclaredConstructor();
      constructor.setAccessible(true);
      Object reader = constructor.newInstance();
      Method method =
          readerType.getDeclaredMethod(
              "read", Path.class, SelectedSourceBasis.class, VerifiedSourceTextSet.class);
      method.setAccessible(true);
      return method.invoke(reader, compilationInput, source, texts);
    } catch (NoSuchMethodException missing) {
      fail(
          "INTERFACE_MISSING: JavaCompilationInputReader must expose read(Path, SelectedSourceBasis, VerifiedSourceTextSet)",
          missing);
      return null;
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError(
          "external compilation-input reader threw before returning a result", cause);
    }
  }

  private JavaCompilationEnvironmentResult readV2(
      ObjectNode module, SelectedSourceBasis source, VerifiedSourceTextSet texts) {
    JavaReadinessPreparation.ModuleInput typedModule =
        new JavaReadinessPreparation.ModuleInput(
            module.path("modulePath").asText(),
            Path.of(module.path("classpathFile").asText()),
            module.path("classpathSeparator").asText(),
            Path.of(module.path("effectivePomFile").asText()),
            Path.of(module.path("targetJdkHome").asText()));
    JavaReadinessPreparation.CompilationInput typedInput =
        new JavaReadinessPreparation.CompilationInput(
            temporaryDirectory.resolve("maven-project"), List.of(typedModule));
    return new JavaCompilationInputReader().read(typedInput, source, texts);
  }

  private static List<String> problemCodes(Object result) throws ReflectiveOperationException {
    List<?> problems = (List<?>) access(result, "problems");
    List<String> codes = new java.util.ArrayList<>();
    for (Object problem : problems) {
      codes.add(access(problem, "code").toString());
    }
    return codes;
  }

  private static List<String> problemDetails(Object result) throws ReflectiveOperationException {
    List<?> problems = (List<?>) access(result, "problems");
    List<String> details = new java.util.ArrayList<>();
    for (Object problem : problems) {
      details.add(access(problem, "detail").toString());
    }
    return details;
  }

  private static List<String> problemModulePaths(Object result, String code)
      throws ReflectiveOperationException {
    List<?> problems = (List<?>) access(result, "problems");
    List<String> modulePaths = new java.util.ArrayList<>();
    for (Object problem : problems) {
      if (code.equals(access(problem, "code").toString())) {
        Object modulePath = access(problem, "modulePath");
        if (modulePath != null) {
          modulePaths.add(modulePath.toString());
        }
      }
    }
    return modulePaths;
  }

  @SuppressWarnings("unchecked")
  private static List<Path> classpath(Object module) throws ReflectiveOperationException {
    Object project = access(module, "project");
    return (List<Path>) access(project, "classpath");
  }

  private Path writeCompilationInput(SelectedSourceBasis source, List<ObjectNode> modules)
      throws IOException {
    return writeCompilationInput(source, modules, Map.of());
  }

  private Path writeCompilationInput(
      SelectedSourceBasis source, List<ObjectNode> modules, Map<String, String> overrides)
      throws IOException {
    ObjectNode input = JSON.createObjectNode();
    input.put("schemaVersion", "java-compilation-input-v1");
    input.put(
        "sourcePreparationRunId", source.preparedSource().publication().address().runId().value());
    input.put(
        "sourceVersionId", overrides.getOrDefault("sourceVersionId", source.snapshotId().value()));
    input.put(
        "effectiveScopeDigest",
        overrides.getOrDefault("effectiveScopeDigest", source.effectiveScopeDigest().value()));
    ArrayNode configuredModules = input.putArray("modules");
    modules.forEach(configuredModules::add);
    ArrayNode buildFiles = input.putArray("buildFiles");
    for (ObjectNode configuredModule : modules) {
      String modulePath = configuredModule.path("modulePath").asText();
      ObjectNode buildFile = buildFiles.addObject();
      buildFile.put("modulePath", modulePath);
      buildFile.put("path", modulePath + "/pom.xml");
      String pom = pomFor(modulePath);
      buildFile.put(
          "sha256",
          overrides.getOrDefault("buildFileDigest", sha256(pom.getBytes(StandardCharsets.UTF_8))));
    }
    ObjectNode export = input.putObject("export");
    export.put("tool", "maven-dependency-plugin");
    export.put("status", overrides.getOrDefault("exportStatus", "SUCCEEDED"));
    export.put("classpathSeparator", java.io.File.pathSeparator);
    return Files.writeString(
        temporaryDirectory.resolve("compilation-input.json"),
        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(input),
        StandardCharsets.UTF_8);
  }

  private ObjectNode module(String modulePath, String sourceRoot, Path classpathFile)
      throws IOException {
    return module(modulePath, sourceRoot, classpathFile, Map.of());
  }

  private ObjectNode module(
      String modulePath, String sourceRoot, Path classpathFile, Map<String, String> overrides)
      throws IOException {
    String targetVersion = javaVersion();
    return moduleWithEvaluatedSettings(
        modulePath,
        sourceRoot,
        classpathFile,
        targetVersion,
        targetVersion,
        targetVersion,
        targetVersion,
        overrides);
  }

  private ObjectNode moduleWithoutMavenEvaluatedSettings(
      String modulePath, String sourceRoot, Path classpathFile) {
    return moduleDeclaration(modulePath, sourceRoot, classpathFile, Map.of());
  }

  private ObjectNode moduleWithEvaluatedSettings(
      String modulePath,
      String sourceRoot,
      Path classpathFile,
      String declaredSource,
      String declaredTarget)
      throws IOException {
    return moduleWithEvaluatedSettings(
        modulePath,
        sourceRoot,
        classpathFile,
        declaredSource,
        declaredTarget,
        declaredSource,
        declaredTarget,
        Map.of());
  }

  private ObjectNode moduleWithEvaluatedSettings(
      String modulePath,
      String sourceRoot,
      Path classpathFile,
      String declaredSource,
      String declaredTarget,
      String evaluatedSource,
      String evaluatedTarget,
      Map<String, String> overrides)
      throws IOException {
    ObjectNode module = moduleDeclaration(modulePath, sourceRoot, classpathFile, overrides);
    module.remove("release");
    module.put("source", declaredSource);
    module.put("target", declaredTarget);
    EffectivePomFixture fixture =
        writeEffectivePomFixture(modulePath, sourceRoot, evaluatedSource, evaluatedTarget);
    module.put("effectivePomFile", fixture.effectivePomFile().toString());
    module.put("effectivePomSha256", sha256(Files.readAllBytes(fixture.effectivePomFile())));
    module.put("mavenProjectDirectory", fixture.mavenProjectDirectory().toString());
    return module;
  }

  private ObjectNode moduleWithCompilerExecutionSettings(
      String modulePath,
      String sourceRoot,
      Path classpathFile,
      String topLevelSource,
      String topLevelTarget,
      String executionSource,
      String executionTarget)
      throws IOException {
    ObjectNode module = moduleDeclaration(modulePath, sourceRoot, classpathFile, Map.of());
    module.remove("release");
    module.put("source", topLevelSource);
    module.put("target", topLevelTarget);
    EffectivePomFixture fixture =
        writeEffectivePomWithCompilerExecutions(
            modulePath,
            sourceRoot,
            topLevelSource,
            topLevelTarget,
            executionSource,
            executionTarget);
    module.put("effectivePomFile", fixture.effectivePomFile().toString());
    module.put("effectivePomSha256", sha256(Files.readAllBytes(fixture.effectivePomFile())));
    module.put("mavenProjectDirectory", fixture.mavenProjectDirectory().toString());
    return module;
  }

  private ObjectNode moduleWithEvaluatedSettings(
      String modulePath,
      String sourceRoot,
      Path classpathFile,
      String declaredSource,
      String declaredTarget,
      String evaluatedSource,
      String evaluatedTarget)
      throws IOException {
    return moduleWithEvaluatedSettings(
        modulePath,
        sourceRoot,
        classpathFile,
        declaredSource,
        declaredTarget,
        evaluatedSource,
        evaluatedTarget,
        Map.of());
  }

  private ObjectNode moduleDeclaration(
      String modulePath, String sourceRoot, Path classpathFile, Map<String, String> overrides) {
    ObjectNode module = JSON.createObjectNode();
    module.put("modulePath", modulePath);
    ArrayNode sourceRoots = module.putArray("sourceRoots");
    sourceRoots.add(sourceRoot);
    module.put("classpathFile", classpathFile.toAbsolutePath().normalize().toString());
    module.put("classpathSeparator", java.io.File.pathSeparator);
    module.put(
        "targetJdkHome",
        overrides.getOrDefault(
            "targetJdkHome", Path.of(System.getProperty("java.home")).toString()));
    String targetVersion = javaVersion();
    module.put("targetJdkVersion", targetVersion);
    module.put(
        "executionEnvironmentName",
        targetVersionInt(targetVersion) <= 8
            ? "JavaSE-1." + targetVersion
            : "JavaSE-" + targetVersion);
    module.put("release", targetVersion);
    module.putArray("sourceModuleDependencies");
    return module;
  }

  private static void addSourceModuleDependency(ObjectNode module, String dependencyModulePath) {
    ObjectNode dependency = module.withArray("sourceModuleDependencies").addObject();
    dependency.put("modulePath", dependencyModulePath);
    dependency.put("kind", "SOURCE_MODULE");
  }

  private EffectivePomFixture writeEffectivePomFixture(
      String modulePath, String sourceRoot, String pomSource, String pomTarget) throws IOException {
    Path mavenProjectDirectory = temporaryDirectory.resolve("maven-project").resolve(modulePath);
    Files.createDirectories(mavenProjectDirectory);
    Files.writeString(
        mavenProjectDirectory.resolve("pom.xml"), pomFor(modulePath), StandardCharsets.UTF_8);
    Path relativeSourceRoot = Path.of(modulePath).relativize(Path.of(sourceRoot));
    Path sourceDirectory =
        mavenProjectDirectory.resolve(relativeSourceRoot).toAbsolutePath().normalize();
    Files.createDirectories(sourceDirectory);
    String escapedSourceDirectory =
        sourceDirectory.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    String effectivePom =
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture</groupId>
          <artifactId>%s</artifactId>
          <version>1</version>
          %s
          <build>
            <sourceDirectory>%s</sourceDirectory>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration><source>%s</source><target>%s</target></configuration>
              </plugin>
            </plugins>
          </build>
        </project>
        """
            .formatted(
                artifactIdFor(modulePath),
                effectiveDependenciesFor(modulePath),
                escapedSourceDirectory,
                pomSource,
                pomTarget);
    Path effectivePomFile =
        temporaryDirectory.resolve(modulePath.replace('/', '-') + "-effective-pom.xml");
    Files.writeString(effectivePomFile, effectivePom, StandardCharsets.UTF_8);
    return new EffectivePomFixture(effectivePomFile, mavenProjectDirectory);
  }

  private EffectivePomFixture writeEffectivePomWithCompilerExecutions(
      String modulePath,
      String sourceRoot,
      String topLevelSource,
      String topLevelTarget,
      String executionSource,
      String executionTarget)
      throws IOException {
    Path mavenProjectDirectory = temporaryDirectory.resolve("maven-project").resolve(modulePath);
    Files.createDirectories(mavenProjectDirectory);
    Files.writeString(
        mavenProjectDirectory.resolve("pom.xml"), pomFor(modulePath), StandardCharsets.UTF_8);
    Path relativeSourceRoot = Path.of(modulePath).relativize(Path.of(sourceRoot));
    Path sourceDirectory =
        mavenProjectDirectory.resolve(relativeSourceRoot).toAbsolutePath().normalize();
    Files.createDirectories(sourceDirectory);
    String escapedSourceDirectory =
        sourceDirectory.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    String effectivePom =
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture</groupId>
          <artifactId>%s</artifactId>
          <version>1</version>
          %s
          <build>
            <sourceDirectory>%s</sourceDirectory>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration><source>%s</source><target>%s</target></configuration>
                <executions>
                  <execution>
                    <id>default-compile</id>
                    <goals><goal>compile</goal></goals>
                    <configuration><source>%s</source><target>%s</target></configuration>
                  </execution>
                  <execution>
                    <id>default-testCompile</id>
                    <goals><goal>testCompile</goal></goals>
                    <configuration><source>%s</source><target>%s</target></configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </project>
        """
            .formatted(
                artifactIdFor(modulePath),
                effectiveDependenciesFor(modulePath),
                escapedSourceDirectory,
                topLevelSource,
                topLevelTarget,
                executionSource,
                executionTarget,
                executionSource,
                executionTarget);
    Path effectivePomFile =
        temporaryDirectory.resolve(modulePath.replace('/', '-') + "-compiler-executions-pom.xml");
    Files.writeString(effectivePomFile, effectivePom, StandardCharsets.UTF_8);
    return new EffectivePomFixture(effectivePomFile, mavenProjectDirectory);
  }

  private static String pomFor(String modulePath) {
    return switch (modulePath) {
      case "modules/common" -> COMMON_POM;
      case "modules/web" -> WEB_POM;
      case "modules/web-with-common" -> WEB_WITH_COMMON_POM;
      default -> throw new IllegalArgumentException("fixture module path is unsupported");
    };
  }

  private static String artifactIdFor(String modulePath) {
    return switch (modulePath) {
      case "modules/common" -> "common";
      case "modules/web" -> "web";
      case "modules/web-with-common" -> "web-with-common";
      default -> throw new IllegalArgumentException("fixture module path is unsupported");
    };
  }

  private static String effectiveDependenciesFor(String modulePath) {
    if ("modules/web-with-common".equals(modulePath)) {
      return """
          <dependencies>
            <dependency><groupId>fixture</groupId><artifactId>common</artifactId>
              <version>1</version><scope>compile</scope></dependency>
          </dependencies>
          """;
    }
    return "";
  }

  private Path writeClasspath(String name, Path... entries) throws IOException {
    return Files.writeString(
        temporaryDirectory.resolve(name),
        java.util.Arrays.stream(entries)
            .map(path -> path.toAbsolutePath().normalize().toString())
            .collect(Collectors.joining(java.io.File.pathSeparator)),
        StandardCharsets.UTF_8);
  }

  private Path writeJar(String name) throws IOException {
    Path jar = temporaryDirectory.resolve(name);
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("fixture/Owned.class"));
      output.write(new byte[] {0, 0, 0, 61});
      output.closeEntry();
    }
    return jar.toRealPath();
  }

  private VerifiedSourceTextSet sourceTexts() {
    List<VerifiedSourceTextDocument> documents =
        List.of(
            document(
                "modules/common/src/main/java/fixture/Common.java",
                "package fixture; final class Common {}\n"),
            document(
                "modules/web/src/main/java/fixture/Web.java",
                "package fixture; final class Web {}\n"),
            document(
                "modules/web-with-common/src/main/java/fixture/WebWithCommon.java",
                "package fixture; final class WebWithCommon {}\n"),
            document("modules/common/pom.xml", COMMON_POM, "application/xml"),
            document("modules/web/pom.xml", WEB_POM, "application/xml"),
            document("modules/web-with-common/pom.xml", WEB_WITH_COMMON_POM, "application/xml"));
    String identity =
        sha256(
            String.join(
                    "\n",
                    documents.stream()
                        .map(document -> document.path() + document.sha256().value())
                        .toList())
                .getBytes(StandardCharsets.UTF_8));
    return new VerifiedSourceTextSet(
        "snapshot:" + identity,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", 'a'),
        reference("source-inventory", 'b'),
        reference("verified-snapshot", 'c'),
        new ArtifactControls(
            digest('d'),
            digest('e'),
            digest('f'),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "1".repeat(64)), digest('1'))),
        documents);
  }

  private static VerifiedSourceTextDocument document(String path, String content) {
    return document(path, content, "text/x-java");
  }

  private static VerifiedSourceTextDocument document(
      String path, String content, String mediaType) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    String contentDigest = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + contentDigest).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        mediaType,
        bytes.length,
        new Sha256Digest(contentDigest),
        ImmutableBytes.copyOf(bytes));
  }

  private static SelectedSourceBasis selectedSourceBasis(String snapshotId) {
    ArtifactId snapshot = ArtifactId.parse(snapshotId);
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    AnalysisStepPublicationReference publication =
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            AnalysisStepArtifactRoot.parse("analysis-step-root:" + "b".repeat(64)),
            AnalysisStepReceiptId.parse("analysis-step-receipt:" + "c".repeat(64)),
            digest('4'));
    ArtifactReference schema = reference("schema-bundle", '5');
    ArtifactReference policy = reference("artifact-policy-registry", '6');
    PreparedSourceReference prepared =
        new PreparedSourceReference(
            snapshot,
            publication,
            schema,
            new ArtifactPolicyRegistryReference(policy.artifactId(), policy.sha256()));
    return new SelectedSourceBasis(
        SelectedSourceBasis.Kind.PREPARED_V1, prepared, null, snapshot, digest('7'));
  }

  private static Object access(Object instance, String accessor)
      throws ReflectiveOperationException {
    Method method = instance.getClass().getDeclaredMethod(accessor);
    method.setAccessible(true);
    return method.invoke(instance);
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64)), digest(fill));
  }

  private static Sha256Digest digest(char fill) {
    return new Sha256Digest(String.valueOf(fill).repeat(64));
  }

  private record EffectivePomFixture(Path effectivePomFile, Path mavenProjectDirectory) {}

  private static String javaVersion() {
    String version = System.getProperty("java.specification.version");
    return version.startsWith("1.") ? version.substring(2) : version;
  }

  private static int targetVersionInt(String version) {
    int dot = version.indexOf('.');
    return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
