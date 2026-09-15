# Progress: business lifecycle consolidation tests

- Status: COMPLETE (RED handoff)
- Agent role: Task 3 follow-up RED/regression tests for lossless repository consolidation
- Model: gpt-5
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Add focused BusinessProcessDiscovery consolidation RED/regression tests and this progress record only.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md`, current `BusinessProcessDiscoveryTest`, current `DefaultBusinessProcessDiscovery`, baseline through `c572c16`.
- Current branch/worktree: current `linguan-prototype-v2` branch; preserve unrelated untracked `docs/research/`.

## Completed

- Read repository, backend, and source-code scoped instructions.
- Read the approved consolidation design section.
- Inspected the current discovery implementation and scripted fixture.
- Added candidate-based normalization and additive-detail consolidation coverage.

## Current state

- Existing production rejects the additive source-only `MERGE_INTO` with `PROCESS_CONSOLIDATION_MERGE_NOT_LOSSLESS`; follow-up production must union those details without dropping them.
- The fixture's candidate-based identical merge emits two equivalent candidate processes with distinct global ActivityUse IDs before consolidation; the normalization guard passes, while the additive union guard is the intended RED.

## Changed files

- `backend-agents/sources/source-code/progress/business-lifecycle-consolidation-tests.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java spotless:apply` | PASS | Focused test source formatted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest test` | RED | 25 tests ran; 0 failures, 1 error, 0 skipped. The sole intended error is `mergesCandidateProcessesWithSourceOnlyDetailsAndPreservesTheirStableUnion`, rejected by current production as `PROCESS_CONSOLIDATION_MERGE_NOT_LOSSLESS`; candidate global-use-ID normalization and KEEP+RELATED guards pass. |

## Decisions

- Extend `ScriptedProvider` with small named scenarios for two processes from distinct catalog candidates, global-use-ID normalization, and additive source-only details (participant/object/use refs/rule/knowledge/pending/source refs).
- Keep assertions at the `BusinessProcessDiscovery` seam; do not modify production, prompts, publisher, or unrelated fixtures.

## Blockers

- None.

## Exact next action

- Commit only `BusinessProcessDiscoveryTest.java` and this progress file; hand the RED to Terra/xhigh for the additive-union implementation.

## Resume checks

- Run `git status --short`; confirm only this progress file and the focused test file are changed/untracked (apart from pre-existing unrelated work).
