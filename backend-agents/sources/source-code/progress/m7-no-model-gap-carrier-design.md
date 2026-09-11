# Progress: M7 no-model upstream Gap carrier design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Resolve only the M7→M8 contract break where a `NO_MODEL` shard names an upstream Capsule/Flow Gap that is absent from M7 `processGaps`.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, both implementation plans, Step 06 M7/M8 contracts, current M7 compiler/publisher, current M8 aggregate publisher, and the all-safe-shards RED/progress. Documentation in this progress file only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, target Step 06 wire, current M6 value-bearing carrier, M7 partition/publication, and M8 full-denominator preflight.
- Confirmed the break is real and upstream-owned: M6 persists each Flow's complete `evidenceCapsule.gapViews`; M7 copies `modelIneligibilityGapIds` into `NO_MODEL` shards but only creates `ProcessInterpretationGapV1` values for M7 budget overflow; M8 correctly rejects every `NO_MODEL` ID that cannot be reopened from M7 `processGaps`.

## Current state

- The valid mixed fixture reaches M8 with `A=3`, `S=2`, `I=1`, then fails before any Provider call because the remaining `NO_MODEL` shard references a real Capsule-origin Gap with no M7 typed carrier.
- Weakening M8 to accept an unresolved ID would lose the exact reason/provenance needed by Step 07 and Trace. Re-labeling a Capsule Gap as a task-budget Gap would invent facts. The fix therefore belongs in M7 projection/publication.

## Changed files

- `progress/m7-no-model-gap-carrier-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only source/design inspection | PASS | M6 contains complete Capsule `gapViews`; M7 `upstreamGaps(...)` returns IDs only; M7 `processGaps` currently contains only `PROCESS_TASK_BUDGET_EXCEEDED`; M8 requires each no-model ID to resolve in that collection. |
| `git diff --check -- progress/m7-no-model-gap-carrier-design.md` | PASS | No whitespace errors; this task owns only the new progress file. |

## Decisions

### Frozen local contract

This is one M7 projection repair. It neither weakens M8 nor changes a Provider request. M7 must
turn every `(NO_MODEL shard, upstream ineligibility Gap)` occurrence into one M7-owned,
content-addressed `ProcessInterpretationGapV1`. The shard then names those M7 process-Gap IDs;
the process Gap embeds the exact upstream value so the original identity and provenance remain
recoverable without reopening source code or interpreting prose.

The exact `ProcessInterpretationGapV1` shape for the currently unaccepted internal M7 wire is:

```text
ProcessInterpretationGapV1
  gapId!
  gapCode!: PROCESS_TASK_BUDGET_EXCEEDED |
            PROCESS_UPSTREAM_MODEL_INELIGIBLE
  gapScope!=PROCESS_TASK_SHARD
  processEvidenceGroupId!
  candidateRelationIds[]!
  affectedFlowSliceIds[]!
  limitKind?                            // existing budget branch only
  configuredLimit?                     // existing budget branch only
  observedValue?                       // existing budget branch only
  messageKey!
  taskShardId!
  upstreamGap?: UpstreamFlowGapProjectionV1

UpstreamFlowGapProjectionV1
  sourceFlowSliceIds[]!                 // nonempty, UTF-8 sorted, unique
  gapView!: exact deep copy of the M6 persisted EvidenceCapsule gapViews record

FlowGapView (the nested `gapView` exact fields)
  gapId!
  scope!
  reasonCode!
  affectedSemanticIds[]!
  evidenceRefs[]!
  originKind!
  originGapLedgerRef?                   // required JSON field; value may be null
