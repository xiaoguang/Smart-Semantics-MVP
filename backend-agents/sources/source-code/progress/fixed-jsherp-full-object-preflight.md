# Progress: fixed jshERP full object preflight

- Status: IN_PROGRESS
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Obtain and verify a complete local Git object set for the already approved fixed jshERP commit, solely so the existing offline capture can later create a truthful frozen input. Do not run customer Maven, tests, scripts, application, a live Provider, or a source analysis step.
- Approved inputs: Scoped `AGENTS.md` fixed jshERP read-only offline capture authorization; commit `8c30ce7861570458920175e200bb2a6442713580`; ignored `.workspace/` only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the currently known local checkout is a promisor/partial Git repository, which the LocalGitCommitCaptureAdapter correctly refuses rather than lazy-fetching.

## Current state

- Checking the approved upstream only to determine whether the fixed commit can be made available as a new, full local object database under this Agent's ignored workspace.
- The isolated no-checkout clone now resolves the exact commit and tree. It is not partial, shallow, alternate-backed, or replace-backed; `git fsck --connectivity-only --no-dangling` passed and the commit tree contains 719 tracked entries.
- The opt-in Step01–05 selector reached inventory M1 after a successful local capture, then failed before M1 installation because the checked-in acceptance configuration registers the M1 artifact ID prefix as `request-admission`, while the stable publisher, store tests, and detailed design all use `source-request`.
- Repaired that acceptance-fixture policy identity and re-ran from a new ignored workspace: Capture plus all Step01 publications now succeed for the full 719-file repository.
- The first remaining execution failure is Step02 before entry discovery. The profile detector recognizes only the direct `spring-webmvc` and two MyBatis artifact IDs. The verified jshERP POM instead declares the standard `spring-boot-starter-web` and `mybatis-plus-boot-starter` dependencies; its controller sources use `@RestController` and Spring mapping annotations. This is a generic framework-recognition omission, not a customer-specific business rule.
- Added a direct RED/Green for those two standard, direct Maven starter dependencies. The detector now recognizes them as Spring MVC and MyBatis capabilities; the focused detector suite is green (8 tests).
- A fresh full-repository retry now detects 337 HTTP entries and 61 Mapper catalog entries, then fails while publishing the Step02 result. Retaining the wrapped cause showed that M4 computed its standalone/JSONL artifact IDs using hard-coded prefixes while the active policy registry is authoritative for those prefixes.
- The publisher now resolves the effective prefix from the trusted module store's current registry before calculating each M4 artifact ID. Existing unit policies with short prefixes and the fixed acceptance registry with longer prefixes both pass their direct checks; no business source rule or artifact payload format changed.
- A fresh full-repository retry now completes the full source inventory and application-discovery step. It persists all four Step02 files with `SUCCEEDED_WITH_GAPS`, including 337 discovered HTTP entries and 61 Mapper catalog entries. The first later failure is a generic program-graph defect: CodeStructureGraphBuilder assigns a duplicate node ID while parsing static SQL in Mapper XML.
- Root-cause inspection is complete: a SQL table/column node identity deliberately omits the span so that one semantic table/column can be shared within a Mapper XML, but `GraphAccumulator.node` rejects a second, different provenance reference rather than accumulating it. The graph model already permits a node to own an ordered set of provenance drafts; statement-to-table edges retain the individual SQL occurrence. This is the smallest generic correction to test.
- The new direct repeated-static-SQL test reaches the real builder and fails exactly with `CODE_STRUCTURE_DUPLICATE_NODE_ID` at the second `UPDATE jsh_depot_head SET status` occurrence. It confirms the stated cause before any production change.
- The graph accumulator now merges provenance only for a node whose ID, kind, canonical value and owners are otherwise identical. The repeated-static-SQL test and the full structure-builder selector are green (8 tests), so the generic source-occurrence correction is closed locally.
- The corrected opt-in acceptance reached program-graphs M1 publication instead of failing during XML parsing. It now fails at the canonical module store with `MODULE_INSTALL_REQUEST_INVALID` from `CodeStructureGraphModulePublisher`; the next task is to trace the module envelope/policy mismatch before making any change.
- M1 root cause is confirmed: the active frozen registry assigns the `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT` payload prefix `code-structure`, while the publisher hard-codes `code-structure-graph`. The canonical store correctly rejects the disagreement. A direct publisher test with a `code-structure` policy fails at the same boundary; all other graph-draft policy prefixes currently match their publishers.
- `CodeStructureGraphModulePublisher` now resolves the prefix from the trusted module store policy before computing the envelope identity. The configured-prefix publisher test and the source-occurrence builder test are green together (10 tests).
- The next fixed-repository retry completed and fresh-reopened M1, then reached call-graph M2. Its first failure is `GRAPH_REFERENCE_BROKEN` in `CallGraphBuilder.InputIndex.structureMethodNode` while resolving a discovered call. This is now the only active acceptance blocker; the failure occurs after the repaired M1 publication.
- The M2 mismatch is now traced to type-name construction, not to an unsupported source file. `CodeStructureGraphBuilder` qualifies simple method parameter types using only the declaring package, while `CallGraphBuilder` uses direct explicit imports. The persisted M1 output contains, for example, `com.jsh.erp.datasource.vo.DepotHeadVo4StatementAccount#setBeginNeed(com.jsh.erp.datasource.vo.BigDecimal)` although the fixed source directly imports `java.math.BigDecimal`. Therefore a later direct call can resolve a different target signature than the M1 node. Both builders must share the explicit-import rule for direct imports.
- The direct imported-parameter regression was RED exactly as expected: M1 emitted `com.example.ImportedAmountHandler#record(com.example.BigDecimal)` instead of `...#record(java.math.BigDecimal)`.
- M1 now collects direct, non-static, non-wildcard Java imports once per compilation unit and uses that immutable import map for method and parameter type spelling. This aligns its declaration identities with M2 without classpath lookup or wildcard guessing. The direct M1/M2 regression selector is green (29 tests).
- The first retry after that repair did not enter analysis because its explicitly configured, ignored output workspace had not yet been created; the opt-in acceptance test correctly refuses an unresolved workspace path before writing. This is an execution-environment precondition, not an analysis result or production defect.
- After the exact workspace was created, the full run again completed capture, inventory and discovery, then exhausted the default 4 GiB JVM heap while executing the complete program-graphs step. The report preserves all earlier publications and correctly marks graph/fact/flow work as not completed. This is an acceptance execution resource limit; no analysis result is being misreported as complete.
- A single bounded replay with a 6 GiB Surefire JVM heap reached the same Step03 full-graph resource boundary. Per the closeout rule, this is recorded as a performance backlog item rather than expanded into a new graph-storage or memory-management subsystem in this work unit.
- Root has explicitly authorized one further unchanged offline replay with an 8 GiB JVM heap. It uses a new empty ignored workspace and the existing opt-in selector only; it does not change graph code, run customer code, access the network, or invoke a model.
- The 8 GiB replay passed the former resource boundary: capture, Step01 and Step02 completed, and
  complete Step03 reached control-flow construction. It then exposed a generic index mismatch:
  nested generated classes were indexed despite having no code-structure/call-graph nodes. The
  direct regression and the complete control-flow suite are green after aligning that index to the
  same top-level declaration domain. A new clean workspace is required for one replay of this
  single correction.
