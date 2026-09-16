# 设计、代码、测试与提交内容清理审计

审计日期：2026-09-15。基线：正式 checkout 的 `main`，`e8c40ea2f250da55d6b8797c32c380461061c3c9`。

**这是审计结论、已确认方向和清理边界，不代表所有修改已经实现。** 用户已确认停用旧 generate、允许补读候选外已保存材料、不能无损合并时保留原过程，以及计划结束后删除临时 progress 的方向。接口细节和实际代码迁移另行落地。历史运行和模型中间结果不删除；本轮不重新扫描客户源码、不调用模型、不改变业务流程发现行为。

## 1. 结论先行

1. 不能把“看上去旧的代码”全部当成死代码。JavaParser 的内部 Adapter/factory 已实现，但正式运行器仍硬限 JDT；旧 `ProcessExplainer` 和九章路径仍被 `generate` 调用。后者与新版 Step07 并存，是真正需要决定如何退役的活路径。
2. 确认存在公共入口接线缺口：CLI 接受 `repository-knowledge`，真实 Agent 却拒绝；应用组合根也没有注入 artifact/render 所需读者。维护运行器成功不等于公共接口已经接通。
3. 上游正常链没有再次重建五图或反复运行 compile/project。确实仍有同进程内重复重开、解析和校验已保存产物，应该按边界收敛，而不是删除来源安全检查。
4. Mapper/XML 已被发现，但 SQL 正文没有通过 `supportingSources` 进入模型材料。它不是“模型已经看过 SQL 但没有理解”，也不是必须重做 JDT。
5. 业务长链的局限部分来自当前设计本身：候选之外的活动不能补读；只能读 Activity 已引用的原文；最终归并不能创作新的跨过程阶段。清理死代码不能解决这些取舍。
6. 当前受跟踪内容没有发现明显运行目录、二进制包或常见明文密钥格式；最明显的历史负担是 811 份 progress，以及留在生产资源目录中的旧 Prompt。两者应分别处理。

## 2. 审计范围与证据等级

范围为 `backend-agents/sources/source-code/` 全模块，以及直接相关的 `.github/workflows/source-analysis.yml`。未跟踪的仓库根 `docs/research/` 不属于本轮成果，不处理。

方法是全模块清单检查、按设计走通生产者/消费者、核对直接测试和真实保存产物；不是逐行形式化证明，也没有重新运行整套真实分析。本文的“确认”指可以从列出的代码、调用关系或文件直接复核，不等于所有路径已通过动态测试。

| 模块 | 核查重点 | 本次判断 |
| --- | --- | --- |
| Capture / Inventory | 固定来源、路径和字节归属、盘点产物 | 继续保留；不能因取材复杂就删除冻结和安全边界 |
| Application discovery | 引擎 catalog、Spring 入口、Mapper/XML | 两引擎内部支持已接入；正式配置受限；XML 后续取材未贯通 |
| JDT / JavaParser / Program graphs | 会话共享、选择隔离、索引、候选/边界 | JavaParser 内部图能力不是死代码；正式运行器尚未支持选择它 |
| Facts / Flows / Capsule | 可选增强、正常执行次数、引用重开 | 未发现正常算法重复执行；仍有重复 I/O 和校验责任 |
| Business materials / Activity | 完整材料、来源、DRAFT/REVIEW、固定结果复用 | 当前 Step07 复用的基础，不能重新实现或整批删除测试 |
| Process discovery / Publication | 卡片、候选、用法、归并、五文件读写 | v2 已实现；长链限制和归并约束见待讨论项 |
| Document / Runtime / CLI | 新旧入口、产物查询、组合根、模型批次 | 新过程维护路径可用；公共接线及旧 generate 路线需要收口 |
| Store / Provider / Build / Tests | 注册、反射/资源加载、隔离、测试分类 | 不能只按字符串零引用删除；提交便携性和部分测试层次有问题 |

## 3. 已确认的问题及处理边界

### F01：公共 CLI 声称支持 Step07，真实 Agent 不支持

