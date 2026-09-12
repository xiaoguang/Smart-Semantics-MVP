# Progress: JDT engine configuration implementation

- Status: COMPLETE
- Agent role: Task 1 GREEN implementation
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12 (final Task 1.2 selector green after fixture migration and formatting)
- Scope: Task 1 P1 review remediation only: verified-source project authority, typed neutral contracts, validated JDT tooling, isolated process startup/readiness, and this progress record.
- Approved inputs: `docs/plans/jdt-first-java-engine-implementation-plan.md` Task 1; Java engine configuration and JDT module contracts; Task 1 RED brief/report and the three direct RED tests.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped repository instructions, Task 1 plan, authoritative engine contracts, RED brief/report, and direct RED tests.
- Confirmed pre-existing unrelated changes are limited to other agents' progress/tests and will be preserved.
- Reproduced the intentional RED with the prescribed JDK 17 toolchain: 17 tests failed and 0 errored because the Task 1 production types are absent.
- Added the scoped Task 1 production seam and required dependencies. The first GREEN command reached Maven dependency convergence before compilation.
- The direct Task 1 suite is green after pinning the Gson transitive Error Prone annotations dependency (17 tests, 0 failures/errors).
- Scoped Spotless verification confirms all 15 Task 1 production Java files are formatted. `git diff --check` reports no whitespace errors.
- Reopened after the P1 review RED: 31 tests run, 14 assertion failures, 0 errors/skips. The original 17 tests remain green.
- Replaced arbitrary working-tree source authority with a verified-text-only project model: projections write admitted immutable bytes only, source roots are snapshot-relative, and the fingerprint is derived from verified documents and effective project inputs.
- Added typed neutral catalog/context records and invariants, including entry-method containment and explicit technical-enhancement availability.
- Moved JDT distribution/JDK validation into direct `JdtConfiguration` construction: canonical paths, one Equinox launcher, a platform config directory, and a bounded Java 21+ probe are required.
- Added package-private `JdtProcessIsolation`; real startup and test injection share it. JDT metadata/projection now use a project root distinct from the language-server `-data` directory, and startup performs bounded readiness plus a declaration probe.
- Made descriptor tool versions mandatory and populated them from the configured JDT distribution/JDK. Session cleanup now surfaces an owned-workspace cleanup failure.
- Final Task 1.2 production GREEN now derives a declaration-bearing UTF-16 readiness position from projected verified source and rejects an empty declaration response; initialization, timeout, and unready cases stay fail-closed.
- Removed absolute-root inference. Source roots stay caller-ordered snapshot-relative roots, while the project fingerprint preserves classpath order and binds every approved file's SHA-256; JDT startup rehashes every classpath file before process creation.
- Stored actual bounded `java -version` output and a launcher-content-derived JDT distribution identity in `JdtConfiguration`; descriptors use those identities rather than directory names. Failed-start `stop=false` is retained as suppressed cleanup evidence.
- Completed the Task 1.2 neutral records: complete method/call/target/catalog JSON fields, flattened source locations, duplicate-key prevention, caller/target/support/limitation reference closure, and strict AVAILABLE/NOT_PRODUCED enhancement invariants.

## Current state

- Final Task 1.2 production and fixture corrections are complete.
- The five-class offline selector passes 44 tests after the final Spotless rewrite; project/session configuration is ready for the Task 1.3 syntax-helper slice.
- A separate read-only gate review remains a coordinator action and does not reopen this implementation assignment.
- The coordinator subsequently closed the three bounded review remnants: document-symbol readiness, parameter/body/control/argument reference invariants, and launcher/JDT-LS-core/JDT-core distribution identity.

## Changed files

