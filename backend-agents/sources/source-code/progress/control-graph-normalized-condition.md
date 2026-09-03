# Progress: Control graph normalized condition

- Status: COMPLETE
- Agent role: Terra/xhigh implementation coordinator
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement only the published control-flow v2 field `normalizedCondition` for supported Java `if` guards, its canonical module/public projection, and directly covering tests. No Fact, Flow, Provider, customer source, or runtime changes.
- Approved inputs: Published design correction ecbdabf; docs/DESIGN.md; docs/analysis-steps/03-program-graphs.md; target standards/toolchain plan; frozen graph fixtures.
- Current branch/worktree: codex/source-analysis-control-graph-guard-condition at /private/tmp/linguan-source-analysis-control-graph-guard-condition/backend-agents/sources/source-code

## Completed

- Read the published Step03/04 correction and current ControlFlow graph builder, wire, public projection and direct fixture tests.
- Completed the first public-wire vertical slice: a supported Java `if` guard supplies the dedicated normalized condition in the draft and the public control graph v2.
- Published the required follow-up design correction `a2ea789`: the internal draft payload is now v4 because that added field changes its canonical bytes.

## Current state

The implementation emits only the exact v4 draft. The strict writer, reader and policy gate use the shared schema constant, so v3 has no reader or conversion path. The direct graph/fresh-reopen/fact-input selector is green.

## Changed files

- progress/control-graph-normalized-condition.md
- src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphGapCarrierTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphGapCarrierTest test` (before schema change) | EXPECTED RED | 1 test failed: expected v4, producer emitted v3. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphGapCarrierTest test` (after schema change) | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphPublicWireTest,ProgramGraphsExecutionTest,PersistedProgramGraphInputReaderTest,FactCandidateExactPathTest test` | PASS | 24 tests, 0 failures/errors/skips; verifies condition extraction, internal/public wire, fresh reopen, M6 publication and Fact input reopening. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=<changed Java files> spotless:check` | PASS | All files in this delivery are formatted. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | BASELINE BLOCKER | 39 pre-existing source files outside this delivery violate format; no broad formatting rewrite applied. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The fixed JavaParser AST condition `toString()` is used once in the ControlFlow builder; downstream consumers do not parse canonicalValue.

## Blockers

- None.

## Exact next action

Commit and push this complete program-graphs delivery, then resume the already prepared Fact work from the new main baseline.

## Resume checks

- Re-read this file, verify HEAD includes ecbdabf, and rerun the direct selector after any code change.
