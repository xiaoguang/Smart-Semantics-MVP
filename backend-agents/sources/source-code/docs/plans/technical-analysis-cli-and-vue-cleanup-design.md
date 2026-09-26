# 前五步技术链：依次清理与接线详细设计

状态：设计完成度以本页末尾核对表为准；**生产实现未开始**。本页列修改顺序、影响文件/接口、保留/删除边界和验收，不是把旧计划重新执行一遍。总体目标由[技术材料采集设计](../modules/technical-analysis/README.md)拥有。

## 1. 本轮要改的事及明确不改的事

修改：准备版源码接入技术运行器；Step02自动准备Maven依赖、按模块/目标JDK投影和诊断就绪；Step02正式Vue/HTTP关联；02+03/04/05三个操作；跨运行准确引用；Step05前端材料；退役代码和陈旧设计清理。

保留：源码准备算法、现有JDT/Core导航/缓存/候选/多态能力、Spring入口识别、MyBatis安全XML/SQL算法、后端材料原文、运行/存储框架、所有历史材料和业务结果。

不做：第二个Java parser、Fact候选/Proof、重复Java调用解析、Step03语义候选裁决器、日志/异常专用业务过滤、SQL/Vue业务语义、Step06/07/九章生成、客户构建/扩展/生成器执行或数据库、未允许仓库下载、跨版本增量复用、自动恢复。配置允许的常规POM/JAR自动取得属于本次设计，不再禁止一切依赖下载。

用户已要求彻底清理退役路线。这里的“彻底”指：不再有生产入口、配置路由或活跃设计要求它运行；**不以清理为由破坏用户要求保留的历史文件查询/重开**。确认无消费者的历史专属实现也删除，不因类名有Reader就永远保留。

## 2. 当前代码核对，不凭名字判废弃

| 位置 | 已核实行为 | 必须改变/保留 |
| --- | --- | --- |
| `adapter/cli/SourceAnalysisCli` | 已有prepare-source；execute-step仅活动/过程；旧plan-materials全链 | 新增三子命令及明确意图；验收后删除旧命令，用户已确认 |
| `adapter/cli/SourceAnalysisExecution.executeMaterialsOnly` | 旧Git捕获/技术全链；新basis准入不等于此链已接通 | 去掉新命令中的重复capture/Step01；使用准备版reader |
| `runtime/PersistedTechnicalRunExecutor` | 一个JDT session执行02–05；sourceLevel调用位置为硬编码17 | 将生命周期止于collect-code的03；language level来自实际编译环境，不混同宿主Java17 |
| `runtime/TechnicalAnalysisWorkflow` | discovery和后续Java/持久化/材料已有清晰调用点 | 拆编排入口、复用算法；删除迁走后无调用的全链wrapper |
| `analysis/discovery/ApplicationDiscoveryExecutor` | session.catalog→入口/Mapper目录 | 加就绪/前端结果；原Java发现规则复用 |
| `analysis/code/VerifiedJavaProject` | 校验已列JAR存在/hash；当前一个classpath | 接每模块真实解析环境，避免扁平错误；不是已有依赖解析器 |
| `analysis/code/jdt/JdtProjectSession` | 一个`.project/.classpath`；JavaSE容器名由sourceLevel生成 | 按模块project引用及实际JDK映射，仍一个LS会话 |
| `JdtSyntaxHelperClient/Protocol`、工具`JdtSyntaxReader` | v2单环境；setEnvironment最后参数true加入工具VM平台 | v3按模块环境及目标平台，缓存含环境；语法/调用目标算法不改 |
| `analysis/code/jdt/JdtLanguageServerClient` | 就绪标记/导航RPC缓存；publishDiagnostics当前空实现 | 仅补诊断收集/就绪接口；现有导航方法不重新设计 |
| `analysis/graph/ProgramGraphsExecution` | 当前JDT index publisher，虽叫Graphs仍在用 | **保留**，改来源/owner/basis接线；不删除或恢复五图 |
| `analysis/persistence/DefaultPersistenceAnalyzer` | XML、binding、JSqlParser、限制 | **保留算法**；独立操作调用它 |
| `analysis/persistence/publish/PersistenceMaterialPublisher/Reader` | publisher推导source run为输出、检查同run | 显式目的run＋准确lineage；新旧版本分开读取 |
| `analysis/material/DefaultCodeReadingMaterialBuilder` | 由已读Java/持久化索引组包 | 增前端选择，现有后端完整性不退化 |
| `analysis/material/publish/CodeReadingMaterialPublisher/Reader` | 同run约束及引用式重开 | 跨run/source basis/前端refs；禁止读取时重分析 |
| `runtime/AnalysisRunRequest/Output`及registry | v3/v7；登记后结束，终态不能继续执行 | 新技术请求/输出分支；三个新run，非同run续跑 |

