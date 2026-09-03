# Progress: Fact candidate exact upstream RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded M1 RED test
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam test for the exact seven-artifact upstream closure of the Fact Candidate module.
- Approved inputs: `docs/analysis-steps/04-proven-code-facts.md`, scoped `AGENTS.md`, existing Fact Candidate module reader/public fixture.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Added exactly one public-seam test: `FactCandidateExactUpstreamTest`.
- The test builds the real two-entry persisted source/discovery/graph fixture, reopens typed
  `FactCandidateInputs`, reflectively requires `candidateModuleUpstreamArtifacts()`, and checks the
  exact seven sorted references: five graph roots plus the discovery capability and entry-point
  descriptors.
- The positive publication path reflectively targets the strict future publisher seam
  `publish(AnalysisStepModuleAddress, FactCandidateInputs, FactCandidateSet)`, so the publisher—not
  its caller—must derive and freeze the complete upstream set.
- The negative publication is installed directly through the canonical module store after replacing
  only one discovery reference with a syntactically valid decoy and recomputing the content ID.
  This isolates the persisted-reader requirement without asking the strict publisher to create an
  invalid artifact. The reader must reject it with `PROOF_PACK_REFERENCE_BROKEN`.

## Current state

The existing typed FactCandidateInputs exposes five graph roots but does not yet expose the complete seven-artifact upstream set required by the M1 module contract. The RED test will assert the seven sorted references and fail closed when a valid-but-unrelated discovery artifact is substituted.

## Changed files

- `progress/fact-candidate-exact-upstream-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactUpstreamTest test` | EXPECTED RED | 1 test; 1 failure, 0 errors; `FACT_CANDIDATE_UPSTREAM_ACCESSOR_NOT_IMPLEMENTED` |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactUpstreamTest test` (after strict-seam test update) | EXPECTED RED | 1 test; 1 failure, 0 errors; `FACT_CANDIDATE_UPSTREAM_ACCESSOR_NOT_IMPLEMENTED` |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateExactUpstreamTest.java spotless:check` | PASS | `BUILD SUCCESS` |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Keep the test bounded to the public typed input reader and module publisher.
- Do not modify production code, design documents, POM files, or existing tests.

## Blockers

## Exact next action

Terra should add the public typed accessor, replace the publisher with the strict typed seam, and
make the persisted module reader require the exact seven upstream references, then rerun this
selector as GREEN without changing this test.

## Resume checks

- Re-read this progress file first.
- Confirm only this progress file and the one new test are in scope before editing.
- Run `git status --short` and inspect the actual test diff before reporting.
