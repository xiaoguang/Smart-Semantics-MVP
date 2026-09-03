# Progress: m4-default-interface-boundary-test

- Status: COMPLETE
- Agent role: Luna RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Scope: Add one public-seam RED proving that an exact Mapper target declared as a Java interface default method still exits frozen Java as a generic boundary.
- Allowed changes: This progress file, `DataFlowGraphBuilderTest`, and the smallest existing `ControlFlowGraphBuilderTest.Fixture`/source helper variation needed to provide the default-interface Mapper source.
- Prohibited changes: Production, POM, documentation, commits, and pushes.

## Contract target

- M4 v3 classifies every interface target outside frozen Java, even when its method has a Java default body.
- The public M4 draft must therefore contain one `JAVA_BOUNDARY_INVOCATION` and one `ARGUMENT_TO_BOUNDARY`, while the Mapper formal parameter receives no `ARGUMENT_TO_PARAMETER` edge.

## Completed RED

- Added `Fixture.createWithDefaultInterfaceMapper`, reusing the existing controller/service/mapper-catalog path and changing only the Mapper document to a `default void updateStatus(String status) {}` body. The fixture gained one narrow Mapper-source argument; no generic fixture framework was introduced.
- Added `keepsAnExactDefaultInterfaceMapperMethodAtTheGenericBoundary`, which requires one generic boundary node and one argument-to-boundary edge, and forbids an argument-to-Mapper-parameter edge.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | RED | Exit 1; 13 tests run, 1 failure, 0 errors, 0 skipped. Only `keepsAnExactDefaultInterfaceMapperMethodAtTheGenericBoundary` failed: expected one `JAVA_BOUNDARY_INVOCATION` and found `[]` at `DataFlowGraphBuilderTest.java:812`. The failure occurs after test compilation and real M1/M2/M3 fixture setup. |

## Handoff

- Production classification must distinguish interface members from frozen concrete Java bodies so an exact default-interface target takes the existing generic boundary path and never creates `ARGUMENT_TO_PARAMETER` to its formal parameter.
