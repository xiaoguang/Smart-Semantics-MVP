# Source Code Analysis Naming and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Current maturity:** The Wire Reset is complete and several bounded deterministic/scripted vertical slices exist, but there is no complete run. BusinessFlows lacks process signals; FlowInterpretation has only local M1–M5; cross-Flow M6–M9, process knowledge, and process-first documentation remain target work.

**Goal:** Replace the current prototype-shaped source-code analyzer with one coherently named, fail-closed Source Code Analysis Agent that implements the approved eight-analysis-step contract without compatibility readers.

**Architecture:** The target is one Java 17 Maven application under `backend-agents/sources/source-code/`, with the public seam at `org.sourceanalysis.app` and eight semantic analysis packages under `org.sourceanalysis.app.analysis`. Canonical module, analysis-step, run, validation, and adapter seams are cross-cutting packages with narrow responsibilities; persisted handoffs use `steps/<ordered-semantic-key>/...` and never infer an input from a directory scan.

**Tech Stack:** Java 17; Maven; Jackson 2; isolated Git CLI; JavaParser Symbol Solver; Maven Model; Tomlj; networknt JSON Schema Validator; Picocli; JUnit, AssertJ, jqwik, ArchUnit, and Awaitility; the quality tools frozen in `docs/plans/target-standards-and-toolchain-plan.md`.

**Spec:** `docs/DESIGN.md` and `docs/analysis-steps/01-verified-source-inventory.md` through `docs/analysis-steps/08-nine-section-document.md`.

## Global Constraints

- The design-publication gate is mandatory: publish a coherent docs-only commit to `origin/main` before any POM, Java, test, schema, fixture, configuration, or behavior change begins.
- Target repository directory: `backend-agents/sources/source-code/`.
- Maven coordinate: `org.sourceanalysis:source-code-analysis-agent`; display name: `Source Code Analysis Agent`.
- Java root: `org.sourceanalysis.app`.
- Analysis packages are exactly `.analysis.inventory`, `.analysis.discovery`, `.analysis.graph`, `.analysis.fact`, `.analysis.flow`, `.analysis.interpretation`, `.analysis.knowledge`, and `.analysis.document`.
- Cross-cutting roots are exactly `org.sourceanalysis.app.capture.localgit`, `org.sourceanalysis.app.artifact`, `org.sourceanalysis.app.evidence`, `org.sourceanalysis.app.runtime`, `org.sourceanalysis.app.validation`, `org.sourceanalysis.app.adapter.cli`, `org.sourceanalysis.app.adapter.http`, and `org.sourceanalysis.app.adapter.provider`.
- A future database adapter, if separately authorized, uses `org.sourceanalysis.db.analysis`; this plan documents the reserved root but does not create database code.
- Do not create `target`, `mvp`, numbered package names, `common`, `shared`, `misc`, `utils`, `codemd`, `github`, or `linguan` in a target package or public wire name.
- Numerical ordering appears only in documentation filenames and runtime paths such as `steps/03-program-graphs/`; Java types, package names, fields, schema names, IDs, and commands use semantic names.
- This is a full Wire Reset. Do not add a dual reader, fallback reader, migration reader, alias, bridge, symlink, old-to-new translator, or dual writer. Old directories, packages, artifact types, schema versions, receipts, and run stores fail closed.
- Preserve the official cardinality: 47 semantic analysis-step payloads, eight analysis-step receipts, one nine-section archive manifest, and one root run manifest: **57** formal run outputs.
- Preserve the fixed jshERP scope and commit `8c30ce7861570458920175e200bb2a6442713580`. Do not run customer Maven, plugins, tests, scripts, or application code.
- A Provider call that starts is never retried or switched automatically. Same-run process recovery, worker takeover, model-call recovery, and terminal repair remain out of scope.
- Implementation and automated verification use frozen fixtures and scripted Provider fakes only. This plan authorizes no live LLM/product-content generation, source capture, network source access, candidate generation, freeze, package, deployment, or runtime activation.
- R0/R1/R2 remain single-Flow. P1/P2 are the sole bounded multi-Flow model exception; their explicit synthetic replenishment-to-settlement fixture is never jshERP evidence. All external effects remain unproven without dedicated Proof.
- Sol/ultra owns design; Luna/xhigh owns RED, bounded reads/rounds/review/reader slots; Terra/xhigh implements only after design+RED; Sol/xhigh only debugs root causes. Contract/schema uncertainty returns to Sol/ultra; eight-step/nine-section/identity/57-count/model-boundary changes stop for user confirmation.
- Run only tests added by or directly covering the current work unit. Maven-heavy commands are serial.

