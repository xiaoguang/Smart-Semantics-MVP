# JDT引擎：各子模块详细设计

> [总设计](README.md) / [统一字段](contracts-and-configuration.md)。本页负责“怎么得到代码”，不负责业务解释。所有子模块产品模型调用为0。

## 1. JdtProjectSession：把完整固定仓库交给一个索引

### 为什么需要

JDT必须看见仓库中的类型和源码根，才能从 Controller 的 `UserService` 找到真正的Service。只把Controller片段交给解析器，或者把不同Maven module的同名类混在一起，都会降低导航质量。

### 输入与操作

输入为 `VerifiedJavaProject`：完整冻结inventory、原有源码根/POM静态画像、明确Java语言级别、允许读取的本地依赖清单。沿用现有安全POM读取，不执行Maven、Gradle、annotation processor、客户插件或应用，不自动下载依赖。

1. 在Agent私有workspace建立源码投影，逐文件保持原始内容，维护“投影URI ↔ 原始相对路径”的唯一映射。客户的`.project/.classpath`不能直接控制宿主；由程序从静态画像生成受控Java项目配置。
2. 一个确定的module一个项目；已知本地module关系写入项目引用，源码根只取inventory。未知parent/property/profile或缺依赖标诊断，不用扫描全盘/Maven仓库来猜classpath。
3. 启动固定JDT LS版本。禁止Maven/Gradle import、自动构建、annotation processing、下载源码和联网配置更新；使用进程/网络隔离保证不能运行客户构建，仅设置一个flag不算安全保证。
4. initialize完成后等待索引可用并做一次声明查询检查。不能将任意sleep时间当作“全部绑定已经完备”。索引部分失败需保留按项目/文件诊断；进程未初始化成功为运行失败。
5. 全仓入口复用这一会话与索引；结束关闭LS和Core helper。新run可复用已验证产物，但本设计不新增持久化LS workspace恢复/锁接管。

JDT启动JDK和客户source level不同：当前调研使用LS1.61.0与JDK26；官方LS要求Java21或更新版本。应用本身保持Java17，不能因为工具要新JDK就偷偷把客户代码按Java26语法解释。[官方1.61说明](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/README.md)

### 输出、失败、下游保证

输出私有 `JdtProjectSession`、URI映射、工具版本和环境诊断，不返回JDT raw handle到下游。每个能取回的URI必须映射到固定来源；`jdt://`库源码只用于判定外部边界，不把它偷渡成客户源文件。

- 缺外部依赖：部分绑定未知，已存在的仓库源码仍可读。
- 多module重名/未知依赖：候选与限制保留，不选择第一个同名类。
- 返回URI越过投影、源码字节漂移、索引执行客户构建：fatal，拒绝相关运行。
- JDT工具缺失或不能启动：明确工具错误；不启用JavaParser。

**例子：**UserController仍保留`import com.jsh.erp.service.*`。程序不把`UserService`拼成Controller包路径；原始import和仓库Service源码一起进入JDT工程，调用位置交由JDT定位。

### Luna RED / Terra GREEN

测试固定源码投影、同一索引处理两入口、客户工作区改变不影响输入、未知依赖不吞源码、不同module同名类型、禁止构建/联网。实现只做现有冻结来源→JDT项目的适配，不实现POM插件执行或完整Maven模型。

## 2. JdtSyntaxReader：由Core取完整方法和每个调用位置

### 输入输出

输入一个经过验证的Java文件文本和source level，输出声明/语法位置记录。主应用Java17拥有来源验证；一个常驻helper运行在固定工具JDK上，Core classpath固定为审阅过的依赖闭包，不把整个未知plugins目录作为可执行扩展平台。

helper 是同一工程中独立构建的工具产物，不进入宿主 Java 17 classpath。源码固定放在 `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/`，其独立 Maven 工程生成自包含的 `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar`；生产版本固定 JDT Core 3.47.0 与 Jackson 2.21.4。宿主只用 `${jdt.javaHome}/bin/java -jar <上述产物>` 的参数数组通过 `ProcessBuilder` 启动，禁止 shell、`java` PATH 查找或当前 JVM 代替 tool JDK。

私有协议只需要一个操作：

```json
{
  "protocolVersion": "jdt-syntax-v2",
  "operation": "DESCRIBE_COMPILATION_UNIT",
  "requestId": "parse-1",
  "sourceKey": "source:user-controller",
  "languageLevel": "8",
  "sourcepathEntries": ["受控投影下的源码根"],
  "classpathEntries": ["已经验证并显式批准的本地依赖"],
  "text": "本字段在真实请求中必须是完整冻结Java文件原文"
}
```

