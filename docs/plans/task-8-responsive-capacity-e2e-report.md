# Task 8：响应式、容量、错误恢复与完整五源 E2E 实施报告

## 当前结论

Checkpoint 8 已完成直接验证、浏览器回归与最终独立复审。用户已把 Demo 限定为单浏览器标签：作者、审核者在同一标签切换用户；双标签同时写同一运行状态的浏览器竞争证明已移入 `future-multi-tab-standardization-todo.md`，不再是本轮门槛。单页陈旧 revision、CAS fail-closed、精确胜者 reload 与 pending saga 回归仍保留。最终 verdict：Spec PASS／Quality PASS，Critical 0／Important 0。

最新 CP8 基线仍覆盖六视口、容量、core、protected 与 checksum fail-closed；本轮内容优先重构允许多个浏览器标签访问，不再使用单标签门禁。跨标签同时写入同一运行状态继续记录为后续 TODO。1024px 的事实检查入口曾遮挡助手发送按钮，已通过按助手真实高度上移修复；移动端审核者切换明确执行“返回时间线 → 切换用户”，不穿透模态层。

每次 runner 都打印 `[cp8-port] finish: 127.0.0.1:5202 closed`，最终 `lsof -nP -iTCP:5202 -sTCP:LISTEN` 无输出。独立 reviewer 已确认最后一项 recorded-target consumer 修复关闭；不存在已知未覆盖 Critical 或 Important。

## 深模块与 React 集成

- `src/features/review-surface/index.ts` 只处理稳定 layer、anchor、cursor、revision、focus ID 与草稿；`use-review-surface.ts` 执行声明式 DOM effects。Document→Inspector→Patch 按栈逆序恢复，overlay/full-screen 先解除 inert/body lock 再恢复底层 focus；Run revision 清除陈旧 preview，受信 document layer migration 保持 r1→r2 的 section/block/anchor。
- `src/features/review-window/index.ts` 统一六类 review stream。cursor 以签名绑定 schema/run/epoch/stream/filter/direction/stable key；默认 20、Timeline 40、page 50、window 80、overscan 6。`indexeddb-index.ts` 保存浏览器 Evidence 索引，生成式容量 adapter 不物化 100k 数组/Map；`readContent` 才读取正文并核对 SHA。
- 管伊佳 Workbench Timeline 已接入 review-window；Document 初页和后续 cursor 页都由 `readReviewCurrentDocument` 读取并以 verified blocks 投影到 React，不能通过 summary-only `readReviewWindow` 绕过正文校验。Inspector 与 Assistant 通过 review-surface 执行 focus／scroll／inert／dialog 合同。`ResizeObserver` 把助手真实高度投影到正文 padding；`visualViewport` 的 width／height／offset 写入 CSS 变量，使 200% 缩放下 fixed layers 仍完全位于可见区域。
- `/cp8/` 容量 harness 是独立 Vite build entry：10k／100k 列表仍只通过生产 review-window interface；单页陈旧 revision、quota、freeze pending 控件使用 `createCp8RecoveryScenarioAdapter()`，按故障动作懒加载真实 production metadata/run/cache/deliverable adapters。该 CAS 场景验证单页恢复合同，不作为双标签并发写证明。

## 容量、性能、可访问性与错误恢复

