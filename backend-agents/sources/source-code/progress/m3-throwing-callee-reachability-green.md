# Progress: M3 throwing-callee reachability green

- Status: COMPLETE
- Agent role: Production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make the bounded serial exact-call walker stop activating later lexical call blocks after an earlier selected callee has no known normal exit.
- Approved inputs: The published M3 normal-exit/continuation contract and the confirmed public RED in `SerialCallContinuationTest`.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Confirmed the new fixture compiles and produces the intended RED: later `AuditClient.recordStatus` still receives a `CALL` edge after `FailingAudit.fail` reaches a throw terminal.
- Implemented the serial-call activation guard in `ControlFlowGraphBuilder` and passed the focused control-flow selector.

## Current state

- The smallest legal repair is a serial-call activation flag local to `walkMethod`: it applies only when `isSupportedSerialCallSequence(...)` is true.  After a target lacks a known normal exit, the walker stops processing later call sites.  Non-serial shapes remain unchanged and unsupported as documented.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java`
- `progress/m3-throwing-callee-reachability-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` | RED | 2 tests, 1 expected assertion failure; later AuditClient `CALL` was activated after throw-only callee |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | changed only `SerialCallContinuationTest.java` formatting |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest,ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,NestedGuardControlFlowSafetyTest test` | PASS | 20 tests; 0 failures, errors, or skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,CallGraphModulePublisherTest,ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,NestedGuardControlFlowSafetyTest,SerialCallContinuationTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,AmbiguousCallHandoffTest,ProgramGraphPublicWireTest,ProgramGraphsPublicationSpecifierTest test` | PASS | 66 tests; 0 failures, errors, or skips |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Do not generalize into a worklist, branch join, nested expression, or exception model in this slice.

## Blockers

- None.

## Exact next action

- Continue the next bounded ProgramGraphs capability without extending this serial-only activation guard into unsupported CFG shapes.

## Resume checks

- Re-run the RED selector and read M3 continuation rules in the step design.
