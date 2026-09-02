# Progress: control flow graph

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M3 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Build the bounded entry-rooted control-flow draft from sealed M1/M2 graphs and same frozen inputs. Start with exact entry, branch polarity, and terminal behavior only.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `46ecf33`, both implementation plans, and the sealed M1/M2 reopening contracts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed M3 may consume only `ReopenedCodeStructureGraph`, `ReopenedCallGraph`, and the same `ReopenedProgramGraphInputs`; no raw drafts or worktree paths.

## Current state

- The first RED will define the M3 public builder seam and prove that a frozen handler produces an entry node and terminal rather than a source-order narrative.

## Changed files

- `progress/control-flow-graph.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Not run | Pending RED | Control-flow public types are absent. |

## Decisions

- M3 will be implemented in vertical slices; it will not execute customer code or infer business outcomes.

## Blockers

- None.

## Exact next action

- Read the fixed M3 type/edge contract, add the entry/terminal RED, and run only its direct selector.

## Resume checks

- Read this file, run `git status --short`, confirm `46ecf33` is an ancestor, then run the direct selector recorded above.