- That replay passed the nested-type correction and reached entry traversal. It then exposed the
  next generic signature mismatch: control flow did not apply a direct Java import while resolving
  a persisted method node. The direct regression and complete control-flow suite are green after
  applying the same import spelling rule already used by the structure and call graphs.
- A fresh 8 GiB replay then completed the structure, call, and control graphs for the full
  repository. It stopped while constructing the data-flow graph because the draft's provenance
  closure check sees a mismatch between declared and referenced evidence IDs. The next work is
  diagnosis only: identify whether the builder imports unused provenance or emits a node/edge
  reference whose provenance was never imported, then add a minimal generic regression.
- The no-argument-boundary correction allowed M4 to construct the full data-flow draft. The next
  failure was a generic publisher error: a single Gap with several candidate elements repeated the
  same ID in the receipt. The publisher now canonicalizes one receipt reference per logical Gap;
  direct data-flow tests remain green.
- A later 8 GiB replay built all five graph drafts and reached the final public graph publication.
  It exposed one remaining generic identity defect: the public data-flow graph used a hard-coded
  prefix although the active artifact policy deliberately configured `data-flow-graph`. The M6
  publisher now resolves every public payload prefix through the trusted policy registry; the
  direct public-payload, Gap-projection and wire tests are green.
- The immediate 8 GiB replay of that correction made no further report or error after roughly
  sixteen minutes, substantially longer than the preceding 428-second full run. It was interrupted
  as a host-throughput/performance result, not recorded as a passed or failed analysis result.

## Changed files

