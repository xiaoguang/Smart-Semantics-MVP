# GitHub Code Agent 目标设计：从冻结代码到可信业务文档

## 1. 先说业务结果

GitHub Code Agent 的目标不是“让模型读一遍仓库并写篇总结”，也不是证明一个代码切片后就停止，而是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，转换成一份业务人员能读、工程人员能追、审计人员能复验的仓库级九章 Markdown 候选。

一次分析成功后，读者应能回答四个问题：

1. 这个应用向外提供哪些业务入口？
2. 每个入口从请求到持久化或返回经历了什么？
3. 哪些结论由源码和确定性规则证明，哪些只是模型解释？
4. 哪些问题当前不知道，应该由谁、用什么新增材料关闭？

候选始终未发布。Selection、冻结来源文档、package 和发布属于后续显式流程，不由本 Agent 自动完成。

### 1.1 信任目标

本设计把“好读”和“可信”同时设为出口条件：

- **来源可信**：同一 revision、路径和字节可以重开；不能静默换成分支最新值。
- **仓库覆盖可信**：冻结仓库中的每个文件、发现 site、入口和后续语义项都有唯一处置；单个 Flow 成功不能冒充整仓分析完成。
- **推导可信**：业务事实的每个语义原子都能回到源码 span、程序图和确定性规则。
- **模型受限**：模型只解释一个已经由程序编译完整的流程，不能发现调用、补路径、写 locator 或自批事实。
- **未知诚实**：静态代码不能证明的运行时、部署和企业政策进入 Gap，不进入事实。
- **过程可见**：每一阶段成功后立即留下 canonical JSON/JSONL 生产资产；下游失败不抹掉上游成果。
- **恢复精确**：恢复继续使用完全相同的输入、工具、profile、schema 和 prompt 哈希，不做“差不多”的重跑。
- **文档可重验**：Markdown 只读 nine-section-plan.json；独立验证可以重开冻结源码，但 renderer 不重新读源码。

### 1.2 五种材料永不混写

| 标签 | 含义 | 可以进入最终正文吗 |
| --- | --- | --- |
| **REAL_SOURCE** | 固定 revision 中实际存在的字节和 locator | 作为引用与技术依据 |
| **DETERMINISTIC_CONCLUSION** | 程序由受支持语法、五张程序图和闭合 Proof 得出的结论 | 可以，保留 Fact/Proof lineage |
| **MODEL_INTERPRETATION** | 模型针对一个 EvidenceCapsule 给出的有限业务解释 | 只有经 Stage 07 准入后可以 |
| **UNKNOWN / GAP** | 当前无法唯一证明、范围外或需要运行时/业务责任方确认的内容 | 可以作为待确认事项，不能写成事实 |
| **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT** | 连贯解释目标流程或目标 wire shape 的示意，不是当前产物 | 不作为当前实现或运行证据；技术示例仍必须符合目标 schema |

后文凡展示目标流程 JSON，除非明确写成“当前已归档产物”，统一属于 **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**。为保持可读性不在每个字段重复标签：已给出的 commit/path/route/locator 是 **REAL_SOURCE**；只有在声明算法/前置全部满足后才能产生的值是 **DETERMINISTIC_CONCLUSION**；示例 ID、digest 和尚未运行得到的业务词是说明性值。技术合同不能用说明性值填补未知，必须使用 nullable、UNRESOLVED、Gap 或 fatal 结构。

## 2. 唯一贯穿样例：真实 DepotHead 状态更新路径

样例来源是 jshERP 固定 commit 8c30ce7861570458920175e200bb2a6442713580 的本地只读快照。本文没有执行客户 Maven、应用、SQL、MyBatis runtime、网络或模型。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本节及各 stage 文档用一组连贯、合理的 ID/digest 展示目标 artifact 怎样接力；它们不声称当前运行已产出。真实源码值与当前 Gap 结论仍以 2.1、2.4 和实现审计为准。

DepotHead 只是完整仓库中 `N` 个入口/FlowSlice 之一的讲解 fixture。本文反复出现的八文件 `BOUNDED_PATH_SET` 只能验证这条局部链路和失败语义，**不具备 repository completion 资格**；生产分析必须使用 Stage 01 证明闭合的 `COMPLETE_CAPTURE`，并对其中所有入口逐一处置。后文故事为了可读性只展开 DepotHead，不表示其他文件、入口或 Flow 可以省略。

### 2.1 REAL_SOURCE：可直接核对的源码位置

| 层 | 固定源码 | 真实内容 |
| --- | --- | --- |
| HTTP Controller | DepotHeadController.java:43 | 类级 RequestMapping 为 /depotHead |
| HTTP 方法 | DepotHeadController.java:178-191 | POST /batchSetStatus；读取 status、ids；调用 depotHeadService.batchSetStatus(status, ids) |
| Service | DepotHeadService.java:741-822 | batchSetStatus；按 status 和当前单据状态筛选 dhIds，做库存条件检查，设置 DepotHead.status，构造 DepotHeadExample，并调用 Mapper |
| 状态赋值 | DepotHeadService.java:798-803 | setStatus(status)、andIdIn(dhIds)、updateByExampleSelective |
| Mapper Java | DepotHeadMapper.java:23 | updateByExampleSelective(record, example) |
| Entity | DepotHead.java:63,301-307 | status 字段及 get/setStatus |
| Criteria | DepotHeadExample.java:149-151 | andIdIn 生成 id in criterion |
| Mapper XML | DepotHeadMapper.xml:3,70-93,385-497 | namespace、动态 where、updateByExampleSelective |
| 表和列 | DepotHeadMapper.xml:386,472-473 | update jsh_depot_head；record.status 非空时写 status |
| where include | DepotHeadMapper.xml:494-495 | 引用 Update_By_Example_Where_Clause |

这些是源码观察，不等于当前程序已经证明整条业务流程。

### 2.2 目标程序应证明什么

当五张程序图和 Proof 全部闭合时，目标系统可以形成如下 **DETERMINISTIC_CONCLUSION**：

1. POST /depotHead/batchSetStatus 绑定到 DepotHeadController.batchSetStatus。
2. Controller 的 status 与 ids 实参精确绑定到 DepotHeadService.batchSetStatus 的两个参数。
3. 只有通过 Service 中状态检查的 id 才进入 dhIds。
4. 非空 dhIds 触发 DepotHead.setStatus(status) 和 DepotHeadExample.andIdIn(dhIds)。
5. Service 调用精确绑定 DepotHeadMapper.updateByExampleSelective。
6. Mapper Java 方法精确绑定 XML statement updateByExampleSelective。
7. record.status 的数据流精确到 XML 的 status = #{record.status}，statement 作用表为 jsh_depot_head。
8. example 的 id-in criterion 精确进入 XML 的 Update_By_Example_Where_Clause。

第 7、8 点必须由正式数据流图、Mapper 参数绑定和 XML include 展开证明，不能因为字符串相似就成立。

### 2.3 MODEL_INTERPRETATION 与 UNKNOWN

模型可以在冻结词表允许时提出“批量审核或反审核单据”作为流程业务名称；程序仍需核对该 term 对本 Flow 是否 eligible。模型不能把注释或 ApiOperation 文本直接升级成企业政策。

静态源码仍不能证明：

- 数据库触发器、隔离级别或外部系统是否另改 status；
- 部署时实际启用了哪些配置；
- status 0/1 的企业口径是否在所有租户、版本和单据类型中相同；
- HTTP 异常最终如何被全局异常处理器呈现；
- 事务外围是否有重试或补偿。

这些保持 **UNKNOWN / GAP**。

### 2.4 当前诚实结果

现有实现中名为 `Stage02Compiler` 的固定 jshERP 八文件验收结果是 **Gap、0 Flow、0 Capsule**；它对应本次八阶段目标中的 Stage 05，而不是新的入口发现 Stage 02。原因不是源码里没有这条路径，而是当前通用数据流、Fact registry 和动态 MyBatis 证明能力不足以闭合目标合同。后文目标 JSON 不能被引用为“当前 DepotHead 已成功”。

## 3. 八阶段纵向主线

唯一生产主线如下。每个箭头跨越的是已落盘、已自验的 canonical artifact，不是上一阶段的 Java 内存对象。

~~~text
01 冻结来源
   -> 02 发现应用类型和入口
   -> 03 构建五张正式程序图
   -> 04 证明代码事实并登记 Gap
   -> 05 编译入口根业务流程和逐流程阅读包
   -> 06 每次只解释一个流程
   -> 07 程序准入解释并合并仓库业务知识
   -> 08 规划九章、渲染、Trace、归档、观察与恢复
~~~

目标 run 目录：

~~~text
runs/<run-id>/
  run-request.json
  run-events.jsonl
  stages/
    01-freeze-source/
      modules/<nn-module>/<module-artifact>.json
      <stage-public-artifacts>
    02-discover-application-and-entries/
    03-build-five-program-graphs/
    04-prove-code-facts/
    05-compile-business-flows/
    06-interpret-one-flow-at-a-time/
    07-admit-and-merge-business-knowledge/
    08-build-nine-section-document-and-archive/
  validations/
  failures/
  run-manifest.json
~~~

每个 stages/<nn-name>/ 目录先在同文件系统 staging 中写满、canonicalize、逐文件算 SHA、自验，再原子安装。安装后不可修改；新的验证、失败或恢复事件写到外部追加区域。

### 3.1 非技术读者怎样顺着 DepotHead 走完主线

| 阶段 | 这一阶段回答的普通问题 | DepotHead 的目标可观察答案 | 为什么下一阶段不必返工 |
| --- | --- | --- | --- |
| 01 | “看的到底是哪份代码？” | 生产 run 固定完整仓库；八个 DepotHead 文件只是其中可追踪的示例子集 | 后续按 snapshot/file identity 重开，不再碰活动工作树 |
| 02 | “仓库有哪些请求入口？” | 全入口 inventory 中包含 `POST /depotHead/batchSetStatus` 及 handler、Mapper 候选 | 图构建直接拿完整 entry/catalog denominator，不再猜 route或漏掉其他入口 |
| 03 | “请求、条件、值和 SQL 怎样连起来？” | 五张图分别表达结构、调用、控制、数据和证据 | Fact prover 只消费 graph edges，不重写 parser |
| 04 | “哪些整句结论真的证明了？” | 每个 candidate Fact 的所有 atom 要么有闭合 Proof，要么成为带原因的 Gap | Flow compiler 只引用 admitted Fact/Proof，不借附近源码 |
| 05 | “每个请求有哪些完整结局？” | 全入口逐一成为 Flow、多条 Outcome/一个 Capsule，或有证据的 GAP/EXCLUDED | Stage06 的 R0/R1/R2 都只能读各自 Flow 的同一 Capsule，不能跨流程补材料 |
| 06 | “新仓库的业务词从哪里来，又怎样安全使用？” | 每个 eligible Flow 先在隔离 R0 提出有 basis 的 bounded 业务词/claim/question；程序验证并冻结唯一 RepositoryInterpretationRegistry，再让同一 Flow 的 R1/R2 只选择有限 provisional keys。正常为每 Flow 三个 slot/call；当前 0 Capsule 所以 0 调用 | Stage07 重放 registry proposal→provisionalKey→selectedKey 的完整 lineage 与所有 slice dispositions，不再调用模型 |
| 07 | “所有流程的说法怎样形成一个仓库视图？” | 程序逐 slice 准入或丢弃有限 key 提案，再跨 Flow 合并 Facts、meanings、relations、metrics、conflicts、registry lineage 和 Gaps | 文档规划只读唯一一份 RepositoryKnowledge，Trace 可回到 R0 basis |
| 08 | “整个仓库的业务读者看到什么，怎样观察每阶段？” | 每个 analysis run 恰一份九章 plan、恰一份 document.md、Trace、未发布 Candidate 和完整 run manifest；同一 run-centric Interface 可 inspect/artifact/render/validate/trace | 审阅、Trace、Selection、validation、resume 和 Java/CLI/HTTP Adapter 各读固定身份；禁止一 Flow 一 Markdown或用 Path 绕过 manifest |

