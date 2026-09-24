package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;

class ActivityRetryPacketScopeTest {

  @Test
  void selectsFailedPacketsWithoutTreatingSampleOmissionsAsFailures() {
    List<CodeReadingMaterialSet.EntryCoverage> materialCoverage =
        List.of(
            entry("entry:failed", "packet:failed"),
            entry("entry:sample-omitted", "packet:omitted"),
            entry("entry:complete", "packet:complete"));
    List<ActivityEntryCoverage> activityCoverage =
        List.of(
            new ActivityEntryCoverage(
                "entry:failed", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT"),
            new ActivityEntryCoverage(
                "entry:sample-omitted",
                "NOT_ANALYZED",
                List.of(),
                "NOT_SELECTED_FOR_ACTIVITY_BATCH"),
            new ActivityEntryCoverage(
                "entry:complete", "ANALYZED", List.of("activity:complete"), null));

    assertThat(SourceAnalysisExecution.retryablePacketIds(materialCoverage, activityCoverage))
        .containsExactly("packet:failed");
  }

  @Test
  void retriesIncompletePacketCompletionEvenWhenEntryCoverageHasNoRetryReason() {
    List<CodeReadingMaterialSet.EntryCoverage> materialCoverage =
        List.of(
            entry("entry:incomplete", "packet:incomplete"),
            entry("entry:complete", "packet:complete"));
    List<ActivityEntryCoverage> activityCoverage =
        List.of(
            new ActivityEntryCoverage(
                "entry:incomplete", "ANALYZED_WITH_GAPS", List.of("activity:incomplete"), null),
            new ActivityEntryCoverage(
                "entry:complete", "ANALYZED", List.of("activity:complete"), null));
    List<ActivityPacketCompletion> packetCompletion =
        List.of(
            new ActivityPacketCompletion(
                "packet:incomplete",
                List.of("entry:incomplete"),
                ActivityPacketCompletion.Completion.INCOMPLETE,
                List.of("slice:complete", "slice:missing"),
                List.of("slice:complete"),
                List.of(
                    new ActivityPacketCompletion.IncompleteScope(
                        "slice:missing",
                        List.of("entry:incomplete"),
                        "ACTIVITY_READING_INCOMPLETE"))),
            new ActivityPacketCompletion(
                "packet:complete",
                List.of("entry:complete"),
                ActivityPacketCompletion.Completion.COMPLETE,
                List.of("whole-packet"),
                List.of("whole-packet"),
                List.of()));

    assertThat(
            SourceAnalysisExecution.retryablePacketIds(
                materialCoverage, activityCoverage, packetCompletion))
        .containsExactly("packet:incomplete");
  }

  @Test
  void targetedRetryKeepsOtherIncompletePartialAndCompletePacketsUntouched() {
    List<CodeReadingMaterialSet.EntryCoverage> materialCoverage =
        List.of(
            entry("entry:f1", "packet:f1"),
            entry("entry:f2", "packet:f2"),
            entry("entry:complete", "packet:complete"));
    ActivityPacketCompletion newF1Completion =
        completion("packet:f1", "entry:f1", "slice:f1-retry");
    ActivityPacketCompletion oldF1Completion =
        incompleteCompletion("packet:f1", "entry:f1", "slice:f1-old-gap");
    ActivityPacketCompletion oldF2Completion =
        incompleteCompletion("packet:f2", "entry:f2", "slice:f2-gap");
    ActivityPacketCompletion oldCompleteCompletion =
        completion("packet:complete", "entry:complete", "whole-packet");
    ActivityPacketCompletion newF2Completion =
        undeterminedCompletion("packet:f2", "entry:f2", "NOT_SELECTED_FOR_ACTIVITY_BATCH");
    ActivityPacketCompletion newCompleteCompletion =
        undeterminedCompletion(
            "packet:complete", "entry:complete", "NOT_SELECTED_FOR_ACTIVITY_BATCH");

    ReviewedActivity oldF2Partial =
        activity("activity:f2-prior-slice", "packet:f2", "entry:f2", "F2 successful slice body");
    ReviewedActivity oldComplete =
        activity("activity:complete-prior", "packet:complete", "entry:complete", "complete body");
    ReviewedActivity newF1 =
        activity("activity:f1-retried", "packet:f1", "entry:f1", "F1 retried body");
    ActivityEntryCoverage newF1Coverage =
        new ActivityEntryCoverage("entry:f1", "ANALYZED", List.of("activity:f1-retried"), null);
    ActivityEntryCoverage oldF1Coverage =
        new ActivityEntryCoverage("entry:f1", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT");
    ActivityEntryCoverage newF2Coverage =
        new ActivityEntryCoverage(
            "entry:f2", "NOT_ANALYZED", List.of(), "NOT_SELECTED_FOR_ACTIVITY_BATCH");
    ActivityEntryCoverage oldF2Coverage =
        new ActivityEntryCoverage(
            "entry:f2",
            "ANALYZED_WITH_GAPS",
            List.of("activity:f2-prior-slice"),
            "ACTIVITY_READING_INCOMPLETE");
    ActivityEntryCoverage newCompleteCoverage =
        new ActivityEntryCoverage(
            "entry:complete", "NOT_ANALYZED", List.of(), "NOT_SELECTED_FOR_ACTIVITY_BATCH");
    ActivityEntryCoverage oldCompleteCoverage =
        new ActivityEntryCoverage(
            "entry:complete", "ANALYZED", List.of("activity:complete-prior"), null);

    ActivityExplanationResult executed =
        result(
            List.of(newF1),
            List.of(newF1Coverage, newF2Coverage, newCompleteCoverage),
            List.of(newF1Completion, newF2Completion, newCompleteCompletion));
    ActivityExplanationResult reusable =
        result(
            List.of(oldF2Partial, oldComplete),
            List.of(oldF1Coverage, oldF2Coverage, oldCompleteCoverage),
            List.of(oldF1Completion, oldF2Completion, oldCompleteCompletion));
    assertThat(
            SourceAnalysisExecution.retryablePacketIds(
                materialCoverage, reusable.coverage(), reusable.packetCompletion().orElseThrow()))
        .containsExactly("packet:f1", "packet:f2");

    ActivityExplanationResult carried =
        SourceAnalysisExecution.carryForwardCompletePackets(
            materialCoverage, executed, reusable, java.util.Set.of("packet:f1"));

    assertThat(executed.reviewedActivities())
        .extracting(ReviewedActivity::materialId)
        .containsExactly("packet:f1");
    assertThat(carried.reviewedActivities())
        .extracting(ReviewedActivity::activityId)
        .containsExactly(
            "activity:complete-prior", "activity:f1-retried", "activity:f2-prior-slice");
    assertThat(carried.reviewedActivities())
        .extracting(ReviewedActivity::activitySteps)
        .containsExactly(
            List.of("complete body"),
            List.of("F1 retried body"),
            List.of("F2 successful slice body"));
    assertThat(carried.coverage())
        .containsExactly(newF1Coverage, oldF2Coverage, oldCompleteCoverage);
    assertThat(carried.packetCompletion().orElseThrow())
        .containsExactly(newF1Completion, oldF2Completion, oldCompleteCompletion);
    assertThat(carried.packetCompletion().orElseThrow().get(1).completion())
        .isEqualTo(ActivityPacketCompletion.Completion.INCOMPLETE);
    assertThat(carried.packetCompletion().orElseThrow().get(1).completedSliceKeys())
        .containsExactly("slice:complete");
    assertThat(carried.packetCompletion().orElseThrow().get(1).incompleteScopes())
        .singleElement()
        .satisfies(scope -> assertThat(scope.sliceKey()).isEqualTo("slice:f2-gap"));
  }

  @Test
  void retryingOneEntrySelectsEveryPacketThatSharesIt() {
    List<CodeReadingMaterialSet.EntryCoverage> materialCoverage =
        List.of(
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:shared",
                List.of("packet:first", "packet:second"),
                CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                List.of()));
    List<ActivityEntryCoverage> activityCoverage =
        List.of(
            new ActivityEntryCoverage(
                "entry:shared", "ANALYZED_WITH_GAPS", List.of("activity:shared"), null));
    List<ActivityPacketCompletion> packetCompletion =
        List.of(
            incompleteCompletion("packet:first", "entry:shared", "slice:missing"),
            completion("packet:second", "entry:shared", "whole-packet"));

    assertThat(
            SourceAnalysisExecution.retryablePacketIds(
                materialCoverage, activityCoverage, packetCompletion))
        .containsExactly("packet:first", "packet:second");
  }

  @Test
  void sampleCanSelectTwoExactPacketsSoTheSecondBatchReusesTheFirstSample() {
    assertThat(SourceAnalysisExecution.selectedInitialPackets("packet:first,packet:second"))
        .containsExactly("packet:first", "packet:second");
    assertThatThrownBy(() -> SourceAnalysisExecution.selectedInitialPackets("packet:first,"))
        .hasMessageContaining("ACTIVITY_PACKET_SELECTION_INVALID");
  }

  @Test
  void attachesSavedPacketFailureWithoutInventingAStageForAnUpstreamGap() {
    ObjectNode saved =
        JsonNodeFactory.instance
            .objectNode()
            .put("materialId", "packet:failed")
            .put("stageKey", "REVIEW")
            .put("attemptsUsed", 3)
            .put("maxAttempts", 3);
    ObjectNode failed = JsonNodeFactory.instance.objectNode();
    SourceAnalysisExecution.appendActivityFailureDetails(
        failed, List.of("packet:failed"), Map.of("packet:failed", saved));
    assertThat(failed.path("packetFailures").size()).isEqualTo(1);
    assertThat(failed.path("packetFailures").get(0).path("stageKey").asText()).isEqualTo("REVIEW");
    assertThat(failed.path("packetFailures").get(0).path("attemptsUsed").asInt()).isEqualTo(3);

    ObjectNode navigationGap = JsonNodeFactory.instance.objectNode();
    SourceAnalysisExecution.appendActivityFailureDetails(
        navigationGap, List.of(), Map.of("packet:failed", saved));
    assertThat(navigationGap.path("packetFailures")).isEmpty();
  }

  @Test
  void limitedButCollectedSourceStillCountsWhenDecidingWhetherModelBatchFailed() {
    List<CodeReadingMaterialSet.EntryCoverage> source =
        List.of(
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:limited",
                List.of("packet:limited"),
                CodeReadingMaterialSet.CoverageStatus.COLLECTED_WITH_LIMITATIONS,
                List.of("JDT_CALL_BOUNDARY")),
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:upstream-gap",
                List.of(),
                CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                List.of("JDT_NAVIGATION_TIMEOUT")));
    ActivityEntryCoverage upstreamGap =
        new ActivityEntryCoverage(
            "entry:upstream-gap", "NOT_ANALYZED", List.of(), "JDT_NAVIGATION_TIMEOUT");

