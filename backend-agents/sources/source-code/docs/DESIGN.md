# Source Code Analysis Agent 目标设计：从冻结代码到可信业务文档

## 0. 目标身份与 Wire Reset

目标实现整体位于 `backend-agents/sources/source-code/`，Maven coordinate
固定为 `org.sourceanalysis:source-code-analysis-agent`，显示名为
`Source Code Analysis Agent`，Java public root 为 `org.sourceanalysis.app`。
八个业务分析包依次是 `org.sourceanalysis.app.analysis.inventory`、
`org.sourceanalysis.app.analysis.discovery`、`org.sourceanalysis.app.analysis.graph`、
`org.sourceanalysis.app.analysis.fact`、`org.sourceanalysis.app.analysis.flow`、
`org.sourceanalysis.app.analysis.interpretation`、
`org.sourceanalysis.app.analysis.knowledge` 和
`org.sourceanalysis.app.analysis.document`；横切root只有
`org.sourceanalysis.app.capture.localgit`、`org.sourceanalysis.app.artifact`、
`org.sourceanalysis.app.evidence`、`org.sourceanalysis.app.runtime`、
`org.sourceanalysis.app.validation`、`org.sourceanalysis.app.adapter.cli`、
`org.sourceanalysis.app.adapter.http` 和`org.sourceanalysis.app.adapter.provider`。
未来数据库根保留为`org.sourceanalysis.db.analysis`，
本设计不创建数据库代码。

结构性 Wire Reset 已完成：工程目录、Maven坐标与Java命名空间已切换，旧
`sources/github-code/`、`com.linguan.codemd`、编号package/type/schema、旧
receipt、旧run store及其production/test/fixture wire已经从当前代码树删除。
它们只可作为历史审计或unsupported-wire输入出现，不是兼容合同。后续实现
只写新格式；不增加alias、symlink、dual reader/writer、迁移bridge、fallback
reader或旧artifact转换。任何旧
path、descriptor、schema、receipt、package identity 或编号类型输入都 fail
closed，且不得产生新 publication。Git 历史与历史 progress 保留用于工程
审计，但永远不是运行时输入。

数字前缀只给文档文件和运行目录排序。Java package/type/field、schema、
artifact ID、测试和命令都使用语义名称。目标 package 不出现 `target`、
`mvp`、`common`、`shared`、`misc`、`utils`、`codemd`、`github`、
`linguan` 或编号分析步骤名称。

## 1. 先说业务结果

Source Code Analysis Agent 的目标不是“让模型读一遍仓库并写篇总结”，也不是证明一个代码切片后就停止，而是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，转换成一份业务人员能读、工程人员能追、审计人员能复验的仓库级九章 Markdown 候选。

一次分析成功后，读者应能回答四个问题：

1. 这个应用向外提供哪些业务入口？
2. 每个入口从请求到持久化或返回经历了什么？
3. 哪些结论由源码和确定性规则证明，哪些只是模型解释？
4. 哪些问题当前不知道，应该由谁、用什么新增材料关闭？

候选始终未发布。Selection、冻结来源文档、package 和发布属于后续显式流程，不由本 Agent 自动完成。

### 1.1 信任目标

本设计把“好读”和“可信”同时设为出口条件：

- **来源可信**：同一 revision、路径和字节可以重开；不能静默换成分支最新值。
- **捕获可信**：显式本地维护命令只从用户给定的本地 Git 对象库读取一个完整 40 位 commit，枚举其整棵 tracked tree；不读 index/工作区、不解析 branch/ref、不联网。
- **仓库覆盖可信**：冻结仓库中的每个文件、发现 site、入口和后续语义项都有唯一处置；单个 Flow 成功不能冒充整仓分析完成。
- **推导可信**：业务事实的每个语义原子都能回到源码 span、程序图和确定性规则。
- **模型受限**：模型只解释一个已经由程序编译完整的流程，不能发现调用、补路径、写 locator 或自批事实。
- **未知诚实**：静态代码不能证明的运行时、部署和企业政策进入 Gap，不进入事实。
- **过程可见**：每一分析步骤成功后立即留下 canonical JSON/JSONL 生产资产；下游失败不抹掉上游成果。
- **模块可复用**：每个命名模块完成时立即原子安装 canonical JSON/JSONL payload 与独立 receipt；下一模块或显式新执行只重开这组已安装 bytes，不接内存旁路。
- **状态不混义**：运行状态只表达当前单进程执行；`FAILED` 没有最终分析结果，`INCOMPLETE_COVERAGE` 只属于完整归档的 `FINISHED` 诊断结果。
- **复用精确**：新 analysis step execution 只接受完全相同的来源、工具、profile、schema、prompt 和 policy 哈希，不做“差不多”的上游引用。
- **文档可重验**：Markdown 只读 nine-section-plan.json；独立验证可以重开冻结源码，但 renderer 不重新读源码。

### 1.2 五种材料永不混写

| 标签 | 含义 | 可以进入最终正文吗 |
| --- | --- | --- |
| **REAL_SOURCE** | 固定 revision 中实际存在的字节和 locator | 作为引用与技术依据 |
| **DETERMINISTIC_CONCLUSION** | 程序由受支持语法、五张程序图和闭合 Proof 得出的结论 | 可以，保留 Fact/Proof lineage |
| **MODEL_INTERPRETATION** | 模型针对一个 EvidenceCapsule 给出的有限业务解释 | 只有经 分析步骤“仓库知识” 准入后可以 |
| **UNKNOWN / GAP** | 当前无法唯一证明、范围外或需要运行时/业务责任方确认的内容 | 可以作为待确认事项，不能写成事实 |
| **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT** | 连贯解释目标流程的示意，不是当前产物 | 不作为当前实现或运行证据；是否属于wire specimen必须再由下节标签声明 |

后文凡展示目标流程 JSON，除非明确写成“当前已归档产物”，统一属于 **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**。为保持可读性不在每个字段重复标签：已给出的 commit/path/route/locator 是 **REAL_SOURCE**；只有在声明算法/前置全部满足后才能产生的值是 **DETERMINISTIC_CONCLUSION**；示例 ID、digest 和尚未运行得到的业务词是说明性值。技术合同不能用说明性值填补未知，必须使用 nullable、UNRESOLVED、Gap 或 fatal 结构。

### 1.3 示例可信度分类

每个JSON/JSONL、wire record、byte excerpt或golden示例必须在紧邻正文显式使用且只使用下列一个标签；`TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT`不替代该分类：

1. **NARRATIVE_ILLUSTRATION**：只解释业务故事、顺序或局部关系；可以省略wire字段，示例中的ID/version/digest不是可加载值，也不能成为测试golden、identity preimage或跨示例映射依据。
2. **STRUCTURAL_WIRE_SPECIMEN**：逐字段展示某一个exact record/variant的完整字段、类型、required-nullable规则、closed enum和canonical顺序；所有ID至少满足safe grammar，但digest、size、root、receipt或artifact ID明确**未由展示bytes重算**，因此不可replay。每个specimen在其声明的isolated fixture boundary内自足；不同analysis step或不同specimen的相似业务名不建立隐式ID remap。遗漏required field/type的内容不能靠改标签冒充specimen。
3. **STRICT_REPLAY_GOLDEN**：输入bytes、direct preimages、typed references、canonical ordering、输出bytes以及每个size/SHA/ID/root/receipt都完整给出并实际重算；可以直接作为Luna测试golden。任何一个值是占位符、跨边界引用未给出或identity未重算时都禁止使用`exact`、`schema-valid`、`executable`、`golden`或本标签。

权威字段表、record组件、enum、identity公式和依赖表始终是exact合同，与示例分类无关；把示例降为narrative不能删除这些合同。各analysis step的大型DepotHead module块默认是逐analysis step隔离的`STRUCTURAL_WIRE_SPECIMEN`，除非紧邻标签明确说是narrative；它们不是一条跨八analysis step replay链，production/test不得把相同显示名当成silent remap。严格重放测试必须在自己的fixture目录保存完整preimage closure并重算全部identity，不能复制specimen digest。

## 2. 唯一贯穿样例：真实 DepotHead 状态更新路径

样例来源是 jshERP 固定 commit 8c30ce7861570458920175e200bb2a6442713580 的本地只读快照。本文没有执行客户 Maven、应用、SQL、MyBatis runtime、网络或模型。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本节及各 analysis step 文档用一组连贯、合理的 ID/digest 展示目标 artifact 怎样接力；它们不声称当前运行已产出。真实源码值与当前 Gap 结论仍以 2.1、2.4 和实现审计为准。

DepotHead 只是完整仓库中 `N` 个入口/FlowSlice 之一的讲解 fixture。本文反复出现的八文件 `BOUNDED_PATH_SET` 只能验证这条局部链路和失败语义，**不具备 repository completion 资格**；生产分析必须使用 分析步骤“已验证源码清单” 证明闭合的 `COMPLETE_CAPTURE`，并对其中所有入口逐一处置。后文故事为了可读性只展开 DepotHead，不表示其他文件、入口或 Flow 可以省略。

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

### 2.4 历史审计给出的诚实基线

已删除的pre-reset `Stage02Compiler`曾对固定jshERP八文件得到 **Gap、0 Flow、0 Capsule**。这是一条历史工程证据：旧实现没有闭合跨层数据流、Fact registry和动态MyBatis证明，不是当前SourceAnalysis运行结果。Wire Reset后的代码尚未运行该仓库，也没有当前Flow/Capsule产物。后文目标JSON只能用于解释应当怎样闭合DepotHead，不能被引用为“当前DepotHead已成功”或“当前仍为0 Flow”。

## 3. 八个分析步骤纵向主线

唯一生产主线如下。每个箭头跨越的是已落盘、已自验的 canonical artifact，不是上一分析步骤的 Java 内存对象。主线之前有一个独立的本地维护 seam：`LocalGitCommitCaptureAdapter` 可以接收主机路径，但它不是分析 Interface，也不在 analysis worker 内运行。

~~~text
显式本地 capture：local repository path + exact 40-hex commit
   -> immutable full-tree snapshot + capture receipt + source registration
   -> rootless, content-addressed AnalysisRunRequest
   -> QUEUED single-process run
已验证源码清单
   -> 应用发现
   -> 程序图
   -> 已证明代码事实
   -> 业务流程
   -> 流程解释
   -> 仓库知识
   -> 九章文档、Trace、验证、归档与观察
~~~

目标 run 目录：

~~~text
runs/<run-id>/
  run-request.json
  execution-status.json
  steps/
    01-verified-source-inventory/
      modules/<nn-module>/<module-artifact>.json
      <analysis-step-public-artifacts>
      verified-source-inventory-receipt.json
    02-application-discovery/
      application-discovery-receipt.json
    03-program-graphs/
      program-graphs-receipt.json
    04-proven-code-facts/
      proven-code-facts-receipt.json
    05-business-flows/
      business-flows-receipt.json
    06-flow-interpretation/
      flow-interpretation-receipt.json
    07-repository-knowledge/
      repository-knowledge-receipt.json
    08-nine-section-document/
      nine-section-document-receipt.json
  validations/
  failures/
  run-manifest.json
~~~

每个 `modules/<nn-module>/` 目录先在同文件系统 staging 中写满至少一个 canonical JSON/JSONL payload 和 `module-receipt.json`，逐文件算 SHA、自验后立即原子安装；前七个分析步骤的 publisher module只发布本步骤的 semantic publication specification，`CanonicalAnalysisStepArtifactStore`重开该 module 后安装 semantic payloads，并最后创建绑定其 reference 的语义 receipt。九章文档使用 13.3.1 定义的 acyclic coordinator preparation provenance：五项 semantic payload、archive 和 receipt 先于 root manifest，最终 M4 publication/receipt 最后反向绑定它们。九章文档的前七个逻辑输出位于其目录，唯一 `run-manifest.json` 位于 run 根；`CanonicalRunManifestStore`在九章文档 receipt 后安装它。安装后不可修改；新的验证或失败诊断写到外部区域。`execution-status.json`只是可替换的轻量诊断状态，不进入任何 artifact identity，也不承担续跑。运行时生成物除最终 `document.md` 外全部是 JSON/JSONL；不得产生第二份人读 Markdown、模块摘要 Markdown 或日志式正文。

### 3.1 非技术读者怎样顺着 DepotHead 走完主线

| 分析步骤 | 这一分析步骤回答的普通问题 | DepotHead 的目标可观察答案 | 为什么下一个分析步骤不必返工 |
| --- | --- | --- | --- |
| 已验证源码清单 | “看的到底是哪份代码？” | 生产 run 固定完整仓库；八个 DepotHead 文件只是其中可追踪的示例子集 | 后续按 snapshot/file identity 重开，不再碰活动工作树 |
| 应用发现 | “仓库有哪些请求入口？” | 全入口 inventory 中包含 `POST /depotHead/batchSetStatus` 及 handler、Mapper 候选 | 图构建直接拿完整 entry/catalog denominator，不再猜 route或漏掉其他入口 |
| 程序图 | “请求、条件、值和 SQL 怎样连起来？” | 五张图分别表达结构、调用、控制、数据和证据 | Fact prover 只消费 graph edges，不重写 parser |
| 已证明代码事实 | “哪些整句结论真的证明了？” | 每个 candidate Fact 的所有 atom 要么有闭合 Proof，要么成为带原因的 Gap | Flow compiler 只引用 admitted Fact/Proof，不借附近源码 |
| 业务流程 | “每个请求有哪些完整结局？” | 全入口逐一成为 Flow、多条 Outcome/一个 Capsule，或有证据的 GAP/EXCLUDED | 流程解释的 R0/R1/R2 都只能读各自 Flow 的同一 Capsule，不能跨流程补材料 |
| 流程解释 | “新仓库的业务词从哪里来，又怎样安全使用？” | 每个 eligible Flow 先在隔离 R0 提出有 basis 的 bounded 业务词/claim/question；程序验证并冻结唯一 RepositoryInterpretationRegistry。只有R0已准备好的 Flow 才有同 Flow 的R1/R2，并且只能选择有限 provisional keys；planned task数为`E+2R`，实际Provider调用数是其可验证子集。ineligible Flow 不调用模型；当前 0 Capsule 所以 0 调用 | 仓库知识重验 registry proposal→provisionalKey→selectedKey 的完整 lineage 与所有 slice dispositions，不再调用模型 |
| 仓库知识 | “所有流程的说法怎样形成一个仓库视图？” | 程序逐 slice 准入或丢弃有限 key 提案，再跨 Flow 合并 Facts、meanings、relations、metrics、conflicts、registry lineage 和 Gaps | 文档规划只读唯一一份 RepositoryKnowledge，Trace 可回到 R0 basis |
| 九章文档 | “整个仓库的业务读者看到什么，怎样观察每个分析步骤？” | 每个完成的仓库分析链恰一份九章 plan、恰一份 document.md、Trace、未发布 Candidate 和完整 run manifest；同一 run-centric Interface 可 inspect/artifact/render/validate/trace | 审阅、Trace、Selection、validation、显式新分析步骤执行和Java/CLI/HTTP Adapter各读固定身份；禁止一 Flow 一 Markdown或用 Path 绕过 manifest |

因果关系必须保持：来源不固定就不能可信定位；**完整入口分母**不固定就不能证明仓库覆盖；五图不闭合就不能证明 Fact；Fact 不闭合就不能编译 Flow；没有 Flow/Capsule 就不得调用模型。历史pre-reset DepotHead审计恰好停在Fact/Flow closure Gap，因此它成为“零任务而非绕过上游调用模型”的回归基线；目标实现仍须独立处理同一仓库的其他eligible Flow，并由分析步骤“仓库知识”和“九章文档”把全部slice的Facts、解释、失败、排除和Gaps合成非空、可审阅的单一仓库知识与九章文档。

### 3.2 共通分析步骤出口

“零业务记录”不等于“没有产物”。每个 `SUCCEEDED` 或 `SUCCEEDED_WITH_GAPS` 分析步骤都必须通过 `CanonicalAnalysisStepArtifactStore` 原子安装该分析步骤规定的 semantic payload 集合，并由 store 最后创建该步骤 registry 指定的语义 receipt；允许为零的 JSONL 仍作为 canonical empty file 存在，但只在 policy registry 对该 `(artifactType,schemaVersion)` 明确许可时有效。receipt 绑定 status、Gap refs/count、publisher module reference、artifact descriptors/SHA 与 controls；**分母、零计数、分区和守恒方程只保存在该分析步骤命名的 semantic coverage/accounting payload中**，receipt 通过其 descriptor/provenance 绑定它们而不重复一套字段。只有 receipt、artifact root、control hashes、相应semantic accounting 和本分析步骤不变量全部通过，下一个分析步骤才可开始。`FAILED` 只写外部failure诊断与轻量execution status，不安装伪成功目录。

分析步骤内部也禁止只传 Java 内存对象。每个命名模块将一个或多个schema-versioned、policy-validated canonical payload与一个独立`module-receipt.json`写入自己的immutable `modules/<nn-module>/`目录；普通模块payload是`ModuleArtifact<T>`，前七个步骤的 publication specifier可使用同名 standalone JSON/JSONL bytes，精确边界见13.3.1。receipt绑定payload集合、module root、upstream/control refs和Gap状态但不包含自身。只有整组原子安装后模块才算完成。下一模块必须重开payload与receipt，逐项校验artifact ID/SHA/root/upstream/control refs后才能处理。模块失败只写外部`ModuleFailure`，已完成模块不回滚。publisher只消费这些已安装artifacts/receipts并生成对下一个分析步骤公开的文件；它不得从内存旁路或重新运行上游模块。

### 3.3 端到端 composition proof

下表不是路线图，而是分析步骤组合的验收证明。每行 postcondition 必须逐字段满足下一行 precondition；reader 不允许从别处补默认。

| AnalysisStep | precondition | 确定性 transformation | postcondition | 精确满足下一个分析步骤 |
| --- | --- | --- | --- | --- |
| Verified source inventory | `sourceRegistrationId`、content-addressed `frozenRepositoryRequestRef`、本地 capture receipt/完整 tracked-tree inventory、controls；不含 Path | registry resolution → admission → NOFOLLOW/size/SHA for every regular file → text/media disposition → rootless identity → per-module receipt → atomic analysis step publish | immutable full snapshot/inventory、每 regular-file disposition、`NON_ANALYZABLE_MEDIA`仍在分母、repositoryCompletionEligible=true、VerifiedSourceInventory root/receipt | ApplicationDiscovery 要求的全仓 snapshotId、opaque source handles、可解析文本集、显式media排除集和tool/profile/schema controls 全部已固定 |
| Application discovery | valid complete VerifiedSourceInventory root；discovery profile 能解析全部声明文件 | application signals → 全 site/entry routes → Mapper candidates → capability accounting | applicationProfileId、全部 entryIds、catalog IDs、site dispositions、entry coverage root、ApplicationDiscovery root | ProgramGraphs 的 graph roots/candidate endpoints/coverage denominator 精确等于这些完整 ID 集合 |
| Program graphs | valid VerifiedSourceInventory与ApplicationDiscovery roots；entry/catalog endpoints 可重开 | structure → call → CFG → data-flow → evidence → cross-graph validation | 五图 roots、exact node/edge IDs、entry ownership、graph Gaps | ProvenCodeFacts 只需 nodes/edges/evidence 枚举 Proof；BusinessFlows 可复用 entry-root call/control/data edges |
| Proven code facts | valid five-graph set；Fact registry 固定 denominator | candidate/atoms → source+rule Proof closure → admit/reject → Gap/accounting | admitted fact/atom IDs 与 Proof IDs，rejection/Gap refs，ProvenCodeFacts root | BusinessFlows 的可用语义只来自 admitted Facts；缺项已是 typed Gap而非隐含未知 |
| Business flows | valid complete entry set、graphs、Facts/Proof/Gaps | 对每 entry 遍历 → Outcomes → ownership → per-Flow minimal projection → repository entry accounting | `N` 个 Flow/Outcome/Capsule IDs 或 entry GAP/EXCLUDED；每 Capsule 封闭地携带 R0/R1/R2 共用的 Fact/Gap/Outcome views、basis atom/Gap、span 与 projection obligation；coverage、BusinessFlows root | 每个compiled Flow都有恰一个 evidence-complete Capsule；FlowInterpretation只把`ELIGIBLE` Capsule作为三轮唯一source projection，`INELIGIBLE` Capsule及其Gap直接交RepositoryKnowledge；一条 Flow 成功不能关闭其他 entry；0 Flow 精确推出 0 task/call |
| Flow interpretation | valid全量 `N` Flow↔Capsule 双射、完整 `ELIGIBLE`/`INELIGIBLE` 分区、optional organization registry seed、R0/R1/R2 prompt/schema/runtime policy | 仅对 `E` 条eligible Flow建隔离R0 task → 单次Provider调用与程序validation/disposition → freeze唯一RepositoryInterpretationRegistry → 仅对其中 `R` 条`READY_FOR_FREEZE` Flow建finite-key R1/R2 tasks → canonical rounds/receipts/candidate/disposition/publication | `E`个R0 task/disposition、恰一冻结registry、`E`个最终interpretation dispositions 与 `2R`个R1/R2 planned tasks；公式始终为`E+2R`。每个成功发布的planned task都有恰一typed disposition；round records/started Provider calls只是planned tasks的可验证子集，R2可因同Flow R1业务失败而`NOT_RUN_UPSTREAM_FAILED`。仅当全部任务都启动且`R=E`时调用数为`3E`。每R0 proposal有basis/disposition/provisionalKey lineage；每eligible Flow恰一最终disposition；`I=N-E` 条model-ineligible Flow不创建FlowInterpretation per-Flow对象；`E=0`精确为0/0/0且仍安装空registry和FlowInterpretation root | RepositoryKnowledge 只重开FlowInterpretation九份semantic artifacts与receipt即可重验全部lineage，并从BusinessFlows读取model-ineligible Flow的eligibility/Gaps；无需 source、Provider 或未持久化session state |
| Repository knowledge | valid full Facts/Flows、唯一FlowInterpretation registry、R0→R1/R2 lineage、每 slice closure | per-slice deterministic admission/fallback decision → validate `registryProposalId→provisionalKey→selectedKey` → cross-Flow proven-anchor merge → conflict/owner/account | **恰一个** repositoryKnowledgeId，含全部 flow/decision/meaning/anchor/relation/metric/Gap/owner/conflict refs及registry lineage；每条lineage原样携带registry的`proposalKind/normalizedLabel/normalizedPurpose` | NineSectionDocument 只接这一份 RepositoryKnowledge；每个 ReaderItem 候选都有 typed semantic item、唯一 knowledge owner、可追到R0 basis的lineage和无需重开FlowInterpretation即可读取的规范业务值 |
| Nine-section document | valid七个上游semantic roots、RepositoryKnowledge coverage **draft**、唯一 RepositoryKnowledge、NineSectionProfile、run lineage | M1先计算无环的 final coverage ledger，再作repository section ownership → one plan → one isolated render → Trace（含registry lineage）→ validation/archive → run-centric observation projection | 每个完成链 **恰一份** exactly-nine plan、**恰一份** document.md、Trace root、UNPUBLISHED Candidate、run manifest；final ledger由同一M1内部artifact给M1/M3/M4与validator共用；inspect/artifact/render/validate/trace按run identity返回 | Review/Trace/Selection、显式新analysis step执行及Java/CLI/loopback HTTP各有完整immutable input，不需要重做有效上游或模型；不存在per-Flow Markdown、Path查询或Adapter私读目录 |

组合成立的条件是：任何一行的 `artifactId + sha256 + controls + payload reference` 不匹配就停止在该边界；禁止下游用源码、当前内存对象、同名字符串或模型响应“修复”上游。这样每个 transformation 的输入都由上一 postcondition 唯一给出，且所有 identity 依赖保持无环。

reader-visible正式输出总数固定为52，不因移除同run自动恢复而改变：VerifiedSourceInventory为`3 semantic + 1 receipt = 4`，ApplicationDiscovery为`4+1=5`，ProgramGraphs为`7+1=8`，ProvenCodeFacts为`4+1=5`，BusinessFlows为`5+1=6`，FlowInterpretation为`9+1=10`，RepositoryKnowledge为`5+1=6`，NineSectionDocument为`5 semantic + archive manifest + analysis step receipt + run manifest = 8`；因此`4+5+8+5+6+10+6+8=52`。module payload/receipt、external validation artifact和`execution-status.json`是实现/诊断资产，不加入这52项，也不能冒充新的reader-visible analysis step output。