- [SourceAnalysisCli](../../src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCli.java) 的 `selectedTargetStep` 将 `repository-knowledge` 转为 `REPOSITORY_KNOWLEDGE`，再调用真实 Agent。
- [LocalRepositoryAnalysisAgent](../../src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java) 第 55–60 行只接受 `FLOW_INTERPRETATION` 和 `NINE_SECTION_DOCUMENT`；上述请求必然抛出 `ANALYSIS_STEP_EXECUTION_NOT_SUPPORTED`。
- [SourceAnalysisCliContractTest](../../src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCliContractTest.java) 的对应测试使用 `RecordingAgent`，只证明参数翻译正确，没有证明真实组合能执行。

**影响：** 用户按公共命令执行新版过程目标会失败；`RepositoryRunMain --mode business-processes` 的实际成功不能覆盖这个问题。

**确定要改：** 不再把当前公共 CLI 描述成已接通 Step07。修复时从 CLI 经过真实 Agent/组合根做一个直接测试，复用既有过程执行器，不再建一套过程算法。旧 `generate` 的处置需要与 D01 一起决定，本轮不临时接出第三条路径。

### F02：应用组合根没有注入 artifact/render 的依赖

[SourceAnalysisApplication](../../src/main/java/org/sourceanalysis/app/runtime/SourceAnalysisApplication.java) 两个构造器均使用 `new LocalRepositoryAnalysisAgent(store, coordinator)`；这个重载将 `artifactReader` 和 `reportRenderer` 设为 null。经该应用拿到的 CLI/Agent 调用 artifact/render 时分别返回 `ANALYSIS_RUN_ARTIFACT_READ_NOT_CONFIGURED` / `ANALYSIS_RUN_DOCUMENT_READ_NOT_CONFIGURED`。

扩展的 Agent 构造器可以注入读者，说明读者不是不存在，而是组合根没有接上。[SourceAnalysisApplicationTest](../../src/test/java/org/sourceanalysis/app/runtime/SourceAnalysisApplicationTest.java) 目前只验证 start 和 QUEUED inspect。

**确定要改：** 完整组合不能只验证“对象创建成功”；需要用已保存材料/过程做真实 artifact 查询测试。`render()` 对仅 Step07 的运行应继续返回未就绪，不应为通过测试而伪造 Step08。

### F03：设计、模块文档和代码注释的“当前状态”相互冲突

