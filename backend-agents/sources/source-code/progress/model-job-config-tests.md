# Progress: model-job-config-tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED test agent for Step 1
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Focused failing tests for unified repository-run-config-v2 YAML modelJobs configuration and identity projection.
- Approved inputs: `docs/modules/model-job-execution.md`, repository `AGENTS.md`, existing `RepositoryRunMain` and `EngineConfigurationLoader` seams/tests.
- Current branch/worktree: `codex/model-job-parallel-execution` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`

## Completed

- Read the scoped repository instructions, progress template, model-job execution contract, current repository-run launcher/configuration code, and relevant tests.
- Added focused RED tests covering unified YAML v2 defaults, explicit Pro/API caps and stable routes, strict modelJobs validation, model-mode absence/legacy provider-config/root rejection, materials-only provider/secret isolation, environment preflight before provider/state access, and base/private model-job identity hashes.
- Added a strict YAML multi-document rejection regression for a valid document followed by `---` and an unknown second document.
- Kept the tests offline: accepted model-mode cases stop at the intentionally absent continuation state; no provider executable or API is invoked.

## Current state

- RED tests are complete and intentionally fail against the current v1 implementation. The hash assertions look for a package-visible composition-root configuration load seam and public/package-visible normalized accessors rather than private fields.

## Changed files

- `progress/model-job-config-tests.md`
- `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunModelJobsConfigurationTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Preserved pre-existing untracked `progress/model-job-parallel-implementation.md`. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest test` (rerun after multi-document YAML regression) | EXPECTED RED | Compiled; 11 tests run, 2 failures, 0 errors. The new failure shows a valid v2 YAML plus `---` second document is accepted and reaches `LOCAL_GIT_REPOSITORY_INVALID`; the existing failure is unchanged `modelJobsSha256` for altered non-secret executable/journal/output. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Tests exercise `RepositoryRunMain.execute` for user-visible mode/configuration behavior. The hash/default projection uses a narrow reflection boundary only to discover a package-visible composition-root loader that is absent today; it does not inspect private fields.
- A present `modelJobs` block is used for model-mode defaults; only `materials-only` cases omit the block, matching the approved contract.
- Valid API preflight uses the existing non-secret `PATH` variable as an offline fixture; it does not repurpose `HOME` or include credentials.
- Mixed-provider execution binding is intentionally deferred to the Step 3 integration tests; this parser-focused suite checks routing shape and references only.

## Blockers

- The current branch has no v2 configuration implementation, so the selector is intentionally RED until the coordinator's production work lands.

## Exact next action

- Report the changed test/progress files and the exact RED result to the coordinator. Do not commit.

## Resume checks

1. Recheck branch and status before edits.
2. Preserve all other agents' files and avoid production edits.
