# 基础持久化、公开 Interface 与信任合同（ACTIVE）

本文保存 Source Code Analysis Agent 各分析步骤共同依赖、但不应在八份步骤设计中重复的稳定技术合同。它是 [总体设计](../DESIGN.md) 的技术参考；分析步骤的业务职责、正式文件和字段仍以各自详细设计为准。

> **目标状态：** 本文描述经用户批准的语义框架目标。当前 Java、JSON Schema、fixture 和已安装产物尚未迁移；实现差异必须标为 `NOT IMPLEMENTED`，不能靠兼容 reader 或隐式默认值掩盖。

## 1. 稳定身份与 package

- 工程目录：`backend-agents/sources/source-code/`
- Maven：`org.sourceanalysis:source-code-analysis-agent`
- Java root：`org.sourceanalysis.app`
- 八个分析 package：`.analysis.inventory`、`.analysis.discovery`、`.analysis.graph`、`.analysis.fact`、`.analysis.flow`、`.analysis.interpretation`、`.analysis.knowledge`、`.analysis.document`
- 横切 package：`.capture.localgit`、`.artifact`、`.evidence`、`.runtime`、`.validation`、`.adapter.cli`、`.adapter.http`、`.adapter.provider`

数字前缀只用于文档和 `steps/<nn-semantic-key>/` 目录排序。公开类型、字段、schema、artifact ID 和命令使用语义名称。旧 `github-code`、`com.linguan.codemd`、编号步骤类型和旧 wire 不是别名；它们在新实现中继续 fail closed。

## 2. 三个持久化层次

### 2.1 Module publication

一个内部模块接收显式 typed upstream references 和 controls，产生一个或多个 canonical payload，最后安装 module receipt。下游必须 fresh-reopen receipt 和 payload；不得使用内存草稿、目录扫描、调用方 Path 或“最新文件”。

Module publication 用于：

- 程序 builder 的中间产物；
- Provider task、实际 request、raw response、observed runtime 和 generation receipt；
- 可选同快照 lookup 的请求、返回片段和预算处置；
- 公开分析步骤不需要逐文件暴露、但 Trace 必须可重开的执行材料。

Module artifacts/receipts 不计入 reader-visible run output 数量，但必须持久化并可由正式 semantic payload 反向引用。

### 2.2 Analysis-step publication

每个成功或带 Gap 成功的分析步骤安装该步骤注册的完整 semantic payload 集合，最后安装唯一 semantic receipt。semantic receipt 绑定：

- analysis step key 与执行身份；
- 精确 upstream publication references；
- publisher module publication reference；
- controls/profile reference；
- 每个 semantic payload 的 descriptor、SHA-256 和集合 root；
- `SUCCEEDED | SUCCEEDED_WITH_GAPS` 与 Gap refs。

分母、分区和业务 accounting 只保存在该步骤命名的 coverage/accounting payload，receipt 绑定它但不复制第二套账本。`FAILED` 不安装伪成功步骤目录。

### 2.3 Run manifest

只有完成 `nine-section-document` 的执行才可安装 `run-manifest.json`。它按闭合依赖顺序引用八个 analysis-step publications，并要求它们绑定同一冻结来源基础和相容 controls。Run manifest 没有第二份 receipt；自身 ID/SHA 即完成引用。

只执行到前七步之一的显式 `executeStep` 可以 `FINISHED`，但 `analysisResult=null` 且没有 root manifest。

## 3. Canonical bytes 与内容身份

- Agent 机器产物是 canonical UTF-8 JSON 或 append-only canonical JSONL；`document.md`、设计/进度 Markdown 和原始冻结输入是明确例外。
- JSON 拒绝 duplicate/unknown fields、非整数计数字段、非法 UTF-8、远程 `$ref` 和非 canonical key ordering。
- JSONL 每行独立验证，顺序由所属步骤合同规定，文件有 final LF；空文件只在 policy registry 明确许可时有效。
- 内容身份使用 domain-separated、length-framed UTF-8 preimage；排序采用 UTF-8 byte order，而不是 locale 或 JVM 默认顺序。
- ID 稳定只说明“这些 canonical 内容相同”，不说明业务解释正确。
- 更改 source、upstream reference、controls、模型实际响应或任何 identity-significant payload 必须产生新身份；不覆盖旧 publication。

本轮直接继承已验证的 `CanonicalJsonCodec`、module/analysis-step store、typed address/reference、binary framing 与 self-excluded identity 公式；它们不由 Luna/Terra 重新设计。精确可执行合同集中在 [Canonical 持久化与身份附录](canonical-persistence-identity-contracts.md)，公共 request/response、唯一 SourceLocator/Excerpt 与 module envelope/failure 字段在 [既有公共与模块合同](inherited-public-and-module-contracts.md)。本设计不允许实现者为了迁移旧 R0 wire 而复用不相同内容的旧 ID。新 Step 06 payload/type/version 只在后续实现 work unit 中加入 policy/filename registry，不改这套身份算法。

