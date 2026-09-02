# Progress: artifact-policy-registry-red

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: One public success-path test for the path-free canonical artifact policy registry loader.
- Approved inputs: Published policy-registry contract in `docs/DESIGN.md`; existing canonical codec and typed-value tests.
- Current branch/worktree: `codex/source-analysis-artifact-policy` at `/private/tmp/linguan-source-analysis-artifact-policy`

## Completed

- Read the target scoped instructions, policy-registry contract, and existing artifact test style.

## Current state

- Added the single success-path test. It builds a strict canonical fixture and computes the self-excluding registry ID and full-document SHA in test code; reflection keeps the missing production seam a useful assertion RED rather than a test-compile failure.

## Changed files

- `backend-agents/sources/source-code/progress/artifact-policy-registry-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalArtifactPolicyRegistryTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalArtifactPolicyRegistryTest test` | RED | 1 test, 1 failure, 0 errors, 0 skipped; missing `CanonicalArtifactPolicyRegistry` type at the public seam assertion. |

## Decisions

- The fixture computes `artifactPolicyRegistryId` from the specified self-excluding framed SHA-256 formula in test code; it does not hard-code the identity.
- The fixture is built and encoded through the existing `CanonicalJsonCodec`, so the intended successful input is strict canonical UTF-8 JSON.

## Blockers

- The next Terra slice must add the production registry types and behavior; this RED intentionally does not implement them.

## Exact next action

- Terra should implement the minimum contract: public `CanonicalArtifactPolicyRegistry` with static `load(ImmutableBytes, CanonicalJsonCodec)`, `reference()`, and `resolve(ArtifactPolicyKey)`; public `ArtifactPolicyRegistryReference`, `ArtifactPolicyKey`, `CanonicalArtifactPolicy`, and the existing policy failure code seam. The loader must parse strict canonical JSON, validate exact schema/ordering/enums/combination, recompute the self-excluding registry ID and full-document SHA, and return an immutable registry.

## Resume checks

- Confirm only this progress file and the new test are changed; do not modify production code, POM, or design. The selector's SLF4J/Jqwik warnings are dependency output and not test errors.
