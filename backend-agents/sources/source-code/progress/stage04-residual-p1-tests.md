# Progress: Stage04 residual P1 tests

- Status: COMPLETE
- Agent role: Stage04 residual P1 test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded Stage04 test class for the residual final-review P1 contracts: generation-receipt/model/root identity closure, technical fallback closure, Gap provenance closure, and stat-before-read resource rejection. No production, design, lifecycle, Round-2, addendum, or existing-test changes.
- Approved inputs: scoped `AGENTS.md`; `progress/final-implementation-review.md`; `Stage04ValidationHardeningTest`; current Stage04 production and progress files.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent and agent changes.

## Completed

- Added exactly one new test class, `Stage04ResidualP1Test`, with four `@Test` methods.
- Receipt/root test performs a coherent model-round and generation-receipt rewrite, updates the
  candidate roots and archive manifest, and leaves the candidate content identity stale. The
  current implementation incorrectly accepts the forged runtime/lifecycle closure (`RED`).
- Technical fallback Trace is exercised against registry, reader-template, and task-anchor
  mutations using a real Stage03 generator plus a scripted, provider-free lifecycle bridge. All
  three mutations fail closed with `TRACE_CLOSURE_BROKEN` (`GREEN`).
- Gap Trace is exercised against searched-scope, missing-evidence, and provenance mutations. All
  three mutations fail closed with `TRACE_CLOSURE_BROKEN` (`GREEN`).
- Source and durable ledger sparse limit+1 inputs use the largest declared source file and one
  persisted ledger event, avoiding large allocations. The current implementation returns
  `SOURCE_SIZE_MISMATCH` and `ROUND_SLOT_CONFLICT` instead of the stable resource-limit codes
  (`RED` for both categories).

## Current state

- The owned progress record was created before test edits.
- The tests use the real Stage03 generator/lifecycle bridge for fallback coverage, frozen source
  fixtures, and the persisted ledger seam. No live provider, network source, or customer build is
  used.
- Registration-specific stat-before-read coverage was not fabricated: the current public
  `FilesystemSourceRegistry` construction path has no configurable registration byte-limit seam
  or stable registration-size failure code. Source-file and ledger-event categories provide the
  required two-category evidence.

## Changed files

- `progress/stage04-residual-p1-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04ResidualP1Test.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ResidualP1Test test` | Expected RED | 4 tests, 2 failures, 0 errors: receipt closure/content identity and source/ledger stat-before-read resource cases. Fallback and Gap mutation subcases passed. |
| `git diff --check` | PASS | No whitespace errors reported. |

## Decisions

- Keep the residual cases in one new class with no more than five `@Test` methods.
- Combine generation receipt/model link/root/manifest and `candidateContentId` identity checks in one coherent archive mutation test.
- Use only frozen synthetic Stage03 fixtures and scripted lifecycle responses; no live model, source, network, or customer build.
- Preserve current RED assertions for missing receipt/runtime/lifecycle closure and observed
  resource-limit rejection; record already-fixed fallback and Gap fail-closed behavior as GREEN.
- Do not add a registration test without a public, bounded registration-read seam; document that
  seam limitation instead of inventing an API.

## Blockers

## Exact next action

- Parent agent may review the two owned files and integrate them without touching lifecycle,
  Round-2, addendum, production, design, or existing-test files.

## Resume checks

- Confirm only this progress file and the new residual test class are owned by this slice; do not
  touch lifecycle/Round2/addendum files.
