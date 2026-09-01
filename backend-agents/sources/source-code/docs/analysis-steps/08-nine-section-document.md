# 九章文档

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `08-` 与运行目录 `steps/08-nine-section-document/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

分析步骤“仓库知识” 已有**一份覆盖全部 Flow 的结构化 RepositoryKnowledge**，但业务读者需要固定、可读的仓库级九章文档，审计者需要从每句话回到 Proof 和源码，调用者需要查看运行状态并复用已经验证的分析步骤产物。

NineSectionDocument的四个analysis step modules先由M1把RepositoryKnowledge coverage draft补成无环的final RepositoryCoverageLedger并生成九章plan，再完成plan-only Markdown渲染、typed Trace、不可变Candidate和whole-run manifest；独立validation是`ValidationModuleAddress`下的run级外部模块，不属于NineSectionDocument编号。每个analysis run严格只有**一个RepositoryKnowledge → 一份NineSectionPlan → 一份document.md**；禁止一Flow一Markdown，也禁止先生成Flow片段Markdown再文本拼接。每个模块一完成就把canonical JSON/JSONL payload与`module-receipt.json`原子安装，后继只能从receipt重开bytes；M1 final ledger是业务coverage校验与后继引用的module artifact，不增加reader-visible文件，`document.md`仍是整个run唯一的人读末端文件。NineSectionDocument不把前七分析步骤隐藏在最终archive背后。

## 2. 具体输入与 DepotHead 例子

输入是前七分析步骤immutable artifacts、FlowInterpretation唯一RepositoryInterpretationRegistry、RepositoryKnowledge唯一RepositoryKnowledge及其registry meaning lineage、RepositoryKnowledge给出的`RepositoryCoverageLedgerDraftReferenceV1`、NineSectionProfile、section ownership/sentence template profiles、Candidate series/round request、archive/trace/validation policies和预算。这个typed reference同时绑定`knowledge-accounting.json` carrier、其中的nested draft ID与draft schema；普通`ArtifactReference`不能表示“文件内部哪一个值”。draft只证明前七个分析步骤，不含reader/section owner，也不是可用于完成run的final ledger。DepotHead只是完整knowledge中的一个Flow/Gaps投影，不是文档范围。

> **示例分类：NARRATIVE_ILLUSTRATION / TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT。** 本文件用一条连贯ReaderItem/Trace故事展示目标归档接力；故事ID不建立跨analysis step remap，也不是fixture。历史pre-reset fixed-slice审计只能支持范围与Gap；当前SourceAnalysis尚未产生正文。技术unknown只用nullable/UNRESOLVED/Gap/fatal表达。

历史pre-reset DepotHead slice有Gap、0 Flow、0 Capsule、0 model round。它定义了未来0 Flow回归时仍须规划诚实九章的行为：

- 文档说明：固定 commit、八文件 BOUNDED_PATH_SET、能力边界；
- 业务目标/活动等章节：仅写已证明内容或 typed empty-section说明；
- 待确认事项：写明数据流/Fact/Flow closure 缺口；
- 不得写“批量审核或反审核流程已分析完成”。

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 下例完整展示一个`ReaderItemV3.GAP_QUESTION` variant；digest只是grammar-valid非重算值，不能复制为golden。它作为`SectionPlan{sectionNumber=9,sectionKey=PENDING_CONFIRMATION,...}`中的一项，`sectionKey`不重复写入非EMPTY ReaderItem：

~~~json
{
  "readerItemKey": "reader-item:1111111111111111111111111111111111111111111111111111111111111111",
  "readerItemKind": "GAP_QUESTION",
  "templateKey": "gap-question-v1",
  "typedSlots": {
    "subject": "DepotHead status persistence path",
    "missingRequirement": "closed cross-layer data-flow proof"
  },
  "ownerKnowledgeItemId": "knowledge:2222222222222222222222222222222222222222222222222222222222222222",
  "knowledgeItemIds": ["knowledge:2222222222222222222222222222222222222222222222222222222222222222"],
  "factIds": [],
  "meaningIds": [],
  "registryProposalIds": [],
  "provisionalKeys": [],
  "interpretationProposalIds": [],
  "selectedKeys": [],
  "gapIds": ["gap:3333333333333333333333333333333333333333333333333333333333333333"],
  "relationIds": [],
  "metricIds": []
}
~~~

这是该variant的完整wire字段，不是当前生成的 Markdown；canonical object key order仍由DESIGN §13.3决定。

## 3. 程序怎样工作

1. fresh-reopen RepositoryKnowledge 的五个semantic artifacts、RepositoryKnowledge `repository-knowledge-receipt.json`、run controls和唯一明确列出的 FlowInterpretation `repository-interpretation-registry`；按`RepositoryCoverageLedgerDraftReferenceV1`重开RepositoryKnowledge `knowledge-accounting.json`、验证carrier identity/schema/hash，再定位并重算唯一nested `RepositoryCoverageLedgerDraftV2`，单Flow PASS不能通过。除这一份被M1用于registry-lineage exact join的FlowInterpretation registry外，M1不重开VerifiedSourceInventory到BusinessFlows的原始analysis step bytes或其他FlowInterpretation bytes：只验证draft中其余`AnalysisStepPublicationReference`的wire形状、同run/semantic key、closed registry顺序/唯一性和与draft/preparation复制字段的逐字一致性；外部validator才逐bytes重开完整前六个分析步骤。
2. M1依据唯一RepositoryKnowledge与frozen NineSectionProfile先确定prospective `readerSemanticItemIds[]`和`sectionOwnerBySemanticItem{}`；它把draft的前六个分析步骤 publication refs、draft内无环`repositoryKnowledgeCoveragePreparationRoot`以及已经完成的RepositoryKnowledge publication ref分别写入`NineSectionDocumentCoveragePreparationV1`，再形成唯一`RepositoryCoverageLedgerV3`。RepositoryKnowledge publication ref只单向指向已经发布的RepositoryKnowledge，不会反向进入RepositoryKnowledge draft；final ledger的第八个coverage root是NineSectionDocument preparation root，不是未来NineSectionDocument analysis step root。顶层ledger对draft ref、reader IDs和owner map的复制必须与该preparation逐字段相等，不得形成两套lineage。
3. 使用与preparation逐字相同的semantic IDs/owner map建恰好九个仓库级SectionPlan；对每个Fact atom、admitted meaning、technical fallback、relation、metric和Gap生成唯一typed ReaderItem，admitted meaning必须复制typed `registryProposalIds/provisionalKeys/interpretationProposalIds/selectedKeys`引用，不能只留显示词。
4. 重算 atom/meaning/Gap dispositions：正文、技术依据、Gap 或 reasoned exclusion，禁止 silent loss；plan引用刚计算的final ledger `ArtifactReference`，final ledger绝不反向引用plan。
5. M1先固定typed draft reference和NineSectionDocument preparation identity，再固定final-ledger identity，最后固定plan-draft identity；canonical `repository-coverage-ledger.json`、`nine-section-plan-draft.json`与同一个`module-receipt.json`原子安装。M2只重开plan draft，M3重开plan draft和同一final ledger。
6. 在能力隔离的 renderer 中只打开已重验的plan bytes，按 frozen templates 生成 UTF-8/LF Markdown，并把Markdown bytes作为`nine-section-document-rendered-document-v2` JSON字段安装；M2不得创建`.md`文件。
7. 编译typed Trace：业务解释走 `ReaderItem → knowledge → meaning → selectedKey → interpretationProposal → provisionalKey → registryProposal → Capsule basis → Fact/Proof → Evidence/source → snapshot`；fallback/Fact/Gap走各自既定typed分支。
8. 组装 Candidate/series/ReaderCandidateRound lineage、validation baseline和`upstreamAnalysisStepRoots[7]`；Candidate不引用尚未计算的NineSectionDocument root。
9. M1–M3各自先安装canonical payload+receipt；M4的package-private coordinator从fresh reopened M1–M3/request/upstream/final-ledger确定性派生一次typed `AnalysisStepInstallRequest`，**仅由AnalysisStep store**从M2 JSON内的已验证Markdown bytes创建最终`document.md`及其余exact五个semantic analysis step payload。该request是同一调用栈内的ephemeral command，不是module output、receipt或可复用跨模块状态。`CanonicalAnalysisStepArtifactStore`使用不含尚未存在M4 reference的`NineSectionDocumentCoordinatorPreparationProvenance`，按五semantic→archive manifest→analysis step receipt顺序安装；receipt绑定M1–M3、request、前七个分析步骤和M1 final-ledger refs，绝不绑定RepositoryKnowledge draft。
10. `AnalysisResult`先由已验证ledger按8.4唯一映射并进入`RunManifestInstallRequest`；`CanonicalRunManifestStore`随后只在`runs/<runId>/run-manifest.json`安装唯一root manifest。最后M4才安装`nine-section-document-candidate-run-publication-v4`及module receipt，payload只引用八项standalone outputs/locations且逐字复制root manifest result，不嵌套第二份RunManifest。single-process worker重开并验证M4/root manifest后把`execution-status.json`写为`FINISHED`并绑定同一result。
11. 后两种diagnostic result不可Selection但可检查。validate可在fresh process从source registration和所有module/analysis step receipts重开bytes；它不改Candidate/result或manifest，但会把validation payload+receipt幂等安装到run外validation目录。若需要重做NineSectionDocument，调用者以`executeStep(targetAnalysisStepKey=nine-section-document)`创建新run并显式传入已验证前七个分析步骤publication references；七个上游分析步骤不重执行，NineSectionDocument仍为0 Provider call。

## 4. 生成的可观察产物

NineSectionDocument的前七项位于analysis step目录，第八项位于run root：

| 文件 | 唯一职责 |
| --- | --- |
| `steps/08-.../nine-section-plan.json` | `nine-section-document-nine-section-plan-v3`；run完成后外部rerender/validator的唯一plan输入、九章typed reader AST与dispositions；M2在analysis step publication前只读M1 plan-draft module payload |
| `steps/08-.../document.md` | `nine-section-document-document-markdown-v1`；唯一reader-facing Markdown |
| `steps/08-.../trace.jsonl` | `nine-section-document-trace-record-v3`；每个ReaderItem的self-describing typed lineage |
| `steps/08-.../candidate.json` | `nine-section-document-candidate-v4`；Candidate/series/round/run/upstream roots和UNPUBLISHED status |
| `steps/08-.../validation-baseline.json` | `nine-section-document-validation-baseline-v1`；安装前deterministic checks的不可变baseline |
| `steps/08-.../nine-section-archive-manifest.json` | `nine-section-document-archive-manifest-v1`；五semantic descriptor set/size/SHA/root |
| `steps/08-.../nine-section-document-receipt.json` | `analysis-step-receipt-v1`；controls、preparation provenance、archive和semantic root |
| `runs/<runId>/run-manifest.json` | `run-manifest-v1`；八个analysis step refs、request/control refs、ledger、唯一knowledge/plan/document/Candidate和最终result |

前七分析步骤目录仍位于`runs/<runId>/steps/`并保持一等资产。root run manifest引用它们；NineSectionDocument目录不得出现第二份run-manifest，也不把前七分析步骤复制成一份难以区分输入/结果的大JSON。

为避免自引用，NineSectionDocument `analysisStepArtifactRoot`只覆盖先固定的五个semantic payload。archive绑定五项/root且不加入root；analysis step receipt绑定archive/semantic root和acyclic M1–M3 preparation provenance，排除自身且不绑定未来M4；root manifest再绑定八个分析步骤 refs/receipt SHA/ledger并排除自身与未来M4。最后才安装`modules/04-archive/nine-section-document-publication.json`及其module receipt，它列齐八项references与两种位置但不内嵌manifest。任何实现把archive/receipt/run-manifest/M4放进semantic root、把root manifest放进analysis step目录或让receipt预报M4都会形成cycle并fatal。

M1的`modules/01-planner/repository-coverage-ledger.json`与M1 receipt是业务coverage校验资产，不是第九个reader-visible NineSectionDocument publication output。run-centric `artifact`可以按`ANALYSIS_STEP_MODULE` identity观察它，但不得把它复制到analysis step public set或改写八项表。它从`RepositoryCoverageLedgerDraftReferenceV1`单向派生，plan、M3、M4、NineSectionDocument receipt、root manifest和validator都逐字引用同一个final `ArtifactReference`；任何一处继续引用RepositoryKnowledge draft或另算第二个ledger identity都fatal。

已删除archive-v2曾把proven-facts.json、proof-pack.json、flow-slices.json、evidence-capsules.json、registry-bundle.json、model-rounds.jsonl、repository-business-model.json等作为final archive preimage；其可重验经验只保留为历史测试意图。目标实现必须改为引用或验证对应analysis step roots，避免“只到最后才第一次落盘”，不得恢复旧archive wire。

目标出口必须同时包含表中的八个文件，`nine-section-plan.json`必须有九个非空SectionPlan envelope，`document.md`必须有九个标题和final LF，Candidate必须是`UNPUBLISHED_CANDIDATE`。0Flow时业务内容可以是typed EMPTY_SECTION/GAP_QUESTION，但plan、Markdown、Trace、manifest和receipt不能省略或变成空文件。多Flow时仍只有一份仓库plan/document；所有Flow知识由planner在typed ReaderItems层组合，renderer绝不接收或拼接Markdown fragments。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 以下JSONL只是人类接力故事，`depothead-*`显示ID和`takesFrom`短语不是wire fields、不是silent remap，也不可作为schema fixture。

~~~jsonl
{"module":"planner","artifacts":["modules/01-planner/repository-coverage-ledger.json","modules/01-planner/nine-section-plan-draft.json"],"receipt":"modules/01-planner/module-receipt.json","takesFrom":["RepositoryCoverageLedgerDraftReferenceV1(carrier+nested ID+schema)","VerifiedSourceInventory through FlowInterpretation publication refs","completed RepositoryKnowledge publication ref","RepositoryKnowledge","NineSectionProfile/templates"],"says":{"repositoryKnowledgeDraftPreparationRootCopied":true,"allRepositoryKnowledgeDecisionLineageOwnershipIdsCopied":true,"finalLedgerDoesNotReferencePlan":true,"sections":9,"activityReaderItem":"reader-item:depothead-batch-audit","pendingReaderItem":"reader-item:runtime-status-policy-gap"}}
{"module":"renderer","artifact":"modules/02-renderer/rendered-document.json","receipt":"modules/02-renderer/module-receipt.json","takesFrom":["reopened nine-section-plan-draft.json only"],"says":{"documentStoredAs":"documentUtf8 in canonical JSON","moduleMarkdownFiles":0,"titles":9,"qualifies":"运行时状态政策待确认"}}
{"module":"trace","artifact":"modules/03-trace/trace-set.json","receipt":"modules/03-trace/module-receipt.json","takesFrom":["reopened plan","knowledge","FlowInterpretation registry/proposals","Proof/Evidence/source"],"says":{"readerItemKey":"reader-item:depothead-batch-audit","chain":"meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→Capsule basis→Proof→source"}}
{"module":"archive","artifact":"modules/04-archive/nine-section-document-publication.json","receipt":"modules/04-archive/module-receipt.json","takesFrom":["reopened repository plan","rendered-document JSON","trace","前七个分析步骤 roots","coverage ledger","NineSectionDocument receipt","root run manifest"],"says":{"candidateId":"candidate:depothead-round1","status":"UNPUBLISHED_CANDIDATE","analysisResult":"INCOMPLETE_SCOPE","lifecycleAfterEvent":"FINISHED","repositoryDocuments":1,"publicFiles":8}}
{"module":"run-validator","artifact":"validations/validation-depothead-round1/modules/01-run-validator/validation-receipt.json","receipt":"validations/validation-depothead-round1/modules/01-run-validator/module-receipt.json","takesFrom":["immutable CandidateReference","fresh run/source readers"],"says":{"validationStatus":"VALID_INCOMPLETE_SCOPE","analysisResult":"INCOMPLETE_SCOPE","documentReRendered":true,"providerCalls":0}}
~~~

ReaderItem保留registry proposal/provisional key/interpretation proposal/selected key/meaning/atom/gap IDs；Trace逐跳引用同一IDs。M2只有plan能力，M4最后绑定document/trace/analysis step roots；外部`run-validator`只追加validation publication，不改Candidate，也不扩充NineSectionDocument模块集合。

## 5. 下游怎样消费而不返工

- 业务审阅者只读 document.md。
- 解释文档结构的工具读 nine-section-plan.json。
- Trace 查询读 trace.jsonl 和引用的 analysis step artifacts；完整 validation 后才返回 source spans。
- Selection 只接收 immutable CandidateReference，不接收活动 run 目录。
- `executeStep(targetAnalysisStepKey=nine-section-document)`读取显式给出的validated前七个分析步骤publications，创建新run且不调用FlowInterpretation Provider。
- renderer 永远不打开源码、Proof、model rounds、registry 或任意 Path；独立 validator可以重开。

### 下游前置条件与后置保证

| 审阅/Trace/Selection/显式NineSectionDocument新执行开始前必须成立 | 分析步骤“九章文档” 成功后保证 |
| --- | --- |
| 前七个分析步骤 roots、run controls、分析步骤“九章文档” artifact set/root 与 Candidate identity 全部重验通过 | plan 恰九章且所有 semantic items 有 disposition；Markdown 可仅由 plan bytes 重渲染 |
| document SHA、Trace closure、archive/run manifests 和 `UNPUBLISHED_CANDIDATE` lineage 一致 | Trace 可从 ReaderItem 闭合回 knowledge/Proof/source；前七 analysis step roots 保持一等引用 |
| 独立 validation 成功后才允许 Trace 返回 source span，Selection 只收 immutable CandidateReference | 审阅无需模型；Selection 不接活动 run；NineSectionDocument新执行不重做已验证前七个分析步骤 |
| RepositoryCoverageLedger对complete source/site/entry/graph/fact/outcome/flow/interpretation/knowledge/reader owner全部闭合；knowledge/plan/document cardinality为1/1/1 | 读者看到完整仓库的一份九章；unsupported/failed/omitted均在Gap/排除accounting中，单Flow PASS无权完成run |

任一下游不得把未 validation 的 locator 当可信引用，也不得修改 Candidate 以修复执行状态、finding 或 selection 状态。

## 6. 终态成功、诊断归档、fatal 与显式复用

- **终态成功**：仅限`COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、完整RepositoryCoverageLedger `closed=true`；唯一RepositoryKnowledge→唯一九章plan→唯一document cardinality成立，所有semantic items有disposition/owner，document可重渲染，Trace/roots闭合，Candidate/run manifest原子安装。无Gap映射`COMPLETE / VALID_COMPLETE`。
- **终态带 Gap 成功**：仍必须满足上述完整capture和closed ledger。0 Flow、无term、静态未知或capability/profile Gap只有在**完整分母中的每项仍有唯一typed disposition**时才映射`COMPLETED_WITH_GAPS / VALID_COMPLETE_WITH_GAPS`；不能用profile缩小source/entry/Flow分母。文档可诚实展示Gap/EMPTY_SECTION并可进入Selection。
- **诊断终态结果（不是成功结果）**：`BOUNDED_PATH_SET`映射`INCOMPLETE_SCOPE`；`COMPLETE_CAPTURE`但ledger以非空closureReason和受影响ID集合法保持`closed=false`时映射`INCOMPLETE_COVERAGE`。两者可原子保存`repositoryCompletionEligible=false`的不可变Candidate并由validator分别报告`VALID_INCOMPLETE_SCOPE`或`VALID_INCOMPLETE_COVERAGE`。它们都进入`FINISHED`，不可Selection；继续扩范围或补覆盖必须创建新run。
- **fatal / invalid**：ledger缺失、不可解析、自相矛盾、分母未知或遗漏未显式记账，或出现单Flow冒充完成、多份knowledge/plan/document、per-Flow Markdown/fragment、章节少/多/乱序、item type/slot不匹配、atom/meaning/Gap丢失、hash/root/Trace/source validation断裂、archive collision、原子移动不可用或安全失败。单纯`closed=false`或scope非COMPLETE不是fatal；把它们伪装成COMPLETE，或无法证明已归档子集自身完整，才fatal。
- **run失败**：进程中断、Provider/完整性/执行错误使当前run成为`FAILED`或由调用者按失败处理；active v0不自动继续同一run。已经完整安装的module/analysis step artifacts仍可inspect和作为新执行的validated inputs。
- **显式复用**：`executeStep(targetAnalysisStepKey=nine-section-document)`必须先验证完整、连续且同一source/control basis的前七个分析步骤publications，再创建新run并只执行NineSectionDocument。它不依赖旧worker或内存，也不是同run自动继续。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 九章 owner/plan | 是 | 否 |
| Markdown 句式和样式 | 程序按冻结模板 | 否 |
| Trace、identity、archive、显式artifact复用校验 | 是 | 否 |
| 重新解释 Flow | 否，消费 分析步骤“仓库知识” | 否 |
| 写正文 | renderer，不是模型 | 禁止 |

