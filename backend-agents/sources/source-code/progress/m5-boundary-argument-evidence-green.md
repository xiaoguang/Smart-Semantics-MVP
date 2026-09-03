# Progress: M5 boundary-argument evidence GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Close the established generic Java source/rule evidence path for each M4 `ARGUMENT_TO_BOUNDARY` edge only, preserving the existing boundary-invocation evidence path and M5 publish/fresh-reopen behavior.
- Approved inputs: `AGENTS.md`; both `docs/plans/*`; `docs/analysis-steps/03-program-graphs.md` M4/M5 boundary contract; `progress/m5-boundary-evidence-tests.md`; and the established `EvidenceGraphBuilderTest` RED.
- Current branch/worktree: shared source-code worktree at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`.

## Completed

- Confirmed the dirty worktree contains unrelated concurrent M1--M6 work and will preserve it.
- Read the M4/M5 boundary contract and exact M5 public-seam RED.
- Traced the current closure: the M4 boundary edge currently carries invocation/call/argument provenance but no dedicated `java-boundary-argument-v1` provenance token, so M5 has no matching source/rule support path to reopen.
- Reproduced the established RED: the exact selector ran 3 tests with one failure at the expected edge-only assertion; the boundary-node path and M5 publish/fresh-reopen completed first.
- Added the edge-specific `java-boundary-argument-v1` source provenance at the existing Java invocation span and referenced it from each M4 `ARGUMENT_TO_BOUNDARY` edge.
- Re-ran the exact public M5 selector after formatting: all three tests pass, including build, module publication, and fresh reopen of the generic edge evidence path.

## Current state

- The M4 boundary node retains its existing `java-boundary-invocation-v1` evidence path. Every generic `ARGUMENT_TO_BOUNDARY` edge now also carries recheckable Java-call provenance under the registered `java-boundary-argument-v1` rule; M5 derives a distinct subject-specific rule node and support edge from it without inferring XML/SQL or external effects.

## Changed files

- `progress/m5-boundary-argument-evidence-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilderTest.java` (Spotless-only formatting of the concurrently supplied RED; no semantic test edit by this task)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | Expected RED | 3 tests, 1 failure, 0 errors. Only `reopensGenericJavaEvidenceForTheMapperBoundaryAndItsArgumentEdge` failed at line 244 because no `java-boundary-argument-v1` path was emitted. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | Passed | Formatter completed successfully; it also formatted the concurrently maintained `EvidenceGraphBuilderTest` file without a semantic test edit by this task. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | Passed | 3 tests, 0 failures, 0 errors, 0 skipped; the Mapper boundary node and its argument edge both close through build, publish, and fresh reopen. |

## Decisions

- Do not add XML/SQL evidence, external-effect semantics, technology-specific rules, M6/public-version changes, tests, POM changes, commits, or pushes.
- Preserve the existing `java-boundary-invocation-v1` evidence path; add only the missing edge-specific generic rule provenance.

## Blockers

- None.

## Exact next action

- Hand the bounded GREEN slice to the parent. Do not extend M4/M5 provenance, add XML/SQL/external-effect semantics, or change M6/public versions in this work unit.

## Resume checks

- Re-read this record and `progress/m5-boundary-evidence-tests.md` before any later M4/M5 evidence change; preserve the separate invocation and argument rule paths.