- `progress/fixed-jsherp-full-object-preflight.md`
- `src/test/resources/analysis/graph/code-structure/src/main/resources/mapper/RepeatedStatusMapper.xml`
- `src/test/resources/analysis/graph/code-structure/src/main/java/com/example/ImportedAmountHandler.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilderTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphModulePublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphModulePublisherTest.java`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| local checkout inspection | PASS | Existing source is partial/promisor and not eligible for a truthful capture. |
| approved upstream availability | PASS | Remote was reachable; a new isolated no-checkout clone was made under ignored `.workspace/`. |
| local object preflight | PASS | Exact commit/tree resolve; no partial/shallow/alternate/replace input; connectivity fsck passed; 719 tracked entries. |
| opt-in fixed-repository Step01–05 selector | BLOCKED_AT_M1 | Capture completed; M1 exposed the acceptance configuration's `request-admission`/`source-request` policy-prefix inconsistency before discovery or later analysis ran. |
| fixed-repository configuration guard | PASS | The repaired canonical acceptance configuration now names `source-request`, matching the production publisher and artifact policy. |
| opt-in fixed-repository Step01–05 retry | BLOCKED_AT_STEP02 | Capture: 719 tracked files, 686 text, 33 media; Step01 M1–M3 publications succeeded. Application discovery rejected the profile because standard Spring Boot/MyBatis Plus starters were not recognized. |
| `ApplicationProfileDetectorTest` | PASS | 8 tests; direct Spring Boot Web/MyBatis Plus starter capability test is green. |
| opt-in fixed-repository Step01–05 retry 2 | BLOCKED_AT_STEP02_M4 | Full capture and Step01 passed; discovery produced 337 HTTP entries and 61 Mapper entries, then Step02 publication threw the stable but cause-less `APPLICATION_DISCOVERY_PUBLICATION_INVALID`. |
| discovery publication root-cause replay | DIAGNOSED | Cause: `MODULE_INSTALL_REQUEST_INVALID` because the active policy's artifact ID prefix and the publisher's fixed prefix differed. |
| `ApplicationProfileDetectorTest`, `ApplicationDiscoveryPublicationSpecifierTest`, acceptance configuration guard | PASS | 13 focused tests; direct/core test policy and fixed acceptance policy both validate under policy-resolved M4 identities. |
| opt-in fixed-repository Step01–05 retry 4 | STEP01_AND_STEP02_PASS; BLOCKED_AT_STEP03 | Capture 719 files; Step01 complete; Step02 installed application profile, capability report, 337 entries and 61 Mapper catalog candidates. Step03 stopped at `CODE_STRUCTURE_DUPLICATE_NODE_ID` while parsing static SQL. |
| `CodeStructureGraphBuilderTest#accumulatesExactOccurrencesWhenTwoStaticStatementsUseTheSameTableAndColumn` | RED | 1 test reached the real builder and failed with `CODE_STRUCTURE_DUPLICATE_NODE_ID` at the second identical table/column occurrence. |
| `CodeStructureGraphBuilderTest` | PASS | 8 tests; one semantic SQL table/column node now retains two exact provenance references while two statement edges remain distinct. |
| scoped Spotless check | PASS | The changed graph builder and direct test satisfy the configured Java formatter. |
| opt-in retry 5 selector | NOT_EXECUTED | Maven accepted an outdated method selector and ran zero tests. No acceptance result was produced; the correct opt-in method is `runsTheFrozenStep01Through05ChainOnlyWhenExplicitlyOptedIn`. |
| opt-in fixed-repository Step01–05 retry 6 | STEP01_AND_STEP02_PASS; BLOCKED_AT_STEP03_M1_PUBLICATION | The structure builder now completed enough to publish, then `CodeStructureGraphModulePublisher` failed canonical envelope validation with `MODULE_INSTALL_REQUEST_INVALID`; no later fact/flow step ran. |
| `CodeStructureGraphModulePublisherTest` with active `code-structure` prefix | RED | 2 tests, 1 expected M1 module-envelope error: publisher computes a hard-coded `code-structure-graph` ID instead of using the trusted active policy. |
| `CodeStructureGraphModulePublisherTest`, `CodeStructureGraphBuilderTest` | PASS | 10 tests; M1 uses the active policy prefix and retains repeated SQL occurrence provenance. |
| scoped Spotless check | PASS | The changed graph builders, publisher and direct tests satisfy the configured Java formatter. |
| opt-in fixed-repository Step01–05 retry 7 | STEP01/STEP02/M1 PASS; BLOCKED_AT_STEP03_M2 | Full source inventory, application discovery and code-structure M1 complete/fresh-reopen. Call-graph M2 stops with `GRAPH_REFERENCE_BROKEN` at `CallGraphBuilder.InputIndex.structureMethodNode`; no later graph/fact/flow step ran. |
| `CodeStructureGraphBuilderTest#qualifiesDirectlyImportedMethodParameterTypesUsingTheirImportedFqn` | RED | M1 emits the declaring-package `BigDecimal` rather than the direct import's `java.math.BigDecimal`. |
| `CodeStructureGraphBuilderTest`, `CodeStructureGraphModulePublisherTest`, `CallGraphBuilderTest` | PASS | 29 tests; M1 and M2 now use the same direct-import spelling rule, including the new `java.math.BigDecimal` regression. |
| opt-in fixed-repository Step01–05 retry 8 | NOT_EXECUTED | The requested ignored output workspace did not exist; the test stopped before capture or analysis. |
| opt-in fixed-repository Step01–05 retry 8 (after workspace creation) | BLOCKED_AT_STEP03_RESOURCE | Capture, Step01 and Step02 completed for all 719 files; complete program-graph execution ended with `OutOfMemoryError: Java heap space` before Step03 publication. |
| opt-in fixed-repository Step01–05 retry 9 (`-Xmx6g`) | BLOCKED_AT_STEP03_RESOURCE | The larger bounded test heap reached the same complete-program-graphs `OutOfMemoryError`; no later fact/flow result was produced. |
| opt-in fixed-repository Step01–05 retry 10 (`-Xmx8g`) | BLOCKED_AT_STEP03_CONTROL_INDEX | Capture, Step01 and Step02 succeeded; resource limit cleared; control-flow index rejected duplicated nested generated `Criterion` signatures before Step03 publication. |
| opt-in fixed-repository Step01–05 retry 11 (`-Xmx8g`) | BLOCKED_AT_STEP03_CONTROL_SIGNATURE | Capture, Step01 and Step02 succeeded; nested declaration index passed; control flow could not match an entry parameter with a direct Java import to the persisted structure method node. |
| opt-in fixed-repository Step01–05 retry 12 (`-Xmx8g`) | STEP03_M1_TO_M3_PASS; BLOCKED_AT_STEP03_M4 | The full repository completed structure, call, and control graph construction without an OOM; data-flow draft validation rejected non-closing provenance references before Stage03 publication. |
| opt-in fixed-repository Step01–05 retry 15 (`-Xmx8g`) | STEP03_M1_TO_M4_BUILD_PASS; BLOCKED_AT_STEP03_M4_PUBLICATION | The full data-flow draft constructed; its module publisher rejected duplicate receipt references for a single Gap with two candidate elements. |
| opt-in fixed-repository Step01–05 retry 18 (`-Xmx8g`) | STEP03_M1_TO_M5_BUILD_PASS; BLOCKED_AT_STEP03_M6 | All five graph drafts completed; public data-flow graph identity used a stale hard-coded prefix instead of the active policy value. |
| public M6 policy regressions | PASS | 3 direct tests; public graph, evidence, Gap and index prefixes are resolved from the trusted registry. |
| opt-in fixed-repository Step01–05 retry 19 (`-Xmx8g`) | INTERRUPTED_FOR_PERFORMANCE | No additional report/error after about sixteen minutes; stopped to avoid treating indefinite host throughput as useful verification. |

