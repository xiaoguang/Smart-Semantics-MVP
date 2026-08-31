# Progress: Stage 03 formula semantic RED test

- Status: COMPLETE
- Agent role: Stage03 formula conservation test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded public-seam formula/field conservation test; do not modify production, design, or prior tests.
- Approved inputs: scoped AGENTS, Stage03 design, current public Stage01/Stage02/Stage03 records and generator seam.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Created this owned progress file before modifying tests.

## Current state

- Added one standard synthetic public-seam tracer. It discovers the real `AVAILABLE_FORMULA` atom and its exact atom ID, canonical formula tokens, and `RELATIONSHIP` role from the Stage02 capsule, then checks the Stage03 metric, field dimension, and section-5/section-7 ReaderItems against those values.
- The selector is assertion-only RED with no test errors: the current metric still claims all 20 flow atoms instead of the one formula atom, fields do not own/render the formula basis or operands/role, and sections 5/7 have no typed formula-backed items.
- Reopened for the invariant correction: section-5/section-7 ReaderItems now retain empty own basis and reference the plan's exact per-atom owner keys, while RepositoryBusinessModel metric/field basis assertions remain exact formulaBasis.
- The corrected selector remains assertion-only RED with no errors: the current implementation still lacks formula-oriented field/metric ReaderItems and formula field dimensions, while the metric continues to claim unrelated atoms.

## Changed files

- `progress/stage03-formula-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03FormulaTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03FormulaTest test` (corrected owner-reference invariant) | RED | 1 test, 1 failure containing 8 grouped assertion failures, 0 errors. The actual formula display/tokens and one-metric count pass; failures are exact formula basis (20 flow atoms vs the one `AVAILABLE_FORMULA` atom), formula field ownership/role/operands, and missing section-5/section-7 typed ReaderItems referencing formula atom owner keys. |

## Decisions

- The test will derive the formula canonical value, atom role, operands, and exact formula fact atom basis from the real Stage02 `AllowedFactView`/`AllowedAtomView` projection.
- Public `MetricDefinition`/`FieldDimension` fields and typed ReaderItem slots will be checked for those actual values; no literal formula or fixture-only IDs will be used as the source of expected values.
- No source mutation is needed for this bounded slice; changing/removing the formula source is deferred unless the public model cannot expose the required semantic values.
- The synthetic public atom value is structured enough to assert formula semantics directly, so no fallback “unstructured formula” branch or source mutation was needed.
- The negative assertion now rejects a metric that references any unrelated atom owner, preserving global exactly-once atom ownership rather than requiring formula atoms to be duplicated in aggregate ReaderItems.

## Blockers

## Exact next action

- Await the production formula/field implementation, then rerun only `mvn -Dtest=Stage03FormulaTest test`.

## Resume checks

- Re-read this file, run `git status --short`, and keep changes confined to `src/test/java/com/linguan/codemd/stage03/` plus this progress file.
