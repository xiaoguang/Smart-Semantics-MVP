# R4 证据到本体：正式实施计划

状态：2026-10-05，原十步正式路径经[九步稳定性收尾](ontology-stability-implementation-plan.md)完成本轮限定框架/材料验收并提交[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，尚未合入main。三个客户局部样例已正式O0–O3执行：18请求、九义务八已审/一采购关系拒绝、四文件INCOMPLETE有效部分保存及重开。34个完整Java正文与同R0逐字/摘要核对，所有实际EXTRACT/REVIEW同包同题目录；最后20项直接回归与质量6695通过。模型残余误读、真实自主选材、全仓及任意小窗口支持均单列，不以工程通过宣称业务零错误。准确实际结果见[验收记录](../supplements/ontology-formal-acceptance-20261003.md)；机器字段由本体contracts.md拥有。

## 全局约束

- 复用现有codex/ontology-recognition隔离工作区，不重置、删除或混入已有无关改动。
- 旧Activity、业务过程、九章的分析代码、Prompt、Schema、Markdown渲染不修改、不淘汰。本体不读取这些内容作为事实。
- 共享CLI、registry、canonical store及Provider只增加本体分派；旧写入格式、请求指纹和行为不变。
- 输入为新R4及准确同R0，保留PREPARED_V1、有效排除范围和真实receipt。旧326/418条Activity、模型记录、more-findings.md及历史R0/R4不改写。
- Java准备和发布零模型；选定完整源码不截断，用途/逐位置限制不丢。未知不包装成业务事实。
- 不重建Maven、JavaScript/SQL/DDL解释器，不新建图平台、恢复或审批系统；不执行客户构建、SQL或应用。
- 真实验收仅财务、采购来源明细、期间统计三个SCOPED样例，选择来源EXPLICIT并公开清单。自动模式只做scripted机械验证，不冒称自主发现成功。
- 产品模型默认Luna/high；无API-key回退、自动重试或自动追加批次。具名真实批次建议上限18次，运行前另行确认，不继承旧授权。
- GPT-6 Sol/xhigh调度、方向、文档、debug和review；Terra/xhigh编码；Luna/xhigh写测试。最多两个工作Agent，同一时间一个重型构建。
- 仅新增及直接覆盖测试；不运行无关整仓套件。质量宿主JDK21+，应用编译/测试Java17。
- 验收后集中交付实现PR，不提交运行数据、本机配置、凭据和无关修改。

## 实际起点

已有实验Corpus、反查、完整单元读回、typed runner与局部assembler，以及正式技术CLI/Agent/存储/Provider。正式本体配置、请求、四操作、跨题作用域及安装查询仍待接通。当前getQueryParams已正式保存；未补的是选择回调及保存上下文。

固定原R4：analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe。
同R0：analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7，源码提交8c30ce7861570458920175e200bb2a6442713580。
新前端生产修复产生新的R1/R4，准确复用原R0/R2/R3；本体真实样例使用修复后的新R4，不覆盖原记录。

## 时间与依赖

10步编号0–9，合计约31–48连续工程小时，工具/模型及用户等待另计。Task2后按实际耗时校准剩余，已完成上游不重算。前端与本体材料可分区并行，共享合同/存储串行交接；每项含直接测试、修复和文档同步。

### Task 0: 固定基线与保护清单

预计0.5–1小时。检查branch/HEAD/dirty归属，保存受保护旧MD包、Prompt和历史资料摘要；保存本计划及获准合同。建立各Agent独立progress，不覆盖旧实验。完成标志是精确基线和保护检查可重做，不是全计划完成。

### Task 1: 独立配置、请求与输出

预计3–5小时。新增ontology-config-v1、ontology-scope-v1、ontology-selection-v1和独立ONTOLOGY请求/输出。仅本体写请求v6/output-v10，旧操作继续原格式；新增PREPARE_ONTOLOGY、IDENTIFY_ONTOLOGY、RELATE_ONTOLOGY、PUBLISH_ONTOLOGY意图，不新增公共Agent方法。输入严格绑定准确R4/R0、scope、Prompt及模型配置，Provider初始化前核验。

机器合同同步明确本轮前端v3及R4 v2新家族；保留原R1 v2/R4 v1严格读取。新本体正式四操作使用自己的payload/receipt，不借旧knowledgeCheckpoint。直接RED/GREEN覆盖配置缺项、互斥请求、来源/owner/排除/伪receipt和旧格式不变。

### Task 2: 正式Corpus、紧凑投影与短编号

预计4–6小时。复用OntologyEvidenceCorpus/ReadingPacket/ModelProjection和真实R4 reader，准确方法、语句变体、实际SQL表列及前端单元倒排。Corpus稳定E/U/K、冻结包S；准确变体/用途/映射参与指纹。完整方法/XML/前端单元保留，机械剔除长ID/回执/重复声明，不默认附入全部调用私有JSON；歧义/未知/查询失败逐调用点保留，正常外部边界可汇总。

同选择清单生成稳定输入，实际导航、阅读、提取与含草稿REVIEW使用同投影测量。字节不当token，无固定70KB保证。必要完整单元超限返回具体问题，不截断/自动摘要/升级模型。准备零模型，实际成本与移出项可查。

### Task 3: 最小前端生产修复

预计3–5小时。保留查询/getQueryParams既有路径。仅用已有vue-eslint-parser及已验Vue2有限模式，增加页面事件、选择回调、保存触发/异步单元、事件实参/回调形参、分支及页面实例上下文。选择端与保存端分开观察，不虚构顺序数据流；地址未知仍候选。

线性wrapperPath不能表达新上下文，新增typed page context。写frontend-http-index-v3及R1 module-v3；R4对应entry-evidence/index/frontend-coverage-v2及module-v4，旧版本读取不变。producer/reader/store/fixtures/Node协议同步，准确来源单元逐字恢复。

新建具名R1/R4，同源复用原R0/R2/R3，不启动JDT/持久化重解析，不替换原R4。两页面及非ERP改名fixture验证实例不串、完整单元保存、未知不升级。

### Task 4: 阅读状态、选择执行与冻结

预计3–5小时。在现有协调器分别保存selectedEntries/selectedClues、discoveredHandles、readHistory、activeUnits、requiredButUnread。移出正文不丢调查入口/待读一端；重复读回保持编号/范围；无进展有具体处置。Prompt、Schema、执行器共用动作数/轮次配置。

QUESTION/DISCOVERY与EXPLICIT/MODEL为独立选择维度。导航按技术共同项组织，未展示/未读分母保留；Java只执行合法查询/选择，不批准关系。冻结投影供同一次EXTRACT/REVIEW，缺helper列未知，变更材料产生新任务。

### Task 5: 类型化提取和实际REVIEW

预计4–6小时。按contracts正式字段实现OBJECT、ACTION、ANALYTIC、RELATE独立Schema/Prompt及校验。OBJECT先审，后续只能使用真实已审对象目录。ANALYTIC同包组件最终整体闭合；金额属性不自动升级KPI。

提取原始响应立即保存。可读、有界、任务可识别但字段/ref错误的草稿进入原定一次REVIEW，附同包完整源码和通用结构诊断；最终严格校验，无额外修稿。非JSON、来源/映射损坏、容量/保存失败或不确定STARTED停止，不伪造稿。保留实际模型绑定、完整Prompt/Schema及corrections。

### Task 6: 跨题对应、关系和确定性发布

预计4–6小时。定义身份包含Corpus/task/localId/reviewVersion。两题O1/S1不可裸合并，Property保留原owner及localId。O2模型核对两端，返回已审identityDecisions和links；Java仅执行合法SAME_OBJECT对应，不按名字/表合并、冲突不覆盖。

发布ontology.json、ontology-coverage.json、ontology-sources.jsonl、ontology-review.json；DRAFT_REVIEWABLE和NOT_EXECUTABLE固定。断引用/冲突/未读有处置，COMPLETE仅声明义务闭合，不承诺全仓/语义正确。输入顺序变化结果稳定。可选ontology.md独立读取已审JSON，不用旧renderer/WRITE。

### Task 7: 四CLI、运行查询和Provider隔离

预计4–6小时。沿唯一source-analysis/public Agent接通：
prepare-ontology --evidence-run R4；
identify-ontology --corpus-run O0 --scope scope.json；
relate-ontology --selection identification-selection.json；
publish-ontology --selection publication-selection.json。

repository-knowledge模块2–5及精确文件集合，step receipt固定真实R4根，语义上游经typed payload及排序唯一ArtifactReference核验。保存先于终态，不返回假receipt。inspect/artifact可读选择、材料、Prompt/Schema、原稿、REVIEW、映射、成本、失败和未知。O0/O3/query不登录、不初始化Provider。

新本体请求提供独立输出token设置，响应字节另限；共享request保留旧构造与旧journal/wire/fingerprint，无override的旧Provider行为不改。厂商协议投影不降低最终校验。完整已审任务精确复用，默认0自动retry/fallback。

### Task 8: 可选DDL、Skill和定向回归

预计4–6小时。O0仅明确配置同R0 schemaSources时调用已有JSqlParser，保存schema-evidence-v1和真实DDL_DECLARED/限制。整dump失败文件级未知；仅明确同源完整定义范围可独立投影，不自写split/恢复。普通索引不等于FK；关闭零parser，不依赖DDL任务继续。

共享Skill新增本体模式及沟通模板，只用正式CLI/JSON/真实产物；用户可改业务Prompt和沟通模板/已有配置，不更改固定合同，不手工桥接材料。第二Agent只看共享说明和输出可接力，不借聊天答案。

运行新增/直接覆盖测试及旧MD隔离测试，再同状态静态quality：JAVA_HOME明确JDK21+，mvn -t .mvn/toolchains.local.xml -Pquality -DskipUTs -DskipITs spotless:check verify。无无关整仓套件/真实业务调用。

### Task 9: 三样例、核验和交付

预计1–2小时核验加工具/模型时间。先正式CLI零模型准备预先公开EXPLICIT选材清单；财务关联、采购来源明细、期间统计三个SCOPED样例。材料全由Java组织，通用Prompt不含标准答案，核验表运行后独立使用。

建议具名真实批次18请求上限：3OBJECT、2ACTION、1ANALYTIC、3RELATE，每组extract+review；运行前单独确认，不继承PoC额度，不自动追加。核验身份两端、回调/明细保存与头编号差异、统计grain/过滤/时间分类/净额及权限masking/未知；动态SQL保留PARTIAL，不执行指标。

分别记录框架/材料、模型质量、范围；已知语义错不报人审通过，自动模式/全仓不宣称通过。更新设计—实现—测试表/backlog，28长期约束，26/27/29/30/31仅按实际证据关闭。验收后集中实现PR，保护历史/旧MD摘要复核，排除运行材料和无关修改。

## 验收结论约定

材料完整性、正式保存/重开、模型理解和覆盖分开判断。scripted证明机械链，真实样例证明有限定义质量；模型误读不自动等于材料损坏，材料漏给不归咎模型。未获真实授权时继续完成全部安全工程工作，但Task9未完成，不能宣称计划完成。旧MD退役另行讨论。
