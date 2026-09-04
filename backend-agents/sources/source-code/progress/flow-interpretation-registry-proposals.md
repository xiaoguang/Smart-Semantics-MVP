# Progress: Flow interpretation registry proposals

- Status: IN_PROGRESS
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement FlowInterpretation M1–M3: isolated R0 task compilation, scripted R0 execution, and one frozen repository interpretation registry. Before M1, repair the already-designed Stage05 public Capsule handoff so it carries the complete same-Flow span/obligation values needed by M1. No R1/R2, live Provider, public runtime adapter, or repository knowledge work.
- Approved inputs: `docs/DESIGN.md`; `docs/analysis-steps/06-flow-interpretation.md`; both implementation plans; the published BusinessFlows step `d3f7d41`; frozen fixtures and a scripted Provider only.
- Current branch/worktree: `codex/source-analysis-registry-proposals` at `/private/tmp/linguan-source-analysis-registry-proposals/backend-agents/sources/source-code`

## Completed

- Created the worktree from the remote main commit that contains the completed BusinessFlows delivery.
- Read repository and scoped rules, the FlowInterpretation detailed design, and implementation plans before making implementation changes.
- Added the first public-seam R0 compiler RED test. Its direct selector compiles and fails only because the R0 compiler/profile are absent.
- Published design correction `26212c2` to `origin/main`, retaining the five-file BusinessFlows public set while requiring the existing Capsule line to carry its complete span and obligation values.

## Current state

The R0 compiler RED is established. The public Capsule handoff had omitted the full values behind
its span and obligation IDs. The target design correction was published at `26212c2`, and the user
has explicitly approved the matching v2 public-wire repair. The repair was published at `c3b3e10`;
each public Capsule now embeds exactly its own complete spans and obligations, and the public schema
allowlist plus fixture policy are v2. The profile lineage clarification was published at `c896d86`.
The first minimal M1 implementation now fresh-reopens the five public BusinessFlows artifacts,
validates their denominator, capsule closure, receipt controls and limits, and emits only one
canonical R0 input per eligible Capsule. M1 now persists that closed task set and M2 has a first
scripted-provider GREEN for valid same-Flow term proposals. Before adding M2 typed Gap/Failed
behavior, the exact provider response envelope is being published as a design-only correction.

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
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`

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

## Decisions

- M1–M3 is a bounded, separately reviewable delivery. It freezes the R0 denominator and registry before any R1/R2 code is introduced.
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

- Repository-wide Spotless is currently blocked by 56 unrelated existing violations. The M1 delivery
  will use the plugin's owned-file selector plus `git diff --check`; it will not bulk-reformat
  unrelated files.

## Exact next action

Publish the exact M2 typed response-envelope clarification, then add its typed Gap/Failed and
no-retry RED cases before extending the Runner.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm branch base is `d3f7d41` and FlowInterpretation design still defines the same M1–M3 contract.