因果关系必须保持：来源不固定就不能可信定位；**完整入口分母**不固定就不能证明仓库覆盖；五图不闭合就不能证明 Fact；Fact 不闭合就不能编译 Flow；没有 Flow/Capsule 就不得调用模型。当前 DepotHead 恰好停在 Fact/Flow closure Gap，因此它在 Stage 06 是零任务；同一仓库的其他 eligible Flow 仍独立执行，Stage 07–08 把全部 slice 的 Facts、解释、失败、排除和 Gaps 合成非空、可审阅的单一仓库知识与九章文档。

### 3.2 共通阶段出口

“零业务记录”不等于“没有产物”。每个 `SUCCEEDED` 或 `SUCCEEDED_WITH_GAPS` 阶段都必须原子安装该阶段规定的完整文件集合和非空 `stage-receipt.json`；允许为零的 JSONL 仍作为 canonical empty file 存在，receipt 显式记录分母、零计数、Gap 数和 artifact SHA。只有 receipt、artifact root、control hashes 和本阶段不变量全部通过，下一阶段才可开始。`FAILED` 只写外部 failure/event 记录，不安装伪成功目录。

阶段内部也禁止只传 Java 内存对象。每个命名模块将一个 schema-versioned `ModuleArtifact<payload>` 写入自己的 immutable `modules/<nn-module>/` 目录；下一模块必须重开、校验 artifact ID/SHA/upstream/control refs 后才能处理。模块成功 artifact 原子安装，模块失败写外部 `ModuleFailure`，已完成模块不回滚。stage publisher 只消费这些 artifacts 并生成对下一阶段公开的 stage files；它不得从内存旁路或重新运行上游模块。

### 3.3 端到端 composition proof

下表不是路线图，而是阶段组合的验收证明。每行 postcondition 必须逐字段满足下一行 precondition；reader 不允许从别处补默认。

| Stage | precondition | 确定性 transformation | postcondition | 精确满足下一阶段 |
| --- | --- | --- | --- | --- |
| 01 | exact run request、capture receipt、非空 `COMPLETE_CAPTURE` inventory、受限本地 root、controls | admission → NOFOLLOW/size/SHA/UTF-8 → rootless identity → atomic publish | immutable full snapshot/inventory、每 path disposition、repositoryCompletionEligible=true、Stage01 root/receipt | Stage02 要求的全仓 snapshotId、source handles、inventory、tool/profile/schema controls 全部已固定 |
| 02 | valid complete Stage01 root；discovery profile 能解析全部声明文件 | application signals → 全 site/entry routes → Mapper candidates → capability accounting | applicationProfileId、全部 entryIds、catalog IDs、site dispositions、entry coverage root、Stage02 root | Stage03 的 graph roots/candidate endpoints/coverage denominator 精确等于这些完整 ID 集合 |
| 03 | valid Stage01/02 roots；entry/catalog endpoints 可重开 | structure → call → CFG → data-flow → evidence → cross-graph validation | 五图 roots、exact node/edge IDs、entry ownership、graph Gaps | Stage04 只需 nodes/edges/evidence 枚举 Proof；Stage05 可复用 entry-root call/control/data edges |
| 04 | valid five-graph set；Fact registry 固定 denominator | candidate/atoms → source+rule Proof closure → admit/reject → Gap/accounting | admitted fact/atom IDs 与 Proof IDs，rejection/Gap refs，Stage04 root | Stage05 的可用语义只来自 admitted Facts；缺项已是 typed Gap而非隐含未知 |
| 05 | valid complete entry set、graphs、Facts/Proof/Gaps | 对每 entry 遍历 → Outcomes → ownership → per-Flow minimal projection → repository entry accounting | `N` 个 Flow/Outcome/Capsule IDs 或 entry GAP/EXCLUDED；每 Capsule 明列 R0/R1/R2 共用的 basis atom/Gap 与 spans；coverage、Stage05 root | Stage06 对每个 compiled Flow 得到恰一个 Capsule，并把它作为所有三轮唯一 source projection；一条 Flow 成功不能关闭其他 entry；0 Flow 精确推出 0 slot/call |
| 06 | valid全量 Flow↔Capsule 双射、optional organization registry seed、R0/R1/R2 prompt/schema/runtime policy | `N`个隔离R0 task/slot → lifecycle-bound R0 proposals → program validation/disposition → freeze唯一RepositoryInterpretationRegistry → 每Flow finite-key R1/R2 compile/lifecycle → canonical candidate/disposition/publication | 正常 `N` Flow产生 `N+2N=3N` slots/rounds/calls；每R0 proposal有basis/disposition/provisionalKey lineage，恰一冻结registry；每eligible Flow恰一最终disposition，READY绑定R1/R2 proposal/candidate，Gap/failed绑定typed refs；0 Flow精确为0/0/0，Stage06 root | Stage07 可纯重放**所有** registry proposals/dispositions、provisional keys、R1/R2 selected keys、Flow dispositions和receipts；无需 source、Provider 或未持久化 session state |
| 07 | valid full Facts/Flows、唯一Stage06 registry、R0→R1/R2 lineage、每 slice closure | per-slice deterministic admission/fallback decision → validate `registryProposalId→provisionalKey→selectedKey` → cross-Flow proven-anchor merge → conflict/owner/account | **恰一个** repositoryKnowledgeId，含全部 flow/decision/meaning/anchor/relation/metric/Gap/owner/conflict refs及registry lineage；每条lineage原样携带registry的`proposalKind/normalizedLabel/normalizedPurpose` | Stage08 只接这一份 RepositoryKnowledge；每个 ReaderItem 候选都有 typed semantic item、唯一 knowledge owner、可追到R0 basis的lineage和无需重开Stage06即可读取的规范业务值 |
| 08 | valid Stage01–07 roots、closed repository coverage ledger、唯一 RepositoryKnowledge、NineSectionProfile、run lineage | repository section ownership → one plan → one isolated render → Trace（含registry lineage）→ validation/archive → run-centric observation projection | 每 run **恰一份** exactly-nine plan、**恰一份** document.md、Trace root、UNPUBLISHED Candidate、run manifest；inspect/artifact/render/validate/trace按run identity返回 | Review/Trace/Selection/resume及Java/CLI/loopback HTTP各有完整immutable input，不需要回跑分析或模型；不存在per-Flow Markdown、Path查询或Adapter私读目录 |

组合成立的条件是：任何一行的 `artifactId + sha256 + controls + payload reference` 不匹配就停止在该边界；禁止下游用源码、当前内存对象、同名字符串或模型响应“修复”上游。这样每个 transformation 的输入都由上一 postcondition 唯一给出，且所有 identity 依赖保持无环。

### 3.4 同一 DepotHead identity 怎样贯穿

下面的 story key 只帮助阅读；生产 join 使用右列的 typed IDs，不新增一个可猜测的全局字符串键。

| 业务故事位置 | 生产字段链（字段名不得中途改义） |
| --- | --- |
| 固定 Controller/XML bytes | `VerifiedSnapshot.snapshotId` → `VerifiedFile.fileId/path/sha256` |
| POST 入口 | `EntryPoint.entryId` 保存 `routeEvidenceNodeIds`；Stage03 entry graph node保存同一个 `entryId/owningEntryIds` |
| Controller→Service→Mapper/XML | `ProgramEdge.edgeId` 的 endpoints 与 `evidenceNodeIds` 原样进入 atom Proof 的 `requiredProgramEdgeIds/requiredEvidenceNodeIds` |
| status 与 ids 事实 | `FactAtom.atomId` 原样进入 `Proof.atomId`、`FlowSlice.atomIds`、`EvidenceCapsule.allowedFacts/projectionObligations` |
| 完整请求过程 | `FlowSlice.flowSliceId` 原样进入 Capsule、RegistryProposalTask、RepositoryInterpretationRegistry item、FlowModelTask、InterpretationProposal、AdmittedFlowMeaning 和 RepositoryBusinessKnowledge.flow refs |
| 仓库特有业务词 | `BusinessRegistryProposal.registryProposalId/flowSliceId/basisAtomIds/basisGapIds/proposalKind/normalizedLabel/normalizedPurpose` 经程序 disposition 生成唯一 `provisionalKey`；R1/R2 的 `InterpretationProposal.selectedKey` 必须等于该 Flow registry item 的 provisionalKey；Stage07 `RegistryMeaningLineage`逐字段复制三项规范值 |
| 业务解释 | `registryProposalId → provisionalKey → InterpretationProposal.interpretationProposalId/selectedKey → meaningId` 原样进入 admission decision和RepositoryKnowledge；admitted meaning保留两类proposal IDs与basis refs |
| 九章读者项 | `ReaderItem.factIds/meaningIds/gapIds/registryProposalIds/interpretationProposalIds` 原样引用 knowledge；ADMITTED_TERM 的typed slots从同一`RegistryMeaningLineage.normalizedLabel/normalizedPurpose`逐字节复制；Trace先引用该`registryLineageId`，再从 readerItemKey 逐跳回 registry proposal/round receipt、basis IDs、Proof、Evidence、VerifiedFile 和 snapshotId |

示意故事从 `POST /depotHead/batchSetStatus` 的 `status` 输入到 `jsh_depot_head.status` ReaderItem 时，任何阶段都不能把 `status` 改成另一个字段、把 `ids` where 条件静默丢掉、或凭空加入“审核”业务词。“批量审核或反审核”只能先作为R0有basis的仓库词候选，经程序freeze得到provisionalKey，再被R1/R2有限选择并由Stage07准入；当前没有Flow时该链停在Gap，Stage08的ReaderItem引用gapId而不是伪registry item或meaningId。

### 3.5 全局 accounting 与 coverage

每个分母项恰有一个终态；alias/merge 必须保留反向引用，不等于删除：

~~~text
sourceInventoryPaths = verifiedFiles + sourceVerificationGaps + reasonedSourceExclusions
discoverySites = supportedSites + unsupportedSites + ambiguousSites + overLimitSites
discoveredEntries = compiledEntries + gappedEntries + excludedEntries
graphCandidates(kind) = exactNodesOrEdges + graphGaps + reasonedGraphExclusions
candidateFacts = admittedFacts + rejectedFacts
candidateAtoms = admittedAtomDispositions + rejectedAtomDispositions
outcomeCandidates = compiledOutcomes + gappedOutcomes + excludedOutcomes
stage04Gaps + stage05Gaps = knowledgeOwnedGaps + mergedAliasGaps
compiledFlowSlices = modelEligibleFlows + modelIneligibleFlowGaps
modelEligibleFlows = r0ReadyFlows + r0GappedFlows + r0FailedFlows
r0TaskSlots = r0TerminalRounds
r0RegistryProposals = acceptedRegistryProposals + rejectedRegistryProposals
r0ReadyFlows = r1r2EligibleFlows
r1r2TaskSlots = r1TerminalRounds + r2TerminalRounds
r1r2EligibleFlows = readyInterpretationFlows + r1r2GappedFlows + r1r2FailedFlows
modelEligibleFlows = readyInterpretationFlows + finalGappedInterpretationFlows + finalFailedInterpretationFlows
r0GappedFlows maps one-to-one into finalGappedInterpretationFlows and has zero R1/R2 slots
r0FailedFlows maps one-to-one into finalFailedInterpretationFlows and has zero R1/R2 slots
readyInterpretationFlows = flowInterpretationCandidates
stage06InterpretationProposals = keep + narrow + drop + needsEvidence + needsTermRegistry
acceptedRegistryProposals = repositoryInterpretationRegistryItems
repositorySemanticItems = readerOwnedItems + reasonedReaderExclusions
readerOwnedItems = exactlyOneSectionOwner
sections = exactlyNineOrderedSections
analysisRunDocuments = exactlyOneRepositoryNineSectionPlan + exactlyOneRepositoryMarkdown
~~~

