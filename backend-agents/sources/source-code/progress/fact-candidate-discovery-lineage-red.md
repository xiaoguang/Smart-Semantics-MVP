# Progress: Fact candidate discovery lineage RED

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam regression test for ApplicationDiscovery source lineage validation in Fact M1.
- Approved inputs: AGENTS.md; docs/analysis-steps/04-proven-code-facts.md; docs/plans/source-analysis-naming-and-delivery-plan.md; docs/plans/target-standards-and-toolchain-plan.md; ProgramGraphsPublicFixture.
- Current branch/worktree: codex/source-analysis-proven-code-facts / /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Added one public-seam test mutating only `application-profile.json.sourceInventoryRef` to an unrelated, well-formed source-inventory reference.
- Added test-only `republishMutatedDiscovery` fixture seam: source publication is copied, mutated discovery is installed downstream of it, and the unchanged graph publication is installed downstream of both.
- Confirmed the fresh Fact M1 reader currently accepts the invalid discovery-to-source lineage, producing the intended RED.

## Current state

Added the single public-seam test and the smallest test-only fixture method needed to mutate the persisted discovery payload and republish the downstream graph. The exact selector reached the intended RED: the fresh reader returned normally instead of failing closed. Production source, design, POM, and existing production tests remain untouched.

## Changed files

- progress/fact-candidate-discovery-lineage-red.md
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateDiscoveryLineageTest.java
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java

## Verification

| Command | Result | Key output |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateDiscoveryLineageTest test` | EXPECTED RED | 1 test, 1 assertion failure, 0 errors; `Expecting code to raise a throwable` because the reader accepted the unrelated `sourceInventoryRef`. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateDiscoveryLineageTest.java,src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java spotless:check` | PASS | BUILD SUCCESS. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Test only one failure mode: application-profile sourceInventoryRef points to a valid-looking but unrelated source artifact while the downstream graph publication remains otherwise coherent.
- The fresh persisted Fact M1 reader must reject this as PROOF_PACK_REFERENCE_BROKEN before enumeration.

## Blockers

## Exact next action

Terra should implement the smallest fresh-reader check that profile `sourceInventoryRef` and `verifiedSnapshotRef` exactly match the actual Verified Source publication payload references, preserving `PROOF_PACK_REFERENCE_BROKEN`.

## Resume checks

- Confirm only this progress file and the narrowly scoped test/fixture paths changed.
- Confirm no production implementation was edited.
- Confirm Maven selector failure is the intended missing lineage validation, not a fixture/compiler error.
