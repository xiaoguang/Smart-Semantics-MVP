# Progress: process recall material clues

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-sol
- Started: 2026-09-11 03:57 NDT
- Last updated: 2026-09-11 04:06 NDT
- Scope: Let the process-reconstruction module use already-built business materials only to recall candidate reading groups; it must not infer a business relationship, order, or identity.
- Approved inputs: reviewed activities, already-persisted business-material set, current Step 07 design, scripted-provider tests
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Confirmed the current ProcessExplainer API receives only reviewed activities although the active design says grouping may also use already-built material clues.
- Confirmed automatic DepotHead material now contains richer local observations, but its unique live Luna DRAFT failed before a response; no automatic semantic result exists.
- Added a focused public-seam test for two semantically disjoint activities that share only a frozen source-file location. It requires a model-reading group, a generic recall reason, and no path in model input.

## Current state

- Implemented one narrow, generic recall signal. Two distinct activities with a common frozen source file may be offered together to the model as a candidate group. The signal is not shown as a business conclusion and source paths remain outside model input.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/knowledge/ExplainRepositoryProcessesRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/ProcessExplainer.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflow.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/ProcessMaterialRecallTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `README.md`
- `progress/process-recall-material-clues.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Targeted source inspection | PASS | Existing interface only accepts activities; grouping currently only compares model-generated objects, terms, and inputs. |
| `mvn -Dtest=ProcessMaterialRecallTest test` | RED | 1 test, 1 expected failure: `PROCESS_MATERIAL_RECALL_NOT_IMPLEMENTED`; the request has no material-set input. |
| `mvn -Dtest=ProcessMaterialRecallTest,ProcessExplainerTest,ProcessGroupingTest,ProcessPromptContractTest,RepositorySummaryTest,ProcessKnowledgeCheckpointTest,PersistedBusinessRunExecutorTest test` | PASS | 10 tests, 0 failures/errors/skips; no product-model calls. |

## Decisions

- Do not parse industry vocabulary, method-name semantics, or source text to create business relationships.
- Do not repeat the failed live task or call another Provider in this work unit.

## Blockers

- None for scripted implementation. Live quality validation remains blocked by the terminal failure of the already-started automatic Luna task.

## Exact next action

- Continue the business-first plan from the next missing end-to-end behavior. Do not retry the terminal automatic Luna request from this run.

## Resume checks

- Read this file, verify the request contract and ProcessExplainer grouping behavior, then use a new explicitly authorized material package before any future live Luna task.