这是协议形状示例，不是可执行源码。响应含`protocolVersion, requestId, declarations, callSites, controls, exits, diagnostics`，各位置沿用[统一字段](contracts-and-configuration.md)。请求不传仓库根供helper自行扫描，也不给它源码目标答案。JSON字符串正确转义换行，一条请求/响应一行；stdout仅协议、stderr限量诊断，用现有Jackson序列化，不手拼JSON。未知operation/版本/字段或进程断流为工具错误，不装作空语法树。

`jdt-syntax-v2` 的传输语义也属于冻结合同：同一进程同一时间恰有一个请求在途，成功响应必须逐字回显 `protocolVersion/requestId`；stdout 的每一非空行只能是一条响应，stderr 有上限且只作诊断。单请求超过配置的正值 query timeout 时不重试，终止 helper、使 session 不可继续并报 `JDT_SYNTAX_TIMEOUT`。非 JSON、未知字段/版本、requestId 不符或请求在途时 EOF 报 `JDT_SYNTAX_PROTOCOL_INVALID`；意外非零退出报 `JDT_SYNTAX_PROCESS_FAILED`。正常关闭先关 stdin、在 shutdown timeout 内只接受 exit 0；超时则强杀并报 `JDT_SYNTAX_SHUTDOWN_TIMEOUT`。这些状态都不能返回空 declarations 冒充成功。

### Core内部算法

使用 `ASTParser` 的 compilation-unit模式，`setSource(char[])`，明确compiler source/compliance，启用方法体。helper只接受会话已经验证的源码投影根和显式批准且校验过内容的本地classpath；它启用JDT Core binding，仅把确定解析到的注解限定名投影给catalog，以支持外部通配import。调用目标、定义和实现候选仍完全由LS导航决定，helper不按绑定建立第二张调用图，也不自行扫描依赖。DOM API版本取固定Core支持版本；**DOM级别、源码语言级别、工具JDK版本不是同一个概念**。

从AST节点的start/length截取**原文**；禁止用`ASTNode.toString()`重新排版后当原文。每文件每有效source level只解析一次并缓存；缓存键至少包括源码内容、Core版本和language level。

| Core节点/语法 | 收集什么 | 不能宣称什么 |
| --- | --- | --- |
| MethodDeclaration | 方法、普通/紧凑构造器、完整声明、参数、body与修饰符 | 接口声明不是实现正文 |
| MethodInvocation / SuperMethodInvocation | 调用原文、receiver、名称位置、有序实参 | 不在Core helper中按名字找callee |
| ClassInstanceCreation / ConstructorInvocation / SuperConstructorInvocation | new/this/super的实际位置、参数和匿名类关联 | 不把class范围当构造器body |
| 方法/构造引用 | 引用目标位置与原文、deferred=true | 引用不是当场执行，实际实参未知 |
| LambdaExpression / 匿名类方法 | 单独可调用范围、完整原文和内部调用归属 | 不把内部调用标成外层方法必经步骤 |
| Initializer / 字段initializer | 完整源码范围及内部调用 | 不推断动态类初始化/注入顺序 |
| EnumConstantDeclaration | 显式参数/匿名体及构造导航位置 | 隐式生成构造器没有可伪造源码 |
| if/else、try/catch/finally、循环、switch、return/throw | 语法范围、嵌套作用域、条件/结果原文 | 不是CFG可达性、异常类型传播或路径证明 |

遍历外层方法时不把嵌套lambda/类方法的调用误归属外层；对这些可调用范围另建记录并保留enclosingMethodKey。lambda和初始化段没有Java方法名时name允许null，kind明确。匿名类方法仍是METHOD，声明源码决定归属。

支持语法枚举的每个调用点必须进入记录。AST恢复节点可作为带`PARSE_RECOVERED`的源码上下文，不能拿不可靠范围造调用目标；文件解析严重失败时保留入口原文及具体`SYNTAX_UNAVAILABLE`，不能写“无调用”。

### 位置处理

callSite同时输出整expression的site和名称/目标的navigationSite：例如`receiver.m(arg)`查询m的位置，`new Type(arg)`查询Type位置，`x::run`查询run的位置。this/super构造及enum常量各由对应AST节点提供位置。禁止下游对字符串做indexOf重建，以免同名参数、嵌套调用和注释选错位置。位置不可可靠提取就记录限制，不能发一个近似位置后把错误结果说成JDT能力不足。

