# Progress: target-contracts-green

- Status: COMPLETE
- Agent role: Terra/xhigh minimal GREEN implementation for public target/contracts
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Implement only `src/main/java/com/linguan/codemd/target/contracts/` public contract types and this progress file.
- Approved inputs: Root, backend, and github-code scoped instructions; `docs/DESIGN.md` §13; Stage 01 and Stage 08 contracts; `TargetContractsTest`; RED progress record.
- Current branch/worktree: isolated `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Confirmed the mandated targeted Maven selector has an established RED caused only by the absent public contract types.
- Read the canonical-envelope identity, JSON/JSONL, strict schema, and source-locator requirements.
- Added strict canonical JSON/JSONL, artifact-reference, full-envelope parser/identity, and rootless source-locator implementations in the scoped target package.

## Current state

- GREEN: all six `TargetContractsTest` cases pass. The target-package ignore rule required force-adding the four scoped source files; no unrelated implementation file was staged or changed. No network, Provider, customer runtime, or generative-content work was involved.

## Changed files

- `progress/target-contracts-green.md`
- `src/main/java/com/linguan/codemd/target/contracts/ArtifactReference.java`
- `src/main/java/com/linguan/codemd/target/contracts/CanonicalJson.java`
- `src/main/java/com/linguan/codemd/target/contracts/ModuleArtifact.java`
- `src/main/java/com/linguan/codemd/target/contracts/SourceLocator.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=TargetContractsTest test` | RED established before this slice | Test compilation reported the four absent public contract types. |
| `mvn -Dtest=TargetContractsTest test` | Build failure during GREEN verification | `ModuleArtifact.parseEnvelope` did not catch `IOException` from the strict mapper; no test class ran. |
| `mvn -Dtest=TargetContractsTest test` | PASS | 6 tests run; 0 failures, 0 errors, 0 skipped. The hand-written complete-envelope ID golden passed unchanged. |
| `git diff --check` | PASS | No whitespace errors reported. |
| `git diff --cached --check` | PASS | No whitespace errors in the force-added target-package source files. |

## Decisions

- The parser will validate the complete Stage 13.3 success envelope and verify the supplied artifact ID from the complete envelope with only top-level `artifactId` removed.
- Unsupported payload mapping will fail closed; `JsonNode.class` preserves the exact payload tree.
- The checked parsing failure is converted to the same public fail-closed `IllegalArgumentException`; no wire semantics changed.
- `backend-agents/sources/github-code/.gitignore` ignores every directory named `target`; the four implementation files were force-added so the requested Java package is deliverable.

## Blockers

- None.

## Exact next action

- Hand off the scoped GREEN implementation and verification evidence to the parent agent.

## Resume checks

- Run `git status --short` in this worktree and confirm changes remain limited to this progress file and `src/main/java/com/linguan/codemd/target/contracts/`.
- If resuming implementation, rerun only `mvn -Dtest=TargetContractsTest test` before changing the green seam.
