# Progress: Stage 03 formula core

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Derive formula metrics, field dimensions, and typed reader items from public Stage 02 allowed fact/atom semantics only.
- Approved inputs: scoped AGENTS, TDD guidance, Stage 03 design, Formula RED contract, current Stage 03 public records and production.
- Current branch/worktree: shared worktree; preserve unrelated changes.

## Completed

- Read the Formula public seam: `Stage03Generator.generate` returns `RepositoryBusinessModel` and `NineSectionPlan`, and all expected formula values are derived from an admitted `AVAILABLE_FORMULA` atom.
- Replaced the hardcoded all-Flow metric with deterministic structured formula extraction over `AllowedFactView`/`AllowedAtomView`: a metric and field have the exact formula atom basis, while display text derives role, canonical formula, operators, and operands.
- Added non-owning `FIELD_FORMULA` and `METRIC_FORMULA` items. They retain empty atom bases, refer to the sole existing atom owner, and render the frozen typed formula slots only.
- Extended the built-in contracts and made nonempty registry validation require the exact Section 5 FIELD and Section 7 METRIC templates/owners.

## Current state

- COMPLETE: Formula derivation, non-owning reader references, and strict custom contracts are green after the fixture owner migrated the complete registry. No legacy registry bypass was added.

## Changed files

- `progress/stage03-formula-core.md` (this file)

## Decisions

- Formula parsing will accept only canonical structured operand/operator forms; every generated display, field role, operand, metric basis, and slot is derived from the admitted atom, never a fixture literal.
- Formula-derived reader items will reference the pre-existing unique atom owner item and retain no independent atom basis ownership.
- A nonempty custom reader registry must include closed FIELD and METRIC contracts; no legacy fallback will be accepted.
- `ReaderItem.basisAtomIds` remains the existing ownership channel: field/metric items will carry `referencedItemKeys` to the original atom owner rather than duplicate the atom basis. The new test's current basis-based lookup conflicts with the pre-existing integrity invariant and requires a test fixture/assertion migration to reference lookup.

## Blockers

- None.

## Exact next action

- None; assigned formula implementation and verification are complete.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03FormulaTest test` | RED | 1 test, 1 grouped failure / 8 assertions; metric basis contains all 20 atoms, no exact formula field, and no typed Section 5/7 formula reader items. |
| `mvn -Dtest=Stage03FormulaTest test` | GREEN | 1 test, 0 failures, 0 errors; exact formula basis/model values and non-owning typed reader references are preserved. |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest,Stage03IntegrityTest,Stage03CompletenessTest,Stage03SemanticTest,Stage03ProofDensityTest,Stage03FormulaTest test` | GREEN | 30 tests, 0 failures, 0 errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors reported. |
