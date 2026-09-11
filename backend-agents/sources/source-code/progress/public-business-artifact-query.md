# Progress: public business artifact query

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Expose finished-run business checkpoints through a bounded, path-free read operation.
- Approved inputs: Existing output manifest, canonical module store, four business checkpoints and
  public run-centric Interface.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed each intended file is already a canonical module payload and can be fresh-reopened
  through its existing report/material/activity/process checkpoint.

## Current state

- The public Interface now exposes a closed-key artifact query. A finished run delegates only its
  output manifest, selected key and positive byte budget to a canonical checkpoint reader; the
  reader fresh-reopens exactly the mapped module payload and rejects oversize output as a whole.

## Changed files

- `progress/public-business-artifact-query.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessOutputArtifactKey.java`
- `src/main/java/org/sourceanalysis/app/runtime/ArtifactQuery.java`
- `src/main/java/org/sourceanalysis/app/runtime/ArtifactView.java`
- `src/main/java/org/sourceanalysis/app/runtime/CompletedBusinessArtifactReader.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessCheckpointArtifactReader.java`
- `src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java`
- `src/test/java/org/sourceanalysis/app/runtime/PublicBusinessArtifactQueryContractTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=PublicBusinessArtifactQueryContractTest test` | RED | 1 assertion failure: the closed business artifact key did not exist. |
| `mvn -Dtest=PublicBusinessArtifactQueryContractTest,PublicReportRenderContractTest,LocalRepositoryAnalysisAgentExecutionTest test` | PASS | 5 tests passed; a finished run reads only the selected, budgeted business output. |
| `mvn -Dtest=BusinessReportCheckpointTest,PublicBusinessArtifactQueryContractTest,PublicReportRenderContractTest test` | PASS | 3 tests passed; a real persisted report payload is fresh-reopened, exposed whole, and rejected when its byte budget is insufficient. |

## Decisions

- v0 selects a closed `BusinessOutputArtifactKey`, rather than exposing arbitrary artifact IDs or
  paths. It is sufficient for the complete business checkpoints and prevents browsing protected
  prompt/model internals.
- Source references are a designated report output; raw model input/response and unregistered
  technical artifacts remain unreachable through this query.

## Blockers

- None.

## Exact next action

- Add the minimal CLI adapter over the same public run, render and artifact operations.

## Resume checks

- Only FINISHED runs are queryable. Oversized content must fail rather than truncate.
