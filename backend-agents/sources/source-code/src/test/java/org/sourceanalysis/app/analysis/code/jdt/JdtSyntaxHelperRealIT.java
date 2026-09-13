package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Real JDT Core helper integration coverage; this class is Failsafe-only. */
class JdtSyntaxHelperRealIT {

  @Test
  void realHelperReturnsCompleteJdtCoreSyntaxAndExactIdentity() {
    Path helper =
        Path.of(
                "tools",
                "jdt-syntax-helper",
                "target",
                "source-code-analysis-jdt-syntax-helper.jar")
            .toAbsolutePath();
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
            installedToolJavaHome(), helper, Duration.ofSeconds(20), Duration.ofSeconds(5))) {
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
