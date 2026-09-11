# Progress: fixed-repository-execution-seam-ruling

- Status: COMPLETE
- Agent role: Bounded real-offline execution composition design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Determine the smallest in-scope seam for real, fixed-repository offline acceptance through BusinessFlows, distinguishing test infrastructure from fabricated analysis inputs and missing product runtime from missing acceptance composition; read-only ruling only.
- Approved inputs: Applicable instructions/skills; authoritative Foundation, verified-inventory, BusinessFlows, runtime, and implementation-plan contracts; `progress/fixed-repository-flow-closeout-preflight.md`; exact capture/store/publisher/executor code and the existing real-capture inventory test; root-supplied facts and clarification.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code` (supplied by root; Git calls prohibited).

## Completed

- Read the scoped source-agent instructions and progress template.
- Read and applied the codebase-design and systematic-debugging guidance for this bounded seam/root-cause ruling.
- Read the relevant Foundation, Step 01, Step 05, delivery-plan, and toolchain-plan contracts and the current implementation seams.
- Traced the existing real-capture inventory test through `LocalGitCommitCaptureAdapter`, `LocalGitSourceRegistry`, `FrozenRequestAdmission`, M1/M2 module publication, M3 publication, and fresh reopen.
- Distinguished an absent product/runtime entry from the already-legal integration-test composition path.
- Prepared the exact narrow Step 05 acceptance clarification and test/configuration map below without editing normative design, Java, tests, Maven, Git state, or prior progress.

## Current state

The real fixed-repository acceptance is **not blocked by** `RunStoreBootstrap.open(Path)` or by the absence of a public inventory executor. It may run as one explicitly selected Failsafe integration test using `RunStoreBootstrap.openForTest` over an existing empty, non-symlink test directory, because that method is the published real-filesystem test bootstrap, not an in-memory or fabricated store. The test may live in the inventory package and compose its package-private M1/M3 publishers around the public M2 indexer, exactly as the existing real-capture test already does. It must then call the actual discovery, graph, Fact, and Flow production components over fresh-reopened publications.

This establishes a real offline acceptance path without claiming that the production root bootstrap, runtime, public `RepositoryAnalysisAgent`, CLI, or HTTP adapters are ready. Those remain later Adapter/runtime work. Capture workspace, actual upstream/publication artifacts, and a canonical JSON acceptance report must be retained under this Agent's ignored `.workspace` on success or downstream failure; this is diagnostic evidence preservation, not same-run recovery. The separate unresolved Step 05 domain-classification exit criterion is unaffected: this harness enables the real run but cannot by itself satisfy or relax that criterion. **This ruling is COMPLETE; the offline acceptance itself was not run and is not complete.**

## Changed files

- `progress/fixed-repository-execution-seam-ruling.md`

## Evidence and contract citations

| Evidence | Ruling consequence |
| --- | --- |
| `AGENTS.md:51-60` authorizes one read-only offline capture of `https://github.com/jishenghua/jshERP.git` at exact commit `8c30ce7861570458920175e200bb2a6442713580`, while forbidding customer Maven/scripts/application execution and refresh. | The integration test may give only that local repository Path and exact commit to the maintenance capture adapter; it may not run customer code or select a live ref. |
| `docs/DESIGN.md:2598-2600,2638-2640` publishes both bootstrap methods and states that `openForTest` uses the real filesystem, force, and atomic move and is not an in-memory fake. | `openForTest` is valid test-scoped acceptance infrastructure. The initial blanket prohibition in this progress record was incorrect and is expressly withdrawn. |
| `docs/DESIGN.md:2628` authorizes a private, path-free `ArtifactReference`-keyed input-byte reader for analysis-step composition; `docs/DESIGN.md:2636` deliberately separates Foundation/store/runtime delivery slices. | A test-local immutable input registry may reopen exact configuration/request bytes; acceptance does not require a fourth store or runtime implementation. |
| `docs/DESIGN.md:2991-2995` separates per-step/mutation testing and real fixed-slice acceptance from `RepositoryAnalysisAgent`/adapter/runtime conformance at `docs/DESIGN.md:3004-3005`. | A lower-level fixed-repository integration test is an intended acceptance mechanism, not a disguised public runtime. |
| `docs/analysis-steps/01-verified-source-inventory.md:19-29,70-82` freezes real raw-object capture, complete tracked regular-file coverage, and the exact M1→M2→M3 order. `:206,225,258-264` freezes the typed handoffs and input-byte reopen. | The test must create Step 01 from the registered real capture and actual publishers/readers; it may not hand-install a source inventory or substitute a fake discovery artifact. |
| `docs/analysis-steps/01-verified-source-inventory.md:427-430` records an already-implemented real-capture M1→M2→M3 test assembly, explicitly says product entry is still absent, and assigns later product orchestration to the Adapter phase. | Package-local test composition is already recognized by the design; lack of a public inventory executor is not an acceptance-test contract gap. |
| `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifierTest.java:195-260` performs a real local Git capture, converts the fresh-reopened registered capture to the admission boundary, publishes M1/M2/M3, and reopens the public Step 01 set. | The capture-to-inventory mechanics are demonstrated. The fixed-repository test must replace that test's synthetic source and repeated-hex control references with the approved real commit and verified canonical configuration bytes. |
| `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java:10-17` confirms `open` throws while `openForTest` returns `FileSystemRunStoreHandle.openEmptyTemporaryDirectory`. | The production opener remains a real later product gap, but adding it now is unnecessary for acceptance. |
| `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexer.java:49-65` is the public M2 entry that fresh-reopens M1 before opening the private source registry; M1/M3 publishers are package-private. | Put the integration test in `org.sourceanalysis.app.analysis.inventory`; no visibility widening or public executor is needed. |
| `docs/analysis-steps/05-business-flows.md:11,55-66,134-137,155,188-197,540-558` requires the complete entry denominator, actual M1→M2→M3 Flow path, fresh persistence, and fail-closed coverage. | The integration test must execute the real chain and assert closure from reopened artifacts; a target JSON, synthetic graph, or one-Flow success is not acceptance. |
| `docs/plans/source-analysis-naming-and-delivery-plan.md:232-245` makes fixed-jshERP verification part of BusinessFlows delivery, while `:301-329` assigns the public seam/adapters to a later task. | Step 05 acceptance precedes and does not require the public runtime batch. |

