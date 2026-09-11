# Progress: Five-step run-prefix audit

- Status: COMPLETE
- Agent role: Sol/ultra architecture auditor for semantic-framework delivery Batch 1
- Model: GPT-5 (delegated architecture audit role)
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Read-only audit of production execution/publication seams for analysis Steps 01–05 and the smallest run-prefix composition. This file is the audit's only write.
- Working-tree note: Findings describe the shared dirty tree as inspected. In particular, the in-flight production-store change now makes `RunStoreBootstrap.open(Path)` delegate to `FileSystemRunStoreHandle.openExistingDirectory(Path)`; its root-resolution rule still needs the narrow ruling below.

## Result

The Steps 01–05 algorithms and canonical publishers already form a usable bounded pipeline. Step 02 and Step 03 each have a public full-step executor. Step 04 and Step 05 can be composed entirely from existing public production readers/builders/publishers. Step 01 is the only package-access hole: its three canonical publishers/specifier are package-private, so a runtime package cannot execute the complete step through an existing public facade.

The smallest coherent Batch-1 addition is therefore one package-internal `runtime` run-prefix coordinator plus one thin inventory execution facade. It must construct both filesystem stores from the **same** production `RunStoreHandle`, retain all five typed step references, and fresh-reopen each reference before handing it onward. It must not copy the current integration test's temporary store, in-memory input map, configuration path, or capture path into production.

Legend below: **VERIFIED** means observed in current production source and its direct test/fixture. **PROPOSED** is the smallest implementation shape inferred from those contracts. **RULING** resolves a contract ambiguity or conflict.

## 1. Existing production seams by step

| Step | Existing execution and persistence seam | Exact result | Audit finding |
| --- | --- | --- | --- |
| 01 `verified-source-inventory` | **VERIFIED:** `FrozenRequestAdmission.admit(byte[], CaptureReceiptView, ProfileView)` and `VerifiedSourceIndexer.index(ModulePublicationReference, CanonicalModuleArtifactStore, LocalGitSourceRegistry)` are public computation seams. Canonical persistence is implemented by `AdmittedSourceRequestModulePublisher.publish(AdmittedSourceRequestPublicationInput)`, `VerifiedSourceIndexModulePublisher.publish(ModulePublicationReference, LocalGitSourceRegistry)`, and `VerifiedSourceInventoryPublicationSpecifier.publish(VerifiedSourceInventoryPublicationSpecificationInputV1)`, but all three classes/methods are package-private. | `VerifiedSourceInventoryReference`, wrapping the complete `AnalysisStepPublicationReference`. | **No existing public full-step execution/persistence seam.** Add one thin inventory facade; making all three internals public would be a wider and shallower API. M3 also requires the exact `analysis-run-request-v2` and `frozen-repository-request-v2` bytes through package-private `AnalysisInputArtifactReader`—not caller bytes carried from M1. |
| 02 `application-discovery` | **VERIFIED:** `new ApplicationDiscoveryExecutor(VerifiedSourceTextReader, CanonicalModuleArtifactStore, CanonicalAnalysisStepArtifactStore).execute(ApplicationDiscoveryRequest)` runs and persists M1–M4. `ApplicationDiscoveryRequest` is exactly `(AnalysisStepPublicationAddress destination, VerifiedSourceInventoryReference verifiedSourceInventory, DiscoveryProfile discoveryProfile)`. | `ApplicationDiscoveryReference`. | Complete public step seam. `PersistedVerifiedSourceTextReader(CanonicalAnalysisStepArtifactStore, LocalGitSourceRegistry)` is the production source-byte adapter. |
| 03 `program-graphs` | **VERIFIED:** `new ProgramGraphsExecution(VerifiedSourceTextReader, CanonicalModuleArtifactStore, CanonicalAnalysisStepArtifactStore).execute(VerifiedSourceInventoryReference, ApplicationDiscoveryReference, ArtifactReference graphProfileRef, ArtifactControls controls)` runs and persists M1–M6 with predecessor reopens. | `ProgramGraphsReference`. | Complete public step seam. Do not invoke individual graph builders from the run coordinator. |
| 04 `proven-code-facts` | **VERIFIED public chain:** `PersistedFactCandidateInputReader.reopen(source, discovery, graphs)` → `FactCandidateEnumerator.enumerate(inputs, FactRegistry.standardJavaFacts())` → `FactCandidateSetModulePublisher.publish(PROVEN_CODE_FACTS/01-candidates, inputs, candidates)` → `PersistedFactCandidateSetReader.reopen(candidateRef, freshlyReopenedInputs, FactRegistry.standardJavaFacts())` → `AtomicProofBuilder.prove(reopenedCandidates, freshlyReopenedInputs, source, ProofRuleRegistry.standardJavaBoundary())` → `ProofDecisionSetModulePublisher.publish(PROVEN_CODE_FACTS/02-proofs, inputs, candidateRef, decisions)` → `FactLedgerPublicationSpecifier.specifyCandidatesAndProofs(freshlyReopenedInputs, candidateRef, proofRef, source, discovery, graphs)`. | `ProvenCodeFactsReference`. | No full-step executor, but every required production seam is public. The explicit `PersistedFactCandidateSetReader` hop is mandatory in the coordinator; proving the enumerator's original Java object, as the broad integration fixture currently does, is an in-memory module handoff. Reopen `FactCandidateInputs` again for M2/M3 as well. |
| 05 `business-flows` | **VERIFIED public chain:** `EntryRootedFlowCompiler.compile(discovery, graphs, facts, FlowCompilationProfile)` → `FlowCompilationModulePublisher.publish(discovery, graphs, facts, compilation)` → `EvidenceCapsuleProjector.project(flowCompilationRef, source, graphs, facts, CapsuleProjectionProfile)` → `CapsuleProjectionModulePublisher.publish(flowCompilationRef, source, graphs, facts, projection)` → `FlowPublicationSpecifier.specify(source, discovery, graphs, facts, flowCompilationRef, capsuleProjectionRef)`. | `BusinessFlowsReference`. | No full-step executor, but every required production seam is public. M1 publisher rebuilds from persisted Steps 02–04; the M2 projector reads persisted M1; the M2 publisher reprojects; M3 reopens both module publications and Steps 01–04. Do not bypass those replay checks. |

