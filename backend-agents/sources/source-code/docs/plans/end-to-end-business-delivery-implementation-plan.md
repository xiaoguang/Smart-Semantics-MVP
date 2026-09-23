# 端到端业务交付实施计划

> 状态：2026-09-23用户已批准实施；先执行离线修改和核对，真实小样展示后仍须确认才扩大全仓。
> 核对日期：2026-09-23。工作目录固定为正式 source-code 模块。

当前执行状态：步骤0、1、2已完成；步骤3开始实施，其余待执行。设计基线为1917798，未验证旧补丁保存为ccd5a96，步骤1修复保存为585c797并通过79项定向测试；步骤2通过8类80项定向测试、Spotless和独立复查。这些是本地验证，不是步骤9完整CI或真实业务验收，尚未推送。当前细项见[执行交接](../../progress/end-to-end-business-delivery-execution.md)。

**Goal：**复用已保存的 Step05 材料和新版 Activity，补齐范围完成记录与可靠接续，修正 Step07 的导航、输入编码和写作约束，经小样核验后生成新版全仓 business-processes.md。

**Architecture：**在现有 Activity、运行协调器、Step07 Discovery、私有任务保存器和 Publisher 内增量修改。不重建上游，不增加公共 Agent、模型调度层、业务规则引擎或证据框架。Java负责取材、结构、范围、引用与保存；模型负责业务发现、推理和最终内容核对。

**Tech stack：**应用 Java 17、Jackson、现有 Maven/JUnit/AssertJ、本地 canonical store、已有 Codex Provider/并发池。质量检查使用 JDK21+ 宿主；按用户最新要求，真实业务模型默认 Luna / high、现有 ChatGPT 登录、并发4、请求超时3600秒，不使用 API-key 回退。旧Activity模型身份不改。

**Spec：**[端到端详细设计](../end-to-end-business-delivery-design.md)，以及其中指定的主设计、Activity、Step07 和模型执行模块合同。补充文档保留历史推演，不作为另一套相反的实现要求。

**已确认选材方式：**无提示自主发现，模型输入focusQuestion=null。采购、销售、调拨仅作人工验收关注范围，不进入模型提示。小样和全仓保持同一输入及语义配置，样本候选集合只影响调度。

## 一、当前已经做到哪里

以下是已核对的实现事实，不重新计入开发量。

| 已有能力 | 当前状态 | 本轮需要补什么 |
| --- | --- | --- |
| JDT、持久化关联、Step05 阅读材料 | 已生成325包，保存326条入口处置 | 不重扫；保留原1个无材料的导航失败入口 |
| 新材料生成 Activity | 已保存418条 Activity；325包都有结果 | 核实每包必需解释范围是否完成，不能只看已有结果数量 |
| 阶段保存、重试和并发 | 步骤1修复已通过定向验证 | 步骤2补最终阅读范围；步骤3补历史接续及公开完成记录 |
| 新 Activity → Step07 | 正式入口、请求、来源读取已接通 | 补真实 Agent/存储贯穿验收与更严格的范围准入 |
| 全仓目录、跨候选选材、阅读检查 | 已实现 | 同入口导航、输入去重和短编号，不改变业务发现为硬编码 |
| DRAFT → WRITE → RULE_REVIEW | 已实现 | 限制 WRITE 修改范围，核对实际成稿，保存可逆编号映射 |
| 归并、五文件、来源页和历史读取 | 已实现 | 回归验证和新版真实交付，不重写 Publisher |

“已有418条”不是“418种业务”，也不等于新版全仓过程已完成。实际是300包各1条，25包多条；其中一个销售统计入口产生18条解释。它们全部保留，后续判断业务用途，不能按数量直接生成18个顺序步骤。

### 固定基线

| 项目 | 本轮使用的值 |
| --- | --- |
| 工作分支 | codex/step05-activity-full-generation |
| 实施起点 HEAD | 51b6625a624b4a63b113dc743ac6e3a3c3cb911d；后续检查点见上方执行状态 |
| 本地 main / 最近 fetch 的 origin/main | 5ceb11115edd23e64d464b8a41b2e3caad27b651；步骤0已fetch核对，交付前仍须重新核对 |
| Step05 状态 | .workspace/jdt-persistence-acceptance-20260917/materials-state-v4.json |
| 状态文件 SHA-256 | 968fc339c5dbd876ce16e7ca9a26ce78e406ad37ae2c4135ca55a069c4a09bb5 |
| 新 Activity 批次 | analysis-run:e00cf448733fc078fe460e84b2ea41f51aec85347a4240a49154087748dfa2f9 |
| 新 M11 receipt | module-receipt:5360f1f47cc4c7b4f5016d85d97978f08e4e999b9b4f923c5705e4dd458a8700 |
| 固定客户源码提交 | 8c30ce7861570458920175e200bb2a6442713580 |
| 受保护补充文档 | docs/supplements/more-findings.md |
| 上述文档 SHA-256 | 59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e |

实施起点的13个生产/测试文件草稿已保存到ccd5a96；本次设计也已独立保存。51b6625的544项测试只对应旧提交，不能代替本轮验证；步骤1的79项定向测试也不能代替后续改动及完整CI。详见[上一轮收口记录](../modules/activity-explanation/post-review-handoff-20260923.md)；不改写该历史记录。

## 二、全局约束

