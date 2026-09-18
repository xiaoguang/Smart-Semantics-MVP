# Java代码引擎：当前实现记录与新目标入口

2026-09-18新生产已固定为JDT-only Step01--05：可选MyBatis/JSqlParser持久化补全和阅读材料取代严格图/Fact/Flow/Capsule/M10 producer。唯一现行合同见[补充详细设计](../../supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md)及[实施计划](../../plans/jdt-persistence-reading-materials-implementation-plan.md)。**下文两引擎、图/Fact和旧Step05段落只记录历史格式与重开边界，不能作为新运行的实现或配置指南。** JDT LS/Core导航、语法和缓存细节继续复用；JavaParser历史配置只读解码，不能打开引擎。

> JDT第一阶段与JavaParser第二阶段于2026-09-12的接入记录仍可用于读取当时产物；2026-09-18后JavaParser Adapter不再存在于生产启动路径。实施计划0--6/A--B已完成，包含旧producer退出新安装及469项clean CI；C的固定仓库验收已完成，325包/326条覆盖且1个导航失败明确保留；实测记录见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)。

## 1. 先用一句话讲清楚

模型执行的失败不属于引擎重扫条件。历史M10/Activity和模型批次仍可按既有读合同重开；新取材在第5步保存 `CodeReadingMaterialSet` 后停止，不启动模型。当前保存的JDT/源码语料和326条Activity可读，但不要求新运行产生或修改它们。

系统给工具一个 Controller 入口；工具找到沿途仓库内的方法实现；程序把这些方法的完整代码、调用位置、实参与形参放在一起；模型据此解释业务。

例如注册入口调用 `userService.registerUser(...)`。只给模型这行调用，它不知道注册具体做什么。新的 JDT 路线要自动带回 Service 正文，包括限制检查、默认属性、保存调用和租户/角色处理。模型因此有材料解释业务，而不是把方法名翻译成中文。

**当前统一的是JDT交付给下游的材料格式。** JDT找到多个实现候选时材料保留全部候选和停止原因；历史JavaParser结果只按原格式重开，不能通过新材料路径伪造、降级或混入。

## 2. 阅读顺序

| 想回答的问题 | 文档 |
| --- | --- |
| 系统如何拼在一起，为什么可行 | 本文 |
| YAML、Interface、字段、数量和失败怎么定 | [统一输入输出与配置](contracts-and-configuration.md) |
| JDT 各子模块具体怎样找代码、取正文、处理调用 | [JDT 引擎详细设计](jdt-engine.md) |
| 两引擎与旧严格图链如何历史读取 | [历史接入与第二阶段适配](integration-and-javaparser.md) |
| 一个真实例子从请求到完整材料，再到业务解释 | [注册与财务贯穿例子](../../examples/java-code-engine-walkthrough.md) |
| 共享方法如何只查一次、保存去重、CI与正文怎样减负 | [已批准优化设计](../../plans/navigation-reuse-and-readable-report-design.md) |

JDT内部导航细节仍以本组文档为准；当前生产的步骤、配置、持久化与材料合同以[补充详细设计](../../supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md)为准。JavaParser第二阶段段落仅保留为历史迁移边界。

## 3. 明确做什么、不做什么

做：固定源码索引、合法入口发现、定位调用声明及实现候选、完整方法/构造器正文、调用点实参、目标形参、语法条件和返回、外部边界、可查来源、统一材料保存和消费。

不做：运行客户构建/应用、证明运行时究竟选中了哪个实现、执行数据库、展开框架驱动内部、制造业务顺序、重写 Java 类型系统、重新建设五图/Proof/恢复系统、热加载插件平台。

JavaParser 的旧生产能力、五图、Facts 和严格 Flow 已退出新安装与新运行；历史 receipt、配置和材料仍按原义读取，不能借历史读取复活旧 producer 或将其“已证明”含义改写。

## 4. 系统只增加一个可替换位置

> **迁移前架构图。** 下图记录两引擎、严格图链和 M10/Activity 的历史接线，便于解释既有 payload；它不描述 2026-09-18 后的新生产路径。新运行固定为 JDT、Step01--05、可选持久化补全和 `CodeReadingMaterialSet`，然后停止。

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

迁移前一次运行只选择一种 Java 引擎。JDT 报错不自动切换 JavaParser；历史结果的不同引擎中间物也不能混成一个未经说明的调用链。新启动不再接受 JavaParser。

