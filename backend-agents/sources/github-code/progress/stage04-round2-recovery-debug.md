# Progress: Stage04 Round-2 recovery regression debug

- Status: COMPLETE
- Agent role: Stage04 systematic debugging and minimal production correction
- Model: gpt-5.6-sol / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Reproduce and root-cause the two regressions introduced around the shared persisted-slot dispatcher/corrective-addendum work, compare them with the prior working recovery paths, then make only the minimum production correction. Do not modify tests.
- Approved inputs: scoped `AGENTS.md`; `progress/stage04-round2-addendum-core.md`; current Stage04 production diff; the two named failing methods and directly related Stage04 tests/production seams.
- Current branch/worktree: Shared dirty worktree; target source directory is untracked from the repository root. Preserve all unrelated and parallel changes.

## Completed

- Created this owned record before production, test, configuration, or durable-design edits.
- Read the inherited and source-scoped instructions, the addendum implementation/test handoffs, and the systematic-debugging/TDD/verification workflows.
- Phase 1 reproduced each regression independently with its single-method selector. Persisted recovery errors at `CandidateSeriesLedger.recover:173`; unaffected-Flow Round 2 errors at `DefaultCodeToMarkdownAgent.improveCandidate:198`.
- Traced both exceptions through the installed validation receipts. In both cases every archive, replay, identity, and Trace check passes; the sole failed check is `LIFECYCLE_EVENT_CLOSURE`.
- Located a retained 19:11 two-Flow run in the test temp area whose Round-1 and Round-2 receipts are both `valid=true`. `CandidateValidationTrace.java` and `CandidateSeriesLedger.java` were then modified at 19:54/19:50 by the final-audit lifecycle-closure slice; the addendum dispatcher landed later at 20:20.
- Phase 2 compared the old working paths with current production:
  - The shared dispatcher preserves the prior state branches and delegates `STARTED_CONSUMED` to the existing `CandidateSeriesLedger.recover`; it does not alter candidate receipt data. The new filesystem addendum store is absent from both failing call graphs.
  - Final-audit validation newly resolves every archived generation receipt only against the Candidate sidecar's current reader-round slot. The earlier path validated receipt formulas/archive replay without this current-slot-only origin assumption.
  - The persisted recovery fixture's immutable archive has two legitimate started receipts (ordinals 2 and 3), while its crash simulation deliberately persists only `ATTEMPT_BEGUN` and the first `THREAD_STARTED` (ordinals 1 and 2). Current validation therefore sees one verified and one absent event and rejects recovery before it can repair completion.
  - Round 2 deliberately copies unaffected-Flow model rounds and generation receipts byte-for-byte from the Round-1 parent. Its affected receipts resolve in the Round-2 slot, while copied parent receipts retain Round-1 event IDs/ordinals and cannot resolve in the Round-2 slot.
  - `Stage04FinalAuditTraceTest` covers a forged current-round receipt, but not a partially persisted recovery prefix or a receipt whose proven origin is the Round-1 parent.
- Phase 3 single hypothesis confirmed from the persisted artifacts: lifecycle closure is origin-unaware. It conflates a Candidate's current reader slot with the origin slot of every archived model-round receipt, so it rejects both a recoverable current-slot event prefix and the intentionally reused parent receipts. A repair must keep forged current-round receipts fatal while resolving each receipt against its proven origin and materializing only an exact missing current-slot suffix during recovery.
- Phase 4 tested the first narrow implementation variable: physically appending the archive's missing `THREAD_STARTED` suffix removed `STARTED_ROUND_INCOMPLETE`, but the recovery contract then correctly failed because one Reader Candidate attempt must retain exactly one persisted `THREAD_STARTED`. That write behavior was removed before continuing.
- Implemented the corrected minimal behavior: recovery simulates and validates the exact deterministic started-event suffix without persisting it, runs the full immutable archive/replay checks under a typed recovery-prefix lifecycle check, then appends only `RECOVERED_COMPLETION`. Normal validation remains exact-ledger-only.
- Made normal lifecycle validation origin-aware for Round 2: a receipt is assigned to the Round-1 slot only when its canonical bytes exactly match the addressed parent archive; every other receipt remains bound to the current Round-2 slot. Missing, rewritten, conflicting, or unresolvable evidence still fails closed.
- Both original single-method RED cases are now GREEN without test changes.
- Updated the current Stage 04 design with the exact recovery-prefix and Round-2 parent-receipt origin contracts.
- Preserved the final-audit forged-receipt rejection, kept the direct addendum selector 4/4, and completed the requested seven-class joint verification at 29/29 GREEN.

