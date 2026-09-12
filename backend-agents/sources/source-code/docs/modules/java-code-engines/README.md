# 可切换的 Java 代码引擎：完整设计

> 目标设计，尚未实施。2026-09-12 按用户确认的方向编写。首先独立打通 JDT；随后适配保留的 JavaParser，只恢复它已有能力。本次没有修改 Java、测试、Schema 或运行产物。

## 1. 先用一句话讲清楚

系统给工具一个 Controller 入口；工具找到沿途仓库内的方法实现；程序把这些方法的完整代码、调用位置、实参与形参放在一起；模型据此解释业务。

例如注册入口调用 `userService.registerUser(...)`。只给模型这行调用，它不知道注册具体做什么。新的 JDT 路线要自动带回 Service 正文，包括限制检查、默认属性、保存调用和租户/角色处理。模型因此有材料解释业务，而不是把方法名翻译成中文。

**统一的是交付给下游的材料格式，不是两个引擎的解析能力。** JDT 找到三个实现候选，材料就保留三个；JavaParser 只能找到声明，材料就如实保留声明和未展开原因。不能为了格式一致，把两边都降成空摘要，也不能让弱引擎伪造强引擎的结果。

## 2. 阅读顺序

| 想回答的问题 | 文档 |
| --- | --- |
| 系统如何拼在一起，为什么可行 | 本文 |
| YAML、Interface、字段、数量和失败怎么定 | [统一输入输出与配置](contracts-and-configuration.md) |
| JDT 各子模块具体怎样找代码、取正文、处理调用 | [JDT 引擎详细设计](jdt-engine.md) |
| 怎样接入现有步骤，怎样保留并迁回 JavaParser，如何测试 | [接入与第二阶段适配](integration-and-javaparser.md) |
| 一个真实例子从请求到完整材料，再到业务解释 | [注册与财务贯穿例子](../../examples/java-code-engine-walkthrough.md) |

这组文档是代码引擎的唯一详细合同；[八步总体设计](../../DESIGN.md)和各步骤文档引用它，不另复制一套字段定义。Interface 和 JSON 片段是待实现合同，不能称为当前生产输出。

## 3. 明确做什么、不做什么

做：固定源码索引、合法入口发现、定位调用声明及实现候选、完整方法/构造器正文、调用点实参、目标形参、语法条件和返回、外部边界、可查来源、统一材料保存和消费。

不做：运行客户构建/应用、证明运行时究竟选中了哪个实现、执行数据库、展开框架驱动内部、制造业务顺序、重写 Java 类型系统、重新建设五图/Proof/恢复系统、热加载插件平台。

JavaParser 保留的是现有可用生产能力及其通用测试，不复活已退役 R0/有限键业务解释链。已有精确证据、五图、Facts 和严格 Flow 不删除，也不改变其“已证明”含义。

## 4. 系统只增加一个可替换位置

```text
固定源码 + 工程画像
        ↓
运行时按 YAML 选择一个 JavaCodeEngine
        ↓
JDT：LS 导航 + Core 语法读取
或者 JavaParser：保留算法的 Adapter（第二阶段）
        ↓
统一代码索引 + 入口代码上下文
        ↓
既有 Step05 保存 / Capsule 投影
        ↓
BusinessMaterialBuilder → ActivityExplainer
        ↓
ProcessExplainer → BusinessReportPublisher → 一份九章
```

这里只画可替换位置，不新增一条产品业务流水线。引擎不输出中文业务判断，不直接调用模型，不渲染 Markdown。`RepositoryAnalysisAgent` 的 `start / executeStep / inspect / artifact / render / validate / trace` 不变。

一次运行只选择一种 Java 引擎。JDT 报错不自动切换 JavaParser；用户改配置后可创建新运行。不同引擎产生的中间结果不混成一个未经说明的调用链。

### 4.1 子模块与唯一职责

