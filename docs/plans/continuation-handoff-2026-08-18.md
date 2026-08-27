# Linguan 管伊佳五源标准化工作台：继续实施交接

> 更新时间：2026-08-19（内容优先五源审阅重构进行中；跨标签写入仍为后续 TODO）
>
> 工作目录：`/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`
>
> 目标：记录 CP1–8 的最终实现边界、证据与未来多标签 TODO；未来会话无需重做已完成工作。
> 最终独立复审：Spec PASS／Quality PASS，Critical 0／Important 0。

当前 Demo 允许多个浏览器标签访问；同一标签仍可通过“切换用户”完成作者／审核者流转。双标签同时写同一运行状态的浏览器竞争证明降为后续 TODO，不再阻塞本轮验收。内容优先工作台正在将正文、Markdown、依据和五源侧栏接入既有 CP8 seam；跨标签并发不作为本轮门禁。

本轮新增固定源快照：仓库内版本化的 `PinnedSourceSnapshotBundle` 已包含五份完整 `SourceDocumentCompilation`（读取摘要、九段 Markdown、识别项、断言、证据定位和冲突投影）。普通 Demo、刷新、代码改动和测试只读取它，浏览器缓存只是可丢弃的镜像；不会重新扫描或重新生成。仅显式 `npm run snapshot:guanyijia:refresh` 的维护工作可以重建 Bundle，且外部重新扫描需要单独授权。

最终浏览器边界已经稳定：主五源 Story 与 `SOURCE_DOCUMENT_SET` 均通过；黄金身份、五源持久 revision/ref/SHA、三个决定 Artifact、两类黄金 SHA、Catalog fingerprint 和完整对象计数均使用真实运行数据断言。Assistant persisted delta、历史 race、取消／确认、技术详情与决定投影的中间 RED 均已在对应直接测试和最终 Story 中关闭，不再作为当前待办。

最新 recorded-target 修复进一步保证：任何普通导航读取到另一条健康正文，都不能清除先前的 checksum/store 失败；只有对记录的原 `contentRef + expectedSha256` 做精确复验才解除 aggregate 写门禁。直接测试、Workbench、页面契约、类型检查与真实浏览器 checksum 路径均已通过，并由独立 reviewer 确认 closure。

## 1. 用户最终批准的目标

只修改 `linguan-prototype-v2`，把“数据标准化”实现为管伊佳五源 Codex 式连续工作台：

```text
设置来源
→ MySQL / GitHub / 官方文档 / 演示制度 Markdown / 派生 Semantica 逐源读取
→ 每源生成独立九段式 Markdown 与结构化块
→ 人工审阅和 revision
→ Git 式处理来源冲突
→ 真实审阅助手提出 Patch，用户确认后才修改
→ 生成交付物
→ 作者确认
→ 异人审核并自动定版
→ M4 语义零变化交接
```

最终不得修改管伊佳正式 V1，不创建个人草稿或 V2。零售旧流程、Backup、Java Demo、后端和远端服务不在修改范围。

## 2. 不可变黄金基线

未来继续前先保护以下值，任何变化都应 fail closed：

```text
Project: guanyijia_erp
Catalog: catalog_guanyijia_v1
Artifact: artifact-guanyijia-v1-40c8572864bd
Markdown SHA: 5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210
Semantic SHA: c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248
Counts: 14实体 / 9事件 / 296字段 / 30关系 / 4维度 / 5指标 /
        2层级 / 8规则候选 / 10同义词 / 9时间语义 /
        63待归类资产 / 2排除项
```

三类正式根来源只有 MySQL、GitHub、官方核心文档。演示制度是 non-formal；Semantica 是 derived，不能增加独立根证据。

## 3. 已完成 Checkpoint

### CP1：标准化运行与事件

- 深模块：`src/features/standardization-run/`
- 有序五源状态机、内容寻址事件、command fingerprint、原子 CAS、损坏状态校验、可恢复事件链接。
- 报告：`docs/plans/task-1-standardization-run-report.md`
- 独立审查：Spec PASS / Quality PASS。