## Ruling

### 1. Smallest legal execution path

Add one opt-in Failsafe selector, `org.sourceanalysis.app.analysis.inventory.FixedRepositoryBusinessFlowsIT`, after the design-first clarification below. It uses separate, unique directories below this Agent's ignored `.workspace` for the private capture workspace and empty `openForTest` store, then executes real `LocalGitCommitCaptureAdapter`/registry → actual inventory M1/M2/M3 → `ApplicationDiscoveryExecutor` → `ProgramGraphsExecution` → persisted Fact M1/M2/M3 → BusinessFlows M1/M2/M3. Every public step is fresh-reopened and checked for the exact commit/snapshot, `COMPLETE_CAPTURE`, repository eligibility, complete file/entry denominators, receipt-last publication, and Flow/Gap/eligibility accounting. A `finally`-safe test diagnostic preserves those artifacts and a canonical JSON acceptance report in the same ignored workspace whether the last step passes or fails; helper mechanics remain test-private.

The deterministic `RegisteredSourceCapture`→`CaptureReceiptView` projection may remain test-local and use the published file-ID formula. M2 reopens M1 and recomputes capture identities and bytes, so this is an adapter over the real registered capture, not a fake inventory. The test must not use `ProgramGraphsPublicFixture`, hand-installed predecessor payloads, target JSON, generated-output goldens, a live ref, or customer code execution.

### 2. Canonical configuration oracle

