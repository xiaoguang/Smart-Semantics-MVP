# Progress: process-signal-capability-audit

- Status: COMPLETE
- Agent role: Sol/ultra design authority; bounded read-only core-capability audit
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08 19:04:25 NDT
- Last updated: 2026-09-08 19:25:32 NDT
- Scope: Determine the smallest already-designed, evidence-preserving processJoinSignals capability slice needed for a meaningful nontrivial cross-Flow candidate from whole-repository Java analysis. No architecture rewrite or implementation.
- Approved inputs: Authoritative `docs/DESIGN.md`; analysis Steps 04/05/06; `.superpowers/flow-signals-implementation-handoff.md`; narrowly relevant persisted graph, Fact, Proof, registry, Flow, signal, compiler, and publisher code/tests. No live source, model, provider, network, Maven, JVM, or customer scan.
- Current branch/worktree: `codex/source-analysis-flow-signals-implementation` at `21bba98638dd37386829f14bd1c8cb8176908ff1`; `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read repository, backend, and source-code scoped instructions and confirmed the worktree/branch/baseline.
- Confirmed shared in-flight changes belong to other roles and must be preserved.
- Read the authoritative trust/cross-Flow/authority sections of `docs/DESIGN.md`, Steps 04/05/06, and the Flow-signal implementation handoff.
- Audited the narrow persisted graph, Fact/atom/Proof/Evidence/Gap, Flow/signal/Capsule, R0 registry, and reader/compiler records needed to answer the bounded capability question.
- Separated the first proof-backed candidate route from the weaker R0 semantic-cue route and from the still-missing domain-classification prerequisite.

## Current state

- Step 04 v2 has exactly `JAVA_BOUNDARY_INVOCATION` and `JAVA_GUARD_CONDITION`: `FactRegistry.standardJavaFacts()` and Step 04 §§3/8.0/8.0.1. Its public `CodeFact`/`FactAtom`/`AtomProof` retain the candidate denominator key, subject IDs, typed atom value, closed Proof, program edges, Evidence IDs, rules, and source closure.
- Step 05 §8.1.1 permits exactly `JAVA_TYPE_ANCHOR`, `EXPLICIT_CALL`, guarded `COUNTER_CONDITION`, and `EXTERNAL_EFFECT_GAP`, all `GENERIC_TECHNICAL`. `FlowCompilation.ProcessJoinSignalV1` now carries the complete sixteen-kind wire and closure checks; the in-flight `EntryRootedFlowCompiler.compileProcessJoinSignals` implements the finite boundary projection, including current counter work. This working-tree state is not public M2/M3/local-reader acceptance; those edits remain owned by the active Luna/Terra/debug roles.
- `PersistedFlowCompilationInputReader` already reopens ApplicationDiscovery, ProgramGraphs, ProvenCodeFacts, Proofs, Evidence/source locators, and Gaps for M1. `RegistryProposalTaskCompiler`/`RegistryProposalRunner`/`RepositoryInterpretationRegistryFreezer` implement the local R0/freeze path and preserve `proposalKind`, `normalizedLabel`, `basisAtomIds`, `basisGapIds`, Flow/Capsule IDs, and `provisionalKey`, but their public BusinessFlows schema cutover is in flight. No `CrossFlowCandidateCompiler`, `ProcessSemanticCueV1`, `ProcessCandidateRelationV2`, or `ProcessEvidenceGroupV2` production class exists; Step 06 §12 correctly records M6–M9 as unimplemented.

## Bounded conclusion

### Smallest proof-backed nontrivial candidate

- The smallest strong route is the already-designed Step 06 direct-call rule: a caller's proof-closed `EXPLICIT_CALL` whose `CALL_TARGET` key exactly equals another compiled Flow's entry target. Step 06 §5 explicitly allows only the calling-side signal array to be nonempty, so `DOMAIN_SPECIFIC` is **not** required. The result is a directed `PROVEN_HANDOFF`; it still proves neither business order beyond the call nor an external effect.
- The necessary graph material already exists without a graph/schema addition: `CallGraphBuilder` emits a `CALL_SITE` and an `EXACT` `CALL_TARGET` edge with `fromNodeId`, `toNodeId`, `ruleId=java-static-field-receiver-call-v1`, and Evidence; `CodeStructureGraphBuilder` gives the target `METHOD.canonicalValue = FQN#method(parameterTypes)`; `ControlFlowGraphBuilder.walkEntry` emits the entry root's exact `NEXT/control-flow-entry-root-v1` edge to that same METHOD node. `ProofRuleRegistry` already permits the call-site/call-target rule. A callee target can therefore be deterministically derived as the unique METHOD reached by that root edge and compared byte-for-byte with the caller anchor key.
- What is missing is the versioned Step 04 Fact admission for an **internal frozen-Java exact call** and its Step 05 projection to `EXPLICIT_CALL`. Current Step 05 `EXPLICIT_CALL` comes only from `JAVA_BOUNDARY_INVOCATION`; `DataFlowGraphBuilder.bindGenericJavaBoundaries()` deliberately skips any target with a concrete frozen Java body. Consequently an ordinary call to another compiled Java entry is traversed but not materialized as the current boundary Fact/signal. The present `ApprovalClient`/`CancellationClient` fixture targets interfaces, not another entry, and proves no cross-Flow handoff.
- This is a bounded adjacent Step 04→05→06 protocol clarification, not a new architecture: Sol/ultra must freeze the internal-call Fact kind/atom set, exact Proof subjects, target-entry derivation/failure rule, and schema versions before Luna writes RED. No new state, recovery subsystem, Provider role, public artifact count, or effect claim is needed.

