# 已批准清理与可扩展活动覆盖实施衔接

> **状态：IMPLEMENTATION IN PROGRESS。** 这是 [总体设计](../DESIGN.md) 的唯一当前实施衔接，规格为[代码清理与可扩展活动覆盖设计](code-cleanup-and-scalable-activity-coverage-design.md)。基线 `fc6d67b` 已按用户授权提交并推送到 `origin/main`；本文件随后指导本 worktree 中的 TDD、定向 Maven 验证、每个完整交付的本地提交/推送，以及最后一次单材料 Luna/high 验收。它不授权客户 Maven、客户系统、整仓真实模型运行、API-key 回退或无关全仓测试。

> **For agentic workers:** 本计划已获实施授权。每个 work unit 先用 Luna/xhigh 建立本文列出的直接 RED，再由 Terra/xhigh 做最小 GREEN；使用 `superpowers:test-driven-development`，维护独立 progress，并且不扩大到本文明确排除的范围。

**Goal:** 安全退役当前工作流不再使用的旧解释链，并让现有 ActivityExplainer 对任意 N 入口材料在一次 DRAFT + 一次完整 REVIEW 内做到可预算、可闭合、可具体下传。

**Architecture:** 保留八步、Step05 EntryContext、四个深业务 Module、唯一 RepositoryAnalysisAgent 和现有 checkpoint stores。先解除测试对旧类的偶然依赖，再删除旧消费者/注册并做两个 Capsule 字段减法；随后只在现有 Activity→Process→Report 接力增加 v2 coverage 与具体 partial，不建新流水线、scanner、业务分类器、存储或恢复系统。

**Tech Stack:** 当前 Java 17/Maven/Jackson/JUnit/scripted Provider；版本与命令前缀服从当前 pom.xml、.mvn 和 source-scoped AGENTS。本计划不升级工具链或改变 POM lifecycle。

## 0. 当前基线与全局约束

已经完成且本计划不重做：

- Step03/04 稳定图与 Fact/Proof 算法；
- Step05 EntryContext/Capsule 到 BusinessMaterialBuilder 的连续传递；
- ordinary Flow/Capsule publication 不重复 compile/project；
- Spring `methodCondition` 对 unrestricted/explicit 的区分；
- BusinessMaterialBuilder → ActivityExplainer → `analysis.knowledge.ProcessExplainer` → BusinessReportPublisher 的当前工作流。

尚未实施、由本文接手：

- `analysis.interpretation.{model,proposal,registry,process}` 共 78 个旧生产类、四个旧测试包 14 个文件、旧 Step06 地址 1–9 和旧 artifact/schema 分支仍在；
- 14 个当前测试仍借 `RegistryProposalTaskCompilerTest` 的通用 Step05 fixture；`BusinessFlowCoverageTest` 仍有 R0 尾段；
- Capsule 仍有 `registryProposalBasisAtomIds`、`registryProposalBasisGapIds`；
- Activity resource/schema/validator 仍是 v1：四入口 DRAFT 只覆盖 E1/E2 时在 REVIEW 前终止；
- Process/report 模型输入仍只传 NOT_ANALYZED 数量；
- Activity/Process 仍在循环结束后聚合 publish；逐包即时保存是独立缺口，不在本计划实现。

所有 work unit 共同遵守：

- 保留 10/11 module 地址、`ModelRuntimeIdentityV1`、`analysis.knowledge.ProcessExplainer`、EntryContext、facts、gaps、processJoinSignals、SourceRefs 和固定九章。
- 不自动 replay/retry/switch Provider，不添加第三次模型调用、API key fallback、same-run takeover、兼容 reader、dual writer 或新 recovery protocol。
- PARTIAL/INCOMPLETE 是文档语义和验收结论，不新增 runtime/report enum。
- 只跑新增或直接覆盖改变的 selector，Maven 串行；客户扫描/构建、全 suite 与整仓真实模型运行不在本轮授权内。Task 7 的精确单材料 Luna/high 调用是唯一例外；每个完整交付在本地验证后提交并推送，不等待远端 CI。
- 每个实施 work unit 同步当时的当前事实；未真正删除/升版前不得把目标写成现状。

