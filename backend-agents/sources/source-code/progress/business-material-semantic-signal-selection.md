# Progress: business material semantic signal selection

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: No product model used; JavaParser-only material selection
- Started: 2026-09-11 03:25 NDT
- Last updated: 2026-09-11 03:49 NDT
- Scope: Make the bounded, generic syntax outline retain state-changing and persistence-like calls
  from an already selected long Java method, so the model-reading packet can describe a local
  activity without a business-specific dictionary or expanded source traversal.
- Approved inputs: Existing frozen source spans and the existing fixed local jshERP commit; no
  customer execution, network capture, or provider call.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the current real DepotHead automatic packet has source and opening guards but loses
  the middle `setStatus`/mapper update operations because condition observations exhaust the
  bounded outline budget.
- Added a public synthetic RED with irrelevant middle calls before a `setStatus` and an `update`
  call, then made the outline selection retain a bounded diversity of inputs, state/persistence
  shaped calls, guards, post-guard calls and terminals.
- Re-ran the fixed-repository materials-only planner. The current automatic DepotHead packet now
  includes `depotHead.setStatus(status)`, `depotHeadMapper.updateByExampleSelective(...)`,
  `depotItemService.updateCurrentStock(...)`, a log call, and relevant status guards.
- Promoted that real-source check into the explicit opt-in fixed-repository material-planner test,
  so later selection changes cannot silently drop the three state/update/stock observations.

## Current state

- The source-selection result is ready for a one-packet product-model quality check. It does not
  itself claim that a model response is useful business language.

## Changed files

- `progress/business-material-semantic-signal-selection.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderFallbackTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| fixed jshERP automatic materials-only planner | PASS with gap | 337 materials; DepotHead package misses middle state/update action. |
| focused synthetic RED | PASS after implementation | Middle `setStatus` and `update` calls remain in the model observations. |
| material-builder direct selectors | PASS | 9 tests, 0 failures/errors. |
| fixed jshERP automatic materials-only planner after change | PASS | 337 materials; current DepotHead packet contains state/update/stock/log observations. |
| fixed jshERP material planner with retained DepotHead assertions | PASS | 1 test, 0 failures/errors; zero Provider calls. |

## Decisions

- Prefer a bounded diversity of syntax categories (input, guard, mutation/persistence-shaped call,
  terminal) to a long prefix of `if` conditions. This is a generic code-reading heuristic, not a
  business rule or proof upgrade.

## Blockers

- None.

## Exact next action

- Hand the inspected, current automatic packet to the opt-in one-packet Luna/high quality seam;
  do not expand to more materials until its result is inspected.

## Resume checks

- Confirm later material changes remain generic (no DepotHead/business dictionary), preserve
  source caps, and are checked against both the direct selector and a fixed-repository packet.