### 4.1 子模块与唯一职责

| 子模块 | 输入 | 拥有的工作 | 输出 / 下一消费者 |
| --- | --- | --- | --- |
| EngineConfigurationLoader | 本地工具 YAML | 严格解析、选择引擎、启动参数预检 | EffectiveEngineConfiguration → factory |
| JavaCodeEngineFactory | 配置、冻结来源能力 | 只创建选定 Adapter，不加载未选工具 | JavaCodeSession → discovery/Step03 |
| JdtProjectSession | 源码清单、源码根、语言级别、已批准本地依赖 | 建受控源码投影、启动 LS；拥有语法/导航缓存与共享方法表 | 私有会话、投影及复用基础 → JDT 内部 |
| JdtSyntaxReader | 同一冻结 Java 文件 | 用 JDT Core 枚举声明、形参、调用语法、条件、返回与原文范围 | 工具中立的语法记录 → catalog/navigator |
| JdtNavigationResolver | 具体调用位置、LS 会话 | 相同操作/位置只查询一次；保留原始返回、全部候选与失败 | NavigationResult → collector |
| EntryCodeCollector | 一个入口、共享语法/导航结果 | 计算入口成员与展开状态，复用方法正文，保留循环和调用发生点 | EntryCodeContext → Step03 publication/Step05 |
| JavaParserCodeEngine（历史） | 同样的冻结来源与入口 | 第二阶段封装过的解析/图算法 | 仅既有历史结果的读取边界 |
| EntryContextAssembler（历史） | 引擎上下文、可选既有技术增强 | 迁移前的 Step05 context/Capsule 组装 | 仅既有历史 payload 的读取边界 |
| BusinessMaterialBuilder（历史） | 保存的上下文 | 迁移前完整单元组包与模型投影 | 历史 M10/Activity 合同 |

这些是代码内部职责，不意味着九个独立部署服务、九层 receipt 或九个公共 Interface。配置与 factory 可以很小；只有两种工具实现真正可替换。JDT 内部 LS/Core 类型不得泄漏到公共 records。

## 5. 当前 Step01--05 链与历史八步槽位

| 步骤 | 当前生产职责 | 必须保留的限制 |
| --- | --- | --- |
| 01 固定源码 | 使用既有 inventory、读取能力和身份 | 不改算法，不读客户工作区改动 |
| 02 发现应用 | JDT catalog 识别 Spring 入口；安全 XML 仅提供可选 Mapper目录线索 | 没有 MyBatis POM/Mapper 不损坏 Java 入口分母；不调用旧解析器 |
| 03 代码关系 | JDT 建立可复用 `java-code-index`，保存完整方法、调用、候选及局限 | 返回位置不是动态执行轨迹，也不是完整数据流 Proof |
| 04 持久化材料 | 可选 MyBatis/JSqlParser 补全，发布 `persistence-material-index` | 关闭插件时为明确 `DISABLED`；不生成 Fact/Proof 或执行 XML/SQL |
| 05 入口阅读材料 | `CodeReadingMaterialSet` 一次组织、保存、重开及按需 JSONL/Markdown 导出 | 不重新导航/解析、不启动模型或 M10 |
| 06--08 | 历史 Activity/过程/报告可按原合同读取 | 新运行不启动这些步骤，也不把第5步材料自动送入模型 |

03的 `java-code-index` 继续使用既有 `PROGRAM_GRAPHS` module 7 存储槽位；04/05分别在既有 step 槽位使用新的持久化材料和阅读材料模块、schema与receipt。严格五图、Fact、Flow、Capsule和M10 producer已退出新安装；其历史 wire、receipt和typed reader仍只读保留。当前接线、保存/重开、CLI和同次02→04安全 XML view均已通过直接回归和clean CI，C的真实固定仓库运行仍须单独给出实际结果。

## 6. 为什么使用 JDT LS 加 JDT Core

JDT LS 已提供定位声明、实现和调用层次；JDT Core 能读取方法、构造器、参数和语法节点。两者分工如下：

- **LS 拥有唯一的项目语义索引与目标定位。** 不自己拼包名、实现继承规则或匹配重载。
- **Core 做精确语法读取，并在受控环境内解析注解身份。** 使用同一源码文本，找完整声明/方法体、实参、形参、if/try/return 等位置；它只消费已验证源码根与显式批准classpath来确定注解限定名，不建立第二张调用图。
- LS 返回整方法 range 时直接按范围查证并取源码；返回方法名位置时，Core 找包含该位置的精确声明。标准 LSP 并没有一个可直接远程调用 `IMethod.getSource()` 的请求，不能把 Java API 当成现成 LSP 接口。

