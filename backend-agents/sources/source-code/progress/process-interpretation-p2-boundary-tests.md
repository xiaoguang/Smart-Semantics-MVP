# Progress: process interpretation P2 boundary tests

- Status: COMPLETE
- Agent role: Root delivery coordinator, Luna/xhigh TDD role
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Verify the first M8 P2 review cannot retain a claim key that the immediately preceding
  accepted P1 did not create. The test runs one dry two-Flow packet through scripted P1/P2 only.
- Approved inputs: `docs/analysis-steps/06-flow-interpretation.md` §6.4, first M8 runner seam,
  and the persisted M7 fixture.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the M8 P2 protected-reference contract and the existing successful P1/P2 packet test.
- Added the retained-claim mutation and ran the direct selector: 1 test, 0 failures/errors/skips.
  It confirmed that `PC99` is rejected as `PROCESS_REVIEW_EXPANDED` after P1 and P2 each start
  once; no retry and no third call occurs.

## Current state

- A valid P1 assigns the program-local claim key `PC01`; this test substitutes only P2's retained
  key with `PC99` and requires a fail-closed result after exactly two scripted calls.

## Changed files

- `progress/process-interpretation-p2-boundary-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationRunnerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationRunnerTest#rejectsAP2ReviewThatRetainsAClaimOutsideTheAcceptedP1Projection test` | PASS | 1 test; 0 failures/errors/skips. |

## Decisions

- P2 expansion is a model-response error, not a reason to modify source evidence or retry P1.

## Blockers

- None.

## Exact next action

- Move to the separately designed M8 terminal P1/P2 outcomes and persisted round/receipt work;
  do not broaden this protected-reference regression.

## Resume checks

- Re-read this file, the M8 P2 response contract, and the direct M8 runner test.
