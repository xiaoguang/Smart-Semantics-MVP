# Progress: target-artifact-store-red-test-correction

- Status: COMPLETE
- Agent role: Luna/xhigh RED-test contract correction
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Correct the valid JSON `ArtifactDescriptor` construction in the shared artifact-store RED test to match the seven-component target contract.
- Approved inputs: Root, backend, and GitHub Code Agent instructions; `docs/DESIGN.md` §13.3.1; current RED test; target artifact-store RED/GREEN progress files.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Created this progress file before modifying the test.
- Added the missing `CanonicalMediaType.JSON` component to the valid `ArtifactDescriptor` expectation in `CanonicalModuleArtifactStoreTest.java`.

## Current state

The current test omits `CanonicalMediaType.JSON` from one valid descriptor expectation. The target contract fixes the component order as `fileName, artifactType, schemaVersion, artifactId, mediaType, sizeBytes, sha256`.

## Changed files

- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalModuleArtifactStoreTest.java`
- `progress/target-artifact-store-red-test-correction.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=CanonicalModuleArtifactStoreTest test` | RED (expected) | Main compilation succeeded; test compilation stopped because the target `com.linguan.codemd.target.artifacts` production seam is not yet present. The corrected descriptor call itself is accepted by javac; no test methods executed. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Add only the missing `CanonicalMediaType.JSON` argument; do not add overloads, infer values in production, or alter any other assertion.

## Blockers

- Production `target/artifacts` types are still absent, so the focused selector cannot reach Surefire. This is the expected RED handoff and is outside this correction scope.

## Exact next action

Terra/xhigh may implement the exact `target/artifacts` production seam and rerun only `mvn -Dtest=CanonicalModuleArtifactStoreTest test`.

## Resume checks

- Re-read this file and confirm the only owned source change is the one descriptor constructor correction.
- Confirm the focused selector remains RED only because production `target/artifacts` types are not yet implemented.
