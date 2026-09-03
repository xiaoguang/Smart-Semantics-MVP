# Progress: M4 boundary accounting green

- Status: BLOCKED
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Scope: Implement only the two established public-seam M4 REDs: exact external Mapper boundary-transfer worklist accounting and local Gap handling for a non-`NameExpr` boundary actual.
- Prohibited scope: Default-interface classification, return behavior, non-Mapper fixtures, M5/M6, docs/POM/test edits, commits, and pushes.

## Approved inputs

- `AGENTS.md`
- `docs/plans/source-analysis-naming-and-delivery-plan.md`
- `docs/plans/target-standards-and-toolchain-plan.md`
- `progress/boundary-dataflow-contract.md`
- `progress/m4-boundary-v3-first-green-review.md`
- `progress/m4-boundary-accounting-and-interface-tests.md`
- The two established `DataFlowGraphBuilderTest` RED methods.

## Intended GREEN

1. A successfully materialized exact external Mapper boundary adds exactly one `data-flow-boundary-transfer-candidate-v1` work item whose identity binds its M2 invocation call ID and `CALL_TARGET` edge ID. It is present in both closed worklist sets.
2. A non-`NameExpr` actual at an exact external boundary becomes one locally owned `DATA_FLOW_BINDING_UNPROVEN` M4 Gap at the actual-expression locator, with no synthetic external formal or `ARGUMENT_TO_PARAMETER` edge and no boundary invocation.

## Verification plan

Run only:

1. `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test`
2. `mvn -t .mvn/toolchains.xml -o spotless:apply`
3. Repeat the same targeted test selector.

## Implementation

- Added a dedicated boundary-transfer work-item set. A successfully materialized generic Java boundary records `identity("data-flow-boundary-transfer-candidate-v1", invocationCallId, callTargetEdgeId)` and includes it in the closed enqueued and processed sets.
- Made `recordBindingGap` tolerate an absent formal parameter. It still records the unsupported Java actual candidate and its exact actual-expression locator, but creates no external parameter candidate or `ARGUMENT_TO_PARAMETER` candidate when the target is a boundary.
- The non-`NameExpr` path consequently returns its local Gap before generic-boundary materialization; it creates no `JAVA_BOUNDARY_INVOCATION`.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | BLOCKED | 12 tests run, 1 failure, 0 errors. Both new established REDs are green. Existing `bindsAnActivatedControllerArgumentToItsSameOrdinalServiceParameter` fails at line 103 because it asserts every work item uses only the legacy `data-flow-argument-work-item-v1` or `data-flow-java-read-work-item-v1` prefixes. The exact same fixture now correctly includes the required `data-flow-boundary-transfer-candidate-v1` work item. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless completed successfully. It reported `DataFlowGraphBuilderTest.java` as formatted; no test behavior was edited by this task. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | BLOCKED | Reproduced the same single line-103 legacy-prefix assertion failure: 12 tests run, 1 failure, 0 errors. |

## Blocker

The only selector failure is an out-of-scope test expectation incompatible with the newly required public work-item family. Updating that assertion to allow the published boundary-transfer prefix is a test edit, which this work unit expressly prohibits. No further change is safe without authority to adjust the stale assertion.

## Files changed by this work unit

- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`
- `progress/m4-boundary-accounting-green.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java` was formatted by the explicitly required `spotless:apply` command; this work unit made no semantic test edit.

## Next action

Have the test owner update the legacy prefix-only assertion, then rerun the same selector. No M4 production change is indicated.