### 3.1 文档示例分类

1. **NARRATIVE_ILLUSTRATION**：只解释业务故事、顺序或局部关系；可以省略 wire 字段，示例中的 ID/version/digest 不是可加载值，也不能成为测试 golden、identity preimage 或跨示例映射依据。
2. **STRUCTURAL_WIRE_SPECIMEN**：逐字段展示某一个 exact record/variant 的完整字段、类型、required-nullable 规则、closed enum 和 canonical 顺序；所有 ID 至少满足 safe grammar，但 digest、size、root、receipt 或 artifact ID 明确未由展示 bytes 重算，因此不可 replay。每个 specimen 在其声明的 isolated fixture boundary 内自足；不同 analysis step 或不同 specimen 的相似业务名不建立隐式 ID remap。遗漏 required field/type 的内容不能靠改标签冒充 specimen。
3. **STRICT_REPLAY_GOLDEN**：输入 bytes、direct preimages、typed references、canonical ordering、输出 bytes 以及每个 size/SHA/ID/root/receipt 都完整给出并实际重算；可以直接作为 Luna 测试 golden。任何一个值是占位符、跨边界引用未给出或 identity 未重算时，都禁止使用 `exact`、`schema-valid`、`executable`、`golden` 或本标签。

权威字段表、record 组件、enum、identity 公式和依赖表始终是 exact 合同，与示例分类无关。大型 DepotHead module 块默认是逐 analysis step 隔离的 `STRUCTURAL_WIRE_SPECIMEN`，除非紧邻标签明确标成 narrative；严格重放测试必须在自己的 fixture 目录保存完整 preimage closure 并重算全部 identity。

## 4. 原子安装与重开

`CanonicalModuleArtifactStore`、`CanonicalAnalysisStepArtifactStore` 和 `CanonicalRunManifestStore` 是三个深模块 Interface；调用者不知道真实目录布局。

共同不变量：

1. 先验证完整 request、policy、schema、upstream references 和预算。
2. 在同文件系统不可见 staging sibling 中写 payload。
3. force payload，再写并 force receipt/manifest。
4. 重新打开并重算 canonical bytes、descriptors 和 root。
5. 最后 atomic move；不支持原子移动时 fail closed。
6. 同一请求、逐字节相同内容返回 `ALREADY_INSTALLED`；不同内容碰撞不覆盖。
7. staging 残留、半套文件、额外文件、symlink 或目录穿越永不算 installed publication。

Active v0 不实现同一 run 跨进程恢复。已经完整安装的上游 publication 保留；调用方可以显式创建新 run 或新 step execution。恢复待办仍只由 `docs/supplements/runtime-recovery-todo.md` 管理，本轮不改。

## 5. 跨步骤 typed reference

公开 request/response 不接受 filesystem `Path`。一个下游步骤只能通过 typed reference 消费上游：

| Reference | 指向 | 下游必须复验 |
| --- | --- | --- |
| `VerifiedSourceInventoryReference` | Step 01 publication | 冻结 source identity、inventory root、receipt |
| `ApplicationDiscoveryReference` | Step 02 publication | Step 01 ref、entries/sites/catalog coverage |
| `ProgramGraphsReference` | Step 03 publication | Step 01/02 refs、五图/index/Gap root |
| `ProvenCodeFactsReference` | Step 04 publication | Step 03 ref、Fact/Proof/Gap/accounting root |
| `BusinessFlowsReference` | Step 05 publication | Step 02/03/04 refs、Flow/Capsule/entry coverage |
| `FlowInterpretationReference` | Step 06 publication | Step 01/02/03/04/05 refs、semantic/accounting roots |
| `RepositoryKnowledgeReference` | Step 07 publication | Step 01/02/03/04/05/06 refs、admission/conflict/accounting root |
| `NineSectionDocumentReference` | Step 08 publication | 所有七个 upstream refs、plan/document/Trace/candidate/baseline root |

每个 reference 至少包含 publication identity 和 SHA expected-value；它们是验证条件，不是调用方可操纵的 locator。

## 6. 运行状态与分析结果

单进程运行状态只有：

- `QUEUED`
- `RUNNING`
- `FINISHED`
- `FAILED`

完成 Step 08 后的 `AnalysisResult` 只有：

- `COMPLETE`
- `COMPLETED_WITH_GAPS`
- `INCOMPLETE_SCOPE`
- `INCOMPLETE_COVERAGE`

`INCOMPLETE_*` 是已归档、可重验但不可 Selection 的诊断结果，不是运行中状态。引用/哈希/来源身份损坏、无法形成合法诊断闭包、Provider started 后失败或安全边界破坏是 `FAILED`。

## 7. 唯一公开应用 Interface

`RepositoryAnalysisAgent` 保持一个 run-centric Interface：

