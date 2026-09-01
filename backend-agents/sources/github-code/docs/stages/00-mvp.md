# 00 POC 实现记录与目标设计反馈

## 1. 文档职责、状态与结论

- 阶段状态：**POC 历史记录 / 准确度出口未满足**。
- 实现形态：Java 17、单 Maven 模块、离线、无 HTTP、无远程 Git、无 live model Adapter。
- 文档职责：记录阶段 00 实际实现、测试、固定样例审计和它们对目标设计的反馈；本文不是另一套总体架构，也不是运行日志。
- 总体架构：[GitHub Code Agent 总体设计](../DESIGN.md)。
- 九章权威：[NineSectionProfile](../../../../../shared/source-agent-contracts/README.md)。
- 当前目标路线：[01 冻结来源](01-freeze-source.md) → [02 发现应用与入口](02-discover-application-and-entries.md) → [03 五张程序图](03-build-five-program-graphs.md) → [04 事实与证明](04-prove-code-facts.md) → [05 流程与 Capsule](05-compile-business-flows.md) → [06 单流程解释](06-interpret-one-flow-at-a-time.md) → [07 准入与知识合并](07-admit-and-merge-business-knowledge.md) → [08 九章与运行归档](08-build-nine-section-document-and-archive.md)。

本 POC 已证明：人工 Flow Manifest 能驱动文件/Evidence 哈希与引用校验，两种隔离测试能分别经过 provider-free baseline 或 recorded R1/R2，再由程序输出固定九章并归档一份 document.md 与七个 JSON sidecar。

它同时证明这条 plumbing 还不具备事实准入准确度。当前 core 没有 Fact→Evidence semantic closure gate；固定 DepotHead 样例虽通过文件/摘录 SHA 和已知 ID 校验，却在逐 atom 独立审计中仅通过 2 个 Fact、失败 3 个 Fact。因此准确度出口 **NOT MET**，整个 Flow 已拒绝，**没有可接受 Candidate**。历史 Markdown 和 Candidate ID 不得改变这个结论。

## 2. POC 假设、临时机制与边界

### 2.1 要验证的假设

阶段 00 用最小纵向切片检验五个高风险假设：

1. Manifest 声明的文件和 Evidence 在解释前被重新校验；
2. 每个 Fact semantic atom 在准入前由该 Fact 声明 span 内字节和 Proof 支持；
3. 模型只能引用 Flow allowlist，R2 不能增加 R1 proposal 或扩张 basis；
4. 程序而非模型渲染固定九章；
5. Candidate 能以固定文件集原子归档，并在新进程中 validate/trace。

第 1、3、4、5 项已有受限实现证据。第 2 项未实现，且 DepotHead 固定样例已经给出反例；其他四项通过不能代替它。

### 2.2 临时机制怎样替代目标能力

| POC 机制 | 本阶段用途 | 它临时代替的目标能力 |
| --- | --- | --- |
| 人工 Flow Manifest | 由人列出文件、Evidence、LockedFact 和 Flow allowlist，让端到端 plumbing 可先运行。 | VerifiedSnapshot 后的自动代码/SQL 分析、CodeFact/Proof 准入、FlowSlice 编译和 EvidenceCapsule 编译。 |
| LockedFact | 把人工声明事实交给 parser 和 renderer。 | 由源码字节与 Proof 逐语义原子支持的 CodeFact；不闭合内容应为 Gap。 |
| provider-free baseline | 不创建 ModelProvider，隔离验证确定性渲染与归档。 | 不是目标旁路；目标只有一条准入主线，缺少解释时登记 Gap。 |
| recorded R1/R2 | 用固定 JSON 检验同一候选内部的引用 allowlist、proposal 完整性和 basis 收窄。 | 运行身份可验证的 FlowInterpretationRound。 |
| 八文件 archive | 固定一份 Markdown 与七份 JSON，验证原子安装、幂等、validate 和 archived Trace seam。 | Candidate/Trace 的内容寻址、不可变归档、完整重验和追加 lineage。 |

这些都是 POC 工具或测试例，不定义长期架构。目标名称、模块顺序和保证只由总体设计维护。

### 2.3 阶段内

- 人工 Flow Manifest；
- 文件 size/SHA、Evidence 行段 SHA 与已知 ID 引用校验；
- `LockedFact` 的 kind、attributes/legacy lockedAtoms 和 Evidence refs；
- provider-free baseline；
- scripted/recorded R1 interpretation + R2 precision review；
- 确定性九章、八个归档文件、candidate identity；
- 原子 archive、同内容幂等、冲突拒绝；
- archived validate 与 locator Trace。

### 2.4 阶段外

- live LLM、HTTP、远程 Git 或自动刷新；
- 自动 Flow 发现/编译；
- Symbol Solver、CFG/数据流、完整 Proof；
- discovery/analysis 自动生成 Manifest 或 Candidate；
- Trace 重开冻结源码并重算摘要；
- 语义原子守恒和读者内容质量完整门禁；
- 追加式 validation/runtime lineage；
- JPA、AOP、反射、SpEL、WebFlux、消息、调度、批处理和动态 SQL 解释。

仓库中已有三个邻接 seam，但不属于 POC 主链完成项：RepositoryDiscoverer 是后续入口/MyBatis 输入，SourceAnalyzer 是后续 CodeFact/Proof 输入，ModelRuntimeReceiptAdmission 是后续模型运行身份校验输入。本文只记录它们的接口现状和设计反馈，不用其存在把目标 ARCH-03/04/07 写成已完成。

### 2.5 退出条件

| 条件 | 当前结果 |
| --- | --- |
| 冻结文件或 Evidence 漂移在 provider 前失败 | IMPLEMENTED，合成测试覆盖 |
| baseline 不调用 ModelProvider | IMPLEMENTED，合成测试覆盖 |
| recorded R1/R2 未知引用和 basis 扩张失败 | IMPLEMENTED，合成测试覆盖 |
| 九章顺序和字节确定性 | IMPLEMENTED；只证明结构/字节，不证明内容充分 |
| 八文件 archive、同内容幂等、新进程 validate/trace | IMPLEMENTED；冲突与原子移动缺少完整直接负向测试 |
| Fact→声明 Evidence 的逐 atom semantic closure | **NOT IMPLEMENTED**；core 仅做哈希/引用校验 |
| 固定 DepotHead manifest 只读核对 | **HASH/REFERENCE PASS；SEMANTIC FAIL**；3/5 Fact 不闭合，已拒绝且未重新生成 Candidate |
| 阶段准确度出口 | **NOT MET**；不得用结构、SHA、Trace 或历史产物替代 |

