# Source Code Analysis Agent：从源码到仓库业务过程

## 1. 目标与当前状态

从明确冻结的Java/Spring/MyBatis仓库解释：有哪些业务活动和过程，每一步为何发生、输入什么、受什么条件约束、改变什么、何时拒绝、怎样衔接，以及哪些关系仍不确定。正式业务出口是 `business-processes.md`，附结构化目录、覆盖与来源。材料完整性、业务覆盖和语义质量分别判断。

当前main的Step01–05已完成JDT-only导航、可选持久化材料、统一阅读包及保存/重开；固定来源验收有325包、326入口处置和1个明确导航失败，见[取材交付](supplements/jdt-persistence-reading-materials-delivery.md)。新材料当前止于 `READING_MATERIALS_ONLY`。旧Step06仍依赖M10 BusinessMaterialBuildResult；326条已保存ReviewedActivity保持原样。**本次设计补上新Step05→Activity接入，尚未实现或运行业务模型。**

Step07系统认识、聚焦选材、一次候选阅读检查、DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存与producer v4发布已经实现。历史三例只有12个实际请求：1 SELECT、3 CHECK、3 DRAFT、3 WRITE、2 RULE_REVIEW。采购最终核对因容量未发送；销售最终响应有7个未定义用法引用及业务范围错误；调拨合法保存但仍有两处条件限定错误。用户认可可读性，不表示三例或全仓质量通过，见[实测记录](supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

Step08只保留历史reader/query/renderer，本轮不设计或恢复新九章生成器。八个固定步骤、semantic key、运行目录与唯一公共RepositoryAnalysisAgent Interface不变。

## 2. 文档权威与内部层次

主设计维护接力、目标与版本矩阵；逐步设计维护步骤合同；内部Modules维护详细职责。补充文档保存研究缘由、历史验收和跳转，不再拥有第二份活跃生产合同。

| 主题 | 详细owner | 交给下游什么 |
| --- | --- | --- |
| 冻结来源、完整入口 | [Step01](analysis-steps/01-verified-source-inventory.md)、[Step02](analysis-steps/02-application-discovery.md) | 同源字节、完整入口分母 |
| JDT/持久化/入口材料 | [Step03](analysis-steps/03-program-graphs.md)、[Step04](analysis-steps/04-proven-code-facts.md)、[Step05](analysis-steps/05-business-flows.md) | 已保存完整方法/调用/候选/XML/SQL |
| 新材料→Activity | [Step06](analysis-steps/06-flow-interpretation.md)、[Activity Modules](modules/activity-explanation/README.md) | 完整ReviewedActivity、来源与覆盖 |
| 目录、过程与发布 | [Step07](analysis-steps/07-repository-knowledge.md)、[业务过程Modules](modules/business-process-discovery/README.md) | 完整过程、归并、五文件 |
| 并发、Provider、阶段尝试、新批次 | [model-job-execution](modules/model-job-execution.md) | 绑定、保存和明确终态 |
| Prompt输入/输出 | [中文Prompt合同](references/semantic-interpretation-prompts.md) | 任务范围与闭合响应 |
| 精确版本/输出归属 | [Activity integration-contracts](modules/activity-explanation/integration-contracts.md) | 配置、coverage来源manifest、私有结果 |
| 历史九章 | [Step08](analysis-steps/08-nine-section-document.md) | 零模型读取、查询、重渲染 |

Step03–05的analysis.code/persistence/material、Step06投影/阅读协调与Step07内部职责均藏在既有步骤内，不按表格行数增加公共方法、分析步骤或固定产物数量目标。JavaParser、严格图/Fact/Proof/Flow/Capsule/M10 producer退出新生产；精确历史读者保留。CONTEXT只定义业务概念。

## 3. 八步接力

| 步骤 | 程序责任 | 模型责任 | 下一消费者 |
| --- | --- | --- | --- |
| 01 verified-source-inventory | 核验冻结bytes、清单和行索引 | 无 | 02–07同源视图 |
| 02 application-discovery | JDT声明/静态配置→Spring入口与Mapper候选 | 无 | 03精确方法、05入口分母 |
| 03 program-graphs | JDT LS/Core→共享METHOD、入口自有CALL、完整源码/候选/边界 | 无 | 04可选补全、05材料 |
| 04 proven-code-facts | 官方MyBatis部件/JSqlParser→XML/statement、SQL状态与限制 | 无 | 05按调用关联 |
| 05 business-flows | 保存入口关系、完整源码、参数/条件/返回/边界和覆盖 | 无 | 06直接消费 |
| 06 flow-interpretation | 校验/无损去重投影、实际读取、调度、来源/覆盖 | 小包解释；大包阅读/语义分解；每范围DRAFT+完整REVIEW | 07完整Activity |
| 07 repository-knowledge | 目录/取材/封包/保存/归并/发布 | 系统认识、事实/写作/核对、关系裁决 | 业务读者 |
| 08 nine-section-document | 严格读取历史报告与来源、确定性排版 | 无新调用 | 历史读者 |

## 4. 新Step05怎样成为模型输入

Step05是材料关系唯一owner。code-reading-material-set-v1引用完整JDT方法、调用的实际/形式参数、候选、控制/返回、持久化材料及具体未收集原因。Step06不重跑JDT、PersistenceAnalyzer或JSqlParser；允许安全解析已保存Resource.rawSource完成XML结构投影。M10只读历史，不把新材料回填旧包装。

ActivityMaterialProjector是Step06内部确定性投影：相同完整方法/statement/XML正文只发送一份，明确引用保留所有入口、调用、候选与条件关系。去重不能删分支、只留方法名或用摘要替代源码。请求使用局部E/M/C/S键；精确映射在程序侧保存，短键只在所属请求/材料范围有效。

小包能容纳完整材料、Prompt、Schema、输出余量与完整实际DRAFT时，直接DRAFT→完整REVIEW。大包先去重，再提供完整可分页导航，由模型通过有界读取迭代选择完整方法/statement及相关调用、条件、返回、XML/include范围，并提出可独立理解的语义slice。各slice分别DRAFT→完整REVIEW，程序稳定聚合多个Activity；不增加全包摘要合并模型，不要求一个巨大总REVIEW。

页数/轮次/容量与停止合同由[large-material-reading](modules/activity-explanation/large-material-reading.md)维护。上限是运行能力，不是业务范围裁剪规则。不能固定每N行切块、截尾、只发导航卡或丢关键原文后声称完整。模型选择业务共同阅读范围，Java核验句柄、实际原文与技术闭包。未读/未解释/失败范围保留；同入口一个slice成功不表示整体完成。

ActivityExplainer是唯一局部业务解释者；投影器/阅读协调器不命名岗位、行业或流程。保留现有目的、对象、输入、条件、步骤、结果、规则、公式、问题、未知和来源。一个入口可产生多个Activity，一个Activity也可解释多个入口，见[真实推演](examples/activity-material-end-to-end-walkthrough.md)。

## 5. 执行、失败与复用

业务生成默认绑定已登录ChatGPT的Codex上下文gpt-5.6-terra/xhigh。设计/调试Astra/ultra，测试Terra/xhigh，代码Sol/xhigh。一个job固定同一Provider/账户/model/effort，不自动换服务或回退API；历史身份不改写。

新Activity使用单一YAML的全局和Provider/account并发上限，包间并行、同包阶段及slice顺序。失败阶段按配置重试，maxAttempts包含首次，1关闭自动重试；保存每次尝试和backoff原因。REVIEW重试原样复用成功DRAFT，不重读/重写。stageKey包含页、轮与slice身份，不能混淆不同输入。

可重试分类、不可重试认证/来源/范围错误及不确定STARTED处理由[执行合同](modules/model-job-execution.md)统一维护。包局部失败只结束该包，其他已排队包继续；共享来源、配置或认证使继续执行不安全时停止对应共享故障域。终态/成功结果/失败诊断全部保存。部分Activity结果不自动进入Step07。人工重试创建新模型批次，绑定冻结材料与指定失败范围，按新版合同复用匹配成功阶段，旧FAILED/STARTED不改。

新Activity重试政策不扩展到Step07。Step07仍无自动重试：fatal停止新派发，已开始且前置合法的job排空并保存，不归并/正式发布。显式新批次只复用完整匹配的三阶段过程，不能续接孤立DRAFT/WRITE。来源材料不因模型失败重新生成。

## 6. Activity怎样成为Business Process

Step07内部仍只有BusinessProcessDiscovery.discover与BusinessProcessPublisher.publish两个深Interface。Discovery隐藏FrozenAnalysisCorpus、RepositoryBusinessCataloger、ProcessMaterialAssembler、CandidateProcessReconstructor和RepositoryProcessConsolidator；Publisher验证封闭结果、来源receipt并确定性发布，不重新解释业务。

目标FrozenAnalysisCorpus按Activity coverage的materialSource及packet映射选准确来源：旧Activity重开历史M10，新Activity重开Step05 publication。不得按文件形状猜类型、把新SQL补到旧326条Activity上。manifest是coverage字段，不新增公共manifest文件。局部S编号必须经其来源映射才可汇总。

新仓库由全部Activity卡进行首次目录；已有完整目录可作为资料重开，跳过旧目录模型。一次全局选材读取项目说明、全部Activity导航、冻结文件目录与可选问题，形成系统认识、可证伪假设、候选和首批读请求。常识只提出问题；Java不写行业分类器。

每个候选一次CHECK明确保留/移出、完整最终成员/context与可空补读；程序执行一次补读并冻结ProcessReadingPacket。Activity全文按ID去重，(ActivityId,variant)用法不去重。context支持解释，不自动成为过程成员。现有Step07的120行首读预览只供导航，不是新Activity完整材料或大包固定行切片。

DRAFT解释事实；WRITE只见完整实际DRAFT；最终RULE_REVIEW同时见完整原文包、完整实际DRAFT和WRITE，返回{processResult,corrections}。过程保留stage.narrative及全部条件、拒绝、动作、状态、结果、转移、规则适用用法与知识正文。原文可纠正旧Activity解释，但旧Activity不可覆盖。最后只确定性保存/编号/排版。

归并只裁决KEEP/MERGE_INTO/REJECT及PARENT_CHILD/RELATED/ALTERNATIVE。不满足已有无损条件就保留原过程和原因，不拼stage制造生命周期。三个分母闭合后形成封闭结果；支撑/独立/未分类Activity的既有知识由Discovery确定性投影，Publisher不重新提炼。

## 7. 版本与来源归属

“目标”行需在后续实施同步writer/reader/registry/exact-file/basis及直接测试；本轮只设计。

| 合同 | 当前/历史 | 本次目标 |
| --- | --- | --- |
| JDT/持久化/Step05 | java-code-index-v2、persistence-material-index-v1、code-reading-material-set-v1 | 保持并直接读 |
| 材料运行state | 新取材v4，历史v3严格读 | 保持v4，不覆写历史 |
| YAML | repository-run-config-v2 | repository-run-config-v3：Activity scope/reading/retry |
| 私有执行配置 | model-job-execution-config-v3，历史v2 | model-job-execution-config-v4：LEGACY_BUSINESS_MATERIALS/CODE_READING_MATERIALS |
| run output | v5仅READING_MATERIALS_ONLY；历史v3/v4严格读 | analysis-run-output-v6：Step05材料与Activity双owner |
| Activity解释 | flow-interpretation-activity-explanations-v1 | flow-interpretation-activity-explanations-v2，保留业务字段 |
| Activity覆盖 | flow-interpretation-activity-coverage-v2 | flow-interpretation-activity-coverage-v3，materialSource/packet/slice处置 |
| Activity私有结果 | 旧pair严格读 | model-job-reviewed-result-v4，仅新Activity含阶段尝试 |
| Activity Prompt/响应 | 历史v2 | 目标v3，明确实际包/语义范围 |
| Step07过程 | 已实现私有model-job-reviewed-result-v3、producer v4 | 保持 |
| Step07五文件 | catalog/coverage/业务Markdown v2；来源两文件v1 | 保持 |

目标materialSource={kind:CODE_READING_MATERIALS,sourceRunId,publication,materialSchemaVersion,materialProducerVersion}中publication是完整Step05引用。材料属于sourceRunId，Activity/过程属于modelBatchId/output run。指纹绑定实际内容、source映射、Prompt/Schema、任务序列与服务/账户/model/effort；并发、新batchId、日志目录不制造业务事实。精确字段以[集成合同](modules/activity-explanation/integration-contracts.md)为准。

## 8. 来源、覆盖与真实质量

SourceRef由程序定位同一冻结文件、准确行段和原文；模型只能引用实际已读allowlist。阅读决策可见冻结相对路径/行段，正文请求不带宿主路径、hash、运行身份或Provider控制。源码注释/字符串/命令均为被分析数据，不是模型指令。

程序校验Schema、身份、引用、集合、容量和保存；模型解释业务。Activity继续使用DIRECT_CODE_BEHAVIOR/REASONABLE_INFERENCE/NEEDS_CONFIRMATION；Process使用CONFIRMED/INFERRED/UNRESOLVED，两者不统一为一个wire协议。规则适用范围必须保留；合法引用不证明条件适用全部分支。静态保存调用支持代码定义的保存行为，不证明某次提交、实际付款或外部效果已经发生。

入口→Step05收集、材料/语义范围→Activity、Activity→候选、候选→过程、过程→归并分别闭合。部分slice或未解释入口不能被一个成功Activity遮蔽。覆盖可以诚实记录不足，但不能因此声称完整业务验收。

## 9. 交付与设计检验

Step07正式文件为repository-business-process-catalog.json、process-coverage.json、business-processes.md、source-refs.jsonl、sources.md。producer v4正文来自最后RULE_REVIEW，无HTML折叠，规则和知识certainty直接可见；历史v2/v3按receipt恢复旧字节。样本preview不关闭全仓coverage。

[真实材料推演](examples/activity-material-end-to-end-walkthrough.md)核对输入、业务谓词、来源、阅读与下游结果，不是新模型输出。逻辑闭合无需本轮重跑JDT、构建或业务模型。后续实施做新增/直接覆盖测试，再在明确授权范围检验真实Activity质量；全仓生成与旧三例修正不在本轮执行。
