# Progress: Stage 03 completeness RED tests

- Status: COMPLETE
- Agent role: Stage03 completeness test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add bounded behavioral RED tests/fixtures at the public Stage03Generator seam only; do not modify production, design, or prior tests.
- Approved inputs: scoped AGENTS, Stage03 design, current public Stage01/Stage02/Stage03 records and generator seam.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Created this owned progress file before modifying tests.

## Current state

- This bounded cycle contains exactly two public-seam tests in `Stage03CompletenessTest`: complete task registry bindings, and canonical receipt/result identity.
- Both tests compile and execute through `Stage03Generator.generate`; RED is assertion-only with no test errors.
- The task test reports all six missing child registry identities/digests plus missing typed term/claim/question/policy/template/ownership values.
- Reopened for a fixture-only compatibility correction: the reader-binding registry must declare all four item kinds and every claim/question/technical template key referenced by the frozen registries.
- `registryWithReaderBindings` now freezes fourteen valid sentence templates (the four required item kinds, the scope template, all four claim template keys, the question template key, and all five technical display template keys) plus four unique ownership rules for PROVEN_VALUE, BUSINESS_TERM, BOUNDED_QUESTION, and TECHNICAL_DISPLAY.
- Reopened for fixture migration: the complete reader registry must also carry the Outcome template and ownership binding required by the Outcome conservation contract.
- Added `READER_OUTCOME_V1` with owner `section-4` and exact `outcome-terminal`/`outcome-semantics` TECHNICAL_DISPLAY slots; the complete fixture now has five unique ownership rules including `OUTCOME → section-4`.
- Formula registry fixture migration completed: added `READER_FIELD_FORMULA_V1` and `READER_METRIC_FORMULA_V1`, each owned by its declared section with the four exact TECHNICAL_DISPLAY formula slots; the complete fixture now has seven unique ownership rules including `FIELD → section-5` and `METRIC → section-7`.
- The identity test reports non-canonical R1/R2 receipt hashes for semantically equivalent key/array order. Its changed valid alias-term assertions pass, proving the second half of the contract is executable.
- Public construction of a malformed Stage02 Capsule and a second Flow is intentionally deferred to a follow-up slice if it requires new fixture surface; no private production hook was added.

## Changed files

- `src/test/java/com/linguan/codemd/stage03/Stage03CompletenessTest.java`
- `progress/stage03-completeness-tests.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03CompletenessTest#taskAdmissionContainsChildRegistryDigestsAndEveryTypedBinding test` | RED | 1 test, assertion-only `MultipleFailuresError` with 12 failures, 0 errors; missing all six child registry id/digest pairs and all typed binding payload assertions. |
| `mvn -Dtest=Stage03CompletenessTest#interpretationAndResultIdentityUseCanonicalRoundReceipts test` | RED | 1 test, assertion-only `MultipleFailuresError` with 1 failure, 0 errors; line 90 shows R1/R2 receipt hashes change under semantically equivalent key/array order. Changed valid alias-term identity assertions passed. |
| `mvn -Dtest=Stage03CompletenessTest test` | RED | 2 tests, 2 assertion failures, 0 errors/skips; exact class selector completed with clean compilation. |
| `mvn -Dtest=Stage03CompletenessTest test` (fixture compatibility rerun) | BLOCKED BEFORE TEST EXECUTION | Production compilation stopped before Surefire: `Stage03Generator.java` reports 6 errors because `ReaderContracts` and `RegistryIndex.readerContracts()` are not yet present. No test errors or assertion result were produced. |
| `mvn -Dtest=Stage03SemanticTest,Stage03CompletenessTest test` (Outcome binding migration) | BLOCKED BY CURRENT PRODUCTION | Main/test compilation succeeded, but Surefire ran 4 tests with 3 errors and 0 assertion failures: both completeness tests failed with `READER_INVARIANT_BROKEN` at `Stage03Generator.java:717`/the call site; the semantic body-cleanliness test passed and its template/ownership test reported the same production invariant. |
| `mvn -Dtest=Stage03SemanticTest,Stage03CompletenessTest test` (formula registry fixture migration) | GREEN | Main/test compilation succeeded; Surefire ran 4 tests with 0 failures, 0 errors, and 0 skipped. Both semantic tests and both completeness tests passed with the frozen FIELD/METRIC formula templates and ownership rules. |

## Decisions

- Tests use only `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` and public result records; no private methods or production test hooks.
- The equivalent-response mutation canonicalizes object keys and sorts JSON arrays, so the receipt assertion is independent of implementation ordering.
- The alternate term uses the same proven Flow anchor/basis and a frozen registry, so the identity assertion is a valid selection mutation rather than an unknown-key shortcut.
- Stop after precise RED assertion failures are established; no production changes are authorized.
- This compatibility correction changes only the test fixture helper and this progress file; production compilation must be restored by the implementation agent before the selector can execute.
- This migration changes only `registryWithReaderBindings`; all completeness assertions and identity mutations remain unchanged.

## Blockers

## Exact next action

- Formula registry fixture migration is complete; no production files were modified.

## Resume checks

- Re-read this file, run `git status --short`, and keep changes confined to `src/test/java/com/linguan/codemd/stage03/` and this progress file.
