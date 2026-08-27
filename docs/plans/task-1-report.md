# Task 1：证据注册表与因果故事完成报告

## 结论

状态：`DONE_WITH_CONCERNS`

已完成唯一 Evidence Registry、零售 B01–B08 因果累积、三项指定 Blocker、Semantica 派生血缘、可见对象证据绑定校验，以及管伊佳 MySQL/GitHub 真实快照身份与四来源累积故事。唯一保留关注点是管伊佳尚无真实 SharePoint/Semantica manifest；实现将两者明确标记为 `NEEDS_CONFIRMATION`，不会伪造 snapshot identity、证据或来源主张。

## 修改文件

- `src/features/evidence-registry/retail-evidence-registry.ts`
  - 新建唯一 GR001–GR025 Registry，集中维护来源、Claim、语义、revision、Locator 和派生上游。
  - 提供零售与管伊佳因果故事编译器，以及 Fixture、候选模型、正式模型的通用对象证据绑定校验器。
- `src/features/evidence-registry/evidence-registry.test.ts`
  - 新增 Registry、B01–B08、血缘 gap、真实 identity、对象绑定和缺席来源约束测试。
- `src/features/ai-modeling/group-retail-source-fixture.ts`
  - 来源资产、Locator 和 Claim 改由 Registry 投影；三项 Blocker 改为净销售、机器人过滤和营业日归属。
- `src/features/ai-modeling/group-retail-model-fixture.ts`
  - 模型证据目录改由 Registry 投影，补齐 GR025；保留完整大模型并补足第 191 个同义词。
- `src/features/ai-modeling/group-retail-runtime.ts`
  - 仅暴露当前批次已加入来源的 Locator、Claim 和 Finding，禁止缺席来源主张。
- `src/features/ai-modeling/ai-modeling.test.ts`
  - 对齐 Registry 的来源顺序、名称和空批次无来源主张契约。
- `src/features/collaboration/retail-evidence-fixture.ts`
  - 冻结 sidecar 改为复用 Registry 的来源、证据、Locator、Claim 与 Semantica 上游血缘。
- `src/features/collaboration/catalog-browser.test.ts`
  - 固定完整模型的 191 个同义词契约。
- `src/features/source-management/types.ts`
  - 快照增加原始 `sourceSnapshotIdentity`。
- `src/features/source-management/fixture-adapter.ts`
  - 采集时保留 fixture 的真实 snapshot identity、fingerprint 和 manifest 引用，不用合成值覆盖。
- `src/features/source-management/seed-data.ts`
  - 零售来源模板复用 Registry；管伊佳 MySQL/GitHub 绑定真实 manifest identity、指纹和 Git commit。
- `package.json`
  - 新增 `test:evidence-registry` 定向测试命令。
- `README.md`
  - 最小更新唯一 Registry、来源顺序/累计数、三项 Blocker、派生血缘、对象绑定和管伊佳待确认边界。

以上修改均位于 `linguan-prototype-v2`。未修改 backup、web-next、Java 或远端；未提交；未删除用户文件。目标目录整体未纳入 Git，因此未用 Git 清理或覆盖既有改动。

## TDD 记录

所有实现循环均先运行测试确认失败，再实现并确认通过。

### RED 1：唯一 GR 语义

命令：

```bash
node --experimental-strip-types --test src/features/evidence-registry/evidence-registry.test.ts
```

摘要：`0 pass / 1 fail`；legacy 资料只出现 GR001–GR024，缺少 GR025，且同一 GR 编码在 legacy 与冻结 sidecar 中属于不同来源。

GREEN：建立 Registry，并让 legacy 来源资产与 sidecar 从其投影；`1 pass / 0 fail`。

### RED 2：零售因果故事

同一命令，摘要：`1 pass / 1 fail`；缺少 `buildRetailEvidenceStory`。

GREEN：形成 MySQL → GitHub → Semantica → SharePoint → MongoDB → Elasticsearch → MinIO → Kafka 的 B01–B08，累计 4/7/9/12/15/18/21/25；`2 pass / 0 fail`。

