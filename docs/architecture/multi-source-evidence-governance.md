# 多源证据治理参考架构

## 目的与边界

本架构把“多个来源说了什么”变成可复核、可比较、可审阅的候选治理结果。它的目标不是让模型从不完整材料中补全一个看似完整的答案，而是让每一个结论都能回答四个问题：依据在哪里、适用什么对象与范围、与别的来源是互证还是矛盾、最终由谁以什么理由决定。

真实性不等于覆盖率。一个覆盖面较小、但能定位回冻结原始字节的结论，比一份内容丰富却不能复核的总结更可信。没有足够依据时，系统必须保留 `GAP`，而不是用推测填补。

这份文档描述未来 2–4 天“完整证据工厂”的参考实现，同时记录当前管伊佳 Demo 的较小实现。正式管伊佳 V1、零售 V1/V2 不由本流程自动修改；任何差异都先成为可审阅候选。

### 非目标

- 不在浏览器、日常测试、构建、热更新或“恢复演示”中重新扫描 MySQL、GitHub、官网或调用 LLM。
- 不把原始业务行、绑定参数、样本值或私有仓库内容部署到浏览器或 Demo 服务器。
- 不让 LLM 直接写入正式模型、解决冲突或发布 V1。
- 不承诺多浏览器标签同时写同一运行的胜者恢复或实时协作；现有 revision/CAS 仅拒绝陈旧写入。

## 信任分级

```ts
type EvidenceClass =
  | 'OBSERVED'
  | 'FROZEN_RECORD'
  | 'GENERATED_TARGET'
  | 'DERIVED'
  | 'GAP';
```

| 等级 | 含义 | 可作为什么依据 | 不能做什么 |
| --- | --- | --- | --- |
| `OBSERVED` | 保存了真实原始字节；可重算摘要并按行、页或 SQL 对象定位。 | 当前事实的直接依据。 | 不能越过其范围或时间做全局结论。 |
| `FROZEN_RECORD` | 有冻结的结构化采集记录与摘要，但未保存完整原文。 | 已知记录的有限依据。 | 不能显示为源码或官方原文。 |
| `GENERATED_TARGET` | 由模型或人工起草、待确认的目标制度/设计。 | 候选治理规则。 | 不能冒充现状事实或独立佐证。 |
| `DERIVED` | 从已确认材料确定性派生，例如知识图谱三元组。 | 导航、解释与聚合。 | 不增加根证据数量。 |
| `GAP` | 内容缺失、不能定位、或证据不足。 | 明确待补事项。 | 不能被模型自动补为事实。 |

证据等级跟随 fragment，而不是跟随来源名称。例如 MySQL DDL 可为 `OBSERVED`，同一来源的没有保存正文的历史记录仍可能是 `FROZEN_RECORD`。展示层必须使用中文可读标签；SHA、内部 ref 和枚举仅保留在内部审计记录，不能成为业务页面条目。

## 完整处理流水线

```text
SourceReader.capture
→ RawSnapshotStore.freeze
→ EvidenceGateway.admit
→ FactNormalizer.normalize
→ GovernanceAgent.propose
→ CitationValidator.validate
→ GovernanceProjector.compare
→ ReviewCommand.recordDecision
→ AuditProjector.replay
→ BundleFreezer.freeze
→ FrontendProjection.read
```

前端只读取最后的只读投影，绝不在 UI 中重新判断证据。下表是所有操作都必须满足的六段契约；后续小节再展开领域规则。

| 操作 | 输入 | 处理与输出 | 校验 | 失败 | 恢复 |
| --- | --- | --- | --- | --- | --- |
| `SourceReader.capture` | 已批准的来源、范围、版本 | 受限读取并产生 raw Artifact/manifest | 原始字节摘要、读点/commit/页面版本、范围与排除项 | 不写不完整 snapshot | 修正授权/范围后使用新 snapshotId 重采集 |
| `RawSnapshotStore.freeze` | 已校验 Artifact/manifest | 内容寻址私有存放，输出只读 raw snapshot | manifest membership、LFS/私有策略、redaction policy | 拒绝临时或半冻结资料 | 保留旧版本，修复存储后新版本冻结 |
| `EvidenceGateway.admit` | raw snapshot Artifact | 切出最小 `EvidenceFragment` 或 `GAP` | 摘要、locator、编码、公开脱敏、证据等级 | 拒绝片段而非猜测替代 | 纠正 locator/redaction 或显式保留 GAP |
| `FactNormalizer.normalize` | 已准入 fragment、词表版本 | 输出可比较 `NormalizedClaim` | fragment/claim/anchor 三向闭合、范围/时间必填规则 | 不产出无依据事实 | 补词表/映射或保留 GAP，生成新 claim revision |
| `GovernanceAgent.propose` | fragments、claims、固定 prompt/schema | 输出有引用的 Proposal/Generation manifest | ChatGPT session、模型/prompt/schema/input/output 摘要 | 不写运行、不把模型文本当事实 | 修正输入/模板后新 generation run |
| `CitationValidator.validate` | proposal、fragment、manifest | 输出已验证 proposal 或拒绝结果 | 引用唯一、artifact/locator/excerpt 精确匹配、目标待确认标记 | 拒绝整个不闭合 proposal | 修正提案或补资料后重新验证 |
| `GovernanceProjector.compare` | 已验证 claims | 输出 relation、conflict candidate 或 GAP | subject/predicate/scope/time/value/证据等级规则可重算 | 输出 `UNSUPPORTED`，不强行判定冲突 | 补证据/范围/时间后以新投影重算 |
| `ReviewCommand.recordDecision` | conflict、用户选择、角色、父 revision | 追加决定事件与新的文档 revision | 权限、CAS、允许策略、双方引用与系统生成审计摘要 | 陈旧/无权/不完整决定不提交 | 刷新后重新选择；`DEFER_AS_GAP` 保留不确定性 |
| `AuditProjector.replay` | raw manifest、事件序列、版本化投影器 | 重建 fragments/claims/relations/decisions/Markdown | 所有输入/输出摘要、事件顺序、parent revision | 标记审计失败，拒绝冻结 | 修复损坏数据，不覆盖历史，重放新候选 |
| `BundleFreezer.freeze` | 已验证且已决定的 revision | 原子候选 Bundle、receipt、redaction approval、**候选** registry-pin proposal | trace/fragment/claim/decision 完整性、未决 GAP/冲突、候选 receipt | 不产生正式 Bundle/pin | 修正审阅状态后生成新候选版本；正式 pin 只能由审批发布写入 |
| `FrontendProjection.read` | 已验证的 Bundle、当前只读审阅投影 | 展示 Markdown、证据、关系和命令入口 | public 字段白名单、内容摘要、技术详情边界 | 在受影响内容旁 fail closed，不回源 | 重新打开并校验本地内容；恢复演示仅重置工作态 |

