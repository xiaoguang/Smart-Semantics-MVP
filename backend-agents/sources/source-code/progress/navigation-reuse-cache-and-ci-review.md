# Progress: navigation-reuse-cache-and-ci-review

- Status: COMPLETE
- Agent role: Luna REVIEW — CI prerequisite fix and JDT session cache
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Re-review the Task 1 P1/P2 fixes and review the Task 2 JDT query-cache diff against approved design sections 3–5. Read-only except this progress file; no heavy Maven or real JDT.
- Approved inputs: `AGENTS.md`; `docs/plans/navigation-reuse-and-readable-report-design.md` §§3–5 and §9; Task 1 RED/GREEN progress; Task 2 current diff and implementation progress.
- Current branch/worktree: `codex/navigation-reuse-implementation` at `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the current worktree status, approved cache design §§3–5, Task 1 RED handoff, and implementation coordinator status.
- Re-read the Task 1 fix GREEN, final CI test/IT/workflow/POM sources, cache implementation, and focused cache tests.
- Verified the retained Surefire XML for `MavenLocalCiClassificationTest` is GREEN: 7 tests, 0 failures, 0 errors, 0 skipped.
- Completed the bounded specification/build-quality review. No P0; two P1 findings and one P2 finding.

## Current state

Review result: **REQUIRES_FIXES**.

- **P1 — The checked-in workflow activates a fail-closed real-JDT profile without supplying three required inputs.** `JdtRealSourceCollectionIT` now correctly requires all four portable properties and throws for missing inputs (`JdtRealSourceCollectionIT.java:35-43,176-239`), so the RED contract is GREEN and explicit absence no longer skips. However, `.github/workflows/source-analysis.yml:60-65` still passes only `sourceanalysis.jdt.testJavaHome`; it neither provisions nor passes `testProject`, `testDistribution`, or `testDependencies`. A clean runner therefore fails immediately on every workflow run rather than executing the promised unit→real-IT→quality delivery. Wire/provision all four inputs when enabling `real-jdt-it`, or conditionally run a clearly reported non-real-JDT path without claiming real IT completion.
- **P1 — Response-shape QUERY_FAILED is journaled and cached as SUCCESS.** `definitions`/`implementations` cache and journal the raw `JsonElement` inside `cachedNavigationQuery`, then validate/normalize it afterward in `rawLocations` (`JdtLanguageServerClient.java:217-240,467-493,582-612`). An incomplete/malformed/unsupported location response therefore records `outcome=SUCCESS` and a successful cache value before `rawLocations` throws `JDT_QUERY_FAILED`. The next call is a key-only `CACHE_HIT` and reparses the bad response; no physical RPC repeats, but the cache/journal does not distinguish this QUERY_FAILED from a located/empty result as the design requires. Validate/normalize inside the cached operation, or cache a typed outcome so semantic failures are stored and journaled as failures. Add a focused malformed/incomplete-result repeat test, not only the current JSON-RPC error test.
- **P2 — The raw journal and statistics are unavailable after the owning session ends.** The journal is written under the session's temporary `language-server-data` directory (`JdtLanguageServerClient.java:495-502`), while `JdtProjectSession.close` deletes the entire workspace (`JdtProjectSession.java:64-67,144-169`). `clearNavigationState` also zeroes counters, and `queryStatistics()` has no production consumer (`JdtLanguageServerClient.java:243-246,504-509`). The test reads both before close, so it does not protect the design's saved troubleshooting/comparison evidence or post-run measurement. Preserve/copy the private journal and final statistics at the existing private run boundary before cleanup, or explicitly expose a final immutable snapshot to that owner; keep them out of public metadata as designed.

Passing checks: operation names separate definition/implementation/prepare/outgoing keys; position keys distinguish URI/line/character; outgoing keys use each complete hierarchy item and every prepared item is traversed; legal empty values and JSON-RPC failures avoid repeat RPCs; cache state is initialized/cleared per JDT session; physical exchanges carry request/response while hits carry only key/operation; the diff does not cache `EntryCodeContext`, and entry-specific collection remains in `EntryCodeCollector.collect`.

## Changed files

- `progress/navigation-reuse-cache-and-ci-review.md` (this file only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short`; `git diff --stat HEAD`; `git diff --name-status HEAD` | PASS | Captured the tracked diff and identified untracked Task 1 test/progress files. |
| Existing `target/surefire-reports/TEST-org.sourceanalysis.app.MavenLocalCiClassificationTest.xml` XPath inspection | PASS | 7 tests, 0 failures, 0 errors, 0 skipped; all four original and three P1/P2 fix contracts are present. No test was run by this review. |
| `xmllint --noout pom.xml tools/jdt-syntax-helper/pom.xml` | PASS | Maven descriptors are well-formed. |
| Focused source/usage searches | REQUIRES FIX | Confirmed workflow has only one of four required real-JDT properties; confirmed cache normalization occurs after success caching and journal/statistics have no consumer before workspace deletion. |
| `git diff --check` | PASS | No tracked-diff whitespace errors. |

## Decisions

- Treat CI prerequisite absence as a failure only when the explicit real-JDT profile is selected; ordinary Surefire classification must remain tool-free.
- Review cache identity and observable accounting separately from normalized target merging and entry projection.
- Do not accept Task 1 merely because its source-text RED is GREEN: the workflow invoking the explicit profile must also supply the new contract.
- Treat semantic response validation as part of the cached query outcome, not as post-cache presentation logic.

## Blockers

- None; the review is complete. Real JDT was intentionally not run.

## Exact next action

Implementation owners should close the two P1 findings, add focused RED coverage for workflow property completeness and malformed/incomplete navigation response replay, then address or explicitly resolve the P2 private-run observability contract before requesting re-review.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm this review changes no file except this progress document.
- Re-check workflow property provisioning, cached semantic-failure outcome/journal records, and evidence/statistics availability after session cleanup.
