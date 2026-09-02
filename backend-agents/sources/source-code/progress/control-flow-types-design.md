# Progress: Control-flow types design

- Status: COMPLETE
- Agent role: Sol/ultra M3 design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02T16:54:27Z
- Last updated: 2026-09-02T17:09:12Z
- Scope: Define the minimal public typed M3 JSON/Java record contract and strict entry/if/return/throw behavior in the M3 section of `docs/analysis-steps/03-program-graphs.md` only.
- Approved inputs: Parent-agent task; repository `AGENTS.md`; M3 design section and its directly governing M1/M2 reopen contract.
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` in `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Read repository instructions, the M3 section, and the directly governing M1/M2 fresh-reopen contract.
- Confirmed the worktree was clean before edits.
- Added the exact M3 public records, closed enums, canonical traversal, closure rules, and basic Java control semantics to the M3 subsection.
- Applied the focused contract review: made Java nullability legal, defined M2 call-pair projection and continuation traversal, closed ownership/reachability, and tied profile stops deterministically to coverage dispositions.

## Current state

- The bounded M3 design contract and review corrections are complete; no code, tests, POM, M4 text, architecture, or artifact count changed.

## Changed files

- `progress/control-flow-types-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean before edits. |
| `git diff --check` | PASS | No whitespace errors. |
| M3 contract/scope assertion script | PASS | 18 required contract points present; balanced fences; only the M3 design and this progress file changed. |

## Decisions

- Keep the public seam input-only and immutable; no raw drafts, detached lists, paths, or free source strings.
- Express strict basic behavior for entry, `if`, `return`, and `throw`; bounded loops and richer exception semantics remain explicit Gaps or later design authority work.
- Keep `ControlFlowGraphProfile` minimal: it carries only the verified content-addressed profile reference; rules and budgets remain behind that reference.
- Represent multi-entry `semanticTraversalOrder` as sorted `ControlFlowTraversal(entryId,nodeIds)` records, with canonical DFS order inside each entry.

## Blockers

- None.

## Exact next action

- Parent agent may consume the local docs-only commit; do not push from this task.

## Resume checks

- Re-read this file, run `git status --short`, and inspect the M3-only diff before continuing.
