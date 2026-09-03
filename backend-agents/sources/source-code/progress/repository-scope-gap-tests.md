# Progress: repository scope and local graph gap tests

- Status: COMPLETE
- Agent role: Luna test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02T23:06:51-0230
- Last updated: 2026-09-02T23:51:00-0230
- Scope: Public-seam RED tests for the corrected M3 local-source Gap and repository-scope Gap projection contract.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, existing public graph builders/publication seams, frozen test fixtures.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

## Current state

Focused public-seam RED tests are complete. The local-source and bounded-scope cases fail at the current implementation exactly where the published correction requires production changes; the non-structure owner guard remains green.

## Changed files

- `progress/repository-scope-gap-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/RepositoryScopeGapTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryScopeGapTest test` | RED | 3 tests: 1 pass; 2 expected errors—empty M1 local owner rejected by current `GraphGapDraft`, bounded M6 scope stops at `requireNoUnprojectableScopeGaps` |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | New test formatted; command also formatted pre-existing untracked `ProgramGraphGapProjectionTest.java` in the shared worktree |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryScopeGapTest test` | RED | 3 tests: 1 pass, 0 failures/errors in the owner-rejection test; 2 expected errors remain at M1 `GraphGapDraft.forLocalOccurrence` and M6 `requireNoUnprojectableScopeGaps` |

## Decisions

- Tests will observe public builders and persisted graph publication; no production internals or fabricated entry/locator data will be used.

## Blockers

- Production GREEN is intentionally outside this Luna test task. The two RED errors are the exact handoff to Terra: permit empty `affectedEntryIds` only for registered CODE_STRUCTURE file-local reasons, and retain bounded scope only in index/receipt accounting.

## Exact next action

Added `RepositoryScopeGapTest` with the zero-entry CODE_STRUCTURE local Gap, non-structure empty-owner rejection, and bounded-scope M6 projection assertions. Corrected three test-only API/assertion mistakes after the first compile. The focused selector establishes the intended RED; the test task is complete and ready for Terra GREEN.

## Resume checks

- Confirm only this progress file and the focused test are included from this task (the formatting command also touched a pre-existing untracked graph projection test).
- Preserve the exact two RED stack locations when implementing production GREEN.
