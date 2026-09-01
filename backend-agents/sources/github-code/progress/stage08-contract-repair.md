# Progress: Stage08 contract repair

- Status: COMPLETE
- Agent role: Sol/ultra design-authority documentation repair
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Align only `docs/stages/08-build-nine-section-document-and-archive.md` with the current `docs/DESIGN.md` Stage08 final-ledger, model-journal/recovery, artifact-observation, Trace, and Adapter contracts.
- Approved inputs: scoped `AGENTS.md`, `docs/DESIGN.md`, `docs/stages/08-build-nine-section-document-and-archive.md`, `progress/TEMPLATE.md`
- Current branch/worktree: `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Re-read the scoped repository instructions and the current Stage08/design authority contracts.
- Confirmed the worktree already contains unrelated and concurrent uncommitted design, contract, implementation, and progress changes.
- Located the Stage08 contradictions: Stage07 draft treated as a final ledger input; missing acyclic M1 final-ledger payload; stale `run-event-v1`; incomplete round-slot journal recovery rules; stale publication-address artifact selector; missing `TraceQuery`/`TraceView`; incomplete CLI/HTTP observation arguments.
- Repaired Stage08 so M1 atomically installs the module-only final coverage ledger before the plan identity, while preserving four registered modules and exactly eight stage/run publication outputs.
- Bound M3, M4, Stage08 provenance, root manifest, validator, and resumer to the same M1 final-ledger reference and kept the Stage07 draft M1-only.
- Replaced stale observation contracts with the five-variant `ArtifactLocation`, exact artifact CLI/HTTP arguments, and complete `TraceQuery`/`TraceView`/path-free hop projection.
- Replaced stale `run-event-v1` wording with the exact v2 model-slot journal/outcome/projection and recovery rules.

## Current state

- The requested Stage08 documentation repair is complete.
- The four registered Stage08 module addresses and exactly eight reader-visible Stage08 publication outputs are preserved. M1 `planner` publishes the final ledger plus plan draft under one atomic module receipt; the ledger remains queryable only at its `STAGE_MODULE` location and is not copied into the public stage set.

## Changed files

- `progress/stage08-contract-repair.md`
- `docs/stages/08-build-nine-section-document-and-archive.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing changes recorded; Stage08 and DESIGN were already modified before this task. |
| `rg`/`sed` reads of DESIGN and Stage08 | PASS | Exact target contracts and stale Stage08 text identified. |
| `git diff --check -- docs/stages/08-build-nine-section-document-and-archive.md progress/stage08-contract-repair.md` | PASS | No whitespace errors. |
| Markdown fence/output/module checks | PASS | 18 fences balanced; exactly eight public output rows; exactly M1–M4 module headings. |
| Stale-contract `rg` check | PASS | No `run-event-v1`, publication-address selector, or old three-variant Adapter wording remains. |

Maven, network, Provider, capture, code tests, commit, and push were intentionally not run.

## Decisions

- Treat DESIGN §3.7, §12.2–12.3, §13.1–13.3 as the exact contract where DESIGN's shorter Stage08 narrative remains stale.
- Keep `planner`, `renderer`, `trace`, `archive` as the four registered Stage08 module keys.
- Keep the final coverage ledger module-only; it is referenced by the plan/stage/run/validation/resume chain and does not increase the eight reader-visible outputs.
- `RoundSlotJournalStore` remains private and absent from `ArtifactLocation`; resume identities still bind every event/outcome byte actually read.
- `ArtifactLocation` has exactly `STAGE_MODULE | VALIDATION_MODULE | RESUME_MODULE | STAGE_PUBLICATION | RUN_MANIFEST`; `TraceQuery.maxHops` is required and all-or-error.

## Blockers

- None.

## Exact next action

- Root should synchronize the remaining DESIGN internal stale spots noted in the handoff; no further change is required in this task.

## Resume checks

- Re-read this file, inspect `git status --short`, and verify no concurrent edit changed the Stage08 authority sections before continuing.