1. **不重新运行 JDT、Step01–05、Builder，不重新生成旧326条或新418条 Activity。**新流程必须先尝试离线接续。如果实际记录不足以确认某包完成，列出具体范围、影响和替代方案，重新生成需另行明确同意；不能用“端到端”默认为全部重做。
2. 不生成九章，不恢复旧 ProcessExplainer 或 JavaParser，不新建 SQL/页面解析器。
3. 旧材料、旧 Activity、历史过程、原始请求响应、日志和 receipt 不覆盖。新增检查点和结果使用新运行身份。
4. 原导航失败入口没有 packet；它保留在326入口分母和范围说明中，不进入模型重试清单。
5. 不以简单字段已填满、coverage CLOSED 或模型调用结束代替业务质量通过；不增加 Java 中文质量黑名单、行业词典或“销售/采购”固定流程答案。
6. Activity 使用已有配置化阶段重试；Step07 没有自动重试、换模型、第四轮润色。失败候选保存结果，需具名授权后才能启动替代尝试，不用新批次绕过次数约束。
7. 本轮计划包含“离线 → 真实小样 → 展示评审 → 全仓”。**小样展示以后，得到用户扩大确认才执行全仓**；无确认不后台继续。
8. 只在正式模块编辑。排除仓库根无关 docs/research/、运行结果、凭据、本机配置和其他工作树。
9. 测试由 Luna/xhigh、实现由 Terra/xhigh；方向判断、设计裁决、设计文档及调试由 Astra/ultra。最多两个工作 Agent，各有独立 progress；共享文件串行修改，同一时间一个重型构建。此前已完成工作的实际作者和模型记录不改标签。
10. 本计划已获准实施，设计基线已保存；所有实现通过本地验证和约定真实验收后统一 PR 交付，不等待远端 CI、不强推、不逐小修推 main。该授权不取消小样后的全仓扩大确认。

## 三、步骤、依赖与连续工时

估算包含相应直接测试、必要修复和文档同步；不含模型等待、用户评审等待以及另行批准的 Activity 重生成。不是按每天8小时计算。

| 步骤 | 工作 | 退出条件 | 连续工程工时 |
| --- | --- | --- | ---: |
| 0 | 保存基线与固定输入 | 已有实现、设计及受保护材料可回退 | 0.5–1h |
| 1 | 补完未验证的 Activity 修复 | 局部失败、重开、错误分类符合合同 | 4–7h |
| 2 | 阅读计划 v2 与配置 | 最终范围、替代关系和运行上限明确 | 5–8h |
| 3 | 完成记录、历史接续和 reuse-only | 新 coverage 可重开；离线接续绝不调用模型 | 9–14h |
| 4 | 实际325包离线核对 | 完成/未完/无法判断的范围逐包可查 | 1–2h |
| 5 | Step07 同入口导航 | 418条保留，重复入口可理解，不制造时序 | 3–5h |
| 6 | 局部引用编码与私有保存 | 完整输入更紧凑，映射可逆、可复用 | 8–12h |
| 7 | WRITE 约束与最终核对 | 写作不改结构化规则，实际纠正进入最终稿 | 4–6h |
| 8 | 正式链与小样驱动贯穿 | CLI、Agent、存储、样本/全量边界都可验证 | 5–8h |
| 9 | 完整本地 CI | 干净构建和模块质量检查通过 | 2–3h |
| 10 | 真实小样及展示 | 自动材料的正文、具体规则经检查并交给用户 | 1–2h核验＋模型时间 |
| 11 | 经确认的全仓生成与交付 | 五项真实产物、范围、调用与耗时记录 | 1–2h核验＋模型时间 |

合计 **约44–70个连续工程小时，另加真实模型及评审等待**。这不是上一轮45–70小时计划重算：上游生成、已有入口和已有三阶段能力不再开发；时间主要花在尚未实现的范围接续、输入编码、写作限制和实际链路验证。

依赖关系：

~~~text
0 → 1 → 2 → 3 → 4（旧输入范围检查）
 \→ 5 → 6 → 7
             \＋3 → 8 → 9 → 10 → 用户确认 → 11
~~~

步骤5可以与Activity工作并行，但涉及同一 SourceAnalysisExecution、DefaultBusinessProcessDiscovery 或 PrivateModelJobResultStore 时必须交接后串行。步骤4有未决范围时，可继续独立代码工作；不能悄悄过滤这些包进入正式全仓。

完成步骤3后按实耗重新估算未开始项；完成真实目录及首个候选后再估算模型时间，不反复把已完成部分加回来。

## 四、逐项实施

以下路径均相对于正式 source-code 模块。为减少重复：

- J = src/main/java/org/sourceanalysis/app/
- T = src/test/java/org/sourceanalysis/app/
- R = src/main/resources/org/sourceanalysis/app/

### 步骤0：保存基线，不把未验证修复冒充已交付

**修改/记录位置：**当前相关设计、README.md、scoped AGENTS.md、progress；不修改历史验收记录。

- [ ] 重新检查 git status、分支和远端；获准执行后 fetch，比较当前 HEAD 与最新 main。保留当前分支已有提交与未提交修复，不直接从 main 重开而丢弃已有能力。
- [ ] 把本次设计形成独立文档提交。已有代码草稿可保存本地检查点，但标明未验证，不以基线提交代表通过。
- [ ] 登记325包状态、418条 M11、旧326条 Activity、旧过程与源文件的完整发布引用及哈希。只核对涉及的保存记录，不扫整个磁盘。
- [ ] 更新当前 README/实现状态中“尚未接入新材料”“尚未实现正式入口”等过时表述；历史记录保持原文。
- [ ] 每个实施 Agent 建立自己的 progress，列清负责文件、下一步和交接点。

**验证：**git diff --check、提交范围和保护清单核对。无构建、扫描或产品调用。

**退出：**后续任务能明确区分“旧提交”“未验证修复”“本计划新增”，所有固定输入仍可定位。

### 步骤1：先修好现有 Activity 失败和重开行为

**主要文件：**