Direct type/sequence checks were made against `VerifiedSourceInventoryPublicationSpecifierTest`, `ApplicationDiscoveryExecutionTest`, `ProgramGraphsExecutionTest`, `AtomicProofBuilderTest`, `ProvenCodeFactsPublicationSpecifierTest`, flow compiler/capsule/publication tests, and `FixedRepositoryBusinessFlowsIT`. The last test is useful only as a sequence oracle: it uses `openForTest`, a test-created workspace/configuration, and `InputArtifacts::reopen` over a Java `Map`, so it is not a production run-core seam.

## 2. Exact persisted handoffs

Every typed result above wraps the full four-part `AnalysisStepPublicationReference(address, analysisStepArtifactRoot, analysisStepReceiptId, analysisStepReceiptSha256)`. Passing a semantic payload reference, a directory, an `Installed*` result, or the builder's Java object is not an equivalent handoff.

| Next consumer | Exact predecessor references it must receive and fresh-reopen | Non-handoff controls/configuration |
| --- | --- | --- |
| Step 02 | Step 01 `VerifiedSourceInventoryReference` | `DiscoveryProfile` (currently `DiscoveryProfile.standard()`) |
| Step 03 | Step 01 `VerifiedSourceInventoryReference` + Step 02 `ApplicationDiscoveryReference` | full `graphProfileRef` `ArtifactReference` + exact `ArtifactControls` |
| Step 04 M1/M2/M3 | Step 01 + Step 02 + Step 03 typed references; M2 additionally reopens the persisted M1 `ModulePublicationReference`; M3 reopens persisted M1 and M2 module references | `FactRegistry.standardJavaFacts()` and `ProofRuleRegistry.standardJavaBoundary()`; do not expose an arbitrary registry because M3 independently replays M1 with `standardJavaFacts()` |
| Step 05 M1 | Step 02 + Step 03 + Step 04 typed references | `FlowCompilationProfile` |
| Step 05 M2/M3 | Step 01 + Step 03 + Step 04 typed references plus persisted M1/M2 module references; M3 also reopens Step 02 | `CapsuleProjectionProfile` |
| Target Step 06 M1/M6 after this prefix | **All five** typed references: inventory, discovery, graphs, facts, business flows | Step 06 is out of this checkpoint; return the references without invoking interpretation |

