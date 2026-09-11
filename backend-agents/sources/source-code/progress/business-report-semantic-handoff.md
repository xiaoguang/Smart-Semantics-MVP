# Progress: business-report-semantic-handoff

- Status: COMPLETE
- Agent role: Root implementation agent
- Model: gpt-5.6-terra / xhigh (production correction after existing business-first design)
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Ensure the report-authoring packet retains business-bearing activity and process fields; no source-parser, persistence, public API, or live Provider change.
- Approved inputs: Step 07 repository knowledge and Step 08 report design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Identified an exact semantic handoff to test: the document-authoring packet must retain reviewed terms and certainty, in addition to activities, conditions, rules, outcomes and process stages.

## Current state

- The RED proved that activity terms were silently dropped from the report-authoring packet.
- The smallest correction preserves reviewed `terms` and `certainty` in the clean activity packet;
  both are business content and neither exposes program transport identity.
- The next bounded check preserves a process stage's reviewed activity name without exposing the
  internal activity identifier to the report model.
- The RED showed the report packet omitted the stage-to-activity link. The correction maps the
  internal activity ID to its already-reviewed human-readable name and rejects a stale stage
  reference instead of sending an unresolvable relation to the model.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisherTest.java`
- `docs/analysis-steps/08-nine-section-document.md`
- `progress/business-report-semantic-handoff.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportPublisherTest test` | RED | 2 tests; 1 failure: reviewed terms absent from the DRAFT packet |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportPublisherTest,BusinessReportCheckpointTest test` | PASS | 3 tests; 0 failures/errors/skips |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityPromptContractTest,ProcessExplainerTest,ProcessPromptContractTest,ProcessGroupingTest,ProcessKnowledgeCheckpointTest,RepositorySummaryTest,BusinessReportPublisherTest,BusinessReportCheckpointTest,CodexSubscriptionStructuredProviderTest test` | PASS | 13 tests; 0 failures/errors/skips |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportPublisherTest test` | RED | 2 tests; 1 failure: process stage omitted its activity name |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportPublisherTest,BusinessReportCheckpointTest test` | PASS | 3 tests; 0 failures/errors/skips |

## Decisions

- This is a report-input completeness check, not a Java attempt to validate Chinese meaning.
- Keep the model packet path/hash/proof-free; business terms and certainty are content, not transport identity.

## Blockers

- None.

## Exact next action

- Resume public runtime integration or real-source acceptance; do not add further fields without a
  concrete report-input information-loss test.

## Resume checks

- Read this file, inspect `BusinessReportPublisher.cleanKnowledge`, and run only the direct report selector.
