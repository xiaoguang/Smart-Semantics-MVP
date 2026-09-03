# Progress: m4-boundary-v3-first-green

- Status: COMPLETE
- Agent role: Terra production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03 (after formatted targeted GREEN verification)
- Scope: Make only `DataFlowGraphBuilderTest#stopsAnExactMapperCallAtOneGenericBoundaryWithItsJavaLocalArgumentOrigin` green through the smallest M4 v3 generic Java boundary-invocation path.
- Approved inputs: Parent-scoped M4 GREEN task; source-code `AGENTS.md`; published M4 and M4 record contract in `docs/analysis-steps/03-program-graphs.md`; completed M4 boundary RED and its progress record.
- Current branch/worktree: `codex/source-analysis-program-graphs`; `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the required scoped instructions, M4/M4-record contract, pre-existing RED, and its progress.
- Confirmed the worktree has unrelated M1–M6 changes; those remain outside this task.
- Confirmed the current value wire already contains the named boundary record carriers and v3 enum/edge constants, but the builder does not emit them.
- Repaired the required-nullable `DataFlowNode` constructor migration at the four existing builder call sites and the data-flow wire parser, without changing test contracts.
- Added the first M4 v3 generic frozen-Java boundary path: exact activated calls whose M2 target has no concrete same-snapshot Java body now reuse their `ARGUMENT` nodes, emit one `JAVA_BOUNDARY_INVOCATION` record/node, and connect each actual with `ARGUMENT_TO_BOUNDARY`.
- Classified targets before argument transfer so an external call does not retain `ARGUMENT_TO_PARAMETER`; existing concrete Java calls keep their parameter transfer behavior.
- Bound the node record to the exact M2 call-site/target edge and static target triple, direct Java-local origins, M3 basic block/guard context, exact Java invocation locator, and `java-boundary-invocation-v1` provenance. No XML/SQL read or external-effect/return behavior was added.
- Upgraded the M4 draft wire and module-artifact registry to `program-graphs-data-flow-draft-v3`, including the required-nullable boundary/return fields, so persisted M4 graphs preserve the new variant.

## Current state

- The M4 first-green slice is complete. The direct `DataFlowGraphBuilderTest` selector remains green after formatting.

## Changed files

- `progress/m4-boundary-v3-first-green.md` (this task record)
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java` (v3 boundary classification, node/edge/origin/control/provenance identity, and nullable variant call sites)
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphWire.java` (required-nullable v3 variant serialization/parsing)
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphDraft.java` (M4 draft v3 schema constant)
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (M4 draft v3 module-artifact registration)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest#stopsAnExactMapperCallAtOneGenericBoundaryWithItsJavaLocalArgumentOrigin test` | RED | Compiled; the boundary node was initially absent, establishing the requested behavior. |
| `mvn -t .mvn/toolchains.xml -Dtest=DataFlowGraphBuilderTest test` | PASS | 10 tests run, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml spotless:apply` | PASS | Final run made 0 changes. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Reuse existing per-ordinal `ARGUMENT` nodes; attach exactly one `ARGUMENT_TO_BOUNDARY` edge per actual to one generic `JAVA_BOUNDARY_INVOCATION` node.
- Keep all handling inside frozen Java, consuming only M2/M3 and Java-source evidence. Do not inspect XML/SQL or add later M4/M5/M6 behavior.
- Preserved the RED test contract. The constructor migration was repaired only in production: four `DataFlowGraphBuilder` constructors and the `DataFlowGraphWire` parser now populate both required-nullable variant slots with `null` for existing non-variant nodes.
- The M4 v3 module draft schema/registry change is limited to preserving its internal boundary variant; M5/M6/public semantic schemas, unknown returns, non-Mapper fixtures, and ambiguity Gap behavior remain out of scope.

## Blockers

- None.

## Exact next action

- None; hand off the completed first-green slice.
