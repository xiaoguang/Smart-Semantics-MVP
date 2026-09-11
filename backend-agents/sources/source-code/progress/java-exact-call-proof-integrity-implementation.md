# Progress: exact-call M2 proof-integrity implementation

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Normalize exact-call M2 unavailable-allowed-rule-pair rejection to the published `PROOF_NOT_CLOSED` reason in `AtomicProofBuilder` only.
- Approved inputs: Step04 §8.0.2 M2 all-or-nothing rule, Luna's frozen `AtomicProofBuilderTest` RED, and the current source-revalidation/evidence-pair implementation.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this implementation progress record before production edits.
- Read the exact M2 contract and the frozen integrity RED handoff.
- Reconfirmed the one-test RED before production change.
- Replaced the shared proof-closure rejection vocabulary with the existing published `PROOF_NOT_CLOSED` code; no pair selection, excerpt revalidation, or all-or-nothing branch changed.
- Verified the exact selector and direct proof regression selector before and after one-file formatting.

## Current state

- The unavailable target METHOD parser pair now rejects both shared-owner exact Facts with direct `STATIC_TARGET_TYPE/PROOF_NOT_CLOSED` and sibling `COMPOSITE_FACT_REJECTED`, without admitting affected Facts or Proofs. Unaffected exact and boundary Facts remain; only boundary external-effect gaps remain.
- The changed `attemptAtom` helper is shared by boundary and guard candidates: any future closure rejection now uses the same published `PROOF_NOT_CLOSED` vocabulary, but its subject selection, closure/pair validation, and all other behavior are unchanged. Their direct positive/source-drift coverage remains GREEN.

## Changed files

- `progress/java-exact-call-proof-integrity-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilder.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root/Luna exact unavailable-rule-pair selector | RED (expected) | 1 test, 1 failure, 0 errors; affected exact Facts already reject all-or-nothing but expose the wrong direct reason. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest#rejectsExactCallFactsWhenTargetMethodRulePairIsUnavailable test` | RED (reconfirmed) | 1 test, 1 failure, 0 errors; actual `PROOF_EVIDENCE_CLOSURE_UNPROVEN` versus required `PROOF_NOT_CLOSED`. |
| Same exact selector after the one-file change | PASS | 1 test, 0 failures, 0 errors, 0 skips. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest,GuardConditionAtomicProofTest,AtomicProofBuilderSourceDriftTest test` | PASS | 5 tests, 0 failures, 0 errors, 0 skips before formatting. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilder.java spotless:apply` | PASS | Exactly 1 production file selected; 0 changed and 1 already clean. |
| Same exact absolute one-file `spotless:check` | PASS | Exactly 1 production file selected; 0 need changes. |
| Same three-class proof selector after formatting | PASS | 5 tests, 0 failures, 0 errors, 0 skips. |
| `git diff --check` on the owned production file; trailing-whitespace scan including this progress file | PASS | No whitespace errors. |

## Decisions

- Preserve actual allowed-pair selection, source byte/hash revalidation, Fact rejection, sibling `COMPOSITE_FACT_REJECTED` semantics, unaffected exact/boundary Facts, and boundary-only external gaps.
- Normalize only the rejection vocabulary to existing published `PROOF_NOT_CLOSED`; add no new reason, schema, compatibility path, or behavior outside the M2 failed-pair path.

## Blockers

- None for this bounded M2 integrity slice.

## Exact next action

- Release Maven. Do not modify M1, M2 publication/readers, M3, schemas, frozen tests, or any later persistence work without a separate approved RED/GREEN brief.

## Resume checks

- No M1 missing-material, M2 publisher/reader, M3, Step05, or test changes belong to this task.
