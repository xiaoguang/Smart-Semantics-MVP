# Progress: selected-scope material accounting RED

- Status: COMPLETE
- Agent role: bounded public-seam regression tests, tests only
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: prove that BusinessMaterialBuilder preserves selected-scope collection accounting from the public technical workflow.
- Approved inputs: existing guarded Java public fixture, coherent JDT test session, public TechnicalAnalysisWorkflow and BusinessMaterialBuilder APIs; no scans, live providers, or customer builds.
- Current branch/worktree: shared `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code` worktree

## Completed

- Read nearest repository guidance and the TDD skill.
- Confirmed the existing builder rejects an entry context with both `codeContext` and `strictTechnicalContext` absent, while the selected JDT route intentionally persists that state for unselected entries.
- Added one public-seam regression to `BusinessMaterialBuilderTest`: selected workflow output must retain a two-entry denominator, materialize the selected entry, and report the unselected entry as `NOT_MATERIALIZED` with `NOT_SELECTED_FOR_SAMPLE`.
- Obtained the intended precise RED: the test reaches `BusinessMaterialBuilder.entryContext` and currently fails before material coverage can be produced.

## Current state

- Test-only RED is complete and ready for the builder implementation owner.

## Changed files

- `progress/jdt-not-collected-material-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilderTest#preservesNotCollectedEntryMaterialCoverageFromSelectedPublicWorkflow test` | RED (expected) | Tests run: 1, failures: 0, errors: 1; `BusinessMaterialException: BUSINESS_MATERIAL_INPUT_INVALID` at `BusinessMaterialBuilder.entryContext(BusinessMaterialBuilder.java:1197)`. |

## Decisions

- Keep the test at the public workflow → persisted Business Flows → public material builder seam.
- Assert the full two-entry denominator, selected material ownership, and exact `NOT_SELECTED_FOR_SAMPLE` coverage reason for the unselected entry.

## Blockers

- None known.

## Exact next action

- Parent/implementation owner should run this exact selector after allowing `NOT_COLLECTED` contexts with absent code and strict contexts; this agent will not modify production code.

## Resume checks

- Preserve unrelated worktree edits and do not modify production code.
