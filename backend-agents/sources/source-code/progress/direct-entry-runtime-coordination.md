# Progress: direct-entry runtime coordination

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-11
- Scope: Make the one runtime coordinator use the already-approved Step01/Step02 direct-entry
  business-material route for a normal report execution. Preserve the full Step01–05 technical
  executor as an optional technical-analysis capability; do not remove graph, Fact, Flow or
  Capsule code, introduce a second public Agent, or call a Provider.
- Approved inputs: The active design makes Flow/Capsule preferred but not mandatory for
  BusinessMaterialBuilder, and the user explicitly prioritized business semantics over forcing a
  complete technical Flow before the first business report.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the Builder and PersistedBusinessRunExecutor already support matched persisted
  Step01/Step02 input.
- Confirmed the existing RepositoryAnalysisRunCoordinator nevertheless always invokes the full
  Step01–05 executor and forwards only its Flow reference, making the direct mode unreachable for
  an ordinary run.
- Added the coordinator public-seam RED. It fails at test compilation because neither the
  explicit source/discovery technical-prefix result nor the matching coordinator constructor
  exists; this is the intended missing capability, not a parsing or Provider failure.
- Implemented `TechnicalDiscoveryWorkflowResult`, a Step02-only workflow branch, and a matching
  persisted technical-executor entry. Normal internal report coordination now passes that prefix
  to the existing direct-entry business executor; explicit full technical analysis still runs
  Step03–05 from the same prefix.

## Current state

- The direct runtime composition is implemented and verified. Fixed-repository material planning is
  a separate work unit because its acceptance policy registry must be extended before Step01 is
  published; changing it afterwards would violate the source/discovery controls closure.

## Changed files

- `progress/direct-entry-runtime-coordination.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| current runtime inspection | GAP CONFIRMED | Coordinator only accepts a complete `TechnicalAnalysisWorkflowResult` and a `BusinessFlowsReference`. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositoryAnalysisRunCoordinatorTest test` | RED | `TechnicalDiscoveryWorkflowResult` and the two-reference coordinator seam are absent (3 expected test-compile errors). |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositoryAnalysisRunCoordinatorTest,LocalRepositoryAnalysisAgentExecutionTest test` | PASS | 3 tests: normal run forwards persisted inventory/discovery, no Flow required, and public run state remains valid. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=TechnicalAnalysisWorkflowTest test` | PASS | 2 tests: persisted executor can stop after discovery and can still continue through Step05. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -DskipTests spotless:check` | PASS | 580 Java files clean after project formatter updated the seven touched runtime/test files. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The first business-language run is allowed to have `FLOW_NOT_AVAILABLE` limitations. Complete
  program graphs remain a later enhancement rather than a hidden prerequisite.

## Blockers

- None.

## Exact next action

- Start the separate fixed-repository material-planning work unit; it must add the material policy
  before Step01/Step02 publication and make zero Provider calls.

## Resume checks

- The direct path must preserve one run ID, durable checkpoints, no source path in public input,
  no Provider call in technical preparation, and exactly nine Markdown H2 sections when a scripted
  Provider is supplied.
