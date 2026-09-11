# Source Code Analysis Agent

本 Agent 把一份完整冻结的 Java/Spring MVC/MyBatis 仓库整理成一份可追溯、明确写出未知项的仓库级九章业务说明。

核心路线是四个深 Module：

~~~text
Steps 01–05 稳定技术产物
  -> BusinessMaterialBuilder：少量连贯源码 + 程序观察 + 短 SourceRef
  -> ActivityExplainer：Luna/high 解释并审阅完整局部活动
  -> ProcessExplainer：程序宽松召回 + Luna/high 串联过程和仓库知识
  -> BusinessReportPublisher：Luna/high 写九章段落 JSON + Java 确定性排 Markdown
~~~

Java 不再被要求按行业词典分析业务。严格 Fact/Proof/Flow/Capsule 继续保留为技术能力和可选技术查看；业务解释的最低来源是程序创建的短 ref，它映射到同一冻结源码的 repository-relative file、精确 lines 和 snippet。模型不能创造 ref、路径、行号或 hash。

清楚构造并保存对象的源码可以写成“系统生成并保存该业务对象”；这描述代码定义行为，不表示某次运行成功。模糊边界调用、岗位、制度、唯一性、实际库存/记账/付款结果和成功次数不得凭常识补全。

目标设计权威是 [docs/DESIGN.md](docs/DESIGN.md)。当前 Java/Schema 只实现其中一部分：四个深 Module 与其 Step 06–08 简单检查点已有经过 scripted Provider 验证的纵切；两个固定 jshERP 小包已通过真实 Luna/high 的局部活动质量检查。公开 Java Agent 与最小 CLI 的排队、执行和检查已经接通；Java 的重渲染和已保存业务输出查询已有实现但尚未由配置 bootstrap 装配到 CLI。本机配置加载、HTTP、跨入口真实过程、整仓模型运行和整仓验收仍未完成。

## 五分钟阅读路线

1. [总体设计](docs/DESIGN.md)：四个深 Module、短 ref、模型边界、调用上限与全仓覆盖。
2. [完整合成 walkthrough](docs/examples/semantic-framework-walkthrough.md)：三入口补货 → 收货 → 应付账单，从 Java/SQL 一直走到恰好九章 Markdown。
3. [Step 06](docs/analysis-steps/06-flow-interpretation.md)：BusinessMaterialBuilder 与 ActivityExplainer。
4. [Step 07](docs/analysis-steps/07-repository-knowledge.md)：ProcessExplainer 与一份完整仓库知识。
5. [Step 08](docs/analysis-steps/08-nine-section-document.md)：BusinessReportPublisher、九章 JSON、同文档 refs 与确定性排版。
6. [中文 Prompt](docs/references/semantic-interpretation-prompts.md)：Activity、Process 和 Report 的 DRAFT + 一次 REVIEW。

[旧语义框架十批计划](docs/plans/semantic-framework-ten-batch-implementation-plan.md) 已被本次设计替代，不能继续照旧实施。后续应从四个深 Module 重新形成精简实施计划。docs/history/ 与 progress/ 只作历史/工程记录。

## 八个分析步骤

| 运行目录 | 人类问题 | 当前业务路线中的作用 | 详细设计 |
| --- | --- | --- | --- |
| steps/01-verified-source-inventory/ | 分析的是哪份不可变源码？ | run input 与安全 source reader | [已验证源码清单](docs/analysis-steps/01-verified-source-inventory.md) |
| steps/02-application-discovery/ | 应用入口在哪里？ | 全部业务分析入口分母 | [应用发现](docs/analysis-steps/02-application-discovery.md) |
| steps/03-program-graphs/ | 结构、调用、条件、数据和来源怎样连接？ | 连贯材料与过程召回线索 | [程序图](docs/analysis-steps/03-program-graphs.md) |
| steps/04-proven-code-facts/ | 哪些技术事实可严格重验？ | 材料技术观察与可选 Proof 查看 | [已证明代码事实](docs/analysis-steps/04-proven-code-facts.md) |
| steps/05-business-flows/ | 每个入口有哪些技术路径？ | 优先材料路径与 noFlow Gap | [业务 Flow 技术切片](docs/analysis-steps/05-business-flows.md) |
| steps/06-flow-interpretation/ | 每个入口整体支持什么业务活动？ | 材料 + 已审活动 | [流程解释](docs/analysis-steps/06-flow-interpretation.md) |
| steps/07-repository-knowledge/ | 多个活动怎样组成过程和仓库知识？ | 已审过程 + 完整知识 | [仓库知识](docs/analysis-steps/07-repository-knowledge.md) |
| steps/08-nine-section-document/ | 怎样形成可读九章？ | 已审九章 JSON + Markdown + refs | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

