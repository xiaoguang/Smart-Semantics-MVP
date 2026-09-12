# Progress: Java analysis engine plugin design

- Status: COMPLETE
- Agent role: Main designer
- Model: gpt-6-astra / ultra
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Source-scoped design, module contracts, examples and development guidance only.
- Approved inputs: User-approved JDT-only Java analysis route, preserved selectable JavaParser route, YAML configuration, existing frozen research outputs and current source.
- Current branch/worktree: codex/jdtls-source-navigation-feasibility / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read scoped rules and existing design/module seams; preserved pre-existing staged research changes.
- Confirmed that the present research mixes JDT navigation and JavaParser syntax extraction; it is not yet a production engine plugin.
- Wrote the engine overview, complete common contract/configuration, JDT submodules, integration/JavaParser module guide and real-source walkthrough.
- Incorporated the user's two-phase sequence: JDT first without JavaParser compatibility constraints; JavaParser current-capability adaptation second.
- Synchronized architecture, affected step designs, scoped rules, navigation and prompt guidance; no production/resource/schema edits.
- Sol/ultra independently confirmed the syntax-only Core helper + LS-only binding design and tool-JVM/source-offset constraints.

## Current state

- Complete. Independent Sol/ultra review confirmed architecture and identified three precise contract issues; all were corrected: annotation identity owner/name selection, distinct call navigationSite, and set-valued candidate roles/navigationKinds.
- Final documentation checks passed; no implementation, build, source scan or product generation started.

## Changed files

- progress/java-analysis-engine-plugin-design.md
- docs/modules/java-code-engines/{README,contracts-and-configuration,jdt-engine,integration-and-javaparser}.md
- docs/examples/java-code-engine-walkthrough.md
- docs/DESIGN.md; docs/analysis-steps/02–08; README.md; AGENTS.md
- docs/references/{inherited-public-and-module-contracts,semantic-interpretation-prompts}.md
- docs/examples/semantic-framework-walkthrough.md
- docs/plans/coherent-code-context-implementation-plan.md (historical scope/navigation only)
- docs/supplements/program-graphs-implementation-backlog.md (preserved graph scope/navigation)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | PASS | Existing staged/untracked research work preserved; no production edits made. |
| Node document links / JSON / exact-source checks | PASS | Final 20 docs: 134 local links, 2 new anchor links, 5 valid JSON examples; 7 source blocks exactly match saved packets. |
| git diff --check | PASS | No whitespace errors in the working diff. |

## Decisions

- Documentation only; no builds, source scans, product model calls, commits or pushes.
- Keep one downstream business chain; no automatic engine fallback, compiler reimplementation or new recovery subsystem.
- Preserve JavaParser algorithms and five-graph/Fact capabilities as optional enrichment, never a mandatory JDT source-reading gate; absent enrichment must not masquerade as empty successful graphs.

## Blockers

- None.

## Exact next action

- User reviews the complete engine design and walkthrough. A later explicitly requested implementation begins with JDT only; JavaParser current-capability adaptation follows as a separate second phase.

## Resume checks

- Read this file, git status and the new plugin design before resuming; distinguish existing research edits from this task's docs.
