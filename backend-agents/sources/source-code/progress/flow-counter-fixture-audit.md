# Progress: Flow counter fixture viability audit

- Status: COMPLETE
- Agent role: Sol/ultra design authority for the bounded Step 05 counter-positive fixture audit
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08T20:19:54Z
- Last updated: 2026-09-08T20:34:35Z
- Scope: Audit the current ControlFlow/DataFlow builders, Flow traversal compiler, and existing public fixture for a natural counter-positive source shape; then apply the explicitly authorized predicate correction only to Step 05 §8.1.1/associated acceptance wording, the ignored implementation handoff, and this progress. Do not edit upstream/code/tests/schema/POM or other progress records, and do not run Maven, Provider, network, customer-source, or Git mutations.
- Approved inputs: Parent audit task and follow-up Sol/ultra decision adopting exact invocation-call-site path membership; current `ControlFlowGraphBuilder`, `DataFlowGraphBuilder`, `EntryRootedFlowCompiler`, `PersistedFlowCompilationInputReader`, `ProgramGraphsPublicFixture`, and directly referenced nested records/helpers only.
- Current branch/worktree: `codex/source-analysis-flow-signals` at `2af584b59628831ab6761854056b3e1f1809e1c6` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read applicable repository instructions and the progress template.
- Confirmed the requested branch/HEAD and preserved root-owned `continued-implementation-coordination.md` plus Luna-owned `flow-signals-tests.md`.
- Created this owned progress record before the source audit.
- Read the complete guard/call-block decision path in `ControlFlowGraphBuilder`, boundary-context projection in `DataFlowGraphBuilder`, branch/path enumeration in `EntryRootedFlowCompiler`, the current private input view, and the guarded public fixture source.
- Proved the current control-block polarity predicate cannot be satisfied by a boundary invocation that also has a non-null persisted guard under these builders.
- Identified the smallest Step 05-only predicate change and an exact source fixture that makes the counter positive provable without changing ProgramGraphs or public schemas.
- Applied the authorized exact invocation-call-site predicate, compact `if/else` fixture, and positive selector wording only to Step 05 and the ignored implementation handoff.

## Current state

- No natural Java source shape can satisfy the published conjunction as written: non-null boundary guard/polarity **and** the boundary `controlBlockNodeId` present only on the recorded-polarity path. This is a builder invariant, not merely a limitation of the current early-return fixture.
- Step 05 now uses the exact boundary invocation call-site node (`JavaBoundaryInvocationV1.invocationCallId`, equal to the `INVOCATION_CALL_ID` atom) for the polarity-only path test while still validating the unique containing `controlBlockNodeId`. This predicate-only change needs no upstream field, graph version, wire field, or identity change.
- The bounded documentation correction is complete. Luna's independent first 3/3 RED remains unchanged.

## Exact source shape after the bounded Step 05 predicate decision

~~~java
void approve(String status) {
  if (status == null) {
    return;
  } else {
    approvalClient.record(status);
  }
}
~~~

For this one-top-level-`IfStmt` method:

1. `basicBlocks(...)` creates one block covering the entire `IfStmt`; `callBlocks(...)` maps the nested `record(...)` call site to that block.
2. `guard(...)` sees exactly one guard, a TRUE return terminal, and no FALSE terminal, so its continuation polarity is FALSE. With no successor top-level block, `guardContinuation` is null.
3. The call therefore has `followsGuard=false`; the builder writes the exact `FALSE` edge `guardNodeId → invocationCallId` with non-null guard ID/polarity.
4. `DataFlowGraphBuilder.guardContext(...)` finds that incoming guard-bearing call-site edge and persists its guard ID/FALSE polarity in `JavaBoundaryInvocationV1.controlContext`; `invocationCallId` is the same call-site node.
5. Flow traversal enumerates the guard's TRUE terminal edge and FALSE call-site edge, validates both with the unique `JAVA_GUARD_CONDITION/CONTROL_CONDITION` atom, and adds each edge target to its private `TraversalPath.nodeIds`. Thus the invocation call-site occurs on the FALSE outcome only.
6. The containing `IfStmt` block necessarily occurs before the guard and therefore appears on both outcomes. It cannot serve as the polarity discriminator.

## Why no current-rule fixture exists

- DataFlow returns a non-null boundary guard only when a guard-bearing CFG edge targets the call-site node.
- ControlFlow emits that edge only when `guard != null && !followsGuard`. If `followsGuard`, it instead emits `guard → callBlock` with the guard and `callBlock → callSite` without it, which is the existing early-return shape and produces a null boundary guard.
- When `!followsGuard`, the boundary path is `... → firstBasicBlock → guard → callSite`; it does not traverse the call's containing block after the guard. If that containing block is `firstBasicBlock`, it is shared by both polarities; otherwise it is absent from the boundary invocation path. Therefore it cannot occur only on the recorded-polarity boundary path.
- Both-terminal guards return from `walkMethod(...)` before call-site projection, while zero-terminal guards and multiple guards are unsupported. No remaining parser branch escapes the invariant.

