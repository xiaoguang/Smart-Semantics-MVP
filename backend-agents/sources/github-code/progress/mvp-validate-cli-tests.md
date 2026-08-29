# Progress: mvp-validate-cli-tests

- Status: COMPLETE
- Agent role: TDD test writer for the offline `code-md validate` CLI command
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add strict recorded-provider CLI validation tests and this progress file only
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; `progress/mvp-integration.md`; `progress/mvp-core.md`; `progress/mvp-persistence-core.md`; current Java sources and tests; synthetic fixtures only
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is untracked and shared

## Completed

- Read the inherited and scoped repository rules, required progress records, Maven configuration, and all current Java production/test sources.
- Confirmed the current CLI exposes recorded `generate` and archived `trace`, but no `validate` subcommand.
- Added `CodeMdCliValidateTest` with fresh-instance success and tampered-Markdown contracts.
- Confirmed the new test compiles and runs against the current implementation.

## Current state

The test generates an archived workspace from recorded R1/R2 responses, invokes validation through fresh CLI instances, asserts JSON receipts and nonzero tamper handling, and proves the tampered Markdown remains unchanged. The CLI's standard output is checked independently from Picocli error output. Both tests are intentionally RED until the production `validate` command exists.

## Changed files

- progress/mvp-validate-cli-tests.md
- src/test/java/com/linguan/codemd/mvp/CodeMdCliValidateTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing root and shared target changes preserved. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CodeMdCliValidateTest test` | RED (expected) | 2 tests, 0 errors: valid path returns 2 because `validate` is not registered; tampered path has no stdout JSON receipt, proving the receipt contract is absent. |

## Decisions

- Keep the test in a new `CodeMdCliValidateTest` class so the command contract is isolated from existing persistence and trace coverage.
- Use the existing synthetic `MvpFixtures` and recorded-round generation helper; do not read customer source or invoke a model/network.
- Parse CLI stdout as one canonical JSON receipt and inspect the persisted `validation-receipt.json`; `document.md` is the only expected Markdown exception to the JSON intermediate-artifact rule.

## Blockers

- None.

## Exact next action

Production implementation owner should make the new selector GREEN; this task must not modify production code.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Inspect the new test and rerun only its Maven selector after the production command is implemented.