### 1. 原始来源采集：`SourceReader.capture`

**输入**：固定 MySQL 事务范围内的 DDL、存储过程、函数、触发器、事件、静态 DML 和经授权的脱敏样本；固定 Git commit 的源码、Mapper XML、迁移 SQL 与文档；官方 HTML/PDF/附件；制度与知识库 Markdown；已确认知识库派生的三元组。

**输出**：内容寻址 Artifact 和不可变清单：

```ts
type RawSourceSnapshotManifest = {
  snapshotId: string;
  sourceId: string;
  sourceVersion: string;
  capturedAt: string;
  captureScope: string;
  exclusions: string[];
  artifacts: RawArtifactRef[];
  contentDigest: string;
};
```

**校验**：逐 Artifact 原始字节摘要、固定 Git commit、数据库事务/读点、页面版本、清单基数、私有/public 脱敏边界。业务行只在授权的私有证据仓保存；公开层只取经过审批的摘录。

**失败与恢复**：连接、范围、摘要或脱敏审批任一失败即不产生 snapshot。修复后以新 snapshotId 再采集，绝不覆盖旧版本。正常产品路径不能调用本阶段。

### 2. 冻结原始输入：`RawSnapshotStore.freeze`

**输入**：通过采集校验的 Artifact 与清单。

**处理**：在私有证据仓中以内容寻址保存原始材料，记录清单摘要、读取范围、排除项和 redaction policy。PDF 无文本层时，维护任务可用受控 OCR 生成页码映射；OCR 输出同样是新 Artifact，不替换 PDF 原件。

**输出**：可回滚的 raw snapshot，以及面向准入阶段的只读 reader。

**失败与恢复**：存储、LFS、receipt 或清单不一致时 fail closed；不允许降级为临时文件或浏览器缓存。重新发布时保留旧 snapshot 与其 receipt。

### 3. 证据准入：`EvidenceGateway.admit`

**输入**：已冻结 raw snapshot 中的特定 Artifact。

**处理**：按文件行、PDF 页、SQL 对象、源码 symbol 或文档章节切出最小必要摘录，重新验证原始摘要和定位。没有正文的结构化归档只能产生 `FROZEN_RECORD`；找不到内容只能产生 `GAP`。

**输出**：

```ts
type EvidenceFragment = {
  evidenceRef: string;
  artifactRef: string;
  sourceId: string;
  evidenceClass: EvidenceClass;
  locator: FileLines | PdfPages | SqlObject | DocumentSection;
  excerpt: string;
  excerptSha256: string;
};
```

**校验**：fragment 必须一对一回到 raw Artifact、摘要和 locator；摘录经过公开层脱敏；`OBSERVED` 不能引用缺失正文；禁止将模板文本作为 DDL、SQL、代码或官方引文。每个引用在 public Bundle 都有可读位置，但私有路径与业务行不会泄露。

**失败与恢复**：定位、编码、摘要、redaction 或清单不通过即拒绝该 fragment，并投影为 `GAP`；不能按相近对象猜测替代摘录。

### 4. 事实标准化：`FactNormalizer.normalize`

**输入**：准入 fragment 与固定规范化词表。

**输出**：

```ts
type NormalizedClaim = {
  claimId: string;
  subjectRef: string;
  predicate: string;
  normalizedValue: unknown;
  scope: string;
  effectiveTime?: string;
  evidenceClass: EvidenceClass;
  evidenceRefs: string[];
};
```

**处理规则**：

1. 用稳定对象映射把表/字段、代码符号、制度术语和模型对象对应到同一 `subjectRef`。
2. 规范化状态枚举、单位、布尔开关、金额/时间口径和关系方向；保留原始展示文本供审阅。
3. 单独记录租户、模块、流程阶段、发布版本、有效期和读取时间，形成 `scope` 与 `effectiveTime`。
4. `OBSERVED`/`FROZEN_RECORD` 的原始描述和 `GENERATED_TARGET` 的目标规则保持不同类别；无充分引用的内容成为 `GAP`，不是普通 claim。

