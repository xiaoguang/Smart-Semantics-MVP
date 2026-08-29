# Repository Frontend and Backend Agent Structure

This English document and the [Chinese rendering](./repository-frontend-backend-structure.zh-CN.md) are parallel renderings of the same approved architecture. They carry the same facts, boundaries, and implementation contract; they are not separate designs.

- Status: approved target architecture; implementation has not occurred as part of this design work
- Decision date: 2026-08-29
- Implementation branch: `codex/backend-agents-import`
- Planned remote branch: `origin/codex/backend-agents-import`
- Lifecycle prerequisite: commit this specification together with both task-owned old-path progress records before the implementation migration begins

## 1. Purpose and current facts

This change gives the repository an explicit frontend, backend Agent, and shared-contract structure without moving or changing the existing frontend application. The repository root remains the frontend application and deployment surface. The Java/Maven Source Agent workspace becomes an independently owned backend module at `backend-agents/`. The language-neutral Source Agent contract becomes the only shared seam at `shared/source-agent-contracts/README.md`.

The following are current facts before implementation:

- The frontend application already lives at repository root. Its implementation is in `src/`; static assets are in `public/`; frontend maintenance scripts are in `scripts/`; deployment files are in `deploy/`; and npm commands are defined by the root `package.json` and `package-lock.json`.
- The current frontend commands are `npm run dev`, `npm run lint`, and `npm run build`. The production build still writes `dist/`, and the independent deployment configuration still lives under `deploy/`.
- The backend Source Agent workspace currently exists locally under `source-to-standard-markdown/`. It contains the shared Source Agent instructions and context, five source-owned directories, one Java 17 Maven module, tests, design documents, progress files, and placeholder READMEs.
- Initially, before the required design-specification checkpoint, the entire `source-to-standard-markdown/` directory is untracked. That checkpoint commits exactly three task-owned files: this specification plus `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md`, owned by the Sol architecture author and already `COMPLETE`, and `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`, owned by the root integration/implementation agent and `IN_PROGRESS`.
- The subsequent implementation migration therefore starts with those two old-path progress files tracked and the remaining backend workspace files untracked. Both progress records relocate under `backend-agents/sources/github-code/progress/`; Git may display either relocation as a rename or as an equivalent old-path deletion plus new-path addition. Every other non-ignored backend workspace file enters the implementation commit as an addition at `backend-agents/` or `shared/source-agent-contracts/`; none has old-path Git history to preserve.
- The current GitHub Code Agent has a local `.gitignore` that excludes `.workspace/` and `target/`. Its ignored local state includes a captured jSHERP snapshot, generated task packages, candidates, receipts, Markdown output, Maven classes, JARs, and test reports.
- `source-to-standard-markdown/.github/modernize/java-upgrade/.gitignore` and its ignored hook files are tool-local modernization state. They are not part of the committed backend Agent surface and have no target path.
- At design time the branch is `codex/backend-agents-import`. Two tracked files already have unrelated working-tree changes: `docs/design/data-standardization-review-experience.md` and `docs/handoffs/2026-08-27-data-standardization-review-ia.md`. The migration must preserve those changes and must not stage the latter file.

This is a repository-layout import, not a frontend rewrite, backend behavior change, dependency upgrade, source capture, candidate generation, deployment, or publication action.

## 2. Target tree

The target repository tree is:

```text
linguan-prototype-v2/
├── AGENTS.md
├── README.md
├── .gitignore
├── package.json
├── package-lock.json
├── src/                              # existing frontend implementation; unchanged
├── public/                           # existing frontend static assets; unchanged
├── scripts/                          # existing frontend maintenance/build scripts; unchanged
├── deploy/                           # existing frontend deployment configuration; unchanged
├── tests/                            # existing frontend/browser tests; unchanged
├── backend-agents/
│   ├── AGENTS.md
│   ├── CONTEXT.md
│   ├── README.md
│   └── sources/
│       ├── mysql/
│       │   └── README.md
│       ├── github-code/
│       │   ├── .gitignore
│       │   ├── AGENTS.md
│       │   ├── DESIGN.md
│       │   ├── README.md
│       │   ├── pom.xml
│       │   ├── docs/
│       │   │   └── stages/
│       │   │       └── 00-mvp.md
│       │   ├── progress/
│       │   │   └── repository-structure-zh-cn.md  # ordinary new progress record for this task
│       │   ├── src/
│       │   │   ├── main/java/
│       │   │   └── test/java/
│       │   ├── .workspace/           # local and ignored; never committed
│       │   └── target/               # generated and ignored; never committed
│       ├── business-docs/
│       │   └── README.md
│       ├── erp-policy/
│       │   └── README.md
│       └── terminology-graph/
│           └── README.md
├── shared/
│   └── source-agent-contracts/
│       └── README.md
└── docs/
    ├── repository-structure.md
    ├── design/
    │   ├── repository-frontend-backend-structure.md
    │   ├── repository-frontend-backend-structure.zh-CN.md
    │   └── data-standardization-review-experience.md
    └── handoffs/
        └── 2026-08-29-backend-agents-import.md
```

