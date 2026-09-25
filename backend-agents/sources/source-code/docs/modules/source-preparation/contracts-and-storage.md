# 源码准备：数据、异常、版本和保存合同

状态：2026-09-25，详细设计实施中。准备记录、请求及身份合同的32项直接测试通过，独立审查的5项缺口已修正并复核；步骤1完成。实际读取、wire写入/重开及来源消费检查尚未完成，不能据此宣称准备功能已可用。本文是字段和状态的唯一 owner；[模块设计](README.md)定义算法，[命令设计](cli-and-skill.md)定义用户操作。

## 1. 三个不同的问题

结果必须分别回答：

1. `inspectionStatus`：请求范围检查完了吗？`COMPLETED / ABORTED`。强杀前没有最终结果，不虚构 ABORTED 文件。
2. `persistenceStatus`：报告保存了吗？CLI envelope 为 `SAVED / NOT_SAVED / INCOMPLETE`。只有 receipt 安装且可重开才是 SAVED。
3. `readiness`：这份范围能给后续分析使用吗？如下表。

| readiness | 具体意义 | 后续来源句柄 | 运行终态 | CLI退出码 |
| --- | --- | --- | --- | --- |
| READY | 检查完成，所有纳入范围已保存核验，至少1个可分析文本，没有跳过/排除范围 | 允许 | FINISHED | 0 |
| READY_WITH_EXCLUSIONS | 同上，但有明确排除或按既定规则跳过的条目/路径 | 允许，只含有效范围 | FINISHED | 0 |
| NEEDS_DECISION | 文件/子目录仍有可通过重新准备或排除解决的问题，或资源控制留下未处理范围 | 禁止 | FAILED，保留结果 | 3 |
| NO_ANALYZABLE_TEXT | 检查结束且无未解决问题，但没有可分析文本；含空目录、全媒体或全部排除 | 禁止 | FAILED，保留结果 | 3 |
| BLOCKED | 根来源、身份、安全边界、请求或可靠保存不能成立 | 禁止 | FAILED（若已创建运行） | 2；输出失败为4 |

结果安装为 `SUCCEEDED` 或 `SUCCEEDED_WITH_GAPS` 只说明检查产物安装成功，不代替 readiness。继续满足现有store的“空gapRefs对应SUCCEEDED，非空对应SUCCEEDED_WITH_GAPS”，不改全局校验。新producer把每条实际范围限制/OPEN问题以及空文本结果写成SourceIssue：排除/链接等为INFORMATIONAL，空文本用SOURCE_NO_ANALYZABLE_TEXT。`gapRefs`是这些issue的 `source-issue:<issueId>` 排序唯一集合，并由新reader核对都能在issues文件找到。只有READY且没有限制/问题时集合为空。已解决且不再限制当前范围的旧问题留在历史记录，不作为当前gap。不是为了凑状态编造业务Gap；每条都对应真实文件、范围或结果事实。reader仍根据readiness决定是否提供源码。

参数/配置不能解析时可以没有 runId，仍返回终端问题。JSON无法发送或进程被强杀时不能保证结构化终态；Unix信号退出保留系统行为，不伪造应用退出码0。

内部 Java 合同将检查原始记录与评估结果分开：`SourcePreparationResult` 保存检查状态、明确的枚举完成标志、条目、问题、未知子目录及未命中排除声明；`SourcePreparationReadinessEvaluator` 从这些记录产生唯一的不可变 `SourcePreparationAssessment`（汇总和可用状态）。正式 result 文件仍保存汇总和 readiness，但由生产者封闭时计算，不接纳调用方另行传入的互相矛盾的值。

`ABORTED` 不直接等于 `BLOCKED`：例如资源上限中止应为 `NEEDS_DECISION`。没有更强阻塞问题的中止也必须为 `NEEDS_DECISION`，不能因已处理文件均正常就返回 READY 或 NO_ANALYZABLE_TEXT。已明确排除的未知子目录以及固定政策跳过的 `.git` 目录不再阻塞有效范围，但总文件数仍为未知；已通过刷新解决、当前不再限制范围的历史问题也不将新结果降为 `READY_WITH_EXCLUSIONS`。