### RED 3：唯一 Locator

同一命令，摘要：`2 pass / 1 fail`；sidecar 的 GR001 Locator 只有类型和表名，未保留 Registry 的 datasource/schema。

GREEN：sidecar 逐条复用 Registry Locator；`3 pass / 0 fail`。

### RED 4：管伊佳故事与对象绑定

同一命令，摘要：`3 pass / 2 fail`；缺少管伊佳故事编译入口和通用对象绑定校验器。

GREEN：增加 MySQL → GitHub → SharePoint → Semantica 累积故事，以及候选模型/Fixture 对象绑定校验；`5 pass / 0 fail`。

### RED 5：真实运行时 identity

同一命令，摘要：`5 pass / 1 fail`；来源中心 MySQL 的 `sourceSnapshotIdentity` 为 `undefined`，没有保留 manifest snapshot id。

GREEN：快照和适配器保留 MySQL/GitHub 的真实 manifest identity、指纹与 Git commit；`6 pass / 0 fail`。

### RED 6：正式可见模型完整性

同一命令，先后暴露：缺少正式模型绑定适配器；补适配器后又发现同义词实际为 190 而需求为 191。

GREEN：为正式模型的实体、事件、字段、关系、维度、指标、层级、规则、同义词和时间语义生成绑定；补足第 191 个有证据同义词；`6 pass / 0 fail`。无直接证据字段的时间日历/时间规则统一显式 `NEEDS_CONFIRMATION`。

### RED 7：模型证据目录仍是第二套事实

命令：`npm run test:evidence-registry`。

摘要：`5 pass / 1 fail`；模型目录仍按旧循环生成，只包含 GR001–GR024，且 `source_file`、quote 未复用 Registry。

GREEN：模型证据目录从 Registry 投影完整 GR001–GR025；`6 pass / 0 fail`。

### RED 8：运行时提前主张缺席来源

命令：`npm run test:evidence-registry`。

摘要：`6 pass / 1 fail`；空批次仍返回全部八来源 Claim，只有 MySQL 的 B01 也会预生成其他来源 Finding。

GREEN：运行时按当前 `batch.sourceIds` 过滤 Locator、Claim 和 Finding；最终 Registry 测试 `7 pass / 0 fail`。

## 最终验证

顺序运行：

```bash
npm run test:evidence-registry
npm run test:modeling-pipeline
npm run test:semantic-evidence
npm run test:guanyijia-story
node --experimental-strip-types --test src/features/collaboration/catalog-browser.test.ts
npm run test:ai-modeling
npx tsc -b --pretty false
```

结果：

- Evidence Registry：`7 pass / 0 fail`
- Modeling pipeline：`3 pass / 0 fail`
- Semantic evidence：`10 pass / 0 fail`
- Guanyijia story：`1 pass / 0 fail`
- Catalog browser：`6 pass / 0 fail`
- AI modeling：首次最终串行验证因测试仍期待旧来源名称/顺序，`72 pass / 1 fail`；更新该契约后单独重跑为 `73 pass / 0 fail`
- TypeScript：`npx tsc -b --pretty false` 退出码 0，无输出、无类型错误

未运行全仓测试或 E2E；根 `AGENTS.md` 要求只运行新增或直接覆盖当前变更的定向测试。

## 设计决策

