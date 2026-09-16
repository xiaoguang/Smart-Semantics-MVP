# Progress: Step 01–05 design/code audit

- Status: COMPLETE
- Agent role: Read-only upstream design/code auditor
- Model: Codex
- Started: 2026-09-15
- Last updated: 2026-09-15
- Fixed point: `main` at `e8c40ea2f250da55d6b8797c32c380461061c3c9`
- Scope: Step 01–05, selectable JDT/JavaParser engines, artifact/evidence storage, safety/reopen behavior, corresponding authoritative design and effective tests.
- Approved inputs: Formal checkout source, design, tests, schemas, resources, and read-only Git metadata. No customer source execution, build, product-model call, network source access, external write, commit, or production-file edit.
- Classification rule: `docs/DESIGN.md`, `docs/analysis-steps/01`–`05`, `docs/modules/java-code-engines/`, and their active contract references are target design. `docs/history/`, completed plans, `docs/supplements/`, and `more-findings` are evidence/history only unless an active design adopts them.

## Executive result

Two material design-to-code gaps are confirmed:

1. The internal JavaParser Adapter is implemented and retained, but the only production CLI composition root rejects it and therefore does not provide the promised selectable-engine route.
2. Mapper XML is safely parsed and published by Step02, but neither selected engine attaches it to `EntryCodeContext.supportingSources`; the JDT navigation-only route consequently cannot carry SQL/XML text into Step05 business materials.

The suspected ordinary-path duplicate `open/compile/project/enumerate` algorithms are **not present** at this fixed point. One selected session is reused, and the Step04/05 owner algorithms run once. What remains is repeated same-process reopen/parse/lineage validation of artifacts that were just produced. That is a confirmed mismatch with the approved locality rule and a complexity/I/O concern, not a semantic double-computation defect.

No Step01–05 production class or JDT/JavaParser algorithm was proven dead. In particular, the JavaParser graph/fact/flow implementation and the JDT syntax-helper process must not be removed.

## Confirmed defects and discrepancies

### C1. JavaParser is implemented internally but blocked by the formal runner

**Approved design.**

- `docs/modules/java-code-engines/contracts-and-configuration.md:21-28` says selecting JavaParser requires only `javaEngine: javaparser` and defines the closed `jdt / javaparser` choice.
- `docs/modules/java-code-engines/integration-and-javaparser.md:7-15` says both integrations are complete and the composition root opens the selected session exactly once.
- `docs/modules/java-code-engines/README.md:38-59` shows one YAML-selected engine and no fallback.

**Actual call chain.**

- `src/main/java/org/sourceanalysis/app/runtime/EngineConfigurationLoader.java:31-52` accepts both values and gives JavaParser no JDT configuration.
- `src/main/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactory.java:11-17` constructs either `JdtCodeEngine` or `JavaParserCodeEngine`; the Adapter is real live code, not a stub.
- However, `src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java:1759-1774` loads that neutral configuration and immediately throws `JDT_ENGINE_REQUIRED` for every non-JDT selection.
- The only production construction of `PersistedTechnicalRunExecutor` is `RepositoryRunMain.java:230`; the other constructions found are tests. Thus an ordinary user of the maintained launcher cannot reach the JavaParser branch.

**Behavioral impact.** The published design promises a selectable fallback-free engine seam, while the formal repository-run launcher is JDT-only. JavaParser remains usable through internal Java workflow seams/tests, so deleting it would destroy implemented graph/Fact/Flow capability rather than remove dead code.

**Minimal direct test before changing behavior.** Add one `RepositoryRunMain` configuration/materials-only integration test using `javaEngine: javaparser` with no `jdt` block. Assert: configuration admission succeeds; the saved index descriptor is `javaparser`; Step03 has the index plus the existing JavaParser graph set; Step04 is `AVAILABLE`; Step05 materials reopen; and no JDT engine/process is constructed. Do not claim completion from `JavaCodeEngineFactory` selection alone (`SelectableJavaEngineAcceptanceTest.java:30-43` only proves that internal seam).

### C2. Mapper XML is published, then disconnected from unified source material

**Approved design.**

- `docs/analysis-steps/02-application-discovery.md:17-26` requires the selected catalog plus safe XML to produce Mapper/statement candidates.
- `docs/analysis-steps/05-business-flows.md:17-25` says located safe configuration/XML is attached to the entry material.
- `docs/modules/java-code-engines/contracts-and-configuration.md:153-159` explicitly defines `supportingSources` for located configuration or MyBatis XML fragments.

**Actual call chain.**

