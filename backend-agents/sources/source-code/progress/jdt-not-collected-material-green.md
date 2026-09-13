# Progress: jdt-not-collected-material-green

- Status: COMPLETE
- Agent role: Bounded BusinessMaterialBuilder consumer green implementation for valid JDT `NOT_COLLECTED` entry contexts
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: After Luna's one public RED, make only the status-aware `BusinessMaterialBuilder` reader/coverage correction required for the actual four-entry JDT run. Preserve the full 326-entry denominator, retain strict fallback support, and fail closed on malformed/unknown persistence states. Do not scan source, invoke JDT/model/customer builds, add cross-run reuse, change schemas/stores, or write tests.
- Approved inputs: Parent's actual-run diagnosis; updated four-entry section of the maintained run plan; existing Step05 `FlowCompilation.EntryContext` producer contract; Luna-owned public `BusinessMaterialBuilderTest` RED.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read the scoped source-agent instructions, repository TDD skill, diagnosis for the terminal four-entry run, and the updated run-plan contract.
- Confirmed the persisted run has four valid `COLLECTED` contexts and 322 valid `NOT_COLLECTED / NOT_SELECTED_FOR_SAMPLE` entries with null code and strict contexts; the full denominator must remain in coverage.
- Located the consumer rejection at `BusinessMaterialBuilder.entryContext(...)`: it currently rejects both context forms being null before interpreting `collectionStatus` and `collectionReason`.
- Received Luna's exact public RED: `BusinessMaterialBuilderTest#preservesNotCollectedEntryMaterialCoverageFromSelectedPublicWorkflow` reached `entryContext` and failed with `BUSINESS_MATERIAL_INPUT_INVALID` at the same line as the real four-entry run.
- Implemented the minimal status-aware reader validation from the existing `FlowCompilation.EntryContext` contract. `COLLECTED` requires matching code and no reason; `NOT_COLLECTED` requires a nonblank reason and no code; strict technical context remains optional; every other combination fails closed.
- Retained the complete entry denominator and existing strict-context fallback. When a valid uncollected entry has no materializable context, coverage now uses its exact `collectionReason` instead of `FLOW_NOT_COMPILED`.
- Focused Spotless completed and Maven was released to the parent for the fresh four-entry materials run.

## Current state

- Production correction and agreed direct verification are complete. The parent owns the new four-entry materials-only execution; this task did not run JDT, a model, a customer build, or add cross-run reuse.

## Changed files

- progress/jdt-not-collected-material-green.md (owned progress)
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java

## Planned seam and minimal contract

- Public seam: `BusinessMaterialBuilder.build(BuildBusinessMaterialsRequest)` through the persisted Step05 business-flow publication.
- `COLLECTED` requires matching entry code context and no collection reason.
- `NOT_COLLECTED` requires a nonblank collection reason and null code context; strict technical context remains optional.
- Entries without a readable context produce no material but retain `NOT_MATERIALIZED` coverage with their exact collection reason, including `NOT_SELECTED_FOR_SAMPLE`.
- Unknown or contradictory states fail closed; strict fallback behavior remains unchanged.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilderTest#preservesNotCollectedEntryMaterialCoverageFromSelectedPublicWorkflow test` | PASS | Public selected-workflow Builder regression: 1 test, 0 failures/errors. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilderTest test` | PASS | Existing Builder class: 6 tests, 0 failures/errors. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.runtime.TechnicalAnalysisWorkflowTest#selectedJdtRuntimePassesApprovedClasspathToDiscoveryAndTechnicalExecution test` | PASS | Repaired runtime classpath method: 1 test, 0 failures/errors. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java` | PASS | Focused formatting completed. |

## Next action

- No further source action. Parent may run only the already-authorized fresh four-entry materials scope.
