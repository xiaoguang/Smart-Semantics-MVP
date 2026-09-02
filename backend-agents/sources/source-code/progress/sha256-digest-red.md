# Progress: sha256-digest-red

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly one RED behavior for `Sha256Digest.parse(String)` and record its targeted Maven result.
- Approved inputs: Published typed-value contract in `docs/DESIGN.md` §13.3.1; `progress/source-analysis-artifact-foundation.md`; prior typed-identity progress records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the repository, backend, and source-code instructions; the published typed-value contract; both source-code implementation plans; the orchestrator progress/status; and prior typed-identity progress records.
- Confirmed the contract: `Sha256Digest` accepts exactly 64 lowercase hexadecimal characters with no prefix, preserves the exact canonical text in `toString()`, and rejects a prefixed `sha256:<hex>` value.
- Created this task-owned progress record before modifying the test tree.

## Current state

- Added the single RED test in `src/test/java/org/sourceanalysis/app/artifact/Sha256DigestTest.java`; production `Sha256Digest` remains intentionally absent.
- Ran the exact targeted Maven selector and established the expected missing-symbol compile RED.

## Changed files

- `backend-agents/sources/source-code/progress/sha256-digest-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Existing artifact-foundation worktree changes were observed and will be preserved. |
| `git status --short` after test creation | PASS | Only this progress record and `Sha256DigestTest.java` are task-owned additions; all prior worktree changes remain untouched. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Sha256DigestTest test` | EXPECTED RED | JDK 17 toolchain and main compilation passed; test compilation failed with three `cannot find symbol` errors for the intentionally absent `Sha256Digest` type, with no test execution. |

## Decisions

- Use one JUnit test method covering canonical parsing, exact `toString()` retention, and rejection of the `sha256:` prefixed form through the public `IllegalArgumentException` category only.
- Do not add address, policy, store, or other identity cases.

## Blockers

- None.

## Exact next action

- Hand off the RED to the implementation owner; do not add production code in this task.

## Resume checks

- Preserve all unrelated pre-existing worktree changes; the final scoped change is this progress record plus the new `Sha256DigestTest.java`.
