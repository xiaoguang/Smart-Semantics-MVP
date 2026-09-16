# 模块详细设计：复用已有材料，先阅读再重建

状态：本文记录已实现取材模块和复用基础；最新目标由[系统认识与三阶段设计](business-reasoning-and-writing.md)拥有。当前生产是完整阅读包后的双轮过程；目标仅候选过程改为事实DRAFT→WRITE→最终RULE_REVIEW。冻结读取、选材、阅读和正式接线已经实现，待改项见[实施状态](implementation-status.md)。两项深Interface保持不变。

```java
ProcessDiscoveryResult BusinessProcessDiscovery.discover(ProcessDiscoveryRequest request);
BusinessProcessPublication BusinessProcessPublisher.publish(ProcessDiscoveryResult result);
```

## 1. FrozenAnalysisCorpus：一次打开已有资料

### 目的、输入与复用

统一打开 ReviewedActivity、Activity coverage、M10 和同源 verified inventory 的**完整保存引用**。旧目录引用另外交给 Cataloger。Activity 批次与材料来源运行可不同，仍须指向同一源码 basis；新过程输出归新批次所有。

保留现有按 Activity ID、statement handle 和 SourceRef 的查询。注入已有 `VerifiedSourceTextReader`，从保存 registry 字节打开冻结文本集合。底层一次加载很多文件不等于解析、扫描，也不代表全部文件都发送给模型。不得读当前 checkout、联网、执行文件或启动 JDT。

### 最小内部读取合同

```text
activities()                         → 全部 Activity 导航
activity(id) / statement(handle)      → 已审完整内容
source(ref)                          → M10 已存片段
files()                              → 冻结相对路径、类型、行数
read(fileKey, range | WHOLE_FILE)     → 原文及实际范围
search(fileKeys, literal, context)    → 字面量命中、上下文和未返回范围
```

这是同一内部 Module 的职责说明，不要求另建六个模块或公开检索框架。搜索范围只能选已列出的冻结文件，使用字面量而非模型提供的可执行命令；不实现行业检索规则。模型可选已展示的 fileKey/相对路径和行范围，程序解析后才创建来源记录。

`ReadingRecord` 只记录 `requestId、目的、实际文件/行段/原文、返回是否完整、遗漏范围或未找到原因`。合法未命中、无可读文本、已读材料不足是正常结果；库存损坏、错误归属、请求逃出固定来源是 fatal。容量不足明确返回实际范围，不能截断后伪称完整文件。

每个任务内已有文件/片段只加载和发送必要的一份；不建立跨运行全局缓存或新的 hash 链。

### 交付与测试

下游拿到完整 Activity 和真实选中原文。Java 不决定这段原文证明了什么。RED：跨旧候选 Activity、M10 之外冻结 XML/Vue、未命中、范围越界、同源检查、零扫描；GREEN 只接既有 reader 和直接查询。

### 当前实现（读取基础）

`FrozenProcessSourceCorpus` 现为 `analysis.knowledge` 内包可见的只读基础：它只接收已验证的 `VerifiedSourceTextSet`，以稳定 UTF-8 路径顺序给出带 `F#` 键的文件目录，并按键或路径执行一基、闭区间的精确读取和字面量上下文搜索。返回文本直接来自已冻结 UTF-8 字节，保留 LF、CRLF 和末尾未换行文本；整文件或实际覆盖整文件的读取才标记为完整。未知文件、越界范围和逃出仓库相对路径的请求均显式失败。内部 `ProcessDiscoveryRequest` 也已能携带可空的已核验 inventory 引用、与其成对的 `VerifiedSourceTextReader`、旧目录字节和关注问题，旧三/四参数构造仍明确传入四个空可选值。该基础不读取 checkout、网络或活动文件，不解析或执行源码，本基础与已保存reader重开、M10/Activity查询及运行接线已经配合使用。

### 当前实现（读取流水线）

