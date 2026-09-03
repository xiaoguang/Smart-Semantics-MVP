# Progress: M5 multi-provenance evidence paths GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Preserve every valid, declared provenance commitment for an admitted M1--M4 program element as a separate rechecked source-plus-rule path in the M5 evidence graph.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` M5 contract, Luna-owned `EvidenceGraphBuilderTest` expected RED, and the four reopened graph drafts plus verified source fixture.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped rules, M5 contract, existing bounded M5 implementation, and Luna RED result.
- Confirmed the exact RED: the builder selects only the first provenance draft and the graph rejects more than one support edge per subject.

## Current state

- The builder emits one source/rule support path for every declared valid provenance, while closure requires one-or-more rather than exactly-one evidence path per subject. Direct and combined M2--M5 selectors are green after formatting. This bounded M5 slice is complete; final graph-set publication remains separate work.

## Changed files

- `progress/m5-multi-provenance-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphDraft.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | Expected RED | 2 tests; the new multi-provenance assertion expected 2 paths and found 1. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | PASS | 2 tests, 0 failures/errors; per-subject coverage is exact and each declared provenance remains a separate rechecked source/rule path. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest test` | PASS | 32 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Project Java formatting applied. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest test && git diff --check` | PASS | 32 tests, 0 failures/errors/skips; no whitespace errors after formatting. |

## Decisions

- A program element may have multiple independently verified source/rule paths; evidence closure requires at least one valid path per evidenced subject and validates every emitted path.
- This slice does not infer extra provenance or business meaning; it only preserves the typed provenance already declared by preceding graphs.

## Blockers

- None.

## Exact next action

1. Begin a separately tested remaining M3--M6 graph contract; do not call this completed M5 slice a complete program-graphs publication.

## Resume checks

1. Re-read this file and `progress/m5-evidence-graph-tests.md`.
2. Confirm the direct M5 selector remains green before extending the evidence wire.
