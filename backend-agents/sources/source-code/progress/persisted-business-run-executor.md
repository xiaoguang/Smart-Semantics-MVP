# Progress: persisted business run executor

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Continue one already-persisted, verified Step 05 Flow publication through business
  materials, reviewed activities, repository knowledge and one reviewed nine-section report.
- Approved inputs: The active business-first Step 06–08 designs; the persisted technical run
  executor; existing four business Modules; the canonical module store; deterministic scripted
  provider tests only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the four business Modules already form a typed in-process workflow from a persisted
  Step 05 Flow reference, and each owns its own durable checkpoint.
- Confirmed that no live model call or source rescan is needed to prove the runtime composition.
- Added `PersistedBusinessRunConfiguration` and `PersistedBusinessRunExecutor`. The executor
  accepts only a persisted `BusinessFlowsReference`, then uses the existing four deep Modules to
  install material, activity, repository-knowledge and report checkpoints in their only valid
  order.
- Added a scripted public-seam runtime test that verifies the continuation produces all four
  checkpoints and a document with exactly nine H2 business sections.

## Current state

- Complete. The executor intentionally starts from a persisted Step 05 publication; a later public
  run coordinator must supply that publication from the existing technical executor rather than
  rebuilding technical analysis.

## Changed files

- `progress/persisted-business-run-executor.md`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunConfiguration.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java`
- `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=PersistedBusinessRunExecutorTest test` | RED | Test compilation failed only because `PersistedBusinessRunExecutor` and `PersistedBusinessRunConfiguration` did not exist. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=PersistedBusinessRunExecutorTest test` | PASS | 1 test, 0 failures/errors/skips; one persisted Flow publication became four checkpoints and one nine-section Markdown through a scripted Provider. |
| `git diff --check -- <owned paths>` | PASS | No whitespace errors in the runtime slice, its test, documentation, or progress record. |

## Decisions

- This composition adds no model semantics: the provider remains the existing injected seam and
  automated verification uses a deterministic scripted provider.

## Blockers

- The public runId-to-report coordinator, CLI, and real fixed-repository capacity/quality
  validation remain future runtime work.

## Exact next action

- Compose the technical executor and this business continuation behind the public
  `RepositoryAnalysisAgent`, then add CLI artifact observation.

## Resume checks

- Confirm the public coordinator gets the Step 05 Flow reference only from a successful technical
  execution and does not expose application-bootstrap paths.