- 容量首屏：Timeline 40、Issues 20、Evidence materialized 0、body reads 0、query 2、序列化 payload `<256 KiB`、surface（含 Portal）DOM `≤300`。继续读取四页后 window=80、可见 rows=50、DOM 仍 `≤300`。
- 浏览器预算附件 `artifacts/cp8-metrics/capacity.json` 记录本机 warm shell interactive `329.42 ms`、四次 cursor page `104.06/41.08/51.82/48.78 ms`、React window commit `0.90 ms`、离线缓存 SHA 校验并显示 `57.50 ms`、DOM `142`、payload `5,885 bytes`、body reads `1`、Long Task `0`。JS heap 从 `3,062,552` 到 `5,548,492 bytes`（delta `2,485,940`）只作为本次附件记录，不设跨机器绝对门槛。首屏 Evidence／Markdown／Assistant reply／Deliverable body 均为 0；模块测试另证明单页 50、anchor offset 0、100k materialized count 0。
- 响应式用例逐状态断言无横向溢出、44×44 触控、safe-area／keyboard 可见区、dialog/aria-modal、focus trap、Escape、stable anchor 与主区最多一个 Primary。工作区 axe serious/critical 为 0；键盘覆盖 Tab、Shift+Tab、Enter、Space、Radio 方向键和 Escape。
- `OFFLINE_NOT_CACHED` 只允许重新读取且缓存正文仍可读；`CURSOR_EXPIRED` 重读同 filter、无重复并播报更新；`CHECKSUM_MISMATCH` 隐藏损坏正文且没有恢复写动作；`CAS_CONFLICT` 保留助手草稿／选择／anchor、清 preview 且不重放；`QUOTA_EXCEEDED` 显示 estimate、metadata revision 不前移；`FREEZE_EVENT_PENDING` 隐藏 handoff 并只允许继续登记。

## 完整故事与保护证据

- 五源顺序与每份九段文档、revision/ref/hash 均从真实 Workbench/Run/SourceDocument 路径产生；结构化修改完成 preview→confirm，刷新后新旧 revision 均可读。
- CP5 按 first-unresolved 完成 debt、negative stock 与 status 9，并保留 current-stock-as-of gap；每项浏览器步骤显示 Hunk 三侧／Result、中文理由、真实行 Diff 和影响对象。
- 助手覆盖来源、冲突、Evidence、影响对象、打开目标、未知请求和自由文本边界；Proposal 先取消一次、再明确确认一次，修改进入相邻 document revision。
- 交付物正文通过 lazy viewer 审阅，作者确认后切换异人 Reviewer 自动定版；M4 Receipt 显示语义变化 0、正式 V1 未修改、无草稿／V2并打开正式 Catalog inspector。
- 保护回归在 fresh contexts 验证零售正式 V1 identity/counts/read-only、个人草稿按用户和项目隔离、旧 M3 仍为八来源 runtime，以及 390px 手机本体详情返回同一列表。

## RED → GREEN 证据

