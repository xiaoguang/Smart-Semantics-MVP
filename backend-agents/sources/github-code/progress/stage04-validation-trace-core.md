# Progress: Stage04 validation and factual Trace core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra xhigh
- Scope: Implement only Stage04 D1 fresh Candidate validation, receipt persistence, and factual ReaderItem Trace.
- Approved inputs: scoped `AGENTS.md`; Stage04 §§5, 11, 12, 14, 16; corrected D1 RED test; current Stage04 assembler/store; Stage01 snapshot verifier and public records.

## Current state

- Created this owned progress file before production changes.
- Reproduced the intended 19-symbol missing-seam RED, then implemented the bounded
  archive validator and factual Trace seam. Validation is fresh-process and
  Provider-free; it verifies exact archive structure/manifest/canonical JSON,
  sidecar and document identity, nine headings, then reopens the registered M1
  request. Trace reopens the verified source bytes rather than returning an
  archived excerpt.
- D1 and the requested Stage04 regression slice are GREEN. The manifest replay
  initially failed because the D1 recomputation prefix contained a literal
  backslash-plus-`n`; it now uses the required newline and compares the actual
  recomputed `archiveManifestId` (there is no diagnostic bypass).

## Changed files

- `progress/stage04-validation-trace-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`

## Verification

| Command | Result | Detail |
| --- | --- | --- |
| `mvn -Dtest=Stage04ValidationTraceTest test` | RED | Test compilation reached the intended 19 missing D1 production symbols. |
| `mvn -Dtest=Stage04ValidationTraceTest test` | GREEN | 2 tests, 0 failures, 0 errors. Honest fresh validation and exact reopened factual Trace pass; source/archive drift and unknown reader item fail closed. |
| `mvn -Dtest=Stage04IdentityLedgerTest,Stage04LifecycleProviderTest,Stage04CandidateStoreTest,Stage04CandidateAssemblerTest,Stage04ValidationTraceTest test` | GREEN | 17 tests, 0 failures, 0 errors. |
| `git diff --check -- <scoped tracked paths>` and `git diff --no-index --check /dev/null <each owned untracked path>` | GREEN | No whitespace diagnostics (the no-index commands return 1 only because they compare an added file to `/dev/null`). |

## Blockers

- None.

## Exact next action

- Await parent integration/review; do not broaden into D2 variants.
