# Progress: M8 all-safe-shards execution implementation

- Status: BLOCKED
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Generalize the existing M8 aggregate publisher from one `MODEL_SAFE` shard to every finite M7 shard denominator, exactly as frozen in `progress/m8-all-safe-shards-design.md`.
- Approved inputs: Scoped `AGENTS.md`, both implementation plans, Step 06 design, the Sol/ultra M8 all-safe-shards design, and the single Luna/xhigh RED.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the frozen M8 cardinality contract, the single public RED, existing M7/M8 runners and projectors, and current shared-worktree status.
- Confirmed the intended RED is caused by `BusinessProcessInterpretationExecutionPublisher` requiring exactly one model-safe shard.

## Current state

- Production M8 has the correct fixed module address, bounded dry packet boundary, runner validation, one final receipt-last installation, and single-safe terminals.
- It currently hard-codes one safe shard, `CLOSED_WITH_GAPS`, `SUCCEEDED_WITH_GAPS`, two Provider calls, and the `A=3/S=1/I=2` accounting shape.
- A bounded generalized implementation is present locally but cannot be validated until the owned RED fixture has unique shard ordinals. The test currently constructs `A=3` ordinals `[2, 1, 2]`; the frozen contract requires unique ordinal values before any Provider call.
- Luna corrected the owned fixture to canonical unique ordinals `0, 1, 2`; the intended finite-cardinality RED is restored.
- The corrected fixture then exposed a second contract mismatch: its `NO_MODEL` shard references capsule-origin Gap IDs absent from M7 `processGaps`; the frozen all-shards preflight correctly rejects that non-resolvable Gap before any Provider call.

## Changed files

- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Luna selector from the owned RED progress | EXPECTED RED | One test compiled and failed at the current finite-cardinality preflight before Provider invocation. |
| Generalized-selector diagnostic run | BLOCKED (resolved) | The new preflight correctly rejected duplicate shard ordinal `2` before Provider invocation; temporary diagnostics were removed after confirming the fixture defect. |
| Corrected-selector diagnostic run | BLOCKED | The unique-ordinal fixture now reaches the required `NO_MODEL` Gap-resolution preflight and fails because M7 did not publish the referenced capsule Gap in `processGaps`; temporary stack instrumentation was removed after identifying line 202. |

## Decisions

- Reuse the existing strict `P1SuccessTerminal` and `P1GapTerminal` projection logic; generalize orchestration and aggregate accounting only.
- Do not persist per-packet results or change M6/M7 packets, model grammar, Schema, or the public output count.

## Blockers

- M7's current compiler allows a `NO_MODEL` shard to reference a capsule-origin Gap while omitting that Gap from its `processGaps` output. The frozen M8 contract requires a resolvable M7 Gap. This requires Sol/parent choice: correct M7's projection or build a complete identity-consistent M7 fixture; M8 must not weaken the fail-closed preflight.

## Exact next action

- Apply the Sol/parent decision on M7 Gap resolvability, then rerun the direct selector, finish any genuine M8 failure, and run the stated M8 regressions and scoped formatting checks.

## Resume checks

- Preserve unrelated shared-worktree edits.
- Do not invoke a real Provider, network, customer Maven, or customer source capture.
