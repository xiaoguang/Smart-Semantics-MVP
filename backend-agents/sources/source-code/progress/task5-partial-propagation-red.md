# Progress: Task 5 partial propagation RED

- Status: COMPLETE
- Agent role: Task 5 RED test worker
- Model: GPT-5 / Luna xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add only new failing tests for concrete Activity v2 unexplained-entry propagation through ProcessExplainer and BusinessReportPublisher.
- Approved inputs: Task 5 of `docs/plans/coherent-code-context-implementation-plan.md`; current `docs/DESIGN.md`; source-scoped AGENTS.md; no production-code or existing-test edits authorized.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`
- Additional execution gate: before any future commit from Task 5 onward, the parent must run the complete correct local build/CI and see it pass; this RED-only work must not commit.
- Local CI gate defined by parent: after Task 5 GREEN, run serially with `MAVEN_OPTS=-Xmx8g`: `mvn -t .mvn/toolchains.xml spotless:check`, `mvn -t .mvn/toolchains.xml test`, and `mvn -t .mvn/toolchains.xml -Pquality -DskipTests verify`.

## Completed

- Read the source-scoped AGENTS.md, target design, Steps 07/08, Task 5 plan, and parent cleanup/coverage progress.
- Confirmed Activity v2 already exposes program-side `UnexplainedActivityEntry` records, while ProcessExplainer and BusinessReportPublisher currently project only counts.
- Created this task-owned progress file before adding tests.

## Current state

- Existing ProcessExplainer repository input contains only `notAnalyzedEntries`; RepositoryBusinessKnowledge has no unexplained-record field.
- Existing BusinessReportPublisher clean input contains only coverage counts; Chapter 9 behavior is therefore not constrained to concrete HTTP method/path and reason.
- Added `Task5PartialPropagationRedTest` with three focused RED behaviors; it uses only scripted Providers and the public ProcessExplainer/BusinessReportPublisher seams.
- First executable RED run compiled the new test and failed 2/3 tests: repository input lacked `unexplainedActivityEntries`, and the report-side knowledge seam had no future record-bearing constructor; the non-escalation assertions were green and are now tied to the required program-side accessor so the full test class remains RED.
- Final executable RED run compiled and ran all three tests: 3 failures, 0 errors, 0 skipped. The failures are feature-shaped, not compile/setup errors.

## Changed files

- `progress/task5-partial-propagation-red.md` — task-owned progress record.
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/Task5PartialPropagationRedTest.java` — new Task 5 RED-only test class.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `MAVEN_OPTS=-Xmx8g mvn -t .mvn/toolchains.xml -Dtest=Task5PartialPropagationRedTest test` | RED setup correction | Initial test compile exposed an AssertJ wildcard assertion at line 68; no production/test behavior ran. Narrowed the test helper to `List<UnexplainedActivityEntry>` before rerun. |
| `MAVEN_OPTS=-Xmx8g mvn -t .mvn/toolchains.xml -Dtest=Task5PartialPropagationRedTest test` | RED setup correction | Second compile exposed a mutable local counter captured by the scripted-provider lambda at line 275; changed only the new test counter to an array before rerun. |
| `MAVEN_OPTS=-Xmx8g mvn -t .mvn/toolchains.xml -Dtest=Task5PartialPropagationRedTest test` | Expected RED | Compiled and ran 3 tests: 3 failures, 0 errors. Process repository input has no `unexplainedActivityEntries`; `RepositoryBusinessKnowledge` exposes no `unexplainedActivityEntries()` accessor; report input has no aggregate (size 0 instead of 1). Failures are the intended missing downstream propagation seams. |
| `git diff --check` and trailing-whitespace scan of both task files | PASS | No whitespace errors. Existing concurrent parent change in `progress/cleanup-coverage-implementation.md` was preserved untouched. |

## Decisions

- Add a new focused test class only; do not modify production code or existing tests.
- Use deterministic scripted Providers and existing public ProcessExplainer/BusinessReportPublisher seams.
- Assert material-level aggregation, program-side retention of global IDs, MODEL_NOT_EXPLAINED non-escalation, concrete Chapter 9 disclosures, and no fabricated Chapter 4 activity.

## Blockers

- GREEN is intentionally not part of this subtask; parent must implement the approved Task 5 seams and then run the newly added selector plus the newly announced complete local CI gate before any commit.

## Exact next action

- Parent next: use the three RED failures to implement only the approved Process/knowledge/report propagation seams; after GREEN, run serial local CI with `MAVEN_OPTS=-Xmx8g` (`spotless:check`, full `test`, then `-Pquality -DskipTests verify`) before any Task 5 commit.

## Resume checks

- Run `git status --short`; preserve unrelated changes.
- Verify no production file or existing test was changed.
- Final RED tree: only this progress file and the new test are owned by this subtask; the pre-existing parent modification to `progress/cleanup-coverage-implementation.md` remains untouched.