The comments on `.workspace/` and `target/` describe local filesystem state, not committed directory placeholders. Git does not preserve empty directories and must not receive any file from those trees.

## 3. Ownership and deep-module seams

### 3.1 Frontend module

The repository-root frontend is one module. Its interface consists of the documented npm commands, browser routes and behavior, generated `dist/` output, and deployment contract. Its implementation remains in the existing root `src/`, `public/`, `scripts/`, and `deploy/` paths.

Frontend owners continue to work from repository root. No frontend import, Vite alias, TypeScript project reference, npm workspace, package script, deployment path, or runtime lookup may point into `backend-agents/`. The migration must not edit `src/`, `public/`, `package.json`, `package-lock.json`, `scripts/`, `deploy/`, frontend test files, or frontend build configuration.

### 3.2 Backend Agents module

`backend-agents/` is the backend Source Agent workspace and ownership root. Its scoped `AGENTS.md`, `CONTEXT.md`, and `README.md` define shared backend Agent rules and navigation. Each directory under `backend-agents/sources/{mysql,github-code,business-docs,erp-policy,terminology-graph}/` remains source-owned and does not import another source Agent's implementation.

The GitHub Code Agent remains a Java 17, single-module Maven implementation. Its deep module interface is the existing `CodeToMarkdownAgent` seam and its CLI; its Java implementation, source parsing, tests, local workspaces, and build output remain private to `backend-agents/sources/github-code/`. Moving the directory does not change Java packages, Maven coordinates, behavior, maturity claims, or safety rules.

### 3.3 Shared Source Agent contract seam

`shared/source-agent-contracts/README.md` is the only cross-source and cross-surface seam introduced by this restructuring. It is a language-neutral documentation interface containing `NineSectionProfile`, minimal candidate/validation concepts, identities, rounds, and explicit Selection. It contains no Java implementation and no source-specific parser, prompt, evidence, rendering, or storage logic.

Backend Agent documentation must link to this shared contract. Frontend architecture and ownership documentation may link to it to explain the nine-section and identity vocabulary. Frontend runtime code must not import backend Java source, backend Maven output, or this Markdown file as executable configuration.

Future cross-surface exchange is limited to separately approved, explicit, immutable candidate and receipt artifacts that conform to the shared contract. Those future artifacts are data crossing the seam, not source-code imports. This import does not create, select, freeze, package, publish, or activate any candidate or receipt.

### 3.4 Dependency direction

The allowed dependency direction is:

```text
frontend documentation ───────┐
                              ├──> shared/source-agent-contracts/README.md
backend Agent documentation ──┘

backend Agent implementation ──> source-owned Java/Maven implementation only
frontend runtime ───────────────> frontend implementation only
future frontend ingestion ──────> explicit immutable candidate/receipt artifacts only
```

There is no frontend-to-backend-source import and no backend-to-frontend-runtime import. The shared Markdown contract is a small interface at a deliberate seam; it must not become a dumping ground for source-specific implementation details.

## 4. Exact old-to-new path mapping

All paths in this table are relative to repository root.

| Current path | Target path | Migration treatment |
| --- | --- | --- |
| `source-to-standard-markdown/AGENTS.md` | `backend-agents/AGENTS.md` | Relocate, then update its scope name and shared-seam references. |
| `source-to-standard-markdown/CONTEXT.md` | `backend-agents/CONTEXT.md` | Relocate without changing its domain definitions. |
| `source-to-standard-markdown/README.md` | `backend-agents/README.md` | Relocate and update navigation, tree, and shared-contract link. |
| `source-to-standard-markdown/contracts/README.md` | `shared/source-agent-contracts/README.md` | Relocate as the language-neutral shared seam; preserve its contract content. |
| `source-to-standard-markdown/sources/mysql/` | `backend-agents/sources/mysql/` | Relocate the placeholder Source Agent README. |
| `source-to-standard-markdown/sources/github-code/` | `backend-agents/sources/github-code/` | Relocate the complete Java/Maven Agent, including source, tests, designs, READMEs, scoped instructions, progress, and ignored local state. Except for the two checkpoint-tracked progress files in the next two rows, its committed files are new additions. |
| `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md` | `backend-agents/sources/github-code/progress/backend-agents-repository-import.md` | Relocate byte-for-byte. This is the Sol architecture author's `COMPLETE` record. The design checkpoint tracks the old path, so the implementation diff may report the relocation as a Git rename; a delete/add presentation is also valid. |
| `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md` | `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md` | Relocate as the root integration/implementation owner's existing `IN_PROGRESS` record. Only the root owner updates it during implementation. Because the design checkpoint tracks the old path, the implementation diff may report the relocation as a Git rename or as a delete/add pair. |
| `source-to-standard-markdown/sources/business-docs/` | `backend-agents/sources/business-docs/` | Relocate the placeholder Source Agent README. |
| `source-to-standard-markdown/sources/erp-policy/` | `backend-agents/sources/erp-policy/` | Relocate the placeholder Source Agent README. |
| `source-to-standard-markdown/sources/terminology-graph/` | `backend-agents/sources/terminology-graph/` | Relocate the placeholder Source Agent README. |
| `source-to-standard-markdown/sources/github-code/.workspace/` | `backend-agents/sources/github-code/.workspace/` | Relocate with the source directory for local continuity; remain ignored and unstaged. |
| `source-to-standard-markdown/sources/github-code/target/` | `backend-agents/sources/github-code/target/` | Relocate with the source directory; remain ignored and unstaged. A later Maven test may regenerate it in the same target path. |
| `source-to-standard-markdown/.github/modernize/java-upgrade/` | none | Leave in place as ignored tool-local state; do not copy, stage, or document it as backend Agent source. |

