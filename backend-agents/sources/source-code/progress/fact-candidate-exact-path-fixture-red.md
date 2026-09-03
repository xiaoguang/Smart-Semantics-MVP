# Progress: fact candidate exact path fixture RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add only a test-side graph-specific persisted two-entry/two-boundary fixture and one Fact M1 public-seam RED test. No production, POM, design, existing-test, or existing-progress changes.
- Approved inputs: Sol/ultra local decision; Step 03 public M6 wire at origin/main; Step 04 exact candidate join contract.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Preserved the earlier blocker at `progress/fact-candidate-exact-path-red.md`; this work unit uses a new progress file as directed.
- Verified the fixture must exercise the current M1–M6 builders and canonical stores, not hand-written ProgramGraphs JSON or draft inputs.

## Current state

- Added a graph-specific `ProgramGraphsPublicFixture` test helper. It exposes only typed persisted references, a verified source reader, and the typed step store needed by the future Fact reader; it internally uses canonical stores and `ProgramGraphsExecution`.
- `ProgramGraphsPublicFixture` now contains real source, discovery publication, and graph execution setup; the Fact test has been added against the typed Fact M1 contract.
- Added `FactCandidateExactPathTest`, targeting the formal typed `FactCandidateInputs` + `FactRegistry` + `PersistedFactCandidateInputReader` seam. It asserts two distinct entry-owned Java boundary candidates, no entry/boundary cross-product, unique boundary subjects, and an explicit empty scoped-disposition result for the unmutated positive fixture.

## Changed files

- progress/fact-candidate-exact-path-fixture-red.md
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateExactPathTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Pre-existing Fact M1 work and progress are present; no unrelated changes were made |
| `apply_patch` | PASS | Added only the authorized graph fixture and Fact test files |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest test` | RED (expected) | testCompile reached 56 test sources; six errors are the absent formal `FactCandidateInputs`, `PersistedFactCandidateInputReader`, `FactRegistry`, `FactCandidateSet.notApplicableDispositions()`, and `FactCandidate.boundaryNodeId()` seam/API |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- The helper is test-only and graph-specific; no production public API or artifact wire is extended.
- The test targets typed persisted inputs and the formal two-argument `enumerate(FactCandidateInputs, FactRegistry)` seam. Its current RED is the absent production seam and the current candidates-only `FactCandidateSet`; it does not use the obsolete raw-JSON enumerator.
- The test checks Java boundary invocation closure only; no SQL or external effect is inferred.
- A non-vacuous missing-relation mutation assertion is deliberately deferred: the current public ProgramGraphs fixture API has no authorized persisted graph mutation constructor, and creating one by editing public graph JSON would violate the M1 contract. The next Fact RED must add a real canonical mutation publication and assert one scoped `NOT_APPLICABLE` disposition without changing the other entry.

## Blockers

- The positive graph fixture was authored through the current ProgramGraphs execution/publication path. Because the Fact seam is absent, Maven stopped at testCompile and could not execute the fixture; Terra must rerun this selector after implementing the typed reader/registry/candidate wire and diagnose any graph-builder limitation then.

## Exact next action

- Hand off the typed positive RED. Terra must implement the formal Fact M1 seam, rerun this exact selector, and then add the bounded canonical mutation case before declaring M1 complete.

## Resume checks

- Read this file first; verify only this file, the new graph fixture, and the new Fact test are owned by this task. Check Maven process exclusivity before running the selector.
