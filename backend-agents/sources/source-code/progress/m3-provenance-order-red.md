# Progress: M3 provenance ordering RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Public M3 ControlFlowGraphBuilder determinism when the same physical handler is reached by two entries with distinct route source spans and discovery order is reversed.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `docs/supplements/program-graphs-implementation-backlog.md`, `progress/m3-owner-union-review.md`, and the existing public multi-entry fixture in `ControlFlowGraphBuilderTest`.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Ran the baseline `ControlFlowGraphBuilderTest` selector before adding the new regression. Existing 14 tests pass.
- Added one public-seam regression to `ControlFlowGraphBuilderTest`; it reuses the real M1/M2 publication/readback multi-entry fixture, gives the two entries distinct route source spans, reverses the fixture input order, and compares the complete `ControlFlowGraphDraft`.

## Current state

The focused regression is complete. It reuses the real M1/M2 publication/readback multi-entry
fixture, gives the two entries distinct route source spans, reverses the fixture input order, and
compares the complete `ControlFlowGraphDraft`. The expected RED did not occur: the current upstream
`ProgramGraphDiscoveryInputs` sorts entries by entry identity and `ControlFlowGraphDraft` sorts
provenance drafts by provenance identity, so both complete drafts are already equal. No production
change is justified by this test.

## Changed files

- `progress/m3-provenance-order-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Baseline selector | PASS | Same selector below; 14 tests, 0 failures/errors/skips; BUILD SUCCESS |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS (baseline) | 14 tests, 0 failures/errors/skips; BUILD SUCCESS |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 15 tests, 0 failures/errors/skips; BUILD SUCCESS; expected provenance-order RED not reproduced |
| `git diff --check -- progress/m3-provenance-order-red.md src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java` | PASS | No whitespace errors |

## Decisions

- Test only the public `ControlFlowGraphBuilder` seam and complete `ControlFlowGraphDraft` equality/canonical bytes.
- Keep the existing real M1/M2 publication/readback fixture path; do not mock or inspect private implementation state.
- Do not modify production code, design documents, POM, or another agent's progress file.

## Blockers

- The requested RED condition cannot be established against the current public seam because entry
  and provenance collections are already canonicalized before the final draft is returned.

## Exact next action

Coordinator should decide whether to retain this passing regression as coverage and close the review
finding, or identify a different public input seam that genuinely permits caller-controlled entry
order. Do not change production code solely to force RED.

## Resume checks

Read this file, `git status --short`, and the existing M3 owner-union review before continuing. Preserve unrelated worktree changes.