After each completed step, the coordinator must call `CanonicalAnalysisStepArtifactStore.reopen(result.publication())` and verify reference equality, semantic key, run ID, controls, receipt status, and gap references. The next call receives only the typed reference(s), never the just-built semantic payloads.

## 3. Smallest run-prefix orchestration seam

**PROPOSED (name illustrative, contract substantive):** a package-internal `org.sourceanalysis.app.runtime.RunCorePrefix` with one `execute(RunCorePrefixRequest)` method and one immutable result containing the five typed references. It is an internal precursor to the future sole product interface `RepositoryAnalysisAgent`; it must not become a second public product API, create a root manifest, or assign an `AnalysisResult` before Step 08.

Construction dependencies must be explicit and path-free after bootstrap:

- one already-open production `RunStoreHandle` from `RunStoreBootstrap.open(configuredStoreRoot)`; the caller owns its lifetime;
- one `CanonicalJsonCodec`, `CanonicalArtifactPolicyRegistry`, and `ArtifactStoreLimits`;
- one `LocalGitSourceRegistry` (its private capture root is configured outside the request and is never exposed);
- the design's composition-only, reference-addressed analysis-input reader for exact run/frozen request bytes;
- one already validated closed run-prefix profile/configuration supplying discovery, graph, Fact/proof, flow, capsule, and admission limits.

`RunCorePrefix` constructs exactly one `FileSystemCanonicalModuleArtifactStore` and one `FileSystemCanonicalAnalysisStepArtifactStore` from that same handle, then one shared `PersistedVerifiedSourceTextReader`. Accepting two independently supplied store interfaces would not let the coordinator prove that they share a run root.

One thin `VerifiedSourceInventoryExecution`/`SourceFreezer` facade in `analysis.inventory` is mechanically required so the runtime package can invoke the package-private M1→M2→M3 publishers. Its narrow input should follow the designed internal `SourceFreezeRequest{runId, analysisRunRequestRef}` and its injected readers/resolvers must derive the frozen request, capture view, profile view, exact eight M1 upstream references, and M3 input bytes from content-addressed inputs. Do not expose the three publishers or their private parser types individually.

The required call order is:

1. inventory facade → fresh-reopen Step 01;
2. `ApplicationDiscoveryExecutor` → fresh-reopen Step 02;
3. `ProgramGraphsExecution` → fresh-reopen Step 03;
4. the Step 04 public chain above, with explicit M1 candidate and input reopens → fresh-reopen Step 04;
5. the Step 05 public chain above → fresh-reopen Step 05;
6. return all five typed references and the persisted gap summary; invoke nothing in Step 06.

**Verified missing composition dependency:** current production has only the package-private `AnalysisInputArtifactReader` contract, not a durable production implementation, and no production profile-bundle resolver. The Step 01 fixture supplies a `Map`; the fixed-repository fixture loads a path-based test config. Batch-1 code cannot claim a production run prefix until reference-addressed implementations are supplied or injected. Per the Step 01 design, the input reader is composition-only and is **not** a fourth canonical publication store.

## 4. Paths that must not participate

- The entire current R0/finite-key Step 06 route under `org.sourceanalysis.app.analysis.interpretation` is outside the prefix and outside the approved target wire. In particular do not call `RegistryProposalTaskCompiler`, `RegistryProposalRunner`, `RepositoryInterpretationRegistryFreezer`, `RepositoryInterpretationRegistryModulePublisher`, `FiniteKeyFlowTaskCompiler`, `InterpretationRunner`, their task/execution publishers, `CrossFlowCandidateCompiler`, `BusinessProcessTaskCompiler`, or `BusinessProcessInterpretationRunner`.
- Do not reuse legacy `ProcessInputArtifactReader` as the missing Step 01 reader; it belongs to the superseded process-interpretation route.
- Do not call `FactRegistry.standardJavaBoundary()` in new code. It is an explicitly documented internal-v0 alias. `FactRegistry.standardJavaFacts()` is the valid Step 04 technical taxonomy and is **not** the removed semantic R0 registry. `ProofRuleRegistry.standardJavaBoundary()` remains the current valid v3 proof-rule API.
- Do not use `RunStoreBootstrap.openForTest`, `Files.createTempDirectory`, the `FixedRepositoryBusinessFlowsIT` workspace/configuration helpers, `InputArtifacts::reopen`, test fixtures, source capture, Provider/model code, directory scans, or active-worktree paths.
- Do not read old `com.linguan.codemd`, history-only design, 57-output topology, old Step 06 schemas/files, aliases, translators, dual readers/writers, or fallback discovery.

