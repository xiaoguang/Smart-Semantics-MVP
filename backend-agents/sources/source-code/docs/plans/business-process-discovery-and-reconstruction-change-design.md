# 业务生命周期与可读过程：本次设计修正清单

> 2026-09-15，用户批准的设计目标；本文中的新增合同尚未实施。本轮仅更新设计，不改 Java、测试、资源 Prompt、历史产物，不扫描、不调用产品模型。它替代本文件此前“从旧 ProcessExplainer 新建 Step07”的待办表。总体见[DESIGN](../DESIGN.md)，逐步推演见[完整例子](../examples/semantic-framework-walkthrough.md)。

## 1. 目标和真实起点

读者要看见：系统支持哪些业务；每种业务的对象怎样创建、变化、流转和结束；允许与拒绝条件是什么；哪里是合理推断；如何直接打开依据。

**不能再把“接收参数 → 校验 → 执行主动作 → 返回”当成业务生命周期。** 也不能仅靠“多 Activity、多 Stage、coverage CLOSED”宣布语义目标完成。

当前实现已具备 corpus、卡片、目录分片/合并、候选完整阅读、源码请求、详细过程、归并、发布及过程专用运行入口。固定输入为326条已审 Activity及原M10材料；不重建它们。

实施状态（2026-09-15）：业务用法、阶段narrative、规则适用用法、来源预览/导航、v2五文件读写、v2 Prompt和代表候选的保存/正式复用接缝已经完成直接测试。尚未完成的是使用固定326条Activity执行新版真实目录、两个候选语义检查及全仓发布；旧v1真实结果不视为本次验收。

当前真实结果：
- 14个目录候选，发布46个过程，其中21个含多个Activity。
- 326条均有处置：102 PROCESS_MEMBER、157 SUPPORT_ONLY、17 STANDALONE、50 UNCLASSIFIED；coverage CLOSED，semantic PARTIAL。
- 库存单据候选主要按新增/修改/审核/复制/删除分组；阶段仍是“按独立入口接收请求”等技术外壳，没有形成所需的订单生命周期。
- 该候选的DRAFT及REVIEW源码请求均为空；源码目录只有短编号和相同的通用用途说明，难以选中要核对的实现。
- 正文大量打印结构字段和裸来源编号；来源JSONL确实存在，但读者不能直接跳到片段。
- 旧340个单Activity、单Stage结果是历史前身，不是当前实现。不能再把替换旧分组算作本次工作。

这些是语义组织和展示缺口，不是“再多收集证据”即可解决。卡片已有步骤包含业务变体线索，不能把失败全部归咎于一个缺失字段。

## 2. 最小修改范围

保持两个深接口：
```java
ProcessDiscoveryResult BusinessProcessDiscovery.discover(ProcessDiscoveryRequest request);
BusinessProcessPublication BusinessProcessPublisher.publish(ProcessDiscoveryResult result);
```

保留：JDT、前五步、326条已审Activity、固定来源、线程池、DRAFT→完整REVIEW、任务保存/显式复用、覆盖枚举、公共Agent和CLI。

只修改Step07：
1. 卡片和目录Prompt能发现通用Activity的不同业务用法，不把它仅归为CRUD。
2. 候选允许同一Activity的不同variant；完整正文按Activity去重，不丢不同用法。
3. 来源目录给出已有源码的可辨识预览，沿用DRAFT请求、REVIEW读取完整片段。
4. 阶段增加业务叙述，规则明确适用的ActivityUse；审阅具体条件和生命周期联系。
5. 归并保留这些细节；正文以业务叙述呈现，来源独立可点击。

不增加模型轮次、行业词表、自然语言规则引擎、证明层、检索数据库或恢复系统。不重跑Activity，不启用Step08；这轮不做九章。

## 3. 数据接力和模块责任