所有等式两侧都保存 ID 列表和 count，validator 不只比较 count。正常无失败时 `N modelEligibleFlows = N R0 slots + 2N R1/R2 slots = 3N provider calls`；异常路径可少于3N calls，但未启动R1/R2的Flow必须由R0 Gap/FAILED disposition唯一解释，不能从分母消失。一个 ID 不能出现在两个互斥分子中；`mergedAliasGaps` 必须保存 canonicalGapId 和 memberGapIds；`reasoned*Exclusions` 必须有版本化 reason code，不能成为垃圾桶。图 node/edge、Fact atom、Outcome、Gap、registry proposal/provisional key、interpretation proposal、knowledge item 和 section owner 的 orphan/duplicate 都是 fatal。

这些不变量共同给出三个出口性质：Proof/identity closure 保证准确，Flow ownership + typed knowledge + fixed templates 保证可读而不串故事，ReaderItem→source 的无断链 Trace 保证可追溯。不支持语法、动态配置或静态不可知行为只能把对应 denominator item 移到 Gap/明确 exclusion，影响的是完整性；它们不能污染已证明内容的准确性，也不能从“待确认事项”外消失。

### 3.6 全局 identity DAG

唯一合法依赖方向是：run controls/source registration/optional organization registry seed → snapshot/file → application/entry/catalog → graph node/edge/evidence → candidate atom/fact → Proof → Outcome/Flow → Capsule → R0 task/round/registry proposal/disposition → frozen RepositoryInterpretationRegistry/provisional key → finite-key R1/R2 task/round/interpretation proposal/Flow disposition → admission decision/meaning/anchor → repository knowledge/ownership → ReaderItem/plan → document/Trace roots → Candidate/run manifest。后项可以引用前项，前项不得含后项 ID；publisher/receipt 只在 payload IDs 固定后计算。Trace record 不含 candidateId，Candidate 最后绑定 traceRoot，避免 Candidate↔Trace 环。R0不能引用R1/R2或meaning，registry item不能引用Stage07 identity；optional seed只有被某R0 proposal以`sourceSeedKey`引用并携带Capsule basis后才能进入冻结registry。

Candidate 只绑定 `upstreamStageRoots[7]`、plan/document/Trace 和 lineage；Stage08 `stageArtifactRoot`在Candidate等五个semantic payload固定后计算，不含archive-manifest、receipt或run-manifest。archive-manifest/receipt依序绑定前项且都不含自身，`run-manifest.stageRoots[8]`再包含Stage01–08 roots与receipt SHA但不含自身；最后由stage外M4 publication envelope列齐八个public files。Candidate不得预先引用Stage08 root，否则形成Candidate↔Stage08 root环。其他stage receipt的artifact list同样排除receipt自身；父级publication/run manifest才记录receipt文件SHA。

### 3.7 Repository completion gate

生产 run 的完成条件不是“DepotHead 或任意一个 Flow PASS”，而是一个 schema-versioned、content-addressed `RepositoryCoverageLedger` 闭合。它至少保存：

~~~text
RepositoryCoverageLedger
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  closed: BOOLEAN
  closureReasonCode: STRING?
  sourceFileIds[]
  discoverySiteIds[]
  entryIds[]
  graphCandidateIdsByKind{}
  factCandidateKeys[]
  atomIds[]
  outcomeCandidateIds[]
  flowSliceIds[]
  gapIds[]
  modelEligibleFlowIds[]
  registryProposalTaskIds[]
  registryProposalRoundIds[]
  registryProposalDispositionIds[]
  repositoryInterpretationRegistryItemIds[]
  provisionalKeys[]
  interpretationTaskIds[]
  interpretationRoundIds[]
  flowInterpretationDispositionIds[]
  flowInterpretationCandidateIds[]
  interpretationProposalIds[]
  interpretationDecisionIds[]
  repositoryKnowledgeItemIds[]
  readerSemanticItemIds[]
  sectionOwnerBySemanticItem{}
  stageCoverageRoots[8]
  shardReceipts[]
  equations[]
  repositoryCoverageLedgerId
~~~

`sourceScopeKind=COMPLETE_CAPTURE` 是 `repositoryCompletionEligible=true` 的必要条件；`BOUNDED_PATH_SET` 必须为 false。`closed=true` 还要求所有 `equations` 的左右 ID 集合相等、互斥分子不交叠、全部 stage/shard roots有效且没有孤儿；此时 `closureReasonCode=null`。任一条件未满足时 `closed=false` 且 `closureReasonCode` 必须是版本化非空 code。ledger ID覆盖上述字段、各排序ID集、disposition artifact refs、equations与roots，不能由count单独决定。

completion、诊断归档和 fatal 是三个互斥层级，不能把“归档模块写成功”误写成“run 已完成”：

