package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** Persists each completed Activity REVIEW under its run-private immutable job location. */
final class ActivityJobPrivateResultStore implements ActivityJobCompletionSink {

  private static final String LEGACY_RESULT_SCHEMA = "model-job-reviewed-result-v2";
  private static final String STAGED_RESULT_SCHEMA = "model-job-reviewed-result-v4";
  private static final String SCOPED_RESULT_SCHEMA = "activity-packet-result-v1";
  private static final String PHASE = "activity";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Path journalDirectory;
  private final AnalysisRunId runId;
  private final String expectedProviderBindingKey;

  ActivityJobPrivateResultStore(ActivityJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "activity job execution configuration");
    this.journalDirectory = configuration.journalDirectory();
    this.runId = configuration.runId();
    this.expectedProviderBindingKey = configuration.providerBindingKey();
  }

  ActivityJobPrivateResultStore(ModelJobExecutionConfiguration configuration) {
    Objects.requireNonNull(configuration, "model job execution configuration");
    this.journalDirectory = configuration.journalDirectory();
    this.runId = configuration.runId();
    this.expectedProviderBindingKey = null;
  }

  @Override
  public void complete(CompletedActivityJob completedJob) {
    complete(completedJob, null);
  }

  @Override
  public void fail(FailedActivityJob failedJob) {
    Objects.requireNonNull(failedJob, "failed activity job");
    ActivityJob job = failedJob.job();
    if (expectedProviderBindingKey != null
        && !expectedProviderBindingKey.equals(job.providerBindingKey())) {
      throw failure("ACTIVITY_JOB_RESULT_PROVIDER_BINDING_MISMATCH", null);
    }
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put("schemaVersion", "activity-job-failure-v1");
    record.put("status", "FAILED");
    record.put("runId", runId.value());
    record.put("jobKey", job.identity().jobKey());
    record.put("inputFingerprint", job.identity().inputFingerprint());
    record.put("materialId", job.materialId());
    record.put("providerBindingKey", job.providerBindingKey());
    record.put("quotaScope", job.providerBinding().quotaScope());
    record.put("reasonCode", failedJob.reasonCode());
    ActivityExplainer.ActivityStageFailure stageFailure = stageFailure(failedJob.cause());
    if (stageFailure == null) {
      record.putNull("sliceKey");
      record.putNull("stageKey");
      record.put("attemptsUsed", 0);
      record.put("maxAttempts", 0);
    } else {
      if (stageFailure.sliceKey() == null) {
        record.putNull("sliceKey");
      } else {
        record.put("sliceKey", stageFailure.sliceKey());
      }
      record.put("stageKey", stageFailure.stageKey());
      record.put("attemptsUsed", stageFailure.attemptsUsed());
      record.put("maxAttempts", stageFailure.maxAttempts());
    }
    var reusable = record.putArray("reusableSuccessfulStages");
    if (stageFailure != null) {
      PrivateModelJobResultStore stages =
          new PrivateModelJobResultStore(journalDirectory, runId, PHASE);
      if (stages.readStageSuccess(stageFailure.jobKey(), "DRAFT").isPresent()) {
        reusable.add("DRAFT");
      }
    }
    record.put("nextAction", "INSPECT_FAILURE_THEN_START_NEW_BATCH");
    new PrivateModelJobResultStore(journalDirectory, runId, PHASE)
        .writeTerminalFailure(job.identity().jobKey(), record);
  }

  private static ActivityExplainer.ActivityStageFailure stageFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ActivityExplainer.ActivityStageFailure context) {
        return context;
      }
    }
    return null;
  }

  void completeReused(CompletedActivityJob completedJob, AnalysisRunId reusedFromModelBatchId) {
    complete(completedJob, Objects.requireNonNull(reusedFromModelBatchId));
  }

  private void complete(CompletedActivityJob completedJob, AnalysisRunId reusedFromModelBatchId) {
    Objects.requireNonNull(completedJob, "completed activity job");
    ActivityJob job = completedJob.job();
    ActivityJobResult result = completedJob.result();
    if (expectedProviderBindingKey != null
        && !expectedProviderBindingKey.equals(job.providerBindingKey())) {
      throw failure("ACTIVITY_JOB_RESULT_PROVIDER_BINDING_MISMATCH", null);
    }
    if (!job.materialId().equals(result.materialId())) {
      throw failure("ACTIVITY_JOB_RESULT_MATERIAL_MISMATCH", null);
    }

    new PrivateModelJobResultStore(journalDirectory, runId, PHASE)
        .write(job.identity().jobKey(), record(completedJob, reusedFromModelBatchId));
  }

  private ObjectNode record(
      CompletedActivityJob completedJob, AnalysisRunId reusedFromModelBatchId) {
    ActivityJob job = completedJob.job();
    ActivityJobResult result = completedJob.result();
    ObjectNode record = JsonNodeFactory.instance.objectNode();
    record.put(
        "schemaVersion",
        job.scopedReading()
            ? SCOPED_RESULT_SCHEMA
            : job.stagedExecution() ? STAGED_RESULT_SCHEMA : LEGACY_RESULT_SCHEMA);
    record.put("status", "COMPLETED");
    record.put("runId", runId.value());
    record.put("phase", PHASE);
    record.put("jobKey", job.identity().jobKey());
    record.put("inputFingerprint", job.identity().inputFingerprint());
    record.put("materialId", job.materialId());
    record.put("providerBindingKey", job.providerBindingKey());
    record.put("quotaScope", job.providerBinding().quotaScope());
    ObjectNode runtimeIdentity = record.putObject("runtimeIdentity");
    runtimeIdentity.put("upstreamProvider", result.runtimeIdentity().upstreamProvider());
    runtimeIdentity.put("model", result.runtimeIdentity().model());
    runtimeIdentity.put("reasoningEffort", result.runtimeIdentity().reasoningEffort());
    runtimeIdentity.put("sandbox", result.runtimeIdentity().sandbox());
    if (job.scopedReading()) {
      record.put("pipeline", "activity-reading-plan-slices-v1");
      record.put("readingPlan", "decision-result.json");
    } else {
      record.set("draft", result.draftResponse());
      record.set("review", result.reviewResponse());
    }
    if (job.stagedExecution() && !job.scopedReading()) {
      record.put("pipeline", "activity-draft-review-v3");
      ObjectNode stageSuccesses = record.putObject("stageSuccesses");
      stageSuccesses.put("DRAFT", "DRAFT/success.json");
      stageSuccesses.put("REVIEW", "REVIEW/success.json");
    }
    if (reusedFromModelBatchId == null) {
      record.putNull("reusedFromModelBatchId");
    } else {
      record.put("reusedFromModelBatchId", reusedFromModelBatchId.value());
    }
    record.set("reviewedActivities", JSON.valueToTree(result.reviewedActivities()));
    record.set("coverage", JSON.valueToTree(result.coverage()));
    record.set("unexplainedActivityEntries", JSON.valueToTree(result.unexplainedEntries()));
    return record;
  }

  private static IllegalStateException failure(String code, Throwable cause) {
    return new IllegalStateException(code, cause);
  }
}
