# Data-standardization review IA handoff — 2026-08-27

This file is the durable continuation point for the approved Guanyijia
data-standardization review work. Continue from the current working tree; do
not regenerate the plan from conversation history.

## Operational safety

- The originating Codex rollout reached about 5.2 GB and caused the core
  process to peak at about 26 GB physical memory on a 16 GB host.
- Do not resume implementation in the originating conversation.
- Start the continuation task without inherited conversation history.
- Do not use full-history delegation. If parallelism is useful, use at most two
  sub-agents, start each with no inherited history, and point it to this file
  plus a small task brief.
- Keep build, Playwright, and integration verification serial and bound command
  output.

## Repository and Git state

- Repository: `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`
- Remote: `git@github.com:xiaoguang/Smart-Semantics-MVP.git`
- Branch: `codex/data-standardization-review-ia`
- Baseline on `main`: `6af9fac chore: establish linguan prototype baseline`
- Committed RED contract tests: `eaf3516 test: define remaining review experience contracts`
- Immutable feature start: `835b802 feat: finalize source review information architecture`.
- Completed implementation commit: `261569c fix: preserve nonblocking review continuation`.
- At the time this final record was prepared, local `HEAD` was `261569c` and
  `origin/codex/data-standardization-review-ia` was `835b802` (`ahead 1`).
  This record is committed immediately after that implementation commit; after
  its own commit and push, `HEAD` is the final handoff record and the branch
  must have no ahead/behind indicator.
- Do not create a directory backup, force-push, deploy, call an LLM, generate
  V7, or modify frozen V6 content.

Start with:

```bash
cd /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2
git status --short --branch
git diff --check
git diff --stat
```

## Approved page contract

```text
来源审阅
┌──────────────────────────────────────────────────────┐
│ 来源标题、范围、状态                    [完成审阅]   │
├──────────────────────────────────────────────────────┤
│ [审阅事项] [审阅结论] [标准化文档]                   │
├──────────────────────────────────────────────────────┤
│ 审阅事项                                             │
│ ├─ 待确认：需要用户选择或修改的事项                  │
│ ├─ 资料缺口：每项独立成行，不阻止读取后续来源        │
│ └─ 已处理：完成后继续保留，可只读回看                │
│                                                      │
│ 审阅结论                                             │
│ ├─ 核心结论：识别出的业务含义                        │
│ └─ 对象目录：默认展开的完整对象表                    │
│                                                      │
│ 标准化文档                                           │
│ ├─ 阅读版：保持当前完整九章体验                      │
│ └─ Markdown源文：同一Revision、只读                  │
└──────────────────────────────────────────────────────┘
```

Fixed UX rules:

- Extend `projectSourceReviewWorkspace()`; do not create a second workbench.
- Remove the visible `本次范围与限制` disclosure and redundant count pills.
- Show `GAP` to users as `资料缺口`, one item per row.
- Saved scripted or formal decisions move to `已处理`; they do not disappear.
- Core conclusions and object-directory entries are mutually exclusive.
- The object directory is open by default and keeps its count, source actions,
  and document anchors.
- Reuse the current V6 nine-section document reader unchanged.
- A reviewed source remains readable; its continuation is `审阅下一个来源`.
- Do not redesign the outer workbench, assistant, right rail, timeline, or
  standardized-document reader.

## Approved domain behavior

- A missing-data gap does not block finishing a source or starting the next
  source. It remains in Markdown and is structurally excluded from AI-modeling
  candidates.
- An unresolved cross-source difference does not block `START_NEXT_SOURCE`.
  Unsaved formal decisions block only final result generation/finalization.
  `登记为缺口` is a valid saved decision.
- Cross-source findings appear when every participating source reaches
  `DOCUMENT_READY`; user review completion is not required.
- Negative stock: MySQL proves only that `minus_stock_flag` exists; frozen
  GitHub Java proves that code reads or uses it. DDL alone must not imply a
  policy. Policy comparison begins only after the policy source is admitted.
- Debt fields: deployed DDL lacks the fields while the GitHub migration record
  contains them; this is a structural difference.
- Document status: deployed `0/1/2/3/9` versus historical `0/1/2` is a version
  record difference; the meaning of status 9 remains a data gap.
- First entry, invalid saved workspace IDs, and the historical retail selection
  normalize to Guanyijia. Hide `零售经营语义模型` from the visible selector but
  preserve retail V1/V2 data and fixtures.

## Final completion record

1. The three-tab surface is complete: `审阅事项` contains pending matters,
   individual gaps and retained decisions; `审阅结论` contains mutually
   exclusive core conclusions and a default-open object directory; `标准化文档`
   continues to render the unchanged V6 nine-section reader and same-revision
   Markdown source.
