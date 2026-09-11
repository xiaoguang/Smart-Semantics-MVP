# Progress: seven-entry synthetic BusinessFlows acceptance

- Status: COMPLETE
- Agent role: Luna/xhigh bounded read-only test-design owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Prepare the Step05 §8.6 synthetic replenishment-to-settlement acceptance for seven distinct declared HTTP entries, real persisted graph/fact/M1/M2/M3 Flow chain, seven Flow↔seven Capsule closure, signal equality, and coverage denominator seven.
- Approved inputs: `ProgramGraphsPublicFixture` factories/source/discovery material, the published Step05 §1/§8.6 contract, and public Flow compilation/publication and Capsule projection/publication seams. No existing two-entry fixture behavior changes, no production/design changes, and no Maven during preparation.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve unrelated shared-worktree changes.

## Completed

- Created and intent-to-added this owned progress file before any Java or fixture edit.
- Java/fixture implementation is now drafted in the two authorized paths; the final frozen snapshot includes the public-seam assertion and its canonical-payload helper fix. No production, design, or Maven changes were made for this slice.

## Read-only preparation

- Read the source-code scoped contract and the Step05 §1/§8.6 contract required for this synthetic acceptance.
- Inspected the existing fixture source/discovery factories and the public `EntryRootedFlowCompiler`, `FlowCompilationModulePublisher`, `EvidenceCapsuleProjector`, `CapsuleProjectionModulePublisher`, and `FlowPublicationSpecifier` seams.
- Confirmed the public M3 coverage shape is the persisted `flow-coverage.json` object: `entryIds`, `compiledEntryIds`, `gappedEntryIds`, `excludedEntryIds`, `flowSliceIds`, `capsuleIds`, signal IDs, Gap IDs, model-eligibility partitions, and `closed`.
- Confirmed `FlowCompilation` requires one terminal disposition per discovered entry and one distinct compiled Flow per compiled entry; `CapsuleProjection` requires distinct `flowSliceId` ownership and independently closed signal/span/obligation bindings; M3 rejects any Flow/Capsule mismatch before install.

## Current state

- The bounded implementation and verification are complete; Maven lease is released. No Step06 work was added.

## Required acceptance shape

- Seven actual declared methods and seven actual discovery entry IDs, each backed by real ProgramGraphs and the existing Fact M1/M2/M3 publishers.
- Seven Flow slices with seven distinct roots/entry owners and seven projected Capsules; every source/proof/terminal/Gap reference is closed within its owning Flow/Capsule.
- Flow signal arrays equal their independently projected Capsule signal arrays by canonical identity/content, with no cross-Flow borrowing.
- Coverage denominator is exactly seven, and the synthetic source/discovery unit is explicitly labeled as such; no inference of ordering, database update, or `DOMAIN_SPECIFIC` meaning from names/shared tables.

## Proposed smallest additive fixture

- Added one additive `ProgramGraphsPublicFixture.createSyntheticReplenishmentToSettlement(Path)` factory. Existing `create`, guarded, shared-call, source text, entry IDs, snapshot/profile labels, and behavior remain unchanged.
- Give the synthetic factory one real controller source document containing seven distinct declared methods and seven distinct boundary interfaces/call targets. Suggested methods/entry keys are `submitReplenishment`, `approveAtStore`, `approveRegionAndCreatePurchaseOrder`, `approvePurchaseOrderAndProcessExpense`, `executePurchaseAndRegisterLogistics`, `receiveAndRegisterInventory`, and `generateConfirmAndSettleMonthlyBill`.
- Publish seven matching `HttpEntryPoint` records with unique routes and handler FQNs for those methods, route/method excerpts taken from the real synthetic document, and the existing canonical source/discovery/graph execution. Each entry calls its own boundary interface method, so no metadata is duplicated onto one method and each root has an independently rooted call/evidence basis.
- Mark the factory/source snapshot as a synthetic unit fixture (not a full-repository capture and not a jshERP/domain claim). The seven names are labels for entry identity only; no test assertion may infer sequence, database updates, shared-table semantics, or `DOMAIN_SPECIFIC` meaning.
- Reuse the existing mapper document only as an unchanged discovery predecessor if the graph/discovery contract requires it; do not use it as a seventh entry or as evidence for business semantics. Keep the existing two-entry factory paths byte/behavior compatible.

