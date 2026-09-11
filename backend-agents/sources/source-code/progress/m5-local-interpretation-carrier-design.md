# Progress: M5 local interpretation publication carrier design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze the minimum M5 carrier RED/GREEN required by Step 06 publication readiness: semantic M4 task-set identity, complete R1/R2 GenerationReceiptV3 values, and complete same-Flow InterpretationProposal values embedded in every accepted Candidate.
- Approved inputs: Scoped `AGENTS.md`, Step 06 §§6.1, 6.7.2, 6.7.6, 6.7.7 and 7.1–7.2, completed M2 receipt-carrier design and implementation shape, M9 design, and current M4/M5 production sources and direct tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules and the exact M9 publication-readiness contract.
- Audited the persisted M4 `flow-task-set.json` body, M5 runner records, M5 `model-execution-set.json` publisher, and `InterpretationRunnerTest` public seam.
- Froze one bounded Luna RED and the minimum Terra GREEN for the three missing M5 publication carriers. No model grammar, source evidence, process interpretation, Step 06 file count, or downstream behavior is added here.

## Current state

- M4 already persists the semantic `FlowModelTaskSet.flowTaskSetId`, but M5 writes the M4 module artifact root into its `flowTaskSetId` field instead of reopening that semantic value.
- M5 R1/R2 receipts currently contain only three runtime artifact references and two digests. They do not contain the configured adapter/auth identities, materialized expected/observed runtime, generation kind and Flow scope, or started/completed fields required by `GenerationReceiptV3`.
- M5 creates complete `InterpretationProposal` values in memory and persists them in a top-level array, while each Candidate persists only proposal IDs. That is insufficient for the public Candidate projection that Step 07 must reopen without private M5 joins.
- The target carrier below makes the Candidate the sole canonical owner of its proposal values, records the original configured and observed Provider identity without hardcoded aliases, and carries the semantic M4 task-set identity into M5. It does not make the existing local reader packet compliant with the separate DRY-packet target and does not make M5 or Step 06 accepted by itself.

## Changed files

