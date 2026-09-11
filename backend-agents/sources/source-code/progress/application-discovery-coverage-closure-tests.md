# Progress: application-discovery coverage closure RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test-only owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One public-executor behavior test proving `repositoryEntryCoverage.closed` is true for complete, fully accounted source input and false for bounded input.
- Approved inputs: Existing `ApplicationDiscoveryExecutor`, `ApplicationDiscoveryExecutionTest` synthetic verified-source helper, canonical artifact stores, and the published Step 02 v2 coverage contract.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the source-scoped instructions, both source-code implementation plans, TDD guidance, Step 02 contract, and the closeout contract ruling.
- Confirmed the test must consume real synthetic verified source bytes through `ApplicationDiscoveryExecutor`; no hand-built capability output or production/schema edit is allowed.
- Created this progress file before the Java edit and intent-to-add staged it with `git add -N` before the test change.
- Added one public-executor behavior test with complete and bounded cases, including observed entry/site/shard/count premises before the closure assertion.

## Current state

- Preparing one test in `ApplicationDiscoveryExecutionTest` that executes complete and bounded source scopes through the real M1–M4 producer, checks actual nonempty entry/site/shard accounting, reopens `capability-report.json`, and asserts the required boolean closure field.
- The corrected selector reaches both closure assertions and produces the expected RED because the current capability report omits `repositoryEntryCoverage.closed`.

## Changed files

- `progress/application-discovery-coverage-closure-tests.md` (owned; intent-to-add staged)
- `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutionTest.java` (owned; one additive behavior test/helper only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/application-discovery-coverage-closure-tests.md` | PASS; numeric exit 0 | Intent-to-add completed before Java edit; initial scoped `git diff --check` passed. |
| Initial exact selector (session 82076) | SETUP FAILURE; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; both cases used nonexistent store roots. Corrected test-only setup with `Files.createDirectory`. |
| Corrected exact selector (session 54902) | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; both complete and bounded cases failed only because `repositoryEntryCoverage.closed` was absent/non-boolean. |
| Absolute one-file Spotless apply (session 90485) | PASS; numeric exit 0 | Used absolute `-DspotlessFiles` for `ApplicationDiscoveryExecutionTest.java`; exactly one file changed. |
| Absolute one-file Spotless check (session 99193) | PASS; numeric exit 0 | Exactly one selected file clean. |
| Post-format exact selector (session 90073) | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; same missing/non-boolean `closed` failures for both cases. |
| Final scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in the owned progress/test scope. |

## Decisions

- Reuse the existing real `VerifiedSourceTextSet` and M1–M4 helper; vary only the declared scope and completion eligibility, preserving actual discovery output.
- Assert observed nonempty entry/site/shard accounting before asserting `repositoryEntryCoverage.closed`; do not infer closure from names or imagined counts.
- Keep the test additive and test-only; do not weaken existing expectations or modify production, schema, fixture, or design files.

## Blockers

- None for this bounded test slice. The missing producer field is the intended RED and remains for the next implementation owner.

## Exact next action

- Release the Maven lease and hand off the test-only RED to root/Terra; do not modify production or schema in this slice.

## Resume checks

- Maven was held serially for the setup-corrected exact selector, one-file Spotless apply/check, and post-format exact selector; it is now released.
- Keep Java edits to `ApplicationDiscoveryExecutionTest.java` only; preserve the old repository-closure BLOCKED progress as history.
- The RED proves the existing public producer must emit a typed boolean `repositoryEntryCoverage.closed`, true only for complete fully accounted input and false for bounded input.
