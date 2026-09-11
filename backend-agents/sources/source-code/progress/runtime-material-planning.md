# Progress: runtime material planning

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add the explicit, zero-Provider `FLOW_INTERPRETATION` material-planning target to the existing run-centric Agent. It produces only the saved business-material checkpoint; it does not execute activity, process, or report modules.
- Approved inputs: Business-first active design; user requirement to inspect small model inputs before wider calls; existing source/discovery and BusinessMaterialBuilder seams.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Located the unsafe distinction: `maxMaterialsToStart=0` limits ActivityExplainer only, while a final-document workflow would still reach later model modules.
- Added the explicit `FLOW_INTERPRETATION` material-only run target. It executes the persisted Step01/02 prefix plus `BusinessMaterialBuilder`, then saves a material-only v2 run output with no activity, process, or report checkpoint.
- Added `plan-materials --run <id>` to the same CLI and confirmed it maps to the same public Agent.
- Made materials-only output fail closed for `render` and every unavailable business artifact. Final-document configuration rejects a zero activity launch limit.
- Updated the runtime/CLI/Step06 documentation and scoped rules to describe the implemented behavior.

## Current state

- The closed output contract is `analysis-run-output-v2`: `MATERIALS_ONLY` owns exactly one material checkpoint; `COMPLETE_REPORT` owns all four downstream checkpoints. The reader rejects shape/kind mismatches and v1 is not translated.

## Changed files

- `src/main/java/org/sourceanalysis/app/runtime/RepositoryMaterialPlanningResult.java`
- `src/main/java/org/sourceanalysis/app/runtime/{RepositoryAnalysisRunCoordinator,LocalRepositoryAnalysisAgent,AnalysisRunOutput,PersistedBusinessRunConfiguration}.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemAnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCli.java`
- `src/test/java/org/sourceanalysis/app/runtime/{LocalRepositoryAnalysisAgentExecutionTest,PublicBusinessArtifactQueryContractTest,PersistedBusinessRunExecutorTest}.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCliContractTest.java`
- `README.md`, `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, `AGENTS.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Runtime contract inspection | PASS | Existing `AnalysisRunOutput` requires all four checkpoints and `executeStep` permits only the final document target, so a dedicated material-only branch is absent. |
| `mvn -Dtest=LocalRepositoryAnalysisAgentExecutionTest test` | RED | 3 tests ran; the materials-only case fails with `MATERIALS_ONLY_RUNTIME_NOT_IMPLEMENTED` because the coordinator has no material-only seam. |
| `mvn -Dtest=LocalRepositoryAnalysisAgentExecutionTest test` | RED after runtime seam | The new material path reached persistence; `FileSystemAnalysisRunRegistry.outputJson` rejected it because v1 unconditionally dereferenced four checkpoints. |
| `mvn -Dtest=LocalRepositoryAnalysisAgentExecutionTest,AnalysisRunRegistryTest,PublicBusinessArtifactQueryContractTest,PublicReportRenderContractTest,SourceAnalysisCliContractTest,PersistedBusinessRunExecutorTest test` | PASS | 16 tests passed; material-only persistence/reopen, unavailable artifact rejection, render rejection, CLI mapping, and final-run positive limit are covered. |

## Decisions

- Material planning runs the existing persisted Step01/02 prefix plus BusinessMaterialBuilder only. It is a preflight artifact, not an incomplete report.
- Later model work starts only from a separate, explicitly queued final-document run with a positive launch limit; no uncertain started model call is resumed or replayed.
- `analysis-run-output-v2` has an explicit `outputKind`; it rejects old v1 output rather than silently translating it.

## Blockers

- None.

## Exact next action

- Begin the remaining runtime composition: bootstrap a real registered-source request/configuration and run the existing fixed-repository material planner through this public target.

## Resume checks

- Re-read this progress file, `LocalRepositoryAnalysisAgent`, `RepositoryAnalysisRunCoordinator`, `AnalysisRunOutput`, and `FileSystemAnalysisRunRegistry`.
- The next unit starts with a new progress file and runs only its direct runtime/capture selectors.
