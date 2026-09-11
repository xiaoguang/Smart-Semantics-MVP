# Progress: M7 model-safe limitation Gap conservation ruling

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Resolve only the M7 construction invariant that currently treats every persisted process Gap as exclusively owned by a `NO_MODEL` shard, even when a `MODEL_SAFE` packet has a packet-local `LIMITATION` binding to an existing M7 Gap.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, Step 06 M7/M8 contracts, the M7 no-model Gap-carrier design and implementation, the M8 all-safe-shards design/RED, current M7/M8 production seams, and the exact constructor failure. Design ruling only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, current M7 compiler/records/publisher, M8 runner/execution publisher, the M7 no-model wrapper repair, the multi-safe M8 contract, and the exact failing selector evidence.
- Reproduced the contract collision from source: `BusinessProcessTaskCompilation` currently requires `processGaps` to equal the IDs uniquely referenced by `NO_MODEL.modelIneligibilityGapIds`, while M8's valid P1 GAP language requires a `MODEL_SAFE` `LIMITATION` binding to resolve to an existing M7 process Gap.
- Froze the two-lane conservation law, exact model-safe limitation carrier, allowed origin and identity rules, fail-closed cases, one Luna RED, minimal Terra scope, and direct rerun selectors.

## Current state

- The M8 all-safe fixture reaches construction of `A=3, S=2, I=1` material, but M7 rejects it before M8 because one existing process Gap is referenced only through a `MODEL_SAFE` `LIMITATION` binding and is therefore absent from the current no-model ownership set.
- Provider calls remain zero. The failure is an adjacent M7 conservation invariant, not an M8 orchestration failure.

## Changed files

- `progress/m7-safe-limitation-gap-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only source/design inspection | PASS | Confirmed that the constructor scans only `NO_MODEL.modelIneligibilityGapIds`; M7 dry packets already carry `LIMITATION` reader bindings; M8 maps P1 `gapKeys` through those bindings and requires their IDs in M7 `processGaps`. |

## Decisions

### Local ruling: ownership and read-only reference are different relations

M7 keeps one canonical `processGaps` collection, but its conservation law has two disjoint
lanes. Let:

```text
P = every unique processGaps[].gapId
O = every unique NO_MODEL.modelIneligibilityGapIds[] value
L = every unique process-Gap ID referenced by a MODEL_SAFE packet's
    one-to-one LIMITATION reader binding

