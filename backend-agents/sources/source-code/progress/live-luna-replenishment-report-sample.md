# Progress: live Luna replenishment report sample

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-luna / high for the explicitly authorized product call; gpt-5.6-terra / xhigh for the opt-in harness
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Validate one bounded nine-section report DRAFT/REVIEW from the synthetic replenishment-to-payable repository knowledge. The scenario validates business-language report shaping and source-reference mapping; it does not represent a customer repository result.
- Approved inputs: User's prior explicit Luna/high authorization; logged-in local Codex subscription; the existing synthetic replenishment process scenario and four synthetic source-reference snippets.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the report module receives complete activities, process candidates, coverage, confirmation topics and short allowlisted references, then produces deterministic Markdown without another source scan.
- Confirmed the report prompt explicitly requires exactly nine business chapters and prohibits unsupported roles, external effects, payments, bookkeeping and metrics.

## Current state

- Preparing one opt-in integration test. It will make exactly two calls: full-report DRAFT and full-report REVIEW. The test will write the structured report, Markdown and source map below the ignored workspace before assessment.
- The live DRAFT/REVIEW completed in 113.0 seconds and wrote a complete nine-section Markdown report plus structured JSON. It produces business-language objects, activities, field relationships, a formula and questions; it keeps the cross-activity sequence as a confirmation issue and makes no unsupported payment, bookkeeping or runtime-success claim.
- The live result used list items rather than paragraphs for Chapter 4. The harness had incorrectly treated paragraphs as the only valid presentation; it now accepts either business paragraphs or business list items. The result was persisted before this assertion, so no second report call is needed.

## Changed files

- `progress/live-luna-replenishment-report-sample.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| report module and prompt inspection | PASS | Model input is business knowledge plus short refs; Markdown renderer reads reviewed report JSON and source references only. |
| `BusinessReportPublisherTest,BusinessReportCheckpointTest` | PASS | 3 tests, 0 failures/errors/skips before the live sample. |
| opt-in `LiveLunaReplenishmentReportIT` | VALID_MODEL_RESULT / TEST_EXPECTATION_RED | Luna/high DRAFT/REVIEW completed in 113.0 seconds and saved the complete report; the former paragraph-only assertion did not accept a valid item-based Chapter 4. |

## Decisions

- The sample must preserve the process as a `NEEDS_CONFIRMATION` business story, not upgrade it to confirmed execution.
- It must treat the formula as a code-observed amount expression, not as an operating KPI.

## Blockers

- None.

## Exact next action

- Retain the saved report as the first full business-language output example. Next, make the production workflow and minimal user entry point consume persisted Step05 results rather than test-created fixtures; do not expand model calls before that path exists.

## Resume checks

- Confirm the test requires explicit live properties and writes only below the requested ignored workspace directory.
