# Java代码引擎：配置与共同材料合同

> [总设计](README.md)。本页字段是已发布的 JDT 合同；第二阶段 JavaParser 必须适配它，而不是要求旧 Java records 原样兼容。

## 1. EngineConfigurationLoader：只选择工具，不决定业务

模型批次只消费已保存材料，不调用本 loader/open/collect。取材时的 engine、源码、classpath及入口选择保留在原 publication basis；模型、Prompt、并发和输出目录不是引擎失效条件。要改变取材内容才显式生成新材料，不能将旧材料就地标成新配置产物。私有材料 state/batch 配置以[模型执行 §7](../model-job-execution.md#7-固定材料与独立模型批次已实现)为唯一合同，不升级此处导航索引/共同源码 Schema。

输入是用户本机的工具配置。输出是启动时固定的 `EffectiveEngineConfiguration`。解析器使用 Jackson YAML数据绑定，不手写 YAML；新增依赖须按项目既定固定版本/安全审阅流程落地，本次不下载。

目标配置：

```yaml
sourceAnalysis:
  javaEngine: jdt
  jdt:
    installation: /opt/source-analysis/tools/jdtls-1.61.0
    javaHome: /opt/source-analysis/tools/jdk
```

第二阶段只需将 `javaEngine` 改成 `javaparser`。工具路径是本地启动配置，不能进入公共分析请求、模型材料或候选正文。上面路径是部署示例，不代表机器上已安装在该位置。

| 字段 | 精确含义 |
| --- | --- |
| sourceAnalysis.javaEngine | 必填，闭集 `jdt` / `javaparser`，大小写敏感；不允许 `auto` 或 fallback列表 |
| jdt.installation / javaHome | 选择JDT时必填；先检查真实安装与Java版本，不能通过shell插值执行任意命令 |
| 缺失/重复/未知配置key | 启动前 `ENGINE_CONFIGURATION_INVALID`，不执行索引和模型 |
| 未选引擎配置 | 允许保留，但不打开路径、不启动进程；结构和未知key仍检查 |
| 宿主资源保护 | 继承现有运行配置与取消/超时；不增加按“预计token费用”停止的开关 |

不从客户仓库读取会改变分析宿主的 YAML。客户 POM/配置只当被分析文本。没有 CLI命令级或环境变量的隐式覆盖优先级：组合根加载一次，Java调用和CLI用同一有效配置。需要更换工具时新建执行，不在旧运行中改值。

已批准的[模型 job YAML](../model-job-execution.md#2-唯一配置入口与精确-yaml)在同一宿主运行文档新增 `sourceAnalysis.modelJobs`，由现有 RepositoryRunMain 组合根统一解析；本 loader 的 engine 字段仍只为 javaEngine/jdt。实现时组合根只把这两个字段投影给当前严格 `SourceAnalysisDocument`，不能假称旧 loader 接受新增 modelJobs，也不建第二配置入口。全局/每 Provider 模型并发不改变 JDT collect 的单 worker 顺序边界；模型池在技术取材与材料保存后才启动。技术/材料基础与模型执行配置分别严格保存核对，改并发值不使已存技术材料失效。

## 2. JavaCodeEngineFactory 与会话 Interface

下面是**内部工具 Interface**，不替换公开 `RepositoryAnalysisAgent`，也不暴露 Eclipse/JavaParser AST：

```java
interface JavaCodeEngine {
    JavaCodeSession open(VerifiedJavaProject project);
}

interface JavaCodeSession extends AutoCloseable {
    JavaDeclarationCatalog catalog();
    EntryCodeContext collect(EntrySeed entry);
    EngineDescriptor descriptor();
    void close();
}
```

`open` 只接收现有源码读取能力包装成的 `VerifiedJavaProject`：snapshot引用、文件列表、源码根和Java语言级别、已批准本地依赖与诊断。不是一个任意 Path 的公开请求。相对文件位置、行列和字节由来源层负责；私有工具 Adapter可使用投影路径。

`catalog` 返回所有可分析Java文件的声明、参数、注解和文件诊断；不输出行业标签。现有Spring映射规则消费它形成 `EntrySeed(entryId, methodLocation, trigger)`。入口源位置来自发现器；调用方不能注入预期Service文件、实现方法列表或解析答案。

这里的 `methodLocation` 精确展开为 `methodKey + methodRange`。`methodKey` 是公共 catalog 中的跨引擎稳定声明键；`methodRange` 是完整方法/构造器声明的 `SourceRange(startOffsetUtf16,lengthUtf16,startLine,endLine)`。Step02 必须将两者写入每一条入口记录并把它们纳入 `entryId` framed identity，Step03/05 必须用两者校验并选择入口；handler FQN、方法名或路由不能替代它们。M2 `http-entry` moduleVersion、`application-discovery-http-entry-discovery-v2` draft，以及 M4 `publish` moduleVersion 都升级到 v3；公开 descriptor `application-discovery-entry-points-v2` 与每行 `application-discovery-entry-point-v2` 同步升级到 v3。profile、capability、mapper schema 保持 v2。所有直接 reader 同步拒绝旧 entry wire，不靠缺字段推断。

`collect` 隐藏工具的查询、递归、去重与候选处理。相同会话同一入口重复读取复用结果；不同入口必须复用已查询的方法/调用位置及完整方法记录，入口成员范围和展开状态独立。精确规则见[优化设计第3–5节](../../plans/navigation-reuse-and-readable-report-design.md#3-jdtprojectsession一次会话拥有复用范围)：按操作/位置去重，不省略必要implementation，不把不同入口的最终CallSite当成一个缓存对象。只支持当前单worker顺序调用，不要求并发会话或分布式索引。

`descriptor` 保存engineId、adapterVersion、toolVersions、languageLevel和能力说明。descriptor在程序侧，不让模型为不同工具选择不同业务答案。工具不具备的能力必须真实披露，不能写成“整个仓库不可分析”。

工具错误以`CodeEngineException(code, detail, affectedSourceKeys)`穿过Interface；code闭集至少包括配置无效、工具不可用、索引失败、查询失败、协议无效、来源无效。已完成session诊断保留；不能把异常返回成空catalog/context。单个调用未解析是合法结果，不抛整个入口异常。

单个RPC查询失败但会话仍可用时，以该调用的QUERY_FAILED诊断保留，结果不称完整，不自动重试；仅会话不可用/协议损坏/来源错误等阻止安全继续时抛上述异常。合法空目标、局部查询失败、整个工具失效三者不能混为一种空列表。

### 2.1 公共catalog最小字段

| Record | 必要字段与消费者 |
| --- | --- |
| JavaDeclarationCatalog | snapshotRef、files、types、methods、fileDiagnostics；所有清单内Java文件都有成功/失败诊断 |
| TypeDeclaration | sourceLocation、qualifiedName（可空）、kind、annotations、fields、methodKeys、supertypeTexts；不猜不存在的绑定 |
| MethodDeclarationView | methodKey、declaringType、name、kind、modifiers、parameters、returnTypeText、annotations、sourceLocation、hasBody |
| AnnotationView | nameText、qualifiedName（工具确认才填）、成员原文、可静态读取的literal/enum/array值、sourceLocation、nameSelection；nameSelection是注解名称本身的UTF-16半开范围，不能用含参数的整注解范围代替 |
| FieldDeclarationView | name、typeText、annotations、initializerText（可空）、sourceLocation |

catalog不强制全仓方法正文都塞入同一个大对象。方法详细记录按需取，发布时以JSONL记录保存。注解identity由JDT Adapter中的catalog解析职责拥有，精确算法见[JDT模块](jdt-engine.md#2-jdtsyntaxreader由core取完整方法和每个调用位置)：Core取import/package和nameSelection，LS定位声明，自定义组合注解按源码递归并防环。只有非 recovered 的 JDT binding 才能直接填 qualifiedName；recovered binding 留空后再使用显式 import 或 LS 结果，防止把缺 classpath 的 `RequestMapping` 错认成当前 package 类型。仍无法确认时qualifiedName=null并保留诊断；不按simple name默认为Spring。未解析mapping对应的方法保留可定位候选与discovery限制，不能被当作“没有入口”从分母消失。

## 3. EntryCodeContext：下游真正需要的内容

程序侧上下文是一个入口可达材料的集合，不是动态调用日志，也不是一条无分支链。

```json
{
  "schemaVersion": "entry-code-context-v1",
  "entryId": "entry:example",
  "entryMethodKey": "method:controller",
  "methods": [],
  "calls": [],
  "supportingSources": [],
  "limitations": [],
  "technicalEnhancements": {
    "availability": "NOT_PRODUCED",
    "reason": "SELECTED_ENGINE_HAS_NO_STRICT_GRAPH_ENRICHMENT",
    "graphRefs": [],
    "factRefs": [],
    "flowRef": null
  }
}
```

这是字段外壳说明；非空入口的有效上下文必须至少包含入口方法，不能用上面空数组提交生产结果。数组始终写出，只有标注nullable的标量允许null。snapshot、引擎身份与产物版本在publication envelope中保存一次，不在每条边反复存SHA。

### 3.1 方法及源码字段

| 字段 | 约束 |
| --- | --- |
| MethodCode.methodKey | 同一快照内由声明的相对路径、offset、length、kind定位；复用现有framed identity，不用JDT handle当跨引擎ID |
| kind | `METHOD / CONSTRUCTOR / INITIALIZER / LAMBDA`；后两者不伪装成显式方法 |
| declaringType / name / signature | 工具给出的准确展示；type未知可空，lambda/initializer的name可空，signature可用声明原文，不猜全限定类型 |
| enclosingMethodKey | nullable；lambda/嵌套可调用范围的外层归属，不等于运行顺序 |
| parameters | 有序 `{ordinal,name,typeText,varArgs,annotationTexts}`，重复类型/名称表达不去重 |
| returnTypeText | 方法返回类型原文；构造器/initializer或未声明的lambda返回类型为null |
| source | `{path,startLine,endLine,startOffsetUtf16,lengthUtf16,text}`；路径仓库相对，行号1起且末行含入，offset0起且半开区间 |
| bodyPresent | true时text含完整声明和body；false时仍有真实接口/abstract/native声明 |
| modifiers / annotations | 准确源码属性；用于判定是否需查询候选，不做Spring注入实现选择 |
| controls | `{kind,expression,sourceRange,parentControlIndex}`；IF/ELSE/TRY/CATCH/FINALLY/LOOP/SWITCH/SYNCHRONIZED，可空 |
| exits | `{kind,expression,sourceRange}`；RETURN/THROW，表达式可空；不推导运行结果 |

controls/exits是语法导航提示，不是CFG Proof。全部条件与return仍在完整text里，缺结构化增强不删除原文。lambda/匿名类body必须保留；其延迟执行语义见[JDT模块](jdt-engine.md)。来源检查只保证对应冻结字节，不能因此升级业务含义。

### 3.2 调用与候选字段

| 字段 | 约束 |
| --- | --- |
| CallSite.callKey / callerMethodKey | 调用位置唯一；相同表达式在两行必须是两个callKey |
| kind | `METHOD / SUPER_METHOD / CONSTRUCTOR / THIS_CONSTRUCTOR / SUPER_CONSTRUCTOR / METHOD_REFERENCE / CONSTRUCTOR_REFERENCE` |
| site | 相对path与起止offset/line；调用expression完整范围，不只方法名位置 |
| navigationSite | 必填的独立SourceRange；调用名/构造目标/引用目标的准确UTF-16范围，来自AST，不从expression字符串重算；无法获得可靠range时为null并明确诊断，不发送错误位置查询 |
| expression / receiverExpression | 源码原文；无显式receiver为null |
| actualArguments | 有序 `{ordinal,expression}`；`f(id,id)`保留两个位置；方法引用无实际调用实参，写空数组 |
| enclosingControlIndexes | 指本方法controls；源码作用域，不代表路径可达证明 |
| deferred | lambda/方法引用等延迟语法为true；不画成已执行的顺序调用 |
| targets | TargetCandidate[]；可空、多项；声明目标和实现候选分开 |
| resolution | `LOCATED / CANDIDATES / UNRESOLVED`；LOCATED仅表示导航目标唯一，不等于运行时exact |
| resolutionDetail | 具体工具限制或来源，不放原始LSP对象 |

每个 TargetCandidate 保存：

| 字段 | 约束 |
| --- | --- |
| methodKey | 仓库内方法记录引用；只有类型、外部库或无源码时可null |
| roles | 非空去重数组，元素`DECLARATION / IMPLEMENTATION`；同一有body的concrete声明可兼任，合并集合但不重复复制源码 |
| displayName | 工具提供；外部或类型目标仍保留说明 |
| navigationKinds | 非空去重数组，元素`CALL_HIERARCHY / DEFINITION / IMPLEMENTATION / ENGINE_BINDING`；同一位置经不同查询取得时合并保留 |
| expansion | `BODY_INCLUDED / DECLARATION_ONLY / EXTERNAL / UNRESOLVED / NOT_EXPANDED` |
| reason | BODY_INCLUDED允许null；其他必须给具体原因，不把“未请求”叫做“工具没找到” |
| argumentAssociations | `{actualOrdinals,formalOrdinal,kind}`，kind=`POSITIONAL / VARARGS / UNKNOWN`；formals来自该候选MethodCode |

可变参数用多个actualOrdinals关联一个formalOrdinal，不丢实参。实际与形参数量不一致、只找到类型、方法引用时保留原文并标UNKNOWN，不实现重载/类型转换算法。参数对照不是完整跨方法数据流证明；不能据此制造SQL列映射。

位置字段统一使用`SourceRange(startOffsetUtf16,lengthUtf16,startLine,endLine)`，方法内controls/exits的范围继承MethodCode的path，调用site/navigationSite继承调用文件；对外不把character offset称为byte offset。candidate按真实目标位置去重，roles/navigationKinds合并为集合，其余事实冲突则保留NAVIGATION_CONFLICT而不是后写覆盖。

### 3.3 supportingSources、limitations和技术增强

`supportingSources`为来源安全的字段声明、初始化段、配置或MyBatis XML片段：`{kind,source,relatedMethodKeys,reason}`。只附实际定位到的内容；XML由既有安全解析能力读取，不算另一种Java引擎。没有XML不阻断Java源码材料。配置实际取值未知必须保留未知。

`limitations`为`{code,detail,methodKeys,callKeys}`；每项须指实际受影响对象，仓库级问题允许两个列表为空。能力不足、依赖不完整、预算停止、候选歧义均可为局部限制；source漂移/越界URI不是局部弱置信度，必须拒绝相关结果。

`technicalEnhancements.availability`仅`AVAILABLE / NOT_PRODUCED`；AVAILABLE提供真实同源引用，NOT_PRODUCED必须非空reason且refs空、flowRef=null。现有strict图/Fact不转换成导航Candidate，导航Candidate也不伪装为旧EXACT Proof。没有strict Flow不代表没有EntryCodeContext。

## 4. 数量与完整性：只留下必要检查

- 每个发现入口恰好一个上下文或一个明确未收集处置。
- 每个取到正文的可调用声明中，每个支持枚举的语法调用点恰好一条CallSite；重复方法正文去重，调用发生点不去重。
- 每条工具返回的仓库内候选有源码记录或具体未展开原因；不只保留第一个。
- 所有method/call/source引用可查；所有已保存text与冻结源码一致。
- lambda、方法引用、构造器与未知语法不能从coverage中静默消失。
- 这些检查不要求某个入口解析率100%，不要求每个中文业务原子有Proof，不重跑生产导航来验证一次保存。

一次工具错误与空合法返回不同：RPC error/timeout记录对应失败诊断；[]表示工具未提供目标。崩溃或损坏响应不能返回一个“没有调用”的成功context。已经完成的上游文件保留，不建设同进程崩溃接管。

## 5. 保存格式、位置与复用

Step03保存`java-code-index.jsonl`，记录类型仍为`ENGINE / TYPE / METHOD / CALL / ENTRY_MEMBERSHIP / DIAGNOSTIC`。每条外壳为`{schemaVersion, recordType, key, payload}`，schemaVersion为`java-code-index-v2`。ENGINE恰一条，payload保存descriptor、snapshotRef和technicalEnhancements；METHOD按全仓methodKey去重且正文必须一致。DIAGNOSTIC保留文件/工具问题。该文件不是新的“第九步”。

**调用位置是全仓身份，调用的展开结果属于本次入口上下文，两者不能混为一个全仓对象。**例如两个入口均经过`Service.load()`中的同一调用：入口A已收集目标正文，入口B在到达目标前触及遍历限制。A的`BODY_INCLUDED`与B的`NOT_EXPANDED`可以同时为真，不能要求整条CallSite相等，也不能拿A的完整结果覆盖B的限制。

- CALL的payload精确为`{entryId,call}`，call仍是未改动的`EntryCodeContext.CallSite`。记录key为`entry-call:`加SHA-256，输入依次为三个带8字节big-endian字节长度前缀的UTF-8字符串：`entry-call-record-v1`、entryId、物理callKey。该key只标识保存记录，不替换源码调用位置callKey，不进入模型业务语义。
- ENTRY_MEMBERSHIP的payload仍为`{seed,collectionStatus,reason,methodKeys,callKeys,supportingSources,limitations}`；seed携带entryId和精确入口位置，callKeys仍按原顺序保存物理调用key。reader用该entryId及每个callKey找到唯一所属CALL，不跨入口借用。COLLECTED恢复原始方法集合、完整调用投影和限制；NOT_COLLECTED必须有原因，方法、调用、补充来源和限制列表为空。
- 同一物理callKey的源码固有字段仍必须一致：callerMethodKey、kind、site、navigationSite、expression、receiverExpression、actualArguments、enclosingControlIndexes、deferred。不同入口的targets、resolution、resolutionDetail允许不同并原样保留。来源固有字段冲突、同一入口重复调用key、错误owner、缺少或悬空CALL仍拒绝；不是用first-win、last-win或候选并集隐藏冲突。
- publisher与reader均执行上述规则。重开不查询JDT、不扩展方法集合，也不提升任何入口的展开状态。记录排序仍按recordType固定顺序，再按key的UTF-8字节序；membership内部列表保留该入口原顺序。
- v1的全仓唯一完整CallSite假设已被真实整仓运行否定。仅导航索引及其直接policy/readers升级v2，内嵌context、Step05和模型材料结构不变；历史v1文件保留且不静默转换。

最小回归使用两个不同入口共享同一调用位置：A保留正文、形参关联，B保留未展开原因，保存重开后二者逐字段不变；再把B的实参源码改成不一致值，必须拒绝。先完成这项公开发布/读取接口验证，再重新执行整仓导航，不以整仓长跑代替此检查。

Step05在`flow-slices.json`的`entryContexts`保存入口归属、收集状态与`codeContextRef`，不再复制索引的完整方法正文。该ref精确为`{indexArtifact: ArtifactReference, entryId: string}`，COLLECTED必填且entryId与外层相同；NOT_COLLECTED为null并有reason。Capsule原内嵌`entryContext`改为`entryContextRef={compilationArtifact: ArtifactReference, entryContextId: string}`。既有reader一次打开引用的索引/compilation并恢复完整不可变视图，不重新导航，不把裸引用交给模型。METHOD正文仍以索引为权威；技术增强不改变。[完整读写与失败例子](../../plans/navigation-reuse-and-readable-report-design.md#6-step05capsule用已保存索引引用代替正文副本)。

以下版本表记录已交付的引用持久化合同。仅改变实际持久化形状的owner/readers/policy同步升版，JavaParser同步产出该保存格式，不改它的解析算法。

| 内容 | 已交付 schema | 优化目标 |
| --- | --- | --- |
| 导航索引 | java-code-index-v2；CALL按入口保存 | 不变；不把RPC缓存写成入口投影 |
| 解引用后的完整上下文 | entry-code-context-v1 | 不变；内存消费者仍得到完整内容 |
| module compilation | business-flows-flow-compilation-v6 | codeContextRef |
| module projection | business-flows-capsule-projection-v11 | entryContextRef |
| 公开flow-slices | business-flows-flow-slices-v6 | codeContextRef |
| 公开Capsule | business-flows-evidence-capsule-v9 | entryContextRef |
| flow覆盖 / 入口处置 | business-flows-flow-coverage-v2 / business-flows-entry-disposition-v2 | 不变 |
| Fact accounting | proven-code-facts-fact-accounting-v4；NOT_PRODUCED时reason必填，counts为null | 不变；不含伪造Fact/Proof refs |

可用性保存在索引ENGINE记录及对应Fact accounting中；现有generic step receipt仍通过实际artifact descriptors引用它们，不给每层receipt新增一套状态。只有实际产物集合变化的拥有者和readers调整，不全工程schema重置。

历史产物不覆盖，旧context版本用稳定`UNSUPPORTED_CODE_CONTEXT_VERSION`拒绝，不能缺新字段就当空列表。新语义下游不提供旧wire双读/别名；第二阶段JavaParser生产新格式，而非读取旧格式冒充。

### 5.1 实际产物集合与现有存储复用

`java-code-index` 注册为 `PROGRAM_GRAPHS` module 7，artifact type 为 `PROGRAM_GRAPHS_JAVA_CODE_INDEX`，schema 为 `java-code-index-v2`，media type 为 JSONL，sensitivity 为 `METADATA_ONLY`。沿用现有 module/step store、原子发布、run registry 和两种 receipt；不建立新 step、存储层或生命周期。

| 选择与步骤 | 合法的实际语义 payload | receipt之外不得出现 |
| --- | --- | --- |
| JDT / Step03 | `java-code-index.jsonl` | 五图占位文件 |
| JDT / Step04 | `fact-accounting.json` | candidate/fact/proof占位文件 |
| 任一引擎 / Step05 | `flow-slices.json`、`flow-coverage.json`、`entry-dispositions.jsonl`、`evidence-capsules.jsonl`、`flow-gaps.jsonl` | 另一套context别名或旧版wire |
| JavaParser / Step03（第二阶段） | `java-code-index.jsonl` 加当前七个实际五图/证据图语义文件 | JDT结果或未运行文件 |
| JavaParser / Step04（第二阶段） | 当前四个严格Fact语义文件，accounting为v4且`AVAILABLE` | 旧v3 accounting |

module publisher、step publisher、exact-set allowlist、`CanonicalArtifactPolicyRegistry`、直接 reader 与测试 fixture 必须把同一实际集合视为一个原子合同。`NOT_PRODUCED` 是 accounting/index 内容中的能力状态，不是给 receipt 新增通用状态机；已声明 AVAILABLE 的损坏产物仍是失败。

Step06 SourceRef编号仍由Builder分配，模型只看短ref和正文；methods/calls全局key、路径、行号、engine信息留在程序侧。一个context可含多个方法，不强制每方法单独生成一个业务活动。Builder在一个请求内复用同一完整方法，但不同请求必须各自含必要正文；请求快照和最终source-refs是有意保留的自包含投影，不因为技术存储去重而只发送模型无法解开的key。

普通同进程传immutable view，保存时检查来源/引用/结构及原子写入。跨进程重开检查bytes/schema/identity；不再索引一遍。复用基础包含snapshot、有效源码根/语言级别/本地classpath内容、引擎与版本、context合同及取材选项；排除绝对工作根。改引擎/工具版本后不得复用旧解析结果或旧业务候选。旧语义Prompt/模型授权规则仍生效。

## 6. 测试与实现指南

Luna先写配置闭集、全方法持久化、重复调用位置、候选保留、参数顺序、来源重开、缺项可见和下游不知工具的行为测试。JDT内部协议测试可以用录制响应，但它只能证明接线；真实工具的两个冻结入口检查证明导航有效。

Terra按本页字段实现，不在DTO里塞Eclipse对象、同义字段、双版本fallback或业务解释结果。配置加载仅在组合根；Builder没有`if(jdt)`/`if(javaparser)`分支。schema/producer/reader/fixture一次更新；不要让测试固定住旧窄CallContext阻止合理升级。

协议无法表示实际候选或构造器时交设计者调整，不删除工具返回以使测试通过。第一阶段不以JavaParser适配完成作门禁；第二阶段不要求JavaParser产生当前未有的图边或方法正文。