The migration moves `source-to-standard-markdown/sources/` as one filesystem directory so ignored `.workspace/`, `target/`, and source-level `.DS_Store` files travel without being copied into the Git index. That move also carries both checkpoint-tracked progress files. The architecture record stays byte-for-byte unchanged; the root owner maintains the orchestration record as implementation advances. Git may detect either relocation as a rename based on content similarity. The three backend workspace documents and the contract README move separately and enter Git as additions. The old top-level directory may remain locally because the ignored modernization stub or `.DS_Store` still exists; no command deletes it or its contents.

## 5. Tracked and excluded content

### 5.1 Content that must be tracked

The design-specification checkpoint tracks exactly these three task-owned files before migration:

- `docs/design/repository-frontend-backend-structure.md`;
- `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md`, the Sol architecture author's `COMPLETE` record;
- `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`, the root integration/implementation owner's `IN_PROGRESS` record.

The subsequent implementation commit includes:

- `backend-agents/AGENTS.md`, `backend-agents/CONTEXT.md`, and `backend-agents/README.md`;
- every source Agent placeholder README;
- the GitHub Code Agent's `.gitignore`, scoped `AGENTS.md`, `DESIGN.md`, `README.md`, `pom.xml`, `docs/stages/00-mvp.md`, all existing progress files, all Java production source, and all Java tests;
- `backend-agents/sources/github-code/progress/backend-agents-repository-import.md`, already marked `COMPLETE` after this architecture-specification task's verification and moved byte-for-byte from its checkpoint-tracked old path;
- `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md`, relocated from its checkpoint-tracked old path and updated only by the root integration/implementation owner through implementation completion;
- `backend-agents/sources/github-code/progress/repository-structure-zh-cn.md`, this Chinese-rendering task's ordinary new backend progress record, marked `COMPLETE` at task completion; it has no old path and does not participate in the two checkpoint progress files' relocation lifecycle;
- `shared/source-agent-contracts/README.md`;
- root navigation and ownership changes in `.gitignore`, `AGENTS.md`, and `README.md`;
- `docs/repository-structure.md`;
- the minimal synchronization changes in `docs/design/repository-frontend-backend-structure.md` that register the parallel language rendering, plus the new `docs/design/repository-frontend-backend-structure.zh-CN.md`;
- `docs/handoffs/2026-08-29-backend-agents-import.md`.

Java source, tests, Maven configuration, stable architecture, stage design, capability README, completed and historical progress records, and placeholder Source Agent READMEs must remain byte-for-byte unchanged except for the specific documentation path/link corrections listed in section 6. Among the pre-existing progress files, the root-owned orchestration progress record is the sole content exception: its owner updates current implementation state according to the scoped progress contract. Historic progress statements are not rewritten merely because files move. The new Chinese-rendering task record is a normal work-unit progress file, not a third relocation or orchestration record.

The design-specification checkpoint tracks no backend workspace files other than the two named old-path progress records. No third implementation-lifecycle or orchestration progress file is created: `backend-agents-import-orchestration.md` is the root owner's implementation progress record. `repository-structure-zh-cn.md` is an ordinary new documentation-work progress record. Every other approved backend workspace file is an addition at its target path.

### 5.2 Content that must remain untracked

The implementation commit excludes:

