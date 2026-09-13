# Progress: jdt-four-entry-selection-green

- Status: IN_PROGRESS
- Agent role: Bounded JDT selected-entry collection and runtime configuration green implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Implement the parent-approved selected-entry seam only: exact selected IDs bind the technical input and graph profile, selected IDs collect normally, unselected discovery entries become `NOT_SELECTED_FOR_SAMPLE`, and default/missing selection remains all entries. Add the authorized bounded observability at ProgramGraphsExecution. No live JDT scan, model execution, customer build, scope-filtered catalog/source, persistence/recovery system, or whole-repository analysis.
- Approved inputs: Parent’s exact selected-entry contract; Luna-owned two-test RED in `ProgramGraphsSelectedEntryExecutionTest`; current technical runtime configuration/workflow/Step03 code; root’s current run-plan design.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read parent-provided selected-entry contract and received authorization to implement after the direct RED.
- Confirmed the combined direct Maven run: Java-code-index regression is GREEN and the two selected-entry tests fail only because the requested public Step03 execute overload is absent.
- Read the root-approved run plan, the public Step03 RED test, runtime configuration/executor/workflow, and launcher identity path. Confirmed the launcher must derive an effective profile-bundle reference from the declared profile bundle plus the canonical selection because graph-profile identity alone is not part of `AnalysisRunRequest`.
- Added the exact public JDT Step03 overload. It validates duplicate, blank, and unknown selected IDs before `session.collect`; selected entries collect normally, while every other persisted discovery entry is `NOT_SELECTED_FOR_SAMPLE` and never collected.
- Added the optional single-source launcher field `technical.selectedEntryIds`, canonicalized it, and bound a nonempty selection into both the effective graph profile and effective profile-bundle run identity. Default/missing selection preserves the existing all-entry references and behavior.
- Threaded the canonical selection through `PersistedTechnicalRunConfiguration`, `PersistedTechnicalRunExecutor`, and `TechnicalAnalysisWorkflow`; preserved the previous 11-argument classpath constructor.
- Added the authorized `System.Logger` boundaries for selected-entry collection and publication without source-body, credential, or new persistence logging.
- Updated the tracked template and README with the single-source selection contract, including the automatic graph-profile injection and effective profile identity.

## Current state

- Production implementation and the direct Step03/launcher/selected-workflow selectors are green. The repaired runtime classpath method is verified green with its test-owned reflective helper explicitly passing `List.of()`; the complete seven-test workflow class was not rerun or claimed green. Production deliberately remains fail-fast on null. Maven has been released to the parent for its separately authorized four-entry materials run; no live scan was started here.

## Changed files

- progress/jdt-four-entry-selection-green.md (owned progress)
- src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java
- src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecution.java
- src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunConfiguration.java
- src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunExecutor.java
- src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java
- tools/repository-run/README.md
- tools/repository-run/jdt-luna-repository-run.template.json

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsSelectedEntryExecutionTest test` | Expected RED | Index: 3 green. Selected scope: 2 failures; `ProgramGraphsExecution.execute(..., ArtifactReference, ArtifactControls, List)` is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsSelectedEntryExecutionTest test` | PASS | Index: 3 tests, 0 failures/errors. Selected scope: 2 tests, 0 failures/errors. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.runtime.TechnicalAnalysisWorkflowTest#selectedJdtRuntimePassesApprovedClasspathToDiscoveryAndTechnicalExecution test` | PASS | The repaired runtime classpath method: 1 test, 0 failures/errors. The full seven-test workflow class was not rerun. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=<touched production Java files>` | PASS | Focused Spotless completed successfully. |
| `git diff --check` | PASS | Final shared-diff whitespace check passed. |

## Decisions

- Empty/missing selection means all discovered entries; nonempty selection must be unique, sorted, and wholly known before any `collect` call.
- Nonselected entries retain the existing `EntrySeed` and are published as `NOT_SELECTED_FOR_SAMPLE`; no synthetic engine failure or fallback source is introduced.
- Selected ID identity is shared between technical input and graph profile and bound into persisted configuration identity.
- The launcher preserves its existing declared profile reference for the all-entry default; nonempty selection derives an effective profile-bundle reference with the declared bundle and sorted IDs, which is used both by the queued request and `ProfileView`.
- Observability records only selection counts, entry IDs, per-selected entry method/call counts, monotonic elapsed durations, and publication boundaries; it never logs source bodies or credentials.

## Blockers

- None.

## Exact next action

- No further selection implementation action. Parent may use the documented single-source selection with the already-authorized four-entry materials scope.

## Resume checks

- Re-read this file, inspect the current shared diff and test source, use `.mvn/toolchains.xml` with offline targeted Maven, and run only direct selectors.
