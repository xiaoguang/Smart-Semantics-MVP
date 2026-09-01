# Progress: Stage03 internal Capsule closure tests

- Status: COMPLETE
- Agent role: Stage03 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: RED tests for the pre-provider deterministic Capsule closure validator
- Approved inputs: scoped AGENTS.md; Stage01/Stage02/Stage03 public records and designs; `Stage03Generator.generate`; approved package-level `Stage03CapsuleClosureValidator.validate` seam
- Current branch/worktree: shared worktree; preserve unrelated parent changes

## Completed

- Created this owned progress file before test edits.
- Confirmed the test may use only a real Stage01 → Stage02 single-flow result,
  public records, and the approved package-level validator seam. No production
  source or design document will be modified.
- Added `Stage03CapsuleClosureTest` with one honest-closure acceptance test and
  one grouped mutation test. The fixture independently checks Stage01 proof
  membership, frozen source bytes and hashes, proof node/edge closure, and
  projection-obligation support before invoking the approved validator.

## Current state

- The test source is complete and ready for the requested narrow selector. It
  covers honest acceptance plus seven single-field public-record mutations:
  proofPackId, atom proofId, source hash, excerpt hash, excerpt, satisfying
  span ID, and unsupported obligation subject.
- The intended RED is precise: the fixture compiles and reaches test
  compilation, but the approved validator type is not yet present in
  production.

## Changed files

- progress/stage03-capsule-closure-tests.md
- src/test/java/com/linguan/codemd/stage03/Stage03CapsuleClosureTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03CapsuleClosureTest test` | RED (expected compile failure) | Main compilation succeeded (171 files); test compilation reached 48 files then failed with exactly 2 `cannot find symbol` errors for `Stage03CapsuleClosureValidator` at `Stage03CapsuleClosureTest.java:56` and `:78`. No test method ran; 0 assertion failures, 0 runtime errors, and no raw NPE. |
| `git diff --check -- progress/stage03-capsule-closure-tests.md src/test/java/com/linguan/codemd/stage03/Stage03CapsuleClosureTest.java` | PASS | No whitespace errors. |

## Decisions

- Expected values will be derived independently from Stage01 proof-pack records,
  frozen source bytes, and Stage02 records; tests will not call the production
  canonicalizer to reproduce its own output.
- The external `Stage03Generator.generate` seam remains unchanged. The new test
  calls only the explicitly approved package-level closure validator seam.

## Blockers

- The approved package-level `Stage03CapsuleClosureValidator` is not yet
  present in the production tree. This is the only blocker and the intended
  compile RED for this test-only cycle.

## Exact next action

- Terra may add the validator behind the approved package-level seam and rerun
  this selector. This agent does not add production code.

## Resume checks

- COMPLETE. Only `Stage03CapsuleClosureTest.java` and this progress file were
  changed by this cycle; no production, design, or existing test file was
  modified. The exact selector above is the only test command run.
