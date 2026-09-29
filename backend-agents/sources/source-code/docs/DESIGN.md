# Source Code Analysis Agent：从源码到仓库业务过程

## 1. 目标与当前状态

从明确冻结的Java/Spring/MyBatis仓库解释：有哪些业务活动和过程，每一步为何发生、输入什么、受什么条件约束、改变什么、何时拒绝、怎样衔接，以及哪些关系仍不确定。正式业务出口是 `business-processes.md`，附结构化目录、覆盖与来源。材料完整性、业务覆盖和语义质量分别判断。

main的Step01–05已完成JDT-only导航、可选持久化材料、统一阅读包及保存/重开；固定来源有325包、326入口处置和1个导航失败，见[取材交付](supplements/jdt-persistence-reading-materials-delivery.md)。实现分支 `codex/step05-activity-full-generation` 的 `51b6625` 已接通新Step05→Activity及正式Step07入口，真实保存418条新Activity；旧326条保持原样。当前工作树还有未验证的审查修复，不能把分支能力或运行结束等同于已合入main、范围完整或全仓业务质量通过。

当前收尾详细设计见[端到端业务交付](end-to-end-business-delivery-design.md)。范围完成性、历史结果接续、同入口切片关系、局部引用及 WRITE 职责已有实现和直接测试；真实端到端业务过程仍须按“先样例、用户确认、再全仓”的边界验收，不能用程序通过代替业务质量通过。