## Task 1：先迁移通用测试 fixture，保住当前测试面

**Files**

- Create: `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupport.java`
- Modify: 14 个在 cleanup 设计 §2.3 列出的当前测试
- Modify: `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java`

**Interface**

- Consumes: 当前 Step05 typed fixtures 与 `ProgramGraphsPublicFixture`
- Produces: 与旧 registry proposal 测试类无关的 `publishBusinessFlows`、`publishProvenFacts`、`flowProfile`、`capsuleProfile`、`reference` testsupport

- [ ] Luna/xhigh 先用引用/编译检查锁定 14 个当前测试实际只需要的 helper surface；不得搬入 RegistryProposal task 行为。
- [ ] Terra/xhigh 将 helper 原样迁到中性 testsupport，调用处改为直接 typed 构造；保留 Provider 注入、DRAFT→REVIEW 和非法 ref 语义断言。
- [ ] 删除 `BusinessFlowCoverageTest` 仅 R0 task 的尾段、imports 和 `modelCapsuleView`；保留真实 Flow/Capsule、budget Gap、eligible 分区和 span closure。
- [ ] 只运行这 15 个直接测试；任何当前语义断言丢失即停止，不进入旧包删除。

## Task 2：原子退役旧解释链与专属注册

**Files**

- Delete: `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/`
- Delete: `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/`
- Delete: `src/main/java/org/sourceanalysis/app/analysis/interpretation/registry/`
- Delete: `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/`
- Delete: 对应四个旧测试目录中的 14 个测试文件
- Modify: `AnalysisStepModuleAddress`
- Modify: `AtomicCanonicalPublicationEngine`

**Interface**

- Consumes: Task 1 已独立的测试 fixture 和当前四 Module 工作流
- Produces: 只保留 Step06 10/11 与当前 Step07 ProcessExplainer 的运行注册

- [ ] Luna/xhigh 建立 active source/test 依赖守卫：旧包 import、旧 1–9 module 注册和旧 artifact type 不得被当前工作流引用，同时 10/11 与 `ModelRuntimeIdentityV1` 必须存在。
- [ ] Terra/xhigh 在一个可审查变更中删除 78 类/14 旧测试及其专属 registry；不留 alias、bridge、compatibility reader 或复制包。
- [ ] 运行 Task 1 当前测试面、module-address/canonical-engine 的直接 selector 与当前 workflow selector；历史 docs/progress/artifacts 不参与 stale-name 失败。

## Task 3：在旧消费者消失后做 Capsule 两字段减法

**Files**

- Modify: `CapsuleProjection.EvidenceCapsule`
- Modify: `EvidenceCapsuleProjector`
- Modify: `CapsuleProjectionModulePublisher`
- Modify: `AtomicCanonicalPublicationEngine` 的 owning schema registry
- Test: `EvidenceCapsuleProjectorTest`, `CapsuleProjectionModulePublisherTest` 及直接 reader/reopen selector

**Interface**

- Consumes: 不再有 registry proposal/process consumer 的 current Capsule
- Produces: capsule-projection v9、evidence-capsule v7；不含两个 registryProposal basis 字段

- [ ] Luna/xhigh RED 证明新 wire 不再接受/产生 `registryProposalBasisAtomIds` 与 `registryProposalBasisGapIds`，同时 EntryContext、factViews、gapViews、processJoinSignals、source/basis closure 逐项保留。
- [ ] Terra/xhigh 删除两个字段、构造参数、赋值和 JSON 分支，只升级真正受影响的 v8→v9、v6→v7 owning schemas/readers。
- [ ] 旧磁盘产物保留旧身份；新 reader 不兼容解释旧 payload，canonical bytes/hash 不宣称跨版本相等。

## Task 4：ActivityExplainer 任意 N、预算和唯一 REVIEW

**Files**

- Modify: `ActivityExplainer`, `ActivityExplanationResult`
- Modify: `ActivityExplanationCheckpointPublisher` 及其 reader
- Modify: `ActivityPromptCatalog`
- Create: versioned Activity DRAFT/REVIEW v2 resource Prompt；v1 只在 catalog 切换后删除未引用 resource
- Modify: activity coverage owning schema 到 v2；ReviewedActivity shape 未变则保持其版本
- Test: `ActivityExplainerTest`, `ActivityExplanationBudgetTest`, `ActivityOutputSchemaTest`, `ActivityPromptContractTest`, `ActivityExplanationCheckpointTest`

