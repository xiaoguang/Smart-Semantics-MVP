package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
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
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured);
    }
    try {
      Path macJavaHome = Path.of("/usr/libexec/java_home");
      if (java.nio.file.Files.isExecutable(macJavaHome)) {
        Process selection = new ProcessBuilder(macJavaHome.toString(), "-v", "21+").start();
        assertThat(selection.waitFor(10, TimeUnit.SECONDS)).isTrue();
        if (selection.exitValue() == 0) {
          return Path.of(
              new String(selection.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                  .strip());
        }
      }
      Process probe =
          new ProcessBuilder("java", "-XshowSettings:properties", "-version")
              .redirectErrorStream(true)
              .start();
      assertThat(probe.waitFor(10, TimeUnit.SECONDS)).isTrue();
      String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return output
          .lines()
          .map(String::strip)
          .filter(line -> line.startsWith("java.home ="))
          .map(line -> Path.of(line.substring(line.indexOf('=') + 1).strip()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("PATH Java did not report java.home"));
    } catch (Exception failure) {
      throw new AssertionError(
          "A Java 21+ tool runtime is required for the JDT helper test", failure);
    }
  }
}
