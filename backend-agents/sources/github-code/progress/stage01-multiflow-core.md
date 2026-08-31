# Progress: Stage01 multiflow core

- Status: COMPLETE
- Agent role: Stage01/02 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Make independently proven Stage01 entry closures compile into independently admitted Stage02 flows without weakening Stage02 admission or sharing facts across entries.
- Approved inputs: Scoped AGENTS, Stage01 and Stage02 designs, Stage03 multi-flow RED contract, current Stage01/02 implementations, scripted fixtures.
- Current branch/worktree: Shared worktree; preserve unrelated work.

## Completed

- Read scoped AGENTS, the Stage03 multi-flow RED contract, and the public two-independent-flow fixture.
- Created this owned progress record before production edits.
- Read the complete Stage01/Stage02 designs, `ProvenFactCompiler`, `Stage02Compiler`, and the relevant Stage01 flow-view/CFG implementation.
- Reproduced the public two-flow RED from the shared worktree.

## Current state

- Closure-bound M3 generation is GREEN. Independent controller/service/mapper/XML closures each produce their own content-addressed facts, atoms, and proof closure; a service shared by several controllers remains owned once by the deterministic first route.

## Changed files

- `progress/stage01-multiflow-core.md`
- `src/main/java/com/linguan/codemd/stage01/ProvenFactCompiler.java`
- `src/main/java/com/linguan/codemd/stage02/Stage02Compiler.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03MultiFlowTest test` | RED | 1 test: 1 assertion failure, 0 errors. Both public routes are discovered, but both entry dispositions are blocking `GAP/FLOW_FACT_NOT_ADMITTED`; `flowSlices=0`, `evidenceCapsules=0`. |
| `mvn -Dtest=ProvenFactExtractionTest,Stage03MultiFlowTest test` | GREEN | 7 tests, 0 failures/errors. Baseline M3 accounting and the independent two-flow public seam both pass. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | RED during compatibility audit | 79 tests: 4 failures and 2 errors. The closure model had changed legacy candidate-denominator/proof-root/projection behavior; no Stage02 admission rule was relaxed. |
| `mvn -Dtest=ProofSemanticClosureRegressionTest,Stage02CompilerTest,Stage03MultiFlowTest test` | GREEN | 30 tests, 0 failures/errors after preserving unresolved-entry accounting, semantic SQL-root locators, and minimal legacy projection roots. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures/errors/skips. |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest,Stage03IntegrityTest,Stage03CompletenessTest,Stage03SemanticTest,Stage03ProofDensityTest,Stage03FormulaTest,Stage03CapsuleTest,Stage03AnchorTest,Stage03TaskBodyTest,Stage03MultiFlowTest test` | GREEN | 46 tests, 0 failures/errors/skips. |
| `git diff --check` | GREEN | Exit 0; no tracked-diff whitespace errors. The shared Stage directories are intentionally untracked in this worktree, so their unrelated contents remain untouched. |

## Decisions

- No fixture, Stage03, or Stage02 admission relaxation will be used. The intended repair must make each entry own a content-addressed, semantically closed Stage01 fact/atom/proof set.
- Root cause is before Stage02: M3 only emits a single hard-coded Reservation F01–F08 candidate set and resolves each component from the globally first matching node. The independently proven Shipment closure has no candidate facts; Stage02's entry-root ownership gate therefore correctly rejects both entries rather than borrowing the Reservation facts.
- The repair derives each workflow from a real `CFG_ENTRY` controller-to-service link, scopes every candidate to its reachable controller/service/mapper/XML/config paths, and groups shared service roots so their facts cannot be duplicated.  Stage02 still requires a proven request-record root; it now checks the exact proven type's simple-name binding instead of a fixture package name.
- An entry whose route is parsed but whose direct target is unresolved retains only an entry-local provisional closure. It produces candidate/rejection accounting without becoming a source of admitted facts, avoiding both denominator shrinkage and cross-entry borrowing.

## Blockers

- None yet; root-cause investigation is in progress.

## Exact next action

- None. Requested Stage01→Stage02 independent multi-flow vertical slice is complete.

## Resume checks

- Verify only this progress file plus necessary Stage01/Stage02 production paths change; preserve all existing shared-worktree edits.
