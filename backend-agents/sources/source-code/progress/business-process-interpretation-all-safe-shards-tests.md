# Progress: M8 all-safe-shards execution RED

- Status: COMPLETE
- Agent role: Luna/xhigh public-seam test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add the single frozen RED for two MODEL_SAFE shards plus one NO_MODEL shard in one M8 aggregate execution.
- Approved inputs: `progress/m8-all-safe-shards-design.md`, Step 06 §§6.4–7.2, current M7/M8 production seams and existing public fixtures.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Added exactly one public-seam test method:
  `BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime`.
- Built a fresh M7 fixture publication with three canonically derived shard IDs, unique ordinals
  `[0, 1, 2]`, and disjoint owners: two `MODEL_SAFE` shards and one `NO_MODEL` shard. The second
  safe packet has a packet-local `Q01` limitation bound to an existing upstream Gap; no stale
  packet or shard identity is reused.
- Corrected the only fixture defect found during review: the original compiled safe shard could
  carry ordinal `2`, so the test now reconstructs all three intended shards with the unique
  deterministic ordinal sequence `[0, 1, 2]` before publishing M7.
- Scripted Provider assertions require exactly the ordered calls `safe-1 P1`, `safe-1 P2`,
  `safe-2 P1`; each call sees only its own dry packet, with no prior P2 keys or forbidden
  technical material. The second safe P1 returns a typed `P1_GAP`, so P2 is not called.
- Assertions cover `A=3/S=2/I=1/H=1/Q=1`, four planned tasks, three rounds/receipts, four
  task dispositions, three process dispositions, three Provider calls, one ready terminal, one
  Gap terminal, one no-model terminal, the closed-with-gaps outcome, the exact Gap union, and
  fresh reopen of the aggregate publication.

## Current state

The existing M8 execution publisher covers one model-safe shard plus no-model terminals. The requested RED must exercise an identity-consistent M7 publication with `A=3`, `S=2`, `I=1`, and verify sequential packet isolation and aggregate conservation.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` (planned single test method only)
- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime test` | EXPECTED RED | Initial fixture identity defect found: ordinals were `[2, 1, 2]`; corrected to `[0, 1, 2]`, then rerun. The corrected test now fails only at the current M8 finite-cardinality rejection (`BusinessProcessInterpretationExecutionPublisher` preflight still requires exactly one `MODEL_SAFE` shard), with provider calls still at 0. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` | PASS | Target test source formatted. |
| `git diff --check` | PASS | No whitespace errors in tracked worktree changes. |

## Decisions

- No production, design, Schema, or fixture changes.
- Use only the scripted Provider and one targeted Maven selector.
- The first RED must be an M8 finite-cardinality/aggregate invariant, not fixture or compilation corruption.

## Blockers

- Production M8 aggregate execution still hard-codes one safe shard and the old aggregate
  accounting. Terra must generalize it under `progress/m8-all-safe-shards-design.md`; this is
  the intended RED, not a fixture or test-compilation blocker.

## Exact next action

Terra/xhigh implements only the finite-shard M8 orchestration, then reruns the exact selector.

## Resume checks

- Preserve all unrelated shared-worktree edits.
- Do not mutate a packet, shard identity, or M7 publication while retaining stale identity fields.
- Do not invoke network, real Provider, customer source, or customer Maven.
