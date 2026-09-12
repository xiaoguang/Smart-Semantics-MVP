package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

class JdtSyntaxHelperClientTest {

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

  @Test
  void rejectsWrongResponseIdentityAndUnknownFields() {
    assertThatThrownBy(() -> invokeFake("wrong-id", Duration.ofSeconds(5)))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID));
    assertThatThrownBy(() -> invokeFake("unknown-field", Duration.ofSeconds(5)))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.JDT_SYNTAX_PROTOCOL_INVALID));
  }

  @Test
  void timeoutAndUnexpectedNonzeroExitAreDifferentFailures() {
    assertThatThrownBy(() -> invokeFake("timeout", Duration.ofMillis(100)))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code()).isEqualTo(CodeEngineException.JDT_SYNTAX_TIMEOUT));
    assertThatThrownBy(() -> invokeFake("nonzero", Duration.ofSeconds(5)))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.JDT_SYNTAX_PROCESS_FAILED));
  }

  @Test
  void closeForceKillsAHelperThatIgnoresEndOfInput() {
    JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(
            fakeCommand("ignore-close"), Duration.ofSeconds(5), Duration.ofMillis(100));
    client.describe("example/Example.java", "17", "class Example {}\n");

    assertThatThrownBy(client::close)
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.JDT_SYNTAX_SHUTDOWN_TIMEOUT));
  }

  private static JdtSyntaxProtocol.Response invokeFake(String mode, Duration timeout) {
    try (JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(fakeCommand(mode), timeout, Duration.ofSeconds(2))) {
      return client.describe("example/Example.java", "17", "class Example {}\n");
    }
  }

  private static List<String> fakeCommand(String mode) {
    return List.of(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("java.class.path"),
        FakeSyntaxHelper.class.getName(),
        mode);
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

  public static final class FakeSyntaxHelper {
    private static final ObjectMapper JSON = new ObjectMapper();

    private FakeSyntaxHelper() {}

    public static void main(String[] args) throws Exception {
      String mode = args[0];
      if ("nonzero".equals(mode)) {
        System.err.print("deliberate helper failure");
        System.exit(7);
      }
      BufferedReader input =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String line = input.readLine();
      if ("timeout".equals(mode)) {
        Thread.sleep(60_000L);
        return;
      }
      JsonNode request = JSON.readTree(line);
      String requestId =
          "wrong-id".equals(mode) ? "different-request" : request.path("requestId").asText();
      String response =
          "{\"protocolVersion\":\"jdt-syntax-v1\","
              + "\"requestId\":\""
              + requestId
              + "\","
              + "\"sourceKey\":\""
              + request.path("sourceKey").asText()
              + "\","
              + "\"sourceSha256\":\""
              + request.path("sourceSha256").asText()
              + "\","
              + "\"packageName\":null,\"imports\":[],\"declarations\":[],"
              + "\"callSites\":[],\"controls\":[],\"exits\":[],\"diagnostics\":[]"
              + ("unknown-field".equals(mode) ? ",\"unexpected\":true" : "")
              + "}";
      System.out.println(response);
      System.out.flush();
      if ("ignore-close".equals(mode)) {
        Thread.sleep(60_000L);
      }
    }
  }
}
