# Progress: Stage01 actual-time and remaining-work recalibration

- Status: COMPLETE
- Agent role: Research and estimation
- Model: gpt-5.6-sol
- Started: 2026-09-01 15:57:35 -0230
- Last updated: 2026-09-01 16:01:57 -0230
- Scope: Read-only evidence collection plus one durable Chinese research note; no production, test, or design-contract changes.
- Approved inputs: Local Git metadata, Foundation/Stage01/Stage02 progress files, retained Maven evidence, code diffs, approved implementation plan and stage designs.
- Current branch/worktree: codex/github-code-target-implementation / /private/tmp/linguan-github-code-target-implementation

## Completed

- Read the scoped AGENTS.md and research skill.
- Confirmed the worktree contains active Stage02 changes owned by another Agent; these will not be modified.
- Reconstructed the observable commit timeline from `a6de913` through `f9d7cf6` and separated strict Stage01 elapsed span from Foundation-plus-Stage01 elapsed span.
- Classified the delivered diff into Foundation completion and Stage01 production/test work, and checked the direct selector evidence in each owned Progress file.
- Compared the original `18–26h` Foundation-plus-Stage01 estimate with the observable `9h04m53s` span without treating Git time as exact engineering effort.
- Wrote a durable recalibration with Stage02 module estimates, Foundation residual, Stage03A through Z ranges, total, assumptions and confidence levels.

## Current state

Research is complete. The calibrated remaining range is 77–124 continuous wall-clock hours with a roughly 100-hour working midpoint; it preserves all target gates and folds full Stage01 repository acceptance into Z.

## Changed files

- `progress/stage1-estimate-research.md`
- `docs/plans/implementation-estimate-calibration-2026-09-01.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Active Stage02 work and two known local untracked remnants observed; no files touched. |
| `git show -s ... a6de913 e8b64cb d497d14 f9d7cf6` | PASS | Foundation-to-Stage01 span 2h59m41s; design baseline through Stage01 publication 9h04m53s. |
| `git diff --shortstat/--numstat ...` | PASS | Foundation first slice 1,515 additions; later commit 9,321 additions, including 4,300 Foundation and 4,476 Stage01 production/test additions. |
| Progress and stage-design review | PASS | Foundation 8 tests and Stage01 9 direct tests recorded; full-jshERP and remaining adversarial acceptance are explicitly incomplete. |
| `git diff --check -- docs/plans/implementation-estimate-calibration-2026-09-01.md progress/stage1-estimate-research.md` | PASS | No whitespace errors. |

## Decisions

- Distinguish observable elapsed wall-clock span, active engineering effort, and forecast; Git timestamps alone do not prove uninterrupted labor.
- Treat delivered Stage01 as the persisted vertical described by current audit, not the later full-jshERP final acceptance.
- Use differentiated calibration factors: routine persistence/parser modules improve most; data-flow, Proof, full-repository closure and Trace retain wider risk buffers.
- Report 100 continuous hours as the scheduling midpoint and 77–124 hours as the risk interval.

## Blockers

- None. Exact effective person-hours remain unknowable because older Progress files contain dates but no start/end times; the research note states this limitation.

## Exact next action

Return the research path and calibrated result to the parent Agent; do not commit or push.

## Resume checks

- Re-read this file and the research note.
- Re-run `git status --short` without modifying active Stage02 paths.
- Recompute the remaining total after each Stage push using its exact commit span and unfinished acceptance list.
