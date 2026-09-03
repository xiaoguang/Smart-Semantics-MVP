# Progress: M4 caller parameter to argument GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Implement the bounded M4 transfer from an unshadowed caller method parameter to the existing simple-name `ARGUMENT` node at an activated callsite, including the exact M3 guard/polarity context.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` M4 matrix, Luna-owned `DataFlowGraphBuilderTest` RED, and fresh reopened M1/M2/M3 fixture drafts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the M4 contract, current builder, and the Luna RED.
- Confirmed the expected RED: 9 tests run with one assertion failure because the external Service `status` parameter has no `DEF_USE` edge to the existing guarded setter-call `ARGUMENT` node.

## Current state

- The builder reuses the existing `ARGUMENT`, derives an unshadowed source parameter from the caller method’s verified AST/M1 declaration, and preserves the M3 guard context. Direct and combined M2--M5 regression selectors are green after formatting. This bounded M4 slice is complete; more transfer shapes and final graph-set publication remain separate work.

## Changed files

- `progress/m4-caller-parameter-use-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedDataFlowGraphReader.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Expected RED | 9 tests, 1 assertion failure: the required `DEF_USE` relation is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | Post-change regression | The new edge exists, but the older worklist-prefix assertion and reader endpoint allowlist were incomplete; both root causes were traced before correction. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 9 tests, 0 failures/errors/skips; the reader fresh-reopens the new exact external-parameter-to-argument edge. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest test` | PASS | 34 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Project Java formatting applied. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest test && git diff --check` | PASS | 34 tests, 0 failures/errors/skips; no whitespace errors. |

## Decisions

- `ARGUMENT_SIMPLE_NAME_READ` has no second M4 `USE` node: it uses the existing argument node as the read endpoint, as required by the M4 node/edge matrix.
- A caller parameter is accepted only when the current method contains exactly one parameter with that spelling and no same-named local or nested parameter declaration; conservative non-admission is preferable to a false data-flow fact.

## Blockers

- None.

## Exact next action

1. Begin a separately tested remaining M4 transfer or M6 graph-set publication slice; do not represent this bounded transfer as complete data-flow analysis.

## Resume checks

1. Re-read this file and `progress/m4-caller-parameter-use-tests.md`.
2. Confirm the direct selector still shows the expected absent-edge assertion before modifying production.
