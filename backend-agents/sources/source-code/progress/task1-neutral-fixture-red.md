# Progress: neutral business-flow test support RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Establish the narrow RED contract for neutral BusinessFlowTestSupport.
- Approved inputs: Approved cleanup Task 1 in the current implementation plan; existing flow publication public types.
- Current branch/worktree: codex/source-analysis-business-flows-closeout; /private/tmp/linguan-source-analysis-process-design

## Completed

- Read the source-scoped instructions and TDD guidance.
- Confirmed no neutral `org.sourceanalysis.app.testsupport.BusinessFlowTestSupport` exists.
- The first selector attempt reached test compilation but exposed an incorrect import in the new test; corrected it to the existing `analysis.flow.publish.BusinessFlowsReference` type.

## Current state

- Added one reflection-based contract test so the missing test-support seam fails as an assertion rather than a test-compile error.

## Changed files

- `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupportContractTest.java` (planned)
- `progress/task1-neutral-fixture-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest test` | RED setup error | Initial test compile failed only because `BusinessFlowsReference` is in `analysis.flow.publish`, not `analysis.flow`. Corrected before rerun. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest test` | RED | Final test compile succeeded; 1 test, 1 failure, 0 errors, 0 skipped. Failure is the expected missing `BusinessFlowTestSupport` assertion at line 25. |
| `git diff --check` | PASS | No whitespace errors in the task files. |

## Decisions

- Use reflection for the RED because the target neutral helper is intentionally not present yet; this keeps the test compiling while making the missing public seam explicit.
- Assert only the two approved `publishBusinessFlows` overloads and `reference` method; do not migrate existing tests in this task.

## Blockers

- None.

## Exact next action

- Terra may implement the neutral support seam; do not migrate existing tests or delete legacy code in this task.

## Resume checks

- Ensure only this test and this progress file are modified by this task.
- Confirm no production source or design file was touched.
