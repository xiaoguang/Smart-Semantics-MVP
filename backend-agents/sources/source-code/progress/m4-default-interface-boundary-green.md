# Progress: m4-default-interface-boundary-green

- Status: COMPLETE
- Agent role: Terra GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make the established M4 default-interface RED green by classifying an interface-declared exact target as outside frozen Java even when JavaParser reports a default-method body.
- Approved inputs: Scoped `AGENTS.md`; both implementation plans; published M4 boundary-v3 contract in `docs/analysis-steps/03-program-graphs.md`; `progress/m4-boundary-v3-first-green-review.md`; `progress/m4-default-interface-boundary-test.md`; and the existing public-seam test.
- Current branch/worktree: `codex/source-analysis-program-graphs`; `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Checked the dirty worktree and preserved all pre-existing M1--M6 and unrelated progress changes.
- Confirmed the contract: interface targets leave frozen Java regardless of a Java default body; only a verified concrete Java method body may receive an internal argument-to-parameter transfer.
- Located the root cause: `Index.hasConcreteFrozenJavaBody` checks only `MethodDeclaration.getBody().isPresent()`, which classifies a default interface method as internal.
- Confirmed the existing no-body Mapper interface follows the generic boundary path; the default-interface fixture is the minimal contrasting RED.
- Changed only `hasConcreteFrozenJavaBody`: a body is concrete for this M4 slice only when its enclosing declaration is not an interface.
- Ran the required targeted selector before and after Spotless; both GREEN runs passed all 13 tests.

## Current state

- Complete. The M4 frozen-body predicate now requires an AST body and excludes methods declared by a `ClassOrInterfaceDeclaration` whose `isInterface()` is true. The existing generic boundary path consequently creates the boundary edge and does not create an argument-to-parameter edge for the default-interface Mapper. M1/M2/M3, XML/SQL handling, unknown-return handling, M5/M6, POM, and authoritative documentation remain unchanged by this task.

## Changed files

- `progress/m4-default-interface-boundary-green.md` (this progress record)
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java` (M4 interface-owner exclusion only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | RED observed by the Luna test handoff | 13 tests; 1 failure: `keepsAnExactDefaultInterfaceMapperMethodAtTheGenericBoundary` expected one `JAVA_BOUNDARY_INVOCATION` and found none. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | GREEN before formatting | Exit 0; 13 tests run, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | GREEN | Exit 0. Spotless reported 291 Java files clean; it formatted the pre-existing untracked `ControlFlowGraphBuilderTest.java`, which is outside this task's owned paths. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | GREEN after formatting | Exit 0; 13 tests run, 0 failures, 0 errors, 0 skipped. |

## Decisions

- The minimal safe classification is: source method exists, has a body, and is not declared by an interface. This causes the existing generic boundary path to create `ARGUMENT_TO_BOUNDARY` and prevents `ARGUMENT_TO_PARAMETER` to the interface formal.

## Blockers

- None.

## Exact next action

- Parent-agent handoff. If retaining a strict task-owned diff, coordinate the formatter-only change to the pre-existing untracked `ControlFlowGraphBuilderTest.java` with that test's owner; this task made no manual test change.

## Resume checks

- Reopen this record, check `git status --short`, and retain the final post-Spotless selector result as the M4 default-interface GREEN evidence.