### CP2：管伊佳五源直接编译

- 深模块：`src/features/guanyijia-standardization-story/`
- 每个来源直接从自身冻结快照编译九段式 Markdown、blocks、assertions、sections 和 evidence locators；禁止从最终 Artifact 反投影。
- 五源固定顺序：MySQL → GitHub → 官方文档 → 演示制度 → Semantica。
- 报告：`docs/plans/task-2-guanyijia-five-source-compilation-report.md`
- 独立审查：PASS。

### CP3：Codex 式时间线和事实检查器

- 页面仅对 `guanyijia_erp` 使用新 Workbench；零售保持 legacy。
- 真实时间线显示逐源读取、文档生成、审阅、冲突和回执；右侧只显示确定事实。
- 报告：`docs/plans/task-3-codex-timeline-workbench-report.md`
- 独立审查：Spec PASS / Quality PASS。

### CP4：结构化审阅、revision 和 Diff

- `SourceDocumentBlock`、`blocksRef`、内容寻址读取、CAS revision、legacy 无 blocks 严格只读。
- 编辑 label/value 同事务重算 blocks → assertions → sections → Markdown → SHA；真实 Before/After 和 Markdown 行 Diff。
- 报告：`docs/plans/task-4-structured-document-review-report.md`
- 最终直接测试 98/98；独立审查 Critical 0 / Important 0 / Minor 0。

### CP5：Git 式冲突解决

- Hunk：Current / Incoming / Corroborating / Result。
- 策略：KEEP_CURRENT / ACCEPT_INCOMING / MERGE / DEFER_AS_GAP。
- 单项 `RESOLVE_SOURCE_CONFLICT`；完整 Artifact、结构化 Patch、USER_CONFIRMED assertion、真实 LCS Markdown Diff、provenance。
- 决定顺序与来源事件生命周期严格校验；Semantica 佐证 receipt 绑定生成 revision。
- 报告：`docs/plans/task-5-git-conflict-resolution-report.md`
- 最终 109/109 + legacy 5/5；独立 Spec PASS / Quality PASS，Critical 0 / Important 0。

### CP6：真实审阅助手

- 文件：
  - `src/features/data-standardization/review-assistant.ts`
  - `src/features/data-standardization/review-assistant-types.ts`
  - `src/features/data-standardization/review-assistant-panel.tsx`
  - `src/features/data-standardization/review-assistant.test.ts`
- ASK、20条历史、确定性回答、打开目标、结构化 Proposal、真实 Preview、显式确认、相邻 document revision、同 Run 审计。
- 助手不能替用户解冲突、定版或交付。
- 多轮 reviewer 修复了 pending saga、事件时点、选择 coherence、页外 Proposal、容量、CAS 与确认完整性。
- 报告：`docs/plans/task-6-review-assistant-report.md`
- 最终 138/138；独立 Spec PASS / Quality PASS，Critical 0 / Important 0；一个非阻断旧 Minor 是共享 Run 历史曾把其他成员提问显示成“你”。CP8 已开始调整历史 AT/身份呈现，不要回退。

### CP7：交付物、异人审核定版和 M4 零变化

- 深模块：`src/features/standardization-deliverable/`
- 四个不可变内容：Source Manifest、九段合并文档、Decision Manifest、Governance Appendix。
- 作者确认绑定全部 core hashes；不同 reviewer 审核一次即冻结；pending Run event 可恢复。
- M4 使用显式 `ZERO_DELTA_MODELING_HANDOFF` Receipt，绝不走 draft/V2 路径。
- 双层比较：semantic payload deep compare + Catalog `deriveDraftObjectChanges() === []`。
- 报告：`docs/plans/task-7-deliverable-zero-delta-report.md`
- 最终 9组 212/212；fixture/tsc/build 通过；独立 Spec PASS / Quality PASS，Critical 0 / Important 0 / Minor 0。

## 4. CP8 已经实现的主体

