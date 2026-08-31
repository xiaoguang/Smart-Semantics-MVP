package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.linguan.codemd.stage01.M3TestSupport.allAtomIds;
import static com.linguan.codemd.stage01.M3TestSupport.analyze;
import static com.linguan.codemd.stage01.M3TestSupport.atomWithCanonicalValue;
import static com.linguan.codemd.stage01.M3TestSupport.atoms;
import static com.linguan.codemd.stage01.M3TestSupport.assertAccounting;
import static com.linguan.codemd.stage01.M3TestSupport.assertCanonicalIdsAndProofReferences;
import static com.linguan.codemd.stage01.M3TestSupport.assertProofPackClosure;
import static com.linguan.codemd.stage01.M3TestSupport.canonicalAtomValues;
import static com.linguan.codemd.stage01.M3TestSupport.codeFacts;
import static com.linguan.codemd.stage01.M3TestSupport.member;
import static com.linguan.codemd.stage01.M3TestSupport.onlyFact;
import static com.linguan.codemd.stage01.M3TestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3 RED contracts for the synthetic six-file reservation inventory. */
class ProvenFactExtractionTest {
    private static final Map<String, Integer> EXPECTED_FACT_ATOM_COUNTS = Map.of(
            "HTTP_ENTRY", 4,
            "QUANTITY_GUARD", 2,
            "INVENTORY_LOAD", 2,
            "AVAILABLE_FORMULA", 1,
            "INSUFFICIENT_GUARD", 2,
            "OPTIMISTIC_UPDATE", 5,
            "UPDATE_COUNT_GUARD", 2,
            "SUCCESS_RESULT", 2);

    @Test
    void reservationFixtureAdmitsExactlyEightFactsAndTwentyProofClosedAtoms() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-facts-"));
        Stage01Result result = analyze(root);

