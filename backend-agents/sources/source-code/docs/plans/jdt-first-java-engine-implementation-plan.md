# JDT-first Java Engine Implementation Plan

> **For the implementing agents:** execute these tasks in order. Luna writes the RED contract tests, Terra implements the smallest GREEN change, and Sol diagnoses only when the contract or environment is genuinely unclear. Do not start Task 9 until Tasks 1–8 have passed the first-stage gate.

**Goal:** Make JDT the first complete Java source engine from Step02 entry discovery through Step05 business material, then adapt JavaParser to the frozen contract without expanding its capability.

**Architecture:** One `JavaCodeEngine` opens one snapshot-bound `JavaCodeSession`. Step02 persists overload-safe `methodKey` plus `SourceRange`; Step03 publishes the session's neutral `java-code-index`; Step04 truthfully publishes whether strict graph-derived facts exist; Step05 consumes persisted `EntryCodeContext` first. JDT LS owns project navigation and a separately built JDT Core helper owns exact source/syntax extraction. JavaParser is connected only after the JDT contract is released.

**Tech stack:** Java 17 host application, Maven, JUnit 5, Jackson JSON/YAML, Eclipse LSP4J/Gson for JDT LS, a shaded JDT Core 3.47.0 helper launched by the configured tool JDK, JSONL protocol `jdt-syntax-v1`.

**Authoritative design:** [Java engine overview](../modules/java-code-engines/README.md), [contracts and configuration](../modules/java-code-engines/contracts-and-configuration.md), [JDT engine](../modules/java-code-engines/jdt-engine.md), and [integration/JavaParser](../modules/java-code-engines/integration-and-javaparser.md). These documents decide behavior when a task below names a contract but does not repeat every field.

## Frozen sequencing and constraints

- Tasks 1–8 are stage one and must produce a JDT-only releasable path. `sourceAnalysis.javaEngine=javaparser` returns `ENGINE_NOT_INTEGRATED` during this stage; JDT failure never falls back.
- Tasks 9–10 are stage two. They restore only JavaParser behavior present at baseline `cec1997`; they do not add Symbol Solver wiring, new overload/import/inheritance algorithms, or JDT parity work.
- Do not rewrite `CanonicalModuleArtifactStore`, `CanonicalAnalysisStepArtifactStore`, the CLI command surface, `RepositoryAnalysisAgent`, `SourceAnalysisApplication`, `BusinessAnalysisWorkflow`, `PersistedBusinessRunExecutor`, `ActivityExplainer`, `ProcessExplainer`, or `BusinessReportPublisher`. Extend their existing seams only where a named task requires wiring or validation.
- Do not add an analysis step. `java-code-index` is module 7 of existing `PROGRAM_GRAPHS`; its address is `(PROGRAM_GRAPHS, 7, "java-code-index")`.
- All paths are snapshot-relative; all offsets are UTF-16; persisted engine-neutral models contain no JDT handles, LSP DTOs, or JavaParser AST nodes.
- Each GREEN must update the direct readers, exact artifact-set allowlists, policies, fixtures, and current-fact documentation in the same change. No legacy-wire fallback reader is permitted.

## Task 1: Configure the engine and open a neutral JDT project session (approved 1.2)

**Files**

- Create: `src/main/java/org/sourceanalysis/app/analysis/code/SourceRange.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/VerifiedJavaProject.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/EntrySeed.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/EngineDescriptor.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/JavaDeclarationCatalog.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/EntryCodeContext.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/JavaCodeEngine.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/JavaCodeSession.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/CodeEngineException.java`
- Create: `src/main/java/org/sourceanalysis/app/runtime/EngineConfigurationLoader.java`
- Create: `src/main/java/org/sourceanalysis/app/runtime/EffectiveEngineConfiguration.java`
- Create: `src/main/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactory.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtCodeEngine.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSession.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java`
- Modify: `pom.xml`
- Create: `src/test/java/org/sourceanalysis/app/runtime/EngineConfigurationLoaderTest.java`
- Create: `src/test/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactoryTest.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java`

**RED**