JDT DOM的offset是UTF-16 code unit，LSP协商也固定UTF-16，行/character从0开始；对外人类行号从1开始。根据同一原始解码文本的line-start表换算，不混用UTF-8 byte offset。CRLF、中文、emoji、BOM都按真实文本处理，不先normalize换行。来源字节哈希留在既有来源层。

文档定位若只给方法名，按准确位置找包含它的声明，不按附近文本、方法名全仓搜索或括号计数猜范围。LS full range与Core声明边界可能因Javadoc范围不同而不完全一样，允许确定的注释范围差异；必须指向同一声明，不允许越到另一个方法。

### catalog注解identity：发现器不能暗中用JavaParser补

这项职责在JDT Adapter内，使用同一SyntaxReader和LS会话，不新增扫描器。Core返回package、imports（含onDemand/static标记）、AnnotationView的完整范围及nameSelection。对已写全限定名可保留该源码名；需要确认声明身份的注解，在nameSelection上询问LS definition并归一化位置。

Core优先使用相同受控sourcepath/classpath解析注解binding；确定解析成功才填qualifiedName。仍未解析时可询问LS definition，并只接受可以归一为确定声明身份的返回；不得从本机缓存路径、simple name或“annotation包”惯例猜包。对源码内自定义组合注解，递归读取其元注解，按声明位置去重防环；只应用既有支持的Spring组合规则，未知AliasFor/动态表达式保留限制。

在通配import或缺依赖下LS仍不能确认时，qualifiedName=null并记录`ANNOTATION_IDENTITY_UNRESOLVED`。Spring消费者保留该已定位mapping候选方法、原注解及具体限制，允许后续读取其源码；不能把未确认候选宣布为准确HTTP路由，也不能从发现site/材料范围中静默过滤。合法省略method仍是UNRESTRICTED，和注解identity无法确认不是同一种问题。

Luna须测试显式全名、通配import、同名自定义注解、源码组合注解及循环、缺依赖时候选保留；Terra不得以正则simple-name匹配或旧JavaParser发现器补齐JDT路径。

依据：[ASTParser](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)、[ASTNode范围](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTNode.html)、[MethodDeclaration](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodDeclaration.html)、[MethodInvocation实参](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodInvocation.html)。这里采用的是现有AST API，不开发Java解析器。

### Luna RED / Terra GREEN

直接测试整方法含注解/Javadoc/条件/返回、重载的准确位置、构造器、varargs、嵌套lambda归属、中文/emoji/CRLF切片、两个同名调用不同位置。JDTCore-only路径必须在无JavaParser依赖的helper中运行。不要用大仓失败后反复补案例词表来验证位置基础错误。

## 3. JdtNavigationResolver：让JDT决定调用连接谁

### 输入与请求序列

输入为具体调用点及其navigation位置，不是字符串`UserService`。先对包含方法使用`prepareCallHierarchy`、`outgoingCalls`，得到批量提示。按fromRanges关联真实语法调用点，保留call-site occurrence；不能只按display name匹配。

针对每个调用点：

1. 收集匹配的hierarchy目标；匹配不足、返回非方法或目标不明时对调用名称位置请求`textDocument/definition`。
2. 归一化标准`Location[]`、`LocationLink[]`与客户端LSP4J包装。处理代码面对的是库对象，不把`left/right`包装当作LSP在线协议。未知形状保存诊断并报适配错误，不作“JDT找不到”。
3. **声明是接口、abstract、非final可覆盖实例方法，或目标属性无法确定时，仍查询`textDocument/implementation`。** 已有hierarchy返回不允许提前退出。静态/private/final方法不为不存在的动态分派展开候选。
4. 将声明与实现候选按同源位置去重，合并`roles`和`navigationKinds`集合；多个实现全部记录。即使只发现一个实现也不宣称它一定是Spring实际bean。
5. 用SyntaxReader读取候选声明和body。LS位置落在类型而非构造器时，不随机选一个构造器；继续用明确构造调用位置查询，仍不明确则保留`CONSTRUCTOR_TARGET_UNRESOLVED`。

可在同一会话缓存相同位置的definition/implementation结果，不重复查已处理位置。是否需要额外查询由工具返回类型决定，不针对`registerUser`等案例硬编码。

### 输出与边界

输出`NavigationResult`只含目标/候选位置、定位来源、外部或未解析原因。不给业务名称、角色或实际执行结论。