数字前缀只用于文档和运行目录排序。Java package、类型、字段、schema、artifact ID 和命令使用语义名称。

## 简单业务检查点

新路线保存：

- Step 06：business-materials.jsonl、activity-explanations.jsonl、activity-coverage.json；
- Step 07：business-processes.jsonl、repository-business-knowledge.json、process-coverage.json；
- Step 08：business-report.json、source-refs.jsonl、document.md、report-validation.json。

材料在首次模型调用前保存；每个完成 review 的活动/过程立即保存，后续失败不删除。进程内可直接传 typed object，不要求每个内部动作 fresh-reopen。一个简单 inputFingerprint 覆盖实际内容输入、实际 Prompt、有效模型/输出配置和 Module 版本，避免错用缓存。

固定 52 项总数、旧 57 项、Step 06 九 payload、逐业务原子 Proof/owner/accounting、bridge/reconciliation ledger、全记录状态机和三个 public adapter 同时完成都不是新的业务质量门。Step 01–05 已有技术产物不删除、不降级。

## 全仓与模型调用

- 每个发现入口必须为 ANALYZED、ANALYZED_WITH_GAPS 或 NOT_ANALYZED + reason。
- Flow 是入口根技术切片，不是最小业务过程；有材料无 Flow 仍可解释并保留 technical Gap。
- 同名/异名不自动 merge；一个活动可属于多个过程。
- 大仓使用完整活动 → 有界重叠 group → 已审过程摘要 → 有界仓库总整理。任何未纳入 group/summary 的内容都显式标 PARTIAL。
- 每个 Activity、Process group、repository summary、report 最多一轮 DRAFT + 一次 REVIEW；preflight 失败为 0 calls。
- started 后失败不 retry、不换 Provider、不用 API key fallback。
- 内部调用共同形成一份 Reader Candidate；产品候选最多仍为 Round 1 和另行授权的 Round 2。
- inspect/validate/render 为 0 model；编辑正文是新的显式生成动作。

真实质量验证顺序是一个小型核心包、第二领域小包、整仓。未取得当次授权前不调用 Luna；工期等待真实样本测量，不承诺 24 小时。

## 九章与来源

最终 document.md 恰好包含：

1. 文档说明
2. 业务目标
3. 业务对象
4. 业务活动
5. 字段与维度
6. 对象关系
7. 指标口径
8. 示例问题
9. 待确认事项

模型写 paragraph/list-item JSON，程序写 H1/H2、列表与 ref 标记。默认在第 1 章内加入可折叠技术依据，给每个 S1 链接提供同文档 anchor、file、lines 和原始 snippet；它不增加第 10 个 H2，也不依赖尚未实现的 HTTP/前端 viewer。

第 7 章只使用已审知识中的实际公式/口径；没有就说明未从源码识别到可定义指标。Java 不写中文蕴含判定器；整篇 Luna REVIEW 和真实小样本人工审阅负责语义质量。

## 目标命名

- 目录：backend-agents/sources/source-code/
- Maven：org.sourceanalysis:source-code-analysis-agent
- 显示名：Source Code Analysis Agent
- Java public root：org.sourceanalysis.app
- 八个分析包：analysis.inventory、analysis.discovery、analysis.graph、analysis.fact、analysis.flow、analysis.interpretation、analysis.knowledge、analysis.document（完整前缀均为 org.sourceanalysis.app）
- 横切包：capture.localgit、artifact、evidence、runtime、validation、adapter.cli、adapter.http、adapter.provider

不创建 common、shared、misc、utils、POC target 或第二 public Interface。旧 sources/github-code 和 pre-reset namespace 只存在于历史。

## 当前实现审计

