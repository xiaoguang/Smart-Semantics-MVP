# Progress: JDT-first Java code engine implementation

- Status: IN_PROGRESS
- Agent role: Root coordinator
- Model: GPT-5
- Started: 2026-09-12T16:23:18Z
- Last updated: 2026-09-12T19:07:00Z
- Scope: Implement the approved JDT-first engine, then adapt the preserved JavaParser engine to the frozen neutral contract.
- Approved inputs: Java engine module designs, fixed jshERP snapshot, installed JDT LS 1.61.0/tool JDK, existing source-analysis code and tests.
- Current branch/worktree: codex/jdtls-source-navigation-feasibility at /private/tmp/linguan-source-analysis-process-design

## Completed

- Verified this is an existing linked worktree on the approved implementation branch.
- Reviewed the approved plan against the current runtime, discovery, Step03-Step05, Builder, storage and CLI seams.
- Confirmed the current runtime still hard-requires program graphs, Facts and strict Flow before business material generation.
- Collected the complete task-scoped research and design diff for the recoverable baseline; staged diff validation passed.

## Current state

- Feasibility research and detailed design exist; production engine configuration, JDT Core helper, neutral engine contracts and pipeline integration do not yet exist.
- Existing research and design changes are staged for the recoverable baseline commit. This checkpoint does not claim Java or research tests pass.
- Recoverable baseline commit `cec1997` now exists.
- Fresh pre-implementation module baseline passed: 358 tests, 0 failures, 0 errors, 0 skipped.
- Step 1.1 design synchronization is complete: the approved plan is now a ten-task executable document, and the entry, helper, index, availability and Step05 ownership contracts are frozen.
- Task 1 RED is accepted: three targeted test classes compile and report 17 intentional assertion failures for the missing engine configuration, factory and snapshot-bound JDT session production seams.
- Task 1 GREEN now passes all 17 direct tests. Strict YAML, the stage-one JDT-only factory, the neutral project/session contracts and owned-session cleanup are implemented; frozen-contract review is in progress before Task 2 starts.
- Task 1 final remediation is implemented. A bounded second RED exposed five assertions across the three remaining review groups; after correction the five-class selector passes 49 tests. Readiness now starts from JDT document symbols rather than source regexes, neutral parameter/body/control/argument references close, and distribution identity binds the Equinox launcher plus JDT LS/Core component bytes.

## Changed files

- progress/jdt-java-engine-implementation.md
- docs/plans/jdt-first-java-engine-implementation-plan.md
- Direct Java-engine, Step02-Step05 and scoped-rule design documents updated by the design owner.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse --git-dir; git rev-parse --git-common-dir; git branch --show-current` | PASS | Existing linked worktree; branch `codex/jdtls-source-navigation-feasibility` |
| Read-only design/code audit | PASS | Reuse boundaries and mandatory Step02-Step05 wire changes identified |
| `git diff --cached --check` | PASS | No staged whitespace errors across the 47-file research/design baseline |
| `mvn -t .mvn/toolchains.xml test` | PASS | 358 tests; 0 failures/errors/skips; build success in 5:17 |
| Step 1.1 design checks | PASS | Tasks 1–10 structurally complete; 80 local links checked with 0 broken; `git diff --check` passed |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | EXPECTED_RED | 17 tests; 17 assertion failures; 0 errors/skips; missing Task 1 production types |
| `mvn -t .mvn/toolchains.xml spotless:check` | PASS | 530 Java files clean after formatting the Task 1 production and test files |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | PASS | 17 tests; 0 failures/errors/skips; fresh coordinator rerun after final workspace metadata change |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` | PASS | 44 tests; 0 failures/errors/skips; fresh rerun after Spotless changed four files |
| `mvn -t .mvn/toolchains.xml spotless:check` | PASS | 533 Java files clean |
| `git diff --check` | PASS | No whitespace errors after final Task 1.2 formatting |
| Final bounded closure RED selector | EXPECTED_RED | 49 tests; 5 assertion failures; 0 errors/skips across three remaining review groups |
| Final bounded closure GREEN selector | PASS | 49 tests; 0 failures/errors/skips before final formatting |
| Final post-format five-class selector | PASS | 49 tests; 0 failures/errors/skips; build success |

## Decisions

- Execute exactly two phases: JDT independently first; JavaParser current-capability adapter second.
- Existing ActivityExplainer, ProcessExplainer, BusinessReportPublisher, store bootstrap and public Agent remain reusable and are not redevelopment tasks.
- First observable checkpoint is a real JDT-only complete Service body, before full persistence integration.

## Blockers

- None.

## Exact next action

- Commit the closed Task 1.2 slice, then start Task 2 RED for the standalone JDT Core syntax helper.

## Resume checks

- Read this progress file and the four documents under `docs/modules/java-code-engines/`.
- Confirm branch and worktree path before any edit.
- Inspect Git status; do not overwrite historical research output or other agents' progress files.
- Resume at the first incomplete numbered implementation step.
