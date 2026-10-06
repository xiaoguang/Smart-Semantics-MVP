# Ontology 小范围实验验收记录

状态：2026-10-01，**类型化合同和 scripted 全链通过；真实 P3 已完成六页调查并自主选中四类任务，但连续阅读未完成，仍为0项无提示已审小样，G1未通过**。历史P1五项人工选材已审小样和旧P2记录保留；正式O0–O3未开始。工程接线通过不等于自主本体识别通过。

## 固定输入与隔离

- R4：`analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`，通过正式 receipt、policy 和 reader 严格重开。
- 同源 R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。P0 核对过同 R0 行段读取；当前 P1/P2 模型包只含所选 R4 单元，**没有把 DDL 当成已提供模型的资料**。
- 历史P1的人工选材模板为`ontology-p1-fixed-source.example.json`，P2模板为`ontology-poc.example.json`。它们及独立旧PoC驱动保留在原工作区，不属于正式O0–O3交付或当前运行说明。P1只用于固定源码验收；P2仅从原始R4导航开始，不读取P1清单、样本、模型输出或历史答案。
- P1与P2隔离；P2后续诊断批次之间只显式复用**同一P2输入**下完整保存且逐字段匹配的决策请求/响应，不复用P1。最初P1/P2各有一次子进程初始化失败，是否到达模型服务无法确认；后续有响应的请求另计。

## P0：真实资料读取与容量

使用 `OntologyFixedCorpusProbeTest` 从已经保存的固定来源只读测量：

| 项目 | 实际结果 |
| --- | ---: |
| R4 入口 | 339 |
| 可寻址单元使用次数 / 去重内容单元 | 40,843 / 15,252 |
| 最大完整单元 | 159,204 UTF-8 字节 |
| 全仓导航页 | 6 页，每页最多 64 入口 |
| 最大一页 SURVEY 输入 | 23,372 字节 |
| 空回答下全局 PRIORITIZE 输入 | 1,145 字节 |
| 四单元 scripted 提取 / 审阅输入 | 23,626 / 23,970 字节 |

R4 严格重开最近一次约 29.5 秒，同 R0 全文重开约 30.9 秒；两者都物化整份来源，不能称“按需磁盘读取”。单次 JVM 已用堆前后差在多次测量中明显浮动，**不是峰值内存或稳定占用**。159,204 字节的最大单元超过本次实验的请求上限；如果模型选到它，程序应明确报超限，不能静默截断。

P1 五个完整阅读包已逐一从固定 R4 校验身份和大小：

| 人工选材任务 | 单元数 | 阅读包字节 |
| --- | ---: | ---: |
| 单据头对象/变体 | 1 | 13,997 |
| 明细对象/来源映射 | 2 | 56,463 |
| 审核操作 | 2 | 14,767 |
| 统计口径 | 4 | 17,688 |
| 财务跨入口联系 | 9 | 46,561 |

首次将头和长明细方法合成一个对象问题时，包超过 60 KB 小样界限，P0 测试按预期失败；拆成两个完整问题后通过。拆分没有截断或改写原文。P1 计划五次 EXTRACT＋五次 REVIEW，正常情况下 10 次请求，上限 12 次；没有自动重试。字节上限不是模型 token 容量证明，实际请求与响应仍需逐次记录检查。

P2 的示例把每次响应限为20,000 UTF-8字节。直接测试构造六份接近19,000字节的调查输出，验证合并时没有再次附上导航正文，且全局选题请求仍落在160,000字节的实验封套内。它是极端字节输入的scripted检查，不是实际模型上下文或业务发现能力的证明。

## Scripted 能证明与不能证明的事

30 项直接测试已覆盖导航分页、全局选题与处置、材料补读、局部引用、提取到实际原文审阅、修正记录、保存重开、失败不冒充完成及调用上限。`OntologyFixedCorpusProbeTest` 对真实 R4/R0 再完成一次 scripted 请求贯穿，实际模型调用为 0。