- J/analysis/interpretation/activity/ActivityExplainer.java
- J/analysis/interpretation/activity/ActivityReadingCoordinator.java
- J/analysis/interpretation/activity/ActivityEntryCoverage.java
- J/analysis/interpretation/activity/ActivityJobCoordinator.java（仅实际需要时）
- J/adapter/provider/ProcessCodexSubscriptionCommand.java
- J/runtime/modeljob/PrivateModelJobResultStore.java
- J/adapter/cli/SourceAnalysisExecution.java

**先补失败测试，再完成已有补丁：**

- [ ] 核验大包已有计划和成功阶段的重开；不得因一个 REVIEW 失败重新请求阅读决策或 DRAFT。
- [ ] 普通retry和reuse-only都要拒绝“已存在但损坏、readingPacket或source mapping不匹配”的计划；新增READING_PLAN调用为0，不能落回coordinate重新选材掩盖损坏。
- [ ] 覆盖 S1成功、S2失败、S3成功：S2为包内局部故障时继续合法范围，并保留S1/S3。对“容量预检在 try 外抛出”单独测试，不能丢掉之前成功结果。
- [ ] 历史 oversized 方案被后续合法方案替代后，不能仍以旧错误把最终计划标成未完成；真正未解决范围不能被过滤掉。完整合同由步骤2落地。
- [ ] Provider 只按可用的结构化错误分类。现有 stderr/stdout 中包含 rate limit、model、schema 等词，不足以证明错误类别；无法可靠判断时记录 UNKNOWN，不基于关键词自动重试。
- [ ] 超时确认子进程已退出后才允许下一次尝试；状态不确定时不得重叠启动。
- [ ] 先完成响应、范围和保存验证，再登记成功。保留失败原文和成功部分，修复不能抹掉历史失败。

**直接测试：**

~~~bash
mvn -t .mvn/toolchains.local.xml -Dtest=ActivityLargePacketFormalEntryTest,ActivityReadingCoordinatorGuardrailTest,ActivityStageRetryExecutionTest,ActivityPacketFailureIsolationTest,ProcessCodexSubscriptionCommandTest test
~~~

对已有补丁先运行其测试确认现状；新增失败测试不能通过回滚用户修改伪造 RED。源码或断言不符合已确认合同才修复。

**退出：**阶段调用数、继续/停止范围和重开都有可观察断言；此时不宣称所有旧包范围已完整。

### 步骤2：阅读计划区分最终范围与历史尝试

**主要文件：**

- J/analysis/interpretation/activity/ActivityReadingPlan.java
- J/analysis/interpretation/activity/ActivityReadingCoordinator.java
- J/analysis/interpretation/activity/ActivityPromptCatalog.java
- J/adapter/cli/RepositoryRunConfiguration.java
- J/adapter/cli/SourceAnalysisExecution.java
- J/runtime/modeljob/PrivateModelJobResultStore.java
- R/analysis/interpretation/activity/ 下现有阅读 Prompt；新增 v2资源，保留历史资源按原合同使用
- [大材料阅读设计](../modules/activity-explanation/large-material-reading.md)、[来源合同](../modules/activity-explanation/integration-contracts.md)

**实现内容：**

- [x] 使用明确的 activity-reading-plan-v2，不能只把旧对象的版本字符串改成v2。
- [x] 在现有阅读响应中加入 finalSliceKeys、supersededSlices、finishReading；保留导航请求、完整单元请求、切片建议和未知问题等已有字段。
- [x] 替代必须显式指出原slice、替代slice和原因；不能靠中文相似度猜测，也不能把历史所有提案累加为最终必需范围。
- [x] 最后必需范围与当前未解决问题单独保存；原始每次决策及超限错误仍保留。
- [x] 解释开始前冻结范围。开始后改变范围不得复用旧 DRAFT。
- [x] 历史v1严格读取并核对实际caller closure、原始scope定义；公共完成性分类留给步骤3，不假定完成。
- [x] 把已有默认128导航页、4补读轮、32个当前有效切片接入 sourceAnalysis.activityReading，复用 ActivityReadingProfile。不是新增费用预算或固定业务数量。
- [x] YAML写v3、执行配置写v5并保存有效reading配置；旧配置按合同严格读取。只增大上限不自动废弃已完成范围，实际范围变化才影响依赖稿件。

**新增字段的表达示例（不是客户实际结果）：**

~~~json
{
  "finalSliceKeys": ["scope-query", "scope-summary"],
  "supersededSlices": [
    {
      "sliceKey": "scope-too-large",
      "replacementSliceKeys": ["scope-query", "scope-summary"],
      "reason": "由两个已明确的解释范围替代"
    }
  ],
  "finishReading": true
}
~~~

**测试：**扩展 ActivityReadingCoordinatorGuardrailTest、ActivityReadingPlanPersistenceTest、ActivityRetryYamlConfigurationTest、SourceAnalysisModelJobsConfigurationTest。覆盖替代指向未知slice、重复替代、已解释范围变更、未结束、历史信息不足、配置默认值与覆盖值。

**退出：**新模型只提出实际范围，程序能重开同一范围；原销售统计18条不会被离线算法擅自合并或删除。

### 步骤3：范围完成记录、历史接续与零调用入口

这是剩余Activity工作中最大的接线项，不另建迁移框架。

**主要文件：**

- J/analysis/interpretation/activity/ActivityExplanationResult.java
- 新增 J/analysis/interpretation/activity/ActivityPacketCompletion.java：仅承载设计规定的完成记录值对象
- ActivityExplanationCheckpointPublisher.java、ActivityExplanationCheckpointReader.java
- ActivityExplainer.java、ActivityEntryCoverage.java
- J/artifact/AtomicCanonicalPublicationEngine.java
- J/runtime/modeljob/PrivateModelJobResultStore.java
- J/adapter/cli/SourceAnalysisCli.java、SourceAnalysisExecution.java
- J/analysis/knowledge/ProcessDiscoveryRequest.java
- 对应输出读取、配置测试及存储fixture

