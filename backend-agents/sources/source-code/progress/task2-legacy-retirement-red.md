# Progress: legacy semantic route retirement RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-11T19:33:51-02:30
- Last updated: 2026-09-11T19:36:00-02:30
- Scope: Add one narrow public-seam RED test for retiring the obsolete flow-interpretation module registrations 1–9 while preserving modules 10/11 and ModelRuntimeIdentityV1.
- Approved inputs: Task 2 cleanup design and current source tree; no production, design, deletion, or compatibility changes.
- Current branch/worktree: shared implementation worktree (parent-owned branch)

## Completed

- Added the single public-seam `LegacySemanticRouteRetirementTest`.

## Current state

The active module registry still contains the retired flow-interpretation module numbers 1–9 and the representative retired `FiniteKeyFlowTaskCompiler` class is still present. The new public-seam test expresses the desired fail-closed retirement behavior and preserves modules 10/11 plus `ModelRuntimeIdentityV1`.

## Changed files

- This progress file (owned by this Agent)
- `src/test/java/org/sourceanalysis/app/artifact/LegacySemanticRouteRetirementTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LegacySemanticRouteRetirementTest test` | EXPECTED RED | Maven compile succeeded; 1 test, 1 failure, 0 errors, 0 skipped. Nine retired module constructions did not throw; `FiniteKeyFlowTaskCompiler` was still present. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Test the observable `AnalysisStepModuleAddress` construction seam and classpath presence, not historical docs or progress names.
- Preserve active module addresses 10 (`business-material-builder`) and 11 (`activity-explainer`) and instantiate `ModelRuntimeIdentityV1` as explicit protected behavior.

## Blockers

- GREEN implementation is intentionally outside this Agent's scope; parent/Terra must remove the retired registrations and implementation before rerunning this selector.

## Exact next action

RED is complete. Parent/Terra may now implement cleanup against this selector; this Agent does not commit or push.

## Resume checks

- Confirm only this progress file and the one owned test changed.
- Re-run the direct selector only if the test source or production registry changes before GREEN implementation.
