# 01 冻结来源

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

本阶段回答一个朴素但不可省略的问题：后面分析的究竟是哪一份代码？

如果输入仍是 branch、活动工作树或可被替换的目录，那么再准确的调用图、事实和文档也无法重验。Stage 01 只接受上游已经明确选定的离线材料，验证 revision、路径、字节和摘要，并把结果立即持久化。它不 clone、fetch、pull 或刷新来源。

## 2. 具体输入与 DepotHead 例子

输入是 FrozenRepositoryRequest：固定 origin、revision、capture receipt、声明 inventory、本地只读 snapshot root、验证 policy、profile 和预算。

贯穿样例固定到 jshERP commit 8c30ce7861570458920175e200bb2a6442713580，只展开以下八个路径：

1. jshERP-boot/pom.xml
2. jshERP-boot/src/main/resources/application.yml
3. jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java
4. jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java
5. jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHead.java
6. jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java
7. jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java
8. jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml

- **REAL_SOURCE**：这些路径在固定本地快照中存在。
- **UNKNOWN / GAP**：八文件 `BOUNDED_PATH_SET` 不代表全 jshERP coverage，固定 `repositoryCompletionEligible=false`；它只能做 walkthrough、局部 fixture 或诊断。
- **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件的 walkthrough JSON 用连贯的目标值解释模块接力，不是本次重新生成的 snapshot；技术合同中的 unknown 仍只能用 nullable/UNRESOLVED/Gap/fatal 表达。

生产 run 必须由上游 capture 提供 `inventoryScope.kind=COMPLETE_CAPTURE`、仓库完整性证明和全文件 inventory。Stage 01 验证的是这个完整 denominator，DepotHead 八文件只是其中一个可追踪子集；本阶段不允许把 `BOUNDED_PATH_SET` 自动升级为 complete，也不通过扫描活动 root 补齐它。

## 3. 程序怎样工作

1. exact-field 解析 request，拒绝未知字段、duplicate key、错误整数和错误 enum。
2. 核对 origin、bound revision、capture receipt 和声明 inventory digest。
3. 逐段规范化路径；拒绝绝对路径、点段、反斜杠混用、重复路径、逃逸和任一祖先/文件 symlink。
4. 在分配前用 NOFOLLOW 校验普通文件与 declared size，再流式计算 SHA-256。
5. 对文本做 strict UTF-8 解码，建立稳定 byte/line index。
6. 按 canonical path 排序，计算与绝对 root、时间和线程无关的 snapshotId。
7. 对 `COMPLETE_CAPTURE` 重算 capture completeness proof：declared path ID 集必须精确等于 capture inventory ID 集；对 `BOUNDED_PATH_SET` 固定 completion-ineligible Gap。
8. 文件可按稳定 fileId 分片验证；所有 shard denominator 必须两两不交叠且 union 精确等于声明 inventory，改变 shard size/顺序不能改变输出。
9. 写入同文件系统 staging，自验 artifact set/size/SHA 后原子安装 stages/01-freeze-source/。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| source-input.json | rootless、可重放的输入与控制 hashes；本地 root 只留在 private source registry |
| verified-snapshot.json | snapshot identity、origin、scope、policy、budget 和完整性汇总 |
| source-inventory.jsonl | 每行一个 canonical file identity、media type、size、SHA 和 line-index digest |
| stage-receipt.json | 输入 roots、control hashes、artifact set、SUCCEEDED/SUCCEEDED_WITH_GAPS 状态 |

DepotHead 目标输出形状：

~~~json
{
  "schemaVersion": "verified-snapshot-v1",
  "snapshotId": "snapshot:<hex64>",
  "originRevision": "8c30ce7861570458920175e200bb2a6442713580",
  "inventoryScope": "BOUNDED_PATH_SET",
  "declaredFiles": 8,
  "verifiedFiles": 8
}
~~~

这段 JSON 不提供本次计算出的 ID；尖括号值不是可提交值。