Use one committed, test-only canonical configuration oracle. It must contain the exact sorted union of already-published Step 01–05 artifact policies and the finite bytes/values for capture, verification, capability, resource, toolchain, profile, schema, prompt, graph, Flow, and Capsule controls. The IT strict-parses it, loads the policy subdocument through `CanonicalArtifactPolicyRegistry.load`, recomputes every ID/SHA/reference, and builds typed profiles from the same values; placeholders, missing/extra policy keys, unregistered bytes, or typed-value drift fail before analysis. This oracle is configuration only, not a source/graph/result fixture, production default, public schema, or official output.

### 3. Minimum file/type scope

- Docs-only publication first: one new bullet in `docs/analysis-steps/05-business-flows.md` §8.6; add the named Failsafe selector/command to `docs/plans/target-standards-and-toolchain-plan.md` only if the project requires every integration selector to be enumerated there.
- Test implementation: one `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java`; any configuration holder/parser is a private nested test type.
- Test configuration: one canonical oracle under `src/test/resources/analysis/flow/fixed-repository/`; its exact filename may be fixed in the docs-only clarification.
- Production Java/POM/runtime/API/schema/output files: **none** for this acceptance seam.

Do not widen the inventory publishers, implement `RunStoreBootstrap.open`, add `VerifiedSourceInventoryExecutor`, add a generic M1→M3 executor, or start `RepositoryAnalysisAgent`/CLI/HTTP/runtime/recovery work in this slice.

### 4. Exact narrow Step 05 §8.6 paragraph to publish before test writing

> 固定仓库离线验收使用唯一显式 Failsafe selector `FixedRepositoryBusinessFlowsIT`。该测试位于 `org.sourceanalysis.app.analysis.inventory`，可在本 Agent 已 ignore 的 `.workspace` 内使用 `RunStoreBootstrap.openForTest` 的真实 filesystem/force/atomic-move store，但不表示 `RunStoreBootstrap.open`、runtime 或 `RepositoryAnalysisAgent` 已交付。selector 只能将已批准的本地 `https://github.com/jishenghua/jshERP.git` exact commit `8c30ce7861570458920175e200bb2a6442713580` 交给 `LocalGitCommitCaptureAdapter`，再依次执行真实 capture/registry、VerifiedSourceInventory M1→M3、`ApplicationDiscoveryExecutor`、`ProgramGraphsExecution`、persisted ProvenCodeFacts M1→M3 与 BusinessFlows M1→M3，每步 fresh reopen；不得手装任何 source-derived predecessor、使用 `ProgramGraphsPublicFixture`、目标 JSON、客户 build/script/application、network/model 或活动 ref。唯一 test-only canonical configuration oracle 必须固定当前 Step 01–05 已发布 policy 的 exact sorted union 与全部 controls/profile/budget bytes/values；selector 先 strict parse 并重算每个 ID/SHA/reference，再从同一组值建立 typed profiles/`ArtifactControls`，占位 digest、缺/多 policy、未登记 bytes 或 typed-value 漂移均在分析前失败。capture、已安装 upstream/publication artifacts 与 canonical JSON acceptance report 在成功或下游失败时都保留于该 ignored workspace；这是验收证据，不是 recovery 或新的正式输出。通过只证明该 fixed commit 在 test-scoped 真实持久化上的 Step 01→05 denominator/Gap/accounting 闭合；不证明 production runtime readiness，也不替代本节其余 synthetic、mutation 和 domain-specific acceptance。

Suggested explicit command shape after publication and implementation (not run in this task):

~~~text
mvn -o -t .mvn/toolchains.xml -DskipUTs=false -Dit.test=FixedRepositoryBusinessFlowsIT test-compile failsafe:integration-test failsafe:verify
~~~

The POM maps Failsafe `skipITs` to `skipUTs`, so `-DskipUTs=true` would skip this selector and is forbidden. The explicit `test-compile` plus Failsafe goals avoid the Surefire test lifecycle. The docs-first test design must add explicit opt-in/source configuration properties; selecting the IT without them must fail rather than skip, while an ordinary direct-selector run does not select this IT.

