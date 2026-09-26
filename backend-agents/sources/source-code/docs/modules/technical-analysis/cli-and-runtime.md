# 三个技术操作：配置、输入输出、保存与 Skill

状态：拟实施合同。当前 CLI 没有下列三个命令。源码准备命令已经实现；旧技术算法已有，不表示本页的新运行接线已经完成。

## 1. 命令与唯一上游

```bash
source-analysis --config /absolute/technical.yaml collect-code
source-analysis --config /absolute/technical.yaml analyze-persistence --code-run <R1>
source-analysis --config /absolute/technical.yaml assemble-materials --persistence-run <R2>
```

配置明确选定R0源码准备结果。`--code-run`必须是已登记并保存有效Step02/03的运行；`--persistence-run`必须保存有效Step04，并能沿完整引用读回其准确Step02/03。不能手工传任意JSON文件拼输入，不能扫描目录找“最新成功”。最后一个命令不再接受另一个可能冲突的code-run。

未传`--run`时CLI仅创建一次新运行；传`--run`时必须匹配已绑定相同意图、来源和上游的QUEUED运行。三项操作都不重新激活结束运行。后两项重开已保存结果，不复活旧JDT进程。

新命令只支持准备版`SelectedSourceBasis`作为新技术采集入口。旧注册来源、旧技术材料和业务结果仍可按其明确历史合同读取；不借新命令偷偷把旧来源转换成准备版。用户若要重新采集旧源码，先显式执行源码准备。

用户已确认：新三个命令接通并验证后，删除旧`plan-materials`生产命令及其独占全链编排，由Skill按用户目标串联。当前代码仍有旧命令，不在文档修订中删除；实施不得保留可绕过新就绪/来源检查的兼容入口。历史产物查询、状态读取和纯重渲染不随旧命令删除。

## 2. 轻量技术配置

新增`technical-analysis-config-v1`，与源码准备配置、业务模型配置分开解码，复用已有工具/插件/材料profile类型。不是复制引擎、插件或存储实现。下列是目标配置示意；路径全部是宿主私有配置，公共请求只保存已冻结的内容引用。

```yaml
schemaVersion: technical-analysis-config-v1
source:
  preparationRunId: analysis-run:<完整ID>
storage:
  root: /absolute/analysis-store
java:
  jdtInstallation: /absolute/jdt-ls
  toolJavaHome: /absolute/tool-jdk
  targetJavaHome: /absolute/target-jdk
  dependencyPreparation:
    mode: AUTO_MAVEN
    rootPom: jshERP-boot/pom.xml
    modules: ALL_ACTIVE_REACTOR_MODULES
    sourceSet: main
    profileActivation: MAVEN_DEFAULT_WITH_RECORDED_CONTEXT
    activeProfiles: []
    inactiveProfiles: []
    userProperties: {}
    buildJdkHome: /absolute/build-context-jdk
    settingsFile: /absolute/approved-settings.xml
    localRepository: /absolute/approved-maven-cache
    network: LOCAL_ONLY
    allowedRepositories: []
    resolverTool: /absolute/fixed-dependency-helper
frontend:
  enabled: true
  nodeExecutable: /absolute/node
  sourceRoots: [jshERP-web/src, jshERP-web/public]
  configurationFiles: [jshERP-web/package.json, jshERP-web/vue.config.js, jshERP-web/public/index.html]
  aliases:
    "@/": jshERP-web/src/
  httpMappings: []
persistence:
  plugins: [mybatis]
readingMaterials:
  maxPacketUtf8Bytes: <明确正整数>
  maxEntriesPerPacket: <明确正整数>
```

