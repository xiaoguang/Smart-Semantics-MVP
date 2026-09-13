# Progress: navigation-reuse-step05-reference-tests

- Status: IN_PROGRESS
- Agent role: Luna RED — Step05/Capsule reference persistence
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Establish strict targeted RED for reference-based FlowCompilation/public FlowSlice and Capsule persistence, hydration into BusinessMaterialBuilder, NOT_COLLECTED handling, and fail-closed missing/wrong references. Do not modify production or design.
- Approved inputs: `docs/plans/navigation-reuse-and-readable-report-design.md` §§6–7; `progress/navigation-reuse-implementation.md`; existing Step05/Capsule public seams and test fixtures.
- Current branch/worktree: `codex/navigation-reuse-implementation` at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the approved reference contract and implementation coordinator progress.
- Located the existing direct Flow/Capsule publication and business-material test surfaces.

## Current state

Inspecting current persistence/readers and fixtures to add one minimal public-seam RED selector without production or design changes.

## Changed files

- `progress/navigation-reuse-step05-reference-tests.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` and focused test search | PASS | Preserved pre-existing Task 1/2 changes; found current embedded `codeContext` and `entryContext` assertions in direct business-flow tests. |

## Decisions

- Assert persisted JSON and reopened consumer behavior, not private helper implementation.
- Keep ordinary tests deterministic and tool-free; use a counting/failing navigation seam only if the existing fixture exposes one.

## Blockers

- None.

## Exact next action

Read the owning FlowCompilation/Capsule publishers/readers and the narrowest existing execution fixture, then add RED assertions for the complete reference/hydration/failure contract.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm only direct tests and this progress file are owned by this task.
