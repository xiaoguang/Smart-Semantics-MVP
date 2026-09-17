# 当前实现与本次待修改清单

核对日期：2026-09-16。目标以[系统认识与三阶段成稿](business-reasoning-and-writing.md)为准。表内“已有”来自实际代码或保存产物；“研究实测”不等于生产接线完成。本文件不重新估算旧计划，也不以文档完成代表功能完成。[本次三例实际验收](three-case-acceptance-result-20260916.md)：步骤0–6完成，步骤7未通过；三例批次已FAILED并排空，不再有正在运行的产品请求。

当前用户反馈：目前展示的文档已经可以看懂，业务也联系起来了。该阅读与衔接水平得到认可；已知容量、内部编号和规则准确性问题记录在上述验收文件中，留待优化讨论。当前只做记录，不修改代码/Prompt/设计合同，不新增模型调用或自动重做三例。

## 1. 已有能力直接复用

代码根目录为 `src/main/java/org/sourceanalysis/app/`。

| 能力 | 真实位置 | 状态与复用方式 |
| --- | --- | --- |
| 冻结 Java/XML/Vue 目录、范围与字面量读取 | `analysis/knowledge/FrozenProcessSourceCorpus.java`；`DefaultBusinessProcessDiscovery.FrozenCorpus` | 已实现，使用保存的文本；不扫描当前 checkout |
| 完整 Activity/M10/statement 重开 | `DefaultBusinessProcessDiscovery` 的 corpus/reopen 接线 | 已实现，326条数据继续复用，框架数量不写死 |
| 旧目录重开、全局选材、每候选阅读检查 | 同类 `materialSelectionInput`、`prepareReadingPackets`、`readingCheckInput` | 已实现同次请求读冻结README、自由systemAssessment与候选investigationQuestions；CHECK v4执行明确R保留集/一次补读及首读预览/已有来源定位导航 |
| 完整阅读包进入三阶段过程 | 同类 `packetInput`、`reconstructRaw` | 已实现 DRAFT→WRITE→RULE_REVIEW；WRITE仅收完整实际DRAFT，最后核对收完整外层输入及两份实际响应 |
| 成员/context、多用法、来源映射 | 同类 `finalizeCandidate` 和相关 parser | 已实现；不能把读过的 Activity 都算步骤 |
| 完整候选并行 | `runtime/modeljob/BoundedModelJobExecutor.java` | 接收 Callable，非硬编码两次调用；直接承载三阶段候选 job |
| 私有任务保存与显式复用 | `runtime/modeljob/PrivateModelJobResultStore.readCompletedProcess`；Discovery `saveProcessTriple/reopenProcess/processFingerprint/requireIntermediateSchema` | 已实现候选v3完整三阶段/完整actual input精确匹配；缺轮次、包装或背景拒绝；fresh及matching reuse两份中间稿按实际candidate Schema校验，旧pair不能充三轮 |
| 正式入口和冻结输入绑定 | `adapter/cli/SourceAnalysisExecution.java`；`analysis/knowledge/ProcessDiscoveryRequest.java` | 已有 Activity/目录/focus/inventory 选择；无须新增公开 Agent |
| 有限候选验收接缝 | `DefaultBusinessProcessDiscovery.reconstructSelectedPreview`、`CandidatePreview`、`BusinessProcessMarkdownRenderer.renderPreview` | 已返回选中候选的全部最终typed fragments、原ordinal/处置/原因及归一化来源；旧reconstructSelected只委托一次，不归并/关闭全仓coverage/发布 |
| 无损归并 | Discovery `merge/isLosslessMerge` | 已实现；保留 target stages 并合并其它记录，不应声称整个对象字节完全不变 |
| 五文件、来源页和重开 | `analysis/knowledge/CanonicalBusinessProcessPublisher.java`、`BusinessProcessMarkdownRenderer.java` 及 checkpoint reader | producer v4完整输出最终narrative/阶段明细/规则/知识文字与同条目certainty，不HTML折叠；公共格式不变，reader按receipt精确支持历史2/3旧渲染 |

