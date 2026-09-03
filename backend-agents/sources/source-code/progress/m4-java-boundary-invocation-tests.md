# Progress: m4-java-boundary-invocation-tests

- Status: COMPLETE
- Agent role: Luna RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03 (bounded M3 control correction verified GREEN)
- Scope: Correct only the new public-seam M4 generic Java boundary test's fixture-specific M3 control assertion; retain the generic boundary, Java-origin, rule/evidence, and no SQL/XML data-flow coverage.
- Approved inputs: User-scoped RED-only task; root, backend, and source-code `AGENTS.md`; both source-code implementation plans; pushed `docs/analysis-steps/03-program-graphs.md` M4/M5 JavaBoundaryInvocation v3 contract.
- Current branch/worktree: `codex/source-analysis-program-graphs`; `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the required instructions, both plans, the M4/M5 boundary-v3 contract, and the existing data-flow public seam.
- Checked the dirty worktree and will preserve unrelated M1–M6 changes.
- Re-read the M4 boundary-v3 contract and the Terra debug conclusion supplied by the parent: this fixture has no Mapper `CALL` control-flow edge, so the test must instead assert the boundary record's M3 basic-block/control-pair contract.

## Current state

- The original RED exercised `DataFlowGraphBuilder.buildDataFlow` with real reopened predecessors and failed because no `JAVA_BOUNDARY_INVOCATION` node was emitted for an exact Mapper call, rather than because a future type was absent.
- The shared Terra implementation now exposes the v3 boundary record. The test's fixture-specific Mapper `CALL` lookup has been replaced with the published `BoundaryControlContextV1` requirement: exactly one referenced M3 `BASIC_BLOCK`, plus either a null/null control pair or a matching M3 guard branch pair.

## Changed files

- `backend-agents/sources/source-code/progress/m4-java-boundary-invocation-tests.md` (this file)
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java` (generic Mapper boundary RED)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest test` | RED | Exit 1; 10 tests run, 1 failure, 0 errors, 0 skipped. Only `stopsAnExactMapperCallAtOneGenericBoundaryWithItsJavaLocalArgumentOrigin` failed: expected one `JAVA_BOUNDARY_INVOCATION`, actual `[]`. |
| `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest#stopsAnExactMapperCallAtOneGenericBoundaryWithItsJavaLocalArgumentOrigin test` | GREEN | Exit 0; 1 test run, 0 failures, 0 errors, 0 skipped. |

## Decisions

- Start with one Mapper boundary invocation using existing fixtures; extend that same public test with a non-Mapper external call only if the fixture can express it without broad setup.
- Assert the closed behavior—not class existence—including generic kind/rule, exact ordered argument IDs and Java-local origins, control/call provenance, and absence of external-effect data edges.
- The existing one-argument Mapper fixture establishes the highest-value first RED without changing M3 fixtures. Non-Mapper shape, external return/use, and ambiguous-target cases remain separate RED slices.

## Blockers

- None.

## Exact next action

- Hand off the confirmed GREEN test correction. No production, fixture, POM, documentation, commit, or push changes were made by this agent.

## Resume checks

- Preserve the narrow scope if follow-up work is requested: only this progress file and the new `DataFlowGraphBuilderTest` test may be changed without a new parent instruction.
