# Progress: Guard condition facts delivery

- Status: COMPLETE
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the published ProvenCodeFacts v2 contract only: evidence-backed Java guard-condition candidates, atomic proofs, v2 fact accounting/public wire, and its direct public-seam tests. Do not implement BusinessFlows, capsules, a model Provider, customer-source capture, or runtime recovery.
- Approved inputs: User confirmation of the recommended guard-condition producer; published design commits f332e0c/a2ea789 and implementation commit 46f9603; docs/DESIGN.md; docs/analysis-steps/04-proven-code-facts.md; docs/analysis-steps/05-business-flows.md; both published implementation plans; frozen fixtures only.
- Current branch/worktree: codex/source-analysis-guard-condition-facts at /private/tmp/linguan-source-analysis-guard-condition-facts/backend-agents/sources/source-code

## Completed

- Created from the published docs-only contract commit f332e0c.
- Read the root/scoped rules, the Step04/05 contract, both implementation plans, and the existing Fact candidate/proof/publication seams.
- Confirmed the public seam: FactCandidateEnumerator, AtomicProofBuilder, and FactLedgerPublicationSpecifier, all operating on canonical persisted artifacts rather than raw paths or in-memory graph drafts.
- Added a guarded frozen source fixture and the M1 public-seam RED: the v1 enumerator produced zero `JAVA_GUARD_CONDITION` candidates.
- Implemented the first M1 vertical slice: v2 registry includes `JAVA_GUARD_CONDITION`; fresh-reopened control nodes retain canonical conditions; the enumerator requires one same-entry guard, its TRUE/FALSE branches, and its evidence closure before creating one independent candidate.
- Implemented M2's independent `CONTROL_CONDITION` proof: it uses the public normalized guard condition, its own source/rule Evidence pair and no required program edge. Boundary `CONTROL_CONTEXT` remains separate; only boundary candidates receive an external-effect Gap.
- Implemented M3's v2 four-payload publication and accounting closure. It rejects mismatched candidate, atom or external-Gap denominators, writes explicit boundary/guard partitions, and requires every external-effect Gap to belong to a boundary candidate.
- The direct M1, M2 and M3 selectors are GREEN.

## Current state

The branch is based on `46f9603`, which publishes the dedicated public `normalizedCondition`. The prior draft incorrectly used an internal technical `canonicalValue`; the v2 candidate now retains the public field through its persisted reader. A guarded fixture proves two boundary Facts, one independent guard Fact and exactly two boundary-only external-effect Gaps. The delivery is verified and ready for the one Step04 commit/push.

## Changed files

- progress/guard-condition-facts-delivery.md
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/GuardConditionFactCandidateTest.java
- src/test/java/org/sourceanalysis/app/analysis/fact/proofs/GuardConditionAtomicProofTest.java
- src/test/java/org/sourceanalysis/app/analysis/fact/publish/GuardConditionFactLedgerTest.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactRegistry.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateInputs.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java
- src/main/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilder.java
- src/main/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModulePublisher.java
- src/main/java/org/sourceanalysis/app/analysis/fact/proofs/PersistedProofDecisionSetReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java
- src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java
- docs/analysis-steps/04-proven-code-facts.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short in the new worktree | PASS | Clean f332e0c baseline before this progress record. |
| Published-design review | PASS | v2 keeps four Step04 public payloads and the eight-step flow; only candidate/proof/accounting semantics change. |
| mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateEnumeratorTest,AtomicProofBuilderTest,ProvenCodeFactsPublicationSpecifierTest test | PASS | 3 tests, 0 failures/errors/skips. Surefire ran AtomicProofBuilderTest (1) and ProvenCodeFactsPublicationSpecifierTest (2); the requested FactCandidateEnumeratorTest class does not exist on the v1 baseline. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest test | RED | 1 test, 1 assertion failure: guarded persisted fixture contained 0 `JAVA_GUARD_CONDITION` candidates. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest test | PASS (superseded) | 1 test, 0 failures/errors/skips after M1 candidate enumeration; it did not yet assert the condition value. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest test (before condition correction) | EXPECTED RED | 1 test failure: expected `status == null`, got the technical `guard:<method>:status == null` key. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest test (after condition correction) | PASS | 1 test, 0 failures/errors/skips. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest,FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest,FactCandidateExactUpstreamTest test | TEST FIX NEEDED | 3 selectors passed; the decoy test used module version v1 although its new v2 envelope declared v2, so store validation correctly rejected it before the intended persisted-reader check. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest,FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest,FactCandidateExactUpstreamTest test (after decoy correction) | PASS | 4 tests, 0 failures/errors/skips. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionAtomicProofTest test | RED → PASS | First run failed because a guard was incorrectly passed to `ExternalEffectGap`; after M2 completion, 1 test, 0 failures/errors/skips. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactLedgerTest test | RED → PASS | First run failed with the v1 M3 wire rejected by the v2 artifact policy; after M3 v2 accounting, 1 test, 0 failures/errors/skips. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactLedgerTest,ProvenCodeFactsPublicationSpecifierTest test | PASS | 3 tests, 0 failures/errors/skips. |
| mvn -t .mvn/toolchains.xml -o -Dtest=GuardConditionFactCandidateTest,FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest,FactCandidateExactUpstreamTest,GuardConditionAtomicProofTest,ProofDecisionSetModuleArtifactTest,PersistedProofDecisionSetReaderTest,AtomicProofBuilderSourceDriftTest,AtomicProofBuilderTest,ProvenCodeFactsPublicationSpecifierTest,GuardConditionFactLedgerTest test | PASS | 12 tests, 0 failures/errors/skips. |
| Targeted Spotless apply/check | PASS | Every changed Java file is formatted; the unrelated full-module formatting backlog was not rewritten. |
| git diff --check | PASS | No whitespace errors. |

## Decisions

- The condition producer belongs in Step04, not Flow compilation: this keeps every BranchDecision.conditionAtomId traceable to a Fact, Proof and Evidence.
- The v2 wire is a hard reset for ProvenCodeFacts readers: no alias, dual write, or v1 fallback.
- Existing Step05 work remains in its own worktree and will consume the v2 publication only after this branch is delivered.
- Step04 public file count remains four semantic payloads plus receipt; this delivery changes only v2 meaning and closed accounting, not the eight-step workflow or reader-visible file count.

## Blockers

- None.

## Exact next action

Start the already-planned BusinessFlows work only after this complete Step04 delivery is committed and visible on `origin/main`; it must fresh-reopen the v2 public Fact ledger rather than reuse this process's objects.

## Resume checks

- Re-read this file and run git status --short.
- Confirm HEAD is based on published `46f9603`.
- Reopen docs/analysis-steps/04-proven-code-facts.md sections 2, 8.0 and 8.0.1 before changing any production wire.