## Adopted bounded Step 05-only predicate

- Keep every existing gate: non-null typed boundary guard ID/polarity; unique matching guard Fact/condition atom; both TRUE/FALSE Flow outcomes; exact same-Flow Fact/Proof/Evidence/source closure; and recorded polarity consistency.
- Replace only `boundary control block occurs on the recorded-polarity path and not the opposite path` with `boundary invocationCallId occurs on at least one recorded-polarity TraversalPath and on no opposite-polarity TraversalPath`.
- Continue validating `controlBlockNodeId` as the unique source-containing basic block, but do not use that statement-level block as the branch-membership discriminator.
- Evaluate this inside M1 while private `TraversalPath.nodeIds` still exists, before conversion to public `OutcomePath`; no new public path field or schema is required.
- The rejected containing-block predicate would require a versioned upstream CFG/basic-block association change; it is not part of this decision or implementation sequence.

## Changed files

- `backend-agents/sources/source-code/progress/flow-counter-fixture-audit.md` (owned by this task)
- `backend-agents/sources/source-code/docs/analysis-steps/05-business-flows.md` (authorized Step 05 predicate/positive acceptance wording only)
- `.superpowers/flow-signals-implementation-handoff.md` (ignored local counter algorithm/selector wording only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Correct branch; only root-owned coordination progress and Luna-owned signal-test progress pre-existed. |
| `git rev-parse HEAD` | PASS | `2af584b59628831ab6761854056b3e1f1809e1c6` |
| `nl`/`sed` source inspection: `ControlFlowGraphBuilder` | PASS | Lines 382–420 establish top-level statement blocks and containment; 299–313 split guarded-block versus guarded-call-site edges; 560–628 enumerate the only supported guard branches. |
| `nl`/`sed` source inspection: `DataFlowGraphBuilder` | PASS | Lines 442–447 persist block/call-site/guard fields; 1454–1482 select the containing block; 1844–1866 derive guard solely from an incoming guarded call-site edge. |
| `nl`/`sed` source inspection: `EntryRootedFlowCompiler` | PASS | Lines 240–280 enumerate all selected guard successors and retain their target nodes in private paths; 351–385 validates each polarity against the unique matching guard condition atom. |
| fixture inspection | PASS | Current lines 602–606 are the two-top-level-statement `if-return; call;` shape, explaining its `followsGuard=true`/null-boundary-context result. |
| adopted-predicate `rg` | PASS | Step 05 and handoff both require exact invocation-call-site membership, keep containing-block validation, name the exact selector, and explain FALSE-only call-site versus shared enclosing block. |
| rejected-predicate `rg` | PASS | No old control-block-only/recorded-polarity block-membership requirement remains in Step 05 or the handoff. |
| `git diff --check` and fence balance | PASS | No tracked whitespace errors; Step 05 has 22 `~~~` fences and the handoff has 2 backtick fences, both balanced. |
| final `git status --short --branch` | PASS | Only Step 05 and this audit belong to this task; root coordination plus other-owner test/implementation files and progress remain present and untouched; the local handoff remains ignored. |

## Decisions

- Finding: no current-rule source-only counter-positive fixture exists.
- Adopted root/Sol decision: use the call-site membership predicate above. It is more precise than statement-block membership because it tests the exact persisted invocation already bound by the signal's `INVOCATION_CALL_ID` atom.
- Candidate subsequent selector remains `EntryRootedFlowCompilerTest#emitsCounterConditionOnlyForProofClosedTypedBoundaryGuardContext`, using the exact `if/else` source shape above through the public stored-artifact fixture seam.

## Blockers

- None for completing this audit.
- Counter-positive implementation remains gated only on root's isolated docs-only publication and the subsequent exact selector; the current 3/3 first vertical is unaffected.

## Exact next action

- Root isolates and publishes the Step 05 predicate correction in its docs-only PR. Luna's current 3/3 RED proceeds independently; only after publication should the separate exact counter-positive selector use the documented `if/else` fixture.

## Resume checks

- Re-read this record and run `git status --short --branch`.
- Preserve `continued-implementation-coordination.md`, `flow-signals-tests.md`, `flow-signals-implementation.md`, and the in-flight compiler test; they have different owners.
- Do not inspect customer sources or run Maven while Luna owns the test lane.
- Reconfirm HEAD/published Step 05 before any later counter-positive test; this audit authorizes no further production, test, schema, upstream, or document edits.