`ApplicationProfileDetector` 判断的是 Java/Spring/MyBatis 等技术应用特征，不能把它描述为已能判断 ERP/CRM/WMS。业务系统判断属于模型的全局认识，不修改技术 discovery。

## 2. 本次应做的最小修改

| 修改面 | 具体工作 | 不做什么 | 退出标志 |
| --- | --- | --- | --- |
| 全局选材 | 已接入 `systemAssessment`、私有候选 `investigationQuestions` 和冻结README正文；selection Prompt v2 | 不写行业枚举/词典，不额外增加系统分类调用 | 直接行为测试已确认实际说明、完整导航与一次请求；真实自主质量仍待三例 |
| 选材收敛 | 已保存purpose和按请求/片段顺序的R编号；CHECK v4执行明确首批保留集、一次补读和说明；INITIAL大于120行WHOLE_FILE实际前120预览、private complete/preview/totalLineCount和同文件M10前8行locator，保留后去重新片段；不重读首批 | 不静默截断/恢复未读全文，不加固定文件数量上限或Java语义评分；SUPPLEMENTARY whole保持全原文，不改既有M引用 | 三项首读预览/retain/M10完整补读测试通过；独立集成复核及596项本地CI通过；真实三份CHECK均已返回并完成补读 |
| 模型上下文 | 已将关注问题、systemAssessment/业务假设及候选问题送入实际DRAFT和最终核对外层investigationContext；readingSelections仅metadata，完整实际输入参与指纹和私有保存 | 不将类型假设作为CONFIRMED事实，不把移出原文复制进外层metadata | 实际Provider请求可查到本次背景、目的和选择，最终核对逐字段保留同一包 |
| 三阶段过程 | 已接 `reconstructRaw/RawCandidateResult/finalizeCandidate` 的 DRAFT→WRITE→RULE_REVIEW；DRAFT/WRITE实际Schema门禁阻止缺字段/错误形状，最终 parser 仅取最后processResult | 不替换前六步，不另造业务运行框架，不把最终覆盖/CONFIRMED或中文语义检查提前 | scripted合同通过；真实三份DRAFT、三份WRITE已返回。调拨三阶段已保存；销售最终返回引用7个未定义查询用法，未保存合法任务；采购最后请求超窗未发送，两稿保留 |
| Prompt 与 Schema | 已新增 WRITE/RULE_REVIEW 任务及资源；DRAFT v4负责事实；最后返回processResult/corrections且保留根$defs | 不在Prompt注入采购链或验收正确答案 | 包装所有局部$ref均可从实际Schema根解析，最终纠正正文和对应结构一致 |
| 私有保存复用 | 已写候选v3/pipeline v1完整 draft/writing/review/input/packet/映射/身份/来源批次；三阶段指纹含完整输入、三Prompt/Schema/顺序/配置/模型绑定 | 不改Activity/目录/归并pair，不把半轮当可复用已审结果 | 完整匹配三轮零调用复用且旧字节不变；旧pair不复用，半轮失败不安装COMPLETED |
| 结果归并与发布 | 已升级producer4与版本感知renderer/reader；最终正文、全部stage字段、规则和knowledge text/原certainty完整可见，sourcesMarkdown不变 | 不加独立未审散文产物，不拼接不同阶段造顺序，不重新判断知识结论 | 真实五文件store/reopen与新增知识certainty两项RED→GREEN通过；v4/preview同条目保留状态，历史2/3 fixed bytes保持，4拒绝旧folded正文 |
| 小样执行 | 内部选定preview接缝及renderer已接线，保留全部最终片段与归一化来源；真实三例驱动已执行，稳定原序号导出检查通过 | 不新增公共POC入口，不安装全仓coverage，不续跑旧全仓批次 | 原批次排空；完整调拨任务已显式复用并零模型调用导出新Markdown及来源，原文未改。销售返回原文和采购两稿均保留，不能标为三例已审 |

