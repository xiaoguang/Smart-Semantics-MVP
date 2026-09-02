# Progress: program graph call graph

- Status: IN_PROGRESS
- Agent role: Terra/xhigh production implementation under the approved Sol/ultra M2 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Program Graphs M2 only — exact static CallGraphBuilder records and public-seam tests
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `7beb3ff`, and the M1 fresh-input boundary
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the final M2 input clarification published by Sol/ultra to `origin/main` and rebased the code branch onto it.
- Added a first public-seam fixture consisting of frozen Controller, Service, Mapper Java, and MyBatis XML resources. It asks only for one exact Controller-to-Service call target and its paired return edge.
- Ran the direct M2 selector. Its RED is limited to the absent CallGraph public types; it has not reached parser behavior.
- Implemented the first typed Call Graph slice. The Controller-to-Service target and its call/return pair are GREEN from the frozen sources and M1 endpoint identity.
- Added the next RED for Service-to-Mapper plus Mapper Java-to-XML binding. It initially failed because the M1 XML statement endpoint includes its canonical parameter list; the test now requires the exact `namespace#statementId(parameterType)` endpoint.
- Implemented recursive static traversal from a resolved method to its exact downstream field-receiver calls, then bound one unique Mapper Java method to one unique M1 XML statement. The binding is based on namespace, Java method signature, statement id, and the preexisting M1 node grammar—not a file scan or simple-name fallback.
- Re-ran the narrow selector: both Controller→Service and Service→Mapper→XML chains are GREEN.
- Added and closed two fail-closed resolution cases: an overloaded HTTP handler now produces a Gap rather than selecting source order, and an explicit Java import resolves before same-package fallback so an imported decoy cannot produce a false exact call edge.

## Current state

- M1 persists and fresh-reopens structure inputs; M2 consumes the typed `CallGraphInputs` contract.
- The current CallGraphBuilder supports static field-receiver calls with literal or parameter argument types, explicit non-wildcard Java imports, handler-overload rejection, and exact Mapper Java-to-XML binding. It deliberately does not yet implement M1 persisted-draft reopening, Mapper ambiguity tests, module publication, or full Symbol Solver dispatch.
- M2’s current raw `CodeStructureGraphDraft` input cannot demonstrate the published requirement that M1 module receipt controls and exact upstream references match the same reopened source/discovery basis. A Sol/ultra docs-only clarification is being published before that implementation begins.

## Changed files

- `progress/program-graph-call-graph.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`
- `src/test/resources/analysis/graph/call-graph/**`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphEdge.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphEdgeKind.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphInputs.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphNode.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphNodeKind.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphProfile.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | Test compilation fails only on absent `CallGraphInputs`, `CallGraphProfile`, `CallGraphBuilder`, `CallGraphDraft`, `CallGraphNode`, and `CallGraphEdgeKind`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 1 test, 0 failures/errors/skips: exact static Controller-to-Service call and paired return are emitted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | Test compilation fails only because `CallGraphEdgeKind.JAVA_METHOD_TO_XML_STATEMENT` is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | 2 assertion failures: recursive traversal exposes the missing exact XML statement canonical binding; the original coverage assertion incorrectly assumed no later Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 2 tests, 0 failures/errors/skips: exact Controller→Service, Service→Mapper, Mapper Java→XML statement, and paired returns. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | 3 tests, 1 assertion failure: an overloaded entry handler silently selected one declaration rather than recording a Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 3 tests, 0 failures/errors/skips: overload becomes a Gap with no emitted edge. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | 4 tests, 1 assertion failure: explicit `com.decoy.DepotHeadService` import was incorrectly treated as `com.example.DepotHeadService`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips: explicit import creates a Gap and emits no false exact target. |

## Decisions

- The M2 public seam is `buildCalls(CallGraphInputs, CallGraphProfile)`; source bytes, HTTP roots, and Mapper candidates stay inside the same fresh reopened input aggregate.
- M1 canonical XML statement IDs include `namespace#statementId(parameterType)`. M2 selects exactly one existing M1 XML node by parsing that canonical identity and matching the exact namespace and statement id; it does not generate an XML endpoint itself.
- This slice deliberately excludes persisted M1 module reopening, Mapper ambiguity/missing binding, module publication, and full Symbol Solver dispatch. Those follow in separate RED/GREEN shards.

## Blockers

- Published M2 lineage requirements cannot yet be enforced from the current raw M1 draft input. A Sol/ultra design correction is in progress; no code will weaken that boundary.

## Exact next action

- After the docs-only M1 reopening contract is published, write the public-seam RED for a mismatched M1 module receipt/control/upstream basis, then implement exact persisted M1 re-opening before adding the next Mapper-binding negative case.

## Resume checks

- Read this file, run `git status --short`, confirm branch `codex/source-analysis-program-graphs`, then rerun the M2 direct selector.
