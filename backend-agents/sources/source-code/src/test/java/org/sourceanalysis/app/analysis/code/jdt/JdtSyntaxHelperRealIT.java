package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** Real JDT Core helper integration coverage; this class is Failsafe-only. */
class JdtSyntaxHelperRealIT {

  private static final String PLATFORM_PROBE =
      "package fixture;\n"
          + "final class PlatformProbe {\n"
          + "  java.lang.Object marker;\n"
          + "  boolean optionalIsEmpty() { return java.util.Optional.of(\"x\").isEmpty(); }\n"
          + "}\n";

  @Test
  void realHelperReturnsCompleteJdtCoreSyntaxAndExactIdentity() {
    Path helper =
        Path.of(
                "tools",
                "jdt-syntax-helper",
                "target",
                "source-code-analysis-jdt-syntax-helper.jar")
            .toAbsolutePath();
    Path toolJavaHome = installedToolJavaHome();
    JdtTargetRuntime targetRuntime =
        configuredTargetRuntime("sourceanalysis.jdt.testSecondTargetJavaHome", "JavaSE-17");
    String source =
        "class RegistrationService {\n"
            + "  User register(String name) {\n"
            + "    if (name.isBlank()) { throw new IllegalArgumentException(name); }\n"
            + "    else { repository.save(name); }\n"
            + "    return user;\n"
            + "  }\n"
            + "}\n";

    try (JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(
            toolJavaHome,
            helper,
            Duration.ofSeconds(20),
            Duration.ofSeconds(5),
            java.util.List.of(),
            java.util.List.of(),
            targetRuntime.targetJdkVersion(),
            targetRuntime.targetPlatformEntries())) {
      JdtSyntaxProtocol.Response response =
          client.describe("example/RegistrationService.java", "17", source);

      assertThat(response.sourceKey()).isEqualTo("example/RegistrationService.java");
      assertThat(response.sourceSha256()).isEqualTo(JdtSyntaxHelperClient.sha256(source));
      assertThat(response.declarations())
          .anySatisfy(
              declaration -> {
                if ("register".equals(declaration.name())) {
                  assertThat(declaration.sourceText()).contains("repository.save(name)");
                  assertThat(declaration.bodyPresent()).isTrue();
                }
              });
      assertThat(response.callSites())
          .extracting(JdtSyntaxProtocol.CallSiteView::expression)
          .contains("repository.save(name)");
      assertThat(response.controls())
          .extracting(JdtSyntaxProtocol.ControlView::kind)
          .contains("IF", "ELSE");
      assertThat(response.exits())
          .extracting(JdtSyntaxProtocol.ExitView::kind)
          .contains("THROW", "RETURN");
    }
  }