| 能力 | 当前状态 | 诚实边界 |
| --- | --- | --- |
| 工程身份/目录 | 已实现（结构） | Maven/Java root 已完成 Wire Reset |
| Step 01–05 | 部分实现（受控纵切） | 保留现有 inventory/discovery/五图/Fact/Proof/Flow/Capsule publication；不是完整 jshERP 当前 run |
| Step 06 | 已实现核心纵切；自动语义质量尚未通过 | BusinessMaterialBuilder 可优先消费 Flow/Capsule，也可从匹配的源码盘点与入口发现直接产生带限制的材料；它将已选 Java 方法中的输入、状态/持久化形态调用、条件、后续调用和终止路径写成中性技术观察。长方法保留开头，并在已有引用额度内补充后续调用；中段的 `set...`、`update...` 等技术动作也可作为短观察保留，避免只读取前 24 行时丢掉后续状态变化；它仍不命名业务。当前固定 jshERP 的 337 个入口均已生成材料，且 DepotHead 自动包已人工检查到状态更新、库存更新和日志调用。ActivityExplainer、scripted DRAFT/REVIEW 及简单检查点已可用。两个固定 jshERP 小包（DepotHead 与 AccountHead）曾各完成一次真实 Luna/high DRAFT+REVIEW，得到可读且保留未知项的局部活动；它们使用人工审阅的技术观察，只验证 Prompt/Provider/活动结构。一次使用当前自动 DepotHead 包的受限 Luna 调用在 DRAFT 前的 Codex 子进程失败，未得到结构化响应，故自动材料和整仓调用尚未验证。 |
| Step 07 | 已实现核心纵切 | ProcessExplainer 以已审活动加已保存材料做宽松候选分组：共同业务词、局部 Flow、技术证据或冻结源码文件都只能让活动一起交给模型阅读，不能由 Java 宣布业务关系；过程 DRAFT/REVIEW、可选一次仓库总整理与三项检查点已可用。整仓预算与真实 Luna/high 尚未验证。 |
| Step 08 | 已实现核心纵切 | BusinessReportPublisher 具备九章 DRAFT/REVIEW、同文档 short-ref anchors 和四项检查点；public `render`可重新打开并核对已保存报告 |
| public Java/CLI/HTTP | 部分实现 | Java 已支持创建、零模型材料预检、最终文档执行、检查完成 output、provider-free render 与预算内业务输出查询。预检只运行 Step01/02 并产生 direct-entry 材料；最终文档运行会继续执行 Step01–05，并将持久化 Flow/Capsule 交给业务模块。最小 CLI 已支持 `capture-local-git`、从已登记 `sourceRegistrationId` 排队、按 run 执行 `plan-materials` 或最终目标和检查。capture 是唯一接受本地路径的命令，只能冻结配置身份下的完整 commit；分析请求仍只使用 registration ID。预检 run 只允许读取业务材料，不能渲染或读取报告；render/artifact 命令已映射但需要后续配置 bootstrap 提供只读依赖；HTTP 尚未完成 |
| 真实 Luna/high | 人工小包已验证；自动小包调用失败 | 两个独立人工审阅的小包完成 DRAFT+REVIEW；当前自动 DepotHead 小包的唯一 DRAFT 在 Codex 子进程返回结构化输出前失败，按规则未重试。它既不证明材料差，也不证明 Provider/Prompt 已通过。 |
| 固定 jshERP | 材料规划已运行；业务验收尚未运行 | 337 个已发现 HTTP 入口已各自产生一份零模型 direct-entry 材料，且当前 DepotHead 自动包已检查到请求、状态条件、状态更新、库存更新和日志调用。没有自动 Builder 产生的 DepotHead Activity/Process/九章结果，更没有整仓业务结果。 |

DepotHead 八文件只用于既有 bounded walkthrough 说明，不是 repository completion。合成“补货 → 收货 → 应付账单”永远不是 jshERP 行为。

## 技术参考

- [基础合同](docs/references/foundation-and-publication-contracts.md)
- [Canonical 身份合同](docs/references/canonical-persistence-identity-contracts.md)
- [既有公共/模块合同](docs/references/inherited-public-and-module-contracts.md)
- [中文 Prompt](docs/references/semantic-interpretation-prompts.md)
- [运行恢复延期说明](docs/supplements/runtime-recovery-todo.md)
- [共享 NineSectionProfile](../../../shared/source-agent-contracts/README.md)

两份 persistence/module reference 中的逐 Module receipt、fresh reopen 和固定旧 registry 只约束已保留的技术 Step 01–05 或当前实现事实；Step 06–08 新目标以当前设计为准。

## 本地 Java 格式与构建检查

在本目录使用项目跟踪的 JDK 17 Toolchain，并保持离线：

~~~bash
mvn -o -t .mvn/toolchains.xml spotless:check
mvn -o -t .mvn/toolchains.xml spotless:apply
mvn -o -t .mvn/toolchains.xml spotless:check
mvn -o -t .mvn/toolchains.xml -DskipUTs=true package
~~~

skipUTs=true 会跳过测试执行；package 成功不等于测试通过。只运行当前 work unit 直接覆盖的定向 selector，除非用户明确要求全套。

文档-only 工作不运行 Maven、模型、客户代码、来源 capture 或网络调用。
