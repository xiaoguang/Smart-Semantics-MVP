# Progress: M3 direct-throw reachability RED test

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add only the smallest public-seam direct-throw control-flow test and this progress file; no production/design changes.
- Approved inputs: Existing verified M1/M2 fixture and `directThrowService()` source fixture; no mocks or customer runtime.
- Current branch/worktree: Shared worktree; unrelated existing edits are preserved.

## Completed

- Read the M3 ControlFlowGraphBuilder contract and current `ControlFlowGraphBuilder`/`ControlFlowGraphBuilderTest`.
- Confirmed an existing direct-throw test covers terminal kind and no entry normal-return terminal, but does not explicitly assert THROW reachability from ENTRY.
- Added the direct-throw reachability characterization test at the public `buildControlFlow` seam.

## Current state

- Need add one public-seam test proving direct throw is reachable from ENTRY and does not create a normal continuation/return path.
- Existing builder implements this behavior; the selector passed with the new characterization test.

## Changed files

- `progress/m3-direct-throw-tests.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 13 tests run, 0 failures, 0 errors; direct throw is present in entry traversal, no entry normal-return terminal exists, and THROW terminal has no outgoing edge. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Reuse `Fixture.createWithDirectThrow(temporaryDirectory)` and the existing public `buildControlFlow(ControlFlowInputs, ControlFlowGraphProfile)` seam.
- Use traversal membership/order as the persisted reachable-path proof, and assert no edge leaves the THROW terminal; retain the existing legitimate structural RETURN projection semantics.

## Blockers

- None. Current builder already met the requested contract, so no RED/fix was needed.

## Exact next action

- Run `git diff --check` and report the passing characterization result.

## Resume checks

- Confirm only this progress file and `ControlFlowGraphBuilderTest.java` changed for this task; preserve all unrelated worktree edits.