现有实现和诊断 seam 可作为后续开发输入，但本 POC 不能宣称准确度退出，也不能把当前 DepotHead 冻结输入或其历史 Candidate 传递为已验收产物。当前没有可接受 Candidate。

## 3. POC 结果怎样反馈目标设计

下表保留 POC 时期的 ARCH 编号，便于理解历史证据；这些编号不再定义当前八阶段目标结构。当前成熟度只在总体设计末尾和八份阶段文档维护。链接指向真实类、测试和本地只读产物；`.workspace` 产物被 Git 忽略，只是核验材料，不是文档权威。

| 目标 ARCH | POC 证据类型 | 当前实现、测试与设计反馈 |
| --- | --- | --- |
| ARCH-01 冻结输入 | DIRECT | [MvpGenerationCore](../../src/main/java/com/linguan/codemd/mvp/MvpGenerationCore.java) 与 [ManifestEvidenceVerificationTest](../../src/test/java/com/linguan/codemd/mvp/ManifestEvidenceVerificationTest.java) 覆盖 POC 文件/摘录哈希；Java core 与 CLI 的 symlink 策略不统一；[DepotHead manifest](../../.workspace/mvp-depothead-audit-input/flow-manifest.json) 仅 hash/reference-valid，semantic audit 已拒绝 |
| ARCH-02 能力范围 | ADJACENT | [CodeMdCli](../../src/main/java/com/linguan/codemd/cli/CodeMdCli.java) 的 `WALKING_SLICE_V0` 与 [CodeMdCliDiscoveryTest](../../src/test/java/com/linguan/codemd/cli/CodeMdCliDiscoveryTest.java)；没有冻结 Capability Manifest |
| ARCH-03 语法/绑定 | OUTSIDE_POC | 00 只读取人工 Manifest；仓库中的 [RepositoryDiscoverer](../../src/main/java/com/linguan/codemd/discovery/RepositoryDiscoverer.java) 与 [SourceAnalyzer](../../src/main/java/com/linguan/codemd/analysis/SourceAnalyzer.java) 分别是后续阶段邻接 seam，不计为 POC 主链证据 |
| ARCH-04 Fact/Proof/Gap | DIRECT + ADJACENT | `LockedFact` 可被解析，但没有 semantic closure/Proof gate，且允许空 attributes object 与空 evidenceIds array；DepotHead 3/5 Fact 审计失败。[CodeFact](../../src/main/java/com/linguan/codemd/analysis/CodeFact.java)、[ConditionFact](../../src/main/java/com/linguan/codemd/analysis/ConditionFact.java)、[Gap](../../src/main/java/com/linguan/codemd/analysis/Gap.java) 属于后续邻接 seam |
| ARCH-05 流程切分 | DIRECT_MANUAL | [MvpGenerationCore](../../src/main/java/com/linguan/codemd/mvp/MvpGenerationCore.java) 读取人工 `flows[]`；无自动 Flow compiler |
| ARCH-06 Evidence/Trace | DIRECT_LIMITED | [CandidateArchiveService](../../src/main/java/com/linguan/codemd/mvp/CandidateArchiveService.java)、[TraceLocatorTest](../../src/test/java/com/linguan/codemd/mvp/TraceLocatorTest.java)、[trace-index.json](../../.workspace/mvp-depothead-baseline/trace-index.json)；Trace 不重开源码 |
| ARCH-07 模型协作 | DIRECT_RECORDED + ADJACENT | [ModelProvider](../../src/main/java/com/linguan/codemd/mvp/ModelProvider.java) 与 [InterpretationAdmissionGateTest](../../src/test/java/com/linguan/codemd/mvp/InterpretationAdmissionGateTest.java) 覆盖 recorded R1/R2；runtime receipt 属于后续邻接 seam，未接本阶段生成路径 |
| ARCH-08 程序准入与仓库知识 | DIRECT_LIMITED | core 有局部 R1/R2 admission 结果并直接交给 renderer；没有跨 Flow 仓库知识、owner、冲突或语义原子台账 |
| ARCH-09 NineSectionPlan | DIRECT_LIMITED | `MvpGenerationCore.MarkdownRenderer`、[DeterministicBaselineGenerationTest](../../src/test/java/com/linguan/codemd/mvp/DeterministicBaselineGenerationTest.java) 与 [NineSectionRenderingDeterminismTest](../../src/test/java/com/linguan/codemd/mvp/NineSectionRenderingDeterminismTest.java) 只覆盖九标题、内部词过滤和确定性；无 NineSectionPlan/原子台账 |
| ARCH-10 Candidate/身份/归档 | DIRECT_LIMITED | [CandidateArchiveService](../../src/main/java/com/linguan/codemd/mvp/CandidateArchiveService.java)、[CandidateArchivePersistenceTest](../../src/test/java/com/linguan/codemd/mvp/CandidateArchivePersistenceTest.java)、[candidate.json](../../.workspace/mvp-depothead-baseline/candidate.json)；validation receipt 可替换 |
| ARCH-11 Interface/Adapter | DIRECT_LIMITED | [CodeToMarkdownAgent](../../src/main/java/com/linguan/codemd/mvp/CodeToMarkdownAgent.java)、[CodeMdCli](../../src/main/java/com/linguan/codemd/cli/CodeMdCli.java)；四方法 Interface、六 CLI、无 HTTP/improve |
| ARCH-12 安全/不执行 | DIRECT_LIMITED | 没有客户执行路径；[RepositoryDiscoverer](../../src/main/java/com/linguan/codemd/discovery/RepositoryDiscoverer.java) 禁用 XML 外部解析，[RepositoryDiscovererTest](../../src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java) 覆盖标准 MyBatis DOCTYPE。CLI/walker/archive 拒绝或跳过各自范围的 symlink，但 Java core 仍跟随 Manifest/root 和 snapshot 内 file symlink，统一策略待硬化 |

### 3.1 由 POC 证据导出的目标设计变化

