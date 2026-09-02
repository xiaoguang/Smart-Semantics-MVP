# Progress: analysis-step-address-red

- Status: COMPLETE
- Agent role: Luna/xhigh TDD RED-test owner for the published analysis-step module address
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly one RED behavior for `AnalysisStepModuleAddress` using the closed module registry; modify only this progress record and its new focused test.
- Approved inputs: Source-code `AGENTS.md`; published `docs/DESIGN.md` §13.3.1; both source-code implementation plans; `progress/source-analysis-artifact-foundation.md`; and neighboring typed-identity RED/GREEN progress records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the repository, backend, and source-code instructions, the authoritative address and module registry mapping, both implementation plans, and the artifact-foundation progress/status records.
- Confirmed `PROGRAM_GRAPHS` maps module number `6` to module key `publish`; module number `3` is `control-flow`, so `3/publish` is an invalid pair.
- Created this task-owned progress record before editing the test tree.
- Added the single requested test with the accepted `6/publish` address and the rejected `3/publish` pair.
- Ran the exact offline selector and established the expected test-compilation RED because `AnalysisStepModuleAddress` remains unimplemented.
- Tightened the fixture to construct both addresses with the exact nested public constructor shape and reran the same selector; the expected missing-class RED remained unchanged.

## Current state

- The production `AnalysisStepModuleAddress` remains intentionally absent in this worktree.
- The focused RED test will construct the accepted address through its public components, assert all components, and assert that the mismatched `3/publish` pair throws `IllegalArgumentException`.

## Changed files

- `backend-agents/sources/source-code/progress/analysis-step-address-red.md` (this task-owned progress record)
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/AnalysisStepAddressTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Existing artifact-foundation files remain untracked pre-existing work; neither task-owned address path existed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisStepAddressTest test` | EXPECTED RED | JDK 17/toolchain and main compilation passed; test compilation failed only with three missing `AnalysisStepModuleAddress` symbols, with no test execution. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisStepAddressTest test` (after exact constructor-shape fixture) | EXPECTED RED | Same three missing `AnalysisStepModuleAddress` symbols at test compilation; no test execution. |
| `git diff --no-index --check /dev/null backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/AnalysisStepAddressTest.java` | PASS | No whitespace diagnostics; exit 1 is the expected new-file difference status. |
| `git diff --no-index --check /dev/null backend-agents/sources/source-code/progress/analysis-step-address-red.md` | PASS | No whitespace diagnostics; exit 1 is the expected new-file difference status. |
| `git status --short -- backend-agents/sources/source-code/progress/analysis-step-address-red.md backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/AnalysisStepAddressTest.java` | PASS | Only the two task-owned paths are present in the scoped status. |

## Decisions

- Use exactly one JUnit test named `acceptsRegisteredModulePairAndRejectsStepOrderAsModuleNumber`.
- Use the exact public constructor shape and `IllegalArgumentException` category from the request; do not add parse, path, text, policy, or store tests.
- Run only `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisStepAddressTest test` and retain the expected missing-class compilation RED.

## Blockers

- None.

## Exact next action

- Hand off this RED to the implementation owner; do not add production code or additional tests.

## Resume checks

- Preserve all unrelated pre-existing worktree changes.
- Confirmed the final scoped change is only this progress record and `src/test/java/org/sourceanalysis/app/artifact/AnalysisStepAddressTest.java`; no production code or other tests were added.