表中包路径均相对`src/main/java/org/sourceanalysis/app/`。实施前重新检查当时工作区差异，不能依据本表覆盖仍在进行的Activity/source-preparation修改。

## 3. 依次实施，完成一条再清理旧连接

### C0：保护基线与确认剩余选择

记录HEAD、远端及已有未提交文件归属；源码准备和Activity补丁不算成本轮新增成果。记录旧326/新418 Activity、固定技术材料、原模型记录和`more-findings.md`校验值。文档任务不自行提交这些代码。

本轮已确认：常规Maven依赖自动解析/按配置仓库取得；特殊扩展/生成器/构建脚本另行确认，仍未就绪不导航；新三命令验收后删除plan-materials，由Skill串联。具体项目的root/profile/JDK/仓库权限是将来运行输入，不由设计任务替用户授权。工具可行性不是让用户挑parser，先做下述有限验证；若不通过再报告具体问题。

退出：范围和用户选择写到所属设计，不靠实施者猜；排除运行数据、凭据、本机配置和无关研究目录。

### C1：先验证三处新技术风险，再大范围接线

**C1-D 声明式Maven与模块环境：**首个原型锁定Maven3.9.15配套Settings/ModelBuilder/Resolver provider及Resolver1.9.27，固定工具进程；不各自挑最新版。自有父子POM、聚合根、BOM、Profile、传递依赖/冲突、provided/exclusions和两模块fixture，与同版本受信任参考工具输出对照。验证本地命中、允许仓库下载、未允许仓库/重定向/凭据失败；测试仓库使用本地fixture。旧缓存同坐标reactor JAR不得覆盖同源模块。扩展/processor/wrapper执行标记必须为零，文件激活不得读快照外宿主，未知编译前插件不能被静默忽略。

用每模块的有效模型构建独立JDT环境，验证不同版本/同名类型不泄漏；不能只证明单模块就宣称多模块通过。完整声明式支持范围由[依赖准备](../modules/technical-analysis/dependency-preparation.md)规定，未知特殊构建阻断。若所选API无法正确处理某种布局，保存具体失败并讨论缩小范围或改装配；不自己实现Maven继承/版本调解器。

**C1-J Java诊断协议：**在锁定JDT版本上用自有fixture证明实际编译classpath、目标JDK和每文件诊断能一致取得。缺类型→补齐后恢复；无消息不能当完成；无法证明完成条件返回UNDETERMINED。只用现有Step03调用查询做回归，不改候选算法。

分母必须先由有效源码根/source set确定，验证`expected = checked ⊎ unchecked`；缺失回调、过期回调、依赖加载前回调均不能满足COMPLETE。项目级与逐文件诊断的可靠终态条件必须在实际锁定版本得到证实；不使用固定sleep替代协议。

同项验证目标平台：LS的execution environment确实映射指定JDK；Core不采用工具VM bootclasspath冒充目标。目标8/17、较新工具JVM的API负例须在两侧一致失败，合法目标API通过。不能只把sourceLevel从17改成8便说完成；helper v3和平台映射以该原型为依据。

**C1-V Vue静态关联：**固定Node helper与成熟parser，跑真实冻结Vue/共享组件/mixin/API封装的只读fixture，取得方法、URL、参数条件和完整中间位置。选择来源默认配置与运行时覆盖要分开。额外最小fixture验证同名方法覆盖、两实例不同this.url、多候选/动态URL、循环/边界。

不在客户工程里安装或执行客户脚本，不拿LLM当parser。输出有限工具可行性记录，固定工具版本、支持模式及限制。没有通过，就不承诺“任意Vue链已解决”，也不扩成全JS解释器。

退出：Maven标准规则/模块环境有对照，真实前端例可静态连接，平台与诊断完成条件可核验；未支持的情况能准确报告。剩余技术不确定性没有隐藏在成功分支。C1只验证工具/接缝，不绕回开发新的Step03。

### C2：先冻结来源、运行与版本合同

实现[运行合同](../modules/technical-analysis/cli-and-runtime.md)的技术配置v1、request v4/output v8分支及准确上游绑定。

