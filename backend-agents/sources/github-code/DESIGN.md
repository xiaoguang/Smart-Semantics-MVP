# GitHub Code Agent 总体设计

## 1. 五分钟看懂目标

### 1.1 输入是什么，输出是什么

输入不是一个会继续变化的分支，而是一个身份明确、字节不可变的代码仓库快照，以及本次允许理解的语言、框架和构造范围。输出也不是已发布文档，而是一个待审阅的 Candidate：它包含固定九章的 Markdown、可回到冻结源码的 Trace、验证回执和不可变身份。

本 Agent 只负责从冻结代码形成未发布候选。Selection、冻结、package、发布和浏览器中的多来源审阅属于下游流程，见[数据标准化审阅体验](../../../docs/design/data-standardization-review-experience.md)。

### 1.2 为什么不能把整个仓库直接交给大模型

大模型擅长把局部技术行为解释成读者能理解的业务语言，但它不是编译器、哈希校验器或证明系统。把整个仓库一次性交给模型会混在一起处理入口、无关工具类、重复实现和不支持的动态行为；上下文再大，也不能保证调用绑定唯一、SQL 映射正确、每个分支都被覆盖，或一段描述真的来自所指行号。

目标设计先由程序把仓库缩成可验证的小事实和业务流程，再让模型只看一个 FlowSlice 的最小 EvidenceCapsule。这样，模型不能扩大范围、补造源码或用流畅文字遮住缺证。

### 1.3 程序做什么，模型做什么

| 责任 | 程序 | 模型 |
| --- | --- | --- |
| 冻结来源、路径、字节、哈希和 locator | 唯一负责 | 不参与 |
| 识别能力范围、解析代码/SQL、绑定调用和条件 | 唯一负责 | 不猜测 |
| 形成 CodeFact、Proof、Gap 和 FlowSlice | 唯一负责 | 只能引用 |
| 编译 EvidenceCapsule | 唯一负责 | 不能添加材料 |
| 给局部对象、活动和关系提出业务化解释 | 校验、准入、收窄或拒绝 | 可在 allowlist 内提案 |
| 组装九章、处置语义原子、渲染 Markdown | 唯一负责 | 不选章节、不写 Markdown |
| Candidate 身份、Trace、归档和恢复 | 唯一负责 | 不创建 ID 或 locator |

### 1.4 怎样保证准确，Gap 又是什么

准确不是“模型看起来有把握”，也不是“文件哈希正确”。一个确定陈述必须先成为 CodeFact；CodeFact 的每个语义原子——类型、属性、条件、字面值和关系——都必须由它自己引用的源码字节与 Proof 支持。只知道 Evidence ID 存在，或摘录哈希匹配，只能证明“这段字节没有变”，不能证明“这段字节表达了所声明的意思”。

无法唯一证明的内容不补写，而是成为 Gap。可披露的 Gap 可以进入九章的待确认事项；来源漂移、Proof 不闭合、语义原子丢失等致命问题则在 Candidate 形成前停止。宁可少写并明确缺口，也不把推测包装成事实。

### 1.5 唯一目标架构主线

~~~mermaid
flowchart LR
    Caller[调用方] --> A11[ARCH-11 公共 Interface]
    A11 --> Repo[冻结仓库]
    Repo --> A01[ARCH-01 VerifiedSnapshot]
    A01 --> A02[ARCH-02 能力识别]
    A02 --> A03[ARCH-03 代码与 SQL 分析]
    A03 --> A04[ARCH-04 CodeFact + Proof + Gap]
    A04 --> A05[ARCH-05 FlowSlice]
    A05 --> A06[ARCH-06 EvidenceCapsule]
    A06 --> A07[ARCH-07 FlowInterpretationCandidate]
    A07 --> A08[ARCH-08 程序准入与仓库知识组装]
    A08 --> A09[ARCH-09 NineSectionPlan]
    A09 --> A10[ARCH-10 Candidate]
    A10 --> Archive[Markdown + Trace 不可变归档]
    A12[ARCH-12 安全与不执行客户代码] -.约束整条链.-> A11
    A12 -.约束整条链.-> Archive