冻结的 Step01/Step05 输入已通过 `inputPolicyRegistry` 专属的 analysis-step reader 重开；正常 `stepArtifacts` reader 仍绑定当前 output registry，仅供本次运行的发布端使用。二者不得全局互换。

在 DRAFT/REVIEW jobs 提交前，已建立全仓 source normalization。现有 completion sink 会按每个成功候选完成时立即验证、归一化并保存该 pair；某个候选或其保存 fatal 后仍 drain 已开始的 jobs 并保存其余合法 pair，但不进行归并或发布。最终结果仍按原 catalog ordinal 聚合。

`DefaultBusinessProcessDiscovery` 已接入一条单一路径：完整旧目录 pair 仅作输入重开；新仓库先保留既有首次目录发现，二者都进入一次 `PROCESS_MATERIAL_SELECTION`。选材与每候选一次 `PROCESS_READING_CHECK` 使用独立 v1 单次决策、轻量全仓 Activity/文件导航、稳定全目录 ordinal provider binding 和现有有界 job pool。所有已保存 M10 ref 都可被 `SOURCE_REF` 显式选择，但不会因与 Activity 有关联而自动发送正文；M10 或冻结文本只有在 `SOURCE_REF`、整文件、范围或字面量请求实际成功后才进入 `sourceExcerpts`，失败/无 reader 记录为限制而不回退到旧 DRAFT 后选源码路线。成员及 context 的完整已审 Activity 进入包；补读只执行一次，随后 DRAFT/REVIEW v3 共用一个不重复正文的 `readingPacket`，REVIEW 再含实际 DRAFT；新过程响应不再有 `requestedSourceRefs`。所有阅读检查完成后，Java 以 `文件、实际范围、原文` 稳定合并各包的局部 `S`，将 DRAFT/REVIEW 的内存副本改写为最终 ref 后才解析、生成过程 ID 并归并；原始响应不改。包内 context 可作 statement/source 依据但不是成员，最终成员才决定 ActivityUse 与处置。单次决策可写读 `process-reading-decision-v1`；过程 pair 保存局部 `readingPacket`、原始 DRAFT/REVIEW 与该包实际 local→final source-ref 映射。运行端在新批次已排队后、Provider 初始化前，通过同一个 CLI 包内装配函数重开 Activity、M10、同源 inventory、冻结 reader 与可选旧目录；它先将这些内容写为私有执行配置再运行 Discovery。新发布 receipt producer 为 v3，但五个读者可见 payload schema 保持 v2/v1；checkpoint reader 严格接受历史 v2 或该 v3 receipt，仍要求同一五文件及原有描述符。这些实现已有直接和本地CI记录；新三阶段仍未实施，两者不得混称。

## 2. RepositoryBusinessCataloger：重开目录与全局选材

### 两条入口

有 `catalogFromModelBatch`：重开已完成目录任务，保留原候选、成员用法和规范化 Activity 处置；**不重跑原 CATALOG/SHARD/MERGE**。没有该参数的新仓库仍按现有首次目录发现；不能删除这条能力。

来源批次必须已停止，目录任务本身须有完整、有效的DRAFT/REVIEW保存记录，并与本次Activity检查点、Activity集合及冻结basis一致。旧批次在目录之后失败不使已完成目录失效。目录缺失、声称完成但损坏或来源不一致明确报错；不能隐式重新调用目录模型补齐。

旧目录是新任务的输入资料，不是新版已审过程。旧提示词不同不妨碍阅读旧目录；但直接复用完整过程结果仍必须满足现有 fingerprint。

### 全局选择输入