分析步骤“九章文档” 没有 Provider Interface，运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

NineSectionDocument模块顺序固定且只包含 `M1 NineSectionPlanner` → `M2 PlanOnlyRenderer` → `M3 TypedTraceCompiler` → `M4 CandidateRunArchiver`。`IndependentRunValidator`是`ValidationModuleAddress(..., moduleNumber=1, moduleKey=run-validator)`下的外部V1模块，没有NineSectionDocument模块号，也不参与Candidate identity。所有模块调用共享的`CanonicalModuleArtifactStore`安装canonical JSON/JSONL payload与`module-receipt.json`；模块间只交换`ArtifactReference`并通过receipt重开，不交换draft对象、Path或mutable in-memory bypass。精确store类型、record组件顺序、`RunStoreBootstrap.openForTest(Path)`、目录/collision/reopen合同见总体设计§13.3.1，NineSectionDocument不得另造seam。

#### M1 NineSectionPlanner

- **解决的问题**：把RepositoryKnowledge只闭合到knowledge的coverage draft扩成包含reader/section ownership的唯一final ledger，并把每个 owned Fact atom、meaning、fallback、Gap 唯一分配到固定九章和 typed ReaderItem，形成 renderer 的全部语义输入。
- **精确上游输入及前置**：只读取8.0.1 M1行列出的content-addressed refs：run-request、profile-bundle、nine-section/reader-template/renderer profiles、唯一明确列出的FlowInterpretation `repository-interpretation-registry`、RepositoryKnowledge typed publication reference及RepositoryKnowledge五项semantic bytes；其中唯一`knowledge-accounting.json`是`RepositoryCoverageLedgerDraftReferenceV1.knowledgeAccountingRef`指定的carrier。M1必须重算nested draft ID/schema，验证draft的前六个分析步骤typed refs、`repositoryKnowledgeCoveragePreparation`、全部denominators/equations与`closedThroughRepositoryKnowledge/closureReasonCode`自洽，再验证已经安装的RepositoryKnowledge publication ref。除该registry外，它不重开VerifiedSourceInventory到BusinessFlows的原始analysis step bytes或其他FlowInterpretation bytes；只验证这些refs的wire形状、同run/semantic key、closed registry顺序/唯一性和在draft/preparation中复制的值，逐bytes重开完整前六个分析步骤属于external validator。final ledger尚不存在，绝不能作为M1 upstream或由调用者注入。envelope逐项绑定所有实际读取的ArtifactReferences且不存在inline budget/hidden control。
- **确定性顺序 / LLM**：验证单一knowledge和typed RepositoryKnowledge draft reference → 验证draft的前六个分析步骤 typed refs与无环RepositoryKnowledge preparation root（不重开其原始bytes）→ fresh-reopen已完成RepositoryKnowledge publication → 确定prospective reader semantic IDs/section owner map → 计算`NineSectionDocumentCoveragePreparationV1`及其root → 逐字段复制RepositoryKnowledge所有decision/lineage/knowledge ownership ID sets并构造`RepositoryCoverageLedgerV3`，严格比较顶层与preparation的draft ref/reader IDs/owner map → 固定final-ledger identity → 用同一map建九个repository-level SectionPlan → 对ADMITTED_TERM exact-join`RegistryMeaningLineage`并把`normalizedLabel/normalizedPurpose`逐字节写入typed slots → 生成其余typed slots/template key → disposition/accounting → plan引用final-ledger ref并固定plan identity → 两个payload+一个receipt原子安装；0 LLM。
- **目标输出与 DepotHead 示例**：同一M1 publication恰含`repository-coverage-ledger.json`的`nine-section-document-repository-coverage-ledger-v1` ModuleArtifact和`nine-section-plan-draft.json`的`nine-section-document-nine-section-plan-draft-v3` ModuleArtifact；bounded DepotHead ledger为`repositoryCompletionEligible=false/closed=false`并保留完整missing-ID accounting，plan在待确认事项放data-flow Gap。该module-only ledger不是NineSectionDocument八项public output之一。
- **必须保持的不变量**：final ledger的`repositoryCoverageLedgerDraftRef`逐字段等于RepositoryKnowledge给出的`RepositoryCoverageLedgerDraftReferenceV1`，其carrier和nested draft ID/schema都已重验；preparation中的`upstreamAnalysisStepCoverageRoots[6]`逐项等于draft，`repositoryKnowledgeDraftPreparationRoot`逐字等于nested RepositoryKnowledge preparation root，`repositoryKnowledgePublicationRef`逐字等于已完成RepositoryKnowledge publication。`analysisStepCoverageRoots[0..6]`逐项等于前七个分析步骤 analysis step roots，`analysisStepCoverageRoots[7]`等于preparation root而不是未来NineSectionDocument analysis step root；final ledger逐字保留RepositoryKnowledge的proposal/decision/meaning/lineage/fallback/knowledge/ownership ID sets，不含plan ID，plan必须含final-ledger `ArtifactReference`。顶层与`nineSectionDocumentCoveragePreparation`的`repositoryCoverageLedgerDraftRef`、有序`readerSemanticItemIds[]`和完整`sectionOwnerBySemanticItem{}`必须逐字段相等。每run恰一plan、九章恰一次/固定顺序标题；全部Flow知识在同一plan中，每semantic item恰一disposition/owner；ledger preparation和plan复制的semantic IDs/owner map逐字相同；每ADMITTED_TERM ReaderItem保存完整五段registry→meaning lineage，`businessTerm/businessPurpose`分别逐字节等于lineage的`normalizedLabel/normalizedPurpose`；无silent loss或per-Flow plan。
- **Gap / fatal / 确定性**：业务未知形成GAP_QUESTION/EMPTY_SECTION；缺/双owner、章错序、slot/type mismatch、atom loss fatal；相同knowledge/profile产生相同bytes。
- **给下游的后置保证**：M2只需从M1 receipt重开的exact plan-draft payload即可完整渲染；M3必须从同一receipt重开plan draft与final ledger，并可从每个ReaderItem回到typed knowledge refs。M4随后由该draft确定性构造public `nine-section-plan.json`，且后续所有ledger消费者使用M1的同一final ref，不让M2依赖未来analysis step publication。
- **明确非目标**：不写Markdown、不重读source/model、不重新merge/admit、不新增第十章。
- **公共测试 seam 与验收**：`plan(draftReference, repositoryKnowledgePublication, knowledge, profiles)`覆盖typed carrier/nested-ID/schema mismatch、draft→RepositoryKnowledge publication→preparation→final-ledger→plan单向identity、前六个分析步骤/RepositoryKnowledge root mutation、任一RepositoryKnowledge decision/lineage/knowledge ownership ID omission/substitution、任一reader owner/denominator mutation、完整五段lineage/任一hop删除、至少双Flow/一个RepositoryKnowledge/一个plan、跨Flowrelation或metric、0Flow、第二knowledge rejection、single-flow omission、template slot和item order；golden独立手写。
- **Luna/xhigh 测试指南**：创建 `NineSectionPlannerTest`，冻结RepositoryKnowledge五项files（`knowledge-accounting.json`含draft）、typed draft reference、RepositoryKnowledge publication reference、profiles、final-ledger和九章手写goldens于 `src/test/resources/analysis/document/planner/`。逐RED：carrier/nested-ID/schema验证→前六个分析步骤+RepositoryKnowledge无环identity DAG→preparation root→RepositoryKnowledge全部decision/lineage/knowledge ownership IDs逐字段守恒→ledger/plan owner equality→registry lineage→hop deletion→九章正向→0Flow Gap/EMPTY→owner/atom loss→slot/type→少多乱改章→order determinism→two-payload receipt atomicity；首RED因planner/schema缺失。只fake artifact reader，planner/accounting/canonical不可mock。命令：`mvn -Dtest=NineSectionPlannerTest test`；无网络/模型。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/document/planner/`，实现package-internal `NineSectionPlanner/RepositoryCoverageLedger/NineSectionPlan` 与`nine-section-document-repository-coverage-ledger-v1`、`nine-section-document-nine-section-plan-draft-v3`；只读RepositoryKnowledge+frozen profiles，draft→preparation→final ledger→registryLineage/sections/owner→typed item→disposition/accounting→two-payload shared-store install。逐RED GREEN；不得让ledger引用plan、把draft冒充final、增加public output、增章/补owner/丢lineage/自由句子。九章或跨analysis step改变MUST STOP并由Sol/ultra交用户，完成审计。

#### M2 PlanOnlyRenderer

- **解决的问题**：把已决定的Reader AST机械渲染为稳定、可读Markdown，同时证明renderer没有分析能力。
- **精确上游输入及前置**：从M1 receipt重开的canonical `nine-section-document-nine-section-plan-draft-v3` payload ArtifactReference和内置frozen template/escaping version；plan schema/identity/九章validation通过，进程无其他capability。public `nine-section-plan.json`此时尚不存在且绝不是M2前置。
- **确定性顺序 / LLM**：exact parse → 依section/item语义顺序选择template → typed slot escaping → UTF-8/LF/final-LF encode → document SHA；0 LLM。
- **目标输出与 DepotHead 示例**：`RenderedDocument{nineSectionPlanId,rendererProfileRef,documentUtf8,documentBytesSha256,byteCount}`作为`nine-section-document-rendered-document-v2` canonical JSON payload与module receipt安装；历史0 Flow回归例含九标题、scope和DepotHead proof Gap，不写成功Flow。M2目录不得出现`.md`；M4验证字段bytes/SHA后才创建唯一末端`document.md`。
- **必须保持的不变量**：每run renderer恰调用一次且唯一输入是单一仓库plan；相同plan bytes输出相同document bytes；标题/顺序与plan一致；无source/model/path/fragment访问。
- **Gap / fatal / 确定性**：plan里的typed Gap正常渲染；unknown schema/template/slot、body cleanliness、size/hash drift fatal；相同plan必须重渲染为相同bytes且不修改已安装输出。
- **给下游的后置保证**：M4得到可重现document SHA，审阅者不需模型；外部run-validator可在隔离环境重渲染比较。
- **明确非目标**：不选事实、owner/措辞语义，不填空章，不读Proof/registry/source，不接受per-Flow plan或Markdown fragment，不做文本拼接。
- **公共测试 seam 与验收**：`render(NineSectionPlanArtifact exactPlan)`在能力隔离进程运行，fixture层只挂载单文件；覆盖multi-flow single-document golden、第二plan/fragment拒绝、九章golden、escaping/LF、missing/extra field、source access trap和different-root determinism。
- **Luna/xhigh 测试指南**：创建 `PlanOnlyRendererTest`，exact plan与独立Markdown golden放 `src/test/resources/analysis/document/renderer/`。逐RED：九章bytes、0Flow内容、escaping/LF/finalLF、missing/extra/schema、source/model access trap、different-root determinism；首RED因renderer缺失。只可mock capability trap/plan file read，template/canonical输出不可mock。命令：`mvn -Dtest=PlanOnlyRendererTest test`；禁网络/Provider/source mount。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `analysis/document/renderer/`，实现内部 `PlanOnlyRenderer/RenderedDocument` 与 `nine-section-document-rendered-document-v2`；唯一输入是从M1 receipt重开的plan artifact，exact parse→template→escape→UTF8/LF/hash→shared store install。逐RED GREEN且隔离test通过；禁止读其他artifact、写`.md`或补语义。需改plan字段/九章MUST STOP交Sol/ultra/用户，更新审计。

#### M3 TypedTraceCompiler

- **解决的问题**：为每个ReaderItem建立无断链、typed、可验证的ReaderItem→source lineage，而不把Trace当Proof。
- **精确上游输入及前置**：M1唯一仓库plan与同一publication内的final ledger、RepositoryKnowledge唯一knowledge/registryLineage、FlowInterpretation registry/R0/R1/R2、BusinessFlows Capsules/flow、ProvenCodeFacts Proof、ProgramGraphs Evidence、VerifiedSourceInventory complete snapshot/inventory及trace/profile refs；实际打开的每个semantic `ArtifactReference`逐项进入envelope，不读取ApplicationDiscovery就不得虚列；final-ledger ref必须逐字等于plan所引用者，所有roots/IDs和ledger denominator按声明结果自洽。
- **确定性顺序 / LLM**：按readerItemKey → 根据kind选择trace schema → ADMITTED_TERM先连接knowledge/`registryLineageId`并核对plan slots与lineage规范值逐字节相同，再连接meaning/selectedKey/interpretationProposal/provisionalKey/registryProposal/Capsule basis，其他kind连接fallback/Fact/Gap → Proof/Evidence/source → hop/type/reference/accounting validation → trace root；0 LLM。
- **目标输出与 DepotHead 示例**：`TraceSet{records,readerItemCoverage,traceRoot}`；status ReaderItem链为meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→Capsule basis→Proof→XML span；当前Gap item链到missing requirement/searched scope。
- **必须保持的不变量**：九章中每个ReaderItem（含EMPTY_SECTION）恰一trace record；ADMITTED_TERM五段lineage不可跳跃、替换或反向；hop方向遵循identity DAG；source locator需validation后返回；Trace不生成缺失Proof；所有Flow知识回到同一`repositoryKnowledgeId`/`repositoryInterpretationRegistryId`。
- **Gap / fatal / 确定性**：GAP/EMPTY有typed lineage；missing/substituted hop、source drift、orphan/duplicate record、budget截断 fatal；相同plan/upstream重编结果逐字相同。
- **给下游的后置保证**：M4与外部run-validator得到self-describing trace root；审计查询可验证后逐跳返回，不需猜record kind。
- **明确非目标**：不改plan/document/Fact，不把locator存在当事实证明，不调用模型。
- **公共测试 seam 与验收**：`compileTrace(plan, knowledge, flowInterpretation, proofs, evidence, source)`覆盖multi-flow ReaderItems、八个closed kind、R0→meaning每hop omission/substitution/crossFlow、九章item completeness、source mutation、orphan/duplicate和different-root bytes。
- **Luna/xhigh 测试指南**：创建 `TypedTraceCompilerTest`，冻结plan/knowledge/FlowInterpretation/Proof/Evidence/source与手写hop goldens于 `src/test/resources/analysis/document/trace/`。逐RED：五段registry lineage→每hop omission/substitution/crossFlow→八个kind→Gap/EMPTY profile chain→synthetic ID rejection→source mutation→orphan/duplicate→root determinism；首RED因trace seam/schema缺失。只fake source reopen，Trace join/canonical不可mock。命令：`mvn -Dtest=TypedTraceCompilerTest test`；无网络。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/document/trace/`，实现package-internal `TypedTraceCompiler/TraceSet` 与 `nine-section-document-trace-set-v3`；只读从receipts重开的M1及VerifiedSourceInventory、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation、RepositoryKnowledge artifacts，按ReaderItemV3 kind和TraceHopV3 union join→hops→closure/root→shared store install。Gap chain解析既有Gap/source-request；ADMITTED_TERM解析完整R0/R1/R2链。不得制造Proof/ID或用Candidate反向ID。跨analysis step lineage不足MUST STOP升级用户，完成审计。

