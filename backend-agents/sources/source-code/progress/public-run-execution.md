# Progress: public run execution

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Expose the already-composed internal source-to-report execution through the sole Java
  Agent without exposing a local source path, packet contents, provider settings, or another API.
- Approved inputs: Active runtime design; queued run registry; existing internal coordinator;
  deterministic fixture tests only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Reviewed the public Agent and persisted registry: run requests are already durable, but the
  state file has only a queue write and no guarded transition.
- Added `AnalysisStepExecutionRequest`, guarded lifecycle transitions in the internal registry,
  and `RepositoryAnalysisAgent.executeStep`. The final-document target uses the injected internal
  coordinator; no source path, model input, or bootstrap profile becomes a public argument.
- Added focused tests for `QUEUED → RUNNING → FINISHED` and a technical failure ending in
  `FAILED`, plus the existing start/inspect and registry tests.

## Current state

- Complete. This is deliberately a final-document execution seam only. It does not implement
  automatic retry, resume, source/model fallback, CLI, HTTP, artifact lookup, rendering or trace.

## Changed files

- `progress/public-run-execution.md`
- `docs/DESIGN.md`
- `src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisStepExecutionRequest.java`
- `src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemAnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/test/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgentExecutionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=LocalRepositoryAnalysisAgentExecutionTest,RepositoryAnalysisAgentStartTest,AnalysisRunRegistryTest test` | PASS | 5 tests, 0 failures/errors/skips; a queued run becomes FINISHED on the final target and becomes FAILED when the technical continuation throws. |

## Decisions

- The public Agent accepts only a queued run ID plus an explicit target. The current implementation
  supports the final nine-section target; other target steps remain unsupported rather than being
  silently treated as a full execution.

## Blockers

- CLI/artifact observation, report rendering/validation/trace views, and real fixed-repository
  capacity/quality validation remain future work.

## Exact next action

- Add safe artifact observation for existing material, activity, knowledge, and report checkpoints.

## Resume checks

- Verify public observation can reopen the report/checkpoints without triggering a model call.
