# Progress: business report publisher

- Status: COMPLETE
- Agent role: Root coordinator; Step 08 minimum business-report vertical slice
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Implement the smallest BusinessReportPublisher seam that turns reviewed repository knowledge and program-owned short source references into a reviewed nine-section JSON report, deterministic Markdown, and a canonical four-file checkpoint. CLI/runtime wiring and real product-model calls are excluded from this slice.
- Approved inputs: Scoped `AGENTS.md`; `docs/DESIGN.md`; Step 08 detailed design; current Step 06/07 typed results.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Added one public-seam scripted test that supplies three reviewed activities, one model-reviewed process, and four program-owned short source references.
- Added the public report types, Chinese DRAFT/REVIEW Prompt resources, strict nine-section/ref validation, and deterministic Markdown with same-document source anchors.
- Added an unknown-reference negative assertion; the model cannot cite a short ref outside the program-owned mapping.
- Added a canonical Step 08 checkpoint that persists and fresh-reopens `business-report.json`, `source-refs.jsonl`, `document.md`, and `report-validation.json` from the Step 07 knowledge checkpoint.
- Corrected RAW UTF-8 publication validation so the store verifies Markdown's UTF-8/no-BOM/content identity, rather than treating Markdown as JSON. Corrected JSONL source-reference identity to use its canonical JSONL formula.

## Current state

- The Step 08 vertical slice is green. It makes exactly one DRAFT and one whole-document REVIEW, preserves only the review result, and fresh-reopens four canonical report outputs. Runtime/CLI, true repository-scale input planning, and real Luna remain outside this slice.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/document/*`
- `src/main/resources/org/sourceanalysis/app/analysis/document/*`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointTest.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisStepModuleAddress.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemRunStoreHandle.java`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `progress/business-report-publisher.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportPublisherTest test` | PASS | 2 tests; 0 failures/errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessReportCheckpointTest test` | PASS | 1 test; 4 report files fresh reopen. |

## Decisions

- The model receives complete business knowledge and short ref IDs only; the source file/line/snippet map stays program-side and is used only during Markdown rendering.
- Java validates report structure, nine fixed chapter titles, and ref scope. It does not decide whether Chinese business prose is semantically implied by a Java statement.
- Markdown remains a first-class canonical RAW_UTF8 artifact. The store verifies valid UTF-8, rejects a BOM, and recalculates its content identity on both install and fresh reopen.

## Blockers

- None for the completed report vertical slice.

## Exact next action

- Start the narrow runtime/CLI orchestrator after the once-only repository consolidation seam is complete; do not introduce a second report interface.

## Resume checks

- Re-read this file, run the direct report selector, and do not extend legacy planner/renderer/trace/archive packages.
