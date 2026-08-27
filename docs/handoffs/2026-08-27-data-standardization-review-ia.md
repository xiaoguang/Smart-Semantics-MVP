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
- The feature implementation is intentionally still uncommitted and requires
  review and verification before its final commit and push.
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

## Work completed before handoff

1. Git baseline was committed and pushed to `main`; the feature branch was
   created before implementation.
2. Three RED contract test files were committed in `eaf3516`.
3. Default-space implementation is present in the working tree:
   persisted invalid/retail selections normalize to Guanyijia, the visible
   retail selector entry is filtered, and `CONTEXT.md` was updated. The LLM
   maintenance and Codex context-safety rules are committed together with this
   handoff. A scoped read-only review reported PASS; its targeted policy test
   recorded 5 passing tests.
4. Nonblocking source advancement and negative-stock evidence changes are
   present in the working tree. The producer ran its direct test group with
   66 passing tests and no failures. Independent review was interrupted by the
   memory incident and is still required.
5. Three-tab UI/projection and directly conflicting test updates are present
   in the working tree, but the producer did not leave a completion report.
   Treat this area as incomplete until its diff is reviewed and the focused
   tests pass.
6. No final TypeScript/build/content/E2E verification has been run after the
   combined implementation. Nothing in the uncommitted implementation should
   be called complete yet.

## Current modified implementation surface

Use `git status --short` as the authority. At handoff it includes:

- Review projection/workbench and related unit/E2E contracts.
- Standardization runtime and nonblocking-gap tests.
- Source-review visibility and verified GitHub negative-stock presentation.
- Default workspace normalization and selector filtering.
- `src/features/data-standardization/CONTEXT.md` and two existing architecture
  contract documents. `AGENTS.md` and this handoff are committed separately
  before the clean continuation task is created.

The two architecture-document edits must be checked for scope: update current
facts only; do not turn them into a new authority design or a cross-document
rewrite.

## Remaining work, in order

1. Review every uncommitted diff for scope and correctness. Pay special
   attention to the incomplete three-tab work, runtime state combinations,
   runtime absence of filesystem/source access, and the two architecture docs.
2. Make the three compact RED tests green without changing V6 or the approved
   page contract.
3. Confirm `审阅事项` contains pending matters, individual gaps, and completed
   decisions; confirm all relevant user-visible gaps are reachable there.
4. Confirm `审阅结论` shows core conclusions and a default-open object
   directory without duplicate descriptions or the old `31 项可追踪对象`
   treatment.
5. Confirm a reviewed source remains readable and `审阅下一个来源` advances
   correctly.
6. Confirm start-next is nonblocking while final delivery still rejects an
   unresolved formal decision.
7. Run the approved targeted verification serially, fix only failures directly
   caused by this work, then perform a final diff/invariant review.
8. Commit the implementation on this feature branch and push the branch. Do
   not deploy or merge.

## Required targeted verification

Run serially:

```bash
node --experimental-strip-types --test src/features/data-standardization/review-information-architecture-compact-contract.test.ts
node --experimental-strip-types --test src/features/data-standardization/source-admission-nonblocking-gaps.test.ts
node --experimental-strip-types --test src/features/model-projects/model-project-ui-policy.test.ts
npm run test:data-standardization
npm run test:standardization-run
npm run test:standardization-deliverable
npm run evidence:guanyijia:content:check
npm run evidence:guanyijia:content:package:check
npm run build
```

Then run only the directly relevant five-source, review/re-entry, and responsive
Playwright stories at 1440, 1024, 390, and real 200% zoom. Do not run the full
repository suite.

## Invariants

- Formal Guanyijia V1, retail V1/V2, Pinned Bundle, golden summaries, original
  evidence, snapshots, and backups remain unchanged.
- V6 remains active and retains five source identities, nine sections per
  source, 30 MySQL tables, 6 procedures, 43 GitHub conclusions, 69 GitHub
  evidence fragments, 56 terms, and 80 relations.
- Ordinary startup, tests, build, and demo do not access source systems, run a
  scanner, or call Codex/LLM.
- Do not add multi-tab concurrency correctness, AI/ontology copy review, or
  other modules to this work unit.
