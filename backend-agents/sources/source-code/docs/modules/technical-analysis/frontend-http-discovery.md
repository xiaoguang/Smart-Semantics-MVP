# Step02 前端请求关联与 Step05 页面材料

状态：新详细设计、未实现。现有源码准备已经保存Vue/JS/XML等原文；Step07曾能按需读取Vue。这两件事都不代表现有Step02或Step05已经拥有正式Vue→HTTP入口关联。

## 1. 输出是什么，为什么需要它

后端可能只有一个通用新增单据接口，不知道哪个页面以何种参数调用。新增前端索引记录“哪个页面/组件，在什么事件或方法中，通过哪些封装发出什么请求，以及可能对应哪些后端入口”，并保留原文条件。

它不从页面名推断行业，不把多个请求排序成业务流程，也不证明请求实际发出或数据库提交。前端筛选条件与后端强制校验分开保存；“页面只给用户选择已审核单据”不能改写成“后台禁止所有其它状态”。

真实走读见[采购关联列表例子](../../supplements/vue-to-sql-walkthrough.md)。下文类型/状态/配置均是拟新增合同，不冒充运行产物。

## 2. 工具及运行边界

采用**固定版本Node工具进程＋成熟Vue/JS语法parser＋有限静态请求连接规则**。parser只提供语法树及位置；跨组件、mixin、封装到HTTP入口的连接不是parser现成功能，必须实现和测试。

主选工具及官方能力核验见[工具调研](../../supplements/frontend-http-parser-research.md)。实施先用真实冻结片段验证Vue2 Options API、模板事件、局部组件、mixin、import及axios封装；通过后锁定工具依赖和协议。不得直接使用客户node_modules或执行客户配置。支持边界不能仅根据工具宣称“支持Vue”就扩展到任意运行时语义。

主选是一套`vue-eslint-parser.parseForESLint`入口，普通JS使用其默认espree路径；固定helper自己的依赖，不根据客户package.json安装工具。Vue2 filter等选项显式保存。首版TS若没有验证并锁定对应脚本parser，标明不支持，不能把示意`.ts`输入当成已实现承诺；不并行维护Babel与另一个AST链接引擎。

工具进程形态与JDT helper同类，但不是调用JDT解析Vue：

- Node路径在私有配置明确指定；使用参数数组启动，禁shell、不执行npm安装/客户构建。
- helper代码和锁定依赖属于框架工具；开发者安装构建工具属于框架工程，不能在分析客户代码时临时联网。
- 只输入公共reader提供的相对路径、原文及语言配置；不能由客户路径加载插件、执行`require/import`、运行Vue或浏览器。
- JSONL协议`frontend-syntax-v1`，含requestId、path、source摘要、syntax语言/版本、节点/范围或具名解析错误。stdout只传协议，stderr诊断；异常退出/超时/破损响应按工具错误处理。
- 返回不可变中立结构，不把Node AST对象或客户可执行函数泄漏到公共Java接口。语法缓存仅限同一来源和同次操作；不建服务常驻/远程worker。

首版必须通过的模式是本项目实际使用的Vue2 Options API；常规Vue3 SFC语法可被parser读取，不代表Composition API、store及任意闭包都已经能追踪。新模式没有测试时明确`UNSUPPORTED_PATTERN`，不启用LLM补链。

## 3. 输入及发现分母

`FrontendHttpDiscoverer`位于Step02内部，输入：

1. 同一`VerifiedSourceTextSet`，含生效的文件排除；不读取当前工作区。
2. 已配置前端根、相对别名及可选HTTP部署映射。
3. 已保存/本次只读的完整Spring入口视图（后端准备失败时可缺，但必须标未执行）。
4. 解析工具身份和运行上限。

先列举有效范围中的`.vue/.js/.ts`等明确支持文件、相关静态JSON/HTML配置；所有发现文件均有`PARSED/PARTIAL/UNSUPPORTED/FAILED/NOT_INSPECTED`处置。用户排除文件不再解析；被引用到时标`SOURCE_EXCLUDED`而不是偷偷取旧内容。

HTTP调用位置分母来自已成功分析的文件，另记录未解析文件和未知范围；不得说“找到了10个请求”就代表全前端只有10个请求。组件即使没被页面路由引用也能存在，`routeReachability=UNKNOWN`不等于没有使用。首版不证明所有页面均可达。

`sourceRoots`限定主动发现前端程序的目录；`configurationFiles`列出允许额外读取的固定配置，如package.json、public/index.html、vue.config.js。解析import需要的同源文件可按准确相对路径补读并登记，不能自动遍历客户node_modules来推断全部运行时行为。配置脚本只解析已支持的字面量赋值，不执行函数/环境访问；未知值需要具名配置或保留未知。

## 4. 静态连接：按顺序做八件事

### 4.1 读取SFC与程序结构

保留template/script/script setup区块及原文件位置，解析静态标签、事件、组件引用、data返回对象、methods、props、imports/exports、mixin列表及函数调用。完整原文始终可读；AST是定位辅助，不把属性名翻译成业务含义。

