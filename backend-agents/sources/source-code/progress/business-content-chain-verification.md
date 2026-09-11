# Progress: Business content chain verification

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh for production changes; Luna-style TDD verification in the current task
- Started: 2026-09-11 12:17 UTC
- Last updated: 2026-09-11 12:55 UTC
- Scope: Verify and, only where a direct public seam loses content, repair the persisted business-material → Activity DRAFT/REVIEW → Process → nine-section report chain using a scripted Provider. Diagnose the Codex subprocess boundary without any real Provider call.
- Approved inputs: User-approved "连贯代码材料到九章业务文档：现有实现修正计划"; Step06–08 design; frozen fixtures and scripted Provider only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Created this progress record before inspecting or changing the Phase 3 production seam.
- Found a direct content loss in `ProcessExplainer`: its Provider-facing activity projection omitted participants, terms, certainty and scope limitations even though reviewed activities had them.
- Added a public ProcessExplainer RED showing the four missing fields, then projected all four fields with the rest of the reviewed activity.
- Extended the persisted Step05-to-report scripted test to prove complete Activity REVIEW is passed into process and report DRAFT inputs, each REVIEW receives the actual full DRAFT, and the final Markdown preserves the reviewed purpose, condition, rule and question.
- Added a fake successful Codex CLI process test. It proves the Java command preflight, schema/output-file argument protocol, prompt stdin and structured-output read path without a logged-in model request.
- Updated Step07, Step08 and overall implementation audit to record the verified content-preservation behavior.

## Current state

- Phase 3 is complete. The direct production seam preserves full reviewed activity content across process and report tasks; fake CLI coverage verifies the subprocess protocol. No product model request has started.

## Changed files

- progress/business-content-chain-verification.md
- src/main/java/org/sourceanalysis/app/analysis/knowledge/ProcessExplainer.java
- src/test/java/org/sourceanalysis/app/analysis/knowledge/ProcessMaterialRecallTest.java
- src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java
- src/test/java/org/sourceanalysis/app/adapter/provider/ProcessCodexSubscriptionCommandTest.java
- docs/analysis-steps/07-repository-knowledge.md
- docs/analysis-steps/08-nine-section-document.md
- docs/DESIGN.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=ProcessMaterialRecallTest test` | Expected RED then PASS | RED identified the four omitted reviewed-activity fields; GREEN proves they are visible to the process DRAFT packet without exposing source paths. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=PersistedBusinessRunExecutorTest test` | PASS | 2 tests: persisted Step05 fixture executes two Activity DRAFT/REVIEW pairs, one process DRAFT/REVIEW pair and report DRAFT/REVIEW; reviewed business content reaches Markdown. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=ProcessCodexSubscriptionCommandTest,CodexSubscriptionStructuredProviderTest test` | PASS | 5 tests: fake Codex process produces structured output; Provider keeps model identity and canonical JSON. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=ActivityExplainerTest,ActivityExplainerDirectEntryContextTest,ProcessMaterialRecallTest,BusinessReportPublisherTest,PersistedBusinessRunExecutorTest,ProcessCodexSubscriptionCommandTest,CodexSubscriptionStructuredProviderTest test` | PASS | 12 tests, 0 failures/errors; material, activity, process, report and adapter seams agree. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | One edited Java test formatted. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 590 Java files clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The test must exercise the formal persisted/public module boundary, not construct a private object graph that bypasses material persistence.
- A response with only labels, keys or a shortened review is not adequate: the final report must still expose reviewed purpose, conditions, steps, result, rules, formulas and pending questions when present.
- Activity field completeness is owned by the Provider packet projection, not by a Java business-language parser. Java only preserves the fields and validates their source-ref scope.

## Blockers

- None.

## Exact next action

- Read the Step06–08 contracts and inspect existing ActivityExplainer, ProcessExplainer and BusinessReportPublisher public seams; then add one focused scripted-provider RED for a reviewed activity whose narrative and business details must survive into the report.

## Resume checks

- Read this file, inspect the frozen-source availability and `git status --short`, then create a separate Phase 4 progress record before zero-provider whole-repository material planning.
