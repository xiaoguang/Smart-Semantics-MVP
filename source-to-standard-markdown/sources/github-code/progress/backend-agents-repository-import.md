# Progress: Backend Agents repository import architecture specification

- Status: COMPLETE
- Agent role: Sole architecture-document author
- Model: `gpt-5.6-sol / ultra`
- Started: 2026-08-29 19:35 NDT
- Last updated: 2026-08-29 20:05 NDT
- Scope: Write the repository restructuring architecture spec only; do not move source files, edit code or build configuration, run builds/tests/network/model providers, commit, push, or alter unrelated worktree changes.
- Approved inputs: Repository and scoped AGENTS instructions; current root/backend READMEs, CONTEXT, shared contract, GitHub Code Agent design, ignore rules, tree/status/link inventory; the user-approved target structure and migration constraints in the task brief.
- Current branch/worktree: `codex/backend-agents-import` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`; pre-existing modifications to `docs/design/data-standardization-review-experience.md` and `docs/handoffs/2026-08-27-data-standardization-review-ia.md` must remain untouched.

## Completed

- Read the repository, prototype, Source Agent workspace, and GitHub Code Agent instructions in full.
- Read the requested current navigation, context, contract, architecture, and ignore documents.
- Confirmed the nested repository branch and dirty worktree state.
- Inventoried the initial 84 non-ignored backend workspace files and the ignored `.workspace/`, `target/`, generated outputs, captured source snapshot, `.DS_Store`, and tool-local modernization stub; this architecture record became the 85th non-ignored workspace file, and the root-owned orchestration record became the 86th.
- Located current Markdown references to `source-to-standard-markdown/`, its source Agent paths, and the shared contract.
- Created `docs/design/repository-frontend-backend-structure.md` with the approved target tree, ownership/seams, complete path map, tracked/excluded inventory, dirty-worktree algorithm, validation, commit/push, rollback, frontend notification, and no-generation contract.
- Self-reviewed the specification for required-section coverage, placeholder language, current-versus-target clarity, dirty-file staging safety, and relative-link depth.
- Confirmed section 7.2 contains exactly one numbered step 5 and a consecutive 1–6 sequence; the shared file already reflected the requested duplicate removal, so no design content changed in the final correction pass.
- Corrected the migration lifecycle: the initial tree was wholly untracked; the design checkpoint tracks the architecture specification plus this `COMPLETE` architecture record and the root-owned `IN_PROGRESS` orchestration record; both progress relocations may appear as renames or delete/add pairs, while all remaining backend workspace files are additions.
- Removed the proposed third implementation progress record. The existing `backend-agents-import-orchestration.md` record is owned and updated only by the root integration/implementation agent.

## Current state

The architecture specification is complete and verified. It distinguishes initial pre-checkpoint Git state from subsequent implementation Git state, contains no unresolved decision or placeholder, and changes no target architecture. No implementation action was taken.

## Changed files

- `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md`
- `docs/design/repository-frontend-backend-structure.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | On `codex/backend-agents-import`; before the checkpoint, two pre-existing modified docs and the untracked `source-to-standard-markdown/` tree were observed. |
| `git ls-files --others --exclude-standard source-to-standard-markdown` | PASS | Initial inventory contained 84 files; the architecture and root orchestration records raised the current pre-checkpoint inventory to 86. The checkpoint tracks those two old-workspace records plus the architecture specification. |
| `git status --short --ignored --untracked-files=all -- source-to-standard-markdown` | PASS | Local `.workspace/`, Maven `target/`, source snapshot, generated candidates/receipts, `.DS_Store`, and modernization stub are ignored. |
| Requested document reads | PASS | Current frontend navigation, Source Agent context/contract, and GitHub Code Agent architecture were read before drafting. |
| Placeholder and section scan | PASS | All required sections are present; no unresolved marker or angle-bracket placeholder remains. |
| Target relative-path normalization | PASS | Backend README, GitHub Code README/design/stage, shared-contract, and downstream-design links normalize to the intended target files. |
| Section 7.2 numbering scan | PASS | Exactly six steps appear in the consecutive sequence 1, 2, 3, 4, 5, 6; step 5 appears once. |
| `git diff --no-index --check -- /dev/null` run once for each architecture-author-owned task file | PASS | The specification and this progress record produced no whitespace diagnostics; exit 1 is the expected no-index “files differ” status before the checkpoint commit. |
| `git diff --check` | PASS | No whitespace errors in tracked working-tree changes. |
| Final `git status --short --branch` | PASS | Required branch retained; two pre-existing modified docs preserved; this task changed only the requested spec and architecture progress record, while the separately root-owned orchestration record remains untouched by this author. |
| Lifecycle statement scan | PASS | The spec names exactly two checkpoint-tracked old-workspace progress records, permits either relocation to appear as a rename or delete/add pair, requires additions for every remaining backend workspace file, and creates no third implementation progress record. |

## Decisions

- Treat `shared/source-agent-contracts/README.md` as the only cross-surface seam; the frontend runtime must not import backend Java implementation.
- Keep root frontend source, commands, runtime, build, and deployment paths unchanged.
- Describe a dirty-worktree-safe migration that stages explicit paths and never stages the pre-existing design/handoff modifications unless they are intentionally updated by the migration owner.
- Record explicitly that the workspace was initially untracked, the checkpoint tracks the two task-owned old-path progress records, and only those later relocations may retain Git history; all remaining imported backend workspace files are additions.
- The plan invokes no generative model or other generative-content workflow.

## Blockers

- None.

## Exact next action

No further action in this documentation task. The root integration/implementation owner may execute `docs/design/repository-frontend-backend-structure.md` while continuing only its existing orchestration progress record and without modifying this completed architecture record.

## Resume checks

- Re-read both checkpoint progress records and confirm their distinct ownership: this architecture record is `COMPLETE` and immutable; the root orchestration record is the sole implementation record and only root updates it.
- Run `git status --short --branch` and confirm the branch remains `codex/backend-agents-import`.
- Preserve the two pre-existing modified documentation files unless the implementation task separately owns their link updates.
- Confirm no source, code, build configuration, generated artifact, or ignored local input was moved or deleted during this documentation-only task.
