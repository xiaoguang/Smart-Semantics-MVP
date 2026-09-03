# Progress: M3 throwing-callee reachability tests

- Status: COMPLETE
- Agent role: TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public M3 regression for a sequential call whose first exact frozen-Java callee has no normal exit.
- Approved inputs: Published M3 contract: a `THROW_TERMINAL` does not activate call-site continuation or contribute a lexical-successor path.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Located the existing sequential-call fixture and the M3 normal-exit contract.
- Added the frozen `FailingAudit.fail(status)` then `AuditClient.recordStatus(status)` fixture and public reachability regression.
- Established the intended RED after correcting a test-only collection assertion: 2 tests ran, with exactly the later-call reachability assertion failing.

## Current state

- The new test will use a real frozen source fixture: `DepotHeadService` first calls a Java `FailingAudit.fail(status)` method that throws, then contains an `AuditClient.recordStatus(status)` statement.  It must prove that the second call site and its boundary call are absent from the activated traversal.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/SerialCallContinuationTest.java`
- `progress/m3-throwing-callee-reachability-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` | RED | after test compilation repair: 2 tests, 1 expected assertion failure; later AuditClient CALL was activated after throw-only callee |

## Decisions

- This test checks reachability, not merely absence of a direct continuation edge.  Enumerating all call sites after a throwing callee would otherwise falsely make later statements appear executable.

## Blockers

- None.

## Exact next action

- Handoff the confirmed RED to the bounded production slice; do not expand the test into a general CFG worklist requirement.

## Resume checks

- Read this file, M3 §8.3/§8.4 in `docs/analysis-steps/03-program-graphs.md`, and `SerialCallContinuationTest.java`.
