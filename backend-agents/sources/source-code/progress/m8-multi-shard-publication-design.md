# Progress: M8 multi-shard process interpretation publication design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze the next smallest M8 contract for deterministically executing and receipt-last publishing a mixed `MODEL_SAFE`/`NO_MODEL` M7 task-shard publication without changing the Step 06 fifteen-file or full-run 57-output contracts.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md` §§6.4–7.2, current M6/M7/M8 progress, and current M7/M8 production/test seams. Documentation only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped Agent rules, the authoritative M8 P1/P2 and fifteen-file wire, current M6/M7/M8 status, and the M7 compiler/publisher plus M8 runner/publisher/tests.
- Confirmed that the existing M7 mixed-eligibility fixture is the smallest honest aggregate: three relation-owner shards, each with exactly two Flow contexts, comprising exactly one `MODEL_SAFE` shard and two `NO_MODEL` shards.
- Froze one aggregate M8 behavior, its internal wire, exact counts, receipt-last identity, terminal/fatal branches, one Luna RED selector, and the bounded Terra scope below.
- Determined that `AGENTS.md` and `docs/DESIGN.md` need no change: this slice implements their existing dry-packet, unique-owner, terminal-disposition, no-retry, fifteen-file, and 57-output rules.
- Corrected the Luna RED receipt oracle after the concrete fixture showed that both `NO_MODEL`
  shards legitimately reference the same upstream Flow Gap. Per-shard dispositions retain that
  shared reference independently; the module receipt carries its sorted unique union once.
- Corrected a second Luna RED identity oracle: `businessProcessTaskPublicationRef` is the frozen
  three-field `ModulePublicationReference`, not an `ArtifactReference`; the test now requires exact
  M7 module root, receipt ID and receipt SHA values and explicitly rejects an invented `artifactId`.

## Design decision

### One new execution publisher

Add one test-visible module seam:

```java
public final class BusinessProcessInterpretationExecutionPublisher {
  public BusinessProcessInterpretationExecutionPublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      ProcessInputArtifactReader inputArtifacts);

  public ModulePublicationReference runAndPublish(
      BusinessProcessInterpretationExecutionRequest request,
      ProcessModelProvider provider);
}

public record BusinessProcessInterpretationExecutionRequest(
    ModulePublicationReference businessProcessTaskPublication,
    ArtifactReference expectedRuntime) {}
```

The request names the complete persisted M7 publication, not one shard. The existing
`BusinessProcessInterpretationModulePublisher.runAndPublish(...)` cannot be called once per shard:
every call owns the same fixed M8 address, so the second different checkpoint would correctly
collide. The new publisher owns the whole M7 denominator and installs one aggregate M8 module.

It reuses the existing runner's strict dry-packet, P1 GAP, P1 hypothesis, P2 KEEP, runtime, subset,
no-expansion, no-retry, and no-fallback behavior. It must not introduce a second response parser. A
small package-private projector may be extracted from the current one-shard publisher so both seams
create byte-equivalent safe-shard task/round/receipt/disposition values.

This slice deliberately accepts exactly one `MODEL_SAFE` shard and at least one `NO_MODEL` shard.
It therefore starts exactly one model packet and the aggregate M8 publication is that packet's
durable checkpoint; there is no later model packet to replay. Inputs with zero or more than one
`MODEL_SAFE` shard fail before any Provider call with `PROCESS_MODEL_SHARD_SET_UNSUPPORTED`.
Arbitrary `S > 1` execution remains a later, separately designed packet-checkpoint behavior and
must not be claimed by this GREEN. This avoids adding a dynamic checkpoint-address or crash-
recovery subsystem merely for this vertical slice.

### Fixture clarification

“two-Flow/three-shard fixture” means **each of the three relation shards has exactly two Flow
contexts**. The repository fixture has three distinct Flows and three distinct candidate relations:

```text
Flow A ---- relation AB ---- Flow B     MODEL_SAFE
Flow B ---- relation BC ---- Flow C     NO_MODEL
Flow A ---- relation AC ---- Flow C     NO_MODEL
```

