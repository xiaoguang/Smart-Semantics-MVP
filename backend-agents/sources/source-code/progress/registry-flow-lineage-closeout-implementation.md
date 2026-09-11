# Progress: Registry-flow lineage closeout implementation

- Status: COMPLETE
- Agent role: Terra/xhigh M4 Registry-to-BusinessFlows lineage GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Private `FiniteKeyFlowTaskCompiler` validation only: close the existing M3 Registry receipt run/control/upstream join to the already reopened current BusinessFlows publication.
- Approved inputs: `progress/registry-flow-lineage-closeout-diagnosis.md`, the frozen Luna public negative, and the current M3 publisher/consumer wire.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared changes.

## Completed

- Read the complete Sol/xhigh lineage diagnosis before editing production code.
- Recorded the frozen pre-change evidence: 8 direct tests, 1 failure, 0 errors, 0 skipped, exit 1; all three generic-store-valid decoys are accepted by M4 because the Registry-to-current-BusinessFlows run/control/upstream join is absent.
- Implemented the private M4 join: the consumer passes the existing reopened BusinessFlows publication into its Registry reopen path; it now requires the same run, exact controls, seven M3 upstream references, and containment of all five reopened BusinessFlows semantic payload references.
- Corrected the direct compile omission exposed by root's first fixed 44-selector aggregate: the new private payload-reference helper requires the existing `ArtifactReference` import. The aggregate stopped in main compilation at lines 151/207/208 before Surefire started (0 tests executed, exit 1, session 59469); this is not a behavioral RED.
- Root verified the corrected fixed 44-selector aggregate in session `16470`: 90 unit tests and 1 config integration test passed with numeric exit 0. The JDK 17 JAR build subsequently passed in session `82282` after final bounded cleanup work.

## Current state

- The bounded M4 Registry-to-current-BusinessFlows lineage slice is COMPLETE: its corrected fixed aggregate and the subsequent JDK 17 JAR build are root-verified PASS. Global quality is NOT PASS because PMD session `3296` retains 26 out-of-scope warnings; this slice does not accept full Step 05 or Step 06.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java` (private M4 Registry-to-current-BusinessFlows run/control/five-reference validation)
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` (8 analyzer-visible direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java` (24 analyzer-visible direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java` (20 analyzer-visible direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java` (4 WIP-new/induced PMD mechanical cleanups)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java` (5 WIP-new/induced PMD mechanical cleanups)
- `progress/registry-flow-lineage-closeout-implementation.md` (owned lineage evidence)
- `progress/business-flow-scoped-quality-cleanup.md` (owned combined quality evidence)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen Luna lineage selector | RED; numeric exit 1 | 8 tests, 1 failure, 0 errors, 0 skipped: generic-store-valid Registry decoys are accepted before run/control/five-public-upstream validation exists. |
| Exact six-file Spotless apply | PASS; numeric exit 0 | Exactly six production files selected; 2 changed to clean (`FiniteKeyFlowTaskCompiler`, `FlowCompilation`), 4 already clean, 0 skipped. |
| Exact six-file Spotless check | PASS; numeric exit 0 | All six selected production files clean; 0 needed changes, 6 cache-skipped. |
| Root fixed 44-selector aggregate, first attempt (session 59469) | COMPILE BLOCKED; numeric exit 1 | Main compilation failed because `ArtifactReference` was unresolved in the new M4 helper/checks; 0 tests executed. Only the missing import is authorized here. |
| Exact one-file Spotless apply after import correction | PASS; numeric exit 0 | Exactly `FiniteKeyFlowTaskCompiler.java` selected; 0 changed, 1 already clean, 0 skipped. |
| Exact one-file Spotless check after import correction | PASS; numeric exit 0 | One selected production file clean; 0 needed changes, 1 cache-skipped. |
| Root corrected fixed 44-selector aggregate (session `16470`) | PASS; numeric exit 0 | 90 unit tests and 1 config integration test passed. |
| Root JDK 17 JAR build (session `82282`) | PASS; numeric exit 0 | Built after the final bounded cleanup work. |
| Root PMD check (session `3296`) | FAIL; numeric exit 1 | 26 unmodified, out-of-scope warnings remain; global quality is not PASS. |

## Decisions

- Pass the already reopened `ReopenedAnalysisStepPublication` into the private Registry reopen path; compare the M3 address run ID and receipt controls with it exactly.
- Derive the five current BusinessFlows semantic `ArtifactReference`s from reopened descriptors; require exactly seven M3 upstream references and containment of all five.
- Keep opaque R0 upstream references, Registry semantic body, schema/API, Provider behavior, IDs, and task identity out of scope.

## Blockers

- None for this bounded lineage slice. The global PMD and SpotBugs baselines remain out of scope.

## Exact next action

- No further action in this bounded lineage slice; preserve the global quality and Step 05 acceptance boundaries.

## Resume checks

- This bounded lineage slice is complete. Full Step 05 and Step 06 remain unaccepted, and global quality is not PASS.