- `collect-code`要求java配置；启用frontend时要求受控helper和Node。`analyze-persistence`只要求来源/存储/插件配置，不检查本机JDT或Node是否仍安装。`assemble-materials`只要求来源/存储/材料profile，不加载任何parser。
- 不要求modelJobs、Prompt、Provider、登录或API凭据。观察/查询不初始化工具。
- 源码根/别名目标必须在有效快照内。前端关闭时保存明确`DISABLED`记录，后端继续；开启但工具缺失是配置错误，不能自动改为关闭。
- `httpMappings`仅用于源码无法确定的部署路径/请求实例映射，结构见[前端合同](frontend-http-discovery.md)。不能把领域词、业务顺序、任意脚本放入配置。
- 示例明确选择Maven默认激活规则，实际JDK/OS/属性/文件条件和激活结果仍须保存；不代表客户线上环境。具名profiles/模块可替代示例选择。`buildJdkHome`用于构建条件核对，`targetJavaHome`用于实际Java平台，`toolJavaHome`运行JDT工具，不能混用。
- `network=LOCAL_ONLY`时只用准确缓存，不联网；`CONFIGURED_REPOSITORIES`必须带允许仓库/mirror的ID与URL，凭据只用私有引用。settings里出现其他仓库也不能扩大allowlist。不采用默认Central来绕过空允许列表。已允许的缺项自动取得，不逐JAR确认。
- `mode=PROVIDED`时使用显式`compilationEnvironment`文件和私有locator，禁止同时指定AUTO_MAVEN解析参数；它不跳过来源/模块/平台/完整性核验。完整字段、特殊构建处理和标准工具选择见[依赖准备](dependency-preparation.md)。
- classpath由实际解析并验证的每模块环境确定，不继续接受“任选几个JAR”而标READY的输入。自动准备发生在Step02内部，不新增第四个依赖CLI。
- 可配置上限包括工具超时、单文件解析字节、前端追踪深度/节点数和候选值数；上限触发必须写具名未处理范围，不能静默截断并宣称完整。首版默认值须在原型中以实际项目及fixture测量后随schema冻结，不以模型费用为依据。

## 3. 运行身份和公共请求

保留现有`RepositoryAnalysisAgent.start/executeStep/inspect/artifact/render`五个方法，不新增公共Agent方法；主设计保留的validate/trace目标也不借此实现。增加`TechnicalAnalysisInputs`这一互斥请求分支及三个执行意图：`COLLECT_CODE`、`ANALYZE_PERSISTENCE`、`ASSEMBLE_MATERIALS`。Step01、Activity及过程原意图不被复用来暗示新操作。对应执行target分别为现有PROGRAM_GRAPHS、PROVEN_CODE_FACTS、BUSINESS_FLOWS；collect-code在本次操作内先完成APPLICATION_DISCOVERY，但不单独暴露第二个Java导航命令。

技术请求携带：已冻结技术配置/profile/schema/toolchain/artifact policy引用、`SelectedSourceBasis`、与意图匹配的准确上游publication引用。不是业务分析请求，因此不伪造Prompt、模型账户、组织seed、ReaderCandidateRound或M10引用。

组合根在启动工具前完成：CLI解析 → 指定run解析成完整typed refs → 配置与上游校验 → 冻结准备配方/权限/构建输入 → start绑定 → executeStep。`--run`重开时再次比较绑定，不允许同run换来源/准备配方/上游。

AUTO_MAVEN的最终classpath尚未生成，不能在start时假装已有完整编译环境。实际解析在RUNNING内进行，先冻结真实环境并保存私有阶段记录，再建立`javaAnalysisBasis`并启动JDT。此时不安装module5，不在`availableOutputs`登记私有文件；诊断结束或已确定阻断后才一次安装环境、就绪两份正式文件与receipt。最终output引用正式结果，不改写原request。PROVIDED模式也记录本次核验结果。依赖/用户选择变化须新run；已结束运行不续跑。只读缓存可复用，未验证旧索引不会因依赖补齐自动成为新结果。

新运行记录写`analysis-run-request-v4`，读取v3/v2保持原义。`AnalysisRunOutput`写v8，新增互斥`technicalOutput`；历史字段不被改解释成另一种owner。

### 3.1 新技术输出

`technicalOutput`按意图区分，包含`outputRunId`、`selectedSourceBasis`、`inspectionStatus`、`availableOutputs`、`continuationStatus`及具名问题引用。

| 意图 | 必须能登记的引用 |
| --- | --- |
| COLLECT_CODE | 实际编译环境与Java就绪报告；独立前端索引（启用/关闭都明确）；成功时完整Step02、Step03；Java分析基础 |
| ANALYZE_PERSISTENCE | 选定Step02/03；本次Step04（启用或DISABLED）；共同来源 |
| ASSEMBLE_MATERIALS | 选定Step02/03/04及前端索引；本次Step05；共同来源 |

`availableOutputs`仅列实际安装成功的完整引用。`continuationStatus=READY|READY_WITH_LIMITATIONS|BLOCKED`针对**当前操作的下一技术操作**，不代表业务完成。下一操作仍须严格重开和校验，不凭状态字符串跳过验证。

输出在RUNNING期间先登记，再转终态；发生局部/工具问题也尽量保存报告。磁盘无法写入则返回能取得的诊断，不编造receipt。进程强杀不能保证最后报告完整；只有已安装receipt可查询，没有自动恢复。