1. 初始页面各层独立维护 open/focus/scroll，嵌套关闭丢 anchor 与草稿；纯 navigator 与 React effects adapter 接入后，稳定 ID 与相对顶部恢复误差保持在 2px 合同内。
2. 初始列表可以分页但 cursor 可跨 filter/epoch 复用，window 也会累积全量；签名 keyset cursor、50/80 上限、spacer 与 anchor 合并后跨查询拒绝且无重复。
3. 100k fixture 初始容易形成 renderer Map；生成式/IndexedDB index 将 materialized count 降为 0，首屏只传 40+20 summaries，body reads=0。
4. offline/checksum/quota/CAS/freeze pending 初始没有统一恢复模型；错误投影 seam 加入后每类只有契约动作，损坏正文和陈旧 preview 都不会保留写入口。
5. 五源 Story 首轮在结构化 r1→r2 后关闭了 Document layer；受信 layer migration 保持新 document、section 与 block 后转绿。
6. 助手 Evidence intent 与直接冲突边界曾落入错误分类；确定性 classifier 与 validator 对齐后，回答、Proposal 与禁止执行边界全链通过。
7. 移动时钟暴露 CP7 link-only reconciliation 改写 `updatedAt`，造成 Receipt 时间不一致；移除无业务 mutation 的时间改写并加入 moving-clock 回归后 handoff 恢复稳定。
8. Chrome 真 200% 首轮仍按 1440px fixed layer 宽度绘制，直接 bounds RED 为右边界 1440 大于 visual viewport 722；接入 `visualViewport` CSS variables 后 Document／Assistant／Inspector 全部进入 720×500 可见区。
9. 助手 Patch 的双栏 min-content 首轮把 preview 扩到面板外（right 1872.734 > panel 1004）；为 history/card/before-after/impact children 设定 `min-width:0`、局部换行与 100% 宽度后，Before／After、影响与确认动作均在面板内。
10. 旧 `core.spec.ts` 仍期待管伊佳走 legacy M3；改为断言管伊佳真实五源 Workbench，同时由独立 protected spec 固定零售旧 M3，避免把兼容期待写回新 domain。
11. 键盘 Story 新断言发现策略方向键会改变 Preview，但异步重投影后焦点回落到页面；把待恢复策略保存为稳定 ID，并在 React commit 完成后聚焦对应 radio，Space、ArrowRight／Left、Enter、Tab／Shift+Tab 与 Escape 全部转绿。
12. Document next-page 初始走 summary-only `readReviewWindow`，cursor 页没有经过 verified façade；把 Workbench next-page consumer 改为 `readReviewCurrentDocument`，直接测试证明相邻两页各20个 blocks、无重复且仅经过 blocks contentRef。
13. Assistant 首屏原本只在模块层证明 summary-only；生产 Workbench consumer 的 history projection 现在保持 `historyLoaded=false`、只显示“查看最近20条”入口，直接 React-consumer model test 在真实 runtime summary query 后确认 body reads=0。
14. capacity harness 原本用 metadata revision=7、React `setState` quota/freeze 以及本地 CAS 文案自证；改为 `createCp8RecoveryScenarioAdapter`，实际触发 Run CAS stale、ReviewWindow quota 和 Deliverable append-pending/reload/reconcile；aggregate guard direct test 逐条恢复 stream，最后一个失败流恢复前持续阻断写入。
15. reviewer I6 暴露 Workbench deliverable summary 只读 index 就清除 aggregate failure、deliverable body 成功不清除、assistant-history 只写局部 error、generic retry 不重读失败 stream；增加 summary→verified-content recovery seam，deliverable retry/正文展开在 verified read 后清自身 failure，assistant 首屏/retry 保持 summary-only，显式历史加载在 verified read 后清自身 failure，所有异常统一进入 aggregate fail-closed map。
16. reviewer I7 暴露 harness CAS 仍 `setPreview(false)` 本地假模拟；增加 `recoverCasConflict` production consumer，真实调用 stale-write 后 adapter `reload()`，读取 winner revision、清 stale preview、保留 draft/anchor，并由 direct scenario test 断言 `reload()` 实际调用一次。
17. reviewer 后续复审暴露 assistant generic retry、Timeline retry 及 Issues/Evidence effect 仍可 summary-success/selection disappearance 假清；增加 `VerifiedStreamRecoveryTarget` 与 `recoverVerifiedStreamTarget` production seam，按 stream 保存 contentRef/expectedSha target，四类 retry 只在真实 `readReviewContent` 成功后清对应 failure；无 target、正文失败、item 缺失均保持 aggregate gate。
18. 主 Story 的跨来源切换暴露旧 assistant history 请求覆盖新 context 的 race；先以 direct deferred-reader 测试复现旧 success 及 catch/finally 污染，再增加 `assistant-history-recovery` controller。controller request key 仍包含 actor/project/run/source/document/run revision/document revision/knowledge revision/assistant mutation sequence/requestEpoch，但 verified conversation 的 scope key 仅为 actor/project/run；同 scope migration 会失效旧 token、保留已验证 items/total/HISTORY，scope 改变才清空。same-scope direct 8/8 GREEN；主 Story 在首次 ask 的 line 332 history article 7/8 RED，未继续重试。
19. 两层直接取证确认 runtime event/history 本身完整：真实 START→MySQL review→GitHub→CONFLICT_BLOCKED→ASK 后，timeline 新增 `ASSISTANT_TURN_RECORDED`，立即 `readAssistantHistory` 返回 total=1、question/response/CONFLICT target；Workbench runtime 31/31。新增 `assistantHistoryContextFor` 与 `projectAssistantHistoryRecoveryState` pure seams 及 context consumer 3/3，确认 command snapshot 的 run revision/assistant mutation sequence/context key 变化并由 HISTORY commit；exact runtime integration 又覆盖 r1/r2/r3 与 Proposal status reconstruction，已加载 HISTORY 的 UI projection 保留已有 items 和完整 metadata。浏览器 line 332 现在保留 7 条但未呈现第 8 条，故不把 runtime/direct GREEN 误报为 UI GREEN。
20. 首次 ask 的纯 orchestrator RED→GREEN 暴露 `storeCommandSnapshot(next)` 早于 history read；抽出 `commitAssistantAskResult` 后，begin next context→await HISTORY→一次性 commit snapshot/history/error，success/failure/stale 三路 direct 3/3，并新增 8-item production-consumer commit direct，orchestrator 全集 4/4。随后为 effect 增加 `projectAssistantHistoryRecoveryState`：`begin(context)` 返回 `loadedMode: HISTORY` 时直接投影 items/pending/total/loaded 并跳过 summary，不再无条件降级 `historyLoaded`；SUMMARY/undefined context 仍为合法未加载空状态。exact runtime 真实确认/取消/事件时点校验 1/1、Workbench runtime 31/31 GREEN，但主 Story line 332 仍 7/8 RED，因此本轮冻结为 RED。
21. 浏览器仍在已有 7 条 conversation 上遗漏最新 ASK；先为 `reduceAssistantHistoryUiState` 写 module-resolution RED，再以同 scope eventId union、incoming status replacement、latest20、loaded fail-closed、scope reset 转 GREEN 3/3。Workbench 已移除 assistant history 的四个独立 React state setter，effect、reload、orchestrator commit 均经单一 atomic reducer，Panel props 从同一 state 派生；相关 direct 合计 18/18、tsc 0，但主 Story line 332 仍 7/8 RED，因此本轮冻结为 RED。
22. 最终独立复审发现普通消费者仍可能以“新内容读取成功”清除旧正文失败。先加入六 stream 精确目标测试并观察缺少 `acknowledgeVerifiedStreamTarget` 的 RED；实现后 Timeline、Assistant、Document、Issues、Evidence、Deliverable 只在已验证目标与记录的原 `contentRef + expectedSha256` 完全一致时清错。缺目标、不同正文或不同 SHA 均保持 aggregate fail-closed，只能通过显式“重新读取”复验原目标。review-window 17/17、Workbench 36/36、data-standardization 8/8、tsc 0，真实 Workbench checksum 浏览器用例 1/1。

