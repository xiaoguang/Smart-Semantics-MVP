# Progress: Cross-Flow final synchronization fix

- Status: COMPLETE
- Agent role: Sole source-code process-reconstruction Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Documentation-only closure of all three P1 findings and the P2 finding in `final-rereview-2-report.md`; synchronize `docs/DESIGN.md`, scoped `AGENTS.md`, delivery-plan Tasks 8-10, and the Step 06-08 terminal contracts without changing production code, tests, schemas, runtime, model use, scan behavior, public API, or artifact accounting.
- Approved inputs: `final-rereview-2-report.md`; repository, backend, and source-scoped `AGENTS.md`; `docs/DESIGN.md`; Steps 06-08; `docs/plans/source-analysis-naming-and-delivery-plan.md`.
- Current branch/worktree: `codex/source-analysis-process-reconstruction-design`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the final rereview through EOF and extracted its three P1 plus one P2 contract defects.
- Read all three applicable instruction files and confirmed the worktree was clean before this progress file was created.
- Confirmed the repair is documentation-only and must preserve eight steps, nine chapters, the public API, evidence/model bounds, and exactly 57 reader-visible outputs.
- Re-opened every cited DESIGN, scoped-AGENTS, Step 06-08, and delivery-plan range and verified each finding against the current text.
- Verified the existing Step 08 process-terminal ReaderItem/Trace branch is intentionally Gap-owned, so P1 `FAILED` must gain a Step 06-owned canonical typed Gap rather than bypass that branch.
- Patched Step 06 and its DESIGN mirror with `PROCESS_P1_HYPOTHESIS_FAILED / PROCESS_P1_RESPONSE`, exact response/disposition/Gap ownership, acyclic materialization, stable code, and terminal Trace requirements.
- Replaced stale DESIGN/scoped-AGENTS P2 four-result and process-total-admission summaries with the `P2_REVIEWS` four-decision branch, typed P2 GAP/FAILED task terminals, six-way hypothesis partition, admission-eligible subset, and reasoned exclusions.
- Patched Step 07 and DESIGN with `canonicalGapId=g.gapId=memberGapIds[0]` and the deterministic actual-admission formula for `affectedBusinessProcessIds`.
- Synchronized Step 08 and delivery-plan Tasks 8-10 with P1 NOT_RUN and actual-P2 terminal ReaderItem/Trace/validator contracts.

## Current state

All four findings are closed in the owning documents, overall mirror, scoped instructions, and delivery plan. The targeted semantic, mirror, count, link, scope, and diff checks pass; no implementation or runtime surface changed.

## Changed files

- `backend-agents/sources/source-code/progress/crossflow-final-sync-fix.md`
- `backend-agents/sources/source-code/AGENTS.md`
- `backend-agents/sources/source-code/docs/DESIGN.md`
- `backend-agents/sources/source-code/docs/analysis-steps/06-flow-interpretation.md`
- `backend-agents/sources/source-code/docs/analysis-steps/07-repository-knowledge.md`
- `backend-agents/sources/source-code/docs/analysis-steps/08-nine-section-document.md`
- `backend-agents/sources/source-code/docs/plans/source-analysis-naming-and-delivery-plan.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Clean worktree. |
| Complete read of `final-rereview-2-report.md` | PASS | P0=0; P1=3; P2=1. |
| Complete reads of applicable `AGENTS.md` files | PASS | Root, backend, and source-code rules loaded. |
| Targeted rereview citation audit | PASS | All four findings reproduced in the cited current contracts. |
| Documentation-only scope check | PASS | Six durable docs plus this progress file; no Java/test/schema/runtime file changed. |
| `git diff --check` after contract edits | PASS | No whitespace errors. |
| Stale-contract search | PASS | No old total-hypothesis admission/certainty equation, four-result-only P2 rule, empty P1-failure Gap rule, or hard-coded counter-scope ownership rule remains in changed scope. |
| Terminal/Gaps cross-document check | PASS | P1 failure carrier, P2 terminal partitions, three Trace branches, deterministic affected-process ownership, and singleton canonical identity are present in their owning and summary documents. |
| Delivery-plan Tasks 8-10 | PASS | RED/GREEN work now covers pair/counter bases, all six response variants, six-way partition/accounting, singleton Gap mapping, affected process IDs, and all three process Trace branches. |
| Preserved boundary counts | PASS | 8 analysis-step documents, 9 fixed chapters, 7 public methods, 15 Step 06 files, and 57 formal run outputs. |
| Mirrored record catalogs | PASS | `ProcessInterpretationGapV1` and `MergedGapV3` match their `DESIGN.md` mirrors after comment normalization where applicable. |
| Markdown links | PASS | All relative links in the six changed durable documents resolve. |

## Decisions

- Keep the approved architecture and repair only contradictions and missing deterministic mappings identified by the rereview.
- Use a canonical typed Step 06 Gap for P1 `FAILED`, so the existing process-terminal ReaderItem and canonical-Gap Trace contract remains exhaustive without a parallel failure-only public branch.
- Derive counter-scope Gap `affectedBusinessProcessIds` from the actual admitted processes affected by that Gap; do not reclassify pending reviewed relations as categorically non-admissible.
- Set every Step 06-owned singleton `MergedGapV3.canonicalGapId` to its member `gapId`.
- Define `MergedGapV3.affectedBusinessProcessIds` as the sorted exact set of non-null `ProcessAdmissionDecisionV1.businessProcessId` values whose decision `gapIds` contains the member Gap; this permits a counter-scope Gap to identify an actually admitted pending process and leaves all non-admission branches empty by construction.

## Blockers

- None.

## Exact next action

- Stage the six durable documents plus this progress record, run the cached scope/diff checks, commit without push/PR, then write the ignored `final-fix-3-report.md` with the resulting SHA and verification evidence.

## Resume checks

- Re-read this file and the three governing `AGENTS.md` files.
- Run `git status --short` and preserve any unrelated changes.
- Re-open the exact final-rereview citations and every patched cross-document contract before claiming closure.
