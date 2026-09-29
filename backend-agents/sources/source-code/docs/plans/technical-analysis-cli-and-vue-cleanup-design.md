# 四个独立技术操作与按入口证据：依次修改及清理设计

状态：2026-09-29 详细设计；本次只修改文档，**以下新增能力尚未实施**。本页拥有从现有代码到目标的修改顺序和验收对应关系；不是已经执行完的实施记录。总体合同见[技术分析](../modules/technical-analysis/README.md)，字段与版本分别由[入口证据](../modules/technical-analysis/entry-evidence.md)、[运行合同](../modules/technical-analysis/cli-and-runtime.md)唯一维护。

## 1. 本次已经确定的范围

保留源码准备R0，技术阶段改为四个独立命令；不新增四套程序或公共Agent：

```text
R0 ─→ R1 collect-frontend ──────────────────┐
 └──→ R2 collect-code → R3 analyze-persistence ─→ R4 assemble-materials
```

R1/R2是独立分支，不要求先运行前端才能运行后端。R3只依赖指定R2；R4指定R1和R3，由R3取得准确R2。匹配前端HTTP请求与后端入口在R4完成，不让R1依赖后端清单。

最终一条后端entryId一份自包含JSON，以目录索引读取；不再把随机相邻的12个入口组成材料包。请求没有匹配入口时另存前端覆盖，不造entryId。源文件路径相对R0，产物路径相对证据目录。长ID继续作为保存身份；局部模型短编号留待业务消费阶段。

本次纳入：

| 项目 | 处理边界 |
| --- | --- |
| 独立前端CLI、后端去耦与R4匹配 | 迁移既有有限解析和匹配，补真实地址条件，不扩大为JS解释器 |
| 每入口JSON、相对路径、覆盖和真实存储 | 完整展开已收集范围；保留缺口，取消随机合包及默认巨型Markdown |
| 21.1 嵌套调用目标错位 | 投影JDT本身的逐调用绑定与准确位置，不另写Java调用解析器 |
| 21.3 外部／未知／查询失败 | 正常JDK/库边界不误报项目缺失；只能依工具身份判定 |
| 前端必要源码单元遗漏 | 补入已解析调用所需的getQueryParams等完整单元，不重建前端框架 |
| SQL排序投影遗漏 | 使用JSqlParser现有AST取得ORDER BY；不执行动态SQL或追加参数场景取证 |
| 活跃设计与台账 | 区分已完成、仍待实现及历史事实，不把已撤换Maven路线再列待开发 |
| Lombok、七入口和长等待 | 仅受限集成/稳定性核验；未过则具名保留，不自建处理器或调度系统 |

不纳入：Activity/过程再生成或新格式业务适配、跨版本增量复用、通用JS求值、完整动态SQL展开、Maven下载管理、全文件诊断完成证明、日志优先级过滤、客户构建和自动重跑。设计批准不等于允许本次文档任务执行这些工具。

## 2. 当前事实，不能重复开发

[固定源码验收](../supplements/technical-analysis-fixed-source-acceptance.md)已保存原三操作链：旧R1包含前后端，旧R2持久化，旧R3组包。官方classpath与有效POM已经交给现有JDT；106个可读JAR不是目前仍缺有效POM。旧R3有47包、339条后端覆盖、51条前端覆盖；332入口有材料、7入口曾因两处定义查询失败未收集。这些是历史结果，不是新四命令已完成。

- 内嵌Maven准备/下载与旧plan-materials生产入口已撤除，不恢复、不重复计算开发工作。
- 外部编译输入v2、LS/Core模块与目标JDK、现有导航、MyBatis/XML/JSqlParser、跨运行Agent/存储已有接线。
- 有限Vue发现已经保存51条请求；不再写成“完全没有前端关联”。仍与后端同运行，且必要单元选择存在缺口。
- 等待超时污染共享缓存已修正并有43项直接测试；[七入口重验](../supplements/jdt-definition-query-failure-experiment.md)仍有间歇长等待。不能把缓存修复说成长等待根因已修，不能把旧覆盖改成重验成功。
- 当前Java索引schema为v2，技术module producer为v3；持久化schema为v1、技术producer为v2。二者不是同一版本轴。新写版本统一见运行合同。
- 人工导出的采购JSON只是已有证据的展示，不是生产已经具备每入口输出。