## 当前 Fix Loop 验证边界与证据

本轮先以 deferred reader 直接复现跨 context race，再逐条转 GREEN；随后串行运行直接覆盖测试、TypeScript 和受限的主 Story runner。没有运行其他 Playwright、浏览器视觉或远端系统；runner 自带 build 仅用于启动该主 Story，不能替代独立 build 验收。

```text
CURRENT bounded technical-resolution lazy projection slice:
  RED: before implementation `readReviewShell` used full `standardizationRuns.read`, causing 65 content-store body reads; the new facade method was absent; tampered artifact and stale-revision cases were initially RED.
  GREEN: `node --experimental-strip-types --test src/features/data-standardization/guanyijia-workbench-runtime.test.ts` = 35/35; `npm run test:standardization-run` = 56/56; `npx tsc --noEmit` exit 0. Shell body reads/event payload reads are 0; facade toggle reads exactly 3 verified event payloads and returns 3 narrow summaries; tamper and stale revision fail closed.
  Browser: the strict-locator RED at line 182 was fixed with explicit accessible labels; main five-source Story = 1/1 GREEN, SOURCE_DOCUMENT_SET Story = 1/1 GREEN, and protected same-project cross-user isolation = 1/1 GREEN after the test-only 60s timeout for its real double-switch flow. Independent `npx tsc --noEmit` = exit 0; every runner closed 5202 and final lsof was empty. This browser-story bounded round is READY; responsive/capacity/core and other CP8 scopes remain outside this round.
RED (本 bounded review-fix slice):
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-recovery.test.ts
    initial RED: module not found for the new production controller; after implementation, the deferred A/B success and catch/finally cases were GREEN 2/2
    after the browser RED, two same-context late-summary success/reject cases initially failed by resetting state to the old revision
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-context.test.ts
    initial RED: `assistant-history-context.ts` module absent; projection RED then exposed missing export; after extraction, Workbench import, and loaded-HISTORY projection guard, consumer test GREEN 3/3
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-orchestrator.test.ts
    initial RED: `assistant-history-orchestrator.ts` module absent; after extraction, success/failure/stale orchestration GREEN 3/3; 8-item consumer case added, full direct GREEN 4/4
  node --experimental-strip-types --test --test-name-pattern='完整生产时序' src/features/data-standardization/guanyijia-workbench-runtime.test.ts
    initial RED: exact confirmation path threw `Runtime未配置Story Assistant确认revision校验器`; fixture was wired to the existing production confirmation validator, then NFKC colon and GitHub-document fixture expectations were corrected; exact runtime GREEN 1/1 without relaxing hash/coherence checks
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-ui-state.test.ts
    initial RED: `assistant-history-ui-state.ts` module was absent; after the reducer and atomic Workbench wiring, same-scope stale/summary, status replacement/latest20, and new-scope tests GREEN 3/3
  first main Story runner attempt
    runner build RED before browser: `ReviewWindowPage` was passed where assistant history items were required, and `nextCursor: null` was not normalized
  final bounded main Story runner
    browser RED: the first ask at spec line 332 has `getByLabel('审阅助手').locator('.guanyijia-review-assistant-history > article')` expected 8, received 7 after 5s; same-scope migration now preserves the existing conversation but the new turn is not rendered
  node --experimental-strip-types --test src/features/review-window/review-window.test.ts
    import failed: recovery-consumer.ts was absent (new verified-recovery seam not yet implemented)
  node --experimental-strip-types --test tests/e2e/cp8-harness/recovery-scenario.test.ts
    import failed: capacity-recovery-consumer.ts was absent (CAS reload consumer not yet implemented)
  after the first seam was added, the new CAS direct test still failed with
    `真实并发旧revision写入未被拒绝` until the adapter forwarded the captured stale revision to the production scenario.
  reviewer follow-up direct test initially failed because `recoverVerifiedStreamTarget` was not exported; after export, its target-reader contract RED exposed that the full target leaked into `readContent` instead of only contentRef/expectedSha.
GREEN:
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-recovery.test.ts
    tests 8 / pass 8 / fail 0 / exit 0
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-context.test.ts
    tests 3 / pass 3 / fail 0 / exit 0
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-orchestrator.test.ts
    tests 4 / pass 4 / fail 0 / exit 0
  node --experimental-strip-types --test src/features/data-standardization/assistant-history-ui-state.test.ts src/features/data-standardization/assistant-history-context.test.ts src/features/data-standardization/assistant-history-orchestrator.test.ts src/features/data-standardization/assistant-history-recovery.test.ts
    tests 18 / pass 18 / fail 0 / exit 0; Workbench Assistant history now has one atomic UI state setter
  node --experimental-strip-types --test src/features/review-surface/review-surface.test.ts
    tests 10 / pass 10 / fail 0 / exit 0
  node --experimental-strip-types --test src/features/data-standardization/guanyijia-workbench-runtime.test.ts
    tests 31 / pass 31 / fail 0 / exit 0
    includes exact r1→r2→CANCEL/CONFIRM→r3→MySQL/GitHub CONFLICT_BLOCKED→ASK→immediate readAssistantHistory integration
  node --experimental-strip-types --test src/features/data-standardization/review-assistant.test.ts
    tests 29 / pass 29 / fail 0 / exit 0
  npx tsc --noEmit
    exit 0 (before each Story attempt and after the reader-type repair)
  main Story runner, after the reader-type, orchestrator, loaded-HISTORY projection, and run-wide scope repairs
    build completed; browser reached the single test, then failed at the first ask with history article count 7 vs 8 (5s timeout); runner exit 1 and no further browser retries were allowed
    runner exit 1; `[cp8-port] finish: 127.0.0.1:5202 closed`
  one-run removable browser diagnostic (final conflict ASK; counts/ids only)
    execute-return runRevision 17/controller items 7; no final-ASK runtime-history-raw event with 8 items; controller-read items 7; orchestrator payload items 7; subsequent React items 7
    diagnostic logs and console listener removed immediately after the run; no behavior change made
  bounded persisted-delta slice direct evidence
    Workbench runtime 33/33; assistant-history orchestrator 7/7; assistant-history UI reducer 4/4; `npx tsc --noEmit` exit 0 after the minimal execute-return type repair
    one new main Story runner stopped at build TS2322 before browser; no Story rerun, SOURCE_DOCUMENT_SET, or protected command
    runner exit 1; `[cp8-port] finish: 127.0.0.1:5202 closed`
  latest main Story validation
    build passed and browser reached `assertTechnicalDetails`; RED at spec line 136: actual document IDs `source-document-3..7` (MySQL r3 at 3) versus expected `6,2,3,4,5`
    systematic read-only trace: source-document metadata is reset and runtime register/revise allocate monotonic IDs from `sequence`; no product/test change made
    runner exit 1; `[cp8-port] finish: 127.0.0.1:5202 closed`; SOURCE_DOCUMENT_SET, protected, and post-RED tsc were not run
  lsof -nP -iTCP:5202 -sTCP:LISTEN
    no output / exit 1 after the final runner
  npm run test:review-window
    tests 14 / pass 14 / fail 0 / exit 0
  node --experimental-strip-types --test tests/e2e/cp8-harness/recovery-scenario.test.ts
    tests 2 / pass 2 / fail 0 / exit 0
    real Run CAS/quota/freeze pending reload/reconcile plus winner-revision reload/draft-anchor consumer; duration 19.47s
  npx tsc --noEmit
    exit 0
  lsof -nP -iTCP:5202 -sTCP:LISTEN
    no output / exit 1 / port closed (no server started in this bounded round)
```

