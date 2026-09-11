# Progress: public report render

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the smallest public, read-only render operation for a finished run. It must consume
  only the run output's existing report checkpoint and a configured deterministic report reader.
- Approved inputs: Public run-centric Interface, existing business-report checkpoint reader and
  renderer, and the finished-run output manifest.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the existing report checkpoint reader already validates all four report files and can
  deterministically rerender without a Provider or source access.

## Current state

- The public Interface now exposes `render(runId)`. A finished run delegates only its persisted
  report checkpoint to a configured renderer; the standard adapter fresh-reopens and verifies the
  report, rerenders it deterministically, and returns its SHA-256 and byte length.

## Changed files

- `progress/public-report-render.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/runtime/CompletedReportRenderer.java`
- `src/main/java/org/sourceanalysis/app/runtime/RenderedDocumentReference.java`
- `src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointRenderer.java`
- `src/test/java/org/sourceanalysis/app/runtime/PublicReportRenderContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=PublicReportRenderContractTest test` | RED | 1 assertion failure: the `CompletedReportRenderer` contract did not exist. |
| `mvn -Dtest=PublicReportRenderContractTest,LocalRepositoryAnalysisAgentExecutionTest,RepositoryAnalysisAgentStartTest test` | PASS | 4 tests passed; render is provider-free and receives only the finished run's report checkpoint. |
| `mvn -Dtest=BusinessReportCheckpointTest,PublicReportRenderContractTest test` | PASS | 2 tests passed; the existing checkpoint reader and the public render contract both fresh-verify deterministic output. |

## Decisions

- Render returns only verified report metadata. Artifact content/query and CLI remain separate
  follow-up work; this avoids coupling rendering to a generic artifact browser.
- The standard adapter rejects a persisted report whose deterministic rerender differs from its
  saved Markdown before exposing a document reference.

## Blockers

- None.

## Exact next action

- Add the public artifact/source-reference query seam, then map it to the CLI.

## Resume checks

- A queued, running, or failed run must not render. Rendering must not invoke a Provider or read
  customer source.
