# Progress: target design capture and lifecycle contracts

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority; design-document-only sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Add the approved local Git commit capture, source-registration, run lifecycle, result, adapter, and immediate artifact-installation contracts to the overall, Stage 01, and Stage 08 target designs only.
- Approved inputs: Parent dispatch with fixed target decisions; repository, prototype, backend-agent, and github-code scoped instructions; the three named target design documents.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the repository-root, prototype-root, backend-agent, and github-code scoped instructions in full.
- Confirmed the pre-existing untracked `progress/target-eight-stage-implementation.md` is unrelated and must be preserved.
- Created this task-owned progress file before changing durable design documentation.
- Read `docs/DESIGN.md`, `docs/stages/01-freeze-source.md`, and `docs/stages/08-build-nine-section-document-and-archive.md` in full, including the long canonical JSONL examples.
- Audited every current `snapshotRoot`, run-state/result, module-publication, schema-version, and `document.md` contract occurrence in the three scoped documents.
- Added the offline `LocalGitCommitCaptureAdapter` contract using constrained Git CLI plumbing against the local object database, an exact 40-hex commit, full tracked regular-file enumeration, binary media disposition, unsupported-mode fail-closed behavior, and synthetic local Git fixtures.
- Replaced Stage 01 analysis inputs with rootless `sourceRegistrationId` plus content-addressed references; aligned all lower technical records/examples to v2 target schemas and immediate payload-plus-receipt installation.
- Separated durable run lifecycle from final analysis result; fixed single-worker queue, HTTP 202, CLI persist-runId-before-drive, safe resume, blocked/new-run, and finished diagnostic-result behavior.
- Defined the shared filesystem-backed `target/artifacts` test/bootstrap seam, exact record component order, typed installation directories, collision/reopen behavior, JSON and JSONL identities, and the defensive `ImmutableBytes` construction/access contract.
- Aligned Stage 08 modules, examples and v2/v3/v4 schemas so every module installs canonical payload plus receipt and only CandidateRunArchiver creates the one terminal `document.md`.

## Current state

- The approved cross-stage target contract is fully recorded in the three scoped design documents.
- Target schema revisions are explicit and kept separate from current-implementation audit facts; no implementation support is claimed.
- No code, tests, build files, or out-of-scope documentation were changed by this task.

## Changed files

- `backend-agents/sources/github-code/progress/target-design-capture-lifecycle.md`
- `backend-agents/sources/github-code/docs/DESIGN.md`
- `backend-agents/sources/github-code/docs/stages/01-freeze-source.md`
- `backend-agents/sources/github-code/docs/stages/08-build-nine-section-document-and-archive.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only the pre-existing unrelated progress file was untracked before this task. |
| `find . -name AGENTS.md -print` | PASS | Located the prototype, backend-agent, and github-code scoped instructions. |
| `rg -n <affected contract terms> docs/...` | PASS | Located all path-bearing request, overloaded run-state, module receipt, schema-version, and human-output references in scope. |
| `git diff --check` | PASS | Final scoped documentation patch has no whitespace errors. |

## Decisions

- Treat the parent dispatch as explicit approval of an architectural documentation change; do not reopen fixed product decisions.
- Use `LocalGitCommitCaptureAdapter` as a local-only adapter outside the public analysis Interface; keep filesystem paths out of all analysis requests and responses.
- Separate durable lifecycle (`QUEUED/RUNNING/PAUSED_SAFE/BLOCKED_NEW_RUN_REQUIRED/FINISHED`) from final analysis result (`COMPLETE/COMPLETED_WITH_GAPS/INCOMPLETE_SCOPE/INCOMPLETE_COVERAGE`).
- Add a standalone `module-receipt-v1` publication contract; every named module atomically installs canonical JSON/JSONL payloads plus this receipt before downstream consumption.
- Bump only target schemas whose fields or semantics change; describe these as target-design changes and retain the current implementation audit without claiming implementation support.
- Do not invoke a model, network, Maven, source capture, or implementation/test workflow.

## Blockers

- None.

## Exact next action

- Parent may dispatch the next RED test against the exact capture/store/lifecycle target contracts; this design-only task has no remaining work.

## Resume checks

- None; task complete. Preserve all unrelated shared-worktree changes when implementing the target contracts.
