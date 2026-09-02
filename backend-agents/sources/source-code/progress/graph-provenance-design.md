# Progress: program graph provenance design

- Status: COMPLETE
- Agent role: Sol/ultra design authority for an in-step schema correction
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: ProgramGraphs M1–M5 provenance handoff only; no production code, tests, or artifact-count change
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, both implementation plans, user-approved shard rules
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` at `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Confirmed the approved M1–M5 architecture requires evidence builders to recover exact source spans without reparsing semantic structure.
- Found that the M1 draft table exposed only opaque `evidenceDraftRefs`, while no persisted provenance-draft table gave M5 their locators, file digest, excerpt digest, and rule identity.

## Current state

- The approved correction is an in-step `provenanceDrafts[]` registry carried inside every graph draft. It preserves the existing six-module and eight-output architecture while making every evidence draft ID dereferenceable and re-verifiable.

## Changed files

- `progress/graph-provenance-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Design read and cross-reference audit | PASS | M5 requires per-element provenance, but the M1 module schema had no persisted mapping from an evidence draft ID to a verified source span. |
| Architecture-change guard | BLOCKED | The tool correctly requires explicit user approval before changing the cross-module schema/version and provenance registry. |
| `git diff --check` and stale-schema scan | PASS | No formatting errors; all M1–M4 draft references are v2 and the required provenance/Configuration registry entries are present. |

## Decisions

- A provenance draft stores only path-free source identity and source coordinates/digests; it does not carry raw source bytes, business conclusions, or Proof.
- EvidenceGraphBuilder will fresh-reopen the verified source bytes, validate full-file and excerpt SHA-256 values, then create the final evidence node. It does not use a string search fallback.

## Blockers

- None.

## Exact next action

- Commit and push the docs-only correction to `main`, then rebase the active M1 implementation branch and restart its RED tests against v2.

## Resume checks

- Read this file, check `git status --short`, verify this dedicated docs branch, and ensure no production code is modified here.
