# 从冻结源码到业务过程：销售业务贯穿推演

> 本页同时说明两件不同的事：**当前实现实际保存了什么**，以及**已批准但尚待实现的业务过程发现设计将怎样使用这些内容**。目标设计的示例 JSON 不是运行产物，不能据此宣称新过程模块已经完成。

## 1. 先给出结论

现有系统已经完成了可靠的代码取材和局部活动解释：固定 jshERP 源码经过 JDT 后，保存了入口、完整方法正文、调用、参数、条件、返回和短来源引用；随后 Luna 对 **326/326** 个入口完成了 Activity DRAFT 与 REVIEW。

当前缺口不在“再找一次代码”，而在整仓过程发现。已保存结果显示：

- `activity-explanations.jsonl` 有 326 个已审 Activity，入口覆盖总数也是 326；
- 当前每个 Activity 只覆盖一个入口，但合同不把这种一对一视为永久规则；
- `business-processes.jsonl` 有 340 个 Process；340 个都只有一个 Activity、一个 Stage；
- 这些 Process 只覆盖 325 个不同 Activity，但 `repository-business-knowledge.json` 的 `unmatchedActivityIds` 却为空。

因此，当前文件证明了“局部解释、保存和九章渲染能运行”，没有证明“已经识别出系统支持哪些完整业务、每种业务经历哪些步骤”。目标修改从保存的 326 个 Activity 和 JDT 语料重新开始**过程发现**，不重新运行 JDT，也不重跑全部 Activity。

## 2. 八步各自交付什么

| 步骤 | 已有交付 | 在新接力中的用途 |
| --- | --- | --- |
| 01 固定源码 | 固定 commit、文件清单和可验证源码字节 | 后续所有片段的唯一来源 |
| 02 发现应用 | Spring 入口、方法位置和 Mapper 声明 | 定义入口分母；省略 HTTP method 的合法映射仍是入口 |
| 03 代码关系 | JDT `java-code-index.jsonl` 中的方法、调用、候选和限制；JavaParser 可提供额外图 | 按需取回 Controller→Service→Mapper 等完整代码材料 |
| 04 严格事实 | JavaParser 路径的 Fact/Proof，或 JDT 路径明确 `NOT_PRODUCED` | 可选的技术增强，不是业务阅读门禁 |
| 05 入口上下文 | 每个入口的完整代码上下文和 Capsule 引用 | 给局部 Activity 提供连贯源码 |
| 06 局部解释 | 326 个完整已审 Activity、来源及覆盖 | 新过程发现的主要语义输入，不需要重跑 |
| 07 仓库知识 | **当前**是机械分组后的单阶段 Process；**目标**是业务目录、详细过程和覆盖 | 本次设计修改的核心 |
| 08 九章概览 | 当前可确定性渲染九章和来源文件 | 以后只消费已归并过程目录，不再自行猜流程 |

代码引擎怎样取得完整 Service 正文，见[Java 引擎贯穿例子](java-code-engine-walkthrough.md)。本页从已审 Activity 开始，解释怎样得到跨入口业务过程。

## 3. Step06 的真实 Activity 能告诉我们什么

一个 ReviewedActivity 不是方法名卡片。它保存：

```text
目的、业务对象、输入与触发
条件、逐步动作、代码定义结果
业务规则、公式、问题与限制
SourceRef 短引用
```

销售相关能力并不只存在于一个入口。当前已审材料中可以读到下列类型的局部活动；这里使用易读别名，实际归属仍由完整 `activityId` 标识。特别要注意：创建销售订单、销售出库和销售退货不是三条已保存 Activity；它们是同一条通用新增单据 Activity 中的三个业务变体：

| 本页别名 | 局部活动中已经保存的可用信息 |
| --- | --- |
| `A-new-document` | 实际 ID `activity:c7f1d27630e7680d5f948fc0c256db863ad015312cc27299e417734e1ec190fa`；新增库存单据和明细，一个通用入口包含销售订单、销售出库、销售退货等业务变体 |
| `A-update` | 修改已有单据；源码规则要求原单状态为 `0` 才允许修改，其他状态拒绝 |
| `A-audit` | 批量审核或反审核；审核发生 `0→1`，反审核要求当前状态为 `1` 且采购进度状态为 `0`；采购进度为 `2/3` 时拒绝 |
| `A-query` | 详情、进度或关联单据查询，用于查看业务，不天然是主流程阶段 |
| `A-statistics` | 销售统计和经营查看，消费业务数据，但不等于订单履约步骤 |
| `A-receipt` | 收款或欠款相关处理；与销售资金流相关，但当前静态材料未必能唯一确认关联的是销售订单还是出库单 |

