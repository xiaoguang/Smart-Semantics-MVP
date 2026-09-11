# Progress: business-flows closeout contract ruling

- Status: COMPLETE
- Scope status: The ruling activity is complete; Step 05 itself is **not** accepted or complete.
- Agent role: Sole Sol/ultra design authority for this bounded ruling
- Model: gpt-5.6-sol / ultra
- Date: 2026-09-09
- Branch/base: `codex/source-analysis-business-flows-closeout` / `dea5c1bd96987270ecdc0f8060b612599b8f51d9`
- Worktree: `/private/tmp/linguan-source-analysis-process-design`
- Allowed write: this progress file only

## Completed

- Read the applicable `AGENTS.md` files, both implementation plans, Step 02/04/05 contracts, the two requested prior progress notes, relevant design law, and affected production/test seams.
- Compared the real ApplicationDiscovery publisher wire with the Fact and Flow readers.
- Traced current Gap producers through M1/M2/M3 and inspected the public low-level module-install seam.
- Issued the bounded decisions and implementation handoff below.

## Current state

- No design, Java, test, fixture, schema, Provider, customer-source, commit, or push work was performed.
- Discovery RED/GREEN has been routed separately by the root owner.
- After the bounded repairs and targeted verification, work must pause at Step 05. No later stage is authorized.

## Changed files

- `progress/business-flows-closeout-contract-ruling.md` only

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Branch/base and dirty-WIP preservation | PASS | Requested base confirmed; existing WIP was left untouched. |
| Real discovery writer vs Fact reader | PASS | Writer emits full profile/capability material but omits required coverage `closed`; Fact reader demands fields absent from the writer and rejects fields the writer really emits. |
| Gap producer trace | PASS | Step 04 owns `EXTERNAL_EFFECT`/`FACT_REJECTION`; M1 owns compiler gaps; M2 alone owns `CAPSULE_BUDGET_NO_SAFE_SPLIT`. |
| M3 trust seam | PASS | Generic canonical/hash validation does not prove that a rehashed M2 body is the exact semantic projection of its predecessors. |
| Maven/tests | NOT RUN | Prohibited for this ruling task. |

## Decisions

### 1. Discovery closure: existing contract, currently underimplemented

`repositoryEntryCoverage.closed` is already required by ApplicationDiscovery v2. Its absence is not a new schema choice.

- `ApplicationDiscoveryPublicationSpecifier` must emit it from the real public profile plus exact site/shard/entry accounting. It may be true only for `COMPLETE_CAPTURE`, `repositoryCompletionEligible=true`, complete exact denominators/receipts, and no unresolved repository-closure condition; bounded material is false.
- Missing, null, wrongly typed, or inconsistent `closed` is invalid. No missing-field-to-true fallback is permitted.
- Keep capability report v2: a contract-valid v2 never omitted the field. Migrate invalid synthetic fixtures instead of accepting two v2 shapes.
- `PersistedFactCandidateInputReader.parseDiscovery` must consume the complete current writer shape. Remove its demands for profile-body `sourceInventoryRef`, `verifiedSnapshotRef`, and `controls`, which the writer does not publish there; accept and validate the writer's real draft/source refs, sites, shard receipts, Gap refs, no-entry disposition, coverage counts/site counts, `noEntryDiscovered`, and `closed`. This is a compatibility repair, not a classifier or recovery feature, and requires no Step 04 public-schema change.
- `PersistedProgramGraphInputReader` must also require and validate the boolean at its existing discovery seam.
- M1 Flow `coverage.closed=true` may remain the local compilation-denominator result. M3 must freshly read ApplicationDiscovery and publish `repositoryFlowCoverage.closed = discoveryCoverage.closed && m1LocalCoverage.closed`. Flow coverage v1 already defines this field/equation and does not need a new version.
- Replace the four-field handcrafted capability payloads with the complete real writer shape. Describe the RED as public writer-to-reader compatibility, not full Capture/Discovery integration.

### 2. Fact/Gap provenance: existing omissions plus one bounded protocol repair

These are existing Step 05 obligations:

- Every `FlowFactView.originFactArtifactRef` is the full exact `ArtifactReference` of `proven-facts.json`.
- `FlowGapView.scope` is restricted to `FLOW|OUTCOME|FACT|ATOM`; current evidence supports `FACT` for Step 04 fact gaps and `FLOW` for compiler/projection gaps. Do not emit `OUTCOME` or `ATOM` without a producer that actually establishes that ownership.
- `evidenceRefs` must be typed full `ArtifactReference` values, never bare IDs. Program-generated compiler/budget gaps have no source-backed Proof, so their evidence list may be empty.
- A Step 04 gap retains its exact candidate-denominator semantics and exact `gap-ledger.json` reference. The M2 budget gap affects only its owning `flowSliceId` and must not claim a Step 04 row.

