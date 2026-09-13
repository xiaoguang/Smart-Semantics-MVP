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
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

class JdtSyntaxHelperClientTest {

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
          "{\"protocolVersion\":\""
              + request.path("protocolVersion").asText()
              + "\","
              + "\"requestId\":\""
              + requestId
              + "\","
              + "\"sourceKey\":\""
              + request.path("sourceKey").asText()
              + "\","
              + "\"sourceSha256\":\""
              + request.path("sourceSha256").asText()
              + "\","
              + "\"packageName\":null,\"imports\":[],\"declarations\":[],\"annotations\":[],"
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