这些信息足以让模型提出销售业务候选，也足以写出具体条件；它们却分散在不同 Activity 中。把 Activity 按原始顺序每 24 个机械分组，模型不会同时看到相关创建、审核、出库和退货活动，这正是当前 340 个单阶段 Process 的根因。

## 4. 目标接力总览

```text
326 个已审 Activity
        ↓ Java：确定性压缩
ActivityIndexCard 全仓卡片
        ↓ LLM：不带行业词典发现业务领域和重叠候选
RepositoryBusinessCatalog + Candidate Process
        ↓ Java：按 activityId 取回完整 Activity 和语句 handle
ProcessMaterial
        ↓ LLM DRAFT：选择本过程相关的业务变体、阶段和规则
SourceRef 查询请求
        ↓ Java：从已保存 JDT/源码返回所选片段，不重新扫描
        ↓ LLM REVIEW：收窄关系，保留具体谓词和不确定性
ReviewedBusinessProcess
        ↓ LLM：一次仓库归并；Java：引用、分母和覆盖校验
RepositoryBusinessProcessCatalog
        ↓ Java：确定性发布
business-processes.md
        ↓ 现有 Step08：只做仓库级概览
一份九章 Markdown
```

这里没有增加另一套证据链。Activity 语句 handle 指向现有已审字段，SourceRef 指向已保存文件、行号和片段。只有关键规则需要核对时才取回源码；技术运行身份、哈希和调度信息不发送给模型。

## 5. 第一遍：从全仓 Activity 建立业务目录

### 5.1 Java 生成精简卡

`RepositoryBusinessCataloger` 的程序部分把每个 ReviewedActivity 确定性投影成卡片。例如：

```json
{
  "activityId": "activity:<A-audit 的实际 ID>",
  "name": "批量审核或反审核库存单据",
  "purpose": "检查并提交一组单据的状态变化",
  "businessObjects": ["库存单据", "单据状态", "库存"],
  "actions": ["审核", "反审核", "提交状态更新"],
  "stateObservations": ["0→1", "1→0"],
  "identifiers": ["单据ID集合"],
  "limitations": ["外部事务的实际提交结果未知"]
}
```

程序不添加“销售”“采购”等预置领域标签。卡片只压缩 Activity 已有字段，用于一次看见全仓目录，不能替代完整 Activity。

### 5.2 LLM 发现业务领域和重叠候选

目录模型得到 326 张卡后，可以提出如下**候选**：

```json
{
  "businessArea": "订单与履约",
  "candidateProcess": "销售订单管理与履约",
  "memberProposals": [
    {
      "activityId": "activity:c7f1d27630e7680d5f948fc0c256db863ad015312cc27299e417734e1ec190fa",
      "possibleUses": ["创建销售订单", "创建关联或独立销售出库", "创建关联或独立销售退货"]
    },
    {"activityId": "activity:<A-update>", "possibleUse": "修改待处理销售订单"},
    {"activityId": "activity:<A-audit>", "possibleUse": "审核或撤销审核"},
    {"activityId": "activity:<A-query>", "possibleUse": "查询详情与进度"}
  ],
  "reason": "活动共享订单、单据状态和关联单号，并出现可衔接的创建、状态变化和出库结果"
}
```

同一个 Activity 可以进入多个候选。例如 `A-audit` 可参与“销售订单管理与履约”和“采购订单管理与履约”；`A-statistics` 可同时关联“销售分析与对账”，但作为 `ANALYTICS`，不强行排进订单主时序。

目录输出只回答“哪些内容值得一起深入阅读”，不宣布顺序已经证明。未分类 Activity 也必须进入 coverage，不能像当前结果那样被遗漏却仍报告 unmatched 为空。

## 6. 第二遍：取回完整内容，而不是拿卡片写流程

`ProcessMaterialAssembler` 根据候选里的 `activityId` 打开完整 ReviewedActivity，为每项可引用字段生成 handle：

```text
activity:<A-update>/businessRules/0
activity:<A-audit>/conditions/1
activity:c7f1d27630e7680d5f948fc0c256db863ad015312cc27299e417734e1ec190fa/codeDefinedResults/2
```

一个通用 Activity 可能包含多个业务变体，因此材料同时提供完整分支，让模型创建不同的 `ActivityUse`：

```json
{
  "activityUseId": "use:sales-order-create",
  "activityId": "activity:c7f1d27630e7680d5f948fc0c256db863ad015312cc27299e417734e1ec190fa",
  "variant": "销售订单",
  "role": "CORE",
  "statementRefs": ["activity:c7f1d27630e7680d5f948fc0c256db863ad015312cc27299e417734e1ec190fa/businessRules/0"],
  "sourceRefs": ["S…"]
}
```

