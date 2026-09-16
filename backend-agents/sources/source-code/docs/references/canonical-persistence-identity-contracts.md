# Canonical 持久化与身份合同（ACTIVE 技术附录）

本附录保留已实现 canonical bytes、typed identity、module/analysis-step publication 和原子存储公式。普通计算结果的传递遵循 [总体设计](../DESIGN.md)：owner 只计算一次，publisher 不重放算法；同进程可复用 immutable typed view，磁盘/新进程/import 边界才核验保存身份/hash/schema/ref/basis。

CanonicalJsonCodec、CanonicalArtifactPolicyRegistry、CanonicalModuleArtifactStore、CanonicalAnalysisStepArtifactStore 已有实现。下文 CanonicalRunManifestStore 签名/完成身份仅保留为既有技术提案的参照，不是本轮新增实现任务或业务交付前置；现有 runtime 使用其实际 run/checkpoint stores，不为此次减法再建存储系统。

## 1. Canonical JSON 与 JSONL

模型批次扩展见[执行设计 §7](../modules/model-job-execution.md#7-固定材料与独立模型批次已实现)。materialsCheckpoint沿用完整ModulePublicationReference；modelBatchId复用新AnalysisRunId，不增加typed-ID算法。私有state/execution/run-output版本与双归属检查由该节拥有；不改本附录canonical公式，也不重算或改写旧receipt。业务内容fingerprint排除新执行身份，持久化地址仍绑定真实生产者，两者不能混用。

`CanonicalJsonCodec` 是唯一公开的 canonical JSON 语法 seam：

~~~java
public final class CanonicalJsonCodec {
    public CanonicalJsonCodec();
    public ImmutableBytes encodeCanonical(JsonNode value);
    public JsonNode parseCanonical(ImmutableBytes canonicalUtf8);
}
~~~

- JSON 是 compact 严格 UTF-8，无 BOM、无无意义空白、无 final LF。
- object key 按解码后字符串的 unsigned UTF-8 bytes 字典序排序；array 保持合同定义的语义顺序。
- 不做 Unicode normalization。`"`、`\` 和 U+0000–U+001F 必须按 codec 固定规则转义；拒绝 unpaired surrogate。
- number 只接受 integral `JsonNode` 并写最短十进制形式；浮点、指数、leading plus/zero 和 negative zero 不是 canonical number。
- parse 开启 duplicate detection 和 trailing-token 拒绝；解码后用同一 encoder 重编，只有逐 byte 相等才接受。
- JSONL 每个非空行是一个 canonical JSON object，无空白行，每行及文件末尾都有唯一 LF。零行文件是 exact zero bytes，仅在 exact policy 允许时有效。

`ImmutableBytes.copyOf(byte[])` 在输入时防御性复制，`copyToByteArray()` 每次返回新副本；没有 unsafe wrap、stream 或 backing-array accessor。

## 2. Typed identity 与路径

所有可进入 filesystem address/root/reference 的 content ID 先按 typed value 解析。Wire grammar 固定为：

~~~text
<prefix>:<64 lowercase hex>
prefix = [a-z][a-z0-9-]{0,47}
hex    = [0-9a-f]{64}
~~~

大写 hex、额外 colon、slash/backslash、dot segment、Unicode 异形、空/过长 prefix 或不匹配 fixed prefix 必须在任何 filesystem lookup 前拒绝。固定 prefix 至少包括 `analysis-run`、`module-root`、`module-receipt`、`analysis-step-root`、`analysis-step-receipt`、`run-manifest` 和 `artifact-policy-registry`；类型不得互换。`Sha256Digest` 只接受 64 位 lowercase hex，无 prefix。

`AnalysisStepKey` 是 closed registry：

~~~text
verified-source-inventory -> 01-verified-source-inventory
application-discovery     -> 02-application-discovery
program-graphs            -> 03-program-graphs
proven-code-facts         -> 04-proven-code-facts
business-flows            -> 05-business-flows
flow-interpretation       -> 06-flow-interpretation
repository-knowledge      -> 07-repository-knowledge
nine-section-document     -> 08-nine-section-document
~~~

numeric/case alias 和未知值拒绝。目录只由 typed address 推导：

~~~text
runs/<encodedRunId>/steps/<NN-analysisStepKey>/modules/<NN-moduleKey>/
runs/<encodedRunId>/steps/<NN-analysisStepKey>/
runs/<encodedRunId>/run-manifest.json
runs/<encodedRunId>/validations/<encodedValidationId>/modules/<NN-moduleKey>/
~~~

private encoder 把已验证 ID 编码为 `<prefix>--<hex64>` 单 segment。调用者不能传 `Path`、相对路径或 basename。

## 3. 公开 store seam 与 records

~~~java
public interface CanonicalModuleArtifactStore {
    InstalledModulePublication install(ModuleInstallRequest request);
    ReopenedModulePublication reopen(ModulePublicationReference reference);
}

public interface CanonicalAnalysisStepArtifactStore {
    InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request);
    ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference);
}

// 批准的目标；当前 NOT IMPLEMENTED
public interface CanonicalRunManifestStore {
    InstalledRunManifest install(RunManifestInstallRequest request);
    ReopenedRunManifest reopen(RunManifestReference reference);
}

public interface CanonicalArtifactPolicyRegistry {
    static CanonicalArtifactPolicyRegistry load(
        ImmutableBytes canonicalDocument, CanonicalJsonCodec canonicalJson);
    ArtifactPolicyRegistryReference reference();
    CanonicalArtifactPolicy resolve(ArtifactPolicyKey key);
}
~~~

filesystem store constructor 参数顺序固定为 `runStore, canonicalJson, artifactPolicies, limits`。`RunStoreBootstrap.open(Path)` 和 `openForTest(Path)` 是 store bootstrap 唯一允许 `Path` 的 seam；返回不暴露 root/path accessor 的 opaque `RunStoreHandle`。

生产 `RunStoreBootstrap.open(Path)` 必须先以 NOFOLLOW 检查调用者所选路径的最后一个 component；它若是 symbolic link 必须拒绝，即使链接目标是目录。既有 parent component 可以是 alias，但 bootstrap 只允许在这里用默认、会跟随链接的 `toRealPath()` 解析一次既有 parent，再在该 real parent 下确定最终 run-store 目录，并且 `RunStoreHandle` 只保留这个 canonical real directory。所有后续 module、analysis-step、run-manifest 与 artifact store 都必须从该 canonical directory 派生，不能保留或再次使用调用者原始 lexical alias。`openForTest(Path)` 仍是独立 test-only seam，不构成生产 `open(Path)` 规则的豁免或替代。

已实现的精确 record component 顺序：

~~~text
ModulePublicationAddress (sealed)
  AnalysisStepModuleAddress(runId, analysisStepKey, moduleNumber, moduleKey)
  ValidationModuleAddress(runId, validationId, moduleNumber, moduleKey)
ModuleProducer(address, moduleVersion)

ArtifactReference(artifactId, sha256)
ArtifactPolicyRegistryReference(artifactId, sha256)
ArtifactPolicyKey(artifactType, schemaVersion)
CanonicalArtifactPolicy(key, artifactIdPrefix, mediaType, envelopeKind,
                        emptyJsonlAllowed, publicContentExposure)
ArtifactControls(toolchainSha256, profileSha256, schemaBundleSha256,
                 promptBundleSha256, artifactPolicyRegistryRef)

ModuleInstallRequest(address, moduleVersion, upstreamArtifacts, controls,
                     status, gapRefs, payloads)
CanonicalModulePayload(fileName, artifactType, schemaVersion, artifactId,
                       mediaType, canonicalUtf8)
ModulePublicationReference(address, moduleArtifactRoot, moduleReceiptId,
                           moduleReceiptSha256)
ArtifactDescriptor(fileName, artifactType, schemaVersion, artifactId,
                   mediaType, sizeBytes, sha256)
ModuleReceipt(schemaVersion, moduleReceiptId, address, moduleVersion,
              upstreamArtifacts, controls, status, payloadArtifacts,
              moduleArtifactRoot, gapRefs)
InstalledModulePublication(reference, disposition, artifactDescriptors)
VerifiedCanonicalPayload(descriptor, canonicalUtf8)
ReopenedModulePublication(reference, receipt, payloads)

AnalysisStepPublicationAddress(runId, analysisStepKey)
AnalysisStepInstallRequest(address, publicationProvenance,
  upstreamAnalysisStepReferences, controls, status, gapRefs,
  semanticPayloads, archiveManifestSpecification)
CanonicalAnalysisStepPayload(fileName, artifactType, schemaVersion,
                             artifactId, mediaType, canonicalUtf8)
AnalysisStepPublicationReference(address, analysisStepArtifactRoot,
  analysisStepReceiptId, analysisStepReceiptSha256)
AnalysisStepReceipt(schemaVersion, analysisStepReceiptId, address,
  publicationProvenance, upstreamAnalysisStepReferences, controls, status,
  semanticArtifacts, archiveManifest, analysisStepArtifactRoot, gapRefs)
InstalledAnalysisStepPublication(reference, disposition,
  semanticArtifactDescriptors, archiveManifestDescriptor)
ReopenedAnalysisStepPublication(reference, receipt, semanticPayloads,
  archiveManifestPayload)
~~~

所有 list component 构造时 `List.copyOf`。`ModuleCompletionStatus`、`ModuleInstallDisposition`、`CanonicalMediaType`、`CanonicalEnvelopeKind` 和 `PublicContentExposure` 都是 closed enum。

`CanonicalArtifactPolicyRegistry` 只从 exact `artifact-policy-registry-v2` canonical document 加载。顶层字段为 `schemaVersion, artifactPolicyRegistryId, policies`；policy 字段为 `artifactType, schemaVersion, artifactIdPrefix, mediaType, envelopeKind, emptyJsonlAllowed, publicContentExposure`，按 `(artifactType, schemaVersion)` UTF-8 bytes 严格递增且唯一。Envelope 只有 `MODULE_ARTIFACT_JSON | STANDALONE_JSON | CANONICAL_JSONL | RAW_UTF8`；exposure 只有 `METADATA_ONLY | PATH_FREE_COMPLETE_UTF8`。

## 4. Binary framing、artifact ID 与 roots

所有字符串先严格 UTF-8 编码；SHA-256 输出 32 raw bytes，对外 hex 始终 64 位小写：

~~~text
U32BE(n)      = exactly 4-byte unsigned big-endian n
U64BE(n)      = exactly 8-byte unsigned big-endian n
frame(bytes)  = U64BE(bytes.length) || bytes
rawSha(hex64) = exact 32 bytes decoded from lowercase hex64

descriptorBytes(d) =
    frame(UTF8(d.fileName)) ||
    frame(UTF8(d.artifactType)) ||
    frame(UTF8(d.schemaVersion)) ||
    frame(UTF8(d.artifactId)) ||
    frame(UTF8(d.mediaType)) ||
    U64BE(d.sizeBytes) ||
    rawSha(d.sha256)

descriptorListRoot(domain, prefix, descriptors) =
  prefix + ":" + lowercaseHex(SHA-256(
    frame(UTF8(domain)) ||
    U32BE(descriptors.size) ||
    concat(frame(descriptorBytes(d)) for d in descriptors)))
~~~

descriptor 先要求 fileName/artifactId 唯一，再按 fileName 的 unsigned UTF-8 bytes 严格递增。Module root 固定为：

~~~text
descriptorListRoot("canonical-module-artifact-root-v1", "module-root", payloadDescriptors)
~~~

Analysis-step root 固定为：

~~~text
descriptorListRoot("canonical-analysis-step-artifact-root-v1", "analysis-step-root", semanticDescriptors)
~~~

Module root 排除 module receipt；analysis-step root 排除 archive manifest、step receipt、root manifest 和 archive module publication。不允许另一套目录 hash、JSON-array hash 或实现自选算法。

四种 envelope identity 精确为：

~~~text
MODULE_ARTIFACT_JSON:
  digest = SHA-256(frame(UTF8("canonical-module-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(canonicalJson(envelopeWithoutArtifactId)))

STANDALONE_JSON:
  digest = SHA-256(frame(UTF8("canonical-standalone-json-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(canonicalJson(documentWithoutArtifactId)))

CANONICAL_JSONL:
  digest = SHA-256(frame(UTF8("canonical-jsonl-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(exactCanonicalJsonlBytes))

RAW_UTF8:
  digest = SHA-256(frame(UTF8("canonical-raw-utf8-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(exactUtf8Bytes))

artifactId = policy.artifactIdPrefix + ":" + lowercaseHex(digest)
~~~

JSON 公式删除且只删除顶层 `artifactId`；不保留 null 占位。JSONL 和 RAW 直接使用 exact bytes。Policy registry identity 为：

~~~text
artifactPolicyRegistryId = "artifact-policy-registry:" + lowercaseHex(SHA-256(
  frame(UTF8("canonical-artifact-policy-registry-id-v2")) ||
  frame(canonicalJson(documentWithoutArtifactPolicyRegistryId))))
~~~

## 5. Completion identity

~~~text
moduleReceiptId = "module-receipt:" + lowercaseHex(SHA-256(
  frame(UTF8("canonical-module-receipt-id-v1")) ||
  frame(canonicalJson(moduleReceiptWithoutModuleReceiptId))))

analysisStepReceiptId = "analysis-step-receipt:" + lowercaseHex(SHA-256(
  frame(UTF8("canonical-analysis-step-receipt-id-v1")) ||
  frame(canonicalJson(analysisStepReceiptWithoutAnalysisStepReceiptId))))

// 已实现的 mixed-ownership 校验摘要
runManifestId = "run-manifest:" + lowercaseHex(SHA-256(
  frame(UTF8("canonical-run-manifest-id-v1")) ||
  frame(canonicalJson(runManifestWithoutRunManifestId))))
~~~

`Without...Id` 删除且只删除命名的顶层 self ID；不删其他字段，不加入该文件自身 SHA。Receipt/manifest file SHA 始终对含已计算 ID 的完整 canonical bytes 求普通 SHA-256。

## 6. 原子 install/reopen

对已选择 canonical store 的既有 artifacts 保留以下存储边界：

1. 验证 exact policy、schema、address/filename registry、upstream refs、controls 和预算，序列化 canonical bytes。
2. 在同文件系统不可见 staging 内先写 payload，再写 receipt，并 force。
3. 核对实际写入 size/hash/descriptor/root/receipt bytes 后 atomic move；不支持原子安装则 fail closed。这是存储完整性检查，不运行任何 graph builder、Fact enumerator、Flow compiler 或 Capsule projector。
4. 目标存在且逐 byte 相等为 ALREADY_INSTALLED；不同为 collision，不覆盖。
5. 外部 reopen 接受完整 typed reference；NOFOLLOW 验证 exact file set，拒绝 symlink、缺失/多余文件、引用及 bytes 漂移。
6. 正常可信进程可传 owner 已验证的 immutable typed objects/bytes；不要求逐内部模块 fresh reopen，也不把它增加为内容准入层。

Module 文件集是 receipt 声明 payloads 加 module-receipt.json。Analysis-step 集是命名 semantic payloads 加 registered receipt；modules/ 不计入 semantic root。已有目录、版本、identity preimage 不在本文重新设计。

## 7. 当前模块与目标减法

Step01 request-admission/source-index/publish、Step02 application-profile/http-entry/mapper-catalog/publish、Step03 五图/publish、Step04 candidates/proofs/publish、Step05 flow-compiler/capsule-projector/publish 的可观察产物保留。

Step05 使用既有文件保存一个 EntryContext 关系模型和对应有界 Capsule 投影；新增字段遵循所属 record 的新 schema version，不创建另一 module registry、文件系统或身份框架。Step06 的 BusinessMaterialBuilder/ActivityExplainer 与当前 Step07 BusinessProcessDiscovery/Publisher 使用同一 store/checkpoint 机制保存 Activity cards、candidate dispositions、reviewed processes 和唯一 consolidated catalog。旧 ProcessExplainer、旧九章生产器及 generate 路线已退役；历史 checkpoint 严格读取能力保留，不新建第三套文件系统或恢复固定 52/57 输出要求。

已批准的[模型 job 保存](../modules/model-job-execution.md#5-保存身份与失败)复用上述 canonical/原子能力：私有运行目录保存每个已审 job，coordinator 按稳定材料/候选顺序一次安装 aggregate；不把固定 publisher 放入并发 worker。Provider/job 独立 journal namespace 补足现有 ModelRuntimeIdentityV1 不含账户的边界，稳定 job key 与单次提交登记留在程序侧，不进入模型 packet。ActivityUse、详细 stage/rule/certainty 和 process catalog 是真实 output shape 变化，实施时必须显式升版并一次同步 producer/reader/validator；并发值、排队时间和完成次序不改变业务内容 fingerprint 或数组语义顺序，identity framing 不重定义。这不是自动恢复或 uncertain started 请求重放协议。

## 8. 不变量与实际边界

- identity-significant bytes/refs/controls 改变产生新身份，不能覆写旧 publication。
- ID 相同只说明 canonical preimage 相同，不说明业务完整、语义真实或已人工确认。
- 原文、prompt、raw response、秘密及 repository path 继续受既有 artifact exposure policy 保护；无路径且可完整公开的类型才使用 PATH_FREE_COMPLETE_UTF8。
- 显式跨 run 复用校验保存输入/Prompt/model/output/Module 的 fingerprint 及边界完整性；不恢复不确定的 started Provider 调用。
- 同 run 崩溃接管和 terminal repair 延后；本轮不增加 journals、hash chains、recovery readers 或完整 run manifest 新机制。

改变 domain separator、framing、prefix、ordering、self-exclusion 或目录推导属于独立明确的 versioned 持久化变更。本轮不改这些公式，只删除普通调用链的重复计算和过强上下文准入条件。