## 5. Narrow rulings and genuine conflicts

### RULING A — production store root and macOS `/var`

The persistence contract requires `RunStoreBootstrap.open(Path)` to be the only production Path seam and requires NOFOLLOW validation for installed descendants. It does **not** require rejection of every lexical ancestor alias. The current in-flight `FileSystemRunStoreHandle.openExistingDirectory` stores `toAbsolutePath().normalize()` and rejects any symlink in that lexical ancestor chain; on macOS this rejects legitimate JUnit roots under `/var`, because `/var` resolves to `/private/var`.

Approved narrow rule:

1. Inspect the caller-selected **final component** with NOFOLLOW and reject it if it is a symlink, absent, or not a directory.
2. Resolve the accepted directory once with default `Path.toRealPath()` (not `toRealPath(NOFOLLOW_LINKS)`), yielding the canonical physical root. This intentionally follows parent aliases such as `/var -> /private/var`.
3. Retain only that resolved root in `FileSystemRunStoreHandle`; never retain or reuse the original lexical alias for a write or reopen.
4. Before store use, revalidate the stored real root/real ancestor chain and let the existing publication engines continue their per-directory/per-file NOFOLLOW checks. If the canonical path is later replaced/retargeted, fail closed before writing.

This also safely permits a caller path containing an arbitrary ancestor alias: retargeting that alias after `open` cannot redirect the handle because the alias is no longer retained. Any test expecting blanket ancestor-alias rejection encodes the wrong lifecycle. The in-flight `RunStoreBootstrapProductionTest.retainsTheRealRootWhenAnAncestorAliasIsRetargetedBeforeInstall` now expresses the correct first two checks: accept and pin an ancestor alias, and reject a final-component symlink. Retain an additional fail-closed check for later retarget/replacement of the stored canonical root chain. The ordinary production open/reopen test must also pass when its JUnit temp root is lexically under `/var`.

### RULING B — Gap is a handoff, fatal is a stop

Detailed Steps 01–05 explicitly define `SUCCEEDED_WITH_GAPS` as an installed, downstream-consumable publication; Step 05 even permits 0 Flow/0 Capsule with gaps so target Step 06 can attempt safe noFlow material. Therefore Batch 1's phrase “stops on Gap/fatal” must not be implemented as “abort at the first `SUCCEEDED_WITH_GAPS`.” The coherent reading is: fatal stops immediately and preserves prior publications; gaps remain typed/accounted inputs, execution continues through Step 05, and the prefix reports a Gap outcome rather than COMPLETE. Any intended early-abort rule would change cross-step publication semantics and needs user approval before code.

### CONFLICT C — Step 01 bounded-scope receipt does not match its design

The Step 01 design requires `BOUNDED_PATH_SET` to carry a fixed completion-ineligible Gap and `SUCCEEDED_WITH_GAPS`. Current `AdmittedSourceRequestModulePublisher`, `VerifiedSourceIndexModulePublisher`, and `VerifiedSourceInventoryPublicationSpecifier` hard-code `SUCCEEDED` with empty gap lists even though the payload records `repositoryCompletionEligible=false`. A receipt-driven prefix therefore cannot honestly classify bounded scope. The authoritative design should win: correct Step 01 gap/status propagation in its own RED/GREEN work unit before a general bounded-scope prefix is accepted. Until then, a Batch-1 prefix may only claim the complete-capture path; it must not invent a runtime-only semantic-field fallback.

No Step 01–05 output schema, artifact count, module key, identity formula, or Step 06 compatibility bridge is required for the orchestration seam itself.

## Verification performed

- Read `AGENTS.md`, `docs/DESIGN.md`, the detailed Step 01–05 designs, the Batch-1 plan, and the canonical persistence contract.
- Inspected the current production artifact bootstrap/handle/stores and the inventory, discovery, graph, fact, and flow execution/publication code named above.
- Inspected direct tests/fixtures only to verify callable types, persisted-reopen sequence, and the differences between test composition and production composition.
- Re-read the working-tree diffs for the in-flight production bootstrap implementation before issuing Ruling A.
- Did **not** run Maven, source capture, customer code, model/Provider code, or network access.

## Changed files

- `progress/five-step-run-prefix-audit.md` only (this agent's sole write).
