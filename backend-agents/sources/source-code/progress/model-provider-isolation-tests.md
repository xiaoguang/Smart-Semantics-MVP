# Progress: model-provider-isolation-tests

- Status: COMPLETE
- Agent role: Step 3 provider-isolation RED
- Model: gpt-5.6-sol
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Define direct observable tests for the OpenAI Responses adapter and Codex Subscription login-context isolation. Do not modify production code before the RED is observed.
- Approved inputs: `docs/modules/model-job-execution.md` and the user-approved model-task parallel execution plan.
- Current branch/worktree: `codex/model-job-parallel-execution` in the formal `source-code` checkout.

## Completed

- Read the model-job design, existing Provider seam, Codex process adapter, and official OpenAI Java SDK structured-output/retry guidance.
- Named the production breaks the tests must catch: implicit SDK retries, missing raw JSON Schema, inherited API-key authentication in Subscription mode, and missing explicit Codex login context.
- Added direct tests for a raw-schema Responses request, one-request failure behavior, and a child-JVM Subscription process with an inherited API key.
- Observed the expected RED outside the loopback sandbox: two failures name `OPENAI_RESPONSES_PROVIDER_NOT_IMPLEMENTED`; the Subscription isolation probe exits non-zero because the explicit Codex-home profile/clean environment path does not exist yet.

## Current state

- The direct Provider boundary is GREEN. Subscription mode uses the explicit login context without inherited API credentials, and the Responses adapter sends raw JSON Schema with one request on failure.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -q -t .mvn/toolchains.xml -Dtest=OpenAiResponsesStructuredProviderTest,ProcessCodexSubscriptionCommandTest test` | EXPECTED RED | 6 tests; 3 failures, 0 errors. Two missing API adapter failures and one missing explicit Subscription isolation path. |
| Same selector after GREEN | PASS | 6 tests, 0 failures/errors. |

## Exact next action

- No further action for this bounded Provider test task.
