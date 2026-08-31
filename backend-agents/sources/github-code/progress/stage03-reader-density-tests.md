# Progress: Stage03 reader information density tests

- Status: COMPLETE
- Agent role: Stage03 public-seam test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: M6 RepositoryBusinessModel to M7 NineSectionPlan/Markdown conservation and typed reader density
- Approved inputs: Scoped AGENTS.md; Stage03 design §8.6/section duties; public Stage03 records; existing Stage03 multi-flow, formula, and proof-density tests
- Current branch/worktree: shared worktree; preserve unrelated changes

## Completed

- Created this owned progress record before test edits.
- Began reviewing the public reader-plan records and existing two-flow scripted scenario.
- Added a reusable test-only frozen two-flow request helper to
  `Stage03Fixtures`; it declares an independent Shipment Controller,
  Service, Mapper, and XML mapper with fresh inventory/receipt hashes.
- Added one integrated public-seam density test. It proves the two-flow
  Stage01 → Stage02 precondition, runs Stage03 with an empty scripted R1/R2
  selection, and verifies that nonempty M6 objects, activities, relations,
  and answerable questions each have exactly one typed section owner/reference
  with clean, business-readable Markdown.

## Current state

- The non-vacuous test fixture and Stage03 generation pass. The density
  contract is intentionally RED: current M7 emits no typed object/activity/
  relation/answerable-question ReaderItems, leaves sections 3/6/8 as
  `EMPTY_SECTION`, and does not render the corresponding M6 displays/text.

## Changed files

- progress/stage03-reader-density-tests.md
- src/test/java/com/linguan/codemd/stage03/Stage03ReaderDensityTest.java
- src/test/java/com/linguan/codemd/stage03/Stage03Fixtures.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03ReaderDensityTest test` | RED (expected) | 1 test, 1 failure, 0 errors; `MultipleFailuresError` contains 20 assertion failures. The fixture's Stage01/Stage02 two-flow precondition and nonempty M6 assertions pass. Missing coverage is reported for typed object/activity/relation/question ReaderItems, section 3/6/8 EMPTY_SECTIONs, and corresponding Markdown display/text. |
| `git diff --check -- progress/stage03-reader-density-tests.md src/test/java/com/linguan/codemd/stage03/Stage03ReaderDensityTest.java src/test/java/com/linguan/codemd/stage03/Stage03Fixtures.java` | PASS | No whitespace errors. |

## Decisions

- Use a real Stage01 → Stage02 → Stage03 replay and scripted provider only; do not construct or mutate Stage02Result.
- Keep the test to one integrated vertical slice and assert public typed fields rather than reflection or display-text matching.
- Match each M6 item by its public ID or exact public atom basis, never by
  localized display text; separately require Markdown display/text presence
  and internal-ID/hash/provider cleanliness.

## Blockers

- No fixture blocker remains. The current production gap is M7 reader-plan
  coverage for object/activity/relation/answerable-question knowledge and the
  corresponding finite template/ownership execution.

## Exact next action

- Terra should add typed M7 items under the design's section owners and rerun
  `mvn -Dtest=Stage03ReaderDensityTest test`; the existing M6 preconditions,
  exact ID/basis bindings, and body-clean assertions should remain intact.

## Resume checks

- Final scope check: only `Stage03ReaderDensityTest.java`, the necessary
  test-only two-flow helper in `Stage03Fixtures.java`, and this owned progress
  file changed; no production or design files were modified.
