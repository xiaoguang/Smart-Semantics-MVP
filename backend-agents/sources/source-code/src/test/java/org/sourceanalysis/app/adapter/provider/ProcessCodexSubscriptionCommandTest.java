package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Guards the process boundary's safe failure classification without invoking Codex. */
class ProcessCodexSubscriptionCommandTest {

  @TempDir Path temporaryDirectory;

  @Test
  void usesTheExplicitCodexHomeForChatgptAndStripsInheritedApiKeyAuthentication() throws Exception {
    Path codexHome = Files.createDirectory(temporaryDirectory.resolve("pro-home"));
    Path executable = temporaryDirectory.resolve("fake-codex-isolated");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$CODEX_HOME" != "$EXPECTED_CODEX_HOME" ]; then
          exit 71
        fi
        if [ -n "$OPENAI_API_KEY" ]; then
          exit 72
        fi
        if [ "$1" = "login" ]; then
          [ "$2" = "status" ] || exit 73
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        output=""
        forced=""
        while [ "$#" -gt 0 ]; do
          if [ "$1" = "--output-last-message" ]; then
            output="$2"
            shift 2
          else
            case "$1" in
              forced_login_method=*chatgpt*) forced="yes" ;;
            esac
            shift
          fi
        done
        [ "$forced" = "yes" ] || exit 74
        cat >/dev/null
        printf '{"answer":"ok"}' > "$output"
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }
    Path marker = temporaryDirectory.resolve("isolated-command-result.txt");
    String classPath = System.getProperty("surefire.test.class.path");
    assertThat(classPath).isNotBlank();
    ProcessBuilder helperBuilder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                classPath,
                CodexSubscriptionIsolationProbe.class.getName(),
                executable.toString(),
                codexHome.toString(),
                marker.toString())
            .redirectErrorStream(true)
            .redirectOutput(temporaryDirectory.resolve("isolation-probe.log").toFile());
    helperBuilder.environment().put("OPENAI_API_KEY", "must-not-reach-subscription-process");
    helperBuilder.environment().put("EXPECTED_CODEX_HOME", codexHome.toString());
    Path probeLog = temporaryDirectory.resolve("isolation-probe.log");
    helperBuilder.redirectOutput(probeLog.toFile());
    Process helper = helperBuilder.start();
    helper.getOutputStream().close();
    assertThat(helper.waitFor(10, TimeUnit.SECONDS)).isTrue();

    assertThat(helper.exitValue()).as(Files.readString(probeLog, StandardCharsets.UTF_8)).isZero();
    assertThat(Files.readString(marker, StandardCharsets.UTF_8)).isEqualTo("PASS");
  }

  @Test
  void returnsTheStructuredOutputProducedByALoggedInCliProcessWithoutInvokingALiveModel()
      throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-success");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        output=""
        while [ "$#" -gt 0 ]; do
          if [ "$1" = "--output-last-message" ]; then
            output="$2"
            shift 2
          else
            shift
          fi
        done
        if [ -z "$output" ]; then
          exit 41
        fi
        cat >/dev/null
        printf '{"answer":"ok"}' > "$output"
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    ProcessCodexSubscriptionCommand command = new ProcessCodexSubscriptionCommand();
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(executable, "gpt-5.6-luna", "high", Duration.ofSeconds(10));
    command.preflight(profile);
    ImmutableBytes response =
        command.execute(
            profile,
            "only structured output",
            ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)));

    assertThat(new String(response.copyToByteArray(), StandardCharsets.UTF_8))
        .isEqualTo("{\"answer\":\"ok\"}");
  }

  @Test
  void exposesOnlyABoundedModelConfigurationCategoryWhenCliWritesSensitiveLookingStderr()
      throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        echo 'model gpt-5.6-luna is unavailable; token=not-for-output' >&2
        exit 23
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    assertThatThrownBy(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .execute(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-luna", "high", Duration.ofSeconds(2)),
                        "untrusted prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("CODEX_SUBSCRIPTION_EXECUTION_FAILED:MODEL_CONFIGURATION")
        .satisfies(
            failure -> assertThat(failure.getMessage()).doesNotContain("not-for-output", "token="));
  }

  @Test
  void classifiesCliFailureReportedOnStandardOutputWithoutSurfacingItsRawText() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-stdout");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        echo 'model gpt-5.6-luna is unavailable; token=not-for-output'
        exit 23
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    assertThatThrownBy(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .execute(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-luna", "high", Duration.ofSeconds(2)),
                        "untrusted prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("CODEX_SUBSCRIPTION_EXECUTION_FAILED:MODEL_CONFIGURATION")
        .satisfies(
            failure -> assertThat(failure.getMessage()).doesNotContain("not-for-output", "token="));
  }

  @Test
  void rejectsAStatusThatReportsApiKeyAuthenticationDespiteExitZero() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-api-login");
    Files.writeString(
        executable,
        "#!/bin/sh\necho 'Logged in using an API key'\nexit 0\n",
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    assertThatThrownBy(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .preflight(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-luna", "high", Duration.ofSeconds(2))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("CODEX_SUBSCRIPTION_PREFLIGHT_FAILED");
  }
}
