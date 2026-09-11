# 既有公共接口、源码位置与模块 Envelope 合同（ACTIVE）

> **适用范围：** 本附录的 source locator、public Interface、artifact access 与产品候选轮次继续有效。逐 Module envelope/receipt/fresh-reopen 只约束已保留的 Step 01–05 技术 Module 和当前既有 artifact；旧 NineSectionPlan、coverage ledger、Step 06–08 module DAG 是迁移事实，不是新的业务目标。Step 06–08 以 [总体设计](../DESIGN.md) 的四个深 Module、简单 JSON/JSONL 检查点和同文档 SourceRef 为准，不得从本附录恢复固定 52 项或重型内部 publication。

> 本附录把语义框架重写前 `DESIGN.md` 中仍有效的技术合同迁回 ACTIVE 文档。它与 [Canonical 持久化与身份合同](canonical-persistence-identity-contracts.md) 共同约束实现；不是新的存储/API 设计，也不恢复旧 R0、P1/P2、TraceV4 或 57 项语义拓扑。

本轮语义变更不修改 production Java 或现有 Schema。下面标为现有 version 的字段必须逐字段保留；若后续实现确实需要因新 semantic knowledge/Trace 增删 wire 字段，必须升级拥有该 record 的 schema version，并在独立 work unit 冻结 Schema、identity preimage 与 migration。不得在 v1/v2 上静默加字段或用 unknown-field 宽松解析。

## 1. 唯一产品外部 Interface

目标 seam 是“启动或显式创建一次可信分析执行，并按 identity 观察其业务产物”。单一公开 `RepositoryAnalysisAgent` 隐藏 run 目录布局、manifest/root join、validation 顺序、renderer 隔离、单进程执行状态与查询安全；Java、CLI 和 loopback HTTP 都是同一个 core 的 Adapter，不得各自遍历 filesystem 或重写规则。独立 `LocalGitCommitCaptureAdapter` 是显式本地维护入口，不是第二个 analysis Interface；它只交出 rootless registration/content refs。当前代码已实现 `start` 与 `inspect`；其余操作在有对应 durable run 结果后接入同一 Interface，不能以永远抛出“未实现”的 placeholder 方法抢占 public contract。

完成业务执行与查询链后的目标 Interface 如下；当前实现只开放其中已有 durable behavior 的
`start` 与 `inspect`，其余方法不会以 placeholder 形式提前出现：

~~~java
public interface RepositoryAnalysisAgent {
    AnalysisRunReference start(AnalysisRunRequest request);
    AnalysisRunReference executeStep(AnalysisStepExecutionRequest request);
    RunInspection inspect(String runId);
    ArtifactView artifact(ArtifactQuery query);
    RenderedDocumentReference render(String runId);
    ValidationReceipt validate(String runId);
    TraceView trace(TraceQuery query);
}
~~~

方法语义保持：

- `start` 只接收 exact `analysis-run-request-v2` 的 content-addressed refs 与 `sourceRegistrationId`，创建新 runId 和 `QUEUED` 状态；拒绝 v1/unknown fields，不接收 Path。当前进程 single worker 按 Step 01→08 执行，进程结束不接回该 run。
- `executeStep` 只接收 exact `analysis-step-execution-request-v1`，验证连续 upstream publications 与同一 frozen basis，创建新 runId，只执行 target 到显式 final step。它不修改旧 run、不继承旧 runtime/model 状态、不重做有效上游。
- `inspect` 只投影 lifecycle、nullable result、step/module receipts、coverage/Gap/failure summaries 和可查询 descriptors；不返回 secret、raw prompt/reasoning 或 Path。
- v0 `artifact` 只接受 `runId + BusinessOutputArtifactKey + maxBytes`。key 是已完成业务路线的
  closed 名称（材料、活动、过程、知识、报告、来源或验证），不是任意 artifact ID、basename、glob
  或 filesystem selector；core 由该 key 选择既有 checkpoint payload，fresh-reopen 后重验 SHA/schema/
  size，再返回预算内完整 UTF-8 bytes。超限整体拒绝，不截断。该小投影不开放 prompt、raw model
  response 或技术内部 artifact；未来若需要通用 artifact browser，另行版本化。
