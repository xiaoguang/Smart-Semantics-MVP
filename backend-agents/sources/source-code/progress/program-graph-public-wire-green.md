# Progress: Program graph public-wire GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the approved M6 projection of required-nullable frozen-Java data-flow boundary variants into the formal `data-flow-graph.json` wire.
- Approved inputs: Published ProgramGraphs M4/M6 contract, existing real-store `ProgramGraphPublicWireTest` RED, and the M4 `DataFlowNode` records.
- Current branch/worktree: `codex/source-analysis-program-graph-public-wire` at `/private/tmp/linguan-source-analysis-program-graph-public-wire/backend-agents/sources/source-code`

## Completed

- Read scoped Agent guidance, architecture and implementation plans, the M4/M6 design contract, coordinator progress, the full public-wire RED test, and current M4/M6 source mapping.
- Confirmed the defect: `PublicNode` retains only common fields, while `DataFlowGraphWire` already owns the exact variant JSON mapping used by M4 drafts.
- Added the two typed variants to the private M6 `PublicNode`; DATA_FLOW serialization now emits both required-nullable fields and uses the M4 mapping rather than reparsing strings.
- Kept ordinary public graph nodes unchanged and continued to use M5-projected `evidenceNodeIds`; no draft provenance or external-effect field is exposed.

## Current state

- The approved public-wire selector is GREEN. The targeted formal boundary node now contains the structured `boundaryInvocation` record.

## Changed files

- `progress/program-graph-public-wire-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphWire.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | EXPECTED RED (provided) | Public data-flow boundary node lacks `boundaryInvocation`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphWire.java,src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java' spotless:check` | PASS | Both changed production files satisfy the configured formatter. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Reuse M4's package-local typed variant records and add only formal M6 serialization helpers; map no draft provenance fields and add no external-effect inference.

## Blockers

- None.

## Exact next action

- Parent coordinator may review and integrate this bounded M6 GREEN with the existing RED/progress changes.

## Resume checks

- Read this file, coordinator progress, `ProgramGraphPublicWireTest`, and `git status --short` before any follow-up.