## 2. 输入和私有请求

`SourcePreparationRequest` 固定字段：

| 字段 | 类型及规则 |
| --- | --- |
| `operation` | NEW / REFRESH / EXCLUDE |
| `origin` | DIRECTORY 或 GIT_COMMIT 的有标签结构；含逻辑来源身份，Git含 exact commit；本机根路径仅在私有配置/请求 |
| `basePreparation` | NEW为null；其它为完整已保存准备引用，不能只存字符串路径 |
| `targets` | REFRESH/EXCLUDE的非空 `{relativePath, kind: FILE或DIRECTORY}` 列表；NEW为空 |
| `declaredExclusions` | NEW的原始配置声明；派生操作校验它与基础的原始声明一致。另存 `effectiveExclusions`，等于继承的累计集合加本次EXCLUDE targets；REFRESH不能用原配置覆盖累计集合 |
| `limits` | 明确的 `maxFiles / maxTotalBytes` 正整数；配置错误不偷偷用默认裁剪值 |
| `policyVersion` | 固定 `source-preparation-policy-v1`，含链接、.git、无压缩展开等规则 |

在私有请求中保存使用的真实配置及来源定位；公共请求只引用它。结果展示不把绝对路径当 source locator，不记录凭据。输出目录不是来源语义，来源身份不能由输出路径或时间生成。

## 3. 文件和目录记录

`source-inventory.jsonl` 按 canonical 相对路径的 UTF-8 字节排序，一路径一条。根目录不占普通文件数。行 schema 为 `source-preparation-entry-v1`：

| 字段 | 类型/含义 |
| --- | --- |
| `relativePath` | 根内规范相对路径，唯一 |
| `entryKind` | REGULAR_FILE / DIRECTORY / SYMLINK / SUBMODULE / OTHER / UNKNOWN |
| `disposition` | ENUMERATED_DIRECTORY / VERIFIED_TEXT / VERIFIED_MEDIA / EXCLUDED_BY_USER / SKIPPED_SYMLINK / SKIPPED_GIT_METADATA / UNAVAILABLE / UNCHECKED / UNSUPPORTED；ENUMERATED_DIRECTORY仅表示该目录项已枚举，不声明子树全部成功 |
| `sizeBytes / sha256 / blobRef / fileId` | 成功核验普通文件必填；未知时null，不填0或伪hash；排除记录可保留基础版本已知值，但 reader 仍不得返回其内容 |
| `textEncoding` | VERIFIED_TEXT为UTF-8，其它null；不保存行索引或摘要 |
| `originAttributes` | Git为真实mode/blobObjectId；普通目录没有伪Git字段，仅可选实际executable属性 |
| `observations` | 已知的读前/后长度、身份/mtime等私有诊断值或其引用；没有观测则null |
| `issueIds` | 引用本结果问题清单 |
| `inheritedFrom` | 从哪一基础准备继承，或null；不等于重新读取 |
| `exclusion` | 排除原因类别、明确决定来源和覆盖路径；非排除为null |

成功 DIRECTORY 条目本身不代表内部所有文件成功。无法列出或明确不进入的子目录另入 `unknownSubtrees`；可以知道该目录存在，但不能声称其中有0个文件。隐藏文件不是异常，二进制不是“未检查”。

汇总计数：`discoveredRegularFiles`、`verifiedTextFiles`、`verifiedMediaFiles`、`excludedKnownFiles`、`unavailableKnownFiles`、`uncheckedKnownFiles`；已发现普通文件由这些互斥集合组成。目录、链接和submodule分别用 `directoryEntries / symlinkEntries / submoduleEntries` 计数，不混入普通文件计数。

