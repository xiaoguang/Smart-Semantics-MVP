# Progress: Stage04 sixth-audit tests

- Status: COMPLETE
- Agent role: Stage04 sixth-audit TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add the sixth-audit tests in vertical slices; the current work unit contains the addressed Candidate-store destination directory pre-collection bound and persisted EMPTY_SECTION Trace self-description. No production/design/existing-test edits.
- Approved inputs: `AGENTS.md`, `progress/stage04-fifth-acceptance-review.md`, Stage04 design, public agent/store seams, and existing real Stage04 fixtures.
- Current branch/worktree: Shared dirty worktree; preserve unrelated parent/agent changes and existing audit tests.

## Completed

- Read the latest fifth-acceptance review and captured its two open P1 contracts.
- Created this owned progress file before editing the new test class.
- Added `src/test/java/com/linguan/codemd/stage04/Stage04SixthAuditTest.java`; per the resumed vertical-slice instruction it currently contains only the CandidateStore bound test.
- Ran the requested single test method and obtained a clean assertion RED with no compile errors or test errors.
- Added the second and final `@Test`, covering exact persisted EMPTY_SECTION refs and canonical archive/root rewrites for deletion/substitution fail-closed checks.
- First Trace-method compile attempt exposed only a test-local helper return-type mismatch at `readObject`; narrowed it to `ObjectNode` with no production change.
- Re-ran the Trace method after that local correction and obtained a clean assertion RED with no compile errors or test errors.

## Current state

- The class now has exactly two tests: the prior CandidateStore bound test and the EMPTY_SECTION Trace contract test.
- The Trace test uses an installed real Candidate, requires exact `sectionKey`, `profileId`, and `templateKey`, and rewrites trace/candidate identity/manifest canonically before checking `validate` and `trace` fail closed for each deletion/substitution.

## Changed files

- `progress/stage04-sixth-audit-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage04/Stage04SixthAuditTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Class/count check | PASS | `Stage04SixthAuditTest` contains exactly two `@Test` methods. |
| `mvn -q -Dtest=Stage04SixthAuditTest#addressedCandidateDestinationRejectsEntry257BeforeIdentityComparison test` | RED (clean; prior production state) | `Tests run: 1, Failures: 1, Errors: 0`; expected `CANDIDATE_SIZE_LIMIT_EXCEEDED`, actual `CANDIDATE_IDENTITY_COLLISION` at the assertion. Root later confirmed this narrow slice GREEN after the production correction. |
| Initial Trace-method compile attempt | BLOCKED (test-local) | `Stage04SixthAuditTest.java:107 incompatible types: JsonNode cannot be converted to ObjectNode`; helper corrected before retry. |
| `mvn -q -Dtest=Stage04SixthAuditTest#persistedEmptySectionTraceDeclaresExactRefsAndRejectsInference test` | RED (clean) | `Tests run: 1, Failures: 1, Errors: 0`; expected persisted profile `nine-section-reader-v1`, actual missing/empty at line 65. No compile or test errors. |

## Decisions

- Exercise addressed Candidate installation through `FilesystemCandidateStore.install` with a real bundle and an existing destination containing 257 tiny entries; assert the stable resource-limit failure before identity comparison.

## Blockers

- None for this test-only deliverable. The expected RED identifies the missing persisted EMPTY_SECTION profile ref.

## Exact RED

- The real addressed Candidate destination with 257 tiny untrusted entries reaches `FilesystemCandidateStore.install` and returns `CANDIDATE_IDENTITY_COLLISION`; it does not reject at the shared directory bound with `CANDIDATE_SIZE_LIMIT_EXCEEDED`.

## Exact next action

Test deliverable is complete; root may implement the missing persisted Trace profile/effective-contract closure and then rerun this selector.

## Resume checks

- Confirmed exactly two `@Test` methods and no production/design/existing-test path changed by this agent.
- Confirmed the focused Trace selector has no compilation errors and its failure is assertion-only.
