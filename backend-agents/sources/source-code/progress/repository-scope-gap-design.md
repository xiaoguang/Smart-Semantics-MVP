# Progress: repository-scoped program-graph Gap design correction

- Status: COMPLETE
- Agent role: Sole design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Correct the Step 03 Gap carrier and M6 publication contract only; no Java, tests, POM, or cross-step redesign.
- Approved inputs: Scoped AGENTS instructions, both implementation plans, Step 03 design, current GraphGap/coverage/publication models and tests, and the 57-test failure evidence in the task brief.
- Current branch/worktree: codex/source-analysis-program-graphs at /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Confirmed the worktree contains extensive pre-existing Step 03 production and test work that this task must not stage or alter.
- Read the repository, backend, and source-scoped AGENTS instructions and the codebase-design skill.
- Read both implementation plans, the complete relevant Step 03 wire/module/Gap/publication sections, and the current public Gap, coverage, M6 publisher/reader tests.
- Corrected the authoritative Step 03 contract without changing the eight-step workflow or any Java/schema filename.
- Added exact module ownership, identity, accounting, fresh-reopen, failure, and Luna selector requirements for local versus repository-scope gaps.

## Current state

- The docs-only correction is ready to commit. No Java, test, POM, or cross-step design file was changed by this task.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `progress/repository-scope-gap-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Branch follows `origin/main`; unrelated Step 03 WIP is preserved and will not be staged. |
| `git diff --check -- docs/analysis-steps/03-program-graphs.md progress/repository-scope-gap-design.md` | PASS | No whitespace errors. |
| Contract contradiction `rg` checks | PASS | No surviving rule projects `scopeGapIds` as GraphGap rows or requires every M1 local Gap to have an entry. |
| Maven/tests | NOT RUN | This work unit is docs-only; the recorded 57-test/53-pass evidence is an approved input, not a result produced here. |

## Decisions

- A `CODE_STRUCTURE` local Gap may have `affectedEntryIds=[]` only for the closed set of verified source-file parsing/structure reasons, with a non-empty candidate set and exact non-null locator; a proven owner must still be recorded when available.
- `CALL`, `CONTROL_FLOW`, and `DATA_FLOW` local gaps always require a non-empty subset of their graph's entry denominator.
- Repository-scope incompleteness has no source occurrence. It is represented only by canonical `scopeGapIds` in the four program-graph coverages, graph index, and module/analysis-step receipts; it never becomes a `GraphGapV1` row.
- M6 writes exactly one JSONL row per M1–M4 local carrier, so a bounded-scope run with no local gaps has an exact zero-byte `graph-gaps.jsonl`; index and receipt identities use the deduplicated union of local IDs and the one canonical scope ID.

## Blockers

- None.

## Exact next action

- Stage only the two owned Markdown files, commit the docs-only correction, and push that commit to `origin/main`.

## Resume checks

- Re-read this progress file.
- Confirm commit `docs: distinguish local and scope graph gaps` is present on `origin/main`.
- Resume implementation with the three named selectors in the authoritative design; do not repair fixtures by inventing entries or GraphGap rows.
