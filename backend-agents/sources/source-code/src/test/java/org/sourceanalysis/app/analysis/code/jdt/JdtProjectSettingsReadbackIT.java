package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
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

/** Failsafe-only real JDT LS target-runtime readback over self-owned project fixtures. */
class JdtProjectSettingsReadbackIT {

  private static final String TOOL_JAVA_HOME = "sourceanalysis.jdt.testJavaHome";
  private static final String TARGET_JAVA_HOME = "sourceanalysis.jdt.testTargetJavaHome";
  private static final String SECOND_TARGET_JAVA_HOME =
      "sourceanalysis.jdt.testSecondTargetJavaHome";
  private static final String DISTRIBUTION = "sourceanalysis.jdt.testDistribution";
  private static final String TARGET_JAVA_VERSION = "8";
  private static final String SOURCE_PATH = "src/main/java/fixture/SettingsProbe.java";
  private static final String SOURCE = "package fixture; final class SettingsProbe {}\n";

  @TempDir Path temporaryDirectory;

  @Test
  void readsSelectedTargetVmAndOwnedClasspathWithoutOpeningTheSyntaxHelper() throws Exception {
    assertProjectSettingsReadback(false);
  }

  @Test
  void readsSelectedTargetVmAndOwnedClasspathWithLegacyJavaSeContainerId() throws Exception {
    assertProjectSettingsReadback(true);
  }

