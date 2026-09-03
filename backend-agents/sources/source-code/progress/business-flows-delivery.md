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

The read-only seam audit found no new cross-step field requirement. It found two stale overall-maturity rows, which were published to `origin/main` as `2001e7f`, and one Java-grammar error in the detailed implementation guidance: a hyphenated persistent module key had been presented as a Java source package. The correction keeps the wire keys `flow-compiler` and `capsule-projector` unchanged, while assigning their Java implementation packages to `analysis.flow.compiler` and `analysis.flow.capsule`. This docs-only correction is ready for the required pre-RED publication.

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

## Decisions

- Flow compilation may only consume fresh-reopened upstream artifacts; it may not reparse source, infer external effects, or create a per-Flow Markdown file.
- A boundary invocation remains a Java fact with an external-effect Gap; the capsule must preserve that Gap for downstream explanation.
- The overall maturity audit must report the delivered M1–M4 discovery and M1–M3 fact vertical slices truthfully, while retaining their full-repository limitations.
- A persistent module key and a Java package name are separate namespaces: `flow-compiler` / `capsule-projector` remain fixed wire keys, while implementation uses `.flow.compiler` / `.flow.capsule`.

## Blockers

- None. The required docs-only package-name correction is being published before the first RED test.

## Exact next action

Commit and push the docs-only package-name correction, then inspect persisted upstream record readers and fixture payloads before creating the first narrow `EntryRootedFlowCompilerTest` RED.

## Resume checks

- Re-read this file and `git status --short`.
- Reopen the current branch at `133ded3` or its direct descendant.
- Confirm `docs/analysis-steps/05-business-flows.md` still defines the same M1/M2/M3 contracts before editing code.