1. `src/main/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutor.java:57-95` obtains `javaCodeSession.catalog()`, passes it to `MapperCapabilityCataloger`, and publishes the Mapper catalog.
2. The legacy strict graph route can read `mapper-catalog.jsonl` through `PersistedProgramGraphInputReader.java:52-120`, and `CallGraphBuilder.java:323-349` uses it. This proves the catalog itself is not dead.
3. The JDT collector constructs the shared context with an empty list at `analysis/code/jdt/EntryCodeCollector.java:162-180` (`supportingSources` is `List.of()` at line 173).
4. The JavaParser Adapter does the same at `analysis/code/javaparser/JavaParserContextAdapter.java:91-113` (`List.of()` at line 106).
5. `analysis/flow/compiler/EntryContextAssembler.java:44-112` reopens only `java-code-index` and transfers its context; it never enriches it from the Step02 Mapper catalog.
6. `analysis/interpretation/material/BusinessMaterialBuilder.java:650-719` builds the JDT navigated material. Its only supplemental-source inputs are `context.supportingSources()` at lines 721-740, and its only supplemental observation is at lines 817-818.
7. Across production source, there is no construction of `new EntryCodeContext.SupportingSource(...)`; the sole explicit construction found is a handcrafted Builder test fixture at `BusinessMaterialBuilderTest.java:647`.

**Behavioral impact.** The JDT route stops at the Mapper Java interface/declaration. Although Step02 has parsed exact statement XML, SQL such as an `UPDATE` is absent from the saved code context, Capsule, business material, and model allowlist. This reduces business interpretation exactly at a persistence boundary where the design says the safe fragment should be available. The JavaParser strict graph route can still use Mapper catalog relationships, but its new common `EntryCodeContext` also fails the promised `supportingSources` contract.

**Why current tests miss it.** `TechnicalAnalysisWorkflowTest.java:886-956` creates `OrderMapper.xml` containing `UPDATE orders SET status = #{status}`, but its actual material assertion at lines 684-691 checks only `OrderController`, `OrderService`, and `mapper.updateStatus(status)`. It never asserts that the SQL/XML survives.

**Minimal direct test before changing behavior.** Extend that self-contained Spring/MyBatis selected-JDT run to reopen `java-code-index.jsonl`, Step05 context/Capsule, and `business-materials.jsonl`; require one source-verified `SupportingSource` for the matching namespace+statement and require the exact SQL in the material/model allowlist. Also cover namespace or statement-ID mismatch as an explicit absence/limitation, and assert no JavaParser fallback. A shared contract test should then verify the same neutral field for JavaParser without requiring equal binding precision.

### C3. Core algorithms run once, but same-process artifact reopen work is repeated

**What is already correct.**

- `PersistedTechnicalRunExecutor.java:96-126` opens one selected `JavaCodeSession` and passes it through discovery and continuation; `openSession` is called once per execution at lines 129-139.
- `ProvenCodeFactsExecutor.java:59-70` detects the JDT navigation-only set and publishes `NOT_PRODUCED` without enumerating. On a graph-capable route, lines 72-95 call `FactCandidateEnumerator.enumerate` once and `AtomicProofBuilder.prove` once.
- `BusinessFlowsExecutor.java:41-100` invokes either navigation assembly or `EntryRootedFlowCompiler.compile` once, `EvidenceCapsuleProjector.project` once, and the final publishers once. There is no second compile/project call hidden in the publishers.
- `PersistedFactCandidateSetReader.java:128-143` parses the saved candidate set without enumeration. Only its explicit audit overload at lines 145-168 re-enumerates, matching the design.

**Remaining mismatch.** The active locality contract says an owner may pass its immutable typed result downstream in the same trusted execution, publishers should serialize/check rather than replay, and disk/new-process/import are the full reopen boundaries (`docs/references/foundation-and-publication-contracts.md:37-43`; Step02 `docs/analysis-steps/02-application-discovery.md:106-110`; Step04 `docs/analysis-steps/04-proven-code-facts.md:37-48,124-128`). Current orchestration repeatedly reopens just-written artifacts:

- Step02 publishes profile M1 and immediately reopens it before M2/M3 (`ApplicationDiscoveryExecutor.java:48-59`).
- Step04 passes in-memory candidates/decisions to publishers, then `FactLedgerPublicationSpecifier.java:110-127` reopens candidate/proof modules; `PersistedProofDecisionSetReader.java:121-173` itself reopens the candidate module twice (typed parse and payload-reference extraction).
- Step05 first reopens Step03 merely to branch (`BusinessFlowsExecutor.java:102-112`), assembler/compiler readers reopen inputs, `FlowCompilationModulePublisher.java:82-133` reopens/validates them again, and `CapsuleProjectionModulePublisher.java:73-117` again reopens Step01/03/04. The final publisher has another persistence-boundary check.

**Behavioral impact.** Results remain fail-closed and there is no algorithmic replay, but large persisted payloads are parsed/validated multiple times in one trusted process. This increases latency, allocation pressure, and code surface; it also makes the documented trust-boundary distinction inaccurate.

