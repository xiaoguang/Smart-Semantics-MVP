# 08 构建九章文档并归档整个运行

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

Stage 07 已有**一份覆盖全部 Flow 的结构化 RepositoryKnowledge**，但业务读者需要固定、可读的仓库级九章文档，审计者需要从每句话回到 Proof 和源码，运维者需要看到运行进度并从中断点恢复。

Stage08同时完成九章plan、plan-only Markdown渲染、typed Trace、不可变Candidate、whole-run manifest、RepositoryCoverageLedger、独立validation和resume。每个analysis run严格只有**一个RepositoryKnowledge → 一份NineSectionPlan → 一份document.md**；禁止一Flow一Markdown，也禁止先生成Flow片段Markdown再文本拼接。它不把前七阶段隐藏在最终archive背后。

## 2. 具体输入与 DepotHead 例子

输入是前七阶段immutable artifacts、Stage06唯一RepositoryInterpretationRegistry、Stage07唯一RepositoryKnowledge及其registry meaning lineage、从Stage01–07累计的RepositoryCoverageLedger草稿、NineSectionProfile、section ownership/sentence template profiles、Candidate series/round request、archive/trace/validation policies和预算。DepotHead只是完整knowledge中的一个Flow/Gaps投影，不是文档范围。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用一条连贯 ReaderItem/Trace 展示目标归档接力；当前 fixed slice 的正文只能呈现范围与 Gap，技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

当前 DepotHead slice 有 Gap、0 Flow、0 Capsule、0 model round。Stage 08 仍可规划一份诚实九章：

- 文档说明：固定 commit、八文件 BOUNDED_PATH_SET、能力边界；
- 业务目标/活动等章节：仅写已证明内容或 typed empty-section说明；
- 待确认事项：写明数据流/Fact/Flow closure 缺口；
- 不得写“批量审核或反审核流程已分析完成”。

目标 plan item 示例：

~~~json
{
  "readerItemKind": "GAP_QUESTION",
  "sectionKey": "PENDING_CONFIRMATION",
  "templateKey": "gap-question-v1",
  "gapId": "gap:<hex64>",
  "slots": {
    "subject": "DepotHead status persistence path",
    "missingRequirement": "closed cross-layer data-flow proof"
  }
}
~~~

这是目标 plan shape，不是当前生成的 Markdown。

## 3. 程序怎样工作

1. 重验 Stage01–07 stage receipts、artifact roots、全仓entry/flow/interpretation/knowledge coverage和run control hashes；单Flow PASS不能通过。
2. 要求恰一个repositoryKnowledgeId，依据固定NineSectionProfile建恰好九个仓库级SectionPlan。
3. 对RepositoryKnowledge中的每个Fact atom、admitted meaning、technical fallback、relation、metric和Gap选择唯一section owner与typed ReaderItem；admitted meaning的ReaderItem必须复制typed `registryProposalIds/provisionalKeys/interpretationProposalIds/selectedKeys`引用，不能只留显示词。
4. 重算 atom/meaning/Gap dispositions：正文、技术依据、Gap 或 reasoned exclusion，禁止 silent loss。
5. 持久化 nine-section-plan.json。
6. 在能力隔离的 renderer 中只打开 nine-section-plan.json，按 frozen templates 输出 UTF-8/LF document.md。
7. 编译typed Trace：业务解释走 `ReaderItem → knowledge → meaning → selectedKey → interpretationProposal → provisionalKey → registryProposal → Capsule basis → Fact/Proof → Evidence/source → snapshot`；fallback/Fact/Gap走各自既定typed分支。
8. 组装 Candidate/series/ReaderCandidateRound lineage、validation baseline和`upstreamStageRoots[7]`；Candidate不引用尚未计算的Stage08 root。
9. 写满staging payload artifacts，计算Stage08 root/receipt；再组装含`stageRoots[8]`和RepositoryCoverageLedger ID/SHA的run-manifest，验证无identity cycle后原子安装Stage08/Candidate。
10. 只有`COMPLETE_CAPTURE + repositoryCompletionEligible=true + coverage ledger closed=true`时才在run-events.jsonl追加completion。bounded scope归档为`INCOMPLETE_SCOPE`，完整capture但coverage未闭合归档为`INCOMPLETE_COVERAGE`；两者都只写diagnostic archive event，不得冒充terminal completion。后续validations写run外部追加目录，不修改Candidate。
11. validate 可在 fresh process 重开 source 和所有阶段 artifacts；resume 从最后一个 valid stage receipt 继续。

## 4. 生成的可观察产物

Stage 08 目录：

| 文件 | 唯一职责 |
| --- | --- |
| nine-section-plan.json | renderer 唯一输入；九章 typed reader AST 与 dispositions |
| document.md | 唯一 reader-facing Markdown |
| trace.jsonl | 每个 ReaderItem 的 self-describing typed lineage |
| candidate.json | Candidate/series/round/run/stage roots 和 UNPUBLISHED status |
| validation-baseline.json | 安装前 deterministic checks 的不可变 baseline |
| archive-manifest.json | Stage 08 exact artifact set/size/SHA/root |
| stage-receipt.json | Stage 08 controls、artifacts、document/trace/candidate IDs |
| run-manifest.json | 八个 stage roots、run request/control hashes、RepositoryCoverageLedger ID/SHA、唯一knowledge/plan/document IDs和terminal state |

前七阶段目录仍位于 runs/<run-id>/stages/ 并保持一等资产。run-manifest 引用它们；不把它们复制成一份难以区分输入/结果的大 JSON。

为避免自引用，Stage08 `stageArtifactRoot`只覆盖先固定的五个semantic payload：`nine-section-plan.json`、`document.md`、`trace.jsonl`、`candidate.json`、`validation-baseline.json`。`archive-manifest.json`绑定这五项/root；`stage-receipt.json`绑定payload root与archive-manifest SHA但不列自身；`run-manifest.json`再绑定Stage01–08 roots、receipt SHA和coverage ledger但不列自身。最后才计算stage外的`modules/04-archive/stage08-publication.json`，它可列齐八个public files。任何实现把receipt/run-manifest自身放进其所声明的root都会形成cycle并fatal。

现有 archive-v2 的 proven-facts.json、proof-pack.json、flow-slices.json、evidence-capsules.json、registry-bundle.json、model-rounds.jsonl、repository-business-model.json 等 final archive 技术合同仍是有效的可重验 preimage 经验；目标实现应改为引用或验证对应 stage roots，避免“只到最后才第一次落盘”。

目标出口必须同时包含表中的八个文件，`nine-section-plan.json`必须有九个非空SectionPlan envelope，`document.md`必须有九个标题和final LF，Candidate必须是`UNPUBLISHED_CANDIDATE`。0Flow时业务内容可以是typed EMPTY_SECTION/GAP_QUESTION，但plan、Markdown、Trace、manifest和receipt不能省略或变成空文件。多Flow时仍只有一份仓库plan/document；所有Flow知识由planner在typed ReaderItems层组合，renderer绝不接收或拼接Markdown fragments。

### 4.1 人类 walkthrough：模块用什么文件接力

~~~jsonl
{"module":"NineSectionPlanner","artifact":"modules/01-planner/nine-section-plan-draft.json","takesFrom":["Stage07Reference","NineSectionProfile/templates"],"says":{"sections":9,"activityReaderItem":"reader-item:depothead-batch-audit","registryProposalId":"registry-proposal:depothead-batch-audit","provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretationProposalId":"interpretation-proposal:depothead-batch-audit","meaningId":"meaning:depothead-batch-audit","pendingReaderItem":"reader-item:runtime-status-policy-gap"}}
{"module":"PlanOnlyRenderer","artifact":"modules/02-renderer/rendered-document.json","takesFrom":["nine-section-plan-draft.json only"],"says":{"documentPath":"document.md","titles":9,"contains":"批量审核或反审核（带技术锚点）","qualifies":"运行时状态政策待确认"}}
{"module":"TypedTraceCompiler","artifact":"modules/03-trace/trace-set.json","takesFrom":["plan","knowledge","Stage06 registry/proposals","Proof/Evidence/source"],"says":{"readerItemKey":"reader-item:depothead-batch-audit","chain":"meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→Capsule basis→Proof→DepotHeadMapper.xml:472-473"}}
{"module":"CandidateRunArchiver","artifact":"modules/04-archive/stage08-publication.json","takesFrom":["one repository plan","one document","trace","Stage01–07 roots","coverage ledger"],"says":{"candidateId":"candidate:depothead-round1","status":"UNPUBLISHED_CANDIDATE","upstreamStageRoots":7,"runManifestStageRoots":8,"repositoryDocuments":1,"publicFiles":8}}
{"module":"IndependentRunValidator","artifact":"validations/validation-depothead-round1/validation-receipt.json","takesFrom":["immutable CandidateReference","fresh run/source readers"],"says":{"validationStatus":"VALID_INCOMPLETE_SCOPE","documentReRendered":true,"traceClosed":true,"repositoryCoverageClosed":false,"providerCalls":0}}
{"module":"RunResumer","artifact":"resume-decisions/resume-depothead-round1.json","takesFrom":["run request","module/stage receipts","events/coverage ledger"],"says":{"decision":"NEW_RUN_REQUIRED","reasonCode":"REPOSITORY_SCOPE_NOT_COMPLETE","lastValidStage":8,"repositoryCoverageClosed":false,"providerReplayAllowed":false}}
~~~

ReaderItem保留registry proposal/provisional key/interpretation proposal/selected key/meaning/atom/gap IDs；Trace逐跳引用同一IDs。M2只有plan能力，M4最后绑定document/trace/stage roots，M5/M6只在外部追加验证或恢复决定，不改Candidate。

## 5. 下游怎样消费而不返工

- 业务审阅者只读 document.md。
- 解释文档结构的工具读 nine-section-plan.json。
- Trace 查询读 trace.jsonl 和引用的 stage artifacts；完整 validation 后才返回 source spans。
- Selection 只接收 immutable CandidateReference，不接收活动 run 目录。
- resume 读取 run request/events/stage receipts，不调用已完成 Stage 06 Provider。
- renderer 永远不打开源码、Proof、model rounds、registry 或任意 Path；独立 validator可以重开。

### 下游前置条件与后置保证