目标 DepotHead walkthrough 出口不是一个空壳 receipt：四个文件必须全部存在，`source-inventory.jsonl` 恰有八条，`declaredFiles=verifiedFiles=8`，且每条 inventory record 都有非空 canonical path、media type、正整数 size 和 SHA-256；但 receipt 仍必须写 `repositoryCompletionEligible=false`。生产出口要求完整仓库的 `declaredFiles=verifiedFiles`、shard union闭合、`scopeKind=COMPLETE_CAPTURE` 和 `repositoryCompletionEligible=true`。一般请求的 `declaredPathCount` 必须至少为 1；空 inventory 是 `REQUEST_SCHEMA_INVALID`，不能作为成功。

### 4.1 人类 walkthrough：模块用什么文件接力

下面三行是面向读者的 artifact payload 视图；完整 envelope 和字段合同见 8.0.1。

~~~jsonl
{"module":"FrozenRequestAdmission","artifact":"modules/01-request-admission/admitted-source-request.json","takesFrom":["run-request.json","capture receipt"],"says":{"revision":"8c30ce7861570458920175e200bb2a6442713580","scope":"BOUNDED_PATH_SET","declaredFiles":8,"businessEntrySource":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","persistenceSource":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml"}}
{"module":"VerifiedSourceIndexer","artifact":"modules/02-source-index/verified-source-index.json","takesFrom":["admitted-source-request.json","registered read-only snapshot"],"says":{"snapshotId":"snapshot:depothead-demo-v1","verifiedFiles":8,"controllerSha256":"1111111111111111111111111111111111111111111111111111111111111111","mapperXmlSha256":"8888888888888888888888888888888888888888888888888888888888888888"}}
{"module":"Stage01ArtifactPublisher","artifact":"modules/03-publish/stage01-publication.json","takesFrom":["admitted-source-request.json","verified-source-index.json"],"says":{"stageStatus":"SUCCEEDED_WITH_GAPS","publicFiles":["source-input.json","verified-snapshot.json","source-inventory.jsonl","stage-receipt.json"],"nextStage":"02-discover-application-and-entries"}}
~~~

故事因果是：M1 固定八个 path，M2 对同八个 path 验证 bytes，M3 只能发布这两个 artifact 已共同证明的四文件集合。M2/M3 不能增加第九个文件或把另一个 revision 混入。

## 5. 下游怎样消费而不返工

Stage 02 只读 persisted verified-snapshot.json 与 source-inventory.jsonl。任何下游若需要源码字节，必须通过 snapshot identity 打开的受限 source handle，重验 file/span SHA；不得 walk root、追加文件、使用 basename 猜路径或把活动工作树当快照。

Stage 01 成功目录安装后不可修改。Stage 02–08 任一失败都保留它；resume 只有在 input/tool/profile/schema/prompt hashes 与 receipt 完全相等时才能复用。

### 下游前置条件与后置保证

| Stage 02 开始前必须成立 | Stage 01 成功后保证 |
| --- | --- |
| `stage-receipt.json` 状态为 SUCCEEDED 或 SUCCEEDED_WITH_GAPS；receipt 的 input root/control hashes 与 run request 完全一致 | 完整四文件 artifact set 已在同文件系统原子安装且逐文件 size/SHA 可重算 |
| `verified-snapshot.json`、inventory root 和 receipt artifact root 自验通过 | 每个声明路径恰一条 disposition；没有静默漏文件、补文件或重复路径 |
| source registry 能按 snapshot/file identity 以 NOFOLLOW 方式重开声明 bytes | 下游得到 rootless identity 和受限 handle；绝对 root 不进入 content identity |
| repository production run 还要求 `scopeKind=COMPLETE_CAPTURE`、capture completeness proof 与 shard union 全部闭合 | Stage02 获得完整 source denominator；若只是 BOUNDED_PATH_SET，只能继续诊断而不能最终完成 repository run |

若任一前置条件不成立，Stage 02 必须拒绝输入；它无权修复 Stage 01、换 root 或重新选择文件。

## 6. 成功、Gap、fatal 与恢复