## 3. 模块职责与准确改动面

Java路径以下相对 `src/main/java/org/sourceanalysis/app/`；helper路径相对模块根。类名表示当前修改位置，不要求增加同名新框架。

| 当前位置 | 修改 | 保留 |
| --- | --- | --- |
| adapter/cli/SourceAnalysisCli、TechnicalAnalysisConfiguredRuntime | 增collect-frontend；collect-code只后端；R4显式两个上游；按操作只核验相关配置 | 唯一启动入口、真实Agent/registry、外部Maven输入和QUEUED绑定 |
| runtime请求/TechnicalRunOutput/查询器 | 四意图，前后端引用各有owner；去除旧requireR1把前端与后端绑同run的假设 | R0和精确来源/配置/receipt验证，不靠取消校验通过 |
| discovery/frontend/FrontendHttpDiscoverer、Request、Index | 去除新生产的backendEntries与entryLinks；保留语法观察、页面实例和完整单元位置 | vue-eslint-parser、有限模式、动态值/不支持范围 |
| tools/frontend-syntax-helper、FrontendSourceUnits | 记录已确认调用路径需要的完整单元；修getQueryParams漏选 | 不执行客户JS，不新增解释器；原文从R0取得 |
| ApplicationDiscoveryExecutor/PublicationSpecifier | 后端不启动Node、不要求frontend receipt；frontend独立module producer | Spring入口、技术画像、Mapper目录、环境报告 |
| code/jdt与tools/jdt-syntax-helper | Core投影可靠调用binding；Resolver按精确调用归属；外部/未知/失败分类 | 官方导航、合法实现候选、同key一次RPC缓存、模块隔离 |
| code/publish/JavaCodeIndexPublicationSpecifier、Reader | 新CALL字段/schema及技术producer；旧版本严格读取 | 原完整方法/调用/入口关系，不回写历史 |
| persistence/DefaultPersistenceAnalyzer及publisher/reader | 补ORDER BY投影，接受新Java basis并保存准确R2引用 | 完整XML、include/resultMap、动态限制和JSqlParser |
| material/DefaultCodeReadingMaterialBuilder及类型 | 新生产按entry构造；移入已保存HTTP请求的匹配；完整展开方法、前端、XML、SQL | 选择与组装职责，不重新解析，不推断业务 |
| material/publish、artifact/AtomicCanonicalPublicationEngine与policy | 入口目录约束的变长平铺文件集合，真实安装/重开与查询 | 现有原子保存、receipt/hash、其他producer的严格文件规则 |
| CodeReadingMaterialMarkdown、私有材料state及业务准入 | Markdown变可选视图；新state类型与明确拒绝未适配业务入口 | 历史Packet、M10、Activity、报告读取和旧renderer |
| 同一个Skill及例子 | 四命令和准确上游；外部Maven仅获授权执行；按真实报告沟通 | 不代替Java取证，不自行补值或继续业务生成 |

## 4. 依次修改与退出条件

这是后续实施顺序，不是当前已运行的步骤。每个工作单元同步producer、reader、policy、fixture和拥有者文档；测试只运行直接覆盖项，同时最多一个重型构建。

### D0：保存基线及合同

保护现有源码准备、Activity及技术工作，记录历史产物和more-findings.md摘要；不重置、不把全部脏文件视为本次成果。对照真实代码登记当前schema和producer，先固定新请求/输出/entry字段合同。

退出：同一文件的现状、目标和历史三者区分，所有新增版本有明确reader分派；实现尚未开始的地方不写“已支持”。

### D1：分开前端和后端运行

按四命令合同拆前端执行，前端v2保存HEADER、FILE、HTTP_REQUEST、SOURCE_UNIT、诊断和配置来源，**不写ENTRY_LINK**。前端禁用仍生成可查询DISABLED结果，零Node；R4可以使用该正式结果，不能把“没提供R1”当禁用。