- `SelectedSourceBasis`复用已有类型；新技术路径不制造旧Git注册或假Step01。
- 组合根解析prepare-source run并严格重开；公开请求不含filesystem Path，不要求Provider/Prompt。
- 源码版本、Java分析基础、输出run ID是三个不同概念；字段及读写不可复用一个`sourceRunId`混装。
- 完整typed publication refs贯穿请求、保存器、reader和registry；开始工具前绑定。
- collect在开始前绑定准备配方和权限，不伪造尚未解析的环境；运行中生成实际环境，先冻结并写私有阶段记录，计算basis后进入JDT；诊断结束/确定阻断后一次安装module5两正式文件和receipt。私有记录不进入availableOutputs。输入不原地修改，用户新选择须新run。
- 新run目的地址明确，各阶段不可原地覆盖上游。旧v3/v7与历史版本严格读取。
- 预期阻断返回带已安装refs的技术output，Agent先登记再置FAILED；不沿当前“抛异常则无output”路线丢报告。新增技术artifact键和FAILED具名查询白名单，准确规则见运行合同§3.2。

先写错来源、错owner、终态重新激活、非法组合的直接失败测试，避免CLI结束才发现同run假设。

退出：R0/R1/R2/R3身份示例在真实store fixture下闭合，无工具/模型初始化；老结果仍可观察。

### C3：接Step02自动依赖准备、环境与JDT会话

增加Step02内部`JavaDependencyPreparer`，使用固定Maven模型/解析库，按配置缓存与仓库策略自动完成常规环境；保留显式PROVIDED输入，同一核验不降级。标准库配置/网络读取/外部parent/BOM/模块关系按详细合同处理，不执行客户扩展/生命周期。其完整或部分实际结果交`JavaAnalysisReadiness`，核验模块/source set/JDK/profile/生成内容范围并收集结构化诊断。

改`VerifiedJavaProject`的每模块环境输入，去掉运行器硬编码language level；LS项目引用/平台映射与Core helper v3一起更新，文件按模块解析后组合catalog。JDT工具JVM与源码目标JDK分离；客户依赖不加入helper自己的可执行类路径。来源投影只取prepared reader有效范围。没有模型参数，JDT不自行联网build；依赖联网仅在已允许的准备器发生。

module5一次安装实际编译环境JSON与就绪JSON，原始解析/下载/诊断和定位保存在私有区。失败返回问题code、对象、原因、允许动作及实际refs；配置权限不能由模型文字覆盖。CLI/Skill确认接力的正式验证在C9，不留只写类无消费者的接口。

保留现有`catalog()/collect()`及查询缓存；新就绪报告与实际会话绑定，检查通过后不换classpath。阻断时完整报告可查询，不能伪造零入口成功。

退出：工具解析的每模块完整清单有依据，缺依赖/损坏/未知Profile/特殊扩展/诊断不全可复现且报告可查；不是承诺历史18个JAR足够。既有Step03功能回归未改算法。代表性真实验收另需明确执行授权，文档审查不是授权。

### C4：接Step02前端关系及持久结果

实现`FrontendHttpDiscoverer`的有限静态规则与Node工具协议，配置frontend关闭返回DISABLED；启用时按固定范围枚举/处理/报告。

- 精确保存组件使用上下文、mixin/import/封装、请求分支、参数用途和部署映射依据。
- 只和既有入口的route/methodCondition/已知约束匹配；未知保留，禁止同名硬连。
- 保存完整前端索引及发现分母，完整Step02引用就绪报告和前端索引；Java未执行则不假造“后端未匹配”。
- Step02 entry行v3字段不为前端关联而改变；新增关联按entryId外连。

退出：真实采购请求的每个中间节点可查；一页面多个请求、一入口多页面、动态/排除/不支持均有记录；旧后端发现不退化。

### C5：把02＋03收口为collect-code

改现有技术运行器：重开R0 → 自动准备依赖并保存环境 → 同一环境的就绪/前端/应用发现 → 同一会话执行已有导航 → 保存Step02和Step03 → 结束/关闭工具。

删除此入口自动调用PersistenceAnalyzer/MaterialBuilder的连接。当前Core声明缓存和LS会话在02/03间共享，不设计持久JDT进程或恢复服务。

保存Step03 v3 ENGINE里的来源basis/Java分析基础，方法/调用/候选记录语义不变。新输入改变则新索引；不得读旧索引后仅改header冒充重分析。