---

## Target name and wire registry

| Analysis step | Java package | Runtime directory | Receipt filename |
| --- | --- | --- | --- |
| Verified source inventory | `org.sourceanalysis.app.analysis.inventory` | `steps/01-verified-source-inventory/` | `verified-source-inventory-receipt.json` |
| Application discovery | `org.sourceanalysis.app.analysis.discovery` | `steps/02-application-discovery/` | `application-discovery-receipt.json` |
| Program graphs | `org.sourceanalysis.app.analysis.graph` | `steps/03-program-graphs/` | `program-graphs-receipt.json` |
| Proven code facts | `org.sourceanalysis.app.analysis.fact` | `steps/04-proven-code-facts/` | `proven-code-facts-receipt.json` |
| Business flows | `org.sourceanalysis.app.analysis.flow` | `steps/05-business-flows/` | `business-flows-receipt.json` |
| Flow interpretation | `org.sourceanalysis.app.analysis.interpretation` | `steps/06-flow-interpretation/` | `flow-interpretation-receipt.json` |
| Repository knowledge | `org.sourceanalysis.app.analysis.knowledge` | `steps/07-repository-knowledge/` | `repository-knowledge-receipt.json` |
| Nine-section document | `org.sourceanalysis.app.analysis.document` | `steps/08-nine-section-document/` | `nine-section-document-receipt.json` |

The semantic keys in this table are closed. The two-digit prefixes order documentation and on-disk directories only; they are not stored as `stepNumber`, embedded in Java type names, or accepted as alternate keys.

## Official output inventory

| Analysis step | Semantic payloads | Count |
| --- | --- | ---: |
| Verified source inventory | `source-input.json`, `verified-snapshot.json`, `source-inventory.jsonl` | 3 |
| Application discovery | `application-profile.json`, `entry-points.jsonl`, `mapper-catalog.jsonl`, `capability-report.json` | 4 |
| Program graphs | `code-structure-graph.json`, `call-graph.json`, `control-flow-graph.json`, `data-flow-graph.json`, `evidence-graph.json`, `graph-index.json`, `graph-gaps.jsonl` | 7 |
| Proven code facts | `proven-facts.json`, `proof-pack.json`, `gap-ledger.json`, `fact-accounting.json` | 4 |
| Business flows | `flow-slices.json`, `flow-coverage.json`, `entry-dispositions.jsonl`, `evidence-capsules.jsonl`, `flow-gaps.jsonl` | 5 |
| Flow interpretation | `registry-proposal-tasks.jsonl`, `registry-proposal-rounds.jsonl`, `registry-proposal-dispositions.jsonl`, `repository-interpretation-registry.json`, `flow-model-tasks.jsonl`, `model-rounds.jsonl`, `generation-receipts.jsonl`, `interpretation-candidates.jsonl`, `flow-interpretation-dispositions.jsonl`, `process-evidence-groups.jsonl`, `process-model-tasks.jsonl`, `process-model-rounds.jsonl`, `business-process-hypotheses.jsonl`, `process-interpretation-dispositions.jsonl` | 14 |
| Repository knowledge | `knowledge-admission-decisions.jsonl`, `repository-business-knowledge.json`, `knowledge-conflicts.jsonl`, `knowledge-accounting.json`, `merged-gaps.json` | 5 |
| Nine-section document | `nine-section-plan.json`, `document.md`, `trace.jsonl`, `candidate.json`, `validation-baseline.json` | 5 |

