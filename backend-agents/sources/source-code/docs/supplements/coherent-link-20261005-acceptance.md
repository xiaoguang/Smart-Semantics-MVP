# 连贯材料与联合LINK：当前验收结论

状态：工程门禁和本轮有限真实运行已完成，最终有效部分已经保存、重开和离线显示；模型质量与小窗口验收未全面通过。采购三对象的来源编号联系已经有准确的新核对，不宣称完整采购生命周期或全仓本体。

## 范围

使用固定R4 `analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744` 与同源R0 `analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。新正式O0为 `analysis-run:63ebafa5f5b0bd3b0a461e6c4ce975f7b99cfc990e2fa67779d357aab5da0885`，完成准备时零模型调用。

Java从保存的K锚点形成一跳材料；宿主不补源码或业务答案。本次锚点是具名定向恢复，不宣称自主全仓发现。没有重跑技术取证、JDT、Maven或SQL执行，也没有生成Activity或业务过程。

## 材料实测

| 同一选材 | 完整源码字节 | 原或中间模型投影字节 | 最终模型投影字节 | 私有未读目录项 |
| --- | ---: | ---: | ---: | ---: |
| K1342 | 38,951 | 原始799,724 | 188,260 | 3,104 |
| K2072 | 107,387 | 中间字典表达651,002 | 491,501 | 5,648 |

两份最终投影解码后，源码单元、调用行、调用用途、调用限制、入口上下文及必需未读项的摘要均与对应原投影相等。完整私有未读目录按自身摘要恢复；不是靠删源码或删除页面实例减少字节。

仅完整字段相等的前端观察共享F行；各页面保留自己的实例、用途、条件、参数差异和有序引用。必需未读项仍完整入模，其他未读导航仅展示精确覆盖组，不作可引用的业务证据。

上述字节是材料投影，不是token数。当前实际运行8702的完整请求成本如下（UTF-8 JSON字节；Base64存储包装不计入）：

|任务/阶段|untrustedInput|Prompt文本|响应Schema|完整请求JSON|
|---|---:|---:|---:|---:|
|A EXTRACT|188,707|1,992|12,669|203,595|
|A REVIEW|220,252|1,846|12,653|234,975|
|B EXTRACT|491,948|1,992|12,669|506,836|
|B REVIEW|518,439|1,846|12,653|533,162|

实际REVIEW含真实草稿和候选诊断，不只计算冻结原文。两项满足本次1,048,576字节的显式上限，没有提高配置；第二包仍偏大，尚未证明适配小上下文或任何4k/8k窗口。容量合规不等于小窗口适配。

## 工程检查

本轮9步中0–7的工程及有限运行均已执行。步骤7的结果是部分验收，不把2项被拒任务或大输入改写为全绿。步骤8交付范围为本文及相关改动，通过现有PR35提交；运行数据、本机配置、凭据及原12项暂存PoC不纳入提交，旧MD路径保留。

### 设计—实现—直接测试对应

|设计边界|当前实现|直接测试|
|---|---|---|
|准确K的一跳技术扩展，完整XML依赖与页面用途|OntologyCoherentLinkBundle、OntologyReadingPacket|OntologyCoherentLinkBundleContractsTest、OntologyCoherentIncomingReferenceContractsTest、OntologyCoherentXmlDependencyBundleContractsTest、OntologyCoherentPersistenceGroupContractsTest|
|请求内批量字面检索，不改变单词搜索/两个阶段|OntologyEvidenceCorpus.searchLiteralBatch、Bundle两个调用点|OntologyLiteralBatchSearchContractsTest；固定K1342/K2072四份输出与旧基线cmp一致|
|页面物理单元的调用内惰性解析，首次失败优先级不变|OntologyEvidenceCorpus.frontendContextSources|OntologyFrontendContextParseReuseContractsTest；计数RED→GREEN，空范围及非法首项零读取|
|精确去重及私有未读目录恢复，不改正文|OntologyFrontendObservationProjection、OntologyUnreadCandidateProjection|同名ContractsTest；真实两包六类摘要相等核验|
|联合对象/联系端点闭合，一次REVIEW|OntologyTypedTaskRunner、OntologyTypedDefinitionValidator|OntologyJointLinkResponseContractsTest、OntologyFormalJointLinkTaskContractsTest、OntologyLinkPromptContractsTest|
|明确对象taskIds、完整审阅身份、生产版本准入|OntologySavedTaskContract、OntologyAnalysisConfiguredRuntime|OntologySelectedObjectSourceContractsTest、OntologyCoherentPublicationFamilyContractsTest、OntologyFormalRuntimeContractsTest限定选择器|
|正式LINK保存重开及双层覆盖|OntologyJobResultStore、OntologyScopedAssembler|OntologyFormalJointLinkTaskContractsTest、OntologyFormalScopedAssemblyContractsTest|
|LINK文本修正不被强制转换，旧typed映射不变|OntologyScopedAssembler.reviewDocument|jointReviewPublishesTextCorrectionsWithoutCastingThemToTypedCorrectionObjects准确RED→GREEN，连同旧组装18项通过|
|DISCOVERY/PRIORITIZE新旧精确分派，不注入客户答案|OntologyPrioritySelection、默认Prompt及原调查接线|OntologyDiscoveryLinkPrioritizeContractsTest、OntologyFormalJointLinkPrioritizeContractsTest|

上表是机械实现及直接门禁，不代替下列真实业务/容量结论；未完成项仍保持未完成状态。

- 最新紧凑表达与正式O1→O2→O3重开门禁：24项直接测试通过。
- 对象准确来源、发布策略族、完整审阅身份对应：18项直接回归通过。
- 最终质量检查：Spotless、SpotBugs和PMD通过；SpotBugs零发现。质量阶段跳过单元与集成测试，没有重复运行无关套件。
- 批量检索修订后quality-11通过（01:38）；页面解析复用修正后最终同代码quality-12通过（01:34）。中间quality-10的键遍历性能告警已改为entrySet，未提高阈值或新增抑制。三项批量直接测试通过（45.768秒），包含跨单元种类/入口顺序和旧单查询等价；解析复用及直接覆盖14项通过（48.769秒）。
- 只读复核未发现新的可复现目录丢失、页面观察误合并或历史格式接线缺陷。
- 最终只读整体实现复核覆盖v6一跳选材、未读目录摘要及精确分组恢复、F观察与页面有序用途、准确taskIds及完整审阅身份、版本/失败分母/发布和图安全；未发现可复现Critical/Important。真实模型业务质量和容量结论不由代码复核代替。
- 98份受保护旧MD代码/Prompt摘要复核通过，`more-findings.md`与基线一致；原12项暂存实验文件未改动暂存归属。

这些门禁说明材料与机械接线，不证明模型业务含义正确。

## 真实识别处置

首次正式O1 `analysis-run:53677018cc705492b03260086f05e5aecc547b229a78e9f09fcefb6abef97c8d` 已结束为PARTIAL/FAILED，保存一个Provider派发尝试和两个任务的完整处置。登录适配器在本地沙箱中无法初始化状态数据库及应用客户端，返回 `Operation not permitted`；没有取得模型答案。运行保留UNKNOWN观察，第二个任务没有派发，未留下等待正确答案的RUNNING状态。

确认本地进程结束并检查原始失败记录后，在用户已授权的同范围允许环境中创建新运行 `analysis-run:0fecd3b705a3c1f38577b8cb3416d4744e072a927366271d06608b549536d170`。模型、账户、固定来源和容量不变，不覆盖旧报告。该运行已结束：4次请求均确认开始和结束，未知请求为0；第一项REVIEWED，第二项REJECTED。

第一项识别了来源编号联系，但把请购和订单等种类放入同一个泛化DepotHead对象，以自关联表达连接，尚未满足可读业务骨架验收。第二项最终稿仍在`propertyRef`填入字段名，并把已经提供的S正文编号填入`missingUnitRefs`，被严格校验拒绝。不能把它们修成猜测的业务答案再发布。

直接检查发现两项框架合同遗漏：默认LINK提示词缺少明确的业务种类区分，响应Schema允许非空propertyRef而原校验器拒绝。3项直接测试准确RED，最小修正只收窄LINK Schema并明确种类、引用和已读S/未读U。24项直接回归及quality-5通过（01:49），旧typed属性引用不变。后续真实8702、聚焦O2及最终O3结果见下文；不保证模型必然守约或业务判断正确。

O2准确对应、财务关系、统计细化及新的页面建立端核对已有下述正式结果；调拨对照被拒绝，最终O3保存有效部分及完整失败分母，零模型。

修正后新运行`analysis-run:8702e89c5e8fb1707c7950ce3dbc430625ad3099f8875dbcac99a4eb5506af25`已结束，4次请求确认开始及结束，未知0，两项均REVIEWED。第一项最终保留独立Purchase Request及Purchase Order，以及linkApply的CONFIRMED联系；明细级联系仍为INFERRED。第二项最终在同一结果中保留Purchase Receipt→Purchase Order（linkNumber）与Purchase Order→Purchase Requisition（linkApply），候选的关系数组引用由原定一次REVIEW修正为有效引用，没有人工改稿或第三次调用。

运行整体仍为PARTIAL/FAILED：收尾组装将LINK的文本corrections强制转换成typed对象，触发ClassCastException。零模型直接测试准确复现同一行852的异常；最小修正按LINK任务类型保留文本，旧typed修正与引用映射不变。18项直接回归通过（联合任务8、原组装10）；同代码quality-6通过，01:55，Spotless/SpotBugs/PMD通过、UT/IT跳过。已安装O1与两份私有审阅原文保持不变；新正式O3零模型发布正在准确重开实际结果，不把两稿有效等同于正式业务图已交付。

## 尚未通过的结论

正式零模型O3 `analysis-run:0aa7f81814d12d6af8a3a08440704bfaad41b331790361fb1dfc5865e783837f`已COMPLETED/FINISHED，确认开始/结束/未知/预留请求均0。四文件安装成功，业务图通过正式artifact查询取得，HTML为3,843,127字节，SHA256 `148b122e36c822086d76ae2b522d9643796d27f76944afab13e83c15dc2f2258`。保存20份对象定义、21条联系；两项LINK的OBJECT/RELATION义务完整，ACTION/ANALYTIC为NOT_REQUESTED。第二项内的采购三对象/两联系已安装保存；跨任务重复对象未自动合并，准确O2身份审阅正在进行。原O1的PARTIAL/FAILED历史不改写。HTML取得不等于浏览器SVG布局已经验收。

- 请购、采购订单、采购入库三个对象及两条来源联系已出现在第二项实际有效审阅结果，并经过正式O3安装及业务图查询；跨任务重复对象仍须显式身份对应，不能按名字合并。
- 财务/统计细化、页面建立端和最终离线SVG已有下述正式结果；调拨对照拒绝，不能称业务质量全绿。
- 小上下文适配、全仓自主发现、完整采购生命周期及其他项目同等质量：未证明。

采购入库→订单的模型引用仍有错误：机制引用S33/U142、S34/U143对应PurchaseBackModal；准确PurchaseIn建立端S39/U149、S40/U150已在选中材料中。结构校验不能辨认业务引用是否恰当，本项不能宣称业务质量通过，原始审阅稿不改写。

O2 `analysis-run:75bec9f738752622e4e289499c9ce01d59f49300f59e6df1539c2907143fb072` 已结束：4轮阅读仅查询、未读齐必需正文，UNPROCESSED/ONTOLOGY_READING_REQUIRED_UNIT_UNREAD，未执行关系提取/审阅。下一次采用正式支持的明确选择核验对应，不把定向选择冒称自主调查成功。

浏览器离线生成SVG成功，未访问网络；SVG文本标签模式曾将编码标点显示为字面实体。原生Mermaid对照验证strict HTML标签模式正确解码，恶意标签文本未形成script/img/iframe/link元素。针对该配置的直接测试准确RED，最小修正后图/联合任务16项通过，quality-7通过（01:42，UT/IT跳过）。修正后正式artifact重新派生的实际图经离线浏览器验证，连字符/引号正常，注入元素0；strict及原标签编码保持，不修改对象名称或关系内容。日志`.workspace/coherent-link-browser-label-mode-final.log`。S39/S40均在实际EXTRACT请求的units中，正文投影分别229及3,637字节，页面路径和来源映射在程序侧保留；不是仅保存私有正文却未入模。

后续只能根据实际保存结果更新这些结论，不从参考图、PoC答案或对象名称补齐联系。

调拨对照准备阶段观察到重复全量规范化：具名本地进程84677运行9分28秒时仍在preflightIdentify，主线程位于searchLiteral/CanonicalJsonCodec；未创建该次模型任务记录或获得正式结果。准确核对仍在preflight后，发送TERM结束该本地进程（143）；不把它写成模型查询失败，也不伪造运行回执。请求内批量字面检索已最小实施，旧public单查询接口不变，两阶段查询集仍分别计算。缺方法直接RED后，16项批量/连贯包/阅读定向回归通过；固定R4的K1342/K2072两份决策及模型输入与保留基线经四次cmp逐字相等（188,260/491,501字节）。日志`.workspace/coherent-link-batch-equivalence.log`。采样RSS约2.1GiB，非峰值证明；批量同时持有多词命中，有资源风险，不宣称通用性能达标。线程栈只能证明该采样位置存在重复解析，不声称全部耗时均由它造成。

## 骨架细化的当前正式材料核验

### 最终发布与实际图

O3 `analysis-run:4c69493e2ed49705ed821e8ab9c5c9a9c6cdad194a08b9e7db7dca44671902da`以明确选择的全部上游发布，零模型。9项REVIEWED、2项REJECTED均保留；运行PARTIAL/FAILED、覆盖INCOMPLETE、语义穷尽性UNDETERMINED，不留下RUNNING，也不删除失败分母。原统计坏稿和调拨坏稿没有定义进入有效本体；新的统计成功不抹去旧失败义务。

实际保存32份对象定义（29个canonical对象引用，3项显式同一对象对应）、28联系、7操作、3规则、6维度、2度量和3指标。它们不是32种已经业务去重完成的对象，也不是28个办理步骤。指标仍NOT_EXECUTABLE，本体DRAFT_REVIEWABLE。最终对象引用中，Purchase receipt→Purchase Order通过linkNumber相连，Purchase Order→Purchase Request通过linkApply相连；跨题订单/请购的统一来自实际已审SAME_OBJECT，不按名字合并。未核查完整采购、库存、付款生命周期。

|正式文件|实际字节|SHA256|
|---|---:|---|
|ontology.json|459,953|ea74bc892d6f95e76e15c685998cf2a1e7e1f0a9b29b59545e10190b06ffb2b1|
|ontology-coverage.json|85,239|8cecd0eb061428a1ceae545e4990eb65a51f454aa00d1a09dd83c6b4e1f4e7e9|
|ontology-sources.jsonl|105,372|d1b0bde4240aa3a798fe12ad99569c9533eaa804ed671d9121bfc4c4e985b752|
|ontology-review.json|52,076|13fc4d8fe2818be94b8913e94b76ae5fa5eeadd868aa0dc8bbd89e2432c88c5f|

四文件通过正式artifact新进程重开，与原安装文件四次cmp逐字相等。只读业务HTML为4,065,126字节，SHA256 `280a4c56545d01ca7e7eb8bd47860c8095b9a3b090235457141786e50da93248`；离线浏览器阻断HTTP，生成1个SVG、页面错误0。截图SHA256 `83afc8ebf144c8f3d4f9d57f927b9a8104b9f7f1801797c28ef25ae4371fd710`。HTML和截图位于忽略目录`.workspace/coherent-link-final-result/`，是派生视图，不是第五份业务真相。实际视觉核查仍发现长标签、英中文混排及复杂总图拥挤；不能说已达到参考图的简洁度。没有重命名模型对象或删关系伪造简洁结果。

当前剩余：小窗口尚未证明；原联合稿的入库联系仍保留历史误引，但新聚焦关系具备正确PurchaseIn建立端；统计身份维度及无法定位的missingUnitRefs仍属模型/旧合同质量限制；调拨未通过。自主阅读、全仓发现、跨项目同等质量没有获证明。第28项长期材料约束和第32项未通过子项继续保留。

本轮临时开发进度`coherent-link-implementation.md`、`coherent-link-identity-review-test.md`、`coherent-link-response-tests.md`的已解决交接已经纳入本文和owner设计，按模块收尾规则移除；不是删除原始证据、模型两稿、日志或历史业务材料。原12项暂存PoC的内容/归属保持，暂存摘要为`040d0633ede7dfeaf437427bcf122623424c6956670792442e2b704e11fb42ee`；98份旧代码/Prompt摘要保护通过。PR35是既有交付入口，不把OPEN称为main已合入。

采购页面建立端定向O2 `analysis-run:71280b8be3d47f7718552aac079e0d8c3a137eee5f721aec1c61890cc034a509`已COMPLETED/FINISHED，2次请求确认结束、未知0，RELATE已审。实际返回一条采购入库单B4→采购订单B3的linkNumber联系；S12/U149和S13/U150均来自同源`PurchaseInModal.vue`完整函数，分别保留选择`采购订单`及回调写入`linkNumber`。已有真实对象目录恢复为本题9对象，没有手工补对象或源码。14单元；保存的准备请求为189,833/194,163字节，含完整原文及实际草稿，仍不能称适配任意小窗口。原错误页面引用的历史LINK稿不改写，新联系单独保留准确来源；全生命周期、数量规则和基数仍未由这项验证证明。

调拨对照运行`analysis-run:900baaa851fb38025a507d012f5255490ce60281118eda110a8411261a5e380a`已经PARTIAL/FAILED，两次请求确认结束、未知0。实际审阅只有`objectKey=warehouse`，关系却引用`depotItem.depotId`和`depotItem.anotherDepotId`两个未定义端点，严格校验拒绝当前任务。143单元的实际EXTRACT请求为792,686字节，不能称为小窗口材料；不手工替换端点、不执行第三次修稿。约26分钟本地准备中采样观察到来源重开、控制索引和页面匹配的重复解析，不能将全部耗时归因于单一位置。

页面匹配最小优化限定一次调用内：在原内层首次读取位置惰性保存已解析的同一物理单元。直接计数测试先确认旧循环每个单元读取2次而非1次（3项中1项预期失败）；最小修正后新增及直接覆盖14项通过（48.769秒），空sourceUnits及非法首项仍零读取。没有跨请求可变缓存、没有新索引，也未改变匹配顺序或失败优先级。只读增量复核没有可复现Important/Critical；同源K1342/K2072两份决策及模型输入再次四份cmp逐字相等（`.workspace/coherent-link-context-equivalence.log`）。同代码quality-12通过（01:34），Spotless/SpotBugs/PMD通过、跳过UT/IT；不宣称整体准备性能已经达标。

运行`analysis-run:184d4cf989582e8673e8e9ec4956866b4afb101b5614995fcdf92e68b8ad86d7`已COMPLETED/FINISHED，两OBJECT均REVIEWED，4次请求确认开始/结束、未知0。财务三对象为AccountHead、AccountItem、DepotHead；统计有六种业务单据与用户/角色/配置共九对象。统计OBJECT实际EXTRACT和REVIEW都包含S4 `parseHomePriceByLimit`完整694字节，SHA256均为`031e3a5d97262eee964294dec50096e37a1b1b22f97e7a875dea2ffc72d071fb`。两次保留`doesNotConfirmCallEdge=true`和未确认导航观察。完整请求分别约79,219/114,228字节（含运行阶段元数据）；不是把文件存在当作已入模，也没有用完整正文将可疑调用升级为确定调用。

细化O1 `analysis-run:81441e8d892f379c06d586c343e8108beeafe6ffe081a37b68f2703be50a4cf7`已PARTIAL/FAILED。财务ACTION REVIEWED，7操作/3规则，仅收到本题三对象，准备请求151,454/175,854字节。实际effect保留SQL按bill_id分组后取返回金额绝对值，不是逐原始明细先取绝对值再求和。统计ANALYTIC REJECTED：度量误用M1–M6、指标误用V1–V3，严格拒绝，4次结束、未知0；辅助正文实际在两次请求中。后续D/V/M提示修正、直接回归及新统计运行见下文，不改编号或旧坏稿。

同范围明确选材O2 `analysis-run:11d17d7583d5e4b52d4c71eb9503ae33de75f9d2cc034fa1b3f05ffd8e9fc7b3`已COMPLETED，两RELATE均REVIEWED。对象对应稿保存3项SAME_OBJECT（两份LINK的采购订单、请购单、销售订单）及4条来源编号关系；财务稿保存明细到主表、明细到业务单据两关系。它们是结构/引用通过的实际结果，不把明确选择冒称自主选材，也不自动消除此前入库关系的错误来源引用。

统计机械编号提示修正：新增直接测试准确失败（1项，34.802秒），两份v2 Prompt逐项写明D维度/V度量/M指标及最终componentMeasureRefs闭合，27项直接回归通过（35.028秒），quality-8通过（01:50）。增量审查发现“只允许当前V”文字过窄，和既有允许已审度量B的合同冲突；新增B条款测试准确RED（29.943秒），最小文案修正后23项Prompt/typed/跨任务组装直接回归通过（36.923秒），静态复核该冲突已消除。Schema与严格引用检查不变，不改号、不改历史坏稿。该修正减少机械误填风险，不保证模型不再出错。

统计独立重验 `analysis-run:a58cc79255d03733c3d03c4a3d1c9c60bbe1aba0031d1b50474e2185f4f7c18e` 已COMPLETED/FINISHED，2次开始/结束、未知0。实际返回6维度、2度量、3指标，D/V/M及定义引用检查通过。实际模型目录仅本题9对象，13份完整单元，S4辅助正文694字节及未确认调用限制保留。但documentIdentity仍被列为维度，应作为模型分类质量问题核验；结构通过不能把它当作正确业务维度。该运行保存的Prompt为当时版本，后来允许已审度量B的文字不追写历史。本题当时目录无已审度量B，没有受到该过窄条款的限制。

统计未知项另有`missingUnitRefs=[V1/V2]`，这些不是可定位的缺失源码单元。只读复核确认旧typed-v3/v4只校验evidenceRefs，missingUnitRefs为辅助字符串数组；装配器不会将其写成来源或确定业务引用。因此不拿它们作为材料缺项或已读证据。新LINK严格只接受实际展示的未读U，该约束不追溯套到旧typed格式。后续如收紧typed未知项定位须单独设计前向合同及历史重开策略，不能原地改历史验证器、把此模型瑕疵包装成有效定位。
