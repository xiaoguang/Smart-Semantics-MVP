# Progress: direct-entry business runtime handoff

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Connect the completed direct-entry BusinessMaterialBuilder mode to the existing internal
  business workflow so a matched Step01/Step02 publication can reach the same activity, process,
  report and checkpoint path without Step05. Preserve the Flow-preferred route, one workflow, and
  public RepositoryAnalysisAgent seam. No Provider calls.
- Approved inputs: Active Step06 design defines safe no-Flow material; user-approved business-first
  implementation plan requires no-Flow entries to be actionable rather than held behind graph
  completion.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- BusinessMaterialBuilder now accepts a direct Step01/Step02 input pair and passes its focused
  material-module tests.
- The same input pair now reaches the existing one BusinessAnalysisWorkflow and produces the
  material, activity, knowledge and report checkpoints without a Step05 publication.

## Current state

- The request validates exactly one input mode: Flow/Capsule or matched source/discovery. Mixed
  fields are rejected instead of silently selecting a mode.

## Changed files

- `progress/direct-entry-business-runtime-handoff.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BuildBusinessMaterialsRequest.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflow.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java`
- `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| BusinessMaterialBuilder direct fallback | PASS | Two safe discovered entries materialized with no Step05 publication. |
| direct-entry workflow before implementation | RED | No executor method accepted the matched source/discovery pair. |
| `PersistedBusinessRunExecutorTest` | PASS | 2 tests, 0 failures/errors/skips; both modes produce durable business checkpoints and nine H2 sections with a scripted Provider. |
| material/executor direct regression | PASS | 8 tests, 0 failures/errors/skips. |
| `spotless:check && git diff --check` | PASS | 0 formatting or whitespace errors. |

## Decisions

- The direct-entry input is an alternative input mode of the one business workflow, not a new
  runtime, candidate, or report renderer.

## Blockers

- None.

## Exact next action

- Begin bounded real-source material planning. It can now start from persisted Step01/02 output
  without waiting for complete program-graph publication.

## Resume checks

- The direct path must use a scripted provider only, retain `ENTRY_SOURCE_FALLBACK` coverage, and
  produce the same four durable business checkpoints and exactly nine Markdown H2 sections.
