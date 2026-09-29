# Step02 Java 编译依赖准备：历史研究与当前交接边界

状态：本页是**撤回方案的研究记录**，不是生产实施合同。原研究建议在 Java 内嵌固定 Maven ModelBuilder、Settings Builder、Resolver 与下载传输器，随后再重建 reactor 模块关系。该方向已撤回；原研究写作时未做 API 原型或客户 Maven/JDT 运行。后续有界原型的实际观察由[依赖交接设计](../modules/technical-analysis/dependency-preparation.md)记录，不能因撤回方案而伪称原型不存在，也不能把局部原型成功当成新生产路径已接通。本轮仅修改文档，未运行 Maven、JDT 或客户代码。

## 当前合同与实际消费者

Agent 或用户在获准的环境使用 Maven 官方 [`dependency:build-classpath`](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html)，取得每个被选编译模块的有序实际文件路径。交给 Java 的输入还需明确源码根、source/target 或 release、目标 JDK、模块间源码关系及与冻结来源的对应。Java 只核对这些**已提供**文件的来源、存在和可读性，并把逐模块环境交给现有 JDT LS/Core；它不解析 Maven 错误、不修复依赖、不下载 POM/JAR、不维护 Maven 仓库/凭据/传输/审批系统。Maven 原始退出码和输出由当前 Agent 向用户说明，必要权限与受信任环境由 Agent 和用户协商。[Java 环境与就绪](../modules/technical-analysis/java-readiness.md)及[技术运行合同](../modules/technical-analysis/cli-and-runtime.md)拥有准确字段、停止条件和保存版本。

已知路径缺失、无法读取、来源或 JDT 绑定冲突仍阻断 Java 导航。JDT LS 的全文件诊断完成信号未证实；覆盖未知允许继续，但须披露已观察范围及未证部分，不得把未收到错误解释为全仓无错。现有 Step03 导航和 Step04 MyBatis/XML/JSqlParser 路线继续复用。

本轮**没有授权在固定 jshERP 捕获中运行 Maven**。直接调用一个 Maven goal 不等于纯读 POM：Maven 会读取项目 `.mvn` 配置、settings，并可能加载 core/build extensions。即使不运行整个 `generate-sources` 生命周期，也不能承诺“客户代码绝不进入 Maven 进程”。这是将调用留给 Agent/用户可信环境选择的原因。[Maven 配置入口](https://maven.apache.org/configure) · [Core extension 研究](https://maven.apache.org/components/studies/extension-demo/) · [生命周期与直接 goal](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle)

## 撤回方案留下的技术事实

- Maven 的有效模型由 parent、BOM、profile、properties、dependencyManagement 等共同决定，传递依赖还受 scope、optional、exclusions 和版本调解决定；从 POM 字面或缓存中挑几个同名 JAR 不能证明编译 classpath 完整。[Model Builder](https://maven.apache.org/ref/3.8.3/maven-model-builder/) · [依赖机制](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- Resolver 自身没有 Maven 有效模型的全部装配语义。旧研究为此提出 ModelBuilder＋Resolver＋Settings Builder＋自有 `WorkspaceReader`，还需自行处理仓库策略、profile 文件激活、reactor 节点和下载边界；这些曾是**方案成本与风险**，现在不再是 Java 的实现清单。[Resolver 工作机制](https://maven.apache.org/resolver/how-resolver-works.html) · [WorkspaceReader API](https://maven.apache.org/resolver/maven-resolver-api/apidocs/org/eclipse/aether/repository/WorkspaceReader.html)
- 官方 classpath 输出也不自行证明未编译 reactor 兄弟模块的源码边、生成源码、实际目标 JDK API、插件改写的根或特殊 module-path。来源身份、模块关系与目标平台须在交接中明确；无法建立时报告未支持或缺项，不把所有模块的 JAR 合并成一个全局 project。[多模块 Reactor](https://maven.apache.org/guides/mini/guide-multiple-modules.html) · [Maven Toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains)
- JDT 工具 JVM 与目标 JDK 不同。旧实现只核对**已列 JAR**的文件/hash、单 project/classpath 和硬编码 source level；Core 曾用 `ASTParser.setEnvironment(..., true)` 引入工具 JVM bootclasspath。逐模块 JDT 实际绑定仍需核对，不从 `sourceLevel` 或 Maven classpath 一项推断整套编译环境。[ASTParser API](https://help.eclipse.org/latest/ntopic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)

## 未实施与验收限度

新交接尚未贯通三个技术命令、逐模块 JDT/目标 JDK、正式 Vue 关联或跨运行 Step04/05。应以提供的逐模块文件、明确元数据和现有 Step03 的具名 Mapper、logger.error、重载、嵌套调用、多态样本验证，记录已知缺项与诊断覆盖未知；不重做 Maven 解析、Step03 候选裁决或 SQL 引擎。此页没有新的客户运行结果，不能宣称旧错误边已被修复或全仓调用准确。