- Luna adds tests for exact, case-sensitive values `jdt` and `javaparser`, rejection of missing/unknown values, canonical absolute `jdt.installation` and `jdt.javaHome`, and rejection when `${jdt.javaHome}/bin/java` is not executable.
- Luna proves a session is bound to one `snapshotId`, source root set, classpath, source level, and fingerprint; source roots outside the snapshot and path traversal fail before JDT starts.
- Luna proves stage-one factory behavior: `jdt` creates `JdtCodeEngine`; `javaparser` fails with `ENGINE_NOT_INTEGRATED`; a JDT startup/index failure is returned unchanged and never selects JavaParser.

**GREEN**

- Terra implements the neutral records/interfaces exactly as specified in the contracts document, with constructors validating ranges, stable keys, snapshot-relative paths, and collection/availability invariants.
- Load configuration once at the composition root. Give the JDT client mandatory positive startup/query/shutdown `Duration` bounds through `EffectiveEngineConfiguration`; do not read environment variables inside analysis modules.
- Start JDT LS with `${jdt.javaHome}/bin/java`, the configured installation, a per-session workspace, and the verified project model. Wait for initialization/import/index readiness before allowing catalog or collection queries. Close the session idempotently and delete only its owned temporary workspace.
- Pin production dependencies to the validated harness line: LSP4J 1.0.0 and Gson 2.14.0. Do not copy research-harness classes into production or add JavaParser to JDT packages.

**Verification**

- Run `mvn -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test`.
- Pass means every test above is green, no JavaParser class is loaded on the JDT path, and startup/close leaves no owned helper or LS process alive.

## Task 2: Read complete Java source through the standalone JDT Core helper (approved 1.3)

**Files**

- Create: `tools/jdt-syntax-helper/pom.xml`
- Create: `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxHelperMain.java`
- Create: `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxProtocol.java`
- Create: `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxReader.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxHelperClient.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxProtocol.java`
- Modify: `pom.xml`
- Create: `tools/jdt-syntax-helper/src/test/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxReaderTest.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxHelperClientTest.java`

**RED**

- Luna specifies a one-request/one-response JSONL transcript with `protocolVersion=jdt-syntax-v1`, unique `requestId`, snapshot-relative file, source fingerprint, and requested method range. Responses echo version/id and contain the complete method text plus declarations, call sites, controls, exits, diagnostics, and exact `SourceRange`s.
- Cover annotations, Javadoc, constructors, overloads, varargs, nested lambdas, duplicate call spelling at different positions, CRLF, Chinese text, and supplementary Unicode. Prove offsets slice the original Java string as UTF-16.
- Cover malformed JSON, wrong/unknown protocol fields, version/id mismatch, stdout EOF, source fingerprint mismatch, per-request timeout, non-zero exit, and hung shutdown. None may be converted to a successful empty result.

**GREEN**

- Build the helper as the independent same-repository artifact `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar`; use JDT Core 3.47.0 and Jackson 2.21.4 and produce a self-contained executable jar.
- Launch it only with `${jdt.javaHome}/bin/java -jar <artifact>` using `ProcessBuilder`, never a shell or the host application's JVM selection. Keep exactly one request in flight per helper process. Reserve stdout for JSONL; bound and capture stderr as diagnostics.
- A request timeout kills the process, marks the session unusable, performs no retry, and raises `JDT_SYNTAX_TIMEOUT`. Invalid JSON/envelope/EOF raises `JDT_SYNTAX_PROTOCOL_INVALID`; unexpected non-zero exit raises `JDT_SYNTAX_PROCESS_FAILED`; failure to exit inside the shutdown bound is forcibly terminated and raises `JDT_SYNTAX_SHUTDOWN_TIMEOUT`. Normal close closes stdin and accepts exit code 0 only.
- Use JDT ASTParser only inside the helper. The host maps the response to neutral models and rejects out-of-bounds paths/ranges or a changed fingerprint.

**Verification**

- Run `mvn -f tools/jdt-syntax-helper/pom.xml test` and `mvn -Dtest=JdtSyntaxHelperClientTest test`.
- Inspect `jar tf tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar`; pass means JDT Core is present, JavaParser is absent, the protocol tests are green, and each fatal case exposes its exact code.

