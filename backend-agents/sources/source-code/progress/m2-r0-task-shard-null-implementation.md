# Progress: M2 R0 task shard null implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Make R0 `GenerationReceiptV3` serialize and identity-bind the required `taskShardId: null` field; do not alter provider behavior or any other Step 06 module.
- Approved inputs: `AGENTS.md`, Step 06 §6.7.2.1, the completed Luna RED in `progress/local-interpretation-publication-carrier-tests.md`, and the current M2 receipt carrier source/tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, M2/M5 publication-readiness contract, target implementation plans, and the explicit Luna RED.
- Confirmed the narrow missing field is absent from both the R0 receipt wire JSON and the canonical identity preimage.
- Reproduced the frozen RED: one test, one assertion failure aggregating six field/identity differences across two R0 receipts; no errors or Provider replay.
- Added `taskShardId: null` in the one shared receipt projection used both by canonical wire serialization and the receipt identity preimage.
- Re-ran the single behavior and all directly affected M2/M3/M4/M5 consumers successfully.

## Current state

- The approved test is RED before any production edit. The only permitted Green change is to make the already-required Java-null scope become explicit canonical JSON null in both the wire record and its receipt-ID projection.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalGenerationReceipt.java`
- `progress/m2-r0-task-shard-null-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#persistsConfiguredAndObservedR0IdentityAsGenerationReceiptV3WithoutReplayingProvider test` | RED | 1 test, 1 failure (six aggregated assertions), 0 errors/skips: `taskShardId` absent and receipt ID mismatches the complete null-bearing oracle. |
| Same selector after production change | PASS | 1 test, 0 failures/errors/skips; persisted complete receipt and independently recomputed identity agree. |
| Same selector after formatting | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest,RegistryProposalRunnerTest,RepositoryInterpretationRegistryFreezerTest,FiniteKeyFlowTaskCompilerTest,InterpretationRunnerTest test` | PASS | 15 tests, 0 failures/errors/skips across five direct consumer classes. |

## Decisions

- `taskShardId` remains Java `null` for R0, while canonical JSON must contain it as a present JSON null.
- The field must be included before `generationReceiptId` is calculated, so existing incomplete receipt IDs are intentionally invalidated.

## Blockers

- None.

## Exact next action

- Return the completed narrow M2 Green evidence to the parent; no further production change is authorized in this task.

## Resume checks

- Recheck this progress file, the one test selector, and only the receipt source before editing; preserve shared-worktree changes.
