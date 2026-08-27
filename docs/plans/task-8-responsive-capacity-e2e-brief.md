# Task 8：响应式、容量、错误恢复与完整五源 E2E

## 目标

在 CP1–7 全部通过定向测试与独立审查后，用真实浏览器验证管伊佳五源故事、Codex 式审阅工作台、独立审核定版和 M4 零变化交接。验收同时覆盖 1440、1024、768、390、320、真实 200% 缩放、10k 问题、100k 证据、可访问性、失败恢复和测试服务清理。

CP8 不补造 CP1–7 的业务能力；任何前置功能未完成时，E2E 必须失败，不得用测试 Seed 伪造缺失页面或决定。

> 2026-08-18 范围调整：当前 Demo 限制为单浏览器标签，作者／审核者在同一标签切换用户。双标签同时写同一运行状态的浏览器竞争证明降为后续 TODO；本轮只保留单页陈旧 revision、CAS fail-closed 与 reload 恢复回归。

## 深模块

### `review-surface`

统一管理 Timeline、Document、Conflict、Assistant Patch、Deliverable 和 Inspector 的打开、关闭、嵌套返回、焦点、滚动和草稿恢复：

```ts
interface ReviewSurfaceNavigator {
  transition(input: {
    state: ReviewSurfaceState;
    event: ReviewSurfaceEvent;
    viewport: 'DESKTOP_INLINE' | 'DESKTOP_OVERLAY' | 'MOBILE_FULLSCREEN';
  }): SurfaceTransition;
}
```

领域状态只保存稳定 anchor、cursor、相对偏移、focus ID 和输入选择区，不保存 HTMLElement 或业务正文。React adapter 执行 focus、scroll、body lock、inert、aria-live 等声明式 effects。关闭、Escape、跨断点、run revision、cursor 过期和刷新都走同一 seam。

### `review-window`

统一 Timeline、Issues、Evidence summaries、Assistant history、Document blocks 和 Deliverable sections 的分页与正文读取：

```ts
interface StandardizationReviewWindow {
  read(request: ReviewWindowRequest): Promise<ReviewWindowPage>;
  readContent(request: VerifiedContentRequest): Promise<VerifiedReviewContent>;
}
```

- 默认 20，硬上限 50；Timeline 初始最多 40。
- opaque keyset cursor 绑定 schema、epoch、run、stream、filter、stable key 和 direction；不使用裸 offset。
- 前端 window 最多保留 80 项、overscan 6，淘汰远页时以 spacer/anchor 保持位置。
- 列表只返回 summary、contentRef 和 hash；正文只有用户展开后才读取并复算 SHA。
- 100k Evidence index 存在 IndexedDB adapter，不在 React renderer 构造全量数组或 Map。
- telemetry 记录 query、body read、bytes、duration 和 DOM 数量，不泄露正文。

## 响应式合同

| 视口 | 合同 |
|---|---|
| 1440×1000 | 主工作区 + 360–420px inline Inspector；助手固定在主列底部 |
| 1024×900 | 主列全宽；Inspector 为右侧 overlay |
| 768×1024 | Document、Conflict、Patch、Deliverable、Inspector 均为全屏子页 |
| 390×844 | 手机底栏、单列、所有触控目标至少 44×44 |
| 320×720 | 技术 ID/Hash 局部滚动或换行，页面无横向溢出 |
| 200% | 1440×1000 物理窗口按 720×500 layout 触发 `<900` 全屏合同 |

全屏层使用 `100dvh` 和 `svh` fallback，覆盖 safe-area；打开时底层不可 Tab、不可滚动。Overlay/fullscreen 必须 `role=dialog`、`aria-modal=true`、focus trap 和 Escape 关闭；desktop inline Inspector 不锁焦点。

200% 使用 CDP `Emulation.setPageScaleFactor({ pageScaleFactor: 2 })` 并读取 `Page.getLayoutMetrics()` 校验 visual viewport；不得用 root font-size、CSS zoom 或仅把 viewport 改成 720px 冒充。Safe-area 和虚拟键盘用 CDP override，验证 composer/CTA 不被遮挡。

## 焦点、按钮与可访问性