## Task 3: Resolve calls and collect coherent entry source (approved 1.4)

**Files**

- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtNavigationResolver.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/CollectionBudget.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSession.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtNavigationResolverTest.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollectorTest.java`

**RED**

- Luna covers method calls, constructors, static/instance method references, interface/multiple implementations, external declarations without source, overloads, cycles, duplicate physical call sites, and disagreement between declaration/type-definition/implementation navigation.
- Luna asserts `navigationSite` is the helper-provided AST range; targets are deduplicated by real location, roles/navigation kinds are merged, and conflicting facts remain `NAVIGATION_CONFLICT` rather than last-write-wins.
- Luna covers deterministic traversal and every stop boundary: depth/method/source-byte/time budget, excluded roots, generated/vendor files, missing source, changed source, and a single corrupt result. Collected neighbors remain usable and each affected key receives a limitation.

**GREEN**

- Query JDT LS only at valid AST navigation positions. Normalize returned URIs, reject locations outside the snapshot, read the exact declaration with the helper, and preserve all real candidates.
- Traverse breadth-first in stable source-position order. Deduplicate method bodies by `methodKey`; retain each call edge and candidate relation; represent recursion by references rather than copied bodies.
- Return one `EntryCodeContext` per `EntrySeed` with complete selected method bodies, calls, candidates, ordered actuals/formals, controls/exits, supporting sources, diagnostics, limitations, collection status, and honest technical-enhancement availability.

**Verification**

- Run `mvn -Dtest=JdtNavigationResolverTest,EntryCodeCollectorTest test`.
- Pass means fixture-order randomization produces identical normalized output and every stopped/ambiguous branch is explicit without losing already collected code.

## Task 4: Integrate JDT catalog with application and overload-safe entry discovery (approved 1.5)

**Files**

- Modify: `src/main/java/org/sourceanalysis/app/analysis/discovery/SpringHttpEntryDiscoverer.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCataloger.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/discovery/HttpEntryPoint.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/discovery/HttpEntryDiscoveryModulePublisher.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryPublicationSpecifier.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReader.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- Modify: `src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistry.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/discovery/SpringHttpEntryDiscovererTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/discovery/HttpEntryDiscoveryModulePublisherTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutionTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryPublicationSpecifierTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCatalogerTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/AmbiguousCallHandoffTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/DiscoveryToFactHandoffTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphGapProjectionTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationSpecifierTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- Modify: `src/test/java/org/sourceanalysis/app/artifact/ProgramGraphsAnalysisStepArtifactStoreTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryContractTest.java`

**RED**

- Luna adds two same-name handler overloads and proves every persisted Step02 entry carries both the neutral `methodKey` and the full declaration `methodRange`; downstream readers select by both and never by handler FQN/name alone.
- Luna freezes the new public wire: M2 `http-entry` moduleVersion and `application-discovery-http-entry-discovery-v2` become v3; M4 `publish` moduleVersion becomes v3; `application-discovery-entry-points-v2` and each `application-discovery-entry-point-v2` line become v3. Profile/capability/mapper stay v2. The old entry wire is rejected with the stable unsupported-version error and all direct consumers agree.
- Preserve Spring route/annotation/import/alias/composed-annotation cases and mapper XML security/gap behavior while proving JDT mode invokes no JavaParser discoverer.

**GREEN**

- Make Java declaration and annotation identity come from the selected session catalog; retain shared Spring route and mapper XML semantics.
- Persist `methodKey` and `methodRange={startOffsetUtf16,lengthUtf16,startLine,endLine}` on every entry and include both in `entryId` framing. Derive `EntrySeed` from those values. Range means the complete declaration and its path is resolved through `methodKey`/catalog, so overload selection is exact.
- Bump the application-discovery draft and entry-point line contracts to version 3 in the writer, every direct reader, artifact policy, and fixtures in one change. Do not add a v2 fallback or infer missing keys.

**Verification**

- Run `mvn -Dtest=SpringHttpEntryDiscovererTest,HttpEntryDiscoveryModulePublisherTest,ApplicationDiscoveryExecutionTest,ApplicationDiscoveryPublicationSpecifierTest,MapperCapabilityCatalogerTest,PersistedProgramGraphInputReaderTest,AmbiguousCallHandoffTest,DiscoveryToFactHandoffTest,ProgramGraphGapProjectionTest,ProgramGraphPublicWireTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest,CanonicalArtifactPolicyRegistryContractTest test`.
- Pass means both overloads survive publish/read round-trip with distinct keys/ranges and the JDT test path has no JavaParser interaction.

## Task 5: Publish navigation and truthful technical-enhancement availability (approved 1.6)

**Files**

- Create: `src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexPublicationSpecifier.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexReader.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/AnalysisStepModuleAddress.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/ArtifactPolicyKey.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistry.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- Modify: `src/main/java/org/sourceanalysis/app/artifact/AtomicAnalysisStepPublicationEngine.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecution.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/fact/ProvenCodeFactsExecutor.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java`
- Modify: `src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryContractTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/artifact/ProgramGraphsAnalysisStepArtifactStoreTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecutionTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationSpecifierTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/fact/ProvenCodeFactsExecutionTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/fact/publish/ProvenCodeFactsPublicationSpecifierTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexPublicationSpecifierTest.java`

