# Progress: M3 multi-entry ownership RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: One public-seam RED for shared control-flow ownership across two discovered HTTP entries.
- Approved inputs: M3 program-graphs design, existing ControlFlowGraphBuilder public seam and real frozen M1/M2 fixture helpers.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Ran the required baseline selector before editing: 13 tests passed.

## Current state

- Added one public-seam RED to `ControlFlowGraphBuilderTest` with two distinct HTTP entry points and one shared `DepotHeadController#batchSetStatus` handler.
- The fixture publishes and fresh-reopens the real M1 code-structure and M2 call-graph artifacts before invoking `ControlFlowGraphBuilder.buildControlFlow`.
- The test checks sorted owner union, traversal membership, one stored shared edge, and equality after reversing the requested entry order.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `progress/m3-multi-entry-ownership-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 13 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | RED (expected) | 14 tests; 1 failure, 0 errors; shared Controller/Service basic-block nodes retain only the first entry owner instead of the sorted two-entry union |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- The RED will assert public graph semantics, not private implementation details: one physical shared node/edge, sorted union `owningEntryIds`, traversal membership for each entry, and determinism under entry order reversal.
- No production, design, POM, or unrelated fixture changes are allowed.

## Blockers

- None.

## Exact next action

- Terra should merge the owner set when M3 reuses a physical node/edge across entry traversals, preserving one stored element and deterministic graph identity; then rerun `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test`.

## Resume checks

- Re-read this file, inspect only the owned test diff, and run the exact selector before reporting completion.
