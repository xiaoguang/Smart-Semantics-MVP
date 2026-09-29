# 独立前端 HTTP 发现与按入口组装

状态：**独立前端四运行拆分为待实施设计**。保留 `prepare-source` 的 R0；`collect-frontend` 单独产出 R1；仅后端 `collect-code` 产出 R2；`analyze-persistence --code-run R2` 产出 R3；`assemble-materials --frontend-run R1 --persistence-run R3` 产出 R4。四次技术运行绑定同一准确 R0。配置/request/output 目标版本为 v3/v5/v9。前端不需要后端入口、JDT 或 Maven；HTTP 请求与后端 `entryId` 的匹配只在 R4 组装。

**当前事实**：`FrontendHttpDiscoverer` 和锁定 `vue-eslint-parser@10.4.1` 的自有 Node helper 已完成有限语法/来源验证；现有 `FrontendHttpDiscoveryRequest` 仍要求 `HttpEntryPoint` 清单，旧 `collect-code` R1 module 6 发布 `frontend-http-index-v1` 的 `ENTRY_LINK`，旧 Step05 v2 依赖此链接。固定源码的旧 R1/R2/R3 技术产物已经生成，见[实例走读](../../supplements/vue-to-sql-walkthrough.md)。这些成品保持历史身份；它们不是新独立 R1/R2/R3/R4 的完成证明。

## 1. 目标、输入和模块接口

R1 记录“哪个页面、哪个实例、哪个调用位置，经哪些组件/封装，以哪些原始参数形成 HTTP 请求”；R4 再与 R2 已发现的后端入口匹配。R1 的外部接口只收准确 R0 的 `VerifiedSourceTextSet`、前端根/alias/具名配置、工具身份和容量；返回来源核验过的前端索引，不接收后端入口或运行状态。Java 验证 `FrontendSyntaxTool` 观察并发布，Vue AST 与 Node 协议留在内部。R4 入口匹配器接收重开的 R1 请求、R2 的完整 `HttpEntryPoint` 视图和具名地址映射；它是组装内部接口，不新建一套 Spring 路由引擎。

不推理业务生命周期；不执行 JavaScript；不分析任意 Vue/React/TypeScript 项目；不实现通用跨过程数据流、运行时组件调度、模块打包器或浏览器。成熟 parser 已能提供 AST 和位置，这部分不重写；我们的新增工作仅是有限的、针对实际受测模式的连接。

真实例子是[采购页面的关联请购单查询](../../supplements/vue-to-sql-walkthrough.md)，不是“采购订单已经创建”。

固定源码对首版有限模式的具体要求已经核对：采购页面通过相对且省略扩展名的 `../dialog/LinkBillList` 导入组件；组件的 `purchaseShow` 在赋值查询条件后，以独立语句调用 `this.loadData(1)`。共享 mixin 使用 `export const JeecgListMixin` 命名导出，经别名 `@/mixins/...` 导入；其 `loadData` 先调用 `this.getQueryParams()`，再以独立语句调用从 `@/api/manage` 命名导入的 `getAction`。`getAction` 将 URL 和参数交给从 `@/utils/request` 命名导入的 `axios`。这些关系必须由导入、声明和调用位置识别；不能把文件名、组件名或方法名作为针对这一个验收样例的分支条件。

该 HTTP 封装中的 `baseURL` 来源是 `window._CONFIG['domianURL'] || "/jshERP-boot"`。只读 Vue/JS 的发现过程可记录动态优先表达式和字面量后备值，不能把后备值写成唯一运行时地址。固定源码的 `public/index.html` 另有静态赋值，但首版不因扫描到了该文件就隐式执行或全局解释 HTML 脚本；若使用它作为部署映射依据，必须在具名映射中注明来源。后端 `context-path` 与客户端 `baseURL` 分别保存。

## 2. 工具与支持清单

首版使用框架自有、锁定依赖的 Node helper 和 `vue-eslint-parser`；当前原型版本为 10.4.1。工具依据和原型限制见[调研](../../supplements/frontend-http-parser-research.md)。分析客户项目时不运行 npm install，不加载其 node_modules、配置脚本或插件。

parser 返回的 AST 只在 helper/模块内部使用。Java 获得带源码位置的中立记录；不保存另一套全前端 AST 数据库，不并行维护第二个 parser/linker。

### 2.1 首版必须做的有限模式

