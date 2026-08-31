# Progress: Stage 04 lifecycle and Round-2 hardening RED tests

- Status: COMPLETE
- Agent role: Stage 04 public recovery/Round-2 hardening test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most one new Stage04 test class for public prestart retry, provider-free installed-Candidate recovery, unaffected-Flow Round-2 reuse, and typed fatal-addendum rejection. No production, design, existing-test, or validation/trace/resource test changes.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; `docs/stages/04-runtime-archive-trace-recovery.md`; existing Stage04 lifecycle, public-core, review-store, persisted-recovery, archive, and Round-2 tests; real Stage03 reservation and independent-two-flow fixtures.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Read the scoped repository instructions, final implementation review, Stage04 runtime/archive/trace/recovery design, existing Stage04 tests, public lifecycle/recovery seams, review finding/addendum types, and Stage03 independent-two-flow fixture.
- Created this progress record before modifying tests.

## Current state

- Added one hardening test source with four tests and only scripted/recorded provider behavior. The double-flow setup invokes the existing Stage03 fixture builder reflectively so Stage01/Stage02 records remain real and compiler-produced.
- The double-flow test uses a test-local larger archive budget and in-memory finding store only to keep the public Round-2 seam from being masked by the fixture's model-round artifact size and the filesystem review store's single-artifact cap; it does not alter production defaults or fabricate Stage02 records.

## Changed files

- `progress/stage04-lifecycle-round2-hardening-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04LifecycleRound2HardeningTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | DONE | Shared worktree contains unrelated existing/parallel changes; none were modified. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest test` | RED | Root run: 4 tests, 4 failures, 0 errors/skips. Failures are the intended seams: fresh PRESTART_RETRYABLE retry returned `ROUND_SLOT_CONFLICT`; installed STARTED_CONSUMED recovery returned `ROUND_SLOT_CONFLICT`; two-Flow Round-2 made 8 calls instead of 6; arbitrary `addendum:anything` did not throw. |
| `git diff --check` | PASS | No whitespace errors reported. |

## Decisions

- Keep all new coverage in one class, below the requested two-class maximum and with four `@Test` methods.
- Exercise prestart retry through two fresh `DefaultCodeToMarkdownAgent` instances sharing the same durable workspace and request.
- Exercise crash recovery by removing only the persisted completion event after a real public Candidate install/validation, then invoke a fresh agent whose Provider fails if called.
- Exercise Round-2 unaffected-Flow reuse with the real independent-two-flow Stage01/02 fixture and a scripted Stage03 provider wrapper; compare Flow B `model-rounds.jsonl` lines byte-for-byte.
- Exercise fatal addendum validation with a real archived `FATAL` finding and a syntactically valid but unarchived `addendum:anything`, expecting rejection before any new Provider call.

## Blockers

- The four public seams remain intentionally RED until the corresponding Stage 04 implementation work lands.

## Exact next action

- No further action in this test-only task; the owned selector result and whitespace check are recorded above.

## Resume checks

- Re-read this file, run `git status --short`, verify only this progress file and the owned new test source changed, and rerun only the owned selector.
