# 源码准备、问题结果与持久排除实施计划

> **For agentic workers:** 用户已于2026-09-25批准实施。步骤0–8的本轮限定范围已完成；最终直接相关测试168/168、质量BUILD SUCCESS（Spotless 630 files clean、SpotBugs 0、PMD 0），见 `progress/source-preparation-execution.md`。资源上限停止枚举后的未知尾部不能靠派生排除已知文件证明完整；恢复只允许调整上限或在NEW请求预声明排除后NEW全范围，该合同及3项新测试已复验。真实业务模型、客户JDT和下游业务生成均为0；旧Step05/M10材料在`PREPARED_SOURCE`下故意fail closed，准备版直接生产新Step05材料未实现，跨版本增量复用仍留backlog。

**Goal：** 一个源码准备命令能接入普通目录或固定 Git 提交，保存逐项结果，按用户决定重新准备/排除具名范围；后续公共读取和派生材料进入新执行时执行排除与来源版本检查。

**Architecture：** 复用唯一CLI、现有Agent/运行registry、Git捕获与canonical store；新增内部来源枚举/核验结果、明确准备意图和共享版本检查。不另建运行框架，不改后续分析算法。

**Tech Stack：** Java17、现有Picocli/Jackson、JDK文件API、受限Git plumbing、现有canonical JSON/store、JUnit及现有替身模式。无新增解析器、数据库、LLM或网络依赖。

**Spec：** [步骤合同](../analysis-steps/01-verified-source-inventory.md)、[内部模块](../modules/source-preparation/README.md)、[数据/保存](../modules/source-preparation/contracts-and-storage.md)、[CLI/Skill](../modules/source-preparation/cli-and-skill.md)。

**Global constraints：** 正式source-code目录；保留脏工作树及旧运行；`more-findings.md`字节不变；只拆源码准备CLI；不重跑JDT/Activity/过程；所有新行为用fixtures或scripted验证。GPT-6 Sol/xhigh负责方向、调度、设计、debug及复审，Terra/xhigh编码，Luna/xhigh直接测试；最多两个工作Agent，一个重型构建。

**Review focus：** 不能把“报告已保存”当来源可用；未知子树不能计零；排除不能被旧内嵌源码绕过；历史只读不能被新版本检查全部封死；删除行索引不能破坏旧 exact 读取。

## 0. 实施前基线

批准时基线：Git固定捕获、原Step01成功发布、公共文本reader、运行状态和CLI；当时不存在新prepare-source、目录源、部分清单、持久排除。实施进度见 `progress/source-preparation-execution.md`；当前HEAD及未提交范围以实际实施时重新检查为准，不套用基线测试结论。

- [x] 读取正式设计和本计划、适用AGENTS；`git status --short`核对当前Activity改动，保护无关`docs/research/`和本机输出。
- [x] 为本计划每个工作Agent建立独立progress；只登记本轮文件。
- [x] 记录受保护文档、旧来源/Activity/过程的具名引用；不用复制或改写模型原文来备份。
- [x] 建立`codex/*`实现分支；有重叠未提交源码先核对归属，不reset、不强推、不擅自提交历史Activity补丁。

## 1. 先冻结结果、问题和版本合同

**修改点：** `analysis/inventory/`新增SourcePreparationResult/SourceEntry/SourceIssue/PreparedSourceReference及typed operation；`runtime`定义SelectedSourceBasis；`capture/localgit/RegisteredSourceCapture`等的公共视图支持带标签origin；保留旧严格构造/解析分支。

**新增直接测试：** `analysis/inventory/SourcePreparationContractsTest.java`。

- [x] RED：100个已发现文件中99核验、1不可读，断言NEEDS_DECISION；文件集合不丢项。
- [x] RED：目录无法列出时总数为null、unknownSubtrees具名；不能以99/99声称全仓完成。
- [x] RED：显式排除解决局部问题后READY_WITH_EXCLUSIONS；全排除/全媒体为NO_ANALYZABLE_TEXT。
- [x] RED：请求错误/整体来源错误/存储失败不能被EXCLUDE转成READY。
- [x] GREEN：最少immutable records与有名构造器、状态计算和错误分类；不加入扫描或模型代码。
- [x] 同步字段、null、集合排序、正常目录ENUMERATED_DIRECTORY和新fileId/sourceVersionId配方；运行直接测试后进入生产者。32项直接测试通过，5项审查问题经两轮限定修复关闭；这里只完成纯合同，不代表实际来源或receipt已经核验。

