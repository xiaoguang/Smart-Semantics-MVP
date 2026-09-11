# Progress: live Luna user lifecycle report

- Status: COMPLETE
- Agent role: primary implementation and verification agent
- Model: gpt-5.6-terra / xhigh for code; gpt-5.6-luna / high only for the explicitly approved report candidate
- Started: 2026-09-11T15:59:17Z
- Last updated: 2026-09-11T16:11:00Z
- Scope: Build a test-only, opt-in real-report harness from the already preserved automatic user-registration/login activities and their reviewed process output. It must not replay activity or process requests.
- Approved inputs: Existing frozen jshERP business-material checkpoint; saved reviewed registration/login activity outputs; saved reviewed user-lifecycle process output; one explicitly authorized Luna/high report DRAFT and REVIEW.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed the current runtime path uses `BusinessMaterialBuilder`, `ActivityExplainer`, `ProcessExplainer`, and `BusinessReportPublisher`; the retired finite-key/registry route is not invoked by it.
- Confirmed the prior live process result has two conservative independent processes, rather than a fabricated registration-to-login sequence.

## Current state

- Added a property-gated, zero-Provider input check and a separate property-gated report harness. The harness reconstructs `RepositoryBusinessKnowledge` from saved outputs, retains the two independent reviewed processes, and derives SourceReferences from the original material set.
- The zero-Provider check passed: 2 activities, 2 processes, 2 coverage rows and a sorted, nonempty SourceReference set. Spotless formatted the two affected test files.
- Codex login preflight passed. The single approved report DRAFT plus REVIEW completed in 187.718 seconds and wrote the ignored local report output. It contains the fixed nine chapters and preserves the independent login and registration activities without inventing a registration-to-login order.
- Updated current-maturity documentation to distinguish this real small-package result from full-repository acceptance.

## Changed files

- progress/live-luna-user-lifecycle-report.md
- src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticUserLifecycleProcessIT.java
- src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticUserLifecycleReportIT.java
- docs/DESIGN.md
- docs/analysis-steps/08-nine-section-document.md
- README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing dirty worktree recorded before this work item. |
| targeted source inspection | PASS | Report publisher performs one DRAFT plus one whole-report REVIEW. |
| report harness compile/input check | PASS | 2 tests executed; 1 input check passed and 1 live test was skipped. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply` | PASS | 593 Java files clean; 2 test files formatted. |
| live report selector | PASS | 2 tests; 1 live report test passed, 1 input test skipped; 187.718 seconds. |
| non-live loader selectors | PASS | 3 tests; 2 model-free input checks passed and 1 live test was skipped. |
| `mvn -o -t .mvn/toolchains.xml spotless:check` | PASS | 593 Java files clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Do not force the two activities into an unproven sequence. The report input must retain their two independent reviewed processes and their confirmation notes.
- Do not replay the prior activity or process model calls.

## Blockers

- None known. The one live report call occurs only after the non-live loader/parser path passes.

## Exact next action

- Run targeted non-live tests after formatting and check the documentation diff. Then preserve this result as the real small-package report-quality checkpoint; do not replay it.

## Resume checks

- Read this file, inspect the saved output in `.workspace/live-luna-automatic-user-lifecycle-report-v1/`, and do not rerun its live opt-in selector.
