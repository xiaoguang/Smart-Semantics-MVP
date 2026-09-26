# Step02 Java 编译依赖自动准备调研

状态：**设计研究；未实现、未作 API 原型、未执行客户 Maven/JDT/模型，也未下载依赖。** 本文供 Step02–05 详细设计吸收；现行正式合同仍以 `docs/modules/technical-analysis/java-readiness.md` 等 owning 文档为准，不能把本文写成已运行的解析结果。

## 结论与首版边界

推荐在 Step02 内新增一个**固定版本的声明式 Maven 准备器**：受控 Java 代码读取已准入的 POM/配置文本，用 Maven 3 同一版本族的 Settings Builder、ModelBuilder、Resolver provider/Resolver 计算各模块的有效模型与生产编译依赖；可按用户批准的仓库配置复用本地缓存并下载缺失的 POM/JAR。准备器只调用固定库 API，不调用客户仓库中的 `mvn`/`mvnw`、Maven CLI、ProjectBuilder、生命周期、插件目标或客户扩展。它产生可核验的**每模块**编译环境及问题报告，再由 Step02 判断就绪，Step03 只消费同一环境做已有导航。ModelBuilder 的官方职责是 profile 激活、继承、插值和 BOM import；Resolver 官方明确说自己没有 Maven 的有效模型“胶水”，仅凭 Resolver API 不足以复现 Maven 工程。[Maven Model Builder](https://maven.apache.org/ref/3.8.3/maven-model-builder/) · [Resolver 工作机制](https://maven.apache.org/resolver/how-resolver-works.html) · [Settings Builder API](https://maven.apache.org/ref/3.9.15/maven-settings-builder/apidocs/allclasses-index.html)

这是一条**支持子集**，不是通用 Maven 构建器。普通 POM 声明、父子继承、导入 BOM、标准 profile、传递依赖、scope/排除/可选依赖及配置仓库是首版目标；扩展改写模型、生命周期产生源码/字节码、非标准 packaging、不能确认的平台和复杂模块产物则报告 `UNSUPPORTED`/`INCOMPLETE`，不标 `READY`、不继续 Java 导航。用户已确认常规 Maven 自动解析及必要的配置仓库下载；特殊扩展、生成器或构建脚本需另行确认并由可信环境提供其产物。绝不把“下载了 JAR”推论为“执行客户代码”，也不把“没有执行 Mojo”误写成“远端内容可信”。

## 两种技术路线与安全边界

| 路线 | 能取得什么 | 代价与边界 | 取舍 |
| --- | --- | --- | --- |
| 固定嵌入 ModelBuilder + Resolver | 对受支持的 POM，建立有效模型、依赖图、冲突调解和本地二进制；可单独限制读取、网络及是否解析插件 | 要自行装配模型解析、settings、仓库 session、工作区 POM、scope、模块映射和重现性；不能假设等同 Maven CLI 全部行为 | 首版主选，先原型验证，再扩大范围 |
| 受控 `mvn`/`mvnw` 或 `dependency:build-classpath` | 与实际 Maven 安装/项目扩展更接近，可输出依赖路径 | 会启动 Maven 及插件目标；CLI 读取 `.mvn/maven.config`、`jvm.config`、`extensions.xml` 与用户/全局 settings；core/build extensions 可进入 Maven 进程，不能承诺客户代码不执行。`dependency:build-classpath` 本身是一个 Mojo，要求项目且要求 `test` scope 解析；直接调目标不等于运行整个 `generate-sources` 生命周期，但也不是纯数据读取 | 不用作零执行主路；仅在明确受信任构建环境由用户执行并导出完整清单时接收 |

Maven 官方说明 `.mvn` 三种文件均属项目可提交配置，`MAVEN_ARGS` 可预置 CLI 参数，core extension 可进入 Maven Core classloader并参与生命周期；POM 的 build extension 还可能在 ProjectBuilder 创建 project realm 时加载。于是“只调一个 Maven goal”不足以构成隔离证明。[Maven 配置入口](https://maven.apache.org/configure) · [Core extension 官方研究](https://maven.apache.org/components/studies/extension-demo/) · [ProjectBuildingHelper API](https://maven.apache.org/ref/3.9.15/maven-core/apidocs/org/apache/maven/project/DefaultProjectBuildingHelper.html) · [dependency:build-classpath](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html) · [生命周期与直接 goal](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle)

“嵌入 API 不执行客户插件/扩展”是**拟议装配的约束**，不是 ModelBuilder+Resolver 对任意接线的自动保证：只使用 Agent 固定类路径和组件，不导入客户 `.mvn`，不创建 Maven CLI/ProjectBuilder/session 的客户扩展 realm；检查并拒绝 POM 中 `<build><extensions>`、`<plugin><extensions>true>`，不解析其二进制或加载它们；`ModelBuildingRequest.setProcessPlugins(false)`，不调用 Mojo/lifecycle/编译/注解处理。ModelBuilder 仍会解析模型文本、profile 文件条件和父/BOM POM；是否有其他代码加载点及上述开关在所选锁定版本的效果，必须用原型及依赖审计证明。POM/仓库元数据均视为不可信输入，限制文件路径、解析大小/深度、仓库主机、超时、下载大小、重定向和凭据使用；隔离进程是建议的进一步边界，不能仅因用了库便宣称沙箱已实现。[ModelBuildingRequest API](https://maven.apache.org/ref/3.9.9/maven-model-builder/apidocs/org/apache/maven/model/building/ModelBuildingRequest.html) · [Maven 插件与 Mojo 语义](https://maven.apache.org/guides/introduction/introduction-to-plugins.html)

## 最小数据流：从 POM 到每模块 classpath

1. **冻结输入和构建上下文。** 仅从 Step01 有效范围中取明确选择的 root/模块 POM；保存其内容身份、模块相对路径、选择的 Maven 主版本/固定库版本、生产 source set、显式 profile ID、用户属性、可见的 OS/JDK/环境属性、settings 来源的非秘密指纹、仓库/mirror ID、离线/在线选择及目标 JDK。Profile 可由 `-P`、JDK、OS、属性或文件条件触发，激活早于完整插值；文件条件若需读有效范围外客户文件，则不能暗中读取并推断为已重现。Settings 可能定义 active profiles、repositories、mirrors、proxy、server 凭据和 localRepository；凭据只留在私有 resolver session，公共产物只保留安全标识。[Profile 官方说明](https://maven.apache.org/guides/introduction/introduction-to-profiles) · [Model Builder 顺序](https://maven.apache.org/ref/3.8.3/maven-model-builder/) · [Settings 参考](https://maven.apache.org/settings.html)
2. **构建有效模型。** 为每个受支持模块调用 ModelBuilder，模型解析器优先以已准入 workspace POM 匹配父 `relativePath`/同反应堆坐标与 BOM `pom`，其余按被批准的仓库解析。校验聚合 `<modules>` 指向的 POM 在固定来源内、GAV 唯一、父/BOM 版本和 property 全可解析。父子继承、BOM import、dependencyManagement 注入需用 Maven 实现而非自己扫 XML；聚合不是继承，`dependencyManagement` 也不等于实际依赖。[Model Builder 步骤](https://maven.apache.org/ref/3.8.3/maven-model-builder/) · [依赖管理/BOM](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) · [多模块 Reactor](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
3. **按模块收集图并选择编译范围。** 给 Resolver 当前模块的有效直接依赖、受管理依赖与有效仓库，执行图收集/冲突调解，保留顺序和冲突来由；仅取生产编译 classpath（`compile`、直接所需 `provided`/`system` 及合规传递项；不要混入 `runtime`/`test`）。解析外部节点的实际文件，核对坐标、分类器、scope、文件 hash/大小；处理 optional/exclusions、版本范围、SNAPSHOT、relocation 及缺失 POM/JAR 时留下具名问题。Resolver 文档将 collection、conflict resolution、flatten 和 payload resolution 区分；Maven 依赖指南有 scope、传递、nearest/同层声明顺序规则。[Resolver 工作机制](https://maven.apache.org/resolver/how-resolver-works.html) · [依赖机制](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
4. **将 reactor 源码模块边单列。** `WorkspaceReader`/Maven 的 `MavenWorkspaceReader.findModel` 可让同快照 POM 参加 parent/descriptor/依赖图解析；图完成后按精确 GAV、类型、分类器区分普通 `jar` 模块源码边和外部二进制。对普通源码模块，输出 `moduleDependency`，而不是要求一个并不存在的 JAR、用本机旧 `~/.m2` 同坐标 JAR 替代，或伪造空 JAR。外部节点才下载/验证 JAR。`pom` 聚合/父/BOM 只是元数据，不是编译类路径二进制。特殊 classifier、插件附加产物、非普通 JAR packaging 或二进制已经生成但来源身份不能证明的模块须阻断/交可信产物。官方 `WorkspaceReader` 能定位工作区产物，Maven 自身 ReactorReader 注释说明可能返回已打包文件或编译输出；**它不把未编译源码自动变成 JAR**。[WorkspaceReader API](https://maven.apache.org/resolver/maven-resolver-api/apidocs/org/eclipse/aether/repository/WorkspaceReader.html) · [MavenWorkspaceReader API](https://maven.apache.org/components/ref/3.9.15/apidocs/org/apache/maven/repository/internal/MavenWorkspaceReader.html) · [Maven ReactorReader 源码](https://maven.apache.org/ref/3.9.0/maven-core/xref/org/apache/maven/ReactorReader.html) · [Reactor 顺序](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
5. **每模块投影和验证。** 对支持的无环普通模块拓扑，为每个 Java 模块建立自己的源码根、外部 JAR 有序列表、已确认的上游源码模块可见性、目标 JDK/`--release` 语义及独立 JDT project/classpath；若 JDT LS 和 Core helper 尚不能用同一模块环境，先只支持单模块，并为多模块给出 `MODULE_CLASSPATH_UNSUPPORTED`，绝不把所有模块依赖并集塞给一个项目。Step02 保存源、模型、下载、图、平台、范围、诊断和 ready/basis；Step03 仍是既有 catalog/collect/缓存/导航算法，不添加第二个调用解析器。

上面第 4–5 步是**设计推论**，不是 Resolver 或本项目现成集成接口。特别要证明：同坐标不同版本选中结果、reactor POM 的传递依赖、模块源码跨 project 定义/调用导航、一个模块的 private/provided JAR 不泄漏到另一个模块、分类器/生成产物阻断。若不能在固定库 API 上稳定收集工作区 POM 图，首版缩为一个真实编译模块加外部依赖，不能手写简化 Maven 版本调解器。

## 源码、生成内容和目标 JDK 的不可省略条件

有效 POM 不等于实际编译输入。`generate-sources` 可通过插件生成新源码，Compiler Plugin 注解处理还会写 generated sources；ModelBuilder/Resolver 不执行这些动作，也不会自动发现所有插件追加的 source roots 或生成字节码。声明在普通 POM `<build><sourceDirectory>` 的静态根可核对；由插件运行时增加的根、Lombok/MapStruct 等注解处理、`target/generated-sources` 和 reactor 已编译输出必须来自**与冻结来源绑定的可信外部产物**并逐文件准入/核验，或阻断。首版需核对有效模型里的 pre-compile 插件执行/编译器参数及明确的受支持白名单；未知插件可能添加源码，不能因未识别为“生成器”就通过。不能碰巧读到客户工作树旧 `target/` 就算完整。[Maven 生命周期](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle) · [Compiler Plugin generatedSourcesDirectory](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html) · [注解处理说明](https://maven.apache.org/plugins/maven-compiler-plugin-4.x/examples/annotation-processor.html)

`source`/`target`/`release` 与**真实 Java API 平台**分开。Maven Toolchains 允许目标 JDK 与运行 Maven 的 JDK 不同；仅有 POM 属性或 `maven.compiler.source` 不足以推出 JDK 安装和 platform modules。JDT LS 的工具 JVM 亦不是目标 JDK。若目标 JDK 或多 release/module path 不能确认，返回 `TARGET_PLATFORM_UNVERIFIED`；首版可明确只支持普通 classpath Java 项目，`module-info.java`、多 release、特殊 compiler/toolchain 参数先不声称 READY。[Maven Toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains) · [Compiler Plugin compile goal](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html)

本地代码核对表明平台问题是现实缺口：`VerifiedJavaProject` 只有一组 `sourceRoots`、一组 `classpath`、一个 `sourceLevel`，其 hash 绑定已列 JAR 内容，但未表达模块图/目标 JDK/完整解析状态；`JdtProjectSession` 只建一个 project，`.classpath` 写全部根、全部 JAR 与 `JavaSE-<sourceLevel>` 容器；`JdtLanguageServerClient.openSyntaxHelper` 从该 project 取同一组路径交给 `JdtSyntaxHelperClient`，后者将路径写进 helper 请求；`JdtSyntaxReader` 用这些路径调用 `ASTParser.setEnvironment(..., true)`，并把 source/compliance/targetPlatform 三项都设为 languageLevel。Eclipse 官方 API 明言 `true` 会把**运行 helper 的 VM bootclasspath**加入环境；`JavaSE-<sourceLevel>` 名称也需要实际映射/安装的 JRE 才能证明平台。因而如果工具运行 JDK 26、目标 JDK 8/17，helper 可能看见目标平台没有的 API；只调低 sourceLevel 不构成平台隔离。这是基于本地源码与官方 API 的风险推论，尚未用实测证明已产生错边。[ASTParser.setEnvironment API](https://help.eclipse.org/latest/ntopic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html) · [Eclipse Execution Environments](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/reference/preferences/java/debug/ref-execution_environments.htm)

## Step02/Skill 协作与失败报告

Java 负责确定性读取、有效模型、依赖图与产物核验；Agent Skill 解读结构化报告、向用户询问它无法从冻结输入判定的环境/权限，不让模型猜坐标、版本、profile 或替代 JAR。**已经确认的用户选择**：常规 Maven 自动解析，可使用所配置的仓库下载；特殊扩展/生成器/构建脚本另行确认，未就绪时不导航。具体仍须由用户或可信环境给出：分析哪个 Maven root/模块及生产或测试范围；实际激活 profiles/关键 user properties 与目标 JDK/toolchains；私服 settings/mirror/proxy/凭据是否可用且哪些 host 准入；若出现生成内容或扩展，可信构建产物的位置、来源证明与是否授权另行构建。缺其中会改变有效依赖/平台的事实时标 `UNDETERMINED`，不能默认 Central 或当前 JVM 来消除问题。[Settings](https://maven.apache.org/settings.html) · [Profiles](https://maven.apache.org/guides/introduction/introduction-to-profiles) · [Toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains)

报告最少按模块保留 `sourceVersionId`/有效范围、POM 与非秘密配置指纹、激活 profile/属性证据、目标 JDK、依赖图选择及原由、已解析 JAR 的 hash/大小/本地私有路径、workspace 模块边、缺失 POM/JAR、仓库/网络失败、生成内容缺项、unsupported 特性、JDT 检查分母及诊断完成状态。凭据、服务器密码、机器绝对路径不得流入公共材料；私有缓存路径本身不是 artifact identity。仅 `environment VERIFIED ∧ diagnostics COMPLETE ∧ 无阻断问题` 才可 `READY`。失败仍保存可查看报告，不伪造空 Step03 索引；修复后新建 `javaAnalysisBasis`，旧材料保持旧来源/依赖身份。此处复用现行 readiness 概念，字段及最终 schema 由 owning 文档决定。

## 后续必须做的最小原型，不是本轮结果

| 夹具/验证 | 应看到的证据或阻断 |
| --- | --- |
| 自有父 POM + 两子模块 + imported BOM + 一个 reactor 普通 JAR 边 + 外部传递依赖 | 每模块有效版本、scope、冲突与有序 classpath；reactor 边作为源码模块，旧本地同坐标 JAR 不进入 |
| 显式/自动 profile、settings mirror/私服、离线缓存和缺件 | profile 激活与仓库选择可重现；缓存命中不下载；缺件报告精确原因；凭据不落公共产物 |
| `.mvn/extensions.xml`、POM build extension、生成源码插件与 annotation processor 声明 | 准备器不加载/执行客户代码，标 `UNSUPPORTED` 或要求可信外部产物；不能因 graph 成功而 READY |
| A/B 子模块各有冲突版本或独有 JAR、跨模块调用 | 独立 JDT projects/helper 环境、不泄漏依赖；真实源码跨模块定位与保存身份一致。若失败，首版只支持单模块 |
| 目标 JDK 8/17、工具 JVM 较新且引入只在新 JDK 才有的 API | helper 和 LS 均不得把新 API 当作目标平台存在；平台映射或协议无法证明时 `TARGET_PLATFORM_UNVERIFIED` |
| 固定相同源码的错误 Mapper、`logger.error`、重载、嵌套调用、合法多态 | 只用现有 Step03 导航核对补依赖前后差异，并确认 Step04/05 不消费错边；不由本调研宣称测试已通过 |

该原型须先锁定 Maven/Resolver/JDT 具体版本；Maven 3 与 Maven 4、Resolver 1.x/2.x 的默认行为不能混写。上表是验收设计，没有在客户 jshERP 或本地工具上运行。首版需要的不是“100% Maven 等价”，而是可解释的完成范围、可复现的模块 classpath 和无法安全证明时的停止条件。[Resolver 版本行为说明](https://maven.apache.org/resolver/how-resolver-works.html)
