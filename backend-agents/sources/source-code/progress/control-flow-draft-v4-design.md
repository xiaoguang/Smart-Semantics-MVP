# Progress: control-flow-draft-v4-design

- Status: COMPLETE
- Agent role: Design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Publish the narrow schema correction required after exposing a guard's normalized condition in the control-flow draft.
- Approved inputs: User-confirmed `JAVA_GUARD_CONDITION` design; existing `docs/DESIGN.md` and analysis-step contracts.
- Current branch/worktree: `codex/source-analysis-control-flow-draft-v4-design` in `/private/tmp/linguan-source-analysis-control-flow-draft-v4-design`

## Completed

- Identified that adding the required-nullable field to the internal control-flow draft changes its canonical payload and must not be silently published as v3.
- Specified `program-graphs-control-flow-draft-v4`; the public reader-visible control-flow graph remains independently versioned at v2.

## Current state

The target design has been checked for stale v3 contract references and is ready for a docs-only commit and push before implementation resumes.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `progress/control-flow-draft-v4-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n --glob '!history/**' 'program-graphs-control-flow-draft-v3|control-flow-draft-v3|M3 v3' docs` | PASS | No active target design retains the old v3 draft contract. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- No legacy v3 reader, alias, or converter is permitted: a payload field changes canonical identity.
- The draft v4/public v2 distinction is intentional: the public graph was already versioned separately when it gained the reader-visible field.

## Blockers

None.

## Exact next action

Commit this docs-only correction and push it to `origin/main`; then the control-flow implementation may adopt draft v4.

## Resume checks

- Confirm the docs-only worktree is clean except for the two paths listed above.
- Confirm `origin/main` advances before applying the corresponding code schema update.
