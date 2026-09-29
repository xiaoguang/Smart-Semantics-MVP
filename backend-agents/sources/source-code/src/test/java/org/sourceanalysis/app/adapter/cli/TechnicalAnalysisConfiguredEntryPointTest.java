package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RED contracts for dispatching the standalone technical configuration through the public CLI. */
class TechnicalAnalysisConfiguredEntryPointTest {

  @TempDir Path temporaryDirectory;

  @Test
  void selectsTechnicalSchemaBeforeLegacyConfigurationAndNamesItsMissingFields() throws Exception {
    Path config = temporaryDirectory.resolve("technical-analysis.yaml").toAbsolutePath();
    Files.writeString(config, "schemaVersion: technical-analysis-config-v1\n");
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

    int exitCode =
        SourceAnalysisCli.executeConfigured(
            new String[] {"--config", config.toString(), "collect-code"},
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));

    assertThat(exitCode).isEqualTo(2);
    assertThat(errorBytes.toString(StandardCharsets.UTF_8))
        .contains("TECHNICAL_CONFIGURATION_INVALID")
        .doesNotContain("ARGUMENTS_INVALID", "MODE_UNSUPPORTED", "MODEL_JOBS");
    assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void malformedTechnicalDocumentDoesNotFallThroughToTheLegacyConfiguredRuntime() throws Exception {
    Path config = temporaryDirectory.resolve("malformed-technical-analysis.yaml").toAbsolutePath();
    Files.writeString(
        config, "schemaVersion: technical-analysis-config-v1\nsource: [\n", StandardCharsets.UTF_8);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

    int exitCode =
        SourceAnalysisCli.executeConfigured(
            new String[] {"--config", config.toString(), "collect-code"},
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));

    assertThat(exitCode).isEqualTo(2);
    assertThat(errorBytes.toString(StandardCharsets.UTF_8))
        .contains("TECHNICAL_CONFIGURATION_INVALID")
        .doesNotContain("ARGUMENTS_INVALID", "MODE_UNSUPPORTED", "MODEL_JOBS");
    assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void wrongSchemaWithAllRequiredSectionsIsStillTechnicalConfigurationInvalid() throws Exception {
    Path config =
        temporaryDirectory.resolve("wrong-schema-technical-analysis.yaml").toAbsolutePath();
    Files.writeString(
        config,
        """
        schemaVersion: business-analysis-config-v1
        source: {}
        storage: {}
        java: {}
        frontend: {}
        persistence: {}
        readingMaterials: {}
        """,
        StandardCharsets.UTF_8);
    ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

    int exitCode =
        SourceAnalysisCli.executeConfigured(
            new String[] {"--config", config.toString(), "collect-code"},
            new PrintWriter(outputBytes, true, StandardCharsets.UTF_8),
            new PrintWriter(errorBytes, true, StandardCharsets.UTF_8));

    assertThat(exitCode).isEqualTo(2);
    assertThat(errorBytes.toString(StandardCharsets.UTF_8))
        .contains("TECHNICAL_CONFIGURATION_INVALID")
        .doesNotContain("TECHNICAL_EXECUTION_NOT_CONNECTED", "ARGUMENTS_INVALID");
    assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEmpty();
  }
}
