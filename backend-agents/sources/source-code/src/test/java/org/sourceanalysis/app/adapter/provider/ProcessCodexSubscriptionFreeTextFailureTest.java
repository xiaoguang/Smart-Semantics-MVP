package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityRetryProfile;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Ensures free-form CLI diagnostics never become a machine retry or binding category. */
class ProcessCodexSubscriptionFreeTextFailureTest {

  @TempDir Path temporaryDirectory;

  @Test
  void treatsUnstructuredCliKeywordsAsUnknownAndKeepsDiagnosticsPrivate() throws Exception {
    assertUnknownFreeTextFailure("rate limit reached; token=private", true);
    assertUnknownFreeTextFailure("selected model cannot serve this request; token=private", false);
    assertUnknownFreeTextFailure("input exceeds context window; token=private", true);
    assertUnknownFreeTextFailure("response schema validation failed; token=private", false);
  }

  private void assertUnknownFreeTextFailure(String diagnostic, boolean standardError)
      throws Exception {
    Path executable =
        temporaryDirectory.resolve(
            "fake-codex-free-text-" + Integer.toUnsignedString(diagnostic.hashCode()));
    String redirect = standardError ? " >&2" : "";
    Files.writeString(
        executable,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"login\" ]; then\n"
            + "  echo 'Logged in using ChatGPT'\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo '"
            + diagnostic
            + "'"
            + redirect
            + "\nexit 17\n",
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
                        "test-only prompt",
                        ImmutableBytes.copyOf(
                            "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(StructuredModelProviderFailure.class)
        .satisfies(
            failure -> {
              StructuredModelProviderFailure classified = (StructuredModelProviderFailure) failure;
              assertThat(classified.reasonCode()).isEqualTo("UNKNOWN");
              assertThat(classified.requestStarted()).isTrue();
              assertThat(classified.requestEnded()).isTrue();
              assertThat(
                      ActivityRetryProfile.defaults()
                          .isRetryable(
                              classified.reasonCode(),
                              classified.requestStarted(),
                              classified.requestEnded()))
                  .isFalse();
              assertThat(classified.getMessage())
                  .isEqualTo("CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN")
                  .doesNotContain("token=private");
              ImmutableBytes privateDiagnostic = classified.rawResponse().orElseThrow();
              assertThat(new String(privateDiagnostic.copyToByteArray(), StandardCharsets.UTF_8))
                  .contains(diagnostic);
            });
  }
}