- **成功**：`COMPLETE_CAPTURE` 声明文件全部验证，capture/shard identity/accounting 闭合，`repositoryCompletionEligible=true`，阶段目录原子安装。
- **带 Gap 成功**：`BOUNDED_PATH_SET` 或 profile 明确允许且能安全隔离的范围/预算问题；Gap 不改变 inventory 分母，且 bounded scope 永远不能令 repository run terminal COMPLETE。
- **fatal**：capture identity 不一致、路径越界、symlink、非普通文件、size/SHA/UTF-8 漂移、root 无效、安全 policy 不可执行或 artifact install 失败。
- **恢复**：重算 Stage 01 artifacts、shard union 与 control hashes；相同则从 Stage 02 继续，不相同则创建新 run。已完成 shard 保留，未开始 shard 可重算；缺 shard仍显式 Gap/未闭合，失败不能删改旧 stage 目录。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 选择/验证文件 | 是：只验证调用者声明 inventory | 否 |
| 哈希、路径、字符集、行索引 | 是 | 否 |
| 刷新 Git 或补缺文件 | 否，必须由另行授权的上游 capture 完成 | 否 |
| 解释业务 | 否，本阶段没有业务解释 | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `FrozenRequestAdmission` → `VerifiedSourceIndexer` → `Stage01ArtifactPublisher`。三者只能交换下面声明的 typed records；不得由 publisher 重新验证来源，也不得由 verifier 改 request scope。

#### M1 FrozenRequestAdmission

- **解决的问题**：把外部 request 变成唯一、可哈希、无路径歧义的冻结请求，先决定“允许验证什么”。
- **精确上游输入及前置**：exact UTF-8 JSON `FrozenRepositoryRequest`、已存在的 `verificationPolicyRef/capabilityProfileRef` 和 capture receipt；schemaVersion 已注册，duplicate/unknown field 尚未被容错处理。
- **确定性顺序 / LLM**：strict parse → 校验 origin/revision/capture binding → 校验 inventory digest/count → canonical path 去重与安全检查 → budget admission；全程 0 LLM。
- **目标输出与 DepotHead 示例**：`AdmittedSourceRequest{requestIdentity, originRevision, inventoryScope, repositoryCompletionEligible, sortedFiles[8], controls}`；样例 revision 为 `8c30…3580`，八个 path 中含 `DepotHeadController.java` 与 `DepotHeadMapper.xml`，且 completion eligible 为 false。生产 COMPLETE_CAPTURE 则保存全仓 files 和 true。
- **必须保持的不变量**：`declaredPathCount=files.size>=1`；path 唯一且 repository-relative；capture repository/revision/inventory 三者与 request 完全相同；只有 exact COMPLETE_CAPTURE completeness proof 可令 `repositoryCompletionEligible=true`。
- **Gap / fatal / 恢复**：request、capture、path、duplicate 或预算 admission 错误均 fatal，不产生 draft；相同 request bytes 重放产生相同 identity，不相同必须新 run，本模块没有可猜测 Gap。
- **给下游的后置保证**：M2 收到的列表已排序、已计数、每项都有 declared mediaType/size/SHA/encoding，且没有任何 inventory 发现职责。
- **明确非目标**：不访问文件 bytes，不 clone/fetch，不补文件，不判断 Java/MyBatis 语义。
- **公共测试 seam 与验收**：以 `admit(byte[] exactRequestJson, CaptureReceiptView receipt, ProfileView profile)` 做纯函数测试；八文件 fixture 通过但 completion-ineligible，完整多目录 capture 为 eligible；unknown/duplicate/path escape/empty inventory/capture mismatch/伪 COMPLETE 各以指定 code 失败或指定 Gap。
- **Luna/xhigh 测试指南**：创建 `Stage01FrozenRequestAdmissionTest`，冻结输入/golden 放 `src/test/resources/target/stage01/request-admission/`。依次只加一个 RED：八文件 exact request→golden artifact、unknown/duplicate、empty/path escape、capture mismatch、乱序 determinism；expected JSON/IDs 由本 schema 手写，首个 RED 应因 public seam/artifact 尚缺而失败。仅可 mock `CaptureReceiptView/ProfileView` 的只读边界，禁止 mock parser/canonical/identity或碰 private 实现。精确命令：`mvn -Dtest=Stage01FrozenRequestAdmissionTest test`；禁网络/live Provider/客户 Maven。偏离按 DESIGN 13.11 MUST STOP并交 Sol/ultra Design Authority。
- **Terra/xhigh 实现指南**：仅观察上述预期 RED 后修改 `target/stage01/requestadmission/`，实现 public `FrozenRequestAdmission`、`AdmittedSourceRequest` 及 `stage01-admitted-source-request-v1` writer/parser；按 strict parse→capture→path→budget，复用 run/capture artifacts，不从 filesystem补字段。先 GREEN 正向schema，再逐个 GREEN rejection、identity/determinism；每片只跑同 selector。不得兼容错误 request、改 golden/schema/failure。全绿后更新本文件当前审计的该模块状态；任何缺上游数据/跨阶段改变按 DESIGN 13.11 停止。

