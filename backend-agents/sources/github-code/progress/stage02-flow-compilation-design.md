# Progress: Stage 02 flow compilation design

- Status: COMPLETE
- Agent role: Stage 02 design agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Design only for M4 business-flow compilation and Evidence Capsule
- Approved inputs: Scoped AGENTS files, DESIGN.md, Stage 01 design, Stage 01 records/tests, target architecture progress
- Current branch/worktree: codex/github-code-design-walkthrough / shared working tree

## Completed

- Read all inherited and scoped AGENTS.md instructions.
- Read the codebase-design skill and deep-module seam guidance.
- Confirmed the existing worktree contains concurrent Stage 01 documentation and implementation changes that this task must preserve.
- Read the target architecture, complete Stage 01 design, target implementation progress, public Stage 01 records, and directly relevant Stage 01 tests.
- Designed the model-free M4 deep Module from replayed Stage 01 input through FlowSlice, nested OutcomePath, total coverage accounting, Proof gating and minimal EvidenceCapsule projection.
- Identified the real package-private graph seam and specified a minimal public `Stage01FlowView` projection instead of exposing all Stage 01 graph internals.
- Specified exact Java records, request/result JSON projections, algorithms, stable identities, failure/Gap behavior, budgets, TDD mutations and bounded jshERP acceptance.

## Current state

Stage 02 design is complete. No Java, test, overall-design, README, Stage 01, source fixture or generated artifact was modified.

## Changed files

- `progress/stage02-flow-compilation-design.md`
- `docs/stages/02-flow-compilation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing Stage 01 and unrelated documentation changes identified and preserved |
| `wc -l docs/stages/02-flow-compilation.md` | PASS | 699-line detailed design |
| Markdown link/file and fence checks | PASS | Both local design targets exist; 36 code fences are balanced; 14 top-level design sections present |
| `git diff --check -- docs/stages/02-flow-compilation.md progress/stage02-flow-compilation-design.md` | PASS | No whitespace errors |
| Targeted tests | NOT RUN | Documentation-only task; no Java/test behavior changed |

## Decisions

- Stage 02 will be specified as one model-free deep module whose external seam consumes `Stage01Result` and returns an immutable Stage 02 result.
- This task will not modify Java, tests, overall design, Stage 01 documentation, README, or another agent's progress file.
- Durable/recovery input is a replayable `Stage01Request` plus `expectedStage01ResultId`; the compiler replays Stage 01 before obtaining source excerpts.
- The minimal cross-package seam is a public immutable `Stage01FlowView` projection; internal `RepositoryGraphTypes` remain package-private.
- Stage 02 cannot infer Outcome paths from the current guard/terminal lists. Stage 01 must first emit versioned TRUE/FALSE/NEXT/call/return/terminal CFG edges and directly test the projection.
- ProofPack closure and model Evidence minimality remain two independent gates.

## Blockers

- None.

## Exact next action

Parent agent can review the Stage 02 design and dispatch Luna/xhigh RED tests for the `Stage01FlowView` prerequisite and `Stage02Compiler` public seam.

## Resume checks

- Re-read this file.
- Confirm branch and `git status --short`.
- Read `docs/stages/02-flow-compilation.md`, especially sections 3–6.
- Reconfirm whether concurrent Stage 01 changes altered the package-private graph seam before implementation.