**3.1 新范围记录贯通发布和读取**

- [ ] coverage-v4在现有根结构新增 packetCompletion；每个真实packet恰好有一条记录。
- [ ] 字段固定为 packetId、entryIds、completion、requiredSliceKeys、completedSliceKeys、incompleteScopes。completion为 COMPLETE / INCOMPLETE / UNDETERMINED；每个 incompleteScope含可空sliceKey、entryIds、reasonCode。
- [ ] 内存结果、发布器、读取器和canonical schema策略一起修改。Activity JSONL v2不变，M11 producer升v4；保留历史v3读取，不把缺字段默认补成完成。
- [ ] COMPLETE取决于最终必需范围与对应成功结果，而不是Activity非空、没有抛异常或所有单次请求有终态。
- [ ] 验证completedSliceKeys属于requiredSliceKeys，且每项对应完整有效REVIEW及其实际Activity/合法未解释处置。按slice.entryKeys汇总多入口结果：不足只落到受影响入口，同包其它入口的成功保留，但packet仍不能标为完整。
- [ ] MODEL_NOT_EXPLAINED等未解释记录仍阻止正式完整业务准入；原导航失败入口无packet，单独保留。
- [ ] 失败运行先保存可验证部分和输出，再转终态。inspect/artifact能查成功部分，不能因此放行Step07。

**3.2 显式离线承接旧结果**

- [ ] 在已有Activity execute-step增加 --reuse-only，必须同时指定 --reuse-from-model-batch；与主动 --retry-failed-from-model-batch互斥。
- [ ] 在 requireModelJobsForExecution、登录检查或 Provider工厂之前分流。缺登录/模型服务也能离线检查，Provider初始化和调用均为0。
- [ ] 按完整材料身份、历史计划、已审结果和来源关联承接旧Activity；保留旧Activity ID、业务字段、模型身份和源映射。
- [ ] 这不是把旧Prompt结果冒充新Prompt任务命中：离线接续保留原生成出处，只产生新范围检查点和运行登记。
- [ ] 能确认完成的承接；缺完成信息但结果有效的记UNDETERMINED；确实有缺项的记INCOMPLETE。声称成功的文件损坏应失败，不改称“未知”掩盖损坏。
- [ ] 不完整时仍保存合法部分和清单，返回非零，不调用模型补齐、不隐藏失败包、不重新激活旧运行。
- [ ] 使用原运行输出v6及 activityBatchComplete，修正其计算含义；不再增加相似的第二个完成布尔值。

**3.3 Step07准入**

- [ ] 在现有 ProcessDiscoveryRequest、SourceAnalysisExecution.assembleStep05ProcessDiscoveryRequest 和真实读取链检查新范围记录。
- [ ] 同时核对全部packet、入口处置、未解释记录和Activity，不只检查输出布尔值。
- [ ] 历史v3可查询，但本次正式全仓使用步骤4生成的明确范围检查点；不把旧“没有packetCompletion”静默视为通过。

**目标命令（本轮待实现的选项，不是现状承诺）：**

~~~text
source-analysis --config /absolute/path/activity.yaml execute-step
  --target flow-interpretation
  --reuse-from-model-batch analysis-run:e00cf448733fc078fe460e84b2ea41f51aec85347a4240a49154087748dfa2f9
  --reuse-only
~~~

该命令创建新的活动结果运行，不重扫、不生成内容。新运行状态、输出及完整引用由CLI返回，后续不手工拼receipt。

**测试：**ActivityExplanationCheckpointTest、ModelBatchActivityCheckpointPublisherTest、ActivitySliceAggregationTest、ActivityRetryPacketScopeTest、ModelBatchAnalysisRunOutputTest。新增真实CLI/Agent/store的reuse-only用例：Provider工厂若被调用即失败；覆盖325→324遗漏、空结果、未知范围、损坏记录、来源不一致、旧v3、新v4、失败运行可查询。

**退出：**从有效旧结果产生明确的新范围检查点成为正式能力，而不是一次性手工改JSON。

### 步骤4：用真实325包完成离线检查

**工具/记录位置：**唯一正式CLI；本地独立执行目录及验收记录，不修改旧运行或业务内容。

- [ ] 对固定完整材料与M11执行reuse-only，输出每包范围完成结果及新运行ID。
- [ ] 核对总分母仍是325包、326入口，418条原Activity无删改；原1个导航失败入口不伪造新解释。
- [ ] 重点核对销售统计18条的历史大范围、替代与成功结果；不预设最终必须变成1条。
- [ ] 记录 COMPLETE / INCOMPLETE / UNDETERMINED数量、逐包原因和可复用内容；检查输入哈希不变、Provider/JDT/Builder/Activity生成调用均为0。

**范围检查后的分支：**

| 实际结果 | 后续行为 |
| --- | --- |
| 全部已有packet必需范围可确认完成 | 使用新M11进入步骤5–11；仍披露原导航缺口及其他语义限制 |
| 部分范围未完成或无法判断 | 展示具体包/入口、已有结果、缺哪份记录；不默许重新生成 |
| 历史成功记录损坏或来源不一致 | 保留现场并定位；不通过重新生成掩盖损坏 |

若确需重新阅读/生成，另列“只重做哪些阶段、为何旧稿不可复用、预计调用范围、替代方案”，请用户决定。这不进入本计划默认调用量；已核实的其它结果继续保留。不能靠缩小325包分母获得全仓通过。

**退出：**明确回答“已有418条能否直接继续、若不能具体卡在哪里”。代码工作仍可推进，但正式全仓不得越过未完成范围。

