package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** M3 RED contracts for proof-source revalidation and semantic mutation. */
class ProofMutationTest {
    @Test
    void generatedProofCannotSurviveSourceHashMutationOnRevalidation() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-proof-hash-"));
        FrozenRepositoryRequest originalRequest = Stage01Fixtures.request(root);
        Stage01Analyzer analyzer = new Stage01Analyzer();
        Stage01Result result = analyzer.analyze(originalRequest);
        assertNotNull(result, "mutation test must begin with a generated Stage01 result");
        assertFalse(M3TestSupport.proofs(result).isEmpty());

        Path mapper = root.resolve("src/main/resources/mappers/InventoryMapper.xml");
        String original = Files.readString(mapper);
        Files.writeString(mapper, original.replace("version = #{version}", "version = #{versioN}"));

        // If a future public validateProof/validateProvenFacts seam exists, test it directly;
        // otherwise this is the contractually allowed fallback through analyze + M1 reopen.
        M3TestSupport.assertTamperedProofRejected(analyzer, result, originalRequest);
    }

    @Test
    void semanticSpanMutationCannotKeepPriorFactOrProofIdentity() throws Exception {
        Path originalRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-proof-span-old-"));
        Stage01Result original = M3TestSupport.analyze(originalRoot);
        Object oldGuard = M3TestSupport.onlyFact(original, "QUANTITY_GUARD");
        String oldFactId = M3TestSupport.text(oldGuard, "factId");
        String oldAtomId = M3TestSupport.text(
                M3TestSupport.atomWithCanonicalValue(oldGuard, "quantity <= 0"), "atomId");

        Path changedRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-proof-span-new-"));
        FrozenRepositoryRequest changedRequest = M3TestSupport.changeQuantityGuardLiteral(changedRoot);
        Stage01Result changed = new Stage01Analyzer().analyze(changedRequest);

        Object changedGuard = M3TestSupport.onlyFact(changed, "QUANTITY_GUARD");
        assertFalse(M3TestSupport.text(changedGuard, "factId").equals(oldFactId),
                "a changed semantic span cannot retain the prior Fact identity");
        assertFalse(M3TestSupport.allAtomIds(changed).contains(oldAtomId),
                "a changed semantic span cannot retain the prior atom identity");
        assertEquals("quantity <= 1",
                M3TestSupport.text(M3TestSupport.member(
                        M3TestSupport.atomWithCanonicalValue(changedGuard, "quantity <= 1"), "value"),
                        "canonical"));
    }
}
