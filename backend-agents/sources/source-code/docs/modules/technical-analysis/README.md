# 技术材料采集：源码准备之后、业务解释之前

状态：2026-09-26 详细设计修订；三个命令、Maven自动准备、Java 就绪检查和正式前后端关联尚未实现。本次仅改文档。现有 JDT 导航、MyBatis/XML/SQL 分析、后端阅读材料继续复用。设计经过代码接线及冻结源码走读，不等于新实现或真实验收通过。

## 1. 要解决什么

用户完成源码准备后，应能分别执行“发现入口并收集代码”“补全持久化材料”“组织阅读材料”。每次操作读取明确的已保存上游，保存自己的结果，然后结束。用户/Codex 可以检查问题，再决定下一操作；程序不因某项成功自动启动业务模型。

本轮增加两项实质能力：

1. **在 Java 导航前自动准备并核对分析环境。** 常规Maven的父POM/多模块/Profile/传递依赖由标准工具解析，使用本地缓存及配置允许仓库；特殊构建由Agent根据报告询问。得到按模块的实际环境后，核对目标JDK和JDT诊断，仍未就绪则不导航。不是LLM猜JAR，也不是直接执行客户Maven脚本。
2. **保存前端请求与后端入口的关系。** 识别页面、共享组件、请求封装和 HTTP 路径，关联已有 Spring 入口；Step05 将相关页面和后端原文放入同一材料包。

不增加第二套 Java 调用分析，不建立 Fact/Proof，不判断“这是不是采购”，不推断业务生命周期。页面关联、Java 调用、Mapper 绑定都是带限制的静态源码关系，不是运行时记录。

## 2. 用户操作与内部步骤

一个程序仍叫 `source-analysis`。下列是该程序的三个**拟新增子命令**，不是三个独立程序。

| 操作 | 内部步骤 | 输入 | 输出 | 不做什么 |
| --- | --- | --- | --- | --- |
| `collect-code`：发现入口并收集代码 | Step02＋Step03 | 准备结果、明确Maven构建选择/仓库策略（或可信环境）、前端静态配置 | 实际编译环境、Java就绪报告、应用/入口/Mapper目录、前端请求关系、Java方法及调用索引 | 不解析 SQL、不组模型包、不调用模型 |
| `analyze-persistence`：补全持久化材料 | Step04 | 指定 collect-code 输出及其中的来源 | Mapper XML、声明绑定、SQL结构和限制 | 不启动 JDT/前端 parser，不执行数据库 |
| `assemble-materials`：组织入口阅读材料 | Step05 | 指定 Step04 输出，沿引用读取 Step02/03 | 可重开的入口材料包及全部入口处置 | 不重新解析 Java/Vue/XML/SQL，不解释 Activity |

保留八个内部 step key 与历史地址；不因显示名称变化迁移历史目录。Step02、Step03 分别保存，只有进程/JDT会话共用。这样既不多启动一次 JDT，也可以明确看出“入口发现结果”和“调用收集结果”。

```text
源码准备 R0：固定原文＋有效范围
  └─ collect-code R1
       ├─ Step02：自动准备依赖 → 就绪检查／入口／前端请求关系
       └─ Step03：复用现有 JDT 收集完整 Java 方法和调用
            └─ analyze-persistence R2：Mapper → XML／SQL
                 └─ assemble-materials R3：入口＋页面＋Java＋XML／SQL
                      └─ 本轮到此结束，不启动 Step06/07
```

R0–R3 是不同运行，**不是不同源码版本**。各运行保存完整上游引用和同一个 `SelectedSourceBasis`。具体接口、版本、保存及失败合同见[命令与运行](cli-and-runtime.md)。

用户已确认：三个新操作接通并验收后删除旧`plan-materials`，不保留另一条一键生产路线。Skill按任务范围串联新命令，Java返回结构化结果，Agent在需要决定时询问用户；Skill本身不是通信进程。成功不逐步重复询问，失败不只留一条异常字符串。

## 3. 模块分工与复用边界

| 模块/现有接缝 | 责任 | 本轮变化 |
| --- | --- | --- |
| `PreparedVerifiedSourceTextReader`／历史文本 reader | 只返回选定来源中有效、未排除的原文 | 复用；技术运行器必须真正接入准备版 reader |
| `JavaDependencyPreparer`（拟新增，Step02内部） | 固定ModelBuilder/Resolver解析声明式Maven、受控下载、每模块环境及问题 | 新增；标准库负责POM与依赖语义，不运行客户扩展/构建 |
| `JavaAnalysisReadiness`（拟新增，Step02 内部） | 核对完整编译环境、诊断覆盖和可导航状态 | 新增；不解析业务、不裁决调用目标 |
| `JdtProjectSession`／`JavaCodeSession` | 同一冻结投影的 catalog/collect/工具生命周期 | 补按模块和目标平台的环境投影、helper v3及诊断；不修改 collect 算法 |
| `ApplicationDiscoveryExecutor` | POM/config 技术线索、Spring入口、Mapper候选 | 复用；接入准备来源、就绪报告和前端关系 |
| `FrontendHttpDiscoverer`（拟新增，Step02 内部） | 已支持Vue/JS静态结构、请求路径、后端入口候选；TS未验证则报告不支持 | 新增；成熟 parser 取语法，有限规则连接请求 |
| `ProgramGraphsExecution`／`EntryCodeCollector` | 从精确入口收集 Java 调用/实现/正文 | 算法保持；仅修改来源/输出归属接线并做验证 |
| `DefaultPersistenceAnalyzer` | 官方 MyBatis 部件、安全 XML、JSqlParser | 算法保持；独立重开输入、输出属于 R2 |
| `DefaultCodeReadingMaterialBuilder` | 按入口组织已保存原文与关联 | 扩展前端选择及范围处置；不变成前端分析器 |
| 现有 publisher/reader/store | 安装、重开、身份与引用校验 | 同运行假设改为准确 lineage；版本一起更新 |
| 现有 Agent/CLI/运行登记 | 配置、工具生命周期、状态、查询 | 新增三项意图；不创建公共 Agent 或恢复框架 |
| 模块内流程 Skill | 指导 Codex 调命令、读实际结果、向用户报告 | 只在实现并验收之后扩展执行说明；不能先教 Skill 调不存在命令 |