后端仅消费R0与已验证官方编译输入，在现有一次JDT会话完成02/03，不初始化Node。两分支配置/指纹相互独立。源码和排除版本相同是R4组合的必要条件。

退出：仅前端无JDK/classpath也能运行；仅后端无Node也能运行；两种运行均真实保存/重开。错误owner/source在工具初始化前拒绝。

### D2：修正已知JDT关联及边界

遵循[JDT设计](../modules/java-code-engines/jdt-engine.md)：Core调用binding和声明binding投影、准确物理调用位置、工具实现候选分别处理。内层Convert.toInt不能沿外层setPageSize读入无关正文；合法重载/多态不被误删。冲突候选可留排查观察，不能当确定边进入XML/SQL。

已确认binary目标按EXTERNAL停止，不再单独造成项目方法缺失；空返回本身不能确认外部。QUERY_FAILED保存操作、位置和安全原因，保留合法局部结果；协议/来源损坏仍是共享fatal。新记录在03、04、05重开后保持同义。

退出：具名错例及反例直接测试通过；真实工具结果另验，不以DTO测试宣布JDT已准确。不得加入第二套候选推理器、logging白名单或客户源码语义补丁。

### D3：补前端必要单元及SQL已有结果的投影

前端针对已支持的调用路径保留完整所需方法/配置单元；本例getQueryParams第115–143行已在R0，但未成为所选SOURCE_UNIT，需要修正常规单元选择，不只硬编码该文件名。动态HTTP参数仍保留表达式/未知，不执行JS。

SQL使用JSqlParser已返回的排序节点，保存嵌套归属、表达式、方向、空值排序等工具实际信息。保留完整Mapper XML和结构；没有参数就不生成某次执行SQL。

退出：解析→发布→重开→入口JSON的字段和原文均保留；范围不靠人工补进验收文件。PARTIAL必须说明仍未表达的动态或工具限制，不能把新增ORDER BY等同完整动态解析。

### D4：按entry组装、匹配和保存

新组装器读取R1与R3沿真实lineage得到R2。后端entryId集合是目录分母，前端requestId集合是另一分母；前端候选和确定关联分开。旧多入口顺序合包、每包12入口、Markdown 5MiB计数退出新生产。

每入口完整展开已收集正文，单文件内去重、不另画重复调用树；跨入口重复共享方法是自包含输出的取舍，不重新导航。未知Java、NOT_COLLECTED入口、无前端匹配、前端关闭、持久化关闭分别表达。

现store最多64个payload且固定白名单，需实现受限变长文件集合：2个固定语义文件＋索引完整entryId推导的平铺文件，拒绝额外文件与断引用。资源上限显式配置，按真实JSON字节核算。超限返回问题，不截断正文、不安装成功集合；调整配置后新R4，无需重跑解析。

退出：超过64入口fixture穿过真实canonical store；搬移导出目录后可以打开；错误来源、文件身份、容量、写盘失败不返回假receipt。

### D5：读取、兼容、Skill和退役清理

入口目录/单入口/前端覆盖接入现有artifact/inspect，已完成R4重开不要求旧Maven临时文件存在。新材料类型未适配的Activity/Step07在Provider初始化之前拒绝，不包装成旧Packet。

新生产不再调用随机合包或默认巨型Markdown；历史reader和对应renderer保留。删除代码必须列出生产/序列化/历史读取消费者核对结果；保留ProgramGraphsExecution等名字旧但仍在用的实现。原Maven下载器、JavaParser生产和plan-materials已退役，不恢复为fallback。

同一Skill更新为四命令，缺Maven输入只说明官方命令与真实失败，用户提供或授权；未知路由、JDT局部失败、容量超限分别解释。禁止自动启动Activity/过程。

退出：只有一条新技术生产路径；旧结果仍严格可查；任意具备终端/文件能力的Agent按共享说明可执行，不依赖聊天记忆或Codex补值。

### D6：真实定向与完整交付核验

实施阶段按明确授权才运行工具。本设计任务不运行。先检查具名嵌套、Mapper、logger、重载、多态和外部调用；前端检查采购“关联请购单”完整原始四参数、第五purchaseStatus NOT_PASSED、共享实例隔离、getQueryParams单元；SQL检查完整XML与排序。

