# Progress: M4 unreachable boundary handoff tests

- Status: COMPLETE
- Agent role: TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Verify that M4 consumes M3 activation rather than all M2 call sites when a prior exact callee only throws.
- Approved inputs: Published M3/M4 contracts and the validated M3 throw-only sequential-call fixture.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Confirmed the fixture supplies one throw-only frozen-Java call followed by a Java boundary call that M3 excludes from its traversal.
- Added the public M4 handoff regression; it passes without production changes, confirming that M4 reads M3 activation rather than enumerating all M2 calls.

## Current state

- The regression will require zero `JavaBoundaryInvocation` and zero `ARGUMENT_TO_BOUNDARY` elements for the unreachable AuditClient call.  It will not constrain how M4 models the reachable throw-only call or any external effect.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `progress/m4-unreachable-boundary-handoff-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 18 tests; 0 failures, errors, or skips |

## Decisions

- This is an M3-to-M4 activation contract regression, not a new Java-boundary data-flow shape.

## Blockers

- None.

## Exact next action

- Continue the next bounded ProgramGraphs capability; retain M3 activation as the sole M4 call denominator.

## Resume checks

- Read this file, `DataFlowGraphBuilderTest`, and the M4 input contract in the ProgramGraphs step design.
