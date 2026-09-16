# 模块详细设计：复用已有材料，先阅读再重建

状态：目标合同，未实施。总体与固定输入见[入口](README.md)，Prompt 见[中文指令](prompts.zh-CN.md)。模块数不扩成新的公共流水线：两项深 Interface 保持不变，选材和阅读检查放在 Discovery 内。

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

## 2. RepositoryBusinessCataloger：重开目录与全局选材

### 两条入口

有 `catalogFromModelBatch`：重开已完成目录任务，保留原候选、成员用法和规范化 Activity 处置；**不重跑原 CATALOG/SHARD/MERGE**。没有该参数的新仓库仍按现有首次目录发现；不能删除这条能力。

来源批次必须已停止，目录任务本身须有完整、有效的DRAFT/REVIEW保存记录，并与本次Activity检查点、Activity集合及冻结basis一致。旧批次在目录之后失败不使已完成目录失效。目录缺失、声称完成但损坏或来源不一致明确报错；不能隐式重新调用目录模型补齐。

旧目录是新任务的输入资料，不是新版已审过程。旧提示词不同不妨碍阅读旧目录；但直接复用完整过程结果仍必须满足现有 fingerprint。

### 全局选择输入

- 旧目录候选及成员用法、目的；
- 全部 Activity 的轻量导航卡：ID、name、businessPurpose、businessObjects、terms、已有入口/来源导航；
- 同源冻结文件目录；
- 可选关注问题。

导航卡重新投影仅是 Java 读已有字段，零模型，不重建 Activity 或原目录卡结果。完整条件、规则、公式等不在此丢弃，候选阅读时全部取回。文件目录可展示仓库相对路径和 fileKey；绝不展示宿主绝对路径、run/hash 或凭据。

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

检查模型收到：候选、全仓轻量导航/文件目录、第一批实际完整 Activity 和原文、未命中/省略说明。允许提出候选范围和成员的修正，以及 `supplementaryRequests`。

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

包必须自包含，DRAFT 和 REVIEW 都收到同一个包；REVIEW 另加完整实际 DRAFT。首次材料充足也仍走一次检查，但不强制实际读更多源文件。完整包与 REVIEW 预检实际容量，不按预算偷偷删条件或数组尾部；确实无法容纳按现有 NOT_PROCESSED_CAPACITY 显示。

### 引用不再依赖旧成员所有权

statement 可来自包内 context Activity；source 可来自包内同源原文。模型只能引用已实际提供的 allowlist，不能凭目录上看见文件就引用未读正文。`activityUseIds` 只表示规则适用的业务用途，不控制证据归属。程序验证未知 ref，不验证中文蕴含。

具体检查是：过程输出的ActivityUse.activityId必须属于封包时的最终成员集合，不能把context静默升级为成员；stage/rule引用的activityUseIds必须是本过程真实用法。ActivityUse自身的sourceRefs与过程、stage、rule、knowledge引用均按同一实际阅读包allowlist检查，不再按旧M10的Activity owner过滤。statement handle仍保持其原Activity归属，引用不能改写其owner。覆盖依据最终成员和处置计算，不依据被引用或读过的Activity集合计算。

RED：DRAFT 前含实际选中 Java/XML/Vue；空补读仍有一次检查；补读后不循环；完整 Activity、公式、条件不丢；context 引用可用但不强迫加入阶段。GREEN 限于取材、容量、映射和封包。

## 4. CandidateProcessReconstructor：完整阅读包生成过程

每候选一次 PROCESS_DRAFT 和一次完整 PROCESS_REVIEW，绑定同一服务/账户/模型/effort。DRAFT 不再承担选源码的职责；新响应合同不再使用 `requestedSourceRefs` 驱动补读。

复用 ActivityUse、stage.narrative、进入条件、动作、状态变化、拒绝、结果、转移、rule.activityUseIds、knowledgeItems 及三个 certainty。跨对象交接写入现有字段，不新增交接证明模型：

1. 前一步产生什么；后一步怎样找到/引用它；
2. 是否可不关联、可分批、有回退；
3. 后续怎样回写原对象数量、金额或状态；
4. 条件来自页面、后台校验还是查询口径；
5. 哪些联系仍缺材料。

REVIEW 返回完整修订过程，可保留、收窄、拆分、删除或判材料不足；不能引入未读 Activity/原文，也不能追加第三轮。已审 Activity 与更完整源码不一致时，原 Activity 不变，过程依据原文纠正并在私有阅读记录说明差异；不因此重跑 Activity。

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

业务正文和来源链接样式不重写。SourceReference 保持 ref/file/startLine/endLine/snippet 五字段；读取是否完整等只放私有 ReadingRecord，不为此升级公共来源 Schema。

补读来源先在各阅读包中分配局部编号。发布前按冻结文件、行范围、原文去重，并为新片段分配不与原有 S 编号冲突的最终编号；不同包的局部同名 ref 必须通过包身份映射，不能直接 join。原 M10 ref 可原样复用。同一来源出现在多个包时最终只保存一份；统一映射所有 stage/rule/knowledge/relationship 等来源字段，再形成闭合 result。Publisher 不再次读 corpus。

