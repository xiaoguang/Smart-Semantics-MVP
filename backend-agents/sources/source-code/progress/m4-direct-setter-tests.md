# M4 direct setter RED test

Status: COMPLETE (RED established)

Scope: add one behavior-at-a-time RED test for the M4 direct activated setter
contract from `docs/analysis-steps/03-program-graphs.md` §§8.3 and 8.6. The
test will use a fresh reopened M1/M2/M3 fixture and assert the target body's
formal-ordinal-0 read, exact M1 field write, caller argument-to-field setter
edge, and shared M3 guard context. A bounded non-direct setter case will be
covered only if it remains one behavior seam.

Changed files planned:

- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
  (minimal direct-setter/entity fixture factory and source documents)
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
  (M4 RED assertions)

Verification:

`mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test`, then
`git diff --check`.

## Result

The canonical fixture was minimally extended with a `DepotHead` M1 type,
`status` field, and guarded `depotHead.setStatus(status)` call. A second
fixture uses the same activated call but transforms the setter formal with
`status.trim()`. `DataFlowGraphBuilderTest` now asserts the direct setter
formal→USE→M1 FIELD `DEF_USE`/`ASSIGNMENT` chain, caller ARGUMENT→M1 FIELD
`SETTER_TO_PROPERTY`, shared TRUE guard metadata, direct-setter work item
accounting, and a typed Gap/no-edge negative for the transformed setter.

Changed files in this work unit:

- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `progress/m4-direct-setter-tests.md`

The required RED command compiled the test class and failed only the two new
direct-setter behavior assertions because the current M4 builder has no direct
setter transfer or typed direct-setter Gap implementation:

```text
$ mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test
[INFO] Running org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest
[ERROR] Tests run: 8, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 2.782 s <<< FAILURE! -- in org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest
[ERROR] org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest.emitsDirectActivatedSetterTransferFromFormalToItsExactM1Field -- Time elapsed: 0.235 s <<< FAILURE!
java.lang.AssertionError:

Expected size: 1 but was: 0 in:
[]
	at org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest.single(DataFlowGraphBuilderTest.java:804)
	at org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest.emitsDirectActivatedSetterTransferFromFormalToItsExactM1Field(DataFlowGraphBuilderTest.java:173)

[ERROR] org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest.recordsABoundedGapForAnActivatedSetterWhoseBodyTransformsTheFormal -- Time elapsed: 0.194 s <<< FAILURE!
java.lang.AssertionError:

Expecting any element of:
  []
to satisfy the given assertions requirements but none did:


	at org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest.recordsABoundedGapForAnActivatedSetterWhoseBodyTransformsTheFormal(DataFlowGraphBuilderTest.java:270)

[ERROR] Tests run: 8, Failures: 2, Errors: 0, Skipped: 0
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.5:test (default-test) on project source-code-analysis-agent: There are test failures.
[ERROR] -> [Help 1]
Process exited with code 1.
```

`git diff --check` completed with exit code 0 and no output.