~~~

这是一条端到端主线，没有另一条“较弱但仍可发布”的旁路。局部解释缺失可以形成 Gap；事实证明失败不能绕过准入。

## 2. ARCH-01..ARCH-12 模块合同

每个 ARCH 都按同一组读者问题描述。模块名表达长期目标边界，不表示当前代码已经实现；当前成熟度只在本文最后一张矩阵中出现。

### ARCH-01 冻结仓库验证

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-01 / FrozenInputModule |
| 读者问题 | 我们分析的究竟是哪一份仓库，稍后还能读到完全相同的字节吗？ |
| 输入 | 显式来源身份、固定 commit 或等价不可变版本、仓库根、文件选择规则和 VerificationPolicy。 |
| 确定性工作 | 规范化路径；拒绝越界与不允许的 symlink；校验文件大小、SHA-256、字符集和 locator 可读性；把来源身份与文件字节共同纳入版本化身份。 |
| 模型角色 | 无。模型不得选择、刷新或验证来源。 |
| 输出 | VerifiedSnapshot：冻结来源身份、已验证文件表、摘要和验证回执。 |
| 失败行为 | 缺失、漂移、路径逃逸、身份不完整或无法执行策略时 fail closed；不回退到 live branch、工作树或相似文件。 |
| 给下一模块的保证 | ARCH-02 只接触身份明确且可重复读取的字节。 |

### ARCH-02 能力与范围识别

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-02 / CapabilityScopeModule |
| 读者问题 | 这次分析真正理解哪些语言、框架和构造，哪些只能登记为未知？ |
| 输入 | VerifiedSnapshot、版本化 CapabilityProfile、预算和排除规则。 |
| 确定性工作 | 对每个文件识别语言、构建表面、框架特征和支持级别；区分“能读取文件”与“能证明语义”；为不支持、超预算或无法分类的范围建立 Gap。 |
| 模型角色 | 无。模型不能把文件名或惯例升级成受支持能力。 |
| 输出 | 与 VerifiedSnapshot 绑定的能力范围台账，以及范围 Gap。 |
| 失败行为 | Profile 缺失或无法绑定来源时停止；局部不支持构造被隔离并记录 Gap，不伪装成已覆盖。 |
| 给下一模块的保证 | ARCH-03 只在声明可理解的范围内产出确定分析，其余范围有明确边界。 |

### ARCH-03 代码与 SQL 分析

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-03 / RepositoryAnalysisModule |
| 读者问题 | 入口、调用、条件、状态变化和 SQL 映射能否从源码中唯一解析出来？ |
| 输入 | VerifiedSnapshot、能力范围台账、语言与框架 Adapter 规则。 |
| 确定性工作 | 解析语法、符号、直接调用、控制条件、数据关系、配置绑定和静态 SQL；每个观察结果保留精确源码位置与解析规则版本。 |
| 模型角色 | 无。模型不能补全调用图、动态绑定、SQL 或运行结果。 |
| 输出 | 带 locator 的 RepositoryAnalysis，包括唯一解析结果、歧义和分析 Gap；此时的观察结果尚不是已准入 CodeFact。 |
| 失败行为 | 解析失败、绑定多解、动态 SQL、反射、AOP、SpEL 或运行时注册成为结构化 Gap；解析器不执行客户代码。 |
| 给下一模块的保证 | ARCH-04 收到的每个可证明观察都有稳定位置和确定规则来源，歧义不会被静默选边。 |