**预期阻断不是直接抛异常后结束。** 已确认的依赖不齐、诊断未收齐等返回带`availableOutputs`和`continuationStatus=BLOCKED`的技术结果；Agent先登记output，再将运行转为FAILED。READY/READY_WITH_LIMITATIONS且所需最终publication完整时转FINISHED。当前Agent的“协调器正常返回就FINISHED”必须针对技术分支修改。不可恢复异常仍可抛出，但若已安装结果能被可靠重开，应先登记这些准确引用；保存本身失败时不得承诺最终output存在。

### 3.2 查询与阻断报告

现有`BusinessOutputArtifactKey`及`artifact`路由是闭集，不能仅在output添加引用便声称查询已接通。本次在同一查询接口增加技术键：`JAVA_COMPILATION_ENVIRONMENT`、`JAVA_ANALYSIS_READINESS`、`FRONTEND_HTTP_INDEX`、`APPLICATION_PROFILE`、`ENTRY_POINTS`、`MAPPER_CATALOG`、`CAPABILITY_REPORT`、`JAVA_CODE_INDEX`、`PERSISTENCE_MATERIAL_INDEX`；已有`CODE_READING_MATERIALS`继续使用。类名中的Business不是另建一套公共Agent的理由。

- 环境/就绪键映射Step02 module5的两个文件，前端键映射module6；其余键映射准确步骤publication中的具名文件。键、artifact type、schema、producer、文件白名单和CLI帮助同时更新。
- `inspect`可读取FINISHED/FAILED技术output，展示实际refs及缺少的阶段。`artifact`在FAILED技术运行只允许查询`availableOutputs`中**已安装、已重开核验**的上述技术键；不放开任意路径、私有半成品或不存在的步骤，也不放行该运行的业务产物。
- Java就绪受阻而只有module5/6时，只能取得其中实际保存的环境/就绪/前端文件；Step02已完成但Step03中断时，可查报告及完整Step02文件，不能查询一个不存在的完整Java索引。
- Step05 v2技术JSONL/Markdown从`technicalOutput`中的R3引用取得，经新版material reader读取。不得再从历史`readingMaterialCheckpoint/sourceRunId`推测新地址。历史技术/业务查询继续按原分支处理。
- 新查询fixture必须经过真实Agent与store；只测试CLI参数转发或直接调用reader不算接通。

## 4. 跨运行发布：校验真实来源，不要求运行号相同

当前04/05 publisher根据Step01 run决定输出地址，并要求所有上游同run；目标改为显式`destinationRunId`。对应reader和身份计算必须同一交付修改，不能只删除同run判断。

每次发布/重开核对：

1. 来源引用可重开，实际basis等于本次配置/请求绑定，不从待检查输入推导“预期”再与自己比较。
2. Step02和Step03属于同一次collect-code，Step03引的discovery恰是该Step02；二者Java分析基础一致。
3. Step04引的navigation/discovery/source准确等于其请求，不从同源码下任选另一份索引。
4. Step05引用Step04的准确Java上游和Step02前端索引；同源码但不同classpath得到的索引不能混用。
5. 每个输出属于当前执行run，各输入保留原owner；不拷贝上游文件到当前目录伪装成本次生成。
6. 来自源码排除前的文件，不得通过内嵌正文/旧索引绕回。快照版本、有效范围和具名引用共同验证。

仅模型配置、输出目录变化不会使技术结果变成新源码；补依赖改变`javaAnalysisBasis`，显式新collect结果与旧结果分别存在。不自动重算下游。

## 5. Step02的报告与结果保存

使用现有canonical module/step store，不新建通用报告框架：

- 新 `APPLICATION_DISCOVERY / module 5 / java-analysis-readiness`：保存`java-compilation-environment.json`、`java-analysis-readiness.json`及标准module receipt。依赖解析未完成时环境文件是明确标记的部分结果；即使阻断Java，仍可安装这些检查报告，不能冒充READY。解析中先保存私有阶段记录，模块最终文件仅安装一次，不反复覆盖。
- 新 `APPLICATION_DISCOVERY / module 6 / frontend-http-discovery`：保存`frontend-http-index.jsonl`及标准module receipt。先保存前端静态请求，Java入口可用后才保存完整关联结果；**一次run只安装一个最终版本**，不在固定地址覆盖两次。Java未就绪时最终前端记录标`BACKEND_DISCOVERY_NOT_RUN`，不假装零后端匹配。
- 当前Step02模块1/2/3/4继续负责profile、entries、mapper、最终发布；完整Step02发布引用5/6及其语义文件。成功时共八个步骤文件：原profile、entry、mapper、capability、step receipt，加实际编译环境JSON、就绪JSON及前端JSONL。module receipts、缓存和私有诊断另计，不是业务正文。
- 阻断Java时不安装完整Step02或Step03；运行输出仍登记已安装5/6。`inspect`写“检查结果已保存，Java导航未执行”，不能写“全仓零入口”。

