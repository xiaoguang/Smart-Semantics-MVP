# Progress: Stage04 identity ledger core

- Status: COMPLETE
- Agent role: Stage04 Slice A production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement only the in-memory Stage04 identity/round-slot ledger required by the Slice A
  public test; no archive, filesystem persistence, provider, or HTTP surface.
- Approved inputs: Scoped AGENTS, TDD guidance, Stage04 runtime/archive design, corrected identity
  ledger test and its test progress.
- Current branch/worktree: Shared worktree; preserve all pre-existing and other-agent changes.

## Completed

- Created this owned progress file before production edits.
- Read the scoped repository instructions, current shared-worktree status, and the progress template.
- Read the full Stage04 runtime/archive/Trace/recovery design, the corrected public identity-ledger
  test, and its completed test-author progress.
- Reproduced the intended RED: Stage04 test compilation reaches 49 test sources and fails only
  because the eight required Stage04 request, identity, slot, event, exception, and ledger types
  are absent; no test method executes.
- Added the minimal in-memory Stage04 ledger with immutable request/lineage/identity/slot/event
  records and typed stable failures. Canonical request material excludes `snapshotRoot`, but binds
  schema, source registration, rootless request, and profile.
- Narrow GREEN: `Stage04IdentityLedgerTest` passes all 3 tests after the implementation compiles.
- Added fail-closed null handling at public record/identity boundaries without changing the slot
  state machine; the narrow selector remains GREEN at 3 tests.

## Current state

- Requested direct selector is green and the shared-worktree whitespace check has no diagnostics.
  Stage03 was not modified and needs no direct regression run.

## Changed files

- progress/stage04-identity-ledger-core.md
- src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | RED | Test compilation reaches 49 sources and fails only on missing Stage04 seam types; 0 tests executed. |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | provisional RED | Main compilation: 1 error, `CANDIDATE_CONTENT_ID` was private while the same-package identity record revalidated it. |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | GREEN | 3 tests, 0 failures/errors after the visibility correction. |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | GREEN | 3 tests, 0 failures/errors after null-boundary hardening. |
| `mvn -Dtest=Stage04IdentityLedgerTest test` | GREEN | Completion verification: 3 tests, 0 failures/errors. |
| `git diff --check` | GREEN | No whitespace diagnostics. |

## Decisions

- Keep the ledger memory-only and package-private validation behind the exact public request/view
  records required by the test.
- Treat local snapshot roots strictly as transport inputs: no root value enters canonical request
  material, IDs, exceptions, or events.
- Preserve the §7 transient begun-attempt representation as `RESERVED` with a positive attempt
  count; only a confirmed no-start receipt moves it to `PRESTART_RETRYABLE`.
- Keep this ledger instance single-series: once a canonical request is reserved, a distinct
  canonical request deterministically fails with `SERIES_IDENTITY_CONFLICT`; repeated canonical
  root-independent requests read the same immutable slot.
- No Stage03 test was run because this Slice only introduces the isolated Stage04 package and
  does not invoke, alter, or link against Stage03 production.

## Blockers

- None.

## Exact next action

- None; Stage04 Slice A identity/ledger implementation is complete.

## Resume checks

- Confirm production remains limited to `src/main/java/com/linguan/codemd/stage04/` and this file.
