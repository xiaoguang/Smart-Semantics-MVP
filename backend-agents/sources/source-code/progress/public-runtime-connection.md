# Progress: Public runtime connection

- Status: IN_PROGRESS
- Agent role: Root implementation coordinator
- Model: Design and scope review: Sol/ultra; production: Terra/xhigh; tests: Luna/xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Connect the existing frozen-source and business-first Modules behind the sole public `RepositoryAnalysisAgent` seam, beginning with a test-only, local public execution path. Do not redesign Steps 01–05, call a live model, add HTTP, or alter the nine-section business contract.
- Approved inputs: Current `docs/DESIGN.md`, active step designs, existing source inventory through business-report Modules, and the user’s continuing authorization for local implementation.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the current linked worktree and preserved the pre-existing shared dirty state.
- Confirmed that `LocalGitCommitCaptureAdapter`, `RunStoreBootstrap`, Steps 01–05 public seams, and the internal `BusinessAnalysisWorkflow` already exist.
- Confirmed the public Agent was previously absent, then added it as the sole runtime seam.
- Added the smallest durable runtime foundation: a closed `AnalysisRunRequest`, its content reference,
  lifecycle/reference values, and an internal run registry. It stores a random queued execution ID plus
  canonical request bytes, then fresh-reopens them without exposing a caller-owned filesystem path.
- Closed two prerequisite read paths for a future public runtime: a saved local-activity checkpoint
  now reopens its typed activity/coverage result, and a saved nine-section report now reopens and
  can deterministically rerender from typed report data rather than saved Markdown bytes.
- Added `RepositoryAnalysisAgent.start` and `inspect` through a real filesystem-backed
  `LocalRepositoryAnalysisAgent`: `start` persists a path-free queued run and `inspect`
  fresh-reopens the same run. No parser or Provider starts as a side effect.

## Current state

- The business-first modules have direct scripted tests and separately validated small live-Luna samples. They are not yet reachable through the only intended public run-centric interface.
- The runtime can now create and observe a queued identity through the sole public Agent. Its last
  two business outputs can be reopened without replaying a Provider. It still does not execute
  Step 01–08, expose artifact/render/validation/trace operations, or provide CLI/HTTP adapters.

## Changed files

- `progress/public-runtime-connection.md`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunRequest.java`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunRequestReference.java`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunReference.java`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunLifecycleState.java`
- `src/main/java/org/sourceanalysis/app/runtime/ReaderCandidateRound.java`
- `src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/runtime/RunInspection.java`
- `src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemAnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/test/java/org/sourceanalysis/app/artifact/AnalysisRunRegistryTest.java`
- `src/test/java/org/sourceanalysis/app/RepositoryAnalysisAgentStartTest.java`
- `docs/DESIGN.md`
- `progress/activity-checkpoint-reader.md`
- `progress/report-deterministic-rerender.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Narrow source inventory | PASS | Capture, stores, Steps 01–05 and `BusinessAnalysisWorkflow` exist; public `RepositoryAnalysisAgent` does not. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=AnalysisRunRegistryTest test` | Expected RED | 1 test, 1 failure: `ANALYSIS_RUN_REGISTRY_NOT_IMPLEMENTED`; 0 errors. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=AnalysisRunRegistryTest test` | PASS | 1 test, 0 failures/errors/skips; queue/reopen works through a real temporary store. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositoryAnalysisAgentStartTest test` | RED → PASS | The initial missing-Agent assertion failed as expected; after the public seam, 1 test passed with 0 failures/errors/skips. |
| Scoped `spotless:check` | PASS | All nine new/changed Java files meet the configured formatter. |
| Direct report/activity checkpoint selectors | PASS | Saved business outputs reopen without replaying model authoring. |

## Decisions

- Do not create a parallel public API or reimplement technical stages; the new public seam is a façade over existing stable modules.
- The run registry remains package-internal. The public Agent receives only an opaque opened store
  handle and exposes no filesystem path.
- The public Agent does not advertise operations that have no durable result yet. The remaining
  target operations join this same Interface only as their execution/query paths are implemented;
  this prevents placeholder operations that look usable but always fail.
- Keep real Luna calls out of this runtime-connection slice; direct tests use the scripted provider only.
- A queued run records a durable starting identity, not a same-run crash-recovery state machine. Step execution will create explicit new observations/progress in later slices.

## Blockers

- None identified. The next slice is the first executable source-inventory handoff; it must reuse
  existing code rather than duplicate the fixed-repository test setup.

## Exact next action

- Extract the existing Step 01 composition into a production executor that consumes one registered
  frozen source and persisted configuration references. Then connect it behind a new execution run;
  do not make `start` silently scan a source repository.

## Resume checks

- Read this file, inspect `git status --short`, and rerun `AnalysisRunRegistryTest` plus
  `RepositoryAnalysisAgentStartTest` before changing the public run seam.
