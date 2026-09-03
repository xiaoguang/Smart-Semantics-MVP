# Progress: Proven code facts delivery

- Status: COMPLETE
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the approved `proven-code-facts` analysis step from persisted ProgramGraphs inputs through candidate enumeration, atomic Proof admission, and the five official artifacts. Do not change the eight-step architecture, infer external effects, or add runtime recovery.
- Approved inputs: `origin/main` at `e7811ca`, `docs/DESIGN.md`, `docs/analysis-steps/04-proven-code-facts.md`, both approved implementation plans, and the frozen Java-boundary rule.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`.

## Completed

- Created a clean branch from the ProgramGraphs checkpoint after its 90 direct tests, Spotless, and whitespace checks passed.
- Read the current fact-step design and implementation/toolchain plans before making a code change.
- Published the local M1 package-location clarification as docs-only `b89cb98` on `origin/main`, then fast-forwarded this worktree before implementation.
- Established and corrected the first Luna RED: the test fixtures must be canonical bytes; its first post-GREEN run reached a real set-versus-list comparison defect rather than hiding it.
- Terra implemented the bounded M1 candidate enumerator. The direct selector is green: 1 test, no failures/errors/skips; owned-file Spotless and `git diff --check` also pass.
- The M1 review found that this bounded slice used a cross-product over entry/boundary/template catalog values. Before adding Proof logic, the corrected Step 04 contract was published as docs-only `55b4810` and this worktree was fast-forwarded to it.
- The requested complete-five-graph M1 RED was deliberately stopped before it invented a fixture when M6 public `data-flow-graph.json` discarded the boundary variant fields it must provide. The existing Step 03 contract already required those fields; its audit correction was published as docs-only `3a3e787`.
- M6 is now repaired and published as `8dac8bc`: formal data-flow nodes retain required-nullable `boundaryInvocation` and `unknownBoundaryReturn` records. This worktree was fast-forwarded to that commit without overwriting the temporary M1 files.
- The first exact-path RED correctly stopped rather than manufacture an unsupported graph: current test fixtures cover either two entries sharing one boundary or one entry with two boundaries. A narrowly named test-only graph fixture may now construct the missing two-entry/two-boundary publication through the real builders/stores. It adds no product API, artifact field, or architecture rule.
- Terra's formal M1 implementation compiles, but its one direct selector stopped before the M1 seam because the test-only graph fixture passed a non-existent child of JUnit's temporary directory to the store bootstrap. Sol/debug is correcting that fixture to create the required empty, non-symlink child directory; this is test infrastructure only and does not alter graph, Fact, or external-boundary contracts.
- Corrected two further test-only fixture details exposed by that first runtime path: the source/discovery publication modules use their registered `publish` addresses, and the public control/data/evidence artifact-policy prefixes match the public graph publisher. These repairs preserve all canonical identity validation rather than weakening it.
- Sol/debug found one production M1 reader defect: it compared the typed `ArtifactPolicyRegistryReference` in `ArtifactControls` with a generic `ArtifactReference`, which can never be equal. Terra changed only that reader to parse and compare the same typed registry reference while retaining exact `{artifactId,sha256}` validation. The exact-path selector now passes independently.
- Luna added a real persisted-mutation test: a fresh run republishes the two-entry graph set with only the approval argument edge's evidence IDs removed, recomputes affected artifact identities, and reopens typed inputs. It unexpectedly passes because M1 already emits the specified one scoped `NOT_APPLICABLE` and preserves the other candidate. The test is retained as a regression.
- Completed the independent M1 review. It found no P0, but confirmed six P1 implementation gaps: program-edge endpoint closure; graph/profile/source lineage cross-checks; source-excerpt hash and locator validation; candidate-set identity coverage; boundary ownership accounting; and Evidence node-versus-edge support typing. These are local implementations of the existing M1 contract, not a change to the eight-step architecture or the frozen-Java boundary.
- Closed the first confirmed P1: `FactCandidateSet` now derives its identity from a versioned, length-framed encoding of every wire-visible candidate, root and scoped-disposition field. The dedicated RED changed from three semantic-identity failures to green, while the two-entry exact-path and isolated missing-evidence regressions remain green.
- Added the next persisted public-wire RED: a complete canonical ProgramGraphs publication whose `CALL_TARGET.toNodeId` is a well-formed but nonexistent node is currently accepted by M1. The required correction is limited to complete cross-graph endpoint closure before any candidate enumeration; it does not require source re-parsing or any external-system inference.
- Closed that endpoint defect: the reader now validates every non-Evidence program edge against the complete cross-graph program-node universe, including an optional guard. The ghost-endpoint RED is green and the related four-test selector remains green.
- Closed the Evidence support-kind defect: persisted Evidence edges now retain their declared node/edge support kind, are checked against the matching public graph universe before input return, and the enumerator asks for the matching kind per subject. The deliberate edge-as-node mutation is green along with the five direct M1 tests.
- Closed the source-excerpt wire defect: the reader now constructs the existing `SourceLocatorV1` and `SourceExcerptV1` values for every persisted source evidence node, so invalid repository paths/ranges and byte-to-hash drift fail before candidate enumeration. The six direct M1 tests are green.
- Closed the graph-profile lineage defect: the reader retains the profile reference from all four program graphs and the Evidence graph, then requires them and the graph-index reference to be identical. A syntactically valid decoy profile is rejected; seven direct M1 tests are green.
- Closed the graph-index descriptor defect: every descriptor is now compared field-for-field with its parsed graph payload—kind, filename, artifact type, schema, graph identity and content reference. A decoy descriptor is rejected; eight direct M1 tests are green.
- Closed the boundary-denominator defect: a `JAVA_BOUNDARY_INVOCATION` node must name at least one owning entry before fresh input is returned. A malformed boundary is now rejected as `PROOF_PACK_REFERENCE_BROKEN`; ordinary graph nodes retain their general empty-owner behavior. The nine direct M1 tests are green.
- Established the next exact RED without changing production code: a test-only re-publication changes only `application-profile.json.sourceInventoryRef` to a valid-looking decoy while preserving source/discovery/graph run lineage, schemas and controls. The fresh reader currently accepts it, proving that discovery-to-source payload identity still needs a fail-closed check.
- Closed that discovery-to-source lineage defect: the reader derives the two expected content-addressed source references from the reopened verified-source payload descriptors and compares them exactly to the application profile before enumeration. The new selector and the ten direct M1 tests are green.
- Added the registry determinism regression. Equivalent template ordering leaves the candidate bytes, identity and denominator unchanged; deleting a template removes only its own entry/boundary combinations; and required-atom declaration order is preserved as candidate semantics. All three checks are already green, so no production change was required.
- Established and closed the M1 module-artifact seam. `FactCandidateSetModulePublisher` now installs exactly one canonical `fact-candidate-set.json` at `PROVEN_CODE_FACTS/01-candidates`, with the five graph roots, candidate/disposition denominator and closed envelope; it fresh-reopens before returning. The Foundation registry was missing this already-designed artifact contract, so the exact type/schema/address/file registration was added without changing the design. Fourteen direct M1 tests are green.
- Established the typed M1 persisted-reader seam and corrected its test fixture to use the same policy/controls as the predecessor graphs. The fresh reader correctly validates the canonical module receipt/envelope/body and re-enumerates the typed candidate set; its direct tamper test is green. The full selector initially exposed an unrelated missing-evidence fixture policy drift, which was traced to a duplicate test registry and corrected without changing production; the two affected tests are green.
- Published `ef964f4` to record the verified M1 checkpoint without calling it a proven Fact or a repository result.
- During the M2 design read, found a local M1-to-M2 handoff loss: the typed Evidence projection validates source excerpts but discards their bytes/rule metadata, and candidate closure omits call-site/local-origin subjects. Published the exact repair contract as `e7811ca`; this does not change the eight-step architecture, artifact cardinality, frozen-Java boundary, or external-effect Gap rule.
- Luna established the correction RED: an applicable candidate omitted its call-site and Java-local-origin evidence subjects. Terra retained the exclusive `SourceExcerptV1`/rule-application union in the persisted reader and closed the required subject set. The handoff test is green and the 17-test M1 selector is green, with scoped Spotless and whitespace checks passing.
- Two independent Luna review attempts failed before execution with the same platform `404` response. Sol performed a read-only fallback review of the exact correction: the union is exclusive, reader propagation and subject closure are fail-closed, candidate identity already covers added bindings, and no external effect/path/compatibility behavior was introduced. No corrective diff is required.
- Published the candidate-instance identity correction as docs-only `a8d8187` and fast-forwarded it to `origin/main` before the M2 implementation begins. A template key is not unique across entries, so every M2 disposition, Proof and external-boundary Gap uses `entryId|boundaryNodeId|candidateFactKey`.
- The M2 public-seam RED is established. `AtomicProofBuilderTest` fresh-reopens the real two-entry ProgramGraphs fixture and M1 candidate set, then requires two admitted Facts, sixteen closed atom Proofs, no root-cause rejection and two `DATA_FLOW_BINDING_UNPROVEN` external-boundary Gaps. It fails exactly because the public `ProofRuleRegistry` and `AtomicProofBuilder` production types do not yet exist (1 test, 1 assertion failure, 0 errors/skips).
- A Terra implementation dispatch failed before work began with the same external `404` service response as the prior Luna attempts. Under the already-approved bounded fallback, Sol implemented only the published M2 public seam after the verified RED. The first live run exposed an actual Evidence-graph fact: one subject has multiple historical rule paths, and a Java parameter node's byte evidence is `source-element-parser-v1`. The corrected, docs-first contract is published as `25f4638`; M2 now chooses one exact allowed Evidence edge pair per required subject and only allows that parser rule for the local-origin node while the argument-to-boundary edge still proves the relation.
- The M2 positive public seam is green: the real persisted two-entry fixture produces 2 admitted Facts, 16 CLOSED atom Proofs, no root causes and 2 external-boundary Gaps. It does not add an SQL/API/message effect claim.
- A Luna source/rule-drift test dispatch also failed before it could write files with the external `404` service response. Sol added the next bounded regression directly: a reopened text set with every document's bytes replaced—but valid file SHA metadata—must fail `PROOF_SOURCE_REOPEN_MISMATCH` when the original Evidence excerpt is revalidated. It passes alongside the positive proof and M1 handoff selectors.
- Published the M2 artifact-lineage correction as docs-only `cca0646` and then the typed-reader input correction as `d2290ca`. M2 now persists its M1 candidate payload plus the exact source inventory and snapshot references; its reader must reopen all of them rather than trust a caller-supplied decision object.
- Added and closed the M2 persisted-reader RED: `PersistedProofDecisionSetReader` fresh-reopens the exact M1 candidate module and M2 proof module, validates all receipt/envelope/upstream fields, reconstructs the typed decision set, and checks candidate/atom/Fact/Proof/Gap conservation before returning it. Its direct test is green.
- Published the M3 four-payload wire contract as docs-only `3d845c1` and the exact M3 public seam as `ff4b6d2` before implementation. M3 now maps an already-closed M2 decision set into four standalone semantic files and the receipt-last analysis-step publication; it does not re-prove source or claim an external effect.
- The M3 public RED was closed. `FactLedgerPublicationSpecifier` fresh-reopens M1/M2 and source/discovery/graph predecessors, installs a single M3 module with the exact four semantic payloads, then installs the four-file analysis step and receipt. The positive two-boundary fixture yields two internal Facts plus two external-effect Gaps; the rejection regression yields no Fact, two fact-rejection Gaps and two external-effect Gaps. Both cases are green.
- Final stage review corrected the ledger's internal candidate/atom separator to a real NUL separator rather than six literal characters. The M1 15-class selector, M2/M3 selector, explicit-file Spotless check and whitespace check all pass; no unsupported external effect was introduced.

## Current state

- 已安装的M1候选清单会从fresh-reopened应用发现与完整五图建立现有entry/call/argument/control/Evidence闭合，安装唯一canonical `fact-candidate-set.json`，再由独立reader重开、重枚举并校验七项准确上游引用。其16个定向测试仍通过。
- M1现在是可用的M2 Proof handoff：每个candidate绑定完整subject ID集合，fresh-reopened input保留其对应`SourceExcerptV1`或rule application payload。M1仍没有Proof、admitted Fact、Fact Gap ledger或本步骤五项正式输出；这些仍分别是M2和M3职责。
- M2的最小public-seam RED已经确认，Terra正在只实现`analysis.fact.proofs`的正向原子闭合。此实现不会重扫源码、不会推断Java边界之外的效果，也不会把模板key误当成候选实例身份。
- M1 candidate enumeration, M2 atomic Proof and typed decision persistence, and M3 four-file Fact ledger publication are complete. The step produces five observable reader-visible files: four canonical JSON semantic artifacts plus the receipt. It is ready for its single stage commit and push.

## Changed files

- `analysis/fact/candidates` production types, one M1 public RED test, the test-only persisted ProgramGraphs fixture, and their task progress files.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | clean worktree before this progress record |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest test` | PASS | 3 tests; candidate identity closure, exact two-entry/two-boundary join, and isolated missing-evidence disposition |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest test` | PASS | 4 tests; adds complete cross-graph endpoint closure |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest test` | PASS | 5 tests; adds typed Evidence node/edge closure |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateSourceExcerptIntegrityTest test` | PASS | 6 tests; adds persisted source-excerpt locator and digest closure |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateSourceExcerptIntegrityTest,FactCandidateIdentityTest,FactCandidateGraphProfileReferenceTest test` | PASS | 7 tests; adds graph-profile lineage closure |
| M1 nine-test selector | PASS | adds graph-index descriptor closure and Java-boundary ownership closure; exact selector retained in `progress/fact-candidate-boundary-ownership-green.md` |
| M1 ten-test selector | PASS | adds application-profile source inventory/snapshot lineage closure; exact selector retained in `progress/fact-candidate-discovery-lineage-green.md` |
| `FactCandidateRegistryDeterminismTest` | PASS | 3 tests; registry ordering/deletion and required-atom ordering behavior |
| M1 candidate module selector | PASS | 14 tests; adds canonical `fact-candidate-set.json` install and fresh-reopen seam |
| changed-file `spotless:check` and `git diff --check` | PASS | no formatting or whitespace errors |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/fact/candidates/*.java' spotless:check` | PASS | owned production files formatted |
| `git diff --check` | PASS | no whitespace errors |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AtomicProofBuilderTest test` | EXPECTED RED | 1 test, 1 assertion failure, 0 errors/skips; `ProofRuleRegistry`/`AtomicProofBuilder` are absent |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AtomicProofBuilderTest test` | PASS | 1 test; 2 admitted Facts, 16 CLOSED atom Proofs and 2 external-boundary Gaps |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AtomicProofBuilderTest,AtomicProofBuilderSourceDriftTest,FactCandidateProofEvidenceHandoffTest test` | PASS | 3 tests; positive closure, source-byte drift rejection and M1 Evidence handoff |
| scoped Spotless and `git diff --check` | PASS | M2 production/tests are formatted with no whitespace errors |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=PersistedProofDecisionSetReaderTest test` | RED → PASS | missing public reader, then 1 test verifies fresh M1/M2 lineage reopen |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=ProvenCodeFactsPublicationSpecifierTest test` | RED → PASS | 2 tests; exact four semantic files / receipt and rejection-to-Gap projection |
| M1 15-class selector | PASS | candidate, identity, Evidence, source, graph and persisted-reader regressions all pass after the M3 fixture policy extension |
| M2/M3 five-class selector | PASS | 6 tests; Proof closure, drift, M2 publication/reopen and Fact ledger publication all pass |
| explicit-file Spotless and `git diff --check` | PASS | source/test files and Foundation changes are formatted; one attempted recursive Spotless glob was rejected by the plugin before formatting and was rerun with explicit files |

## Decisions

- Keep the approved generic frozen-Java boundary: a Fact may prove the Java invocation and its Java-local arguments, but it cannot claim an external database/message/search/API effect.
- Correct malformed fixture bytes in the test layer; do not weaken `CanonicalJsonCodec` or make production accept noncanonical input.

## Blockers

- None. M2必须按`e7811ca`与`a8d8187`的逐atom source/rule/edge closure及candidate-instance identity建立Proof；它仍不能从候选清单推断外部系统效果。

## Exact next action

- 从新的`main`创建分析步骤“业务流程”的分支，先只读取本步骤四个semantic artifacts和五图，按入口编译Flow与Evidence Capsule；不得重新扫描源码或升级external-effect Gap。

## Resume checks

- 确认新RED只经fresh-reopened M1 candidate artifact、真实canonical ProgramGraphs publication和冻结source reader构造输入；不得以raw JSON、私有字段、模型或外部效果断言代替。
