# Progress: M2 multi-entry physical call-site owner union tests

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Public-seam RED test for one physical Service call site reached by two discovered HTTP entries
- Approved inputs: scoped AGENTS.md; M2.1 design; program-graphs backlog P3; existing CallGraphBuilderTest and graph fixtures
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory

## Completed

- Added one public-seam regression test to `CallGraphBuilderTest` for two static
  Spring MVC entries calling the same Service method, whose Service method calls
  one Mapper method at one physical call site.
- Added the smallest frozen four-file fixture: two route annotations in one
  controller, one Service, one Mapper interface, and one MyBatis XML statement.
- The test asserts one shared Service call-site node, sorted union ownership,
  one call-target/return pair, one coverage candidate, and stable output when
  the supplied entry order is reversed.

## Current state

The fixture reaches the intended M2 path. The expected RED is precise: the
shared Service call-site node is emitted once but owns only the first entry;
the second entry is not unioned into `owningEntryIds`. The assertions after the
owner check are retained to enforce no duplication and traversal-order
determinism after the production fix.

## Changed files

`src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`
`src/test/resources/analysis/graph/call-graph-multi-entry/`
`progress/m2-multi-entry-owner-union-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED (expected) | 15 tests; 1 failure, 0 errors; shared Service call site has only first `entryId` instead of both sorted owners |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

The test must use the existing public `CallGraphBuilder` seam and persisted structure/discovery inputs. It must not assert an implementation detail or create a synthetic production object. If the discovery fixture cannot represent two valid entries, record the setup blocker instead of weakening the test.

## Blockers

Production owner-union implementation is not part of this Luna task. The test
is intentionally left RED for Terra to implement under the M2.1 owner-union
contract.

## Exact next action

Terra should make the accumulator merge owners for an identical physical
call-site node and its associated exact edges without creating a second
call-site/candidate. Then rerun this selector.

## Resume checks

Read this file, inspect `git status --short`, verify only task-owned test/fixture paths are changed, then rerun the direct selector before any further edit.