这些检查只能证明接口、保存和输入边界。它们**不能证明**：模型会自己找到跨 Controller 业务联系；会把状态、订金和付款区分正确；会给出准确 measure/metric；能在弱模型或小上下文条件下稳定工作。P1、P2 和后续正式验收须分别判定。

真实调用前又补了两项定向失败测试：REVIEW 返回坏JSON时，私有记录保留已完成请求的结构化原因与实际坏响应，已成功EXTRACT仍可查但任务不完成；SURVEY请求结局未确认时只记开始/结束状态，不保存虚构响应或自动重发。两项先失败，窄改后通过；这仍是scripted Provider验证。

首次P1失败后发现，私有尝试记录原本只为 `INVALID_JSON` 保存底层失败输出，普通子进程失败只剩 `UNKNOWN`。新增直接测试先重现该遗漏，再将Provider提供的诊断保存在私有 `providerDiagnosticBase64` 中；`OntologyJobResultStoreTest` 的6项定向测试通过。诊断不进入公开小样或提交。

## 真实请求的实际结果与阻断

| 实验 | 首次请求 | 已发起 / 已审完成 | 实际结果 |
| --- | --- | ---: | --- |
| P1，人工选材 | 单据头OBJECT的EXTRACT | 1 / 0 | 子进程以 `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN` 结束；首次实现未保存底层诊断，不能独立断定原因。无样本。 |
| P2，原始R4独立选材 | 第1页SURVEY | 1 / 0 | 同一错误；本轮私有诊断显示 Codex 无法写入 `~/.codex/state_5.sqlite`（readonly database），随后 `failed to initialize in-process app-server client: Operation not permitted`。无样本。 |
| P1b，同一固定任务新批次 | 单据头OBJECT的EXTRACT | 1 / 0 | 可写执行环境下仍以 `UNKNOWN` 结束；当时截取了CLI输出开头4096字节，末尾服务端错误未进入记录。 |
| P1c，同一固定任务新批次 | 单据头OBJECT的EXTRACT | 1 / 0 | 改为保存有界输出末尾后，取得服务端400：`invalid_json_schema`，指出 `claims[].evidenceRefs` 的 `uniqueItems` 不被Provider结构化输出接口接受。请求在模型生成前被拒绝，无语义结果。 |
| P1d，同一固定任务新批次 | 单据头OBJECT的EXTRACT | 1 / 0 | 移除发送Schema中的 `uniqueItems` 后，服务端400指出 `schemaVersion` 还缺显式 `type`。仍在模型生成前拒绝；据此增加五份Schema的离线字段类型检查。 |
| P1e，前两项新批次 | OBJECT提取→审阅 | 4 / 1项 | 第一项单据头EXTRACT/REVIEW均成功并另存小样；第二项的审阅改写O2定义却只为O1登记correction，程序拒绝第二项完成，原响应及已成功第一项均保留。 |
| P1f，剩余四项新批次 | OBJECT、ACTION、ANALYTIC、RELATE | 8 / 4项 | 审阅提示明确逐个修改的localId要记修正原因；四项均完成提取、审阅、本地校验及私有保存。 |
| P2b，原始R4独立选材新批次 | 第1页SURVEY | 1 / 0 | 模型返回四个调查问题；其中一个系统假设的64位入口编号抄错一段，`ONTOLOGY_SURVEY_ENTRY_UNKNOWN` 拒绝整页。原始响应保留。问题候选里的编号均属于该页，证明不能把此故障泛称“所有引用错误”。 |
| P2c，页内短键新批次 | 六页SURVEY及PRIORITIZE | 7 / 6页 | 六页调查都成功保存。合并选题从33个问题选了2个，延期清单漏掉`192:Q2`；其中一题选了4种任务，超出本轮每题2种的配置。程序在`ONTOLOGY_PRIORITIZE_DISPOSITION_INCOMPLETE`处停止，尚未阅读和生成定义。原响应保留。 |
| P2d，修正全局选题后独立批次 | 六页SURVEY及PRIORITIZE | 7 / 7项决策 | 从39个调查问题选中库存/出入库及账户两个问题，各2种任务；随后首个READING请求在派发前被`ONTOLOGY_TASK_INPUT_TOO_LARGE`拒绝。选中8个入口合计约2,193个技术单元句柄，程序原本不分优先级地全部附到请求里；没有截断或模型阅读结果。 |
| P2e，缩小句柄输入并精确复用P2d | 7项复用决策＋第一题READING | 1新请求 / 8项决策 | 阅读输入实际含8个完整初始方法、6组搜索观察、118个搜索命中句柄；模型要求保留7段并补读7段，封包超过70KB。程序没有截断，但当时局部超限使整批结束。 |
| P2f，精确复用P2e的8项决策 | 第一题局部超限记录；第二题两轮READING | 2新请求 / 8项复用决策 | 实验驱动结束，两题均为`INCOMPLETE/ONTOLOGY_READING_PACKET_TOO_LARGE`，没有EXTRACT/REVIEW或无提示小样。第一题要求7保留＋7补读；第二题第一轮要求6保留＋9补读并列出未知。两题完整原始响应和未完成范围均保存。 |

