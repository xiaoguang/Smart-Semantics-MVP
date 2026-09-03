# Progress: Fact candidate module reader fixture fix

- Status: COMPLETE
- Agent role: Luna/xhigh test-fixture correction
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Align M1 module publication tests with one canonical ArtifactControls instance; no production changes.
- Approved inputs: M1 module artifact/readers tests, ProgramGraphsPublicFixture, M1 design contract.
- Current branch/worktree: codex/source-analysis-proven-code-facts

## Completed

## Current state

The positive module artifact and fresh-reader tests currently construct a separate policy registry and controls from the fixture's upstream publications. This violates the M1 contract that the candidate module artifact controls must equal the fresh-reopened FactCandidateInputs controls.

## Changed files

Updated the test-only `ProgramGraphsPublicFixture` to expose the exact registry and controls used
to publish its source, discovery, and graph publications. Added the candidate-set module policy to
that same registry. Updated `FactCandidateModuleArtifactTest` and
`FactCandidateModuleReaderTest` to use those exposed values, so both publication and fresh input
share one canonical controls identity.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest test` | EXPECTED PARTIAL RED | module artifact test PASS; reader test fails only with `FACT_CANDIDATE_MODULE_READER_NOT_IMPLEMENTED` |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java spotless:check` | PENDING | Run after formatting check is available |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

Expose only a test-scoped way to reuse the fixture's canonical controls/policies; do not alter production code or introduce a second control identity.

## Blockers

None known.

## Exact next action

Parent should incorporate this fixture correction and let the production reader owner implement the
expected missing `PersistedFactCandidateSetReader`; this task does not modify production.

## Resume checks

Read this file, then verify `git status --short`; inspect only the owned fixture/tests before edits.
