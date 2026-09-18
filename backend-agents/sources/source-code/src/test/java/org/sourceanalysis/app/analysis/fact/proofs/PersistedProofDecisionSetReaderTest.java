package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.fact.candidates.HistoricalFactReaderFixture;

/** Contract guard for the retained historical M2 reader without the retired proof producer. */
class PersistedProofDecisionSetReaderTest {

  @Test
  void reopensAStoredProofAgainstItsCandidatePredecessorWithoutReplayingProofConstruction() {
    HistoricalFactReaderFixture.Fixture fixture = HistoricalFactReaderFixture.open();

    PersistedProofDecisionSetReader reader =
        new PersistedProofDecisionSetReader(fixture.proofStore());
    ProofDecisionSet reopened =
        reader.reopen(
            fixture.proofPublication(),
            fixture.inputs(),
            fixture.publication(),
            fixture.candidateSet());

    assertThat(reopened.candidateSetId()).isEqualTo(fixture.candidateSet().candidateSetId());
    assertThat(reopened.codeFacts()).isEmpty();
    assertThat(reopened.externalEffectGaps()).isEmpty();
    assertThat(reopened.factDispositions()).hasSize(1);
    assertThat(reopened.factDispositions().get(0).disposition()).isEqualTo("REJECTED_WITH_REASON");
    assertThat(reopened.atomDispositions()).hasSize(4);
    assertThat(reopened.rootCauseRejections()).hasSize(1);
  }

  @Test
  void rejectsTamperedProofPayloadAndForeignCandidateControls() {
    HistoricalFactReaderFixture.Fixture fixture = HistoricalFactReaderFixture.open();

    assertThatThrownBy(
            () ->
                new PersistedProofDecisionSetReader(fixture.tamperedProofStore())
                    .reopen(
                        fixture.proofPublication(),
                        fixture.inputs(),
                        fixture.publication(),
                        fixture.candidateSet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
    assertThatThrownBy(
            () ->
                new PersistedProofDecisionSetReader(fixture.proofStore())
                    .reopen(
                        fixture.proofPublication(),
                        fixture.inputsWithControls(HistoricalFactReaderFixture.controls('9')),
                        fixture.publication(),
                        fixture.candidateSet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROOF_PACK_REFERENCE_BROKEN");
  }
}
