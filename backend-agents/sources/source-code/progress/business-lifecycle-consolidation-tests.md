# Progress: business lifecycle consolidation tests

- Status: COMPLETE (RED handoff)
- Agent role: Task 3 RED tests for lossless repository consolidation
- Model: gpt-5
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Add focused BusinessProcessDiscovery consolidation RED tests and this progress record only.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md`, current `BusinessProcessDiscoveryTest`, current `DefaultBusinessProcessDiscovery`, baseline through `97a1c81`.
- Current branch/worktree: current `linguan-prototype-v2` branch; preserve unrelated untracked `docs/research/`.

## Completed

- Read repository, backend, and source-code scoped instructions.
- Read the approved consolidation design section.
- Inspected the current discovery implementation and scripted fixture.

## Current state

- Consolidation currently applies model `MERGE_INTO` decisions without comparing complete ordered stage business fields.
- Existing fixture emits one reviewed process and defaults consolidation to `KEEP`; new variants must make two reviewed processes visible to consolidation.

## Changed files

- `backend-agents/sources/source-code/progress/business-lifecycle-consolidation-tests.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest test` | RED | 24 tests, exactly 1 intended failure, 0 errors/skips: a model-requested merge whose stage narratives differ is currently accepted. The identical-sequence normalization and separate-related-process regression guards pass. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java spotless:apply` | PASS | Focused test source formatted. |

## Decisions

- Extend `ScriptedProvider` with small named scenarios for two reviewed processes and local-use-ID normalization.
- Keep assertions at the `BusinessProcessDiscovery` seam; do not modify production, prompts, publisher, or unrelated fixtures.

## Blockers

- None.

## Exact next action

- Terra/xhigh should reject `MERGE_INTO` unless the two normalized ordered stage sequences are byte-for-byte equal across all business fields; then rerun the focused selector.

## Resume checks

- Run `git status --short`; confirm only this progress file and the focused test file are changed/untracked (apart from pre-existing unrelated work).
