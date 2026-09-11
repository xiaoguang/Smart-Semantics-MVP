# Progress: exact-call M3 ledger/publication

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare one typed public-seam shared-call M1v3→M2v3→M3 accounting/publication test covering the four public v3 Fact/Proof/Gap/accounting schemas and unchanged module-4 semantic + receipt shape.
- Approved inputs: Step04 §8.0.2 v3 contract, real `ProgramGraphsPublicFixture.createWithSharedJavaCall`, current public M1/M2/M3 APIs and accounting tests only; no production/design/Step05/Provider/customer/network changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed `FactLedgerPublicationSpecifier.specifyCandidatesAndProofs(...)` is the typed M3 seam and installs four standalone semantic payloads plus one analysis-step receipt.
- Confirmed current accounting fields are `candidateDenominatorKeys`, `boundaryCandidateDenominatorKeys`, `guardCandidateDenominatorKeys`, admitted/rejected Fact and atom keys, and external-effect gap IDs/counts; the v3 contract needs `exactCallCandidateDenominatorKeys` in the same accounting union.
- Confirmed before apply that M3 production and fixture public-Fact policy entries were v2 while M1/M2 fixture policy entries were already v3; the four fixture policies are now v3 and M3 production remains the only stale v2/accounting seam.

## Current state

- Java/Maven gate was open for this bounded slice. The shared-call M3 test and approved v3 fixture/test migrations are applied and formatted.
- The applied test derives exact owner-edge keys from fresh shared-call M1v3 candidates, consumes fresh M2v3 decisions, publishes M3, and asserts exact 3-key exact-call accounting plus disjoint boundary/guard/exact unions.
- The initial selector RED reached all shared-call M1/M2 premises and hit the stale M3 accounting partition equation with `FACT_ACCOUNTING_INVARIANT_BROKEN`; the final selector rerun after Terra's M3 production fix is GREEN and all post-publication oracles pass.
- The guarded-fixture regression now derives its four actual exact-call denominator keys from reopened candidates and compares the published accounting array exactly; it no longer assumes that fixture has zero exact calls.
- The bounded guard-ledger oracle correction is complete: the guarded fixture's actual four exact-call keys are compared as a sorted typed-key list, while boundary=2, guard=1, external-effect gaps=2, and no guard/exact gap ownership remain asserted.

## Intended test oracles

- Derive `exactKeys` from M1 candidates filtered by `JAVA_EXACT_CALL`; require exactly 3 owner-edge keys, and require the M2 decision set to contain the corresponding exact Facts and four closed atom Proofs per Fact before M3.
- Reopen the M3 analysis-step publication and require exactly `fact-accounting.json`, `gap-ledger.json`, `proof-pack.json`, and `proven-facts.json` semantic payloads plus the unchanged single receipt.
- Require all four descriptors and JSON headers to be v3; require accounting `exactCallCandidateDenominatorKeys` to equal `exactKeys`, and require boundary/guard/exact candidate-key lists to be pairwise disjoint with their union equal to all candidate denominator keys.
- Derive admitted/rejected Fact keys, admitted/rejected atom disposition keys, external-effect gap IDs, and all public Fact/Proof values from the actual typed M2 decisions; assert exact serialized arrays/counts and fresh public reopen values, with external-effect gaps limited to boundary keys.

## Changed files