| 审阅/Trace/Selection/resume 开始前必须成立 | Stage 08 成功后保证 |
| --- | --- |
| Stage 01–07 roots、run controls、Stage 08 artifact set/root 与 Candidate identity 全部重验通过 | plan 恰九章且所有 semantic items 有 disposition；Markdown 可仅由 plan bytes 重渲染 |
| document SHA、Trace closure、archive/run manifests 和 `UNPUBLISHED_CANDIDATE` lineage 一致 | Trace 可从 ReaderItem 闭合回 knowledge/Proof/source；前七 stage roots 保持一等引用 |
| 独立 validation 成功后才允许 Trace 返回 source span，Selection 只收 immutable CandidateReference | 审阅无需模型；Selection 不接活动 run；resume 从最后 valid stage 且不重放 started round |
| RepositoryCoverageLedger对complete source/site/entry/graph/fact/outcome/flow/interpretation/knowledge/reader owner全部闭合；knowledge/plan/document cardinality为1/1/1 | 读者看到完整仓库的一份九章；unsupported/failed/omitted均在Gap/排除accounting中，单Flow PASS无权完成run |

任一下游不得把未 validation 的 locator 当可信引用，也不得修改 Candidate 以修复 run event、finding 或 selection 状态。

## 6. 终态成功、诊断归档、fatal 与恢复

- **终态成功**：仅限`COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、完整RepositoryCoverageLedger `closed=true`；唯一RepositoryKnowledge→唯一九章plan→唯一document cardinality成立，所有semantic items有disposition/owner，document可重渲染，Trace/roots闭合，Candidate/run manifest原子安装。无Gap映射`COMPLETE / VALID_COMPLETE`。
- **终态带 Gap 成功**：仍必须满足上述完整capture和closed ledger。0 Flow、无term、静态未知或capability/profile Gap只有在**完整分母中的每项仍有唯一typed disposition**时才映射`COMPLETED_WITH_GAPS / VALID_COMPLETE_WITH_GAPS`；不能用profile缩小source/entry/Flow分母。文档可诚实展示Gap/EMPTY_SECTION并可进入Selection。
- **诊断归档（不是run成功）**：`BOUNDED_PATH_SET`映射`INCOMPLETE_SCOPE`；`COMPLETE_CAPTURE`但ledger以非空closureReason、受影响ID集和恢复cursor合法保持`closed=false`时映射`INCOMPLETE_COVERAGE`。两者可原子保存`repositoryCompletionEligible=false`的不可变Candidate并由validator报告`VALID_INCOMPLETE_SCOPE`，但都非终态、不可Selection、不可写completion event。
- **fatal / invalid**：ledger缺失、不可解析、自相矛盾、分母未知或遗漏未显式记账，或出现单Flow冒充完成、多份knowledge/plan/document、per-Flow Markdown/fragment、章节少/多/乱序、item type/slot不匹配、atom/meaning/Gap丢失、hash/root/Trace/source replay断裂、archive collision、原子移动不可用、安全失败或resume controls不一致。单纯`closed=false`或scope非COMPLETE不是fatal；把它们伪装成COMPLETE，或无法证明已归档子集自身完整，才fatal。
- **恢复**：重验最后valid stage和ledger/shard receipts；只有终态完成条件本已成立且Stage08已安装但completion event缺失时，完整validation通过后追加RECOVERED_COMPLETION。诊断归档只从其missing-ID/recovery cursor继续，不追加完成事件。已started但未完成的模型round不重放；缺失deterministic shard从最后未完成cursor继续，已完成Flow/knowledge artifacts保留。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 九章 owner/plan | 是 | 否 |
| Markdown 句式和样式 | 程序按冻结模板 | 否 |
| Trace、identity、archive、resume | 是 | 否 |
| 重新解释 Flow | 否，消费 Stage 07 | 否 |
| 写正文 | renderer，不是模型 | 禁止 |

Stage 08 没有 Provider Interface，运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块顺序固定为 `NineSectionPlanner` → `PlanOnlyRenderer` → `TypedTraceCompiler` → `CandidateRunArchiver` → `IndependentRunValidator`。`RunResumer` 是独立入口，只读取 run request/events/receipts 并选择下一 stage/module，不参与 Candidate identity。模块间只交换已安装 artifacts。

#### M1 NineSectionPlanner

- **解决的问题**：把每个 owned Fact atom、meaning、fallback、Gap 唯一分配到固定九章和 typed ReaderItem，形成 renderer 的全部语义输入。
- **精确上游输入及前置**：valid且恰一个Stage07 RepositoryKnowledge及其`repositoryInterpretationRegistryId/registryLineage`、全部flow/meaning/Gaps/relations/metrics/ownership/conflicts/accounting、显式scope/eligible/closed/closureReason状态的RepositoryCoverageLedger草稿、NineSectionProfile、owner/template profiles、budget；Stage01–07 roots/control hashes已重验。bounded scope只允许生成INCOMPLETE_SCOPE诊断候选；完整capture但closed=false只允许生成INCOMPLETE_COVERAGE诊断候选。
- **确定性顺序 / LLM**：验证单一knowledge及完整semantic denominator → 建九个repository-level SectionPlan → 按semanticItemId/owner rule选择section → 对ADMITTED_TERM exact-join`RegistryMeaningLineage`并把`normalizedLabel/normalizedPurpose`逐字节写入typed slots → 生成其余typed slots/template key → disposition/accounting → plan identity；0 LLM。
- **目标输出与 DepotHead 示例**：`NineSectionPlan{repositoryKnowledgeId,repositoryInterpretationRegistryId,sections[9],readerItems,dispositions,coverage}`；当前例在待确认事项放data-flow Gap；未来activity item引用registry proposal/provisional/interpretation proposal/selected key/meaning/fact IDs。
- **必须保持的不变量**：每run恰一plan、九章恰一次/固定顺序标题；全部Flow知识在同一plan中，每semantic item恰一disposition/owner；每ADMITTED_TERM ReaderItem保存完整五段registry→meaning lineage，`businessTerm/businessPurpose`分别逐字节等于lineage的`normalizedLabel/normalizedPurpose`；slots符合template schema；无silent loss或per-Flow plan。
- **Gap / fatal / 恢复**：业务未知形成GAP_QUESTION/EMPTY_SECTION；缺/双owner、章错序、slot/type mismatch、atom loss fatal；恢复纯重算相同knowledge/profile。
- **给下游的后置保证**：M2只需一个exact plan文件即可完整渲染；M3可从每个ReaderItem回到typed knowledge refs。
- **明确非目标**：不写Markdown、不重读source/model、不重新merge/admit、不新增第十章。
- **公共测试 seam 与验收**：`plan(knowledge, gaps, profile, templates)`覆盖完整五段lineage/任一hop删除、至少双Flow/一个RepositoryKnowledge/一个plan、跨Flowrelation或metric、0Flow、第二knowledge rejection、single-flow omission、owner mutation、template slot、item order；golden独立手写。
- **Luna/xhigh 测试指南**：创建 `Stage08NineSectionPlannerTest`，冻结Stage07 files、profile/templates与手写九章golden于 `src/test/resources/target/stage08/nine-section-planner/`。逐RED：registry lineage→hop deletion→九章正向→0Flow Gap/EMPTY→owner/atom loss→slot/type→少多乱改章→order determinism；首RED因planner/schema缺失。只fakeartifact reader，planner/accounting/canonical不可mock。命令：`mvn -Dtest=Stage08NineSectionPlannerTest test`；无网络/模型。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage08/planner/`，实现 public `NineSectionPlanner/NineSectionPlan` 与 `stage08-nine-section-plan-draft-v2`；只读Stage07+frozen profiles，registryLineage→sections→owner→typed item→disposition/accounting。逐RED GREEN；不得增章/补owner/丢lineage/自由句子。九章或跨stage改变MUST STOP并由Sol/ultra交用户，完成审计。

#### M2 PlanOnlyRenderer

