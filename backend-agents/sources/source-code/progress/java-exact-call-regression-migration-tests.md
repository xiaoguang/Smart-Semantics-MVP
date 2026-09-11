# Progress: exact-call candidate regression migrations

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare bounded v3-oracle migrations for `FactCandidateExactPathTest`, `FactCandidateProofEvidenceHandoffTest`, and `FactCandidateMissingPathTest` only; preserve boundary-specific assertions while retaining actual `JAVA_EXACT_CALL` rows.
- Approved inputs: current public v3 candidate records, real persisted graph fixtures and public mutation seams already present in the three tests; no new fixture/helper/schema/production changes, no Provider/customer/network.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note before Java/Maven preparation.
- Confirmed the migration must separate `JAVA_BOUNDARY_INVOCATION` rows from the additional exact-call family instead of weakening the existing boundary path/owner/evidence assertions.

## Current state

- The three candidate tests now filter `JAVA_BOUNDARY_INVOCATION` for their original boundary assertions and explicitly retain the four actual `JAVA_EXACT_CALL` rows produced by the real two-entry fixture.
- `FactCandidateMissingPathTest` independently reopens the unmutated persisted base fixture, derives its exact denominator keys, and requires the valid republished missing-evidence mutation to retain that exact subset while only the approve boundary combination becomes NOT_APPLICABLE.
- No fixture, mutation helper, schema, production, or unrelated test files were changed.

## Changed files

- `progress/java-exact-call-regression-migration-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateExactPathTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateProofEvidenceHandoffTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped source inspection | PASS | Confirmed the three legacy whole-result assumptions and the public persisted graph/mutation seams; no fixture/helper/production seam was expanded. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateExactPathTest,FactCandidateProofEvidenceHandoffTest,FactCandidateMissingPathTest test` (pre-migration) | RED (expected stale-oracle failures) | 3 tests, 3 failures, 0 errors, 0 skips; the real standard fixture had 6 candidates (2 boundary + 4 exact), while all three tests still assumed boundary-only results. |
| Final `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateExactPathTest,FactCandidateProofEvidenceHandoffTest,FactCandidateMissingPathTest test` | PASS | 3 tests, 0 failures, 0 errors, 0 skips; boundary assertions remain intact, all 4 exact rows are retained, and missing-path exact keys equal the independently reopened baseline subset. |
| Pinned three-file Spotless apply | PASS | Selected exactly 3 owned Java files; 1 changed to clean and 2 were already clean. |
| Pinned three-file Spotless check | PASS | Selected exactly 3 owned Java files; 0 needed changes and the build succeeded. |
| `git diff --check -- progress/java-exact-call-regression-migration-tests.md src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateExactPathTest.java src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateProofEvidenceHandoffTest.java src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java` | PASS | No whitespace errors in the bounded migration files or progress note. |

## Decisions

- Preserve original boundary-only owner/path/evidence assertions by filtering candidates to `JAVA_BOUNDARY_INVOCATION` and retaining the known boundary expectations.
- Derive exact-call keys and retained rows from the actual baseline candidate set; do not hardcode or silently discard exact Facts.
- For `FactCandidateMissingPathTest`, use the existing valid public republished graph mutation, retain the one approve-boundary NOT_APPLICABLE assertion, and require unaffected exact keys to equal the independently reopened baseline subset.
- Leave custom boundary-only registry tests untouched; this slice covers only the three named standard-v3 regressions.

## Blockers

- None. The bounded three-class migration is complete; no production, fixture, schema, or helper changes are pending.

## Exact next action

- Release Maven to Terra/root. Do not broaden this slice until a new gate is issued.

## Resume checks

- Re-read this note before apply. Keep scope to the three named candidate tests, preserve boundary assertions, retain actual exact rows, and make no fixture/helper/schema/production changes.
