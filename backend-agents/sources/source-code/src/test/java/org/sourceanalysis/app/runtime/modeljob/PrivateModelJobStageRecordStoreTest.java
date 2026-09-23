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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Direct RED contract for immutable, safe private persistence of Activity stage attempts. */
class PrivateModelJobStageRecordStoreTest {

  private static final String PHASE = "activity-explanation";
  private static final String JOB_KEY = "packet-001";
  private static final String STAGE_KEY = "REVIEW";

  @TempDir Path temporaryDirectory;

  @Test
  void writesEachAttemptRecordAndTheVerifiedSuccessIndexImmutably() throws IOException {
    AnalysisRunId run = runId('a');
    Path journal = journal(run);
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(journal, run, PHASE);

    for (String recordName : List.of("request", "started", "response", "validation", "outcome")) {
      ObjectNode record = attemptRecord(recordName, "attempt-1-" + recordName);
      store.writeStageAttemptRecord(JOB_KEY, STAGE_KEY, 1, recordName, record);
      store.writeStageAttemptRecord(JOB_KEY, STAGE_KEY, 1, recordName, record.deepCopy());

      assertThat(store.readStageAttemptRecord(JOB_KEY, STAGE_KEY, 1, recordName)).contains(record);
    }

    ObjectNode conflictingStarted = attemptRecord("started", "different-start-time");
    assertThatThrownBy(
            () ->
                store.writeStageAttemptRecord(JOB_KEY, STAGE_KEY, 1, "started", conflictingStarted))
        .hasMessage("MODEL_JOB_RESULT_CONFLICT");

    ObjectNode success = stageSuccess(1);
    store.writeStageSuccess(JOB_KEY, STAGE_KEY, success);
    store.writeStageSuccess(JOB_KEY, STAGE_KEY, success.deepCopy());
    assertThat(store.readStageSuccess(JOB_KEY, STAGE_KEY)).contains(success);

    ObjectNode conflictingSuccess = stageSuccess(2);
    assertThatThrownBy(() -> store.writeStageSuccess(JOB_KEY, STAGE_KEY, conflictingSuccess))
        .hasMessage("MODEL_JOB_RESULT_CONFLICT");
  }

  @Test
  void rejectsNoncanonicalAndSymlinkStageRecords() throws IOException {
    AnalysisRunId run = runId('b');
    Path journal = journal(run);
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(journal, run, PHASE);

    store.writeStageAttemptRecord(
        "bad-canonical", STAGE_KEY, 1, "response", attemptRecord("response", "good"));
    Files.writeString(
        attemptRecordPath(journal, run, "bad-canonical", STAGE_KEY, 1, "response"),
        "{\"z\":1,\"a\":2}",
        StandardCharsets.UTF_8);
    assertThatThrownBy(
            () -> store.readStageAttemptRecord("bad-canonical", STAGE_KEY, 1, "response"))
        .hasMessage("MODEL_JOB_STAGE_RECORD_INVALID");

    store.writeStageSuccess("symlink", STAGE_KEY, stageSuccess(1));
    Path success = stageSuccessPath(journal, run, "symlink", STAGE_KEY);
    Path external = temporaryDirectory.resolve("external-success.json");
    Files.writeString(external, "{}", StandardCharsets.UTF_8);
    Files.delete(success);
    Files.createSymbolicLink(success, external);

    assertThatThrownBy(() -> store.readStageSuccess("symlink", STAGE_KEY))
        .hasMessage("MODEL_JOB_STAGE_RECORD_INVALID");
  }

  @Test
  void neverUsesAnIncompleteAttemptOrLegacyReviewedPairAsStageSuccess() throws IOException {
    AnalysisRunId run = runId('c');
    Path journal = journal(run);
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(journal, run, PHASE);

    store.writeStageAttemptRecord(
        "unfinished", STAGE_KEY, 1, "started", attemptRecord("started", "request-open"));
    store.writeStageAttemptRecord(
        "unfinished", STAGE_KEY, 1, "outcome", attemptRecord("failed", "provider-timeout"));
    store.write("unfinished", legacyReviewedPair());

    assertThat(store.readStageSuccess("unfinished", STAGE_KEY))
        .as(
            "STARTED/FAILED attempt records and a historical v2 pair are not verified stage success")
        .isEmpty();
  }

  @Test
  void listsOnlyThisBatchTerminalFailuresInStableJobOrder() throws IOException {
    AnalysisRunId run = runId('d');
    Path journal = journal(run);
    PrivateModelJobResultStore store = new PrivateModelJobResultStore(journal, run, PHASE);
    ObjectNode later =
        JsonNodeFactory.instance.objectNode().put("runId", run.value()).put("jobKey", "packet-z");
    ObjectNode earlier =
        JsonNodeFactory.instance.objectNode().put("runId", run.value()).put("jobKey", "packet-a");
    store.writeTerminalFailure("packet-z", later);
    store.writeTerminalFailure("packet-a", earlier);
    store.write("packet-complete", legacyReviewedPair());

    assertThat(store.listTerminalFailures())
        .extracting(value -> value.path("jobKey").asText())
        .containsExactly("packet-a", "packet-z");
  }

  private Path journal(AnalysisRunId run) throws IOException {
    return Files.createDirectories(
        temporaryDirectory.resolve(PHASE + "-" + run.value().substring("analysis-run:".length())));
  }

  private static Path attemptRecordPath(
      Path journal,
      AnalysisRunId run,
      String jobKey,
      String stageKey,
      int attemptOrdinal,
      String recordName) {
    return stageDirectory(journal, run, jobKey, stageKey)
        .resolve("attempt-" + attemptOrdinal)
        .resolve(recordName + ".json");
  }

  private static Path stageSuccessPath(
      Path journal, AnalysisRunId run, String jobKey, String stageKey) {
    return stageDirectory(journal, run, jobKey, stageKey).resolve("success.json");
  }

  private static Path stageDirectory(
      Path journal, AnalysisRunId run, String jobKey, String stageKey) {
    return journal
        .resolve("model-jobs")
        .resolve(run.value().substring("analysis-run:".length()))
        .resolve(PHASE)
        .resolve(jobKey)
        .resolve(stageKey);
  }

  private static ObjectNode attemptRecord(String status, String eventId) {
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("eventId", eventId);
    record.put("status", status);
    return record;
  }

  private static ObjectNode stageSuccess(int attemptOrdinal) {
    ObjectNode success = JsonNodeFactory.instance.objectNode();
    success.put("attemptOrdinal", attemptOrdinal);
    success.put("status", "SUCCESS");
    return success;
  }

  private static ObjectNode legacyReviewedPair() {
    ObjectNode pair = JsonNodeFactory.instance.objectNode();
    pair.put("schemaVersion", "model-job-reviewed-result-v2");
    pair.put("status", "COMPLETED");
    pair.putObject("draft").put("id", "legacy-draft");
    pair.putObject("review").put("id", "legacy-review");
    return pair;
  }

  private static AnalysisRunId runId(char fill) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
  }
}
