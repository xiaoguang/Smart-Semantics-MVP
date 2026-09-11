# Progress: fixed-repository business material planning

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add a zero-Provider acceptance seam that runs the approved fixed jshERP commit through
  persisted source inventory and application discovery, then writes inspectable direct-entry
  `business-materials.jsonl`. Do not run program graphs, Facts, Flows, a customer build, or a
  live model.
- Approved inputs: fixed offline commit `8c30ce7861570458920175e200bb2a6442713580`, direct-entry
  material route, and the active business-first design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the fixed-repository acceptance registry covers 37 Step01–05 policies but omits the
  existing `FLOW_INTERPRETATION_BUSINESS_MATERIAL` policy required to install direct business
  materials.
- Added the policy requirement to the no-source-read acceptance oracle and observed the intended
  RED: `policy union drift`. No source path, capture, parser, or Provider was invoked.
- Identified an execution-scale issue before invoking the fixed source: direct fallback currently
  reparses the complete Java document set once per discovered entry.
- Replaced that repeated scan with one per-build handler-span index. The selected fallback content,
  source references and coverage contract are unchanged; only the repeated parsing work was
  removed.
- Added the opt-in fixed-repository planner. It captures the approved local commit, persists
  Step01 and Step02, then produces direct-entry material without graphs, Facts, Flows, customer
  Maven or a Provider.
- Ran that planner successfully: all 337 discovered HTTP entries received an
  `ENTRY_SOURCE_FALLBACK` material and no entry was `NOT_MATERIALIZED`. The persisted JSONL has
  674 records (337 materials plus 337 coverage decisions); every model packet excludes source
  paths, lines, hashes and Proof identities.

## Current state

- The fixed repository now has an inspectable, zero-Provider material plan. This validates the
  first business-first handoff at real repository scale; it does not validate local activity
  semantics, cross-entry processes or a nine-section report.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java`
- `src/test/resources/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `README.md`
- `progress/fixed-repository-business-material-planning.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| fixed acceptance configuration inspection | GAP CONFIRMED | Base64 registry has 37 technical policies and no direct business-material policy. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=FixedRepositoryBusinessFlowsIT#loadsAndValidatesFrozenAcceptanceOracleBeforeAnySourceRead test` | RED | `policy union drift`; the frozen bundle lacks the required material policy before any source read. |
| same selector after registry update | PASS | 38-policy canonical bundle resolves the material file policy before any source read. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest,BusinessMaterialBuilderReplenishmentTest,BusinessMaterialBuilderZeroEntryTest test` | PASS | 6 direct material-builder tests pass after one-index refactor. |
| opt-in fixed planner selector | PASS | 337 materials and 337 coverage records; 0 Provider calls; post-format Surefire test time 38.59 seconds. |

## Decisions

- Do not mutate an already-published source/discovery run to add a new policy. The material policy
  belongs in the frozen policy bundle before the direct execution begins.
- This work only plans and saves material. It must not call Luna or make claims about business
  quality.
- Direct-entry fallback is a usable scale checkpoint, but handler-only excerpts are intentionally
  insufficient evidence that the upcoming activity explanation will be rich enough. The next work
  unit must improve source exploration only where a safe, generic direct call can add the missing
  local context.

## Blockers

- None for fixture and configuration work. The fixed source clone must still be checked before any
  opt-in acceptance execution.

## Exact next action

- Run formatter and direct regression selectors, then start the ActivityExplainer work with a
  small, inspectable source-exploration packet. Do not make another live Luna call until a concrete
  Codex CLI compatibility hypothesis exists.

## Resume checks

- The opt-in test must require an explicit source path/workspace property, reject missing source,
  write no Provider checkpoint, and expose each discovered entry as material or a concrete reason.
