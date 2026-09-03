# M4 non-Mapper boundary genericity test

## Scope

Add one public-seam `DataFlowGraphBuilderTest` case using the existing real M1/M2/M3 fixture pipeline. The fixture will invoke both the existing Mapper interface and a plain Java `AuditClient` interface, so the assertion can compare their boundary records and argument edges without inspecting implementation details.

## Contract under test

An exact external Java call is technology-neutral: the non-Mapper call must produce a `JAVA_BOUNDARY_INVOCATION` carrying `java-boundary-invocation-v1` and an `ARGUMENT_TO_BOUNDARY` edge, with no XML/SQL data-flow nodes or edges. Its kind and rule family must match the Mapper call, differing only in static target identity.

## Status

Complete — independent GREEN. No production code, build configuration, or documentation was modified.

## Result

`DataFlowGraphBuilderTest#representsAnExactNonMapperClientCallWithTheSameGenericBoundaryShape`
uses a real fixture containing both `DepotHeadMapper.updateStatus(String)` and
`AuditClient.recordStatus(String)`. The public M4 draft exposes one generic
`JAVA_BOUNDARY_INVOCATION` for each call. The AuditClient record has the same
boundary and argument-edge rule family and Java-local argument origin shape as
the Mapper record, while retaining its own static target identity. It also has
no `ARGUMENT_TO_PARAMETER` transfer and no XML/SQL data-flow shape.

Verification run:

```text
mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
