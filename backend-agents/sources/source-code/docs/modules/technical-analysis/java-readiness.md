# JDT 输入核对、诊断记录与导航条件

状态：C2真实LS/Core双模块测试已验证Java8/17平台、catalog及跨模块collect；历史合并 `collect-code` 的v2输入、READY/BLOCKED发布和排队复核曾由直接测试验证，当前新生产已转为四命令config-v3。固定源码同源 classpath 和有效 POM 已取得；新后端 R2 `collect-code` 保存了 `READY` 环境并完成339入口的导航循环，但Step03索引发布失败，不能把环境就绪或循环完成称为R2成功。扩展的固定源码定向IT在同一会话里重查旧七入口，全部取得材料，但个别入口仍等待约160秒；全量调用准确性及 Vue→SQL 验收尚未完成。旧R1事实若在历史记录中出现，均指先前合并命令，不能与新前端R1或新后端R2混同。用户已确认：**输入核对通过、无已知阻断时，可以继续调用收集；无法证明全文件诊断完成，要明确披露，不再自建完成证明系统。**

Maven 执行、下载及其错误解释由 Maven 和 Agent/用户完成，详见[交接设计](dependency-preparation.md)。本模块只负责正确使用给定输入并调用 JDT，不负责证明整个客户项目可编译。

## 1. 输入和必要检查

