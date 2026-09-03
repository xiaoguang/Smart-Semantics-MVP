# Progress: Program Graph stable boundary and implementation backlog

- Status: COMPLETE
- Agent role: Sole design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02T23:47:13-02:30
- Last updated: 2026-09-02T23:53:00-02:30
- Scope: Close the current ProgramGraphs redesign cycle by recording the verified stable contract
  boundary and the remaining implementation backlog; update only the Step 03 maturity audit and
  add one linked supplement.
- Approved inputs: Repository/backend/source-scoped AGENTS instructions; `docs/DESIGN.md`;
  `docs/analysis-steps/03-program-graphs.md`; both implementation plans; current ProgramGraphs
  source, tests, and progress evidence including the exact 57-test passing selector.
- Current branch/worktree: `codex/source-analysis-program-graphs` at
  `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`.

## Completed

- Read the applicable AGENTS files, the `codebase-design` skill, both implementation plans, the
  overall design, the complete Step 03 design, and current M3 progress evidence.
- Confirmed the exact current evidence: the established M3 direct selector reports 57 tests, 0
  failures, 0 errors, and 0 skipped.
- Confirmed that passing evidence closes the bounded M1--M6 carrier/publication path, not the full
  ProgramGraphs capability or repository acceptance.
- Added one concise implementation supplement that freezes nine stable contract boundaries, lists
  eight still-unimplemented capabilities in priority order, separates permitted local changes from
  escalation triggers, and gives every future slice one acceptance checklist.
- Replaced the stale Step 03 maturity audit with the exact 57/57 evidence and current M1--M6 facts;
  ProgramGraphs remains explicitly `PARTIAL` until full data-flow, multi-entry, and complete jshERP
  offline acceptance close.

## Current state

- The target contract above the maturity audit remains authoritative. Future work starts from the
  backlog rather than deriving another eight-step workflow.
- No Java, test, POM, schema, public Interface, wire name, artifact count, Gap rule, or scope changed.

## Changed files

- `progress/program-graph-boundary-backlog-design.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/supplements/program-graphs-implementation-backlog.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Applicable rules and design reads | PASS | Root, backend, scoped AGENTS; overall/Step 03 designs; both implementation plans read. |
| New-link existence and reciprocal-link check | PASS | Both local Markdown targets exist and each document links to the other. |
| Stale/contradictory maturity scan | PASS | No `53/57`, “M6 still in progress”, or ProgramGraphs-complete claim remains in the two current documents. |
| Stable/incomplete boundary scan | PASS | Exact 57/57 evidence, `PARTIAL`, no-redesign rule, and incomplete-capability heading are all present. |
| Trailing-whitespace scan for new files | PASS | No trailing blank characters. |
| `git diff --check` | PASS | No whitespace errors in the shared worktree. |
| Maven/tests | NOT RUN | Docs-only work; 57/57 is existing directly recorded evidence, not a new test run by this task. |

## Decisions

- “Stable” means future implementation may fill registered capabilities and fix local bugs without
  changing the eight-step flow, seven ProgramGraphs semantic payloads, public wire, Gap split,
  public Interface, or cross-step postcondition.
- “Unimplemented” means no current capability claim; each item requires a fresh public-seam RED and
  the existing contract, not another end-to-end architecture derivation.

## Blockers

- None.

## Exact next action

- Parent performs final integrated review and commits these three WIP Markdown files atomically
  with the matching ProgramGraphs code changes; this task must not commit or push them independently.

## Resume checks

- Re-read this file, the Step 03 maturity audit, and the linked supplement.
- Reconfirm the 57/57 evidence in `progress/program-graph-publication-identity-green.md` before a
  later implementation claim.
- Keep ProgramGraphs `PARTIAL` until backlog P1--P8 and complete-repository acceptance close.
