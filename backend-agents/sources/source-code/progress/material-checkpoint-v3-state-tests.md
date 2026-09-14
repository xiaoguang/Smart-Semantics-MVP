# Progress: material checkpoint v3 state tests

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: GPT-5.6 Luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Plan step 1 only — explicit offline repository-run-state-v2 to v3 export and direct v3 M10 material reopening. Do not modify production code, JDT, Builder, Provider, or model execution.
- Approved inputs: `docs/modules/model-job-execution.md` §7, current `RepositoryRunMain`, the typed `BusinessMaterialCheckpointReader`, and deterministic local fixtures/artifact stores.
- Current branch/worktree: Shared formal checkout at `linguan-prototype-v2/backend-agents/sources/source-code`; preserve unrelated parent-agent changes.

## Completed

- Re-read the authoritative §7 fixed-material/model-batch contract and the current state-v2 writer/reader in `RepositoryRunMain`.
- Confirmed the typed M10 reader is now present and direct, but state-v3 export/direct model-only composition is not yet implemented.
- Created this independent progress record before adding tests.

## Current state

- The focused package-private `RepositoryRunStateV3` seam is implemented and GREEN. It uses real local Step05/M10 publications, preserves the v2 file, and reopens the new v3 material checkpoint without upstream execution.

## Changed files

- `progress/material-checkpoint-v3-state-tests.md`
- `src/test/java/org/sourceanalysis/app/adapter/cli/MaterialCheckpointStateV3Test.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -q -t .mvn/toolchains.xml -o -Dmaven.compiler.testExcludes=**/ReviewedModelJobReuseTest.java -Dtest=MaterialCheckpointStateV3Test test` | RED (expected) | 3 test methods, 3 failures, 0 errors, 0 skipped. All failures are the explicit `REPOSITORY_RUN_STATE_V3_NOT_IMPLEMENTED` assertion caused by `ClassNotFoundException: org.sourceanalysis.app.adapter.cli.RepositoryRunStateV3`. The selector required excluding unrelated pre-existing `ReviewedModelJobReuseTest.java`, which currently fails compilation on `IterableAssert<JsonNode>.isObject()`. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=MaterialCheckpointStateV3Test test` | BLOCKED before test execution | Maven test compilation stops in unrelated `ReviewedModelJobReuseTest.java` (`IterableAssert<JsonNode>` has no `isObject()`); the new test itself compiles. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=MaterialCheckpointStateV3Test test` | GREEN | 3 tests, 0 failures, 0 errors, 0 skipped. |

## Decisions

- State-v3 tests will assert exact field closure and the full typed references/profile/version/basis required by §7.2.
- The export test must preserve the v2 file and read the v3 file directly; it must reject mismatched base configuration, source/Step05 ownership, and invalid M10 references. The explicit seam contract is `RepositoryRunStateV3.exportV2ToV3(...)` plus `RepositoryRunStateV3.reopenV3Materials(...)` so no CLI/JDT/model execution is needed.
- Reopen assertions will use the existing M10 reader and a counting artifact-store wrapper, so a passing implementation demonstrates no Builder/JDT/upstream execution.

## Blockers

- Target package-private export/direct-read seam is not present in current production. RED discovers the intended seam reflectively and fails with the precise `REPOSITORY_RUN_STATE_V3_NOT_IMPLEMENTED` capability message.

## Exact next action

- No further action; production state export/direct reopen is implemented.

## Resume checks

- Final check: only this progress file and `MaterialCheckpointStateV3Test.java` are added by this task; all parent-agent changes remain untouched.