命令（实施时）：`mvn -t .mvn/toolchains.local.xml -Dtest=SourcePreparationContractsTest test`。

## 2. 普通目录和Git逐项捕获

当前状态（2026-09-25）：步骤2目录及固定Git读取、私有暂存和旧Git捕获共享对象会话已完成限定复审；修复Git writer关闭失败、字节额度恰好耗尽时的零字节blob、未知子项属性失败三个已证明缺口。定向Spotless后六个直接相关测试类合计25/25通过，`git diff --check` 为0。正式四文件发布尚属步骤3，不能因本步完成宣称整个源码准备可用。

**新增：** `capture/SourceOriginReader`及目录实现；包名按现有源码层次收敛，不复制Git协议实现。**修改：** `capture/localgit/LocalGitCommitCaptureAdapter`、注册/快照读写；新注册v2与旧v1分支。

**测试：** 新`DirectorySourcePreparationTest`；扩展`LocalGitCommitCaptureAdapterTest`。

- [x] RED：无.git目录的Java/XML/Vue/点文件/target/node_modules均按原字节保存；.git具名跳过。
- [x] RED：文件/目录链接不跟随，链接外哨兵文件读取次数为0；不支持压缩根、设备/FIFO，不解压目录内普通压缩文件。
- [x] RED：子目录枚举/文件读取失败继续兄弟；根枚举失败不进入子树；使用可注入reader失败替身，不能只依赖chmod（管理员执行可能绕过）。
- [x] RED：用屏障确定性改变读前后长度、mtime或文件身份，得到明确变化问题；实际观测与未知原因分别保存。首次单次读取没有预期内容hash，不声称可检测所有同长度变化；已有保存副本的hash不一致在重开/继承测试核对。
- [x] RED：资源控制在读取时生效，先前已核验记录保留，剩余标明未检查；无全仓bytes提前入内存。资源上限停止枚举后的未知尾部不得由派生排除已知触发文件消除；仅允许NEW全范围恢复，3项新增测试及最终168项套件通过。
- [x] RED：Git同提交读取不见工作树修改；链接跳过，submodule具名未支持；不fetch、不执行hooks。
- [x] GREEN：流式捕获与逐项问题，复用现有安全Git命令。只catch已分类问题，不用RuntimeException全部转CAPTURE_IDENTITY_INVALID。
- [x] 核对新origin在文件身份/模式信息中的表达，不向目录源伪造Git mode或commit。

直接测试：`-Dtest=DirectorySourcePreparationTest,LocalGitCommitCaptureAdapterTest`。

## 3. 准备核验、四文件发布和删除行索引

当前状态（2026-09-26）：private frozen archive、M1/M2/M3 v3、M3/Step 的精确四文件、v2/v3 receipt 选择白名单和 strict reopen 已实现；新路径不生成行索引，正式 `prepare-source` 已接线真实 controls 与 input/output 重叠预检。此前发布/身份22项和随后步骤4/6边界22项直接测试通过；步骤8已在最终源码状态以168项定向测试验收。

**修改：** `VerifiedSourceIndexer`、`VerifiedSourceFile`、`VerifiedSourceIndexModulePublisher`、`VerifiedSourceInventoryPublicationSpecifier`、executor，以及对应reader；新增PreparedSourcePublisher/Reader。`artifact/CanonicalArtifactPolicyRegistry`与source-only policy文件一起接线。

**测试：** 扩展`VerifiedSourceInventoryPublicationSpecifierTest`、`PersistedVerifiedSourceTextReaderTest`，新增`PreparedSourcePublicationTest`。