| 子模块 | 输入 | 拥有的工作 | 输出 / 下一消费者 |
| --- | --- | --- | --- |
| EngineConfigurationLoader | 本地工具 YAML | 严格解析、选择引擎、启动参数预检 | EffectiveEngineConfiguration → factory |
| JavaCodeEngineFactory | 配置、冻结来源能力 | 只创建选定 Adapter，不加载未选工具 | JavaCodeSession → discovery/Step03 |
| JdtProjectSession | 源码清单、源码根、语言级别、已批准本地依赖 | 建隔离只读源码投影、启动 LS、复用一个索引 | 私有 LS 会话与投影映射 → JDT 内部 |
| JdtSyntaxReader | 同一冻结 Java 文件 | 用 JDT Core 枚举声明、形参、调用语法、条件、返回与原文范围 | 工具中立的语法记录 → catalog/navigator |
| JdtNavigationResolver | 具体调用位置、LS 会话 | call hierarchy / definition / implementation，保留所有返回候选 | NavigationResult → collector |
| EntryCodeCollector | 一个入口、语法记录、导航结果 | 沿仓库实现展开、去重、保留循环及每个调用的处置 | EntryCodeContext → Step03 publication/Step05 |
| JavaParserCodeEngine | 同样的冻结来源与入口 | 第二阶段封装现有解析/图算法，映射已有信息 | 同一种 EntryCodeContext，缺项如实记录 |
| EntryContextAssembler | 引擎上下文、可选现有技术增强 | 归属到入口、附来源与既有技术引用，不再次找实现 | Step05 上下文及 Capsule → Builder |
| BusinessMaterialBuilder（已有） | 保存的上下文 | 完整单元组包、短引用、模型可见投影 | BusinessMaterialSet → 既有业务模型链 |

这些是代码内部职责，不意味着九个独立部署服务、九层 receipt 或九个公共 Interface。配置与 factory 可以很小；只有两种工具实现真正可替换。JDT 内部 LS/Core 类型不得泄漏到公共 records。

## 5. 如何融入原八步，不再绕回证据门禁

| 原步骤 | 插件化后的目标职责 | 必须保留的限制 |
| --- | --- | --- |
| 01 固定源码 | 继续使用现有 inventory、读取能力和身份 | 不改算法，不读客户工作区改动 |
| 02 发现应用 | 配置/POM/XML继续共用；Java 声明及注解来自选定引擎的 catalog | 选择 JDT 时不能偷偷调用旧 JavaParser 发现器 |
| 03 代码关系 | 用选定引擎建立可复用 Java 导航索引，保存完整方法、调用、候选及局限 | JDT 返回位置不是动态执行轨迹，也不是完整数据流 Proof |
| 04 严格事实 | 对已有且实际提供的技术图继续计算严格 Fact；无对应增强时明确未生成 | 不为 JDT 补假图/假 Fact，不阻止有来源的方法正文向下传 |
| 05 入口材料 | 组织选定引擎已经找到的代码关系，保存完整上下文；附可选严格 Flow/Fact | 不再依赖 strict Flow 数量决定可读入口数量 |
| 06 局部解释 | Builder 只消费统一材料，ActivityExplainer 保留完整 DRAFT/REVIEW | 不解析 Java，不按引擎选另一套业务提示词 |
| 07 过程与知识 | 现有 ProcessExplainer 读取完整活动和必要代码材料 | 不把同名、共享表或同一个 Java 调用当作业务先后证明 |
| 08 九章 | 现有报告模块组织一份仓库级报告及来源 | 不把两个真实导航例子当成整仓业务验收 |

**这里有一项有意的协议变化：03/04 的完整五图及严格 Fact 不再是每种引擎都必须先产生的业务取材前置。** JavaParser 的现有五图能力保留为技术增强；JDT 首先交付导航与源码材料，不为了兼容旧 publisher 先重做五张图。

因此不能将现有 `ProgramGraphsExecution → ProvenCodeFactsExecutor` 硬接线原样留在 JDT 主路径：否则“选择 JDT”仍会暗中运行 JavaParser。目标仍保留八步 key，但步骤回执必须区分实际产生的导航材料和未生成的技术增强。没有执行的 graph/Fact 模块记录 `NOT_PRODUCED` 及原因，不冒充空图成功；这是产物可用性，不新增运行生命周期。具体接线和旧消费者处理见[接入设计](integration-and-javaparser.md)。

### 5.1 已冻结的步骤接线

