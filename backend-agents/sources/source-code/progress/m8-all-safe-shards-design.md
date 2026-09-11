# Progress: M8 arbitrary model-safe shard execution design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze the smallest target-aligned M8 generalization from the current `A=3, S=1` aggregate publisher to every finite M7 `MODEL_SAFE` shard count, without changing the Step 06 fifteen-file or full-run 57-output contracts.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, Step 06 §§6.4–7.2, current M6/M7/M8 progress, and current M7/M8 production/test seams. Documentation only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, target Step 06 wire and identity DAG, current M7 shard compiler/publication, all bounded M8 checkpoint contracts, and the current whole-M7 `A=3, S=1` execution publisher.
- Confirmed M8 already has the correct aggregate artifact type, Schema, fixed module address, dry packet boundary, P1 GAP terminal, P1-success/P2-KEEP terminal, and receipt-last store. The missing capability is finite-cardinality orchestration, not another protocol.
- Froze the all-`S` execution, accounting, failure, persistence, RED, and Terra implementation contracts below.
- Chose one final aggregate receipt-last installation only. Per-packet results are validated immediately in memory but are not independently persisted and do not form a same-run recovery subsystem.

## Current state

The current production aggregate publisher only accepts one `MODEL_SAFE` shard and at least one `NO_MODEL` shard, hard-codes the `A=3, S=1` counts, and rejects a valid zero-safe or multiple-safe M7 denominator. Existing single-shard runner/module-publisher behavior is otherwise the implementation basis and remains a regression contract.

This design closes only the M8 cardinality/orchestration gap. It does not broaden the current bounded packet grammar, model response language, Step 06 file set, Step 06 boundary, Provider adapters, or downstream knowledge/document behavior.

## Frozen design

### 1. Denominators and terminal classes

For one freshly reopened, valid M7 publication:

```text
A = number of all M7 task shards
S = number of MODEL_SAFE shards
I = number of NO_MODEL shards = A - S
H = MODEL_SAFE shards whose P1 hypothesis is accepted and whose P2 is KEEP
Q = MODEL_SAFE shards whose P1 terminal is GAP

0 <= S <= A
S = H + Q
```

For this bounded M8 slice, every safe shard must end in exactly one of the two already implemented nonfatal terminals: `P1SuccessTerminal` or `P1GapTerminal`. A Provider/runtime/Schema failure is not a third terminal; it is fatal and prevents publication.

`A=0, S=0` is valid. It means M7 found no process candidate shard. It produces a closed empty M8 aggregate and zero calls; it does not claim that the repository has no business behavior. Downstream accounting and upstream Gaps retain that distinction.

`A>0, S=0` is also valid. Every M7 shard becomes an exact `NO_MODEL_ADMISSION_PENDING` terminal with its M7-owned Gap IDs and zero calls.

### 2. Whole-set preflight before any model call

`runAndPublish` must first reopen the exact M7 reference and validate the complete denominator before invoking the Provider:

1. The M7 address, type, Schema, payload count, `closed=true`, and `providerCallCount=0` are exact.
2. Every shard ID and ordinal is unique and every candidate relation has exactly one shard owner.
3. `MODEL_SAFE` means non-null dry packet, non-empty reader bindings, and no ineligibility Gap.
4. `NO_MODEL` means null packet, empty reader bindings, and at least one resolvable M7 process Gap.
5. Every dry packet passes the existing bounded packet checks; runtime identity and policy are valid and consistent for the execution request.
6. The complete owner-relation union equals the M7 candidate-relation denominator; no owner relation is duplicated or omitted.

Any failure here is fatal before the first Provider call and installs no M8 publication.

### 3. Deterministic sequential execution with semantic isolation

After preflight, sort all shards by `(shardOrdinal, taskShardId UTF-8)`. Visit them in that order:

