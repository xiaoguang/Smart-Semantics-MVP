# Progress: jdt-selected-entry-red

- Status: IN_PROGRESS
- Agent role: Bounded selected-entry public-seam regression tests
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Tests only; prove selected-entry collection retains the full denominator and rejects unknown IDs before collection.
- Approved inputs: Existing ProgramGraphsPublicFixture public source/discovery publications and the frozen selected-entry contract in the current run plan; no source scan, model call, or customer build.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read scoped AGENTS.md, TDD, and test-quality guidance.
- Confirmed the selected execution overload and workflow forwarding overload are now present; tests call both public seams directly.

## Current state

- The selected-entry contract requires a sorted/nonempty selection to invoke `JavaCodeSession.collect` only for selected discovered IDs, retain other entries as `NOT_COLLECTED / NOT_SELECTED_FOR_SAMPLE`, and reject unknown IDs before any collection.
- Two tests now exercise that contract through the future public overload via `Class#getMethod`; the lookup emits an explicit RED assertion while the overload is absent.
- The direct graph-seam tests now call the public selected overload. The separate runtime-workflow test now calls `TechnicalAnalysisWorkflow.continueAfterDiscovery(..., selectedEntryIds)` directly with a persisted discovery fixture and counting JDT session, covering selected collection, denominator retention, and unknown-ID rejection before collection.

## Changed files

- progress/jdt-selected-entry-red.md (owned progress)
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsSelectedEntryExecutionTest.java
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java (mechanical v2 policy alignment for the selected public graph fixture)
- src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowSelectedEntryExecutionTest.java
- src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java (all-entry reflection fixture supplies empty `selectedEntryIds`)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing worktree edits preserved; selected-entry and runtime forwarding tests are isolated from production edits. |
| `git diff --check` | PASS | New selected-entry test and progress file have no whitespace errors. |
| `git diff --check` | PASS | Runtime workflow forwarding test added with no whitespace errors; Maven intentionally deferred while Terra completes the workflow overload. |
| `git diff --check` | PASS | Existing all-entry approved-classpath fixture now supplies empty selected IDs; no production changes. |

## Decisions

- Keep selected-entry tests in a separate class so the index collision test remains independently runnable while Terra adds the public overload.
- Derive a real entry ID from the persisted public discovery payload; do not invent IDs or use private reflection.

## Blockers

- The public selected-entry overload and runtime workflow forwarding overload are now present. Direct tests call them without reflection. The persisted executor’s JDT branch was observed not to pass `configuration.selectedEntryIds()` to the workflow overload; parent must decide/fix production. The all-entry classpath helper now passes `selectedEntryIds = List.of()` while retaining the constructor’s non-null validation.

## Exact next action

- Runtime forwarding test is staged in a separate class; it uses a real persisted discovery fixture, a counting JDT session, and the direct public workflow overload. Do not start Maven until Terra hands off the combined selector.

## Resume checks

- Re-read this file, run `git status --short`, and wait for the parent/production agent before any post-GREEN selector run.
