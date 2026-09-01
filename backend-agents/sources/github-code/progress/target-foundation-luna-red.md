# Progress: target foundation Luna RED

- Status: BLOCKED
- Agent role: Luna/xhigh TDD RED-test owner for the target foundation public seams
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Worktree: `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`
- Scope: Create only the foundation RED tests and target/foundation fixtures for `CanonicalJsonCodecTest`, `CanonicalArtifactPolicyRegistryTest`, and `TargetArchitectureTest`, plus this progress file.
- Allowed edits: this progress file; `src/test/java/com/linguan/codemd/target/**`; `src/test/resources/target/foundation/**`.
- Forbidden edits: production source, POM, design/stage docs, legacy packages, and unrelated tests/resources.

## Constraints

- Read the scoped `AGENTS.md`, DESIGN §§13.2–13.3.1, and plan §§5–7 before test edits.
- Tests must target only public seams and independent literal goldens; each expected missing type/behavior must remain an explicit RED.
- Maven verification is offline and exact-selector only; no full repository suite, network, Provider, customer build, or production workaround.
- The historical `CanonicalModuleArtifactStoreTest` is outside this RED slice's requested selectors and uses the superseded AtomicDirectoryInstaller/four-field-controls contract; it must be replaced so it cannot add old-contract `testCompile` noise.

## Current state

- Required architecture and toolchain contracts read in full.
- Existing target tests inspected; `TargetContractsTest` and `CanonicalModuleArtifactStoreTest` refer to superseded or absent APIs and do not represent the requested foundation RED.
- The required three-selector RED was not established within the available execution window.
- Per parent instruction, no further test or fixture edits will be made in this task.

## Changed files

- `progress/target-foundation-luna-red.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing worktree changes observed and preserved before edits. |

## Decisions

- Use the documented `com.linguan.codemd.target.artifacts` public package for the three selectors. Do not reintroduce `target.contracts.ModuleArtifact.parse` or any caller-path seam.
- Replace the old `CanonicalModuleArtifactStoreTest` with a minimal target-contract placeholder only if needed to prevent stale-contract compilation; do not retain old AtomicDirectoryInstaller/four-field-controls assertions.
- Keep expected failures attributable to missing `CanonicalJsonCodec`, `CanonicalArtifactPolicyRegistry`, typed addresses/sealed location/address rules, or target-to-legacy dependency violations.

## Blockers

- Time window ended before a clean, attributable RED could be observed for all three requested selectors; the parent task must decide whether to resume the RED slice or clean up the partial test-worktree state.

## Exact next action

- Parent agent to inspect the worktree and either resume the three-selector RED slice under the same scope or discard the incomplete test changes.

## Resume checks

- Re-read this file and the scoped `AGENTS.md` before resuming.
- Confirm all edits remain under the allowed progress/test/resource paths.
- Never modify production code to make RED compile or pass.
