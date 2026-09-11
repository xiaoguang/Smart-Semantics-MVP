# Progress: business flows execution

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the one production execution seam that composes existing Step 05 Flow compilation, evidence-capsule projection, and publication modules. It must accept only typed technical predecessor references and explicit bounded profiles.
- Approved inputs: Active `docs/analysis-steps/05-business-flows.md`; existing persisted graph fixture and Step 04 executor.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the Step 05 M1–M3 sequence exists as public persisted modules but is manually composed in the fixed acceptance and interpretation test helpers.
- Added direct two-entry and zero-entry public-seam RED coverage.
- Implemented `BusinessFlowsExecutionRequest` and `BusinessFlowsExecutor`. The executor only orders Flow compilation, persisted Capsule projection, and five-file publication; Flow/Capsule modules remain their existing owners.
- Verified a two-entry fixture and a zero-entry denominator each fresh-reopen the correct five semantic Step 05 files.

## Current state

- The Step 05 M1–M3 production ordering seam is closed. The global queued run still needs a safe input resolver plus composition of Steps 01–05; those are separate runtime work.

## Changed files

- `progress/business-flows-execution.md`
- `docs/analysis-steps/05-business-flows.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutionTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutionRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutor.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessFlowsExecutionTest test` | RED | 2 tests, 2 expected assertion failures: missing typed Step 05 request/executor; 0 errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=BusinessFlowsExecutionTest,ProvenCodeFactsExecutionTest,EntryRootedFlowCompilerTest,EvidenceCapsuleProjectorTest test` | PASS | 16 tests, 0 failures/errors/skips. |
| Scoped Spotless + `git diff --check` | PASS | The three new Java files are formatted; repository-wide Spotless remains blocked by 59 unrelated dirty files. |

## Decisions

- Flow and capsule resource limits remain explicit request profiles rather than hidden executor constants. The executor will not reparse source, synthesize an external effect, assign business meaning, or invoke a model.

## Blockers

- None.

## Exact next action

- Build the global run-input resolver that feeds the now-available Step 01, 04, and 05 execution seams without accepting an arbitrary source path.

## Resume checks

- Reopen the resulting five-file Step 05 publication and ensure subsequent BusinessMaterialBuilder can consume its existing `BusinessFlowsReference` unchanged.
