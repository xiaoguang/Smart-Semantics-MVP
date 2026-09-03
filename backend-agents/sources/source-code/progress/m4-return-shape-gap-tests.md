# Progress: M4 unsupported boundary return shape tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for an exact external non-void return assigned to a local whose generic type cannot be resolved by the current M4 data-flow contract.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`; `progress/m4-unknown-boundary-return-review.md`; `progress/m4-unsupported-return-gap-green.md`; current `DataFlowGraphBuilderTest` and `ControlFlowGraphBuilderTest.Fixture`.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped guidance, M4 return/Gaps contract, prior review, and existing boundary-return tests.
- Created this progress record before modifying tests.
- Added the bounded `ControlFlowGraphBuilderTest.Fixture` source and one
  `DataFlowGraphBuilderTest` public-seam test. The fixture has one exact
  `AuditClient.recordStatus(String)` call, a `java.util.List<Boolean>` return
  and local assignment, followed by a copy into `java.lang.Object` so the
  return is consumed without an external effect or a second boundary call.
- Confirmed the RED is behavioral: the exact boundary node and one transfer
  candidate are produced, while the expected local Gap is absent.

## Current state

- Added a fixture with an exact `AuditClient.recordStatus(String)` non-void return assigned to a `java.util.List<Boolean>` local. The local is subsequently copied to a `java.lang.Object`, so the return is consumed without reusing the supported boolean guard shape. The current implementation treats the unresolved generic local type as absent and omits both the required local Gap and the return node.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `progress/m4-return-shape-gap-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check` | Passed | No whitespace errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest#recordsAGapWhenAConsumedBoundaryReturnHasAnUnresolvedLocalType test` | RED | 1 test, 1 failure, 0 errors; expected `DATA_FLOW_BINDING_UNPROVEN` Gap was absent after the exact boundary and transfer candidate were built. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | RED | 17 tests, 1 failure, 0 errors, 0 skipped; only the new unresolved-local-type test failed. |

## Decisions

- Use a generic local type rather than a previously covered `if (!recorded)` guard. This isolates the review finding about a consumed direct-local return with no determinable local type without introducing external technology semantics. The copy into `Object` proves the result is consumed while adding no external call.
- Expect the Gap locator to equal the exact boundary invocation locator and require no `UNKNOWN_BOUNDARY_RETURN` or return edges.

## Blockers

- The fixture uses a generic return/local type that the current M4 helper deliberately cannot resolve; this is the intended unsupported shape, not a source parser or call binding failure. The exact boundary and transfer candidate are present before the missing Gap assertion.

## Exact next action

- Hand the bounded RED to the parent/Terra implementation agent. No production change is authorized in this slice.

## Resume checks

- Do not modify production, design, POM, or unrelated tests.
- Confirm the failure is an assertion about missing Gap/return handling, not parsing, binding, or fixture construction.
