# Progress: Stage04 fourth audit tests

- Status: IN_PROGRESS
- Agent role: Stage04 fourth audit test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most two Stage04 test classes (at most eight tests) for the fourth audit's receipt/ledger, typed Trace, bounded-read, and Round-2 addendum contracts. Make only the explicitly requested minimal correction to the legacy persisted-recovery test. Do not modify production or design.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; current Stage04 design, production seams, fixtures, and direct tests.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent and agent changes.

## Completed

## Current state

- Progress file created before test changes.

## Changed files

- `progress/stage04-fourth-audit-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |

## Decisions

- Keep all new coverage in two classes and under the eight-test cap; use real persisted fixtures and scripted providers only.
- Correct the old recovery positive fixture only because the final review explicitly rejects simulated missing `THREAD_STARTED` evidence; preserve its recovery assertion with every real started event durable and only completion missing.

## Blockers

## Exact next action

- Add the two test classes, minimally correct the legacy recovery fixture, run only their selectors plus the directly corrected recovery selector, then record exact RED/GREEN outcomes and `git diff --check`.

## Resume checks

- Confirm no production/design paths are changed and no more than two new test classes/eight test methods are added.
