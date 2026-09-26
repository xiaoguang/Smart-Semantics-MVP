# Step02：自动准备 Maven 编译依赖

状态：2026-09-26 详细设计，**尚未实现**。用户已确认首版自动处理常规 Maven；本地缓存不足时只从配置明确允许的仓库取得文件；特殊扩展、生成器和客户构建不自动执行。依赖仍未就绪则保存报告、阻止 Java 调用收集，由 Agent 询问。本文替代原先“只接受外部清单、分析程序一律不下载”的目标约定；外部可信清单仍是显式输入方式，不是静默降级。

## 1. 为什么不是让模型找 JAR

确定版本需要父 POM、属性、BOM、Profile、依赖管理、传递依赖、排除和作用域共同参与。先按 Maven 规则得到选定模块的有效构建模型，再解析依赖，最后定位实际文件。不能先扫描缓存，把名称相近或版本较新的 JAR 塞进 JDT。

采用固定版本 **Maven Model Builder＋Maven Resolver**，由我们的 Java 模块受控调用，不自写继承/版本调解算法，也不调用 LLM 猜版本。Model Builder 负责有效 POM；Resolver 负责依赖图、冲突处理及实际 artifact 取得。Resolver 不能单独代替项目有效模型构建。依据及工具约束见[专项研究](../../supplements/java-dependency-preparation-research.md)。