同一候选还可以为相同 ActivityId 建立 `use:sales-outbound-create` 和 `use:sales-return-create`，但每个用法只选择自己的 variant、语句和结果。候选成员分母仍只有这一条 Activity，不会虚构三条 Activity。这一步解决一个关键错误：不能因为“新增库存单据”同时支持多种单据，就把它的全部分支复制进每个过程，也不能把它整体等同于销售订单创建。

## 7. 第三遍：重建一条详细销售过程

### 7.1 DRAFT 先提出过程，并请求关键源码

`CandidateProcessReconstructor` 的 DRAFT 看到候选的全部 Activity，而不是只看摘要。它必须输出具体阶段、分支和规则，并列出需要核对的 SourceRef，例如：

```json
{
  "requestedSourceRefs": ["S<审核条件>", "S<关联数量状态写回>"],
  "draftProcess": {
    "name": "销售订单管理与履约",
    "activityUses": [
      "use:sales-order-create",
      "use:sales-order-update",
      "use:sales-order-audit",
      "use:sales-outbound-create"
    ],
    "proposedStages": [
      "创建销售订单",
      "修改待处理订单",
      "审核或反审核",
      "关联销售出库",
      "更新履约状态"
    ]
  }
}
```

程序只允许请求已有 SourceRef，并从冻结 JDT/源码语料返回真实文件、行号和片段。它不再次启动 JDT，也不让模型创建源码位置。

### 7.2 REVIEW 形成可读、具体的过程

REVIEW 同时看到完整 DRAFT、完整相关 Activity 和请求到的源码片段。目标结果应接近：

```markdown
### 销售订单管理与履约

**目的与范围**

管理销售订单从建立、调整和状态确认，到按订单形成销售出库并更新履约进度的过程。销售出库也可以不关联销售订单独立创建；该分支不应被强行解释为订单履约。

**阶段 1：创建销售订单**

- 操作者提交订单及明细，系统生成并保存单据和明细。
- 新增或更新入口可以携带状态 `1`；因此不能断言所有订单都必须经过独立批量审核入口才会进入已审核状态。

**阶段 2：修改待处理订单**

- 原单状态为 `0` 时允许修改单据及明细。
- 原单状态不是 `0` 时拒绝修改；不能缩写成“状态允许时可以修改”。

**阶段 3：审核或撤销审核**

- 审核分支把单据状态从 `0` 变为 `1`。
- 反审核要求当前状态为 `1` 且采购进度状态为 `0`。
- 采购进度状态为 `2` 或 `3` 时拒绝反审核。

**阶段 4：关联销售出库**

- 销售出库单可以引用销售订单，也可以在没有销售订单关联时独立创建。
- 有关联订单时，系统根据关联数量与原订单明细比较，更新订单履约状态；已保存代码可观察到保持已审核、部分完成和完成等状态分支。

**结束结果与分支**

- 关联订单的出库处理可以推进订单履约状态。
- 独立销售出库是合法备选路径，不代表系统一定先创建销售订单。
- 静态材料尚不能确认现实中的岗位审批制度，也不能证明某次事务已经成功提交。
```

每个阶段在 JSON 中必须保留 `entryConditions`、`actions`、`stateChanges`、`rejectionConditions`、`outcomes`、`transitions`、`certainty`、Activity statement handles 和 SourceRefs。Markdown 是这些字段的确定性读物，不是模型另一次自由发挥。

### 7.3 规则不能退化为空话

目标规则记录为：

```json
{
  "subject": "销售订单",
  "when": "原单 status = 0",
  "actionOrDecision": "允许修改单据及明细",
  "otherwise": "拒绝修改",
  "result": "仅待处理状态的订单进入修改路径",
  "certainty": "CONFIRMED",
  "statementRefs": ["activity:<A-update>/businessRules/2"],
  "sourceRefs": ["S…"]
}
```

如果只知道活动相关、但没有代码直接声明先后，关系写成 `INFERRED`；存在冲突或外部效果未知则写成 `UNRESOLVED`。不用数值置信分，也不把合理业务推断伪装成动态执行轨迹。

## 8. 第四遍：从候选过程归并为系统业务目录

不同候选可能重叠或重复。`RepositoryProcessConsolidator` 读取所有完整 REVIEW，进行一次仓库级归并。对销售领域，合理目标可能包含：

