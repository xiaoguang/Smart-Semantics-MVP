# Progress: four-package-policy-registry-green

- Status: COMPLETE
- Agent role: Bounded shipped runtime-policy registry GREEN implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: After Luna's direct shipped-config RED, add only the nine existing downstream checkpoint policy registrations required by four-package `generate` mode. Update the relevant runtime-policy documentation fact. Do not change Java, schemas, run state, model behavior, scans, or any business artifact.
- Approved inputs: Parent's actual failed `generate` diagnosis; current publisher policy registrations; `ProgramGraphsPublicFixture` registration references; Luna-owned `RepositoryRunMainTest` shipped-config RED.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Current state

- Luna's direct shipped-template RED reported the exact nine missing policy keys. The configuration closure is implemented and verified; the actual failed run and all eight completed Luna receipts remain retained. Maven has been released to the parent for the required fresh technical/materials run, which is outside this task.

## Required policy closure

- Activity: `activity-coverage-v2`, `activity-explanations-v1`.
- Process: `business-processes-v1`, `process-coverage-v2`, `business-knowledge-v2`.
- Report: `business-report-v1`, `business-report-markdown-v1`, `business-report-source-ref-v1`, `business-report-validation-v1`.

## Changed files

- progress/four-package-policy-registry-green.md (owned progress)
- tools/repository-run/jdt-artifact-policy-set-v1.json
- tools/repository-run/README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.adapter.cli.RepositoryRunMainTest#shippedPolicyTemplateRegistersExactNineBusinessCheckpointPolicies test` | PASS | Direct shipped-policy regression: 1 test, 0 failures/errors. |
| `mvn -o -t .mvn/toolchains.xml spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java` | PASS | Focused style check for the Luna-owned test. |
| `git diff --check` | PASS | No whitespace errors in the shared worktree. |

## Next action

- No further configuration action. Parent may run the already-required fresh technical/materials scope; the old failed store is intentionally retained.