## Decisions

- Any later analysis consumes only the capture manifest and opaque registration produced from complete local objects; this preflight itself does not analyze the repository.
- Treat the M1 configuration mismatch as a test-resource contract defect. It is repaired and guarded.
- Detect supported frameworks from their direct, standard Maven starter artifacts. Do not add customer package names, business rules, or a parent-POM resolver merely to recognize an unambiguous direct dependency.
- SQL table/column nodes represent a semantic declaration within one frozen Mapper document and may therefore carry multiple exact provenance references. Per-statement edges remain occurrence-specific; the fix must merge only otherwise identical node metadata and must not merge conflicting kinds, values, owners, files, or rules.
- Direct non-wildcard Java imports are part of the stable static type spelling used by both M1 and M2. A simple imported parameter type must map to the explicit imported FQN; no classpath lookup, wildcard import resolution, Maven execution, or customer-specific rule is needed.
- Complete-repository Step03 currently needs a bounded-memory/streaming design before it can be used as an all-719-file acceptance path. This is a performance backlog item, not evidence that Steps01/02 or the small-slice graph contracts are invalid.

## Blockers

- None. The Step02 framework-recognition repair is local, generic and covered by a direct test before the acceptance retry.

## Exact next action

- Do not repeat an unchanged full 8 GiB replay. Preserve retry 19 as a performance observation and
  continue direct contracts until a separate, bounded performance hypothesis is approved.

## Resume checks

- Before capture, verify the clone has no promisor, alternate, shallow, replace, or graft input and can resolve the full commit object without network access.
