# Progress: Task 9 four-entry process and report candidate

- Status: IN_PROGRESS
- Agent role: primary implementation and live-candidate operator
- Model: gpt-5.6-terra/xhigh for code; gpt-5.6-luna/high for approved business content
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Continue the approved business-first plan from the completed real four-entry activity candidate through ProcessExplainer and BusinessReportPublisher, without replaying activity generation.
- Approved inputs: frozen Task 8 four-entry activity result; matching persisted business-materials JSONL; fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; user-approved Codex login session and relevant local host permission.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Confirmed the Task 8 output contains four reviewed activities and closed entry coverage for S487, S722, S731 and S898.
- Confirmed the active production chain is `ProcessExplainer` followed by `BusinessReportPublisher`; neither requires another source scan or activity Provider call.
- Added an opt-in test harness that reopens the actual one-file, four-entry activity result and its exact matching `BusinessMaterial` without invoking a Provider.
- The zero-Provider reopen selector passes: four activities, four analyzed entries and refs S487/S722/S731/S898 close correctly.
- Ran the bounded live candidate. Process DRAFT/REVIEW and repository-summary DRAFT/REVIEW all completed and were saved; they produced cautious user-account and session-management interpretations without asserting a mandatory lifecycle order.

## Current state

- The candidate stopped after `BUSINESS_REPORT_DRAFT`: its returned JSON has nine sections but duplicates `指标口径` at section 6 instead of `对象关系`. `BusinessReportPublisher` correctly rejected it as `BUSINESS_REPORT_INVALID` before REVIEW.
- The direct cause is a product contract gap: the generated report JSON Schema constrains section shape but does not constrain the exact title for each position, so the provider can emit an invalid draft despite structured output.

## Changed files

- `progress/task9-four-entry-process-report.md`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticUserAccountGroupChainIT.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only JSON and module inspection | PASS | Four reviewed activities map to four analyzed entries; ProcessExplainer and BusinessReportPublisher are the active modules. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LiveLunaAutomaticUserAccountGroupChainIT …ChainInput=true test` | PASS | 1 enabled zero-Provider reopen test; 1 live test skipped. |
| Live `LiveLunaAutomaticUserAccountGroupChainIT` | TERMINAL CANDIDATE FAILURE | 4 process/summary calls completed, 1 report DRAFT call completed, report DRAFT rejected before REVIEW as `BUSINESS_REPORT_INVALID`; no replay. |
| `mvn -o -t .mvn/toolchains.xml test` | PASS | 361 tests, 0 failures, 0 errors, 1 explicit live-test skip. |
| `mvn -o -t .mvn/toolchains.xml -Pquality -DskipTests verify` | PASS | Full local quality build, including SpotBugs/PMD, completed in 9m13s. |

## Decisions

- Do not replay the completed activity DRAFT/REVIEW.
- Use one frozen package and declare the live process/report call limits before invoking Luna.
- Keep process conclusions conservative: the four user-account endpoints are related context, not a proven lifecycle sequence.
- Live limit: at most 6 ProcessExplainer calls (one or two bounded process groups plus optional repository summary), then exactly 2 report calls; total maximum 8. A started failure is terminal for this candidate.
- The completed Process REVIEW retained four distinct local activities rather than forcing a lifecycle; its DRAFT's two loose process hypotheses are preserved in diagnostics but not promoted over the REVIEW.
- A corrected report schema is a local contract repair, not an architecture change. Any later real report call is a separately saved replacement candidate, never a replay of the terminal draft.
- The report output Schema now binds each `sections` array position to its one fixed number/title pair, matching the Java response validator.

## Blockers

- The report candidate is terminal. Before an explicitly tracked replacement candidate, add a direct RED/GREEN proving each report section position requires its one fixed title.

## Exact next action

- Commit/push the schema and live-harness work, then create a replacement report candidate from the already-completed repository knowledge only.

## Resume checks

- Confirm the Task 8 activity artifact is unchanged and contains four reviewed activities plus four coverage records.
- Run the direct Task 9 selector before any live Provider invocation.
