package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobCapacityProfile;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/**
 * RED contract for direct Step05 Activity retry semantics.
 *
 * <p>The model-facing assertions intentionally exercise only the stable Activity request boundary:
 * an already-verified DRAFT is never regenerated when REVIEW is retried, invalid model output does
 * not acquire an implicit retry, and an outcome-unknown timeout never starts a second request in
 * the same batch.
 */
class ActivityStageRetryExecutionTest {

  @Test
  void rejectsAnOversizedVisibleRequestBeforeStartingTheProvider(@TempDir Path temporary)
      throws Exception {
    AtomicInteger calls = new AtomicInteger();
    StructuredModelProvider provider =
        request -> {
          calls.incrementAndGet();
          throw new AssertionError("capacity failure must precede the model request");
        };
    ModelJobProviderBinding binding =
        new ModelJobProviderBinding(
            "pro",
            "capacity-fixture",
            1,
            List.of(provider),
            ActivityStageAttemptStoreTest.IDENTITY,
            new ModelJobCapacityProfile(20, 0, 0, "UTF8_BYTE_ESTIMATE"));
    ActivityExplainer explainer =
        ActivityExplainer.forExecution(
            new ModelJobExecutionConfiguration(
                1,
                Map.of("pro", binding),
                Map.of(
                    "activity", List.of("pro"),
                    "processGroup", List.of("pro"),
                    "repositorySummary", List.of("pro"),
                    "report", List.of("pro")),
                Files.createDirectory(temporary.resolve("journal")),
                AnalysisRunId.parse("analysis-run:" + "7".repeat(64))));

    ActivityExplanationResult result =
        ActivityStageAttemptStoreTest.explain(
            explainer,
            ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));

