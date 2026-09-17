# Java代码引擎：当前实现记录与新目标入口

2026-09-17用户已决定新生产只保留JDT，移除JavaParser和严格Fact/Proof，增加可选持久化材料并重划第3—5步。目标唯一见[补充详细设计](../../supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md)。**下文两引擎合同记录2026-09-12实现与旧格式，不再是未来必须保留JavaParser的要求。** JDT LS/Core完整导航、语法与缓存细节仍复用；历史reader保留。新迁移尚未实施，本轮验收到第5步结束。

> JDT 第一阶段与 JavaParser 第二阶段均已于 2026-09-12 完成正式接入与验收。JavaParser 只恢复迁移前已有能力，不追平 JDT。本页同时记录稳定合同、已验证事实和剩余边界。

## 1. 先用一句话讲清楚

模型执行的失败不属于引擎重扫条件。已实现的[固定材料＋独立模型批次](../model-job-execution.md#7-固定材料与独立模型批次已实现)在 M10 之后复用材料，JDT/JavaParser 均不打开会话；此处已有会话内 query 缓存、方法共享和引用式持久化。当前保存的 JDT/源码语料已经支撑 326 个 Activity 完成 DRAFT＋REVIEW。新的业务过程发现只重用这些结果，不增加代码引擎能力，也不重新扫描源码。

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
| 共享方法如何只查一次、保存去重、CI与正文怎样减负 | [已批准优化设计](../../plans/navigation-reuse-and-readable-report-design.md) |

这组文档是代码引擎的唯一详细合同；[八步总体设计](../../DESIGN.md)和各步骤文档引用它，不另复制一套字段定义。统一 Interface 与 JSON 已成为当前生产合同；JavaParser 的第二阶段段落同时保留为已完成迁移边界。

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
全仓业务目录发现 → 候选过程详细重建 → 仓库过程归并
        ↓
确定性 business-processes.md → 下游一份九章概览
```

这里只画可替换位置，不新增一条产品业务流水线。引擎不输出中文业务判断，不直接调用模型，不渲染 Markdown。`RepositoryAnalysisAgent` 的 `start / executeStep / inspect / artifact / render / validate / trace` 不变。

一次运行只选择一种 Java 引擎。JDT 报错不自动切换 JavaParser；用户改配置后可创建新运行。不同引擎产生的中间结果不混成一个未经说明的调用链。

### 4.1 子模块与唯一职责

| 子模块 | 输入 | 拥有的工作 | 输出 / 下一消费者 |
| --- | --- | --- | --- |
| EngineConfigurationLoader | 本地工具 YAML | 严格解析、选择引擎、启动参数预检 | EffectiveEngineConfiguration → factory |
| JavaCodeEngineFactory | 配置、冻结来源能力 | 只创建选定 Adapter，不加载未选工具 | JavaCodeSession → discovery/Step03 |
| JdtProjectSession | 源码清单、源码根、语言级别、已批准本地依赖 | 建受控源码投影、启动 LS；拥有语法/导航缓存与共享方法表 | 私有会话、投影及复用基础 → JDT 内部 |
| JdtSyntaxReader | 同一冻结 Java 文件 | 用 JDT Core 枚举声明、形参、调用语法、条件、返回与原文范围 | 工具中立的语法记录 → catalog/navigator |
| JdtNavigationResolver | 具体调用位置、LS 会话 | 相同操作/位置只查询一次；保留原始返回、全部候选与失败 | NavigationResult → collector |
| EntryCodeCollector | 一个入口、共享语法/导航结果 | 计算入口成员与展开状态，复用方法正文，保留循环和调用发生点 | EntryCodeContext → Step03 publication/Step05 |
| JavaParserCodeEngine | 同样的冻结来源与入口 | 第二阶段封装现有解析/图算法，映射已有信息 | 同一种 EntryCodeContext，缺项如实记录 |
| EntryContextAssembler | 引擎上下文、可选现有技术增强 | 归属到入口、持久化索引/context引用；读取时恢复完整视图，不复制方法正文 | Step05 上下文及 Capsule → Builder |
| BusinessMaterialBuilder（已有） | 保存的上下文 | 完整单元组包、短引用、模型可见投影 | BusinessMaterialSet → ActivityExplainer |

这些是代码内部职责，不意味着九个独立部署服务、九层 receipt 或九个公共 Interface。配置与 factory 可以很小；只有两种工具实现真正可替换。JDT 内部 LS/Core 类型不得泄漏到公共 records。

## 5. 如何融入原八步，不再绕回证据门禁

| 原步骤 | 插件化后的目标职责 | 必须保留的限制 |
| --- | --- | --- |
| 01 固定源码 | 继续使用现有 inventory、读取能力和身份 | 不改算法，不读客户工作区改动 |
| 02 发现应用 | 配置/POM/XML继续共用；Java 声明及注解来自选定引擎的 catalog | 选择 JDT 时不能偷偷调用旧 JavaParser 发现器 |
| 03 代码关系 | 用选定引擎建立可复用 Java 导航索引，保存完整方法、调用、候选及局限 | JDT 返回位置不是动态执行轨迹，也不是完整数据流 Proof |
| 04 严格事实 | 对已有且实际提供的技术图继续计算严格 Fact；无对应增强时明确未生成 | 不为 JDT 补假图/假 Fact，不阻止有来源的方法正文向下传 |
| 05 入口材料 | 组织选定引擎已经找到的代码关系，保存完整上下文；附可选严格 Flow/Fact | 不再依赖 strict Flow 数量决定可读入口数量 |
| 06 局部解释 | Builder 只消费统一材料，ActivityExplainer 保留完整 DRAFT/REVIEW；当前 326 个已审 Activity 可直接重用 | 不解析 Java，不按引擎选另一套业务提示词 |
| 07 过程与知识 | 目标 `BusinessProcessDiscovery` 从全仓 Activity 卡片发现候选，再按 ID 取回完整 Activity 和所选源码重建多阶段过程 | 不把同名、共享表或同一个 Java 调用当作业务先后证明；不重新打开引擎会话 |
| 08 九章 | 报告只消费已归并 `RepositoryBusinessProcessCatalog`，组织一份仓库级概览及来源 | 不从原始 Activity 二次发现流程，不把两个真实导航例子当成整仓业务验收 |

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

### 第一阶段：JDT 正式链（已完成）

实施时先按业务目标冻结最小合理 Interface 和材料字段，再独立完成 JDT 从入口发现到完整代码上下文，并交给材料和 Activity 模块。该阶段没有因 JavaParser 当时尚未适配而削弱合同，也没有同时修改两套引擎。

旧 JavaParser 源码和有效测试在过渡期保留；当时未接入 factory 的版本选择 `javaparser` 会明确返回 `ENGINE_NOT_INTEGRATED`。这是历史实施顺序，不是当前仍存在的运行限制。

第一阶段验收：JDT 独立发现入口、注册/财务及跨领域源码自动展开、构造器/多实现/循环和缺失可见、统一材料持久化后正式 Builder可读、scripted 业务全链正常；JDT 路径对 JavaParser 零调用。真实 Luna 验收另外按当次授权，不隐式执行。

### 第二阶段：恢复 JavaParser 既有能力（已完成）

第二阶段依据已经走通的合同，只完成 Adapter、字段映射、入口接线、保存读取和必要回归。现有图、Fact 和 Flow 能力及未解析边界保持；没有强迫它追平 JDT，也没有补写 wildcard/import/继承/重载解析或新建 Symbol Solver 工程接线。

第二阶段验收已经证明 YAML 可切换两种引擎，同一个 Builder 和 Activity 模块消费二者输出；JavaParser 原有可识别内容不退化，不能识别的内容诚实保留。后续过程发现和报告只依赖同一种已审 Activity/SourceRef 合同，不比较两边方法数、解析率或 JSON SHA 是否相同。

## 8. 质量目标与可行性推演

可信性不是靠“校验层更多”，而是以下五个直接条件：

1. 每段源码来自冻结文件且范围正确，不能是模型或程序拼出的假方法。
2. 每个可见调用点都能在材料中找到目标、候选或停止原因；没有结果不能被过滤掉。
3. 完整方法的条件、构造、保存调用和返回没有在组包时丢掉。
4. 模型确实收到这些内容，REVIEW也收到完整草稿；不是只把内容留在技术文件里。
5. 已审活动的完整条件和未知项能被过程发现按需取回，进入多阶段过程、`business-processes.md` 和九章；索引卡不能替代完整 Activity。

当前验收说明 JDT 可以补回注册 Service，并已覆盖完整候选保留、构造器、循环、边界及生产接线。完整方法提供业务动作和条件；实参/形参及调用位置说明方法如何联系；模型负责解释这些联系的业务意义。这条取材接力已经在固定 jshERP 两入口、整仓材料和自包含跨领域运行中验证；它不等于业务过程发现已经达到完整生命周期质量，旧单阶段 `ProcessExplainer` 也已退役。

不能保证的内容包括缺依赖的绑定、动态代理选择、反射目标、配置实际值和外部执行结果。保留真实调用和限制后，模型仍可解释已看见的行为；不能把这些边界转化为“整个入口不准阅读”。

## 9. 当前实现审计

| 项目 | 实际状态 | 剩余边界 |
| --- | --- | --- |
| JDT Core | 独立 helper 已读取完整声明、正文、参数、调用、control/exits；生产 JDT 包不引用 JavaParser | 缺依赖时 recovered binding 只作未解析线索，不能冒充确定限定名 |
| JDT导航 | hierarchy、definition、implementation 的候选归一、构造器、循环、重复及边界已有直接测试 | 动态代理实际选择、反射目标和没有源码的外部实现仍未知 |
| 真实结果 | 固定 jshERP 注册入口自动取得三段关键 Service 正文；财务入口取得 Service 与 Mapper 声明；完整材料检查点已保存并支撑 326 个 Activity | 动态代理、反射、缺依赖和外部效果仍不能因整仓运行而视为已解析 |
| JavaParser现状 | Adapter 已封装迁移前的有限名称解析、方法正文、调用/参数、七图、Fact/Proof 和 Flow；POM有 Symbol Solver 不等于生产已接线 | wildcard/import、继承和重载增强不在本阶段；不宣称 JavaParser 库做不到 |
| Builder | 只消费已保存 EntryCodeContext，输出声明类型、完整方法、调用、参数、控制和限制 | 两个引擎共用同一无解析器 Builder，Builder 不按 engine 分支 |
| 正式编排 | YAML、factory、同会话发现、索引、Step05、材料及 scripted 九章已接通；JDT 写 NOT_PRODUCED strict facts，JavaParser 写实际七图和 strict facts | 产品 Luna 与整仓业务质量另验；两个引擎不自动回退或混合 |
| JDT导航索引 | v2 的 METHOD 全仓共享、CALL 按入口保存；会话 query 缓存和原始响应复用已接入，保存材料可跨模型批次重用 | 动态分派与无源码外部调用仍按实际边界记录 |
| 引用与CI优化 | Step05/Capsule 引用保存、正文来源外置和 unit/real-JDT/quality 分类已交付 | 不新增缓存服务，不修改 JavaParser 解析算法 |
| Activity层 | 保存的 JDT 材料已经完成 326/326 Activity DRAFT＋REVIEW | 新过程发现直接复用，不全量重跑 Activity |
| 当前过程/报告 | 当前保存 340 个 Process，均为单 Activity、单 Stage；只覆盖 325 个不同 Activity，但 unmatched 为空；九章结构可渲染 | 这是 Step07 覆盖和业务串联缺口，不是引擎缺口；目标改为全仓目录、详细重建、归并和确定性 `business-processes.md` |

调研细节和准确限制见[贯穿例子](../../examples/java-code-engine-walkthrough.md)。历史调研产物仍保留；当前状态以正式 JDT 验收为准。

## 10. 开发原则

设计及偏差裁决由 Sol/ultra 或 Astra/ultra；Luna/xhigh 写直接行为测试；Terra/xhigh 实现；Sol/xhigh 定位实际故障。每个 Agent 先维护自己 progress。不要让 Terra/Luna重新设计接口、按案例增加行业规则，或者以“JDT返回包装不符”为由丢掉有效结果。

局部协议变化先同步本组详细设计，可由设计者裁决；影响业务目标、运行客户代码、外部调用权限或共享合同需交用户。只跑直接覆盖的验证，重型命令串行。当前设计工作产品模型调用为 **none**，不自动启动实现、运行调研、提交或合入 main。
