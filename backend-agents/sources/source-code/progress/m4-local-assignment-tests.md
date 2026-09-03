# Progress: M4 local assignment RED

- Status: COMPLETE
- Agent role: Luna/xhigh RED for the bounded M2 local-argument prerequisite and M4 intra-method local-assignment slice
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add one M2 public-seam test for a local variable argument so the M4 parameter-to-local
  assignment-to-Mapper chain has an exact upstream call edge.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `progress/data-flow-graph.md`, and `progress/m3-statement-block-green.md`.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the repository and scoped Agent rules plus the published M4 intra-method/direct-property contract.
- Confirmed the fixture `createWithTwoServiceStatements` contains the exact `String normalized = status;` then `depotHeadMapper.updateStatus(normalized);` source slice.

## Current state

- The M4 public-seam test remains present in `DataFlowGraphBuilderTest` and established the
  intended RED without production changes. It stopped at the missing normalized Mapper argument
  node because the current upstream call graph does not type-resolve a local argument.
- The upstream cause is now isolated in a separate M2 RED: `CallGraphBuilder` only resolves
  `NameExpr` argument types from method parameters, not from an AST local declaration.
- The M2 public-seam test is present in `CallGraphBuilderTest`; it requires the exact local-argument
  Service→Mapper target and the Mapper Java→XML statement binding.

## Changed files

- `progress/m4-local-assignment-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASSING RED | 6 tests, 1 assertion failure, 0 errors/skips. The M4 test fails at `normalizedArguments`: expected 1, actual 0. Existing five tests remain passing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | EXPECTED RED | 10 tests, 1 assertion failure, 0 errors/skips. The new test fails because the Service→Mapper `CALL_TARGET` list is empty; the existing 9 tests remain passing. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Use the existing real fixture, persisted/reopened M1/M2/M3 predecessors, and the public `DataFlowGraphBuilder` seam; do not use mocks, private helpers, reflection, or source-name shortcuts.
- Assert the exact source-backed parameter → use → local definition → mapper argument chain and its typed edge rules.

## Blockers

- The M4 RED is a normal assertion failure, not a compile or test infrastructure error. Its
  requested mapper argument cannot exist until M2 exposes the local-argument call. This task does
  not change that upstream production behavior.

## Exact next action

- Terra/Design Authority should implement the smallest M2 local-argument type resolution needed by
  the existing public contract, then rerun this selector before implementing the M4
  parameter → USE → DEFINITION → ARGUMENT chain.

## Resume checks

- Read this file, run `git status --short`, inspect the diff to ensure only the focused test and this progress file are owned here, then rerun the targeted selector.
