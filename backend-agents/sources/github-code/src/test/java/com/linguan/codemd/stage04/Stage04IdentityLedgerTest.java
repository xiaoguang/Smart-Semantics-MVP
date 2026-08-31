package com.linguan.codemd.stage04;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 04 RED tracer for the canonical series/round ledger seam.
 *
 * <p>The fixture is typed request material only.  The absolute root is a
 * transport binding and is intentionally supplied to prove it is excluded
 * from identity; no source directory is opened by this test.</p>
 */
class Stage04IdentityLedgerTest {

    @Test
    void canonicalRequestAndSortedFindingsAreRootIndependentButLineageChangesCandidateId() {
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        CandidateSeriesRequest rootA = request(Path.of("/private/snapshot-a"));
        CandidateSeriesRequest rootB = request(Path.of("/private/snapshot-b"));
        String contentId = "candidate-content:" + "1".repeat(64);

        CandidateIdentity roundOneA = ledger.identity(rootA, contentId,
                new CandidateLineage(1, null, List.of(), null));
        CandidateIdentity roundOneB = ledger.identity(rootB, contentId,
                new CandidateLineage(1, null, List.of(), null));

        assertEquals(roundOneA.canonicalRequestId(), roundOneB.canonicalRequestId(),
                "snapshotRoot is transport-only and must not enter canonical request identity");
        assertEquals(roundOneA.seriesId(), roundOneB.seriesId(),
                "equivalent rootless requests must share one candidate series");

        CandidateIdentity roundTwo = ledger.identity(rootA, contentId,
                new CandidateLineage(2, "candidate:round-1", List.of("finding:z", "finding:a"),
                        "addendum:reader-order"));
        CandidateIdentity roundTwoReordered = ledger.identity(rootB, contentId,
                new CandidateLineage(2, "candidate:round-1", List.of("finding:a", "finding:z"),
                        "addendum:reader-order"));

        assertEquals(roundOneA.canonicalRequestId(), roundTwo.canonicalRequestId());
        assertEquals(roundOneA.seriesId(), roundTwo.seriesId());
        assertEquals(roundTwo.candidateId(), roundTwoReordered.candidateId(),
                "finding IDs are canonicalized in stable order");

        assertNotEquals(roundOneA.candidateId(), roundTwo.candidateId(),
                "reader Candidate Round 2 is a new lineage, not a third interpretation round");

        CandidateIdentity differentParent = ledger.identity(rootA, contentId,
                new CandidateLineage(2, "candidate:other-round-1", List.of("finding:a", "finding:z"),
                        "addendum:reader-order"));
        CandidateIdentity differentFindingSet = ledger.identity(rootA, contentId,
                new CandidateLineage(2, "candidate:round-1", List.of("finding:a"),
                        "addendum:reader-order"));
        CandidateIdentity differentAddendum = ledger.identity(rootA, contentId,
                new CandidateLineage(2, "candidate:round-1", List.of("finding:a", "finding:z"),
                        "addendum:other"));
        assertNotEquals(roundTwo.candidateId(), differentParent.candidateId());
        assertNotEquals(roundTwo.candidateId(), differentFindingSet.candidateId());
        assertNotEquals(roundTwo.candidateId(), differentAddendum.candidateId());

        assertThrows(M8Exception.class, () -> ledger.identity(rootA, contentId,
                new CandidateLineage(1, "candidate:round-0", List.of("finding:a"), null)),
                "Round 1 must not carry a parent or findings");
        assertThrows(M8Exception.class, () -> ledger.identity(rootA, contentId,
                new CandidateLineage(2, null, List.of(), null)),
                "Round 2 must carry a parent and at least one finding");

        assertThrows(M8Exception.class, () -> ledger.identity(rootA, contentId,
                new CandidateLineage(3, "candidate:round-2", List.of("finding:a"), null)),
                "the schema must not represent Reader Candidate Round 3");
    }

