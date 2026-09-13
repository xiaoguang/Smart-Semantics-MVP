# Progress: repository run main RED

- Status: COMPLETE
- Agent role: Bounded CLI launch seam RED test
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Preserve the original materials-only reflection RED and add one bounded continuation-mode
  check for strict invalid configuration rejection at the thin whole-repository launch seam.
- Approved inputs: `AGENTS.md`, `progress/jsherp-jdt-luna-repository-run.md`, and the published
  engine configuration contract.
- Current branch/worktree: `codex/jsherp-jdt-luna-repository-run` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the scoped repository instructions and the run progress record.
- Confirmed the exact test configuration: an absolute JSON file with
  `sourceAnalysis.javaEngine` set to `definitely-unknown` and an unsupported top-level key;
  invoke `--mode materials-only`.
- Added `recognizesContinuationModesBeforeStrictConfigurationValidation`, which invokes the public
  `execute` seam with both the approved `activities-sample` argument shape (including absolute
  provider config and `material:sample`) and `generate` without a material ID. Both use the same
  intentionally invalid configuration and require `CONFIGURATION_INVALID`, not argument/mode
  rejection, with no run/provider output and no new temporary-directory files.

## Current state

- The original materials-only reflection test is now GREEN against the implemented launcher. The
  new continuation-mode test remains intentionally unverified and is expected to fail at the
  argument-parser/strict-configuration boundary until the production owner wires those modes.

## Changed files

- `progress/repository-run-main-red.md`
- `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryRunMainTest test` | DEFERRED | Parent requested no Maven while the corrected whole-repository JDT run is active; the new continuation selector is an intentional RED against the current four-argument parser. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java spotless:check` | PASS | Target-only Spotless check succeeded. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the tests offline and reflection-based so the public launcher boundary remains the only
  dependency and continuation RED does not require a production compile-time test seam.
- Use a literal existing engine configuration envelope; do not add an unapproved launcher schema or
  fake JDT/provider executable.
- Assert nonzero exit plus unchanged temporary launch directory and no subprocess evidence exposed
  on output/error streams.
- Exercise both approved continuation argument shapes in one public `execute` test, while keeping
  the invalid configuration as the first observable failure boundary.

## Blockers

- The continuation parser/modes are not yet wired into production; the RED is intentionally
  deferred for the production owner to run after the active JDT lane.

## Exact next action

- Hand the focused reflection tests and intentional continuation RED to the parent; do not add
  production code or run another selector.

## Resume checks

- Read this file and preserve unrelated `progress/jsherp-jdt-luna-repository-run.md` worktree state.
