# Progress: Program-graph Gap projection contract design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Decide and, only if required, freeze the common M1--M4 typed Gap wire that lets M6
  project every local unsupported, ambiguous, or over-limit graph disposition into
  `graph-gaps.jsonl` without inference.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`,
  `docs/analysis-steps/03-program-graphs.md`, `GraphCoverage`, `GraphGapDisposition`,
  `GraphGapDraft`, and `ProgramGraphSetPublicationSpecifier`.
- Current worktree:
  `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`.

## Completed

- Read the scoped rules, authoritative overall design, ProgramGraphs detailed design, and the
  requested M1--M6 Gap/coverage/publication records.
- Confirmed the current detailed design explicitly requires all graph-local unsupported,
  ambiguous, and over-limit items to remain visible as Graph Gaps, but gives a rich persisted Gap
  carrier only to M4.
- Updated only the ProgramGraphs detailed design to freeze one common M1--M4 local Gap carrier,
  exact identity and coverage closure, producer ownership, pure M6 projection, fatal conditions,
  and direct public tests.

## Current state

- Design decision complete. Current M1--M3 implementation remains on its old v2 drafts and M6
  still rejects those local Gaps; implementation/tests must follow the newly frozen v3 contracts.

## Changed files

- `progress/program-graph-gap-contract-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg` contract scans | PASS | No remaining M1--M3-v2/M4-only/fatal-at-M6 exception in the active contract |
| `git diff --check -- docs/analysis-steps/03-program-graphs.md progress/program-graph-gap-contract-design.md` | PASS | No whitespace errors |

## Decisions

- The prior contract was insufficient and contradictory: it required every builder Gap to persist
  and reach `graph-gaps.jsonl`, while making M1--M3 local Gaps unprojectable and fatal in M6.
- M1--M4 now share `GraphGapDraft(gapId, reasonCode, affectedEntryIds,
  candidateElementIds, sourceLocator)` inside each installed draft. The enclosing draft supplies
  the graph kind; no detached Gap input or sidecar is added.
- M1 code-structure, M2 call, and M3 control-flow draft schemas advance from v2 to v3 because they
  add required `gapDrafts`; M4 data-flow remains v2 because that field already exists. Public graph
  schemas and `program-graphs-graph-gap-v1` remain unchanged.
- A local Gap ID is the framed SHA-256 of a versioned identity material containing owner graph kind,
  reason, sorted affected entries, sorted candidate IDs, and the verified source locator. Every
  M1--M4 graph ID binds its complete sorted `gapDrafts`.
- For each gap draft, candidate IDs equal exactly the coverage gap dispositions carrying its gap
  ID; every disposition maps to exactly one draft. Scope gaps stay separate. Module `gapRefs` are
  exactly local draft Gap IDs union scope Gap IDs.
- M1--M4 own reason/entry/candidate/locator production. A known local over-limit item is a Gap only
  when its full candidate denominator is already known; inability to finish the denominator is
  fatal and cannot produce a partial draft.
- M6 fresh-reopens and validates every carrier, then copies it one-to-one to `GraphGapV1` while
  adding only owner graph kind and public schema version. Missing/extra/mutated carriers or broken
  closure are fatal; a valid M1--M3 local Gap is not fatal.
- Public tests must cover one local Gap from each M1--M4 producer, a mixed four-graph publication,
  field/identity/coverage/module-ref mutations, M1--M3 v2 rejection, empty JSONL, and status/receipt
  closure.

## Blockers

- None. No code, tests, build files, `docs/DESIGN.md`, recovery design, or other analysis steps were
  changed.

## Exact next action

Luna/xhigh should write the direct M1--M4 carrier/version REDs and M6 four-kind projection REDs;
Terra/xhigh must not continue M6 against the old M1--M3 v2 shapes.

## Resume checks

1. Re-read this progress file and inspect `git status --short` without disturbing other agents.
2. Confirm only the detailed ProgramGraphs design and this progress file belong to this decision.
3. Begin with the public M1--M3 v3 schema/carrier REDs; do not patch M6 around missing metadata.