- `progress/m5-local-interpretation-carrier-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only contract/source inspection | PASS | Exact M4/M5 record, publisher, identity, and direct public-test changes identified. |
| `git diff --check -- progress/m5-local-interpretation-carrier-design.md` | PASS | No whitespace errors in the owned design record. |

## Decisions

### Semantic M4 task-set identity

- Add required `String flowTaskSetId` to `InterpretationExecutionSet`. `InterpretationRunner` must fresh-reopen M4 and return both the semantic `payload.flowTaskSetId` and its tasks; it may not derive this field from `ModulePublicationReference.moduleArtifactRoot`.
- M5 `payload.flowTaskSetId` is exactly that semantic M4 value. `InterpretationExecutionSetModulePublisher` fresh-reopens the exact M4 publication, reads its semantic ID, and rejects a mismatch before installation. The M4 artifact root remains only the content-addressed publication locator in `flowTaskSetPublicationRef` and upstream lineage.
- `executionSetId` replaces the M4 artifact-root input in its identity preimage with the semantic `flowTaskSetId`; it also includes the canonical ordered receipt, Candidate, task-disposition, and Flow-disposition IDs. Changing carrier content therefore cannot reuse the old execution-set identity. The enclosing M5 module artifact ID continues to be recomputed by the canonical store.

### Complete configured and observed identity for R1/R2

- Reuse the already introduced shared immutable `ModelRuntimeIdentityV1`; do not create another runtime-identity type.
- Extend `FlowModelTaskProfile` independently for R1 and R2 with required nonblank `configuredAdapterId`, required nonblank `configuredAuthMode`, the existing expected-runtime `ArtifactReference`, and one materialized `ModelRuntimeIdentityV1 expectedRuntime`. R1 and R2 values may differ; neither configured field is compared with `expectedRuntime.upstreamProvider`.
- Extend every persisted `FlowModelTask` with the selected round's `configuredAdapterId`, `configuredAuthMode`, renamed `expectedRuntimeRef`, and materialized `expectedRuntime`, all outside `inputJson`. `FiniteKeyFlowTaskCompiler` copies them from the matching round profile; `FlowModelTaskSetModulePublisher` writes and fresh-reopen consumers validate them. None of these program-only values may appear recursively in Provider-visible `inputJson`.
- Recompute each `taskSpecId` from canonical `FlowModelTask` content excluding only `taskSpecId`. The identity-significant content includes kind/round, Flow and Capsule scope, session, sorted finite keys, exact application JSON and digest, output-schema and prompt digests, configured adapter/auth, expected-runtime reference, and materialized expected runtime. Consequently an adapter/auth/runtime change cannot reuse a task or task-set identity. M4 keeps its already-frozen v4 type/schema; this is completion of that target wire, not a compatibility version.
- Change `FlowModelProviderResponse.observedRuntime` from an `ArtifactReference` to `ModelRuntimeIdentityV1`. The Provider interface and one-call semantics do not change. M5 requires exact full-value equality between task expected runtime and observed runtime; configured adapter/auth remain separate configuration identities and are never compared to the observed upstream provider.
- Replace the current partial `GenerationReceipt` shape with the exact M5 `GenerationReceiptV3` carrier fields: `schemaVersion=flow-interpretation-generation-receipt-v3`, `artifactType=FLOW_INTERPRETATION_GENERATION_RECEIPT`, computed `generationReceiptId`, `generationKind`, `taskSpecId`, nonnull `flowSliceId`, null `taskShardId`, `requestSha256`, `responseSha256`, `configuredAdapterId`, `configuredAuthMode`, materialized `expectedRuntime`, materialized `observedRuntime`, `started=true`, and `completed=true`.
- `generationKind` is derived only from the persisted task: R1 maps to `R1_FLOW_INTERPRETATION`; R2 maps to `R2_FLOW_PRECISION_REVIEW`. The receipt ID uses the same formal v3 algorithm as M2: `generation-receipt:` plus lowercase SHA-256 of `frame(UTF8("flow-interpretation-generation-receipt-id-v3")) || frame(canonicalJson(receiptWithoutGenerationReceiptId))`. Every listed field is identity-significant.
- `InterpretationRunner` creates a receipt only after one valid response and exact runtime equality. Provider exceptions retain `PROVIDER_FAILURE_AFTER_START`; mismatch retains `MODEL_RUNTIME_IDENTITY_MISMATCH`; neither path retries, changes Provider, fabricates a receipt, or produces a partial M5 publication.
- `InterpretationExecutionSetModulePublisher` validates every receipt against the exact M4 task and corresponding M5 round before serializing it unchanged: task/kind/Flow scope/configuration/expected runtime/request digest must match the task; response digest must match the round; expected and observed runtime must be equal; both booleans and the recomputed v3 ID must be valid. M5 keeps its already-frozen v5 type/schema.

### Candidate owns complete same-Flow proposal values

- Replace `FlowInterpretationCandidate.interpretationProposalIds` with an ordered, immutable `List<InterpretationProposal> interpretationProposals`. The M5 payload Candidate contains each proposal's complete value: `interpretationProposalId`, `registryProposalId`, `flowSliceId`, `provisionalKey`, `selectedKey`, sorted unique `basisAtomIds`, sorted unique `basisGapIds`, and `r2Decision`.
- Remove the duplicate top-level `InterpretationExecutionSet.interpretationProposals` and M5 `payload.interpretationProposals`. A proposal has exactly one canonical owner Candidate; M9 later projects the Candidate and its embedded values directly. No IDs-only compatibility field or duplicate owner is retained.
- `InterpretationRunner` builds the embedded list from the already validated R1 selection and R2 review. This carrier work performs no new semantic inference and does not change the closed R2 decision set. Each proposal must name the Candidate's Flow, its `registryProposalId/provisionalKey/selectedKey` must resolve to the same-Flow M3 item exposed by the matching M4 task, its basis arrays must be same-Flow finite values admitted by that task, and its R2 decision must be one of the existing four values.
- Recompute `interpretationProposalId` from canonical complete proposal content excluding only its own ID. Recompute `candidateId` from the complete Candidate content excluding only `candidateId`, including the ordered embedded proposal values. The publisher independently recomputes both IDs and rejects duplicate proposals, a proposal owned by two Candidates, a foreign Flow/key/basis, a Candidate without proposals, or a Candidate/Flow disposition mismatch before any install.
- Proposal order inside a Candidate is UTF-8 byte order by `interpretationProposalId`; Candidate order remains by `flowSliceId`. The set of embedded proposal values across Candidates must equal the accepted R2 proposal denominator. Dropped or evidence-needed proposals remain represented with their actual R2 decision; M5 does not silently convert or discard them here.

### One Luna/xhigh RED

- Add exactly one method to `InterpretationRunnerTest`: `persistsSemanticTaskSetIdentityCompleteR1R2ReceiptsAndEmbeddedProposalsWithoutReplay`.
- Build the existing real M1–M4 fixture, but give R1 and R2 deliberately distinct, non-product configuration sentinels and materialized runtime identities, for example `adapter-fixture-r1-alpha/auth-fixture-r1-beta/provider-fixture-r1-gamma` and corresponding R2 values. Assert M4 persists these values outside `inputJson`, and recursively assert the configured/runtime values are absent from both model-visible inputs.
- Fresh-reopen M4 and capture `payload.flowTaskSetId`; assert it is not the M4 `moduleArtifactRoot`. Execute the scripted Provider exactly once for each planned R1/R2 task, with the response reporting that task's exact materialized observed runtime.
- For every in-memory receipt, independently assert all formal v3 fields, round-derived generation kind, Flow-only scope, task/request/round-response closure, independent receipt-ID recomputation, and deliberately different configured adapter/auth versus upstream-provider values.
- For every Candidate, assert it contains complete proposal values and no `interpretationProposalIds`; assert each proposal is same-Flow, resolves to the matching finite M4 registry entry, carries the actual selected key/basis/R2 decision, and independently recomputes both proposal and Candidate IDs.
- Publish M5, fresh-reopen it, and assert `payload.flowTaskSetId` equals the semantic M4 value, every complete receipt and embedded Candidate/proposal is byte-value equivalent to the in-memory result, the old partial receipt fields and IDs-only Candidate field are absent, and the Provider counter has not changed during publication/reopen.
- Exact selector: `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest#persistsSemanticTaskSetIdentityCompleteR1R2ReceiptsAndEmbeddedProposalsWithoutReplay test`.

