package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityPacketCompletionTest {

  @Test
  void preservesAllThreeDispositionsAndDistinguishesLegacyAbsenceFromAnExplicitEmptySet() {
    ActivityPacketCompletion complete =
        new ActivityPacketCompletion(
            "packet:complete",
            List.of("entry:complete"),
            ActivityPacketCompletion.Completion.COMPLETE,
            List.of("whole-packet"),
            List.of("whole-packet"),
            List.of());
    ActivityPacketCompletion incomplete =
        new ActivityPacketCompletion(
            "packet:incomplete",
            List.of("entry:incomplete"),
            ActivityPacketCompletion.Completion.INCOMPLETE,
            List.of("whole-packet", "slice:required"),
            List.of("whole-packet"),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    "slice:required", List.of("entry:incomplete"), "REVIEW_FAILED")));
    ActivityPacketCompletion undetermined =
        new ActivityPacketCompletion(
            "packet:undetermined",
            List.of("entry:undetermined"),
            ActivityPacketCompletion.Completion.UNDETERMINED,
            List.of(),
            List.of(),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    null, List.of("entry:undetermined"), "HISTORICAL_REQUIRED_SCOPE_UNKNOWN")));

    assertThat(List.of(complete.completion(), incomplete.completion(), undetermined.completion()))
        .containsExactly(
            ActivityPacketCompletion.Completion.COMPLETE,
            ActivityPacketCompletion.Completion.INCOMPLETE,
            ActivityPacketCompletion.Completion.UNDETERMINED);
    assertThat(incomplete.incompleteScopes())
        .containsExactly(
            new ActivityPacketCompletion.IncompleteScope(
                "slice:required", List.of("entry:incomplete"), "REVIEW_FAILED"));
    assertThat(undetermined.incompleteScopes())
        .containsExactly(
            new ActivityPacketCompletion.IncompleteScope(
                null, List.of("entry:undetermined"), "HISTORICAL_REQUIRED_SCOPE_UNKNOWN"));

    ActivityExplanationResult historical =
        new ActivityExplanationResult(List.of(), List.of(), List.of(), null);
    ActivityExplanationResult currentWithNoPackets =
        new ActivityExplanationResult(List.of(), List.of(), List.of(), List.of(), null);

    assertThat(historical.packetCompletion()).isEmpty();
    assertThat(currentWithNoPackets.packetCompletion()).contains(List.of());
  }

  @Test
  void rejectsACompletedSliceThatWasNotInTheFinalRequiredSet() {
    assertThatThrownBy(
            () ->
                new ActivityPacketCompletion(
                    "packet:complete",
                    List.of("entry:complete"),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice:required"),
                    List.of("slice:other"),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completePacketCannotClaimVacuousCompletionWithoutARequiredScope() {
    assertThatThrownBy(
            () ->
                new ActivityPacketCompletion(
                    "packet:empty-scope",
                    List.of("entry:empty-scope"),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of(),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sameKeyEarlierCompletionDoesNotEraseTheCurrentRevisionGap() {
    ActivityPacketCompletion revisionGap =
        new ActivityPacketCompletion(
            "packet:revision-gap",
            List.of("entry:revision-gap"),
            ActivityPacketCompletion.Completion.INCOMPLETE,
            List.of("range"),
            List.of("range"),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    "range", List.of("entry:revision-gap"), "CURRENT_REVISION_UNAVAILABLE")));

    assertThat(revisionGap.completion()).isEqualTo(ActivityPacketCompletion.Completion.INCOMPLETE);
    assertThat(revisionGap.completedSliceKeys()).containsExactly("range");
    assertThat(revisionGap.incompleteScopes())
        .singleElement()
        .extracting(ActivityPacketCompletion.IncompleteScope::sliceKey)
        .isEqualTo("range");
  }
}
