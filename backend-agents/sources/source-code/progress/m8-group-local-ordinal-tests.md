# Progress: M8 group-local shard ordinals RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: One public-seam M8 aggregate test for two independent process groups, each with shard ordinal 0.
- Approved inputs: Step 06 §6.7.2.2; current M6/M7/M8 public classes and frozen test fixtures.
- Current branch/worktree: codex/source-analysis-business-flows-closeout

## Completed

- Added one public-seam test: `BusinessProcessInterpretationGroupLocalOrdinalTest.acceptsGroupLocalZeroOrdinalsAcrossTwoIndependentGroups`.
- The test constructs two independent M6 groups from the real two-flow fixture, publishes M7, fresh-reopens the M7 publication through the aggregate M8 publisher, and requires both group-local ordinal-0 NO_MODEL shards to be retained with zero Provider calls.
- The test setup reached two distinct NO_MODEL shards with `shardOrdinal=0` in each group; the current M8 preflight rejects the second ordinal as globally duplicated.

## Current state

The RED test is written. No production, design, schema, or fixture files are in scope.

## Changed files

- This progress file only.
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationGroupLocalOrdinalTest.java`

## Verification

| Command | Result | Key output |
| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationGroupLocalOrdinalTest test` | RED (expected) | 1 test, 0 failures, 1 error: `BusinessProcessInterpretationException: PROCESS_MODEL_REFERENCE_INVALID` at `BusinessProcessInterpretationExecutionPublisher.preflight` line 180, the global ordinal uniqueness guard. |

## Decisions

- The test will use two fresh-reopened M7 `NO_MODEL` shards from distinct M6 `processEvidenceGroupId` values; both shard ordinals must be zero.
- The Provider must receive zero calls; the assertion is on M8 aggregate acceptance, complete shard retention, and `(groupId, ordinal, shardId)` ordering.
- No production fallback or fixture mutation will be added to make the RED pass.

## Blockers

## Exact next action

Terra should update only M8 preflight ordering/uniqueness to use `(processEvidenceGroupId, shardOrdinal, taskShardId)`, then rerun this exact selector before any aggregate selector.

## Resume checks

- Read this file before continuing.
- Confirm only the test and this progress file are owned by this task.
- Confirm no live Provider, network, customer source, or customer Maven is used.
