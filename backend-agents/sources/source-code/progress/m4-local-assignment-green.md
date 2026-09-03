# Progress: M4 local assignment data-flow GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02T20:59:50Z
- Last updated: 2026-09-02T21:56:36Z
- Scope: The smallest M4 intra-method data-flow slice: a formal parameter read, a direct local assignment, and that local used as an already-admitted mapper-call argument.
- Approved inputs: Fresh reopened M1/M2/M3 publications, the published Step 03 M4 contract, and the Luna-owned public RED test.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the M4 contract and current data-flow public seam.
- Confirmed the existing builder covers only direct NameExpr call arguments to target M1 formal parameters.
- Read the Luna M4 RED: the desired M4 chain is absent because the upstream M2 call graph omits the
  Service-to-Mapper call in the two-statement fixture.
- Traced the omission to `CallGraphBuilder.expressionType`: it accepts a NameExpr only when the name
  is a method parameter, and does not inspect a direct local declaration in the same verified AST.
- Luna established an independent M2 public-seam RED: 10 tests with exactly the local-argument
  Service-to-Mapper edge assertion failing.
- Implemented direct, prior-in-the-same-block local declaration type lookup in M2. It is restricted
  to method-target typing and does not create or imply a value-flow relation.
- M2 selector is GREEN: all 10 `CallGraphBuilderTest` tests pass.
- Re-ran the M4 RED after the M2 repair: it now fails precisely at the missing parameter-read `USE`,
  not at a missing Mapper argument.
- Implemented the bounded M4 chain from the fresh M1/M2/M3 artifacts: external M1 `PARAMETER`
  to `USE`, direct local `DEFINITION`, then the existing M4 mapper-call `ARGUMENT`. Both the
  declaration and call expression must each belong to one activated M3 basic block with matching
  entry ownership; every new edge carries fresh source provenance plus predecessor provenance.
- M4 selector is GREEN: all 6 `DataFlowGraphBuilderTest` tests pass.
- Project formatting and the serial M2/M3/M4 direct selector are GREEN: 28 tests with no failures,
  errors, or skips. `git diff --check` is also GREEN.
- Replaced one redundant reparsing of the already parsed M4 compilation unit with
  `MethodCallExpr.findCompilationUnit()`. The direct selectors must be rerun after this contained
  no-behavior-change reuse adjustment.
- Re-ran after the contained AST reuse adjustment: the serial M2/M3/M4/M5 selector is GREEN with
  29 tests and no failures/errors/skips.

## Current state

- The bounded direct-local vertical slice is complete. It does not
  claim support for aliases, joins, reassignment, fields, setters, loops, or complex expressions;
  those require later RED slices or typed Gaps.

## Changed files

- `progress/m4-local-assignment-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Expected RED | 6 tests; the new local-assignment assertion fails because the M2 graph contains no Mapper call for `normalized`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 10 tests, 0 failures/errors/skips; direct local declared type resolves the exact Service-to-Mapper and Mapper-to-XML edges. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 6 tests, 0 failures/errors/skips; `PARAMETER → USE → DEFINITION → ARGUMENT` is source-backed and exact. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest test` | PASS | 28 tests, 0 failures/errors/skips after formatting. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Reuse the existing M4 argument node at the mapper call. Add only the missing exact parameter-read `USE`, local `DEFINITION`, and their two deterministic edges.
- Do not infer aliases, joins, fields, setters, loops, or complex expressions in this slice.
- Repair the M2 precondition first: a directly declared local type is source-proven exact static
  information; resolving it is not a data-flow guess.

## Blockers

- None. The test-first gate is in progress.

## Exact next action

1. Start a separate RED cycle before expanding M4 beyond direct local assignment.

## Resume checks

1. Read this progress file and `progress/m4-local-assignment-tests.md`.
2. Re-run the 29-test direct selector before extending M4.
3. Re-read the M4 subsection of `docs/analysis-steps/03-program-graphs.md`.
