# Progress: Task 4 activity coverage v2 RED

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Public ActivityExplainer RED tests for arbitrary entry counts, one REVIEW closure, capacity admission, and coverage v2 unexplained-entry persistence.
- Approved inputs: Task 4 contract in the active cleanup implementation plan; current ActivityExplainer, material records, scripted-provider seam, and checkpoint store.
- Current branch/worktree: shared implementation worktree `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Added `ActivityCoverageV2ContractTest` with five focused public-seam tests:
  - N=4 DRAFT E1/E2 enters exactly one REVIEW with complete draft and sorted E3/E4 missing keys, then closes coverage through explicit unexplained entries and a v2 checkpoint sidecar.
  - N=12 preserves exact E1…E12 mapping, including E10/E11/E12, without prefix matching.
  - Incompatible activity capacity produces entry coverage before any Provider call.
  - Missing or null REVIEW `unexplainedEntries` and incomplete REVIEW coverage are fatal after the single allowed REVIEW, never a third call.
- The fixture keeps activity membership many-to-many by returning one activity over the full entry set; it does not require one activity per entry.

## Current state

The current implementation validates DRAFT coverage before REVIEW, accepts only the `activities` top-level field, and publishes activity coverage v1. The RED tests will make those missing behaviors observable without changing production code or existing tests.

The new public-seam test has been added. It uses the existing persisted BusinessMaterial fixture and a deterministic provider that records both serialized requests and call count.

## Changed files

- `progress/task4-activity-coverage-v2-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityCoverageV2ContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityCoverageV2ContractTest test` | EXPECTED RED | 5 tests, 5 failures, 0 errors, 0 skipped. Current DRAFT rejects E1/E2 omissions as `ACTIVITY_DRAFT_INVALID`; current capacity path calls the Provider; current N=12 REVIEW rejects the missing `unexplainedEntries`; current coverage v1 has no v2 unexplained-entry result/accessor. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Tests will use a small material cloned from the existing public graph/material fixture, with deterministic entry IDs so N=4 and N=12 are exercised without customer-source or model calls.
- The scripted provider will inspect serialized Review input and count calls; no live Provider, network, customer build, or API key is allowed.
- Green must preserve many-to-many activity membership; tests will not require one activity per entry.
- The minimum GREEN surface is `ActivityExplainer` request/response validation and Review packet construction, `ActivityExplanationResult` plus `UnexplainedActivityEntry`, and checkpoint publisher/reader coverage v2 serialization; prompt/schema resources may be updated only as needed to express the same public behavior.

## Blockers

## Exact next action

Parent agent should use the new test and this progress as the Task4 RED deliverable; production GREEN must be limited to the listed public behavior.

## Resume checks

- Recheck this file and `git status --short` before further edits.
- Do not modify the existing Activity tests, production sources, design documents, or another Agent's progress file.
