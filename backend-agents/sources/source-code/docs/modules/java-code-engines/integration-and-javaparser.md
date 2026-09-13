# 接入现有流水线与JavaParser第二阶段适配

> [总设计](README.md)。本页固定改哪里、保留什么、怎么证明没丢内容。不是再次重写八步。

## 1. 第一阶段接线：先使JDT独立成立

### 1.1 组合根与应用发现

组合根加载 engine 配置并打开一次 snapshot-bound session；`PersistedTechnicalRunExecutor` 将同一会话交给 application discovery、navigation publication 与 Step05，再由业务运行链消费已保存结果。JDT 路径不 hardcode 或调用 JavaParser；JavaParser 路径也不启动 JDT。未知配置值明确失败。

已批准的后续优化将相同操作/位置的JDT查询和完整方法表提升为该会话的共享数据，不改变组合根或新建运行路径。复用范围、失败缓存及索引就绪条件见[优化设计](../../plans/navigation-reuse-and-readable-report-design.md)。宿主仍Java17；正式目录旧分支的整体Java25升级不是该优化前提。

ApplicationProfileDetector继续静态读POM/配置以形成VerifiedJavaProject；涉及Java声明的部分改为消费`session.catalog()`。SpringHttpEntryDiscoverer消费统一AnnotationView/MethodDeclarationView，route组合规则只有一份，继续支持省略method及method={}，不制造默认GET。Mapper catalog的Java声明来自选定引擎；XML安全读取继续复用。

注解identity解析归JDT Adapter（Core名称范围+LS声明定位），不留给共享发现器重写类型规则。未确认的mapping方法保留可定位候选与具体限制；不能按simple name冒充Spring，也不能从site分母吞掉。显式框架注解/源码组合注解/缺依赖分支必须有直接测试。

在代码布局上保持语义命名：

```text
org.sourceanalysis.app.analysis.code
  JavaCodeEngine / JavaCodeSession / 中立records
  jdt/                 LS会话、Core语法客户端、导航和收集
  javaparser/          已接入的Adapter，保留迁移前现有能力
org.sourceanalysis.app.runtime
  引擎配置和组合根
org.sourceanalysis.app.analysis.flow
  既有compiler、projector、publisher及新的context接线
```

`analysis.code`是本次新增的来源专属技术seam，不是第九个analysis step，也不新增`target/stageNN/common`。helper是同一source-code工程内的工具构建单元，运行于tool JDK；不放到共享合同，也不创建POC产品命名空间。

### 1.2 Step03/04的实际产物可用性

Step03调选定会话建立导航材料，保存`java-code-index.jsonl`。JDT第一阶段不调用JavaParser五图builders、不制造完整CFG/DFG。旧五图能力保留代码和既有有效产物；只有真正运行技术增强时才登记graph descriptors。

索引固定登记为现有 `PROGRAM_GRAPHS` 的 module 7 `java-code-index`，不是 module 6 的别名，也不创建新 step。JDT 路径 Step03 的实际公开集合精确为 `java-code-index.jsonl + program-graphs-receipt.json`；module/step receipt 只列这一项语义 payload。JavaParser 路径由 module 7 作为最终发布模块登记 index 加现有七个图语义 payload，避免修改 step store 的单一最终 publisher 规则。

同一step receipt的artifact列表描述实际文件；索引ENGINE记录中的technicalEnhancements说明严格五图增强`NOT_PRODUCED`及原因，不给generic receipt另增状态机。**不是写五个空文件骗过旧reader，也不是吞掉一次运行失败后当作optional。** 已提供的图如果损坏仍拒绝；没有请求/没有实现的增强才可未生成。

Step04无graph增强输入时仍有明确的步骤结果：没有评估严格事实，并关联Step03导航basis。保存原有`fact-accounting.json`位置中的新版本状态与step receipt，明确`availability=NOT_PRODUCED`，不声称“候选0条，全部已证明”。不调用旧FactCandidateEnumerator。已有可用图时运行现有严格Fact算法一次并保存原有结果，不改变Fact.kind或Proof准入意义。

JDT 路径 Step04 的实际公开集合精确为 `fact-accounting.json + proven-code-facts-receipt.json`。v4 accounting 的 `reason` 非空，所有数值 counts 为 `null`，并保留 Step03 navigation/index basis；它不得含 Candidate/Fact/Proof 引用。Step05 不以这个状态阻断入口：先读 `EntryCodeContext`，再按 AVAILABLE 与否附严格增强。

