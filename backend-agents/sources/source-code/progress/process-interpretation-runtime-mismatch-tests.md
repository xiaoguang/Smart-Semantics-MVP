# Progress: process interpretation runtime mismatch tests

- Status: COMPLETE
- Agent role: Root delivery coordinator, Luna/xhigh TDD role
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Lock the already-designed M8 runtime-identity boundary: a response from any runtime other
  than the fresh-reopened profile runtime must stop before P1 acceptance and must not start P2.
  Use one existing dry two-Flow fixture and scripted Provider only.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, M7
  `MODEL_SAFE` packet fixture, and the first M8 runner seam.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the first M8 runner contract and its direct green packet test.
- Added the one public-seam mismatch assertion and ran its direct selector: 1 test, 0
  failures/errors/skips. The scripted provider received only P1, and its substituted runtime was
  rejected before P1 could be accepted or P2 could start.

## Current state

- The first runner implementation checks observed runtime after each Provider call. This test will
  verify that the check is actually on the public transport seam, occurs before response parsing,
  and does not begin P2.

## Changed files

- `progress/process-interpretation-runtime-mismatch-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationRunnerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationRunnerTest#rejectsAMismatchedObservedRuntimeBeforeItCanAcceptP1OrStartP2 test` | PASS | 1 test; 0 failures/errors/skips. |

## Decisions

- The mismatch response still receives only the same dry P1 request; it is not a real Luna call.

## Blockers

- None.

## Exact next action

- Start the separate P2 protected-reference expansion behavior; do not combine it with model
  failure, persistence, or M9 publication work.

## Resume checks

- Re-read this file, `git status --short`, the first M8 test report, and the M8 runtime rule.
