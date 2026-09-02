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

- Awaiting the docs-only current-audit publication. The next change is one M2 public-seam RED that asserts the declared registry exactly closes all node and edge evidence references.

## Changed files

- `progress/m2-provenance-closure.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | RED | M3 reader fails at an M2 `CALL_SITE` evidence reference with `GRAPH_REFERENCE_BROKEN`; missing M2 provenance closure is the cause. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest#declaresEveryNodeAndEdgeEvidenceReferenceInItsProvenanceRegistry test` | RED | Two direct Java-call provenance IDs are referenced but absent from `CallGraphDraft.provenanceDrafts`; one Mapper-binding draft is present. |

## Decisions

- M3 will not substitute, recreate, or ignore missing M2 evidence. The producer must publish a closed M2 artifact.

## Blockers

- No design ambiguity. A docs-only current-audit update is in progress before production repair.

## Exact next action

- Add the M2 provenance-closure RED after the audit is published, then minimally fix the M2 producer/reader contract.

## Resume checks

- Read this file, verify the docs-only audit commit on `origin/main`, then run the direct M2 selector before and after the minimal repair.