- `progress/java-exact-call-ledger-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/publish/ProvenCodeFactsPublicationSpecifierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/publish/GuardConditionFactLedgerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateExactUpstreamTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (four public Fact policies v2→v3 only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Public API/fixture inspection | PASS | M3 typed specifier, four payload names/fields, module-4 + receipt shape, guard accounting assertions, and exact upstream v2 constants were inspected. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProvenCodeFactsPublicationSpecifierTest#publishesSharedExactCallFactLedgerWithDisjointV3AccountingAndFreshReopen test` | RED (expected M3 accounting gap) | 1 test, 0 failures, 1 error, 0 skips; shared fixture produced 2 boundary keys, 3 exact-call keys, full M2 Facts/Proofs, and boundary-only external gaps before `FactLedgerPublicationSpecifier.requireAccountingClosure` rejected the stale boundary+guard-only partition at line 396. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateExactUpstreamTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skips; v3 candidate descriptor/moduleVersion/hash frame still reaches the intended `PROOF_PACK_REFERENCE_BROKEN` decoy-lineage rejection. |
| Pinned four-file Spotless apply | PASS | Selected exactly 4 owned Java files; 1 changed to clean and 3 were already clean. |
| Pinned four-file Spotless check | PASS | Selected exactly 4 owned Java files; all clean. |
| `git diff --check -- progress/java-exact-call-ledger-tests.md` | PASS | No whitespace errors in the updated owned progress note. |
| Final `mvn -o -t .mvn/toolchains.xml -Dtest=ProvenCodeFactsPublicationSpecifierTest#publishesSharedExactCallFactLedgerWithDisjointV3AccountingAndFreshReopen test` | PASS | 1 test, 0 failures, 0 errors, 0 skips; all four v3 payloads, receipt, full typed Fact/Proof/disposition/Gap values, disjoint accounting IDs, and fresh reopen passed. |
| Final `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateExactUpstreamTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skips after the M3 production fix. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=GuardConditionFactLedgerTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skips; actual guarded fixture has boundary=2, guard=1, exact=4 and the exact accounting keys match the candidate-derived sorted keys; exact/guard keys are absent from external gaps. |
| Pinned one-file Spotless apply | PASS | Selected exactly 1 owned Java file; the file remained clean. |
| Pinned one-file Spotless check | PASS | Selected exactly 1 owned Java file; 0 needed changes and the build succeeded. |
| `git diff --check -- progress/java-exact-call-ledger-tests.md src/test/java/org/sourceanalysis/app/analysis/fact/publish/GuardConditionFactLedgerTest.java` | PASS | No whitespace errors in the corrected test or progress note. |

## Decisions

- Reuse actual shared-call graph/candidate/proof inputs and derive every denominator, Fact, atom, Proof, Gap, and accounting count from public typed records; do not assume legacy totals.
- Preserve boundary-only external-effect gaps and assert exact-call Facts do not create external-effect gaps.
- Apply-time migration is limited to the four fixture public-Fact policies plus contractual v3 test constants/formula/guard rejection reason; module-4 semantic and receipt remain unchanged.
- Exact minimal apply-time migration list: `ProgramGraphsPublicFixture.java` four policies (`PROVEN_FACTS`, `PROOF_PACK`, `GAP_LEDGER`, `FACT_ACCOUNTING`) v2→v3; `ProvenCodeFactsPublicationSpecifierTest.java` v3 schema/formula expectations and shared-call test; `GuardConditionFactLedgerTest.java` four v3 schema expectations and old rejected-fixture reason to `PROOF_NOT_CLOSED`; `FactCandidateExactUpstreamTest.java` candidate v3 descriptor/moduleVersion/hash frames at its existing v2 constants.
- Existing positive/rejected publication tests now derive complete candidate/admitted/rejected counts and per-kind key lists from typed records, preserve the explicit two boundary Facts/two external gaps, use `PROOF_NOT_CLOSED` for the synthetic direct rejection, and create external-effect gaps only for boundary candidates. Atom/disposition wire lookups use the compound candidate-key plus atom-key identity.
- `GuardConditionFactLedgerTest` must treat the guarded fixture as boundary=2, guard=1, exact=4 from actual candidate records; its exact accounting array is compared in sorted order and exact/guard keys are excluded from external-effect gaps.
- Do not touch Step05 readers/production, flow/capsule code, or unrelated M3 totals in this slice; module-4 semantic payload count and one receipt remain four-plus-one.

## Blockers

- None. The correction is test-oracle-only; no production/Step05 edit was made in this slice.

## Exact next action

- Correction complete: release Maven to Terra/root. The paused three-test regression-migration preparation may resume only under a new gate; do not edit production or Step05 in this slice.

## Resume checks

- Re-read this note before continuation. Preserve the exact 3-key exact-call denominator, disjoint union/accounting equations, boundary-only external gaps, fresh public reopen/equality, four v3 public schemas, unchanged module-4/receipt shape, and no production/Step05 changes.