    @Test
    void immutableRoundSlotFoldCoversZeroCapsulePrestartStartedAndRecoveryStates() {
        RoundSlotView zeroCapsule = new CandidateSeriesLedger().reserve(
                new RoundSlotRequest(request(Path.of("/private/zero-capsule")), 1, null, List.of(), null));
        assertEquals("RESERVED", zeroCapsule.slotState());
        assertEquals(0, zeroCapsule.prestartAttemptCount());
        RoundSlotView completedWithoutProvider = new CandidateSeriesLedger().fold(zeroCapsule,
                RoundSlotEvent.zeroCapsuleCompleted());
        assertEquals("COMPLETED", completedWithoutProvider.slotState());
        assertEquals(0, completedWithoutProvider.prestartAttemptCount(),
                "zero-Capsule completion must not create a Provider attempt");

        CandidateSeriesLedger retryLedger = new CandidateSeriesLedger();
        RoundSlotRequest retryRequest = new RoundSlotRequest(request(Path.of("/private/retry")), 1,
                null, List.of(), null);
        RoundSlotView retryable = retryLedger.reserve(retryRequest);
        retryable = retryLedger.fold(retryable, RoundSlotEvent.attemptBegun());
        retryable = retryLedger.fold(retryable, RoundSlotEvent.prestartFailureConfirmedNoThreadStarted());
        assertEquals("PRESTART_RETRYABLE", retryable.slotState());
        assertEquals(1, retryable.prestartAttemptCount());
        retryable = retryLedger.fold(retryable, RoundSlotEvent.attemptBegun());
        retryable = retryLedger.fold(retryable, RoundSlotEvent.prestartFailureConfirmedNoThreadStarted());
        assertEquals("PRESTART_RETRYABLE", retryable.slotState());
        assertEquals(2, retryable.prestartAttemptCount());
        retryable = retryLedger.fold(retryable, RoundSlotEvent.attemptBegun());
        retryable = retryLedger.fold(retryable, RoundSlotEvent.prestartFailureConfirmedNoThreadStarted());
        assertEquals("TERMINAL_FAILED", retryable.slotState());
        assertEquals(3, retryable.prestartAttemptCount());

        CandidateSeriesLedger startedLedger = new CandidateSeriesLedger();
        RoundSlotView started = startedLedger.reserve(new RoundSlotRequest(
                request(Path.of("/private/started")), 1, null, List.of(), null));
        started = startedLedger.fold(started, RoundSlotEvent.attemptBegun());
        started = startedLedger.fold(started, RoundSlotEvent.threadStarted());
        assertEquals("STARTED_CONSUMED", started.slotState());
        started = startedLedger.fold(started, RoundSlotEvent.failedAfterStarted());
        assertEquals("TERMINAL_FAILED", started.slotState(),
                "any post-start failure is terminal and must not be replayed");

        CandidateSeriesLedger recoveryLedger = new CandidateSeriesLedger();
        RoundSlotView begun = recoveryLedger.reserve(new RoundSlotRequest(
                request(Path.of("/private/recovery")), 1, null, List.of(), null));
        begun = recoveryLedger.fold(begun, RoundSlotEvent.attemptBegun());
        RoundSlotView ambiguous = recoveryLedger.fold(begun, RoundSlotEvent.ambiguousPrestartCrash());
        assertEquals("TERMINAL_FAILED", ambiguous.slotState(),
                "begun without proof of no started event must recover conservatively");
    }

    @Test
    void reserveIsIdempotentForSameCanonicalRequestAndConflictsForDifferentRequest() {
        CandidateSeriesLedger ledger = new CandidateSeriesLedger();
        RoundSlotRequest firstRequest = new RoundSlotRequest(request(Path.of("/private/idempotent-a")), 1,
                null, List.of(), null);
        RoundSlotView first = ledger.reserve(firstRequest);
        RoundSlotView repeated = ledger.reserve(firstRequest);
        assertEquals(first, repeated, "same canonical request must create-or-read one immutable slot");

        RoundSlotRequest conflictingRequest = new RoundSlotRequest(
                requestWithPayload(Path.of("/private/idempotent-b"), "rootless-request:changed"), 1,
                null, List.of(), null);
        M8Exception conflict = assertThrows(M8Exception.class,
                () -> ledger.reserve(conflictingRequest));
        assertEquals("SERIES_IDENTITY_CONFLICT", conflict.failureCode());
    }

    private static CandidateSeriesRequest request(Path snapshotRoot) {
        return requestWithPayload(snapshotRoot, "rootless-request:reservation-v1");
    }

    private static CandidateSeriesRequest requestWithPayload(Path snapshotRoot, String rootlessRequest) {
        return new CandidateSeriesRequest("candidate-series-request-v1", "source-registration:reservation-v1",
                rootlessRequest, "profile-bundle:java-spring-mybatis-nine-section-v0", snapshotRoot);
    }
}
