package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryRequest;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** RED contract for the private v3 process-reading execution binding. */
class CrossObjectProcessExecutionConfigurationTest {

  private static final String CATALOG_FINGERPRINT = "c".repeat(64);
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void writesAnIdempotentV3BindingWithCompleteLineageAndRejectsChangedReadingInputs()
      throws IOException {
    AnalysisRunId sourceRun = runId('a');
    AnalysisRunId outputRun = runId('b');
    AnalysisRunId catalogRun = runId('c');
    String focusQuestion = "原样问题：核对采购入库后的库存回写";
    ModelJobsConfiguration modelJobs = modelJobs();
    RepositoryRunConfiguration configuration = configuration(modelJobs);
    RepositoryRunStateV3.SavedState materials = savedMaterials(sourceRun);
    ModulePublicationReference activityCheckpoint = activityCheckpoint(sourceRun);
    ProcessDiscoveryRequest readingRequest =
        readingRequest(outputRun, sourceRun, catalogRun, focusQuestion, activityCheckpoint);

    SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
        configuration, modelJobs, materials, activityCheckpoint, outputRun, null, readingRequest);
    ObjectNode first =
        SourceAnalysisExecution.readModelJobExecutionConfiguration(modelJobs, outputRun);

    SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
        configuration, modelJobs, materials, activityCheckpoint, outputRun, null, readingRequest);
    ObjectNode reopened =
        SourceAnalysisExecution.readModelJobExecutionConfiguration(modelJobs, outputRun);
    assertThat(reopened).isEqualTo(first);
    assertThat(first.path("schemaVersion").asText()).isEqualTo("model-job-execution-config-v3");
    assertThat(first.path("modelBatchId").asText()).isEqualTo(outputRun.value());
    assertThat(first.path("sourceRunId").asText()).isEqualTo(sourceRun.value());
    assertThat(first.path("materialsCheckpoint").path("runId").asText())
        .isEqualTo(sourceRun.value());
    assertThat(first.path("materialsCheckpoint").path("moduleNumber").asInt()).isEqualTo(10);
    assertThat(first.path("activityCheckpoint").path("runId").asText())
        .isEqualTo(sourceRun.value());
    assertThat(first.path("activityCheckpoint").path("moduleNumber").asInt()).isEqualTo(11);

    ObjectNode inventory = (ObjectNode) first.path("sourceInventoryReference");
    assertThat(inventory.path("publication").path("address").path("runId").asText())
        .isEqualTo(sourceRun.value());
    assertThat(inventory.path("publication").path("address").path("analysisStepKey").asText())
        .isEqualTo(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY.name());
    assertThat(inventory.path("publication").path("analysisStepArtifactRoot").asText())
        .isEqualTo("analysis-step-root:" + "1".repeat(64));
    assertThat(inventory.path("publication").path("analysisStepReceiptId").asText())
        .isEqualTo("analysis-step-receipt:" + "2".repeat(64));
    assertThat(inventory.path("publication").path("analysisStepReceiptSha256").asText())
        .isEqualTo("3".repeat(64));

    ObjectNode savedCatalog = (ObjectNode) first.path("savedCatalogInput");
    assertThat(savedCatalog.path("runId").asText()).isEqualTo(catalogRun.value());
    assertThat(savedCatalog.path("phase").asText()).isEqualTo("process-catalog");
    assertThat(savedCatalog.path("jobKey").asText()).isEqualTo("business-catalog-merge");
    assertThat(savedCatalog.path("inputFingerprint").asText()).isEqualTo(CATALOG_FINGERPRINT);
    assertThat(savedCatalog.path("bytesSha256").asText())
        .isEqualTo(SourceAnalysisExecution.sha256(catalogInputBytes(catalogRun).copyToByteArray()));
    assertThat(savedCatalog.path("bytesSize").asInt())
        .isEqualTo(catalogInputBytes(catalogRun).size());
    assertThat(first.path("focusQuestion").asText()).isEqualTo(focusQuestion);

    ProcessDiscoveryRequest changedFocus =
        readingRequest(outputRun, sourceRun, catalogRun, "不同问题", activityCheckpoint);
    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    materials,
                    activityCheckpoint,
                    outputRun,
                    null,
                    changedFocus))
        .hasMessage("MODEL_EXECUTION_CONFIGURATION_CONFLICT");

    ProcessDiscoveryRequest changedCatalog =
        readingRequest(outputRun, sourceRun, runId('e'), focusQuestion, activityCheckpoint);
    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    materials,
                    activityCheckpoint,
                    outputRun,
                    null,
                    changedCatalog))
        .hasMessage("MODEL_EXECUTION_CONFIGURATION_CONFLICT");

    ProcessDiscoveryRequest changedSource =
        readingRequest(
            outputRun,
            sourceRun,
            catalogRun,
            focusQuestion,
            activityCheckpoint,
            sourceInventory(sourceRun, "4"));
    assertThatThrownBy(
            () ->
                SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
                    configuration,
                    modelJobs,
                    materials,
                    activityCheckpoint,
                    outputRun,
                    null,
                    changedSource))
        .hasMessage("MODEL_EXECUTION_CONFIGURATION_CONFLICT");

    AnalysisRunId noFocusOutputRun = runId('d');
    SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration(
        configuration,
        modelJobs,
        materials,
        activityCheckpoint,
        noFocusOutputRun,
        null,
        readingRequest(noFocusOutputRun, sourceRun, catalogRun, null, activityCheckpoint));
    ObjectNode noFocus =
        SourceAnalysisExecution.readModelJobExecutionConfiguration(modelJobs, noFocusOutputRun);
    assertThat(noFocus.has("focusQuestion")).isTrue();
    assertThat(noFocus.path("focusQuestion").isNull()).isTrue();
  }

  private RepositoryRunConfiguration configuration(ModelJobsConfiguration modelJobs) {
    return new RepositoryRunConfiguration(
        JSON, null, modelJobs, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, 1);
  }

  private ModelJobsConfiguration modelJobs() throws IOException {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("journal"));
    Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
    ModelJobProviderConfiguration provider =
        new ModelJobProviderConfiguration(
            ModelProviderKind.CODEX_SUBSCRIPTION,
            "scripted-account",
            4,
            "gpt-5.6-luna",
            "high",
            Duration.ofSeconds(600),
            temporaryDirectory.resolve("scripted-provider"),
            null,
            new ModelJobAuthentication("chatgpt", List.of("SOURCE_ANALYSIS_TEST_HOME")));
    return new ModelJobsConfiguration(
        4,
        Map.of("pro", provider),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        journal,
        output,
        "model-jobs-config-hash");
  }

  private ProcessDiscoveryRequest readingRequest(
      AnalysisRunId outputRun,
      AnalysisRunId sourceRun,
      AnalysisRunId catalogRun,
      String focusQuestion,
      ModulePublicationReference activityCheckpoint) {
    return readingRequest(
        outputRun,
        sourceRun,
        catalogRun,
        focusQuestion,
        activityCheckpoint,
        sourceInventory(sourceRun, "1"));
  }

  private ProcessDiscoveryRequest readingRequest(
      AnalysisRunId outputRun,
      AnalysisRunId sourceRun,
      AnalysisRunId catalogRun,
      String focusQuestion,
      ModulePublicationReference activityCheckpoint,
      VerifiedSourceInventoryReference inventory) {
    return new ProcessDiscoveryRequest(
        new ActivityExplanationResult(List.of(), List.of(), activityCheckpoint),
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet("materials", List.of(), List.of()),
            materialsCheckpoint(sourceRun)),
        new ProcessDiscoveryProfile(1, 1, 1, 1, 1, 1, 1, 1, 1),
        outputRun,
        inventory,
        ignored -> null,
        catalogInputBytes(catalogRun),
        focusQuestion);
  }

  private static RepositoryRunStateV3.SavedState savedMaterials(AnalysisRunId sourceRun) {
    return new RepositoryRunStateV3.SavedState(
        sourceRun, null, materialsCheckpoint(sourceRun), null, "v1", "a".repeat(64));
  }

  private static ModulePublicationReference materialsCheckpoint(AnalysisRunId run) {
    return moduleCheckpoint(run, 10, "business-material-builder", "a", "b", "c");
  }

  private static ModulePublicationReference activityCheckpoint(AnalysisRunId run) {
    return moduleCheckpoint(run, 11, "activity-explainer", "d", "e", "f");
  }

  private static ModulePublicationReference moduleCheckpoint(
      AnalysisRunId run,
      int moduleNumber,
      String moduleKey,
      String rootFill,
      String receiptFill,
      String receiptShaFill) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            run, AnalysisStepKey.FLOW_INTERPRETATION, moduleNumber, moduleKey),
        new ModuleArtifactRoot("module-root:" + rootFill.repeat(64)),
        new ModuleReceiptId("module-receipt:" + receiptFill.repeat(64)),
        new Sha256Digest(receiptShaFill.repeat(64)));
  }

  private static VerifiedSourceInventoryReference sourceInventory(
      AnalysisRunId run, String rootFill) {
    return new VerifiedSourceInventoryReference(
        new AnalysisStepPublicationReference(
            new AnalysisStepPublicationAddress(run, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
            new AnalysisStepArtifactRoot("analysis-step-root:" + rootFill.repeat(64)),
            new AnalysisStepReceiptId("analysis-step-receipt:" + "2".repeat(64)),
            new Sha256Digest("3".repeat(64))));
  }

  private static ImmutableBytes catalogInputBytes(AnalysisRunId catalogRun) {
    ObjectNode pair = JsonNodeFactory.instance.objectNode();
    pair.put("schemaVersion", "model-job-reviewed-result-v2");
    pair.put("status", "COMPLETED");
    pair.put("runId", catalogRun.value());
    pair.put("phase", "process-catalog");
    pair.put("jobKey", "business-catalog-merge");
    pair.put("inputFingerprint", CATALOG_FINGERPRINT);
    pair.put("providerBindingKey", "pro");
    pair.put("quotaScope", "scripted-account");
    ObjectNode identity = pair.putObject("runtimeIdentity");
    identity.put("upstreamProvider", "codex-subscription");
    identity.put("model", "gpt-5.6-luna");
    identity.put("reasoningEffort", "high");
    identity.put("sandbox", "read-only");
    pair.putObject("draft").put("candidateProcesses", "raw-draft");
    pair.putObject("review").put("candidateProcesses", "raw-review");
    return JSON.encodeCanonical(pair);
  }

  private static AnalysisRunId runId(char fill) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
  }
}