### Legitimate weaker route

- A local R0 frozen cue can legitimately create a non-singleton candidate without `DOMAIN_SPECIFIC`: Step 06 §5.1 allows two frozen `BUSINESS_TERM` registry items with the same `normalizedCueKey`, each with a nonempty same-Capsule atom basis, to form `REGISTRY_BUSINESS_TERM`. M6 would emit an undirected `SEMANTIC_CUE` relation and connected group with `relationUse=PENDING_ONLY`; M6 itself makes zero Provider calls.
- This is evidence-bounded model interpretation, not a proof-backed `processJoinSignal` handoff. It cannot prove order/causality, cannot promote generic signals, and cannot discharge Step 05 domain/classification acceptance. Method/route/Chinese-name similarity without two qualifying frozen registry terms remains no edge.
- A small contract algorithm is genuinely absent: neither code nor the detailed design defines the `ProcessCueProfile` record/reference, exact `normalizedCueKey` normalization, or the frozen entry/state lexicons. The current R0 runner also copies response `label` into the field named `normalizedLabel`; the authoritative design requires program normalization/Unicode validation. A bounded Sol/ultra clarification is required before this M6 cue RED is exact.

### Domain/classification prerequisite

- The remaining signal families are wire-designed in `ProcessJoinSignalV1`, and Step 06 `SHARED_ANCHOR` is fully specified at the pair level, but Step 04 has no Fact/atom/rule that proves business object, business identifier, state, table, field, event, return transfer, object reference, conflict, or `DOMAIN_SPECIFIC` classification. Step 05 §8.1.1 calls these exact absences.
- This is genuinely missing upstream semantic material, not reader work. Repository ownership, type/parameter/method names, guard text, or Mapper/XML text cannot be promoted. R0 is downstream and cannot create a Fact or rewrite signal specificity. Full Step 05/domain and effective whole-repository reconstruction acceptance therefore still needs a separate bounded Sol/ultra choice of one proofable classification source/rule; this audit does not invent it.

## Direct fixtures and effort

