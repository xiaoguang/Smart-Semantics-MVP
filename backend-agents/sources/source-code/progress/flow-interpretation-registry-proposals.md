# Progress: Flow interpretation registry proposals

- Status: BLOCKED
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-04
- Scope: Complete FlowInterpretation: isolated R0 task compilation, scripted R0 execution, a frozen repository interpretation registry, finite-key R1/R2 execution, and the ten official step outputs. Before M1, repair the already-designed Stage05 public Capsule handoff so it carries the complete same-Flow span/obligation values needed by M1. No live Provider, public runtime adapter, or repository knowledge work.
- Approved inputs: `docs/DESIGN.md`; `docs/analysis-steps/06-flow-interpretation.md`; both implementation plans; the published BusinessFlows step `d3f7d41`; frozen fixtures and a scripted Provider only.
- Current branch/worktree: `codex/source-analysis-registry-proposals` at `/private/tmp/linguan-source-analysis-registry-proposals/backend-agents/sources/source-code`

## Completed

- Completed the bounded, persisted M1–M5 vertical slice: R0 task compilation, scripted R0 execution, frozen registry, finite-key R1/R2 task compilation, and scripted R1/R2 execution.
- Each implemented module fresh-reopens its direct upstream canonical artifacts and installs its own module artifact receipt-last; no live Provider, customer source capture, or customer Maven execution occurred.
- Verified the five direct public seams together: 10 tests, 0 failures, 0 errors, 0 skips.
- Published the M1–M5 checkpoint to `origin/main` as `90c3ab9`.

## Current state

M1–M5 are a verified internal vertical slice, not a completed Flow Interpretation step. M6 cannot
be added safely under the current contract: `repository-interpretation-registry.json` is specified
with the same `(artifactType, schemaVersion)` as both M3's module-artifact JSON and M6's standalone
analysis-step JSON. The canonical artifact store intentionally maps each such pair to one envelope
kind, so implementing both would require an unapproved dual write, alias, or wire-identity change.
All experimental M6 changes were removed. The stable checkpoint contains only M1–M5.

## Changed files

- `progress/flow-interpretation-registry-proposals.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskProfile.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTask.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskSet.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskShardReceipt.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilationException.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompiler.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskSetModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalProvider.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalProviderResponse.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunner.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRound.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalGenerationReceipt.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalFlowDisposition.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalExecutionSet.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/BusinessRegistryProposal.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RegistryFreezeException.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RegistryProposalAccounting.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistry.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistryFlowDisposition.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistryFreezer.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistryItem.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistryModulePublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/registry/RepositoryInterpretationRegistryFreezerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompilerTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTaskProfile.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTask.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTaskShardReceipt.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTaskSet.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTaskException.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelTaskSetModulePublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunnerTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelProvider.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowModelProviderResponse.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/ModelRound.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/GenerationReceipt.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/ModelTaskDisposition.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationProposal.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowInterpretationCandidate.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FlowInterpretationDisposition.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationExecutionSet.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunner.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationExecutionSetModulePublisher.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | New worktree began clean at `d3f7d41`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest test` | EXPECTED RED | 1 test; failure `REGISTRY_PROPOSAL_TASK_COMPILER_NOT_IMPLEMENTED`; no compile/test error. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowsPublicationSpecifierTest test` | EXPECTED RED | 3 tests; one failure because public Capsule schema is v1 rather than required v2; no errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowsPublicationSpecifierTest test` | UNEXPECTED RED | 3 tests; all fail `FLOW_PUBLICATION_SPECIFIER_FAILED` caused by `FLOW_ACCOUNTING_INVARIANT_BROKEN` after the first v2 implementation. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceCapsuleProjectorTest` | PASS | 3 tests; added same-Flow span/obligation ownership and closure assertion passes. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceCapsuleProjectorTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 6 tests; v2 public Capsule handoff, policy allowlist, and Stage05 publication all pass. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest test` | PASS | 1 test; two eligible persisted Capsules compile to two unique isolated canonical R0 tasks with complete Capsule views and recomputed SHA-256. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest test` | PASS | 2 tests; the all-ineligible public BusinessFlows case preserves its Flow publication but generates an empty R0 denominator and zero tasks. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | BLOCKED_EXTERNAL | 56 pre-existing unrelated source/test files violate formatting; no global formatter was run because it would rewrite unrelated work. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest#persistsTheClosedR0TaskSetBeforeTheProviderRunnerCanReadIt test` | EXPECTED RED | 1 test; `RegistryProposalTaskSetModulePublisher` was absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest test` | PASS | 3 tests; M1 task-set compiler, zero eligible denominator, and persisted receipt-last module artifact all pass. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest test` | EXPECTED RED → PASS | First run failed only because the R0 provider and runner types were absent; the scripted valid-term run now produces one round, receipt, proposal and `READY_FOR_FREEZE` disposition per persisted task. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#acceptsOneSameFlowBusinessTermFromEachPersistedR0TaskWithoutChangingItsEvidence test` | EXPECTED RED | 1 test; exact failure `REGISTRY_PROPOSAL_EXECUTION_SET_PUBLISHER_NOT_IMPLEMENTED`, with no errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#acceptsOneSameFlowBusinessTermFromEachPersistedR0TaskWithoutChangingItsEvidence test` | PASS | 1 test; M2 fresh-reopens M1 and installs `registry-proposal-execution-set.json` receipt-last without another Provider call. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#failsAfterTheFirstStartedProviderFailureWithoutRetryingAnotherTask test` | EXPECTED RED → PASS | First run exposed the generic response-invalid wrapper; M2 now returns `PROVIDER_FAILURE_AFTER_START` with the original cause and exactly one scripted call. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#closesEachEligibleFlowAsTypedGapUsingOnlyItsPersistedCapsuleGap test` | EXPECTED RED → PASS | The response kind was initially rejected; an allowed same-Capsule Gap now gives every eligible Flow one typed `GAP` disposition and no proposal. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest test` | PASS | 4 tests; valid proposal, typed failure, typed Gap, receipt-last M2 persistence, and no-retry provider failure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryInterpretationRegistryFreezerTest test` | EXPECTED RED | The pure freezer passed; the expected remaining failure was `REPOSITORY_INTERPRETATION_REGISTRY_PUBLISHER_NOT_IMPLEMENTED`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryInterpretationRegistryFreezerTest test` | PASS | 1 test; M3 accepts fresh M1/M2/BusinessFlows closure and installs the receipt-last frozen registry. Two nullable source fields are compared explicitly, so a valid absent reason/seed is not treated as a failure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FiniteKeyFlowTaskCompilerTest test` | EXPECTED RED | 1 test; `FINITE_KEY_FLOW_TASK_COMPILER_NOT_IMPLEMENTED` because the immutable R1/R2 profile/compiler is absent; no compile error. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FiniteKeyFlowTaskCompilerTest test` | EXPECTED RED → PASS | Compiler first passed the task shape; the publisher-specific RED was `FLOW_MODEL_TASK_SET_PUBLISHER_NOT_IMPLEMENTED`. The final 1-test selector passes with M4 fresh reopen and receipt-last persistence. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest test` | EXPECTED RED | 1 test; `INTERPRETATION_RUNNER_NOT_IMPLEMENTED` because M5's provider/runner types are absent; no compile error. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest test` | PASS | 1 test; 2 ready flows produce four ordered scripted calls, four rounds/receipts, two candidates, and two `READY_FOR_ADMISSION` final dispositions. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest test` | EXPECTED RED → PASS | The M5 persistence RED was `INTERPRETATION_EXECUTION_SET_PUBLISHER_NOT_IMPLEMENTED`; the final selector fresh-reopens its three direct inputs and receipt-last installs `model-execution-set.json`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest,RegistryProposalRunnerTest,RepositoryInterpretationRegistryFreezerTest,FiniteKeyFlowTaskCompilerTest,InterpretationRunnerTest test` | PASS | 10 tests, 0 failures, 0 errors, 0 skips across M1–M5. |
| owned-file `spotless:check` | PASS | Only interpretation implementation/tests, artifact engine, and fixture policy checked; global Spotless remains out of scope because of existing unrelated violations. |
| `git diff --check` | PASS | No whitespace errors in the checkpoint diff. |