### ARCH-04 可验证事实、证明与缺口

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-04 / FactProofModule |
| 读者问题 | 每个代码声明到底由哪些字节和哪条可重验规则证明？ |
| 输入 | RepositoryAnalysis、VerifiedSnapshot 中的源码字节、locator 和范围 Gap。 |
| 确定性工作 | 把可证明观察规范化为 CodeFact；列出全部语义原子；为每个原子构造 Proof DAG；确认 Fact 自己引用的 Evidence span 足以支持类型、属性、条件、值与关系。 |
| 模型角色 | 无。模型置信度、解释一致性和名称流畅度都不能替代 Proof。 |
| 输出 | 不可变 CodeFact、Proof 和 Gap 集合，以及逐原子准入回执。 |
| 失败行为 | 声明字节存在但语义不闭合时拒绝该 CodeFact；能够继续分析的未知内容变成 Gap，不能借用未被该 Fact 引用的其他 Evidence。 |
| 给下一模块的保证 | ARCH-05 看见的确定事实都已逐原子闭合；未闭合内容只能以 Gap 身份存在。 |

### ARCH-05 业务流程切分

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-05 / FlowCompilationModule |
| 读者问题 | 哪些事实共同构成一条从入口到结果的业务流程，哪里存在分支或未覆盖终点？ |
| 输入 | 已准入 CodeFact、Proof、Gap，以及版本化入口、分支、终端和副作用规则。 |
| 确定性工作 | 按入口、条件分支、终端、副作用和结果确定性编译 FlowSlice；计算事实归属与覆盖台账；稳定排序并保留跨 Flow 引用。 |
| 模型角色 | 无。模型不发现、合并、扩大或切分 Flow。 |
| 输出 | 一个或多个 FlowSlice、覆盖台账和流程 Gap。 |
| 失败行为 | 入口或终点无法唯一闭合时不臆造完整流程；保留可证明的局部切片并登记缺失边界，致命覆盖断裂则阻止该切片继续。 |
| 给下一模块的保证 | ARCH-06 的每个切片都有明确范围、事实闭包、分支边界和稳定身份。 |

### ARCH-06 流程证据包与 Trace 基础

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-06 / EvidenceTraceModule |
| 读者问题 | 要解释这一条 FlowSlice，最少需要哪些事实、证明和源码片段？ |
| 输入 | FlowSlice、其 CodeFact/Proof/Gap 闭包和 VerifiedSnapshot。 |
| 确定性工作 | 编译最小 EvidenceCapsule；冻结 Fact/Evidence allowlist、精确 locator、摘录摘要、规则版本和任务身份；排除无关仓库内容。 |
| 模型角色 | 无。模型不能挑选证据或创建 locator。 |
| 输出 | 每个 FlowSlice 一个 EvidenceCapsule，以及可供最终 Trace 使用的来源链。 |
| 失败行为 | span 缺失、摘要漂移、allowlist 不闭合或 Proof 依赖缺失时，在模型调用前停止该切片。 |
| 给下一模块的保证 | ARCH-07 只看到一条已证明流程所需的最小冻结材料，不能访问全仓或扩大边界。 |

### ARCH-07 单流程模型解释

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-07 / FlowInterpretationModule |
| 读者问题 | 在不改变源码事实的前提下，怎样把局部技术行为解释成清楚的业务语言？ |
| 输入 | EvidenceCapsule、严格输出 Schema、prompt 版本和冻结运行策略。 |
| 确定性工作 | 构造任务；分别核验 configuredAdapterId、configuredAuthMode、observedUpstreamProvider、observedModel、observedReasoningEffort 和 observedSandbox；执行 Schema、身份、allowlist 与 basis 收窄门禁。 |
| 模型角色 | 对局部对象名、活动名和关系含义提出 FlowInterpretationCandidate；同一 FlowInterpretationRound 的 R2 只复核 R1，不得增加 Fact、Evidence、Flow 或 locator。 |
| 输出 | FlowInterpretationCandidate、运行回执和解释 Gap；它仍是待程序准入的提案。 |
| 失败行为 | 空响应、非法 Schema、身份不匹配、basis 扩张或越界引用时保留失败回执，不自动重试、不切换 Provider；缺少业务解释可记录 Gap，但不能反向削弱 CodeFact。 |
| 给下一模块的保证 | ARCH-08 收到的解释只能引用 Capsule 内已验证材料，且模型运行身份可独立核对。 |