1. Luna public-seam RED: two distinct persisted entries where Flow A has an exact internal Java call to Flow B's concrete handler. Assert the Step 04 closed call Fact, the caller-only generic `EXPLICIT_CALL`, exact target equality through `FlowSlice.rootNodeId → control-flow-entry-root-v1 → METHOD.canonicalValue`, one `PROVEN_HANDOFF/LEFT_TO_RIGHT` relation, no invented external-effect claim, and stable relation/group identity. In the same fixture, overload/signature mismatch and same-name/no-exact-edge variants produce no relation; deleting call target Evidence/Proof fails closed.
2. Luna public-seam RED after the cue-profile clarification: two Flow-scoped frozen `BUSINESS_TERM` items with equal normalized cue key and nonempty own-Capsule atom bases produce exactly one `SEMANTIC_CUE/UNDIRECTED/PENDING_ONLY` relation; `CLAIM/QUESTION`, gap-only/empty/foreign basis, unequal keys, or name-only similarity produce none.

- Approximate additional continuous engineering time after the current counter/Capsule/M3/local-reader work: **16–24 hours** for the proof-backed internal-call vertical (bounded Sol contract, Luna RED/mutations, Terra Step 04/05/M6 GREEN and targeted verification). A cue-only M6 vertical is about **10–14 hours**, including its bounded profile/normalization clarification, but it is not a substitute for the strong slice or domain acceptance. Domain-classification production time should not be folded into either estimate until Sol selects an evidence source/rule.
- Current ownership limitations do not block the proposed disjoint positive fixtures, but they block a whole-repository claim: `PersistedFlowCompilationInputReader.boundaryInvocation/boundaryFor` require singleton entry ownership, while legitimate graph subjects can have multiple entry owners. The new Capsule projector also keys a global span by `evidenceNodeId` alone and `SpanBuilder.requireOwningFlow` rejects reuse by a second Flow. A shared-source, distinct Flow-owned Fact/atom regression is required before whole-repository acceptance; the exact contextualization remedy is outside this audit and remains unverified.

## Changed files

- `progress/process-signal-capability-audit.md` (this audit record only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared compiler/publisher/Capsule/counter/test edits observed; no audit-owned code change. |
| `git branch --show-current && git rev-parse HEAD` | PASS | Requested branch; baseline `21bba98638dd37386829f14bd1c8cb8176908ff1`. |
| Narrow `rg`/`nl -ba` inspection of Step 04/05/06 and named Java records/builders/readers | PASS | Confirmed finite taxonomy/projection, exact call/root fields, R0 basis fields, missing M6/profile/domain contracts, and current ownership restrictions. |
| `rg` for `CrossFlowCandidateCompiler`, `ProcessSemanticCueV1`, and `ProcessCueProfile` | PASS | No production M6/cue/profile implementation; profile terms occur only in authoritative docs. |
| Maven/JVM/customer/model/network commands | NOT RUN | Explicitly prohibited for this audit. |

## Decisions

- Treat current code as implementation evidence only; authoritative design and step contracts remain the target contract.
- Do not infer business ordering, causality, uniqueness, or external effects from names or generic technical signals.
- Generative model/product-content use: none.
- Recommend exact internal-call handoff as the minimum proof-backed candidate slice; treat R0 semantic cue only as an honest pending-only topology slice.
- Do not use generic shared anchors, blocking signals, repository ownership, or names alone to create a relation.
- Keep the domain/classification prerequisite explicit and separate; current generic completion is not full Step 05 or end-to-end acceptance.

## Blockers

- No blocker to this audit. Implementation of the recommended slice must pause for the bounded Sol/ultra internal-call Fact/target-matching clarification; semantic-cue implementation separately needs `ProcessCueProfile` clarification.

## Exact next action

- Root may continue the already-owned counter/Capsule/M3/local-reader TDD unchanged. Separately schedule the bounded internal-call Fact/entry-target clarification, then the first Luna public-seam direct-handoff RED above. Do not declare whole-repository reconstruction complete until domain/classification and shared-owner/shared-source acceptance are closed.

## Resume checks

- Re-run `git status --short` and preserve all non-owned changes.
- Re-open authoritative docs before relying on any historical/progress statement.
- Do not run Maven/JVM, customer capture/scan, network, Provider, or model commands.