- every `.workspace/` directory;
- the captured jSHERP repository snapshot and any other captured source snapshot;
- task packages, recorded model inputs, model responses, generated candidates, generated Markdown, receipts, trace indexes, evidence packs, selections, and other model/runtime output under local workspaces;
- every Maven `target/` directory;
- JARs, class files, generated sources, Maven metadata, Surefire reports, and other build/test reports;
- `.DS_Store` and editor-local files;
- secrets, credentials, tokens, local environment files, and API keys;
- repository-local Codex state and other local agent/session state;
- `node_modules/`, `dist/`, `dist-ssr/`, frontend test results, Playwright reports, blob reports, and log files;
- `source-to-standard-markdown/.github/modernize/java-upgrade/` and its hooks.

Local candidate and receipt output must stay below `.workspace/` during this import. The future immutable candidate/receipt seam is not implemented by weakening ignore rules or force-adding current outputs; it requires a separately approved artifact path, identity contract, and validation gate.

### 5.3 Ignore contract

The root `.gitignore` keeps the existing frontend exclusions and adds repository-wide protection for backend/local state. The implementation must add equivalent rules for:

```gitignore
# Backend Agent local inputs and generated outputs
/backend-agents/**/.workspace/
/backend-agents/**/target/
/backend-agents/**/*.jar
/backend-agents/**/*.class
/backend-agents/**/reports/
/backend-agents/**/surefire-reports/

# Local agent state and secrets
/.codex/
.env
.env.*
!.env.example
!*.env.example
!.env.*.example

# Tool-local legacy modernization state
/source-to-standard-markdown/.github/modernize/java-upgrade/
```

The relocated `backend-agents/sources/github-code/.gitignore` remains in place with its existing `.workspace/` and `target/` rules. Redundant protection is intentional: the scoped file protects the module in isolation, while the root file protects future backend Agent directories and the repository import.

## 6. Documentation and link updates

### 6.1 Root navigation and ownership

The implementation updates the following current-fact documents:

- `README.md` gains a concise repository navigation section immediately after its opening description. It identifies repository root as the frontend application, links to `./backend-agents/README.md`, links to `./shared/source-agent-contracts/README.md`, and states the frontend and backend command working directories.
- `AGENTS.md` gains scope routing for `backend-agents/`, its source-owned subdirectories, and `shared/source-agent-contracts/`. It continues to route frontend work to the existing frontend instructions and does not turn backend Agent work into frontend work.
- `docs/repository-structure.md` becomes the durable repository ownership map. It records the target tree, command roots, ownership, dependency direction, ignored surfaces, and where implementation-specific instructions live. From that file, its links to the backend navigation, shared contract, English design, and Chinese design are `../backend-agents/README.md`, `../shared/source-agent-contracts/README.md`, `./design/repository-frontend-backend-structure.md`, and `./design/repository-frontend-backend-structure.zh-CN.md`.
- `docs/handoffs/2026-08-29-backend-agents-import.md` is the frontend notification described in section 11. From that file, its links to backend navigation, the shared contract, repository structure, English design, and Chinese design are `../../backend-agents/README.md`, `../../shared/source-agent-contracts/README.md`, `../repository-structure.md`, `../design/repository-frontend-backend-structure.md`, and `../design/repository-frontend-backend-structure.zh-CN.md`.

The English design at `docs/design/repository-frontend-backend-structure.md` and the Chinese design at `docs/design/repository-frontend-backend-structure.zh-CN.md` link to each other near the top and remain materially identical descriptions of one architecture, migration behavior, and acceptance contract.

### 6.2 Backend documentation links

The following link changes are exact:

| Target document | Required target link or text |
| --- | --- |
| `backend-agents/README.md` | Shared contract link becomes `../shared/source-agent-contracts/README.md`; workspace tree uses `backend-agents/` and `shared/source-agent-contracts/`. |
| `backend-agents/AGENTS.md` | Scope text names `backend-agents/`; the only shared seam is `shared/source-agent-contracts/`. |
| `backend-agents/sources/github-code/README.md` | `NineSectionProfile` link becomes `../../../shared/source-agent-contracts/README.md`. |
| `backend-agents/sources/github-code/DESIGN.md` | `NineSectionProfile` link becomes `../../../shared/source-agent-contracts/README.md`. Its downstream review link remains `../../../docs/design/data-standardization-review-experience.md` because the directory depth is unchanged. |
| `backend-agents/sources/github-code/docs/stages/00-mvp.md` | `NineSectionProfile` link becomes `../../../../../shared/source-agent-contracts/README.md`; links to `../../DESIGN.md`, `../../src/`, and `../../.workspace/` keep their existing relative forms. |
| Existing progress files | Move without rewriting historical path statements. The Sol architecture-author record is `COMPLETE` and remains unchanged. The root orchestration record is `IN_PROGRESS` at the checkpoint, is the implementation progress record, and is updated only by its root owner; no third implementation-lifecycle or orchestration progress file is created. |
| `backend-agents/sources/github-code/progress/repository-structure-zh-cn.md` | Enter the implementation inventory as this parallel-Chinese-document task's ordinary new backend progress record and mark it `COMPLETE` at task end. It has no old path and does not participate in the two checkpoint progress files' relocation lifecycle. |

