# 技术分析：先复用成熟工具的有界决策

状态：2026-09-27 一手资料调研与设计取舍；**不是新实施、安装或运行授权**。本轮没有运行客户 Maven、JDT、npm、测试，也没有验证新产品接线。正式合同以[依赖准备](../modules/technical-analysis/dependency-preparation.md)、[Java 就绪](../modules/technical-analysis/java-readiness.md)、[前端 HTTP 发现](../modules/technical-analysis/frontend-http-discovery.md)及[清理计划](../plans/technical-analysis-cli-and-vue-cleanup-design.md)为准。

## 先问是否属于核心

**非核心工作交给外部成熟工具和当前 Agent/用户：** Maven 负责有效模型、依赖解析、仓库和下载；Vue/TypeScript 工具负责语法及其已有的符号服务；JDT 负责 Java 语义。不要在 Java 内重建 Maven 管理、通用 JS/Vue 解释器、JDK/编译器、SQL 引擎或 Maven 错误解释/审批系统。**核心只保留有限适配：** 读取显式的逐模块环境，将其绑定给现有 JDT；针对已有 fixture 的页面→事件/组件→请求封装→HTTP 方法/路径→Spring 入口做有界静态关联，未知与多候选照实输出。这是本项目的设计取舍，不声称成熟工具能直接产出完整业务关联。

