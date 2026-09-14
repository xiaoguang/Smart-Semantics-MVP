# 公共接口、源码位置与模块 Envelope

本文保留唯一 RepositoryAnalysisAgent、现有 request identities、SourceLocator/Excerpt 和 canonical envelope；不恢复旧全链 typed-hop Trace、NineSectionPlan 或强制 validation receipt 前置。来源阅读只需要短 ref 能准确回到冻结文件、行段和片段。

Java工具替换采用[内部JavaCodeEngine Interface](../modules/java-code-engines/contracts-and-configuration.md)，不新增公开Agent方法。YAML工具路径只属于宿主配置；新run保存有效engine/version以避免跨引擎错误复用。Step03/04技术增强可用性按[接入设计](../modules/java-code-engines/integration-and-javaparser.md)处理，不以旧固定图文件集合阻断JDT源码材料。

## 1. 唯一公开 Agent 与当前能力

当前 RepositoryAnalysisAgent 已有以下五个实际方法：

~~~java
AnalysisRunReference start(AnalysisRunRequest request);
AnalysisRunReference executeStep(AnalysisStepExecutionRequest request);
RunInspection inspect(String runId);
ArtifactView artifact(ArtifactQuery query);
RenderedDocumentReference render(String runId);
~~~

start 创建 path-free QUEUED run；executeStep 按明确目标经已配置内部能力执行；inspect 读取已保存状态；artifact 读取一个命名业务 checkpoint 输出；render 复用完整已审报告 JSON 确定性排版。LocalRepositoryAnalysisAgent、BusinessAnalysisWorkflow 及运行 coordinator 已存在，不再以过期 JavaDoc 将接口说成只有 start/inspect。

完整目标仍保留 validate、trace 两个操作名，未来接入同一 Agent，不是第二 public seam：

- validate 可作为用户显式请求的独立检查，必要时重放相关算法；普通发布/读取/render 不调用它作为重复 gate。
- trace 的最小任务是按 run/report 与短 source ref 查回既有 SourceReference，返回准确 repository-relative file、startLine、endLine、snippet。不要求先建立 Candidate validation receipt、全图 hops、逐句 Proof 或新 trace ledger。本轮不增加其 Java 类型、HTTP route 或实现。
- 原文与本机绝对 Path 的边界不同：经授权的 repository-relative file/line/snippet 可供读者追溯，本机 workspace/root、秘密、Provider 原始响应不进入公开响应。

Java、CLI、未来 authenticated loopback HTTP 都复用同一 Agent。当前 CLI composition root 从预登记来源及固定配置创建请求，不允许任意 Provider/path 参数进入 analysis core。capture-local-git 是分析前独立维护适配器，路径只用于显式 capture，不是第二分析入口。HTTP、validate/trace 适配不是本轮业务质量验收前置。