- **解决的问题**：把已决定的Reader AST机械渲染为稳定、可读Markdown，同时证明renderer没有分析能力。
- **精确上游输入及前置**：M1 canonical `nine-section-plan.json` artifact和内置 frozen template/escaping version；plan schema/identity/九章 validation通过，进程无其他capability。
- **确定性顺序 / LLM**：exact parse → 依section/item语义顺序选择template → typed slot escaping → UTF-8/LF/final-LF encode → document SHA；0 LLM。
- **目标输出与 DepotHead 示例**：`RenderedDocument{nineSectionPlanId,rendererProfileRef,documentBytesSha256,byteCount}`加`document.md`；当前例含九标题、scope和DepotHead proof Gap，不写成功Flow。
- **必须保持的不变量**：每run renderer恰调用一次且唯一输入是单一仓库plan；相同plan bytes输出相同document bytes；标题/顺序与plan一致；无source/model/path/fragment访问。
- **Gap / fatal / 恢复**：plan里的typed Gap正常渲染；unknown schema/template/slot、body cleanliness、size/hash drift fatal；恢复可从相同plan无限重渲染但不修改已安装输出。
- **给下游的后置保证**：M4得到可重现document SHA，审阅者不需模型；M5可在隔离环境重渲染比较。
- **明确非目标**：不选事实、owner/措辞语义，不填空章，不读Proof/registry/source，不接受per-Flow plan或Markdown fragment，不做文本拼接。
- **公共测试 seam 与验收**：`render(NineSectionPlanArtifact exactPlan)`在能力隔离进程运行，fixture层只挂载单文件；覆盖multi-flow single-document golden、第二plan/fragment拒绝、九章golden、escaping/LF、missing/extra field、source access trap和different-root determinism。
- **Luna/xhigh 测试指南**：创建 `Stage08PlanOnlyRendererTest`，exact plan与独立Markdown golden放 `src/test/resources/target/stage08/plan-renderer/`。逐RED：九章bytes、0Flow内容、escaping/LF/finalLF、missing/extra/schema、source/model access trap、different-root determinism；首RED因renderer缺失。只可mock capability trap/plan file read，template/canonical输出不可mock。命令：`mvn -Dtest=Stage08PlanOnlyRendererTest test`；禁网络/Provider/source mount。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage08/renderer/`，实现 public `PlanOnlyRenderer/RenderedDocument` 与 `stage08-rendered-document-v1`；唯一输入M1 plan artifact，exact parse→template→escape→UTF8/LF/hash。逐RED GREEN且隔离test通过；禁止读其他artifact/补语义。需改plan字段/九章MUST STOP交Sol/ultra/用户，更新审计。

#### M3 TypedTraceCompiler

- **解决的问题**：为每个ReaderItem建立无断链、typed、可验证的ReaderItem→source lineage，而不把Trace当Proof。
- **精确上游输入及前置**：M1唯一仓库plan、Stage07唯一knowledge/registryLineage、Stage06 registry/R0/R1/R2 artifacts、Stage04 Proof、Stage03 Evidence、Stage01 complete snapshot/inventory、trace profile/budget；所有roots/IDs和ledger denominator闭合。
- **确定性顺序 / LLM**：按readerItemKey → 根据kind选择trace schema → ADMITTED_TERM先连接knowledge/`registryLineageId`并核对plan slots与lineage规范值逐字节相同，再连接meaning/selectedKey/interpretationProposal/provisionalKey/registryProposal/Capsule basis，其他kind连接fallback/Fact/Gap → Proof/Evidence/source → hop/type/reference/accounting validation → trace root；0 LLM。
- **目标输出与 DepotHead 示例**：`TraceSet{records,readerItemCoverage,traceRoot}`；status ReaderItem链为meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→Capsule basis→Proof→XML span；当前Gap item链到missing requirement/searched scope。
- **必须保持的不变量**：九章中每个ReaderItem（含EMPTY_SECTION）恰一trace record；ADMITTED_TERM五段lineage不可跳跃、替换或反向；hop方向遵循identity DAG；source locator需validation后返回；Trace不生成缺失Proof；所有Flow知识回到同一`repositoryKnowledgeId`/`repositoryInterpretationRegistryId`。
- **Gap / fatal / 恢复**：GAP/EMPTY有typed lineage；missing/substituted hop、source drift、orphan/duplicate record、budget截断 fatal；恢复从相同plan/upstream重编。
- **给下游的后置保证**：M4/M5得到self-describing trace root；审计查询可验证后逐跳返回，不需猜record kind。
- **明确非目标**：不改plan/document/Fact，不把locator存在当事实证明，不调用模型。
- **公共测试 seam 与验收**：`compileTrace(plan, knowledge, stage06, proofs, evidence, source)`覆盖multi-flow ReaderItems、五kind+EMPTY、R0→meaning每hop omission/substitution/crossFlow、九章item completeness、source mutation、orphan/duplicate和different-root bytes。
- **Luna/xhigh 测试指南**：创建 `Stage08TypedTraceCompilerTest`，冻结plan/knowledge/Stage06/Proof/Evidence/source与手写hop goldens于 `src/test/resources/target/stage08/trace-compiler/`。逐RED：五段registry lineage→每hop omission/substitution/crossFlow→五kind+EMPTY→Gap chain→synthetic ID rejection→source mutation→orphan/duplicate→root determinism；首RED因trace seam/schema缺失。只fake source reopen，Trace join/canonical不可mock。命令：`mvn -Dtest=Stage08TypedTraceCompilerTest test`；无网络。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage08/tracecompiler/`，实现 public `TypedTraceCompiler/TraceSet` 与 `stage08-trace-set-v2`；只读M1+Stage01/03/04/06/07 artifacts，按ReaderItem kind join→hops→closure/root。Gap chain解析既有Gap/source-request；ADMITTED_TERM解析完整R0/R1/R2链。不得制造Proof/ID或用Candidate反向ID。跨stage lineage不足MUST STOP升级用户，完成审计。

#### M4 CandidateRunArchiver

- **解决的问题**：把唯一plan/document、Trace、RepositoryCoverageLedger、七个upstream stage roots和series/round lineage组成不可变未发布Candidate，再无环地形成Stage08 root与精确run状态；只有完整capture且coverage闭合的状态是终态完成。
- **精确上游输入及前置**：M1唯一plan、M2唯一document、M3 trace、Stage01–07 roots、已验证且显式scope/eligible/closed/closureReason的RepositoryCoverageLedger、run request/events、Candidate series/round request、archive policy/budget；全部preimage IDs/SHA已验证。bounded scope只归档`repositoryCompletionEligible=false/INCOMPLETE_SCOPE`；完整capture且closed=false只归档`repositoryCompletionEligible=false/INCOMPLETE_COVERAGE`；两者都不能写completion event或进入Selection。
- **确定性顺序 / LLM**：构造绑定`upstreamStageRoots[7]`的Candidate content → validation baseline → exact payload manifest → 计算Stage08 root/receipt → 组装含`stageRoots[8]`和ledger ID/SHA的run manifest → staging force/SHA → atomic install → 仅当complete/eligible/closed三条件同时成立时append completion event，否则append diagnostic archive event；0 LLM。
- **目标输出与 DepotHead 示例**：八个 Stage08 files及CandidateReference；当前例status=UNPUBLISHED_CANDIDATE、0Flow roots、九章document SHA、Gap trace root。
- **必须保持的不变量**：Candidate最后绑定plan/document/trace和upstreamStageRoots[7]，不含Stage08 root；run-manifest才绑定stageRoots[8]，无Candidate↔Stage08 cycle；knowledge/plan/document cardinality为1/1/1；只有complete/eligible/closed三条件同时成立才映射COMPLETE/COMPLETED_WITH_GAPS，bounded映射INCOMPLETE_SCOPE，complete但unclosed映射INCOMPLETE_COVERAGE；前七stage不复制/吞并；Round≤2且lineage闭合；install后immutable。
- **Gap / fatal / 恢复**：在closed完整分母内唯一处置的typed content Gap不阻塞终态；合法scope/coverage不完整只归档诊断Candidate；artifact/root/series/collision/atomic/size或不完整账本自相矛盾为fatal。仅终态完成条件已成立但缺completion event时，M5 validation后追加RECOVERED_COMPLETION。
- **给下游的后置保证**：M5/Selection获得immutable CandidateReference和完整run manifest；Selection不见活动staging。
- **明确非目标**：不发布/选择、不重新render/trace/调用模型、不删除上游stage。
- **公共测试 seam 与验收**：`archive(plan, document, trace, run, policy)`覆盖multi-flow唯一文档、单Flow PASS/ledger未闭合、Candidate含Stage08 root自环、第二plan/document、crash points、collision、Round1/2/3、tamper、force/atomic move和current 0Flow Candidate；只有无环八文件set返回reference。
- **Luna/xhigh 测试指南**：创建 `Stage08CandidateRunArchiverTest`，M1–M3/run artifacts与eight-file goldens放 `src/test/resources/target/stage08/run-archiver/`。逐RED：Round1 0Flow archive、Round2 exact lineage、Round3 rejection、tamper/root、各crash点、collision/atomic unsupported、completion event recovery；首RED因archiver缺失。只mockartifact store/event append faults，identity/manifest不可mock。命令：`mvn -Dtest=Stage08CandidateRunArchiverTest test`；禁网络/Provider。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage08/archiver/`，实现 public `CandidateRunArchiver/CandidateReference` 与 `stage08-candidate-run-publication-v3`；只读M1–M3+stage roots/run request，Candidate→baseline→含repositoryInterpretationRegistryId的manifest→force/SHA/atomic→按runState选择event。逐RED GREEN；不得发布/复制上游/扩Round。跨stage/lineage变更MUST STOP交Sol/ultra/用户，更新审计。

#### M5 IndependentRunValidator

- **解决的问题**：在fresh process重算全run closure，防止archive内部重复字段互相“自证”。
- **精确上游输入及前置**：immutable CandidateReference、run/stage/module manifests、source registry handle、所有schemas/profiles；不信任原进程cache或对象。
- **确定性顺序 / LLM**：Stage01起重验files/modules/stage roots → 全仓coverage ledger/shard unions → facts/flows/R0 registry/R1/R2/唯一knowledge/唯一plan/document → plan-only rerender → registry-to-source Trace closure → acyclic candidate/run identities → validation receipt；0 LLM/Provider。
- **目标输出与 DepotHead 示例**：`ValidationReceipt{runId,candidateId,repositoryCoverageLedgerId,validatedRoots,checks,status,validationId}`；八文件bounded例确认0round、Gap可见、document/trace与八stage roots一致，但状态是`VALID_INCOMPLETE_SCOPE`，不能Selection/COMPLETE；完整multi-flow fixture才可VALID。
- **必须保持的不变量**：每check独立从bytes重算；任何tamper fail closed；validation写外部append-only且不改变Candidate。
- **Gap / fatal / 恢复**：业务Gap仍可VALID；schema/root/source/document/trace/runtime/lifecycle mismatch为INVALID/fatal code；validation可重跑但每receipt绑定相同Candidate bytes。
- **给下游的后置保证**：Trace查询/Selection只接受VALID receipt匹配的Candidate；不需信任生成进程。
- **明确非目标**：不修artifact、不补event、不重放Provider、不批准发布。
- **公共测试 seam 与验收**：`validate(CandidateReference, ReadOnlyRunStore, SourceRegistry)`不得mock canonical/identity；覆盖multi-flow完整ledger、single-flow PASS/other omitted、shard缺/重叠、第二knowledge/plan/document、identity cycle、每层tamper、coherent duplicate-field rewrite、source mutation、0Flow valid和different-root reopen。
- **Luna/xhigh 测试指南**：创建 `Stage08IndependentRunValidatorTest`，valid run copy和逐层tamper fixtures在 `src/test/resources/target/stage08/run-validator/`。每RED只改一层：valid0Flow、module/stage root、Fact/Flow/round/knowledge/plan/document/Trace/source、coherent duplicate rewrite、different-root reopen；明确预期code。只fakeReadOnlyRunStore/SourceRegistry I/O，canonical/identity/validators不mock。命令：`mvn -Dtest=Stage08IndependentRunValidatorTest test`；禁Provider/network。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage08/validator/`，实现 public `IndependentRunValidator/ValidationReceipt` 与 `stage08-validation-receipt-v2`；fresh bytes按Stage01→R0/registry/R1/R2→Stage07/08→rerender→Trace→Candidate/run重算。逐层GREEN；不得信原对象/修artifact/调用Provider。若closure字段不足属跨stageMUST STOP并升级用户，完成审计。

#### M6 RunResumer

