# Progress: persisted run request reader

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Let internal runtime composition fresh-reopen the exact persisted queued-run request, its typed value, and canonical bytes without exposing a filesystem path or inventing a second run registry.
- Approved inputs: Existing run registry/store contract and active public runtime design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed `start` persists canonical `run-request.json`, but the only reopen operation currently returns its reference and lifecycle state. Step 01 needs both the exact bytes and typed request to validate the content-addressed frozen-request binding.

## Current state

- Implemented the public internal-runtime read result and the direct reopen-after-store-close contract. It remains path-free and does not add run lifecycle repair or automatic resume.

## Changed files

- `src/main/java/org/sourceanalysis/app/runtime/PersistedAnalysisRunRequest.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemAnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/test/java/org/sourceanalysis/app/artifact/AnalysisRunRegistryTest.java`
- `progress/persisted-run-request-reader.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=AnalysisRunRegistryTest#freshReopensTheExactPersistedRequestValueAndCanonicalBytesForRuntimeComposition test` | RED then PASS | The RED proved the read result was absent. The green run passed 1 test: fresh reopening returns the persisted typed request and exact canonical bytes with the queued digest. |

## Decisions

- The read result is not a new public analysis request format. It is the exact bytes already saved by `start`, coupled with the existing typed request and run reference for runtime composition only.

## Blockers

- None.

## Exact next action

- Use this path-free read result in the Step 01 runtime input resolver; do not reconstruct a request from test-only values.

## Resume checks

- A later Step 01 resolver must consume this result without reconstructing or reserializing the run request.
