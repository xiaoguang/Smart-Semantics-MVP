# Source Code Analysis Agent：技术证据与仓库语义

## 1. 目标与当前状态

从明确固定的源码取得技术证据，再由用户选择语义分析分支。当前技术出口是按后端入口组织的 R4 JSON；本次设计的语义出口是 `ontology.json`，识别对象、身份、属性、跨入口联系、操作/规则、维度和指标。旧 Activity→业务过程分支仍存在，其出口是 `business-processes.md`，但不是新本体的上游。材料完整性、业务覆盖和语义质量分别判断。

历史后端 Packet 批次有325包、326入口处置和1个导航失败，见[当时取材交付](supplements/jdt-persistence-reading-materials-delivery.md)；在该格式上曾生成418条Activity，旧326条保持原样。这些数字不是当前R4证据规模，也不表示全仓业务质量通过。当前技术事实是四命令生成的339份入口证据及51条前端请求处置，见第2节。

当前收尾详细设计见[端到端业务交付](end-to-end-business-delivery-design.md)。范围完成性、历史结果接续、同入口切片关系、局部引用及 WRITE 职责已有实现和直接测试；真实端到端业务过程仍须按“先样例、用户确认、再全仓”的边界验收，不能用程序通过代替业务质量通过。

Step07系统认识、聚焦选材、一次候选阅读检查、DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存与producer v4发布已经实现。历史三例只有12个实际请求：1 SELECT、3 CHECK、3 DRAFT、3 WRITE、2 RULE_REVIEW。采购最终核对因容量未发送；销售最终响应有7个未定义用法引用及业务范围错误；调拨合法保存但仍有两处条件限定错误。用户认可可读性，不表示三例或全仓质量通过，见[实测记录](supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

Step08只保留历史reader/query/renderer，本轮不设计或恢复新九章生成器。八个固定步骤、semantic key、运行目录与唯一公共RepositoryAnalysisAgent方法集合不变。

新证据的[本体识别](modules/ontology-recognition/README.md)是本总体设计的独立分支。用户已明确：**新分支只对接新R4证据及其同源R0，不兼容旧分析输入/结果**。不读取M10、旧Packet、326/418条Activity、过程目录、旧模型稿件或九章，不设计迁移、双写和自动转换。公共Agent、运行登记、canonical存储和Provider可复用；历史内容不删除。2026-10-02辅助两联系PoC已验证：薄Java零模型准备27个完整单元，4次Luna/high请求得到有限对象/关系/操作与两金额计算；选择清单人工，维度为空，部分前端材料为实验补充，不能宣称自主选材或正式R4全链通过。正式四CLI、typed运行、publication及查询已接通并通过限定工程测试；最终静态质量已通过，三个真实模型样例尚待验收，实际派发0。当前详细目标见[材料组织](modules/ontology-recognition/material-preparation.md)、[透明工作流](modules/ontology-recognition/workflow-and-prompts.md)和[实现差距](modules/ontology-recognition/implementation-delta.md)；旧实验门禁状态见[实施状态](plans/ontology-recognition-implementation-plan.md)。

源码接入见[源码准备详细设计](analysis-steps/01-verified-source-inventory.md)及[实施计划](plans/source-preparation-implementation-plan.md)：普通目录/固定Git逐项读取、结构化问题、四文件发布与严格重开、具名刷新/排除、新准备路径移除行索引、独立 CLI 和 Skill 已实现并通过直接测试。准备版公共源码 reader 只暴露有效范围；真实发布/重开fixture证明排除的Java/XML不进入`VerifiedJavaProject`、`PersistenceAnalysisRequest`或`MapperXmlResourceView`。新执行先核对配置绑定、保存的 request/output 与重新打开的来源；历史 Step05/M10 材料的实际 LEGACY 来源由其引用的 Step01 publication 只读元数据投影，不为来源检查读取源码 blob。M10重开还核对receipt上游引用与state业务流publication的实际payload引用。选择 `PREPARED_SOURCE` 时，旧 Step05/M10 材料因缺准备版来源及有效排除的材料级证明而拒绝复用。本轮最终直接相关测试168/168、质量BUILD SUCCESS（Spotless 630 files clean，SpotBugs/PMD均0）；资源上限停止枚举后的恢复仅允许NEW全范围，3项新增测试通过，计划按本轮范围收口。以上源码准备计划当时未运行客户JDT或产品模型。其后技术计划已从准备版来源保存新技术结果，见下段；跨版本增量复用仍为backlog。

## 2. 文档权威与内部层次

前五步技术设计的权威入口为[技术分析详细设计](modules/technical-analysis/README.md)。四个独立操作collect-frontend、collect-code、analyze-persistence、assemble-materials已经实现并完成固定源码技术交付；最终R4有339份入口JSON及51条前端请求处置。已知调用归属、外部边界、前端必要单元和SQL排序经过定向验证；运行时地址、全量逐边语义和长等待根因仍有明确限制。旧47包和当时7个失败是历史三命令状态，不覆盖新结果。详见[最终验收](supplements/technical-entry-evidence-acceptance-20260929.md)、[入口证据](modules/technical-analysis/entry-evidence.md)及[修改设计](plans/technical-analysis-cli-and-vue-cleanup-design.md)。技术交付止于Step05、零业务模型；R4尚未接入旧Activity/Step07。

Maven用官方 `dependency:build-classpath` 给依赖路径，用 `help:effective-pom` 给本次选择下已求值的项目配置。用户执行或明确授权Agent执行；Agent读退出状态/日志，交输出文件位置和明确的模块/JDK选择，**不手填源码根、编译版本、模块边或私有语义JSON**。Java只读取已有具体字段、绑定R0并核验实际环境，不求值原/父POM、Profile/BOM，不解析或下载依赖，不调用Maven。该协议适用于OpenClaw、Trae等运行器，不能依靠Codex临时补全；Skill负责操作与沟通，Java负责确定性处理。Maven可能加载扩展，不能声称天然零客户代码执行。缺输出或已知JAR/JDK/来源/项目问题阻断；通过后可导航并披露诊断覆盖未确认，不证明编译正确，不自建Maven管理或全文件诊断证明系统。旧 `plan-materials` 已退役，见[依赖详细设计](modules/technical-analysis/dependency-preparation.md)。

主设计维护接力、目标与版本矩阵；逐步设计维护步骤合同；内部Modules维护详细职责。补充文档保存研究缘由、历史验收和跳转，不再拥有第二份活跃生产合同。

| 主题 | 详细owner | 交给下游什么 |
| --- | --- | --- |
| 源码准备、完整入口 | [源码准备](analysis-steps/01-verified-source-inventory.md)、[准备Modules](modules/source-preparation/README.md)、[Step02](analysis-steps/02-application-discovery.md) | 有效源码范围、问题和固定字节；后续发现入口 |
| JDT/持久化/入口材料 | [Step03](analysis-steps/03-program-graphs.md)、[Step04](analysis-steps/04-proven-code-facts.md)、[Step05](analysis-steps/05-business-flows.md) | 已保存完整方法/调用/候选/XML/SQL |
| 技术操作接力、环境、前端关联 | [技术Modules](modules/technical-analysis/README.md) | 已实现：四操作、准确双分支来源、静态请求匹配与按入口JSON，保留未确认范围 |
| 新证据→本体分支 | [Ontology Modules](modules/ontology-recognition/README.md)、[实现差距](modules/ontology-recognition/implementation-delta.md) | 正式四操作、Agent/store/发布/查询及typed审阅通过限定工程回归；新R4/O0保存查询与最终静态质量通过。三个真实样例待验，真实自主选材和全仓覆盖未证明，无旧分析兼容 |
| 新材料→Activity | [Step06](analysis-steps/06-flow-interpretation.md)、[Activity Modules](modules/activity-explanation/README.md) | 完整ReviewedActivity、来源与覆盖 |
| 目录、过程与发布 | [Step07](analysis-steps/07-repository-knowledge.md)、[业务过程Modules](modules/business-process-discovery/README.md) | 完整过程、归并、五文件 |
| 并发、Provider、阶段尝试、新批次 | [model-job-execution](modules/model-job-execution.md) | 绑定、保存和明确终态 |
| Prompt输入/输出 | [中文Prompt合同](references/semantic-interpretation-prompts.md) | 任务范围与闭合响应 |
| 精确版本/输出归属 | [Activity integration-contracts](modules/activity-explanation/integration-contracts.md) | 配置、来源映射、范围完成性与私有结果 |
| 历史九章 | [Step08](analysis-steps/08-nine-section-document.md) | 零模型读取、查询、重渲染 |

Step03–05的analysis.code/persistence/material、Step06投影/阅读协调与Step07内部职责均藏在既有步骤内，不按表格行数增加公共方法、分析步骤或固定产物数量目标。JavaParser、严格图/Fact/Proof/Flow/Capsule/M10 producer退出新生产；精确历史读者保留。CONTEXT只定义业务概念。

## 3. 总体接力与分支

```text
源码准备 R0
  ├─ collect-frontend R1 ──────────────────┐
  └─ collect-code R2 → analyze-persistence R3
                                          ↓
                              assemble-materials R4
                                          ↓
                       新 Ontology 分支（工程已接通，真实样例待验）
                        O0 准备资料，零模型
                         → O1 调查/识别/原文审阅
                         → O2 跨入口关联/原文审阅
                         → O3 确定性发布，零模型
                                          ↓
               对象、身份、属性、联系、操作、规则、维度、指标

旧已保存 Packet/M10 → Activity → Business Process
（独立既有分支；不向 O0–O3 输入内容，不为本体补兼容）
```

R0–R4是技术操作角色，O0–O3是本体操作角色；不是新增九个分析步骤。八个semantic key保留，本体操作使用 `repository-knowledge` 下独立执行意图和独立publication。用户可只准备资料、只识别指定范围、只关联或只发布；Skill不能因上一操作成功便擅自扩大模型范围。

新分支只接受版本明确的 `entry-evidence-index`／`entry-evidence`／`frontend-evidence-coverage` v1或v2家族及其真实receipt，且来源为准备版 `PREPARED_V1`；按各自准确Schema、producer和保存owner读取，不补字段转换旧文件。同R0可选补读只读有效原文，含DDL或项目说明；不是从旧分析补结论。版本、排除范围或receipt不符时在Provider初始化前拒绝。新输出采用本体v1合同；不把旧processResult换个名字当本体。

### 3.1 八个固定步骤中的职责

| 步骤 | 程序责任 | 模型责任 | 下一消费者 |
| --- | --- | --- | --- |
| 源码准备（内部01 verified-source-inventory） | 已实现：接入并保存源码、逐文件核验、明确排除/问题/版本；新路径不建立行索引 | 无 | 公共源码视图及新执行来源检查 |
| 02 application-discovery | 独立前端保存请求/单元；后端读已有Maven输出并发现Spring入口/Mapper候选 | 无；外部Agent只操作工具和解释报告 | 前端供05；后端同会话供03，入口清单供05匹配 |
| 03 program-graphs | JDT LS/Core→共享METHOD、入口自有CALL；逐调用绑定归属及外部/未知/失败分类 | 无 | 04可选补全、05材料 |
| 04 proven-code-facts | 官方MyBatis部件/JSqlParser→XML/statement、SQL状态与限制 | 无 | 05按调用关联 |
| 05 business-flows | R4匹配请求后按entryId保存完整已收集Java/XML/SQL/前端JSON及覆盖；历史Packet保持可读 | 无 | R4供技术查询及正式本体consumer；不接旧分析兼容。旧Packet另供既有06 |
| 06 flow-interpretation | 校验/无损去重投影、实际读取、调度、来源/覆盖 | 小包解释；大包阅读/语义分解；每范围DRAFT+完整REVIEW | 07完整Activity |
| 07 repository-knowledge（既有步骤执行） | 目录/取材/保存/归并/过程发布 | 系统认识、事实DRAFT/WRITE/RULE_REVIEW、归并 | business-processes.md；不是本体前置 |
| 08 nine-section-document | 严格读取历史报告与来源、确定性排版 | 无新调用 | 历史读者 |

### 3.2 新本体分支的责任与实现差距

O0–O3另属平行本体操作族，不在上表增加一个必经编号步骤。它们仅复用repository-knowledge这个存储key的新module槽位，运行意图、typed输入/输出、查询和业务算法独立；不执行上表第07行。

五个内部职责为Corpus/MaterialPreparation、Recognition、Linker、Validator、Publisher；不得为五个名字先造五套框架。Java以零模型调用组织完整原文、紧凑上下文、技术邻居、来源映射及成本；模型判断业务含义；Validator只检查结构/来源/范围；Publisher不再调模型改写。选中入口、待读范围、已读历史和当前正文分别保存，移出正文不得删除尚未读的另一端导航。

四个正式命令为 `prepare-ontology`、`identify-ontology`、`relate-ontology`、`publish-ontology`，沿唯一CLI及既有Agent/store接通。现有四技术命令及 `prepare-source` 不重做。识别/关联采用EXTRACT或RELATE→完整原文REVIEW，无WRITE、无旧过程归并。每次请求只处理具名问题及完整必要单元；输入/REVIEW超容量如实失败或保留未知，不截断。限定工程通过不等于客户模型样例通过。

当前正式Corpus、阅读状态、typed提取/审阅、私有保存、确定性组装、四CLI及公开查询已接通，复用R4/R0读者与StructuredModelProvider。scripted DISCOVERY/QUESTION＋MODEL及有限DDL已通过正式Agent/store直接测试；客户新旧R4→O0均已安装、查询。辅助PoC与正式工程测试分别记录，自动选材两批的旧失败不因此改写。真实三例对象/关系/维度/指标质量及全仓覆盖尚未验证，产品模型派发0；当前十步计划工程完成0–8，最终静态质量已通过，真实样例和交付仍待完成。实际可读且有界的结构错误稿及通用诊断已能进入同一次预定REVIEW，不增加自动修稿调用；来源损坏、无法读取、容量或保存失败不靠模型绕过。

共同线索反查、真实`statementRefs`读回及已读方法作为合法查询键已进入正式阅读与保存接线，并通过直接工程测试。共同方法/语句/表只是待查技术邻居，不是业务关系；模型须核对标识产生、传递、保存及使用两端。具体读取状态、紧凑投影和实际成本由[材料设计](modules/ontology-recognition/material-preparation.md)拥有；不增加新解析器、图平台或宿主Agent代写/导入路线。

同R0的DDL按配置可选进入O0，保存独立schema-evidence及覆盖，不新增必经技术R命令；最新辅助PoC没有使用DDL。证据读者/O0提前提供作用域内稳定短别名，内部entryId/hash不替换，原文包仍有局部S引用；不改写历史R4。类型化JSON为主格式，三元组只是可选关系视图。

Skill驱动Java正式操作并读取真实结果；用户可改业务Prompt与沟通Prompt、配置已有输入/模型/容量/范围。Prompt不能改Schema、有效范围或来源规则；需要新组织算法时是明确代码扩展，不能依赖当前Codex记忆临时补证。CLI必须可查询实际材料、来源映射、输入、Prompt、模型原稿和修正，不是黑盒。框架材料验收、模型质量与业务覆盖分层；自主选材成功不再作为证明机械准备可行的唯一前提，但仍是独立未通过项。后续实施须依本轮设计另行安排，不继承已耗尽的实验授权。

首版不搭图数据库/向量库、语义证明系统、规则执行器、指标引擎或Foundry部署；不自研DDL恢复器、Maven管理、JavaScript求值器或全文件诊断证明。小样不默认扩大到全仓，详细改动顺序见[implementation-delta](modules/ontology-recognition/implementation-delta.md)。

## 4. 既有Activity分支：Step05 v1怎样成为模型输入

本节只描述现有后端Packet消费，不表示已保存技术v2或本次目标入口证据已经接入模型。新技术材料的读取、查询及未适配业务入口拒绝规则见[技术接线合同](modules/technical-analysis/cli-and-runtime.md)。

源码准备只保证明确范围的原文和检查结果，不输出业务关系。新目标中的readiness与检查/保存状态分别表达；用户可基于明确版本刷新或排除具名文件/目录。新来源版本必须被公共reader和旧材料复用入口共同执行，不能只在Skill写“跳过”。首版不做跨版本依赖级复用，旧结果仍可历史查看；具体合同由[来源版本与异常](modules/source-preparation/contracts-and-storage.md)维护。

Step05是材料关系唯一owner。code-reading-material-set-v1引用完整JDT方法、调用的实际/形式参数、候选、控制/返回、持久化材料及具体未收集原因。Step06不重跑JDT、PersistenceAnalyzer或JSqlParser；允许安全解析已保存Resource.rawSource完成XML结构投影。M10只读历史，不把新材料回填旧包装。

ActivityMaterialProjector是Step06内部确定性投影：相同完整方法/statement/XML正文只发送一份，明确引用保留所有入口、调用、候选与条件关系。去重不能删分支、只留方法名或用摘要替代源码。请求使用局部E/M/C/S键；精确映射在程序侧保存，短键只在所属请求/材料范围有效。

小包能容纳完整材料、Prompt、Schema、输出余量与完整实际DRAFT时，直接DRAFT→完整REVIEW。大包先去重，再提供完整可分页导航，由模型通过有界读取迭代选择完整方法/statement及相关调用、条件、返回、XML/include范围，并提出可独立理解的语义slice。各slice分别DRAFT→完整REVIEW，程序稳定聚合多个Activity；不增加全包摘要合并模型，不要求一个巨大总REVIEW。

页数/轮次/容量与停止合同由[large-material-reading](modules/activity-explanation/large-material-reading.md)维护。上限是运行能力，不是业务范围裁剪规则。不能固定每N行切块、截尾、只发导航卡或丢关键原文后声称完整。模型选择业务共同阅读范围，Java核验句柄、实际原文与技术闭包。未读/未解释/失败范围保留；同入口一个slice成功不表示整体完成。

ActivityExplainer是唯一局部业务解释者；投影器/阅读协调器不命名岗位、行业或流程。保留现有目的、对象、输入、条件、步骤、结果、规则、公式、问题、未知和来源。一个入口可产生多个Activity，一个Activity也可解释多个入口，见[真实推演](examples/activity-material-end-to-end-walkthrough.md)。

## 5. 执行、失败与复用

业务生成默认绑定已登录ChatGPT的Codex上下文Luna/high。方向判断、调度、设计文档和调试由 GPT-6 Sol/xhigh 负责，测试使用 Luna/xhigh，代码使用 Terra/xhigh。一个job固定同一Provider/账户/model/effort，不自动换服务或回退API；历史身份不改写。

新Activity使用单一YAML的全局和Provider/account并发上限，包间并行、同包阶段及slice顺序。失败阶段按配置重试，maxAttempts包含首次，1关闭自动重试；保存每次尝试和backoff原因。REVIEW重试原样复用成功DRAFT，不重读/重写。stageKey包含页、轮与slice身份，不能混淆不同输入。

可重试分类、不可重试认证/来源/范围错误及不确定STARTED处理由[执行合同](modules/model-job-execution.md)统一维护。包局部失败只结束该包，其他已排队包继续；共享来源、配置或认证使继续执行不安全时停止对应共享故障域。终态/成功结果/失败诊断全部保存。部分Activity结果不自动进入Step07。人工重试创建新模型批次，绑定冻结材料与指定失败范围，按新版合同复用匹配成功阶段，旧FAILED/STARTED不改。

新Activity重试政策不扩展到Step07。Step07仍无自动重试：fatal停止新派发，已开始且前置合法的job排空并保存，不归并/正式发布。显式新批次只复用完整匹配的三阶段过程，不能续接孤立DRAFT/WRITE。来源材料不因模型失败重新生成。

## 6. 既有过程分支：Activity怎样成为Business Process

既有Step07过程分支内部有BusinessProcessDiscovery.discover与BusinessProcessPublisher.publish两个深Interface。它们不拥有新本体分支。Discovery隐藏FrozenAnalysisCorpus、RepositoryBusinessCataloger、ProcessMaterialAssembler、CandidateProcessReconstructor和RepositoryProcessConsolidator；Publisher验证封闭结果、来源receipt并确定性发布，不重新解释业务。

FrozenAnalysisCorpus按已绑定运行的准确材料引用和Activity自身来源字段选择：旧Activity重开历史M10，新Activity重开Step05 publication。现有coverage-v3没有materialSource对象；新Activity行的materialId即packetId，另存materialSource字符串、sliceKey、originalSourceRefs。不得按文件形状猜类型、把新SQL补到旧326条Activity上。局部S编号必须经其来源映射才可汇总。

新仓库由全部Activity卡进行首次目录；已有完整目录可作为资料重开，跳过旧目录模型。一次全局选材读取项目说明、全部Activity导航、冻结文件目录与可选问题，形成系统认识、可证伪假设、候选和首批读请求。常识只提出问题；Java不写行业分类器。

每个候选一次CHECK明确保留/移出、完整最终成员/context与可空补读；程序执行一次补读并冻结ProcessReadingPacket。Activity全文按ID去重，(ActivityId,variant)用法不去重。context支持解释，不自动成为过程成员。现有Step07的120行首读预览只供导航，不是新Activity完整材料或大包固定行切片。

DRAFT解释事实；WRITE只见完整实际DRAFT；最终RULE_REVIEW同时见完整原文包、完整实际DRAFT和WRITE，返回{processResult,corrections}。过程保留stage.narrative及全部条件、拒绝、动作、状态、结果、转移、规则适用用法与知识正文。原文可纠正旧Activity解释，但旧Activity不可覆盖。最后只确定性保存/编号/排版。

此前端到端实施的增量包括：导航展示同packet/entry的切片关系而不删原Activity；候选输入使用可逆局部A/T编号；WRITE只改展示文字、固定其它事实结构；最终核对同步检查正文/规则/条件/结果。当前工作区已有对应实现，其中还有未提交修复；真实业务验收状态由所属端到端设计维护。本次前五步设计不将这些既有改动重新列为待开发，也不据此宣称全仓业务验收通过。

归并只裁决KEEP/MERGE_INTO/REJECT及PARENT_CHILD/RELATED/ALTERNATIVE。不满足已有无损条件就保留原过程和原因，不拼stage制造生命周期。三个分母闭合后形成封闭结果；支撑/独立/未分类Activity的既有知识由Discovery确定性投影，Publisher不重新提炼。

## 7. 版本与来源归属

源码准备和业务正文合同本轮不变。版本的唯一精确矩阵由[技术运行合同](modules/technical-analysis/cli-and-runtime.md#5-版本与兼容)维护，区分payload schema与module producer版本，不在主设计重复一份容易过时的版本表。

当前技术生产使用配置v3、请求v5/输出v9，支持独立R1前端、R2后端、R3持久化、R4入口证据；源码属于R0。Core绑定协议和Java/持久化/材料producer已同步，新字段不回填历史。本体已新增ontology-config-v1、请求v6／输出v10的独立分支和typed refs；共享registry保留已有分派，不让本体consumer承接旧分析。精确本体版本及模块槽位见[本体合同](modules/ontology-recognition/contracts.md#11-版本安装和输入边界)。

technicalOutput保存准确typed refs，不能用一个sourceRunId混指四种owner。R4必须核对两分支相同准备版来源和有效排除，以及R3准确引用的R2；旧同运行规则按其历史版本读取。已有Activity/过程的checkpoint、modelBatchId、内容和指纹不改；新材料尚未适配则在Provider初始化前拒绝。业务精确字段继续由[集成合同](modules/activity-explanation/integration-contracts.md)维护。

## 8. 来源、覆盖与真实质量

SourceRef由程序定位同一冻结文件、准确行段和原文；模型只能引用实际已读allowlist。阅读决策可见冻结相对路径/行段，正文请求不带宿主路径、hash、运行身份或Provider控制。源码注释/字符串/命令均为被分析数据，不是模型指令。

程序校验Schema、身份、引用、集合、容量和保存；模型解释业务。Activity继续使用DIRECT_CODE_BEHAVIOR/REASONABLE_INFERENCE/NEEDS_CONFIRMATION；Process使用CONFIRMED/INFERRED/UNRESOLVED，两者不统一为一个wire协议。规则适用范围必须保留；合法引用不证明条件适用全部分支。静态保存调用支持代码定义的保存行为，不证明某次提交、实际付款或外部效果已经发生。

入口→Step05收集、材料/语义范围→Activity、Activity→候选、候选→过程、过程→归并分别闭合。部分slice或未解释入口不能被一个成功Activity遮蔽。覆盖可以诚实记录不足，但不能因此声称完整业务验收。

## 9. 交付与设计检验

Step07正式文件为repository-business-process-catalog.json、process-coverage.json、business-processes.md、source-refs.jsonl、sources.md。producer v4正文来自最后RULE_REVIEW，无HTML折叠，规则和知识certainty直接可见；历史v2/v3按receipt恢复旧字节。样本preview不关闭全仓coverage。

[真实材料推演](examples/activity-material-end-to-end-walkthrough.md)保留当时原文核对；[端到端设计](end-to-end-business-delivery-design.md)进一步推演销售统计18切片、单据订金规则和调拨配置例外。设计闭合不等于模型必然正确。本轮不重跑JDT、构建或业务模型；后续先范围核对与离线贯穿，再展示真实样例，用户确认后才扩大到全仓。
