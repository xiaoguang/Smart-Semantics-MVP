# Progress: pre-reset-wire-rejection-red

- Status: COMPLETE
- Agent role: Luna/xhigh TDD — pre-reset wire rejection RED
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Define the public fail-closed seam for rejecting pre-reset and unknown analysis wire descriptors.
- Approved inputs: Published semantic naming design, source-scoped AGENTS.md, both implementation plans, DESIGN.md sections 0 and 13, and SourceAnalysisArchitectureTest.
- Current branch/worktree: codex/source-analysis-semantic-cutover / /private/tmp/linguan-source-analysis-semantic-cutover

## Completed

- Added the public-seam RED test with one positive current-header case and six parameterized pre-reset/unknown descriptor cases.
- Ran the exact targeted Maven selector and captured the expected test-compile RED because the production guard and exception types do not exist yet.
- Reopened this task for the approved hardening pass.
- Added six current-header-plus-pre-reset-discriminator cases, one for each existing legacy category.
- Extended the architecture regression test to inspect production/test package declarations and test fixture paths, with a temporary-tree test proving Java string literals are not scanned.

## Current state

- The hardening test changes produced the required runtime RED; the current production guard accepts the current header regardless of additional legacy discriminator fields.

## Changed files

- `backend-agents/sources/source-code/progress/pre-reset-wire-rejection-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/PreResetWireRejectionTest.java`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/SourceAnalysisArchitectureTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=PreResetWireRejectionTest test` | RED (expected) | `BUILD FAILURE` during `testCompile`; six `cannot find symbol` errors for the anticipated `AnalysisWireFormatGuard` and `UnsupportedAnalysisWireException` types at `PreResetWireRejectionTest.java:24,27-28,42`. Main compile and JDK 17 toolchain selection passed. |
| `mvn -t .mvn/toolchains.xml -Dtest=PreResetWireRejectionTest,SourceAnalysisArchitectureTest test` | RED (expected) | `BUILD FAILURE`; 15 tests run, 6 failures, 0 errors, 0 skipped. `SourceAnalysisArchitectureTest`: 2/2 passed, including temporary-tree package/path scanner. `PreResetWireRejectionTest`: six current-header legacy-discriminator cases failed at line 153 with `Expecting code to raise a throwable`; all original rejection cases and current-header positive passed. Main/test compilation and JDK 17 toolchain passed. |
| `git diff --check` | NOT RUN | No formatter or additional verification was requested for this RED-only slice. |

## Decisions

- The test will require acceptance of only the exact current envelope header `wireKind=SOURCE_ANALYSIS` and `wireVersion=v1`, while allowing other current descriptor fields.
- Rejection cases will be represented as descriptors and asserted through the same stable `UNSUPPORTED_ANALYSIS_WIRE` code; the production guard need not contain legacy token lists.
- The test will use only the public guard and exception seam, with no filesystem or production installation implementation.
- Architecture checks inspect only POM text, Java package declarations, and `src/test/resources` relative paths; Java string literal content is deliberately out of scope so rejection fixtures can retain legacy examples.

## Blockers

- None.

## Exact next action

- Hand the runtime RED to the Terra implementation agent; it must make the guard reject legacy discriminator-bearing descriptors without adding a legacy-token parser or changing the architecture scanner contract.

## Resume checks

- Verify only this progress file and the named test are modified.
- Verify no formatter, commit, push, production, POM, or design change was made; only this progress file and the two named test files are owned by this task.
