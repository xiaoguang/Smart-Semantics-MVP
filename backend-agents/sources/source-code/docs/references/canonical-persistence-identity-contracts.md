# Canonical 持久化与身份合同（ACTIVE 技术附录）

> **适用范围：** 第 1–6 节继续约束 Step 01–05 已保留的技术 publication 和任何显式选择使用既有 canonical store 的 artifact；它们不要求新的 Step 06–08 每个内部动作都拥有 ModuleArtifact/receipt/fresh-reopen。四个业务深 Module 的检查点、inputFingerprint 与保存时机以 [总体设计](../DESIGN.md) 为准。旧 Step 06–08 registry 只记录当前实现/迁移事实，不是 target DAG。

本附录保留 Source Code Analysis Agent 已经验证的 canonical bytes、typed identity、module/analysis-step publication 与 atomic store 合同。本轮只简化业务语义职责，**不改这些公式、存储语义或已有身份**。

> 实现现状：`CanonicalJsonCodec`、`CanonicalArtifactPolicyRegistry`、`CanonicalModuleArtifactStore` 和 `CanonicalAnalysisStepArtifactStore` 已有生产纵切；`CanonicalRunManifestStore` 仍是批准的未实现目标。Step 06 新 module/payload registry 也是 `NOT IMPLEMENTED`。不得从本附录反推完整运行已经存在。

## 1. Canonical JSON 与 JSONL

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

// 批准目标；当前 store 尚未实现
runManifestId = "run-manifest:" + lowercaseHex(SHA-256(
  frame(UTF8("canonical-run-manifest-id-v1")) ||
  frame(canonicalJson(runManifestWithoutRunManifestId))))
~~~

`Without...Id` 删除且只删除命名的顶层 self ID；不删其他字段，不加入该文件自身 SHA。Receipt/manifest file SHA 始终对含已计算 ID 的完整 canonical bytes 求普通 SHA-256。

## 6. 原子 install/reopen（保留技术 publication）

对 Step 01–05 已保留 Module 和现有 analysis-step store，使用同一语义：

1. 在写入前验证 exact policy、schema、address/filename registry、upstream refs、controls、canonical bytes 和 budget。
2. 在同文件系统不可见 sibling staging 中按 payload 先、receipt 后写入并 force。
3. 在 staging 中 fresh reopen，重算 size/SHA/artifact ID/descriptor/root/receipt ID，再 atomic move。不支持 atomic move 则 fail closed。
4. 目标已存在且逐 byte 一致返回 `ALREADY_INSTALLED`；任何差异是 collision，不覆盖。
5. `reopen` 只接受完整 typed reference；NOFOLLOW 检查精确文件集，拒绝 symlink、嵌套目录、缺失/多余文件、引用或字节漂移。
6. 这些技术 publication 的下游只能消费 `Reopened*Publication` 的 defensive immutable bytes；新的 Step 06–08 在同进程可以直接传 immutable typed object，并在有意义检查点保存，不适用逐内部动作重开要求。

Module 目录的文件集等于 receipt 声明的 payloads + `module-receipt.json`。Analysis-step 公开文件集等于该步 exact semantic payloads + 可选 archive manifest + registered receipt；`modules/` 不计入 step root，也不被当作 semantic payload 遍历。

## 7. Module registry：保留技术目标与迁移事实分开

下列是当前已编译 module key。Step 01–05 继续作为目标技术 Module；Step 06–08 的旧 key 仅是迁移输入：

~~~text
verified-source-inventory: 01=request-admission, 02=source-index, 03=publish
application-discovery:     01=application-profile, 02=http-entry, 03=mapper-catalog, 04=publish
program-graphs:            01=code-structure, 02=call-graph, 03=control-flow,
                           04=data-flow, 05=evidence-graph, 06=publish
proven-code-facts:         01=candidates, 02=proofs, 03=publish
business-flows:            01=flow-compiler, 02=capsule-projector, 03=publish
repository-knowledge:      01=admission, 02=knowledge-merge, 03=publish
nine-section-document:     01=planner, 02=renderer, 03=trace, 04=archive
~~~

当前 Step 06 的 R0/R1/R2 九模块、Step 07 admission/knowledge-merge/publish 和 Step 08 planner/renderer/trace/archive 在迁移前仍是实现事实，但不是新业务目标规范。目标只保留 BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher 四个深 Module；它们写总体设计列出的简单检查点，不另建新的六模块 registry。后续切换不得使用 alias、dual reader、第二 namespace 或旁路 POC。Step 01–05 的既有 canonical 机制不变。

## 8. 不变量与未实现边界

- 源、upstream reference、controls、model raw response 或任何 identity-significant payload 变化都生成新身份，不覆盖旧 publication。
- ID 相同只表示 canonical preimage 相同，不表示语义真实、完整或已人工确认。
- `METADATA_ONLY` 是含 source locator/excerpt、prompt、raw response、secret 或 repository path 材料的默认 exposure。只有 schema 能静态保证 path-free 且可完整公开时才用 `PATH_FREE_COMPLETE_UTF8`。
- Active v0 不从半成 staging/module 恢复。已完整安装的上游 publication 可在新 execution 中显式重用；不是 same-run resume。
- Run manifest store、完整 runtime、NineSection archive 路径仍须后续实现，本文不把目标当现状。

任何修改 domain separator、framing、prefix、ordering、self-exclusion 集或目录推导都是独立、显式的 versioned 持久化设计变更；不可作为语义框架迁移的顺手修改。
