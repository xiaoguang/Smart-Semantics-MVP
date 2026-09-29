# C1-J：JDT 诊断完成性与目标 JDK 研究

状态：**历史研究及有界原型观察，不是当前就绪规范；未证明全仓 Java 就绪**。本页当时研究[技术分析计划](../plans/technical-analysis-cli-and-vue-cleanup-design.md)的 JDT 接缝。下列官方资料和自有 probe 事实保留，旧“全文件诊断完成才允许导航”及自建完成屏障建议已撤回；现行判断见[Java 环境与就绪](../modules/technical-analysis/java-readiness.md)。本轮没有运行客户工程、Maven 构建或 Step03 导航。这里的“模块”指 source-set 隔离的编译环境；JPMS `module-info.java`/module-path 是另外的能力。

当前交接由 Agent/用户使用 Maven 官方 `dependency:build-classpath` 取得逐模块有序路径，并显式提供源码根、source/target 或 release、目标 JDK 与模块关系；Java 核验提供文件的来源、存在与可读性及实际 JDT 绑定，不复现 Maven 模型或分类 Maven 失败。已知缺项、无效绑定和实际工具失败仍阻断；**全文件诊断覆盖未知可以继续**，须在结果中写明检查范围与未知，不能说全仓零错误。Maven 原始退出码/输出交 Agent 解释，本页不设计 Java 审批或修复系统。

## 结论

