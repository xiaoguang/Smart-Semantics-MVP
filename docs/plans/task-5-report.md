# Task 5 最终验收与补缺报告

## 结果

Task 5 已把两条确定性主链、容量合同、隔离/反向消失行为和响应式验收落到直接测试与 Playwright。未部署远端。

## 生产补缺

- `document-materializer.ts`：通用标准文档基于 Sidecar V2 Catalog 物化时，按业务名称对账更新已有实体、事件、字段、关系、维度和指标，不重复追加整份模型。零售 R2 从唯一 Registry 更新 Sidecar；其他新增对象没有精确 support edge 时明确标记`NEEDS_CONFIRMATION`，不再把 Evidence 共现误判为语义支持。
- `catalog-browser-adapter.ts`／`collaboration/runtime.ts`：发布门禁要求 VERIFIED 对象有支持 Claim、每条 Evidence 被绑定 Claim 覆盖，并拒绝 systemCode 不属于当前模型项目的 Sidecar。
- `generate-ai-modeling-fixtures.mjs`：Fixture 检查由陈旧的“派生 Claim 自身至少两条 Evidence”改为验证`upstreamClaimIds`存在、上游 Claim 存在且来源声明的上游关系闭合，和唯一 Registry 合同一致。

## 新增验收

- `standardization-story.test.ts`
  - 零售 B01–B08 → r8 → 三项决定 r9 → 作者确认 → 异人审核 → 冻结 → M4 → 个人草稿 → 审核 → Catalog V2。
  - 管伊佳先经真实 SourceManagement + ModelingPipeline 形成 MySQL／固定 GitHub Commit Sidecar，再完成欠款字段冲突决定 → 标准文档 → M4 → 个人草稿 → 审核 → Catalog V1；SharePoint/Semantica 未绑定时不进入 Claim。
  - 验证项目、Catalog V1/V2、文档项目和用户私有草稿隔离。
- `evidence-review.test.ts`：10,000 个问题和 100,000 条证据索引；首屏只返回 20 个问题且证据加载器 0 调用，结论层仍为 0，根来源每个游标页只加载 20 条。
- `ai-modeling.test.ts`：移除来源产生来源／Claim／Locator 的负向 delta，移除内容全部消失；未知来源组合拒绝且不生成结果。
- `core.spec.ts`：数据标准化真实首屏验证 r8、25 条证据、3 个问题、0 个证据正文节点和审核 DOM 后代节点不超过 200。
- `task5-acceptance.spec.ts`与既有响应式套件合并覆盖 320／360／390／768／1024／1440 和 200% 文字放大。

## 脚本与文档

新增`test:evidence-review`、`test:standardization-story`、`test:e2e:task5`、`test:e2e:responsive`。README、M3/M4 架构、统一建模管线、协作领域词汇和`docs/demo-step-by-step.md`已更新为当前合同。

## 最终验证

以下命令按从证据、来源、文档、协作到浏览器的顺序逐项运行：

- `npm run test:evidence-registry`：13/13 PASS
- `npm run test:semantic-evidence`：10/10 PASS
- `npm run test:source-management`：16/16 PASS
- `npm run test:modeling-pipeline`：4/4 PASS
- `npm run test:modeling-document`：14/14 PASS
- `npm run test:data-standardization`：8/8 PASS
- `npm run test:evidence-review`：1/1 PASS
- `npm run test:standardization-story`：2/2 PASS
- `npm run test:guanyijia-story`：1/1 PASS
- `npm run test:ai-modeling`：73/73 PASS
- `npm run test:draft-comparison`：9/9 PASS
- `npm run test:collaboration`：20/20 PASS
- `npm run test:catalog-browser`：7/7 PASS
- `npm run fixture:check`：PASS
- `npx tsc -b --pretty false`：PASS
- `npm run test:e2e:core`：6/6 PASS
- `npm run test:e2e:responsive`：12/12 PASS

Playwright 控制台仍会显示现有 Ant Design deprecated API 警告（`Alert.message`、`Drawer.width`、`Space.direction`与静态`message`上下文）；不影响本任务行为验收，且本任务没有扩大这些调用面。

## TDD 记录

标准化主链首次 RED 在发布门禁失败：`正式模型对象缺少持久证据状态`。原因是通用 M4 物化只追加模型对象，没有更新 Sidecar V2 的对象绑定。完成最小生产修复后两条故事转绿。容量和响应式测试作为明确的验收合同补入；旧 Core E2E 对已经移除的“确认并冻结”按钮和手机隐藏桌面导航有陈旧选择器，已改为当前人类审阅主链与真实手机底栏。

第一次独立复审进一步发现最初修复仍会重复追加零售完整模型、按 Evidence 共现产生过宽 VERIFIED、管伊佳空基线丢失 Sidecar、错项目 Sidecar 可绕过，以及根字号不等价于 200% 浏览器缩放。第二轮 RED/GREEN 改为身份对账、唯一 Registry 精确投影、真实管线 Sidecar 基线、严格项目门禁与 720 CSS 像素等效 1440 物理像素的 200% 缩放验收，并新增正式计数和隔离断言。

第二轮复审又发现 R2 后缀判断可能把部分批次扩张成八来源，以及手写`proposal.changes`与真实 UI 保存路径分叉。第三轮 RED/GREEN 要求来源快照集合与 Registry 八个 R2 身份精确相等，并加入单 MySQL R2 反例；所有差异统一从`base → materializedData`推导。两条故事现在按真实 UI 顺序执行`openDraft(base) → materialize → saveDraft(explicit changes) → submit`，管伊佳复用 Pipeline V2 Sidecar 与稳定对象编码；无 Sidecar 的兼容文档使用诚实 schema1 承载维度而不伪造证据。

第三轮独立只读复审结论为 **Spec PASS / Quality PASS**。Reviewer 逐项确认历史功能发现全部闭合；报告计数和遗留手写 changes 死代码两项 Minor 已在交付前同步清理。