| 模式 | 连接方式 | 遇到什么停止推导 |
| --- | --- | --- |
| Vue2 Options API | 读取静态 template 事件、components、methods、data 返回的静态字段、mixin 列表 | 动态组件/extends/运行时生成结构 |
| import 与引用 | 精确相对 import、明确配置 alias、实际声明/导出；`@` 按精确或斜杠边界匹配，`@/` 作为明确前缀匹配，重叠时取最长配置键 | 动态 import、包解析不明、多个文件候选 |
| 父页面到共享组件 | 静态组件 ref 与其 methods 调用；保留页面实例 | 动态 ref、运行时替换、跨实例值来源未知 |
| mixin 方法 | 按受测 Vue2 模式连接导入声明及明确覆盖 | 无法确认覆盖时留候选，不任选 |
| 有序参数 | 调用实参与形参位置对应，缺参显式保存；只搬运表达式/已知字面量 | 不执行用户表达式，不模拟函数 |
| 请求配置 | 已验证的静态 URL/data 字段与 getAction/axios 包装的直接参数传递 | 属性动态改写、interceptor 改地址、复杂计算 |
| 请求事实 | 保存已知 HTTP 方法、原 URL、可解析 path、origin/baseURL 表达式及依据 | R1 不读取后端入口；条件不足时保留未知 |

有限值只含字面量、静态字段和直接参数绑定。控制条件原样保留，不能为了“求出 URL”逐渐实现任意循环、闭包、Promise、对象 mutation、动态调用或通用分支求值。

新语法/封装不是“parser 能解析就支持”。首版不承诺 fetch、Composition API、store、TypeScript 调用追踪或任意模板字符串推导；有真实需求和具名 fixture 后再讨论扩展。未知请求保留原文，不能丢掉以美化覆盖。

### 2.2 helper 运行

- 输入仅来自 R0 公共 reader 的有效文件，另带明确排除清单、语言/别名配置；源码中的文字是数据。
- 用 Node 参数数组启动框架自有的 `tools/frontend-syntax-helper/main.cjs` 一次性 helper；`src/main.cjs` 仅是转发到它的包装文件，不承载实际解析逻辑。stdin 为一条 `frontend-syntax-request-v1` JSONL，请求含 requestId、选定根/别名，以及每个已准入文件的 path/sourceHash/sourceText。helper 不自行打开当前 checkout 或客户 node_modules。stdout 为私有 `frontend-syntax-v1` JSONL，stderr 为工具错误；输出含每个选定文件的处置、来源绑定的请求链观察及诊断，供 Java 做准确入口匹配。解析器启用 JSX 语法识别但不执行源码；一个文件的语法失败记为 FAILED/诊断，已解析父文件若引用该文件不得解引用缺失 AST，更不能阻断其它文件的请求链发现。
- `NodeFrontendSyntaxTool` 只向 helper 发送本次配置根内、有效范围中的 `.vue/.js` 文件及其 R0 原文、路径和摘要；不把工作目录设为客户树，不加载客户 `node_modules`。它关闭一条 UTF-8 JSONL stdin 后等待一次性退出；超时会强制终止并在有界时间内确认退出。必须核对全部选定文件都有且仅有一个处置；截断、超限、重复、未知类型或缺 FILE 的 stdout 不能冒充完整结果。响应的 UTF-16 位置、sourceHash 与本次输入核对，错误不降格为普通未解析请求。Java `String` 与 parser offset 都按 UTF-16 单位核对，行号由同一冻结文本中的换行计算，不把字符 offset 当 UTF-8 字节位置。不是 RPC 平台、常驻服务或恢复系统。
- 新 R1 前端工具身份沿用 Node 可执行文件、框架自有 `main.cjs` 和锁文件字节摘要；静态设置及配置文件来源进入独立 profile 身份。旧 queued request 的内容变化在工具启动前拒绝。launcher 固定解析自有 helper/lockfile，不从客户 YAML 或调用方 CWD 取得工具；旧实现已具备这套固定工具身份，新四运行接线仍待实施。
- 进程无法启动/非零退出或无法确认超时终止为 `FRONTEND_SYNTAX_TOOL_FAILED`；已确认终止的超时为 `FRONTEND_SYNTAX_TOOL_TIMEOUT`；截断、超限、JSON/记录/FILE 闭合错误为 `FRONTEND_SYNTAX_PROTOCOL_INVALID`；输出 path/hash 不属于本次 R0 为 `SOURCE_OBSERVATION_SOURCE_MISMATCH`。局部语法不支持记录该文件，独立文件可继续。
- 使用现有工具调用上限；支持清单未命中则停，不通过不断增大深度/候选数发展通用解释器。

