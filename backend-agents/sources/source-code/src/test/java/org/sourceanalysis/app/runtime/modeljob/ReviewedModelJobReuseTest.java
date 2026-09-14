package org.sourceanalysis.app.runtime.modeljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/**
 * RED contract for explicit cross-batch reuse of complete model jobs.
 *
 * <p>The current private store is write-only, so these tests intentionally fail until the existing
 * store gains a strict, read-only completed-pair seam. They do not authorize replaying a Provider
 * call or treating an isolated DRAFT as a completed job.
 */
class ReviewedModelJobReuseTest {

  private static final String QUOTA_SCOPE = "scripted-account";

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  @TempDir Path temporaryDirectory;

  @Test
  void matchingCompleteActivityPairIsReadableForZeroCallReuse() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("complete-activity"));
    AnalysisRunId sourceBatch = runId('a');
    String jobKey = "activity-job-001";
    String fingerprint = "1".repeat(64);
    write(journal, sourceBatch, "activity", jobKey, completePair(fingerprint, "activity-1"));

    Optional<ObjectNode> reused =
        readCompleted(journal, sourceBatch, "activity", jobKey, fingerprint);

    assertThat(reused).as("a complete DRAFT plus REVIEW is reusable").isPresent();
    assertThat(reused.orElseThrow().path("reviewedActivities").isArray()).isTrue();
    assertThat(reused.orElseThrow().path("reviewedActivities")).isNotEmpty();
    assertThat(reused.orElseThrow().path("draft").isObject()).isTrue();
    assertThat(reused.orElseThrow().path("review").isObject()).isTrue();
  }

  @Test
  void isolatedDraftAndReviewFailureAreNotReusable() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("incomplete-activity"));
    AnalysisRunId sourceBatch = runId('b');
    String fingerprint = "2".repeat(64);
    write(
        journal,
        sourceBatch,
        "activity",
        "activity-draft-only",
        incompletePair(fingerprint, "DRAFT_ONLY"));
    write(
        journal,
        sourceBatch,
        "activity",
        "activity-review-failed",
        incompletePair(fingerprint, "REVIEW_FAILED"));

    assertThat(readCompleted(journal, sourceBatch, "activity", "activity-draft-only", fingerprint))
        .as("an isolated DRAFT must cause a fresh DRAFT plus REVIEW")
        .isEmpty();
    assertThat(
            readCompleted(journal, sourceBatch, "activity", "activity-review-failed", fingerprint))
        .as("a failed REVIEW must not be treated as complete")
        .isEmpty();
  }

  @Test
  void damagedDeclaredCompleteResultFailsClosedInsteadOfTriggeringImplicitRetry() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("damaged-activity"));
    AnalysisRunId sourceBatch = runId('c');
    String jobKey = "activity-damaged";
    String fingerprint = "3".repeat(64);
    write(journal, sourceBatch, "activity", jobKey, completePair(fingerprint, "activity-1"));

    Path result;
    try (Stream<Path> paths = Files.walk(journal)) {
      result =
          paths
              .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
              .findFirst()
              .orElseThrow();
    }
    Files.writeString(result, "{\"status\":\"COMPLETED\"}", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> readCompleted(journal, sourceBatch, "activity", jobKey, fingerprint))
        .hasMessageStartingWith("MODEL_JOB_RESULT_");
  }

  @Test
  void inputOrProviderBindingMismatchDoesNotReuseEvenWhenResultBytesLookValid() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("mismatch-activity"));
    AnalysisRunId sourceBatch = runId('d');
    String jobKey = "activity-mismatch";
    String storedFingerprint = "4".repeat(64);
    write(journal, sourceBatch, "activity", jobKey, completePair(storedFingerprint, "activity-1"));

    assertThat(readCompleted(journal, sourceBatch, "activity", jobKey, "5".repeat(64)))
        .as("changed clean input or profile requires a fresh pair")
        .isEmpty();
    assertThat(
            readCompleted(
                journal,
                sourceBatch,
                "activity",
                jobKey,
                storedFingerprint,
                QUOTA_SCOPE,
                new ModelRuntimeIdentityV1("scripted", "other-model", "high", "read-only")))
        .as("changed Provider/model binding requires a fresh pair")
        .isEmpty();
    assertThat(
            readCompleted(
                journal,
                sourceBatch,
                "activity",
                jobKey,
                storedFingerprint,
                "other-account",
                IDENTITY))
        .as("changed account/quota scope requires a fresh pair")
        .isEmpty();
  }

  @Test
  void sameLocalEntryKeyFromDifferentMaterialsNeverSharesAReusableJob() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("material-scope"));
    AnalysisRunId sourceBatch = runId('e');
    write(
        journal,
        sourceBatch,
        "activity",
        "material-a-E1",
        completePair("6".repeat(64), "activity-material-a"));
    write(
        journal,
        sourceBatch,
        "activity",
        "material-b-E1",
        completePair("7".repeat(64), "activity-material-b"));

    ObjectNode first =
        readCompleted(journal, sourceBatch, "activity", "material-a-E1", "6".repeat(64))
            .orElseThrow();
    ObjectNode second =
        readCompleted(journal, sourceBatch, "activity", "material-b-E1", "7".repeat(64))
            .orElseThrow();
    assertThat(first.path("reviewedActivities").get(0).path("activityId").asText())
        .isEqualTo("activity-material-a");
    assertThat(second.path("reviewedActivities").get(0).path("activityId").asText())
        .isEqualTo("activity-material-b");
  }

  @Test
  void matchingProcessGroupPairIsReusableButChangedActivitySetIsNot() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("process-group"));
    AnalysisRunId sourceBatch = runId('f');
    String jobKey = "process-group-001";
    String fingerprint = "8".repeat(64);
    write(
        journal,
        sourceBatch,
        "process-group",
        jobKey,
        completePair(fingerprint, "process-1", "process-group"));

    assertThat(readCompleted(journal, sourceBatch, "process-group", jobKey, fingerprint))
        .as("unchanged process-group input is reusable without Provider calls")
        .isPresent();
    assertThat(readCompleted(journal, sourceBatch, "process-group", jobKey, "9".repeat(64)))
        .as("changed activity membership invalidates the process result")
        .isEmpty();
  }

  private static void write(
      Path journal, AnalysisRunId batch, String phase, String jobKey, ObjectNode result) {
    new PrivateModelJobResultStore(journal, batch, phase).write(jobKey, result);
  }

  private static Optional<ObjectNode> readCompleted(
      Path journal, AnalysisRunId batch, String phase, String jobKey, String fingerprint)
      throws Exception {
    return readCompleted(journal, batch, phase, jobKey, fingerprint, QUOTA_SCOPE, IDENTITY);
  }

  @SuppressWarnings("unchecked")
  private static Optional<ObjectNode> readCompleted(
      Path journal,
      AnalysisRunId batch,
      String phase,
      String jobKey,
      String fingerprint,
      String quotaScope,
      ModelRuntimeIdentityV1 identity)
      throws Exception {
    Method method;
    try {
      method =
          PrivateModelJobResultStore.class.getMethod(
              "readCompleted",
              String.class,
              String.class,
              String.class,
              ModelRuntimeIdentityV1.class);
    } catch (NoSuchMethodException missing) {
      fail("PRIVATE_MODEL_JOB_RESULT_COMPLETED_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    Object store = new PrivateModelJobResultStore(journal, batch, phase);
    try {
      Object result = method.invoke(store, jobKey, fingerprint, quotaScope, identity);
      assertThat(result).isInstanceOf(Optional.class);
      return (Optional<ObjectNode>) result;
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static ObjectNode completePair(String fingerprint, String resultId) {
    return completePair(fingerprint, resultId, "activity");
  }

  private static ObjectNode completePair(String fingerprint, String resultId, String phase) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", "model-job-reviewed-result-v2");
    value.put("status", "COMPLETED");
    value.put("inputFingerprint", fingerprint);
    value.put("providerBindingKey", "scripted");
    value.put("quotaScope", QUOTA_SCOPE);
    value.put("phase", phase);
    value
        .putObject("runtimeIdentity")
        .put("upstreamProvider", IDENTITY.upstreamProvider())
        .put("model", IDENTITY.model())
        .put("reasoningEffort", IDENTITY.reasoningEffort())
        .put("sandbox", IDENTITY.sandbox());
    value.putObject("draft").put("resultId", resultId + "-draft");
    value.putObject("review").put("resultId", resultId + "-review");
    value.putArray("reviewedActivities").addObject().put("activityId", resultId);
    value.putArray("coverage");
    value.putArray("unexplainedActivityEntries");
    return value;
  }

  private static ObjectNode incompletePair(String fingerprint, String status) {
    ObjectNode value = completePair(fingerprint, status.toLowerCase());
    value.put("status", status);
    if ("DRAFT_ONLY".equals(status)) {
      value.remove("review");
      value.remove("reviewedActivities");
      value.remove("coverage");
      value.remove("unexplainedActivityEntries");
    } else {
      value.remove("draft");
    }
    return value;
  }

  private static AnalysisRunId runId(char fill) {
    return AnalysisRunId.parse("analysis-run:" + String.valueOf(fill).repeat(64));
  }
}
