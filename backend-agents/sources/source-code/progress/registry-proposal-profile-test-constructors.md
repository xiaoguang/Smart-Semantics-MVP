# Progress: Registry proposal profile test constructors

- Status: COMPLETE
- Agent role: Terra/xhigh test compatibility maintenance
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Update only confirmed stale test helpers after the R0 task-profile wire extension.
- Approved inputs: `progress/m2-r0-receipt-carrier-design.md`, current R0 profile contract, and the five named test helpers.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Updated exactly the five approved test-only `RegistryProposalTaskProfile` constructors:
  `BusinessFlowCoverageTest`, `FiniteKeyFlowTaskCompilerTest`, `InterpretationRunnerTest`,
  `RegistryProposalRunnerTest`, and `RepositoryInterpretationRegistryFreezerTest`.
- Each constructor now supplies explicit `adapter-fixture-alpha`, `auth-fixture-beta`, and
  `ModelRuntimeIdentityV1(provider-fixture-gamma, model-fixture-delta,
  reasoning-fixture-epsilon, sandbox-fixture-zeta)` values.
- No compatibility constructor, production default, semantic assertion, oracle, fixture, design,
  or Schema change was made.
## Current state

The production `RegistryProposalTaskProfile` requires explicit configured adapter/auth values and a materialized `ModelRuntimeIdentityV1`. Five test-only direct constructors still use the pre-extension argument list.

## Changed files

- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only constructor audit | PASS | Exactly five stale direct constructors identified in the approved test files. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=...` | PASS | All five touched test files formatted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessFlowCoverageTest,FiniteKeyFlowTaskCompilerTest,InterpretationRunnerTest,RegistryProposalRunnerTest,RepositoryInterpretationRegistryFreezerTest test` | TEST COMPILE PASS; TESTS BLOCKED BY EXISTING PRODUCTION GAPS | 12 tests: 1 pass, 4 failures, 5 errors. All failures occur after test compilation in `RegistryProposalRunner`/`RegistryProposalGenerationReceipt`: missing R0 response constructor carrier and null `requestSha256`; no test-constructor compile errors. |
| `git diff --check -- <five test files> progress/registry-proposal-profile-test-constructors.md` | PASS | No whitespace errors in the owned paths. |

## Decisions

- Use the existing scripted sentinel identity values from `RegistryProposalRunnerTest#carrierProfile` and `runtimeIdentity`.
- Do not add compatibility constructors, production defaults, fixture changes, semantic assertions, or oracle changes.

## Blockers

- The direct selector cannot pass until the parallel M2 R0 carrier implementation completes. Current failures are `REGISTRY_PROPOSAL_RUNNER_FAILED` from the stale response-construction path and `REGISTRY_PROPOSAL_RESPONSE_INVALID` caused by null `requestSha256`; fixing either would exceed this test-only task.

## Exact next action

No further action in this scoped task. The M2 production carrier owner should rerun the same selector after completing its GREEN implementation.

## Resume checks

- Preserve all unrelated shared-worktree edits.
- Confirm no production, design, Schema, or fixture file changes.