CP8 Brief：`docs/plans/task-8-responsive-capacity-e2e-brief.md`。

### 已建立深模块

- `src/features/review-surface/index.ts`
- `src/features/review-surface/use-review-surface.ts`
- `src/features/review-surface/review-surface.test.ts`
- `src/features/review-window/index.ts`
- `src/features/review-window/indexeddb-index.ts`
- `src/features/review-window/review-window-recovery.tsx`
- `src/features/review-window/review-window.test.ts`

已实现或已转 GREEN：

- Document → Inspector → Assistant Patch 的嵌套逆序返回。
- 稳定 anchor、相对 scroll、focus、selection、draft 恢复。
- inline / overlay / fullscreen modal effects。
- Run revision 的受信 document layer migration。
- Timeline 40、默认页20、单页50、window80、overscan6。
- 10k issues / 100k Evidence 的生成式索引，不把全量数组或 Map 交给 renderer。
- 首屏 body read 0、signed keyset cursor、filter/epoch/tamper 拒绝。
- 离线、checksum、quota 的 typed review-window 错误投影基础。
- 390px 触控尺寸、`100dvh`/`svh`、safe-area、visualViewport CSS variables。
- Document 初页及 next cursor 页都通过 `readReviewCurrentDocument` verified façade；Assistant 首屏仅读 summary，生产 consumer model test body reads=0。
- Workbench aggregate recovery 已接入 stream-keyed fail-closed state：deliverable summary 不清 failure，retry/正文展开只有 verified read 后清理自身 failure；assistant summary/retry 真实重读 `ASSISTANT_HISTORY`，显式历史加载成功 verified read 后清理自身 failure，且首屏保持零正文读取；任意其他 failure 仍阻止全部内容依赖 mutation。
- assistant、Timeline、Issues、Evidence 的 failure target 现在按 stream 保留 stable key/contentRef/expectedSha；generic retry 不再靠 summary/token/当前 selection 清错，只能通过 recorded target 的 verified content read 成功清理；无 target、正文仍坏、item/ref 消失均保持 failure 和 mutation gate。
- capacity harness 的 CAS/quota/freeze pending 控件已接 `createCp8RecoveryScenarioAdapter`，真实 production failpoint 验证 metadata 不前移及 reload/reconcile；CAS UI 通过 production `reload()` consumer 读取 winner revision、清 stale preview、保留 draft/anchor。

### 已有浏览器基础设施

- `tests/e2e/guanyijia-five-source-story.spec.ts`
- `tests/e2e/standardization-capacity-errors.spec.ts`
- `tests/e2e/standardization-responsive.spec.ts`
- `tests/e2e/standardization-zoom.spec.ts`
- `tests/e2e/standardization-protected.spec.ts`
- `tests/e2e/cp8-harness/`
- `scripts/run-cp8-playwright.mjs`
- `tests/e2e/vite.cp8.config.ts`

审查前已跑通过一次的浏览器故事：

- 管伊佳五源 Story 1/1，约2分钟。
- Responsive 6/6。
- 首轮容量覆盖不完整，已由最终独立故障场景 7/7 取代。
- Protected 4/4。
- Core 7/7。
- 每个 runner 当时都报告 5202 closed。

上述结果是“首轮可运行证据”，不是最终验收；独立 reviewer 后来发现覆盖不足和若干生产 seam 未真正接入，见下一节。

### 首轮性能附件（仅作基线，不是最终结论）

`artifacts/cp8-metrics/capacity.json` 曾记录：

```text
warm interactive: 329.42 ms
cursor pages: 104.06 / 41.08 / 51.82 / 48.78 ms
window commit: 0.90 ms
cached content: 57.50 ms
DOM: 142
payload: 5,885 bytes
Long Task: 0
```

首轮 20 张图在 `artifacts/cp8-visual/`。人工检查已发现并修过两个真实问题：200% fixed layer 裁切、Assistant Patch min-content 横溢。后来 Spec reviewer 又发现 06 图 Ant message Portal 右侧裁切，因此必须重新生成并重新审阅视觉证据。

