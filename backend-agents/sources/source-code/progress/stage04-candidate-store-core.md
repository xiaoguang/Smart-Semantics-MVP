# Progress: Stage04 Candidate store core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra xhigh
- Scope: Implement the bounded Stage04 C1 filesystem candidate store in `src/main/java/com/linguan/codemd/stage04/` only.
- Approved inputs: scoped `AGENTS.md`; Stage04 §§6, 10, 14, 16; Candidate store RED tests; current Stage04 ledger and M8 types.

## Current state

- Created this owned progress file before production changes.
- Implemented the bounded filesystem store: defensive byte copies, exact 17-artifact closure, strict UTF-8/canonical JSON or JSONL validation, generated sorted manifest, staging plus atomic installation, idempotency/collision checks, and symlink/size gates.
- The corrected C1 contract and the direct Stage04 regressions are GREEN.

## Changed files

- `progress/stage04-candidate-store-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemCandidateStore.java`

## Verification

| `mvn -Dtest=Stage04CandidateStoreTest test` | RED then GREEN | Initial: 26 missing seam symbols at test compilation. After the fixture correction: 6 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04IdentityLedgerTest,Stage04LifecycleProviderTest,Stage04CandidateStoreTest test` | GREEN | 13 tests, 0 failures, 0 errors. |
| `git diff --check -- src/main/java/com/linguan/codemd/stage04` | GREEN | Exit 0; no scoped whitespace errors. |

## Blockers

- None.

## Exact next action

- None; bounded Candidate store C1 is complete.
