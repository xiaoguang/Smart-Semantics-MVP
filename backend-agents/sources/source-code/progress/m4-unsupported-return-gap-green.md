# Progress: m4-unsupported-return-gap-green

- Status: COMPLETE
- Agent role: Terra/xhigh production GREEN implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Account for a directly consumed, nonvoid external Java return outside the supported direct-local initializer shape as an M4 local Gap only.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`; `progress/m4-unknown-boundary-return-review.md`; `progress/m4-unsupported-return-shape-gap-test.md`; `DataFlowGraphBuilderTest` exact RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped agent guidance, M4 return contract, prior review, and exact public-seam RED.
- Confirmed the pre-existing worktree changes are outside this owned slice.
- Reconfirmed the clean RED: the targeted selector ran 16 tests with exactly one failure, the direct-condition fixture's missing `DATA_FLOW_BINDING_UNPROVEN` Gap.
- Implemented the smallest generic boundary-return fallback: a consumed nonvoid return outside the direct-local initializer path closes the existing boundary-transfer candidate as an entry-owned `DATA_FLOW_BINDING_UNPROVEN` Gap at the boundary call locator.
- Preserved the existing direct-local unknown-return transfer and avoided return-value/effect, XML/SQL, and technology-specific inference.
- Ran Spotless apply and re-ran the exact selector successfully.

## Current state

- The direct-condition fixture now records its existing boundary transfer candidate as the required entry-owned local Gap; direct-local return handling remains unchanged.

## Changed files

- `progress/m4-unsupported-return-gap-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Expected RED observed | 16 tests; 1 failure at `recordsAGapForAConsumedExternalReturnOutsideTheDirectLocalShape`, due solely to zero matching Gaps. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | Passed | Spotless completed successfully; it formatted the production builder and the already-added test file. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Passed | 16 tests, 0 failures, 0 errors, 0 skipped. |

## Decisions

- Preserve the existing direct-local return transfer unchanged.
- Use only generic Java boundary/call provenance; do not infer a return value, effect, XML/SQL behavior, or technology-specific rule.

## Blockers

- None.

## Exact next action

- Hand this completed bounded GREEN slice to the parent agent; do not make further changes.

## Resume checks

- Re-read the M4 return contract and re-run the targeted selector before changing this behavior.
