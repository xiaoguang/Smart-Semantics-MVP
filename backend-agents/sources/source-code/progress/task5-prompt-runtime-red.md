# Progress: Task 5 prompt/runtime acceptance RED

- Status: COMPLETE
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
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessAcceptanceSampleTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessPromptV2ContractTest,BusinessProcessSemanticFingerprintV2Test,BusinessProcessAcceptanceSampleTest test` | RED (expected) | 5 tests run; 5 failures, no compilation errors. The v2 prompt resources and semantic identity are absent, and the package-internal real discovery seam is not implemented. |

## Decisions

- Assert the eight canonical Step07 resources (`catalog`, `catalog merge`, `process`, `consolidation`, each draft/review) and all ten task-kind aliases (including catalog-shard draft/review) by semantic v2 resource identity.
- Use reflection for the not-yet-existing package-internal discovery seams so RED compiles without production changes and fails with an explicit contract message.
- Selector derives candidate ordinal from the complete catalog result and Provider from the supplied `ModelJobExecutionConfiguration`; fixture catalog JSON contains neither ordinal nor binding fields.
- The acceptance test requires actual saved DRAFT+REVIEW pairs, verifies their original-ordinal Provider binding, and then proves the formal full run reuses those pairs while executing only the remaining candidate and consolidation.

## Blockers

- No blocking verification remains for this RED task. Full local CI remains the parent/GREEN task's responsibility.

## Exact next action

Implement the semantic-v2 resources/fingerprint and package-internal catalog/sample seams; do not touch Task 4 publication files.

## Resume checks

- Do not touch production prompt resources, Task 4 publisher paths, `RepositoryRunMainTest`, or `ProgramGraphsPublicFixture`.
- Do not invoke a live model or source scan.