1. 目标总体设计只保留一条主线，不把 baseline 或 recorded provider 写成并列架构路径；它们只是隔离测试。
2. VerifiedSnapshot 负责来源完整性，CodeFact + Proof 负责语义正确性；两个门禁不能合并。
3. 人工 Flow Manifest 和 LockedFact 必须被自动分析、逐原子 Proof、FlowSlice 与 EvidenceCapsule 取代，不能成为长期输入合同。
4. Fact semantic closure 必须发生在 EvidenceCapsule 和任何模型任务之前；known ID、有效 SHA、模型一致或 allowlist identity 都不能补证。
5. FlowInterpretationRound 与 ReaderCandidateRound 必须分开，模型解释不能改变 Candidate 数量语义或源码事实。
6. 程序必须先组装仓库知识和 NineSectionPlan，并为每个语义原子记录正文、技术依据、Gap 或有理由排除；九个标题和字数不是质量证明。
7. Candidate ID 只证明身份，Trace locator 只证明位置链；两者都不是 Proof。目标 validate/trace 必须重开冻结源码并检查完整关系。
8. configured Adapter、configured Auth Mode 和 observed upstream provider 必须分字段记录，不能继续共用一个 provider 语义。
9. 一份 Markdown 加七个 JSON 只是一种 POC 归档布局；目标合同约束 Candidate、Trace、不可变性和追加 lineage，不固定长期文件数。

## 4. POC 实现模块与内存边界

### 4.1 POC Module

| Module | 当前 Interface | 当前职责 | 不负责 |
| --- | --- | --- | --- |
| `CodeToMarkdownAgent` | `generate`、`generateBaseline`、`validate`、`trace` | 内存 Adapter 的稳定 Java 入口 | archive、HTTP、improve |
| `DefaultCodeToMarkdownAgent` | 实现上述 Interface | 调用 `MvpGenerationCore`，按 candidateId 保留进程内 Candidate | 进程重启后的存储 |
| `MvpGenerationCore` | package-private static core | Manifest/Evidence 验证、两条生成路径、R1/R2 准入、身份、Trace index、Renderer | HTTP、live runtime receipt、目录 discovery |
| `CandidateArchiveService` | `archive`、`validate`、`trace` | 八文件归档、原子安装、idempotency、fresh-process validate/trace | 重开冻结源码、追加 lineage |
| `CodeMdCli` | 六个 subcommand | 离线 recorded/baseline/archive/diagnostics | HTTP、live model、`analyze` CLI |
| `CodeMdCli.RecordedProvider` | `ModelProvider.execute(ModelTask)` | 每轮提供一个已记录 JSON object，确保 R1/R2 各消费一次 | 多 Flow recorded response multiplexing |

`MvpGenerationCore` 与 `CandidateArchiveService` 是当前深 Module。当前只有一个 Maven module，不存在总体设计中的目标多模块布局。

### 4.2 未归档的内存中间态

核心在进程内使用 FrozenManifest、SourceFile、Flow、LockedFact、RoundOneAdmission、RoundTwoAdmission、FlowAdmission 和 GeneratedCandidate 等 package-private/private records。DefaultCodeToMarkdownAgent 还用进程内 Map 按 candidateId 保存 GeneratedCandidate，以便同进程 trace；进程重启后不保留这些对象。

归档边界只有一份 document.md 和七个 JSON sidecar。它不会保存完整 FrozenManifest、LockedFact、admission 对象、通用 NineSectionPlan、Proof DAG、语义原子台账或已接线 runtime receipt。CandidateArchiveService 能在新进程中从 sidecar validate/trace，不表示这些内存中间态已经持久化或可恢复。

### 4.3 仓库内邻接 seam（不计入 POC 主链）

| Seam | 归属阶段 | 当前 Interface 与现状 | 未接边界 |
| --- | --- | --- | --- |
| `RepositoryDiscoverer` | 当前目标 02/03 的历史输入 | `discover(DiscoveryRequest)`；目录级 Phase 1 诊断 | 冻结 Stage 02/五图/Candidate |
| `SourceAnalyzer` | 当前目标 04 的历史输入 | `analyze(AnalysisRequest)`；目录级 Phase 2 CodeFact/Condition/Gap | canonical Proof、Stage 04 assets、Candidate |
| `ModelRuntimeReceiptAdmission` | 当前目标 06/08 的历史输入 | `admit(policy, receipt)`；四个 runtime 字段逐字比较 | `generate`/CLI 接线、task identity、Adapter/Auth/upstream 分离 |

上表只记录 POC 邻接 seam；当前规范分别见 [02 应用与入口](02-discover-application-and-entries.md)、[03 五图](03-build-five-program-graphs.md)、[04 事实与证明](04-prove-code-facts.md)、[06 单流程解释](06-interpret-one-flow-at-a-time.md)和[08 九章与归档](08-build-nine-section-document-and-archive.md)。seam 的存在不表示对应目标阶段已实现。

## 5. 两条 POC 隔离测试路径

这两条路径验证同一个 POC core 的不同依赖边界，不是总体架构的两个分支。两者都缺少同一道 Fact semantic closure gate，所以路径运行成功不能产生可接受 Candidate。

### 5.1 Provider-free baseline 测试

```text
Flow Manifest
  -> 读取 schemaVersion/origin/rootName
  -> 解析 snapshot root real path
  -> 校验声明文件 size + SHA-256
  -> 校验 Evidence 行段 + excerpt SHA-256
  -> 校验已知 Fact/Evidence 引用并解析 LockedFact/anchors/flows
  -> [当前缺失] 逐 Fact atom semantic Evidence closure gate
  -> 从 LockedFact 按固定规则渲染九章
  -> 计算 candidateContentId/candidateId/Trace
  -> 生成八个 canonical 归档文件
  -> 原子安装 workspace
```

该路径不会创建或调用 `ModelProvider`。R1/R2 归档文件仍存在，但内容是 `schemaVersion: 1` 与空 `recordedResponses`。方括号所示 gate 是设计要求而非当前代码；当前 core 会直接越过它。因此执行成功只说明 plumbing 成功，不代表 Fact 可准入。固定 DepotHead 输入的合规处理应在该位置停止且不产生 Candidate。

### 5.2 Recorded R1/R2 测试

```text
Flow Manifest
  -> 同一套文件/Evidence hash 与已知 ID/Fact/Flow 校验
  -> [当前缺失] 逐 Fact atom semantic Evidence closure gate
  -> 对每个 Flow 计算 ModelTask(round=1)
  -> recorded R1 JSON 解析与身份/引用/anchor/proposal 准入
  -> 对同一 Flow 计算 ModelTask(round=2)
  -> recorded R2 JSON 解析与 proposal 完整性/basis 收窄准入
  -> 只保留 decision == KEEP 的 label
  -> 程序渲染九章
  -> 计算 Candidate/Trace
  -> 生成八个 canonical 归档文件
  -> 原子安装 workspace
```