1. **证据记录不等于语义 Claim。** Registry 中 Evidence Entry 保存单一来源、陈述和精确 Locator；Claim 独立表达可被互证的业务断言。
2. **一个 GR 编码只有一个所有者。** legacy 来源资产、冻结 sidecar、模型证据目录和来源中心模板均从同一 Registry 投影，消除编号相同但来源/定位不同的问题。
3. **批次严格累积。** 每批 Claim 必须同时满足来源已到达且引用证据已到达；运行时也按当前批次过滤，禁止缺席来源主张。
4. **派生来源不制造独立真相。** Semantica 明确依赖 SharePoint；B03 缺上游时报告 gap，独立根来源仍为 2；B04 上游到达后 gap 清除，根来源为 3。
5. **Blocker 由新到达证据触发。** B02 为净销售退款时点，B06 为机器人/压测过滤，B08 为营业日归属；其余批次不生成 Blocker。
6. **大模型完整保留。** 零售仍为 7 实体、5 事件、84 字段、15 关系、11 维度、12 指标、2 层级、7 规则、191 同义词、3 项时间语义；管伊佳候选模型计数未缩减。每个业务可见对象必须拥有当前 Evidence ID，或明确待确认。
7. **真实 identity 优先于完整外观。** 管伊佳 MySQL/GitHub 使用实际 fixture manifest 的 snapshot ID、fingerprint 和 commit；SharePoint/Semantica 尚无真实 manifest，因此保留空 identity 与 `NEEDS_CONFIRMATION`，且没有这两个来源的 Claim。

## 遗留关注点

- 管伊佳 SharePoint 与 Semantica 目前只有累积故事占位，没有真实 manifest、snapshot identity 或 Claim。后续接入真实快照后才能转为 `READY`；在此之前不得以演示字符串补齐。
- 本 Task 只建立事实基础与因果故事，没有实现 Task 2 的 ReviewIssue、Decision、审批和 Markdown revision 状态机。
- `linguan-prototype-v2` 及其中 `docs/`、`src/features/` 等在当前工作树中整体未跟踪；本次没有提交，也没有清理任何用户已有文件。

## 2026-08-12 复审整改补充

依据 `docs/plans/task-1-review.md`，Critical、Important、Minor 全部修复；实现范围仍限定为 Task 1。

### 复审 RED → GREEN

1. **正式 Catalog 对象证据 XOR 与语义 support edge**
   - RED：`node --experimental-strip-types --test src/features/collaboration/catalog-browser.test.ts`
   - 输出摘要：`6 pass / 1 fail`；正式 sidecar 只有 11 条 `objectEvidence`，实际 ModelBrowser 有 337 个对象。
   - GREEN：sidecar 持久化 `objectKind/objectCode/ownerCode/status/evidenceRefs/supportClaimIds`，ModelBrowser 严格消费并校验 XOR；`7 pass / 0 fail`。
   - 只读诊断：337 个对象 = 26 个 `VERIFIED` + 311 个 `NEEDS_CONFIRMATION`，337 条绑定，XOR 全部成立。未在投影器中根据空数组自动补状态。
   - `retailObjectSupportEdges` 显式建立对象→Claim 支持边；经营组织同义词不再用 NET_SALES/INVENTORY 证据伪验证，BOT_FILTER 指标只由 BOT_FILTER Claim 支持。

2. **管伊佳实际四来源、冻结 identity 与 manifest metadata**
   - RED：`npm run test:guanyijia-story`
   - 输出摘要：`0 pass / 1 fail`；来源中心实际只有 MySQL/GitHub，没有 SharePoint/Semantica 状态。
   - GREEN：来源中心登记四来源，MySQL/GitHub 为 `READY`，SharePoint/Semantica 为 `DRAFT`；管线 coverage 使用四来源顺序，候选仍只要求已存在的双真实快照；发布 sidecar 含四来源，其中后两项 `NEEDS_CONFIRMATION` 且没有 Claim；`1 pass / 0 fail`。
   - 额外 RED：发布 sidecar 的 fingerprint 最初为 `undefined`；增加冻结字段并从 SourceSnapshot 贯穿后 GREEN。
   - MySQL/GitHub 的 `sourceSnapshotIdentity`、fingerprint、manifestRef、version/commit 和 `objectCounts` 与原始 JSON manifest 深比较一致；GitHub 不再手写 `sourceFiles: 418`/`mapperQueries: 126`。