P2没有收到P1的人工清单或输出；首次失败发生在自主选材的首个请求。P2 的初始化阻断不等于 P1c/P1d 的Schema错误。服务端诊断将后两次定位为**我们的请求合同错误**，不是材料读取或模型业务推理失败。修复保持本地严格Schema不变：发送版去掉不支持的 `uniqueItems`，给纯字符串固定值/枚举补显式类型；离线定向测试先RED再验证。P1e第二项则是模型审阅与既有correction合同不一致，不属于Schema或沙箱错误。P2b显示调查阶段仍暴露并要求模型复写完整入口哈希，违背已有短键设计；本轮按页使用E1…En并保存映射，不猜测错号。旧批次和原始记录不改写。P1实际有12次获得模型响应的请求，另有先前4次在初始化/Schema阶段失败的派发；两类不混算，五项已审小样来自P1e的1项和P1f的4项，提示词版本差异保留在回执。

P1小样初读：对象材料区分了单据主信息与明细；审核/反审核列出当前状态、拒绝及实际更新；统计小样明确SQL行粒度与Java各期间后处理，并将辅助方法的计算式保留未知；跨单据小样给出 `bill_id`、`header_id`、`bill_no` 的具名交接与欠款回写条件，同时明确没有直接证明 `insertAccountItemWithObj` 调用某条XML语句。这些是**P1有人工选材前提的初步核查**，不证明全仓自主发现或所有规则准确。P2c故障后的直接测试先发现原合同要求模型抄写全部31个延期项，又没有把“每题最多2种任务”传入PRIORITIZE。现改为程序机械登记未选项、明确传递并校验任务种类上限。P2d确认阅读请求误附约2,193个单元句柄；1000单元直接测试先RED后GREEN，改为只给已命中的字面搜索句柄并披露列表不完整。P2e确认模型仍可能选择超过单包容量的完整材料；局部超限直接测试先RED后GREEN，P2f已能继续第二题。两题都要求大于当前封套的材料，**这不是靠沙箱权限或机械重试能解决的设计问题**。

## G1结论与已确认的接续设计