#### M4 CandidateRunArchiver

- **解决的问题**：把唯一plan/rendered-document JSON、Trace、RepositoryCoverageLedger、七个upstream analysis step roots和series/round lineage组成不可变未发布Candidate，再无环地形成NineSectionDocument root与精确`AnalysisResult`；所有四种结果都以`FINISHED`结束生命周期。
- **精确上游输入及前置**：只接收M1 final-ledger与plan-draft、M2 rendered-document、M3 trace-set `ArtifactReference`、前七个分析步骤 `AnalysisStepPublicationReference`、run request、Candidate series/round和archive policy/budget；每个实际重开的control、analysis step publication、module payload和receipt都进入M4 envelope `upstreamArtifacts[]`。Candidate、NineSectionDocument receipt和root manifest是本模块依次创建或随后fresh-reopen的对象，不能被误写成M4开始前的输入。final-ledger ref必须逐字等于M1 plan、M3 trace、NineSectionDocument preparation request所引用者。bounded scope只归档`repositoryCompletionEligible=false/INCOMPLETE_SCOPE`；完整capture且closed=false只归档`repositoryCompletionEligible=false/INCOMPLETE_COVERAGE`；两者不能进入Selection，但仍是`FINISHED`结果。
- **确定性顺序 / LLM**：fresh-reopen M1–M3、前七个分析步骤 publications、request-v2、series/round和archive policy/budget → 验证M2 `documentUtf8`/size/SHA → 构造绑定`upstreamAnalysisStepRoots[7]`的Candidate与baseline → `CanonicalAnalysisStepArtifactStore.install`五semantic→archive→receipt（preparation provenance不含M4）→ `CanonicalRunManifestStore.install`唯一root manifest → fresh-reopen Candidate/NineSectionDocument receipt/root manifest，安装只含八项refs/locations的M4 payload+receipt → worker把execution status写为`FINISHED`；0 LLM。
- **目标输出与 DepotHead 示例**：八个NineSectionDocument files及CandidateReference；历史0 Flow回归例要求status=UNPUBLISHED_CANDIDATE、0Flow roots、九章document SHA、Gap trace root。
- **必须保持的不变量**：Candidate最后绑定plan/document/trace和upstreamAnalysisStepRoots[7]，不含NineSectionDocument root；run-manifest才绑定analysisStepPublications[8]，无Candidate↔NineSectionDocument/M4↔manifest cycle；M4 payload只有`RunManifestReference`而无nested manifest；knowledge/plan/document cardinality为1/1/1；result映射唯一；前七analysis step不复制/吞并；request-v2 Round_1/ROUND_2 lineage闭合；install后immutable。
- **Gap / fatal / 运行结果**：在closed完整分母内唯一处置的typed content Gap不阻塞成功结果；合法scope/coverage不完整归档为`FINISHED`诊断Candidate；artifact/root/series/collision/atomic/size或不完整账本自相矛盾为fatal。若worker在最终状态写入前中断，当前run不声明完成；active v0不修补终态，完整安装的Candidate/manifest只保留供inspect/validation或新执行引用。
- **给下游的后置保证**：外部run-validator与Selection获得immutable CandidateReference和完整run manifest；Selection不见活动staging。
- **明确非目标**：不发布/选择、不重新render/trace/调用模型、不删除上游analysis step。
- **公共测试 seam 与验收**：`archive(plan, document, trace, requestV2, policy)`覆盖multi-flow唯一文档、ledger未闭合、Candidate含NineSectionDocument root自环、duplicate manifest、五semantic→archive→receipt→root manifest→M4各partial-install边界、collision、ROUND_1/2与ROUND_3 rejection、tamper和0Flow Candidate；只有无环七analysis-step-file+一root-file set及最终M4 reference返回成功。
- **Luna/xhigh 测试指南**：创建 `CandidateRunArchiverTest`，M1–M3/run artifacts与eight-output goldens放 `src/test/resources/analysis/document/archive/`。逐RED：ROUND_1、ROUND_2 exact lineage、v1/ROUND_3 rejection、five→archive→receipt→root manifest→M4各partial-install边界、collision/atomic unsupported和fresh-reopen validation。使用真实module/analysis step/run-manifest stores；identity/manifest不可mock。命令：`mvn -Dtest=CandidateRunArchiverTest test`；禁网络/Provider。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/document/archive/`，实现内部 `CandidateRunArchiver/CandidateReference` 与 `nine-section-document-candidate-run-publication-v4`；从receipts重开M1–M3+analysis step roots+request-v2，严格调用AnalysisStep store→RunManifest store→M4 module store→status update。M4只存refs/locations，不嵌manifest；不得发布/复制上游/扩Round。跨analysis step/lineage变更MUST STOP交Sol/ultra/用户，更新审计。

#### External IndependentRunValidator (`ValidationModuleAddress`, module 01)

- **解决的问题**：在fresh process重算全run closure，防止archive内部重复字段互相“自证”。
- **精确上游输入及前置**：先构造8.0.1定义的八个typed analysis step refs、按artifactId排序的`directArtifactRefs[]`和按fileId排序的`sourceFilePreimages[]`；这些显式列齐fresh validation会打开的Candidate/M4/root manifest、analysis step/module payload+receipts、semantic outputs、request/control/profile/policy/source-registration、M1 final coverage ledger与source file SHA。validator从FlowInterpretation九项semantic artifacts验证R0/R1/R2 tasks与`ModelTaskDispositionV1`的`E+2R`双射，并把所有实际打开的canonical artifacts列入`directArtifactRefs[]`。final ref必须逐字等于plan/M3/M4/analysis step receipt/root manifest链；RepositoryKnowledge draft只能作为M1 ledger的已绑定preimage，不能替代final。不信任原进程cache、对象或传递root替代bytes。
- **确定性顺序 / LLM**：VerifiedSourceInventory起重验files/modules/analysis step roots → 从FlowInterpretation public tasks/dispositions重算`E+2R` closure、accepted round/receipt refs和R2 `NOT_RUN_UPSTREAM_FAILED` chain → 全仓coverage ledger/shard unions → facts/flows/R0 registry/R1/R2/唯一knowledge/唯一plan/document → plan-only rerender → registry-to-source Trace closure → acyclic candidate/run identities → validation receipt；0 LLM/Provider。
- **目标输出与 DepotHead 示例**：`ValidationReceiptV4{validationId,runId,candidateRef,candidateRunPublicationRef,runManifestRef,analysisStepPublicationRefs[8],directArtifactRefs,sourceFilePreimages,repositoryCoverageLedgerRef,analysisResult,validationStatus,validatedAnalysisStepRoots[8],traceRoot,checks}`作为`validations/<validation-id>/modules/01-run-validator/validation-receipt.json`与同目录module receipt安装；八文件bounded例为`INCOMPLETE_SCOPE/VALID_INCOMPLETE_SCOPE`，完整capture但coverage未闭合例为`INCOMPLETE_COVERAGE/VALID_INCOMPLETE_COVERAGE`；两者都不能Selection。
- **必须保持的不变量**：每check独立从bytes重算；任何tamper fail closed。validate相对Candidate、AnalysisResult和analysis step/root manifests严格read-only，但`validationId`由Candidate ref+validation policy ref确定，且通过module store幂等写外部append-only payload/receipt；相同input返回同一reference，不同bytes collision失败。
- **Gap / fatal / 可重验**：业务Gap仍可VALID；schema/root/source/document/trace/result mismatch为INVALID/fatal code；validation可重复执行，但每receipt绑定相同Candidate bytes。
- **给下游的后置保证**：Trace查询/Selection只接受VALID receipt匹配的Candidate；不需信任生成进程。
- **明确非目标**：不修artifact、不调用Provider、不批准发布。
- **公共测试 seam 与验收**：`validate(CandidateReference, ReadOnlyRunStore, SourceRegistry)`不得mock canonical/identity；覆盖multi-flow完整ledger、single-flow PASS/other omitted、shard缺/重叠、第二knowledge/plan/document、identity cycle、每层tamper、coherent duplicate-field rewrite、source mutation、0Flow valid和different-root reopen。
- **Luna/xhigh 测试指南**：创建 `IndependentRunValidatorTest`，valid run copy和逐层tamper fixtures在 `src/test/resources/validation/run-validator/`。每RED只改一层：valid0Flow、module/analysis step root、Fact/Flow/FlowInterpretation task disposition或round/receipt/R2 upstream-task、knowledge/plan/document/Trace/source、coherent duplicate rewrite、different-root reopen；明确预期code。只fakeReadOnlyRunStore/SourceRegistry I/O，canonical/identity/validators不mock。命令：`mvn -Dtest=IndependentRunValidatorTest test`；禁Provider/network。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `validation/run-validator/`，实现内部 `IndependentRunValidator/ValidationReceipt` 与 `nine-section-document-validation-receipt-v4`；先冻结`directArtifactRefs/sourceFilePreimages/analysisStepPublicationRefs[8]`，再对这些fresh bytes按VerifiedSourceInventory→R0/registry/R1/R2→RepositoryKnowledge→NineSectionDocument→rerender→Trace→Candidate/run重算并通过shared store安装payload+receipt。逐层GREEN；不得信原对象/修artifact/调用Provider。若closure字段不足属跨分析步骤变更，MUST STOP并升级用户，完成审计。

#### Active v0 的能力边界

active v0 不提供同一run的进程重启续跑，capability manifest固定报告 `RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`。进程中断后，调用者创建新run；若目标只是重做NineSectionDocument，可用`executeStep(targetAnalysisStepKey=nine-section-document)`显式传入validated前七个分析步骤publications，从业务artifact边界开始新执行。未来同run自动恢复只记录在[运行时恢复 TODO](../supplements/runtime-recovery-todo.md)，该补充不是实现合同，不得据此增加public resume Interface、runtime journal或终态修复代码。

### 8.0.1 模块 artifact wire schemas

本节字段表、closed enum、顺序和direct-preimage规则都是exact合同。大型跨分析步骤示例不再冒充一条可重放链；任何fixture若标为`STRICT_REPLAY_GOLDEN`，必须在自己的fixture目录保存这里列出的全部preimage bytes并重算identity。未展示module envelope并不缩减`ModuleArtifact<T>`统一字段；每个模块仍按DESIGN §13.3.1保存完整envelope与receipt。

#### ReaderItemV3与template-slot闭集

`ReaderItemV3`字段固定为`readerItemKey!`、`readerItemKind!`、`templateKey!`、`typedSlots!`、`ownerKnowledgeItemId?`、`knowledgeItemIds[]!`、`factIds[]!`、`meaningIds[]!`、`registryProposalIds[]!`、`provisionalKeys[]!`、`interpretationProposalIds[]!`、`selectedKeys[]!`、`gapIds[]!`、`relationIds[]!`、`metricIds[]!`。全部数组按UTF-8 ID排序去重；`ownerKnowledgeItemId=null`当且仅当`EMPTY_SECTION`。kind/template/slot唯一配对如下，任何未列组合、slot缺失或额外slot都拒绝：

| readerItemKind | templateKey | exact typedSlots variant |
| --- | --- | --- |
| `TECHNICAL_FALLBACK` | `technical-scope-v1` | `TechnicalScopeSlotsV1{display!}` |
| `EMPTY_SECTION` | `empty-section-v2` | `EmptySectionSlotsV2{sectionKey!,effectiveProfileRef!,reasonCode!}` |
| `RECORD_REFERENCE` | `record-anchor-v1` | `RecordReferenceSlotsV1{record!,evidence!}` |
| `ADMITTED_TERM` | `activity-with-anchor-v1` | `AdmittedActivitySlotsV1{businessTerm!,businessPurpose!,technicalAnchor!,flow!,outcomes[]!}` |
| `FACT_SENTENCE` | `field-write-v1` | `FactFieldWriteSlotsV1{inputField!,targetColumn!}` |
| `RELATION_REFERENCE` | `relation-v1` | `RelationReferenceSlotsV1{from!,relation!,to!}` |
| `METRIC_REFERENCE` | `metric-with-gap-v1` | `MetricReferenceSlotsV1{metric!,definitionState!}` |
| `GAP_QUESTION` | `gap-question-v1` | `GapQuestionSlotsV1{subject!,missingRequirement!}` |

`effectiveProfileRef`是完整`ArtifactReference`，必须等于同一plan的`nineSectionProfileRef`；不能写裸`profileId`。`EmptySectionReasonV1`闭集只有`NO_SEPARATE_PROVEN_GOAL | NO_ADMITTED_EXAMPLE_QUESTION | NO_ADMITTED_SECTION_CONTENT`。`ADMITTED_TERM`必须携带RepositoryKnowledge同一lineage的四个registry arrays和meaning；其他kind的四个registry arrays必须为空。

#### TraceRecordV3与typed hop闭集

`TraceRecordV3{schemaVersion=nine-section-document-trace-record-v3,traceId,readerItemKey,traceKind,hops[]}`逐条对应一个`ReaderItemV3`。`traceKind`与`readerItemKind`使用同一八值闭集。`TraceHopV3`是以下sealed union，wire始终含`kind` discriminator：

- `IDENTITY {identityKind,id}`，其中`identityKind`闭集为`READER_ITEM | KNOWLEDGE_ITEM | REGISTRY_LINEAGE | MEANING | SELECTED_KEY | INTERPRETATION_PROPOSAL | PROVISIONAL_KEY | REGISTRY_PROPOSAL | MODEL_TASK | MODEL_ROUND | GENERATION_RECEIPT | EVIDENCE_CAPSULE | BASIS_ATOM | BASIS_GAP | FACT | FACT_ATOM | PROOF | PROGRAM_NODE | PROGRAM_EDGE | EVIDENCE | GAP | RELATION | METRIC | RESOLUTION | ANCHOR`；
- `ARTIFACT_REFERENCE {referenceRole,artifactRef}`，其中`referenceRole`闭集为`SEARCHED_SCOPE | NINE_SECTION_PROFILE | READER_TEMPLATE_PROFILE | RENDERER_PROFILE`，`artifactRef`是完整`ArtifactReference`；
- `SOURCE_EXCERPT {sourceExcerpt}`，值必须是DESIGN §13.2唯一`SourceExcerptV1`；不连续source拆为多个hop，禁止`path:line`、拼接raw或ellipsis；
- `SECTION {sectionKey}`，`sectionKey`只能是本文件`8.2`九个closed key；
- `TEMPLATE {templateKey}`，值必须与owner ReaderItem的closed pairing一致。

`EMPTY_SECTION`的hop序列固定为`READER_ITEM → SECTION → NINE_SECTION_PROFILE ArtifactReference → TEMPLATE`；`GAP_QUESTION`的`SEARCHED_SCOPE`是VerifiedSourceInventory admitted source-request完整ArtifactReference。每个source hop携带同一VerifiedSourceInventory verified file的`SourceExcerptV1`并由validation重新验证。

#### M1 final coverage ledger exact wire

RepositoryKnowledge `knowledge-accounting.json`内嵌的`RepositoryCoverageLedgerDraftV2`只含前六个分析步骤 publication refs、无环`RepositoryKnowledgeCoveragePreparationV1`、前七个分析步骤 denominators/shard receipts/equations和`closedThroughRepositoryKnowledge/closureReasonCode`，没有reader/section owner。NineSectionDocument不能用carrier的普通`ArtifactReference`冒充对nested draft的引用；它必须消费RepositoryKnowledge已经发布的下列typed reference。M1生成的`nine-section-document-repository-coverage-ledger-v1` ModuleArtifact payload必须是以下exact `RepositoryCoverageLedgerV3`；module schema、draft domain schema和final domain schema是三个不同版本，不得互换：

~~~text
RepositoryCoverageLedgerDraftReferenceV1
  knowledgeAccountingRef!: ArtifactReference
  repositoryCoverageLedgerDraftId!
  schemaVersion=repository-coverage-ledger-draft-v2

NineSectionDocumentCoveragePreparationV1
  schemaVersion=nine-section-document-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]!: AnalysisStepPublicationReference
  repositoryKnowledgePublicationRef!: AnalysisStepPublicationReference
  repositoryCoverageLedgerDraftRef!: RepositoryCoverageLedgerDraftReferenceV1
  repositoryKnowledgeDraftPreparationRoot!
  repositoryKnowledgeRef!: ArtifactReference
  nineSectionProfileRef!: ArtifactReference
  readerSemanticItemIds[]!
  sectionOwnerBySemanticItem{}!
  nineSectionDocumentCoveragePreparationRoot!

RepositoryCoverageLedgerV3
  schemaVersion=repository-coverage-ledger-v3
  repositoryCoverageLedgerId!
  repositoryCoverageLedgerDraftRef!: RepositoryCoverageLedgerDraftReferenceV1
  nineSectionDocumentCoveragePreparation!: NineSectionDocumentCoveragePreparationV1
  sourceScopeKind!: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible!: BOOLEAN
  sourceFileIds[]!
  analyzableTextFileIds[]!
  nonAnalyzableMediaFileIds[]!
  discoverySiteIds[]!
  entryIds[]!
  graphCandidateIdsByKind{}!
  factCandidateKeys[]!
  admittedFactIds[]!
  atomIds[]!
  outcomeCandidateIds[]!
  outcomePathIds[]!
  flowSliceIds[]!
  evidenceCapsuleIds[]!
  modelEligibleFlowSliceIds[]!
  modelIneligibleFlowSliceIds[]!
  modelIneligibilityGapIds[]!
  modelIneligibilityByFlow[]!{flowSliceId!,gapIds[]!}
  gapIds[]!
  registryProposalTaskIds[]!
  registryProposalRoundIds[]!
  registryProposalDispositionIds[]!
  registryProposalIds[]!
  acceptedRegistryProposalIds[]!
  rejectedRegistryProposalIds[]!
  repositoryInterpretationRegistryItemIds[]!
  provisionalKeys[]!
  interpretationTaskIds[]!
  interpretationRoundIds[]!
  flowInterpretationDispositionIds[]!
  flowInterpretationCandidateIds[]!
  interpretationProposalIds[]!
  interpretationProposalDecisionIds[]!
  flowAdmissionDecisionIds[]!
  admittedMeaningIds[]!
  registryLineageIds[]!
  technicalFallbackIds[]!
  repositoryKnowledgeItemIds[]!
  relationIds[]!
  metricIds[]!
  knowledgeConflictIds[]!
  semanticItemIds[]!
  ownerSemanticItemIds[]!
  reasonedSemanticExclusionIds[]!
  readerSemanticItemIds[]!
  sectionOwnerBySemanticItem{}!
  analysisStepCoverageRoots[8]!
  shardReceipts[]!: CoverageShardReceiptV1
  equations[]!: CoverageEquationV1
  closed!: BOOLEAN
  closureReasonCode?
~~~

M1必须先fresh-reopen`knowledgeAccountingRef`，验证artifact/schema/SHA，再要求nested draft的`repositoryCoverageLedgerDraftId`和`schemaVersion`逐字等于typed reference；只有三项都匹配，才能复制draft值。`NineSectionDocumentCoveragePreparationV1.upstreamAnalysisStepCoverageRoots`逐项等于draft，`repositoryKnowledgeDraftPreparationRoot`逐字等于draft内`repositoryKnowledgeCoveragePreparation.repositoryKnowledgeCoveragePreparationRoot`，`repositoryKnowledgePublicationRef`则指向已经完成且fresh-reopened的RepositoryKnowledge public set；RepositoryKnowledge draft从未引用该publication，所以引用方向无环。

final ledger从draft逐字段复制`sourceFileIds`到`reasonedSemanticExclusionIds`的全部denominator、decision、lineage、knowledge和ownership ID sets，不允许用`interpretationProposalDecisionIds`一个集合代替Flow admission、meaning、registry lineage、fallback或owner集合。复制后至少重验：`flowSliceIds = modelEligibleFlowSliceIds ⊎ modelIneligibleFlowSliceIds`、ineligible mapping domain/非空Gap/union闭合、eligible Flow恰等于FlowInterpretation dispositions、全部Flow恰等于Flow admission decisions、proposal decisions覆盖全部interpretation proposals、KEEP/NARROW恰对应meaning与registry lineage、semantic items恰为owner与reasoned exclusion的互斥并集。任一draft字段遗漏、改名、合并或顺序相关漂移都使M1失败。

`readerSemanticItemIds[]`、map keys和其他无序ID列表按UTF-8 bytes排序；`modelIneligibilityByFlow`按flowSliceId且每项gapIds排序。preparation root排除且只排除自身字段，ledger ID为`repository-coverage-ledger:SHA-256(frame(UTF8("repository-coverage-ledger-id-v3")) || frame(canonicalJson(ledgerWithoutRepositoryCoverageLedgerId)))`。`analysisStepCoverageRoots[0..5]`逐项复制前六个分析步骤 `analysisStepArtifactRoot`，`analysisStepCoverageRoots[6]`逐字等于`repositoryKnowledgePublicationRef.analysisStepArtifactRoot`，`analysisStepCoverageRoots[7]`逐字等于preparation root；任何NineSectionDocument analysis step root/receipt/archive/run manifest/M4/plan ID进入final ledger都会制造环并拒绝。`closed=true`要求draft自身闭合、全部复制/新增equation ID集合相等、互斥分子不交叠、shard union与完整分母相等且`closureReasonCode=null`；否则reason必须是版本化非空code。

顶层的重复字段只是查询便利，绝不是两套可选择的状态。M1与external validator均必须在计算identity前逐字段验证下列三式；任一不等为`REPOSITORY_COVERAGE_LEDGER_INVALID`：

~~~text
ledger.repositoryCoverageLedgerDraftRef == ledger.nineSectionDocumentCoveragePreparation.repositoryCoverageLedgerDraftRef
ledger.readerSemanticItemIds == ledger.nineSectionDocumentCoveragePreparation.readerSemanticItemIds
ledger.sectionOwnerBySemanticItem == ledger.nineSectionDocumentCoveragePreparation.sectionOwnerBySemanticItem
~~~

第一式比较完整nested draft reference，第二式比较有序ID数组，第三式比较完整key/value map。不能通过同时改两份不相等的复制字段自证`closed`。

#### 六种module payload合同与direct-preimage闭包

| owner / schema | exact direct upstream | exact payload与排序 |
| --- | --- | --- |
| NineSectionDocument M1 `planner` / `nine-section-document-repository-coverage-ledger-v1` | run-request、profile-bundle、nine-section-profile、reader-template-profile、renderer-profile、repository-interpretation-registry、RepositoryKnowledge五个semantic `ArtifactReference`及RepositoryKnowledge receipt的direct `ArtifactReference`；另在payload中保存并验证`RepositoryCoverageLedgerDraftReferenceV1`和typed `repositoryKnowledgePublicationRef`。`knowledge-accounting.json`是唯一draft carrier，final ledger不得作为upstream；全部实际读取的ArtifactReferences与envelope `upstreamArtifacts[]`逐字相同且按artifactId | 上述exact `RepositoryCoverageLedgerV3`；先验证carrier+nested draft+RepositoryKnowledge publication，再计算preparation/root，逐字段复制全部RepositoryKnowledge ID sets，最后计算ledger ID；不含plan ref或任何未来NineSectionDocument publication identity |
| NineSectionDocument M1 `planner` / `nine-section-document-nine-section-plan-draft-v3` | 与同publication final-ledger payload使用相同外部direct preimages；payload内另逐字引用刚固定的final-ledger `ArtifactReference`，该sibling binding不是RepositoryKnowledge draft的替代名 | `planDraftId!`、run/profile/knowledge/registry/final-ledger typed refs、`repositoryCardinality!{knowledgeCount=1,planCount=1,documentCountExpected=1}`、`sections[9]!`、`dispositions[]!`、`coverage!`；section按1..9，item按readerItemKey，owner map逐字等于ledger preparation |
| NineSectionDocument M2 `renderer` / `nine-section-document-rendered-document-v2` | 只含M1 `nine-section-document-nine-section-plan-draft-v3` payload `ArtifactReference`；renderer不打开public analysis step plan、profile/source/registry | `renderedDocumentId!`、`planDraftId!`、`rendererProfileRef!`、`documentUtf8!`、`documentSizeBytes!`、`documentSha256!`、`encoding=UTF-8`、`lineEnding=LF`、`finalLf=true`；size/SHA从exact UTF-8 bytes重算 |
| NineSectionDocument M3 `trace` / `nine-section-document-trace-set-v3` | M1 final-ledger与plan-draft payload refs、trace/profile refs及它实际join的VerifiedSourceInventory、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation、RepositoryKnowledge semantic `ArtifactReference`；逐项显式进入envelope，ApplicationDiscovery若未读取不得虚列 | `traceSetId!`、`planDraftId!`、`repositoryCoverageLedgerRef!`、`records[]!`、`readerItemCoverage!`、`traceRoot!`；ledger ref等于plan ref，records按readerItemKey，每项是`TraceRecordV3` |
| NineSectionDocument M4 `archive` / `nine-section-document-candidate-run-publication-v4` | M1 final-ledger+plan、M2/M3 payload、run-request、candidate-series/round、archive-policy、resource-budget、前七个分析步骤 `AnalysisStepPublicationReference`及其实际fresh-reopened publication/receipt bytes；其后产生的Candidate、NineSectionDocument receipt与root run-manifest也必须fresh-reopen并进入M4 direct preimage，但它们都不反向引用M4。所有实际refs逐字等于envelope upstream | `publicationId!`、`candidateRef!`、`analysisStepPublicationRef!`、`analysisStepPublicationRefs[8]!`、`runManifestRef!`、`repositoryCoverageLedgerRef!`、`publishedArtifacts[8]!`、`analysisResult!`；analysis step refs按analysisStepKey，final-ledger ref逐字等于M1/M3/analysis step/run链。M4绑定已安装NineSectionDocument receipt/root manifest作为后序preimage，NineSectionDocument receipt/root manifest不含M4，故不成cycle |
| external validation `run-validator` / `nine-section-document-validation-receipt-v4` | `directArtifactRefs[]`与envelope `upstreamArtifacts[]`逐字相同并按artifactId严格排序；显式枚举fresh validation实际打开的run-request、全部control/profile/policy refs、source-registration、八analysis step每个semantic/archive/receipt、每个module payload/receipt、M4、root manifest、Candidate和M1 final coverage ledger。FlowInterpretation九项semantic artifacts中的tasks、task dispositions、rounds和receipts按实际读取逐项列入；RepositoryKnowledge draft若因验证M1而读取也必须单独列入，不能冒充final；额外打开artifact必须先加入列表并改变identity | `validationId!`、`runId!`、`candidateRef!:ArtifactReference`、`candidateRunPublicationRef!:ModulePublicationReference`（逐字段指向NineSectionDocument M4）、`runManifestRef!:RunManifestReference`、`analysisStepPublicationRefs[8]!:AnalysisStepPublicationReference`、`directArtifactRefs[]!`、`sourceFilePreimages[]!{fileId!,sha256!}`、`repositoryCoverageLedgerRef!`、`analysisResult!`、`validationStatus!`、`validatedAnalysisStepRoots[8]!`、`traceRoot!`、`checks[]!`；ledger ref逐字等于plan/M3/M4/analysis step/root链，八analysis step refs/roots必须等于同一M4和root manifest链 |

`ValidationCheckV1`字段固定为`checkKey,status,recomputedId,failureCode`。`checkKey`闭集为`SOURCE_REGISTRATION | ANALYSIS_STEP_PUBLICATION_CHAIN | MODULE_RECEIPT_CHAIN | REPOSITORY_COVERAGE | REGISTRY_MEANING_LINEAGE | DOCUMENT_RERENDER | SOURCE_TRACE_CLOSURE | CANDIDATE_MANIFEST_BINDING`；`status=PASS | GAP | FAIL`。`failureCode` required-nullable，非null时闭集为`VALIDATION_SOURCE_INVALID | VALIDATION_ANALYSIS_STEP_PUBLICATION_INVALID | VALIDATION_MODULE_PUBLICATION_INVALID | VALIDATION_REPOSITORY_SCOPE_INCOMPLETE | VALIDATION_REPOSITORY_COVERAGE_INCOMPLETE | VALIDATION_REGISTRY_LINEAGE_INVALID | VALIDATION_DOCUMENT_RERENDER_MISMATCH | VALIDATION_SOURCE_TRACE_INVALID | VALIDATION_CANDIDATE_MANIFEST_MISMATCH`。`GAP`只允许两种repository incomplete code；其他不一致为`FAIL`。`recomputedId` required-nullable且非null时满足content-ID grammar。

validator读取source bytes时，`sourceFilePreimages[]`按fileId严格排序并绑定每个实际打开file的完整SHA；只读某段仍绑定完整file SHA并在返回locator前重验`SourceExcerptV1`。manifest/root/descriptor不能替代semantic/module/source bytes进入identity。FlowInterpretation验证只依赖九项public semantic artifacts与analysis step/module receipts：tasks和`ModelTaskDispositionV1`集合必须双射为`E+2R`，accepted disposition必须指向matching round/receipt，R2 `NOT_RUN_UPSTREAM_FAILED`必须指向同Flow R1 GAP/FAILED task。所有实际读取的artifact都逐项进入`directArtifactRefs[]`，不存在运行时目录扫描或隐藏输入。

#### 八个standalone output schemas与root贡献

下表是八个独立wire schemas；字段集合exact，未列字段拒绝。所有`*Ref`均为完整`{artifactId,sha256}`或DESIGN定义的typed publication reference；identity、descriptor ordering、root/receipt/manifest self-exclusion只用DESIGN §13.3.1的framed公式。

| logical output / schema | exact fields / bytes | dependency与root贡献 |
| --- | --- | --- |
| `nine-section-plan.json` / `nine-section-document-nine-section-plan-v3` | `schemaVersion,artifactType,artifactId,repositoryKnowledgeRef,repositoryInterpretationRegistryRef,repositoryCoverageLedgerRef,nineSectionProfileRef,profileBundleRef,rendererProfileRef,repositoryCardinality,sections[9],dispositions[],coverage`；items为`ReaderItemV3` | M4从已验证M1 plan draft确定性构造；作为五semantic set首项进入analysis step root |
| `document.md` / `nine-section-document-document-markdown-v1` | exact strict UTF-8 bytes、无BOM、LF only、final LF、九个按profile排序标题 | bytes来自只读M1 plan draft的M2 machine artifact，且必须与public plan重渲染一致；进入analysis step root |
| `trace.jsonl` / `nine-section-document-trace-record-v3` | 每行一个完整`TraceRecordV3`；按readerItemKey，nonempty/final LF | 只依赖plan+显式upstream refs；进入analysis step root |
| `candidate.json` / `nine-section-document-candidate-v4` | `schemaVersion,artifactType,artifactId,runId,analysisRunRequestRef,candidateSeriesRef,readerCandidateRound,parentCandidateRef,approvedFindingRefs[],repositoryKnowledgeRef,nineSectionPlanRef,documentRef,traceRoot,upstreamAnalysisStepRoots[7],repositoryCompletionEligible,status=UNPUBLISHED_CANDIDATE`；`artifactId`是唯一Candidate identity | 不含NineSectionDocument root；进入analysis step root |
| `validation-baseline.json` / `nine-section-document-validation-baseline-v1` | `schemaVersion,artifactType,artifactId,candidateRef,repositoryCoverageLedgerRef,analysisResult,checks[]{checkKey,status,recomputedId,failureCode}`；`artifactId`是唯一standalone identity | candidate固定后产生；进入analysis step root |
| `nine-section-archive-manifest.json` / `nine-section-document-archive-manifest-v1` | `schemaVersion,artifactType,artifactId,runId,analysisStepAddress,semanticArtifacts[5],analysisStepArtifactRoot,controls,preparationModuleReferences[3],analysisRunRequestRef,upstreamAnalysisStepReferences[7],repositoryCoverageLedgerRef` | 只描述五semantic/root；不加入root |
| `nine-section-document-receipt.json` / `analysis-step-receipt-v1` | `schemaVersion,analysisStepReceiptId,analysisStepArtifactRoot,runId,analysisStepKey,publicationProvenance=NineSectionDocumentCoordinatorPreparationProvenance{preparationModuleReferences[3],analysisRunRequestReference,upstreamAnalysisStepReferences[7],repositoryCoverageLedgerRef},upstreamAnalysisStepReferences[7],status,controls,semanticArtifacts[5],archiveManifest{fileName,artifactType,schemaVersion,artifactId,mediaType,sizeBytes,sha256},gapCount,gapRefs[]` | 与DESIGN §12.1同一exact record；analysis step store最后写，不进root且不引用未来M4 |
| `runs/<runId>/run-manifest.json` / `run-manifest-v1` | `schemaVersion,runManifestId,runId,analysisRunRequestRef,analysisStepPublications[8],repositoryCoverageLedgerRef,repositoryInterpretationRegistryRef,repositoryKnowledgeRef,nineSectionPlanRef,documentRef,traceRef,candidateRef,analysisResult,controls` | receipt后安装；排除self/未来M4 |

`nine-section-plan.json.artifactId`是plan唯一self ID，按DESIGN `STANDALONE_JSON`公式排除且只排除该字段；Java/API/reference记录中的`nineSectionPlanId`逐字使用这个`artifactId`，standalone wire不得再增加同名第二self ID。

依赖序列唯一是：plan→document/trace→candidate→baseline，形成五semantic set/root；再archive manifest→analysis step receipt→root run manifest→M4 publication/module receipt→`FINISHED` execution status。

#### 最小严格renderer byte golden

**示例分类：STRICT_REPLAY_GOLDEN（仅覆盖`PlanOnlyRenderer`九标题/UTF-8/LF pure byte seam，不是ModuleArtifact或跨analysis step identity golden）。** 全部输入都内联在下列record：九个heading strings、九个空body数组、exact `profileRules`、`lineEnding=LF`和`finalLf=true`；没有隐藏profile/path/artifact preimage：

~~~json
{
  "bodyItemsBySection": [[], [], [], [], [], [], [], [], []],
  "documentSha256": "67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5",
  "documentSizeBytes": 150,
  "documentUtf8": "## 文档说明\n## 业务目标\n## 业务对象\n## 业务活动\n## 字段与维度\n## 对象关系\n## 指标口径\n## 示例问题\n## 待确认事项\n",
  "finalLf": true,
  "headingLines": ["## 文档说明", "## 业务目标", "## 业务对象", "## 业务活动", "## 字段与维度", "## 对象关系", "## 指标口径", "## 示例问题", "## 待确认事项"],
  "lineEnding": "LF",
  "profileRules": {"emptyBodyEmission": "NONE", "headingPrefix": "## "}
}
~~~

该golden重算为150 UTF-8 bytes、九行`## `且final LF；测试必须重算SHA。完整module golden另须提供M1 final-ledger与plan-draft两个canonical artifacts及其同一receipt、M2 envelope/receipt的全部framed identities，不能扩大本byte golden范围。


