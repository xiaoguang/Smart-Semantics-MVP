# Progress: ModuleArtifactRoot RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD RED-test owner for the artifact-foundation typed values
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly one RED for the fixed-prefix `ModuleArtifactRoot` value.
- Approved inputs: `docs/DESIGN.md` typed-value contract; source-code scoped
  `AGENTS.md`; current `progress/source-analysis-artifact-foundation.md`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at
  `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the root, backend, and source-code scoped instructions, both required
  implementation plans, the typed-value design contract, and current artifact
  foundation progress/status records.
- Confirmed `ModuleArtifactRoot` is not yet present and its test path is new.
- Added one focused test method covering canonical parsing, exact `toString`,
  and rejection of the `analysis-run` prefix.
- Ran the exact offline Maven selector and established the intended RED: the
  test compilation fails because `ModuleArtifactRoot` is not yet defined.

## Current state

- The requested fixed-prefix RED is complete. No production implementation,
  `ModuleReceiptId`, address, or store code was added.

## Changed files

- `progress/module-artifact-root-red.md` (this task-owned progress record)
- `src/test/java/org/sourceanalysis/app/artifact/ModuleArtifactRootTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short -- progress/module-artifact-root-red.md src/test/java/org/sourceanalysis/app/artifact/ModuleArtifactRootTest.java` | PASS | Neither task file existed before this progress record. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModuleArtifactRootTest test` | EXPECTED RED | Main compilation passed; test compilation reports three missing `ModuleArtifactRoot` symbols. |

## Decisions

- Modify only `src/test/java/org/sourceanalysis/app/artifact/ModuleArtifactRootTest.java`
  plus this progress file.
- Use only the documented `ModuleArtifactRoot` seam; do not add
  `ModuleReceiptId`, address, store, or implementation code.
- The expected RED is test compilation failure because `ModuleArtifactRoot` is
  absent.

## Blockers

- None.

## Exact next action

- Parent agent may implement the missing `ModuleArtifactRoot` GREEN in its
  next isolated slice.

## Resume checks

- Re-read this file and inspect the two task-owned paths with `git status`.
- Preserve all pre-existing worktree changes and do not implement the missing
  production type in this RED task.
