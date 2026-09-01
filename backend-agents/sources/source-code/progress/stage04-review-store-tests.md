# Progress: Stage 04 review-store public-seam RED test

- Status: COMPLETE
- Agent role: Stage 04 public-seam TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded public `CandidateReviewStore` selector covering a real Round-1 Candidate, canonical approved finding persistence/idempotence, exact resolution, and closed-reference rejection. Do not add production, design, or existing-test changes.
- Approved inputs: scoped `AGENTS.md`; Stage 04 design §4.1; public review-finding records; existing archive/public-core fixtures.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Read repository and source-scoped instructions, TDD guidance, Stage 04 §4.1, the four public review-finding types, and the existing public-core/archive fixtures.
- Created this progress record before modifying tests.

## Current state

- Scoped contracts and the real `Stage04ImprovementTest` setup are verified. Added the one compile-safe `Stage04ReviewStoreTest` against the public `CandidateReviewStore` records and the target `FilesystemCandidateReviewStore(Path, CodeToMarkdownAgent)` constructor seam. The test uses a scripted provider and derives all Candidate references from the generated fixture. The expected RED is isolated to the absent concrete filesystem review store.

## Changed files

- `progress/stage04-review-store-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04ReviewStoreTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ReviewStoreTest test` | RED (expected) | Test and testCompile succeeded; 1 test ran with 1 assertion failure: `FILESYSTEM_REVIEW_STORE_NOT_IMPLEMENTED`; 0 errors. |
| `git diff --check` | PASS | No whitespace errors in the owned test/progress changes. |

## Decisions

- Use reflection for `FilesystemCandidateReviewStore` so the shared Maven module remains test-compilable while the concrete durable implementation is absent.
- Do not fabricate Candidate/archive internals or permit arbitrary finding IDs; malformed, cross-parent, cross-flow, and cross-receipt references must be rejected by the durable seam.

## Blockers

- The concrete `FilesystemCandidateReviewStore` implementation is intentionally absent; this is the expected RED seam for the next production slice.

## Exact next action

- None for this test-authoring slice. A production slice should implement the filesystem review store and rerun the same selector.

## Resume checks

- Re-read this file, run `git status --short`, and ensure only this progress file and the owned test source changed.