### 步骤5：Step07将同入口材料组织成可理解导航

**主要文件：**

- J/analysis/knowledge/DefaultBusinessProcessDiscovery.java 中 ActivityIndexCard、FrozenCorpus、catalog、materialSelectionInput
- J/analysis/knowledge/BusinessProcessPromptCatalog.java
- R/analysis/knowledge/ 的 catalog、catalog-shard、catalog-merge、material-selection Prompt
- [目录模块](../modules/business-process-discovery/repository-business-cataloger.md)、[材料模块](../modules/business-process-discovery/process-material-assembler.md)

**实现内容：**

- [ ] 用保存来源身份、packet及完整entry集合生成组导航键，不按名称猜同入口；重叠集合如[E1,E2]与[E2,E3]不得合并成同一组。
- [ ] 卡片保留已有业务字段、来源组键、原sliceKey和单条Activity身份；共享入口信息可去重，但不删除418条的具体规则和结果。
- [ ] 来源关联贯穿首次目录、分片/合并、全局选材和阅读检查；局部P/E组键跨分片稳定。同组过大时沿既有机制分页并带组键及页号，不截掉该组剩余卡片。
- [ ] CHECK中未读导航和已读完整Activity均保留来源组、完整入口集合及sliceKey；同一关系继续进入最终readingPacket，不能在目录阶段展示后又丢掉。
- [ ] 让模型看到“这些解释来自同一入口”，而非把18条slice误当18个独立接口或顺序阶段。
- [ ] 目录模型仍决定业务用途、候选和多用法；不新增一次语义去重模型调用，不在Java合并Activity。
- [ ] 首次目录基于新418条生成，不能把旧326条的目录视为匹配结果。当前Step05路线对不支持的旧目录输入继续明确拒绝，不顺带扩建目录导入。
- [ ] 导航卡用于找材料；重建继续拿完整Activity与实际阅读原文，不用卡片代替正文。

**测试：**扩展 BusinessProcessReadingPipelineTest、FrozenAnalysisCorpusDualMaterialSourceTest 和目录现有用例；覆盖同名不同包、不同slice、重叠入口、多业务用法、任意N与分片覆盖。不得只断言本仓固定418。

**退出：**实际catalog/selection输入中看得到来源组织，Activity内容和最终处置分母不减。

### 步骤6：A/T局部引用、输入结构与三阶段保存同步更新

**主要文件：**

- J/analysis/knowledge/DefaultBusinessProcessDiscovery.java：packetInput、processInput、statementMap、Schema、reconstructRaw、finalizeCandidate、processFingerprint、saveProcessTriple/reopenProcess
- 新增 J/analysis/knowledge/ProcessLocalReferenceMap.java：包内编码/解码值对象；不增加公共服务或新存储系统
- J/runtime/modeljob/PrivateModelJobResultStore.java
- BusinessProcessPromptCatalog及相关Prompt
- [候选重建合同](../modules/business-process-discovery/candidate-process-reconstructor.md)

**6.1 建立一次映射**

- [ ] 阅读检查及一次补读完成、成员与上下文冻结后，为该完整阅读包分配稳定A/T/S作用域。
- [ ] A映射Activity，T映射实际字段statement；复用已有S来源归一化，不建第二套源码引用系统。
- [ ] T目录提供所属A、字段名、数组下标或标量位置，使模型能定位完整Activity里的文本；不能只给T1而丢掉它对应哪句话。
- [ ] 成员、context、candidate、statement/source目录、Schema允许值和三阶段输入统一采用同一映射。
- [ ] 映射由稳定排序生成，不受并发完成顺序影响；A1/A10按完整键处理，不能全局字符串替换，更不能改业务正文中的字面量。
- [ ] 不新增stageLocalId。阶段沿用(processLocalId, order)，U仍为每个过程自己的业务用法作用域。

**内部接口建议（本步骤实现并测试，不代表已有API）：**

~~~java
// ProcessLocalReferenceMap：只在同一完整阅读包内使用。
// encodeInput 编码结构化引用，不改业务文本。
// decodeProcess 解码最终结构化引用，返回副本。
// toPrivateRecord 保存双向可检查映射。
ObjectNode encodeInput(ObjectNode globalInput);
ObjectNode decodeProcess(ObjectNode localProcessResult);
ObjectNode toPrivateRecord();
~~~

**6.2 保存与复用**

- [ ] 新过程私有model-job-reviewed-result-v5保存实际编码输入、完整三稿、localReferenceMap、inputEncodingVersion及现有sourceReferenceMapping。
- [ ] 原始响应保持模型实际返回；还原后的对象不能覆盖raw。
- [ ] 阅读包v2、Discovery producer v5、三阶段pipeline及指纹v2同步；历史双轮/三阶段各按原格式读取。
- [ ] 新格式缺映射、映射不闭合、未知引用明确失败。旧完整过程不冒充新输入任务命中。
- [ ] localReferenceMap的真实映射内容和编码版本参与语义指纹；仅局部A/T/S和编码后文字一致不足以复用。fresh与reopen/reuse均执行映射、DRAFT局部引用闭合和WRITE约束校验，不能复用时跳过。
- [ ] 单决策保存格式仍v2；输入和producer/指纹变化使旧语义决策失效，不机械增加所有版本。
- [ ] 检查实际Prompt、input、Schema及输出余量；最终核对还包含完整DRAFT和WRITE。字段压缩不等于已证明能装下上下文，超限明确报告，不能截断。

**测试：**BusinessProcessThreeStagePipelineTest、BusinessProcessReadingPipelineTest、PrivateModelJobResultStore相关直接测试。新增包内编码往返测试：同名键跨包、A1/A10、业务文本含类似ID、context引用、数组顺序、旧格式读取、映射损坏、合法复用/输入失效、根部$defs仍有效；编码后输入相同但真实映射改变时必须不命中复用。

