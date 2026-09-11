# Progress: exact-call guard counter ruling

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Decide and document the bounded M5→M6 rule for attaching a proof-closed CFG guard counter to an exact Java call that becomes an M6 direct-call candidate. Documentation only; no Java, tests, fixtures, or POM changes.
- Approved files: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/05-business-flows.md`, `docs/analysis-steps/06-flow-interpretation.md`, and this progress record.

## Evidence

- Step 04 already persists a closed `JAVA_EXACT_CALL` Fact and a separate same-entry `JAVA_GUARD_CONDITION/CONTROL_CONDITION` Fact.
- Step 05 already retains complete per-entry traversal paths with exact guard decision polarity and call-site membership.
- Existing Step 05 design allows an equivalent counter only when a `JAVA_BOUNDARY_INVOCATION.controlContext` carries the guard, leaving an exact in-repository call unable to carry the same provable restriction.

## Ruling

- APPROVED as a bounded local M5→M6 protocol adjustment.
- It adds no Fact family, public artifact, model responsibility, or cross-Flow inference. M5 may emit `COUNTER_CONDITION/BLOCKS` for an exact-call tuple only when a unique same-entry closed guard Fact and the complete traversal set prove the call occurs on exactly one polarity and on no path of the opposite polarity.
- The counter uses the exact call's `CALL_TARGET` key and the union of only the exact-call Fact atoms/Proof/Evidence/source plus the matched guard atom/Proof/Evidence/source. Missing, ambiguous, mixed-polarity, incomplete, or foreign material emits no counter; malformed claimed closure fails closed.
- In M6 the counter may attach only to the already-established `EXPLICIT_CALL_TO_ENTRY` positive pair with the identical exact anchor. It cannot create a relation, reverse or prove order, or prove the callee ran or any external effect occurred.

## Verification

- `git diff --check`: PASS (exit 0).

## Affected contracts and consumers

- Producer: BusinessFlows M1 `EntryRootedFlowCompiler` signal projection and its persisted/public Flow/Capsule identities.
- Consumer: FlowInterpretation M6 exact positive-pair counter scoping and relation/group/downstream identities.
- No new schema field, signal kind, Fact family, analysis step, public semantic file, model-visible field, or output-count entry.

## Preserved invariants

- `EXPLICIT_CALL` remains the only positive basis; `COUNTER_CONDITION` alone cannot form a relation.
- Static call direction is not business-process order or proof of runtime execution.
- No Java call or guard proves callee behavior, persistence, messaging, or another external effect.
- Full Fact/Proof/Evidence/source and per-Flow ownership closure remain mandatory; the model sees only the existing dry projection.
- Step 05/06 file counts, eight-step order, fixed nine-section output, and full-run 57-artifact accounting are unchanged.

## Fail-closed cutover

- Valid but insufficient guard/traversal material emits no counter and preserves the independent exact-call signal.
- A record that claims the rule but has duplicate, foreign, mixed-polarity, or broken closure fails with the existing stable input/signal error family.
- Old canonical bytes are not dual-read or patched; affected signal, Flow, Capsule, relation, group, and downstream identities are recomputed together.

## Changed files

- `AGENTS.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `progress/exact-call-guard-counter-ruling.md`