It cannot mean “only two distinct repository Flows but three relation-owner shards”. M6 emits one
candidate relation per unordered Flow pair and M7 requires every relation to have exactly one
owner; three owner shards for one pair would duplicate ownership or invent ownerless non-singleton
shards.

The concrete setup is the existing
`ProgramGraphsPublicFixture.createWithChainedJavaCalls(...)` path used by
`BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner`:

```text
Capsule safe-byte ceiling = 350
maxFlowsPerShard = 2
A = 3 shards
S = 1 MODEL_SAFE shard
A - S = 2 NO_MODEL shards
owner relation count = 3, each owned exactly once
context Flow count per shard = 2
```

The `MODEL_SAFE` packet takes the already-green P1 hypothesis → P2 KEEP branch. The two
`NO_MODEL` shards retain their exact M7 `modelIneligibilityGapIds`; neither has a packet or reader
bindings, and neither triggers a Provider call. The already-green P1 GAP → planned P2
`NOT_RUN_UPSTREAM_FAILED` selector remains an exact regression contract; this aggregate behavior
must not rewrite or silently convert it into success.

## Exact M8 internal wire

The fixed M8 address remains:

```text
AnalysisStepKey = FLOW_INTERPRETATION
moduleNumber    = 8
moduleKey       = business-process-interpretation-runner
fileName        = process-interpretation-checkpoint.json
moduleVersion   = v1
```

The aggregate payload uses a new internal pair so the existing singular checkpoint v1 is not
silently reinterpreted:

```text
schemaVersion = flow-interpretation-process-interpretation-checkpoint-set-v1
artifactType  = FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT_SET
```

This is an internal M8 module artifact, not a sixteenth Step 06 file, and is excluded from the 57
reader-visible artifact count. The singular
`flow-interpretation-process-interpretation-checkpoint-v1` pair remains only for its two bounded
regression seams; M9 must consume the aggregate set pair, not treat a singular checkpoint as full
M8 completion.

The aggregate payload is exact; extra fields are invalid:

```text
ProcessInterpretationCheckpointSetV1
  businessProcessTaskPublicationRef!: ModulePublicationReference
  shardResults[]!: ProcessInterpretationShardTerminalV1
  accounting!: ProcessInterpretationCheckpointAccountingV1
  executionOutcome!: ALL_READY_FOR_ADMISSION | CLOSED_WITH_GAPS
  providerCallCount!
  closed = true

ProcessInterpretationShardTerminalV1
  taskShardId!
  shardOrdinal!
  shardModelDisposition!: MODEL_SAFE | NO_MODEL
  ownerCandidateRelationIds[]!
  modelIneligibilityGapIds[]!
  processModelTasks[]!
  processModelRounds[]!
  generationReceipts[]!
  taskDispositions[]!
  businessProcessHypotheses[]!
  processInterpretationDisposition!
  providerCallCount!
  closed = true

ProcessInterpretationCheckpointAccountingV1
  taskShardIds[]!
  ownerCandidateRelationIds[]!
  taskShardCount!
  modelSafeShardCount!
  noModelShardCount!
  plannedProcessModelTaskCount!
  actualProcessModelRoundCount!
  generationReceiptCount!
  taskDispositionCount!
  businessProcessHypothesisCount!
  processInterpretationDispositionCount!
  providerCallCount!
  readyForAdmissionCount!
  gapDispositionCount!
  noModelAdmissionPendingCount!
  failedDispositionCount!
```

`shardResults` is ordered by `(shardOrdinal, taskShardId)` and has the exact same IDs, ordinals,
model dispositions and relation-owner sets as M7. `taskShardIds` and
`ownerCandidateRelationIds` are UTF-8 sorted, duplicate-free exact unions. Each M7 relation owner
appears in exactly one shard result.

For the fixture's accepted P1/P2 branch, exact aggregate counts are:

```text
taskShardCount                        = 3
modelSafeShardCount                   = 1
noModelShardCount                     = 2
plannedProcessModelTaskCount          = 2
actualProcessModelRoundCount          = 2
generationReceiptCount                = 2
taskDispositionCount                  = 2
businessProcessHypothesisCount        = 1
processInterpretationDispositionCount = 3
providerCallCount                     = 2
readyForAdmissionCount                = 1
gapDispositionCount                   = 0
noModelAdmissionPendingCount          = 2
failedDispositionCount                = 0
executionOutcome                      = CLOSED_WITH_GAPS
```

The one `MODEL_SAFE` shard result contains the exact existing success checkpoint projection: two
tasks, two rounds, two receipts, two accepted task dispositions, one hypothesis/claim/KEEP review,
one `READY_FOR_ADMISSION` process disposition, zero gaps and two calls. Its P1 and P2 Provider-
visible bytes are only the existing canonical application requests containing the M7
`ProcessModelPacketV1`; program-only IDs, bindings, SHA, evidence/proof records, runtime, controls
and host paths never enter those bytes.

Each `NO_MODEL` shard result is fixed as:

```text
processModelTasks                 = []
processModelRounds                = []
generationReceipts                = []
taskDispositions                  = []
businessProcessHypotheses         = []
providerCallCount                 = 0
processInterpretationDisposition.executionKind = NO_MODEL
processInterpretationDisposition.p1TaskId       = null
processInterpretationDisposition.p2TaskId       = null
processInterpretationDisposition.disposition    = NO_MODEL_ADMISSION_PENDING
all six hypothesis partition arrays              = []
processInterpretationDisposition.gapIds          = exact modelIneligibilityGapIds
processInterpretationDisposition.processGaps     = exact M7-owned ProcessInterpretationGapV1
                                                    values whose gapId is in that set; otherwise []
processInterpretationDisposition.failureRef      = null
processInterpretationDisposition.reasonCode      = PROCESS_MODEL_INELIGIBLE
closed                                            = true
```

No new Gap is synthesized for an upstream model-ineligibility Gap. If M7 already owns a typed
budget/counter Gap value for this shard, M8 carries that exact value without changing a field. This
preserves evidence already present in M6/M7 rather than reparsing source or strengthening a weak
relation. Semantic priority, direction, relation use, support and counter material remain M7
values; M8 only interprets or records terminal non-execution.

The aggregate `executionOutcome` is `CLOSED_WITH_GAPS` whenever any shard is `GAP` or
`NO_MODEL_ADMISSION_PENDING`; `ALL_READY_FOR_ADMISSION` is legal only when every M7 shard is
`MODEL_SAFE` and every process disposition is `READY_FOR_ADMISSION`. The latter cannot occur in
this mixed-only slice. The module receipt is therefore `SUCCEEDED_WITH_GAPS`, with `gapRefs` equal
to the sorted unique union of all three process dispositions' `gapIds`. Successful installation
must never be reported as “all business processes interpreted”.

The union above has set semantics. If two shard dispositions contain the same Gap ID, each
disposition keeps it, but `ModuleReceipt.gapRefs` contains it exactly once. This is required by the
canonical store's strict sorted-string invariant and prevents shard multiplicity from changing the
module-level Gap identity.

If the sole safe shard returns the existing P1 GAP response, its exact 2 task / 1 round / 1 receipt
/ 2 task disposition / 0 hypothesis / 1 call projection and planned P2
`NOT_RUN_UPSTREAM_FAILED` remain unchanged. The aggregate then has three process dispositions,
`gapDispositionCount=1`, `noModelAdmissionPendingCount=2`, `providerCallCount=1` and
`CLOSED_WITH_GAPS`. The existing exact selector protects that branch; the new RED need not
duplicate it.

## Identity, installation and fresh reopen

- The aggregate artifact ID uses the existing framed canonical module-artifact formula with the
  new schema/type pair and full envelope excluding `artifactId`. Traversal order cannot enter the
  preimage because every array has a prescribed order.
- Existing task, round, generation-receipt, claim, hypothesis and review IDs use the already-green
  §7.2 formulas unchanged. `NO_MODEL` creates none of those IDs.