退出：正式CLI→真实Agent→运行登记/store fixture贯穿；工具启动计数一次，04/05/模型调用零；失败保留实际可查询报告。

### C6：将现有Step04独立为analyze-persistence

重开指定R1的Step02/03与准确R0，不启动JDT/Node。原`DefaultPersistenceAnalyzer`照常消费只读输入，`MapperXmlResourceView`可在本次操作首次打开（跨进程无法共享旧DOM，不为此建缓存）。

publisher显式写R2。Step04 v2绑定R1的navigation/discovery、R0的source basis；reader按同样合同核验，不只删“同run”断言。插件未配置保存明确DISABLED，仍可交给Step05，不偷偷启用SQL解析。

退出：打开同一上游可以独立得到相同XML/SQL业务内容；开/关插件均零JDT；错来源/错navigation拒绝；真实已有XML和全部限制没有减少。

### C7：将Step05接前端并独立为assemble-materials

按R2引用取得准确R1/R0，恢复已保存Java/持久化/前端数据，builder只做选择和组包。新增`frontendSelection/frontendCoverage`及source refs；原文通过已保存范围重开，不跑Node/SQL parser。

运行组合根先水化不可变`FrontendSourceUnits`，连同`FrontendHttpIndex`传给扩展后的`CodeReadingMaterialRequest`；原文和unit/source映射共同参与计量。保存侧仅记选择引用；重开侧恢复相同单元，不重跑builder。不能只给builder一个索引ID，却要求它自己寻找前端文件。

给每个入口组织“相关页面及请求→后端方法→Mapper/XML/SQL”；不把它称业务流程。候选、未匹配前端、后端无页面、容量未选分别报告。新增前端不能使原后端正文静默丢失。

Step05 v2跨run发布到R3；technical Markdown和reader均反映新增完整内容、限制及字节数。接口持有不可变视图，builder不接任意路径/JDT/parser。

退出：保存重开逐字节相同，全部入口及前端范围有处置；后两操作零JDT，组包零parser/模型。技术artifact/Markdown从technicalOutput的R3重开；未适配v2的Step06、Step07直接reader/复用入口、旧状态恢复均在模型初始化前明确拒绝，不在本轮改业务Prompt或执行模型。

### C8：清理退役及冗余代码、测试、文档

只在替代接线通过后删除旧路径。按下节精确清单核对生产消费者、反射/序列化、artifact query及历史fixture，再执行；不得全删`analysis.graph`或`analysis.flow`目录。

清理active README、AGENTS、主设计、逐步设计、工具指南与schema策略中的旧生产指令。删除旧plan-materials注册、帮助、专用编排和仅保护退役行为的测试；当前有效断言先迁至新入口。历史Git记录/运行输出原义不改；文档以当前/目标区分，实施验收后再把新命令改称已实现。

退出：仅一条当前技术生产路径，唯一source-analysis入口；没有JavaParser/five-graph/Fact生产注册；保留历史观察链可用。

### C9：Skill、集成验收与交付

扩展同一个模块内Skill调用三个已经验收的命令，不另写扫描/连接算法，不自动安装到用户全局目录。同步可复制配置、使用指南、错误示例和步骤关系。

定向集成fixture：prepare R0 → collect R1 → persistence R2 → material R3 → 重开/查询 → 排除一个Vue/XML形成R0b → 拒绝把R1–R3当成R0b结果。补依赖形成R1b也不能与旧R2混用。旧结果继续可查看。

新旧材料校验、前端工具失败、依赖未就绪、记录保存失败和空项目都有直接测试。按用户规则只跑新增/直接覆盖测试，重型构建串行；同一编译产物上跑质量，不重复全套测试。

依赖确认闭环fixture：缺parent且仓库未允许 → 正式CLI保存NEEDS_USER_DECISION报告 → inspect/artifact可读准确坐标和下一动作 → 显式改变允许配置启动新run → 解析完成并进入JDT → 旧失败记录不变。再测凭据不落日志、特殊扩展不因“同意继续”被执行。Skill只能按JSON和用户决定操作，不能自己找替代JAR或修改源码。

交付技术材料及验收记录，不交付新Activity或业务过程。是否运行真实客户JDT由实施任务另明确；不因本设计请求自行启动。

## 4. 精确清理分类

### 4.1 已不存在的生产算法：不假装再次删除

代码检索未发现当前生产的JavaParserAdapter、CodeStructureGraphBuilder、CallGraphBuilder、ControlFlowGraphBuilder、DataFlowGraphBuilder、EvidenceGraphBuilder或Fact枚举/Proof生成器。它们不列新开发待办；实施时复核无注册/依赖/资源残留即可。