### ARCH-08 程序准入与仓库知识组装

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-08 / KnowledgeAdmissionModule |
| 读者问题 | 哪些模型解释可以使用，多个 FlowSlice 的事实又怎样合成一个不冲突的仓库知识视图？ |
| 输入 | CodeFact、Proof、Gap、FlowSlice、EvidenceCapsule 和 FlowInterpretationCandidate。 |
| 确定性工作 | 逐项核对解释 basis、身份、范围和冲突；执行 KEEP、NARROW、DROP 或 NEEDS_EVIDENCE 等准入决定；合并跨 Flow 的对象、活动与关系，建立唯一 owner 和引用。 |
| 模型角色 | 无新增角色。模型提案只能被准入、收窄或拒绝，不能自我批准。 |
| 输出 | AdmittedRepositoryKnowledge：已证明事实、已准入解释、显式 Gap、冲突和语义原子清单。 |
| 失败行为 | 无证解释和冲突性解释被拒绝或转为 Gap；事实 Proof 失败是致命错误，不能由解释替代。 |
| 给下一模块的保证 | ARCH-09 只处理来源闭合、类型明确、owner 唯一的仓库知识。 |

### ARCH-09 九章计划

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-09 / NineSectionAssemblyModule |
| 读者问题 | 已准入知识怎样完整、无重复地进入固定九章？ |
| 输入 | AdmittedRepositoryKnowledge 与共享 NineSectionProfile。 |
| 确定性工作 | 为每个 Document Item 指定唯一章节 owner；交叉章节只做引用；把每个语义原子处置为正文、技术依据、Gap 或带 reason code 的有理由排除；验证九章基数与顺序。 |
| 模型角色 | 无。模型不选择章节、不排序、不处置语义原子。 |
| 输出 | NineSectionPlan，以及原子覆盖和读者问题可回答性回执。 |
| 失败行为 | 少章、多章、owner 冲突、未处置原子、占位内容或静默信息丢失均为 fatal。 |
| 给下一模块的保证 | ARCH-10 得到的是完整且无二义的渲染计划，不需要再次推断事实或章节。 |

### ARCH-10 Candidate、身份与不可变归档

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-10 / CandidateStoreModule |
| 读者问题 | 怎样稳定生成可审阅 Markdown，并在重启后证明它来自同一份来源和计划？ |
| 输入 | NineSectionPlan、VerifiedSnapshot 身份、Evidence/Flow/任务 lineage 和版本化渲染规则。 |
| 确定性工作 | 用受控中文、固定顺序和安全转义渲染 document.md；计算内容身份与 Candidate 身份；写入 canonical UTF-8 JSON/JSONL sidecar；以不可变方式归档 Candidate、Trace 和验证 lineage。 |
| 模型角色 | 无。模型不渲染 Markdown，不写 ID、SHA、receipt 或 Trace。 |
| 输出 | 未发布 Candidate、document.md、Trace、验证回执和可恢复归档。 |
| 失败行为 | 读者门禁失败、身份冲突、归档不完整或来源重验失败时不产生可选 Candidate；已有 Candidate 不覆盖。Candidate ID 只标识内容与 lineage，不证明内容正确。 |
| 给下一模块的保证 | 不可变归档与下游 Selection 取得的是可重验、来源可追溯的同一候选。 |

### ARCH-11 公共 Interface 与 Adapter

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-11 / CodeToMarkdownAgent |
| 读者问题 | CLI、测试和未来 HTTP 怎样使用同一条主线，而不理解内部十二层细节？ |
| 输入 | 显式冻结请求、能力 Profile、存储位置和必要的模型运行策略。 |
| 确定性工作 | 通过一个小而稳定的 Interface 编排 ARCH-01..ARCH-10；让 CLI、未来 HTTP 和测试复用同一 core；只在有两种真实生产策略时建立来源、语言/框架、Provider 或存储 Adapter。 |
| 模型角色 | 只通过 ARCH-07 的受控 Adapter 参与；公共入口不提供任意 prompt 或全仓上下文入口。 |
| 输出 | Candidate reference、ValidationReceipt、TraceView 或结构化失败。 |
| 失败行为 | Adapter 能力不明、身份未冻结、恢复状态不闭合或请求越权时 fail closed；诊断输出不能冒充 Candidate。 |
| 给下一模块的保证 | ARCH-01 总能收到显式冻结请求；调用方不会因使用不同入口而绕过准入、渲染或归档合同。 |

