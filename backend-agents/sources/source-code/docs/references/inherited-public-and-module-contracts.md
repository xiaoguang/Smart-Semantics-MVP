# 公共接口、源码位置与模块 Envelope

本文保留唯一 RepositoryAnalysisAgent、现有 request identities、SourceLocator/Excerpt 和 canonical envelope；不恢复旧全链 typed-hop Trace、NineSectionPlan 或强制 validation receipt 前置。来源阅读只需要短 ref 能准确回到冻结文件、行段和片段。

Java生产只走JDT，内部JavaCodeEngine Interface和历史精确读取见[引擎合同](../modules/java-code-engines/contracts-and-configuration.md)。工具路径只在宿主配置；不恢复JavaParser、严格图/Fact/Flow生产，不增加公开Agent方法。

## 1. 唯一公开 Agent 与当前能力

当前 RepositoryAnalysisAgent 已有以下五个实际方法：

~~~java
AnalysisRunReference start(AnalysisRunRequest request);
AnalysisRunReference executeStep(AnalysisStepExecutionRequest request);
RunInspection inspect(String runId);
ArtifactView artifact(ArtifactQuery query);
RenderedDocumentReference render(String runId);
~~~

start 创建 path-free QUEUED run；executeStep 按明确意图经已配置内部能力执行材料、Activity 或过程目标；inspect 读取已保存状态；artifact 读取一个命名业务 checkpoint 输出；render 只对已有历史九章 checkpoint 做确定性重排版。LocalRepositoryAnalysisAgent 及运行 coordinator 是当前实现；退役的 BusinessAnalysisWorkflow 不属于公共合同。

