# Progress: activity explanation checkpoint

- Status: COMPLETE
- Agent role: Root coordinator; TDD continuation for the Step 06 business-first route
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Persist the already-reviewed ActivityExplainer output and its denominator coverage through the existing canonical module store, then fresh-reopen it. No new business inference, Provider behavior, retry, or legacy interpretation route.
- Approved inputs: Scoped `AGENTS.md`; Step 06 sections 5.3, 5.6 and 5.7; existing canonical module-store contract; deterministic persisted fixture and scripted Provider only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified the prior coverage slice: the direct ActivityExplainer selectors are green.
- Identified the only storage seam needed for this slice: a new Flow Interpretation module publication with two canonical payloads ordered as `activity-coverage.json`, `activity-explanations.jsonl`.
- Added a public-seam RED that requires the two-payload checkpoint after scripted DRAFT/REVIEW execution. The RED first exposed the absent constructor, then exposed the missing registered artifact policies.
- Added the smallest registered module/storage contract and a package-local publisher. `ActivityExplainer(provider, store)` now attaches a content-addressed checkpoint to the result; the existing one-argument constructor remains an intentionally unpersisted in-memory seam for bounded tests.
- Fresh reopening now verifies the two exact file names and artifact types, and reads the complete reviewed business-language payload plus entry coverage from disk.

## Current state

- The aggregate checkpoint is green. Per-material immediate saving and protected raw model-call receipts remain a later, separate resilience slice; no retry or live-provider behavior was introduced.

## Changed files

- `progress/activity-explanation-checkpoint.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationCheckpointPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationResult.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisStepModuleAddress.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationCheckpointTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| ActivityExplainer direct selectors | PASS | 2 tests; 0 failures/errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplanationCheckpointTest test` | EXPECTED RED | 1 assertion failure: `ACTIVITY_EXPLANATION_CHECKPOINT_NOT_IMPLEMENTED`. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplanationCheckpointTest,ActivityExplanationBudgetTest,ActivityExplainerTest test` | PASS | 3 tests; 0 failures/errors/skips. |
| Scoped Spotless and `git diff --check` | PASS | No formatting or whitespace findings on this slice. |

## Decisions

- Use the existing receipt-last canonical store rather than inventing a parallel persistence layer.
- Store only reviewed business output and coverage. Raw model inputs/responses and private prompting data are not part of this checkpoint.
- Keep the new publication as one named ActivityExplainer module rather than reactivating any legacy proposal/registry/finite-key module.

## Blockers

- None.

## Exact next action

- A future Agent should add a bounded raw-call receipt/per-material checkpoint slice only if a later run-centric orchestration needs it; it must not delay semantic process reconstruction.

## Resume checks

- Re-read this file, check the shared worktree status, and run only its direct selector before further edits.
