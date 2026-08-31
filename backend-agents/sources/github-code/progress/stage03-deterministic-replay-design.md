# Progress: Stage03 deterministic replay design synchronization

- Status: COMPLETE
- Agent role: Stage03 deterministic replay design sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30 13:18 NDT
- Last updated: 2026-08-30 13:32 NDT
- Scope: Synchronize the Stage03-owned deterministic replay seam in `DESIGN.md` and `docs/stages/03-nine-section-generation.md`; read Stage04 only for boundary alignment.
- Approved inputs: Replayed Stage02, complete frozen registries, and each Flow's canonical R1/R2 task, schema, and response artifacts; no Provider invocation.
- Current branch/worktree: `codex/github-code-design-walkthrough` at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Read the repository-root, prototype, backend-agent, and GitHub Code Agent `AGENTS.md` files.
- Recorded the pre-existing worktree state before edits.
- Read all of `DESIGN.md` and `docs/stages/03-nine-section-generation.md`, plus Stage04 §§10.3, 11, and 16.2.
- Checked the current Stage03 public records, failure codes, strict R1/R2 parser, admission flow, and nested round receipts so the design uses real names and boundaries.
- Added the Stage03-owned `Stage03DeterministicReplay` target seam to the overall design and Stage03 detailed design.
- Defined exact replay inputs, canonical round records, shared-core algorithm, generation/replay byte equality, no-Provider/M8 ownership constraints, stable failure codes, and future TDD coverage.

## Current state

- Durable-design synchronization and all requested verification are complete.
- `docs/stages/04-runtime-archive-trace-recovery.md` remains read-only for this task.

## Changed files

- `backend-agents/sources/github-code/progress/stage03-deterministic-replay-design.md`
- `backend-agents/sources/github-code/DESIGN.md`
- `backend-agents/sources/github-code/docs/stages/03-nine-section-generation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing repository changes recorded; target repository is currently untracked from the outer repository. |
| `git status --short -- DESIGN.md docs/stages/03-nine-section-generation.md docs/stages/04-runtime-archive-trace-recovery.md progress` | PASS | `DESIGN.md` was already modified; Stage03/Stage04 stage documents and existing progress files were already untracked. |
| `git diff --check` | PASS | No whitespace errors in tracked diffs. |
| `git diff --no-index --check /dev/null <untracked-file>` | PASS | Stage03 and progress checks emitted no whitespace diagnostics; exit 1 is the expected no-index content-difference status. |
| New-link/anchor `rg` checks | PASS | Stage03 §3.5, Stage04 §§14.2/16.2, and Stage03 §§14.2/14.3 links and targets all resolve textually and their files exist. |
| Trailing-whitespace / fence / final-newline checks | PASS | No trailing whitespace; fence counts are even (`DESIGN.md` 64, Stage03 54); all three changed files end in LF. |
| Terminology and placeholder scans | PASS | Replay type/records/four failure codes are consistent; no `Stage03ReplayResult`, placeholder, or conflict marker exists. |
| Tests/build | NOT RUN | Documentation-only scope explicitly excluded code and test changes; no live Provider, network, customer Maven, generation, or archive workflow was invoked. |

## Decisions

- Keep replay as a narrow Stage03-owned execution seam, not a second architecture branch.
- Do not modify code, tests, README files, or the Stage04 detailed design.
- `Stage03DeterministicReplay.replay(Stage03ReplayRequest)` has no Provider parameter and returns the exact generation `Stage03Result` shape.
- Generation and replay share one private strict parser/admission/M6/M7/renderer/result core; M8 owns archive/lifecycle/source checks and final byte comparison only.
- Preserve both canonical-response-byte SHA and semantic-response SHA; reconstruct Stage03 nested receipts byte-identically while M8 validates its lifecycle receipts separately.
- Keep the replay contract independent of archive file count and legacy POC layout so it stays on the single target M5→M7 architecture line.

## Blockers

- None.

## Exact next action

- Parent agent can review and integrate this design sync with the separately maintained Stage04 correction; implementation requires a future authorized TDD work unit.

## Resume checks

- Re-read this file and `git status --short`.
- Re-run the documented link/terminology/whitespace checks before implementing the replay seam.