- `pom.xml`
- `src/main/java/org/sourceanalysis/app/analysis/code/CodeEngineException.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/EngineDescriptor.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/EntryCodeContext.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/EntrySeed.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/JavaCodeEngine.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/JavaCodeSession.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/JavaDeclarationCatalog.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/SourceRange.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/VerifiedJavaProject.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtCodeEngine.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtProcessIsolation.java`
- `src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSession.java`
- `src/main/java/org/sourceanalysis/app/runtime/EffectiveEngineConfiguration.java`
- `src/main/java/org/sourceanalysis/app/runtime/EngineConfigurationLoader.java`
- `src/main/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactory.java`
- `progress/jdt-engine-configuration-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | RED (expected) | 17 tests failed, 0 errored; missing `EngineConfigurationLoader` and `VerifiedJavaProject`. |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | Blocked before compile | Dependency convergence found Gson 2.14.0 requires Error Prone annotations 2.48.0 while the existing JavaParser path selects 2.47.0. |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | PASS | 17 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml spotless:check` | Needs scoped formatting | Nine files need formatting; three are pre-existing RED tests owned by another agent and will not be modified. |
| `mvn -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=<Task 1 production files>` | PASS | Formatted only six Task 1 production files; no tests or other agents' files changed. |
| `mvn -t .mvn/toolchains.xml spotless:check -DspotlessFiles=<Task 1 production files>` | PASS | All 15 Task 1 production Java files are clean. |
| `git diff --check` | PASS | No whitespace errors in tracked changes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` | PASS | 44 tests, 0 failures, 0 errors, 0 skipped after the two fixture corrections and final formatting. |
| `mvn -t .mvn/toolchains.xml spotless:check` | PASS | 533 Java files clean. |
| Final bounded closure selector | PASS | 49 tests, 0 failures, 0 errors, 0 skipped before the final formatting pass. |
| Final post-format five-class selector | PASS | 49 tests, 0 failures, 0 errors, 0 skipped; build success. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtEngineFinalContractTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped after the neutral record/invariant implementation. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` | Awaiting fixture migration | 30 tests, 2 failures, 0 errors; both failures are the approved absolute-root legacy fixture. EngineConfigurationLoader (13), JavaCodeEngineFactory (3), JdtEngineFinalContract (4), and the remaining JavaCodeEngineContract assertions are green. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply/check -DspotlessFiles=<five changed Task 1.2 production files>` | PASS | Changed Task 1.2 production sources are formatted. |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest test` | MIXED | Production compilation passed; `JavaCodeEngineContractTest` 10/10 and `EngineConfigurationLoaderTest` 13/13 passed. The legacy session fixture had 5 raw-Path/public-fake setup failures; the legacy factory fixture had one expected pre-start version-probe setup error. |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineContractTest test` | PASS | 23 tests, 0 failures, 0 errors, 0 skipped after production formatting. |
| `mvn -t .mvn/toolchains.xml spotless:apply -DspotlessFiles=<changed production Java regexes>` | PASS | Only changed production files were mechanically reformatted; no test source was edited. |
| `mvn -t .mvn/toolchains.xml spotless:check` | BLOCKED_BY_TEST_OWNERSHIP | All production files are clean; only the two pre-existing RED test files owned by the test assignment remain unformatted. |
| `git diff --check` | PASS | No whitespace errors in tracked changes. |

## Decisions

- Use Jackson YAML strict data binding with duplicate-key detection; validate only selected JDT paths.
- Keep the process client package-private and limited to startup/readiness/termination; syntax, navigation, discovery, persistence, Builder, and JavaParser integration remain out of scope.
- Manage Error Prone annotations at 2.48.0 so the mandatory Gson 2.14.0 dependency converges with the existing JavaParser/Guava tree.
- Preserve stage-one `javaparser` as explicit `ENGINE_NOT_INTEGRATED`; the selected JDT path contains no JavaParser runtime/reference edge.
- Do not restore raw-Path project construction or a public no-LS fake-success session route merely to satisfy legacy reflection fixtures; parent explicitly rejected that compatibility API.

## Blockers

- None.

## Exact next action

- Coordinator commits Task 1.2 and begins Task 1.3.

## Resume checks

- After the test owner migrates the two legacy fixtures, run `git status --short` and the four-class Task 1 selector with `.mvn/toolchains.xml`.