- [x] RED：原子真实store安装四份payload及receipt，含一个UNAVAILABLE时也能查询完整问题；readiness却拒绝源码消费。
- [x] RED：没有错误时空issues JSONL可重开；空目录/根失败有明确汇总，不造空的成功源码句柄。
- [x] RED：准备计算与新产物没有lineStartByteOffsets/lineIndexDigest；历史v2完整fixture仍严格读取。
- [x] RED：新origin、文本/media、正常/未知目录、排除项和继承记录保存重开不丢字段；空目录/只有排除时有真实issue，receipt gapRefs与SUCCEEDED_WITH_GAPS一致。
- [x] RED：轻量配置产生真实profile/toolchain/schema/budget refs；query使用已保存controls，不要求JDT/模型配置，不因当前工具版本变化重算旧身份。
- [x] GREEN：本步骤实现准备controls的生成/保存及重开；第6步只接正式CLI，不把本步骤依赖留到最后。
- [x] RED：缺文件、改hash、非法ref、错policy、安装中断被拒绝；旧policy内容ID不因新增schema被改写。
- [x] GREEN：移除新准备路径的行索引计算；新M1/M2升v3，新正式producer v3；旧v2 reader及其所需历史行索引按准确版本单独保留。
- [x] GREEN：存储错误返回实际保存状态；不声称失败存储还能可靠写最终报告。

直接测试：`-Dtest=PreparedSourcePublicationTest,VerifiedSourceInventoryPublicationSpecifierTest,PersistedVerifiedSourceTextReaderTest,CanonicalArtifactPolicyRegistryContractTest`。

## 4. 单文件/子目录刷新和排除版本

**新增测试：** `SourcePreparationRevisionTest`；生产修改在SourcePreparationService及版本计算/基础读取中。

- [x] RED：A版本的x文件变化，REFRESH x生成B；其它条目实际读取计数0，继承字节不变；A不被覆盖。
- [x] RED：刷新目录替换子树；缺失文件、类型变化不静默消失；仍无法读取则保持NEEDS_DECISION。
- [x] RED：EXCLUDE文件/未知子树不读被排除内容；可用兄弟保留；未知总数不变成已知。
- [x] RED：基础结果已完整保存但原目录消失/不可读时仍可EXCLUDE；不得被NEW的根目录存在检查拦住。
- [x] RED：排除在显式派生链继承；配置原exclusions为空、CLI排除a后用原配置刷新b，累计排除a仍存在；REFRESH不能重新纳入。
- [x] RED：同逻辑名称但规范根目录变化时拒绝派生；NEW使用明确新配置，不偷偷继承/清除并冒称接续。
- [x] RED：错base、错origin/commit、危险路径、重叠targets、refresh/exclude混用在开始前失败；旧结果字节不变。
- [x] RED：Git REFRESH只能原提交；改提交/工作树混用被拒绝。
- [x] RED：继承遇到保存blob损坏必须报告；刷新得到相同bytes也产生新版本，旧checkpoint不能自动升级。
- [x] GREEN：实现明确范围的不可变派生，不增加依赖影响分析、自动重算或恢复系统。

直接测试：`-Dtest=SourcePreparationRevisionTest`。

## 5. 公共读取和派生材料版本检查

当前状态（2026-09-26）：配置来源、request-v3/output-v7、prepared 四文件 fresh reopen、LEGACY 实际 Step01 元数据身份投影以及 Step05/M10 新执行门禁已接线。旧 Step05 v4/M10 v3 材料不保存准备版 publication 与有效排除，因此 `PREPARED_SOURCE` 必须拒绝旧材料；这不等于支持新准备版本的材料复用。M10 每次重开均核对 receipt 的 `upstreamArtifacts` 与 state 的 `businessFlows` publication payload refs。新增`SourceExclusionConsumerContractTest`以真实READY_WITH_EXCLUSIONS发布/重开核对Java/XML纳入与排除，再经`VerifiedJavaProject`、`PersistenceAnalysisRequest`和`MapperXmlResourceView`消费；单项1/1及最终直接相关套件168/168通过，没有启动JDT或SQL解析。Step5消费者证据已齐，第19.1项按本轮fail-closed范围关闭；准备版直接生产新Step05材料不在本轮实现范围。