这要求同步实际artifact policy、schema、step store、run检查器和直接消费者。校验目标是“当前能力的产物集合正确”，不是历史固定数量齐全。具体owner版本已列在[合同版本表](contracts-and-configuration.md#5-保存格式位置与复用)，不要全工程Wire Reset或让JavaParser兼容需求阻止JDT接线。

### 1.3 EntryContextAssembler：Step05的职责收窄

输入：Step02所有入口、Step03统一索引、可选同源图/Fact/strict Flow。输出：`FlowCompilation.entryContexts`中的新合同对象、原样Capsule投影与每入口处置。

处理：按ENTRY_MEMBERSHIP重新组装完整MethodCode/CallSite，不重新定位方法。导航索引v2的METHOD仍全仓共享，CALL必须按入口owner及物理callKey读取；不能把另一入口已经展开的目标替换本入口的NOT_EXPANDED。附有效技术refs，不用refs筛掉原文。一个safe entry没有strict Flow时`flowRef=null`。现有严格Flow仍可单独保留，但不得要求新context伪造OutcomePath/obligation才能过publisher。

唯一依据模型为[EntryCodeContext](contracts-and-configuration.md)，业务消费者不使用旧strict CallContext代替完整导航。优化后的磁盘Step05只写codeContextRef，Capsule只写entryContextRef；reader在既有边界恢复完整对象，不另判一次调用是不是可信、不重启JDT。compiler/projector/publisher、artifact policy、reader和两引擎直接fixture必须在同一次交付中完成对应v6/v11/v6/v9升级，不能只改写出端。

失败：entry ID不属于同快照、method/source引用断裂、原文范围错误或安装碰撞为fatal；未解析call是局部limitation。无安全入口位置为具体NOT_COLLECTED处置；无strict Flow但有body可正常形成阅读材料。0入口仍有空索引/覆盖与诚实步骤结果。

### 1.4 BusinessMaterialBuilder：不再藏解析器

Builder 已不含 JavaParser 语法读取或引擎分支。它只做完整方法选择、调用/候选展示、短 ref 分配、来源映射与保存；声明类型也进入可读观察，避免模型只看见同名方法却不知道所属 Controller 或 Service。

模型侧用包内E/M/C/S短编号连接入口、方法、调用和来源。每个选中方法有完整正文；调用携带原文、有序actual与目标formals，候选分支明确标注。完整代码中已有的条件、对象构造、保存调用和return不能被`maxObservations`等摘要限制一起删除。

模型输入不包含path/hash、引擎版本、LSP响应或Proof链。程序侧保存来源；技术诊断用简短限制解释。一个入口、多个直接Service及其辅助方法可以同包，不强制每个方法一次模型调用。

当内容过大：先在入口边界分包，共享方法去重；对单入口保留整体调用目录与明确选入的完整方法，未进入材料的body有具体记录。若入口和直接业务实现都塞不进有效请求，不把Controller-only降级叫作完整业务材料；需扩展有效上下文或报告材料不完整。成本预算不是静默截断的理由，真实模型有上下文上限则如实处理，不声称用户可用授权消除物理限制。

ActivityExplainer、ProcessExplainer、BusinessReportPublisher的现有职责不重写。模型能够解释对象、条件与业务目的，Java不能用新的行业词典替代。现有missingEntryKeys/完整REVIEW/具体未解释入口下传规则继续生效。

## 2. JavaParserCodeEngine：已完成的第二阶段适配

### 为什么保留

现有实现积累了五图、Fact/Flow、来源和大量有效边界测试，现在是不使用 JDT 工具进程时的可选引擎。已经发现的有限名称处理缺陷说明当前 Adapter 的能力边界，不等于 JavaParser 库天生不能导航。

### 输入、处理和输出

输入同一个VerifiedJavaProject和EntrySeed。Adapter封装当前JavaParser源码读取与关系结果，投影到第一阶段最终稳定的catalog/EntryCodeContext。

| 现有信息 | 第二阶段映射 |
| --- | --- |
| 已定位方法、参数、正文 | MethodCode，保留实际字段，不补不存在的正文 |
| 现有已解析call及actual/formal | CallSite、声明target、POSITIONAL关联；严格证明仍单独附refs |
| 当前接口/外部边界 | DECLARATION_ONLY/EXTERNAL；没有新实现候选就不制造 |
| 未解析调用或graph Gap | 原文/位置及UNRESOLVED；无法定位则具体说明 |
| 现有controls、returns、源码观察 | 统一语法材料；不把缺CFG细边当做缺少源码 |
| 五图、Facts、strict Flows | 可选technicalEnhancements真实引用，沿用其可信性 |

包装所必需的通用语法字段可以用JavaParser已有AST读取，但**不新增定位能力**：不额外实现wildcard/继承/重载修正，不新增Symbol Solver工程接线，不逐项追平JDT。新增字段若是旧能力无法给出的语义结果，填合法的未知/缺失状态；若是已存在完整源码可直接给出的范围/参数，则正常映射，不以旧DTO没有为由丢掉。

第二阶段已对照迁移前保存的现有能力测试和代表输出验收，不对照 JDT 更丰富的结果计数。已能找到的调用、参数和正文没有退化；当前找不到的 Service 未在此阶段补写解析规则。源码和通用测试保留，统一 context 直接供下游消费。

## 3. 具体代码改动地图

以下路径均相对`src/main/java/org/sourceanalysis/app/`，指当前源码位置，不是本次已修改清单。

| 位置 | 第一阶段 | 第二阶段 |
| --- | --- | --- |
| runtime/TechnicalAnalysisWorkflow.java | 注入选定engine，去掉JDT路径强制五图/Fact硬依赖 | 挂上JavaParser Adapter已有技术增强 |
| runtime/PersistedTechnicalRunExecutor.java | 固定有效配置与engine basis；保存步骤可用性 | 复用同一编排，不另建运行核心 |
| analysis/discovery/* | Java AST读取移到engine catalog；共享Spring与XML规则 | 映射原JavaParsercatalog |
| analysis/graph/ProgramGraphsExecution.java | 保留现有算法，JDT取材不调用；新增导航publication接线 | 用作JavaParser实际增强，不重复build |
| analysis/fact/* | 提供明确未生成增强路径，不伪造Fact | 原有严格Fact路径回归 |
| analysis/flow/compiler/FlowCompilation.java | 升级context为候选/正文/实参形参合同 | 只适配字段 |
| analysis/flow/compiler/EntryRootedFlowCompiler.java | 入口context消费engine结果；严格Flow算法留在原技术增强路径 | 恢复旧技术能力输出 |
| analysis/flow的projector/publisher/readers | 移除无body读取却要求strictProof的耦合；同步新版本 | 共用 |
| analysis/interpretation/material/BusinessMaterialBuilder.java | 删除公共路径JavaParser调用，直接消费统一材料 | 共用，禁止engine条件分支 |
| runtime/PersistedBusinessRunExecutor.java | 注入同一材料结果，保持四模块 | 共用 |
| adapter/cli/SourceAnalysisCli.java | 宿主启动加载配置，现有命令语义不变 | YAML选择第二个Adapter |
| artifact中的policy/store/readers | 接受实际能力声明，拒绝旧context版本或损坏ref | 共用；不建设双版本reader |

具体类可以在实现时做小型内聚整理；不得换成另一套公开Agent、第三个scanner、行业规则器或兼容桥。

## 4. 验收矩阵：测业务材料，而不是只测壳

| 测试 | 第一阶段JDT必须满足 | 第二阶段JavaParser要求 |
| --- | --- | --- |
| 注册真实入口 | 三段Service正文自动取得，继续处理内部调用，限制明确 | 保留当前能取得的内容；不以相同结果为门槛 |
| 财务真实入口 | Controller/Service/Mapper声明与billId对照，完整正常/异常返回 | 原有财务能力不退化 |
| wildcard/static import、重载/继承 | 定位交JDT，不含拼包名hack | 当前能力如实返回，定位增强不在第二阶段 |
| interface两实现 | 全部候选，未伪称运行时选中 | 无实现解析能力时保留声明/原因 |
| 构造器、lambda、方法引用、递归 | 原文/归属/候选或具体限制，调用点不静默删除 | 已有语法可映射，未有定位不补编译器规则 |
| source切片 | 中文/emoji/CRLF、重复调用位置、全方法字节/行号准确 | 同一来源标准 |
| Spring入口 | unrestricted合法，不默认GET；全入口都有处置 | 同一规则/分母 |
| 模型实际input | 包含入口和已选完整Service，候选与未展开信息可读 | 同一shape，能力差别明示 |
| scripted业务链 | 活动→过程→九章，条件/结果/来源不丢；rerender零调用 | 同一业务模块和测试 |
| 动态实际执行 | 不声称“此次实际执行/提交成功” | 同样 |
| engine隔离 | JDT路径不加载/调用JavaParser；不隐式fallback | 选择JavaParser不启动JDT |
| 保存重开 | 新context完整，损坏/旧版被拒绝，不重复导航 | 同一标准 |

测试模型分工：Luna/xhigh写直接公开seam RED；Terra/xhigh最小GREEN；Sol/xhigh调试，架构变化由Sol/ultra或Astra/ultra。每人维护独立progress。不为中间private helper每函数建立重复测试；helper位置转换等高风险内部seam可有精确测试。

第一阶段先位置/响应小fixture，再真实注册、同索引财务，再不同领域与多入口scripted全链。先观察JSON真实内容，确认Service正文已进入实际模型请求，才讨论扩大真实模型运行。产品调用仍需授权，设计/自动测试不调用Luna。

### 4.1 本地CI的唯一执行归属

普通录制LSP、纯Java和scripted Provider测试由Surefire执行；启动真实LS/Core、依赖固定客户源码的测试移到显式`real-jdt-it` Failsafe profile与`*IT`类，不能两边重复运行。一次完整交付用一个verify生命周期连接unit、IT、SpotBugs/PMD；quality不得硬写skipTests=false或自己再触发test。日常RED/GREEN仍只跑直接selector。[目标命令与验收](../../plans/navigation-reuse-and-readable-report-design.md#9-本地-ci一个测试只由一个阶段执行)尚待POM/测试实现，本轮不改构建文件。

## 5. 实施收口与禁止扩大项

第一阶段已经交付可运行 JDT 取材及既有业务链：真实位置、完整代码和候选可观察，保存重开后进入 BusinessMaterialBuilder，并在自包含 Spring/MyBatis 验收中到达 scripted 九章。固定 jshERP 注册/财务入口也通过真实工具检查。当时JavaParser未适配不影响第一阶段结论；第二阶段随后已经完成，不再列为当前待开发。

第二阶段已经从第一阶段冻结合同出发，恢复可选 JavaParser 及其迁移前能力。后续不要开展两引擎自动投票、混合结果、失败 fallback、runtime 插件安装、跨引擎缓存复用、完整编译器/外部效果证明、复杂恢复，也不要求重新设计报告模块。

最终一次YAML选择决定取材引擎；业务模块始终一套。源码能力可以不同，来源正确、内容保存、入口处置和业务职责边界必须相同。

选定引擎也是运行身份的一部分。同一运行已经安装某个引擎的
`java-code-index.jsonl`后，以另一个引擎再次发布会触发
`MODULE_PUBLICATION_COLLISION`，原索引保持不变；切换YAML配置必须创建新运行。该门禁禁止
JDT与JavaParser跨引擎覆盖或复用同一份索引，不实现自动fallback或混合结果。

现有 `CanonicalModuleArtifactStore`、`CanonicalAnalysisStepArtifactStore`、CLI 操作面、`RepositoryAnalysisAgent`、`SourceAnalysisApplication`、`BusinessAnalysisWorkflow`、`PersistedBusinessRunExecutor`、`ActivityExplainer`、`ProcessExplainer` 和 `BusinessReportPublisher` 已由两个引擎共用。JavaParser Adapter 与双引擎选择回归没有重写这些组件。

JavaParser 迁移 oracle 固定在 git `cec1997`。迁移前先记录 `SpringHttpEntryDiscovererTest`、`MapperCapabilityCatalogerTest`、四个 graph builder 测试、`EvidenceGraphBuilderTest`、`ProvenCodeFactsExecutionTest`、`BusinessFlowsExecutionTest`、`BusinessMaterialBuilderTest`、`TechnicalAnalysisWorkflowTest` 与 `FourEntryBusinessSemanticChainTest` 的行为；第二阶段保留可观察能力和诚实 gap，不比较两引擎数量、解析率或 JSON SHA。可执行清单见[实施计划](../../plans/jdt-first-java-engine-implementation-plan.md#javaparser-pre-migration-capability-baseline-at-cec1997)。
