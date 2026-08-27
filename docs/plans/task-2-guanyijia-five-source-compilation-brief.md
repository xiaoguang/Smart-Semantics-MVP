# Task 2：管伊佳五源 Fixture、逐源文档与派生血缘

## 目标

在不修改正式 Catalog、黄金 V1、旧候选 Fixture 和现有页面的前提下，新增一个深模块：

```text
src/features/guanyijia-standardization-story/
```

该模块的唯一外部 seam 负责：给定一个来源步骤，直接从该来源冻结事实编译一份独立九段式来源文档；同时给出读取摘要、结构化块、相对当前工作标准的增量、该来源引入或增强的冲突，以及血缘状态。禁止从最终 Artifact 反向筛选来源内容。

## 公开接口

接口保持小而深，建议形状：

```ts
type GuanyijiaStandardizationStory = {
  listSources(): StorySource[];
  compileSource(input: {
    sourceId: string;
    priorCompilations: SourceDocumentCompilation[];
  }): SourceDocumentCompilation;
  listConflictDefinitions(): SemanticConflictDefinition[];
};
```

`compileSource()`必须确定性、无存储副作用。后续 Checkpoint 才把结果注册进 `SourceDocumentRuntime` 和 `StandardizationRunRuntime`。

## 通用编译结果

新增业务无关类型（可以放在本模块，也可深化现有 `source-documents/types.ts`，但不要扩大现有 Runtime 接口）：

```ts
type SourceDocumentBlock = {
  blockId: string;
  section: ModelingDocumentSection;
  semanticKind: ModelBrowserObjectKind | 'GAP' | 'PENDING_ASSET' | 'EXCLUSION';
  stableCode: string;
  label: string;
  value: StructuredValue;
  evidenceStatus: 'FACT' | 'INFERENCE' | 'GAP' | 'CONFLICT';
  evidenceRefs: string[];
  affectedObjectRefs: ModelBrowserObjectRef[];
};

type SourceDocumentCompilation = {
  sourceId: string;
  sourceName: string;
  sourceClass: 'REAL' | 'DEMO_POLICY' | 'DERIVED';
  snapshotId: string;
  authority: 'PRIMARY' | 'CORROBORATING' | 'AUXILIARY' | 'DERIVED';
  readSummary: SourceReadSummary;
  sections: SourceDocumentSections;
  blocks: SourceDocumentBlock[];
  assertions: StructuredModelingAssertion[];
  delta: {
    addedBlockIds: string[];
    changedBlockIds: string[];
    addedGapIds: string[];
  };
  introducedConflictIds: string[];
  corroboratedConflictIds: string[];
  lineageStatus: 'ROOT' | 'DERIVED_VALID' | 'UPSTREAM_MISSING';
  upstreamSourceIds: string[];
};
```

类型可按现有项目对象引用契约做最小调整，但必须保留以上语义。Markdown由 `sections + assertions/blocks` 确定性生成，不得手写第二份不同真相。

## 五源顺序与身份

严格顺序：

1. `guanyijia_mysql`：真实 MySQL 部署快照
   - snapshotId：使用 `guanyijia-modeling-package.json` / `guanyijia-database-evidence.json` 当前冻结身份，不得新造。
   - `sourceClass=REAL`，`authority=PRIMARY`，`lineageStatus=ROOT`。
2. `guanyijia_github`：真实 GitHub 固定 Commit
   - 使用现有 repository evidence 固定 SHA/manifest。
   - `sourceClass=REAL`，`authority=CORROBORATING`，`lineageStatus=ROOT`。
3. `guanyijia_official_docs`：真实官方核心文档
   - 使用黄金 package 当前冻结 official docs snapshot 和 14 条证据。
   - `sourceClass=REAL`，`authority=AUXILIARY`，`lineageStatus=ROOT`。
4. `guanyijia_demo_policy`：内置演示制度 Markdown
   - 明确 `sourceClass=DEMO_POLICY`，不得写入正式 V1 Sidecar。
   - 三份文件：`业务术语/往来单位.md`、`单据管理/审核状态.md`、`库存管理/负库存与库存时点.md`。
   - 使用 `demo-policy://...` 的确定性 locator/evidence id，醒目标记“演示制度，不是真实生产制度”。
5. `guanyijia_semantica_demo`：从演示制度派生的术语图
   - `sourceClass=DERIVED`、`authority=DERIVED`、`lineageStatus=DERIVED_VALID`。
   - `upstreamSourceIds=['guanyijia_demo_policy']`。
   - 每个 block/assertion/evidence 都必须可回链具体 policy 文件和章节；不得增加根来源数，不得进入正式 V1 Sidecar。

## 每源必须讲清的内容

### MySQL

- 读取摘要必须来自当前真实 manifest：表、视图、字段、索引、过程、DML 摘要等，不允许沿用旧的 93/1114 计数覆盖当前 manifest。
- 九段文档确认物理事实：`jsh_supplier`、`jsh_depot_head`、`jsh_depot_item`、`jsh_material`、`jsh_material_extend`，以及 `oper_time`、`bill_time`、`create_time`。
- 明确 DDL 只能确认结构；完整业务名称、公式和制度属于缺口。
- 63 张扩展表是 `PENDING_ASSET`，不得按表名生成本体。
- `current_stock_as_of` 是 GAP；不生成虚假时间规则。
- 建立工作标准：部署 `jsh_depot_head` 不存在 `debt/last_debt/last_deposit`。