### 6.3 Existing dirty documents

`docs/design/data-standardization-review-experience.md` currently contains old-path links and a workspace tree only in pre-existing unstaged additions. The migration owner must make these exact working-copy corrections:

- `../../source-to-standard-markdown/sources/github-code/DESIGN.md` becomes `../../backend-agents/sources/github-code/DESIGN.md`;
- `../../source-to-standard-markdown/sources/github-code/docs/stages/00-mvp.md` becomes `../../backend-agents/sources/github-code/docs/stages/00-mvp.md`;
- the workspace name becomes `backend-agents/`;
- the shared seam becomes `shared/source-agent-contracts/`;
- `sources/github-code/` in the ownership sentence becomes `backend-agents/sources/github-code/`;
- the displayed tree becomes the target `backend-agents/` plus `shared/source-agent-contracts/` tree in section 2.

Because those lines do not exist in the committed `HEAD` version, the file remains entirely unstaged. This preserves the original owner's work and prevents the migration commit from absorbing unrelated design changes. The remote migration commit therefore contains no stale link from the committed version of that file; the corrected link text remains in the local owner's unstaged change for its later owning commit.

`docs/handoffs/2026-08-27-data-standardization-review-ia.md` remains byte-for-byte untouched and unstaged. The historical sentence in `docs/handoffs/2026-08-29-v7-publication-and-deployment.md` saying that a prior commit did not touch `source-to-standard-markdown/` remains unchanged because it records a past event rather than current navigation.

## 7. Dirty-worktree-safe migration algorithm

The implementation follows these steps in order after the separate design-specification checkpoint has committed this document and both old-workspace progress records. It does not use `git stash`, `git reset`, `git checkout --`, broad cleanup commands, or `git add .`. The root integration/implementation owner continues the existing `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md` record, relocates it with the rest of `sources/`, and is the only agent that updates it. The completed architecture-author record remains unchanged. No third implementation-lifecycle or orchestration progress file is created. The new `repository-structure-zh-cn.md` is an ordinary documentation-work progress record and does not replace either checkpoint record.

### 7.1 Preflight and preservation

1. Run `git status --short --branch` and require branch `codex/backend-agents-import`.
2. Record `git rev-parse HEAD`, `git diff --name-only`, `git diff --check`, `git ls-files source-to-standard-markdown`, and `git ls-files --others --exclude-standard source-to-standard-markdown`. The tracked listing must contain exactly `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md` and `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`; the other approved non-ignored backend files remain in the untracked listing.
3. Create a unique preservation directory with `mktemp -d /private/tmp/backend-agents-import-20260829.XXXXXX`, record the returned path, and save read-only copies of the two pre-existing dirty-file patches there. These copies are recovery evidence only and are never staged.
4. Verify that `backend-agents/` and `shared/source-agent-contracts/` do not already exist. If either exists, stop before moving anything; overwriting or merging an unknown target is forbidden.
5. Verify that the expected Source Agent files, `.workspace/`, `target/`, and modernization stub match the inventory described in sections 1 and 5. New unknown non-ignored files require review before the import continues.

### 7.2 Relocate without deleting local state

1. Create only the empty target parents `backend-agents/` and `shared/source-agent-contracts/`.
2. Relocate `source-to-standard-markdown/AGENTS.md`, `CONTEXT.md`, and `README.md` into `backend-agents/`.
3. Relocate the complete `source-to-standard-markdown/sources/` directory to `backend-agents/sources/`. This keeps source, tests, documentation, progress, placeholders, `.workspace/`, `target/`, and scoped ignore rules together, including both checkpoint-tracked progress files.
4. Relocate `source-to-standard-markdown/contracts/README.md` to `shared/source-agent-contracts/README.md`.
5. Leave `source-to-standard-markdown/.github/modernize/java-upgrade/`, any old top-level `.DS_Store`, and empty local parent directories in place. Do not remove them.
6. Confirm that the relocated `.workspace/` and `target/` still exist locally when they existed before, remain ignored, and are absent from `git ls-files --others --exclude-standard backend-agents`.

### 7.3 Apply only approved text changes

1. Apply the exact backend documentation link and scope changes from section 6.
2. Update root `.gitignore`, `AGENTS.md`, and `README.md` as specified.
3. Create `docs/repository-structure.md` and the frontend handoff.
4. Keep the English and Chinese designs as parallel renderings of the same architecture. Apply only the registration, navigation, inventory, validation, notification-navigation, and acceptance synchronization specified in sections 6, 7.4, 8, 11, and 13; do not change migration behavior.
5. Correct only the five old-path forms named in section 6.3 inside the dirty data-standardization design; keep that file unstaged.
6. Do not edit the dirty 2026-08-27 handoff.
7. Only the root integration/implementation owner updates `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md` after each verifiable phase. Keep it `IN_PROGRESS` through the initial section 8 validation. After every section 8 check succeeds, mark it `COMPLETE`, restage only that exact target path, and rerun the section 8.1 Git and inventory checks before following section 9. Record the immutable commit identity in the handoff and out-of-band notification rather than by adding a post-commit progress edit. Do not edit the completed architecture-author progress file.

