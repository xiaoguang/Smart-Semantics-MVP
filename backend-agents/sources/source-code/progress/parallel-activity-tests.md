# Progress: parallel-activity-tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED test agent for model-job task-pool and ActivityExplainer integration
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13 (after fatal-context correction and direct selector)
- Scope: Add focused RED tests for bounded parallel activity jobs, per-job DRAFT/REVIEW isolation, immediate private saving, stable aggregation, deduplication, and fatal-stop behavior.
- Approved inputs: `docs/modules/model-job-execution.md`, repository `AGENTS.md`, current ActivityExplainer public seam and checkpoint tests, current model-job configuration work.
- Current branch/worktree: `codex/model-job-parallel-execution` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read scoped instructions, model-job design, ActivityExplainer implementation, activity checkpoint tests, provider seam, and current configuration RED/progress.
- Added `ParallelActivityExplainerTest` with a 13-material blocking scripted Provider. The test requires four DRAFTs to overlap, verifies one DRAFT plus one matching REVIEW per material, stable material/coverage aggregation, and fatal-stop behavior where an already-started valid pair completes while the fatal pair is not retried or reviewed.
- Ran the direct selector: test sources compiled and both tests failed only on the intended missing parallel behavior (`the Activity phase must dispatch multiple jobs before waiting` and `the fatal job must be observed after the bounded initial dispatch`), with 0 test errors.
- Corrected the fatal fixture/assertion to use `activity-job-004`, the fourth initially dispatched job; `activity-job-001` remains the positive already-started valid pair whose REVIEW must complete.

## Current state

- No production changes made. The current ActivityExplainer is a serial single-provider loop and publishes only after its complete loop; the RED integration test exposes the missing bounded scheduler. A separate private-save seam is not yet present in production and remains an implementation handoff concern.

## Changed files

- `progress/parallel-activity-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ParallelActivityExplainerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Existing configuration RED files preserved. |
| `git diff --check` | PASS | No whitespace errors in tracked or newly added test/progress content. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelActivityExplainerTest test` | EXPECTED RED | After the fatal-context correction, test compilation succeeded; 2 tests, 2 assertion failures, 0 errors. Current serial ActivityExplainer never reaches the four-DRAFT barrier or bounded initial fatal dispatch. |

## Decisions

- Tests will use immutable synthetic job inputs and a blocking scripted provider with `Phaser`/latches and bounded timeouts; no sleeps or live model/network calls.
- The test uses the existing `ActivityExplainer.explain` seam directly; no reflection-based coordinator seam is assumed or introduced.
- The existing ActivityExplainer two-call contract remains the integration oracle; prompts and schemas are not changed by this task.
- The test deliberately calls the existing `ActivityExplainer.explain` seam so a parallel implementation must remain compatible with existing activity validation and cannot pass by exercising an unrelated executor alone.

## Blockers

- The production coordinator/private-result seam is not present yet; the selector is intentionally RED until Terra adds it. The current test directly proves the missing parallel dispatch; private-save and duplicate-submission assertions need the coordinator's concrete internal seam before they can be made executable without inventing a public API.

## Exact next action

- Report the test/progress paths and expected RED to the coordinator; Terra can now implement the bounded Activity job seam against an executable public behavior test.

## Resume checks

1. Recheck branch and status before edits.
2. Preserve all other agents' files and do not run Maven concurrently with another agent.
3. After tests are written, run only the direct selector when parent confirms no heavy Maven command is active.
