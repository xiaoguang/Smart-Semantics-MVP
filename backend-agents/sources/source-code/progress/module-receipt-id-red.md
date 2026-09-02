# Progress: module-receipt-id-red

- Status: COMPLETE
- Agent role: TDD RED test author for the fixed-prefix module receipt identity
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01 (after mandated RED run)
- Scope: Add exactly one RED for `ModuleReceiptId` fixed-prefix validation.
- Approved inputs: `docs/DESIGN.md` typed-value contract; source-code scoped
  `AGENTS.md`; current `progress/source-analysis-artifact-foundation.md`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at
  `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the repository-root, backend, and source-code instructions; the source
  design and implementation plans; and the current artifact-foundation
  progress/status records.
- Confirmed the public contract: `ModuleReceiptId` accepts only
  `module-receipt:<64 lowercase hex>`, preserves its exact wire value, and
  rejects a valid digest with the `module-root` fixed prefix.
- Created this task-owned progress record before modifying the test tree.
- Added one public-seam test covering canonical parsing, exact `toString()`
  retention, and rejection of the `module-root` prefix using the same digest.

## Current state

- The requested fixed-prefix RED is complete. Production `ModuleReceiptId`
  remains intentionally absent; no address, policy, or store code was added.

## Changed files

- `progress/module-receipt-id-red.md` (this task-owned progress record)
- `src/test/java/org/sourceanalysis/app/artifact/ModuleReceiptIdTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Existing artifact-foundation implementation, tests, and progress files were observed and preserved. |
| `git status --short -- progress/module-receipt-id-red.md src/test/java/org/sourceanalysis/app/artifact/ModuleReceiptIdTest.java` | PASS | Only the two task-owned paths are in scope; all other worktree changes are pre-existing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModuleReceiptIdTest test` | EXPECTED RED | JDK 17/toolchain and main compilation passed; test compilation failed only with three missing `ModuleReceiptId` symbols. |

## Decisions

- Modify only this progress record and
  `src/test/java/org/sourceanalysis/app/artifact/ModuleReceiptIdTest.java`.
- Use one JUnit test covering canonical parsing, exact `toString()` retention,
  and wrong fixed-prefix rejection through `IllegalArgumentException` only.
- Do not add or test any other type, address, policy, or store behavior.

## Blockers

- None.

## Exact next action

- Hand off this RED to the implementation owner; do not add production code
  in this task.

## Resume checks

- Preserve all unrelated pre-existing worktree changes.
- Confirm the final scoped change is only this progress record and the new
  `ModuleReceiptIdTest.java`; do not implement `ModuleReceiptId` here.
