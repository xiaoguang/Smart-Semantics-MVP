package org.sourceanalysis.app.adapter.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Guards the process boundary's safe failure classification without invoking Codex. */
class ProcessCodexSubscriptionCommandTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesOnlyABoundedModelConfigurationCategoryWhenCliWritesSensitiveLookingStderr()
      throws Exception {
    Path executable = temporaryDirectory.resolve("fake-codex");
    Files.writeString(
        executable,
        """
        #!/bin/sh
        if [ "$1" = "login" ]; then
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
}