这两个模型 round 是同一 Candidate 内部的 `FlowInterpretationRound`，不是两份产品候选。semantic closure 必须在 R1 前完成；模型引用已知 ID 或 R1/R2 一致不能补足缺失源码支持。核心可针对每个 Flow 调用一个能够按 `ModelTask` 路由的自定义 Provider；当前 CLI `RecordedProvider` 只允许每轮对象消费一次，因此 CLI 的 recorded 路径实际上只适用于一个 Flow。

## 6. Manifest、Evidence、Fact 与 Flow

### 6.1 Manifest 当前读取合同

| 位置 | 字段 | 当前校验 |
| --- | --- | --- |
| root | `schemaVersion` | 可转 int 且等于 1 |
| root | `origin.repositoryUrl`、`origin.commitSha`、`rootName` | 非空文本；不校验 commit 格式或 rootName 与目录一致 |
| `files[]` | `path`、`size`、`sha256` | path 为无反斜杠、无空白/`.`/`..` segment 的相对路径；真实目标必须仍在 snapshot；路径唯一；字节数和 64 位小写 hex SHA 精确匹配。Java core 允许指向 snapshot 内目标的 file symlink |
| `evidence[]` | `evidenceId`、`path`、`startLine/endLine`、`startColumn/endColumn`、`excerptSha256` | ID 唯一；文件必须已声明；正整数范围；摘录 SHA 精确匹配 |
| `lockedFacts[]` | `factId`、`kind`、`attributes` 或 legacy `lockedAtoms`、`evidenceIds` | Fact ID 唯一且 `factId`/`kind` 非空；attributes 必须是 object，但空 object 可通过；text scalar 可为空，array 必须非空且每项为非空 text；`evidenceIds` 可为空，非空项才检查为已知 ID；不检查 atom 语义支持 |
| `anchors[]` | `anchorId` | ID 唯一；当前实现忽略其余字段 |
| `flows[]` | `flowId`、`traceItemKey`、`factIds`、`evidenceIds` | Flow/trace key 唯一；两个 allowlist 非空且引用已知 ID；按 flowId 排序 |

当前 parser 不要求 JSON object 只有这些字段，也不加载 JSON Schema。路径行为必须按调用层区分：

- Java core 的 `readManifest` 使用会跟随链接的 `Files.isRegularFile`，所以可读取 Manifest symlink；snapshot root 先 `toRealPath()`，所以可跟随 root symlink；
- CLI 的 `existingRegularFile`/`existingDirectory` 使用 `NOFOLLOW_LINKS` 并显式检查 `Files.isSymbolicLink`，所以 `generate`/`baseline` 会拒绝顶层 Manifest/root symlink；
- core 的 `resolveInside` 对声明文件先判 regular file 再 `toRealPath()`，允许 symlink 的最终目标位于 snapshot 内，拒绝解析后逃逸；
- discovery/analysis walker 跳过 symlink entry，archive 拒绝 symlink workspace 和八个 artifact。

因此不能笼统写成“全部拒绝 symlink”。统一 Java Interface/CLI 策略、覆盖祖先目录链接和决定是否拒绝 snapshot 内 file symlink，是 ARCH-01/12 的后续硬化项。

Evidence 摘录摘要的实际算法是：以 UTF-8 读取整文件，取闭区间行段，以 `\n` 拼接并在末尾追加一个 `\n` 后做 SHA-256。`startColumn/endColumn` 会被记录和检查为正数，但不参与摘录字节切片或摘要计算。

Flow 的 `factIds` 与 `evidenceIds` 分别校验存在性；当前实现未强制每个 Flow 的 Evidence allowlist 覆盖该 Flow 所有 Fact 自带的全部 `evidenceIds`。Fact 自身只要求其非空 Evidence ID 已知，也不验证任何 atom 是否由这些声明 span 支持。因此当前只有 ID/reference 检查，不是 Flow Evidence reference closure，更不是 Fact→Evidence semantic closure。

### 6.2 LockedFact 保真边界

内部 `LockedFact` 精确保存：

```text
factId
kind
attributes: Map<String, List<String>>
evidenceIds: stable sorted set
```

读取时优先使用 `attributes`，缺失时兼容 legacy `lockedAtoms`，最终统一为 `Map<String,List<String>>`；当前实现不会因两个字段同时存在而执行 exact-field 拒绝。校验边界是：

- `factId` 与 `kind` 必须是非空文本；
- attributes/lockedAtoms 必须是 object，但 object 可以没有任何字段；
- 单个 textual attribute 会直接进入单元素 list，即使值是空字符串；
- array attribute 不能为空，且每个元素必须是非空文本；
- `evidenceIds` 的空 array 会成为空 set 并通过；只要存在条目，每项必须是非空文本并引用已知 Evidence。

这些只是当前 parser 事实，不是目标合同。保存在内存也不等于已由源码证明或进入读者内容：当前 core 没有 semantic closure gate；baseline Renderer 只识别少数 kind/key，recorded Renderer 主要使用准入后的 entity name/activity label，语义原子守恒同样未实现。

## 7. ModelTask 与当前准入

### 7.1 ModelTask

`ModelTask` 字段是：

```text
taskSpecId
flowSliceId = flowId
capsuleId
round = 1 | 2
```

每个 Flow 的 R1/R2 共用 `taskSpecId`、`flowSliceId` 和 `capsuleId`；R2 只改变 `round`。

### 7.2 R1 当前校验

- response 必须是一个非空 JSON object；
- `taskSpecId`、`flowSliceId`、`capsuleId` 必须匹配任务；
- 任意嵌套的已知 Fact/Evidence reference 字段不得引用 Flow allowlist 外 ID；
- `localEntities[]` 的 `localKey` 唯一，`anchorRef` 必须存在；`proposedName` 被清洗；
- `interpretations[]` 的 `proposalKey` 唯一，收集 Fact/Evidence basis，`proposedLabel` 被清洗。

当前核心不拒绝额外字段，不执行忽略目录中的 R1 JSON Schema，也未强制 proposal basis 非空。清洗会拒绝超过 80 字、控制字符或包含内部 identity token 的名称/label，但空 label 可以继续并在渲染时被忽略。

### 7.3 R2 当前校验

- `taskSpecId`、`flowSliceId` 必须匹配；当前没有再次核对 response `capsuleId`；
- 引用仍须在 Flow allowlist；
- 每个 R1 proposal 必须恰好被 review 一次，不能新增、重复或遗漏；
- R2 Fact/Evidence basis 只能是对应 R1 basis 的子集；
- `decision` 只要求非空文本，Renderer 仅把精确 `KEEP` 当作准入。

忽略目录的 R2 Schema 把 decision 限制为 `KEEP/NARROW/DROP/NEEDS_EVIDENCE`，但核心目前不加载该 Schema，因此不能表述为“完整 Schema 已执行”。