Each row also installs one semantic receipt. The document step additionally installs its archive manifest and the run installs one root manifest. Module/validation artifacts do not enter the 57-output count.

### Task 1: Publish the naming design

**Files:**
- Modify: `backend-agents/AGENTS.md`
- Modify: `backend-agents/sources/github-code/AGENTS.md` (moves with the directory during Task 2)
- Modify: `backend-agents/sources/github-code/README.md`
- Modify: `backend-agents/sources/github-code/docs/DESIGN.md`
- Create: `backend-agents/sources/github-code/docs/analysis-steps/01-verified-source-inventory.md` through `08-nine-section-document.md`
- Create: `backend-agents/sources/github-code/docs/plans/source-analysis-naming-and-delivery-plan.md`
- Preserve: `backend-agents/sources/github-code/progress/*.md`

**Interfaces:**
- Consumes: approved naming decision and current source-agent contracts.
- Produces: the only target naming/package/wire contract that later tasks may implement.

- [ ] **Step 1: Run documentation path and relative-link checks.**

  Resolve every local Markdown link from its containing file and require every target to exist.

- [ ] **Step 2: Run the stale target-name scan.**

  Search active design, analysis-step, README, AGENTS, plan, and supplement documents for future uses of `github-code`, `com.linguan.codemd`, `/target/`, `/mvp/`, numbered Java packages/types, `common`, `misc`, and `utils`. Permit old terms only inside explicitly marked current-maturity or history-only text.

- [ ] **Step 3: Verify scope and cardinality.**

  Confirm the fixed jshERP commit, eight analysis-step documents, 42 semantic payloads, eight receipts, one archive manifest, one root manifest, design-publication gate, and no-same-run-auto-recovery rule remain explicit.

- [ ] **Step 4: Commit and publish documentation only.**

  Add only the docs and task-owned progress file, run `git diff --cached --check`, commit with a documentation message, fast-forward push, and record the published commit SHA before Task 2 starts.

### Task 2: Perform the full Wire Reset

**Files:**
- Move: `backend-agents/sources/github-code/` → `backend-agents/sources/source-code/`
- Modify: `backend-agents/sources/source-code/pom.xml`
- Delete: `backend-agents/sources/source-code/src/main/java/com/linguan/codemd/`
- Delete: `backend-agents/sources/source-code/src/test/java/com/linguan/codemd/`
- Delete or regenerate: pre-reset target fixtures/schemas under `backend-agents/sources/source-code/src/test/resources/target/`
- Create: `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/`
- Create: `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/`

**Interfaces:**
- Consumes: the published docs SHA from Task 1.
- Produces: a clean target project that cannot compile against or read the pre-reset wire.

- [ ] **Step 1: Write the architecture RED.**

  Create `src/test/java/org/sourceanalysis/app/SourceAnalysisArchitectureTest.java`. Assert the Maven coordinate/display name, allowed package roots, semantic analysis packages, dependency direction, and absence of the forbidden target names.

- [ ] **Step 2: Prove the RED is caused by the pre-reset tree.**

  Run only `SourceAnalysisArchitectureTest`; expect failure naming the old coordinate/package/directory vocabulary.

- [ ] **Step 3: Move the source-agent directory and replace Maven identity.**

  Change the coordinate to `org.sourceanalysis:source-code-analysis-agent` and the display name to `Source Code Analysis Agent`. Do not retain a Maven relocation POM or old artifact alias.

- [ ] **Step 4: Delete old production/test namespaces and obsolete target fixtures.**

  Remove the pre-reset implementation instead of wrapping it. Preserve Git history and `progress/*.md`; do not copy old classes into a `legacy` package.

