# Progress: Cleanup and Scalable Coverage Implementation

- Status: IN_PROGRESS
- Agent role: Primary implementation coordinator
- Model: GPT-5
- Started: 2026-09-11
- Last updated: 2026-09-11 (Task 3 Capsule wire reduction complete; known v1 coverage failure recorded)
- Scope: Approved cleanup, arbitrary-N Activity coverage, Knowledge/Report partial propagation, and bounded live Activity validation
- Approved inputs: User-approved implementation plan; baseline commit `fc6d67b` pushed to `origin/main`; current design contracts
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Saved and pushed the pre-implementation baseline as `fc6d67b`; no test or CI result is implied.
- Read the approved cleanup/coverage design, current implementation handoff, and directly affected implementation seams.
- Confirmed the old interpretation chain is isolated from the active four-Module workflow and that Activity v1 rejects DRAFT coverage shortfalls before REVIEW.
- Synchronized the approved v2 coverage contract, exact single-material live-validation task, and current authorization boundaries into the implementation guidance.
- Committed and pushed that design delivery as `4c65f47` (`docs: align cleanup implementation contract`).
- Received the Task 1 neutral-testsupport RED: `BusinessFlowTestSupportContractTest` compiled and failed as expected because the new neutral helper does not yet exist (1 failure, 0 errors).
- Completed Task 1 fixture migration: the 14 active consumers and retained Flow/Capsule coverage test now use neutral testsupport; no legacy source/test package has been deleted yet.
- Investigated the two direct-selector failures. Both enter `ActivityExplainer.validateResponse` before Process/Report work and fail `ACTIVITY_DRAFT_INVALID`; the migrated tests differ from HEAD only in the helper import/call, and the neutral helper preserves the prior Step05 publication algorithm. This is the approved Task 4 v1 coverage limitation, not a Task 1 fixture regression.
- Completed Task 2 retirement: deleted the 78 obsolete interpretation production classes and 14 dedicated tests, removed their Step06 addresses 1–9, canonical file/schema branches and fixture policies, and retained only the current Step06 addresses 10/11, `ModelRuntimeIdentityV1`, materials, activities, and `analysis.knowledge.ProcessExplainer`.
- Completed Task 3 Capsule wire reduction: removed the two registry proposal basis fields, advanced only capsule-projection v8→v9 and public evidence-capsule v6→v7, and verified new publication/reopen while old policy lookup rejects v8/v6.

## Current state

- Task 3 GREEN is complete. The next work unit is Activity v2: arbitrary N entries, one complete REVIEW and explicit unexplained-entry closure; the known v1 coverage failure remains intentionally unfixed until that work.
- No customer source scan or Provider call has started in this work unit.

## Changed files

- `docs/DESIGN.md` and `docs/analysis-steps/06-flow-interpretation.md` — current v2 target behavior (Astra/ultra draft retained).
- `docs/plans/coherent-code-context-implementation-plan.md` — implementation state, Task 7, and approved execution boundary.
- `docs/plans/code-cleanup-and-scalable-activity-coverage-design.md` — matching current execution state.
- `progress/cleanup-coverage-implementation.md` — this task-owned continuity record.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git ls-remote origin refs/heads/main` | PASS | Baseline branch was at `6a191017` before the approved push. |
| `git push origin HEAD:main` | PASS | `fc6d67b` is now `origin/main`. |
| `git push origin HEAD:main` | PASS after DNS retry | `4c65f47` is now `origin/main`. |
| `git diff --check` | PASS | Documentation synchronization has no whitespace errors. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest test` | Expected RED | 1 failure, 0 errors: neutral support seam absent. |
| Task 1 direct combined selector | PARTIAL | 12 current test classes plus support/coverage green; `BusinessReportCheckpointTest` and `ProcessKnowledgeCheckpointTest` stop at existing `ACTIVITY_DRAFT_INVALID`. |
| Targeted source inspection | PASS | Identified v1 coverage early-fail, count-only downstream input, old-module registrations, and capsule v8/v6 consumers. |
| Task 2 retirement selector | PASS | 1 guard test; old 1–9 registrations and legacy class are gone while 10/11 and runtime identity remain. |
| Task 2 direct regressions | PASS / known unrelated limitation | 26 material/activity-related tests pass; Process/Report checkpoint selectors only hit the pre-existing Task 4 `ACTIVITY_DRAFT_INVALID`. |
| `git diff --check` | PASS | Task 2 deletion and registration change have no whitespace errors. |
| Task 3 Capsule wire selectors | PASS | 1 wire test plus projector, publisher, Flow coverage and focused provenance selectors pass with v9/v7. |
| Full `BusinessFlowProvenanceTest` | Existing unrelated failure | 5/6; Fact atom replay mutation is not rejected by `FlowPublicationSpecifier.validateFactOrigins`. This task did not alter or hide it. |

## Decisions

- Preserve the four active Modules, eight-step flow, addresses 10/11, and existing source/Proof boundaries.
- Keep all model execution to the final bounded real Activity check: one DRAFT and one REVIEW only.

## Blockers

- None.

## Exact next action

- Create Task 4 RED coverage for arbitrary N entry keys, DRAFT coverage shortfall entering one complete REVIEW, and required explicit unexplained-entry closure.

## Resume checks

- Re-read this file, confirm `origin/main` contains `fc6d67b`, and check the scoped working tree before each delivery.