### 8.1 Interface 与 records

NineSectionDocument finalizer、M1–M4以及run级external validator都是package-internal deep modules；分析侧所有外部调用统一通过以下**唯一public Interface**。本地capture不属于分析Interface；任何Adapter都不得直接打开run目录或绕过core：

~~~java
public interface RepositoryAnalysisAgent {
    AnalysisRunReference start(AnalysisRunRequest request);
    AnalysisRunReference executeStep(AnalysisStepExecutionRequest request);
    RunInspection inspect(String runId);
    ArtifactView artifact(ArtifactQuery query);
    RenderedDocumentReference render(String runId);
    ValidationReceipt validate(String runId);
    TraceView trace(TraceQuery query);
}
~~~

方法语义固定：`start`只接收exact request-v2，创建新runId并写`QUEUED`后返回；当前进程的single worker顺序执行八个分析步骤。`executeStep`只接收exact `analysis-step-execution-request-v1`，验证连续upstream publications和同一frozen basis，创建新runId，只执行target到final analysis step，不修改旧run或继承旧Provider状态。`inspect`返回`QUEUED|RUNNING|FINISHED|FAILED`及每analysis step/module摘要；`artifact`只按`runId+artifactId`返回完整验证的metadata，并且只在artifact policy标为`PATH_FREE_COMPLETE_UTF8`时返回完整bytes；`render`只从唯一plan重渲染；`trace`只在匹配validation下查询typed lineage。inspect/artifact/render/trace对run完全read-only；validate对Candidate/result/manifests read-only，但幂等写独立validation artifact+receipt。五种观察均零Provider call。active v0只有单进程worker，不提供进程重启后的同run继续。

