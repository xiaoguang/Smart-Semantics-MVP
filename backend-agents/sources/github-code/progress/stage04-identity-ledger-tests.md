# Progress: Stage04 identity ledger RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Bounded RED tests for canonical series/candidate identity and immutable RoundSlot lifecycle/idempotency.
- Approved inputs: scoped AGENTS.md; `docs/stages/04-runtime-archive-trace-recovery.md`; Stage03 public result seam.
- Current branch/worktree: shared worktree; preserve unrelated parent changes.

## Completed

- Read the complete Stage04 design and applicable scoped guidance.
- Confirmed this task modifies only this progress file and new Stage04 tests.

## Current state

- Added `Stage04IdentityLedgerTest` as one bounded vertical RED tracer. It covers
  root-independent canonical request/series IDs, sorted finding IDs, round/parent/
  finding/addendum lineage, rejection of Round 3, immutable event-folded slot
  transitions, three-attempt prestart ceiling, started/post-start semantics,
  conservative begun-crash recovery, and same-request reserve idempotency versus
  different-request conflict.
- Refactored the identity fixture so Round 1 obeys its empty-lineage contract;
  the finding-order comparison now uses two valid Round 2 lineages.
- Added fail-closed assertions for Round 1 carrying parent/findings and Round 2
  lacking parent/findings.

## Changed files

- `progress/stage04-identity-ledger-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04IdentityLedgerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | GREEN | Main compilation succeeded for 173 sources; test compilation succeeded for 49 sources; `Stage04IdentityLedgerTest` ran 3 tests with 0 failures, 0 errors, 0 skipped. |

## Decisions

- Test canonical values from independent literals/spec rules rather than
  reproducing implementation internals.
- Carry `Path` only as a typed transport-bound snapshot-root value; the test does
  not open or enumerate the filesystem and asserts the root is excluded from
  canonical identity.
- Exercise finding order and lineage through typed inputs and event factories,
  without reflection or private implementation details.
- Keep filesystem archive, Provider adapter, CLI, and HTTP behavior out of this
  first bounded identity/ledger slice.

## Blockers

- Stage04 production seam types are now present in the shared worktree, so the
  corrected fixture is verified green. The earlier missing-seam RED is retained
  in the preceding task report, but is superseded by this latest selector run.

## Exact next action

- None for this test-authoring slice; the narrow selector is green.

## Resume checks

- COMPLETE. Only `src/test/java/com/linguan/codemd/stage04/Stage04IdentityLedgerTest.java`
  and this progress file were edited by this slice; no production or design
  file was edited.