- 每个 Timeline row、Document block、Conflict、Assistant turn、Deliverable section 都有稳定 review anchor。
- 以 item ID + 相对顶部偏移恢复滚动，误差不超过 2px。
- Document、Inspector、Patch 嵌套关闭时逆序恢复；助手返回后恢复 textarea value、selection 和焦点。
- 每个状态可见主区最多一个 `[data-workflow-primary=true]`；composer submit 不算 workflow primary。
- Patch preview 出现时隐藏或禁用底层业务 CTA；Inspector 除关闭外不得有 workflow action。
- 虚拟行设置 `aria-setsize/aria-posinset`；Radio 支持方向键；错误 `role=alert`；加载和恢复用 polite live region。
- 引入 `@axe-core/playwright`，工作区 serious/critical 为 0；实测 Tab、Shift+Tab、Enter、Space、方向键和 Escape。

## 容量与性能预算

浏览器容量夹具通过与生产相同的 `review-window` interface 注入 10,000 issue summaries 和 100,000 Evidence refs。Seed 不得进入生产 build，也不得暴露全局测试后门。

硬断言：

- 初屏只读取 header、最多 40 条 Timeline summary 和 20 条 Issue；Evidence、Markdown、Assistant reply、Deliverable 正文读取均为 0。
- 首屏传给 React 的序列化 payload 不超过 256 KiB；任一 page 不超过 50 项。
- Review surface 连同 Portal 的 DOM 不超过 300；任一可见列表 row 不超过 50。
- Built preview/warm shell 下：工作区可交互 ≤2000ms，cursor/filter page ≤500ms，缓存正文校验显示 ≤300ms，window 更新 ≤100ms。
- 记录 heap 前后值、long tasks、body reads、bytes 和 DOM 作为附件；跨机器不设脆弱的绝对 heap 门槛。

## 错误恢复合同

1. `OFFLINE_NOT_CACHED`：缓存内容继续可读；未缓存正文不伪造，唯一恢复动作为“重新读取”。
2. `CURSOR_EXPIRED`：丢弃失效 window，从相同 filter 重读；保留可用 anchor，无重复项并播报“列表已更新”。
3. `CHECKSUM_MISMATCH`：不显示损坏正文，不允许 Patch、冲突或定版；技术详情显示 expected/actual SHA。
4. `CAS_CONFLICT`：不自动重放写命令；刷新 run、保留助手草稿/选择/anchor、清除旧 preview。
5. `QUOTA_EXCEEDED`：metadata/event 不前移，旧 revision 可读；显示 storage estimate，不自动删除用户数据。
6. `FREEZE_EVENT_PENDING`：显示“定版登记中”，禁止 handoff、二次审核或解冻；reload 自动 reconcile，失败时唯一动作“继续登记定版”。
7. 初始化、权限或 store unavailable：保留壳层和已读 metadata，不无限 spinner。

故障通过注入的 content、metadata、run、network adapter 和 failpoint 测试；不修改黄金 Fixture。

## Playwright 运行方式

- 先 build，再由 Playwright 独占启动 `vite preview --host 127.0.0.1 --port 5202 --strictPort`；`workers=1`、`retries=0`、`reuseExistingServer=false`。
- 每个 viewport 新建 context/page，不在同一 page resize 后复用布局状态。
- 固定 locale、timezone、clock、reduced-motion；新用例禁止 `waitForTimeout` 和 `if (isVisible/count)` 条件分支。
- 外层 runner 在开始前要求 5202 空闲，结束、失败、SIGINT、SIGTERM 后都探测端口已关闭；只报告未知占用，不 kill 未知 PID。
- 视觉快照用 lockfile 对应 Chromium，行为和真实 zoom 用 Chrome channel；约 18–20 张关键状态截图。禁止未经人工检查直接更新 snapshots。

## 分层测试

### L1：模块 seam

- `review-surface.test.ts`：全部 layer/viewport transition、嵌套恢复、revision/cursor fallback、single-primary。
- `review-window.test.ts`：10k/100k keyset、0 body first paint、page/window bounds、content hash、offline/cursor/quota。

### L2：响应式矩阵

