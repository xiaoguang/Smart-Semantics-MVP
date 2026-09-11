# Progress: Report deterministic rerender

- Status: COMPLETE
- Agent role: Root implementation coordinator
- Model: Design and scope review: Sol/ultra; production: Terra/xhigh; tests: Luna/xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Make a saved business report deterministically rerenderable from its typed report and source references, without a Provider, source scan, or business inference.
- Approved inputs: Active Step 08 design, the completed report checkpoint reader, and the user's continuing authorization for local implementation.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Inspected the existing publisher: Markdown rendering was deterministic but private to its model-backed publisher, so saved reports could not prove that re-rendering used report data rather than persisted Markdown bytes.
- Added the package-internal `BusinessReportMarkdownRenderer`, reused by the Publisher and the
  checkpoint reader. The reader now regenerates Markdown from `BusinessReport` and
  `SourceReference` values only.

## Current state

- The behavior is complete: a deliberately altered in-memory persisted-Markdown field cannot
  influence the regenerated document.

## Changed files

- `progress/report-deterministic-rerender.md`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportMarkdownRenderer.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointReader.java`
- `docs/analysis-steps/08-nine-section-document.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportCheckpointTest test` | Expected RED | 1 test, 1 failure: `BUSINESS_REPORT_DETERMINISTIC_RERENDER_NOT_IMPLEMENTED`; 0 errors. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportCheckpointTest test` | PASS | 1 test, 0 failures/errors/skips; rerender ignores deliberately altered saved Markdown. |

## Decisions

- Extracted only the existing deterministic renderer; no second report format, model call, source read, or runtime API was added.

## Blockers

- None.

## Exact next action

- COMPLETE.

## Resume checks

- Read this file, inspect `git status --short`, and run the targeted checkpoint selector after the RED is established.
