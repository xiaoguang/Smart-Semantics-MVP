# Progress: M5 local interpretation carrier RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09 23:34
- Scope: Add exactly one public-seam RED test for the M5 local interpretation carrier.
- Approved inputs: M5 carrier design, Astra/ultra review, M4/M5 public records, runners, publishers, and existing tests.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read the scoped Agent rules, M5 design, M5 design review, M5 R2 gate, and current public seam.
- Confirmed the current implementation still exposes the pre-carrier M5 shapes (partial receipt, top-level proposal ownership, and module-root flow task-set projection).

## Current state

- Added exactly one `@Test` method to `InterpretationRunnerTest`:
  `persistsSemanticTaskSetIdentityCompleteR1R2ReceiptsAndEmbeddedProposalsWithoutReplay`.
- The test uses the real persisted M1–M4 fixture, distinct R1/R2 configuration sentinels,
  a scripted reflective Provider seam, M5 publication, fresh reopen, and an unchanged call
  counter.
- The independent assertions cover the three priority carrier gaps (semantic M4 task-set ID,
  complete GenerationReceiptV3 carrier, and Candidate-owned complete proposals), plus the four
  specified task/proposal/Candidate/execution identity projections.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunnerTest.java`
- `progress/m5-local-carrier-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped source/design inspection | PASS | M5 carrier RED target and current seam identified. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest#persistsSemanticTaskSetIdentityCompleteR1R2ReceiptsAndEmbeddedProposalsWithoutReplay test` | RED (expected) | Test compiled and ran 1 test; 33 assertion failures, 0 errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunnerTest.java` | PASS | Test source formatted. |
| Same exact selector after formatting | RED (expected) | 1 test, 33 assertion failures, 0 errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Add one test method only, using the existing M1–M4 fixture and scripted Provider.
- Do not modify production, schemas, design, or existing test assertions.

## Blockers

- Production remains unchanged and is expected to fail this RED until Terra implements the
  frozen carrier design. The current RED evidence shows:
  - M4 does not persist configured adapter/auth or materialized expected runtime values and its
    task IDs use the old projection;
  - M5 writes the module artifact root instead of the semantic `flowTaskSetId`;
  - M5 receipts have the old 7-field shape instead of the required 15-field GenerationReceiptV3;
  - M5 persists top-level proposals and Candidate IDs-only ownership instead of embedded complete
    same-Flow proposal values;
  - task/proposal/Candidate/execution IDs do not match the frozen domain/framing projections.

## Exact next action

- Hand the RED to Terra/xhigh for the minimal production GREEN defined by the frozen M5 design;
  do not modify this test to accommodate the current production shape.

## Resume checks

- Preserve unrelated shared-worktree edits.
- Do not run live Provider, network, customer code, or broad Maven tests.
