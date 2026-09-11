# Progress: exact-call M2 Proof implementation

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: The approved positive M2 `JAVA_EXACT_CALL` proof projection in `AtomicProofBuilder` and `ProofRuleRegistry` only.
- Approved inputs: Step04 §8.0.2, frozen `AtomicProofBuilderTest` exact-call RED, fresh M1 v3 candidates, and persisted ProgramGraphs inputs.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this implementation progress record before production edits.
- Read the approved exact-call M2 contract and Luna's frozen RED handoff.
- Added the explicit `JAVA_EXACT_CALL` call-site/call-target/METHOD subject projection and exact target-canonical atom projection.
- Replaced the closed proof-rule registry with v3 and allowed the target `METHOD` source-element-parser pair.
- Reconfirmed the one-test RED, then reached one-test GREEN. The next test author is preparing a distinct frozen test file, so Maven is held at the root's request before aggregate verification.

## Current state

- `AtomicProofBuilder` closes `INVOCATION_CALL_ID` from the persisted call site and call-target edge, and each static-target atom from that edge plus the persisted target METHOD. It never reads boundary static-target fields for exact candidates. `ProofRuleRegistry` is `proven-code-facts-proof-rules-v3` and permits `METHOD` with `source-element-parser-v1/v1`.
- The exact positive selector and the direct proof regression selector are both GREEN after the two-file scoped formatter. This bounded M2-positive implementation is complete; M1 negative closure, M2 publication/readers, M3, Step05, and the separate missing-evidence negative behavior remain outside this task.

## Changed files

- `progress/java-exact-call-proof-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/proofs/ProofRuleRegistry.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root-run exact `AtomicProofBuilderTest#provesExactCallsFromFreshPublishedCandidatesWithClosedAtomsAndRuleEvidence` | RED (expected) | 1 test, 0 failures, 1 error: `PROOF_DECISION_INVALID: atom value` at legacy `AtomicProofBuilder.canonicalValue`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest#provesExactCallsFromFreshPublishedCandidatesWithClosedAtomsAndRuleEvidence test` | RED (reconfirmed) | 1 test, 0 failures, 1 error: `PROOF_DECISION_INVALID: atom value` at `AtomicProofBuilder.java:429`. |
| Same exact selector after the two-file implementation | PASS | 1 test, 0 failures, 0 errors, 0 skips. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest,GuardConditionAtomicProofTest,AtomicProofBuilderSourceDriftTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skips before formatting. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilder.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/proofs/ProofRuleRegistry.java spotless:apply` | PASS | Exactly 2 production files selected; 1 changed to clean and 1 already clean. |
| Same exact absolute two-file `spotless:check` | PASS | Exactly 2 production files selected; 0 need changes. |
| Same three-class test selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skips. |
| `git diff --check` on the two production files; trailing-whitespace scan including this progress file | PASS | No whitespace errors. |

## Decisions

- Retain source byte/hash revalidation and all existing boundary/guard proof behavior.
- Use the persisted exact call-site, call-target edge, and target METHOD fields; do not parse customer source or reuse null boundary target fields.
- Limit this slice to M2 proof construction and proof-rule registry v3. M2 publication/readers, M3, Step05, and the separate missing-evidence negative behavior remain outside scope.

## Blockers

- None for this bounded positive slice.

## Exact next action

- Release Maven. Do not run the intentionally RED M1 candidate aggregate or modify its silent-row-loss behavior without the separate approved GREEN brief.

## Resume checks

- Preserve the exact four-atom closure, edge/METHOD evidence requirements, no exact `ExternalEffectGap`, and retained boundary gap assertions from Luna's frozen test.
