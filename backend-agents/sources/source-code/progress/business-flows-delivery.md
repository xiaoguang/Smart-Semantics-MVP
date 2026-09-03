# Progress: Business flows delivery

- Status: IN_PROGRESS
- Agent role: Sol/ultra design authority and delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the semantic `business-flows` analysis step only: entry-rooted Flow compilation, evidence-capsule projection, and five public payloads plus receipt.
- Approved inputs: Published `docs/DESIGN.md`; `docs/analysis-steps/05-business-flows.md`; both published implementation plans; persisted ApplicationDiscovery, ProgramGraphs, and ProvenCodeFacts artifacts; frozen test fixtures only.
- Current branch/worktree: `codex/source-analysis-business-flows` at `/private/tmp/linguan-source-analysis-business-flows/backend-agents/sources/source-code`

## Completed

- Read the root and scoped Agent rules, the complete target design, the complete BusinessFlows detailed design, and both implementation plans.
- Confirmed the worktree starts clean at `133ded3`, the published ProvenCodeFacts delivery commit.
- Confirmed that the target Flow package contains only a package skeleton and no active Flow tests or production implementation.

## Current state

The read-only seam audit confirmed that the persisted upstream fields can support M1 without a new cross-step artifact. It also found a local M1 interface conflict: the compiler was specified to return the M3-only `BusinessFlowsReference`. The detailed design now correctly specifies an M1 `FlowCompilation`, a content-addressed `FlowCompilationProfile`, and M3 as the sole owner of `BusinessFlowsReference`. This preserves the eight-step data flow and output count, but needs the required docs-only publication before the first RED.

## Changed files

- `progress/business-flows-delivery.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/05-business-flows.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean worktree before this progress file was created. |
| `wc -l` and bounded `sed` reads of required design files | PASS | Read all required target and BusinessFlows contracts before implementation. |
| `rg --files src/main/java src/test/java` | PASS | Existing upstream artifacts/readers are present; `analysis.flow` is still only a skeleton. |
| Current-audit cross-check against analysis-step 02–04 documents and published code | PASS | Corrected only stale maturity descriptions; no target architecture, artifact count, or interface changed. |
| `git push origin HEAD:main` | PASS | Published docs-only commit `2001e7f`; remote `main` equals this commit. |
| Target package-name audit | PASS | Persistent module keys retain hyphens by contract; Java packages now use legal semantic names without changing any wire field. |
| `git push origin HEAD:main` | PASS | Published docs-only commit `ef43824`; remote `main` includes the legal Java-package clarification. |
| M1 interface and artifact audit | PASS | Repaired local M1/M3 return-type conflict and made profile/budget identity explicit; no upstream payload, final public API, or output-count change. |

## Decisions

- Flow compilation may only consume fresh-reopened upstream artifacts; it may not reparse source, infer external effects, or create a per-Flow Markdown file.
- A boundary invocation remains a Java fact with an external-effect Gap; the capsule must preserve that Gap for downstream explanation.
- The overall maturity audit must report the delivered M1–M4 discovery and M1–M3 fact vertical slices truthfully, while retaining their full-repository limitations.
- A persistent module key and a Java package name are separate namespaces: `flow-compiler` / `capsule-projector` remain fixed wire keys, while implementation uses `.flow.compiler` / `.flow.capsule`.
- M1 returns an immutable `FlowCompilation`; M2 reopens its persisted artifact; only M3 returns `BusinessFlowsReference`. Profile and budget are deterministic content-addressed M1 input, not ambient process configuration.

## Blockers

- None. The required docs-only M1 contract correction is being published before the first RED test.

## Exact next action

Commit and push the M1 contract correction, then create the first narrow `EntryRootedFlowCompilerTest` RED from the audited persisted upstream fixture.

## Resume checks

- Re-read this file and `git status --short`.
- Reopen the current branch at `133ded3` or its direct descendant.
- Confirm `docs/analysis-steps/05-business-flows.md` still defines the same M1/M2/M3 contracts before editing code.
