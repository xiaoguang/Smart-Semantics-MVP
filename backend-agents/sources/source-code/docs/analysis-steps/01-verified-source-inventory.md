# 已验证源码清单

> [总体设计](../DESIGN.md)；固定 key：verified-source-inventory，目录：steps/01-verified-source-inventory/。本步骤运行时模型调用为 0。

## 1. 为什么存在

后面的代码结构和业务解释必须来自一份确定的源码。Step01 一次核对已经选定、注册的离线快照：哪个 commit、哪些文件、每个文件的准确 bytes 和可读位置。后续通过这份不可变视图导航，不反复扫描工作树，也不在每层重新发现仓库。

它只保证来源身份和可读范围，不解释业务。来源错误会污染所有结果，因此路径、字节和身份错误必须停止；某段代码的业务含义未知则不属于这里的来源失败。

## 2. 输入与显式 capture 边界

分析 core 接收 exact analysis-run-request-v2 的 sourceRegistrationId 和 frozenRepositoryRequestRef 等内容寻址控制引用。被引用的 frozen-repository-request-v2 固定 expected origin、完整 40 位 revision、capture/snapshot manifest、inventory scope 和验证 profile/policy/budget refs。public request/response 不携本机 Path；private registry 将 registration 解析为 opaque read-only source handle。

LocalGitCommitCaptureAdapter 是本步骤之前独立、显式授权的维护入口，不由分析 worker 自动触发：

~~~text
source-analysis capture-local-git --repository-path <absolute-local-path> --commit <exact-40-lower-hex>
~~~

它只读受约束 Git CLI plumbing 的 commit/tree/blob 原始对象，不读工作树、不执行客户代码、不联网。bootstrap 固定受信 Git executable 和 git-dir；ProcessBuilder 不经 shell，清空环境后只放安全必需项，禁 system/global config、lazy fetch、prompt、pager、optional locks 和 replace objects。private 空配置目录/文件不改变实际用户 HOME。只接受完整 commit object ID，不接受 branch/tag/short SHA。

通过 NUL 分隔 tree 枚举完整 tracked regular files；100644/100755 blobs 原样保存。symlink 120000、gitlink/submodule 160000、未知 mode、缺对象、promisor/partial clone、alternates、config include、replace/graft/shallow 等不在安全 capture 合同中的来源直接失败，不尝试 fetch/fallback。git-dir/object/config 逐段 NOFOLLOW，前后 identity 不漂移；不执行 hooks、filters、客户 Maven、插件或应用。capture 的 stderr 只进私有诊断。

## 3. 一次验证与文件分母

三个内部模块维持现有职责：

| 模块 | 输入 → 工作 → 输出 |
| --- | --- |
| FrozenRequestAdmission | 明确 request、registration、manifest/controls → exact 字段与范围检查 → 已准入输入 |
| source-index 模块 | 已准入清单、opaque handle → 路径/类型/size/SHA/编码/行索引检查 → verified source index |
| VerifiedSourceInventoryPublicationSpecifier | 已完成不可变输入/index → 序列化三项 semantic payload → 步骤 publication |

首次 source admission 对每个 regular file 做预算预检、NOFOLLOW/size 校验、流式 hash 和读取后 identity 检查。路径必须 canonical repository-relative，拒绝绝对路径、点段、混用分隔符、重复或越界。所有 regular files 都计数、hash；二进制本身不是 fatal，也不能静默删除。

strict UTF-8 且没有禁用字节的文本标 ANALYZABLE_TEXT，建立稳定 byte/line index；其余 NON_ANALYZABLE_MEDIA 保留原始 bytes、mode、size、digest，textEncoding/lineIndexDigest 为 null，不送 Java/XML parser。空 regular file 合法；空声明 inventory 不合法。

保存分母必须满足：

~~~text
trackedRegularFileIds = verifiedRegularFileIds ⊎ unverifiedRegularFileIds
verifiedRegularFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds
successful admission ⇒ unverifiedRegularFileIds = ∅
~~~

COMPLETE_CAPTURE 的 regular-file ID 集必须精确等于 capture manifest；分片应两两不交并完整覆盖，改变顺序/分片大小不能改变语义身份。BOUNDED_PATH_SET 永远不满足 repositoryCompletionEligible，不能靠补扫工作树升级为完整。

首次验证后的 bytes/index 作为不可变视图复用；保存各 module/step 产物以便观察。publisher 只序列化、检查必要 type/ID/ref/budget 并原子安装，不能再次扫描或重做 source inventory。读取新磁盘内容时核验该实际边界；同进程重复消费已验证 immutable bytes 不重新验证全仓。

