# 本体识别：定义与机器合同

状态：2026-10-05，正式机器合同、typed-v3、v2阶段/覆盖/查询及v4冻结材料已接通并经过直接验证；固定客户O0–O3已经有限结束、保存和查询。O1六已审，O2两已审/一拒绝，O3四文件部分发布保留全部九项义务。最后正式响应读取及未执行owner聚合两项边界已有20项直接回归和最终质量6695通过；实现已提交[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)。实际输入、模型质量和完整运行ID由[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)维护；结构合法不表示业务零错误。旧格式严格读取，历史Activity/Process Schema不改变。

## 1. 五类结论，不混为一份散文

| 类型 | 要回答的问题 | 最小定义，不足时保留什么 |
| --- | --- | --- |
| Object Type | 系统操作的是什么，同一个对象如何再次找到 | 名称/含义、身份、存储/程序映射、业务变体条件、属性；缺身份保留候选而非部署就绪类型 |
| Link Type | 两个对象如何联系 | 两端类型、标识对应/查询或写入路径、成立条件、方向、可选性/基数已知范围；未知基数不填one-to-many |
| Operation / Rule | 对什么对象，在什么条件下允许或拒绝什么，改变什么 | 业务参数与程序映射、前置/拒绝条件、修改/输出、适用变体、支撑查询与未知；不将所有HTTP查询定义为修改Action |
| Dimension | 按什么口径分类、筛选或观察 | 所属对象/指标、来源字段/派生式、时间或类别语义、关联路径、已知值域/层级和适用范围 |
| Measure / Metric | 实际计算什么，怎样避免重复和口径混淆 | 输入粒度、表达式、聚合/组合、过滤/时间窗、单位、允许维度、去重/连接规则、返回端处理和未知 |

业务对象与技术实体分开：表/DTO/方法/路由是mapping。一个表可以承载多个对象变体；一个对象可以组合多表；没有表的临时业务结果也可作为有范围的候选。不能从表名直接批准一个Object。

Operation分为 MUTATION、QUERY、ANALYSIS。只有有对象修改依据者才标为Action候选；支撑查询可以独立保留，不能为了流程好看强行改为办理步骤。管理权限、事务声明、外部效果各依实际证据记录，不能从“有HTTP接口”推断。

## 2. 已实现定义与分析扩展分开

每项有 origin：

- IMPLEMENTATION：来自本次固定实现。仍需certainty与review，不能因来源是代码就全部CONFIRMED。
- ANALYTIC_EXTENSION：提出的新观察维度/指标/问题。必须说明可用输入、缺少输入和建议用途；不会出现在“系统已有指标”清单。

例如“按业务日计算库存可用天数”可能需要库存基准时点、净销量口径、自然日分母和业务日历。源码只包含库存数与销售总额时，不能默认生成已确认可用天数，更不能伪造真实计算值。

主体可收录有明确未知的部分定义，但首页和机器输出必须分开展示已核实实现、推断、待确认及建议。允许没有指标/维度：空结果要说明实际已读范围，不能用ERP常识补齐。

## 3. 公共结论骨架

所有候选/已审项共用以下内容，但任务Schema只开放当前kind所需字段，不让小任务返回整个本体：

```text
localId
kind
name / definition
origin
certainty = CONFIRMED | INFERRED | UNRESOLVED
scope：业务变体、入口用法、已读范围
evidenceRefs：当前阅读包的合法引用
unknowns：具体未知；含影响哪部分定义
review：实际审阅任务引用/结论，由程序保存
```

certainty是模型对本次所读静态证据的审阅等级，不是置信概率、机器证明或人类接受。模型可以提出枚举，但Java只能检查枚举/来源合法；publication整体固定DRAFT_REVIEWABLE，人工验收另存状态。关键未知须落在相应身份/规则/指标项上，不能藏在文件尾部“可能有误”的统括免责声明。

不建立每个中文句子一份Proof的体系。以有业务意义的定义/规则/计算项为核对单元，已有来源ref足够；技术错误观察、未读范围和材料损坏各保持原性质。

## 4. Object与Property

Object字段：

- identity：零或多组候选键，每组含sourceBindings、scope、uniquenessBasis及unknowns。唯一性的来源区分DDL声明、代码期望、已观察实例（本轮没有实例）、未确认。
- backing：table/type/read/write映射；声明业务变体的predicate及来源，不能只写表名。
- properties：业务名称、程序/存储字段、数据类型及nullable已知范围、原始/派生性质、单位、值域/含义及refs。
- rolesOrVariants：模型识别的变体/角色及必要条件，不强制先建继承层级或全表基础对象。
- lifecycleFacts：具名状态/动作引用；只收实际有据的部分，不自动补“申请→批准→完成”模板。

Property ID只在所属Object语义范围唯一。相同status字段用于不同对象时，值0/1的含义与适用动作分别记录；同一status同时承载审核和办理进度，也必须保留操作语境，不能强制成单一漂亮状态机。

“id是表主键”与“业务单号在所有租户中全局唯一”是不同断言。当前代码有重复号检查、DDL只有普通INDEX时，前者可直接支持，后者不能靠猜测补齐。

## 5. Link与跨入口业务联系

Link字段：

- endpoints：当前任务输入中已有的已审Object键；对象身份可以PARTIAL，但该对象定义本身必须真实存在。缺端点则放顶层unresolved，不生成带悬空端点的Link，不用对象名伪装ID。
- mechanism：保存的字段对应/赋值/读取/连接依据及实际sourceRefs。不得只有“两个对象相关”的自然语言。
- applicability：对象变体、非空条件、删除/租户/状态等已知限制。
- cardinality：declared、codeImplied或unknown及其依据。SQL JOIN本身不证明基数。
- direction / optionality：按定义含义表达，不把SQL左右表当业务方向。
- inverseName：可选展示；不能新增没有依据的逆向操作。

跨入口比较结果允许 SAME_OBJECT、ROLE_OR_VARIANT、BUSINESS_LINK、SUPPORTS、UNRELATED、UNRESOLVED。比较输入可含多个候选，但不可超容量。SAME_OBJECT要求实际身份与语义一致；名称相同/同表/共用Controller均不足。

技术候选边不进入已确认Link。程序可列“entry A与B引用同一个statementRef”，模型须再读完整上下文；共用日志/用户会话方法不得成为业务先后依据。

首版不输出Process fragment。以后若确需过程展示，须另行限定为已识别对象/操作之间有依据的交接，不重新建设Activity/完整过程发现。当前业务link与操作效果已足以表示对象联系，不能为可读性增加无条件流程顺序。

## 6. Operation与Rule

Operation字段：

- kind：MUTATION/QUERY/ANALYSIS；name和业务目的。
- targetObjectUses：对象和具体变体；一条通用实现可有多个用途。
- parameters：业务含义、程序来源、身份/数量/金额等适用范围；不以beanJson/request等程序包装名代替业务输入说明。
- preconditions / rejectionConditions：完整谓词与来源；条件的AND/OR/分支原义用表达式原文和明确说明保存。
- effects：对象字段的设置/递增/保存/删除、关系变化或输出；conditions和targets不得省略。
- externalEffects / permissions / transactions：有证据时记录；没有则unknown，不填默认权限角色。
- entryUses：准确入口用途，而不是Action ID等于entryId。

Rule字段：owner、applicability、condition、consequence、rejection/output、certainty、refs、unknowns。规则适用于具体对象用途和分支；通用方法里的采购退货规则不能扩展为所有采购。

表达式保留原JAVA/SQL/XML条件文本及字段绑定，业务说明供阅读。首版不设计自己的可执行规则DSL，不运行OGNL、不求值Java、不用中文关键词检查规则真假。

## 7. Dimension、Measure和Metric

Dimension字段：

- ownerRefs与sourceBindings；CATEGORY/TIME/DERIVED性质。
- role：GROUPING、FILTERING、DISPLAY可多选，各有实际依据。
- definition、knownValues/unknownValues、hierarchy（确有来源时）、timezone/calendar未知范围。
- joinPath与适用指标。属性能筛选不等于能对所有指标安全分组。

Measure字段：

- inputGrain：例如distinct单据头、商品明细、库存快照；具体键/范围。
- expression：原表达式及字段绑定；aggregation、sign/null/rounding政策已知范围。
- filters：删除、状态、子类、权限及动态条件；每条件的分支语境。
- unit：金额/数量等业务量纲，币种/基本单位是否已知。未知币种不得填CNY。
- sources与limitations：SQL PARTIAL保留；AST没列出的XML分支仍需读取原文。

Metric字段：

- componentMeasureRefs与组合表达式；可直接为一个measure，也可由多个组成。
- grain、timeWindows、allowedDimensions、去重/连接规则及返回端变换。
- implementationStatus：已有实现/建议定义；definitionCompleteness及具体missingFields。
- executionReadiness：首版固定NOT_EXECUTABLE，表示只识别定义，未接运行数据库。

指标审阅必须同时考虑Java后处理、SQL取数、动态XML及必要UI语境。不能只见SUM就批准指标；不能把付款额、订金、应付额混成同一个amount。权限遮蔽的返回值不是原始数值。DDL允许NULL但计算没有NULL保护时，须记录正常非空路径与异常/未确认数据范围，不能默认NULL=0或承诺所有合法存储行均可计算。

### 7.1 正式任务字段：可组装，不让程序再读中文补结构

以下是**已由正式typed-v3 runner、Schema和validator实现的字段约定**，31项相关直接/历史测试及限定复审通过；正式CLI已有scripted Agent/store直接验证；三个真实问题已通过O1/O2/O3有限结束并保存有效部分；九任务八已审一拒绝，模型质量和工程交付分别记录。它不是旧PoC最终JSON的字段。共同必需顶层为`schemaVersion / taskKind / definitions / unresolved / corrections`；schemaVersion分别为ontology-typed-candidate-v3或ontology-typed-review-v3。definitions只开放当前任务的数组：OBJECT为objects，ACTION为operations/rules，ANALYTIC为dimensions/measures/metrics，RELATE为links。只有RELATE另要求顶层`identityDecisions[]`，无决定时为空；其它任务不接收该字段。数组可空，任何类型不靠数量门槛凑定义。

共同字段为`localId / name / definition / origin / certainty / scope / evidenceRefs / unknowns`。scope含本次具名问题及业务变体/入口用途引用；unknowns为`{field, reason, missingUnitRefs}`，无法定位单元时refs可空但reason必填。中文说明不是ID字段。最终review元数据、全局定义身份和模型回执由程序添加，不由模型伪造。

本节是模型响应的准确字段合同，第3节是概念骨架。定义类别由所在数组确定，不再增加一个重复的通用`kind`字段；operations的`kind`专指MUTATION/QUERY/ANALYSIS。对象、关系、操作、规则、维度、组成量和指标的本次局部编号分别为`O1/L1/A1/R1/D1/V1/M1`及同前缀的正整数后续编号；Property在对象内使用`P1`。输入已审目录仍统一使用B键，不将这些局部编号直接当作跨任务身份。

