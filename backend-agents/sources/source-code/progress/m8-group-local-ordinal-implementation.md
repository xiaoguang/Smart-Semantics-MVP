# Progress: M8 group-local shard ordinal implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Repair only M8 preflight uniqueness and deterministic ordering so shard ordinals are scoped to their `processEvidenceGroupId`.
- Approved inputs: `AGENTS.md`; Step 06 §6.7.2.2; Luna RED progress/test; `BusinessProcessInterpretationExecutionPublisher`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Read the frozen M8 group-local ordinal contract and its direct Luna RED.
- Identified the exact defect: preflight retains `Set<Integer> shardOrdinals` globally and both preflight and aggregate sort without group identity.
- Replaced the global ordinal set with the exact group-local key, while retaining global unique `taskShardId` validation.
- Added `processEvidenceGroupId` to each checkpoint terminal and made M8 execution, checkpoint, and aggregate ordering exactly `(processEvidenceGroupId UTF-8, shardOrdinal numeric, taskShardId UTF-8)`.
- Added fresh-reopen verification that every terminal retains its source group identity.

## Current state

- The direct Luna behavior is green. Two independent groups can each retain ordinal zero as `NO_MODEL` shards with zero Provider calls.

## Changed files

- `progress/m8-group-local-ordinal-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisher.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationGroupLocalOrdinalTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisher.java` | PASS | BUILD SUCCESS. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Require unique `taskShardId` globally and unique `(processEvidenceGroupId, shardOrdinal)` pairs.
- Order execution/checkpoint shards by `(processEvidenceGroupId UTF-8, shardOrdinal numeric, taskShardId UTF-8)`.
- Do not change Provider invocation, NO_MODEL terminal behavior, packets, P1/P2, M9, schemas, tests, or fixtures.

## Blockers

- None.

## Exact next action

- Return the direct selector result to the coordinating Agent; do not start another M8 behavior.

## Resume checks

- Re-read this progress file and the Luna RED before editing.
- Confirm only this progress file and the M8 publisher are modified by this task.
- Do not invoke a live Provider, network source, customer build, or customer Maven.