  @Test
  void readsDistinctModuleVmAndClasspathFromOneLanguageServerWorkspace() throws Exception {
    WorkspaceSeam seam = requireWorkspaceSeam();
    Path toolJavaHome = requiredJavaHome(TOOL_JAVA_HOME);
    Path leftTargetJavaHome = requiredJavaHome(TARGET_JAVA_HOME);
    Path rightTargetJavaHome = requiredJavaHome(SECOND_TARGET_JAVA_HOME);
    Path distribution = requiredDirectory(DISTRIBUTION);
    assertThat(leftTargetJavaHome)
        .as("module target runtimes must be distinct")
        .isNotEqualTo(rightTargetJavaHome);
    assertThat(leftTargetJavaHome).isNotEqualTo(toolJavaHome);
    assertThat(rightTargetJavaHome).isNotEqualTo(toolJavaHome);

    Path leftJar = compileApiJar("left-api", "leftOnly", "8");
    Path rightJar = compileApiJar("right-api", "rightOnly", "17");
    String leftSource =
        "package fixture; public final class Twin { "
            + "public int invoke() { return new Api().leftOnly(); } }\n";
    String rightSource =
        "package fixture; public final class Twin { "
            + "public int invoke() { return new Api().rightOnly(); } }\n";
    VerifiedSourceTextSet completeSourceTexts = twoModuleSourceTexts(leftSource, rightSource);
    VerifiedJavaProject leftProject =
        moduleProject(
            completeSourceTexts,
            "modules/left/src/main/java",
            "modules/left/src/main/java/fixture/Twin.java",
            leftJar,
            "8");
    VerifiedJavaProject rightProject =
        moduleProject(
            completeSourceTexts,
            "modules/right/src/main/java",
            "modules/right/src/main/java/fixture/Twin.java",
            rightJar,
            "17");
    Object leftRuntime = targetRuntime("JavaSE-1.8", leftTargetJavaHome);
    Object rightRuntime = targetRuntime("JavaSE-17", rightTargetJavaHome);
    Object binding =
        seam.binding(
            List.of(
                seam.module("modules/left", leftProject, leftRuntime, List.of()),
                seam.module("modules/right", rightProject, rightRuntime, List.of())));

    EffectiveEngineConfiguration.JdtConfiguration configuration =
        new EffectiveEngineConfiguration.JdtConfiguration(
            distribution,
            toolJavaHome,
            Duration.ofSeconds(120),
            Duration.ofSeconds(45),
            Duration.ofSeconds(10));
    AtomicInteger languageServerStarts = new AtomicInteger();
    AtomicReference<Path> languageServerWorkingDirectory = new AtomicReference<>();
    JdtProcessIsolation isolation =
        new JdtProcessIsolation(
            (command, workingDirectory) -> {
              languageServerStarts.incrementAndGet();
              languageServerWorkingDirectory.set(workingDirectory);
              return new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
            });
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);
    Object session = seam.open(binding, languageServer, isolation);
    try {
      assertThat(languageServerStarts)
          .as("both module projects must belong to one LS process/workspace")
          .hasValue(1);
      assertThat(languageServerWorkingDirectory.get()).isNotNull();

      Path leftRoot = seam.projectRoot(session, "modules/left");
      Path rightRoot = seam.projectRoot(session, "modules/right");
      assertThat(leftRoot).isNotEqualTo(rightRoot);
      assertModuleProjection(leftRoot, leftSource, "left-api.jar", "right-api.jar", "JavaSE-1.8");
      assertModuleProjection(rightRoot, rightSource, "right-api.jar", "left-api.jar", "JavaSE-17");

      JsonNode leftSettings = languageServer.readProjectSettings(leftRoot.toUri().toString());
      JsonNode rightSettings = languageServer.readProjectSettings(rightRoot.toUri().toString());
      assertThat(leftSettings.path("org.eclipse.jdt.ls.core.vm.location").isTextual())
          .as("left project settings must report its selected target VM")
          .isTrue();
      assertThat(rightSettings.path("org.eclipse.jdt.ls.core.vm.location").isTextual())
          .as("right project settings must report its selected target VM")
          .isTrue();
      assertThat(
              Path.of(leftSettings.path("org.eclipse.jdt.ls.core.vm.location").asText())
                  .toRealPath())
          .as("left project VM readback must match only the Java 8 target")
          .isEqualTo(leftTargetJavaHome)
          .isNotEqualTo(rightTargetJavaHome);
      assertThat(
              Path.of(rightSettings.path("org.eclipse.jdt.ls.core.vm.location").asText())
                  .toRealPath())
          .as("right project VM readback must match only the Java 17 target")
          .isEqualTo(rightTargetJavaHome)
          .isNotEqualTo(leftTargetJavaHome);
      assertModuleClasspathReadback(
          leftSettings, leftRoot, leftJar, rightJar, "left project classpath readback");
      assertModuleClasspathReadback(
          rightSettings, rightRoot, rightJar, leftJar, "right project classpath readback");
    } finally {
      closeWorkspaceSession(session);
    }
  }

  @Test
  void catalogsAndCollectsAcrossModuleEdgeWithPerModuleTargetPlatforms() throws Exception {
    Path toolJavaHome = requiredJavaHome(TOOL_JAVA_HOME);
    Path commonTargetJavaHome = requiredJavaHome(TARGET_JAVA_HOME);
    Path webTargetJavaHome = requiredJavaHome(SECOND_TARGET_JAVA_HOME);
    Path distribution = requiredDirectory(DISTRIBUTION);
    assertThat(commonTargetJavaHome).isNotEqualTo(webTargetJavaHome).isNotEqualTo(toolJavaHome);
    assertThat(webTargetJavaHome).isNotEqualTo(toolJavaHome);

    String commonPath = "modules/common/src/main/java/fixture/Twin.java";
    String webPath = "modules/web/src/main/java/fixture/Twin.java";
    String commonSource =
        "package fixture;\n"
            + "final class CommonService {\n"
            + "  boolean platformProbe() { return java.util.Optional.of(\"common\").isPresent(); }\n"
            + "  String value() { return \"common\"; }\n"
            + "}\n";
    String webSource =
        "package fixture;\n"
            + "final class WebEntry {\n"
            + "  boolean platformProbe() { return java.util.Optional.of(\"web\").isEmpty(); }\n"
            + "  String entry() { return new CommonService().value(); }\n"
            + "}\n";
    VerifiedSourceTextSet completeSourceTexts =
        bridgeSourceTexts(commonPath, commonSource, webPath, webSource);
    VerifiedJavaProject commonProject =
        VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
            completeSourceTexts,
            List.of("modules/common/src/main/java"),
            List.of(commonPath),
            List.of(),
            "8");
    VerifiedJavaProject webProject =
        VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
            completeSourceTexts,
            List.of("modules/web/src/main/java"),
            List.of(webPath),
            List.of(),
            "17");
    assertThat(commonProject.sourceEntries()).containsExactly(commonPath);
    assertThat(webProject.sourceEntries()).containsExactly(webPath);

    JdtWorkspaceBinding binding =
        new JdtWorkspaceBinding(
            List.of(
                new JdtWorkspaceBinding.ModuleBinding(
                    "modules/common",
                    commonProject,
                    new JdtTargetRuntime("JavaSE-1.8", commonTargetJavaHome),
                    List.of()),
                new JdtWorkspaceBinding.ModuleBinding(
                    "modules/web",
                    webProject,
                    new JdtTargetRuntime("JavaSE-17", webTargetJavaHome),
                    List.of("modules/common"))));

    EffectiveEngineConfiguration.JdtConfiguration configuration =
        new EffectiveEngineConfiguration.JdtConfiguration(
            distribution,
            toolJavaHome,
            Duration.ofSeconds(120),
            Duration.ofSeconds(45),
            Duration.ofSeconds(10));
    AtomicInteger languageServerStarts = new AtomicInteger();
    AtomicReference<Path> languageServerWorkingDirectory = new AtomicReference<>();
    JdtProcessIsolation isolation =
        new JdtProcessIsolation(
            (command, workingDirectory) -> {
              languageServerStarts.incrementAndGet();
              languageServerWorkingDirectory.set(workingDirectory);
              return new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
            });
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);

    try (JdtWorkspaceSession session =
        JdtWorkspaceSession.open(binding, languageServer, isolation)) {
      assertThat(languageServerStarts)
          .as("both module projects must share one language-server process")
          .hasValue(1);
      assertThat(languageServerWorkingDirectory.get()).isNotNull();

      Path commonRoot = session.projectRoot("modules/common");
      Path webRoot = session.projectRoot("modules/web");
      assertThat(Files.readString(webRoot.resolve(".classpath")))
          .as("the web project must retain its explicit common project edge")
          .contains(binding.projectName("modules/common"));
      assertTargetVm(
          languageServer.readProjectSettings(commonRoot.toUri().toString()),
          commonTargetJavaHome,
          webTargetJavaHome,
          "common JDT LS target VM");
      assertTargetVm(
          languageServer.readProjectSettings(webRoot.toUri().toString()),
          webTargetJavaHome,
          commonTargetJavaHome,
          "web JDT LS target VM");

      JavaDeclarationCatalog catalog = session.catalog();
      assertThat(catalog.files())
          .as("the merged catalog must include exactly one owner for each module source")
          .containsExactlyInAnyOrder(commonPath, webPath);
      assertThat(catalog.fileDiagnostics().getOrDefault(webPath, ""))
          .as("the web Core parse must use its Java 17 target platform")
          .doesNotContain("isEmpty");

      List<JavaDeclarationCatalog.MethodDeclarationView> webEntries =
          catalog.methods().stream()
              .filter(method -> "entry".equals(method.name()))
              .filter(method -> webPath.equals(method.sourcePath()))
              .toList();
      assertThat(webEntries)
          .as("web methods=%s, file diagnostics=%s", catalog.methods(), catalog.fileDiagnostics())
          .hasSize(1);
      JavaDeclarationCatalog.MethodDeclarationView webEntry = webEntries.get(0);
      EntryCodeContext context =
          session.collect(
              new EntrySeed("web-entry", webEntry.methodKey(), webEntry.sourceRange(), "HTTP"));

      List<EntryCodeContext.MethodCode> entryMethods =
          context.methods().stream().filter(method -> "entry".equals(method.name())).toList();
      assertThat(entryMethods).hasSize(1);
      assertThat(entryMethods.get(0).source().path()).isEqualTo(webPath);
      assertThat(entryMethods.get(0).source().text())
          .contains("String entry() { return new CommonService().value(); }");
      List<EntryCodeContext.MethodCode> commonTargets =
          context.methods().stream()
              .filter(method -> "value".equals(method.name()))
              .filter(method -> commonPath.equals(method.source().path()))
              .toList();
      assertThat(commonTargets).hasSize(1);
      EntryCodeContext.MethodCode commonTarget = commonTargets.get(0);
      assertThat(commonTarget.source().text()).contains("String value() { return \"common\"; }");
      assertThat(
              context.calls().stream()
                  .filter(call -> call.callerMethodKey().equals(webEntry.methodKey()))
                  .flatMap(call -> call.targets().stream())
                  .map(EntryCodeContext.CallTarget::methodKey))
          .contains(commonTarget.methodKey());

      assertThatThrownBy(
              () ->
                  session.collect(
                      new EntrySeed(
                          "web-entry-wrong-range",
                          webEntry.methodKey(),
                          new SourceRange(0, 1, 1, 1),
                          "HTTP")))
          .as("collection must validate a catalog key against its exact source range")
          .isInstanceOf(RuntimeException.class);
      assertThatThrownBy(
              () ->
                  session.collect(
                      new EntrySeed(
                          "web-entry-unknown-key",
                          "method:not-in-the-merged-catalog",
                          webEntry.sourceRange(),
                          "HTTP")))
          .as("collection must not guess when the entry key is absent")
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void rejectsOneVerifiedSourcePathOwnedByTwoWorkspaceModules() {
    String leftSource =
        "package fixture; final class Twin { String value() { return \"left\"; } }\n";
    String rightSource =
        "package fixture; final class Twin { String value() { return \"right\"; } }\n";
    VerifiedSourceTextSet sourceTexts = twoModuleSourceTexts(leftSource, rightSource);
    String duplicatePath = "modules/left/src/main/java/fixture/Twin.java";
    VerifiedJavaProject firstProject =
        VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
            sourceTexts,
            List.of("modules/left/src/main/java"),
            List.of(duplicatePath),
            List.of(),
            "17");
    VerifiedJavaProject secondProject =
        VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
            sourceTexts,
            List.of("modules/left/src/main/java"),
            List.of(duplicatePath),
            List.of(),
            "17");
    JdtTargetRuntime runtime =
        new JdtTargetRuntime("JavaSE-17", Path.of(System.getProperty("java.home")));

    assertThatThrownBy(
            () ->
                new JdtWorkspaceBinding(
                    List.of(
                        new JdtWorkspaceBinding.ModuleBinding(
                            "modules/first", firstProject, runtime, List.of()),
                        new JdtWorkspaceBinding.ModuleBinding(
                            "modules/second", secondProject, runtime, List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one verified source path");
  }

  private void assertProjectSettingsReadback(boolean useLegacyJavaSeContainer) throws Exception {
    Path toolJavaHome = requiredJavaHome(TOOL_JAVA_HOME);
    Path targetJavaHome = requiredJavaHome(TARGET_JAVA_HOME);
    Path distribution = requiredDirectory(DISTRIBUTION);
    assertThat(targetJavaHome)
        .as("the target runtime must be distinct from the JDT process tool runtime")
        .isNotEqualTo(toolJavaHome);
    EffectiveEngineConfiguration.JdtConfiguration configuration =
        new EffectiveEngineConfiguration.JdtConfiguration(
            distribution,
            toolJavaHome,
            Duration.ofSeconds(90),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10));
    VerifiedJavaProject project = selfOwnedProject(TARGET_JAVA_VERSION);
    Object targetRuntime = targetRuntime("JavaSE-1.8", targetJavaHome);
    Object binding = projectBinding(project, targetRuntime);
    JdtProcessIsolation isolation =
        useLegacyJavaSeContainer
            ? isolationWithLegacyJavaSeContainer()
            : JdtProcessIsolation.system();
    JdtLanguageServerClient languageServer = new JdtLanguageServerClient(configuration, isolation);

    try (JdtProjectSession session = openBoundSession(binding, languageServer, isolation)) {
      assertThat(Files.readString(session.projectRoot().resolve(".classpath")))
          .as("the projected JDT execution environment must match the selected target runtime")
          .contains("org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8")
          .doesNotContain("JavaSE-8");
      JsonNode settings =
          languageServer.readProjectSettings(session.projectRoot().toUri().toString());
      String observedVm = settings.path("org.eclipse.jdt.ls.core.vm.location").asText();

      assertThat(Path.of(observedVm).toRealPath())
          .as("real LS settings=%s; target and tool runtimes intentionally differ", settings)
          .isEqualTo(targetJavaHome)
          .isNotEqualTo(toolJavaHome);
      JsonNode entries = settings.path("org.eclipse.jdt.ls.core.classpathEntries");
      assertThat(entries.isArray()).isTrue();
      List<String> entryPaths = new ArrayList<>();
      entries.forEach(
          entry -> {
            try {
              entryPaths.add(Path.of(entry.path("path").asText()).toRealPath().toString());
            } catch (IOException failure) {
              throw new AssertionError(
                  "JDT returned a non-readable owned classpath entry", failure);
            }
          });
      assertThat(entryPaths)
          .as("the project settings should expose the controlled source classpath entry")
          .contains(session.projectRoot().resolve("src/main/java").toRealPath().toString());
    }
  }

  private static JdtProcessIsolation isolationWithLegacyJavaSeContainer() {
    return new JdtProcessIsolation(
        (command, workingDirectory) -> {
          Path classpathFile = workingDirectory.resolve(".classpath");
          String classpath = Files.readString(classpathFile, StandardCharsets.UTF_8);
          if (classpath.contains("JavaSE-8")) {
            Files.writeString(
                classpathFile, classpath.replace("JavaSE-8", "JavaSE-1.8"), StandardCharsets.UTF_8);
          } else {
            assertThat(classpath).contains("JavaSE-1.8");
          }
          return new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        });
  }

  private static Object targetRuntime(String executionEnvironmentName, Path targetJdkHome) {
    try {
      Class<?> runtimeType =
          Class.forName("org.sourceanalysis.app.analysis.code.jdt.JdtTargetRuntime");
      return runtimeType
          .getConstructor(String.class, Path.class)
          .newInstance(executionEnvironmentName, targetJdkHome);
    } catch (ReflectiveOperationException missingOrInvalidSeam) {
      throw new AssertionError(
          "JDT target runtime binding must expose JdtTargetRuntime(String, Path)",
          missingOrInvalidSeam);
    }
  }

  private static Object projectBinding(VerifiedJavaProject project, Object targetRuntime) {
    try {
      Class<?> bindingType =
          Class.forName("org.sourceanalysis.app.analysis.code.jdt.JdtProjectBinding");
      return bindingType
          .getConstructor(VerifiedJavaProject.class, targetRuntime.getClass())
          .newInstance(project, targetRuntime);
    } catch (ReflectiveOperationException missingOrInvalidSeam) {
      throw new AssertionError(
          "JDT binding must expose JdtProjectBinding(VerifiedJavaProject, JdtTargetRuntime)",
          missingOrInvalidSeam);
    }
  }

  private static WorkspaceSeam requireWorkspaceSeam() {
    String packageName = "org.sourceanalysis.app.analysis.code.jdt.";
    try {
      Class<?> bindingType = Class.forName(packageName + "JdtWorkspaceBinding");
      Class<?> moduleBindingType = Class.forName(packageName + "JdtWorkspaceBinding$ModuleBinding");
      Class<?> sessionType = Class.forName(packageName + "JdtWorkspaceSession");
      return new WorkspaceSeam(bindingType, moduleBindingType, sessionType);
    } catch (ClassNotFoundException missingWorkspaceSeam) {
      throw new AssertionError(
          "one JDT LS workspace must accept multiple module bindings; "
              + "single-project JdtProjectSession cannot isolate module runtimes",
          missingWorkspaceSeam);
    }
  }

  private record WorkspaceSeam(
      Class<?> bindingType, Class<?> moduleBindingType, Class<?> sessionType) {

    Object module(
        String modulePath,
        VerifiedJavaProject project,
        Object targetRuntime,
        List<String> sourceModuleDependencyPaths)
        throws Exception {
      var constructor =
          moduleBindingType.getDeclaredConstructor(
              String.class, VerifiedJavaProject.class, targetRuntime.getClass(), List.class);
      constructor.setAccessible(true);
      return constructor.newInstance(
          modulePath, project, targetRuntime, sourceModuleDependencyPaths);
    }

    Object binding(List<Object> modules) throws Exception {
      var constructor = bindingType.getDeclaredConstructor(List.class);
      constructor.setAccessible(true);
      return constructor.newInstance(modules);
    }

    Object open(
        Object binding, JdtLanguageServerClient languageServer, JdtProcessIsolation isolation)
        throws Exception {
      try {
        var method =
            sessionType.getDeclaredMethod(
                "open", bindingType, JdtLanguageServerClient.class, JdtProcessIsolation.class);
        method.setAccessible(true);
        return method.invoke(null, binding, languageServer, isolation);
      } catch (java.lang.reflect.InvocationTargetException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof Exception exception) {
          throw exception;
        }
        if (cause instanceof Error error) {
          throw error;
        }
        throw new AssertionError("shared JDT workspace failed to open", cause);
      }
    }

    Path projectRoot(Object session, String modulePath) throws Exception {
      var method = sessionType.getDeclaredMethod("projectRoot", String.class);
      method.setAccessible(true);
      Object value = method.invoke(session, modulePath);
      if (value instanceof Path path) {
        return path;
      }
      throw new AssertionError("workspace projectRoot(modulePath) must return a Path");
    }
  }

  private static void closeWorkspaceSession(Object session) throws Exception {
    try {
      var method = session.getClass().getDeclaredMethod("close");
      method.setAccessible(true);
      method.invoke(session);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new AssertionError("shared JDT workspace cleanup failed", cause);
    }
  }

  private VerifiedSourceTextSet twoModuleSourceTexts(String leftSource, String rightSource) {
    String snapshotId = "snapshot:" + digest("self-owned-two-module-snapshot");
    String leftPath = "modules/left/src/main/java/fixture/Twin.java";
    String rightPath = "modules/right/src/main/java/fixture/Twin.java";
    String seed = snapshotId + digest(leftSource) + digest(rightSource);
    return new VerifiedSourceTextSet(
        snapshotId,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", seed),
        reference("source-inventory", seed),
        reference("verified-snapshot", seed),
        controls(seed),
        List.of(document(leftPath, leftSource), document(rightPath, rightSource)));
  }

  private VerifiedSourceTextSet bridgeSourceTexts(
      String commonPath, String commonSource, String webPath, String webSource) {
    String snapshotId = "snapshot:" + digest("self-owned-jdt-workspace-bridge");
    String seed =
        snapshotId
            + digest(commonPath)
            + digest(commonSource)
            + digest(webPath)
            + digest(webSource);
    return new VerifiedSourceTextSet(
        snapshotId,
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", seed),
        reference("source-inventory", seed),
        reference("verified-snapshot", seed),
        controls(seed),
        List.of(document(commonPath, commonSource), document(webPath, webSource)));
  }

  private static void assertTargetVm(
      JsonNode settings, Path expectedTarget, Path otherTarget, String description)
      throws IOException {
    assertThat(settings.path("org.eclipse.jdt.ls.core.vm.location").isTextual())
        .as("%s must expose its selected VM", description)
        .isTrue();
    assertThat(Path.of(settings.path("org.eclipse.jdt.ls.core.vm.location").asText()).toRealPath())
        .as(description)
        .isEqualTo(expectedTarget)
        .isNotEqualTo(otherTarget);
  }

  private static VerifiedJavaProject moduleProject(
      VerifiedSourceTextSet sourceTexts,
      String sourceRoot,
      String admittedSourcePath,
      Path classpath,
      String sourceLevel) {
    return VerifiedJavaProject.fromVerifiedSourceTextSetPartition(
        sourceTexts,
        List.of(sourceRoot),
        List.of(admittedSourcePath),
        List.of(classpath),
        sourceLevel);
  }

  private Path compileApiJar(String artifact, String methodName, String release) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler)
        .as("the self-owned fixture is compiled by the configured Java toolchain")
        .isNotNull();
    Path fixtureRoot = Files.createDirectories(temporaryDirectory.resolve(artifact));
    Path source = fixtureRoot.resolve("src/fixture/Api.java");
    Path classes = Files.createDirectories(fixtureRoot.resolve("classes"));
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package fixture; public final class Api { public int "
            + methodName
            + "() { return 1; } }\n",
        StandardCharsets.UTF_8);
    ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();
    int compileResult =
        compiler.run(
            null,
            compilerOutput,
            compilerOutput,
            "--release",
            release,
            "-d",
            classes.toString(),
            source.toString());
    assertThat(compileResult)
        .as("self-owned %s dependency compiles: %s", artifact, compilerOutput)
        .isZero();

    Path jar = fixtureRoot.resolve(artifact + ".jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
        Stream<Path> files = Files.walk(classes)) {
      for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
        output.putNextEntry(
            new JarEntry(
                classes
                    .relativize(file)
                    .toString()
                    .replace(file.getFileSystem().getSeparator(), "/")));
        Files.copy(file, output);
        output.closeEntry();
      }
    }
    return jar;
  }

  private static VerifiedSourceTextDocument document(String path, String source) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        new ArtifactId("file:" + digest(path)),
        path,
        "100644",
        "text/x-java-source",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static void assertModuleProjection(
      Path projectRoot,
      String expectedSource,
      String ownDependency,
      String otherDependency,
      String executionEnvironment)
      throws IOException {
    List<Path> javaFiles;
    try (Stream<Path> files = Files.walk(projectRoot)) {
      javaFiles =
          files
              .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
              .sorted()
              .toList();
    }
    assertThat(javaFiles)
        .as("a JDT module project may contain only its source-path partition")
        .hasSize(1);
    assertThat(Files.readString(javaFiles.get(0), StandardCharsets.UTF_8))
        .isEqualTo(expectedSource);
    String classpath = Files.readString(projectRoot.resolve(".classpath"), StandardCharsets.UTF_8);
    assertThat(classpath)
        .contains(ownDependency, executionEnvironment)
        .doesNotContain(otherDependency);
  }

  private static void assertModuleClasspathReadback(
      JsonNode settings,
      Path projectRoot,
      Path ownDependency,
      Path otherDependency,
      String description)
      throws IOException {
    JsonNode entries = settings.path("org.eclipse.jdt.ls.core.classpathEntries");
    assertThat(entries.isArray()).as(description).isTrue();
    List<Path> entryPaths = new ArrayList<>();
    for (JsonNode entry : entries) {
      String value = entry.path("path").asText();
      Path path = value.startsWith("file:") ? Path.of(java.net.URI.create(value)) : Path.of(value);
      entryPaths.add(path.toRealPath());
    }
    Path sourceRoot;
    try (Stream<Path> paths = Files.walk(projectRoot)) {
      List<Path> candidates =
          paths
              .filter(Files::isDirectory)
              .filter(
                  path ->
                      path.getFileName() != null
                          && path.getFileName().toString().equals("java")
                          && path.getParent() != null
                          && path.getParent().getFileName().toString().equals("main")
                          && path.getParent().getParent() != null
                          && path.getParent().getParent().getFileName().toString().equals("src"))
              .toList();
      assertThat(candidates).as(description + " source-root projection").hasSize(1);
      sourceRoot = candidates.get(0).toRealPath();
    }
    assertThat(entryPaths)
        .as(description)
        .contains(ownDependency.toRealPath(), sourceRoot)
        .doesNotContain(otherDependency.toRealPath());
  }

  private static JdtProjectSession openBoundSession(
      Object binding, JdtLanguageServerClient client, JdtProcessIsolation isolation)
      throws ReflectiveOperationException {
    try {
      java.lang.reflect.Method open =
          JdtProjectSession.class.getDeclaredMethod(
              "open", binding.getClass(), JdtLanguageServerClient.class, JdtProcessIsolation.class);
      open.setAccessible(true);
      return (JdtProjectSession) open.invoke(null, binding, client, isolation);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw new AssertionError("bound real JDT session failed", cause);
    }
  }

  private static Path requiredJavaHome(String property) throws IOException {
    Path javaHome = requiredDirectory(property);
    if (!Files.isExecutable(javaHome.resolve("bin").resolve("java"))) {
      throw missing(property, "bin/java is not executable");
    }
    return javaHome;
  }

  private static Path requiredDirectory(String property) throws IOException {
    String configured = System.getProperty(property);
    if (configured == null || configured.isBlank()) {
      throw missing(property, "property is blank");
    }
    Path directory;
    try {
      directory = Path.of(configured).toRealPath();
    } catch (IOException | RuntimeException failure) {
      throw new IllegalStateException(
          "Missing required real-jdt-it prerequisite: " + property + " is not a real path",
          failure);
    }
    if (!Files.isDirectory(directory)) {
      throw missing(property, "path is not a directory");
    }
    return directory;
  }

  private static VerifiedJavaProject selfOwnedProject(String targetVersion) {
    byte[] bytes = SOURCE.getBytes(StandardCharsets.UTF_8);
    String seed = SOURCE_PATH + ":" + digest(bytes);
    VerifiedSourceTextDocument document =
        new VerifiedSourceTextDocument(
            new ArtifactId("file:" + digest(SOURCE_PATH)),
            SOURCE_PATH,
            "100644",
            "text/x-java-source",
            bytes.length,
            new Sha256Digest(digest(bytes)),
            ImmutableBytes.copyOf(bytes));
    VerifiedSourceTextSet sourceTexts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest(seed),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", seed),
            reference("source-inventory", seed),
            reference("verified-snapshot", seed),
            controls(seed),
            List.of(document));
    return VerifiedJavaProject.fromVerifiedSourceTextSet(
        sourceTexts, List.of("src/main/java"), List.of(), targetVersion);
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
      throw new AssertionError(impossible);
    }
  }

  private static IllegalStateException missing(String property, String detail) {
    return new IllegalStateException(
        "Missing required real-jdt-it prerequisite: " + property + " (" + detail + ")");
  }
}
