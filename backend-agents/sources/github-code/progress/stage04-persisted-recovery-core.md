# Progress: Stage04 persisted recovery core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Scope: Filesystem-backed CandidateSeriesLedger and provider-free Candidate recovery only; no API, HTTP, archive schema expansion, or Provider execution.
- Approved inputs: scoped `AGENTS.md`; Stage04 recovery contract; `Stage04PersistedRecoveryTest`; `progress/stage04-persisted-recovery-tests.md`; existing ledger, store, validation, and lifecycle seams.
- Current branch/worktree: shared and pre-existing dirty; unrelated work is preserved.

## Completed

- Read scoped guidance, worktree status, the recovery RED contract location, and the existing Stage04 production seams.
- Established the bounded implementation boundary: a filesystem event log is the sole recovery preimage; recovery validates/folds it and may append terminal events, but never calls a Provider or mutates Candidate bytes.
- Reproduced the focused intentional compile RED: 12 errors because `CandidateSeriesLedger(Path)` and `recover(RoundSlotRequest, CandidateReference, CandidateValidationService)` are absent; no tests ran.
- Added the Path-backed ledger, canonical series/slot/event records, no-follow/atomic event persistence, strict fresh-process folding, and provider-free recovery completion/failure paths.
- `mvn -Dtest=Stage04PersistedRecoveryTest test` is GREEN: 9 tests, 0 failures, 0 errors, 0 skipped.

## Current state

- Filesystem-backed recovery is complete: every persisted event is canonical, independently recomputed on reload, and recovery has no Provider collaborator.

## Changed files

- `progress/stage04-persisted-recovery-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared worktree is pre-existing dirty; unrelated changes are preserved. |
| `mvn -Dtest=Stage04PersistedRecoveryTest test` | RED | Test compilation: 12 missing-seam errors for `CandidateSeriesLedger(Path)` and `recover(...)`; no test executed. |
| `mvn -Dtest=Stage04PersistedRecoveryTest test` | RED | Production compiled and 9 tests ran: 8 errors caused by rejecting the platform `/var` alias as a workspace symlink. |
| `mvn -Dtest=Stage04PersistedRecoveryTest test` | PASS | 9 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest='Stage04*Test' test` | PASS | 48 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No whitespace diagnostics. |

## Decisions

- Persist canonical JSON events one at a time with create-new/force/atomic move and no-follow/symlink checks.
- Treat noncanonical, reordered, duplicate, forged, or state-transition-inconsistent event records as fatal drift; do not repair or tolerate them.
- `recover` will only validate a complete installed Candidate through `CandidateValidationService`, append the permitted recovery terminal event, or append a terminal failure event. It has no Provider parameter.
- A malformed event chain is never rewritten or admitted as a normal fold. It produces `ROUND_SLOT_CONFLICT` during recovery and an in-memory terminal quarantine view on subsequent reservation, preventing all further Provider transitions without fabricating an on-disk repair event.

## Blockers

- None.

## Exact next action

- Complete.
