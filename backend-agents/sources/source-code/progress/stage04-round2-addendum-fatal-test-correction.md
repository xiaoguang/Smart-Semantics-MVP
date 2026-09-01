# Progress: Stage04 Round-2 addendum fatal-test contract correction

- Status: COMPLETE
- Agent role: Stage04 Luna test-only closeout
- Model: gpt-5.6-luna / delegated sub-agent
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Correct only the obsolete fatal test in `Stage04Round2AddendumTest` so bounded-v0's `FilesystemCorrectiveAddendumStore.record` is asserted to reject the self-signed legacy intake before any Round-2 Provider call. Preserve the warning/readability Round-2 positive tests. No production/design changes.
- Approved inputs: scoped `AGENTS.md`, `progress/final-implementation-review.md`, current Stage04 addendum implementation and test contract.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent and agent changes.

## Completed

- Dedicated progress file created before the test correction.

## Current state

- The fatal test now asserts the disabled filesystem store rejects the legacy intake at `record()` with `IMPROVEMENT_PARENT_INVALID`, before any Round-2 Provider work. The warning/readability/lifecycle tests are unchanged.

## Changed files

- `progress/stage04-round2-addendum-fatal-test-correction.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04Round2AddendumTest.java` (one fatal-test contract correction)

## Verification

| Command | Result | Key output |
| --- | --- | --- |

| `mvn -q -Dtest=Stage04Round2AddendumTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; fatal `record()` rejects with `IMPROVEMENT_PARENT_INVALID` before provider work. |
| `git diff --check` | PASS | No whitespace diagnostics. |

## Decisions

- Accept `IMPROVEMENT_PARENT_INVALID` or `CAPABILITY_NOT_ENABLED` at the `record()` seam, matching the bounded-v0 fail-closed contract while requiring provider-call count to remain unchanged.

## Blockers

None.

## Exact next action

Correction is complete; report the exact selector result and changed paths to the parent agent.

## Resume checks

- Confirm no production file or unrelated Stage04 test was modified.
