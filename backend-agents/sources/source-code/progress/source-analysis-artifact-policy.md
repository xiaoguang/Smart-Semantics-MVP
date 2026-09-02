# Progress: source-analysis-artifact-policy

- Status: COMPLETE
- Agent role: Delivery orchestrator
- Model: gpt-5.6-sol / ultra (design authority), gpt-5.6-luna / xhigh (RED), gpt-5.6-terra / xhigh (GREEN)
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Next artifact-foundation slice — load and validate the path-free canonical artifact policy registry.
- Approved inputs: User-approved implementation plan; published artifact-foundation clarification; `origin/main` at `1007a1d`.
- Current branch/worktree: `codex/source-analysis-artifact-policy` at `/private/tmp/linguan-source-analysis-artifact-policy`

## Completed

- Created a fresh branch from the merged canonical-primitives delivery.
- Confirmed the published policy-registry contract is sufficient for a bounded loader slice.
- Recorded the public-seam RED for a valid canonical registry: one test fails because the registry
  type does not yet exist.

## Current state

- Terra is implementing only the policy loader, typed policy records, closed enums, and stable
  failure-code seam. Stores, filesystem publication, runtime state, and business analysis remain
  outside this slice.
- A Sol/ultra review found two P1 contract deviations before commit: the published registry seam
  is an interface rather than a public final class, and `ArtifactStoreException` codes must be a
  frozen set rather than arbitrary uppercase strings. A second Luna RED and minimal Terra repair
  are required before integration.
- The second Luna RED and Terra repair are complete: the registry is now the documented public
  interface, and the exception accepts only the 13 published shared persistence codes. The final
  integration pass and local CI rerun are complete; the delivery is ready to stage, commit, and
  submit for local-verified merge.

## Changed files

- `backend-agents/sources/source-code/progress/source-analysis-artifact-policy.md`
- `backend-agents/sources/source-code/progress/artifact-policy-registry-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryTest.java`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryContractTest.java`
- `backend-agents/sources/source-code/progress/artifact-policy-registry-green.md`
- `backend-agents/sources/source-code/progress/artifact-policy-contract-red.md`
- `backend-agents/sources/source-code/progress/artifact-policy-contract-green.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ArtifactStoreException.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ArtifactPolicyRegistryReference.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ArtifactPolicyKey.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicy.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistry.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalEnvelopeKind.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalMediaType.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/PublicContentExposure.java`
- `backend-agents/sources/source-code/docs/DESIGN.md`
- `backend-agents/sources/source-code/README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git log -1 --oneline` | PASS | Base is `1007a1d feat(source-analysis): add canonical artifact primitives (#4)`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest test` | RED | 1 test, 1 assertion failure, 0 errors; expected missing `CanonicalArtifactPolicyRegistry` public seam. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest test` | PASS | 1 test, 0 failures/errors/skips after the initial Terra slice. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 48 Java files clean. |
| `mvn -t .mvn/toolchains.xml -o test` | PASS | 27 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | Enforcer, toolchain, tests, SpotBugs and PMD completed without findings. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest,CanonicalArtifactPolicyRegistryContractTest test` | PASS | 3 tests, 0 failures/errors/skips after the contract repair. |
| Final local CI rerun | PASS | Spotless check; 29 unit tests; Enforcer, JDK 17 toolchain, SpotBugs and PMD all passed. |
| `git diff --check` | PASS | No whitespace errors after all implementation, test, and audit changes. |

## Decisions

- Reuse the merged codec and typed references; registry loading remains path-free and immutable.
- The test computes the self-excluded registry identity from the published U64BE framing formula;
  the production implementation may not hard-code a fixture identity.
- `PATH_FREE_COMPLETE_UTF8` safety allowlisting remains intentionally deferred: the published
  design has no executable type/schema safety catalogue, so this slice preserves its closed enum
  and does not invent one.

## Blockers

- None.

## Exact next action

- This delivery is complete. The next delivery starts from the merged `main` and implements the
  first filesystem store seam without broadening the policy registry.

## Resume checks

- Read this file, inspect `git status --short`, read the policy-registry contract in `docs/DESIGN.md`, then run only the direct policy selector.