**校验**：每个 claim 至少有一个有效 fragment；每个识别项必须能回到 assertion、Markdown anchor 和 fragment。规范化函数是确定性的、有版本号的纯函数。

### 5. LLM 提案：`GovernanceAgent.propose`

**输入**：已准入 fragment、标准化 claim、固定 prompt 模板/Schema、模型版本及输入摘要。

**输出**：设计 Markdown、企业知识库、候选关系/解释和 `GenerationRunManifest`。提案不能直接改变当前事实：

```ts
type GenerationRunManifest = {
  sessionId: string;
  model: 'gpt-5.6-luna';
  reasoningEffort: 'xhigh';
  promptVersion: string;
  inputDigest: string;
  outputDigest: string;
  citationRefs: string[];
};
```

维护生成只允许已登录的 ChatGPT/Codex session、Luna xhigh、read-only 和 never approval；拒绝 `OPENAI_API_KEY` 及任何按量 API 回退。普通 Demo、测试、构建绝不运行该步骤。

**校验**：模型输出的每项 claim/段落必须给出已准入引用；CitationValidator 逐字验证 locator 与摘要。不带有效引用的提案只能标为 `GENERATED_TARGET` 或 `GAP`。模型不是事实来源，也不能解除冲突或修改正式 V1。

**失败与恢复**：登录、模型、prompt/schema 摘要、引用或输出格式失败时不产生 proposal；修复后创建新的 generation run，不覆盖旧输出。

### 6. 引用验证：`CitationValidator.validate`

**输入**：proposal、fragment、raw manifest 和 generation manifest。

**输出**：带验证状态的 proposal 或可读拒绝原因。

**校验**：引用 ID 唯一、fragment 存在、artifact/locator/excerpt 摘要精确匹配；目标制度使用 `PENDING_HUMAN_CONFIRMATION`；派生材料必须回到已经确认的上游文档。验证器不读取网络、不会容错到“相近行”。

### 7. 一致与矛盾投影：`GovernanceProjector.compare`

比较是确定性规则，不依赖一次模型回答。对于可比较的一对 claim，顺序为：

1. `subjectRef` 是否相同；
2. `predicate` 或关系是否相同；
3. `scope`（租户、模块、阶段）是否相同、相交或不同；
4. `effectiveTime` 是否相同、可比较或不同；
5. `normalizedValue` 是否相同、可同时成立或互斥；
6. 证据等级是否足以支持该关系。

```ts
type EvidenceRelation =
  | 'CORROBORATES'
  | 'COMPLEMENTS'
  | 'CONFLICTS'
  | 'SCOPE_DIFFERENCE'
  | 'TEMPORAL_DRIFT'
  | 'UNSUPPORTED';
```

| 条件 | 投影 |
| --- | --- |
| 同对象、属性、范围、时间与值 | `CORROBORATES` |
| 信息不重叠但可同时成立 | `COMPLEMENTS` |
| 范围/时间可比但值互斥 | `CONFLICTS` |
| 租户、模块、阶段或适用范围不同 | `SCOPE_DIFFERENCE` |
| 版本、发布日期或有效时间不同 | `TEMPORAL_DRIFT` |
| 引用不足、只有缺口，或不可验证 | `UNSUPPORTED` / `GAP` |

`DERIVED` 不能作为第二份独立佐证。`GENERATED_TARGET` 与现状不同只生成治理候选，绝不覆盖 `OBSERVED` 现状。没有全局“来源优先级”；冲突由人工在上下文中决定。

### 8. 人工审阅与决定：`ReviewCommand.recordDecision`

现有命令语义可表达四种处理：`KEEP_CURRENT`、`ACCEPT_INCOMING`、`MERGE`、`DEFER_AS_GAP`。每个决定记录冲突双方完整引用、比较规则、规范化值、决定人/角色/时间、系统生成的审计摘要、输入 revision、输出 revision，以及对对象/规则/指标/Markdown 的影响。

命令以父 revision/CAS 提交。陈旧写入会被拒绝并提示刷新后重试；本架构不将多标签竞争胜者恢复作为当前能力。`GENERATED_TARGET` 必须经过有权限的人类确认，才可能进入后续正式审批。

### 9. 审计、重放与冻结

完整事件链：

```text
SOURCE_CAPTURED
→ EVIDENCE_ADMITTED
→ CLAIM_NORMALIZED
→ PROPOSAL_GENERATED
→ CITATION_VALIDATED
→ RELATION_PROJECTED
→ CONFLICT_FOUND
→ DECISION_RECORDED
→ DOCUMENT_REVISED
→ REVIEW_CONFIRMED
→ BUNDLE_FROZEN
```

`AuditProjector.replay` 从固定 raw manifest 和事件顺序重放 fragment、claim、relation、conflict、decision、Markdown 与最终候选模型；每个事件绑定前一 revision 与输入/输出摘要。重放差异是审计失败，不以最新内容覆盖历史。

`BundleFreezer.freeze` 原子生成五份来源 Markdown、blocks/assertions/trace links、fragments/relations、冲突与解决 artifacts、generation manifests、publication receipt、redaction approval、**候选 registry-pin proposal**，以及与正式 V1 的差异候选。它不能改写静态 `TrustedPublicationRegistry`；只有现有正式审核/发布流程在审批候选后才能把新的受信 pin 随应用发布为 V2。

