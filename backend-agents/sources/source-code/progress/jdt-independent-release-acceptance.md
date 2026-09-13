# JDT independent release acceptance

Status: COMPLETE

## Scope

- Exercise the production-selected JDT engine from configuration through discovery, persisted
  navigation, Step05 business context, model-visible material, and the existing scripted business
  report chain.
- Verify registration, financial, and cross-domain source shapes plus explicit JDT failure states.
- Confirm the JDT route neither invokes nor references JavaParser at runtime.
- Do not run a product model or customer build and do not start JavaParser parity work.

## Completed evidence

- Task 1.8 is committed as `b28d572`; model-visible material receives persisted complete JDT method
  and call content without invoking JavaParser.
- The installed JDT LS 1.61.0 and shaded JDT Core helper passed
  `JdtRealSourceCollectionTest`: the fixed jshERP registration entry located
  `validateCaptcha`, `checkLoginName`, and `registerUser`, including the user and tenant insert calls;
  the financial entry reached its Service and Mapper declaration.
- `TechnicalAnalysisWorkflowTest#productionSelectedRealJdtReachesPersistedMaterialAndTheScriptedNineSectionReport`
  passed through capture, discovery, JDT navigation publication, truthful Step04 `NOT_PRODUCED`,
  Step05 context/Capsule, business material, and a scripted nine-section report.
- A recovered JDT annotation binding was incorrectly presented as a resolved local type. The helper
  now accepts only non-recovered bindings; the catalog can then use explicit imports or LS
  navigation. `JdtSyntaxReaderTest` covers the regression.
- Repeated identical JDT limitations could violate the strict EntryContext set invariant. The
  assembler now deduplicates identical limitation records without merging distinct call sites or
  reasons. `BusinessFlowsExecutionTest` covers the regression.
- Model observations now include each method's declaring type so Controller, Service, and Mapper
  ownership remains visible in the actual request. `BusinessMaterialBuilderTest` covers this.

## Verification

- Helper: `mvn -o -Dtest=JdtSyntaxReaderTest test` — 9 tests, all passed.
- Real fixed-source JDT: `mvn -o -t .mvn/toolchains.xml -Dtest=JdtRealSourceCollectionTest test`
  — 1 test, all passed.
- Final combined JDT and public-seam selector — 79 tests, all passed. It includes the Tasks 1–7
  contracts, the installed-JDT source check, the production-selected JDT-to-report path,
  `FourEntryBusinessSemanticChainTest`, `SourceAnalysisCliContractTest`, and
  `RepositoryAnalysisAgentStartTest`.
- `mvn -o -t .mvn/toolchains.xml spotless:check` passed.
- The read-only relative Markdown link check passed for all 13 changed contract/progress files.
- `git diff --check` passed.
- Product model calls, customer builds, and JavaParser parity were not part of this acceptance.

## Decisions and blockers

- No blocker. The independent JDT stage is accepted. JavaParser remains the separate second-phase
  adapter and is not a JDT release condition.
