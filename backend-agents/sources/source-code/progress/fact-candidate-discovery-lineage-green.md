# Progress: Fact candidate discovery lineage green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make the Fact M1 persisted input reader reject an application profile whose source inventory or verified snapshot reference is not the exact payload from the reopened verified-source publication.
- Approved inputs: Scoped `AGENTS.md`; `docs/analysis-steps/04-proven-code-facts.md`; current implementation plans; Luna RED `FactCandidateDiscoveryLineageTest`.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, Fact M1 contract, target plans, and the new public RED test.
- Confirmed the reader currently parses the profile references but does not compare them to the reopened source publication payload descriptors.
- Derived the source inventory and verified snapshot references from the reopened verified-source publication's exact payload descriptors.
- Rejected either mismatching profile reference before graph parsing or Fact enumeration with `PROOF_PACK_REFERENCE_BROKEN`.

## Current state

- The reader now derives expected references directly from the reopened verified-source publication's `source-inventory.jsonl` and `verified-snapshot.json` descriptors, then requires an exact match in `application-profile.json`.

## Changed files

- `progress/fact-candidate-discovery-lineage-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateDiscoveryLineageTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateSourceExcerptIntegrityTest,FactCandidateGraphProfileReferenceTest,FactCandidateGraphIndexDescriptorTest,FactCandidateBoundaryOwnershipTest,FactCandidateDiscoveryLineageTest test` | PASS | 10 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateDiscoveryLineageTest.java' spotless:check` | PASS | Scoped formatting check. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the correction inside `PersistedFactCandidateInputReader`; do not change fixtures, schemas, controls, or graph behavior.

## Blockers

- None.

## Exact next action

- Return the precise implementation and verification result to the parent Agent.

## Resume checks

- Re-read this file; inspect `git status --short`; run `FactCandidateDiscoveryLineageTest` and the ten Fact M1 selectors.
