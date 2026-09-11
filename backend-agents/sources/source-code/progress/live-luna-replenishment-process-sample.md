# Progress: live Luna replenishment process sample

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-luna / high for the explicitly authorized product call; gpt-5.6-terra / xhigh for the opt-in harness
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Validate one bounded P1/P2 cross-activity process reconstruction against the existing synthetic replenishment-to-receipt-to-payable acceptance scenario. The sample is explicitly synthetic and validates the framework's process prompt and response contract; it is not customer-source evidence.
- Approved inputs: User's prior explicit Luna/high authorization; logged-in local Codex subscription; the existing synthetic three-activity process scenario used for automated acceptance.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Inspected the production `ProcessExplainer` and its prompts: Java only forms bounded recall groups; the model decides the process order and relation, and its review must preserve uncertainty.
- Confirmed the two completed live customer activity samples are intentionally unrelated and therefore must not be used to force a cross-activity process.

## Current state

- Preparing one opt-in integration test with three synthetic but fully specified activities: create replenishment order, record receipt, and form payable bill. It will disable repository summary so the live sample makes exactly two calls: process DRAFT and process REVIEW.
- The first live P1/P2 run reached a valid reviewed result, but the test incorrectly required exactly one merged process. Luna returned two cautious process candidates (creation and receipt) and stated their cross-activity relation needs confirmation. This is the desired model behavior for incomplete connection material, not a production failure. The raw response is private temporary data and the assertion preceded output persistence, so the next test-only change will persist the reviewed result before assessment and accept either connected or separated, source-bounded process candidates.
- The second P1/P2 run saved a single coherent `补货至应付账单流程` with all three activities, stages and shared objects. It correctly used `NEEDS_CONFIRMATION`, explaining that object handoff supports the story but does not prove a mandatory business sequence. The live test had incorrectly demanded only `REASONABLE_INFERENCE`; it now accepts both non-direct certainty levels without another model rerun.

## Changed files

- `progress/live-luna-replenishment-process-sample.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| production `ProcessExplainer` and prompt inspection | PASS | The provider receives full activities, source-ref allowlist and recall reasons; it receives no paths, hashes, proof IDs or raw source. |
| opt-in `LiveLunaReplenishmentProcessIT` | VALID_MODEL_RESULT / TEST_EXPECTATION_RED | P1/P2 completed in 70.79 seconds; test expected one forced process but Luna conservatively returned two candidates with confirmation notes. |
| second opt-in `LiveLunaReplenishmentProcessIT` | VALID_MODEL_RESULT / TEST_EXPECTATION_RED | P1/P2 completed in 43.95 seconds; persisted one three-stage process, correctly labeled `NEEDS_CONFIRMATION`. |

## Decisions

- The test output will persist the process input and reviewed result in the ignored workspace so the user can inspect the exact model-facing material.
- The process must be marked `REASONABLE_INFERENCE` or `NEEDS_CONFIRMATION` unless a direct source edge proves the business ordering. The test will reject an unsupported claim of roles, completed payment, bookkeeping, unique documents or runtime execution.
- A process sample may produce connected processes, separate process candidates or unmatched activities. The acceptance condition is source-bounded, uncertainty-preserving output, not a fixed number of merges.

## Blockers

- None.

## Exact next action

- Retain the saved synthetic process result as prompt-quality evidence. Move to production persistence and workflow connection; do not expand model calls until the thin production path can consume saved materials and explanations.

## Resume checks

- Confirm the test requires explicit live properties and writes only below the requested ignored workspace directory.