3. **Claim upstream lineage、Registry Blocker 单一事实与 evidence count**
   - RED：`npm run test:evidence-registry`
   - 输出摘要：`7 pass / 3 fail`；legacy Claim 丢 `upstreamClaimIds`、不存在 Registry blocker projector、artifact manifest 仍为 24。
   - GREEN：Claim lineage 贯穿 Registry → legacy/source-management → pipeline → frozen sidecar；冻结与发布检查验证上游 Claim 和 source snapshot lineage 闭合。三项 Blocker 统一由 `buildRetailRegistryBlockers()` 投影到 story、legacy runtime 和 frozen sidecar；artifact `evidenceCount` 从 25 条目录派生；`12 pass / 0 fail`。

4. **单来源多 Claim**
   - RED：`npm run test:modeling-pipeline`
   - 输出摘要：新增场景中 MongoDB 同时贡献 2 个客户等级 Claim，却返回 `CONSISTENT`。
   - GREEN：`sourceCount` 改为按 Claim→`sourceId` 去重；同一来源两 Claim 返回 `SINGLE_SOURCE/WEAK`；管线 `4 pass / 0 fail`。

5. **Registry 强化自检**
   - RED：`npm run test:evidence-registry`
   - 输出摘要：损坏小 Registry 删除 GR025 仍未抛错。
   - GREEN：自检覆盖恰好 GR001–GR025、来源/Claim ID 唯一、Claim evidence 存在且属于同源、upstream Claim/source 存在、派生 authority/lineage、support edge、statement/section/Locator 非空；损坏用例全部被拒绝。

6. **发布边界与草稿兼容**
   - 发布时执行 frozen lineage 和最终 ModelBrowser XOR 校验；草稿比较允许尚未同步 sidecar 的新对象暂时阅读，但正式发布必须显式物化状态。
   - 在加入发布严格校验后，协作回归先暴露 V2 新规则/别名/时间语义缺少持久状态；将三者分别物化为有效 SharePoint 支持或显式 `NEEDS_CONFIRMATION` 后，协作 `19 pass / 0 fail`。

### 复审最终顺序验证

```bash
npm run test:evidence-registry
npm run test:modeling-pipeline
npm run test:semantic-evidence
npm run test:guanyijia-story
node --experimental-strip-types --test src/features/collaboration/catalog-browser.test.ts
npm run test:source-management
npm run test:collaboration
npm run test:draft-comparison
npm run test:ai-modeling
npx tsc -b --pretty false
```

结果：

- Evidence Registry：`12 pass / 0 fail`
- Modeling pipeline：`4 pass / 0 fail`
- Semantic evidence：`10 pass / 0 fail`
- Guanyijia published story：`1 pass / 0 fail`
- Catalog browser：`7 pass / 0 fail`
- Source management：`13 pass / 0 fail`
- Collaboration：`19 pass / 0 fail`
- Draft comparison：`9 pass / 0 fail`
- AI modeling：`73 pass / 0 fail`
- TypeScript：退出码 0，无类型错误

### 复审后关注点

- 管伊佳 SharePoint/Semantica 仍无真实外部 manifest，因此发布 sidecar 正确保留为 `NEEDS_CONFIRMATION`，空 identity/证据/Claim；这是事实边界，不是未完成的伪数据补齐。
- 零售 311 个暂无线性语义 support edge 的对象现在明确待确认。后续只能在获得相关 Claim 后转为 `VERIFIED`，不能复用“ID 有效但语义无关”的证据填满。

## 2026-08-12 第二轮复审整改补充

第二轮唯一残留类别已修复：真实零售候选模型与正式 Catalog 现在消费同一个 Registry 对象证据投影，support edge 也由“同主题相关”收紧为可审阅的精确支持面。

### 本轮修改文件

- `src/features/evidence-registry/retail-evidence-registry.ts`
  - 每条 support edge 新增 `supportAspect` 和 `allowedTopics`；Registry 自检拒绝缺失支持面或 Claim topic 超界。
  - 新增 `projectRetailObjectEvidence()`，作为 candidate 与 frozen sidecar/Catalog 的唯一对象→Claim→evidence/status 投影入口。
  - 删除无直接 Claim 的过度支持边，包括库存移动事件及其字段、退货率、搜索转化事件/转化率、退款金额；仅保留可直接支持的 19 条边。
