# Progress: Business Flow owner replay implementation

- Status: COMPLETE
- Agent role: Terra/xhigh M3 owner-replay GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Published M3 owner-replay vertical: fresh-reopen persisted M1/M2 and predecessors, reproject through the existing M2 owner, canonicalize with the same owner body encoder, and compare full M2 payload bytes before an M3/public install.
- Approved inputs: Published Step 05 §8.3 owner-replay contract, current `EvidenceCapsuleProjector` and `CapsuleProjectionModulePublisher` implementation, and frozen Luna RED `BusinessFlowProvenanceTest#rejectsSelfConsistentRehashedFactMutationBeforeStep05Receipt` (1 test, 1 failure, 0 errors, 0 skips: generic M2 install/reopen succeeds but M3 currently accepts the rehashed owning-Fact mutation).
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this tracked owner progress checkpoint before any future production edit.
- Read published Step 05 §8.3. It requires M3 to fresh-reopen exact M1/M2 plus the source/graph/fact predecessors, read the persisted M2 `capsuleProjectionProfile`, reproject through the existing `EvidenceCapsuleProjector`, and byte-compare the complete rebuilt canonical M2 payload body before any M3/public installation. A generic-valid rehashed M2 with a changed owning Fact atom/view must therefore fail before the Step 05 receipt is installed.
- Confirmed `EvidenceCapsuleProjector` already has the required fresh-reopen behavior and accepts the existing `VerifiedSourceTextReader`; reopening verified bytes through that reader is allowed and is not AST/source reanalysis.
- Confirmed `CapsuleProjectionModulePublisher.payload(...)` currently owns the M2 `payload` construction through private `projectionBody(...)`, followed by `capsuleProjectionId` derivation. The future shared owner seam must canonicalize that complete body (including its projection ID), not export a partial-ID comparator or a second encoder.
- Confirmed `FlowPublicationSpecifier` already fresh-reopens source/discovery/graph/fact and M1/M2 before material/public installation, but its unique constructor currently lacks `VerifiedSourceTextReader` and it does not reproject/whole-body-compare M2. The future change is restricted to injecting that existing reader, reading the persisted profile, invoking the existing projector, and comparing the owner-produced full canonical body before `material(...)` or either install.
- The frozen public RED verifies a canonical, rehashed, receipt/lineage/control-consistent M2 mutation: two roots/upstream equality and generic M2 reopening succeed, then M3 incorrectly publishes without throwing. Its required failure boundary is before any Step 05 receipt.
- Added `CapsuleProjectionModulePublisher.canonicalProjectionPayloadBody(...)`, the M2 owner's single pure canonical encoder for the complete payload body. It derives and includes `capsuleProjectionId`, and the M2 publisher now parses that encoder's output when building its payload envelope.
- Added the existing `VerifiedSourceTextReader` as the required unique third `FlowPublicationSpecifier` constructor argument. Before materialization or either M3/public install, M3 now reads the persisted projection profile, reprojects using the existing `EvidenceCapsuleProjector`, and rejects any byte difference between the reopened M2 payload body and the M2-owner canonical full body.
- Formatted the two owned production files after Luna froze all five constructor-only test migration sites.

## Current state

- This owner-replay slice is complete: the rehashed-M2 regression now fails at the required pre-install M3 owner-replay boundary.
- The M3 constructor remains unique with the required source reader; Luna migrated and froze the five constructor-only test sites. No compatibility constructor was added.
- Full Step 05 is not accepted. Bounded closure, provider stripping, compiler-origin Gap normalization, and any other deferred vertical remain outside this completed owner-replay slice.

## Changed files

- `progress/business-flow-owner-replay-implementation.md` (owner-replay implementation and verification record)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java` (single pure full M2 payload-body encoder, also used by M2 publication)
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java` (required source-reader dependency and pre-install owner-replay gate)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped owner-replay source/design inspection | COMPLETE | §8.3 requires exact M1/M2/predecessor reopen → persisted-profile projector replay → M2-owner full body canonical bytes comparison before any M3 install. |
| Frozen Luna public RED | RED; numeric exit 1 | `BusinessFlowProvenanceTest#rejectsSelfConsistentRehashedFactMutationBeforeStep05Receipt`: 1 test, 1 failure, 0 errors, 0 skips; rehashed generic-valid M2 is accepted by current M3 and Step 05 receipt is not prevented. |
| Absolute two-file Spotless apply | GREEN; numeric exit 0 | Both owned production files were selected; Spotless reported 2 clean files. |
| Absolute two-file Spotless check | GREEN; numeric exit 0 | Both selected production files required no changes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest test` | GREEN; numeric exit 0 | 5 tests, 0 failures, 0 errors, 0 skipped. This includes `rejectsSelfConsistentRehashedFactMutationBeforeStep05Receipt`. |
| Scoped `git diff --check` | GREEN; numeric exit 0 | No whitespace errors in the two owned production files and this progress record. |

## Decisions

- The completed GREEN reuses the existing `EvidenceCapsuleProjector`, injects the existing `VerifiedSourceTextReader` into the unique M3 constructor, and uses the M2 owner’s pure full-body canonical encoder. It fresh-reopens the exact persisted inputs and compares whole canonical M2 payload bytes before any M3/public installation.
- No partial identity checklist, AST/source reanalysis, compatibility constructor, new subsystem, schema increment, bounded-coverage behavior, or Provider work is in scope.
- The public owner encoder must include the deterministic `capsuleProjectionId` in the serialized body because M3 compares it with the persisted M2 `payload`; M2 installation must call the same encoder. M3 may parse the persisted profile only to construct the existing typed `CapsuleProjectionProfile` for replay.

## Blockers

- None for this bounded owner-replay slice. Full Step 05 remains unaccepted pending its separately scoped verticals.

## Exact next action

- Release the Maven lease. Do not expand into compiler-origin Gap normalization, bounded closure, provider stripping, or other Step 05 work without a separate frozen RED and scope assignment.

## Resume checks

- Maven lease is released after the assigned post-format provenance selector passed. No test/design/schema/source/network/Provider/commit/push/subagent action was taken by this owner.
- Full Step 05 and the previously deferred bounded-coverage/Provider work remain outside this completed owner-replay slice.