| definitions键 | 必需的结构化字段 | 空值/引用约定 |
| --- | --- | --- |
| objects | identities[]、properties[]、backing[]、variants[] | identities可以为空，但对象definitionCompleteness=PARTIAL，且unknowns必须具名这一结构字段：正式写`field="identities"`；validator仅为既有候选兼容`"identity"`。不从reason prose推断，不填默认主键 |
| links | fromObjectRef、toObjectRef、mechanism[]、conditions[]、cardinality | 两端必须是输入已审对象键；缺端点进顶层unresolved，不能填对象名或复合说明 |
| operations | kind、targetObjectRefs[]、parameters[]、preconditions[]、rejections[]、effects[]、entryUses[] | 目标及effect目标只准输入已审对象；多目标用数组，缺目标进unresolved；QUERY不要求虚构更新 |
| rules | ownerRef、applicability[]、condition、consequence、evidenceRefs、unknowns | owner只准输入已审对象/操作或本次operations键；依赖同包操作时随它完整REVIEW，未解owner不生成Rule |
| dimensions | ownerRefs[]、sourceBindings[]、roles[]、grain、joinPath[] | GROUPING/FILTERING/DISPLAY按实际用途选择；时间/值域未知可null并列unknowns |
| measures | ownerRefs[]、inputGrain、expression、aggregation、filters[]、postProcessing[]、unit | expression保留原文/绑定；聚合、单位等缺项为null，不能猜SUM/CNY |
| metrics | componentMeasureRefs[]、expression、grain、dimensionRefs[]、timeWindows[]、executionReadiness | 只引用输入已审目录或本次同包measures/dimensions；不能造未返回组件；固定NOT_EXECUTABLE |

对象identity每项含`parts[{propertyRef, sourceBinding}] / scope / uniqueness{basis,status,evidenceRefs} / unknowns`；basis区分DDL_DECLARED、CODE_EXPECTED、UNKNOWN，本轮没有INSTANCE_OBSERVED。Property含localId、name、definition、sourceBindings、dataType、nullable、derivation、unit、evidenceRefs和unknowns。dataType/nullable分别表达已知值或UNKNOWN，不能用空字符串伪装值。

sourceBinding描述**技术映射**：JAVA_MEMBER、TABLE_COLUMN、FRONTEND_VALUE或DERIVED的实际名称/表达式及evidenceRefs。名称本身允许是源码字符串，不等于程序确认它已解析成符号；缺解析身份须保留限制。Model不能据此创建外部数据库/来源身份。

条件/效果/连接mechanism按语义项保存`description / expression{language,text} / target或binding / evidenceRefs / unknowns`。expression是有来源的JAVA/SQL/XML/JS原表达式，不是新可执行DSL；缺完整表达式时保留已知部分和UNKNOWN，不让Java执行或读中文猜运算。

数值属性赋值/符号转换首先是Property derivation或Operation effect；只有确有聚合及输入粒度依据才为Measure，组合/业务口径才形成Metric。辅助PoC的`totalPrice`、`changeAmount`不能因为在实验metrics数组出现就无条件升级经营KPI。指标或维度缺失只记录本任务未识别/未处理，不证明系统不存在。

ANALYTIC允许同一小任务产生组成量/维度和指标：EXTRACT阶段它们全是候选，候选内交叉引用按本次localId核对；REVIEW看到完整同包，最终measures/dimensions/metrics整体校验、保存。只有先前已审目录或最终同包仍存在且合法的组件可引用；REVIEW删除V1时必须同步修正依赖M1，或将M1移到unresolved。不把候选V1提前当成已审给另一个任务；不强制加一次模型调用。包仍必须能容纳完整实际REVIEW。

顶层unresolved每项固定含`issueId / proposedKind / description / knownDefinitionRefs[] / relatedLocalDefinitionRefs[] / missingRequirements[] / evidenceRefs[]`。missingRequirements每项含`field / reason / unitRefs[]`；无法定位材料则unitRefs为空。knownDefinitionRefs只含输入已审键，relatedLocalDefinitionRefs只含本次最终存在的定义键；description允许说明尚未建立的对象含义，但没有“文字对象ID”。Relation缺端点或Operation缺目标时在此记录已知端、机制及缺什么，完整候选原稿仍保存。Publisher保留这些未解项，不把它们当确定Link/Action；没有对应定义时relatedLocalDefinitionRefs为空。

模型不得在ACTION/RELATE/ANALYTIC任务偷偷创建对象端点；需要新对象则具名未解并进入后续OBJECT任务。OBJECT REVIEW可按原文拆分对象，变化对象的ID/处置显式记录；下游只能使用它的最终已审目录。

### 7.2 ID作用域、跨题对应和纠正

保存定义身份为`(corpusIdentity, producingTaskId, localId, reviewVersion)`。corpusIdentity包含完整SelectedSourceBasis（版本及有效范围）、准确R4 publication/receipt及其内容摘要、同R0补充选择、索引/投影规则和工具身份；不是只用snapshotId。O0运行owner另存，不以输出目录替代内容身份。O1只是显示短键，不是全仓对象ID。来源S1则为`(readingPacketId,S1)`。不同题的O1/S1绝不直接合并；相同中文名称不是身份键。

正式O0的corpus payload保存`corpusIdentity / selectedSourceBasis / evidencePublication / preparationControls / preparationBindings / projectionRuleVersion`，原有contentSourceIdentity仍指准确R4内容。sourceBasis及publication复用已有完整wire；preparationBindings保存实际resourceBudgetRef、schemaBundleRef、toolchainRef；preparationControls保存O0 normalized reading的八个实际字段。projectionRuleVersion严格为`ontology-model-projection-v1`或`ontology-model-projection-v2`，不是模型正确性的证明。v1严格重建历史metadata-only Corpus并使用formal reading packet v3；v2严格重开其保存的R4→R3→R2/R0补读Corpus并使用formal reading packet v4。O1/O2/O3按保存的规则选择这两个packet家族、身份和恢复路径，不用当前目标Policy、Prompt或模型配置替换它们；后续阶段自己的派发上限另行保存。O0控制改变则正式身份改变；相同输入/控制的独立owner可以保持相同内容身份，不将运行ID当内容版本。

这里的“读取O0控制”用于恢复原Corpus身份、别名和材料准备约定，不表示后续请求忽略当前阶段的派发限制。O1/O2的实际请求分别受本次保存配置的`maxRequestBytes / maxOutputBytes / maxOutputTokens / maxRequests`约束；O0允许较大的请求，不授权O1绕过自己更小的限制。额度以本次运行实际派发计数，依赖任务共享该计数；不累计其它O1运行，也不从已审任务数量推算。后续阶段限制改变不改写O0；当前限制与O0材料约定不能同时满足时返回具体容量问题，不能截断原文或悄悄放宽。

正式模型输入的已审目录使用独立短键`B1、B2…`，按完整定义身份稳定排序；不同类型共用这一个目录编号空间，私有映射另存类型和实际定义身份。当前任务创建的`O1/A1/D1/V1/M1/L1`不与B键混用。已审Property引用为`B1.P1`，本次OBJECT内的Property引用为`O1.P1`，所属对象不可省略；Schema及实际目录共同限制哪些键可用。这样，先前已审的V1与本次新建的V1不会因裸编号相同而串用。目录编号不是名称匹配或自动合并。

这里的producingTaskId绑定实际已保存任务身份及其输入，不仅是用户清单中的短taskId。reviewVersion绑定该任务实际完成的审阅记录，不仅是Schema版本字符串。不同问题即使复用相同taskId，仍不能产生相同的定义身份；无需另建身份注册平台，使用既有任务匹配和保存记录即可。

下游任务获得程序生成的完整已审对象目录短键和版本映射，Schema限制合法键。RELATE响应的顶层identityDecisions每项固定为`{decisionId,kind,leftRef,rightRef,canonicalRef,conditions,evidenceRefs,unknowns}`，left/right只准输入已审对象键。只有SAME_OBJECT要求canonicalRef为两端其中一个已有键；其它kind的canonicalRef必须为null，不创造第三个对象或表达隐含拆分。只有自身REVIEW通过的SAME_OBJECT对应才执行统一，其余ROLE_OR_VARIANT/BUSINESS_LINK/SUPPORTS/UNRELATED/UNRESOLVED保留独立身份；BUSINESS_LINK的具体机制放definitions.links，不自动由decision生成Link。Link端点、Operation目标、Property所属、Metric组件的字段引用在统一后机械重映射，中文业务字符串不替换。

SAME_OBJECT只统一对象身份，不授权程序选择哪条冲突属性/规则正确。Property的保存键含原完整Object定义身份及property.localId；两个题的P1先保留为不同属性键，统一owner不使它们相互覆盖。重复属性可以保留独立来源/候选对应；显式对应缺失时不按名字合并。相互矛盾的canonical决定或已知属性冲突保留具名未解，Publisher不得静默最后写入者胜出。它可报告结构/明确冲突字段，不能判中文同义。

corrections按定义ID保存变更/删除/新增及理由、相关原文refs。最后REVIEW可修正结构错误草稿，但原稿不变；完整最终结果必须严格通过Schema和引用检查。原草稿中文改写不让程序推断等价，逐定义变化有对应correction；无改动可空。这个检查不证明修正的语义必然正确。若模型把本次对象内的Property声明localId写成`O1.P1`，程序只在所属对象唯一且`P1`不冲突时规范为声明键`P1`；结构引用仍须写`O1.P1`。本次最终定义误填到unresolved.knownDefinitionRefs时，只在它确实属于本次最终定义的情况下移入relatedLocalDefinitionRefs。corrections属于结构差异账本：程序对实际草稿和最终定义的顶层ID计算ADDED/CHANGED/DELETED，模型账本不符时替换为该机械差异；不改写业务定义或从中文猜等价。原始REVIEW、规范化结果及逐项规范化事件同时保存在私有阶段记录，重开时重新计算并核对；规范化后仍有坏Schema、非法来源或断开引用则拒绝，不自动派发额外请求。

结构记号核对采用`ontology-formal-review-notation-v5`：ACTION的rules若把结构`localId`误用为`D1`，仅在本任务没有同号`R1`冲突时改为`R1`，并同步改结构化corrections和relatedLocalDefinitionRefs。ANALYTIC的measures若把唯一结构`localId`写作`L1`，仅在所有当前定义中没有同号`V1`冲突时改为`V1`，同时改metric.componentMeasureRefs、corrections和relatedLocalDefinitionRefs。业务描述、公式和过滤字符串均不改。已审目录的旧局部编号（如上一OBJECT的`O1`）误填到unresolved引用时，仅根据保存目录中唯一的localId→B键映射改为knownDefinitionRefs；跨任务存在同名局部编号时不猜测，最终严格拒绝。corrections差异计算与最终校验使用同一份“可识别草稿定义”：草稿即使把同一ID误放到两个类型中，仍按首次可识别定义核对实际审阅稿；原始冲突和诊断不删除，最终审阅稿仍须通过唯一性检查。若审阅明确删除了草稿中的定义，仅移除unresolved.relatedLocalDefinitionRefs中指向这些已删除定义的悬空编号，保留未知说明、原文引用及原始审阅响应；从未出现在草稿里的未知编号仍拒绝，其他定义内部的业务引用不自动改写。v5另处理一种冗余结构位置：metric.grain.keyRefs中的D编号，只有同时属于当前已定义维度及该metric.dimensionRefs时才移除；真正的属性引用和维度引用保留，绝不把D编号猜成某个属性。已有v1–v4阶段记录重开分别使用原规则，不以新规则改写历史结果。每次转换保存事件、原文和规范化结果并重算核对，不能借结构规范化修正金额、条件或业务关系。

