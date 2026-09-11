# Progress: coherent code context implementation

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh for production changes; direct TDD verification in the current task
- Started: 2026-09-11 10:16 UTC
- Last updated: 2026-09-11 11:28 UTC
- Scope: Implement the approved coherent Step05 context, Capsule projection, BusinessMaterialBuilder handoff, normal-path replay reduction, Spring method condition support, and direct business-chain regression checks.
- Approved inputs: User-approved "连贯代码材料到九章业务文档：现有实现修正计划"; current target design and frozen fixtures only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed this is an isolated linked worktree and recorded pre-existing documentation-only changes from the preceding design refresh.
- Read the approved implementation plan, target Step05 contract, relevant runtime composition, Provider diagnostic record, and applicable Agent rules.
- Confirmed live model generation is outside the first three implementation stages; automated tests use frozen fixtures and scripted Provider only.
- Added a public-seam RED test for one guarded Controller → Service → boundary-client path.
- Ran the direct selector: it fails only because the packet lists independent calls rather than the controller-to-service argument handoff and service-to-boundary relationship. Source snippets are present; no test or production compilation error occurred.
- Implemented the Step05-owned `EntryContext`: its persisted shape holds the entry/flow identity, exact call relationships, formal argument types, boundary flag, controls, return paths, admitted fact IDs, source evidence IDs and limitations.
- Projected that context through compiler publication, Capsule projection and public flow publication. The normal publishers now validate and serialize their owned result instead of ordinarily recompiling/projecting it.
- Updated BusinessMaterialBuilder to read the saved Capsule context and emit connected technical observations while leaving short source snippets in the model packet.
- Re-ran the original public-seam selector successfully: 1 test, 0 failures, 0 errors. It now observes `OrderController → OrderService`, `OrderService → ApprovalClient` at a Java boundary, the formal `java.lang.String` argument type, the `status == null` guard, a return path, and source snippets containing the actual `status` variable.
- Added frozen source locators to every persisted EntryContext and the originating evidence-node identity to every projected model span. The public Step05 schemas are now v4/v8/v4/v6 for compilation/projection/flow-slices/capsule lines; old shapes cannot silently enter the new reader path.
- Replaced publisher replays with a light structural closure check: a span must retain the hash-derived identity of its owning Flow and evidence node. The existing cross-Flow span swap and bare-evidence-node mutation regression now pass without reprojecting the capsule.
- Added an explicit public `flow-slices.json` assertion: a published EntryContext contains a controller-to-service call, formal argument type and fixed source locators.
- Removed the Builder's source/discovery-only fallback that re-parsed Java and guessed a direct callee from field type/name/arity. Production material generation now requires published Step05 output; no-strict-Flow entries retain readable material through their saved EntryContext.
- Updated the production material-planning continuation to execute the completed technical Step05 before it builds material. Legacy source/discovery test seams remain isolated from the production constructor pending the later runtime cleanup phase.
- Corrected the ownership boundary exposed by the long-method regression: EntryContext calls and controls now come directly from persisted Call/Control graph relationships, not from the three strict Fact kinds. Facts remain optional proved conclusions. A persisted graph Gap with a safe locator is retained as a limitation and source location, so the Builder can show code near an unresolved target without inventing one.
- Confirmed the long method retains its `orderMapper.update(record)` source observation even when no Fact exists for that call. Raised the bounded syntax-outline observation cap from 12 to 24 so a coherent entry method does not lose a later persistence call after retaining its necessary inputs, calls and guards.
- Ran the direct Step05-to-material regression set successfully (38 tests, 0 failures, 0 errors) before final formatting. Applied Spotless only to the four Java files changed after the preceding format run; final Spotless check and `git diff --check` pass.
- Updated the Step05/Step06/overall design and scoped Agent rule: Fact/Proof classification must not filter persisted graph context needed for business reading.

## Current state

- Phase 1 is complete: persisted Step05 context is the sole production handoff to business materials, it contains direct graph-derived relationships and source locators, and the Builder only packages it. The next approved delivery is Phase 2: Spring MVC method-condition compatibility plus the remaining ordinary-path Fact reopen de-duplication.

## Changed files

- progress/coherent-code-context-implementation.md
- src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java
- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java
- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java
- src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BuildBusinessMaterialsRequest.java
- src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java
- src/main/java/org/sourceanalysis/app/runtime/RepositoryAnalysisRunCoordinator.java
- src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerDirectEntryContextTest.java
- src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing design Markdown changes only before this progress file; no implementation edits yet. |
| linked-worktree inspection | PASS | Branch `codex/source-analysis-business-flows-closeout` uses its own Git worktree metadata. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=BusinessMaterialBuilderTest#keepsCallerArgumentsBoundaryAndSourceTogetherInOneModelPacket test` | Expected RED | 1 test, 1 assertion failure, 0 errors: packet has snippets but lacks a connected handoff/boundary observation. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=BusinessMaterialBuilderTest#keepsCallerArgumentsBoundaryAndSourceTogetherInOneModelPacket test` | PASS | 1 test, 0 failures, 0 errors: the packet now holds the connected two-call path, guard, return path and snippets. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CapsuleProjectionModulePublisherTest test` | PASS | 4 tests, 0 failures, 0 errors: legitimate projection and Flow-rooted span mutation rejection pass without replay. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest,FlowCompilationModulePublisherTest,EvidenceCapsuleProjectorTest,CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest,BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest test` | PASS | 34 tests, 0 failures, 0 errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessMaterialBuilderFallbackTest,ActivityExplainerDirectEntryContextTest,PersistedBusinessRunExecutorTest,RepositoryAnalysisRunCoordinatorTest test` | PASS | Direct Step05-material and runtime-continuation selectors passed after source/discovery fallback removal. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowsPublicationSpecifierTest#installsTheExactFiveSemanticFilesAfterJoiningEveryPersistedFlowAndCapsule test` | PASS | 1 test, 0 failures, 0 errors: public entry context is observable in flow-slices.json. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=EntryRootedFlowCompilerTest,FlowCompilationModulePublisherTest,EvidenceCapsuleProjectorTest,CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest,BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest,ActivityExplainerDirectEntryContextTest,PersistedBusinessRunExecutorTest,RepositoryAnalysisRunCoordinatorTest test` | PASS | 38 tests, 0 failures, 0 errors: direct graph context, publication, material packing and runtime continuation agree. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 589 Java files clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Step05 is the sole owner of connected entry context. It reads persisted Call/Control graph relations directly; the Builder only packages that context and maps short source refs.
- Existing Activity, Process, Report, runtime, CLI and Provider records are preserved unless a direct regression exposes content loss or an actual integration defect.
- The Java-to-Codex child-process diagnostic is deferred until the scripted end-to-end chain is green; no customer source or live Provider request will be retried automatically.
- The strict source/Proof objects remain intact, but their evidence-node identity is used only for lightweight ownership checking at publication; it is not included in the model packet.

## Blockers

- None.

## Exact next action

- Begin Phase 2 with a Luna RED proving that Spring `@RequestMapping` with an omitted or empty `method` is a legal unrestricted condition rather than a missing-method Gap; then make the smallest discovery implementation change and run only its direct selector.

## Resume checks

- Read this file and `progress/evidence-for-business-design-coordination.md`, then inspect `git status --short` and run only the direct Maven selector recorded by the next test cycle.
