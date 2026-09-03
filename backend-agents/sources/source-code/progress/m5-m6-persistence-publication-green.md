# Progress: M5 evidence persistence and M6 program-graph publication GREEN

- Status: IN_PROGRESS
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Implement only the established M5 persisted evidence graph handoff and M6 graph-set
  publication seam. M6 may publish only fresh-reopened M1--M5 artifacts; it may not parse source,
  add graph relations, or make facts.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` M5/M6 contracts and the Luna-owned
  `ProgramGraphsPublicationSpecifierTest` compile RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` at
  `/private/tmp/linguan-source-analysis-verified-inventory`.

## Completed

- Read the scoped rules, both approved implementation plans, the M5/M6 detailed design, and the
  current compile RED.
- Confirmed that M6's prior raw-draft test shape was corrected: fifth-graph persistence and fresh
  reopen are prerequisites, not an implementation shortcut.
- Added the M5 canonical evidence-graph wire, receipt-last publisher, opaque reference and fresh
  reopen reader. The reader recomputes the evidence graph from the same reopened M1--M4 inputs
  and verified source bytes before it permits M6 to consume the persisted result.

## Current state

- M5 persistence and M6 graph-set publication are implemented. M6 reopens persisted M1--M5
  artifacts and the two required upstream step publications before projecting the seven formal
  graph payloads, then installs the program-graphs step receipt last.
- The corrected, identity-consistent M1--M5 fixture reaches the public M6 seam. The direct M1--M6
  selector is green with 36 tests. A separate public-wire test now verifies evidence closure,
  graph-index identity/catalog closure, and absence of draft-only fields in the seven semantic
  outputs.
- A design-only review is now resolving the remaining all-graph Gap contract: M1--M3 coverage can
  record a Gap disposition, but only M4 currently carries the reason/locator draft that M6 needs
  for `graph-gaps.jsonl`. Until the shared typed Gap contract is published, M6 fails closed rather
  than losing a non-exact result.
- The Sol/ultra decision is now committed and pushed as `ae83851` before matching code begins.
  It upgrades only M1--M3 internal draft schemas to v3, gives M1--M4 one `GraphGapDraft` carrier,
  and requires M6 to project all valid local gaps one-to-one.
- M1, M2 and M3 now emit the same v3 local Gap carrier; M4 already used that carrier. Each
  carrier records the reason, affected entry, candidate element and verified source location,
  and its identity and coverage mapping are rechecked when the module is reopened.
- M6 now projects valid local carriers from all four static graphs into `graph-gaps.jsonl` without
  parsing source or adding graph relations. The real status-loop fixture proves that one M3 Gap
  survives M1--M5 persistence into the formal JSONL and graph index unchanged.
- The M3 storage registry had one stale v2 control-flow schema literal. It is now corrected to
  the published v3-only contract; the end-to-end Gap test reaches M6 and passes.

## Changed files

- `progress/m5-m6-persistence-publication-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphWire.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphDraftReference.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedEvidenceGraphReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ReopenedEvidenceGraph.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationInputs.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsReference.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicAnalysisStepPublicationEngine.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest test` | RED | Five missing M5/M6 public seam types; test execution not reached. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest test` | RED | M5 public seams compile; only `ProgramGraphSetPublicationSpecifier` remains missing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsAnalysisStepArtifactStoreTest test` | PASS | One test verifies the exact seven M6 payloads and ordered source-inventory/application-discovery upstreams. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest test` | BLOCKED | Store test passes; M6 integration fixture mixes its source controls with a different policy registry, and fails at M5 before the specifier is reached. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest,EvidenceGraphBuilderTest,DataFlowGraphBuilderTest,ControlFlowGraphBuilderTest,CallGraphBuilderTest test` | PASS | 36 tests, 0 failures/errors/skips; real M1--M5 reopen and M6 publication are covered. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | PASS | One test verifies public evidence and graph-index closure, catalog completeness, and draft-field exclusion. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Production and direct-test files formatted. |
| `git diff --check` | PASS | No whitespace errors in current worktree. |
| `git push origin HEAD:main` | PASS | Docs-only commit `ae83851` pushed before matching code. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphGapCarrierTest test` | PASS | One test confirms M3 v3 carrier identity, exact locator and terminal/coverage closure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphGapCarrierTest test` | PASS | One test confirms M2 v3 source-located carrier and coverage closure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest test` | PASS | One test confirms M1 v3 source-located carrier and coverage closure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | PASS | A real M1--M5 status-loop fixture proves M6 projects the M3 carrier to one formal JSONL row and closes the index without changing any graph nodes or edges. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphGapCarrierTest,CodeStructureGraphModulePublisherTest,CallGraphBuilderTest,CallGraphGapCarrierTest,CallGraphModulePublisherTest,ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,PersistedProgramGraphInputReaderTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest,ProgramGraphPublicWireTest,ProgramGraphGapProjectionTest test` | BLOCKED | 57 tests: 53 passed; M1 rejects two valid repository-level parse Gaps because the shared carrier requires a nonempty entry list, and two existing M6 public tests then fail with `GRAPH_REFERENCE_BROKEN`. A Sol/ultra design correction is in progress; no code repair starts before its docs-only publication. |

## Decisions

- M5 persistence is an independent module receipt; M6 consumes only its reopened result.
- M6 only transforms validated drafts into formal graph payloads and index/gap projections. It
  does not treat a raw input list as a source of unverified information.

## Exact next action

1. Await the docs-only repository-scope Gap decision and its required push to `origin/main`.
2. From a fresh RED, implement only the resulting M1/M6 carrier correction, then repeat the
   same serial selector before any M3 maturity claim.

## Resume checks

1. Re-read this file and `progress/program-graphs-publication-tests.md`.
2. Confirm all five inputs are persisted/reopened before allowing M6 publication.
3. Run only the M6 selector until it is green, then the direct graph selector and formatter.
