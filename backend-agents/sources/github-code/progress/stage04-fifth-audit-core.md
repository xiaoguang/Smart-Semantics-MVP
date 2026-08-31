# Progress: Stage04 fifth-audit P1 preparation

- Status: COMPLETE
- Agent role: Stage04 production implementation preparation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Prepare minimal production corrections for the four fifth-audit P1s only: nested task/receipt round closure, self-describing archived typed Trace, bounded no-follow source/directory admission, and post-start Round-1/Round-2 terminalization. Do not change production before Luna's selectors compile and reproduce a clean RED.
- Approved inputs: Scoped `AGENTS.md`; `progress/stage04-final-acceptance-review.md`; `progress/stage04-fifth-audit-tests.md`; current Stage04 production and current Stage04 design.
- Current branch/worktree: Shared dirty worktree. Preserve all pre-existing production, test, design, and other-agent progress changes.

## Completed

- Read the scoped implementation rules, the completed final acceptance review, current dirty-worktree state, and the fifth-audit test brief.
- Created this owned preparation record before any production modification.
- Identified the candidate deep-module seams and initial minimum production paths; no Maven was started while Luna owns test compilation/RED generation.
- Received Luna's first clean RED: `Stage04FifthAuditArchiveTraceTest` currently has 1 test / 1 assertion failure because a coherent rewrite of the duplicated archived model/receipt flow tuple is accepted. Luna is adding the persisted Trace-record RED in the same class; production and Maven remain frozen until that contract is ready.
- Received the combined clean archive/Trace RED: `Stage04FifthAuditArchiveTraceTest` has 2 tests / 2 assertion failures / 0 errors. The second failure requires a persisted `TECHNICAL_FALLBACK` Trace record to declare its subtype and exact policy/template/task/anchor/resolution-order refs.
- Implemented the first bounded production slice, pending selector verification: model/receipt duplicate flow/capsule/round fields now bind to the nested canonical task; archive Trace serialization is now `reader-lineage-v2` and self-describes Fact, term, Flow/empty fallback, Gap, and reference lineage. Replay regenerates those same bytes before Trace admission.
- Closed the archive/Trace GREEN slice: `Stage04FifthAuditArchiveTraceTest` is 2/2 GREEN. The exact deterministic replay comparison now rejects coherent root/manifest rewrites that alter nested-task duplicate semantics or typed Trace fields.
- Closed the directly affected FlowGap regression without relaxing closure: a shared Stage03 anchor can legitimately yield the same interpretation-gap ID in multiple FlowInterpretation results. Archived typed Trace now retains every exact, sorted interpretation origin and the resolver follows each source provenance; duplicate copies inside one result still fail closed. A non-interpretation gap remains absent from that collection and is resolved only through its exact FlowGap branch.
- Completed the directly affected archive/Trace regression set: 29/29 GREEN across ArchiveV2, ValidationTrace, TypedTrace, TraceReference, FinalAuditTrace, and ResidualP1. The Maven window is released for Luna's lifecycle RED execution; no lifecycle production has been modified.
- Received Luna's clean lifecycle RED: `Stage04FifthAuditLifecycleBoundsTest` has 1 test / 1 assertion failure because a Round-1 archive-install failure after durable model starts leaves the slot `STARTED_CONSUMED`. Implemented the shared generated-candidate catch-path correction for both Round 1 and Round 2: it now uses the active bridge slot, folds `FAILED_AFTER_STARTED` only after a durable start, and preserves the existing deterministic pre-provider terminal path otherwise. Selector verification is next.
- Closed P1(4) narrow GREEN: `Stage04FifthAuditLifecycleBoundsTest` is 1/1 GREEN. The test confirms its post-start install failure is terminalized and a fresh Agent replays the durable post-start failure without invoking its Provider.
- Completed direct lifecycle regressions: public core, Provider bridge lifecycle, and persisted recovery are 22/22 GREEN. Both Round-1 and Round-2 orchestration catches use the same active-slot terminalizer; only `STARTED_CONSUMED` receives `FAILED_AFTER_STARTED`, so no-start evidence remains governed by its existing transition rules.
- Received the clean resource RED in the same fifth-audit class: its grouped bound probe has 4 missing-seam assertions for source registrations, installed candidates, durable series, and durable event directories; existing sparse source `limit+1` behavior is GREEN. Implemented package-private `CandidateValidationSupport.readBoundedDirectory(Path,int,M8FailureCode)`: no-follow directory admission before collection, observed limit-plus-one failure, sorted immutable paths, and stable `CANDIDATE_SIZE_LIMIT_EXCEEDED`. Wired it to those four directory consumers and replaced Trace source reopening's raw byte read with the shared no-follow bounded regular reader. Narrow verification is next.
- Closed P1(3) narrow GREEN after Luna's fixture-only boxing correction: `Stage04FifthAuditLifecycleBoundsTest` is 2/2 GREEN and its shared seam invokes the natural `int` max-entry contract. `Stage04ReopenAndDirectoryBoundsTest` is 3/3 GREEN: callers retain `SOURCE_REGISTRATION_INVALID` and `ROUND_SLOT_CONFLICT` at their existing public boundaries, while the helper itself emits the stable resource-limit code. The earlier compile-only catch cleanup was mechanical: replacing `Files.list`/`readAllBytes` eliminated checked `IOException` from those local blocks.
- Luna's dedicated Round-2 lifecycle selector is 1/1 GREEN. Final direct lifecycle/resource regressions are now pending this production agent's combined run.
- Completed final direct regressions: `Stage04FifthAuditLifecycleBoundsTest` (2), `Stage04FifthAuditRound2LifecycleTest` (1), `Stage04ReopenAndDirectoryBoundsTest` (3), `Stage04SecurityTest` (3), `Stage04LifecycleProviderTest` (4), `Stage04PersistedRecoveryTest` (9), `Stage04PublicCoreTest` (9), and `Stage04Round2Test` (1) are 32/32 GREEN. `git diff --check` is clean.

