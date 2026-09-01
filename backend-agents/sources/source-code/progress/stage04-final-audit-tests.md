# Progress: Stage04 final audit tests

- Status: COMPLETE
- Agent role: Stage04 final audit test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most two new Stage04 test classes (at most seven tests) for the final review's receipt-only coherent rewrite, complete fallback/Gap Trace, and remaining bounded-read contracts. No production, design, Round-2/addendum, or existing-test changes.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; `docs/stages/04-runtime-archive-trace-recovery.md`; current Stage04 public/archive/Trace/loopback seams and frozen fixtures.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent and agent changes.

## Completed

## Current state

- The owned progress record was created before adding test files.
- Delivered test classes: one receipt/Trace class with two tests (A and positive fallback/empty-section/Gap B), and one bounded-read class with four tests (Candidate collision, validation-receipt collision, aggregate Candidate budget, and three loopback/target-CLI config paths). Six `@Test` methods total, below the seven-test cap.

## Changed files

- `progress/stage04-final-audit-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04FinalAuditTraceTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04FinalAuditBoundsTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04FinalAuditTraceTest,Stage04FinalAuditBoundsTest test` | RED (expected review-gap baseline) | Test compilation passed; 6 tests run, 6 failures, 0 errors, 0 skipped. A validation returned `valid=true` after a formula-consistent receipt-only rewrite (ledger event resolution absent); fallback Trace lacked the frozen template hop; oversized existing Candidate and validation receipt returned `CANDIDATE_IDENTITY_COLLISION` instead of `CANDIDATE_SIZE_LIMIT_EXCEEDED`; aggregate Candidate budget returned `valid=true`; sparse loopback config returned `M8_REQUEST_INVALID` instead of `CANDIDATE_SIZE_LIMIT_EXCEEDED`. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Use a real `DefaultCodeToMarkdownAgent` generation into a persistent workspace for receipt-only mutation; rewrite only local started-event fields (and the derived receipt ID, model link, roots, and manifest) while asserting stable Candidate IDs and invalid validation from the durable ledger mismatch.
- Keep positive fallback/empty-section/Gap Trace assertions structural and semantic, checking registry/template/task slots, basis/Proof/source, effective-template and zero-eligible accounting, and Gap reason/searched-scope/missing-evidence provenance rather than only IDs.
- Use sparse files positioned at limit+1 and tiny canonical archives/configs; never allocate a large byte array in the tests.

## Blockers

- None for this test-authoring slice. The six RED assertions are the intended evidence for the five remaining P1 review gaps; production fixes are explicitly outside scope.

## Exact next action

- Parent agent should consume the two new selectors and use the recorded RED baseline while addressing production gaps in its own authorized slice.

## Resume checks

- Confirm only this progress file and the two new test classes are owned by this slice; do not touch Round-2/addendum files or existing tests.