Step07系统认识、聚焦选材、一次候选阅读检查、DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存与producer v4发布已经实现。历史三例只有12个实际请求：1 SELECT、3 CHECK、3 DRAFT、3 WRITE、2 RULE_REVIEW。采购最终核对因容量未发送；销售最终响应有7个未定义用法引用及业务范围错误；调拨合法保存但仍有两处条件限定错误。用户认可可读性，不表示三例或全仓质量通过，见[实测记录](supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

Step08只保留历史reader/query/renderer，本轮不设计或恢复新九章生成器。八个固定步骤、semantic key、运行目录与唯一公共RepositoryAnalysisAgent方法集合不变。

源码接入见[源码准备详细设计](analysis-steps/01-verified-source-inventory.md)及[实施计划](plans/source-preparation-implementation-plan.md)：普通目录/固定Git逐项读取、结构化问题、四文件发布与严格重开、具名刷新/排除、新准备路径移除行索引、独立 CLI 和 Skill 已实现并通过直接测试。准备版公共源码 reader 只暴露有效范围；真实发布/重开fixture证明排除的Java/XML不进入`VerifiedJavaProject`、`PersistenceAnalysisRequest`或`MapperXmlResourceView`。新执行先核对配置绑定、保存的 request/output 与重新打开的来源；历史 Step05/M10 材料的实际 LEGACY 来源由其引用的 Step01 publication 只读元数据投影，不为来源检查读取源码 blob。M10重开还核对receipt上游引用与state业务流publication的实际payload引用。选择 `PREPARED_SOURCE` 时，旧 Step05/M10 材料因缺准备版来源及有效排除的材料级证明而拒绝复用。本轮最终直接相关测试168/168、质量BUILD SUCCESS（Spotless 630 files clean，SpotBugs/PMD均0）；资源上限停止枚举后的恢复仅允许NEW全范围，3项新增测试通过，计划按本轮范围收口。以上源码准备计划当时未运行客户JDT或产品模型。其后技术计划已从准备版来源保存新技术结果，见下段；跨版本增量复用仍为backlog。

## 2. 文档权威与内部层次

前五步技术设计的权威入口为[技术分析详细设计](modules/technical-analysis/README.md)。当前已完成外部Maven文件交接及原三个技术命令固定源码运行；旧47包/339入口/51前端请求是实际结果，但调用准确性未通过。2026-09-29目标为四个独立操作：collect-frontend、collect-code、analyze-persistence、assemble-materials。前后端独立，组装时匹配并按entryId交付完整JSON；新增职责尚未实施。同步修正已知调用归属、外部边界、前端必要单元及SQL排序，不重写Maven/Java/JS/SQL分析器。详见[依次修改设计](plans/technical-analysis-cli-and-vue-cleanup-design.md)、[入口证据](modules/technical-analysis/entry-evidence.md)和[采购查询例子](supplements/vue-to-sql-walkthrough.md)。本轮止于技术材料，不依赖Step07，也不运行业务模型。

Maven用官方 `dependency:build-classpath` 给依赖路径，用 `help:effective-pom` 给本次选择下已求值的项目配置。用户执行或明确授权Agent执行；Agent读退出状态/日志，交输出文件位置和明确的模块/JDK选择，**不手填源码根、编译版本、模块边或私有语义JSON**。Java只读取已有具体字段、绑定R0并核验实际环境，不求值原/父POM、Profile/BOM，不解析或下载依赖，不调用Maven。该协议适用于OpenClaw、Trae等运行器，不能依靠Codex临时补全；Skill负责操作与沟通，Java负责确定性处理。Maven可能加载扩展，不能声称天然零客户代码执行。缺输出或已知JAR/JDK/来源/项目问题阻断；通过后可导航并披露诊断覆盖未确认，不证明编译正确，不自建Maven管理或全文件诊断证明系统。旧 `plan-materials` 已退役，见[依赖详细设计](modules/technical-analysis/dependency-preparation.md)。

主设计维护接力、目标与版本矩阵；逐步设计维护步骤合同；内部Modules维护详细职责。补充文档保存研究缘由、历史验收和跳转，不再拥有第二份活跃生产合同。

| 主题 | 详细owner | 交给下游什么 |
| --- | --- | --- |
| 源码准备、完整入口 | [源码准备](analysis-steps/01-verified-source-inventory.md)、[准备Modules](modules/source-preparation/README.md)、[Step02](analysis-steps/02-application-discovery.md) | 有效源码范围、问题和固定字节；后续发现入口 |
| JDT/持久化/入口材料 | [Step03](analysis-steps/03-program-graphs.md)、[Step04](analysis-steps/04-proven-code-facts.md)、[Step05](analysis-steps/05-business-flows.md) | 已保存完整方法/调用/候选/XML/SQL |
| 技术操作接力、环境、前端关联 | [技术Modules](modules/technical-analysis/README.md) | 目标：四个独立操作、准确双分支来源、页面请求匹配与按入口完整JSON |
| 新材料→Activity | [Step06](analysis-steps/06-flow-interpretation.md)、[Activity Modules](modules/activity-explanation/README.md) | 完整ReviewedActivity、来源与覆盖 |
| 目录、过程与发布 | [Step07](analysis-steps/07-repository-knowledge.md)、[业务过程Modules](modules/business-process-discovery/README.md) | 完整过程、归并、五文件 |
| 并发、Provider、阶段尝试、新批次 | [model-job-execution](modules/model-job-execution.md) | 绑定、保存和明确终态 |
| Prompt输入/输出 | [中文Prompt合同](references/semantic-interpretation-prompts.md) | 任务范围与闭合响应 |
| 精确版本/输出归属 | [Activity integration-contracts](modules/activity-explanation/integration-contracts.md) | 配置、来源映射、范围完成性与私有结果 |
| 历史九章 | [Step08](analysis-steps/08-nine-section-document.md) | 零模型读取、查询、重渲染 |

Step03–05的analysis.code/persistence/material、Step06投影/阅读协调与Step07内部职责均藏在既有步骤内，不按表格行数增加公共方法、分析步骤或固定产物数量目标。JavaParser、严格图/Fact/Proof/Flow/Capsule/M10 producer退出新生产；精确历史读者保留。CONTEXT只定义业务概念。

## 3. 八步接力

| 步骤 | 程序责任 | 模型责任 | 下一消费者 |
| --- | --- | --- | --- |
| 源码准备（内部01 verified-source-inventory） | 已实现：接入并保存源码、逐文件核验、明确排除/问题/版本；新路径不建立行索引 | 无 | 公共源码视图及新执行来源检查 |
| 02 application-discovery | 目标拆运行：独立前端保存请求/单元；后端读既有Maven输出并发现Spring入口/Mapper候选 | 无；外部Agent只操作工具和解释报告 | 前端供05；后端同会话供03，入口清单供05匹配 |
| 03 program-graphs | JDT LS/Core→共享METHOD、入口自有CALL；目标修正绑定归属及外部/未知/失败分类 | 无 | 04可选补全、05材料 |
| 04 proven-code-facts | 官方MyBatis部件/JSqlParser→XML/statement、SQL状态与限制 | 无 | 05按调用关联 |
| 05 business-flows | 当前Packet保存前后端；目标R4匹配请求后按entryId保存完整Java/XML/SQL/前端JSON及覆盖 | 无 | 旧v1供06；新入口证据先完成技术消费，未适配业务消费者提前拒绝 |
| 06 flow-interpretation | 校验/无损去重投影、实际读取、调度、来源/覆盖 | 小包解释；大包阅读/语义分解；每范围DRAFT+完整REVIEW | 07完整Activity |
| 07 repository-knowledge | 目录/取材/封包/保存/归并/发布 | 系统认识、事实/写作/核对、关系裁决 | 业务读者 |
| 08 nine-section-document | 严格读取历史报告与来源、确定性排版 | 无新调用 | 历史读者 |

## 4. 已有Step05 v1怎样成为模型输入

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

## 6. Activity怎样成为Business Process

Step07内部仍只有BusinessProcessDiscovery.discover与BusinessProcessPublisher.publish两个深Interface。Discovery隐藏FrozenAnalysisCorpus、RepositoryBusinessCataloger、ProcessMaterialAssembler、CandidateProcessReconstructor和RepositoryProcessConsolidator；Publisher验证封闭结果、来源receipt并确定性发布，不重新解释业务。

FrozenAnalysisCorpus按已绑定运行的准确材料引用和Activity自身来源字段选择：旧Activity重开历史M10，新Activity重开Step05 publication。现有coverage-v3没有materialSource对象；新Activity行的materialId即packetId，另存materialSource字符串、sliceKey、originalSourceRefs。不得按文件形状猜类型、把新SQL补到旧326条Activity上。局部S编号必须经其来源映射才可汇总。

新仓库由全部Activity卡进行首次目录；已有完整目录可作为资料重开，跳过旧目录模型。一次全局选材读取项目说明、全部Activity导航、冻结文件目录与可选问题，形成系统认识、可证伪假设、候选和首批读请求。常识只提出问题；Java不写行业分类器。

每个候选一次CHECK明确保留/移出、完整最终成员/context与可空补读；程序执行一次补读并冻结ProcessReadingPacket。Activity全文按ID去重，(ActivityId,variant)用法不去重。context支持解释，不自动成为过程成员。现有Step07的120行首读预览只供导航，不是新Activity完整材料或大包固定行切片。

DRAFT解释事实；WRITE只见完整实际DRAFT；最终RULE_REVIEW同时见完整原文包、完整实际DRAFT和WRITE，返回{processResult,corrections}。过程保留stage.narrative及全部条件、拒绝、动作、状态、结果、转移、规则适用用法与知识正文。原文可纠正旧Activity解释，但旧Activity不可覆盖。最后只确定性保存/编号/排版。

此前端到端实施的增量包括：导航展示同packet/entry的切片关系而不删原Activity；候选输入使用可逆局部A/T编号；WRITE只改展示文字、固定其它事实结构；最终核对同步检查正文/规则/条件/结果。当前工作区已有对应实现，其中还有未提交修复；真实业务验收状态由所属端到端设计维护。本次前五步设计不将这些既有改动重新列为待开发，也不据此宣称全仓业务验收通过。

归并只裁决KEEP/MERGE_INTO/REJECT及PARENT_CHILD/RELATED/ALTERNATIVE。不满足已有无损条件就保留原过程和原因，不拼stage制造生命周期。三个分母闭合后形成封闭结果；支撑/独立/未分类Activity的既有知识由Discovery确定性投影，Publisher不重新提炼。

## 7. 版本与来源归属

源码准备和业务正文合同本轮不变。版本的唯一精确矩阵由[技术运行合同](modules/technical-analysis/cli-and-runtime.md#5-版本与兼容)维护，区分payload schema与module producer版本，不在主设计重复一份容易过时的版本表。

当前是技术配置v2、请求v4/输出v8、前后端同旧R1。目标配置v3、请求v5/输出v9支持独立R1前端、R2后端、R3持久化、R4入口证据；源码始终属于R0。Core绑定协议和Java/持久化/材料producer同步升级，新字段不回填历史。

technicalOutput保存准确typed refs，不能用一个sourceRunId混指四种owner。R4必须核对两分支相同准备版来源和有效排除，以及R3准确引用的R2；旧同运行规则按其历史版本读取。已有Activity/过程的checkpoint、modelBatchId、内容和指纹不改；新材料尚未适配则在Provider初始化前拒绝。业务精确字段继续由[集成合同](modules/activity-explanation/integration-contracts.md)维护。

## 8. 来源、覆盖与真实质量

SourceRef由程序定位同一冻结文件、准确行段和原文；模型只能引用实际已读allowlist。阅读决策可见冻结相对路径/行段，正文请求不带宿主路径、hash、运行身份或Provider控制。源码注释/字符串/命令均为被分析数据，不是模型指令。

程序校验Schema、身份、引用、集合、容量和保存；模型解释业务。Activity继续使用DIRECT_CODE_BEHAVIOR/REASONABLE_INFERENCE/NEEDS_CONFIRMATION；Process使用CONFIRMED/INFERRED/UNRESOLVED，两者不统一为一个wire协议。规则适用范围必须保留；合法引用不证明条件适用全部分支。静态保存调用支持代码定义的保存行为，不证明某次提交、实际付款或外部效果已经发生。

入口→Step05收集、材料/语义范围→Activity、Activity→候选、候选→过程、过程→归并分别闭合。部分slice或未解释入口不能被一个成功Activity遮蔽。覆盖可以诚实记录不足，但不能因此声称完整业务验收。

## 9. 交付与设计检验

Step07正式文件为repository-business-process-catalog.json、process-coverage.json、business-processes.md、source-refs.jsonl、sources.md。producer v4正文来自最后RULE_REVIEW，无HTML折叠，规则和知识certainty直接可见；历史v2/v3按receipt恢复旧字节。样本preview不关闭全仓coverage。

[真实材料推演](examples/activity-material-end-to-end-walkthrough.md)保留当时原文核对；[端到端设计](end-to-end-business-delivery-design.md)进一步推演销售统计18切片、单据订金规则和调拨配置例外。设计闭合不等于模型必然正确。本轮不重跑JDT、构建或业务模型；后续先范围核对与离线贯穿，再展示真实样例，用户确认后才扩大到全仓。
