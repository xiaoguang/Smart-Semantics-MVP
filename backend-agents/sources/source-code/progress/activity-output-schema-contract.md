# Progress: activity output schema contract

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Make the ActivityExplainer's external structured-output boundary describe the complete activity response needed by the existing Java validator. This work does not change activity semantics, input packet selection, source handling, storage, Provider fallback, or call count.
- Approved inputs: Step 06 active business-first design; Chinese Activity DRAFT/REVIEW prompts; existing ActivityExplainer response validator; user-approved small clean Luna/high packets.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified that ActivityExplainer has a strict Java-side response validator, but currently sends only `{ "type": "object" }` as the Codex output schema.
- Identified the production break this change prevents: a real Provider can return a syntactically valid arbitrary object without the required complete activity structure, wasting a model request before Java rejects it.
- Added `ActivityOutputSchemaTest`, verified a clean RED caused by the missing required `activities` field in the forwarded schema, then implemented the complete dynamic schema.
- The schema now exposes only the existing business response structure: exact activity fields, certainty enum, string/list limits, and the material-local source-ref allowlist. Java validation remains unchanged and authoritative after the response returns.

## Current state

- The provider boundary now communicates the complete output shape before a request starts. No live model or source scan was performed.

## Changed files

- `progress/activity-output-schema-contract.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityOutputSchemaTest.java`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| implementation inspection | PASS | Current external schema is only `{"type":"object"}`; Java-side validation requires the full activity shape. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityOutputSchemaTest test` | RED | One test failed because the generic schema omitted required `activities`; an initial test count assumption was corrected before the clean RED. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityOutputSchemaTest,ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityPromptContractTest test` | PASS | 5 tests, 0 failures/errors/skips. |
| scoped `spotless:check` | PASS | ActivityExplainer and the new direct test satisfy the project's formatter. |

## Decisions

- The JSON Schema is an external model boundary, not a replacement for Java validation. Java keeps its strict allowlist and source-ref checks.
- The schema will expose business response fields only; it must not expose internal material, flow, path, hash, proof, Provider, or run identity.
- The same profile used by Java to reject oversize output is projected into the model schema. Model noncompliance still fails closed at the Java boundary.

## Blockers

- None.

## Exact next action

- Prepare one explicitly bounded real Luna/high activity packet only after verifying the saved packet and declared call limit; do not use a synthetic fixture as jshERP acceptance.

## Resume checks

- Re-read this progress, inspect `outputJsonSchema` and `validateResponse`, and rerun the five direct activity selectors before modifying the provider boundary.