**RED**

- Luna freezes module 7 `(PROGRAM_GRAPHS,7,"java-code-index")`, artifact type `PROGRAM_GRAPHS_JAVA_CODE_INDEX`, schema `java-code-index-v1`, and the exact ENGINE/TYPE/METHOD/CALL/ENTRY_MEMBERSHIP/DIAGNOSTIC envelope/invariants.
- Luna asserts the JDT Step03 semantic allowlist is exactly `java-code-index.jsonl`; its actual public files are that file plus `program-graphs-receipt.json`. No empty graph file is legal.
- Luna asserts the JDT Step04 semantic allowlist is exactly `fact-accounting.json`; its actual public files are that file plus `proven-code-facts-receipt.json`. Accounting schema v4 has `availability=NOT_PRODUCED`, a non-empty reason, the Step03 navigation basis, every numeric count `null`, and no candidate/fact/proof reference. The legacy enumerator is not called.
- Luna retains tests for corruption: an artifact advertised as AVAILABLE but missing/damaged remains fatal; only an enhancement not requested/not implemented may be NOT_PRODUCED.

**GREEN**

- Register module 7 in the existing address/store/policy machinery; do not add a step, store, receipt type, or lifecycle state. Make module 7 the final Step03 publisher for selected-engine runs.
- Teach exact-set validation the legal actual sets: JDT module 7/Step03 contains the index only; JDT Step04 module 3 contains accounting only. Preserve the existing module-6 seven-graph exact set as an optional enhancement subchain, not an automatic JDT dependency.
- Branch Step04 on persisted technical-enhancement availability. When graphs are absent by design, publish only NOT_PRODUCED accounting; when valid graphs are AVAILABLE, keep the existing strict candidate/proof algorithm and meanings.

**Verification**

- Run `mvn -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsExecutionTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest,ProvenCodeFactsExecutionTest,ProvenCodeFactsPublicationSpecifierTest,BusinessFlowsPublicationSpecifierTest,CanonicalArtifactPolicyRegistryContractTest test`.
- Inspect one JDT fixture run's receipts. Pass means Step03 lists one semantic descriptor, Step04 lists one semantic descriptor, counts are null rather than zero, and no graph/fact placeholder exists.

## Task 6: Make Step05 context-first and update Capsule/readers (approved 1.7)

**Files**

- Create: `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryContextAssembler.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutor.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisherTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutionTest.java`

**RED**