    assertThat(
            SourceAnalysisExecution.step05ActivityBatchComplete(
                source,
                List.of(
                    new ActivityEntryCoverage(
                        "entry:limited", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT"),
                    upstreamGap)))
        .isFalse();
    assertThat(
            SourceAnalysisExecution.step05ActivityBatchComplete(
                source,
                List.of(
                    new ActivityEntryCoverage(
                        "entry:limited", "ANALYZED_WITH_GAPS", List.of("activity:limited"), null),
                    upstreamGap)))
        .isTrue();
  }

  @Test
  void sampleCliKeepsUnselectedEntriesInCoverageWithoutPrintingEachOne() {
    List<ActivityEntryCoverage> coverage =
        List.of(
            new ActivityEntryCoverage(
                "entry:unselected", "NOT_ANALYZED", List.of(), "NOT_SELECTED_FOR_ACTIVITY_BATCH"),
            new ActivityEntryCoverage(
                "entry:failed", "NOT_ANALYZED", List.of(), "ACTIVITY_READING_INCOMPLETE"),
            new ActivityEntryCoverage(
                "entry:upstream-gap", "NOT_ANALYZED", List.of(), "JDT_NAVIGATION_TIMEOUT"));

    assertThat(SourceAnalysisExecution.reportableUnprocessedEntries(coverage))
        .extracting(ActivityEntryCoverage::entryId)
        .containsExactly("entry:failed", "entry:upstream-gap");
  }