### 3.4 同一 DepotHead identity 怎样贯穿

下面的 story key 只帮助阅读；生产 join 使用右列的 typed IDs，不新增一个可猜测的全局字符串键。

| 业务故事位置 | 生产字段链（字段名不得中途改义） |
| --- | --- |
| 固定 Controller/XML bytes | `VerifiedSnapshot.snapshotId` → `VerifiedFile.fileId/path/sha256` |
| POST 入口 | `EntryPoint.entryId` 保存 `routeEvidenceNodeIds`；ProgramGraphs entry graph node保存同一个 `entryId/owningEntryIds` |
| Controller→Service→Mapper/XML | `ProgramEdge.edgeId` 的 endpoints 与 `evidenceNodeIds` 原样进入 atom Proof 的 `requiredProgramEdgeIds/requiredEvidenceNodeIds` |
| status 与 ids 事实 | `FactAtom.atomId` 原样进入 `Proof.atomId`、`FlowSlice.atomIds`、`EvidenceCapsule.factViews[].atoms/projectionObligations` |
| 完整请求过程 | `FlowSlice.flowSliceId` 原样进入 Capsule、RegistryProposalTask、RepositoryInterpretationRegistry item、FlowModelTask、InterpretationProposal、AdmittedFlowMeaning 和 RepositoryBusinessKnowledge.flow refs |
| 仓库特有业务词 | `BusinessRegistryProposal.registryProposalId/flowSliceId/basisAtomIds/basisGapIds/proposalKind/normalizedLabel/normalizedPurpose` 经程序 disposition 生成唯一 `provisionalKey`；R1/R2 的 `InterpretationProposal.selectedKey` 必须等于该 Flow registry item 的 provisionalKey；RepositoryKnowledge `RegistryMeaningLineage`逐字段复制三项规范值 |
| 业务解释 | `registryProposalId → provisionalKey → InterpretationProposal.interpretationProposalId/selectedKey → meaningId` 原样进入 admission decision和RepositoryKnowledge；admitted meaning保留两类proposal IDs与basis refs |
| 九章读者项 | `ReaderItem.factIds/meaningIds/gapIds/registryProposalIds/interpretationProposalIds` 原样引用 knowledge；ADMITTED_TERM 的typed slots从同一`RegistryMeaningLineage.normalizedLabel/normalizedPurpose`逐字节复制；Trace先引用该`registryLineageId`，再从 readerItemKey 逐跳回 registry proposal/round receipt、basis IDs、Proof、Evidence、VerifiedFile 和 snapshotId |

示意故事从 `POST /depotHead/batchSetStatus` 的 `status` 输入到 `jsh_depot_head.status` ReaderItem 时，任何分析步骤都不能把 `status` 改成另一个字段、把 `ids` where 条件静默丢掉、或凭空加入“审核”业务词。“批量审核或反审核”只能先作为R0有basis的仓库词候选，经程序freeze得到provisionalKey，再被R1/R2有限选择并由RepositoryKnowledge准入；当前没有Flow时该链停在Gap，NineSectionDocument的ReaderItem引用gapId而不是伪registry item或meaningId。

### 3.5 全局 accounting 与 coverage

每个分母项恰有一个终态；alias/merge 必须保留反向引用，不等于删除：

~~~text
trackedRegularFileIds = verifiedRegularFileIds ⊎ unverifiedRegularFileIds
verifiedRegularFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds
successfulVerifiedSourceInventory requires unverifiedRegularFileIds = ∅
parserInputFileIds is a subset of analyzableTextFileIds
discoverySites = supportedSites + unsupportedSites + ambiguousSites + overLimitSites
discoveredEntries = compiledEntries + gappedEntries + excludedEntries
graphCandidates(kind) = exactNodesOrEdges + graphGaps + reasonedGraphExclusions
candidateFacts = admittedFacts + rejectedFacts
candidateAtoms = admittedAtomDispositions + rejectedAtomDispositions
outcomeCandidates = compiledOutcomes + gappedOutcomes + excludedOutcomes
provenCodeFactsGaps + businessFlowsGaps = knowledgeOwnedGaps + mergedAliasGaps
compiledFlowSlices = modelEligibleFlows + modelIneligibleFlows
domain(modelIneligibilityByFlow) = modelIneligibleFlows
modelIneligibilityGapIds = exactUnion(modelIneligibilityByFlow[*].gapIds)
modelEligibleFlows = r0ReadyFlows + r0GappedFlows + r0FailedFlows
r0Tasks = r0TaskDispositions = modelEligibleFlows
r0Rounds = r0Tasks
r0RegistryProposals = acceptedRegistryProposals + rejectedRegistryProposals
r0ReadyFlows = r1r2EligibleFlows
r1Tasks = r1TaskDispositions = r1r2EligibleFlows
r2Tasks = r2TaskDispositions = r1r2EligibleFlows
r2ResponseRounds is a subset of r2Tasks
r2NotRunUpstreamFailedDispositions = r2Tasks - r2ResponseRounds
r1r2EligibleFlows = readyInterpretationFlows + r1r2GappedFlows + r1r2FailedFlows
modelEligibleFlows = readyInterpretationFlows + finalGappedInterpretationFlows + finalFailedInterpretationFlows
r0GappedFlows maps one-to-one into finalGappedInterpretationFlows and has zero R1/R2 tasks
r0FailedFlows maps one-to-one into finalFailedInterpretationFlows and has zero R1/R2 tasks
readyInterpretationFlows = flowInterpretationCandidates
flowInterpretationInterpretationProposals = keep + narrow + drop + needsEvidence + needsTermRegistry
acceptedRegistryProposals = repositoryInterpretationRegistryItems
repositorySemanticItems = readerOwnedItems + reasonedReaderExclusions
readerOwnedItems = exactlyOneSectionOwner
sections = exactlyNineOrderedSections
analysisRunDocuments = exactlyOneRepositoryNineSectionPlan + exactlyOneRepositoryMarkdown
~~~

所有等式两侧都保存 ID 列表和 count，validator 不只比较 count。令 `E` 为 model-eligible Flow 数、`R` 为其R0处置中 `READY_FOR_FREEZE` 的子集数：FlowInterpretation恰有 `E` 个R0 tasks，且只对这 `R` 个Flow各有R1/R2，因此planned tasks恰为`E+2R`，每个task都有一个`ModelTaskDispositionV1`。R0/R1/R2 shard denominators分别固定为`E/R/R`。Provider calls等于`E + R + count(R1 RESPONSE_ACCEPTED)`；只有全部R1 accepted时等于`E+2R`，再加`R=E`才是`3E`。R1 typed GAP/FAILED不删除已规划R2 task，而是为它写`NOT_RUN_UPSTREAM_FAILED`并引用同Flow R1 task。R0 GAP/FAILED不创建该Flow的R1/R2 tasks。model-ineligible Flow的FlowInterpretation tasks与Provider calls恒为0，但仍进入RepositoryKnowledge的唯一technical-fallback decision，不能从总Flow分母消失。一个 ID 不能出现在两个互斥分子中；`mergedAliasGaps` 必须保存 canonicalGapId 和 memberGapIds；`reasoned*Exclusions` 必须有版本化 reason code，不能成为垃圾桶。图 node/edge、Fact atom、Outcome、Gap、registry proposal/provisional key、interpretation proposal、knowledge item 和 section owner 的 orphan/duplicate 都是 fatal。

这些不变量共同给出三个出口性质：Proof/identity closure 保证准确，Flow ownership + typed knowledge + fixed templates 保证可读而不串故事，ReaderItem→source 的无断链 Trace 保证可追溯。不支持语法、动态配置或静态不可知行为只能把对应 denominator item 移到 Gap/明确 exclusion，影响的是完整性；它们不能污染已证明内容的准确性，也不能从“待确认事项”外消失。

### 3.6 全局 identity DAG

唯一合法依赖方向是：run controls/source registration/optional organization registry seed → snapshot/file → application/entry/catalog → graph node/edge/evidence → candidate atom/fact → Proof → Outcome/Flow → Capsule → R0 task/round/registry proposal/disposition → frozen RepositoryInterpretationRegistry/provisional key → finite-key R1/R2 task/round/interpretation proposal/Flow disposition → admission decision/meaning/anchor → repository knowledge/ownership → RepositoryKnowledge coverage preparation → embedded RepositoryKnowledge coverage draft → NineSectionDocument coverage preparation → final coverage ledger → ReaderItem/plan → document/Trace roots → Candidate/run manifest。后项可以引用前项，前项不得含后项 ID；publisher/receipt 只在 payload IDs 固定后计算。Trace record 不含 candidateId，Candidate 最后绑定 traceRoot，避免 Candidate↔Trace 环。R0不能引用R1/R2或meaning，registry item不能引用RepositoryKnowledge identity；optional seed只有被某R0 proposal以`sourceSeedKey`引用并携带Capsule basis后才能进入冻结registry。

Candidate只绑定`upstreamAnalysisStepRoots[7]`、plan/document/Trace和lineage；NineSectionDocument `analysisStepArtifactRoot`在Candidate等五个semantic payload固定后计算，不含archive-manifest、receipt或run-manifest。archive-manifest/receipt依序绑定前项且都不含自身，`run-manifest.analysisStepPublications[8]`再包含八个semantic analysis step的address/root/receipt ID/SHA但不含自身；最后由analysis step外M4 publication envelope列齐八个public files。Candidate不得预先引用NineSectionDocument root，否则形成Candidate↔NineSectionDocument root环。其他analysis step receipt的artifact list同样排除receipt自身；父级publication/run manifest才记录receipt文件SHA。

### 3.7 Repository completion gate

生产 run 的完成条件不是“DepotHead 或任意一个 Flow PASS”，而是一个 schema-versioned、content-addressed `RepositoryCoverageLedgerV3` 闭合。为了不形成 `plan ↔ ledger` 身份环，账本有两个**职责不同、引用方向固定**的版本：

1. RepositoryKnowledge M3 在既有 `knowledge-accounting.json` 内发布 `RepositoryCoverageLedgerDraftV2`。它已经证明 VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation roots 加上 RepositoryKnowledge M1/M2 语义集合的分母、处置、Flow/模型/知识所有权闭包，但**绝不引用尚由自己所在 RepositoryKnowledge public set 计算的 root**；它还不知道九章 reader item 和 section owner，也不是新的对外文件。
2. NineSectionDocument M1 从这份 draft、已经存在的 RepositoryKnowledge publication、唯一 RepositoryKnowledge 与九章 profiles 计算 `NineSectionDocumentCoveragePreparationV1`，再原子安装一个供M1/M3/M4、validator与后续显式执行共同读取的 `modules/01-planner/repository-coverage-ledger.json`。该 `nine-section-document-repository-coverage-ledger-v1` ModuleArtifact 是唯一 final ledger。它**不引用 plan artifactId**；随后 M1 才让 plan 引用它。因此仍保持单向 DAG，且不增加 NineSectionDocument 的八个 reader-visible 文件。

M1、M3、M4、NineSectionDocument analysis step receipt、root run manifest和fresh validator都必须逐字引用同一个 final `ArtifactReference`。`RepositoryCoverageLedgerDraftV2` 只可进入M1；任何模块不得把 draft 冒充为可完成的 final ledger。

~~~text
RepositoryCoverageLedgerDraftReferenceV1    // selects an exact nested value, never a loose JSON file reference
  knowledgeAccountingRef: ArtifactReference
  repositoryCoverageLedgerDraftId
  schemaVersion=repository-coverage-ledger-draft-v2

RepositoryCoverageLedgerDraftV2             // embedded in RepositoryKnowledge knowledge-accounting.json
  schemaVersion=repository-coverage-ledger-draft-v2
  repositoryCoverageLedgerDraftId
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  upstreamAnalysisStepCoverageRoots[6]: AnalysisStepPublicationReference
  repositoryKnowledgeCoveragePreparation: RepositoryKnowledgeCoveragePreparationV1
  sourceFileIds[], analyzableTextFileIds[], nonAnalyzableMediaFileIds[]
  discoverySiteIds[], entryIds[], graphCandidateIdsByKind{}
  factCandidateKeys[], admittedFactIds[], atomIds[]
  outcomeCandidateIds[], outcomePathIds[]
  flowSliceIds[], evidenceCapsuleIds[]
  modelEligibleFlowSliceIds[], modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  gapIds[]
  registryProposalTaskIds[], registryProposalRoundIds[], registryProposalDispositionIds[]
  registryProposalIds[], acceptedRegistryProposalIds[], rejectedRegistryProposalIds[]
  repositoryInterpretationRegistryItemIds[], provisionalKeys[]
  interpretationTaskIds[], interpretationRoundIds[]
  flowInterpretationDispositionIds[], flowInterpretationCandidateIds[]
  interpretationProposalIds[], interpretationProposalDecisionIds[]
  flowAdmissionDecisionIds[], admittedMeaningIds[], registryLineageIds[], technicalFallbackIds[]
  repositoryKnowledgeItemIds[], relationIds[], metricIds[], knowledgeConflictIds[]
  semanticItemIds[], ownerSemanticItemIds[], reasonedSemanticExclusionIds[]
  shardReceipts[], equations[]              // exact ID sets, never counts alone
  closedThroughRepositoryKnowledge: BOOLEAN
  closureReasonCode: STRING?                // null iff closedThroughRepositoryKnowledge

RepositoryKnowledgeCoveragePreparationV1                // embedded in draft; cannot mention M3/public/root/receipt or NineSectionDocument
  schemaVersion=repository-knowledge-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]: AnalysisStepPublicationReference
  repositoryKnowledgeAdmissionDecisionSetRef: ArtifactReference // installed RepositoryKnowledge M1
  repositoryKnowledgeKnowledgeMergeRef: ArtifactReference       // installed RepositoryKnowledge M2
  repositoryInterpretationRegistryRef: ArtifactReference
  repositoryKnowledgeId
  flowAdmissionDecisionIds[], interpretationProposalDecisionIds[], admittedMeaningIds[]
  registryLineageIds[], technicalFallbackIds[], repositoryKnowledgeItemIds[]
  relationIds[], metricIds[], knowledgeConflictIds[], mergedCanonicalGapIds[]
  semanticItemIds[], ownerSemanticItemIds[], reasonedSemanticExclusionIds[]
  repositoryKnowledgeCoveragePreparationRoot            // hash excluding only this self field

NineSectionDocumentCoveragePreparationV1                // M1 calculation; embedded in the final ledger
  schemaVersion=nine-section-document-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]: AnalysisStepPublicationReference
  repositoryKnowledgePublicationRef: AnalysisStepPublicationReference // RepositoryKnowledge is already installed; no reverse draft reference
  repositoryCoverageLedgerDraftRef: RepositoryCoverageLedgerDraftReferenceV1
  repositoryKnowledgeDraftPreparationRoot               // exact copy of reopened nested RepositoryKnowledgeCoveragePreparationV1 root
  repositoryKnowledgeRef: ArtifactReference
  nineSectionProfileRef: ArtifactReference
  readerSemanticItemIds[]                   // deterministic prospective IDs, UTF-8 sorted
  sectionOwnerBySemanticItem{}               // same values later copied into the plan
  nineSectionDocumentCoveragePreparationRoot            // hash excluding only this self field

RepositoryCoverageLedgerV3                  // M1 module artifact, schema nine-section-document-repository-coverage-ledger-v1
  schemaVersion=repository-coverage-ledger-v3
  repositoryCoverageLedgerId
  repositoryCoverageLedgerDraftRef: RepositoryCoverageLedgerDraftReferenceV1
  nineSectionDocumentCoveragePreparation: NineSectionDocumentCoveragePreparationV1
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  sourceFileIds[], analyzableTextFileIds[], nonAnalyzableMediaFileIds[]
  discoverySiteIds[], entryIds[], graphCandidateIdsByKind{}
  factCandidateKeys[], admittedFactIds[], atomIds[]
  outcomeCandidateIds[], outcomePathIds[]
  flowSliceIds[], evidenceCapsuleIds[]
  modelEligibleFlowSliceIds[], modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  gapIds[]
  registryProposalTaskIds[], registryProposalRoundIds[], registryProposalDispositionIds[]
  registryProposalIds[], acceptedRegistryProposalIds[], rejectedRegistryProposalIds[]
  repositoryInterpretationRegistryItemIds[], provisionalKeys[]
  interpretationTaskIds[], interpretationRoundIds[]
  flowInterpretationDispositionIds[], flowInterpretationCandidateIds[]
  interpretationProposalIds[], interpretationProposalDecisionIds[]
  flowAdmissionDecisionIds[], admittedMeaningIds[], registryLineageIds[], technicalFallbackIds[]
  repositoryKnowledgeItemIds[], relationIds[], metricIds[], knowledgeConflictIds[]
  semanticItemIds[], ownerSemanticItemIds[], reasonedSemanticExclusionIds[]
  readerSemanticItemIds[], sectionOwnerBySemanticItem{}
  analysisStepCoverageRoots[8]
  shardReceipts[], equations[]
  closed: BOOLEAN
  closureReasonCode: STRING?
~~~

`analysisStepCoverageRoots[0..5]`必须依 closed registry 的语义键顺序逐项等于 `upstreamAnalysisStepCoverageRoots` 中 VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation 的 `AnalysisStepPublicationReference.analysisStepArtifactRoot`；`analysisStepCoverageRoots[6]`必须逐字等于 `nineSectionDocumentCoveragePreparation.repositoryKnowledgePublicationRef.analysisStepArtifactRoot`；`analysisStepCoverageRoots[7]`必须逐字等于 `nineSectionDocumentCoveragePreparation.nineSectionDocumentCoveragePreparationRoot`。前者将已安装的 RepositoryKnowledge public set纳入final accounting，后者是**NineSectionDocument 发布前的 coverage root**，不是尚不存在的 NineSectionDocument analysis step root、analysis step receipt、archive、run manifest、M4 reference 或 plan identity。这样 final ledger 可在 plan 前产生并被 plan引用，却仍把NineSectionDocument reader ownership纳入完成性验证。

`RepositoryCoverageLedgerV3`中为便于顶层查询而复制的三个值**不是第二套可选择的数据**。下面三条必须逐字段相等，任何一条不等都是`REPOSITORY_COVERAGE_LEDGER_INVALID`：

~~~text
ledger.repositoryCoverageLedgerDraftRef == ledger.nineSectionDocumentCoveragePreparation.repositoryCoverageLedgerDraftRef
ledger.readerSemanticItemIds == ledger.nineSectionDocumentCoveragePreparation.readerSemanticItemIds
ledger.sectionOwnerBySemanticItem == ledger.nineSectionDocumentCoveragePreparation.sectionOwnerBySemanticItem
~~~

其中前一条比较 `knowledgeAccountingRef`、draft ID 和 schemaVersion；第二条比较有序数组的每一项；第三条比较完整key/value map。M1 在计算 root/ID 前验证该复制关系，外部 validator 也重算它；不能通过同时改两份不相等的字段自证。

`repositoryCoverageLedgerId = repository-coverage-ledger:SHA-256(frame(UTF8("repository-coverage-ledger-id-v3")) || frame(canonicalJson(ledgerWithoutRepositoryCoverageLedgerId)))`；所有列表按声明的稳定 ID/UTF-8 bytes排序，`sectionOwnerBySemanticItem`按key排序。final ledger 的 ID覆盖 draft ref、preparation、所有排序ID集、处置refs、equations与八个coverage roots，不能由 count 单独决定。`closed=true`还要求所有 `equations` 的左右 ID 集合相等、互斥分子不交叠、所有analysis step/shard/preparation roots有效且没有孤儿；此时 `closureReasonCode=null`。任一条件未满足时 `closed=false` 且 `closureReasonCode` 必须是版本化非空 code。

RepositoryKnowledge draft 的 `closedThroughRepositoryKnowledge=true` 同样不是“没有业务Gap”：它要求 `sourceScopeKind=COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation 的全部 roots 与M1/M2 preparation sets有效，并且它自己的全ID-set equations闭合；此时 `closureReasonCode=null`。`BOUNDED_PATH_SET` 必须保持 false，并以 `BOUNDED_PATH_SET_NOT_REPOSITORY_COMPLETE`（或另一版本化、可由ID差集重算的code）说明原因。NineSectionDocument只可在fresh reopen后把这个nested值投影到typed draft reference，不能根据carrier文件名、计数或内存对象猜测其范围。

原先的字段清单在这份 final ledger 中完整保留：

~~~text
sourceFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds
flowSliceIds = modelEligibleFlowSliceIds ⊎ modelIneligibleFlowSliceIds
flowSliceIds ↔ evidenceCapsuleIds ↔ flowAdmissionDecisionIds
modelIneligibleFlowSliceIds -> nonempty modelIneligibilityByFlow.gapIds
modelEligibleFlowSliceIds = set(d.flowSliceId for each canonical FlowInterpretationDisposition d in flow-interpretation-dispositions.jsonl)
registryProposalIds = acceptedRegistryProposalIds ⊎ rejectedRegistryProposalIds
interpretationProposalDecisionIds = KEEP ⊎ NARROW ⊎ DROP ⊎ NEEDS_EVIDENCE ⊎ NEEDS_TERM_REGISTRY
semanticItemIds = ownerSemanticItemIds ⊎ reasonedSemanticExclusionIds
readerSemanticItemIds = exactlyOne sectionOwnerBySemanticItem key
~~~

`sourceScopeKind=COMPLETE_CAPTURE` 是 `repositoryCompletionEligible=true` 的必要条件；`BOUNDED_PATH_SET` 必须为 false。`sourceFileIds` 必须等于 `analyzableTextFileIds` 与 `nonAnalyzableMediaFileIds` 的不交叠 canonical union；后者仍验 size/SHA、保留 snapshot identity，并以 `NON_ANALYZABLE_MEDIA` 明确排除解析，不能从 denominator 消失。

运行状态与分析结果是两个正交字段，不能由一个 `runState` 混写：

- `RunLifecycleState = QUEUED | RUNNING | FINISHED | FAILED` 只描述active v0单进程执行；
- `AnalysisResult = COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE` 只描述完成NineSectionDocument并安装完整root manifest的仓库分析。`start`成功结束或`executeStep.finalAnalysisStepKey=nine-section-document`成功结束时，它随`FINISHED`非空；只完成从ApplicationDiscovery到RepositoryKnowledge中某个请求区间的`FINISHED` analysis step execution没有全仓分析结果，`analysisResult`必须为null；
- `QUEUED/RUNNING/FAILED` 的 `analysisResult` 必须为 null；
- `FAILED` 表示本次执行已经结束但没有合法分析结果。完整安装的上游业务产物仍可供inspect、诊断和新的 `executeStep` 使用；旧run本身不再排队。

requested analysis-step-range completion、terminal analysis result、诊断结果和 fatal 是互斥层级，不能把“某个analysis step发布成功”或“归档模块写成功”误写成完整分析：

| 层级 | 精确条件 | `FINISHED.analysisResult` 与权限 |
| --- | --- | --- |
| requested analysis step range | `executeStep.finalAnalysisStepKey` 是 closed registry 中早于 `nine-section-document` 的语义键，且从target到final的每个请求analysis step publication都已完整安装并fresh-reopen通过 | `analysisResult=null`；该执行是`FINISHED`，其publications可inspect、作为validated upstream供后续新执行引用，但没有Candidate/root run manifest，不能render/trace/Selection |
| complete result | `COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、`closed=true`，且 identity/reference/security/cardinality/Trace 全部有效 | 无 Gap 为 `COMPLETE` / `VALID_COMPLETE`；全分母仍唯一处置且 Gap 不破坏闭包时为 `COMPLETED_WITH_GAPS` / `VALID_COMPLETE_WITH_GAPS`；两者可进入Selection |
| diagnostic final result | `BOUNDED_PATH_SET`，或 `COMPLETE_CAPTURE` 但 coverage ledger以非空closureReason和完整missing-ID accounting合法地保持`closed=false`；已归档子集自身schema/root/reference/Trace完整，且没有可继续执行的安全工作 | 前者为`INCOMPLETE_SCOPE` / `VALID_INCOMPLETE_SCOPE`，后者为`INCOMPLETE_COVERAGE` / `VALID_INCOMPLETE_COVERAGE`；二者都是`FINISHED`最终结果并持久化不可变诊断Candidate，但不可Selection，也不等于成功完成覆盖 |
| fatal / invalid | ledger不能解析或自相矛盾、分母未知、遗漏未显式列出、孤儿/重复/断引用、hash/root/security/cardinality/Trace或原子安装失败 | 不安装success artifact set或validator写`INVALID`；当前run转`FAILED`且`analysisResult=null`，不能降级成诊断结果或业务Gap |

