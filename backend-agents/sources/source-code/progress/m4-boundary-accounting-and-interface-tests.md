# Progress: m4-boundary-accounting-and-interface-tests

- Status: COMPLETE
- Agent role: Luna RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Scope: Add public-seam RED tests only for M4 external-boundary transfer-candidate accounting, unsupported external actual Gap handling, and (only if the existing fixture is reusable) default-interface classification.
- Allowed changes: This progress file, `DataFlowGraphBuilderTest`, and a narrowly necessary existing test fixture/helper.
- Prohibited changes: Production, POM, documentation, commits, pushes, or broad fixture frameworks.

## Planned slices

1. An exact external Mapper boundary has its required `data-flow-boundary-transfer-candidate-v1` item in both worklist sets.
2. An exact external Mapper call with a non-`NameExpr` actual produces a `DATA_FLOW_BINDING_UNPROVEN` local Gap rather than throwing or omitting it.
3. Reuse an existing fixture for a default-interface target only if that requires a narrow fixture variant; otherwise explicitly defer it.

## Completed REDs

- Re-read the M4 v3 contract, the first-green review, and current data-flow fixtures.
- Added `accountsForTheExactMapperBoundaryTransferCandidate`: a standard exact Mapper boundary must produce exactly one `data-flow-boundary-transfer-candidate-v1` item in both enqueued and processed accounting.
- Added `recordsALocalGapForAnUnsupportedExactMapperBoundaryActual`: an exact Mapper call with literal actual must not throw, must create one local `DATA_FLOW_BINDING_UNPROVEN` Gap owned by the known entry, and must not create a boundary node.
- Added only `Fixture.createWithLiteralMapperArgument` plus its narrow Service source text; no new fixture framework.
- Deferred the default-interface test. It requires changing the shared source/mapper-catalog construction to admit a mapper body, which is broader than the two requested accounting/Gap RED slices.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest test` | RED | Exit 1; 12 tests run, 2 failures, 0 errors, 0 skipped. `accountsForTheExactMapperBoundaryTransferCandidate` expected one candidate item and found `[]` (line 747). `recordsALocalGapForAnUnsupportedExactMapperBoundaryActual` failed its no-throw assertion because `DataFlowGraphBuilder$Accumulator.recordBindingGap` dereferenced `formal == null` at `DataFlowGraphBuilder.java:1037` (test line 758). |

## Handoff

- Implement only enough M4 production behavior to account a boundary transfer candidate in both worklist lists and map unsupported external actuals to the prescribed local Gap. Leave these tests RED until that production work is authorized.