- A `NO_MODEL` shard is projected immediately to its deterministic terminal and makes no Provider call.
- A `MODEL_SAFE` shard is executed with the existing runner, one dry packet at a time.
- Validate and project that shard's complete terminal before visiting the next shard.
- Never concatenate two packets, never put a prior model response into a later prompt, and never use execution order as evidence of business order.

The M7 packet retains the business semantics: ordered flow cards, relation direction, signal/counter-signal/limitation slots, and reader-key bindings. Hashes, source paths, receipts, Proof/Evidence internals, budgets, runtime configuration, and other technical transport material stay outside the model request. Packet-local keys such as `F01`, `F02`, and `L01` may repeat in different packets because they are interpreted only inside their owning packet.

This is the required small-input behavior in production form: each model call sees one already bounded packet, each response is checked before the next call, and a later packet cannot contaminate an earlier interpretation.

### 4. Exact per-shard behavior

| M7 shard / response | Tasks | Rounds | Receipts | Task dispositions | Hypotheses | Calls | Final disposition | Continue? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| `NO_MODEL` | 0 | 0 | 0 | 0 | 0 | 0 | `NO_MODEL_ADMISSION_PENDING` | yes |
| P1 returns valid GAP | 2 | 1 | 1 | 2 | 0 | 1 | `GAP` | yes |
| P1 valid hypothesis, P2 valid KEEP | 2 | 2 | 2 | 2 | 1 | 2 | `READY_FOR_ADMISSION` | yes |
| Invalid packet/runtime before calls | — | — | — | — | — | 0 | fatal | no |
| Provider transport/runtime failure or invalid/unsupported P1/P2 | — | — | — | — | — | calls already made | fatal | no |

For the P1 GAP branch, the P2 task remains planned and its task disposition is `NOT_RUN_UPSTREAM_FAILED`; P2 is not called. P1 GAP limitation keys must map only to the current packet's finite reader bindings and then to existing M7 Gap IDs.

The success branch remains the current exact one-hypothesis plus P2 `KEEP` contract. Multiple hypotheses, P2 `NARROW/DROP/NEEDS_EVIDENCE`, P1/P2 typed failure publications, and a wider packet shape are not silently introduced by this cardinality change.

### 5. Aggregate conservation equations

Once all `A` shards reach nonfatal terminals, the checkpoint-set must satisfy:

```text
shardResults                              = A
accounting.taskShardCount                = A
accounting.modelSafeShardCount           = S
accounting.noModelShardCount             = I
accounting.plannedProcessModelTaskCount  = 2S
accounting.actualProcessModelRoundCount  = S + H
accounting.generationReceiptCount        = S + H
accounting.taskDispositionCount          = 2S
accounting.businessProcessHypothesisCount= H
accounting.processInterpretationDispositionCount = A
accounting.providerCallCount              = S + H
accounting.readyForAdmissionCount         = H
accounting.gapDispositionCount            = Q
accounting.noModelAdmissionPendingCount   = I
accounting.failedDispositionCount         = 0
body.providerCallCount                    = S + H
```

The current bounded runner produces exactly one retained hypothesis for each success terminal, hence `businessProcessHypothesisCount=H`. The equation must be changed with the response contract if that bounded rule is ever broadened; it must not be guessed from array length alone.

The global Step 06 call equation remains unchanged:

```text
planned model tasks = E + 2R + 2S
actual Provider calls
  = E
  + R
  + accepted local R1
  + S
  + accepted process P1

accepted process P1 = H in the current M8 terminal language
```

The aggregate receipt `gapRefs` is the UTF-8-sorted unique union of all terminal process-disposition Gap IDs: P1 GAP mappings plus `NO_MODEL` ineligibility Gaps.

### 6. Aggregate outcome and completion

```text
if Q == 0 and I == 0:
    executionOutcome = ALL_READY_FOR_ADMISSION
    module completion = SUCCEEDED
    receipt gapRefs = []
else:
    executionOutcome = CLOSED_WITH_GAPS
    module completion = SUCCEEDED_WITH_GAPS
    receipt gapRefs = sorted unique terminal Gap union
```

