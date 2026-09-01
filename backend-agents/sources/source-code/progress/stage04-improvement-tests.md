# Progress: stage04-improvement-tests

- Status: COMPLETE
- Agent role: Stage 04 Round-2 public-seam RED tests
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add only `Stage04ImprovementTest.java` to expose the smallest missing public Round-2 improvement contract after a real public-core Round-1 archive.
- Approved inputs: Stage04 §§3.2, 3.3, 4, 9.2, 13, 16; DESIGN.md Round-2 passages; scoped AGENTS.md; existing public core, improvement, archive, ledger, and fixture tests.
- Current branch/worktree: shared worktree; preserve unrelated changes and the earlier adapter RED files.

## Completed

- Read the scoped repository guidance and the required Stage 04/DESIGN.md Round-2 passages.
- Confirmed `CodeToMarkdownAgent` exposes no public durable seam to persist/read named archived review findings.
- Confirmed `DefaultCodeToMarkdownAgent.improveCandidate` is an explicit `NOT_IMPLEMENTED` RED seam and `Stage04PublicCoreTest` already covers the basic request shape.

## Current state

The smallest honest RED creates a real Round-1 Candidate through `DefaultCodeToMarkdownAgent`, validates it, records a canonical `CandidateReviewFindingDraft` through the public `CandidateReviewStore`, and resolves its exact sorted ID set before improvement. No internal Candidate fabrication or direct artifact injection is permitted. Round-2 then requires the bounded per-Flow overlay behavior and same-request idempotency.

## Changed files

- `progress/stage04-improvement-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04ImprovementTest.java` (new RED test only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ImprovementTest test` | RED (expected) | 1 test ran, 1 assertion failure, 0 errors. The real public-core Round-1 archive and fresh validation completed; the sole RED is the missing `CandidateReviewStore`/`CandidateReviewFinding`/`CandidateReviewFindingDraft`/`ReviewFindingSet` public seam check. |

## Decisions

- Add exactly one new test class and no production, existing-test, or design edits.
- Reuse the real Stage 01–03 fixture and scripted lifecycle Provider pattern from the public-core test, duplicating only setup needed because its helpers are private.
- Keep the RED focused on the missing public finding/archive capability; do not require improvement to accept unarchived IDs.
- Use the real Stage 01–03 fixture plus a recording scripted Provider; do not inject internal Candidates or finding records.
- Once the durable seam exists, Round-2 must repeat the same M5 R1/R2 scripted lifecycle (four total Provider calls), while same-request retry is idempotent.

## Blockers

- The review-store/overlay production seam is not yet present in Java; the selector intentionally REDs on the design-defined durable-boundary check. No unarchived IDs are accepted and no test-only Candidate/finding artifact is fabricated.

## Exact next action

Parent/Terra can implement the design-defined durable review store and per-Flow overlay against this RED, then rerun the same selector.

## Resume checks

- Verify only the new test and this progress file changed for this task.
- Re-run the exact selector if the first result is a compile/setup failure; the final RED must be a missing behavior, not a compile mistake.
