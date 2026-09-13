# Progress: navigation-reuse-ci-fix-tests

- Status: COMPLETE
- Agent role: Luna RED — CI review finding contracts
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Add RED-only contracts for the exact two-class Failsafe selector and portable, fail-closed real-navigation prerequisites. Do not modify production, POM, workflow, design docs, or run real JDT.
- Approved inputs: CI design §9; completed `progress/navigation-reuse-ci-review.md`; current `MavenLocalCiClassificationTest` and `JdtRealSourceCollectionIT`.
- Current branch/worktree: `codex/navigation-reuse-implementation` at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the TDD skill and good-test rules.
- Re-read the current worktree and confirmed the P1/P2 implementation gaps are still present.
- Strengthened the Failsafe selector assertion to require exactly `JdtRealSourceCollectionIT` and `JdtSyntaxHelperRealIT`, with no missing or extra includes.
- Added ordinary Surefire contracts requiring four portable real-navigation prerequisite properties, rejecting `/Users/` and `/Library/` paths, and requiring missing prerequisites to fail explicitly rather than use JUnit assumptions.
- Ran only the direct `MavenLocalCiClassificationTest` selector and witnessed the expected RED without starting real JDT.

## Current state

RED is established accurately: 7 tests ran; 3 failed; 0 errors; 0 skipped. The exact-selector test passed against the current POM. The three new portable/fail-closed tests failed for the intended implementation gaps:

- `realNavigationItUsesConfiguredPortablePrerequisites`: all four required keys are absent: `sourceanalysis.jdt.testJavaHome`, `sourceanalysis.jdt.testProject`, `sourceanalysis.jdt.testDistribution`, and `sourceanalysis.jdt.testDependencies`.
- `realNavigationItContainsNoDeveloperHostPaths`: the current IT still contains both `/Users/` and `/Library/` paths.
- `realNavigationItFailsClosedWhenPrerequisitesAreMissing`: the current IT still imports `org.junit.jupiter.api.Assumptions` and calls `Assumptions.assumeTrue`; the contract additionally requires an explicit `IllegalStateException` preflight carrying `Missing required real-jdt-it prerequisite`.

The other four contracts passed, including the exact two-class include assertion. No production, POM, workflow, design documentation, real IT, or real JDT was modified or run.

## Changed files

- `progress/navigation-reuse-ci-fix-tests.md` (this file)
- `src/test/java/org/sourceanalysis/app/MavenLocalCiClassificationTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` and focused source read | PASS | Confirmed pre-existing Task 1 changes and that this RED task has not modified POM/workflow/production. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=MavenLocalCiClassificationTest test` | RED (expected) | Final run: 7 tests, 3 failures, 0 errors, 0 skipped. Only `MavenLocalCiClassificationTest` appears in the Surefire XML; no real JDT or Failsafe test ran. |
| Surefire XML XPath check | PASS | Failure names are exactly the three portable/fail-closed contracts listed above; the exact-selector and three pre-existing classification contracts passed. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/MavenLocalCiClassificationTest.java` plus owned-file trailing-whitespace scan | PASS | No whitespace errors found. |

## Decisions

- Strengthen the existing profile test with the exact two literal includes.
- Add a separate contract for the real navigation IT's portable property inputs and fail-closed preflight, while keeping the contract itself a normal Surefire test that never launches JDT.
- Use `sourceanalysis.jdt.testJavaHome`, `sourceanalysis.jdt.testProject`, `sourceanalysis.jdt.testDistribution`, and `sourceanalysis.jdt.testDependencies` as the explicit portable property contract. The dependency-set value may be encoded by the implementation, but its paths must come through the one configured property rather than developer-home constants.
- Require a descriptive missing-prerequisite failure marker (`Missing required real-jdt-it prerequisite`) so merely deleting assumptions without adding an explicit preflight cannot turn the contract green.

## Blockers

- None for the RED task. GREEN implementation belongs to the implementation owner.

## Exact next action

Implementation owner should update only the real navigation IT and the workflow/POM inputs needed to satisfy the four portable properties and fail-closed preflight, then rerun this direct selector before any prepared real-JDT verification.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm only the contract test and this progress file are owned by this task.
- Expect the direct selector to remain RED with the exact three failure names until the portable/fail-closed implementation is complete.
