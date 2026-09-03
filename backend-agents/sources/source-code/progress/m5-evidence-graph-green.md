# Progress: M5 evidence graph GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02T21:32:00Z
- Last updated: 2026-09-02T21:56:36Z
- Scope: Implement the first M5 evidence graph vertical slice: re-open provenance from fresh M1–M4 graph drafts, recheck exact source bytes and hashes, and attach source/rule evidence to every admitted program element.
- Approved inputs: Published M5 contract in `docs/analysis-steps/03-program-graphs.md`, Luna-owned `EvidenceGraphBuilderTest`, and fresh M1–M4 fixture artifacts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the M5 contract and Luna RED test.
- Confirmed the intended RED is test compilation: the public `EvidenceGraphBuilder` seam does not yet exist.
- Added typed evidence nodes, rule applications, support edges, coverage, and the first public
  `EvidenceGraphBuilder` seam. For every admitted M1--M4 element it selects the canonical first
  source-provenance commitment, reopens the matching verified source bytes, recomputes file and
  excerpt SHA-256 values, and creates a source excerpt plus a subject-specific rule node.
- Corrected an implementation-only check that tried to infer node/edge kind from an opaque
  artifact-ID spelling. Node/edge kind now stays structural information supplied by the typed
  M1--M4 draft view; no source relationship is inferred from an ID string.
- The direct M5 selector is GREEN (1 test), and the serial M2/M3/M4/M5 selector is GREEN (29
  tests). Project formatting and `git diff --check` also pass.

## Current state

- The first M5 vertical slice is complete. It deliberately proves one closed, rechecked source/rule
  path per admitted program element; adding additional support paths for multi-provenance elements,
  M5 persistence, and final M6 graph-set publication need separate RED slices.

## Changed files

- `progress/m5-evidence-graph-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphCoverage.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceNodeV2.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceNodeKind.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceEdge.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceEdgeKind.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/RuleApplicationV2.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | Expected RED | Test compilation fails because `EvidenceGraphBuilder` is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | PASS | 1 test, 0 failures/errors/skips; every admitted element has a rechecked source excerpt and a rule node. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Project formatter applied. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest test` | PASS | 29 tests, 0 failures/errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Treat evidence as integrity/reproducibility information, not a Fact or proof.
- The initial vertical slice supplies one canonical closed provenance path per admitted element.
  A later test must expand this to assert the full multi-provenance policy rather than silently
  changing this bounded behavior.
- Fail closed for unknown/duplicate program element ownership, missing provenance, source file/hash/span/excerpt drift, or unreferenced source/rule evidence.

## Blockers

- None.

## Exact next action

1. Begin the next separately tested M4/M5 data/evidence rule or the M6 graph-set publication
   slice; do not describe this bounded builder as complete Stage 03 publication.

## Resume checks

1. Read this file and `progress/m5-evidence-graph-tests.md`.
2. Confirm the direct M5 selector and the 29-test graph selector remain green before extending M5.
3. Re-read the M5 subsection and M5 wire rules in `docs/analysis-steps/03-program-graphs.md`.