  private static CodeReadingMaterialSet.EntryCoverage entry(String entryId, String packetId) {
    return new CodeReadingMaterialSet.EntryCoverage(
        entryId, List.of(packetId), CodeReadingMaterialSet.CoverageStatus.COLLECTED, List.of());
  }

  private static ActivityExplanationResult result(
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage,
      List<ActivityPacketCompletion> packetCompletion) {
    return new ActivityExplanationResult(activities, coverage, List.of(), packetCompletion, null);
  }

  private static ReviewedActivity activity(
      String activityId, String packetId, String entryId, String body) {
    return new ReviewedActivity(
        activityId,
        packetId,
        List.of(entryId),
        "Name " + body,
        "Purpose " + body,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(body),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "HIGH",
        List.of(),
        List.of(),
        List.of());
  }

  private static ActivityPacketCompletion completion(
      String packetId, String entryId, String sliceKey) {
    return new ActivityPacketCompletion(
        packetId,
        List.of(entryId),
        ActivityPacketCompletion.Completion.COMPLETE,
        List.of(sliceKey),
        List.of(sliceKey),
        List.of());
  }

  private static ActivityPacketCompletion incompleteCompletion(
      String packetId, String entryId, String missingSliceKey) {
    return new ActivityPacketCompletion(
        packetId,
        List.of(entryId),
        ActivityPacketCompletion.Completion.INCOMPLETE,
        List.of("slice:complete", missingSliceKey),
        List.of("slice:complete"),
        List.of(
            new ActivityPacketCompletion.IncompleteScope(
                missingSliceKey, List.of(entryId), "ACTIVITY_READING_INCOMPLETE")));
  }

  private static ActivityPacketCompletion undeterminedCompletion(
      String packetId, String entryId, String reasonCode) {
    return new ActivityPacketCompletion(
        packetId,
        List.of(entryId),
        ActivityPacketCompletion.Completion.UNDETERMINED,
        List.of("whole-packet"),
        List.of(),
        List.of(
            new ActivityPacketCompletion.IncompleteScope(
                "whole-packet", List.of(entryId), reasonCode)));
  }
}