## 审查前历史基线

只运行 brief 列出的直接测试和 build-owned Playwright；没有运行全仓测试、远端服务或 fixture generator 写模式。Playwright 版本 `1.62.1`，实际 Chrome `151.0.7922.138`，固定 `zh-CN`、`Asia/Shanghai`、reduced motion、单 worker、retry 0。截图为 `artifacts/cp8-visual/01…20`，人工检查 `20/20`。

```text
L1/CP1–7 直接模块：tests 320 / pass 320 / fail 0 / exit 0
  review-surface 7/7；review-window 7/7；standardization-run 56/56
  source-documents 12/12；story+workbench 39/39；review-assistant 28/28
  standardization-deliverable 20/20；modeling-document-projector 4/4
  evidence-review 2/2；data-standardization 8/8；ai-modeling 73/73
  collaboration 22/22；catalog-browser 8/8；source-management 17/17
  semantic-evidence 10/10；document-alignment 5/5
fixture:check：exit 0
npm run lint：exit 0（仅既有仓库 warning；CP8 变更路径无 warning）
npm run build：exit 0（仅单 chunk >500 kB 提示）
Playwright：25 / pass 25 / fail 0
  story 1/1；capacity-errors 7/7；responsive 6/6；protected 4/4；core 7/7
每个 E2E runner：start 5202 closed；finish 5202 closed
git diff --check -- linguan-prototype-v2：exit 0
CP8 变更范围 trailing-whitespace：无匹配（rg exit 1）
CP8 E2E test.only／waitForTimeout／条件可见性分支：无匹配（rg exit 1）
最终 lsof 127.0.0.1:5202 LISTEN：无结果（exit 1，端口关闭）
```

