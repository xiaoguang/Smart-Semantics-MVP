# Progress: Stage04 fifth-audit lifecycle and bounds tests

- Status: IN_PROGRESS
- Agent role: Stage04 fifth-audit lifecycle/bounds test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add `Stage04FifthAuditLifecycleBoundsTest.java` with exactly two tests: one Round-1 P1(4) lifecycle failure/re-entry test and one grouped P1(3) finite source/directory bounds test. Round-2 remains deferred by the latest parent task. No production/design/existing-test edits.
- Approved inputs: scoped `AGENTS.md`; `progress/stage04-final-acceptance-review.md`; current Stage04 public agent, ledger, source registry, archive and test seams.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent/agent changes and existing audit test files.

## Completed

- Created this progress file before editing the new test class.
- Added exactly one `@Test` in `Stage04FifthAuditLifecycleBoundsTest.java`.
- The test uses the real Stage 04 fixture, a scripted adapter that persists every real `THREAD_STARTED` event, and a reflective `CandidateStoreLimits(1_000_000, 1)` install failure after both model rounds start.
- The test asserts the durable slot is `TERMINAL_FAILED` with `FAILED_AFTER_STARTED`, then asserts fresh re-entry replays `PROVIDER_FAILURE_AFTER_START` without Provider calls.
- Corrected the test-only durable ledger reopening helper to supply the fixture's known absolute snapshot root because rootless `series.json` intentionally omits `snapshotRoot`.
- Added a second grouped bounds test with one sparse source at limit+1 and five finite 257-entry seams; directory checks use a reflection-based shared package-private `CandidateValidationSupport.readBoundedDirectory` contract so a missing implementation becomes an assertion failure rather than a test error.
- Corrected the reflection invocation to pass an explicitly boxed `Integer` or `Long` in separate branches; Java's conditional numeric promotion had previously passed `Long` to the `int` seam and produced suppressed argument-type errors.
- Re-ran the focused selector after the correction: `Stage04FifthAuditLifecycleBoundsTest` is `2/2 GREEN`, with 0 failures/errors.
- `git diff --check` passed for the owned paths.
- The parent released the Maven window; the focused selector was run and produced clean assertion RED.

## Current state

- The Round-1 lifecycle test is verified as clean assertion RED against the current stale-`reserved` outer-catch behavior. The grouped bounds selector is ready for rerun after the reflection type correction.

## Changed files

- `progress/stage04-fifth-audit-lifecycle-bounds-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditLifecycleBoundsTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check -- progress/stage04-fifth-audit-lifecycle-bounds-tests.md src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditLifecycleBoundsTest.java` | PASS | No whitespace errors. |
| `rg -n "@Test" src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditLifecycleBoundsTest.java` | PASS | Exactly two test annotations. |
| `mvn -q -Dtest=Stage04FifthAuditLifecycleBoundsTest test` | PASS | 2 tests, 0 failures, 0 errors after the reflection type correction. |

## Decisions

- Use the public `DefaultCodeToMarkdownAgent` Round-1 path and the existing reflective `DefaultCodeToMarkdownAgent.limits` test seam.
- Drive both real recorded model rounds to `THREAD_STARTED` before the low sidecar limit forces Candidate installation failure.
- Inspect durable ledger state by reopening the persisted `series.json` request and folding the real event directory; no lifecycle event is synthesized in the test.

## Blockers

- None for this narrowed test. The selector intentionally remains RED until production lifecycle closure is corrected by the implementation owner.

## Exact next action

Notify the parent with the exact clean RED result; do not modify production or expand this narrowed deliverable.

## Resume checks

- Confirm the class has exactly two tests and no production/design/existing-test path changed.
- Run only `Stage04FifthAuditLifecycleBoundsTest` after the parent releases the Maven window.