## 5. CP8 独立审查状态

首轮审查的 16 项 Spec Important 与 9 项 Quality Important 已全部完成 RED→GREEN 和浏览器闭环；旧“尚未关闭”清单已删除，避免将历史中间态误读为当前状态。已完成证据包括：

- `SOURCE_DOCUMENT_SET` 与 `MERGED_DOCUMENT` 两种浏览器闭环。
- 1440／1024／768／390／320／真实 200% 六视口完整状态矩阵。
- 助手目标返回焦点、真实取消、应用级 modal inert、Conflict 可访问名称及虚拟列表全局序号。
- 六类 review-window、正文按需校验、checksum／quota／store unavailable 写门禁。
- 容量与错误恢复 8/8、core 7/7、protected 4/4；每个 runner 均确认 5202 已关闭。
- 五源身份、三个决定 Artifact、黄金 Artifact／两类 SHA／Catalog fingerprint／完整对象计数均有精确断言。

最终候选复审曾剩余一项消费者层 Important：普通导航读取另一条健康正文时可能误清除旧完整性错误。当前实现已将 Timeline、Assistant、Document、Issues、Evidence 与 Deliverable 统一收口：只有精确复验已记录的原 `contentRef + expectedSha256` 才能清除失败；缺失或不同目标均保持 fail-closed。直接回归已由16项增加到17项并通过；独立 reviewer 已确认该项关闭，最终 Critical 0／Important 0。

范围调整：Demo 允许多标签访问；同一标签完成角色切换。多标签同时写同一运行状态的浏览器 CAS 竞争证明记录在 `future-multi-tab-standardization-todo.md`，不属于本轮验收。底层 CAS、revision 与完整性保护没有删除。

注意：父仓仍将整个 `linguan-prototype-v2` 视为 untracked，`git diff --check` 无法枚举其内容；最终继续保留定向 whitespace 扫描并如实记录。

## 6. 当前代码热点

继续前优先读这些文件：

```text
src/features/review-window/index.ts
src/features/review-window/indexeddb-index.ts
src/features/review-window/review-window-recovery.tsx
src/features/review-surface/index.ts
src/features/review-surface/types.ts
src/features/review-surface/use-review-surface.ts
src/features/data-standardization/guanyijia-standardization-workbench.tsx
src/features/data-standardization/guanyijia-workbench-runtime.ts
src/features/data-standardization/review-assistant-panel.tsx
src/features/data-standardization/review-assistant-types.ts
src/features/data-standardization/standardization-timeline.tsx
src/features/standardization-run/types.ts
src/features/standardization-run/runtime.ts
src/features/standardization-deliverable/runtime.ts
scripts/run-cp8-playwright.mjs
scripts/run-cp8-playwright.test.mjs
tests/e2e/cp8-harness/main.tsx
tests/e2e/guanyijia-five-source-story.spec.ts
tests/e2e/standardization-responsive.spec.ts
tests/e2e/standardization-zoom.spec.ts
tests/e2e/standardization-capacity-errors.spec.ts
tests/e2e/standardization-protected.spec.ts
playwright.config.ts
```

## 7. 继续实施顺序

下一次会话按此顺序恢复，不要重做 CP1–7：

1. 先读本文件、CP8 Brief、CP8 Report、CP7 Report。
2. 运行 `git status --short`，保护所有既有改动；不要修改 Backup、web-next、后端或远端。
3. 确认 5202 空闲：

   ```bash
   lsof -nP -iTCP:5202 -sTCP:LISTEN
   ```

   2026-08-18 本交接写入时该命令无输出，端口已关闭。

