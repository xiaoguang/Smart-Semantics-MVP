# Progress: Stage 01 proof-integrity core

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement production-only Stage 01 proof semantic-closure, typed-request, and exact failure-receipt behavior for `ProofSemanticClosureRegressionTest`.
- Approved inputs: Root/local AGENTS.md, existing Stage 01 M1–M3 source, new integrity regression tests, and the task-specified M3 closure contract.
- Current branch/worktree: shared workspace; preserve other agents' progress, tests, design, and unrelated changes.

## Completed

- Created this dedicated progress file before any production edit.
- Confirmed the required 8/8 RED baseline for `ProofSemanticClosureRegressionTest`.
- Added `Stage01Request`, `GapExpectationProfileRef`, and an exact duplicate/unknown-field rejecting JSON request parser; preserved the existing `FrozenRepositoryRequest` overload and added the typed-request analyzer overload.
- Added parsed record declaration/component graph nodes and rebuilt ProofPack assembly around candidate-specific roots, semantic SQL spans, explicit reachable dependency edges, and A09/A16 binding-rule chains.
- Scoped proof candidate selection to typed controller→service→mapper closure, preventing decoy files from supplying dependencies while retaining candidate accounting after semantic renames.
- Made expectation-gap emission search the frozen workflow scope so retry and warehouse-key evidence close their respective profile expectations.

## Current state

- All requested proof-integrity behavior is implemented and freshly verified.

## Changed files

- `progress/stage01-proof-integrity-core.md`
- `src/main/java/com/linguan/codemd/stage01/RepositoryCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/ProvenFactCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01Analyzer.java`
- `src/main/java/com/linguan/codemd/stage01/{Stage01Request,GapExpectationProfileRef,Stage01RequestJson}.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=ProofSemanticClosureRegressionTest test` | EXPECTED RED | 8 failures, covering root/reachability, SQL spans, binding chains, decoy isolation, rename accounting, expectation search, and typed JSON request seams. |
| `mvn -Dtest=ProofSemanticClosureRegressionTest test` | PASS | 8 tests, 0 failures/errors. |
| `mvn -Dtest=ProvenFactExtractionTest,ProofMutationTest test` | PASS | 8 tests, 0 failures/errors. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest,RepositoryIntegrityRegressionTest test` | PASS | 27 tests, 0 failures/errors. |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | PASS | 7 tests, 0 failures/errors. |
| `mvn -Dtest=JshErpStage01AcceptanceTest test` | PASS | 1 test, 0 failures/errors. |
| `git diff --check` | PASS | no whitespace errors. |

## Decisions

- Preserve `analyze(FrozenRepositoryRequest)` for the existing M3 suite while adding the new typed request seam.
- SQL Proof roots use semantic byte spans inside the frozen XML source, while every proof closure has an atom-specific root and explicit directed dependency edges.

## Blockers

- None.

## Exact next action

- None; task complete.

## Resume checks

- Re-read this file and the nearest AGENTS.md, check scoped git status, then run only the directly covering selectors.