输入是同一个R0的有效源码、由Java读取官方Maven输出后形成的逐模块环境、明确选择的实际JDK以及锁定JDT工具。Agent不填写源码根、编译级别或模块边；上游提取合同见[依赖交接§3](dependency-preparation.md#3-最小交接内容)。检查只包含后续使用必须依赖的事实：

| 检查 | 通过表示什么 | 不表示什么 |
| --- | --- | --- |
| 来源/排除/源码根一致 | 使用的是用户选定且允许读取的源码 | 业务材料完整 |
| classpath 文件及列出项可读、内容稳定 | 可以把这些准确文件交给工具 | 未列出的依赖一定不存在或一定不需要 |
| 模块归属及从受支持直接依赖形成的project引用合法 | 不把模块A的输入混给B | 自动解析了Maven reactor |
| 目标 JDK 与编译设置可表达 | 不会静默换成运行工具的 JDK | 编译所有源码已经通过 |
| JDT 项目实际绑定成功 | 工具实际载入所给项目/平台 | 每个导航答案都正确 |

已知来源损坏、列出的 JAR 缺失、目标 JDK 错误、编译设置不支持、模块归属冲突或 JDT 初始化失败，保存问题后阻断。不自动找替代 JAR，不降低语言版本，不跳过用户排除。

Maven 导出失败不在这里重新解析：Agent 读取其日志解决交接；没有可用导出就不进入本模块。Java 的问题记录只表达本次消费/工具操作失败，不扩成 Maven 故障分类系统。

## 2. 使用现有 JDT 接缝，不构造新编译器

### 2.1 LS 工作区

保留 `JdtTargetRuntime`、`JdtProjectBinding`、`JdtWorkspaceBinding/Session` 中有效的工作：

- `JdtCodeEngine.open(JavaCompilationEnvironment)` 只接收 readiness 已准入的同一环境，并将其中的既有 `VerifiedJavaProject`、模块边和目标平台适配为 `JdtWorkspaceBinding`；不重新构造编译项目模型。
- 从准备结果投影固定源码，不读当前 checkout；一个 LS 工作区内按模块各自写受控 project 的 source/lib/project/JRE entries。
- 每个项目设置回读都核对目标 VM、投影后的源码根、显式 library classpath 和受控 workspace project 引用。JDT 的 project 引用是虚拟路径，按精确受控项目名核对而不把它当宿主文件；真实源码根和 JAR 继续 canonical real-path 核验。缺少必要设置或与输入不符时失败，不把“接口返回了”称为“已经核对”。
- 声明目录合并全部已准入模块；入口只能由 `methodKey`、精确 range 和该合并目录中的唯一 `sourcePath` 一起归属。未知或歧义入口拒绝，而不从 `EntrySeed` 猜测路径。
- 工具 JVM、Maven 运行 JVM、客户目标 JDK 不混为一谈。

多模块仅按已提供的映射创建项目；不在 JDT 接线里再次解读 POM 或计算依赖闭包。源码映射使用现有 `VerifiedJavaProject` 分区能力；不要增加一套通用编译项目模型。

### 2.2 Core helper

现有 helper v3 已显式接收目标平台，关闭隐式工具 JVM 类库。工作区路由对每个源码文件开启/复用该文件所属模块的 helper，并传入该模块投影后的源码根、显式 classpath、目标 JDK 版本及从该目标 home canonicalized 的系统库条目：Java 8 及更早版本只能用 `jre/lib/rt.jar`，模块化 JDK 只能用 `lib/jrt-fs.jar`，其 JRT 映像为 `lib/modules`。私有 v2 排队身份同时散列 `release`、`bin/java`和这三个可能的平台文件；缺失写入明确 unavailable marker，执行前重开比较，绝不扫描整个 JDK。缺少、非普通文件或解析到目标 home 之外的条目在启动 helper 前阻断；不能退回工具 JVM 或仅传目标 JDK 目录。helper 的启动 JVM 仍只是锁定的工具 JVM。保留这个必要边界，使用 JDT 官方支持的目标平台方式接线；不自写 `ct.sym`、JRT、JMOD 或 Java API 模拟器。

当前仅有 `languageLevel` 同时设置 source/compliance/target，尚不能可靠表达所有 source≠target 或 release 配置。实施应先验证锁定 JDT 的现有设置接口能否传递必要值；不能支持的组合返回具体限制。优先使用实际目标 JDK；不为了支持任意较新 JDK 模拟旧 release 而扩大本轮。

LS 与 Core 对同一源码文件使用同一模块的环境。模块路由、完整源码和参数保持。目标R2仅在现有JDT模块内修正已确认的`fromRange`嵌套错归与外部/未知/查询失败状态；不另建Java解析器、调用裁决器或多态分派系统。具体工具证据与保存合同见[JDT引擎§3](../java-code-engines/jdt-engine.md#3-jdtnavigationresolver让jdt决定调用连接谁)。

工具自身故障不是单个源码文件的语法缺口。`EntryCodeCollector.buildIndex()` 现在原样传播 `syntax.describe` 抛出的 `CodeEngineException`，因此 helper 版本/协议不兼容、进程故障或超时不会继续发布大量看似独立的 `SYNTAX_UNAVAILABLE` 文件缺口。`ProgramGraphsExecution` 只将明确的共享工具或来源故障中止整个索引：`JDT_PROTOCOL_INVALID`、`JDT_INDEX_FAILED`、`ENGINE_CONFIGURATION_INVALID`、`SOURCE_INVALID`、`JDT_TOOL_UNAVAILABLE` 和四个 `JDT_SYNTAX_*` 代码；它不按异常消息或所有 `CodeEngineException` 一律中止。现行`JDT_QUERY_FAILED`保留为逐入口的`NOT_COLLECTED`结果，允许其他入口继续收集和发布；目标在可安全保留调用点时再精确标记受影响CALL及其入口限制，不能混成`UNRESOLVED`或`EXTERNAL`。正常返回的单文件语法诊断，以及非引擎的逐文件运行时读取失败，仍逐文件保留。旧R1保存阻断问题报告，目标R2沿用该职责；超时缓存43项直接测试通过的修正不重新开发。

### 2.3 当前缺口

| 实际代码 | 已有部分 | 尚缺 |
| --- | --- | --- |
| `JdtWorkspaceSession` | 多项目及LS/Core具名测试、正式R1 production opener已有接线 | 固定源码具名导航验收 |
| `JavaCompilationEnvironmentComposer` | 来源分区、二进制/平台检查及R1 fixture消费 | 从新v2官方输出派生环境接入；不重写composer |
| `JavaReadinessPreparation` | BLOCKED/READY环境返回与正式module5保存已有fixture | 新输入接线及固定源码诊断观测核验 |
| `EntryCodeCollector` | 现有声明/调用收集、双模块真实测试，以及 `CodeEngineException` 的工具故障传播 | 目标R2保存已确认外部、未知及逐调用查询失败；只展开可确认的仓库边 |
| `ProgramGraphsExecution` | 显式中止共享工具/来源故障，保留 `JDT_QUERY_FAILED` 的逐入口收集 | 目标R2沿用阻断报告及入口分母；保存局部CALL失败时仍不得把入口写成完整 |
| `JdtLanguageServerClient` | 客户端在内存暂存原始诊断事件，且诊断记录锁与同步请求锁分离，避免诊断先到时阻塞 JSON-RPC response | 补报告保存；不要求全文件最终诊断证明 |

这些是代码核对，不是本轮新测试结果。环境接口采用现有值对象加必要字段即可；不要仅为隐藏所有字段再增加多层 factory/plan/outcome 框架。

## 3. 执行顺序

1. 重开R0，按官方Maven输出确定性形成环境；执行上文必要检查，不接收Agent手算设置。
2. 保存实际使用的环境及内容身份，形成 `javaAnalysisBasis`。身份绑定来源、模块/源码根、依赖内容与顺序、目标平台和工具版本；不绑定下载时间或账户。
3. 使用该环境打开一个受控 JDT 会话；核对实际项目绑定。必要设置不一致则保存报告并结束。
4. 保存本次会话收到的项目/文件诊断；标明来源与覆盖范围，不补造未收到的结果。
5. 已知阻断不存在时执行原 Step02 声明/入口发现及 Step03 收集。二者使用同一会话，不重新准备依赖。
6. 操作结束保存环境、诊断观测、实际入口/调用覆盖和限制。若执行中出现使会话不可继续的错误，保留已保存内容并停止；不发布不存在的完整索引。

JDT 自身可以进行受控项目的语法/类型检查，不等于执行客户 Maven 构建。分析程序不得启动客户扩展、生成器、注解处理器或应用。禁用自动 Maven/Gradle 导入，优先使用完整受支持工具配置；需要出站隔离时使用已有操作系统/执行环境能力。已观察到 Buildship 可能主动联网，因此不能只凭一个“关闭导入”选项宣称网络已隔离；不通过删除随机 bundle、自制 OSGi 发行包或新建跨平台 sandbox 系统来补齐。环境不支持已承诺的隔离时报告并讨论，不擅自放开。

## 4. 诊断：记录所见，不证明全仓

原始 `publishDiagnostics` 是工具通知，`ServiceReady` 不是全文件编译完成证明。没有通知，也不能填“0 个错误，全部检查完成”。相关协议见[JDT LS 官方扩展](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/lsp/JavaProtocolExtensions.java)。

本轮采用以下目标规则：

- 保存本次会话中收到的原始 code、severity、URI/相对路径、range、message；外部文件或旧消息不能算当前源文件观测。
- 回调中的 `data` 是工具扩展字段；Gson `JsonElement` 按其 JSON 结构复制为独立 JSON 树，不以宿主对象映射或字符串化破坏嵌套值，也不能让该回调阻止后续诊断事件保存。
- 可列“选中多少文件”“哪些文件收到诊断”，但收到通知不自动表示该文件已最终检查完。
- 旧合并R1及目标后端R2 `collect-code` 的 READY readiness 报告在没有成熟完成证明时写 `diagnosticCoverage=UNCONFIRMED`，不是 `NO_READINESS_PROBLEMS`，也不表示零错误或全文件已完成。
- 只有成熟工具明确提供且本版本已验证的完成结果，才能写 `CONFIRMED`；本轮不开发逐文件最终回调、版本推断或专用完成协议。
- 已收到的明确缺类型、缺类库、项目配置错误或其他 error 不隐瞒。无法可靠判断是否阻断的实际 error 返回问题给 Agent 讨论；不能为继续而降成 warning。
- 普通 warning 保留，不按数量机械失败。
- 没有已知 error 且输入/绑定通过时，覆盖未确认不单独阻止导航；结果是“可分析，但诊断覆盖未确认”，不是“项目完全正确”。

Agent 负责解释工具原始诊断、给出下一步建议。Java 仅使用工具提供的稳定 severity/状态和自身实际操作结果，不构造中文 message 关键词规则来自动修复或补包。

## 5. 保存与结果含义

仍使用 Step02 module 5 的两个文件：

- `java-compilation-environment.json`：实际输入来源、模块、依赖内容/顺序、JDK 和限制。私有绝对路径与凭据不进入公共报告。
- `java-analysis-readiness.json`：实际检查、是否允许导航、已知阻断、诊断观测和覆盖声明。

目标报告示意（不是实际产物，也不是已经发布的完整 Schema）：

```json
{
  "environmentStatus": "VERIFIED",
  "projectBindingStatus": "VERIFIED",
  "diagnosticCoverage": "UNCONFIRMED",
  "readiness": "READY_WITH_LIMITATIONS",
  "navigationAllowed": true,
  "limitations": ["未确认诊断覆盖全部源码文件"],
  "diagnostics": []
}
```

这里 `VERIFIED` 只指§1列出的消费检查；`diagnostics=[]` 是未记录到诊断，不代表编译无错误。完整 wire 仍需携带来源、编译环境引用和检查范围，实施时同步正式 Schema/读取器/fixture。

导航条件由实际检查推导，不由用户改一个布尔值放行。缺 JAR 等已知阻断时 `navigationAllowed=false`；诊断覆盖未确认这一项单独存在时允许有限继续。运行状态、报告保存与导航条件分别表达：

- 报告可靠保存但不能导航：FAILED 运行仍可查询报告。
- 环境可用但带限制：可以生成技术结果；限制向 Step04/05 传递。
- 保存失败：只返回真实可得错误，不提供虚构 receipt。
- 依赖后来补齐：启动新运行，不修改旧报告，不自动修正旧索引。

现有module5 v1已在BLOCKED/READY fixture保存重开。本次输入v2改动复用该正式输出，原始Maven输出摘要和提取细目保存在私有v2记录；不为交接另建公开产物。历史稀疏报告仍按原合同读取，不把缺失字段默认读成已核对或READY。

## 6. 如何验收限定的 Step03 修正

验证两条链，不能只看启动成功：

1. 自有fixture：官方输出路径→Java提取/绑定→实际LS/Core环境；不手填语义JSON，目标JDK、两模块隔离和源码模块引用正确。
2. 固定客户源码：原 Step03 在补齐输入后，核对错误 Mapper、`logger.error`、重载、嵌套调用及合法多态；保留原始响应及最终边，并检查错误 SQL 没有进入 Step04/05。

诊断数量下降不是调用准确性通过。代表例通过也不是全仓绝对正确；全量技术采集仍报告筛查和人工确认的差别。目标R2修正具名错边和外部边界时只使用现有JDT Core/LS的绑定、位置及候选输出；若仍有错误，记录原始工具结果与框架归属，不临时新增多态/候选裁决算法。

当前调用目标合并仍有一项已确认限制：`JdtNavigationResolver.resolve` 用调用层次 `fromRange` 包含 AST 导航位置的条件归属候选，再与定义、实现结果合并。固定源码中，`pageDomain.setPageSize(Convert.toInt(...))` 的内层 `Convert.toInt` 记录既包含正确的 `Convert.toInt(Object,Integer)`，也包含只来自 `CALL_HIERARCHY` 的外层 `PageDomain.setPageSize(Integer)`。目标R2已批准对此做最小关联修正：唯一AST调用点和可靠JDT声明绑定共同约束hierarchy归属；范围跨越嵌套调用或绑定无法确认时保留未确认观察，不附确定边，不选第一个候选。同时确认真实外部调用才正常停在外部边界，空位置、未知身份及查询失败各自保存。补齐编译依赖不能代替这组准确性验收；历史R1索引不改写。

Lombok只核查官方Eclipse/ECJ集成能否在受控LS及Core helper进程中使用，再分别验证具名`getCode/getId/setInitStock/log`诊断与绑定。验证不通过或版本/隔离条件不成立即保留生成成员缺口，不实现注解解释器，也不把生成成员当原始源码。约51秒定义查询等待只做聚焦复核；原在途请求缓存修正已另行完成，文档生命周期任务的具体调度根因仍未明，不能用这次设计许诺消除长等待。

本次取消的工作是“全文件诊断证明”和“自建构建环境解释”，不是放弃环境正确传递或真实导航验收。
