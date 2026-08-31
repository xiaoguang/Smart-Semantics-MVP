# Progress: Stage04 Candidate assembler core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra xhigh
- Scope: Implement only the Stage04 C2 candidate assembly seam and narrowly required Stage04 helpers.
- Approved inputs: scoped `AGENTS.md`; Stage04 §§6, 9, 10, 12, 15; Candidate assembler RED test; Stage03 scenario bridge; current Stage04 ledger/store.

## Current state

- Created this owned progress file before production changes.
- Implemented the bounded C2 request/compiler seam. It closes Stage01→02→03 and slot lineage, validates document/plan/reader/receipt closure, emits all 17 canonical artifacts, derives Candidate content/lineage IDs rootlessly, and hands the bundle to the existing store.
- The complete 109,208-byte `proof-pack.json` is retained; the C2 fixture's isolated sidecar budget was raised to 200,000 bytes. All four direct Stage04 selectors are GREEN.

## Changed files

- `progress/stage04-candidate-assembler-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`

## Verification

| `mvn -Dtest=Stage04CandidateAssemblerTest test` | RED then GREEN | Initial: 9 missing assembler/request symbols. After the C2 budget correction: 2 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04IdentityLedgerTest,Stage04LifecycleProviderTest,Stage04CandidateStoreTest,Stage04CandidateAssemblerTest test` | GREEN | 15 tests, 0 failures, 0 errors. |
| `git diff --check -- src/main/java/com/linguan/codemd/stage04` | GREEN | Exit 0; no scoped whitespace errors. |

## Blockers

- None.

## Exact next action

- None; bounded Candidate assembler C2 is complete.
