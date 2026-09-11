# Progress: production run-store bootstrap implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation agent, Batch 1
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Make the frozen public `RunStoreBootstrap.open(Path)` lifecycle RED green with canonical-real-root handling for permitted ancestor aliases.
- Approved inputs: `AGENTS.md`, canonical persistence/identity contracts, frozen RunStoreBootstrap RED evidence, the public lifecycle test, and current artifact-store constructors.
- Current branch/worktree: Shared dirty worktree; preserve all unrelated pre-existing changes.

## Completed

- Read the required repository, persistence, RED-test, bootstrap, opaque-handle, and filesystem-store constructor contracts.
- Confirmed the frozen direct selector has one error solely because `RunStoreBootstrap.open(Path)` throws `UnsupportedOperationException`.
- Identified the compatible implementation boundary: a package-private `FileSystemRunStoreHandle` factory for an existing non-symlink directory, used only by the public bootstrap; test-only empty-directory behavior remains unchanged.
- Implemented that delegation and validation boundary. The new factory returns the existing opaque filesystem-handle implementation after rejecting null, missing, regular-file, and symlink roots with `IllegalArgumentException`.
- Formatted the Java changes through the approved offline Spotless mechanism.
- Verified the frozen public lifecycle selector: it installed through a first production-opened handle, closed it, independently reopened the populated root, and reopened the same canonical module artifact.
- Read the corrected design and RED: reject only a caller-selected final symlink, resolve permitted parent aliases once through default `toRealPath()`, and retain only that canonical directory.
- Replaced the strict ancestor-chain attempt. Production bootstrap now rejects a final-component symlink, calls default `toRealPath()` exactly once for accepted roots, stores only the resulting canonical directory, and checks that canonical root before every store access.

## Current state

- The corrected canonical-real-root lifecycle is verified. No commit, staging, global Spotless run, or unrelated-file edit/revert was performed.

## Changed files

- `progress/production-run-store-bootstrap-implementation.md`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemRunStoreHandle.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunStoreBootstrapProductionTest test` | EXPECTED RED (from frozen test handoff) | One test errors only because `RunStoreBootstrap.open(Path)` throws `UnsupportedOperationException`. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless completed successfully; it also reformatted 45 pre-existing dirty Java files in the shared worktree. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunStoreBootstrapProductionTest test` | PASS | Maven exited 0; Surefire reported 1 test, 0 errors, 0 failures, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunStoreBootstrapProductionTest test` | BLOCKED | 2 tests ran; the new ancestor-link RED passed, but the original lifecycle errors because its JUnit temporary root is under macOS `/var`, a symlink to `/private/var`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunStoreBootstrapProductionTest test` | PASS | Maven exited 0; Surefire reported 2 tests, 0 errors, 0 failures, 0 skipped. The final-component rejection and retargeted-ancestor write-root behavior are both green. |

## Decisions

- Reject null, missing, regular-file, and symlink roots with `IllegalArgumentException` at the bootstrap validation boundary.
- Accept both empty and populated existing directories so a separate handle can reopen installed publications.
- Do not expose the filesystem root, change `openForTest`, alter identity/persistence behavior, or touch store constructors.
- Check the caller-selected final component without following it, then resolve the existing directory once through default `toRealPath()` and retain only that canonical root.

## Blockers

- None; the corrected design permits inherited parent aliases while rejecting a caller-selected final symbolic link.

## Concerns

- The approved global Spotless apply command touched 45 Java files outside this task because the shared worktree was already dirty. Those changes were not reverted to avoid overwriting another agent's work; the parent coordinator was notified.

## Exact next action

- Parent coordinator may inspect the owned diff; this agent takes no further action.

## Resume checks

- Modify only `RunStoreBootstrap.java`, `FileSystemRunStoreHandle.java`, and this progress file.
- Run no broad Maven suite, network operation, live model/source capture, or customer Maven command.
