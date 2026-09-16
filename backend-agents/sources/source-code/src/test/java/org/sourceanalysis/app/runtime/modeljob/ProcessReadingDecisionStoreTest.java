package org.sourceanalysis.app.runtime.modeljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** RED contract for one-shot process-reading decisions and saved catalog input reuse. */
class ProcessReadingDecisionStoreTest {

  private static final String QUOTA_SCOPE = "scripted-account";
  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @TempDir Path temporaryDirectory;

  @Test
  void writesAndReopensACompleteDecisionAtADistinctDecisionResultPath() throws IOException {
    AnalysisRunId run = runId('a');
    String phase = "process-reading-check";
    String jobKey = "candidate-001";
    String fingerprint = "1".repeat(64);
    PrivateModelJobResultStore store = store(run, phase);
    ObjectNode decision = completeDecision(run, phase, jobKey, fingerprint);

    store.writeDecision(jobKey, decision);
    store.writeDecision(jobKey, decision.deepCopy());

    Optional<ObjectNode> reopened =
        store.readCompletedDecision(jobKey, fingerprint, QUOTA_SCOPE, IDENTITY);

    assertThat(reopened).isPresent();
    assertThat(reopened.orElseThrow()).isEqualTo(decision);
    Path jobDirectory = jobDirectory(run, phase, jobKey);
    assertThat(Files.isRegularFile(jobDirectory.resolve("decision-result.json"))).isTrue();
    assertThat(Files.exists(jobDirectory.resolve("reviewed-result.json"))).isFalse();
    ObjectNode reopenedDecision = reopened.orElseThrow();
    assertThat(
            List.of(
                reopenedDecision.path("schemaVersion").asText(),
                reopenedDecision.path("status").asText(),
                reopenedDecision.path("phase").asText(),
                reopenedDecision.path("taskKind").asText(),
                reopenedDecision.path("jobKey").asText(),
                reopenedDecision.path("runId").asText(),
                reopenedDecision.path("producerVersion").asText()))
        .containsExactly(
            "process-reading-decision-v1",
            "COMPLETED",
            phase,
            "PROCESS_READING_CHECK",
            jobKey,
            run.value(),
            "v3");
  }

  @Test
  void doesNotReuseHalfFailedOrDeclaredCompleteMalformedDecision() throws IOException {
    AnalysisRunId run = runId('b');
    String phase = "process-reading-check";
    String fingerprint = "2".repeat(64);
    PrivateModelJobResultStore store = store(run, phase);

    ObjectNode half = completeDecision(run, phase, "half", fingerprint);
    half.put("status", "DRAFT_ONLY");
    installDecision(store, run, phase, "half", half);
    assertThat(store.readCompletedDecision("half", fingerprint, QUOTA_SCOPE, IDENTITY)).isEmpty();

    ObjectNode failed = completeDecision(run, phase, "failed", fingerprint);
    failed.put("status", "FAILED");
    installDecision(store, run, phase, "failed", failed);
    assertThat(store.readCompletedDecision("failed", fingerprint, QUOTA_SCOPE, IDENTITY)).isEmpty();

    ObjectNode missingInput = completeDecision(run, phase, "missing-input", fingerprint);
    missingInput.remove("input");
    installDecision(store, run, phase, "missing-input", missingInput);
    assertThatThrownBy(
            () -> store.readCompletedDecision("missing-input", fingerprint, QUOTA_SCOPE, IDENTITY))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");

    ObjectNode missingResponse = completeDecision(run, phase, "missing-response", fingerprint);
    missingResponse.remove("response");
    installDecision(store, run, phase, "missing-response", missingResponse);
    assertThatThrownBy(
            () ->
                store.readCompletedDecision("missing-response", fingerprint, QUOTA_SCOPE, IDENTITY))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");

    installRawDecision(store, run, phase, "corrupt", "{not-json");
    assertThatThrownBy(
            () -> store.readCompletedDecision("corrupt", fingerprint, QUOTA_SCOPE, IDENTITY))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");
  }

  @Test
  void treatsChangedFingerprintQuotaRuntimeOrPhaseAsANewDecision() throws IOException {
    AnalysisRunId run = runId('c');
    String phase = "process-reading-check";
    String jobKey = "candidate-003";
    String fingerprint = "3".repeat(64);
    PrivateModelJobResultStore store = store(run, phase);
    store.writeDecision(jobKey, completeDecision(run, phase, jobKey, fingerprint));

    assertThat(store.readCompletedDecision(jobKey, "4".repeat(64), QUOTA_SCOPE, IDENTITY))
        .isEmpty();
    assertThat(store.readCompletedDecision(jobKey, fingerprint, "other-account", IDENTITY))
        .isEmpty();
    assertThat(
            store.readCompletedDecision(
                jobKey,
                fingerprint,
                QUOTA_SCOPE,
                new ModelRuntimeIdentityV1("scripted", "other-model", "high", "read-only")))
        .isEmpty();

    PrivateModelJobResultStore otherPhaseStore = store(run, "process-material-selection");
    assertThat(otherPhaseStore.readCompletedDecision(jobKey, fingerprint, QUOTA_SCOPE, IDENTITY))
        .isEmpty();
  }

