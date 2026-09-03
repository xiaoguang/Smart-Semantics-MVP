# Progress: M2 call-target ambiguity RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: One public-seam CALL_TARGET_AMBIGUOUS RED test and its isolated frozen fixture.
- Approved inputs: Published M2.1 design, existing CallGraphBuilder public seam and M1 publication/readback fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory

## Completed

- Added two frozen resource variants with one reachable `auditClient.recordStatus(null)` call and the `String`/`Integer` overload order exchanged.
- Added two public-seam tests to `CallGraphBuilderTest` for the required ambiguity Gap and order-stable Gap/accounting projection.
- Confirmed the order-comparison test uses separate empty store roots, avoiding a fixture lifecycle error.

## Current state

Reviewed the scoped instructions, M2.1 algorithm, backlog, existing CallGraphBuilder test, and inline frozen graph fixtures. No production behavior has been changed. The test is expected to be RED until M2 resolves `null` as a reference-compatible actual and computes the two-target candidate set.

## Changed files

- progress/m2-call-target-ambiguity-red.md
- src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java
- src/test/resources/analysis/graph/call-graph-target-ambiguous/
- src/test/resources/analysis/graph/call-graph-target-ambiguous-reversed/

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | EXPECTED RED | 12 tests, 1 failure, 0 errors; only new assertion expected `CALL_TARGET_AMBIGUOUS`, actual `CALL_ARGUMENT_TYPE_UNRESOLVED` |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 295 Java files clean |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

The RED will use the existing M1 publication/readback path and a minimal inline resource fixture with one reachable `recordStatus(null)` call against a visible interface that declares `String` and `Integer` overloads. It will assert the exact local Gap shape, call-site locator, candidate/accounting closure, absence of call elements, and declaration-order determinism.

## Blockers

None. The required RED is established and is caused by the current resolver treating `null` as an unresolved argument before candidate-set resolution.

## Exact next action

Terra should implement M2.1 candidate-set resolution for this public seam, then rerun the same selector and review the exact Gap/accounting assertions.

## Resume checks

- Confirm only this progress file, the intended test, and the intended fixture are changed.
- Confirm RED is caused by missing `CALL_TARGET_AMBIGUOUS`, not a malformed fixture or compile failure.
- Preserve this RED test while implementing M2.1 candidate-set resolution; do not rename `CALL_ARGUMENT_TYPE_UNRESOLVED` into the required ambiguity reason.
