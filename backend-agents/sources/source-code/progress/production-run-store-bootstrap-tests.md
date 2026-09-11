# Progress: production run-store bootstrap tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test agent, Batch 1
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add one public-seam RED test proving legitimate ancestor aliases are canonicalized and retained handles stay bound to the original real root.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/01-verified-source-inventory.md`, run-store production sources, and related tests.
- Current branch/worktree: shared worktree; preserve unrelated pre-existing changes.

## Completed

- Read the required repository guidance and run-store implementation/tests.
- Initially confirmed `RunStoreBootstrap.open(Path)` threw `UnsupportedOperationException`; the production bootstrap now exists in the shared worktree and is outside this test-only scope.
- Added one public-seam lifecycle test in a new test class. It opens the caller-provided empty root with `open(Path)`, installs a canonical request-admission module artifact through `CanonicalModuleArtifactStore`, closes, opens a second handle through `open(Path)`, and verifies the same payload.
- Confirmed the test compiles with the project-local JDK 17 Maven toolchain.
- Confirmed the direct test selector fails at the unimplemented production bootstrap before installation or reopen setup runs.
- Replaced the incorrect ancestor-rejection oracle with exactly one focused public-seam test: open an existing final directory through an ancestor alias, retarget that alias, install the existing canonical request, and verify publication under the original real root rather than the replacement path.
- Added a concise final-component symbolic-link rejection assertion in the same test.
- The test uses real target/replacement directories and assumes only when the host cannot create symbolic links.
- Confirmed the direct selector produces the intended RED: the current strict implementation rejects the legitimate ancestor alias before installation, including the inherited macOS `/var` alias used by the temporary directory.

## Current state

- RED is intentionally preserved for the production canonical-root implementation.

## Changed files

- `src/test/java/org/sourceanalysis/app/artifact/RunStoreBootstrapProductionTest.java`
- `progress/production-run-store-bootstrap-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -DskipTests -Dtest=RunStoreBootstrapProductionTest test-compile` | PASS | Test compilation succeeded with project-local JDK 17 toolchain. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunStoreBootstrapProductionTest test` | EXPECTED RED | Tests run: 2, Failures: 0, Errors: 2, Skipped: 0; both errors are `IllegalArgumentException: store root must be an existing non-symlink directory` from the current strict ancestor walk—explicit alias test at `RunStoreBootstrapProductionTest.java:92`, and the lifecycle test through the inherited macOS `/var` alias at line 34. |

## Decisions

- Add a separate test class so no existing test is modified.
- Exercise the production `open(Path)` seam with a caller-provided empty directory, install through `CanonicalModuleArtifactStore`, close, reopen through a second `open(Path)` handle, and verify the canonical payload.
- Keep the symlink check at the public bootstrap seam: the test verifies final-component rejection, then requires an ancestor alias to be accepted and canonicalized before retargeting; the publication assertions prove the retained handle cannot follow the retargeted lexical alias.

## Blockers

- Production `RunStoreBootstrap.open(Path)` currently rejects all symbolic-link ancestors, including legitimate inherited aliases; the new test records this canonical-root RED. No production change is made here.

## Exact next action

- Hand off the exact canonical-root RED and changed paths to the parent agent; do not modify production code in this slice.

## Resume checks

- Do not modify production code, docs, existing tests, POM, or another progress file.
- Do not run broad Maven tests, live models, source capture, customer Maven, network, or other out-of-scope commands.