因此 `closed=false` 或 scope非COMPLETE_CAPTURE 本身不是 integrity fatal；**把它冒充 COMPLETE** 才是违反合同。只有在受影响IDs与原因全部显式、已归档闭包仍可重验且当前策略明确产生诊断归档时，才允许 `FINISHED/INCOMPLETE_*`；“不知道漏了什么”或无法构成合法诊断闭包时必须 `FAILED`。调用者之后可以显式创建新执行，但这不会改变旧run结果。

每个 `entryId` 恰好是 `COMPILED(flowSliceId)`、`GAP(gapIds,reasonCode,evidenceRefs)` 或 `EXCLUDED(reasonCode,evidenceRefs)`；每个 `COMPILED` Flow 恰一个 EvidenceCapsule。每个 Capsule 还恰为 `ELIGIBLE` 或 `INELIGIBLE`：后者必须有非空、可重验的`modelIneligibilityGapIds`，但仍留在完整 Flow/Capsule分母。每个 model-eligible Flow先恰一个R0 task/terminal disposition；全部R0分母处置后冻结**一份且仅一份**RepositoryInterpretationRegistry。R0 READY Flow才有该Flow-scoped provisional keys并进入R1/R2；每个model-eligible Flow最终仍恰一个`FlowInterpretationDisposition`：`READY`必须绑定恰一个完整两轮`FlowInterpretationCandidate`，`GAP`或`FAILED`必须绑定typed Gap/failure refs且candidateId为null。model-ineligible Flow没有FlowInterpretation task或disposition；RepositoryKnowledge以它的BusinessFlows eligibility/Gaps产生唯一technical-fallback admission decision。于是RepositoryKnowledge仍为**每个**compiled Flow写恰一个程序admission/fallback decision。RepositoryKnowledge将全部Flow decisions、registry lineage与程序Facts/Gaps合成**一份且仅一份**RepositoryKnowledge；NineSectionDocument每个analysis run只从该knowledge生成**一份且仅一份**NineSectionPlan和document.md。禁止一Flow一Markdown，也禁止先渲染Markdown片段再文本拼接。

资源分片只能改变执行调度，不能改变范围或语义。shard key 固定为稳定 `fileId`、`entryId` 或 `flowSliceId`；每个 shard receipt 保存 denominator IDs、output IDs、controls 和 SHA。validator 要求 shard denominators 两两不相交，按 canonical ID union 后**精确等于**未分片分母；遗漏、重叠、first-N、sample 或超限截断均不允许 COMPLETE。策略明确停止且missing IDs/reason全部可重验时才可归档`FINISHED/INCOMPLETE_COVERAGE`；若缺失破坏引用、身份、安全或诊断闭包则当前run为`FAILED`。只有完整分母里的每项都被唯一处置时，已支持但静态不可知的事项才可作为 typed Gap 随闭合 ledger 进入 `COMPLETED_WITH_GAPS`。

完整验收必须含至少两个不同入口、两个独立Flow/Capsule、两个隔离R0 proposal sets、一份冻结RepositoryInterpretationRegistry、两个`READY`有限key解释candidate/decision和一个跨Flow合并（含关系或指标、冲突或identity合并）。其中“正常全READY”fixture的`E`个eligible Flow各有一条完整`R0 proposal → provisionalKey → R1/R2 selectedKey → meaning` lineage，并精确产生`3E`started calls；另有model-ineligible Flow fixture，验证它保留Capsule/Gap却产生零FlowInterpretation artifact，并由RepositoryKnowledge fallback完整处置。另有R0失败、R0全拒绝与R1/R2失败fixture，证明每种都会成为typed Gap/FAILED disposition而不会吞掉Flow或用seed/别Flow补词。改变shard size或程序遍历顺序后，所有analysis step public bytes、registry、RepositoryKnowledge、NineSectionPlan和document.md必须逐字节相同。业务上可表达的模型回答由FlowInterpretation写typed disposition，并可在全分母仍唯一处置时随闭合ledger进入`FINISHED/COMPLETED_WITH_GAPS`；Provider调用开始后的transport/runtime失败使当前run `FAILED`，不自动重试或切换。只有ledger全部分母均有唯一处置、所有analysisStepCoverageRoots验证通过且单一仓库文档closure成立，run才可进入`FINISHED`并得到`COMPLETE/COMPLETED_WITH_GAPS`；前者无Gap，后者仅含已显式计数且不破坏覆盖账本闭合的Gap。

## 4. 分析步骤“已验证源码清单”：冻结来源

### 分析步骤“已验证源码清单” 之前：独立本地 capture seam

`LocalGitCommitCaptureAdapter` 是显式维护 Adapter，不是 `RepositoryAnalysisAgent` 的第八个方法，也不在 analysis worker 内。它唯一允许接受主机路径：调用者给出本地 Git repository path 与小写完整 40-hex commit；Adapter 只按该 object ID 打开 commit/tree/blob objects，不读取 branch/ref、index、工作区文件、submodule 工作区、hooks、filters 或客户代码，也没有网络/fetch/promise-object fallback。v0实现固定使用本机Git CLI plumbing而不增加JGit依赖：Git executable先解析为受信absolute path，ProcessBuilder不经shell；每个子进程先`environment.clear()`，再只设置`LC_ALL=C`、`LANG=C`、`HOME=<private-empty-dir>`、`XDG_CONFIG_HOME=<private-empty-dir>`、`GIT_CONFIG_NOSYSTEM=1`、`GIT_CONFIG_GLOBAL=<private-empty-file>`、`GIT_NO_LAZY_FETCH=1`、`GIT_TERMINAL_PROMPT=0`、`GIT_OPTIONAL_LOCKS=0`、`GIT_PAGER=cat`、`PAGER=cat`。没有继承的`PATH`或其他ambient variable；`--git-dir=<NOFOLLOW-validated-absolute-git-dir>`与`--no-replace-objects`只作为固定argv传入。命令固定为`cat-file` raw bytes与NUL-delimited tree enumeration；不得调用alias、hook、filter、credential helper或pager。解析得到的git-dir及其object/config路径逐段NOFOLLOW验证；local config中的include/promisor/partial-clone/alternates相关设置、objects/info/alternates、replace/graft、shallow/missing object一律拒绝。任何命令前后对象库identity或安全检查漂移均使capture失败。

Adapter 递归枚举该 commit 的完整 tracked tree。tree 容器不计文件；`100644`/`100755` blob 是 regular-file denominator，每个 blob 均读取、算 SHA-256并原样安装到 content-addressed immutable snapshot。能严格解码为 UTF-8 且不含 NUL/禁用控制字节的 regular file 标为 `ANALYZABLE_TEXT`并建立line index；其余 regular file标为`NON_ANALYZABLE_MEDIA`，仍计数、验hash、保留bytes，但任何parser都不得打开它。`120000` symlink、`160000` gitlink/submodule或其他v0未知mode使整次capture fail closed；不得把它们记成media排除后继续。

capture 原子产生 `snapshot-manifest.jsonl`、`capture-receipt.json`（`local-git-capture-receipt-v1`）和rootless `source-registration.json`（`source-registration-v1`），并把本机storage locator仅放进private registry。任一object缺失/损坏、commit不是exact 40-hex commit、unsupported tree entry或安装失败都不创建可用registration。分析调用者此后只传`sourceRegistrationId`与content-addressed request/ref；本地path不得进入run request、artifact、View、error或identity。

### 为什么存在

所有后续 locator、图、Proof 和 Trace 都依赖“同一路径仍然是同一字节”。本分析步骤把上游 capture 与本地分析分开；它只验证调用方已明确选定的离线快照。

### 具体输入

`sourceRegistrationId`、content-addressed `frozenRepositoryRequestRef`、固定仓库 identity/revision、capture receipt、**完整**声明文件 inventory、验证策略和预算。analysis request没有snapshot root；VerifiedSourceInventory通过注入的private source registry把registration ID解析为opaque read-only handle。生产 run 的 `inventoryScope` 必须是 `COMPLETE_CAPTURE`；DepotHead walkthrough 使用固定 commit 的八文件 `BOUNDED_PATH_SET` 仅作局部 fixture，明确 `repositoryCompletionEligible=false`，不能使 run 完成。

### 工作步骤

1. 校验 origin、revision、capture receipt 和 inventory digest。
2. 规范化相对路径；拒绝绝对路径、逃逸、重复路径和registration/capture中的symlink、gitlink或未知tree mode。
3. 对每个regular file在分配前校验类型与大小，再流式重算 SHA-256；`NON_ANALYZABLE_MEDIA`也必须完成这一步。
4. 只对`ANALYZABLE_TEXT`严格 UTF-8 解码并建立 byte/line index；media的line index/encoding保持null且显式禁止parser消费。
5. 验证`trackedRegularFileIds = verifiedRegularFileIds ⊎ unverifiedRegularFileIds`、`verifiedRegularFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds`、`unverifiedRegularFileIds=∅`及capture full-tree proof，计算 root-independent snapshot identity。
6. M1/M2各自先安装canonical payload+module receipt；M3从fresh reopen的M1/M2形成恰三个semantic payload并安装自己的receipt；`CanonicalAnalysisStepArtifactStore`重开M3、计算analysis step root并最后创建analysis step receipt。

### 可观察产物

- source-input.json
- verified-snapshot.json
- source-inventory.jsonl
- verified-source-inventory-receipt.json

### 下游如何消费

分析步骤“应用发现” 只读取 verified-snapshot.json 和 source-inventory.jsonl 中列出的 handle identity；它不能 walk 工作树、补文件或改路径解释。

### 成功、Gap、fatal 与角色

全部声明regular files闭合才成功；media可明确排除解析，但不能从file denominator消失或跳过hash。局部超预算只有在 profile 明确允许隔离时形成范围 Gap；来源缺失、hash 漂移、路径越界、symlink/gitlink/未知mode或安全策略不可执行是 fatal。程序负责全部工作，LLM 角色为零。

## 5. 分析步骤“应用发现”：发现应用类型和入口

### 为什么存在

文件冻结不等于知道它是什么应用，也不等于知道从哪里进入。先发现应用形态和入口，后续图构建才有明确根和能力边界。

### 具体输入

分析步骤“已验证源码清单” 的 persisted snapshot、inventory、版本化 application-discovery profile 和预算。DepotHead 输入包含 pom.xml、application.yml、Controller、Service、entity、Example、Mapper Java 和 Mapper XML。

生产 分析步骤“应用发现” 必须遍历完整 VerifiedSourceInventory inventory 中所有受支持 discovery site；DepotHead route 只是全入口 inventory 中一项。任何入口 candidate 都必须形成 supported EntryPoint、带证据 EXCLUDED，或 typed Gap，不得因 parser/预算/分片失败从 denominator 消失。

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
- application-discovery-receipt.json

### 下游如何消费

分析步骤“程序图” 直接读取入口和 catalog identity，按入口建立图根；它不重新解析 pom、配置或注解来“再发现一次”。

### 成功、Gap、fatal 与角色

一个入口可以带 capability Gap，但不能从分母消失。全局 parser 安全失败、source reopen 漂移或 catalog 自相矛盾为 fatal。程序负责，LLM 角色为零。

## 6. 分析步骤“程序图”：构建五张正式程序图

### 为什么存在

业务流程不是文件顺序。只有结构、调用、控制、数据和证据五类关系同时显式存在，程序才能证明“谁调用谁、在什么条件下、哪些值流到哪、为什么相信这条边”。

### 具体输入

分析步骤“已验证源码清单” snapshot 与 分析步骤“应用发现” application/entry artifacts，加上版本化 graph profile、parser/toolchain identity 和预算。

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
- program-graphs-receipt.json

### 下游如何消费

分析步骤“已证明代码事实” 只遍历这五张 persisted graph 和 graph-index；它不重新打开 AST 来修补缺边。分析步骤“业务流程” 也复用控制/调用/数据图，不另写一套 parser。

### 成功、Gap、fatal 与角色

局部不支持或歧义边进入 graph-gaps.jsonl，并明确受影响入口。断引用、重复 canonical ID、数据流假绑定、XML 外部解析尝试或图账本不闭合为 fatal。程序负责，LLM 角色为零。

## 7. 分析步骤“已证明代码事实”：证明代码事实

### 为什么存在

一条图边存在，不等于复合业务陈述成立。本分析步骤枚举每个候选事实必须包含的全部语义原子，再逐原子证明；缺一项就拒绝整条复合 Fact，而不是写半句真话。

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
- proven-code-facts-receipt.json

### 下游如何消费

分析步骤“业务流程” 使用 CodeFact、Proof ID、Gap 和五图引用编译流程；它不能借用未被 Fact 引用的 Evidence，也不能把 EvidenceCapsule 当 Proof。

### 成功、Gap、fatal 与角色

Fact rejection 和非阻塞 Gap 可以属于成功结果；Proof reference 断裂、source reopen 漂移、冲突 Fact 同时 admitted 或 accounting 不闭合为 fatal。程序负责，LLM 角色为零。

## 8. 分析步骤“业务流程”：编译完整业务流程和逐流程阅读包

### 为什么存在

事实列表还不能告诉读者一个入口怎样开始、经过哪些分支、在哪里结束。模型也不应看到整个仓库。本分析步骤对 ApplicationDiscovery 的**每个入口**给出唯一 disposition：支持的入口编译成完整入口根 Flow，并为每个 Flow 生成最小、封闭、预算内的 EvidenceCapsule；不支持/失败的入口保留证据和 Gap。DepotHead 只是 `N` 个 slice 中一个。

### 具体输入

分析步骤“应用发现” 入口清单、分析步骤“程序图” 五图、分析步骤“已证明代码事实” Facts/Proof/Gaps、flow/evidence profiles 和预算。

### 工作步骤

1. 每个 entryId 建一个入口根遍历，沿唯一调用边和显式控制流边前进。
2. 保留 call/return 配对和每个 guard 的 TRUE/FALSE 极性。
3. 每个 return、throw 或受支持 stop 成为 OutcomePath；所有终点必须进入 compiled、Gap 或 reasoned exclusion。
4. 为 Fact、atom、Outcome 和 Gap 建 flow ownership；禁止第二入口借用不属于自己的事实。
5. 从 Proof root 投影直接表达业务语义的最小、非重叠 source spans，并把该 Flow 的 admitted Fact 原子、Gap reason/evidence、Outcome guard/terminal 与R0/R1/R2可引用的basis atom/Gap集合复制进 Capsule。
6. 建 projection obligations，逐一证明删除任一 span 会损失至少一个义务；同一 evidence-complete Capsule 是该Flow三轮模型任务的唯一source projection。
7. 自验入口、Outcome、Fact、atom、Gap 和 Capsule 覆盖后原子安装。

### 可观察产物

- flow-slices.json
- flow-coverage.json
- entry-dispositions.jsonl
- evidence-capsules.jsonl
- flow-gaps.jsonl
- business-flows-receipt.json

### 下游如何消费

FlowInterpretation为BusinessFlows的每个model-eligible Flow先建立一个隔离R0 registry-proposal工作项，再在全仓R0分母处置并冻结唯一RepositoryInterpretationRegistry后，为R0 READY的同一Flow建立有限key R1/R2工作项。三轮都只能读这一条FlowSlice和它唯一对应的EvidenceCapsule；optional organization seed只是R0的content-addressed候选词输入，不能替代Capsule basis。FlowInterpretation不能读仓库目录、五张全图、其他Flow、未准入Fact或任意本地路径；调度/分片可变，但Flow集合、三轮closure和最终canonical bytes不变。一条Flow成功不能结束FlowInterpretation或整个run。

### 成功、Gap、fatal 与角色

某入口无法闭合时可记录GAP disposition；0 Flow、0 Capsule仍可成为诚实的SUCCEEDED_WITH_GAPS分析步骤结果，并让后续生成零R0/R1/R2任务。引用断裂、coverage不守恒、Capsule source hash漂移或R0/R1/R2读取不同source projection为fatal。程序负责，LLM角色为零。

## 9. 分析步骤“流程解释”：一次只解释一个流程

### 为什么存在

确定性分析能证明“发生了什么”，但预置词表无法覆盖每个新客户仓库。模型先在隔离R0为单个Flow提出有证据basis的bounded业务词、claim和question；程序把全仓R0结果验证、处置并冻结为唯一RepositoryInterpretationRegistry，随后模型只能在同一Flow的有限provisional keys里做R1解释与R2精度复核。模型始终不是编译器、证明器、准入者或文档作者。

### 具体输入

全量eligible Flow/Capsule双射、optional content-addressed organization registry seed、R0/R1/R2严格output schemas与prompt bundles、expected runtime identity和预算；执行时一次只取其中一对，跨Flow session永不共享。DepotHead当前0 Capsule，所以它产生0 task、0 Provider call，不妨碍同仓库其他eligible Flow独立处理。

### 工作步骤

1. 重验完整Flow/Capsule双射及其`ELIGIBLE ⊎ INELIGIBLE` partition、R0 schema/prompt/runtime/budget和optional seed identity；只为每个eligible Flow编译恰一个隔离`R0_REGISTRY_PROPOSAL` task。ineligible Flow保留BusinessFlows Gap、零FlowInterpretation task/round/disposition，交由RepositoryKnowledge写唯一technical fallback。
2. 按canonical task顺序执行R0。模型只能返回bounded `proposalKind/label/purpose/basisAtomIds/basisGapIds/sourceSeedKey?`；程序验证后才产生`normalizedLabel/normalizedPurpose`，模型不能返回Fact、locator、Flow、Outcome、Markdown或Capsule外reference。
3. 程序逐proposal做exact schema、Unicode/size/control-character、basis closure、seed exact-match和identity校验；每个proposal唯一ACCEPTED/REJECTED disposition，跨Flow同名不静默合并。
4. 全部R0 dispositions闭合后，程序按flow/proposal排序生成provisional keys并原子冻结一份RepositoryInterpretationRegistry；失败Flow仍在coverage中。
5. 只为R0 READY Flow按其同Flow registry items编译有限key R1/R2；R1提出selectedKey+basis，且selectedKey必须逐字等于同Flow registry item的provisionalKey。只有R1返回`RESPONSE_ACCEPTED`才调用R2；若R1返回typed `RESPONSE_GAP|RESPONSE_FAILED`，已规划的同Flow R2 task写`NOT_RUN_UPSTREAM_FAILED` disposition并引用R1 taskSpecId，不调用Provider。R2在同一session只能保持该selectedKey；`NARROW`只可收窄decision、basis子集或meaning eligibility，不能替换/派生key，也不能增加来源、Fact、proposal或basis。
6. Provider调用开始后若transport/runtime中断、响应缺失或无法形成可验证response record，当前run直接`FAILED`，不自动重试、不切换Provider，也不生成FlowInterpretation success publication。
7. 保存canonical tasks、rounds、generation receipts、registry、candidates和typed dispositions。RepositoryKnowledge只从FlowInterpretation的**九份语义文件及其analysis step receipt**枚举全部planned tasks与处置。令`E`为eligible Flow数、`R`为R0 READY数，FlowInterpretation精确产生`E + 2R`个planned tasks；成功发布时每个task恰有一个typed disposition。持久化round与started Provider call只是task集合的可验证子集；只有所有task都成功started且`R=E`的正常全READY分支才有`3E` calls。本分析步骤不自批最终业务解释。

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
- flow-interpretation-receipt.json

### 下游如何消费

RepositoryKnowledge读取canonical R0/R1/R2 rounds、唯一frozen registry、registry/interpretation proposal refs和Flow dispositions，逐项重算`registryProposalId→provisionalKey→selectedKey`及basis closure；它不重新调用模型，也不信任模型自报的decision。

### 成功、Gap、fatal 与角色

没有Flow时仍写出全部十个命名文件，其中JSONL为零行、registry为canonical empty、调用数为0。R0没有可接受item、R0/R1/R2安全隔离失败或合法但没有合适term时，为该Flow写typed Gap/FAILED disposition并让RepositoryKnowledge使用技术回退；不能从分母删除。schema、runtime identity、unknown/cross-Flow reference、seed漂移、R2 expansion或Provider started后失败为fatal；当前run失败且不得自动重试、换Provider或回退API key。程序冻结任务、验证/冻结registry并校验传输；LLM在R0只提出bounded仓库词候选，在R1/R2只选择有限key。

## 10. 分析步骤“仓库知识”：准入解释并合并仓库业务知识

### 为什么存在

模型提案不是事实，多个 Flow 的同名对象也不一定是同一对象。本分析步骤对**全部 slice** 的候选逐一作 KEEP、NARROW、DROP 或 NEEDS_EVIDENCE 决定，再把全部程序 Facts/Gaps 与 admitted interpretations 按 Proof-backed technical anchor 合并成**一份且仅一份** RepositoryKnowledge，显式保存跨 Flow identity、关系、指标和冲突。

### 具体输入

ProvenCodeFacts Facts/Proof/Gaps、BusinessFlows Flow/Capsule、FlowInterpretation canonical R0/R1/R2 artifacts、唯一RepositoryInterpretationRegistry、registry proposal dispositions、knowledge profile和预算。

### 工作步骤

1. 对每个R1 interpretation proposal重新验证`registryProposalId→provisionalKey→selectedKey`、同Flow eligibility和basis；selectedKey必须逐字等于该registry item provisionalKey。`NARROW`只收窄程序decision、仍闭合的basis子集或meaning eligibility，绝不改写selectedKey。
2. 用更保守的程序 decision 覆盖模型自评；开放文本不进入知识。
3. 没有 admitted term 时使用 total TechnicalDisplayRegistry。
4. 按 FQN、SQL table、Flow/Outcome ID 或 Proof-backed equivalence edge 合并；中文名或 simple name 不能作为 identity。
5. 保持 Proven Fact、Admitted Interpretation、Gap 三种知识等级。
6. admitted meaning保留registryProposalId、provisionalKey、interpretationProposalIds和R0/R1/R2 basis/receipt lineage；跨Flow同名只有Proof-backed anchor equivalence才可在本分析步骤合并。
7. 给每个atom、meaning、registry lineage和Gap唯一owner，解决或显式记录冲突。
8. 重算仓库级accounting后原子安装。

### 可观察产物

- admitted-flow-meanings.jsonl
- repository-business-knowledge.json
- knowledge-conflicts.jsonl
- knowledge-accounting.json
- merged-gaps.json
- repository-knowledge-receipt.json

### 下游如何消费

NineSectionDocument只读repository-business-knowledge.json、merged-gaps.json和ownership/accounting，并通过FlowInterpretation/07 typed IDs编译registry-aware Trace；它不读raw reasoning，也不重新决定term。ReaderItem可展示已准入label，但必须携带registryProposalId/interpretationProposalId lineage。

### 成功、Gap、fatal 与角色

所有业务 term 被 DROP 仍可成功，技术显示保证文档可生成。未解决的业务问题保留为 Gap。双 owner、无 owner、anchor 冲突未处置或 registry 漂移为 fatal。程序拥有最终准入和 merge；LLM 在本分析步骤无执行角色。

## 11. 分析步骤“九章文档”：九章、Markdown、Trace、验证、归档与观察

### 为什么存在

唯一 RepositoryKnowledge 还需要稳定地分配到读者结构中，并与完整分析 lineage 一起保存。每个完成的分析链只生成一份仓库级 NineSectionPlan 和一份 document.md；只保存 document.md 不能证明它如何产生，也不能让validation或后续显式执行重验输入。禁止逐 Flow 生成 Markdown 或拼接预渲染片段。

### 具体输入

前七分析步骤的 immutable analysis step references、唯一 RepositoryKnowledge、`analysis-run-request-v2`、NineSectionProfile、section ownership/template profiles、Candidate series/round lineage、content-addressed archive/trace/validation policies 和预算。NineSectionDocument不得另收profile、budget、toolchain、schema、prompt或organization seed的inline/隐式副本。

### 工作步骤

