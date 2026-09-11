# Progress: business analysis workflow

- Status: COMPLETE
- Agent role: Root coordinator; minimal Step 05 to Step 08 runtime composition
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Connect the four completed business Modules from a persisted technical Flow publication through to a persisted business report. This is an internal runtime composition, not the future public RepositoryAnalysisAgent or CLI.
- Approved inputs: Scoped `AGENTS.md`; active Step 06–08 designs; persisted BusinessFlows seam; existing four Module interfaces.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Added `BusinessAnalysisWorkflow`, which performs the only allowed business order: Flow publication → material set → reviewed activities → repository knowledge → report.
- Added `BusinessAnalysisWorkflowResult`, retaining all four typed results rather than passing temporary objects to an adapter without observability.
- Reworked the existing Step 08 checkpoint test to exercise the workflow rather than manually wiring the four modules in test code.
- Source refs are collected from the program-owned material set, deduplicated by short ref, and rejected if the same short ref points to different source locations.

## Current state

- The internal chain is green through the persisted four report files with a scripted Provider. It starts from a Step 05 `BusinessFlowsReference`; it does not yet capture/execute Steps 01–05, expose CLI commands, or call a real model.

## Changed files

- `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflow.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflowResult.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointTest.java`
- `progress/business-analysis-workflow.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportCheckpointTest test` | PASS | 1 test; the workflow produces and fresh-reopens all four report outputs. |

## Decisions

- This composition is intentionally not a new public interface. The later RepositoryAnalysisAgent/CLI layer must delegate to it, not recreate material/activity/process/report wiring.
- The workflow begins at an already-persisted Step 05 publication, preserving the established rule that new business modules do not depend on test-only in-memory graphs or paths.

## Blockers

- None for the internal workflow. Public execution and actual source capture remain later work.

## Exact next action

- Add the minimal RepositoryAnalysisAgent/CLI entry that can invoke this workflow from a registered, completed Step 05 publication.

## Resume checks

- Run the workflow checkpoint selector; do not add a second business-analysis interface or copy this composition into CLI code.