### 7.4 Stage an explicit allowlist

Stage only these paths:

```text
.gitignore
AGENTS.md
README.md
backend-agents/AGENTS.md
backend-agents/CONTEXT.md
backend-agents/README.md
backend-agents/sources/mysql/README.md
backend-agents/sources/business-docs/README.md
backend-agents/sources/erp-policy/README.md
backend-agents/sources/terminology-graph/README.md
backend-agents/sources/github-code/.gitignore
backend-agents/sources/github-code/AGENTS.md
backend-agents/sources/github-code/DESIGN.md
backend-agents/sources/github-code/README.md
backend-agents/sources/github-code/pom.xml
backend-agents/sources/github-code/docs/
backend-agents/sources/github-code/progress/
backend-agents/sources/github-code/src/
source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md
source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md
shared/source-agent-contracts/README.md
docs/repository-structure.md
docs/design/repository-frontend-backend-structure.md
docs/design/repository-frontend-backend-structure.zh-CN.md
docs/handoffs/2026-08-29-backend-agents-import.md
```

Directory pathspecs in that list are bounded to an approved ownership root. The two exact old progress-file paths are included only so their tracked deletions can be paired with their new paths; stage those exact old paths and no broader old-workspace pathspec. `backend-agents/sources/github-code/progress/` includes the ordinary new `repository-structure-zh-cn.md` record, which is not one of the two old-path relocation exceptions. Before commit, compare every staged filename with this allowlist. In particular, the index must not contain `docs/design/data-standardization-review-experience.md`, `docs/handoffs/2026-08-27-data-standardization-review-ia.md`, any `source-to-standard-markdown/` path other than the deleted sides of the two progress-record relocations, `.workspace/`, `target/`, `.github/modernize/`, `.DS_Store`, a JAR, a class file, or a generated report.

## 8. Validation

All validation is local and serial. No validation command invokes a model provider, source scanner, customer Maven project, browser deployment, or external source.

### 8.1 Git and inventory checks

Run and inspect:

```bash
git status --short --branch
git diff --check
git diff --cached --check
git diff --cached --find-renames --name-status
git diff --cached --stat
git ls-files backend-agents shared/source-agent-contracts
git ls-files source-to-standard-markdown
git status --short --ignored --untracked-files=all -- backend-agents source-to-standard-markdown
```

Acceptance requirements:

- the branch is `codex/backend-agents-import`;
- the staged paths match section 7.4 exactly by ownership scope;
- the two pre-existing dirty documents remain unstaged;
- no Java production/test file, stable design, stage design, progress file, or placeholder README is missing;
- local ignored artifacts remain present where preserved or relocated and none is staged;
- the architecture-author progress record remains `COMPLETE`, the root-owned orchestration record is `COMPLETE` in the final staged state after being updated only by its root owner, and the ordinary new `repository-structure-zh-cn.md` progress record is `COMPLETE` without altering either checkpoint record's relocation lifecycle;
- `git diff --cached --find-renames --name-status` reports each of the two progress-record relocations either as a rename from its old path to its new path or as the equivalent old-path deletion plus new-path addition;
- every remaining committed backend workspace file is an addition at `backend-agents/` or `shared/source-agent-contracts/`, with no other old-path rename or deletion;
- `git ls-files source-to-standard-markdown` is empty after staging because both tracked old-path progress records have relocated;
- no frontend implementation, package, lockfile, script, deployment, or frontend-test path appears in the diff.
- both language design files are allowlisted, their near-top cross-links resolve, and they remain materially aligned on every architecture fact, path, ownership boundary, migration/validation/rollback contract, two-checkpoint-progress-file lifecycle, generative-content inventory, and acceptance condition.

Use this staged-name exclusion check; it must produce no output:

```bash
git diff --cached --name-only | rg '(^|/)(\.workspace|target|reports|surefire-reports)(/|$)|\.(jar|class)$|(^|/)\.DS_Store$|(^|/)\.github/modernize/'
```

### 8.2 Link checks

Confirm each new active target exists:

```text
backend-agents/README.md
backend-agents/AGENTS.md
backend-agents/sources/github-code/README.md
backend-agents/sources/github-code/DESIGN.md
backend-agents/sources/github-code/docs/stages/00-mvp.md
shared/source-agent-contracts/README.md
docs/repository-structure.md
docs/design/repository-frontend-backend-structure.md
docs/design/repository-frontend-backend-structure.zh-CN.md
docs/handoffs/2026-08-29-backend-agents-import.md
```

