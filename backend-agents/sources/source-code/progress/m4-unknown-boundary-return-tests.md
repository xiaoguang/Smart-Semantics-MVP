# M4 unknown boundary return tests

## Scope

Add one public-seam `DataFlowGraphBuilderTest` slice with a real M1/M2/M3 fixture. A plain Java `AuditClient` returns `boolean`; frozen Java assigns that result to a local and consumes the local in a simple guard.

## Contract under test

For an exact external non-void call whose return is consumed by frozen Java, M4 must retain one generic boundary invocation, create exactly one `UNKNOWN_BOUNDARY_RETURN` node linked to that boundary with `UNKNOWN_EXTERNAL_RETURN`, and connect it by `BOUNDARY_INVOCATION_TO_RETURN` and `BOUNDARY_RETURN_TO_USE` edges. It must not infer a literal, external effect, external parameter transfer, or XML/SQL data flow.

## Status

Complete — clean behavioral RED. This task changed only the fixture, public-seam test, and this record; it did not modify production code, POM, or documentation.

## Frozen RED

The fixture makes the exact non-Mapper call
`AuditClient.recordStatus(String)` return `boolean`, assigns it to `recorded`,
and consumes `recorded` in a Java guard. The new public-seam test requires a
single audit boundary, one `UNKNOWN_BOUNDARY_RETURN` record linked to it, the
two required boundary-return edge families, `UNKNOWN_EXTERNAL_RETURN` state,
and no external parameter or XML/SQL data flow.

Verification run:

```text
mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test
Tests run: 15, Failures: 1, Errors: 0, Skipped: 0
```

The only failure is the new test:

```text
DataFlowGraphBuilderTest.representsAConsumedExactExternalReturnAsAnUnknownJavaBoundaryReturn
DataFlowGraphBuilderTest.java:959
Expected size: 1 but was: 0 in: []
```

That assertion selects the required `UNKNOWN_BOUNDARY_RETURN` node. The exact
Audit boundary had already been built, so the RED isolates the missing return
transfer behavior rather than a fixture, type, or compilation failure.
