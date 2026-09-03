# Progress: program graph analysis-step store contract test

- Status: COMPLETE
- Agent role: Luna/xhigh public-seam test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add one targeted RED test for the Program Graphs analysis-step store contract
- Approved inputs: scoped AGENTS.md, docs/plans/*.md, docs/analysis-steps/03-program-graphs.md M6 wire
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

## Current state

Added `ProgramGraphsAnalysisStepArtifactStoreTest` with one public-seam test. It installs and
fresh-reopens real Verified Source Inventory and Application Discovery analysis-step
publications, prepares exactly seven Program Graph semantic payloads, and exercises the valid
ordered pair plus swapped and incomplete upstream mutations. No production code is in scope.
The parent task completed the production/test compile setup, and the focused selector is now
GREEN.

## Changed files

- progress/program-graph-step-store-tests.md
- src/test/java/org/sourceanalysis/app/artifact/ProgramGraphsAnalysisStepArtifactStoreTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsAnalysisStepArtifactStoreTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

The test uses real module and analysis-step stores for both ordered upstream publications, not
constructed references or nulls. It asserts the seven semantic program-graph payloads and
rejects missing, swapped, or wrong upstream references.

## Blockers

None. The focused test is GREEN after the parent task completed the required compile setup.

## Exact next action

The Program Graphs analysis-step store contract is now covered by a passing public-seam test.
Keep the real persisted/reopened upstream fixture; do not relax it to null or synthetic
publication references.

## Resume checks

Read this file, check git status, inspect the focused test and its selector, then rerun only that selector. Preserve any concurrent production changes.
