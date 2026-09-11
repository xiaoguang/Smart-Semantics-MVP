# Progress: exact-call M2 proof integrity negative

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare one public-seam M2 negative test in `AtomicProofBuilderTest` for a target METHOD whose permitted `source-element-parser-v1` source/rule closure is unavailable while generic M1 closure remains valid.
- Approved inputs: merged Step04 §8.0.2 and PR12 public records, real `ProgramGraphsPublicFixture.createWithSharedJavaCall`, fresh five-graph inputs, valid M1 candidates, and immutable public in-memory adversarial Evidence/input copies only; no production/schema/API/status/design/Provider/customer/network changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Current state

- Java/Maven was released after Terra's M1 GREEN and is now released again after this bounded verification.
- The applied test keeps generic M1 Evidence closure intact, replaces only the target METHOD's permitted `source-element-parser-v1/v1` rule payload for the shared target, and labels the mutation as an in-memory negative rather than persisted-reader behavior.
- Before invoking M2, the test derives and establishes exact M1 candidate presence plus the two shared-owner affected denominator keys from public records.

## Completed

- Confirmed `AtomicProofBuilder.prove(candidates, inputs, source, rules)` is the public M2 seam and that exact-call `STATIC_TARGET_*` atoms require the target METHOD's `source-element-parser-v1/v1` pair.
- Confirmed the public `FactCandidateInputs.PublicEvidenceGraph`, `EvidenceNode`, `RuleApplication`, and `EvidenceEdge` records can express the negative without changing graph IDs, source excerpts, locators, or persisted-reader bytes.
- Confirmed the mutation can retain the target source excerpt and same rule-application input subject while replacing only the target METHOD rule payload with the actual call-target edge rule ID derived from the fresh CALL graph; M1 `closureFor` remains generic-valid, while M2's METHOD allowlist rejects the replacement.
- Applied exactly one new public-seam test in `AtomicProofBuilderTest`; no existing proof or boundary assertion was removed or weakened.
- The test re-enumerates the mutated public input and proves the exact M1 premise: 3 exact candidates remain, including 2 shared-owner affected denominator keys; the target generic M1 closure remains present with a non-parser rule payload.
- The mutation is explicitly an immutable public in-memory negative `FactCandidateInputs` copy; no claim is made that a modified persisted reader accepted it.

## Intended assertions

- Affected exact candidates remain accounted but are wholly rejected: direct `STATIC_TARGET_TYPE` atom is `PROOF_NOT_CLOSED`, every sibling atom is `COMPOSITE_FACT_REJECTED`, and no Fact or Proof is admitted for either affected key.
- Unaffected exact and boundary Facts remain; original external-effect Gaps remain; no additional external-effect Gap is created for exact-call rejection.

## Changed files

- `progress/java-exact-call-proof-integrity-tests.md` only so far.
- `src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg`/`sed` inspection of Step04 §8.0.2, `AtomicProofBuilder`, `ProofRuleRegistry`, `FactCandidateInputs`, `FactCandidateSet`, and current `AtomicProofBuilderTest` | PASS | Public M2 seam, exact atom rejection order, all-or-nothing sibling behavior, and immutable Evidence/input records are available. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest#rejectsExactCallFactsWhenTargetMethodRulePairIsUnavailable test` | RED (expected) | 1 test, 1 failure, 0 errors, 0 skips; all M1 premise assertions passed, then affected Fact disposition reported current `PROOF_EVIDENCE_CLOSURE_UNPROVEN` instead of required `PROOF_NOT_CLOSED`. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java spotless:apply` | PASS | Spotless selected exactly 1 file and changed it to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java spotless:check` | PASS | Spotless selected exactly 1 file; no formatting changes needed. |
| `git diff --check -- progress/java-exact-call-proof-integrity-tests.md` | PASS | No whitespace errors in the owned progress note. |

## Decisions

- Derive target/rule IDs and affected owner keys from fresh reopened public graph/candidate records; do not hardcode IDs or fabricate source/hash/locator claims.
- Use only immutable public graph/evidence/input record copies for the negative and explicitly avoid claiming the mutated input passed through a reader.
- Keep exactly one new test and do not remove or weaken existing proof/boundary assertions.
- Replace only the shared target METHOD's parser rule application payload with the actual CALL edge rule ID; retain all Evidence nodes/edges and source IDs so generic M1 closure stays present but the METHOD-specific permitted pair is unavailable.
- Treat the observed `PROOF_EVIDENCE_CLOSURE_UNPROVEN` as the precise current production RED; the test oracle remains the approved `PROOF_NOT_CLOSED` M2 contract.

## Blockers

- No test-slice blocker remains. The bounded RED identifies the remaining M2 reason-code implementation gap; production and schema changes remain outside this owned slice.

## Exact next action

- Release Maven to root with the precise RED and mutation classification; root may now implement the M2 reason-code behavior. Do not run an aggregate or edit production/schema/publication in this slice.

## Resume checks

- Re-read this note before continuation. Preserve the in-memory-negative classification, parser-pair-only mutation, two shared-owner keys, one-test scope, and no-claim of reader acceptance for mutated inputs.
