# Progress: M5 R1/R2 basis-closure gate RED

- Status: IN_PROGRESS
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One minimal M5 RED in `InterpretationRunnerTest`: direct scripted-provider R1/R2 calls must reject R2 basis expansion (`MODEL_REVIEW_EXPANDED`) and foreign atom/Gap references (`MODEL_REFERENCE_INVALID`) before returning an execution.
- Approved inputs: Step 06 §6.7.2.1, the current valid M4 publication produced by `prepare`, and the public `InterpretationRunner.runInterpretations` seam.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped source-code instructions, Step 06 §6.7.2.1, and the current `InterpretationRunnerTest`/`InterpretationRunner` seam.
- Confirmed the test must use the existing real R0→registry→M4 preparation and only a scripted `FlowModelProvider`.

## Current state

- Production currently validates R2 selected keys but does not enforce R2 basis subsets of the actual R1 selection, nor validate atom/Gap membership; the new test should freeze both missing gates as RED.
- Existing shared-worktree edits in the same test file and unrelated files are preserved.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunnerTest.java`
- `progress/local-interpretation-publication-carrier-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Pending | — | Run only the new M5 method selector after adding the direct scripted-provider scenarios. |

## Decisions

- Use the real persisted M4 task publication from `prepare`; do not fabricate or mutate an M4 input.
- R1 response selects only the first allowed atom; R2 response expands that same key to the first two allowed atoms and must fail with `MODEL_REVIEW_EXPANDED`.
- A second scripted scenario returns the same key with valid-format foreign atom and Gap and must fail with `MODEL_REFERENCE_INVALID`.
- Do not modify production code, design, fixtures, or other tests.

## Blockers

- None for this M5 slice.

## Exact next action

- Add the narrow direct seam/scenarios, run the exact selector, and record accurate RED evidence.

## Resume checks

- Recheck only the owned test/progress paths and preserve all other shared-worktree changes.