**失败与恢复**：缺 trace、未声明 GAP、伪摘录、未确认的目标制度、未解决冲突或 receipt/pin 不匹配均拒绝冻结。创建新版本、保留旧 Bundle，并以 receipt 支持比较与回滚。

### 10. 前端投影：`FrontendProjection.read`

前端消费冻结 Bundle 与当前审阅 revision。来源文档固定为“审阅事项／审阅结论／标准化文档”三页签，精确设计见[管伊佳数据标准化审阅体验设计](../design/data-standardization-review-experience.md)：审阅事项以 Claim 和运行级已准入比较为主数据，固定展示待确认事项、逐项待补充资料和已处理事项；审阅结论只展示核心结论与默认展开的完整对象目录；标准化文档保持同一 revision 的 V6 九章阅读版和只读 Markdown 源文。每个结论内可展开
全部来源材料、再跳转到唯一的 Markdown 段落；底层三向链路保持可验证，但不额外暴露一个难以理解的页面。点击结论、
Markdown 段落或依据入口后，三向联动：

```text
可定位的 SQL／DDL／代码／文档摘录
↔ 通俗对象说明与支持结论
↔ Markdown 章节与识别项
```

冲突卡并排显示双方可读内容、信任等级、范围/时间与可用决定。用户只选择“保留当前结论／采用新来源结论／登记为缺口”；系统记录兼容策略与审计摘要。作者定版前，已保存决定可以从业务时间线重新打开并形成新的不可变决定版本；旧版本仍可回看，最新版本才会进入下一份交付物。若当前交付物已经生成，系统将其标为历史结果、要求重新生成而非在原结果上覆盖决定集合。交付结果页只显示来源审阅、治理决定和正式模型检查的中文摘要，不把合并文档、决定清单、治理附录、zero-delta 原文或其 SHA/内部引用作为业务页面内容。技术 ID、SHA、content ref、内部枚举和 actor ID 仅保留在内部审计。所有按钮必须执行命令、导航或展开；只有 Toast 或改变内部选中状态的伪操作不允许出现。

**失败与恢复**：正文、候选 Bundle 或校验失败时，只在该文档/操作旁显示可读错误并禁用写操作；恢复动作重新打开并校验本地内容。前端不会把缓存故障说成扫描失败，也不会联网补证据。

### 10.1 固定资料运行中的准入与可见性

固定 Bundle 已存在只代表 `AVAILABLE`，不代表它已加入本次运行的 `ADMITTED` 集合。来源文档必须保持单源纯度：数据库文档只能显示数据库自身的 `OBSERVED` 摘录；它不得把尚未读入的 GitHub、官方资料、目标制度或派生图预先写进 Markdown。

| 运行状态 | 可见内容 | 不可见内容 |
| --- | --- | --- |
| `PENDING` | 来源名称、顺序和“待读取” | 摘录、claim、关系、冲突和目标制度 |
| `READING` | “正在校验并载入固定快照” | 正文、关系和冲突 |
| `DOCUMENT_READY` | 当前来源正文、所有参与来源均已准入的比较，以及本来源建议完成后首项可处理差异 | 尚未读入来源的任何内容；未具备前提或不在首位的决定按钮 |
| `REVIEWED` / `ALIGNED` | 已审阅正文、已发现关系和决定摘要；当前文档仍可读 | 尚未准入来源的任何关系或正文 |
| `CONFLICT_BLOCKED` | 当前可读来源与按来源顺序选择的第一项未决差异 | 尚未准入来源的任何关系或正文 |
| `READY` | 五源已读后的最高顺序可读文档，以及第一项未决差异 | 未准入来源的任何关系或正文 |

关系的每个参与者必须同时满足精确的 `sourceId + snapshotId` 和已准入状态；身份不匹配只阻断该关系，不阻断本源文档阅读。`GENERATED_TARGET` 只在制度来源读入后显示，官方 `GAP` 只在官方资料读入后显示，Semantica 只在自身读入后显示派生佐证且不增加独立证据数。`snapshot.current` 只选择活动来源或顺序最高的已读文档；第一项未决差异单独按来源与引入顺序选择。只要存在 `PENDING` 来源，`CONFLICT_BLOCKED` 或 `READY` 都继续投影 `READ_NEXT_SOURCE`；审阅者完成一个来源后显式点击“审阅下一个来源”才载入下一份。当前 Demo 的顺序是：MySQL 本源文档 → GitHub 的三项比较发现 → 官方资料的状态 9 GAP → 演示制度的目标制度 → Semantica 的派生佐证 → 欠款字段、负库存、状态 9 三项决定。五源全部读取、仍有未决项时才转为 `RESOLVE_CONFLICT`；最后一项决定保存前不能生成结果。

每个来源使用同一信息架构：`审阅事项`、`审阅结论`、`标准化文档`。事项回答“还需要判断或回看的事项”，结论回答“当前业务含义与对象目录”，标准化文档回答“如何写成完整九章文档”。审阅事项过滤 Markdown 标题、内部对象名、只有位置的记录和重复元数据，固定保留待确认事项（本来源建议、跨来源的一致／互补／冲突／不同版本记录不一致）、逐项待补充资料和已处理事项；审阅结论只保留核心结论和默认展开对象明细。每条结论原位承担单源链路“来源材料 → 审阅结论 → Markdown 段落”；所有跨来源关系都由整次运行的已准入来源投影到审阅事项，而不是按当前文档分流到审阅结论。

