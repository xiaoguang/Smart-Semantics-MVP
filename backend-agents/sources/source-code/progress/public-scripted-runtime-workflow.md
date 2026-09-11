# Progress: public scripted runtime workflow

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh (production), following Sol/ultra design and Luna/xhigh TDD contract
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Prove the public `RepositoryAnalysisAgent` executes the saved technical prefix through Flow/Capsule material and the four business modules in one scripted end-to-end run.
- Approved inputs: Existing frozen synthetic Git fixture, existing canonical stores, scripted Provider only; no customer build, network capture, or live model call.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Identified that the current public coordinator invokes only source inventory and application discovery, then uses the source-entry fallback material route.
- Confirmed the intended full technical executor already exposes the persisted Step 01–05 result, including `BusinessFlowsReference`.
- Added a public-Agent end-to-end test using a captured synthetic Java/Spring/MyBatis repository and the scripted Provider.
- Rebound normal final-document execution to persisted Step 01–05 Flow/Capsule output. Materials-only planning remains the sole Step01/02 direct-entry route.
- Updated durable runtime documentation to distinguish the two execution paths.

## Current state

- The public final-document path now produces `FLOW_PREFERRED` material for the synthetic captured repository. This confirms the technical handoff, not business-language quality.

## Changed files

- `progress/public-scripted-runtime-workflow.md`
- `src/main/java/org/sourceanalysis/app/runtime/RepositoryAnalysisRunCoordinator.java`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- `docs/DESIGN.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn ... -Dtest=TechnicalAnalysisWorkflowTest#publicFinalDocumentExecutionUsesPersistedFlowsRatherThanTheDiscoveryFallback test` | RED then PASS | RED showed `ENTRY_SOURCE_FALLBACK`; GREEN uses persisted `BusinessFlowsReference` and emits `FLOW_PREFERRED`. |
| `mvn ... -Dtest=TechnicalAnalysisWorkflowTest,RepositoryAnalysisRunCoordinatorTest,LocalRepositoryAnalysisAgentExecutionTest,PersistedBusinessRunExecutorTest test` | PASS | 11 tests, 0 failures/errors/skips. |
| `mvn ... spotless:apply` (two changed Java files) | PASS | Targeted Java formatting applied. |
| `git diff --check -- <owned paths>` | PASS | No whitespace errors. |

## Decisions

- This is an execution-path correction, not a rewrite of the stable technical steps: it reconnects their already-persisted Flow/Capsule output to the business modules.
- The correction exposed the next quality gate: `FLOW_PREFERRED` technical material must be understandable enough for Luna to explain a business activity. That is the next work unit, not an excuse to extend technical Proof machinery.

## Blockers

- None for scripted fixture development. A complete fixed jshERP object store remains required only for real-repository acceptance.

## Exact next action

- Start a separate material-quality work unit: write a public test for concise input/guard/call/result observations in a `FLOW_PREFERRED` packet, then improve only the material presentation seam.

## Resume checks

- Read this file; the next work unit begins at `BusinessMaterialBuilder.observations(Capsule)` and must not modify frozen source, graph, Fact, Proof, Flow, or Capsule contracts.
