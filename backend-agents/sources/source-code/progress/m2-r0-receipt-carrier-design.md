# Progress: M2 R0 GenerationReceiptV3 carrier design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-6-astra / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze the minimum M2 R0 carrier RED/GREEN needed before Step 06 M9 publication readiness.
- Approved inputs: Scoped AGENTS, Step 06 section 6.7.2 and GenerationReceiptV3 contract, current M1/M2 proposal sources and tests, and the two implementation plans.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read the scoped repository rules, both implementation plans, and the Step 06 publication-readiness contract.
- Audited `RegistryProposalTaskProfile`, `RegistryProposalTask`, M1 task compilation/publication, `RegistryProposalRunner`, `RegistryProposalGenerationReceipt`, `RegistryProposalExecutionSet`, M2 publication, and `RegistryProposalRunnerTest`.
- Froze one bounded Luna RED and the matching minimum Terra GREEN described below.

## Current state

- M2 currently persists only task/receipt runtime `ArtifactReference` values and request/response digests. It does not preserve configured adapter/auth, the materialized upstream runtime identity, the R0 discriminator/scope, started/completed, or the GenerationReceiptV3 identity preimage. Therefore M9 cannot legitimately project an R0 `GenerationReceiptV3` yet.
- The smallest correct seam is to freeze configuration in M1's R0 task, let the Provider response report only observed upstream runtime, create the complete receipt in M2, and have the existing Provider-free M2 publisher revalidate and persist it. M9 remains out of scope.

## Changed files

- `progress/m2-r0-receipt-carrier-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only contract/source inspection | PASS | Exact missing carrier fields and all affected R0 records/publishers identified; no Maven or Provider call. |
| `git diff --check -- progress/m2-r0-receipt-carrier-design.md` | PASS | No whitespace errors in the owned progress file. |

## Decisions

- This work freezes only the M2 R0 carrier prerequisite; it does not design or implement M9 publication.
- Add one shared immutable `ModelRuntimeIdentityV1` in `org.sourceanalysis.app.analysis.interpretation` with exactly four required nonblank strings: `upstreamProvider`, `model`, `reasoningEffort`, and `sandbox`. It contains no configured adapter or auth field.
- M1's `RegistryProposalTaskProfile` and each `RegistryProposalTask` carry, outside model-visible `inputJson`, exactly: `configuredAdapterId`, `configuredAuthMode`, `expectedRuntimeRef`, and materialized `ModelRuntimeIdentityV1 expectedRuntime`. Adapter/auth are required nonblank opaque configuration values; no closed list and no equality comparison with `upstreamProvider` is allowed. M1 task and task-set identities must include both configured strings and canonical expected-runtime value so a configuration change cannot reuse the same task identity.
- `RegistryProposalProviderResponse` reports only `ModelRuntimeIdentityV1 observedRuntime` plus exact response bytes. The runner requires full value equality between expected and observed runtime, while never comparing `configuredAdapterId` or `configuredAuthMode` to any observed provider field.
- Expand `RegistryProposalGenerationReceipt` into the exact nested R0 carrier of `GenerationReceiptV3`: `schemaVersion=flow-interpretation-generation-receipt-v3`, `artifactType=FLOW_INTERPRETATION_GENERATION_RECEIPT`, `generationReceiptId`, `generationKind=R0_REGISTRY_PROPOSAL`, `taskSpecId`, nonnull `flowSliceId`, null `taskShardId`, `requestSha256`, `responseSha256`, configured adapter/auth, materialized expected/observed runtime, and `started=true`, `completed=true`. Unknown/missing fields fail closed.
- Compute `generationReceiptId` independently of the legacy receipt formula as `generation-receipt:` plus lowercase SHA-256 of `frame(UTF8("flow-interpretation-generation-receipt-id-v3")) || frame(canonicalJson(receiptWithoutGenerationReceiptId))`. Every field above, including schema/type, scope, configuration, runtimes, booleans, and digests, is identity-significant.
- `RegistryProposalRunner` remains the only R0 caller and makes one call per persisted task. It constructs the complete receipt only after a valid response is returned and runtime equality succeeds. The existing started-call exception remains `PROVIDER_FAILURE_AFTER_START`; there is no retry, fallback, or fabricated incomplete receipt.
- `RegistryProposalExecutionSetModulePublisher` remains Provider-free. It fresh-reopens M1 and rejects any receipt whose task/scope/configuration/expected runtime/request digest differ from that exact task, whose response digest differs from its round, whose runtime equality/booleans/discriminators are invalid, or whose ID does not recompute. It serializes the full carrier unchanged into `payload.receipts[]`; publication must not invoke the Provider.

### One Luna/xhigh RED

- Add exactly one method to `RegistryProposalRunnerTest`: `persistsConfiguredAndObservedR0IdentityAsGenerationReceiptV3WithoutReplayingProvider`.
- Use deliberately non-product sentinel configuration values such as `adapter-fixture-alpha` and `auth-fixture-beta`, plus an expected/observed runtime `{upstreamProvider: "provider-fixture-gamma", model: "model-fixture-delta", reasoningEffort: "reasoning-fixture-epsilon", sandbox: "sandbox-fixture-zeta"}`. This makes any hardcoded `scripted`, `codex_subscription`, or `openai` implementation fail.
- Compile and persist the real M1 R0 task set; assert the two configured strings and materialized expected runtime are stored outside `inputJson` and are absent recursively from the model-visible `inputJson`.
- Run the existing recording scripted Provider once per R0 task, returning only the exact observed runtime and response bytes. For every in-memory receipt, independently assert every frozen GenerationReceiptV3 field, the exactly-one scope rule (`flowSliceId` set, `taskShardId` null), request/round response digest equality, expected/observed equality, both booleans, and independently recomputed v3 ID.
- Publish the execution set, fresh-reopen M2, and assert `payload.receipts[]` is byte-value equivalent to those carriers. Assert the Provider counter is unchanged by publication, proving no model rerun.
- Exact direct selector: `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#persistsConfiguredAndObservedR0IdentityAsGenerationReceiptV3WithoutReplayingProvider test`.

### Minimum Terra/xhigh GREEN

- Add only `ModelRuntimeIdentityV1` and modify: `RegistryProposalTaskProfile`, `RegistryProposalTask`, `RegistryProposalTaskCompiler`, `RegistryProposalTaskSetModulePublisher`, `RegistryProposalProviderResponse`, `RegistryProposalGenerationReceipt`, `RegistryProposalRunner`, and `RegistryProposalExecutionSetModulePublisher`.
- `RegistryProposalExecutionSet` needs no new field; it already owns the receipt list. Do not introduce a new Provider interface, public Step 06 publisher, schema file, retry/lifecycle module, adapter implementation, M5/M7/M8 change, or M9 code.
- Mechanically update only R0 test helpers that construct the changed profile/response types; never supply production defaults or compatibility constructors because missing configured identity must fail closed.
- After the exact RED is GREEN, run only the directly affected regression selector: `RegistryProposalTaskCompilerTest,RegistryProposalRunnerTest,RepositoryInterpretationRegistryFreezerTest,FiniteKeyFlowTaskCompilerTest,InterpretationRunnerTest`. Formatting is limited to the touched Java files.

## Blockers

- None.

## Exact next action

- Luna/xhigh writes and runs the single `RegistryProposalRunnerTest` RED above; Terra/xhigh then implements only the frozen carrier GREEN.

## Resume checks

- Re-read Step 06 sections 6.7.2 and 7.1/7.2 plus the current `RegistryProposalRunnerTest` before implementation.
- Confirm no other Agent has modified this progress file.