| 问题 | 现成能力与证据 | 当前选择及边界 |
| --- | --- | --- |
| 依赖路径从哪里来？ | Maven 官方 [`dependency:build-classpath`](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html) 将已解析依赖的本地路径输出到文件或日志，可设置 scope、输出文件与分隔符；它要求 Maven 项目并进行依赖解析。 | 用户运行，或 Agent 在明确授权后运行**官方 Maven 目标**；逐模块交付实际导出文件。Java 不嵌入 Resolver、不下载、不遍历本地仓库猜 JAR，也不把单个 classpath 文件误作源码根/JDK/模块关系。 |
| 有效构建值如何核对？ | Maven 官方 [`help:effective-pom`](https://maven.apache.org/plugins/maven-help-plugin/effective-pom-mojo.html) 可显示计入活动 profile 的有效 POM；[`help:evaluate`](https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html) 可求值表达式并输出结果。 | 仅在需要澄清编译值时由外部 Maven/Agent 使用；Java 只消费已明确给出的必要 source、JDK、模块元数据，不再计算父 POM、profile 或插件规则。两目标不是自动执行清单。 |
| Maven 是否“只读无风险”？ | Maven 官方文档说明项目 `.mvn/extensions.xml` 可配置[核心扩展](https://maven.apache.org/configure)，扩展能[改变 Maven 运行时行为](https://maven.apache.org/guides/mini/guide-using-extensions.html)。`build-classpath` 本身也要解析依赖。 | 不能承诺零客户代码、零网络或零缓存写入；Agent 依据实际副本和权限与用户协商。原始退出状态/日志交 Agent 解释，Java 不做 Maven 错误分类、自动修复或审批服务。本轮未运行。 |
| 能否直接用 JDT LS/M2E 导入？ | [JDT LS 官方项目](https://github.com/eclipse-jdtls/eclipse.jdt.ls) 明确集成 M2Eclipse、支持 Maven `pom.xml` 项目及 Java 导航/诊断；[M2E 文档](https://eclipse.dev/m2e/documentation/m2e-documentation.html)明确能由 POM 管理 Eclipse build path、解析/下载依赖。 | 这是成熟替代候选，**不是**当前受控 `.classpath` 输入的无副作用读取器。当前沿用显式逐模块 classpath/source/project reference/目标 JDK → JDT 的路线；是否整体转为 M2E 导入需另做权限、副作用、保真比较，不同时维护两条路线。 |
| IDE 导入怎样处理 JDK 与副作用？ | [VS Code Java 项目文档](https://code.visualstudio.com/docs/java/java-project)说明自动导入 Maven 工程、`java.configuration.runtimes` 映射本地 JDK，Maven 工程的 JDK 还受 POM 配置影响；标准模式会解析导入依赖并由语言服务器构建工程。其[扩展配置](https://github.com/redhat-developer/vscode-java/blob/main/package.json)含默认启用的 Maven importer、默认非离线模式及 build-configuration 更新策略。[M2E FAQ](https://eclipse.dev/m2e/documentation/m2e-faq.html)说默认导入不执行所有 Maven goals，但配置/插件可影响项目更新。 | IDE 路线可借鉴多 JDK/模块映射，不可推断它符合本轮“固定来源、无需客户构建执行”的运行边界；也不可把工具 JVM 当目标 JDK。若未来选用，先验证真实副作用和产物。 |
| Vue 页面语法需要自研解析器吗？ | Vue 组织的 [`vue-eslint-parser`](https://github.com/vuejs/vue-eslint-parser)提供 `.vue` 模板/脚本 AST 与模板遍历接口；微软 [TypeScript Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)提供 Program、AST、TypeChecker 与 Symbol；Vue 官方 [`language-tools`](https://github.com/vuejs/language-tools)已有 SFC 解析、虚拟代码、语言服务及 TypeScript 插件。 | 首版复用已选 `vue-eslint-parser`，只识别既有 fixture 所需形态；不自研 Vue/JS 解释器。若遇真实 TS/类型级需求，再以同一 fixture 比较 Compiler API 或 Vue language-tools 的必要接口与运行成本，不预装第二套语义引擎。 |
| 有没有可直接给出页面→后端业务关联的成品？ | 上述一手资料描述的是解析、类型/符号和 IDE 服务，并未提供本项目所需的“页面事件经仓库特定请求封装匹配到 Spring 入口”的现成合同；**这是对所查接口的限定推论，不是全生态不存在此工具的断言**。[Vue parser](https://github.com/vuejs/vue-eslint-parser)、[Vue language-tools](https://github.com/vuejs/language-tools)、[TypeScript API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)。 | 保留最小、可验证的 import/事件/已知 wrapper/HTTP 路由连接；动态 URL、运行时状态、未知 wrapper、冲突入口留 Gap/候选，不扩成通用前端执行模型。 |
| Mapper/XML/SQL 是否另造引擎？ | MyBatis 官方有 [`XPathParser`](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/parsing/XPathParser.html)和递归处理 `<include>` 的 [`XMLIncludeTransformer`](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLIncludeTransformer.html)；[JSqlParser](https://github.com/JSQLParser/JSqlParser)将 SQL 解析为可遍历 Java AST。 | 沿用现有 Step04 MyBatis/XML/JSqlParser 及 Step03 导航；这里只做所需输入接线/材料消费，不复制 MyBatis SQL 模板、方言 SQL 解析或 Java 调用解析。 |

### 常用开源静态分析系统的补充核查

| 系统 | 已有能力与边界 | 对当前范围的判断 |
| --- | --- | --- |
| Joern | 官方列有 [Java、JavaScript 前端](https://docs.joern.io/frontends/)，其 [CPG 查询](https://docs.joern.io/cpgql/calls/)可读调用点、出入调用，[切片](https://docs.joern.io/cpg-slicing/)可做跨过程数据流；[Java 前端](https://docs.joern.io/frontends/java/)仍区分额外 JAR/JDK 类型信息。 | 可把一般调用/数据流查询交给成熟工具研究，不应另造通用 CPG；但所查文档未证实它开箱即完成 Vue SFC 事件→仓库封装→HTTP→Spring 的业务匹配，也未在本项目验证 Java/JS 同图精度与受控来源接线。 |
| Semgrep CE | 官方将 CE 定位为[单文件分析](https://semgrep.dev/docs/contributing/semgrep-philosophy)，可用[规则搜索及 taint](https://semgrep.dev/docs/writing-rules/glossary)识别局部语法/数据流；[跨文件及跨函数能力属于 Pro Engine](https://semgrep.dev/products/semgrep-vs-ce/)，不能算作 CE 的现成能力。 | 可用于局部已知 API/模式筛查；不能凭 CE 的局部命中替代本轮跨文件页面→封装→后端关联。这里说的是官方 CE 边界，不是断言整个 Semgrep 产品不支持。 |

本轮选择：一般调用、数据流和规则筛查已有成熟系统，不在本项目重写。当前只设计具名Vue请求模式到已有入口的有限适配，尚未用真实比较排除Joern等替代方案；不能因已有代码或fixture而认定自写连接更好。实施前若发现需要一般跨文件/跨过程求值，应暂停自写扩展，先拿同一例子比较成熟工具输出、执行边界和集成成本，再讨论选择。本轮不安装、不运行，也不把未见现成业务合同写成“不支持”。

## 仍须验证，不以调研代替验收

- 官方 Maven classpath 输出**不证明**同源模块 JAR 可取得、生成源码齐全、目标 JDK 正确或整个项目编译通过。逐模块实际路径、显式 source/JDK/module metadata、同源绑定与 JDT 传入仍待真实 fixture 验证；已知缺项阻断，诊断覆盖未知可继续但必须披露。[Maven 目标说明](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html)、[当前 owning 合同](../modules/technical-analysis/dependency-preparation.md)。
- M2E/VS Code Java 的导入能力已由产品文档确认，但本项目未验证它在受控副本中的实际文件写入、网络、扩展/goal 加载、JDK 和源码映射，也未比较与当前 `.classpath` 接线的导航结果。不能据此改选路线。[M2E 文档](https://eclipse.dev/m2e/documentation/m2e-documentation.html)、[VS Code Java 项目文档](https://code.visualstudio.com/docs/java/java-project)。
- Vue parser/语言工具提供语法/语义基础，不代表仓库别名、mixin、参数传播、Axios wrapper、运行时 URL 和 Spring 匹配已成功。已知 fixture 范围、失败样例与未来版本锁定以[前端研究](frontend-http-parser-research.md)和[owning 合同](../modules/technical-analysis/frontend-http-discovery.md)为准。
- 本文没有做产品的穷尽式支持矩阵，也没有运行或安装任何候选工具。新增能力之前应先用具名真实样例证明需要，再复查一手资料和最小可复用接口。
