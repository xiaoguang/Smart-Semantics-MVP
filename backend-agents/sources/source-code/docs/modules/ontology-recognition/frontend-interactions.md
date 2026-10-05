# 选择回调与保存上下文：正式前端补齐合同

状态：第3步的有限前端生产与Corpus消费实现已通过直接验收。版本化发布/重开、实际R1运行时、R0到R4的完整上下文正文恢复及覆盖处置通过14项直接测试；新R4页上下文消费及其两入口关联冲突修复通过53项相关直接测试，限定独立复审的设计与质量结论均通过。不同入口的上下文元数据分别保留，真实U编号不改，完整正文仍去重。固定客户新R1/R4及公共查询已经完成；三个本体样例的18份实际请求已核对完整回调、保存正文和各入口用途。采购O1已审、O2关系scope错误拒绝，保留模型与静态关联限制，准确结果见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)。只补已经验证的有限Vue2模式；不改变旧MD解释逻辑，不把实验JSON安装成正式证据。

## 当前实现状态

已实现并以直接 Node 合同覆盖：同一 helper 的有限 `PAGE_CONTEXT` 观察、完整源码单元、
事件实参/回调形参、Promise 回调和字面保存分支；私有 Java DTO、Node 协议读取及
`FrontendSyntaxScan`/`FrontendHttpIndex` 的兼容上下文槽位也已接入。直接 filesystem
canonical-store 合同已验证 `frontend-http-index-v3`/module-v3、entry-evidence v2 家族和
严格重开；旧 R1/R4 分支保持原版本读取。实际 `TechnicalAnalysisConfiguredRuntime` 的R1
重构会保留 `pageContexts`、发布/重开v3，并且只按已保存策略的完整ID+digest重开。实际
配置执行测试还经过正式源码准备、运行登记、R2/R3及R4保存，验证完整选择回调和Promise
正文从R0进入所属入口；外部Java/前端工具仅以确定性fixture替换，不是客户源码准确性验收。
独立store fixture仍只证明其保存边界。R4公共artifact查询键的版本化由单独Task 7负责，
不能把上述测试误报为完整本体或端到端查询验收。

页面内直接保存封装的2项Node合同，以及改名、非ERP三参数/mixin合同现已直接覆盖。
后者的HTTP封装为`httpAction(url, parameter, method)`；保存函数在明确导入的mixin中，先给
局部`url/method`设置默认值，在唯一的无`else`顶层`if (this.model.id)`中各改写一次，最后由
唯一直接`return`调用封装。页面字面`@click`处理器只允许一跳明确mixin的`this`调用到包含
字面`.then`箭头的mixin方法；箭头再调用明确mixin的`request`。该回调保留页面
`classifyIntoFormData`的完整单元，但不把它解释为业务数据流。

该有限识别同时验证默认导入自字面`axios`模块的`.create`服务，以及三项wrapper实参的位置。
它不执行条件或通用JS求值：第二个顶层`if`、`else`、额外`url`/`method`赋值、非唯一直接返回、
动态事件或未能精确解析的mixin，均不会发布确认的HTTP/PROMISE/wrapper观察；独立的字面组件
事件上下文仍可保留。这也不把选择事件表述为导致父页面保存，且HTTP请求继续保持其实际
`page#default`实例键，而不是子组件`ref`实例键。

## 1. 为什么需要新版本

历史R1 v2的`wrapperPath`表示一条HTTP封装路径。它不能表示子组件发出事件、父模板绑定、父回调参数和另一个保存按钮之间的独立观察。把这些观察塞进一条线会错误断言“选择必然触发保存”。

新生产使用frontend-http-index-v3；同源新R4使用entry-evidence-index-v2、entry-evidence-v2、frontend-evidence-coverage-v2和entry-evidence module-v4。旧R1 v1/v2与R4 v1严格按原格式读取，不添加字段、不改写产物。Java后端和XML/SQL结果按准确上游复用，不重跑JDT或持久化解析。

## 2. 页上下文是观察集合，不是运行时数据流

新增`FrontendPageContext`，独立于历史HTTP请求record保存。它只描述一个准确的页面实例：

| 字段 | 类型与含义 |
| --- | --- |
| contextId | 程序生成的稳定身份；包含页面实例、来源及实际观察内容，不由模型产生 |
| pagePath、sourceSha256、instanceKey | 同R0的页面路径、摘要和已有实例键 |
| requestIds[] | 此页面实例中实际登记的请求ID；表示共属页面，不表示事件已经导致该请求 |
| sourceUnits[] | 每项unitRef及完整`FrontendSupportingSourceUnit`定位；从R0恢复正文，不复制AST |
| observations[] | 下节限定的技术观察；不加入业务含义或任意表达式求值结果 |
| requestConditions[] | 请求ID、原条件表达式、成立/不成立分支、包含条件的unitRef与实际位置 |
| limitations[] | 具体code、detail及可定位的unitRef；没有位置时明确为空 |

