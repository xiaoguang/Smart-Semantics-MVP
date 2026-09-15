# Progress: Task 5 prompt/runtime acceptance RED

- Status: IN_PROGRESS
- Agent role: Task 5 RED test author
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Add focused failing tests for Step07 semantic-v2 prompt identity, strict reuse invalidation, and package-internal acceptance seam.
- Approved inputs: Task 5 brief; current Step07 design; current fixed Activity/M10 checkpoints (tests use fixtures only).
- Current branch/worktree: codex/business-lifecycle-readable-implementation / formal source-code checkout

## Completed

- Read scoped repository/source instructions, the approved Step07 correction design, the Step07 module documents, and model-job execution contract.
- Confirmed current prompt catalog still points at v1 resources and the current process fingerprint uses the v1 catalog module version.

## Current state

RED tests are complete in files owned by this task. Production classes/resources remain untouched. Task 4 publisher files and shared runtime tests remain out of scope.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptV2ContractTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessSemanticFingerprintV2Test.java`
- `src/test/java/org/sourceanalysis/app/runtime/BusinessProcessAcceptanceSampleContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessPromptV2ContractTest,BusinessProcessSemanticFingerprintV2Test,BusinessProcessAcceptanceSampleContractTest test` | RED (expected) | 8 tests run; 8 failures, no compilation errors. v2 prompt resources/identity and `BusinessProcessAcceptanceRunner` are not implemented; legacy Step07 fingerprint still matches. |

## Decisions

- Assert the eight canonical Step07 resources (`catalog`, `catalog merge`, `process`, `consolidation`, each draft/review) by semantic v2 resource identity, while preserving task-kind aliases as an implementation detail.
- Use reflection for not-yet-existing runtime seams so RED compiles without production changes and fails with an explicit contract message.
- Selector contract derives ordinal from the complete candidate array and Provider from the supplied `ModelJobExecutionConfiguration`; fixture JSON has no ordinal or binding fields.
- Acceptance result contract requires selected IDs, complete reviewed-pair job keys, and zero upstream JDT/Builder/Activity/Step08 calls; pair reuse is then validated by the formal store contract rather than by a second sample algorithm.

## Blockers

- No blocking verification remains for this RED task. Full local CI remains the parent/green task's responsibility.

## Exact next action

Commit only the owned tests and this progress file after parent confirmation; do not touch Task 4 files.

## Resume checks

- Do not touch production prompt resources, Task 4 publisher paths, `RepositoryRunMainTest`, or `ProgramGraphsPublicFixture`.
- Do not invoke a live model or source scan.
