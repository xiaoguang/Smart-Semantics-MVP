# 精简材料、跨段类型对应与局部图：正式验收

## 1. 判定范围

工程接线、离线验证及固定来源的有限正式链路验收已结束：两个新LINK、两个独立类型比较和零模型O3均保存终态，实际局部图连接采购入库单→采购订单→请购单。此文不把旧实验包或scripted结果当作本次业务识别结果。仅验证两段来源编号联系、类型对应及确定性发布；不验证全仓发现、完整采购生命周期、实例相等或任意小token窗口。实际请求仍较大，小窗口目标没有通过，不能用局部图成功覆盖该限制。

固定输入：

| 阶段 | 准确运行 |
| --- | --- |
| R0 | `analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7` |
| R4 | `analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744` |
| O0 | `analysis-run:63ebafa5f5b0bd3b0a461e6c4ce975f7b99cfc990e2fa67779d357aab5da0885` |

O0保持corpus-v2／projection-v3，实际Corpus身份为`ontology-corpus:3e0c4ac6e683921c9e327a6b2dcf86ac5e315011975c0cdb73c9b2337254c63e`。新执行明确选择policy-v5，未改旧O0、R4或R0版本。验收范围通过当前准确K1342、K2072定向恢复，不宣称自主选材；问题和默认Prompt没有提供采购链答案或预期对象数量。

## 2. 设计—实现—测试对应

| 实施步骤 | 实际修改及复用边界 | 直接证据 |
| --- | --- | --- |
| 0 基线 | 原分支／工作树、12份暂存PoC和旧材料保留 | 原暂存补丁SHA256未变；98份旧MD／Prompt／Schema／历史保护摘要核对 |
| 1 合同及身份 | selection-v4明确profile；stage/result/policy-v5严格读取；未准备无job | `OntologyTypeSelectionV4ContractsTest`、`OntologyLeanPublicationV5ContractsTest`、历史publication家族测试 |
| 2 精简包 | packet/model-v7复用原读取器；完整正文、调用用途和异常位置可逆；普通未选LOCATED留私有 | `OntologyLeanLinkPacketV7ContractsTest`；源正文不截断，callRows/callUses保持选定顺序 |
| 3 LINK-v2 | 联合对象／联系、程序编号、一次审阅及固定投影；未知身份／基数不阻断骨架 | `OntologyLeanJointLinkV2ContractsTest`、`OntologyLeanFormalLinkV2Test` |
| 4 TYPE_COMPARE | 已审完整身份形成候选；两端本次S及上游仅存在引用区分；显式必读也能引用 | `OntologyObjectTypeCorrespondenceContractsTest`、`OntologyFormalTypeCompareStoreContractsTest`、`OntologyObjectTypeProfileContractsTest` |
| 5 组装／图 | 执行SAME_OBJECT_TYPE而非实例相等；原关系不丢；局部2边只来自保存图，全图及视图分母可展开 | `OntologyBusinessOverviewRendererTest`；正式CLI保存及artifact重开 |
| 6 Skill | 同一四命令、准确选单和policy模板；宿主不补正文／对象答案／模型JSON | 同一Skill前向静态检查及可移植模板，不声称安装过其他Agent产品 |
| 7 贯穿／质量 | 真实Agent/store、局部拒绝后继续、预算未派发、容量失败查询；LINK对象准确供ACTION/ANALYTIC | `OntologyFormalRuntimeContractsTest`的两项policyV5直接方法；44/44及quality6通过 |
| 8 有限真实验收 | 同一O0新LINK→专用类型比较→O3；材料由正式Java准备 | 四个已审任务、四文件公开重开、离线主图3节点／2联系；实际来源和成本见§4，容量限制未关闭 |

交付前定向组`lean-formal-all-green18.log`：44项，无失败／错误／跳过，49.206秒成功；与前一GREEN17同一生产代码及测试。应用及测试Java17；质量宿主JDK26，满足21+要求。`lean-quality7.log`执行计划规定的`spotless:check verify -Pquality -DskipUTs -DskipITs`，01:51成功；未执行无关整仓测试。

## 3. 已修正的框架问题

