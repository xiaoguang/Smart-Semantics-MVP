package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterpretationAdmissionGateTest {
    @Test
    void unknownFactReferenceIsFatal() throws Exception {
        MvpFixtures.Fixture fixture = fixture("unknown-fact-");

        RuntimeException failure = assertThrows(RuntimeException.class, () -> generate(
                fixture, MvpFixtures.providerWithR1Fact("fact:not-in-capsule")));

        assertTrue(failure.getMessage().contains("UNKNOWN_FACT"),
                () -> "expected unknown fact rejection, got: " + failure.getMessage());
    }

    @Test
    void unknownEvidenceReferenceIsFatal() throws Exception {
        MvpFixtures.Fixture fixture = fixture("unknown-evidence-");

        RuntimeException failure = assertThrows(RuntimeException.class, () -> generate(
                fixture, MvpFixtures.providerWithR1Evidence("evidence:not-in-capsule")));

        assertTrue(failure.getMessage().contains("UNKNOWN_EVIDENCE"),
                () -> "expected unknown evidence rejection, got: " + failure.getMessage());
    }

    @Test
    void precisionReviewCannotExpandEvidenceOrFactBasis() throws Exception {
        MvpFixtures.Fixture evidenceFixture = fixture("r2-evidence-expansion-");
        RuntimeException evidenceFailure = assertThrows(RuntimeException.class, () -> generate(
                evidenceFixture, MvpFixtures.providerWithR2EvidenceExpansion()));
        assertTrue(evidenceFailure.getMessage().contains("R2_EVIDENCE_EXPANSION"),
                () -> "expected R2 evidence expansion rejection, got: "
                        + evidenceFailure.getMessage());

        MvpFixtures.Fixture proposalFixture = fixture("r2-new-proposal-");
        RuntimeException proposalFailure = assertThrows(RuntimeException.class, () -> generate(
                proposalFixture, MvpFixtures.providerWithR2NewProposal()));
        assertTrue(proposalFailure.getMessage().contains("R2_NEW_PROPOSAL"),
                () -> "expected R2 new proposal rejection, got: " + proposalFailure.getMessage());
    }

    private static MvpFixtures.Fixture fixture(String prefix) throws Exception {
        return MvpFixtures.valid(Files.createTempDirectory(prefix));
    }

    private static CandidateReference generate(MvpFixtures.Fixture fixture,
                                               ModelProvider provider) {
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();
        return agent.generate(new GenerationRequest(
                fixture.manifest(), fixture.snapshotRoot(), provider));
    }
}