- `start`
- `executeStep`
- `inspect`
- `artifact`
- `render`
- `validate`
- `trace`

Java、CLI 和 authenticated loopback HTTP 是同一 Interface 的对称 adapter，不得增加 adapter-only 业务分支。`executeStep` 创建新执行并消费精确上游 publications；它不是 same-run resume。

只读观察不得：调用 Provider、执行客户代码、刷新来源、注入证据、修补 publication 或产生新的业务解释。

Artifact lookup 使用 `runId + artifactId`，可带 sealed `ArtifactLocation` expected-value。只有 policy 为 `PATH_FREE_COMPLETE_UTF8` 的内容可以返回完整 bytes；源码片段、raw Trace、prompt、raw model response 和含 repository path 的材料是 `METADATA_ONLY`。

## 8. Evidence、Proof、解释与 Trace

四种材料必须分开：

| 材料 | 能担保什么 | 不能担保什么 |
| --- | --- | --- |
| `Evidence`/located excerpt | 来源快照中的精确文件、行/字节和原文 | 对原文的业务解释正确 |
| `Proof` | 某个 closed `CodeFact` 原子可由来源和确定性规则重验 | 组织目的、角色或跨请求制度必然如此 |
| model interpretation | 对业务对象、目的、规则、Action 或过程的有依据解释/假设 | 新的来源事实或自动批准 |
| human confirmation | 对重要不确定解释作组织责任判断 | 改写历史来源或把缺失 Proof 伪造成存在 |

`SOURCE_CONFIRMED` 只可用于有相应 Proof 的精确技术陈述。带 file/line 的未证明片段可以支持 `LOCATED_SOURCE_CONTEXT` 下的业务解释，但该解释仍是推断，并进入模型 REVIEW 或人工确认。Hash 正确不替代 Proof；Trace locator 也不替代 Proof。

Trace 必须从正文/知识项回到：准入决定、draft/review、packet-local handle 的 program-only reverse binding、Fact/Proof 或 located excerpt、VerifiedSnapshot。对纯解释允许没有 Proof hop，但必须有 located excerpt 或显式 Gap，并清楚标记为 interpretation/hypothesis。

## 9. Provider 与模型执行

- 产品语义模型目标为 `gpt-5.6-luna / high`；自动化测试只用 scripted Provider。
- configured adapter/auth、expected runtime 和 observed provider/model/reasoning/sandbox 分别记录和验证。
- 一个 Provider request 一旦 started，不自动 retry、switch、resume 或回退 API key。
- semantic task、物理 Provider request 和可选 source lookup tool read 分别计数；不得把多次 continuation/tool round-trip 宣称为一次物理请求。
- Prompt、model-safe packet、raw response、parsed draft/review、generation receipt 全部通过现有 module publication 基础设施持久化。
- Scripted Provider 只能证明合同；真实业务质量必须从一个明确授权的小型 Luna/high 冻结样本开始，再决定是否扩大。

## 10. 安全与通用解析

- 只读明确命名的 frozen snapshot；不读活动工作树，不运行客户 Maven、插件、测试、脚本、class loading 或 application。
- JavaParser/Symbol Solver 只解析快照内 source roots；缺 classpath、dynamic dispatch 或 unsupported construct 形成 Gap。
- Standard MyBatis `DOCTYPE` 只有在外部 DTD、general/parameter entity、schema 和全部 network resolution 均禁用时才可解析。
- 通用静态分析可以证明受支持代码行为；仅凭没有组织上下文的语法和调用图，不能唯一推出业务目的、参与者或跨请求制度。新增支持应优先扩展通用技术 adapter，而不是堆叠某行业名词规则。

## 11. 目标迁移状态

| 项目 | 当前实现 | 批准目标 | 状态 |
| --- | --- | --- | --- |
| Step 05 | 五 semantic payload + receipt；Flow/Capsule 严格证据闭包 | 保持文件名和证据强度；Step 06 另适配无 Flow 入口材料 | 当前能力保留 |
| Step 06 local | mandatory R0 registry + finite-key R1/R2 | DRAFT + REVIEW，terminology 为普通数据 | `NOT IMPLEMENTED` |
| Step 06 process | 预计算 edge/group + P1/P2 narrow review | 高召回 context retrieval + process/reconciliation DRAFT/REVIEW | `NOT IMPLEMENTED` |
| 正式 run outputs | 47 semantic + 8 receipts + archive + root = 57 | 42 semantic + 8 receipts + archive + root = 52 | `NOT IMPLEMENTED` |
| JSON Schema/Java | 仍实现旧 wire | 必须在后续独立 TDD work units 切换 | 本轮不得修改 |

迁移不删除旧 candidate、module publication、progress 或恢复设计。新实现不提供 silent alias、dual reader 或“缺字段即按旧语义”兼容；生产切换必须在后续用户授权 work unit 中重新冻结 schema、RED、实现与验收。