最终独立审查的三个Important均通过直接RED→GREEN，不再派第二轮reviewer：

1. TYPE显式／必读材料已经提交，但两端sourceRefs没有纳入它，模型无法合法引用。现按准确入口用途归属相应端，越界仍拒绝。
2. 未选普通LOCATED调用仍进入限制表。现在它仅留完整私有调用目录及计数，真正未知／候选／失败／外部边界保留准确位置。
3. 容量失败只有原因码。现保存实际边界、字节及封套分项，并按准确任务重开核对，无假job。

正式查询预检还重现v5任务索引错误回退v1，只列实际job，遗漏未派发分母。新增反例后修正为完整taskOutcomes查询。未把测试夹具数组错误、命令误用相对配置路径或模型误读列为客户业务错误。

质量检查要求运行器复杂度回到既定阈值内。候选展开归已有窄TYPE组件，失败／任务处置的严格读取归`OntologySavedTaskContract`；不增加平台或放宽PMD。不可变packet的两条SpotBugs例外只限`Prepared.packet`字段/accessor；额外测试修改返回JSON／字节副本，冻结输入、模型投影和绑定均不变。

## 4. 有限真实运行、成本和原文核验

O1、O2、O3均已结束。所有实际请求、原稿／审阅、来源和成本来自本次正式保存记录，没有安装PoC包或修改模型JSON。本批累计12次确认开始／结束的请求：首次拒绝O1四次、新O1四次、O2四次；未知结果零，O3和全部查询零模型。没有自动重试、模型／账户回退或第三轮修稿。

### 4.1 LINK与来源编号联系

首次O1 `ee29b2cc76f50783910edde7531377685ff497a5bb5b569d65c476ff1bfd96f6`，4次确认开始／结束，2任务REJECTED，PARTIAL／FAILED。A审阅把已读S填到只能使用待读U的missingUnitRefs；B首稿使用展示name而非objectKey，审阅修正键后删剩1条边，仍保留linkIndexes=4。两个任务均结束，第二题不因第一题拒绝而未执行。旧原稿、审阅及处置保持原样。

聚焦O1 `4a485541fe6c77e4a1beddd65313023a92ec8573a1317f329e5889b3a976e818`，4次确认开始／结束，0未知，2任务REVIEWED，COMPLETED／FINISHED。默认Prompt及问题只询问准确K锚点的来源编号联系，没有给请购／订单／入库标准答案。A识别2对象／1联系，B识别10对象／10联系；B仍按材料展开范围提出额外业务种类，而非只输出一条采购联系。结构通过不等于全部11条业务联系已经人工核验。

主要联系按实际原文核对：A的采购订单引用请购单，页面onSearchLinkApply选择请购单，LinkBillList.handleOk取得record.number，PurchaseOrderModal.linkBillListOk将linkNumber写入linkApply；DepotItemService.saveDetials读取getLinkApply并在采购订单分支处理关联单据。B的采购入库单引用采购订单，PurchaseInModal.onSearchLinkNumber选择采购订单，handleOk取得编号，PurchaseInModal.linkBillListOk写入linkNumber，后台读取getLinkNumber。取得编号的是确认选择的handleOk，不是仅打开窗口的函数。跨段采购订单类型由下面O2另行审阅，不因名称相同直接拼接。

对保存的两个v7私有包进行只读重构：其modelInput与实际EXTRACT逐字节一致，REVIEW的readingPacket与EXTRACT相等；完整正文及用途未截断。另按R0的manifest、实际blob SHA256及UTF16范围核对74个Java／前端正文（A16、B58），全部相等。诊断脚本仅比较已有记录，没有为生产准备材料、补答案或调用Provider。

### 4.2 专用类型比较与确定性发布

O2 `analysis-run:c9ab4eff5f7c054b1d500892f2f3bf9cfe2f7450203206cc7f854c5def1a2990`，两个候选均REVIEWED，COMPLETED／FINISHED，四次确认开始／结束、零未知。候选由准确已审来源及保存的技术信息形成；没有人工添加对应决定。

