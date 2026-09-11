# Progress: Task 4 activity coverage v2 GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Approved Task 4 only: arbitrary-N ActivityExplainer capacity/closure behavior, required
  Review unexplained entries, v2 activity coverage persistence and direct resources/readers.
- Approved inputs: `docs/plans/coherent-code-context-implementation-plan.md` Task 4; scoped
  `AGENTS.md`; the Luna RED `ActivityCoverageV2ContractTest` and its completed progress file.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the scoped guidance, approved Task 4 contract, Luna RED/progress and the current detailed
  Step 06 ActivityExplainer design.
- Kept the Luna RED contract intact: an incomplete but structurally valid DRAFT now reaches the
  sole REVIEW with the complete actual DRAFT and program-computed missing local keys.
- Added program-owned `UnexplainedActivityEntry` records; v2 coverage persists and fresh-reopens
  them while mapping their entries to `NOT_ANALYZED / MODEL_NOT_EXPLAINED`.
- Upgraded only the activity-coverage schema/module version to v2. Reviewed activity JSONL
  remains v1. Switched Activity DRAFT/REVIEW resources to v2 and removed the unused v1 resources.
- Updated only scripted Activity REVIEW fixtures that run through the affected seam; Process and
  Report production assertions/logic were not changed.

## Current state

- Task 4 is complete. The current activity seam accepts arbitrary material-local E1...EN keys,
  gives one structurally valid partial DRAFT exactly one complete REVIEW, and persists explicit
  unexplained coverage without extending the Process or Report production seams.

## Changed files

- `progress/task4-activity-coverage-v2-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationResult.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/UnexplainedActivityEntry.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationCheckpointPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationCheckpointReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPromptCatalog.java`
- `src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-*-v2.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-*-v1.txt` (removed)
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- Direct Activity and downstream scripted-provider test fixtures listed in Task 4 verification.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Worktree begins with the Task 4 Luna RED test/progress only. |
| Luna RED selector | Recorded RED | 5 expected failures: v1 pre-REVIEW coverage rejection, Provider capacity call, no required Review unexplained entries and no v2 sidecar. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityCoverageV2ContractTest test` | PASS | 5 tests, 0 failures/errors/skips. |
| Direct Activity selectors | PASS | 14 tests across ActivityExplainer, budget, schema, prompt, checkpoint and direct-context selectors; 0 failures/errors/skips. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProcessKnowledgeCheckpointTest,BusinessReportCheckpointTest test` | PASS | 2 tests, 0 failures/errors/skips after REVIEW fixtures added required empty or explicit unexplained arrays. |
| Final affected-selector rerun | PASS | 18 tests, 0 failures/errors/skips across Activity, checkpoint, Process/Report checkpoint and persisted-workflow seams. |
| Scoped Spotless | PASS | 17 Task 4 production/test Java files are clean after targeted formatting. |
| `git diff --check` | PASS | No whitespace errors. |
| Full Spotless | Existing unrelated failure | 24 pre-existing nonclean Java files remain outside this scoped work; no broad formatting applied. |

## Decisions

- Preserve current many-to-many activity/entry membership and material-local E1...EN keys.
- Keep Process, Report, public agent/CLI, old steps and documents outside Task 4.
- Do not add retries, splitting, queueing, or a third Provider request.
- The actual DRAFT in REVIEW is the first model response JSON, not the first material input.
- An E3/E4 REVIEW omission is a closed, explicit `NOT_ANALYZED / MODEL_NOT_EXPLAINED` result,
  not an invented business activity or a source/Proof Gap.

## Blockers

- None for the approved Task 4 scope.

## Exact next action

- Parent Agent can integrate Task 4 with Task 5, which alone owns passing specific
  `MODEL_NOT_EXPLAINED` records into repository knowledge and Chapter 9.

## Resume checks

- Re-read this file, check `git status --short`, and run `ActivityCoverageV2ContractTest` before
  claiming Task 4 complete.
