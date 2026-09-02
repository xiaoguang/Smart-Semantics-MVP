# Progress: artifact-policy-contract-red

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Public contract RED for the canonical artifact policy registry and closed artifact-store failure codes.
- Approved inputs: Published `docs/DESIGN.md` §§13.7 and 13.7.1, existing policy-registry RED/GREEN seam, and the parent task brief.
- Current branch/worktree: `codex/source-analysis-artifact-policy` at `/private/tmp/linguan-source-analysis-artifact-policy`

## Completed

- Read the scoped repository rules, the published public contract, the existing registry test, and the current parallel policy implementation.
- Created this progress file before changing the test tree.
- Added the two public-contract tests: interface/operation shape and closed failure-code behavior.
- Ran the bounded selector and confirmed two intentional assertion failures with zero errors.

## Current state

- The existing implementation exposes `CanonicalArtifactPolicyRegistry` as a concrete class and accepts arbitrary uppercase strings in `ArtifactStoreException`; the new public-contract tests are expected to fail on those exact divergences. The malformed registry failure normalization is already green in the current parallel implementation.

## Changed files

- `backend-agents/sources/source-code/progress/artifact-policy-contract-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only source/design inspection | PASS | Existing registry implementation and §13.7 failure-code family located. |

| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryContractTest test` | RED | 2 tests, 2 assertion failures, 0 errors/skips: concrete class is not an interface; `MADE_UP_FAILURE` is accepted. Registry malformed-input normalization assertion passes. |
| `git diff --check` | PASS | No whitespace errors in the owned test/progress changes. |

## Decisions

- The interface-shape test uses reflection only for the required public static `load` method; instance method signatures are exercised through a compile-time typed helper.
- The failure-code test accepts only the published shared artifact/store code family and verifies that malformed registry input is normalized to `ARTIFACT_POLICY_REGISTRY_INVALID`.
- The test intentionally does not inspect private implementation details or require persistence behavior.

## Blockers

- None before the targeted RED run.

## Exact next action

- Terra should make the registry a public interface with the documented static loader and close `ArtifactStoreException` construction/validation to the shared artifact/store failure-code family, then rerun this selector.

## Resume checks

- Do not modify production files, POM, design, or another agent's progress file. The current worktree contains parallel GREEN production files; they remain outside this task's ownership.
- Preserve all pre-existing untracked policy implementation files in the worktree.
