# Progress: m4-boundary-v3-first-green-review

- Status: COMPLETE
- Agent role: Luna read-only reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Scope: Read-only review of the current untracked M4 boundary-v3 first-green vertical slice against the M4 v3 contract and the new public-seam test.
- Constraints: Do not modify production, tests, POM, documentation, Git state, or run Maven. This progress file is the sole permitted write.

## Inputs

- `docs/analysis-steps/03-program-graphs.md` M4 v3 contract.
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java` new boundary test.
- Current untracked M4 production files under `src/main/java/org/sourceanalysis/app/analysis/graph/` only.

## Review result

- No P0 finding.
- The reviewed Mapper `NameExpr` slice is technically generic: an external no-body target receives an M4 `ARGUMENT` and one `JAVA_BOUNDARY_INVOCATION`, the boundary record carries the M2 call/call-target IDs, ordered Java-local origins, activated M3 basic block, optional M3 guard pair, Java call locator, generic rule, and identity preimage; it does not create XML/SQL data-flow relations or technology-specific branches.
- P1 — interface default methods are misclassified as frozen concrete Java. `DataFlowGraphBuilder.java:1250-1252` checks only `MethodDeclaration.getBody()`. A default interface method therefore becomes an internal target; `:570-573` creates a formal and `:598-620` emits `ARGUMENT_TO_PARAMETER`, contrary to the v3 rule that an interface target leaves frozen Java. Generated-source classification is likewise absent from this predicate.
- P1 — external boundary calls are not represented in the M4 candidate/worklist denominator, and an unsupported external actual crashes rather than becoming the required Gap. `DataFlowGraphBuilder.java:117-130` adds an argument work item only when an `ARGUMENT_TO_PARAMETER` edge exists, while `:375-404` creates boundary nodes without adding the required `data-flow-boundary-transfer-candidate-v1`. If an external actual is not a `NameExpr`, `:575-578` calls `recordBindingGap` with `formal == null`, then `:1032-1038` dereferences it; even without that crash, `:400-402` silently drops a missing argument binding rather than emitting `DATA_FLOW_BINDING_UNPROVEN`.

## Bounded-slice assessment

- Acceptable only as the explicitly narrow Mapper/no-body/`NameExpr` first-green: the current public test passes this path and the Java-only stop is correct there. It must not be promoted as the full M4 v3 boundary implementation until both P1 findings are addressed and the deferred non-Mapper, return-use, ambiguity, guard, and rejection slices are covered.

## Verification

- No Maven or other test command was run, per task constraint.
