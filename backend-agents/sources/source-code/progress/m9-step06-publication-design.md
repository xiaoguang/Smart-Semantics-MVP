# Progress: M9 Step 06 formal publication design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze one target-aligned M9 `FlowInterpretationPublicationSpecifier` contract that fresh-reopens the completed local and process interpretation modules, closes all Step 06 accounting, and publishes the existing fourteen semantic payloads plus one receipt without any Provider call or source reparse.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, full Step 06 §§6–7.2, both implementation plans, current M6/M7/M8 progress, sources, and direct tests. Documentation/progress only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, both implementation plans, target Step 06 model boundary, exact standalone wire catalog, identity DAG, and the latest M6/M7/M8 finite-shard implementation records.
- Confirmed M9 is a deterministic publication/accounting module, not another interpretation runner and not a source/evidence reconstruction module.
- Audited the eight persisted M1–M8 module publications and identified the exact target-wire values that current checkpoint carriers do not yet retain.
- Added the target M9 public seam, eight-reference reopen chain, publication-readiness gate, fourteen semantic projections, count equations, terminal mappings, evidence lineage, receipt-last identity and fatal behavior to the Step 06 detailed design.
- Froze the initial executable fixture as two fully interpreted local Flows plus one model-ineligible Flow and three process shards (`A=3/S=2/I=1`), and froze the zero-Flow publication behavior.
- Froze the only safe implementation order: four predecessor-carrier RED/GREEN changes followed by one M9 RED/GREEN; M9 may never invent missing semantics or runtime identity.

## Current state

- The M9 design is closed. M9 fresh-reopens exact M1–M8 publications, performs program-only projection/accounting, calls neither Provider nor source parser, and installs the established fourteen semantic files plus `flow-interpretation-receipt.json` last.
- Current M2/M5/M7/M8 checkpoint carriers cannot yet losslessly produce the formal wire. They must first retain the already-produced receipt identity, local proposal semantics, formal process request/Gap values and full process-hypothesis projection. This is an implementation prerequisite, not a new model round or new evidence subsystem.

## Changed files

- `docs/analysis-steps/06-flow-interpretation.md`
- `progress/m9-step06-publication-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check -- docs/analysis-steps/06-flow-interpretation.md progress/m9-step06-publication-design.md` | PASS | No whitespace errors. |
| Targeted terminology/search audit | PASS | M9 keeps 15/57, aggregate M8 only, `A=3/S=2/I=1`, zero Flow, no Provider/source read and one M9 selector. |

## Decisions

- Preserve the Step 06 fifteen-file and full-run 57-output contracts, the existing P1/P2 grammar, and the Step 07 interface.
- Prioritize fluent business-process semantics already present in accepted local/process results; retain their existing evidence lineage without adding a new precision mechanism.
- A legal but reduced checkpoint is not publication-ready. Missing target-wire values produce `FLOW_INTERPRETATION_PUBLICATION_INPUT_INCOMPLETE` before any Step 06 write.
- Public local records use standalone type/schema pairs; they never reuse the M1–M5 collection-module envelopes. Public Candidate embeds its validated same-Flow proposal projection so Step 07 does not depend on private M5 state.

## Blockers

- None.

## Exact next action

- Luna/xhigh starts the four ordered predecessor-carrier REDs described in §6.7.2. After their Terra GREENs, Luna creates the single `FlowInterpretationPublicationSpecifierTest` selector and Terra implements only M9 publication.

## Resume checks

- Preserve every unrelated shared-worktree edit.
- Do not modify Java, tests, Schema, Maven, Provider adapters, Git state, or any design document other than the Step 06 detailed design unless a true cross-step rule is missing.
- Do not call a live Provider, customer source scanner, network, or customer Maven.
