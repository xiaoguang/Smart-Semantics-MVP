# Progress: M5 evidence graph RED test

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add only the smallest public-seam `EvidenceGraphBuilder` RED test and any necessary test-only fixture/resource under this source-code directory.
- Approved inputs: Fresh reopened M1–M4 canonical graph inputs plus verified source handles; no mocks for canonical/hash/coverage logic.
- Current branch/worktree: Shared worktree; unrelated existing M1–M4 edits are preserved.

## Completed

- Read scoped `AGENTS.md`, `docs/DESIGN.md`, both `docs/plans/*`, and the M5 contract in `docs/analysis-steps/03-program-graphs.md`.
- Confirmed the requested direct selector is `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test`.
- Added one assertion-level RED slice requiring two distinct closed source-plus-rule paths for one admitted element with exact source-byte rehash checks.
- Re-ran the selector after the M4 owner cleared the prior compilation blocker; JUnit reached the new test and failed for the expected one-path behavior.
- M5 production now preserves multiple valid provenance paths; the original first-test cardinality assertion must be widened from one edge per subject to one-or-more closed paths per subject.
- Updated the first test to assert subject-set equality and one-or-more closed source-plus-rule paths, while retaining exact source-file and excerpt rehash checks.
- Re-ran the exact selector successfully: 2 tests, 0 failures, 0 errors.

## Current state

- M5 public builder seam is present in the shared worktree. The two-provenance RED is now implemented in production; this follow-up updates the baseline assertion to permit multiple paths.
- The prior M4 test-compilation blocker has been cleared by its owner; the exact EvidenceGraph selector is ready to rerun.

## Changed files

- `progress/m5-evidence-graph-tests.md` (this file)
- `src/test/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Previous `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | RED (expected) | Earlier compiler RED for missing `EvidenceGraphBuilder`; M5 production seam is now present in shared worktree. |
| Previous `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | RED (expected) | 2 tests run, 1 failure, 0 errors. `preservesTwoClosedSourceAndRulePathsForOneAdmittedElement` failed at line 165: expected 2 support paths but got 1. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | PASS | 2 tests run, 0 failures, 0 errors; adjusted first test accepts multiple paths and verifies closed source/rule provenance. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Use real canonical module stores and fresh M1–M4 reopen paths from existing test fixtures/helpers; do not mock hash, canonicalization, coverage, or predecessor validation.
- Keep the first slice to one assertion-oriented RED test; no production edits, POM edits, existing-test edits, or design edits.

## Blockers

- M5 now preserves multiple valid provenance paths. The first test therefore requires the admitted subject set to match exactly while permitting one-or-more closed paths per subject; each path still reopens and rehashes source bytes and verifies subject-specific rule provenance.

## Exact next action

- Hand off the updated passing test to the parent; no production changes were made in this task.

## Resume checks

- Re-run `git status --short` in this directory; confirm only this progress file plus task-owned RED test/resource have changed before final reporting.
