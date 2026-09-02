# Progress: artifact-reference-red

- Status: COMPLETE
- Agent role: Luna/xhigh TDD RED-test owner for the artifact-foundation typed values
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01 (after mandated RED run)
- Scope: Add exactly one RED for the public `ArtifactReference` constructor's typed component retention and null-component validation.
- Approved inputs: `docs/DESIGN.md` typed-value contract; source-code scoped `AGENTS.md`; current `progress/source-analysis-artifact-foundation.md`; completed `ArtifactId` and `Sha256Digest` RED/GREEN records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the repository-root, backend, and source-code instructions; both implementation plans; the authoritative design; and the current artifact-foundation progress/status records.
- Confirmed `ArtifactReference` is not yet present and its focused test path is new.
- Created this task-owned progress record before modifying the test tree.

## Current state

- Added the single public-constructor RED test covering typed component retention and null-component validation. Production `ArtifactReference` remains intentionally absent.

## Changed files

- `progress/artifact-reference-red.md` (this task-owned progress record)
- `src/test/java/org/sourceanalysis/app/artifact/ArtifactReferenceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Existing artifact-foundation implementation, tests, and progress files were observed and preserved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ArtifactReferenceTest test` | EXPECTED RED | JDK 17/toolchain and main compilation passed; test compilation failed only with four missing `ArtifactReference` symbols, with no test execution. |

## Decisions

- Use one JUnit test for the documented constructor seam: retain the exact typed `ArtifactId` and `Sha256Digest` instances, and reject null in either component position with `IllegalArgumentException` rather than an incidental `NullPointerException`.
- Do not add or test policy, store, address, or other artifact behaviors.

## Blockers

- None.

## Exact next action

- Hand off this RED to the implementation owner; do not add production code in this task.

## Resume checks

- Preserve all unrelated worktree changes; the final scoped change must be this progress record plus `src/test/java/org/sourceanalysis/app/artifact/ArtifactReferenceTest.java`.
