# Data-standardization review IA handoff — updated 2026-08-28

This file is the durable continuation point for the approved Guanyijia
data-standardization review work. The current user-facing design is locked in
[`../design/data-standardization-review-experience.md`](../design/data-standardization-review-experience.md).
Continue from the current working tree; do not recreate a design from old
conversation history.

## Operational safety

- The originating Codex rollout reached about 5.2 GB and caused the core
  process to peak at about 26 GB physical memory on a 16 GB host.
- Do not resume implementation in the originating conversation.
- Start any continuation task without inherited conversation history.
- Do not use full-history delegation. If a later task genuinely benefits from
  parallel read-only review, use at most two sub-agents with no inherited
  history, this file, and a small task brief.
- Keep build, Playwright, and integration verification serial and bind command
  output.
- Do not read, restore, or delete the historical rollout material that was
  moved outside this repository.

## Repository and Git state

- Repository: `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`
- Remote: `git@github.com:xiaoguang/Smart-Semantics-MVP.git`
- Branch: `codex/data-standardization-review-ia`
- Baseline on `main`: `6af9fac chore: establish linguan prototype baseline`
- Immutable feature start: `835b802 feat: finalize source review information architecture`
- Earlier nonblocking continuation: `261569c fix: preserve nonblocking review continuation`
- Earlier design and planning gate: `19e1e28 docs: lock data standardization review design`
- Earlier correction implementation: `3ae6b6e fix: restore review matters and early conflict decisions`
- Complete-review and merged-preview design lock: `1580c24 docs: lock complete review and merged preview design`
- Complete implementation: `950f648 feat: complete review matters and merged preview`
- At the time this final handoff update was prepared, local `HEAD` was
  `950f648` and `origin/codex/data-standardization-review-ia` was `222c69a`
  (`ahead 2`). This record is the next documentation-only commit; push it with
  the two preceding commits and then verify the branch has no ahead/behind
  indicator.
- Do not amend, rebase, force-push, merge, deploy, create a backup, call an
  LLM, generate V7, or modify frozen V6 content.

Start any later inspection with:

```bash
cd /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2
git status --short --branch
git diff --check
git diff --stat
```

## Locked page contract

```text
来源审阅
┌────────────────────────────────────────────────────────────────────┐
│ ←  [待审阅] [已保存数据库快照]                  [唯一主操作]       │
│    来源标题、范围与状态                                            │
├────────────────────────────────────────────────────────────────────┤
│ [审阅事项]  [审阅结论]  [标准化文档]                               │
├────────────────────────────────────────────────────────────────────┤
│ 审阅事项                                                           │
│ ├─ 待确认事项：区段标题与计数                                      │
│ │  ├─ 本来源建议：提示色独立 Box                                   │
│ │  └─ 来源差异与比较：中性／蓝色独立 Box                           │
│ ├─ 待补充资料：当前精确来源第九章的每项独立成行                    │
│ └─ 已处理事项：已保存建议与来源差异决定，继续可回看                │
│                                                                    │
│ 审阅结论                                                           │
│ ├─ 核心业务结论                                                    │
│ └─ 默认展开的完整对象目录                                          │
│                                                                    │
│ 标准化文档                                                         │
│ ├─ 同一 Revision 的 V6 九章阅读版                                  │
│ └─ 同一 Revision 的只读 Markdown 源文                              │
└────────────────────────────────────────────────────────────────────┘
```

五份来源全部审阅、三项正式差异全部保存后，来源工作层进入只读
“标准化结果预览”。它继续使用 `审阅事项／审阅结论／标准化文档` 三页签：
三个页签都投影同一个 `runId + 精确来源 revisions + 决定清单 + Preview SHA256`
身份。审阅事项列全部补充资料和已处理决定；审阅结论按固定九章、每章五份来源
完整展示；标准化文档提供阅读版、Markdown 源文和复制。生成、定版和交接只改变
标题与唯一 Primary，不替换这份内容。

Fixed UX rules:

- All scripted suggestions and admitted cross-source matters appear in
  `审阅事项`; no cross-source matter appears in `审阅结论`.
- Cross-source visibility is derived from the full admitted run, not the
  document currently open. Debt fields, negative stock and status 9 persist
  when a later source or Semantica is opened, in stable order.
