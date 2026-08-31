# Progress: Stage04 Candidate assembler RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Bounded in-process Candidate assembler contract from honest Stage01/02/03 synthetic results to a complete CandidateBundle.
- Approved inputs: scoped AGENTS.md; `docs/stages/04-runtime-archive-trace-recovery.md` §§6, 9, 10, 12, 15; current Stage01–04 public/package records and tests.
- Current branch/worktree: shared worktree; preserve unrelated parent changes.

## Completed

- Created this progress file before test edits.

## Current state

- Added the bounded Stage03 scenario bridge and assembler test. The bridge runs
  the real synthetic Stage01 -> Stage02 -> scripted Stage03 path; the test then
  checks exact 17-artifact projection, canonical bytes, Stage01/02/03/slot
  bindings, typed trace/generation receipts, root relocation identity, store
  acceptance, and tamper rejection.
- Correcting only the C2 store integration budget: the complete proof-pack sidecar
  is independently measured at 109,208 bytes, so this test must use a 200,000-byte
  sidecar ceiling while retaining the 1,000,000-byte total ceiling.
- Updated the assembler-to-store assertion to `new CandidateStoreLimits(1_000_000,
  200_000)`; C1's independent size-limit tests remain unchanged.

## Changed files

- `progress/stage04-candidate-assembler-tests.md`
- `src/test/java/com/linguan/codemd/stage03/Stage03ScenarioBridge.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateAssemblerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04CandidateAssemblerTest test` | GREEN | Main compilation succeeded for 176 sources; test compilation succeeded for 53 sources; `Stage04CandidateAssemblerTest` ran 2 tests with 0 failures, 0 errors, 0 skipped. |

## Decisions

- The assembler must compute Candidate content/lineage identity from its request
  and results; the test will not supply a precomputed candidate ID.
- Candidate artifacts remain synthetic, canonical UTF-8 JSON/JSONL, and are
  checked independently for exact key/byte/reference closure.
- Store integration is limited to the existing `FilesystemCandidateStore` seam;
  no Provider, HTTP, customer source, or filesystem archive behavior is added.

## Blockers

- The corrected store integration admits the independently measured 109,208-byte
  proof pack and remains green; no C1 size-limit test or production/design file
  was changed.

## Exact next action

- None for this test-only correction; the narrow selector is green.

## Resume checks

- COMPLETE. Only this progress, the new Stage04 assembler test, and the explicit
  Stage03 test-only bridge were edited by this slice; no production or design
  file was changed.
