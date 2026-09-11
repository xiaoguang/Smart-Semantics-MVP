# Progress: automatic material real packet validation

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: No model used; static frozen-source validation only
- Started: 2026-09-11 03:18 NDT
- Last updated: 2026-09-11 03:23 NDT
- Scope: Re-run the existing opt-in fixed-jshERP direct-entry material planner against the local
  fixed commit, with zero Provider calls and no customer Maven/application execution, to inspect
  a current automatic packet after the bounded syntax-outline improvements.
- Approved inputs: The already approved fixed local jshERP commit
  `8c30ce7861570458920175e200bb2a6442713580`; no network refresh or source substitution.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Verified that the local checkout contains the approved commit object and a 719-path tree listing.
- Ran the existing opt-in, zero-Provider material planner successfully against that commit. It
  produced 337 `BUSINESS_MATERIAL` records and 337 matching `ENTRY_COVERAGE` records; all are
  `ENTRY_SOURCE_FALLBACK`, so this run does not claim that technical Flows were compiled.
- Inspected the current automatic `DepotHeadController#batchSetStatus` packet. It includes the
  HTTP entry, request `status`/`ids`, the controller-to-service call, the opening service method
  window, and neutral syntax observations. It does **not** include the later guarded update and
  inventory branches needed for a complete audit/unaudit business explanation.

## Current state

- The earlier validation result named `LOCAL_GIT_PROMISOR_UNSUPPORTED`; this direct run instead
  completed capture/planning from the approved local commit. The first invocation stopped before
  capture because its temporary output directory was outside the module's ignored `.workspace`;
  the successful rerun used an absolute directory inside `.workspace`.
- The automatic packet is adequate to describe that a batch audit/unaudit request reaches the
  service, but is not yet adequate to substantiate the richer business explanation obtained from
  the manually curated live-Luna sample. This is a product gap, not a provider-quality result.

## Changed files

- `progress/automatic-material-real-packet-validation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| local Git commit/tree read | PASS | Approved commit resolves; tree lists 719 tracked paths. |
| fixed-repository material planner, output under `/private/tmp` | PRECONDITION RED | Rejected before source read: output workspace must be inside this module's ignored `.workspace`. |
| fixed-repository material planner, absolute ignored workspace | PASS | 337 materials and 337 entry-coverage records; zero Provider calls and no customer build. |
| automatic DepotHead packet inspection | GAP FOUND | Omits later service guards/update/inventory branches required for a complete business explanation. |

## Decisions

- This is an inspectable materials-only preflight, not a product generation, model call, or
  complete Step 01–05 acceptance claim.
- Do not use the earlier manually curated Luna result as proof that this automatic packet is ready
  for a real model call. The next implementation work must improve selection of semantically
  important service regions or declare the material incomplete.

## Blockers

- Automatic service-method selection currently preserves the opening and tail windows, but does
  not select the middle state-update and inventory branches for this real DepotHead method.

## Exact next action

- Write one focused public-seam RED that requires a material packet to retain semantically
  relevant guarded mutation branches from an already selected long service method, then implement
  the smallest generic selection rule. Do not call Luna until the resulting packet is inspected.

## Resume checks

- Confirm the next change remains generic (no DepotHead/business dictionary), preserves source
  caps, and is verified by a direct material-builder selector before another fixed-repository
  inspection.