#### M2 VerifiedSourceIndexer

- **解决的问题**：证明声明 path 当前仍是声明的普通文件字节，并生成稳定 source locator 所需索引。
- **精确上游输入及前置**：M1 的 `AdmittedSourceRequest` 与 `RegisteredSnapshotHandle{sourceRegistrationId, snapshotRoot}`；root 已由本机 registry 授权，只读且不进入 content identity。
- **确定性顺序 / LLM**：逐 canonical path 做 NOFOLLOW ancestor/file check → pre-allocation stat/size gate → streaming SHA → post-read identity check → strict UTF-8/line index → snapshot identity；全程 0 LLM。
- **目标输出与 DepotHead 示例**：`VerifiedFile{path,mediaType,sizeBytes,sha256,lineIndexDigest}` 列表、`SourceShardReceipt{denominatorFileIds,verifiedFileIds}` 和 `VerifiedSnapshotDraft{snapshotId,sourceIntegrity}`；Controller 样例 record 指向完整 repository-relative path、真实 size/SHA，不保存绝对 root。
- **必须保持的不变量**：每个 admitted file 恰一个 VerifiedFile；verified count 等于 declared count；shard denominators不交叠且union等于全部 admitted fileIds；byte offsets 基于原始 UTF-8、line/column 1-based/end exclusive；不同 root/shard size/order 的等价 bytes 得到相同 IDs。
- **Gap / fatal / 恢复**：symlink、非普通文件、size/hash/UTF-8/post-read drift、安全策略不可执行均 fatal；安全隔离的 profile 预算 Gap 仍保留原分母；恢复从头重验 M1/M2，不复用未发布 draft。
- **给下游的后置保证**：M3 得到完整、排序、rootless 的 snapshot/inventory drafts，任一 record 都可通过 registry handle 重开并重验 SHA。
- **明确非目标**：不解析 Java/XML，不生成 route/Fact，不缓存可变 byte buffer 供下游绕过 source handle。
- **公共测试 seam 与验收**：以可注入的 read-only `SnapshotHandle` 测试 stat/read/reopen；八文件局部正例、非空完整多目录 capture、single-byte mutation、ancestor/file symlink、UTF-8、size race、缺/重叠 shard、不同 root/shard replay 全部命中预期。
- **Luna/xhigh 测试指南**：创建 `Stage01VerifiedSourceIndexerTest`，fixtures/goldens 放 `src/test/resources/target/stage01/source-indexer/`。RED 顺序：八文件 verify→golden index、hash/size、UTF-8、ancestor/file symlink、read-race、different-root same IDs、resume重验；每个测试只暴露一个行为，初始应因indexer/artifact缺失失败。只可 fake `SnapshotHandle` 与文件stat/read故障，canonicalizer/hash/identity不得mock。命令：`mvn -Dtest=Stage01VerifiedSourceIndexerTest test`；无网络/客户执行。异常 RED/偏离按 DESIGN 13.10–13.11。
- **Terra/xhigh 实现指南**：RED 后仅拥有 `target/stage01/sourceindex/`，实现 public `VerifiedSourceIndexer`、`VerifiedFile`、`VerifiedSnapshotDraft` 与 `stage01-verified-source-index-v1`；逐片实现NOFOLLOW/stat gate→stream hash→post-read check→UTF-8/index→snapshot ID，输入只能是M1 artifact+registered handle。GREEN 条件依序是正向、每个fatal、root-independent determinism、reopen；禁止缓存绕过handle或调整failure等级。完成后更新审计；需改schema/上游/安全即MUST STOP交Sol/ultra。