| 实际结果 | 保存的意思 |
| --- | --- |
| 仓库concrete method正文 | 可以继续展开它的静态调用 |
| interface声明+仓库实现候选 | 声明与全部候选都保留；未知实际dispatch |
| Mapper声明，implementation无仓库正文 | 停在接口边界，附实际声明及参数；不是分析器崩溃 |
| 找到外部library符号 | 保留目标标识/调用参数，不展开不属于固定客户仓库的源码 |
| 返回空位置 | UNRESOLVED，保留原始调用；不猜当前包+类型名 |
| RPC错误或超时 | 明确QUERY_FAILED；不把未查询成功记成目标不存在 |
| 多工具请求给出冲突位置 | 保留候选和NAVIGATION_CONFLICT，不选第一个 |

**真实例子：**Controller中的`userService.registerUser(ue, manageRoleId, request)`在调研hierarchy里遗漏，但definition返回了UserService方法名位置。这是LS另一条导航能力成功，不是人工补Service。正式模块要把这类兜底作为通用导航流程；不同于“失败后自动换另一个引擎”。

静态导航不能证明“这段Service确实在某次请求运行过”。调用树展示可能涉及的源码和条件；是否执行由原代码条件/运行时决定。调用/控制/数据图均不是动态运行结果。

### Luna RED / Terra GREEN

以真实录制位置验证Location与LocationLink、empty/error区分；hierarchy已有interface也会询问implementation；两实现都保留；wildcard/static import由LS解析；构造器不误取class正文。所有测试通过后用相同两入口验证真实工具，不把录制fixture通过当作JDT实测。

## 4. EntryCodeCollector：连成一个可读对象，不再制造证明层

### 处理步骤

1. 输入真实EntrySeed，取入口完整MethodCode。
2. 对该body的每个调用执行resolver；返回的仓库内concrete候选进入工作队列。
3. 按稳定源码位置顺序遍历队列，方法正文以methodKey去重。每个调用点及目标关系仍独立保存，递归/互递归通过key引用，不递归复制无限正文。
4. 取形参与有序实参做位置对照，保留原文；类型适配/重载选择交LS，不自己证明数据流。多候选分别关联，不能拿A实现的形参配B实现。
5. 记录外部、无实现、未知目标及停止原因；lambda/回调引用保留deferred，不能排成业务顺序。
6. 入口上下文引用全部已取得methods/calls、必要字段/配置声明与限制，交给Step03保存索引、Step05组织发布。

示例输出不是一串“Controller→Service→Mapper”文字，而是可以直接展开方法代码的图状集合。共用方法只存一次，路径归属保持多对多，两个调用相同Service不会互相覆盖。

### 完整与停止的定义

“完整”指：在本次声明的源码范围内，对每个已取body的可枚举调用点都查明了导航结果或明确停止原因，不保证找到所有动态调用，不保证每个库都有源码。

默认不设置一个为节省token而只走两层/几个方法的固定上限。整仓索引与代码取材不调用模型；输出仍遵守宿主内存、单次RPC超时和用户取消这些运行安全限制。若限制触发，保留已取结果及未展开调用点，明确`NOT_EXPANDED`影响的入口，不能称为完整。

未提供的外部依赖/生成代码不会临时运行客户构建补齐。普通库调用、代理Mapper边界不会迫使整个入口失败；非法来源或工具协议损坏才是阻断问题。

### 下游保证

Step05不再重跑JDT或解析器，只归属/保存已经取得的关系。Builder无需再查“缺少的Service”——它应在本模块交付物中，或者有准确原因；若有body却没传到模型，是组包错误，不是导航缺口。

验收同时读取保存的context和实际模型请求：注册三段Service不能只存在技术索引中。直接业务callee优先完整进入核心包；若所有相关方法不能放进一次请求，按完整方法组合并如实记录未进入该包的方法，不切掉关键条件或假称完整。如何选择可读单元归Builder，不能由Collector按行业词猜哪些方法重要。

### Luna RED / Terra GREEN

测试重复方法、重复调用位置、循环、多入口共享、interface多候选、构造器、无body边界、部分取消与源码定位；再以真实注册/财务和一个不同结构的fixture验证。禁止为达到expected count人工加入Service路径，禁止把非空body全部压成label后宣称完成。

## 5. 目前尚未实测的部分

研究已证明两入口的LS定位价值，但独立Core helper、构造器覆盖、虚调用实现候选、全仓多module隔离、YAML生产接线尚未完成。它们是第一阶段的实现及测试任务，不写成“JDT试验已全部通过”。

两种工具采用同一source level与来源规则，但不要求返回同一数量/精度。JDT第一阶段稳定后，才按照[第二阶段设计](integration-and-javaparser.md)适配JavaParser现有能力。