1. P1已得到五项EXTRACT→原文REVIEW小样，仍须用户判断业务可用性；它只证明“提供相关材料后可以识别局部定义”。
2. P2确已从原始R4的339个入口无提示完成六页调查和全局选题，未继承P1。但两个自主问题分别横跨6–8个入口；模型要求的完整补读超过70KB单包上限，两题都没有进入EXTRACT。P2累计有18次取得模型响应的真实请求（P2b 1、P2c 7、P2d 7、P2e 1、P2f 2）；其中P2e/P2f对同一P2已完成决策做了精确复用，**不能把复用次数再算成新调用**。
3. G1未通过，正式O0–O3不得开始。用户已同意[薄共同线索与聚焦选材设计](../modules/ontology-recognition/evidence-navigation-and-selection.md)。字段读取、有限查询、首批独立选择及新版决策保存已接通，49项直接测试通过；P2g暴露新的全局汇总容量断点，尚无自主本体识别通过结论。不设计单问题多包摘要归并，不扩大容量掩盖问题。

### 历史P2的读取与容量断点

P2 SURVEY实际只看到HTTP方法/路径、处理方法、组装状态和限制数；Corpus已有反查代码，但P2未使用。程序依据调查问题的全部入口自动读Controller，没有模型单独选择首批。128:Q1的8条导航卡共1,783字节，8个方法原文共34,855字节、完整结构化单元共69,712字节、初始包69,836字节。实际READING输入共130,075字节，还含118个搜索命中句柄及6组搜索观察。这些量不同，不能概括为“每个入口10KB”。

历史只读核对发现真实R4绑定的`statementRefs`元素是对象，旧Corpus按字符串读取；此项已在P2g薄实现中修正。SQL空AST/没有COLUMN节点继续按实际记录披露，不能解释为无表列联系。旧实验记录不改写；新的实际请求与旧输入分别核验。

P1五项小样可供用户查看；旧P2无可展示的已审定义，G1门禁未通过。原始失败及成功决策均保留，不自动扩大调用或进入正式O0–O3。

## 聚焦选材薄实验：P2g

本次只修改既有Corpus、决策runner、阅读协调器及私有保存。模型可见共同方法/语句/AST表列，单独选择具体问题及首批完整单元；候选入口不自动加入模型包。READING能从null包导航起步，执行有限反查、字面分页、读回、保留/移出；无实际正文不能READY。私有决策写v3、输入/响应v2；EXTRACT/REVIEW合同不变，没有WRITE。

### 零模型验证

- 明确运行10个直接ontology测试类：**49项，0 failures/errors/skips**。限定24个Java文件的Spotless apply/check通过，git diff --check通过；没有运行整仓测试或客户工具。
- 真实statementRefs对象、databaseId变体、空AST、分页、实际Provider请求、8候选仅读2单元、零正文查询到读回、非法引用、末轮观察及超限Q1不阻断Q2均有直接测试。
- 独立审查发现搜索offset超过total会产生负的未读数量，以及首批包超限缺题目结果；两个有效RED后最小修复、最终GREEN和只读复核通过。
- 完整viewId重复使固定R4零模型测量第2页封套达160,682字节。仅改为13字节模型viewId，私有完整身份/映射/指纹仍保存；六页零模型测量为110,101、113,400、116,606、113,386、114,532、48,665字节，全部低于160,000。这组使用probe scope；P2g中性scope首请求实测110,179字节，不能把测量值逐项冒充实际请求值。封套含Prompt、Provider Schema及20,000字节输出余量，**不是token容量证明**。
- 本轮P0重开仍是339入口/40,843次单元使用/15,252个去重内容，最大单元159,204字节；未改变原始材料。空SURVEY响应的PRIORITIZE测量不代表真实调查聚合一定可容纳。

### 实际执行边界及状态

原始R4、同源R0身份不变；本次模型实际只接R4单元，没有DDL输入。中性scope不含评分目标或人工选材；P1/P2旧响应和片段不复用。私有p2g配置已冻结为Luna/high、24请求、2题、4轮READING/题、最多2种任务/题、70,000字节包、160,000字节封套、20,000字节输出，无自动重试。

启动时因宿主未创建journalRoot在Provider初始化前失败，manifest明确`dispatchedModelRequests=0`。该目录保留为`p2g-local-preparation-failed-output`；创建所需目录后使用同一冻结配置继续。此项是本地启动准备错误，不计为模型失败或新增语义实验。