- [ ] **Step 5: Add the fail-closed old-wire RED/GREEN.**

  Create `src/test/java/org/sourceanalysis/app/artifact/PreResetWireRejectionTest.java`. Cover the pre-reset `stages/` directories, `stageNN` keys/types, `stage-receipt.json`, old schema versions, old Maven/package identity in persisted descriptors, and unknown aliases. The only accepted result is a stable unsupported-wire failure with no installed output.

- [ ] **Step 6: Run the two direct selectors and commit.**

  Run `SourceAnalysisArchitectureTest,PreResetWireRejectionTest`, then the formatting check. Commit the reset separately so every later task starts from the new wire only.

### Task 3: Build artifact, evidence, runtime, and validation foundations

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/artifact/`
- Create: `src/main/java/org/sourceanalysis/app/evidence/`
- Create: `src/main/java/org/sourceanalysis/app/runtime/`
- Create: `src/main/java/org/sourceanalysis/app/validation/`
- Test: matching files under `src/test/java/org/sourceanalysis/app/`

**Interfaces:**
- Consumes: canonical JSON/schema policy and the closed semantic analysis-step registry.
- Produces: `CanonicalModuleArtifactStore`, `CanonicalAnalysisStepArtifactStore`, `CanonicalRunManifestStore`, typed evidence records, run state, and exterior validation publication seams.

- [ ] **Step 1: Write RED tests for canonical bytes, policies, and semantic addresses.**

  Create `CanonicalJsonCodecTest`, `CanonicalArtifactPolicyRegistryTest`, and `AnalysisStepAddressTest`. Assert semantic keys, `steps/<ordered-semantic-key>/` paths, semantic receipt names, and rejection of caller paths or numeric aliases.

  The first bounded 3–5 hour slice starts with one
  `CanonicalJsonCodecTest#encodesCanonicalObjectWithUtf8ByteOrderedKeys` RED and
  implements only `CanonicalJsonCodec`, `ImmutableBytes`, and the published
  typed identity/address primitives. Policy loading and each of the three stores
  follow through their own listed selectors. This first slice creates no
  filesystem publication, receipt, manifest, runtime/validation record,
  evidence record, JSONL/RAW_UTF8 writer, or business-analysis capability.

- [ ] **Step 2: Implement the minimum artifact value types and registries.**

  Keep generic byte/install mechanics in `.artifact`; evidence locators and excerpts belong in `.evidence`. Do not create a catch-all helper package.

- [ ] **Step 3: Write RED tests for atomic stores and execution state.**

  Create `CanonicalModuleArtifactStoreTest`, `CanonicalAnalysisStepArtifactStoreTest`, `CanonicalRunManifestStoreTest`, `RunExecutionStateTest`, and `CanonicalAnalysisStepArtifactStoreAtomicInstallIT`.

- [ ] **Step 4: Implement receipt-last publication and the four-state runtime.**

  Implement `QUEUED`, `RUNNING`, `FINISHED`, and `FAILED`; do not add resume/recovery states. A partial install is never observable as a successful publication.

- [ ] **Step 5: Run direct selectors, the one integration selector, and quality checks serially.**

  Verify fresh-process reopen, collision, partial-install, canonical identity, and old-wire rejection before committing.

