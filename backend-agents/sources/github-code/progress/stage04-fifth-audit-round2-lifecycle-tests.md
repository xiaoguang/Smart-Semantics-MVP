# Progress: Stage04 fifth-audit Round-2 lifecycle test

- Status: IN_PROGRESS
- Agent role: Stage04 fifth-audit Round-2 lifecycle test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add exactly one public-seam Round-2 lifecycle test proving that assembler/store/install failure after real model starts persists `FAILED_AFTER_STARTED` and `TERMINAL_FAILED`, then fresh re-entry replays `PROVIDER_FAILURE_AFTER_START` without Provider calls. No production/design/existing-test edits.
- Approved inputs: scoped `AGENTS.md`, `progress/stage04-final-acceptance-review.md`, Stage04 public agent/ledger/review-store seams, and the real `Stage04CandidateFixture`.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent/agent changes and existing audit test files.

## Completed

- Created this progress file before editing the new test class.
- Added exactly one public-seam `@Test` in `Stage04FifthAuditRound2LifecycleTest.java`.
- The test generates and validates a real Round-1 parent, records a real approved warning finding, starts both scripted Round-2 model rounds with durable `THREAD_STARTED` events, and forces only Candidate install failure through the reflective low sidecar-limit seam.
- The test reopens the durable Round-2 ledger with the fixture's known absolute snapshot root, asserting `TERMINAL_FAILED`/`FAILED_AFTER_STARTED`, then checks fresh re-entry returns `PROVIDER_FAILURE_AFTER_START` with zero Provider calls.
- `git diff --check` and the one-test count passed before the focused run.
- The first selector attempt was a clean assertion failure (`IMPROVEMENT_PARENT_INVALID`) because the test lowered the agent limit before parent archive reopening. Removed that premature limit mutation; the test now lowers the limit only after the second real Round-2 `THREAD_STARTED` ACK through an existing reflective seam.
- Re-ran the corrected focused selector: `Stage04FifthAuditRound2LifecycleTest` is `1/1 GREEN`, with 0 failures/errors.

## Current state

- The single Round-2 test fixture is corrected and GREEN; the test proves durable terminal closure and provider-free stable re-entry without production/test collateral changes.

## Changed files

- `progress/stage04-fifth-audit-round2-lifecycle-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditRound2LifecycleTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n "@Test" src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditRound2LifecycleTest.java` | PASS | Exactly one test annotation. |
| `git diff --check -- progress/stage04-fifth-audit-round2-lifecycle-tests.md src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditRound2LifecycleTest.java` | PASS | No whitespace errors. |
| `mvn -q -Dtest=Stage04FifthAuditRound2LifecycleTest test` | BLOCKED at compile | Production compilation reports missing `DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES` in `CandidateSeriesLedger.java:452,538`, `DefaultCodeToMarkdownAgent.java:387`, and `FilesystemSourceRegistry.java:150`; no tests executed. |
| `mvn -q -Dtest=Stage04FifthAuditRound2LifecycleTest test` (initial run) | RED (fixture-precondition failure) | 1 test, 1 failure, 0 errors; expected `CANDIDATE_SIZE_LIMIT_EXCEEDED` but got `IMPROVEMENT_PARENT_INVALID` because low limits blocked parent reopen before Round-2. |
| `mvn -q -Dtest=Stage04FifthAuditRound2LifecycleTest test` (corrected fixture) | PASS | 1 test, 0 failures, 0 errors. |

## Decisions

- Generate a real Round-1 parent and validation finding through the public agent and filesystem review store.
- Use a scripted adapter that synchronously persists both real Round-2 `THREAD_STARTED` events; force only Candidate installation to fail with the existing reflective limits seam.
- Reopen the persisted Round-2 request through `CandidateSeriesLedger` to assert durable state without synthesizing lifecycle events.

## Blockers

- None.

## Exact next action

No further work in this narrowed deliverable; parent may include the corrected selector in the fifth-audit verification record.

## Resume checks

- Confirm the class has exactly one `@Test` and no production/design/existing-test path changed.
- Confirm the focused selector has no compilation errors before reporting its RED/GREEN result.
