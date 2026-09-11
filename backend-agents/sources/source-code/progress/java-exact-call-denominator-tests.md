# Progress: exact-call M1 denominator dispositions

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare two public-seam M1 `JAVA_EXACT_CALL` missing-material tests in `FactCandidateEnumeratorTest`: malformed present METHOD canonical precedence, and canonical-valid missing generic evidence closure with shared-owner denominator conservation.
- Approved inputs: Newly published Step04 §8.0.2 M1 denominator-closure paragraph, real `ProgramGraphsPublicFixture.createWithSharedJavaCall`, immutable public graph/input records and adversarial in-memory copies only; no production/schema/API/status/design/Provider/customer/network changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress handoff before any Java edit.
- Read the newly published §8.0.2 M1 denominator-closure paragraph and current public candidate/input/graph records.
- Confirmed the target test file and existing shared-call fixture are present; Java/Maven gate was released after Terra's M2 aggregate and has now been released again after this bounded verification.
- Confirmed feasibility: `FactCandidateInputs` and its public graph/evidence records can form immutable in-memory negative copies without changing IDs, hashes, schemas, or production readers.

## Current state

- The two M1 tests remain the only added tests. Each uses an immutable public in-memory negative `FactCandidateInputs` copy, not a claimed reader publication: one keeps a present `METHOD` with malformed canonical while removing target evidence; the other keeps canonical-valid material and now exercises all seven nonempty subsets of the three generic subject closures for the shared call-site/edge/target.
- The denominator-union oracle now treats candidate and `NOT_APPLICABLE` lists as separately ordered, requiring exact membership and multiplicity independent of their concatenation order. Every subset still requires the two shared-owner dispositions and unchanged unaffected row.
- Both strengthened selectors compile and reach the intended missing-material assertion: each reports 1 test, 1 failure, 0 errors, 0 skips; no fixture construction or premise assertion fails.

## Changed files

- `progress/java-exact-call-denominator-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Worktree remains on `codex/source-analysis-proof-and-flow-materials`; pre-existing shared Java/docs/progress changes preserved. |
| `rg`/`sed` contract and public-record inspection | PASS | §8.0.2 requires malformed canonical → `NOT_APPLICABLE/REQUIRED_ATOM_MISSING/[TARGET_METHOD_CANONICAL]`; valid canonical with missing generic closure → `NOT_APPLICABLE/PROOF_NOT_CLOSED` with ordered missing-role subsequence. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest#retainsMalformedExactCallDenominatorBeforeEvidenceClosureDisposition test` | RED (expected) | 1 test, 1 failure, 0 errors, 0 skips; compilation/construction passed, then actual one-row exact output lacked both shared-owner denominator rows/dispositions. Any-order union oracle is active. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest#retainsExactCallDenominatorWhenGenericEvidenceClosureIsMissing test` | RED (expected) | 1 test, 1 failure, 0 errors, 0 skips; compilation/construction passed, then actual one-row exact output lacked both shared-owner denominator rows/dispositions on the first bounded subset. The same single test contains all seven nonempty subsets. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java spotless:apply` | PASS | Spotless selected exactly 1 file and changed it to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java spotless:check` | PASS | Spotless selected exactly 1 file; no formatting changes needed. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java progress/java-exact-call-denominator-tests.md` | PASS | No whitespace errors. |

## Decisions

- Test malformed METHOD canonical with evidence also absent and assert malformed-canonical precedence over evidence missing; retain every shared-owner `entryId|edge|JAVA_EXACT_CALL` denominator as one NOT_APPLICABLE disposition.
- Test canonical-valid missing generic source→rule closures across all seven nonempty subsets of call-site, call-target edge, and target METHOD subjects; derive and assert each exact ordered nonempty `CALL_SITE_EVIDENCE`, `CALL_TARGET_EDGE_EVIDENCE`, `TARGET_METHOD_EVIDENCE` subsequence while unaffected rows remain unchanged.
- Treat candidate and disposition lists as separately ordered outputs; assert the exact denominator union with order-independent membership and multiplicity.
- Derive denominator keys, owner multiplicity, graph endpoints, and unaffected rows from independently copied public records; never derive expected dispositions from enumerator output or fabricate IDs/locators/hashes.
- Record whether each mutation is an immutable public in-memory negative input or a genuinely fresh-reopened publication; do not imply unsupported reader behavior.

## Blockers

- No production/schema/API/design blocker remains for this bounded test slice. The expected RED is the absent denominator-disposition behavior; Maven is released after the two selectors and exact file format/check.

## Exact next action

- Keep the one-file test and this progress handoff scoped; root may now apply the approved denominator implementation and rerun these exact selectors. Do not run an aggregate or edit production/schema/API/design in this slice.

## Resume checks

- Re-read this progress file before any continuation. Preserve the public in-memory mutation classification, shared-owner denominator assertions, and exact ordered missing-role oracles.