已批准的[模型 job 执行配置](../modules/model-job-execution.md#2-唯一配置入口与精确-yaml)归现有 CLI 组合根：v2 loader/CLI 从同一 `--config` YAML 的 `sourceAnalysis.modelJobs` 读取全局/Provider 并发、路由、模型与认证引用，并已落地有界并行。它不增加公开请求字段或 Path，调整并发不强制重跑 JDT。[模型执行设计第 7 节](../modules/model-job-execution.md#7-固定材料与独立模型批次已实现)的固定材料/独立 model batch、state v3、`analysis-run-output-v3`、跨 batch reader 与 `--reuse-from-model-batch` 已实现；模型模式直接重开 M10，不调用 Builder 或 JDT。

## 2. 启动、单步执行与运行引用

以下是已冻结字段，不因 semantic profile 改写：

~~~text
AnalysisRunRequest
  schemaVersion=analysis-run-request-v2
  sourceRegistrationId
  frozenRepositoryRequestRef {artifactId, sha256}
  profileBundleRef {artifactId, sha256}
  resourceBudgetRef {artifactId, sha256}
  toolchainRef {artifactId, sha256}
  schemaBundleRef {artifactId, sha256}
  promptBundleRef {artifactId, sha256}
  organizationRegistrySeedRef              // required nullable ArtifactReference
  artifactPolicyRegistryRef {artifactId, sha256}
  candidateSeriesRef {artifactId, sha256}
  readerCandidateRound: ROUND_1 | ROUND_2
  parentCandidateRef                        // required nullable ArtifactReference
  approvedFindingRefs[]                     // ArtifactReference, sorted by artifactId

AnalysisRunRequestReference
  analysisRunRequestId
  sha256

AnalysisStepExecutionRequest
  schemaVersion=analysis-step-execution-request-v1
  analysisStepExecutionRequestId
  analysisRunRequestRef: AnalysisRunRequestReference
  targetAnalysisStepKey: application-discovery | program-graphs | proven-code-facts |
                         business-flows | flow-interpretation | repository-knowledge |
                         nine-section-document
  finalAnalysisStepKey: target key or a later semantic key in the closed registry
  upstreamAnalysisStepPublications[]: AnalysisStepPublicationReference
    // exact continuous registry prefix before targetAnalysisStepKey

AnalysisRunReference
  schemaVersion=analysis-run-reference-v1
  runId
  analysisRunRequestId
  analysisStepExecutionRequestId           // required nullable; non-null only for executeStep
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult: COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE |
                  INCOMPLETE_COVERAGE | null
  failureCode                              // required nullable; non-null iff FAILED
~~~

`analysis-run-request-v2` 的 artifact-valued 字段都是完整 `ArtifactReference`，不得内联内容或只给 ID。`organizationRegistrySeedRef` 与 `parentCandidateRef` 字段始终存在但可空；其他单值 reference 非空。`ROUND_1` 要求 parent=null 且 approved findings 为空；`ROUND_2` 要求 parent 和 findings 按既有 candidate-series/同源/同 controls 规则 fresh-reopen。产品候选轮不是 Step 06 的 DRAFT/REVIEW；删除 mandatory R0 不删除这两个产品交付轮次。

这些既有 identity 公式直接继承，不交给实现 work unit 重新选择：

~~~text
analysisRunRequestId = "run-request:" + lowercaseHex(SHA-256(
    frame(UTF8("analysis-run-request-id-v2")) ||
    frame(canonicalJson(request))))

analysisStepExecutionRequestId = "analysis-step-execution-request:" + lowercaseHex(SHA-256(
    frame(UTF8("analysis-step-execution-request-id-v1")) ||
    frame(canonicalJson(requestWithoutAnalysisStepExecutionRequestId))))
~~~

`analysis-run-request-v2` wire 本身没有 self ID，因此第一式没有 self-exclusion；`AnalysisRunRequestReference.sha256` 是同一完整 request bytes 的 SHA-256。第二式删除且只删除顶层 self ID，并覆盖其余完整 canonical request。`runId` 不是业务内容 ID：core 每次 `start` 或 `executeStep` 都生成新的 256-bit 随机值，编码为 `analysis-run:<64 lowercase hex>`，并在任何目录写入前检查 collision；caller/Adapter 不得提供、派生或复用它。相同业务 request 可以有多个独立 execution。所有 Adapter 必须在创建 run 目录前完成 version、lineage 与 identity 校验。

在已批准的 model-batch 目标中，`modelBatchId` 直接就是每次新 `start` 分配的这个 `AnalysisRunId`，不新造第二类 ID；`sourceRunId` 则保留材料生产者。运行 batch 与产品 Reader Candidate Round 正交：在完整最终候选产生前，为处理必要 job 而显式新建 batch 仍属同一候选轮；已有完整最终候选后的内容修正才必须使用 `ROUND_2`。

新参数首选放入现有 versioned profile/prompt 内容；若改变 public request 字段则升级其 schema，不修改 v2 allowlist。人工确认不属于本轮新增机制，模型 review 不能伪造人工批准。

## 3. 实际输出、过程主读物与简单来源查阅

当前 RenderedDocumentReference 的字段为 runId、reportCheckpoint、documentSha256、sizeBytes；没有 nineSectionPlanId。artifact 的实际 ArtifactView 包含 runId、businessOutputArtifactKey、immutableReference、schemaVersion、mediaType、contentUtf8。查询按闭集业务输出名及 maxBytes 读取，拒绝任意 Path/glob/目录浏览，超预算整体拒绝，不截断。`analysis-run-output-v3` 在不改公开 publication reference 形状的前提下增加 `sourceRunId`：material checkpoint 继续按 source run 验证，Activity/Knowledge/Report 按输出 owner（即 `modelBatchId`）验证。读写器接受这两种精确身份，不得将上游 publication 伪造为新 run 地址。

目标 Step07 在同一 run-centric artifact 查询机制内增加语义过程输出，不增加公开方法：`repository-business-process-catalog.json` 是结构化权威结果，`process-coverage.json` 闭合 Activity/candidate/process 分母，`business-processes.md` 是回答“有哪些业务、每种业务怎样进行”的确定性主读物。当前同名旧 process/knowledge 输出尚未满足这一合同，不能用 340 个 singleton records 冒充目标 catalog。

现有程序侧 SourceReference 是：

~~~java
record SourceReference(
    String ref, String file, int startLine, int endLine, String snippet) {}
~~~

ref 在 BusinessMaterialSet 内全局唯一，同 ref 只能定位一处；file 为冻结 repository-relative path，行号从 1 开始，snippet 为真实原文。该映射保存在独立source-refs.jsonl；九章正文仅显示短编号，不再附源码折叠区，不新增第十章或完整技术Trace系统。SourceReference本身的基本构造检查不能替代磁盘边界对来源/basis/bytes的验证。来源外置不改变模型已读材料或已审业务JSON。

同进程可复用已验证 immutable source/context/checkpoint views。磁盘、新进程或导入验证保存 hash/schema/ID/ref/basis，实际读取源码时验证对应 bytes；publisher 不运行 compiler/projector 重证业务关系。跨 batch 复用只接受完整已审、已验证并原子保存的整个 model job；孤立 DRAFT 不是可复用产品。纯 render 不改正文、不调用 Provider、不重扫源码。

## 4. 唯一源码位置合同

ApplicationDiscovery、ProgramGraphs、BusinessFlows、FlowInterpretation 与 NineSectionDocument 共用以下两个 versioned value records，不得另造 `path:line`、basename locator 或拼接 excerpt：

~~~text
SourceLocatorV1
  fileId                                   // exact inventory file ID
  path                                     // canonical repository-relative path
  startByte                                // zero-based UTF-8 byte offset, inclusive
  endByteExclusive                         // zero-based UTF-8 byte offset, exclusive
  startLine, startColumn                   // one-based
  endLine, endColumn                       // one-based, exclusive

SourceExcerptV1
  locator: SourceLocatorV1
  rawUtf8                                  // exact strict-UTF-8 decode of locator range
  rawUtf8Sha256                            // lowercase SHA-256 of exact bytes
~~~

约束逐字保留：

- `0 <= startByte < endByteExclusive <= source size`；byte slice 落在 UTF-8 code-point boundaries。
- 重新编码 `rawUtf8` 必须逐字节等于 slice；坐标由同一 inventory line index 唯一推出。
- 一个 record 只表示一个文件内连续 span；不连续证据拆为多个 excerpt，按 `path,startByte,endByteExclusive` 排序。
- 禁止插入 ` + `、`...`、省略号或合成空白后声称 raw。
- schema 只需要位置时仍嵌入完整 locator；声称 source evidence 或显示 excerpt 时使用完整 excerpt。

面向模型只投影短 ref 与原文，不发送 file/line/hash；程序侧 SourceReference 及其 basis 回到以上 exact records。短 ref 不是第二套 source identity。

## 5. ModuleArtifact、ModuleReceipt 与 ModuleFailure

普通计算模块 JSON payload 使用同一 `ModuleArtifact<T>` envelope。Analysis-step 最终 publisher 的 narrow exception、四类 envelope policy、identity、descriptor/root 与 install/reopen 算法仍由 [Canonical 附录](canonical-persistence-identity-contracts.md) 定义。

~~~text
ModuleArtifact<T>
  schemaVersion: STRING                    // required, exact artifact schema
  artifactType: STRING                     // required, registered enum value
  artifactId: STRING                       // required, canonical computed value
  producer:
    address:                               // exact tagged ModulePublicationAddress
      kind: ANALYSIS_STEP | VALIDATION
      runId: STRING
      analysisStepKey?                     // only ANALYSIS_STEP
      validationId?                        // only VALIDATION
      moduleNumber: INTEGER
      moduleKey: STRING                    // exact compiled lowercase key
    moduleVersion: STRING
  upstreamArtifacts[]:
    artifactId: STRING
    sha256: HEX64
  controls:
    toolchainSha256: HEX64
    profileSha256: HEX64
    schemaBundleSha256: HEX64
    promptBundleSha256: HEX64?             // required nullable
    artifactPolicyRegistryRef:
      artifactId: STRING
      sha256: HEX64
  completion:
    status: SUCCEEDED | SUCCEEDED_WITH_GAPS
    gapRefs[]: STRING                      // sorted unique
    failureRef: STRING?                    // required null for installed success
  payload: T

ModuleReceipt
  schemaVersion=module-receipt-v1
  moduleReceiptId
  address: ModulePublicationAddress
  moduleVersion
  upstreamArtifacts[] {artifactId, sha256}
  controls
  status: SUCCEEDED | SUCCEEDED_WITH_GAPS
  payloadArtifacts[] {fileName, artifactType, schemaVersion, artifactId,
                      mediaType, sizeBytes, sha256}
  moduleArtifactRoot
  gapRefs[]
~~~

对已保留的 Step 01–05 技术 Module 和当前既有 artifact，每个模块至少一个 payload + receipt；payload 先固定，receipt 最后计算且排除自身。该规则不要求 Step06–08 的每个内部动作伪装成独立 ModuleArtifact。BusinessMaterialBuilder、ActivityExplainer、BusinessProcessDiscovery 内部阶段、BusinessProcessPublisher 与 BusinessReportPublisher 按总体设计保存少量有意义检查点；`business-processes.md` 由已归并 process catalog 确定性生成，最终 `document.md` 由已验证 paragraph JSON 与 source refs 确定性组装。

引擎接线沿用这一机制并允许经合同登记的实际 payload 集：Step03 的 `java-code-index` 是 `PROGRAM_GRAPHS` module 7，不是新 step；JDT Step03 为 index 一项、JDT Step04 为 v4 NOT_PRODUCED accounting 一项，分别再由现有 step store 生成 receipt。Receipt 的 `payloadArtifacts[]` 只列实际 semantic payload。所有 exact-set allowlist、artifact policy、reader 与 fixture 必须和[引擎实际集合表](../modules/java-code-engines/contracts-and-configuration.md#51-实际产物集合与现有存储复用)一致；未执行增强不写空文件，已声明 AVAILABLE 的损坏产物仍失败。

失败不安装 success envelope，只在既有外部 failure area 写：

~~~text
ModuleFailure
  schemaVersion=module-failure-v1
  address: ModulePublicationAddress
  attemptedUpstreamArtifacts[]
  controls
  failureCode
  safeDetail
  failureId
~~~

`safeDetail` 不含 secret、absolute path、raw prompt/response 或未授权源码。Failure 不冒充 Gap；Gap 是成功/带 Gap 成功的可解释限制，failure 是未安装成功 publication 的诊断。

## 6. 不变量

1. Step 01–05 继续遵守既有 canonical framing、identity、store、source locator 与 Module publication；业务简化不削弱这些技术产物。
2. Step 06–08 使用总体设计规定的简单检查点：材料在首次模型调用前保存，coordinator 立即保存每个已审 job 的私有不可变结果，再按稳定顺序安装 aggregate。Activity 有界并行、当前 Process 并行、跨 batch 验证读取、复用记录和 mixed-ownership aggregate 已实施；目标 Step07 复用该执行机制承载 catalog/candidate/reconstruction/consolidation，而不新增运行框架。不能循环安装不同 bytes，不增加公开 Agent 方法或状态 enum，进程内无需逐内部动作 fresh-reopen。
3. 跨进程/model batch 复用 Step 06–08 整个已审 job 时，新旧 batch 必须绑定同一完整 `materialsCheckpoint` reference，再比较覆盖完整实际内容、实际 Prompt、有效模型/输出配置与 Module 版本的 inputFingerprint，并核验磁盘身份/hash/schema/ref/basis。同名短 ref 不能代替这一检查。DRAFT 完成但 REVIEW 失败/未知只保留诊断；显式新 batch 重做完整 pair，旧结果不覆写。不恢复旧六/三/四模块 DAG、固定五/九 payload 或 52-output 顺序。
4. 公开 artifact 查询不接受 Path，也不返回截断内容。
5. Source excerpt/SourceRef 验证来源；Proof 证明受支持的 exact technical fact；两者都不自动证明模型自由业务文本，也不要求每个业务原子拥有 Proof。
6. runtime、Step05 EntryContext、Builder、ActivityExplainer、旧 ProcessExplainer 与 BusinessReportPublisher 均已存在。Activity v2 `missingEntryKeys`/required `unexplainedEntries` 及独立批次复用保持不变。目标 Step07 的 ActivityUse、详细 stage/rule/certainty、catalog/coverage 和 `business-processes.md` 尚未进入 Schema/Java/resource；实现时必须以新版本一次贯通 producer、reader、validator 和直接测试，不兼容双读，也不能让 Step08 回退到 raw Activity 重做过程发现。