## Affected contracts, versions, and consumers

- Existing consumed contracts remain unchanged: capture v1, request/inventory v2, current published discovery/graph/Fact/Flow module and public schema versions, `ArtifactControls`, and typed public references.
- The test-only configuration oracle creates no public schema or runtime input format. It only makes every otherwise opaque test reference correspond to immutable bytes and one typed value source.
- The only new consumer is the opt-in integration test. Existing direct selectors and all production consumers remain unchanged.
- No analysis-step key/order, public API, evidence/Proof/Trace rule, model boundary, cross-step publication identity, artifact count, or output filename changes.

## Authority and blockers

- **No new user-level invariant authority is required** for this acceptance seam. The narrow test-contract clarification is within Sol/ultra's recorded authority and must pass the existing docs-only publication gate before Luna writes the IT.
- If anyone instead proposes a production default policy/profile schema, a public inventory executor, a change to `RepositoryAnalysisAgent`, or a different cross-step/publication identity, that is outside this ruling and must be reclassified under the scoped authority rules.
- The missing production `RunStoreBootstrap.open` and public runtime remain real later delivery gaps; they do not block this IT and must not be reported as completed by it.
- The unresolved Step 05 domain-specific classification criterion remains a separate closeout decision. The IT neither assumes its resolution nor weakens §8.6; all unaffected Step 05 entry/Flow/Capsule/ownership/accounting work remains mandatory.

## Verification

| Check | Result | Key output |
| --- | --- | --- |
| Instruction/skill inspection | PASS | One owned progress file; bounded read-only ruling; no Git/Maven/customer execution. |
| Contract inspection | PASS | Foundation explicitly makes `openForTest` a real-filesystem seam and separates runtime delivery; Step 01 records real test assembly and later product entry separately. |
| Code seam inspection | PASS | Real capture/registry and public M2 exist; package-local M1/M3 are directly accessible to an inventory-package IT; downstream public/lower-level production composition exists. |
| Existing test evidence | PASS (read-only) | Current real-capture inventory test demonstrates capture→M1→M2→M3/fresh-reopen mechanics; its synthetic source and placeholder controls are not reusable as fixed-repository acceptance inputs. |
| Maven / Java / fixed repository | NOT RUN | Explicitly prohibited for this ruling; no PASS claim. |

## Decisions

- Correct the initial ruling: `openForTest` is permitted and preferred for this opt-in offline acceptance because the published contract says it is real filesystem infrastructure, not a fake store.
- Use a test-scoped real-capture composition; do not build production bootstrap/runtime merely to run Step 05 acceptance.
- Keep every source-derived artifact real and freshly reopened. Only deterministic configuration bytes are test resources, and their identities must be recomputed and bound before analysis.
- Retain real capture/upstream/publication artifacts and the canonical JSON acceptance report below this Agent's ignored `.workspace` on both success and downstream failure; do not let default temporary-directory cleanup erase the only real-repository diagnostics.
- Keep the missing production runtime truth visible: the IT proves the fixed offline analysis path only.

## Blockers

- No execution-seam or user-authority blocker remains after the narrow docs-first test clarification is published.
- Full Step 05 delivery still cannot be claimed until its separately unresolved domain-specific criterion and all other §8.6 requirements are resolved and verified; this ruling does not decide that issue.

## Exact next action

- Root publishes only the narrow Step 05 §8.6 clarification (and named Failsafe command mapping if needed), then Luna may add the single opt-in IT and its canonical configuration oracle. No production main/runtime/API implementation is part of that slice.

## Resume checks

- Do not reopen this completed ruling unless the acceptance seam, not its implementation result, changes.
- Do not infer domain-classification approval.
- Do not treat a missing/skip property, a synthetic source, a hand-installed predecessor, or a placeholder digest as fixed-repository acceptance.
- Do not claim `RunStoreBootstrap.open`, runtime, CLI, HTTP, or `RepositoryAnalysisAgent` readiness from the IT.