        assertNotNull(member(result, "verifiedSnapshot"));
        assertNotNull(member(result, "repositoryUnderstanding"));
        assertNotNull(M3TestSupport.provenSourceFacts(result));
        assertEquals("stage01-result-v1", text(result, "schemaVersion"));
        assertEquals("proven-source-facts-v1",
                text(M3TestSupport.provenSourceFacts(result), "schemaVersion"));
        assertEquals(6, M3TestSupport.list(member(result, "verifiedSnapshot"), "files").size());
        assertCanonicalIdsAndProofReferences(result);
        assertAccounting(result, 8, 20, 0);
        assertEquals(EXPECTED_FACT_ATOM_COUNTS.keySet(), codeFacts(result).stream()
                .map(fact -> text(fact, "kind"))
                .collect(java.util.stream.Collectors.toSet()),
                "the synthetic registry must contain exactly F01-F08 kinds");
        for (Map.Entry<String, Integer> expected : EXPECTED_FACT_ATOM_COUNTS.entrySet()) {
            Object fact = onlyFact(result, expected.getKey());
            assertEquals(expected.getValue(), atoms(fact).size(),
                    "unexpected atom count for " + expected.getKey());
            for (Object atom : atoms(fact)) {
                assertTrue(Set.of("KIND", "ATTRIBUTE", "CONDITION", "LITERAL",
                                "RELATIONSHIP").contains(text(atom, "role")),
                        "each atom must use the versioned role vocabulary");
                Object value = member(atom, "value");
                assertTrue(Set.of("STRING", "INTEGER", "DECIMAL", "BOOLEAN", "SYMBOL_REF")
                                .contains(text(value, "type")),
                        "each atom must use the versioned value vocabulary");
                assertNotNull(text(value, "canonical"));
            }
        }
        assertEquals(20, M3TestSupport.atomDispositions(result).size());
        for (Object disposition : M3TestSupport.atomDispositions(result)) {
            assertEquals("ADMITTED_WITH_PROOF", text(disposition, "disposition"));
            assertNotNull(text(disposition, "candidateFactKey"));
            assertNotNull(text(disposition, "atomKey"));
            assertNotNull(text(disposition, "proofId"));
            assertNotNull(text(disposition, "admittedFactId"));
        }
        assertProofPackClosure(result, root);
    }

    @Test
    void repeatedAnalysisOfEquivalentFrozenBytesIsCanonicalAndIdempotent() throws Exception {
        Path firstRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-canonical-a-"));
        Path secondRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-canonical-b-"));

        Stage01Result first = analyze(firstRoot);
        Stage01Result second = analyze(secondRoot);

        assertEquals(first, second, "canonical records must not include temporary root paths");
        assertEquals(M3TestSupport.sortAndJoinIds(first), M3TestSupport.sortAndJoinIds(second));
    }

    @Test
    void removingControllerClassRoutePrefixRejectsEntireHttpEntryWithoutShrinkingDenominator()
            throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-route-reject-"));
        FrozenRepositoryRequest request = M3TestSupport.removeControllerRoutePrefix(root);
        Stage01Result result = new Stage01Analyzer().analyze(request);

        assertAccounting(result, 8, 20, 4);
        assertEquals(7, codeFacts(result).size());
        assertTrue(codeFacts(result).stream().noneMatch(fact -> "HTTP_ENTRY".equals(text(fact, "kind"))));
        M3TestSupport.assertRejectedDispositions(result, "A01", "A02", "A03", "A04");
        M3TestSupport.assertFactRejections(result, "A01", "A02", "A03", "A04");
    }

    @Test
    void removingXmlVersionPredicateRejectsEntireOptimisticUpdateAndRetainsA16Rejection()
            throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-version-reject-"));
        FrozenRepositoryRequest request = M3TestSupport.removeVersionPredicate(root);
        Stage01Result result = new Stage01Analyzer().analyze(request);

        assertAccounting(result, 8, 20, 5);
        assertEquals(7, codeFacts(result).size());
        assertTrue(codeFacts(result).stream()
                .noneMatch(fact -> "OPTIMISTIC_UPDATE".equals(text(fact, "kind"))));
        M3TestSupport.assertRejectedDispositions(result, "A12", "A13", "A14", "A15", "A16");
        M3TestSupport.assertFactRejections(result, "A12", "A13", "A14", "A15", "A16");
        Object a16 = M3TestSupport.dispositionsForAtomKeys(result, "A16").get(0);
        assertNotNull(text(a16, "reasonCode"), "A16 needs an explicit rejection reason");
    }

    @Test
    void changingGuardLiteralRemovesOldAtomIdentityAndEmitsNewCanonicalValue() throws Exception {
        Path originalRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-guard-old-"));
        Stage01Result original = analyze(originalRoot);
        Object originalGuard = onlyFact(original, "QUANTITY_GUARD");
        Object oldAtom = atomWithCanonicalValue(originalGuard, "quantity <= 0");
        String oldAtomId = text(oldAtom, "atomId");

        Path changedRoot = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-guard-new-"));
        FrozenRepositoryRequest changedRequest = M3TestSupport.changeQuantityGuardLiteral(changedRoot);
        Stage01Result changed = new Stage01Analyzer().analyze(changedRequest);
        Object changedGuard = onlyFact(changed, "QUANTITY_GUARD");

        assertTrue(canonicalAtomValues(changedGuard).contains("quantity <= 1"));
        assertFalse(canonicalAtomValues(changedGuard).contains("quantity <= 0"));
        assertFalse(allAtomIds(changed).contains(oldAtomId),
                "a changed semantic literal cannot retain the old atom ID");
        assertAccounting(changed, 8, 20, 0);
    }

    @Test
    void gapLedgerHasOnlyTheFiveProfileExpectationsWithSearchProvenance() throws Exception {
        Path root = Stage01Fixtures.copyReservationSnapshot(
                Files.createTempDirectory("stage01-m3-gaps-"));
        Stage01Result result = analyze(root);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("LIFECYCLE_SYMMETRY", "ASK_RESERVATION_EXPIRY_RELEASE_POLICY");
        expected.put("WAREHOUSE_KEY_SCOPE", "ASK_WAREHOUSE_SCOPE_POLICY");
        expected.put("UNIT_SEMANTICS", "ASK_QUANTITY_UNIT_POLICY");
        expected.put("RETRY_HANDLING", "ASK_NON_SINGLE_UPDATE_RETRY_POLICY");
        expected.put("MISSING_ROW_HANDLING", "ASK_MISSING_ROW_POLICY");

        List<?> gaps = M3TestSupport.expectationGaps(result);
        assertEquals(expected.size(), gaps.size());
        for (Object gap : gaps) {
            String expectationId = text(gap, "expectationId");
            assertTrue(expected.containsKey(expectationId), "no unprofiled expectation may appear");
            assertNotNull(text(gap, "triggerRuleId"));
            assertNotNull(text(gap, "questionTemplateKey"));
            assertEquals(expected.get(expectationId), text(gap, "questionTemplateKey"));
            assertFalse(M3TestSupport.list(gap, "searchedScope").isEmpty());
            Object absenceEvidence = member(gap, "absenceEvidence");
            assertNotNull(absenceEvidence);
            assertNotNull(text(absenceEvidence, "searchRuleId"));
        }
    }
}