源码准备已通过这五个方法接入PREPARE_SOURCE意图、准备请求分支和output/query key；不增加第二个Agent。字段、轻量配置以及新来源选择以[CLI合同§5](../modules/source-preparation/cli-and-skill.md#5-运行组装及公共接口)和[数据合同](../modules/source-preparation/contracts-and-storage.md)为准。准备分支不要求尚未产生的sourceRegistration或伪Prompt；下列旧v2字段继续严格历史读取，不约束新版准备请求。

当前四技术操作已通过上述Agent保存固定源码结果；request v5/output v9和准确双分支上游已实现，见[技术CLI合同](../modules/technical-analysis/cli-and-runtime.md)与[最终验收](../supplements/technical-entry-evidence-acceptance-20260929.md)。原三操作v4/v8为历史。技术操作不要求模型配置，未增加Agent方法。

新Ontology属于[总体设计](../DESIGN.md#3-总体接力与分支)独立请求分支，已接通限定工程测试；真实样例尚待验。已新增ONTOLOGY、四执行意图和request v6/output v10，不借旧ANALYSIS的candidateRound、knowledgeCheckpoint或BusinessOutputArtifactKey。存储按repository-knowledge注册独立module2–5，本体输入只新R4+同R0，不做旧分析兼容。精确owner为[本体合同§11](../modules/ontology-recognition/contracts.md#11-版本安装和输入边界)，实施差距见[计划](../plans/ontology-recognition-implementation-plan.md)。下文旧v2/业务checkpoint合同只约束既有路径，不成为本体新输入要求。

完整目标仍保留 validate、trace 两个操作名，未来接入同一 Agent，不是第二 public seam：

- validate 可作为用户显式请求的独立检查，必要时重放相关算法；普通发布/读取/render 不调用它作为重复 gate。
- trace 的最小任务是按 run/report 与短 source ref 查回既有 SourceReference，返回准确 repository-relative file、startLine、endLine、snippet。不要求先建立 Candidate validation receipt、全图 hops、逐句 Proof 或新 trace ledger。本轮不增加其 Java 类型、HTTP route 或实现。
- 原文与本机绝对 Path 的边界不同：经授权的 repository-relative file/line/snippet 可供读者追溯，本机 workspace/root、秘密、Provider 原始响应不进入公开响应。

Java、CLI、未来 authenticated loopback HTTP 都复用同一 Agent。当前 CLI composition root 从预登记来源及固定配置创建请求，不允许任意 Provider/path 参数进入 analysis core。capture-local-git 是分析前独立维护适配器，路径只用于显式 capture，不是第二分析入口。HTTP、validate/trace 适配不是本轮业务质量验收前置。

现有单一YAML、两级并发、固定材料/独立模型批次及显式复用已实现，见[模型执行](../modules/model-job-execution.md)。历史业务批次读取M10，现行新取材state v4/output v5仅READING_MATERIALS_ONLY。新Activity目标repository-run-config-v3、model-job-execution-config-v4与analysis-run-output-v6由[集成合同](../modules/activity-explanation/integration-contracts.md)维护，不增加公共请求字段/Path，不覆盖历史state或因失败重跑JDT。

## 2. 启动、单步执行与运行引用

旧目录、冻结文本引用和关注问题属于内部ProcessDiscoveryRequest/私有配置，现行Step07 execution-config-v3已实现。新Activity/过程来源目标使用显式materialSource discriminator与完整Step05或历史M10引用；QUEUED run在Provider前固定选择，改变输入另起run。现行run-output-v5仅reading-only，目标v6保留knowledgeCheckpoint/reportCheckpoint字段及双owner；历史v3/v4严格读。详见[集成合同](../modules/activity-explanation/integration-contracts.md)。

以下是保留的旧v2请求合同，不因semantic profile改写；源码准备目标v3使用上文链接的明确分支，不向旧v2文件添加字段：

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

当前 RenderedDocumentReference 的字段为 runId、reportCheckpoint、documentSha256、sizeBytes；没有 nineSectionPlanId。artifact 的实际 ArtifactView 包含 runId、businessOutputArtifactKey、immutableReference、schemaVersion、mediaType、contentUtf8。查询按闭集业务输出名及 maxBytes 读取，拒绝任意 Path/glob/目录浏览，超容量整体拒绝，不截断。历史analysis-run-output-v3引入sourceRunId；历史业务v4区分sourceRunId、activityModelBatchId和输出run。现行新取材v5只支持READING_MATERIALS_ONLY，目标v6连接新Step05和Activity；材料、上游Activity与新过程各按自己的owner验证。读写器只接受合同规定的跨运行引用，不得将上游publication伪造为新run地址。

当前 Step07 已在同一 run-centric artifact 查询机制内提供语义过程输出：`repository-business-process-catalog.json` 是结构化权威结果，`process-coverage.json` 闭合 Activity/candidate/process 分母，`business-processes.md` 是回答“有哪些业务、每种业务怎样进行”的确定性主读物。46过程属于历史v1，本次选材起点为后续已审24候选目录；旧340 singleton也仅为历史对照。结构闭合仍不能代表生命周期语义通过。

现有程序侧 SourceReference 是：

~~~java
record SourceReference(
    String ref, String file, int startLine, int endLine, String snippet) {}
~~~

历史ref在所属BusinessMaterialSet内唯一；新Step05 sourceRef属于packet，模型S属于请求，跨包须连同owner映射，同一scope内的ref只定位一处；file 为冻结 repository-relative path，行号从 1 开始，snippet 为真实原文。该映射保存在独立source-refs.jsonl；九章正文仅显示短编号，不再附源码折叠区，不新增第十章或完整技术Trace系统。SourceReference本身的基本构造检查不能替代磁盘边界对来源/basis/bytes的验证。来源外置不改变模型已读材料或已审业务JSON。本次Step07在现有映射上新增确定性sources.md，主文档用相对链接/锚点导航；来源链接允许留空，不要求为此补齐、解释或增加专项验收。SourceReference结构不变，不要求回读Activity或重新导航。

同进程可复用已验证immutable views，磁盘边界验证来源/bytes。内容生成跨批只复用完整已审pair，孤立DRAFT不是产品；新单次选材/阅读decision按独立类型验证完整成功记录，不能伪装成pair。旧目录可作为新任务资料读取。Publisher不重放算法，纯render零Provider。补读来源在Discovery内映射为原SourceReference五字段，不新增证明层。

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

对已保留的 Step 01–05 技术 Module 和当前既有 artifact，每个模块至少一个 payload + receipt；payload 先固定，receipt 最后计算且排除自身。该规则不要求 Step06–08 的每个内部动作伪装成独立 ModuleArtifact。历史BusinessMaterialBuilder结果只读；新Activity直接读Step05，ActivityExplainer、Discovery与Publisher按总体设计保存有意义检查点；`business-processes.md` 由已归并 process catalog 确定性生成。当前只保留历史 `document.md` 的严格读取和确定性重渲染，本轮不设计新的Step08生产器。

引擎接线沿用这一机制并允许经合同登记的实际 payload 集：Step03 的 `java-code-index` 是 `PROGRAM_GRAPHS` module 7，不是新 step；当前Step03发布JDT index，Step04发布persistence-material-index-v1与其实际XML/SQL材料，Step05发布code-reading-material-set-v1；旧Step04 v4 NOT_PRODUCED accounting只保留精确历史读取。各步骤由现有step store生成receipt。Receipt 的 `payloadArtifacts[]` 只列实际 semantic payload。所有 exact-set allowlist、artifact policy、reader 与 fixture 必须和[引擎实际集合表](../modules/java-code-engines/contracts-and-configuration.md)一致；未执行增强不写空文件，已声明 AVAILABLE 的损坏产物仍失败。

发布/计算本身无法可靠完成时不安装success envelope，使用既有failure area。源码准备新增合同有所区分：完成检查并得到文件级问题是一个可安装的检查结果，安装后readiness仍可为NEEDS_DECISION/BLOCKED；它不宣称源码全部可用。具体issue与gapRefs对应见专属数据合同。安装本身失败仍不能伪造receipt。

既有外部failure形状：

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
2. Step 06–08 使用总体设计规定的简单检查点：材料在首次模型调用前保存，coordinator 立即保存每个已审 job 的私有不可变结果，再按稳定顺序安装 aggregate。Activity 有界并行、当前 Process 并行、跨 batch 验证读取、复用记录和 mixed-ownership aggregate 已实施；当前Step07已复用该执行机制承载catalog/candidate/reconstruction/consolidation，而不新增运行框架。不能循环安装不同 bytes，不增加公开 Agent 方法或状态 enum，进程内无需逐内部动作 fresh-reopen。
3. 跨进程/model batch 复用 Step 06–08 整个已审 job 时，新旧 batch 必须绑定同一完整 `materialsCheckpoint` reference，再比较覆盖完整实际内容、实际 Prompt、有效模型/输出配置与 Module 版本的 inputFingerprint，并核验磁盘身份/hash/schema/ref/basis。同名短 ref 不能代替这一检查。DRAFT 完成但 REVIEW 失败/未知只保留诊断；显式新 batch 重做完整 pair，旧结果不覆写。不恢复旧六/三/四模块 DAG、固定五/九 payload 或 52-output 顺序。
4. 公开 artifact 查询不接受 Path，也不返回截断内容。
5. Source excerpt/SourceRef 验证来源；Proof 证明受支持的 exact technical fact；两者都不自动证明模型自由业务文本，也不要求每个业务原子拥有 Proof。
6. runtime、Step05、Builder、ActivityExplainer、新BusinessProcessDiscovery/Publisher和旧九章均已存在。上游任意N覆盖、Activity v2和批次复用保持。本次只升级Step07：catalog/coverage/主Markdown v2、来源JSONL v1、新sources Markdown v1；narrative、规则用法和coverage原name同步到producer/reader/validator/registry/直接fixture。PROCESS_CATALOG和三个owner不变；来源附件走现有artifact闭集，无新公共Agent方法。不能让Step08回退原Activity重新发现过程。