**Minimal direct test for a later simplification.** Use counting wrappers around the canonical step/module stores and spies around enumerate/compile/project. Assert exactly one owner algorithm call and a bounded reopen count on ordinary same-process execution; separately retain full corruption/hash/schema/ref tests at an explicit new-process/reopen reader boundary. Do not weaken atomic install, collision, exact-file-set, or source-byte verification.

## Known deferrals, intentional choices, and non-findings

### Known deferral: multi-module fidelity is not complete

`docs/modules/java-code-engines/jdt-engine.md:15-21` describes determined modules/projects and module relationships, while `PersistedTechnicalRunExecutor.java:129-164` derives conventional roots and creates one `VerifiedJavaProject`; `JdtProjectSession.java:64-82,276-324` projects all roots into one Eclipse project. The same design explicitly limits current evidence for multi-module dependency handling at `jdt-engine.md:185-191`. Treat this as an acknowledged coverage boundary, not a newly discovered regression or deletion target. A future change needs a multi-module fixture with duplicate FQNs and explicit module edges, not a large-repository anecdote.

### Intentional choice: JDT Core helper and private protocol are active and justified

The helper is not dead architecture. `JdtProjectSession.java:194-209` locates and starts it; `JdtLanguageServerClient.java:163-171` launches it with the configured tool JDK and verified roots/classpath. `jdt-engine.md:40-71` explains the Java 17 host versus Java 21+ tool/runtime isolation, fixed helper dependencies, source hashing, and narrow JSONL protocol. Its source, protocol client, artifact locator, Maven submodule, and targeted tests must stay together.

The remaining packaging question is narrower: `JdtSyntaxHelperArtifact.java:17-49` discovers the jar from working-directory/protection-domain candidates, and `JdtLanguageServerClient.java:144-147` records JDT LS/JDK identities but not an explicit helper-jar digest. Decide whether the release layout already guarantees helper identity strongly enough. If portability/tamper provenance is required, make the helper artifact an explicit composition-root input and bind a content digest into the engine descriptor; first test packaged-jar launch from a non-project working directory and mutation after configuration. This is a discussion item, not a confirmed dead protocol.

### Intentional choice: local absolute paths; portability question remains

- The tracked `tools/repository-run/jdt-luna-repository-run.template.json` contains placeholders, not one developer's actual filesystem paths. JDT installation/Java home paths are intentionally local bootstrap configuration (`contracts-and-configuration.md:9-31`) and should not be removed from startup configuration.
- Absolute approved-classpath strings participate in both effective toolchain identity (`RepositoryRunMain.java:2048-2060`) and `VerifiedJavaProject` fingerprint (`VerifiedJavaProject.java:197-221`), even when jar bytes are identical. This safely binds the exact approved local file but makes otherwise identical runs machine/location dependent. Whether to normalize identity to ordered content digest plus semantics is a product/security decision because jar filenames/order can affect Java resolution. Do not mechanically strip paths.
- `tools/repository-run/README.md` embeds a Homebrew Java path and a historical `.workspace/...` toolchain path in command examples. Those examples are documentation-only and should be replaced by neutral placeholders when that guide is next edited; they are not runtime configuration or evidence.

### Safety/reopen strengths retained

- `VerifiedJavaProject.java:81-109,152-165` rechecks approved classpath bytes and projects only verified inventory documents into a session-owned temporary root.
- `JdtProjectSession.java:50-101,143-184` owns lifecycle cleanup and fails rather than silently using customer workspace metadata.
- Canonical module/step stores remain the disk/new-process verification boundary with receipt-last install, exact payload sets, identities, hashes, lineage, and collision checks. No recommendation here removes those controls.

## Deterministic cleanup set

### 1. Reconcile stale engine-maturity text (safe documentation cleanup)

The following statements cannot all be true and should be replaced with one precise status: internal Adapter complete; `RepositoryRunMain` still JDT-only; no fallback.

- `docs/analysis-steps/02-application-discovery.md:17` still says JavaParser adaptation is future work.
- The same file at lines 112-118 says selection returns `ENGINE_NOT_INTEGRATED`.
- `src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunConfiguration.java:113-115` says the no-engine route is retained only until phase-two adaptation.
- These conflict with `integration-and-javaparser.md:7-15,70+`, `contracts-and-configuration.md:3,21-28`, and the live factory/Adapter.

Readers: maintainers and operators selecting an engine. Minimal verification: targeted configuration/factory tests plus the proposed formal-runner JavaParser test; link checking/`rg` for the retired `ENGINE_NOT_INTEGRATED` maturity claim. This is text cleanup, not authority to enable the runner in this audit.

### 2. Business-process v1 prompt resources (incidental, outside Step01–05)