`unitRef`只在这份上下文中唯一，保存范围必须是完整函数、模板、静态声明或明确FILE_FALLBACK。表达式位置`callRange`与完整单元位置分开；不能把调用点当作完整函数。箭头函数按既有FUNCTION单元保存；观察可以说明它是Promise回调，不增加自有异步执行模型。

`requestIds`必须来自同一pagePath的真实请求集合，各请求原有instanceKey保持不变。context.instanceKey标识本上下文的字面组件实例；父页面的保存请求可以仍属于page#default，不能改写为子组件实例。对子组件请求和父页面请求，分别保留其真实用法。某观察与请求只有共属页面的联系时，保持这一性质；不得输出“变量已流入请求体”的确定边。没有发现请求的独立选择上下文仍可保存在R1，不能伪造一个HTTP请求来挂载它。

## 3. 有限观察字段

每项观察有`observationId / kind / fromUnitRef / toUnitRef / callRange / eventName / actualArguments[] / formalParameters[] / argumentBindings[] / detail`。其中：

- fromUnitRef必须存在；toUnitRef仅在目标确切定位时填写，否则为null并说明限制。
- callRange属于fromUnitRef对应的文件，位于其完整范围内。
- actualArguments与formalParameters均为实际源码表达式，保持全部位置，包括多余实参或未传形参。argumentBindings采用已有位置映射合同，不补造形参。
- eventName只记录字面事件名；没有事件的观察为null。detail只能描述技术性质，不补写业务关系。

kind首版只开放：

| kind | 可以说明什么 | 不能说明什么 |
| --- | --- | --- |
| TEMPLATE_EVENT_BINDING | 字面模板事件绑定到确切本地/mixin回调 | 实际用户已经触发事件 |
| COMPONENT_EMIT | 子组件方法中确实写有字面`$emit`及实参 | 任意动态组件一定被运行 |
| EVENT_CALLBACK_BINDING | 同一组件实例的字面事件与父回调及参数位置 | 回调内所有变量已完成跨函数数据流证明 |
| DIRECT_CALL | 已支持的本地、明确mixin或静态import直接调用 | 任意动态this属性、重写或闭包求值 |
| PROMISE_CALLBACK | 方法中实际包含的`.then`箭头及完整源码 | Promise必然成功或回调一定执行 |
| HTTP_WRAPPER_CALL | 已有封装签名下的明确调用 | 部署地址、服务器实际收到请求 |
| INSTANCE_METHOD_BOUNDARY | `$emit`、`$nextTick`等未展开实例API | 已取得框架内部正文 |

子组件emit、父模板绑定和父回调分别有位置；EVENT_CALLBACK_BINDING必须有同一组件实例与同一字面事件的依据，不能按同名函数建立。两个页面使用同一个组件时分别保存上下文；共享正文允许去重，用法不合并。

requestConditions每项为`{requestId, unitRef, range, expression, branch}`；expression原样保存，branch为TRUE/FALSE/UNCONDITIONAL。有限模式只识别已验证的单个if字面URL/HTTP方法赋值分支，不执行条件，不把复杂多次赋值推成已求值结果。复杂情况保留未知和完整实现。

## 4. 从工具到R4的接线

1. 框架自有Node helper继续使用锁定vue-eslint-parser；输入只有同R0准入文本，不打开客户当前目录或加载客户插件。
2. 将有限探针中已经验证的模板事件、静态emit、明确调用与保存封装观察接入同一helper。只复用AST和声明解析；不保留第二套实验发布器。
3. 新私有协议记录页上下文；Java验证路径/hash、完整范围、调用位置、全部局部引用、请求实例、参数数量和闭合关系。非法协议拒绝，不修补成成功。
4. FrontendSyntaxScan/FrontendHttpIndex增加上下文列表并保留旧构造器。R1 v3增加PAGE_CONTEXT行，SOURCE_UNIT是请求和上下文所需单元的并集；旧版本不接受PAGE_CONTEXT。
5. R4按实际请求与入口的候选/确定匹配选取该页面上下文，恢复其完整源码单元。每份入口JSON的frontend新增pageContexts；requestUses/candidateRequestUses保持原性质。
6. 没有关联后端入口的页面上下文保留在R1与覆盖处置中，不伪造entryId，不从覆盖分母移除。关闭前端不启动Node，正式保存空上下文和DISABLED。
7. Corpus将R4页上下文登记为可独立选择的完整技术单元，给出U别名和页面/请求用途。模型可以选择相关完整函数与上下文，不默认附入全部页面文件。结构化长引用在模型投影中短化，原表达式和源码不改写。