排除路径未命中另列 `unmatchedExclusions`，是精确 `{relativePath, kind}` 列表，由读取方记录在原始 `SourcePreparationResult`，评估器原样保留到汇总。不存在的声明不制造 UNKNOWN 文件记录，也不增加已发现或不可读文件数；它仍是继承的有效范围限制，有可用文本且没有其它问题时为 READY_WITH_EXCLUSIONS。未命中本身不代表存在未知子目录。

`excludedKnownFiles` 包含明确用户排除和固定政策跳过的已知普通文件；例如普通目录中的 `.git` 元数据可能是普通文件，仍以 `SKIPPED_GIT_METADATA` 留在清单中，不打开其内容。正文说明必须区分用户排除与政策跳过，不能把两者都说成用户选择。

有未知子树或遍历提前终止时 `totalRegularFiles=null`、`enumerationComplete=false`。禁止将“本次已见文件数”冒充整个目录文件总数。明确排除解决消费范围后，readiness可以READY_WITH_EXCLUSIONS，但全物理目录总数仍可能未知。

## 4. 结构化问题

错误码继续保留；结构化分类是控制流依据，中文说明供人阅读。新增码是设计目标，不声称已有实现。

`SourceIssue` 字段：

| 字段 | 类型/规则 |
| --- | --- |
| `issueId` | 本准备结果内唯一稳定ID；根据分类、路径、操作及观测摘要产生，不用中文消息匹配 |
| `code` | 具体稳定错误码 |
| `category` | REQUEST / ACCESS / SOURCE_CHANGE / SAVED_CONTENT_INTEGRITY / UNSUPPORTED / RESOURCE_LIMIT / OUTPUT / INTERNAL |
| `scope` | REQUEST / ROOT / DIRECTORY / FILE / OUTPUT |
| `relativePath` | 来源内路径；根/配置错误可为null；拒绝的原始选择值放私有诊断，不拼接后读取 |
| `operation` | VALIDATE_REQUEST / LIST_DIRECTORY / READ_INPUT / READ_SAVED_CONTENT / VERIFY_IDENTITY / SAVE_OUTPUT / REOPEN_RESULT |
| `message` | 明确主语和对象的可读说明；未知原因写未知 |
| `expected / observed` | 有类型的小对象或null，只写实际已知hash/长度/来源ID等；不保存全文或密钥 |
| `resolution` | OPEN / RESOLVED_BY_REFRESH / RESOLVED_BY_EXCLUSION / INFORMATIONAL |
| `allowedActions` | REFRESH_FILE / REFRESH_DIRECTORY / EXCLUDE_FILE / EXCLUDE_DIRECTORY / FIX_CONFIGURATION / FIX_OUTPUT / NEW_PREPARATION / CONTACT_MAINTAINER 的实际允许子集 |
| `diagnosticRef` | 可选私有诊断引用；不存在时null |

问题矩阵：