## 4. 输出与真实样本

| 文件 | 唯一用途 |
| --- | --- |
| source-input.json | rootless 冻结输入与实际控制 refs |
| verified-snapshot.json | snapshot/revision、scope、regular/text/media counts 与完整性 |
| source-inventory.jsonl | 每文件 canonical identity、path、mode、size、SHA、disposition、nullable line index |
| verified-source-inventory-receipt.json | 上游 roots、controls、artifact descriptors、状态 |

以下为**真实样本的阅读投影，不是完整 wire、重新生成的产物或可重放 golden**：

~~~json
{
  "originRevision": "8c30ce7861570458920175e200bb2a6442713580",
  "scope": "COMPLETE_CAPTURE",
  "verifiedRegularFileCount": 719,
  "nextUse": "Locate AccountHeadController#getFinancialBillNoByBillId and its related source"
}
~~~

现有离线捕获/运行已经保存完整 719 文件；本轮只读现有事实，没有新 capture。DepotHead 八文件子集可用于局部 walkthrough，不能代替这份完整 source denominator。文件 digest/identity 必须由真实 bytes 计算，文档短 ID 不能装成 strict golden。

## 5. 下一消费者怎样不返工

Step02 用同一 verified source view 读取必要配置和 Java 发现入口。Step03 建图，Step05 根据已有位置提取连贯方法/条件/调用/返回代码；每个片段都能回到这个 snapshot。Step06 只封装 Step05 上下文，不重新发现文件。

source registry 只按明确 snapshot/file identity 读清单成员，不能 walk root、按 basename 搜索、补文件或换 commit。需要重新打开磁盘文件时验证 size/hash/编码以及切片与定位一致；不是让每个消费者再跑完整 Step01。非文本仍留在分母但不送 parser。

## 6. 技术合同、保存与复用

已发布的 frozen-repository-request-v2、verified-snapshot-v2、SourceLocatorV1/SourceExcerptV1 及身份公式保持真实含义；新增字段必须在所属 record 明确升级，不在旧 version 静默加字段。exact 公共输入及唯一 locator 见 [公共接口附录](../references/inherited-public-and-module-contracts.md)，canonical bytes、framing、root/receipt 与原子 install 见 [持久化附录](../references/canonical-persistence-identity-contracts.md)。

M3 提供恰三个 semantic payload，AnalysisStep store 计算 root 并最后创建 receipt；module 目录与 module receipts 保留，不计入 public semantic root。不能预报自己的未生成 receipt 形成自引用，不覆盖旧 publication。进程内可把受信不可变结果及其 basis 交给下一 owner；磁盘/新进程/导入则经 typed reference 校验 exact file set、identity/hash/schema/ref/basis。禁止 mutable 草稿和任意 Path 输入，但不要求每内部调用 fresh reopen。

显式跨 run 复用要求输入/profile/policy/tool/schema 等有效 basis 相同。纯技术步骤无 Prompt 消费时 prompt hash 为 null；无关配置不应触发重扫，实际 source/policy 改变必须重新 admission。失败保留已完成产物，不设计同 run crash takeover。

## 7. Gap、fatal 与预算

BOUNDED_PATH_SET、未解析 media 和支持范围限制是范围信息，不删文件。预算至少包括 maxFiles、maxTotalBytes、maxFileBytes，分配前检查；不能用预算失败伪造验证成功。

身份/hash/size 漂移、危险路径、symlink/gitlink、不安全 reader、文本/media 处置错误、manifest 分母遗漏、断 refs 和安装冲突 fatal。稳定故障范围保留 SOURCE_PATH_INVALID、SOURCE_HASH_MISMATCH、SOURCE_SIZE_MISMATCH、SOURCE_REGISTRATION_NOT_FOUND、SOURCE_HANDLE_INVALID、CAPTURE_IDENTITY_INVALID 及既有 schema/resource-limit codes，不用业务 Gap 掩盖这些问题。

## 8. 当前实现与后续定向验证

本地 capture、已验证源码清单、private registered source handle、持久化执行基础已有实现，固定 719 文件来源已有保存证据；不能继续写成“仅 package 骨架”或“完整捕获未完成”。这不证明全部 Java/框架语法可解析，也不证明整仓业务解释完成。

后续仅在改动读取/复用边界时由 Luna/xhigh 为“同进程只 admission 一次、外部替换 bytes 仍失败、media 不消失、路径逃逸失败”建立 RED，Terra/xhigh 最小 GREEN。已有 exact snapshot identity、完整分母、UTF-8/locator、原子安装及相关 mutation 合同仍有效；不为 docs-only 运行 Maven、客户仓库、网络或 Provider。
