# Progress: JDT engine configuration tests

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-12
- Last updated: 2026-09-12 (P1 contract review retry complete)
- Scope: Task 1 RED tests for strict engine configuration, factory selection, and snapshot-bound JDT session behavior
- Approved inputs: `task-1-brief.md`; `contracts-and-configuration.md`; `jdt-engine.md`; Task 1 of `jdt-first-java-engine-implementation-plan.md`; baseline `b28d6da`
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility`

## Completed

- Read the scoped repository instructions, Task 1 brief, authoritative engine contracts, JDT design, and TDD guidance.
- Confirmed the worktree had only the pre-existing untracked progress file before edits.
- Retry identified: the previous attempt stopped after creating this progress file and did not create the three RED tests or run Maven.
- Added the three requested test classes with a test-only reflective harness so missing Task 1 production types surface as assertion failures after test compilation.
- First targeted Maven attempt exposed two test-only generic assertion compile errors in `JdtProjectSessionTest`; corrected them to compare normalized path strings.
- Second targeted Maven attempt exposed three checked-`Throwable` rethrow errors in the reflective session helper; corrected them with a test-only exception rethrow adapter.
- Targeted test compilation now succeeds; the current RED is assertion-only against absent Task 1 production types.
- Final targeted run confirms 17 assertion failures and zero test errors/skips; report written at the requested SDD path.
- Task 1 GREEN implementation is now present; the three owned test files were formatted with scoped Spotless and the focused selector passes all 17 tests.
- Final `git diff --check` passes.
- Parent review identified six P1 contract defects in the GREEN implementation; this retry adds bounded RED coverage without changing production.
- Added the authorized `JavaCodeEngineContractTest` with reflection-based checks for verified-source-text admission/projection/fingerprints, JDT roots/readiness/isolation, neutral catalog/context fields, entry-method containment, and descriptor tool versions.
- Extended configuration RED coverage for Equinox launcher cardinality, platform configuration, bounded Java 21 version validation, and direct `JdtConfiguration` validation bypasses.
- Scoped Spotless completed successfully for all four owned test files.
- Focused review selector compiled successfully and produced assertion-only RED: 31 tests, 14 failures, 0 errors, 0 skipped. The original strict YAML/factory/session assertions remain green (17 tests).
- `git diff --check` passes.

## Current state

- Existing three test files remain owned by this progress record; the one additional bounded `JavaCodeEngineContractTest` is authorized for the P1 review. No production/design/POM files were modified by this agent.
- The existing 17 strict YAML/factory/session tests are green against the current implementation; the review additions intentionally fail only at assertions for the six frozen-contract defect groups.

## Changed files

- `progress/jdt-engine-configuration-tests.md`
- `src/test/java/org/sourceanalysis/app/runtime/EngineConfigurationLoaderTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactoryTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/code/JavaCodeEngineContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Clean baseline at `b28d6da` before this progress file |
| `mvn -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | BLOCKED_BEFORE_COMPILE | Repository has no configured JDK 17 Maven toolchain |
| `mvn -t /private/tmp/jdt-task1-toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | RED | testCompile PASS; 17 run, 17 failures, 0 errors, 0 skipped; failures are assertions for missing EngineConfigurationLoader/VerifiedJavaProject types |
| `mvn -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/runtime/EngineConfigurationLoaderTest.java,src/test/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactoryTest.java,src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java` | PASS | Scoped Spotless formatting completed |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | PASS | 17 run, 0 failures, 0 errors, 0 skipped |
| `git diff --check` | PASS | No whitespace errors |
| `mvn -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/runtime/EngineConfigurationLoaderTest.java,src/test/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactoryTest.java,src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java,src/test/java/org/sourceanalysis/app/analysis/code/JavaCodeEngineContractTest.java` | PASS | Scoped formatting completed for all four owned test files |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest test` | RED | testCompile PASS; 31 run, 14 assertion failures, 0 errors, 0 skipped; failures cover the six frozen review defect groups |
| `git diff --check` | PASS | No whitespace errors after final formatting |

## Decisions

- Tests will exercise public configuration/factory/session seams with real YAML bytes and temporary filesystem fixtures.
- A narrow fake external-process boundary may be used only to keep JDT startup deterministic; tests must assert session/factory outcomes and failure codes.

## Blockers

- None. The repository `.mvn/toolchains.xml` enabled the bounded compile/test verification; prior missing-toolchain output is preserved above as historical evidence.

## Exact next action

- Hand off the completed Task 1 RED contract evidence to the parent agent; implementation must address the six frozen P1 groups before the selector can turn GREEN.

## Resume checks

- Confirm no production, POM, design, or unrelated test files are modified.
- Confirm RED reaches test assertions rather than failing only because of test typos or missing test dependencies.
