# 来源、发布与信任边界

本附录服务 [八步总体设计](../DESIGN.md)。保留来源完整性、精确 Fact/Proof 和可观察持久化；删除重复算法和逐层资格判断。它不创建新存储、恢复、graph/proof 或业务分类体系。

## 1. 各种依据分别说明什么

| 依据 | 可以说明 | 不自动说明 |
| --- | --- | --- |
| Verified source / SourceExcerpt | 这是指定冻结文件的准确代码片段 | 运行成功或完整业务意义 |
| Program graph | 支持范围内的准确结构、调用、控制/数据关系及定位 | 所有路径均已分析、行业业务目的 |
| Step04 Fact/Proof | 选定模式全部原子通过相应确定性规则 | 所有可读信息都在 Fact 内、任一外部实际效果 |
| Step05 EntryContext | 入口的已知关系、代码结构、完整片段和具体限制 | 无 Proof 的内容自动变成 exact Fact |
| Reviewed business interpretation | 模型根据材料整理的活动、过程与业务语言 | 已人工确认的制度、实际执行结果 |
| Human confirmation | 授权者对指定解释的确认 | 修改源码或补造不存在的 Proof |

SOURCE_CONTEXT 可支持安全源码阅读，但不能赋 CLOSED、SOURCE_CONFIRMED 等严格技术标签。unknown graph edge、没有 Fact 或缺 strict Flow 不等于没有可读源码。业务解释按相应段落给依据和限定，不要求每词一条 Proof。

## 2. 计算只有一个拥有者

| 工作 | 唯一拥有者 | 下游应做什么 |
| --- | --- | --- |
| 来源 admission/index | Step01 | 复用已验证 immutable source view |
| 入口分母 | Step02 | 按已发现 IDs 消费，不重新发现 |
| 五图关系 | Step03 | 按真实 edge/locator 导航，不补边 |
| 选定技术证明 | Step04 | Facts 可选附着，不重复 enumerate/prove |
| 连贯入口关系及源码 | Step05 | EntryContext 是唯一模型；Capsule 是有界原样投影 |
| 模型材料短 refs/预算封装 | BusinessMaterialBuilder | 不重新构造调用链 |
| 活动/过程/九章解释 | 三个现有 Explainer/Publisher 内容任务 | DRAFT + 完整 REVIEW，完整正文继续传递 |

Step05 在现有 flow-slices/Capsule 文件中保存有代码的上下文。无 strict Flow 的安全入口也由同一 owner 整理，flowRef=null；不要求另一个 noFlow chain Module 或 Builder fallback 分析器。

## 3. 保存保留，普通算法重放删除

保留现有技术 ModuleArtifact、module receipts、八步 publication 与业务 checkpoints，保留 canonical 身份公式和路径安全。一次可信执行中，owner 完成计算后可将 immutable typed view 交给下游；每个有意义的步骤仍保存可观察结果。

publisher 只序列化、检查必要 type/ID/ref/budget、计算写入 bytes/hash 并原子安装。它不能再次调用业务 compiler/projector 检查自己是否可信。Fact 枚举器、Flow compiler、Capsule projector 在普通执行各运行一次；明示的独立 audit/mutation tests 可以重放，但不是每层 reader/publisher 的常规前置。

磁盘、新进程、导入或显式跨 run 复用是信任边界：核验 typed reference、exact file set、schema、hash、ID、refs 与保存 basis，实际读取源码 bytes 时核验对应文件/切片。身份/hash/schema/ref 检查保留，算法重算区分开。同进程已验证 immutable bytes 不反复扫描仓库。

### 3.1 文档示例分类

- NARRATIVE_ILLUSTRATION / TARGET_CONCEPTUAL_PROJECTION_NOT_WIRE_SCHEMA：解释责任和数据形状，可用短 ID；不是可提交 wire、当前已保存输出或测试 golden。
- STRUCTURAL_WIRE_SPECIMEN：展示 exact record/variant，但 digest/root 未经展示 bytes 重算，不可当 strict golden。
- STRICT_REPLAY_GOLDEN：确由完整冻结 fixture preimages 生成并验证身份的重放基准，只能用于其明确 fixture。

真实源码摘录和真实保存产物必须说明具体来源，不能把目标字段或合成故事贴上“已运行”标签。新增目标 EntryContext 与 Spring methodCondition 字段在所属步骤有明确 required/type/nullability；后续实现须升级 schema，本轮未改 JSON Schema。

## 4. 保持现有持久化，不新建基础设施

