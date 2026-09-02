# Progress: artifact-id-red

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add the single published `ArtifactId.parse(String)` RED behavior and record its targeted Maven result.
- Approved inputs: Published artifact typed-value contract in `docs/DESIGN.md` §13.3.1 and the artifact-foundation progress/status records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read repository, backend, and source-code instructions.
- Read the published typed-value contract and orchestrator progress/status.
- Confirmed no existing `ArtifactIdTest` or `ArtifactId` production type.
- Created this owned progress file before modifying test code.

## Current state

- Added the single RED test method for canonical parsing/toString and one uppercase-hex rejection.
- The targeted Maven RED run reached test compilation and failed as expected because `ArtifactId` remains intentionally unimplemented.

## Changed files

- `backend-agents/sources/source-code/progress/artifact-id-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/ArtifactIdTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing canonical JSON files and orchestrator progress remain untouched. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ArtifactIdTest test` | PASS (expected RED) | Exit 1 at `testCompile`; three `cannot find symbol` errors for missing `ArtifactId` type/parse calls, with no test execution. |

## Decisions

- Keep one JUnit test method covering valid canonical parse/toString plus representative noncanonical rejection, with no address, policy, store, or other identity cases.
- Assert the public exception category only; do not assert exception messages.

## Blockers

- None; the intended missing-production-type RED was established.

## Exact next action

- Hand off the RED to the implementation owner; do not add production code in this task.

## Resume checks

- Preserve all unrelated pre-existing worktree changes; the final scoped change is this progress file plus the new `ArtifactIdTest.java`.
