# Source-to-Standard-Markdown 共同行文语境

本文提供所有 Source Agent 共用的读者语言。中文名称放在前面，精确英文类型名放在括号中。这里定义的是跨来源概念，不描述某个来源的 parser、命令、文件布局或当前成熟度。

## 核心对象

**来源 Agent（Source Agent）**

独立负责一种来源从冻结输入到未发布候选的过程。每个 Agent 只拥有自己的来源规则；它不能替其他来源作事实判断，也不负责发布。

**冻结来源输入（Frozen Source Input）**

调用方明确选定、不可静默刷新或替换的来源身份与材料。它说明“准备验证什么”，本身还不等于验证已经通过。

**已验证快照（VerifiedSnapshot）**

程序已经核对来源身份、路径、字节和摘要后的不可变快照。后续事实、流程、候选和 Trace 都必须绑定它；无法重复读取同一字节时应停止。

**证据（Evidence）**

VerifiedSnapshot 中一段边界明确的原始材料及其 locator 和摘要。Evidence 说明“可以去哪里查看哪些字节”，不自动证明对这些字节的解释正确。

**代码事实（CodeFact）**

程序从来源中提取并准备准入的一条确定陈述。它由类型、属性、条件、字面值和关系等语义原子组成；每个原子都必须有自己的 Evidence 和 Proof。

**证明（Proof）**

把 CodeFact 的语义原子与 Evidence 字节、确定性规则和中间推导连接起来的可重验理由。Proof 回答“为什么这段来源足以支持这句话”。

**缺口（Gap）**

系统明确知道但无法唯一证明、无法支持或不应推断的内容。Gap 不是占位文字；它必须说明缺什么、影响什么，以及需要怎样的来源或责任方才能关闭。可披露 Gap 可以进入候选，致命证据缺口会阻止候选形成。

**流程切片（FlowSlice）**

程序按入口、条件分支、终点和副作用切出的最小业务流程单元。FlowSlice 的边界由已证明事实决定，不由模型自由扩大、合并或发现。

**流程证据包（EvidenceCapsule）**

只包含解释一个 FlowSlice 所需 CodeFact、Proof、Gap、Evidence 和 allowlist 的最小冻结包。它不是整个仓库的上下文转储。

**流程解释候选（FlowInterpretationCandidate）**

模型针对一个 EvidenceCapsule 提出的局部业务名称、活动或关系解释。它只能引用包内材料，并且必须经程序准入；它不是 CodeFact，也不是最终文档。

**已准入仓库知识（AdmittedRepositoryKnowledge）**

程序合并所有已证明事实、已准入解释、显式 Gap 和冲突后形成的仓库级知识视图。它为每个知识项保留 owner、来源与语义原子，不允许模型提案自我批准。

**九章计划（NineSectionPlan）**

程序把已准入仓库知识放入固定九章前形成的结构化计划。每个知识项有唯一章节 owner，每个语义原子都必须进入正文、技术依据、Gap 或有理由排除。

**九章合同（NineSectionProfile）**

语言无关的共同合同，固定九个章节的名称、顺序和“每章恰好一次”要求。来源 Agent 可以有不同章内知识类型，但不能另建章节列表。

**候选（Candidate）**

尚未发布、可供审阅的一组内容与 lineage。它包含按 NineSectionPlan 确定性渲染的 Markdown 和必要的证据/验证材料。Candidate 不是最终文档，也不因拥有 ID 就自动正确。

**追溯链（Trace）**

从候选中的知识项回到 Evidence locator 和 VerifiedSnapshot 的可查询链。Trace 帮助读者找到来源；它必须与 Proof 配合，不能独自完成事实准入。

**语义原子（Semantic Atom）**

一个最小的意义单位，例如事实类型、属性、条件、字面值或关系。每个已准入原子必须在读者内容、技术依据、明确 Gap 或有理由排除中有处置结果。

**流程解释轮（FlowInterpretationRound）**

同一个 Candidate 内、同一个 FlowSlice 的 R1 解释和 R2 精度复核。R2 只能检查或收窄 R1，不能增加来源事实，也不产生第二份产品候选。

**读者候选轮（ReaderCandidateRound）**

针对同一冻结来源材料的产品候选轮次。Round 1 产生初始 Candidate，Round 2 是针对已命名问题的唯一替换；最多两份产品候选。

**选择（Selection）**

人明确选择一个不可变 Candidate 进入后续冻结的动作。没有自动“最新候选”或默认胜者。

**冻结来源文档（Frozen Source Document）**

被显式选择并通过独立冻结门禁后的追加式文档身份。它不同于仍待审阅的 Candidate。

## 五组不能混淆的概念

| 不要混淆 | 前者说明什么 | 后者说明什么 | 判断规则 |
| --- | --- | --- | --- |
| 哈希完整性 vs 语义证明 | 哈希完整性说明所指字节与冻结时一致。 | 语义证明说明这些字节和规则足以支持 CodeFact 的每个原子。 | 哈希正确但 locator 指错行时，完整性通过、Proof 仍失败。 |
| Trace locator vs Proof | Trace locator 告诉读者去哪里找来源。 | Proof 解释从来源字节到事实结论的可重验推导。 | 能打开某行不等于那一行支持所声明含义。 |
| Candidate ID vs 内容正确 | Candidate ID 区分一组确定内容和 lineage。 | 内容正确要求来源、Proof、原子守恒和读者门禁分别通过。 | 同一个错误内容也能有稳定 ID；ID 不能用作质量结论。 |
| FlowInterpretationRound vs ReaderCandidateRound | 前者在同一 Candidate 内复核一个 FlowSlice 的解释。 | 后者产生或替换整份产品候选，最多 Round 1 和 Round 2。 | 流程解释 R2 不消耗 Reader Candidate Round 2。 |
| configured Adapter/Auth vs observed upstream provider | configuredAdapterId 与 configuredAuthMode 说明本地选择的调用适配器和认证方式。 | observedUpstreamProvider 说明实际响应来自哪个上游服务；observed model、reasoning effort 和 sandbox 也需独立观测。 | 这些字段分别记录、分别验证；配置值不能冒充观测值，缺失或不匹配应 fail closed。 |
