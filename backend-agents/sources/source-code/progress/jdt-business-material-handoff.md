# JDT business material handoff

Status: COMPLETE

## Scope

- Make `BusinessMaterialBuilder` consume persisted engine-neutral Step 05 entry contexts.
- Remove JavaParser source parsing from the JDT material path.
- Carry the same complete methods, calls, arguments, controls, exits, supporting sources, and limitations through the existing activity/process/report workflow.
- Preserve existing public runtime and business-module contracts unless a directly observed handoff defect requires a local correction.

## Approved constraints

- JDT is the selected engine for this phase; JavaParser source remains present but is not invoked on the JDT route.
- Do not rebuild program graphs, Facts, or strict Flows to make code readable.
- Automated verification uses scripted providers only and does not run customer Maven or applications.
- Run only tests directly covering this handoff.

## Changed paths

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialMode.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunConfiguration.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunExecutor.java`
- `src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- `docs/plans/jdt-first-java-engine-implementation-plan.md`
- `progress/jdt-business-material-handoff.md`

## Checks

- The public material seam first failed with `BUSINESS_MATERIAL_INPUT_INVALID` because it could not
  read the persisted neutral `EntryCodeContext`.
- The JDT material now contains complete selected methods, calls, ordered actual/formal
  associations, candidate expansion states, controls, exits, supporting source, and limitations.
- `BusinessMaterialBuilder` contains no JavaParser import, AST parse, or source-level call
  resolution. It verifies persisted source ranges against frozen bytes and only formats the
  engine-neutral context.
- One selected JDT session is now shared across discovery through Step05 when an engine
  configuration is present. The existing no-engine configuration route remains available until
  the separate JavaParser adapter task.
- Direct JDT material test passed on 2026-09-12.
- Direct task selector passed on 2026-09-12:
  `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=BusinessMaterialBuilderTest,TechnicalAnalysisWorkflowTest,PersistedBusinessRunExecutorTest,FourEntryBusinessSemanticChainTest test`
  (13 tests, 0 failures, 0 errors, 0 skipped).
- The additional legacy fallback selector ran 9 tests with 2 assertion failures. Both failing
  assertions require `BusinessMaterialBuilder` to reparse source and synthesize JavaParser-style
  expression observations. That route is intentionally unavailable until Task 9 adapts the
  retained JavaParser engine to the neutral context; the seven remaining fallback/replenishment
  cases passed. The JDT route is not affected.

## Decisions and blockers

- The JDT path does not fall back to the old parser when a source or navigation binding is invalid.
- Navigated entry materials remain separate packets so locally scoped M/C identifiers cannot
  collide across entries.
- No blocker remains for Task 1.9. JavaParser baseline restoration remains the separately approved
  second implementation stage.

## Exact next action

Run Task 1.9 acceptance against the real JDT installation and fixed source, wire the production
configuration/CLI composition root, and release the independent JDT stage only after persisted
materials and the scripted business chain are reopened successfully.
