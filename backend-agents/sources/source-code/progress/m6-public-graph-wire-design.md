# Progress: M6 public ProgramGraph wire design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Close the existing M6 public five-graph, graph-index, graph-gap, typed input, and typed reference wire ambiguity without changing ProgramGraphs architecture or output cardinality.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, both `docs/plans/*.md`, and read-only inspection of the in-progress M1--M6 implementation/tests.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`.

## Completed

- Read the scoped rules, authoritative design, detailed ProgramGraphs design, and both approved implementation plans.
- Confirmed the seven semantic filenames, five graph kinds, artifact types, publisher/store responsibilities, and no-detached-input constraint are already frozen.
- Confirmed the unresolved contract is limited to public graph/index/gap record fields, exact catalog and Gap conservation, canonical identity/order/status, and the five-reopened-input/reference seam.
- Added the exact M6 public wire for four v1 program graphs, the v2 evidence graph, v1 graph index, and v1 graph-gap JSONL without changing the seven-payload/eight-reader-visible-file architecture.
- Froze resolved `evidenceNodeIds`, catalog/coverage/status equations, lossless M4 and bounded-scope Gap projection, identity/order, failure behavior, and mutation-test guidance.
- Added both required upstream `AnalysisStepPublicationReference`s to the documented `ProgramGraphsPublicationInputs` seam and froze the guarded `ProgramGraphsReference` return contract.
- Corrected only stale maturity claims: M4/M5 bounded persistence work is in progress, while M6 public publication is not yet claimed GREEN.

## Current state

- Documentation correction is complete and ready for the parent task's docs-only commit. No Java, test, POM, commit, or push was performed by this task.

## Changed files

- `progress/m6-public-graph-wire-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | OBSERVED | Shared worktree contains unrelated in-progress M1--M6 Java/test/progress changes; this task will preserve them. |
| scoped docs-check discovery with `rg` | PASS | No repository Markdown/link/stale-name check command was discoverable. |
| `git diff --check` | PASS | No whitespace errors. |
| scoped `git status --short` | PASS | Only the intended design document and this progress file are task-owned changes. |

## Decisions

- Keep M6 as a deep publication seam: callers provide exactly five sealed fresh-reopened aggregates, and receive only the typed analysis-step publication reference.
- Define public graph evidence links as a deterministic projection of M5 support edges; M6 may not copy draft provenance IDs or synthesize evidence.
- Preserve the authoritative public schema versions from `docs/DESIGN.md`: v1 for code/call/control/data and index/gap, v2 only for evidence.
- Require M6 to fail rather than fabricate rich M1--M3 local Gap detail; graph-wide bounded-scope rows are derived only from the freshly reopened VerifiedSourceInventory contract, and M4 rows are copied one-to-one from persisted `gapDrafts`.

## Blockers

- None.

## Exact next action

- Parent task may review and make the required docs-only main commit before continuing M6 production code.

## Resume checks

- Re-read this file and inspect only the two task-owned documentation paths; rerun `git diff --check` after any integration edit.
