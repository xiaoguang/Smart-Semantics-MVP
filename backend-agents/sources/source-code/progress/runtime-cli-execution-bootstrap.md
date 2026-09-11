# Progress: runtime CLI execution bootstrap

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add the smallest production-owned bootstrap and CLI execution mapping that can start and execute an already path-free analysis request through the existing `RepositoryAnalysisAgent`; preserve existing read-only CLI operations.
- Approved inputs: Current business-first architecture; existing frozen-source technical executors; explicit `maxMaterialsToStart` capacity limit; no live model call in this work unit.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- The CLI has tested mappings for `start --source-registration`, `execute-step --run`, `inspect`, `render`, and `artifact` through the same public Agent.
- `AnalysisRunRequestTemplate` builds only initial, round-one path-free requests from a registered source identity plus bootstrap-owned frozen references.
- `SourceAnalysisApplication` is the one small composition root that shares an Agent instance and its request template with the CLI.

## Current state

- This unit intentionally does not load customer configuration, expose a capture command, configure report/artifact read dependencies, or call a model. Those are later runtime/acceptance work, not a second execution path.

## Changed files

- `progress/runtime-cli-execution-bootstrap.md`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCli.java`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunRequestTemplate.java`
- `src/main/java/org/sourceanalysis/app/runtime/SourceAnalysisApplication.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCliContractTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/AnalysisRunRequestTemplateTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/SourceAnalysisApplicationTest.java`
- `README.md`, `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, `docs/analysis-steps/08-nine-section-document.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=SourceAnalysisCliContractTest,AnalysisRunRequestTemplateTest,SourceAnalysisApplicationTest,LocalRepositoryAnalysisAgentExecutionTest test` | PASS | 8 tests, 0 failures/errors/skips. |
| Targeted Spotless and `git diff --check` | PASS | All owned Java files format clean; no whitespace errors in owned source, tests, docs, or progress. |

## Decisions

- A CLI start accepts only an existing `sourceRegistrationId`; a bootstrap-owned request factory supplies the already-approved non-path configuration references.
- One application owns one configured Agent and exposes adapters over that same instance; no CLI-specific analysis logic or source-path escape hatch is allowed.
- The existing activity launch cap remains the required explicit control for future small-package runs.

## Blockers

- None. A user-supplied production configuration format has not yet been chosen; the bootstrap must stay internal and typed until its narrow configuration contract is tested.

## Exact next action

- Build the narrow user configuration/capture bootstrap required to run this composition root against an actual registered frozen repository, starting with zero-model material planning.

## Resume checks

- Re-read this progress file and `SourceAnalysisCliContractTest`.
- Run only the new CLI selector, followed by the directly related runtime selector after implementation.
