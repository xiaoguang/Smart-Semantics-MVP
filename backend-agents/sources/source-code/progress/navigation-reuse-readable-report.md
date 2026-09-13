# Progress: navigation reuse readable report

- Status: IN_PROGRESS
- Agent role: primary TDD implementation — report/source separation
- Model: GPT-5
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: remove source bodies and dead anchors from `document.md`, retain plain short references and the complete independent `source-refs.jsonl`, then prove deterministic reopen/rerender.
- Approved input: `docs/plans/navigation-reuse-and-readable-report-design.md` report contract.

## Completed

- Updated the direct publisher and checkpoint tests to require plain `[S…]` references, one source-map instruction, no embedded source details, and Markdown schema v2.
- Observed the intended RED: five tests, two failures for the old embedded source block and Markdown schema v1.
- Removed the details/source-code renderer branch, retained plain short citations, and upgraded the Markdown producer/reader/policy to v2.
- `BusinessReportPublisherTest` is GREEN (4 tests). The checkpoint selector is temporarily blocked by the concurrently edited Step05 publisher until that production slice compiles.

## Current state

- Renderer behavior is implemented. Re-run `BusinessReportCheckpointTest` after the Step05 compilation slice is coherent, then update status.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportMarkdownRenderer.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointReader.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- directly affected policy/test fixtures and report tests
- this progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessReportPublisherTest,BusinessReportCheckpointTest test` before GREEN | RED | 5 tests; 2 expected failures: source block still embedded and Markdown schema v1 |
| same selector after renderer change | PARTIAL | publisher 4/4 GREEN; checkpoint reached unrelated concurrent Step05 compile error |

## Exact next action

- Re-run both direct report classes after Step05 compiles; confirm `source-refs.jsonl` retains snippets and pure rerender is byte-identical without Provider calls.