### 实际结果与定位结论

运行：`analysis-run:2b6443c7e57ddde78ef2595164698d4eb8cae1b8d684dd1a42a4e067bbb0efa2`。独立批次实际派发6次SURVEY，6次有效响应，复用0次。六页问题数分别为7、7、5、8、8、6，共41项。系统假设及问题只是模型提出的调查方向，尚不是本体事实。

| 阶段 | 实际结果 |
| --- | --- |
| SURVEY | 6页全部保存，模型实际看到了新版导航/共同线索 |
| PRIORITIZE | 派发前抛出`ONTOLOGY_TASK_INPUT_TOO_LARGE`，没有选题模型请求 |
| READING / EXTRACT / REVIEW | 均为0次，没有阅读包或新已审定义 |
| 隐藏跨Controller目标 | 未到语义核验阶段，不能评分为通过或归因于模型推理失败 |

独立零模型诊断从原始R4重开，并scripted返回这6份实际调查响应。逐页输入和jobKey与原实验完全一致，重建的是**没有发给产品模型的PRIORITIZE输入**。诊断runner仅为捕获输入临时放宽字节检测；真实配置仍为160,000，没有新增业务调用。

| 汇总组成 | UTF-8字节 |
| --- | ---: |
| 六页投影导航 | 169,279 |
| 六份完整调查响应 | 55,213 |
| 页及外层容器 | 813 |
| 实际input合计 | 225,305 |
| Prompt / Provider Schema / 输出余量 | 838 / 1,757 / 20,000 |
| 封套合计 / 上限 / 超出 | **247,900 / 160,000 / 87,900** |

根因在程序输入组装：`OntologyDecisionRunner.prioritize`汇集每页所有问题的入口/线索引用并集，再附上对应导航投影及完整SURVEY输出。`OntologyNavigationView.project`保留被引用线索所属入口卡及相关单元元数据。41个问题合计仍留下184张卡、307条线索；没有重复复制整页，也不足以收窄全局汇总。即使去掉所有SURVEY输出，导航封套仍为192,687字节，超过上限32,687字节。

驱动墙钟532.42秒（约8分52秒，含R4重开等）；6次请求文件至响应文件的间隔分别104、90、55、65、105、75秒，合计494秒。该间隔包含Provider启动、排队、生成和响应处理，不等于纯模型推理时间。

原始记录位于本工作树`.workspace/ontology-recognition-poc-20261001/`：`p2g-output/manifest.json`、`p2g-journal/model-jobs/<运行摘要>/ontology/`。诊断结果位于`p2g-diagnostics/replay-output/capacity-report.json`及`prioritize-input.json`；后者不是已发送请求。冻结配置与驱动SHA-256分别为`28b8619ec169d9d2dfc6b3c520a00c5e1311bf4438e79ce2b823ae829bdf1494`、`d6fbe14fadd7691cc676e4cea09fcc5bdaee38071365e3736c37f49331ff7518`。

## P2g之后的最小调整与独立重验

PRIORITIZE v3只比较实际SURVEY问题、系统假设、未解决项和页范围，不附41题的导航并集；每个被选问题的原页入口、线索及完整单元句柄，只在该题的首轮READING单独投影。选题不再请求`initialReadRequests`，首轮READING从空包请求原文。原SURVEY响应、旧运行和源码证据不改写。直接覆盖测试49项全部通过，Spotless及`git diff --check`通过。

零模型重放从同一固定R4重开，逐页核对P2g六份SURVEY的实际输入、响应和jobKey完全相同，显式复用6项。新版PRIORITIZE的实际组装输入55,918字节，Prompt 900、Provider Schema 1,514、输出预留20,000，**封套78,332/160,000字节**；41个问题均保留，六页的`navigationView`均不存在。报告和输入另存于`.workspace/ontology-recognition-poc-20261001/p2h-diagnostics/replay-output/`。这是字节预检，不是token容量或语义质量证明。