Production `BusinessProcessPromptCatalog.java:11-26` maps every current task to `*-v2.txt`. Therefore these six tracked v1 files have neither a production nor test reader and are definite dead resources:

- `business-catalog-merge-draft-v1.txt`
- `business-catalog-merge-review-v1.txt`
- `business-process-draft-v1.txt`
- `business-process-review-v1.txt`
- `business-process-consolidation-draft-v1.txt`
- `business-process-consolidation-review-v1.txt`

Two more are production-dead but still have a deliberate historical-test reader:

- `business-catalog-draft-v1.txt`
- `business-catalog-review-v1.txt`

`BusinessProcessSemanticFingerprintV2Test.java:115-160` reads those two to synthesize a legacy v1 fingerprint. They may be removed only in the same change that replaces that fixture with a fixed, reviewed legacy fingerprint/preimage; otherwise the test breaks. After such a fixture rewrite, the coherent cleanup set is all eight business `*-v1.txt` files. Readers: only the named test for the last two; none for the first six. Minimal verification: `BusinessProcessSemanticFingerprintV2Test` and `BusinessProcessPromptV2ContractTest`. Do **not** delete `process-group-draft-v1.txt` or `process-group-review-v1.txt`: `ProcessPromptCatalog.java:10-23` loads them in production and `ProcessExplainer` consumes that catalog.

### Not cleanup targets

- `tools/repository-run/jdt-artifact-policy-set-pre-process-discovery-v1.json` is used as `inputPolicyRegistry` by retained historical workspaces; `RepositoryRunMain.java:1780-1787` supports the reader role. It is migration/reopen input, not an unused duplicate of the current policy.
- The legacy no-session overloads in `TechnicalAnalysisWorkflow.java:50-65,88-120,221-234`, `ApplicationDiscoveryExecutor.java:18-27`, and the null `engineConfiguration` branch in `PersistedTechnicalRunExecutor.java:105-114` are absent from the formal runner but are exercised by existing strict JavaParser/graph tests. Their removal or conversion requires a caller migration inventory and direct strict-route equivalence test; they are not confirmed dead.
- JavaParser parsers, five-graph builders, Fact enumerators/provers, strict Flow compiler/projector, JDT helper, and their valid tests are active capability and history-preserving code.

## Audited scope and limits

Completed read-only tracing covered:

- Formal `RepositoryRunMain` configuration through run creation and `PersistedTechnicalRunExecutor` Step01–05 execution.
- Both engine loaders/factory/sessions, Step02 discovery/Mapper publication, Step03 navigation/strict graph branches, Step04 availability/enumeration/publication, Step05 assembly/compiler/projector/publication, and Builder consumption.
- Relevant canonical store/read boundaries, engine/context schemas, tracked runtime resources/configuration, and focused unit/integration tests that claim the selectable-engine and Spring/MyBatis route.
- The three scoped `AGENTS.md` files and the authoritative Step01–05/Java-engine/reference design documents in full.

Not exhaustively audited:

- Step06–08 semantic correctness, provider behavior, report prose, full historical workspaces, every schema/policy line, every failure/mutation test, or every possible reflection/service-loader consumer.
- External customer repositories, installed JDT distributions/JDKs, generated `target/`, product model output, network behavior, or runtime performance measurements.
- No build or test was run by instruction; all verification here is static call-chain/design/test-source inspection at the fixed commit.

## Changed files

- `progress/design-code-audit-upstream.md` (this audit record only)

## Verification log

| Check | Result | Evidence |
| --- | --- | --- |
| Formal fixed point | PASS | Nested `HEAD` resolved to `e8c40ea2f250da55d6b8797c32c380461061c3c9`. |
| Worktree preservation | PASS | Pre-existing and concurrently created untracked audit/research paths were preserved; this audit changed only its own progress file. |
| One selected session | PASS (static) | `PersistedTechnicalRunExecutor.java:96-139`. |
| No normal duplicate enumerate/compile/project | PASS (static) | `ProvenCodeFactsExecutor.java:59-95`; `BusinessFlowsExecutor.java:41-100`; explicit replay overload isolated at `PersistedFactCandidateSetReader.java:145-168`. |
| Formal JavaParser selection | FAIL (confirmed) | `RepositoryRunMain.java:1769-1774` rejects it before executor construction. |
| Mapper XML reaches unified context/material | FAIL (confirmed) | Step02 publishes it; both collectors write empty `supportingSources`; assembler/builder have no Mapper-catalog join. |
| Builds/tests/customer/model calls | NOT RUN | Prohibited for this audit. |

## Blockers

- None for this audit report. Enabling JavaParser in the formal runner, joining Mapper XML into entry context, and simplifying same-process reopen behavior are intentionally left for explicit design/implementation work with the direct tests above.
