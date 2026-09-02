# Progress: call graph reopening

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M2→M3 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Reopen and verify the persisted M2 call graph as M3's only call-graph input. Do not create control-flow nodes, outputs, or analysis-step publications.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `854aa94`, both implementation plans, and the completed M1→M2 reader contract.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed the published M2→M3 contract requires a sealed `ReopenedCallGraph`; raw `CallGraphDraft` cannot enter M3.
- Added the real-store reopening RED to the M2 module selector. Compilation failed only because `PersistedCallGraphReader` and `ReopenedCallGraph` were absent.
- Implemented the sealed M2 aggregate and strict persisted reader. It reopens the M2 receipt/payload, verifies its `program-graphs / 2 / call-graph` identity, the eight upstream artifacts, controls, producer/completion envelope, and the same source/discovery/M1/profile basis before decoding the call graph.
- The reader selector is GREEN with real canonical M1 and M2 module publications.
- M3 contract audit found that the sealed aggregate must expose its already-verified M1 payload identity. Added `codeStructurePayloadRef`; a compile RED established the missing accessor, and the real-store selector is GREEN again.

## Current state

- The M2→M3 reopening seam is GREEN. The next M3 work can create a control-flow RED using only the sealed M1/M2 aggregates and the same reopened source/discovery inputs.

## Changed files

- `progress/call-graph-reopening.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ReopenedCallGraph.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedCallGraphReader.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | RED | Test compilation fails only because `PersistedCallGraphReader` and `ReopenedCallGraph` are absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | PASS | 3 tests, 0 failures/errors/skips; the reader fresh-reopens real M2 with its same M1 and inputs. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | PASS | 3 tests, 0 failures/errors/skips; the reopened M2 aggregate exposes the exact upstream M1 payload reference. |

## Decisions

- This reader mirrors the published identity checks; it creates no new artifact, module, state machine, or compatibility path.

## Blockers

- None.

## Exact next action

- Establish the first `ControlFlowGraphBuilder` RED; its input must be only the sealed M1/M2 aggregates and same reopened inputs.

## Resume checks

- Read this file, run `git status --short`, confirm `854aa94` is an ancestor, then run the direct selector recorded above.