**修改：** `VerifiedSourceTextDocument/Set`、`PersistedVerifiedSourceTextReader`、`VerifiedJavaProject`的来源属性适配；新增共享SourceBasisGuard。接线点为`PersistedTechnicalRunExecutor`、`SourceAnalysisExecution`、Activity执行入口、`DefaultBusinessProcessDiscovery`及job/catalog reuse入口。各reader历史重开算法不复制。

**新增测试：** `SourceBasisGuardTest`、`SourceExclusionConsumerContractTest`。扩展已有`SourceAnalysisExecutionFrozenSourceInputTest`及checkpoint测试中直接相关断言。

- [x] 先实现配置来源解析、SelectedSourceBasis绑定和持久传递，再连接guard；第6步消费该路径，不临时从材料本身反推expected。
- [x] RED：新范围reader枚举/按路径都拿不到排除文件；普通目录映射snapshot/ref/scope/capability/controls保持准确；JDT project和PersistenceAnalysisRequest用内存fixture，不启动JDT或SQL解析。`SourceExclusionConsumerContractTest`真实发布/重开READY_WITH_EXCLUSIONS，纳入与排除Java/XML，并核对prepared reader、`VerifiedJavaProject`、`PersistenceAnalysisRequest`及`MapperXmlResourceView`；单项1/1通过。
- [x] RED：Step05含旧方法正文/Resource.rawSource，选择排除后的B版本时在Activity projector/Provider前拒绝；工具和模型创建/调用均0。
- [x] RED：历史M10片段及旧Activity进入Step07也在catalog之前拒绝；不能等目录模型调用后才发现原文不匹配。M10 receipt upstream refs与state businessFlows publication refs逐次核对的新增2项已在定向31/31中通过。
- [x] RED：同commit不同排除仍拒绝；同仓同文件名不同版本仍拒绝；expected只能来自已绑定配置来源，不能从材料自己反推；精确匹配仍能进入scripted执行。
- [x] RED：旧artifact/Activity/过程重开与确定性历史渲染可用；不要求它等于当前选择的B版本。
- [x] RED：旧job/catalog复用不能绕过basis检查；Java seam和正式CLI均覆盖，不只测试参数转发。M10逐次重开一致性已修复，正式入口与复用门禁包含在最终168项直接相关测试中。
- [x] GREEN：只做共同版本/范围检查与不可变view接线，不改JDT导航、持久化、Activity/Process业务算法。
- [x] 将这些测试引用回填持续backlog第19.1；必要消费者边界已有直接证据，跨版本增量复用仍留第19.2项。最终168项同版复跑见步骤8。

直接测试：`-Dtest=SourceBasisGuardTest,SourceExclusionConsumerContractTest,SourceAnalysisExecutionFrozenSourceInputTest`。

## 6. 正式CLI、配置和运行结果

**修改：** `SourceAnalysisCli`、`ConfiguredSourceAnalysisRuntime`、`SourceAnalysisExecution`的早期操作路由；新增轻量loader。`AnalysisRunRequest`/持久化request、`AnalysisExecutionIntent`/`AnalysisStepExecutionRequest`、`RepositoryAnalysisRunCoordinator`、`LocalRepositoryAnalysisAgent`、`AnalysisRunOutput`、`FileSystemAnalysisRunRegistry`、artifact查询key按新分支接线。

**测试：** 新`SourcePreparationCliTest`与`SourcePreparationRunOutputTest`；扩展`SourceAnalysisConfiguredEntryPointTest`、`ModelBatchAnalysisRunOutputTest`历史断言。

- [x] RED：无Git工具/模型登录/JDT配置的DIRECTORY YAML，经真实main组装、Agent、store完成准备并停止；所有后续执行计数0。
- [x] RED：请求v3准备分支不需要虚假的sourceRegistration/Prompt/candidate；旧v2构造/读取不退化；分析config-v4来源分支及旧v2/v3的LEGACY绑定进入同一个guard。
- [x] RED：局部问题结果先保存output，再FAILED；inspect/artifact仍读到完整结果；render不会冒称已有报告。
- [x] RED：NEW/REFRESH/EXCLUDE参数、base解析及配置校验完整；重复结束run不可再执行。
- [x] RED：输出失败/请求错误输出JSON与退出码一致，无不存在的outputRef；未完成临时目录查询不显示成功。
- [x] RED：v7新output可重开，v3–v6历史业务输出仍严格读；SOURCE_PREPARATION不得混入Step05/Activity/report字段。
- [x] GREEN：同一公共Agent路由，不复制运行主类或建立第二套生命周期。
- [x] 添加薄启动脚本及portable使用例子，不提交Java绝对安装路径、客户源码或输出。