## 8. 身份计算

所有摘要都是 UTF-8 或原始文件字节的 SHA-256，小写 hex。

### 8.1 Snapshot

按文件相对路径排序：

```text
snapshotContentId = sha256(
  "mvp-frozen-snapshot-v1\n"
  + each(file.path + "\n" + file.sha256 + "\n")
)
```

当前 `repositoryUrl`、`commitSha`、`rootName` 和 Manifest 原始 SHA 不直接进入 `snapshotContentId`；snapshot identity 由声明文件路径/内容摘要定义。

### 8.2 Task 与 Capsule

Fact/Evidence ID 先去重并按字典序稳定排序：

```text
taskSpecId = sha256(
  "mvp-task-spec-v1\n" + flowId + "\n"
  + join("\n", factIds) + "\n"
  + join("\n", evidenceIds) + "\n"
)

capsuleId = sha256(
  "mvp-evidence-capsule-v1\n" + flowId + "\n"
  + join("\n", evidenceIds) + "\n"
)
```

### 8.3 Candidate

```text
candidateContentId = sha256(UTF-8 document.md)

candidateId = "candidate:" + sha256(
  "mvp-candidate-v1\n"
  + candidateContentId + "\n"
  + snapshotContentId + "\n"
  + sorted trace item key/locator/hash material
)
```

每个 Trace item 的 identity material 按 item key 排序；每个 locator 写入 `relativePath`、`startLine:startColumn`、`endLine:endColumn` 和 `excerptSha256`。Evidence ID 不进入 Trace locator material。`documentSha256` 当前等于 `candidateContentId`。

由于不是所有 Manifest/Fact 字段都直接进入上述身份，POC identity 只按当前算法解释，不能外推为长期完整来源身份。

## 9. POC 输出：一份 Markdown 与七个 JSON sidecar

这八个文件是 POC 持久化边界；第 4.2 节列出的中间态只在内存存在。归档 JSON 会递归按 object key 排序后序列化；数组保留构造顺序。workspace 首次成功后必须恰好包含：

| 文件 | 当前字段/内容 |
| --- | --- |
| `document.md` | UTF-8 九章正文；唯一 Markdown 产物 |
| `candidate.json` | `schemaVersion`、`candidateId`、`candidateContentId`、`documentSha256` |
| `evidence-pack.json` | `schemaVersion`、`candidateId`、`snapshotContentId`、`evidence[]`；每项含 `evidenceId` 与完整 locator/hash |
| `r1-interpretation.json` | 单 Flow 时保存 canonicalized recorded response object；多 Flow 时 `{schemaVersion, recordedResponses[{flowId,response}]}`；baseline 是空 wrapper |
| `r2-precision-review.json` | 与 R1 同一归档形态；baseline 是空 wrapper |
| `trace-index.json` | `schemaVersion`、`candidateId`、`items{traceItemKey:[locator...]}`；不保存 Evidence ID |
| `generation-receipt.json` | `schemaVersion`、Candidate 两个 ID、`snapshotContentId`、两个 `recordedRounds`；每轮含 round/responseCount/responseSha256 |
| `validation-receipt.json` | `schemaVersion`、Candidate 两个 ID、`documentSha256`、`valid`、`findings[]` |

生成时初始 validation receipt 固定为 valid。后续 `validate` 会以原子 replace 覆盖同名文件；因此它不是追加式历史。

## 10. Java Interface 精确现状

### 10.1 `CodeToMarkdownAgent`

| 方法 | Request | Result | 当前行为 |
| --- | --- | --- | --- |
| `generate` | `GenerationRequest(manifest, snapshotRoot, modelProvider)` | `CandidateReference` | recorded/scripted R1/R2；只存进程内 Candidate |
| `generateBaseline` | `BaselineGenerationRequest(manifest, snapshotRoot)` | `CandidateReference` | provider-free |
| `validate` | `CandidateReference(candidateId, candidateContentId, markdown)` | `ValidationReceipt` | 内存校验正文 hash 与九标题 |
| `trace` | `TraceQuery(candidateId, itemKey)` | `TraceView` | 从 `DefaultCodeToMarkdownAgent` 内存 map 查询 |

Interface 没有 `improve`、`inspect`、`discover`、`analyze`、archive 或 HTTP 方法。

`CandidateArchiveService` 是另一公开类，提供持久化 `archive(GenerationRequest)`、`archive(BaselineGenerationRequest)`、`validate(CandidateReference)` 和 `trace(TraceQuery)`。CLI 直接使用它，而不是 `DefaultCodeToMarkdownAgent`。

### 10.2 Trace/Validation records

- `TraceEvidence(relativePath,startLine,endLine,startColumn,endColumn,excerptSha256)`；
- `TraceView(candidateId,itemKey,evidence[])`；
- `ValidationReceipt(valid,candidateId,candidateContentId,documentSha256,findings[])`。

这些是 Java records，不是共享 JSON Schema。

## 11. 六个 CLI 精确现状

| 命令 | 必需参数 | 当前结果 |
| --- | --- | --- |
| `generate` | `--manifest --snapshot-root --workspace --recorded-r1 --recorded-r2` | 读取两个 JSON object、归档一个 recorded Candidate、stdout 打印 candidateId |
| `baseline` | `--manifest --snapshot-root --workspace` | 归档 provider-free Candidate、stdout 打印 candidateId |
| `trace` | `--workspace --candidate-id --item-key` | 从 archive 打印 item 与 locator；不读取源码 |
| `validate` | `--workspace --candidate-id` | 重建 Candidate reference，写/打印 JSON receipt；valid exit 0，invalid exit 1；不修正文 |
| `inspect` | `--repository-root` | 输出 `WALKING_SLICE_V0`、Java/XML 数量、routeCount 与 gaps 的目录诊断 JSON |
| `discover` | `--repository-root` | 输出 routes/direct calls/Mapper bindings/static UPDATE/gaps 的目录诊断 JSON |

没有 `analyze` CLI，也没有 HTTP。`inspect`/`discover` 接受任意本地目录并使用相对路径；它们不校验 Manifest、commit、file SHA 或 Evidence SHA，因此不得进入 Candidate、Markdown 或正式 Trace。

## 12. 程序与模型责任