```

Required-nullable rules are closed:

- `PROCESS_TASK_BUDGET_EXCEEDED` keeps its current values, requires all three limit fields,
  requires `upstreamGap=null`, and keeps `messageKey=process-task-budget-exceeded`.
- `PROCESS_UPSTREAM_MODEL_INELIGIBLE` requires all three limit fields to be null, requires one
  non-null `upstreamGap`, and fixes
  `messageKey=process-upstream-model-ineligible`.
- No other `gapCode`, `gapScope`, field combination, or unknown nested field is accepted.
- Java uses boxed `Integer` for the three nullable numeric fields. A sentinel zero is forbidden.

### Exact projection algorithm

For each atomic unit that M7 classifies `NO_MODEL` because one or more context Capsules are
`INELIGIBLE`:

1. For every ineligible context Flow, read its exact
   `evidenceCapsule.modelIneligibilityGapIds[]` from the fresh-reopened M6 material.
2. Resolve each named ID to exactly one `evidenceCapsule.gapViews[]` value in that same Flow.
   Missing, duplicated, blank, or non-object values are
   `PROCESS_MODEL_REFERENCE_INVALID`.
3. If the same upstream Gap ID occurs in more than one context Flow, its complete `gapView`
   bytes must be identical. Merge only its `sourceFlowSliceIds`; conflicting bytes fail closed.
4. For each unique upstream Gap ID in that shard, create one wrapper whose:
   - `processEvidenceGroupId` equals the owning group;
   - `candidateRelationIds` equals the shard's exact owner-relation set;
   - `affectedFlowSliceIds` equals the shard's exact context-Flow set because the whole shard is
     model-ineligible;
   - `upstreamGap.sourceFlowSliceIds` is the exact subset of context Flows whose Capsule names
     that upstream ID;
   - `upstreamGap.gapView` is the byte-equivalent deep projection of the one verified source
     record; no label, reason, evidence reference, origin, or scope is translated.
5. Compute the wrapper ID as:

   ```text
   gap:lowercaseHex(SHA-256(
       frame(UTF8("flow-interpretation-upstream-model-ineligibility-gap-v1")) ||
       frame(canonicalJson(wrapper without gapId and taskShardId))))
   ```

   Excluding only `gapId` and `taskShardId` avoids the owner cycle. The upstream Gap ID remains
   identity-significant inside `upstreamGap.gapView`.
6. Set the `NO_MODEL` shard's `modelIneligibilityGapIds[]` to these wrapper IDs, UTF-8 sorted and
   unique. Do not leave the upstream IDs there: one upstream Flow Gap may make more than one
   relation-owning shard ineligible, so reusing it as the process owner ID would create one ID
   with multiple `taskShardId` values.
7. Append the complete wrappers to `BusinessProcessTaskCompilation.processGaps` together with
   existing task-budget Gaps. Sort the final collection by `gapId` and reject duplicate IDs.
   Across the compilation, every process Gap ID is referenced by exactly one `NO_MODEL` shard
   and every `NO_MODEL` Gap ID resolves to exactly one process Gap.

This mapping makes no claim about the upstream reason. For example,
`CAPSULE_BUDGET_NO_SAFE_SPLIT` stays exactly that reason inside the nested Flow Gap; the wrapper
only states the deterministic fact that this source limitation prevents the owning M7 shard
from being sent to the model.

The M7 module file, artifact type, module address, Provider count, and filename remain unchanged:

```text
process-task-shards.json
FLOW_INTERPRETATION_PROCESS_TASK_SHARDS
business-process-task-compiler
providerCallCount=0
```

This internal v1 slice has not been accepted or published as a stable product wire, so the exact
record is corrected in place; no v1/v2 dual reader or compatibility branch is created. Its
artifact/module identities naturally change because canonical payload bytes change. The fifteen
Step 06 files and 57-run-output denominator do not change.

### One Luna/xhigh RED

Change only the assertions of the existing real failing shape:

```text
BusinessProcessTaskCompilerTest
  #separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner
```

Exact selector:

```text
mvn -t .mvn/toolchains.xml -o \
  -Dtest=BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner \
  test
