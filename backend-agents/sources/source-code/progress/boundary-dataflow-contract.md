# Progress: boundary data-flow contract

- Status: COMPLETE
- Agent role: Sol/ultra documentation design authority for the M4/M5 Java boundary contract
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03T00:15:49-02:30
- Last updated: 2026-09-03T00:46:44-02:30
- Scope: Docs-only refinement of M4 DataFlow and M5 Evidence semantics at calls leaving frozen Java code; carry only the necessary effects through M6 and analysis steps 04-08; update the P5 backlog. No Java, tests, POM, commit, or push.
- Approved inputs: Parent task conveying explicit user approval; frozen five-graph, M1-M3, eight-stage, artifact-count, and wire-name contracts; generic JavaBoundaryInvocation with conservative unknown-return and Gap semantics.
- Current branch/worktree: `codex/source-analysis-program-graphs` in `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the applicable root `AGENTS.md` and `progress/TEMPLATE.md`.
- Captured the pre-existing dirty worktree before any durable-document edit.
- Read `docs/DESIGN.md`, analysis steps 03 through 08, and the ProgramGraphs implementation backlog in full.
- Published the approved generic frozen-Java boundary contract, exact M4 record/identity/edge changes, M5 evidence constraints, M6 schema migration/rejection gate, downstream invocation-only wording, and revised P5.

## Current state

The approved contract is published and the scoped documentation validation is complete. No Java, tests, POM, commit, or push was performed.

## Changed files

- `progress/boundary-dataflow-contract.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/supplements/program-graphs-implementation-backlog.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing program-graph code/docs/progress changes recorded; this task will preserve unrelated work. |
| `rg --files -g 'AGENTS.md'` | PASS | Exactly one applicable `AGENTS.md` at this worktree root. |
| `git diff --check -- <seven tracked design docs>` | PASS | No whitespace errors. |
| `rg -n '[[:blank:]]+$' <nine task files>` | PASS | No trailing whitespace matches. |
| local Markdown link validator over `<nine task files>` | PASS | `validated local Markdown links in 9 files`. |
| fenced-block delimiter validator over `<nine task files>` | PASS | `balanced fenced-block delimiters in 9 files`. |
| required contract/version `rg` | PASS | Exact boundary/return records, three boundary edges, v3/v2 versions, and Luna RED/Terra GREEN instructions are present. |
| legacy-version gate and forbidden technology-specific semantics `rg` | PASS | All five exact old schema versions appear only in rejection requirements; no old Mapper-only candidate key or removed placeholder/column edge kinds remain. |
| scoped external-effect contradiction `rg` and context review | PASS | Matches are explicit prohibitions or Gap requirements; no positive external-effect Fact remains. |
| five-graph/seven-semantic/eight-file invariant `rg` and table review | PASS | Graph kinds, filenames, artifact counts, wire names, and eight-stage shape remain unchanged. |
| `git diff --stat -- <seven tracked design docs>` | PASS | 7 tracked docs changed; the new backlog and this progress ledger are untracked task files. |

No test command was run: this is a docs-only design task, and the user explicitly excluded Java/test/POM work.

## Decisions

- Treat the user-supplied boundary semantics as the approved architectural design; do not broaden it with technology-specific adapters or effects.
- Use `JavaBoundaryInvocation` despite the general codebase-design preference for “seam,” because this exact record name is a user-approved public contract.

## Blockers

- None.

## Exact next action

None. Hand the exact changed paths and validation evidence back to the parent agent.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm no edit outside this worktree.
- Confirm the five graphs, M1-M3, eight stages, artifact counts, and wire names remain untouched.