| 工作 | 程序 | 模型 |
| --- | --- | --- |
| 选择文件、冻结 commit、校验字节 | 唯一责任 | 禁止 |
| 创建 locator/Evidence digest | 唯一责任 | 禁止 |
| 声明/解析 LockedFact | 人工 Manifest 声明；程序只做当前结构/hash/reference 校验，semantic admission 尚缺 | 只能引用，不能创建或补证 |
| 发现和切分 Flow | POC 为人工 Manifest；目标由程序 | 禁止 |
| 提出本地对象名、活动 label | 清洗、allowlist、准入 | R1 可提案 |
| 复核 R1 proposal 精度 | 验证完整性与 basis 不扩张 | R2 可给 decision |
| 章节、排序、事实/GAP disposition | 唯一责任 | 禁止 |
| Markdown 和技术 locator | 唯一责任 | 禁止 |
| runtime identity 判定 | 唯一责任 | 不得自我批准 |

模型不发现流程、不创建 locator、不渲染 Markdown。

## 13. DepotHead 固定小样：semantic audit 已拒绝

### 13.1 身份与范围

只读来源是 [flow-manifest.json](../../.workspace/mvp-depothead-audit-input/flow-manifest.json)：

- repository：`https://github.com/jishenghua/jshERP.git`；
- commit：`8c30ce7861570458920175e200bb2a6442713580`；
- root：`jshERP-8c30ce7861570458920175e200bb2a6442713580`；
- 3 个文件、5 个 Evidence、5 个 LockedFact、1 个 Flow；
- 文件、摘录 SHA 与已知 ID reference checks 通过，但 Fact→Evidence semantic closure 失败；
- 本文只读核对，未修改 Manifest/产物，未重新生成 Candidate、未执行客户代码。

三个文件是：

| 文件 | size | SHA-256 |
| --- | ---: | --- |
| `DepotHeadController.java` | 42241 | `1b45a173d5bd1a56888541da558db65c26c83e9758ed900bbada249dfd0bc55b` |
| `DepotHeadService.java` | 108586 | `e15d22b6aa8282b3af2719e1faa827cf8ccd5f65346b440f19f83f40e5e4fa49` |
| `DepotHeadMapper.xml` | 26978 | `eff37432ad2b5b4f62ea0faaea08a52be45ba2bc22071dadc4663bc58d645148` |

### 13.2 五个 declared Fact 的逐 atom 审计

下表只审计 Fact 自身明确引用的 Evidence span。源文件其他位置存在相似或补充字节，不能自动代偿未被该 Fact 引用的 span。

| Declared Fact | Declared Evidence | span 内支持的 atoms | span 内不支持的 declared atoms | semantic audit |
| --- | --- | --- | --- | --- |
| `fact:http-status-request` / `HTTP_ENTRY` | `evidence:http-entry`，controller 178–191，SHA `e36b0d32b06d94b503cc187cc45047c7beb4606d0aaa79f85596e5b7c56ad8c4` | `POST`、route suffix `/batchSetStatus`、参数 `status`/`ids`、target `DepotHeadService.batchSetStatus` | 完整 route `/depotHead/batchSetStatus`；class prefix `/depotHead` 在 line 43，span 外 | **FAIL**：route atom 不闭合 |
| `fact:reverse-audit-eligibility` / `STATE_GUARD` | `evidence:reverse-audit-guard`，service 750–776，SHA `9d784525f70f8fc4050cf0aced1fc28f022a83a6a00da2cb7f267fcdad510ed3` | requested `0`、current `1`、purchase `0`、失败 `BusinessRunTimeException` 均在 span 内 | 无 | **PASS**：只读独立审计支持全部 declared atoms |
| `fact:audit-and-stock-guard` / `STATE_AND_STOCK_GUARD` | `evidence:audit-and-stock-guard`，service 779–811，SHA `c81a88e4bd51bc734c83556f4379123ab62c64f21eb7dd51ee197df99d268583` | requested `1`；`forceApprovalFlag && !minusStockFlag` 以及受配置/单据类型约束的 stock check 在 span 内 | required current `0` 与失败 `BusinessRunTimeException` 在 lines 767–775，span 外 | **FAIL**：状态与失败 atoms 不闭合 |
| `fact:status-persistence-request` / `PERSISTENCE_CALL` | `evidence:status-persistence-call`，service 813–839，SHA `ad8385c994ffe31b81e0f240b2bfa2821251e157131ae887f1811c7a4a4e1ae3` | 声明 span 是日志与下一方法，未支持所声明的持久化语义 | target `DepotHeadMapper.updateByExampleSelective`、accepted IDs selection 与 assigned `status` 实际在 lines 798–803，span 外 | **FAIL**：全部核心 atoms 不闭合 |
| `fact:status-column-mapping` / `SQL_MAPPING` | `evidence:mapper-status-column`，mapper 385–489，SHA `a012fe8f9bf2b9caa089f85fe47c86ac13f048ac7d78d214815bf9731f34f189` | update id、table `jsh_depot_head`、column `status` 与 mapping `record.status` 均在 span 内 | 无 | **PASS**：只读独立审计支持全部 declared atoms |

审核状态 Fact 所缺的 lines 767–775 虽落在 `evidence:reverse-audit-guard` 的 span 内，但该 Fact 只引用 `evidence:audit-and-stock-guard`，不能借用另一个 Fact 的 Evidence。持久化字节同样虽存在于源码 lines 798–803，却不在 `fact:status-persistence-request` 声明的 span 内。

Flow 是 `flow:depot-head-batch-status`，Trace item 是 `activity:depot-head-batch-status`，allowlist 列出 5 Fact 与 5 Evidence。2 个 Fact 通过、3 个 Fact 失败，故整个 Flow semantic closure 失败。当前 core 仍会因 ID 全部已知而接受这个 Manifest，这是实现缺口，不是输入有效性证据。按当前算法得到的 taskSpecId `ceded384806b4077f193567de0628f3c5a89cc3705ddc5740ed00b9b7804e95b` 与 capsuleId `5599924e06d2f21a39d5d984c3839db2fbe814d008491fc889125d6486fac28b` 只标识既有 allowlist 材料，不证明其语义正确。

结论：该固定样例是“文件/摘录哈希有效、reference checks 可通过、semantic Evidence closure 失败”的拒绝输入。不得用于当前验收、Candidate 内容依据或 Reader Selection。本工作单元不修改其 Manifest/历史产物，也不重新生成。

### 13.3 修正冻结输入后才可用的目标投影

下表是拒绝草稿：它描述完成 Evidence span 修正、重新冻结并通过逐 atom semantic audit 后，NineSectionPlan 应怎样投影。对当前输入，生成必须在 Fact admission 前停止，表中内容一律不得作为已准入事实进入 Candidate。