2. The Workbench now separates current readable document from first unresolved
   difference. Active `READING`/`DOCUMENT_READY` wins document selection;
   otherwise the highest-order read document is retained. Difference selection
   is stable by source order and introduced conflict order.
3. `CONFLICT_BLOCKED` and `READY` continue with `READ_NEXT_SOURCE` whenever a
   source remains pending. All five sources can be read before debt, negative
   stock and status 9 decisions are saved; unresolved differences still reject
   generation until the last decision transitions the run to `READY_FOR_OUTPUT`.
4. Completing a source preserves its document, revision, tab and reading
   position. Its only continuation action is the explicit `审阅下一个来源`.
   A timeline-opened conflict uses a temporary Review Surface layer with only
   `保存当前决定`; closing it restores the underlying source review. A saved
   decision advances only to the next difference or the result stage, never
   auto-loads a source.
5. Direct browser contracts cover the completed-document re-entry, conflict
   layer primary ownership, deferred five-source decisions, status-9 gap,
   negative-stock source lines, narrow layout and real 200% zoom. Two stale
   browser assumptions were updated: the status-9 finding belongs to review
   matters rather than review conclusions, and a short document need not force
   the outer workbench to scroll.
6. Current product and architecture facts were updated in `README.md`, the
   demo guide, content snapshot, UI action hierarchy, M3/M4 architecture and
   multi-source evidence governance. No new authority design document was
   created; the external plan and dated historical execution reports remain
   unchanged.

## Final implementation surface

`261569c` changes only the approved Workbench runtime/UI seam, its direct unit
and browser contracts, one narrow reader overflow rule, and the six current
fact documents. It does not change `CONTEXT.md`, generated content, V6,
formal V1/V2 data, source snapshots or pinned browser content.

No implementation work remains. The only delivery action after committing this
record is to push `codex/data-standardization-review-ia`; do not merge, deploy,
back up, amend `835b802`, rebase or force-push.

## Final targeted verification

All commands below were run serially on 2026-08-27 and passed:

```text
node --experimental-strip-types --test \
  src/features/data-standardization/review-information-architecture-compact-contract.test.ts
  → 5 passed

node --experimental-strip-types --test \
  src/features/data-standardization/source-admission-nonblocking-gaps.test.ts
  → 2 passed

node --experimental-strip-types --test \
  src/features/model-projects/model-project-ui-policy.test.ts
  → 5 passed

node --experimental-strip-types --test \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts
  → 47 passed

npm run test:data-standardization
  → 79 passed

npm run test:standardization-run
  → 59 passed

npm run test:standardization-deliverable
  → 24 passed

npm run evidence:guanyijia:content:check
  → V6 snapshot `guanyijia-demo-content-v6-20260826`, 5 source reviews

npm run evidence:guanyijia:content:package:check
  → V6 content and browser package checks passed

npm run build
  → TypeScript and Vite production build passed
```

`lsof -nP -iTCP:5202 -sTCP:LISTEN` returned no listener (exit 1), so no unknown
process was stopped. The direct single-worker browser run then passed all 37
tests in 8.4 minutes:

```bash
node scripts/run-cp8-playwright.mjs \
  tests/e2e/guanyijia-curated-review.spec.ts \
  tests/e2e/guanyijia-document-reentry.spec.ts \
  tests/e2e/guanyijia-five-source-story.spec.ts \
  tests/e2e/information-subtraction.spec.ts \
  tests/e2e/source-document-set-story.spec.ts \
  tests/e2e/standardization-responsive.spec.ts \
  tests/e2e/standardization-zoom.spec.ts
```

It covered 1440, 1024, 390, 320, fullscreen breakpoints and real Chrome 200%
zoom; no page error, unexpected console error or failure screenshot was
reported. The runner printed `127.0.0.1:5202 closed` at both start and finish.

Final pre-commit checks passed: `git diff --check`; `git diff --stat 835b802`;
and `git diff --name-only 835b802`. The diff contains only the approved
Workbench/UI/test/current-doc paths. No full repository suite was run.

## Final invariant review

- Confirmed unchanged by the final name-only diff: formal Guanyijia V1, retail
  V1/V2, Pinned Bundle, golden summaries, original evidence and snapshots.
  No backup, V7, deployment configuration or unrelated formatting change was
  created.
- V6 remains active and retains five source identities, nine sections per
  source, 30 MySQL tables, 6 procedures, 43 GitHub conclusions, 69 GitHub
  evidence fragments, 56 terms, and 80 relations.
- The final V6 checks, tests, build and browser runner used only committed
  resources; no source-system access, scanner or Codex/LLM call occurred.
- `StandardizationRun` schema, persisted command shape and the
  `READY_FOR_OUTPUT` deliverable gate are unchanged. Multi-tab concurrency,
  AI/ontology copy review and other modules remain outside this work unit.