Adapter映射必须一一同义：

| Core | Java | CLI | loopback HTTP |
| --- | --- | --- | --- |
| start | `start(request)` | `analyze --request <analysis-run-request-v2.json>`：拒绝v1；先持久化并打印runId，再由前台驱动single worker | `POST /analysis-runs`：body exact request-v2，`202 Accepted` + `{runId,lifecycleState:"QUEUED"}` + `Location`，不等待worker |
| executeStep | `executeStep(request)` | `analyze-step --request <analysis-step-execution-request-v1.json>`：打印新的runId；不得接受旧run cursor | `POST /analysis-step-executions`：body exact request-v1，`202 Accepted` + new `{runId,lifecycleState:"QUEUED"}` |
| inspect | `inspect(runId)` | `inspect --run <id>` | `GET /analysis-runs/{runId}` |
| artifact | `artifact(query)` | `artifact --run <id> --artifact-id <id> [--location-kind ANALYSIS_STEP_MODULE --analysis-step-key <key> --module-number <n> --module <key> \| --location-kind VALIDATION_MODULE --validation-id <id> \| --location-kind ANALYSIS_STEP_PUBLICATION --analysis-step-key <key> \| --location-kind RUN_MANIFEST] [--type <type>] [--sha256 <hex>] [--metadata-only \| --max-bytes <n>]` | `GET /analysis-runs/{runId}/artifacts/{artifactId}`；optional `expectedLocationKind`与该kind的完整typed fields组成`ArtifactLocation`，另有`expectedType/expectedSha256/contentMode/maxBytes` |
| render | `render(runId)` | `render --run <id>` | `POST /analysis-runs/{runId}:render` |
| validate | `validate(runId)` | `validate --run <id>` | `POST /analysis-runs/{runId}:validate` |
| trace | `trace(query)` | `trace --run <id> --reader-item <key> --max-hops <positive-int> [--expected-candidate <candidate-id>]` | `GET /analysis-runs/{runId}/trace?readerItemKey=<key>&maxHops=<positive-int>[&expectedCandidateId=<candidate-id>]` |

