# Progress: Fact candidate module reader RED

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for fresh reopening and fail-closed validation of the persisted Fact M1 candidate-set module artifact.
- Approved inputs: Scoped AGENTS.md; docs/analysis-steps/04-proven-code-facts.md M1/module artifact contract; source-analysis implementation plans; current canonical module store, FactCandidateSetModulePublisher, and ProgramGraphsPublicFixture.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Created this progress record before modifying the test tree.
- Added `FactCandidateModuleReaderTest` using the real canonical module store, graph fixture,
  candidate enumerator, and M1 module publisher.
- The test now requires the typed public seam
  `PersistedFactCandidateSetReader(CanonicalModuleArtifactStore)` with
  `reopen(ModulePublicationReference, FactCandidateInputs, FactRegistry)`.
- The positive path asserts a fresh reopened `FactCandidateSet` preserves candidate-set identity,
  candidate contents, and denominator. The same test mutates the persisted candidate-set payload
  and requires the reopen path to fail closed.

## Current state

The publisher installs a canonical module artifact, but no public typed reader has been established. The RED will require fresh reopening through a typed `PersistedFactCandidateSetReader` seam and rejection after a persisted candidate-set payload mutation.

## Changed files

- `progress/fact-candidate-module-reader-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleReaderTest test` | RED (expected) | testCompile succeeded; 1 test, 1 failure, 0 errors; `FACT_CANDIDATE_MODULE_READER_NOT_IMPLEMENTED` because the production reader class is absent. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java spotless:check` | PASS | Scoped test formatting is clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Use the real `FactCandidateSetModulePublisher`, canonical filesystem module store, and real graph fixture; do not hand-build JSON or mock persistence.
- Keep the test scoped to M1 persisted reader behavior. Do not modify production, design, POM, existing tests, or M2 Proof.

## Blockers

## Exact next action

Hand the RED to Terra for the production typed reader. The test owns no production correction.

## Resume checks

Read this file, inspect `git status --short`, verify only this test and progress are owned by this task, and run the named selector only.
