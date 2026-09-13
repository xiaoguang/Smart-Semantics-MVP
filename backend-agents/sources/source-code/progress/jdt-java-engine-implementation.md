# Progress: JDT-first Java code engine implementation

- Status: COMPLETE
- Agent role: Root coordinator
- Model: GPT-5
- Started: 2026-09-12T16:23:18Z
- Last updated: 2026-09-13
- Scope: Implement the approved JDT-first engine, then adapt the preserved JavaParser engine to the frozen neutral contract.
- Approved inputs: Java engine module designs, fixed jshERP snapshot, installed JDT LS 1.61.0/tool JDK, existing source-analysis code and tests.
- Final release commits: JDT `a572f0f`; JavaParser and dual-engine integration `126e94d`

## Completed

- Verified this is an existing linked worktree on the approved implementation branch.
- Reviewed the approved plan against the current runtime, discovery, Step03-Step05, Builder, storage and CLI seams.
- Confirmed the current runtime still hard-requires program graphs, Facts and strict Flow before business material generation.
- Collected the complete task-scoped research and design diff for the recoverable baseline; staged diff validation passed.

## Final state

- The recoverable pre-implementation baseline is `cec1997`.
- Tasks 1–8 are complete and released at `a572f0f`: strict engine configuration, the
  snapshot-bound JDT LS session, standalone JDT Core syntax helper, call navigation, overload-safe
  discovery, persisted Java-code index, context-first Flow/Capsule publication, and the unchanged
  business-material/report chain all use the neutral engine contract.
- Tasks 9–10 are complete and released at `126e94d`: the preserved JavaParser capability is adapted
  to the same contract and selected only by exact YAML configuration. There is no engine fallback,
  mixing, or cross-engine artifact reuse.
- The release audit adds the direct same-run engine-collision check and aligns stale tests with the
  approved Step05 wire. The full module suite passes 430 tests; the quality profile reports zero
  SpotBugs findings and passes PMD.

## Changed files

- progress/jdt-java-engine-implementation.md
- docs/plans/jdt-first-java-engine-implementation-plan.md
- Direct Java-engine, Step02-Step05 and scoped-rule design documents updated by the design owner.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse --git-dir; git rev-parse --git-common-dir; git branch --show-current` | PASS | Existing linked worktree; branch `codex/jdtls-source-navigation-feasibility` |
| Read-only design/code audit | PASS | Reuse boundaries and mandatory Step02-Step05 wire changes identified |
| `git diff --cached --check` | PASS | No staged whitespace errors across the 47-file research/design baseline |
| `mvn -t .mvn/toolchains.xml test` | PASS | 358 tests; 0 failures/errors/skips; build success in 5:17 |
| Step 1.1 design checks | PASS | Tasks 1–10 structurally complete; 80 local links checked with 0 broken; `git diff --check` passed |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | EXPECTED_RED | 17 tests; 17 assertion failures; 0 errors/skips; missing Task 1 production types |
| `mvn -t .mvn/toolchains.xml spotless:check` | PASS | 530 Java files clean after formatting the Task 1 production and test files |
| `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest test` | PASS | 17 tests; 0 failures/errors/skips; fresh coordinator rerun after final workspace metadata change |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest test` | PASS | 44 tests; 0 failures/errors/skips; fresh rerun after Spotless changed four files |
| `mvn -t .mvn/toolchains.xml spotless:check` | PASS | 533 Java files clean |
| `git diff --check` | PASS | No whitespace errors after final Task 1.2 formatting |
| Final bounded closure RED selector | EXPECTED_RED | 49 tests; 5 assertion failures; 0 errors/skips across three remaining review groups |
| Final bounded closure GREEN selector | PASS | 49 tests; 0 failures/errors/skips before final formatting |
| Final post-format five-class selector | PASS | 49 tests; 0 failures/errors/skips; build success |

## Decisions

- Execute exactly two phases: JDT independently first; JavaParser current-capability adapter second.
- Existing ActivityExplainer, ProcessExplainer, BusinessReportPublisher, store bootstrap and public Agent remain reusable and are not redevelopment tasks.
- First observable checkpoint is a real JDT-only complete Service body, before full persistence integration.

## Blockers

- None.

## Exact next action

- None. The approved two-phase implementation is complete; future work is ordinary maintenance or
  explicitly approved capability expansion.

## Resume checks

- Read this file, `progress/java-engine-release-audit.md`, and the four documents under
  `docs/modules/java-code-engines/` before changing engine contracts.
- Treat JDT and JavaParser as two isolated implementations of the same persisted neutral contract.
- Do not reopen the completed implementation plan for unrelated business-language work.
