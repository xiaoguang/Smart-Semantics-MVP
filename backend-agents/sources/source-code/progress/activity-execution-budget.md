# Progress: activity execution budget

- Status: COMPLETE
- Agent role: Root implementation agent; Step 06 bounded real-model execution
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add the smallest execution-level cap that permits an operator to validate one or a few ActivityExplainer packages before any wider real-model run. This does not change material selection, source evidence, process inference, report rendering, or introduce resumable job machinery.
- Approved inputs: User instruction to inspect small model packets before broader calls; scoped `AGENTS.md`; active Step 06 design; existing `ActivityExplainer` and deterministic scripted-provider tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the current explainer limits bytes per packet but not the number of material packages in one execution. With the verified jshERP plan this would otherwise allow 337 DRAFT/REVIEW pairs to start immediately.
- Added `maxMaterialsToStart` to the internal activity execution request while retaining the existing two-argument scripted-fixture constructor as an unlimited convenience overload.
- The explainer now processes material IDs in deterministic order, starts no more than the chosen number of DRAFT/REVIEW pairs, and records each remaining entry as `NOT_ANALYZED_EXECUTION_CAPACITY` without contacting the Provider.
- Updated the Step 06 design, overall design, scoped rules, and README so the actual small-package execution rule is visible to later implementation Agents.

## Current state

- The focused RED and GREEN are complete. This is a bounded launch control only; selecting later independent batches and merging their results remains a future explicit execution concern.

## Changed files

- `progress/activity-execution-budget.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ExplainActivitiesRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationBudgetTest.java`
- `AGENTS.md`, `README.md`, `docs/DESIGN.md`, and `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Focused business workflow selectors | PASS | 24 scripted tests passed before this change. |
| Execution-cap RED | EXPECTED RED | The missing three-argument request constructor failed as `ACTIVITY_EXECUTION_BUDGET_NOT_IMPLEMENTED`. |
| `ActivityExplanationBudgetTest` | PASS | 2 tests; one selected material made exactly two calls, and the second material was retained as capacity coverage. |

## Decisions

- The cap belongs to the execution request, not the per-packet output-shape profile: it controls how much real work may start, whereas the existing profile controls one request's content bounds.
- Excluded material is recorded as `NOT_ANALYZED` with a concrete capacity reason. It is not sent to the provider and is not treated as a provider failure.

## Blockers

- None.

## Exact next action

- Begin the minimal runtime bootstrap/CLI work that exposes a caller-approved profile; do not issue a full-repository model run until its input count and launch limit are observable.

## Resume checks

- Verify that the test counts actual provider invocations and checks all entry coverage; do not turn a simple execution cap into an automatic retry or recovery subsystem.