#### M3 Stage01ArtifactPublisher

- **解决的问题**：把已验证 draft 变成不可变、可恢复的 Stage 01 生产出口，而不是只留 Java 内存对象。
- **精确上游输入及前置**：M1 request identity、M2 完整 drafts、runId、同文件系统 stage destination；counts、identity 和 source reopen validation 已通过。
- **确定性顺序 / LLM**：构造四个文件 → canonical sort/encode → staging 写满并 force → 逐文件 size/SHA 和 artifact set 自验 → atomic move → 写成功 receipt identity；0 LLM。
- **目标输出与 DepotHead 示例**：`source-input.json`、`verified-snapshot.json`、八行 `source-inventory.jsonl`、`stage-receipt.json`；walkthrough receipt 记录 `verifiedFiles=8`、四个 artifact SHA、`SUCCEEDED_WITH_GAPS` 与 completion-ineligible。生产 complete receipt 才可记录 eligible=true。
- **必须保持的不变量**：文件集合恰为四项；receipt payload artifact list排除自身以避免自引用；receipt 最后计算并绑定 controls/payload artifacts；destination 已存在只能接受同 addressed bytes，安装后不可修改。
- **Gap / fatal / 恢复**：canonicalization、force、SHA、collision 或 atomic move 失败均 fatal并只留外部 failure event；staging 不算成功；恢复只认已安装且重新验证通过的 receipt。
- **给下游的后置保证**：Stage 02 获得一个 immutable Stage01Reference，能只凭 receipt/artifact roots 验证输入并按 source identity 重开 bytes。
- **明确非目标**：不重新读取/修复文件，不启动 Stage 02，不把本地 root/异常/时间写入 identity。
- **公共测试 seam 与验收**：以可注入 `StageArtifactStore` 做 crash-before/after-force、缺文件、SHA mutation、destination collision、unsupported atomic move；只有完整四文件 set 能返回 Stage01Reference。
- **Luna/xhigh 测试指南**：创建 `Stage01ArtifactPublisherTest`，fixtures/goldens放 `src/test/resources/target/stage01/artifact-publisher/`。RED 顺序：exact four-file golden、canonical ordering、missing/SHA mutation、crash before/after force、collision/atomic unsupported、valid receipt resume；首 RED 应因publisher/store seam缺失而失败。只可 fake filesystem crash/atomic boundary，禁止mock canonical/identity/receipt validator。命令：`mvn -Dtest=Stage01ArtifactPublisherTest test`；禁网络/live Provider/客户 Maven。偏离遵循DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED 后仅拥有 `target/stage01/artifactpublisher/`，共享存储代码只能按已设计 seam 放 `target/artifacts/`；实现 public `Stage01ArtifactPublisher/Stage01Reference` 与 `stage01-publication-v1`。只读M1/M2 artifacts，按canonical write→force→SHA→atomic move→receipt，不重验/修source。每个slice以对应RED GREEN退出，最终四文件bytes/IDs和resume相同；同步审计。任何store/schema/cross-stage contract改变MUST STOP交Sol/ultra/必要时用户。

### 8.0.1 模块 artifact wire schemas

每个文件使用 DESIGN 13.3 的 `ModuleArtifact<T>` envelope。以下字段集合固定；`!`=required non-null，`?`=required nullable。未列字段是 unknown field并拒绝。