## Proposed one public-seam test

- Added `SyntheticReplenishmentBusinessFlowsTest` at the authorized public seam. It executes the real Fact M1 candidate/proof/ledger publication and Flow M1 → Capsule M2 → Business Flows M3 join with generous profiles (`maxFlows` 16, capsule budget sufficient for seven).
- Before compiling, reopen ApplicationDiscovery and assert its capability report has `repositoryEntryCoverage.entryCount == 7`, exactly seven distinct entry IDs, and seven distinct handler/method declarations matching the synthetic source. Do not assert an order between them.
- Compile from the real persisted source/discovery/ProgramGraphs references and real Proven Code Facts. Assert seven `COMPILED` entry dispositions, seven distinct Flow IDs, seven distinct root node IDs, and one disposition/Flow owner for each discovered entry. Assert every Flow has non-empty facts/atoms, at least one outcome, and all Flow fact/atom/proof/evidence/Gap references resolve through the reopened predecessor set.
- Publish and fresh-reopen M1; assert seven flow slices and seven entry dispositions are retained byte-for-byte, with every process-join signal owned by its Flow, `GENERIC_TECHNICAL` specificity, closed proof/evidence/source basis, and any external-effect Gap ID present in both the Flow and its owning persisted Gap.
- Project/publish/fresh-reopen M2; assert seven Capsules, exactly one Capsule per Flow ID and entry ID, non-empty fact/outcome/span/obligation material, closed source/proof/terminal/Gap views, no span/obligation ID is reused across Capsules, and each Capsule signal array is canonically identical to its owning M1 Flow signal array. Assert every signal’s basis IDs and every projection obligation/span are local to that owner.
- Run the real public `FlowPublicationSpecifier.specify` seam and fresh-reopen M3. Assert the exact five semantic files, `flow-coverage.json.closed == true`, coverage `entryIds`, `compiledEntryIds`, `flowSliceIds`, and `capsuleIds` each have cardinality seven and set equality, with no `gappedEntryIds`/`excludedEntryIds` unless the real compiler emits a documented disposition. Assert public Flow and Capsule signal arrays remain canonical-byte equal to M1/M2, and all five files have closed local references. Do not assert a hard-coded standard signal count because the seven-entry source has one boundary per root and the accepted signal multiplicity is an implementation result of the real graph/fact basis.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (owned additive synthetic factory/source/discovery material)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/SyntheticReplenishmentBusinessFlowsTest.java` (owned public-seam acceptance)
- `progress/seven-entry-business-flow-tests.md` (owned; intent-to-added)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=SyntheticReplenishmentBusinessFlowsTest test` | PASS, numeric exit 0, session 33588 | Tests 1, Failures 0, Errors 0, Skipped 0; BUILD SUCCESS. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/publish/SyntheticReplenishmentBusinessFlowsTest.java spotless:apply` | PASS, numeric exit 0, session 50202 | Exactly 2 files selected; 2 changed to clean. |
| Same exact two-file `spotless:check` | PASS, numeric exit 0, session 47203 | Exactly 2 files clean; BUILD SUCCESS. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=SyntheticReplenishmentBusinessFlowsTest,EntryRootedFlowCompilerTest test` | PASS, numeric exit 0, session 30510 | Tests 8, Failures 0, Errors 0, Skipped 0; synthetic 1 + compiler 7; BUILD SUCCESS. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java src/test/java/org/sourceanalysis/app/analysis/flow/publish/SyntheticReplenishmentBusinessFlowsTest.java progress/seven-entry-business-flow-tests.md` | PASS | No whitespace errors. |

## Blockers

- Compile/test behavior is unverified by this agent because Maven remains under the coordinated lease.

## Exact next action

- Bounded slice complete; retain the three owned paths and stop.

## Resume checks

- Keep scope to this progress file during preparation. Do not alter existing two-entry samples, production, design, fixtures, other tests, or Step06; do not run Maven until root releases the gate.