外部`script src`、别名import只有在固定范围内唯一解析才进入；扩展名省略/index文件使用显式解析规则，多个命中保留歧义。TS类型不当运行值，装饰器或插件语法失败列限制，不安装客户插件。

### 4.2 建立有限的跨文件声明链接

按import/export实际声明连接组件/mixin/API函数。不是“同名函数就相连”。模板ref调用到对应静态组件，事件handler到组件方法；实例/组件关系保留上下文。

Vue mixin和局部方法同名时按已支持的明确覆盖规则处理；无法确认覆盖或`extends`/动态mixin时保留候选。不能把所有叫`request`的函数并在一起。循环import/递归用已有visited集合和边界报告，不无限展开。

### 4.3 追踪到HTTP客户端

识别实际导入的axios实例、`axios({...})`、已支持的实例get/post/put/delete等方法、原生fetch；记录封装调用中的有序实参及配置对象字段。

沿调用位置连接调用者参数与被调用函数形参，跟踪静态对象属性/局部赋值/return表达式。例如`httpAction(this.url.add, data, 'post')`→`axios({url, method, data})`。`this.url`必须绑定当前组件实例的数据来源，不能从其它页面借值。遇到未知对象、动态代码或不能确定的重写时停止该值推导，保留原表达式。

### 4.4 有限值与条件，不模拟JavaScript

仅支持字面量、可确定常量、静态对象属性、字符串拼接、模板字符串中的已知部分、条件表达式及具名参数映射。值为`KNOWN`、带分支条件的`CANDIDATES`或`UNKNOWN`。未知路径变量保存模式，不按字符串相似度补答案。

页面`model.id`决定post/put时，保存“无id时post/有id时put”的两个静态分支。来自用户输入的数量、ID、日期只保存表达式与去向，不造运行值。追踪上限达到后标`CAPACITY_NOT_ANALYZED`及停点；不能把前面找到的第一个值当唯一。

### 4.5 保留页面语境和参数来源

每条请求保存触发位置、页面/子组件、共享方法、HTTP方法/URL原表达式、确定值或候选值、参数表达式和已知常量、控制条件及实际使用到的配置来源。

参数用法分为`QUERY/BODY/HEADER/PATH`及未知。保存`name/expression/knownValue/sourceRange`，不是把`status='1,3'`直接翻译成业务状态。状态含义如在页面说明/常量/SQL中有定义，可作为原文材料供后续解释，Step02不代替业务模型解释。

### 4.6 确定请求地址与后端映射

分别保存：页面填写的相对URL、客户端baseURL、已读取的静态环境配置、后端context-path/类方法route及实际映射依据。查询字符串不参与route匹配但保留参数。URL规范化不能擅自删前缀、变更大小写或URL decode成另一路径。

- 同源绝对/相对地址、固定后端context-path可在明确配置依据下对齐。
- 多环境/运行时可覆盖值按分支保存，不用开发默认值证明所有部署。
- 未知域名或网关改写不猜。可由用户配置`httpMappings`：`clientRef`（实际文件/实例位置）、`requestOrigin`、`requestPathPrefix`、`backendApplicationRef`、`backendContextPath`、`stripPrefix`/`addPrefix`及来源说明；转换只作用于该绑定客户端，实际原URL不丢失。
- 匹配使用Step02已有`methodCondition`；`UNRESTRICTED`不是未知，也不能把GET默认给所有请求。路径变量按已支持的Spring匹配规则处理，不能仅凭尾部字符串相等。
- 同路径受params/headers/consumes等条件影响时，若当前入口信息不足以区分，保留多候选/条件不足，不宣布唯一handler。首版不模拟Spring运行时bean选择。

### 4.7 保存关联结果

关联状态：`MATCHED_UNIQUE`（给定静态配置/条件下唯一候选）、`MATCHED_MULTIPLE`、`NO_MATCH`、`UNRESOLVED_REQUEST`、`BACKEND_DISCOVERY_NOT_RUN`。名称中的matched只指静态匹配，不是执行证明。

每个HTTP位置都保留记录，未命中的请求不能为了让coverage好看而删除。链接用完整`entryId`，不用业务名称、方法简名或裸路径当ID。

### 4.8 生成可重开的前端索引

结果稳定按相对路径、物理位置及分支顺序保存；同一共享函数的原文仅一份，页面实例/请求用法分别保留。页面A的this.url不能污染页面B；一个入口可关联多页面，一页面可关联多入口。

## 5. 正式数据合同

`frontend-http-index-v1`，UTF-8 JSONL；每行`schemaVersion/recordType/key/payload`。地址及发布归属见[运行合同](cli-and-runtime.md)。

| 类型 | 内容 | 消费者 |
| --- | --- | --- |
| HEADER | sourceBasis、有效配置、工具版本、frontend开关、发现/后端匹配状态、范围统计 | reader、Step05 |
| FILE | 相对路径、内容摘要、文件处置、解析问题、sourceRef | 报告、范围核对 |
| SOURCE_UNIT | 完整template块/函数/method/data字段所属完整单元/配置声明的位置及原文引用；声明依赖 | Step05取原文，不重parse |
| COMPONENT_USE | 页面/组件/mixin/导入/事件链接及上下文、候选和限制 | 请求追踪与阅读 |
| HTTP_REQUEST | 物理调用位置、上下文/分支、method/URL/params值、封装路径、原文单元引用 | 入口关联、阅读 |
| ENTRY_LINK | requestKey、准确entryIds、匹配状态、配置转换、原因、参与判断的来源位置 | Step05按入口选页面 |
| DIAGNOSTIC | 位置/类型/原因/未处理范围/影响对象 | CLI、覆盖及材料限制 |