冻结来源永远不可编辑；当前 Demo 仅允许两条有精确 `sourceId + snapshotId + Claim + Block + Evidence` 映射的剧本化文字修订。用户只能选择保留当前结论，或采用推荐修改后微调该剧本显式允许的业务名称／业务说明；编辑器只呈现这些可改字段和确认后会变化的 Markdown 段落。首版可以使用更短的业务展示名，但用户确认或微调后的名称必须在事项、结论、标准化文档和结论内依据中逐字保留，不能被展示层再次裁剪。SQL、DDL和来源位置保持冻结，完整性约束仅留在内部审计。保留决定写入审计但不创建 revision，采用推荐修改先产生纯预览，确认后通过既有 `SourceDocumentRuntime.revise → REVISE_SOURCE_DOCUMENT → CAS` 链生成 revision。未核对的剧本项阻止可见的“完成本来源审阅”动作。`projectSourceReviewWorkspace` 将已验证 revision 的受限文字同步投影到审阅事项、审阅结论、标准化文档和结论内依据，但不改写冻结原文、trace 或跨源结论；没有剧本映射的条目不显示虚假的编辑入口。数据库来源先将真实 DDL 解析为字段结构表，再提供完整结构和带行号的原始 DDL 复核。所有业务操作都是显式 `COMMAND`、`NAVIGATE` 或 `EXPAND`，每个页面或移动层最多一个主按钮：有待核对剧本时为“核对 N 项建议”，可完成时为“完成{来源}审阅”，已审阅且仍有待阅来源时为“审阅下一个来源”。完成审阅后当前文档继续可读；从时间线打开差异层时，它临时拥有唯一“保存当前决定”主操作，关闭后恢复来源主线。工作台的“运行来源”只展示已锁定的资料实例，不能扫描、编辑、冻结或导入来源。

## 管伊佳三议题工作示例

### 负库存：互补资料与目标制度差异

1. MySQL `jsh_system_config.minus_stock_flag` 的冻结 DDL 摘录是 `OBSERVED`；它能证明当前部署存在该配置字段。
2. GitHub 的 V6 冻结源码摘录是 `SOURCE_NATIVE`，可按 `jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:L511-L520`、完整文件摘要和行段回到保存源码；它只覆盖负库存读取链的节选，不能替代完整租户范围、完整实现或负库存政策。
3. 因此当前 Demo 将两者并列为同一治理议题的互补资料，而不是把 GitHub 节选误写成对数据库租户配置的第二份独立佐证。只有完成正式 Evidence 准入和范围校验后，才可重新判断是否 `CORROBORATES`。
4. Luna 生成的“统一禁止负库存”制度是 `GENERATED_TARGET`，带 `PENDING_HUMAN_CONFIRMATION`。它与当前可配置现状不自动形成事实替换，而是呈现给人类保留、合并或延期。

### 欠款字段：结构冲突

1. 当前 MySQL `jsh_depot_head` 部署结构没有 `debt/last_debt`，为 `OBSERVED`。
2. GitHub 固定提交行段保存 debt/last_debt 的迁移语句，为 `SOURCE_NATIVE`；它可呈现为该精确行段，但不能代表整个历史、当前部署或对现状的单方面裁决。
3. 对象与属性相同，范围和时间被标为当前部署与历史代码记录；当前候选以 `CONFLICTS` 呈现，不假装它们是同一份原文或直接判断谁正确。
4. 审阅者可选择以部署结构为准、采用新来源结论或登记为缺口；决定和系统生成的审计摘要进入 revision 审计。

### 单据状态：时间漂移与 GAP

1. MySQL DDL 列出 `0/1/2/3/9`，为 `OBSERVED`。
2. GitHub 固定提交中的显示分支把状态值 9 呈现为“审核中”，为 `SOURCE_NATIVE`；它没有证明该状态在所有单据、版本和流程中的完整业务含义。
3. 当前部署字段与该历史词汇不能单独证明同一时间点的完整枚举；Demo 将其作为时间漂移风险和资料缺口，而不是错误的“直接冲突”。
4. 状态 `9` 的业务含义没有可信文本，明确为 `GAP`。目标制度若提出解释，仍是待确认提案。

## 实施等级与单调升级

| 维度 | 完整证据工厂：2–4 天 | 第一轮裁剪：10–16 小时 | 当前真实 Demo：本次交付 |
| --- | --- | --- | --- |
| 数据库 | DDL/SP/Function/Trigger/Event/静态 DML、授权样本 | 全部现有零行业务逻辑 Artifact | 三议题相关的 DDL 与现有记录 |
| GitHub | 固定 commit 的完整源码、Mapper、迁移和文档 | 全部结构化冻结归档准入 | 内容 sidecar 保存完整固定 commit；浏览器只带 12 条真实行段，尚未进入正式 Evidence 准入 |
| 官方资料 | HTML/PDF/附件、OCR 页码 | 现有结构化资料；正文缺失为 GAP | 丰富的 `DEMO_AUTHORED` 业务资料；官方原文身份仍为 GAP |
| 知识库 | 多轮 Luna 生成、独立验证与人工确认 | 一次/少量受控生成 | 一次小型目标制度提案 |
| Semantica | 所有确认文档的可重放派生 | 候选知识库的广覆盖派生 | 三议题的派生说明 |
| 比较 | 全部 Claim | 核心对象与规则 | 三个固定议题 |
| 发布 | 正式 V2 receipt/pin 与审批 | 完整 Candidate V2 Bundle | 只读 Candidate Bundle，不是 V2 |
| 浏览器验证 | 全量引用与完整 E2E | 主故事与主要 E2E | 三议题故事和 1440/1024/390 |