```text
销售订单管理与履约
├─ 核心：建单、修改、审核、关联出库、履约状态更新
├─ 支撑：详情和进度查询
└─ 相关：销售退货处理

销售退货处理
├─ 可关联原销售出库单
└─ 也可按未关联分支处理

客户收款与欠款
└─ 与销售资金流有关；关联订单还是出库单若无法唯一确认则待确认

销售分析与对账
└─ 统计和经营查看是分析活动，不伪装成订单主流程阶段
```

归并允许 `activity:c7f1d276…` 通过 `use:sales-order-create`、`use:sales-outbound-create` 和 `use:sales-return-create` 等不同 ActivityUse 参加多个过程。它不会因为共享“单据”“用户”或同一表名就硬连顺序，也不会为了减少过程数删除合法替代过程。

## 9. `business-processes.md` 与九章怎样分工

`BusinessProcessPublisher` 校验所有 Activity、Candidate、Process 和 SourceRef 都有处置，然后确定性发布：

- `repository-business-process-catalog.json`
- `process-coverage.json`
- `business-processes.md`
- 已有 `source-refs.jsonl`

`business-processes.md` 是检验“系统支持哪些业务、每种业务怎样走”的首要读物。它逐过程展示名称、目的、适用范围、阶段、分支、具体规则、结束结果、待确认联系和短来源引用。

固定九章仍然保留，但职责变成仓库级概览：

1. 文档说明：范围、来源和不确定性语言；
2. 业务目标：从已归并过程提取目标；
3. 业务对象：汇总过程中的对象及其用途；
4. 业务活动：按完整 Business Process 组织，不按 Controller 罗列；
5. 字段与维度：只保留理解规则所需字段；
6. 对象关系：使用已归并过程关系；
7. 指标口径：没有代码口径就不编造；
8. 示例问题：从已保存过程和规则生成可回答问题；
9. 待确认事项：汇总所有 `UNRESOLVED`、未分类和外部效果。

九章模型不能重新分组 Activity、创建新过程或改写具体谓词。删除九章输入中的原始 Activity 后，仍应能仅靠完整过程目录生成报告；这证明过程发现只发生一次。

## 10. 不能识别时怎样记录

所有分母都必须闭合，但“有处置”不等于“业务理解完整”。

| 情况 | 目标处置 |
| --- | --- |
| 卡片没有足够业务信息 | `UNCLASSIFIED_ACTIVITY`，保留 Activity 和原因 |
| 相关 Activity 找到，但顺序有多个合理解释 | 保存替代过程或 `UNRESOLVED` transition |
| 过程 DRAFT 请求不存在的引用 | 拒绝该结果，不创建来源 |
| SourceRef 片段与 Activity 结论实质冲突 | 停止该候选；只允许具名 Activity delta review |
| 外部系统、运行配置或岗位制度未知 | 保留 `pendingConnections`，不写成确认事实 |
| 入口或 Activity 被遗漏 | coverage 失败；不能像当前历史结果那样 unmatched 为空 |

零过程也是合法、诚实的技术结果，但不能宣称业务过程发现成功。

## 11. 为什么这条链能达到目标

它同时满足三项必要条件：

1. **全仓视野。**目录模型一次看到全部 Activity 卡片，销售创建、审核、出库和退货不会再因机械分片互不可见。
2. **细节不丢。**候选确定后重新打开完整 Activity，并按需读取已保存 JDT 片段；`status=0` 等谓词不会被卡片摘要吞掉。
3. **职责正确。**Java 只做保存、引用、覆盖和确定性出版；模型负责领域、同义词、成员、业务顺序和不确定性，不在 Java 中硬编码行业规则。

它仍不能从静态代码证明现实组织制度、运行配置值、动态代理最终选择或某笔交易已经成功。目标不是假装无所不知，而是把能识别的业务过程写完整，把不能识别的联系写具体。

## 12. 后续实施验收

后续计划必须通过下列可观察结果，而不是以文件数量代替语义质量：

- 直接复用现有 326 Activity 和保存的 JDT/SourceRef，过程发现期间 JDT 调用数为 0；
- 326 个 Activity 全部进入候选成员、支撑、独立或未分类处置；
- 至少一个真实候选形成多 Activity、多 Stage 的过程；340 个单阶段包装不算成功；
- 同一个通用新增 Activity 能以不同 ActivityUse 参与不同业务过程；
- 销售过程保留 `status=0` 才允许修改、审核 `0→1`、反审核的采购状态限制、关联与独立出库等具体分支；
- 每个关键规则能回到 Activity statement handle 和 SourceRef，不重新运行 JDT；
- `business-processes.md` 已经能独立回答“系统有哪些业务、每种业务怎样走”；
- 九章只消费归并目录，不能再次发现、合并或改写业务过程。

本页是设计闭环，不是新算法已执行的声明。
