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

The read-only seam audit found no new cross-step field requirement, but it found two stale rows in the overall current-maturity table: ApplicationDiscovery and ProvenCodeFacts had already advanced beyond the text shown there. This documentation correction is being published before the first BusinessFlows RED. The next task remains a narrow persisted-upstream reader audit followed by the first Flow compiler test.

## Changed files

- `progress/business-flows-delivery.md`
- `docs/DESIGN.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean worktree before this progress file was created. |
| `wc -l` and bounded `sed` reads of required design files | PASS | Read all required target and BusinessFlows contracts before implementation. |
| `rg --files src/main/java src/test/java` | PASS | Existing upstream artifacts/readers are present; `analysis.flow` is still only a skeleton. |
| Current-audit cross-check against analysis-step 02–04 documents and published code | PASS | Corrected only stale maturity descriptions; no target architecture, artifact count, or interface changed. |

## Decisions

- Flow compilation may only consume fresh-reopened upstream artifacts; it may not reparse source, infer external effects, or create a per-Flow Markdown file.
- A boundary invocation remains a Java fact with an external-effect Gap; the capsule must preserve that Gap for downstream explanation.
- The overall maturity audit must report the delivered M1–M4 discovery and M1–M3 fact vertical slices truthfully, while retaining their full-repository limitations.

## Blockers

- None. A cross-step field audit is in progress before the first RED test.

## Exact next action

Publish this docs-only correction to `origin/main`; then inspect persisted upstream record readers and fixture payloads before creating the first narrow `EntryRootedFlowCompilerTest` RED.

## Resume checks

- Re-read this file and `git status --short`.
- Reopen the current branch at `133ded3` or its direct descendant.
- Confirm `docs/analysis-steps/05-business-flows.md` still defines the same M1/M2/M3 contracts before editing code.
