# Progress: M3 control-flow Gap carrier contract test

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add one narrowly scoped direct M3 public-seam test for the shared `GraphGapDraft` carrier; no production, design, fixture, or M1/M2/M4/M6 changes.
- Approved inputs: Existing `Fixture.createWithStatusLoop(temporaryDirectory)` unsupported-control-flow fixture and `ControlFlowGraphBuilder.buildControlFlow(ControlFlowInputs, ControlFlowGraphProfile)` seam.
- Current worktree: Shared worktree; unrelated existing edits are preserved.

## Contract under test

- A profile-stop/unsupported draft uses schema version `program-graphs-control-flow-draft-v3`.
- It carries one non-empty local `GraphGapDraft` with exact reason code, affected entry/candidate IDs, and verified source locator.
- `gapDrafts` and `coverage.gapDispositions` close bidirectionally on the same candidate and `gapId`.
- Rebuilding from the same sealed predecessors is deterministic, while changing the carrier reason changes its content-addressed `gapId`.

## Completed

- Read `AGENTS.md`, both implementation plans, and the M3/8.0.1/8.0.2/8.4–8.6 contract sections.
- Added one direct `ControlFlowGraphGapCarrierTest` using the real reopened M1/M2 fixture and public `buildControlFlow` seam.
- Added an in-memory `GraphGapDraft.forLocalOccurrence` identity mutation assertion; it verifies carrier identity rather than mutating the enclosing graph ID.
- Made the existing loop fixture factory package-visible solely so the new same-package test can reuse it; no fixture source or production behavior changed.
- The test intentionally remains RED until the designated production slice adds the v3 `gapDrafts` carrier and graph identity binding.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphGapCarrierTest test` | EXPECTED RED | Test compilation fails only because `ControlFlowGraphDraft` has no `gapDrafts()` method (lines 32, 33, and 86 of the new test); this is the expected absent-v3-carrier error. |

## Changed files

- `progress/control-flow-gap-carrier-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphGapCarrierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java` (package visibility for existing loop fixture factory only)

## Exact next action

- Hand the RED test to the designated production agent; do not extend this slice to M1/M2/M4/M6.
