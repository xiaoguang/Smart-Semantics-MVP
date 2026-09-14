# Source Code Analysis Agent

从一份已注册、冻结的 Java/Spring MVC/MyBatis 源码，整理代码关系，解释业务活动与跨活动过程，生成可回到源码的九章业务报告。唯一公开入口是 `RepositoryAnalysisAgent`；Java 管来源、导航、上下文、预算与保存，配置的模型解释业务，默认 Pro Luna/high。

先读 [总体设计](docs/DESIGN.md)，再看 [真实财务查询与合成业务 walkthrough](docs/examples/semantic-framework-walkthrough.md)。全部文档以目标设计与当前实现分开表述；单个真实 Luna/high 活动样本只证明局部语义链可用，不代表整仓报告已生成。

新的取材方向及其[完整 JDT/JavaParser 插件设计](docs/modules/java-code-engines/README.md)已经落地，包括配置、统一材料合同、JDT 各子模块、JavaParser 适配和开发测试指南；先看[真实注册与财务代码如何进入业务解释](docs/examples/java-code-engine-walkthrough.md)更容易理解。**JDT 与 JavaParser 都已接入同一正式分析链。** YAML 每次只选择一个引擎，不自动回退或混合；JDT 提供完整导航能力，JavaParser 只保留迁移前已有的有限解析、五图、Fact 和 Flow 能力。

## 分析路线

| 步骤 | 职责 | 详细设计 |
| --- | --- | --- |
| 01 verified-source-inventory | 一次验证固定源码清单、字节与位置 | [已验证源码清单](docs/analysis-steps/01-verified-source-inventory.md) |
| 02 application-discovery | 发现合法 Spring 入口及全入口分母 | [应用发现](docs/analysis-steps/02-application-discovery.md) |
| 03 program-graphs | 选定引擎建立导航索引；保留实际可提供的五图增强 | [程序图](docs/analysis-steps/03-program-graphs.md) |
| 04 proven-code-facts | 有实际图输入时提供严格Fact/Proof，否则明确未生成增强 | [已证明代码事实](docs/analysis-steps/04-proven-code-facts.md) |
| 05 business-flows | 一次组织入口执行上下文并投影 Capsule | [业务流程](docs/analysis-steps/05-business-flows.md) |
| 06 flow-interpretation | 连贯材料与活动 DRAFT/完整 REVIEW | [流程解释](docs/analysis-steps/06-flow-interpretation.md) |
| 07 repository-knowledge | 宽松召回、跨活动过程与仓库知识 | [仓库知识](docs/analysis-steps/07-repository-knowledge.md) |
| 08 nine-section-document | 九章段落 DRAFT/完整 REVIEW 与确定性排版 | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

步骤 04、05 保留。严格 Fact 没有覆盖的安全源码仍可用于业务理解；未知技术边保持未知。普通 publisher 不重新运行 compiler/projector，保存的步骤产物和来源完整性仍保留。

JDT目标不以重做全部五图为取材前置，也不自动调用JavaParser兜底。完整源码、候选与边界使用统一合同交给同一业务链；两个引擎的解析能力可以不同，差异必须如实可见。

## 当前能力与明确差距

现有实现包含源码清单、入口发现、五图、选定技术 Facts、入口 Flow/Capsule，以及 BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher 和 BusinessAnalysisWorkflow。业务材料统一从已发布的 Step05 读取；具体 public 操作成熟度见 [公共接口附录](docs/references/inherited-public-and-module-contracts.md)。

固定 jshERP commit `8c30ce7861570458920175e200bb2a6442713580` 的完整 719 文件离线捕获及图/Fact 运行已有证据。财务单号查询的小例可在图中看到跨层参数传递；对应图/Fact run 没有 Step05 正式 publication。另一运行保存了 339 份入口材料，其中 31 份来自已编译 Flow、308 份来自同一 Step05 安全入口上下文；这些材料不能被拼成一份已验收业务交付。

当前已打通五图/Step05 关系到材料的消费，停止普通 Flow/Capsule publish 的重复 compile/project，并保留无严格 Fact 的可读条件；合法无 method 的 RequestMapping 已通过 `methodCondition` 被发现为未限制入口。固定 jshERP commit 的一次零模型全仓验证生成 107 个材料包，覆盖 339 个入口：21 个单入口包、10 个双入口包、6 个三入口包、70 个四入口包；12 包为 `FLOW_PREFERRED`，95 包为 `ENTRY_SOURCE_FALLBACK`。每个入口都有材料或明确限制，材料分组数不等于入口数，也不是固定生产常量。

JDT 第一阶段已经通过两层验收：安装的 JDT LS 1.61.0 与独立 JDT Core helper 在固定 jshERP 源码上自动找到了注册入口的 `validateCaptcha`、`checkLoginName`、`registerUser` Service 正文，并在财务入口到达 Service 与 Mapper 声明；另一个自包含 Spring/MyBatis 仓库从真实 JDT 配置经过发现、导航索引、Step05、业务材料和 scripted 业务链生成了恰好九章的 Markdown。两项验收均未运行客户 Maven、客户应用或产品模型，也不等于完整 jshERP 业务语义已经验收。

自动 `POST /user/registerUser` 与 `POST /user/login` 小包均已完成一次真实 Luna/high DRAFT+REVIEW；现有业务语言 Prompt 已在登录样本中保留完整路径并避免不必要的 Java 标识泄漏。两份完成活动的一次真实过程审阅没有把它们仅因同一 Controller/验证码硬拼成顺序过程，而是保守地保留为两个独立局部过程。它们证明局部语义和“拒绝无依据连接”的过程边界可用，不证明跨入口过程或整仓九章报告已经验收。四个业务 Module 已实现不等于自动语义和整仓九章已验收。

