# Progress: Step05 domain-specific exit design

- Status: COMPLETE
- Agent role: Sole design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Docs-only Step05 acceptance narrowing inside `backend-agents/sources/source-code/`; no code, tests, fixtures, schemas, POM, capture, generation, customer execution, or network activity.
- Approved inputs: The user approved an independent offline complete Git object copy at fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580` for later Step05 acceptance, leaving the original partial/promisor repository unmodified; the user also confirmed that no business-table mapping is supplied, so complete Step05 exit must not require `DOMAIN_SPECIFIC` classification.
- Current branch/worktree: `codex/source-analysis-step05-domain-exit` at `/private/tmp/source-analysis-step05-domain-exit`

## Completed

- Read the repository, backend-agent, and source-code `AGENTS.md` files.
- Confirmed a clean scoped worktree before edits.
- Audited the active architecture, Step04/05/06 detailed contracts, README, source-scoped instructions, and current production/test class inventory.
- Confirmed `origin/main` is `5b0f7e4` and current production still has the documented v2 ProvenCodeFacts, signal-free BusinessFlows M1–M3, and local-only FlowInterpretation M1–M5 slices; no current jshERP run or independent complete object copy was observed or executed.
- Published the two user-approved boundaries consistently in the authoritative architecture, Step04/05/06 contracts, README, and source-scoped instructions.
- Removed `DOMAIN_SPECIFIC` as a current Step05 exit prerequisite while preserving it and `SHARED_ANCHOR` as optional future stronger evidence under an explicit classification authority.
- Preserved the eight analysis steps, fixed nine H2 reader sections, 57 reader-visible outputs, public API, and strict Proof/Evidence/Trace trust boundary.

## Current state

- The docs-only design patch is complete and locally verified.
- The approved decisions close design questions only. No independent copy was created, no fixed-repository acceptance ran, and no production capability or maturity was claimed.
- Generative-content inventory: none. This task invokes no model subprocess, source capture, candidate generation, network source, customer code, or external write.

## Changed files

- `progress/step05-domain-exit-design.md`
- `AGENTS.md`
- `README.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean before this task-owned progress file was created. |
| `rg -n 'domain分类缺口只阻断\|完整domain acceptance\|尚未解决的 domain-specific 验收门\|仍须由Sol/ultra冻结一个Proof闭合的有限分类来源' docs/DESIGN.md docs/analysis-steps/04-proven-code-facts.md docs/analysis-steps/05-business-flows.md docs/analysis-steps/06-flow-interpretation.md README.md AGENTS.md` | PASS | Exit 1 with no matches: all named obsolete blocker phrases are absent. |
| `rg -n '完整Step 05出口\|完整Step05验收\|generic/pending足以\|独立.*完整.*非promisor\|原partial/promisor.*不变\|不要求出现.DOMAIN_SPECIFIC\|不要求.DOMAIN_SPECIFIC' docs/DESIGN.md docs/analysis-steps/04-proven-code-facts.md docs/analysis-steps/05-business-flows.md docs/analysis-steps/06-flow-interpretation.md README.md AGENTS.md` | PASS | Exit 0; the approved exit, generic/pending, independent-copy, untouched-original, and not-yet-run boundaries are present. |
| `perl -MFile::Basename=dirname -MFile::Spec -e '<scan Markdown links and fail unless each local target exists>' README.md docs/DESIGN.md docs/analysis-steps/04-proven-code-facts.md docs/analysis-steps/05-business-flows.md docs/analysis-steps/06-flow-interpretation.md` | PASS | Exit 0: `all local Markdown targets exist`. |
| `rg --files docs/analysis-steps \| rg '/[0-9]{2}-[^/]+\\.md$' \| sort` and `sed -n '/^## 14\\. 固定九章合同/,/^## 15\\./p' docs/DESIGN.md \| rg '^[1-9]\\. '` | PASS | Listed exactly eight numbered analysis-step docs and the nine fixed reader section names. |
| `rg -n 'reader-visible正式输出总数固定为\\*\\*57\\*\\*\|全run.*57\|57 reader-visible' docs/DESIGN.md README.md AGENTS.md` | PASS | Exit 0; the 57-output contract remains present. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- A later Step05 acceptance may use an independent offline complete Git object copy at the already-approved fixed jshERP commit; it must not mutate the original partial/promisor repository and must make no network or customer execution.
- With no user-supplied business-table mapping, exact static Java-to-Mapper-to-XML-to-SQL references remain generic/pending structural material. They do not prove business object, sequence, causality, or external effect and cannot form `SHARED_ANCHOR`.
- Complete Step05 exit still requires every entry to be classified as `COMPILED`, `GAP`, or `EXCLUDED`; it does not require `DOMAIN_SPECIFIC` classification.
- Step06 alone may interpret business meaning, under its frozen R0/P1/P2 safeguards.

## Blockers

- None.

## Exact next action

- Hand the verified docs-only patch back to the parent task. Do not commit, push, create a PR, run capture, or execute customer code in this task.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm all edits remain under `backend-agents/sources/source-code/`.
- Preserve eight steps, nine fixed H2 reader sections, 57 reader-visible outputs, the public API, and strict Proof/Evidence/Trace trust.
- Re-run `git diff --check` and the scoped Markdown/link/terminology scans if any later edit touches these files.