| 层级 | 精确条件 | 允许结果 |
| --- | --- | --- |
| terminal completion | `COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、`closed=true`，且 identity/reference/security/cardinality/Trace 全部有效 | 无 Gap 为 `COMPLETE` / `VALID_COMPLETE`；全分母仍唯一处置且 Gap 不破坏闭包时为 `COMPLETED_WITH_GAPS` / `VALID_COMPLETE_WITH_GAPS`；可写 completion event并进入Selection |
| diagnostic archive | `BOUNDED_PATH_SET`，或 `COMPLETE_CAPTURE` 但 coverage ledger以非空closureReason和完整missing-ID/recovery accounting合法地保持`closed=false`；已归档子集自身的schema/root/reference/Trace仍完整 | 前者run state为`INCOMPLETE_SCOPE`，后者为`INCOMPLETE_COVERAGE`；都可持久化不可变诊断Candidate，validator统一报告`VALID_INCOMPLETE_SCOPE`，但它们非终态、不可Selection、不可写completion event |
| fatal / invalid | ledger不能解析或自相矛盾、分母未知、遗漏未显式列出、孤儿/重复/断引用、hash/root/security/cardinality/Trace或原子安装失败 | 不安装success artifact set或validator写`INVALID`；不能降级为诊断归档或业务Gap |

因此 `closed=false` 或 scope非COMPLETE_CAPTURE 本身不是 integrity fatal；**把它冒充 COMPLETE** 才是违反合同。反之，只有在受影响IDs、原因和恢复位置全部显式且已归档闭包仍可重验时才允许诊断归档；“不知道漏了什么”的不完整仍是 fatal。

每个 `entryId` 恰好是 `COMPILED(flowSliceId)`、`GAP(gapIds,reasonCode,evidenceRefs)` 或 `EXCLUDED(reasonCode,evidenceRefs)`；每个 `COMPILED` Flow 恰一个 EvidenceCapsule。每个 model-eligible Flow先恰一个R0 task/terminal disposition；全部R0分母处置后冻结**一份且仅一份**RepositoryInterpretationRegistry。R0 READY Flow才有该Flow-scoped provisional keys并进入R1/R2；每个model-eligible Flow最终仍恰一个`FlowInterpretationDisposition`：`READY`必须绑定恰一个完整两轮`FlowInterpretationCandidate`，`GAP`或`FAILED`必须绑定typed Gap/failure refs且candidateId为null。Stage07再为每个Flow写恰一个程序admission/fallback decision。Stage07将全部Flow dispositions、registry lineage与程序Facts/Gaps合成**一份且仅一份**RepositoryKnowledge；Stage08每个analysis run只从该knowledge生成**一份且仅一份**NineSectionPlan和document.md。禁止一Flow一Markdown，也禁止先渲染Markdown片段再文本拼接。

资源分片只能改变执行调度，不能改变范围或语义。shard key 固定为稳定 `fileId`、`entryId` 或 `flowSliceId`；每个 shard receipt 保存 denominator IDs、output IDs、controls 和 SHA。validator 要求 shard denominators 两两不相交，按 canonical ID union 后**精确等于**未分片分母；遗漏、重叠、first-N、sample 或超限截断均不允许 COMPLETE。缺 shard 可写 scoped Gap 并保留已完成 shards，但 `repositoryCoverageLedger.closed=false` 且 run 只能进入非终态 `INCOMPLETE_COVERAGE`；若缺失破坏引用/身份/安全则 fatal。只有完整分母里的每项都被唯一处置时，已支持但静态不可知的事项才可作为 typed Gap 随闭合 ledger 进入 `COMPLETED_WITH_GAPS`。

完整验收必须含至少两个不同入口、两个独立Flow/Capsule、两个隔离R0 proposal sets、一份冻结RepositoryInterpretationRegistry、两个`READY`有限key解释candidate/decision和一个跨Flow合并（含关系或指标、冲突或identity合并）；正常fixture的`N`个Flow各有一条完整`R0 proposal → provisionalKey → R1/R2 selectedKey → meaning` lineage，并精确产生`3N`started calls。另有R0失败、R0全拒绝与R1/R2失败fixture，证明每种都会成为typed Gap/FAILED disposition而不会吞掉Flow或用seed/别Flow补词。改变shard size、并行/完成顺序或恢复点后，所有stage public bytes、registry、RepositoryKnowledge、NineSectionPlan和document.md必须逐字节相同。一个Flow失败时已完成Flow artifacts保留；未开始的deterministic slice可安全重算，已started的R0/R1/R2 slot按Stage06 lifecycle永不重放。安全隔离且生命周期明确的模型失败由Stage06写终态Flow disposition，并可在全分母仍唯一处置时随闭合ledger进入`COMPLETED_WITH_GAPS`；身份、引用、安全或生命周期不明确仍为fatal。只有ledger全部分母均有唯一处置、所有stageCoverageRoots验证通过且单一仓库文档closure成立，run才可进入terminal COMPLETE/COMPLETED_WITH_GAPS；前者无Gap，后者仅含已显式计数且不破坏覆盖账本闭合的Gap。

## 4. Stage 01：冻结来源

### 为什么存在

所有后续 locator、图、Proof 和 Trace 都依赖“同一路径仍然是同一字节”。本阶段把上游 capture 与本地分析分开；它只验证调用方已明确选定的离线快照。

### 具体输入

固定仓库 identity、revision、capture receipt、**完整**声明文件 inventory、snapshot root、验证策略和预算。生产 run 的 `inventoryScope` 必须是 `COMPLETE_CAPTURE`；DepotHead walkthrough 使用固定 commit 的八文件 `BOUNDED_PATH_SET` 仅作局部 fixture，明确 `repositoryCompletionEligible=false`，不能使 run 完成。

### 工作步骤

1. 校验 origin、revision、capture receipt 和 inventory digest。
2. 规范化相对路径；拒绝绝对路径、逃逸、重复路径和 symlink。
3. 在分配前校验文件类型与大小，再流式重算 SHA-256。
4. 严格 UTF-8 解码文本并建立 byte/line index。
5. 计算 root-independent snapshot identity。
6. 自验全部引用后原子安装阶段目录。

### 可观察产物

- source-input.json
- verified-snapshot.json
- source-inventory.jsonl
- stage-receipt.json

### 下游如何消费

Stage 02 只读取 verified-snapshot.json 和 source-inventory.jsonl 中列出的 handle identity；它不能 walk 工作树、补文件或改路径解释。

### 成功、Gap、fatal 与角色

全部声明文件闭合才成功。局部超预算只有在 profile 明确允许隔离时形成范围 Gap；来源缺失、hash 漂移、路径越界或安全策略不可执行是 fatal。程序负责全部工作，LLM 角色为零。

## 5. Stage 02：发现应用类型和入口

### 为什么存在

文件冻结不等于知道它是什么应用，也不等于知道从哪里进入。先发现应用形态和入口，后续图构建才有明确根和能力边界。

### 具体输入

Stage 01 的 persisted snapshot、inventory、版本化 application-discovery profile 和预算。DepotHead 输入包含 pom.xml、application.yml、Controller、Service、entity、Example、Mapper Java 和 Mapper XML。

生产 Stage 02 必须遍历完整 Stage01 inventory 中所有受支持 discovery site；DepotHead route 只是全入口 inventory 中一项。任何入口 candidate 都必须形成 supported EntryPoint、带证据 EXCLUDED，或 typed Gap，不得因 parser/预算/分片失败从 denominator 消失。

### 工作步骤

1. 从构建声明与配置识别 Java 版本和候选框架；依赖出现只启用 parser，不证明行为。
2. 解析 Spring MVC 类级与方法级 mapping。
3. 发现入口方法、参数、返回类型及注解 locator。
4. 发现 MyBatis mapper location、namespace 和 statement 候选。
5. 对每个发现 site 给出 SUPPORTED、UNSUPPORTED、AMBIGUOUS 或 OVER_LIMIT disposition。
6. 计算应用 profile、入口清单和能力覆盖，然后原子安装。

### 可观察产物

- application-profile.json
- entry-points.jsonl
- mapper-catalog.jsonl
- capability-report.json
- stage-receipt.json

### 下游如何消费

Stage 03 直接读取入口和 catalog identity，按入口建立图根；它不重新解析 pom、配置或注解来“再发现一次”。

### 成功、Gap、fatal 与角色

一个入口可以带 capability Gap，但不能从分母消失。全局 parser 安全失败、source reopen 漂移或 catalog 自相矛盾为 fatal。程序负责，LLM 角色为零。

## 6. Stage 03：构建五张正式程序图

### 为什么存在

业务流程不是文件顺序。只有结构、调用、控制、数据和证据五类关系同时显式存在，程序才能证明“谁调用谁、在什么条件下、哪些值流到哪、为什么相信这条边”。

### 具体输入

Stage 01 snapshot 与 Stage 02 application/entry artifacts，加上版本化 graph profile、parser/toolchain identity 和预算。

### 工作步骤

1. 建 **代码结构图**：package、type、field、method、config、XML statement、SQL table/column 及 containment/declaration。
2. 建 **调用图**：receiver 静态类型、直接调用、Controller→Service、Service→Mapper、Mapper Java→XML statement。
3. 建 **控制流图**：entry、TRUE/FALSE、NEXT、CALL、RETURN、THROW/terminal 与明确 branch polarity。
4. 建 **数据流图**：definition/use、实参与形参、field setter/property、record/example 参数和 SQL placeholder/column。
5. 建 **证据图**：每个语义 node/edge 回到 source locator、span SHA、解析规则和 binding rule。
6. 逐图及跨图做引用、覆盖、唯一绑定和预算自验，再一次性安装五个一等产物。

### 可观察产物

- code-structure-graph.json
- call-graph.json
- control-flow-graph.json
- data-flow-graph.json
- evidence-graph.json
- graph-index.json
- graph-gaps.jsonl
- stage-receipt.json

### 下游如何消费

Stage 04 只遍历这五张 persisted graph 和 graph-index；它不重新打开 AST 来修补缺边。Stage 05 也复用控制/调用/数据图，不另写一套 parser。

### 成功、Gap、fatal 与角色

局部不支持或歧义边进入 graph-gaps.jsonl，并明确受影响入口。断引用、重复 canonical ID、数据流假绑定、XML 外部解析尝试或图账本不闭合为 fatal。程序负责，LLM 角色为零。

## 7. Stage 04：证明代码事实

### 为什么存在

一条图边存在，不等于复合业务陈述成立。本阶段枚举每个候选事实必须包含的全部语义原子，再逐原子证明；缺一项就拒绝整条复合 Fact，而不是写半句真话。

### 具体输入

五张正式程序图、snapshot、能力报告、版本化 Fact/Gap profile 和预算。DepotHead 的目标候选包括完整 HTTP route、状态输入、资格条件、Mapper 调用、status 数据流、表/列赋值和 id-in where 约束。

### 工作步骤

1. 先枚举 candidate Fact 与 required atoms，固定分母。
2. 从证据图取 source nodes，从结构/调用/控制/数据图取 required edges。
3. 为每个 atom 构造 Proof closure 并重验 source span。
4. 所有 required atoms 闭合后才 admission 整个 CodeFact。
5. 未闭合内容写入 fact rejection 或 Gap；已知但静态不可证明的政策进入 expectation Gap。
6. 重算 accounting 方程并原子安装。

### 可观察产物

- proven-facts.json
- proof-pack.json
- gap-ledger.json
- fact-accounting.json
- stage-receipt.json

### 下游如何消费

Stage 05 使用 CodeFact、Proof ID、Gap 和五图引用编译流程；它不能借用未被 Fact 引用的 Evidence，也不能把 EvidenceCapsule 当 Proof。

### 成功、Gap、fatal 与角色

Fact rejection 和非阻塞 Gap 可以属于成功结果；Proof reference 断裂、source reopen 漂移、冲突 Fact 同时 admitted 或 accounting 不闭合为 fatal。程序负责，LLM 角色为零。

## 8. Stage 05：编译完整业务流程和逐流程阅读包

### 为什么存在

事实列表还不能告诉读者一个入口怎样开始、经过哪些分支、在哪里结束。模型也不应看到整个仓库。本阶段对 Stage02 的**每个入口**给出唯一 disposition：支持的入口编译成完整入口根 Flow，并为每个 Flow 生成最小、封闭、预算内的 EvidenceCapsule；不支持/失败的入口保留证据和 Gap。DepotHead 只是 `N` 个 slice 中一个。

### 具体输入

Stage 02 入口清单、Stage 03 五图、Stage 04 Facts/Proof/Gaps、flow/evidence profiles 和预算。

### 工作步骤

1. 每个 entryId 建一个入口根遍历，沿唯一调用边和显式控制流边前进。
2. 保留 call/return 配对和每个 guard 的 TRUE/FALSE 极性。
3. 每个 return、throw 或受支持 stop 成为 OutcomePath；所有终点必须进入 compiled、Gap 或 reasoned exclusion。
4. 为 Fact、atom、Outcome 和 Gap 建 flow ownership；禁止第二入口借用不属于自己的事实。
5. 从 Proof root 投影直接表达业务语义的最小、非重叠 source spans，并显式列出R0/R1/R2可引用的basis atom/Gap集合。
6. 建 projection obligations，逐一证明删除任一 span 会损失至少一个义务；同一 Capsule 是该Flow三轮模型任务的唯一source projection。
7. 自验入口、Outcome、Fact、atom、Gap 和 Capsule 覆盖后原子安装。

### 可观察产物

- flow-slices.json
- flow-coverage.json
- entry-dispositions.jsonl
- evidence-capsules.jsonl
- flow-gaps.jsonl
- stage-receipt.json

### 下游如何消费

Stage06为Stage05的每个model-eligible Flow先建立一个隔离R0 registry-proposal工作项，再在全仓R0分母处置并冻结唯一RepositoryInterpretationRegistry后，为R0 READY的同一Flow建立有限key R1/R2工作项。三轮都只能读这一条FlowSlice和它唯一对应的EvidenceCapsule；optional organization seed只是R0的content-addressed候选词输入，不能替代Capsule basis。Stage06不能读仓库目录、五张全图、其他Flow、未准入Fact或任意本地路径；调度/分片可变，但Flow集合、三轮closure和最终canonical bytes不变。一条Flow成功不能结束Stage06或整个run。

### 成功、Gap、fatal 与角色

某入口无法闭合时可记录GAP disposition；0 Flow、0 Capsule仍可成为诚实的SUCCEEDED_WITH_GAPS阶段结果，并让后续生成零R0/R1/R2任务。引用断裂、coverage不守恒、Capsule source hash漂移或R0/R1/R2读取不同source projection为fatal。程序负责，LLM角色为零。

## 9. Stage 06：一次只解释一个流程

### 为什么存在

确定性分析能证明“发生了什么”，但预置词表无法覆盖每个新客户仓库。模型先在隔离R0为单个Flow提出有证据basis的bounded业务词、claim和question；程序把全仓R0结果验证、处置并冻结为唯一RepositoryInterpretationRegistry，随后模型只能在同一Flow的有限provisional keys里做R1解释与R2精度复核。模型始终不是编译器、证明器、准入者或文档作者。

### 具体输入

全量eligible Flow/Capsule双射、optional content-addressed organization registry seed、R0/R1/R2严格output schemas与prompt bundles、expected runtime identity和预算；执行时一次只取其中一对，跨Flow session永不共享。DepotHead当前0 Capsule，所以它产生0 task、0 Provider call，不妨碍同仓库其他eligible Flow独立处理。

### 工作步骤

1. 重验全部Flow/Capsule、R0 schema/prompt/runtime/budget和optional seed identity；为每个Flow编译恰一个隔离`R0_REGISTRY_PROPOSAL` task。
2. 按持久化slot lifecycle执行R0。模型只能返回bounded `proposalKind/label/purpose/basisAtomIds/basisGapIds/sourceSeedKey?`；程序验证后才产生`normalizedLabel/normalizedPurpose`，模型不能返回Fact、locator、Flow、Outcome、Markdown或Capsule外reference。
3. 程序逐proposal做exact schema、Unicode/size/control-character、basis closure、seed exact-match和identity校验；每个proposal唯一ACCEPTED/REJECTED disposition，跨Flow同名不静默合并。
4. 全部R0 slot终态后，程序按flow/proposal排序生成provisional keys并原子冻结一份RepositoryInterpretationRegistry；失败Flow仍在coverage中。
5. 只为R0 READY Flow按其同Flowregistry items编译有限key R1/R2；R1提出selectedKey+basis，R2在同一session逐项保持或收窄，不能增加来源、Fact、proposal、key或basis。
6. 每次模型调用前先持久化started event，之后才接收内容；R0、R1、R2使用不同slot identity，任何started slot都不重放。
7. 保存canonical tasks/responses、registry/dispositions、expected/observed runtime和lifecycle receipts；正常`N`个eligible Flow精确为`3N` slots/rounds/calls，本阶段不自批最终业务解释。

### 可观察产物

- registry-proposal-tasks.jsonl
- registry-proposal-rounds.jsonl
- registry-proposal-dispositions.jsonl
- repository-interpretation-registry.json
- flow-model-tasks.jsonl
- model-rounds.jsonl
- generation-receipts.jsonl
- interpretation-candidates.jsonl
- flow-interpretation-dispositions.jsonl
- stage-receipt.json

### 下游如何消费

Stage07读取canonical R0/R1/R2 rounds、唯一frozen registry、registry/interpretation proposal refs和Flow dispositions，逐项重算`registryProposalId→provisionalKey→selectedKey`及basis closure；它不重新调用模型，也不信任模型自报的decision。

### 成功、Gap、fatal 与角色

没有Flow时仍写出全部十个命名文件，其中JSONL为零行、registry为canonical empty、调用数为0。R0没有可接受item、R0/R1/R2安全隔离失败或合法但没有合适term时，为该Flow写typed Gap/FAILED disposition并让Stage07使用技术回退；不能从分母删除。schema、runtime identity、unknown/cross-Flow reference、seed漂移、R2 expansion或started后生命周期不明确为fatal；不得重试started slot、换Provider或回退API key。程序冻结任务、验证/冻结registry并校验传输；LLM在R0只提出bounded仓库词候选，在R1/R2只选择有限key。

## 10. Stage 07：准入解释并合并仓库业务知识

### 为什么存在

模型提案不是事实，多个 Flow 的同名对象也不一定是同一对象。本阶段对**全部 slice** 的候选逐一作 KEEP、NARROW、DROP 或 NEEDS_EVIDENCE 决定，再把全部程序 Facts/Gaps 与 admitted interpretations 按 Proof-backed technical anchor 合并成**一份且仅一份** RepositoryKnowledge，显式保存跨 Flow identity、关系、指标和冲突。

### 具体输入

Stage04 Facts/Proof/Gaps、Stage05 Flow/Capsule、Stage06 canonical R0/R1/R2 artifacts、唯一RepositoryInterpretationRegistry、registry proposal dispositions、knowledge profile和预算。

### 工作步骤

1. 对每个R1 interpretation proposal重新验证`registryProposalId→provisionalKey→selectedKey`、同Flow eligibility和basis；selectedKey必须逐字等于该registry item provisionalKey。
2. 用更保守的程序 decision 覆盖模型自评；开放文本不进入知识。
3. 没有 admitted term 时使用 total TechnicalDisplayRegistry。
4. 按 FQN、SQL table、Flow/Outcome ID 或 Proof-backed equivalence edge 合并；中文名或 simple name 不能作为 identity。
5. 保持 Proven Fact、Admitted Interpretation、Gap 三种知识等级。
6. admitted meaning保留registryProposalId、provisionalKey、interpretationProposalIds和R0/R1/R2 basis/receipt lineage；跨Flow同名只有Proof-backed anchor equivalence才可在本阶段合并。
7. 给每个atom、meaning、registry lineage和Gap唯一owner，解决或显式记录冲突。
8. 重算仓库级accounting后原子安装。

### 可观察产物

- admitted-flow-meanings.jsonl
- repository-business-knowledge.json
- knowledge-conflicts.jsonl
- knowledge-accounting.json
- merged-gaps.json
- stage-receipt.json

### 下游如何消费

Stage08只读repository-business-knowledge.json、merged-gaps.json和ownership/accounting，并通过Stage06/07 typed IDs编译registry-aware Trace；它不读raw reasoning，也不重新决定term。ReaderItem可展示已准入label，但必须携带registryProposalId/interpretationProposalId lineage。

### 成功、Gap、fatal 与角色

所有业务 term 被 DROP 仍可成功，技术显示保证文档可生成。未解决的业务问题保留为 Gap。双 owner、无 owner、anchor 冲突未处置或 registry 漂移为 fatal。程序拥有最终准入和 merge；LLM 在本阶段无执行角色。

## 11. Stage 08：九章、Markdown、Trace、归档、观察与恢复

### 为什么存在

唯一 RepositoryKnowledge 还需要稳定地分配到读者结构中，并与完整分析 lineage 一起保存。每个 analysis run 只生成一份仓库级 NineSectionPlan 和一份 document.md；只保存 document.md 不能证明它如何产生，也不能安全恢复中断运行。禁止逐 Flow 生成 Markdown 或拼接预渲染片段。

### 具体输入

前七阶段的 immutable artifacts、NineSectionProfile、section ownership/template profiles、Candidate series/round request、archive/trace/validation policies 和预算。

### 工作步骤

1. 将每个Fact atom、admitted meaning、registry lineage和Gap分配到唯一章节owner。
2. 生成typed ReaderItem和disposition；每个语义原子必须进入正文、技术依据、Gap或有理由排除，admitted label必须引用registryProposalId/interpretationProposalId。
3. 生成恰好九章的 nine-section-plan.json。
4. renderer **只读取 nine-section-plan.json**，输出 UTF-8/LF document.md；不得打开源码、模型 response 或 registry。
5. 编译reader item → knowledge → meaning → interpretationProposal/selectedKey → provisionalKey/registryProposal/R0 receipt/basis → Fact/Gap → Proof → Evidence → snapshot的typed Trace。
6. 保存前七阶段 roots、运行/模型回执、Candidate/series lineage 和 validation baseline。
7. 原子安装Candidate；只在complete/eligible/closed时追加run completion event，否则只追加diagnostic archive event。
8. 单一RepositoryAnalysisAgent按run identity提供start/inspect/resume/artifact/render/validate/trace；artifact查询禁止Path，所有Java/CLI/loopback HTTP Adapter共用core manifest/root/security规则。
9. validate/trace可在独立进程重开来源和所有阶段产物；resume从最后一个valid stage receipt继续，inspect/artifact/render不调用Provider或修改run。

### 可观察产物

- nine-section-plan.json
- document.md
- trace.jsonl
- candidate.json
- validation-baseline.json
- archive-manifest.json
- stage-receipt.json
- run-manifest.json

Stage 08 不吞并前七阶段目录；Candidate 和 run manifest 引用它们的 Merkle roots。这样 final archive 不再是唯一可观察状态。

### 下游如何消费

审阅者读document.md；Trace/validation工具读plan、trace、Proof和snapshot；Selection流程读immutable Candidate reference。Java、CLI和loopback HTTP调用者只学习同一个run-centric Interface：inspect看状态/coverage，artifact按identity取验证后的metadata或预算内完整bytes（超限全量拒绝），render从唯一plan确定性重验document。任何下游都不需要重新调用模型或遍历run目录。

### 成功、Gap、fatal 与角色

九章可以诚实呈现 Gap 和空业务覆盖，但不能静默丢失知识。只有 RepositoryCoverageLedger 对完整仓库闭合、唯一 knowledge→唯一 plan/document cardinality 成立才可完成 run；单一 Flow PASS 永远不足。缺章、多章、错序、atom loss、document hash drift、Trace 断裂、archive collision 或无法原子安装为 fatal。程序规划、渲染、归档和恢复；LLM 不写 Markdown。

## 12. 运行持久化与精确恢复

### 12.1 Stage receipt

每个成功或带 Gap 成功的阶段都有 stage-receipt.json，至少包含：

~~~json
{
  "schemaVersion": "stage-receipt-v1",
  "stage": 3,
  "status": "SUCCEEDED_WITH_GAPS",
  "runId": "analysis-run:<hex64>",
  "inputArtifactRoots": ["artifact-root:<hex64>"],
  "controls": {
    "inputSha256": "<hex64>",
    "toolchainSha256": "<hex64>",
    "profileBundleSha256": "<hex64>",
    "schemaBundleSha256": "<hex64>",
    "promptBundleSha256": null
  },
  "artifacts": [
    {"path": "code-structure-graph.json", "sizeBytes": 0, "sha256": "<hex64>"}
  ],
  "gapCount": 1,
  "stageReceiptId": "stage-receipt:<hex64>"
}
~~~

上例属于本文件统一声明的 **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**；sizeBytes=0 仅表示字段形状，不是实际 graph 大小。

`stage-receipt.json.artifacts`只列该stage的payload artifacts，不列receipt自身；`stageArtifactRoot`也只由这些payload bytes计算。receipt文件自己的SHA由后继stage publication或最终run-manifest记录，从而避免receipt自引用。最终`run-manifest.json`必须嵌入或content-addressed引用`RepositoryCoverageLedger`，并把Stage01–08 roots、ledger ID/SHA、唯一RepositoryInterpretationRegistry/RepositoryKnowledge/plan/document IDs与精确`runState`一起绑定。

### 12.2 Resume 规则

1. 找到编号最大的 valid stage-receipt.json。
2. 重算该阶段全部 artifact size/SHA 和 artifact root。
3. 将 run-request.json 的 input/tool/profile/schema/prompt hashes 与 receipt 逐字段比较。
4. 完全相等才可从下一阶段继续；任一差异创建新 run identity，不能覆盖旧 run。
5. 当前阶段若只有 failure receipt，不冒充 success；上游 immutable stage 目录继续保留。
6. Stage06的R0/R1/R2任何started slot失败后都不重放；恢复读取persisted lifecycle state。R0全部terminal后才可冻结registry；已安装registry按ID/SHA复用且永不原位增补，部分R0结果不能冒充registry。
7. R0 READY以前不创建该Flow的R1/R2 slot；R0 Gap/FAILED恢复时直接复用终态Flow disposition。已冻结registry后，R1/R2只能使用其中同Flow provisional keys，seed/prompt/schema/runtime漂移一律新run。
8. 恢复只可使用ledger已闭合shard的canonical receipts；未开始deterministic shard可重跑，已started model slot不可重放。只有完整repository denominator重新union闭合时才可写terminal completion。
9. `inspect/artifact/render/validate/trace`只重开并验证已持久化bytes；它们不迁移、补写或修复旧run，不调用Provider，也不改变resume point。只有`start/resume`可推进run lifecycle。

## 13. 技术参考

前面已经说明每阶段为什么存在、输入、过程、产物、下游、失败和角色；本节才固定精确 records、interfaces、identity、算法、预算、安全和测试目标。

### 13.1 目标外部 Interface

目标seam是“管理并观察一次可信分析运行”。单一`RepositoryAnalysisAgent`隐藏run目录布局、manifest/root join、validation顺序、renderer隔离、resume lifecycle与查询安全；Java、CLI和loopback HTTP都是这个deep module的Adapter，不得各自遍历filesystem或重写规则：

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

方法语义固定：

- `start` 接收完整content-addressed request并创建/推进一个run；CLI用户命令叫`analyze`，不是第二套语义。
- `inspect`只投影run state、stage/module receipts、coverage/Gap/failure summaries和可查询artifact descriptors；不返回secret、raw prompt/reasoning或任意Path。
- `resume`只按12.2推进唯一安全恢复点；controls漂移、started slot或schema不匹配均按稳定code停止。
- `artifact`只接受`runId+artifactId` canonical identity，先从run-manifest和stage/module roots解析、重验SHA/schema/size，再返回metadata或预算内的**完整**UTF-8 bytes；超过query/server ceiling就以稳定错误拒绝，绝不截断。禁止Path、basename、glob或目录遍历。
- `render`只定位该run唯一persisted NineSectionPlan，以plan-only renderer在内存重渲染并与已安装document SHA比较，返回不可变document reference；不产生第二份plan/document、不补写run、不读source/registry/model。
- `validate`fresh-process式重算全run closure并把receipt写到run外append-only validation目录；validator execution失败与`INVALID`结果分开。
- `trace`只在对应Candidate已有匹配validation receipt后返回typed hops；source locator需重新通过source registry/root验证。

Adapter一一映射，名称与route不得由实现者重新选择：

| Core Interface | CLI Adapter | loopback HTTP Adapter |
| --- | --- | --- |
| `start(request)` | `analyze --request <json>` | `POST /analysis-runs` |
| `inspect(runId)` | `inspect --run <id>` | `GET /analysis-runs/{runId}` |
| `resume(runId)` | `resume --run <id>` | `POST /analysis-runs/{runId}:resume` |
| `artifact(query)` | `artifact --run <id> --artifact-id <id> [--stage <n>] [--module <name>] [--type <type>] [--sha256 <hex>] [--metadata-only \| --max-bytes <n>]` | `GET /analysis-runs/{runId}/artifacts/{artifactId}`；`expectedStage/expectedModule/expectedType/expectedSha256/contentMode/maxBytes`只能是bounded query参数 |
| `render(runId)` | `render --run <id>` | `POST /analysis-runs/{runId}:render` |
| `validate(runId)` | `validate --run <id>` | `POST /analysis-runs/{runId}:validate` |
| `trace(query)` | `trace --run <id> --reader-item <key>` | `GET /analysis-runs/{runId}/trace?readerItemKey=<key>` |

HTTP只绑定loopback且必须认证；request只含ID/enum/bounded scalar，不接受Path。Java/CLI/HTTP对同一frozen run必须返回相同canonical error code、identity、ordering和content SHA；Adapter只做transport/encoding，不能绕过core、改变budget或把diagnostic state升级为COMPLETE。

现有`CodeToMarkdownAgent`可以作为兼容Adapter或委托者，但其方法集合不是架构公理。稳定Interface是上述七个run-centric方法及其完整前置、不变量、错误和资源语义。

### 13.2 核心 records

~~~text
AnalysisRunRequest
  sourceRegistrationId
  frozenRepositoryRequest
  profileBundleRef
  toolchainRef
  schemaBundleRef
  promptBundleRef
  organizationRegistrySeedRef?             // nullable, content-addressed optional seed
  resourceBudget
  readerCandidateRound
  parentCandidateId

ProgramGraph
  graphKind
  snapshotId
  nodes[]
  edges[]
  coverage
  graphId

CodeFact
  factId
  kind
  subjectNodeIds[]
  atoms[]

Proof
  proofId
  factId
  atomId
  requiredEvidenceNodeIds[]
  requiredProgramEdgeIds[]
  ruleIds[]
  status=CLOSED

FlowSlice
  flowSliceId
  entryId
  trigger
  steps[]
  outcomePaths[]
  factIds[]
  atomIds[]
  gapIds[]

RegistryProposalTask
  taskSpecId
  taskKind=R0_REGISTRY_PROPOSAL
  flowSliceId
  evidenceCapsuleId
  isolatedSessionKey
  organizationRegistrySeedRef?
  inputJsonSha256
  outputSchemaSha256
  promptBundleSha256
  expectedRuntime

BusinessRegistryProposal
  registryProposalId
  taskSpecId
  flowSliceId
  evidenceCapsuleId
  proposalKind: BUSINESS_TERM | CLAIM | QUESTION
  normalizedLabel
  normalizedPurpose
  basisAtomIds[]
  basisGapIds[]
  sourceSeedKey?

RegistryProposalDisposition
  registryProposalDispositionId
  flowSliceId
  disposition: READY_FOR_FREEZE | GAP | FAILED
  registryProposalIds[]
  gapIds[]
  failureRef?
  reasonCode?

RepositoryInterpretationRegistry
  repositoryInterpretationRegistryId
  stage05Root
  organizationRegistrySeedRef?
  eligibleFlowSliceIds[]
  items[]
    provisionalKey
    registryProposalId
    flowSliceId
    evidenceCapsuleId
    proposalKind
    normalizedLabel
    normalizedPurpose
    basisAtomIds[]
    basisGapIds[]
    sourceSeedKey?
  registryProposalDispositionIds[]
  r0FlowDispositions[]
  coverage

InterpretationProposal
  interpretationProposalId
  flowSliceId
  registryProposalId
  provisionalKey
  selectedKey                              // exact same-flow provisionalKey
  basisAtomIds[]
  basisGapIds[]
  r2Decision: KEEP | NARROW | DROP | NEEDS_EVIDENCE

FlowInterpretationCandidate
  candidateId
  flowSliceId
  evidenceCapsuleId
  r1RoundId
  r2RoundId
  interpretationProposalIds[]

FlowInterpretationDisposition
  flowSliceId
  disposition: READY_FOR_ADMISSION | GAP | FAILED
  candidateId?
  gapIds[]
  failureRef?
  reasonCode?

EvidenceCapsule
  evidenceCapsuleId
  flowSliceId
  allowedFacts[]
  allowedGaps[]
  registryProposalBasisAtomIds[]
  registryProposalBasisGapIds[]
  modelEvidenceSpans[]
  projectionObligations[]
  budgetUsage

RepositoryBusinessKnowledge
  repositoryKnowledgeId
  repositoryInterpretationRegistryId
  sourceScopeId
  flowSliceIds[]
  objects[]
  activities[]
  flows[]
  outcomes[]
  fields[]
  relations[]
  formulas[]
  questions[]
  facts[]
  admittedMeanings[]
  registryLineage[]
    registryLineageId
    registryProposalId, provisionalKey
    interpretationProposalId, selectedKey, meaningId, flowSliceId
    proposalKind, normalizedLabel, normalizedPurpose
    basisAtomIds[], basisGapIds[]
  gaps[]
  ownership[]
  conflicts[]

RepositoryCoverageLedger
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  closed: BOOLEAN
  closureReasonCode: STRING?
  sourceFileIds[]
  discoverySiteIds[]
  entryIds[]
  graphCandidateIdsByKind{}
  factCandidateKeys[]
  atomIds[]
  outcomeCandidateIds[]
  flowSliceIds[]
  gapIds[]
  modelEligibleFlowIds[]
  registryProposalTaskIds[]
  registryProposalRoundIds[]
  registryProposalDispositionIds[]
  repositoryInterpretationRegistryItemIds[]
  provisionalKeys[]
  interpretationTaskIds[]
  interpretationRoundIds[]
  flowInterpretationDispositionIds[]
  flowInterpretationCandidateIds[]
  interpretationProposalIds[]
  interpretationDecisionIds[]
  repositoryKnowledgeItemIds[]
  readerSemanticItemIds[]
  sectionOwnerBySemanticItem{}
  stageCoverageRoots[8]
  shardReceipts[]
  equations[]
  repositoryCoverageLedgerId

RunInspection
  runId
  runState
  analysisRunRequestId
  repositoryCompletionEligible
  repositoryCoverageLedgerId
  repositoryCoverageClosed
  stageReceipts[]                           // stage order
  moduleReceipts[]                          // stage, module order
  artifactDescriptors[]                     // stage, module, artifactType, artifactId
  gapSummaries[]                            // gapId order, safe fields only
  failureSummaries[]                        // failureId order, safe fields only

StageArtifactQuery
  runId
  artifactId                                // required canonical identity
  expectedStageNumber?                      // optional discriminator, never locator
  expectedModuleName?
  expectedArtifactType?
  expectedSha256?
  contentMode: METADATA_ONLY | COMPLETE_UTF8
  maxBytes                                  // 0 for metadata; positive all-or-error ceiling otherwise

StageArtifactView
  runId
  stageNumber
  moduleName?
  artifactType
  schemaVersion
  artifactId
  sha256
  sizeBytes
  mediaType: application/json | application/x-ndjson | text/markdown
  validationState: MANIFEST_VERIFIED | FULLY_VALIDATED
  immutableReference
  contentUtf8?                              // null for metadata; never truncated

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

TraceQuery
  runId
  readerItemKey
  expectedCandidateId?
  maxHops
~~~

`StageArtifactQuery`必须同时有non-null `runId+artifactId`；四个`expected*`字段若给出，只做manifest membership/discriminator校验，任一不等即`ARTIFACT_IDENTITY_MISMATCH`，绝不作为“找最近文件”的替代selector。任何record都不含filesystem Path。数组按上述注释或stable ID canonical排序；`COMPLETE_UTF8`要求positive `maxBytes`且不得超过server ceiling，artifact大于任一ceiling时返回`ARTIFACT_RESPONSE_BUDGET_EXCEEDED`而不返回partial content。统一source locator使用canonical repository-relative path、UTF-8 byte offsets、1-based line/column和exclusive end；机器records禁止basename-only locator。

### 13.3 Canonical JSON 与 identity

所有模块 artifact 使用同一 envelope；各 stage doc 固定 payload schema 和 filename：

~~~text
ModuleArtifact<T>
  schemaVersion: STRING                    // required, exact artifact schema
  artifactType: STRING                     // required, registered enum value
  artifactId: STRING                       // required, computed as below
  producer:
    stage: INTEGER                         // required, 1..8
    module: STRING                         // required, exact module key
    moduleVersion: STRING                  // required
  upstreamArtifacts[]:
    artifactId: STRING                     // required
    sha256: HEX64                          // required, bytes of exact upstream file
  controls:
    toolchainSha256: HEX64                 // required
    profileSha256: HEX64                   // required
    schemaBundleSha256: HEX64              // required
    promptBundleSha256: HEX64?             // required nullable; non-null only where used
  completion:
    status: SUCCEEDED | SUCCEEDED_WITH_GAPS
    gapRefs[]: STRING                      // required, may be empty, sorted unique
    failureRef: STRING?                    // required nullable; always null for installed success
  payload: T                               // required, schema fixed in stage doc
~~~

失败不安装 success envelope，而写外部：

~~~text
ModuleFailure
  schemaVersion=module-failure-v1
  runId
  stage
  module
  attemptedUpstreamArtifacts[]
  controls
  failureCode
  safeDetail
  failureId
~~~

模块 artifact 的唯一 identity 规则是：`artifactId = <type-prefix>:SHA-256(UTF-8(schemaVersion) + LF + canonicalJson(envelopeWithoutArtifactId))`。`envelopeWithoutArtifactId` 是上面完整 `ModuleArtifact<T>` 删除且只删除顶层 `artifactId` 字段后的 object；它仍包含 `schemaVersion`、`artifactType`、producer、upstream IDs/digests、controls、completion 和 payload。前置的 `schemaVersion + LF` 是显式 domain separator，envelope 内的同名字段仍参与 canonical JSON；实现不得把 preimage 缩成 payload、漏掉 controls/completion，或用 null/空串占位 self ID。`upstreamArtifacts` 按 artifactId 排序，`gapRefs` 按 Gap ID 排序并去重；payload 数组的顺序由各 schema 明示，未声明 unordered 的数组保持业务顺序。此处选择并澄清本节原先先声明的完整-envelope规则，不改变字段或既有 schemaVersion。

- JSON 为 UTF-8、无 BOM、LF；object key canonical 排序；number 使用 schema 允许的唯一表示。
- JSONL 每行一个 canonical object并以 LF 结束；顺序由稳定 semantic key 定义。
- 数组只有在合同声明 unordered 时按稳定 ID 排序；控制流、步骤和章节顺序保持语义顺序。
- artifactId 只按上一段完整 `envelopeWithoutArtifactId` 公式计算；不存在第二套“payload-only”公式。
- graph、Fact、Proof、Flow、Capsule、task、meaning、knowledge、plan、Candidate 的 identity 依赖顺序必须无环。
- 时间、绝对 root、临时目录、线程顺序、异常 message、raw reasoning 和 secret 不进入内容 identity。

Schema 演进 fail closed：字段名、类型、必填/可空、来源规则、排序、identity material 或语义任何变化都先修改详细设计并发布新的 `schemaVersion`；reader 按 exact version 解析，未知 version/field 拒绝。允许增加字段也必须先设计+版本化，不能在同一 version 下做“向后兼容”猜测；旧 artifacts 保持可读且不可原位迁移。禁止删除必需字段、重解释字段、从其他文件补默认或绕过 upstream artifact。

### 13.4 关键算法不变量

1. 发现分母先于成功分子：entry/site/Fact atom/Outcome 都不能因失败而消失。
2. 调用唯一绑定需要 receiver/static type、method candidate 和版本化 rule；simple name 不足。
3. 控制流每个 guard 保存 polarity；终点列表或源码行序不能替代 CFG。
4. 数据流跨 Controller/Service/Mapper/XML 时逐段保存 definition/use/argument/property/placeholder binding。
5. Evidence graph 证明“图从何而来”；Proof 证明“这些图和字节为什么支持这个 atom”；Trace 只负责查询链，三者不互相冒充。
6. EvidenceCapsule 是模型阅读投影，不是 ProofPack。
7. Stage 06 的 R0_REGISTRY_PROPOSAL 只提出有 Capsule basis 的仓库特定词、claim 和 question；程序先验证并冻结唯一 `RepositoryInterpretationRegistry`。之后 R1/R2 才能在同一 Flow 的 finite provisional keys 中选择。R0/R1/R2 属于三个独立 lifecycle slot；R1/R2 属于同一产品 Candidate，ReaderCandidateRound 1/2 最多两份 Candidate，三者不能混用。
8. Markdown renderer 的唯一输入是 nine-section-plan.json。
9. `N` 个 compiled Flow 精确产生 `N` 个 Capsule 和 `N` 个 R0 disposition；全部 R0 slots 终态后精确冻结 1 个 `RepositoryInterpretationRegistry`。R0 READY 子集才各执行 R1/R2，并最终使全部 `N` 个 Flow 精确产生 `N` 个 FlowInterpretationDisposition；其中 `READY_FOR_ADMISSION` 子集一一产生 Candidate。Stage07 再为全部 `N` 个 Flow 一一产生 admission/fallback decision并精确产生1个RepositoryKnowledge；Stage08每run精确产生1个NineSectionPlan和1个document.md。正常 `N` Flow 是 `N+2N=3N` slots/calls；任何少于3N都必须由逐slot disposition解释。
10. Markdown 只能由仓库级 plan 一次渲染；不得先生成 per-Flow Markdown、再做文本拼接或让 renderer 读取多个 fragment。
11. 分片 receipts 的 denominator ID 集合必须不交叠且 union 等于未分片完整分母；资源上限只能产生可计数 Gap/fatal，不得变成 truncation/sample/first-N。

### 13.5 预算

Profile 至少固定：

- maxFiles、maxTotalBytes、maxFileBytes；
- maxAstNodes、maxXmlNodes、maxSqlChars、maxTraversalDepth；
- maxGraphNodes/Edges 分图上限；
- maxCandidateFacts、maxAtoms、maxProofEdges；
- maxFlows、maxOutcomesPerFlow、maxCapsules；
- maxSpansPerCapsule、maxSpanBytes、maxCapsuleUtf8Bytes；
- maxRegistryProposalTasks、maxRegistryProposalResponseBytes、maxRegistryItems、maxRegistryLabelUtf8Bytes、maxRegistryPurposeUtf8Bytes；
- maxInterpretationTasks、maxInterpretationResponseBytes；每个 eligible Flow 恰好一个 R0 slot，R0 READY 的 Flow 恰好 R1/R2；
- maxKnowledgeItems、maxReaderItems、maxArchiveFiles/Bytes。

超限不得截断后声称 COMPLETE。可安全隔离时进入 Gap并在 RepositoryCoverageLedger 记录原 denominator、受影响 IDs 和恢复 cursor；破坏引用、覆盖或完整性时 fatal。分片只允许按稳定 fileId/entryId/flowSliceId 调度，不能降低覆盖或改变 canonical 输出。

### 13.6 安全

- 只读声明快照；不 clone/fetch/pull，不运行客户 Maven/Gradle、插件、测试、脚本、应用、SQL 或 MyBatis runtime。
- 所有路径逐段 NOFOLLOW；普通文件在分配前做 size gate，读取后复查；目录在收集 limit+1 前停止。
- MyBatis DOCTYPE 可以存在，但 external DTD、general/parameter entity、schema 和所有网络 resolver 必须禁用；无法执行策略即 fatal。
- 模型只看到 Capsule canonical JSON；源码中的 prompt injection 只是 evidence data。
- R0 label/purpose 是不可信 JSON data：NFC、控制字符和双向覆盖检查、长度/字节/basis闭包都由程序执行；它们绝不成为 prompt 指令、Fact、locator、Flow 或 Markdown。可选组织 seed 只有被 R0 明确 `sourceSeedKey` 引用、值逐字段相等且同一 Capsule basis 非空时才可进入冻结 registry。
- configuredAdapterId、configuredAuthMode、expected/observed upstream provider、model、reasoning effort、sandbox 分字段记录。
- `RepositoryAnalysisAgent` 的 artifact/trace 查询只接受 run 与 typed identity；Java API、CLI 与 loopback HTTP 都不得接受或回显主机 `Path`。HTTP 只绑定 loopback 且每个请求校验 run-scoped bearer capability；inspect/artifact/render/validate/trace 不调用 Provider、不执行客户代码。
- secret、环境变量值、绝对本地路径、raw stderr 和 reasoning 不进入 Candidate、异常或正文。

### 13.7 稳定 failure code 家族

| 阶段 | 目标 code 家族 |
| --- | --- |
| 01 | REQUEST_SCHEMA_INVALID、CAPTURE_IDENTITY_INVALID、SOURCE_PATH_INVALID、SYMLINK_FORBIDDEN、SOURCE_HASH_MISMATCH、SOURCE_UTF8_INVALID、STAGE01_RESOURCE_LIMIT_EXCEEDED |
| 02 | APPLICATION_PROFILE_UNRESOLVED、ENTRY_DISCOVERY_INVARIANT_BROKEN、MAPPER_CATALOG_AMBIGUOUS、CAPABILITY_ACCOUNTING_BROKEN |
| 03 | GRAPH_REFERENCE_BROKEN、CALL_TARGET_AMBIGUOUS、CFG_POLARITY_MISSING、DATA_FLOW_BINDING_UNPROVEN、EVIDENCE_GRAPH_INVARIANT_BROKEN、XML_EXTERNAL_RESOLUTION_ATTEMPT |
| 04 | PROOF_REFERENCE_BROKEN、PROOF_SOURCE_REOPEN_MISMATCH、FACT_ACCOUNTING_INVARIANT_BROKEN、CONFLICTING_FACTS |
| 05 | FLOW_OUTCOME_CLOSURE_BROKEN、FLOW_ACCOUNTING_INVARIANT_BROKEN、EVIDENCE_PROJECTION_UNSATISFIABLE、CAPSULE_BUDGET_EXCEEDED |
| 06 | REGISTRY_PROPOSAL_TASK_INVALID、REGISTRY_PROPOSAL_RESPONSE_INVALID、REGISTRY_PROPOSAL_REFERENCE_INVALID、REGISTRY_SEED_MISMATCH、REGISTRY_FREEZE_INCOMPLETE、REGISTRY_IDENTITY_COLLISION、MODEL_TASK_INVALID、MODEL_RESPONSE_INVALID、MODEL_REFERENCE_INVALID、MODEL_REVIEW_EXPANDED、MODEL_RUNTIME_IDENTITY_MISMATCH、PROVIDER_FAILURE_AFTER_START |
| 07 | INTERPRETATION_ADMISSION_INVALID、TECHNICAL_FALLBACK_NOT_TOTAL、KNOWLEDGE_OWNER_INVALID、KNOWLEDGE_CONFLICT_UNRESOLVED |
| 08 | NINE_SECTION_INVALID、READER_ATOM_LOSS、DOCUMENT_HASH_MISMATCH、TRACE_CLOSURE_BROKEN、ARCHIVE_IDENTITY_COLLISION、RUN_RESUME_CONTROL_MISMATCH、RUN_NOT_FOUND、ARTIFACT_QUERY_INVALID、ARTIFACT_NOT_FOUND、ARTIFACT_IDENTITY_MISMATCH、ARTIFACT_RESPONSE_BUDGET_EXCEEDED、RENDER_NOT_AVAILABLE、RUN_NOT_VALIDATABLE |

具体实现可以保留更细 code，但不能把 integrity/fatal 降级成普通 Gap。

### 13.8 验证与测试目标

- 每阶段有正例、缺边/缺 span/漂移/预算 mutation 和不同 root 的 determinism 测试。
- 五张图分别有引用闭包、覆盖和跨图 edge mutation 测试。
- DepotHead walkthrough 必须先补齐通用数据流/Fact 能力，再以 real fixed slice 做正向验收；不得用目标 JSON 当 golden。
- 0 Flow/0 Capsule 有直接测试，断言 0 Provider call 且 Gap 仍进入九章计划。
- 至少两个入口/Flow 的 repository fixture 验证：每 Flow 独立 Capsule、独立 R0 proposal/disposition、一个全仓 frozen registry、同 Flow finite-key R1/R2 和 Stage06 disposition；正常分支必须精确 `3N` slots/calls。READY子集独立candidate，Stage07每Flow独立decision且全仓只有一个跨 Flow RepositoryKnowledge，Stage08只有一个九章plan/document；任何per-Flow Markdown或片段拼接应失败。
- R0 测试覆盖 novel repository term、同 label 不同 Flow 不误合并、optional seed exact-match/拒绝、basis缺失、非法 Unicode/控制字符、预算、单 Flow R0失败后其他 Flow bytes不变，以及 registry freeze 的排序/identity/恢复 determinism；R0不得产生 Fact/locator/Flow/Markdown。
- 同一 multi-flow fixture 改变 shard size、并行顺序、一个 Flow 先失败后恢复，最终 stage bytes/knowledge/plan/document 必须一致；缺 shard、重叠 shard、单 Flow PASS 冒充 run complete 均 fail closed。
- generation/replay 对相同 canonical rounds 产生相同 admitted knowledge、plan 和 Markdown bytes。
- renderer 测试用只含 nine-section-plan.json 的隔离目录，证明它没有 source/model读取能力。
- 独立 validation 重开 snapshot，逐项重算 source、graphs、Proof、Capsule、rounds、knowledge、plan、document 和 Trace roots。
- 同一 `RepositoryAnalysisAgent` 通过 Java、CLI、loopback HTTP 三种 Adapter 做 contract conformance：start/inspect/resume/artifact/render/validate/trace 一一同义；artifact 用 run+artifact identity、拒绝Path/越权/截断；所有观察方法零 Provider call，crash/resume 后返回相同 bytes/digests。
- 文档-only work 不需要运行这些测试；它们是后续实现合同。

### 13.9 已冻结的架构裁决

后续实现 Agent 可以选择内部类拆分、集合实现、parser library Adapter 和性能优化，但不得自行改变下列产品/架构决定；若现实材料无法满足，输出 Gap/fatal 或提交新的设计变更，不能在代码里暗改合同。

| 已冻结裁决 | 不允许实现者自行推断的替代方案 |
| --- | --- |
| 唯一主线是 01→08，成功边界是 immutable persisted stage artifact set | 不把阶段重新合并成内存 pipeline，也不跳过阶段“直接生成文档” |
| Stage 03 恰有五张一等图；Stage 04 的 Fact 必须逐 atom CLOSED Proof | 不用 repository blob、文件顺序、字符串相似或模型判断代替 graph/Proof |
| 每个 compiled entry 恰一个入口根 Flow，每个 Flow 恰一个 Capsule，多个终点是 Outcomes | 不按终点拆 Flow，不让模型自行找入口、分支或补源码 |
| 目标范围是完整冻结仓库；DepotHead 只是一条 walkthrough/fixture | 不把八文件 BOUNDED_PATH_SET、单入口或单 Flow PASS 当 repository completion；不得因分片/预算静默降覆盖 |
| LLM 只在 Stage 06、只读单 Flow Capsule：R0可提出 bounded open label/purpose，程序验证并冻结一个 registry；同 Flow R1/R2只返回其 finite provisional keys；Stage 07 程序最终准入 | 不让 R0 创建 Fact/locator/Flow/Markdown，不允许跨 Flow context、开放事实总结、模型自批或后续阶段补调模型 |
| 每个 eligible Flow 恰一个隔离 R0 slot；全部R0终态后恰一 frozen RepositoryInterpretationRegistry；R0 READY 才恰有R1/R2 | 不从组织seed直接绕过R0，不在registry freeze前编R1/R2，不把3N预算压回2N或重放started slot |
| `N` FlowSlice → `N` Capsule/Stage06 disposition → READY子集candidate → `N` Stage07 decision → 恰1 RepositoryKnowledge → 恰1九章plan/document | 不一 Flow 一 Markdown，不预渲染片段再拼接，不为某个失败 Flow另开旁路文档 |
| 0 Flow/0 Capsule 是合法带 Gap 结果且 Provider 调用数必须为 0 | 不为了“非空文档”伪造 Flow、Capsule 或模型解释 |
| Stage 08 计划恰九章；renderer 唯一输入是 `nine-section-plan.json` | 不让 renderer 回读 source、Proof、registry 或 model response，也不临时补章 |
| Candidate 始终 `UNPUBLISHED_CANDIDATE`，Selection/发布是后续显式流程 | 不自动发布，不把 analysis success 等同于业务批准 |
| resume 逐字段匹配 input/tool/profile/schema/prompt hashes，并重验 artifact roots | 不做宽松版本兼容、隐式迁移、覆盖旧 run 或重放 started model round |
| 单一 run-centric `RepositoryAnalysisAgent` 固定 start/inspect/resume/artifact/render/validate/trace，Java/CLI/loopback HTTP逐项同义 | 不暴露run目录或Path，不让Adapter各自发明状态/错误/渲染语义，不用final-only API替代阶段可观察性 |
| run completion 必须由 RepositoryCoverageLedger 证明所有文件/site/entry/graph/fact/outcome/flow/proposal/knowledge/reader owner有唯一处置 | 不因部分成功、计数相等但 ID 集不等、缺 shard 或 omitted item 宣布 COMPLETE |
| target design 优先于当前类/测试；当前 DepotHead 仍是 Gap、0 Flow、0 Capsule | 不为复用 `CodeToMarkdownAgent` 或通过现有 fixture 而降低目标 |

每份阶段文档进一步冻结该阶段的输入、输出文件、records、算法、Gap/fatal、安全/预算和验收 seam。实现工作只剩编码与已指定合同内的工程取舍；新增 schema 字段、状态、业务术语、自动重试、合并规则或降级策略都必须先改设计。

阶段内部的每个命名模块也使用同一九项合同：解决的问题；精确上游输入/前置；确定性处理顺序与 LLM 角色；目标输出结构和 DepotHead 示例；不变量；Gap/fatal/恢复；下游后置保证；明确非目标；公共测试 seam/验收。模块不能依赖同阶段总述来省略其中任一项，模块连接只能按各 stage doc 声明的顺序与 typed records 发生。

### 13.10 Agent 执行纪律

目标实现采用固定 Agent handoff，不把设计选择留给实现 Agent：

1. **Luna/xhigh 写 RED**：只对一个模块的 public Interface/record/artifact seam 写测试；从 detailed stage doc 的 fixture、independent expected values 和 failure code 构造一个行为一个 RED。golden 手写自合同/冻结输入，禁止由待测 production code 或生成器反推。
2. **Terra/xhigh 做 GREEN**：只有观察到该 RED 因预期缺失能力失败后，才能修改模块拥有的 production package。按 artifact schema、确定性算法、identity、Gap/fatal 和恢复顺序做最小 vertical slice；不得顺手扩 schema、兼容错误旧设计或从其他 stage 重读数据。
3. **Sol/xhigh 只调试异常**：仅当 RED 不是预期原因，或同一 slice 在两次有根据的 GREEN 修订后仍失败时介入。Sol 查明实现/fixture/环境根因，但无权改合同；合同确需变更时先停下并更新详细设计/version。
4. 每个 GREEN 后只运行该模块 selector 和直接跨模块 contract selector；不得运行 full suite，除非当次用户显式要求。测试/实现 Agent 均禁止网络、live Provider、客户 Maven/Gradle、客户代码执行、生成器和部署。

本目标的唯一 Design Authority 是 **gpt-5.6-sol / ultra（Sol/ultra Design Authority）**。Sol/xhigh 是受限 debug 角色，不得替代 Sol/ultra 作架构裁决；Luna/xhigh、Terra/xhigh 和 Sol/xhigh 都不能自行改 durable design。

新目标模块的拥有范围固定为：production `src/main/java/com/linguan/codemd/target/stageNN/<module-key>/`，公共跨阶段 wire records 为 `src/main/java/com/linguan/codemd/target/contracts/`，共享 canonical artifact store 为 `src/main/java/com/linguan/codemd/target/artifacts/`；tests 镜像到 `src/test/java/...`，冻结 fixtures/goldens 位于 `src/test/resources/target/stageNN/<module-key>/`。现有 `stage01`–`stage04` 包只作为可复用实现证据/Adapter，不是新模块可以随意修改的拥有范围。

允许 mock：只读 source handle、文件系统 crash/atomic-move boundary、clock（仅非 identity 显示）、Stage06 Provider transport 和 event sink。禁止 mock：canonicalizer、identity/hash material、accounting、graph/Proof/Flow/admission/planner core、上游 artifact parser/validator；这些必须用 schema-valid frozen files。禁止反射/private-field 断言、调用当前私有实现、把 exception message 当 golden。

每个 stage doc 的模块 brief 给出 exact Maven selector。命令均从本 source 根运行，形式为 `mvn -Dtest=<ExactTestClass> test`；它运行的是本 Agent 模块项目，不是客户 Maven。模块完成后，在同一文档的当前实现审计中把对应项从“尚未符合目标”更新为有证据的中文状态，并记录 selector/fixture/version；不得用 GREEN 外推未测模块或真实 DepotHead success。

### 13.11 实现偏离与设计变更协议

Luna/xhigh 或 Terra/xhigh 在以下任一情况必须停止受影响 vertical slice：需求按现合同无法满足；预期 RED 不成立或因错误原因失败；现有实现与合同冲突；必需数据在上游 artifact 不存在；方案需要猜 schema/语义/failure/model boundary；或实现路径明显偏离业务/信任目标。Agent 先在自己唯一 progress 文件记录可复验证据、受影响 IDs/stages、为何不能继续和最小可选项，然后把请求交给 **Sol/ultra Design Authority**；不得静默改变 schema、golden/expected value、失败等级、重试、模型职责或上下游边界。

只有 Sol/ultra Design Authority 可以裁决**单模块或单阶段内部**、局部、可逆、语义等价且不改变跨阶段 contract 的实现权衡，例如内部命名/算法、模块内部拆分、明确 non-identity 的观测字段或满足同一硬上限的预算实现。裁决不得削弱业务目标、Fact准确、evidence/Proof/Trace closure、逐模块/阶段持久化与恢复、固定九章、安全或 LLM 受限职责。局部裁决先更新对应 stage doc/schemaVersion，再允许 Luna 重写 RED、Terra 继续。Sol/xhigh 只能提交 debug 证据，不能批准该变更。

**MUST/STOP 升级规则**：任何影响跨阶段 Interface、artifact field semantics、identity DAG、composition/accounting invariant、阶段边界/顺序/持久化，或任何业务目标、信任模型、九章合同、来源范围、安全/模型边界的修改，Sol/ultra Design Authority 也 **MUST NOT** 自行决定。相关实现 **MUST STOP**；Sol/ultra 必须整理可复验证据、影响范围、至少两个可行备选（若确实只有一个则说明为何）、推荐项与不变项，交回用户讨论确认。用户确认前不得改 RED、production code 或当前有效合同；确认后才更新 DESIGN/相关 stage doc/schemaVersion并恢复实现。

progress 只记录中断/恢复证据与下一动作；durable design 只保存当前有效合同，不写时间流水账。每个模块 handoff 的 Luna/Terra 指南都必须显式引用本节。

## 14. 固定九章合同

NineSectionProfile 的名称、顺序和“每章恰好一次”保持不变：

1. 文档说明
2. 业务目标
3. 业务对象
4. 业务活动
5. 字段与维度
6. 对象关系
7. 指标口径
8. 示例问题
9. 待确认事项

plan 中每个 ReaderItem 有唯一 section owner。空章也使用 typed EMPTY_SECTION item，不能让 renderer临时写 filler。

## 15. 当前实现审计（与目标设计分开）

本节只描述当前仓库事实。它不缩小前述目标，也不把测试通过外推为 DepotHead 成功。

| 目标阶段/横切能力 | 中文状态 | 当前事实 | 与目标的差距 |
| --- | --- | --- | --- |
| Stage 01 冻结来源 | **已验证（有限、内存态）** | 现有 Stage01 M1 对声明 inventory 做 path/size/SHA/UTF-8/identity 校验；固定 jshERP 八文件可重验 | 成功中间态尚未按本设计立即持久化到 run/stages/01 |
| Stage 02 应用与入口发现 | **部分具备（内存态）** | 现有 Stage01 M2 能在有限 Java/Spring MVC/MyBatis profile 中发现 route、部分 Mapper 绑定与 capability site | 目标独立 application/entry artifacts 与完整 catalog 尚未形成 |
| Stage 03 五张程序图 | **缺失目标产物** | 现有 RepositoryModel、Stage01FlowView 含部分结构、调用和 CFG 投影 | 没有五个 standalone first-class graph files；通用 data-flow/evidence graph 不足 |
| Stage 04 事实与证明 | **已验证（有限 profile）/ DepotHead 仍有缺口** | 现有 Stage01 M3 有 CodeFact、ProofPack、GapLedger 与 accounting 的 in-memory bounded tests | Fact registry 偏合成剖面；无法充分证明 DepotHead status/ids 的跨层通用数据流 |
| Stage 05 流程与 Capsule | **已验证（有限 profile）/ 真实样例为缺口** | 现有 Stage02 compiler 在受支持 fixture 上工作 | 当前固定 jshERP slice 的诚实结果是 Gap、0 Flow、0 Capsule；且 Stage02 结果主要在内存 |
| Stage 06 单流程解释 | **部分具备（scripted R1/R2）** | 现有 Stage03/04 有逐 Flow R1/R2、有限 key、runtime/lifecycle 和 deterministic replay 的 bounded scripted tests | 尚无R0 registry proposal、程序freeze或单一RepositoryInterpretationRegistry；正常3N lifecycle/accounting与独立Stage06 run artifacts未实现。DepotHead没有Capsule，所以当前仍应0 task/call |
| Stage 07 准入与仓库知识 | **部分具备（有限 profile、内存后归档）** | 现有 Stage03 有程序准入、technical fallback、有限 merge 与 typed knowledge | 通用 anchor/conflict/owner 能力仍有限；没有按目标单独持久化 Stage07 |
| Stage 08 九章与运行归档 | **部分具备（final-only Candidate）** | 现有 archive-v2 能保存最终 Candidate、plan、trace、rounds、receipts，并做零 Provider validation；Candidate 未发布 | 只有最终 Candidate 归档，不是八阶段逐段 production persistence；whole-run resume 仍不符合本目标 |
| Java/CLI/HTTP runtime | **部分具备（注入式 Adapter）** | target CLI/loopback HTTP seam 有 bounded tests，现有`CodeToMarkdownAgent`可作兼容Adapter证据 | 单一run-centric `RepositoryAnalysisAgent`七方法、identity-only artifact query、完整Java/CLI/HTTP同义映射与默认composition尚未实现 |
| Stage 00 | **POC 历史记录** | 人工 Manifest/LockedFact、旧 baseline/archive 仍用于解释历史 | 不得作为目标主线旁路或 DepotHead 成功证据 |

因此当前最重要的结论是：

- Stage01/02 的有限能力通过不等于八阶段生产主线已实现；
- final-only Candidate archive 不等于逐阶段可观察与可恢复；
- DepotHead 源码路径是真实的，但当前仍缺正式五图、通用数据流与 Facts；
- 当前 jshERP 结果必须继续写成 Gap、0 Flow、0 Capsule；
- 后续实现应补齐设计，而不是把设计降到当前类和文件布局。

## 16. 详细文档导航

1. [01 冻结来源](stages/01-freeze-source.md)
2. [02 发现应用类型和入口](stages/02-discover-application-and-entries.md)
3. [03 构建五张正式程序图](stages/03-build-five-program-graphs.md)
4. [04 证明代码事实](stages/04-prove-code-facts.md)
5. [05 编译业务流程](stages/05-compile-business-flows.md)
6. [06 一次解释一个流程](stages/06-interpret-one-flow-at-a-time.md)
7. [07 准入并合并仓库业务知识](stages/07-admit-and-merge-business-knowledge.md)
8. [08 构建九章文档与归档](stages/08-build-nine-section-document-and-archive.md)

[Stage 00](stages/00-mvp.md)只保留为 POC 记录。共享九章权威见 [NineSectionProfile](../../../../shared/source-agent-contracts/README.md)。