`docs/supplements/program-graphs-implementation-backlog.md`此前将这些写成当前能力和待扩算法；本轮文档已原位改为退役说明，导航指向当前JDT设计。实施时仍需核对其对应代码消费者，不能以文档纠偏代替代码清理验收。

### 4.2 确认进入删除核对的遗留组

目前主源码检索显示下列typed读者主要在自身历史读取链/专属测试互相引用，未发现当前生产调用者；这不是已完成反射/所有序列化核对的证明：

| 候选组 | 精确对象 | 实施时删除条件 |
| --- | --- | --- |
| 五图专用typed readers | `PersistedCodeStructureGraphReader`、`PersistedCallGraphReader`、`PersistedControlFlowGraphReader`、`PersistedDataFlowGraphReader`、`PersistedEvidenceGraphReader`及各`Reopened*Graph` | 当前业务/history查询不调用；generic artifact仍可返回历史原件；DTO无仍需的序列化消费者 |
| Fact候选/Prooftyped链 | `analysis.fact.candidates`内reader/inputs/set及`analysis.fact.proofs`内reader/set | 无当前或必须保留的历史业务reader消费；不因此删除Activity/材料中的已有事实字段 |
| 专属测试 | `ProgramGraphPublicWireTest`、`FactCandidateModuleReaderTest`、`PersistedProofDecisionSetReaderTest`等 | 保护被删死链的断言退出；有效通用artifact损坏拒绝/历史原件查询断言先迁移 |
| 旧全链wrapper | 迁走后的`executeMaterialsOnly`技术分支、`executeThroughApplicationDiscovery`或`continueAfterDiscovery`中无消费者部分 | 新三个操作全部接通后，按实际调用者删除；不先整类删除 |
| 旧术语/注释 | `TechnicalDiscoveryWorkflowResult`中Facts/Flows fallback说明；工具README旧命令 | 改成真实职责，不能留误导“还会补Fact” |

**不能只因旧测试还能通过就保留无用生产实现；也不能只因没有`new X`就删除序列化用类。** 核对方式是生产引用、注册表/反射字符串、MAPPER反序列化目标、generic artifact/历史重开fixture四项一起查；结果保存为最终删除清单。

### 4.3 明确保留，不是漏清理

- `ProgramGraphsExecution`、`PersistedProgramGraphInputReader`当前用于读取入口并发布JDT index；历史包名不是死代码依据。
- `EntryCodeContext`、JavaCodeIndex、source ranges、方法/调用/参数/限制类型是当前链条本体。
- 历史M10、旧326 Activity、新418 Activity、历史Step05及报告所需的确切reader/DTO/schema/renderer；必须具名列消费者，不能笼统“历史都保留”。
- `FlowCompilation`/`CapsuleProjection`若仍被历史M10重开使用，保留其数据读取最小集合，不恢复producer。
- closed step keys、旧目录和schema枚举用于真实历史artifact寻址时保留；展示名称更新即可，不制造全库迁移。
- `more-findings.md`及所有冻结来源、运行记录、模型结果一字不改。

### 4.4 剩余未核实项怎样处理

无法确认消费者的对象进入“待核对”清单，列具体引用位置和缺少的检查；不写“已清干净”，不默默删除。若为进一步清理必须放弃历史重开，那是新范围选择，必须询问用户，不能引用“清除历史代码”跳过已有保留要求。

## 5. 设计—代码—测试对应验收表

