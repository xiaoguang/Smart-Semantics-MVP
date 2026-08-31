# Progress: Stage03 multi-flow tests

- Status: COMPLETE
- Agent role: Stage03 multi-flow test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Public Stage01 → Stage02 → Stage03 multi-flow fixture validation and bounded RED coverage
- Approved inputs: Scoped AGENTS.md; Stage01/02/03 design documents; target architecture implementation; Stage03 final review; Stage03 multi-flow design
- Current branch/worktree: shared worktree; preserve unrelated changes

## Completed

- Created this owned progress record before test edits.
- Fully read `sources/github-code/AGENTS.md`, `backend-agents/AGENTS.md`,
  `docs/stages/01-proven-source-facts.md`,
  `docs/stages/02-flow-compilation.md`,
  `docs/stages/03-nine-section-generation.md`,
  `progress/target-architecture-implementation.md`,
  `progress/stage03-final-review.md`, and
  `progress/stage03-multiflow-design.md`.
- Added a public-seam test fixture that declares a genuinely independent
  second `ShipmentController -> ShipmentService -> ShipmentMapper/XML`
  path, with fresh declared-file hashes, inventory digest, and capture receipt.
- Proved through `Stage01Analyzer.analyze`/`flowView` that the fixture exposes
  exactly the two independent routes `/reservations` and `/shipments`.
- Added the Stage02 vertical tracer requiring two FlowSlices and two
  EvidenceCapsules, without constructing or mutating any Stage02 result.
- Confirmed the tracer has a precise RED: Stage02 returns two entry
  dispositions but both are blocking `FLOW_FACT_NOT_ADMITTED` Gaps, leaving
  zero FlowSlices and zero EvidenceCapsules.
- Re-ran the retained precondition after the parent implementation update:
  the public Stage01 → Stage02 path now produces exactly two FlowSlices and
  exactly two EvidenceCapsules for the independent controller fixture.
- Added public Stage03 generator coverage for two-round task creation,
  capsule/flow-local fact, atom, proof, span and Gap accounting, same-table
  RECORD hard-anchor merging, distinct request anchors, relation presence,
  and pending-question Gap provenance. The task-locality and Gap provenance
  assertions pass; the hard-anchor/relation assertions remain an intentional
  RED against the current implementation.

## Current state

- The non-vacuous public fixture is verified: Stage01 exposes `/reservations`
  and `/shipments`, and Stage02 compiles exactly two FlowSlices and two
  EvidenceCapsules. Stage03 receives one two-round task pair per Capsule.
- Flow-local task and pending-question provenance checks are green. A Capsule
  Gap may be represented once in both its Flow's `gapIds` and its Capsule's
  `allowedGaps`; the test treats that same-Flow duplication as valid while
  rejecting cross-Flow ownership.
- The remaining RED is concentrated in M6 model assembly: the current result
  emits two RECORD objects instead of one shared `inventory` object, collapses
  distinct request anchor keys, and emits no typed object relations.

## Changed files

- `progress/stage03-multiflow-tests.md`
- `src/test/java/com/linguan/codemd/stage03/Stage03MultiFlowTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage02CompilerTest#aDeclaredSecondEntryGetsItsOwnDispositionAndCannotBorrowFactsOrSpans test` | PASS | 1 test, 0 failures, 0 errors; existing shared-Service fixture preserves a second-entry GAP. |
| `mvn -Dtest=Stage03MultiFlowTest test` | RED (expected) | 5 tests, 1 failure, 0 errors. `sameProvenInventoryTableMergesOneRecordObjectWhileDistinctTypesStaySeparate` has 3 assertion failures: expected 1 RECORD but got 2; expected 2 distinct request anchors but got 1; expected non-empty relations but got empty. The two-flow precondition, two-round task count, task-local closure, and pending-question Gap provenance tests pass. |
| `git diff --check -- progress/stage03-multiflow-tests.md src/test/java/com/linguan/codemd/stage03/Stage03MultiFlowTest.java` | PASS | No whitespace errors. |

## Decisions

- Keep the retained two-flow/two-capsule precondition; only add Stage03
  assertions after a non-vacuous public Stage02 result is observed.
- Do not weaken flow/capsule count assertions, construct Stage02 results directly, or modify production/design files.
- The independent fixture deliberately uses distinct package/type/route/mapper
  identities, so the RED is not caused by shared-Service ownership ambiguity.
- No Stage03 Provider call was attempted after Stage02 failed to produce the
  required Capsules; a per-task isolation assertion in that state would be
  vacuous and would violate the public-seam contract.
- The current cycle uses only the approved public `Stage03Generator.generate`
  seam and the real Stage01/Stage02 fixture; no Stage02 result is fabricated,
  no reflection/private hook is used, and the initial invalid same-Flow Gap
  duplicate assertion was corrected without weakening cross-Flow checks.

## Blockers

- Historical blocker (resolved and reverified in this cycle):
  Stage01 M3 previously created a single global reservation candidate list:
  `ProvenFactCompiler.java:31-32,92-115` selects one hard-coded F01–F08
  reservation workflow, rather than producing per-entry fact ownership for
  both independent flows. The current public run now supplies two independent
  Stage02 FlowSlices/Capsules, so the Stage03 tests are non-vacuous.
- The former Stage02 `FLOW_FACT_NOT_ADMITTED` blocker is historical only; the
  retained two-flow precondition is green.
- Current implementation blocker for the next production cycle: M6 model
  assembly does not merge the two same-table RECORD anchors, preserves neither
  distinct request anchors nor typed object relations. These are precise RED
  findings, not fixture or test errors.

## Exact next action

- Terra should implement the three failing M6 behaviors, then rerun the same
  narrow selector. The green isolation/provenance assertions should remain as
  regression coverage.

## Resume checks

- Recheck owned-file scope before each edit.
- Completion evidence recorded above: current selector has 5 tests, 1 failing
  test, 0 errors; `git diff --check` is clean. Scope remains limited to this
  test and its owned progress record.