| 情况 | 范围与程序行为 | 用户动作 |
| --- | --- | --- |
| SOURCE_ROOT_NOT_FOUND / SOURCE_ROOT_LIST_FAILED（新增） | 根级BLOCKED；不再读取；返回已知原因 | 修正配置/权限后NEW；不能排除根 |
| SOURCE_ENTRY_READ_FAILED / SOURCE_DIRECTORY_LIST_FAILED（新增） | 文件/子树NEEDS_DECISION；继续独立范围 | 刷新具名范围或明确排除 |
| SOURCE_CHANGED_DURING_READ / SOURCE_ENTRY_MISSING / SOURCE_ENTRY_TYPE_CHANGED（新增） | 不接纳不稳定字节；保留问题与旧版本 | 刷新或明确排除 |
| SOURCE_SIZE_MISMATCH / SOURCE_HASH_MISMATCH（已有） | 明确被检查对象是输入还是保存副本；不推断变化原因 | 能安全定位原来源时刷新；或明确排除；不继续使用坏副本 |
| SOURCE_PATH_INVALID / DUPLICATE_SOURCE_PATH（已有） | 请求/清单问题，不读取危险目标；重复项不得任意取一份 | 修正请求；损坏清单需新准备，不能确认接受错误身份 |
| CAPTURE_IDENTITY_INVALID（已有） | 整体来源不可信，BLOCKED | 修正来源/重新准备；不能文件级跳过绕过 |
| UNSUPPORTED_ENTRY（新增） | 链接按既定规则INFORMATIONAL跳过；submodule/其它不支持项OPEN | 支持显式排除；不执行特殊文件或外部代码 |
| VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED（已有） | 停止超过控制的读取，列未检查范围 | 改明确资源配置后NEW，或排除已知范围；不自动缩小分母 |
| SOURCE_OUTPUT_FAILED（新增） | 停止依赖失败存储的操作；准确返回SAVED/INCOMPLETE/NOT_SAVED | 修正输出后NEW；不能“忽略错误当已保存” |
| SOURCE_PREPARATION_INTERNAL_ERROR（新增） | 停止可能不安全的处理，尽可能返回诊断 | 调试/修复；不冒充普通文件问题 |
| SOURCE_BASIS_MISMATCH（新增） | 后续新执行在首个工具/模型调用前拒绝材料 | 选择该材料原来源查看，或另行安排新版本取材；不自动重算 |

相同文件可能有多个观测问题，但只有一个最终处置。明确排除可以关闭该范围的文件访问/变化问题，不能关闭来源整体身份错误、错误请求或输出失败。检查结果中的allowedActions不是授权，Codex仍要等用户选择。

## 5. 源码版本和排除继承

`PreparedSourceReference` 完整保存 sourceVersionId、ownerRunId、步骤地址、receiptId/sha256、schema及policy引用。目录名、commit、短ID不够定位与信任一个版本。

新 sourceVersionId 对 canonical 内容计算：来源种类与逻辑身份、Git exact commit（仅Git）、父sourceVersionId、操作/明确范围变化、最终文件记录的路径/类型/内容身份/处置、排除路径及未知范围、有效源码政策。无行索引、时间、宿主绝对路径、模型/并发信息。完整产物ID另外覆盖全部实际文件bytes；不能用sourceVersionId跳过磁盘完整性检查。

该ID沿用 `snapshot:<64hex>` 语法，哈希输入增加固定命名空间 `source-preparation-basis-v1`，不与历史Git snapshot配方混用。新普通文件 `fileId = source-file:SHA256(canonical{format:source-file-v2,path,entryKind:REGULAR_FILE,sizeBytes,sha256,originAttributes})`；scope/exclusion不伪装成文件内容变化，仍进入sourceVersionId。历史fileId继续原公式。

本机根路径不进入公共内容ID，但私有基础请求保存规范来源定位。REFRESH/EXCLUDE核对kind、逻辑identity及规范根路径一致，Git还核对commit；同名不同目录必须NEW，不能混入继承内容。基于旧结果仅做EXCLUDE无需原目录当前可读，但仍核对记录的定位，不重新解析链接借机改根。

派生操作总是新结果/新版本，不原地编辑父版本。排除继承于这条显式父版本链；独立NEW的排除来自该次配置。该粗粒度版本策略意味着即使一次刷新没有改变字节，也不能默认把旧checkpoint升级为新版本；这是首版避免假定依赖影响的边界。

普通目录版本不宣称所有文件在同一时刻读取；结果列出继承/重新准备的范围。Git派生版本允许同提交的范围排除或重新取得，禁止混合提交。仅日志措辞变化不能改变已保存源码版本。

## 6. 四文件与原子安装

新 producer 为 `verified-source-inventory` v3，内部模块地址沿用；新正式payload固定四份：