**Interface**

- Consumes: 一个完整 material、实际 `N=entryIds.size()`、E1…EN/ref allowlists、相容 profile
- Produces: ReviewedActivity、global coverage、`ActivityExplanationResult.unexplainedActivityEntries`

- [ ] Luna/xhigh RED：N=4 合法 DRAFT 只含 A1/E1、A2/E2 时，唯一 REVIEW 收到完整 material、完整实际 DRAFT、`missingEntryKeys=[E3,E4]`。
- [ ] RED：单包 N≥12 的 schema/Prompt/映射准确包含 E10/E11/E12；跨包相同 E1 仅按 `(materialId,key)` 映射，不用前缀/substring/词典序。
- [ ] RED：活动与入口 many-to-many 合法；输出容量允许 N 项独立活动但不强制恰好 N 项。
- [ ] RED：首次调用前检查 `maxActivitiesPerMaterial≥N`、单活动 key 容量≥N、N 项最小 REVIEW output、实际 cleanPacket 和完整 REVIEW 预留；DRAFT 后对真实 actualDraft REVIEW packet 再 canonical serialize。
- [ ] RED：不相容材料零请求并写具体 NOT_ANALYZED；ActivityExplainer 不拆已保存 material。非法 JSON/key/ref/ID/bytes 或 started 失败立即 fatal，不进 REVIEW。
- [ ] RED：REVIEW 响应 required `unexplainedEntries` 允许 `[]`、禁止 null/省略；activities keys 与它 union 为全集且 disjoint。REVIEW 仍漏项 fatal，无第三次调用。
- [ ] Terra/xhigh 将现有 validator 拆成 structure/scope 与 final closure 两层，程序生成 `UnexplainedActivityEntry(entryId,materialId,entryKey,materialContext,MODEL_NOT_EXPLAINED)`。
- [ ] `activity-coverage.json` v2 顶层 `unexplainedActivityEntries` 保存完整 record 数组；模型 `unexplainedEntries` 只保存 scope-local keys，二者不得混用。

## Task 5：具体 partial 经 knowledge 进入第 9 章

**Files**

- Modify: `RepositoryBusinessKnowledge`, `ProcessExplainer.repositoryInput`
- Modify: `ProcessKnowledgeCheckpointPublisher` 及 reader、真正承载新字段的 repository/process coverage schemas
- Modify: `BusinessReportPublisher.cleanKnowledge` 与报告 input Prompt
- Test: `ProcessMaterialRecallTest`, `ProcessKnowledgeCheckpointTest`, `BusinessReportPublisherTest`, direct reopen tests

**Interface**

- Consumes: Task 4 程序侧完整 unexplained records
- Produces: knowledge/report 模型输入中按 material 聚合的 `{materialContext, unexplainedEntryKeys, reasonCode}`

- [ ] Luna/xhigh RED：同一 materialContext 只发送一次，包含全部 local missing keys；material/global entry IDs 留程序侧，不 regex 解析中文 context，不新增 EntryDescriptor。
- [ ] RED：Process 模型不得把 `MODEL_NOT_EXPLAINED` 变成虚构活动、过程、SOURCE 缺失或技术 Gap；其现有 member/ref/JSON/fatal validator 保持不变。
- [ ] RED：报告第9章以 context 中完整 HTTP 方法/路径列出具体入口与原因类别，不只写数量或裸 E3/E4；第4章不替未解释入口编造活动。
- [ ] Terra/xhigh 只扩现有 result/checkpoint/read/input seams 和确实承载数组的 schema；报告九章 output shape 未变则不强制升版。
- [ ] 0 入口时 Activity/Process Provider 为 0；显式空仓报告仍走现有 DRAFT+REVIEW，文档验收 INCOMPLETE。

## Task 6：四入口与可扩展性闭环验收

**Files**

- Test: `PersistedBusinessRunExecutorTest` 及 Tasks 1–5 的直接 selectors
- Docs: 只在实现事实真正改变后同步当前权威文档

