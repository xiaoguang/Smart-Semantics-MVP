# Progress: Program graph Gap projection test-contract correction

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Correct stale test-contract expectations in `CodeStructureGraphGapCarrierTest` and `ProgramGraphGapProjectionTest`
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` M1/M6 public wire policies and the named public wire tests
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Plan

- Require empty `affectedEntryIds` for a malformed CODE_STRUCTURE source-file local Gap when ownership cannot be proven, preserving candidate, locator, and coverage assertions.
- Align the M6 helper policy registry's CONTROL_FLOW and DATA_FLOW standalone artifact ID prefixes with the production/public policy: `program-graphs-control-flow-graph` and `program-graphs-data-flow-graph`.
- Run only the requested combined Maven selector and `git diff --check`.

## Current state

- The named tests and applicable M1/M6 contracts have been read. The two requested assertions were stale; no production change was made.

## Changed files

- `progress/program-graph-gap-projection-test-fix.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphGapCarrierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphGapProjectionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest,ProgramGraphGapProjectionTest test` | PASS | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| `git diff --check` | PASS | No whitespace errors in tracked changes |
| `git diff --no-index --check /dev/null progress/program-graph-gap-projection-test-fix.md` (and both owned test files) | PASS | No whitespace errors in the three untracked task files; exit 1 is expected for a non-empty no-index diff |

## Blockers

- None.