验收分别判断材料与模型：程序要证明所选联系的建立端、使用端、必要条件、原文和已知限制均已交给对应任务并能重开；材料齐全后模型仍误读，记录为模型质量问题，不把它伪装成材料缺失。任何缺材料或误关联都属于框架/取材问题；调用计数只作审计和成本观察，不替代这两类判断。

### 7.3 正式 Schema 的小型嵌套结构

本节是正式 typed-v3 的实施约定，**尚不是已经生成的模型结果**。它补齐7.1的字段形状；实现和测试使用同一组结构，不能让每个任务自行选择字符串或任意JSON。所有对象关闭未知字段；有意义的缺值使用明确的null/UNKNOWN及unknowns，不用空字符串。表达式只保存原文，不求值。

| 结构 | 固定字段和含义 |
| --- | --- |
| Expression | language（JAVA/SQL/XML/JS/DDL/DERIVED/UNKNOWN）、text（原表达式或null）、bindings（symbol、definitionRef、propertyRef；后两项可null）、evidenceRefs。定义/属性引用须在当前合法目录或同包允许范围内；symbol只是原文名称。 |
| SourceBinding | kind（JAVA_MEMBER/TABLE_COLUMN/FRONTEND_VALUE/DERIVED）、owner（技术类型/表/页面名称或null）、name（实际字段/表达式名称）、expression（Expression或null）、evidenceRefs、unknowns。owner/name是技术字符串，不充当业务对象ID。 |
| SemanticItem | description、expression（Expression或null）、targetObjectRefs、sourceBindings、evidenceRefs、unknowns。用于条件、连接机制、过滤及后处理；targetObjectRefs只含实际合法对象，纯条件可为空。 |
| Effect | SemanticItem的字段加conditions（SemanticItem数组）。条件属于这个效果，不能移为所有操作共用条件。 |
| Grain | description、keyRefs、unknowns。keyRefs引用准确属性目录；粒度未确认时数组可空并说明未知，不造主键。 |
| TypedValue | status（KNOWN/UNKNOWN）、value（非空字符串或null）；UNKNOWN时value必须为null。用于dataType和unit；已知金额量纲不自动补币种。 |
| Variant | name、conditions（SemanticItem数组）、evidenceRefs、unknowns。这里只记录有依据的用途/条件，不形成继承推理。 |
| Parameter | name、definition、sourceBindings、required（TRUE/FALSE/UNKNOWN）、evidenceRefs、unknowns。业务含义与程序字段分别保存。 |
| EntryUse | entryRef、conditions（SemanticItem数组）、evidenceRefs、unknowns。entryRef只能指本包实际入口短键，不能填URL当ID。 |
| Cardinality | basis（DECLARED/CODE_IMPLIED/UNKNOWN）、value（ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/MANY_TO_MANY/UNKNOWN）、evidenceRefs、unknowns。UNKNOWN不补常识基数。 |

具体应用：Object.backing使用SourceBinding数组，variants使用Variant数组；identity的parts仍是propertyRef加SourceBinding，scope使用共同scope结构，uniqueness.status为CONFIRMED/UNCONFIRMED/UNKNOWN。Property.derivation为Expression或null、nullable为TRUE/FALSE/UNKNOWN，dataType/unit使用TypedValue。Object.definitionCompleteness只准COMPLETE_FOR_READ_SCOPE/PARTIAL；没有身份时必须PARTIAL并说明。

Operation.parameters使用Parameter，preconditions/rejections使用SemanticItem，effects使用Effect，entryUses使用EntryUse。Rule.applicability使用SemanticItem数组，condition使用SemanticItem，consequence使用Effect。Relation.mechanism/conditions、Dimension.joinPath、Measure.filters/postProcessing均使用SemanticItem数组。Measure.expression、Metric.expression使用Expression；grain/inputGrain使用Grain。Measure.aggregation为非空的原聚合说明或null；Metric.timeWindows是SemanticItem数组，另保存implementationStatus（IMPLEMENTATION/ANALYTIC_EXTENSION）与definitionCompleteness；executionReadiness固定NOT_EXECUTABLE。

共同scope固定为questionRef、entryUseRefs、variants（非空用途说明字符串数组，可空）。unknowns和unresolved仍使用7.1已有形状。corrections固定为targetLocalId、changeKind（ADDED/CHANGED/DELETED）、reason、evidenceRefs；目标必须在实际原稿或最终稿中存在，不能写无对应项的笼统纠正。原稿没有可识别localId的损坏片段只在原稿和程序诊断中保留，不能为了DELETED纠正项编造一个原ID；最终新定义仍须ADDED纠正。结构错误草稿不先满足最终Schema，才能进入原定REVIEW；最终完整定义及合法引用依然严格检查。本文第8节是概念节选，不是完整正式Schema夹具；正式Expression引用统一使用bindings，不再另设componentRefs来重复组件数组。

### 7.4 发布时统一身份，不销毁原定义

正式发布区分两种键：`globalId`是完整已审定义身份的稳定键；对象的`canonicalObjectRef`是实际已审SAME_OBJECT决定后采用的对象身份键。未统一时两者相同。`definitionIndex`保存原Corpus/任务/局部编号/审阅版本到这两个键的对应，不把裸O1作为索引键。

`objectTypes`保留每份实际已审对象定义及其原始身份、来源和未知；统一后共同指向canonicalObjectRef，不能删除非canonical定义中不同的身份范围或属性。Link端点、Operation目标和Property当前owner使用统一后的对象键；Property另保留原owner定义身份和自己的稳定属性键，因此两个原对象的P1不会相互覆盖。这里的“统一”是执行已审身份对应，不是Java自动合并中文定义、选择业务名称或合并同名属性。

公开对象内的每条Property统一使用`globalId`保存稳定属性键，另有`originalOwnerObjectRef`（原对象定义的globalId）和`ownerObjectRef`（实际采用的canonicalObjectRef）。属性键由原对象完整定义身份及属性localId确定，不因对象对应发生改变。结构化Property引用如`B1.P1`映射到该属性`globalId`；不是映射到对象键，也不按属性名称查找。这三个字段由确定性发布器产生，不要求模型预先填写。

只有确定、无歧义的SAME_OBJECT映射可以执行。相互矛盾或无法唯一得到canonical身份的决定保留为具名未解，不能按输入顺序挑选一个。不同属性的已知值不被程序自动裁决为等价；来源、明确冲突字段及模型未知继续保留。Publisher只机械处理已定义的引用字段，表达式text、名称和说明不替换。最终索引必须能从原定义定位到保留的完整记录和实际对应决定。

正式局部组装对结构上互相矛盾的SAME_OBJECT canonical决定采用明确的保留规则：受冲突影响的对象不执行统一，各自保留原`globalId`为`canonicalObjectRef`；全部原定义、属性和决定仍保存。`ontology-review.json.assemblyIssues[]`记录`code="IDENTITY_CANONICAL_CONFLICT"`、排序后的`definitionRefs[]`（受影响对象的globalId）、实际`producingTaskIds[]`（产生矛盾决定的RELATE任务完整身份，不是端点OBJECT任务或清单短taskId）及`detail`；两组引用均排序、去重。供查看和后续人审，不由Java选择哪一条业务决定正确。这是结构冲突诊断，不是模型返回的确定Link；该未解决组装义务不能报告完成。

Task 6 的纯组装入口 `OntologyScopedAssembler.assembleFormal(FormalInput)` 已有实现；16项直接覆盖测试与限定独立复审已通过，验证合法规则归属、未解项引用、原始对应决定/生产者、可逆来源及局部覆盖。该入口消费实际保存并重开的 formal 结果、显式 binding 和 scoped coverage，生成四项字节；不安装产物、不写 receipt，也不替代既有实验 `assemble()`。正式runtime另已验证多选O1/O2汇合、实际scripted纠正记录公开投影和四文件安装；这些工程检查点不代表真实模型或人工验收完成。

## 8. 一份目标JSON的形状

以下是**目标合同形状的人工节选，不是当前模型产物**；O1/P1/S1等仅在这一示例包内有效，V1/V2/D1指输入已审目录或同一次最终ANALYTIC包中实际存在的定义。正式字段使用对应闭合Schema，不能按这个节选补默认值。

```json
{
  "localId": "M1",
  "name": "分期间净采购额",
  "origin": "IMPLEMENTATION",
  "certainty": "CONFIRMED",
  "definition": "期间内采购入库的优惠后金额合计，减采购退货的优惠后金额合计",
  "scope": {"questionRef": "Q1", "entryUseRefs": ["E1"], "variants": []},
  "componentMeasureRefs": ["V1", "V2"],
  "expression": {"language": "DERIVED", "text": "V1 - V2", "bindings": [{"symbol": "V1", "definitionRef": "V1", "propertyRef": null}, {"symbol": "V2", "definitionRef": "V2", "propertyRef": null}], "evidenceRefs": ["S1", "S2"]},
  "grain": {"description": "经查询条件筛选并去重的单据头", "keyRefs": ["O1.P1"], "unknowns": []},
  "dimensionRefs": ["D1"],
  "timeWindows": [],
  "evidenceRefs": ["S1", "S2"],
  "unknowns": [
    {"field": "unit.currency", "reason": "已读实现未给出币种定义", "missingUnitRefs": []},
    {"field": "timeWindows", "reason": "节选未展示时间窗，须读取完整组成量定义", "missingUnitRefs": []}
  ],
  "executionReadiness": "NOT_EXECUTABLE"
}
```

此处CONFIRMED仅指展示的计算口径，不消除unknowns；其组件、过滤、时间窗和权限规则必须在完整定义中保存，节选不是可直接执行的指标配置。正式review应核对全部组件，而不是这个短例子。

## 9. 输入材料和局部引用

资料身份为完整SelectedSourceBasis + R4 receipt + optional同R0资料选择。技术去重身份为(sourceBasis, kind, originalIdentity)，不能把不同版本或仅文本相同的独立业务用法合并。

Mapper语句身份同时保留已有`databaseId`；同一原身份对应冲突内容或无法区分的变体时不能静默选一份。SQL表/列目录只来自实际AST节点，缺节点保留覆盖未知。真实`statementRefs`对象形状的读取缺陷已修复；当前包引用反查限制见[共同线索设计](evidence-navigation-and-selection.md)。

模型包：