### Task 4: Deliver verified source inventory

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/capture/localgit/`
- Create: `src/main/java/org/sourceanalysis/app/analysis/inventory/`
- Test: matching packages under `src/test/java/org/sourceanalysis/app/`
- Fixture: `src/test/resources/analysis/inventory/`

**Interfaces:**
- Consumes: one explicit registered immutable source capture.
- Produces: `VerifiedSourceInventoryReference` and the three official semantic payloads plus `verified-source-inventory-receipt.json`.

- [ ] **Step 1: Write `LocalGitCommitCaptureAdapterTest` and `FrozenRequestAdmissionTest` RED cases.**
- [ ] **Step 2: Implement isolated, read-only local Git capture and request admission.**
- [ ] **Step 3: Write `VerifiedSourceIndexerTest` RED cases for complete/bounded scope, media, symlink, gitlink, drift, and shard accounting.**
- [ ] **Step 4: Implement verified indexing from registered bytes only.**
- [ ] **Step 5: Write and pass `VerifiedSourceInventoryPublicationSpecifierTest`.**
- [ ] **Step 6: Verify three semantic outputs plus the semantic receipt, then commit.**

### Task 5: Deliver application discovery

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/discovery/`
- Test: `src/test/java/org/sourceanalysis/app/analysis/discovery/`
- Fixture: `src/test/resources/analysis/discovery/`

**Interfaces:**
- Consumes: `VerifiedSourceInventoryReference` and verified source bytes.
- Produces: `ApplicationDiscoveryReference`, four semantic payloads, and `application-discovery-receipt.json`.

- [ ] **Step 1: Write and pass `ApplicationProfileDetectorTest`.**
- [ ] **Step 2: Write and pass `SpringHttpEntryDiscovererTest`.**
- [ ] **Step 3: Write and pass `MapperCapabilityCatalogerTest`, including offline MyBatis DOCTYPE handling and XXE rejection.**
- [ ] **Step 4: Write and pass `ApplicationDiscoveryPublicationSpecifierTest`.**
- [ ] **Step 5: Verify complete source/site/entry/catalog accounting and commit.**

### Task 6: Deliver program graphs and proven code facts

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/graph/`
- Create: `src/main/java/org/sourceanalysis/app/analysis/fact/`
- Test: matching packages under `src/test/java/org/sourceanalysis/app/analysis/`

**Interfaces:**
- Consumes: `ApplicationDiscoveryReference`, then `ProgramGraphsReference`.
- Produces: seven program-graph payloads/receipt, then four proven-fact payloads/receipt.

- [ ] **Step 1: RED/GREEN `CodeStructureGraphBuilderTest`, `CallGraphBuilderTest`, and `ControlFlowGraphBuilderTest` in dependency order.**
- [ ] **Step 2: RED/GREEN `DataFlowGraphBuilderTest` and `EvidenceGraphBuilderTest` against verified bytes only.**
- [ ] **Step 3: RED/GREEN `ProgramGraphsPublicationSpecifierTest`; verify all five standalone graphs, index, gaps, and semantic receipt.**
- [ ] **Step 4: RED/GREEN `FactCandidateEnumeratorTest` for candidate/atom/disposition closure.**
- [ ] **Step 5: RED/GREEN `AtomicProofBuilderTest` for every claimed semantic atom.**
- [ ] **Step 6: RED/GREEN `ProvenCodeFactsPublicationSpecifierTest`; verify four semantic payloads and receipt, then commit each analysis step separately.**

### Task 7: Deliver business flows

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/flow/`
- Test: `src/test/java/org/sourceanalysis/app/analysis/flow/`

**Interfaces:**
- Consumes: `ApplicationDiscoveryReference`, `ProgramGraphsReference`, and `ProvenCodeFactsReference`.
- Produces: `BusinessFlowsReference`, five semantic payloads, and `business-flows-receipt.json`.

- [ ] **Step 1: RED/GREEN `EntryRootedFlowCompilerTest` for one disposition per discovered entry.**
- [ ] **Step 2: RED/GREEN `EvidenceCapsuleProjectorTest` for one persisted Capsule per Flow and evidence-backed `processJoinSignals`; reject generic-only joins and preserve counter/Gap/source lineage.**
- [ ] **Step 3: RED/GREEN `BusinessFlowsPublicationSpecifierTest` for complete denominators, zero-Flow accounting, and receipt-last installation.**
- [ ] **Step 4: Verify the fixed jshERP audit remains Gap / zero Flow / zero Capsule until a new authorized full run proves otherwise, then commit.**

