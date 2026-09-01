# Progress: Stage 03 semantic completeness RED tests

- Status: COMPLETE
- Agent role: Stage03 semantic completeness test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most five bounded behavioral RED tests/fixtures at the public Stage03Generator seam; do not modify production, design, or prior tests.
- Approved inputs: scoped AGENTS, Stage03 design, current public Stage01/Stage02/Stage03 records and generator seam.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Created this owned progress file before modifying tests.

## Current state

- Added the first minimal semantic slice: frozen template/ownership placement, template-driven Markdown, and fail-closed missing/duplicate binding expectations.
- Added a body-cleanliness regression covering localized term, technical display, sentence-template, and question registry values containing prompt/provider/runtime/model/path/file-extension tokens.
- The first selector is clean RED: one test, nine assertion failures, and zero errors. The implementation still places typed items in fixed sections, ignores the frozen template literal/ownership bindings, emits scope filler, and accepts missing/duplicate bindings.
- The body-cleanliness selector is green: one test, zero failures, zero errors; the current implementation rejects the unsafe frozen registry before model-backed reader generation.
- Reopened for fixture migration: semantic registries now need an explicit Outcome reader template/ownership binding for the Outcome conservation contract.
- Added `READER_OUTCOME_V1` with owner `section-4` and the exact `outcome-terminal`/`outcome-semantics` TECHNICAL_DISPLAY slots; the default and duplicate-owner mutation registries now include the unique `OUTCOME → section-4` rule.
- Formula registry fixture migration completed: added `READER_FIELD_FORMULA_V1` and `READER_METRIC_FORMULA_V1`, each owned by its declared section with the four exact TECHNICAL_DISPLAY formula slots; complete and duplicate-owner registry variants include unique `FIELD → section-5` and `METRIC → section-7` rules.

## Changed files

- `progress/stage03-semantic-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03SemanticTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03SemanticTest#frozenTemplateAndOwnershipBindingsDriveReaderItemsAndFailClosedWhenInvalid test` | RED | 1 test, 9 assertion failures, 0 errors. Failures: atom/meaning/gap owner sections remain section-4/section-2/section-9; all three template markers are absent; scope filler remains; missing-template and duplicate-owner mutations do not fail closed. |
| `mvn -Dtest=Stage03SemanticTest#forbiddenRegistryDisplayTemplateAndQuestionValuesFailBeforeReaderBody test` | GREEN | 1 test, 0 failures, 0 errors. Unsafe registry content is rejected before `ScriptedModelProvider` receives a task. |
| `mvn -Dtest=Stage03SemanticTest test` | RED | 2 tests, 1 failing test, 9 assertion failures, 0 errors; the body-cleanliness test passes and the template/ownership tracer remains RED. |
| `mvn -Dtest=Stage03SemanticTest,Stage03CompletenessTest test` (Outcome binding migration) | BLOCKED BY CURRENT PRODUCTION | Main/test compilation succeeded, but Surefire ran 4 tests with 3 errors and 0 assertion failures: Stage03SemanticTest template/ownership test failed with `READER_INVARIANT_BROKEN` at `Stage03Generator.java:717`; the body-cleanliness test passed; both completeness tests failed with the same production reader invariant. |
| `mvn -Dtest=Stage03SemanticTest,Stage03CompletenessTest test` (formula registry fixture migration) | GREEN | Main/test compilation succeeded; Surefire ran 4 tests with 0 failures, 0 errors, and 0 skipped. Both semantic tests and both completeness tests passed with the frozen FIELD/METRIC formula templates and ownership rules. |

## Decisions

- Tests use only `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` and public records; no private methods or production test hooks.
- If malformed Capsule construction cannot be injected while preserving replay identity via the public request seam, record and defer it rather than weakening the assertion.
- Stop after precise RED assertion failures with no test errors; no production changes are authorized.
- The only non-green behavior in this cycle is the public template/ownership contract above; no production implementation was changed.
- This migration is test/fixture-only; existing assertions remain unchanged.

## Blockers

## Exact next action

- Formula registry fixture migration is complete; no production files were modified.

## Resume checks

- Re-read this file, run `git status --short`, and keep changes confined to `src/test/java/com/linguan/codemd/stage03/` and this progress file.
