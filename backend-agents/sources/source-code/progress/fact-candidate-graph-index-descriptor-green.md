# Progress: Fact candidate graph-index descriptor GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh bounded production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make Fact M1 fail closed when a graph-index descriptor diverges from the persisted public graph it names.
- Approved inputs: Step 04 design, both implementation plans, and the completed `FactCandidateGraphIndexDescriptorTest` RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the scoped Agent rules, target plans, existing RED test, and `PersistedFactCandidateInputReader` seam.
- Confirmed the supplied selector is a clean RED: one assertion failure because the reader accepts a forged DATA_FLOW descriptor `graphId`.
- Retained each parsed graph's public payload identity metadata and made `validateIndex` compare a descriptor's kind, file name, artifact type, schema version, graph ID, and artifact reference to that exact graph or Evidence payload.
- Kept the existing fail-closed error and all other candidate-enumeration behavior unchanged.

## Current state

- The supplied descriptor mismatch is now rejected before typed Fact inputs reach candidate enumeration.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- This progress file only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphIndexDescriptorTest test` | RED as expected | 1 test, 1 assertion failure, 0 errors; the reader accepted the forged descriptor. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphIndexDescriptorTest test` | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateSourceExcerptIntegrityTest,FactCandidateIdentityTest,FactCandidateGraphProfileReferenceTest,FactCandidateGraphIndexDescriptorTest test` | PASS | 8 tests, 0 failures/errors/skips. |
| changed-file `spotless:check` and `git diff --check` | PASS | no format or whitespace issues. |

## Decisions

- Retain existing public wire and fail with `PROOF_PACK_REFERENCE_BROKEN`; do not add a compatibility path or change schemas.

## Blockers

- None.

## Exact next action

- Return control to the M1 coordinator for the next bounded Fact candidate gate.

## Resume checks

- Read the M1 coordinator progress and current worktree status before any further task.
