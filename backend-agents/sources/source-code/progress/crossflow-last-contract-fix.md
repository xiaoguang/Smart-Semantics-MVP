# Progress: cross-flow final contract repair

- Status: COMPLETE
- Agent role: sole Sol/ultra design authority
- Model: `gpt-5.6-sol / ultra`
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Documentation-only repair of the four final cross-flow contract findings in `docs/DESIGN.md` and analysis Steps 05–08, plus this progress record and the required final report. No code, tests, schemas, runtime/model execution, scans, push, or pull request.
- Approved inputs: final re-review report, approved cross-flow requirements, and standing authorization for bounded non-goal-changing intermodule protocol repairs.
- Current branch/worktree: `codex/source-analysis-process-reconstruction-design` in `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the final re-review findings and approved cross-flow requirements.
- Read the applicable repository, backend-agent, and source-code instructions.
- Read the complete Step 05–08 detailed designs and the affected overall-design contracts.
- Reconciled the four findings as one bounded protocol repair: an embedded Step 06 Gap carrier, closed P2 terminal branches, complete relation-level counter bases, and precise partition-control stability language.
- Patched Step 05 so complete disjoint business-object anchor sets are restored as a relation-level `DIFFERENT_BUSINESS_OBJECT` counter basis without changing Step 05 signal kinds.
- Patched Step 06 with `ProcessInterpretationGapV1`, its unique disposition carrier and acyclic preimage; complete positive/counter basis records; and exact P2 response, publication, nullable lineage, task disposition, and six-way accounting rules.
- Patched Step 07 with admission-versus-exclusion rules and singleton reversible `MergedGapV3` mapping.
- Patched Step 08 with exact P1-terminal and P2-terminal `GAP_QUESTION`/Trace branches, including source-or-searched-scope termination.
- Patched the overall design and scoped source-code instructions to state that changed partition controls preserve candidate/topology/logical membership, not group IDs or bytes.
- Preserved invariants identified: eight steps; fixed nine chapters; unchanged public `RepositoryAnalysisAgent`; unchanged evidence/model trust boundary; exactly 57 reader-visible outputs.

## Current state

- The four final contract findings are closed in the synchronized detailed and overall designs.
- The documentation-only change set passed the cross-document, preserved-invariant, link, scope, catalog-mirror, identity-table-mirror, stale-name, and whitespace checks recorded below.
- The final commit SHA and check summary are recorded in the required ignored final report; no implementation capability or current-run result is claimed.

## Changed files

- `backend-agents/sources/source-code/AGENTS.md`
- `backend-agents/sources/source-code/docs/DESIGN.md`
- `backend-agents/sources/source-code/docs/analysis-steps/05-business-flows.md`
- `backend-agents/sources/source-code/docs/analysis-steps/06-flow-interpretation.md`
- `backend-agents/sources/source-code/docs/analysis-steps/07-repository-knowledge.md`
- `backend-agents/sources/source-code/docs/analysis-steps/08-nine-section-document.md`
- `backend-agents/sources/source-code/progress/crossflow-last-contract-fix.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Worktree was clean before this progress file was created. |
| scoped `git status --porcelain=v1` comparison | PASS | Exactly the six synchronized design/instruction files plus this progress record; documentation/progress only. |
| fixed-boundary counts | PASS | 8 analysis packages, 9 fixed chapters, 7 public methods, 15 Step 06 files, and the formal `4+5+8+5+6+15+6+8=57` equation. |
| targeted contract searches | PASS | Typed Step 06 Gap carrier/codes, all P2 terminal branches, complete pair/counter bases including `DIFFERENT_BUSINESS_OBJECT`, and changed-partition stability wording are present in every owning document. |
| stale schema/protocol searches | PASS | Replaced V1/V2 names and the prior bounded-material/counter wording are absent from the active changed scope. |
| normalized record-catalog comparison | PASS | Step 06, `MergedGapV3`, and `TraceRecordV4` detailed catalogs match `DESIGN.md` after comment/whitespace normalization. |
| identity-table comparison | PASS | Step 06 and Step 07 detailed identity tables match `DESIGN.md` byte-for-byte. |
| relative Markdown link check | PASS | Every relative link in the changed durable design documents resolves. |
| `git diff --check` | PASS | No whitespace errors before staging. |

## Decisions

- Embed each Step 06-owned `ProcessInterpretationGapV1` exactly once in the owning `ProcessInterpretationDispositionV2.processGaps[]`; retain `gapIds[]` as the union of those owned IDs and referenced upstream Gap IDs. This adds no file and preserves the 57-output boundary.
- Preserve typed `P2_GAP` and `P2_FAILED`: publish their P1 hypotheses with nullable review lineage, exclude them from Step 07 admission, and trace the terminal P2 round/receipt plus canonical Gap without inventing a review.
- Store every qualifying positive-pair basis and every scoped counter basis in the program-only relation record. A disjoint pair of proof-closed business-object anchor sets becomes a deterministic `DIFFERENT_BUSINESS_OBJECT` counter basis; the model still sees only the existing aggregate path-free relation view.
- Treat changed partition controls as an identity change for group records because persisted limits are identity material. Only candidate relation records/topology and group membership partitions remain stable; group IDs/bytes do not.

## Blockers

- None.

## Exact next action

- No further action remains in this documentation repair. Later implementation must begin from the frozen contracts and the repository's required RED-first workflow; it is outside this work unit.

## Resume checks

- Read the required final report for the commit SHA and verification summary before beginning later work.
- Do not run live models, source scans, code/tests, push, or PR operations.