CLI的request文件是transport入口，不是artifact query。完整run request只含`sourceRegistrationId`与内容寻址refs；analysis step execution request只含原request ref、target/final analysis step和连续upstream publication refs。本地repository path只能交给独立capture CLI/adapter，绝不进入core records。`artifact` query必须给`runId + artifactId`，其他字段只作expected discriminators；location discriminator要么整组省略，要么构造完整`ANALYSIS_STEP_MODULE | VALIDATION_MODULE | ANALYSIS_STEP_PUBLICATION | RUN_MANIFEST` union variant。external module variants不接受或返回伪`analysisStepKey`，analysis step publication和root manifest也不得伪装成module owner。`trace`必须逐字段传递required positive `maxHops`和optional `expectedCandidateId`，缺少hop budget按`ARTIFACT_QUERY_INVALID`拒绝而不是补默认。HTTP只绑定loopback，要求run-scoped bearer capability与固定body/queue/response budgets；Java/CLI/HTTP逐字段传递同一request并返回相同stable code/digest/bytes。

~~~text
AnalysisRunRequest                       // exact schema analysis-run-request-v2; v1 rejected
  schemaVersion=analysis-run-request-v2
  sourceRegistrationId
  frozenRepositoryRequestRef: ArtifactReference
  profileBundleRef: ArtifactReference
  resourceBudgetRef: ArtifactReference
  toolchainRef: ArtifactReference
  schemaBundleRef: ArtifactReference
  promptBundleRef: ArtifactReference
  organizationRegistrySeedRef: ArtifactReference // required nullable
  artifactPolicyRegistryRef: ArtifactReference
  candidateSeriesRef: ArtifactReference
  readerCandidateRound: ROUND_1 | ROUND_2
  parentCandidateRef: ArtifactReference  // required nullable
  approvedFindingRefs[]: ArtifactReference

AnalysisStepExecutionRequest                    // exact schema analysis-step-execution-request-v1
  schemaVersion=analysis-step-execution-request-v1
  analysisStepExecutionRequestId
  analysisRunRequestRef: AnalysisRunRequestReference
  targetAnalysisStepKey: application-discovery | program-graphs | proven-code-facts |
                         business-flows | flow-interpretation | repository-knowledge |
                         nine-section-document
  finalAnalysisStepKey: target key or a later semantic key in the closed registry
  upstreamAnalysisStepPublications[]: AnalysisStepPublicationReference
    // exact continuous registry prefix before targetAnalysisStepKey

AnalysisRunReference                    // schema analysis-run-reference-v1
  schemaVersion=analysis-run-reference-v1
  runId
  analysisRunRequestId
  analysisStepExecutionRequestId                // required nullable; non-null only for executeStep
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult: null | COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE
  failureCode                            // required nullable; non-null iff FAILED

NineSectionPlanV3                       // exact standalone wire; component order matches 8.0.1
  schemaVersion=nine-section-document-nine-section-plan-v3
  artifactType                          // exact policy-registry value for this schema
  artifactId                            // canonical plan identity; public nineSectionPlanId aliases this value
  repositoryKnowledgeRef: ArtifactReference
  repositoryInterpretationRegistryRef: ArtifactReference
  repositoryCoverageLedgerRef: ArtifactReference
  nineSectionProfileRef: ArtifactReference
  profileBundleRef: ArtifactReference
  rendererProfileRef: ArtifactReference
  repositoryCardinality {knowledgeCount=1,planCount=1,documentCountExpected=1}
  sections[9]: SectionPlanV3
  dispositions[]
  coverage

SectionPlanV3
  sectionNumber: 1..9
  sectionKey: closed value from 8.2
  title
  readerItems[]: ReaderItemV3

ReaderItemV3                            // sealed eight-kind union; exact pairs in 8.0.1
  readerItemKey
  readerItemKind
  templateKey
  typedSlots
  ownerKnowledgeItemId
  knowledgeItemIds[]
  factIds[]
  meaningIds[]
  registryProposalIds[]
  provisionalKeys[]
  interpretationProposalIds[]
  selectedKeys[]
  gapIds[]
  relationIds[]
  metricIds[]

CandidateV4                             // exact standalone wire; component order matches 8.0.1
  schemaVersion=nine-section-document-candidate-v4
  artifactType
  artifactId                              // sole Candidate identity and TraceQuery expectedCandidateId value
  runId
  analysisRunRequestRef: ArtifactReference
  candidateSeriesRef: ArtifactReference
  readerCandidateRound: ROUND_1 | ROUND_2
  parentCandidateRef: ArtifactReference  // required nullable
  approvedFindingRefs[]: ArtifactReference
  repositoryKnowledgeRef: ArtifactReference
  nineSectionPlanRef: ArtifactReference
  documentRef: ArtifactReference
  traceRoot
  upstreamAnalysisStepRoots[7]
  repositoryCompletionEligible
  status=UNPUBLISHED_CANDIDATE

RunManifest                              // schema run-manifest-v1; only at run root
  schemaVersion=run-manifest-v1
  runManifestId
  runId
  analysisRunRequestRef: ArtifactReference
  analysisStepPublications[8]: AnalysisStepPublicationReference
  repositoryCoverageLedgerRef: ArtifactReference
  repositoryKnowledgeRef: ArtifactReference
  repositoryInterpretationRegistryRef: ArtifactReference
  nineSectionPlanRef: ArtifactReference
  documentRef: ArtifactReference
  traceRef: ArtifactReference
  candidateRef: ArtifactReference
  analysisResult: COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE
  controls

ValidationReceiptV4
  validationId
  runId
  candidateRef: ArtifactReference
  candidateRunPublicationRef: ModulePublicationReference // exact NineSectionDocument M4 publication
  runManifestRef: RunManifestReference
  analysisStepPublicationRefs[8]: AnalysisStepPublicationReference
  directArtifactRefs[]
  sourceFilePreimages[]
  repositoryCoverageLedgerRef
  analysisResult: COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE
  validationStatus: VALID_COMPLETE | VALID_COMPLETE_WITH_GAPS | VALID_INCOMPLETE_SCOPE | VALID_INCOMPLETE_COVERAGE | INVALID
  validatedAnalysisStepRoots[8]
  traceRoot
  checks[]: ValidationCheckV1

RunInspection
  runId
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult?
  analysisRunRequestId
  analysisStepExecutionRequestId?
  repositoryCompletionEligible           // false when final ledger is unavailable; otherwise equal final ledger field
  repositoryCoverageLedgerId?            // non-null iff NineSectionDocument M1 final ledger is installed and fresh-valid
  repositoryCoverageClosed               // false iff ledger ID is null; otherwise equal final ledger closed
  analysisStepReceipts[]
  moduleReceipts[]                         // sealed ModulePublicationAddress; canonical address order
  artifactDescriptors[]                    // ArtifactLocation, artifactType, artifactId; canonical location order
  gapSummaries[]
  failureSummaries[]

ModulePublicationAddress                // sealed tagged union; never a path
  ANALYSIS_STEP {runId,analysisStepKey,moduleNumber,moduleKey}
  VALIDATION {runId,validationId,moduleNumber=1,moduleKey=run-validator}

ArtifactLocation                        // public sealed tagged union; never a path
  ANALYSIS_STEP_MODULE {address: AnalysisStepModuleAddress}
  VALIDATION_MODULE {address: ValidationModuleAddress}
  ANALYSIS_STEP_PUBLICATION {address: AnalysisStepPublicationAddress}
  RUN_MANIFEST {address: RunManifestAddress}

ArtifactQuery
  runId
  artifactId
  expectedArtifactLocation?: ArtifactLocation
  expectedArtifactType?
  expectedSha256?
  contentMode: METADATA_ONLY | COMPLETE_UTF8
  maxBytes                                  // 0 metadata; positive all-or-error ceiling otherwise