| 批次 | 实际模型派发／精确复用 | 已确认结果 |
| --- | ---: | --- |
| P2h | 1／6 | 首个新PRIORITIZE尚未取得模型响应，Codex CLI在当前文件沙箱中无法写`~/.codex/state_5.sqlite`，且app-server客户端初始化被系统拒绝；完整失败尝试另存。不是模型选题或业务推理失败。 |
| P2i | 3／6 | 可写环境下PRIORITIZE成功：从41题选中`256:Q2`库存单据/明细/库存更新、`256:Q8`财务单据头/明细。第一题首轮READING请求4个真实Java方法并成功保存；第二轮模型返回实际存在的包内`J1/J4`，要求用它们作`METHOD_USES`查询。当前程序只允许导航`METHOD`线索作此查询键，因类别为`PACKET_UNIT`拒绝，未处理第二题，也没有EXTRACT/REVIEW或小样。 |

P2i失败的准确代码为`ONTOLOGY_READING_QUERY_REFERENCE_INVALID`，不是未知或编造的短编号。第二轮输入中的`J1/J4`都映射到已读取的`JAVA_METHOD`，原方法键可从包内恢复；`retainedRefs`对同一包引用已通过，失败发生在`queries[].keyRef`类别检查。当前`reading-v2.txt`只说“查询键类别必须匹配”，没有明确说已读方法的包引用不能查询同一方法的用法。需要先决定最小合同：推荐类型安全地允许**已读JAVA_METHOD包引用→METHOD_USES**（并对已读XML_STATEMENT→STATEMENT_USES作同类核对），只用已有原始方法/语句键调用Corpus反查，不新增解析器或放宽任意引用；或者明确禁止并让模型先通过字面搜索取得导航线索。两条路线不可混称已实现，须用这份实际响应做离线回归，再另行确定是否增加真实调用。

本次P2i虽证明“全局先比较问题、选后再给详细导航”能越过先前容量断点，也证明首轮确实只读了选中问题的4个单元；**并未证明**模型可以继续读完、产出正确本体或命中隐藏跨Controller目标。财务题被选中不等于已发现财务跨对象关系。两份原始批次分别保存在`p2h-output/p2h-journal`、`p2i-output/p2i-journal`；都没有被修饰为成功。正式G1门禁保持关闭，不自动重试、正式化或全仓识别。

## 已读引用的零模型重放

用户同意先详细设计，再做有界技术实验；[类型化反查合同](../modules/ontology-recognition/cross-entry-reading-contract.md)明确由Java对准确技术键建跨入口邻居，模型只审阅有限材料的业务含义。本次没有增加真实模型请求，没有重跑JDT、Vue、Mapper、Activity或过程。

直接测试先按旧实现得到预期RED：`OntologyReadingCoordinatorTest`共7项，其中已读`JAVA_METHOD→METHOD_USES`与`XML_STATEMENT→STATEMENT_USES`两项均以`ONTOLOGY_READING_QUERY_REFERENCE_INVALID`失败。窄改`OntologyDecisionRunner`的类型矩阵与`OntologyReadingCoordinator`的键还原后，新增错类型/错视图拒绝测试，并明确READING Prompt；目标三类测试共**23项，0失败、0错误、1项无关P0环境门禁跳过**。其中固定R4的P2i重放测试实际执行，没有跳过。`git diff --check`通过。目标测试命令只跑`OntologyDecisionRunnerTest,OntologyReadingCoordinatorTest,OntologyFixedCorpusProbeTest`，不代表全仓测试通过。

