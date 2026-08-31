# Progress: Stage 03 proven-anchor core

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Replace synthetic Stage 03 anchor identities with deterministic, provenance-bearing bindings derived from replayed public Stage 01/02 structure.
- Approved inputs: scoped AGENTS, TDD guidance, Stage 03 design, Anchor RED contract, current Stage 01/02 public records and Stage 03 production.
- Current branch/worktree: shared worktree; preserve unrelated changes.

## Completed

- Read the public Anchor seam and confirmed current `CapsuleContext` builds `anchor:<kind>:sha256(flowSliceId + kind)`, which carries no source provenance.
- Confirmed task input already serializes typed anchors and M5 meanings/fallbacks consume the context anchor keys, so the smallest change is a provenance-aware replacement at context construction.
- Replayed the Anchor RED: the first observed anchor was exactly the old synthetic flow/kind hash.
- Passed `Stage01FlowView` into `CapsuleContext` and built each typed anchor from deterministic public source structures: entry/root/route, exact typed Fact/atom identities, first canonical OutcomePath/terminal, and a shared-step Fact binding.
- Added complete sorted `provenBindings` and a clean `provenDisplay` to every task anchor. M5 meanings/fallbacks continue to receive the same context keys; source IDs remain task-only.
- Preserved the 4 KiB scripted response budget by using one short proven primary token in each anchor key and retaining the complete binding list separately in canonical task input.

## Current state

- COMPLETE: all requested selectors are GREEN. No cross-flow merge or synthetic/name-based fallback was introduced.

## Changed files

- `progress/stage03-anchor-core.md` (this file)

## Decisions

- Anchor identity will be deterministic and include at least one exact public provenance token. Missing required evidence is fatal; no name-based or cross-flow merging is introduced.
- The task's bindings and M5 meanings/fallbacks must use the exact same anchor key.

## Blockers

- None.

## Exact next action

- None; assigned proven-anchor implementation and verification are complete.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03AnchorTest test` | RED | 1 test, 1 failure; a public anchor was exactly `sha256(flowSliceId + kind)`. |
| `mvn -Dtest=Stage03AnchorTest test` | GREEN | 1 test, 0 failures, 0 errors; all task and interpretation anchors reuse non-synthetic public-provenance keys. |
| `mvn -Dtest=Stage03AnchorTest,Stage03CapsuleTest,Stage03CompletenessTest,Stage03FormulaTest,Stage03GeneratorTest,Stage03IntegrityTest,Stage03JshErpBoundaryTest,Stage03ProofDensityTest,Stage03SemanticTest test` | GREEN | 32 tests, 0 failures, 0 errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors reported. |
