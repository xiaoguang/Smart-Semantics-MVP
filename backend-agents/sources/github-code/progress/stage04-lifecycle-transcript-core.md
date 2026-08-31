# Progress: Stage04 lifecycle transcript core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Scope: lifecycle-aware per-round Stage03 run transcript only; no archive-v2 assembly, store, validation, HTTP, or CLI changes.
- Approved inputs: scoped `AGENTS.md`; Stage04 lifecycle transcript RED test/progress; current ledger/bridge; Stage03 canonical result records.
- Current branch/worktree: shared and pre-existing dirty; unrelated work is preserved.

## Completed

- Read scoped guidance, current blocked archive-v2 progress, lifecycle transcript RED contract/progress, and existing ledger/bridge/result records.
- Confirmed the seam: `LifecycleProviderBridge` captures real adapter preflight/start/response facts per task and `seal(Stage03Result)` validates exact closure against Stage03 canonical rounds; it does not infer lifecycle evidence in the archive assembler.
- Reproduced the intentional RED with `mvn -Dtest=Stage04LifecycleTranscriptTest test`: test compilation has three errors because `ProviderPreflightReceipt` lacks the adapter-provided `attemptId` and `LifecycleProviderBridge.seal(Stage03Result)`/its transcript output are absent.
- Added immutable run-transcript records, adapter-supplied preflight attempt identity, and a bridge-owned capture/seal path. The first `THREAD_STARTED` consumes the slot; later started events are persisted as `STARTED_CONSUMED -> STARTED_CONSUMED` audit events, so R2 is not a prestart retry.
- `seal(Stage03Result)` requires an exact, unique per-round closure over task, canonical response/hash, observed runtime, upstream started receipt, real preflight/attempt values, and persisted started event ID/ordinal. Missing lifecycle facts remain `STARTED_ROUND_INCOMPLETE`.
- `mvn -Dtest=Stage04LifecycleTranscriptTest test` is GREEN: 2 tests, 0 failures, 0 errors, 0 skipped.
- Existing prestart retry logic initially regressed because subsequent attempts in `PRESTART_RETRYABLE` were rejected as additional Stage03 rounds. Restored that legal `ATTEMPT_BEGUN` transition while preserving the no-increment rule for Stage03 rounds after the first start.
- `mvn -Dtest=Stage04LifecycleTranscriptTest,Stage04LifecycleProviderTest test` is GREEN: 6 tests, 0 failures, 0 errors, 0 skipped.
- Re-ran the Stage03 deterministic replay selector after the lifecycle seam: 10 tests, 0 failures, 0 errors, 0 skipped. The seam adds no Provider substitution to replay.
- Re-ran all direct Stage04 selectors. The lifecycle vertical and the pre-existing Stage04 selectors outside archive-v2 are green (21 tests total, including 2 transcript and 4 Provider tests). The separately blocked archive-v2 selector is still red solely because the assembler has not yet emitted `registry-bundle.json` and `model-rounds.jsonl`.
- Ran `git diff --check` successfully. The shared worktree remains intentionally untracked for the stage packages, so the check has no whitespace diagnostics for tracked changes; the lifecycle files were independently inspected and compile in the fresh Maven runs.

## Current state

- The lifecycle transcript vertical is complete. Archive-v2 remains its own blocked migration and must consume this immutable seam rather than recreate lifecycle values.

## Changed files

- `progress/stage04-lifecycle-transcript-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/LifecycleProviderBridge.java`
- `src/main/java/com/linguan/codemd/stage04/Stage03RunTranscript.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04LifecycleTranscriptTest test` | RED | Test compilation: three missing replay lifecycle seam errors (`attemptId`, `seal(Stage03Result)`, transcript output). |
| `mvn -Dtest=Stage04LifecycleTranscriptTest test` | PASS | 2 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest=Stage04LifecycleTranscriptTest,Stage04LifecycleProviderTest test` | PASS | 6 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest=Stage03DeterministicReplayTest test` | PASS | 10 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest='Stage04*Test' test` | PARTIAL | 39 tests: 21 pass; `Stage04ArchiveV2Test` has 1 failure and 17 errors because archive-v2 artifacts `registry-bundle.json` and `model-rounds.jsonl` are not implemented by its separately blocked assembler vertical. |
| `git diff --check` | PASS | Exit 0; no whitespace diagnostics. |

## Decisions

- The old `progress/stage04-archive-v2-core.md` remains BLOCKED and untouched.
- Per-round IDs must come only from the adapter/preflight and persisted slot event materialization. No Candidate/archive fields are synthesized here.
- The legacy two-argument `ProviderPreflightReceipt` construction remains transport-compatible, but produces no attempt ID. `seal` rejects such an incomplete round instead of manufacturing one.

## Blockers

- None for the lifecycle transcript vertical. The global Stage04 selector cannot be all-green until separately blocked archive-v2 implements its 19-artifact contract.

## Exact next action

- Lifecycle transcript work is complete. A later archive-v2 owner may consume `Stage03RunTranscript` to write lifecycle-backed model-round and generation-receipt artifacts.