  @Test
  void reopensCompleteLegacyCatalogPairWithoutNewDecisionFingerprintAndRejectsIncompleteOrCorrupt()
      throws IOException {
    AnalysisRunId run = runId('d');
    String phase = "process-catalog";
    String jobKey = "business-catalog-merge";
    PrivateModelJobResultStore store = store(run, phase);
    ObjectNode pair = completeCatalogPair(run, phase, jobKey);
    store.write(jobKey, pair);

    Optional<ObjectNode> reopened = store.readCatalogInput(jobKey);

    assertThat(reopened).isPresent();
    assertThat(reopened.orElseThrow().path("draft")).isEqualTo(pair.path("draft"));
    assertThat(reopened.orElseThrow().path("review")).isEqualTo(pair.path("review"));
    assertThat(reopened.orElseThrow().path("runId").asText()).isEqualTo(run.value());
    assertThat(reopened.orElseThrow().path("input").isMissingNode()).isTrue();

    assertThat(store.readCatalogInput("missing-catalog")).isEmpty();

    ObjectNode declaredCompleteWithoutReview =
        completeCatalogPair(run, phase, "catalog-missing-review");
    declaredCompleteWithoutReview.remove("review");
    installPair(store, run, phase, "catalog-missing-review", declaredCompleteWithoutReview);
    assertThatThrownBy(() -> store.readCatalogInput("catalog-missing-review"))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");

    ObjectNode half = completeCatalogPair(run, phase, "catalog-half");
    half.remove("review");
    half.put("status", "DRAFT_ONLY");
    installPair(store, run, phase, "catalog-half", half);
    assertThat(store.readCatalogInput("catalog-half")).isEmpty();

    installRawPair(store, run, phase, "catalog-corrupt", "{\"status\":\"COMPLETED\"");
    assertThatThrownBy(() -> store.readCatalogInput("catalog-corrupt"))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");
  }

  private PrivateModelJobResultStore store(AnalysisRunId run, String phase) throws IOException {
    return new PrivateModelJobResultStore(
        Files.createDirectories(
            temporaryDirectory.resolve(
                phase + "-" + run.value().substring("analysis-run:".length()))),
        run,
        phase);
  }

  private void installDecision(
      PrivateModelJobResultStore store,
      AnalysisRunId run,
      String phase,
      String jobKey,
      ObjectNode value)
      throws IOException {
    store.writeDecision(jobKey, completeDecision(run, phase, jobKey, "f".repeat(64)));
    writeCanonical(decisionPath(run, phase, jobKey), value);
  }

  private void installRawDecision(
      PrivateModelJobResultStore store, AnalysisRunId run, String phase, String jobKey, String raw)
      throws IOException {
    store.writeDecision(jobKey, completeDecision(run, phase, jobKey, "e".repeat(64)));
    Files.writeString(decisionPath(run, phase, jobKey), raw, StandardCharsets.UTF_8);
  }

  private void installPair(
      PrivateModelJobResultStore store,
      AnalysisRunId run,
      String phase,
      String jobKey,
      ObjectNode value)
      throws IOException {
    store.write(jobKey, completeCatalogPair(run, phase, jobKey));
    writeCanonical(pairPath(run, phase, jobKey), value);
  }

  private void installRawPair(
      PrivateModelJobResultStore store, AnalysisRunId run, String phase, String jobKey, String raw)
      throws IOException {
    store.write(jobKey, completeCatalogPair(run, phase, jobKey));
    Files.writeString(pairPath(run, phase, jobKey), raw, StandardCharsets.UTF_8);
  }

  private void writeCanonical(Path path, ObjectNode value) throws IOException {
    Files.write(path, json.encodeCanonical(value).copyToByteArray());
  }

  private Path jobDirectory(AnalysisRunId run, String phase, String jobKey) {
    return temporaryDirectory
        .resolve(phase + "-" + run.value().substring("analysis-run:".length()))
        .resolve("model-jobs")
        .resolve(run.value().substring("analysis-run:".length()))
        .resolve(phase)
        .resolve(jobKey);
  }

  private Path decisionPath(AnalysisRunId run, String phase, String jobKey) {
    return jobDirectory(run, phase, jobKey).resolve("decision-result.json");
  }

  private Path pairPath(AnalysisRunId run, String phase, String jobKey) {
    return jobDirectory(run, phase, jobKey).resolve("reviewed-result.json");
  }

  private static ObjectNode completeDecision(
      AnalysisRunId run, String phase, String jobKey, String fingerprint) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "process-reading-decision-v1");
    value.put("status", "COMPLETED");
    value.put("runId", run.value());
    value.put("phase", phase);
    value.put("taskKind", "PROCESS_READING_CHECK");
    value.put("jobKey", jobKey);
    value.put("inputFingerprint", fingerprint);
    value.put("providerBindingKey", "pro");
    value.put("quotaScope", QUOTA_SCOPE);
    value.put("producerVersion", "v3");
    value
        .putObject("runtimeIdentity")
        .put("upstreamProvider", IDENTITY.upstreamProvider())
        .put("model", IDENTITY.model())
        .put("reasoningEffort", IDENTITY.reasoningEffort())
        .put("sandbox", IDENTITY.sandbox());
    value.putObject("input").put("candidateId", "candidate:001").put("focusQuestion", "核对订单状态回写");
    value.putObject("response").putArray("supplementaryRequests");
    return value;
  }

  private static ObjectNode completeCatalogPair(AnalysisRunId run, String phase, String jobKey) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "model-job-reviewed-result-v2");
    value.put("status", "COMPLETED");
    value.put("runId", run.value());
    value.put("phase", phase);
    value.put("jobKey", jobKey);
    value.put("inputFingerprint", "9".repeat(64));
    value.put("providerBindingKey", "pro");
    value.put("quotaScope", QUOTA_SCOPE);
    value
        .putObject("runtimeIdentity")
        .put("upstreamProvider", IDENTITY.upstreamProvider())
        .put("model", IDENTITY.model())
        .put("reasoningEffort", IDENTITY.reasoningEffort())
        .put("sandbox", IDENTITY.sandbox());
    value.putObject("draft").put("catalog", "raw-draft");
    value.putObject("review").put("catalog", "raw-review");
    return value;
  }

  private static AnalysisRunId runId(char fill) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
  }
}