- **解决的问题**：从不可变module/stage receipts安全选择唯一恢复点，既复用已验证工作又不重放started模型round。
- **精确上游输入及前置**：runId、exact run-request/control hashes、read-only installed module/stage artifacts、append-only events/failures和RoundSlot ledger；调用者无权修改旧run。
- **确定性顺序 / LLM**：Stage01起顺序验证每module/stage/shard → 遇第一missing/invalid停止 → compare exact controls → fold Stage06 R0/R1/R2 slots → 未freeze则恢复freezer、已freeze则验hash复用 → 验证ledger和1/1/1 cardinality → 返回唯一decision；0 LLM/Provider。
- **目标输出与 DepotHead 示例**：`ResumeDecision{runId,lastValidArtifactId,nextStage,nextModule,decision,reasonCode,repositoryCoverageClosed}`；八文件bounded例返回`NEW_RUN_REQUIRED/REPOSITORY_SCOPE_NOT_COMPLETE`，完整run可从valid Stage05后进入Stage06零任务publisher，controls漂移也要求new run。
- **必须保持的不变量**：不跳号/不跳shard；只复用validated immutable artifact；R1/R2不能先于registry freeze，freeze后不可追加；COMPLETE要求完整repository ledger闭合；control mismatch不覆盖旧run；任一started/ambiguous R0/R1/R2无Provider重放；decision不进入Candidate identity。
- **Gap / fatal / 恢复**：业务Gap不妨碍继续；invalid receipt/artifact、control mismatch或ambiguous lifecycle返回stable stop/new-run code；重复resume产生同decision。
- **给下游的后置保证**：orchestrator得到唯一、可执行恢复点和禁止动作，无需猜哪些内存对象可信。
- **明确非目标**：不执行下一模块、不修旧artifact、不迁移schema、不调用模型或发布Candidate。
- **公共测试 seam 与验收**：`decideResume(runId, request, runStore, ledger)`覆盖multi-flow shard crash/恢复、single-flow PASS、每module crash boundary、stage gap、control drift、started/no-start/ambiguous、completed event missing；同bytes decision稳定且Provider mock调用0。
- **Luna/xhigh 测试指南**：创建 `Stage08RunResumerTest`，每module/stage crash boundary、controls和ledger fixtures放 `src/test/resources/target/stage08/run-resumer/`。逐RED：fresh Stage01、每个last-valid point、Gap继续、control drift new-run、confirmed-no-start、started/ambiguous禁止重放、installed/no event recovered completion；expected decision手写。只fakeReadOnlyRunStore/Ledger/零调用Provider trap，resume算法不可mock。命令：`mvn -Dtest=Stage08RunResumerTest test`；无网络。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage08/resumer/`，实现 public `RunResumer/ResumeDecision` 与 `stage08-resume-decision-v1`；只读run request/module/stage receipts/events/ledger，顺序validate→first invalid→controls→lifecycle fold→decision。逐boundary GREEN；不得执行下一模块/迁移/重放。跨stage receipt语义不足MUST STOP交Sol/ultra/用户，完成审计。

### 8.0.1 模块 artifact wire schemas

M1–M4使用DESIGN 13.3 envelope并位于Stage08；M5/M6使用相同envelope但写run外部append-only目录。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-planner/nine-section-plan-draft.json` | `stage08-nine-section-plan-draft-v2` / `STAGE08_NINE_SECTION_PLAN_DRAFT` | Stage07 registry/knowledge+coverage-ledger+profile/template IDs/SHAs | `planDraftId!`、`repositoryKnowledgeId!`、`repositoryInterpretationRegistryId!`、`repositoryCoverageLedgerId!`、`repositoryCardinality!{knowledgeCount=1!,planCount=1!,documentCountExpected=1!}`、`profileRef!`、`sections[9]!`、`dispositions[]!`、`coverage!`、`rendererProfileRef!`；sections 1..9语义顺序，items按owner rule+readerItemKey |
| `modules/02-renderer/rendered-document.json` | `stage08-rendered-document-v1` / `STAGE08_RENDERED_DOCUMENT` | M1 ID/SHA only | `renderedDocumentId!`、`planDraftId!`、`rendererProfileRef!`、`documentArtifact{path!,sizeBytes!,sha256!}`、`encoding=UTF-8!`、`lineEnding=LF!`、`finalLf=true!` |
| `modules/03-trace/trace-set.json` | `stage08-trace-set-v2` / `STAGE08_TRACE_SET` | M1+Stage01+Stage03+Stage04+Stage05+Stage06+Stage07 IDs/SHAs | `traceSetId!`、`planDraftId!`、`records[]!{traceId!,readerItemKey!,traceKind!,hops[]!{kind!,id!}}`、`readerItemCoverage!`、`traceRoot!`；ADMITTED_TERM必须依序含REGISTRY_LINEAGE/MEANING/SELECTED_KEY/INTERPRETATION_PROPOSAL/PROVISIONAL_KEY/REGISTRY_PROPOSAL/EVIDENCE_CAPSULE/BASIS_ATOM或BASIS_GAP，且REGISTRY_LINEAGE解析的规范值必须等于plan slots；records按readerItemKey |
| `modules/04-archive/stage08-publication.json` | `stage08-candidate-run-publication-v3` / `STAGE08_CANDIDATE_RUN_PUBLICATION` | M1+M2+M3+Stage01–07+coverage-ledger+run/series IDs/SHAs | `publicationId!`、`candidate!{upstreamStageRoots[7]!}`、`repositoryCoverageLedgerId!`、`stageArtifactRoot!`、`runManifest!{stageRoots[8]!,repositoryKnowledgeId!,repositoryInterpretationRegistryId!,nineSectionPlanId!,documentSha256!,repositoryCoverageLedgerId!,repositoryCoverageLedgerSha256!,runState!}`、`publishedArtifacts[8]!`、`runState!`；两处runState逐字相等，前两种才终态，后两种非终态诊断；files按path，roots按stage number；Candidate不得含Stage08 root |
| `validations/<validation-id>/validation-receipt.json` | `stage08-validation-receipt-v2` / `STAGE08_VALIDATION_RECEIPT` | M4 CandidateReference+fresh schema/source registry IDs/SHAs | `validationId!`、`runId!`、`candidateId!`、`repositoryCoverageLedgerId!`、`validationStatus=VALID_COMPLETE\|VALID_COMPLETE_WITH_GAPS\|VALID_INCOMPLETE_SCOPE\|INVALID!`、`validatedRoots[]!`、`checks[]!{checkKey!,status=PASS\|GAP\|FAIL!,recomputedId!,failureCode?}`；checks至少含registry-lineage/coverage/cardinality/acyclic identity并按registered key；仅前两种可进入Selection |
| `resume-decisions/<decision-id>.json` | `stage08-resume-decision-v1` / `STAGE08_RESUME_DECISION` | exact run-request+installed receipt/event/coverage-ledger IDs/SHAs | `resumeDecisionId!`、`runId!`、`lastValidArtifactId?`、`nextStage?`、`nextModule?`、`decision!`、`reasonCode!`、`repositoryCoverageClosed!`、`providerReplayAllowed!`；不存在的next fields为null，COMPLETE要求coverageClosed=true |

Section/ReaderItem/Candidate字段按8.1。`ReaderItem`固定为`readerItemKey!/readerItemKind!/templateKey!/typedSlots!/ownerKnowledgeItemId?/knowledgeItemIds[]!/factIds[]!/meaningIds[]!/registryProposalIds[]!/provisionalKeys[]!/interpretationProposalIds[]!/selectedKeys[]!/gapIds[]!/relationIds[]!/metricIds[]!`；非ADMITTED_TERM四个registry lineage arrays必须为空，ADMITTED_TERM必须与Stage07 exact lineage相等，其`activity-with-anchor-v1` typed slots必含`businessTerm!`、`businessPurpose!`、`technicalAnchor!`、`flow!`、`outcomes[]!`，前两项不得改写Stage07规范值。`ownerKnowledgeItemId=null`当且仅当EMPTY_SECTION。Trace hop target必须是既有typed ID；source locator只在validation后返回。GAP/SEARCHED_SCOPE必须解析既有Gap/source-request。M5 INVALID是validator payload状态，不是ModuleFailure。RepositoryCoverageLedger按DESIGN 3.7 canonical排序；ID/SHA进入M1/M4/M5/M6/run-manifest identity。任何语义变化先设计+升version；跨阶段变化按DESIGN 13.11 MUST STOP并由用户确认。

