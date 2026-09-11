# Progress: M2 R0 receipt carrier RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one public-seam RED test for the M2 R0 GenerationReceiptV3 carrier.
- Approved inputs: `docs/analysis-steps/06-flow-interpretation.md` §6.7.2, `progress/m2-r0-receipt-carrier-design.md`, existing RegistryProposal M1/M2 code and tests.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read the scoped instructions, the publication-readiness contract, the frozen Sol design, and the current M1/M2 public seams.
- Added exactly one `RegistryProposalRunnerTest` method with reflection-only tolerance for the not-yet-installed carrier type/constructor. The test runs the real M1/M2 path over two eligible Flows, counts one scripted Provider call per task, reopens the M1/M2 publications, checks the model-visible input boundary, and verifies that publication does not replay the Provider.
- The test independently checks the frozen GenerationReceiptV3 discriminator, identity, scope, digests, configured adapter/auth, expected/observed runtime, and started/completed fields. The current legacy carrier fails these assertions without a compile or fixture error.

## Current state

- The current M2 execution payload stores only a legacy receipt projection. It does not carry configured adapter/auth, GenerationReceiptV3 discriminators/scope, started/completed flags, or the materialized runtime identity required by the frozen design. The RED is therefore at the intended carrier behavior.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java` (one RED test and test-only helpers)
- `progress/registry-proposal-r0-receipt-carrier-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RegistryProposalRunnerTest#persistsConfiguredAndObservedR0IdentityAsGenerationReceiptV3WithoutReplayingProvider test` | EXPECTED RED | 1 test, 1 failure, 0 errors/skips; 34 grouped assertion failures expose missing carrier fields and legacy receipt identity. Provider calls completed before assertions and publication was confirmed not to replay. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java` | PASS | Formatting applied. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java` | PASS | Touched test is formatted. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalRunnerTest.java progress/registry-proposal-r0-receipt-carrier-tests.md` | PASS | No whitespace errors. |

## Decisions

- Keep the test at the existing M2 runner/publication seam and use only the scripted provider.
- Use reflection only to tolerate the not-yet-installed carrier constructor seam; do not add production compatibility constructors.
- Assert that configured/auth identity is program-only and absent recursively from model-visible R0 `inputJson`.
- Assert the persisted receipt payload, not a private implementation field, and prove publication does not call the provider again.

## Blockers

- None.

## Exact next action

- Hand the frozen RED to Terra/xhigh for the M2 producer carrier GREEN; no production changes were made in this slice.

## Resume checks

- Do not modify production, design, schema, or fixture files.
- Do not invoke a live provider, network, customer Maven, or unrelated test suite.
