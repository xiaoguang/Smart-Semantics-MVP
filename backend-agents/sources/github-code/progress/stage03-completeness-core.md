# Progress: Stage 03 completeness core

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Turn the two public `Stage03CompletenessTest` contracts green by changing only Stage 03 production and this progress record.
- Approved inputs: scoped AGENTS, Stage 03 design, completeness RED progress/tests, integrity review, and the current Stage 03 public production seam.
- Current branch/worktree: shared worktree; preserve all unrelated changes.

## Completed

- Read the scoped guidance, Stage 03 design sections for frozen registries/tasks/rounds, the exact completeness RED contracts, and the current integrity review.
- Confirmed the public seams are `Stage03Generator.generate`, `FlowModelTask`, and immutable Stage 03 result records; automated checks remain scripted-provider-only.
- Added canonical child registry identity records and exact typed bindings to the per-Capsule task admission: eligible term/claim/question entries, anchor-bound technical policies, and frozen reader-template/ownership entries.
- Added canonical accepted R1/R2 protocol serialization for receipt hashing and made interpretation lineage part of both Flow interpretation and Stage 03 result identities.
- Completed the full Stage 03 direct selector without regression.
- Completed the direct Stage 01/02 regression selector without regression.

## Current state

- Both completeness contracts and all requested direct regression selectors are GREEN.

## Changed files

- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java`
- `progress/stage03-completeness-core.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03CompletenessTest test` | RED | 2 tests, 2 assertion failures, 0 errors; exact registry binding and semantic canonicalization gaps reproduced. |
| `mvn -Dtest=Stage03CompletenessTest#taskAdmissionContainsChildRegistryDigestsAndEveryTypedBinding test` | GREEN | 1 test, 0 failures, 0 errors; child IDs/digests and all required typed bindings are task-visible. |
| `mvn -Dtest=Stage03CompletenessTest#interpretationAndResultIdentityUseCanonicalRoundReceipts test` | GREEN | 1 test, 0 failures, 0 errors; reordered semantic JSON has identical receipts/result IDs, while an eligible term selection remains distinct. |
| `mvn -Dtest=Stage03CompletenessTest test` | GREEN | 2 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest,Stage03IntegrityTest,Stage03CompletenessTest test` | GREEN | 26 tests, 0 failures, 0 errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors reported. |

## Decisions

- Preserve per-Capsule payload isolation and existing byte budgets.
- Canonicalize only accepted strict response semantics; retain strict parsing and reject malformed/unapproved fields before receipt construction.
- Do not edit tests, fixtures, or design documents; no external model/network calls.

## Blockers

- None.

## Exact next action

- None; assigned implementation and verification are complete.

## Resume checks

- Keep changes inside `src/main/java/com/linguan/codemd/stage03/` and this file; preserve the shared worktree.
