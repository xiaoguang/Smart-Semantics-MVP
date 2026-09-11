# Progress: Business flow source-gap origin implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded existing-contract GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One Step 05 priority-1 source-ledger Gap-provenance slice only: project strict source Gap origin fields from fresh-reopened Step 04 gap-ledger and evidence-graph descriptors into M2 capsule views.
- Approved inputs: Frozen `BusinessFlowProvenanceTest#freshReopenedSourceGapViewsCarryExactLedgerAndEvidenceGraphProvenance` RED; published Step 05 §8.1.3 priority-1 contract; root-provided exact source-Gap requirements; existing capsule production code; and current reopened Step 04 input shape.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the applicable agent guidance, both implementation plans, and the TDD/executing-plans instructions before production edits.
- Confirmed the parent-provided frozen public RED: numeric exit 1; 1 test, 1 failure, 0 errors, 0 skipped; real ledger/evidence/M2 prerequisites complete and the failure is the missing required `originKind` field.
- Created this owner-only progress file before production edits.
- Passed the frozen source-Gap selector after the production change.
- Passed the complete two-test provenance class before formatting.
- Passed the same two-test provenance class after selected production formatting.

## Current state

- The bounded production implementation is complete: source-ledger Gap views carry strict origin and descriptor provenance in the capsule package.
- This bounded source-ledger Gap-origin slice is complete. Source ledger rows provide `FACT` scope, row code, opaque candidate-denominator keys, exact ledger origin, and the resolved EvidenceGraph descriptor; the pre-existing budget Gap remains the published empty-evidence/null-ledger `CAPSULE_PROJECTION` case.
- The source-Gap method and complete provenance class are green before and after selected production formatting. Gap compiler-origin/public M3 normalization, M3 replay, Provider stripping, and full Step 05 remain outside this result.

## Changed files

- `progress/business-flow-source-gap-origin-implementation.md` (owned; created before production edits)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java` (authorized; immutable typed source-Gap origin transport)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java` (authorized; derive source Gap from the exact reopened ledger row and EvidenceGraph descriptor)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java` (authorized; persist typed source-Gap provenance in the existing M2 v7 canonical body)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen source-Gap public selector evidence | RED; numeric exit 1 | 1 test, 1 failure, 0 errors, 0 skipped; the real ledger/evidence/M2 prerequisite path completes, then the Gap view lacks required `originKind`. |
| `BusinessFlowProvenanceTest#freshReopenedSourceGapViewsCarryExactLedgerAndEvidenceGraphProvenance` | PASS; numeric exit 0 | 1 test, 0 failures, 0 errors, 0 skipped. |
| `BusinessFlowProvenanceTest` before formatting | PASS; numeric exit 0 | 2 tests, 0 failures, 0 errors, 0 skipped. |
| Three-file absolute selected Spotless apply | PASS; numeric exit 0 | 3 files selected; 1 changed to clean, 2 already clean, 0 skipped. |
| Three-file absolute selected Spotless check | PASS; numeric exit 0 | 3 files selected; 0 need changes, cache skipped all 3 as clean. |
| `BusinessFlowProvenanceTest` after formatting | PASS; numeric exit 0 | 2 tests, 0 failures, 0 errors, 0 skipped. |

## Decisions

- The exact Step 04 gap-ledger row is the only authority for `originKind`, `reason`, scope `FACT`, affected candidate-denominator keys, and the reopened ledger descriptor reference.
- Evidence references may use only the actual reopened EvidenceGraph descriptor after each row `evidenceNodeId` resolves there; opaque node IDs and affected denominator keys are never converted into artifact references.
- Preserve existing source Gap/Flow/Capsule/signal IDs, eligibility, and budgets. Do not change schema beyond already-required v7, versions, compiler/public M3 behavior, replay, or Provider stripping.

## Blockers

- None for this bounded source-ledger Gap-origin implementation. Compiler-origin/public M3 normalization, M3 replay, Provider stripping, and full Step 05 need their own frozen REDs.

## Exact next action

- Release the Maven lease to Luna for the separately scoped bounded-source test. Do not widen this result to a full carrier or Step 05 acceptance.

## Resume checks

- The shared worktree is intentionally dirty. Only this progress file and the authorized capsule package production files are in scope for this slice.
- The Maven lease is released. No test/fixture/design/schema/source changes, network, Provider, source capture, commit, push, or subagents were used by this owner.
- Completing this source-Gap provenance slice does not accept the full v7 carrier, full Step 05, compiler-origin/public M3 normalization, replay, or Step 06 Provider stripping.