### Task 8: Deliver flow interpretation

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/interpretation/`
- Create: `src/main/java/org/sourceanalysis/app/adapter/provider/`
- Test: matching packages under `src/test/java/org/sourceanalysis/app/`

**Interfaces:**
- Consumes: `BusinessFlowsReference`, each local Capsule, and the whole-repository `processJoinSignals` projection.
- Produces: `FlowInterpretationReference`, fourteen semantic payloads, and `flow-interpretation-receipt.json` (15 files).

- [ ] **Step 1: RED/GREEN `RegistryProposalTaskCompilerTest`, `RegistryProposalRunnerTest`, and `RepositoryInterpretationRegistryFreezerTest` serially.**
- [ ] **Step 2: RED/GREEN `FiniteKeyFlowTaskCompilerTest` for local `E + 2R` task/disposition conservation.**
- [ ] **Step 3: RED/GREEN `InterpretationRunnerTest` with a recording scripted Provider adapter.**
- [ ] **Step 4: RED/GREEN `CrossFlowCandidateCompilerTest` for four signal levels, generic-only rejection, `C` edges, `G` groups covering all Flows, and zero model calls.**
- [ ] **Step 5: RED/GREEN `BusinessProcessTaskCompilerTest` for `S` bounded shards and exactly one owner shard per candidate edge.**
- [ ] **Step 6: RED/GREEN `BusinessProcessInterpretationRunnerTest` for Luna P1/P2, non-expanding P2, typed GAP/FAILED, and P2 `NOT_RUN_UPSTREAM_FAILED`.**
- [ ] **Step 7: Prove `planned=E+2R+2S`, the exact actual-call formula, and started Provider failure with no retry/switch/resume/API fallback.**
- [ ] **Step 8: RED/GREEN `FlowInterpretationPublicationSpecifierTest`; verify fourteen payloads plus receipt, distinct M3/public registry pairs, and commit.**

### Task 9: Deliver repository knowledge

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/knowledge/`
- Test: `src/test/java/org/sourceanalysis/app/analysis/knowledge/`

**Interfaces:**
- Consumes: proven facts, all business flows, and all local/process interpretation artifacts.
- Produces: `RepositoryKnowledgeReference`, five semantic payloads, and `repository-knowledge-receipt.json`.

- [ ] **Step 1: RED/GREEN `ProposalAdmissionEngineTest` for local total admission, process claim admission, P1/P2 lineage, and exactly three certainty values.**
- [ ] **Step 2: RED/GREEN `AnchoredKnowledgeMergerTest` for conflict/alternative preservation, nine process arrays, many-to-many membership totality, and one knowledge.**
- [ ] **Step 3: RED/GREEN `RepositoryKnowledgePublicationSpecifierTest` for the renamed `knowledge-admission-decisions.jsonl`, five payloads, process-aware draft ledger, receipt, and acyclic lineage.**
- [ ] **Step 4: Verify model-ineligible Flows never gain fabricated interpretation records, then commit.**