新R1仅使用同源固定R0，新R4准确复用原后端R2与持久化R3。原R4继续可查询，不能把一个新前端文件混入旧R4却沿用旧receipt。

### 4.1 新发布策略与旧上游策略必须分开

新R1/R4格式需要新的artifact policy registry；原R2/R3仍绑定原registry的准确ID和摘要。当前技术CLI只接受一份registry，直接把新策略追加到原文件会改变身份，使旧上游无法严格重开。本轮不得改写旧registry、关闭身份校验或因此重跑JDT。

技术配置仅增加显式的历史技术策略文件列表，字段为storage.upstreamArtifactPolicyRegistries。未配置时保持原行为。读取指定上游时，从已保存请求取得其准确registry引用，在当前策略与显式列表中按完整ID/摘要选择唯一匹配项；没有匹配或内容冲突则拒绝。新安装仍使用当前策略。模块和step重开继续由现有canonical store核验原receipt，不尝试不同策略直到某个宽松读取成功，不扫描目录寻找策略。

同一次R4发布读取R1、R2、R3时，按各产物所属运行分别选择已核实策略；保存的来源、配置、工具、Schema等控制记录不合并。实际产物仍引用原owner及原摘要。这个适配只服务本轮技术版本接线，不改变旧MD运行、请求指纹或存储格式。

### 4.2 独立上下文的覆盖处置（直接测试及独立复审通过）

新v2的同一份`frontend-coverage.jsonl`除`REQUEST_COVERAGE`外，增加`PAGE_CONTEXT_COVERAGE`，每个实际R1上下文恰好一行；不增加文件、运行或假HTTP请求。payload固定为`contextId / context / units / includedEntryIds / disposition / reason`。context为原R1的完整观察，units为从准入R0恢复的完整单元正文，不能只有路径和范围。contextId与context.contextId一致，局部单元身份、文件/hash/kind/range逐项对应，正文不截断。

disposition只有两种：`REQUEST_MEMBERSHIP`表示原context.requestIds非空；`NO_REQUEST_MEMBERSHIP`表示该集合为空，同时includedEntryIds为空，reason明确为没有已保存HTTP请求成员。这是成员处置，不是业务成功或运行时因果。includedEntryIds仅表示此上下文实际存入了哪些入口文件，由各入口的确定/候选requestUses对应context集合求并集；候选关系仍由原requestUses分类说明，不能把这一列表当作确定匹配。存在请求但未匹配到入口时，该列表也可以为空，原请求覆盖结果继续保存。

新v2 header保存`frontendPageContextCount`，数值来自完整R1上下文分母。publisher和reader核对记录唯一性、总数、请求上下文成员和入口实际纳入范围；无法关联入口的上下文不从分母删除，不复制进无关入口。无请求上下文仍在这份覆盖产物保存完整原文及处置。历史R4 v1和R1 v1/v2的文件、分母及读取规则不变；该首版v2字段在真实新R4交付前同步定稿与验证。

reason是非空的人类说明，不是控制流错误码；是否有请求成员及允许的引用关系由disposition和结构化集合核验，不能根据英文或中文说明文本判断。

这项接线已通过4项store及10项配置执行直接测试：无请求上下文保留完整正文和处置，
有请求的上下文只纳入实际关联入口，原v1/v2格式保持不变。Corpus消费另经3项新增直接合同覆盖：
严格V2/V1重开、同入口完整正文、共享正文去重但实例不合并、S/R短引用及实际展开成本均已验证；
这不等同于实际客户源验收或Task 7公共查询验收，保存有PAGE_CONTEXT字段也不等于这些后续验收通过。

## 5. 验收及不能声称的结果

直接测试和真实新生产结果必须核对：两个页面的完整选择回调、子组件9个实际参数、两个父回调分别3/8个形参、保存点击/明确mixin/实际箭头、数据整理与HTTP封装、POST/PUT条件、查询getQueryParams不回退。另用改名非ERP fixture验证没有采购名称规则、共享页面不串参、动态事件和缺失mixin如实限制。

Node协议→Java索引→canonical发布→R4恢复→严格重开必须贯穿；只跑探针不算正式完成。旧格式仍重开、旧R0/R1/R4字节不变；新增业务模型、Maven、JDT、SQL执行次数为零。

通过只证明已采集和保存有限静态观察及完整原文，不证明选择回调与保存之间的运行时因果、不证明任意前端覆盖，也不证明本体模型的结论正确。源码中的`info.linkId = info.id`必须留在完整回调内，不能被程序翻译成业务对象答案。