### ARCH-12 安全与不执行客户代码

| 字段 | 合同 |
| --- | --- |
| ARCH 身份 | ARCH-12 / SafetyBoundaryModule |
| 读者问题 | 怎样确认分析过程只读源码，不运行客户项目，也不会被 XML、路径或模型输出带出边界？ |
| 输入 | 全部路径、源码、XML、SQL、配置、模型响应、归档和运行策略；这些一律视为不可信输入。 |
| 确定性工作 | 只读解析；禁止客户 Maven/Gradle、插件、测试、脚本、应用、注解处理器、SQL 和 MyBatis runtime；阻断外部 DTD、entity、schema 与网络解析；执行路径、资源和大小限制。 |
| 模型角色 | 只能在 ARCH-07 的冻结沙箱与最小 Capsule 内返回数据；不得获得客户代码执行权或隐式网络能力。 |
| 输出 | 每步安全准入结果、可披露安全 Gap 或致命失败码。 |
| 失败行为 | 无法设置或验证安全开关、路径越界、隐式联网、输入超限或执行请求一律停止，不降级为不受控解析。 |
| 给下一模块的保证 | 整条链和下游归档只包含通过同一安全边界读取的惰性字节与结构化结果。 |

## 3. POC 术语到目标术语

以下名称只描述阶段 00 的临时机制或测试例，不是另一套长期架构。

| POC 名称 | 临时作用 | 目标架构中的替代 |
| --- | --- | --- |
| Flow Manifest | 人工列出文件、Evidence、LockedFact 和一个或多个流程 allowlist，先验证端到端 plumbing。 | 程序从 VerifiedSnapshot、CodeFact/Proof/Gap 和 FlowSlice 编译出的冻结输入与 EvidenceCapsule。 |
| LockedFact | 人工声明、当前只做结构/hash/reference 校验的事实载荷。 | 每个语义原子都通过 Proof 的 CodeFact；无法证明的内容成为 Gap。 |
| provider-free baseline | 不调用模型的渲染/归档测试路径，用来隔离模型依赖。 | 不保留为目标架构旁路；单一主线对每个 FlowSlice 执行标准准入，解释缺失时如实登记 Gap。 |
| recorded R1/R2 | 用固定 JSON 响应测试 allowlist、proposal 完整性和 basis 收窄。 | 受冻结任务、Schema 和运行身份约束的 FlowInterpretationRound，输出 FlowInterpretationCandidate。 |
| eight-file archive | 固定为一份 document.md 与七个 JSON sidecar 的阶段性归档合同。 | 按 Candidate 与 Trace 合同进行内容寻址、不可变归档和追加 lineage；长期文件数量不由 POC 固定。 |

## 4. DepotHead 真实 POC 案例

### 4.1 冻结输入与结论

样例来自 jshERP 仓库 https://github.com/jishenghua/jshERP.git 的固定 commit 8c30ce7861570458920175e200bb2a6442713580。当前 POC Flow Manifest 声明 3 个文件、5 个 Evidence、5 个 LockedFact 和 1 个 Flow：

- DepotHeadController.java，42241 字节，SHA-256 为 1b45a173d5bd1a56888541da558db65c26c83e9758ed900bbada249dfd0bc55b；
- DepotHeadService.java，108586 字节，SHA-256 为 e15d22b6aa8282b3af2719e1faa827cf8ccd5f65346b440f19f83f40e5e4fa49；
- DepotHeadMapper.xml，26978 字节，SHA-256 为 eff37432ad2b5b4f62ea0faaea08a52be45ba2bc22071dadc4663bc58d645148。