**退出：**三个请求实际使用相同可逆短编号；方法、规则、原文、范围和候选允许引用没有因压缩而消失。

### 步骤7：限制WRITE，只由最终核对纠正业务事实

**主要文件：**DefaultBusinessProcessDiscovery.java、BusinessProcessPromptCatalog.java、DRAFT/WRITE/RULE_REVIEW Prompt；原渲染器保持最终结果消费。

- [ ] DRAFT返回后只做必要的本地结构/引用定义检查，不用最终业务确认规则过早拒绝本可由最终核对修正的稿件。
- [ ] WRITE仍返回完整过程JSON，但只允许修改：
  - processes[*].name、purpose；
  - processes[*].stages[*].name、narrative。
- [ ] 对其它业务字段进行深比较，包括成员/用法、条件、规则、金额、状态、引用、分支、结果、数组次序、数量和处置。不能只是比较几个字段，也不能把整段stages排除后漏检。
- [ ] 业务文本可以改善表达，但不加中文黑名单。WRITE若改变受保护结构，保存实际失败响应并停止该任务，不自动“取回旧字段”伪装写作成功。
- [ ] RULE_REVIEW看到同一完整原文、完整实际DRAFT和实际WRITE；它可以根据材料纠正事实、重新组织合法U或拆分过程，不受WRITE只改展示字段的限制。
- [ ] 最终合法性按固定A/T/S、成员/context和各过程局部定义验证；只为理解而读取的context不能自动变成未声明的业务用法。
- [ ] 解析完整processResult，应用来源还原，最终纠正直接进入保存和Markdown。corrections是说明，不能只改说明而保留错误正文。
- [ ] RULE_REVIEW之后只有确定性渲染，不增加润色模型。

**测试示例：**

~~~text
输入规则：已审核或部分办理可关联；订金和本次付款分开。
WRITE只改阶段名称/正文 → 允许进入最终核对。
WRITE把结构化状态改成未审核 → 结构检查拒绝，保存失败。
WRITE正文写反、结构没变 → Java不假装懂中文；
scripted RULE_REVIEW返回修正后的完整结果 → Markdown只采用修正稿。
RULE_REVIEW调整一个U并同步全部引用 → 合法；
引用不存在的查询U → 失败，不制造空定义。
~~~

同时覆盖历史调拨“规则有强审核例外，拒绝条件/结果漏写”的最终纠正传递。脚本测试证明接线，不证明真实模型必然能纠正。

**退出：**Java能保证WRITE没有篡改结构化规则，最终核对能修正内容；不把这两点混成“业务已自动证明正确”。

### 步骤8：正式CLI、真实存储与可维护小样驱动贯穿

**主要文件：**

- SourceAnalysisCli.java、SourceAnalysisExecution.java
- J/runtime/ 中 PersistedBusinessProcessRunExecutor 及真实调用路径
- ProcessDiscoveryRequest.java、DefaultBusinessProcessDiscovery.java
- CanonicalBusinessProcessPublisher、BusinessProcessCheckpointReader（只改实际需要的接线）
- 新增 tools/repository-run/acceptance/Step05ProcessSampleDriver.java 及同目录说明：仅调用既有sample接缝，不另写取材/生成算法
- tools/repository-run/README.md、实际配置示例

**实现与测试：**

- [ ] 正式入口从新M11推导材料与来源，验证三个owner；模型配置、实际执行范围和输入编码写入execution-config-v5。
- [ ] 用真实CLI、真实Agent、真实canonical store加scripted/fake进程跑通；不能只用fake Agent检查参数转发。
- [ ] 保留旧M10/旧Activity的读取和历史渲染，当前全仓使用新Step05来源。
- [ ] 小样调用已有 discoverCatalogSample、reconstructSelectedPreview/reconstructSelected 接缝；不调用完整discover或归并。
- [ ] 驱动读取正式新状态和候选manifest，不复用旧M10/v3状态、写死候选ID和Luna容量的历史三例脚本。
- [ ] 驱动与正式入口共用assembleStep05ProcessDiscoveryRequest、validateStep05ReuseBatch、validateProcessReuseActivity、validateStep05ProcessReuseReadingInputs及writeStep05ProcessModelJobExecutionConfiguration，再调用已有sample接缝。必要helper最小调整为同包可见，驱动放相同Java包；不复制整CLI，也不另写组装/身份校验算法。
- [ ] 目录从全部新Activity开始，选择模型实际返回的候选；保持完整目录原序号与Provider绑定。一个候选返回多个片段全部导出。
- [ ] 样本候选集合写入执行manifest，属于调度子集；不加入会使同一候选在全仓中失配的内容指纹。候选真实输入、阅读选择和完整Activity集合仍按实际依赖匹配。
- [ ] 小样保存正常私有决策/三阶段结果，不安装假的全仓publication；未选候选和归并调用均为0。
- [ ] 全仓命令显式复用同轮完整目录、选材、阅读和三阶段结果；输入、Prompt、模型及配置不匹配时列出未命中，不假装复用。
- [ ] 五文件经真实安装、重开、artifact查询、确定性渲染验证，所有来源链接能对应已保存引用；没有链接不启动补证据。

**直接测试组合：**

~~~bash
mvn -t .mvn/toolchains.local.xml -Dtest=ProcessDiscoveryDualMaterialProtocolTest,FrozenAnalysisCorpusDualMaterialSourceTest,BusinessProcessAcceptanceSampleTest,BusinessProcessPublicationTest,BusinessProcessReadableRenderingTest,CrossObjectProcessExecutionConfigurationTest test
~~~

