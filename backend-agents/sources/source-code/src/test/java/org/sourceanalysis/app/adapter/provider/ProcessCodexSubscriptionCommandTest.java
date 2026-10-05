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
  void rejectsSparseCliOutputBeyondTheRequestBudgetWithoutReadingItIntoTheProbeHeap()
      throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-oversized-output");
    Path executablePid = temporaryDirectory.resolve("fake-codex-oversized-output.pid");
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
        [ -n "$output" ] || exit 41
        cat >/dev/null
        printf '%s\n' "$$" > "$CODEX_TEST_PID_MARKER"
        exec dd if=/dev/zero of="$output" bs=1048576 count=1 seek=80 2>/dev/null
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    Path outcome = temporaryDirectory.resolve("oversized-output-probe.outcome");
    Path probeLog = temporaryDirectory.resolve("oversized-output-probe.log");
    String classPath = System.getProperty("surefire.test.class.path");
    assertThat(classPath).isNotBlank();
    ProcessBuilder probeBuilder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx64m",
                "-cp",
                classPath,
                CodexSubscriptionOutputBoundsProbe.class.getName(),
                executable.toString(),
                outcome.toString())
            .redirectErrorStream(true)
            .redirectOutput(probeLog.toFile());
    probeBuilder.environment().put("CODEX_TEST_PID_MARKER", executablePid.toString());
    Process probe = probeBuilder.start();
    boolean completedWithinWatchdog;
    boolean probeStopped;
    boolean fakeCodexAliveAfterProbe;
    try {
      completedWithinWatchdog = probe.waitFor(12, TimeUnit.SECONDS);
      if (!completedWithinWatchdog) {
        probe.destroyForcibly();
      }
      probeStopped = completedWithinWatchdog || probe.waitFor(2, TimeUnit.SECONDS);
      fakeCodexAliveAfterProbe = fakeCodexIsAlive(executablePid);
    } finally {
      if (probe.isAlive()) {
        probe.destroyForcibly();
        probe.waitFor(2, TimeUnit.SECONDS);
      }
      terminateFakeCodex(executablePid);
    }

    assertThat(completedWithinWatchdog)
        .as(
            "bounded-output probe must complete without a parent-side heap failure; %s",
            readIfPresent(probeLog))
        .isTrue();
    assertThat(probeStopped).isTrue();
    assertThat(probe.exitValue()).as(readIfPresent(probeLog)).isZero();
    assertThat(Files.readString(outcome, StandardCharsets.UTF_8).split("\\t", -1))
        .containsExactly("FAILURE", "RESPONSE_BUDGET_EXCEEDED", "true", "true", "NO_RAW");
    assertThat(fakeCodexAliveAfterProbe).isFalse();
  }

  @Test
  void appliesTheDeadlineWhileWritingWhenCodexDoesNotReadTheLargePrompt() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-does-not-read-stdin");
    Path executablePid = temporaryDirectory.resolve("fake-codex.pid");
    Path outcome = temporaryDirectory.resolve("deadline-probe.outcome");
    Path probeLog = temporaryDirectory.resolve("deadline-probe.log");
    Files.writeString(
        executable,
        "#!/bin/sh\nprintf '%s\\n' \"$$\" > \"$CODEX_TEST_PID_MARKER\"\nexec sleep 30\n",
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    String classPath = System.getProperty("surefire.test.class.path");
    assertThat(classPath).isNotBlank();
    ProcessBuilder probeBuilder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                classPath,
                CodexSubscriptionDeadlineProbe.class.getName(),
                executable.toString(),
                outcome.toString())
            .redirectErrorStream(true)
            .redirectOutput(probeLog.toFile());
    probeBuilder.environment().put("CODEX_TEST_PID_MARKER", executablePid.toString());
    Process probe = probeBuilder.start();
    boolean completedWithinWatchdog;
    boolean probeStopped;
    boolean fakeCodexAliveAfterProbe;
    try {
      probe.getOutputStream().close();
      completedWithinWatchdog = probe.waitFor(5, TimeUnit.SECONDS);
      if (!completedWithinWatchdog) {
        probe.destroyForcibly();
      }
      probeStopped = completedWithinWatchdog || probe.waitFor(2, TimeUnit.SECONDS);
      fakeCodexAliveAfterProbe = fakeCodexIsAlive(executablePid);
    } finally {
      if (probe.isAlive()) {
        probe.destroyForcibly();
        probe.waitFor(2, TimeUnit.SECONDS);
      }
      terminateFakeCodex(executablePid);
    }

    assertThat(completedWithinWatchdog)
        .as("parent watchdog must contain a blocked child JVM; %s", readIfPresent(probeLog))
        .isTrue();
    assertThat(probeStopped).isTrue();
    assertThat(probe.exitValue()).as(readIfPresent(probeLog)).isZero();
    String[] fields = Files.readString(outcome, StandardCharsets.UTF_8).split("\\t", -1);
    assertThat(fields).hasSize(5);
    assertThat(fields[0]).isEqualTo("REQUEST_TIMEOUT");
    assertThat(fields[1]).isEqualTo("true");
    assertThat(fields[2]).isEqualTo("true");
    assertThat(fields[3]).isEqualTo("CODEX_SUBSCRIPTION_TIMEOUT");
    assertThat(Long.parseLong(fields[4])).isBetween(1L, 3_000L);
    assertThat(fakeCodexAliveAfterProbe).isFalse();
  }

  @Test
  void treatsFreeTextStderrAsUnknownWhileKeepingItsSensitiveContentPrivate() throws Exception {
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
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.reasonCode()).isEqualTo("UNKNOWN");
              assertThat(classified.requestStarted()).isTrue();
              assertThat(classified.requestEnded()).isTrue();
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN")
                  .doesNotContain("not-for-output", "token=");
            });
  }

  @Test
  void treatsFreeTextStandardOutputAsUnknownWithoutSurfacingItsRawText() throws Exception {
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
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.reasonCode()).isEqualTo("UNKNOWN");
              assertThat(classified.requestStarted()).isTrue();
              assertThat(classified.requestEnded()).isTrue();
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN")
                  .doesNotContain("not-for-output", "token=");
            });
  }

  @Test
  void preservesTheEndOfLongCliStderrInPrivateFailureOutput() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-long-stderr");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        i=0
        while [ "$i" -lt 5000 ]; do
          printf x >&2
          i=$((i + 1))
        done
        echo ' terminal cause: schema rejected' >&2
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
                            executable, "gpt-6-luna", "high", Duration.ofSeconds(10)),
                        "untrusted prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN")
                  .doesNotContain("schema rejected");
              assertThat(
                      new String(
                          classified.rawResponse().orElseThrow().copyToByteArray(),
                          StandardCharsets.UTF_8))
                  .contains("terminal cause: schema rejected");
            });
  }

  @Test
  void doesNotInferProviderCapacityFromFreeTextStderr() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-capacity");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        echo 'Selected model is at capacity. Please try a different model.' >&2
        exit 1
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
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN");
              assertThat(classified.reasonCode()).isEqualTo("UNKNOWN");
              assertThat(classified.requestStarted()).isTrue();
              assertThat(classified.requestEnded()).isTrue();
            });
  }

  @Test
  void doesNotMapFreeTextToRetryOrBindingReasonsWithoutExposingCliDiagnostics() throws Exception {
    assertFreeTextFailureIsUnknown("rate limit exceeded; token=private", "rate-limit");
    assertFreeTextFailureIsUnknown(
        "service temporarily unavailable; token=private", "provider-capacity");
    assertFreeTextFailureIsUnknown("quota exhausted; token=private", "quota");
    assertFreeTextFailureIsUnknown(
        "authentication credential unauthorized; token=private", "authentication");
    assertFreeTextFailureIsUnknown(
        "model reasoning output-schema is invalid; token=private", "configuration");
  }

  @Test
  void doesNotInferAnInputContextRejectionFromFreeText() throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-context");
    Files.writeString(
        executable,
        "#!/bin/sh\necho 'Input exceeds the maximum context length; token=private' >&2\nexit 1\n",
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    assertThatThrownBy(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .execute(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-terra", "xhigh", Duration.ofSeconds(2)),
                        "untrusted prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              assertThat(((StructuredModelProviderFailure) failure).reasonCode())
                  .isEqualTo("UNKNOWN");
              assertThat(failure.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN")
                  .doesNotContain("token=private");
            });
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

  private void assertFreeTextFailureIsUnknown(String diagnostic, String fixtureName)
      throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex-" + fixtureName);
    Files.writeString(
        executable,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"login\" ]; then\n"
            + "  echo 'Logged in using ChatGPT'\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo '"
            + diagnostic
            + "' >&2\n"
            + "exit 1\n",
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    assertThatThrownBy(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .execute(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-terra", "xhigh", Duration.ofSeconds(2)),
                        "untrusted prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN");
              assertThat(classified.reasonCode()).isEqualTo("UNKNOWN");
              assertThat(classified.requestStarted()).isTrue();
              assertThat(classified.requestEnded()).isTrue();
              assertThat(classified.getMessage()).doesNotContain("token=private");
            });
  }

  private static void terminateFakeCodex(Path pidFile) throws Exception {
    if (!Files.isRegularFile(pidFile)) {
      return;
    }
    long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim());
    ProcessHandle.of(pid)
        .filter(ProcessHandle::isAlive)
        .ifPresent(
            process -> {
              process.destroyForcibly();
              try {
                process.onExit().get(2, TimeUnit.SECONDS);
              } catch (Exception failure) {
                throw new IllegalStateException("FAKE_CODEX_PROCESS_DID_NOT_EXIT", failure);
              }
            });
  }

  private static boolean fakeCodexIsAlive(Path pidFile) throws Exception {
    if (!Files.isRegularFile(pidFile)) {
      return false;
    }
    long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim());
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  private static String readIfPresent(Path path) throws Exception {
    return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
  }
}