### 当前 Demo 的已知 GAP

- 固定 commit 的完整 Git 树只保留在内容 sidecar；浏览器只部署 12 条 `SOURCE_NATIVE` 故事行段，它们不等于正式 Evidence 准入。
- 没有官方 HTML/PDF 正文；该来源的无法定位结论是 `GAP`。
- 没有业务行，不能推断分布、异常率、库存规模或质量趋势。
- Luna 仅分析三项议题，不能代表完整企业知识库。
- 没有私有发布 receipt/pin，候选不能声称为正式 V2。
- 冲突覆盖有限；不保证多标签并发修改正确性。

### 各实施等级完成后的剩余 GAP

**完整证据工厂（2–4 天）**仍明确保留：任何未授权/无法访问的来源、未获批准的业务样本、无法 OCR 或不能稳定定位的官方材料都是 `GAP`；它只描述冻结时点，不能代表之后的线上变化；任何 `GENERATED_TARGET` 仍须人类确认；多标签实时同步与并发胜者恢复仍不在范围内。所谓“完整”是可追溯的治理闭环，不是无遗漏的世界知识。

**第一轮裁剪（10–16 小时）**还缺：固定 commit 的完整源码原文和官方 HTML/PDF、业务样本/质量分布、将冻结结构化归档提升为 `OBSERVED` 的私有 Artifact、全来源/全识别项覆盖、正式 publication receipt/registry pin，以及覆盖所有页面状态的浏览器验收。它可冻结 Candidate V2，但不能发布正式 V2。

**当前真实 Demo**的精确 GAP 如上节列出：只涵盖三议题；GitHub 的浏览器摘录是 `SOURCE_NATIVE` 但尚未升级为正式 Evidence，官方状态 9 的官方原文仍是 `GAP`；没有业务行、官方 HTML/PDF、全系统正式知识库或正式 candidate receipt/pin。

### 从当前 Demo 升级到第一轮裁剪

1. 为 MySQL 的全部零行业务逻辑 Artifact 建立 fragments、claims 和 trace。
2. 准入全部现有 GitHub 结构化归档，并持续诚实标为 `FROZEN_RECORD`。
3. 完成一次广覆盖的 Luna 知识库提案和独立引用验证。
4. 为所有识别项投影关系/冲突，冻结完整 Candidate V2 Bundle。
5. 扩展到完整资料审阅 E2E。

### 从第一轮裁剪升级到完整证据工厂

1. 私有证据仓保存固定 commit 的完整源码。
2. 归档官方 HTML/PDF，必要时增加 OCR Artifact 与页码映射。
3. 在明确授权与脱敏审批下加入业务样本。
4. 将可复核记录从 `FROZEN_RECORD` 提升为 `OBSERVED`。
5. 多轮独立生成/验证、正式 publication receipt、redaction approval 和 registry pin。
6. 经过既有正式审核后才发布 V2。

升级是单调的：旧 snapshot、fragment、决定和 Bundle 永不原地覆盖；新能力总是新版本和新的验证结果。

## 为什么该方法可治理数据

- 输入冻结且可定位，结论可以回到原始依据。
- 不完整性显式成为 `GAP`，不会被语言模型悄悄填充。
- 派生图和生成制度不会冒充新的独立事实。
- 范围与时间先规范化，避免将租户差异、阶段差异或历史版本误判为矛盾。
- 互证/冲突规则可重放，模型只辅助理解与提出候选。
- 决定、理由、角色和 revision 相互绑定，可审计、比较与回滚。
- 前端不承载事实判断，因而可替换为未来 Agent 或不同界面而不损失治理语义。

## 未来 Agent 与前端的稳定边界

```text
EvidenceGateway.read
→ GovernanceAgent.propose
→ CitationValidator.validate
→ GovernanceProjector.compare
→ ReviewCommand.recordDecision
→ BundleFreezer.freeze
→ Frontend read-only projection
```

- `EvidenceGateway` 只读取已准入的冻结资料。
- `GovernanceAgent` 只返回结构化 Proposal 和引用，不能写入运行。
- `CitationValidator` 是确定性门禁。
- `GovernanceProjector` 是确定性比较器，输出关系和冲突候选。
- `ReviewCommand` 是唯一修改审阅 revision 的入口，负责权限、CAS 与审计。
- `BundleFreezer` 只接受已验证、已决定的 revision。
- 前端只展示投影与提交命令。

这使未来真实 Agent 的接入成为替换 `GovernanceAgent.propose` 的问题，而不是把网络、模型、证据判断或正式发布能力塞进浏览器。

## 运行与维护规则