| module artifact | schemaVersion / artifactType | 精确 upstream | payload、来源和排序 |
| --- | --- | --- | --- |
| `modules/01-request-admission/admitted-source-request.json` | `stage01-admitted-source-request-v1` / `STAGE01_ADMITTED_SOURCE_REQUEST` | run-request与capture-receipt artifact ID/SHA | `requestIdentity!`、`originRepositoryUrl!`、`originRevision!`、`inventoryScope{kind!{COMPLETE_CAPTURE,BOUNDED_PATH_SET},scopeRoot?}`、`repositoryCompletionEligible!`、`declaredPathCount!`、`files[]!{path!,mediaType!,sizeBytes!,sha256!,textEncoding!}`；全部来自exact request/capture，files按path排序且长度等于count；eligible 当且仅当 COMPLETE_CAPTURE completeness proof 闭合 |
| `modules/02-source-index/verified-source-index.json` | `stage01-verified-source-index-v1` / `STAGE01_VERIFIED_SOURCE_INDEX` | M1 artifact ID/SHA与source-registration receipt ID/SHA | `snapshotId!`、`requestArtifactId!`、`verifiedFileCount!`、`verifiedFiles[]!{fileId!,path!,sizeBytes!,sha256!,lineIndexDigest!}`、`shardReceipts[]!{shardId!,denominatorFileIds[]!,verifiedFileIds[]!,status!,gapIds[]!}`、`sourceIntegrity!`；hash/index来自verified bytes，files按path，shards按shardId，denominator union等于M1 files |
| `modules/03-publish/stage01-publication.json` | `stage01-publication-v1` / `STAGE01_PUBLICATION` | M1、M2 artifact IDs/SHAs | `stageStatus!`、`repositoryCompletionEligible!`、`stageArtifactRoot!`、`publishedArtifacts[4]!{path!,sizeBytes!,sha256!}`、`nextStage!`；`stageArtifactRoot`只覆盖三个payload files、不含receipt自身；publisher envelope在receipt计算后按path列齐四个public files |

`inventoryScope.scopeRoot` 只在 scope kind 需要逻辑相对根时非空，绝不保存本地绝对 root。`completion.gapRefs` 可记录 bounded-scope Gap；fatal 只写 DESIGN 13.3 `ModuleFailure`，success artifact 的 `failureRef` 必须为 null。任何字段、可空性、排序或SHA identity material变化都发布新 schemaVersion；不得从Stage02或filesystem补旧artifact字段。

下面是 schema-valid 技术示例；digest/IDs为说明性合法值，源码 path/revision 对应真实 walkthrough：