## 独立只读复审

最终冻结快照独立复审结果：Spec PASS；Quality PASS；Critical 0／Important 0。唯一文档状态 Minor 已在本报告与 continuation handoff 中同步关闭。

## Responsive visual round — READY（2026-08-18）

- Root-approved browser evidence: responsive + zoom 8/8 GREEN（约 1.2m，5202 closed）；zoom 单独重跑 1/1 GREEN（10.6s，5202 closed）；main five-source Story 1/1 GREEN（约 1.1m，5202 closed）；protected 4/4 GREEN（38.9s，5202 closed）。
- Final visual set contains 21 PNG files: `01–20` plus the separate `06-720x500-lockfile-chromium.png`; there are no duplicate filenames or extra stale numbered artifacts. The true-zoom `06-200-percent-real-zoom.png` was regenerated after its first stale Portal capture.
- Manual review was restarted from image 01 after the final regeneration. All 21 images were opened with `view_image`: 1440/1024/768/390/320 surfaces, true 200% and lockfile 720×500, structured MySQL review, Assistant Patch, three Git-style conflicts, deliverable generation/content/independent review/frozen handoff, protected retail V1/draft isolation/legacy M3/mobile ontology return. No clipping, unreadable technical value, blocked primary action, or stacked-notice visual blocker remained after the `message.config({ maxCount: 1, duration: 3 })` startup fix.
- Accessibility/interaction evidence remains from the approved suites: conflict and assistant companion states are readable, Portal stays in the visual viewport, and the responsive state matrix retains the existing Axe and focus/return contracts. This closes the responsive visual round only; it does not by itself close unrelated CP8 capacity or independent-review gates.

