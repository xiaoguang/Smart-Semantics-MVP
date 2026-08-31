# Progress: Authoritative design document relocation

- Status: COMPLETE
- Agent role: design Agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Move the authoritative GitHub Code Agent overall design from `DESIGN.md` to `docs/DESIGN.md`, then update only current authoritative navigation/recovery references and verify that design content/contracts are unchanged.
- Approved inputs: the user's explicit relocation approval; current scoped documentation and progress template; existing repository/worktree as read-only evidence outside the explicitly scoped Markdown edits.
- Current branch/worktree: `codex/github-code-design-walkthrough` / `/Users/yexiaoguang/Documents/ErpMock`

## Completed

- Read `progress/TEMPLATE.md`, the current scoped `AGENTS.md`, and the pre-edit shared-worktree status.
- Recorded that unrelated/shared Java, test, progress, and documentation changes must be preserved.
- Inventoried every `DESIGN.md` reference inside the component and all exact GitHub Code Agent design-path references outside it.
- Confirmed the repository currently contains only one same-named `DESIGN.md`, at the GitHub Code Agent root; no other component design may be rewritten by an ambiguous filename match.
- Classified current navigation references in component README/AGENTS/Stage00, `backend-agents/README.md`, the data-standardization design, and both repository-structure docs separately from immutable historical statements in completed progress files. Three historical progress resume instructions still actively direct a future Agent to the old path and therefore require a bounded path correction.
- Safely renamed the authority from component-root `DESIGN.md` to `docs/DESIGN.md`; the pre-patch bytes were identical before and after the rename (`sha256 c296b45e4fc88e140d8f1e8225906b17d291979a5c33cf1acf8853341124c867`).
- Rebased the relocated design's relative stage/shared-contract links for its one-level-deeper location.
- Updated current authority/navigation links in the component README and scoped AGENTS, Stage00–08, `backend-agents/README.md`, the data-standardization design, and both repository-structure documents.
- Corrected only the three historical progress files whose live resume instructions would otherwise direct a future Agent to the removed path; immutable past facts and command output remain unchanged.

## Current state

- The independent progress file existed before every authoritative design/navigation edit.
- `docs/DESIGN.md` is now the sole same-named design file in the repository and component-root `DESIGN.md` is absent.
- Classified authority/navigation and active recovery references have been updated.
- The relocation is complete: `docs/DESIGN.md` is the sole authority, every current scoped navigation/recovery reference resolves, all design contracts remain unchanged, and no implementation/runtime artifact was modified by this work unit.

## Changed files

- `progress/design-document-relocation.md`
- `DESIGN.md` → `docs/DESIGN.md`
- `README.md`
- `AGENTS.md`
- `docs/stages/00-mvp.md`
- `docs/stages/01-freeze-source.md`
- `docs/stages/02-discover-application-and-entries.md`
- `docs/stages/03-build-five-program-graphs.md`
- `docs/stages/04-prove-code-facts.md`
- `docs/stages/05-compile-business-flows.md`
- `docs/stages/06-interpret-one-flow-at-a-time.md`
- `docs/stages/07-admit-and-merge-business-knowledge.md`
- `docs/stages/08-build-nine-section-document-and-archive.md`
- `progress/design-end-to-end-walkthrough.md` (active resume instruction only)
- `progress/stage04-runtime-archive-trace-design.md` (active resume instruction only)
- `progress/stage04-sixth-documentation-closeout.md` (active resume instruction only)
- `../../README.md`
- `../../../docs/design/data-standardization-review-experience.md`
- `../../../docs/design/repository-frontend-backend-structure.md`
- `../../../docs/design/repository-frontend-backend-structure.zh-CN.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared dirty worktree recorded before relocation; no unrelated changes will be rewritten. |
| `sed -n '1,260p' progress/TEMPLATE.md` | PASS | Required progress headings confirmed. |
| `rg -l 'DESIGN\.md'` inside component plus bounded cross-repository path search | PASS | Active navigation and historical facts classified; no other component-local same-named design exists. |
| `find . -name DESIGN.md -type f` | PASS | Exactly one existing design authority before move: `backend-agents/sources/github-code/DESIGN.md`. |
| pre/post-rename `shasum -a 256` plus existence assertions | PASS | Safe rename preserved bytes exactly; old absent and new present immediately after rename. |
| final design-location and same-name inventory assertion | PASS | `DESIGN_LOCATION_PASS new=backend-agents/sources/github-code/docs/DESIGN.md old_absent=true repository_same_name_count=1`. |
| JSON/JSONL parser over the 14 design/navigation documents | PASS | `JSON_FENCE_PARSE_PASS objects=79 files=14`. |
| per-module facet and handoff checks | PASS | 8 stages, 34 modules, 11 required facets; 34 standard Luna and 34 standard Terra module guides plus the one global O2 handoff. |
| ModuleArtifact envelope and artifact DAG checks | PASS | 34 envelopes, 8 required envelope fields, canonical upstream/Gap ordering; 91 internal edges, one root and seven external upstream identities. |
| DepotHead composition and Stage07→08 ownership checks | PASS | 10 atoms/proofs, 5-part registry lineage, 23 uniquely owned semantic items, 9 ReaderItems/sections and a 23-hop admitted-term Trace remain closed. |
| R3, O2, identity and completion-state checks | PASS | `3N`/single-registry R3 remains in 8 stages; O2 remains 7 methods/7 routes/4 records/3 adapters; the single envelope identity formula and terminal/diagnostic/fatal separation remain intact. |
| exact stage set and human-first ordering | PASS | Exactly Stage01–08 plus Stage00 POC; each stage's purpose/example precedes its technical contract. |
| relative Markdown link/anchor validator over all 20 scoped documents | PASS | `MARKDOWN_LINK_ANCHOR_PASS files=20 links=109 anchors=10`. |
| repository-wide old-design Markdown-link target scan | PASS | `GLOBAL_OLD_DESIGN_LINK_TARGET_PASS markdown_files=446 stale_targets=0`. |
| current-navigation and active-progress stale-path validator | PASS | `ACTIVE_DESIGN_PATH_PASS current_docs=16 progress_files=155 stale_active=0`; immutable historical statements remain intentionally unchanged. |
| nested repository `git diff --check` | PASS | No tracked-diff whitespace errors. |
| `git diff --no-index --check` over all 20 current scoped Markdown files | PASS | `NO_INDEX_DIFF_CHECK_PASS files=20`. |
| explicit scoped-extension assertion | PASS | `DOCS_ONLY_SCOPE_PASS logical_files=20 path_effects=21 markdown_only=1`. |

## Decisions

- The approved authoritative path is `backend-agents/sources/github-code/docs/DESIGN.md`; the old component-root `DESIGN.md` must not remain.
- Preserve the design bytes/history through a safe filesystem rename, then patch relative links and active authority/recovery instructions for the new nesting level.
- Historical progress statements remain unchanged when they are only immutable past facts; active instructions that would direct a resumed Agent to the old authority must be updated.
- This work unit is documentation-only: no Java, tests, schemas, JSON runtime artifacts, models/providers, network, Maven, generators, deployment, commit, or push.

## Blockers

- None.

## Exact next action

- No relocation action remains. Future design work must begin at `docs/DESIGN.md`; implementation Agents must continue to follow its Sol/ultra Design Authority and MUST/STOP change protocol.

## Resume checks

- Read this progress file and run `git status --short`.
- Confirm `docs/DESIGN.md` remains the sole authority and the old component-root `DESIGN.md` is absent before resuming any link or verification work.
- Preserve all unrelated/shared dirty changes and keep the task docs-only.