~~~jsonl
{"schemaVersion":"stage01-admitted-source-request-v1","artifactType":"STAGE01_ADMITTED_SOURCE_REQUEST","artifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":1,"module":"FrozenRequestAdmission","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"capture-receipt:0000000000000000000000000000000000000000000000000000000000000000","sha256":"0000000000000000000000000000000000000000000000000000000000000000"},{"artifactId":"run-request:depothead-round1","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"requestIdentity":"request:depothead-eight-files","originRepositoryUrl":"https://gitee.com/jishenghua/JSH_ERP.git","originRevision":"8c30ce7861570458920175e200bb2a6442713580","inventoryScope":{"kind":"BOUNDED_PATH_SET","scopeRoot":"jshERP-boot"},"repositoryCompletionEligible":false,"declaredPathCount":8,"files":[{"path":"jshERP-boot/pom.xml","mediaType":"application/xml","sizeBytes":1000,"sha256":"1111111111111111111111111111111111111111111111111111111111111111","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","mediaType":"text/x-java-source","sizeBytes":3000,"sha256":"2222222222222222222222222222222222222222222222222222222222222222","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHead.java","mediaType":"text/x-java-source","sizeBytes":5000,"sha256":"3333333333333333333333333333333333333333333333333333333333333333","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java","mediaType":"text/x-java-source","sizeBytes":6000,"sha256":"4444444444444444444444444444444444444444444444444444444444444444","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java","mediaType":"text/x-java-source","sizeBytes":700,"sha256":"5555555555555555555555555555555555555555555555555555555555555555","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java","mediaType":"text/x-java-source","sizeBytes":4000,"sha256":"6666666666666666666666666666666666666666666666666666666666666666","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/resources/application.yml","mediaType":"application/yaml","sizeBytes":2000,"sha256":"7777777777777777777777777777777777777777777777777777777777777777","textEncoding":"UTF-8"},{"path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","mediaType":"application/xml","sizeBytes":8000,"sha256":"8888888888888888888888888888888888888888888888888888888888888888","textEncoding":"UTF-8"}]}}
{"schemaVersion":"stage01-verified-source-index-v1","artifactType":"STAGE01_VERIFIED_SOURCE_INDEX","artifactId":"source-index:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":1,"module":"VerifiedSourceIndexer","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"source-registration:depothead-read-only","sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"artifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","sha256":"9999999999999999999999999999999999999999999999999999999999999999"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","requestArtifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","verifiedFileCount":8,"verifiedFiles":[{"fileId":"file:pom","path":"jshERP-boot/pom.xml","sizeBytes":1000,"sha256":"1111111111111111111111111111111111111111111111111111111111111111","lineIndexDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"fileId":"file:depothead-controller","path":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","sizeBytes":3000,"sha256":"2222222222222222222222222222222222222222222222222222222222222222","lineIndexDigest":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"fileId":"file:depothead-entity","path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHead.java","sizeBytes":5000,"sha256":"3333333333333333333333333333333333333333333333333333333333333333","lineIndexDigest":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},{"fileId":"file:depothead-example","path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java","sizeBytes":6000,"sha256":"4444444444444444444444444444444444444444444444444444444444444444","lineIndexDigest":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},{"fileId":"file:depothead-mapper-java","path":"jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java","sizeBytes":700,"sha256":"5555555555555555555555555555555555555555555555555555555555555555","lineIndexDigest":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"fileId":"file:depothead-service","path":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java","sizeBytes":4000,"sha256":"6666666666666666666666666666666666666666666666666666666666666666","lineIndexDigest":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"},{"fileId":"file:application-yml","path":"jshERP-boot/src/main/resources/application.yml","sizeBytes":2000,"sha256":"7777777777777777777777777777777777777777777777777777777777777777","lineIndexDigest":"abababababababababababababababababababababababababababababababab"},{"fileId":"file:depothead-mapper-xml","path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","sizeBytes":8000,"sha256":"8888888888888888888888888888888888888888888888888888888888888888","lineIndexDigest":"cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"}],"shardReceipts":[{"shardId":"source-shard:depothead-eight-files","denominatorFileIds":["file:application-yml","file:depothead-controller","file:depothead-entity","file:depothead-example","file:depothead-mapper-java","file:depothead-mapper-xml","file:depothead-service","file:pom"],"verifiedFileIds":["file:application-yml","file:depothead-controller","file:depothead-entity","file:depothead-example","file:depothead-mapper-java","file:depothead-mapper-xml","file:depothead-service","file:pom"],"status":"SUCCEEDED","gapIds":[]}],"sourceIntegrity":"VERIFIED"}}
{"schemaVersion":"stage01-publication-v1","artifactType":"STAGE01_PUBLICATION","artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":1,"module":"Stage01ArtifactPublisher","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"source-index:2222222222222222222222222222222222222222222222222222222222222222","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","sha256":"9999999999999999999999999999999999999999999999999999999999999999"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","repositoryCompletionEligible":false,"stageArtifactRoot":"stage-root:0101010101010101010101010101010101010101010101010101010101010101","publishedArtifacts":[{"path":"source-input.json","sizeBytes":1200,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"path":"source-inventory.jsonl","sizeBytes":6400,"sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"path":"stage-receipt.json","sizeBytes":1600,"sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},{"path":"verified-snapshot.json","sizeBytes":1800,"sha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"}],"nextStage":"02-discover-application-and-entries"}}
~~~

### 8.1 Interface 与 records

~~~java
interface SourceFreezer {
    Stage01Reference freeze(FrozenRepositoryRequest request);
}
~~~

目标 request 的 exact 字段：

~~~text
FrozenRepositoryRequest
  schemaVersion
  origin {kind, repositoryUrl, revision}
  captureProof {kind, receiptId, boundRepositoryUrl, boundRevision,
                inventorySha256, receiptSha256}
  sourceRegistrationId
  snapshotRoot                 // transport only；不进入 content identity
  inventoryScope {kind, scopeRoot, declaredPathCount}
  files[] {path, mediaType, sizeBytes, sha256, textEncoding}
  verificationPolicyRef
  capabilityProfileRef
  resourceBudget
~~~

VerifiedSnapshot 保存 origin、capture proof、scope、policy/profile refs、budget、排序 files 和 sourceIntegrity；不保存绝对 root、byte buffer 或时间。

