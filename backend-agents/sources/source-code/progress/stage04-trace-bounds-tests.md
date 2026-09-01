# Progress: Stage04 trace and bounds tests

- Status: COMPLETE
- Agent role: Stage04 trace-reference and bounded-input test author
- Model: gpt-5.6 / delegated sub-agent
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most two new Stage04 test classes (six `@Test` methods total) covering exact typed Trace provenance and source/directory bounded-admission contracts identified as P1 in the final implementation review. No production or existing test assertions changed; one shared test-fixture root correction was made at the parent agent's request so strict persisted validation can observe the fixture ledger.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; current Stage04 archive/Trace implementation; Stage01→Stage03 synthetic fixtures.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent and agent changes, including `stage04-fourth-audit-tests.md` and `Stage04FourthAuditReceiptTest.java`.

## Completed

- Added exactly two test classes and six `@Test` methods. `Stage04TraceReferenceTest` covers admitted-term registry identity/priority, fallback task specification/resolution order/unique anchor, and real Stage01/Flow/Interpretation Gap provenance. `Stage04ReopenAndDirectoryBoundsTest` covers source reopen growth across Trace/Stage02/Stage03, candidate and source-registry directory cardinality, and durable ledger event cardinality.
- Corrected the shared `Stage04CandidateFixture` lifecycle: `create()` uses its original in-memory ledger so public-core generation owns the archive ledger; `install()` now reserves the matching durable slot and folds exactly the real `roundSlot.events()` sequence before installing the Candidate, with an equality assertion and no fabricated started event.
- Kept directory inputs bounded at `DIRECTORY_LIMIT + 1` (256 + 1), avoiding large allocations while exercising the pre-collect admission contract.

## Current state

- Trace selector is an intentional RED contract: 3 tests, 3 assertion failures for missing term registry/priority, fallback task-spec reference, and Gap question/provenance references. The real fixture validates before tracing.
- Bounds selector is an intentional mixed result: 3 tests, 1 RED candidate directory cardinality assertion (still reports `ARCHIVE_MANIFEST_INVALID` instead of `CANDIDATE_SIZE_LIMIT_EXCEEDED`) and 2 GREEN tests. Source reopen growth returns stable Trace `SNAPSHOT_REOPEN_MISMATCH`, Stage02 `EVIDENCE_SOURCE_REOPEN_MISMATCH`, and Stage03 `CAPSULE_CLOSURE_BROKEN`; source-registry overflow returns `SOURCE_REGISTRATION_INVALID`; ledger overflow returns `ROUND_SLOT_CONFLICT`.
- Existing `Stage04TypedTraceTest` was rerun after fixture migration and is GREEN (2 tests). Only the requested selectors were run for the owned tests; no repository-wide suite was run.

## Changed files

- `progress/stage04-trace-bounds-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04TraceReferenceTest.java` (FlowGap-only install and Trace-helper sidecar budget correction)
- `src/test/java/com/linguan/codemd/stage04/Stage04ReopenAndDirectoryBoundsTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateFixture.java` (persisted-ledger root and exact replay correction requested by root)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -q -Dtest=Stage04TraceReferenceTest test` | Expected RED | 3 tests, 3 assertion failures (term registry/priority, fallback taskSpecId, Stage01 Gap question refs). |
| `mvn -q -Dtest=Stage04ReopenAndDirectoryBoundsTest test` | Expected mixed RED/GREEN | 3 tests, 1 assertion failure (candidate directory limit), source/Stage02/Stage03 reopen and source-registry/ledger stable failures pass. |
| `mvn -q -Dtest=Stage04TypedTraceTest test` | PASS | 2 tests, 0 failures, 0 errors after fixture ledger migration. |
| `mvn -q -Dtest=Stage04TraceReferenceTest test` | PASS | 3 tests, 0 failures, 0 errors, 0 skipped after matching the FlowGap install/Trace sidecar limits. |
| `git diff --check` | PASS | No whitespace diagnostics. |

## Current correction verification

- The exclusive direct selector passed after the fixture correction: 17 tests, 0 failures, 0 errors, 0 skipped across `Stage04TypedTraceTest` (2), `Stage04PublicCoreTest` (9), `Stage04ImprovementTest` (1), `Stage04Round2Test` (1), and `Stage04LifecycleRound2HardeningTest` (4). No `SERIES_IDENTITY_CONFLICT` occurred.
- The FlowGap fixture now installs with a 300,000-byte sidecar limit, and its `CandidateFixture` Trace helper uses that same limit; ordinary fixtures remain at 200,000 bytes. The focused Trace selector passes.

## Blockers

The fixture correction is complete. The candidate directory cardinality assertion remains an intentional RED contract from the prior test slice and is outside this fixture-only correction. The FlowGap-only sidecar budget correction and focused Trace verification are complete.

## Exact next action

Report the complete FlowGap-only sidecar budget correction and exact selector result. The separate obsolete Round-2 fatal-test contract correction is tracked in its own progress file.
