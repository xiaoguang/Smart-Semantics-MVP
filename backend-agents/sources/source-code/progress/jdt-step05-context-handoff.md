# JDT Step05 context handoff

Status: COMPLETE

## Scope

- Reopen `java-code-index-v1` and assemble one context disposition for every discovered entry.
- Persist those contexts in the existing Step05 flow and Capsule artifacts.
- Allow navigation-only JDT runs to continue without invented strict Flow, Fact, or Proof values.
- Keep the existing strict graph/Fact/Flow route working when technical enhancements are available.

## Starting point

- Task 1.6 completed in commit `b8ad924`.
- Step03 now reopens the selected engine's complete entry contexts.
- Step04 records strict Facts as `NOT_PRODUCED` for the JDT navigation-only path.

## Verification

- Added a public JDT navigation-only execution case that publishes the existing five Step05 files
  with zero invented strict Flows or Proofs. Both discovered entries retain their complete
  `entry-code-context-v1` and each collected context is reachable through one Capsule.
- Added a partial collection case: one JDT query failure remains an explicit entry Gap while its
  collected sibling still produces a Capsule.
- Frozen and exercised the final Step05 schemas: flow compilation v5, capsule projection v10,
  flow slices v5, evidence capsule v8, flow coverage v2, and entry disposition v2.
- The legacy strict graph/Fact path remains executable and uses an explicitly separate optional
  strict technical enhancement rather than masquerading as neutral engine context.
- Direct selector passed on 2026-09-12:
  `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationTest,FlowCompilationModulePublisherTest,CapsuleProjectionModulePublisherTest,BusinessFlowsExecutionTest test`
  (13 tests, 0 failures, 0 errors, 0 skipped).