| 文件 | Artifact type | Artifact schema |
| --- | --- | --- |
| source-input.json | SOURCE_PREPARATION_INPUT | source-preparation-input-v1 |
| source-inventory.jsonl | SOURCE_PREPARATION_INVENTORY | source-preparation-inventory-v1；行source-preparation-entry-v1 |
| source-issues.jsonl | SOURCE_PREPARATION_ISSUES | source-preparation-issues-v1；行source-preparation-issue-v1 |
| source-preparation-result.json | SOURCE_PREPARATION_RESULT | source-preparation-result-v1 |

无问题JSONL是合法零字节；根不存在可有零清单，但必须有问题和BLOCKED结果。`source-preparation-result` 包含operation、basePreparation、sourceVersionId（无法形成安全来源时null）、inspectionStatus、readiness、计数/unknownSubtrees、完整文件/问题引用及scope声明。blob存储/注册由input中的typed引用定位。

先在本运行私有临时目录流式落bytes，再封闭结果并调用现有canonical安装。receipt只在所有正式文件验证后安装。禁止将先写一个“成功.json”当提交标记，也不对客户目录写文件。不完整临时内容保留供诊断，不能供reader当正式来源。

已处理的文件问题可以形成完整检查publication；结果中不可用条目没有可消费blob。保存失败若无法安装publication，CLI只返回实际可用的内存摘要及诊断位置；`outputRef=null`，不能打印一个不存在的正式receipt。

每个输入文件至多按本次算法读取一次；publisher不二次读取源码。必要磁盘重开校验不等于重新扫描客户源。单文件更新继承旧blob时重新核验所打开存储边界，发现坏副本即报告，不把它复制为新成功内容。

## 7. 版本迁移和运行登记

| 合同 | 本轮处理 |
| --- | --- |
| Git capture/registration v1 | 原样严格读；保留历史不安全来源拒绝规则，不改旧schema |
| 新来源注册 | source-registration-v2、source-capture-receipt-v2、source-snapshot-entry-v2，origin显式区分目录/Git，普通目录无伪Git字段；允许条目检查状态 |
| 原M1/M2/v2源码产物 | 只读历史；新M1/M2的输入/索引合同升v3，删除行摘要、增加结果与排除信息；不静默兼容旧shape |
| 新准备请求 | 私有source-preparation-request-v1；公共analysis-run-request-v3增加PREPARE_SOURCE及引用，保留v2严格读 |
| 原YAML v2/v3 | 原样读历史/现有业务；源码准备使用独立轻量source-preparation-config-v1，仍由同一个--config和同一CLI解析，不加载整份模型/JDT配置 |
| 已有分析入口选择新来源 | repository-run-config-v4增加显式source分支：LEGACY_REGISTRATION或PREPARED_SOURCE；准确字段见CLI合同。不改变技术/模型配置的原职责，不把新准备配置当整条分析配置 |
| run output | 写analysis-run-output-v7；增加SOURCE_PREPARATION及sourcePreparationCheckpoint，允许失败运行中的完整检查结果；历史v3–v6严格读 |
| 所有新分析执行的来源绑定 | 排队前必须保存selectedSourceBasis到请求v3/output v7；来源来自配置指定的registration或preparedSource，不从待消费材料反推expected。必须和材料实际basis一致；历史只读无需新绑定 |
| Step03/04/05、Activity/Process正文 | 不改变数据或业务算法；从准确上游引用取得basis，必要公共typed view移除Git强制要求 |
| Artifact policy registry | 新建有新schema的policy文件；旧policy字节和内容ID保留，禁止原地修改导致旧receipt无法重开 |

`AnalysisRunOutput` 不可用假Step05或假M10表示准备完成；SOURCE_PREPARATION只能带自身结果，无Activity/Process/report。新的result reader与原v2 reader按准确schema/producer分流，不“缺字段就猜旧版”。

历史来源basis从其真正Step01/capture引用求得，保留历史命名空间；不能只用同commit把历史材料等同新准备版本。新执行的选定basis先持久绑定；历史查询不要求与一个全局“最新版本”相同。

