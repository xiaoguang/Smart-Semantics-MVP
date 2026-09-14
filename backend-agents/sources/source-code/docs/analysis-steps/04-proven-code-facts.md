# 已证明代码事实

> 模型批次解耦（已批准、待实施）：已保存技术增强继续按原义保留。新模型批次不重新枚举 Fact 或补 Proof；模型调用失败、换并发/Prompt 不使技术事实失效。唯一执行合同见[模型执行 §7](../modules/model-job-execution.md#7-固定材料与独立模型批次已批准待实施)。本次不修改本步骤算法或产物。

> 新目标的引擎接入见[各子模块设计](../modules/java-code-engines/README.md)。本步保留严格Fact能力，但不是读取完整Service的门禁：JDT未提供原五图增强时，按[接入合同](../modules/java-code-engines/integration-and-javaparser.md)明确保存NOT_PRODUCED与原因，不运行旧Fact枚举，不伪称0候选均通过。以下Proof规则只适用于实际提供并请求分析的图输入。

> [总体设计](../DESIGN.md)；固定 key：proven-code-facts，目录：steps/04-proven-code-facts/。本步骤保留，运行时模型调用为 0。

## 1. 为什么存在

五图包含大量有用代码关系，但有些精确技术结论需要更严格的检查。例如“这个调用点唯一绑定到该 Java 方法”，不仅需要看到方法名，还要有准确 call edge、目标声明、源码位置和匹配规则。Step04 把这些**选定模式**构成可验证的 Fact/Proof，供下游直接复用。

严格 Fact 是增强，不是所有可读信息的容器。当前 FactRegistry 只支持 JAVA_EXACT_CALL、JAVA_BOUNDARY_INVOCATION、JAVA_GUARD_CONDITION。它不会自动把所有变量传递、SQL、异常处理和业务规则都转成 Fact。没有某条 Fact，意味着不能宣称对应 exact 技术证明成立；不意味着安全源码、已知图关系或可读条件必须从业务材料删除。

保留 Step04 的价值在于有选择地提高技术判断可靠性，并避免下游重复证明同一模式。其复杂度应与实际使用的模式相称，不扩展成行业分类器或自然语言逐原子证明系统。

## 2. 输入：已建立的五图与明确规则

输入为同源的 VerifiedSourceInventory、ApplicationDiscovery、完整 ProgramGraphs typed views，以及版本化 FactRegistry、ProofRuleRegistry 和预算。五图继续是关系的拥有者；Step04 不重新解析客户仓库补边，不猜调用，不运行客户代码。

以下是**目标阅读投影，不是 wire Schema 或已保存 JSON**：

~~~json
{
  "entry": "E1",
  "subject": "C1",
  "graphRelationship": "Controller calls Service.getFinancialBillNoByBillId",
  "selectedPattern": "JAVA_EXACT_CALL",
  "required": ["call site", "exact target edge", "target method", "source/rule basis"]
}
~~~

固定 jshERP 的财务单号查询已有图/Fact 运行证据：Controller→Service 与 Service→Mapper 对应两条 JAVA_EXACT_CALL，Mapper 调用另有一条 JAVA_BOUNDARY_INVOCATION。这个计数只针对该入口，不代表全链业务语义已证明，也不证明 SQL 实际执行。

## 3. 处理：枚举一次，证明一次，保存结果

| 模块 | 输入 → 处理 → 输出 |
| --- | --- |
| FactCandidateEnumerator | 从五图按 registry 匹配技术模式；固定 candidate/required atom 分母，产出 FactCandidateSet |
| AtomicProofBuilder | 对每个 candidate 检查其精确 subject、edge、source/rule closure；产出 ProofDecisionSet |
| FactLedgerPublicationSpecifier | 从已完成结果序列化事实、证明、Gap 和守恒账，安装步骤产物 |

每个 template-subject-entry 组合只有一条适用或不适用处置。共享 callsite 按实际 owningEntryIds 逐入口记录，不做入口×全仓调用的笛卡尔积。候选分母在证明前固定，失败项不能消失；枚举结果也不由后面的 Proof 成败反向改变。

同进程传不可变已验证输入及结果，阶段产物仍保存。publisher 不重新 enumerate/prove；PersistedFactCandidateSetReader 跨磁盘边界只检查身份、schema、refs、basis 和保存分母。显式独立审计/mutation test 才可重新枚举并比较。当前 reader 再次枚举的实现应按此收敛。

## 4. 严格技术合同

三个模块仍位于 analysis.fact.candidates、analysis.fact.proofs、analysis.fact.publish。FactCandidateEnumerator.enumerate、AtomicProofBuilder.prove 和既有 publisher/reader 是后续测试的相关 seam，不建立第二套事实入口。

### 4.1 当前三种模式

| Fact | 必需内容 | 不证明什么 |
| --- | --- | --- |
| JAVA_EXACT_CALL | INVOCATION_CALL_ID、STATIC_TARGET_TYPE、STATIC_TARGET_METHOD、STATIC_TARGET_SIGNATURE；call site/EXACT CALL_TARGET/目标 METHOD 及对应 source/rule basis | callee 一定有 body、调用成功、业务顺序、外部效果 |
| JAVA_BOUNDARY_INVOCATION | INVOCATION_CALL_ID、STATIC_TARGET_TYPE、STATIC_TARGET_METHOD、STATIC_TARGET_SIGNATURE、ORDERED_ARGUMENTS、JAVA_LOCAL_ORIGINS、CONTROL_CONTEXT、INVOCATION_EVIDENCE | SQL 成功、数据库写入、消息送达或其他实际外部效果 |
| JAVA_GUARD_CONDITION | CONTROL_CONDITION；自身 GUARD、相关 TRUE/FALSE edge、normalizedCondition 和自身 source/rule pair | 运行时一定走哪条分支、未分析分支内容、业务制度 |

JAVA_EXACT_CALL 按 `entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL"` 区分候选。target 必须是同一 CodeStructure publication 的 METHOD；canonical signature 的 type、method、parameter types 按精确声明读取，不能用 simple name 替代。其四个 atoms 仍需对应 call site、target edge、target METHOD 的合法 rule pair，不借 boundary 的相似字段补 Proof。

boundary 的 ordered arguments 按 ordinal 对齐 ARGUMENT_TO_BOUNDARY edges 与 Java-local origins；control block/guard 的 owner 必须包含同一 entry。boundary 与 exact call 可以同时存在，它们证明的范围不同。下游按同一 callsite 对齐展示，不删除其中一类 candidate 来伪造去重。

CONTROL_CONTEXT 不是 CONTROL_CONDITION。严格条件 Fact 仍必须来自 guard 自身已支持的 normalized condition 与 rule，不从附近字符串、另一个 Fact 的 Evidence 或数据边临时构造。没有这个 Fact 时，Step05 可以保存独立 graph/source condition context，但不能给它伪造 conditionAtomId 或 CLOSED Proof。

### 4.2 Proof admission 保持全有或全无

一个 admitted Fact 的每个 required atom 都必须有 CLOSED Proof。Proof 同时验证准确来源 bytes、图 endpoints/edge 和允许的规则链；hash 只证明完整性，locator 只定位，模型同意只是一种解释，都不能替代 Proof。

任一 required atom 不闭合，整条 composite Fact 不准入。直接失败 atom 保留根因，其余 sibling 记录 COMPOSITE_FACT_REJECTED；不能留下可被误当成独立已准入事实的 partial atoms。候选、拒绝及外部效果 Gap 仍保存。

每个 boundary candidate 保留独立外部效果 Gap，admitted invocation 也不关闭它。静态 Mapper XML 可被定位阅读，但不能直接成为 Java boundary 的效果证明。未知规则不能当作未来自动兼容规则。冲突的 admitted Fact、伪 CLOSED、断 reference 或来源漂移都 fatal。

### 4.3 来源、身份与分母

SourceExcerptV1 保留 SourceLocatorV1、原文 rawUtf8 和原文摘要；切片必须逐字节对应已验证快照，UTF-8 code-point 边界及行列坐标一致，不 trim、不 normalize、不搜索附近替代位置。完整定位合同见 [公共接口与源码位置](../references/inherited-public-and-module-contracts.md#4-唯一源码位置合同)。

身份按无环依赖计算：图元素已固定；atom 绑定 snapshot、候选、entry、subject、role/name/value；fact 绑定 kind/subject/atoms；proof 绑定 fact/atom、精确 Evidence/edge closure 和 rule IDs；最终 set/accounting 身份在后。原有 canonical 公式和已发布 wire 不因本文简化而改名或重新解释。

当前 v3 在 boundary/guard 基础上包含 exact call，守恒必须同时包括三类：

~~~text
candidateFactCount = admittedFactCount + rejectedFactCount
candidateAtomCount = admittedAtomDispositionCount + rejectedAtomCount
provenFactAtomCount = admittedAtomDispositionCount
candidateDenominatorKeys = disjointUnion(boundaryKeys, guardKeys, exactCallKeys)
externalEffectGapCount = count(boundaryKeys)
~~~

相同 count 不替代 ID 集守恒。Atom value 只使用 STRING、INTEGER、DECIMAL、BOOLEAN、SYMBOL_REF 或 ENUM_REF，业务 prose 不进入 atom。此处是合同解释，不修改当前 JSON Schema 文件或创建新的 wire 版本。

## 5. 输出与下一消费者

| 文件 | 内容与用途 |
| --- | --- |
| proven-facts.json | codeFacts、Fact dispositions；05/06 按已有 ID 引用技术增强 |
| proof-pack.json | atom Proof、dispositions、root causes；完整技术依据留在程序侧 |
| gap-ledger.json | 不支持、未闭合及外部效果限制；下游保留限制范围 |
| fact-accounting.json | 三类 candidate/atoms 的 ID 集与计数守恒 |
| proven-code-facts-receipt.json | 保存上游身份、controls 与 artifact descriptors |

上表的四个语义文件是技术图增强 AVAILABLE 时的严格路线。JDT 第一阶段未生产五图时，合法 actual set 精确为 `fact-accounting.json + proven-code-facts-receipt.json`：accounting 使用 `proven-code-facts-fact-accounting-v4`，`availability=NOT_PRODUCED`、`reason` 非空，保留 Step03 navigation/index basis，所有数值 count 为 `null`，且没有 Candidate/Fact/Proof 引用。publisher、step exact-set allowlist、artifact policy、直接 reader 与 fixture 必须同步该实际集合；不得写其余三个空文件，也不得调用候选枚举器。

该 JDT actual set 已在生产发布与读取路径实现并通过验收：Step04 只保存 v4 accounting 与 receipt，不调用旧 FactCandidateEnumerator。它表示“本次没有运行严格图增强”，不是“扫描后发现零事实”；Step05 仍可直接使用已保存的 JDT 源码上下文。

以下是**目标阅读投影**：

~~~json
{
  "entry": "E1",
  "facts": [
    {"id": "F1", "kind": "JAVA_EXACT_CALL", "subject": "Controller → Service"},
    {"id": "F2", "kind": "JAVA_EXACT_CALL", "subject": "Service → Mapper"},
    {"id": "F3", "kind": "JAVA_BOUNDARY_INVOCATION", "subject": "Mapper(billId)"}
  ],
  "limitation": "External execution remains unknown",
  "nextConsumer": "Step05 keeps graph argument/return/control links and attaches these facts"
}
~~~

Step05 用五图组织执行上下文，Facts 有则附着，无则保留 graph/source 依据及缺口。不能把 Fact ledger 当成所有可读内容的过滤器，也不能在 Step05 重新发明 Fact kind。Step06 模型不接收整份 ProofPack，而读程序制作的连贯技术观察和来源片段。

## 6. 失败、预算与复用

严格 Proof 不闭合是 candidate rejection/Gap；已知限制不自动阻断其他安全材料的阅读。候选/atom/Proof nodes/edges、rule applications、源码读取量和输出 bytes 都有预算；超限不缩小分母。

来源/hash/schema/ref 冲突、断 Proof、重复身份、冲突事实、accounting 不守恒与安装失败必须停止。显式复用只接受同一实际来源/图/profile/basis，边界完整性验证不能省略；普通可信进程不反复重开全图或运行枚举器。失败保留已完成上游步骤。

## 7. 当前成熟度和后续验证

FactRegistry、候选枚举、AtomicProofBuilder、模块保存及三类 v3 技术 Fact 已有实现。固定完整 jshERP 719 文件及图/Fact 运行已有离线保存证据；不能继续写“当前只有 package 骨架”或“完整捕获尚未实现”。财务小例的 2 exact call + 1 boundary 是已核对事实，其图/Fact run 没有 Step05 正式 publication，也没有证明整仓业务报告质量。

JDT `NOT_PRODUCED` 路线已经在 publisher、reader、artifact policy、Step05 和正式运行链中验收：普通运行不会调用旧 Fact 枚举器，安全源码上下文仍可进入 05/06。严格图增强路线继续保留原有 exact Fact 与 Proof mutation tests；JavaParser 第二阶段只恢复既有能力，不改变这些 Fact 的含义。
