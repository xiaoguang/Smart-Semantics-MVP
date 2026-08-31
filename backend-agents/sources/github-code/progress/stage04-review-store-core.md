# Progress: stage04-review-store-core

- Status: COMPLETE
- Agent role: Stage 04 Round-2 Slice 2 review-store production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement only `FilesystemCandidateReviewStore(Path, CodeToMarkdownAgent)` and its strict §4.1 durable finding read/write closure; no Round-2 improvement or overlay behavior.
- Approved inputs: scoped `AGENTS.md`; Stage 04 §4.1; `progress/stage04-review-store-tests.md`; `Stage04ReviewStoreTest`; existing Candidate archive and validation services.
- Current branch/worktree: shared dirty worktree; preserve all unrelated Stage 01–04 work.

## Completed

- Read the scoped guidance, §4.1 record/store contract, review-store RED/progress, and the archive/validation/public-agent seams.
- Confirmed the direct selector reaches a reflection-backed concrete-store seam and exercises a real Round-1 archive, not fabricated storage.
- Reproduced the expected 1-test RED: only the missing `FilesystemCandidateReviewStore` reflection seam failed.
- Added package-local `FilesystemCandidateReviewStore(Path, CodeToMarkdownAgent)`, using public validation, immutable archive reads, parent Round-1/receipt closure, Flow and reader/section membership checks, and no model/source execution.
- Added canonical UTF-8 finding persistence under the §4.1 review path with review-boundary symlink checks, forced same-directory staging files, atomic installation, exact-byte idempotence, and collision rejection.
- Implemented exact resolution with duplicate/unknown/cross-parent/unapproved rejection and canonical typed-record reconstruction before a sorted `ReviewFindingSet` is returned.

## Current state

- The bounded §4.1 durable review-store slice is complete. Review-store persistence is available; `improveCandidate` and the per-Flow Round-2 overlay remain intentionally out of scope.

## Changed files

- `progress/stage04-review-store-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/FilesystemCandidateReviewStore.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ReviewStoreTest test` | RED (expected) | 1 test / 1 assertion failure: concrete filesystem review store was absent. |
| `mvn -Dtest=Stage04ReviewStoreTest test` | PASS | 1 test / 0 failures / 0 errors; canonical/idempotent store and closed references pass. |
| `mvn -Dtest=Stage04ImprovementTest test` | PASS | 1 test / 0 failures / 0 errors after the final store implementation. |
| `mvn -Dtest=Stage04TargetCliAdapterTest,Stage04LoopbackHttpAdapterTest test` | PASS | 4 tests / 0 failures / 0 errors with approved local loopback socket access. |
| `git diff --check` | PASS | No whitespace errors in tracked changes. |

## Decisions

- The store will use the existing `CandidateArchive` reader and public `CodeToMarkdownAgent.validateCandidate` rather than reimplementing archive validation or source replay.
- Rejected drafts will complete all validation before any review-directory installation.
- macOS may expose the temporary workspace through the system `/var` alias. The implementation rejects a symlink at the configured workspace or any descendant review path, without rejecting that platform-owned ancestor alias.

## Blockers

- None.

## Exact next action

- A later bounded slice can consume `resolveExact` before reserving a Round-2 slot and compile the design-defined per-Flow improvement overlay.

## Resume checks

- Verify only this progress file and `FilesystemCandidateReviewStore.java` are owned by this slice.
