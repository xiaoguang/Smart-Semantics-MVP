# Progress: M4--M6 current implementation audit synchronization

- Status: COMPLETE
- Agent role: Sol/ultra design audit
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Synchronize only the verified current-implementation audit and ProgramGraphs backlog for completed M4--M6 slices; do not change target contracts, schemas, quantities, production code, tests, POM, commit, or push state.
- Approved inputs: Parent-scoped audit brief; scoped `AGENTS.md`; ProgramGraphs current audit; backlog P5--P7; named completed M4--M6 progress records.
- Current branch/worktree: `codex/source-analysis-program-graphs`; `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped instructions, the ProgramGraphs current implementation audit, backlog P5--P7, and the named completed progress records.
- Confirmed the worktree contains extensive unrelated ProgramGraphs implementation work and will preserve it unchanged.
- Updated only the current implementation audit and corresponding backlog facts for the verified M4 generic-boundary/return behavior, M5 boundary provenance closure, and M6 public version bundle.
- Preserved every named open capability: M2 `CALL_TARGET_AMBIGUOUS`, general CFG/data-flow and multi-entry/full-repository coverage, the complete M5 budget/mutation matrix, the complete M6 publication matrix, and the product execution entry.
- Reworded 57/57 only as the historical bounded baseline; newer direct selectors remain separately evidenced by their completed progress records rather than an invented aggregate count.

## Current state

- Before this synchronization, the current audit predated verified M4 generic Java-boundary, M5 boundary-provenance, and M6 version-wire slices and still described those features as unimplemented or only in older general terms.
- The target design and completion criteria remain unchanged. Full M2 call-target ambiguity, general CFG/multi-entry/full-repository coverage, complete M5 budget/mutation coverage, and complete M6 mutation/execution coverage remain open.
- The audit now reflects the verified bounded slices without promoting ProgramGraphs beyond `PARTIAL` or claiming complete jshERP/repository coverage.

## Changed files

- `progress/m4-m6-current-audit-sync.md` (this file)
- `docs/analysis-steps/03-program-graphs.md` (section 9 current implementation audit only)
- `docs/supplements/program-graphs-implementation-backlog.md` (current-state text in the introduction and P5--P7 only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Local Markdown link validator over the three owned files | PASS | `LOCAL_MARKDOWN_LINKS_OK`; every local relative link resolves. |
| Targeted terminology and obsolete-wording grep | PASS | Verified bounded/current wording and all required open gaps are present; obsolete claims that generic boundary/unknown return/v3 remain wholly unimplemented are absent. |
| Exact documentation diff inspection | PASS | Only current implementation audit/backlog facts plus this progress record changed in this work unit; no target contract, schema, quantity, production, test, or POM edit. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Report each verified bounded behavior as current implementation only; never promote fixture-level GREEN evidence to complete ProgramGraphs or jshERP acceptance.
- Preserve the historical 57/57 baseline as a bounded baseline and point to newer progress records for additional direct selectors instead of inventing a new aggregate count.

## Blockers

- None.

## Exact next action

- Return the completed documentation-only audit synchronization to the parent. Do not commit or push from this task.

## Resume checks

- Re-read this file, inspect the exact documentation diff, and ensure no production, test, POM, schema, contract, count, commit, or push changes enter this audit.
