# Progress: stage01-integrity-core

- Status: COMPLETE
- Agent role: Stage01 M1/M2 integrity-regression implementation
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Make the independent M1/M2 integrity RED suite GREEN by changing only `src/main/java/com/linguan/codemd/stage01/**` and this progress file. Do not modify tests, design, POC, `ProvenFactCompiler`, or any Stage01 analyze seam.
- Approved inputs: `RepositoryIntegrityRegressionTest`, existing M1/M2 production, existing M2/M1/acceptance selectors, and the coordinator's stated review requirements.
- Current branch/worktree: shared working tree; all pre-existing modifications and untracked artifacts are preserved.

## Completed

- Read the completed preceding repository-core progress record and recorded the current dirty worktree before this task's code changes.
- Read the independent nine-test M1/M2 integrity contract and ran its exact selector. It produced the required RED: 9 tests run, 6 assertion failures.
- Added the fixed profile registry/parser selection, exact mapper-location matching, entry-rooted call-site ownership, snapshot-salted M2 identities, and exclusive locator end-column behavior. The integrity selector is GREEN (9 tests).
- Preserved the three established local budget behaviors: the existing M2 selector remains GREEN (18 tests).
- Ran the requested M1 selector and, with no concurrent M3 core agent, the fixed-checkout jshERP acceptance. Both are GREEN.

## Current state

- Stage01 M1/M2 integrity regression implementation is complete. All requested direct selectors are fresh GREEN; pre-existing unrelated worktree changes remain preserved.

## Changed files

- `progress/stage01-integrity-core.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing unrelated modifications and untracked Stage01 work preserved. |
| `mvn -Dtest=RepositoryIntegrityRegressionTest -DfailIfNoTests=false test` | RED (expected) | 9 tests run, 6 failures: profile/parser, mapper location, call ownership, snapshot-salted identities, and exclusive locator end-column. |
| `mvn -DskipTests compile` | PASS | Stage01 production compiles after the integrity implementation. |
| `mvn -Dtest=RepositoryIntegrityRegressionTest -DfailIfNoTests=false test` | PASS | 9 tests, 0 failures/errors/skips. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | PASS | 18 tests, 0 failures/errors/skips; budget regressions preserved. |
| `mvn -Dtest=VerifiedSnapshotContractTest -DfailIfNoTests=false test` | PASS | 7 tests, 0 failures/errors/skips. |
| `mvn -Dtest=JshErpStage01AcceptanceTest -DfailIfNoTests=false test` | PASS | 1 test, 0 failures/errors/skips. |

## Decisions

- This task remains strictly within M1/M2 production; M3 and `Stage01Analyzer` analyze surfaces are excluded.

## Blockers

- None.

## Exact next action

- No implementation action remains. Any later work must start from the resume checks and a new scoped task decision.

## Resume checks

- Re-read this progress file, run `git status --short`, and rerun the integrity selector before changing production code.
