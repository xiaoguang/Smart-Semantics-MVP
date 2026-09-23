package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
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
  void retryScopeIncludesCompletedPacketsOnlyWhenTheyCanBeReused() {
    List<CodeReadingMaterialSet.EntryCoverage> source =
        List.of(entry("entry:failed", "packet:failed"), entry("entry:done", "packet:done"));
    List<ActivityEntryCoverage> failedBatch =
        List.of(
            new ActivityEntryCoverage(
                "entry:failed", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT"),
            new ActivityEntryCoverage("entry:done", "ANALYZED", List.of("activity:done"), null));

    assertThat(
            SourceAnalysisExecution.retryAndReusePacketIds(source, failedBatch, failedBatch, null))
        .containsExactlyInAnyOrder("packet:failed", "packet:done");
    assertThat(SourceAnalysisExecution.retryAndReusePacketIds(source, failedBatch, null, null))
        .containsExactly("packet:failed");
  }

  @Test
  void oneIncompleteEntryPreventsReusingItsSharedPacket() {
    List<CodeReadingMaterialSet.EntryCoverage> source =
        List.of(
            entry("entry:first", "packet:shared"),
            entry("entry:second", "packet:shared"),
            entry("entry:retry", "packet:retry"));
    List<ActivityEntryCoverage> retryBatch =
        List.of(
            new ActivityEntryCoverage("entry:first", "ANALYZED", List.of("activity:first"), null),
            new ActivityEntryCoverage("entry:second", "ANALYZED", List.of("activity:second"), null),
            new ActivityEntryCoverage(
                "entry:retry", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT"));
    List<ActivityEntryCoverage> reuseBatch =
        List.of(
            retryBatch.get(0),
            new ActivityEntryCoverage(
                "entry:second", "NOT_ANALYZED", List.of(), "MODEL_PROVIDER_TIMEOUT"),
            retryBatch.get(2));

    assertThat(SourceAnalysisExecution.retryAndReusePacketIds(source, retryBatch, reuseBatch, null))
        .containsExactly("packet:retry");
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
}