4. 不要重做已经关闭的六 stream、两种交付、六视口、容量、core 或 protected 工作。
5. 先复验最后的 recorded-target consumer 修复：review-window、Workbench runtime、data-standardization、类型检查和真实 Workbench checksum 浏览器用例。
6. 将稳定快照交回同一独立 reviewer，要求最后一项 Important closure；Critical／Important 必须为0。
7. 若 reviewer 无新增 Important，仅串行执行受影响的 lint／build／whitespace／5202 检查并完成最终报告。
8. 若 reviewer 提出新问题，继续遵循 direct RED→GREEN；不得降低 Brief，也不得重新把多标签浏览器 CAS 提升为本轮门禁。

## 8. 目标命令（必须串行）

先跑模块和直接回归：

```bash
cd /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2

npm run test:review-surface
npm run test:review-window
npm run test:standardization-run
npm run test:source-documents
node --experimental-strip-types --test \
  src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts
npm run test:review-assistant
npm run test:standardization-deliverable
npm run test:modeling-document-projector
npm run test:evidence-review
npm run test:data-standardization
npm run test:ai-modeling
npm run test:collaboration
npm run test:catalog-browser
npm run test:source-management
npm run test:semantic-evidence
npm run test:document-alignment
npm run fixture:check
npm run lint
npm run build
```

再串行跑浏览器：

```bash
npm run test:e2e:cp8:story
npm run test:e2e:cp8:capacity-errors
npm run test:e2e:cp8:responsive
npm run test:e2e:cp8:protected
npm run test:e2e:core
```

每条后检查：

```bash
lsof -nP -iTCP:5202 -sTCP:LISTEN
```

最终文本检查：

```bash
git diff --check -- linguan-prototype-v2
rg -n '[[:blank:]]+$' \
  linguan-prototype-v2/src/features/review-surface \
  linguan-prototype-v2/src/features/review-window \
  linguan-prototype-v2/src/features/data-standardization \
  linguan-prototype-v2/src/features/standardization-deliverable \
  linguan-prototype-v2/tests/e2e \
  linguan-prototype-v2/docs
```

注意：父仓中 `linguan-prototype-v2/` 整体是 untracked，`git diff --check` 不能独立证明其全部内容；必须保留 `rg` 补充检查。

## 9. 当前文档和证据入口

```text
README.md
docs/architecture/m3-m4-standardization.md
docs/ui-action-hierarchy.md
docs/demo-step-by-step.md
docs/plans/task-1-standardization-run-report.md
docs/plans/task-2-guanyijia-five-source-compilation-report.md
docs/plans/task-3-codex-timeline-workbench-report.md
docs/plans/task-4-structured-document-review-report.md
docs/plans/task-5-git-conflict-resolution-report.md
docs/plans/task-6-review-assistant-report.md
docs/plans/task-7-deliverable-zero-delta-report.md
docs/plans/task-8-responsive-capacity-e2e-brief.md
docs/plans/task-8-responsive-capacity-e2e-report.md   # 已同步至当前 CP8 direct/browser evidence
artifacts/cp8-metrics/capacity.json
artifacts/cp8-visual/01…20                         # 需按最终修复重新生成/复核
```

## 10. 明确不要做

- 不要重新生成或修改管伊佳黄金 Fixture、Artifact、SHA、Counts、Catalog、Seed、Fingerprint。
- 不要创建或发布个人草稿/V2。
- 不要把 Demo Policy 或 Semantica 提升为正式根证据。
- 不要把完整列表先送进 React 再 `slice` 来伪装分页。
- 不要使用 CSS zoom、root font-size 或单纯720px viewport冒充真实200%。
- 不要用本地 React state 假装 CAS/quota/freeze 恢复。
- 不要使用 `waitForTimeout`、条件可见性分支、hidden full DOM、broad console ignore 或 E2E-only production backdoor 获得通过。
- 不要在 E2E 结束后留下 5202 服务或 test dist。
- 不要运行全仓测试；只运行本文件列出的直接覆盖。
- 不要部署远端。

## 11. 完成条件

只有全部满足时才能关闭任务：