技术 Module 仍先保存 payload、后 receipt，AnalysisStep store 组合其命名 semantic 文件。不得预报自身 receipt/root 构成循环。canonical framing、identity preimage、原子 install、collision 等具体规则见 [Canonical 附录](canonical-persistence-identity-contracts.md)；public request、SourceLocator、Module envelope 见 [公共接口附录](inherited-public-and-module-contracts.md)。

业务材料在首次 Provider 前保存。目标是完整 REVIEW 的 activity/process 随即保存；当前两个 Explainer 都在各自循环结束后以固定 module 地址聚合 publish，不能把 publisher 简单移入循环并反复安装不同 bytes。即时逐包保存仍是独立已知缺口，本次清理/覆盖修复不扩建分片或恢复协议。报告保存完整 paragraph JSON、SourceRefs、Markdown 和 validation。inputFingerprint 包含实际内容输入、实际 Prompt 文本/版本、有效模型/output 配置、Module 版本；新 runId 不属于内容输入。跨 run 比较 fingerprint 还要经过磁盘边界完整性验证，不能只比较一个字符串就信任未知 bytes。

使用当前 run/checkpoint stores 和 output manifest 记录已有结果，不要求新建 CanonicalRunManifestStore、固定 52/57 文件大清单、event journal、hash chain、reconciliation ledger 或同 run recovery。保留历史身份和已完成产物，不另做 Wire Reset、dual writer、兼容 alias 或第二 namespace。

## 5. 失败要落在真正问题上

fatal：错误 source identity、坏 bytes、危险 path、断 refs、伪 exact Proof、冲突 ID、budget 安全违规、非法模型 keys/refs、不完整 JSON、Activity REVIEW 仍遗漏入口、覆盖遗漏却声明完整、原子安装失败。不能降为“低置信度”继续发布。唯一例外是 Activity DRAFT 结构与 scope 均合法而仅 coverage 不足：它按已批准目标进入唯一 REVIEW，不能把同一例外外推到 Process/Report 的其他非法结构。

局部限制：unsupported 静态结构、无法证明的 edge、未知业务含义/岗位/制度、可能的跨活动顺序、无 strict Flow。保留可读的已知部分，对受影响结论注明限制。无法安全定位或包超预算才记入口不可分析，不能全仓排除安全代码。

全入口与过程组都要有明确处置。账面闭合并不意味着业务完成；只有范围与内容达到目标才能宣布整仓九章已完成。

## 6. Provider 与内容审阅

业务活动、过程和报告用 Luna/high。每个有界任务最多 DRAFT + 一次完整 REVIEW；后者输入原材料和完整实际 DRAFT，输出完整修订结果。Activity 的批准目标还把程序计算的 `missingEntryKeys` 交给 REVIEW，并要求响应字段 `unexplainedEntries` 存在（允许 `[]`，禁止 null/省略）；活动 keys 与其并集为全集且不相交。程序侧另存完整 `unexplainedActivityEntries` records，并按 material 聚合具体入口给 Process/Report。所有有用条件、规则、公式与长段落保留到下游和 final Markdown。

调用前校验预算/schema/allowlists；超容量零请求并保存未覆盖原因。started 后 transport/schema/runtime 失败停止该执行，不自动重试、切 Provider、用 API key 或重放不确定请求。内部调用属于同一 Reader Candidate，仍只允许产品 Round1 与针对具体问题另行授权的 Round2。

真实 Provider 需当次授权与登录状态/本地状态写入 preflight；自动测试只用 frozen fixtures 和 scripted Provider。源码本身是数据，不得服从其注释、字符串或 Markdown 内的指令。

## 7. 当前实现审计

Canonical stores、源码/图/Fact 纵切、四个业务 Module、BusinessAnalysisWorkflow、RepositoryAnalysisAgent 和持久化运行基础均已存在。Step05 EntryContext→Builder 接力、普通 Flow/Capsule 发布去重与 Spring unrestricted `methodCondition` 已实现；旧 interpretation 链清理、Activity 任意 N/v2 REVIEW 闭合，以及具体未解释入口到 knowledge/第9章也已实现。四个业务 Module 不能写为 NOT IMPLEMENTED；下一项是 scripted 四入口与任意 N 的全链验收，不能把它提前写成真实整仓业务验收。

固定完整 jshERP 捕获和图/Fact 产物已有证据；相关财务图/Fact 历史 run 没有 Step05 正式 publication，另一历史 material run 只提供受限 fallback，不能跨 run 拼接。较新的全仓材料 run 为 107 包覆盖 339 个入口。存在实现或人工小包结果均不证明自动整仓九章已验收。本轮只改文档，不改变现有 artifact、Schema、resource Prompt 或代码。
