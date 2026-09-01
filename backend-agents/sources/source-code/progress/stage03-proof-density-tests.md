# Progress: Stage 03 proof and semantic density RED tests

- Status: COMPLETE
- Agent role: Stage03 proof/density RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most three bounded public-seam tests/fixtures for formula and OutcomePath conservation; do not modify production, design, or prior tests.
- Approved inputs: scoped AGENTS, Stage03 design, current public Stage01/Stage02/Stage03 records and generator seam.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Created this owned progress file before modifying tests.

## Current state

- Added one public-seam Outcome conservation test. It derives every OutcomePath and required atom basis from the real Stage02 result, matches each public BusinessOutcome by outcomePathId/basis, and requires one section-4 typed outcome ReaderItem whose slots retain terminal and branch polarity/guard semantics.
- The test is assertion-only RED with no test errors: the current result preserves BusinessOutcome rows but emits no typed outcome ReaderItems, so all four paths lack section-4 ownership and semantic slots.

## Changed files

- `progress/stage03-proof-density-tests.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03ProofDensityTest test` | RED | 1 test, 1 failure containing 5 grouped failures, 0 errors. All four `OutcomePath`s lack exactly one section-4 outcome ReaderItem (0 vs 1), lack terminal slot values (`THROW`/`RETURN`), and lack polarity/guard semantics; section 4 has 0 typed outcome items vs 4 paths. |

## Decisions

- Tests will inspect actual public Stage02 `AllowedFact`/`FactAtom` values and Stage03 public result records rather than fixture-specific constants in production code.
- The third requested closure slice will prefer exact public Flow/Capsule allowlist accounting; deeper source mutations will be deferred if Stage02 replay cannot be constructed without private hooks.
- No production implementation or design document changes are authorized.
- The public `BusinessOutcome` record exposes `outcomePathId` and `basisAtomIds`, so the test asserts those directly; terminal/polarity/guard preservation is required through public ReaderItem typed slot values because ReaderItem has no separate terminal metadata fields.

## Blockers

## Exact next action

- Production implementation should satisfy the Outcome conservation contract, then rerun only `mvn -Dtest=Stage03ProofDensityTest test`; formula mutation and deeper Capsule closure remain deferred.

## Resume checks

- Re-read this file, run `git status --short`, and keep changes confined to `src/test/java/com/linguan/codemd/stage03/` plus this progress file.