## 3. R1 独立发现的处理

目标模块入口保持一个类型化 discover 接口，入参从当前 `FrontendHttpDiscoveryRequest` 移除 `httpEntries/backendDiscoveryExecuted`；产物是只含前端事实的 `FrontendHttpIndex` v2。现有构造和旧 v1 reader 保留历史读取用途，不能把旧 `link()` 当作新 R1 的处理步骤。

请求含同源 `VerifiedSourceTextSet`、选定根/alias/配置和容量。构造注入返回中立、带来源位置请求链观察的 `FrontendSyntaxTool`；Java 负责核对来源、范围、覆盖和稳定顺序，不重复 JS 连接。parser AST 和 Node 私有协议不进入索引。固定 helper 与 `NodeFrontendSyntaxTool` 的直接测试已通过，但生产运行从后端拆出尚未实现。

1. **枚举选定文件。** 记录明确前端根中的文件、配置与排除；每个选中文件有 PARSED/PARTIAL/UNSUPPORTED/FAILED/NOT_INSPECTED 处置。
2. **调用成熟 parser。** 得到模板/脚本节点和位置，不执行客户内容。
3. **连接声明。** 仅按§2的 import、组件 ref、mixin 模式连接；同名不是依据。
4. **保留页面实例和参数对应。** 同一组件被两个页面使用时保留两个上下文。原文可去重，参数用法不可合并；四实参调用的第五形参记录 `NOT_PASSED`。
5. **找到受支持 HTTP 封装。** 记录 method/URL 的字面量或原表达式、query/body 参数来源和未传参数。不能把 status 数值翻译为业务含义。
6. **保存客户端地址依据。** 原 URL、baseURL 表达式及静态后备值分开保存；R1 不读后端 context-path。具名地址映射与后端 path 的核对留给 R4，未知不自动去前缀。
7. **保留必要源码单元。** 不只保存发出 HTTP 的函数，还选择触发、组件方法、`loadData`、`getQueryParams`、`getAction`、axios 配置中实际参与链路的完整函数、template 或静态声明。旧固定 R1 仅选入 mixin `loadData` 90–114 行，未把 `getQueryParams` 115–143 行单独选入；补齐是新目标，不能说历史产物已具备。
8. **稳定保存前端事实。** 按路径、位置、页面用法排序；原文引用、完整链片段、请求与局限一起发布。R1 不调用匹配器。

解析 import 的文件必须在同源有效范围，未找到不能自动说“已排除”；只有实际命中 R0 排除记录才能写 SOURCE_EXCLUDED。外部包或 alias 不明保留问题，不扫描客户依赖目录尝试全部补齐。

计数只表示已选择/已处理文件和已发现请求。未解析文件的请求数量未知；不证明“整个前端所有请求都找到了”，也不证明页面路由可达。页面路由发现不另建一轮任务。

## 4. R4 地址匹配及结果

R4 严格重开准确 R1、R3→R2 和共同 R0，再以 R2 的完整 HTTP 入口视图匹配。动态 path 或 Spring params/headers/consumes 等条件无法核对时保留候选，不伪造唯一匹配；不实现第二套 Spring 路由解释器。R1 仅保存客户端请求事实，不填后端 entryId。

可选 `httpMappings` 仅解决具名客户端的已知部署地址映射：
`clientRef / requestOrigin / requestPathPrefix / backendApplicationRef / backendContextPath / stripPrefix / addPrefix / basis`。
可选地址字段的 YAML `null` 或空文本都表示“不声明该段”，不会被保留为一个空地址；具名 client、backend 和 basis 仍必须是非空文本。
不允许任意脚本；源文件对应实例及原 URL 不丢失。映射没有证据就留未知，不让 Agent 为使测试通过手填一个“正确入口”。

当前旧代码只保存 `httpMappings` 于技术配置身份，`FrontendHttpDiscoverer.link` 按 `resolvedPath` 与后端 route/method 直接比较；未核对 `clientRef/requestOrigin`、前缀和后端应用。旧 `MATCHED_UNIQUE` 只能说明 path/method 相同，不能独立证明动态部署地址可达。R4 目标有具名映射与原始来源依据才转换地址；不能靠路径后缀猜测。

R4 对每个 R1 请求给出：