| 模块 | 输入 → 输出 | 本次变化 |
| --- | --- | --- |
| FrozenAnalysisCorpus | 已审Activity + M10 SourceReference → 只读查询和statement handle | 保持当前来源范围，不新增JavaCodeIndex读取 |
| RepositoryBusinessCataloger | 全仓字面索引卡 → 业务领域、重叠候选、处置 | 补已有businessRules；候选按对象生命周期/业务目的发现 |
| ProcessMaterialAssembler | 候选 → 完整Activity、用法、来源目录 | 同Activity多用法、正文去重、原文预览 |
| CandidateProcessReconstructor | 完整材料 → DRAFT → 所请求完整源码 → REVIEW | 阶段narrative、规则activityUseIds、具体业务条件 |
| RepositoryProcessConsolidator | 全部已审过程 → 唯一catalog | 保留用法、正文和规则；不将无关阶段拼成假生命周期 |
| BusinessProcessPublisher | 封闭result → 正文、来源、catalog、coverage | 五项输出；无模型、无外部回读 |

所有业务判断由模型完成。Java只处理ID、引用、结构、归属、保存和确定性渲染。

## 4. 全仓目录：发现业务用法，不制造行业规则

### 4.1 卡片

沿用当前字段的原文投影：name、businessPurpose、participants、businessObjects、triggerOrInput、conditions、activitySteps、codeDefinedResults、terms、scopeLimitations；增加已有businessRules。

不让Java从中文抽取状态、行业词、隐含顺序或生成摘要。卡片去掉哈希等技术元数据，但不能截去数组尾部、规则条件或变体；变大时采用已有稳定分片及唯一合并。分片合起来恰好覆盖全部Activity。

### 4.2 候选

模型回答：有哪些业务对象/目的？哪些活动描述创建、修改、状态变化、关联对象、履约或结束？哪些只是支撑查询？关系有什么线索、缺什么？

复用candidate的purpose记录候选联系理由，variant记录该Activity在此候选中的具体对象/分支用途。不新增一套关系证明字段。

同一候选允许同一Activity多次出现，只要variant不同；唯一性按 `(activityId, variant)` 校验，不能继续按ActivityId去重用法。模型给出的variant是待审业务解释，不是Java选择器。本次不增加逐variant处置账：候选用法可在深入阅读后收窄或删除，REVIEW必须逐项对照原用法并在现有reason/pendingConnections说明丢弃或缺失。结构分母仍是Activity、候选、已审过程；同Activity丢了一个业务variant属于语义验收问题，不能谎称当前ActivityId集合校验能捕获。相同方法、同表、同名均不能单独确立先后。

全仓merge必须能连接跨分片的成员，保留不同变体及原Activity标识。查询可以提供关联线索，但不能因此被当成创建或履约动作。不强求所有对象都有完整生命周期。

每个Activity必须有现有处置。声明PROCESS_MEMBER却没有任何真实候选成员关系是错误；不得由程序悄悄改成UNCLASSIFIED掩盖遗漏。

## 5. 深入阅读：完整Activity和可用源码目录

### 5.1 来源范围不扩张

第一版仍只使用当前corpus中Activity引用且经M10验证的SourceRef；不重新读取客户checkout、不导航、不查JavaCodeIndex。若真正需要的片段不在此集合，记录具体缺失，另行讨论定向补料。

完整Activity保留条件、步骤、规则、结果、公式、问题和限制；稳定statement handle保持不变。一个Activity以多个variant参与时，正文只发送一次，用法列表分别引用它。

### 5.2 源码目录

替换无区分度的“用于按需核对已保存源码”为：
```json
{
  "ref": "S688",
  "activityIds": ["A1"],
  "openingLines": ["…该SourceReference原文的前8个物理行…"]
}
```
示例内容是字段示意，不是假造源码。预览只能从已保存snippet确定性截取；已有可用符号信息可保留，不为生成方法名再建解析器。模型不需要本机路径或身份链。

DRAFT据此请求完整SourceRef。REVIEW看到原完整Activity、实际完整DRAFT、程序解析出的完整原始片段；预览不能充当完整规则依据。空请求仍合法：材料已经充分时不强迫多查。不可因空请求自动判失败，也不可把空请求自动视为充分。

不得加入第三轮、自动重试或模型自由浏览文件系统。合法来源不足时写UNRESOLVED及缺少的内容。

## 6. 详细业务输出：两个语义字段

### 6.1 阶段正文