    assertThat(calls).hasValue(0);
    assertThat(result.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));
  }

  @Test
  void explicitlyConfiguredInvalidJsonRetriesOnlyTheFailedDraft(@TempDir Path temporary)
      throws Exception {
    ActivityStageAttemptStoreTest.SuccessfulProvider delegate =
        new ActivityStageAttemptStoreTest.SuccessfulProvider();
    AtomicInteger draftCalls = new AtomicInteger();
    StructuredModelProvider provider =
        request -> {
          if ("ACTIVITY_DRAFT".equals(request.taskKind()) && draftCalls.incrementAndGet() == 1) {
            return new StructuredModelResponse(
                ImmutableBytes.copyOf("{broken".getBytes(StandardCharsets.UTF_8)),
                ActivityStageAttemptStoreTest.IDENTITY);
          }
          return delegate.generate(request);
        };
    ActivityRetryProfile retry =
        new ActivityRetryProfile(2, 0, 0, 2.0, 0.0, Set.of("INVALID_JSON"), Map.of());

    ActivityStageAttemptStoreTest.explain(
        multiProviderExplainer(
            provider,
            Files.createDirectory(temporary.resolve("retry-invalid-json")),
            AnalysisRunId.parse("analysis-run:" + "6".repeat(64)),
            null,
            retry),
        ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));

    assertThat(draftCalls).hasValue(2);
    assertThat(delegate.callsByKind()).containsEntry("ACTIVITY_REVIEW", 1);
  }

  @Test
  void retainsOneSuccessfulDraftWhileReviewFailsTwiceThenSucceeds(@TempDir Path temporary)
      throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("retry-journal"));
    RetryingReviewProvider provider = new RetryingReviewProvider();

    ActivityStageAttemptStoreTest.explain(
        ActivityStageAttemptStoreTest.configuredExplainer(provider, journal),
        ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));

    assertThat(provider.draftCalls).isEqualTo(1);
    assertThat(provider.reviewCalls).isEqualTo(3);
    assertThat(provider.reviewInputs)
        .as("every REVIEW retry sees the same complete successful DRAFT and material input")
        .allSatisfy(
            input -> {
              assertThat(input.path("actualDraft").isObject()).isTrue();
              assertThat(input.path("actualDraft"))
                  .isEqualTo(provider.reviewInputs.get(0).path("actualDraft"));
            });
    assertThat(provider.reviewInputs)
        .extracting(JsonNode::toString)
        .containsOnly(provider.reviewInputs.get(0).toString());

    try (var paths = Files.walk(journal)) {
      List<Path> reviewAttempts =
          paths
              .filter(path -> path.getFileName().toString().matches("attempt-[1-3]"))
              .filter(path -> path.getParent().getFileName().toString().equals("REVIEW"))
              .toList();
      assertThat(reviewAttempts).hasSize(3);
    }
  }

  @Test
  void doesNotRetryBadJsonOrAnUnknownSourceReferenceByDefault(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05"));

    BadJsonProvider badJson = new BadJsonProvider();
    ActivityExplanationResult badJsonResult =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(
                badJson, Files.createDirectory(temporary.resolve("bad-json"))),
            materials);
    assertThat(badJsonResult.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));
    assertThat(badJson.calls).isEqualTo(1);

    UnknownReferenceProvider unknownReference = new UnknownReferenceProvider();
    ActivityExplanationResult unknownResult =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(
                unknownReference, Files.createDirectory(temporary.resolve("unknown-ref"))),
            materials);
    assertThat(unknownResult.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));
    assertThat(unknownReference.calls).isEqualTo(1);
  }

  @Test
  void refusesSameBatchRetryWhenTheTimedOutRequestOutcomeIsUnknown(@TempDir Path temporary)
      throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("uncertain-timeout"));
    OutcomeUnknownProvider provider = new OutcomeUnknownProvider();

    ActivityExplanationResult result =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(provider, journal),
            ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));
    assertThat(result.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));

    assertThat(provider.draftCalls).isEqualTo(1);
    assertThat(provider.reviewCalls)
        .as("a possibly still-live request prohibits a second same-stage request in this batch")
        .isEqualTo(1);
    assertThat(provider.maximumUnresolvedReviewRequests).hasValue(1);
  }

  @Test
  void savesTheActualInvalidCodexResponsePrivatelyWithoutRetry(@TempDir Path temporary)
      throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("invalid-response-journal"));
    AtomicInteger calls = new AtomicInteger();
    ImmutableBytes invalid = ImmutableBytes.copyOf("{not-json".getBytes(StandardCharsets.UTF_8));
    StructuredModelProvider provider =
        request -> {
          calls.incrementAndGet();
          throw new StructuredModelProviderFailure(
              "INVALID_JSON", true, true, "MODEL_RESPONSE_INVALID", null, invalid);
        };

    ActivityExplanationResult result =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(provider, journal),
            ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));
    assertThat(result.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));

    assertThat(calls).hasValue(1);
    try (var paths = Files.walk(journal)) {
      List<Path> responseFiles =
          paths.filter(path -> path.getFileName().toString().equals("response.json")).toList();
      assertThat(responseFiles).singleElement();
      JsonNode response =
          new CanonicalJsonCodec()
              .parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(responseFiles.get(0))));
      assertThat(response.path("responseBase64").asText())
          .isEqualTo(java.util.Base64.getEncoder().encodeToString(invalid.copyToByteArray()));
      assertThat(response.path("invalidFromProvider").asBoolean()).isTrue();
    }
  }

  @Test
  void configuredSingleAttemptDoesNotRetryATransientReview(@TempDir Path temporary)
      throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("single-attempt"));
    RetryingReviewProvider provider = new RetryingReviewProvider();
    ActivityRetryProfile singleAttempt =
        new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of("TRANSIENT_TRANSPORT"), Map.of());
    ActivityExplainer explainer =
        ActivityExplainer.forExecution(
            provider,
            new ActivityJobExecutionConfiguration(
                1,
                "pro",
                "retry-fixture-scope",
                journal,
                new AnalysisRunId("analysis-run:" + "d".repeat(64)),
                ActivityStageAttemptStoreTest.IDENTITY,
                singleAttempt));

    ActivityExplanationResult result =
        ActivityStageAttemptStoreTest.explain(
            explainer,
            ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05")));
    assertThat(result.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));

    assertThat(provider.draftCalls).isEqualTo(1);
    assertThat(provider.reviewCalls).isEqualTo(1);
    try (var paths = Files.walk(journal)) {
      Path failureFile =
          paths
              .filter(path -> path.getFileName().toString().equals("failed-result.json"))
              .findFirst()
              .orElseThrow();
      JsonNode failure =
          new CanonicalJsonCodec()
              .parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(failureFile)));
      assertThat(failure.path("stageKey").asText()).isEqualTo("REVIEW");
      assertThat(failure.path("attemptsUsed").asInt()).isEqualTo(1);
      assertThat(failure.path("maxAttempts").asInt()).isEqualTo(1);
      assertThat(failure.path("reusableSuccessfulStages").get(0).asText()).isEqualTo("DRAFT");
    }
  }

  @Test
  void newBatchExplicitlyReusesVerifiedDraftAndRunsOnlyFailedReview(@TempDir Path temporary)
      throws Exception {
    Path journal = Files.createDirectory(temporary.resolve("cross-batch"));
    var materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(temporary.resolve("step05"));
    AnalysisRunId failedRun = new AnalysisRunId("analysis-run:" + "a".repeat(64));
    AnalysisRunId resumedRun = new AnalysisRunId("analysis-run:" + "b".repeat(64));
    ActivityRetryProfile oneAttempt =
        new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of("TRANSIENT_TRANSPORT"), Map.of());
    RetryingReviewProvider failing = new RetryingReviewProvider();
    ActivityExplanationResult failed =
        ActivityStageAttemptStoreTest.explain(
            multiProviderExplainer(failing, journal, failedRun, null, oneAttempt), materials);
    assertThat(failed.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));
    assertThat(failing.draftCalls).isEqualTo(1);
    assertThat(failing.reviewCalls).isEqualTo(1);

    RetryingReviewProvider resumed = new RetryingReviewProvider();
    resumed.reviewCalls = 2;
    ActivityStageAttemptStoreTest.explain(
        multiProviderExplainer(resumed, journal, resumedRun, failedRun, oneAttempt), materials);
    assertThat(resumed.draftCalls).isZero();
    assertThat(resumed.reviewCalls).isEqualTo(3);
  }

  private static ActivityExplainer multiProviderExplainer(
      StructuredModelProvider provider,
      Path journal,
      AnalysisRunId runId,
      AnalysisRunId reuseFrom,
      ActivityRetryProfile retry) {
    ModelJobProviderBinding binding =
        new ModelJobProviderBinding(
            "pro", "retry-fixture-scope", 1, provider, ActivityStageAttemptStoreTest.IDENTITY);
    Map<String, List<String>> routes =
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro"));
    return ActivityExplainer.forExecution(
        new ModelJobExecutionConfiguration(
            1, Map.of("pro", binding), routes, journal, runId, reuseFrom, retry));
  }

  private static final class RetryingReviewProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<JsonNode> reviewInputs = new ArrayList<>();
    private int draftCalls;
    private int reviewCalls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if ("ACTIVITY_DRAFT".equals(request.taskKind())) {
        draftCalls++;
        return response("{\"activities\":[]}");
      }
      reviewCalls++;
      reviewInputs.add(canonicalJson.parseCanonical(request.untrustedInputJson()));
      if (reviewCalls < 3) {
        throw providerFailure("TRANSIENT_TRANSPORT", true, true);
      }
      return response("{\"activities\":[],\"unexplainedEntries\":[\"E1\"]}");
    }
  }

  private static final class BadJsonProvider implements StructuredModelProvider {
    private int calls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      calls++;
      return response("{not-json");
    }
  }

  private static final class UnknownReferenceProvider implements StructuredModelProvider {
    private int calls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      calls++;
      return response(responseWithUnknownReference());
    }
  }

  private static final class OutcomeUnknownProvider implements StructuredModelProvider {
    private final AtomicInteger unresolvedReviewRequests = new AtomicInteger();
    private final AtomicInteger maximumUnresolvedReviewRequests = new AtomicInteger();
    private int draftCalls;
    private int reviewCalls;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if ("ACTIVITY_DRAFT".equals(request.taskKind())) {
        draftCalls++;
        return response("{\"activities\":[]}");
      }
      reviewCalls++;
      int unresolved = unresolvedReviewRequests.incrementAndGet();
      maximumUnresolvedReviewRequests.accumulateAndGet(unresolved, Math::max);
      if (unresolved > 1) {
        throw new AssertionError("a possibly-live REVIEW request was overlapped");
      }
      throw providerFailure("OUTCOME_UNKNOWN", true, false);
    }
  }

  private static StructuredModelResponse response(String json) {
    return new StructuredModelResponse(
        ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8)),
        ActivityStageAttemptStoreTest.IDENTITY);
  }

  private static String responseWithUnknownReference() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode activity = root.putArray("activities").addObject();
    activity.put("activityLocalId", "activity-unknown-ref");
    activity.putArray("entryKeys").add("E1");
    activity.put("name", "unknown reference");
    activity.put("businessPurpose", "must be rejected");
    activity.putArray("participants");
    activity.putArray("businessObjects");
    activity.putArray("triggerOrInput");
    activity.putArray("conditions");
    activity.putArray("activitySteps").add("must not be accepted");
    activity.putArray("codeDefinedResults").add("must not be accepted");
    activity.putArray("businessRules");
    activity.putArray("formulasOrMetrics");
    activity.putArray("terms");
    activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
    activity.putArray("sourceRefs").add("not-an-allowlisted-packet-ref");
    activity.putArray("questions");
    activity.putArray("scopeLimitations");
    return new String(
        new CanonicalJsonCodec().encodeCanonical(root).copyToByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * Expected structured failure from a Provider after a request has started.
   *
   * <p>The execution contract must not derive retry eligibility from exception message text. The
   * required public classification carries the reason and whether the request is known to have
   * ended, allowing the Activity coordinator to distinguish retryable `REQUEST_TIMEOUT` from
   * `OUTCOME_UNKNOWN`.
   */
  private static RuntimeException providerFailure(
      String reasonCode, boolean requestStarted, boolean requestEnded) {
    try {
      Class<?> type =
          Class.forName("org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure");
      if (!RuntimeException.class.isAssignableFrom(type)) {
        throw new AssertionError("StructuredModelProviderFailure must be a RuntimeException");
      }
      Constructor<?> constructor = type.getConstructor(String.class, boolean.class, boolean.class);
      return (RuntimeException) constructor.newInstance(reasonCode, requestStarted, requestEnded);
    } catch (ClassNotFoundException absent) {
      throw new AssertionError("missing structured Provider retry classification", absent);
    } catch (ReflectiveOperationException invalidContract) {
      throw new AssertionError(
          "invalid structured Provider retry classification contract", invalidContract);
    }
  }
}
