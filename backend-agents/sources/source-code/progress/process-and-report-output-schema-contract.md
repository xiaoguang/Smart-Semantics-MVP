# Progress: process and report output schema contract

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Apply the same complete structured-output boundary now established for local activities to the ProcessExplainer and BusinessReportPublisher. This work does not alter grouping, business language, report rendering, source selection, checkpoints, public runtime, or Provider calls.
- Approved inputs: Step 07/08 active designs; existing strict Java response validators; user requirement to send small clean inputs and avoid wasting real Luna calls on predictable schema mistakes.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed both ProcessExplainer and BusinessReportPublisher currently forward only a generic object schema despite validating a detailed response shape after the Provider returns.

## Current state

- Process group, optional repository summary, and whole-report DRAFT/REVIEW requests now each carry a separately generated closed JSON Schema. The schemas use the current profile and only the scope-local activity IDs and short source refs which the model may select. Java keeps the final response validator and source-scope authority.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/knowledge/ProcessExplainer.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/ProcessExplainerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/RepositorySummaryTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisherTest.java`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/DESIGN.md`
- `AGENTS.md`
- `progress/process-and-report-output-schema-contract.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| implementation inspection | PASS | Both downstream business Modules still use only `{"type":"object"}` as their provider schema. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ProcessExplainerTest,BusinessReportPublisherTest test` | RED | 3 tests ran; process and report boundary assertions failed because their schemas required no complete fields. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositorySummaryTest,ProcessExplainerTest,BusinessReportPublisherTest test` | PASS | 4 tests; group, repository-summary, and report schemas expose their distinct complete shapes. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityOutputSchemaTest,ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityPromptContractTest,ProcessExplainerTest,ProcessGroupingTest,ProcessKnowledgeCheckpointTest,ProcessPromptContractTest,RepositorySummaryTest,BusinessReportPublisherTest,BusinessReportCheckpointTest test` | PASS | 13 tests, 0 failures/errors/skips; no live Provider call. |
| scoped `spotless:check` and `git diff --check` | PASS | All seven owned Java files are formatted; no whitespace errors. |

## Decisions

- The process group, repository summary, and nine-section report have different response shapes and receive separate schemas rather than one broad permissive schema.
- Schemas may expose short refs and existing reviewed activity identifiers where an output must select from them; they may not expose filesystem paths, hashes, proof chains, or runtime identity.
- The static JSON Schema is an early rejection boundary, not a business-language verifier. The existing Java validators remain authoritative for response parsing, source-reference scope, and required coverage.

## Blockers

- No implementation blocker. A live quality sample remains blocked only by the absence of a complete frozen jshERP Git object set; it is not replaced with a synthetic fixture.

## Exact next action

- Continue with the next business-delivery seam: connect existing technical Step 01–05 publications to the four deep business Modules through the public runtime without weakening technical artifacts or faking a complete fixed-repository run.

## Resume checks

- Before any live call, inspect a real frozen small material package, verify Codex login and declared call limit, and use the new complete schemas as the Provider boundary.