统一 locator 是 canonical repository-relative path、startByte、endByteExclusive、startLine/startColumn/endLine/endColumn。byte offset 指 UTF-8 原始 bytes；line/column 为 1-based，end exclusive。

### 8.2 Identity 与 canonicalization

snapshotId = snapshot:SHA-256("verified-snapshot-v1" + LF + canonical identity material)。identity material 包含 origin、capture proof、scope、policy、profile、budget 和排序后的 file path/mediaType/size/SHA；排除 root、line index、时间和异常。

source-inventory.jsonl 按 path 排序，每行一个 canonical JSON object并以 LF 结束。stage receipt 绑定 exact artifact names、size 和 SHA。

### 8.3 预算和安全

至少执行 maxFiles、maxTotalBytes、maxFileBytes。先 stat/size gate，再分配或解码；读取后复查 identity。root、每个祖先目录段和文件都 NOFOLLOW。无 live Git、网络、客户 build/runtime 或任意 fallback。

### 8.4 稳定 failure codes

REQUEST_SCHEMA_INVALID、CAPTURE_IDENTITY_INVALID、PROFILE_REFERENCE_INVALID、SNAPSHOT_ROOT_INVALID、SOURCE_PATH_INVALID、SYMLINK_FORBIDDEN、DUPLICATE_SOURCE_PATH、SOURCE_NOT_REGULAR、SOURCE_SIZE_MISMATCH、SOURCE_HASH_MISMATCH、SOURCE_UTF8_INVALID、STAGE01_RESOURCE_LIMIT_EXCEEDED、STAGE_ARTIFACT_INSTALL_FAILED。

错误只暴露 stable code/run receipt ID，不包含绝对路径、源码或 stack trace。fatal 不安装 partial success 目录。

### 8.5 测试 seam 与验收

- 等价 bytes 位于不同 root 或 inventory 乱序时，records/IDs/JSON bytes 相同。
- hash、size、UTF-8、duplicate、path traversal、root/ancestor/file symlink mutation 在任何 parser 前失败。
- 边界预算通过，超一失败；不截断 inventory。
- source input 注释或内容不能影响 request/schema 行为。
- artifact staging 不完整、跨文件系统原子移动不可用或 addressed collision 均 fail closed。

验收 fixture 必须同时覆盖八文件 DepotHead 正向冻结、空 inventory、单文件 hash mutation、symlink/path escape 和不同绝对 root 的 byte-identical replay。只有正向 fixture 产出四个 exact filenames、八条 inventory、相同 snapshotId/canonical bytes，且全部反例在 parser 前以指定 code 失败，Stage 01 才算可交付。

### 8.6 已冻结裁决：实现者不得自由推断

- Stage 01 只验证上游声明的离线 inventory；不发现、扩展、刷新或“修好”来源。
- 空 inventory 非法；声明文件必须全量验证，不能用抽样、best effort 或成功百分比替代。
- `snapshotRoot` 只用于 transport；identity 必须 rootless，locator 必须 repository-relative。
- 只有完整 artifact set 原子安装后才能写成功 receipt；partial directory 永远不是可恢复成功点。
- 只有 profile 已明确列出的可隔离情况能成为 Gap；identity、路径、安全、hash、UTF-8 和 install 问题一律 fatal。
- 内部 I/O buffer、hash library 和 class/package 划分可自行选择；文件名、字段语义、identity material、failure 分类和上述出口不可改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **已验证（有限、内存态）** | 现有 Stage01 M1 能验证声明 inventory、NOFOLLOW、size/SHA、strict UTF-8、line index 和 stable identity；固定 jshERP 八文件有只读验收证据 |
| **尚未符合目标** | 成功结果主要作为 Stage01Result 内存 record 继续流转；没有本设计的 run/stages/01 canonical production assets 和 whole-run receipt |
| **明确边界** | 不含 remote Capture；COMPLETE_CAPTURE 完整性仍依赖上游 receipt；八文件不代表全仓 |

本阶段目标不会因当前只有内存结果而降级。后续实现应增加持久化 Adapter，同时保留现有验证强度。
