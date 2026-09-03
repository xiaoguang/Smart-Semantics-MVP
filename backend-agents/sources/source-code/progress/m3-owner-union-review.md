# Progress: M3 owner-union review

- Status: COMPLETE
- Agent role: Luna/xhigh read-only code reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Read-only review of M3 shared-node, edge, traversal, terminal, and profile-stop Gap ownership/identity behavior.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `docs/supplements/program-graphs-implementation-backlog.md`, `ControlFlowGraphBuilder.java`, `ControlFlowGraphBuilderTest.java`, and M3 owner-union progress records.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

## Current state

Review is complete as a read-only pass. No Maven command and no production/test/design modification was performed.

## Changed files

- `progress/m3-owner-union-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Static source/test inspection (`ControlFlowGraphBuilder.java`, `ControlFlowGraphBuilderTest.java`, approved M3 progress) | PASS | Owner merge, edge reuse, profile-stop aggregation, and graph identity paths inspected; no M2/M4 changes in scope. |
| `git diff --check --` on reviewed paths | PASS | No whitespace errors. |

## Findings

### P1 — Draft publication is not deterministic when entry order changes provenance registration order — CLOSED / false positive

The initial static concern was that `buildControlFlow` walks entries in caller order while
`finish()` copies an insertion-ordered provenance map. The subsequent Luna public-seam regression
`m3-provenance-order-red` used two distinct route spans and reversed entry order, then verified full
`ControlFlowGraphDraft` equality; `ControlFlowGraphBuilderTest` passed 15/15 after the established
canonical ordering in `ProgramGraphDiscoveryInputs` and `ControlFlowGraphDraft`. The concern is
therefore not reproducible under the actual sealed public input/output seam and is closed without
a production change.

### P2 — Multi-entry profile-stop Gap ownership and terminal remapping have no public regression test

`profileStopGap(...)` correctly keys transient aggregation by the stable terminal candidate,
rebuilds the Gap with the sorted owner union, updates `gaps`, and `profileStop(...)` replaces the
terminal disposition with the rebuilt Gap ID. However, the current multi-entry test only exercises
shared normal control-flow nodes/edges; `recordsAnUnsupportedLoopAsOneTypedProfileStopGap` is
single-entry. There is no test proving that a shared loop/branch profile-stop produces one Gap,
owners equal to the complete sorted entry union, one terminal disposition pointing to the rebuilt
Gap, and unchanged coverage mapping. Add that narrow public-seam test before calling this part
fully accepted.

### P2 — Conflict branches are implemented but not directly tested for shared-node/edge provenance

`mergeNodeOwners(...)`, `registerEdge(...)`, and `registerProvenance(...)` fail closed on unequal
representations for one identity. The owner test does not inject a conflicting repeated physical
representation (canonical value, node kind, provenance, or edge payload), so the fail-closed
contract is only visible by inspection. Add mutation tests at the public builder seam if this
contract is required for the M3 exit gate; no production change is implied by this finding.

No P0 or open P1 was found. The current implementation satisfies the exercised normal
shared-node/edge owner union and profile-stop aggregation logic by inspection. The two P2 test
coverage items remain open follow-ups; they do not indicate a confirmed production defect.

## Decisions

## Blockers

- No implementation blocker remains from the reviewed P1; the remaining P2 items are test coverage
  follow-ups.

## Exact next action

Add the two narrow public mutation tests in a separate implementation/test work unit if they remain
required by the M3 exit gate; do not change production behavior for the closed P1.

## Resume checks

Re-read this file and preserve the finding severities. Do not treat the existing 14-test owner
selector as coverage of distinct provenance, profile-stop aggregation, or conflict mutations.