### Minimum Terra/xhigh GREEN

- Modify only `FlowModelTaskProfile`, `FlowModelTask`, `FlowModelProviderResponse`, `FiniteKeyFlowTaskCompiler`, `FlowModelTaskSetModulePublisher`, `GenerationReceipt`, `InterpretationProposal`, `FlowInterpretationCandidate`, `InterpretationExecutionSet`, `InterpretationRunner`, and `InterpretationExecutionSetModulePublisher`.
- Mechanically update only directly affected test fixture constructors. Use the existing `ModelRuntimeIdentityV1`; do not add a Provider adapter, retry/lifecycle logic, M7/M8/M9 code, Step 06 public files, new source/evidence analysis, or new model meaning.
- Do not call a Provider during `publish` or fresh reopen. Do not add defaults or compatibility constructors for missing adapter/auth/runtime; missing identity fails closed.
- After the exact method is GREEN, run only `FiniteKeyFlowTaskCompilerTest,InterpretationRunnerTest`. Apply Spotless only to the touched Java files, run the corresponding Spotless check, and run `git diff --check`.

## Blockers

- None.

## Exact next action

- Luna/xhigh writes and immediately runs the single `InterpretationRunnerTest` RED above; Terra/xhigh then implements only this frozen carrier GREEN and reruns the exact method before the two-class regression selector.

## Resume checks

- Preserve all unrelated shared-worktree edits.
- Do not edit Java, tests, Schema, Maven, Provider adapters, or target design documents in this task.
- Do not run Maven, a Provider, customer source analysis, network access, or Git mutations.