Lombok仅核对成熟工具的受控接入，分别验证LS/Core生成成员及原文位置；不通过就保存具体限制，不自行写注解处理。七入口及长等待保留实际次数/耗时/迟到结果，区分“缓存已修”与“生命周期等待未查明”。没有确认具体任务/调度原因不能宣称51秒根因解决。

通过定向后，才按实施计划执行技术全量四命令、止于R4/Step05，记录实际入口/前端/限制，不预设339或47。若仍有错误确定边进入正文/SQL，该项准确性验收不通过；其他工程部分可独立报告。

## 5. 验收—设计—实现对应表

| 编号 | 必须证明什么 | 拥有者与主要修改处 |
| --- | --- | --- |
| A01 | 前后端可独立，禁用/空结果不混淆 | cli-and-runtime；runtime/FrontendHttpDiscoverer |
| A02 | R0一致、R3引用准确R2、R4准确双分支 | cli-and-runtime；TechnicalRunOutput/reader |
| A03 | 嵌套调用不串位、合法实现保留 | JDT设计；Core/Resolver/Collector |
| A04 | 外部正常、未知目标、工具失败分别保存 | JDT设计；JavaIndex/coverage |
| A05 | 前端四参数与NOT_PASSED、完整所需单元 | frontend设计；helper/sourceUnits |
| A06 | 未知地址或路由条件不伪装唯一关联 | entry-evidence；R4 matcher |
| A07 | XML所有分支及已解析ORDER BY保留 | Step04；DefaultPersistenceAnalyzer |
| A08 | 每入口一文件，两类分母完整，自包含重开 | entry-evidence/Step05；builder/publisher |
| A09 | 超64文件真实安装、相对路径、容量失败 | entry-evidence；store/policy/query |
| A10 | 历史可读，新材料未适配业务提前拒绝 | cli-and-runtime；state/admission |
| A11 | 旧随机合包只历史读取，无默认重复大MD | Step05；旧consumer审计 |
| A12 | 零Maven启动/下载、零LLM、R3/R4零上游工具 | runtime直接计数，真实调用记录 |
| A13 | Lombok/七入口/长等待结果不被成功计数掩盖 | bounded验收记录，backlog21.2/21.3 |

测试采用Luna/xhigh，编码Terra/xhigh，GPT-6 Sol/xhigh设计、调度、文档和debug；不同时运行重型检查。本页不把客户业务模型的历史授权带进技术验收。

## 6. 设计推演与未确认范围

用[采购查询走读](../supplements/vue-to-sql-walkthrough.md)逐段代入：
1. R1可以只从R0保存页面实例、共享组件、mixin、HTTP封装；这里没有后端ID，所以能独立运行。
2. R2从相同R0取得入口和Java关系；外部Maven输入既有，不由R1提供。
3. R3沿准确Mapper声明取XML及SQL；前端不影响该解析，所以修改SQL投影不必重跑Node/JDT。
4. R4读取两分支，以方法/路径和已知条件匹配，再按entry展开完整正文；不需要新解析器或模型。
5. 未知地址条件、Lombok目标、超时入口都有具体位置，不通过补造关系使JSON“全都有”。
6. 所需变更可落在现有运行/工具/存储接缝；动态文件集合是现store的必要窄扩展，不是第二套发布系统。

这个推演证明合同可以分解落实，**不是证明真实准确性已经通过**。尚未确认的是工具兼容和稳定性：Lombok集成效果、JDT长等待具体根因、修正后真实边准确性及入口文件总体大小。各有受限核验出口；不以这些未知为理由无限扩框架。若必须改变本页边界才能完成，先说明具名失败与成熟工具替代方案，再向用户讨论。

## 7. 文档交付口径

本次只形成详细设计、对应清理顺序和backlog状态更新；不实施D0–D6、不改源码/测试/实际Skill，不启动Maven/JDT/Node或业务模型，不覆盖历史产物。受保护more-findings.md保留原字节。后续实施计划应从本页新目标开始，不能继续照旧“有效POM尚缺/三命令未接通/Maven管理待开发”的已过期待办执行。