详细职责由 [Step02](../../analysis-steps/02-application-discovery.md)、[Step03](../../analysis-steps/03-program-graphs.md)、[Step04](../../analysis-steps/04-proven-code-facts.md)、[Step05](../../analysis-steps/05-business-flows.md)分别拥有；本模块文档只拥有跨步骤接线、Java环境和前端新增能力。

## 4. 已实现的基础与真正缺口

已核对的代码事实：

- `PersistedTechnicalRunExecutor.execute()`目前会重新发布旧 Step01，打开一个 JDT session 后连做02–05；不是读取 prepare-source 结果的三个命令。
- `SourceAnalysisExecution.executeMaterialsOnly()`仍依赖旧 Git 捕获。准备版公共 reader 和拒绝混用旧材料的检查已存在，但**准备版来源直接生产技术材料**仍需本轮接通。
- Step02已有持久入口与Mapper候选；没有 Vue请求关系。Step03已有调用、所有候选、正文及局限。Step04已有完整 XML/SQL；Step05已有后端包和引用式保存。
- `PersistenceMaterialPublisher`及`CodeReadingMaterialPublisher`目前要求上游运行ID相同，并从来源运行推导输出位置。这与三个独立操作不相容，必须一起改生产者、读取器与运行输出，不能只加 CLI 参数。
- `VerifiedJavaProject`核对已列 JAR 的存在及摘要，不证明“该列的 JAR 一个不少”；JDT客户端当前丢弃 `publishDiagnostics` 通知。
- 当前仅一个project/classpath，运行器把sourceLevel写为17；Core把工具VM类库加入解析。父子/多模块自动解析及两套工具目标平台一致性是本次真实修改项，不是已经支持的能力。工具方案、支持子集和失败说明见[自动依赖准备](dependency-preparation.md)。
- 已删除的 JavaParser／五图生成算法不能列作“待删除现存实现”。剩余历史读者/DTO必须按真实消费者分别决定去留，见[清理设计](../../plans/technical-analysis-cli-and-vue-cleanup-design.md)。

## 5. 两种变化不要混为一谈

**源码刷新/排除：**R0产生新源码版本。例如排除某XML后，旧索引仍含该XML，不能用于新范围。旧版本仍可查看；首版不计算哪些旧材料恰好不受影响。

**仅补编译依赖：**源码字节和版本可不变，但 Java 分析基础改变。例如第一次少 SLF4J，第二次补齐；旧调用结果不会因此自行变正确。需要用户显式执行新的 collect-code，保存新的 `javaAnalysisBasis` 和索引，再选择是否执行04/05。不能自动重跑 Activity。

同来源、同依赖、同工具、同有效配置的已保存输出可被下一命令直接读取；没有自动查找“最新索引”、跨版本拼接、覆盖历史或自动补算。

## 6. 验收不偷换目标

1. 准备版来源能够进入02–05，排除文件未被投影、前端读取或XML读取重新带回。
2. Step02能说明实际父POM/Profile/模块、JDK、编译依赖、准备操作和诊断；需要用户处理时返回具名问题和已保存报告。“下载完成”不是“就绪”，“报告未收到”不是“零错误”。依赖核对边界见[Java就绪设计](java-readiness.md)。
3. 使用**现有Step03**复验错误Mapper、logger.error、重载、嵌套调用及合法多态。错误消失才通过这一改动的验收；有残留则列出，不暗中增加导航裁决器。
4. Vue通过真实组件/封装关联到HTTP入口，再通过已保存Java及Mapper关系取得SQL。未知路径、多候选、SQL动态片段仍能定位查看，不伪造唯一链。
5. 三个命令可以分进程执行，后两项不启动JDT，组包不重新解析；完整来源和各步骤输出归属一致。
6. 零客户构建/插件/生成器执行、零数据库、零业务模型、零Step06/07执行；允许的POM/JAR取得是程序数据准备，不运行其中客户代码。未来模型能否正确解释流程不属于这次技术材料验收。

真实例子及现有局限见[Vue到SQL走读](../../supplements/vue-to-sql-walkthrough.md)。Maven装配/模块隔离、JDT平台/诊断、前端静态关联三处先通过有限可行性夹具，再全面接线；技术假设未验证时不宣称完整链已实现。
