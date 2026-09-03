# Progress: Fact candidate strict publisher test migration

- Status: COMPLETE
- Agent role: Luna/xhigh test migration agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Migrate the two M1 module-publication tests to the frozen three-argument publisher seam.
- Approved inputs: Scoped AGENTS.md; `docs/analysis-steps/04-proven-code-facts.md`; current M1 module artifact/reader tests; `FactCandidateExactUpstreamTest` as contract reference.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Updated `FactCandidateModuleArtifactTest` and `FactCandidateModuleReaderTest` to reflectively require
  exactly `publish(AnalysisStepModuleAddress, FactCandidateInputs, FactCandidateSet)`.
- Removed the tests' free upstream-list and controls construction. The publication test now compares
  persisted upstream references with the typed `FactCandidateInputs` accessor; the reader tamper path
  still mutates and reopens through the low-level canonical module store.

## Current state

Both tests use the strict three-argument publisher contract and preserve their original publication,
fresh-reopen, and persisted-payload-tamper semantics.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java`
- `progress/fact-candidate-strict-publisher-test-migration.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest test` | PASS | 2 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java spotless:check` | PASS | BUILD SUCCESS |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

## Blockers

## Exact next action

No further action in this bounded migration. Terra may continue with the already-frozen production
strict publisher implementation and its own progress record.

## Resume checks

Read this progress file and verify the two named tests still resolve the strict three-argument
publisher before any later M1 changes.