- [ ] Scripted complete 分支使用真实四 ref：E1 DELETE /user/delete S487、E2 GET /user/getUserSession S722、E3 POST /user/registerUser S731、E4 GET /user/logout S898；REVIEW 补齐四入口，Process 可保守输出独立过程，报告固定九章。
- [ ] Scripted PARTIAL 分支用 `unexplainedEntries=[E3,E4]`；knowledge/report 收到一次 context + keys + `MODEL_NOT_EXPLAINED`，第9章具体列入口；该结果不得通过完整业务验收。
- [ ] 同时覆盖跨包 N=9、单包 N≥12、零入口、输出/REVIEW 超预算、非法首轮和 REVIEW 仍遗漏。
- [ ] 不把用户四动作强串成生命周期；合成补货故事继续标 SYNTHETIC，不冒充 jshERP。
- [ ] 直接 selectors 串行通过后做一次 spec/standards review。当前失败 v1 response 不重放、不改名为 v2 结果。

## Task 7：脚本验收后执行已批准的单材料 Live 验证

**Files**

- Modify: `LiveLunaAutomaticMaterialIT` 或其同等的精确选择器测试；仅创建新的命名候选目录
- Output: 新候选的 DRAFT/REVIEW 请求、响应、结果与可读人工检查记录

**Frozen input and limits**

- 只选择 `material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb`，并逐项核对有序入口 `entry:160b90d56d87df77d8ad02aadb61130b138af016d31edde06f30e517f4497ece`、`entry:2d16e9a0ceb9b9555823ff3b091485941d4e26fb0b8e75b0314fdedd837722bc`、`entry:2d573f55b3164ae226957503381cb4c22104e263ba0e36f316cd1ad609302439`、`entry:3bc9f42e69961211dec7e48baeffbb0bd61a4c4be03280eb02894f86b6e9b144`，以及 `[S487,S722,S731,S898]`。
- 使用 `ActivityExplanationProfile(20000, 12000, 4, 24, 1000)`；只使用登录的 Codex Subscription Luna/high，一次 DRAFT 加一次完整 REVIEW，最多两次请求；不运行真实过程、报告或整仓调用。

**Acceptance and stop rule**

- [ ] 调用前先检查登录状态、子进程本地状态和全部预检；任一不匹配或容量不足时零请求并记录原因。
- [ ] 理想结果是四个入口均有实质业务解释：不得仅以编号、方法名或技术类型充数，也不得把删除、会话、注册、退出强写成必然的用户生命周期。
- [ ] 非法 JSON/ref/key、DRAFT/REVIEW 仍不能闭合、传输或运行失败均为该新候选的 fatal；保存输入、输出和结果后停止，不重试、不改用 API key、不重放旧失败请求。
- [ ] 这一次 DRAFT+REVIEW 是同一候选的内容审阅，不是新的产品改进回合。本计划没有获批的第二个 Reader Candidate；若人工检查发现明确问题，保留 findings，等待用户另行授权后才可启动 Round 2 replacement。
- [ ] 执行后人工核对业务语言、范围限定和四个入口覆盖；程序只验证 schema/ref/coverage/预算，不能把通过结构检查说成业务质量通过。

## 独立缺口与停止条件

- Activity/Process 每包 REVIEW 即时保存仍未实现。固定 module 地址不能循环安装不同聚合内容；若用户以后单独授权，先确定不覆盖既有 publication 的聚合/分片语义。本计划不设计或实现它。
- 受保护 dirty 主 worktree 中的 stage01–04 只记录待协调，不执行 worktree remove/reset/clean，也不把 target 构建目录当源码。
- 任一实施任务若需要改变八步、四 Module、九章、source/Proof 信任、公共 Agent、当前 10/11 身份、Process/Report 其他 validator 或新增业务结果决定，立即回 Astra/Sol ultra 作最小设计裁决；只有影响最终业务目标或跨模块身份的变化才交回用户。
- 本轮授权范围限于本计划列出的 Java/test/resource/schema、定向 Maven、文档与本地验证、交付提交/推送，以及 Task 7 的单材料真实 Provider；其余客户扫描、客户构建、整仓模型运行和外部 API key 均不在范围内。