- MATCHED_UNIQUE：已知静态配置下一个入口，不是运行时证明。
- MATCHED_MULTIPLE：多个可能入口。
- NO_MATCH：已确定请求，但选定后端未匹配。
- UNRESOLVED_REQUEST：无法确定请求，或虽有路径候选但必需路由/地址条件仍未知；保存候选ID及具体条件，不当确定调用者。
- BACKEND_DISCOVERY_NOT_RUN 只供旧 v1 历史读取。新 R1 可早于 R2 完成；R4 缺准确 R2 时拒绝组装，不发布假“零匹配”。

`FrontendHttpIndex` v2 的读取界面只保留文件处置、配置文件来源、完整源码单元、组件使用、请求和诊断；不含入口关联。每个请求保留页面实例、原始位置及有序参数。配置文件以单独 `CONFIGURATION_FILE` 行保存 R0 相对路径/SHA，不计入 parser 的 `FILE` 分母；不执行 Vue/构建配置，也不因选中 `public/index.html` 就推断部署值。动态 `baseURL` 表达式和静态 fallback 分别保存；后备值不是唯一运行时地址。未传形参记录 `NOT_PASSED`。`DISABLED` 是正式 R1 结果：不启动 Node，仍有准确 publication 并可供 R4 使用；R4 将入口前端状态标为“未分析”，不能把它等同 `ENABLED` 零请求或省略 `--frontend-run`。

R1 v2 沿用具体 `FrontendHttpIndexModulePublisher` 的 receipt-last canonical store，改为独立 R1 owner 和 v2 schema；严格发布/重开核对 R0 basis、R1 controls、descriptor、行间引用、path/hash/range。`CONFIGURATION_FILE` 不可被 wrapper 当作 `FILE`；`SOURCE_UNIT`、`COMPONENT_USE` 从已保存关系稳定派生。旧 v1 publisher、旧 R1/R3 fixture 通过直接测试，不能代替新 v2 的运行及重开验收。

## 5. 最小正式索引

`frontend-http-index-v2`，UTF-8 JSONL；每行含 schemaVersion/recordType/key/payload，不增加求值轨迹数据库：

| 类型 | 内容 | 用处 |
| --- | --- | --- |
| HEADER | R0/R1 来源、配置、工具、启用/关闭、实际检查范围 | 重开与报告 |
| FILE | 路径、hash、处理状态及问题 | 不遗漏未知文件范围 |
| CONFIGURATION_FILE | 根配置等已选文件的 R0 相对路径与 SHA-256 | 保留配置证据；不执行配置，也不把根外配置误计入 parser 文件分母 |
| SOURCE_UNIT | 完整函数/template/静态声明或完整文件的位置和来源 | 第五步恢复原文，无须重解析 |
| COMPONENT_USE | 仅参与请求链的页面/组件/mixin 连接与实例 | 防止页面间串值 |
| HTTP_REQUEST | 调用位置、上下文、方法/地址、参数表达式、封装片段 | 阅读请求如何形成 |
| DIAGNOSTIC | 工具错误、不支持模式、动态值或容量未处理位置 | 说明缺口 |

源码字节仍由 R0 保存；索引保存精确位置/摘要，不复制完整 AST。找不到可靠单元边界时保留完整文件并标 FILE_FALLBACK；不任意截前几行。每条参与链路的 `FrontendWrapperCall` 同时保存 `callRange`、包含它的 `sourceUnitRange` 和 `sourceUnitKind`（`FUNCTION / TEMPLATE / STATIC_DECLARATION / FILE_FALLBACK`）；两处范围绑定同一文件与摘要。文件级诊断保存 `code / sourcePath / sourceSha256`，其 `requestId` 可以为空；请求级诊断还保存真实请求 ID。这样 Step05 无须重新运行 parser，就能按范围取回完整原文并准确指出坏文件。

wrapperPath 的片段含 fromUnit/toUnit、callRange、sourceUnitRange/sourceUnitKind，以及实际支持的实参绑定与限制；表达式只记录不求运行值。无法连接的下一段为空且说明原因，不能在后面的展示里补成闭环。receiverExpression、conditions 等尚未有稳定提取或保存合同的字段不应被描述为当前已经具备。

R1 reader 检查引用存在、来源相同、位置合法，不需要后端 entry。R4 reader 才检查匹配 entry 来自准确 R2。索引身份含页面用法/位置，不能只按相同 URL 去重。

## 6. R4 每入口材料与全仓覆盖

R4 严格重开 R1 前端索引、R3 持久化及其准确 R2 后端索引，并核对四运行共同 R0。它在这一步生成匹配，不重跑 Node、JDT、SQL parser 或业务模型，零业务 LLM。