- Step02 的每个入口必须同时保存 `methodKey` 和完整声明的 `SourceRange`；handler 名/FQN 只供展示，不能作为重载选择键。
- `java-code-index` 固定为现有 `PROGRAM_GRAPHS` 的 module 7，地址为 `(PROGRAM_GRAPHS, 7, "java-code-index")`。它不是新 step，也不改变八步编号。
- JDT 第一阶段 Step03 的实际语义产物只有 `java-code-index.jsonl`，再由现有步骤发布器写 `program-graphs-receipt.json`；未执行的五图不写空文件。
- JDT 第一阶段 Step04 的实际语义产物只有 `fact-accounting.json`，再写 `proven-code-facts-receipt.json`。accounting 为 v4、`availability=NOT_PRODUCED`、`reason` 非空且所有数量为 `null`，不包含伪造的 Candidate/Fact/Proof 引用。
- Step05 以保存的 `EntryCodeContext` 为第一输入；严格 Flow/Fact 只是可选增强。实际语义产物仍是既有五件套及 receipt，版本以[合同版本表](contracts-and-configuration.md#5-保存格式位置与复用)为准。

这些差异必须进入 receipt 的实际 artifact descriptors，并同步现有 exact-set allowlist、artifact policy、直接 reader 与公共 fixture；不把固定旧文件数当成步骤成功条件。

## 6. 为什么使用 JDT LS 加 JDT Core

JDT LS 已提供定位声明、实现和调用层次；JDT Core 能读取方法、构造器、参数和语法节点。两者分工如下：

- **LS 拥有唯一的项目语义索引与目标定位。** 不自己拼包名、实现继承规则或匹配重载。
- **Core 做精确语法读取，并在受控环境内解析注解身份。** 使用同一源码文本，找完整声明/方法体、实参、形参、if/try/return 等位置；它只消费已验证源码根与显式批准classpath来确定注解限定名，不建立第二张调用图。
- LS 返回整方法 range 时直接按范围查证并取源码；返回方法名位置时，Core 找包含该位置的精确声明。标准 LSP 并没有一个可直接远程调用 `IMethod.getSource()` 的请求，不能把 Java API 当成现成 LSP 接口。

选择独立的 Core 语法辅助进程，使用已固定工具 JDK；主应用维持 Java 17。不编写 JDT LS 插件、OSGi 扩展或另一套工程导入器。LS 与 Core 各有必要解析：**LS负责调用/定义/实现导航，Core负责语法输出和注解限定名binding**；后者不扩展成通用调用解析器，也不是每个步骤再解析一次。

依据：[JDT LS 官方功能](https://github.com/eclipse-jdtls/eclipse.jdt.ls)、[ASTParser](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)、[MethodDeclaration](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodDeclaration.html)。具体启动与缓存见[JDT详细设计](jdt-engine.md)。

## 7. 先 JDT、后 JavaParser：不能颠倒

### 第一阶段：只把 JDT 正式链走通

按业务目标冻结最小合理 Interface 和材料字段，然后实现 JDT 从入口发现到完整代码上下文，再交给已有四个业务模块。允许修改当前 compiler、Capsule、读取器和 material 协议；**不因 JavaParser 暂时不能满足而削弱合同，不同时修两套引擎。**

旧 JavaParser 源码和有效测试保留。过渡版本若尚未接入 factory，选择 `javaparser` 返回明确 `ENGINE_NOT_INTEGRATED`，不能偷偷调用不符合新合同的旧流水线。第一阶段不以 JavaParser Adapter、双引擎一致性或旧专属测试全部迁移完成作为退出条件。

第一阶段验收：JDT 独立发现入口、注册/财务及跨领域源码自动展开、构造器/多实现/循环和缺失可见、统一材料持久化后正式 Builder可读、scripted 业务全链正常；JDT 路径对 JavaParser 零调用。真实 Luna 验收另外按当次授权，不隐式执行。

### 第二阶段：恢复 JavaParser 既有能力

依据第一阶段已经走通的合同，只做 Adapter、字段映射、入口接线、保存读取和必要回归。现有图、Fact和 Flow 的能力及未解析边界保持；不强迫它追平 JDT，不在此阶段补全 wildcard/import/继承/重载解析或启用一套新的 Symbol Solver 配置。

第二阶段验收：YAML可切换两种引擎；同一个 Builder、Activity/Process/Report 消费二者输出；JavaParser 现有能识别的内容不退化，不能识别的内容诚实保留。不比较两边方法数、解析率或 JSON SHA 是否相同。

## 8. 质量目标与可行性推演

可信性不是靠“校验层更多”，而是以下五个直接条件：

1. 每段源码来自冻结文件且范围正确，不能是模型或程序拼出的假方法。
2. 每个可见调用点都能在材料中找到目标、候选或停止原因；没有结果不能被过滤掉。
3. 完整方法的条件、构造、保存调用和返回没有在组包时丢掉。
4. 模型确实收到这些内容，REVIEW也收到完整草稿；不是只把内容留在技术文件里。
5. 已审活动、关系、条件和未知项继续进入仓库知识与九章。

已有试验说明 JDT 可以补回注册 Service；它没有证明完整候选处理、构造器和生产接线已完成。设计必须补齐这些接线职责，但无需发明类型解析算法。完整方法提供业务动作和条件；实参/形参及调用位置说明方法如何联系；模型负责解释这些联系的业务意义。这条接力可验证，也能换仓库复用。

不能保证的内容包括缺依赖的绑定、动态代理选择、反射目标、配置实际值和外部执行结果。保留真实调用和限制后，模型仍可解释已看见的行为；不能把这些边界转化为“整个入口不准阅读”。

## 9. 当前实现审计

| 项目 | 实际状态 | 本设计要补的内容 |
| --- | --- | --- |
| JDT调研 | 两入口已获得真实导航与源码；研究程序仍用 JavaParser切正文/枚举语法 | JDT Core替换这部分；不是重做 LS类型系统 |
| 注册结果 | 76 个方法记录，67 个有正文、9 个仅声明；228 个语法调用 | 不能把76说成76个已展开实现；每个未展开点保留 |
| 原始结果处理 | 部分 hierarchy 成功路径提前返回；可能没询问 implementation；构造位置未完整处理 | 统一归一化、实现候选查询、构造器支持 |
| JavaParser现状 | 现有 CallGraphBuilder 对 import/type 采用有限规则；POM有 Symbol Solver 不等于生产已接线 | 第二阶段保持真实能力；不宣称 JavaParser库做不到 |
| Builder | 消费已有上下文，但仍有 JavaParser语法读取与观察提取 | parser相关工作移入对应引擎，公共Builder禁止重新解析 |
| 正式编排 | YAML engine factory、JDT发现、module 7导航索引发布和Step04 `NOT_PRODUCED` accounting已经接通；Step05仍只读取旧图/Fact/Flow | 下一交付使Step05从持久化导航索引组装入口上下文，保留公共Agent |
| JDT导航索引 | `java-code-index-v1`已保存ENGINE/TYPE/METHOD/CALL/ENTRY_MEMBERSHIP/DIAGNOSTIC，读取器可从磁盘重建完整入口上下文 | Step05直接消费该读取器，不得重新启动JDT或JavaParser |
| 业务模块 | Activity/Process/Report已经存在 | 接收内容变丰富；不另建业务路线 |

调研细节和准确限制见[贯穿例子](../../examples/java-code-engine-walkthrough.md)。本设计不改写历史运行结果，不把目标 JSON 当作已生成产物。

## 10. 开发原则

设计及偏差裁决由 Sol/ultra 或 Astra/ultra；Luna/xhigh 写直接行为测试；Terra/xhigh 实现；Sol/xhigh 定位实际故障。每个 Agent 先维护自己 progress。不要让 Terra/Luna重新设计接口、按案例增加行业规则，或者以“JDT返回包装不符”为由丢掉有效结果。

局部协议变化先同步本组详细设计，可由设计者裁决；影响业务目标、运行客户代码、外部调用权限或共享合同需交用户。只跑直接覆盖的验证，重型命令串行。当前设计工作产品模型调用为 **none**，不自动启动实现、运行调研、提交或合入 main。