- `render` 定位该 run 唯一已验证 business-report.json 与 source-refs.jsonl，确定性重渲染并核对 document bytes；不产生第二份业务草稿、不补写 run、不读 source/registry/model。v0 的 `RenderedDocumentReference` 只交出
  `runId`、report checkpoint、重渲染后的 `documentSha256` 与 `sizeBytes`；正文仍由后续已验证
  artifact query 读取。NineSectionPlan 只描述尚未迁移的旧实现。
- `validate` fresh-process 式重算闭包；相对 Candidate/AnalysisResult/publication/manifest 是 read-only，但 validation payload/receipt 按既有独立 validation store 幂等安装。execution failure 与 `INVALID` 结果分开。
- `trace` 只在 Candidate 有匹配 validation receipt 后返回完整、预算内、path-free typed hops；重新通过 source registry/root 验证源码 span，不截断 hops 或暴露本机 Path。

### 1.1 Adapter 一一映射

下表规定 adapter 存在时的语义对齐，不要求 Java、CLI 与 HTTP 在业务核心验收前同时完成；authenticated loopback HTTP 是后续接入。

| Core | CLI | authenticated loopback HTTP |
| --- | --- | --- |
| `start(request)` | `analyze --request <analysis-run-request-v2.json>` | `POST /analysis-runs` → `202` + `{runId,lifecycleState:"QUEUED"}` |
| `executeStep(request)` | `analyze-step --request <analysis-step-execution-request-v1.json>` | `POST /analysis-step-executions` → `202` + new run |
| `inspect(runId)` | `inspect --run <id>` | `GET /analysis-runs/{runId}` |
| `artifact(query)` | `artifact --run <id> --artifact-id <id> ...` | `GET /analysis-runs/{runId}/artifacts/{artifactId}` |
| `render(runId)` | `render --run <id>` | `POST /analysis-runs/{runId}:render` |
| `validate(runId)` | `validate --run <id>` | `POST /analysis-runs/{runId}:validate` |
| `trace(query)` | `trace --run <id> --reader-item <key> --max-hops <positive-int> ...` | `GET /analysis-runs/{runId}/trace?...` |

两个 `--request` 路径只是 Adapter 的本地 transport，不进入 core record。HTTP 只绑定 loopback 且认证。Java/CLI/HTTP 对同一 request 返回相同 canonical error code、identity、ordering 与 content SHA；Adapter 不改变 budget 或并发启动第二 worker。public Interface/CLI/HTTP 没有 resume 方法或 route；恢复仍只由未改动的 recovery TODO 管理。

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

新语义参数首选放入现有 versioned `profileBundleRef/promptBundleRef` 所指内容；若必须改变本 request 的字段集，升级 request version，不能修改 v2 allowlist。可选人工确认的唯一入口固定为 profile bundle 的 JSON pointer `/semanticFramework/humanConfirmationBundleRef`，其值是 required-nullable `ArtifactReference`，默认 `null`；它属于 profile bundle 自身的下一版 closed schema，不是 `AnalysisRunRequest` 或 `AnalysisStepExecutionRequest` 的新字段。新确认要生效时，调用方创建引用新版 profile bundle 的 `analysis-run-request-v2`，再用 `start` 或 `executeStep` 启动新 run；不得修改既有 run。

## 3. 观察、Artifact 与公开 Trace response

### 3.1 精确字段

~~~text
RunInspection
  runId
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult?                          // non-null exactly for FINISHED through Step 08
  analysisRunRequestId
  analysisStepExecutionRequestId?          // non-null exactly for executeStep
  failureCode?                             // non-null exactly when FAILED
  repositoryCompletionEligible
  repositoryCoverageLedgerId               // required nullable; Step 08 planner-owned final coverage
  repositoryCoverageClosed
  analysisStepReceipts[]                   // analysis-step order
  moduleReceipts[]                         // canonical module address order
  artifactDescriptors[]                   // canonical ArtifactLocation order
  gapSummaries[]                           // gapId order, safe fields only
  failureSummaries[]                       // failureId order, safe fields only

ModulePublicationAddress                  // sealed tagged union; never a path
  ANALYSIS_STEP {runId, analysisStepKey, moduleNumber, moduleKey}
  VALIDATION {runId, validationId, moduleNumber=1, moduleKey=run-validator}

ArtifactLocation                          // sealed tagged union; never a path
  ANALYSIS_STEP_MODULE {address: AnalysisStepModuleAddress}
  VALIDATION_MODULE {address: ValidationModuleAddress}
  ANALYSIS_STEP_PUBLICATION {address: AnalysisStepPublicationAddress}
  RUN_MANIFEST {address: RunManifestAddress}