- 正常开发、Demo、恢复、测试、构建和部署只读取仓库内固定 Bundle；清空浏览器缓存不会触发回源。
- 当前仓库中，`npm run evidence:guanyijia:capture` 只做受控的本地维护前置检查，`npm run evidence:guanyijia:admit` 从已有冻结资料生成当前候选证据。当前没有 `evidence:guanyijia:generate` 或 `evidence:guanyijia:freeze` 的可执行 npm 入口；本次小型目标制度由一次明确授权的 Luna/ChatGPT session 生成后作为已验证资产提交。
- 完整工厂中的采集、生成与冻结是未来独立维护工作流：必须显式授权、产生新 immutable version，并保留前一版本。实现这些入口前，文档和产品不得暗示它们已经可执行。
- 当前 Demo 的 Candidate Bundle 只使用已有冻结资产；不连接数据库、拉取 Git、抓取官网，也不调用 LLM。
- 原始数据库行、私有源码与原始文档不进入 public Bundle 或 Demo 服务器。

## Source Material 内容 sidecar

除了正式证据链，本仓库还可以维护一个与其完全隔离的“Source Material 内容 sidecar”。首个版本为
`guanyijia-demo-content-v1-20260821`，最近一次已冻结的富内容版本为
`guanyijia-demo-content-v6-20260826`，目录位于父工作区的
`modeling-evidence/guanyijia/demo-content/snapshots/`。它服务于未来 Demo 的丰富阅读材料和源码溯源，
但**不是** `EvidenceGateway.admit` 的输入，也不会生成 formal Claim、Assertion、Conflict、模型对象、
正式 V1 差异或 publication receipt。

| 内容 | origin | 可用于什么 | 绝不能用于什么 |
| --- | --- | --- | --- |
| MySQL `reference.json` | `SNAPSHOT_REFERENCE` | 指向既有零行逻辑快照及范围说明 | 复制或推断业务行、样本、profile |
| 固定 Git commit 全源码 | `SOURCE_NATIVE` | `commit + path + startLine + endLine` 的真实源码定位 | 自动成为正式事实或取代审阅准入 |
| 官方业务说明 Markdown | `DEMO_AUTHORED` | 丰富、可读的演示资料 | 冒充外部官网原文或引用链 |
| ERP 管理制度 Markdown | `DEMO_AUTHORED` | 待讨论的业务制度与例外 | 认定为现行生产制度、自动解决冲突 |
| 术语图 Markdown/JSON | `DERIVED_DEMO` | 术语导航、关系展示和 Demo 理解 | 增加独立根证据数量 |

sidecar 使用自己的 manifest、文件 SHA-256、固定 Git tree 与生成记录校验。所有 Markdown 只要求文件级完整性
和稳定章节 ID；它们不具备正式证据链必须的 fragment→claim→assertion→anchor 闭合。因此，接入前端时必须显示
其 origin，并把来源准确性规则保持在正式 Evidence Bundle 一侧。

### 当前 Demo 的内容投影与运行绑定

当前实现由 `DemoContentReview` 作为唯一 seam 接入内容 sidecar。开始资料整理时，模块把 `runId`、五项正式
`sourceId + snapshotId`、内容快照 ID 和内容 SHA 绑定为 `DemoContentRunBinding`；再次读取来源时，它重新计算并
验证这个绑定，再返回单源 `SourceReviewProjection`。投影读取失败、来源顺序不一致、摘要不一致、Git 行段越界或
Markdown/trace 不闭合时，只让该来源 fail closed，绝不回退旧模板或访问外部源。

浏览器发布包不复制 GitHub 的完整 719 文件。显式维护命令
`npm run evidence:guanyijia:content:package` 只从已经校验的 sidecar 裁剪当前阅读所需的有界源码摘录，并生成
已提交的静态资源；`package:check` 只重算并对照该资源。正常启动、测试、构建、恢复和 Demo 只读生成资源，既不
调用这两个命令，也不会下载 Git 或调用模型。

每个来源消费相同的三份冻结来源投影；审阅产生的受限 revision 只覆盖剧本 Claim 的可编辑文字，不改变来源材料：

```text
审阅事项：来源材料 → 待确认事项（本来源建议／跨来源比较）／待补充资料／已处理事项
审阅结论：核心业务结论 → 默认展开对象目录
标准化文档：由当前审阅 revision 投影的 V6 九章只读正文与 Markdown 源文
结论内依据：来源材料 → 审阅结论 → Markdown 段落
```

每条非元信息 Claim 持有完整 `evidenceRefs` 集合，而不是“第一条依据”。未决定的剧本化核对项和所有已准入的跨来源比较只属于审阅事项，不重复计入审阅结论；状态 9 资料不足也独立作为待补充资料留在事项。阅读区把中文结论放在上方，SQL／DDL／代码／文档摘录在其“查看来源依据”折叠区显示；文档说明和业务目标只关联快照或审阅说明，不能错误指向索引、外键或 DML。这样读者可以从任一结论回看所有依据，而不把匿名代码块误读为另一条结论。

当前最近一次已冻结的人类阅读资源为不可变的 `guanyijia-demo-content-v6-20260826`。它把每个可见 Claim 显式绑定到章节、章节用途、唯一 Markdown 锚点和全部材料；标题、空章节、TRACE 标记、HTML 注释、完整提交摘要和内部标识不会从 Markdown 邻近文本或序号规则反推到页面。旧内容版本、原始源码和正式 Evidence Bundle 继续原样保留。发布前的静态文字质量检查会拒绝乱码、HTML 实体、重复标题/正文、内部字段和原始异常文本；文档篇幅不是质量门禁或告警指标。计划中的 V7 只能追加自精确 V6；截至本说明更新时，V7 尚未冻结，不能被表述为当前资源。

