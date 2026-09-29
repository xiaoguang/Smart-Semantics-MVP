package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  void realHelperV4ProjectsBindingForNestedOverloadsExternalAndInterfaceCalls() {
    assertThat(JdtSyntaxProtocol.VERSION).isEqualTo("jdt-syntax-v4");
    RecordComponent callBinding =
        Arrays.stream(JdtSyntaxProtocol.CallSiteView.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    RecordComponent declarationBinding =
        Arrays.stream(JdtSyntaxProtocol.Declaration.class.getRecordComponents())
            .filter(component -> "binding".equals(component.getName()))
            .findFirst()
            .orElse(null);
    assertThat(callBinding).isNotNull();
    assertThat(declarationBinding).isNotNull();
    assertThat(callBinding.getType()).isEqualTo(declarationBinding.getType());
    assertThat(Arrays.stream(callBinding.getType().getRecordComponents()))
        .extracting(RecordComponent::getName)
        .contains("state", "declarationKey", "declaringTypeKey", "typeOrigin", "displayIdentity");

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
        "interface Service { void save(int value); void save(String value); }\n"
            + "final class Impl implements Service { public void save(int value) {} public void save(String value) {} }\n"
            + "final class Controller { void run(Service service, PageDomain page, String value) { page.setPageSize(Convert.toInt(value)); service.save(1); service.save(\"x\"); String.valueOf(value); } }\n"
            + "final class PageDomain { void setPageSize(int value) {} }\n"
            + "final class Convert { static int toInt(String value) { return 1; } }\n";
    try (JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(
            toolJavaHome,
            helper,
            Duration.ofSeconds(20),
            Duration.ofSeconds(5),
            List.of(),
            List.of(),
            targetRuntime.targetJdkVersion(),
            targetRuntime.targetPlatformEntries())) {
      JdtSyntaxProtocol.Response response =
          client.describe("fixture/Controller.java", "17", source);
      assertThat(response.callSites())
          .extracting(JdtSyntaxProtocol.CallSiteView::expression)
          .contains(
              "page.setPageSize(Convert.toInt(value))",
              "Convert.toInt(value)",
              "service.save(1)",
              "service.save(\"x\")",
              "String.valueOf(value)");
      assertThat(response.declarations())
          .extracting(JdtSyntaxProtocol.Declaration::name)
          .contains("save", "setPageSize", "toInt");

      Map<String, Object> bindingsByExpression =
          response.callSites().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      JdtSyntaxProtocol.CallSiteView::expression,
                      JdtSyntaxHelperRealIT::binding,
                      (first, second) -> first));
      Object outer = bindingsByExpression.get("page.setPageSize(Convert.toInt(value))");
      Object inner = bindingsByExpression.get("Convert.toInt(value)");
      Object intOverload = bindingsByExpression.get("service.save(1)");
      Object stringOverload = bindingsByExpression.get("service.save(\"x\")");
      Object external = bindingsByExpression.get("String.valueOf(value)");
      assertResolvedBinding(outer);
      assertResolvedBinding(inner);
      assertResolvedBinding(intOverload);
      assertResolvedBinding(stringOverload);
      assertResolvedBinding(external);
      assertThat(component(outer, "declarationKey"))
          .isNotEqualTo(component(inner, "declarationKey"));
      assertThat(component(outer, "declaringTypeKey"))
          .isNotEqualTo(component(inner, "declaringTypeKey"));
      assertThat(component(intOverload, "declarationKey"))
          .isNotEqualTo(component(stringOverload, "declarationKey"));
      assertThat(component(intOverload, "declaringTypeKey"))
          .isEqualTo(component(stringOverload, "declaringTypeKey"));
      assertThat(component(external, "typeOrigin")).isEqualTo("BINARY");

      List<Object> declarations =
          response.declarations().stream()
              .filter(declaration -> "METHOD".equals(declaration.kind()))
              .map(JdtSyntaxHelperRealIT::binding)
              .toList();
      assertThat(declarations).allSatisfy(JdtSyntaxHelperRealIT::assertResolvedBinding);
      assertThat(declarations.stream().map(value -> component(value, "declarationKey")).toList())
          .contains(component(outer, "declarationKey"), component(inner, "declarationKey"));
      assertThat(declarations.stream().map(value -> component(value, "declarationKey")).toList())
          .contains(
              component(intOverload, "declarationKey"),
              component(stringOverload, "declarationKey"));
      Map<String, Object> declarationsByKey =
          declarations.stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      value -> (String) component(value, "declarationKey"),
                      value -> value,
                      (first, second) -> first));
      assertThat(declarationsByKey.get(component(outer, "declarationKey"))).isNotNull();
      assertThat(declarationsByKey.get(component(inner, "declarationKey"))).isNotNull();
      assertThat(
              component(
                  declarationsByKey.get(component(outer, "declarationKey")), "declaringTypeKey"))
          .isEqualTo(component(outer, "declaringTypeKey"));
      assertThat(
              component(
                  declarationsByKey.get(component(inner, "declarationKey")), "declaringTypeKey"))
          .isEqualTo(component(inner, "declaringTypeKey"));
    }
  }

  private static Object binding(JdtSyntaxProtocol.CallSiteView call) {
    try {
      Method accessor = call.getClass().getDeclaredMethod("binding");
      accessor.setAccessible(true);
      return accessor.invoke(call);
    } catch (ReflectiveOperationException unavailable) {
      throw new AssertionError("jdt-syntax-v4 call binding accessor is missing", unavailable);
    }
  }

  private static Object binding(JdtSyntaxProtocol.Declaration declaration) {
    try {
      Method accessor = declaration.getClass().getDeclaredMethod("binding");
      accessor.setAccessible(true);
      return accessor.invoke(declaration);
    } catch (ReflectiveOperationException unavailable) {
      throw new AssertionError(
          "jdt-syntax-v4 declaration binding accessor is missing", unavailable);
    }
  }

  private static void assertResolvedBinding(Object binding) {
    assertThat(binding).isNotNull();
    assertThat(component(binding, "state")).isEqualTo("RESOLVED");
    assertThat(component(binding, "declarationKey")).isInstanceOf(String.class);
    assertThat((String) component(binding, "declarationKey")).isNotBlank();
    assertThat(component(binding, "declaringTypeKey")).isInstanceOf(String.class);
    assertThat((String) component(binding, "declaringTypeKey")).isNotBlank();
    assertThat(component(binding, "typeOrigin")).isInstanceOf(String.class);
    assertThat((String) component(binding, "typeOrigin")).isNotBlank();
    assertThat(component(binding, "displayIdentity")).isInstanceOf(String.class);
    assertThat((String) component(binding, "displayIdentity")).isNotBlank();
  }

  private static Object component(Object binding, String name) {
    try {
      Method accessor = binding.getClass().getDeclaredMethod(name);
      accessor.setAccessible(true);
      Object value = accessor.invoke(binding);
      return value instanceof Enum<?> enumValue ? enumValue.name() : value;
    } catch (ReflectiveOperationException unavailable) {
      throw new AssertionError("jdt-syntax-v4 binding component is missing: " + name, unavailable);
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
