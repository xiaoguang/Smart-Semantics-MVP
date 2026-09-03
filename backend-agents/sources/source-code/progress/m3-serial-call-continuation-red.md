# Progress: M3 serial call continuation RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: One public-seam regression for sequential exact Java call sites in M3 control flow
- Approved inputs: M3 program-graphs design, existing `ControlFlowGraphBuilderTest.Fixture.createWithMapperAndAuditClient`, frozen in-memory fixture sources, public M1/M2 publisher/readback seam
- Current branch/worktree: `codex/source-analysis-program-graphs`

## Completed

- Read scoped AGENTS and M3 continuation/call-return contract.
- Ran the required baseline selector before adding the RED test: `ControlFlowGraphBuilderTest` passed 16/16.
- Added `src/test/java/org/sourceanalysis/app/analysis/graph/SerialCallContinuationTest.java`.
- Confirmed the intended RED: the mapper call has no normal-return continuation to the audit basic block.

## Current state

- The test uses the real M1/M2 publisher/readback fixture and identifies both Service callsites by exact M2 provenance and target method.
- It requires one `NEXT` from the first mapper callsite to the second audit basic block, rejects the direct first-block bypass, and confirms the second call retains its own `CALL`/`RETURN` projections.
- The current builder fails only the missing continuation assertion, as expected; the test is ready for Terra/GREEN.

## Changed files

- `progress/m3-serial-call-continuation-red.md` (this file)
- `src/test/java/org/sourceanalysis/app/analysis/graph/SerialCallContinuationTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 16 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` | EXPECTED RED | 1 test, 1 failure, 0 errors, 0 skipped; missing mapper-call → audit-block `NEXT` continuation |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/graph/SerialCallContinuationTest.java progress/m3-serial-call-continuation-red.md` | PASS | no whitespace errors |

## Decisions

- Add a separate test class so the existing shared `ControlFlowGraphBuilderTest.java` remains untouched while another M3 slice has uncommitted changes.
- Identify callsites through M2 `CALL_SITE` nodes and their exact source provenance, not node names or source order alone.
- Assert one normal-return `NEXT` from the first callsite to the second call's basic block, and reject the direct basic-block bypass.

## Blockers

- None.

## Exact next action

- Hand off the exact RED to Terra: implement normal-return continuation between sequential exact Java callsites without retaining a direct basic-block bypass.

## Resume checks

- Re-read this file, inspect `git status --short` for the test/progress scope, then run `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` after production GREEN is available.
