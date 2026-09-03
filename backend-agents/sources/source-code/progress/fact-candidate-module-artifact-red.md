# Progress: Fact candidate module artifact RED

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for persisted Fact M1 candidate-set module artifact.
- Approved inputs: Scoped AGENTS.md; docs/analysis-steps/04-proven-code-facts.md; source-analysis implementation plans; current canonical stores and graph fixture.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

## Current state

The Fact M1 enumerator returns a typed `FactCandidateSet`, but no public M1 module publication seam has been identified in production. The RED test will specify the smallest public publisher contract needed to install `modules/01-candidates/fact-candidate-set.json` and fresh-reopen it by its typed reference.

## Changed files

Added one test-only RED at `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java`.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleArtifactTest test` | RED (expected) | testCompile passed; 1 test, 1 failure, 0 errors; `FACT_CANDIDATE_MODULE_PUBLICATION_NOT_IMPLEMENTED` because `FactCandidateSetModulePublisher` is absent |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java spotless:check` | PASS | BUILD SUCCESS |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Test the M1 module artifact through canonical module stores and typed reopened records, not through filesystem paths, raw JSON input, or an in-memory-only assertion.
- Preserve the exact M1 wire contract: `PROVEN_CODE_FACTS_FACT_CANDIDATE_SET`, `proven-code-facts-fact-candidate-set-v1`, complete candidate set fields, and fresh-reopen identity.
- Do not change production, POM, design, or existing tests.

## Blockers

## Exact next action

Hand the RED to Terra for the production publisher implementation; preserve this test as the public M1 module-publication contract.

## Resume checks

Read this file, inspect `git status --short`, confirm only this test slice and progress are in scope, then run the named Maven selector only.
