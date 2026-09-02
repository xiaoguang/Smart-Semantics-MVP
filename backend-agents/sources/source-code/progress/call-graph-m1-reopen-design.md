# Progress: call graph M1 reopen design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority for the Program Graphs M1-to-M2 persisted handoff
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Clarify the typed, fail-closed reopening of the persisted M1 code-structure graph before M2; documentation only
- Approved inputs: `AGENTS.md`, both `docs/plans/*.md`, `docs/analysis-steps/03-program-graphs.md`, and read-only inspection of the existing design
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` at `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Read the scoped instructions and both implementation plans completely.
- Confirmed the required correction is local to the Program Graphs step: the current M2 aggregate names a fresh-reopened M1 draft but does not define the persisted reopening proof that produces it.
- Defined `PersistedCodeStructureGraphReader` as the only M1 module reopening seam and `ReopenedCodeStructureGraph` as its sealed, opaque result.
- Fixed the exact reopening checks: current ProgramGraphs M1 address, module version, receipt/reference, one exact payload descriptor/envelope, six source/discovery references, controls, graph profile, and parsed snapshot/profile/entry identities.
- Replaced M2's raw-draft input with the sealed reopened aggregate plus the same `ReopenedProgramGraphInputs`; specified execution, Luna RED, Terra GREEN, and `GRAPH_REFERENCE_BROKEN` behavior.
- Confirmed this is a ProgramGraphs-local interface correction; `docs/DESIGN.md`, schemas, artifact counts, and cross-analysis-step contracts do not change.
- Published the docs-only correction as `e1aa95258e8d04e984577b1f0a91251ef204e5ee`; read-only remote verification confirmed `origin/main` at that commit.

## Current state

- The detailed design now closes M1→M2 persisted lineage without adding an artifact or output. Implementation is explicitly marked not yet present.

## Changed files

- `progress/call-graph-m1-reopen-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Worktree was clean before this progress file was created. |
| `git diff --check` | PASS | No whitespace errors. |
| Program Graphs fence parity check | PASS | 4 backtick and 14 tilde fence markers; both balanced. |
| `git status --short` after edit | PASS | Only the Program Graphs design and this task-owned progress file are changed. |
| `git push origin HEAD:main` | PASS | Fast-forwarded `origin/main` from `7beb3ff` to `e1aa952`. |
| `git ls-remote --heads origin main` | PASS | `origin/main` resolved to `e1aa95258e8d04e984577b1f0a91251ef204e5ee`. |

## Decisions

- Keep the correction inside `docs/analysis-steps/03-program-graphs.md` unless inspection reveals a missing cross-step invariant.
- Preserve the existing public M2 builder shape conceptually, but replace its raw structure member with an immutable reopened structure aggregate.
- The M1 graph profile is checked against `CallGraphProfile.graphProfileRef`; M1–M5 already share one exact graph profile by the existing wire contract.
- Reader validation failures are fatal `GRAPH_REFERENCE_BROKEN`, never a local Graph Gap.

## Blockers

- None.

## Exact next action

- Parent rebases the code branch onto the published design, then resumes the M2 RED/GREEN cycle from the reader/execution RED.

## Resume checks

- Confirm commit/push SHA is present on `origin/main`; then verify the implementation branch contains the same design before editing Java or tests.