| 章节 | 修正并准入后拟投影内容 | 明确 Gap/边界 |
| --- | --- | --- |
| 文档说明 | 固定 commit、冻结文件/Evidence/Flow 范围及通过的证据等级 | 修正前只有本审计记录，不生成本章；通过后仍是非全仓、非运行时结论 |
| 业务目标 | 有完整入口与处理证据后，说明对指定 IDs 的批量状态处理 | 更高层业务价值未由代码证明 |
| 业务对象 | 有闭合 persistence Evidence 后，描述 accepted IDs 对应记录与 `jsh_depot_head` 锚点 | “仓储单据”等业务名仍需独立解释准入 |
| 业务活动 | 接收参数 → 反审核 eligibility → 审核与条件库存校验 → accepted IDs persistence | 只有修正后通过的 Fact 可进入；其他分支不得补写 |
| 字段与维度 | `status`、`ids`、current/purchase status、强审核/负库存条件与写入字段 | 数值状态完整业务枚举待确认 |
| 对象关系 | Controller→Service、Service→Mapper、record field→table column | 每条关系分别需要其 Fact 声明 Evidence 闭包 |
| 指标口径 | 无已锁定公式或粒度 | “影响多少条”只能是问题，不能冒充指标 |
| 示例问题 | 仅使用修正后已准入对象、条件、字段和关系生成问题 | 当前失败 Fact 不能作为问题前提 |
| 待确认事项 | 状态码业务含义、对象业务名、事务结果、库存一致性、指标定义 | 每项指向缺失证据或外部责任方 |

修正后仍需先验证五个 Fact 的 kind、全部 attributes、条件、字面值和关系均有 semantic Evidence closure，再执行正文/技术依据/Gap/有理由排除的原子 disposition。当前既缺第一道准入门禁，也缺第二道守恒台账；九章结构有效不能替代任何一道。

## 14. 当前归档、验证与安全

### 14.1 Archive

- 先在 workspace 父目录创建 staging directory，写满八文件，再以 `ATOMIC_MOVE` 安装；平台不支持原子移动时失败，不降级为非原子复制。
- workspace 不存在或为空才允许首次安装；非空但不完全符合八文件布局时拒绝。
- 已存在完整 workspace 时重新生成；只有八个文件逐字节全部相同才返回同一 Candidate，任何差异都报 `WORKSPACE_CONFLICT`。
- archive 不覆盖一个不同 Candidate，也没有“latest”指针。

### 14.2 Validate 与 Trace

- archived `validate` 检查 workspace 恰好八文件、candidate sidecar 与请求 Candidate 的两个 ID/document hash、正文 SHA 和九个 H2 标题；
- 正文被篡改时只原子替换 `validation-receipt.json` 为 invalid，不修复 `document.md`；
- archived `trace` 校验 candidateId 和 locator JSON 的基本形态/相对路径，再返回存档 locator；它不重开 snapshot，不重算 source/excerpt SHA。

### 14.3 安全

- 模块不运行客户 Maven、插件、测试、脚本、应用、SQL 或 MyBatis runtime；
- CLI 只读本地路径和 recorded JSON，不联网；
- CLI 拒绝作为直接参数传入的 Manifest/root symlink；Java core 会跟随 Manifest/root symlink，并允许解析到 snapshot 内目标的 file symlink，只拒绝逃逸；
- discovery/analysis walker 跳过 symlink entry；archive 拒绝 symlink workspace/artifact；这些边界不能概括成统一的 no-symlink 合同；
- XML parser 接受标准 MyBatis DOCTYPE 声明，但 secure processing、external general/parameter entities、external DTD、external schema 与 EntityResolver 共同禁止外部解析。

## 15. 目标差异

以下都是当前代码缺口，不得写成已实现：

1. Fact→Evidence semantic closure 未实现：known Evidence ID、有效文件/摘录 SHA 与引用可解析不会逐 atom 证明 Fact；固定 DepotHead 输入因此被文档审计拒绝，当前 core 却不能自行阻断。
2. Java core 与 CLI 的 path/symlink 策略不一致；core 允许 Manifest/root symlink 和 snapshot 内 file symlink，CLI 拒绝顶层 Manifest/root symlink。需要定义统一 policy、祖先目录行为和直接 Java Interface 测试。
3. `LockedFact` parser 允许空 attributes object、空 textual attribute 和空 `evidenceIds` array；尚无目标 Fact Schema/Proof 准入规则。
4. runtime receipt admission 未接 `generate`、archive 或 CLI；`ModelRuntimeReceipt.taskSpecId` 也未由 admission 与任务策略核对。
5. `ModelRuntimePolicy.provider` 与 receipt `provider` 混用了 configured Adapter/Auth 与 observed upstream provider 语义；字段未分离时必须 fail closed。
6. R1/R2 核心 Schema 不完整：不执行 local JSON Schema、不拒绝额外字段；R1 basis 可空；R2 不复核 capsuleId、decision enum 只在外部 schema 中声明。
7. archived Trace 只读 locator，不重开冻结源码或验证 file/excerpt hash。
8. archived validate 主要检查八文件布局、candidate sidecar、正文 hash 和九标题；不校验 evidence pack、rounds、generation receipt、Trace closure、semantic Evidence closure、Fact coverage 或读者信息密度。
9. discovery/SourceAnalyzer 不自动生成冻结 Manifest、Evidence、LockedFact、Flow 或 Candidate；其目录输出未冻结。
10. 自动 Flow 编译、多 Flow 知识归并、完整 Proof 与语义原子守恒未实现。
11. `validation-receipt.json` 可替换，不是追加 lineage；没有目标的最小单进程execution state。
12. 没有 HTTP、远程 Git Adapter 或 live model Adapter。
13. Snapshot/candidate identity 没有直接覆盖所有 Manifest/origin/Fact 字段；长期算法需要版本化补全。

## 16. POC 证据与目标设计反馈

本节保留形成第 3.1 节设计变化的具体证据。它不为 POC 增加新能力，也不把历史产物升级为目标输入。

### 16.1 Hash/reference closure 不等于 semantic Evidence closure

文件 SHA、摘录 SHA、known Evidence ID、Flow allowlist、taskSpecId 和 capsuleId 都可以精确有效，同时 Fact 的语义仍不由其声明 span 支持。DepotHead 样例正是这个反例。Fact 准入必须逐 atom 检查声明 span 内字节并绑定确定性 Proof 或独立审计 proof；不能借用 Manifest 中未被该 Fact 引用的其他 Evidence，也不能由模型一致、九章结构或 Candidate identity 补齐。

### 16.2 结构正确不等于正文有内容

