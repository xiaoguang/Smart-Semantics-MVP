# Progress: model batch output tests

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: GPT-5 Codex
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Plan step 2 only — focused public/observable RED tests for analysis-run-output-v3 dual ownership, stopped/FAILED source-run batch creation, and output publishers receiving an explicit output run.
- Approved inputs: Frozen local fixtures and deterministic scripted Provider only; no production implementation, JDT execution, real model, network, customer build, commit, or push.
- Current branch/worktree: Shared formal checkout at `linguan-prototype-v2/backend-agents/sources/source-code`; preserve unrelated worktree changes.

## Completed

- Read repository, backend-agent, and source-code scoped instructions.
- Read the model-job execution design routing and TDD test-writing guidance.
- Created this per-agent progress record before touching tests.

## Current state

- Existing `AnalysisRunOutput`, `FileSystemAnalysisRunRegistry`, publisher, and focused test seams are being located and read.
- First mixed-owner RED is in place: a real filesystem registry scenario stops the source run as `FAILED`, starts a distinct output batch, and expects a v3 manifest whose material checkpoint remains source-owned while Activity/Knowledge/Report checkpoints are batch-owned.
- The v3 implementation now makes the mixed-owner round-trip green, and the added registry mutation case proves a third-run Activity/Knowledge/Report owner is rejected without writing output or mutating source/batch lifecycle state.
- Added a real `ProgramGraphsPublicFixture`/artifact-store Activity publisher contract. The explicit output-run overload is now implemented and GREEN.

## Changed files

- `progress/model-batch-output-tests.md`
- `src/test/java/org/sourceanalysis/app/runtime/ModelBatchAnalysisRunOutputTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ModelBatchActivityCheckpointPublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=ModelBatchAnalysisRunOutputTest test` | BLOCKED | Maven could not find the required Java 17 toolchain when no explicit toolchain file was supplied. |
| `mvn -t .mvn/toolchains.xml -Dtest=ModelBatchAnalysisRunOutputTest test` | RED | 1 test executed, 1 failure: `NoSuchMethodException` for the missing v3 five-argument `AnalysisRunOutput` constructor carrying `sourceRunId`. |
| `mvn -t .mvn/toolchains.xml -Dtest=ModelBatchAnalysisRunOutputTest test` | PASS | 2 tests executed, 0 failures: mixed-owner save/reopen and strict foreign output-owner rejection; source remains `FAILED`, output batch remains `RUNNING` after rejection. |
| `mvn -t .mvn/toolchains.xml -Dtest=ModelBatchActivityCheckpointPublisherTest test` | RED | 1 test executed, 1 failure: `NoSuchMethodException` for `ActivityExplanationCheckpointPublisher.publish(AnalysisRunId, BusinessMaterialBuildResult, List, List, List)`. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=ModelBatchAnalysisRunOutputTest,ModelBatchActivityCheckpointPublisherTest test` | GREEN | Mixed ownership and explicit Activity output ownership pass. |

## Decisions

- Keep tests at public/observable registry and publisher seams; do not assert production source text or private implementation details.
- Preserve strict cross-run ownership rejection while proving explicit output-run ownership for new batch artifacts.
- Use the explicit five-argument constructor as the initial v3 public record seam; reflection keeps this RED compilable against the current four-field record and reports the missing capability at runtime.
- Keep the Activity publisher test at the real artifact-store seam: after the overload exists, it must install the publication at `outputRunId` while its receipt upstream refs remain the source material payload refs.

## Blockers

- None yet.

## Exact next action

- No further action; the production ownership path is implemented and covered.

## Resume checks

- Re-run `git status --short` and verify this file plus only the intended focused test changes are present.
