# Progress: Task 11 bounded repository synthesis

- Status: IN_PROGRESS
- Agent role: primary implementation and root-cause investigator
- Model: gpt-5.6-terra/xhigh for production changes after public-seam RED; design contract already approved in `docs/DESIGN.md`
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Close the existing generic large-repository synthesis gap: retain all reviewed activities and processes, but use bounded reviewed group summaries as the model-visible basis for repository synthesis and the final report. Do not change report prose based on sample feedback and do not add jshERP-specific business rules.
- Approved inputs: Existing frozen fixture data; zero-provider automated tests only. No live-model call is part of this task.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Identified the root cause from the production path rather than a sample symptom: `ProcessExplainer.repositoryInput(...)` serializes all reviewed activities and processes, while `BusinessReportPublisher.cleanKnowledge(...)` serializes all activities and processes again. Either input exceeds the model budget as repository size grows.
- Confirmed the active design already requires complete reviewed activities, bounded overlapping groups, reviewed group summaries, and one bounded repository synthesis. The missing work is implementation of that existing contract, not a new architecture.

## Current state

- Small real candidates are closed as reader-language quality evidence. Per user direction, further wording improvements are backlog-only unless an actual delivery defect appears.
- No broad live execution will start before the bounded-synthesis path is covered by scripted tests.

## Changed files

- `progress/task11-bounded-repository-synthesis.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Production-path inspection | PASS | Located the two all-content serializations and the existing design contract they fail to implement. |

## Decisions

- Preserve full reviewed activity and process records in `RepositoryBusinessKnowledge`; do not silently truncate or replace them.
- Use bounded, model-reviewed group summaries as the repository and report model basis. Any scope that cannot be carried must be explicit rather than compressed invisibly.

## Blockers

- None. The next action is a public-seam RED that proves a large valid set reaches repository/report synthesis without serializing every full activity.

## Exact next action

- Add and run one focused scripted-provider RED for bounded repository/report input before editing production code.

## Resume checks

- Re-read `ProcessExplainer.repositoryInput`, `BusinessReportPublisher.cleanKnowledge`, and the Stage07/08 large-repository sections.
- Confirm no live-provider execution flag is set.
