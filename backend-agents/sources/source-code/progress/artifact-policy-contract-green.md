# Progress: artifact-policy-contract-green

- Status: COMPLETE
- Agent role: Terra/xhigh production repair
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Repair only the public policy-registry interface shape and closed artifact/store failure-code constructor contract.
- Approved inputs: `docs/DESIGN.md` §§13.3.1, 13.7, the Sol review, and the two public policy-registry RED tests.
- Current branch/worktree: `codex/source-analysis-artifact-policy` at `/private/tmp/linguan-source-analysis-artifact-policy`

## Completed

- Read the scoped rules, exact public contract, initial registry implementation, and both RED test records.

## Current state

- The repair is ready for the direct public selector: `CanonicalArtifactPolicyRegistry` is now the documented interface, its existing immutable logic is package-private, and the exception accepts only the published shared artifact/store family.
- The selector is green after applying the project formatter. No storage, filesystem, or runtime behavior was introduced.

## Changed files

- `backend-agents/sources/source-code/progress/artifact-policy-contract-green.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistry.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ArtifactStoreException.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Public RED selector | Recorded RED | Registry type is not an interface; `MADE_UP_FAILURE` is accepted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest,CanonicalArtifactPolicyRegistryContractTest test` | PASS | 3 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Project formatter completed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest,CanonicalArtifactPolicyRegistryContractTest test` | PASS | 3 tests, 0 failures, 0 errors, 0 skipped after formatting. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the loader's behavior and identity calculations intact by moving it behind the public interface, not by adding an alternate construction path.
- Permit only the 13 published shared artifact/store persistence failure codes in the String constructor.

## Blockers

- None.

## Exact next action

- Hand the bounded production repair back to the delivery orchestrator for integration with the existing registry slice.

## Resume checks

- Confirm the production files and this progress file are the only files changed by this repair; preserve all parallel RED tests and progress files.