Resolve the relative Markdown links in the three moved GitHub Code Agent documents, the root navigation documents, and the English/Chinese design cross-links from each containing directory. Then search the active navigation set for stale paths:

```bash
rg -n 'source-to-standard-markdown|(^|[^[:alnum:]])contracts/README\.md' \
  README.md AGENTS.md docs/repository-structure.md \
  docs/design/data-standardization-review-experience.md \
  backend-agents/README.md backend-agents/AGENTS.md \
  backend-agents/sources/github-code/README.md \
  backend-agents/sources/github-code/DESIGN.md \
  backend-agents/sources/github-code/docs/stages/00-mvp.md
```

That search must return no active old-path reference. Both language renderings of this specification and historical progress/handoff records are intentionally outside the search because their old-path text documents the migration or a past event.

### 8.3 Backend Maven verification

The directory move changes the Maven module's working path, so both a targeted smoke and the complete GitHub Code Agent module test are required. This is explicit authorization for the complete `github-code` Maven module only, not for a full repository suite. Run the two commands serially from repository root:

```bash
mvn -q -f backend-agents/sources/github-code/pom.xml \
  -Dtest=CodeMdCliDiscoveryTest,CodeMdCliValidateTest,CandidateArchivePersistenceTest test

mvn -q -f backend-agents/sources/github-code/pom.xml test
```

These tests use synthetic fixtures or recorded providers. They must not execute the captured jSHERP project, access a network source, or call a model. If Maven needs uncached network dependencies, stop and report the dependency-resolution limitation rather than broadening authorization. Maven output stays ignored under `backend-agents/sources/github-code/target/`.

### 8.4 Frontend validation threshold

The plan intentionally changes no frontend implementation or build/runtime/deployment configuration. Verify that this command produces no output:

```bash
git diff --cached --name-only -- src public package.json package-lock.json scripts deploy tests
```

When it is empty, do not run npm install, `npm ci`, frontend tests, `npm run lint`, or `npm run build` for this migration. There is no package or lockfile change and no npm reinstall is expected. If an authorized implementation unexpectedly needs to change any listed frontend path, stop this migration and obtain a revised design; frontend validation then belongs to that separately approved change.

## 9. Atomic commit and push sequence

After every check in section 8 passes, create one atomic implementation commit in addition to the already completed design-specification checkpoint:

1. Re-run `git status --short --branch` and inspect `git diff --cached` in full.
2. Confirm the two pre-existing dirty documents are present only as unstaged changes and that their saved preflight patches remain recoverable.
3. Create one atomic commit with subject `chore: import backend agents workspace`.
4. Record the resulting full SHA with `git rev-parse HEAD`.
5. Push with `git push -u origin codex/backend-agents-import`.
6. Verify the local branch tracks `origin/codex/backend-agents-import` and the pushed tip equals the recorded SHA.
7. Send the frontend notification with the exact branch and pushed SHA.

The implementation commit contains both progress-file relocations, the root-owned orchestration record's approved status updates, the remaining backend additions, approved repository documentation changes, the synchronized English/Chinese design registration, and the Chinese-document task's ordinary new progress record. It is pushed to `origin/codex/backend-agents-import`, not `main`. This task does not create a pull request, merge, rebase, force-push, deploy, or delete a branch. Any PR or merge requires a separate request.

The tracked handoff identifies the migration commit as “the single commit containing this handoff” on `origin/codex/backend-agents-import`. A Git commit cannot contain its own SHA. Therefore the exact SHA is added to the out-of-band frontend notification after push and can be recovered deterministically with:

```bash
git log -1 --format=%H origin/codex/backend-agents-import -- \
  docs/handoffs/2026-08-29-backend-agents-import.md
```

This avoids a second documentation-only commit or an amend while still giving frontend owners an exact immutable identity.

## 10. Rollback

### 10.1 Before commit

Do not reset or delete. Reverse the explicit filesystem relocations, carrying `.workspace/` and `target/` back with `sources/`. Reverse only the task-owned text changes. Preserve newly written repository documents in the unique preflight directory created in section 7.1 if the import is abandoned. Compare the two dirty files with their saved patches and restore their original unstaged content without discarding it.

### 10.2 After commit but before push

Copy the committed backend source and documents to a safe local preservation directory, relocate ignored local state back to the old workspace, and create a normal `git revert` commit using the full migration SHA recorded in section 9 step 4. Do not use reset, amend, or history rewriting. Verify the two pre-existing dirty documents still match their expected unstaged state.

### 10.3 After push

