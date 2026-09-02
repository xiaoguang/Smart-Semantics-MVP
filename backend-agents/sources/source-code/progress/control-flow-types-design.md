# Progress: Control-flow types design

- Status: COMPLETE
- Agent role: Sol/ultra M3 design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02T16:54:27Z
- Last updated: 2026-09-02T19:18:00Z
- Scope: Define and audit the minimal public typed M3 contract, including direct-throw callee semantics, in the M3 section of `docs/analysis-steps/03-program-graphs.md` only.
- Approved inputs: Parent-agent tasks; repository `AGENTS.md`; M3 design section and its directly governing M1/M2 reopen contract.
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` in `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Read repository instructions, the M3 section, and the directly governing M1/M2 fresh-reopen contract.
- Confirmed the worktree was clean before edits.
- Added the exact M3 public records, closed enums, canonical traversal, closure rules, and basic Java control semantics to the M3 subsection.
- Applied the focused contract review: made Java nullability legal, defined M2 call-pair projection and continuation traversal, closed ownership/reachability, and tied profile stops deterministically to coverage dispositions.
- Audited and encoded the direct-throw callee case: an activated M2 `CALL_RETURN` remains an exact structural frame link, while M3 continuation exists and is activated only for an exact normal callee exit.
- Restricted projection completeness to M3-activated call anchors, while requiring unreachable M2 call-pair candidates to close through exclusions or Gaps.
- Made direct-throw semantics path-local so an independently reachable lexical successor remains in the graph.

## Current state

- The approved structural `RETURN` versus executable continuation correction and the matching M3 maturity gate are complete. No public wire fields, artifact count, source, tests, or POM changed.

## Changed files

- `progress/control-flow-types-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean before edits. |
| `git diff --check` | PASS | No whitespace errors. |
| M3 contract/scope assertion script | PASS | 18 required contract points present; balanced fences; only the M3 design and this progress file changed. |
| Direct-throw contract audit | PASS | Structural M2 return pairing and path-sensitive M3 continuation can be represented with the existing fields. |
| Focused contract review | PASS after correction | Projection denominator is activated-anchor-only; direct throw is call-site/path-local and preserves independently reachable successors. |

## Decisions

- Keep the public seam input-only and immutable; no raw drafts, detached lists, paths, or free source strings.
- Express strict basic behavior for entry, `if`, `return`, and `throw`; bounded loops and richer exception semantics remain explicit Gaps or later design authority work.
- Keep `ControlFlowGraphProfile` minimal: it carries only the verified content-addressed profile reference; rules and budgets remain behind that reference.
- Represent multi-entry `semanticTraversalOrder` as sorted `ControlFlowTraversal(entryId,nodeIds,edgeIds)` records, with canonical walker order inside each entry.
- Follow-up authority requires enforceable input construction, explicit node-and-edge traversal closure, distinct entry/callee return terminals, and a typed terminal disposition mapping.
- `RETURN` is an unconditional structural frame link for every activated M2 call pair, never a standalone successor. A call-site continuation `NEXT` exists only when at least one exact normal callee exit or normal leaf can activate it.
- A direct-throw-only call anchor has no continuation and contributes no path to its successor. The successor is omitted and excluded only without another reachable predecessor; an independently reachable successor remains. Unsupported/profile-stop uncertainty becomes a Gap.

## Blockers

- None.

## Exact next action

- Parent agent may consume the local docs-only commit; do not push from this worktree.

## Resume checks

- Re-read this file, run `git status --short`, and inspect the M3-only diff before continuing.
