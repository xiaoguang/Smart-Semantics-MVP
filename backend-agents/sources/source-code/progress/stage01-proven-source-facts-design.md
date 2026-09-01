# Progress: Stage 01 proven-source-facts detailed design

- Status: COMPLETE
- Agent role: Stage 01 design owner
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30 00:25:55 NDT
- Last updated: 2026-08-30 00:42:36 NDT
- Scope: Design Stage 01 only; modify this progress file and `docs/stages/01-proven-source-facts.md`.
- Approved inputs: Repository-local instructions, design/current implementation, bounded synthetic fixtures, and the already captured immutable jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; no live source or model call.
- Current branch/worktree: `codex/rag-frontend-phase-one`; target directory is currently untracked in the outer worktree and unrelated existing changes must be preserved.

## Completed

- Read the repository-root, prototype, backend-agents, and GitHub Code Agent `AGENTS.md` files completely.
- Read `progress/TEMPLATE.md` and recorded the pre-edit `git status --short`.
- Read the overall-design main line, M1-M3 walkthrough, assurance pre/postcondition chain, ARCH mapping, and current POC maturity matrix.
- Read `docs/stages/00-mvp.md` completely and recorded its Stage 01 handoff constraints, especially the missing semantic-closure gate and rejected DepotHead input.
- Read the current discovery/analysis records, implementations, focused tests, CLI projection, and the POC `FrozenManifest` verification path.
- Reconfirmed the local jshERP capture is at commit `8c30ce7861570458920175e200bb2a6442713580`, has no worktree changes at inspection time, and declares Java 8 plus MyBatis mapper locations; this is design input, not a Stage 01 acceptance result.
- Wrote the 357-line Stage 01 detailed design with one M1→M2→M3 admission seam, exact request/success/failure JSON shapes, deterministic identities, module-local failure behavior, and atom/Proof accounting.
- Defined the bounded six-file synthetic contract and an eight-file fixed-commit jshERP honesty acceptance that permits explicit gaps but forbids unsupported Fact promotion.
- Defined Luna→Terra vertical TDD order, positive/negative mutations, exit gates, out-of-stage work, and an explicit no-LLM/no-generation runtime boundary.

## Current state

- Design is complete and ready for Luna to author Stage 01 RED tests, followed by Terra implementation. No implementation or acceptance result is claimed by this task.

## Changed files

- `progress/stage01-proven-source-facts-design.md`
- `docs/stages/01-proven-source-facts.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated changes and untracked prototype directories observed before edits. |
| `wc -l docs/stages/01-proven-source-facts.md` | PASS | 357 lines, within the requested approximately 350-500 line range. |
| local linked-file existence checks | PASS | DESIGN, Stage 00, RepositoryDiscoverer, SourceAnalyzer, and CodeFact targets exist. |
| `awk` fenced-block balance check | PASS | 22 fences; balanced. |
| trailing-whitespace/unresolved-marker scan | PASS | No trailing whitespace or unresolved drafting markers. |
| `git diff --check -- docs/stages/01-proven-source-facts.md progress/stage01-proven-source-facts-design.md` | PASS | No whitespace errors reported. |
| scoped `git status --short` | PASS | Only the two authorized untracked files are attributable to this task. |

## Decisions

- This work unit is documentation-only and will invoke no LLM or other generative-content runtime.
- Historical artifacts and existing test reports are context only, never proof that the Stage 01 target contract has passed.
- The formal Stage 01 path replaces, rather than blesses, unfrozen directory diagnostics; only a complete `Stage01Result` may reach M4.
- The jshERP acceptance uses a separate Java 8 profile and bounded inventory; nonzero Gap/rejection is acceptable when all sites/atoms are honestly accounted.

## Blockers

- None.

## Exact next action

- Hand the approved design to Luna/xhigh for `VerifiedSnapshotContractTest` RED, then Terra/xhigh for the minimal M1 GREEN implementation.

## Resume checks

- Re-read this file and the nearest `AGENTS.md`.
- Run `git status --short` and confirm only the two authorized Stage 01 files are attributable to this task.
- Reconfirm the jshERP snapshot identity before treating it as an acceptance input.
- Do not change this document to IMPLEMENTED/ACCEPTED until the fresh targeted commands and fixed-commit honesty gates named in Section 12 have actually passed.
