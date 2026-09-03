# Progress: Source-local M1 Gap test correction

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Correct the two stale M1 local source-file Gap assertions in `CodeStructureGraphBuilderTest`
- Approved inputs: Published `docs/analysis-steps/03-program-graphs.md` M1 local Gap contract; existing `CodeStructureGraphBuilderTest`
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Plan

- Update only the malformed Java and forbidden XML entity assertions in `CodeStructureGraphBuilderTest`.
- Require empty affected-entry ownership when no entry owner is provable, exact candidate/locator retention, and closed coverage for a complete capture containing only source-file local Gap(s).
- Run only the direct `CodeStructureGraphBuilderTest` Maven selector and `git diff --check`.

## Current state

- Production implementation already exposes typed M1 `GraphGapDraft` carriers and returns source-file local Gap coverage as closed for `COMPLETE_CAPTURE`.
- The two direct tests still encode the superseded global-entry-owner / open-coverage expectation.

## Completed

- Updated only the malformed Java and forbidden XML entity assertions in `CodeStructureGraphBuilderTest`.
- Both cases now require empty `affectedEntryIds`, exact candidate-to-disposition closure, the exact frozen-file full-span locator, and `coverage.closed=true`.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | PASS | 7 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| `git diff --check` | PASS | No whitespace errors |

## Blockers

- None.