直接测试：`-Dtest=SourcePreparationCliTest,SourcePreparationRunOutputTest,SourceAnalysisConfiguredEntryPointTest,ModelBatchAnalysisRunOutputTest`。

## 7. 源码准备Skill与文档同步

**新增：** `skills/source-analysis/SKILL.md`（模块内共享源文件，非自动全局安装）。遵守创建Skill的专门指导；不复制Java算法或植入后续步骤命令。

- [x] 每条说明以实际JSON字段和artifact为依据；列明NEW/接续区别、未知范围、可选处理和单次结束位置。
- [x] fixture演练：命令非0但有完整报告；命令0但范围有明确排除；强杀无receipt；Codex都不能乱报全仓完成。
- [x] 检查真实命令、配置、退出码及输出示例与程序一致。示例路径不含本机用户名，人工样例明确标记。
- [x] 同步主设计、步骤、模块、使用说明、作用域AGENTS和当前实施状态；补充文档只存决策依据和backlog，不成为第二套合同。

## 8. 集成验收与交付

本轮范围状态：**COMPLETE**。验收是直接相关fixtures与测试的组合；资源上限中止枚举后必须NEW全范围（调整上限或在NEW请求中预声明排除），不能用基于中止结果的派生EXCLUDE宣称READY。不声称运行真实客户仓库、JDT或模型。

- [x] 同一最终源码状态的临时fixtures覆盖NEW→文件失败→明确排除→按范围读取→拒绝旧派生材料的新执行，以及REFRESH→新版本→历史只读；没有真实模型、JDT或客户代码。
- [x] 直接测试注入目录枚举失败、文件读取中变化、输出写入失败与进程中断；新增3项验证资源上限问题只许NEW、派生排除不消除未知尾部，以及NEW预声明排除可完成剩余范围。
- [x] 同一最终源码状态串行跑本轮新增/直接相关测试：168/168，0 failure/error/skipped，BUILD SUCCESS（2026-09-26 05:52:13 -02:30，52.568s）；准确命令见交付进度文件，不借用旧CI报告。
- [x] Java17编译/测试、JDK21+质量宿主；`mvn -o -t .mvn/toolchains.local.xml -Pquality -DskipUTs -DskipITs spotless:check verify` 在同一最终源码状态BUILD SUCCESS（05:51:09，Spotless 630 files clean、SpotBugs 0、PMD 0），未运行real-jdt-it。
- [x] `git diff --check`、文档链接/版本矩阵/CLI help与受保护hash按本轮交付记录核对；未修改`more-findings.md`或旧保存产物。
- [x] 交付范围检查：排除.workspace、日志、捕获内容、凭据、本机toolchains和无关Activity文件；此项不代表已提交或推送。后续Git交付需用户另行授权并遵循既定PR流程，不强推、不以远端CI代替本地验收。
- [x] 最终验证记录已补齐；第19.1按必要消费者与fail-closed边界关闭，跨版本增量复用继续留第19.2 backlog。

## 9. 完成与不完成的判据

**本实现完成：**普通目录/Git准备、逐项结果、用户明确刷新/排除、正确历史重开及最小下游检查经正式入口验证；Skill只调用准备并准确说明实际结果。

**不能声称：**代码能编译就已完成；保存排除表就后续全生效；一个文件刷新后相关业务已更新；没有监控告警就证明原目录未变；旧结果仍可查看就能跨版本复用。

真实业务模型调用为0，客户JDT调用为0，Activity/Process重新生成次数为0。任何超出此范围的需要先说明具体原因、替代方案并征求用户决定。
