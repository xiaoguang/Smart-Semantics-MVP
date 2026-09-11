# Progress: repository-analysis-agent

- Status: IN_PROGRESS
- Agent role: Root implementation agent
- Model: gpt-5.6-terra / xhigh (implementation under existing Sol/ultra design)
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Define and implement the sole public run-centric Java seam by composing the completed business workflow; do not add a parallel API or reconstruct source parsing.
- Approved inputs: `docs/DESIGN.md`, `docs/references/foundation-and-publication-contracts.md`, the eight step designs, persisted Step 05 `BusinessFlowsReference`, and the four completed business Modules.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed the internal `BusinessAnalysisWorkflow` fresh-opens a persisted Step 05 publication and runs material, activity, process, and report modules in the approved order.
- Re-ran its direct business-module selector: 13 tests passed with no failures, errors, or skips.
- Confirmed the existing fixed jshERP checkout is a promisor repository and cannot be used as a truthful live-model packet.
- Ran one exploratory public-interface RED and removed it before production implementation. The
  fixed public API requires a large family of lifecycle/query/Trace value types and does not
  improve the core code-to-business-language result; it remains the final integration delivery.

## Current state

- Public `RepositoryAnalysisAgent` and the CLI adapter are not implemented.
- The real persisted Step 05-to-Step 08 composition already exists as the internal
  `BusinessAnalysisWorkflow`, without a second source-analysis path. Broader Step 01–05
  orchestration and all external adapters remain the final integration delivery.

## Changed files

- `progress/repository-analysis-agent.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityPromptContractTest,ProcessExplainerTest,ProcessPromptContractTest,ProcessGroupingTest,ProcessKnowledgeCheckpointTest,RepositorySummaryTest,BusinessReportPublisherTest,BusinessReportCheckpointTest,CodexSubscriptionStructuredProviderTest test` | PASS | 13 tests; 0 failures/errors/skips |
| `/Applications/ChatGPT.app/Contents/Resources/codex login status` | PASS | Logged in using ChatGPT; no model request made |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositoryAnalysisAgentContractTest test` | EXPECTED RED, removed | Missing public seam; deferred as non-business integration work |

## Decisions

- A synthetic fixture remains test-only and is not a substitute for real jshERP semantics.
- The public seam must delegate to `BusinessAnalysisWorkflow`; it must not reimplement material selection, model calls, process grouping, or report rendering.
- Do not create an API/type skeleton merely to satisfy naming. Implement the fixed public Interface
  only together with its run/inspection/artifact behavior, after the business-quality path is
  executable against a complete frozen source.

## Blockers

- Full fixed-repository model acceptance is blocked until a complete non-promisor offline object copy of the already-approved commit is available.

## Exact next action

- Update the core delivery audit, keep the completed four business Modules stable, and resume the
  public-interface work only as the final integration delivery rather than a parallel subsystem.

## Resume checks

- Read this file, verify the 13-test selector, inspect `BusinessAnalysisWorkflow`, and confirm no live Provider request has been made.
