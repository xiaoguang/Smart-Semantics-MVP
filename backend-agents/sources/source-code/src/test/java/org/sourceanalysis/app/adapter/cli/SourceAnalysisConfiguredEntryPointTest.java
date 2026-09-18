package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contracts for the single configured {@code source-analysis} process entry point. */
class SourceAnalysisConfiguredEntryPointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void configuredEntryPointUsesTheUnifiedProcessCommandRatherThanLegacyModes() {
    ExecutionResult result =
        execute(
            "--config",
            temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString(),
            "execute-step",
            "--target",
            "repository-knowledge",
            "--activity-model-batch",
            "analysis-run:" + "a".repeat(64));

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION")
        .doesNotContain("ARGUMENTS_INVALID", "MODE_UNSUPPORTED");
  }

  @Test
  void configuredEntryPointRecognizesCaptureStartAndAnExplicitQueuedRun() {
    String config = temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString();
    assertConfigurationReached(execute("--config", config, "capture-local-git"));
    assertConfigurationReached(
        execute(
            "--config",
            config,
            "start",
            "--source-registration",
            "source-registration:" + "a".repeat(64)));
    assertConfigurationReached(
        execute(
            "--config",
            config,
            "execute-step",
            "--target",
            "flow-interpretation",
            "--run",
            "analysis-run:" + "b".repeat(64)));
  }

  @Test
  void planMaterialsAcceptsAnExplicitFrozenSourceRegistrationBeforeConfiguration() {
    String config = temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString();

    ExecutionResult result =
        execute(
            "--config",
            config,
            "plan-materials",
            "--source-registration",
            "source-registration:" + "a".repeat(64));

    assertConfigurationReached(result);
  }

  @Test
  void configuredReadingMaterialsArtifactKeepsJsonlDefaultAndValidatesMarkdownProjection() {
    String config = temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString();
    String run = "analysis-run:" + "a".repeat(64);
    String output = temporaryDirectory.resolve("reading-materials.md").toAbsolutePath().toString();

    assertConfigurationReached(
        execute(
            "--config",
            config,
            "artifact",
            "--run",
            run,
            "--key",
            "CODE_READING_MATERIALS",
            "--max-bytes",
            "4096",
            "--format",
            "markdown",
            "--output",
            output));
    assertConfigurationReached(
        execute(
            "--config",
            config,
            "artifact",
            "--run",
            run,
            "--key",
            "CODE_READING_MATERIALS",
            "--max-bytes",
            "4096"));

    ExecutionResult invalidFormat =
        execute(
            "--config",
            config,
            "artifact",
            "--run",
            run,
            "--key",
            "CODE_READING_MATERIALS",
            "--max-bytes",
            "4096",
            "--format",
            "yaml",
            "--output",
            output);
    assertThat(invalidFormat.exitCode()).isNotZero();
    assertThat(invalidFormat.diagnostics())
        .contains("ARGUMENTS_INVALID")
        .doesNotContain("CONFIGURATION");
  }

  @Test
  void repositoryKnowledgeAcceptsCatalogSourceWithAnOptionalExactFocusQuestion() {
    String config = temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString();
    String activityBatch = "analysis-run:" + "c".repeat(64);
    String catalogBatch = "analysis-run:" + "d".repeat(64);

    assertConfigurationReached(
        execute(
            "--config",
            config,
            "execute-step",
            "--target",
            "repository-knowledge",
            "--activity-model-batch",
            activityBatch,
            "--catalog-from-model-batch",
            catalogBatch,
            "--focus-question",
            "原样问题：核对采购入库后的库存回写"));
    assertConfigurationReached(
        execute(
            "--config",
            config,
            "execute-step",
            "--target",
            "repository-knowledge",
            "--activity-model-batch",
            activityBatch,
            "--catalog-from-model-batch",
            catalogBatch));
  }

  @Test
  void repositoryKnowledgeKeepsCatalogAndReuseInputsSeparateAndRejectsDuplicateProcessOptions() {
    String config = temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString();
    String activityBatch = "analysis-run:" + "e".repeat(64);
    String reuseBatch = "analysis-run:" + "f".repeat(64);
    String catalogBatch = "analysis-run:" + "0".repeat(64);

    assertConfigurationReached(
        execute(
            "--config",
            config,
            "execute-step",
            "--target",
            "repository-knowledge",
            "--activity-model-batch",
            activityBatch,
            "--reuse-from-model-batch",
            reuseBatch));

    assertConfigurationReached(
        execute(
            "--config",
            config,
            "execute-step",
            "--target",
            "repository-knowledge",
            "--activity-model-batch",
            activityBatch,
            "--catalog-from-model-batch",
            catalogBatch,
            "--reuse-from-model-batch",
            reuseBatch));

    assertThat(
            execute(
                    "--config",
                    config,
                    "execute-step",
                    "--target",
                    "repository-knowledge",
                    "--activity-model-batch",
                    activityBatch,
                    "--catalog-from-model-batch",
                    catalogBatch,
                    "--catalog-from-model-batch",
                    catalogBatch)
                .diagnostics())
        .contains("ARGUMENTS_INVALID");

    assertThat(
            execute(
                    "--config",
                    config,
                    "execute-step",
                    "--target",
                    "flow-interpretation",
                    "--catalog-from-model-batch",
                    catalogBatch)
                .diagnostics())
        .contains("ARGUMENTS_INVALID");
    assertThat(
            execute(
                    "--config",
                    config,
                    "execute-step",
                    "--target",
                    "flow-interpretation",
                    "--focus-question",
                    "process-only")
                .diagnostics())
        .contains("ARGUMENTS_INVALID");
  }

  @Test
  void legacyModeSyntaxIsNotAcceptedByTheUnifiedEntryPoint() {
    ExecutionResult result =
        execute(
            "--config",
            temporaryDirectory.resolve("missing.yaml").toAbsolutePath().toString(),
            "--mode",
            "generate");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics()).contains("ARGUMENTS_INVALID");
  }

  private static ExecutionResult execute(String... arguments) {
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    int exitCode =
        SourceAnalysisCli.executeConfigured(
            arguments,
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));
    return new ExecutionResult(
        exitCode,
        outputBytes.toString(StandardCharsets.UTF_8) + errorBytes.toString(StandardCharsets.UTF_8));
  }

  private static void assertConfigurationReached(ExecutionResult result) {
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.diagnostics())
        .contains("CONFIGURATION")
        .doesNotContain("ARGUMENTS_INVALID", "MODE_UNSUPPORTED");
  }

  private record ExecutionResult(int exitCode, String diagnostics) {}
}
