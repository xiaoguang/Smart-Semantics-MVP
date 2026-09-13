# Progress: model-job-config-implementation

- Status: COMPLETE
- Agent role: Terra/xhigh GREEN implementation agent for Step 1 unified model-job configuration
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Strict `repository-run-config-v2` YAML/JSON configuration, normalized non-secret model-job identity, state-hash split, and model-mode preflight only. Excludes the task pool and network API adapter.
- Approved inputs: `AGENTS.md`, `docs/modules/model-job-execution.md`, `progress/model-job-config-tests.md`, `RepositoryRunModelJobsConfigurationTest`, and the existing launcher/engine seams.
- Current branch/worktree: `codex/model-job-parallel-execution` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the approved model-job execution contract and the focused RED test handoff.
- Confirmed the target selector is RED before production changes: 10 tests, 6 expected v1 failures.
- Implemented a strict unified `repository-run-config-v2` YAML/JSON composition root. It projects only `javaEngine` and `jdt` into the unchanged engine loader.
- Added immutable normalized model-job configuration: global and Codex defaults, explicit API shape, route/Provider/quota validation, and safe non-secret canonical identity.
- Removed the active second provider-config path from model modes while retaining a deliberate legacy-argument rejection after root configuration validation.
- Split materials continuation state from the execution configuration: v2 states use the base configuration SHA calculated after removing only `sourceAnalysis.modelJobs`.
- Added model-mode-only environment/path preflight before continuation-state access. Materials-only validates an optional block without resolving authentication references or constructing a Provider.
- Updated the current launcher/template guidance for v2 configuration while recording that scheduling, per-job persistence, and the API adapter remain later tasks.
- Corrected the normalized model-job identity so it includes journal/output locations, Codex executable or API endpoint, defaults, timeout, auth reference names, routing, caps, quota scopes and provider details without ever resolving or persisting secret values.
- Rejected trailing YAML/JSON documents after the first configuration document.
- Added the atomically idempotent per-run `model-job-execution-config-v1` record in the existing private journal directory. It is written after the saved state is valid and before the first Provider is constructed; invalid state therefore writes nothing.
- Restricted the pre-pool bridge to four identical singleton Codex routes. Mixed routes and API routes now fail closed before a Provider call.
- Corrected launcher/template/current-status wording so it does not claim mixed routing or authentication isolation is executable.
- Replaced the private execution-configuration writer's check-then-move install with an atomic no-replace hard-link install. Same bytes are idempotent; a concurrently installed different record remains authoritative and fails closed as a conflict.
- Added a direct concurrent-installation regression covering simultaneous identical writes and a competing different write that arrives after the temporary file is created.
- Corrected the model-job module and overall design current-state text: strict v2 YAML/JSON parsing, state-hash split, and pre-execution non-secret record are implemented; the pool, API adapter, authentication isolation, and per-job reviewed-result saving remain pending.

## Current state

- Step 1 configuration work is complete. The private execution-configuration record now has a tested atomic no-replace install path, and current documentation distinguishes the implemented strict v2 YAML/JSON boundary from the still-pending task-pool work.

## Changed files

- `progress/model-job-config-implementation.md`
- `src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunModelJobsConfigurationTest.java`
- `README.md`
- `tools/repository-run/README.md`
- `tools/repository-run/jdt-luna-repository-run.template.json`
- `docs/DESIGN.md`
- `docs/modules/model-job-execution.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest test` | EXPECTED RED before GREEN | 10 tests, 6 v1 failures. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest test` | EXPECTED RED after focused review regressions | 11 tests, 2 failures: trailing YAML document accepted and non-secret runtime values omitted from the model-jobs SHA. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest,RepositoryRunMainTest,EngineConfigurationLoaderTest test` | PASS | 27 tests, 0 failures/errors. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest test` | EXPECTED RED for concurrent install hardening | 12 tests, 1 failure: a concurrent different destination write was overwritten instead of producing `CONFLICT`. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest,RepositoryRunMainTest,EngineConfigurationLoaderTest test` | PASS | 29 tests, 0 failures/errors. |
| `mvn -q -t .mvn/toolchains.xml -DspotlessFiles=src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java spotless:check` | PASS | Touched production Java is formatted. |
| `mvn -q -t .mvn/toolchains.xml -DspotlessFiles=src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java,src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunModelJobsConfigurationTest.java spotless:check` | PASS | Touched production and direct-test Java are formatted. |
| `jq -e . tools/repository-run/jdt-luna-repository-run.template.json` | PASS | Updated JSON template is valid. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The loader will normalize a v2 YAML/JSON document once, project only `javaEngine` and `jdt` into `EngineConfigurationLoader`, and expose an immutable package-visible composition-root record for direct tests.
- Model execution preflights environment references before opening the continuation state; materials-only validates model-job structure but neither resolves secrets nor constructs a provider.
- The normalized non-secret model-job document must include all declared operational values, including journal/output directories, provider executable or endpoint, timeout/defaults, authentication reference names, routing, caps and quota scopes. Only resolved secret values stay excluded.
- Until the task-pool and multi-provider adapter work is implemented, the existing serial provider bridge must accept only four identical singleton Codex routes; all other valid future configurations fail closed before a model call.
- Private execution configuration uses `Files.createLink(destination, temporary)` as the atomic no-replace installation primitive. This cannot substitute a concurrently existing destination; an existing record is compared byte-for-byte to preserve idempotence or rejected as a conflict. Unsupported filesystems fail closed rather than falling back to an overwriting move.

## Blockers

- None for this bounded configuration work. The task pool, API adapter, per-job reviewed-result persistence and subscription-authentication isolation remain separate approved steps.

## Exact next action

- Coordinator may continue with the separate task-pool GREEN work. Do not extend this serial bridge into parallel scheduling or an API adapter.

## Resume checks

1. Confirm this branch and preserve the other agents' untracked progress/tests.
2. Run only the approved Step 1 selector after each coherent GREEN attempt.
