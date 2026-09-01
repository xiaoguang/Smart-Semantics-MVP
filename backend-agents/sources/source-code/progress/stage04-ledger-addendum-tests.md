# Progress: Stage04 ledger and addendum hardening tests

- Status: COMPLETE
- Agent role: Stage04 final P1 test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded test class (at most five tests) for durable lifecycle evidence, coherent receipt rewrites, conservative recovery, and fatal corrective-addendum fail-closed behavior while preserving warning Round-2 behavior. No production or design changes.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; Stage04 runtime/archive/recovery design; existing receipt, persisted-recovery, Round-2, review-store, and real fixture tests.
- Current branch/worktree: shared dirty worktree; preserve unrelated parent and parallel changes.

## Completed

- Read scoped guidance, Stage04 design, final independent review's remaining P1s, and existing receipt/addendum/recovery tests.
- Confirmed the shared candidate fixture has non-empty real model rounds and generation receipts.
- Created this progress file before modifying test state.
- Added `Stage04LedgerAddendumHardeningTest` with exactly five `@Test` methods.
- Focused selector reached test execution after one compile-only correction (the direct validator seam is owned by `CodeToMarkdownAgent`, not `FilesystemSourceRegistry`).

## Current state

- The new selector is intentionally RED against the current implementation: missing ledger, taskSpec-only rewrite, missing-start recovery, and self-signed addendum assertions fail; the preflight/attempt/policy rewrite assertion already passes against the existing formula/link checks.
- Corrected the legacy persisted-recovery positive fixture to persist every real `THREAD_STARTED` event before its baseline validation, leaving only completion absent. Without this ordering, the new missing-ledger contract makes the fixture invalid for the wrong reason.

## Changed files

- `progress/stage04-ledger-addendum-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04LedgerAddendumHardeningTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04PersistedRecoveryTest.java` (minimal fixture ordering correction)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04LedgerAddendumHardeningTest test` | RED (expected baseline) | Test compilation passed; 5 tests ran, 4 failures and 0 errors. Missing workspace/series ledger, taskSpec-only rewrite, missing real `THREAD_STARTED` recovery, and self-signed Filesystem addendum assertions failed; preflight/attempt/policy rewrite was already rejected by existing formula/link checks. |
| `mvn -Dtest=Stage04PersistedRecoveryTest test` | GREEN baseline | 9 tests, 0 failures/errors/skips after the positive fixture was reordered to persist all real starts before validation and leave only completion absent. |
| `git diff --check -- <three owned paths>` | GREEN | No whitespace errors after the fixture correction and progress closeout. |

## Decisions

- Keep exactly one new class with five focused `@Test` methods: missing ledger, task-spec receipt rewrite, preflight/attempt/policy rewrite, missing real started-event recovery, and fatal addendum fail-closed plus warning Round-2 unaffected behavior.
- Mutate only archived receipt/addendum bytes and recompute their local roots/manifest in tests, so failures exercise public validation rather than incidental hash mismatch.
- Use only scripted providers and the existing Stage04 fixture; no live model, network, source capture, or production implementation changes.

## Blockers

## Exact next action

- Parent production slice (Terra) should make the new ledger/addendum selector GREEN, then rerun only `Stage04LedgerAddendumHardeningTest` and the directly corrected `Stage04PersistedRecoveryTest` selector.

## Resume checks

- COMPLETE. This slice changed only this progress file, `Stage04LedgerAddendumHardeningTest.java`, and the explicitly allowed `Stage04PersistedRecoveryTest` fixture ordering; no production or design file was changed.
