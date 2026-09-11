# Progress: cross-flow M6 tiered RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Public-seam RED tests for program-only cross-Flow candidate compilation
- Approved inputs: Reopened Step 03 and Step 05 publications; Step 06 process-join signal contract
- Current branch/worktree: codex/source-analysis-business-flows-closeout / shared Step 05 worktree

## Completed

- Read the scoped Agent rules and the Step 06/07 cross-Flow contracts.
- Confirmed the existing test only checks a missing class and does not lock the semantic-priority tiers.

## Current state

- Replaced only `CrossFlowCandidateCompilerTest.java` with four bounded public-seam RED tests; the test invokes the compiler through constructor injection of the fixture's module and analysis-step stores and does not lock a no-arg constructor.
- The exact-entry fixture has two Flow slices and a persisted direct-call signal; no upstream fixture was changed.
- The zero-Flow case is produced through the existing public Flow compiler profile with a one-node bound; it reaches the M6 seam with an empty Flow publication.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/CrossFlowCandidateCompilerTest.java`
- `progress/cross-flow-m6-tiered-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=CrossFlowCandidateCompilerTest test` | RED (expected) | testCompile succeeded; 4 tests, 4 failures, 0 errors/skips; all failures are `CROSS_FLOW_CANDIDATE_COMPILER_NOT_IMPLEMENTED` |
| `mvn -t .mvn/toolchains.xml spotless:apply` | PASS | only this test was formatted |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/process/CrossFlowCandidateCompilerTest.java progress/cross-flow-m6-tiered-red.md` | PASS | no whitespace errors |

## Decisions

- The test will require exact direct entry-target calls to be `PROVEN_HANDOFF` only when the public fixture exposes that relation.
- Non-low-information corroboration may produce only `SEMANTIC_CUE`/`PENDING_ONLY`; it cannot prove ordering or external effects.
- Tenant, audit, logger, utility, and name-only similarities cannot create candidate edges.
- Zero Flow is a successful empty result; invalid entry targets fail closed.
- No production, design, POM, schema, fixture, model, network, or customer-source changes are in scope.

## Blockers

- The current public fixture does not yet expose a safe mutation hook for deleting the exact entry-root edge. The missing-target negative remains documented as a deferred public-mutation assertion; no upstream fixture was modified.

## Exact next action

- Hand the RED to Terra: add the documented `CrossFlowCandidateCompiler.compileCandidates(request)` seam and make the four tests green without changing fixture inputs.

## Resume checks

- Re-open only the Step 03/05 publications from disk.
- Confirm the test remains the only source/test file changed by this task.
- The selector reached Surefire after constructor-injection correction and failed only at the absent M6 production seam; the zero-Flow precondition passed, so this is not a broken fixture. The compiler lookup requires a public constructor accepting the fixture stores, in line with Step 06 §5.3.