  @Test
  void java8CoreHelperUsesTheSelectedTargetsRtJarInsteadOfTheToolJdk() throws Exception {
    Path toolJavaHome = installedToolJavaHome();
    JdtTargetRuntime targetRuntime =
        configuredTargetRuntime("sourceanalysis.jdt.testTargetJavaHome", "JavaSE-1.8");
    Path expectedPlatform = targetRuntime.targetJdkHome().resolve("jre/lib/rt.jar").toRealPath();

    assertSelectedTargetPlatform(targetRuntime, expectedPlatform, toolJavaHome);

    JdtSyntaxProtocol.Response response = describePlatformProbe(toolJavaHome, targetRuntime);

    assertThat(
            response.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity().equals("ERROR")))
        .singleElement()
        .satisfies(diagnostic -> assertThat(diagnostic.message()).contains("isEmpty"));
    assertThat(response.diagnostics())
        .noneSatisfy(diagnostic -> assertThat(diagnostic.message()).contains("java.lang.Object"));
  }

  @Test
  void java17CoreHelperUsesTheSelectedTargetsJrtFsJarInsteadOfTheToolJdk() throws Exception {
    Path toolJavaHome = installedToolJavaHome();
    JdtTargetRuntime targetRuntime =
        configuredTargetRuntime("sourceanalysis.jdt.testSecondTargetJavaHome", "JavaSE-17");
    Path expectedPlatform = targetRuntime.targetJdkHome().resolve("lib/jrt-fs.jar").toRealPath();

    assertSelectedTargetPlatform(targetRuntime, expectedPlatform, toolJavaHome);

    JdtSyntaxProtocol.Response response = describePlatformProbe(toolJavaHome, targetRuntime);

    assertThat(response.diagnostics())
        .noneSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo("ERROR"));
    assertThat(response.diagnostics())
        .noneSatisfy(diagnostic -> assertThat(diagnostic.message()).contains("java.lang.Object"));
  }

  @Test
  void missingTargetPlatformFailsBeforeTheCoreHelperCanFallBackToTheToolJdk(@TempDir Path temp)
      throws IOException {
    for (String environment : List.of("JavaSE-1.8", "JavaSE-17")) {
      String targetVersion = environment.equals("JavaSE-1.8") ? "8" : "17";
      Path targetJavaHome =
          Files.createDirectories(temp.resolve("missing-target-jdk-" + targetVersion));
      Path targetJava = Files.createDirectories(targetJavaHome.resolve("bin")).resolve("java");
      Files.writeString(targetJava, "self-owned fixture; this file is never executed");
      assertThat(targetJava.toFile().setExecutable(true)).isTrue();
      JdtTargetRuntime targetRuntime = new JdtTargetRuntime(environment, targetJavaHome);

      assertThatThrownBy(
              () ->
                  JdtSyntaxHelperClient.start(
                      installedToolJavaHome(),
                      temp.resolve("deliberately-absent-helper-" + targetVersion + ".jar"),
                      Duration.ofSeconds(20),
                      Duration.ofSeconds(5),
                      List.of(),
                      List.of(),
                      targetRuntime.targetJdkVersion(),
                      targetRuntime.targetPlatformEntries()))
          .isInstanceOfSatisfying(
              CodeEngineException.class,
              failure ->
                  assertThat(failure.code())
                      .isEqualTo(CodeEngineException.ENGINE_CONFIGURATION_INVALID));
    }
  }

  private static JdtSyntaxProtocol.Response describePlatformProbe(
      Path toolJavaHome, JdtTargetRuntime targetRuntime) {
    try (JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(
            toolJavaHome,
            JdtSyntaxHelperArtifact.locate(),
            Duration.ofSeconds(20),
            Duration.ofSeconds(5),
            List.of(),
            List.of(),
            targetRuntime.targetJdkVersion(),
            targetRuntime.targetPlatformEntries())) {
      String languageLevel =
          targetRuntime.targetJdkVersion().equals("8") ? "1.8" : targetRuntime.targetJdkVersion();
      return client.describe("fixture/PlatformProbe.java", languageLevel, PLATFORM_PROBE);
    }
  }

  private static void assertSelectedTargetPlatform(
      JdtTargetRuntime targetRuntime, Path expectedPlatform, Path toolJavaHome) {
    assertThat(targetRuntime.targetJdkHome()).isNotEqualTo(toolJavaHome);
    assertThat(expectedPlatform).isRegularFile();
    assertThat(expectedPlatform.startsWith(toolJavaHome)).isFalse();
    assertThat(targetRuntime.targetPlatformEntries()).containsExactly(expectedPlatform);
    assertThat(targetRuntime.targetPlatformEntries())
        .noneMatch(path -> path.startsWith(toolJavaHome));
  }

  private static JdtTargetRuntime configuredTargetRuntime(String property, String environment) {
    String configured = System.getProperty(property);
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException("Missing required real-jdt-it prerequisite: " + property);
    }
    return new JdtTargetRuntime(environment, Path.of(configured));
  }

  private static Path installedToolJavaHome() {
    String configured = System.getProperty("sourceanalysis.jdt.testJavaHome");
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "Missing required real-jdt-it prerequisite: sourceanalysis.jdt.testJavaHome is blank");
    }
    Path javaHome = Path.of(configured);
    if (!Files.isExecutable(javaHome.resolve("bin").resolve("java"))) {
      throw new IllegalStateException(
          "Missing required real-jdt-it prerequisite: sourceanalysis.jdt.testJavaHome/bin/java is not executable");
    }
    return javaHome;
  }
}
