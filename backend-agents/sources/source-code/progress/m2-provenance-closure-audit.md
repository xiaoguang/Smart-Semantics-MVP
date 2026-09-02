# Progress: M2 provenance closure audit

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Document the observed M2 call-graph provenance-registry closure gap and required remediation in the current implementation audit only.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, current M2 implementation fact supplied by the root Agent, scoped `AGENTS.md`
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` / `/private/tmp/linguan-source-analysis-graph-provenance-design`

## Completed

- Read the scoped Agent rules and the existing Program Graphs target design/current implementation audit.
- Confirmed the target provenance-closure contract already exists and must not be changed.
- Recorded that current M2 emits evidence references without registering the corresponding provenance entries.
- Recorded the exact remediation gate across builder, draft, publisher, and fresh reader before M3 may trust M2.

## Current state

- The documentation-only correction is complete; no target architecture, wire, artifact count, production code, or test changed.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `progress/m2-provenance-closure-audit.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Branch is based on `origin/main`; worktree was clean before edits. |
| `git diff --check` | PASS | No whitespace errors. |
| `git diff -- docs/analysis-steps/03-program-graphs.md progress/m2-provenance-closure-audit.md` | PASS | Only the M2/M3 current implementation audit and this progress file changed. |

## Decisions

- Treat this as an implementation defect against an already correct target contract, not as a new architecture or wire change.
- Require closure remediation before M3 may trust the persisted M2 artifact.

## Blockers

- None.

## Exact next action

- Root Agent should publish this docs-only commit, then establish a Luna/xhigh RED for the M2 provenance closure defect before Terra/xhigh remediation.

## Resume checks

- Read this file, inspect `git status --short`, and confirm the target architecture sections remain unchanged.
