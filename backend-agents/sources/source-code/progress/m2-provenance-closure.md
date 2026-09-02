# Progress: M2 provenance closure

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the existing M2 provenance-closure contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Repair the M2 call-graph draft so every node/edge evidence draft reference resolves to one declared `ProvenanceDraftV1`; do not change M2 identity, wire, or call resolution.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, M2/M3 reopening contract, and the observed M3 fresh-reopen failure.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Reproduced the failure through the M3 public seam: a reopened M2 `CALL_SITE` names an evidence draft absent from its persisted provenance registry.
- Added a direct M2 public-seam RED. It identifies two missing AST-call provenance IDs; the only
  declared draft is the Mapper binding provenance.

## Current state

- Closure is now installed at the producing record/builder seam: direct AST call provenance is
  registered before it is referenced, and `CallGraphDraft` rejects every non-closed node/edge
  evidence registry. M3 has re-run successfully against the fresh persisted M2 artifact.

## Changed files

- `progress/m2-provenance-closure.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraft.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | RED | M3 reader fails at an M2 `CALL_SITE` evidence reference with `GRAPH_REFERENCE_BROKEN`; missing M2 provenance closure is the cause. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest#declaresEveryNodeAndEdgeEvidenceReferenceInItsProvenanceRegistry test` | RED | Two direct Java-call provenance IDs are referenced but absent from `CallGraphDraft.provenanceDrafts`; one Mapper-binding draft is present. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest#declaresEveryNodeAndEdgeEvidenceReferenceInItsProvenanceRegistry,CallGraphModulePublisherTest#rejectsACallGraphWhoseEvidenceReferenceIsNotDeclared test` | PASS | 2 tests, 0 failures/errors/skips; producer registers exact AST evidence and draft rejects a non-closed registry. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 1 test, 0 failures/errors/skips; M3 can fresh-reopen the corrected M2 artifact. |

## Decisions

- M3 will not substitute, recreate, or ignore missing M2 evidence. The producer must publish a closed M2 artifact.
- `CallGraphDraft` is the shared parser/producer gate; `PersistedCallGraphReader` rebuilds this
  type from canonical JSON, so a tampered missing reference also fails closed at reopen.

## Blockers

- None.

## Exact next action

- Include this bounded M2 repair in the next local safety checkpoint; no remote code publication
  occurs until the full Program Graphs delivery is accepted.

## Resume checks

- Read this file, verify the docs-only audit commit on `origin/main`, then run the direct M2 selector before and after the minimal repair.
