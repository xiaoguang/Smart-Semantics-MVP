# Progress: End-to-end Task 1 activity reliability production repair

- Status: COMPLETE
- Agent role: Task 1 production-code implementer (tests owned by the assigned Terra agent; design/debug/review owned by root)
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23
- Scope: Task 1 only; inspect and minimally repair the named Activity reliability production owners plus their current-fact owner documentation after confirmed RED evidence
- Owning plan: docs/plans/end-to-end-business-delivery-implementation-plan.md, Task 1
- Approved inputs: task-1-brief.md; approved design section 5; activity-explanation integration/large-material contracts; deterministic tests and frozen fixtures only
- Current branch/worktree: codex/step05-activity-full-generation at ccd5a96; preserve all pre-existing changes

## Completed

- Read the repository, prototype, backend-agent, and source-code AGENTS.md instructions.
- Read the complete Task 1 bounded brief and recorded the no-live/no-customer-build/no-product-generation constraints.
- Read the approved design and Activity/model-job owner contracts plus all named production owners.
- Received confirmed RED evidence for damaged saved reading packets, free-text Provider misclassification, and the reading unknown-list range failure; partial-slice continuation was an approved review gap, while the initial retry/capacity failures also contained test-fixture scope mistakes.
- Implemented the bounded Task 1 production repair without changing tests: exact plan/stage/aggregate reopen validation, packet-local slice continuation, hard saved-state/source/storage boundaries, deterministic local capacity classification, truly bounded private Codex diagnostics, and free-text UNKNOWN classification.
- Updated current-fact owner documentation; Task 2 plan-v2 and Task 3 coverage-v4/reuse-only remain explicitly out of scope.

## Current state

- Task 1 production, owner docs, progress, and code report are frozen.
- Root's final formatted snapshot passed the expanded 12-class, 79-test regression with no failures, errors, or skips.
- No Maven, live model, JDT/Builder, customer-source build, commit, or push was run by this agent.

## Changed files

- progress/e2e-activity-reliability-code.md (this agent's handoff only)
- src/main/java/org/sourceanalysis/app/adapter/provider/ProcessCodexSubscriptionCommand.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityJobCoordinator.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinator.java
- src/main/java/org/sourceanalysis/app/runtime/modeljob/PrivateModelJobResultStore.java
- docs/modules/model-job-execution.md
- docs/modules/activity-explanation/integration-contracts.md
- docs/modules/activity-explanation/large-material-reading.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` (formal source-code checkout) | PASS | Pre-existing plan/progress edits and Terra progress file observed; no production/test edit by this agent |
| Root isolated baseline / Terra RED runs | RED confirmed | Damaged saved packet returned cached Activities; free-text `rate limit` became RATE_LIMITED; reading unknown mutation caused invalid subList range. Initial slice tests also exposed fixture identity/capacity defects and are not recorded as production RED evidence. |
| `git diff --check` after frozen main patch | PASS | No whitespace errors |
| Root nine-class direct regression | 53 PASS / 1 fixture failure / 0 errors | Missing-plan/missing-REVIEW hard failures, auth partial preservation, free-text UNKNOWN, stage retry, and generic store compatibility passed. Remaining two-entry S3 failure was a fixture dependency-closure error; production capacity logic unchanged. |
| Root formatted twelve-class direct regression | 76 PASS / 0 failures / 0 errors; Spotless PASS | Corrected fixture passed; root added three baseline-impact classes. Elapsed time: 56.825 seconds. Independent review then identified two previously untested saved-claim gaps, so this is not final Task 1 closure evidence. |
| Terra follow-up RED | 15 tests / 3 failures / 0 errors | Empty-plan packet ID corruption, required/shared saved-plan inconsistency, and a completed slice missing REVIEW success did not throw. |
| Implementer follow-up GREEN | 15 PASS / 0 failures / 0 errors | Exact `ActivityLargePacketFormalEntryTest` command passed; BUILD SUCCESS in 1:22, test time 4.534 seconds. |
| Root final formatted direct regression | 79 PASS / 0 failures / 0 errors / 0 skipped; Spotless PASS | Fresh post-review run across 12 directly covering classes; root reported 1:33. |
| Final `git diff --check` and Surefire summary read | PASS | Twelve retained class summaries sum to 79 and each reports zero failures/errors/skips. |

## Decisions

- Task 1 is bounded to the explicit reliability defects. Plan-v2 final/superseded sets, coverage-v4, reuse-only expansion, and new protocols remain out of scope.
- Codex CLI free-text stdout/stderr is private diagnostic only and maps to UNKNOWN; verified typed Provider reasons elsewhere retain their existing behavior.
- A completed scoped aggregate is a claim on its exact nonempty plan and successful slice stages. Missing/corrupt dependencies are hard errors and cannot trigger a Provider fallback. A legal empty plan with no business result remains eligible for explicit reselection.
- Packet-local slice failures continue later independent slices and return one partial terminal outcome carrying all successful slices. Binding auth/config stops the binding but preserves earlier verified partials; source/storage corruption remains hard.

## Blockers

- None for Task 1.
- Maven verification remains root-controlled; this agent has not requested or used a build slot.

## Exact next action

- No Task 1 action. Root may checkpoint the frozen local work; Task 2 starts separately from Terra-owned RED evidence.

## Resume checks

- Re-read this progress file and `git status --short`.
- Confirm root has returned direct regression results and explicitly reopened `src/main` before any production edit.
- Confirm root has granted the Maven build slot before running any Maven command.

## Plan closeout destinations

- Durable decisions: docs/modules/activity-explanation/integration-contracts.md and/or large-material-reading.md, limited to corrected current implementation facts
- Remaining issues: Task 2 plan-v2 final/superseded state and Task 3 coverage-v4/reuse-only obligations stay in the owning plan/backlog
- Verification and output references: .workspace/end-to-end-business-delivery-20260923/task-1-code-report.md

Keep this handoff while the plan is active. At whole-plan closeout, consolidate
the information above into its durable destinations and remove the temporary
task file; do not archive a second copy of the progress record.
