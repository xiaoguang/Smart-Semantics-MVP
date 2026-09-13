# Progress: navigation-reuse-ci-tests

- Status: COMPLETE
- Agent role: Luna RED — local CI test classification
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Establish targeted RED coverage for Surefire unit tests versus explicit Failsafe real-JDT integration tests; keep skipUTs and skipITs independent. No POM/workflow/production changes.
- Approved inputs: `docs/plans/navigation-reuse-and-readable-report-design.md` §9; frozen source-code tests and existing JDT helper configuration.
- Current branch/worktree: formal source-code checkout at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

## Current state

Read the source-scoped instructions, plan §9, and progress template. Confirmed the current POM has no `real-jdt-it` profile, binds Failsafe `skipITs` to `${skipUTs}`, and has no root Failsafe execution. Renamed the real frozen-source test to `JdtRealSourceCollectionIT`, split the real helper invocation into `JdtSyntaxHelperRealIT`, and kept fake helper process tests in `JdtSyntaxHelperClientTest`. Added a focused POM contract test that fails on those missing build contracts. The task-local RED is complete; POM/profile implementation remains with the parent/Terra task.

## Changed files

- `progress/navigation-reuse-ci-tests.md` (this file)
- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtRealSourceCollectionIT.java`
- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxHelperRealIT.java`
- `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxHelperClientTest.java` (fake-helper tests retained as unit)
- `src/test/java/org/sourceanalysis/app/MavenLocalCiClassificationTest.java` (RED contract)
- Removed `src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtRealSourceCollectionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtSyntaxHelperClientTest test` | PASS | Main/test compilation succeeded; Surefire ran only `JdtSyntaxHelperClientTest`: 3 tests, 0 failures, 0 errors, 0 skipped. This confirms fake helper process tests remain unit coverage. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=MavenLocalCiClassificationTest test` | RED (expected) | Final rerun after secure-POM parser/format cleanup: main/test compilation succeeded; Surefire ran 4 tests, 2 failures, 0 errors, 0 skipped. Failures are `skipITs` absent (`expected false, was null`) and missing `real-jdt-it` profile; Surefire mapping and dedicated `*IT` class checks passed. |
| `mvn -o -t .mvn/toolchains.xml -Preal-jdt-it -DskipUTs -Dit.test=JdtRealSourceCollectionIT verify` | RED evidence (configuration not active) | Maven emitted `The requested profile "real-jdt-it" could not be activated because it does not exist`; Surefire reported `Tests are skipped`, no Failsafe report was created, and the command exited 0 without running the requested IT. No compilation or JDT failure occurred. |
| `git diff --check` and owned-file whitespace/path checks | PASS | No tracked diff whitespace errors; only the approved test split, new RED test, and this progress file are owned by this task. No `pom.xml`, workflow, or `src/main` path changed. |


## Decisions

- Keep `JdtRealSourceCollectionIT` and `JdtSyntaxHelperRealIT` outside Surefire's default `*Test` naming. The real helper test must not remain mixed with fake process tests; the latter stay in `JdtSyntaxHelperClientTest`.
- The POM contract test is intentionally RED until Terra adds the explicit `real-jdt-it` profile, Failsafe goals/selector, and independent `${skipITs}` property. It only reads the local POM and test-source names; it does not invoke a customer build, model, network, or real JDT.

## Blockers

- None for the RED task. The observed profile/property gaps are intentional expected RED and require the parent/Terra POM implementation before the target Failsafe command can execute ITs.

## Exact next action

Parent/Terra should consume this RED and implement the POM/profile split. After that change, rerun only `MavenLocalCiClassificationTest`, then the explicit `real-jdt-it` Failsafe selector when the configured helper/frozen tools are available. Do not run real JDT, customer source, model, full suite, or quality scans in this Luna task.

## Resume checks

- Run `git status --short` in the formal checkout.
- Re-read this progress file and confirm no POM/workflow/production files are changed.
