# Progress: Fact candidate graph-profile reference GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh bounded M1 implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03T12:49:53Z
- Last updated: 2026-09-03T12:53:10Z
- Scope: Make `PersistedFactCandidateInputReader` retain and enforce one identical graph-profile reference across the four program graphs, Evidence graph, and graph-index. No schema, fixture, registry, design, POM, or external behavior changes.
- Approved inputs: Scoped `AGENTS.md`; both approved implementation plans; `docs/analysis-steps/04-proven-code-facts.md`; review finding P1; `FactCandidateGraphProfileReferenceTest` expected RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the M1 contract, implementation plans, TDD and code-review reception guidance.
- Confirmed the dedicated persisted public-wire test has the expected RED: a decoy CALL/index graph-profile reference is currently accepted.
- Retained each program graph and Evidence graph profile reference in private persisted-reader values and required one exact reference across all graphs and the graph index.
- The dedicated selector is GREEN.

## Current state

- The bounded graph-profile correction is complete. The persisted reader fails closed when any program graph, Evidence graph, or graph-index profile reference diverges.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- `progress/fact-candidate-graph-profile-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphProfileReferenceTest test` | RED (pre-existing test run) | Expected `PROOF_PACK_REFERENCE_BROKEN`; reader accepted decoy profile reference. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphProfileReferenceTest test` | PASS | 1 test, 0 failures/errors/skips; the decoy profile is now rejected. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateSourceExcerptIntegrityTest,FactCandidateIdentityTest,FactCandidateGraphProfileReferenceTest test` | PASS | 7 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateGraphProfileReferenceTest.java' spotless:check` | PASS | Scoped formatting check passed. |
| `git diff --check` | PASS | No tracked-diff whitespace errors. |

## Decisions

- A mismatched graph-profile reference is a publication/lineage inconsistency, therefore a fatal `PROOF_PACK_REFERENCE_BROKEN`, not a candidate-level disposition.
- This slice validates only cross-graph profile equality. Graph IDs and descriptor identity remain outside this bounded correction.

## Blockers

- None.

## Exact next action

- Parent may incorporate this bounded M1 correction into the analysis-step acceptance gate. Do not expand it to graph IDs/descriptors; that is a separate review item.

## Resume checks

- Recheck the reader retains the private graph-profile values and all seven M1 direct selectors remain green before staging any shared M1 delivery.
