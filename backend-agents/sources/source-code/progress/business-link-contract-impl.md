# Progress: business-link-contract-impl

- Status: IN_PROGRESS
- Agent role: delegated production contract and typed implementation
- Model: delegated Codex agent
- Started: 2026-10-05
- Last updated: 2026-10-05
- Scope: scope-v2 purpose/objectSources; typed candidate/review-v4 displayRole/clueDispositions; reference validation, versioned Prompt/typed job persistence; subsequently delegated v4 assembler and standalone offline overview renderer
- Owning plan: docs/plans/business-link-first-implementation-plan.md
- Approved inputs: fixed R4 analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744; fixed R0 analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7
- Current branch/worktree: codex/ontology-recognition; technical-entry-evidence worktree

## Completed

- Read repository instructions, owning plan, design sections 9.4, 11 and 14, and the progress template.
- Confirmed existing staged and unstaged work; preserving all pre-existing changes.
- Located ScopeReader v1-only fields, typed-v3 dynamic Schema and inspect calls, formal task identity, v3 review restoration, configured Prompt defaults, and artifact policy gates.
- Agreed the typed-v4 inspect API with the parent and test owner; direct RED was established before behavioral edits.
- Parent reported direct Scope RED: 8 tests, 2 positive v2 failures at the v1 unknown-field gate; legacy and rejection cases passed.
- Implemented ScopeReader v2 strict field admission, purpose/task rules, and per-question external source syntax while retaining v1 constructors.
- Implemented typed-v4 closed candidate/review schema and reference diagnostics with explicit visible K, preserving old v3 methods. Parent reported typed-v4 RED (7 errors) then typed7/scope8/artifact6 GREEN in one serialized build.
- Implemented model-reading-v5-gated formal-v4 runner identity and v3 persisted job result with exact visible K restore. Added eight independent v2 Prompt resources and configuration selector that preserves explicit overrides and old canonical v1 configuration.
- Updated current scope/typed contracts and researched the official Mermaid 11.12.0 IIFE build for offline use.
- Added explicit `assembleFormalV2` obligations and deterministic ontology-v2/coverage-v3/review-v3 assembly, preserving old four-file entry. Parent reported two new direct Assembler tests GREEN after a true typed-v4 RED.
- Corrected the v2 obligation rule to allow exact visible K in all task kinds while aggregating business-link dispositions only for RELATE. Implemented the standalone four-file offline HTML renderer after two direct RED tests; parent reported both GREEN. Added a third direct test using real v2 assembler output to check source/unknown consistency; its run is pending.
- Parent reported the real assembler→renderer test GREEN. Added a fourth direct counterexample for long Chinese labels with conditions, punctuation and multiple mechanisms; parent reported its RED, then the renderer was changed to reversible Mermaid decimal entities without truncation and to show every mechanism. Its GREEN is pending.

## Current state

- Scope/typed, v2 assembly and renderer's first three direct contracts are GREEN per parent's serialized builds. Runner/Store/Config full regression and runtime strategy-based Prompt selection remain parent-owned. Long-label/multi-mechanism GREEN and offline browser QA remain pending; no edits to existing PreviewRenderer or runtime/Atomic/Corpus/Packet.

## Changed files

- progress/business-link-contract-impl.md
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyScopeReader.java
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyTypedDefinitionValidator.java
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyTypedTaskRunner.java
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyJobResultStore.java
- src/main/java/org/sourceanalysis/app/adapter/cli/OntologyConfiguration.java
- src/main/resources/org/sourceanalysis/app/analysis/ontology/formal-*-v2.txt (eight new resources)
- docs/modules/ontology-recognition/scope-and-reading.md
- docs/modules/ontology-recognition/contracts.md
- docs/supplements/mermaid-offline-overview-resource.md
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyScopedAssembler.java
- src/test/java/org/sourceanalysis/app/analysis/ontology/OntologyBusinessLinkAssemblyV2ContractsTest.java
- src/main/java/org/sourceanalysis/app/analysis/ontology/OntologyBusinessOverviewRenderer.java
- src/test/java/org/sourceanalysis/app/analysis/ontology/OntologyBusinessOverviewRendererTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | inspected | Pre-existing staged and unstaged changes present. |
| `git branch --show-current` | inspected | `codex/ontology-recognition`. |
| Parent's targeted Scope RED | failed as expected | 8 tests, 2 positive v2 cases rejected by the old unknown-field gate. |
| Parent's targeted typed-v4 RED | failed as expected | 7 tests errored on the old v3 schema/empty root diagnostic path. |
| Parent's targeted Scope+material GREEN | passed | 25 direct tests including ScopeReader v2. |
| Parent's targeted typed/Scope/artifact GREEN | passed | typed7, scope8, artifact6; Corpus2 had one independent failure then was fixed by parent. |
| Parent's targeted Corpus/material/frontend GREEN | passed | Corpus3 + FormalMaterial17 + frontend direct regressions; 24 direct tests. |
| Parent's targeted Assembler V2 RED | failed as expected | 2 direct tests; positive rejected at old review-v3 identity gate, negative rejected. |
| Parent's targeted Assembler V2 GREEN | passed | 2 direct tests. |
| Parent's targeted renderer RED | failed as expected | 2 direct tests: empty shell output and unknown-version non-rejection. |
| Parent's targeted renderer GREEN | passed | 2 direct tests. |
| Parent's targeted real assembly→renderer GREEN | passed | Third renderer direct test; actual v2 four-file shape and source/unknown mapping. |
| Parent's targeted long-label/multi-mechanism RED | failed as expected | Current graph label dropped punctuation/truncated and used only first mechanism. |

## Decisions

- Keep old version readers strict and add explicit new versions; preserve public request-v6/output-v10.
- Do not modify EvidenceCorpus, ReadingPacket/ModelProjection, runtime main execution loop, Luna's tests, customer source, or legacy MD.
- Add explicit typed-v4 `forKindV4`, `inspectFormalCandidateV4`, `inspectFormalReviewV4`, and `validateFormalReviewV4` paths while retaining v3 methods. They accept the actual visible K set explicitly; the reading/runtime owner must pass it rather than inferring it from raw Scope.
- New formal task/result identity is v4 only for packet model-reading-v5; old v3 job result/profile is exact and unchanged.
- Prompt v2 selection must depend on exact policy content `ONTOLOGY_IDENTIFICATION/ontology-identification-v3`, not policy filename or prompt equality; runtime owner must call `forTypedV4()` before request binding.
- Mermaid 11.12.0 `dist/mermaid.min.js` official IIFE build sets `splitting:false`; research note explicitly leaves published tarball and offline-browser QA unverified.

## Blockers

- Parent owns serialized build timing and remaining Runner/Store/Config integration verification.
- Long-label/multi-mechanism direct GREEN and offline browser verification have not yet run.

## Exact next action

- Ask parent to run the fourth renderer direct test plus peers after entity/multi-mechanism patch; inspect failures without touching runtime/store and report remaining offline browser QA.

## Resume checks

- Recheck `git status --short`, this progress record, and the exact RED failure before changing production code.

## Plan closeout destinations

- Durable decisions: docs/modules/ontology-recognition/business-link-first-design.md and active contracts.
- Remaining issues: owning plan's final acceptance record.
- Verification and output references: progress/business-link-first-execution.md and final acceptance record.

Keep this handoff while the plan is active. At whole-plan closeout, consolidate
the information above into its durable destinations and remove the temporary
task file; do not archive a second copy of the progress record.