### Task 10: Deliver the nine-section document and exterior validation

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/analysis/document/`
- Extend: `src/main/java/org/sourceanalysis/app/validation/`
- Test: matching packages under `src/test/java/org/sourceanalysis/app/`

**Interfaces:**
- Consumes: exact references from all seven upstream analysis steps.
- Produces: five semantic document payloads, `nine-section-archive-manifest.json`, `nine-section-document-receipt.json`, root `run-manifest.json`, and a separate validation publication.

- [ ] **Step 1: RED/GREEN `NineSectionPlannerTest` for one fixed-nine plan, process-first Chapter 4, five process ReaderItem kinds, certainty grouping, and body-cleanliness fields.**
- [ ] **Step 2: RED/GREEN `PlanOnlyRendererTest` for deterministic UTF-8/LF Markdown from plan bytes only.**
- [ ] **Step 3: RED/GREEN `TypedTraceCompilerTest` for the exact ReaderItem→ProcessKnowledge→Admission→Hypothesis→P1/P2→Group/Signal→Flow/Capsule→Fact/Proof/Evidence/Source chain.**
- [ ] **Step 4: RED/GREEN `CandidateRunArchiverTest` for five semantic payloads → archive manifest → semantic receipt → root run manifest.**
- [ ] **Step 5: RED/GREEN `IndependentRunValidatorTest`; reopen only explicit typed references and write idempotent exterior validation artifacts.**
- [ ] **Step 6: Count and fresh-reopen all 57 official outputs; verify validation/module artifacts are excluded, then commit.**

### Task 11: Deliver the public seam and adapters

**Files:**
- Create: `src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java`
- Create: `src/main/java/org/sourceanalysis/app/adapter/cli/`
- Create: `src/main/java/org/sourceanalysis/app/adapter/http/`
- Test: matching packages under `src/test/java/org/sourceanalysis/app/`

**Interfaces:**
- Consumes: runtime/application seams and typed analysis-step references.
- Produces: one run-centric `start`, `executeStep`, `inspect`, `artifact`, `render`, `validate`, and `trace` interface with symmetric Java/CLI/loopback HTTP adapters.

- [ ] **Step 1: RED/GREEN `RepositoryAnalysisAgentContractTest`.**

  Cover path-free requests, semantic `AnalysisStepExecutionRequest`, a new execution consuming exact upstream publications, pre-document `FINISHED` with null result/no root manifest, and the four final repository results only after the document step.

- [ ] **Step 2: RED/GREEN `RepositoryAnalysisCliAdapterTest`.**

  Parse semantic commands only; reject numbered aliases and caller paths. Keep stdout/stderr/exit-code goldens deterministic.

- [ ] **Step 3: RED/GREEN `RepositoryAnalysisLoopbackHttpAdapterTest` and the serial `RepositoryAnalysisLoopbackHttpAdapterIT`.**

  Reuse the same application seam, bind loopback only, and add no HTTP-only domain branch.

- [ ] **Step 4: Package and smoke-test the CLI locally.**

  Verify `--help`, invalid request, fixture query, and no source/Provider/network call.

- [ ] **Step 5: Commit adapters after direct, integration, quality, and release gates pass.**

### Task 12: Final acceptance and delivery

**Files:**
- Modify only current durable docs/progress when verification exposes a factual gap.
- Do not create compatibility or migration code to make acceptance pass.

**Interfaces:**
- Consumes: all independently reviewed implementation commits.
- Produces: a clean-checkout, fail-closed Source Code Analysis Agent delivery.

- [ ] **Step 1: Verify clean-checkout package and wire naming.**

  Require the new directory, Maven coordinate, package registry, semantic analysis-step keys, semantic receipts, and absence of forbidden target names.

- [ ] **Step 2: Run only the planned direct/regression selectors, then serial integration, quality, Javadoc, SBOM, and release gates.**

  Run OWASP only when its feed, credentials, and persistent cache are ready; otherwise record it as not run, never PASS.

- [ ] **Step 3: Reopen all official outputs in a fresh process.**

  Verify 47 semantic payloads + eight receipts + one archive manifest + one run manifest = 57, exact lineage, and partial-install rejection.

- [ ] **Step 4: Verify Wire Reset rejection and no recovery.**

  Feed representative pre-reset directories, descriptors, schema versions, receipt names, and type keys to every public reader. Require fail-closed responses and zero new publication. Interrupt a run and require `FAILED`; only a caller-created new run or explicit new step execution may continue.

- [ ] **Step 5: Run independent Standards and Spec reviews.**

  Require zero P0/P1 findings before final commit/push. Report any environmental gate that was not executable.
