# Progress: Fact candidate boundary ownership GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Reject a persisted `JAVA_BOUNDARY_INVOCATION` data-flow node with no owning entry before Fact M1 enumeration.
- Approved inputs: scoped `AGENTS.md`; two implementation plans; `04-proven-code-facts.md`; the established RED test and its progress record.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`.

## Completed

- Read the scoped contracts, plans, M1 design, existing typed reader/input seam, and the bounded RED.
- Reproduced the expected RED: the fresh reader accepted an ownerless Java boundary and would let enumeration silently drop it.
- Added the boundary-specific nonempty-owner validation in `FactCandidateInputs.PublicProgramNode`.
- Confirmed the bounded RED is GREEN and all nine current M1 Fact-candidate selectors remain GREEN.

## Current state

- Root cause: `PublicProgramNode` accepts an empty owner list for every kind; the generic allowance is correct for non-boundary nodes, but persisted Java boundary nodes require at least one entry to define the M1 denominator.

## Changed files

- `progress/fact-candidate-boundary-ownership-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateInputs.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateBoundaryOwnershipTest test` | RED | 1 test; reader returned normally instead of `PROOF_PACK_REFERENCE_BROKEN`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateBoundaryOwnershipTest test` | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateBoundaryOwnershipTest,FactCandidateEvidenceSupportKindTest,FactCandidateExactPathTest,FactCandidateGhostEndpointTest,FactCandidateGraphIndexDescriptorTest,FactCandidateGraphProfileReferenceTest,FactCandidateIdentityTest,FactCandidateMissingPathTest,FactCandidateSourceExcerptIntegrityTest test` | PASS | 9 tests, 0 failures/errors/skips. |

## Decisions

- Apply the smallest validation in the typed Fact input seam: only `JAVA_BOUNDARY_INVOCATION` requires nonempty `owningEntryIds`; all other public program nodes retain their current generic behavior.
- The failure remains a malformed persisted input (`PROOF_PACK_REFERENCE_BROKEN`), not a business-flow Gap or any inference about external systems.

## Blockers

## Exact next action

- Parent may include this slice in the current M1 gate; no additional implementation is pending here.

## Resume checks

- Confirm only this progress file and the bounded `analysis/fact/candidates` production file are owned by this task.