重放使用P2i保存的第二轮**原始响应字节**和实际R4四个`JAVA_METHOD`，先逐单元与原请求的完整内容核对，再重建相同`packetId`与同一`viewId/ref`包视图。脚本Provider第一轮只返回原响应，第二轮只作离线停止标记；产品模型调用数**0**，它不是对P2i原模型任务的精确指纹复用，也不是对历史运行状态的修改。原请求和响应的当前SHA-256分别为`6c91449e69b690e8fb669581016d853046c2d778893c2af1cb11c7fc9ce52848`和`41e269c522f1c11c2660405c4820b7cc8dc8330b46f71f6c407959e1e7ef538a`，原文件未被本实验写入。

| 离线查询 | 真实结果 | 证明与不证明 |
| --- | ---: | --- |
| P2i `J4` 已读方法→`METHOD_USES` | 总数1、显示1、可从R4重读 | 证明原始响应的类别断点已越过；没有找到别的入口 |
| P2i `J1` 已读方法→`METHOD_USES` | 总数1、显示1、可从R4重读 | 同上；不能据此声称跨Controller业务联系 |
| 独立共同方法`method:83bcea…`→`METHOD_USES` | 总数10、显示10、10个入口的单元均可从R4重读 | 证明Java可以不靠模型从准确键找到跨入口**技术邻居**；不证明这些入口是一个业务过程 |

共同方法命中的实际入口至少包括`GET /depotHead/findInOutMaterialCount`与`GET /depotItem/buyIn`；它们的入口文件和方法正文均在固定R4内。高扇出共用方法本身可能是通用查询或辅助代码，不据此生成业务对象、操作或关系。**G1仍未通过**：没有让新模型继续选择必要原文、没有EXTRACT/REVIEW、没有已审本体，也没有核实模型在小上下文下能稳定完成语义判断。正式CLI的全仓邻居文件与定向DDL投影均未实现；这次实验只证明所需的Java反查接缝可行。

## 可组装定义 P3 的当前真实结果

固定 R4/R0 和普通问题“财务记录如何关联业务单据、相关金额如何计算并影响欠款？”未变；没有复用 P1/P2i 的候选、定向材料或模型结果。P3 第一批次在模型启动前遇到本机 Codex 状态库权限错误，记1次派发尝试、0份模型响应。第二独立批次收到1份SURVEY响应，其中一处可见线索 `L125` 的 `viewId` 多抄了字符，严格引用检查拒绝；原始响应不改写。

对此增加仅适用于**单页SURVEY**的确定性校验：若短编号在本页唯一且类型正确，只校正误抄的 `viewId`，将原值和校正值写入单独的 `validation.json`；其他编号、类别及多页引用仍严格拒绝。定向测试先RED后GREEN，本体模块直接测试和静态质量检查通过。第三独立批次没有触发这条校正规则，故不能把它说成实际语义成功的原因。

第三批次 `analysis-run:3c6e4d03a8b24f0dbe30956d3f1466b9c0e4a660018e42423046f15dce60d8a9` 从原始R4独立启动，6页SURVEY与1次PRIORITIZE共派发7次请求，7次均保存有效响应。模型自主提出财务单据关联、金额汇总和欠款变化等调查问题；PRIORITIZE最终只选中`256:Q1`，任务类别仅`RELATE`。实验执行器要求OBJECT、RELATE、ACTION、ANALYTIC四类都由模型选择，故返回`EXPERIMENTAL_INCOMPLETE / ONTOLOGY_QUESTION_REQUIRED_TASKS_MISSING`，没有READING、EXTRACT、REVIEW或局部本体JSON。入口和线索尚未被完整阅读，不能评价这些问题的业务答案。三批次共派发2＋7＝9次尝试，其中第一批次未到模型生成；没有自动重试。

