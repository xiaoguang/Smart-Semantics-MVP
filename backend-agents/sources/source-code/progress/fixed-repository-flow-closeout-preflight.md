# Progress: fixed repository flow closeout preflight

- Status: BLOCKED
- Agent role: Terra/xhigh read-only closeout execution mapping
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Read-only preparation for the authorized fixed local jshERP commit through Capture, inventory, discovery, ProgramGraphs, ProvenCodeFacts, and BusinessFlows. Identify existing production seams, test-only assembly, minimal wiring, immutable-reader/policy/bootstrap availability, and any missing seam. No Java, test, design, Maven, customer-source scan, runtime/CLI, Provider, network, or Git change.
- Approved inputs: Current production execution/step contracts and the historical original-prototype location `github-code/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580`; root has verified only the commit object exists.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record from `progress/TEMPLATE.md` before inspection.
- Read the current production capture, source-registry, inventory, discovery, ProgramGraphs, Fact, Flow, artifact-store, policy-registry, and profile seams; did not open, enumerate, or execute the historical jshERP capture.
- Confirmed that `LocalGitCommitCaptureAdapter` and `LocalGitSourceRegistry` are real production seams: capture accepts an absolute local Git repository, exact lowercase SHA-1, capture-policy reference, and resource-budget reference; the registry later fresh-reopens the content-addressed registration and opaque snapshot bytes.
- Confirmed that `ApplicationDiscoveryExecutor` and `ProgramGraphsExecution` are real public whole-step executors once given a published `VerifiedSourceInventoryReference`, a `PersistedVerifiedSourceTextReader`, canonical module/step stores, matching controls, and their immutable profile references.
- Confirmed that Fact and Flow production components are public, fresh-reopen based, but are lower-level compositions rather than a whole-step runner: Facts require the persisted input reader → candidate enumerator/standard registry → M1 publisher → proof builder/standard rule registry → M2 publisher → ledger specifier; BusinessFlows requires M1 compiler/publisher, M2 projector/publisher, then M3 specifier.
- Identified the test-only substitute path: `RunStoreBootstrap.openForTest`, `ProgramGraphsPublicFixture`, and test helpers manually construct a policy registry, synthetic inventory/discovery payloads, fake verified source documents, and predecessor public references. They cannot be used for the fixed repository acceptance.

## Current state

- Mapping only; no source capture, customer scan, executable invocation, or production/test edit has occurred. The requested end-to-end fixed-repository acceptance cannot start from currently exposed production seams because the capture-to-inventory/bootstrap boundary is absent.

## Changed files

- `progress/fixed-repository-flow-closeout-preflight.md`

## Execution map