运行在RUNNING时保存output，再转终态。部分/失败准备的inspect/artifact允许读取已安装检查文件；原公共读取方法另行要求READY。参数无效且run尚未创建时不伪造runId。

### 7.1 不依赖模型/JDT配置的真实controls

轻量loader在私有注册区保存canonical描述，得到真实typed引用与SHA：`source-preparation-profile-v1`来自有效范围政策/资源配置；`source-preparation-toolchain-v1`来自准备producer/build身份、Java运行版本及Git模式下的受信Git版本；`source-preparation-schema-bundle-v1`来自随程序发布的实际schema清单；resourceBudget来自本次limits。`ArtifactControls`的profile/toolchain/schema/hash均从这些真实bytes取得，promptBundleSha256为null。不得填伪JDT/模型hash，也不要求用户手工造控制文件。

这些引用写进私有准备请求、正式input及receipt。后续inspect/reopen使用保存的引用，不根据当前Java版本重新计算旧controls。配置内policyRegistry是完整可验证文件；新schema用新policy文件，旧文件保留。

### 7.2 新来源到已有只读技术视图的确切映射

保留已有VerifiedSourceTextSet的字段语义，不伪造旧verified-snapshot文件：

| 既有字段 | 新来源映射 |
| --- | --- |
| snapshotId | 本次sourceVersionId，保持snapshot前缀 |
| inventoryScopeKind | 无跳过/排除且范围完整用COMPLETE_CAPTURE；明确缩小范围用BOUNDED_PATH_SET |
| repositoryCompletionEligible | 仅完整原始声明范围、无排除/未知子树时true；READY_WITH_EXCLUSIONS为false，不妨碍对明确范围提供只读句柄 |
| capabilityProfileRef | 准备时生成的source-preparation-capability-v1引用，描述来源读取/编码政策，不声称发现Java/Spring；真正技术能力仍由后续原模块判断 |
| sourceInventoryRef | 新source-inventory.jsonl准确引用 |
| verifiedSnapshotRef | 新source-preparation-result.json准确引用；名字为历史Java字段，不生成假旧schema。对应消费者按实际新schema接纳并保持同源比较 |
| controls | 7.1保存的真实准备controls |
| documents | 仅VERIFIED_TEXT且未排除的记录，完整rawUtf8；普通目录originAttributes没有gitMode，修改该typed view的强制Git条件，保留历史有名构造/reader |

scope/readiness和来源ref贯通ApplicationProfile的透传、JavaCodeIndex的snapshotRef、PersistenceAnalysisRequest的同源比较；不运行或改写它们的分析算法。变更的是来源合同适配和原样透传，不是把准备模块包装成旧Git输出。

未来技术运行的工具/profile controls仍由技术运行配置产生，不要求等于准备工具controls；比较的是各准确上游引用及其owner的真实controls，不替换历史controls。本轮用fixture验证这些Java消费者的适配；不启动客户技术分析、不新增后续CLI或悄悄改变现有plan-materials执行范围。

## 8. 返回例子与一致性

以下是人工说明投影，短ID不作为可重放fixture：

```json
{
  "runId": "示例R1",
  "inspectionStatus": "COMPLETED",
  "persistenceStatus": "SAVED",
  "readiness": "NEEDS_DECISION",
  "knownVerifiedTextFiles": 99,
  "unavailableKnownFiles": 1,
  "unknownSubtrees": [],
  "outputRef": "示例完整receipt引用",
  "problems": [{"relativePath":"src/Legacy.java","operation":"READ_INPUT","code":"SOURCE_ENTRY_READ_FAILED","allowedActions":["REFRESH_FILE","EXCLUDE_FILE"]}]
}
```

人类输出必须与结构化值一致：“检查结束；99个文本文件可用，1个文件无法读取；报告已保存，尚不能把这份范围交给后续分析。可以修复后重新准备该文件，或明确排除。”不能写“全部成功”或仅写FAILED。

自动化必须验证完整wire而非仅此投影；hash/fileId均由fixture真实字节计算。