原始字节保存在源码准备快照；SOURCE_UNIT保存不可变来源与精确UTF16/行范围，按已保存位置恢复全文，不保存另一棵完整AST。sourceUnit身份含源码版本、路径、范围、kind；request/use/link身份含物理位置和实例/分支，不能只按URL去重。

每个`HTTP_REQUEST.wrapperPath`为有序连接片段，至少包含`fromUnitId/toUnitId/callRange/receiverExpression/argumentBindings/conditions/resolution`。`argumentBindings`保存有序实际表达式与形参位置的静态对应；不把JSON.stringify后的参数与业务字段含义混同。无法定位的段保留`toUnitId=null`和具体原因，其后不得假装链已闭合。`COMPONENT_USE`另外记录实例与mixin覆盖关系，避免同一wrapper在两个页面的调用上下文串用。原文相同可去重，参数/分支不同的use不得去重。

正式reader须检查全部unit/use/request/link引用存在、来源一致、范围未越界、链接entry属于准确Step02、关联状态与候选数量一致；不得把匹配状态当业务证明，亦不增加每字段语义Proof。

没有可靠单元边界时退到**完整所属文件**并标`FILE_FALLBACK`，不能取随意前8行冒充实现。完整文件仍超过材料上限时在Step05明确未选，不静默截断。

“完整单元”不是“包含所有运行时依赖”。例如函数正文完整但调用动态外部函数，原文完整性和语义未知分别保存。

## 6. Step05怎样组织到后端包

Step05先沿现有方法/调用/Mapper关系组后端内容，再通过`entryId`反向取前端ENTRY_LINK：

1. 读取准确Step02前端索引；禁重新执行Node或Vue parser。
2. 每个关联use保留页面、事件/方法、封装路径、参数/条件和匹配性质。多候选链接同时保留，不改Step03的Java调用目标。
3. 选择链接所需的完整源码单元、静态配置和必要模板语境。首次组包前由组合根按索引范围从同源快照水化`FrontendSourceUnits`，与已读`FrontendHttpIndex`一起传入builder；重开packet时由material reader只恢复已保存选择，校验摘要。共享单元正文一份，各use不合并；builder没有文件系统或parser能力。
4. `frontendSelection`保存索引引用、link/use/unit IDs及短来源映射；Step05不再次保存整套前端AST/原文副本。
5. 技术Markdown按“页面触发与请求→后端入口→Java调用→Mapper/XML/SQL”分段，明确候选与未知。不是将并列技术节点编号为业务时序步骤。
6. 完整投影字节计数包含新增前端单元。若超限，先保持现有完整Java入口可用，将放不下的前端单元逐项记为未选及材料限制；不能仍称端到端链完整，也不能挤掉原本完整Java/SQL而不报告。

原入口coverage继续全部保留；额外`frontendCoverage`按FILE/HTTP_REQUEST/ENTRY_LINK处置，列无后端匹配、无页面的后端入口及不支持范围。“没有关联页面”可能是后端供其它客户端用，不能直接判后端错误。

Step05 v2 reader返回包含这些字段的完整不可变Packet。原有v1 reader保持历史原义。Step06及Step07尚未获准接新前端正文，不能读取v2后悄悄只投影Java；旧状态恢复、任务复用等入口也须明确拒绝不支持的v2。技术查询/Markdown可从technicalOutput重开R3；后续模型消费另行设计。

## 7. 问题及验收

局部语法不支持、动态URL、外部服务、多候选、循环或容量上限均产生具体限制，不阻止安全独立文件分析。来源损坏、排除越界、helper协议失真或结果无法可靠保存则阻断对应操作，不伪造成功。

有限原型必须先覆盖：

- 真实采购页面→共享列表→mixin→getAction→axios→Controller，以及页面新增/修改分支；URL与context-path依据不能省略。
- 两页面同名this.url指向不同接口，不串用；一个组件多个使用上下文分别存在。
- 后端同route多条件/多候选，动态URL、未知alias、循环import和排除文件不误连。
- Vue2模板语法/mixin覆盖和常规Vue3/TS解析的支持/不支持结果都可定位；不通过加载客户插件“修好”。
- 启用/关闭、解析失败、后端未就绪、零前端项目都有显式结果；遍历顺序变化不改变输出。
- 真实存储重开后Step05恢复相同原文和关系，Node/JDT/SQL解析调用为0；模型调用为0。

可行性依赖于有限静态模式命中真实源码，不承诺解析任意JavaScript。若真实例子不能通过原型，则不能只交一张“Vue已支持”的表；报告具体不支持模式，收窄或讨论后再扩大实现。