- The M8 module receipt has exactly the M7 payload reference as upstream and inherits exact M7
  controls. No source/evidence/model-runtime material is copied into Provider bytes or added as an
  invented upstream.
- Installation is receipt-last. The publisher then fresh-reopens and verifies address, descriptor
  pair, M7 reference, all accounting equations, every M7 shard and relation owner, each terminal
  matrix, request/response SHA, runtime equality, completion status and exact `gapRefs` union.
  Merely checking array sizes is insufficient.
- Same address plus different aggregate bytes is a store collision and fails closed. The execution
  publisher never calls the singular publisher first because that would occupy the fixed address.

## Execution and failure branches

The execution publisher has two phases:

1. **Program-only preflight:** fresh-reopen M7 and M6/profile lineage; verify M7 is closed with zero
   Provider calls; verify unique/contiguous shards, disjoint-exact relation ownership, exactly one
   safe shard plus at least one no-model shard, the safe DRY packet/bindings, every no-model null
   packet/empty bindings/nonempty ineligibility gaps, and expected runtime. Failure occurs before a
   Provider call and installs no M8 result.
2. **One packet execution and terminal assembly:** execute the sole safe packet through the existing
   P1/P2 logic, construct no-model terminal results without calls, then atomically install and
   fresh-reopen the single aggregate M8 module.

| Branch | Calls | Persisted M8 result | Outcome |
| --- | ---: | --- | --- |
| Safe P1 hypothesis + P2 KEEP; two NO_MODEL | 2 | one aggregate | `CLOSED_WITH_GAPS`; one ready, two no-model pending |
| Safe P1 GAP; planned P2 not run; two NO_MODEL | 1 | one aggregate | `CLOSED_WITH_GAPS`; one GAP, two no-model pending |
| Invalid/tampered M7, bad ownership/gap, wrong safe count, request runtime mismatch | 0 | none | fatal stable code |
| Provider throws after P1/P2 starts | 1 or 2 | no successful M8 module | `PROVIDER_FAILURE_AFTER_START`; no retry/fallback |
| Observed runtime mismatch | 1 or 2 | no successful M8 module | `MODEL_RUNTIME_IDENTITY_MISMATCH`; no retry/fallback |
| Invalid P1/P2 response or P2 expansion | 1 or 2 | no successful M8 module | existing response/expansion code; no retry/fallback |
| Install or fresh-reopen mismatch | 1 or 2 | no verified success result | `PROCESS_MODEL_REFERENCE_INVALID`; never relabel as GAP |

Preflight cardinality failure uses `PROCESS_MODEL_SHARD_SET_UNSUPPORTED`; other malformed reference
or closure failures use `PROCESS_MODEL_REFERENCE_INVALID`. Fatal branches fail the current run and
retain normal store staging/diagnostic facts; this slice adds no same-run crash recovery. Because
it accepts only one model-safe packet, there is no earlier completed packet for a later packet to
replay. General `S > 1` checkpoint/reuse remains explicitly unsupported rather than falsely marked
complete.

## One exact Luna RED

Luna/xhigh adds one test and runs only:

```text
mvn -t .mvn/toolchains.xml -o \
  -Dtest=BusinessProcessInterpretationExecutionPublisherTest#publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result \
  test
```

The test builds and receipt-last publishes the existing chained-call M7 fixture with `A=3,S=1`,
scripts only the exact P1 hypothesis and P2 KEEP responses, and asserts:

- exactly two Provider calls in P1→P2 order, both receiving only dry canonical application bytes;
- all three M7 shard IDs/ordinals and all three relation owners occur exactly once in M8;
- the safe terminal is field-equivalent to the current success checkpoint contract;
- both no-model terminals have zero model objects/calls and preserve exact upstream Gap IDs;
- aggregate counts equal the fixed table, outcome is `CLOSED_WITH_GAPS`, receipt is
  `SUCCEEDED_WITH_GAPS`, and `gapRefs` is the exact union;
- a fresh module-store reopen revalidates the complete payload/reference.

