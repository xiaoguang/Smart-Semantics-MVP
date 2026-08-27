# Task 3：数据标准化与来源中心 UX 实施报告

## 完成范围

本任务只修改 M3 数据标准化、来源中心、三组直接测试和对应长期文档，没有修改 Task 4 的本体、指标、规则、同义词、时间语义、应用壳或导航业务页。

### M3 人类审阅主链

- 新增`review-workspace.ts`作为 UI 与 Task 2 `ReviewQueryService`之间的公开适配层。初始化只执行`runtime.review`、`getOverview`和`listIssues({ status: 'OPEN', limit: 20 })`；不读取 Evidence 正文。
- 新增`ReviewInspector`，右侧固定为“总览 → 问题 → 结论 → 证据 → 标准文档”。选择问题后才调用`getConclusion`，选择结论中的根来源后才调用`listEvidence({ issueId, rootSourceId, limit: 20 })`；下一页只使用服务返回的不透明游标。
- 问题支持状态、严重级别、当前已加载页搜索和按游标“加载更多”；证据支持根来源过滤和当前页搜索。两处都显示筛选后数量、当前已加载数量和查询总数，并明确当前页搜索的边界。存在后续开放问题页时，决定写回保持禁用；加载页经纯函数合并后才参与人工决定完成度计算。
- 删除旧页面对批次全部 Evidence ID、全部 Claim 分组和全部 Assertion 的直接`flatMap/map`渲染。总览只保留小规模冻结来源摘要；证据正文最多渲染当前 20 条服务页。
- 标准文档按九段章节审阅，显示当前文档状态、作者确认、异人审核、冻结状态和不可变修订链；r1-r8显示来源`delta`，人工决定版显示决定`delta`。页面动作按 Runtime 状态依次记录全部阻断决定、作者确认、异人审核、冻结和交给 M4，不能再从旧页面直接绕过审批冻结。
- 左侧保留共享来源／我的任务、六段 Agent 流水线和独立滚动对话；输入表单作为第三个固定网格行保持在底部。桌面保留左右双栏，390px 使用全屏右检查器。

### 来源中心

- 桌面仍为来源列表＋详情双栏。390px 下使用`mobile-list/mobile-detail`互斥状态：列表阶段不渲染详情，选择来源后隐藏批次摘要和列表，详情顶部提供“返回来源列表”。连接列表不再拥有`max-height/overflow`嵌套滚动，纵向滚动只属于`source-center-content`。
- 新增`ReadableTechnicalValue`。批次 ID／fingerprint、连接 ID、配置、凭据引用、读取范围、版本引用、快照 ID、来源快照 identity、快照 fingerprint和 Manifest Locator 均完整换行或展开显示，并提供复制按钮和`aria-expanded`；详情不再依赖`dd title`或单行 ellipsis读取完整值。Clipboard API 缺失或拒绝会转换成明确的“复制失败”状态。
- 每个视觉区域最多一个主动作：来源批次、单连接步骤、快照就绪、YAML确认和配置编辑分别只有一个 primary；次要操作保持默认或危险语义。

## TDD 记录

按公开 seam 逐组执行 RED → GREEN：

1. 先加入渐进查询行为测试。测试以`ERR_MODULE_NOT_FOUND: review-workspace.ts`失败；实现适配层后验证初始化调用序列严格为`review → overview + OPEN issues:20`，结论和 Evidence 调用只在显式展开后出现。
2. 再加入 M3 结构和 1440／390 响应式测试。测试因缺少`review-inspector.tsx`、旧两行会话网格、旧全量 Evidence／Claim／Assertion代码失败；拆出服务驱动检查器、固定输入和新样式后通过。
3. 再加入来源中心可读性和移动结构测试。测试因缺少`ReadableTechnicalValue`、缺少 list/detail 状态、旧`dd title`与连接列表嵌套滚动失败；实现技术值组件和移动互斥页面后通过。
4. 首次 TypeScript 检查发现`ReviewInspector`有一个未使用的`Space`导入；删除后重新检查通过。
5. 首轮独立复审构造出超过 20 条开放问题的边界：旧 UI 只显示首屏，却从 artifact 内部全量问题计算决定门禁。新增 21 条问题的 RED 行为测试后，问题页改为消费服务游标并合并已加载页；人工决定准备逻辑改为纯状态机，只有全部开放页加载、每个阻断项都选择 resolution 并填写理由后才生成一次原子输入。
6. 首轮独立复审指出移动导航、复制和决定链只被源码正则覆盖。新增可执行的决定准备、游标合并、来源列表→详情→返回和 Clipboard 成功／拒绝／缺失测试，并让生产组件消费这些公开函数。
7. 第二轮独立复审发现当前问题筛选会污染新 artifact 的 OPEN 缓存，且 Warning 的后续游标会误阻塞审核阶段。新增 artifact-change 重置与阶段门禁 RED；生产 UI 现在每次 revision 变化都硬编码查询`OPEN`并重置筛选，决定门禁只在`AWAITING_CONFIRMATION`生效。

## 1440 与 390 验证边界

本任务遵守“只跑直接测试＋tsc”的命令限制，没有运行全仓或 Playwright E2E。直接结构测试分别锁定：

- 1440 契约：M3 `minmax(520px, 1fr) + minmax(400px, 38%)`双栏；来源中心`minmax(230px, 300px) + minmax(0, 1fr)`双栏。
- 390 契约：600px媒体查询、M3全屏检查器、固定输入、来源中心列表／详情互斥选择器、返回列表入口和无列表嵌套滚动。

视觉像素级多视口 E2E 属于 Task 5；本任务未将静态结构断言描述为浏览器截图验证。

## 直接验证

- `npm run test:data-standardization`：8/8 通过。
- `npm run test:source-management`：16/16 通过。
- `npm run test:modeling-document`：13/13 通过。
- `npx tsc -b --pretty false`：通过，无诊断。

## 独立只读复审

首轮复审为 Spec FAIL／Quality FAIL：无 Critical；两项 Important 分别为第 21 条以后问题不可达、关键 UI 只用源码正则测试；一项 Minor 为 Clipboard 拒绝未处理。第二轮复审确认首轮三项已关闭，但新增两项 Important：artifact 变化时 OPEN 缓存被当前筛选污染，以及审核阶段被可保留 Warning 的后续游标误阻塞。两项均已补可执行 RED 并修改生产状态函数。

第三轮最终复审：

- Spec verdict：PASS。
- Quality verdict：PASS。
- Critical／Important／Minor：均无发现。

Reviewer 最终确认：artifact 变化固定重建 OPEN 状态；决定门禁仅作用于待作者确认；按钮、提示和执行函数消费同一门禁；21 条问题、显式决定、筛选重置、各审批状态的 Warning 游标、移动导航和 Clipboard 失败均有可执行覆盖。
