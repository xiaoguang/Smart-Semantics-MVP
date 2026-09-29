package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.JavaReadinessPreparation;

/** Direct contracts for the v2 technical configuration's official-output handoff. */
class TechnicalCompilationInputConfigurationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsTheRetiredAutoMavenModeInsteadOfStartingJavaDependencyResolution() throws Exception {
    Path config = temporaryDirectory.resolve("retired-auto-maven.yaml");
    Files.writeString(config, autoMavenConfiguration(), StandardCharsets.UTF_8);

    Object loaded = loadConfiguration(config);
    try {
      invoke(loaded, "technicalJavaConfig");
      fail("AUTO_MAVEN was accepted; Java must consume a named java.compilationInput instead");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      assertThat(cause)
          .hasMessageContaining("unsupported")
          .hasMessageNotContaining("resolver")
          .hasMessageNotContaining("download")
          .hasMessageNotContaining("repository");
    }
  }

  @Test
  void parsesV2OfficialOutputLocationsWithoutCallerDeclaredJavaSemantics() throws Exception {
    Path projectDirectory = Files.createDirectory(temporaryDirectory.resolve("project"));
    Path config = temporaryDirectory.resolve("external-input.yaml");
    Files.writeString(config, externalInputConfiguration(projectDirectory), StandardCharsets.UTF_8);

    Object loaded = loadConfiguration(config);
    Object javaConfig;
    try {
      javaConfig = invoke(loaded, "technicalJavaConfig");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      fail("INTERFACE_MISSING: java configuration must expose java.compilationInput", cause);
      return;
    }

    JavaReadinessPreparation.CompilationInput selected =
        (JavaReadinessPreparation.CompilationInput) accessor(javaConfig, "compilationInput");
    assertThat(selected.projectDirectory())
        .isEqualTo(projectDirectory.toAbsolutePath().normalize());
    assertThat(selected.modules()).hasSize(1);
    assertThat(selected.modules().get(0).modulePath()).isEqualTo(".");
    assertThat(selected.modules().get(0).classpathFile())
        .isEqualTo(projectDirectory.resolve("classpath.txt"));
    assertThat(selected.modules().get(0).effectivePomFile())
        .isEqualTo(projectDirectory.resolve("effective-pom.xml"));
  }

  @Test
  void rejectsV2CompilationInputThatSuppliesRetiredSemanticFields() throws Exception {
    Path projectDirectory = Files.createDirectory(temporaryDirectory.resolve("project"));
    Path config = temporaryDirectory.resolve("v2-retired-semantic-fields.yaml");
    Files.writeString(
        config, v2ConfigurationWithRetiredSemanticFields(projectDirectory), StandardCharsets.UTF_8);

    Object loaded = loadConfiguration(config);
    try {
      invoke(loaded, "technicalJavaConfig");
      fail("v2 compilation input accepted caller-declared derived Java semantics");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      assertThat(cause).hasMessageContaining("CONFIGURATION_INVALID");
    }
  }

  private Object loadConfiguration(Path config) throws Exception {
    Class<?> configurationType =
        Class.forName(
            "org.sourceanalysis.app.adapter.cli.TechnicalAnalysisConfiguredRuntime$Configuration");
    Method load = configurationType.getDeclaredMethod("load", Path.class, String.class);
    load.setAccessible(true);
    try {
      return load.invoke(null, config, "collect-code");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      fail(
          "technical configuration could not be loaded before java input decoding: " + cause,
          cause);
      return null;
    }
  }

  private static Object invoke(Object instance, String methodName) throws Exception {
    Method method = instance.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    return method.invoke(instance);
  }

  private static Object accessor(Object instance, String name) throws Exception {
    Method method = instance.getClass().getDeclaredMethod(name);
    method.setAccessible(true);
    return method.invoke(instance);
  }

  private String externalInputConfiguration(Path projectDirectory) {
    return """
        schemaVersion: technical-analysis-config-v2
        source:
          preparationRunId: analysis-run:%s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput:
            projectDirectory: %s
            modules:
              - modulePath: "."
                classpathFile: %s
                classpathSeparator: ":"
                effectivePomFile: %s
                targetJavaHome: %s
        frontend:
          enabled: false
        """
        .formatted(
            "a".repeat(64),
            yamlPath("analysis-store"),
            yamlPath("prepared-source-archive"),
            yamlPath("source-preparation-policy.json"),
            yamlPath("technical-policy.json"),
            yamlPath(projectDirectory),
            yamlPath(projectDirectory.resolve("classpath.txt")),
            yamlPath(projectDirectory.resolve("effective-pom.xml")),
            yamlPath(Path.of(System.getProperty("java.home"))));
  }

  private String autoMavenConfiguration() {
    return """
        schemaVersion: technical-analysis-config-v2
        source:
          preparationRunId: analysis-run:%s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput:
            projectDirectory: %s
            modules:
              - modulePath: "."
                classpathFile: %s
                classpathSeparator: ":"
                effectivePomFile: %s
                targetJavaHome: %s
          dependencyPreparation:
            mode: AUTO_MAVEN
            rootPom: pom.xml
            modules: ALL_ACTIVE_REACTOR_MODULES
            sourceSet: main
            profileActivation: MAVEN_DEFAULT_WITH_RECORDED_CONTEXT
            activeProfiles: []
            inactiveProfiles: []
            userProperties: {}
            buildJdkHome: %s
            localRepository: %s
            network: LOCAL_ONLY
            allowedRepositories: []
        frontend:
          enabled: false
        """
        .formatted(
            "a".repeat(64),
            yamlPath("analysis-store"),
            yamlPath("prepared-source-archive"),
            yamlPath("source-preparation-policy.json"),
            yamlPath("technical-policy.json"),
            yamlPath("project"),
            yamlPath("project/classpath.txt"),
            yamlPath("project/effective-pom.xml"),
            yamlPath(Path.of(System.getProperty("java.home"))),
            yamlPath(Path.of(System.getProperty("java.home"))),
            yamlPath("maven-cache"));
  }

  private String v2ConfigurationWithRetiredSemanticFields(Path projectDirectory) {
    return """
        schemaVersion: technical-analysis-config-v2
        source:
          preparationRunId: analysis-run:%s
        storage:
          root: %s
          preparedSourceArchive: %s
          sourcePreparationPolicyRegistry: %s
          artifactPolicyRegistry: %s
        java:
          compilationInput:
            projectDirectory: %s
            modules:
              - modulePath: "."
                classpathFile: %s
                classpathSeparator: ":"
                effectivePomFile: %s
                targetJavaHome: %s
                sourceRoots: ["src/main/java"]
                source: "17"
                target: "17"
                release: "17"
                sourceModuleDependencies: []
                status: "SUCCEEDED"
        frontend:
          enabled: false
        """
        .formatted(
            "a".repeat(64),
            yamlPath("analysis-store"),
            yamlPath("prepared-source-archive"),
            yamlPath("source-preparation-policy.json"),
            yamlPath("technical-policy.json"),
            yamlPath(projectDirectory),
            yamlPath(projectDirectory.resolve("classpath.txt")),
            yamlPath(projectDirectory.resolve("effective-pom.xml")),
            yamlPath(projectDirectory.resolve("jdk")));
  }

  private String yamlPath(String value) {
    return yamlPath(Path.of(value));
  }

  private String yamlPath(Path path) {
    Path absolute = path.isAbsolute() ? path : temporaryDirectory.resolve(path);
    return "\"" + absolute.toAbsolutePath().normalize().toString().replace("\\", "\\\\") + "\"";
  }
}