ArtifactView
  runId
  artifactLocation: ArtifactLocation
  artifactType
  schemaVersion
  artifactId
  sha256
  sizeBytes
  mediaType: application/json | application/x-ndjson | text/markdown
  validationState: MANIFEST_VERIFIED | FULLY_VALIDATED
  immutableReference: ArtifactReference
  publicContentExposure: METADATA_ONLY | PATH_FREE_COMPLETE_UTF8
  contentUtf8?                              // 仅requested PATH_FREE_COMPLETE_UTF8时非null；从不截断

RenderedDocumentReference
  runId
  candidateRef: ArtifactReference
  nineSectionPlanId
  documentArtifactId
  documentSha256
  sizeBytes
  mediaType=text/markdown
  validationReceiptId?
  rerenderMatched=true

TraceQuery
  runId
  readerItemKey
  expectedCandidateId?
  maxHops                                  // required positive; all-or-error, never truncation

TraceView
  schemaVersion=trace-view-v1
  runId
  candidateRef: ArtifactReference
  validationReceiptRef: ArtifactReference
  readerItemKey
  traceRecordRef: ArtifactReference
  traceKind                                // same closed eight values as TraceRecordV3
  sourceValidationState: NO_SOURCE_HOPS | ALL_SOURCE_HOPS_REOPENED_AND_HASH_VERIFIED
  hopCount
  hops[]: PublicTraceHopV1                 // exact stored order; hopCount == length
  allHopsReturned=true

PublicTraceHopV1                           // path-free projection; sealed tagged union
  IDENTITY {identityKind,id}
  ARTIFACT_REFERENCE {referenceRole,artifactRef}
  SOURCE_EXCERPT {fileId,startByte,endByteExclusive,startLine,startColumn,endLine,endColumn,rawUtf8,rawUtf8Sha256}
  SECTION {sectionKey}
  TEMPLATE {templateKey}
~~~

request-v2 round规则不可由Candidate补默认：ROUND_1要求`parentCandidateRef=null`且`approvedFindingRefs=[]`；ROUND_2要求parent为同series/source/全部control refs的ROUND_1 Candidate，并要求非空、按artifactId排序的finding refs逐一引用该parent。ROUND_2不能作为parent，ROUND_3不存在。profile/budget/toolchain/schema/prompt/policy/seed均只以完整ArtifactReference出现；任何inline budget、仅ID无SHA、v1或unknown field均在创建run之前拒绝。Candidate逐字复制这些lineage refs，不能降成仅parent/finding IDs。

Candidate 的 `upstreamAnalysisStepRoots[7]` 只含 前七个分析步骤。NineSectionDocument root由五个semantic payload descriptors计算，随后才写入root RunManifest的`analysisStepPublications[8]`；root manifest又先于M4 publication，所以前项不引用后项，identity DAG无环。

`ArtifactQuery`始终以`runId+artifactId`做已安装receipt/root membership lookup；analysis-step-range execution没有root manifest时只能由其immutable analysis step/module receipts证明自身输出，不能依赖execution status或目录枚举。optional expected location/type/SHA任一不等即`ARTIFACT_IDENTITY_MISMATCH`。它不是目录浏览；`COMPLETE_UTF8`只可返回完整bytes，超预算必须以稳定code拒绝。它还必须命中artifact policy中`publicContentExposure=PATH_FREE_COMPLETE_UTF8`；否则以`ARTIFACT_CONTENT_NOT_PUBLIC`整体拒绝，绝不返回脱敏、截断或“附近”内容。任何含`SourceLocatorV1`、`SourceExcerptV1`、prompt或raw model response的raw artifact（包括raw `trace.jsonl`）强制为`METADATA_ONLY`。`expectedArtifactLocation`与`ArtifactView.artifactLocation`使用同一个sealed union：module payload/receipt归准确module variant，analysis step semantic/archive/receipt归`ANALYSIS_STEP_PUBLICATION`，root manifest归`RUN_MANIFEST`。`RenderedDocumentReference`只可指向唯一persisted document或其相同bytes的plan-only rerender；diagnostic Candidate可render/inspect但仍不可Selection。`TraceView`仍是raw Trace唯一fresh-validated、path-free source-hop projection。

`TraceView`是内部`TraceRecordV3`唯一公开、path-free投影。core先fresh reopen Candidate、匹配的`ValidationReceiptV4`、exact trace record及其source files；optional `expectedCandidateId`不等时整体拒绝。`maxHops`必须在`1..resourceBudget.maxTraceHops`且完整record不得超过它；超限以`OBSERVATION_BUDGET_EXCEEDED`整体失败，绝不裁剪。每个source hop重验file SHA、span和excerpt SHA后只返回fileId/coordinates/exact bytes/SHA；没有source hop时状态只能是`NO_SOURCE_HOPS`，否则全部通过才是`ALL_SOURCE_HOPS_REOPENED_AND_HASH_VERIFIED`。

`CodeToMarkdownAgent`及其pre-reset Interface、CLI和兼容路径已经由Wire Reset删除；不得恢复为public或nonpublic Adapter、委托桥、迁移桥或别名seam。唯一目标产品Interface是上述七方法，当前尚未实现。

#### 8.1.1 RepositoryAnalysisAgent handoff

- **Luna/xhigh测试指南**：创建`RepositoryAnalysisAgentContractTest`、`RepositoryAnalysisCliAdapterTest`和`RepositoryAnalysisLoopbackHttpAdapterTest`；共用`src/test/resources/runtime/run-centric-agent/`内一个完整multi-flow run、一个diagnostic run、一个以validated前六个分析步骤publications直接执行RepositoryKnowledge的fixture、四个`ArtifactLocation` variants的artifact identity表和独立response goldens。RED顺序固定为start→executeStep→inspect→artifact四location query→render→validate→trace candidate/maxHops/full-view，再逐项跑三Adapter conformance、wrong upstream lineage/identity/Path/cross-run/auth/budget/进程中断；每个RED因对应public method/mapping/guard缺失失败。允许mock只读run store、loopback transport、auth verifier和fault boundary；禁止mock identity lookup、canonical bytes、core状态/validation/Trace，禁止网络/live Provider/客户Maven/private实现耦合。命令：`mvn -Dtest=RepositoryAnalysisAgentContractTest,RepositoryAnalysisCliAdapterTest,RepositoryAnalysisLoopbackHttpAdapterTest test`。异常RED才由Sol/xhigh debug；任何public语义变化按DESIGN 13.11 STOP。
- **Terra/xhigh实现指南**：仅观察对应RED后，public `RepositoryAnalysisAgent`归`org.sourceanalysis.app`，core request/view与编排归`org.sourceanalysis.app.runtime`，Adapter分别归`org.sourceanalysis.app.adapter.cli`和`org.sourceanalysis.app.adapter.http`；实现/替换七方法、minimal single-process execution、`AnalysisStepExecutionRequest/RunInspection/ArtifactLocation/ArtifactQuery/ArtifactView/RenderedDocumentReference/TraceQuery/TraceView`和stable error mapping。vertical slices严格按七方法顺序，每slice须core selector与三Adapter同义selector GREEN，并同步本节实现审计；复用各analysis step persisted artifacts、NineSectionDocument M1–M4与run-validator，不能直接读run Path、复制编排逻辑、隐藏artifact、调用Provider或兼容pre-reset final-only语义。若artifact identity、状态/result边界或安全边界不够，STOP交Sol/ultra Design Authority；跨分析步骤修改需用户确认后先改DESIGN/analysis step docs。

### 8.2 精确九章

每章恰好一次，closed `sectionKey/title`配对、顺序均不得改变：

1. `DOCUMENT_GUIDE / 文档说明`
2. `BUSINESS_GOALS / 业务目标`
3. `BUSINESS_OBJECTS / 业务对象`
4. `BUSINESS_ACTIVITIES / 业务活动`
5. `FIELDS_AND_DIMENSIONS / 字段与维度`
6. `OBJECT_RELATIONS / 对象关系`
7. `METRIC_DEFINITIONS / 指标口径`
8. `EXAMPLE_QUESTIONS / 示例问题`
9. `PENDING_CONFIRMATION / 待确认事项`

不得新增第十章、改名或交换。空章使用`ReaderItemV3.EMPTY_SECTION`，其`empty-section-v2` slots保存exact `sectionKey/effectiveProfileRef/reasonCode`，且profile ref逐字等于plan的`nineSectionProfileRef`；validator不能从当前代码补推缺失字段。

### 8.3 Renderer 隔离

renderer内部 Interface（不是run-centric公共API）：

~~~java
byte[] render(NineSectionPlanArtifact exactPlan);
~~~

它只解析 one exact canonical plan。进程/模块能力中没有 snapshot root、source registry、Provider、model round、Fact prover 或 network。输出统一 UTF-8、LF、final LF。相同 plan bytes 必须输出相同 Markdown bytes。

### 8.4 Trace

traceKind闭集恰为八种，并与ReaderItem kind一一对应：

- FACT_SENTENCE：ReaderItem→Fact atom→Proof→Evidence/source；
- ADMITTED_TERM：ReaderItem→knowledge→meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→R0 task/round/receipt→EvidenceCapsule→basis atoms/Gaps→Proof/Evidence/source；
- TECHNICAL_FALLBACK：ReaderItem→resolution→policy/template→anchor proven slots；
- GAP_QUESTION：ReaderItem→canonical Gap（其字段给出 missing/closure requirement）→VerifiedSourceInventory admitted source-request artifact（searched scope）；
- RELATION_REFERENCE：ReaderItem→relation→两端knowledge/anchor；
- METRIC_REFERENCE：ReaderItem→metric→basis Facts/Gaps；
- RECORD_REFERENCE：ReaderItem→record knowledge→anchor→Fact/Proof/source；
- EMPTY_SECTION：section/profile/template lineage。

Trace locator 不是 Proof。Trace 查询前先完成全 Candidate/run validation；源码 hash/span mutation使查询失败，而不是返回陈旧 locator。

Gap Trace 不发明 `scope:*` 或 `requirement:*`：`GAP` hop 的id来自RepositoryKnowledge `canonicalGapId`，`SEARCHED_SCOPE` hop 的id来自VerifiedSourceInventory admitted source-request `artifactId`；missing/closure requirement是该canonical Gap的已持久化字段，不另造身份。

validation 状态按`AnalysisResult`唯一选择：`COMPLETE→VALID_COMPLETE`；`COMPLETED_WITH_GAPS→VALID_COMPLETE_WITH_GAPS`；`INCOMPLETE_SCOPE→VALID_INCOMPLETE_SCOPE`；`INCOMPLETE_COVERAGE→VALID_INCOMPLETE_COVERAGE`。前四种都要求其声明范围内的schema/root/cardinality/source/Trace/ledger重验一致；任一内部一致性检查失败为`INVALID`。两种`VALID_INCOMPLETE_*`只表示`FINISHED`诊断归档自洽，不表示repository coverage完成，禁止Selection，也不能改名为前两种VALID绕过门禁。

### 8.5 Candidate rounds、最小执行状态与 archive

active v0 的执行状态闭集只有：

~~~text
QUEUED -> RUNNING -> FINISHED
                  \-> FAILED
~~~

`QUEUED/RUNNING/FAILED`的`analysisResult`必须为null。`FINISHED`表示请求的analysis step区间已经完整发布：完整`start`或`executeStep.finalAnalysisStepKey=nine-section-document`必须且只能绑定`COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE`之一，并要求M4 publication、NineSectionDocument receipt和root manifest都fresh-reopen通过；以更早semantic key结束的显式analysis step execution在其最后一个请求analysis step publication fresh-reopen通过后也可为`FINISHED`，但`analysisResult=null`且不存在root run manifest。`execution-status.json`只是最小观察记录，不进入Candidate、analysis step root或run-manifest identity：接受`start/executeStep`后写`QUEUED`，当前进程single worker开始时写`RUNNING`，满足上述对应完成门后写`FINISHED`。任一fatal、完整性错误、Provider started后失败或进程中断都不声明成功；当前run为`FAILED`或由调用者按失败处理。

active v0没有跨进程队列认领、worker generation、event log、slot journal、终态修补或同run自动继续。进程重启不接管旧`QUEUED/RUNNING` run。已经完整安装的module/analysis step publications保持immutable，可由`inspect/artifact`读取，并可由新`executeStep`在启动前fresh-validate后作为显式上游；只有具备完整NineSectionDocument Candidate/root manifest的分析链才可调用run级`validate`。部分安装目录、缺receipt的payload或只有root manifest没有`FINISHED`状态都不能冒充成功。FlowInterpretation Provider调用一旦开始却未得到可验证response，当前run直接失败，不自动重试或切换Provider。

ReaderCandidateRound最多两份：

- ROUND_1初始Candidate，request要求`parentCandidateRef=null/approvedFindingRefs=[]`；
- ROUND_2只能针对ROUND_1已归档且APPROVED_FOR_ROUND_2的content-addressed finding refs；request保存同series parent Candidate完整ID/SHA并冻结同一source/controls/Facts/Flows/Capsules/registries；
- 不存在 Round 3；Round 2 不能作为 parent；
- unaffected Flow逐字节复用parent canonical rounds/receipts；
- fatal/addendum-required finding在没有可信外部attestation workflow时fail closed。

Candidate目录安装后不可变；后续validation/review写外部append-only目录。staging与destination同文件系统；写满、force、逐SHA校验后ATOMIC_MOVE，不支持时停止。

### 8.6 显式分析步骤新执行与 identity

`start(AnalysisRunRequest)`每次创建新的随机`runId`并按VerifiedSourceInventory→08完整执行。`executeStep(AnalysisStepExecutionRequest)`同样每次创建新的随机`runId`；它不重新激活旧run，也不继承旧worker、队列、Provider session或模型调用状态。

