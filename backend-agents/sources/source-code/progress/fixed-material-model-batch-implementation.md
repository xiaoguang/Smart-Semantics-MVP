# Progress: fixed material model batch implementation

- Status: COMPLETE
- Agent role: Primary implementation coordinator
- Model: Codex root; delegated roles follow the approved plan
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Implement direct material-checkpoint reopening, independent model-batch ownership, explicit reviewed-job reuse, downstream/CLI wiring, and offline acceptance. Do not rerun JDT or invoke a live model.
- Branch/worktree: `codex/material-model-batch-reuse` / formal `linguan-prototype-v2` checkout

## Completed

- Saved the approved design and related progress as commit `68356b0` and pushed it to `origin/main`.
- Created the dedicated implementation branch from that exact main commit.
- Confirmed the selected offline basis is the first complete 326-material checkpoint; the later 325-material run and its single JDT timeout are outside this implementation.
- Implemented and directly verified the typed M10 checkpoint reader. It performs one canonical reopen and reconstructs material plus coverage without Builder, JDT, or upstream analysis.
- Upgraded the run-output manifest to `analysis-run-output-v3`: source material ownership is separate from model-batch output ownership; foreign third-run output is rejected.
- Added an explicit Activity publisher owner and connected execution-configured Activity jobs to their model-batch run while retaining source-material upstream references.
- Added the prebuilt-material workflow/executor path so later CLI work can skip `BusinessMaterialBuilder` entirely.
- Direct tests passed for material reopening, mixed run-output ownership, explicit Activity publication ownership, Activity coverage, parallel execution, and private job persistence.
- Implemented explicit v2-to-v3 material-state export and direct v3 loading. The first complete jshERP checkpoint was exported in place as a new file: source run `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`, M10 receipt `module-receipt:7228b9f5048bc3dc9dfba94712e3e839fd211d8a1c2c9baebccea6825a89e67b`, 326 materials. No JDT, Builder, upstream analysis or Provider ran.
- Each `activities-sample` and `generate` command now starts a separate model-batch run, writes a frozen `model-job-execution-config-v2`, and keeps the material source run unchanged. Sample output is namespaced by model batch.
- Completed Activity, Process-group, Repository-summary and Report DRAFT+REVIEW pairs now use one strict private v2 record format. Exact matching results can be read and copied as explicit reuse records; partial, mismatched or damaged results are not silently reused.
- Added a full scripted workflow proof: the first two-activity run used six calls; an identical second batch used zero calls and owned new Activity/Knowledge/Report checkpoints; changing only the report profile reused upstream work and made exactly two report calls.
- Synchronized the overall design, Steps 01–08, engine contracts, persistence contracts and CLI run guide to the implemented state.
- Closed all four Important pre-merge review findings: failed preflight now terminates the new batch, reuse binds the quota/account scope, reused Activity and Process results are revalidated and rebuilt from REVIEW, and v3 materials are checked against the configured repository and commit.
- Completed the final full module-local CI on the exact implementation tree: 501 tests, 0 failures, 0 errors, 2 skips; Spotless clean; Enforcer passed; SpotBugs 0 bugs; PMD passed; Maven `BUILD SUCCESS` in 8m03s. The earlier sandboxed attempt had three loopback-bind environment errors; the identical command passed outside that restriction.

## Current state

- Steps 0–5 implementation and offline acceptance are complete. The final read-only review found no remaining Critical or Important issue.
- No JDT scan and no live model call was started.

## Rulings

- Use the existing canonical artifact stores and private result stores; do not add another storage framework.
- A valid completed material checkpoint remains readable when its source run is terminal or failed.
- Only a complete reviewed job is reusable; an isolated DRAFT is diagnostic input to a future full pair, never a resumable half-job.
- The current 326-material run is the offline acceptance checkpoint because it is complete. The second 325-material run is retained only as comparison evidence.

## Exact next action

- The primary agent will commit, push and merge this verified implementation branch.