| 候选 | 实际最终决定 | 两端实际依据 | 组装行为 |
| --- | --- | --- | --- |
| 两段中的采购订单 | SAME_OBJECT_TYPE | PurchaseOrderModal的CGDD及type/subType用途，与另一段选择窗口传入的采购订单类型及编号用途；两端本次S原文均入模 | 执行OBJECT_TYPE_EQUIVALENCE，统一类型端点，不声明同一笔订单、唯一性或基数 |
| 请购单与销售订单 | DISTINCT | 完整共享选择组件中不同的subType及两端页面用途 | 不合并，保存原生决定；没有改写成业务无关 |

两个比较包各78个完整选择单元／186份用途，54份Java／前端完整正文。按同R0的摘要及UTF16范围另核对108个正文出现次数，全部相等；加上O1共182个出现次数，包含跨包重复，不等于182个不同文件。实际EXTRACT分别288,195／288,224字节，REVIEW分别289,802／289,059字节；每请求另预留65,536输出字节。私有包恢复的EXTRACT及REVIEW投影一致，未把上游仅存在的引用冒充本次已读S。这些包没有选中XML／SQL正文，不能宣称本次客户运行核验了全部SQL格式；对应机械完整性由直接fixture覆盖。

O3 `analysis-run:adaa3c4786ecfb5561e3791efe9b6ed28a59581837c285c6cda110db13e5b5c2`，COMPLETED／FINISHED，四项声明任务均REVIEWED，零组装问题、零模型。保存12份原对象定义、11个canonical对象类型和11条联系；ACTION／ANALYTIC为NOT_REQUESTED，不是已识别为零。coverage的COMPLETE只指本次四项声明义务；semanticExhaustiveness仍为UNDETERMINED，全输入分母339入口、91前端请求、0DDL没有缩成已读范围。

| 正式事实文件 | 公开artifact查询字节 |
| --- | ---: |
| ontology.json | 103,021 |
| ontology-coverage.json | 141,270 |
| ontology-sources.jsonl | 54,523 |
| ontology-review.json | 26,587 |
| 合计 | 325,401 |

四文件通过正式artifact查询导出，与实际步骤安装逐字节相同；同一结果同时保留模块和步骤发布。inspect恢复准确上游、FINISHED及零模型计数。派生business-overview.html约3.84MB，主要包含框架自带离线Mermaid，不是模型阅读包或第五个事实文件。把四文件及HTML复制到新目录后再离线渲染，主图仍3节点／2边、页面错误0、HTTP请求0；这验证派生视图不依赖原导出路径，不声称所有原文查询脱离上游也能执行。首次沙箱浏览器启动的macOS IPC权限错误与GitHub网络限制经具名沙箱权限重试解决，没有模型重试或代码绕过。

实际保存的局部视图为：

```text
采购入库单 ──采购入库单引用采购订单──▶ 采购订单
采购订单   ──采购订单引用请购单──────▶ 请购单
```

箭头沿保存的引用方向，不倒转成办理顺序。浏览器加载公开artifact派生HTML，阻断全部HTTP网络请求：主图3节点／2边，节点为采购入库单、采购订单、请购单；页面错误0、网络请求0。全图保留11个canonical对象及11条联系，支撑／主业务／待确认视图仍可展开；Mermaid的空子图占位不算业务对象。其余九条联系在模型保存结果中保留，但本次人工语义核验重点为上述两条，不把全部边的结构合法当成人工认证。

### 4.3 成本、压缩口径和剩余限制

| 成本口径 | A：K1342 | B：K2072 |
| --- | ---: | ---: |
| 完整选择单元／单元用途 | 19／51 | 82／203 |
| Java／前端完整正文UTF8字节 | 38,951 | 107,387 |
| 私有调用用途／选定调用用途 | 788／6 | 926／13 |
| 模型readingPacket字节 | 209,017 | 508,987 |
| EXTRACT封套：input＋Prompt＋适配Schema | 220,000 | 519,970 |
| REVIEW封套：含实际首稿 | 222,884 | 535,892 |
| 每请求输出字节预留／输出token配置 | 65,536／4,096 | 65,536／4,096 |
| EXTRACT容量核对总字节 | 285,536 | 585,506 |
| REVIEW容量核对总字节 | 288,420 | 601,428 |