新正式CLI测试加入相应组合。特别保留sample→formal reuse测试：只有剩余候选及归并发请求，已完成样本没有再生成。

**退出：**正式执行不是另一套脚本算法；新版资料、历史资料、样本及完整发布边界均清楚。

### 步骤9：完整本地验证，建立可运行基线

- [ ] 完成所有直接测试及文档同步，检查没有无关源码/配置、运行数据或凭据进入提交。
- [ ] 执行本模块一次干净构建与完整正确性/质量检查：

~~~bash
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" \
mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify
~~~

批准本实施计划后才执行上述完整模块检查；不启用real-jdt-it，不运行外层工程，同一时间没有其它重型构建。

- [ ] 记录实际UT数量、quality结果、版本和耗时；不得拿51b6625历史544测试结果替代。
- [ ] 使用正式新读取器重新打开步骤4的检查点、旧326条与已有过程，检查受保护文件哈希。
- [ ] 建立本地已验证代码检查点。此处只表示代码验证通过，真实业务验收尚未通过。

若后续真实运行暴露生产修改，先补直接失败测试，再修复并重做受影响验证；最终交付必须对应最后代码，不为省时引用修改前构建。

### 步骤10：真实小样，先让用户看到结果

**输入：**步骤4新范围检查点、固定Step05及冻结源码；不使用旧手工采购材料替代自动选材。

1. 在新独立批次上生成本轮全仓目录，随后系统认识与全局选材。focusQuestion=null；采购、销售、调拨仅作为人工验收关注范围，不传给模型，也不提供正确Activity、文件、状态或流程答案。
2. 从模型实际候选中选2–3个：优先采购/销售/调拨，必要时说明实际候选如何覆盖这些关注点；不能因为名称没出现便造候选。
3. 每候选一次阅读检查与补读，再按DRAFT→WRITE→RULE_REVIEW执行；并发不超4，实际小样最多3个同时处理。
4. 检查实际请求容量和原文进入情况；先检查第一个完整结果，再扩大到其余小样。
5. 导出最终核对结果对应的business-processes.md、sources.md及完整中间稿。给用户直接链接和关键原文，明确生成时间、本轮批次，不能用旧文件冒充新样本。
6. 提交逐项核验结论，**停止扩大，等待用户确认后再执行步骤11**。

| 关注范围 | 直接核验的业务问题 |
| --- | --- |
| 采购相关 | 对象关联的标识及带入/回写动作；关联来源状态；分批办理；订金与本次付款；累计查询的过滤与适用条件 |
| 销售相关 | 订单与后续单据的联系；修改/审核/反审核条件；库存或最低售价条件不能泛化；查询不能写成创建/退款已经发生 |
| 调拨相关 | 两仓及数量方向；审核、强审核例外在正文/规则/拒绝/结果一致；不添加无依据的在途/签收/结算 |
| 同入口拆片 | 18条销售统计解释是否被合理组织，完整公式/过滤是否保留，未被强塞成销售生命周期18步 |

验证以本轮实际材料为准，历史已知错误仅供核对，不把正确答案注入通用Prompt。可读但规则不准不算通过；材料不足如实说明，不强行补齐全部生命周期。

失败时报告具体阶段、已有结果和最小修正范围。Step07不自动增加第四次修稿；若需具名替换尝试，先讨论。用户扩大确认与失败重试授权不是同一件事。

### 步骤11：确认后复用小样，完成全仓与交付

- [ ] 用户看过小样并同意扩大后，使用相同材料、语义配置及完整执行范围创建新过程批次。
- [ ] 正式source-analysis入口显式指定匹配的小样复用来源；目录、选材和已审样本任务命中时零调用承接，原运行/日志不改。
- [ ] 继续其余候选的阅读检查及三阶段；每个完整结果立即保存。按实际候选N排队，不按418两两组合。
- [ ] 全部必需候选成功后执行既有仓库归并DRAFT＋REVIEW，保留不能无损合并的完整过程，不拼阶段制造时序。
- [ ] 覆盖核对Activity、候选、过程三个分母；统计/查询作为支撑可以保留，未分类/不足范围明确披露。
- [ ] 发布五文件并用正式reader重开核验，保留全部中间记录和前后对照。
- [ ] 复核代表性业务正文、已知规则、来源和未解决范围。若只有格式/覆盖通过而内容仍不合格，分别报告，不宣称计划完成。
- [ ] 最终实现集中提交/推送一个PR；按既定流程合入main并核对本地、远端状态。不等待远端CI、不强推。当前分支已有但尚未合并的实现一并审阅，不能漏掉。
- [ ] 将结论写入当前实现状态和验收记录；计划结束后按仓库规则收纳本计划progress，历史运行产物不删除。

**最终五项产物：**

1. business-processes.md：业务目录、具体步骤/分支/回退、规则、结果、支撑与未知。
2. repository-business-process-catalog.json：完整结构化过程及关系。
3. process-coverage.json：真实处置与未处理范围。
4. source-refs.jsonl：来源原文、文件及范围。
5. sources.md：正文“查看依据”的独立来源页面。

另交付离线范围核对、新M11引用、小样原文、与旧结果对照、实际调用/复用/失败数及耗时。没有活动重生成授权时，Activity生成调用数应始终为0。

## 五、版本与模块修改对照

一次贯通生产者、指纹、写入、读取、canonical策略和直接fixture。不得先写出新格式再留待读取器修补。