- `src/features/modeling-pipeline/retail-adapter.ts`
  - 候选模型不再信任 raw fixture `evidence_ids`，所有 334 个真实可见对象逐条使用 Registry projector，产生 `VERIFIED/NEEDS_CONFIRMATION`、evidence 和 support Claim。
- `src/features/collaboration/retail-evidence-fixture.ts`
  - frozen sidecar 删除本地重复投影逻辑，改用同一 `projectRetailObjectEvidence()`。
- `src/features/ai-modeling/candidate-model.ts`
  - 候选对象类型保留 `supportClaimIds`，真实检查器对象不再只有不可解释的 GR ID。
- `src/features/evidence-registry/evidence-registry.test.ts`
  - 新增完整 19 条 semantic edge 表驱动合同和负例 topic 自检。
- `src/features/modeling-pipeline/modeling-pipeline.test.ts`
  - 走八个 R2 真实快照和 runtime，检查候选逐对象 XOR、经营组织同义词、三类过度验证、BOT_FILTER 规则，以及与 V2 Catalog 重叠对象的全量状态/evidence/support Claim 一致。
- `src/features/collaboration/catalog-browser.test.ts`
  - 明确搜索转化率公式待确认，BOT_FILTER Claim 只验证过滤规则。
- `README.md`
  - 最小补充 candidate/Catalog 共用投影与 support aspect 语义契约。

### 本轮 RED 记录

1. 命令：`npm run test:evidence-registry`
   - 摘要：`11 pass / 2 fail`。失败确认 26 条旧 support edge 无 `supportAspect/allowedTopics`，且 Registry 不拒绝 topic 错配。
2. 命令：`npm run test:modeling-pipeline`
   - 摘要：`3 pass / 1 fail`。真实 R2 pipeline candidate 没有任何 `NEEDS_CONFIRMATION`，复现 334/334 `VERIFIED`。
3. 命令：`npm run test:catalog-browser`
   - 摘要：`6 pass / 1 fail`。搜索转化率仍被 BOT_FILTER Claim 误标 `VERIFIED`。

### 本轮 GREEN 结果与设计决策

- 候选模型为 334 个对象：17 个 `VERIFIED` + 317 个 `NEEDS_CONFIRMATION`，全部满足 evidence/pending XOR；经营组织业务称呼已是空证据的待确认对象。
- V1 正式 Catalog 为 337 个对象：19 个 `VERIFIED` + 318 个 `NEEDS_CONFIRMATION`。与 candidate 的两个数量差来自 candidate 不包含 3 项时间规则，其中 2 项有直接 support edge；不是事实源分叉。
- V2 正式 Catalog 会真实删除 1 个 V1 同义词并新增变更对象。测试对所有仍重叠的 candidate 对象逐条比较，其 status、evidenceRefs 和 supportClaimIds 全部相同。
- 库存快照 Claim 只支持“当前库存读取最后有效快照”的指标/规则/时间支持面，不再验证库存移动事件。退款完成时间不再验证退货数量/销售数量公式；BOT_FILTER 不再验证转化数/曝光数公式。

### 本轮最终定向验证

```bash
npm run test:evidence-registry
npm run test:modeling-pipeline
npm run test:catalog-browser
npx tsc -b --pretty false
```

- Evidence Registry：`13 pass / 0 fail`
- Modeling pipeline：`4 pass / 0 fail`
- Catalog browser：`7 pass / 0 fail`
- TypeScript：退出码 0，无输出、无类型错误

### 本轮遗留关注点

- 管伊佳 SharePoint/Semantica 仍无真实外部 manifest，继续按上一轮结论保留待确认；本轮未扩大该边界。
- 未有直接 Claim 的对象不再借用相关 topic 升级状态；日后如需升级为 `VERIFIED`，必须先进 Registry 增加对应结构、字段或公式的直接 Claim。