定位到一个任务合同冲突：当时执行器只允许选1个问题并要求其覆盖四类定义，而PRIORITIZE提示词要求将问题收窄为单个可核对问题。本次模型按后者选择关联键问题，前者因此无法通过。当前代码已改为最多选四个有来源的问题，由模型在各问题间明确分配四类任务；Java仅检查缺失或重复，不补造定义。scripted多题贯穿及第四批次的真实四类选题已通过；后者仍未完成阅读和已审定义，不能把选题当成可组装本体成功。第三批次原始manifest和逐次记录保存在本工作树忽略目录`.workspace/ontology-recognition-poc-20261001/p3-assemblable-final-output/`及对应`p3-assemblable-final-journal/`，不进入提交。

第四批次`analysis-run:f589bba6aff0a038f7972c71408f448f126474f88f4cf9460d61a0c3c7a66c2c`按具名授权精确复用第三批次的6页SURVEY，未复用PRIORITIZE。新PRIORITIZE从原调查问题中分别为OBJECT、RELATE、ACTION、ANALYTIC选择了`0:Q1`、`64:Q1`、`320:Q3`、`256:Q2`；这是任务选择，不是已审本体。第四批次仅新派发2次请求（PRIORITIZE、OBJECT首轮READING），后者要求3个完整单元，同时请求3页查询，每页`limit=50`。当前配置每轮所有分页请求合计最多25项；程序以`ONTOLOGY_READING_QUERY_LIMIT_INVALID`拒绝，未执行这些查询，也未进入EXTRACT/REVIEW。原始请求和响应保存在`p3-assemblable-continuation-journal/`，manifest状态为`EXPERIMENTAL_FAILED`，没有局部本体JSON。

本次失败暴露输入合同遗漏：当时READING请求和Prompt没有告知模型`maxPageItems=25`，所以不能把它归因于业务推理。现已将实际上限写入每轮模型输入，并说明单项和总和限制；程序的严格上限检查仍保留。定向测试先RED再GREEN，但真实模型还没有用新合同重验。迄今P3四批次累计派发1＋1＋7＋2＝11次尝试；第一批次未到模型生成。原26次累计上限尚余15次。重新选题、四类各读一次及各提取/审阅一次的理论最少调用数是1＋4＋8＝13；若四类均需两轮阅读则为17次。此前把16次说成不可避免的最小值并不准确。按用户指定只精确复用六页调查，在剩余15次内可以继续试验，但不能保证完成；Provider硬限仍须生效，额度不足时报告未完成，不追加调用。

第五批次`analysis-run:0c45edf7fe4d5ffb3aa7acd97ebe7a9dbda4d7bd476d4556400fb66974149e00`按上述选择精确复用六页调查；首次启动因新的journal目录未预建而在Provider初始化前失败，未派发请求，诊断manifest另存。补齐目录后的具名批次重新选择相同的四类问题，新派发4次：1次PRIORITIZE、OBJECT两轮READING、RELATE首轮READING。OBJECT首轮读回了已选源码，第二轮仍要求服务调用和实体定义，达到本次两轮阅读上限时保存`INCOMPLETE`，没有提取或审阅。RELATE首轮响应在`retainedRefs`中放了导航入口/语句引用，又在`readRequests`中放了入口引用；这些引用本身可见，但不属于相应字段允许的类别，程序以`ONTOLOGY_READING_RETENTION_REFERENCE_INVALID`拒绝。原始响应、OBJECT阅读观察均保存在`p3-assemblable-cap26-final-retry-journal/`，最终manifest为`EXPERIMENTAL_FAILED`，没有局部本体JSON。

此处也是输入合同不清：当时Prompt未说明`retainedRefs`只保留已有`readingPacket`单元（空包时必须为空），也未说明入口引用不能作为完整单元放进`readRequests`。已补明字段职责，并保留严格类别校验；直接测试先RED再GREEN，**尚未真实重验**。P3五批次累计派发15次，原26次上限只余11次。若仍只复用六页调查，新批次至少需要1次选题、4次阅读和8次提取/审阅，即13次；因此在既定复用方式和上限下无法完成四类已审定义。本轮如实判为未通过，不把四类选题或脚本组装成功冒充真实本体成功，也不自动扩大额度。
