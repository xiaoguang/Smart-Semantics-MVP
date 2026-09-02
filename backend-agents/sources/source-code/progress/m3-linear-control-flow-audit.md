# Progress: M3 linear control-flow audit

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Record the verified M2 provenance-closure fix and the first bounded M3 linear control-flow slice in the current implementation audit only.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, verified code/test facts supplied by the root Agent, scoped `AGENTS.md`, prior M2 provenance audit
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` / `/private/tmp/linguan-source-analysis-graph-provenance-design`

## Completed

- Read the scoped Agent rules, prior provenance audit, and current Program Graphs implementation audit.
- Confirmed this task updates current maturity only; the target M2/M3 contracts remain unchanged.
- Recorded the verified bounded M2 provenance-registration and missing-reference rejection behavior.
- Recorded the first M3 linear control-flow GREEN and its exact unsupported branch/publication limits.
- Removed the stale M1→M2 audit sentence that still described the now-present M2 execution/publication as unimplemented.

## Current state

- The current-implementation audit correction is complete; no production, test, target contract, wire, or artifact-count change was made.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `progress/m3-linear-control-flow-audit.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Worktree was clean before edits and aligned with `origin/main`. |
| `git diff --check` | PASS | No whitespace errors before completion update. |
| `git diff -- docs/analysis-steps/03-program-graphs.md progress/m3-linear-control-flow-audit.md` | PASS | Only the implementation maturity audit and this task progress changed. |

## Decisions

- Describe only the M2 closure behavior and M3 linear fixture behavior directly verified in the code branch.
- Keep unsupported branching behavior visible as a typed `PROFILE_STOP_TERMINAL` Gap, not as implemented CFG semantics.

## Blockers

- None.

## Exact next action

- Root Agent should publish this docs-only commit before continuing the next M3 RED/GREEN slice.

## Resume checks

- Read this file, inspect `git status --short`, and verify no target-contract section changed.
