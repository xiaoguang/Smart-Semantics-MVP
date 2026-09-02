# Progress: application-discovery-execution

- Status: COMPLETE
- Agent role: Luna/xhigh RED then Terra/xhigh GREEN
- Model: gpt-5.6-luna / xhigh; gpt-5.6-terra / xhigh
- Started: 2026-09-02T10:52:00Z
- Last updated: 2026-09-02T11:22:00Z
- Scope: Close the Stage 02 execution chain by reopening the persisted M1 application-profile draft before M2/M3 analysis, then invoking the existing M4 publisher. Do not change HTTP/Mapper discovery rules, source parsing scope, later analysis steps, Provider behavior or runtime recovery.
- Approved inputs: Published Stage 02 design gate `5d177fa`; completed M1–M4 component publications; synthetic frozen source fixture only.
- Current branch/worktree: `codex/source-analysis-application-discovery-implementation` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Identified the remaining Stage 02 chain defect: direct M2/M3 seam tests accept the in-memory M1 `ApplicationProfile`; M4 reopens drafts, but the actual runner does not yet force the M1 persisted handoff before parsing M2/M3.

## Current state

- The completed executor has the only approved M1–M4 order: detect/publish M1, fresh-reopen and
  cross-check the persisted profile, discover and publish M2/M3, then delegate official output
  installation to M4. It does not add a second parser, rederive discovery decisions, or imply
  product runtime recovery.

## Changed files

- `progress/application-discovery-execution.md` — this recovery record.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutionTest.java` — public execution-seam RED.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/PersistedApplicationProfileReader.java` — fresh M1 profile reopen and source-basis verification.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutor.java` — fixed M1–M4 order.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryRequest.java` — closed execution input.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| M1–M4 direct discovery/store selectors | PASS | 55 tests, including the persisted M1→M2/M3→M4 chain; 0 failures, errors or skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationDiscoveryExecutionTest test` | EXPECTED RED | 1 test: executor class missing; 0 errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationDiscoveryExecutionTest test` | PASS | 2 tests: path-free seam and M1→persisted profile→M2/M3→M4 Spring MVC/MyBatis fixture; 0 failures/errors/skips. |

## Decisions

- The runner owns order only: source handle → M1 detect/publish → fresh M1 profile reopen → M2/M3 discover/publish → M4 publish. It does not add a second parser or re-derive a route/catalog decision.
- Reopened profile content must be cross-checked against the same frozen source basis and controls before M2/M3 receive it.

## Blockers

- None.

## Exact next action

- Publish this completed application-discovery work unit. No full-repository conclusion is
  permitted from this frozen fixture; the next delivery begins the separately designed program
  graph step.

## Resume checks

- Read this file and the M1/M2/M3/M4 progress files, check `git status --short`, and run only the chain selector before changing production code.
