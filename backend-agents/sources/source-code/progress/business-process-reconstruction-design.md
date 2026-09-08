# Progress: Business-process reconstruction design

- Status: COMPLETE_POST_REVIEW_FIX_2
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08 03:50:53 NDT
- Last updated: 2026-09-08 09:47:40 NDT
- Scope: Documentation-only cross-Flow BusinessProcess reconstruction design under `backend-agents/sources/source-code/`.
- Approved inputs: `task-1-brief.md` and `cross-flow-requirements.md` in `.superpowers/sdd/source-analysis-process-reconstruction-design-plan/`.
- Current branch/worktree: `codex/source-analysis-process-reconstruction-design` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the Task 1 brief, exact cross-Flow requirements, and scoped `AGENTS.md` in the mandated order.
- Confirmed the worktree was clean before tracked edits.
- Read all nine in-scope durable documents completely and mapped the old single-Flow/count contracts that require revision.
- Revised `docs/analysis-steps/05-business-flows.md`: added evidence-backed `processJoinSignals`, their exact schema/identity/coverage contracts, the real bounded DepotHead specimen, the explicitly synthetic replenishment-to-settlement acceptance case, and Luna RED/Terra GREEN/current-delta guidance while preserving the six-file Step 05 boundary.
- Replaced `docs/analysis-steps/06-flow-interpretation.md` with the approved nine-module, fifteen-file design; local R0/R1/R2 remain single-Flow and P1/P2 are the sole bounded multi-Flow exception.
- Replaced `docs/analysis-steps/07-repository-knowledge.md` with a zero-call local/process admission design, three-value certainty, conflict preservation, many-to-many memberships, nine process arrays, and the renamed admission file.
- Replaced `docs/analysis-steps/08-nine-section-document.md` with a fixed-nine-section process-first plan, five process ReaderItem kinds, plan-only renderer, full process Trace, and unchanged public interface.
- Aligned `docs/DESIGN.md`, `AGENTS.md`, `README.md`, the naming/delivery plan, and the ProgramGraphs backlog to the same eight-step/57-artifact/model-boundary contracts.
- Read and verified the independent Task 2 review. All five P1 and two P2 findings were reproduced against baseline commit `5b5fc5b` and accepted for fix round 1.
- Closed all seven review findings: exact four-tier signal mapping and thirteen-kind closure; local-versus-total task accounting; typed claim/slot lineage and closed certainty predicates; complete Step 06/07/08 wire catalogs aligned with DESIGN; bounded Sol/ultra local/inter-module authority; Capsule span V4; and this post-review progress handoff.
- Read and verified fix-round-1 re-review. The two remaining P1s reproduce: generic all-fields self hashing creates Step 06 round/hypothesis/review and Step 07 process/child cycles, and the Step 06 hypothesis sample is not schema-conforming.
- Replaced the Step 06 hypothesis example with a complete synthetic structural fixture: every mandatory field is present, support/cue/counter names match the exact schema, all claim/slot/relation references close, and full P1/P2 lineage is present.
- Recorded the user's direct confirmation of the exact identity-chain repair after two earlier safety-gate rejections.
- Replaced the circular Step 06 and Step 07 identity formulas with explicit acyclic semantic-ID DAGs, exact per-record exclusions/references, mandatory creation order, closed back-reference validation, unchanged full-wire artifact hashing, and no alias/dual-write/compatibility path. The detailed contracts and `docs/DESIGN.md` are byte-equivalent across each identity table and validation block.

## Current state

- Commit `376df3e` is the fix-round-2 reviewed baseline. This record and the three corrected durable design files form the post-review fix-2 handoff contained in the next documentation commit; resolve that containing commit with `git rev-parse HEAD` after checkout rather than embedding a self-referential prospective SHA here.
- The two re-review P1s are closed in the target design: every new Step 06/07 process semantic ID now has a finite topological preimage, and the Step 06 `BusinessProcessHypothesisV1` fixture has the exact mandatory field sets and closed internal references. No production implementation is claimed.
- No Java, tests, schemas, generated/runtime artifacts, source scans, or model calls are in scope.

## Changed files

- `progress/business-process-reconstruction-design.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/DESIGN.md`
- `AGENTS.md`
- `README.md`
- `docs/plans/source-analysis-naming-and-delivery-plan.md`
- `docs/supplements/program-graphs-implementation-backlog.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Clean branch `codex/source-analysis-process-reconstruction-design` tracking `origin/main` before the progress-file edit. |
| `git diff --check` | PASS | No whitespace errors. |
| targeted `rg` contract checks | PASS | In the approved files: 57 count, five new Step 06 files, task/call formula, NOT_RUN semantics, exact signal tiers, nine process arrays, complete Plan/Reader/Trace fields, five ReaderItem kinds, and synthetic caveats are present; stale 52, `ModelEvidenceSpanV3`, and wire `nineSectionPlanId!` are absent. |
| identity-block `cmp` and row counts | PASS | Step 06 and Step 07 detailed identity blocks match `docs/DESIGN.md`; each enumerates ten self-ID records. |
| extracted fixture `jq -e` assertions | PASS | JSON parses; root/claim/member/relation/slot key sets are exact; typed purpose/end, member/relation/slot references close; obsolete abbreviated signal fields are absent. |
| fix-2 targeted `rg` checks | PASS | Exact approved Step 06/07 exclusion sets, full-wire SHA/root validation, no-alias rule, fixed eight steps/nine sections/57 count, `flow-interpretation`, P1/P2 boundary, and synthetic/external-effect caveats remain present. |

## Decisions

- Preserve the eight analysis steps, the `flow-interpretation` key, the fixed nine-section document, and the public `RepositoryAnalysisAgent` Interface.
- Treat P1/P2 as the sole bounded multi-Flow model exception; all other model rounds remain single-Flow.
- Keep target architecture separate from current implementation maturity and preserve unproved external effects as Gaps.
- Evolve Step 06 from six modules to nine: preserve local M1–M5, add deterministic cross-Flow M6/M7 and Luna P1/P2 M8, then move the expanded publication specifier to M9.
- Keep Step 05 at five semantic payloads plus receipt, Step 07 at five semantic payloads plus receipt, and add exactly five Step 06 semantic payloads so the formal run total becomes 57.

## Blockers

- None.

## Exact next action

- Independent review may inspect the containing fix-2 commit. If accepted, Luna/xhigh may derive bounded RED tests from the frozen contracts; no production implementation is part of this documentation work unit.

## Resume checks

- Re-read this progress file and run `git status --short --branch`.
- Verify that only the approved documentation paths and this progress file are modified.
- Re-read the approved brief and exact requirements before changing a cross-step contract.
