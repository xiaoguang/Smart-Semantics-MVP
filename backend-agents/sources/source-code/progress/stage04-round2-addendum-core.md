# Progress: Stage04 Round-2 addendum

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Round-2 persisted-state dispatch and a filesystem-backed, receipt-bound corrective-addendum store. Production code and this record only.
- Approved inputs: scoped `AGENTS.md`; Stage04 Round-2/addendum design; `Stage04Round2AddendumTest`; related lifecycle, recovery, review-store, and public-agent production seams.
- Current branch/worktree: Shared dirty worktree; preserve all unrelated and parallel changes.

## Completed

- Created this owned record before production edits.
- Read scoped constraints, the direct RED test, existing addendum records/store seam, current public agent, and the previous lifecycle/Round-2 implementation record.
- Reproduced the direct baseline: 4 tests, 2 failures and 2 errors. All three persisted Round-2 re-entry states collapse to `ROUND_2_SLOT_ALREADY_CONSUMED`; `FilesystemCorrectiveAddendumStore` is absent.
- Added the first production vertical slice, pending compilation: a common persisted-slot dispatcher for both public rounds; a full corrective-addendum v2 record with closed finite Sol/ultra read-only diagnosis receipt; and a bounded, no-follow append-only filesystem store that revalidates its Round-1 parent and exact review finding closure.
- Corrected the receipt canonical-material map after its first compilation exposed Java's ten-entry `Map.of` limit; canonical JSON now receives an ordered map.
- Direct acceptance selector is GREEN: all four Round-2/addendum behaviors pass.
- Began the requested direct regression selector. Its completed reports show two errors: `Stage04PersistedRecoveryTest` reports `STARTED_ROUND_INCOMPLETE` from `CandidateSeriesLedger.recover` at line 173 while validating a freshly installed archive; `Stage04LifecycleRound2HardeningTest` reports `TRACE_CLOSURE_BROKEN` from `DefaultCodeToMarkdownAgent.improveCandidate` line 198. The other completed requested classes are green: Round2 1/1, ReviewStore 1/1, Improvement 1/1, PublicCore 9/9.
- Completed integration review after the Sol/xhigh recovery correction. The shared dispatcher remains intact and is not in either corrected call path; the filesystem addendum store continues to require a current valid Round-1 parent, exact re-resolved finding set, full v2 content identity, finite Sol/ultra read-only receipt, canonical bounded bytes, no-follow ancestry, and create-if-absent atomic installation.
- Verified the Sol correction is complementary: it changes durable lifecycle receipt-origin/prefix validation in `CandidateSeriesLedger` and `CandidateValidationTrace`, rather than weakening the dispatcher or addendum admission.

## Current state

- The dispatcher will be shared by public Round-1 and Round-2 entry paths: only a complete last no-start pair can resume a `PRESTART_RETRYABLE` slot; `STARTED_CONSUMED` validates/recover-completes without Provider; a terminal event maps back to its persisted lifecycle failure class.
- The addendum store will accept the test's legacy-shaped intake only at recording time, validate it against the current Round-1 Candidate/review set, and archive a full content-addressed v2 addendum with a finite Sol/ultra read-only diagnosis receipt. Resolution accepts only that archived full form.
- The recovery regression is closed by origin-aware lifecycle validation: current-round forged receipts stay fatal, while parent byte-identical reused receipts and a proven recovery prefix remain verifiable. This does not broaden addendum authority.

## Changed files

- `progress/stage04-round2-addendum-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendum.java`
- `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendumDiagnosisReceipt.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemCorrectiveAddendumStore.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04Round2AddendumTest test` | RED | 4 tests: 2 failures, 2 errors; no unexpected compile failures. |
| `mvn -Dtest=Stage04Round2AddendumTest test` | GREEN | 4 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest,Stage04Round2Test,Stage04PersistedRecoveryTest,Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04PublicCoreTest test` | RED | Completed reports: 25 tests, 0 failures, 2 errors. `Stage04PersistedRecoveryTest` 9 tests/1 error (`STARTED_ROUND_INCOMPLETE` at `CandidateSeriesLedger.recover:173`); `Stage04LifecycleRound2HardeningTest` 4 tests/1 error (`TRACE_CLOSURE_BROKEN` at `DefaultCodeToMarkdownAgent.improveCandidate:198`). |
| `mvn -Dtest=Stage04Round2AddendumTest,Stage04PersistedRecoveryTest,Stage04LifecycleRound2HardeningTest,Stage04Round2Test,Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04PublicCoreTest test` | GREEN (Sol/xhigh verification) | 29 tests, 0 failures, 0 errors, 0 skipped. |

## Decisions

- A persisted terminal slot must reproduce its recorded lifecycle failure without attempting a provider call.
- Fatal review findings require a stored, content-addressed diagnosis receipt and directives; caller-supplied addendum strings are never evidence.

## Blockers

- None.

## Exact next action

- Complete. Preserve the append-only addendum and dispatcher admission boundaries in later Stage04 changes.

## Resume checks

- Re-read this record, preserve production-only scope, and rerun the direct selector before completion.