`AnalysisStepExecutionRequest`必须列出closed registry中位于`targetAnalysisStepKey`之前的完整、连续、有序`AnalysisStepPublicationReference`前缀。core在创建新run前逐项fresh-reopen并验证semantic key/order、descriptors、receipt/root、source registration、AnalysisRunRequest及toolchain/profile/schema/prompt/policy/budget controls来自同一frozen basis。producer runIds可以不同，但lineage必须连续且业务basis逐字相同；缺项、重复、越级、root不符或control漂移都以稳定code拒绝。验证通过后只执行`targetAnalysisStepKey`到`finalAnalysisStepKey`的closed registry连续区间，已验证上游不重扫、不重算、不复制成内存旁路。

固定示例：原RepositoryKnowledge执行失败后，调用者可给出validated前六个分析步骤publications并请求`targetAnalysisStepKey=repository-knowledge`。新run直接fresh-reopen ProvenCodeFacts、BusinessFlows、FlowInterpretation所需bytes并执行RepositoryKnowledge；六个上游分析步骤不运行，Provider调用为0。若`finalAnalysisStepKey=nine-section-document`，同一新run再用新发布RepositoryKnowledge执行NineSectionDocument。这是核心业务artifact复用，不是同run恢复。

capability manifest固定为`RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`；public Interface、CLI和HTTP都没有resume方法、route或伪扩展点。未来同run自动恢复仅见[运行时恢复 TODO](../supplements/runtime-recovery-todo.md)，不属于active v0实现、测试或验收范围。

### 8.7 预算、安全与 failure codes

预算覆盖reader items、sections=9、document bytes、trace records/hops、archive files/bytes、directory entries和validation reopen bytes。run-centric观察另固定`maxInspectedAnalysisSteps=8`、`maxInspectionModules`、`maxArtifactBytes`、`maxTraceHops`、`maxConcurrentRequests`、`maxQueuedRequests`和HTTP body bytes；超限返回stable error，artifact bytes不得截断。普通文件先NOFOLLOW/size gate；目录在limit+1前停止。

secret、prompt、raw response/reasoning、绝对路径不进入正文或任何View。HTTP只绑定loopback并校验run-scoped bearer capability、ID-only requests、body/queue/idempotency limits；artifact/trace先校验run ownership与artifact membership，拒绝Path、glob、目录枚举、symlink和跨run identity。Adapter不能绕过core；观察方法没有Provider/source-execution capability。

稳定 code：

ANALYSIS_RUN_REQUEST_UNSUPPORTED、ANALYSIS_STEP_EXECUTION_REQUEST_INVALID、ANALYSIS_STEP_EXECUTION_UPSTREAM_INVALID、PROCESS_INTERRUPTED、NINE_SECTION_DOCUMENT_INPUT_INVALID、NINE_SECTION_INVALID、SECTION_OWNER_INVALID、READER_ITEM_INVALID、READER_ATOM_LOSS、REPOSITORY_COVERAGE_LEDGER_INVALID、REGISTRY_MEANING_LINEAGE_BROKEN、READER_INFORMATION_DENSITY_FAILED、BODY_CLEANLINESS_FAILED、DOCUMENT_HASH_MISMATCH、TRACE_CLOSURE_BROKEN、ANALYSIS_STEP_PUBLICATION_MISMATCH、RUN_MANIFEST_INVALID、RUN_LIFECYCLE_TRANSITION_INVALID、RUN_RESULT_LIFECYCLE_MISMATCH、RUN_WORKER_ALREADY_ACTIVE、PROVIDER_FAILURE_AFTER_START、RUN_NOT_FOUND、ARTIFACT_QUERY_INVALID、ARTIFACT_NOT_FOUND、ARTIFACT_IDENTITY_MISMATCH、ARTIFACT_ACCESS_DENIED、ARTIFACT_CONTENT_NOT_PUBLIC、ARTIFACT_RESPONSE_BUDGET_EXCEEDED、RENDER_NOT_AVAILABLE、RUN_NOT_VALIDATABLE、OBSERVATION_BUDGET_EXCEEDED、MODULE_PUBLICATION_COLLISION、MODULE_PUBLICATION_INVALID、ARCHIVE_MANIFEST_INVALID、ARCHIVE_ARTIFACT_SET_INVALID、ARCHIVE_IDENTITY_COLLISION、ATOMIC_MOVE_UNSUPPORTED、CANDIDATE_SIZE_LIMIT_EXCEEDED、SERIES_LINEAGE_INVALID、ROUND_2_SLOT_ALREADY_CONSUMED、MODEL_TASK_NOT_RUN_UPSTREAM_INVALID，以及`ValidationCheckV1`的九个`VALIDATION_*`闭集code。不得使用未声明的`REPOSITORY_SCOPE_NOT_COMPLETE`或任意fixture-only code。

### 8.8 测试 seam 与验收

- nine-section-plan恰九章；少/多/乱序/改名均失败。
- renderer只拿plan的隔离测试仍能渲染；尝试读source/model artifact不可达。
- 每个atom/meaning/Gap有唯一disposition；删除/重 owner fatal。
- 相同plan不同root/order重复render，Markdown bytes/SHA相同。
- 八类Trace exact refs有omission/substitution/source mutation测试，EMPTY_SECTION额外覆盖section/profile/template/reason。
- exact analysis step roots/run manifest/candidate identity coherent tamper仍失败，不能靠一起改重复字段自证。
- final install各partial-install边界、fresh-reopen、completed tamper readmission均不调用Provider；进程中断的旧run不自动补成完成。
- 0 Flow/0 Capsule生成诚实九章、0 rounds、Gap可见。
- ReaderCandidateRound 2 exact parent/finding/frozen-basis/unaffected-round reuse；第三份不可表示。
- 至少双Flow的RepositoryKnowledge只生成一份plan/document；跨Flowrelation/metric均有ReaderItem/Trace。第二plan/document、per-Flow Markdown fragment或文本拼接输入必须失败。
- RepositoryCoverageLedger mutation覆盖普通carrier ref冒充typed nested draft ref、carrier/nested ID/schema任一漂移、前六个分析步骤 ref或RepositoryKnowledge publication/root漂移、RepositoryKnowledge任一registry proposal/proposal decision/Flow admission/meaning/lineage/fallback/knowledge relation/metric/conflict/owner ID遗漏或合并、draft冒充final、ledger反向引用plan、缺/重叠shard、omitted entry/Flow/proposal/knowledge/owner、single Flow PASS；均不得写COMPLETE。改变shard size或程序遍历顺序后final ledger/plan/document bytes分别稳定。
- 同一frozen basis fixture对`RepositoryAnalysisAgent`七方法做Java/CLI/loopback HTTP contract conformance；三种Adapter的状态、error、artifact SHA/bytes、render reference、validation和Trace逐字节/逐字段同义。另有`executeStep(targetAnalysisStepKey=repository-knowledge)`直接消费validated前六个分析步骤、只运行RepositoryKnowledge且Provider调用0的正反例。
- `artifact`覆盖`ANALYSIS_STEP_MODULE/VALIDATION_MODULE/ANALYSIS_STEP_PUBLICATION/RUN_MANIFEST`四种`ArtifactLocation`、analysis-step-only receipt membership、wrong run/location/artifact、Path/glob注入、跨run访问和size budget；`PATH_FREE_COMPLETE_UTF8` document/plan正例必须返回完整bytes，含`SourceLocatorV1`/`SourceExcerptV1`的raw Trace/source artifact在`COMPLETE_UTF8`下必须稳定`ARTIFACT_CONTENT_NOT_PUBLIC`；`trace`覆盖required maxHops、expected Candidate、完整all-or-error view和path-free source projection；inspect/render/artifact/validate/trace全部断言Provider调用0。
- NineSectionDocument M1–M4与run-validator都覆盖payload-before-receipt partial install、receipt-before-move partial install、identical reinstall、different collision、fresh-process reopen、额外/缺失/symlink文件、JSON/JSONL identity和bytes defensive copy；M1额外覆盖final-ledger→plan两payload同一原子receipt且不增加public output。fixture使用总体设计§13.3.1的真实临时filesystem bootstrap，不得用mutable in-memory store绕开。
- minimal execution覆盖QUEUED早于worker、同进程single worker、RUNNING→FINISHED/FAILED、NineSectionDocument完成时四种result恰一、应用发现至仓库知识的分析步骤区间完成时FINISHED但result/root manifest均为空、FAILED无result、HTTP 202和CLI先打印runId再前台驱动；进程中断不继续旧run。FlowInterpretation业务测试覆盖R1 GAP/FAILED→同Flow R2 `NOT_RUN_UPSTREAM_FAILED`（task不丢、R2零Provider调用、same-Flow upstream task），started Provider失败直接FAILED且不重试/切换。

验收必须覆盖typed RepositoryKnowledge draft reference→前六个分析步骤+RepositoryKnowledge无环preparation→M1 final ledger→plan的单向identity、RepositoryKnowledge全部decision/lineage/knowledge ownership ID sets逐字段守恒、九章少/多/乱序/改名、至少双Flow→唯一RepositoryKnowledge/plan/document、plan-only renderer capability isolation、八类typed Trace（含EMPTY_SECTION）、0Flow Gap文档、coverage ledger/shard/accounting mutations、Candidate/NineSectionDocument identity cycle、archive partial-install points、tamper、显式upstream validation、四种ArtifactLocation、Trace maxHops/Candidate check、Round2 lineage与Round3 rejection。只有八文件set原子安装、module-only final ledger不增加第九项、fresh validator证明同一ledger/1:1:1 cardinality/acyclic roots全闭合、相同plan产生相同Markdown bytes且所有反例fail closed，NineSectionDocument才算可交付。

### 8.9 已冻结裁决：实现者不得自由推断

- NineSectionProfile 的九个标题、顺序和恰一次规则固定；空章用 typed EMPTY_SECTION，不能增删改名或 renderer 临时写 filler。
- RepositoryKnowledge `RepositoryCoverageLedgerDraftV2`只可通过`RepositoryCoverageLedgerDraftReferenceV1`进入M1；M1必须分别验证前六个分析步骤 refs、draft内无环RepositoryKnowledge preparation root与已完成RepositoryKnowledge publication ref，并把全部RepositoryKnowledge decision/lineage/knowledge ownership ID sets原样保留到final `RepositoryCoverageLedgerV3`。final ledger不引用plan；plan与后续M3/M4/analysis step/run/validation只引用该final ref。它是M1 module-only业务coverage资产，不增加八项reader-visible output。
- planner拥有section owner/disposition；M2 renderer唯一输入是M1 verified plan-draft payload，外部rerender唯一输入是public `nine-section-plan.json`；两者都没有source/model/registry能力且输出bytes必须相同。
- Trace 只做 typed lineage 查询，不代替 Proof；查询前必须完成 Candidate/run validation。
- Candidate 安装后不可变且始终未发布；review/validation只能写外部append-only区域，Selection是后续流程。
- Candidate只引用前七个分析步骤 upstream roots；NineSectionDocument root只进入run-manifest。每run只有一份RepositoryKnowledge、NineSectionPlan和document.md；禁止per-Flow Markdown/fragment拼接。
- completion只由COMPLETE_CAPTURE的RepositoryCoverageLedger闭合触发；单Flow PASS、缺/重叠shard或任何unsupported/failed/omitted item无处置时不得COMPLETE。
- ReaderCandidateRound 最多 2；Round 2 只处理已批准 finding并冻结 basis，不存在 Round 3。
- `executeStep`先验证连续upstream publications和同一frozen basis，再创建新run并仅执行target到final analysis step；不得覆盖旧run、重扫有效上游或继承Provider状态。active v0不提供同run自动继续。
- 唯一公共Interface是run-centric `RepositoryAnalysisAgent.start/executeStep/inspect/artifact/render/validate/trace`；Java/CLI/loopback HTTP只是同义Adapter。artifact必须使用run+identity与可选四variant `ArtifactLocation`且不得接受Path；trace必须使用`TraceQuery`的required maxHops并返回完整path-free `TraceView`，观察方法不得调用Provider。
- template engine、Markdown escaping 和 archive 内部类可选择；九章语义、plan-only boundary、Trace、identity、lifecycle、failure 策略不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.document`、runtime、validation和adapter package目前只有语义骨架；当前没有九章、Trace、Candidate、run manifest或公共运行Interface。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package、Maven身份和JDK 17 Toolchain已就位；通用`SOURCE_ANALYSIS/v1`头门禁不解析plan、Candidate或run artifact。 |
| **本步骤生产能力尚未实现** | M1–M4、final coverage ledger、planner、plan-only renderer、typed Trace、Candidate/archive、IndependentRunValidator和八项正式输出均不存在。当前没有SourceAnalysis `document.md`。 |
| **runtime/adapters尚未实现** | `RepositoryAnalysisAgent`七方法、最小single-process worker、四值AnalysisResult、ArtifactLocation、CLI、loopback HTTP及观察/validation语义均不存在。 |
| **历史证据，不是当前能力** | 已删除的pre-reset stage03/stage04/archive-v2曾验证有限typed plan/renderer/Trace、immutable Candidate、atomic archive和零Provider validation。它们仅保留为测试意图，不是当前final-only archive或兼容wire。 |
| **样例边界** | 历史DepotHead 0 Flow/0 Capsule baseline要求未来文档把范围/Gap保持可见；当前没有该Markdown或Candidate。 |
| **下一实现门** | 按本章从唯一RepositoryKnowledge生成唯一plan/document，闭合九章、每ReaderItem typed Trace、52项run、fresh validation和Java/CLI/HTTP同义性。 |

目标NineSectionDocument吸收历史验证经验，但必须按新wire重新实现并把可观察性前移到每一分析步骤；它不实现同run自动恢复。
