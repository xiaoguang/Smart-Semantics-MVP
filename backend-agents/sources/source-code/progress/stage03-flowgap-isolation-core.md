# Progress: Stage03 FlowGap isolation core

- Status: COMPLETE
- Agent role: Stage03 FlowGap isolation production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Restrict every Capsule task and provider-response Gap closure to the current compiled
  Stage02 Flow/Capsule while retaining repository-level FlowGap projection and accounting.
- Approved inputs: Scoped AGENTS, public FlowGap isolation seam and its test progress, Stage03
  CapsuleContext/assembly implementation, and existing Stage03 typed public records.
- Current branch/worktree: Shared worktree; preserve all unrelated existing changes.

## Completed

- Created this owned progress file before modifying production code.
- Read scoped guidance, the complete public regression contract, and its recorded expected RED.
- Reproduced the public selector: 3 tests, 1 failure. The foreign-Gap response completed instead
  of throwing `MODEL_RESPONSE_REFERENCE_INVALID`; the two positive isolation/accounting tests passed.
- Traced the response data flow through `parseR1` → `resolveQuestionGaps` → `CapsuleContext.gapIds`.
  The context accepts the foreign ID because `CapsuleContext.of` unconditionally adds every
  `Stage02Result.flowGaps()` item.
- Confirmed repository assembly separately enumerates every capsule `allowedGaps` and every
  Stage02 `flowGaps` for pending questions/accounting. This must remain repository-wide.
- Rebuilt the Capsule Gap map from only its `allowedGaps` plus IDs declared by the current
  `FlowSlice`. An allowed Gap requires its Stage01 ledger reason; a FlowGap requires one exact
  Stage02 ledger match and the current entry ownership. Closure now checks that exact union.
- Narrow GREEN: all three FlowGap isolation tests pass, including foreign provider-reference
  rejection with `MODEL_RESPONSE_REFERENCE_INVALID` and repository-level pending/accounting retention.
- Full Stage03 direct regression is GREEN: 56 tests with no failures/errors.
- Stage01/02 direct regression is GREEN: 79 tests with no failures/errors.

## Current state

- Complete. Per-Capsule Gap admission is flow-local; repository-level pending question and
  accounting assembly intentionally retain every Stage02 FlowGap.

## Changed files

- progress/stage03-flowgap-isolation-core.md
- src/main/java/com/linguan/codemd/stage03/Stage03Generator.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03FlowGapIsolationTest test` | RED | 3 tests / 1 failure: foreign provider Gap reference did not fail closed; positive isolation/accounting checks passed. |
| `mvn -Dtest=Stage03FlowGapIsolationTest test` | GREEN | 3 tests, 0 failures/errors. |
| `mvn -Dtest='Stage03*Test' test` | GREEN | 56 tests, 0 failures/errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures/errors. |
| `git diff --check` | GREEN | No whitespace errors reported. |

## Decisions

- Keep repository pending-question and accounting assembly repository-wide; confine only the
  per-Capsule task/context and model-response references to flow-owned Gap IDs.
- The minimal hypothesis is to construct the context Gap map from the capsule ledger plus only
  IDs listed by the current `FlowSlice`, resolving a FlowGap by ID and requiring its `entryId` to
  equal the Flow entry before admitting its code as the reason.
- The exact current-context union is defended in `validateClosure`; the independent repository
  assembly remains the only consumer that admits all Stage02 FlowGaps.

## Blockers

- None identified.

## Exact next action

- None; this vertical slice is closed.

## Resume checks

- Verify only Stage03 production and this owned progress file change for this vertical slice.