- `introducedConflictIds` is the formal source-difference authority. Every
  introduced ID is represented exactly once by an unresolved matter card or a
  saved item; a missing candidate adapter becomes a visible integrity error and
  blocks mutation or delivery instead of silently hiding the difference.
- `待补充资料` is the only user-facing term for a gap. Reader-only projection
  turns recognisable `GAP` markers into individual Chinese rows; frozen
  Markdown source and copy behavior remain byte-for-byte untouched. The active
  source page reads only its exact ninth chapter; the final preview aggregates
  all five sources.
- After the local scripted suggestion for a source is saved, the first
  eligible source difference can be decided while that source remains
  `DOCUMENT_READY`. The saved decision neither completes the source nor loads
  the next one, and it locks later document revision/assistant changes through
  the command layer.
- Completing a source keeps the exact document, revision, tab and reading
  position open. The only continuation is the explicit `审阅下一个来源`.
- The conflict layer temporarily owns the only primary action,
  `保存当前决定`; closing it restores the source layer and its primary action.
- Use the stable `.guanyijia-local-suggestions` and
  `.guanyijia-cross-source-matters` CSS classes, never a Chinese `aria-label`,
  for card layout. At a review-document container width greater than 959px,
  cards have three content columns; at 959px or below they have one. 390px,
  320px and real 200% zoom have no horizontal overflow.
- Do not redesign the outer workbench, assistant, right rail, timeline, or
  V6 nine-section reader.

## Approved domain behavior

- A missing-data item does not block source completion or
  `START_NEXT_SOURCE`. It remains in Markdown and is structurally excluded
  from AI-modeling candidates.
- An unresolved cross-source difference does not block
  `START_NEXT_SOURCE`. Unsaved formal decisions block only final result
  generation/finalization; `登记为缺口` is a valid saved decision.
- New runs record `CONFLICT_FOUND` when a source reaches `DOCUMENT_READY`.
  Existing runs with the old `DOCUMENT_REVIEWED → CONFLICT_FOUND` order remain
  readable and replayable. The persisted schema, command union and
  `READY_FOR_OUTPUT` delivery gate are unchanged.
- Negative stock: MySQL proves only that `minus_stock_flag` exists; frozen
  GitHub Java proves that code reads or uses it. DDL alone must not imply a
  policy. Policy comparison becomes decidable only after the policy source is
  admitted.
- Debt fields: deployed DDL lacks the fields while the GitHub migration record
  contains them; this is a structural difference.
- Document status: deployed `0/1/2/3/9` versus historical `0/1/2` is a
  version-record difference; the formal business meaning of status 9 remains
  a separate item of `待补充资料`.
- First entry, invalid saved workspace IDs, and the historical retail selection
  normalize to Guanyijia. The visible selector hides `零售经营语义模型` while
  retail V1/V2 data and fixtures remain intact.

## Completed implementation

1. `1580c24` extended the authority document with the approved complete-review
   and merged-preview contract. The scoped planning gate remains active: any
   later user-facing design is reviewed in Markdown (or a necessary visual)
   before implementation and written to a current design document. This work
   invoked no model or generative task.
2. `950f648` makes `introducedConflictIds` the run-wide authority for review
   matters. Debt fields, negative stock and status 9 remain visible after the
   reader moves to a later source. A missing candidate adapter becomes a
   visible integrity error and fail-closed write/delivery gate rather than a
   missing card.
3. The `审阅事项` tab now has a title-only `待确认事项` section followed by two
   sibling Boxes: `本来源建议` and `来源差异与比较`. The latter retains formal
   differences and preliminary comparisons; saved formal decisions stay in
   `已处理事项`. Stable classes restore three content columns above the 959px
   container breakpoint and one column below it.
4. `projectReviewSupplementMatters()` derives individual, Chinese
   `待补充资料` rows from the exact active source's V6 chapter nine. It preserves
   source ID, document revision and Markdown anchor without changing frozen
   Markdown, copying, revision or SHA. The database source exposes its four
   explicit rows in the browser contract.
5. The deterministic deliverable runtime exposes read-only `preview()`. It
   builds the complete nine-chapter, five-source document, source manifest,
   decision manifest and three-tab review projection from the same validated
   inputs. Its `Preview SHA256` is canonical; `MERGED_DOCUMENT` generation
   requires the matching SHA and verifies that the stored merged-document
   reference equals the preview reference.
