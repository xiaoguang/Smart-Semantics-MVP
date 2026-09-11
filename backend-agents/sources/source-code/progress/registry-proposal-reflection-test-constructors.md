# Progress: Registry proposal reflection test constructors

- Status: COMPLETE
- Agent role: Terra/xhigh test compatibility maintenance
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Repair only the two confirmed stale reflection seams after the R0 receipt-carrier wire extension.
- Approved inputs: current `RegistryProposalProviderResponse` and `RegistryProposalTaskProfile` constructors, M2 receipt-carrier design, and the two named tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Updated `RegistryProposalRunnerTest#run` to reflect the exact `ModelRuntimeIdentityV1, ImmutableBytes` provider-response constructor.
- Updated `RegistryProposalTaskCompilerTest#compile` to reflect the exact 12-argument profile constructor, including configured adapter/auth and expected runtime.

## Current state

The targeted tests now compile against the current production wire. No production code, fixture, schema, oracle, or compatibility constructor was changed.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java`
- `progress/registry-proposal-reflection-test-constructors.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest,RegistryProposalTaskCompilerTest test` before edits | EXPECTED RED | 9 tests: 3 failures from stale provider-response reflection and 4 errors from stale 9-argument profile reflection; 2 tests passed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest,RegistryProposalTaskCompilerTest test` after edits | PASS | 9 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=...` | PASS | Both changed Java tests formatted. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=...` | PASS | Both changed Java tests pass the formatter check. |
| `git diff --check -- <owned paths>` | PASS | No whitespace errors. |

## Decisions

- Use the explicit production constructor signatures. Do not add overloaded compatibility constructors or alter semantic assertions.
- Keep the existing fixture sentinel runtime values (`adapter-fixture-alpha`, `auth-fixture-beta`, `provider-fixture-gamma`, `model-fixture-delta`, `reasoning-fixture-epsilon`, `sandbox-fixture-zeta`).

## Blockers

- None.

## Exact next action

No further action in this scoped task. The owning M2 carrier implementation may consume the now-green constructor seams.

## Resume checks

- Preserve unrelated shared-worktree changes.
- Confirm only the two named tests and this progress file changed in this task.
- Do not modify production, design, schema, fixture, or compatibility code.
