# Progress: M4 unsupported boundary return shape GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Close the established RED for a consumed direct-local external return whose local type or downstream use cannot safely produce an `UNKNOWN_BOUNDARY_RETURN`.
- Approved inputs: `AGENTS.md`; `docs/analysis-steps/03-program-graphs.md`; `docs/plans/source-analysis-naming-and-delivery-plan.md`; `docs/plans/target-standards-and-toolchain-plan.md`; `progress/m4-return-shape-gap-tests.md`; `DataFlowGraphBuilderTest`.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped guidance, plans, M4 data-flow/Gap contract, test-author progress, existing return tests, and current builder implementation.
- Confirmed the worktree has substantial unrelated in-progress M3 changes; this slice will modify only the builder and this progress record.
- Reproduced the expected behavioral RED: the `DataFlowGraphBuilderTest` selector ran 17 tests with exactly one assertion failure because the unresolved generic local type silently omitted the required local Gap.
- Corrected only the unsupported-return classification. It now identifies a consumed direct-local initializer first; if the existing supported-transfer builder cannot construct the unknown return, it closes the already-enumerated boundary-transfer candidate as an entry-owned local Gap.
- Preserved unconsumed direct initializers, supported local boolean-to-`if` returns, and direct-condition unsupported returns.
- Applied Spotless and re-ran the direct selector successfully.

## Current state

- The direct selector is green. A consumed direct-local external return that cannot be represented by the supported unknown-return shape now yields the required local Gap at the exact invocation locator, with no inferred return value, node, edge, XML/SQL behavior, or external effect.

## Changed files

- `progress/m4-return-shape-gap-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Expected RED | 17 tests; 1 assertion failure: required `DATA_FLOW_BINDING_UNPROVEN` Gap is absent for the unresolved local type. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | Passed | Applied the project formatter before final verification. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Passed | 17 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | Passed | No tracked-file whitespace errors. |

## Decisions

- The minimal correction must use the existing boundary-transfer candidate and exact Java invocation locator, create no unknown-return node or edge, and infer no external behavior.
- A direct local is considered consumed only when a same-name Java `NameExpr` begins after its declaration. An unconsumed initializer remains outside this unsupported-consumption Gap.

## Blockers

- None.

## Exact next action

- Hand this completed GREEN slice to the parent agent. Do not widen return-shape support without a separate RED and design check.

## Resume checks

- Do not alter public wire, artifact count, test, POM, design, XML/SQL behavior, or commit state.
- After implementation, run Spotless apply, the assigned selector, and `git diff --check`.