九个 H2、正确 SHA 和可查询 Trace 只能证明形状、字节与 locator。当前 recorded Renderer 主要渲染模型 entity/activity label，baseline Renderer 只读取预设 Fact kind/key；它们都可能遗漏已准入事实原子。下一阶段必须用 atom disposition 和 reader gate 验证内容，不以行数或文件大小代替。

### 16.3 `baseline-v2` 是历史产物

忽略目录 `.workspace/mvp-depothead-baseline-v2/` 在 Renderer 源码随后变化前生成。它可以帮助解释历史行为，但不代表当前源码、当前 candidate identity 或当前验收结果；本工作单元禁止重新生成它。任何当前验收都应从源码与冻结输入重新定义授权运行，而不是沿用该历史 document。

### 16.4 Provider 身份语义曾混用

[历史 failure receipt](../../.workspace/mvp-depothead-audit-candidate/model-run-failure-receipt.json) 同时出现 configured `codex_subscription` 与 observed `openai`，说明 Adapter/Auth/upstream provider 不能共用一个 `provider` 枚举。该手工审计 receipt 不等于 `ModelRuntimeReceiptAdmission` 已接生成链，也不能据此声称 R1 完成了运行时身份准入全链验证。

### 16.5 MyBatis DOCTYPE

标准 mapper DOCTYPE 本身不是失败条件；安全条件是绝不解析其外部 system ID、DTD、entity 或 schema。把 DOCTYPE 一律拒绝会损失正常 MyBatis 覆盖，把声明当成联网许可则违反安全不变量。

## 17. 测试矩阵

| 测试 | 直接覆盖 | 尚未覆盖 |
| --- | --- | --- |
| [ManifestEvidenceVerificationTest](../../src/test/java/com/linguan/codemd/mvp/ManifestEvidenceVerificationTest.java) | source/excerpt 漂移 fail closed | Fact atom semantic closure、Java core/CLI symlink 差异、origin identity、exact fields |
| [InterpretationAdmissionGateTest](../../src/test/java/com/linguan/codemd/mvp/InterpretationAdmissionGateTest.java) | 未知 Fact/Evidence、R2 basis 扩张 | 完整 R1/R2 Schema、runtime receipt 接线 |
| [NineSectionRenderingDeterminismTest](../../src/test/java/com/linguan/codemd/mvp/NineSectionRenderingDeterminismTest.java) | 九标题、内部 token、确定性 | 原子守恒、reader density |
| [DeterministicBaselineGenerationTest](../../src/test/java/com/linguan/codemd/mvp/DeterministicBaselineGenerationTest.java) | provider-free、Trace | 多 Flow 与通用 Fact kind |
| [CandidateArchivePersistenceTest](../../src/test/java/com/linguan/codemd/mvp/CandidateArchivePersistenceTest.java) | 八文件、JSON、幂等、validate tamper | 原子移动不支持、workspace conflict 直接负向测试 |
| [CodeMdCliPersistenceTest](../../src/test/java/com/linguan/codemd/mvp/CodeMdCliPersistenceTest.java) | recorded CLI + fresh Trace | 多 Flow recorded CLI、源码重验 |
| [CodeMdCliValidateTest](../../src/test/java/com/linguan/codemd/mvp/CodeMdCliValidateTest.java) | fresh validate、invalid receipt、不修正文 | sidecar 全闭包与追加 lineage |
| [TraceLocatorTest](../../src/test/java/com/linguan/codemd/mvp/TraceLocatorTest.java) | 精确存档 locator | snapshot reopen/hash |
| [RepositoryDiscovererTest](../../src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java) | route/call/Mapper/static UPDATE/gap/MyBatis DOCTYPE | Symbol Solver、动态 SQL、Manifest admission |
| [CodeMdCliDiscoveryTest](../../src/test/java/com/linguan/codemd/cli/CodeMdCliDiscoveryTest.java) | inspect/discover JSON 与相对身份 | frozen diagnostic promotion |
| [CodeFactAnalyzerTest](../../src/test/java/com/linguan/codemd/analysis/CodeFactAnalyzerTest.java) | exact direct call、condition identity、Gap | Proof、Candidate integration |
| [ModelRuntimeReceiptAdmissionTest](../../src/test/java/com/linguan/codemd/mvp/ModelRuntimeReceiptAdmissionTest.java) | 四字段 exact match；model/reasoning/sandbox mismatch | provider mismatch 负向测试、Adapter/Auth/upstream 分离、task/result identity、generate/CLI integration |

直接验证命令（只运行本模块直接覆盖测试）为：

```bash
mvn -q -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,DeterministicBaselineGenerationTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest,TraceLocatorTest,RepositoryDiscovererTest,CodeMdCliDiscoveryTest,CodeFactAnalyzerTest,ModelRuntimeReceiptAdmissionTest test
```

测试使用合成 fixture 或 recorded provider，不访问网络、模型或客户构建。

现有测试通过不能关闭准确度出口：测试只验证 Manifest 字节/摘录 hash 与 reference failure，没有逐 atom 解释源码语义，因此不会发现 DepotHead 三个 Fact 的 span 错配。该缺口需要独立的 semantic audit/Proof gate 及正反例测试。

## 18. 当前目标路线中的位置

本 POC 不再向一个临时“下一阶段”文件交接。仍有效的反馈已经分别进入：

- [01 冻结来源](01-freeze-source.md)：统一 path/symlink、snapshot identity 与立即持久化；
- [03 五张程序图](03-build-five-program-graphs.md)：把诊断式结构/调用信息升级为五个一等图产物；
- [04 事实与证明](04-prove-code-facts.md)：逐 atom semantic closure，拒绝空 LockedFact 或借用 Evidence；
- [05 流程与 Capsule](05-compile-business-flows.md)：自动入口根 Flow 和最小模型阅读包；
- [08 九章与运行归档](08-build-nine-section-document-and-archive.md)：plan-only renderer、完整 Trace、逐阶段资产和基于validated upstream publications的显式新stage执行。

后续实现只能按当前目标设计新增 production 能力；不得恢复人工 Flow Manifest、provider-free baseline 或旧八文件 archive 作为并列主线。

在 DepotHead 样例能再次作为阶段输入前，必须经另行授权修正并重新冻结 Evidence/Fact 引用，逐 atom 通过独立 semantic audit，再由未来 gate 验证；本工作单元不做该修复或生成。进入下一阶段时不得把一次 `inspect`/`discover` 输出复制为正式 Trace，也不得把当前拒绝 Manifest、historical baseline、baseline-v2 或 failure receipt 当作当前源码验收。
