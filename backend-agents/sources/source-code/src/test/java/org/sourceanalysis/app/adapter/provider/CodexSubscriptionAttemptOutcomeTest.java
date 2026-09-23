package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * RED contract for timeout classification delivered from the process boundary to stage retry.
 *
 * <p>A timeout is retryable only after the local request process is confirmed ended. A request
 * whose outcome cannot be confirmed carries `OUTCOME_UNKNOWN`; the Activity executor must retain
 * that attempt and refuse a same-batch retry rather than overlap possible server work.
 */
class CodexSubscriptionAttemptOutcomeTest {

  @Test
  void reportsAConfirmedEndedProcessTimeoutAsStructuredRetryData(@TempDir Path temporary)
      throws Exception {
    Path executable = temporary.resolve("fake-codex-slow");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
          echo 'Logged in using ChatGPT'
          exit 0
        fi
        sleep 5
        """,
        StandardCharsets.UTF_8);
    if (!executable.toFile().setExecutable(true, true)) {
      throw new IllegalStateException("TEST_EXECUTABLE_PERMISSION_NOT_SET");
    }

    Throwable failure =
        catchThrowable(
            () ->
                new ProcessCodexSubscriptionCommand()
                    .execute(
                        new CodexSubscriptionProfile(
                            executable, "gpt-5.6-terra", "xhigh", Duration.ofMillis(100)),
                        "test-only prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))));

    assertThat(failure).isNotNull();
    assertStructuredFailure(failure, "REQUEST_TIMEOUT", true, true);
  }

  @Test
  void representsAnUncertainTimeoutWithoutFalselyClaimingTheRequestEnded() throws Exception {
    RuntimeException failure = structuredFailure("OUTCOME_UNKNOWN", true, false);

    assertStructuredFailure(failure, "OUTCOME_UNKNOWN", true, false);
  }

  @Test
  void preservesOnlyTheBoundedActualResponseWhenCodexReturnsInvalidJson() {
    CodexSubscriptionProfile profile =
        new CodexSubscriptionProfile(
            Path.of("/trusted/codex"), "gpt-5.6-terra", "xhigh", Duration.ofSeconds(30));
    ImmutableBytes invalid = ImmutableBytes.copyOf("{not-json".getBytes(StandardCharsets.UTF_8));
    CodexSubscriptionCommand command =
        new CodexSubscriptionCommand() {
          @Override
          public void preflight(CodexSubscriptionProfile ignored) {}

          @Override
          public ImmutableBytes execute(
              CodexSubscriptionProfile ignored, String prompt, ImmutableBytes outputJsonSchema) {
            return invalid;
          }
        };
    StructuredModelRequest request =
        new StructuredModelRequest(
            "activity:bad-json",
            "ACTIVITY_DRAFT",
            "Return JSON",
            ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)),
            ImmutableBytes.copyOf("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)),
            128);

    Throwable failure =
        catchThrowable(
            () -> new CodexSubscriptionStructuredProvider(profile, command).generate(request));

    assertThat(failure).isInstanceOf(StructuredModelProviderFailure.class);
    StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
    assertThat(classified.reasonCode()).isEqualTo("INVALID_JSON");
    assertThat(classified.requestStarted()).isTrue();
    assertThat(classified.requestEnded()).isTrue();
    assertThat(classified.rawResponse()).isEqualTo(Optional.of(invalid));
  }

  private static RuntimeException structuredFailure(
      String reasonCode, boolean requestStarted, boolean requestEnded) throws Exception {
    Class<?> type = failureType();
    Constructor<?> constructor = type.getConstructor(String.class, boolean.class, boolean.class);
    Object value = constructor.newInstance(reasonCode, requestStarted, requestEnded);
    assertThat(value).isInstanceOf(RuntimeException.class);
    return (RuntimeException) value;
  }

  private static void assertStructuredFailure(
      Throwable failure, String reasonCode, boolean requestStarted, boolean requestEnded)
      throws Exception {
    Class<?> type = failureType();
    assertThat(failure).isInstanceOf(type);
    Method reason = type.getMethod("reasonCode");
    Method started = type.getMethod("requestStarted");
    Method ended = type.getMethod("requestEnded");
    assertThat(reason.invoke(failure)).isEqualTo(reasonCode);
    assertThat(started.invoke(failure)).isEqualTo(requestStarted);
    assertThat(ended.invoke(failure)).isEqualTo(requestEnded);
  }

  private static Class<?> failureType() {
    try {
      Class<?> type =
          Class.forName("org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure");
      if (!RuntimeException.class.isAssignableFrom(type)) {
        throw new AssertionError("StructuredModelProviderFailure must be a RuntimeException");
      }
      return type;
    } catch (ClassNotFoundException absent) {
      throw new AssertionError("missing structured Provider retry classification", absent);
    }
  }
}
