# Progress: registered capture receipt projection

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Extract the mechanical, path-free projection from a freshly reopened Local Git registration to the existing Step 01 `CaptureReceiptView`, so production runtime composition no longer duplicates acceptance-test logic.
- Approved inputs: Active Step 01 contract; existing capture registry and `CaptureReceiptView` identity rules.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed `CaptureReceiptView` construction is duplicated in the inventory acceptance test and the fixed-repository acceptance test, while the production Step 01 executor correctly expects that typed value.
- Added a direct RED using the existing synthetic Local Git capture fixture.
- Implemented `RegisteredCaptureReceiptProjector`; it derives the existing complete-capture scope, text/media file dispositions, and canonical `file:` IDs only from freshly reopened registration metadata and one frozen-request reference.
- The result equals the established acceptance view exactly, so Step 01 can receive it without a test-local helper.

## Current state

- The mechanical capture-to-admission projection is available for global runtime composition. It does not yet supply the queued run's stored frozen-request bytes or configuration; that is the next runtime input-resolver seam.

## Changed files

- `progress/registered-capture-receipt-projection.md`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifierTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/RegisteredCaptureReceiptProjector.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=VerifiedSourceInventoryPublicationSpecifierTest#projectsARegisteredCaptureIntoTheExistingPathFreeReceiptView test` | RED | Initial test setup named a nonexistent fixture policy helper; after root-cause check, the corrected selector reached one expected missing-projector assertion. |
| Same selector after implementation | PASS | 1 test, 0 failures/errors/skips; projected view equals the accepted local-capture view. |
| Scoped Spotless | PASS | The new projector and modified direct test are formatted. |

## Decisions

- This projector will be mechanical only: registration metadata plus manifest entries become the existing view. It will not read a worktree, alter the frozen request, or interpret any source code.

## Blockers

- None.

## Exact next action

- Implement a safe queued-run input resolver that supplies the existing Step 01 executor with persisted request bytes, registered capture metadata, and approved runtime profiles.

## Resume checks

- Its output must equal the existing acceptance view exactly and be accepted unchanged by `FrozenRequestAdmission`.