目标增量：同一次选择先基于轻量导航和冻结项目说明输出可修正的系统认识/业务假设，再形成候选和调查问题。不使用技术ApplicationProfile作为行业分类。完整合同见[新设计§3](business-reasoning-and-writing.md#3-什么时候判断系统类型)；以下为已实现基础。

- 旧目录候选及成员用法、目的；
- 全部 Activity 的轻量导航卡：ID、name、businessPurpose、businessObjects、terms、已有入口/来源导航；
- 同源冻结文件目录；
- 可选关注问题。

导航卡重新投影仅是 Java 读已有字段，零模型，不重建 Activity 或原目录卡结果。完整条件、规则、公式等不在此丢弃，候选阅读时全部取回。文件目录可展示仓库相对路径和 fileKey；绝不展示宿主绝对路径、run/hash 或凭据。

导航只携带 `statementCount`，不枚举长 `statementHandles[]`；这只压缩全仓导航的重复定位数据。候选的完整 Activity 正文和最终 `readingPacket.statementDirectory` 仍保留所有实际可用 handle，不能以导航裁剪替代原文或证据目录。

### 全局选择输出（单次 `PROCESS_MATERIAL_SELECTION`）

```text
candidateChanges:
  候选局部键、名称、目的/范围、承接的旧候选键
  activityUses（activityId、variant、role）
  contextActivityIds（只为理解而读，不必是成员）
  initialReadingRequests（已有 ref / 文件范围 / 完整文件 / 字面量搜索）
  每项请求的业务疑问、尚未解决问题
oldCandidateDecisions:
  KEEP 或由哪些新候选 REPLACE，附理由
changedActivityDispositions:
  仅受影响且最终不再作为成员的 Activity 的已有合法处置与理由
```

这是阅读决策，不是详细过程，也不声称已经审阅。可以跨候选合读、拆分和重命名；可以从原 SUPPORT_ONLY/UNCLASSIFIED 活动取材。未知 ID 报错；未提到的旧候选按 KEEP 原样保留，不能因模型省略而删除。

继承且未新增首批请求的候选，仍取其全部成员Activity正文并进入一次阅读检查；不自动读取它引用的全部文件，也不由Java编造阅读清单。检查模型可以使用唯一补读机会选择所需原文。保留候选不是直接复用旧过程结果。

一次全局输入超出真实上下文时，先报告未执行范围；不静默取前 N 张卡，不自动重跑旧目录分片。后续若需扩大设计，另行讨论。这里限制模型可容纳的上下文，不增费用门槛。

### 增量覆盖

沿用一份 Activity 处置账：未涉及记录承接原处置；最终候选有成员关系的更新为 PROCESS_MEMBER。旧成员移出全部候选时，模型必须给出现有非成员处置和原因，不能由 Java 猜成 STANDALONE。新增用法、移出用法后重新计算实际成员关系；只作为 context 读过不自动改变处置。

候选最终成员可继续在阅读检查中调整，程序在冻结阅读包时完成上述更新。重建若拒绝/拆分候选，沿用现有候选和过程处置规则。无新账本，不用 326 个 ID 的闭合声称每种 variant 已被理解。

RED：任意 N、跨旧组召回、多用法、未涉及继承、移出最后成员须处置、读取不等于成员、完整旧目录重开。GREEN 不写行业分类器。

## 3. ProcessMaterialAssembler：取原文、检查后封包

### 第一批读取

执行选材清单，按 Activity ID 去重完整正文；用法和 context 身份分别保留。Activity 的目的、对象、参与者、输入、条件、步骤、结果、规则、公式、问题、限制及 statement handle 全部保留。不要只发索引卡。

原文可来自全部 M10 或冻结文本，不要求它预先绑定某个 Activity。Java 按请求取文件和字面量上下文；Mapper `<include>`、Vue mixin、常量等仍由模型判断是否值得补读，不自研 SQL/Vue 解析器。

### 每候选一次 `PROCESS_READING_CHECK`

目标增量：检查显式返回首批实际ReadingRecord的最终保留集合，可移出旁支材料；完整成员/context语义保持。当前supplementaryRequests是追加式实现，待按[新设计§5](business-reasoning-and-writing.md#5-聚焦选材问题决定读什么)修改，历史决策不重写。

检查模型收到：候选、覆盖全仓 Activity 的材料/导航、完整冻结文件目录、第一批实际完整 Activity 和原文、未命中/省略说明。已经完整提供的 Activity 不再重复导航卡；未读 Activity 的导航卡保留 ID、name、businessPurpose、businessObjects、statementCount、入口/来源导航，仅不发送 terms。完整 Activity 与未读导航卡的 ID 并集仍是全部 Activity，CHECK 仍可召回任一 Activity 和冻结文件。允许提出候选范围和成员的修正，以及 `supplementaryRequests`。

检查输出中的 `name`、`purpose`、`scope` 都是 required 且可为 null 的字段；null 明确表示沿用进入检查前的候选值，不能用缺字段表达该回退。

`activityUses` 和 `contextActivityIds` 返回本候选检查后的**完整最终集合**，不是增量修改；即使不调整，也必须完整原样返回输入候选的相应集合。`activityUses` 必须非空，CHECK Schema 以 `minItems: 1` 与现有候选解析器对齐；每个用法仍完整保留 `activityId`、`variant`、`role`。`contextActivityIds` 可以为空，但 `[]` 只表示最终确实没有 context，不能表示“不调整”或“自动沿用”。新增、移出成员和 context 仍由模型明确选择，程序不把空集合猜成继承。

`changedActivityDispositions` 继续只返回必要的增量处置，未涉及记录沿用既有处置；不得因成员集合要求完整而改为重写全仓台账。移出最后成员关系时仍须提供现有合法非成员处置和原因。三项文本字段的 null 继承与两个完整集合的语义分开。

上述成员合同明确化已实现并通过直接及本地 CI 验证：只将私有 CHECK Prompt 从 v1 改为 v2，并给 CHECK `activityUses` 增加 `minItems: 1`。实际 Prompt/Schema 进入既有指纹，使旧 CHECK 不能误复用；全局选择的输入、Schema、Prompt 和指纹保持不变，公共产物、producer、私有 decision 容器及过程输出版本不升版。已保存的空成员 raw response 保持原样，不能由程序补成员后继续，也不能自动重试；新的真实 CHECK 必须取得明确授权。

- `supplementaryRequests=[]`：无实际补读，直接封包。
- 非空：程序执行清单，补入新选 Activity 全文和原文，然后封包。
- 两者都可有 `unresolvedQuestions`。空清单不保证语义完整。

**每个进入重建的候选都调用一次检查模型。** Java 不判断“第一轮够不够”。补读完成后不再发第三次选材；若仍缺共享文件/SQL片段，DRAFT/REVIEW 明确缺口，不伪造补全。

候选检查允许细化名称、用途、成员、范围；为保持此次路径简单，新增全局候选或大拆分由全局选材负责，检查内需要拆分可交给现有过程输出的 SPLIT，不递归创建阅读检查。

### 最终 ProcessReadingPacket

```text
candidate（目的/范围/全部业务用法）
reviewedActivities[]（成员与 context 的完整原文，按 ID 一份）
statementDirectory（所有实际读过 Activity 的可用 handle）
sourceExcerpts[]（已取得的完整片段/文件，含程序分配局部 ref）
readingLimitations[]（未命中、实际省略范围、未回答问题）
```

包必须自包含。当前双轮都收到同一包；目标DRAFT和最终RULE_REVIEW收到该包，WRITE收到完整事实草稿，最终核对另收实际DRAFT和WRITE。首次材料充足仍有一次检查，不强制补读。检查实际最终核对请求容量，不能静默删正文或规则。

### 私有请求容量去重（已实现双轮阅读路径的合同）

实际候选检查暴露的容量问题包含重复包装：`reviewedActivities` 已有原始完整业务字段，却额外附加 `statementHandles[]` 和 `statements[{handle,text}]`；后者再次复制所有字段正文，包级 `statementDirectory` 又列出相同 handle。响应 Schema 还在多个位置重复完整 Activity、文件或 statement 枚举。这些是程序生成的重复表示，不是新增业务资料。

此次最小修正只做两项：

1. 每个已读 Activity 保留现有原始字段及其原文、顺序和全部数组元素，取消额外生成的 `statementHandles`、`statements` 两个字段。完整 canonical handle 仍在唯一的 `readingPacket.statementDirectory` 中；`activityId/字段名/零基序号` 精确定位相应原字段，`businessPurpose` 直接定位文本。不得删除、缩写或重述条件、规则、公式、问题、限制、certainty、来源或任何原始业务字段。
2. 仅对候选 CHECK 和过程 DRAFT/REVIEW 的私有响应 Schema 使用具名 `$defs` / `$ref` 共享完全相同的 allowlist。CHECK 的全仓 Activity 枚举和冻结 fileKey 枚举各保存一份；过程的 packet statement/source 枚举各保存一份，ActivityUse、stage、rule、knowledge 的原字段引用同一定义。枚举值、required、类型、业务结构及 Java 对未知 ID/ref 的拒绝均保持原合同。不是放宽成任意字符串，也不创建通用 Schema 压缩框架。

上述去重保留选中的完整 Activity、首批及补读源码原文。CHECK 和过程继续使用原 canonical Activity ID/statement handle，无需另加短键转换、来源账本或新存储协议。DRAFT/REVIEW 使用完全相同的完整包，REVIEW 额外携带完整实际 DRAFT。已有 SourceRef 的局部到最终编号映射不受影响。

用户另已批准一个仅限 `readingCheckInput` 的导航投影：从本次 CHECK 输入的副本移除该任务不用的 `readingPacket.statementDirectory`；按已实际完整提供的 `reviewedActivities` ID 去掉重复导航卡；仅对其余未读导航卡省略 `terms`。不得修改共享卡/packet 节点或以候选成员名单代替实际完整提供集合来过滤。候选及其选项、全部原 Activity 字段（包括其 terms）、全部来源原文/SourceRef、完整文件目录及响应 allowlist 不变。全局选材仍收到全部原导航卡和字段；最终 DRAFT/REVIEW 仍含完整 statementDirectory，实际输入不受此投影影响。

这项省略未读导航 terms 是已明确批准的导航信息取舍，不宣称它与原导航逐字段等价；未读 Activity 仍能依名称、目的、对象和来源召回，完整 terms 随实际选中 Activity 一并读取。它不删任何已读业务正文，不新增 parser、语义规则、补读轮次或自动重试。CHECK Prompt 保持现有 v2，Schema、全局选材及各 producer/输出版本不变，仅 CHECK 的实际输入经已有 fingerprint 自然区分。实施/真实验证状态见交付记录。

全局 `PROCESS_MATERIAL_SELECTION` 的输入、Schema、Prompt、producer 及指纹计算保持不变；共享 Schema 的调用点不能意外改变全局选材。CHECK 与过程的实际输入/Schema 变化由既有内容指纹自然区分，不升级公共产物或已保存 decision/pair 的容器版本，不重写历史记录，不使成功的旧全局选择失效。

实施前后的离线比较必须检查实际序列化的输入、完整响应 Schema 和 Prompt/envelope；REVIEW 再计入实际完整草稿及实际配置的生成空间。编码 token 估计只用于本次容量观察，不声称等同 Provider 精确计账，不新增固定字符/token/费用门槛。首批材料容纳不保证补读后的包容纳；最终包仍按真实有效上下文预检，不能因此缩材料、追加阅读轮次或自动重试。

### 引用不再依赖旧成员所有权

statement 可来自包内 context Activity；source 可来自包内同源原文。模型只能引用已实际提供的 allowlist，不能凭目录上看见文件就引用未读正文。`activityUseIds` 只表示规则适用的业务用途，不控制证据归属。程序验证未知 ref，不验证中文蕴含。

具体检查是：过程输出的ActivityUse.activityId必须属于封包时的最终成员集合，不能把context静默升级为成员；stage/rule引用的activityUseIds必须是本过程真实用法。ActivityUse自身的sourceRefs与过程、stage、rule、knowledge引用均按同一实际阅读包allowlist检查，不再按旧M10的Activity owner过滤。statement handle仍保持其原Activity归属，引用不能改写其owner。覆盖依据最终成员和处置计算，不依据被引用或读过的Activity集合计算。

RED：DRAFT 前含实际选中 Java/XML/Vue；空补读仍有一次检查；补读后不循环；完整 Activity、公式、条件不丢；context 引用可用但不强迫加入阶段。GREEN 限于取材、容量、映射和封包。

## 4. CandidateProcessReconstructor：完整阅读包生成过程

当前已实现每候选PROCESS_DRAFT→PROCESS_REVIEW。目标为PROCESS_DRAFT（事实）→PROCESS_WRITE（正文）→PROCESS_RULE_REVIEW（核对实际正文并修正）。三次绑定同一服务/账户/模型/effort，仍是一个job；输出保留现有Process字段。详见[新设计§6](business-reasoning-and-writing.md#6-事实推理写作和最终核对)。DRAFT不选材。

复用 ActivityUse、stage.narrative、进入条件、动作、状态变化、拒绝、结果、转移、rule.activityUseIds、knowledgeItems 及三个 certainty。跨对象交接写入现有字段，不新增交接证明模型：

1. 前一步产生什么；后一步怎样找到/引用它；
2. 是否可不关联、可分批、有回退；
3. 后续怎样回写原对象数量、金额或状态；
4. 条件来自页面、后台校验还是查询口径；
5. 哪些联系仍缺材料。

最终RULE_REVIEW核对实际WRITE，返回完整修订过程和私有纠正说明；可依据原文收窄或判不足，不引入未读材料，之后不再润色或自动重试。原Activity与更完整原文不一致时，原记录不变，过程说明纠正和适用范围，不重跑Activity。

引用合法、字段齐全不等于语义正确。不能用技术模板、多阶段数量替代人工样例验收。

## 5. RepositoryProcessConsolidator：保留细节，不创造长链

沿用现有业务完整投影、一次 DRAFT/REVIEW 和 KEEP/MERGE_INTO/REJECT、PARENT_CHILD/RELATED/ALTERNATIVE。长链由完整阅读后的重建形成，不靠归并器拼两个 stage 数组。

复用**当前已实现**的不能无损合并时保留原过程及原因的行为；这不是 fatal。未知 ID、非法引用、损坏对象仍失败。遗漏处置沿用安全 KEEP。局部过程与跨对象过程可并存；不为减少数量删掉不同条件、用途或结果。

Activity/候选/已审过程仍是原来的三个分母。阅读 context 不引入第四个业务分母；私有读取记录只说明本次看过什么。

## 6. BusinessProcessPublisher：已有五文件原样接力

零模型，仅消费闭合 ProcessDiscoveryResult，发布：

1. `business-processes.md`
2. `repository-business-process-catalog.json`
3. `process-coverage.json`
4. `source-refs.jsonl`
5. `sources.md`

SourceReference五字段保持，完整性留在私有ReadingRecord。目标正文来自最终RULE_REVIEW，主要条件不只藏在结构明细；小样不输出HTML折叠标签。现有来源页/链接复用，缺链接可空。

补读来源先在各阅读包中分配局部编号。发布前按冻结文件、行范围、原文去重，并为新片段分配不与原有 S 编号冲突的最终编号；不同包的局部同名 ref 必须通过包身份映射，不能直接 join。原 M10 ref 可原样复用。同一来源出现在多个包时最终只保存一份；统一映射所有 stage/rule/knowledge/relationship 等来源字段，再形成闭合 result。Publisher 不再次读 corpus。

缺链接仍可留空，不补证据、不追加模型调用。已填的引用必须能指到实际保存文本。正文不嵌大段源码。

## 7. 运行接线、私有保存和版本

### 唯一运行入口

继续 `source-analysis execute-step --target repository-knowledge`，Activity 批次推导材料及来源 owner。当前材料state v3不直接含verified inventory ref；组合根从已核验sourceRun的Step01 publication重开完整引用并与M10 basis核对，不靠路径猜测，也不升级/覆盖旧state。内部 `ProcessDiscoveryRequest` 增加可选目录输入引用、冻结文本读取依据及可选关注问题；传验证后的引用/依赖，不传宿主 Path 给公共 Agent。

这里的`ProcessDiscoveryRequest`是Step07深模块的内部输入，Java声明为public不等于公共`RepositoryAnalysisAgent`请求协议。本次改它的字段，不新增公共Agent方法或另一套公开执行请求，也不为避开内部字段调整再创建一层通用上下文框架。

现行入口支持以下仅过程读取使用的参数：

```text
--catalog-from-model-batch <analysis-run-id>
--focus-question <text>                 # 可选；只是问题，不是事实
```

现有 `--activity-model-batch` 和 `--reuse-from-model-batch` 保留；后者只表示完整任务复用，不兼任旧目录输入选择。没有旧目录参数时可先生成首次目录，之后使用同一阅读路径。不得恢复 RepositoryRunMain。

CLI创建/选择QUEUED run后，在任何Provider初始化或模型请求前，从材料所绑定的业务流 publication 取回同源 verified inventory 的完整引用，打开冻结 reader，并将Activity、M10、inventory、目录输入和原样focusQuestion写入该run的私有 v3 执行配置。旧目录批次只须停止且其 v2 配置的材料/Activity 与完整 catalog pair 一致；之后失败不抹去已完成目录。已有记录必须完全匹配，改变选择须另起新run；仍沿用现有queued source/config校验，不只比较source request就放行。使用现有原子幂等保存，不新增公共Agent方法或通用身份框架。没有事先过程选择的通用start允许第一次execute绑定一次。

### 保存

复用现有 job pool、Provider 和 journal。全局选择和每候选检查是**单次决策任务**，私有 `decision-result.json` 保存完整请求/响应、身份、指纹、终态；不能伪装成 reviewed pair。不复用中途/损坏结果。保持简单直接写读，不新增通用恢复框架。

全局选材沿用repositorySummary路由；候选检查和后续job按稳定ordinal绑定processGroup，不能因完成顺序或名称换账户。目标三阶段共用binding和两层并发，无新池；现有pair继续用于历史读取和其它两轮任务。

保存目录输入、系统认识/问题、首批保留/补充请求、读取记录、完整包、事实DRAFT、WRITE、最终RULE_REVIEW、处置和复用来源。目标过程reviewed-result.json使用私有v3三阶段，当前v2 pair严格历史读取；精确版本见新设计§7。同源旧输入只读，新输出属新run。无自动重试或服务切换。

当前双轮实现是在候选jobs启动前稳定生成全仓source映射；每候选完整完成后独立归一化并保存packet、映射及pair。目标三阶段沿用同一来源归一化，保存完整三阶段记录。随后其他候选的 fatal 不能丢弃已完成的合法保存结果；只有最终归并/发布等待全量完成，不新增恢复框架。

### 已批准的一次性租户引用修正

用户只批准对已完成但解析失败的租户 REVIEW 内存副本做一个确定映射：`/processes/0/businessRules/5/activityUseLocalIds/1` 中的 `activity:f557933b36ff5eb47a60b6c300ceb86e99d271431bc499dfd636c879ab05207c/activitySteps/9` 改为本过程唯一已存在的 `useLocalId`：`activity:f557933b36ff5eb47a60b6c300ceb86e99d271431bc499dfd636c879ab05207c`。其余 REVIEW 值及原 DRAFT 不变；这不是再次模型审阅，也不产生新的业务内容。旧 FAILED run 与 Provider raw 文件字节保持原样，零新增模型调用。使用 ignored 验收工具在新私有 batch 中，复用现有 canonical codec、inputFingerprint、source normalization、parseCandidate 与 PrivateModelJobResultStore 完成验证和保存；遇到第二个错误即停止，不扩大修正。

新 pair 的 `review` 明确是这一用户授权派生副本；顶层仅增加可选 `reviewCorrection={manifestPath,manifestSha256}`。manifestPath 是同一 journal 内独立、不可覆盖的修正清单相对路径。清单记录授权范围、原 FAILED run、DRAFT/REVIEW 原始 artifact 路径与文件/解码响应 SHA-256、jobKey、精确 pointer/before/after 和派生 REVIEW canonical SHA-256。原模型 runtimeIdentity 表示原始生成来源，不把这次映射归给模型。清单先以 canonical、create-new 方式保存，完整 pair 含标记后只写一次；不先存未标记 pair 再覆写。首次导入的 `reusedFromModelBatchId` 为空，原失败来源由清单说明；以后正常显式复用仍按既有字段记录来源 batch。

后续显式复用只沿用现有不可变来源链：`saveProcessPair(reused=true)` 已写入实际 `reusedFromModelBatchId`，同一 jobKey 可逐批回到首次导入中带 reviewCorrection 的记录及清单。后续 canonical 记录不必重复该额外字段；必须保留链上的来源批次、该 job 记录和 manifest，不能把链中派生 REVIEW 宣称为未经修正的 Provider 原文。来源映射照常重新计算。不新增生产字段传递、测试协议或清单加载器，不改公共 Interface、parser、Provider、Prompt、fingerprint 或容器版本，不从错误引用自动生成标记或执行修复。

### 已实现v3阅读路径的复用与版本（历史读取依据）

- 旧目录作资料读取：不要求新提示词等于旧提示词。
- 单次决策结果复用：显式来源、完整成功记录及输入/Prompt/Schema/producer匹配；验证providerBindingKey、quotaScope和ModelRuntimeIdentity的provider/model/effort/sandbox。输入包含实际focusQuestion、首批阅读结果和对应ref映射，不保存密钥值；不是复用半轮pair。
- 过程 pair 复用：最终完整 packet、ref 映射、Prompt、响应结构、业务配置及服务/账户/模型均匹配。
- 这里的匹配映射是包内短 ref 对应的实际文件、范围和原文，不是发布时的全仓编号。pair 保留原始局部响应；新批次合并全部实际来源后，才复制并归一化响应、构造最终过程身份。这样后续加入其他候选不会使同一阅读包的已审样本误失效，受其影响的仓库归并则按新的最终输入重新匹配。
- 无关的并发、日志目录、新 batch ID 不使内容失效；新关注问题、实际补读文本或新 Prompt 进入指纹。
- 旧过程没看过新包，不能冒充新版过程。不得因此重跑任何 Activity。

新增私有 `process-reading-decision-v1`（phase 区分全局/检查）和 `process-reading-packet-v1`；过程 DRAFT/REVIEW Prompt 与响应合同升 v3，移除旧请求源码语义。发现/发布 producer 升 v3 以区分新路径；公共 catalog/coverage/业务 Markdown 仍 v2、来源 JSONL/Markdown 仍 v1，字段未变不做全工程 wire reset。原目录、归并 Prompt 不因本次取材而强制改正文；其实际输入变化自然改变 fingerprint。

私有模型执行记录升 `model-job-execution-config-v3`，保存可空旧目录输入、完整verified inventory引用及可空focusQuestion。保留v2严格只读以重开本次旧目录和旧任务；不能把v2缺少新字段当成已经执行新阅读路径，也不覆盖旧记录。YAML repository-run-config-v2、run-output-v4、M10、Activity、JDT和材料state-v3均不因此升版，公共Agent方法不变。

## 8. 本轮实现差距

冻结读取、旧目录输入、全局选材、一次检查、DRAFT前完整包、引用范围、CLI和五文件均已实现。待改是系统认识/问题保存、CHECK最终保留集、三阶段成稿及完整保存复用、最终正文核对。具体类/函数见[实施状态](implementation-status.md)。上方原版本及一次性修正属于已实现/历史合同；新任务采用[目标版本表](business-reasoning-and-writing.md#7-保存并行与版本影响限制在过程任务)，旧pair不得冒充新三阶段。
