# Progress: Business flows delivery

- Status: COMPLETE
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the semantic `business-flows` analysis step only: entry-rooted Flow compilation, evidence-capsule projection, and five public payloads plus receipt.
- Approved inputs: Published `docs/DESIGN.md`; `docs/analysis-steps/05-business-flows.md`; both published implementation plans; persisted ApplicationDiscovery, ProgramGraphs, and ProvenCodeFacts artifacts; frozen test fixtures only; published Step04 v2 commit `1a6b6f6`.
- Current branch/worktree: `codex/source-analysis-business-flows` at `/private/tmp/linguan-source-analysis-business-flows/backend-agents/sources/source-code`

## Completed

- M1 now fresh-reopens the v2 Proven Code Facts wire and compiles exact guard decisions.
- The control-flow traversal prefers a direct GUARD successor over an auxiliary generic NEXT
  edge from the same source block, preventing a path that would bypass the guard condition.
- M1 compiler, canonical publisher and invariant selectors pass (4 tests).
- Read the root and scoped Agent rules, the complete target design, the complete BusinessFlows detailed design, and both implementation plans.
- Confirmed the worktree starts clean at `133ded3`, the published ProvenCodeFacts delivery commit.
- Confirmed that the target Flow package contains only a package skeleton and no active Flow tests or production implementation.

## Current state

M1–M3 now form one persisted, double-entry vertical slice. M1 reopens the v2 Fact ledger and
compiles exact controller/service call-return paths and TRUE/FALSE guard outcomes. It writes every
entry as COMPILED or GAP, including a stable reason and a FlowGap record. M2 reopens M1 plus
Proof/Evidence/source artifacts, projects one evidence-complete Capsule per compiled Flow, and
turns budget overflow into an INELIGIBLE Capsule with a retained Gap. M3 reopens both modules and
publishes the five public BusinessFlows files, including model eligibility partitions and all Flow
Gaps. The remaining delivery work is bounded verification, formatting, design-audit synchronization,
and the Step05 commit; it does not include real jshERP execution or Provider calls.

## Changed files