文件哈希、五段摘录哈希和已知 ID 引用都通过。独立逐语义原子审计只通过 2 个 Fact，失败 3 个 Fact，所以整个 Flow 不闭合，准确度出口 NOT MET，不得接受任何 Candidate。

### 4.2 五个事实为何两过三败

| 声明事实 | 声明源码范围 | 独立语义审计 |
| --- | --- | --- |
| HTTP 入口 | Controller 178–191 | FAIL。范围内有 POST、/batchSetStatus、status、ids 和 Service 调用，但完整路径所需的类级 /depotHead 在 line 43，范围外。 |
| 反审核资格 | Service 750–776 | PASS。requested 0、current 1、purchase 0 和 BusinessRunTimeException 都在范围内。 |
| 审核与库存条件 | Service 779–811 | FAIL。requested 1 和库存条件在范围内；required current 0 与失败异常在 767–775，范围外。 |
| 状态持久化请求 | Service 813–839 | FAIL。所声明的 Mapper 调用、accepted IDs 选择和 status 赋值实际在 798–803；声明范围主要是日志与下一方法。 |
| 状态列映射 | Mapper 385–489 | PASS。updateByExampleSelective、jsh_depot_head、status 和 record.status 都在范围内。 |

哈希回答的是“这些行的字节有没有改变”，不是“这些行是否支持一句代码声明”。例如 Service 813–839 的摘要可以完全正确，但目标持久化调用位于 798–803；这只是精确地锁定了错误范围。另一个 Evidence 即使包含缺失字节，也不能替一个没有引用它的 Fact 补证。

### 4.3 按目标模块走一遍

| 目标模块 | 本例实际输入 | 程序实际输出或应有输出 | 模型角色 | 失败或 Gap 行为 |
| --- | --- | --- | --- | --- |
| ARCH-01 | 固定 commit、3 个声明文件、5 个 locator | 当前 POC 完整性校验为 HASH/REFERENCE PASS；尚没有完整目标 VerifiedSnapshot 合同。 | 无 | 没有完整性失败；这一步不能宣告语义正确。 |
| ARCH-02 | 人工限定的 Java/Spring MVC/MyBatis 小样 | 当前候选未绑定逐文件能力台账；WALKING_SLICE_V0 只是邻接诊断 Profile。 | 无 | 未覆盖构造应成为范围 Gap，不能由文件可读性代替。 |
| ARCH-03 | Controller、Service、Mapper XML | 当前 POC 不从源码自动生成可准入分析，只读取人工声明；邻接 discovery/analysis 未接 Candidate。 | 无 | 自动绑定、控制流和 SQL 分析缺失，不能把声明当分析结果。 |
| ARCH-04 | 5 个 LockedFact 与各自声明 Evidence | 独立审计得到 2 PASS、3 FAIL；没有一组可整体准入的 CodeFact/Proof。 | 无 | 三个语义闭包失败是 Candidate 前的致命错误。 |
| ARCH-05 | 人工声明的 1 个 Flow，包含 5 Fact/5 Evidence allowlist | 当前只有手工流程身份 flow:depot-head-batch-status；没有程序编译的 FlowSlice 或覆盖证明。 | 无 | 整个 Flow 因 3 个 Fact 不闭合而停止；不得只摘取流畅片段继续。 |
| ARCH-06 | 5 个 locator、Fact/Evidence allowlist | POC 可算 capsuleId 并核对摘要，但不能形成语义闭合的目标 EvidenceCapsule。 | 无 | 在调用模型前失败；正确摘要不能补足错误 span。 |
| ARCH-07 | 目标上应接收已准入 EvidenceCapsule，本例没有 | 本例不应产生 FlowInterpretationCandidate。recorded R1/R2 仅是 POC 测试机制。 | 不得为本例补证或生成可接受解释。 | 保留失败/Gap，不自动重试；解释一致也不能升级失败 Fact。 |
| ARCH-08 | 2 个可支持事实、3 个失败事实、无可准入解释 | 不得形成完整 AdmittedRepositoryKnowledge。 | 无新增角色 | 事实 Proof 失败不能降级成模型提案；仓库知识组装停止。 |
| ARCH-09 | 没有完整的已准入仓库知识 | 不得形成可渲染 NineSectionPlan。 | 无 | 九个标题或预设段落不能替代原子闭包。 |
| ARCH-10 | 没有通过门禁的 NineSectionPlan | 不得接受 Candidate。历史空洞 baseline document 只说明旧渲染行为，不是目标证据，也不是当前验收。 | 无 | Candidate ID 即使稳定，也只标识一组字节；它不证明内容正确。 |
| ARCH-11 | 当前 Java Interface/CLI 可以进入 POC 执行路径 | 符合目标的 Interface 应返回语义准入失败，而不是提供可选择候选。 | 只能经 ARCH-07 | 当前 core 缺失 semantic closure gate 是实现缺口。 |
| ARCH-12 | 只读冻结文件、Manifest 和审计记录 | 本次核对没有运行客户 Maven、测试、脚本、应用、SQL 或 MyBatis runtime。 | 未调用模型 | 安全边界通过不等于准确度通过；两者是独立门禁。 |

