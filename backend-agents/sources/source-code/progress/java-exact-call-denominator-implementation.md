# Progress: exact-call M1 denominator implementation

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: `JAVA_EXACT_CALL` malformed-canonical and missing-generic-evidence denominator dispositions in `FactCandidateEnumerator` only.
- Approved inputs: Step04 §8.0.2 M1/M2 exact denominator closure, Luna's two frozen public-seam REDs, and current M1 v3 candidate/disposition records.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this implementation progress record before production edits.
- Read the normative M1 denominator contract and Luna's frozen RED handoff.
- Prepared the one-file change without applying it: canonical-invalid tuples will emit first; canonical-valid tuples will collect the fixed-order three generic-closure roles before deciding candidate versus disposition.
- Applied the enumerator-only disposition path after Luna reconfirmed the strengthened two-test RED against unchanged production.
- Verified the two exact selectors and the direct M1 class set before and after one-file formatting.

## Current state

- Each qualifying owner-edge now yields either its exact candidate or one existing `NotApplicableDisposition`. Malformed target METHOD canonical values emit `REQUIRED_ATOM_MISSING/[TARGET_METHOD_CANONICAL]` before generic evidence is examined. For canonical-valid targets, the enumerator gathers each absent generic closure in the exact fixed role order and emits `PROOF_NOT_CLOSED`.
- The strengthened missing-closure method covers all seven nonempty subsets and is GREEN. Valid exact calls, existing target/non-METHOD/no-edge behavior, and boundary/guard behavior remain covered by the direct M1 class selector.

## Changed files

- `progress/java-exact-call-denominator-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root/Luna exact malformed-canonical selector | RED (expected) | 1 test, 1 failure, 0 errors: both shared-owner exact denominator rows are silently absent. |
| Root/Luna exact missing-generic-evidence selector | RED (expected) | 1 test, 1 failure, 0 errors: both shared-owner exact denominator rows are silently absent. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest#retainsMalformedExactCallDenominatorBeforeEvidenceClosureDisposition+retainsExactCallDenominatorWhenGenericEvidenceClosureIsMissing test` | PASS | 2 tests, 0 failures, 0 errors, 0 skips; the strengthened second method exercises all seven nonempty missing-role subsets. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest,FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest test` | PASS | 7 tests, 0 failures, 0 errors, 0 skips before formatting. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java spotless:apply` | PASS | Exactly 1 production file selected and changed to clean. |
| Same exact absolute one-file `spotless:check` | PASS | Exactly 1 production file selected; 0 need changes. |
| Same three-class M1 selector after formatting | PASS | Raw Surefire totals: enumerator 4/0/0/0, artifact 2/0/0/0, reader 1/0/0/0; aggregate 7/0/0/0. |
| `git diff --check` on the owned production file; trailing-whitespace scan including this progress file | PASS | No whitespace errors. |

## Decisions

- Preserve the existing endpoint-reference fatal, non-METHOD, no-exact-edge, valid exact-call, and boundary/guard paths.
- For every qualifying owner-edge tuple, emit a closed exact candidate or exactly one existing `NotApplicableDisposition`; do not add a schema, API, status, parser, or source fallback.
- Keep missing permitted M2 proof-rule pairs outside this M1 behavior.

## Blockers

- None for this bounded M1 denominator-closure slice.

## Exact next action

- Release Maven. Do not alter M2 missing-permitted-rule-pair behavior, candidate publishers/readers, schema/API, or the frozen tests without a separate approved RED/GREEN brief.

## Resume checks

- Preserve canonical-before-evidence precedence; preserve the required fixed missing-role order `CALL_SITE_EVIDENCE`, `CALL_TARGET_EDGE_EVIDENCE`, `TARGET_METHOD_EVIDENCE` for every nonempty evidence gap subset.
