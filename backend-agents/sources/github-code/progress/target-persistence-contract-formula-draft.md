# Progress: target persistence contract formula draft

- Status: IN_PROGRESS
- Agent role: Sol/ultra read-only design-support sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31 18:52:25 NDT
- Last updated: 2026-08-31 18:52:25 NDT
- Scope: Produce an implementation-ready formula/schema/order/test draft for the user-approved cross-stage persistence package; change only this progress file.
- Approved inputs: User-approved module/stage/run stores, descriptor-root formulas, artifact policy registry, safe typed address grammar, `analysis-run-request-v2`, corrected Stage 01 order, root run manifest, and durable run events; P0 review and current DESIGN/Stage 01/06/08 documents.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the repository, backend-agent, and GitHub Code Agent `AGENTS.md` files and the applicable deep-module/domain-modeling guidance.
- Confirmed all existing worktree changes are shared/pre-existing and out of scope for this task.
- Read the P0 contract review and identified the exact authoritative documents to reconcile.

## Current state

- Contract extraction and line-number reconciliation are in progress.

## Changed files

- `backend-agents/sources/github-code/progress/target-persistence-contract-formula-draft.md` (this task only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing shared design/code/test changes identified before this file was created. |

## Decisions

- This file is implementation input for the sole Design Authority, not an authoritative design replacement.
- Do not reopen the approved architecture or edit design, code, tests, POM, captures, or runtime state.

## Blockers

- None.

## Exact next action

- Read DESIGN and Stages 01/06/08 completely with line numbers, then record exact records, formulas, safe address grammar, ordering, and focused tests here.

## Resume checks

- Re-read this file and `target-design-contract-review.md`.
- Run `git status --short`; preserve all files except this task-owned draft.
- Re-open cited current-document lines because a concurrent Design Authority may edit them.