本案例的正确产物是拒绝结论和明确 Gap，而不是九章正文。只有另行修正 Evidence 范围、重新冻结并让五个 Fact 逐原子通过 Proof 后，流程才可继续。

## 5. 跨模块稳定合同

### 5.1 九章与读者内容

章节合同只引用共享 [NineSectionProfile](../../../shared/source-agent-contracts/README.md)。每个 Candidate 的正文必须按以下顺序恰好包含一次九个 H2，不得改名、调序或增加第十章：

~~~text
## 文档说明
## 业务目标
## 业务对象
## 业务活动
## 字段与维度
## 对象关系
## 指标口径
## 示例问题
## 待确认事项
~~~

CodeFact 的 kind、全部 attributes、条件、字面值和关系都必须进入正文、技术依据、明确 Gap 或带稳定 reason code 的有理由排除。结构正确、Markdown SHA 正确、Trace 可查、引用闭合、Proof 闭合、事实覆盖和读者信息密度是不同门禁；字数、行数和文件大小不能替代任何一个。

### 5.2 身份与 Trace

目标至少区分 VerifiedSnapshot、Evidence、CodeFact/Proof、FlowSlice、EvidenceCapsule、模型任务、Candidate content、Candidate series、运行回执和验证回执。算法与 canonicalization 必须版本化；改变 identity-bearing 字段就必须产生新身份。

Trace 是从文档项回到 frozen source locator 的链，不是 Proof。目标 validate/trace 必须能重开所绑定的 VerifiedSnapshot，重算文件与摘录摘要，并验证 Fact/Proof/Item 关系；只读 sidecar 不能冒充源码重验。

Candidate 一经封存不可覆盖。重复同内容可以幂等，内容冲突必须拒绝；验证、runtime admission、修复和 lineage 采用追加记录。Candidate 是未发布提案，只有显式 Selection 才能进入后续冻结。

### 5.3 轮次与模型身份

FlowInterpretationRound 是同一 reader candidate 内、同一 FlowSlice 的 R1 解释与 R2 精度复核；R2 不能扩大 R1 的事实或 Evidence basis。ReaderCandidateRound 才表示产品候选替换，最多 Round 1 初始候选和一次 Round 2 replacement。

configured Adapter、configured Auth Mode 与 observed upstream provider 必须分字段记录并分别验证。observed model、reasoning effort、sandbox 或任何必需身份缺失或不匹配时，在响应进入仓库知识前 fail closed。模型失败不授权自动重试、换 Provider 或 API-key fallback。

### 5.4 失败分类

| 类型 | 例子 | 处理 |
| --- | --- | --- |
| 致命完整性/事实错误 | 来源漂移、路径越界、Proof 不闭合、未知引用、身份混用 | 停止受影响流程，不产生可选 Candidate。 |
| 可披露分析 Gap | 不支持构造、歧义绑定、动态行为、运行结果未知 | 保留准确边界，可在九章待确认事项中呈现。 |
| 模型候选失败 | 非法响应、越界引用、basis 扩张、运行身份不匹配 | 保存回执，不自动重试或切换 Provider。 |
| 读者质量失败 | 原子无处置、占位文本、章节 owner 冲突 | Candidate 不得进入 Selection。 |

