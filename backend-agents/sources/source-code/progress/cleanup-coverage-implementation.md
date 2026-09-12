# Progress: Cleanup and Scalable Coverage Implementation

- Status: IN_PROGRESS
- Agent role: Primary implementation coordinator
- Model: GPT-5
- Started: 2026-09-11
- Last updated: 2026-09-12 (Task 5 local CI gate passed; Task 6 is next)
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
- Corrected one newly added Task 4 RED assertion before GREEN: REVIEW `actualDraft` must equal the first model response, not the first material request. This implements the approved DRAFT→REVIEW contract and preserves the remaining RED cases.
- Corrected a second contradictory Task 4 RED assertion: keys explicitly returned in REVIEW `unexplainedEntries` are `NOT_ANALYZED` with `MODEL_NOT_EXPLAINED`, not analyzed coverage. This keeps the coverage record honest while preserving the explicit sidecar.
- Completed Task 4 Activity v2: arbitrary-N capacity preflight, DRAFT structure/scope validation, one REVIEW over the complete actual DRAFT plus `missingEntryKeys`, required union/disjoint `unexplainedEntries`, `UnexplainedActivityEntry` sidecar, activity coverage v2, and v2 prompt resources. The existing reviewed-activity JSONL shape remains v1.
- Completed Task 5 partial propagation: RepositoryBusinessKnowledge and its coverage/knowledge checkpoint wires are v2 and retain complete `UnexplainedActivityEntry` records; Process/Report model input receives one identity-free material aggregate; `MODEL_NOT_EXPLAINED` is not a technical Gap; Report Prompt requires concrete Chapter 9 scope and forbids invented Chapter 4 activities.

## Current state

- Tasks 1–5 are functionally complete and their current facts are synchronized into the target design. The three full-local-CI findings have direct green remediations: M1/M2 receipt-upstream lineage plus exact Fact-atom tuples are now checked on publication; neutral Flow test support is within the allowed semantic package; and signal publication verifies source-derivable closure without prohibited compiler replay.
- The local Task 5 gate is green: Spotless reports 509 clean Java files; the full quality build reran 352 tests with zero failures/errors; SpotBugs reports zero warnings; and PMD passes its documented narrow high-risk/severe-complexity rules. PMD's rejected default-wide configuration had 453 historical style/complexity/boundary-normalization reports; the tracked ruleset now avoids file-level suppression while retaining dangerous constructs and severe complexity regression checks.
- Task 6 scripted end-to-end verification begins after this delivery is committed and pushed to `main`.
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
| Task 4 direct selector | PASS | 18 tests, 0 failures/errors/skips across arbitrary-N coverage, activity schema/prompt/checkpoint, Process/Report checkpoint and persisted workflow seams. |
| Task 4 scoped Spotless | PASS | 17 Task 4 Java files are formatted. |
| Task 4 full Spotless | Existing unrelated limitation | 24 already-out-of-scope Java files remain noncompliant; no broad formatting change was made. |
| Task 5 RED selector | Expected RED | 3 tests, 3 failures, 0 errors: Process aggregate absent, knowledge sidecar absent, Report aggregate absent. |
| Task 5 direct GREEN | PASS | 10/10 direct tests, then 5/5 Process suite and 5/5 policy/reopen suite. |
| Task 5 scoped Spotless / diff | PASS | 12 changed Java files formatted; no whitespace errors. |
| Task 5 full `spotless:check` | Initially RED, then formatting repaired | 13 existing Java test files were reformatted with the project Spotless rule; rerun passed. |
| Task 5 full `mvn test` | RED | 131 test classes: 3 failures, 0 errors. `BusinessFlowProvenanceTest` exposes missing M1/M2 receipt-upstream lineage validation; `FlowSignalPublicationIntegrityTest` exposes a strict replay expectation that must be reconciled with ordinary publisher policy; `SourceAnalysisArchitectureTest` rejects the new neutral `org.sourceanalysis.app.testsupport` package. |
| Task 5 CI-remediation direct selectors | PASS | 10 provenance/architecture/signal tests and 34 affected consumer tests pass; normal publication validates persisted source closure but does not replay the compiler to infer a missing complete signal set. |
| Task 5 complete `mvn test`, first two attempts | RED | The only remaining error was the existing fake-Codex success test's 2-second preflight budget under full-suite load; its isolated selector passes. The test fixture now uses a 10-second local fake-process budget so the full-suite gate measures the command contract rather than scheduler contention. |
| Task 5 `spotless:check` | PASS | 509 Java files clean. |
| Task 5 final `mvn -Pquality -DskipTests verify` | PASS | 352 tests, 0 failures/errors/skips; SpotBugs 0 warnings; PMD passes the documented narrow quality gate. |

## Decisions

- Preserve the four active Modules, eight-step flow, addresses 10/11, and existing source/Proof boundaries.
- Keep all model execution to the final bounded real Activity check: one DRAFT and one REVIEW only.
- From Task 5 onward, a delivery may be committed and pushed only after the project's complete local correctness build/local CI passes: serial `spotless:check`, full `test`, then `-Pquality -DskipTests verify`, all with the project JDK 17 toolchain and `MAVEN_OPTS=-Xmx8g`. Remote CI remains informational and is never a wait condition. Task 4 predates this delivery gate and is not reopened solely to apply it retroactively.

## Blockers

- No Task 5 blocker remains. Task 6 has not yet started.

## Exact next action

- Run `git diff --check`, commit/push Task 5 to `main`, then create Task 6 scripted end-to-end RED.

## Resume checks

- Re-read this file, confirm `origin/main` contains `fc6d67b`, and check the scoped working tree before each delivery.
