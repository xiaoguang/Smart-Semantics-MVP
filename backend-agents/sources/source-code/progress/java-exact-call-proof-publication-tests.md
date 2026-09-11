# Progress: exact-call M2 v3 proof publication

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare one public-seam persistence test in `ProofDecisionSetModuleArtifactTest` for the complete v3 M2 exact-call decision set, fresh typed-reader reopening, and retained boundary assertions.
- Approved inputs: Step04 §8.0.2 v3 replacement, real `ProgramGraphsPublicFixture.createWithSharedJavaCall`, fresh graph inputs, fresh M1 v3 publication/read, actual typed M2 decisions, and current public publisher/reader APIs only; no production/schema/design/Provider/customer/network changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed the public typed reader signature is `PersistedProofDecisionSetReader.reopen(proofPublication, inputs, candidatePublication, candidateSet)` and the fresh-store seam can construct a new `FileSystemCanonicalModuleArtifactStore` over the same `RunStoreHandle`.
- Confirmed the current fixture registers `PROVEN_CODE_FACTS_PROOF_DECISION_SET` as `proven-code-facts-proof-decision-set-v2`; the bounded apply patch must change only that M2 fixture policy entry and this test's M2 schema constant to v3.
- Confirmed the current M2 publisher/reader production constants and module producer are v2, so with the fixture/test v3 cutover the first RED should be the stale production M2 policy/schema/registration gate, not missing shared-call fixture data.
- Applied exactly one new publication/fresh-reader test, plus only the related fixture M2 policy and this test's M2 schema/module-version constants v2→v3.
- The new test establishes the shared fixture's 3 exact Facts and 12 exact closed Proofs before publication, validates complete serialized decision-array counts and sorted upstream references, and uses a new typed reader over a new store instance sharing the same RunStoreHandle.
- Migrated the existing positive publication method to retain explicit boundary-only assertions (2 `JAVA_BOUNDARY_INVOCATION` Facts, 16 closed boundary Proofs, 16 closed boundary atom dispositions, and 2 boundary external-effect Gaps) while deriving and asserting the additional exact-call rows from the real candidate set.

## Current state

- Java/Maven gate was released after Terra's M2 allowed-rule normalization and is now released again after this bounded verification and the old-method semantic migration.
- The applied test establishes three exact owner-edge Facts and twelve closed Proofs (four per Fact) before publication, then verifies v3 descriptor/schema/moduleVersion, exact upstream references, complete serialized fields, and fresh typed-reader equality; the M2v3 publication/fresh-reader test is GREEN.
- Existing positive boundary totals remain explicit and untouched; the old shared-boundary publication test derives exact-call keys from the actual candidate set and derives every serialized array size from the complete `ProofDecisionSet`.

## Changed files

- `progress/java-exact-call-proof-publication-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModuleArtifactTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (M2 policy entry only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Public API/fixture inspection | PASS | Reader signature, publisher seam, v2 M2 fixture policy, current descriptor fields, and fresh-store construction were confirmed before apply. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProofDecisionSetModuleArtifactTest#publishesAndFreshReopensTheV3ExactCallProofDecisionSet test` | RED (expected stale gate) | 1 test, 0 failures, 1 error, 0 skips; fresh shared fixture/M1/M2 premise reached publication, then current v2 publisher rejected the v3 M1 predecessor with `PROOF_PACK_REFERENCE_BROKEN` at its candidate-schema gate. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModuleArtifactTest.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java spotless:apply` | PASS | Spotless selected exactly 2 owned Java files; 1 changed to clean and 1 was already clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModuleArtifactTest.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java spotless:check` | PASS | Spotless selected exactly 2 owned Java files; both clean. |
| `git diff --check -- progress/java-exact-call-proof-publication-tests.md` | PASS | No whitespace errors in the owned progress note. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProofDecisionSetModuleArtifactTest#publishesAndFreshReopensTheCompleteProofDecisionSet test` | PASS | 1 test, 0 failures, 0 errors, 0 skips after deriving boundary/exact subsets and complete serialized counts. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProofDecisionSetModuleArtifactTest test` | PASS | 2 tests, 0 failures, 0 errors, 0 skips post-format. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModuleArtifactTest.java spotless:apply` | PASS | Spotless selected exactly 1 owned Java file; 1 changed to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModuleArtifactTest.java spotless:check` | PASS | Spotless selected exactly 1 owned Java file; clean. |
| `git diff --check -- progress/java-exact-call-proof-publication-tests.md` | PASS | No whitespace errors in the updated owned progress note. |
| Final old-method selector rerun after removing the redundant global gap total | PASS | 1 test, 0 failures, 0 errors, 0 skips. |
| Final class selector rerun | PASS | 2 tests, 0 failures, 0 errors, 0 skips. |
| Final pinned one-file Spotless apply/check | PASS | Selected count 1; file clean after apply and check. |

## Decisions

- Use a fresh `FileSystemCanonicalModuleArtifactStore` over the same `RunStoreHandle` and a new `PersistedProofDecisionSetReader` instance for the reopen assertion.
- Inspect actual reader signature and wire fields rather than infer them; preserve the existing positive boundary assertions and adjust totals only from observed fixture outputs.
- Keep M1 candidate publication/read v3 and migrate only the fixture M2 policy plus this test's M2 expected schema constant at apply time.
- Use typed `ProofDecisionSetModulePublisher` and typed `PersistedProofDecisionSetReader` in the new test; compare the reopened `ProofDecisionSet` to the complete pre-publication decisions object.
- Assert every serialized decision array is an actual JSON array and retains the corresponding complete typed count; assert exact upstream artifact IDs/SHA-256 references in sorted wire order.
- Keep the old positive method's boundary accounting explicit while deriving exact-call denominator keys from `FactCandidateSet`; never replace the complete serialized totals with a hardcoded combined count.
- Keep future `FactCandidateExactUpstreamTest` v2 constants outside this bounded slice; it was not modified or run and requires the coordinated M1 v3 regression migration.

## Blockers

- None for this bounded test slice. The original stale-v2 RED is retained as historical evidence; the v3 publication and fresh-reader path now passes.

## Exact next action

- Release Maven to root with the old-method/class GREEN and one-file formatting evidence; do not run an aggregate or edit production/public Step04/M3/Step05 in this slice.

## Resume checks

- Re-read this note before continuation. Preserve the v3-only M2 scope, fresh typed-reader/store reopen, complete exact-call field assertions, and no production/public Step04/M3/Step05 changes.
