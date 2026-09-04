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
has explicitly approved the matching v2 public-wire repair. The repair now passes its direct M2/M3
tests: each public Capsule embeds exactly its own complete spans and obligations, and the public
schema allowlist plus fixture policy were advanced to v2. The next action is to commit/push that
completed Stage05 repair without the still-RED Stage06 R0 test. Before the R0 compiler GREEN, a
detail-design correction defines its typed task profile as a frozen analysis-run-request projection,
rather than an arbitrary caller configuration.

## Changed files

- `progress/flow-interpretation-registry-proposals.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | New worktree began clean at `d3f7d41`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalTaskCompilerTest test` | EXPECTED RED | 1 test; failure `REGISTRY_PROPOSAL_TASK_COMPILER_NOT_IMPLEMENTED`; no compile/test error. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowsPublicationSpecifierTest test` | EXPECTED RED | 3 tests; one failure because public Capsule schema is v1 rather than required v2; no errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowsPublicationSpecifierTest test` | UNEXPECTED RED | 3 tests; all fail `FLOW_PUBLICATION_SPECIFIER_FAILED` caused by `FLOW_ACCOUNTING_INVARIANT_BROKEN` after the first v2 implementation. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceCapsuleProjectorTest` | PASS | 3 tests; added same-Flow span/obligation ownership and closure assertion passes. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceCapsuleProjectorTest,BusinessFlowsPublicationSpecifierTest test` | PASS | 6 tests; v2 public Capsule handoff, policy allowlist, and Stage05 publication all pass. |

## Decisions

- M1–M3 is a bounded, separately reviewable delivery. It freezes the R0 denominator and registry before any R1/R2 code is introduced.
- Automated tests use a recording scripted Provider only; no real Provider, API key, customer Maven, or source capture is in scope.
- Do not make FlowInterpretation read a Stage05 private module artifact, re-open source bytes, or use a partial Capsule view. The smallest correct repair is to enrich the existing public `evidence-capsules.jsonl` item with the complete span and projection-obligation values, then advance its schema/version and update the Stage05/06 public contract together.
- The initial post-v2 failure was not cross-Flow evidence sharing. A direct M2 ownership test proved every span and obligation has one Capsule owner. The actual cause was the generic artifact contract and fixture policy still allowlisting v1; both now allowlist only v2.
- M1 receives `RegistryProposalTaskProfile` only as a package-level, immutable projection of a
  previously verified `analysis-run-request-v2`. Its prompt/schema/runtime digests must match the
  persisted BusinessFlows controls; a future adapter cannot bypass request admission by constructing
  the profile itself.

## Blockers

- None. The user explicitly approved the published Capsule v2 public-wire repair.

## Exact next action

Publish the narrow Stage06 profile-contract clarification, then implement the existing R0 compiler
RED using only fresh-reopened BusinessFlows v2 public artifacts.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm branch base is `d3f7d41` and FlowInterpretation design still defines the same M1–M3 contract.
