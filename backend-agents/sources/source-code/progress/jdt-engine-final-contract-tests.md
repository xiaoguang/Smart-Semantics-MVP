# Progress: Task 1.2 final RED contract tests

- Status: COMPLETE
- Agent role: TDD RED contract-test owner
- Model: GPT-5
- Date: 2026-09-12
- Scope: Final Task 1.2 RED coverage for the six closed Astra P1 findings. Test sources and this progress record only; no production, design, Task 2, commit, or push.
- Approved inputs: Task 1 plan and RED/GREEN records, `docs/modules/java-code-engines/contracts-and-configuration.md` §§2.1, 3.1–3.3, `docs/modules/java-code-engines/jdt-engine.md`, current Task 1 production seams, and the four existing Task 1 test classes.
- Worktree: `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read `AGENTS.md`, the approved Task 1 contracts, all current Task 1 production seams, and the four existing test classes before editing.
- Ran the existing four-class selector first: 31 tests, 0 failures, 0 errors, 0 skipped.
- Extended `JdtProjectSessionTest` through its package-private process-isolation seam with behavior tests for reliable declaration readiness, empty/unready/timed-out probes, absolute and multiple source roots, classpath order/content identity and pre-start drift rejection, concrete JDK/JDT tool identities, and startup stop-failure observability.
- Added `JdtEngineFinalContractTest` with direct typed-record behavior and serialized-wire assertions for the §2.1/§3.1–3.3 method, call, target, catalog, and technical-enhancement contract. It rejects duplicate method keys, dangling caller/target references, and AVAILABLE enhancements without real references.
- The new tests use direct public/package seams and real temporary files/fake LSP behavior; they do not use reflection-based shape assertions and do not require Task 2 catalog extraction.

## Current state

The final RED is clean: test compilation succeeds, failures are assertion failures only, and every failure belongs to one of the six requested P1 groups. Existing Task 1 behavior remains green outside those findings.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtEngineFinalContractTest.java`
- `progress/jdt-engine-final-contract-tests.md`

No production, design, POM, Task 2, commit, or push was performed.

## Verification

| Command | Result | Evidence |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest test` | PASS | Baseline existing four classes: 31 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtProjectSessionTest,JdtEngineFinalContractTest test` | RED | 18 tests, 11 assertion failures, 0 errors, 0 skipped; test compilation succeeded. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` | RED | 44 tests, 11 failures, 0 errors, 0 skipped; the original 31 remain green. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java,src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtEngineFinalContractTest.java` | PASS | Both owned test files formatted. |
| `mvn -o -t .mvn/toolchains.xml spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java,src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtEngineFinalContractTest.java` | PASS | Both owned test files pass Spotless. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSessionTest.java src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtEngineFinalContractTest.java progress/jdt-engine-final-contract-tests.md` | PASS | No whitespace errors. |

## RED evidence by P1

| P1 | RED evidence in the final selector |
| --- | --- |
| 1. Readiness | `readinessUsesAReliableDeclarationPositionBeforeOpeningTheSession` observes the current probe at character 0 instead of a declaration-bearing position; `anEmptyDeclarationReadinessResponseCannotOpenASession` observes an empty declaration response accepted as success. Unready and declaration timeout cases already fail safely and remain green evidence. |
| 2. Neutral catalog/context | `serializedNeutralMaterialCarriesTheCompleteMethodCallTargetAndCatalogContract` finds missing method/call/target/catalog wire fields; duplicate method keys, dangling caller/target references, and AVAILABLE-without-real-reference tests each fail because current records accept them. |
| 3. Source roots | `rejectsAbsoluteSourceRootsBeforeStartingJdt` observes an absolute source-root hint being inferred/accepted; the separate two-root test confirms valid relative roots are not folded. |
| 4. Classpath identity | `classpathFingerprintIncludesEffectiveOrderAndFileContents` observes equal fingerprints for reversed classpath order and changed bytes; `rejectsClasspathContentDriftBeforeStartingJdt` observes startup proceeding after same-path content drift. |
| 5. Tool identity | `descriptorReportsActualToolIdentitiesInsteadOfVersionlessDirectoryNames` observes `jdk`/`jdtls` directory basenames instead of fake JDK `21.0.8` and launcher identity. |
| 6. Startup cleanup | `startupCleanupFailureRemainsObservableWhenTheProcessCannotBeStopped` leaves a deliberately sticky fake process alive and observes no cleanup/suppressed evidence in the startup failure. |

## Decisions

- Kept Task 2 out of scope: readiness tests use a deterministic fake declaration response and only check startup readiness semantics and the queried position.
- Kept the final selector bounded to the five Task 1 classes; no full repository suite, customer source, network, model/provider, commit, or push was used.

## Exact next action

- Hand this final RED evidence to the parent agent for the six-P1 GREEN implementation review.

## Resume checks

- Confirm only the two test files and this progress record are owned by this task.
- Re-run the five-class selector with `.mvn/toolchains.xml`; expected current state remains 44 tests / 11 assertion failures / 0 errors / 0 skips until the P1 production implementation changes.
