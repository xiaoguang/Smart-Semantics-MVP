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
- Locked design and planning gate: `19e1e28 docs: lock data standardization review design`
- Correction implementation: `3ae6b6e fix: restore review matters and early conflict decisions`
- At the time this handoff record was prepared, local `HEAD` was `3ae6b6e`
  and `origin/codex/data-standardization-review-ia` was `bd9c03a` (`ahead 2`).
  This record is committed immediately after that implementation commit; after
  its own commit and push, the branch must have no ahead/behind indicator.
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
│ ├─ 待确认事项：本来源建议、已准入跨来源比较和来源差异              │
│ ├─ 待补充资料：每项独立成行，不阻断后续来源                        │
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

Fixed UX rules:

- All scripted suggestions and admitted cross-source matters appear in
  `审阅事项`; no cross-source matter appears in `审阅结论`.
- Cross-source visibility is derived from the full admitted run, not the
  document currently open. Debt fields, negative stock and status 9 persist
  when a later source or Semantica is opened, in stable order.
- `待补充资料` is the only user-facing term for a gap. Reader-only projection
  turns recognisable `GAP` markers into individual Chinese rows; frozen
  Markdown source and copy behavior remain byte-for-byte untouched.
- After the local scripted suggestion for a source is saved, the first
  eligible source difference can be decided while that source remains
  `DOCUMENT_READY`. The saved decision neither completes the source nor loads
  the next one, and it locks later document revision/assistant changes through
  the command layer.
- Completing a source keeps the exact document, revision, tab and reading
  position open. The only continuation is the explicit `审阅下一个来源`.
- The conflict layer temporarily owns the only primary action,
  `保存当前决定`; closing it restores the source layer and its primary action.
- Use the stable `.guanyijia-pending-matters` CSS class, never a Chinese
  `aria-label`, for card layout. At a review-document container width greater
  than 959px, cards have three content columns; at 959px or below they have
  one. 390px, 320px and real 200% zoom have no horizontal overflow.
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

1. `19e1e28` created the authoritative design document and added the scoped
   planning gate: every future user-facing design is reviewed in Markdown (or
   a necessary visual) before implementation and then recorded in a current
   design document. Every future LLM/generative task must declare exact input
   and output identity, ideal and fatal acceptance criteria, deterministic
   validation, two bounded improvement rounds, a post-round decision rule and
   time estimate. This correction invoked no model or generative task.
2. The three-tab surface now uses the approved groups: `待确认事项`, individual
   `待补充资料`, and retained `已处理事项`. Cross-source comparisons no longer
   leak into conclusions or disappear when the reader moves to a later source.
3. The V6 reader adds a one-way presentation seam for contiguous `GAP 1：…`
   / `GAP 2：…` and related markers. It displays individual Chinese
   `待补充资料` text while preserving raw V6 Markdown, its revision, SHA and
   copying behavior.
4. The run and Workbench runtimes support early, ordered conflict resolution
   only after the unresolved conflict source's local scripted suggestion is no
   longer pending. This preserves document review status and revision, records
   the decision on the source, and rejects later source-document or assistant
   revision commands for that source.
5. Review cards have been restored using stable class-based CSS. Both local
   suggestions and cross-source matters use the same three-column card
   skeleton above the 959px review-container breakpoint, with safe single
   column behavior below it.
6. The existing nonblocking continuation behavior remains intact: reviewed
   documents stay readable, the reviewer explicitly chooses the next source,
   and all unresolved formal decisions still prevent result generation until
   the final decision moves the run to `READY_FOR_OUTPUT`.
7. Current product, demo, action-hierarchy and architecture documents now link
   to the locked design and describe the correction. No dated historical
   execution report or external plan was rewritten.

No implementation work remains. The only delivery action after committing this
record is to push `codex/data-standardization-review-ia`; do not merge, deploy,
back up, amend `835b802`, rebase or force-push.

## Final targeted verification

All commands below were run serially on 2026-08-28 and passed against the
implementation represented by `3ae6b6e`:

```text
node --experimental-strip-types --test \
  src/features/data-standardization/review-information-architecture-compact-contract.test.ts
  → 6 passed

node --experimental-strip-types --test \
  src/features/data-standardization/review-supplement-presentation.test.ts
  → 2 passed

node --experimental-strip-types --test \
  src/features/data-standardization/standardized-document-reader.test.ts
  → 8 passed

node --experimental-strip-types --test \
  src/features/guanyijia-evidence-factory/source-review-document.test.ts
  → 31 passed

node --experimental-strip-types --test \
  src/features/guanyijia-evidence-factory/source-review-visibility.test.ts
  → 6 passed

node --experimental-strip-types --test \
  src/features/data-standardization/source-admission-nonblocking-gaps.test.ts
  → 2 passed

node --experimental-strip-types --test \
  src/features/standardization-run/standardization-run.test.ts
  → 60 passed

node --experimental-strip-types --test \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts
  → 48 passed

npm run test:data-standardization
  → 80 passed

npm run test:standardization-run
  → 60 passed

npm run test:standardization-deliverable
  → 24 passed

npm run evidence:guanyijia:content:check
  → V6 snapshot `guanyijia-demo-content-v6-20260826`, 5 source reviews

npm run evidence:guanyijia:content:package:check
  → V6 content and browser package checks passed

npm run build
  → TypeScript and Vite production build passed (standard Vite bundle-size warning only)
```

The required direct single-worker browser command passed all 38 tests in about
8.7 minutes:

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
conflict decisions, stable cross-source visibility, per-item Chinese
supplement projection, unchanged Markdown source, and result-generation
blocking. The runner reported `127.0.0.1:5202 closed` at finish. The final run
had no page error, unexpected console error, or failure screenshot. Earlier
focused browser failures were stale assertions corrected by this work; the
final required runner is the authoritative result.

No full repository suite was run: the repository instruction and approved plan
restrict verification to directly affected coverage.

## Final invariant review

- `git diff --check` passed before the implementation commit.
- `git diff --stat bd9c03a` and `git diff --name-only bd9c03a` were inspected.
  The implementation diff contains only the locked design/planning gate,
  approved Workbench/runtime/UI/test seams, and current documentation.
- Confirmed absent from the name-only diff: generated pinned browser content,
  V6/V7 content files, formal Guanyijia V1, retail V1/V2, Pinned Bundle,
  golden summaries, original evidence and snapshots. The one file named
  `docs/guanyijia-demo-content-snapshot.md` is a current-fact document, not a
  frozen content asset.
- No LLM/generative call, source-system access, scanner, capture, package,
  freeze, V7 generator, deployment, backup, merge, rebase, force-push or
  Trash access occurred.
- `StandardizationRun` schema, persisted command shape and the
  `READY_FOR_OUTPUT` deliverable gate remain unchanged. Multi-tab concurrency,
  AI/ontology copy review and other modules remain outside this work unit.