Java运行中断但Step02已完整发布时，该Step02及所有已保存原始导航记录保留；不把私有中途索引当完整Step03。新命令第一版不续接半个JDT会话。

新增报告的发布合同固定为：

| 模块 | 文件 | Artifact type | Schema / producer |
| --- | --- | --- | --- |
| APPLICATION_DISCOVERY / 5 / java-analysis-readiness | java-compilation-environment.json | APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT | java-compilation-environment-v1 / v1 |
| APPLICATION_DISCOVERY / 5 / java-analysis-readiness | java-analysis-readiness.json | APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS | java-analysis-readiness-v1 / v1 |
| APPLICATION_DISCOVERY / 6 / frontend-http-discovery | frontend-http-index.jsonl | APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX | frontend-http-index-v1 / v1 |

报告module的完成状态表示报告本身已可靠生成；报告内`readiness/continuationStatus`才决定能否导航。不能为了表达环境BLOCKED而把完整检查报告当成未安装文件。新artifact策略只放行上述精确文件、schema及media type；module receipt和完整Step02引用在真实store测试中核对。

## 6. 版本与读取矩阵（目标，尚未写出）

| 合同 | 当前已实现 | 本次目标及原因 |
| --- | --- | --- |
| 技术配置 | 旧RepositoryRunConfiguration含业务字段 | 新technical-analysis-config-v1；按操作要求最少配置 |
| 公共请求/运行输出 | request v3、output v7 | v4/v8增加技术操作分支与各上游/输出owner |
| 源码准备产物 | producer v3、四文件v1 | 不变；消费已有公共reader |
| Java编译环境／就绪／前端索引 | 无 | 三项新增v1 |
| Core helper私有协议 | jdt-syntax-v2，工具VM bootclasspath | v3，增加按模块环境/目标平台输入；客户端和固定helper一起升级，正文/语法字段不重做 |
| Step02入口行 | entry v3 | 保持行结构/含义；不把前端caller塞进entryId |
| Step02发布/capability | publish producer v3、capability v2 | publish v4、capability v3；加入新引用、前端范围及就绪信息，profile/mapper语义不变 |
| Step03索引 | java-code-index-v2、module producer v1 | schema v3、producer v2；只扩ENGINE provenance（basis、准备来源）；方法/调用/候选算法和字段不变 |
| Step04索引 | persistence-material-index-v1、module producer v1 | schema/producer v2；扩来源/上游/owner/basis合同；XML/SQL算法不变 |
| Step05材料 | code-reading-material-set-v1、module producer v1 | schema/producer v2；扩来源、前端选择/引用/覆盖及跨运行上游 |
| Step05私有材料状态 | repository-run-state-v4 | v5保存新来源及typed refs；不覆盖旧v4 |
| Activity/过程正文 | 当前独立版本 | 不变、不重生成；新技术材料未验证消费者时明确拒绝，不能读取时静默丢前端字段 |

所有新版reader用明确schema分派，保留实际历史数据所需的严格读支。旧版本缺新字段不得默认READY/前端为空/与准备版同源。历史查询与新技术生产分开，不能让宽松历史reader成为绕过就绪的入口。

Step06/07不在本轮执行范围。对于新增Step05 v2，先完成技术reader及纯离线结构传递测试；所有尚未适配的业务入口必须在模型初始化前返回明确`MATERIAL_VERSION_NOT_SUPPORTED`，**不能声称新增Vue原文已经送给业务模型**。检查范围包括Activity projector/执行入口、`PersistedBusinessProcessRunExecutor`及FrozenCorpus的直接重开、旧`RepositoryRunStateV4`状态恢复/导出、任务复用入口。新版私有state-v5仅登记技术材料，不自动转换为旧业务状态。历史v1和旧Activity的原有业务读取仍保持；本轮只加新版本拒绝和技术查询路由，不改业务提示词、选材或模型输入合同。

## 7. 各操作失败与继续条件

