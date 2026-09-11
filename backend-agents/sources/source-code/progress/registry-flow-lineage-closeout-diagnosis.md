# Progress: Registry-flow lineage closeout diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh bounded read-only debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Determine the published Registry-to-BusinessFlows lineage contract, the current M3 Registry upstream wire, and the smallest public-seam negative fixture exposing acceptance of a wrong-run, wrong-controls, or wrong-upstream Registry without changing Flow IDs or semantic basis.
- Approved inputs: Current published Step 05/06 contracts, current Registry/Flow compiler/publisher source, current tests/progress, and existing canonical module-store seams. No Maven, production/test/design/schema edits, network/source/Provider action, commit, push, or sub-agent.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve the active carrier-cutover WIP owned by other agents.

## Completed

- Re-read the systematic-debugging workflow and captured the shared-worktree baseline.
- Traced the existing published boundary rule to `docs/DESIGN.md:224-226`: downstream work may begin only after the receipt/root/control/accounting contract passes, and every named next module must fresh-reopen payload plus receipt and validate artifact ID/SHA/root/upstream/control references. Step 06 narrows the relevant public input to the five Step 05 semantic artifacts plus `business-flows-receipt.json` (`docs/analysis-steps/06-flow-interpretation.md:16-23`) and fixes the R0 -> M3 -> M4 order (`:58-76`).
- Traced the real M3 wire. `RepositoryInterpretationRegistryModulePublisher.publish` first proves the in-memory Registry belongs to the supplied BusinessFlows publication, fresh-reopens M1/M2/BusinessFlows, and requires their controls to match (`:73-92`). It addresses M3 with the BusinessFlows run ID (`:94-99`). Its receipt/envelope upstream list is exactly seven sorted, distinct references: M1's only task-set payload, M2's only execution-set payload, and all five reopened BusinessFlows semantic payloads (`:247-263`); its envelope repeats that producer, upstream list, and controls (`:266-300`).
- Located the consumer defect at `FiniteKeyFlowTaskCompiler.java:70-74,148-198`: M4 fresh-reopens BusinessFlows and Registry separately, passes `businessFlows.publication()` into `reopenRegistry`, but the method never reads that argument. It checks only M3 step/module/key and Registry payload shape. Consequently it accepts a generic-store-valid Registry whose run, controls, or BusinessFlows portion of `upstreamArtifacts` belongs to a different lineage, as long as the unchanged Registry body names Flow/capsule/basis IDs that exist in the separately supplied current BusinessFlows payload.
- Confirmed a bounded public-seam RED is feasible. Reuse the exact real Flow -> R0 M1 -> R0 M2 -> Registry freezer -> M3 publisher chain already present in `FiniteKeyFlowTaskCompilerTest.java:53-79`. Fresh-reopen the valid M3 publication, keep its `payload` node byte-for-byte semantically unchanged, and install one re-enveloped copy into a separate empty store through public `RunStoreBootstrap.openForTest`, `FileSystemCanonicalModuleArtifactStore`, and `ModuleInstallRequest`. The isolated store permits the same M3 address for controls/upstream cases without colliding with the valid publication. A test-local envelope rehash helper can follow the already accepted public-install pattern at `BusinessFlowProvenanceTest.java:735-777,846-888`.
- Defined three independent mutations over that one real semantic body: (1) change only `AnalysisStepModuleAddress.runId` and the envelope producer for wrong-run; (2) retain the original address/upstreams and change only one non-policy digest in `ArtifactControls` plus envelope controls for wrong-controls; (3) retain address/controls and replace one exact BusinessFlows semantic `ArtifactReference` in the seven-item sorted upstream list plus envelope upstreams for wrong-BusinessFlows-upstream. Rehash the module artifact ID with the existing `canonical-module-artifact-id-v1` framing, then install. Each decoy store validates the altered envelope/receipt itself, and the current M4 accepts all three because Registry semantic bytes, Registry ID/items/dispositions, Flow IDs, capsule IDs, and basis IDs remain unchanged.
- Calibrated the minimum owner fix: pass the already reopened `ReopenedAnalysisStepPublication flowPublication` to `reopenRegistry`; compare M3 address run ID and receipt controls exactly with that publication; derive the five exact `ArtifactReference(artifactId, sha256)` values from its semantic payload descriptors; require M3 `upstreamArtifacts` cardinality seven and containment of all five. Fail with the existing `FLOW_INTERPRETATION_INPUT_INVALID`. This is private implementation plus imports only: no schema, record, API, ID formula, Provider, or design change. M4 cannot independently reconstruct the identities of the two R0 payload references from its current public inputs, so the scoped upstream negative must replace a BusinessFlows reference; authenticating arbitrary substitute M1/M2 references would require a broader input/contract change and is not part of this P1.

## Current state

- Diagnosis complete. The defect is a production consumer contract under-validation, not an invalid test bootstrap. A public-store negative fixture can isolate all three currently missing joins without changing semantic Flow/Registry material.

## Changed files

- `progress/registry-flow-lineage-closeout-diagnosis.md` (owned diagnosis only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped `git status --short` | Read-only baseline captured | Registry/Flow source and tests contain active WIP owned by other agents; they will not be changed. |
| Static published-contract trace | PASS | DESIGN receipt/upstream/control rule and Step 06 five-payload BusinessFlows handoff agree with the real M3 publisher's seven-reference receipt. |
| Static public-fixture feasibility trace | PASS | Existing real-chain test plus public test-store bootstrap/install and canonical envelope rehash pattern are sufficient; no private filesystem state or new API is needed. |
| Maven | NOT RUN | Explicitly prohibited; Luna retains the sole Maven lease. |

## Decisions

- Do not inspect or alter the concurrent v5/v2 carrier cutover beyond the exact lineage fields needed for this diagnosis.
- Do not run Maven or create private filesystem state; use the existing public stores and publishers as the proposed negative seam.
- Keep the negative at M4's owned Registry-to-current-BusinessFlows join. Do not mutate Registry semantic body, Flow IDs, capsule/basis data, R0 execution semantics, or the concurrent carrier versions.
- Require three independently valid receipt mutations rather than one combined decoy so each missing run/control/BusinessFlows-upstream predicate is proven and no earlier predicate masks another.

## Blockers

- None for the scoped run/control/BusinessFlows-upstream P1. Exact authentication of the two opaque R0 upstream references is not derivable from M4's current inputs and is outside this fix.

## Exact next action

- Luna/Terra may add one public negative test (one real chain, three isolated decoy installs) and then apply only the private M4 comparisons above after the active carrier cutover is stable.

## Resume checks

- Maven was not acquired.
- No production, test, design, schema, source, network, Provider, commit, or push action is authorized.