1. M1 fresh-reopen 仓库知识的五个semantic artifacts、`repository-knowledge-receipt.json` **以及唯一明确列出的流程解释 `repository-interpretation-registry.json`**，把全部七个实际读取的ArtifactReference放入自己的direct preimage/envelope；再用 `RepositoryCoverageLedgerDraftReferenceV1` 同时核对`knowledge-accounting.json` carrier artifact、nested draft ID和schema，并 fresh-reopen 已安装的仓库知识 publication。除这一份为 ReaderItem registry-lineage exact join 所必需的流程解释 registry 外，M1 **不**重开已验证源码清单至业务流程的原始bytes或其他流程解释 bytes；它只验证nested draft内的其余上游 `AnalysisStepPublicationReference` 的wire形状、同run/语义key、排序、唯一性以及与draft/preparation中被复制字段的逐字一致性。真正逐bytes重开全部上游bytes的职责属于外部 validator。拒绝把普通 ArtifactReference 或draft自身冒充final ledger。
2. 将每个Fact atom、admitted meaning、registry lineage、technical fallback和Gap分配到唯一章节owner，先得到 prospective `readerSemanticItemIds[]` 与 `sectionOwnerBySemanticItem{}`。
3. M1先计算不含plan identity的 `NineSectionDocumentCoveragePreparationV1`，再安装唯一`nine-section-document-repository-coverage-ledger-v1` final-ledger module artifact；它以上游六个语义roots、已安装仓库知识 publication和nested draft为输入。final ledger固定后，M1才生成并与ledger在同一receipt下原子安装恰好九章的`nine-section-document-nine-section-plan-draft-v3` module payload。这个draft是M2唯一可读的plan语义bytes，不是尚未创建的public analysis step file。
4. 生成typed ReaderItem和disposition；每个语义原子必须进入正文、技术依据、Gap或有理由排除，admitted label必须引用registryProposalId/interpretationProposalId，且plan逐字引用刚安装的final ledger。
5. M2 renderer **只读取从M1 receipt重开的plan-draft payload**，把UTF-8/LF Markdown bytes封入自己的machine artifact；不得打开源码、模型response或registry。run完成后的外部`render`/validator才读取public `nine-section-plan.json`作同义重渲染。
6. M3用同一M1 plan/ledger编译 `reader item → knowledge → meaning → interpretationProposal/selectedKey → provisionalKey/registryProposal/R0 receipt/basis → Fact/Gap → Proof → Evidence → snapshot` 的typed Trace。
7. 每个模块先立即安装canonical JSON/JSONL+module receipt；renderer的Markdown bytes先封在machine artifact中。M4从fresh reopened M1 plan draft/final ledger、M2 document machine artifact和M3 trace确定性产生五个semantic analysis step payload：`nine-section-plan.json`、唯一`document.md`、`trace.jsonl`、`candidate.json`、`validation-baseline.json`。
8. M4 coordinator按固定依赖顺序编排三个deep store：`CanonicalAnalysisStepArtifactStore`从五个semantic payload生成`nine-section-archive-manifest.json`并创建绑定M1–M3+request/upstream/final-ledger provenance（明确不绑定尚不存在的M4 reference）的`nine-section-document-receipt.json`；`CanonicalRunManifestStore`再把唯一`runs/<runId>/run-manifest.json`安装为第八个逻辑九章文档输出；最后`CanonicalModuleArtifactStore`才安装唯一M4 publication payload及其module receipt，使它反向绑定八项references。不得在analysis step目录或M4 payload内嵌第二份RunManifest。
9. 只有M4 publication/receipt也已安装后，single worker才把运行状态写为`FINISHED`，绑定四选一`AnalysisResult`和root run-manifest reference；只有complete/eligible/closed映射COMPLETE类结果，合法诊断映射INCOMPLETE类结果。integrity、Provider或执行失败统一为`FAILED`且没有result。
10. 单一RepositoryAnalysisAgent按run identity提供start/executeStep/inspect/artifact/render/validate/trace；artifact查询禁止Path，所有Java/CLI/loopback HTTP Adapter共用core manifest/root/security规则。start/executeStep只创建新run并排入当前单进程worker。validate/trace可在独立进程重开来源和所有分析步骤产物；executeStep从显式validated upstream publications开始新执行，inspect/artifact/render不调用Provider或修改业务产物。

### 可观察产物

- `steps/08-nine-section-document/nine-section-plan.json` — `nine-section-document-nine-section-plan-v3`
- `steps/08-nine-section-document/document.md` — `nine-section-document-document-markdown-v1`
- `steps/08-nine-section-document/trace.jsonl` — `nine-section-document-trace-record-v3`
- `steps/08-nine-section-document/candidate.json` — `nine-section-document-candidate-v4`
- `steps/08-nine-section-document/validation-baseline.json` — `nine-section-document-validation-baseline-v1`
- `steps/08-nine-section-document/nine-section-archive-manifest.json` — `nine-section-document-archive-manifest-v1`
- `steps/08-nine-section-document/nine-section-document-receipt.json` — `analysis-step-receipt-v1`
- `runs/<runId>/run-manifest.json` — `run-manifest-v1`

这八项是八个独立schema，不是一个嵌套RunManifest的八个视图。NineSectionDocument artifact root只覆盖前五个semantic payload；archive manifest描述并绑定这五项及root，但不加入该root；analysis step receipt绑定五项root、archive manifest和acyclic preparation provenance，排除自身且不声称绑定尚不存在的M4；root run manifest绑定 closed registry 中八个语义键的 references 与 receipt SHA，排除自身；M4 publication/receipt最后绑定上述八项及其实际位置。分析步骤“九章文档” 不吞并前七分析步骤目录；Candidate 和 run manifest 引用它们的 Merkle roots。这样 final archive 不再是唯一可观察状态。

### 下游如何消费

审阅者读document.md；Trace/validation工具读plan、trace、Proof和snapshot；Selection流程读immutable Candidate reference。Java、CLI和loopback HTTP调用者只学习同一个run-centric Interface：inspect看状态/coverage，artifact按identity取验证后的metadata或预算内完整bytes（超限全量拒绝），render从唯一plan确定性重验document。任何下游都不需要重新调用模型或遍历run目录。

### 成功、Gap、fatal 与角色

九章可以诚实呈现 Gap 和空业务覆盖，但不能静默丢失知识。只有 RepositoryCoverageLedger 对完整仓库闭合、唯一 knowledge→唯一 plan/document cardinality 成立才可完成 run；单一 Flow PASS 永远不足。缺章、多章、错序、atom loss、document hash drift、Trace 断裂、archive collision 或无法原子安装为 fatal。程序规划、渲染、验证、归档和显式artifact复用；LLM 不写 Markdown。

## 12. 业务产物持久化与显式复用

### 12.1 AnalysisStep receipt

每个成功或带 Gap 成功的分析步骤都有唯一 `analysis-step-receipt-v1`，由 `CanonicalAnalysisStepArtifactStore` 在 semantic payloads 与可选archive manifest全部写满、自验、force之后创建。调用者不构造receipt bytes、receipt ID、root或任何Path。精确字段为：

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 下例完整展示ProgramGraphs receipt record和typed VerifiedSourceInventory/02 lineage；所有ID/digest/size只满足grammar，未由展示bytes重算，不是strict replay golden。

