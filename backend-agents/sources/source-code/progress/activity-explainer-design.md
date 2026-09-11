# Progress: activity-explainer-design

- Status: IN_PROGRESS
- Agent role: Step 06 design authority
- Model: gpt-5.6-sol / ultra-equivalent design role
- Started: 2026-09-10 07:57 NDT
- Last updated: 2026-09-10 07:57 NDT
- Scope: Define the minimal ActivityExplainer contract that consumes persisted BusinessMaterial results and produces reviewed local business activities.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, scoped `AGENTS.md`, and the current `analysis.interpretation.material` public seam.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the repository, backend-agent, and source-code scoped instructions.
- Read the active overall design and the current Step 06 ActivityExplainer design.
- Confirmed this task changes design/progress only and makes no product model call.

## Current state

The active Step 06 document has the correct business-first direction but still leaves the public Java records, Provider seam, DRAFT/REVIEW wire shapes, checkpoint identity, and fatal/coverage cases too open for Luna RED and Terra GREEN to implement without interpretation.

## Changed files

- `progress/activity-explainer-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Shared worktree is dirty; this task owns only its progress file and an optional narrow Step 06 design clarification. |

## Decisions

- Keep ActivityExplainer as one deep Module with one public `explain` method.
- Introduce a Provider seam only because scripted and Codex Subscription adapters are both required.
- Do not reuse or extend the retired finite-key R0/R1/R2 protocol.
- Do not add automatic retry, Provider fallback, or same-run recovery.

## Blockers

None.

## Exact next action

Inspect the current material records and existing storage/provider primitives, then add an exact implementation brief to Step 06 if the existing contract cannot determine one RED and one GREEN implementation.

## Resume checks

- Re-read the current Step 06 section 5 before editing because another Agent may be working in the shared tree.
- Confirm no production or test file is modified by this task.
