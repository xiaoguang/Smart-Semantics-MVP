# Progress: M2 physical call-site and owner identity RED tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add three public-seam RED tests for M2.1 physical call-site identity, owner-sensitive graph identity, and shared ambiguous-gap owner union.
- Approved inputs: scoped AGENTS.md; `docs/analysis-steps/03-program-graphs.md` M2.1; `docs/supplements/program-graphs-implementation-backlog.md` P3; `progress/m2-owner-union-review.md`; existing CallGraphBuilder public seam and frozen graph fixtures.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped rules and M2.1/P3 design contract.
- Ran the existing `CallGraphBuilderTest` selector before adding tests: 15 tests passed.
- Created this progress file before modifying tests.
- Added three bounded public-seam tests to `CallGraphBuilderTest`:
  - distinct source spans for same-shaped direct field calls;
  - graph identity changes when only physical-call owner set changes;
  - shared ambiguous call Gap owner union, identity rebuild, and order determinism.
- Added one minimal frozen source fixture, `call-graph-same-shape`, containing two identical direct Mapper calls at different source spans.

## Current state

- The existing 15 tests remain green. Two new tests are precise REDs against current production behavior; the shared ambiguous-gap test is green because the owner-union implementation already satisfies that contract.
- Targeted selector result: 18 tests, 2 failures, 0 errors, 0 skipped.
- RED A: `GraphReferenceException: GRAPH_REFERENCE_BROKEN` from same-shaped call-site node collision instead of two span-distinct nodes/return frames.
- RED B: one-owner and two-owner drafts produced the same `graphId` although the node owner payload differed.
- C passed: one `CALL_TARGET_AMBIGUOUS` Gap, sorted two-entry owner union, one disposition, identity reconstructed from the union, and reversed entry order equal to forward order.

## Changed files

- `progress/m2-physical-identity-red-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`
- `src/test/resources/analysis/graph/call-graph-same-shape/`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 15 tests, 0 failures/errors/skips before this RED slice |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | EXPECTED RED | 18 tests, 2 failures, 0 errors/skips; A and B fail for the reviewed identity defects, C passes |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java src/test/resources/analysis/graph/call-graph-same-shape progress/m2-physical-identity-red-tests.md` | PASS | No whitespace errors |

## Decisions

- Tests will use `CallGraphBuilder.buildCalls(...)` and real canonical M1 structure publication/readback fixtures; no private implementation seam or production-generated golden.
- Tests will be added to the existing `CallGraphBuilderTest` class as requested by the step design.

## Blockers

- Two RED assertions are the expected implementation blockers described by `progress/m2-owner-union-review.md`; no test or fixture ambiguity blocker remains.

## Exact next action

- Report the 18-test selector and exact two RED findings to the parent; Terra should then address source-span identity and owner-inclusive graph identity under the published M2.1 design.

## Resume checks

- Re-read this file, `CallGraphBuilderTest`, M2.1 design, and `m2-owner-union-review.md` before continuing.
- Do not edit production code, design, POM, or another Agent's progress file.
