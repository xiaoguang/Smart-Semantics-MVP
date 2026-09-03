# Progress: m4-java-boundary-invocation-green

- Status: IN_PROGRESS
- Agent role: Terra production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03 (before production edits)
- Scope: Make the existing public `DataFlowGraphBuilderTest` Mapper-boundary RED green with one exact generic Java boundary invocation. Preserve Java-local argument origin and M3 guard/control context; do not infer XML/SQL effects or implement later M4/M5/M6 slices.
- Approved inputs: Parent-scoped M4 GREEN task; root and source-code `AGENTS.md`; both source-code implementation plans; published `docs/analysis-steps/03-program-graphs.md` JavaBoundaryInvocation v3 contract; the completed M4 RED and its progress record.
- Current branch/worktree: `codex/source-analysis-program-graphs`; `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed the worktree contains unrelated in-progress M1–M6 changes and will preserve them.
- Read the M4 RED and confirmed its established failure is the missing `JAVA_BOUNDARY_INVOCATION` node for one exact Mapper call.

## Current state

- Production implementation has not been changed.
- The intended vertical slice is one generic boundary node with ordered existing arguments, M3 guard/polarity, Java call provenance/rule, and no XML/SQL expansion.

## Changed files

- `backend-agents/sources/source-code/progress/m4-java-boundary-invocation-green.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Not run by this agent yet | PENDING | Production change has not started. |

## Decisions

- Do not add XML parsing, SQL nodes/edges, unknown return handling, non-Mapper genericity, ambiguous target behavior, M5, or M6 behavior.
- Preserve the single public-seam test and all pre-existing passing selector behavior.

## Blockers

- None currently.

## Exact next action

- Finish tracing the published M4 contract and the existing builder/data-flow value seams, then make the smallest contract-preserving production change.

## Resume checks

- Change only task-owned progress and narrowly necessary production graph types/builder behavior.
- Run only `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest test`, then `mvn -t .mvn/toolchains.xml spotless:apply`, then the same selector again.
