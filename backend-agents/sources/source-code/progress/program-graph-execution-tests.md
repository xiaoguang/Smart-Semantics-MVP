# Program Graph Execution Test Progress

- Owner: Luna/xhigh (program_graph_execution_tests)
- Scope: P1 RED test for the product ProgramGraphs execution entry only.
- Exact selector: `ProgramGraphExecutionTest`
- Required input contract: fresh-reopened `VerifiedSourceInventoryReference` and `ApplicationDiscoveryReference` only.
- Required behavior: one product entry runs M1–M6 in order with canonical stores, shared controls/profile/basis, reopens intermediate module artifacts, and returns one `ProgramGraphsReference` with exactly eight reader-visible outputs.
- Constraints: frozen fixtures/builders only; no live Git/model/customer build; no production/design/POM changes; no raw draft or builder-input assembly in the test.
- Current state: IN_PROGRESS; repository and implementation seams under inspection before test edit.
- RED command: `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphExecutionTest test`
- RED result: pending.
- Next action: add one public-seam behavior test, run the exact selector, record the expected compile/error RED, then mark COMPLETE.