## Capacity/error bounded round — STORE_UNAVAILABLE seam and test split (2026-08-18)

- The capacity/error browser spec was split into independent fresh-page cases: capacity budget; offline + cursor recovery; checksum fail-closed; quota revision protection; CAS reload/preview cleanup/draft retention; freeze pending → reload/reconcile → handoff; and reviewer IndexedDB `STORE_UNAVAILABLE` recovery. Each case captures page errors and console errors and does not reload to erase a business failure.
- TDD RED: the new direct review-window test failed with `TypeError: index.retry is not a function` for a stateful `IDBFactory` whose first `open()` throws `SecurityError` and whose retry delegates to the real factory.
- Production GREEN: `IndexedDbReviewWindowIndex.retry()` remains available to the technical adapter, while the user-facing Workbench treats secondary index failure as a compact compatibility reading mode. The shell remains readable and no invalid “重试审阅索引” action is shown;正文仍按需独立校验。
- Verification: `node --experimental-strip-types --test src/features/review-window/review-window.test.ts tests/e2e/cp8-harness/recovery-scenario.test.ts` = 17/17 pass; `npx tsc -b --pretty false` = exit 0. No browser or Playwright command was run in this bounded round; the split browser cases remain pending the approved serial runner.

### Freeze lazy-preparation test correction — 2026-08-18

- The freeze case's first browser RED was a test timing issue: `prepareFreezeScenario()` is intentionally lazy and performs the real five-source/deliverable preparation only after the injection click. The click leaves the injection button disabled while `recoveryBusy` is true; the prior default 5s assertion expired before the pending snapshot existed.
- Test-only fix: capture the injection button, assert it becomes disabled immediately, then wait for `定版登记中` with an explicit 30s timeout. The rest of the strict pending → no handoff → reconcile → handoff assertions remain unchanged. No sleep, reload-based recovery, production or harness change was made. Browser verification is pending the root-approved serial freeze/capacity runner.

## Capacity/errors final browser evidence — READY (2026-08-18)

Root's approved serial runner completed the seven independent fresh-page scenarios in 7/7, 24.2s; the isolated freeze case also completed 1/1. Every runner finished with TCP 5202 closed.

The seven scenarios and their verified boundaries are:

1. Capacity budget: 10k Issues / 100k Evidence, first paint body reads 0, payload 5,885 bytes, DOM 147 nodes, Timeline 40, Issue window 80, and no full Evidence materialization.
2. Offline and cursor recovery: typed `OFFLINE_NOT_CACHED` and `CURSOR_EXPIRED` recover through their declared actions while retaining draft, selection, and anchor.
3. Checksum mismatch: typed `CHECKSUM_MISMATCH`, corrupted body hidden, no recovery or mutation action exposed.
4. Quota: typed `QUOTA_EXCEEDED`; metadata revision remains unchanged.
5. CAS: production winner reload clears preview, retains draft/anchor, and does not replay the stale write.
6. Freeze: pending registration remains without AI handoff until reload/reconcile completes; only then is handoff exposed.
7. IndexedDB reviewer store: the same production index instance keeps typed storage errors for technical recovery; Workbench summary navigation can fall back to a bounded current-run projection, while content reads and mutations remain governed by verified references.

