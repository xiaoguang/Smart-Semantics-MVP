# 已验证源码清单

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `01-` 与运行目录 `steps/01-verified-source-inventory/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

本分析步骤回答一个朴素但不可省略的问题：后面分析的究竟是哪一份代码？

如果输入仍是 branch、活动工作树或可被替换的目录，那么再准确的调用图、事实和文档也无法重验。分析步骤“已验证源码清单” 只接受上游已经明确选定并注册的离线材料，验证 revision、完整tracked-tree分母、路径、字节、摘要与解析处置，并把结果逐模块立即持久化。它不 clone、fetch、pull 或刷新来源。

## 2. 具体输入与 DepotHead 例子

analysis core的输入是exact `analysis-run-request-v2`中的`sourceRegistrationId + frozenRepositoryRequestRef{artifactId,sha256}`，并继承其content-addressed profile/budget/toolchain/schema/prompt/policy/optional-seed refs；v1与inline budget均在打开source前拒绝。被引用的`frozen-repository-request-v2`固定expected origin、完整40位revision、capture/snapshot-manifest refs、inventory scope和验证policy/profile/budget refs；它没有snapshot root或任何本机Path。VerifiedSourceInventory只能用注入的private registry把registration ID解析为opaque read-only handle。

### 2.1 独立 `LocalGitCommitCaptureAdapter`

capture是VerifiedSourceInventory之前的显式本地maintenance Adapter，不是`RepositoryAnalysisAgent`方法，也不由analysis worker隐式触发。唯一允许路径的命令形状是：

~~~text
capture-local-git --repository <local-path> --commit <exact-40-lower-hex>
~~~

`repository`只用于定位本地Git object database，`commit`必须直接命名一个commit object；不接受branch/tag/short SHA。v0明确选择**受约束Git CLI plumbing实现**，不引入JGit/Maven依赖：bootstrap只解析一次本地git-dir和受信absolute Git executable；ProcessBuilder不经shell。每个子进程先`environment.clear()`，再只设置`LC_ALL=C`、`LANG=C`、`HOME=<private-empty-dir>`、`XDG_CONFIG_HOME=<private-empty-dir>`、`GIT_CONFIG_NOSYSTEM=1`、`GIT_CONFIG_GLOBAL=<private-empty-file>`、`GIT_NO_LAZY_FETCH=1`、`GIT_TERMINAL_PROMPT=0`、`GIT_OPTIONAL_LOCKS=0`、`GIT_PAGER=cat`、`PAGER=cat`；不继承`PATH`或其他ambient variable。`--git-dir=<NOFOLLOW-validated-absolute-git-dir>`与`--no-replace-objects`只作为固定argv传入。用`cat-file`验证commit/tree/blob并读取raw blob bytes，用NUL分隔的tree plumbing枚举；不经alias/pager/credential/helper。解析后的git-dir/object/config路径逐段NOFOLLOW，命令前后identity不变。它不读ref/index/工作区文件、hooks、filters、submodule工作区或客户代码，不联网；检测到local config include/promisor/partial-clone/alternates设置、objects/info/alternates、replace/graft/shallow输入、missing object或需要lazy fetch时直接失败，不尝试fallback。

Adapter从commit tree递归枚举全部tracked entries：tree只作容器，`100644/100755` blob组成完整regular-file denominator；`120000` symlink、`160000` gitlink/submodule或未知mode使整次capture fail closed。实现不得用`checkout/archive/show`等可能应用attributes/filter或接触工作区的porcelain替代raw object读取；command stderr只进入私有diagnostic receipt，绝不进入public artifact或identity。

每个regular blob都原样写入content-addressed immutable snapshot并计算SHA-256。strict UTF-8且不含NUL/禁用控制字节者标`ANALYZABLE_TEXT`；其余标`NON_ANALYZABLE_MEDIA`。media仍计入完整仓库分母、保留git mode/size/blob object ID/SHA-256并在VerifiedSourceInventory重验，只是`textEncoding/lineIndexDigest=null`且parser禁止消费。capture一次性原子安装`snapshot-manifest.jsonl`、`capture-receipt.json`与rootless`source-registration.json`，并把storage locator只写private registry。

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 下面capture receipt字段/type完整；digest/ID只满足wire grammar，未由展示bytes重算，不能作为replay golden：

~~~json
{
  "schemaVersion": "local-git-capture-receipt-v1",
  "captureReceiptId": "capture-receipt:0000000000000000000000000000000000000000000000000000000000000000",
  "declaredRepositoryIdentity": "https://gitee.com/jishenghua/JSH_ERP.git",
  "objectFormat": "SHA1",
  "commitId": "8c30ce7861570458920175e200bb2a6442713580",
  "treeObjectId": "1111111111111111111111111111111111111111",
  "snapshotId": "snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "snapshotManifestRef": {"artifactId": "snapshot-manifest:1111111111111111111111111111111111111111111111111111111111111111", "sha256": "1111111111111111111111111111111111111111111111111111111111111111"},
  "regularFileCount": 1,
  "analyzableTextFileCount": 1,
  "nonAnalyzableMediaFileCount": 0,
  "unsupportedTreeEntryCount": 0,
  "worktreeRead": "FORBIDDEN",
  "networkAccess": "DISABLED",
  "capturePolicyRef": {"artifactId": "capture-policy:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
}
~~~

若tree中出现`assets/logo.png`，它可有`analysisDisposition=NON_ANALYZABLE_MEDIA`并仍贡献file/snapshot/coverage identity；若出现`vendor-lib` mode `160000`，capture返回`LOCAL_GIT_TREE_ENTRY_UNSUPPORTED`且不得产出registration。该区别保证“不能解析”不会变成“没有看见”。

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

生产 run 必须由上述capture提供`inventoryScope.kind=COMPLETE_CAPTURE`、commit/tree完整性证明和全regular-file inventory。分析步骤“已验证源码清单” 验证的是这个完整 denominator，DepotHead 八文件只是其中一个可追踪子集；本分析步骤不允许把`BOUNDED_PATH_SET`自动升级为complete，也不通过扫描活动root或Git工作区补齐它。

## 3. 程序怎样工作

1. exact-field 解析 request，拒绝未知字段、duplicate key、错误整数和错误 enum。
2. 以`sourceRegistrationId`解析opaque handle，核对expected origin、完整40位bound revision、capture receipt与snapshot-manifest ID/SHA；public registration/request出现Path或locator字段即拒绝。private registry允许内部保留local locator，但handle/public record/error/identity均不得暴露它。
3. 逐段规范化manifest path；拒绝绝对路径、点段、反斜杠混用、重复路径、逃逸，以及capture中任何symlink/gitlink/未知mode。
4. 对每个regular file在分配前用NOFOLLOW校验类型与declared size，再流式重算SHA-256；media也不得跳过。
5. 只对`ANALYZABLE_TEXT`做strict UTF-8与稳定byte/line index；`NON_ANALYZABLE_MEDIA`必须保持`textEncoding=null/lineIndexDigest=null`并从parser input显式排除。
6. 按canonical path排序，证明`trackedRegularFileIds = verifiedRegularFileIds ⊎ unverifiedRegularFileIds`及`verifiedRegularFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds`；安装成功要求`unverifiedRegularFileIds=∅`，不能把unverified项记成Gap后发布成功。随后计算与绝对root、时间和线程无关的snapshotId。
7. 对`COMPLETE_CAPTURE`重算capture completeness proof：declared regular-file ID集必须精确等于capture manifest regular-file ID集；对`BOUNDED_PATH_SET`固定completion-ineligible Gap。
8. 文件可按稳定fileId分片验证；所有shard denominator必须两两不交叠且union精确等于声明inventory，改变shard size/顺序不能改变输出。
9. M1/M2使用DESIGN 13.3.1 `CanonicalModuleArtifactStore`立即安装canonical payload+`module-receipt.json`。M3只从reopened M1/M2 publications构造并在自己的module publication中安装**恰好三个**semantic payload：`source-input.json`、`verified-snapshot.json`、`source-inventory.jsonl`，再写M3 module receipt；M3不含analysis step root、analysis step receipt bytes或receipt descriptor。`CanonicalAnalysisStepArtifactStore`fresh reopen M3 reference，逐bytes安装同三项public payload，计算analysis step root，并最后创建绑定M3 reference的`verified-source-inventory-receipt.json`。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| source-input.json | `frozen-repository-request-v2`的rootless、可重验输入与控制hashes；本地root只留在private source registry |
| verified-snapshot.json | `verified-snapshot-v2`：snapshot identity、origin、scope、policy、budget、regular/text/media counts和完整性汇总 |
| source-inventory.jsonl | 每行一个canonical regular-file identity、git mode、media type、size、SHA、analysis disposition与nullable line-index digest |
| verified-source-inventory-receipt.json | 输入 roots、control hashes、artifact set、SUCCEEDED/SUCCEEDED_WITH_GAPS 状态 |

**示例分类：NARRATIVE_ILLUSTRATION。** DepotHead 目标输出计数投影（`TARGET_CONCEPTUAL_PROJECTION_NOT_WIRE_SCHEMA`；exact wire见8.0.1）：

~~~json
{
  "schemaVersion": "verified-snapshot-v2",
  "snapshotId": "snapshot:<hex64>",
  "originRevision": "8c30ce7861570458920175e200bb2a6442713580",
  "inventoryScope": {"kind": "BOUNDED_PATH_SET", "scopeRoot": "jshERP-boot"},
  "trackedRegularFileCount": 8,
  "verifiedRegularFileCount": 8,
  "unverifiedRegularFileCount": 0,
  "analyzableTextFileCount": 8,
  "nonAnalyzableMediaFileCount": 0
}
~~~

这段 JSON 不提供本次计算出的 ID；尖括号值不是可提交值。

目标 DepotHead walkthrough 出口不是一个空壳 receipt：reader-visible analysis step set有四个文件，即M3提供的三个semantic payload加AnalysisStep store最后生成的一份receipt。`source-inventory.jsonl`恰有八条，`verified-snapshot.json`记录`trackedRegularFileCount=verifiedRegularFileCount=8`、`unverifiedRegularFileCount=0`且text/media不交叠union为8；每条 inventory record 都有非空 canonical path、git mode、media type、非负size、SHA-256和analysis disposition，只有text有line-index digest。`repositoryCompletionEligible=false`属于`verified-snapshot.json`和其coverage/accounting，而receipt只绑定该payload的descriptor/status/Gap。生产出口要求完整仓库的regular-file counts/shard union闭合、`scopeKind=COMPLETE_CAPTURE`和`repositoryCompletionEligible=true`。一般请求的`declaredPathCount`必须至少为1；空inventory是`REQUEST_SCHEMA_INVALID`，不能作为成功。空regular file本身合法，其`sizeBytes=0`仍必须hash，不能与空inventory混淆。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下面三行只是面向读者的artifact接力视图；完整 envelope 和字段合同见8.0.1，不能加载为wire。

~~~jsonl
{"module":"request-admission","artifact":"modules/01-request-admission/admitted-source-request.json","receipt":"modules/01-request-admission/module-receipt.json","takesFrom":["run-request.json","capture receipt","snapshot manifest"],"says":{"revision":"8c30ce7861570458920175e200bb2a6442713580","scope":"BOUNDED_PATH_SET","declaredPathCount":8,"businessEntrySource":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","persistenceSource":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml"}}
{"module":"source-index","artifact":"modules/02-source-index/verified-source-index.json","receipt":"modules/02-source-index/module-receipt.json","takesFrom":["reopened M1 publication","registered opaque snapshot handle"],"says":{"snapshotId":"snapshot:depothead-demo-v2","verifiedRegularFileCount":8,"analyzableTextFileCount":8,"nonAnalyzableMediaFileCount":0,"controllerSha256":"1111111111111111111111111111111111111111111111111111111111111111","mapperXmlSha256":"8888888888888888888888888888888888888888888888888888888888888888"}}
{"module":"publish","artifacts":["modules/03-publish/source-input.json","modules/03-publish/verified-snapshot.json","modules/03-publish/source-inventory.jsonl"],"receipt":"modules/03-publish/module-receipt.json","takesFrom":["reopened M1 publication","reopened M2 publication"],"says":{"semanticPublicFiles":["source-input.json","source-inventory.jsonl","verified-snapshot.json"],"analysisStepRoot":null,"analysisStepReceipt":null,"nextAnalysisStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

故事因果是：M1固定八个path，M2对同八个path验证bytes与text/media disposition，M3只能从store重开的两组payload+receipt产生恰好三个semantic payload并先取得自己的module receipt。AnalysisStep store随后重开M3，安装这三项并最后追加receipt，所以reader看到四文件analysis step set。M2/M3不能拿前一模块的Java对象、预报receipt/root、增加第四个semantic文件或把另一个revision混入。

## 5. 下游怎样消费而不返工

分析步骤“应用发现” 只读persisted verified-snapshot.json与source-inventory.jsonl。它从`ANALYZABLE_TEXT`子集发现sites，并为每个`NON_ANALYZABLE_MEDIA`保留`MEDIA_NOT_PARSED` disposition；后者仍在repository file denominator中。任何下游若需要源码字节，必须通过snapshot/file identity打开opaque受限source handle并重验file/span SHA；不得walk root、追加文件、使用basename猜路径、解析media或把活动工作树当快照。

分析步骤“已验证源码清单”成功目录安装后不可修改。后续任一分析步骤失败都保留它；后续显式新执行只有在input/tool/profile/schema/prompt hashes与receipt完全相等时才能引用它。

### 下游前置条件与后置保证

| 分析步骤“应用发现” 开始前必须成立 | 分析步骤“已验证源码清单” 成功后保证 |
| --- | --- |
| `verified-source-inventory-receipt.json` 状态为 SUCCEEDED 或 SUCCEEDED_WITH_GAPS；receipt 的 M3 reference/input roots/control hashes 与 run request 完全一致 | 三个semantic payload与store-last receipt组成的完整四文件analysis step set已在同文件系统原子安装且逐文件size/SHA可重算 |
| `verified-snapshot.json`、inventory root 和 receipt artifact root 自验通过 | 每个声明路径恰一条 disposition；没有静默漏文件、补文件或重复路径 |
| source registry 能按 snapshot/file identity 以 NOFOLLOW 方式重开声明 bytes | 下游得到 rootless identity 和受限 handle；绝对 root 不进入 content identity |
| repository production run 还要求 `scopeKind=COMPLETE_CAPTURE`、capture completeness proof 与 shard union 全部闭合 | ApplicationDiscovery 获得完整 source denominator；若只是 BOUNDED_PATH_SET，只能继续诊断而不能最终完成 repository run |

若任一前置条件不成立，分析步骤“应用发现” 必须拒绝输入；它无权修复 分析步骤“已验证源码清单”、换 root 或重新选择文件。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：`COMPLETE_CAPTURE`全部tracked regular files验证，text/media union、capture/shard identity/accounting闭合，三个module publications与分析步骤目录均原子安装，`repositoryCompletionEligible=true`。
- **带 Gap 成功**：`BOUNDED_PATH_SET` 或 profile 明确允许且能安全隔离的范围/预算问题；Gap 不改变 inventory 分母，且 bounded scope 永远不能令 repository run terminal COMPLETE。
- **fatal**：capture/registration identity不一致、路径越界、symlink/gitlink/未知mode、非普通文件、size/SHA漂移、text被误标media或media被送入UTF-8 parser、opaque handle无效、安全policy不可执行、module receipt缺失/漂移或artifact install失败。二进制本身不是fatal；静默遗漏或错误处置才是。
- **显式复用**：新 ApplicationDiscovery execution fresh-reopen并重算 VerifiedSourceInventory artifacts、shard union 与 control hashes；完全相同才读取，不相同则拒绝该输入。已完整安装的shard保留；缺shard仍显式Gap/未闭合，失败不能删改旧analysis step目录。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 本地commit capture | 独立maintenance Adapter：只读Git objects，完整tree→snapshot/receipt/registration | 否 |
| 选择/验证文件 | VerifiedSourceInventory只验证已注册完整inventory；所有regular files计数/hash，media不解析 | 否 |
| 哈希、路径、字符集、行索引 | 是 | 否 |
| 刷新 Git 或补缺文件 | 否；capture也只接受本地exact commit，缺object失败而不fetch | 否 |
| 解释业务 | 否，本分析步骤没有业务解释 | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 Capture wire records（VerifiedSourceInventory外部maintenance seam）

~~~text
LocalGitCaptureRequest
  schemaVersion=local-git-capture-request-v1
  declaredRepositoryIdentity
  commitId                              // exactly 40 lowercase hex
  capturePolicyRef {artifactId, sha256}
  resourceBudgetRef {artifactId, sha256}
  repositoryPath                        // CLI transport only; excluded from request/capture/snapshot IDs

LocalGitCaptureReceipt
  schemaVersion=local-git-capture-receipt-v1
  captureReceiptId
  declaredRepositoryIdentity
  objectFormat=SHA1
  commitId
  treeObjectId
  snapshotId
  snapshotManifestRef {artifactId, sha256}
  regularFileCount
  analyzableTextFileCount
  nonAnalyzableMediaFileCount
  unsupportedTreeEntryCount=0
  worktreeRead=FORBIDDEN
  networkAccess=DISABLED
  capturePolicyRef

SourceRegistration
  schemaVersion=source-registration-v1
  sourceRegistrationId
  declaredRepositoryIdentity
  commitId
  snapshotId
  snapshotManifestRef {artifactId, sha256}
  captureReceiptRef {artifactId, sha256}
  regularFileCount
~~~

`declaredRepositoryIdentity`由调用者给出并原样绑定；Adapter不读remote config，也不把它冒充Git对象证明。`repositoryPath`是唯一path字段，只存在local capture transport request，序列化capture receipt/registration时必须删除；其余字段共同参与rootless IDs。snapshot manifest每行一个`local-git-snapshot-entry-v1`：`path/gitMode/blobObjectId/sizeBytes/sha256/mediaType/analysisDisposition/textEncoding?`。任何字段、计数或digest不一致不安装三件套；同addressed destination只有全bytes一致才幂等成功。

### 8.1 固定模块合同

模块执行顺序固定为 `FrozenRequestAdmission` → `VerifiedSourceIndexer` → `VerifiedSourceInventoryPublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`。前三者只能交换下面声明的typed records/module references；不得由specifier重新验证来源，也不得由verifier改request scope。AnalysisStep store是总体设计的共享deep seam，不是第四个VerifiedSourceInventory业务模块。

#### M1 FrozenRequestAdmission

- **解决的问题**：把外部 request 变成唯一、可哈希、无路径歧义的冻结请求，先决定“允许验证什么”。
- **精确上游输入及前置**：exact `analysis-run-request-v2`给出的`sourceRegistrationId`、content-addressed `frozenRepositoryRequestRef`和profile/budget/toolchain/schema/prompt/policy refs；exact UTF-8 `frozen-repository-request-v2`、capture receipt/snapshot-manifest IDs/SHAs及已存在的`verificationPolicyRef/capabilityProfileRef/resourceBudgetRef`。任何record均无Path；v1、inline budget、duplicate/unknown field直接拒绝。
- **确定性顺序 / LLM**：strict parse → 校验 origin/revision/capture binding → 校验 inventory digest/count → canonical path 去重与安全检查 → budget admission；全程 0 LLM。
- **目标输出与 DepotHead 示例**：`AdmittedSourceRequest{requestIdentity,sourceRegistrationId,originRevision,inventoryScope,repositoryCompletionEligible,sortedRegularFiles[8],analyzableTextFileIds,nonAnalyzableMediaFileIds,controls}`；样例revision为`8c30…3580`，八个path均为text且completion eligible=false。生产COMPLETE_CAPTURE保存全仓regular files与显式text/media partitions。
- **必须保持的不变量**：`declaredPathCount=regularFiles.size>=1`；path唯一且repository-relative；每项gitMode只可100644/100755；capture repository/revision/inventory三者与request完全相同；text/media ID集不交叠且union为全regular denominator；只有exact COMPLETE_CAPTURE completeness proof可令`repositoryCompletionEligible=true`。
- **Gap / fatal / artifact复用**：request、capture、path、duplicate 或预算 admission 错误均 fatal，不产生 draft；相同 request bytes 产生相同业务identity，不相同是另一输入，本模块没有可猜测 Gap。
- **给下游的后置保证**：M1 publication已由shared store安装并可重开；列表已排序/计数，每项都有gitMode/mediaType/size/SHA/disposition，textEncoding按disposition必为UTF-8或null，M2没有inventory发现职责。
- **明确非目标**：不访问文件 bytes，不 clone/fetch，不补文件，不判断 Java/MyBatis 语义。
- **公共测试 seam 与验收**：以 `admit(byte[] exactRequestJson, CaptureReceiptView receipt, ProfileView profile)` 做纯函数测试；八文件 fixture 通过但 completion-ineligible，完整多目录 capture 为 eligible；unknown/duplicate/path escape/empty inventory/capture mismatch/伪 COMPLETE 各以指定 code 失败或指定 Gap。
- **Luna/xhigh 测试指南**：创建 `FrozenRequestAdmissionTest`，冻结输入/golden 放 `src/test/resources/analysis/inventory/request-admission/`。依次只加一个 RED：八文件 exact request→golden artifact、unknown/duplicate、empty/path escape、capture mismatch、乱序 determinism；expected JSON/IDs 由本 schema 手写，首个 RED 应因 public seam/artifact 尚缺而失败。仅可 mock `CaptureReceiptView/ProfileView` 的只读边界，禁止 mock parser/canonical/identity或碰 private 实现。精确命令：`mvn -Dtest=FrozenRequestAdmissionTest test`；禁网络/live Provider/客户 Maven。偏离按 DESIGN 13.11 MUST STOP并交 Sol/ultra Design Authority。
- **Terra/xhigh 实现指南**：仅观察上述预期RED后修改`analysis/inventory/request-admission/`，实现public `FrozenRequestAdmission`、`AdmittedSourceRequest`及`verified-source-inventory-admitted-source-request-v2` writer/parser；通过DESIGN 13.3.1 store安装payload+receipt，按strict parse→registration/capture refs→path/mode/disposition→budget，复用run/capture artifacts，不从filesystem补字段。不得兼容v1默认、改golden/schema/failure。

#### M2 VerifiedSourceIndexer

- **解决的问题**：证明声明 path 当前仍是声明的普通文件字节，并生成稳定 source locator 所需索引。
- **精确上游输入及前置**：由`CanonicalModuleArtifactStore.reopen(M1 reference)`得到的`AdmittedSourceRequest`与private registry解析出的opaque `RegisteredSnapshotHandle`；handle无可读取的path字段，只提供按snapshot/file identity做NOFOLLOW stat/read/reopen的受限操作。
- **确定性顺序 / LLM**：逐canonical regular path做NOFOLLOW ancestor/file check → pre-allocation stat/size gate → streaming SHA → post-read identity check → 对ANALYZABLE_TEXT strict UTF-8/line index、对NON_ANALYZABLE_MEDIA验证null text fields并禁止parser → partition/accounting → snapshot identity；全程0 LLM。
- **目标输出与 DepotHead 示例**：`VerifiedFile{path,gitMode,mediaType,sizeBytes,sha256,analysisDisposition,textEncoding?,lineIndexDigest?}`列表、`SourceShardReceipt{denominatorFileIds,verifiedFileIds}`和`VerifiedSnapshotDraft{snapshotId,regular/text/media counts,sourceIntegrity}`；Controller为ANALYZABLE_TEXT。binary fixture为NON_ANALYZABLE_MEDIA且两个text字段为null；均不保存绝对root。
- **必须保持的不变量**：每个admitted regular file恰一个终态；`trackedIds=verifiedIds⊎unverifiedIds`且`verifiedIds=textIds⊎mediaIds`，只有`unverifiedIds=∅`才能安装M2 success publication；所有files验SHA，只有text建立line index且可进入parser；shard denominators不交叠且union等于全部admitted fileIds；byte offsets基于原始UTF-8、line/column 1-based/end exclusive；不同private root/shard size/order的等价bytes得到相同IDs。
- **Gap / fatal / artifact复用**：symlink/gitlink/未知mode、非普通文件、size/hash/post-read drift、disposition与bytes矛盾、media进入parser、安全策略不可执行均fatal；合法binary本身不是Gap/fatal。安全隔离的profile预算Gap仍保留原分母；M2只从reopened M1 receipt重验，不复用未发布draft。
- **给下游的后置保证**：M3 得到完整、排序、rootless 的 snapshot/inventory drafts，任一 record 都可通过 registry handle 重开并重验 SHA。
- **明确非目标**：不解析 Java/XML，不生成 route/Fact，不缓存可变 byte buffer 供下游绕过 source handle。
- **公共测试 seam 与验收**：以可注入的 read-only `SnapshotHandle` 测试 stat/read/reopen；八文件局部正例、非空完整多目录 capture、single-byte mutation、ancestor/file symlink、UTF-8、size race、缺/重叠 shard、不同 root/shard replay 全部命中预期。
- **Luna/xhigh 测试指南**：创建 `VerifiedSourceIndexerTest`，fixtures/goldens 放 `src/test/resources/analysis/inventory/source-index/`。RED 顺序：八文件 verify→golden index、hash/size、UTF-8、ancestor/file symlink、read-race、different-root same IDs、fresh-reopen重验；每个测试只暴露一个行为，初始应因indexer/artifact缺失失败。只可 fake `SnapshotHandle` 与文件stat/read故障，canonicalizer/hash/identity不得mock。命令：`mvn -Dtest=VerifiedSourceIndexerTest test`；无网络/客户执行。异常 RED/偏离按 DESIGN 13.10–13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有`analysis/inventory/source-index/`，实现public `VerifiedSourceIndexer`、`VerifiedFile`、`VerifiedSnapshotDraft`与`verified-source-inventory-verified-source-index-v2`；输入只能来自reopened M1 publication+opaque registered handle，输出通过shared store安装payload+receipt。逐片实现NOFOLLOW/stat→hash→post-read→text/media分支→accounting→snapshot ID；禁止缓存/Java对象旁路、解析media或调整failure等级。

#### M3 VerifiedSourceInventoryPublicationSpecifier

- **解决的问题**：把已验证draft变成AnalysisStep store可重开的三个exact semantic payload，不在尚未存在的analysis step receipt/root上制造自引用。
- **精确上游输入及前置**：由shared store分别reopen并完整验证的M1/M2 `ModulePublicationReference`，以及它们所属的exact `analysis-run-request-v2` 和 `frozen-repository-request-v2` `ArtifactReference`。执行组合层注入一个只接受`ArtifactReference`、只返回已重验`ImmutableBytes`的无路径输入读取器；它不枚举、不按名称搜索、不接受Path，也不是第四个canonical publication store。M3经该读取器直接重开后两者来取得它要写入`source-input.json`/`verified-snapshot.json`的profile、budget、toolchain、schema、prompt、policy与capture refs；不得经M1/M2转拉这些未重开bytes。另有runId与typed VerifiedSourceInventory destination只是address，不是content preimage；原始draft对象不是输入。
- **确定性顺序 / LLM**：fresh reopen M1 → M2 → analysis-run-request → frozen-request → 构造exact三个semantic payload → canonical sort/encode → 用一次`ModuleInstallRequest`安装三payload并绑定四个direct preimage refs → store写M3 module receipt → fresh reopen M3 → 调用`CanonicalAnalysisStepArtifactStore.install` → store计算analysis step root并最后写analysis step receipt；0 LLM。
- **目标输出与 DepotHead 示例**：M3 module publication恰有`source-input.json`、`verified-snapshot.json`、八行`source-inventory.jsonl`及其module receipt；没有`verified-source-inventory-publication.json`。AnalysisStep store重开M3后产生相同三项analysis step bytes和`verified-source-inventory-receipt.json`。walkthrough的`verified-snapshot.json`/`source-inventory.jsonl`一起记录`trackedRegularFileCount=verifiedRegularFileCount=analyzableTextFileCount=8`、`unverifiedRegularFileCount=nonAnalyzableMediaFileCount=0`与completion-ineligible；analysis step receipt只绑定三个semantic descriptors、M3 reference、controls、status与Gap refs/count，不重复这些coverage字段。
- **必须保持的不变量**：M3 semantic payload集合恰为三项；M3 module receipt只绑定三项。analysis step root只覆盖这三项；analysis step receipt排除自身并绑定M3 module root/receipt ID/SHA。任何M3 payload预报analysis step root、analysis step receipt bytes/SHA/descriptor或四项published list都属schema error。
- **Gap / fatal / artifact复用**：canonicalization、force、SHA、collision或atomic move失败均fatal并只留外部failure诊断；staging不算成功。只有完整验证通过的M3/analysis step receipts才可被下游或新执行引用。
- **给下游的后置保证**：分析步骤“应用发现” 获得一个 immutable VerifiedSourceInventoryReference，能只凭 receipt/artifact roots 验证输入并按 source identity 重开 bytes。
- **明确非目标**：不重新读取/修复文件，不启动 分析步骤“应用发现”，不把本地 root/异常/时间写入 identity。
- **公共测试 seam 与验收**：M1–M3 module publication统一使用`CanonicalModuleArtifactStore.install/reopen`，随后必须通过真实`CanonicalAnalysisStepArtifactStore`；测试M3 exact three descriptors、store-last analysis step receipt、M3→analysis step provenance、各partial-install点、identical collision=`ALREADY_INSTALLED`、different collision和unsupported atomic move。只有三组完整module publications加四文件reader-visible analysis step set能返回VerifiedSourceInventoryReference。
- **Luna/xhigh 测试指南**：创建 `VerifiedSourceInventoryPublicationSpecifierTest`，fixtures/goldens放 `src/test/resources/analysis/inventory/publish/`。RED顺序：M3 exact-three golden、AnalysisStep store four-file output、receipt-last/provenance、missing/SHA mutation、各partial-install点、collision/atomic unsupported、valid receipt reopen；禁止mock canonical/root/receipt stores。命令：`mvn -Dtest=VerifiedSourceInventoryPublicationSpecifierTest test`；禁网络/live Provider/客户Maven。偏离遵循DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有`analysis/inventory/publish/`，共享存储只调用DESIGN 13.3.1两个store；实现package-internal `VerifiedSourceInventoryPublicationSpecifier`与public`VerifiedSourceInventoryReference`。只读reopened M1/M2 payloads+receipts，安装exact-three M3 payload+receipt，再用typed `AnalysisStepInstallRequest`发布三项+store receipt；不得生成旧的单一publication envelope或预报root/receipt。任何store/schema/cross-analysis step contract改变MUST STOP。

### 8.1.1 模块 artifact wire schemas

M1/M2 JSON payload使用DESIGN 13.3的`ModuleArtifact<T>` envelope；M3使用analysis step schema注册的两个JSON policies和一个`CANONICAL_JSONL` policy，`source-inventory.jsonl`不是伪JSON envelope。每个目录另有DESIGN 13.3.1 `module-receipt-v1`且必须经shared store原子安装/重开。以下字段集合固定；`!`=required non-null，`?`=required nullable。未列字段是unknown field并拒绝。

| module artifact | schemaVersion / artifactType | 精确 upstream | payload、来源和排序 |
| --- | --- | --- | --- |
| `modules/01-request-admission/admitted-source-request.json` | `verified-source-inventory-admitted-source-request-v2` / `VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST` | M1 fresh read的exact `analysis-run-request`、`frozen-request`、`capture-receipt`、`snapshot-manifest`、`source-registration`、`capability-profile`、`resource-budget`与`verification-policy` ArtifactReferences；排序/去重后全部绑定 | `requestIdentity!`、`sourceRegistrationId!`、`originRepositoryUrl!`、`originRevision!`、`inventoryScope{kind!{COMPLETE_CAPTURE,BOUNDED_PATH_SET},scopeRoot?}`、`repositoryCompletionEligible!`、`declaredPathCount!`、`files[]!{path!,gitMode!{100644,100755},mediaType!,sizeBytes!,sha256!,analysisDisposition!{ANALYZABLE_TEXT,NON_ANALYZABLE_MEDIA},textEncoding?}`；files按path，textEncoding按disposition为UTF-8/null；text/media union=count |
| `modules/02-source-index/verified-source-index.json` | `verified-source-inventory-verified-source-index-v2` / `VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX` | exact M1 `source-request`与`source-registration` ArtifactReferences | `snapshotId!`、`requestArtifactId!`、`verifiedRegularFileCount!`、`analyzableTextFileCount!`、`nonAnalyzableMediaFileCount!`、`verifiedFiles[]!{fileId!,path!,gitMode!,mediaType!,sizeBytes!,sha256!,analysisDisposition!,textEncoding?,lineIndexDigest?}`、`shardReceipts[]!{shardId!,denominatorFileIds[]!,verifiedFileIds[]!,status!,gapIds[]!}`、`sourceIntegrity!`；全部files验hash，只有text两字段非null；denominator union等于M1 files |
| `modules/03-publish/source-input.json` | `verified-source-inventory-source-input-v2` / `VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT` | exact M1 `source-request`、M2 `source-index`、`analysis-run-request-v2`与`frozen-repository-request-v2` ArtifactReferences，四者绑定在同一M3 install request/receipt | `sourceInputId!`、`sourceRegistrationId!`、`frozenRepositoryRequestRef!`、`captureReceiptRef!`、`snapshotManifestRef!`、`inventoryScope!`、`repositoryCompletionEligible!`、`profileBundleRef!`、`resourceBudgetRef!`、`toolchainRef!`、`schemaBundleRef!`、`promptBundleRef!`、`artifactPolicyRegistryRef!`；不含Path/root/receipt |
| `modules/03-publish/verified-snapshot.json` | `verified-snapshot-v2` / `VERIFIED_SNAPSHOT` | 与上一行相同的M3 install request/receipt binding | `snapshotId!`、`declaredRepositoryIdentity!`、`objectFormat=SHA1!`、`originRevision!`、`captureReceiptRef!`、`snapshotManifestRef!`、scope/eligibility、`verificationPolicyRef!`、`capabilityProfileRef!`、`resourceBudgetRef!`、`trackedRegularFileCount!`、`verifiedRegularFileCount!`、`unverifiedRegularFileCount=0!`、text/media counts、五个sorted file-ID sets、shard/accounting proof、`sourceIntegrity!`；不含analysis step root/receipt |
| `modules/03-publish/source-inventory.jsonl` | `verified-source-inventory-source-inventory-v2` / `VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY` | 与上一行相同的M3 install request/receipt binding | 每行exact `source-inventory-entry-v2 {fileId!,path!,gitMode!,mediaType!,sizeBytes!,sha256!,analysisDisposition!,textEncoding?,lineIndexDigest?}`；按path UTF-8 bytes严格排序，零行禁止；不含summary/root/receipt |

`inventoryScope.scopeRoot`只在scope kind需要逻辑相对根时非空，绝不保存本地绝对root。M3这三行共同属于**一次**module install和一个`module-receipt.json`，不是三个模块；它们的exact bytes随后由AnalysisStep store安装成同名analysis step semantic files。M3 install request的`upstreamArtifacts`是按ArtifactReference规则排序/去重的四项闭包：M1 payload ref、M2 payload ref、run-request ref、frozen-request ref；M3 receipt必须逐bytes重复该集合。其他模块每个上述payload同目录也必须有receipt；下一模块以完整`ModulePublicationReference`重开，不能只拿artifact ID。`completion.gapRefs`可记录bounded-scope Gap；fatal只写DESIGN 13.3 `ModuleFailure`。旧request/payload versions与旧单一publication envelope均拒绝，不能从extension、UTF-8尝试、ApplicationDiscovery或未来receipt补默认。

M3的exact调用边界为`VerifiedSourceInventoryPublicationSpecificationInputV1(runId, destination, admittedSourceRequestPublication, verifiedSourceIndexPublication, analysisRunRequestRef, frozenRepositoryRequestRef)`。前两个publication都是完整`ModulePublicationReference`；后两个是完整`ArtifactReference`；`destination`是validated `AnalysisStepPublicationAddress`。这六个组件全部required，未知/重复字段拒绝。specifier从两个publication重开各自payload ref，并把这两个payload ref与两个显式request refs组成上述四项identity闭包；不得将publication receipt/root ID冒充semantic preimage。

**示例分类：NARRATIVE_ILLUSTRATION（五个彼此隔离的record投影）。** 下面五行只展示M1/M2 envelope与M3 standalone payload的形态差异；它们的digest/ID没有从同一组展示bytes重算，不是schema-valid fixture、executable fixture或cross-analysis step replay chain。前两行表示普通M1/M2 `ModuleArtifact<T>` envelope，后三行分别表示M3直接安装的两个standalone JSON document与一条JSONL record；后三行不得增加`producer/upstreamArtifacts/controls/completion/payload` wrapper。Luna strict replay fixture必须按上表构造每个exact record、对实际canonical bytes重算ID/SHA，并用`AnalysisStepModuleAddress(runId, "verified-source-inventory", 3, "publish")`创建一次`ModuleInstallRequest`；该request的sorted `upstreamArtifacts`必须精确等于已重开M1/M2 payload refs、run-request ref和frozen-request ref，`payloads`逐bytes等于三个M3文件，生成的M3 receipt再重复同一个集合。不存在说明值到strict值的隐式remap：

~~~jsonl
{"schemaVersion":"verified-source-inventory-admitted-source-request-v2","artifactType":"VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST","artifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"verified-source-inventory","moduleNumber":1,"moduleKey":"request-admission"},"moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"capture-receipt:0000000000000000000000000000000000000000000000000000000000000000","sha256":"0000000000000000000000000000000000000000000000000000000000000000"},{"artifactId":"frozen-request:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"artifactId":"run-request:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"snapshot-manifest:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:7777777777777777777777777777777777777777777777777777777777777777"],"failureRef":null},"payload":{"requestIdentity":"request:6666666666666666666666666666666666666666666666666666666666666666","sourceRegistrationId":"source-registration:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee","originRepositoryUrl":"https://gitee.com/jishenghua/JSH_ERP.git","originRevision":"8c30ce7861570458920175e200bb2a6442713580","inventoryScope":{"kind":"BOUNDED_PATH_SET","scopeRoot":"jshERP-boot"},"repositoryCompletionEligible":false,"declaredPathCount":1,"files":[{"path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","gitMode":"100644","mediaType":"application/xml","sizeBytes":8000,"sha256":"8888888888888888888888888888888888888888888888888888888888888888","analysisDisposition":"ANALYZABLE_TEXT","textEncoding":"UTF-8"}]}}
{"schemaVersion":"verified-source-inventory-verified-source-index-v2","artifactType":"VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX","artifactId":"source-index:2222222222222222222222222222222222222222222222222222222222222222","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"verified-source-inventory","moduleNumber":2,"moduleKey":"source-index"},"moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"source-registration:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee","sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"artifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:7777777777777777777777777777777777777777777777777777777777777777"],"failureRef":null},"payload":{"snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","requestArtifactId":"source-request:1111111111111111111111111111111111111111111111111111111111111111","verifiedRegularFileCount":1,"analyzableTextFileCount":1,"nonAnalyzableMediaFileCount":0,"verifiedFiles":[{"fileId":"file:7777777777777777777777777777777777777777777777777777777777777777","path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","gitMode":"100644","mediaType":"application/xml","sizeBytes":8000,"sha256":"8888888888888888888888888888888888888888888888888888888888888888","analysisDisposition":"ANALYZABLE_TEXT","textEncoding":"UTF-8","lineIndexDigest":"cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"}],"shardReceipts":[{"shardId":"source-shard:8888888888888888888888888888888888888888888888888888888888888888","denominatorFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"verifiedFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"status":"SUCCEEDED","gapIds":[]}],"sourceIntegrity":"VERIFIED"}}
{"schemaVersion":"verified-source-inventory-source-input-v2","artifactType":"VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT","artifactId":"verified-source-inventory-source-input:3333333333333333333333333333333333333333333333333333333333333333","sourceInputId":"verified-source-inventory-input:3333333333333333333333333333333333333333333333333333333333333333","sourceRegistrationId":"source-registration:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee","frozenRepositoryRequestRef":{"artifactId":"frozen-request:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"captureReceiptRef":{"artifactId":"capture-receipt:0000000000000000000000000000000000000000000000000000000000000000","sha256":"0000000000000000000000000000000000000000000000000000000000000000"},"snapshotManifestRef":{"artifactId":"snapshot-manifest:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},"inventoryScope":{"kind":"BOUNDED_PATH_SET","scopeRoot":"jshERP-boot"},"repositoryCompletionEligible":false,"profileBundleRef":{"artifactId":"profile-bundle:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},"resourceBudgetRef":{"artifactId":"resource-budget:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},"toolchainRef":{"artifactId":"toolchain:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},"schemaBundleRef":{"artifactId":"schema-bundle:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},"promptBundleRef":{"artifactId":"prompt-bundle:6666666666666666666666666666666666666666666666666666666666666666","sha256":"6666666666666666666666666666666666666666666666666666666666666666"},"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}}
{"schemaVersion":"verified-snapshot-v2","artifactType":"VERIFIED_SNAPSHOT","artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","declaredRepositoryIdentity":"https://gitee.com/jishenghua/JSH_ERP.git","objectFormat":"SHA1","originRevision":"8c30ce7861570458920175e200bb2a6442713580","captureReceiptRef":{"artifactId":"capture-receipt:0000000000000000000000000000000000000000000000000000000000000000","sha256":"0000000000000000000000000000000000000000000000000000000000000000"},"snapshotManifestRef":{"artifactId":"snapshot-manifest:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},"inventoryScope":{"kind":"BOUNDED_PATH_SET","scopeRoot":"jshERP-boot"},"repositoryCompletionEligible":false,"verificationPolicyRef":{"artifactId":"verification-policy:9999999999999999999999999999999999999999999999999999999999999999","sha256":"9999999999999999999999999999999999999999999999999999999999999999"},"capabilityProfileRef":{"artifactId":"capability-profile:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},"resourceBudgetRef":{"artifactId":"resource-budget:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},"trackedRegularFileCount":1,"verifiedRegularFileCount":1,"unverifiedRegularFileCount":0,"analyzableTextFileCount":1,"nonAnalyzableMediaFileCount":0,"trackedRegularFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"verifiedRegularFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"unverifiedRegularFileIds":[],"analyzableTextFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"nonAnalyzableMediaFileIds":[],"shardReceipts":[{"shardId":"source-shard:8888888888888888888888888888888888888888888888888888888888888888","denominatorFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"verifiedFileIds":["file:7777777777777777777777777777777777777777777777777777777777777777"],"status":"SUCCEEDED","gapIds":[]}],"accountingProof":{"trackedEqualsVerifiedUnionUnverified":true,"verifiedEqualsAnalyzableTextUnionNonAnalyzableMedia":true},"sourceIntegrity":"VERIFIED"}
{"schemaVersion":"source-inventory-entry-v2","fileId":"file:7777777777777777777777777777777777777777777777777777777777777777","path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","gitMode":"100644","mediaType":"application/xml","sizeBytes":8000,"sha256":"8888888888888888888888888888888888888888888888888888888888888888","analysisDisposition":"ANALYZABLE_TEXT","textEncoding":"UTF-8","lineIndexDigest":"cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"}
~~~

### 8.2 Interface 与 records

~~~java
interface SourceFreezer { // package-internal analysis step seam; not a product public Interface
    VerifiedSourceInventoryReference freeze(SourceFreezeRequest request);
}
~~~

`RepositoryAnalysisAgent.start`仍是唯一外部入口。VerifiedSourceInventory内部request的exact字段：

~~~text
SourceFreezeRequest
  runId
  analysisRunRequestRef {artifactId, sha256} // exact analysis-run-request-v2

FrozenRepositoryRequest
  schemaVersion=frozen-repository-request-v2
  expectedOrigin {kind, repositoryUrl, revision40}
  captureReceiptRef {artifactId, sha256}
  snapshotManifestRef {artifactId, sha256}
  inventoryScope {kind, scopeRoot, declaredPathCount}
  verificationPolicyRef {artifactId, sha256}
  capabilityProfileRef {artifactId, sha256}
  resourceBudgetRef {artifactId, sha256}
~~~

`expectedOrigin`是严格closed object，字段恰为`kind`、`repositoryUrl`和`revision40`：

~~~text
ExpectedOriginV2
  kind = GIT_SHA1_COMMIT
  repositoryUrl = non-empty path-free UTF-8 text
  revision40 = [0-9a-f]{40}
~~~

`kind`没有其他合法值；它描述冻结对象的语义，不描述capture transport，所以不得写成`LOCAL_GIT_COMMIT`。`repositoryUrl`不读取Git remote、不做URL规范化，必须逐字等于capture receipt/registration的`declaredRepositoryIdentity`。`revision40`必须逐字等于registration与capture receipt的`commitId`，且receipt的`objectFormat`必须为`SHA1`。unknown/duplicate/null字段、大小写变体或格式错误为`REQUEST_SCHEMA_INVALID`；任何与已登记capture不一致的值为`CAPTURE_IDENTITY_INVALID`。未来即使由远程adapter冻结同一Git commit，仍使用此对象和值；adapter位置不改变源码身份。

`FrozenRepositoryRequest`本身也是content-addressed artifact；inventory只能从它绑定的snapshot manifest读取。SourceFreezer先fresh reopen `analysis-run-request-v2`，从中取得source registration、frozen request以及profile/budget/toolchain/schema/prompt/policy refs，任何重复inline字段都拒绝。`SourceFreezeRequest`和`FrozenRepositoryRequest`均不含repository/snapshot/store path。private registry把`sourceRegistrationId`解析成opaque handle是composition行为，不是request字段。

`VerifiedSnapshot v2`保存origin、capture proof、scope、policy/profile/budget refs、排序regular files、text/media partitions和sourceIntegrity；不保存inline budget、绝对root、byte buffer或时间。

统一 locator 是 canonical repository-relative path、startByte、endByteExclusive、startLine/startColumn/endLine/endColumn。byte offset 指 UTF-8 原始 bytes；line/column 为 1-based，end exclusive。

### 8.3 Identity 与 canonicalization

`snapshotId = snapshot:lowercaseHex(SHA-256(frame(UTF8("verified-snapshot-id-v2")) || frame(canonicalJson(snapshotIdentityMaterial))))`，其中`frame`严格采用DESIGN 13.3.1的U64BE length framing。identity material包含origin、capture receipt/snapshot-manifest refs、scope、policy/profile/budget refs，以及按path UTF-8 bytes排序的regular-file path/gitMode/mediaType/size/SHA/analysisDisposition/textEncoding-nullability；排除self ID、private root、line-index digest、时间和异常。v1 snapshot ID不得在缺字段时升级或复用为v2。

### 8.3.1 稳定 source file ID

所有步骤只使用统一前缀`file:`；`source-file:`从来不是合法ID。M1从已登记manifest派生候选值，M2在重新读取并验证实际bytes的SHA-256后再次派生，同一值才可进入`verified-source-index.json`和后续步骤。

~~~text
sourceFileIdentityMaterial = canonicalJson({
  "path": canonicalRepositoryRelativePath,
  "gitMode": "100644" | "100755",
  "sizeBytes": nonNegativeInteger,
  "sha256": lowercaseHex64
})

fileId = "file:" + lowercaseHex(SHA-256(
  frame(UTF8("verified-source-file-id-v1")) ||
  frame(sourceFileIdentityMaterial)
))
~~~

path保留已校验Git repository-relative UTF-8 bytes；不得做Unicode、大小写或locale归一化。identity material**恰好**四个字段，不加入snapshot、origin、registration、Git blob ID、media type、analysis disposition、text encoding、line index、private root、时间或执行信息。因此同一path、mode和bytes在不同私有root、分片顺序或snapshot中得到同一`fileId`；它属于哪个snapshot由`(snapshotId,fileId)`成员关系证明。传入/持久化的`fileId`与此重算结果不一致一律是`CAPTURE_IDENTITY_INVALID`，不得用相邻路径、simple name或旧前缀回退。

source-inventory.jsonl按path排序，每行一个`source-inventory-entry-v2` canonical JSON object并以LF结束；所有regular files均出现。analysis step receipt绑定exact artifact names、size和SHA。

### 8.4 预算和安全

capture与VerifiedSourceInventory至少执行maxFiles、maxTotalBytes、maxFileBytes。capture以受约束Git CLI plumbing读取raw objects，拒绝promisor/alternates/replace/graft与任何lazy fetch；VerifiedSourceInventory先stat/size gate，再分配或解码，读取后复查identity。private snapshot handle、每个祖先目录段和文件都NOFOLLOW。所有regular files hash；只解码ANALYZABLE_TEXT。无live ref、工作区读取、网络、客户build/runtime或任意fallback。

### 8.5 稳定 failure codes

capture：LOCAL_GIT_REQUEST_INVALID、LOCAL_GIT_REPOSITORY_INVALID、LOCAL_GIT_COMMIT_NOT_FOUND、LOCAL_GIT_OBJECT_CORRUPT、LOCAL_GIT_PROMISOR_UNSUPPORTED、LOCAL_GIT_ALTERNATES_UNSUPPORTED、LOCAL_GIT_TREE_ENTRY_UNSUPPORTED、LOCAL_GIT_NETWORK_FORBIDDEN、LOCAL_CAPTURE_INSTALL_FAILED。

VerifiedSourceInventory：REQUEST_SCHEMA_INVALID、CAPTURE_IDENTITY_INVALID、SOURCE_REGISTRATION_NOT_FOUND、PROFILE_REFERENCE_INVALID、SOURCE_HANDLE_INVALID、SOURCE_PATH_INVALID、SYMLINK_FORBIDDEN、GITLINK_FORBIDDEN、DUPLICATE_SOURCE_PATH、SOURCE_NOT_REGULAR、SOURCE_SIZE_MISMATCH、SOURCE_HASH_MISMATCH、SOURCE_TEXT_DISPOSITION_INVALID、SOURCE_MEDIA_PARSER_FORBIDDEN、VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED，以及共享store的ARTIFACT_POLICY_REGISTRY_INVALID、ARTIFACT_POLICY_NOT_FOUND、ARTIFACT_POLICY_MISMATCH、MODULE_INSTALL_REQUEST_INVALID、MODULE_PAYLOAD_NOT_CANONICAL、MODULE_PUBLICATION_COLLISION、MODULE_PUBLICATION_INVALID、ANALYSIS_STEP_INSTALL_REQUEST_INVALID、ANALYSIS_STEP_PUBLICATION_COLLISION、ANALYSIS_STEP_PUBLICATION_INVALID、ATOMIC_MOVE_UNSUPPORTED。实现不得折叠成任何未声明的粗粒度安装失败别名。

错误只暴露 stable code/run receipt ID，不包含绝对路径、源码或 stack trace。fatal 不安装 partial success 目录。

### 8.6 测试 seam 与验收

- 等价 bytes 位于不同 root 或 inventory 乱序时，records/IDs/JSON bytes 相同。
- 用测试内创建并commit的确定性本地Git仓库（至少两个text blobs、一个binary blob、一个100755 blob）验证full 40-hex capture；commit后任意修改index/worktree不改变snapshot/receipt/registration。fixture不得依赖网络、当前仓库或下载的第三方repository。
- synthetic repos分别含symlink、gitlink、unknown mode、alternates、promisor marker与missing object，断言capture无可用registration并返回精确stable code；binary fixture必须进入`NON_ANALYZABLE_MEDIA`且hash/count闭合、Provider/parser调用0。
- hash、size、UTF-8、duplicate、path traversal、root/ancestor/file symlink mutation 在任何 parser 前失败。
- 边界预算通过，超一失败；不截断 inventory。
- source input 注释或内容不能影响 request/schema 行为。
- artifact staging 不完整、跨文件系统原子移动不可用或 addressed collision 均 fail closed。
- M1/M2/M3各自必须有payload+`module-receipt.json`，其中M3恰有三个semantic payload；AnalysisStep store必须fresh reopen M3并最后创建analysis step receipt。下一模块只用store reopen结果；request bytes、原Java对象直传或M3预报root/receipt的测试trap必须失败。

验收fixture必须同时覆盖上述deterministic synthetic local Git capture、八文件DepotHead正向冻结、空inventory、单文件hash mutation、symlink/path escape、binary media与不同private root的byte-identical replay。只有capture产出完整snapshot/receipt/registration，VerifiedSourceInventory三模块各原子安装payload+receipt，M3 exact-three descriptors经AnalysisStep store形成三个semantic files+store-last receipt、八条inventory/相同v2 snapshotId与canonical bytes，且全部反例在parser前以指定code失败，VerifiedSourceInventory才算可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- 分析步骤“已验证源码清单” 只验证上游声明的离线 inventory；不发现、扩展、刷新或“修好”来源。
- 上游本地capture固定为`LocalGitCommitCaptureAdapter`的受约束Git CLI plumbing：exact 40-hex object ID、raw object bytes、完整tree；不新增JGit依赖，不读ref/index/worktree，不允许promisor/alternates/replace或联网补object。
- 空 inventory 非法；声明文件必须全量验证，不能用抽样、best effort 或成功百分比替代。
- 所有tracked regular files均属denominator并验hash；binary标`NON_ANALYZABLE_MEDIA`且禁止parser，但不遗漏。symlink、submodule/gitlink与未知mode使capture失败。
- public/analysis transport只含`sourceRegistrationId`和content-addressed refs；private registry可内部保留local locator，本地path只在capture CLI/private registry，且不得进入public registration/identity/error；source locator必须repository-relative。
- `ExpectedOriginV2`只有`GIT_SHA1_COMMIT`；repository identity和40位revision分别逐字绑定到已登记capture的declared identity和SHA-1 commit。每个verified regular file只使用8.3.1的`file:` identity preimage；不得保留或接受`source-file:`别名。
- 只有M3三个semantic payload先完成module publication、再由AnalysisStep store安装完整analysis step set并最后写receipt，才是成功；partial directory或M3自己预报analysis step receipt/root永远不是完成点。
- 每个模块payload与`module-receipt.json`先由`CanonicalModuleArtifactStore`立即原子安装；下一模块只消费reopen结果，不能传draft对象。
- 只有 profile 已明确列出的可隔离情况能成为 Gap；identity、路径、安全、hash、UTF-8 和 install 问题一律 fatal。
- 内部 I/O buffer、hash library 和 class/package 划分可自行选择；文件名、字段语义、identity material、failure 分类和上述出口不可改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.inventory`已开始形成源码清单纵切，而不是只有package骨架。共享artifact包除`SOURCE_ANALYSIS/v1`通用头门禁外，`CanonicalModuleArtifactStore`可对已注册的M1 `admitted-source-request.json`、M2 `verified-source-index.json`和M3恰好三项`source-input.json`、`verified-snapshot.json`、`source-inventory.jsonl`做receipt-last原子安装与fresh reopen。M3的独立JSON和JSONL身份会重算，文件组和输入顺序是封闭的。