- Luna asserts Step05 reads persisted `java-code-index-v1`/`EntryCodeContext`, never reparses Java and never re-navigates. Every entry has either one COLLECTED context or NOT_COLLECTED with reason; every collected context has a Capsule, including when strict Flow/Fact is unavailable.
- Luna freezes exact target schema versions: flow compilation v5, capsule projection v10, flow slices v5, evidence capsule v8, flow coverage v2, entry disposition v2, while `entry-code-context-v1` remains v1.
- Cover zero strict flow with rich code context (`flowRef=null`), multiple navigation candidates, constructors, limitations, corrupt refs, and rejection of every legacy context wire. Preserve the Step05 five-file semantic allowlist and receipt.

**GREEN**

- Replace the narrow single-target/actual-only context with the neutral model. `EntryContextAssembler` joins only persisted Step02 seeds, Step03 index/context, and optional verified Step04 enhancements.
- Make Capsule a faithful reference/projection of context. It may attach existing exact Fact/Proof/Flow references when AVAILABLE, but must not recalculate navigation or require them for a collected context.
- Update all named writers/readers/policies/fixtures atomically to the frozen versions. Remove compatibility constructors and fallback aliases that could silently turn missing new fields into empty collections.

**Verification**

- Run `mvn -Dtest=FlowCompilationTest,FlowCompilationModulePublisherTest,CapsuleProjectionModulePublisherTest,BusinessFlowsExecutionTest test`.
- Pass means the JDT no-graph fixture still publishes the existing five Step05 semantic files, each entry is accounted for, and every collected context is reachable from an evidence capsule.

## Task 7: Feed unified code context into business material and the existing run chain (approved 1.8)

**Files**

