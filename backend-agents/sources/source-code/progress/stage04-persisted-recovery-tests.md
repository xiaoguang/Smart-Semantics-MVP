# Progress: Stage04 persisted series/recovery RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Minimal public-seam RED tests for filesystem-persisted Candidate series/round slots, append-only event files, fresh-process folding, and conservative Candidate recovery.
- Approved inputs: scoped `AGENTS.md`; `docs/stages/04-runtime-archive-trace-recovery.md` §§7, 9, 10, 11, 16; current `CandidateSeriesLedger`, `FilesystemCandidateStore`, `CandidateValidationService`, and direct Stage04 tests.
- Current branch/worktree: shared worktree; preserve unrelated parent changes.

## Completed

- Read the complete scoped guidance and the requested Stage04 design sections.
- Confirmed the current ledger is in-memory and the existing Candidate store/validator seams are the only available archive inputs.
- Created this progress file before changing test state.
- Added the bounded persisted recovery test class and ran its focused selector.

## Current state

- Added one bounded test class against the proposed persistent constructor and recovery operation on `CandidateSeriesLedger`.
- The test contains one representative valid archive recovery and one parameterized failure slice, without a broad transition matrix.

## Changed files

- `progress/stage04-persisted-recovery-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04PersistedRecoveryTest.java`

## Decisions

- Extend the existing `CandidateSeriesLedger` public seam with a `Path`-backed constructor and `recover(...)` operation rather than inventing a second ledger abstraction.
- Reuse the real `Stage04CandidateFixture` archive and fresh `CandidateValidationService`; recovery receives no Provider, making any Provider retry observable through the persisted event history rather than a mocked private collaborator.
- Assert durable event bytes through the documented `series/<digest>/reader-round-*/events/<ordinal>-<eventId>.json` layout: one canonical JSON object per file, exact ordinal/eventId fields, regular files only, and fail-closed tamper/ordinal cases.
- Keep archive failure cases and event-integrity cases in one small parameterized selector; no horizontal state matrix.

## Blockers

- The persistent constructor, recovery seam, recovered-completion event, and durable event reader are not present in the current production code; this is the intentional RED seam handed to the parent implementation.

## Exact next action

- Focused selector run completed; exact RED output is recorded above.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04PersistedRecoveryTest test` | RED (intentional compile gate) | Main compilation succeeded for 181 sources; test compilation reached the new class and failed with 12 errors: `CandidateSeriesLedger(Path)` is absent at lines 49, 81, 85, 92, 109, 116, 133, 154, 179, 183, 201; `recover(RoundSlotRequest,CandidateReference,CandidateValidationService)` is absent at line 118. No tests ran. |

## Resume checks

- COMPLETE. Only this progress file and `Stage04PersistedRecoveryTest.java` were added by this slice; no production or design file was changed.
