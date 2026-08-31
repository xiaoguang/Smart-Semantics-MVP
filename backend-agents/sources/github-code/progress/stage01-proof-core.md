# Progress: Stage 01 M3 proof core

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement the Stage 01 M3 proof core under `src/main/java/com/linguan/codemd/stage01/**`, with only this progress file as the durable-document change.
- Approved inputs: Root and local AGENTS.md, Stage 01 design sections 7/8/9/11/12, DESIGN.md M3 F01–F08/A01–A20/A16 closure, existing M1/M2 production code, and M3 tests/test support.
- Current branch/worktree: shared workspace; unrelated root and `web-next` changes and untracked directories are preserved.

## Completed

- Created this progress file before production edits.
- Read repository and local instructions, M3 stage contract, design contract, and M3 test-author handoff.
- Ran the exact M3 selector and confirmed expected RED at the absent public M3 seam: `Stage01Result` and `Stage01Analyzer.analyze(FrozenRepositoryRequest)`.
- Read the full M3 reflective access contract and synthetic fixture source shape, plus the existing M1/M2 source seam and graph identity rules.
- Added the public immutable M3 result, Fact/atom/accounting, ProofPack, and GapLedger records; added M1→M2→M3 `analyze` orchestration and byte-revalidated proof construction.
- Indexed parsed guard/throw/return nodes independently of route ownership so a broken route prefix rejects F01 while preserving the full candidate accounting denominator.
- Derived canonical, root-independent IDs from frozen snapshot identity and ordered payloads; every admitted proof reopens and hashes its M1 source span.
- Implemented the exact F01–F08/A01–A20 candidate registry, composite rejection dispositions, five frozen expectation gaps, and required result-type/projection/config/namespace dependencies for the available and optimistic-update closures.
- M3, M1, and M2 direct selectors all pass after the test-owner corrected the route-prefix test mutation helper.

## Current state

- M3 is complete and verified. No work remains in this scope.

## Changed files

- `progress/stage01-proof-core.md`
- `src/main/java/com/linguan/codemd/stage01/Stage01Analyzer.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01FailureCode.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/{Stage01Result,ProvenSourceFacts,ProvenFactSet,CodeFact,FactAtom,FactValue,CandidateAccounting,AtomDisposition,ProofPack,Proof,ProofNode,ProofLocator,ProofEdge,GapLedger,GapProfile,CapabilityGap,FactRejection,ExpectationGap,SearchedScope,AbsenceEvidence,ProvenFactCompiler}.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=ProvenFactExtractionTest,ProofMutationTest test` | EXPECTED RED | Test compilation fails only because `Stage01Result` and `Stage01Analyzer.analyze(FrozenRepositoryRequest)` are absent. |
| `mvn -Dtest=ProvenFactExtractionTest,ProofMutationTest test` | PASS | 8 tests, 0 failures/errors. |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | PASS | 7 tests, 0 failures/errors. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest test` | PASS | 18 tests, 0 failures/errors. |
| `git diff --check` | PASS | no whitespace errors. |

## Decisions

- The implementation will derive all M3 candidates/proofs from M1 verified bytes and M2 graph/model values; fixture file names will not be used as the trigger.
- Route-prefix loss remains an F01 composite rejection rather than suppressing all eight candidate Facts; parsed M2 semantic nodes stay available even when no entry can be admitted.

## Blockers

- None.

## Exact next action

- None; task complete.

## Resume checks

- Re-read this file and nearest AGENTS.md, run `git status --short`, then use only the selectors directly covering M3, M1, and M2.