### GitHub

- 读取摘要来自真实 repository manifest：commit、文件、32核心表、Mapper statement、Service、Controller等当前值。
- 文档补充采购入库、采购退货、销售出库、销售退货、调拨、盘点、组装、拆卸和财务活动；Join、状态、负库存/批次/序列号/调拨/订金均只绑定真实代码 evidence。
- 加入时引入冲突 `gyj-conflict-debt-schema`：源码 schema 有 `debt/last_debt/last_deposit`，部署结构无。
- 不把规则候选伪装成正式可执行规则。

### 官方核心文档

- 使用黄金 package 中 official docs 的真实 14 条证据与定位。
- 补充往来单位/客户/供应商/会员、采购销售退货调拨流程、商品分类/组织层级、指标名称和业务时间解释。
- 只做辅助业务定义，不覆盖部署数据库事实。
- 本源不新增主要冲突。

### 演示制度 Markdown

- 三份九段来源文档内容中的制度条款必须人类可读、短小、具体。
- 加入时引入：
  - `gyj-conflict-negative-stock`：制度“一律禁止负库存” vs 实现“按租户配置控制”。
  - `gyj-conflict-status-nine`：制度“状态9=待审核” vs 代码工作流“审核中且枚举依据不完整”。
- 库存生效时点条款只能形成建议/缺口，不得解除 `current_stock_as_of` GAP。

### Semantica 派生图

- 从上述三份演示制度块确定性生成 Counterparty 角色、采购入库/销售出库/退货退库概念、审核状态术语和库存制度规则候选。
- 所有内容必须 `DERIVED` 且有 policy upstream；不引入新的独立冲突，只能 corroborate `negative-stock` / `status-nine`。
- 若临时删除任一 upstream 引用，编译必须变成 `UPSTREAM_MISSING` 并产生 gap；不得伪装有效派生来源。

## 九段式与去重规则

- 九个一级章节必须全部存在。
- 每个 blockId、assertionId 在单文档内唯一。
- 相同 `stableCode + normalized value + evidenceRefs` 只出现一次。
- 每条 assertion 必须由对应 block 投影；block/assertion/Markdown 数量与章节归属一致。
- Evidence ID 只能属于当前来源；Semantica 允许引用自身派生 statement evidence，但必须另带上游 policy refs。
- 技术 ID 不拼入默认业务正文；进入 block/evidence 与后续检查器。
- 示例问题没有证据时明确 GAP，不制造空表格或虚假问题。

## 冲突定义

新增三个稳定定义，供 Checkpoint 5 生成 Git hunk：

- `gyj-conflict-debt-schema`：FIELD / 第5节；MySQL current=`DEPLOYED_SCHEMA_NO_DEBT`，GitHub incoming=`SOURCE_SCHEMA_HAS_DEBT`；影响应收欠款指标与订金候选。
- `gyj-conflict-negative-stock`：RULE / 第7.1节；GitHub current=`TENANT_CONFIG_CONTROLS`，Policy incoming=`ALWAYS_FORBIDDEN`；影响负库存规则候选。
- `gyj-conflict-status-nine`：DIMENSION/RULE / 第5/7.1节；GitHub current=`REVIEWING_UNCONFIRMED_ENUM`，Policy incoming=`PENDING_REVIEW`；影响单据状态与审核规则候选。

本 Checkpoint 只定义 variants、来源、章节、影响对象和默认结果草案；解决命令后续实现。

## 黄金基线保护

测试前后必须验证 `guanyijiaFrozenModelingArtifact` 的：

- artifactId、Markdown SHA、semantic payload SHA 不变。
- 14/9/296/30/4/5/2/8/10/9/63/2 数量不变。
- 演示 policy 与 Semantica 的 evidence/sourceId 不出现在正式 artifact evidenceContext。

不得修改：

- `src/features/ai-modeling/fixtures/guanyijia-modeling-package.json`
- 现有 Guanyijia Catalog seed
- `modeling-evidence/`
- UI、source center、M4、协作运行时

## TDD 与验证

先新增：

```text
src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts
```

必须先观察接口缺失 RED，再实现 GREEN。覆盖：

1. 五源身份、顺序、真实/演示/派生标识。
2. 每源九章节、唯一 block/assertion、证据归属、Markdown确定性。
3. MySQL/GitHub/官方读取摘要取当前 manifest 值。
4. 冲突出现时点：GitHub debt；Policy negative-stock+status-nine；Semantica只corroborate。
5. Semantica upstream 完整且根来源数仍为4；缺上游变 `UPSTREAM_MISSING`。
6. 63 pending 与 current stock GAP 只来自 MySQL；演示制度不能解除。
7. 修改一个 policy block 只改变该来源 compilation 与对应冲突 variant，不改真实三源。
8. 黄金 artifact 身份、SHA、对象计数与证据源保持完全不变。

运行：

```bash
node --experimental-strip-types --test src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts
npx tsc -p tsconfig.app.json --noEmit
git diff --check
```

更新：

- `docs/architecture/m3-m4-standardization.md`
- `docs/plans/task-2-guanyijia-five-source-compilation-report.md`

报告记录 RED/GREEN、实际 manifest 计数、每源 block/assertion/章节计数、三项冲突触发点、黄金 hash 与关注边界。
