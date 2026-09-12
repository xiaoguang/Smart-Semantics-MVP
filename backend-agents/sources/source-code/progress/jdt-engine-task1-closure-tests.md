# Progress: Task 1.2 closure RED tests

- Status: COMPLETE
- Agent role: bounded Task 1.2 contract-test owner
- Scope: Three final review findings only: JDT declaration readiness must use document symbols, neutral context records must validate body/control/argument references, and JDT distribution identity must include core-plugin content.
- Worktree: `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Changes

- Extended the fake JDT session protocol with a document-symbol response and a source fixture whose first textual type-looking token is inside a comment. The readiness test requires a document-symbol request and the declaration probe to use the returned selection position.
- Added neutral-contract assertions for parameter annotation text serialization, body-included targets, control-index bounds, and actual/formal argument-association bounds.
- Added a configuration test that mutates a JDT LS core plugin while keeping the Equinox launcher unchanged and requires the distribution identity to change.

No production code, design, Task 1.3, commit, or push was changed by this task.

## Verification

`mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` compiled and ran 49 tests: 5 expected assertion failures, 0 errors, 0 skipped. The failures isolate the three approved closure groups: document-symbol readiness, parameter/body/control/argument invariants, and core-plugin-bound distribution identity. The previously green 44 assertions remain green.

## Next action

Production owner implements only these five failing assertions, then reruns the same selector.