- Spec reviewer：PASS，Critical 0 / Important 0。
- Quality reviewer：PASS，Critical 0 / Important 0。
- 完整五源 Story、Source Document Set、容量/错误、六视口矩阵、保护回归、core 全绿。
- 20张最终视觉图逐张人工确认；真200%、Portal、Assistant Patch、Conflict、Deliverable均无裁切。
- 10k/100k 首屏正文读取为0、DOM≤300、页≤50、window≤80。
- 任何 checksum/store/CAS/quota/freeze pending 错误都 fail closed 且可恢复。
- 每个 E2E runner 结束后 5202 无监听，临时 test dist 已删除。
- 管伊佳黄金值、零售正式结果、草稿/V2数量保持不变。
- `task-8-responsive-capacity-e2e-report.md` 更新为与最终证据一致。

## CP8 responsive visual round final handoff — READY（2026-08-18）

- Final approved evidence: responsive + zoom 8/8 GREEN（约 1.2m）；zoom 1/1 GREEN（10.6s）；main five-source Story 1/1 GREEN（约 1.1m）；protected 4/4 GREEN（38.9s）。所有 runner 均 finish 后关闭 5202。
- `artifacts/cp8-visual` 当前为 21 张：`01–20` 加独立的 `06-720x500-lockfile-chromium.png`，无重复命名。`06-200-percent-real-zoom.png` 已在真 pageScaleFactor=2、720×500 bounds 通过后重生成。
- 21 张均已从 01 重新使用 `view_image` 人工复核：五种响应式 viewport、真 200%/lockfile 720×500、MySQL 结构化文档、Assistant Patch、三项 Git 式冲突、交付物生成/内容/独立审核/冻结、零售保护 V1/草稿隔离/旧版 M3/手机本体返回均通过。启动层 `message.config({ maxCount: 1, duration: 3 })` 后没有重复 Toast 堆叠、Portal 裁切或关键按钮遮挡。
- 本轮仅关闭 responsive visual round；容量、独立 Spec/Quality review 及其他 CP8 gates 仍按完成条件继续追踪。

## Capacity/error bounded handoff — 2026-08-18

- `tests/e2e/standardization-capacity-errors.spec.ts` is now split into fresh-page cases for capacity, offline/cursor, checksum, quota, CAS, freeze, and reviewer `STORE_UNAVAILABLE`; each case asserts page/console errors and leaves business failures fail-closed rather than reloading them away.
- `src/features/review-window/indexeddb-index.ts` exposes `retry(): Promise<void>`. It resets only the failed open promise and reopens the same index object. The new direct test covers a stateful factory: first `SecurityError`, retry delegation to real `indexedDB`, and successful query.
- `tests/e2e/cp8-harness/main.tsx` 当时曾用生产 IndexedDB index 展示 `STORE_UNAVAILABLE` 和“重试审阅索引”；当前内容优先工作台已将该能力降为技术实现，不再向普通审阅者展示无效重试按钮，索引失败时直接回退到当前运行的受限列表投影。
- 当时的 bounded slice 证据是 review-window + CP8 recovery 17/17 与 `npx tsc -b --pretty false` exit 0；随后 capacity/error 浏览器闭环已完成并确认 5202 关闭，见下方最终浏览器证据。本条仅保留为历史实现说明，不再表示待办。

### Freeze lazy-preparation timing correction — 2026-08-18

- Root freeze run identified a non-runtime RED: the first click starts lazy real five-source/deliverable preparation and disables the injection button; the old default 5s assertion fired before the pending state was ready.
- `standardization-capacity-errors.spec.ts` now asserts the injection button is disabled after the click and uses an explicit 30s timeout for `定版登记中`, while retaining pending/no-handoff/reconcile/handoff assertions. No sleep, reload workaround, production or harness change.
- Browser freeze/capacity rerun remains the next bounded step; this turn did not start a browser or 5202.

## Capacity/errors final browser evidence — READY (2026-08-18)