Initial RED must be only the absent aggregate execution publisher/behavior, not fixture failure,
compile drift or a changed P1/P2 oracle. Existing selectors
`BusinessProcessInterpretationModulePublisherTest#persistsP1GapAndPlannedP2NotRunWithoutCallingP2`
and `#persistsP1HypothesisAndP2KeepWithTwoReceipts` are regression checks after GREEN, not
additional REDs.

## Terra GREEN scope

Terra/xhigh may only:

- add `BusinessProcessInterpretationExecutionPublisher` and its two-field request;
- add internal checkpoint-set projection records or equivalent package-private types;
- extract one shared package-private terminal projector from the current publisher so existing P1
  GAP and P1→P2 KEEP values remain exact;
- register the new internal checkpoint-set artifact policy/type at the same fixed M8 address;
- implement preflight, mixed-shard accounting, no-model terminal projection, receipt-last install
  and fresh-reopen verification required by the one RED.

Terra must not modify M6/M7 semantics, evidence collection, dry packet content, Provider adapters,
P1/P2 response language, public Step 06 schemas/files, M9, Step 07/08, the public
`RepositoryAnalysisAgent`, or the 15/57 counts. It must not generalize to zero or multiple model-
safe packets, retry a call, auto-resume, add fallback, reparse source, or synthesize evidence.

## Current state

The bounded aggregate M8 design and corrected receipt oracle are complete. No production Java,
schema files, Provider, Maven, network, Git, `AGENTS.md`, `docs/DESIGN.md`, or stage design file was
changed by this task.

## Changed files

- `progress/m8-multi-shard-publication-design.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared-worktree WIP identified and preserved. |
| Read-only source/design inspection | PASS | Existing M7 fixture proves three two-Flow-context shards with one `MODEL_SAFE` plus two `NO_MODEL`; existing M8 proves exact P1 GAP and P1→P2 KEEP terminals. |
| Receipt oracle audit | CORRECTED | Two shard-local references to one Flow Gap remain in their dispositions; expected module `gapRefs` now uses `distinct().sorted()` and exact ordered equality. |
| M7 reference oracle audit | CORRECTED | The aggregate payload is checked against exact `{moduleArtifactRoot,moduleReceiptId,moduleReceiptSha256}` values from M7; `artifactId` is absent. |
| Maven / Provider / network / Git mutation | NOT RUN | Explicitly outside this design-only task. |

## Decisions

- Use one new whole-publication execution publisher; do not call the fixed-address singular
  publisher per shard.
- Bound the aggregate to exactly one model-safe packet plus one or more no-model shards. This
  exercises mixed publication and obeys packet-checkpoint discipline without overdesigning multi-
  packet recovery.
- Preserve all business/evidence semantics from M7 and send only its dry packet to the Provider.
- Persist terminal no-model and P1 GAP states as gaps, never as full interpretation success.
- Treat module receipt `gapRefs` as the sorted unique union while preserving the same Gap reference
  in each owning shard disposition.
- Preserve `businessProcessTaskPublicationRef` as a `ModulePublicationReference`; never coerce it
  to or test it as an `ArtifactReference`.

## Blockers

- None for this bounded RED/GREEN.
- Arbitrary `S > 1`, zero-safe execution, additional P2 decisions, P1/P2 FAILED/GAP variants and
  full M9 publication remain intentionally outside this slice.

## Exact next action

- Luna/xhigh creates only
  `BusinessProcessInterpretationExecutionPublisherTest#publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result`,
  runs the exact selector above to record an assertion-only RED, and hands that single RED to
  Terra/xhigh. Terra implements only the frozen mixed `A=3,S=1` aggregate behavior, reruns that
  selector, then reruns the two existing singular checkpoint regression selectors.

## Resume checks

- Re-read this file, scoped `AGENTS.md` packet rules, and Step 06 §§6.4–7.2.
- Confirm the fixture has three distinct Flows and three unique relation owners; “two-Flow” applies
  to each shard context, not the repository denominator.
- Confirm no model-safe packet beyond the one supported packet starts and no no-model shard causes
  a Provider call.
