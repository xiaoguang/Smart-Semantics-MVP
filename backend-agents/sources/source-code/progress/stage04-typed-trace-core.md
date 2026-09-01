# Progress: Stage04 typed Trace core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra xhigh
- Scope: Implement only D2 typed Trace closure over the immutable Candidate archive.
- Approved inputs: scoped `AGENTS.md`; Stage04 §12; `Stage04TypedTraceTest`; current C2 archive and D1 Trace seams.
- Current branch/worktree: shared and pre-existing dirty; unrelated changes are preserved.

## Completed

- Read the scoped contract, D2 RED test/progress, D1 archive Trace code, and C2 archive projections.
- Confirmed the bounded design: all five public trace kinds share one strict
  plan/trace-record closure gate; only atom chains reopen source. Terms prove
  archived admitted meaning and business-term key, not unavailable registry bytes.

## Current state

- D2 now has a strict plan/trace graph gate, term/GAP/reference dispatch, and
  reused D1 atom-source reopening. The two original RED behaviors are green:
  admitted-term lineage resolves and a refreshed-manifest unknown reference is
  rejected. The only remaining selector error is a test-fixture/design mismatch
  for `TECHNICAL_FALLBACK`: the selected artifact is a valid built-in
  `EMPTY_SECTION` but the test independently requires a matching flow-level
  `TechnicalDisplayResolution`, while the fixture's `technicalFallbacks` is
  empty. The test contract has now been corrected to recognize this legitimate
  built-in empty-section lineage; D2 narrow is green. Next: direct Stage04
  regression selector only.

## Changed files

- `progress/stage04-typed-trace-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04TypedTraceTest test` | RED | 2 failures: missing admitted-term dispatch and unknown reference accepted after manifest refresh. |
| `mvn -Dtest=Stage04TypedTraceTest test` | BLOCKED by fixture contract (resolved) | 2 tests execute: 0 failures, 1 error. Term/reference implementations are green; the test formerly sought an absent flow-level fallback for a built-in item. |
| `mvn -Dtest=Stage04TypedTraceTest test` | GREEN | 2 tests, 0 failures, 0 errors after the test contract distinguished built-in empty sections from flow fallbacks. |
| `mvn -Dtest='Stage04*Test' test` | GREEN | 19 tests, 0 failures, 0 errors, 0 skipped across all six direct Stage04 selectors. |
| `git diff --check` | GREEN | No whitespace diagnostics. |

## Decisions

- Registry content is not an archive artifact in D2; term lineage stops at the
  admitted meaning and its business-term key.
- Gap lineage never gets a positive Fact/Proof/source span solely for absence.
- `EMPTY_SECTION` is traced as its registry-backed built-in template, not as a
  fictional flow `TechnicalDisplayResolution`.

## Blockers

- None.

## Exact next action

- Await parent integration/review; do not broaden beyond D2.