当前 [README](../../README.md)、[总体设计 §11](../DESIGN.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[模块导航](../modules/business-process-discovery/README.md)、[模型 job 文档](../modules/model-job-execution.md) 仍有“46 个过程”“v2 尚未实施/真实运行尚待执行”等当前时态。源码和实际 v2 保存产物已不同。

本次直接读取正式 v2 产物，结果如下，不采用 progress 中未经复核的汇总数：

| 项目 | 实际保存值 |
| --- | --- |
| 正式过程运行 | `analysis-run:c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035` |
| catalog schema | `repository-business-process-catalog-v2` |
| 业务领域 / 候选 / 发布过程 | 24 / 24 / 85 |
| Activity 最终处置 | 138 PROCESS_MEMBER、155 SUPPORT_ONLY、19 STANDALONE、14 UNCLASSIFIED，共 326 |
| 候选最终处置 | 18 SPLIT、4 RECONSTRUCTED、2 INSUFFICIENT_MATERIAL |
| 覆盖 / 语义状态 | CLOSED / PARTIAL |
| 多阶段过程 | 85 |
| 多 ActivityUse / 多不同 Activity 的过程 | 22 / 21；不是同一个计数 |

一条 Activity 的两个业务用法不等于两个不同 Activity。历史记录中“22 个含多个 Activity”的表述应纠正为明确口径，不能为了指标好看混用。

还存在：

- [Step02 当前状态](../analysis-steps/02-application-discovery.md) 仍称 JavaParser 未接入、选择会报 `ENGINE_NOT_INTEGRATED`；内部 Adapter 已完成，正式运行器却另有 `JDT_ENGINE_REQUIRED` 限制。不能简单改成“全部已完成”，需要按 F08 区分内部能力和外部接线。
- [RepositoryAnalysisAgent](../../src/main/java/org/sourceanalysis/app/RepositoryAnalysisAgent.java) Javadoc 称“只暴露两个操作”，实际已声明五个方法。
- 总体设计和[归并模块](../modules/business-process-discovery/repository-process-consolidator.md) 已批准 REVIEW 漏处置时保留原过程 KEEP；归并模块的“覆盖与失败”和 job 文档又笼统把遗漏写成 fatal。代码及直接测试实现了前者，不应因旧段落改回失败。

**确定要改：** 当前状态统一到已验证事实，历史结果明确标为历史；已延期的 Step08、validate/trace 不能又被标为本轮已完成。审计文档不是替换全部设计合同，后续同步只改状态和上述矛盾，不新增架构。

### F04：旧 Step07 Prompt 仍随生产资源发布

当前 [BusinessProcessPromptCatalog](../../src/main/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptCatalog.java) 用私有闭集把十种任务全部映射到八份 v2 Prompt，没有版本选择或目录扫描加载。

旧资源不能一刀切：

| 旧资源（均位于 `org/sourceanalysis/app/analysis/knowledge/`） | 实际消费者 | 正确清理 |
| --- | --- | --- |
| `business-catalog-draft-v1.txt`、`business-catalog-review-v1.txt` | 仅 `BusinessProcessSemanticFingerprintV2Test` 读取原文，验证旧任务不能误复用 | 原文移至 `src/test/resources` 同包路径，保留这个有效测试 |
| `business-catalog-merge-draft-v1.txt`、`business-catalog-merge-review-v1.txt` | 未发现生产/测试消费者 | 从 `src/main/resources` 删除，历史由 Git 保留 |
| `business-process-draft-v1.txt`、`business-process-review-v1.txt` | 未发现生产/测试消费者 | 同上 |
| `business-process-consolidation-draft-v1.txt`、`business-process-consolidation-review-v1.txt` | 未发现生产/测试消费者 | 同上 |

**边界：** 不删除 `process-group-*-v1.txt`，它属于仍在运行的旧 generate 路线；也不因文件名含 v1 删除仍有效的 SourceReference schema 或历史材料读取策略。

### F05：Mapper/XML 被发现，但没有进入业务阅读材料

[ApplicationDiscoveryExecutor](../../src/main/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryExecutor.java) 调用 Mapper cataloger，发现并保存静态 XML 候选。可是：

1. [JDT EntryCodeCollector](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java) 第 167–174 行构造 context 时，`supportingSources` 固定为空。
2. [JavaParserContextAdapter](../../src/main/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserContextAdapter.java) 第 100–113 行同样为空。
3. Step05 从导航索引恢复这些 context；[BusinessMaterialBuilder](../../src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java) 只消费其中的辅助源码，没有另读 mapper-catalog 补 SQL。

**影响：** Mapper Java 调用和声明可以进入模型，SQL 筛选、汇总、更新条件不能因此声称已提供。含 XML 的 fixture 若只断言 Mapper 调用，仍不能验证 XML 正文接通。

**分类：** 辅助源码合同有此扩展位置，但设计同时允许“无 XML 不阻断 Java 材料”。这是明确的能力接线缺口，不是当前所有 JDT 材料无效。是否现在补 SQL、在哪里补、怎样让旧固定 corpus 使用新增材料，见 D02；本轮不扩建 SQL 解析器或重新扫描。

### F06：重复工作主要在重开/验证，而不是再次运行生产算法

已跟踪正常入口：选定引擎 session 只打开一次；`BusinessFlowsExecutor` compile/project 各一次；JDT 的 Step04 记录 NOT_PRODUCED，不运行旧 Fact 枚举；JavaParser 的 Fact enumerate/prove 各一次。

仍然存在同进程中把刚生成的对象保存，再经多个 reader 重开、解析、核对后交给下一模块的路径。Fact candidate/proof、Flow compilation/projection/final publication 尤其明显。它与“同进程传不可变结果、跨进程重开时验证”的设计方向没有完全对齐。具体可核对：

- `ApplicationDiscoveryExecutor:48–59`：刚发布 profile 即通过 reader 重开。
- `FactLedgerPublicationSpecifier:110–127`：重开 candidates/proofs；`PersistedProofDecisionSetReader:121–173` 内还为 typed view 与 payload reference 再读同一 candidate module。
- `BusinessFlowsExecutor:102–112`：先为分支判断重开 Step03；`FlowCompilationModulePublisher:82–133`、`CapsuleProjectionModulePublisher:73–117` 和最终 publisher 再次读取相关上游。

**确定结论：** 可以减少冗余 I/O；不能再说是“五图不停重建”。本次没有计时实验，不能承诺节省多少分钟或百分比。

**清理原则：** 保留跨进程读取、损坏/来源错误拒绝、原子安装；只让同一次可信执行复用已经验证的不可变视图。先以调用计数证明一次打开/解析，再删除重复路径；不另建全局缓存框架。

### F07：公共执行和观察合同尚未统一

设计的 `executeStep` 是“创建新的执行并显式复用上游”；现有 `AnalysisStepExecutionRequest(runId, targetStep)` 与 `LocalRepositoryAnalysisAgent` 是把指定 QUEUED run 变成 RUNNING，不能直接表达这个设计。

另一个具体边界是：样本驱动允许 FINISHED 且无正式 `AnalysisRunOutput`；公共 `inspect` 对全部 FINISHED 都强制要求该输出，否则 `ANALYSIS_RUN_OUTPUT_MISSING`。不能用“样本故意不发布全仓结果”推导出“样本不应能被观察”。

这是运行模型与公共入口的合同差异，见 D01。不要为了让旧注释成立，再增加一套恢复或运行框架。

### F08：JavaParser 内部可用，不代表正式配置已经可以切换

[JavaCodeEngineFactory](../../src/main/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactory.java) 已保留 JavaParser Adapter，内部技术 workflow 和直接测试能使用它；但 [RepositoryRunMain](../../src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java) 第 1769–1774 行在加载统一配置后，明确拒绝非 JDT 引擎，抛出 `JDT_ENGINE_REQUIRED`。

**影响：** 按设计只将 YAML 改为 `javaEngine: javaparser`，无法走正式运行器。引擎 factory 测试通过不能代替真实 CLI 配置测试。

**确定要改：** 保留 JavaParser 既有能力，不扩展解析算法；正式组合根应使用已选择引擎，或在修好之前明确标注该入口只支持 JDT。修复需通过真实配置加载及材料路径，断言 JavaParser descriptor、原有严格图/Fact 增强和零 JDT 构造，而不是只删除这一行 guard 就声称完成。

## 4. 不是死代码：保留清单及测试判断

| 内容 | 为什么不能直接删 |
| --- | --- |
| JavaParser engine、图/Fact/Flow 和有效测试 | 内部 Adapter/factory/workflow 有真实能力及调用；用户要求保留，正式运行器应补接线而非删除 |
| `ProcessExplainer`、旧 knowledge checkpoint producer/reader、旧过程 Prompt | `PersistedBusinessRunExecutor` 和 generate 仍调用；必须先决定该入口去留 |
| `BusinessReportPublisher`、九章 reader/render 测试 | Step08 目标改造未做，用户曾明确保留；旧功能有真实消费者 |
| wire reset / 旧模块拒绝测试 | 测试旧名称是为了拒绝旧协议，不是旧包重新参与生产 |
| `BusinessProcessSemanticFingerprintV2Test` | 阻止旧 Prompt/结果误复用，是当前有效行为；其旧资源应放测试目录 |
| 旧材料 artifact policy | 显式离线重开现有固定检查点仍需要，不能因 schema 旧就删除 |
| JDT 原始研究程序和 oracle | 不属于主 Maven 生产路径，但有已批准的历史调研和对照用途，归档与否需要决定 |

本次不按测试数量设减量指标。尚未证明可整类删除的测试，不标成死测试。已有测试的主要问题之一是层次偏浅：用 fake Agent 测 CLI 路由，不能替代真实组合测试。应补或替换这一薄弱层，而不是删掉断言后称“清理通过”。

旧有限键/R0 的 `interpretation.model/proposal/registry/process` 实现已经退出，不能把过去清理计划的 78 类、14 测试再次当作当前待删除数量。当前 `stage01`、`linguan`、`codemd` 等命中主要来自 wire 拒绝常量、拒绝测试和历史记录，不是现存生产包名。

待旧 generate 正式退役时，必须联动审查而非直接整批删除的测试包括：`ProcessExplainerTest`、`ProcessGroupingTest`、`ProcessMaterialRecallTest`、`ProcessPromptContractTest`、`ParallelProcessExplainerTest`、`RepositorySummaryTest`、`ProcessKnowledgeCheckpointTest`、旧过程/九章真实 IT，以及 workflow、coordinator、复用和报告测试中仍在验证这条路径的部分。共享的 Activity、Provider、存储、来源、并发和报告渲染断言仍有价值，应保留或迁入新入口测试。

## 5. 哪些提交内容不合适

### 5.1 当前受跟踪快照的清单

基线共有 **1,569 个文件**，按 Git 跟踪文件统计，而不是把 `.workspace` 的本地运行结果算进源码：

| 区域 | 文件数 | 处理意见 |
| --- | ---: | --- |
| `src/main` | 453 | 保留当前功能；本次只清已确认旧资源 |
| `src/test` | 229 | 按消费者与行为价值清理，不批量删除 |
| `docs` | 44 | 修正当前状态、重复合同与历史边界 |
| `progress` | 811 | 历史负担最大；约 4.07 MB、53,574 行，不能直接全删 |
| `research` | 14 | 独立调研程序/测试/oracle，不是生产依赖；建议归档策略 |
| `tools` | 10 | 含在用 JDT helper 与运行配置，不是统一的临时垃圾 |
| 根文件、构建和质量配置 | 8 | 包括 README、AGENTS、POM、工具链与质量规则 |

本次在受跟踪快照中检查了 target/.workspace/node_modules、常见二进制/日志/数据库后缀、auth/env 文件名，以及常见私钥、GitHub/OpenAI token 格式；**未发现可疑运行文件或凭据**。未加单词边界的初筛曾匹配三个历史文件名中 `task-...` 的 `sk-...` 子串，经复核为文件路径而非密钥。没有展示任何秘密值。这不是 Git 全历史密钥取证，也不保证任意形态秘密均可被正则检出。

### 5.2 确定不宜作为通用生产交付的内容

- F04 的旧 Prompt：无消费者的删除，测试专用的移到测试资源。
- [.mvn/toolchains.xml](../../.mvn/toolchains.xml) 固定 `/usr/local/opt/openjdk@17/...`，不适合所有开发机。workflow 还依赖精确替换这一条 macOS 路径才能在 Linux 运行。应保存可移植模板/生成规则，实际本机路径由环境生成；不能直接删掉现有工具链配置而让构建失去 JDK17 约束。
- [运行指南](../../tools/repository-run/README.md) 的通用命令依赖某次 `.workspace/...toolchains.xml` 和具体 Homebrew patch 版本的 java 路径。历史复现实例可保留，通用启动说明不应要求另一台机器有这些路径。
- 活跃设计里的旧 `/private/tmp`、个人路径和历史结果链接必须明确“本地对照”，不能冒充新 clone 就可访问的产品文档。

### 5.3 不是误提交，而是旧制度导致的积累

811 份 progress 中，单一 `Status: COMPLETE` 为 688 份，`IN_PROGRESS` 为 61 份，`BLOCKED` 为 24 份，另有不同格式/状态。旧记录没有结束标记不表示今天仍有 61 个任务运行。

审计基线的 AGENTS 要求每个 Agent 建独立 progress 且永久保留完成记录，这是积累原因。该规则现已按用户确认改为“计划期间交接，整个计划结束后收纳结论并删除临时记录”，尚未批量处理历史文件。主实现最大的几个类也确实较大：Discovery 2,589 行、RepositoryRunMain 2,542 行、DataFlowGraphBuilder 2,242 行、AtomicCanonicalPublicationEngine 1,992 行。但行数只说明阅读成本，不足以证明它们全部“不合理”或应该拆成新公共接口。

**D04 已确认：** 不再额外建立 progress 归档；计划结束后先收纳有价值结论，再删除该计划临时记录。处理现有历史时需要辨明所属计划和未完成交接，不能只按 COMPLETE 标签或文件数量批量删除，不重写 Git 历史。

### 5.4 暂不扩大成新任务的工程边界

- 多 Maven module 当前被投影成一个 Eclipse project；多模块依赖/重复全限定名的完整保真尚不是已证实能力。引擎设计也承认这个边界，不能把“完整仓库扫描过”说成“各种模块结构都已覆盖”。这需要以后用具名跨模块 fixture 验证，不属于删死代码。
- 依赖 classpath 的绝对路径仍参与部分身份计算。同样 jar 内容换目录后是否应允许复用，需要明确内容、顺序与解析环境的身份原则；不机械删除路径校验。
- JDT Core helper 是活组件，具有主程序 Java17 与工具 JVM 隔离的实际作用。它的 jar 发现方式与打包后启动位置值得单独检验，但没有理由删除 helper 或重新实现 Java 语法分析。

## 6. 设计反推：已确认的四个方向与实施边界

### D01：新版过程入口与旧 generate/九章路线如何收口？

事实是两条活路径：

```text
business-processes → 固定 Activity → 新目录/候选/归并 → 五文件 v2
generate           → Activity → 旧 ProcessExplainer → 旧 knowledge → 九章
```

**已确认方向：** 停用旧 generate，统一新版 business-processes 主路径及公共入口、观察/产物查询；按真实消费者清理旧路径专属代码。不为了此次清理顺带开发新版 Step08。

尚未实施：停用入口和依赖迁移。未来是否以新 catalog 恢复九章是独立范围，不通过“保留旧生成路径”默认为已实现。

### D02：模型缺材料时，是否允许补读已保存但未入候选的内容？

现有 [FrozenAnalysisCorpus](../modules/business-process-discovery/frozen-analysis-corpus.md) 只开放 Activity 已引用、且在 M10 中存在的 SourceRef；[候选组装](../modules/business-process-discovery/process-material-assembler.md) 又将范围限制为当前候选活动。即使另一条已审 Activity 或另一段保存源码可能解释对象交接，当前候选也不能请求它。

例如保存材料中已有 `linkApply` 关联申请、`linkNumber` 关联订单和完成进度回写，但“存在于仓库”不等于“同一个候选模型读到了它们”。原文位置和含义已记录在[更多发现](more-findings.md)，不能把那份讨论备忘自动升级为已批准设计。

**已确认方向：** 允许模型补读候选之外的已保存 Activity 和源码，保留原活动，不重跑 JDT 和 326 个 Activity。读取请求、范围和来源的最小接线仍待详细落地。

尚需明确：是否同时纳入尚未进入 M10 的冻结 XML。用户的“允许补读”不能自动变成运行新扫描或开发 SQL 编译器的授权；这部分有额外输入和版本变化，不能用清理死代码名义实施。

### D03：严格去重与业务生命周期串联是两个不同职责

[requireLosslessMerge](../../src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java) 要求名称/目的/范围和归一化阶段序列一致。它只去重，不会把“申请”“订单”“入库”三个局部过程写成一条更长的业务链。这是当前设计明确要求，不是再加几个 Java 条件能修好的 bug。

另有一个具体张力：给归并模型的输入去掉了阶段等嵌套来源字段，但 Java 的 `normalizedStages` 相等检查仍包含这些来源字段。模型即使看到相同业务内容，也未必能判断隐藏字段是否允许合并；不满足就 fatal。不能要求模型负责它看不到的纯机械条件。

**建议：** Java 完成完全相同记录的确定性去重；模型负责业务相关、父子、替代和必要的上层过程表达。若要新增上层生命周期，应复用过程重建职责，引用现有局部步骤，不通过取消校验直接拼数组。不新增逐句证明或中文蕴含判断。

**用户已选择最小修改：** 模型建议的合并不满足机械无损条件时，保留两个完整原过程并说明“未合并”，不因此让全仓失败；不额外编造二者的关系、不增加模型调用。未知 ID、损坏输入、非法引用等其他错误不因此被放行。这是已确认的合同调整，代码尚未修改。

跨局部过程的上层串联仍是后续业务设计，不等于本次“不能合并就保留”的处理。

### D04：计划交接记录何时删除，哪些配置应该提交？

**已确认并写入 AGENTS 的规则：**

- 每个 Agent 在 plan 进行中维护自己的 progress，明确所属计划；一个子任务完成不代表可以立即删除交接记录。
- 整个 plan 结束时，重要决定进入正式设计，遗留问题进入 backlog，验收结果及产物位置进入交付记录；随后删除该 plan 临时 progress，不另建归档目录。
- 已提交过的记录可从 Git 历史找回。现有历史需先确认所属计划及是否仍承担交接，不按全部 COMPLETE 文件做无差别删除。`progress/TEMPLATE.md` 保留。
- JDT 材料、Activity、DRAFT/REVIEW、来源与最终文档是运行产物，不属于 progress 清理对象；JDT 历史研究代码也不在这项临时交接清理中。
- 团队共用、简洁稳定的 AGENTS.md 应提交；可移植的 Maven 构建配置应提交；本机安装路径、个人设置和凭据不应作为共享配置原样提交。
- 当前 `.mvn/toolchains.xml` 的 Homebrew 路径仍未迁移。替换时一起接通模板/环境输入、POM/workflow 和运行说明，不能直接删除有效配置使构建失效。

本轮只完成规则同步，没有批量删除 811 份历史记录，没有修改实际 Maven/CI 配置。

## 7. 后续清理顺序，不扩大范围

1. **本轮已完成 F04 资源整理**，保留有效旧版本拒绝测试；这是不改变业务行为的确定性减法。
2. 统一 F03 当前状态和合同例外；不重新推导流程，不把 PARTIAL 改称业务完整。
3. 按已确认 D01 修公共接线、样本观察并停用旧 generate，再按实际消费者删除旧生产类/测试/注册。
4. 对 F06 只做同进程重复读取的定向削减；用调用次数和重开损坏测试验收，不引入新存储框架。
5. 按已确认 D02/D03 补最小接口/行为设计和直接验证，不将尚未选择的 XML/上层过程扩展混入。
6. 按 D04 核对已完成 plan、收纳必要结论并删除其临时 progress；不另建归档，禁止无差别 `git clean`、删 `.workspace` 或重写 Git 历史。

## 8. 本轮变更与验证

本轮完成了审计及 F04 的安全清理。随后的讨论已确认 D01–D04 方向，并同步 progress/配置提交规则；不代表 F01–F03/F05–F08 或业务行为改动已经实现。

- 删除 6 份确认无消费者的旧 Prompt；从生产目录移除另外 2 份测试专用 Prompt，并原文保存在同包 test resources。
- 两份迁移资源与基线 Git blob 逐字节相同；旧版本误复用测试保留，未删除测试用例或减弱断言。
- 写入本文及各 Agent 的审计/清理记录。当前状态文档的全面同步仍是 F03 后续工作，不把本文当成已改过全部设计。
- 未修改生产 Java、业务提示词 v2、模型配置、客户运行产物或历史 progress；没有提交或推送本轮修改。删除资源可从 Git 基线恢复。

| 本轮验证 | 结果 |
| --- | --- |
| 固定提交、生产/测试/资源消费者核对 | 完成；两个独立审计与主任务复核 |
| 正式 v2 catalog/coverage 重开 | 85 过程、326 条最终处置；CLOSED/PARTIAL；统计口径见 F03 |
| 两份资源与原 Git blob 比较 | 字节一致；main 副本不存在 |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessPromptV2ContractTest,BusinessProcessSemanticFingerprintV2Test test`，使用 JDK17 | **5 个测试通过，0 failures/errors/skips**；未运行无关测试 |
| `git diff --check` | 通过 |
| 新增文档的文件链接、尾随空格检查 | 通过 |
| 客户扫描、JDT、真实模型、整套 CI、Git 全历史密钥审计 | 本轮未运行 |

没有执行 `clean`；旧 `target/classes` 或旧 jar 可能仍有此前复制的资源。本次验证的是当前源码资源布局、v2 选择和 v1 误复用拒绝，不宣称旧构建缓存已经清理。

没有把过去 CI 的通过记录当成本轮验证，也没有因静态审计没有发现问题就宣称整个系统正确。
