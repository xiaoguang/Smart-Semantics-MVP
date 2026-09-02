# Progress: M4 data-flow types design

- Status: COMPLETE
- Agent role: Sol/ultra M4 design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02T19:22:25Z
- Last updated: 2026-09-02T19:46:22Z
- Scope: Define the smallest exact M4 Java/wire, reopened-input, worklist-accounting, closure, and fail-closed contract needed for the first adjacent `ARGUMENT_TO_PARAMETER` edge.
- Approved inputs: Parent-agent request; repository `AGENTS.md`; only `docs/DESIGN.md` and `docs/analysis-steps/03-program-graphs.md` as design evidence.
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` in `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Read the scoped repository instructions and created this progress record before auditing target design.
- Classified the request as a bounded deep-module design correction with an already-approved publication gate.
- Audited only `docs/DESIGN.md` and `docs/analysis-steps/03-program-graphs.md`; the stable eight-step architecture already owns the required module and publication boundary.
- Received parent approval for the proposed single-seam M4 contract before editing target design.
- Published the exact M3 fresh-reopen aggregate, M4 Java/wire records, explicit-actual worklist denominator, adjacent `ARGUMENT_TO_PARAMETER` projection, local typed Gap registry, direct predecessor validation, and fail-closed rules in Step03.
- Reconciled final review findings: M1 parameter identity is enforceable without a new field; edge ownership is derived from its argument endpoint; work-item identity precedes support; expression canonical values remain semantic; M4 Gap projection cannot leak draft provenance IDs; `gapDrafts` remains M4-only and does not retrofit M1–M3/M5 v2 wires.

## Current state

- The bounded contract edit is complete and ready for docs-only publication. No production source, test, POM, artifact count, or architecture branch changed.

## Changed files

- `progress/data-flow-types-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Worktree was clean before this progress file was created. |
| `git diff --check` | PASS | No whitespace errors. |
| `git diff -- docs/DESIGN.md` | PASS | Empty; stable architecture unchanged. |
| Focused contract scan | PASS | Only M4 has `gapDrafts`; no identity-encoded argument canonical value, edge ownership field, or Gap provenance-draft reference remains. |

## Decisions

- Preserve the existing five-graph architecture and M4 module/artifact count.
- Keep `docs/DESIGN.md` unchanged because this is a local Step03 interface completion, not a stable architecture change.
- Design only the first exact adjacent argument-to-parameter relation; broader fixed-point transfer kinds remain closed enum values with explicit scope Gaps, not a parallel seam.
- Define work items from activated M2 call edge plus explicit actual ordinal before formal support; unsupported implicit-only call shapes use separate local Gap candidates.
- Treat `GraphGapDraft` as an M4-local wire record and project it one-to-one at M6 without detached Gap inputs or dangling provenance draft IDs.

## Blockers

- None.

## Exact next action

- Commit the two docs-only files and fast-forward push the commit to `origin/main`; if network DNS remains unavailable, report that publication blocker with the local commit SHA.

## Resume checks

- Re-read this file, run `git status --short`, and inspect only the two approved design documents before continuing.