这些投影保持来源纯度。跨来源关系仍由正式运行状态机根据已准入来源动态投影到审阅事项；内容 sidecar
不会新增 Claim、Assertion、Conflict、V1 差异或根证据数。来源配置使用同一绑定投影出五项只读配置，显示位置、
范围、正式和内容版本、来源性质、凭据状态与当前步骤，但不暴露连接、扫描、冻结或 YAML 管理能力。

### 当前 Demo 的作者直接定版与建模资格

当前 Demo 的标准化交付物可以包含资料缺口和无法确定项，而不是要求“零语义变化”。资料审阅和三项治理决定写入原有
revision/CAS 链后，运行作者以 `AUTHOR_CONFIRM_AND_FREEZE` 执行“确认结果并定版”。冻结生成完整 Markdown、可建模
结论清单和排除清单：

| 资格 | 写入完整 Markdown | 交给 AI 建模 |
| --- | --- | --- |
| `ELIGIBLE` | 是 | 生成候选 |
| `EXCLUDED_GAP` | 是，标明资料缺口与原因 | 不生成候选 |
| `EXCLUDED_UNCERTAIN` | 是，标明无法确定与原因 | 不生成候选 |

定版必须复核来源 revision、已保存的来源差异决定、正文／引用完整性、每项结论到 Markdown 的定位，以及排除项没有误入
建模候选。已登记的缺口或无法确定项不是失败条件；未作出决定、来源损坏、引用缺失或 CAS 失败才会阻止定版。定版记录作者、
时间与输入 revision；“前往 AI 建模”携带完整文档和结构化资格投影。AI 建模只能读取 `ELIGIBLE` 结论来生成候选，
仍展示但不编译被排除事项。新 UI 不收集审批意见、不提交独立审核，也不显示审核人；旧的
`AWAITING_INDEPENDENT_REVIEW` 和 `reviewerApproval` 记录保持可读兼容。无论候选如何变化，标准化阶段都不会修改正式 V1。

### 业务时间线与可回看记录

业务时间线是运行审计之上的只读导航投影。来源已读取、来源文档已生成／已审阅、已发现差异、已保存决定、结果已生成、
结果已定版和已交给 AI 建模都必须能重新打开对应资料。当前未完成步骤固定在顶部并提供唯一主操作；已完成记录不会因为定版
而失去入口。历史文档与历史决定以只读方式显示精确 revision、双方材料和当时结果；后来重做决定会创建新 revision、标记旧
结果需要重新生成，但不覆盖旧事件。关闭历史详情后恢复原时间线滚动位置与触发焦点。

### Sidecar 维护协议

本轮已定版的 V6 只通过以下显式维护命令校验和发布：

```bash
npm run evidence:guanyijia:content:check
npm run evidence:guanyijia:content:package
npm run evidence:guanyijia:content:package:check
```

这些通用命令固定验证和发布 `guanyijia-demo-content-v6-20260826`；V7 尚未冻结，不能成为它们的目标。`package` 只校验
固定 sidecar 并生成浏览器安全的已提交投影，不能联网、扫描或调用 Codex；`package:check` 只重算其摘要。检查命令只读取
本地文件，复核清单、Git file index、摘要、章节 ID、领域覆盖和术语图完整性。

V6 的不可缩水门禁比较的是语义结构，而非文档篇幅：五项来源与正式快照身份、每份文档的九章、结论全文、材料全文、
结论—材料—Markdown 锚点关联、合并文档，以及术语图的术语和关系都必须精确保留。当前固定基线为 115 条结论、
140 份材料和 8 项缺口；其中 MySQL 保留 30 张表与 6 个过程，GitHub 保留 43 条结论与 69 份源码材料，术语图保留
56 个术语与 80 条关系。门禁不读取或比较字数、行数和文件大小：精炼文字不会被当作缩水，但删除或改写可见内容、材料
或关联会使发布失败。

V7 仍是未来的显式候选维护。它先预检 Codex 状态目录的数据库写入能力和 `codex login status` 的 ChatGPT/Codex 登录，
拒绝 API 凭据，再读取精确 V6 中已经冻结的五份来源审阅资料。本轮明确启动的五个单来源尝试均因
`~/.codex/state_5.sqlite` 只读而在模型调用前失败：必须保留诊断回执、报告被阻断的 `CODEX_STATE_DB_WRITE` 能力和路径，
并停止；不得把它描述为模型、配额、来源内容或 Schema 失败，也不得自动重试。预检获准后的维护者才可明确启动五个互相独立的
`gpt-5.6-luna` / `high` 会话（每个来源一个），而不是共享一个调用或回退到按量 API；它不自动重试、不切换模型。候选必须使用
完全相同的九章：文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、示例问题、待确认事项。完整性校验
要求各章有非空、可读的正文或完整 GAP（缺少材料、限制、下一步）。它必须使用新的 snapshotId，不能覆盖已冻结版本；失败时不写
manifest，也不能以旧模板或伪原文补位。

普通启动、测试、构建、Demo、恢复和 UI 热更新均不得调用上述维护命令、下载 Git 或调用 Codex。
