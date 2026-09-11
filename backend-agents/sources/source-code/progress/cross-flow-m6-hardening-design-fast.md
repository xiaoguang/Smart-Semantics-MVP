# Progress: cross-flow M6 hardening design fast

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Harden the durable M6 contract for evidence closure, complete program-only material, dry M7 model projection, incremental packet persistence, and behavior-at-a-time repair acceptance. Documentation only; no Java, tests, fixtures, or POM changes.
- Approved files: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, and this progress record.

## Inputs read

- `AGENTS.md`
- `progress/cross-flow-m6-luna-review.md`
- `docs/analysis-steps/06-flow-interpretation.md` §§5.1–5.3 and relevant §6.2
- Current M6 process package and tests (read-only audit in progress)

## Design constraints

- Preserve complete audit and trace material program-side; expose only a small, DRY, business-reading packet to Luna.
- Start with one Flow, then the smallest cross-Flow group; persist each input, result, and disposition independently and never replay a verified packet.
- Repair one behavior at a time: intended RED, minimal GREEN, direct selector; run the M6 aggregate only after all direct selectors are GREEN.
- Do not change the eight analysis steps, Step 06 fifteen-file contract, or full-run 57 outputs.

## Current decision

- P0 trust closure and complete M6 persisted material are mandatory before M7.
- Registry input compatibility/closure, run/profile/budget reopening, existing positive lanes, counter/group/identity/accounting closure, and typed-record canonical validation are M6 acceptance work, sequenced after the P0 foundation.
- Only cue kinds without an upstream verifiable basis (`ENTRY_VERB`, `STATE_WORD`) and generic-context independent relation formation may be deferred; they must remain unable to create a relation.

## Verification

- `git diff --check`: PASS (exit 0).

## Changed files

- `AGENTS.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `progress/cross-flow-m6-hardening-design-fast.md`

## Final repair sequence

1. Cut M3 and every current internal consumer over to the distinct internal Registry module wire.
2. Fresh-reopen and close run/profile/budget plus Step 03/04/05/M3 publication lineage.
3. Replace the tautological Flow/Capsule equality check and revalidate Fact→atom→Proof→Evidence→source/span/obligation closure.
4. Persist and fresh-reopen complete value-bearing M6 program material, limits, and upstream references.
5. Implement the currently provable positive lanes and entry-target traversal closure.
6. Close counters, group ownership, canonical records, identities, accounting, and stable fail-closed errors.
7. For every behavior run one RED → minimal GREEN → identical direct selector; only after all direct selectors pass run the M6 aggregate, then affected Registry-consumer aggregates serially.

Deferred only: `ENTRY_VERB`, `STATE_WORD`, and generic-context-only relation formation until a deterministic upstream basis/profile exists. Multiple Flows sharing one exact METHOD target are legal and must be enumerated, not rejected as ambiguous.
