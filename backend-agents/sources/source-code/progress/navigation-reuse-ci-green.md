# Progress: navigation-reuse-ci-green

- Status: COMPLETE
- Agent role: Terra GREEN — local CI classification
- Model: gpt-6-astra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Implement the minimal Maven/Failsafe and root workflow contract for isolated Surefire unit tests and explicit real-JDT integration tests. No JDT/business production changes, test assertion removals, customer builds, model work, or broad scans.
- Approved inputs: `docs/plans/navigation-reuse-and-readable-report-design.md` §9; `progress/navigation-reuse-ci-tests.md`; existing `MavenLocalCiClassificationTest` RED contract.
- Current branch/worktree: formal source-code checkout at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code` on `codex/navigation-reuse-implementation`.

## Completed

- Read repository and source-scoped instructions, CI design §9, RED handoff, progress template, current POM/workflow, and the unit/real-JDT test split.
- Confirmed pre-existing formal-checkout changes are limited to the Luna RED test split and another agent's `progress/navigation-reuse-implementation.md`; they remain outside this task's ownership.
- Added `skipITs=false`, moved Failsafe to the independent property, and bound only the two real-JDT `*IT` classes to the explicit `real-jdt-it` profile.
- Replaced the workflow's duplicated test/quality lifecycle with a single `quality,real-jdt-it` root verify after an independent Java 21 helper build; the host Maven toolchain remains Java 17.
- Confirmed the POM classification RED is GREEN after the profile-local Failsafe configuration repeats `${skipITs}`.
- Updated the Java-code-engine module's current CI contract so its implementation status no longer says the POM/test split is pending.
- Ran the required direct Surefire selectors: `MavenLocalCiClassificationTest` (4/4) and `JdtSyntaxHelperClientTest` (3/3).
- Completed `git diff --check`; no tracked-diff whitespace errors were reported. The owned new progress file also has no trailing whitespace.

## Current state

The POM now has independent default-false unit/integration switches, with Surefire still mapped to `${skipUTs}` and Failsafe mapped to `${skipITs}`. `real-jdt-it` is the only profile that binds Failsafe and explicitly includes the frozen-source and syntax-helper IT classes. The workflow performs formatting, an independent Java 21 helper package, then one Java 17-hosted `-Pquality,real-jdt-it verify` with the helper's tool JVM property. The first contract run identified one remaining structural requirement: the profile-local Failsafe configuration must repeat `${skipITs}` in addition to the shared plugin configuration; that mapping is now present and GREEN. The local JDK 26 and Spring jars are installed, but the frozen JDT projection, selected JDT distribution, and helper jar are absent, so no real-JDT selector is authorized to run.

## Changed files

- `pom.xml`
- `../../../.github/workflows/source-analysis.yml`
- `docs/modules/java-code-engines/integration-and-javaparser.md`
- `progress/navigation-reuse-ci-green.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=MavenLocalCiClassificationTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped. The first post-change run exposed the profile-local mapping requirement; after adding it, the fresh rerun passed all POM/test-class contract assertions. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtSyntaxHelperClientTest test` | PASS | 3 tests, 0 failures, 0 errors, 0 skipped. The fake-helper protocol/lifecycle unit coverage remains in Surefire. |
| Local real-JDT prerequisite check | NOT RUN | JDK 26 and required Spring jars are present; `.workspace/jdtls-source-navigation-feasibility/{projection/src,tools/selected}` and `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar` are absent. The `JdtRealSourceCollectionIT` selector was not run, so its required installed-tool/frozen-source behavior is unverified rather than treated as skipped success. |
| `git diff --check` | PASS | Exit 0 with no tracked-diff whitespace errors after the configuration, workflow, and durable-documentation updates. |

## Decisions

- Keep Failsafe declared in the regular build only for shared configuration; bind its `integration-test` and `verify` goals exclusively through explicit `real-jdt-it`.
- Include only `JdtRealSourceCollectionIT` and `JdtSyntaxHelperRealIT` in that profile, so default Surefire stays free of installed-tool/frozen-source work.
- Replace the workflow's duplicated test/quality sequence with one root `verify` that enables `quality,real-jdt-it`; use both `-DskipUTs` and `-DskipITs` only for a quality-only invocation.
- The failing profile-local assertion is deliberate structure, not a Maven merge failure: repeat the same `${skipITs}` mapping in the profile-local Failsafe configuration so the profile is self-describing and satisfies the RED contract.

## Blockers

- Installed prerequisites required for the narrow real-JDT selector are incomplete: frozen source projection, selected JDT distribution, and helper jar are absent. Building/refreshing them or scanning the source is out of this task's scope; the real IT remains unverified.

## Exact next action

No further work in this task. Hand off the exact targeted-test counts and the real-IT prerequisite gap; do not wait for remote CI or run broader Maven/JDT work.

## Resume checks

- Run `git status --short` in the formal checkout.
- Re-read this progress file and `progress/navigation-reuse-ci-tests.md`.
- Verify POM properties/profile/selector and root workflow commands before testing.