## Current state

- Complete. Production and current documentation are aligned; tests were not modified.
- Generative-model inventory: none. This debug task uses only local source, persisted test fixtures, and scripted providers; it invokes no LLM, network source, capture, candidate generation, freeze, package, or deployment action.

## Changed files

- `progress/stage04-round2-recovery-debug.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `docs/stages/04-runtime-archive-trace-recovery.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04PersistedRecoveryTest#completeInstalledArchiveRecoversStartedSlotWithoutProviderAndAppendsRecoveredCompletion test` | RED (expected) | 1 test, 1 error: `STARTED_ROUND_INCOMPLETE` at `CandidateSeriesLedger.recover:173`. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest#roundTwoCallsProviderOnlyForFlowWithFindingAndReusesUnaffectedFlowRoundsByteForByte test` | RED (expected) | 1 test, 1 error: `TRACE_CLOSURE_BROKEN` at `DefaultCodeToMarkdownAgent.improveCandidate:198`. The installed receipt's sole failed check is `LIFECYCLE_EVENT_CLOSURE`. |
| `mvn -Dtest=Stage04PersistedRecoveryTest#completeInstalledArchiveRecoversStartedSlotWithoutProviderAndAppendsRecoveredCompletion test` | RED (hypothesis refinement) | Lifecycle error removed, but assertion saw 2 `THREAD_STARTED` events instead of the contract's 1; proved recovery must validate, not append, the missing deterministic receipt suffix. |
| `mvn -Dtest=Stage04PersistedRecoveryTest#completeInstalledArchiveRecoversStartedSlotWithoutProviderAndAppendsRecoveredCompletion test` | GREEN | 1 test, 0 failures, 0 errors after switching to non-mutating exact-prefix validation. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest#roundTwoCallsProviderOnlyForFlowWithFindingAndReusesUnaffectedFlowRoundsByteForByte test` | GREEN | 1 test, 0 failures, 0 errors with exact parent/current receipt-origin resolution. |
| `mvn -Dtest=Stage04FinalAuditTraceTest#publicPersistedReceiptOnlyRewriteKeepsCandidateIdsButFailsAgainstExactLedgerEvent test` | GREEN | 1 test, 0 failures, 0 errors; a formula-consistent forged current-round event reference remains invalid. |
| `mvn -Dtest=Stage04Round2AddendumTest test` | GREEN | 4 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04Round2AddendumTest,Stage04PersistedRecoveryTest,Stage04LifecycleRound2HardeningTest,Stage04Round2Test,Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04PublicCoreTest test` | GREEN | 29 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | GREEN | No whitespace diagnostics. |

## Decisions

- No production correction will be attempted before Phases 1-3 identify and minimally test one root-cause hypothesis.
- The existing named regression methods are the required TDD RED cases; tests remain unchanged.
- Preserve the final-audit guarantee: a formula-consistent forged current-round receipt must still fail against durable event evidence.

## Blockers

- None.

## Exact next action

- Complete. Terra needs only necessary integration/closeout; do not rework this recovery fix or its tests.

## Resume checks

- Re-read this record and scoped `AGENTS.md`; confirm the two production files and one Stage 04 design update are present; rerun only the directly relevant selectors if later shared-worktree changes overlap them.