1. 对 R1 每个请求按具名地址依据和 R2 完整 method/route/条件匹配，结果及所有候选只属于 R4。找不到可证地址留 `UNRESOLVED_REQUEST`；确定地址而无入口为 `NO_MATCH`。唯一匹配才进入入口的确定 `requestUses`；多候选进入每个候选入口的 `candidateRequestUses`，附全部候选 ID，不能当确定调用者或沿它断言 SQL；全仓覆盖对该请求只计一次。
2. R0 按 R1 保存的同源范围恢复完整前端 `SOURCE_UNIT`，再按 R2/R3 保存的来源恢复 Java、Mapper/XML/SQL 材料。共享正文可以在同一入口内去重，页面实例与参数用法分别保留。
3. 对每个真实后端 `entryId` 生成且仅生成一个平铺 `entry-<entryIdhex>.json`，包括来源链、入口、确定 `requestUses`、分开的 `candidateRequestUses`、完整已收集源码单元、Java 调用及实参、持久化候选、上游限制和覆盖。源码位置统一为 R0 source-relative path，产物引用为本 publication 的 bundle-relative path；原文按字节/范围恢复，不做字符串替换。即使未收集 Java 或没有页面，也写明确空范围及原因；R1 `DISABLED` 时写“前端未分析”。不能把最多 12 个入口混在同一个 packet 才可读。
4. `entry-evidence-index.json` 列出入口、对应文件名/状态及数量；文件字节数和摘要只以 canonical receipt descriptor 为准，不另建一层校验账本。`frontend-coverage.jsonl` 对所有 R1 请求保留匹配、未匹配、未知、实际纳入情况和原因。独立前端未匹配请求仍在全仓索引可查询，不因没有 entryId 从分母消失。
5. 文件名从已校验 entryId 的十六进制部分生成；manifest 限制变长 payload 集合、文件数和容量，继续使用原子发布，不接受任意路径。每入口 JSON 自身可单独严格重开，不要求先打开其它入口的包；索引与覆盖同一个 R4 publication。
6. 按实际 canonical JSON 字节计量每入口及整体容量。任一单入口或整套输出超限，R4 保存具名失败报告而不发布成功证据集合；不得逐单元削减、截断正文或写伪完整 stub。上游已有未收集范围仍照原限制报告。技术 Markdown 按准确 R4 入口 JSON 展示“页面请求→后端入口→Java调用→Mapper/XML/SQL”，明确静态候选和动态地址；它不是业务办理顺序。

旧 Step05 v2 Builder 目前只把前端单元附在已形成的最多 12 入口 packet，并仅消费 v1 `MATCHED_UNIQUE ENTRY_LINK`；旧 R3 的跨运行重开及 Markdown 已有验证。新 R4 matcher、每入口完整 JSON、独立全仓覆盖与严格 reader 均待实施。无页面的后端入口可能供其它客户端用，不自动判错；Step06/07 未适配新格式时须在模型初始化前拒绝。

## 7. 验收与扩展停止线

必须以生产模块接口和真实 store 验收，而不只再次测试 parser：

- 真实采购页面→LinkBillList→JeecgListMixin→getAction/axios→Controller，保留 status 原值、四个实参和未传第五参；URL/baseURL/context-path 依据完整。
- 两页面共享方法但 URL/参数不同，不串用；同名函数不能凭名称相连。
- R1 在无 Maven/JDT/后端入口时独立发布；R2 在无前端时独立发布。R4 严格拒绝不同 R0、错 R1/R3 lineage、伪 receipt、错来源、无效排除。
- 动态 URL、未知 alias、mixin 歧义、局部坏文件、排除、正式 `DISABLED` R1、`ENABLED` 零请求、无页面入口、无匹配请求及多候选均在正确运行的覆盖中如实保存；容量超限不发布成功入口文件。
- R4 为每个 entry 生成一个可单独重开的完整 JSON，全仓未匹配请求仍可查询；技术 reader 不重新分析，JDT/Node/业务模型新增调用为零。

如果需要超过§2范围的代码求值才能连上真实例子，先说明具体断点和成熟工具替代方案，不继续堆通用解释规则。固定旧 R1 已发现 51 条请求/链接，但新独立四运行、`getQueryParams` 完整单元、地址映射匹配及每入口 JSON 都未验收；不能把旧路径的 `MATCHED_UNIQUE` 称为新结果。