缺链接仍可留空，不补证据、不追加模型调用。已填的引用必须能指到实际保存文本。正文不嵌大段源码。

## 7. 运行接线、私有保存和版本

### 唯一运行入口

继续 `source-analysis execute-step --target repository-knowledge`，Activity 批次推导材料及来源 owner。当前材料state v3不直接含verified inventory ref；组合根从已核验sourceRun的Step01 publication重开完整引用并与M10 basis核对，不靠路径猜测，也不升级/覆盖旧state。内部 `ProcessDiscoveryRequest` 增加可选目录输入引用、冻结文本读取依据及可选关注问题；传验证后的引用/依赖，不传宿主 Path 给公共 Agent。

这里的`ProcessDiscoveryRequest`是Step07深模块的内部输入，Java声明为public不等于公共`RepositoryAnalysisAgent`请求协议。本次改它的字段，不新增公共Agent方法或另一套公开执行请求，也不为避开内部字段调整再创建一层通用上下文框架。

目标新增参数（**当前代码尚不支持**）：

```text
--catalog-from-model-batch <analysis-run-id>
--focus-question <text>                 # 可选；只是问题，不是事实
```

现有 `--activity-model-batch` 和 `--reuse-from-model-batch` 保留；后者只表示完整任务复用，不兼任旧目录输入选择。没有旧目录参数时可先生成首次目录，之后使用同一阅读路径。不得恢复 RepositoryRunMain。

CLI创建/选择QUEUED run后，在任何Provider初始化或模型请求前，将Activity、M10、verified inventory、目录输入和原样focusQuestion写入该run的私有执行配置。已有记录必须完全匹配，改变选择须另起新run；仍沿用现有queued source/config校验，不只比较source request就放行。使用现有原子幂等保存，不新增公共Agent方法或通用身份框架。没有事先过程选择的通用start允许第一次execute绑定一次。

### 保存

复用现有 job pool、Provider 和 journal。全局选择和每候选检查是**单次决策任务**，私有 `decision-result.json` 保存完整请求/响应、身份、指纹、终态；不能伪装成 reviewed pair。不复用中途/损坏结果。保持简单直接写读，不新增通用恢复框架。

全局选材沿用repositorySummary路由；候选检查和其后过程pair在全局候选稳定顺序上捕获同一processGroup服务绑定，不能因完成顺序或细化名称换账户。两类任务分别计数/保存，同一候选DRAFT与REVIEW绑定不变；复用现有两层并发，无新池。

保存目录输入、首批/补充请求、实际读取记录、最终阅读包、DRAFT/完整 REVIEW、增量处置和复用来源。过程 pair 继续现有 `reviewed-result.json`。同源旧输入只读，新输出属新 run。新执行只在明确授权后启动；无自动重试、换模型或 API 回退。

### 精确复用与版本

- 旧目录作资料读取：不要求新提示词等于旧提示词。
- 单次决策结果复用：显式来源、完整成功记录及输入/Prompt/Schema/producer匹配；验证providerBindingKey、quotaScope和ModelRuntimeIdentity的provider/model/effort/sandbox。输入包含实际focusQuestion、首批阅读结果和对应ref映射，不保存密钥值；不是复用半轮pair。
- 过程 pair 复用：最终完整 packet、ref 映射、Prompt、响应结构、业务配置及服务/账户/模型均匹配。
- 无关的并发、日志目录、新 batch ID 不使内容失效；新关注问题、实际补读文本或新 Prompt 进入指纹。
- 旧过程没看过新包，不能冒充新版过程。不得因此重跑任何 Activity。

新增私有 `process-reading-decision-v1`（phase 区分全局/检查）和 `process-reading-packet-v1`；过程 DRAFT/REVIEW Prompt 与响应合同升 v3，移除旧请求源码语义。发现/发布 producer 升 v3 以区分新路径；公共 catalog/coverage/业务 Markdown 仍 v2、来源 JSONL/Markdown 仍 v1，字段未变不做全工程 wire reset。原目录、归并 Prompt 不因本次取材而强制改正文；其实际输入变化自然改变 fingerprint。

私有模型执行记录升 `model-job-execution-config-v3`，保存可空旧目录输入、完整verified inventory引用及可空focusQuestion。保留v2严格只读以重开本次旧目录和旧任务；不能把v2缺少新字段当成已经执行新阅读路径，也不覆盖旧记录。YAML repository-run-config-v2、run-output-v4、M10、Activity、JDT和材料state-v3均不因此升版，公共Agent方法不变。

## 8. 本轮实现差距

现有代码已能做完整 Activity 传递、两轮过程、归并、五文件发布；缺的是冻结文件查询接线、旧目录显式输入、两类单次阅读决策、DRAFT 前封包、扩展到实际阅读包的 ref allowlist 及新来源重编号。详细修改面和验收见 [acceptance](acceptance.md)，不把已完成能力再算成待开发。
