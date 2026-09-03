# M4 unsupported consumed return-shape Gap test

## Scope

Add one public-seam `DataFlowGraphBuilderTest` fixture in which the exact,
non-Mapper `AuditClient` boolean return is consumed directly by an `if`
condition rather than a local initializer.

## Contract under test

The direct-condition return shape is outside the installed direct-local return
transfer. M4 must account for it as an entry-owned `DATA_FLOW_BINDING_UNPROVEN`
local Gap at the Java call locator, and must not invent an
`UNKNOWN_BOUNDARY_RETURN` node.

## Status

Complete — clean behavioral RED. Only the fixture, public-seam test, and this
progress record changed; no production, POM, documentation, commit, or push
changes were made.

## Frozen RED

The fixture calls `AuditClient.recordStatus(String)` directly in an `if`
condition. Its public-seam assertion first selects the exact generic audit
boundary, then requires the entry-owned `DATA_FLOW_BINDING_UNPROVEN` Gap at
that boundary's Java call locator and requires no `UNKNOWN_BOUNDARY_RETURN`
node.

Verification run:

```text
mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test
Tests run: 16, Failures: 1, Errors: 0, Skipped: 0
```

The only failure is the new test:

```text
DataFlowGraphBuilderTest.recordsAGapForAConsumedExternalReturnOutsideTheDirectLocalShape
DataFlowGraphBuilderTest.java:1043
Expected size: 1 but was: 0 in: []
```

The empty selection is the required `DATA_FLOW_BINDING_UNPROVEN` Gap. The
audit boundary selection completed first, so this is a narrow missing-Gap RED,
not a fixture, target-resolution, or compilation failure.
