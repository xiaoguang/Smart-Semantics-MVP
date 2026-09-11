# Progress: persisted activity launch limit

- Status: COMPLETE
- Agent role: Root implementation agent; runtime handoff for bounded model work
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Thread the already-tested `maxMaterialsToStart` bound through the persisted business executor and its internal workflow. This makes the bound usable before a fixed-repository model run without changing source capture, material construction, business semantics, process grouping, or the public Agent interface.
- Approved inputs: User instruction to start with a small model package; scoped `AGENTS.md`; active Step 06 design; completed activity execution-budget slice.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the internal `ActivityExplainer` can now enforce the cap, but `PersistedBusinessRunExecutor` currently uses the legacy unlimited convenience request and would not expose the protection to a configured run.
- Added the launch cap to `PersistedBusinessRunConfiguration`, retaining its prior four-argument
  fixture constructor as an unlimited convenience overload.
- Threaded the cap through `PersistedBusinessRunExecutor` and `BusinessAnalysisWorkflow` into
  `ExplainActivitiesRequest`; a direct source/discovery run with cap one now produces one reviewed
  activity and one `NOT_ANALYZED_EXECUTION_CAPACITY` entry.
- Updated Step 06's failure table and test guidance, plus the overall implementation audit.

## Current state

- The runtime handoff is green. The remaining work is bootstrap/CLI construction of the approved
  configuration and an explicit zero-model material-count report; it is not another change to
  activity semantics.

## Changed files

- `progress/persisted-activity-launch-limit.md`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunConfiguration.java`
- `src/main/java/org/sourceanalysis/app/runtime/BusinessAnalysisWorkflow.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java`
- `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- `docs/DESIGN.md` and `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Activity execution-budget selector | PASS | The underlying cap starts exactly one DRAFT/REVIEW pair for two materials. |
| Persisted runtime RED | EXPECTED RED | Missing five-argument configuration failed as `PERSISTED_ACTIVITY_LAUNCH_LIMIT_NOT_IMPLEMENTED`. |
| `PersistedBusinessRunExecutorTest` | PASS | 3 tests; direct source/discovery route carries a cap of one to activity coverage. |

## Decisions

- The bound remains application-bootstrap configuration, not a CLI implementation detail or a provider setting.

## Blockers

- None.

## Exact next action

- Implement bootstrap-owned construction and a safe user command that shows material count and the
  chosen launch cap before any real Provider execution.

## Resume checks

- Keep the test on the direct source/discovery route; do not rebuild Flow/Fact/graphs solely to test model-launch budgeting.
