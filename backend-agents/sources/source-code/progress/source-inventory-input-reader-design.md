# Progress: source-inventory-input-reader-design

- Status: COMPLETE
- Agent role: source-inventory design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Record the minimal path-free composition seam that lets M3 fresh-reopen the two already content-addressed input documents required by the approved source-inventory design. This is not runtime crash recovery, a new public product API, or a change to the eight-step flow.
- Approved inputs: `docs/DESIGN.md` §13.3.1 and `docs/analysis-steps/01-verified-source-inventory.md` §8.1 M3.
- Current branch/worktree: `codex/source-analysis-verified-inventory` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Confirmed the design already requires M3 to fresh-reopen the exact `analysis-run-request-v2` and `frozen-repository-request-v2` bytes, while the current shared stores only reopen module and analysis-step publications.

## Current state

- Added the smallest composition-only reader. It accepts only an `ArtifactReference`, returns only immutable bytes after identity verification, and exposes no path or discovery operation; M3 performs its own schema and closure validation.

## Changed files

- `progress/source-inventory-input-reader-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| design cross-check | PASS | The reader carries the existing M3 prerequisite; it does not add a new analysis step, output, lifecycle state, or recovery behavior. |

## Decisions

- The reader is a composition dependency, not a fourth canonical publication store and not a caller-facing repository-path API.

## Blockers

- None.

## Exact next action

- The design clarification was published as `497950d`; the matching M3 implementation is ready for its implementation commit.

## Resume checks

- Confirm the documentation commit is present on `origin/main` before modifying the M3 behavior implementation.
