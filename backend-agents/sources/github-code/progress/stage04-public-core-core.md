# Progress: Stage04 public core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Scope: Java public core only: source registration, public generate/improve/validate/trace façade, and existing Stage01→04 orchestration. No CLI, HTTP, customer-code execution, or archive schema expansion.
- Approved inputs: scoped `AGENTS.md`; Stage04 public-core RED contract; current Stage01–04 production seams and archive-v2/persisted-recovery behavior.
- Current branch/worktree: shared and pre-existing dirty; unrelated work is preserved.

## Completed

- Read scoped guidance and checked the shared dirty worktree.
- Confirmed the public boundary: registered source input is revalidated at generation time; Stage01/02/03 request/result objects are rebuilt from the fixed public configuration, not reused from an earlier snapshot binding.
- Reproduced the supplied focused RED before production edits.
- Added the bounded public M8 facade, strict read-only filesystem registration resolver, and the rootless archive control projection needed to keep private roots and registration lookup IDs out of Candidate content identity.
- Repaired the registration resolver's no-follow policy after it incorrectly rejected the host's system-level `/var` transport alias; it now rejects a supplied registry/binding symlink while allowing the host's normal temporary-directory backing.
- Verified the complete requested direct regression surface and diff whitespace check.

## Current state

- The focused public-core selector is GREEN. A corrected fixture now gives the relocated independent archive its own scripted lifecycle adapter; production re-executes both rounds and derives the same Candidate content identity without retaining either private root or per-series lifecycle IDs in the content root.

## Changed files

- `progress/stage04-public-core-core.md`
- `src/main/java/com/linguan/codemd/stage04/CodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/ImprovementRequest.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemSourceRegistry.java`
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared worktree is pre-existing dirty; unrelated changes are preserved. |
| `mvn -Dtest=Stage04PublicCoreTest test` | RED (expected) | Test compilation reports the missing public seam only: `FilesystemSourceRegistry`, `CodeToMarkdownAgent`, `DefaultCodeToMarkdownAgent`, and `ImprovementRequest` (24 symbol errors). |
| `mvn -Dtest=Stage04PublicCoreTest test` | RED (1 error / 9 tests) | The first 8 checks pass. The final relocated-root run reuses an exhausted scripted adapter counter: its required new R1 call accesses `canonicalRounds[2]`, is caught inside Stage 03, and becomes `MODEL_RESPONSE_INVALID` at `Stage04PublicCoreTest:96`. |
| `mvn -Dtest=Stage04PublicCoreTest test` | PASS | 9 tests, 0 failures, 0 errors. Includes registration exactness/no-follow/reverification, initial lifecycle generation, full validation/Trace, fresh completed-slot idempotency, independent relocated R1/R2, and strict `NOT_IMPLEMENTED` improvement boundary. |
| `mvn -Dtest='Stage04*Test' test` | PASS | 57 tests, 0 failures, 0 errors across all direct Stage04 selectors. |
| `mvn -Dtest='CapabilityAccountingTest,JshErpStage01AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,VerifiedSnapshotContractTest,JshErpStage02AcceptanceTest,Stage02CompilerTest' test` | PASS | 79 tests, 0 failures, 0 errors across direct Stage01/02 regression classes. |
| `mvn -Dtest='Stage03*Test' test` | PASS | 69 tests, 0 failures, 0 errors across direct Stage03 selectors. |
| `git diff --check` | PASS | Exit 0; no whitespace diagnostics. |

## Decisions

- `generateCandidate` will accept only a registered request whose rootless canonical controls match the stored registration; a relocated snapshot is a private binding change, not Candidate content input.
- The public façade will use an injected scripted/recorded runtime adapter only through `LifecycleProviderBridge`; it will never call a live Provider or execute customer code.
- `improve` remains a strict, stable `NOT_IMPLEMENTED` boundary after request validation.
- Archive v2 continues to retain full actual lifecycle records for validation; Candidate content identity uses a separate canonical task/response/runtime root so private registration IDs and series-slot started-event IDs cannot alter content identity.

## Blockers

- None.

## Exact next action

- None; implementation and requested verification are complete.
