package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Retained historical M1 reader tests over preinstalled canonical bytes only. */
class FactCandidateModuleReaderTest {

  @Test
  void reopensAStoredCandidateSetWithoutReplayingEnumeration() {
    HistoricalFactReaderFixture.Fixture fixture = HistoricalFactReaderFixture.open();

    FactCandidateSet reopened =
        new PersistedFactCandidateSetReader(fixture.store())
            .reopen(fixture.publication(), fixture.inputs());

    assertThat(reopened).isEqualTo(fixture.candidateSet());
    assertThat(reopened.candidates()).hasSize(1);
    assertThat(reopened.candidates().get(0).kind()).isEqualTo("JAVA_EXACT_CALL");
    assertThat(reopened.candidates().get(0).requiredAtoms())
        .extracting(FactCandidateSet.RequiredAtom::atomKey)
        .containsExactly(
            "INVOCATION_CALL_ID",
            "STATIC_TARGET_TYPE",
            "STATIC_TARGET_METHOD",
            "STATIC_TARGET_SIGNATURE");
    assertThat(reopened.sourceGraphRoots()).isEqualTo(fixture.inputs().sourceGraphRoots());
  }

  @Test
  void rejectsTamperedPayloadIdentityAndForeignInputControls() {
    HistoricalFactReaderFixture.Fixture fixture = HistoricalFactReaderFixture.open();
    PersistedFactCandidateSetReader reader =
        new PersistedFactCandidateSetReader(fixture.tamperedStore());

    assertThatThrownBy(() -> reader.reopen(fixture.publication(), fixture.inputs()))
        .isInstanceOf(FactCandidateReferenceException.class);
    assertThatThrownBy(
            () ->
                new PersistedFactCandidateSetReader(fixture.store())
                    .reopen(
                        fixture.publication(),
                        fixture.inputsWithControls(HistoricalFactReaderFixture.controls('9'))))
        .isInstanceOf(FactCandidateReferenceException.class);
  }
}
