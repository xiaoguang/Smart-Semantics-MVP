# Progress: model-job-parallel-implementation

- Status: COMPLETE
- Agent role: Primary coordinator
- Model: gpt-5.6-sol
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Implement configurable two-level model-job concurrency for activity and process work, provider isolation, deterministic aggregation, unified CLI configuration, and local verification.
- Approved inputs: `docs/modules/model-job-execution.md` and the user-approved implementation plan in the controlling thread.
- Current branch/worktree: `codex/model-job-parallel-execution` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Documentation-only design commit `0a351e5` was pushed, opened as PR #25, squash-merged, and verified on local and remote `main` as `35098ca`.
- Created the implementation branch from that exact `main` commit.
- Luna/xhigh completed the strict unified YAML RED: 10 tests compiled, 6 expected failures, 0 errors; failures are the missing v2 configuration behavior rather than test compilation defects.
- Step 1 unified configuration is GREEN: strict `repository-run-config-v2` YAML/JSON, split base/model configuration identities, private non-secret execution record, legacy argument rejection, and transitional route fail-closed behavior are implemented. The final hardening uses an atomic no-replace hard-link install; a concurrent conflicting writer now cannot be overwritten.

## Current state

- Step 1: complete. The final selector passes 29 direct tests (12 configuration + 3 launcher + 14 engine); scoped Spotless and `git diff --check` pass. The reviewer found one timing-dependent test construction after confirming production clean; the root replaced it with a barrier race between two content groups and re-ran all 29 tests successfully.
- Step 2: complete. Five direct tests pass: bounded whole-job Activity execution, fatal dispatch stop, injected effective cap, job-specific request identity, immediate run-private reviewed-result saving, and composition-root mapping from the unified configuration.
- Step 3: complete. Codex Subscription now fixes an explicit `CODEX_HOME`, forces ChatGPT authentication, and removes inherited API credentials. The OpenAI adapter uses the official Responses Java SDK with raw JSON Schema and `maxRetries(0)`. Formal Activity routing supports multiple Providers, a global cap, each Provider cap, and multiple API-key clients fixed per whole job.
- Step 4: complete. Independent Process groups use bounded two-level parallel execution, save each reviewed group privately, aggregate in stable group order, and close a barrier before the single repository summary. The formal persisted workflow selects Activity, Process-group, summary, and report Providers from one execution configuration and still emits one report.
- Step 5: complete. Three earlier local-CI attempts exposed and corrected two stale serial test doubles and four static-analysis findings. Read-only review found seven P1 hardening items: fatal start gate, pending-packet retention, complete job fingerprints, duplicate resolved credentials, ChatGPT status verification, incomplete API responses, and local model/effort declaration validation. Each is implemented. The final fresh local CI passes all 483 tests (0 failures/errors, 2 conditional skips), Spotless, Enforcer, SpotBugs with 0 findings, and PMD with 0 findings.

## Changed files