ArtifactQuery
  runId
  businessOutputArtifactKey                 // closed v0 business checkpoint file key
  maxBytes                                  // positive all-or-error; no truncation

ArtifactView
  runId
  businessOutputArtifactKey
  immutableReference: ArtifactReference
  schemaVersion
  mediaType: application/json | application/x-ndjson | text/markdown
  contentUtf8                               // full verified content; never truncated

RenderedDocumentReference
  runId
  candidateRef: ArtifactReference
  nineSectionPlanId
  documentArtifactId
  documentSha256
  sizeBytes
  mediaType=text/markdown
  validationReceiptId?
  rerenderMatched=true

TraceQuery
  runId
  readerItemKey
  expectedCandidateId?
  maxHops                                // required positive; all-or-error

TraceView
  schemaVersion=trace-view-v1
  runId
  candidateRef: ArtifactReference
  validationReceiptRef: ArtifactReference
  readerItemKey
  traceRecordRef: ArtifactReference
  traceKind                              // registered Step 08 trace projection kind
  sourceValidationState: NO_SOURCE_HOPS |
                         ALL_SOURCE_HOPS_REOPENED_AND_HASH_VERIFIED
  hopCount
  hops[]: PublicTraceHopV1
  allHopsReturned=true

PublicTraceHopV1
  IDENTITY {identityKind,id}
  ARTIFACT_REFERENCE {referenceRole,artifactRef}
  SOURCE_EXCERPT {fileId,startByte,endByteExclusive,startLine,startColumn,
                  endLine,endColumn,rawUtf8,rawUtf8Sha256}
  SECTION {sectionKey}
  TEMPLATE {templateKey}
~~~

Step 08 新的 internal `ReaderTraceRecord/TracePath` 通过上述五种 path-free public hop 投影；不复制旧 P1/P2 hop 强制链。若新的 trace kind 无法在 `trace-view-v1` 的已注册 closed enum 中表达，必须升级为 `trace-view-v2`，不能把 unknown value 偷塞进 v1。内部 noFlow path 可以没有 Flow/Proof；公开 source excerpt 仍必须 fresh-validate 后才返回。

`ArtifactQuery` 必须同时有 non-null runId+closed business key，且 maxBytes 为 positive。v0 只允许
finished run 的四个已验证 business checkpoint 中预注册的文件；无 key、未知 key、受保护内容或容量
不足均整体拒绝。来源引用是公开报告的有效组成部分，但 prompt、raw model response 与未迁移的 raw
trace 永远不属于该 key 集合；TraceView 仍是 source hops 的唯一 path-free 公开投影。

`TraceView` 要求匹配 Candidate validation receipt；`maxHops` 必须容纳完整 record，否则拒绝而非截断。每个 source hop 重验 file SHA、span 和 excerpt SHA，只投影 fileId/coordinates/excerpt bytes，不返回本机或仓库 Path。

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

面向模型的 DRY packet 可以投影 `file/startLine/endLine/shortExcerpt/localHandle`，但 program-only reverse binding 必须回到以上 exact records；短摘录不是第二套 source identity。

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

对已保留的 Step 01–05 技术 Module 和当前既有 artifact，每个模块至少一个 payload + receipt；payload 先固定，receipt 最后计算且排除自身。该规则不施加给新的 BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher 内部动作。它们按总体设计保存少量有意义检查点，最终 document.md 由已验证 paragraph JSON 与 source refs 确定性组装，不把 Markdown 先包进内部 Module JSON。

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
2. Step 06–08 的四个深 Module 使用总体设计规定的简单检查点：材料在首次模型调用前保存，完成的 activity/process 随即保存，进程内可直接传 immutable typed object，不要求逐内部 Module fresh-reopen。
3. 跨进程复用 Step 06–08 检查点只比较覆盖实际内容、实际 Prompt、有效模型/输出配置与 Module 版本的 inputFingerprint；不恢复旧六/三/四模块 DAG、固定五/九 payload 或 52-output 顺序。
4. 公开 artifact 查询不接受 Path，也不返回截断内容。
5. Source excerpt/SourceRef 验证来源；Proof 证明受支持的 exact technical fact；两者都不自动证明模型自由业务文本，也不要求每个业务原子拥有 Proof。
6. 当前 Schema/Java 未迁移。字段或 enum 变化必须显式 version；不存在兼容 alias、dual reader 或“缺字段按旧语义”。
