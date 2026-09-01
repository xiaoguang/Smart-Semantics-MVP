# Progress: target artifact store green

- Status: IN_PROGRESS
- Agent role: Terra/xhigh production implementer for the shared canonical module-artifact store
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Implement only `src/main/java/com/linguan/codemd/target/artifacts/**` and this progress file for the DESIGN §13.3.1 public seam.
- Approved inputs: Scoped AGENTS instructions; `docs/DESIGN.md` §13.3/§13.3.1; Stage 01 M3; Luna RED test and its progress; existing target contracts.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the required architecture contract, scoped rules, RED test, RED handoff, and target contract implementation.
- Confirmed the requested public production package is absent and that the focused RED is caused by the missing public seam.

## Current state

The Luna test correction now matches the exact seven-component `ArtifactDescriptor` contract. Implementing the typed artifact store; it will be the only component that derives filesystem locations from opaque typed addresses, while all public request and result records remain path-free.

## Changed files

- `progress/target-artifact-store-green.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing concurrent design/contract/test work is present and preserved. |
| `mvn -Dtest=CanonicalModuleArtifactStoreTest test` | RED observed by Luna | Test compilation fails because the required target/artifacts public seam is absent. |

## Decisions

- Reuse `target/contracts/CanonicalJson`, `ModuleArtifact`, and `ArtifactReference` without modifying them.
- Implement the bounded store as a deep module: no caller-provided module path and no path accessor on `RunStoreHandle`.

## Blockers

- None.

## Exact next action

- Implement the exact DESIGN §13.3.1 typed addresses, value records, canonical codecs, receipt validation, and filesystem installer; run only the specified focused Maven selector.

## Resume checks

- Re-read this progress file and the scoped AGENTS instructions.
- Confirm production edits are limited to `src/main/java/com/linguan/codemd/target/artifacts/**`.