P = O ⊎ L
O ∩ L = ∅
```

`O` is ownership: the Gap prevents that shard from entering the model. `L` is a read-only
limitation carrier: the shard remains model-safe, but its dry packet tells the model that a
specific upstream uncertainty exists. A `MODEL_SAFE` shard therefore keeps
`modelIneligibilityGapIds=[]`; merely having an `L` value never changes its eligibility.

No third lane is permitted. A Gap value that is neither owned through `O` nor referenced through
`L` is orphaned and construction fails. A Gap ID in both lanes, in two no-model owners, in two
model-safe packets, or in two LIMITATION bindings fails construction. `processGaps` itself remains
sorted and ID-unique.

### NO_MODEL ownership remains exactly as repaired

For every `gapId` in a `NO_MODEL` shard's `modelIneligibilityGapIds`:

- exactly one complete `ProcessInterpretationGapV1` with that ID exists in `processGaps`;
- `gap.taskShardId` equals that no-model shard's `taskShardId`;
- the value is either the existing `PROCESS_TASK_BUDGET_EXCEEDED` Gap or the existing
  `PROCESS_UPSTREAM_MODEL_INELIGIBLE` wrapper;
- no `MODEL_SAFE` reader binding may reference that wrapper ID;
- the complete union of this lane remains owned exactly once, as in the accepted M7 no-model
  carrier repair.

This ruling does not weaken no-model ownership and does not permit M8 to accept a bare upstream
Gap ID.

### MODEL_SAFE limitation carrier

M7 is the sole module allowed to create the `L` carrier. For every unique upstream `gapView`
included as a limitation in one model-safe shard, M7 creates exactly one
`ProcessInterpretationGapV1` with this frozen matrix:

```text
gapCode                 = PROCESS_UPSTREAM_LIMITATION
gapScope                = PROCESS_TASK_SHARD
processEvidenceGroupId  = owning model-safe shard group
candidateRelationIds    = owning model-safe shard owner relation IDs
affectedFlowSliceIds    = exact sorted context Flow subset whose Capsule carries the gapView
limitKind               = null
configuredLimit         = null
observedValue           = null
messageKey              = process-upstream-limitation
taskShardId             = owning model-safe shard ID
upstreamGap              = complete byte-equivalent M6 upstream Gap projection
```

This is a deterministic carrier projection, not a newly inferred business or source Gap. Its
`upstreamGap.gapView` must match exactly one Gap value already persisted in M6 material for a
Flow in the same shard's `contextFlowSliceIds`. The upstream `gapId`, `scope`, `reasonCode`,
`affectedSemanticIds`, `evidenceRefs`, `originKind`, and nullable `originGapLedgerRef` are copied
unchanged. Any upstream origin already accepted by that frozen `FlowGapViewV1` is allowed; a model
response, another M7 process Gap, a fixture-only string, a foreign group/Flow, or a reconstructed
summary is not an allowed origin.

The carrier ID uses the existing canonical process-Gap content identity over the complete value
excluding only `gapId`. Because the identity includes `taskShardId`, group, relation/context and
the complete upstream projection, the same upstream Gap used in a different shard gets a distinct
carrier without ID collision.

For each carrier there is exactly one `ProcessModelPacketV1.limitations[]` record and exactly one
`ReaderKeyBindingV1` satisfying:

```text
binding.readerKey            = limitation.limitationKey
binding.keyKind              = LIMITATION
binding.internalReferenceIds = [carrierGapId]
```

The packet key, limitation record and binding form a one-to-one triple. Duplicate upstream Gap
occurrences inside the same shard are normalized to one carrier/limitation/binding and the exact
affected Flow keys are unioned and sorted. A LIMITATION binding with zero or multiple internal
references, a packet limitation without a binding, a binding without a packet limitation, a
reference to an `O` wrapper, or a carrier whose `taskShardId`/group/context/origin does not match
the shard fails before any Provider call.

The Provider still sees only the packet-local limitation key and human-readable bounded summary.
It never sees the carrier ID, upstream Gap ID, origin ledger reference, hash, receipt or lineage.
If P1 returns that local key as `P1_GAP`, M8 maps it back to this already persisted carrier ID; M8
does not synthesize, duplicate, upgrade or infer a Gap.

### Why the current M8 test fixture is not a valid carrier

The current helper converts a no-model shard into a second safe shard but leaves the former
`PROCESS_UPSTREAM_MODEL_INELIGIBLE` wrapper unchanged. That wrapper still names the removed
no-model `taskShardId` and originates outside the new safe packet's exact context. Loosening the
constructor to accept it would preserve a dangling owner and permit cross-packet limitation
contamination. The fixture must instead consume an M7-created
`PROCESS_UPSTREAM_LIMITATION` carrier whose task shard, group, context and M6 origin all match the
second safe packet. This is a test-input correction after the M7 RED is GREEN, not an M8 behavior
change.

### Preserved public and model contracts

- M7 remains module 7, zero Provider calls, one `process-task-shards.json` payload and one
  receipt-last publication.
- M8 retains the same dry Provider request and P1/P2 response grammar.
- No sixteenth Step 06 file is added; Step 06 remains fifteen files and the full run remains 57
  outputs.
- No runtime recovery, retry, Provider fallback, new public Interface or cross-step artifact is
  introduced.
- The internal M7 v1 wire is corrected in place because it is still WIP and unpublished; no dual
  reader, alias or compatibility branch is allowed.

### One Luna/xhigh RED

Add exactly one behavior to `BusinessProcessTaskCompilerTest`:

```text
conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately
```

Use the existing three-Flow mixed-eligibility compiler fixture, but obtain limitation material
only from the freshly reopened M6 same-context Capsule `gapViews`; do not manufacture a Gap ID or
reuse a no-model wrapper. Assert in one test that:

- at least one `MODEL_SAFE` shard has a packet limitation while its
  `modelIneligibilityGapIds` remains empty;
- that limitation has one `LIMITATION` binding to one `PROCESS_UPSTREAM_LIMITATION` carrier;
- the carrier names the same safe shard/group/relation/context and preserves the exact upstream
  M6 Gap value;
- each `NO_MODEL` wrapper is still uniquely owned by its no-model shard;
- `P = O ⊎ L`, with no duplicate/orphan ID and `providerCallCount=0`.

Exact selector:

```text
mvn -t .mvn/toolchains.xml -o \
  -Dtest=BusinessProcessTaskCompilerTest#conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately \
  test
```

The expected RED is that current M7 binds the packet limitation directly to an upstream Gap ID
and does not persist a corresponding safe limitation carrier; it must not be fixture corruption,
an M8 call, or a broad aggregate failure.

### Minimal Terra/xhigh GREEN scope

Terra may change only:

- `ProcessInterpretationGapV1` to add the exact `PROCESS_UPSTREAM_LIMITATION` field matrix;
- `BusinessProcessTaskCompiler` to deduplicate same-shard upstream limitations, create the
  canonical carrier and bind the local key to its carrier ID;
- `BusinessProcessTaskCompilation` to enforce the exact disjoint `P = O ⊎ L` ownership/reference
  law and shard-local origin checks available in its value graph;
- `BusinessProcessTaskModulePublisher` only if its fresh-reopen/recompute validation needs the
  same law; it must not implement a second serializer or mapper.

No M6, M8, packet-visible field, model response, public Schema file, Provider adapter, output
count, runtime or recovery change is permitted in this repair.

After the exact RED is GREEN, run only:

```text
BusinessProcessTaskCompilerTest
BusinessProcessTaskModulePublisherTest
```

Then Luna corrects the M8 all-safe fixture to use the compiler-produced carrier and reruns only:

```text
BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime
```

If that selector then enters M8 and fails on finite-cardinality execution, Terra resumes the
already frozen M8 implementation. It must not weaken this M7 carrier law.

## Blockers

- None. This is a local M7 construction correction that preserves the eight-step architecture, Provider boundary, fifteen Step 06 files, and 57-output denominator.

## Exact next action

- Luna/xhigh establishes the one M7 compiler RED above. Terra/xhigh implements only the frozen
  two-lane conservation and carrier projection, runs the two M7 regressions, and returns the
  compiler-produced safe limitation carrier to the existing M8 all-safe selector.

## Resume checks

- Modify no file other than this progress record.
- Do not run Maven or any Provider.
- Preserve every unrelated shared-worktree edit.