```text
question / taskKind
knownCandidateDefinitions（只含实际上游任务）
units：完整已选择源码/statement/DDL单元，及必要条件/调用上下文
observedUses：入口/页面实例、实际与形式参数、明确或候选性质
limitations / unreadRequiredUnits
scopeLocalAllowlist
```

正式目标：证据接口在O0就给稳定的Corpus短键E/U/K；分页不重置编号。模型只返回本次实际可见allowlist中的短键，资料/轮次作用域由请求记录携带，不让模型复写viewId。正文包统一S键、定义各用O/L/A/R/D/V/M键，不能互用；完整映射和实际可见范围参与指纹。内部entryId/methodKey/statementRef继续保留。详见[短号合同](material-preparation.md#7-短-id-提前提供不替换稳定身份)。输入无宿主路径、凭据、运行控制或哈希；选材可见允许的源码相对路径/已存范围，提取只见已有引用和正文。

来源ref由程序产生，模型只能引用allowlist，不能新造文件/行号/DDL事实。最终来源表回到R4或同R0路径和真实范围，不将模型生成文字当证据原文。

### 9.1 私有选材合同与最终原文引用分开

SURVEY保存具体问题、线索引用、可能相关的`candidateEntryRefs`与未知；PRIORITIZE v3保存选择理由、选中问题/任务及延期处置，**不包含`initialReadRequests`**。首批完整单元由该题READING从专用导航页显式请求，或者先查询再读；保存实际请求和用途。候选入口不等于必读或业务成员；EXTRACT/REVIEW仍必须收到必要原文。

查询观察保存种类、准确键/变体、页身份、offset/limit、实际总数/命中/未命中、完整单元句柄和大小、技术性质及限制。只支持Corpus反查、入口单元页、字面搜索/读回的薄适配；字面搜索分页与唯一语句变体读回已接通，歧义变体明确拒绝。不允许任意表达式、shell或网络。原始响应与程序观察分开保存，越界/错误类别引用拒绝。已读方法/语句包引用的合法查询种类见[类型矩阵](cross-entry-reading-contract.md#2-p2i-的真实断点和最小查询合同)。

现有实验v2决策使用`{viewId,ref}`，按原合同只读保留；正式目标改为请求绑定作用域+Corpus稳定短键，私有决策写v4，不自动迁移/复用v2/v3。导航引用、readRequests及retainedRefs分别验证类别和实际可见范围；零正文输入只能NEEDS_MORE_MATERIAL或具名UNRESOLVED，retainedRefs为空，不能借占位正文READY。

线索短键只能用于查询和选材，不能冒充最终定义的`evidenceRefs`。实际原文引用带其阅读包/视图映射；映射与查询结果进入任务匹配，不对长ID模糊匹配、不替换中文正文。详见[读写链路及私有保存](evidence-navigation-and-selection.md#7-保存匹配和读写链路)。

## 10. 覆盖与发布

ontology.json包含：schemaVersion、publicationStatus=DRAFT_REVIEWABLE、source/input引用、系统假设、objectTypes、linkTypes、operations、rules、dimensions、measures、metrics、analysisExtensions、unknowns及definitionIndex。可空数组有明确已读范围，不能将空解释为系统没有该能力。

根级`analysisExtensions`与`unknowns`是已审结构的**确定性索引**，不让Java从业务文字猜建议或未知。`analysisExtensions`逐项指向同一文件类型数组中`origin=ANALYTIC_EXTENSION`（或指标`implementationStatus=ANALYTIC_EXTENSION`）的定义，保存`globalId`、`definitionType`及实际`producingTaskId/reviewVersion`；不是第二份定义。`unknowns`分别索引定义内非空的`unknowns`（`definitionRef`为定义全局编号、`path`为字段所在结构路径、`item`为原结构化未知项，并保存同一任务身份）和审阅顶层`unresolved`（`path`指向该项、`item`为已映射结构，并保存实际任务身份）。两个索引按稳定身份排序；空仅表示**已审范围内未返回此类项**，不是全系统没有建议或未知。详细纠正及未解处置仍保存在`ontology-review.json`，不得靠这两个索引替代。

ontology-coverage.json分别记录：

- inputCoverage：R4全入口/前端请求和选定DDL文件分母、已纳入/未纳入/源限制。
- readingCoverage：navigation页、实际已读完整单元、必需未读/超容量/未支持。
- semanticCoverage：问题与候选的已审/拒绝/未处理/未确认处置。
- scope：SCOPED或REPOSITORY；coverageStatus：COMPLETE/INCOMPLETE/UNDETERMINED。

COMPLETE只表示**声明的处理义务**全部完成：选定来源已登记、必需阅读有处置、指定候选/问题/任务均有最终结果；不表示业务模型穷尽或每项语义正确。另存semanticExhaustiveness=UNDETERMINED，本轮不能宣称已经识别一切业务。PoC只允许SCOPED；以后REPOSITORY表示输入范围来自全仓，不消除该未知。

SCOPED不能发布成REPOSITORY。已审对象多不代表所有入口已认识；原始技术限制不能因语义层成功消失。全仓导航看过但未读原文，不等于全仓业务已核实。

ontology-review.json保存最终处置、修正/不合并原因、实际模型身份和人工验收状态。程序不能把“模型review返回”写成“用户已接受”。正式发布的每条identityDecision和unresolved另带实际corpusIdentity、producingTaskId和reviewVersion。决定的leftRef/rightRef/canonicalRef保留原已审定义的完整全局编号；只有SAME_OBJECT另以resolvedCanonicalObjectRef保存统一后的对象身份，其它决定该字段为null，不能用统一后的同一个编号覆盖原比较两端。relatedLocalDefinitionRefs必须回到本任务最终存在的定义，不能发布裸O1/A1或串到别的任务。

发布根级的可选`typedReviewSchemaVersion`只声明发布器接收的正式审阅协议，例如`ontology-typed-review-v3`。它不是从模型原文摘出的内容、审阅成功证明或实际`reviewVersion`，不能替代每项真实任务及修正来源。实际原稿、审阅和诊断必须仍可按准确任务查询。

正式局部覆盖中，已知必需阅读未完成（`REQUIRED_UNREAD`或必需内容`TOO_LARGE`）、已声明任务`UNPROCESSED`/`REJECTED`、或上述未解决组装冲突，必须为`coverageStatus=INCOMPLETE`；不能因为已保存部分定义而缩小分母。仅准备了材料的`PREPARED`不计模型已读。真正零任务范围允许保留空定义，但若仍有这些明确未完成义务，同样为`INCOMPLETE`。`semanticExhaustiveness=UNDETERMINED`独立保留，不因声明义务完成而变成“全系统语义已发现”。

ontology-sources.jsonl只保存定义引用到原证据的索引；完整原文留在已有R4/R0及私有阅读包，不另建公共证据体系。每条索引保存实际sourceIdentity/corpusIdentity、producingTaskId/reviewVersion、packetId/localRef以及冻结包中的真实U编号evidenceUnitRef和E编号entryRefs，连同原kind/originalId/entryUses形成可逆定位；只在审阅处置中引用的来源也必须进入索引。产物可导出，但需带其来源包或声明可追溯到准确保存位置；本体文件不是客户源码的替代。

局部组装不要求所有R4入口都读过。原始输入分母为正、但没有任何任务处置或已审结果时，不能声称声明义务已闭合：若没有明确未完成项，coverageStatus为UNDETERMINED；若已有必需未读等项，则仍为INCOMPLETE。已审局部任务的明确义务全部完成，可以为SCOPED/COMPLETE，并继续保留全量输入分母和semanticExhaustiveness=UNDETERMINED。零输入且零任务允许真正空范围，不为其伪造定义或来源。

若声明范围已完成处置但没有任何定义引用，sources文件允许为零条JSONL记录；其实际policy必须明确允许空JSONL，不为了满足存储而伪造来源行。coverage与review仍完整保存没有定义及未确认范围的实际处置，空文件不等于系统没有业务对象。

非空的`ontology-sources.jsonl`由正式组装器直接生成canonical JSONL：每条为canonical JSON对象，每条末尾含一个LF，最后一条也不例外，不含空白记录。空来源集合仍生成零字节。O3必须安装组装器返回的原始字节，不在安装时补换行；公共canonical store的校验规则不因本体分支而放宽。此约定只约束新的正式本体来源输出，不改写历史产物。

## 11. 版本、安装和输入边界

11.1–11.5记录现有首版v1公开合同和正式typed-v3私有协议；11.6记录已接线的稳定证据、局部失败隔离和观察v2版本化事实。它们不对v1文件补字段或猜新行为。导航、阅读包、candidate/review仍复用既有job底座，不为每层各建公共Reader/Publisher；不得借用Activity/Process格式。跨边界producer/schema/reader/store/fixture同批接通。

### 11.1 输入只接受新证据

严格按两套准确版本分派：历史R4的entry-evidence-index-v1、entry-evidence-v1和frontend-evidence-coverage-v1使用moduleKey=entry-evidence、moduleVersion=v3、payload producer=entry-evidence-v1；本轮新增R4的三个v2 schema使用moduleVersion=v4、payload producer=entry-evidence-v2。两套都必须真实安装，不把moduleVersion当producer，不缺字段猜版本。新v2用于保存独立选择回调和保存上下文；它来自frontend-http-index-v3及frontend module-v3，而不是给旧文件改版本号。来源必须PREPARED_V1。从实际header/receipt恢复同R0；可选原文路径还须在该有效排除范围内。旧M10、Packet、Activity、过程结果和模型记录即使文件可读也不是合法输入。拒绝发生在登录/Provider初始化之前。

### 11.2 新的请求及结果，不复用旧checkpoint

当前技术分支写请求v5／输出v9；仅新增本体分支写请求v6／输出v10，新增互斥RequestKind.ONTOLOGY和OntologyInputs、OntologyRunOutput。其它请求、输出及旧构造形状继续按原版本写入，不能全局升级。四意图为PREPARE_ONTOLOGY、IDENTIFY_ONTOLOGY、RELATE_ONTOLOGY、PUBLISH_ONTOLOGY。精确R4/R0、O0/O1/O2选择、scope和配置引用保存到请求，executeStep按已排队请求执行，不借upstreamRunId的旧Activity含义。

当前Task 1只在不可变record和v6/v10 registry层作形状准入：本体分支互斥、PREPARED_V1、R4的business-flows地址、操作必需引用及O0/O1/O2/O3的准确槽位/键，并拒绝重复选择；其直接合同测试同时回归旧technical v5/v9。它不以相同R4根推断entry-evidence版本或same-R0。11.1规定的实际receipt/header/payload内容及来源范围核验仍由Provider初始化前的reader/runtime重开完成。

保存仍分类到repository-knowledge；这是存储地址，不表示调用业务过程。注册四个独立模块槽位：2 ontology-corpus（O0）、3 ontology-identification（O1）、4 ontology-relations（O2）、5 ontology-publisher（O3），各producer v1；module1仍归原过程。public step publication从各自模块receipt验证，不借旧knowledgeCheckpoint。

| 边界 | 首版新合同 | 内容 |
| --- | --- | --- |
| 配置/范围/选单 | ontology-config-v1、ontology-scope-v1、ontology-selection-v1 | 明确资料、模型/容量、调查范围和准确上游选择 |
| O0 | ontology-corpus-v1；可选schema-evidence-v1 | 新R4/R0、完整单元句柄/来源、导航/倒排/早期短号；仅配置DDL时保存结构声明、实际解析覆盖及限制 |
| O1 | ontology-identification-v1 | 调查/局部已审定义、拒绝/未处理、准确任务引用和scope |
| O2 | ontology-relations-v1 | 完整已审对应关系、定义替代映射、零任务处置和scope |
| O3 | ontology-v1、ontology-coverage-v1、ontology-source-v1、ontology-review-v1 | 四文件的确定性闭合结果 |
| 私有已审任务（正式目标） | ontology-job-result-v2 | 完整EXTRACT/RELATE与REVIEW、实际输入/映射/模型身份；只在正式本体profile精确复用，不自动接收实验v1 |
| 私有决策（已实现薄合同） | ontology-decision-result-v3；survey/reading输入与响应v2；prioritize输入与响应v3 | 共同线索、按题具体选题、阅读阶段首批选择、有限查询及原始观察；不新增公共publisher |

Task 4另已在相同私有底座保存正式阅读的`ontology-decision-result-v4`和`ontology-formal-reading-state-v1`：请求绑定稳定E/U/K范围、实际技术导航/查询页、active/required/read历史、结构化未解处置及按保存O0规则冻结的v3或v4投影一并可查。私有job匹配基础保留Corpus来源身份、完整canonical短号映射摘要和可选正式配置上下文身份；实际Prompt/Schema快照参与派发与匹配，但均不作为模型可写的导航字段。状态保存实际visibleScope（含查询后展示的入口）、queryObservations及收窄标志，不只是旧selected/active摘要；无模型decision的EXPLICIT冻结、预派发单元/完整封套容量拒绝和结构化Provider失败同样按稳定问题/任务/范围/材料/来源身份保存，且未派发时visibleScope为null、readHistory为空。它只接收严格`ontology-scope-v1`/`ontology-selection-v1` Reader输出；没有在本步增加公共Reader、Publisher、CLI、Provider初始化或receipt准入。

正式私有记录按准确保存版本重开：decision-result-v4（请求作用域、已选范围/activeUnits分离）、reading-input/response-v3（稳定Corpus短键）、v1 O0的reading-packet-v3、v2 O0的reading-packet-v4、typed-candidate/review-v3（本节结构字段）、ontology-job-result-v2（完整阶段、实际Prompt/Schema及fingerprint）。v4建立独立的保存/恢复及任务身份边界：`selectedTargets`已完整保留时去除重复的首个`targetRef / argumentAssociations`便利副本；完整method source已存在时，controls/exits仅移除重复的`code / text`行字段；selected JAVA_CALL的arguments、targets和observations由同位callContext及其CT/CO映射保留。v3及其历史字节不重写或重开。它们只有相同正式profile可互用；实验typed-job-result-v1、旧文字v1和辅助PoC各自查询，不默认作为正式完成记录。Prompt配置变更不升级R4版本，不改历史产物版本号。

模型端的`ontology-model-reading-v3`与`ontology-model-reading-v4`都明确`callContextEncoding=EXACT_ATOMS_V1`：根级`callEvidence.targets`及`callEvidence.observations`为CT/CO键对象，按原子canonical字节排序编号；每位置`targetRefs / observationRefs`为有序数组，保留重复及空数组。v4以`callRef`保留显式JAVA_CALL与callContext的可逆同位关联：CALL unit保留表达式、receiver、控制索引和resolution，context保留site和actual arguments，CT/CO保留targets（包括argumentAssociations）和observations，selectedTargets保留S目标映射。完整sourceText、controls/exits的索引/范围/条件/表达式/type、候选和限制仍在已选单元或上下文中；只删除已由上述完整字段保留的`code / text`或便利副本。后续机械投影压缩必须留待其专属版本/测试，不能改写v3。CT/CO不能用作`evidenceRefs`、对象端点或本体定义。完整私有阅读包与模型实际收到的`modelInput`、明确投影版本及编码标记共同进入formal任务身份；只改变实际投影也必须改变jobKey，不能复用旧已审任务。旧v2模型投影不变。精确结构与保留项见[材料合同](material-preparation.md#6-完整单元到简洁阅读包)。

O0索引指向原R4/R0正文；具体模型阅读包和响应存在已有私有job底座，不为每个内部步骤建公共产物类型。O1/O2保存的候选/最终定义可独立重开，但必须沿准确Corpus核验引用；O3选择的定义必须全部有处置。新artifact查询键和reader只读OntologyRunOutput，不扩旧BusinessOutputArtifactKey假装完成旧过程。

历史PoC写`ontology-decision-result-v2`及`ontology-survey/prioritize/reading-input-v1`、对应响应v1；本次薄实现已写私有decision-result-v3、survey/reading-v2及prioritize-v3。旧文件不改写、不补字段；按原合同严格查询。新匹配依据变化后不复用旧选材决策，P2i只显式精确复用同v2的六份P2g调查响应。

### 11.3 封闭文件集及上游保存方式

每个操作单独run，只有自己的repository-knowledge step publication。现有AtomicAnalysisStepPublicationEngine没有该key合同，实施时必须增加**只匹配module2–5、moduleVersion=v1**的新分派；不能放宽其它步骤或旧module1。

| 操作 | 精确语义文件集合 | artifact type / schema |
| --- | --- | --- |
| O0 | ontology-corpus.json | ONTOLOGY_CORPUS / ontology-corpus-v1 |
| O0配置DDL | 上述文件＋schema-evidence.json | SCHEMA_EVIDENCE / schema-evidence-v1；仅存在真实配置及保存引用时安装 |
| O1 | ontology-identification.json | ONTOLOGY_IDENTIFICATION / ontology-identification-v1 |
| O2 | ontology-relations.json | ONTOLOGY_RELATIONS / ontology-relations-v1 |
| O3 | ontology.json、ontology-coverage.json、ontology-sources.jsonl、ontology-review.json | ONTOLOGY / ontology-v1；ONTOLOGY_COVERAGE / ontology-coverage-v1；ONTOLOGY_SOURCE_INDEX / ontology-source-v1；ONTOLOGY_REVIEW / ontology-review-v1 |
| O3可选最小视图 | 上述四个文件＋ontology.md | ONTOLOGY_VIEW / ontology-view-v1（Markdown）；其余文件合同不变 |

现有step receipt的上游槽位是固定列表，不为本体增加可变同key依赖系统。O0–O3的step receipt都固定绑定唯一准确R4（upstreamStepKeys=[business-flows]），作为共同证据根。**这不表示O1/O2/O3没有语义上游**：v6请求和每个阶段payload共同保存准确O0、O1/O2列表及selection内容；`ModuleInstallRequest.upstreamArtifacts`及`ModuleReceipt.upstreamArtifacts`绑定真实上游payload的`ArtifactReference(artifactId, sha256)`。O0绑定R4已安装目录和覆盖payload的descriptor，O1含O0，O2含所选O1，O3含所选O2与最终使用的O1；列表按底层`requireStrictReferences`要求严格排序、去重。R4的step root不是ArtifactReference，须另用准确的`AnalysisStepPublicationReference`核验，不能把两类引用混用。

安装和OntologyStageReader/PublicationReader重开都须验证：step的R4根一致；payload中的完整typed refs与实际module/step receipts及保存请求一致；selection的完整内容/摘要、同Corpus/source、scope、候选处置和模块artifact摘要闭合。缺任何一层不能以“R4相同”放行。多上游变长仅存在本体选单/payload内，用既有ArtifactReference列表绑定，不扩底层StepContract依赖语法。原文R0来自已验证R4 header；不混用旧source。

正式运行只读取输入清单一次，使用同一份canonical JSON完成解析、摘要和保存：O1的`ontology-identification.json.scope`保存完整原始`ontology-scope-v1`；O2的`ontology-relations.json.selection`及O3的`ontology-review.json.selection`保存完整原始`ontology-selection-v1`。不从简化任务列表重建原清单，不因输入文件后来移走就丢失已执行范围。阶段payload另保存`semanticUpstreams`，字段固定为`corpusPublication / identificationPublications / relationPublications`，采用公共请求中已有的完整引用结构及实际选择；无需另建清单数据库或新增公开文件。读取时重新计算snapshot的content reference，与保存请求的scope/selection引用核对，并核对这些语义上游与请求、实际module上游payload引用一致。O1/O2的`promptBundleRef`指向实际Prompt文本组成的canonical对象，不得指向scope/selection，也不得用Map的显示字符串代替canonical内容。这些内容不进入模型导航，不增加业务模型请求。

O3不默认HTML输出，也不建新视图runtime。Markdown只读取已审JSON，安装前生成、同一已选四文件内容核验。其它复杂视图另议。

O0 payload显式保存schemaEvidence状态DISABLED或指向真实ArtifactReference；不配置则精确文件集仅corpus。配置后文件可保存PARTIAL/UNSUPPORTED及原诊断，corpus/readiness不因此虚构表结构完整；需要库约束却未取得时依赖定义保留未知。schemaEvidence与corpus同run/module，引用同R0原文；O1/O2沿O0引用重开，不重新解析DDL。与两种精确文件集对应的producer/reader/store/fixture一起修改，不放松其它artifact白名单。

这里的`DISABLED`是新正式O0的显式字段，不采用“缺字段就猜已关闭”。正式O0首版仍在实施，补齐这个已规定字段会改变其临时测试产物的canonical字节和artifactId；不能同时宣称该临时payload逐字未变。DDL关闭时，既有R4内容身份、E/U/K映射和阅读包身份不因增加空schema单位而改变；历史R0/R4、实验结果及旧MD产物不改写。

### 11.4 旧代码的边界

共享registry既有分派和读者保持原状；不新增本体对旧内容的reader、转换器、兼容别名或迁移任务。对直接修改的共享接口做隔离回归即可，不将“兼容全部历史分析格式”列为本体完成条件。不自动升级旧待执行请求，不改旧结果，旧分析退役另需授权。

成功操作都新建run；来源属于R0/R4，识别/关联/发布属于各自新run。已结束run不能重新激活。同一任务仅复用完整已审且输入、Prompt、Schema、局部映射、profile、真实模型绑定匹配的结果；不复用孤立草稿或“内容看起来一样”的旧过程。

canonical安装先验证、实际保存，再登记输出及终态。没有receipt不返回正式产物引用；临时文件不是成功。新失败结果有成功项/诊断时通过本体typed查询读取，不能以它声明全范围完成。历史Activity、过程、R4和more-findings.md保持不变。

### 11.5 对用户和Agent公开的观察合同

除最终四文件，inspect/artifact必须可取到：准确选择清单与来源、导航/查询观察、实际原文包及移出范围、Prompt/Schema、原始候选和结构校验、实际REVIEW/修正、短号可逆映射、调用/容量/失败和未读范围。敏感源码/日志按现有受保护访问策略，不为透明度把凭据或Provider私有错误公开成业务正文。

CLI结果按[沟通合同](workflow-and-prompts.md#9-agent沟通可观察可移植不是黑盒)区分执行终态、实际保存、框架检查、模型结果和coverage；用户改沟通Prompt不能改真实计数/状态。没有实际receipt不返回正式引用。不同层的PARTIAL/COMPLETE不合并成一个整体成功布尔值。

现有正式本体操作和`inspect`的JSON观察保留`runId / operation / lifecycleState / resultStatus / canContinue / availableArtifactKeys / problems / modelRequestsDispatched`。字段从真实记录取得；没有公共output时resultStatus为null、公共键为空，不能假造publication。现有problems仅为`code / taskId / stage`且runtime只保留一个主要问题，不能表达多个任务隔离后的完整结果。**现有modelRequestsDispatched在lazy Provider预检前增加，预检失败时不是已确认产品请求开始数**；保留历史实际值，不改写为另一口径。新版观察的逐任务问题和预留/开始/结束计数由11.6规定；不能由任务数推算。canContinue只表示声明范围满足准入，不是授权或语义正确。O0/O3真实无派发时为0。

operation采用保存请求的`PREPARE_ONTOLOGY / IDENTIFY_ONTOLOGY / RELATE_ONTOLOGY / PUBLISH_ONTOLOGY`枚举；lifecycleState采用真实`QUEUED / RUNNING / FINISHED / FAILED`；resultStatus采用真实公共输出的`COMPLETED / PARTIAL / BLOCKED`或null，不新建同义状态。availableArtifactKeys为已安装大写公开键的稳定排序数组；私有任务记录不冒充公开产物。没有公共输出、已知阻断或必需范围未完成时canContinue为false。运行命令输出和其后的inspect应报告同一已保存事实。

`availableArtifactKeys`只列本次运行实际安装的键，不要求列齐查询表中的所有可能键；`SCHEMA_EVIDENCE`还要求实际配置并安装DDL材料。`modelRequestsDispatched`只统计该运行，Provider实例创建次数不是派发次数；同一任务的提取和审阅可以使用同一个已绑定Provider。没有派发许可时不得为新任务初始化Provider。

查询接口沿用现有`source-analysis --config <ontology.yaml> inspect --run <准确运行ID>`及`artifact --run <准确运行ID> --key <闭合键> --max-bytes <正整数>`。本体查询键采用现有技术CLI的大写枚举形式；不接收任意文件路径，不增加文件名或小写别名：

| 查询键 | 所属操作及准确保存文件 |
| --- | --- |
| ONTOLOGY_CORPUS | O0 / ontology-corpus.json |
| SCHEMA_EVIDENCE | 配置并实际保存DDL的O0 / schema-evidence.json；未配置返回明确不存在 |
| ONTOLOGY_IDENTIFICATION | O1 / ontology-identification.json |
| ONTOLOGY_RELATIONS | O2 / ontology-relations.json |
| ONTOLOGY | O3 / ontology.json |
| ONTOLOGY_COVERAGE | O3 / ontology-coverage.json |
| ONTOLOGY_SOURCE_INDEX | O3 / ontology-sources.jsonl |
| ONTOLOGY_REVIEW | O3 / ontology-review.json |

私有观察通过另一个闭合键`ONTOLOGY_TASK_RECORD`及必需的`--task-id <准确已保存producing-task-id>`读取。先核对该任务确实属于请求中的运行，再读取既有私有job记录；不把任意字符串转换成宿主文件路径，不把Provider私有失败内容直接公开。尚未形成正式产物的失败任务，可按该运行实际登记的任务记录查询，不能伪造ontology文件或receipt。查询初始化只读取存储和策略设置及已保存请求，不要求当前Prompt文件、Maven输出、客户JDK、源码工作目录或模型登录仍存在。这些公开和私有查询键已接通并经过直接存储/CLI测试；最终当前代码质量和三个真实样例另行验收，不以接口接通宣称模型质量通过。

公开阶段安装失败时，不能要求Agent直接列私有目录才能知道任务编号。新增同一`artifact`入口的闭合只读键`ONTOLOGY_TASK_INDEX`，不带`--task-id`，使用准确运行ID和正整数`--max-bytes`。只接受O1/O2；根据既有私有job中本运行的实际已审/失败终态记录逐项核对formal成员，返回`ontology-task-index-v1`：根级`schemaVersion / runId / taskRecords`，每行固定`taskId / producingTaskId / jobKey / taskKind / status`。status只采用核验后的`REVIEWED / REJECTED`，按完整producingTaskId稳定排序；冲突、错run、错schema或损坏成员明确拒绝。它不列尚未准备或尚无终态的清单任务，不声明全范围完成，也不返回正文、Provider日志或任意私有路径。实际没有终态成员时返回空数组，不能生成占位任务。

`ONTOLOGY_TASK_INDEX`与`ONTOLOGY_TASK_RECORD`是受保护记录的查询投影，不是新公共publication或数据库，不进入`availableArtifactKeys`的已安装公开文件列表。即使公共output缺失，准确运行、保存请求、策略和成员校验通过后仍可查询；据返回的真实producingTaskId再取TASK_RECORD。不得为这两种查询启动Provider、重做请求、伪造ontologyPublication或放宽原访问校验。

O1/O2的阶段payload在根级`taskRecords[]`保存**实际已准备任务**的查询成员关系，每项固定为`taskId / producingTaskId / jobKey / taskKind / status`。taskId是输入清单的具名标签；producingTaskId是实际formal job生成的完整身份，不能用裸taskId替代；jobKey只能由实际不可变准备结果取得。REVIEWED/REJECTED表示实际终态，不能把尚未派发或未审阅的任务标为REVIEWED。未进入准备的任务保留独立未处理处置，不虚构这一成员行。初始化前预检和准备本身不算模型已收到请求。

任务成员关系在真实派发前存入既有受保护私有记录；运行结束后安装的阶段payload收录同一真实成员及终态，不能为更新状态重写已经安装的module。保存REVIEW失败的阶段报告后，用户从该payload的`taskRecords[].producingTaskId`选择准确任务查询；程序再次核对run及成员映射，才读取对应私有记录。没有成功安装阶段报告时只能报告实际可查询的私有记录，不能返回虚构的公共artifact。该成员合同已在正式runtime及失败安装后私有查询的直接测试中接通；不是历史PoC迁移或新任务数据库，也不是客户模型质量验收。

额度/授权耗尽禁止新增模型派发；`canContinue=false`本身只说明原范围不完整，不应被解释为所有独立任务永久不能执行。新v2 O1/O2已对确认结束的局部坏响应继续独立任务，对依赖和共享故障停止准确范围；直接测试依据11.6和[隔离设计](evidence-stability-and-failure-isolation.md)判断准确依赖及共享故障。只读查询和准确选单下的零模型部分发布允许保留已审部分，但必须继承全部失败/未处理义务、输出INCOMPLETE；不能改空非空关系范围绕过失败，不清除来源/存储阻断。已有零问题O2测试只核验其明确空清单。

该私有查询的结果为`ontology-task-observation-v1`聚合观察：根级`runId / taskId / producingTaskId / jobKey / taskKind / status`来自已核验成员；`extract`和`review`各保存实际`request / response / validation / outcome`记录，没有对应记录时为null；根级`completion / failure`为实际终态记录或null。不将“提取有效”升级为“已经审阅”。例如VALID_CANDIDATE只表示实际提取校验，REVIEW失败仍为失败。查询只读现有阶段记录，不重做请求。Provider失败中的私有原始输出不出现在这个聚合结果；outcome仅保留结构化原因和开始/结束状态，并以`privateFailureOutputStored`如实说明是否另有受保护原始输出。正常模型的实际原稿/审阅响应仍按其真实保存记录返回，不把凭据、失败日志或中文异常消息用作本体事实或控制流。

模型配置只要求本体路由（survey、extract、relate）及共享Provider绑定/上限；REVIEW沿用其提取/关联任务绑定，不缺省另开模型。不要求activity/processGroup/report等旧phase。复用BoundedModelJobExecutor和PrivateModelJobResultStore通用不可变保存；本体reader按自己的准确合同/指纹核验，不调用readCompletedProcess/Activity。

### 11.6 稳定证据与单任务隔离（已接线，限定验收中）

行为owner为[evidence-stability-and-failure-isolation](evidence-stability-and-failure-isolation.md)。以下v2资源、Schema与runtime已同步接线；最终20项直接回归、静态质量6695及三个局部样例的实际证据核验已通过。九任务八已审一关系拒绝、O3覆盖INCOMPLETE，模型语义质量单列。本体证据、candidate/review-v3和严格最终引用校验不变；旧MD、其它请求/输出分派不升级。

**策略owner。** 新运行仅安装当前`ontologyPolicyRegistry`的策略；重开保存的O0–O3、选择其上游、读取artifact或观察时，按保存请求的`artifactPolicyRegistryRef`在当前路径加`storage.upstreamArtifactPolicyRegistries`的显式路径列表中逐项计算完整身份，且必须恰好一份匹配。不得扫描目录、按文件名回退、用当前目标运行策略反推旧记录，或改写历史registry字节。缺失或重复owner均为策略不匹配并拒绝；旧schema/依赖规则仍按保存记录准确读取。

**选单与依赖。** O1继续采用scope-v1字段，ACTION/ANALYTIC的前置为同题排在它之前的全部OBJECT任务；规则版本单独进入任务匹配，不把其它题累计结果加入目录。O2/O3新执行采用`ontology-selection-v2`。根级字段沿用v1对应operation字段；RELATE问题在原字段上增加必需非空`objectSources`，每项仅`identificationRun / questionId`，准确run须在根级identificationRuns中，同一来源对不得重复。QUESTION的实际问题范围来自保存scope.questions；DISCOVERY来自保存selectedQuestions及其taskOutcomes，不能读取原始空scope.questions或从activeUnits反推。缺实际范围即依赖不可恢复。Java恢复准确OBJECT任务并要求全部REVIEWED；首次查找用`(identificationRun, producingTaskId)`，而保存O2目录重开必须同时匹配完整`(corpusIdentity, producingTaskId, reviewVersion)`及该O2原selection的准确来源owner范围。相同producingTaskId但不同owner/reviewVersion不可互相覆盖或任取；同完整身份且相同保存结果可作为同一正式输入复用。其它ACTION/ANALYTIC失败保留覆盖但不自动阻断RELATE。来源对及真实对象任务/定义身份参与冻结/指纹，模型不能填写长身份替代。PUBLISH的v2字段形状保持v1的corpusRun/identificationRuns/relationRuns，继承全部所选上游义务，不按成功列表缩小分母。v1历史严格读取，不猜依赖升级。

**新执行与历史选单。** 新策略写relations-v2/v3时，非空RELATE范围必须使用selection-v2；selection-v1在Provider初始化前以`ONTOLOGY_SELECTION_VERSION_INVALID`拒绝，不能自动猜出对象来源和新版依赖。旧策略下selection-v1的执行和历史查询保持原行为。兼容查询流程若明确声明零关系问题，可保留原selection-v1快照并写真实空`taskOutcomes`；这不表示任何非空关系范围已经完成，也不转换历史文件。

**业务联系阶段的实际选材。** 新`ontology-relations-v3`另含`readingSelections`数组，每个声明问题恰一条，字段为`questionId / taskId / status / issueCode / selectedEntries / selectedClues`。status为`NOT_STARTED / READY / INCOMPLETE / UNRESOLVED / FAILED`，issueCode为真实阅读原因或空字符串；E/K必须属于准确Corpus，不重复。初始selection按请求内容保持原字节身份，不能改成模型最后选择；实际阅读选择单独保存。MODEL通过同一正式阅读协议传入真实RELATE种类，阅读与提取/审阅共用运行预算。未准备型任务没有虚构typed job，但其实际选择仍可保存；O3按实际选择保留未完成K分母。旧relations-v1/v2不补该字段，按原选择和保存规则读取。

重开时，EXPLICIT与NOT_STARTED的实际E/K必须逐项等于原声明清单；不能加入Corpus中存在却未被该题选择的线索。MODEL允许通过已保存的阅读过程扩大/调整选择，不能仅与初始空清单比较。已审RELATE结果的`selectedClues`必须与该任务冻结身份中的`visibleClueRefs`准确相等，状态必须READY；O3不接受另外一份题的选单替代它。未形成typed任务的失败选单仅是受安装receipt保护的实际阅读观察/覆盖分母，不因此成为已审关系证据。

**阶段输出。** O1采用`ontology-identification-v2`，O2采用`ontology-relations-v2`，相应模块producer为v2；既有来源、语义上游、scope/selection、已审定义及taskRecords保留，新根级字段为`taskOutcomes`。它列完整实际声明/选中任务而非仅实际job，按声明顺序保存：

| taskOutcomes字段 | 类型/含义 |
| --- | --- |
| questionId / taskId / taskKind | 准确范围标签及OBJECT/ACTION/ANALYTIC/RELATE，不拿producingTaskId替代taskId |
| status | REVIEWED、REJECTED、UNPROCESSED，不能由缺字段猜REVIEWED |
| producingTaskId / jobKey | 实际prepare成功者的身份；未准备为null，不生成占位成员 |
| dependencyTaskRefs | 每项`runId / questionId / taskId`，引用实际声明前置；无依赖为空数组 |
| reason | REVIEWED时null；其它为下表的结构化原因，不以中文message控制流程 |

reason固定字段为`code / category / stage / jsonPointer / offendingRef / expectedRefs / dependencyTaskRefs`。stage/结构定位/非法引用未知为null；expectedRefs为当前错误位置的小型合法范围或空数组，不复制整个对象目录；依赖列表与任务前置可核对。category闭合为`MODEL_OUTPUT / MATERIAL / DEPENDENCY / SOURCE / CONFIGURATION / PROVIDER / STORAGE / DISPATCH_LIMIT / ASSEMBLY / UNKNOWN`。已知边界由实际生产者提供原因；真正无法分类的内部故障使用UNKNOWN并保守停止受影响范围，不能把普通RuntimeException一律写成ASSEMBLY。共享run的实际派发额度在provider委派之前耗尽时，producer精确给出`ONTOLOGY_MODEL_BUDGET_EXHAUSTED / DISPATCH_LIMIT / stage`：已prepare的O1/O2 formal任务为REJECTED，尚未prepare的MODEL阅读或discovery范围为UNPROCESSED，并且不增加reserved/started/ended/unknown计数或伪造远端观察。该当前运行的typed failure也以原taskId/category/stage写入私有runtime观察；即使历史payload没有taskOutcomes，inspect仍据该保存事实报告`DISPATCH_LIMIT`，而旧runtime记录缺category才按UNKNOWN读取。code以实际接线后的结构化原因定义为准，设计中的`DEPENDENCY_NOT_REVIEWED / REQUIRED_UNREAD`等不得用中文消息匹配产生。依赖阻断status为UNPROCESSED，MODEL_OUTPUT最终拒绝为REJECTED；共享故障未派发的任务为UNPROCESSED并指向同一准确故障。没有坏模型响应时不伪造MODEL_OUTPUT诊断。该项属于本轮v2增量，不对历史记录补字段或重新分类。

taskRecords继续只登记实际formal job。taskOutcomes、readingCoverage、结果来源按producingTaskId/jobKey关联，不使用prepared/completed数组下标；没有job时以准确范围标签保存处置。错误发生在某任务的材料准备之前，也必须出现在完整处置中。存在REJECTED、UNPROCESSED或必需未读则原范围INCOMPLETE。

**发布。** Publisher producer v2写`ontology-coverage-v2 / ontology-review-v2`；增加准确taskOutcomes和scope保留依据，ontology-v1/source-v1字段不变时不升级。全体所选O1/O2义务及成功/失败被继承，REVIEWED完整定义才入本体；不同reviewVersion的正式定义分别进入纯组装，相同完整身份只有在question/kind/status、冻结packet canonical/model字节与format、raw candidate/review/catalog字节、diagnostics和两段runtime identity全部相等时才可机械去重。断引用或未解决组装冲突仍阻止该次正式安装，不自动删定义。O1/O2 producer v1和旧coverage/review-v1按原版本查看，不把旧单值problem推成新版完整隔离记录。

O1/O2阶段的taskOutcomes归属由该阶段的根级运行明确，保留上表字段。O3覆盖及审阅v2继承多个阶段时，每条taskOutcomes在这些字段之外增加必需`runId`，值为该处置的原O1或O2运行，不是O3运行；唯一键为`runId / questionId / taskId`。不同运行的同名局部任务分别保留；未准备任务仍无producingTaskId/jobKey，不靠它们区分来源。程序从已核验的阶段记录附加owner，不改写上游字节，也不使用数组位置或另一个平行owner数组关联。历史v1阶段若没有完整taskOutcomes，只能保留其真实旧处置，不补造新版完整检查结果。

v2 O3的identificationRuns必须包含每个所选O2原selection/保存请求中的准确O1引用集合，允许额外显式选择独立O1。每个O2重开时按自身原请求/receipt核对完整准确集合，再验证其为O3集合的子集；不要求各O2列表等于O3全表，不自动补缺少的O1。所有O1同Corpus并保留完整义务，不能仅按R4相同放行。新v2 selectedRelationStage已实现并通过明确并集可发布、遗漏实际上游拒绝的直接测试；v1历史仍按原合同读取。这是明确上游规则，不是取消来源检查或新增依赖引擎。

**任务查询。** 新`ontology-task-index-v2`保留原真实taskRecords并增加taskOutcomes；未准备任务没有TASK_RECORD、不声称模型已收到材料。新`ontology-task-observation-v2`只为真实job提供现有完整阶段观察及具体原因；它不能创建缺失的response。索引仍是既有受保护记录的只读投影，不新增第五个公开文件、任务数据库或任意文件查询。

**CLI/inspect。** 新`ontology-operation-observation-v2`在现有公开观察上增加`schemaVersion / taskOutcomes / nextActions / modelRequestCounts`，problems按准确任务或共享故障完整列出，不只留最后一个问题。每个已声明非REVIEWED taskOutcome输出其已核验的结构化reason；runtime摘要只有在当前run、task、code和stage没有相同任务原因时才另列。问题公开`code / category / taskId / stage`和有界结构定位，不公开Provider私有输出；其内部去重按完整`runId / questionId / taskId`任务身份，而不把相同本地标签的不同继承任务合并。O0在队列后发生的R2/R0补读失败写现有私有runtime observation的实际`SOURCE/PREPARE`原因及零请求计数，公开观察因此可说明失败但不制造Corpus receipt、task或Provider调用。canContinue仍表示本次声明范围完整；nextActions另表达实际支持的局部后续。`SOURCE`或`STORAGE`完整性失败不得广告`PUBLISH_REVIEWED_PART`。

nextActions每项固定`kind / taskRefs / upstreamRunRefs / requiresNewRun`；kind仅为`QUERY_TASK / QUERY_EVIDENCE / PUBLISH_REVIEWED_PART / START_NEW_RUN / PROVIDE_INPUT`。taskRefs采用上述准确范围引用，upstreamRunRefs为真正可查询/复用的运行集合；查阅不需新run，生成/修改后执行需新run。它们只是程序支持的动作，不是授权、完整业务承诺或任意shell命令。Agent另核对用户已有授权；Java不建设审批系统。实际功能未接通的动作不得出现在结果中。

modelRequestCounts字段为`reservedAttempts / confirmedStarted / confirmedEnded / outcomeUnknown`，没有可靠记录为null，不混算。reservedAttempts用于既定上限，不能据它宣称已向Provider发送；confirmedStarted来自实际开始记录，confirmedEnded来自实际完成/确认终止，outcomeUnknown来自未确认结束。预检失败不算confirmedStarted；历史modelRequestsDispatched保留原口径并在新观察中标明legacy，不与新值相加。

若v2 O3在安装前保存的纯组装`assemblyIssues`拒绝正式发布，关闭的`ONTOLOGY_ASSEMBLY_DIAGNOSTIC`查询只返回该运行、选择/范围provenance及有界问题；它不返回ontology定义、coverage/review/source-index、正式receipt或私有原始记录。

目前RunJournal在调用Provider委托前记录STARTED，不得仅据它证明适配器实际启动。确认计数使用真实响应/结构化失败或等价适配观察，口径为本地适配调用，不足则null；不能把本地进程退出产生的requestEnded=true当远端服务终止证明。阶段观察另保存`localProcessState=NOT_STARTED/EXITED/UNCONFIRMED/NOT_APPLICABLE`与`remoteOutcome=OBSERVED/UNKNOWN/NOT_STARTED`；不适用本地进程的SDK采用NOT_APPLICABLE，仍须披露远端结果。请求结果未知即停对应绑定，不建设取消证明系统。正常COMPLETED登记FINISHED，PARTIAL/BLOCKED登记FAILED且保留可查output，不能等待用户而保持RUNNING。

正常CLI结束必须把保存事实登记到终态，不等待用户修改Prompt。若O1/O2已保存局部任务失败、随后canonical公共安装本身失败，任务原稿、reason和成员记录仍按其实际终态保留可查；但运行级最终观察必须以artifact store的实际稳定code记录`STORAGE / INSTALL`、空taskId和无receipt，不能用较早的MODEL_OUTPUT等任务问题覆盖它。共享保存失败可没有公共output，但仍报告实际私有可查内容，不能虚构receipt。进程外部强制中断只能报告最后真实记录，不承诺一定有最终报告。细节及Provider整次deadline由[隔离设计§8](evidence-stability-and-failure-isolation.md#8-进程有界结束不是只捕获一次异常)规定。

这些新版本已同步producer、Reader、Schema、artifact policy、runtime、安装与直接fixture；定向GREEN、最终质量和三个局部样例的有限结束/材料/查询均已核验，实现已提交[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)。不将结构或材料通过称作所有业务定义正确。允许已有公共request-v6/output-v10绑定新payload的精确引用，不借此整体改旧版本；若字段形状实际新增到持久wire，则只升级本体分支，必须先更新该明确合同。旧待执行请求不静默套用新规则。

### 11.7 业务骨架优先、外部对象依赖与材料v5（正式接线，真实验收待完成）

行为owner为[业务联系优先详细设计](business-link-first-design.md)。本节合同已同步ScopeReader、typed validator/runner、材料、producer/Reader/policy、正式运行和发布查询，scripted直接测试已有结果，真实局部验收尚未开始。准确完成边界见[正式验收状态](../../supplements/ontology-business-link-formal-acceptance-20261005.md)。11.6保留历史规则和验收事实。不得仅通过Prompt新增字段。

**Scope。** 新路径写`ontology-scope-v2`：在v1根字段上增加必需`purpose`，只允许`SKELETON / ENRICHMENT`；每个Question增加必需数组`objectSources`，每项仅`identificationRun / questionId`。SKELETON只允许OBJECT任务，所有objectSources为空。ENRICHMENT允许OBJECT/ACTION/ANALYTIC；后两类依赖本题此前全部OBJECT和显式外部对象来源，至少具备一种。每个外部来源对唯一，指向已结束的准确O1，不能自引用；同Corpus、原请求/receipt/完整review身份均须核对，恢复该题全部实际OBJECT且要求全部REVIEWED。来源题无OBJECT或任一OBJECT拒绝/未处理即DEPENDENCY失败、零派发，其它ACTION/ANALYTIC失败不自动否定已审OBJECT。QUESTION读scope.questions，DISCOVERY读保存selectedQuestions，不从正文范围反推。

ScopeReader现按版本严格读取上述字段形状、任务类型和本题/外部来源至少一项的静态约束；跨运行的完成状态、同Corpus与完整review身份只能在runtime依赖解析时核验，不能视作Reader已证明。scope-v2直接测试8项已通过；旧v1仍按旧字段闭合读取。

实际依赖定义仍用完整`corpusIdentity / producingTaskId / reviewVersion`及owner定位，私有目录重映射为本任务B键；不能直接拼不同运行的B1。不增加裸对象名选单或任务依赖DSL。同源但不是同Corpus也不放行；外部对象目录和实际Prompt/材料一起参与新任务匹配。scope-v1历史原样读，不自动变成SKELETON。

**阶段和发布上游。** O1写identification-v3/producer-v3，保存实际scope-v2、外部对象来源及全部taskOutcomes。O2继续采用现有selection-v2形状，其阶段升级relations-v3/producer-v3承载新typed结果及线索处置。O3保留明确selection集合；不仅包含每个O2的原O1来源，也必须包含所选细化O1实际引用的所有外部O1。沿这些已经声明的准确引用做有限闭包核验；缺上游拒绝，循环/自引用拒绝，不自动补入或重算。不同题同名对象不合并，只有完整已审SAME_OBJECT及无冲突的结构映射可统一。

**Corpus与材料。** O0写ontology-corpus-v2/producer-v2，明确projectionRuleVersion=ontology-model-projection-v3；K扩展CONTROL_REFERENCE，B保留已审对象目录用途。冻结包/model-reading-v5采用EXACT_ROWS_WITH_USES_V1：callRows为精确非用途共同记录，callUses保留入口、调用者、同入口目标及原顺序；固定site列顺序、CT/CO及完整逐位置限制按[材料§9.3](material-preparation.md)维护。候选或状态不同不合并，解码须与当前已选投影逐字段一致。新增前端单元→上下文反查使用实际物理身份，不重新解析源码。私有decision-result-v5/formal-reading-state-v2与reading-input/response-v4保存新编码、成本及选中K处置，旧协议不暗改。

业务阅读input-v4包含配置实际maxUnitBytes/maxRequestBytes；动作及查询数量在每次复制的Schema中分别绑定maxActionsPerRound与maxNavigationEntries。保存和身份比较使用这份实际Schema，不仅保存无上限的模板。历史input-v3及原Schema仍按原合同读取；私有完整包字节不当成模型输入成本，也不由模型自行断言Java已经拒绝容量。

新业务producer-v3执行O1/O2/O3只接受保存projection-v3的Corpus-v2，不能因配置列出旧owner策略就把旧Corpus带入新Prompt/类型化协议。版本不符在Provider前返回ONTOLOGY_CORPUS_VERSION_INVALID；查询历史及原v1/v2生产路径不升级。新的phase reading限额来自本阶段明确配置，O0保存控制继续按原值核验，不用新配置改写上游身份。

**Typed与线索处置。** candidate/review-v4沿用v3业务定义，加Object.displayRole，枚举`MAIN / SUPPORT / TECHNICAL_OR_UNKNOWN`；由模型审阅，不由Java据类名默认MAIN。新增根级`clueDispositions`数组，RELATE项固定`clueRef / outcome / linkRefs / reason`，K须属于本任务实际展示范围；outcome枚举`LINK_SUPPORTED / NOT_A_BUSINESS_LINK / NEEDS_MORE_MATERIAL`。LINK_SUPPORTED的linkRefs非空且引用本次返回Link，其它两类为空且reason非空。非RELATE数组为空。缺少处置的已选K程序标MODEL_NOT_ADDRESSED；未派发者按真实失败原因记录。K不是evidenceRef，业务依据仍只用本包实际S。未知K、悬空Link和角色枚举错误与其它坏引用一样进入一次REVIEW/最终严格拒绝，Java不补语义结果。

typed-v4校验入口显式接收实际可见`visibleClueRefs`，只在`ontology-model-reading-v5`的formal任务中选择；旧v3方法、构造和保存格式不升级。新任务结果使用`ontology-formal-typed-job-result-v3`并准确保存可见K，旧结果仍按v2读取。新Prompt资源为独立v2文件，只有策略内容精确声明identification-v3时才可在请求创建前选用，显式prompt覆写仍优先；这一步的runtime调用不由配置类自行猜策略路径。

**覆盖和视图。** ontology-v2保存displayRole，Publisher producer-v3写coverage-v3/review-v3，source-v1形状不变。coverage-v3增加`recognitionLayers[]`，每项`layer / status / taskRefs`；layer仅OBJECT/RELATION/ACTION/ANALYTIC，taskRefs使用原owner的runId/questionId/taskId。未请求层为NOT_REQUESTED且taskRefs为空；请求层按全部实际声明义务标COMPLETE_FOR_DECLARED_SCOPE或INCOMPLETE，不以成功任务列表缩小分母。局部骨架闭合不是全仓本体COMPLETE。视图只渲染正式四文件，实线表示本次模型审阅的CONFIRMED，不表示机器证明；未知可见、支撑对象可分层，过滤视图不能删除原目录。

纯组装已新增`assembleFormalV2(BusinessFormalInput)`，由调用者显式提供每个owner/question/task的种类、实际选中K、保存任务处置及失败原因；旧`assembleFormal(FormalInput)`仍产旧v1四文件。新路径只在已审RELATE的实际K上汇总模型返回处置，漏答标`MODEL_NOT_ADDRESSED`；未执行K用实际任务状态/失败code，OBJECT等任务即使看过K也不产关系处置。`coverage-v3`新增`clueDispositions[]`，每项`runId / questionId / taskId / clueRef / outcome / linkRefs / reason`；LINK_SUPPORTED的本地L映射为该已审任务的全局Link，不能跨任务拼接或从K本身生成边。`review-v3.taskResults[].clueDispositions`保留相应已审任务的归属；四文件确定性字节的发布、policy及离线视图接线由其各自生产边界验证。

独立`OntologyBusinessOverviewRenderer`只消费上述四文件，严格要求ontology-v2/coverage-v3/review-v3及source-v1行；MAIN为主图层，SUPPORT、TECHNICAL_OR_UNKNOWN和孤立对象仍列出。只有已审CONFIRMED且无该边已知组装冲突/unknowns的联系画实线，其余虚线，箭头按已审端点与全部mechanism描述，不从K推边；各mechanism完整结构另列于详情。图标签用Mermaid quoted label的十进制实体逐码点保留标点、比较条件和任意长度，不截断或改写事实；HTML列定义、实际sourceRef/短号及coverage/review限制并转义不可信文本。Mermaid 11.12.0从framework本地资源以base64脚本嵌入，strict安全级别、禁HTML标签、无CDN。它是四文件确定性投影，不是第五份公开真相或用户源代码读取；断网浏览器效果仍须另验。

配置保持ontology-config-v1；公共本体request-v6/output-v10继续绑定准确版本化引用，不改变旧MD/技术分支及其请求指纹。生产者、Schema、读取器、policy、私有任务身份、查询及fixture须同批更新；未知版本拒绝，不用字段存在猜新旧。完整版本目标见[版本表](business-link-first-design.md#14-合同及版本修订)。本文写完不是机器合同已实现；小窗口与跨项目质量仍需具名验收。

新IDENTIFY请求的`identificationPublications`保存scope-v2实际外部OBJECT来源的精确模块引用；历史IDENTIFY该数组仍为空，旧序列化和指纹不增加字段。新O1安装的上游是准确O0加实际外部O1，阶段正文的semanticUpstreams与保存请求一致。重开按原owner、Corpus、问题、任务和审阅版本恢复目录；只对声明的外部对象来源开放，不能用待检查结果反推预期来源。PUBLISH必须显式包含细化O1所依赖的全部O1，不能由程序静默补入。scripted正式运行测试已核对这些来源和缺失上游拒绝；不将工程验证称作客户模型细化成功。

新`ontology-artifact-policy-set-v3.json`同时列出必要历史版本及准确新版本，按现有Registry的type/schema严格排序。生产者版本与payload家族对应检查在模块、步骤两层执行；当前策略不能改写旧产物保存的策略身份。操作观察读取明确v2/v3的taskOutcomes；未识别版本不默认解释为完整成功。

真实任务查询继续使用该运行`taskRecords[].producingTaskId`，不是scope里的局部taskId。任务观察对准确的typed-job-result-v2/v3均报告已审终态，保留原候选和审阅字节；新版已审结果不能被误报成PREPARED。外部OBJECT细化O1只保存本次任务及准确外部依赖，不在私有阶段凭空组装一份缺少对象端点的本体；最终O3须明确选择这些对象的真实O1后才能组装。

新执行的Corpus生产版本准入由已有`OntologySavedTaskContract`校验，运行器只提交明确的生产者/Corpus家族判定；不改变历史查询或新旧任务分派。此职责拆分不改变拒绝码，也不提高静态复杂度阈值。

## 12. 程序能够和不能够检查什么

能够：Schema/类型/引用存在、模型allowlist、来源/排除版本、实际输入身份、候选/入口处置不遗漏、已审映射引用不悬空、容量/保存/稳定排序。

不能：确认两个业务名称一定同义、证明所有代码分支正确、判定中文是否被代码蕴含、证明真实库存或付款、验证组织是否接受某KPI。后者由原文模型审阅及具体人审验收承担，不增加自研解释器或语义证明系统。
