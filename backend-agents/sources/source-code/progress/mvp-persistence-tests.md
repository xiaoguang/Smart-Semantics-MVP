# Progress: mvp-persistence-tests

- Status: COMPLETE
- Agent role: persistence and recorded-provider CLI TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add the smallest sufficient RED tests for MVP JSON sidecar persistence, validation, and recorded-provider Picocli integration.
- Approved inputs: Scoped AGENTS.md, progress/TEMPLATE.md, progress/mvp-core.md, progress/mvp-integration.md, existing Java production and test sources, synthetic fixtures, scripted/recorded JSON only.
- Current branch/worktree: /Users/yexiaoguang/Documents/ErpMock on codex/rag-frontend-phase-one; target Maven project is untracked and shared.

## Completed

- Created this progress record before modifying tests or production code.
- Read scoped repository rules, progress template, MVP core/integration progress, all current `src/main/java` and `src/test/java`, and TDD test-writing guidance.
- Added `CandidateArchivePersistenceTest` and `CodeMdCliPersistenceTest` with the agreed production seams and synthetic recorded-provider setup.
- Ran the focused selector and confirmed a clean intentional RED: test compilation reaches only the absent persistence/validation production types.

## Current state

New persistence/CLI tests are written test-first. Production files remain untouched by this task; the coordinator can now implement the named seam against these failing tests.

## Changed files

- progress/mvp-persistence-tests.md
- src/test/java/com/linguan/codemd/mvp/CandidateArchivePersistenceTest.java
- src/test/java/com/linguan/codemd/mvp/CodeMdCliPersistenceTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root changes preserved; target project is untracked. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | RED (expected) | Maven/test compilation reached only absent `CandidateArchiveService` and `ValidationReceipt` symbols; no fixture, dependency, or CLI test type error remained. |

## Decisions

- Use real temporary workspaces and recorded JSON fixtures; do not use live providers, network, customer source, or customer builds.
- Assert every generated machine artifact as parseable JSON except the reader-facing `document.md`.
- Production seam is `CandidateArchiveService(Path workspace)` with `CandidateReference archive(GenerationRequest)`, `ValidationReceipt validate(CandidateReference)`, and workspace-backed trace support used by the CLI.
- `ValidationReceipt.valid()` is the fixed fail-closed contract for both Markdown hash and nine-section tampering; valid receipts also expose `candidateId()`.
- CLI trace contract is `code-md trace --workspace <path> --candidate-id <id> --item-key <key>`; CLI generate accepts the five requested paths and reads one JSON object per recorded round file.

## Blockers

- Production implementation is intentionally absent; this is the expected RED handoff, not a test-author blocker.

## Exact next action

Coordinator should implement the named archive/validation seam and CLI options, then rerun this selector to turn RED to GREEN; this task must not modify production.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm only this progress file and scoped test files are changed by this task.