`standardization-responsive.spec.ts` 在每个独立视口覆盖 Timeline、九段 Document、Conflict Hunk、Assistant normal/Patch、Deliverable review/frozen 和 Inspector，逐状态断言 overflow、bounds、primary、Inspector action、DOM 和 focus。

### L3：容量和错误

`standardization-capacity-errors.spec.ts` 覆盖 10k/100k 初屏、滚动淘汰和 anchor；offline、cursor、checksum、双页 CAS、quota、freeze pending。禁止未处理 pageerror 和 broad console ignore。

### L4：唯一完整管伊佳故事

`guanyijia-five-source-story.spec.ts` 不含条件分支，按一个连续故事执行：

1. 记录黄金 Artifact、两个 SHA、Catalog fingerprint、counts、draft=0、无 V2。
2. MySQL → GitHub → 官方核心文档 → 演示制度 → Semantica，逐源断言读取事件、九段文档、revision/hash；至少一项结构化修改 preview→confirm，旧 revision 可读，并刷新验证恢复。
3. 按 first-unresolved 处理 debt、negative-stock、status9、current-stock-as-of；逐项看 Hunk、依据和 line diff，写中文理由，验证决定 Artifact/hash/timeline。
4. 助手覆盖当前来源、冲突、证据、影响对象、打开目标、未知请求；修改先 preview，分别取消一次和确认一次；直接要求解冲突/交付必须被边界文案拒绝。
5. 生成 `MERGED_DOCUMENT`，作者确认；切换 `zhiyuan.xu` 独立审核并自动定版。另用小预置 READY run 覆盖 `SOURCE_DOCUMENT_SET`。
6. 交给 M4，显示“语义变化 0 / 正式 V1 未修改 / 未创建草稿或 V2 / 仅追加治理记录”。
7. 重新核对黄金 Artifact/SHA/Catalog/fingerprint/counts 完全不变，治理 appendix/receipt 新增且 hash 可读。

### L5：保护回归

Fresh context 验证零售 V1、个人草稿隔离、旧 M3 审阅和手机本体返回；证明新 review surface/window 未替换零售 domain/runtime。

## TDD 重点

先观察以下 RED：全量 Timeline DOM、首屏正文读取、100k Map 入 renderer、cursor 跨 filter/epoch、损坏正文仍可编辑、quota 后 metadata 前移、双页 stale CAS 自动重放、freeze pending 可 handoff、嵌套返回丢 anchor/draft、任一视口裁切、伪 200%、safe-area/keyboard 遮挡、focus escape、多个 primary、Inspector 业务动作、axe 严重错误、五源/助手/冲突/独立审核/M4 任一步缺失、零变化路径创建 draft/V2、E2E 条件分支假通过、5202 遗留。

## 验证命令

只运行直接覆盖：

```bash
npm run test:review-surface
npm run test:review-window
npm run test:standardization-run
npm run test:source-documents
node --experimental-strip-types --test src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts src/features/data-standardization/guanyijia-workbench-runtime.test.ts
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
npm run test:e2e:cp8:story
npm run test:e2e:cp8:capacity-errors
npm run test:e2e:cp8:responsive
npm run test:e2e:cp8:protected
npm run test:e2e:core
git diff --check
```

每个 E2E 命令结束后必须确认 5202 无监听。更新 README、M3/M4 架构、UI 动作层级、Demo 手册和 `docs/plans/task-8-responsive-capacity-e2e-report.md`。

完成后分别进行 Spec 和 Quality 独立复审；Critical/Important 必须为 0。视觉快照必须逐张人工检查并在报告记录浏览器版本、测试数量、viewport、axe、性能、DOM/body-read、错误恢复、端口清理和黄金/零售保护证据。

## 不可修改

- 管伊佳黄金 JSON/Fixture、Artifact ID、SHA、Counts、Catalog、Seed、Fingerprint。
- 不创建或发布 Draft/V2，不提升 Demo/Semantica 为根证据。
- 不改 CP1–7 历史事件/hash，不改零售 domain/runtime 语义。
- 不改 backend、legacy frontend、backup 或远端服务。
- 不使用 fixture generator、hidden DOM、CSS zoom、随机 sleep、E2E-only production backdoor 或 broad console ignore 获得通过。
