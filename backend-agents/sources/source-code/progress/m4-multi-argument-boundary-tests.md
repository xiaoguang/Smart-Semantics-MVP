# Progress: M4 multi-argument Java boundary tests

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for ordered multi-argument Java boundary values and local origins.
- Approved inputs: Scoped AGENTS.md; docs/analysis-steps/03-program-graphs.md M4 contract; existing graph tests and fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Added `MultiArgumentBoundaryDataFlowTest` with one minimal frozen-Java fixture: an `AuditClient.recordStatus(String, String)` interface call receives the service parameter `status` and a local `normalized` assigned directly from that parameter.
- Added only the test fixture factory/source strings needed by the new test to `ControlFlowGraphBuilderTest`; no production code, design, POM, or existing assertions were changed.
- The initial run exposed a test-only lookup mistake (a structure `PARAMETER` origin was incorrectly looked up as a data-flow node); corrected the test to validate the two origin categories through their owning public graphs.
- The corrected test passed without a production change, so the requested multi-argument/order/origin behavior is already present in the current implementation; no Terra GREEN slice was needed.

## Current state

The public M1–M4 build seam now proves one generic boundary invocation with two contiguous ordered arguments, one edge per argument, exact target-edge identity, direct-parameter/local-definition origins, no cross-boundary argument-to-parameter edge, and no XML/SQL nodes in the Java-local origin set.

## Changed files

progress/m4-multi-argument-boundary-tests.md
src/test/java/org/sourceanalysis/app/analysis/graph/MultiArgumentBoundaryDataFlowTest.java
src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java

## Verification

| Command | Result | Key output |
| Command | Result | Key output |
| `mvn -t .mvn/toolchains.xml -o -Dtest=MultiArgumentBoundaryDataFlowTest test` | PASS | 1 test, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | New test formatted; 292 files clean |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- The test will cover one external Java interface call with two explicit actual arguments: one request parameter and one assigned Java local.
- The test will assert only generic Java-boundary semantics; it will not assert XML, SQL, or external side effects.
- Because the corrected public-seam test is already green, current implementation has no RED attributable to single-argument handling or origin/accounting for this bounded behavior.

## Blockers

No implementation blocker. The intended RED was not reproducible because the current implementation already handles this case.

## Exact next action

Leave the bounded test and fixture in place for regression coverage; the next action belongs to the next approved M4/M5 slice.

## Resume checks

- Confirm only this test and progress file are owned by this task.
- Confirm no production, design, POM, or unrelated test assertions are modified.
