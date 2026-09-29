# Maven 输出文件、Agent 操作与 Java 确定性交接

状态：官方 `dependency:build-classpath` 已从固定源码导出 106 个可读 JAR，同源 `help:effective-pom` 也已导出。v2 文件交接已接入配置、正式 Agent、私有记录和排队复核；固定源码的正式运行已保存环境 READY 并进入 JDT 导航。全量调用准确性与 Vue→SQL 验收仍未通过，环境 READY 和 fixture 成功不能代替。Java 不运行 Maven、不下载、不求值父 POM。

**当前生产交接：**用户或获授权的 Agent 取得 Maven 的 classpath 和有效 POM 文件，只把实际文件位置、所选模块和目标 JDK 位置交给 `collect-code`。Java 从原始输出提取支持范围内的设置，绑定 R0 并形成实际环境；不再要求 Agent 另写语义 JSON。历史 v1 结果可查询，当前collect-code接受配置v2；本次四操作目标的配置v3沿用同一Maven输出子结构，私有java-compilation-input-v2不变。版本以[运行合同](cli-and-runtime.md#5-版本与兼容)为准。

## 1. 分工：不要让 Java 再做一遍 Maven 的事

```text
Agent 向用户说明需要运行什么、可能下载什么
  → 用户自行运行，或明确授权 Agent 运行 Maven
  → Maven 返回退出状态、日志、classpath 文件和有效 POM 文件
  → Agent 解释失败；成功则只交文件位置及明确的环境选择
  → collect-code 的 Java 读取 Maven 输出，绑定 R0，形成并检查实际环境
  → 同一环境交给现有 JDT
  → JDT 返回结果或原始错误；Agent 向用户说明
```

Skill 是 Agent 的操作说明，不是 Java 与 LLM 之间的消息服务。Agent 可以在首次 `collect-code` 前发现缺少导出文件并询问，不要求先运行一次注定失败的 CLI。直接使用 CLI 的用户缺少输入时，CLI 同样返回明确的输入缺项。

| 事情 | 负责者 | 我们不再做什么 |
| --- | --- | --- |
| 父 POM、BOM、Profile、依赖版本、传递依赖和作用域 | Maven | Java 不重新计算有效模型或依赖图 |
| 本地仓库、下载、认证、镜像、代理、重定向和缓存 | Maven 与用户配置的运行环境 | Java 不下载、不管理仓库、不实现 HTTP transport |
| 是否运行命令、选择模块/Profile、凭据或特殊构建怎么处理 | Agent 与用户 | 不建立 Java 审批服务，不逐 JAR 询问 |
| Maven 失败是什么意思、下一步建议 | 当前 Agent，依据原始日志 | 不建设 Maven 错误分类/修复引擎，不用关键词自动补依赖 |
| 从有效 POM 读取具体设置、绑定 R0、按模块形成 JDT 环境 | Java | 不让 Agent 手填语义 JSON，不重新求值 Maven 配置，不猜缺少了哪个未列出的 JAR |
| 语法、类型与调用分析 | 现有 JDT | 不再写 Java 分派/编译器或 JDK 平台模拟器 |

此处 Agent 使用当前对话能力解释结果；**不在分析程序中新增业务模型请求**。规范与运行器无关：OpenClaw、Trae、Claude Code、Codex 或人直接操作，都使用相同的 Maven 输出和 CLI 合同。Skill 是操作说明；不依赖 Codex 会话记忆、专有审批工具或本次执行者临时补配置。运行器不能读取文件或执行终端时，说明缺少这种能力，由用户提供文件；不得伪造输出。

### 1.1 有效 POM 是什么

有效 POM 是 Maven 在**这次选定的模块、Profile、属性等选项下**处理继承和变量后输出的项目配置，不是所有可能构建方式的集合，也不是成功编译证明。[官方说明](https://maven.apache.org/plugins/maven-help-plugin/usage.html#The_help:effective-pom_Goal)

以下为人工简化示例，不是固定客户源码的实测输出：父 POM 定义 `java.version=8`，子 POM 的 compiler-plugin 配置引用 `${java.version}`。Maven 的有效 POM 可给出具体编译设置 `8`。Agent 不追踪父子配置或替换变量；Java 不重新计算它，只读取已有具体值。若输出仍有变量、所需设置缺失或存在本程序不能处理的执行级差异，就返回具体缺项/不支持原因，不能猜成 8 或 17。

classpath 是另一份输出，说明实际依赖 JAR 在哪里。物理目标 JDK 安装位置不是这两个输出必然提供的信息，由用户已有配置或明确选择提供，Java 检查实际安装版本；R0 提供允许分析的源码字节和范围。不得把“由 Maven 提供项目设置”扩大为“Maven 已证明源码、JDK 和所有依赖完整正确”。

## 2. 使用官方输出，不扫描本地仓库猜 JAR

采用官方 [`dependency:build-classpath`](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html)。它输出实际依赖路径；不要求用户逐个提供库名和版本。生产源码的导出使用明确的 `includeScope=compile`，保留传递依赖；记录实际 Maven/插件版本和构建选择。插件写文件时使用其 `outputEncoding` 参数，见[3.11.0 的写入实现](https://github.com/apache/maven-dependency-plugin/blob/maven-dependency-plugin-3.11.0/src/main/java/org/apache/maven/plugins/dependency/fromDependencies/BuildClasspathMojo.java#L290-L304)，因此下方显式固定 UTF-8。

以下只是单模块命令形状，不是已经执行的命令。实施时固定已验证的插件版本、具体路径和参数；不直接复制尖括号占位符：

```text
mvn -B -N -f <同源受信任副本中的模块pom.xml>
  org.apache.maven.plugins:maven-dependency-plugin:<已验证版本>:build-classpath
  -DincludeScope=compile
  -DexcludeTransitive=false
  -DoutputEncoding=UTF-8
  -Dmdep.outputFile=<该模块独立的绝对输出路径>
```

首次或改动构建选择时写入新的空输出位置，不能把旧文件误认成本次成功产物。需要Profile、settings、离线模式时使用Maven本身的参数；Agent说明实际采用了什么，Java不解析这些规则。逐模块命令使用 `-N`，不递归替其它模块写相同输出；不要把多个模块写进同一输出文件，也不要把所有缓存JAR的并集传给JDT。

`dependency:build-classpath` 只提供依赖路径，不提供完整 JDT 编译输入。目标交接每模块还必须取得**同一模块、同一 Maven 构建选择**的官方 [`help:effective-pom`](https://maven.apache.org/plugins/maven-help-plugin/effective-pom-mojo.html) 文件。两个命令使用相同 `-f`、Profile、属性、settings 和同源副本；逐模块独立输出，不混用不同次选择的文件。`help:evaluate` 可以用于外部排查，但首版正式消费固定为有效 POM 文件，不新增一套任意表达式交接协议。

有效 POM 的单模块命令形状如下；具体版本、路径和现有授权必须在执行前确定。本段本身不授权运行：

```text
mvn -B -N -f <与classpath相同的模块pom.xml>
  org.apache.maven.plugins:maven-help-plugin:<已验证版本>:effective-pom
  -Doutput=<该模块独立的绝对输出路径>
```

两份输出各自必须有实际成功退出记录，并采用新输出位置；插件版本、非秘密选择、工作目录和日志作为外部运行记录保留。记录来自实际工具调用，不要求 Agent 发明一份 `SUCCEEDED` 证书。Java 的文件核验不能证明外部命令真实执行过；用户提供的输出属于明确的受信任交接，不以自报状态代替内容检查。缺任一必需输出、同源绑定不符或设置无法提取时，返回具体不能运行的原因，不用手读 POM 补值。

新准备可优先在**同一条获授权Maven命令**中依次列出这两个官方目标，分别使用 `mdep.outputFile` 和 `output` 写两个新文件，从操作上保持同一构建选择；该组合仍需直接实测，不标为本次已运行。已有分开导出的文件可以使用，但Skill必须核对实际命令记录中的模块和选择相同；选择未知或冲突时说明需要同条件重新导出，不让Java从日志重新实现Maven命令解释器。Java对已接收的受信任输出作内容核验，不能仅凭两个文件名就声称已证明同一次构建。

### 2.1 执行边界

- Maven 在与 R0 对应的受信任副本中运行，或由用户提供同源构建环境的导出结果；不修改固定原件，不从客户当前工作区暗中补源码。
- Maven 可能访问仓库并更新缓存。即使只调用一个插件目标，项目扩展仍可能在启动时加载；不能承诺“运行 Maven 就天然零客户代码执行”。
- Agent 先说明具体命令、目录、网络/缓存影响与已发现的扩展。没有适用授权时让用户选择自行执行、授权具名命令或暂不继续。已有具名授权可沿用，不重复逐包询问。
- 不自动追加 `compile/test/package/install`、wrapper、生成器、处理器或客户脚本。需要这些操作时另行讨论；用户一句“继续分析”不表示它们已经获准或已经执行。
- 特定源码已有的禁止执行约束继续有效。固定 jshERP 捕获的 scoped AGENTS 仍禁止运行其 Maven；后续验收应使用用户提供的同源外部导出，或先取得对具名命令/副本的明确新授权并同步该例外，不能把“真实验收”四个字当作越过约束的许可。本次设计更新本身不授权运行 Maven。
- Java 的运行预算只管理它实际启动的 JDT/Node 等工具；不要在 Java 配置里继续保留 Maven 下载超时、字节、仓库 allowlist 或 Resolver 参数。
- 若用户要求严格网络隔离，使用现成的执行环境/网络控制；不存在合适环境时报告限制，不自研 Maven 网络管理器。

## 3. 最小交接内容

### 3.1 已确认目标：调用者只交文件和选择，Java 生成环境

外部输入写在技术 YAML 的 `java.compilationInput` 对象中；准确字段及示例由[CLI 设计§2](cli-and-runtime.md#2-技术配置与外部编译输入)统一拥有。本模块消费已解析输入，不增加导出 CLI、运行器专用接口或公共 Agent 方法。

| 输入/内容 | 谁提供 | Java 做什么 |
| --- | --- | --- |
| 准备运行 R0 | 已有 `source.preparationRunId` | 重开准确源码版本、有效范围和构建文件；摘要自己计算，不让 Agent 填 |
| 同源 Maven 副本根目录、所选模块相对目录 | 用户配置或 Agent 按已明确的项目选择填写 | 以副本根＋模块路径定位原 POM，核对 R0；不是在任意目录扫描项目 |
| 每模块 classpath 文件及分隔符 | Maven 输出；分隔符按实际导出环境明确填写 | 按原顺序读取准确路径，检查文件可读并计算摘要，不扫描仓库 |
| 每模块有效 POM 文件 | Maven 输出 | 使用现成 Maven 模型读取器反序列化已有结果，提取已具体化的源码根和编译设置 |
| 每模块目标 JDK home | 已有配置或用户明确选择 | 检查真实安装版本和 JDT 可用性；私有 v2 身份记录 `release`、`bin/java`及 JDT 实际使用的平台文件内容：Java 8 的 `jre/lib/rt.jar`，模块化 JDK 的 `lib/jrt-fs.jar` 与 `lib/modules`。缺少任一被观察路径写明 unavailable marker，不扫描整个 JDK |
| 模块间源码关系 | 不由 Agent 填 | 沿用当前窄规则，从选定有效 POM 的明确坐标和直接依赖形成；不重建 reactor 或传递图 |
| 命令、退出状态、日志、非秘密构建选择 | 外部工具实际执行记录 | 保留审计附件；不作为“依赖绝对完整”的证明，不从日志猜编译设置 |

程序处理顺序：

1. 解析路径与选择，重开准确 R0。缺文件可形成阻断问题：同源副本原 POM 必须明确报告为 `project POM`，Maven 输出缺失必须明确报告为 `effective POM`；不得启动 Maven 补齐。
2. 校验同源副本中所选模块 POM 与 R0 保存的对应文件一致。JDT 最终读的源码仍来自 R0，而不是外部副本。外部父 POM/BOM 已由 Maven 处理，Java 不追踪或下载。
3. 读取每模块有效 POM。把已具体化的 `build.sourceDirectory` 按副本根映射为 R0 相对路径，确认它属于该模块且在允许范围内。首版沿用单一生产源码根；额外 source set 或未提供的生成源码不自行补造。
4. 读取 compiler-plugin 顶层的具体 `release` 或完整 `source`/`target`。有效 POM 的 execution 若也提供编译级别，只接受自身完整且与顶层同一 tuple 的值（例如 `default-compile`、`default-testCompile` 都重复 `source=1.8,target=1.8`）；部分或不同值阻断。不解释 Maven lifecycle、execution 优先级或覆盖规则，也不在原 POM/父 POM或默认值中自行求解。
5. 读取有序 JAR、核对实际 JDK；从所选有效 POM 的直接坐标关系形成受支持的模块引用，详见§4。当前可用的环境适配器继续复用。
6. 程序内部形成不可变编译输入和 `JavaCompilationEnvironment`，保存原输出摘要、上述 JDK 平台文件摘要及所用字段，绑定既有请求身份；排队后执行前继续检查输入是否变化。任何摘要或 unavailable marker 改变都在启动 JDT 前阻断，不扫描或散列整个 JDK。
7. 有阻断时保存真实问题并返回；通过后把这份环境交给同一次 JDT 会话。此后不由 Agent 或 Java 再填一份不同环境。

新私有 `java-compilation-input-v2` 是第6步的**程序生成记录**，不是需要用户/Agent编写的输入。它记录 R0、原始文件身份、提取值、模块边、JDK及限制，复用已有私有记录和正式 module 5；不另建交接数据库。重复输入须产生相同环境和语义摘要，不受使用哪种 Agent 影响。包含本地路径的私有记录不提交仓库。

明确禁止要求调用者提供或覆盖 `sourceRoots`、`source`、`target`、`release`、`sourceModuleDependencies`、R0摘要或“已经核验成功”。技术YAML只允许本节的文件位置和选择。缺少必要选择时 Agent 问用户；缺少可由 Maven 输出给出的事实时先取得真实输出，不能让 LLM 算一个值。

### 3.2 历史实现：v1 手工语义交接，仅供历史读取

以下说明历史 v1 格式及其已验证门禁，不是当前调用说明。v1 先接收填好语义字段的私有 JSON，再与 Maven 输出比较；§3.1 的 v2 直接从官方输出派生这些事实。历史结果保留原格式读取，新的 `collect-code` 不能绕回 Agent 填值。

| 字段组 | 最小内容与来源 |
| --- | --- |
| 固定来源 | R0 的完整 `SelectedSourceBasis`；本次导出使用的构建文件内容摘要及模块路径。不存在证据时不能只填一个 R0 ID 冒充同源 |
| 导出记录 | 实际 Maven/插件版本、模块、非秘密构建选择、退出状态、classpath 文件及日志位置；用户提供结果也明确标明来源 |
| 模块源码 | 模块的相对路径、实际生产源码根；构建声明由同一 Maven 选择的已求值输出确认，源码字节及范围由 R0 确认，不由 JAR 名称或默认目录猜测 |
| 有序依赖 | 该模块独立 classpath 文件、路径分隔符/编码；只读其中实际路径，不依赖宿主平台猜分隔符 |
| 编译设置 | 项目的 source/target 或 release 来自已求值的 Maven 输出；物理目标 JDK 由运行环境选择并检查其真实版本。两者不一致时停止，而非使用工具 JVM 代替 |
| 模块间源码引用 | 如存在，明确 from/to；仅以同一批已求值有效 POM 中选定模块的直接 G:A:V 依赖为依据，没有则为空。不凭模块名相似自动建立 |
| 已知限制 | 导出未完成、需要但尚未提供的生成内容、未纳入模块等；不知道就记录未知，不默认完整 |

Java 可以使用标准 JSON/XML/文本读取器读这份输入；**不能**在解析失败时退回旧 `JavaDependencyPreparer`。坐标、scope、冲突原因等只在工具实际给出时保留，不从文件名或 Maven 缓存目录反推后伪称工具结论。

当前 JSON 合同版本为 `java-compilation-input-v1`：顶层精确提供 `sourcePreparationRunId`、`sourceVersionId`、`effectiveScopeDigest`、`buildFiles`、`export` 和 `modules`。每个 `buildFiles` 项必须是 R0 内相对 `modulePath/pom.xml` 和完全相同的 SHA-256；每个 module 自己提供相对 `modulePath`/`sourceRoots`、绝对 `classpathFile`、声明分隔符、目标 JDK home/version/执行环境、`release` 或成对 `source`/`target`，以及显式 `sourceModuleDependencies`。此外，每模块必须提供绝对 `mavenProjectDirectory`、绝对 `effectivePomFile` 和有效 POM 字节的 `effectivePomSha256`：目录下的 `pom.xml` 要与该模块 R0 build-file 的摘要相同；有效 POM 的 artifactId 及 R0 原 POM 中非继承可确定的 groupId/version 必须匹配。有效 POM 的具体绝对 `build.sourceDirectory` 映射回 R0 后必须与唯一交接源码根相同，且有效 compiler plugin 只能明确提供 `release` 或完整 `source`/`target`，并与 JSON 相同。对多模块，所有选定有效 POM 都必须给出唯一、具体的 G:A:V；`SOURCE_MODULE` 边集合必须恰好等于这些选定坐标之间的直接普通 JAR、默认/`compile`/`provided` 依赖集合。缺文件、散列/身份/根/级别不符、属性未求值、重复、执行级设置歧义或无法按此窄规则核对的选定模块依赖均以 `JAVA_COMPILATION_INPUT_PROJECT_SETTINGS_UNVERIFIED` 阻断；`export.status=SUCCEEDED` 不再能单独放行。`export.status` 仍只能是 `SUCCEEDED`。外部导出须采用 UTF-8；当前读取器按 UTF-8 读取 classpath 和有效 POM文件，不猜项目默认报告编码。读取器保留 classpath 文件内容摘要和按文件中顺序读取的 JAR；请求身份再使用这些摘要、每个 JAR 的有序 SHA-256、目标 JDK `release` 摘要及模块边。路径不作为请求引用的明文字段保存；不过 JSON 原文摘要及 `VerifiedJavaProject.fingerprint()` 都把规范化本地路径作为不透明摘要输入，所以路径迁移也会改变身份，不能复用旧运行。排队执行真正开始前会重新计算同一摘要；JSON、classpath 导出、JAR、目标 JDK 或模块边任一变化，均以 `JAVA_COMPILATION_INPUT_CHANGED` 阻断，不能让新环境使用旧运行身份。

当前实现边界：`java-compilation-input-v1` 会核对交接字段、R0、classpath 文件、JAR、JDK，以及每模块的有效 POM 项目设置证据。它只接受有效 POM 已有的具体 `sourceDirectory` 和 compiler-plugin 结论，不会对原 POM、父 POM、BOM、Profile 或表达式作任何求值；不支持的 Maven 设置形状会阻断而非退回 JSON 自报。`export.status=SUCCEEDED` 仍只是交接文件的导出声明，也不证明客户项目可编译。多模块只完成了窄的直接来源证明：选定模块的唯一有效 G:A:V 与直接普通 JAR 的默认/`compile`/`provided` 依赖，必须和 JSON `SOURCE_MODULE` 集合双向完全相同。非选定二进制依赖仍只由 classpath 交接；读取器不构建 reactor、依赖管理、传递依赖或测试/runtime/system scope 图，也不从另一个坐标猜来源模块。有效 POM 中未求值的直接依赖坐标、重复选定坐标或无法按该规则解释的选定依赖会阻断，而非夸大为完整多模块 Maven 验证。固定源码当前是单模块，这一限制不改变本次门槛。固定源码真实验收仍须保存实际命令和同源有效 POM输出，并在用户授权后核对完整 JDT 导航；fixture GREEN 不能替代该验收。

Java 使用前只做必需检查：

1. R0 可重开、来源及有效范围匹配；源码根和明确模块引用没有越界或引用已排除文件。
2. 输入格式、模块身份及 classpath 路径合法；列出的依赖可读。记录实际顺序和内容摘要，后续使用不能偷偷换文件。
3. 目标 JDK 可用；按支持的 JDT 接口传入实际编译设置，不用工具 JVM 代替目标平台。
4. 给同一次 JDT 会话传这份环境；出现缺项或工具错误，保存实际检查/工具结果并返回。

不要求 Java 重新运行 Maven 来证明输入完整；也不能将这些检查写成“所有依赖已经证明齐全”。

## 4. 多模块与特殊项目：保留支持，但不自建 reactor

每个模块使用自己的源码根、classpath 和 JDK。已有 `JdtWorkspaceBinding/Session` 的多项目隔离可以复用，不能把兄弟模块的依赖随意混入。

v2仅提取当前reader已支持的直接关系：所选有效POM具有唯一具体G:A:V，直接普通JAR依赖的默认/compile/provided scope、无classifier且非optional可连接到另一个已选模块。未选模块的二进制仍使用classpath，不据此自动加入源码模块。坐标未求值、重复、关系形状不支持或映射不唯一时，报告具体模块，不猜关系；不实现依赖管理、冲突解决或传递闭包。这里是从Maven已有输出读取关系，不是自行求解Maven依赖。

`build-classpath` 并不保证在未构建的 reactor 中取得所有同源模块产物，也不导出源码项目之间的全部分析关系。本轮不实现自定义 workspace resolver 来补这个缺口：

- 实际可导出的普通多模块：消费各模块的输出及明确的源码模块映射。
- 导出需要同源模块 JAR 但该 JAR 不存在：返回 Maven 原始失败，由 Agent 讨论受信任环境的准备方式；不由 Java 自动 `install` 或生成假 JAR。
- 用同源源码项目替换某个模块二进制时，必须有准确的模块与该二进制对应记录；不能靠文件名去除任意 JAR。无法核对则不作替换，报告所缺映射。
- 未提供生成源码：不猜源码、不执行生成器；说明实际受影响范围。若补入新的分析源码，遵守源码准备的新版本规则。
- 未支持的 JPMS、复杂 source set、特殊编译器或构建扩展：返回有限支持说明，不偷偷改成普通 classpath，也不本轮开发通用支持。

这些限制不能被当作已完成多模块验收；要用一个真实可导出的多模块 fixture 验证接线。若只能完成单模块，应如实标明，不用扩大 Java 实现来维持“全都支持”的说法。

## 5. 如何交给现有 JDT

本轮先复用已经选择的受控项目路线：Java 根据固定源码和显式编译输入，向 JDT 提供 source entries、每模块 library entries、必要的 project references 和目标 JRE。JDT LS 不自行导入客户 Maven 工程或下载依赖。

JDT LS 原生 Maven/m2e 导入是工具已有能力，但会改变本轮的执行和副作用边界；**不同时维护第二条导入路线**。是否改用它须以真实需要另行决定，不能用它掩盖当前接线未完成。

Core helper 与 LS 消费同一模块设置；现有缺口、诊断政策及验收见[Java 就绪](java-readiness.md)。只修输入和调用接线，不开发新 Step03 裁决算法。

## 6. Agent 如何向用户说明和取得选择

人工示例（不是本次运行结果）：

> 尚未取得这个模块的依赖路径。需要运行 Maven 的 classpath 导出命令；它可能下载依赖及官方插件。你可以自行运行并提供输出，也可以授权我在这份受信任副本中运行。分析程序不会自行下载。

之后：

- **用户自行运行：**Agent 给出准确命令、工作目录和预期输出；收到实际文件/日志后继续，不把“我已经下载过”当成完整 classpath。
- **授权 Agent：**Agent 通过现有终端执行机制运行指定命令；读取真实退出状态及输出。失败时引用原始错误并说明不确定性，不编造已补齐。
- **classpath 成功导出但项目设置尚未确认：**这只是部分输入；Agent 说明仍缺同一 Maven 选择下的源码根、编译级别等具体字段，取得适用授权后使用官方 Maven 求值目标。必需字段无法从实际输出确认时报告不能运行的原因，不创建声称完整的 JDT 交接。
- **Maven 输出已取得：**Agent 在技术 YAML 中提供输出文件位置、所选模块、同源副本根和目标 JDK 位置，调用 `collect-code`。Java 负责读取、提取、绑定、核验和保存。Agent 不制作语义 JSON，也不把本次人工读 POM 的结论填进程序。Java 返回成功或具名缺项后，Agent 才向用户解释其实际状态。
- **不允许执行/暂不提供：**保存已有进度，说明缺少哪些输入；不启动 JDT 调用收集。
- **Maven 失败：**当前 Agent 根据原始输出提出建议；不得自动改 POM、换版本、增加仓库或启动构建补成功。无需 Java 先把所有失败归类。
- **Java/JDT 失败：**CLI 返回实际操作、问题、原始诊断与已保存结果；Agent 解释，必要时讨论。不是 Java 发起 LLM 请求。
- **成功：**Agent 把导出文件绑定到配置，新建技术运行。旧失败记录保持原状；如果用户已要求完整技术链，成功后按授权继续 R2/R3，不逐步重复请求批准。

例如用户向 OpenClaw 或 Trae 要求“分析到技术材料”：Agent 读 Skill，确认 R0和停止位置；已有两份同源输出则直接配置其路径，否则说明两个官方 Maven 目标并取得必要选择。Maven 失败时 Agent展示退出状态和日志中的具体问题；成功时分别执行前端、后端、持久化、组装四个技术操作，并逐步检查真实结果。前端采集不依赖 Maven；缺少编译输入时可先独立完成前端，不能借此宣称后端已就绪。**Agent用到的是工具操作和结果解释能力，不是替Maven推导配置的能力。** 不要求额外付费模型、Codex专用session或额外Java审批服务。

当前共享Skill和配置示例已撤去手填v1语义JSON的指令。四操作设计仅调整前后端和组装的调用顺序，Maven真实文件交接不变；本次文档工作不编辑可执行Skill，不由另一个Agent临时补语义值来宣称框架通过。

## 7. 保存、失败与待实施项

Maven 原始命令/日志/classpath 作为本次外部准备附件保存；凭据、settings 密钥和敏感参数不进入公共报告或聊天。Java 将实际使用的编译环境及 JDT 检查写到既有 module 5 的两份正式文件，不再保存自建下载/解析阶段。

最小的程序问题是“缺交接文件”“输入文件不可读”“来源不符”“JDT 绑定失败”等消费错误。Maven 日志里什么原因造成依赖未导出，由 Agent 解释；不再设计数十种下载、mirror、Profile 错误码。

必须完成的替换和直接测试：

- **已实现并有定向 fixture：**技术配置 v2直接列 Maven 文件；读取器从有效 POM 提取事实，不再要求外部重复输入；内部生成私有 v2 记录。不是在 v1 外再加一层需要 Agent 维护的 JSON。
- **已实现并有定向 fixture：**配置绑定、输入指纹、排队后复核、BLOCKED 报告、Skill/示例迁移；旧 v1 观察保留，新执行不得双路线回退。R1 完成后移走原始 Maven 输出再接续 R2/R3 已通过直接测试；本次直接覆盖的四类测试在最终修改后合计 62 项通过，静态质量检查通过。固定源码的同源有效 POM 已取得，并有同值 compiler execution 的 typed-reader 覆盖；这不替代真实 JDT 验收。精确范围见[当前事实与修改边界](../../plans/technical-analysis-cli-and-vue-cleanup-design.md#2-当前事实不能重复开发)。
- **待验收：**同一组原始Maven输出经正式CLI生成同一环境，并在固定源码上通过实际 JDT 导航核验；给Agent的操作说明中不含手填业务/编译语义的隐含步骤。缺有效POM、未知编译设置、混源、JDK不匹配均在JDT前报告，Java的Maven/下载/业务模型调用为0。
- 已删除嵌入式 Maven preparer、传输器及仅用于该路线的配置/依赖/测试；保留来源、模块隔离、JDK、失败报告断言。因 `ApplicationProfileDetector` 仍读取已验证 POM 的静态技术线索，`maven-model` 不是该撤换路线的专属依赖，继续保留。
- C1 读取器将外部 classpath 保留在实际 `JavaCompilationEnvironment` 中；文件缺失、顺序、来源变化和模块映射错误均有直接测试。C2 已在具名真实 LS/Core 测试里把该环境交给 JDT；正式 `collect-code` READY fixture 已调用并发布这项能力，固定客户源码上仍需核验实际调用目标。
- Java `collect-code` 的 Maven 启动和下载计数为零；Skill 的外部执行是另一项需用户授权的动作。
- 正式 Agent 能展示 Maven 的真实结果，并在交接后消费准确文件；不以源码中的文字作为执行指令。
- 对同源真实例验证导航结果，而不是只验证 JAR 列表可读取。

直接测试已验证 JSON 读取、来源绑定、导出状态、缺失 classpath/JDK、模块顺序和 `AUTO_MAVEN` 拒绝；真实 JDT LS/Core 定向测试已验证 Java 8/17 双模块环境及跨模块调用。固定源码的具名定向测试现已核对嵌套、重载、Mapper及`logger.error`目标；真实全量新版 Java 索引及下游技术材料仍待发布验收，不能由具名测试推断全仓准确。首次授权的 Maven 导出在受限沙箱因仓库 DNS 失败；同一具名目标在获允许的网络环境成功导出 106 个 JAR 路径。此后同源有效POM也已取得并用于正式运行；两份文件可消费不等于项目编译或全量导航准确性已证明。未运行客户编译、测试、应用或业务模型。