- `progress/business-flows-delivery.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompilerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationProfile.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationReferenceException.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionProfile.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsReference.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest,FlowCompilationModulePublisherTest,FlowCompilationTest test` | PASS | 4 tests, 0 failures/errors/skips |
| `git status --short` | PASS | Clean worktree before this progress file was created. |
| `wc -l` and bounded `sed` reads of required design files | PASS | Read all required target and BusinessFlows contracts before implementation. |
| `rg --files src/main/java src/test/java` | PASS | Existing upstream artifacts/readers are present; `analysis.flow` is still only a skeleton. |
| Current-audit cross-check against analysis-step 02–04 documents and published code | PASS | Corrected only stale maturity descriptions; no target architecture, artifact count, or interface changed. |
| `git push origin HEAD:main` | PASS | Published docs-only commit `2001e7f`; remote `main` equals this commit. |
| Target package-name audit | PASS | Persistent module keys retain hyphens by contract; Java packages now use legal semantic names without changing any wire field. |
| `git push origin HEAD:main` | PASS | Published docs-only commit `ef43824`; remote `main` includes the legal Java-package clarification. |
| M1 interface and artifact audit | PASS | Repaired local M1/M3 return-type conflict and made profile/budget identity explicit; no upstream payload, final public API, or output-count change. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest test` | EXPECTED RED | 1 test, 1 assertion failure, 0 errors/skips: missing `FlowCompilationProfile` / `EntryRootedFlowCompiler`; fixture setup and public seam reached successfully. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest test` | PASS | 1 test, 0 failures/errors/skips; real persisted two-entry fixture proves one isolated Flow each with its own Fact/atom/closed Proof IDs and existing external-effect Gap ID. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=…flow/compiler/*.java spotless:check` | PASS | Checked only owned M1 production/test Java files. |
| `git diff --check` | PASS | No whitespace errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FlowCompilationModulePublisherTest test` | EXPECTED RED | 1 test reached the canonical module store and failed with `MODULE_INSTALL_REQUEST_INVALID`: the declared `BUSINESS_FLOWS_FLOW_COMPILATION` wire type lacked its existing-contract module-address allowlist entry. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FlowCompilationModulePublisherTest test` | PASS | 1 test, 0 failures/errors/skips; canonical module envelope, exact semantic address, one payload, and 13 reopened predecessors verified. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest,FlowCompilationModulePublisherTest test` | PASS | 2 tests, 0 failures/errors/skips; the compiler and its persisted M1 result agree on the same fresh-reopened public predecessor wire. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=…flow/compiler/*.java,…AtomicCanonicalPublicationEngine.java,…ProgramGraphsPublicFixture.java spotless:check` | PASS | Only owned M1 Java files and the one fixture policy registration were checked. |
| `git diff --check` | PASS | No whitespace errors after the M1 persistence implementation. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FlowCompilationTest test` | EXPECTED RED → PASS | The public value test first exposed a false order coupling as `FLOW_ACCOUNTING_INVARIANT_BROKEN`; after a one-method identity/ownership repair, 1 test passes with 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest test` | EXPECTED RED, BLOCKED | A real `if (status == null) return` fixture initially showed two paths collapsed into one. M1 now fresh-reopens the public control traversal and follows exact call/return edges, but fails closed as `FLOW_CONDITION_ATOM_UNPROVEN`: Stage04's admitted Fact atoms do not furnish a traceable guard-condition atom. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest,FlowCompilationTest,FlowCompilationModulePublisherTest,EvidenceCapsuleProjectorTest,CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 11 tests, 0 failures/errors/skips. Includes two Flow/Capsule fixtures, exact TRUE/FALSE guard outcomes, source-backed capsules, M2 budget-to-INELIGIBLE retention, and M3 eligibility/Gaps publication. |
| Targeted Spotless check for owned Step05 Java files | PASS | Spotless 3.10.1 completed without changing files. |
| `git diff --check` | PASS | No whitespace errors before final review. |

## Decisions

- Flow compilation may only consume fresh-reopened upstream artifacts; it may not reparse source, infer external effects, or create a per-Flow Markdown file.
- A boundary invocation remains a Java fact with an external-effect Gap; the capsule must preserve that Gap for downstream explanation.
- The overall maturity audit must report the delivered M1–M4 discovery and M1–M3 fact vertical slices truthfully, while retaining their full-repository limitations.
- A persistent module key and a Java package name are separate namespaces: `flow-compiler` / `capsule-projector` remain fixed wire keys, while implementation uses `.flow.compiler` / `.flow.capsule`.
- M1 returns an immutable `FlowCompilation`; M2 reopens its persisted artifact; only M3 returns `BusinessFlowsReference`. Profile and budget are deterministic content-addressed M1 input, not ambient process configuration.
- A Flow too large for the model remains a complete, persisted Flow/Capsule. It is partitioned as `INELIGIBLE` with a stable `CAPSULE_BUDGET_NO_SAFE_SPLIT` Gap, rather than being deleted or silently truncated.
- Whole-project `spotless:apply` re-formatted 40 unrelated baseline files on first cache creation. All exact, formatter-only changes were restored after review; future checks are limited with `spotlessFiles` unless a formatting-only repository work unit is approved.

## Blockers

- None. Real full-repository jshERP acceptance and any Provider invocation are deliberately outside this Step05 fixture delivery.

## Exact next action

Commit and merge this complete semantic-step delivery. The next Agent must start a fresh branch
from the merged main and implement the next planned semantic step; it must not treat this bounded
fixture vertical slice as a full-repository or real-model acceptance result.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm the rebased branch starts at published `1a6b6f6`.
- Confirm `docs/analysis-steps/05-business-flows.md` still defines the same M1/M2/M3 contracts before editing code.
