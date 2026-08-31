# Progress: Stage04 deterministic validation and factual Trace RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: One bounded D1 public/package seam test for fresh Candidate validation and factual Trace over a real assembled and stored Stage01→Stage03 synthetic Candidate.
- Approved inputs: scoped AGENTS.md; `docs/stages/04-runtime-archive-trace-recovery.md` §§5, 11, 12, 16; current Stage01–04 records, assembler/store, and Stage03ScenarioBridge.
- Current branch/worktree: shared worktree; preserve unrelated parent and agent changes.

## Completed

- Created this progress file before test edits.
- Read the D1 source-registry, deterministic validation, Trace, failure-code, and TDD requirements and inspected the current assembler/store and Stage03 scenario bridge.

## Current state

- About to add one bounded Stage04ValidationTraceTest. It will assemble and install the honest synthetic Candidate, bind its snapshot to the honest Stage01 request/root through an in-memory registry fixture, then target the proposed CandidateValidationService and TraceResolver package seams.
- The test will keep expected values independent of production canonicalization: Stage01/Stage02/Stage03 result records and reopened fixture bytes provide the source of truth for candidate/document/span checks.
- Added the bounded test class with one positive validation/Trace flow, repeat-validation idempotency, source-byte drift, archived-document tamper, and unknown-reader-item fail-closed assertions.
- Corrected the repeat-validation archive comparison to compare artifact keys and exact bytes independently, rather than relying on `Map.equals` for `byte[]` values.
- The test source compiles cleanly up to the expected missing D1 production seam: there are no fixture accessor, import, or assertion-shape errors in the compiler output.

## Changed files

- `progress/stage04-validation-trace-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04ValidationTraceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ValidationTraceTest test` | RED (previous) | Main compilation succeeds for 176 sources and test compilation reaches 54 sources, then stops with 19 missing-symbol errors for the proposed D1 `SourceRegistry`, `CandidateValidationService`, `CandidateTraceResolver`, `ValidationReceipt`, `TraceQuery`, and `TraceView` types; 0 test methods execute. |
| `mvn -Dtest=Stage04ValidationTraceTest test` | RED (expected) | After the byte-map assertion correction, main compilation succeeds for 176 sources and test compilation reaches 54 sources, then stops with the same 19 missing-symbol errors for the proposed D1 `SourceRegistry`, `CandidateValidationService`, `CandidateTraceResolver`, `ValidationReceipt`, `TraceQuery`, and `TraceView` types; 0 test methods execute. |
| `git diff --check -- progress/stage04-validation-trace-tests.md src/test/java/com/linguan/codemd/stage04/Stage04ValidationTraceTest.java` | GREEN | No whitespace diagnostics. |
| `git status --short -- progress/stage04-validation-trace-tests.md src/test/java/com/linguan/codemd/stage04/Stage04ValidationTraceTest.java` | GREEN | Only the owned progress and new test are untracked/changed in the scoped status view. |

## Decisions

- Use the existing real Stage03ScenarioBridge and CandidateAssembler/FilesystemCandidateStore seams; do not fabricate Stage02/03 records or invoke a Provider.
- Keep source roots private in the test registry and assert returned Trace source paths are root-relative.
- Exercise validation as a fresh service instance and Trace as a separate resolver; no legacy MVP archive or reflection/private method access.

## Blockers

- The D1 validation/Trace service, registry, receipt, and typed Trace seams are not present in the current production source; the first selector is compile RED until the production agent supplies them. This is a precise missing-seam RED, not a fixture/runtime failure.

## Exact next action

- Production agent can now implement the proposed D1 seam against this test; rerun only `mvn -Dtest=Stage04ValidationTraceTest test` after those types land.

## Resume checks

- COMPLETE. Confirm only this progress and `src/test/java/com/linguan/codemd/stage04/Stage04ValidationTraceTest.java` were edited by this slice; do not modify Stage04 production, design, C1, or C2 files.