- Modify: `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- Modify: `src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java`
- Modify: `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflow.java`
- Modify: `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java`
- Modify: `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/runtime/FourEntryBusinessSemanticChainTest.java`

**RED**

- Luna proves material includes every selected method's complete source, calls, ordered arguments/formals, candidate branches, controls/exits, supporting-source excerpts, limitations, and optional strict refs under stable E/M/C/S short identifiers.
- Prove `BusinessMaterialBuilder` has no JavaParser AST/import and applies no observation cap to complete code. Prove persisted execution gives the same material to the unchanged Activity → Process → Report chain.
- Cover registration, financial, cross-domain, constructor, multiple-implementation, cycle, missing-source, and NOT_COLLECTED entries. Source paths remain hidden according to the existing publication policy.

**GREEN**

- Make `BusinessMaterialBuilder` a pure consumer/formatter of persisted unified material; move any remaining parser-dependent observation extraction into the engine adapters.
- Wire the selected session and new persisted references through the existing technical/business workflows and executor. Keep CLI operations, run registry/storage, `ActivityExplainer`, `ProcessExplainer`, and `BusinessReportPublisher` behavior unchanged.

**Verification**

- Run `mvn -Dtest=BusinessMaterialBuilderTest,TechnicalAnalysisWorkflowTest,PersistedBusinessRunExecutorTest,FourEntryBusinessSemanticChainTest test`.
- Pass means the scripted chain still reaches the existing nine-chapter report and JDT-derived code is visible in business material without parser/model/provider work.

## Task 8: Accept and release the independent JDT stage (approved 1.9)

**Files**

- Modify: `docs/DESIGN.md`
- Modify: `docs/analysis-steps/02-application-discovery.md`
- Modify: `docs/analysis-steps/03-program-graphs.md`
- Modify: `docs/analysis-steps/04-proven-code-facts.md`
- Modify: `docs/analysis-steps/05-business-flows.md`
- Modify: `docs/modules/java-code-engines/README.md`
- Modify: `docs/modules/java-code-engines/contracts-and-configuration.md`
- Modify: `docs/modules/java-code-engines/integration-and-javaparser.md`
- Modify: `docs/modules/java-code-engines/jdt-engine.md`
- Modify: `AGENTS.md`

**RED**

- Luna creates an acceptance matrix that proves: JDT discovers exact entries independently; registration/financial/cross-domain code expands; constructors/multiple implementations/cycles/missing source are visible; persisted unified material is readable; the scripted business chain completes; JavaParser receives zero calls.
- Add failure acceptance for missing tool, bad installation/JDK, startup/index/query timeout, helper protocol failure, source drift, and corrupt artifacts. Expected result is the stable explicit failure/availability state, never fallback or fake empty success.

**GREEN**

- Terra fixes only failures that violate the frozen first-stage contracts. Update current-fact documentation from “target design” to the actually verified maturity and keep `javaparser` explicitly unavailable until Task 9.
- Do not run a model/provider, change prompts, redesign reports, or begin JavaParser parity work to satisfy this gate.

**Verification**

- Run the targeted tests from Tasks 1–7, then `mvn -Dtest=FourEntryBusinessSemanticChainTest,SourceAnalysisCliContractTest,RepositoryAnalysisAgentStartTest test`.
- Run the repository's documented read-only documentation-link check and `git diff --check`.
- Stage one is releasable only when JDT has no JavaParser runtime/reference edge and all receipts/actual artifact sets match Tasks 4–6.

## Task 9: Adapt baseline JavaParser behavior to the frozen interface (approved 2.1)

**Files**

- Create: `src/main/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserCodeEngine.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserProjectSession.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserCatalogAdapter.java`
- Create: `src/main/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserContextAdapter.java`
- Modify: `src/main/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactory.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecution.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexPublicationSpecifier.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/fact/ProvenCodeFactsExecutor.java`
- Modify: `src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java`
- Create: `src/test/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserCodeEngineBaselineTest.java`
- Modify: the baseline tests named in the checklist below.

**RED**

- Luna runs the checklist below against `cec1997` first, records behavioral expectations, then expresses them through `JavaParserCodeEngineBaselineTest` and the neutral contract. New-contract fields derived directly from already parsed source (range, parameter order, complete method text) are required; unavailable semantic navigation is represented explicitly.
- Assert selecting JavaParser starts no JDT process. Assert no new wildcard/inheritance/overload repair, Symbol Solver project wiring, candidate invention, or hidden fallback is introduced.
- Assert JavaParser's existing module-6 graph set is retained as AVAILABLE technical enhancement; module 7 publishes the index plus those actually produced graph descriptors as the Step03 final set. Strict Step04 remains the current four-file route but uses fact-accounting v4 with `availability=AVAILABLE`, `reason=null`, and real counts.

**GREEN**

- Wrap the seven baseline JavaParser production classes listed below behind the neutral catalog/context adapter. Reuse their algorithms and tests; remove their direct ownership from common discovery/material code.
- Enable the exact `javaparser` factory value only after it writes the same final schema versions as JDT. Preserve current exact Fact/Proof meanings and the existing graph records; fill only honest unknown/limitation states for capabilities the baseline does not provide.
- Extend module-7/Step03 exact-set policy for the JavaParser variant (index plus the current seven graph semantic files). Do not mix results from JDT and JavaParser in one run.

**Verification**

- Run `mvn -Dtest=JavaParserCodeEngineBaselineTest,SpringHttpEntryDiscovererTest,MapperCapabilityCatalogerTest,CodeStructureGraphBuilderTest,CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,ProvenCodeFactsExecutionTest,BusinessFlowsExecutionTest,BusinessMaterialBuilderTest,FourEntryBusinessSemanticChainTest test`.
- Pass means every applicable baseline item remains observable, all outputs use the final contracts, and no assertion requires matching JDT counts, candidate sets, precision, or serialized hashes.

## Task 10: Run dual-engine regression and publish the selectable release (approved 2.2)

**Files**

- Modify: `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- Modify: `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- Create: `src/test/java/org/sourceanalysis/app/runtime/SelectableJavaEngineAcceptanceTest.java`
- Modify: `docs/modules/java-code-engines/README.md`
- Modify: `docs/modules/java-code-engines/integration-and-javaparser.md`
- Modify: `docs/DESIGN.md`
- Modify: `AGENTS.md`

**RED**

- Luna parameterizes the same snapshot/entry/business-material acceptance over `jdt` and `javaparser`; verifies engine descriptor/provenance, isolation, persisted round-trip, and the unchanged Activity/Process/Report consumers.
- Assert JDT errors never fallback; JavaParser never starts JDT; switching configuration requires a new run; cache/artifact reuse cannot cross engine descriptor/fingerprint.
- Compare invariants and baseline preservation only. Explicitly prohibit equality assertions for method count, candidate count, resolution rate, source breadth, strict enhancement availability, or JSON SHA.

**GREEN**

- Terra corrects only adapter/wiring/policy defects exposed by the matrix. Update authoritative docs to state both exact config values and their verified maturity.
- Keep the common contract JDT-shaped and neutral; do not weaken it to the JavaParser intersection and do not introduce voting, mixing, fallback, runtime plugin installation, or report redesign.

**Verification**

- Run `mvn -Dtest=SelectableJavaEngineAcceptanceTest,TechnicalAnalysisWorkflowTest,PersistedBusinessRunExecutorTest,FourEntryBusinessSemanticChainTest test` once per configured engine.
- Run the complete Task 9 baseline selector, the repository's documented read-only documentation-link check, and `git diff --check`.
- Release passes only when both engines use the same persisted reader/material chain and every engine-specific absence is explicit and valid.

## JavaParser pre-migration capability baseline at `cec1997`

This is a source-inspected acceptance checklist, not a claim that tests ran during plan authoring. Before Task 9 changes production behavior, run the named selectors at commit `cec1997` (or an isolated worktree at that commit), capture pass/fail and normalized fixtures, and use behavior—not byte/hash identity—as the migration oracle.

| Baseline owner at `cec1997` | Blob | Acceptance behavior to preserve | Selector |
| --- | --- | --- | --- |
| `MapperCapabilityCataloger.java` | `9ce24d5ca0f40db8b880a4901ea189e8559d15e0` | Mapper Java/XML association, standard DOCTYPE, namespace mismatch gaps, external-entity rejection | `MapperCapabilityCatalogerTest` |
| `SpringHttpEntryDiscoverer.java` | `563ec83ccdc73c9eaf8167a81137edf011b92577` | static class/method routes; GET/RequestMapping/unrestricted conditions; class+method union; import identity; dynamic/missing/multiple annotation gaps; deterministic shards | `SpringHttpEntryDiscovererTest` |
| `CodeStructureGraphBuilder.java` | `3158990f1fafc1de80f24f4f3e3f92bbcb9b98ce` | Java/MyBatis structure nodes, direct imports, exact source spans, malformed-Java and custom-entity gaps, configuration flattening | `CodeStructureGraphBuilderTest` |
| `CallGraphBuilder.java` | `7228f55cbe9b436e97859491e0d95ffc704ededc` | controller→service and service→mapper→XML calls; entry-owner union; distinct same-shaped call ranges; honest overload/ambiguous/import-decoy/unresolved gaps | `CallGraphBuilderTest` |
| `ControlFlowGraphBuilder.java` | `1ec57e676643d6fe56c2c5f2d9ce06bbe0764d21` | call/return traversal, guards and polarities, throws, exact spans, explicit loop limitation | `ControlFlowGraphBuilderTest` |
| `DataFlowGraphBuilder.java` | `2c8012d568e877f6381c806102921e9a4a61b6d8` | actual→formal, setter/local assignment, mapper-boundary arguments, external returns and gaps | `DataFlowGraphBuilderTest` |
| `BusinessMaterialBuilder.java` | `d6893592864041d7c12ed82839e2db1ae0f51aed` | current source/evidence material, source-reference mapping and path hiding | `BusinessMaterialBuilderTest` |
| Strict enhancement chain | baseline implementation | evidence source/rule closure; four-file strict facts; current five-file and zero-flow publication routes | `EvidenceGraphBuilderTest,ProvenCodeFactsExecutionTest,BusinessFlowsExecutionTest` |
| Existing run consumers | baseline implementation | persisted technical/business workflow and scripted Activity→Process→nine-chapter report | `TechnicalAnalysisWorkflowTest,FourEntryBusinessSemanticChainTest` |

Record separately whether a behavior is an intentional gap. Task 9 must preserve the gap as a limitation, not accidentally turn it into a fabricated target. The migration oracle excludes retired R0/finite-key interpretation behavior and does not require JavaParser to gain capabilities first delivered by JDT.
