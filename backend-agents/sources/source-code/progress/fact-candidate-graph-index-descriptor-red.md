# Progress: Fact candidate graph-index descriptor RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Prove that a persisted graph-index descriptor graphId cannot diverge from its referenced public graph.
- Approved inputs: Current target design, current public ProgramGraphs fixture, current M1 reader seam.
- Current branch/worktree: codex/source-analysis-proven-code-facts / /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Added `FactCandidateGraphIndexDescriptorTest` with one public-seam test.
- Mutated only the DATA_FLOW graph-index descriptor `graphId` to a different valid ArtifactId; the referenced graph payload and `artifactRef` remain unchanged.
- Fresh persisted reopen reached the expected RED: the current reader accepted the inconsistent descriptor, so AssertJ reported `Expecting code to raise a throwable`.

## Current state

The RED test is complete and proves the missing M1 graph-index descriptor check through the canonical public stores.

## Changed files

- This progress file only.
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateGraphIndexDescriptorTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphIndexDescriptorTest test` | RED as expected | 1 test, 1 failure, 0 errors; current reader accepted forged descriptor and AssertJ reported `Expecting code to raise a throwable` |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Use `ProgramGraphsPublicFixture.republishMutatedGraphs` so the test exercises persisted public artifacts, not an in-memory graph or a reduced JSON object.
- Do not infer or test external system behavior; this is only graph-index lineage integrity.

## Blockers

## Exact next action

Sol/ultra or Terra/xhigh may implement the graph-index descriptor identity check in the next bounded M1 slice; this test must remain RED until that production change is authorized and made.

## Resume checks

- Read this file first.
- Confirm only this progress file and the new test are in scope.
- Preserve the recorded RED; do not alter production code or this test in this slice.