首个原型固定Maven **3.9.15**的Settings/ModelBuilder/Resolver provider与其配套Resolver **1.9.27**，使用同版本Maven的依赖选择器、scope/optional/exclusion处理和图转换配置；参考对照也使用该版本。不是独立取各库最新版，更不能默认套用Resolver2的行为。[官方配套依赖](https://maven.apache.org/ref/3.9.15/maven-resolver-provider/dependencies.html)。此版本是本轮拟锁定工具，不是已经安装的产品依赖；若原型证明不适用须修改工具合同并重跑对应fixture。

三种方案比较：

| 方案 | 优点 | 本次决定 |
| --- | --- | --- |
| 仅接收外部编译清单 | 分析程序不需要准备依赖 | 保留显式 PROVIDED 模式，但不再是唯一方式 |
| 在客户目录直接运行 Maven 命令 | 接近现有构建环境 | 首版不采用；启动可能加载客户扩展，不能承诺零客户代码执行 |
| 固定工具内使用 Maven 的模型与解析库 | 复用标准规则，限制为声明式输入，不加载客户构建扩展 | AUTO_MAVEN 推荐实现；先通过有限原型再接线 |

本轮不开发 Gradle 自动解析。非 Maven、特殊 Maven 布局或必须执行生成器的项目返回明确原因；用户可选择提供受信任环境导出的完整结果。不得悄悄删掉不支持的配置后声称已完整解析。

## 2. 输入：先选定构建环境，再开始解析

内部 `JavaDependencyPreparer.prepare(request)` 返回不可变 `DependencyPreparationResult`，放在现有 `analysis.code` 内部环境职责下；不增加公共 Agent 方法或独立 CLI。准备发生在 `collect-code` 的 Step02 内，JDT 启动之前。

请求由组合根提供：

| 输入 | 精确含义 |
| --- | --- |
| 来源 | 完整 `SelectedSourceBasis` 和已验证只读来源；只读取有效未排除文件 |
| 根 POM | 一个明确的快照相对 `rootPom`；不能任选找到的第一个 POM |
| 分析模块/source set | 用户指定模块或该根实际激活的 reactor 模块；首版自动路径支持生产 Java `main`，不把测试依赖并入生产 |
| 构建选择 | 显式激活/禁用 Profile、用户属性、影响激活的 JDK/OS/环境输入；记录实际值或安全引用和来源 |
| 目标平台 | 每模块实际 release 或 source/target、目标 JDK/platform，区别于解析工具 JVM 和 JDT 工具 JVM |
| 仓库策略 | 允许仓库的 ID、URL、mirror 规则、release/snapshot策略、凭据引用及网络开关；不接任意 shell 命令 |
| 缓存 | 明确的本地缓存目录和本次私有下载/保存目录；不扫描全盘 |
| 工具与限额 | 固定 ModelBuilder/Resolver/传输器版本及摘要、超时、依赖数/深度/总下载字节上限 |
| PROVIDED模式 | 显式选择的可信环境清单及文件定位，和 AUTO_MAVEN 互斥；两种输入接受相同最终核验 |

缺少用户构建选择时，Agent 可以读 README/CI 声明，提出有出处的配置建议；不能把自己猜测的生产 Profile 当成程序发现。若采用 Maven 默认激活规则，配置必须明确选择这个模式，并固定其 JDK/OS/属性/文件存在性输入。报告显示实际激活 Profile，不称其必然就是客户线上配置。

构建激活使用的 JDK 与目标编译 JDK 可能不同。例如 Maven 在 JDK17运行而以 release8编译；不能以 JDT工具JDK26代替两者。环境变量、系统属性、settings 均不能无记录地继承开发者机器全局值。凭据不写入公共 JSON、指纹或命令日志。

文件激活的Profile（`exists/missing`）必须在固定投影中求值并记录路径/结果，不能交给默认`File.exists`任意读取宿主。路径出了有效来源范围、需要未知外部文件或取决于未保存的构建输出时返回`BUILD_SELECTION_REQUIRED`，不把“禁止读取”解释为文件不存在。只有确定属于有效枚举范围、确实未存在的路径才可计算missing=true；此前排除/未知目录也不能伪装为missing。

## 3. Java 内部依次做什么

### 3.1 验证来源与执行约束

读取根 POM及影响它的冻结构建描述。来源错误、损坏、路径越界或显式排除的必要 POM不能通过本机同名文件/缓存内容补回。只允许解析普通 XML 配置，不执行客户 wrapper、shell、hook、插件或应用。

`.mvn/extensions.xml`、POM中的 build extensions、特殊 packaging/lifecycle及修改源码根/生成代码的配置必须进入能力检查。不会启动 Maven CLI，所以这些声明不会自动变成可执行扩展。首版不认识且可能影响实际编译环境的项返回 `BUILD_FEATURE_UNSUPPORTED`，不能直接忽略。

`.mvn/maven.config`、`.mvn/jvm.config`、settings等不能直接交给启动器执行：仅由受限读取把支持的 Profile/属性映射成明确输入；未知开关或相互冲突的选择返回问题。实际凭据通过配置的私有引用注入受控传输器。网络访问策略由传输层/运行环境执行，不仅依靠模型或 Skill 的文字承诺。

采用固定工具helper装载上述库，沿用现有参数数组/进程/超时管理方式，不新增调度框架。helper执行classpath只含审阅并锁定的工具依赖，取得的客户POM/JAR只作为数据；不实例化客户Maven CLI/ProjectBuilder或extension realm，不加载客户`.mvn`。配置固定ModelBuilder的plugin processing行为并用原型核对；关闭plugin processing不能成为遗漏继承的Compiler配置的理由，无法准确解释编译选项时必须报告。受限文件/网络访问和取消后进程终止均要实测，不因使用库就宣称隔离已经成立。

### 3.2 建立有效 POM 与模块清单

1. 用 Maven Model Builder 解析根和被选模块。父 POM按 Maven规则定位：有效冻结范围内的 relativePath，或准确坐标对应的允许仓库内容；不读取快照外本机父目录。
2. 父 POM、导入BOM和外部依赖POM均保存实际坐标、内容摘要、来源和解析关系。父/BOM常常只有POM，没有要交给JDT的JAR。
3. 计算属性、继承、Profile效果、dependencyManagement和每模块有效依赖声明。聚合 `<modules>` 与继承 `<parent>` 分别记录，不能假定聚合根一定是所有模块的父。
4. 保存模块目录、有效源码根、目标平台、选定source set及依赖模块关系。嵌套模块、多级父、不同模块版本由标准工具处理；重复坐标、循环或需要未知扩展则报问题。
5. 源码根只能指向有效快照内容。相对parent在快照外时可以按准确坐标解析仓库父POM，但必须记录实际来源；被用户明确排除的必要构建文件不得以仓库替身绕过排除。

### 3.3 解析每模块实际依赖

由 Resolver 在有效模型、明确scope及仓库会话下计算依赖图，遵守冲突调解、optional、exclusion、classifier/type及BOM结果。生产编译需要的 `provided` 不可因未打包而丢弃；不纳入测试和仅运行期依赖冒充编译清单。

先核对配置缓存中的准确坐标/版本，再在允许联网时取得缺项；只下载所需POM、元数据和二进制，不枚举整个仓库。缓存命中也核验实际文件，保存独立内容摘要；远端checksum加本地hash说明所取得内容的一致性，不等于证明发行者可信。

POM中出现的新repository先经过有效mirror和allowlist判定；未获准时返回具体仓库和缺项，不直接访问。HTTP重定向也不能越过允许范围；认证失败不输出口令、不改用另一私库、不让 Agent 通过普通浏览绕过策略。版本范围/SNAPSHOT使用本次解析的确定结果和内容摘要，后续JDT不重新解析“最新”。

限额/网络失败保留成功取得的文件及未完成列表，但不能把部分列表标成完整。下载缓存可以复用；本轮未实现自动重算或不确定请求无限重试。

### 3.4 多模块：不是把所有依赖合起来

目标是按模块提供独立环境：

```text
common 模块 → common 自己的源码和依赖
web 模块    → web 自己的源码和依赖 + 确定的 common 模块关系
batch 模块  → batch 自己的源码和依赖（未选分析时明确列为范围之外）
```

对于相同源码版本内、普通Java jar布局的 reactor 模块：workspace模型提供其POM参与标准依赖图解析和版本调解；最终选中节点若准确对应被准入源码模块，表示为 `SOURCE_MODULE`，不虚构不存在的JAR，不随意 `mvn install`。外部节点才定位为 `BINARY`。附加classifier、生成字节码、特殊packaging不适用这个替换，缺实际产物则明确报告。

源码依赖模块即使不作为HTTP入口扫描目标，也需记录为编译支撑范围。用户排除了必要源码时不能默默转用含该源码的旧本项目JAR绕回。不同模块的同名类型和不同版本必须保持可见性边界；不能把所有source roots/classpath全局取并集。

**现状限制：**`VerifiedJavaProject`当前只有一套sourceRoots/classpath/sourceLevel；LS只生成一个project，Core请求也没有模块环境。必须修改项目环境投影与helper协议，不可仅替换JAR列表后宣称支持多模块。首版支持子集须通过父子/聚合分离、两模块依赖、相同FQN隔离fixture；不支持的布局返回阻断报告，不削减范围后称全仓完成。

### 3.5 生成内容和特殊构建

POM解析成功不代表 Lombok、注解处理器、代码生成器或客户插件产生的类型已经存在。首版不会启动这些程序补齐。可消费来自同一来源/构建选择的可信已保存生成源码或编译支持产物；未提供时列出缺少的生成内容及影响模块。

能力检查覆盖有效模型中编译前的插件执行、编译器参数/processor配置及依赖中可观察的processor注册，不只看能否匹配一个“生成器名称”。只有明确验证过的声明式配置可通过；未知且可能改变编译输入的配置默认阻断，或要求可信外部环境结果。已知仅在打包后处理、且不改变本次编译输入的声明可按经测试的规则保留不执行；不可凭名字猜其无影响。JPMS module-path、特殊source set或其它尚未支持的平台布局同样明确报告，不退成普通classpath继续。

需要外部生成内容时由 Agent 解释原因，用户选择提供材料或另行讨论受信任执行环境。**用户说“继续”不等于允许跳过缺项，不等于本实现已经具备执行客户构建的能力。** 普通源码变化/补入缺文件仍按源码准备的新版本规则，不在依赖准备里改客户源码。

## 4. 输出：实际编译环境与准备报告

`DependencyPreparationResult`包含：准备状态、每模块有效模型/源码根/平台、已解析二进制及顺序、源码模块边、生成内容、未完成范围、问题和保存引用。状态为 `RESOLVED / NEEDS_INPUT / UNSUPPORTED / FAILED`；只有RESOLVED能进入JDT环境核验，**RESOLVED本身还不等于Java导航READY**。

Step02 module5保存两份正式文件：

- `java-compilation-environment.json`：`java-compilation-environment-v1`，无凭据和本机路径的实际环境结果；部分失败也保存明确状态、已完成/未完成范围，不能伪装完整。
- `java-analysis-readiness.json`：`java-analysis-readiness-v1`，包含依赖准备、JDT诊断以及是否允许导航的最终报告，详见[Java就绪](java-readiness.md)。

POM/effective model、原始工具诊断、下载记录和私有文件定位按run保存为私有附件；正式环境结果通过受控内容引用关联它们，不复制口令。每个二进制记录坐标、resolvedVersion、type/classifier、scope、内容hash/size、仓库ID及有序classpath位置。模块边记录from/to、使用源码或实际产物；生成内容有自己的来源，不冒充源码准备已捕获的普通文件。

指纹包含真实源码/排除范围、模型输入及Profile结果、模块拓扑、依赖内容与顺序、目标平台和工具版本。文件存放目录、下载时刻、账户口令不决定Java语义身份。新请求开始时只绑定来源和准备配方；实际环境在本run准备完成后冻结为不可变值，并保存**私有阶段记录**，再计算`javaAnalysisBasis`并启动JDT。此时没有提前安装module5，也不能把私有记录放进`availableOutputs`。取得最终诊断或确定阻断后，才将实际环境、就绪报告两文件与module receipt一次正式安装。不得为了“启动前已经有最终环境”伪造结果或改写已登记请求。

## 5. 如何交给现有 JDT

现有传递机制可以复用，但有两处环境缺口必须修：

| 消费者 | 当前已核实 | 本次目标 |
| --- | --- | --- |
| JDT LS | `JdtProjectSession`生成临时`.classpath`，不是命令行传所有JAR；目前只有一个项目 | 每模块一个受控project，明确项目引用、按模块的classpath与实际target JDK；禁止Maven importer自行联网或执行扩展 |
| Core helper | JSON传classpath/sourcepath，调用`ASTParser.setEnvironment(..., true)` | 按当前文件的模块发送环境；新私有`jdt-syntax-v3`携带模块/平台身份及实际平台输入，不隐式使用工具JVM类库 |
| catalog/collect | 已有声明与导航/缓存 | 保留算法；目录可组合模块结果但MethodKey仍按准确源码定位；collect使用同一会话和实际环境 |

`true`会加入运行helper的VM bootclasspath；仅设置languageLevel不能证明使用的是客户目标平台。LS的`JavaSE-17`名字也不证明实际映射到了指定JDK。必须在C1原型中验证选定JDK/platform在LS和Core中一致，尤其JDK8/17目标不能看见仅较新工具JDK才有的API。JDK8 rt.jar、模块化JDK和release对应API的具体投影采用JDT支持机制，以实测固定，不在设计中假定“传一个JavaHome字符串就已经生效”。

目标平台或模块隔离无法可靠表达时返回 `UNSUPPORTED_JAVA_ENVIRONMENT`，不默认改为工具JDK或仓库JAR并集。协议v3修改只涉及环境，语法位置、完整正文、导航候选及缓存规则不重新开发。旧保存索引不需要重新打开旧helper；生产锁定新客户端/helper版本一致，版本不符明确失败。

## 6. Java、Skill、Agent、用户如何接力

没有“Java给Skill发消息”的服务。Skill是Agent阅读的操作说明；Agent执行CLI，Java通过stdout JSON及正式报告返回事实。需要决定时CLI保存报告后结束，不在后台等聊天回复。

最小CLI摘要是 `runId / operation / resultStatus / continuationStatus / availableOutputs / problems / requestedActions`。每个问题包含稳定code、模块/坐标/相关文件、发生操作、期望/实际、是否可重试、允许的后续动作。标准输出只写完整JSON，过程日志写stderr；秘密不出现在二者中。

下表中的code是**目标合同，非当前已实现错误码**：

| 原因 | 程序本次行为 | Agent应当问什么 |
| --- | --- | --- |
| `DEPENDENCY_DOWNLOAD_NOT_ALLOWED` | 保存缺项，未访问未允许仓库 | 是否为具名仓库开放此次下载，或提供准确文件？ |
| `REPOSITORY_AUTH_REQUIRED` | 保存脱敏仓库/缺项，不输出秘密 | 请在本机配置对应凭据引用，或提供已导出环境；不让用户在聊天粘贴密码 |
| `BUILD_SELECTION_REQUIRED` | 不猜模块/Profile/属性 | 根据已读取构建说明列明确可选环境，问采用哪一个 |
| `BUILD_FEATURE_UNSUPPORTED` | 不执行客户扩展/插件 | 提供可信环境结果，还是另行设计特殊构建支持？ |
| `GENERATED_INPUT_MISSING` | 记录模块/缺失支持产物 | 能否提供同源生成内容；不能靠确认将缺失当成功 |
| `SOURCE_BASIS_INVALID` | 不初始化工具 | 回源码准备核对版本/范围，不在这里替换源码 |
| `DEPENDENCY_TRANSFER_FAILED` | 保存本次已完成及未取得列表 | 用户是否要求修正网络后新执行；不无限重试 |

在原配置允许范围内取得依赖，不逐JAR询问。只有超出权限或输入不明时询问；Agent不得自行换版本、启用任意Profile、增repo或shell执行来补成功。用户确认后，把具名选择落实到新配置/准确输入，创建新的运行；不复活FAILED运行、不覆盖旧报告。仅下载缓存可正常命中，JDT结果不会因文件后来出现自动更新。

成功且用户原本要求做到材料组装时，Skill继续依次调用剩余两个命令，不增加每步人为审批。用户只要求收集代码则报告后停止。无论如何都不自动启动Activity/业务过程。

## 7. 可行性推演与实施前验证

正常例：父POM管理A2.0 → 子模块声明A → Resolver取得A2.0和实际传递B → 保存每模块环境 → 两个JDT工具使用同环境 → Step02诊断确认 → 现有Step03导航。父POM无JAR不会报缺JAR；不用LLM计算版本。

缺私库例：父POM不在缓存且仓库不允许 → 保存父坐标与权限问题 → Agent询问 → 用户配置后新run解析 → 旧报告保留。未取得父POM时不能从子POM猜一份classpath。

多模块例：web依赖同快照common → 两个有效模型参与解析 → common表示源码模块边 → 按模块投影JDT → 从web Service导航到common声明。若实际common类型由插件生成而未保存，则在导航前报告，不构造空模块。

必须先用自有fixture验证：

1. 多级parent、BOM、属性覆盖、profiles、provided/optional/exclusions及版本冲突与固定Maven受信任参考解析结果一致。
2. 默认缓存命中、缺项允许下载、未允许仓库/重定向/认证失败不越界；用本地测试仓库，不访问客户私库。
3. 聚合与继承不同、两模块源码依赖、同名类型与依赖版本隔离；不生成虚假jar、不运行install。
4. 扩展、插件、processor、wrapper各设置可观察执行标记，首版路径执行次数为0；未支持的构建明确阻断。
5. tool JDK与目标平台不同的负例；LS/Core都不能将新平台API误判为目标可用。
6. 结构化报告经正式CLI→Agent→artifact重开，权限/配置改变须新run，旧结果不变。

以上是实施验收要求，**本轮没有运行这些原型**。库能力有官方依据，具体组合、模块投影及目标平台仍需C1验证。只有原型与后续直接测试通过，才可宣称自动依赖准备可用；不能以“使用Maven”作为依赖绝对完整或导航绝对正确的证明。