`ProcessStage` 增加必填非空 `narrative`：
- 写谁/什么对象，在什么条件下做什么，形成什么变化或结果。
- 阶段以业务里程碑/活动命名，不默认生成技术调用模板。
- 已知具体值和拒绝分支必须说清；正式业务含义未知时保留代码值并注明待确认。
- 无根据不指定岗位；不把静态代码行为写成某次运行成功。

保留entryConditions、actions、stateChanges、rejectionConditions、outcomes、transitions等结构字段；narrative不取代它们。完整REVIEW同时检查叙述与结构字段，不能保存时只留标题。

### 6.2 规则适用范围

`BusinessRule` 增加必填非空 `activityUseIds`；模型wire使用 `activityUseLocalIds`，解析为正式ID。

规则继续保存：
```text
subject / when / actionOrDecision / otherwise / result
certainty / statementRefs / sourceRefs
activityUseIds
```

Java检查用法属于本过程，规则引用属于这些用法对应Activity允许的引用集合；这只是来源范围检查，**不证明某段条件适用于每个业务变体**。具体分支适用性由REVIEW核对。

例如同一个新增方法中某价格校验只适用于特定单据子类型，就不能写成所有订单均受该规则限制。不能因为整方法SourceRef存在就把整个方法里的规则复制给所有ActivityUse。

### 6.3 确认与推断

保留CONFIRMED / INFERRED / UNRESOLVED，不增加分数。
- 有ref是CONFIRMED的必要来源条件，不是充分语义证明。
- 跨入口存在关联字段可支持INFERRED的业务联系，不等同Controller直接调用或强制时序。
- “可关联”说明关联到什么、依据哪个条件、关联后做什么；找不到允许/禁止条件就明确缺失，不用空话填补。
- Java不写中文关键词黑名单、不验证中文蕴含；结构错误是fatal，语义质量由完整REVIEW和真实样例验收判断。

## 7. 归并：不丢细节，也不拼假流程

保持主处置KEEP / MERGE_INTO / REJECT，附加关系PARENT_CHILD / RELATED / ALTERNATIVE。

合并必须保留各ActivityUse、阶段narrative、规则适用用法、具体条件、certainty和来源。只能删除完全相同的重复记录，不能用一句总结替代不同分支。

归并DRAFT/REVIEW沿用当前完整已审Process JSON输入，包含正文、规则、用法和来源，不新增摘要投影。模型只有裁决权，没有改写权。程序允许MERGE_INTO的前提是：按(activityId, variant, role)映射用法后，两份完整阶段序列的业务字段（含narrative、结构条件、certainty和refs）逐项相等；只忽略各自局部ID，不以中文相似度匹配。相同序列可无损并入其余记录并只去除完全相同记录；不满足该条件的合并裁决拒绝，不隐式降级或追加模型轮次。

不同过程仅共享通用Activity，或不同variant顺序无法无损合并时，保留独立过程并记录关系。**数组拼接并不代表这些阶段按顺序执行。** 仓库归并不重建新生命周期，不改写已审业务内容。

## 8. 可读发布与最小Wire变化

### 8.1 正文

阶段呈现：业务名称 → narrative → 必要分支/转移 → 一个“查看依据”链接。不用括号列所有技术Activity名，也不在句末铺满SourceRef。

完整结构字段仍在catalog；Markdown可用“条件与结果明细”折叠区确定性保留全部字段，避免渲染丢失，但主要允许/拒绝条件必须在narrative中可读，不能只藏在折叠区。规则显示适用用法的业务名称，不输出机器ID墙。

有直接sourceRefs的阶段，“查看依据”跳到本过程来源索引，再列文件名、行范围，逐条跳到 `sources.md#s688`。全部引用保留，不任意截成前N条。

来源链接是非核心展示项：没有可用链接时允许留空，不强制增加说明、补齐、反查或专项验收，也不阻塞发布。复用现有来源即可，不新增证据模块或模型调用；主要验收始终是业务步骤、分支、具体规则和结果是否可读。

新增 `sources.md`，从已有SourceReference确定性生成：短编号锚点、文件相对路径、原行范围和完整snippet。源码围栏长度按内容处理，避免snippet破坏Markdown。它不是新证据，不访问源码磁盘。连同JSONL一起交付后，两个Markdown相对链接可移植。