| 设计条款 | 修改owner | 必须见到的直接验收 | 当前状态 |
| --- | --- | --- | --- |
| 准备版来源进技术链 | SourceAnalysisExecution、PersistedTechnicalRunExecutor、reader组合 | 排除Vue/Java/XML不回流；零重复capture | 待实施 |
| Maven父子/模块/传递依赖准备 | 拟新增JavaDependencyPreparer＋固定ModelBuilder/Resolver工具 | 有效模型/依赖图与参考一致；按模块classpath；允许仓库下载/未允许拒绝；零客户代码执行 | 待C1-D原型/实施 |
| JDT实际环境与就绪 | VerifiedJavaProject、JdtProjectSession、helper v3、诊断/readiness | LS/Core目标平台一致；缺依赖/未收齐不能READY；现有Step03复验五类调用 | 待C1-J原型/实施 |
| 前端正式关联 | discovery内部＋固定Node helper | 真实Vue到HTTP全中间路径；错名/动态/多候选不误连 | 待原型/实施 |
| Step03复用 | ProgramGraphsExecution、publisher/reader | 原有候选/实现能力保持；只新增basis/接线 | 待接线，算法已存在 |
| Step04独立 | persistence组合及publisher/reader | 跨run正确，零JDT，完整XML/SQL及限制保持 | 待接线，算法已存在 |
| Step05页面材料 | material builder/reader/formatter | 全文/来源/覆盖、字节一致重开，零解析 | 待扩展，后端组包已存在 |
| 三命令/run归属 | CLI、Agent、request/output、registry | 真实存储接力；终态不重启；假refs拒绝 | 待实施 |
| 阻断报告查询 | Agent、artifact键及技术查询路由 | FAILED运行能读取实际报告；缺失/未安装publication不能查询 | 待实施 |
| 依赖问题由Agent询问 | runtime报告＋同一Skill | 缺项→保存报告→用户具名选择→新run；零LLM依赖猜测/零后台等待确认 | 待实施 |
| v2业务边界 | Activity/过程入口、状态reader、复用准入 | 新技术查询成功；尚未适配业务路径在Provider初始化前明确拒绝 | 待实施，不改业务算法 |
| 历史读取 | 具名保留reader/schema/query | 保存原件可查；旧basis不得冒充新basis | 待回归 |
| 退役删除 | §4精确清单 | 无生产消费者/注册；有效断言迁移；无构建依赖残留 | 待清理，不声称已完成 |
| Skill与说明 | 同一个模块Skill/README/主设计 | 只调真实命令、报告实际结果、不越授权 | 待代码完成后更新执行说明 |

只有该表全部给出实际代码位置、直接测试及具名残留处置，才能宣称“清理后的代码与本次设计对应”。当前只能确认修改范围和读写链路已推导，不可宣称未来实现完全符合设计。

## 6. 本轮文档推导结论

方案不需要重新开发Step03：02与03已经共享session；04/05已能消费不可变索引。必须修的不是调用算法，而是重复源码准备、同run假设、依赖准入、前端关联及新材料字段。

真实Vue链含共享组件/mixin/HTTP封装，不能用正则提取几个URL就宣称完成；所选成熟parser能读语法，有限跨文件连接仍需C1-V验证。Maven库装配/模块隔离须C1-D验证，JDT目标平台/诊断完整性须C1-J验证，下载成功或未收到诊断不能替代就绪。

可行性结论为：**既有接缝支持目标；新增三处工具集成风险先通过有限验证，剩余工作按明确接口接线。尚无新生产实现/真实运行验收。** 用户已确认自动依赖范围、未就绪阻断和旧命令删除；当前不存在让实施者二选一的政策空白。具体特殊项目/原型失败按报告另行讨论；任何结果都不授权业务模型重跑。

### 6.1 本轮文档核对的范围与结果

已对照实际运行器、Agent、请求/输出、publisher/reader、CLI artifact闭集及冻结Vue/Java/XML走读。独立只读审查发现的四个合同缺口已原位补齐：阻断报告的保存与查询、Step05完整前端输入、所有未适配业务入口的v2拒绝、Java诊断的检查分母及完成条件。复核未发现这四项仍有断链；这不是对尚未实施代码的测试。

本轮再补自动依赖准备、父子/多模块、平台隔离、真实环境保存和Agent确认接力。独立研究审查的文件激活Profile范围、未知编译前插件、工具版本配套和正式发布顺序均已补入对应合同；同module文件引用不携带自身receipt，避免循环。用户的两个策略确认已写入，不再留“是否删除旧命令/是否自动准备常规依赖”的二选一要求。

2026-09-26最终文档检查覆盖30份设计/说明：323个本地链接均存在，其中52个行号有效、12个章节锚点匹配；已跟踪及新增文档的空白检查无错误。`more-findings.md`实测SHA-256仍为`59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`；`git diff -- src pom.xml`摘要与本轮开始一致，没有将原有未提交代码修改计为本轮成果。所有调用/材料数量均注明历史来源；Vue关系为人工核对而非本轮新产物。未执行JDT、客户构建、SQL、模型或Maven测试。上述检查证明文档链接/保存及核对范围，不证明工具原型或生产实现通过。

本页可作为后续实施计划的修改依据，但不能跳过C1的工具原型，也不能把“文档推导可行”当作生产通过；每一行必须由对应代码/直接测试闭合后才改状态。
