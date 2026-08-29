# Progress: deterministic-baseline-core

- Status: COMPLETE
- Agent role: deterministic baseline core implementation
- Model: GPT-5.6 Terra (inherited)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Implement the minimum production API and deterministic baseline generation required by `DeterministicBaselineGenerationTest`; do not add CLI, archive, provider, documentation, or tests.
- Approved inputs: Existing frozen fixture manifest and snapshot root created by `MvpFixtures.valid`; existing targeted JUnit test.
- Current branch/worktree: shared worktree; pre-existing unrelated modifications are preserved.

## Completed

- Read repository, source-to-standard-markdown, and GitHub-code scoped instructions.
- Inspected the deterministic baseline test and existing MVP generation seam.
- Confirmed the existing targeted test fails at compilation because `BaselineGenerationRequest` is intentionally absent.
- Added a provider-free baseline request and agent entry point.
- Wired the default adapter to a deterministic core path that verifies the frozen manifest and evidence, renders the nine-section baseline, retains traces, and reuses candidate validation.
- Confirmed the baseline document visibly identifies itself as `确定性基线` and contains no model-provider invocation path.
- Passed the targeted baseline generation test.

## Current state

- The requested deterministic baseline core is implemented and verified. No CLI, archive, provider, test, or non-progress documentation changes were made by this task.

## Changed files

- `progress/deterministic-baseline-core.md`
- `src/main/java/com/linguan/codemd/mvp/BaselineGenerationRequest.java`
- `src/main/java/com/linguan/codemd/mvp/CodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/mvp/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/mvp/MvpGenerationCore.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=DeterministicBaselineGenerationTest test` | Expected red | Test compilation fails: `BaselineGenerationRequest` cannot be resolved. |
| `mvn -Dtest=DeterministicBaselineGenerationTest test` | Passed | 1 test run; 0 failures, 0 errors, 0 skipped. |

## Decisions

- Reuse existing frozen-manifest verification, candidate hashing, nine-section validation, and trace index mechanisms.
- Keep deterministic baseline prose generic, business-readable, and explicitly labelled `确定性基线`.
- Return no recorded model responses from the deterministic baseline path because it never accepts or invokes a `ModelProvider`.

## Blockers

- None.

## Exact next action

- Task complete; preserve unrelated shared-worktree changes.

## Resume checks

- Run `git status --short`, review this file, and rerun `mvn -Dtest=DeterministicBaselineGenerationTest test` before any follow-up work.