This makes `A=0, S=0` a closed empty `ALL_READY_FOR_ADMISSION` execution with `SUCCEEDED`, all counts zero, and no calls. It is a vacuous M8 accounting result, not reader-facing evidence of business completeness.

### 7. Fatal behavior

The following remain fatal and stop the loop immediately:

- malformed, stale, noncanonical, incomplete, or wrong-address M7 material;
- invalid runtime identity/policy or a dry packet that fails the existing bounded checks;
- Provider transport/runtime failure;
- empty, malformed, extra-key, out-of-allowlist, or otherwise unsupported response;
- P2 expansion or reference outside the P1/current-packet closure;
- payload installation collision, receipt-last failure, or fresh-reopen mismatch.

On a fatal event, no successful M8 aggregate is installed. M7 remains valid. Calls already made in this attempt are not called “rolled back,” automatically retried, switched to another Provider, or treated as reusable model results. A later explicit new analysis run may execute the model again; that is a new user-visible execution, not same-run recovery.

### 8. Persistence and identity law

Use the existing fixed M8 address, file, artifact type, and Schema:

```text
business-process-interpretation-runner
process-interpretation-checkpoint.json
FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT_SET
flow-interpretation-process-interpretation-checkpoint-set-v1
```

All per-shard terminals remain in memory until the whole denominator closes. Then install exactly one canonical aggregate module publication, receipt last, and fresh-reopen it once. Do not add a per-packet journal, mutable checkpoint, dynamic module address, generation receipt side store, replay cursor, or same-run recovery state machine.

The scoped rule that a packet is “installed before the next packet” is narrowed here to its target purpose: the packet must be fully validated and converted to an immutable terminal before the next packet executes. It does not require a durable module publication per packet. The current store has one immutable M8 address; durable incremental replacement would create a new recovery subsystem outside the user's priority.

Aggregate identity is determined by the exact M7 publication reference, controls, canonical ordered terminals, accounting, execution outcome, and completion/gap set through the existing framed artifact and receipt formulas. Therefore:

- equal M7 bytes, controls/runtime identity, and exact Provider response bytes produce equal aggregate bytes and identity across workspace roots;
- different response bytes or a different terminal branch produce a different artifact/publication identity;
- no byte-for-byte determinism is claimed for live model generation;
- calling `runAndPublish` again is not a resume promise and must not be advertised as avoiding Provider replay.

This adds no Step 06 public file. M8 remains an internal module publication feeding the existing Step 06 publisher. The Step 06 contract stays at 15 files and the complete run stays at 57 outputs.

## Minimal Luna RED contract

Add exactly one new test method to the existing execution-publisher test class. Do not create another `S=1` loop.

Selector:

```text
mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime test
```

The synthetic M7 fixture has exactly three canonically identified candidate relations and therefore `A=3` shards:

1. `MODEL_SAFE` shard 1 owns relation 1 and contains exactly two flow cards plus that relation. Its distinct operation markers let the scripted Provider assert it received only packet 1. P1 proposes the one allowed hypothesis and P2 returns `KEEP`.
2. `MODEL_SAFE` shard 2 owns relation 2 and contains exactly two flow cards plus that relation. Its operation markers differ from packet 1. A packet-local `L01` binding maps to an existing M7 Gap. P1 returns valid GAP; P2 must not be invoked.
3. `NO_MODEL` shard owns relation 3, has null packet and empty bindings, and references its own existing M7 ineligibility Gap.

Use production M7 identity formulas to build the publication; do not mutate a packet while retaining its old task-shard, packet, or publication identity. Packet-local `F01/F02/L01` reuse across safe shards is expected and proves isolation.

The scripted Provider must observe exactly this canonical call order:

```text
safe shard 1 P1
safe shard 1 P2
safe shard 2 P1
```

It must fail the test on a fourth call, a combined packet, prior-response leakage, forbidden technical payload, or reversed shard order.

Expected aggregate values are:

