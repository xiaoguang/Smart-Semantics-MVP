# JDT navigation index publication

Status: COMPLETE

## Scope

- Publish the engine-neutral JDT code index as Program Graphs module 7.
- Make the JDT Step03 public artifact set contain the navigation index and receipt only.
- Publish truthful Step04 `NOT_PRODUCED` accounting when strict graph enrichment is absent.
- Preserve the existing graph/fact path when an engine actually supplies validated enhancements.

## Progress

- Task 1.5 completed and committed as `94778e7`.
- Registered Program Graphs module 7 and the canonical `java-code-index-v1` JSONL artifact.
- Added the module/step publisher and fresh reader for ENGINE, TYPE, METHOD, CALL,
  ENTRY_MEMBERSHIP, and DIAGNOSTIC records.
- Added the selected-session execution path. It collects every persisted Step02 entry and never
  runs the legacy JavaParser graph builders.
- Added the accounting-only Step04 route. It writes `availability=NOT_PRODUCED`, a concrete
  reason, the navigation input reference, and null counts without running Fact enumeration.
- Preserved the existing module-6 graph publication and four-file strict Fact route.

## Verification

- `mvn -o -t .mvn/toolchains.xml -Dtest=JavaCodeIndexPublicationSpecifierTest test`: PASS, 1
  test, 0 failures/errors/skips.
- `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml
  -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsExecutionTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphsAnalysisStepArtifactStoreTest,ProvenCodeFactsExecutionTest,ProvenCodeFactsPublicationSpecifierTest,BusinessFlowsPublicationSpecifierTest,CanonicalArtifactPolicyRegistryContractTest
  test`: PASS, 14 tests, 0 failures/errors/skips.
- The direct fixture fresh-reopens one Step03 semantic payload named `java-code-index.jsonl` and
  one Step04 semantic payload named `fact-accounting.json`; no placeholder graph, Fact, or Proof
  file is installed.
