# M5 boundary evidence tests

## Scope

Add one public-seam `EvidenceGraphBuilderTest` case using the real Mapper M4
boundary fixture and M5 module publish/fresh-reopen path.

## Contract under test

The M5 graph must close generic Java source-and-rule evidence for the Mapper
`JAVA_BOUNDARY_INVOCATION` node and its `ARGUMENT_TO_BOUNDARY` edge. The
invocation path is rooted at the Java service invocation locator and uses
`java-boundary-invocation-v1`; the edge retains the generic boundary-argument
rule. No evidence subject or rule may claim XML/SQL column/placeholder/where
semantics or an external effect.

## Status

Complete — clean behavioral RED. Only this progress record, the public M5
test, and the narrow shared test fixture support needed for M5 publication
changed; no production, POM, documentation, commit, or push changes were
made.

## Frozen RED

The test uses the real M1--M4 Mapper fixture, builds M5, publishes it as module
5, and fresh-reopens it before inspecting evidence. The Mapper boundary node
has the required Java-call `java-boundary-invocation-v1` evidence path. Its
`ARGUMENT_TO_BOUNDARY` edge has no M5 source/rule path carrying
`java-boundary-argument-v1`, so M5 cannot yet close the generic argument-edge
evidence contract.

Verification run:

```text
mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test
Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
```

The only failure is the new test:

```text
EvidenceGraphBuilderTest.reopensGenericJavaEvidenceForTheMapperBoundaryAndItsArgumentEdge
EvidenceGraphBuilderTest.java:244 -> assertGenericBoundaryEvidencePath:320
Expected size: 1 but was: 0 in: []
```

The invocation-node assertion at line 238 and the M5 publish/fresh-reopen both
completed first. The RED therefore isolates the missing generic
`java-boundary-argument-v1` provenance path, rather than an M5 module setup or
boundary-node evidence failure.