已完成[旧解释链清理与任意 N 活动覆盖设计](docs/plans/code-cleanup-and-scalable-activity-coverage-design.md)的代码收口：结构/范围合法但漏入口的 Activity DRAFT 会进入唯一 REVIEW，REVIEW 必须用活动或显式 `unexplainedEntries` 闭合；程序把具体未解释入口按材料投影给过程与报告，第 9 章必须说明对应 HTTP 入口及原因。当前资源、coverage 和 knowledge/report checkpoint 都使用 v2；历史 v1 调用不会被重放或冒充为修复后的结果。双引擎都已通过正式材料接力与 scripted 九章回归；产品 Luna 与完整仓库业务质量仍需单独验收。

## 九章与阅读依据

已批准的[材料检查点与模型批次最小解耦](docs/modules/model-job-execution.md#7-固定材料与独立模型批次已批准待实施)**尚待实施**：JDT/Builder 材料保存一次；后续显式模型批次直接读取它，失败不重扫。旧失败日志不动，只有完整已审且输入/模型/来源匹配的 job 才复用；其余必要 job 在新批次完成 DRAFT＋完整 REVIEW。现有 generate 能从 Step05 重建材料但仍受同 run失败状态约束，不能把目标写成当前命令已支持。操作现状和目标分开见[运行指南](tools/repository-run/README.md)。

[导航复用、共享正文、单次本地CI与来源外置](docs/plans/navigation-reuse-and-readable-report-design.md)已交付，测量结果保留在该设计的验收节。[模型任务并行设计](docs/modules/model-job-execution.md)也已接入正式运行链，其[本地验证记录](docs/supplements/model-job-parallel-execution-verification.md)保存 12 个 Activity job、两个 Process-group job 和两级并发峰值。同一 `repository-run-config-v2` YAML/JSON 配置全局及每 Provider 上限、稳定路由和非秘密认证引用；Activity 与 Process-group 各以完整 DRAFT→REVIEW 为一个并行 job，完成结果立即保存后稳定聚合。Codex Subscription 强制 ChatGPT 登录上下文并清除 API 认证环境；显式 OpenAI API 使用 Responses SDK、关闭自动重试。过程组全部完成后才执行至多一次仓库总结，随后只生成并审阅一份九章。共享账户额度不因多 key/新会话增加；失败不重试或转路。

固定章节为：文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、示例问题、待确认事项。模型写自然段 JSON 并完整审阅；程序排 Markdown 和短 ref。完整已审条件、规则与长段落保留到报告，不只传递摘要标题。

SourceRef 定位冻结文件、行段和原文。Fact/Proof 只证明支持的精确技术陈述；源码行为不证明某次运行成功。所有发现入口必须有分析结果或未分析原因，一个好切片不能宣布整仓完成。

优化后的document.md只保留九章业务正文与来源编号，源码单独在已有source-refs.jsonl中查询；不再把几千行代码附在第一章。模型实际输入仍保留完整实现，纯重渲染不调用模型。已有JDT四入口比较产物只读保留，其中报告为未完成REVIEW的草稿，不冒充最终业务验收。

## 开发与文档入口

- [引擎完整设计和各子模块](docs/modules/java-code-engines/README.md)：本轮设计主入口。
- [配置与共同材料合同](docs/modules/java-code-engines/contracts-and-configuration.md)
- [模型任务、两级并发 YAML、认证与保存](docs/modules/model-job-execution.md)：已实现的并行执行合同。
- [JDT子模块算法与失败行为](docs/modules/java-code-engines/jdt-engine.md)
- [先JDT、后JavaParser的接入与测试指南](docs/modules/java-code-engines/integration-and-javaparser.md)

- [JDT LS 最小可行性验证](docs/plans/jdtls-source-navigation-feasibility-plan.md)：保留的独立调研记录；其结论已经进入正式 JDT 分析路线。
- [源目录约束](AGENTS.md)
- [来源、发布和失败边界](docs/references/foundation-and-publication-contracts.md)
- [Canonical 身份公式](docs/references/canonical-persistence-identity-contracts.md)
- [公共接口与 SourceLocator](docs/references/inherited-public-and-module-contracts.md)
- [模型材料与 Prompt](docs/references/semantic-interpretation-prompts.md)
- [后续最小改动与测试衔接](docs/plans/coherent-code-context-implementation-plan.md)
- [已批准清理与可扩展活动覆盖设计](docs/plans/code-cleanup-and-scalable-activity-coverage-design.md)
- [工具链约束与定向验证](docs/plans/target-standards-and-toolchain-plan.md)
- [程序图能力与待办](docs/supplements/program-graphs-implementation-backlog.md)
- [延后恢复工作](docs/supplements/runtime-recovery-todo.md)

历史文件与已完成 progress 保留原样，不作为新实现契约。后续实现遵循 Luna/xhigh RED、Terra/xhigh GREEN，只跑直接覆盖改动的测试；真实 Provider、捕获、生成和发布仍须明确授权。同一已给出的运行授权不重复询问，但用户当前暂停/讨论指令优先；本次文档刷新不启动运行。

已完成的真实小包质量检查只覆盖 jshERP 的用户登录和用户注册：两个活动、两个保守独立过程和一份九章报告。它证明报告链路能保留业务语言与不确定性，不能替代完整仓库验收。
