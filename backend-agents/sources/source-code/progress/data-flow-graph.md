# Progress: data flow graph

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M4 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Build the bounded, evidence-carrying data-flow graph from the same sealed structure, call,
  and control-flow graphs. Start with exact Java call arguments to target parameters; expand only by
  separately verified adjacent edges.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, both
  implementation plans, and the sealed M1–M3 reopening contracts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at
  `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the M4 target contract and confirmed that M4 consumes only freshly reopened M1/M2/M3
  artifacts and their shared verified source basis; it cannot accept raw drafts, a caller path,
  detached source text, or a fallback scan of a worktree.
- Established the expected public-seam RED: the first data-flow test failed only because the
  builder/input/profile/draft did not exist.
- Implemented and passed the first exact transfer: an activated Java `NameExpr` actual argument
  binds to the target method's M1 parameter endpoint by the proven M2 call target and zero-based
  ordinal. The M1 parameter canonical identity now binds its declaring method node, ordinal, and
  declared canonical type; parameter spelling is not a key.
- Established and closed the persistence RED: a data-flow graph publishes as
  `data-flow-draft.json`, then fresh-reopens only with the exact same M1/M2/M3 payload lineage.
- Added the first capability-boundary GREEN: a literal actual argument, which the installed
  NameExpr-only rule does not yet support, produces one `DATA_FLOW_BINDING_UNPROVEN` typed Gap
  and two coverage dispositions (prospective argument node and binding edge) instead of a guessed
  edge or fatal reference error.
- Added a persisted-wire boundary test: a canonical-looking M4 edge redirected to an unknown
  external endpoint is rejected during fresh reopen with `GRAPH_REFERENCE_BROKEN`; reader validity
  is therefore stronger than JSON shape and digest validity alone.
- Added the complementary work-domain boundary: the reader independently rebuilds M4 from the
  fresh M1/M2/M3 predecessors and rejects a saved graph whose call-argument worklist has an extra
  or missing item. This protects the denominator used by all later fact and coverage accounting.
- Corrected the M4 graph identity so it binds the complete emitted node/edge content, worklist,
  local gaps, and coverage—not merely the IDs of exact nodes and edges. The reader's independent
  rebuild remains the separate on-disk tamper check.

## Current state

- The bounded argument-to-parameter rule and independent persistence/reopen seam are green. This
  slice intentionally has no field, setter, SQL placeholder, criteria, or column inference yet.

## Changed files

- `progress/data-flow-graph.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlow*.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ReopenedDataFlowGraph.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedDataFlowGraphReader.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Expected RED | Test compile fails only because `DataFlowGraphDraft`, `DataFlowInputs`, `DataFlowGraphProfile`, and `DataFlowGraphBuilder` do not exist. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 3 tests, 0 failures/errors/skips: exact binding, fresh publication/reopen, and the typed unproven-binding Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips: adds rejection of an unknown external parameter endpoint on fresh reopen. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 5 tests, 0 failures/errors/skips: adds independent reconstruction and rejection of a forged worklist denominator. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 5 tests, 0 failures/errors/skips after graph identity was extended to include accounting, Gap, and coverage content. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest,ControlFlowGraphBuilderTest,CallGraphBuilderTest,CallGraphModulePublisherTest test` | PASS | 25 tests, 0 failures/errors/skips after the first data-flow publication slice. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest,ControlFlowGraphBuilderTest,CallGraphBuilderTest,CallGraphModulePublisherTest test` | PASS | 28 tests, 0 failures/errors/skips after independent M4 rebuild/reopen validation. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Changed data-flow/control-flow files to project format. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Each long DepotHead value chain is assembled only from typed adjacent edges. A missing or
  ambiguous adjacent edge becomes a Gap; M4 never uses a same-name shortcut.
- This bounded vertical slice begins with a Java call argument-to-parameter edge. XML, property,
  criteria, placeholder, and column edges each require their own RED and provenance rule.

## Blockers

- None.

## Exact next action

- Await the Sol/ultra-published exact contract for M4's next intra-method/property transfer slice,
  then fresh-rebase and establish its RED. Keep SQL/property transfers out of scope until their
  own evidence-backed tests exist.

## Resume checks

- Read this file, run `git status --short`, confirm the M3 direct selector remains green, then run
  only the M4 selector recorded after the RED is added.