## Current state

- All currently selector-backed fifth-audit P1 slices are GREEN: nested archive/receipt closure, self-describing typed Trace, bounded no-follow file/directory admission, and Round-1/Round-2 post-start terminalization. No remaining production work is owned by this progress file.

## Proposed production paths

- `CandidateValidationTrace.java`: bind top-level `model-rounds` and `generation-receipts` duplicate flow/capsule/round fields to the nested immutable task; validate persisted typed Trace records against all archived controls before any runtime Trace reconstruction; use a shared bounded no-follow reader for source reopen.
- `CandidateAssembler.java`: write canonical, self-describing typed Trace records with exact per-kind registry/task/proof/gap references and fallback subtype, derived only from already-admitted Stage03 structure.
- `CandidateValidationSupport`: centralizes stat/no-follow/size-capped file reads and bounded directory iteration without a public API expansion.
- `FilesystemSourceRegistry.java`, `CandidateSeriesLedger.java`, and `DefaultCodeToMarkdownAgent.java`: use the shared capped pre-collect iterator at their registration/candidate/series/event boundaries.
- `DefaultCodeToMarkdownAgent.java`: retain the current `LifecycleProviderBridge`/slot through assembly and installation; if a start was persisted, fold `FAILED_AFTER_STARTED` on the current slot, otherwise retain the no-start terminal path. The same dispatcher must cover Round 1 and Round 2.

## Invariant boundaries

- A receipt/model tuple is admitted only when every duplicated flow/capsule/round value matches its nested canonical `FlowModelTask`; receipt linkage alone is insufficient.
- Trace persistence is self-describing and exact: archive records carry authoritative typed references, validation compares them to the immutable plan/model/registry/evidence graph, and runtime resolution must not fill a missing persisted reference by inference.
- Every untrusted filesystem read is no-follow, size-capped before allocation, and every directory is count-capped before retaining the limit-plus-one entry. Existing candidate, source-registration, and durable-ledger limits remain fail-closed.
- Once any `THREAD_STARTED` is durable, no assembly/install/runtime failure may leave that Round 1 or Round 2 slot in `STARTED_CONSUMED`; it must append the design-defined post-start terminal event without another Provider call.

## Changed files

- `progress/stage04-fifth-audit-core.md` (this file only)
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemSourceRegistry.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Maven selectors | NOT RUN | Luna owns selector compilation and clean RED generation; this preparation agent must not run Maven until signaled. |
| `Stage04FifthAuditArchiveTraceTest` (Luna-owned) | RED | 1 test, 1 assertion failure: a coherent duplicate model/receipt flow tuple rewrite is currently accepted. |
| `Stage04FifthAuditArchiveTraceTest` (Luna-owned) | RED | 2 tests, 2 assertion failures, 0 errors: duplicate tuple acceptance and absent self-describing Flow fallback refs. |
| `mvn -Dtest=Stage04FifthAuditArchiveTraceTest test` | GREEN | 2 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04TraceReferenceTest,Stage04FifthAuditArchiveTraceTest test` | GREEN | 5 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04ArchiveV2Test,Stage04ValidationTraceTest,Stage04TypedTraceTest,Stage04TraceReferenceTest,Stage04FinalAuditTraceTest,Stage04ResidualP1Test test` | GREEN | 29 tests, 0 failures, 0 errors. |
| `Stage04FifthAuditLifecycleBoundsTest` (Luna-owned) | RED | 1 test, 1 assertion failure, 0 errors: post-start Round-1 install failure leaves `STARTED_CONSUMED`. |
| `mvn -Dtest=Stage04FifthAuditLifecycleBoundsTest test` | GREEN | 1 test, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04PublicCoreTest,Stage04LifecycleProviderTest,Stage04PersistedRecoveryTest test` | GREEN | 22 tests, 0 failures, 0 errors. |
| `Stage04FifthAuditLifecycleBoundsTest` (Luna-owned, resource probe) | RED | 2 tests, 1 failure, 0 errors: grouped directory-bound probe reports four absent `readBoundedDirectory` seam assertions; sparse source limit+1 is GREEN. |
| `mvn -Dtest=Stage04FifthAuditLifecycleBoundsTest test` | BLOCKED_TEST_HELPER | After real production behavior compiled, the four directory calls failed only with reflection `Long`→`int` argument mismatch; Luna owns the fixture helper correction. |
| `Stage04FifthAuditLifecycleBoundsTest` (Luna after fixture-only fix) | GREEN | 2 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04ReopenAndDirectoryBoundsTest test` | GREEN | 3 tests, 0 failures, 0 errors. |
| `Stage04FifthAuditRound2LifecycleTest` (Luna-owned) | GREEN | 1 test, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04FifthAuditLifecycleBoundsTest,Stage04FifthAuditRound2LifecycleTest,Stage04ReopenAndDirectoryBoundsTest,Stage04SecurityTest,Stage04LifecycleProviderTest,Stage04PersistedRecoveryTest,Stage04PublicCoreTest,Stage04Round2Test test` | GREEN | 32 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Keep corrections behind existing package-local Stage04 modules; do not expand public interfaces or add a generic archive schema outside the exact persisted Trace fields demanded by the existing contract.
- Treat production reconstruction of missing Trace data as a validation failure, not a compatibility fallback.

## Blockers

- None.

## Exact next action

- Complete.

## Resume checks

- Re-read this progress file and `git status --short`; preserve all shared worktree changes.
- Confirm Luna has released Maven and supplied a clean selector result before editing production.