```text
A=3, S=2, I=1, H=1, Q=1
planned tasks=4
rounds=3
receipts=3
task dispositions=4
hypotheses=1
process dispositions=3
Provider calls=3
ready=1, gap=1, no-model-pending=1, failed=0
executionOutcome=CLOSED_WITH_GAPS
completion=SUCCEEDED_WITH_GAPS
```

Assert that receipt Gap refs are exactly the sorted union of the shard-2 P1 GAP and shard-3 no-model Gap, and fresh-reopen the aggregate. The test's first accurate RED must be the current finite-cardinality rejection or equivalent hard-coded aggregate invariant, not fixture corruption.

Exact `S=0` and `A=0` behavior is frozen above for Terra's generalized implementation, but this bounded RED adds no second selector or extra one-shard test loop. Existing M7 zero/no-model conservation tests and later Step 06 accounting tests retain those denominators.

## Terra GREEN scope

Terra/xhigh may modify only the current M8 execution publisher and the smallest package-private M8 runner/projector extraction required to reuse existing strict validation/projection for a list of terminals.

Required production changes:

- replace the single `modelSafeShard` preflight field with canonical `modelSafeShards` and retain all no-model shards;
- preflight the complete denominator and runtime before the first Provider call;
- iterate safe packets sequentially in canonical shard order;
- project both existing `P1SuccessTerminal` and `P1GapTerminal` without loosening their validation;
- compute all counts, outcome, completion, Provider calls, and receipt Gap refs from the frozen equations rather than `A=3, S=1` constants;
- accept every finite `0 <= S <= A`, including empty and all-no-model denominators;
- keep the one final receipt-last aggregate install and fresh-reopen verification.

Explicitly out of scope:

- no new artifact type, Schema, file, module address, public Interface, Step 06 boundary, or 15/57 count;
- no per-packet durable publication, mutable checkpoint, journal, retry, fallback, Provider switching, or same-run recovery;
- no M6/M7 packet or shard semantic change;
- no wider process-model response grammar;
- no M9/Step 07/Step 08 change;
- no real Provider invocation.

After the one new selector turns GREEN, rerun the existing M8 single-safe runner, module-publisher GAP/success, and mixed execution-publisher selectors as regressions. Do not weaken an old assertion merely to make the generalized loop pass.

## Changed files

- `progress/m8-all-safe-shards-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only scoped design/source/test inspection | PASS | M8 has one immutable registered module address and an existing aggregate checkpoint-set Schema; current publisher alone hard-codes one safe shard and `A=3/S=1`. |
| Contract self-audit | PASS | Covers every finite `S`, both existing nonfatal branches, exact aggregate equations, fatal stop, one final install, one multi-safe RED, and bounded Terra scope without changing 15/57. |

## Decisions

- Generalize orchestration, not the model protocol.
- Preflight the entire M7 denominator before any Provider call.
- Execute one dry packet at a time, sequentially and canonically.
- Validate each result immediately, but persist only the final closed aggregate.
- Accept the simple consequence that a fatal later call leaves no M8 result and a new explicit run can call again; do not build recovery around a non-core feature.
- Preserve all current strict packet/runtime/response rules and all existing single-safe regression paths.

## Blockers

- None. The durable-per-packet wording in scoped guidance is resolved for M8 by interpreting “installed” as terminal validation inside the aggregate execution; only the aggregate is a durable receipt-last module publication. The parent should synchronize that sentence in the next authorized design/rule update so future Agents do not invent a journal.

## Exact next action

- Luna/xhigh adds only `BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime`, runs the exact offline selector, and records the first accurate RED. Terra/xhigh then generalizes only the M8 aggregate execution publisher under the scope above.

## Resume checks

- Preserve every existing shared-worktree edit.
- Read this completed Sol ruling before changing M8.
- Modify no design boundary, file-count contract, packet grammar, or Provider policy while implementing the cardinality loop.
- Do not run a real Provider, customer source, network operation, or customer Maven.