| 合同 | 本轮目标 | 不变/历史处理 |
| --- | --- | --- |
| YAML | repository-run-config-v3，接activityReading | 历史配置严格读 |
| 模型执行配置 | model-job-execution-config-v5 | 历史v4严格读，输出仍v6 |
| Activity覆盖 | flow-interpretation-activity-coverage-v4，新增packetCompletion | 历史v3不默认完成 |
| Activity发布 | M11 activity-explainer producer v4 | flow-interpretation-activity-explanations-v2不变 |
| 私有阅读计划/批次结果 | activity-reading-plan-v2 / activity-batch-result-v2 | 历史计划与结果按原形状读 |
| Activity私有完整结果 | 保持model-job-reviewed-result-v4 | 不把v4重新用于过程 |
| Activity packet结果 | 保持activity-packet-result-v1 | 逐attempt记录不机械升版 |
| Step07 Discovery | producer v5 | 旧producer结果不能冒充新输入 |
| 阅读包/局部引用 | process-reading-packet-v2 / process-local-reference-map-v1 | 单决策容器仍v2 |
| 三阶段私有结果 | model-job-reviewed-result-v5 | 保存映射与实际三稿，保留旧严格读取 |
| 三阶段pipeline | business-reasoning-writing-rule-review-v2 | 不影响Activity双轮或归并双轮 |
| 三阶段指纹 | business-process-three-stage-input-fingerprint-v2 | 输入/Schema/顺序/模型身份参与 |
| Prompt版本 | catalog/shard/merge 2→3；selection 2→3；reading-check 4→5；draft 4→5；write 1→2；rule-review 1→2 | consolidation v2不变 |
| 过程Publisher/五文件 | Publisher v4及现有公共格式不变 | 新内容有新身份，历史渲染按原producer |
| Step05、导航、持久化 | 不变 | 不重扫、不重新解析 |

动态JSON Schema在现有代码中生成，按实际Schema字节参与指纹；不虚构额外Schema资源文件。严格旧读不是“新字段缺失默认空”。

## 六、模型调用与时间如何计算

步骤0–9全部离线，真实模型调用为0。真实业务模型只在步骤10、11启用。

定义：

- C：首次目录任务数，包含实际shard及唯一merge；每任务DRAFT＋REVIEW。
- N：全局选材确定的实际候选数；不是Activity数。
- k：小样实际选择数，通常2–3。
- 每候选：一次READING_CHECK＋DRAFT＋WRITE＋RULE_REVIEW，共4次。

无失败、无历史复用时：

~~~text
小样：2C + 1次全局选材 + 4k
全仓总计（含小样）：2C + 1 + 4N + 2次仓库归并 = 2C + 4N + 3
小样全部匹配复用后追加：4(N-k) + 2
~~~

没有足够容量生成目录时先按已有分片机制及完整merge检查；不能截断418张卡后称全仓。容量是能否执行的问题，不是用户费用预算。

记录读取、排队、各模型阶段、保存、人工核验的独立耗时。用实际目录、首候选三阶段和首批并发测得数据更新预测；不预估固定“半天/一天”，不以串行总时长简单除以4冒充实测加速。失败及授权替代请求另外计数。

## 七、五类重点审查与交付检查

### Review focus

1. **成功被局部失败吞掉：**S1成功、S2预检/REVIEW失败、S3合法，公开结果及私有稿件是否保留；下一包是否按故障范围继续。
2. **把未知当完成：**旧v1没有显式替代、旧v3没有packetCompletion、损坏成功文件，三者必须分别处理；不能只有布尔值或非空结果就通过。
3. **错误业务归组：**同名不同packet、重叠entry集合、同入口多slice、多用途不能被确定性“去重”误删，也不能强制排成流程。
4. **编码或写作破坏内容：**A1/A10、context/T归属、U局部作用域、$defs、数组次序、受保护字段和实际最终纠正必须贯通三稿/保存/Markdown。
5. **无意触发昂贵执行：**reuse-only在登录/Provider之前分流；新Step05驱动不借用旧M10脚本；小样不调用未选候选/归并；全仓不绕过展示确认；没有任何隐式上游重跑。

### 最终可观察检查

- [ ] 旧325包/326入口、418条和326条旧Activity、more-findings.md等受保护材料字节保持。
- [ ] 新范围检查点可通过正式CLI重开；所有无法判断范围均有明确解释，不缩分母。
- [ ] 新输入实际保留完整Activity、XML/SQL原文、条件、公式和适用范围。
- [ ] 同入口导航和A/T编码不丢字段；实际请求容量有记录。
- [ ] WRITE不改变受保护结构，RULE_REVIEW纠正进入实际最终内容。
- [ ] 已保存匹配任务显式复用；失败任务没有伪装成功或额外自动模型轮次。
- [ ] 样本和全仓范围不同，扩大有明确确认；全仓五文件真实发布并可重开。
- [ ] 读者可以说明业务怎样衔接、哪些条件允许或拒绝、哪些联系仍不能确认。
- [ ] 本地CI对应最后代码；提交不含本机路径配置、凭据、运行材料及无关docs/research。
- [ ] 原技术导航缺口与业务未决事项如实披露；不宣称识别出系统所有隐含业务。

## 八、计划评审结论与执行边界

本计划按“现有实现 → 剩余修改 → 直接测试 → 真实输入 → 最终文档”逐项核对，未将已接通的Step05/Activity/Step07入口重复列为新开发。

当前不确定的不是要不要重写整套系统，而是两项必须实测的事实：

1. 旧325包保存记录能否全部确认最终必需范围。步骤4先零调用回答；不能确认时先讨论最小补救。
2. 输入组织与WRITE约束能否改善真实业务准确性。步骤10先交给用户可读小样，再决定扩大；程序测试不能替代这一结论。

因此，按已批准计划连续完成离线实现和验证；遇到上述需要新Activity内容或扩大范围的决定时，明确列材料、影响与选择，不自作主张重新生成。执行状态见progress/end-to-end-business-delivery-execution.md；代码通过与真实业务通过分别记录。