## 6. 当前成熟度矩阵

状态按完整目标模块衡量：PARTIAL 表示存在经过验证的局部行为或 seam，但没有达到该 ARCH 的端到端保证；POC_ONLY 表示只有临时人工机制；NOT_IMPLEMENTED 表示尚无对应目标产物。详细当前事实统一由[00 POC 实现记录](docs/stages/00-mvp.md)维护。

| 架构模块 | 当前状态 | 已验证内容 | 缺失内容 | 详细阶段文档 |
| --- | --- | --- | --- | --- |
| ARCH-01 冻结仓库验证 | PARTIAL | 声明文件 size/SHA、Evidence 行段摘要和已知引用可重验；漂移在 provider 前失败。 | 完整 VerifiedSnapshot、统一 Java/CLI symlink policy、祖先链接规则、exact-field Schema、完整 origin identity。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-02 能力与范围识别 | PARTIAL | CLI 暴露 WALKING_SLICE_V0；目录诊断能报告部分文件/route/gap。 | 与 Candidate 绑定的 CapabilityProfile 和逐文件范围台账。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-03 代码与 SQL 分析 | PARTIAL | RepositoryDiscoverer 与 SourceAnalyzer 可诊断部分 Spring MVC、直接调用、MyBatis 绑定、静态 UPDATE 和 Java 条件。 | Symbol Solver/完整控制流与数据流、冻结输入绑定、自动 Candidate 接线。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-04 CodeFact/Proof/Gap | PARTIAL | 有 CodeFact、ConditionFact、Gap seam；POC 可解析 LockedFact。 | 完整 Proof、逐原子 semantic Evidence closure、Fact 自动准入；DepotHead 当前 3/5 失败。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-05 业务流程切分 | POC_ONLY | 可读取人工 Flow Manifest 中的 Flow 与 allowlist。 | 自动入口到终端 FlowSlice 编译、多 Flow 覆盖闭包。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-06 EvidenceCapsule 与 Trace 基础 | PARTIAL | 生成前重验摘录，计算 taskSpecId/capsuleId，归档 Evidence/locator 与 Trace index。 | 语义闭合 Capsule、归档后重开源码与摘要重验、完整 Fact/Proof/Item closure。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-07 单流程模型解释 | PARTIAL | scripted/recorded R1/R2 allowlist、proposal 完整性和 basis 收窄有直接测试；runtime receipt 有独立 seam。 | live Adapter、生成链接线、完整 Schema、Adapter/Auth/upstream provider 分离。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-08 程序准入与仓库知识 | NOT_IMPLEMENTED | 仅有局部 R1/R2 admission 结果供当前 renderer 使用。 | 统一解释准入、跨 Flow 知识组装、owner/冲突与语义原子台账。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-09 NineSectionPlan | PARTIAL | renderer 可确定性输出精确九个 H2。 | 通用 NineSectionPlan、章内知识类型、原子守恒和 reader gate。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-10 Candidate 与不可变归档 | PARTIAL | Candidate identity、document.md 加七个 JSON sidecar、原子安装、逐字节幂等、冲突拒绝和 fresh-process validate/trace。 | 完整 sidecar/源码/语义重验、追加 validation/runtime lineage、长期身份覆盖。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-11 公共 Interface 与 Adapter | PARTIAL | Java Interface 有 generate、generateBaseline、validate、trace；CLI 有六个离线命令。 | 单一目标请求合同、HTTP、远程 Git、完整 Provider/存储 Adapter 和诊断到准入 seam。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-12 安全与不执行客户代码 | PARTIAL | 当前模块无客户执行路径；MyBatis parser 禁用外部 DTD/entity/schema/network；walker/archive 有局部 symlink 防护。 | 统一路径/symlink 安全合同、live model sandbox 与更广 Adapter 的端到端验证。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