- `progress/model-job-parallel-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Design branch clean before merge. |
| `gh pr merge 25 --squash --delete-branch` | PASS | Local and remote main advanced to `35098ca`. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest test` | EXPECTED RED | 10 tests, 6 failures, 0 errors. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest,RepositoryRunMainTest,EngineConfigurationLoaderTest test` | PASS after corrections | 28 tests, 0 failures/errors; one concurrent install race remains a code-review finding rather than a functional-test failure. |
| Same Step 1 selector after atomic no-replace hardening | PASS | 29 tests, 0 failures/errors; concurrent identical/conflicting installation regression included. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelActivityExplainerTest,ActivityJobExecutionConfigurationTest,RepositoryRunModelJobsConfigurationTest#mapsEffectiveGlobalAndProviderCapIntoActivityExecution test` | PASS | 5 tests, 0 failures/errors after replacing one stale IDE-generated test class. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=OpenAiResponsesStructuredProviderTest,ProcessCodexSubscriptionCommandTest test` | EXPECTED RED | 6 tests, 3 failures: missing API adapter and explicit Subscription authentication isolation. |
| Same Provider selector after implementation | PASS | 6 tests, 0 failures/errors; loopback API failure produced one HTTP request and no SDK retry. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest,ActivityJobExecutionConfigurationTest,ParallelActivityExplainerTest,MultiProviderActivityExecutionTest,ProcessCodexSubscriptionCommandTest,OpenAiResponsesStructuredProviderTest test` | PASS | 24 direct tests; 12 whole Activity jobs reached global 6 / Pro 4 / API 2 and saved 12 reviewed results. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelProcessExplainerTest,ProcessExplainerTest,ProcessGroupingTest,ProcessMaterialRecallTest,RepositorySummaryTest,ProcessKnowledgeCheckpointTest,PersistedBusinessRunExecutorTest,FourEntryBusinessSemanticChainTest,BusinessReportPublisherTest,BusinessReportCheckpointTest test` | PASS | Process parallelism, repository-summary barrier, persisted workflow, and one nine-section report remained green. |
| `mvn -t .mvn/toolchains.xml -Pquality spotless:check verify` | EXPECTED CORRECTION | 479 tests, 1 failure, 0 errors, 2 skipped; the only failure was the stale non-thread-safe schema-capturing test seam, not a production schema failure. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ActivityOutputSchemaTest test` after correcting the test seam | PASS | 1 test, 0 failures/errors; concurrent observations retain every DRAFT and REVIEW schema. |
| Third complete local CI before review hardening | EXPECTED CORRECTION | 479 tests, 0 failures/errors, 2 skipped; SpotBugs then reported four findings in new immutable configuration/private stores. |
| `mvn -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest,ProcessCodexSubscriptionCommandTest,ParallelProcessExplainerTest,ParallelActivityExplainerTest test` | PASS | 23 tests; full process fingerprints, credential/config checks, ChatGPT preflight and concurrency remain green. |
| `mvn -t .mvn/toolchains.xml -Dtest=OpenAiResponsesStructuredProviderTest test` | PASS | 3 loopback tests; HTTP failure is one request and an incomplete response with parseable JSON is rejected. |
| `mvn -t .mvn/toolchains.xml -Pquality -DskipUTs -DskipITs verify` | PASS | Unit/IT execution skipped; SpotBugs 0 and PMD 0, proving the quality-only phase does not repeat tests. |
| `mvn -t .mvn/toolchains.xml -Pquality spotless:check verify` | PASS | 483 tests, 0 failures/errors, 2 conditional skips; Spotless, Enforcer, SpotBugs 0 and PMD 0; 7:04 elapsed. |

## Decisions

- The implementation uses the existing four business modules and one `StructuredModelProvider` seam; no second workflow or public API is introduced.
- Single-activity process groups remain zero-call unmatched activities.
- Automatic tests use scripted/substitute providers only; no real model call is authorized by this plan.
- The model execution configuration digest covers every normalized non-secret field, including local paths and the Codex executable; only resolved credential values are excluded.
- Stable routing binds a whole DRAFT-to-REVIEW job before dispatch. Multiple API key references within one Provider rotate by stable job ordinal but share the Provider's single quota cap.
- Repository summary and report remain singleton jobs and select the first Provider in their configured routes.
- A configured model name is checked for a safe finite syntax and reasoning effort for the adapter-supported closed set. Actual account/model availability remains a single Provider request failure because it cannot be proven offline without making that request.

## Blockers

- None.

## Exact next action

- Commit and push the verified implementation branch, open and squash-merge its PR to main, then verify local and remote main match.

## Resume checks

1. Confirm branch is `codex/model-job-parallel-execution` and merge base is `35098ca`.
2. Read `docs/modules/model-job-execution.md` and this file.
3. Inspect live agents before assigning work; do not run heavy Maven commands concurrently.