~~~jsonl
{"schemaVersion":"stage08-nine-section-plan-draft-v2","artifactType":"STAGE08_NINE_SECTION_PLAN_DRAFT","artifactId":"plan-draft:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":8,"module":"NineSectionPlanner","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"nine-section-profile:v1","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"reader-template-profile:v1","sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"artifactId":"repository-coverage:depothead-bounded-v1","sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"artifactId":"stage07-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"7777777777777777777777777777777777777777777777777777777777777777"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"planDraftId":"plan:depothead-round1","repositoryKnowledgeId":"repository-knowledge:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","profileRef":"nine-section-profile:v1","sections":[{"sectionNumber":1,"sectionKey":"DOCUMENT_GUIDE","title":"文档说明","readerItems":[{"readerItemKey":"reader-item:depothead-scope","readerItemKind":"TECHNICAL_FALLBACK","templateKey":"technical-scope-v1","typedSlots":{"display":"POST /depotHead/batchSetStatus → DepotHeadService.batchSetStatus"},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":["knowledge:activity:depothead-status-change"],"relationIds":[],"metricIds":[]}]},{"sectionNumber":2,"sectionKey":"BUSINESS_GOALS","title":"业务目标","readerItems":[{"readerItemKey":"empty:business-goals","readerItemKind":"EMPTY_SECTION","templateKey":"empty-section-v1","typedSlots":{"reasonCode":"NO_SEPARATE_PROVEN_GOAL"},"ownerKnowledgeItemId":null,"factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":[],"relationIds":[],"metricIds":[]}]},{"sectionNumber":3,"sectionKey":"BUSINESS_OBJECTS","title":"业务对象","readerItems":[{"readerItemKey":"reader-item:jsh-depot-head-record","readerItemKind":"REFERENCE_ONLY","templateKey":"record-anchor-v1","typedSlots":{"record":"jsh_depot_head","evidence":"DepotHeadMapper.xml:386"},"ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":["knowledge:record:jsh-depot-head"],"relationIds":[],"metricIds":[]}]},{"sectionNumber":4,"sectionKey":"BUSINESS_ACTIVITIES","title":"业务活动","readerItems":[{"readerItemKey":"reader-item:depothead-batch-audit","readerItemKind":"ADMITTED_TERM","templateKey":"activity-with-anchor-v1","typedSlots":{"businessTerm":"批量审核或反审核","businessPurpose":"描述同一入口依据输入状态批量改变单据状态","technicalAnchor":"POST /depotHead/batchSetStatus","flow":"flow:post-depothead-batch-set-status","outcomes":["outcome:no-eligible-document","outcome:status-updated"]},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":["fact:depothead-status-persistence"],"meaningIds":["meaning:depothead-batch-audit"],"registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"],"selectedKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"gapIds":[],"knowledgeItemIds":["knowledge:activity:depothead-status-change"],"relationIds":[],"metricIds":[]}]},{"sectionNumber":5,"sectionKey":"FIELDS_AND_DIMENSIONS","title":"字段与维度","readerItems":[{"readerItemKey":"reader-item:depothead-status-field","readerItemKind":"FACT_SENTENCE","templateKey":"field-write-v1","typedSlots":{"inputField":"status","targetColumn":"jsh_depot_head.status"},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":["fact:depothead-status-persistence"],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":["knowledge:activity:depothead-status-change"],"relationIds":[],"metricIds":[]}]},{"sectionNumber":6,"sectionKey":"OBJECT_RELATIONS","title":"对象关系","readerItems":[{"readerItemKey":"reader-item:activity-updates-record","readerItemKind":"REFERENCE_ONLY","templateKey":"relation-v1","typedSlots":{"from":"DepotHead status change activity","relation":"UPDATES","to":"jsh_depot_head"},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"relationIds":["relation:activity-updates-record"],"metricIds":[]}]},{"sectionNumber":7,"sectionKey":"METRIC_DEFINITIONS","title":"指标口径","readerItems":[{"readerItemKey":"reader-item:depothead-status-change-metric","readerItemKind":"REFERENCE_ONLY","templateKey":"metric-with-gap-v1","typedSlots":{"metric":"DepotHead status change count","definitionState":"UNRESOLVED_RUNTIME_SCOPE"},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"relationIds":[],"metricIds":["metric:depothead-status-change-count"]}]},{"sectionNumber":8,"sectionKey":"EXAMPLE_QUESTIONS","title":"示例问题","readerItems":[{"readerItemKey":"empty:example-questions","readerItemKind":"EMPTY_SECTION","templateKey":"empty-section-v1","typedSlots":{"reasonCode":"NO_ADMITTED_EXAMPLE_QUESTION"},"ownerKnowledgeItemId":null,"factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":[],"knowledgeItemIds":[],"relationIds":[],"metricIds":[]}]},{"sectionNumber":9,"sectionKey":"PENDING_CONFIRMATION","title":"待确认事项","readerItems":[{"readerItemKey":"reader-item:runtime-status-policy-gap","readerItemKind":"GAP_QUESTION","templateKey":"gap-question-v1","typedSlots":{"subject":"DepotHead status policy","missingRequirement":"trusted runtime or domain-owner attestation"},"ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","factIds":[],"meaningIds":[],"registryProposalIds":[],"provisionalKeys":[],"interpretationProposalIds":[],"selectedKeys":[],"gapIds":["gap:runtime-status-policy"],"knowledgeItemIds":["knowledge:activity:depothead-status-change"],"relationIds":[],"metricIds":[]}]}],"dispositions":[{"semanticItemId":"atom:column","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:eligible-id-set","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:entry-route","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-scope"},{"semanticItemId":"atom:input-field","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:mapper-method","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:service-handler","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-scope"},{"semanticItemId":"atom:table","disposition":"READER_ITEM","readerItemKey":"reader-item:jsh-depot-head-record"},{"semanticItemId":"atom:value-source","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:where-key","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"atom:where-operator","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-field"},{"semanticItemId":"fallback:depothead-route-display","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-scope"},{"semanticItemId":"flow:post-depothead-batch-set-status","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"gap:runtime-status-policy","disposition":"READER_ITEM","readerItemKey":"reader-item:runtime-status-policy-gap"},{"semanticItemId":"knowledge:activity:depothead-status-change","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"knowledge:record:jsh-depot-head","disposition":"READER_ITEM","readerItemKey":"reader-item:jsh-depot-head-record"},{"semanticItemId":"meaning:depothead-batch-audit","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"registry-proposal:depothead-batch-audit","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"interpretation-proposal:depothead-batch-audit","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"metric:depothead-status-change-count","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-status-change-metric"},{"semanticItemId":"outcome:no-eligible-document","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"outcome:status-updated","disposition":"READER_ITEM","readerItemKey":"reader-item:depothead-batch-audit"},{"semanticItemId":"relation:activity-updates-record","disposition":"READER_ITEM","readerItemKey":"reader-item:activity-updates-record"}],"coverage":{"semanticItemIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator","fallback:depothead-route-display","flow:post-depothead-batch-set-status","gap:runtime-status-policy","knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head","meaning:depothead-batch-audit","registry-proposal:depothead-batch-audit","TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretation-proposal:depothead-batch-audit","metric:depothead-status-change-count","outcome:no-eligible-document","outcome:status-updated","relation:activity-updates-record"],"readerOwnedIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator","fallback:depothead-route-display","flow:post-depothead-batch-set-status","gap:runtime-status-policy","knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head","meaning:depothead-batch-audit","registry-proposal:depothead-batch-audit","TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretation-proposal:depothead-batch-audit","metric:depothead-status-change-count","outcome:no-eligible-document","outcome:status-updated","relation:activity-updates-record"],"reasonedExclusionIds":[]},"rendererProfileRef":"renderer-profile:nine-section-markdown-v1","repositoryCoverageLedgerId":"repository-coverage:depothead-bounded-v1","repositoryCardinality":{"knowledgeCount":1,"planCount":1,"documentCountExpected":1}}}
{"schemaVersion":"stage08-rendered-document-v1","artifactType":"STAGE08_RENDERED_DOCUMENT","artifactId":"rendered-document:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":8,"module":"PlanOnlyRenderer","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"plan-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"renderedDocumentId":"document:depothead-round1","planDraftId":"plan:depothead-round1","rendererProfileRef":"renderer-profile:nine-section-markdown-v1","documentArtifact":{"path":"document.md","sizeBytes":2400,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},"encoding":"UTF-8","lineEnding":"LF","finalLf":true}}
{"schemaVersion":"stage08-trace-set-v2","artifactType":"STAGE08_TRACE_SET","artifactId":"trace-set:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":8,"module":"TypedTraceCompiler","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"plan-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"stage06-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"6666666666666666666666666666666666666666666666666666666666666666"},{"artifactId":"stage07-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"7777777777777777777777777777777777777777777777777777777777777777"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"traceSetId":"trace-set:depothead-round1","planDraftId":"plan:depothead-round1","records":[{"traceId":"trace:empty-business-goals","readerItemKey":"empty:business-goals","traceKind":"EMPTY_SECTION","hops":[{"kind":"SECTION","id":"BUSINESS_GOALS"},{"kind":"PROFILE","id":"nine-section-profile:v1"},{"kind":"TEMPLATE","id":"empty-section-v1"},{"kind":"REASON_CODE","id":"NO_SEPARATE_PROVEN_GOAL"}]},{"traceId":"trace:empty-example-questions","readerItemKey":"empty:example-questions","traceKind":"EMPTY_SECTION","hops":[{"kind":"SECTION","id":"EXAMPLE_QUESTIONS"},{"kind":"PROFILE","id":"nine-section-profile:v1"},{"kind":"TEMPLATE","id":"empty-section-v1"},{"kind":"REASON_CODE","id":"NO_ADMITTED_EXAMPLE_QUESTION"}]},{"traceId":"trace:activity-updates-record","readerItemKey":"reader-item:activity-updates-record","traceKind":"REFERENCE_ONLY","hops":[{"kind":"RELATION","id":"relation:activity-updates-record"},{"kind":"FROM_KNOWLEDGE_ITEM","id":"knowledge:activity:depothead-status-change"},{"kind":"TO_KNOWLEDGE_ITEM","id":"knowledge:record:jsh-depot-head"},{"kind":"FACT_ATOM","id":"atom:value-source"},{"kind":"PROOF","id":"proof:value-source"}]},{"traceId":"trace:depothead-batch-audit","readerItemKey":"reader-item:depothead-batch-audit","traceKind":"ADMITTED_TERM","hops":[{"kind":"KNOWLEDGE_ITEM","id":"knowledge:activity:depothead-status-change"},{"kind":"FLOW","id":"flow:post-depothead-batch-set-status"},{"kind":"REGISTRY_LINEAGE","id":"registry-lineage:depothead-batch-audit"},{"kind":"OUTCOME","id":"outcome:no-eligible-document"},{"kind":"OUTCOME","id":"outcome:status-updated"},{"kind":"MEANING","id":"meaning:depothead-batch-audit"},{"kind":"SELECTED_KEY","id":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"kind":"INTERPRETATION_PROPOSAL","id":"interpretation-proposal:depothead-batch-audit"},{"kind":"PROVISIONAL_KEY","id":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"kind":"REGISTRY_PROPOSAL","id":"registry-proposal:depothead-batch-audit"},{"kind":"REGISTRY_PROPOSAL_TASK","id":"task:depothead-r0"},{"kind":"REGISTRY_PROPOSAL_ROUND","id":"round:depothead-r0"},{"kind":"GENERATION_RECEIPT","id":"receipt:depothead-r0"},{"kind":"EVIDENCE_CAPSULE","id":"capsule:post-depothead-batch-set-status"},{"kind":"BASIS_ATOM","id":"atom:input-field"},{"kind":"BASIS_ATOM","id":"atom:value-source"},{"kind":"BASIS_GAP","id":"gap:runtime-status-policy"},{"kind":"FACT","id":"fact:depothead-status-persistence"},{"kind":"FACT_ATOM","id":"atom:value-source"},{"kind":"PROOF","id":"proof:value-source"},{"kind":"PROGRAM_EDGE","id":"data-edge:record-status-to-column"},{"kind":"EVIDENCE","id":"evidence:status-column-span"},{"kind":"SOURCE_SPAN","id":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473"}]},{"traceId":"trace:depothead-scope","readerItemKey":"reader-item:depothead-scope","traceKind":"TECHNICAL_FALLBACK","hops":[{"kind":"KNOWLEDGE_ITEM","id":"knowledge:activity:depothead-status-change"},{"kind":"FALLBACK","id":"fallback:depothead-route-display"},{"kind":"FACT_ATOM","id":"atom:entry-route"},{"kind":"PROOF","id":"proof:entry-route"},{"kind":"PROGRAM_EDGE","id":"structure-edge:controller-route"},{"kind":"EVIDENCE","id":"evidence:controller-route"},{"kind":"FACT_ATOM","id":"atom:service-handler"},{"kind":"PROOF","id":"proof:service-handler"},{"kind":"PROGRAM_EDGE","id":"call-edge:controller-service"},{"kind":"EVIDENCE","id":"evidence:controller-call"},{"kind":"SOURCE_SPAN","id":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43,178-191"},{"kind":"TEMPLATE","id":"technical-scope-v1"}]},{"traceId":"trace:depothead-status-change-metric","readerItemKey":"reader-item:depothead-status-change-metric","traceKind":"REFERENCE_ONLY","hops":[{"kind":"METRIC","id":"metric:depothead-status-change-count"},{"kind":"INPUT_KNOWLEDGE_ITEM","id":"knowledge:activity:depothead-status-change"},{"kind":"INPUT_KNOWLEDGE_ITEM","id":"knowledge:record:jsh-depot-head"},{"kind":"BASIS_GAP","id":"gap:runtime-status-policy"}]},{"traceId":"trace:depothead-status-field","readerItemKey":"reader-item:depothead-status-field","traceKind":"FACT_SENTENCE","hops":[{"kind":"KNOWLEDGE_ITEM","id":"knowledge:activity:depothead-status-change"},{"kind":"FACT","id":"fact:depothead-status-persistence"},{"kind":"FACT_ATOM","id":"atom:column"},{"kind":"PROOF","id":"proof:column"},{"kind":"FACT_ATOM","id":"atom:eligible-id-set"},{"kind":"PROOF","id":"proof:eligible-id-set"},{"kind":"FACT_ATOM","id":"atom:input-field"},{"kind":"PROOF","id":"proof:input-field"},{"kind":"FACT_ATOM","id":"atom:mapper-method"},{"kind":"PROOF","id":"proof:mapper-method"},{"kind":"FACT_ATOM","id":"atom:value-source"},{"kind":"PROOF","id":"proof:value-source"},{"kind":"FACT_ATOM","id":"atom:where-key"},{"kind":"PROOF","id":"proof:where-key"},{"kind":"FACT_ATOM","id":"atom:where-operator"},{"kind":"PROOF","id":"proof:where-operator"},{"kind":"PROGRAM_EDGE","id":"data-edge:record-status-to-column"},{"kind":"EVIDENCE","id":"evidence:status-column-span"},{"kind":"SOURCE_SPAN","id":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473"}]},{"traceId":"trace:jsh-depot-head-record","readerItemKey":"reader-item:jsh-depot-head-record","traceKind":"REFERENCE_ONLY","hops":[{"kind":"KNOWLEDGE_ITEM","id":"knowledge:record:jsh-depot-head"},{"kind":"ANCHOR","id":"anchor:table-jsh-depot-head"},{"kind":"FACT_ATOM","id":"atom:table"},{"kind":"PROOF","id":"proof:table"},{"kind":"SOURCE_SPAN","id":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:386"}]},{"traceId":"trace:runtime-status-policy-gap","readerItemKey":"reader-item:runtime-status-policy-gap","traceKind":"GAP_QUESTION","hops":[{"kind":"GAP","id":"gap:runtime-status-policy"},{"kind":"SEARCHED_SCOPE","id":"source-request:1111111111111111111111111111111111111111111111111111111111111111"}]}],"readerItemCoverage":{"readerItemKeys":["empty:business-goals","empty:example-questions","reader-item:activity-updates-record","reader-item:depothead-batch-audit","reader-item:depothead-scope","reader-item:depothead-status-change-metric","reader-item:depothead-status-field","reader-item:jsh-depot-head-record","reader-item:runtime-status-policy-gap"],"traceRecordKeys":["empty:business-goals","empty:example-questions","reader-item:activity-updates-record","reader-item:depothead-batch-audit","reader-item:depothead-scope","reader-item:depothead-status-change-metric","reader-item:depothead-status-field","reader-item:jsh-depot-head-record","reader-item:runtime-status-policy-gap"]},"traceRoot":"trace-root:3333333333333333333333333333333333333333333333333333333333333333"}}
{"schemaVersion":"stage08-candidate-run-publication-v3","artifactType":"STAGE08_CANDIDATE_RUN_PUBLICATION","artifactId":"stage08-publication:4444444444444444444444444444444444444444444444444444444444444444","producer":{"stage":8,"module":"CandidateRunArchiver","moduleVersion":"v3"},"upstreamArtifacts":[{"artifactId":"candidate-series:depothead","sha256":"abababababababababababababababababababababababababababababababab"},{"artifactId":"plan-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"rendered-document:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"repository-coverage:depothead-bounded-v1","sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"artifactId":"run-request:depothead-round1","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"stage06-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"6666666666666666666666666666666666666666666666666666666666666666"},{"artifactId":"stage07-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"7777777777777777777777777777777777777777777777777777777777777777"},{"artifactId":"trace-set:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"publicationId":"publication:depothead-round1","candidate":{"candidateId":"candidate:depothead-round1","candidateContentId":"candidate-content:depothead-round1","runId":"run:depothead-round1","seriesId":"series:depothead","readerCandidateRound":1,"parentCandidateId":null,"findingIds":["finding:runtime-status-policy-gap"],"nineSectionPlanId":"plan:depothead-round1","documentSha256":"2222222222222222222222222222222222222222222222222222222222222222","traceRoot":"trace-root:3333333333333333333333333333333333333333333333333333333333333333","archivePolicyId":"archive-policy:v1","status":"UNPUBLISHED_CANDIDATE","upstreamStageRoots":["stage-root:0101010101010101010101010101010101010101010101010101010101010101","stage-root:0202020202020202020202020202020202020202020202020202020202020202","stage-root:0303030303030303030303030303030303030303030303030303030303030303","stage-root:0404040404040404040404040404040404040404040404040404040404040404","stage-root:0505050505050505050505050505050505050505050505050505050505050505","stage-root:0606060606060606060606060606060606060606060606060606060606060606","stage-root:0707070707070707070707070707070707070707070707070707070707070707"],"repositoryCompletionEligible":false},"stageArtifactRoot":"stage-root:0808080808080808080808080808080808080808080808080808080808080808","publishedArtifacts":[{"path":"archive-manifest.json","sizeBytes":1600,"sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"path":"candidate.json","sizeBytes":1800,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"document.md","sizeBytes":2400,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"nine-section-plan.json","sizeBytes":3600,"sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"path":"run-manifest.json","sizeBytes":2000,"sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"path":"stage-receipt.json","sizeBytes":1400,"sha256":"6666666666666666666666666666666666666666666666666666666666666666"},{"path":"trace.jsonl","sizeBytes":2200,"sha256":"7777777777777777777777777777777777777777777777777777777777777777"},{"path":"validation-baseline.json","sizeBytes":1200,"sha256":"8888888888888888888888888888888888888888888888888888888888888888"}],"runState":"INCOMPLETE_SCOPE","repositoryCoverageLedgerId":"repository-coverage:depothead-bounded-v1","runManifest":{"stageRoots":["stage-root:0101010101010101010101010101010101010101010101010101010101010101","stage-root:0202020202020202020202020202020202020202020202020202020202020202","stage-root:0303030303030303030303030303030303030303030303030303030303030303","stage-root:0404040404040404040404040404040404040404040404040404040404040404","stage-root:0505050505050505050505050505050505050505050505050505050505050505","stage-root:0606060606060606060606060606060606060606060606060606060606060606","stage-root:0707070707070707070707070707070707070707070707070707070707070707","stage-root:0808080808080808080808080808080808080808080808080808080808080808"],"repositoryKnowledgeId":"repository-knowledge:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","nineSectionPlanId":"plan:depothead-round1","documentSha256":"2222222222222222222222222222222222222222222222222222222222222222","repositoryCoverageLedgerId":"repository-coverage:depothead-bounded-v1","repositoryCoverageLedgerSha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee","runState":"INCOMPLETE_SCOPE"}}}
{"schemaVersion":"stage08-validation-receipt-v2","artifactType":"STAGE08_VALIDATION_RECEIPT","artifactId":"validation:5555555555555555555555555555555555555555555555555555555555555555","producer":{"stage":8,"module":"IndependentRunValidator","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"source-registration:depothead-read-only","sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"},{"artifactId":"stage08-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED","gapRefs":[],"failureRef":null},"payload":{"validationId":"validation:depothead-round1","runId":"run:depothead-round1","candidateId":"candidate:depothead-round1","validationStatus":"VALID_INCOMPLETE_SCOPE","validatedRoots":["stage-root:0101010101010101010101010101010101010101010101010101010101010101","stage-root:0808080808080808080808080808080808080808080808080808080808080808","trace-root:3333333333333333333333333333333333333333333333333333333333333333"],"checks":[{"checkKey":"REGISTRY_MEANING_LINEAGE","status":"PASS","recomputedId":"interpretation-registry:depothead-v1","failureCode":null},{"checkKey":"DOCUMENT_RERENDER","status":"PASS","recomputedId":"document:depothead-round1","failureCode":null},{"checkKey":"SOURCE_TRACE_CLOSURE","status":"PASS","recomputedId":"trace-set:depothead-round1","failureCode":null},{"checkKey":"STAGE_ROOT_CHAIN","status":"PASS","recomputedId":"candidate:depothead-round1","failureCode":null},{"checkKey":"REPOSITORY_COVERAGE","status":"GAP","recomputedId":"repository-coverage:depothead-bounded-v1","failureCode":"REPOSITORY_SCOPE_NOT_COMPLETE"}],"repositoryCoverageLedgerId":"repository-coverage:depothead-bounded-v1"}}
{"schemaVersion":"stage08-resume-decision-v1","artifactType":"STAGE08_RESUME_DECISION","artifactId":"resume-decision:6666666666666666666666666666666666666666666666666666666666666666","producer":{"stage":8,"module":"RunResumer","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"run-request:depothead-round1","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage08-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED","gapRefs":[],"failureRef":null},"payload":{"resumeDecisionId":"resume:depothead-round1","runId":"run:depothead-round1","lastValidArtifactId":"stage08-publication:4444444444444444444444444444444444444444444444444444444444444444","nextStage":null,"nextModule":null,"decision":"NEW_RUN_REQUIRED","reasonCode":"REPOSITORY_SCOPE_NOT_COMPLETE","providerReplayAllowed":false,"repositoryCoverageClosed":false}}
~~~

### 8.1 Interface 与 records

Stage08 finalizer 是内部deep module；所有外部调用统一通过以下唯一run-centric Interface。任何Adapter都不得直接打开run目录或绕过core：

~~~java
interface RepositoryAnalysisAgent {
    AnalysisRunReference start(AnalysisRunRequest request);
    RunInspection inspect(String runId);
    AnalysisRunReference resume(String runId);
    StageArtifactView artifact(StageArtifactQuery query);
    RenderedDocumentReference render(String runId);
    ValidationReceipt validate(String runId);
    TraceView trace(TraceQuery query);
}
~~~

方法语义固定：`start`只创建/启动一个新run；`inspect`返回已持久化状态和每stage/module摘要；`resume`按M6决定继续点且不重放started slot；`artifact`只按`runId+artifactId`返回metadata或预算内的完整canonical bytes/digest，超限全量拒绝而不截断；`render`只返回或从已验证唯一plan确定性重渲染document，不重新分析；`validate` fresh replay；`trace`只在匹配validation下查询typed lineage。inspect/artifact/render/validate/trace均为read-only、零Provider call。

Adapter映射必须一一同义：

| Core | Java | CLI | loopback HTTP |
| --- | --- | --- | --- |
| start | `start(request)` | `analyze --request <json>` | `POST /analysis-runs` |
| inspect | `inspect(runId)` | `inspect --run <id>` | `GET /analysis-runs/{runId}` |
| resume | `resume(runId)` | `resume --run <id>` | `POST /analysis-runs/{runId}:resume` |
| artifact | `artifact(query)` | `artifact --run <id> --artifact-id <id> [--stage <n>] [--module <name>] [--type <type>] [--sha256 <hex>] [--metadata-only \| --max-bytes <n>]` | `GET /analysis-runs/{runId}/artifacts/{artifactId}`；optional query仅为`expectedStage/expectedModule/expectedType/expectedSha256/contentMode/maxBytes` |
| render | `render(runId)` | `render --run <id>` | `POST /analysis-runs/{runId}:render` |
| validate | `validate(runId)` | `validate --run <id>` | `POST /analysis-runs/{runId}:validate` |
| trace | `trace(query)` | `trace --run <id> --reader-item <key>` | `GET /analysis-runs/{runId}/trace?readerItemKey=<key>` |

CLI 的 `<json>` 是显式request文件入口，不是artifact query。`artifact` query必须给`runId + artifactId`；stage/module/type/digest只能作为optional expected discriminators，不能替代identity；`METADATA_ONLY`要求`maxBytes=0`，`COMPLETE_UTF8`要求positive ceiling，artifact超过query或server ceiling时返回`ARTIFACT_RESPONSE_BUDGET_EXCEEDED`且不返回partial content。不得接受Path、glob、相对路径或“最新文件”。HTTP只绑定loopback，要求run-scoped bearer capability、固定body/queue/response budgets与idempotency key；Java/CLI/HTTP返回相同stable error code和同一digest/bytes，不得各自发明fallback。

~~~text
NineSectionPlan
  nineSectionPlanId
  repositoryKnowledgeId
  repositoryInterpretationRegistryId
  profileRef
  sections[9]
  dispositions[]
  coverage
  rendererProfileRef

SectionPlan
  sectionNumber
  sectionKey
  title
  readerItems[]

ReaderItem
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

Candidate
  candidateId
  candidateContentId
  runId
  seriesId
  readerCandidateRound
  parentCandidateId?
  findingIds[]
  upstreamStageRoots[7]
  nineSectionPlanId
  documentSha256
  traceRoot
  archivePolicyId
  repositoryCompletionEligible
  status=UNPUBLISHED_CANDIDATE

RunManifest
  runId
  stageRoots[8]
  repositoryCoverageLedgerId
  repositoryCoverageLedgerSha256
  repositoryKnowledgeId
  repositoryInterpretationRegistryId
  nineSectionPlanId
  documentSha256
  runState: COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE

ValidationReceipt
  validationId
  runId
  candidateId
  repositoryCoverageLedgerId
  validationStatus: VALID_COMPLETE | VALID_COMPLETE_WITH_GAPS | VALID_INCOMPLETE_SCOPE | INVALID
  validatedRoots[]
  checks[]

RunInspection
  runId
  runState
  analysisRunRequestId
  repositoryCompletionEligible
  repositoryCoverageLedgerId
  repositoryCoverageClosed
  stageReceipts[]
  moduleReceipts[]
  artifactDescriptors[]
  gapSummaries[]
  failureSummaries[]

StageArtifactQuery
  runId
  artifactId
  expectedStageNumber?
  expectedModuleName?
  expectedArtifactType?
  expectedSha256?
  contentMode: METADATA_ONLY | COMPLETE_UTF8
  maxBytes                                  // 0 metadata; positive all-or-error ceiling otherwise

StageArtifactView
  runId
  stageNumber
  moduleName?
  artifactType
  schemaVersion
  artifactId
  sha256
  sizeBytes
  mediaType
  validationState
  immutableReference
  contentUtf8?

RenderedDocumentReference
  runId
  candidateId
  nineSectionPlanId
  documentArtifactId
  documentSha256
  sizeBytes
  mediaType=text/markdown
  validationReceiptId?
  rerenderMatched=true
~~~

Candidate 的 `upstreamStageRoots[7]` 只含 Stage01–07。Stage08 root由Candidate/plan/document/Trace等payload artifacts计算，随后才写入RunManifest的`stageRoots[8]`；这样前项不引用后项，identity DAG无环。

`StageArtifactQuery`始终以`runId+artifactId`做manifest membership lookup；optional expected stage/module/type/SHA任一不等即拒绝。它不是目录浏览；`COMPLETE_UTF8`只可返回完整bytes，超预算必须以稳定code拒绝。`RenderedDocumentReference`只可指向唯一persisted document或其相同bytes的plan-only rerender；diagnostic Candidate可render/inspect但仍不可Selection。

现有CodeToMarkdownAgent只可作为兼容Adapter委托这七方法，不能成为第二套语义或固定target seam。

#### 8.1.1 RepositoryAnalysisAgent handoff

- **Luna/xhigh测试指南**：创建`RepositoryAnalysisAgentContractTest`、`RepositoryAnalysisCliAdapterTest`和`RepositoryAnalysisLoopbackHttpAdapterTest`；共用`src/test/resources/target/runtime/run-centric-agent/`内一个完整multi-flow run、一个diagnostic run、每stage/module artifact identity表和独立response goldens。RED顺序固定为start→inspect→resume→artifact stage/module query→render→validate→trace，再逐项跑三Adapter conformance、wrong identity/Path/cross-run/auth/budget/crash；每个RED因对应public method/mapping/guard缺失失败。允许mock只读run store、loopback transport、auth verifier和fault boundary；禁止mockidentity lookup、canonical bytes、core lifecycle/validation/Trace，禁止网络/live Provider/客户Maven/private实现耦合。命令：`mvn -Dtest=RepositoryAnalysisAgentContractTest,RepositoryAnalysisCliAdapterTest,RepositoryAnalysisLoopbackHttpAdapterTest test`。异常RED才由Sol/xhigh debug；任何public语义变化按DESIGN 13.11 STOP。
- **Terra/xhigh实现指南**：仅观察对应RED后，core职责归`target/runtime/RepositoryAnalysisAgent`与public records，Adapter职责分别归`target/adapters/java/`、`target/adapters/cli/`、`target/adapters/http/`；实现/替换七方法、`RunInspection/StageArtifactQuery/StageArtifactView/RenderedDocumentReference`和stable error mapping。vertical slices严格按七方法顺序，每slice须core selector与三Adapter同义selector GREEN，并同步本节实现审计；复用各stage persisted artifacts和M1–M6 public Interfaces，不能直接读run Path、复制编排逻辑、隐藏artifact、调用Provider或兼容旧final-only语义。若artifact identity、run state或安全边界不够，STOP交Sol/ultra Design Authority；跨阶段修改需用户确认后先改DESIGN/stage docs。

### 8.2 精确九章

每章恰好一次，顺序与标题不得改变：

1. 文档说明
2. 业务目标
3. 业务对象
4. 业务活动
5. 字段与维度
6. 对象关系
7. 指标口径
8. 示例问题
9. 待确认事项

不得新增第十章、改名或交换。空章使用 typed EMPTY_SECTION item，保存 exact sectionKey、effective profileId 和 templateKey；validator不能从当前代码补推缺失字段。

### 8.3 Renderer 隔离

renderer内部 Interface（不是run-centric公共API）：

~~~java
byte[] render(NineSectionPlanArtifact exactPlan);
~~~

它只解析 one exact canonical plan。进程/模块能力中没有 snapshot root、source registry、Provider、model round、Fact prover 或 network。输出统一 UTF-8、LF、final LF。相同 plan bytes 必须输出相同 Markdown bytes。

### 8.4 Trace

traceKind 至少：

- FACT_SENTENCE：ReaderItem→Fact atom→Proof→Evidence/source；
- ADMITTED_TERM：ReaderItem→knowledge→meaning→selectedKey→interpretationProposal→provisionalKey→registryProposal→R0 task/round/receipt→EvidenceCapsule→basis atoms/Gaps→Proof/Evidence/source；
- TECHNICAL_FALLBACK：ReaderItem→resolution→policy/template→anchor proven slots；
- GAP_QUESTION：ReaderItem→canonical Gap（其字段给出 missing/closure requirement）→Stage01 admitted source-request artifact（searched scope）；
- REFERENCE_ONLY：关系/引用 item；
- EMPTY_SECTION：section/profile/template lineage。

Trace locator 不是 Proof。Trace 查询前先完成全 Candidate/run validation；源码 hash/span mutation使查询失败，而不是返回陈旧 locator。

Gap Trace 不发明 `scope:*` 或 `requirement:*`：`GAP` hop 的id来自Stage07 `canonicalGapId`，`SEARCHED_SCOPE` hop 的id来自Stage01 admitted source-request `artifactId`；missing/closure requirement是该canonical Gap的已持久化字段，不另造身份。

validation 状态按程序规则唯一选择：完整capture且ledger闭合、无Gap为`VALID_COMPLETE`；完整capture且ledger闭合、所有不支持/失败项已有typed disposition为`VALID_COMPLETE_WITH_GAPS`；bounded scope（run state `INCOMPLETE_SCOPE`）或完整capture但合法未闭合coverage（run state `INCOMPLETE_COVERAGE`）若其已归档内容在自身声明范围内结构/identity/Trace一致，统一为`VALID_INCOMPLETE_SCOPE`；任一schema、root、cardinality、source、Trace或ledger内部一致性检查失败为`INVALID`。`VALID_INCOMPLETE_SCOPE`只表示诊断归档自洽，不表示repository coverage完成；禁止Selection、禁止completion event，也不能把它改名成前两种VALID来绕过仓库完成门禁。

### 8.5 Candidate rounds、lifecycle 与 archive

ReaderCandidateRound最多两份：

- Round 1 初始 Candidate；
- Round 2 只能针对 Round 1 已归档、命名、引用闭合且 APPROVED_FOR_ROUND_2 的 finding，冻结同一 source/Facts/Flows/Capsules/registries；
- 不存在 Round 3；Round 2 不能作为 parent；
- unaffected Flow 逐字节复用 parent canonical rounds/receipts；
- fatal/addendum-required finding 在没有可信外部 attestation workflow 时 fail closed。

Candidate目录安装后不可变；后续 validation/review/run events在外部追加目录。staging与destination同文件系统；写满、force、逐SHA校验后ATOMIC_MOVE，不支持时停止。

### 8.6 Run resume 与 identity

runId 绑定 rootless AnalysisRunRequest、source registration、toolchain/profile/schema/prompt bundles、budgets和ReaderCandidateRound lineage。每个 stageReceipt绑定input roots与controls。

resume：

1. 重算run-request/control hashes；
2. 从Stage01起依序验证installed stage receipts，不跳号；
3. 第一个缺失/invalid stage是恢复点；
4. 任一已完成stage controls不等则RUN_RESUME_CONTROL_MISMATCH并创建新run；
5. Stage06 R0/R1/R2 slots分别按durable lifecycle折叠，started/ambiguous绝不重放；全部R0终态才可freeze，已freeze registry只验hash复用；
6. 下游失败不删除上游stages。

### 8.7 预算、安全与 failure codes

预算覆盖reader items/slots、sections=9、document bytes、trace records/hops、archive files/bytes、directory entries、validation reopen bytes和run events。run-centric观察另固定`maxInspectionStages=8`、`maxInspectionModules`、`maxArtifactBytes`、`maxTraceHops`、`maxConcurrentRequests`、`maxQueuedRequests`和HTTP body bytes；超限返回stable error，artifact bytes不得截断。普通文件先NOFOLLOW/size gate；目录在limit+1前停止。

secret、prompt、raw response/reasoning、绝对路径不进入正文或任何View。HTTP只绑定loopback并校验run-scoped bearer capability、ID-only requests、body/queue/idempotency limits；artifact/trace先校验run ownership与artifact membership，拒绝Path、glob、目录枚举、symlink和跨run identity。Adapter不能绕过core；观察方法没有Provider/source-execution capability。

稳定 code：

STAGE08_INPUT_INVALID、NINE_SECTION_INVALID、SECTION_OWNER_INVALID、READER_ITEM_INVALID、READER_ATOM_LOSS、REGISTRY_MEANING_LINEAGE_BROKEN、READER_INFORMATION_DENSITY_FAILED、BODY_CLEANLINESS_FAILED、DOCUMENT_HASH_MISMATCH、TRACE_CLOSURE_BROKEN、STAGE_REPLAY_MISMATCH、RUN_MANIFEST_INVALID、RUN_RESUME_CONTROL_MISMATCH、RUN_NOT_FOUND、ARTIFACT_QUERY_INVALID、ARTIFACT_NOT_FOUND、ARTIFACT_IDENTITY_MISMATCH、ARTIFACT_ACCESS_DENIED、ARTIFACT_RESPONSE_BUDGET_EXCEEDED、RENDER_NOT_AVAILABLE、RUN_NOT_VALIDATABLE、OBSERVATION_BUDGET_EXCEEDED、ARCHIVE_MANIFEST_INVALID、ARCHIVE_ARTIFACT_SET_INVALID、ARCHIVE_IDENTITY_COLLISION、ARCHIVE_ATOMIC_MOVE_UNSUPPORTED、CANDIDATE_SIZE_LIMIT_EXCEEDED、SERIES_LINEAGE_INVALID、ROUND_2_SLOT_ALREADY_CONSUMED。

### 8.8 测试 seam 与验收

- nine-section-plan恰九章；少/多/乱序/改名均失败。
- renderer只拿plan的隔离测试仍能渲染；尝试读source/model artifact不可达。
- 每个atom/meaning/Gap有唯一disposition；删除/重 owner fatal。
- 相同plan不同root/order重复render，Markdown bytes/SHA相同。
- 五类Trace及EMPTY_SECTION exact refs有omission/substitution/source mutation测试。
- exact stage roots/run manifest/candidate identity coherent tamper仍失败，不能靠一起改重复字段自证。
- final install前后crash、recovered completion、completed tamper readmission均不重放Provider。
- 0 Flow/0 Capsule生成诚实九章、0 rounds、Gap可见。
- ReaderCandidateRound 2 exact parent/finding/frozen-basis/unaffected-round reuse；第三份不可表示。
- 至少双Flow的RepositoryKnowledge只生成一份plan/document；跨Flowrelation/metric均有ReaderItem/Trace。第二plan/document、per-Flow Markdown fragment或文本拼接输入必须失败。
- RepositoryCoverageLedger mutation覆盖缺/重叠shard、omitted entry/Flow/proposal/knowledge/owner、single Flow PASS；均不得写COMPLETE。改变shard size/order/recovery point后唯一document bytes相同。
- 同一frozen run fixture对`RepositoryAnalysisAgent`七方法做Java/CLI/loopback HTTP contract conformance；三种Adapter的状态、error、artifact SHA/bytes、render reference、validation和Trace逐字节/逐字段同义。
- `artifact`覆盖stage-level与module-level identity query、wrong run/stage/module/artifact、Path/glob注入、跨run访问、size budget、crash后相同bytes；inspect/render/artifact/validate/trace全部断言Provider调用0。

验收必须覆盖九章少/多/乱序/改名、至少双Flow→唯一RepositoryKnowledge/plan/document、plan-only renderer capability isolation、五类Trace加EMPTY_SECTION、0Flow Gap文档、coverage ledger/shard/accounting mutations、Candidate/Stage08 identity cycle、archive crash points、tamper、resume controls、Round2 lineage与Round3 rejection。只有八文件set原子安装、fresh validator证明完整repository ledger/1:1:1 cardinality/acyclic roots全闭合、相同plan产生相同Markdown bytes且所有反例fail closed，Stage08才算可交付。

### 8.9 已冻结裁决：实现者不得自由推断

- NineSectionProfile 的九个标题、顺序和恰一次规则固定；空章用 typed EMPTY_SECTION，不能增删改名或 renderer 临时写 filler。
- planner 拥有 section owner/disposition；renderer 唯一输入是 exact `nine-section-plan.json`，没有 source/model/registry 能力。
- Trace 只做 typed lineage 查询，不代替 Proof；查询前必须完成 Candidate/run validation。
- Candidate 安装后不可变且始终未发布；review/validation/events 只能写外部 append-only 区域，Selection 是后续流程。
- Candidate只引用Stage01–07 upstream roots；Stage08 root只进入run-manifest。每run只有一份RepositoryKnowledge、NineSectionPlan和document.md；禁止per-Flow Markdown/fragment拼接。
- completion只由COMPLETE_CAPTURE的RepositoryCoverageLedger闭合触发；单Flow PASS、缺/重叠shard或任何unsupported/failed/omitted item无处置时不得COMPLETE。
- ReaderCandidateRound 最多 2；Round 2 只处理已批准 finding并冻结 basis，不存在 Round 3。
- resume 从 Stage 01 顺序验证 receipt、精确匹配 controls、保留上游结果且不重放 started model round；不能宽松迁移旧 run。
- 唯一公共Interface是run-centric `RepositoryAnalysisAgent.start/inspect/resume/artifact/render/validate/trace`；Java/CLI/loopback HTTP只是同义Adapter。artifact必须run+identity且不得接受Path，观察方法不得调用Provider。
- template engine、Markdown escaping 和 archive 内部类可选择；九章语义、plan-only boundary、Trace、identity、lifecycle、failure 策略不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **部分具备（final-only Candidate archive）** | 现有 Stage03 有 typed NineSectionPlan/renderer，Stage04 archive-v2 保存 document、plan、Trace、registries、rounds、receipts等完整final preimage并支持零Provider validation |
| **有效保留的合同** | immutable Candidate、started-before-content、typed Trace、self-describing EMPTY_SECTION、no-follow/size/directory admission、atomic install、append-only validation和最多两ReaderCandidateRounds |
| **尚未符合目标** | 前七阶段成功资产未立即作为生产stage目录落盘；final Candidate archive是第一处完整持久化，whole-analysis-run resume/manifest不完整 |
| **runtime差距** | target CLI/HTTP主要是注入式受测seam；默认target runtime bootstrap、filesystem reader composition、CLI improve/serve及更广routes未完成 |
| **真实DepotHead边界** | 当前只能诚实渲染范围/Gap，不能声称业务Flow成功；0 Flow/0 Capsule必须保持可见 |

目标Stage08保留当前archive-v2的证明强度，但把可观察性前移到每一阶段，并把恢复对象从“一个Candidate slot”提升为“整个分析run”。