字节、输出预留与token分别计量；未取得可靠token验证，不宣称4k／8k兼容。当前输入仍偏大：A的594条限制行108,030字节，含422处NAVIGATION_CONFLICT、166处EXTERNAL和6处CANDIDATES；这些是已读方法的准确位置观察，不把它们当确定业务连接。B有666条限制行。完整正文／用途已提交，不能用隐藏这些观察宣布更小。

同选择便利字段对照：只把已移出模型的Java AST便利字段还原，A阅读包234,962→209,017（约11.04%），B538,614→508,987（约5.50%）；所有原文与用途相同。**这不是旧v6生产运行的总压缩率。** 使用旧v6序列化／旧调用准入的反事实包分别188,260／491,501，新包反而更大；v7还明确保留旧规则未纳入的正常外部边界等观察，投影范围并不完全相同。不能把便利字段节省包装为整体请求更小或小窗口已通过。

首次O1准备阶段主线程样本显示正在`OntologyEvidenceCorpus.withPreparedSourceBodies → uniquelyLocatedDeclaration → declarationDisplay`恢复已保存的导航位置；聚焦O1第234秒样本显示在ReadingPacket构造中反复CanonicalJsonCodec序列化前缀包，逐组核对容量。不是JDT重跑或模型等待。性能剩余项单列，不能藏在模型耗时中。

## 5. 工程裁决与保留项

依发生顺序保留本次裁决及代价：

- 以模块规定的scoped progress为唯一执行ledger，日志在ignored目录，不另造技能账本；代价是brief／RED-GREEN由ledger人工核对，而非技能脚本生成。
- 按用户计划验收后集中提交，原暂存PoC不进入本轮；代价是须精确选取路径，而非直接提交暂存区。
- 只运行新增和直接覆盖测试，优先于技能的全套建议；未覆盖的整仓行为不能获本轮认证。
- O2内部保留RELATE kind，profile及模型任务明确TYPE_COMPARE，不扩公共TaskKind；查询必须显示profile，否则容易误当通用关系推断。
- TYPE取材绑定pair而不是伪造K，普通已审OBJECT允许无K；须分别验证LINK与TYPE规则的严格准入。
- 独立reviewer未判断实际业务语义／客户O0、本地构建和原暂存PoC；实际限定运行及原文核验承担业务判断，PoC保留，不把结构通过当业务正确。
- 首次正式两题拒绝后，不重发原任务；按已授权同范围调整默认Prompt和问题范围，新建一次有限O1。最终键、零基序号、未读U与已读S边界，以及不把有来源联系的业务种类压成一个技术类，均为通用约束，不给采购链答案。代价是最多新增4次内容请求，首次失败必须保留；不能据此无限调稿。

过程偏差：候选组件曾提前写入，未等其直接测试RED；不能把此前Schema RED冒充该组件RED。后续直接反例及回归结果如实记录，不追认完全符合测试先行。

Deferred minor：局部图用已审关系name作短标签，而不是自动生成机制短句。泛称可能不充分解释联系，完整机制／条件在详情；没有为修饰图而改业务事实。此项不以新增模型或缩写系统处理。

## 6. 交付和保护

四正式命令保持原名；无新分析CLI。新增默认Prompt、policy-v5及selection-v4示例，代码／测试／设计／同一Skill同批交付。原PoC代码、运行数据、本机配置、凭据和其他progress不混入提交。旧MD、Activity、过程、九章及原R0/R4未修改；不得自动合入main。

本体仍标DRAFT_REVIEWABLE，指标NOT_EXECUTABLE；backlog32和28的长期小窗口约束不整体关闭。实际材料、来源映射、模型请求、原稿、审阅、四文件、公开HTML和截图仅保存于ignored验收目录，不进入提交。98份有效保护摘要全部一致；清单中14个非摘要标题另行排除，不把格式警告视作缺失文件。原12份暂存PoC补丁SHA256仍为040d0633ede7dfeaf437427bcf122623424c6956670792442e2b704e11fb42ee，未改写。交付分支采用现有[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，不自动合入main；本轮只选择相关实现／测试／设计／Skill路径，原PoC和无关进度不参与提交。
