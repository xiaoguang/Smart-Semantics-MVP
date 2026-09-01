# Progress: Stage04 typed Trace RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Bounded D2 typed Trace coverage for admitted terms, technical fallbacks, Gap questions, and reference-only ReaderItems over an honest stored synthetic Candidate.
- Approved inputs: scoped AGENTS.md; `docs/stages/04-runtime-archive-trace-recovery.md` §§12, 16; current Candidate assembler, D1 validation/Trace implementation, Stage03ScenarioBridge, and Stage03 public plan/model records.
- Current branch/worktree: shared worktree; preserve unrelated parent and agent changes.

## Completed

- Created this progress file before test edits.
- Read the D2 Trace kinds, source-reopen closure, and test-matrix requirements and inspected the current archive/Trace projection and typed Stage03 records.
- Corrected `assertFallbackLineage` so an honest built-in empty-section fallback is checked as template lineage, while matching Stage03 flow fallbacks retain their existing policy assertion.

## Current state

- The test contract distinguishes flow-level `TechnicalDisplayResolution` from registry-backed built-in empty-section items without manufacturing a flow fallback.
- Built-in empty-section assertions require `EMPTY_SECTION` and `READER_EMPTY_SECTION_*`, validate ReaderItem/section-anchor and `BUILT_IN_EMPTY_SECTION` template lineage, and reject Fact/Proof/source claims.

## Changed files

- `progress/stage04-typed-trace-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04TypedTraceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04TypedTraceTest test` | PASS | Main compilation succeeds for 177 sources and test compilation for 55 sources; Surefire runs 2 tests with 0 failures, 0 errors, and 0 skips; Maven BUILD SUCCESS. |

## Decisions

- Use only the honest Stage03ScenarioBridge → CandidateAssembler → FilesystemCandidateStore path; no Provider, HTTP, reflection, or archive-side fabrication.
- Derive expected term/fallback/Gap/reference IDs from public Stage03 plan/model records and archived trace lines; source span expectations remain independently reopened from Stage01 frozen bytes.
- Keep mutation cases archive-local and fail closed with `TRACE_CLOSURE_BROKEN`; do not weaken manifest/validation checks or add production hooks.

## Blockers

- None.

## Exact next action

- Parent agent may integrate this test-only contract correction; no further test work is required in this slice.

## Resume checks

- COMPLETE. Confirm only this progress and `src/test/java/com/linguan/codemd/stage04/Stage04TypedTraceTest.java` were edited by this slice; no Stage04 production, design, D1/C1/C2 tests, or Stage03 fixtures were modified.
