# Progress: navigation-reuse-ci-review

- Status: COMPLETE
- Agent role: Luna REVIEW — local CI classification
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Review the current uncommitted Task 1 CI-classification diff for specification compliance and code/build quality. Do not modify production, tests, POM, workflow, or other documentation; do not run heavy builds or real JDT.
- Approved inputs: `AGENTS.md`; `docs/plans/navigation-reuse-and-readable-report-design.md` §9; `progress/navigation-reuse-ci-tests.md`; `progress/navigation-reuse-ci-green.md`; current uncommitted Task 1 diff.
- Current branch/worktree: `codex/navigation-reuse-implementation` at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the source-scoped instructions, CI design §9, RED/GREEN handoffs, and the progress template.
- Fixed the review baseline at `HEAD` (`4bc15d28adca743712313f3c4ee8c5e8f5866c23`) plus all Task 1 untracked files.
- Reviewed the Task 1 diff along separate specification and repository/build-quality axes. The prescribed parallel review could not start because the active task tree had reached its agent limit, so both bounded axes were completed locally.
- Verified the current POM gives Surefire and Failsafe disjoint ownership: ordinary `*Test` classes stay with Surefire, while `real-jdt-it` explicitly includes only `JdtRealSourceCollectionIT` and `JdtSyntaxHelperRealIT`.
- Verified `skipUTs` and `skipITs` are independent default-false properties and map to Surefire `skipTests` and Failsafe `skipITs`, respectively.
- Verified the workflow has one root `verify` lifecycle after the independent helper build; it does not run a separate root `test` followed by `verify`.
- Verified the frozen-source test is an exact class-name-only move after normalization. The real helper method and its tool-JDK selector moved to the Failsafe-only class while all three fake-process tests remain in the Surefire class; no assertions were removed by the split.
- Classified one P1 and one P2. No P0 finding.

## Current state

Review result: **REQUIRES FIXES**.

- **P1 — The workflow can pass while the real JDT navigation IT is skipped.** `JdtRealSourceCollectionIT` still requires ignored `.workspace/jdtls-source-navigation-feasibility/{projection/src,tools/selected}`, a hard-coded macOS JDK 26 path, and three hard-coded `/Users/yexiaoguang/.m2/...` Spring jars, then expresses every missing prerequisite with `Assumptions.assumeTrue` (`JdtRealSourceCollectionIT.java:35-60`). A clean Ubuntu checkout contains none of the ignored `.workspace` inputs; the workflow provisions Temurin 21 and the helper jar but not the source projection, JDT distribution, or those macOS paths (`.github/workflows/source-analysis.yml:29-65`). Therefore the profile can report a green Maven `verify` with `JdtRealSourceCollectionIT` skipped, contrary to design §9's requirement to report the IT unverified rather than treat a skip as success. The workflow property `sourceanalysis.jdt.testJavaHome` is consumed only by `JdtSyntaxHelperRealIT`; the navigation IT ignores it and retains the host-specific JDK 26 constant. Fix by making the navigation IT consume portable/configured prerequisites, provisioning them in CI, and failing an explicit-profile preflight (or asserting Failsafe `skipped=0` and both intended test classes executed).
- **P2 — The POM contract test does not lock the exact two-class selector.** `MavenLocalCiClassificationTest.java:62-63` checks only that any include matches `.*Jdt.*IT.java`. It would pass if one required include disappeared, an unintended third IT were added, or a broad JDT wildcard replaced the exact list. Assert the exact two include values (and preferably no extras) so the test protects the contract it documents.

All other assigned checks passed by inspection: Surefire/Failsafe are currently mutually exclusive; skip properties are independent; the current profile contains exactly the two intended root IT classes; helper Java 21 versus host Java 17 is correctly separated for the helper build/root Maven process; the root workflow does not duplicate the test lifecycle; and the test split retains the prior assertions. No real JDT execution was attempted, and the absence of a Failsafe report remains `NOT VERIFIED`, not success.

## Changed files

- `progress/navigation-reuse-ci-review.md` (this file only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short`; `git diff --stat HEAD`; `git diff --name-status HEAD` | PASS | Captured the tracked Task 1 diff and identified untracked test/progress files that require separate review. |
| Normalized `diff` of `HEAD:JdtRealSourceCollectionTest.java` against `JdtRealSourceCollectionIT.java` | PASS | No output after replacing the old class name with the new one; the 292-line frozen-source test retained its content and assertions. |
| `xmllint --noout pom.xml tools/jdt-syntax-helper/pom.xml` | PASS | Both Maven descriptors are well-formed XML. |
| `git diff --check` | PASS | No whitespace errors in the tracked Task 1 diff. |
| Static source/POM/workflow inspection | REQUIRES FIX | Found the P1 clean-runner skip path and the P2 under-specified exact-selector contract above. No Maven lifecycle or real JDT was run. |
| Existing `target/surefire-reports` inspection | PASS, bounded | Current retained reports show `MavenLocalCiClassificationTest` 4/4 and `JdtSyntaxHelperClientTest` 3/3 with zero skipped; no Failsafe report exists, so real IT remains unverified. |

## Decisions

- Review the uncommitted working tree against `HEAD`, because the assigned fixed point is the current Task 1 diff rather than a committed branch range.
- Treat missing real-JDT prerequisites as `NOT VERIFIED`; do not infer success from skipped or absent IT reports.
- Do not accept the Task 1 delivery until the P1 skip-as-success path is closed. The P2 contract-test strengthening should be included in the same bounded fix because it directly protects the two-class profile invariant.

## Blockers

- None; the review is complete. Real JDT verification remains an implementation-gate requirement, not a blocker to issuing this review result.

## Exact next action

Implementation owner should fix the P1 portable prerequisite/preflight behavior and strengthen the P2 exact selector assertion, run the focused POM contract test, then run the explicit two-class Failsafe profile only in a prepared environment and inspect its XML for both classes with zero skips before requesting re-review.

## Resume checks

- Re-read this progress file and run `git status --short`.
- Confirm only this review progress file is modified by the review task.
- Re-check the workflow/test prerequisite contract and exact two-class includes; do not rely on `BUILD SUCCESS` without Failsafe XML evidence.
