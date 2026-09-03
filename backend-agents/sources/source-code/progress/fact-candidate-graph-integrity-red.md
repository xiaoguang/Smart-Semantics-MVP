# Progress: Fact candidate persisted graph integrity RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded M1 RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add only public-seam RED coverage for persisted CALL_TARGET endpoint closure in Fact M1. Evidence support-kind closure is deferred to a separate RED.
- Approved inputs: `AGENTS.md`, `docs/plans/source-analysis-naming-and-delivery-plan.md`, `docs/plans/target-standards-and-toolchain-plan.md`, `docs/analysis-steps/04-proven-code-facts.md`, existing M1 reader/enumerator tests, and the public graph fixture.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the scoped rules, TDD instructions, M1 exact-join contract, independent review, and existing public persisted fixtures.
- Added the test-only complete-public-publication republish helper to `ProgramGraphsPublicFixture` and one focused ghost-endpoint mutation test.
- Replaced the earlier two-mutation test with the narrower `FactCandidateGhostEndpointTest` requested by the parent task.

## Current state

- The focused selector runs one test and fails only because `reopen` returns normally for the malformed persisted CALL graph relation.

## Changed files

- `progress/fact-candidate-graph-integrity-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateGhostEndpointTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing M1 uncommitted work preserved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphIntegrityTest test` | HISTORICAL RED | Earlier two-test selector reached 2 assertion failures; that test was replaced by the narrower ghost-only selector. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGhostEndpointTest test` | RED | 1 test, 1 assertion failure, 0 errors/skips; `reopen` returned normally instead of raising `PROOF_PACK_REFERENCE_BROKEN`. |
| `git diff --check` | PASS | No whitespace errors in owned test/fixture/progress edits. |

## Decisions

- Cover exactly two persisted public mutations: ghost `CALL_TARGET.toNodeId` and node-support kind used for an edge subject.
- Do not modify production code, design documents, POM, or existing tests.
- Do not assert or infer behavior outside the frozen Java graph boundary.

## Blockers

- Production M1 reader currently accepts the ghost CALL_TARGET endpoint; Terra must add the minimal fail-closed check before GREEN. Evidence support-kind remains out of this slice.

## Exact next action

- Parent agent should dispatch the corresponding Terra GREEN against `FactCandidateGhostEndpointTest`; this RED task makes no production or design changes.

## Resume checks

- Re-read this file, inspect only owned test/fixture changes, and run the named selector before any further action.
