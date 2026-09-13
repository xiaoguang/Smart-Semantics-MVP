# Progress: runtime JDT approved classpath RED

- Status: COMPLETE
- Agent role: Bounded runtime classpath regression test
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Add one offline regression covering approved Spring dependency paths flowing from the
  persisted technical runtime configuration into the selected JDT project.
- Approved inputs: Scoped `AGENTS.md`, `progress/jsherp-jdt-luna-repository-run.md`, existing
  `TechnicalAnalysisWorkflowTest` infrastructure, and the three existing local Spring 5.0.4 JARs.
- Current branch/worktree: `codex/jsherp-jdt-luna-repository-run` at
  `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Confirmed the current runtime creates `VerifiedJavaProject` with an empty classpath even when a
  selected JDT run is configured.
- Confirmed the existing local JDT tool and Spring web/core/jcl JARs are available for a targeted,
  offline test.
- Added one targeted regression to `TechnicalAnalysisWorkflowTest` using a synthetic captured Git
  fixture whose controller uses a wildcard Spring MVC import. The test reflectively requires the
  `approvedClasspath` `List<Path>` configuration component, supplies the three existing local
  Spring 5.0.4 JARs, and observes one entry through both discovery-only and full technical
  execution.

## Current state

- The test is intentionally RED at the new configuration seam: the current configuration record
  does not yet expose `approvedClasspath`, so the test fails before JDT execution. Once the seam is
  present, the same test will expose whether `PersistedTechnicalRunExecutor` actually passes it to
  `VerifiedJavaProject`.

## Changed files

- `progress/runtime-jdt-classpath-red.md`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java spotless:check` | PASS | Target-only Spotless check succeeded. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=TechnicalAnalysisWorkflowTest#selectedJdtRuntimePassesApprovedClasspathToDiscoveryAndTechnicalExecution test` | EXPECTED RED | 1 test, 1 failure, 0 errors; the `approvedClasspath` configuration component is absent at the reflection boundary. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the test offline and use reflection only for the new internal approved-classpath record
  component, so the RED test does not add a production compile dependency before the seam exists.
- Exercise `executeThroughApplicationDiscovery` and `execute` through a synthetic captured Git
  fixture; do not invoke a customer build, model, network source, or live provider.

## Blockers

- The production configuration field and executor wiring are intentionally absent until the
  implementation agent turns this RED test GREEN.

## Exact next action

- Hand the test and intentional RED result to the parent; the implementation agent owns the
  production configuration and executor wiring.

## Resume checks

- Preserve unrelated worktree changes and the paused journal RED files. Do not run a full suite.