具体版本见[详细合同§7](business-reasoning-and-writing.md#7-保存并行与版本影响限制在过程任务)。如果实施中发现公共结构确实容不下文字，先指出具体丢失字段，再讨论协议；不能让新正文只留私有文件却宣称正式产物已具备。

## 3. 本轮有意不动的部分

JDT/Core、JavaParser算法、五图和Fact/Proof、Step05/Capsule、M10、Activity、源码快照、公共Agent方法、已有CLI名称、Provider认证、两层并发、材料state、run-output、五文件类型及来源五字段均直接复用。无需新增证据链、SQL/Vue解析器、行业规则引擎、向量库、恢复系统或第四覆盖账。

## 4. 小实验与生产状态分别记

| 事实 | 当前结论 |
| --- | --- |
| 旧跨候选阅读生产接线 | 已实现并有本地验证；历史交付记录保留 |
| 系统认识与聚焦阅读生产接线 | 七项实际RED后实现，重复片段metadata及首读预览补项各有独立RED→GREEN；P1阅读/Prompt26项通过，集成审查与修正后596项本地CI通过。自动化产品/上游调用0；真实SELECT完成1次，CHECK零调用预检通过 |
| 人工采购材料上的四请求研究 | 形成用户接受的可读正文；不能说零事实错误 |
| 一次自主系统认识/选材研究 | 自主提出业务方向并召回相关材料；包偏宽；没有用自动包完成后续成稿 |
| 独立三请求 DRAFT→规则核对→WRITE | 生成用户接受可读性的正文；WRITE引入两处已知错误 |
| 本设计 DRAFT→WRITE→最终RULE_REVIEW | 已生产接线和完整私有保存复用；原九项及四项中间Schema损坏RED均转GREEN，13项三阶段测试通过；最终六直接类85项加通用历史pair6项共91项通过，局部独立复核Accepted。仅scripted行为验证，不代表真实模型业务质量已通过 |
| producer4可读渲染与选定预览 | 九项render/store/reopen测试通过，含已有知识certainty显示的两项RED→GREEN；preview行为实际RED→GREEN，历史2/3 exact bytes通过，局部独立复核Accepted；产品模型/上游调用0 |
| 三例最小验收 | SELECT6844…已完成；批次82d416f4…已FAILED并排空，三CHECK/三DRAFT/三WRITE及两最终核对返回。调拨保存但人工准确性未通过，销售最终返回存在7个未定义query用法，采购最终核对超窗未发送；整个计划未完成 |
| 全仓 | 本轮不执行；三例审阅后再讨论 |

## 5. 后续实施顺序

全局认识与问题选材、首读聚焦补项、三阶段、完整私有保存、producer4五文件/历史读取及内部样本预览已接线并获独立集成复核Approved。修正后完整本地CI通过：596项、零失败/错误、2跳过，SpotBugs零bug/error、PMD通过，9分25秒；此前593项通过及首次输出竞争失败日志都保留，本次具名IDE builder已恢复S。步骤0–6完成，步骤7真实验收未完成。原SELECT加本批实际请求共12次，全部已返回，采购第13次未发送；调拨有效任务已保存但人工质量未通过，销售有最终返回但不是合法已审任务。原批次已排空；仅调拨有效任务由现有renderer离线导出，新增模型调用0。真实质量以最终正文为准，不再次把JDT或326Activity算作待开发。

## 6. 三例真实输入的具名容量阻塞

新SELECT批次`6844166e…`从原始输入自主提出采购、销售、调拨，耗时3分31秒。续跑批次`b80321b0…`显式复用SELECT，保持候选原序号9、23、10和服务绑定，只执行这三例；其中CHECK请求均未实际发给模型。实际输入含完整12–14条Activity、剩余312–314张导航卡及686个冻结文件目录项。最大源码项不是重复引用，而是首批整文件：Controller854行、HeadService2020行、ItemService1535行、两个Mapper XML1456及1153行，另有相关页面。

| CHECK候选 | 输入及Prompt/Schema估算（o200k / cl100k） | 状态 |
| --- | --- | --- |
| 采购 | 239480 / 251453 | 发送前止步 |
| 销售 | 245850 / 258362 | 发送前止步 |
| 调拨 | 234954 / 246721 | 发送前止步 |

本轮私有驱动以两种编码的较大估算加32000实验输出余量，对比绑定环境观察有效上下文258400；这些数不是收费预算，也不是框架写死的容量合同。源码占阅读包约40–42万字符，完整Activity约4–5万字符；没有把问题归咎于缺少源码或反复取证。实际请求保存在`.workspace/system-assessment-three-stage-20260916/output/b80321b0bbec9165023d6ca6b84e83d88f3a816b50723ffe5bbd690ef307721d/observations/`。

P1只补齐已批准的大文件首批预览/已有M10定位导航，不新增模块或阅读轮次。具体合同已进入详细设计§3。三个具名测试实际2断言失败/0错误后均转GREEN，阅读及Prompt26项通过，独立集成审查与修正后596项干净本地CI通过。新固定JAR SHA256为`d2712935abba3946c44f35acda558eb551bfe7e7c52d1cbbe20e47fa07b7337a`，旧c402构建及所有失败记录保留。原销售模拟152412/165307不是最终请求，下面改用实际生产请求。

零模型预检批次`analysis-run:5ea2a8009da970539842d9407a533e3932b75073d7ca8c6c6d631f75eb8043ac`显式复用成功SELECT，三份真正CHECK封装含新Prompt、Schema及已有定位导航。采购估算168077/180516、销售170799/183757、调拨166610/178890；各自较大值加原32000实验余量均低于本次观察258400。采购7个大文件预览都为实际1–120行、preview=true/complete=false/原总行数准确，附129个已有M10定位；预览与旧请求原文前120行逐项相等。三个包的完整Activity、未读导航、冻结文件目录、调查背景及响应Schema与旧请求深比较一致。私有guard在Provider前明确结束，批次FAILED为预检停止状态，不是模型失败；0新增模型调用、没有源码重扫或正式小样输出。补读后DRAFT/最终REVIEW仍需按实际输入各自观察，不能用CHECK通过替代。

依据根AGENTS启动前失败条款与模块已有范围授权条款复核：三个CHECK均未启动真实Provider，不消耗内容轮；修复测试后可在用户明确继续指令内显式新批次执行剩余首次正常3CHECK＋9三阶段，不增加一次具名审批。此前额外请求确认属于过严处理，不作为新授权限制。保留旧失败记录、逐阶段容量检查及真正启动后失败政策，不启动其它24候选或全仓归并。

当前显式真实批次82d416f4…复用原SELECT，仅处理原序号9/23/10。采购最终完整请求估算252524/264448 tokens（o200k/cl100k），未调用Provider；已保存DRAFT和WRITE及原因，按详细设计§7不自动续接。只读诊断发现1048个长statement handle在索引/Schema重复、14个长Activity ID共2595次；尚未采用局部短编号或修改Schema。调拨最终核对纠正五项结构/归属问题，但人工发现拒绝条件遗漏强审核且未审核时跳过库存检查、结束结果漏强审核条件，准确性未通过。销售最后返回6项纠正，但查询阶段及支撑列表引用7个未定义用法；原返回保留，不人工清空或补造ID。新候选修正和替换执行待明确决定，未增加模型调用或继续全仓。

设计记录、旧实测记录和原始模型结果都保留；当前交接只新增目标/差异，不改写旧实验的历史结论。用户对可读性的接受是新的验收意见，与当时记录的准确性问题并列保存。