1. **现有 JDT LS push 协议没有可用的最终诊断屏障。** LSP `textDocument/publishDiagnostics` 是服务端通知，`version` 可选；空数组表示该次发布清除了此前诊断，并不证明“本次依赖与所有文件均已校验”。[LSP 3.17 push 规范](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/publishDiagnostics.md#L1-L7) · [参数定义](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/publishDiagnostics.md#L60-L84)。JDT LS 的 `java/validateDocument` 是 `@JsonNotification void`，其实现把校验放进 `computeAsync`；文档处理器随后可取消并延迟调度诊断 job，且不适用的 URI 可直接返回。它没有返回“该文件及项目已终结”的响应。[扩展接口](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/lsp/JavaProtocolExtensions.java#L777-L779) · [服务端实现](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/JDTLanguageServer.java#L3890-L3911) · [延迟/过滤/发布路径](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/BaseDocumentLifeCycleHandler.java#L2381-L2469)。`endReporting()` 确实推送一个可能为空的列表，但构造的 `PublishDiagnosticsParams` 没有设置版本；诊断过滤还可能让它根本不推送。[JDT LS 诊断发布](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/BaseDiagnosticsHandler.java#L1298-L1325)。协调者自有探针报告：本次 `initialize.capabilities.diagnosticProvider=null`，一次 `java/validateDocument` 后见 `version=null` 的通知、没有终止标记；这是该探针的观察，不是所有版本的定理。LSP 的同步 pull 文档诊断**只有服务端声明 `diagnosticProvider` 并实际实现时**才是备选协议，本次探针不具备此条件。[LSP 3.17 pull 规范](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/pullDiagnostics.md#L54-L104)。

2. **JDT Core 可同步给出某次解析的有界诊断，但 `CompilationUnit.getProblems()` 不是完整构建证明，也不等待 LS。** `ASTParser.createAST()` 同步返回 AST；在 `K_COMPILATION_UNIT`、确定的源码字节、unit name 和显式环境下，成功返回后读取该 AST 的 `getProblems()`，可以把“这次解析返回空问题”与“未收到任何 LS 通知”区分开。[ASTParser `createAST`](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html#createAST(org.eclipse.core.runtime.IProgressMonitor)) · [`setEnvironment`、`setUnitName`、`setForceProblemDetection`](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)。官方明说 `getProblems()` 的详细问题列表**可能只是 Java 编译器检出错误的子集**，不代表整个工程 builder、项目 marker、其他文件、生成代码或 LS 的最终诊断。[CompilationUnit API](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/CompilationUnit.html#getProblems()) · [Eclipse 编译/marker 与 reconcile 指南](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/guide/jdt_api_compile.htm)。本研究当时提过新增受控 LS 侧 reconcile 请求取得完成状态；该**自建完成屏障方向已撤回**，不再是导航前置或后续实施任务。[ICompilationUnit `reconcile` 语义](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/ICompilationUnit.html)。

3. **语言级别、工具 JVM、目标 JDK 是三个不同输入。** JDT Core `setEnvironment(..., true)` 会把*运行 helper 的 VM bootclasspath 前置*，所以仅将 `source/compliance/targetPlatform` 改成 `1.8` 仍可能看见较新 JDK API。[ASTParser `setEnvironment` 合同](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html#setEnvironment(java.lang.String%5B%5D,java.lang.String%5B%5D,java.lang.String%5B%5D,boolean))。LS 的 `JavaSE-8/17` 容器名称也不是实际安装路径的证明：JDT LS `JVMConfigurator` 从配置 runtime 注册 VM、验证安装并把兼容 VM 设为 execution environment 默认；兼容不保证严格同一版本。[JDT LS JVMConfigurator](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/JVMConfigurator.java#L100-L182) · [environment 默认映射](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/JVMConfigurator.java#L217-L245)。必须核对**实际选中的 VM 安装位置和库**，而非容器字符串。Eclipse 提供 `JavaRuntime.getVMInstall(IJavaProject)` 与 `getLibraryLocations(IVMInstall)` 读取此事实。[JavaRuntime API](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/launching/JavaRuntime.html)。

## 当前仓库证据与缺口

| 输入、处理、输出 | 当前事实 | 缺口 |
| --- | --- | --- |
| 已核实源码、根、classpath、sourceLevel → JDT project | `VerifiedJavaProject` 只有单组环境，且仅确认列出的 JAR 文件存在/摘要未变；[实现](../../src/main/java/org/sourceanalysis/app/analysis/code/VerifiedJavaProject.java#L25-L54) · [验证](../../src/main/java/org/sourceanalysis/app/analysis/code/VerifiedJavaProject.java#L81-L90) | 未表达每模块目标平台/项目边与完整依赖闭包；文件 hash 不能证明缺项不存在。 |
| project → LS 投影/探针 | 当前创建单个 `.project/.classpath`，给每个源码根、所有 JAR 和按 sourceLevel 命名的 JRE 容器；[投影](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtProjectSession.java#L276-L335)。`initialize` 后只检查首个 Java 文件的声明或 `ServiceReady`；[探针](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java#L337-L380)。LS 启动 JVM 是配置的 `javaHome`；[命令](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java#L284-L296)。 | 未记录/核对 JRE 容器解析到哪个目标 JDK；单文件声明成功不证明所有文件或项目诊断已完成。LS 设置禁用自动构建/导入，且没有 runtime 列表；[设置](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java#L438-L450)。 |
| LS 诊断 → 当前消费者 | `SessionLanguageClient.publishDiagnostics()` 空实现；[代码](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java#L840-L849)。 | 目前没有 LS 诊断消费链或诊断分母；不能说现有 Step02 已判定 Java 就绪。 |
| 源码 → Core AST/诊断 | 当前**开发分支**已将 helper 私有协议升为 `jdt-syntax-v3`，增加目标 JDK 与平台库条目；Reader 改为 `setEnvironment(..., false)`；宿主旧的单项目启动重载暂时拒绝缺少已核实目标平台的请求。[协议](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxProtocol.java) · [Reader](../../tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxReader.java)。自有平台 fixture 与宿主传输共 3 个定向测试已过。 | 尚无生产的逐模块目标平台构造及 LS 同环境核对，旧单项目会话因此尚不能完成新生产链；Core API 还允许 recovered/incomplete bindings，不能仅凭一个非空 binding 判定环境正确。[ASTParser bindings recovery](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html#setBindingsRecovery(boolean))。 |

协调者的独立 scratch 观察为：显式 JDK8 `rt.jar`、language level `1.8`、`setEnvironment(..., false)` 对 `Optional.isEmpty()` 报 ERROR，目标 17/level 17 则没有该错误。这支持目标平台负例设计，但只是一个样本，尚未证明 v3 helper、所有模块或 LS 同步配置。当前 Core helper 锁定 `org.eclipse.jdt.core:3.47.0`，[POM](../../tools/jdt-syntax-helper/pom.xml#L13-L28)；LS 分发版由运行配置提供，[版本记录](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java#L144-L148)，本研究未取其精确运行 artifact/hash。官方 Core 对显式 system library 也出现过版本相关失败报告；因此平台路径的具体格式不能只靠 API 文本决定，须在**仓库锁定版本**实测。[Eclipse JDT Core issue #4879](https://github.com/eclipse-jdt/eclipse.jdt.core/issues/4879)。

上述 scratch 观察后来在固定 helper 构建上收窄重测：同一个 `JdtCorePlatformProbe` 以工具 JDK 26 运行，显式传入目标 JDK 17 的 `lib/jrt-fs.jar`、语言级别 17，得到 `PROBLEM_COUNT=0`；改传目标 JDK 8 的 `jre/lib/rt.jar`、语言级别 1.8，则对 `Optional.isEmpty()` 得到一条“method undefined”问题（`PROBLEM_COUNT=1`）。两次均使用 `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar` 和 `includeRunningVMBootclasspath=false`。这证明该受限 Core API 样本不会无条件使用运行工具的 JDK 26 API；**仍未证明**生产 v3 helper 的逐模块路径、LS 与 Core 的实际平台一致、项目级诊断完成或任意 Java 17 项目的可分析性。探针源码与运行记录位于临时目录 `/private/tmp/source-analysis-maven-XllsOS`，不作为产品产物。

随后只读检查了本机已安装的 LS 核心包 `org.eclipse.jdt.ls.core_1.61.0.202609021834.jar`（位于配置所用的 VS Code Java 扩展 `server/plugins`），未启动客户工程。该具体版本的 `ProjectCommand.ClasspathResult` 只有 `projectRoot/classpaths/modulepaths`；单靠 `java.project.getClasspaths` 响应不能核对项目实际选中的 VM。对同一包执行 `javap -c -p` 可见 `ProjectCommand.getProjectSettings` 接受 `org.eclipse.jdt.ls.core.vm.location` 和 `org.eclipse.jdt.ls.core.classpathEntries`，前者实际调用 `JavaRuntime.getVMInstall(project).getInstallLocation()`，后者读取项目 classpath 条目。这使“分别查询并比对目标 VM 与项目路径”有了锁定版可测试入口，**还没有**证明命令的实际 JSON 响应、环境完整性或诊断终态。`BuildWorkspaceHandler.buildWorkspace` 虽存在，但可能触发项目 builder，不把它当作本轮禁止客户构建情况下的只读就绪屏障。

同一锁定包的 `JDTDelegateCommandHandler` 字节码还表明，`java.project.getSettings` 从 `workspace/executeCommand` 参数列表取第 0 项作为项目 URI 字符串，第 1 项作为设置键字符串列表，再调用上述 `ProjectCommand.getProjectSettings`。这是**准备定向探针的接口形状**，不是探针已经返回正确值的证明。后续必须在自有项目 fixture 上实际发请求，核对 JSON 中的 VM 安装位置和 classpath；若读取值与受控目标环境不一致，就阻断，而不是信任配置文件中写的目标 JDK。

### Runtime 注册 wire 的补充核对（等待真实 LS probe）

JDT LS 的偏好键是 `java.configuration.runtimes`，所以当前 client 的
`initializationOptions.settings.java` 对象应在**初始化请求之前**扩展为
`configuration.runtimes` 数组，而不是另设顶层配置。上游解析器只接受每个数组元素中的
`name`、`path`、可选 `javadoc`/`sources` 和布尔 `default`；`name` 或 `path` 缺失会使该
runtime 无效，重复 `name` 也不会注册第二个 runtime。[偏好键与解析器](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/preferences/Preferences.java#L129-L132) · [runtime 字段与有效性](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/RuntimeEnvironment.java)

最小候选 wire（路径和值均来自已经核验的目标平台，而非工具 JVM）是：

```json
{
  "settings": {
    "java": {
      "configuration": {
        "runtimes": [
          {
            "name": "<verified execution-environment name>",
            "path": "<canonical target-JDK home>",
            "default": true
          }
        ]
      }
    }
  }
}
```

上游实现对**遇到的第一个** `default` 字段即停止处理随后元素的 `default`；即便第一个值是
`false` 也如此。因此生产投影应只给一个已选 runtime 写 `default: true`，其余元素完全省略
该字段，而不是为每个元素发送 `default: false`。[默认值解析](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/preferences/Preferences.java#L1587-L1641)

这还要求把 execution-environment 名称建模为已核验 target-platform 的独立字段，而不是由
`sourceLevel` 拼接。当前 `.classpath` 固定生成 `JavaSE-` + source level；例如 `8` 会得到
`JavaSE-8`，是否能与目标 JDK 8 注册项匹配必须由锁定版的真实 readback probe 决定，不能在
本研究中把它改写为记忆中的 `JavaSE-1.8`。每个模块的 JRE container 末段必须与该模块绑定的
runtime `name` 逐字相同；一个 LS workspace 可注册多个不同 runtime，但仅一个可带
`default: true`。

待 real-LS 自有 fixture 返回后，最小 GREEN 顺序是：

1. 将每模块的 canonical target-JDK home、平台身份和**实际接受的** environment 名称冻结为
   不可变 runtime bindings；不触碰运行 LS 的 `toolJavaHome`。
2. 从所有 module bindings 去重生成上述 runtime 数组，并让每个投影 `.classpath` 引用对应的
   runtime name，而非全局 source level。
3. 每个投影 project 在启动后调用现有 `java.project.getSettings` readback，比较
   `org.eclipse.jdt.ls.core.vm.location` 与该模块的 target-JDK home；`classpathEntries` 不含
   JRE container，故只作为受控源码/二进制输入的辅助核对，不能代替 VM 比较。
4. 任一 runtime 没注册、容器未解析到预期 VM、或 readback 不完整即阻断；不得回退到工具 JDK。

需要先有的直接 RED seam 是一个自有真实-LS fixture：工具 JVM 与目标 JDK 不同，记录
initialize 的 runtime wire、投影 container 名称和两个 readback 值。它应先证明锁定分发版接受
的名称/路径及 project VM 实际位置；没有该结果，以上只是受限实现方案，不能声称目标 JDK
隔离或 Java READY。
该锁定版 `ProjectCommand.getJavaProjectFromUri` 先尝试将 URI 解析为 Java 类型单元；否则按 URI 在 Eclipse workspace 中查找项目容器。因此定向探针可使用已投影项目的根 URI；若它未归属一个 Java 项目，服务端明确报错，不能拿“空设置”当作就绪结果。这也只确认目标版本的字节码行为，仍需实际请求验证。

现有测试框架中的假 LS JSON-RPC 进程可核对宿主发送的命令形状和错误映射，**不能证明真实 LS 注册/返回的项目设置**。真实 readback 应使用自有极小 Java 项目和锁定 LS 分发版做具名定向集成探针，比对目标 JDK（不是工具 JVM 的偶然相同值）及模块源码/二进制 classpath，并用工作区外 URI 验证错误不会被解作空成功。进一步只读检查发现该版本的 `classpathEntries` 分支**跳过 JRE container**；所以不能要求它本身包含目标平台 JAR/JRT，更不能仅凭它证明系统库身份。目标 VM 安装位置要单独用 `vm.location` 读回，必要时核对其库内容；`referencedLibraries` 是另一条辅助设置键。当前 `JdtProjectSession.open` 可只启动 LS 并完成声明探针；会在后续 `catalog()`/采集需要启动 v3 syntax helper 时，因缺少已核实目标平台主动失败。因此 readback-only 探针可以停在 LS 项目设置查询，**不得**由该结果声称整个会话、Core 解析或诊断已经 READY。

## 撤回的全文件完成性方案（历史推演，不作为实施验收）

当时拟维护 `expectedSourcePaths = checked ⊎ unchecked`、逐文件请求身份和项目 marker 终态，必要时新增 LS 侧 reconcile 请求，才允许导航。这些是**撤回的设计假设**，没有成为生产验收。官方 JDT Core 的同步 AST 问题列表仍可用于具体文件的有界观察；LSP push 的缺席仍不能解释为零错误。逐模块独立 project、源码模块引用及目标 VM 的实际绑定属于导航准确性要求，见[Eclipse IJavaProject](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/IJavaProject.html)、[IClasspathEntry](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/IClasspathEntry.html)及[当前就绪设计](../modules/technical-analysis/java-readiness.md)。

## 当前最小验收与仍不能证明的事

当前只对已交接的逐模块路径/元数据做来源、存在、可读性与 JDT 项目绑定核对；用现有 Step03 的具名 Mapper、logger.error、重载、嵌套调用与多态样本复验。已知缺依赖、项目绑定错误或工具失败阻断；诊断未覆盖所有文件时保存已观察问题、范围和未知，允许继续。无需为此遍历每个 Java 文件制造完成证明，也不启动客户构建。以下旧研究限制仍成立：

- 同步 Core 检查完成 ≠ LS 诊断最终到达 ≠ Eclipse builder 完整编译。`getProblems()` 明示可能是子集；项目级 marker、跨文件增量变化、annotation processing、客户生成器和实际 `javac --release` 的全部语义均需额外证据。普通逐模块 classpath 成功不能推广到 JPMS module-path 或特殊 source set；未支持范围以[当前依赖交接设计](../modules/technical-analysis/dependency-preparation.md)为准。
- 现有有界 fixture 与目标平台 probe 只支持固定工具版本和具名场景的判断；不是 jshERP 全仓执行、任意 Maven 项目编译等价、所有 Step03 调用准确率或产品发布证明。修订后的交接与生产接线仍待直接验证。