选择独立的 Core 语法辅助进程，使用已固定工具 JDK；主应用维持 Java 17。不编写 JDT LS 插件、OSGi 扩展或另一套工程导入器。LS 与 Core 各有必要解析：**LS负责调用/定义/实现导航，Core负责语法输出和注解限定名binding**；后者不扩展成通用调用解析器，也不是每个步骤再解析一次。

依据：[JDT LS 官方功能](https://github.com/eclipse-jdtls/eclipse.jdt.ls)、[ASTParser](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)、[MethodDeclaration](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodDeclaration.html)。具体启动与缓存见[JDT详细设计](jdt-engine.md)。

## 7. 历史迁移顺序：先 JDT、后 JavaParser

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

## 9. 2026-09-12 历史实现审计

| 项目 | 实际状态 | 剩余边界 |
| --- | --- | --- |
| JDT Core | 独立 helper 已读取完整声明、正文、参数、调用、control/exits；生产 JDT 包不引用 JavaParser | 缺依赖时 recovered binding 只作未解析线索，不能冒充确定限定名 |
| JDT导航 | hierarchy、definition、implementation 的候选归一、构造器、循环、重复及边界已有直接测试 | 动态代理实际选择、反射目标和没有源码的外部实现仍未知 |
| 真实结果 | 固定 jshERP 注册入口自动取得三段关键 Service 正文；财务入口取得 Service 与 Mapper 声明；完整材料检查点已保存并支撑 326 个 Activity | 动态代理、反射、缺依赖和外部效果仍不能因整仓运行而视为已解析 |
| JavaParser历史现状 | Adapter 曾封装有限名称解析、方法正文、调用/参数、七图、Fact/Proof 和 Flow；生产 POM 和启动路径现不再含它 | 历史 config/payload 只读，不将其解释为 JDT 或重新启动 Adapter |
| Builder（历史） | 只消费已保存 EntryCodeContext，输出声明类型、完整方法、调用、参数、控制和限制 | 新运行由 Step05 `CodeReadingMaterialSet` 取代，不按 engine 分支 |
| 正式编排（当前） | YAML 只启动 JDT；同会话发现、导航、持久化、阅读材料为 Step01--05 | 不启动 JavaParser、严格图/Fact/Flow/M10 或模型；B的退役与clean CI已完成，C真实固定仓库验收另记 |
| JDT导航索引 | v2 的 METHOD 全仓共享、CALL 按入口保存；会话 query 缓存和原始响应复用已接入，保存材料可跨模型批次重用 | 动态分派与无源码外部调用仍按实际边界记录 |
| 引用与CI优化 | Step05/Capsule 引用保存、正文来源外置和 unit/real-JDT/quality 分类已交付 | 不新增缓存服务，不修改 JavaParser 解析算法 |
| Activity层 | 保存的 JDT 材料已经完成 326/326 Activity DRAFT＋REVIEW | 新过程发现直接复用，不全量重跑 Activity |
| 当前过程/报告 | 当前保存 340 个 Process，均为单 Activity、单 Stage；只覆盖 325 个不同 Activity，但 unmatched 为空；九章结构可渲染 | 这是 Step07 覆盖和业务串联缺口，不是引擎缺口；目标改为全仓目录、详细重建、归并和确定性 `business-processes.md` |

调研细节和准确限制见[贯穿例子](../../examples/java-code-engine-walkthrough.md)。历史调研产物仍保留；当前状态以正式 JDT 验收为准。

## 10. 开发原则

设计及偏差裁决由 Sol/ultra 或 Astra/ultra；Luna/xhigh 写直接行为测试；Terra/xhigh 实现；Sol/xhigh 定位实际故障。每个 Agent 先维护自己 progress。不要让 Terra/Luna重新设计接口、按案例增加行业规则，或者以“JDT返回包装不符”为由丢掉有效结果。

局部协议变化先同步本组详细设计，可由设计者裁决；影响业务目标、运行客户代码、外部调用权限或共享合同需交用户。只跑直接覆盖的验证，重型命令串行。当前设计工作产品模型调用为 **none**，不自动启动实现、运行调研、提交或合入 main。
