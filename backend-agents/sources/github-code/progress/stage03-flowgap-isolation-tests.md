# Progress: Stage03 FlowGap isolation tests

- Status: COMPLETE
- Agent role: Stage03 public-seam test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Real Stage01 → Stage02 → Stage03 FlowGap ownership and Capsule/task isolation
- Approved inputs: Scoped AGENTS.md; Stage01/02/03 public records and designs; existing Stage03 multi-flow fixture
- Current branch/worktree: shared worktree; preserve unrelated changes

## Completed

- Created this owned progress record before test edits.
- Reviewed the existing public two-flow fixture and Stage03 scripted-provider helpers.
- Added a test-only frozen three-entry fixture: two complete independent
  Controller → Service → Mapper/XML flows plus a third discovered controller
  entry that shares the already-claimed facts and therefore cannot form a
  complete FlowSlice/Capsule.
- Verified through the public Stage01 analyzer/flowView and Stage02 compiler
  that the fixture returns exactly two FlowSlices and two EvidenceCapsules and
  retains a blocking FlowGap owned by the third entry.
- Added Stage03 public-seam assertions that compiled task inputs and flow
  interpretations exclude the third-entry Gap while repository pending
  questions and Gap accounting retain it.
- Added a negative scripted-provider tracer that makes the third-entry Gap
  reason eligible to the frozen question registry and attempts to reference it
  from a compiled Flow; this is the intentional RED for missing flow-local Gap
  validation.

## Current state

- The three-entry fixture is non-vacuous and public-seam verified. Positive
  isolation/accounting checks pass; the foreign-gap proposal test fails because
  the current Stage03 generator accepts the cross-entry Gap instead of failing
  with `MODEL_RESPONSE_REFERENCE_INVALID`.

## Changed files

- progress/stage03-flowgap-isolation-tests.md
- src/test/java/com/linguan/codemd/stage03/Stage03FlowGapIsolationTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03FlowGapIsolationTest#incompleteThirdEntryProducesBlockingFlowGapWithoutFlowOrCapsule test` | PASS | 1 test, 0 failures, 0 errors; exactly 2 compiled flows/2 Capsules and a blocking third-entry FlowGap. |
| `mvn -Dtest=Stage03FlowGapIsolationTest test` | RED (expected) | 3 tests, 1 failure, 0 errors. The only failure is `aProviderQuestionCannotReferenceAnotherEntryGap` (line 113): expected `Stage03Exception(MODEL_RESPONSE_REFERENCE_INVALID)` but no exception was thrown. |
| `git diff --check -- progress/stage03-flowgap-isolation-tests.md src/test/java/com/linguan/codemd/stage03/Stage03FlowGapIsolationTest.java` | PASS | No whitespace errors. |

## Decisions

- Use only the public Stage01 analyzer, Stage02 compiler, and Stage03 generator seams; do not construct or mutate Stage02Result.
- Keep the incomplete entry's FlowGap in repository accounting while asserting that compiled Capsule task inputs and interpretations remain flow-local.
- The negative test uses a custom frozen question entry for the actual third
  entry Gap reason, avoiding an unrelated unknown-question failure; the
  scripted provider still sends only bounded JSON and uses the standard
  runtime identity.

## Blockers

- Current production blocker exposed by the RED: `CapsuleContext`/Stage03
  response admission must restrict Gap references to the current Flow/Capsule;
  the public flow task itself does not serialize the foreign Gap, but a
  provider can currently reference it because repository-wide Stage02 Gaps are
  admitted into the per-Capsule context.

## Exact next action

- Terra should implement flow-local Gap admission and rerun
  `mvn -Dtest=Stage03FlowGapIsolationTest test`; the precondition and positive
  isolation checks should remain green and the foreign-gap test should then
  pass.

## Resume checks

- Final scope check: only `Stage03FlowGapIsolationTest.java` and this owned
  progress file were changed by this task; no production/design files or
  Stage02 results were constructed or mutated.