| 问题 | 保存及后续行为 |
| --- | --- |
| 来源未准备好/错版本/保存内容损坏/越界 | 报具名问题，禁止工具或下游读取；用户回到源码准备处理 |
| 编译依赖缺失/构建选择不明/特殊扩展/诊断未完成 | 在已允许范围自动准备；仍阻断则保存环境/就绪与前端能安全取得的结果，Agent询问。无降级导航，不假造索引 |
| Vue单文件不支持/动态路径/请求无法匹配 | 保存具体原文位置、候选和原因；后端仍可采集，前端不完整明确呈现 |
| XML动态SQL/缺include/方言不支持 | 沿用现有局部限制，完整原文保存，不执行SQL求值 |
| 单JDT查询失败而会话仍安全 | 沿现有规则保存受影响调用/入口，不自动改导航算法或重试 |
| 工具/协议不可继续或存储失败 | 保留已成功安装结果，当前后续操作不自动启动 |
| 上游合法但存在范围限制 | 下游在所选准确结果上继续，传播限制及分母，不以多跑命令清除问题 |

返回JSON给自动化，中文摘要给用户。固定说明使用的来源/上游、已保存文件、成功/限制/未执行范围及允许下一操作。新三命令退出码固定为：`0`＝所需交付已保存且允许下一技术操作（可带限制）；`2`＝命令/配置/上游选择不合法；`3`＝检查报告已保存但准入阻断；`4`＝工具/协议失败；`5`＝结果保存失败。先保存报告后失败仍使用相应非零码，不因有文件而返回0。若多个问题并存，以保存失败5、工具失败4、准入阻断3的顺序确定退出码，全部问题仍在JSON中；未启动时的输入错误使用2。已有命令退出码不在本轮全局重定义。JSON中的结构化类别和实际refs是控制依据，不解析中文或异常栈。

## 8. 一个 Skill，按授权串联

源码准备的现有Skill保持生效。实现后在同一Skill增加三个操作章节：输入检查、调用模板、真实输出检查、问题报告、何时可进入下一项。

- 用户只要求collect-code，Codex做到该结果报告即止，不自动运行04/05。
- 用户要求做到材料组装，Codex可依次调用三项；不为每个成功步骤强加人工确认。已允许仓库内的下载由Java自动完成。发生来源/依赖阻断、构建选择不明确或需要排除文件才问用户。
- 读取正式JSON与已安装refs，不从命令退出0、目录有文件或旧报告推断完成。
- Codex不能自己拼旧新索引、补HTTP路径、删除排除记录或代替Java做分析。
- 三项技术操作结束不自动进入Activity；本轮业务模型调用为0。

### 8.1 确认不是Java调用模型

Agent读取Skill → 执行CLI → CLI保存并返回JSON → Agent读取真实问题 → 必要时在当前对话询问 → 用户选择 → Agent修改本次具名配置/材料选择 → 新run重新执行。Java不向Skill发送网络消息，不启动聊天LLM，不保持后台进程等待确认。

目标JSON摘要包含 `runId, operation, resultStatus, continuationStatus, availableOutputs, problems, requestedActions`。问题code、模块/文件/坐标、实际操作、期望/实际和可选动作由程序给出；Agent不从错误中文猜控制流。`resultStatus`区分 `COMPLETED / NEEDS_USER_DECISION / FAILED`，不替代内部FINISHED/FAILED；需要用户决定的运行也会结束并保存报告。

人工示例：缺少parent POM且唯一仓库未获允许 → 报告明确哪个parent、哪个仓库及本次未下载 → Agent问允许该仓库还是由用户提供可信环境 → 用户选择后形成新配置/new run → 程序重新核验。不能拿用户回答直接把旧readiness从BLOCKED改成READY，也不能凭口头“继续”运行首版不支持的客户扩展。

报告可用但终态失败时先展示实际产物和缺项，不只说“FAILED”。无权读取的凭据不让用户粘进聊天；提示配置本机私有引用。特殊构建能力的设计批准不等于任何具体客户代码执行授权。详细问题清单见[依赖准备§6](dependency-preparation.md#6-javaskillagent用户如何接力)。

## 9. 可行性结论

02/03共会话、04/05重开保存结果在算法上已有接缝；主要工程工作是来源入口、跨run身份、结果登记和新增前端材料。不能称为“只加三个按钮”。确切修改文件、验证先后与旧路线清理见[依次清理设计](../../plans/technical-analysis-cli-and-vue-cleanup-design.md)。