| Stage | Existing production seam | Minimal eventual input/output wiring | Preflight result |
| --- | --- | --- | --- |
| Local immutable capture | `LocalGitCommitCaptureAdapter.capture(LocalGitCaptureRequest)` | Historical local Git directory + exact `8c30ce7861570458920175e200bb2a6442713580` + declared repository identity + pre-registered capture-policy/resource-budget references → `SourceRegistrationReference`; `LocalGitSourceRegistry` can later reopen it. | Capture adapter and registered-input reader exist; input references must already be real immutable artifacts. |
| Store/policy bootstrap | `CanonicalArtifactPolicyRegistry.load(...)`, filesystem stores | Canonical policy bytes → policy registry/reference; production `RunStoreHandle` + limits + controls → module and analysis-step stores. | **Missing:** `RunStoreBootstrap.open(configuredStoreRoot)` always throws `UnsupportedOperationException`; only `openForTest` creates the opaque handle needed by public filesystem-store constructors. No production policy/controls/bootstrap composition is exposed. |
| Verified source inventory | `FrozenRequestAdmission`, `VerifiedSourceIndexer`, `PersistedVerifiedSourceTextReader` | Canonical run request + a capture receipt view + profile view → admitted M1; persisted M1 → index M2; fresh-reopened M1/M2 → inventory M3 → `VerifiedSourceInventoryReference`; later `PersistedVerifiedSourceTextReader(stepStore, sourceRegistry)` returns verified text. | **Missing:** no production bridge constructs `CaptureReceiptView` from a `SourceRegistrationReference`/`LocalGitSourceRegistry`; M1 and M3 inventory publishers/specifier are package-private, and no public inventory executor composes them. |
| Application discovery | `ApplicationDiscoveryExecutor.execute(ApplicationDiscoveryRequest)` | Published inventory + persisted verified-text reader + module/step stores + `DiscoveryProfile.standard()` + discovery destination → `ApplicationDiscoveryReference`. | Executable after inventory/bootstrap exists. |
| Program graphs | `ProgramGraphsExecution.execute(...)` | Inventory + discovery + same verified-text reader/stores + immutable graph-profile reference + matching controls → `ProgramGraphsReference`. | Executable after inventory/bootstrap exists; it performs its own M1–M6 fresh-reopen chain. |
| Proven code facts | Public `PersistedFactCandidateInputReader`, `FactCandidateEnumerator`, `FactCandidateSetModulePublisher`, `AtomicProofBuilder`, `ProofDecisionSetModulePublisher`, `FactLedgerPublicationSpecifier` | Fresh-reopen inventory/discovery/graphs → Fact inputs → standard v3 Fact/rule registries, two module publications at ProvenCodeFacts M1/M2 addresses, then M3 public ledger → `ProvenCodeFactsReference`. | All required primitives are public, but no single production Fact executor supplies destinations/profiles or performs the composition. |
| BusinessFlows closeout | Public compiler/publisher, projector/publisher, and `FlowPublicationSpecifier` | Fresh-reopened discovery/graphs/facts + immutable Flow profile → M1 compilation/publication; source/graphs/facts + Capsule profile → M2 projection/publication; exact six predecessors → M3 `BusinessFlowsReference`. | All component seams are public and fresh-reopen inputs; use only after the ongoing Step05 v3/v6/v3/v4 cutover is complete and real profiles/controls/bootstrap exist. |

## Available immutable readers and constructors

- `LocalGitSourceRegistry` fresh-reopens a captured registration/snapshot without exposing a worktree.
- `PersistedVerifiedSourceTextReader` is the required inventory-to-parser reader; it requires a real published inventory, step store, and source registry.
- `ApplicationDiscoveryExecutor`, `ProgramGraphsExecution`, `PersistedFactCandidateInputReader`, and `PersistedFlowCompilationInputReader` all consume typed persisted predecessor references and validate their artifact/control lineage.
- `CanonicalArtifactPolicyRegistry.load(...)` validates a supplied canonical immutable registry document, but does not create or register one. Flow and graph profiles are opaque `ArtifactReference` inputs; no production profile registrar/default value was found.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| None | Not run | Read-only preparation; Maven and customer-source scan are explicitly out of scope. |
| Production-seam search/read | PASS (read-only) | Found capture, reader, discovery, graph, Fact, and Flow seams; no `RepositoryAnalysisAgent`, generic run executor, or production inventory executor/bootstrapping path in `src/main/java`. |

## Decisions

- Use only real public production seams and immutable registered-input readers for the eventual fixed local acceptance. Test fixtures may identify missing wiring but must not substitute for a repository execution.
- The eventual smallest real sequence is capture → production bootstrap/policy → capture-to-inventory admission/publication → discovery executor → ProgramGraphs executor → public Fact composition → M1/M2/M3 Flow composition. The first three links are not currently runnable from public production seams.
- This is a missing execution seam, not authorization to use `openForTest`, synthesize policy/profile references, manually install fake predecessor payloads, or add a runtime/CLI/harness in this work unit.

## Blockers

- Execution is not authorized in this preparation. The current hard blocker is the absence of a production store/policy bootstrap and capture-registration-to-inventory public handoff; inventory also lacks a public M1–M3 executor. These must be explicitly designed and authorized before a fixed-repository acceptance can begin.

## Exact next action

- HOLD. Await root/design direction on the missing production bootstrap and capture-to-inventory/inventory-executor seams; do not substitute test fixtures or implement a harness.

## Resume checks

- Do not scan or execute the historical jshERP source tree, run Maven, invoke a Provider/network/customer build, add a harness/CLI/runtime path, or modify source/tests/design/Git state.
