# Progress: four-package preview driver

- Status: UNREVIEWED_DRAFT_EXPORTED
- Agent role: Bounded ignored-workspace operational driver for four reviewed packages to preview process/report output
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Build one thin Java driver under the ignored run workspace. Its default `--dry-run` validates four explicit saved reviewed-activity JSON files, original materials/source references, coverage, profiles, and output destination without a Provider request. Its separately selected preview mode uses existing public in-memory ProcessExplainer and BusinessReportPublisher with the existing journal-backed Luna/high Provider, then writes preview-only files. No production source, run receipts/lifecycle, journal rewrite, model retry, JDT scan, customer build, schema, or new public interface.
- Approved inputs: Parent's current four-package operational request; saved run `92bdb80…` materials; exact four reviewed activity outputs; existing eight COMPLETE activity journal records; existing process/report profile configuration; current four-package scope plan.
- Current branch/worktree: Shared source-code worktree and ignored run root `.workspace/jsherp-jdt-luna-run.5Oqj9Y/`.

## Contract

- Exactly four reviewed activities and their exact material IDs/source refs are loaded from saved inputs. Their original material contexts are authoritative, never regenerated path-bearing variants.
- Selected coverage is verified; retained unselected coverage may be carried forward only from the original material coverage. The driver never fabricates full-scope coverage.
- Preview artifacts are clearly marked non-checkpoint output: knowledge JSON, report JSON, source refs JSON, Markdown, validation JSON, and an operational handoff note that the formal CLI lifecycle remains `RUNNING`.
- `--dry-run` sends no Provider request. Do not execute preview/live mode without a later parent launch authorization.

## Current state

- The ignored inspection driver and its explicit input manifest are complete. `--dry-run` validates the four exact stored reviewed activities, the original run-92 material JSONL, the original source-reference map, the existing run configuration profiles, both immutable lifecycle states, the 4/326 coverage denominator, and a longest saved activity with 30 steps. It creates no output directory and makes zero Provider requests. The parent-launched preview completed the report DRAFT but stopped at `BUSINESS_REPORT_PROVIDER_FAILED_AFTER_START`; the report REVIEW journal remains `STARTED` and was not retried. A separate zero-provider, package-local exporter decoded only the completed DRAFT and rendered it as explicitly unreviewed output.

## Changed files

- progress/four-package-preview-driver.md (owned progress)
- `.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/FourPackagePreviewDriver.java` (ignored operational driver)
- `.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/four-package-preview-input.json` (ignored explicit input manifest)
- `.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/org/sourceanalysis/app/analysis/document/FourPackageUnreviewedDraftExport.java` (ignored zero-provider DRAFT exporter)

## Next action

- The unreviewed DRAFT is available under `inspection/four-package-preview/` as exact model JSON, the original 457-ref map, and `UNREVIEWED-DRAFT.md`. It is not a reviewed report, validation, publication, receipt, or formal lifecycle completion. Do not retry the started report review from this path.

## Verification

- `javac --release 17 -cp "target/classes:$(tr -d '\n' < target/repository-run-classpath.txt)" -d .workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/classes .workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/FourPackagePreviewDriver.java`
- `java -cp ".workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/classes:target/classes:$(tr -d '\n' < target/repository-run-classpath.txt)" FourPackagePreviewDriver --dry-run /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/four-package-preview-input.json` — PASS: builder-framed material set `business-material-set:8a96a7525dc55a82fe5fb91467f7edeb28f6ad0f6435ee80342270cda9b52a47`; 4 reviewed activities; 326 coverage / 322 `NOT_ANALYZED`; 30 longest saved activity steps; 457 unique source refs; run-92 `FAILED`; formal run `0f970…` `RUNNING`; `providerCalls=0`.
- `git diff --check` — PASS.
- `javac --release 17 -cp "target/classes:$(tr -d '\n' < target/repository-run-classpath.txt)" -d .workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/classes .workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/org/sourceanalysis/app/analysis/document/FourPackageUnreviewedDraftExport.java` — PASS.
- `java -cp ".workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/classes:target/classes:$(tr -d '\n' < target/repository-run-classpath.txt)" org.sourceanalysis.app.analysis.document.FourPackageUnreviewedDraftExport /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jsherp-jdt-luna-run.5Oqj9Y/models/journal/request-fc0114da82f7fcd57dbf9b3140aed589ec82121fc3e6356974f4d70c2fcdca2a.json /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jsherp-jdt-luna-run.5Oqj9Y/stores/runs/analysis-run--92bdb80fc76b5050c45612994811bae5ea74eb4d09cf733b9436b738dfbb6f12/steps/06-flow-interpretation/modules/10-business-material-builder/business-materials.jsonl /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/four-package-preview` — PASS: 9 sections, 457 source refs, `providerCalls=0`.
- The exported `unreviewed-draft-business-report.json` SHA-256 matches the exact decoded completed journal response; `UNREVIEWED-DRAFT.md` begins with the unreviewed/start-state notice.