The frozen `originGapLedgerRef` alone cannot honestly encode all three finite producers. A Step 04 reference for an M1/M2 gap fabricates provenance; an M2 self-reference is impossible. The minimal truthful adjacent-protocol repair is:

```text
originKind = PROVEN_CODE_FACTS_GAP_LEDGER | FLOW_COMPILATION | CAPSULE_PROJECTION
originGapLedgerRef = required-nullable ArtifactReference
```

`originGapLedgerRef` is non-null iff the origin kind is `PROVEN_CODE_FACTS_GAP_LEDGER`; it is null for M1/M2-owned gaps. `CAPSULE_PROJECTION` is currently limited to `CAPSULE_BUDGET_NO_SAFE_SPLIT`. The owning carrier plus this discriminant supplies provenance without a self-reference. It does not change the Gap, eligibility, result, source excerpts, or claim authority, and it must be stripped with other program-only origin metadata before Provider material.

Ruling: the user's explicit permission for minor cross-module protocol changes that preserve the result covers this metadata repair. It does **not** authorize invented source evidence or Proof. Apply it docs-first and cut fresh affected carrier versions: M2 capsule projection v6 to v7, public evidence capsule v4 to v5, and normalized public flow-gap v1 to v2. M1 flow compilation v3, Step 04 v3, Flow slices v3, and Flow coverage v1 remain unchanged.

### 3. M3 semantic replay: existing contract, currently underimplemented

M3 cannot treat successful `CanonicalModuleArtifactStore.install`/reopen as owner-semantic validation. The smallest compliant implementation is:

1. Fresh-reopen M1, M2, source inventory, ProgramGraphs, and ProvenCodeFacts under exact references, run ID, controls, and lineage.
2. Read M2's persisted projection profile and invoke the existing `EvidenceCapsuleProjector` from reopened M1 and persisted inputs. Reopening verified source bytes is allowed; source parsing is not.
3. Share one pure canonical Capsule-body encoder between the M2 publisher and M3. Compare the complete rebuilt body byte-for-byte with reopened M2 before deriving any public file. This enforces exact Fact/atom/proof/origin, Gap/scope/evidence/origin, outcome, process-signal, span, obligation, and budget views required by Step 05 section 8.3.
4. Fail before Step 05 installation on any mismatch.

The public-seam negative test must deep-copy a valid M2 envelope, alter one owning semantic value (for example an atom's canonical value), recompute its artifact ID/hash, and install it through the public module store with otherwise valid address, controls, lineage, completion, and receipt. Generic reopen must succeed; `FlowPublicationSpecifier.specify` must reject it with the stable Flow publication invariant and leave no Step 05 receipt. Do not mutate store internals or reparse source.

## Exact next RED and implementation scope

1. Discovery compatibility RED in the ApplicationDiscovery publisher tests and Fact/ProgramGraph public-input tests: exact real writer shape succeeds; missing `closed` fails; true and false remain distinct. Migrate `ProgramGraphsPublicFixture` and dependent Fact/Flow fixture builders.
2. Closure RED in `BusinessFlowCoverageTest` / `BusinessFlowsPublicationSpecifierTest`: discovery false plus M1 local true publishes public false; absent `closed` fails.
3. Provenance RED in `EvidenceCapsuleProjectorTest` / `CapsuleProjectionModulePublisherTest`: exact Fact origin; exact Step 04 fact-gap origin/scope; all three finite origin kinds; required-nullable ledger rule; typed references; budget Gap has no ledger, no self-reference, and no invented evidence. Update only affected Capsule/Gap version assertions.
4. Replay RED in `BusinessFlowsPublicationSpecifierTest` through public module install; then implement the shared canonical encoder/profile read and inject `VerifiedSourceTextReader` into `FlowPublicationSpecifier` for complete reprojection comparison.

Known production surfaces: `ApplicationDiscoveryPublicationSpecifier`, `PersistedFactCandidateInputReader`, `PersistedProgramGraphInputReader`, `CapsuleProjection`, `EvidenceCapsuleProjector`, `CapsuleProjectionModulePublisher`, one shared Capsule canonical codec/profile reader, and `FlowPublicationSpecifier`. No new subsystem, classifier, recovery mode, evidence authority, or later-stage implementation is allowed.

## Blockers

- No additional provenance-approval question remains under the user's explicit minor-protocol permission.
- Full Step 05 acceptance remains blocked by the previously recorded domain gate and promisor source-copy permission. This ruling does not solve or weaken either.

## Exact next action

- Implement only the four RED groups and named surfaces above, run only directly affected tests, update the main closeout record, and then pause at Step 05.

## Resume checks

- Reconfirm branch/base and dirty paths; preserve all unrelated WIP and historical progress.
- Do not broaden into full Capture/Discovery orchestration, new evidence authority, customer source, Provider work, schema generation, commits/pushes, or Step 06+.
