# Progress: M5 R2 basis gate tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add minimal public-seam RED tests proving R2 cannot expand the exact R1 basis and cannot reference foreign atom/gap IDs.
- Approved inputs: Existing persisted M4 task/registry fixture and scripted Provider only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout

## Completed

- Read scoped instructions, Step 06 M5 contract, InterpretationRunner and its public fixture path.

## Current state

- Existing runner validates R2 key membership and same-Flow membership, but `validateBasis` does not compare R2 basis with the exact R1 selection.
- Added two direct public-seam tests using persisted M4 task/registry preparation and scripted Provider.
- Corrected the expansion fixture to publish an M3 registry item whose single key has two allowed, same-Capsule atom IDs; M4 is then recompiled and persisted from that registry.
- The foreign-reference fixture remains separate and uses a distinct invalid atom/Gap pair.
- The corrected tests now pass because a concurrent Terra change already implements the reviewed R2 membership/subset gate; this task did not modify production.

## Changed files

- This progress file only so far.
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/M5R2BasisGateTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=M5R2BasisGateTest#rejectsR2BasisExpansionAgainstTheExactR1Selection test` | PASS | 1 test; corrected same-key/two-allowed-atom fixture is rejected with `MODEL_REVIEW_EXPANDED`. Production was already changed by another agent before this corrected run. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=M5R2BasisGateTest#rejectsR2ForeignAtomAndGapReferencesBeforeCandidateCreation test` | PASS | 1 test; foreign atom/Gap reference is rejected with `MODEL_REFERENCE_INVALID`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=M5R2BasisGateTest#rejectsR2BasisExpansionAgainstTheExactR1Selection+rejectsR2ForeignAtomAndGapReferencesBeforeCandidateCreation test` | PASS | 2 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/model/M5R2BasisGateTest.java` | PASS | Formatting applied. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the test small and fixture-derived; do not alter production or design.
- Use one parameterized public-seam test for basis expansion and foreign references if the existing fixture can provide both independently.

## Blockers

- None yet.

## Exact next action

- Hand off the corrected test contract and green evidence to the M5 production implementation agent/reviewer.

## Resume checks

- Confirm no production files changed by this task.
- Confirm the corrected expansion fixture contains two distinct allowed basis atoms from the same persisted M3 registry item.
- Confirm production changes shown by `git diff` belong to the Terra implementation task, not this test task.