Preserve local ignored inputs first, then revert the migration commit on `codex/backend-agents-import` and push the revert normally. Do not force-push and do not change `main`. The revert restores both progress files at their checkpoint-tracked old paths—the architecture record as `COMPLETE` and the orchestration record in its checkpointed `IN_PROGRESS` state—and removes the implementation commit's new backend/shared paths; preserved local backend inputs remain available for a corrected import.

Rollback of this repository-layout commit does not require an npm reinstall or frontend deployment because frontend runtime/build/deploy paths never changed.

## 11. Frontend notification contract

`docs/handoffs/2026-08-29-backend-agents-import.md` is addressed to frontend owners, links to both parallel language renderings of this architecture, and must contain all of the following as current facts:

1. **Outcome.** The repository now contains a backend Source Agent workspace at `backend-agents/` and a language-neutral contract at `shared/source-agent-contracts/`; the frontend remains at repository root.
2. **Old-to-new mapping.** Include the mapping table from section 4 for workspace documents, five source directories, GitHub Code Agent, both checkpoint-tracked progress-file rename/delete-add exceptions, and shared contract.
3. **Unchanged frontend surface.** State that `src/`, `public/`, `package.json`, `package-lock.json`, `scripts/`, `deploy/`, frontend tests, Vite runtime, `dist/`, and server deployment paths did not move or change.
4. **Unchanged commands.** State that frontend owners still run `npm run dev`, `npm run lint`, and `npm run build` from repository root.
5. **No reinstall.** State that no npm dependency or lockfile changed, so no `npm install` or `npm ci` is expected after pulling.
6. **Shared seam.** Point frontend documentation to `shared/source-agent-contracts/README.md`; state that frontend runtime must not import Java implementation or Maven output from `backend-agents/`.
7. **Post-pull checks.** Ask frontend owners to inspect the commit, confirm the frontend-path diff is empty, resolve root navigation links, and search `src/`, `public/`, `scripts/`, and package configuration for accidental `backend-agents` imports. A frontend build is optional because the frontend surface is unchanged, not a migration acceptance requirement.
8. **Backend command location.** State that Maven runs from repository root with `mvn -q -f backend-agents/sources/github-code/pom.xml test`, or from the module directory with `mvn -q test`.
9. **Excluded local artifacts.** Name `.workspace/`, captured source snapshots, model outputs/candidates/receipts, `target/`, JARs/classes/reports, `.DS_Store`, secrets, local Codex state, `node_modules/`, `dist/`, and test artifacts as excluded from the commit.
10. **Identity.** Name branch `codex/backend-agents-import`, remote branch `origin/codex/backend-agents-import`, commit subject `chore: import backend agents workspace`, and the rule for obtaining the exact commit SHA. The out-of-band notification sent after push includes that SHA explicitly.
11. **Integration scope.** State that the branch was pushed but no PR, merge to `main`, or deployment was performed.

The post-pull frontend checks are:

```bash
git fetch origin
git show --stat --oneline origin/codex/backend-agents-import
git diff --name-only origin/codex/backend-agents-import^ \
  origin/codex/backend-agents-import -- \
  src public package.json package-lock.json scripts deploy tests
rg -n 'backend-agents|source-agent-contracts' src public scripts package.json
```

The diff and runtime-import search must be empty. Documentation links to the shared contract are expected outside these runtime paths.

## 12. Generative-content inventory

None. The implementation performs deterministic filesystem relocation, ignore-rule updates, navigation edits, local link checks, and Maven tests using scripted or recorded providers. It invokes no LLM, image generator, OCR system, source scanner, live model provider, candidate-generation workflow, source capture, freeze, package, publication, or deployment process.

## 13. Acceptance summary

The restructuring is complete only when all of these statements are true:

- the frontend remains at repository root with no runtime, build, command, package, test, or deployment path change;
- the full approved backend Agent source and documentation surface is tracked under `backend-agents/`;
- the language-neutral contract is tracked only at `shared/source-agent-contracts/README.md`;
- the two checkpoint-tracked progress files are the only old-path files eligible to appear as renames (or delete/add pairs), and every other backend workspace file appears as a new target-path addition; `repository-structure-zh-cn.md` is an ordinary new backend progress record, is `COMPLETE`, and does not alter the two-file lifecycle;
- local/generated/backend build artifacts and the modernization stub are absent from the index;
- all active navigation links point to the new paths;
- `docs/design/repository-frontend-backend-structure.md` and `docs/design/repository-frontend-backend-structure.zh-CN.md` have resolvable near-top cross-links and are materially aligned language renderings of the same architecture;
- the targeted and full Maven module tests pass from the new path;
- the two pre-existing dirty documents are preserved and excluded from the migration commit as specified;
- one atomic implementation commit, following the separate design-specification checkpoint, is pushed to `origin/codex/backend-agents-import`;
- the frontend handoff provides the exact post-push identity and unchanged-frontend guidance;
- no PR, merge, deployment, source access, model call, generation, or deletion occurred.