Latest `artifacts/cp8-metrics/capacity.json` records: interactive 1,366.67ms; cursor pages 98.98/44.63/59.85/32.77ms; cached body 59.01ms; window commit 1ms; heap 20,195,768→28,457,308 bytes (delta 8,261,540); body reads 1; long tasks 353ms and 50ms. These are observed measurements, not suppressed errors.

## Standardization deliverable bounded closure — 2026-08-18

### TDD RED

- The forged merged-document attack and forged governance-appendix attack both stopped at the new early `DELIVERABLE_GENERATED` review-window guard: `交付生成事件review-window索引与内容载荷不一致`.
- The static check for `查看九段合并文档` also failed because the action is intentionally projected as `查看${item.title}`, not hard-coded in the Workbench JSX.

### GREEN changes

- `rewriteGeneratedRunEvent` in the attack fixture now rewrites the canonical `DELIVERABLE_GENERATED` payload and its six-entry `deliveryContentIndex` together. Both attacks therefore pass the early integrity guard and still fail at their intended deep guards: the merged document must be the unique five-source revision projection, and the governance appendix must be the unique source-role/decision projection.
- The production runtime now exposes `guanyijiaDeliverableContentLabelFor` and uses the same label map that builds the deliverable review-window summaries. The test asserts the real `mergedDocumentRef → 九段合并文档` projection and the dynamic Workbench action shape instead of scanning for a brittle literal.

### Verification

- Targeted attack/label tests: 3/3 GREEN.
- `npm run test:standardization-run`: 56/56 GREEN.
- `npm run test:standardization-deliverable`: 20/20 GREEN (51.4s).
- `npx tsc -b --pretty false`: exit 0.
- No browser/E2E/full-suite command was run in this bounded closure.

## Reviewer recovery target and CAS winner closure — 2026-08-18

This bounded reviewer slice originally closed only the recovery-target, CAS-harness, and core-locator scope. The later final candidate completed the serial browser runs; the historical command boundary below is retained only to show what that slice itself established.

- Review-window recovery now requires the recorded failure `contentRef + expectedSha256`; it no longer selects `page.items[0]`. The recorded target must also be present in the returned summary page before its verified body can clear the failure. Missing targets remain typed `STORE_UNAVAILABLE`.
- Workbench assistant summary reads no longer clear `ASSISTANT_HISTORY` failures. Explicit history reads still clear only after the verified body path succeeds. Deliverable body errors record their exact target; deliverable retry and body expansion clear only when that same target is verified.
- The CAS recovery consumer now requires real winner metadata (`revision`, `status`, timeline length), performs stale write only after the caller has advanced the production winner, reloads the production adapter, and refuses to clear the preview if the reloaded winner does not match. A direct negative test covers forged winner metadata.
- `tests/e2e/core.spec.ts` removed conditional `isVisible()` branches from the retail selection and standardization generation path. The helper asserts the known initial closed browser state and explicitly opens it; the generation test asserts the button is visible before clicking.

Verification:

```text
npm run test:review-window                                      16/16 pass
node --experimental-strip-types --test \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts 36/36 pass
node --experimental-strip-types --test \
  tests/e2e/cp8-harness/recovery-scenario.test.ts                 3/3 pass
npx tsc -b --pretty false                                        exit 0
```

That historical slice did not start a browser. It was subsequently superseded by core 7/7, capacity/errors 8/8 and the final real-Workbench checksum 1/1 browser evidence; every runner reported TCP 5202 closed. Files changed in the original slice: `src/features/review-window/recovery-consumer.ts`, `src/features/review-window/review-window.test.ts`, `src/features/data-standardization/guanyijia-standardization-workbench.tsx`, `tests/e2e/cp8-harness/capacity-recovery-consumer.ts`, `tests/e2e/cp8-harness/recovery-scenario.test.ts`, and `tests/e2e/core.spec.ts`.