6. The result stage renders the same complete preview before generation and
   after generation, freeze, handoff or refresh. The three result tabs expose
   matters, nine chapters × five source sections, and reader/Markdown/copy
   views from one preview identity. Preview errors hide the generate Primary.
7. Existing nonblocking continuation remains intact: reviewed documents stay
   readable, the reviewer explicitly chooses the next source, and unsaved
   formal decisions prevent only final generation until `READY_FOR_OUTPUT`.
8. README, demo, UI-action and architecture current-fact documentation now
   links to the authority design and describes the actual behavior. No dated
   historical report, external plan, V6 content or frozen artifact was
   rewritten.

No implementation work remains. The only delivery action after committing this
record is to push `codex/data-standardization-review-ia`; do not merge, deploy,
back up, amend `835b802`, rebase or force-push.

## Final targeted verification

All commands below were run serially on 2026-08-28 against the implementation
represented by `950f648` and passed:

```text
node --experimental-strip-types --test \
  src/features/data-standardization/review-information-architecture-compact-contract.test.ts
  → 9 passed

node --experimental-strip-types --test \
  src/features/data-standardization/review-supplement-presentation.test.ts
  → 3 passed

node --experimental-strip-types --test \
  src/features/data-standardization/standardized-document-reader.test.ts
  → 8 passed

node --experimental-strip-types --test \
  src/features/guanyijia-evidence-factory/source-review-document.test.ts
  → 31 passed

node --experimental-strip-types --test \
  src/features/guanyijia-evidence-factory/source-review-visibility.test.ts
  → 8 passed

node --experimental-strip-types --test \
  src/features/standardization-run/standardization-run.test.ts
  → 60 passed

node --experimental-strip-types --test \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts
  → 48 passed

node --experimental-strip-types --test \
  src/features/standardization-deliverable/standardization-deliverable.test.ts
  → 27 passed

npm run test:data-standardization
  → 80 passed

npm run test:standardization-run
  → 60 passed

npm run test:standardization-deliverable
  → 27 passed

npm run evidence:guanyijia:content:check
  → V6 snapshot `guanyijia-demo-content-v6-20260826`, 5 source reviews

npm run evidence:guanyijia:content:package:check
  → V6 content and browser package checks passed

npm run build
  → TypeScript and Vite production build passed (standard Vite bundle-size warning only)
```

Before the browser run, `lsof -nP -iTCP:5202 -sTCP:LISTEN` returned no listener.
The required direct single-worker browser command passed all 38 tests in about
8.9 minutes:

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

It covers the three-column wide review container, 1024/390/320 widths, real
Chrome 200% zoom, explicit next-source continuation, early and deferred
conflict decisions, stable cross-source visibility, separate local-suggestion
and source-difference Boxes, four individual database supplements, the
complete three-tab preview, Preview SHA binding, unchanged Markdown source and
result-generation blocking. The runner reported `127.0.0.1:5202 closed` at
finish. The final run had no page error, unexpected console error or failure
screenshot.

No full repository suite was run: the repository instruction and approved plan
restrict verification to directly affected coverage.

## Final invariant review

- `git diff --check` passed before staging and `git diff --cached --check`
  passed before `950f648`.
- `git diff --stat 222c69a` and `git diff --name-only 222c69a` were inspected.
  The diff contains only the locked design/current documentation, approved
  Workbench/runtime/UI seams and their direct tests.
- Confirmed absent from the name-only diff: generated pinned browser content,
  V6/V7 content files, formal Guanyijia V1, retail V1/V2, Pinned Bundle,
  golden summaries, original evidence and snapshots. The one file named
  `docs/guanyijia-demo-content-snapshot.md` is a current-fact document, not a
  frozen content asset.
- No LLM/generative call, source-system access, scanner, capture, package,
  freeze, V7 generator, deployment, backup, merge, rebase, force-push or
  Trash access occurred.
- `StandardizationRun` schema and persisted command union remain unchanged;
  the optional `expectedPreviewSha256` is a backward-compatible field on the
  existing deliverable generation command. `READY_FOR_OUTPUT` remains the only
  no-deliverable generation gate. Multi-tab concurrency, AI/ontology copy
  review and other modules remain outside this work unit.
