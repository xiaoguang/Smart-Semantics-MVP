# Progress: analysis-run-id-red

- Status: COMPLETE
- Agent role: TDD RED test owner for the fixed-prefix analysis-run identity
- Model: gpt-5 / current Codex agent
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly one RED behavior for `AnalysisRunId.parse(String)` and record its targeted Maven result.
- Approved inputs: Published typed-value contract in `docs/DESIGN.md` §13.3.1; `progress/source-analysis-artifact-foundation.md`; existing generic `ArtifactId` RED/GREEN records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the repository, backend, and source-code instructions; the artifact-foundation design brief; both source-code implementation plans; the orchestrator status; and the prior typed-identity progress records.
- Confirmed the fixed-prefix contract: `AnalysisRunId` accepts only `analysis-run:<64 lowercase hex>` and rejects a valid generic content ID with another fixed prefix.
- Created this progress record before modifying the test tree.
- Added one public-seam test covering canonical parse, exact `toString()` retention, and rejection of the valid wrong fixed prefix.
- Ran the exact selector and established the expected RED at test compilation because `AnalysisRunId` is absent.

## Current state

- Added the requested single `AnalysisRunIdTest` behavior. The test is expected to fail at test compilation because `AnalysisRunId` remains intentionally unimplemented.

## Changed files

- `backend-agents/sources/source-code/progress/analysis-run-id-red.md` (this record)
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/AnalysisRunIdTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing artifact-foundation implementation, tests, and progress files observed and preserved. |
| `git status --short` after adding the test | PASS | Only this progress record and `AnalysisRunIdTest.java` are new task-owned paths; prior foundation files remain untouched. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisRunIdTest test` | EXPECTED RED | JDK 17 toolchain and main compilation passed; test compilation failed only with three `cannot find symbol` errors for the intentionally absent `AnalysisRunId` type. |

## Decisions

- Use one JUnit test method covering valid parsing/exact `toString()` retention and wrong fixed-prefix rejection through the public `IllegalArgumentException` category only.
- Use the hand-authored `analysis-run:` and `module-root:` fixtures directly; do not test other IDs, addresses, policy, stores, or exception messages.
- Do not modify production code or any file outside this progress record and the new `AnalysisRunIdTest.java`.

## Blockers

- None; the intended missing-production-type RED was established.

## Exact next action

- Hand off this RED to the implementation owner; do not add production code in this task.

## Resume checks

- Inspect the focused test and progress diff; confirm only the two task-owned paths changed and no production workaround was added.
