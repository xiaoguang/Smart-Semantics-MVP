# Progress: business-material-builder-implementation

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: First delivery of the business-first implementation plan: persisted `BusinessMaterialBuilder`, its public build seam, canonical `business-materials.jsonl`, and direct tests only.
- Approved inputs: Frozen-source, discovery, graph, fact, flow, and capsule artifacts already present in this source Agent; active Step 06 design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Inspected the active Step 06 design and confirmed that the legacy `interpretation/model`, `proposal`, `registry`, and `process` packages are not the target business-first path.
- Confirmed that existing Step 05 `BusinessFlowsReference` provides persisted `flow-slices.json` and `evidence-capsules.jsonl` material suitable for adaptation.
- Added the public `BusinessMaterialBuilder` seam and material records under `analysis.interpretation.material`.
- Implemented source-span revalidation, set-global short `SourceRef` allocation, stripped model packets, canonical `business-materials.jsonl`, material coverage, and bounded source selection.
- Implemented a safe no-Flow fallback that reopens the same frozen discovery/source input and locates the discovered Java handler; it retains the technical Flow Gap rather than inventing a Flow.
- Verified the zero-entry path end to end: the normal graph/Fact/Flow publication route and Builder persist an empty `business-materials.jsonl` rather than inventing activity material.
- Corrected the two pre-existing public JSONL readers that treated a policy-approved empty denominator as malformed input. The correction is limited to returning an empty list for zero bytes; non-empty JSONL remains newline- and object-validated.
- Persisted both `BUSINESS_MATERIAL` and `ENTRY_COVERAGE` records in canonical JSONL. Each material record stores the exact stripped `modelPacket` beside the program-only source map, so the first provider input can be inspected before a call.
- Removed duplicate short references within each model packet and filtered graph/evidence/internal-node identifiers from its technical observations.
- Verified a seven-entry synthetic replenishment-to-settlement scenario produces seven bounded, independently readable packets.
- Installed a temporary internal module slot for the new Builder while legacy Step 06 code remains compiled. It will be removed when the new activity/process/report path replaces the old semantic route.

## Current state

- Builder vertical slice is complete for compiled Flows, no-Flow handler fallback, zero entries, material budget rejection, persisted model-packet/coverage inspection, and the synthetic replenishment scenario. The model-facing packet now excludes internal Flow, Gap, and material identities; program-side records retain those values. ActivityExplainer remains a separate next delivery.

## Changed files

- `progress/business-material-builder-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/*`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/*`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared worktree changes identified and left unstaged. |
| source/Step 06 inventory | PASS | No target `BusinessMaterialBuilder` production implementation exists. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 7 tests; compiled and ran with Temurin 17. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessMaterialBuilderZeroEntryTest test` | PASS | 1 test; a zero discovered-entry denominator installs an empty model-preparation checkpoint. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest,BusinessMaterialBuilderZeroEntryTest,BusinessMaterialBuilderReplenishmentTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 9 tests; Builder material/coverage contracts and direct Step 05 regression are green. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest,BusinessMaterialBuilderZeroEntryTest,BusinessMaterialBuilderReplenishmentTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 9 tests; the clean model-packet regression confirms Flow, Gap, and material identities remain program-side. |
| scoped `spotless:check` + `git diff --check` | PASS | Only the Builder package and directly touched zero-entry boundary files were checked/formatted. |
| scoped Spotless apply | PASS | Formatted only the new material production/test package. |

## Decisions

- Preserve stable Step 01–05 algorithms and consume their persisted public artifacts rather than extending the obsolete finite-key/registry route.
- Keep the model packet free of paths, hashes, run IDs, and proof-chain internals; retain the source map in the persisted business-material output.
- Keep Flow, Gap, and material identities program-side as well: the ActivityExplainer already receives its enclosing `BusinessMaterial`, so the model packet needs no internal identity for correlation.
- Treat an empty canonical JSONL body as an allowed empty denominator only when its artifact policy permits an empty JSONL payload; all non-empty JSONL remains strict.
- Persist the exact model packet and entry coverage rather than relying on a later module to recreate them from an in-memory `BusinessMaterialSet`.
- Preserve old semantic module slots while this Builder uses a temporary internal slot; delete the old route only after the new activity/process/report path is complete.

## Blockers

- None for fixture-backed first delivery. A real fixed-repository run remains separately subject to source completeness and live-model authorization.

## Exact next action

- Create the ActivityExplainer public contract and first scripted-provider RED. Do not call a live Provider.

## Resume checks

- Read this file, inspect `git status --short`, and restrict staging to this task's `analysis/interpretation/material`, direct tests, active Step 06 maturity entry, and this progress file.
