# Progress: M3 serial call continuation GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Minimal `ControlFlowGraphBuilder` support for normal-return continuation between supported sequential exact Java calls in distinct lexical basic blocks.
- Approved inputs: M3 program-graphs design, P4 backlog, `SerialCallContinuationTest` RED, existing `ControlFlowGraphBuilderTest` fixtures and M1/M2 reopened inputs.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read scoped Agent guidance, M3 normal-exit/continuation contract, P4 boundary, and the existing Luna RED record.
- Re-ran `SerialCallContinuationTest`: confirmed its single expected failure. The first mapper call has no `NEXT` edge to the audit statement's basic block.
- Implemented the bounded serial shape in `ControlFlowGraphBuilder`: calls are located in their exact lexical basic blocks; when two or more activated exact callsites occupy distinct blocks, predecessor block `NEXT` edges that would bypass a call anchor are suppressed.
- A normal-exit target now sends the callsite to its immediate lexical successor block in that bounded serial shape, or to the caller normal terminal if it is the final block. A target without a known normal exit still has no continuation.
- Ran Spotless and both required direct selectors successfully.

## Current state

- The bounded distinct-basic-block serial shape is GREEN. Nested shapes and multiple calls in one lexical basic block are intentionally unchanged and remain outside this slice.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java`
- `progress/m3-serial-call-continuation-green.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` | EXPECTED RED | 1 test, 1 assertion failure, 0 errors/skips: missing mapper-call → audit-block normal continuation |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | formatter reported three files; the shared builder test already had concurrent uncommitted content and received formatting only, not a semantic edit in this slice |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SerialCallContinuationTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 16 tests, 0 failures, 0 errors, 0 skipped |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java progress/m3-serial-call-continuation-green.md` | PASS | no whitespace errors |

## Decisions

- Continuation is emitted only after an M2-exact call whose target has a known normal exit. It leaves the callsite, not the predecessor basic block.
- For a supported serial sequence, lexical `NEXT` edges out of call-containing blocks are suppressed; a call's immediate next lexical block becomes its continuation target, otherwise the caller normal terminal.
- The block-to-call anchor edge and M2 CALL/RETURN structural projections are unchanged; the added continuation does not interpret an external effect.

## Blockers

- None.

## Exact next action

- Hand the GREEN result to the M3 coordinator for inclusion in the stage-level verification and audit.

## Resume checks

- Re-read this progress file and `progress/m3-serial-call-continuation-red.md`; inspect the diff; run `SerialCallContinuationTest` followed by `ControlFlowGraphBuilderTest`.