另有一条独立、仍不构成分析步骤成功的Local Git capture纵切：`LocalGitCommitCaptureAdapter`只接受标准本地`.git`目录中的小写完整40位commit，以不经shell的`cat-file`和NUL tree枚举读取raw blob，在私有workspace原子保存manifest、receipt、blob和rootless registration。`LocalGitSourceRegistry`可以仅凭registration ID重新打开并核验这三份私有文档，返回rootless、无源码字节的capture view；synthetic fixture已验证文本、二进制、100755、工作区修改无影响、symlink拒绝及自报identity一致；没有调用真实jshERP或任何网络来源。它尚未由正式执行器与M1 writer连通，也尚未提供M2/M3 publication或analysis-step store，因此不能声称已完成源码盘点。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 工程身份与package已切换，项目使用JDK 17 Toolchain；`analysis.inventory`和`capture.localgit`目标位置存在。共享module store已能持久化三种已注册模块形状。 |
| **已实现（独立capture纵切）** | `LocalGitCommitCaptureAdapter`已在synthetic local Git repository上按exact commit读取raw tree/blob并安装manifest、receipt、content-addressed blob与path-free registration；`LocalGitSourceRegistry`已能只凭registration ID fresh-reopen并核验registration、receipt和manifest，且其返回值不泄露workspace/blob路径或原始字节。它对text/media/100755分类，拒绝tree symlink，且工作区修改不会影响相同commit的capture identity。 |
| **已实现（M1准入与落盘 writer；尚未端到端组装）** | `FrozenRequestAdmission`已严格解析canonical `analysis-run-request-v2`的ROUND_1/ROUND_2顶层形态，对注入的rootless capture/profile view核对source-registration、frozen request、profile/budget、canonical path、完整regular-file分母与资源预算，并按UTF-8 path顺序生成`AdmittedSourceRequest`。`AdmittedSourceRequestModulePublisher`已将该结果与八项精确上游引用写为`admitted-source-request.json`及store-last module receipt，并可fresh reopen；其direct selector验证完整capture的text/media payload、上游闭包和无Path seam。它不读来源字节，也尚未由private source-registration registry、真实run request/frozen request reader与执行器驱动，M2 reader尚未实现。 |
| **已实现（M2字节核验核心，尚未发布）** | `VerifiedSourceIndexer`只通过注册表提供的opaque snapshot handle重新读取M1准入的每个文件。它逐项重新核对capture身份、path/mode/size/SHA/处置和统一`file:` ID；文本以strict UTF-8解码、拒绝NUL/禁用控制字符并建立稳定的行起始byte索引，媒体保持无文本索引。synthetic测试已覆盖text/media分区、稳定ID以及同长度单字节漂移的`SOURCE_HASH_MISMATCH`拒绝。它尚未从已发布M1 payload读取输入、尚未安装M2 module artifact/receipt，也尚未形成reader-visible步骤输出。 |
| **已实现（M3投影与步骤公开集，尚未由真实M1/M2驱动）** | `VerifiedSourceInventoryPublicationSpecifier`已经从fresh-reopened的synthetic M1/M2 module publications、按完整`ArtifactReference`读取并重验的run/frozen request bytes，构造`source-input.json`、`verified-snapshot.json`与`source-inventory.jsonl`；随后`CanonicalAnalysisStepArtifactStore`原子安装三项并最后写`verified-source-inventory-receipt.json`。直接测试覆盖三项文件、M3 provenance、input hash与receipt-last store重开。这是M3组合纵切，不等于真实客户代码的盘点。 |
| **本步骤生产能力尚未实现** | M1与private source registry的正式组装、M1 module artifact parser、M2从已发布M1重开并安装module artifact/receipt、以及统一执行器仍不存在。故当前M3只能消费已构造的canonical上游publication，不能从已登记客户commit生成四项reader-visible输出；capture纵切、M1 writer/M2纯核心、M3投影和共享store均不能代表完整本步骤或任何jshERP结果。 |
| **历史证据，不是当前能力** | 已删除的pre-reset纵切曾在小型synthetic repository上验证只读capture、text/media disposition、hash与原子重开。这些结果只保留在Git历史/progress中，不能作为当前SourceAnalysis artifact或jshERP运行结果。 |
| **下一实现门** | 按本章M1→M2→M3合同重新实现并通过完整tree、binary、symlink/gitlink、single-byte drift和不同root测试；随后才可对已批准完整jshERP commit做离线验收。 |

本章目标不会因历史纵切被删除而降级，也不能因为package骨架和wire头门禁存在就声称源码已经冻结或盘点。
