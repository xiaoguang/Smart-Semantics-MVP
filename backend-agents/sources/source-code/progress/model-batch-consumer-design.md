# Progress: model batch consumer design

- Status: COMPLETE
- Agent role: Bounded consumer-document synchronization
- Model: inherited Codex agent
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Synchronize six active consumer documents with the approved fixed-material/model-batch contract in `docs/modules/model-job-execution.md` section 7.
- Approved inputs: Accepted docs-only design; no code, tests, configuration, runtime artifacts, source scans, JDT, or model execution.
- Repository/worktree: `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`, branch `main`, starting/current commit `df5e8d4`.
- Initial state: the nested repository already contained broad documentation edits, including the authoritative design work; no clean-scope claim is made. This task preserved unrelated changes.

## Completed

- Read all applicable repository instructions and the complete authoritative section 7.
- Synchronized the six assigned consumers with the required source-run, material-basis, model-batch, reviewed-job reuse, and `analysis-run-output-v3` terminology.
- Separated current CLI behavior from the approved, unimplemented target: current model modes only continue a `RUNNING` run, rebuild materials from saved Step 05 without JDT, and cannot reopen a failed run as a new batch.
- Recorded that target reuse requires the same complete materials checkpoint and a complete reviewed pair; an isolated DRAFT is diagnostic only and a new batch reruns the whole pair.
- Recorded that batch controls remain outside model input, operational batches do not themselves advance Reader Candidate Round, and a completed sample batch becomes `FINISHED` without fabricating a full-chain output.

## Current state

- Consumer-document synchronization is complete; fixed-material/model-batch behavior remains explicitly documented as approved but unimplemented.

## Changed files

- `progress/model-batch-consumer-design.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/references/semantic-interpretation-prompts.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `tools/repository-run/README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse --show-toplevel` | PASS | `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2` |
| `git branch --show-current`; `git rev-parse --short HEAD` | PASS | `main`; `df5e8d4` |
| `git diff --check -- <six assigned consumer files>` | PASS | No whitespace errors. |
| Bounded `rg` checks over the six consumers | PASS | No stale “parallel not implemented” assertion; no bare `run-output-v3`; the target CLI flag appears only as explicitly unavailable prose. |
| Tests, scans, JDT, and model execution | NOT RUN | Prohibited by this docs-only task. |

## Decisions

- Preserve the four business Modules and the public `RepositoryAnalysisAgent` interface.
- Describe only explicit new-batch reuse; never imply same-run recovery, isolated-DRAFT reuse, or implemented CLI support.
- Bind reuse to the exact complete `materialsCheckpoint` reference, never to coincidentally identical local S/E keys.
- Preserve historical sample facts while making clear that their material counts are per-run observations, not future batch constants.

## Blockers

- None.

## Handoff

- Parent agent owns integration and final repository-wide documentation checks. No further action is required in this bounded subtask.