- Root-approved serial capacity/errors runner: 7/7 GREEN in 24.2s; isolated freeze case: 1/1 GREEN; every runner finished with TCP 5202 closed.
- Seven fresh-page scenarios are now recorded: capacity budget; offline + cursor recovery; checksum fail-closed; quota with unchanged metadata revision; CAS winner reload with preview cleanup and draft/anchor retention; freeze pending → reload/reconcile → handoff; and reviewer IndexedDB `STORE_UNAVAILABLE`.
- Reviewer recovery remains fail-closed for corrupted正文 and write storage, while a secondary index failure is non-blocking: the shell and current-run summaries remain readable and the compatibility detail is hidden from normal content review.
- Latest `artifacts/cp8-metrics/capacity.json`: interactive 1,366.67ms; cursor pages 98.98/44.63/59.85/32.77ms; cache body 59.01ms; window commit 1ms; payload 5,885 bytes; DOM 147; body reads 1; heap 20,195,768→28,457,308 bytes; observed long tasks 353ms and 50ms. These measurements are preserved as observed values.
- The earlier capacity line was a first-round incomplete baseline and is superseded; current evidence is 7/7. No further browser run is required for this bounded slice.

## Standardization deliverable bounded closure — 2026-08-18

- TDD RED was isolated to two attack fixtures and one brittle static assertion. The forged merged-document and governance-appendix attacks were rejected too early because their rewritten `DELIVERABLE_GENERATED` payloads did not also rewrite the six-entry `deliveryContentIndex`; the literal `查看九段合并文档` scan missed the runtime-projected `查看${item.title}` action.
- Test fixture GREEN: `rewriteGeneratedRunEvent` now recomputes the canonical generated payload and matching review-window index/content refs together. Both attacks now reach and preserve their intended deep unique-projection rejection.
- Production label seam GREEN: `guanyijiaDeliverableContentLabelFor` is the shared runtime label projection used for deliverable summaries; the test verifies `mergedDocumentRef → 九段合并文档` and the dynamic component action without relying on a hard-coded JSX sentence.
- Verification: targeted attack/label tests 3/3; `npm run test:standardization-run` 56/56; `npm run test:standardization-deliverable` 20/20 in 51.4s; `npx tsc -b --pretty false` exit 0. No browser/E2E/full suite was run.
- Files changed in this closure: `src/features/standardization-deliverable/standardization-deliverable.test.ts`, `src/features/data-standardization/guanyijia-workbench-runtime.ts`, plus the CP8 report, handoff, and SDD ledger.

## Reviewer recovery target and CAS winner closure — 2026-08-18

Bounded scope completed: Workbench/review-window recovery target integrity, capacity CAS harness winner binding, and removal of conditional visibility branches from `tests/e2e/core.spec.ts`. No Playwright or full suite was run.

- `readAndVerifyReviewWindowPage` now takes the recorded failure `contentRef + expectedSha256`, rejects missing targets, rejects pages that do not list that exact target, and verifies only that target. It cannot recover by reading the first summary item.
- Workbench assistant summary success no longer clears `ASSISTANT_HISTORY`; only explicit verified history reads clear it. Deliverable failure capture stores its exact target, and deliverable retry/body reads clear only on exact target match.
- `recoverCasConflict` requires real winner metadata (`revision`, `status`, timeline length), then performs stale-write and reload verification against that winner. The direct harness test proves incorrect winner metadata cannot clear preview; the happy path remains production-adapter based and retains draft/anchor.
- Core test selection now asserts the known initial closed formal browser and explicitly opens it after retail selection. Standardization generation explicitly asserts `生成来源文档` is visible before clicking; no conditional `isVisible()` branch remains in that path.

Direct verification:

```text
review-window direct                         16/16 pass
guanyijia-workbench-runtime direct           36/36 pass
cp8-harness/recovery-scenario direct          3/3 pass
npx tsc -b --pretty false                     exit 0
```

该 bounded slice 当时未启动浏览器；随后 core 7/7、protected 4/4、capacity/errors 8/8 和六视口矩阵均已完成且 runner 确认 5202 关闭。本条仅保留为历史审计边界，不再表示浏览器验证待执行。
