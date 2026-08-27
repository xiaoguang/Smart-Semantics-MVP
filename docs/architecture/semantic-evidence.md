# 语义证据编译器

`src/features/semantic-evidence/`负责把 Semantica RDF/SPARQL、MySQL、GitHub 和 SharePoint 的确定性快照编译成可审阅的语义证据包。它不访问外部服务，也不替代来源中心、个人草稿或 Catalog。

## 数据边界

```text
SourceSnapshot
→ EvidenceRecord
→ SemanticEvidenceClaim
→ SemanticReconciliationFinding
→ SemanticCandidate
→ 审阅说明书
```

- `EvidenceRecord`是不可变事实信封。RDF 保留命名图、IRI、Literal datatype、language 和 BNode 作用域；数据库、代码和文档使用结构化断言及各自 Locator。
- `statementId`由快照身份和无损语句稳定生成；`checksum`覆盖整条证据记录。重复 Evidence ID、损坏校验和、缺失 Locator、缺失上游证据或循环血缘会阻止编译。
- `SemanticEvidenceClaim`按“主语＋业务谓词＋值”聚合，但保留全部 Evidence ID、直接连接和独立根来源。
- 派生 Semantica 语句保留 SharePoint 等上游 Evidence ID。派生图与原文只能算一个独立根来源，不能制造虚假的双来源互证。
- `SemanticReconciliationFinding`按业务问题展示每个来源的主张、一致点或差异、影响对象和阻断级别。
- `SemanticCandidate`只是实体、事件、字段、关系、维度、指标、层级、规则、同义词或时间语义提案。规则候选不可直接成为可执行规则。

## 采集与映射

Semantica 的“建模采集策略”明确配置命名图、namespace、种子概念、谓词映射、排除谓词和上游来源。未映射谓词进入待解释队列，不会静默丢弃。

首版固定流程：

1. 读取 RDF/SPARQL Fixture 或结构化来源快照。
2. 规范化为无损 Quad 或 StructuredAssertion。
3. 校验 Statement ID、checksum、Locator 和血缘。
4. 聚合标签、同义词、类型、层级和属性。
5. 通过显式谓词映射生成业务主张。
6. 按独立根来源计算一致、单一依据、冲突和派生依据。
7. 生成候选对象、规则草案、影响关系和阻断项。
8. 由结构化包生成 Markdown 审阅说明书和 ZIP。

确定性代码负责解析、映射、去重、校验和血缘。未来 LLM 只能建议未知谓词解释、客户化说明或概念聚类；建议必须记录输入指纹、模型版本、Prompt 版本和引用证据，且不能自动合并正式对象。

## 内置示例

零售经营示例覆盖商品、销售订单行、商品品类层级、净销售额、退款时点冲突、客户等级生效时间和机器人流量排除。管伊佳示例覆盖往来单位角色、采购入库、销售出库、退货退库以及源码字段与部署数据库不一致。

入口：

```text
AI 建模
→ 我的草稿
→ 底部“＋”
→ 来源
→ 管理已配置来源
→ 企业术语与本体图
→ 建模采集策略
→ 加载内置示例
```

示例运行在组件内的临时批次中，仅用于验证编译因果和审阅内容，不写入共享连接 Revision、正式 V1、R2 或现有个人草稿。Markdown 是从 `modeling-package.json`生成的只读审阅材料，不能重新导入为机器真相。

## 验证

```bash
npm run test:semantic-evidence
```

测试覆盖 RDF 无损性、派生血缘去重、单条三元组的局部影响、缺失来源不伪造主张、未知谓词、两套示例、审阅 ZIP 及损坏证据的冻结阻断。
