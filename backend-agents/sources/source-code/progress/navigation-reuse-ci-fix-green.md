# Progress: navigation-reuse-ci-fix-green

- Status: COMPLETE
- Scope: Close the bounded P1/P2 CI-classification findings with portable,
  explicitly configured, fail-closed real-navigation prerequisites. Preserve
  the concurrent JDT cache work and do not run real JDT or broad CI.

## Initial state

- The formal source-code checkout is already dirty with the concurrent Task 1
  CI split, JDT cache tests, and prior CI-review/RED artifacts; these are
  preserved.
- The direct `MavenLocalCiClassificationTest` RED defines the required four
  portable properties, host-path rejection, fail-closed preflight, and exact
  two-class Failsafe selector.

## Diagnosis

- The exact two-class Failsafe selector is already correct. The P1 is local to
  `JdtRealSourceCollectionIT`: it declares ignored workspace and developer
  machine paths, then converts each missing input into an assumption skip.
- Hypothesis: replacing those constants with four required system properties
  and one deterministic preflight that validates directories, the tool Java
  executable, and dependency files will make explicit-profile absence a clear
  failure while leaving the ordinary Surefire selector unable to start real
  JDT. The module CI documentation will name the same configured contract.

## Completed

- Replaced the navigation IT's ignored workspace and developer-home constants
  with `sourceanalysis.jdt.testJavaHome`, `sourceanalysis.jdt.testProject`,
  `sourceanalysis.jdt.testDistribution`, and
  `sourceanalysis.jdt.testDependencies`.
- Added a fail-closed preflight for every required directory, the JDT and tool
  Java executables, every configured dependency JAR, and the built syntax
  helper artifact. Any absent or malformed input throws the documented
  `Missing required real-jdt-it prerequisite` failure instead of skipping.
- Kept the real IT in Failsafe only. No POM selector change was required: the
  already-correct profile continues to contain exactly the two real-JDT IT
  classes, while the ordinary Surefire selector does not activate it.
- Updated the Java-engine integration document with the portable property
  contract and its fail-closed meaning.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=MavenLocalCiClassificationTest test` | GREEN | 7 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. The selector ran only `MavenLocalCiClassificationTest`, not real JDT/Failsafe. |
| Narrow `git diff --check` and untracked IT whitespace check | PASS | No whitespace diagnostics. |

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtRealSourceCollectionIT.java`
- `docs/modules/java-code-engines/integration-and-javaparser.md`
- `progress/navigation-reuse-ci-fix-green.md`

## Deferred

- No real-JDT execution, fixture/source provisioning, JDT distribution download,
  dependency download, POM/profile expansion, or broad CI run was performed.