## Decisions

- M1–M3 freezes the R0 denominator and registry before M4 creates any R1/R2 task. The user requested completion of the whole FlowInterpretation step here, so M4–M6 follow M3's direct GREEN.
- Automated tests use a recording scripted Provider only; no real Provider, API key, customer Maven, or source capture is in scope.
- Do not make FlowInterpretation read a Stage05 private module artifact, re-open source bytes, or use a partial Capsule view. The smallest correct repair is to enrich the existing public `evidence-capsules.jsonl` item with the complete span and projection-obligation values, then advance its schema/version and update the Stage05/06 public contract together.
- The initial post-v2 failure was not cross-Flow evidence sharing. A direct M2 ownership test proved every span and obligation has one Capsule owner. The actual cause was the generic artifact contract and fixture policy still allowlisting v1; both now allowlist only v2.
- M1 receives `RegistryProposalTaskProfile` only as a package-level, immutable projection of a
  previously verified `analysis-run-request-v2`. Its schema/runtime digests must match the
  persisted BusinessFlows controls; a future adapter cannot bypass request admission by constructing
  the profile itself.
- Correction before coding: the pre-model BusinessFlows receipt may legally carry a null prompt
  digest. Therefore M1 checks schema and profile/runtime against that receipt; its exact prompt
  reference is frozen by the admitted run request and must not be inferred from the nullable receipt.

## Blockers

- **M6 registry envelope collision:** before M6 implementation, design authority must choose one
  stable relationship between the M3 frozen registry and the reader-visible M6 registry: either a
  new public artifact type/schema, or one shared canonical envelope made valid for both uses. The
  current same-type/schema-but-two-envelope requirement cannot be implemented without violating
  canonical identity. Do not add compatibility readers or dual writes.
- Repository-wide Spotless has 56 pre-existing unrelated violations. This checkpoint uses the
  owned-file selector and `git diff --check`, without rewriting unrelated work.

## Exact next action

On the next work session, read the M6 collision above and record a focused design decision before
writing any M6 publication code.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm `origin/main` contains `90c3ab9` and that M6 still has no approved envelope/identity decision.
