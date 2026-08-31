# Progress: stage01-repository-core

- Status: COMPLETE
- Agent role: Stage01 M2 repository-core implementation
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement Stage01 M2 GREEN only in `src/main/java/com/linguan/codemd/stage01/**`, plus this progress file and a necessary `pom.xml` change if required.
- Approved inputs: `AGENTS.md`, `docs/stages/01-proven-source-facts.md` sections 6/9/11, M2 tests and fixtures, existing M1 implementation.
- Current branch/worktree: shared working tree; unrelated changes preserved.

## Completed

- Created the required exclusive progress record before implementation changes.
- Recorded pre-existing dirty worktree state; no unrelated paths will be modified.
- Ran the exact M2 selector and observed the required RED: test compilation reports only the absent `RepositoryUnderstanding` type and `Stage01Analyzer.understand(FrozenRepositoryRequest)` seam (20 diagnostics).
- Added the minimal public immutable M2 result records, package-private graph value records/compiler, M1 verified-byte reopen check, and M2 fatal codes. The compiler reopens only `VerifiedSnapshot.files()` using M1 no-follow path checks; it does not enumerate directories.
- Re-ran the exact M2 selector after the implementation; all 8 M2 tests are GREEN.
- Ran the requested M1 selector and direct `RepositoryDiscovererTest` regression; both are GREEN.
- Completed a read-only review. It identified five P1 conservative-analysis gaps outside the initial M2 test coverage: M2 budget handling, unsupported INSERT/DELETE SQL, duplicate mapper statements, Spring annotation qualification, and wildcard/signature call resolution.
- Read the Luna-added P1 regression coverage, ran its exact selector, and observed 10 expected assertion RED failures across these five behaviors.
- Implemented only the tested P1 behaviors: four local `OVER_LIMIT` sites, unsupported INSERT/DELETE static SQL, duplicate-statement ambiguity, explicit Spring annotation imports, and wildcard/arity call gaps. The exact M2 selector is now GREEN (18 tests).

## Current state

- Stage01 M2 repository-core implementation is complete. All authorized direct selectors and whitespace checks are fresh GREEN; unrelated worktree changes remain preserved.

## Changed files

- `progress/stage01-repository-core.md`
- `src/main/java/com/linguan/codemd/stage01/CapabilityReport.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryGraphTypes.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryModel.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryUnderstanding.java`
- `src/main/java/com/linguan/codemd/stage01/SnapshotVerifier.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01Analyzer.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01FailureCode.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing unrelated modifications and untracked directories observed. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | RED (expected) | Test compilation failed only for absent `RepositoryUnderstanding` and `understand(FrozenRepositoryRequest)`. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | PASS | 8 tests, 0 failures/errors/skips. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest,VerifiedSnapshotContractTest,RepositoryDiscovererTest -DfailIfNoTests=false test` | PASS | 18 tests, 0 failures/errors/skips. |
| `git diff --check` plus `git diff --no-index --check` for Stage01 source/progress | PASS | No whitespace errors. |
| `mvn -DskipTests compile` | PASS | Production compilation succeeds after withdrawing incomplete review-driven edits. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | RED (expected) | 18 tests ran; 10 P1 assertion failures exposed the new conservative-analysis requirements. |
| `mvn -DskipTests compile` | PASS | Production compile succeeded after the tested P1 implementation. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | PASS | 18 tests, 0 failures/errors/skips. |
| `mvn -Dtest=VerifiedSnapshotContractTest -DfailIfNoTests=false test` | PASS | 7 tests, 0 failures/errors/skips. |
| `mvn -Dtest=RepositoryDiscovererTest -DfailIfNoTests=false test` | PASS | 3 tests, 0 failures/errors/skips. |

## Decisions

- The analyzer will depend exclusively on the verified snapshot returned by M1 and declared verified bytes; it will not traverse a repository directory.
- P2 CFG completeness is outside this M2 slice per coordinator instruction; it is not being expanded in this task.

## Blockers

- None.

## Exact next action

- No implementation action remains. A later change must begin with the resume checks and a new scoped task decision.

## Resume checks

- Re-read this file, run `git status --short`, and inspect the three M2 tests before changing implementation.