### 8.2 输出和版本

canonical module仍为 `REPOSITORY_KNOWLEDGE/1/business-process-publisher`，producer v1→v2。

| 文件 | Schema | 变化 |
| --- | --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 | stage.narrative、rule.activityUseIds |
| process-coverage.json | repository-business-process-coverage-v2 | ActivityDisposition增加原Activity的name，供读者辨认范围 |
| business-processes.md | repository-business-process-markdown-v2 | 业务正文和来源链接 |
| source-refs.jsonl | repository-business-process-source-references-v1 | 原结构不变 |
| sources.md | repository-business-process-sources-markdown-v1 | 新的确定性来源视图 |

新增artifact type `REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_SOURCES_MARKDOWN`，其他type不改。coverage.name由程序投影，不让模型重写。

受影响的类型、schema registry、producer、publisher、reader、exact-file checks、artifact查询和fixture同批修改；不能只让内存对象支持。Step07八份目录/merge/过程/归并Prompt资源及响应合同同步v2；源码目录/输入投影改变也进入任务fingerprint。旧v1结果保留，不伪装成新结果，不复用旧语义job冒充修正质量。

上游Activity、M10、JDT版本不变；PROCESS_CATALOG output kind、三个owner和公共Agent方法不变。Step08仍不在本次生产路径，旧报告产物不覆盖。来源附件可通过现有artifact查询扩展闭集取得，不新增公共接口。

## 9. 后续实现的准确修改面

| 现有位置 | 修改 |
| --- | --- |
| DefaultBusinessProcessDiscovery | 卡片补原规则；候选多用法校验；完整Activity去重；源码预览；narrative和rule-use解析；禁止静默修正成员处置 |
| BusinessProcessPromptCatalog及8份业务发现Prompt | v2中文指令/响应Schema；不预置行业答案 |
| ProcessStage / BusinessRule / coverage disposition | 两个语义字段及一个确定性展示name；直接构造者同步 |
| 现有归并代码 | 保留新字段与use映射；禁止机械拼接冒充生命周期 |
| BusinessProcessMarkdownRenderer / CanonicalBusinessProcessPublisher | 业务正文、索引链接、sources.md、五文件发布 |
| BusinessProcessCheckpointReader / artifact registry和查询 | v2重开、引用闭合、来源视图重渲染一致 |
| 直接测试与saved-job匹配 | 多用法/分支范围/无损保存/链接/零上游调用/版本不误复用 |

这是下一份实施计划的输入，不是本轮代码改动。详细模块设计同目录同步；已完成的并发/JDT/旧实现计划保留历史原文。

## 10. 用已保存材料推演与验收

[完整例子](../examples/semantic-framework-walkthrough.md)逐项区分现有真实片段、程序投影、未来模型解释和未知项。

- 采购例：linkApply关联申请，采购订单另以linkNumber支持特定订单关联；采购入库借关联单号更新原单进度。不能说所有采购必须来自申请。
- 销售例：默认未审核与传入状态不同；修改/审核/反审核有具体状态限制；关联出库按数量更新订单进度；退货查询只能支持它实际表达的关联，不证明完整退货财务链。
- 已知最低售价检查不能泛化到销售订单；请购→采购→入库、销售订单→出库是需按材料重建的业务联系，不是单个入口的直接调用链。
- 别的领域使用同一代码和Prompt；领域词仅出现在fixture及模型输入/输出。

先通过离线直接测试，再做小范围真实目录+过程验收：先检查目录是否自己发现业务用法，随后查看一份订单生命周期和一份不同关联路径。不能手工塞正确候选后声称目录通过，不能再先全仓跑完才发现是技术说明书。

结构门检查引用、分母、版本和内容保留；语义门检查读者能否说清具体业务及步骤、分支、限制。二者分别报告。合法UNRESOLVED不自动失败，但“材料明明有谓词却写空话”“把子类型规则用错”“只有参数模板”不能被多Stage指标掩盖。

本轮没有新的模型结果。已有来源足以支持上述受限路径的设计推演，不等于保证自动发现所有业务；未发现/无法串联的部分必须明确记录。