```

Keep its current three-Flow, one safe plus two no-model shard fixture. In addition to the existing
edge-owner assertions, fresh-reopen M6 and establish the upstream Capsule Gap map. Assert:

- every no-model shard Gap ID resolves to exactly one `result.processGaps` value;
- the union of no-model shard Gap IDs equals the complete process-Gap ID set in this fixture;
- the two relation-owning no-model shards have distinct wrapper IDs even when they originate in
  the same upstream Capsule Gap;
- each wrapper has the exact group, owner relation, context Flow and owning source-Flow sets;
- `gapCode/gapScope/messageKey` equal the frozen constants;
- all limit fields are JSON null;
- `upstreamGap.gapView` is byte-equivalent to the matching M6 Capsule `gapViews` object, including
  its original `gapId`, `scope`, `reasonCode`, `affectedSemanticIds`, `evidenceRefs`, `originKind`
  and required-nullable `originGapLedgerRef`;
- `providerCallCount=0`.

The accurate RED is the current empty/incomplete `processGaps` carrier. Do not fabricate a Gap in
the fixture, weaken M8, or add a second test before this method is GREEN.

### Terra/xhigh GREEN scope

Terra may change only:

- `ProcessInterpretationGapV1` and, if kept as a separate semantic record, one colocated
  `UpstreamFlowGapProjectionV1`;
- `BusinessProcessTaskCompiler` for source-Gap resolution, wrapper creation, ordering and exact
  conservation;
- `BusinessProcessTaskCompilation` only if needed to enforce sorted unique Gap IDs and the
  shard↔Gap bijection at construction;
- `BusinessProcessTaskModulePublisher` only if fresh-reopen validation must enforce that same
  bijection; serialization already follows the compilation and must not gain a parallel mapper.

Terra must not change M6, Flow/Capsule public wire, M8 Provider behavior, model packet contents,
artifact/file counts, runtime recovery, public Interfaces, or any response grammar. The complete
upstream `gapView` already exists in M6; re-open and copy it rather than reconstructing it from an
ID or message.

After the one method is GREEN, run only these regressions before M8 resumes:

```text
BusinessProcessTaskCompilerTest
BusinessProcessTaskModulePublisherTest
```

The existing M8 all-safe-shards RED does not change its Provider requests, expected call order,
or cardinality. It must rebuild its M7 fixture from the corrected compilation so its no-model
Gap IDs are wrapper IDs present in `processGaps`; it must not retain a stale pre-repair task shard
or manually substitute an upstream Gap ID. Its current receipt/no-model expectations can continue
to use `shard.modelIneligibilityGapIds`; add only one assertion that each embedded no-model
`processGaps` value preserves the original nested upstream `gapId`. Then resume the M8 selector.

### Why the rejected alternatives are wrong

- **Let M8 accept an ID with no value:** loses the reason/provenance and makes Step 07/Trace guess.
- **Put the raw upstream ID directly in multiple process Gaps:** the same upstream Gap can make
  multiple owner shards ineligible, producing duplicate IDs with different owners.
- **Rename every source reason to `PROCESS_TASK_BUDGET_EXCEEDED`:** invents an M7 budget fact and
  destroys the original scope/origin.
- **Rescan Step 05 or source code in M8:** reverses the M7→M8 seam and duplicates projection logic.
- **Add a sixteenth Step 06 file:** unnecessary; M7's existing internal payload is the correct
  carrier.

## Blockers

- None; this is an adjacent-module protocol repair and does not change the eight-step architecture, Provider input, fifteen Step 06 files, or 57-output count.

## Exact next action

- Luna/xhigh updates the one existing mixed-eligibility compiler test and confirms the exact RED; Terra/xhigh then implements only the bounded M7 projection described above, runs the two named regressions, and hands the corrected fresh M7 fixture back to M8.

## Resume checks

- Preserve all unrelated shared-worktree edits.
- Do not modify production, tests, Schema files, Maven, Provider behavior, or Git in this design task.
- Do not weaken M8's full-denominator preflight.
