# Progress: program-graph local-gap coverage tests

- Status: COMPLETE (two bounded local-gap coverage REDs captured)
- Agent role: Luna/xhigh bounded public-builder test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Strengthen exactly two existing ControlFlow/DataFlow builder tests so complete, repository-eligible fixtures with nonempty local Gap dispositions explicitly require empty `scopeGapIds` and `coverage.closed=true`. Preserve all existing local Gap, ownership, and boundary assertions.
- Approved inputs: Published Step 03 coverage contract, completed bounded closure diagnosis, and the existing named builder tests/fixtures. No new fixture, source, production, design, schema, version, or unrelated test changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

## Current state

- Read-only inspection confirms both named fixtures use `COMPLETE_CAPTURE` with `repositoryCompletionEligible=true`; their structure/call inputs have empty `coverage.scopeGapIds`, while the existing tests already prove real local unsupported-syntax Gap dispositions.
- Current ControlFlow/DataFlow builders derive `coverage.closed` from local Gap emptiness plus eligibility, so the new assertions are expected to expose the published contract mismatch while preserving local Gap accounting.
- Added the approved assertions to both named tests: each verifies complete/eligible source and empty upstream scope gaps before building, then requires empty builder `scopeGapIds` and `coverage.closed=true` after the existing nonempty local Gap assertions.
- The combined selector confirmed both fixtures satisfy their complete/eligible and empty-scope premises; the two new `closed=true` assertions are the only failures. The bounded publication and provenance selectors passed independently.

## Changed files

- `progress/program-graph-local-gap-coverage-tests.md` (owned; created/add-N before test edits)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java` (owned; additive assertions in the named shared unsupported-guard test)
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java` (owned; additive assertions in the named unsupported mapper-boundary test)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only Step 03 contract/fixture inspection | PASS | Complete eligible fixtures and empty upstream `scopeGapIds` confirmed; named tests already assert nonempty local Gap behavior. |
| Absolute two-file Spotless apply | PASS | Numeric exit 0; exactly the two owned Java paths selected, both already clean. |
| Absolute two-file Spotless check | PASS | Numeric exit 0; exactly the two owned Java paths selected and clean. No test command run. |
| Scoped diff check | PASS | `git diff --check` clean for the owned progress and two test files after formatting and the combined selector. |
| Combined exact selector (session `88052`) | EXPECTED RED | Numeric exit 1; `Tests run: 8, Failures: 2, Errors: 0, Skipped: 0`. `BusinessFlowProvenanceTest`: 5/0/0/0; `BoundedBusinessFlowPublicationTest`: 1/0/0/0; the two graph methods each 1 failure at `ControlFlowGraphBuilderTest.java:237` and `DataFlowGraphBuilderTest.java:782`, both observed `closed=false` instead of `true`. |

## Decisions

- Add only direct non-vacuous assertions inside the two named tests: source scope/eligibility, empty upstream `scopeGapIds`, empty builder `scopeGapIds`, and `coverage.closed=true` after local Gap assertions.
- Do not alter fixtures, source bytes, production builders, existing Gap expectations, or any other tests.
- Formatting and the exact combined selector are complete; Java remains frozen and the Maven lease is released.

## Blockers

## Exact next action

- Terra should correct only the production builder closure predicate under its separate implementation task, then root may rerun the exact two graph selectors. Preserve this 8-test report as the baseline RED.

## Resume checks

- Preserve all existing local Gap owner, candidate, locator, disposition, edge, and boundary assertions. If either fixture is not actually complete/eligible or has nonempty scope gaps, stop and report rather than inventing the premise.
- No production, fixture, source, design, schema, version, unrelated test, or full-suite action is authorized.
- The bounded/provenance passes are not part of this production fix claim; they only confirm the combined verification remained isolated.