~~~json
{
  "schemaVersion": "analysis-step-receipt-v1",
  "analysisStepReceiptId": "analysis-step-receipt:0000000000000000000000000000000000000000000000000000000000000000",
  "analysisStepArtifactRoot": "analysis-step-root:1111111111111111111111111111111111111111111111111111111111111111",
  "runId": "analysis-run:2222222222222222222222222222222222222222222222222222222222222222",
  "analysisStepKey": "program-graphs",
  "publicationProvenance": {
    "kind": "ANALYSIS_STEP_PUBLISHER_MODULE",
    "publisherSpecificationModuleReference": {
      "address": {
        "kind": "ANALYSIS_STEP",
        "runId": "analysis-run:2222222222222222222222222222222222222222222222222222222222222222",
        "analysisStepKey": "program-graphs",
        "moduleNumber": 6,
        "moduleKey": "publish"
      },
      "moduleArtifactRoot": "module-root:4444444444444444444444444444444444444444444444444444444444444444",
      "moduleReceiptId": "module-receipt:5555555555555555555555555555555555555555555555555555555555555555",
      "moduleReceiptSha256": "6666666666666666666666666666666666666666666666666666666666666666"
    }
  },
  "upstreamAnalysisStepReferences": [
    {
      "address": {"runId": "analysis-run:2222222222222222222222222222222222222222222222222222222222222222", "analysisStepKey": "verified-source-inventory"},
      "analysisStepArtifactRoot": "analysis-step-root:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "analysisStepReceiptId": "analysis-step-receipt:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "analysisStepReceiptSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    },
    {
      "address": {"runId": "analysis-run:2222222222222222222222222222222222222222222222222222222222222222", "analysisStepKey": "application-discovery"},
      "analysisStepArtifactRoot": "analysis-step-root:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
      "analysisStepReceiptId": "analysis-step-receipt:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
      "analysisStepReceiptSha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    }
  ],
  "status": "SUCCEEDED_WITH_GAPS",
  "controls": {
    "toolchainSha256": "7777777777777777777777777777777777777777777777777777777777777777",
    "profileSha256": "8888888888888888888888888888888888888888888888888888888888888888",
    "schemaBundleSha256": "9999999999999999999999999999999999999999999999999999999999999999",
    "promptBundleSha256": null,
    "artifactPolicyRegistryRef": {"artifactId": "artifact-policy-registry:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
  },
  "semanticArtifacts": [
    {"fileName": "call-graph.json", "artifactType": "PROGRAM_GRAPHS_CALL_GRAPH", "schemaVersion": "program-graphs-call-graph-v1", "artifactId": "program-graphs-call-graph:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "mediaType": "application/json", "sizeBytes": 1, "sha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},
    {"fileName": "code-structure-graph.json", "artifactType": "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH", "schemaVersion": "program-graphs-code-structure-graph-v1", "artifactId": "program-graphs-code-structure:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "mediaType": "application/json", "sizeBytes": 1, "sha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"},
    {"fileName": "control-flow-graph.json", "artifactType": "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH", "schemaVersion": "program-graphs-control-flow-graph-v1", "artifactId": "program-graphs-control-flow:0000000000000000000000000000000000000000000000000000000000000000", "mediaType": "application/json", "sizeBytes": 1, "sha256": "1111111111111111111111111111111111111111111111111111111111111111"},
    {"fileName": "data-flow-graph.json", "artifactType": "PROGRAM_GRAPHS_DATA_FLOW_GRAPH", "schemaVersion": "program-graphs-data-flow-graph-v1", "artifactId": "program-graphs-data-flow:2222222222222222222222222222222222222222222222222222222222222222", "mediaType": "application/json", "sizeBytes": 1, "sha256": "3333333333333333333333333333333333333333333333333333333333333333"},
    {"fileName": "evidence-graph.json", "artifactType": "PROGRAM_GRAPHS_EVIDENCE_GRAPH", "schemaVersion": "program-graphs-evidence-graph-v2", "artifactId": "program-graphs-evidence:4444444444444444444444444444444444444444444444444444444444444444", "mediaType": "application/json", "sizeBytes": 1, "sha256": "5555555555555555555555555555555555555555555555555555555555555555"},
    {"fileName": "graph-gaps.jsonl", "artifactType": "PROGRAM_GRAPHS_GRAPH_GAP", "schemaVersion": "program-graphs-graph-gap-v1", "artifactId": "program-graphs-graph-gaps:6666666666666666666666666666666666666666666666666666666666666666", "mediaType": "application/x-ndjson", "sizeBytes": 1, "sha256": "7777777777777777777777777777777777777777777777777777777777777777"},
    {"fileName": "graph-index.json", "artifactType": "PROGRAM_GRAPHS_GRAPH_INDEX", "schemaVersion": "program-graphs-graph-index-v1", "artifactId": "program-graphs-graph-index:8888888888888888888888888888888888888888888888888888888888888888", "mediaType": "application/json", "sizeBytes": 1, "sha256": "9999999999999999999999999999999999999999999999999999999999999999"}
  ],
  "archiveManifest": null,
  "gapCount": 1,
  "gapRefs": ["gap:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
}
~~~

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 上例字段/variant完整且ProgramGraphs direct lineage精确包含VerifiedSourceInventory、ApplicationDiscovery两个`AnalysisStepPublicationReference`并按closed registry依赖顺序排列；普通full-run路径中producer runId相同，显式`executeStep`路径允许producer runId不同，但必须通过12.2的同一frozen-basis/controls/连续analysis step验证。其中digest/size值只是grammar-valid specimen，并不声称是所示payload的实际重算结果。每个content ID仍严格满足13.3.1的`<prefix>:<64 lowercase hex>` safe grammar，production必须从canonical bytes重算，不能复制specimen digest。

`semanticArtifacts`按UTF-8 `fileName` byte order严格递增且无重复；它不列archive manifest、receipt或root run manifest。前七个分析步骤的`publicationProvenance.kind=ANALYSIS_STEP_PUBLISHER_MODULE`并绑定已经安装的publisher **specification** module reference；例如VerifiedSourceInventory精确绑定M3，而M3只含三个已注册semantic payload bytes/descriptors，绝不预报analysis step root或receipt。NineSectionDocument因批准的依赖顺序要求M4最后，使用`NINE_SECTION_DOCUMENT_COORDINATOR_PREPARATION`，绑定M1–M3 module references、analysis request reference、七个上游分析步骤references与M1 `nine-section-document-repository-coverage-ledger-v1` final ledger reference，明确不含尚不存在的M4 reference；它绝不能绑定RepositoryKnowledge draft。M4稍后反向绑定analysis step receipt/run manifest，不形成cycle。

前七个分析步骤的`archiveManifest`恒为null；NineSectionDocument为`{fileName,artifactType,schemaVersion,artifactId,mediaType,sizeBytes,sha256}`，绑定已固定的五项semantic set/root。`analysisStepArtifactRoot`只由semantic descriptor list计算；analysis step receipt ID排除且只排除`analysisStepReceiptId`本身，receipt也不加入analysis step root。后继analysis step或最终run manifest记录receipt文件SHA，从而避免自引用。

最终`run-manifest-v1`由 `CanonicalRunManifestStore` 单独安装到 `runs/<runId>/run-manifest.json`；它不是NineSectionDocument目录成员。它包含`runManifestId`、当前NineSectionDocument execution的`runId`、`analysisRunRequestRef`、按closed registry依赖顺序排列的`analysisStepPublications[8]`（address/root/receipt ID/SHA）、`repositoryCoverageLedgerRef`、唯一registry/knowledge/plan/document/trace/candidate refs、`analysisResult`和`controls`。full-run路径的八个producer runId相同；显式analysis step链可以不同，但每个cross-run edge都必须满足12.2验证。identity排除且只排除`runManifestId`；它不含mutable lifecycle、queue、worker或M4 receipt。M4 module publication稍后引用run-manifest reference，不能反向写入该manifest；轻量execution status不进入manifest或任何canonical identity。

### 12.2 已验证产物的显式新执行

AnalysisStep/module JSON、JSONL、receipt 与 publication reference 是业务分析数据流合同和上下游对话格式。它们不是 runtime journal，也不是为了进程崩溃恢复才存在。active v0 必须允许调用者显式创建一个新的 analysis step execution，直接消费完整验证的上游 publication references：

1. `executeStep(AnalysisStepExecutionRequest)` 每次产生新的 `runId`（即本次执行 identity）；它从不重新激活旧 run，也不继承旧 queue、worker、Provider session 或模型调用状态。
2. request 固定 `targetAnalysisStep`、连续的 required upstream `AnalysisStepPublicationReference[]`、原始 `AnalysisRunRequest`/source registration 及全部 tool/profile/schema/prompt/policy/budget refs。它不接受 Path、目录、latest、模糊 analysis step key 或内存对象。
3. core fresh-reopen 每个引用的 analysis step receipt、semantic artifacts 与目标分析步骤实际需要的 module payload，重算 SHA/root/schema/controls/lineage。不同 producer `runId` 可以出现在输入链中，但 frozen source、request lineage 与 controls 必须逐字相同，semantic keys 必须形成closed registry中的连续依赖前缀且 DAG 无环；任一不等立即拒绝新执行。
4. 通过验证后只运行 `targetAnalysisStep` 及调用者明确请求的后续分析步骤；不得重扫源码或重做有效上游。新 analysis step receipt 保存实际消费的 upstream publication refs，因而下游能继续以内容身份复用该链。
5. 例如 RepositoryKnowledge 失败后，调用者可创建 `targetAnalysisStepKey=repository-knowledge` 的新执行，传入已验证的 VerifiedSourceInventory 到 FlowInterpretation publications。RepositoryKnowledge 直接重开 ProvenCodeFacts Facts、BusinessFlows Flows/Capsules、FlowInterpretation registry/tasks/rounds/dispositions，重新执行 RepositoryKnowledge；这些上游分析步骤不运行，Provider 调用数为零。其输出可再作为 NineSectionDocument 新执行的输入。
6. 显式复用不保证旧执行与新执行的 runtime 状态连续，也不保证 Provider exactly-once。若调用者选择从 BusinessFlows 重新启动 FlowInterpretation，那个新执行按新请求发起模型调用；这是显式新工作，不是系统自动重放旧调用。

完整 `start(AnalysisRunRequest)` 仍按 VerifiedSourceInventory→08 顺序执行；`executeStep` 只是同一核心的窄入口。Java、CLI 和 loopback HTTP 必须把同一个 `AnalysisStepExecutionRequest` 逐字段传给 core，不得各自复制 analysis step 编排。

### 12.3 active v0 最小运行语义

active v0 的运行状态闭集只有：

~~~text
QUEUED -> RUNNING -> FINISHED
                  \-> FAILED
QUEUED ----------------> FAILED
~~~

- `QUEUED` 与 `RUNNING` 只描述当前单进程 worker。HTTP 可在入队后返回 `202`；CLI 可在同一前台进程驱动该 worker。没有跨进程 owner、generation、claim、lease、event hash-chain、OS lock/CAS 或 queue takeover。
- `FINISHED` 表示本次请求的analysis step区间已经完整发布。完整`start`或`finalAnalysisStepKey=nine-section-document`必须再绑定恰一个 `AnalysisResult = COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE` 和完整root run manifest；以 closed registry 中更早语义键结束的analysis-step-range execution则必须`analysisResult=null`且没有root run manifest。`FAILED`同样没有`AnalysisResult`，只保留稳定failure summary与已经完整安装的artifacts/receipts；partial directory永远不算已安装。
- 一个轻量诊断状态可供 `inspect` 使用，但它不进入 canonical analysis step/root identity，也不承担任务恢复。进程退出时遗留的 `QUEUED/RUNNING` work 不继续；重启后的观察将其视为 `FAILED / PROCESS_INTERRUPTED`，调用者必须 `start` 或 `executeStep` 创建新 identity。
- Provider 调用一旦开始，active v0 不自动重试、不切换 Provider、不猜测终态。响应前中断或 Provider transport/runtime 失败使当前 run `FAILED`；此前已安装业务 artifacts 和诊断保留。只有收到并验证的模型响应才能形成 FlowInterpretation round/receipt/disposition。
- 同一进程内的确定性 analysis step/module 使用 write-once、receipt-last 的 artifact publication；identical reinstall 只用于单次 publication 的幂等保护，不是进程重启后的 run continuation。
- capability manifest 对未来能力只声明 `RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`。public Interface、CLI 和 HTTP 不得暴露 `resume`、`RESUME_MODULE` 或假的扩展点。

进程崩溃后恢复同一 runId、队列/worker/model call，以及模型终态自动修复，全部在 `docs/supplements/runtime-recovery-todo.md` 标为 DEFERRED/TODO。该文档不是 active v0 实现或验收合同。
## 13. 技术参考

前面已经说明每个分析步骤为什么存在、输入、过程、产物、下游、失败和角色；本节才固定精确 records、interfaces、identity、算法、预算、安全和测试目标。

### 13.1 目标外部 Interface

目标seam是“启动或显式创建一次可信分析执行，并按identity观察其业务产物”。单一且唯一公开的analysis Interface `RepositoryAnalysisAgent`隐藏run目录布局、manifest/root join、validation顺序、renderer隔离、单进程执行状态与查询安全；Java、CLI和loopback HTTP都是这个deep module的Adapter，不得各自遍历filesystem或重写规则。独立`LocalGitCommitCaptureAdapter`是显式本地维护入口，不是第二个analysis Interface；它完成后只交出rootless registration/content refs：

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

方法语义固定：

- `start` 只接收exact `analysis-run-request-v2`的content-addressed refs与`sourceRegistrationId`，创建新的runId和`QUEUED`状态后返回；它显式拒绝v1/unknown fields，不接收Path。当前进程的single worker按VerifiedSourceInventory→08执行；进程结束不接回该run。
- `executeStep`只接收exact `analysis-step-execution-request-v1`，按12.2验证连续upstream publications与同一frozen basis，创建新的runId后只执行target analysis step（以及request显式列出的连续后续analysis step）。它不修改旧run、不继承旧runtime/model状态，也不重做有效上游。
- `inspect`只投影`QUEUED|RUNNING|FINISHED|FAILED`、nullable `analysisResult`、analysis step/module receipts、coverage/Gap/failure summaries和可查询artifact descriptors；不返回secret、raw prompt/reasoning或任意Path。
- `artifact`只接受`runId+artifactId` canonical identity，并从该run已安装的analysis step/module receipts与roots解析；存在root run manifest时还必须验证其membership。随后重验SHA/schema/size，再返回metadata或预算内的**完整**UTF-8 bytes；超过query/server ceiling就以稳定错误拒绝，绝不截断。禁止Path、basename、glob或目录遍历。
- `render`只定位该run唯一persisted NineSectionPlan，以plan-only renderer在内存重渲染并与已安装document SHA比较，返回不可变document reference；不产生第二份plan/document、不补写run、不读source/registry/model。
- `validate` fresh-process式重算已完成分析链closure；相对Candidate/AnalysisResult/analysis step/run manifest它是read-only，但会把由`candidateRef+validationPolicyRef`决定identity的validation payload/receipt幂等写到run外validation目录。validator execution失败与`INVALID`结果分开。
- `trace`只在对应Candidate已有匹配validation receipt后返回完整、预算内的path-free typed hops；它重新通过source registry/root验证每个源码span，绝不截断hops或暴露本机Path。

Adapter一一映射，名称与route不得由实现者重新选择：

| Core Interface | CLI Adapter | loopback HTTP Adapter |
| --- | --- | --- |
| `start(request)` | `analyze --request <analysis-run-request-v2.json>`：exact parse；先打印runId，再由当前前台进程驱动single worker | `POST /analysis-runs`，body exact request-v2 → `202 Accepted` + `{runId,lifecycleState:"QUEUED"}` + `Location` |
| `executeStep(request)` | `analyze-step --request <analysis-step-execution-request-v1.json>`：打印新的runId；不得接受旧run的“resume cursor” | `POST /analysis-step-executions`，body exact analysis-step-execution-request-v1 → `202 Accepted` + new `{runId,lifecycleState:"QUEUED"}` |
| `inspect(runId)` | `inspect --run <id>` | `GET /analysis-runs/{runId}` |
| `artifact(query)` | `artifact --run <id> --artifact-id <id> [--location-kind ANALYSIS_STEP_MODULE --analysis-step-key <key> --module-number <n> --module <key> | --location-kind VALIDATION_MODULE --validation-id <id> | --location-kind ANALYSIS_STEP_PUBLICATION --analysis-step-key <key> | --location-kind RUN_MANIFEST] [--type <type>] [--sha256 <hex>] [--metadata-only | --max-bytes <n>]` | `GET /analysis-runs/{runId}/artifacts/{artifactId}`；optional expected location/type/SHA只作discriminator |
| `render(runId)` | `render --run <id>` | `POST /analysis-runs/{runId}:render` |
| `validate(runId)` | `validate --run <id>` | `POST /analysis-runs/{runId}:validate` |
| `trace(query)` | `trace --run <id> --reader-item <key> --max-hops <positive-int> [--expected-candidate <candidate-id>]` | `GET /analysis-runs/{runId}/trace?readerItemKey=<key>&maxHops=<positive-int>[&expectedCandidateId=<candidate-id>]` |

两个`--request <json>`参数都只是Adapter读取request envelope的本地transport路径，不进入core record；只有`LocalGitCommitCaptureAdapter`的maintenance CLI可把repository path交给capture实现。HTTP只绑定loopback且必须认证；request只含ID/enum/bounded scalar，不接受Path。`trace`的candidate check与hop budget由三种Adapter逐字段传递，缺少`maxHops`是`ARTIFACT_QUERY_INVALID`而不是隐式默认。Java/CLI/HTTP对同一request必须返回相同canonical error code、identity、ordering和content SHA；Adapter只做transport/encoding，不能绕过core、改变budget或并发启动第二worker。

`CodeToMarkdownAgent`及其旧Interface、CLI和兼容路径已经由Wire Reset删除，不得恢复为public或nonpublic Adapter、委托桥或迁移桥。稳定目标产品Interface只有上述七个方法，当前尚未实现。capability manifest可显示`RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`，但public Interface、CLI和HTTP没有resume方法或route。
### 13.2 核心 records

~~~text
AnalysisRunRequest
  schemaVersion=analysis-run-request-v2
  sourceRegistrationId
  frozenRepositoryRequestRef {artifactId, sha256}
  profileBundleRef {artifactId, sha256}
  resourceBudgetRef {artifactId, sha256}
  toolchainRef {artifactId, sha256}
  schemaBundleRef {artifactId, sha256}
  promptBundleRef {artifactId, sha256}
  organizationRegistrySeedRef              // required nullable ArtifactReference
  artifactPolicyRegistryRef {artifactId, sha256}
  candidateSeriesRef {artifactId, sha256}
  readerCandidateRound: ROUND_1 | ROUND_2
  parentCandidateRef                        // required nullable ArtifactReference
  approvedFindingRefs[]                     // ArtifactReference, sorted by artifactId

AnalysisRunRequestReference
  analysisRunRequestId
  sha256

AnalysisStepExecutionRequest
  schemaVersion=analysis-step-execution-request-v1
  analysisStepExecutionRequestId
  analysisRunRequestRef: AnalysisRunRequestReference
  targetAnalysisStepKey: application-discovery | program-graphs | proven-code-facts |
                         business-flows | flow-interpretation | repository-knowledge |
                         nine-section-document
  finalAnalysisStepKey: target key or a later semantic key in the closed registry
  upstreamAnalysisStepPublications[]: AnalysisStepPublicationReference
    // exact continuous registry prefix before targetAnalysisStepKey

AnalysisRunReference
  schemaVersion=analysis-run-reference-v1
  runId
  analysisRunRequestId
  analysisStepExecutionRequestId                  // required nullable; non-null only for executeStep
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult: COMPLETE | COMPLETED_WITH_GAPS | INCOMPLETE_SCOPE | INCOMPLETE_COVERAGE | null
  failureCode                              // required nullable; non-null iff FAILED

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
  inputJson: RegistryProposalInputV1       // complete exact single-Flow Provider request
  inputJsonSha256
  outputSchemaSha256
  promptBundleSha256
  expectedRuntime

RegistryProposalInputV1
  schemaVersion=flow-interpretation-registry-proposal-input-v1
  kind=R0_REGISTRY_PROPOSAL_INPUT
  flowSliceId, evidenceCapsuleId
  capsuleView: ModelEvidenceCapsuleViewV1 // all fact/gap/outcome/span/obligation values from that Capsule
  permittedProposalKinds[]
  proposalLimits
  seedEntries[]                            // required empty array when optional seed reference is null

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
  r0TaskDisposition: ModelTaskDispositionV1
  registryProposalIds[]
  gapIds[]
  failureRef?
  reasonCode?

  READY_FOR_FREEZE iff registryProposalIds[] is non-empty and every referenced
  proposal is accepted, same-flow and Capsule-basis-closed.
  GAP|FAILED requires registryProposalIds[] = [].

ModelTaskDispositionV1
  taskSpecId, flowSliceId, round: R0 | R1 | R2
  state: RESPONSE_ACCEPTED | RESPONSE_GAP | RESPONSE_FAILED | NOT_RUN_UPSTREAM_FAILED
  modelRoundId?, generationReceiptId?      // required nullable
  upstreamTaskSpecId?                      // required nullable; only NOT_RUN R2 -> same-Flow R1
  gapIds[], failureRef?, reasonCode?

  RESPONSE_* requires a persisted round and generation receipt. NOT_RUN is
  allowed only for R2 after the same-Flow R1 produced RESPONSE_GAP/FAILED; it
  has no Provider call and both round/receipt IDs are null. R0 and R1 cannot be
  NOT_RUN. Provider transport/runtime interruption is not represented here:
  it fails the run before FlowInterpretation publication.

RepositoryInterpretationRegistry
  repositoryInterpretationRegistryId
  organizationRegistrySeedRef              // required nullable ArtifactReference
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
  flowDispositions[]                       // exact E RegistryProposalDisposition entries, flowSliceId order
  proposalAccounting                       // exact E/R0 denominator, proposal/basis/Gap closure
  closed                                  // true iff every eligible R0 disposition is terminal and closure holds

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
  flowInterpretationDispositionId
  flowSliceId
  disposition: READY_FOR_ADMISSION | GAP | FAILED
  r1TaskDisposition?, r2TaskDisposition? // required nullable ModelTaskDispositionV1
  candidateId?
  gapIds[]
  failureRef?
  reasonCode?

For every eligible Flow, the R0 disposition has one R0 task disposition. The
final interpretation disposition has both R1/R2 task dispositions iff its R0
disposition is READY_FOR_FREEZE; otherwise both are null. Across both public
disposition sets these records close exactly to the planned `E + 2R` task IDs.
RepositoryKnowledge enumerates them only from FlowInterpretation semantic artifacts; there is no
separate runtime-outcome carrier.

EvidenceCapsule
  evidenceCapsuleId
  flowSliceId
  proofPackId
  modelEligibility: ELIGIBLE | INELIGIBLE
  modelIneligibilityGapIds[]
  entryView                              // entry/trigger/root/evidence projection
  factViews[]                             // exact bounded ProvenCodeFacts CodeFact projections
  gapViews[]                              // exact bounded Gap projections with reason/evidence
  outcomePathViews[]                      // exact owning Flow outcomes and guard/terminal semantics
  registryProposalBasisAtomIds[]
  registryProposalBasisGapIds[]
  modelEvidenceSpanIds[]
  projectionObligationIds[]
  budgetUsage

RepositoryFlowCoverage
  entryIds[]
  compiledEntryIds[]
  gappedEntryIds[]
  excludedEntryIds[]
  flowSliceIds[]
  capsuleIds[]
  modelEligibleFlowSliceIds[]
  modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  entryShardReceiptIds[]
  flowShardReceiptIds[]
  closed

RepositoryBusinessKnowledge
  repositoryKnowledgeId
  repositoryInterpretationRegistryId
  sourceScopeId
  flowSliceIds[]
  flowAdmissionDecisionIds[]
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
  technicalFallbacks[]
  registryLineage[]
    registryLineageId
    registryProposalId, provisionalKey
    interpretationProposalId, selectedKey, meaningId, flowSliceId
    proposalKind, normalizedLabel, normalizedPurpose
    basisAtomIds[], basisGapIds[]
  gaps[]
  ownership[]
  conflicts[]

RepositoryCoverageLedgerDraftV2
  schemaVersion=repository-coverage-ledger-draft-v2
  repositoryCoverageLedgerDraftId
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  upstreamAnalysisStepCoverageRoots[6]: AnalysisStepPublicationReference
  repositoryKnowledgeCoveragePreparation: RepositoryKnowledgeCoveragePreparationV1
  sourceFileIds[]
  analyzableTextFileIds[]
  nonAnalyzableMediaFileIds[]
  discoverySiteIds[]
  entryIds[]
  graphCandidateIdsByKind{}
  factCandidateKeys[]
  admittedFactIds[]
  atomIds[]
  outcomeCandidateIds[]
  outcomePathIds[]
  flowSliceIds[]
  evidenceCapsuleIds[]
  modelEligibleFlowSliceIds[]
  modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  gapIds[]
  registryProposalTaskIds[]
  registryProposalRoundIds[]
  registryProposalDispositionIds[]
  registryProposalIds[]
  acceptedRegistryProposalIds[]
  rejectedRegistryProposalIds[]
  repositoryInterpretationRegistryItemIds[]
  provisionalKeys[]
  interpretationTaskIds[]
  interpretationRoundIds[]
  flowInterpretationDispositionIds[]
  flowInterpretationCandidateIds[]
  interpretationProposalIds[]
  interpretationProposalDecisionIds[]
  flowAdmissionDecisionIds[]
  admittedMeaningIds[]
  registryLineageIds[]
  technicalFallbackIds[]
  repositoryKnowledgeItemIds[]
  relationIds[]
  metricIds[]
  knowledgeConflictIds[]
  semanticItemIds[]
  ownerSemanticItemIds[]
  reasonedSemanticExclusionIds[]
  shardReceipts[]: CoverageShardReceiptV1
  equations[]: CoverageEquationV1
  closedThroughRepositoryKnowledge: BOOLEAN
  closureReasonCode?
  // no reader/section owner fields; RepositoryKnowledge public root is not an input to this nested value

RepositoryKnowledgeCoveragePreparationV1
  schemaVersion=repository-knowledge-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]: AnalysisStepPublicationReference
  repositoryKnowledgeAdmissionDecisionSetRef: ArtifactReference
  repositoryKnowledgeKnowledgeMergeRef: ArtifactReference
  repositoryInterpretationRegistryRef: ArtifactReference
  repositoryKnowledgeId
  flowAdmissionDecisionIds[]
  interpretationProposalDecisionIds[]
  admittedMeaningIds[]
  registryLineageIds[]
  technicalFallbackIds[]
  repositoryKnowledgeItemIds[]
  relationIds[]
  metricIds[]
  knowledgeConflictIds[]
  mergedCanonicalGapIds[]
  semanticItemIds[]
  ownerSemanticItemIds[]
  reasonedSemanticExclusionIds[]
  repositoryKnowledgeCoveragePreparationRoot

RepositoryCoverageLedgerDraftReferenceV1
  knowledgeAccountingRef: ArtifactReference
  repositoryCoverageLedgerDraftId
  schemaVersion=repository-coverage-ledger-draft-v2

NineSectionDocumentCoveragePreparationV1
  schemaVersion=nine-section-document-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]
  repositoryKnowledgePublicationRef: AnalysisStepPublicationReference
  repositoryCoverageLedgerDraftRef: RepositoryCoverageLedgerDraftReferenceV1
  repositoryKnowledgeDraftPreparationRoot
  repositoryKnowledgeRef: ArtifactReference
  nineSectionProfileRef: ArtifactReference
  readerSemanticItemIds[]
  sectionOwnerBySemanticItem{}
  nineSectionDocumentCoveragePreparationRoot

RepositoryCoverageLedgerV3
  schemaVersion=repository-coverage-ledger-v3
  repositoryCoverageLedgerId
  repositoryCoverageLedgerDraftRef: RepositoryCoverageLedgerDraftReferenceV1
  nineSectionDocumentCoveragePreparation: NineSectionDocumentCoveragePreparationV1
  sourceScopeKind: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible: BOOLEAN
  sourceFileIds[]
  analyzableTextFileIds[]
  nonAnalyzableMediaFileIds[]
  discoverySiteIds[]
  entryIds[]
  graphCandidateIdsByKind{}
  factCandidateKeys[]
  admittedFactIds[]
  atomIds[]
  outcomeCandidateIds[]
  outcomePathIds[]
  flowSliceIds[]
  evidenceCapsuleIds[]
  modelEligibleFlowSliceIds[]
  modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  gapIds[]
  registryProposalTaskIds[]
  registryProposalRoundIds[]
  registryProposalDispositionIds[]
  registryProposalIds[]
  acceptedRegistryProposalIds[]
  rejectedRegistryProposalIds[]
  repositoryInterpretationRegistryItemIds[]
  provisionalKeys[]
  interpretationTaskIds[]
  interpretationRoundIds[]
  flowInterpretationDispositionIds[]
  flowInterpretationCandidateIds[]
  interpretationProposalIds[]
  interpretationProposalDecisionIds[]
  flowAdmissionDecisionIds[]
  admittedMeaningIds[]
  registryLineageIds[]
  technicalFallbackIds[]
  repositoryKnowledgeItemIds[]
  relationIds[]
  metricIds[]
  knowledgeConflictIds[]
  semanticItemIds[]
  ownerSemanticItemIds[]
  reasonedSemanticExclusionIds[]
  readerSemanticItemIds[]
  sectionOwnerBySemanticItem{}
  analysisStepCoverageRoots[8]
  shardReceipts[]: CoverageShardReceiptV1
  equations[]: CoverageEquationV1
  closed: BOOLEAN
  closureReasonCode: STRING?

  Invariants before identity calculation:
  repositoryCoverageLedgerDraftRef == nineSectionDocumentCoveragePreparation.repositoryCoverageLedgerDraftRef
  readerSemanticItemIds == nineSectionDocumentCoveragePreparation.readerSemanticItemIds
  sectionOwnerBySemanticItem == nineSectionDocumentCoveragePreparation.sectionOwnerBySemanticItem

CoverageShardReceiptV1
  analysisStepKey: verified-source-inventory | application-discovery | program-graphs |
                   proven-code-facts | business-flows | flow-interpretation |
                   repository-knowledge
  shardKind
  shardId
  denominatorIds[]
  dispositionIds[]
  outputIds[]
  status
  gapIds[]
  owningArtifactRef: ArtifactReference

CoverageEquationV1 = closed tagged union
  EXACT_SET_EQUAL {equationKey, leftIds[], rightIds[]}
  DISJOINT_UNION {equationKey, denominatorIds[], partitions[] {partitionKey, ids[]}}
  BIJECTION {equationKey, leftIds[], rightIds[], mappings[] {leftId, rightId}}
  TOTAL_FUNCTION {equationKey, domainIds[], codomainIds[], mappings[] {domainId, codomainId}}
  NONEMPTY_SET_BY_DOMAIN {equationKey, domainIds[], mappings[] {domainId, memberIds[]}}

NineSectionPlanV3
  schemaVersion=nine-section-document-nine-section-plan-v3
  artifactType                           // exact policy-registry value for this schema
  artifactId                             // canonical plan identity; public nineSectionPlanId aliases this value
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
  sectionKey: DOCUMENT_GUIDE | BUSINESS_GOALS | BUSINESS_OBJECTS |
              BUSINESS_ACTIVITIES | FIELDS_AND_DIMENSIONS | OBJECT_RELATIONS |
              METRIC_DEFINITIONS | EXAMPLE_QUESTIONS | PENDING_CONFIRMATION
  title                                 // exact closed key/title pairing in NineSectionDocument §8.2
  readerItems[]: ReaderItemV3

ReaderItemV3                            // sealed by readerItemKind + templateKey + typedSlots
  readerItemKey
  readerItemKind: TECHNICAL_FALLBACK | EMPTY_SECTION | RECORD_REFERENCE |
                  ADMITTED_TERM | FACT_SENTENCE | RELATION_REFERENCE |
                  METRIC_REFERENCE | GAP_QUESTION
  templateKey
  typedSlots
  ownerKnowledgeItemId                  // required nullable; null iff EMPTY_SECTION
  knowledgeItemIds[], factIds[], meaningIds[]
  registryProposalIds[], provisionalKeys[], interpretationProposalIds[], selectedKeys[]
  gapIds[], relationIds[], metricIds[]

ReaderTemplateSlotsV3                   // exact kind/template pairing; no other variant
  TECHNICAL_FALLBACK / technical-scope-v1 -> {display}
  EMPTY_SECTION / empty-section-v2 -> {sectionKey,effectiveProfileRef,reasonCode}
  RECORD_REFERENCE / record-anchor-v1 -> {record,evidence}
  ADMITTED_TERM / activity-with-anchor-v1 ->
      {businessTerm,businessPurpose,technicalAnchor,flow,outcomes[]}
  FACT_SENTENCE / field-write-v1 -> {inputField,targetColumn}
  RELATION_REFERENCE / relation-v1 -> {from,relation,to}
  METRIC_REFERENCE / metric-with-gap-v1 -> {metric,definitionState}
  GAP_QUESTION / gap-question-v1 -> {subject,missingRequirement}

TraceRecordV3
  schemaVersion=nine-section-document-trace-record-v3
  traceId
  readerItemKey
  traceKind                             // same closed eight values as ReaderItemV3
  hops[]: TraceHopV3

TraceHopV3                              // sealed tagged union
  IDENTITY {identityKind,id}
  ARTIFACT_REFERENCE {referenceRole,artifactRef}
  SOURCE_EXCERPT {sourceExcerpt: SourceExcerptV1}
  SECTION {sectionKey}
  TEMPLATE {templateKey}

ValidationReceiptV4
  validationId, runId
  candidateRef: ArtifactReference
  candidateRunPublicationRef: ModulePublicationReference // exact NineSectionDocument M4 publication
  runManifestRef: RunManifestReference
  analysisStepPublicationRefs[8]: AnalysisStepPublicationReference
  directArtifactRefs[]                  // exact equality with envelope upstreamArtifacts
  sourceFilePreimages[] {fileId,sha256}
  repositoryCoverageLedgerRef: ArtifactReference
  analysisResult
  validationStatus
  validatedAnalysisStepRoots[8]
  traceRoot
  checks[]: ValidationCheckV1

ValidationCheckV1
  checkKey: SOURCE_REGISTRATION | ANALYSIS_STEP_PUBLICATION_CHAIN | MODULE_RECEIPT_CHAIN |
            REPOSITORY_COVERAGE | REGISTRY_MEANING_LINEAGE | DOCUMENT_RERENDER |
            SOURCE_TRACE_CLOSURE | CANDIDATE_MANIFEST_BINDING
  status: PASS | GAP | FAIL
  recomputedId                          // required nullable
  failureCode                           // required nullable; closed VALIDATION_* registry below

RunInspection
  runId
  lifecycleState: QUEUED | RUNNING | FINISHED | FAILED
  analysisResult?                          // non-null exactly for FINISHED execution through NineSectionDocument
  analysisRunRequestId
  analysisStepExecutionRequestId?                 // non-null exactly for executeStep
  failureCode?                             // non-null exactly when lifecycleState=FAILED
  repositoryCompletionEligible           // false when final ledger unavailable; otherwise equal final ledger field
  repositoryCoverageLedgerId             // required nullable; non-null iff NineSectionDocument M1 final ledger is installed and fresh-valid
  repositoryCoverageClosed               // false iff ledger ID is null; else equal final ledger closed
  analysisStepReceipts[]                           // analysis step order
  moduleReceipts[]                          // each has sealed ModulePublicationAddress; canonical address order
  artifactDescriptors[]                     // ArtifactLocation, artifactType, artifactId; canonical location order
  gapSummaries[]                            // gapId order, safe fields only
  failureSummaries[]                        // failureId order, safe fields only

ModulePublicationAddress                    // sealed tagged union; never a path
  ANALYSIS_STEP {runId, analysisStepKey, moduleNumber, moduleKey}
  VALIDATION {runId, validationId, moduleNumber=1, moduleKey=run-validator}

ArtifactLocation                            // public sealed tagged union; never a filesystem path
  ANALYSIS_STEP_MODULE {address: AnalysisStepModuleAddress}
  VALIDATION_MODULE {address: ValidationModuleAddress}
  ANALYSIS_STEP_PUBLICATION {address: AnalysisStepPublicationAddress}
  RUN_MANIFEST {address: RunManifestAddress}

ArtifactQuery
  runId
  artifactId                                // required canonical identity
  expectedArtifactLocation?                 // optional exact ArtifactLocation discriminator
  expectedArtifactType?
  expectedSha256?
  contentMode: METADATA_ONLY | COMPLETE_UTF8
  maxBytes                                  // 0 for metadata; positive all-or-error ceiling otherwise

ArtifactView
  runId
  artifactLocation                          // exact ArtifactLocation; describes the real owning store
  artifactType
  schemaVersion
  artifactId
  sha256
  sizeBytes
  mediaType: application/json | application/x-ndjson | text/markdown
  validationState: MANIFEST_VERIFIED | FULLY_VALIDATED
  immutableReference: ArtifactReference
  publicContentExposure: METADATA_ONLY | PATH_FREE_COMPLETE_UTF8
  contentUtf8?                              // non-null only for requested PATH_FREE_COMPLETE_UTF8; never truncated

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
  maxHops                                  // required positive; all-or-error, never a truncation hint

TraceView
  schemaVersion=trace-view-v1
  runId
  candidateRef: ArtifactReference
  validationReceiptRef: ArtifactReference  // matching validated Candidate
  readerItemKey
  traceRecordRef: ArtifactReference
  traceKind                                // same closed eight values as TraceRecordV3
  sourceValidationState: NO_SOURCE_HOPS | ALL_SOURCE_HOPS_REOPENED_AND_HASH_VERIFIED
  hopCount
  hops[]: PublicTraceHopV1                 // exact stored order; hopCount == length
  allHopsReturned=true

PublicTraceHopV1                           // path-free projection of a validated TraceHopV3
  IDENTITY {identityKind,id}
  ARTIFACT_REFERENCE {referenceRole,artifactRef}
  SOURCE_EXCERPT {fileId,startByte,endByteExclusive,startLine,startColumn,endLine,endColumn,rawUtf8,rawUtf8Sha256}
  SECTION {sectionKey}
  TEMPLATE {templateKey}
~~~

`FlowInterpretationDisposition`不能用`flowSliceId`充当隐式identity。其版本化公式固定为：

~~~text
flowInterpretationDispositionId = "flow-interpretation-disposition:" + lowercaseHex(SHA-256(
    frame(UTF8("flow-interpretation-disposition-id-v2")) ||
    frame(canonicalJson(dispositionWithoutFlowInterpretationDispositionId))))
~~~

preimage删除且只删除顶层self ID，保留required-nullable字段，`gapIds`按UTF-8 byte order排序去重。FlowInterpretation `flow-interpretation-flow-interpretation-disposition-v2`和`RepositoryCoverageLedger.flowInterpretationDispositionIds[]`必须逐字使用这一结果；任何按flow临时映射或reader侧补ID均拒绝。

`analysis-run-request-v2`的所有artifact-valued字段必须都是完整`ArtifactReference`：frozen request、profile、resource budget、toolchain、schema、prompt、required-nullable organization seed、artifact policy registry、candidate series、required-nullable parent及每个approved finding；不得内联内容，也不得只给ID而无SHA。`organizationRegistrySeedRef`和`parentCandidateRef`字段始终存在但可为null；其他单值reference字段非空。ROUND_1要求`parentCandidateRef=null`且`approvedFindingRefs=[]`。ROUND_2要求parent非空并fresh reopen为同一candidate series、source registration、frozen request以及逐字相同的profile/resource-budget/toolchain/schema/prompt/organization-seed/policy refs的ROUND_1 Candidate；每个approved finding也必须fresh reopen、唯一引用该parent，列表非空且按artifactId严格排序。ROUND_2不能以ROUND_2为parent；ROUND_3、v1、未知字段/version或从parent补默认一律`ANALYSIS_RUN_REQUEST_UNSUPPORTED`。

request bytes是整个record的canonical JSON，wire本身没有self ID字段，因此没有self-exclusion步骤：`analysisRunRequestId = run-request:SHA-256(frame(UTF8("analysis-run-request-id-v2")) || frame(canonicalJson(request)))`；`AnalysisRunRequestReference.sha256`是同一完整request bytes的SHA-256。`AnalysisStepExecutionRequest.analysisStepExecutionRequestId`同样以domain `analysis-step-execution-request-id-v1`覆盖除self ID外的完整canonical request。`runId`不是业务内容ID：core每次`start`或`executeStep`创建时生成新的256-bit随机值并编码为`analysis-run:<64 lowercase hex>`，在任何目录写入前做collision check；caller和Adapter不能提供、派生或复用它。因此相同业务request可有多个独立execution，artifact内容身份仍由request refs与canonical bytes决定。所有Adapter必须在创建run目录前完成version/lineage/identity检查。

`ArtifactQuery`必须同时有non-null `runId+artifactId`；三个`expected*`字段若给出，只做已安装receipt/root membership与discriminator校验，任一不等即`ARTIFACT_IDENTITY_MISMATCH`，绝不作为“找最近文件”的替代selector。analysis-step-range execution没有root manifest时仍可由其immutable analysis step/module receipts证明自身输出membership，不能从轻量execution status或目录枚举猜测。`expectedArtifactLocation`和返回的`artifactLocation`都使用同一个sealed union：module payload/receipt必须是准确的`ANALYSIS_STEP_MODULE | VALIDATION_MODULE`，analysis-step-owned semantic/archive/receipt必须是`ANALYSIS_STEP_PUBLICATION`，root `run-manifest.json`必须是`RUN_MANIFEST`。实现不得为方便查询而伪造module owner或analysis step fields。任何record都不含filesystem Path。数组按上述注释或stable ID canonical排序；`COMPLETE_UTF8`要求positive `maxBytes`且不得超过server ceiling，artifact大于任一ceiling时返回`ARTIFACT_RESPONSE_BUDGET_EXCEEDED`而不返回partial content。它还必须命中该artifact policy的`publicContentExposure=PATH_FREE_COMPLETE_UTF8`；否则以`ARTIFACT_CONTENT_NOT_PUBLIC`整体拒绝，即使bytes未超预算也不能返回“脱敏截断版”。含`SourceLocatorV1`、`SourceExcerptV1`、prompt或raw model response的原始artifact（包括raw `trace.jsonl`）一律标为`METADATA_ONLY`。`TraceView`是这些source hops唯一允许的、fresh-validated且path-free的public projection。

`TraceView`是已验证内部`TraceRecordV3`的唯一公开、path-free投影。core先fresh reopen Candidate、匹配的`ValidationReceiptV4`、trace record和每个所需source file；`expectedCandidateId`非null时必须逐字等于该Candidate的artifact ID，否则`ARTIFACT_IDENTITY_MISMATCH`。`maxHops`必须在`1..resourceBudget.maxTraceHops`内；若完整record hop count超过它，返回`OBSERVATION_BUDGET_EXCEEDED`，不能裁剪或省略后声称成功。每个SOURCE_EXCERPT hop重新验证file SHA、span边界和excerpt SHA，再只投影fileId、span coordinates、excerpt bytes/SHA，绝不暴露本机或仓库Path。没有source hop时状态只能是`NO_SOURCE_HOPS`；否则所有source hops都通过才可为`ALL_SOURCE_HOPS_REOPENED_AND_HASH_VERIFIED`。candidate、validation、trace ref、status、hop count和完整有序hops共同构成返回值；任何source drift、validation不匹配、unknown hop或预算超限都整个查询失败。

`ReaderItemV3`不是开放`kind+Map`。NineSectionDocument §8.0.1列出的八个kind、八个template和八个typed-slot record逐项唯一配对；所有ID arrays按UTF-8 byte order排序去重。`EMPTY_SECTION`必须使用`empty-section-v2`并保存closed `sectionKey`、完整`effectiveProfileRef`和closed reason，且profile ref逐字等于owner plan的`nineSectionProfileRef`；只有该variant允许`ownerKnowledgeItemId=null`。其他variant不能借EMPTY reason或裸profile ID补身份。任何字段、enum或配对变化都升级plan schema。

`TraceHopV3`只允许`IDENTITY | ARTIFACT_REFERENCE | SOURCE_EXCERPT | SECTION | TEMPLATE`五个tagged variants；identity/reference-role闭集与每种ReaderItem的最短合法hop序列以NineSectionDocument §8.0.1/§8.4为准。`SOURCE_EXCERPT`必须嵌入下文同一个`SourceExcerptV1`；`SEARCHED_SCOPE`和profile lineage必须是完整`ArtifactReference`，不得回退到裸ID、`path:line`或合成raw excerpt。Trace records按readerItemKey排序且一项ReaderItem恰一record。

`nine-section-document-validation-receipt-v4`把实际读取闭包作为自身identity material：payload `directArtifactRefs[]`与module envelope `upstreamArtifacts[]`必须逐字相同并按artifactId严格排序，列齐fresh validator打开的每个control/profile/policy、source-registration、semantic/archive/receipt、module payload/receipt、M4、Candidate、root manifest和coverage artifact；`sourceFilePreimages[]`另按fileId绑定每个实际打开source file的完整SHA。validator只根据immutable manifest/publications证明AnalysisResult与业务闭包，不把轻量execution status当作identity preimage。manifest/root/descriptor不能代替被读bytes。payload中的八个`analysisStepPublicationRefs`、八个`validatedAnalysisStepRoots`必须逐项等于同一M4 publication和root run manifest，`traceRoot`等于它们指向的NineSectionDocument Trace。显式analysis step链允许不同producer runId，但必须重新验证12.2的连续lineage；额外读取任何bytes都必须先加入preimage并改变validation identity。

`ValidationCheckV1.checkKey`闭集为`SOURCE_REGISTRATION | ANALYSIS_STEP_PUBLICATION_CHAIN | MODULE_RECEIPT_CHAIN | REPOSITORY_COVERAGE | REGISTRY_MEANING_LINEAGE | DOCUMENT_RERENDER | SOURCE_TRACE_CLOSURE | CANDIDATE_MANIFEST_BINDING`；status为`PASS | GAP | FAIL`。`failureCode`required-nullable，非null闭集为`VALIDATION_SOURCE_INVALID | VALIDATION_ANALYSIS_STEP_PUBLICATION_INVALID | VALIDATION_MODULE_PUBLICATION_INVALID | VALIDATION_REPOSITORY_SCOPE_INCOMPLETE | VALIDATION_REPOSITORY_COVERAGE_INCOMPLETE | VALIDATION_REGISTRY_LINEAGE_INVALID | VALIDATION_DOCUMENT_RERENDER_MISMATCH | VALIDATION_SOURCE_TRACE_INVALID | VALIDATION_CANDIDATE_MANIFEST_MISMATCH`；`GAP`只可配两种incomplete code，其余不一致为`FAIL`。fixture-only code一律拒绝。

`NineSectionPlanV3.artifactId`是standalone plan唯一self ID，按`STANDALONE_JSON`公式排除且只排除该字段；Java/API中的`nineSectionPlanId`逐字承载这个`artifactId`，wire document不得再加入第二个self-ID字段。

统一源码位置合同只有以下两个versioned value records；ApplicationDiscovery、ProgramGraphs、BusinessFlows、NineSectionDocument不得另造`path:line`、basename locator或拼接excerpt：

~~~text
SourceLocatorV1
  fileId                                   // exact VerifiedSourceInventory inventory file ID
  path                                     // canonical repository-relative path
  startByte                                // zero-based UTF-8 byte offset, inclusive
  endByteExclusive                         // zero-based UTF-8 byte offset, exclusive
  startLine, startColumn                   // one-based
  endLine, endColumn                       // one-based, exclusive

SourceExcerptV1
  locator: SourceLocatorV1
  rawUtf8                                  // exact strict-UTF-8 decode of source bytes in locator range
  rawUtf8Sha256                            // lowercase SHA-256 of those exact bytes
~~~

`0 <= startByte < endByteExclusive <= source size`；byte slice必须落在UTF-8 code-point boundaries，重新编码`rawUtf8`必须逐字节等于该slice，坐标由同一VerifiedSourceInventory line index唯一推出。一个record只表示一个文件内的**连续**span；类/方法、guard/return或其他不连续证据必须拆成多个`SourceExcerptV1`并以`path,startByte,endByteExclusive`排序，禁止插入` + `、`...`、省略号或合成空白后声称raw。schema只需要位置时仍嵌入完整`SourceLocatorV1`；声称source evidence或显示excerpt时必须使用完整`SourceExcerptV1`。

### 13.3 Canonical JSON 与 identity

普通计算模块的JSON payload使用下面同一`ModuleArtifact<T>` envelope；各analysis step doc固定payload schema和filename。前七个分析步骤的最终publication specifier是有意的窄例外：为了让analysis step store逐bytes重用，它的module publication可包含analysis step schema注册的`MODULE_ARTIFACT_JSON`、`STANDALONE_JSON`或`CANONICAL_JSONL` bytes，但仍必须由`CanonicalModuleArtifactStore`按policy验证并以独立module receipt原子安装；它不能使用`RAW_UTF8`，不能接受caller-selected envelope/prefix，也不能创建summary envelope：

~~~text
ModuleArtifact<T>
  schemaVersion: STRING                    // required, exact artifact schema
  artifactType: STRING                     // required, registered enum value
  artifactId: STRING                       // required, computed as below
  producer:
    address:                               // required, exact tagged ModulePublicationAddress
      kind: ANALYSIS_STEP | VALIDATION
      runId: STRING
      analysisStepKey?                     // required only for ANALYSIS_STEP
      validationId?                        // required only for VALIDATION
      moduleNumber: INTEGER
      moduleKey: STRING                    // exact compiled lowercase key
    moduleVersion: STRING                  // required
  upstreamArtifacts[]:
    artifactId: STRING                     // required
    sha256: HEX64                          // required, bytes of exact upstream file
  controls:
    toolchainSha256: HEX64                 // required
    profileSha256: HEX64                   // required
    schemaBundleSha256: HEX64              // required
    promptBundleSha256: HEX64?             // required nullable; non-null only where used
    artifactPolicyRegistryRef:             // required
      artifactId: STRING
      sha256: HEX64
  completion:
    status: SUCCEEDED | SUCCEEDED_WITH_GAPS
    gapRefs[]: STRING                      // required, may be empty, sorted unique
    failureRef: STRING?                    // required nullable; always null for installed success
  payload: T                               // required, schema fixed in analysis step doc
~~~

每个命名模块的成功目录还必须包含独立receipt；payload artifact先固定，receipt最后计算且排除自身：

~~~text
ModuleReceipt
  schemaVersion=module-receipt-v1
  moduleReceiptId
  address: ModulePublicationAddress
  moduleVersion
  upstreamArtifacts[] {artifactId, sha256}
  controls
  status: SUCCEEDED | SUCCEEDED_WITH_GAPS
  payloadArtifacts[] {fileName, artifactType, schemaVersion, artifactId, mediaType, sizeBytes, sha256}
  moduleArtifactRoot
  gapRefs[]
~~~

`moduleArtifactRoot`只覆盖本模块policy-validated canonical JSON/JSONL payload bytes；`module-receipt.json`不覆盖自身。每个模块至少安装一个payload artifact和receipt，二者在同一文件系统staging写满、force、逐SHA验证后一次原子安装；缺receipt、receipt早于payload、root不匹配或destination collision都不构成完成publication。运行时唯一`RAW_UTF8` artifact是NineSectionDocument最终的`document.md`：renderer模块先把完整Markdown UTF-8内容作为canonical JSON字符串封进自己的machine artifact并安装receipt，AnalysisStep store才把同一bytes安装为全run唯一人读文件；任何module publication不得直接携带RAW_UTF8或在module目录留第二份`.md`。

#### 13.3.1 `org.sourceanalysis.app.artifact` 三个深公共存储 seam

跨分析步骤共享存储只公开三个职责分离的Interface；“public”指Luna/Terra可从测试/production package调用的稳定seam，不是第二个产品外部Interface。`CanonicalModuleArtifactStore`保持不变的模块边界；`CanonicalAnalysisStepArtifactStore`拥有semantic analysis step set、可选archive manifest和最后receipt；`CanonicalRunManifestStore`只拥有root run manifest。三者都不暴露caller Path：

~~~java
public interface CanonicalModuleArtifactStore {
    InstalledModulePublication install(ModuleInstallRequest request);
    ReopenedModulePublication reopen(ModulePublicationReference reference);
}

public interface CanonicalAnalysisStepArtifactStore {
    InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request);
    ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference);
}

public interface CanonicalRunManifestStore {
    InstalledRunManifest install(RunManifestInstallRequest request);
    ReopenedRunManifest reopen(RunManifestReference reference);
}

public interface CanonicalArtifactPolicyRegistry {
    ArtifactPolicyRegistryReference reference();
    CanonicalArtifactPolicy resolve(ArtifactPolicyKey key);
}

public final class FileSystemCanonicalModuleArtifactStore
        implements CanonicalModuleArtifactStore {
    public FileSystemCanonicalModuleArtifactStore(
            RunStoreHandle runStore,
            CanonicalJsonCodec canonicalJson,
            CanonicalArtifactPolicyRegistry artifactPolicies,
            ArtifactStoreLimits limits);
}

public final class FileSystemCanonicalAnalysisStepArtifactStore
        implements CanonicalAnalysisStepArtifactStore {
    public FileSystemCanonicalAnalysisStepArtifactStore(
            RunStoreHandle runStore,
            CanonicalJsonCodec canonicalJson,
            CanonicalArtifactPolicyRegistry artifactPolicies,
            ArtifactStoreLimits limits);
}

public final class FileSystemCanonicalRunManifestStore
        implements CanonicalRunManifestStore {
    public FileSystemCanonicalRunManifestStore(
            RunStoreHandle runStore,
            CanonicalJsonCodec canonicalJson,
            CanonicalArtifactPolicyRegistry artifactPolicies,
            ArtifactStoreLimits limits);
}

public final class RunStoreBootstrap {
    public static RunStoreHandle open(Path configuredStoreRoot);
    public static RunStoreHandle openForTest(Path emptyTemporaryDirectory);
}

public interface RunStoreHandle extends AutoCloseable {
    @Override void close();
}

public final class CanonicalJsonCodec {
    public CanonicalJsonCodec();
}

public record ArtifactStoreLimits(
        int maxPayloadFiles,
        long maxArtifactBytes,
        long maxPublicationBytes,
        int maxDirectoryEntries) {}
~~~

以上类型都位于`org.sourceanalysis.app.artifact`。三个constructor参数顺序都固定为`runStore, canonicalJson, artifactPolicies, limits`，不得新增隐藏global/default。具体实现全部委托同一package-private `AtomicCanonicalPublicationEngine`；该engine、filesystem locator、staging name和atomic move细节既不出现在public constructor，也不成为可替换public seam。`RunStoreHandle`拥有/共享这一私有engine，保证三个store使用相同NOFOLLOW、force、collision与cleanup语义。

`RunStoreBootstrap.open`与`openForTest`是唯一允许`Path`的store bootstrap seam：前者接application配置的store root，后者要求JUnit提供的已存在、空、非-symlink临时目录；二者返回不暴露root/path accessor的opaque handle。`openForTest`使用真实filesystem、真实force与atomic move，不是in-memory fake；测试完成必须close handle。private source/policy registry可以在store内部保留local locator，但公开的source registration、policy registry load/registration和三个store request/reference一律只接受content ID/SHA/typed values，绝不接受或回显locator/Path。

最小RED setup固定先用path-free fixture安装一份canonical policy registry并取得`CanonicalArtifactPolicyRegistry policies`，随后分别构造`new FileSystemCanonicalModuleArtifactStore(handle, codec, policies, limits)`、`new FileSystemCanonicalAnalysisStepArtifactStore(handle, codec, policies, limits)`和`new FileSystemCanonicalRunManifestStore(handle, codec, policies, limits)`；三个对象共享同一个`RunStoreHandle`。旧的`AtomicDirectoryInstaller` constructor参数与无registry constructor都必须编译失败，测试不得mock root/receipt/atomic engine。

精确records：

~~~text
ModulePublicationAddress (sealed)
  AnalysisStepModuleAddress(runId, analysisStepKey, moduleNumber, moduleKey)
  ValidationModuleAddress(runId, validationId, moduleNumber, moduleKey)

ModuleProducer(address, moduleVersion)

ArtifactReference(artifactId, sha256)
ArtifactPolicyRegistryReference(artifactId, sha256)
ArtifactPolicyKey(artifactType, schemaVersion)
CanonicalArtifactPolicy(key, artifactIdPrefix, mediaType, envelopeKind, emptyJsonlAllowed, publicContentExposure)
ArtifactControls(toolchainSha256, profileSha256, schemaBundleSha256, promptBundleSha256, artifactPolicyRegistryRef)

ModuleInstallRequest(address, moduleVersion, upstreamArtifacts, controls, status, gapRefs, payloads)

CanonicalModulePayload(fileName, artifactType, schemaVersion, artifactId, mediaType, canonicalUtf8)
  fileName is a registered basename; no slash, never module-receipt.json
  mediaType is application/json | application/x-ndjson
  canonicalUtf8 is ImmutableBytes: defensive copy, complete bytes, never stream alias

ModulePublicationReference(address, moduleArtifactRoot, moduleReceiptId, moduleReceiptSha256)

ArtifactDescriptor(fileName, artifactType, schemaVersion, artifactId, mediaType, sizeBytes, sha256)
InstalledModulePublication(reference, disposition, artifactDescriptors)
  disposition is INSTALLED | ALREADY_INSTALLED

VerifiedCanonicalPayload(descriptor, canonicalUtf8)
ReopenedModulePublication(reference, receipt, payloads)

AnalysisStepPublicationAddress(runId, analysisStepKey)
AnalysisStepPublicationProvenance (sealed)
  AnalysisStepPublisherModuleProvenance(publisherSpecificationModuleReference) // first seven analysis steps only
  NineSectionDocumentCoordinatorPreparationProvenance(preparationModuleReferences, analysisRunRequestReference, upstreamAnalysisStepReferences, repositoryCoverageLedgerRef) // NineSectionDocument only; M1-M3 refs
AnalysisStepInstallRequest(address, publicationProvenance, upstreamAnalysisStepReferences, controls, status, gapRefs, semanticPayloads, archiveManifestSpecification)
CanonicalAnalysisStepPayload(fileName, artifactType, schemaVersion, artifactId, mediaType, canonicalUtf8)
  fileName is an analysis-step-registered semantic basename; no slash, never any registered receipt basename or run-manifest.json
  mediaType is application/json | application/x-ndjson | text/markdown
  canonicalUtf8 is ImmutableBytes with the same defensive-copy contract as module payloads
ArchiveManifestSpecification(fileName, artifactType, schemaVersion) // null for first seven analysis steps; required for NineSectionDocument
AnalysisStepPublicationReference(address, analysisStepArtifactRoot, analysisStepReceiptId, analysisStepReceiptSha256)
InstalledAnalysisStepPublication(reference, disposition, semanticArtifactDescriptors, archiveManifestDescriptor)
ReopenedAnalysisStepPublication(reference, receipt, semanticPayloads, archiveManifestPayload)

RunManifestAddress(runId)
RunManifestInstallRequest(address, analysisRunRequestReference, analysisStepPublications, repositoryCoverageLedgerRef, repositoryInterpretationRegistryRef, repositoryKnowledgeRef, nineSectionPlanRef, documentRef, traceRef, candidateRef, analysisResult, controls)
RunManifestReference(address, runManifestId, runManifestSha256)
InstalledRunManifest(reference, disposition, descriptor)
ReopenedRunManifest(reference, manifest, canonicalUtf8)
~~~

`CanonicalModulePayload`与`CanonicalAnalysisStepPayload`故意不是同一个媒体类型/策略集合。module payload只允许`application/json`或`application/x-ndjson`，并由compiled module registry把exact address+filename固定到`MODULE_ARTIFACT_JSON`、`STANDALONE_JSON`或`CANONICAL_JSONL` policy；普通模块只用第一种，前七个分析步骤的publication specifier才可用后两种。raw Markdown不会装入module publication。analysis step payload额外允许且只允许registry为exact `(artifactType,schemaVersion)`选出的`text/markdown`/`RAW_UTF8`策略；本版唯一实例是NineSectionDocument `document.md`的`NINE_SECTION_DOCUMENT_DOCUMENT_MARKDOWN` / `nine-section-document-document-markdown-v1`。`CanonicalAnalysisStepArtifactStore`必须按§13.3.3 RAW_UTF8规则验证严格UTF-8、无BOM、schema固定LF/final-LF约束，并用该policy的`artifactIdPrefix`重算identity；media type、envelope或prefix任一不符都fail closed。M4只在自己的JSON publication specifier中携带这个analysis step artifact的typed reference/descriptor，不复制Markdown bytes。

`ModuleArtifact<T>`是本节`MODULE_ARTIFACT_JSON` policy的wire envelope形状，不是所有standalone/JSONL bytes的虚构wrapper，也不是另一个public parser seam。`producer.address`逐字段等于`ModuleInstallRequest.address`；`ANALYSIS_STEP`、`VALIDATION`两种variant互斥，不能把validator伪装成NineSectionDocument模块。pre-reset parser明确退出目标合同：它没有policy registry、expected address/upstream/controls或exact artifact type/version，任何analysis step/runtime/test都不得用它验证或重开production bytes。三种artifact envelope策略的写入都只能经registry-aware `CanonicalModuleArtifactStore.install(ModuleInstallRequest)`，读取/验证只能经`reopen(ModulePublicationReference)`；domain payload解码发生在store已验证并返回`VerifiedCanonicalPayload`之后。Wire Reset后旧类型不存在，也没有兼容入口或旧golden reader。

跨analysis step执行仍只接受完整`AnalysisStepPublicationReference`并fresh reopen；`ModuleArtifact.upstreamArtifacts`不保存虚构的numeric-key publication ArtifactReference。它按`artifactId`再`sha256`的UTF-8 bytes严格排序，逐项绑定该模块实际读取的semantic artifact bytes及其他直接content-addressed preimage；每份analysis step doc的“精确 upstream”列就是这个完整集合。某个analysis step的未读semantic file不因同analysis step关系自动加入，已读文件也不得以analysis step receipt、root或传递依赖代替。若模块的直接依赖是同analysis step前驱module artifact，则绑定该artifact；publication specifier的standalone payload不内嵌依赖，依赖由同一次`ModuleInstallRequest.upstreamArtifacts`和生成的`ModuleReceipt.upstreamArtifacts`绑定。NineSectionDocument M4额外在payload中保存八个完整`AnalysisStepPublicationReference`，因为它观察全部analysis step roots/receipts；这些typed refs不能降格成八个receipt ArtifactReference。

模块brief里未带`ArtifactReference`限定的registry/profile/budget/toolchain/schema/prompt名称属于冻结run control，不自动变成额外`upstreamArtifacts`：模块只可消费orchestrator已经按request-v2和`ModuleControls`验证的immutable rule view、hash和budget guard，不能私自重开其artifact bytes。若模块确实读取某项control artifact bytes，该analysis step“精确 upstream”表必须逐名列出完整reference，fixture也必须绑定它；未列出就禁止读取。于是每个module publication的完整identity preimage恰为typed address（其runId绑定request-v2）、`controls`、表列`upstreamArtifacts`与payload，不允许实现从brief中的概念名暗增隐藏输入。

`CanonicalArtifactPolicyRegistry`本身由exact `artifact-policy-registry-v2` canonical JSON document加载；document字段是`schemaVersion`、`artifactPolicyRegistryId`和`policies[]`。每项严格为`artifactType`、`schemaVersion`、`artifactIdPrefix`、`mediaType`、`envelopeKind`、`emptyJsonlAllowed`、`publicContentExposure`，按`artifactType`再`schemaVersion`的UTF-8 bytes排序且key唯一。`envelopeKind`是closed `MODULE_ARTIFACT_JSON | STANDALONE_JSON | CANONICAL_JSONL | RAW_UTF8`；JSON/RAW_UTF8策略的`emptyJsonlAllowed`必须为false。`publicContentExposure`是closed `METADATA_ONLY | PATH_FREE_COMPLETE_UTF8`：后者只可用于已由该schema保证不会携带 source locator/excerpt、prompt、raw response、secret或absolute/repository path的完整bytes；默认及任何无法静态证明安全的type/version必须是`METADATA_ONLY`。registry ID排除且只排除`artifactPolicyRegistryId`，公式为`artifact-policy-registry:SHA-256(frame(UTF8("canonical-artifact-policy-registry-id-v2")) || frame(canonicalJson(documentWithoutArtifactPolicyRegistryId)))`；reference SHA覆盖完整document bytes。

每个install/reopen先要求constructor registry的reference逐字等于`ArtifactControls.artifactPolicyRegistryRef`，再用exact `(artifactType,schemaVersion)`解析唯一policy。descriptor/media/envelope/identity prefix都以registry为authoritative；payload/request不得含`artifactIdPrefix`，也不得用artifactId中的caller prefix反向选择policy。缺key、重复key、prefix/media/envelope不匹配或zero-byte JSONL未被该exact policy许可均fail closed。policy registry/reference是内容寻址input：改变任一entry会改变registry ID、controls、payload/receipt/manifest identity和新run ID；旧run仍只按自己引用的registry重开。

`ImmutableBytes`不是未定义占位符；它是同一公开package内唯一允许出现在store request/reopen output中的bytes值类型，精确API为：

~~~java
package org.sourceanalysis.app.artifact;

public final class ImmutableBytes {
    private ImmutableBytes(byte[] ownedCopy);
    public static ImmutableBytes copyOf(byte[] source);
    public int size();
    public byte[] copyToByteArray();
    @Override public boolean equals(Object other);
    @Override public int hashCode();
}
~~~

`copyOf`拒绝null并立即复制输入；`copyToByteArray`每次返回新副本；`equals/hashCode`按全部byte内容而非数组identity；没有公开constructor、backing-array/`ByteBuffer`/stream accessor或unsafe wrap。测试精确用`ImmutableBytes.copyOf(canonicalBytes)`构造`CanonicalModulePayload`，并用`reopened.payloads().get(i).canonicalUtf8().copyToByteArray()`读取完整副本；随后修改原数组或返回副本都不得改变install/reopen结果。`CanonicalModulePayload`和`VerifiedCanonicalPayload`的`canonicalUtf8` component类型均为这个`ImmutableBytes`。

对应Java records的component顺序严格等于上面圆括号顺序；lists在constructor内`List.copyOf`，`ImmutableBytes`在进入/返回store时都保持上述value/defensive-copy语义。`promptBundleSha256`是required nullable的第4个control component，`artifactPolicyRegistryRef`是required non-null第5个component。`ModuleCompletionStatus`、`ModuleInstallDisposition`、`CanonicalMediaType`和`CanonicalEnvelopeKind`是closed enum；不得用String扩展未知值。

所有可进入filesystem address/root/reference的content ID先解析为value type；wire grammar固定为`<prefix>:<64 lowercase hex>`，其中prefix为`[a-z][a-z0-9-]{0,47}`，hex为`[0-9a-f]{64}`。大写hex、额外colon、空/超长prefix、slash、backslash、dot segment、Unicode normalization变化或不匹配的固定prefix均在任何filesystem lookup前拒绝。固定prefix至少包括`analysis-run`、`module-root`、`module-receipt`、`analysis-step-root`、`analysis-step-receipt`、`run-manifest`和`artifact-policy-registry`；类型不得互换。

`analysisStepKey`是closed semantic enum：`verified-source-inventory | application-discovery | program-graphs | proven-code-facts | business-flows | flow-interpretation | repository-knowledge | nine-section-document`。它们的依赖顺序由compiled-in registry固定；numeric alias不进入wire。`moduleKey`必须满足`[a-z][a-z0-9-]{0,47}`并命中下面唯一compiled-in registry；仅语法合法但未注册、analysis step/module错配或大小写别名都拒绝：

~~~text
verified-source-inventory: 01=request-admission, 02=source-index, 03=publish
application-discovery: 01=application-profile, 02=http-entry, 03=mapper-catalog, 04=publish
program-graphs: 01=code-structure, 02=call-graph, 03=control-flow, 04=data-flow, 05=evidence-graph, 06=publish
proven-code-facts: 01=candidates, 02=proofs, 03=publish
business-flows: 01=flow-compiler, 02=capsule-projector, 03=publish
flow-interpretation: 01=registry-task-compiler, 02=registry-proposal-runner, 03=registry-freezer, 04=flow-task-compiler, 05=interpretation-runner, 06=publish
repository-knowledge: 01=admission, 02=knowledge-merge, 03=publish
nine-section-document: 01=planner, 02=renderer, 03=trace, 04=archive
validation address: 01=run-validator
~~~

`moduleKey`是持久化身份，不是实现类显示名。任何wire `producer.address.moduleKey`、walkthrough顶层`module`、`modules/<NN-moduleKey>/`目录段以及`src/test/resources/analysis/<semantic-package>/<moduleKey>/` fixture末段都必须逐字使用上表的lowercase key；class/test名称只描述实现，可在正文以`implementationClass`或模块标题单独出现，绝不能代替或派生持久化key。fixture的`semantic-package`依次是`inventory | discovery | graph | fact | flow | interpretation | knowledge | document`。validation使用`ValidationModuleAddress(..., moduleNumber=1, moduleKey=run-validator)`，fixture根精确是`src/test/resources/validation/run-validator/`；它是run级external module，没有NineSectionDocument module number。因此例如ProgramGraphs M6可写`moduleKey=publish, implementationClass=ProgramGraphSetPublicationSpecifier`；implementation class名、从class名机械派生的key或大小写别名都不是合法address/producer/fixture owner。

filename同理由policy/analysis step schema registry给出exact basename，不能由caller把任意字符串拼进目录。module registry增加/重命名任何一项都是versioned design change；caller不能用语法合法的新key扩展它。

目录由typed address唯一推导，调用者不能传relative/absolute path；私有encoder把已验证content ID确定性编码为`<prefix>--<hex64>`单一segment，再使用closed analysis step/module mapping：

- analysis step module：`runs/<encodedRunId>/steps/<NN-analysisStepKey>/modules/<NN-moduleKey>/`；
- analysis step publication：`runs/<encodedRunId>/steps/<NN-analysisStepKey>/`；
- root manifest：`runs/<encodedRunId>/run-manifest.json`；
- validator：`runs/<encodedRunId>/validations/<encodedValidationId>/modules/<NN-moduleKey>/`；

实现不得对caller string做`resolve`/concatenate后再检查，也不得把wire ID原文当path segment；必须先构造typed value、查closed mapping、编码segment，再在opaque run handle内NOFOLLOW解析。

`CanonicalModuleArtifactStore.install`先拒绝空payload、重复fileName/artifactId、RAW_UTF8、非canonical JSON/JSONL bytes、policy/address/filename不匹配及预算超限。`MODULE_ARTIFACT_JSON`还必须逐字段验证envelope producer/address/upstream/control；`STANDALONE_JSON`/`CANONICAL_JSONL`只允许compiled registry标记的前七个分析步骤publication-specifier address+filename，upstream/control由request和receipt绑定且AnalysisStep store再次对provenance验证。随后生成`module-receipt-v1`，在目标同文件系统的不可见sibling staging目录写payload→force files→写receipt→force receipt/directory→重开逐SHA/root校验→atomic move。目标不存在时返回`INSTALLED`。目标已存在时不覆盖：它按传入request重新计算expected reference并完整`reopen`；所有payload bytes、receipt ID/SHA、root和descriptors逐字节一致才返回`ALREADY_INSTALLED`，否则`MODULE_PUBLICATION_COLLISION`。staging残留永远不是installed publication。

`reopen`只接受完整`ModulePublicationReference`，先按typed address定位，再NOFOLLOW检查目录和所有文件；目录集合必须精确等于receipt声明payloads加`module-receipt.json`，不得有缺失、额外文件、symlink或嵌套目录。它重算每个size/SHA/canonical JSON或JSONL/artifactId、module root和receipt ID/SHA，重验receipt的address/upstream/control，并按policy重验MODULE envelope字段或specifier address+filename allowance；任一不符以`MODULE_PUBLICATION_INVALID`失败且不返回partial payload。只有`ReopenedModulePublication`中的defensive immutable bytes可交给下一模块；原始`ModuleInstallRequest`对象或caller byte arrays不得跨模块。

`CanonicalAnalysisStepArtifactStore.install`先按provenance variant验证完整preimage：前七个分析步骤必须fresh reopen唯一publisher specification module，并验证其semantic payload bytes/descriptors与request逐项相等；NineSectionDocument必须fresh reopenM1–M3以及request/upstream/ledger references，且拒绝任何M4 reference。`semanticPayloads`必须精确等于该analysis step schema注册的semantic filename/type/version集合。前七个分析步骤要求`archiveManifestSpecification=null`；NineSectionDocument要求exact `nine-section-archive-manifest.json/NINE_SECTION_DOCUMENT_ARCHIVE_MANIFEST/nine-section-document-archive-manifest-v1`。store先写semantic payloads并重算identity/descriptors/root；NineSectionDocument随后由这些五项descriptor和root生成archive manifest；最后才生成绑定typed provenance的语义receipt。semantic files → optional archive manifest → semantic receipt依次force后才atomic install整个analysis step public set。相同request逐bytes返回`ALREADY_INSTALLED`，任何不同collision均不覆盖。`reopen`要求目录中的public files精确等于registered semantic set、可选archive和该步骤注册的receipt basename，再重算完整链；analysis step的`modules/`子目录由module store独立拥有，不计入public set/root，也不能让analysis step reopen遍历为payload。

`CanonicalRunManifestStore.install`仅接受exact八个按closed registry依赖顺序排列的`AnalysisStepPublicationReference`，逐个fresh reopen并验证同一frozen basis/controls/连续lineage后构造`run-manifest-v1`；full-run producer runId相同，显式analysis step execution链允许不同。NineSectionDocument receipt必须已经存在，M4 publication/receipt此时尚未进入manifest。store在run根写唯一`run-manifest.json`，force、自验、atomic install；相同request返回`ALREADY_INSTALLED`，不同bytes返回`RUN_MANIFEST_COLLISION`。`reopen`只接受完整reference，NOFOLLOW定位固定basename，重算canonical JSON、ID、SHA和全部analysis step/request/content refs；run根存在第二份manifest、analysis step内存在manifest、unknown field或任何引用漂移均`RUN_MANIFEST_INVALID`。run manifest没有第二个receipt；其自身ID/SHA就是完整completion reference。

失败不安装 success envelope，而写外部：

~~~text
ModuleFailure
  schemaVersion=module-failure-v1
  address: ModulePublicationAddress
  attemptedUpstreamArtifacts[]
  controls
  failureCode
  safeDetail
  failureId
~~~

所有content identity和descriptor root共用下面唯一binary framing；伪代码中的字符串均先严格UTF-8编码，`SHA-256`输出32 raw bytes，最终hex为64个小写字符：

~~~text
U32BE(n)       = exactly 4-byte unsigned big-endian n
U64BE(n)       = exactly 8-byte unsigned big-endian n
frame(bytes)   = U64BE(bytes.length) || bytes
rawSha(hex64)  = exact 32 bytes decoded from lowercase hex64

descriptorBytes(d) =
    frame(UTF8(d.fileName)) ||
    frame(UTF8(d.artifactType)) ||
    frame(UTF8(d.schemaVersion)) ||
    frame(UTF8(d.artifactId)) ||
    frame(UTF8(d.mediaType)) ||
    U64BE(d.sizeBytes) ||
    rawSha(d.sha256)

descriptorListRoot(domain, prefix, descriptors) =
    prefix + ":" + lowercaseHex(SHA-256(
        frame(UTF8(domain)) ||
        U32BE(descriptors.size) ||
        concat(frame(descriptorBytes(d)) for d in descriptors)))
~~~

descriptor列表先要求fileName唯一，再按fileName的UTF-8 byte lexicographic order严格排序；不做locale/Unicode/case folding。`sizeBytes`必须在`0..2^63-1`且仍以unsigned U64BE编码；descriptor数必须在`0..2^32-1`和store budget内。module root固定调用`descriptorListRoot("canonical-module-artifact-root-v1", "module-root", payloadDescriptors)`；analysis step root固定调用`descriptorListRoot("canonical-analysis-step-artifact-root-v1", "analysis-step-root", semanticDescriptors)`。module root排除module receipt；analysis step root排除archive manifest、analysis step receipt、root run manifest和M4 publication。没有别的Merkle tree、目录hash、JSON数组hash或实现自选root算法。

policy的四种envelope identity精确为：

~~~text
MODULE_ARTIFACT_JSON:
  digest = SHA-256(frame(UTF8("canonical-module-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(canonicalJson(envelopeWithoutArtifactId)))

STANDALONE_JSON:
  digest = SHA-256(frame(UTF8("canonical-standalone-json-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(canonicalJson(documentWithoutArtifactId)))

CANONICAL_JSONL:
  digest = SHA-256(frame(UTF8("canonical-jsonl-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(exactCanonicalJsonlBytes))

RAW_UTF8:
  digest = SHA-256(frame(UTF8("canonical-raw-utf8-artifact-id-v1")) ||
                  frame(UTF8(schemaVersion)) || frame(UTF8(artifactType)) ||
                  frame(exactUtf8Bytes))

artifactId = policy.artifactIdPrefix + ":" + lowercaseHex(digest)
~~~

两种JSON公式删除且只删除顶层`artifactId`；字段不可留null占位，其他字段全部参与。module envelope的producer/upstream/controls/completion/payload全参与；`upstreamArtifacts`按artifactId再sha256排序，`gapRefs`按ID排序去重。JSONL每个非空行必须是无BOM canonical JSON object、禁止空白行并以LF结束；nonempty file必须final LF。零行文件是exact zero bytes且仅当exact policy的`emptyJsonlAllowed=true`。RAW_UTF8必须严格UTF-8、无BOM并服从schema固定line-ending/final-LF规则。store逐bytes重算，不得把JSONL伪装成JSON envelope或从artifact ID caller prefix选择公式。

三个completion identity使用同一self-exclusion规则：

~~~text
moduleReceiptId = "module-receipt:" + lowercaseHex(SHA-256(
    frame(UTF8("canonical-module-receipt-id-v1")) ||
    frame(canonicalJson(moduleReceiptWithoutModuleReceiptId))))

analysisStepReceiptId = "analysis-step-receipt:" + lowercaseHex(SHA-256(
    frame(UTF8("canonical-analysis-step-receipt-id-v1")) ||
    frame(canonicalJson(analysisStepReceiptWithoutAnalysisStepReceiptId))))

runManifestId = "run-manifest:" + lowercaseHex(SHA-256(
    frame(UTF8("canonical-run-manifest-id-v1")) ||
    frame(canonicalJson(runManifestWithoutRunManifestId))))
~~~

每个`Without...Id`删除且只删除命名顶层self ID；不得保留null、删除其他字段或加入该文件自己的SHA。module receipt的payload descriptors按fileName、upstream refs按artifactId、gap refs按ID；analysis step receipt的semantic descriptors按fileName、upstream analysis steps按closed registry依赖顺序、provenance module refs按semantic analysis step key再按module runtime order、gap refs按ID；run manifest的analysis step publications按closed registry依赖顺序，其他unordered refs按artifactId。receipt/manifest file SHA总是对含已计算ID的完整canonical file bytes求普通SHA-256。archive manifest是policy控制的`STANDALONE_JSON`并只描述五项semantic descriptors/root；M4 publication同样不参与它所描述的任何root。上述domain separators、framing、prefix、ordering与排除集均为versioned executable contract，任何变化必须升version。

本次目标设计的schema版本变动明确如下；这是**目标合同修订**，不是当前实现成熟度声明：

| 目标schema | 版本决定 | 原因 |
| --- | --- | --- |
| local capture request/receipt/source registration | 新增`local-git-capture-request-v1`、`local-git-capture-receipt-v1`、`source-registration-v1` | 独立本地object-database capture seam此前没有wire contract |
| analysis request/reference | 唯一接收`analysis-run-request-v2`；保留`analysis-run-reference-v1`view | v2把budget/全部controls/policy/seed变成content refs并冻结round/parent lineage；v1显式拒绝 |
| artifact policy/analysis step/run persistence | 新增`artifact-policy-registry-v2`、`analysis-step-receipt-v1`、`run-manifest-v1` | 固定type/version→prefix/media/envelope/empty/public-content policy、analysis step provenance与唯一root manifest |
| per-module receipt | 新增`module-receipt-v1` | module完成现在要求独立、无自引用receipt |
| VerifiedSourceInventory target payloads | `verified-snapshot-v1`→`v2`；`verified-source-inventory-admitted-source-request-v1`→`v2`；`verified-source-inventory-verified-source-index-v1`→`v2`；删除旧M3 summary envelope | 加入git mode、text/media disposition、nullable line index及分母计数；M3 module publication恰含三个已注册semantic payload与receipt，analysis step store另建analysis step receipt |
| NineSectionDocument standalone outputs | `nine-section-document-nine-section-plan-v3`、`nine-section-document-document-markdown-v1`、`nine-section-document-trace-record-v3`、`nine-section-document-candidate-v4`、`nine-section-document-validation-baseline-v1`、`nine-section-document-archive-manifest-v1`、`analysis-step-receipt-v1`、`run-manifest-v1` | v3封闭ReaderItem/template-slot与typed Trace/source/profile lineage；八项位置/identity独立，五semantic→archive→receipt→root manifest→M4 publication/receipt，无嵌套或duplicate manifest |
| NineSectionDocument module/external payloads | `nine-section-document-repository-coverage-ledger-v1`、`nine-section-document-nine-section-plan-draft-v3`、`nine-section-document-rendered-document-v2`、`nine-section-document-trace-set-v3`、`nine-section-document-candidate-run-publication-v4`、`nine-section-document-validation-receipt-v4` | M1先安装无环final ledger再安装plan draft；planner/trace跟随closed union升版；validator显式绑定全部实际direct preimages与八analysis step typed chain |

各AnalysisStep文档必须列出上述standalone schema的完整字段；同名module preimage schema和standalone output schema不是同一个artifact，不能互换。reader exact-version解析；旧artifact不可原位迁移。当前实现成熟度只以本文件第15节为准；本表不得被引用为“代码已支持”。

- JSON 为 UTF-8、无 BOM、LF；object key canonical 排序；number 使用 schema 允许的唯一表示。
- JSONL 每行一个 canonical object并以 LF 结束；顺序由稳定 semantic key 定义。
- 数组只有在合同声明 unordered 时按稳定 ID 排序；控制流、步骤和章节顺序保持语义顺序。
- artifactId 只按registry选定的四种framed公式之一计算；同一`(artifactType,schemaVersion)`不存在第二套prefix、envelope或“payload-only”公式。
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
7. 分析步骤“流程解释” 的 R0_REGISTRY_PROPOSAL 只提出有 Capsule basis 的仓库特定词、claim 和 question；程序先验证并冻结唯一 `RepositoryInterpretationRegistry`。之后 R1/R2 才能在同一 Flow 的 finite provisional keys 中选择。R0/R1/R2 是三个独立task kinds；R1/R2 属于同一产品 Candidate，ReaderCandidateRound 1/2 最多两份 Candidate，三者不能混用。
8. renderer永远只有一个plan输入：NineSectionDocument M2读取M1的verified plan-draft module payload；run完成后的外部rerender/validator读取语义等价的public `nine-section-plan.json`。两条路径都不得读取source、Proof、registry或model response。
9. `N` 个 compiled Flow 精确产生 `N` 个 Capsule，并按 `ELIGIBLE ⊎ INELIGIBLE` 划分。令 `E` 为eligible子集：FlowInterpretation只为这 `E` 个Flow各产生恰一个R0 task/disposition；全部R0 dispositions闭合后精确冻结1个 `RepositoryInterpretationRegistry`。令 `R` 为R0 READY子集：只有这 `R` 个Flow各产生R1/R2两个tasks，最终每个eligible Flow各有一个`FlowInterpretationDisposition`。每个planned task恰有一个`ModelTaskDispositionV1`；round records和started Provider calls只是tasks的可验证子集。ineligible Flow没有任何FlowInterpretation task/round/disposition，而是保留其BusinessFlows gap并由RepositoryKnowledge生成唯一`MODEL_INELIGIBLE_TECHNICAL_FALLBACK`。于是RepositoryKnowledge仍为全部`N`个Flow一一生成admission/fallback decision并精确产生1个RepositoryKnowledge；NineSectionDocument每run精确产生1个NineSectionPlan和1个document.md。正常且每个eligible R0都READY、每个planned task都实际调用并成功时才是 `E+2E=3E` calls；任何较少调用必须由typed task dispositions与eligible R0/R1 GAP/FAILED或ineligible partition共同解释。
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
- maxInterpretationTasks、maxInterpretationResponseBytes；每个 eligible Flow 恰好一个 R0 task，R0 READY 的 Flow 恰好各一个 R1/R2 task；
- maxKnowledgeItems、maxReaderItems、maxArchiveFiles/Bytes。

超限不得截断后声称 COMPLETE。可安全隔离并形成合法业务处置时进入 Gap，在 RepositoryCoverageLedger 记录原 denominator 与受影响 IDs；破坏引用、覆盖或完整性时 fatal。分片只允许按稳定 fileId/entryId/flowSliceId 调度，不能降低覆盖或改变 canonical 输出。

### 13.6 安全

- `LocalGitCommitCaptureAdapter`只按用户给定的完整40-hex commit从本地Git object database读commit/tree/blob；不读ref/index/worktree，不clone/fetch/pull，不触发hook/filter/客户代码。missing/promised object必须失败，不能联网补齐。
- analysis core只接`sourceRegistrationId`和content-addressed refs；本机repository/snapshot/store path只存在于capture transport/private registry，不进入任何public Interface record、run artifact、View、error或identity。
- 只读已注册快照；不运行客户 Maven/Gradle、插件、测试、脚本、应用、SQL 或 MyBatis runtime。
- 所有tracked regular files均验size/SHA并进入coverage；`NON_ANALYZABLE_MEDIA`保留bytes与identity但不给parser。symlink、gitlink/submodule和未知Git mode在capture时fail closed。
- 所有路径逐段 NOFOLLOW；普通文件在分配前做 size gate，读取后复查；目录在收集 limit+1 前停止。
- MyBatis DOCTYPE 可以存在，但 external DTD、general/parameter entity、schema 和所有网络 resolver 必须禁用；无法执行策略即 fatal。
- 模型只看到 Capsule canonical JSON；源码中的 prompt injection 只是 evidence data。
- R0 label/purpose 是不可信 JSON data：NFC、控制字符和双向覆盖检查、长度/字节/basis闭包都由程序执行；它们绝不成为 prompt 指令、Fact、locator、Flow 或 Markdown。可选组织 seed 只有被 R0 明确 `sourceSeedKey` 引用、值逐字段相等且同一 Capsule basis 非空时才可进入冻结 registry。
- configuredAdapterId、configuredAuthMode、expected/observed upstream provider、model、reasoning effort、sandbox 分字段记录。
- `RepositoryAnalysisAgent` 的 artifact/trace 查询只接受 run 与 typed identity；Java API、CLI 与 loopback HTTP 都不得接受或回显主机 `Path`。HTTP 只绑定 loopback 且每个请求校验 run-scoped bearer capability；inspect/artifact/render/validate/trace 不调用 Provider、不执行客户代码。
- secret、环境变量值、绝对本地路径、raw stderr 和 reasoning 不进入 Candidate、异常或正文。

### 13.7 稳定 failure code 家族

| 分析步骤 | 目标 code 家族 |
| --- | --- |
| shared artifact/store persistence | ARTIFACT_POLICY_REGISTRY_INVALID、ARTIFACT_POLICY_NOT_FOUND、ARTIFACT_POLICY_MISMATCH、MODULE_INSTALL_REQUEST_INVALID、MODULE_PAYLOAD_NOT_CANONICAL、MODULE_PUBLICATION_COLLISION、MODULE_PUBLICATION_INVALID、ANALYSIS_STEP_INSTALL_REQUEST_INVALID、ANALYSIS_STEP_PUBLICATION_COLLISION、ANALYSIS_STEP_PUBLICATION_INVALID、RUN_MANIFEST_COLLISION、RUN_MANIFEST_INVALID、ATOMIC_MOVE_UNSUPPORTED |
| local capture | LOCAL_GIT_REQUEST_INVALID、LOCAL_GIT_REPOSITORY_INVALID、LOCAL_GIT_COMMIT_NOT_FOUND、LOCAL_GIT_OBJECT_CORRUPT、LOCAL_GIT_TREE_ENTRY_UNSUPPORTED、LOCAL_GIT_NETWORK_FORBIDDEN、LOCAL_CAPTURE_INSTALL_FAILED |
| 01 | REQUEST_SCHEMA_INVALID、CAPTURE_IDENTITY_INVALID、SOURCE_REGISTRATION_NOT_FOUND、SOURCE_PATH_INVALID、SYMLINK_FORBIDDEN、SOURCE_HASH_MISMATCH、SOURCE_TEXT_DISPOSITION_INVALID、VERIFIED_SOURCE_INVENTORY_RESOURCE_LIMIT_EXCEEDED |
| 02 | APPLICATION_PROFILE_UNRESOLVED、ENTRY_DISCOVERY_INVARIANT_BROKEN、MAPPER_CATALOG_AMBIGUOUS、CAPABILITY_ACCOUNTING_BROKEN |
| 03 | GRAPH_REFERENCE_BROKEN、CALL_TARGET_AMBIGUOUS、CFG_POLARITY_MISSING、DATA_FLOW_BINDING_UNPROVEN、EVIDENCE_GRAPH_INVARIANT_BROKEN、XML_EXTERNAL_RESOLUTION_ATTEMPT |
| 04 | PROOF_REFERENCE_BROKEN、PROOF_SOURCE_REOPEN_MISMATCH、FACT_ACCOUNTING_INVARIANT_BROKEN、CONFLICTING_FACTS |
| 05 | FLOW_OUTCOME_CLOSURE_BROKEN、FLOW_ACCOUNTING_INVARIANT_BROKEN、EVIDENCE_PROJECTION_UNSATISFIABLE、CAPSULE_BUDGET_EXCEEDED |
| 06 | REGISTRY_PROPOSAL_TASK_INVALID、REGISTRY_PROPOSAL_RESPONSE_INVALID、REGISTRY_PROPOSAL_REFERENCE_INVALID、REGISTRY_SEED_MISMATCH、REGISTRY_FREEZE_INCOMPLETE、REGISTRY_IDENTITY_COLLISION、MODEL_TASK_INVALID、MODEL_TASK_NOT_RUN_UPSTREAM_INVALID、MODEL_RESPONSE_INVALID、MODEL_REFERENCE_INVALID、MODEL_REVIEW_EXPANDED、MODEL_RUNTIME_IDENTITY_MISMATCH、PROVIDER_FAILURE_AFTER_START |
| 07 | INTERPRETATION_ADMISSION_INVALID、FLOW_INTERPRETATION_TASK_DISPOSITION_CLOSURE_INVALID、TECHNICAL_FALLBACK_NOT_TOTAL、KNOWLEDGE_OWNER_INVALID、KNOWLEDGE_CONFLICT_UNRESOLVED |
| runtime/08 | ANALYSIS_RUN_REQUEST_UNSUPPORTED、ANALYSIS_STEP_EXECUTION_REQUEST_INVALID、ANALYSIS_STEP_EXECUTION_UPSTREAM_INVALID、PROCESS_INTERRUPTED、PROVIDER_FAILURE_AFTER_START、NINE_SECTION_INVALID、READER_ATOM_LOSS、REPOSITORY_COVERAGE_LEDGER_INVALID、DOCUMENT_HASH_MISMATCH、TRACE_CLOSURE_BROKEN、ARCHIVE_IDENTITY_COLLISION、RUN_LIFECYCLE_TRANSITION_INVALID、RUN_WORKER_ALREADY_ACTIVE、RUN_NOT_FOUND、ARTIFACT_QUERY_INVALID、ARTIFACT_NOT_FOUND、ARTIFACT_IDENTITY_MISMATCH、ARTIFACT_CONTENT_NOT_PUBLIC、ARTIFACT_RESPONSE_BUDGET_EXCEEDED、RENDER_NOT_AVAILABLE、RUN_NOT_VALIDATABLE、OBSERVATION_BUDGET_EXCEEDED、VALIDATION_SOURCE_INVALID、VALIDATION_ANALYSIS_STEP_PUBLICATION_INVALID、VALIDATION_MODULE_PUBLICATION_INVALID、VALIDATION_REPOSITORY_SCOPE_INCOMPLETE、VALIDATION_REPOSITORY_COVERAGE_INCOMPLETE、VALIDATION_REGISTRY_LINEAGE_INVALID、VALIDATION_DOCUMENT_RERENDER_MISMATCH、VALIDATION_SOURCE_TRACE_INVALID、VALIDATION_CANDIDATE_MANIFEST_MISMATCH |

具体实现可以保留更细 code，但不能把 integrity/fatal 降级成普通 Gap。

### 13.8 验证与测试目标

- 每个分析步骤有正例、缺边/缺 span/漂移/预算 mutation 和不同 root 的 determinism 测试。
- local capture fixture证明exact 40-hex commit只读object database、完整regular-file tree union、binary `NON_ANALYZABLE_MEDIA`仍验hash，以及worktree/index mutation不改变snapshot bytes；symlink/gitlink/unknown mode/missing object均无registration并以stable code失败。
- 每个模块的测试必须先观察payload+`module-receipt.json`原子安装；缺receipt、receipt root篡改、payload-after-receipt partial install和内存旁路都失败。运行时除最终NineSectionDocument `document.md`外不得出现`.md`生成物。
- 五张图分别有引用闭包、覆盖和跨图 edge mutation 测试。
- DepotHead walkthrough 必须先补齐通用数据流/Fact 能力，再以 real fixed slice 做正向验收；不得用目标 JSON 当 golden。
- 0 Flow/0 Capsule 有直接测试，断言 0 Provider call 且 Gap 仍进入九章计划。
- 至少两个入口/Flow 的 repository fixture 验证：每 Flow 独立 Capsule；每个eligible Flow独立R0 proposal/disposition，同Flow finite-key R1/R2和FlowInterpretation disposition；每个ineligible Flow零FlowInterpretation artifact且保留BusinessFlows Gap；一个全仓 frozen registry。令`E`为eligible数、`R`为R0 READY数，planned tasks精确为`E+2R`，R0/R1/R2 shard denominators分别为`E/R/R`；正常`R=E`且所有task都实际调用成功时Provider calls为`3E`，R1 typed GAP/FAILED时对应R2 task保留并写`NOT_RUN_UPSTREAM_FAILED`且不调用Provider。READY子集独立candidate，RepositoryKnowledge为**全部**Flow各有独立decision且全仓只有一个跨 Flow RepositoryKnowledge，NineSectionDocument只有一个九章plan/document；任何per-Flow Markdown或片段拼接应失败。
- R0 测试覆盖 novel repository term、同 label 不同 Flow 不误合并、optional seed exact-match/拒绝、basis缺失、非法 Unicode/控制字符、预算、单 Flow R0业务处置后其他 Flow bytes不变，以及 registry freeze 的排序/identity determinism；R0不得产生 Fact/locator/Flow/Markdown。
- 同一 multi-flow fixture 改变 shard size和程序遍历顺序，最终 analysis step bytes/knowledge/plan/document 必须一致；缺 shard、重叠 shard、单 Flow PASS 冒充 run complete 均 fail closed。
- 相同canonical rounds与task dispositions必须产生相同admitted knowledge、plan和Markdown bytes。
- M2 renderer测试用只含M1 verified plan-draft payload的隔离目录；外部rerender/validator测试只含public `nine-section-plan.json`。两者都证明没有source/model读取能力并产出相同Markdown bytes。
- 独立 validation 重开 snapshot，逐项重算 source、graphs、Proof、Capsule、rounds、knowledge、plan、document 和 Trace roots。
- 同一 `RepositoryAnalysisAgent` 通过 Java、CLI、loopback HTTP 三种 Adapter 做 contract conformance：start/executeStep/inspect/artifact/render/validate/trace 一一同义；RepositoryKnowledge显式执行直接消费validated VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs、ProvenCodeFacts、BusinessFlows、FlowInterpretation refs且Provider调用为零；artifact 用 run+artifact identity、拒绝Path/越权/截断；`PATH_FREE_COMPLETE_UTF8` document/plan 正例返回完整bytes，而含SourceLocator/SourceExcerpt的raw Trace/source artifact在`COMPLETE_UTF8`下必须稳定返回`ARTIFACT_CONTENT_NOT_PUBLIC`；所有观察方法零 Provider call。
- 最小运行测试覆盖`start/executeStep`创建QUEUED后立即返回、同进程single worker、四种FINISHED result、FAILED无result、started Provider failure不重试/切换、HTTP 202以及CLI先输出runId再前台驱动；任何并发第二worker或lifecycle/result混写均fail closed。
- 文档-only work 不需要运行这些测试；它们是后续实现合同。

### 13.9 已冻结的架构裁决

后续实现 Agent 可以选择内部类拆分、集合实现、parser library Adapter 和性能优化，但不得自行改变下列产品/架构决定；若现实材料无法满足，输出 Gap/fatal 或提交新的设计变更，不能在代码里暗改合同。

| 已冻结裁决 | 不允许实现者自行推断的替代方案 |
| --- | --- |
| 本地来源由独立`LocalGitCommitCaptureAdapter`按exact 40-hex commit只读Git object database，完整枚举tracked regular files并产snapshot/receipt/registration | 不读branch/ref/index/worktree，不fetch，不让analysis core接Path；binary不静默省略，symlink/submodule/未知mode不降级继续 |
| 唯一主线是 01→08，成功边界是 immutable persisted analysis step artifact set | 不把分析步骤重新合并成内存 pipeline，也不跳过分析步骤“直接生成文档” |
| 每个命名模块完成即原子安装canonical JSON/JSONL payload+`module-receipt.json`；最终`document.md`是唯一人读运行产物 | 不等analysis step末尾或最终archive才首次落盘，不用内存对象越过模块，不生成per-module Markdown/第二份document |
| 分析步骤“程序图” 恰有五张一等图；分析步骤“已证明代码事实” 的 Fact 必须逐 atom CLOSED Proof | 不用 repository blob、文件顺序、字符串相似或模型判断代替 graph/Proof |
| 每个 compiled entry 恰一个入口根 Flow，每个 Flow 恰一个 Capsule，多个终点是 Outcomes | 不按终点拆 Flow，不让模型自行找入口、分支或补源码 |
| 目标范围是完整冻结仓库；DepotHead 只是一条 walkthrough/fixture | 不把八文件 BOUNDED_PATH_SET、单入口或单 Flow PASS 当 repository completion；不得因分片/预算静默降覆盖 |
| LLM 只在 分析步骤“流程解释”、只读单 Flow Capsule：R0可提出 bounded open label/purpose，程序验证并冻结一个 registry；同 Flow R1/R2只返回其 finite provisional keys；分析步骤“仓库知识” 程序最终准入 | 不让 R0 创建 Fact/locator/Flow/Markdown，不允许跨 Flow context、开放事实总结、模型自批或后续分析步骤补调模型 |
| 每个 eligible Flow 恰一个隔离 R0 task/disposition；全部R0 dispositions闭合后恰一 frozen RepositoryInterpretationRegistry；R0 READY 才恰有R1/R2 tasks | 不从组织seed直接绕过R0，不在registry freeze前编R1/R2，不省略`E+2R` planned tasks；started Provider调用失败时当前run失败且不自动重试/切换 |
| `N` FlowSlice → `N` Capsule；eligible子集才有FlowInterpretation disposition、ineligible子集零FlowInterpretation artifact；随后全部`N`个Flow各有RepositoryKnowledge decision → 恰1 RepositoryKnowledge → 恰1九章plan/document | 不一 Flow 一 Markdown，不预渲染片段再拼接，不为某个失败 Flow另开旁路文档 |
| 0 Flow/0 Capsule 是合法带 Gap 结果且 Provider 调用数必须为 0 | 不为了“非空文档”伪造 Flow、Capsule 或模型解释 |
| 分析步骤“九章文档” 计划恰九章；M2只读M1 verified plan draft，外部rerender只读public `nine-section-plan.json`，二者为同一plan语义 | 不让 renderer 回读 source、Proof、registry 或 model response，也不临时补章或制造analysis-step-publication前置依赖 |
| Candidate 始终 `UNPUBLISHED_CANDIDATE`，Selection/发布是后续显式流程 | 不自动发布，不把 analysis success 等同于业务批准 |
| `executeStep`逐字段匹配frozen source/input/tool/profile/schema/prompt/policy hashes并重验连续upstream analysis step roots，再创建新run | 不做宽松版本兼容、隐式迁移、覆盖旧run、重扫源码或把显式新执行伪装成同run resume |
| 单一 run-centric `RepositoryAnalysisAgent` 固定 start/executeStep/inspect/artifact/render/validate/trace，Java/CLI/loopback HTTP逐项同义 | 不暴露run目录或Path，不让Adapter各自发明状态/错误/渲染语义，不用final-only API替代分析步骤可观察性 |
| lifecycle固定为QUEUED/RUNNING/FINISHED/FAILED，final result固定四种且只在FINISHED非空；single-process worker驱动 | 不把INCOMPLETE_*当进行中，不同步阻塞HTTP start，不让CLI在runId产生前开工，不实现跨进程queue/worker接管 |
| run completion 必须由 RepositoryCoverageLedger 证明所有文件/site/entry/graph/fact/outcome/flow/proposal/knowledge/reader owner有唯一处置 | 不因部分成功、计数相等但 ID 集不等、缺 shard 或 omitted item 宣布 COMPLETE |
| target design 优先于当前类/测试；历史pre-reset DepotHead审计为Gap、0 Flow、0 Capsule | `CodeToMarkdownAgent`及其旧Interface/CLI/兼容路径已经由Wire Reset删除；不得以nonpublic Adapter或迁移桥恢复，也不为历史fixture降低目标 |

每份分析步骤文档进一步冻结该分析步骤的输入、输出文件、records、算法、Gap/fatal、安全/预算和验收 seam。实现工作只剩编码与已指定合同内的工程取舍；新增 schema 字段、状态、业务术语、自动重试、合并规则或降级策略都必须先改设计。

分析步骤内部的每个命名模块也使用同一九项合同：解决的问题；精确上游输入/前置；确定性处理顺序与 LLM 角色；目标输出结构和 DepotHead 示例；不变量；Gap/fatal/显式artifact复用；下游后置保证；明确非目标；公共测试 seam/验收。模块不能依赖同分析步骤总述来省略其中任一项，模块连接只能按各 analysis step doc 声明的顺序与 typed records 发生。

### 13.10 Agent 执行纪律

目标实现采用固定 Agent handoff，不把设计选择留给实现 Agent：

1. **Luna/xhigh 写 RED**：只对一个模块的 public Interface/record/artifact seam 写测试；从 detailed analysis step doc 的 fixture、independent expected values 和 failure code 构造一个行为一个 RED。golden 手写自合同/冻结输入，禁止由待测 production code 或生成器反推。
2. **Terra/xhigh 做 GREEN**：只有观察到该 RED 因预期缺失能力失败后，才能修改模块拥有的 production package。按 artifact schema、确定性算法、identity、Gap/fatal 和显式artifact输入顺序做最小 vertical slice；不得顺手扩 schema、兼容错误旧设计或从未声明analysis step重读数据。
3. **Sol/xhigh 只调试异常**：仅当 RED 不是预期原因，或同一 slice 在两次有根据的 GREEN 修订后仍失败时介入。Sol 查明实现/fixture/环境根因，但无权改合同；合同确需变更时先停下并更新详细设计/version。
4. 每个 GREEN 后只运行该模块 selector 和直接跨模块 contract selector；不得运行 full suite，除非当次用户显式要求。测试/实现 Agent 均禁止网络、live Provider、客户 Maven/Gradle、客户代码执行、生成器和部署。

本目标的唯一 Design Authority 是 **gpt-5.6-sol / ultra（Sol/ultra Design Authority）**。Sol/xhigh 是受限 debug 角色，不得替代 Sol/ultra 作架构裁决；Luna/xhigh、Terra/xhigh 和 Sol/xhigh 都不能自行改 durable design。

新目标模块的拥有范围固定为八个语义根：`org.sourceanalysis.app.analysis.inventory`、`.discovery`、`.graph`、`.fact`、`.flow`、`.interpretation`、`.knowledge`、`.document`。模块类留在其所属语义根内；不得派生编号、`common`、`shared`、`misc`或`utils`包。源码捕获属于`org.sourceanalysis.app.capture.localgit`；持久化wire/store、源码证据、运行编排、独立验证分别属于`org.sourceanalysis.app.artifact`、`.evidence`、`.runtime`、`.validation`；Adapter只属于`.adapter.cli`、`.adapter.http`、`.adapter.provider`。tests镜像生产package，冻结fixtures/goldens位于`src/test/resources/analysis/<semantic-package>/<module-key>/`，validator fixture位于`src/test/resources/validation/run-validator/`。未来数据库根保留为`org.sourceanalysis.db.analysis`，本设计不创建数据库代码。

允许 mock：只读 source handle、文件系统partial-install/atomic-move boundary、clock（仅非identity显示）和FlowInterpretation Provider transport。禁止 mock：canonicalizer、identity/hash material、accounting、graph/Proof/Flow/admission/planner core、上游 artifact parser/validator；这些必须用 schema-valid frozen files。禁止反射/private-field 断言、调用当前私有实现、把 exception message 当 golden。

每个 analysis step doc 的模块 brief 给出 exact Maven selector。命令均从本 source 根运行，形式为 `mvn -Dtest=<ExactTestClass> test`；它运行的是本 Agent 模块项目，不是客户 Maven。模块完成后，在同一文档的当前实现审计中把对应项从“尚未符合目标”更新为有证据的中文状态，并记录 selector/fixture/version；不得用 GREEN 外推未测模块或真实 DepotHead success。

### 13.11 实现偏离与设计变更协议

Luna/xhigh 或 Terra/xhigh 在以下任一情况必须停止受影响 vertical slice：需求按现合同无法满足；预期 RED 不成立或因错误原因失败；现有实现与合同冲突；必需数据在上游 artifact 不存在；方案需要猜 schema/语义/failure/model boundary；或实现路径明显偏离业务/信任目标。Agent 先在自己唯一 progress 文件记录可复验证据、受影响 IDs/analysis steps、为何不能继续和最小可选项，然后把请求交给 **Sol/ultra Design Authority**；不得静默改变 schema、golden/expected value、失败等级、重试、模型职责或上下游边界。

只有 Sol/ultra Design Authority 可以裁决**单模块或单分析步骤内部**、局部、可逆、语义等价且不改变跨分析步骤 contract 的实现权衡，例如内部命名/算法、模块内部拆分、明确 non-identity 的观测字段或满足同一硬上限的预算实现。裁决不得削弱业务目标、Fact准确、evidence/Proof/Trace closure、逐模块/分析步骤持久化与显式复用、固定九章、安全或 LLM 受限职责。局部裁决先更新对应 analysis step doc/schemaVersion，再允许 Luna 重写 RED、Terra 继续。Sol/xhigh 只能提交 debug 证据，不能批准该变更。

**MUST/STOP 升级规则**：任何影响跨分析步骤 Interface、artifact field semantics、identity DAG、composition/accounting invariant、分析步骤边界/顺序/持久化，或任何业务目标、信任模型、九章合同、来源范围、安全/模型边界的修改，Sol/ultra Design Authority 也 **MUST NOT** 自行决定。相关实现 **MUST STOP**；Sol/ultra 必须整理可复验证据、影响范围、至少两个可行备选（若确实只有一个则说明为何）、推荐项与不变项，交回用户讨论确认。用户确认前不得改 RED、production code 或当前有效合同；确认后才更新 DESIGN/相关 analysis step doc/schemaVersion并恢复实现。

progress 只记录编码Agent中断后的继续工作证据与下一动作；durable design 只保存当前有效产品合同，不写时间流水账。两者不得被误建成产品runtime recovery。每个模块 handoff 的 Luna/Terra 指南都必须显式引用本节。

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

本节只描述当前仓库事实。它不缩小前述目标，也不把已经删除的pre-reset测试结果外推为当前能力或DepotHead结果。

| 目标分析步骤/横切能力 | 中文状态 | 当前事实 | 与目标的差距 |
| --- | --- | --- | --- |
| 工程目录、Maven与Java命名空间 | **已实现（结构）** | 工程位于`backend-agents/sources/source-code/`，坐标为`org.sourceanalysis:source-code-analysis-agent`，生产package都在批准的`org.sourceanalysis.app`语义根下。 | 语义package目前主要是`package-info.java`骨架；结构正确不代表分析能力存在。 |
| JDK 17 Toolchain | **已实现（构建）** | 项目内Toolchain选择JDK 17，compiler release固定为17。 | 只约束Agent自身构建；不执行客户Maven，也不证明任一分析步骤。 |
| 新wire头门禁 | **已实现（窄门禁）** | `AnalysisWireFormatGuard`只接受JSON对象头`wireKind=SOURCE_ANALYSIS`与`wireVersion=v1`，并以稳定`UNSUPPORTED_ANALYSIS_WIRE`拒绝顶层描述符元数据中的pre-reset path、编号stage、stage receipt/schema、旧Maven/Java package身份和wire alias；不扫描业务内容。 | 该guard明确把owner-specific descriptor验证留给未来实现；它不是canonical artifact store、schema registry或八步reader。 |
| Local Git capture / 分析步骤“已验证源码清单” | **尚未实现** | 只有`capture.localgit`与`analysis.inventory`package骨架；没有capture、source registration、inventory module或四项正式输出。 | 需按分析步骤文档实现并对完整固定commit做离线验收。 |
| 分析步骤“应用发现” | **尚未实现** | 只有`analysis.discovery`package骨架；没有Maven/Spring MVC/MyBatis detector或五项正式输出。 | 需建立全仓site/entry/Mapper分母，不能复用已删除旧wire。 |
| 分析步骤“程序图” | **尚未实现** | 只有`analysis.graph`package骨架；当前没有五张图、graph index、Gap或receipt。 | 需证明结构、调用、控制、数据与证据闭包。 |
| 分析步骤“已证明代码事实” | **尚未实现** | 只有`analysis.fact`package骨架；没有Fact、Proof、Gap ledger或accounting。 | 历史POC发现的Evidence语义错配只作为反例，不能成为当前Fact。 |
| 分析步骤“业务流程” | **尚未实现** | 只有`analysis.flow`package骨架；没有Flow、Outcome、EvidenceCapsule或coverage。 | 当前不存在可供模型读取的Flow；历史“Gap、0 Flow、0 Capsule”不是当前运行结果。 |
| 分析步骤“流程解释” | **尚未实现** | 只有`analysis.interpretation`package骨架；没有Provider adapter、R0/R1/R2 task、registry或十项输出。 | scripted、Codex Subscription和OpenAI-compatible实现均待后续工作；当前模型调用能力为零。 |
| 分析步骤“仓库知识” | **尚未实现** | 只有`analysis.knowledge`package骨架；没有admission、anchor merge、RepositoryKnowledge或coverage draft。 | 需对全部Flow给唯一决定并汇总恰一份仓库知识。 |
| 分析步骤“九章文档” | **尚未实现** | 只有`analysis.document`package骨架；没有planner、renderer、Trace、Candidate、run manifest或八项输出。 | 当前没有SourceAnalysis生成的Markdown。 |
| Java/CLI/HTTP runtime与独立validation | **尚未实现** | `runtime`、`validation`与adapter package只有骨架；不存在`RepositoryAnalysisAgent`七方法、`source-analysis` CLI或loopback HTTP。 | 需在八步业务模块之上实现同一核心及其三个同义入口。 |
| pre-reset Stage/POC代码 | **已删除；仅历史证据** | 旧Stage01–04、POC、旧tests/fixtures、`CodeToMarkdownAgent`与旧CLI/API均已物理删除。 | 只允许从Git历史和保留的history/progress中学习测试意图；禁止兼容层或旧wire复活。 |

因此当前最重要的结论是：

- Wire Reset已经完成“在哪里写、叫什么、拒绝哪类wire”，但尚未完成“怎样分析业务”；
- 当前代码没有对jshERP执行新主线，因此没有当前Flow、Capsule、RepositoryKnowledge或九章产物；
- 历史pre-reset的“Gap、0 Flow、0 Capsule”和POC语义审计只是实现新Proof/dataflow门禁的反例来源；
- 后续Luna/Terra必须按八份详细设计逐步实现，不得把历史绿色测试写回成当前能力，也不得为复用旧类降低目标合同。

## 16. 详细文档导航

1. [分析步骤“已验证源码清单”](analysis-steps/01-verified-source-inventory.md)
2. [分析步骤“应用发现”](analysis-steps/02-application-discovery.md)
3. [分析步骤“程序图”](analysis-steps/03-program-graphs.md)
4. [分析步骤“已证明代码事实”](analysis-steps/04-proven-code-facts.md)
5. [分析步骤“业务流程”](analysis-steps/05-business-flows.md)
6. [分析步骤“流程解释”](analysis-steps/06-flow-interpretation.md)
7. [分析步骤“仓库知识”](analysis-steps/07-repository-knowledge.md)
8. [分析步骤“九章文档”](analysis-steps/08-nine-section-document.md)

旧POC说明只存在于`docs/history/`，不属于活动导航。共享九章权威见 [NineSectionProfile](../../../../shared/source-agent-contracts/README.md)。
