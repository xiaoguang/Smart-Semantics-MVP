# JDT引擎：各子模块详细设计

> [总设计](README.md) / [统一字段](contracts-and-configuration.md)。本页负责“怎么得到代码”，不负责业务解释。所有子模块产品模型调用为0。C2逐模块classpath/目标平台接线、真实LS回读、双模块catalog与跨模块collect已由直接测试验证；旧正式三命令也已在固定源码保存Step02/03结果。逐调用绑定归属、外部边界与查询失败的窄修正已有直接测试和新 Java 索引 v3 发布／重开测试；**固定客户源码的具名错边尚未用本轮四命令重验**，不能把测试通过写成真实准确性通过。旧结果与局部查询失败见[固定源码验收](../../supplements/technical-analysis-fixed-source-acceptance.md)。官方Maven导出由用户或获明确授权的Agent执行，Java只消费结果，见[Step02交接](../technical-analysis/dependency-preparation.md)。

## 1. JdtProjectSession：把完整固定仓库交给一个索引

### 为什么需要

JDT必须看见仓库中的类型和源码根，才能从 Controller 的 `UserService` 找到真正的Service。只把Controller片段交给解析器，或者把不同Maven module的同名类混在一起，都会降低导航质量。

### 输入与操作

现有`VerifiedJavaProject`可保留一份来源身份并为模块划分源码、验证已列JAR存在/hash；它不证明Maven依赖完整。`JdtCodeEngine.open(JavaCompilationEnvironment)` 将 readiness 已准入的同一模块项目、直接源码模块边和目标平台适配为一个多 project workspace；`catalog()/collect()` 经该工作区的源码所有者路由使用逐模块 Core helper。入口仍须在合并 catalog 中以 methodKey、完整 range 和唯一 sourcePath 同时定位；没有从 `EntrySeed` 推断路径。

目标输入由Step02从逐模块官方classpath/有效POM中读取具体设置、绑定R0与明确的目标JDK后提供：来源/有效范围、有序依赖、源码模块关系和target platform。Agent只交文件位置及明确选择，不手填语义；Java不重新求值父POM/Profile/BOM、下载或启动Maven。Maven本身可能加载扩展，执行权限由用户决定。JDT会话不联网补包或运行客户脚本、annotation processor或应用。v2直接输出交接的当前状态见[技术配置](../technical-analysis/cli-and-runtime.md#2-技术配置与外部编译输入)，引擎不另建第二条Maven导入路线。

1. 在Agent私有workspace建立源码投影，逐文件保持原始内容，维护“投影URI ↔ 原始相对路径”的唯一映射。客户的`.project/.classpath`不能直接控制宿主；由程序从静态画像生成受控Java项目配置。
2. 目标为一个所选module一个受控project，显式确认的本地module关系写入项目引用；按模块导出的classpath和目标JDK隔离，不能取全仓并集。导出缺失、JAR不可读、来源/项目不符或JDK绑定失败时阻断；Maven原始失败交由Agent解释。源码和支撑材料来自准入输入，不扫描全盘猜classpath。
3. 启动固定JDT LS版本。禁止Maven/Gradle import、自动构建、annotation processing、下载源码和联网配置更新；使用进程/网络隔离保证不能运行客户构建，仅设置一个flag不算安全保证。
4. initialize后逐项目回读并核对目标JDK、投影源码根、显式 library classpath 和受控 workspace project 引用；虚拟 project 路径只按精确受控项目名验证，真实源码根/JAR 仍用 canonical real path。必要环境有效时可提供catalog/collect，保存并返回实际收到的诊断，同时披露诊断覆盖未确认。现有客户端可记录`publishDiagnostics`事件，且诊断记录不占用同步 query monitor，避免 diagnostics 先于 response 到达时阻塞 JSON-RPC；但没有全文件终态证明，本轮不为此自建协议，也不把无消息解释为零诊断或编译正确。
5. 全仓入口复用这一会话、Core语法缓存、原始导航查询缓存和方法表；不同入口保留独立成员与展开状态。结束关闭LS和Core helper并释放缓存。新run可复用已验证规范化产物，不复用LS handle，不新增持久化LS workspace恢复/锁接管。

缓存只在所选环境与项目绑定后启用。源码、模块/源码根、language level、本地classpath内容、工具/Adapter版本或显式环境选择变化都需要新会话；注解binding缓存也受classpath影响。会话内相同操作/位置只查询一次的精确键、错误复用和比较指标见[已批准优化设计第3–5节](../../plans/navigation-reuse-and-readable-report-design.md#3-jdtprojectsession一次会话拥有复用范围)。该优化已经实现并通过固定四入口对照。

JDT启动JDK和客户source level不同：当前调研使用LS1.61.0与JDK26；官方LS要求Java21或更新版本。应用本身保持Java17，不能因为工具要新JDK就偷偷把客户代码按Java26语法解释。[官方1.61说明](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/README.md)

### 输出、失败、下游保证

输出私有 `JdtProjectSession`、URI映射、工具版本和环境诊断，不返回JDT raw handle到下游。每个能取回的URI必须映射到固定来源；`jdt://`库源码只用于判定外部边界，不把它偷渡成客户源文件。

- 历史缺依赖路径可能返回部分绑定/错误候选，旧材料仍可按旧身份查看；新目标在Step02拦截已知未就绪，不继续产生新调用索引。
- 多module重名/未知依赖：候选与限制保留，不选择第一个同名类。
- 返回URI越过投影、源码字节漂移、索引执行客户构建：fatal，拒绝相关运行。
- JDT工具缺失或不能启动：明确工具错误；不启用JavaParser。

**例子：**UserController仍保留`import com.jsh.erp.service.*`。程序不把`UserService`拼成Controller包路径；原始import和仓库Service源码一起进入JDT工程，调用位置交由JDT定位。

### Luna RED / Terra GREEN，Sol设计与debug

测试固定源码投影、同一索引处理两入口、客户工作区改变不影响输入、缺少或不可读的导出路径返回报告、不同module同名类型、禁止JDT构建/联网。新增环境投影接收Step02核验的逐模块导出结果，不在Java内重做POM规则；LS/Core目标平台必须一致。

## 2. JdtSyntaxReader：由Core取完整方法和每个调用位置

### 输入输出

输入一个经过验证的Java文件文本和source level，输出声明/语法位置记录。主应用Java17拥有来源验证；一个常驻helper运行在固定工具JDK上，Core classpath固定为审阅过的依赖闭包，不把整个未知plugins目录作为可执行扩展平台。

helper 是同一工程中独立构建的工具产物，不进入宿主 Java 17 classpath。源码固定放在 `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/`，其独立 Maven 工程生成自包含的 `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar`；生产版本固定 JDT Core 3.47.0 与 Jackson 2.21.4。宿主只用 `${jdt.javaHome}/bin/java -jar <上述产物>` 的参数数组通过 `ProcessBuilder` 启动，禁止 shell、`java` PATH 查找或当前 JVM 代替 tool JDK。

私有协议只需要一个操作：

以下是**当前v3协议的形状示例**。客户端/helper 已要求显式目标平台；工作区按源文件所属模块传递该模块的投影源码根、classpath、目标 JDK 版本和从该目标 home 解析的单一系统库归档（Java 8 及更早版本为 `jre/lib/rt.jar`，模块化 JDK 为 `lib/jrt-fs.jar`），不传 JDK 目录。

```json
{
  "protocolVersion": "jdt-syntax-v3",
  "operation": "DESCRIBE_COMPILATION_UNIT",
  "requestId": "parse-1",
  "sourceKey": "source:user-controller",
  "languageLevel": "8",
  "sourceSha256": "真实请求中的完整源码摘要",
  "sourcepathEntries": ["受控投影下的源码根"],
  "classpathEntries": ["已经验证并显式批准的本地依赖"],
  "targetJdkVersion": "8",
  "targetPlatformEntries": ["经核验的目标JDK平台条目"],
  "text": "本字段在真实请求中必须是完整冻结Java文件原文"
}
```

这是协议形状示例，不是可执行源码。响应含`protocolVersion, requestId, declarations, callSites, controls, exits, diagnostics`，各位置沿用[统一字段](contracts-and-configuration.md)。请求不传仓库根供helper自行扫描，也不给它源码目标答案。JSON字符串正确转义换行，一条请求/响应一行；stdout仅协议、stderr限量诊断，用现有Jackson序列化，不手拼JSON。未知operation/版本/字段或进程断流为工具错误，不装作空语法树。

`jdt-syntax-v3` 的传输语义延续单请求合同：同一进程同一时间恰有一个请求在途，成功响应必须逐字回显 `protocolVersion/requestId`；stdout 的每一非空行只能是一条响应，stderr 有上限且只作诊断。单请求超过配置的正值 query timeout 时不重试，终止 helper、使 session 不可继续并报 `JDT_SYNTAX_TIMEOUT`。非 JSON、未知字段/版本、requestId 不符或请求在途时 EOF 报 `JDT_SYNTAX_PROTOCOL_INVALID`；意外非零退出报 `JDT_SYNTAX_PROCESS_FAILED`。正常关闭先关 stdin、在 shutdown timeout 内只接受 exit 0；超时则强杀并报 `JDT_SYNTAX_SHUTDOWN_TIMEOUT`。这些状态都不能返回空 declarations 冒充成功。

当前`jdt-syntax-v3`已增加实际target platform输入，并用`setEnvironment(..., false)`禁止隐式工具JVM平台；宿主从已核验 target home 取 Java 8 及更早版本的 `jre/lib/rt.jar` 或模块化 JDK 的 `lib/jrt-fs.jar`，并在文件缺失、非普通文件或 canonical 路径逃出 target home 时 fail closed。未增加module/environment身份字段，宿主按文件所属模块提供正确的sourcepath/classpath/platform并维护 helper 缓存身份。JDK8 和 Java 17 的实际归档路径有定向验证；release语义和source≠target仍须按实际工具行为验证，不能从一个`languageLevel`推定。v2历史索引重开不运行helper；现行生产使用匹配的v3客户端/工具，目标新生产升级为v4协议。

### Core内部算法

使用 `ASTParser` 的 compilation-unit模式，`setSource(char[])`，明确compiler source/compliance，启用方法体。helper只接受会话已经验证的源码投影根和显式批准且校验过内容的本地classpath；它已启用JDT Core binding，但**当前仅投影注解类型身份**。目标v4再投影逐调用和声明的绑定身份，用于核对LS导航归属及确认外部边界；不按绑定另建调用图，也不自行扫描依赖。DOM API版本取固定Core支持版本；**DOM级别、源码语言级别、工具JDK版本不是同一个概念**。

从AST节点的start/length截取**原文**；禁止用`ASTNode.toString()`重新排版后当原文。每文件每有效解析配置只解析一次并缓存；缓存基础包括源码内容、Core版本、language level以及参与注解binding的受控sourcepath/classpath，不能在依赖变化后复用旧注解身份。

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

### 已批准的最小绑定投影（目标；现行v3未提供）

现行`CallSiteView`只有源码范围、名称导航位置、表达式、实参与归属；`Declaration`没有方法绑定。目标私有`jdt-syntax-v4`在**这两个既有记录**加可空的JDT绑定观察，不增第二个解析操作：对`MethodInvocation`用`resolveMethodBinding()`，其他已支持的构造、super及方法引用节点用其对应的JDT `resolve*Binding()`；对`MethodDeclaration`用声明绑定。仅非空、非`isRecovered()`的`IMethodBinding.getMethodDeclaration()`及其声明类型可形成`RESOLVED`身份。保留`ABSENT`、`RECOVERED`、`UNSUPPORTED_CALL_KIND`的具体状态，不能把恢复生成的key当可靠身份。注解原规则不变。[JDT调用绑定](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodInvocation.html)、[方法声明绑定](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodDeclaration.html)、[绑定恢复与相等](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/IBinding.html)。

投影最少包括声明binding key、声明类型binding key、声明类型来源`SOURCE/BINARY/UNKNOWN`和解析状态；用于展示的限定名/方法名只能来自已解析的JDT绑定，不能反向参与选择。`getMethodDeclaration()`消除泛型/原始调用绑定与其声明的形态差异；同一冻结会话、模块环境内比较`isEqualTo`或其key，不用对象`equals`，不把binding key当跨版本或跨环境稳定的公共methodKey。[JDT方法绑定](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/IMethodBinding.html)。`ITypeBinding.isFromSource()==false`仅在确认是类/接口等声明类型、非recovered且来自受控binary环境时支持外部判断；它对基本类型等也会返回false，不能单用作外部标志。[JDT类型绑定](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ITypeBinding.html)。原始AST仍只在helper内，宿主取得小的值投影，不持有Core对象。

方法/构造引用仍标`deferred`；绑定只标识所引用声明，不证明调用当场执行或实参。Lombok生成成员可能有绑定而无冻结源码声明；不得从binding制造`MethodCode`、原文范围或正文。若LS/Core对同一位置给出不一致身份，保留工具观察和冲突，不按字符串补正。

### 位置处理

callSite同时输出整expression的site和名称/目标的navigationSite：例如`receiver.m(arg)`查询m的位置，`new Type(arg)`查询Type位置，`x::run`查询run的位置。this/super构造及enum常量各由对应AST节点提供位置。禁止下游对字符串做indexOf重建，以免同名参数、嵌套调用和注释选错位置。位置不可可靠提取就记录限制，不能发一个近似位置后把错误结果说成JDT能力不足。

JDT DOM的offset是UTF-16 code unit，LSP协商也固定UTF-16，行/character从0开始；对外人类行号从1开始。根据同一原始解码文本的line-start表换算，不混用UTF-8 byte offset。CRLF、中文、emoji、BOM都按真实文本处理，不先normalize换行。来源字节哈希留在既有来源层。

文档定位若只给方法名，按准确位置找包含它的声明，不按附近文本、方法名全仓搜索或括号计数猜范围。LS full range与Core声明边界可能因Javadoc范围不同而不完全一样，允许确定的注释范围差异；必须指向同一声明，不允许越到另一个方法。

### catalog注解identity：发现器不能暗中用JavaParser补

这项职责在JDT Adapter内，使用同一SyntaxReader和LS会话，不新增扫描器。Core返回package、imports（含onDemand/static标记）、AnnotationView的完整范围及nameSelection。对已写全限定名可保留该源码名；需要确认声明身份的注解，在nameSelection上询问LS definition并归一化位置。

Core优先使用相同受控sourcepath/classpath解析注解binding；只有 binding 非空且不是 recovered binding 时才填qualifiedName。recovered binding在缺依赖时可能伪装成当前 package 下的类型，必须视为未解析，再由显式 import 或LS definition恢复身份。仍未解析时只接受可以归一为确定声明身份的LS返回；不得从本机缓存路径、simple name或“annotation包”惯例猜包。对源码内自定义组合注解，递归读取其元注解，按声明位置去重防环；只应用既有支持的Spring组合规则，未知AliasFor/动态表达式保留限制。

在通配import或缺依赖下LS仍不能确认时，qualifiedName=null并记录`ANNOTATION_IDENTITY_UNRESOLVED`。Spring消费者保留该已定位mapping候选方法、原注解及具体限制，允许后续读取其源码；不能把未确认候选宣布为准确HTTP路由，也不能从发现site/材料范围中静默过滤。合法省略method仍是UNRESTRICTED，和注解identity无法确认不是同一种问题。

Luna须测试显式全名、通配import、同名自定义注解、源码组合注解及循环、历史缺依赖候选保留；Terra实现不得以正则simple-name匹配或旧JavaParser发现器补齐JDT路径。

依据：[ASTParser](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)、[ASTNode范围](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTNode.html)、[MethodDeclaration](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodDeclaration.html)、[MethodInvocation实参](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodInvocation.html)。这里采用的是现有AST API，不开发Java解析器。

### Luna RED / Terra GREEN，Sol设计与debug

直接测试整方法含注解/Javadoc/条件/返回、重载的准确位置、构造器、varargs、嵌套lambda归属、中文/emoji/CRLF切片、两个同名调用不同位置。JDTCore-only路径必须在无JavaParser依赖的helper中运行。不要用大仓失败后反复补案例词表来验证位置基础错误。

## 3. JdtNavigationResolver：让JDT决定调用连接谁

### 输入与请求序列

输入为具体调用点及其navigation位置，不是字符串`UserService`。现行先对包含方法使用`prepareCallHierarchy`、`outgoingCalls`，按`fromRanges`**包含导航起点**挂接目标，再合并逐点definition/implementation。这已让内层`Convert.toInt(...)`混入外层`setPageSize(...)`，也让`getBillNo()`混入相邻目标。目标保留批量查询但以AST物理调用点和JDT绑定核对归属；不能只按display name匹配。[具名证据](../../supplements/technical-analysis-fixed-source-acceptance.md)。

针对每个调用点：

1. 收集hierarchy原始目标及每个`fromRange`。**目标修正：**先以AST完整表达式范围或名称范围的精确对应确定一个物理调用；其他跨度只有在对应的AST调用唯一、且有效逐调用binding与目标声明binding确认同一声明时才归属。一个`fromRange`同时覆盖内外两个调用点，即使两者引用同一方法，也不得靠binding相同或选最内层/第一个来归属。范围与binding冲突或无法唯一归属时，保留原观察和`CALL_SITE_ASSOCIATION_UNCONFIRMED`，不把该候选加入未证实调用的可展开边。对具体调用位置照常发definition，不因hierarchy有观察而跳过。
2. 归一化标准`Location[]`、`LocationLink[]`与客户端LSP4J包装。处理代码面对的是库对象，不把`left/right`包装当作LSP在线协议。未知形状保存诊断并报适配错误，不作“JDT找不到”。
3. **声明是接口、abstract、非final可覆盖实例方法，或目标属性无法确定时，仍查询`textDocument/implementation`。** 已有hierarchy返回不允许提前退出。静态/private/final方法不为不存在的动态分派展开候选。
4. 对可读的仓库声明，以Core声明binding的`getMethodDeclaration()`身份核对直接调用binding；明确不一致的直接声明位置不成为确定可展开边，其原始LS观察随冲突保存。没有可靠Core binding时，**从该调用精确navigationSite返回**且唯一、准确落到catalog声明的LS definition仍可作为工具确认的静态声明，附`BINDING_UNAVAILABLE`限制；多位置或不匹配声明只能保留候选观察，不能选第一个。`IMPLEMENTATION`是工具给出的动态候选集合，全部保留并标为可能目标；JDT的`overrides`可正向确认继承关系，但其false或缺失**不能**删掉LS返回的合法接口实现或推断实际运行bean。按同源位置去重，合并`roles`和`navigationKinds`；无法确认的工具位置不展开为确定边。
5. 用SyntaxReader读取候选声明和body。LS位置落在类型而非构造器时，不随机选一个构造器；继续用明确构造调用位置查询，仍不明确则保留`CONSTRUCTOR_TARGET_UNRESOLVED`。

同一会话必须缓存相同操作/位置的原始结果：prepare按方法位置，outgoing按每个hierarchy item，definition/implementation按实际navigationSite。相同key最多一次实际RPC；不同操作不共用结果，必要implementation不因hierarchy命中省略。合法空、最终失败与候选分开保存，最终失败复用原诊断而非换入口重试。原始请求/响应首次保存一次，cache hit关联key；私有诊断不进模型或公开metadata。是否需要额外查询由工具返回类型决定，不针对`registerUser`等案例硬编码。

**已确认的超时修正合同（2026-09-28，已实现并通过43项直接测试；固定源码七入口已有定向重验但仍有首次等待超时、未正式发布）：**调用者等待超时，不代表远端请求已经结束。缓存保留原请求；下一次相同查询不得重发RPC，而是等待该请求或使用它已经返回的最终结果。每个调用者仍使用现有查询等待上限，不延长配置、不自动重跑先前失败的入口。迟到的正常空列表也是有效查询结果；远端异常、协议错误仍按最终失败处理。私有日志区分等待超时和最终完成，保留可取得的异常类型、cause及耗时。不能在请求回调里获取正在等待该请求的客户端锁造成死锁；关闭会话后不重新打开日志或发请求。该**先前缓存修正**未改变单查询失败时入口部分材料的保存；下文四命令目标另行设计逐调用失败投影。

实现保持现有同步查询边界：异步回调仅保存原始结果/异常和实际完成时间；调用者再次观察时才校验响应、写最终exchange并缓存最终状态。日志中的请求耗时按实际完成时间计算，不把两次访问之间的空闲时间算进工具耗时。没有后续观察就可能只有等待超时记录，不能据此断言远端最终失败。关闭会话释放缓存；迟到回调不写日志、不重新打开已关闭会话。线程中断与等待超时分别记录，均不触发另一次RPC。

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

### 目标：外部、未知、查询失败各有自己的状态

现行`JdtNavigationResolver`把无法打开的目标位置丢到诊断，`EntryCodeCollector`把零仓库目标统一写成`UNRESOLVED_CALL`；`JDT_QUERY_FAILED`目前可能令整个入口`NOT_COLLECTED`。目标保留既有`LOCATED/CANDIDATES`意义，并在CALL及入口限制中明确区分：

| 工具证据 | CALL状态与展开 | 入口/下游含义 |
| --- | --- | --- |
| 非recovered方法绑定的声明类型被Core确认是受控binary，或LS给出可识别的库URI及其符号位置；身份和来源可核对 | `EXTERNAL`，保存工具给出的类型/方法身份、调用原文/参数与`EXTERNAL`停止原因；不造客户`MethodCode` | 正常外部边界，不单独增加`UNRESOLVED_CALL` Gap；Step04/05可展示，但不沿库正文展开 |
| 绑定缺失/恢复、返回空位置、库身份未确认、源码目标无法唯一定位 | `UNRESOLVED`或`CANDIDATES`及具体`UNCONFIRMED`限制；未确认观察不可展开 | 仍是覆盖限制，不能由`Long.parseLong`字面名称、空数组或`open(uri)==null`宣称外部 |
| 必需的hierarchy/definition/implementation RPC超时、异常或协议失败 | `QUERY_FAILED`，保存操作、位置、可得的异常类/cause、已有局部观察；不得归为正常空结果或外部 | 对受影响调用/入口保留不完整覆盖；共享工具/来源故障仍按既有fatal边界处理，其他安全入口可继续 |

一条调用若已有确认的仓库目标而另一个必需查询失败，仍显式记录`QUERY_FAILED`与局部候选，不宣称候选集合完整。仅确认的仓库边可入Collector队列；工具位置、binding或fromRange不足以确立所有权的候选只作观察，不成为Step04/05的Mapper/SQL连接。`EXTERNAL`不等于项目源码查不到；二者由工具证据区分。这是**现有JDT模块的关联和状态投影规则**，不是另一个Java解释器或通用调用裁决器。查询失败的私有原始交换仍按现行日志保存，公开CALL仅保存稳定代码与安全细节，不泄露绝对路径。

实施字段只放在既有seam：Core v4 的`CallSiteView`/`Declaration`加`bindingObservation={state,declarationKey,declaringTypeKey,typeOrigin,displayIdentity}`（不可得字段为null）；Resolver对每个LS位置留`{operation,uriKind,sourceRange,association=CONFIRMED|UNCONFIRMED|CONFLICT}`观察，并返回确认的静态声明、可能实现、外部身份或失败。index v3的CALL保留原`targets`供**确认的仓库调用边**；外部身份、未确认位置及操作失败放在同一调用键下的结构化观察，`resolution`新增`EXTERNAL`、`QUERY_FAILED`，原`UNRESOLVED`只表达正常查询后仍未定位。Collector内存可以暂留`NOT_EXPANDED`的未确认诊断候选；v3发布时必须把它移入观察，而不能作为正式CALL target。相反，已经确认、仅因收集预算而`NOT_EXPANDED`的仓库边仍属target，不得一概删除。`EXTERNAL`的`targets`必须为空：现有`CallSite`构造合同禁止把库方法伪装成仓库目标；工具确认的方法身份与来源由观察保存，不能只留URI或从调用字符串猜名称。publisher/reader检查状态与字段组合，Step04/05只沿确认的仓库target连接Mapper/SQL，不从未确认观察或外部标记合成客户声明。对方法owner的outgoing查询失败时，其所辖调用共同记录该操作失败；精确逐点查询仍可取得局部观察，但不能把它们合并成完整候选集。只捕获可归属的`JDT_QUERY_FAILED`，协议损坏/来源漂移等共享fatal不降为逐调用状态。

正式记录的具体位置是`CALL.payload.call.observations`；`CALL.payload.call.targets`只存确认的仓库边。v2 writer明确省略`observations`，v2 reader要求该字段缺失并重建空列表；v3 writer必须写数组，v3 reader必须验证它存在且结构合法。不能通过缺字段默认空列表猜测版本。

目标实施使Core私有协议`jdt-syntax-v3→v4`、Step03新发布`java-code-index-v2→v3`、技术Step03 module producer `v3→v4`（与四CLI设计的版本矩阵一致）；新读写器严格校验新字段及状态，现存index v2只由历史读法读取，绝不回填绑定或重写历史错边。Step02环境/readiness v1、目标JDK/`diagnosticCoverage`、已修好的同key RPC缓存语义均不变。独立版本和确切lineage由[技术运行合同](../technical-analysis/cli-and-runtime.md)持有。

Lombok仅作受限验证：官方[Eclipse安装](https://projectlombok.org/setup/eclipse)与[ECJ Java agent](https://projectlombok.org/setup/ecj)给出成熟入口；受控工具JVM的候选接入点是在`-jar`之前配置经核验的`-javaagent:<lombok.jar>`，ECJ独立模式的官方形式为`-javaagent:<lombok.jar>=ECJ`。[执行路径](https://projectlombok.org/contributing/lombok-execution-path)说明普通classpath不足以变换AST。先核对**固定**LS 1.61.0、Core 3.47.0、现有Lombok 1.18.12与工具JDK的兼容性、agent加载及进程隔离，再在本轮批准的**受限具名验证**中分别测试LS和helper的`@Getter/@Data/@Slf4j`生成成员诊断、绑定和源码范围。通过条件是两工具在同一冻结环境中对具名成员给出稳定身份，客户原始范围仍指向原始文本且没有新阻断诊断；失败条件是入口不可用、版本不兼容、身份仍缺失或范围不可信，此时保留`LOMBOK_GENERATED_MEMBER_UNCONFIRMED`并退出。不自行模拟注解、执行客户构建或把[delombok](https://projectlombok.org/features/delombok)产物冒充冻结源码原文；不许诺全部Lombok特性。51秒文档生命周期等待只复核已保存的时序与聚焦样本；未定位具体任务与调度原因前保持`ROOT_CAUSE_UNCONFIRMED`，不新建调度/诊断系统，不将缓存修正宣称为延迟根因修复。

**真实例子：**Controller中的`userService.registerUser(ue, manageRoleId, request)`在调研hierarchy里遗漏，但definition返回了UserService方法名位置。这是LS另一条导航能力成功，不是人工补Service。正式模块要把这类兜底作为通用导航流程；不同于“失败后自动换另一个引擎”。

静态导航不能证明“这段Service确实在某次请求运行过”。调用树展示可能涉及的源码和条件；是否执行由原代码条件/运行时决定。调用/控制/数据图均不是动态运行结果。

### Luna RED / Terra GREEN

以真实录制位置验证Location与LocationLink、empty/error区分；hierarchy已有interface也会询问implementation；两实现都保留；wildcard/static import由LS解析；构造器不误取class正文。所有测试通过后用相同两入口验证真实工具，不把录制fixture通过当作JDT实测。

四命令目标的直接验收加五组，不扩大到全仓测试：① `setPageSize(Convert.toInt(...))`和`getBillNo()`的错误hierarchy候选被留为未确认观察，内层真实声明仍可定位；另有内外调用同一方法的夹套fixture确保binding相同也不误归属；② 重载与interface的两个合法实现都保留，`overrides`缺失不能删候选；③ 工具确认的JDK/第三方binary边是`EXTERNAL`且无`UNRESOLVED_CALL`，空definition加缺binding仍是`UNRESOLVED`；④ 某一必需RPC失败单独是`QUERY_FAILED`，既有局部观察保留、入口不标完整，下一入口不因失败被伪装为空结果；⑤ index v3严格重开并沿确认Mapper边给Step04/05，旧v2原样历史读取。具名客户样本在获准运行时再对照原始JDT结果、保存CALL及下游材料；fixture通过本身不是客户准确性证明。

## 4. EntryCodeCollector：连成一个可读对象，不再制造证明层

### 处理步骤

1. 输入真实EntrySeed，取入口完整MethodCode。
2. 对该body的每个调用取得resolver结果；会话内已有结果直接复用，返回的仓库内concrete候选进入本入口工作队列。
3. 按稳定源码位置顺序遍历队列，完整方法正文以会话级methodKey去重，本入口只记录成员关系。每个调用点及入口展开投影仍独立保存，递归/互递归通过key引用，不递归复制无限正文。不把其他入口的BODY_INCLUDED覆盖本入口的NOT_EXPANDED。
4. 取形参与有序实参做位置对照，保留原文；类型适配/重载选择交LS，不自己证明数据流。多候选分别关联，不能拿A实现的形参配B实现。
5. 记录外部、无实现、未知目标及停止原因；lambda/回调引用保留deferred，不能排成业务顺序。
6. 入口上下文引用全部已取得methods/calls、必要字段/配置声明与限制，交给Step03保存索引、Step05组织发布。

示例输出不是一串“Controller→Service→Mapper”文字，而是可以直接展开方法代码的图状集合。共用方法只存一次，路径归属保持多对多，两个调用相同Service不会互相覆盖。

### 完整与停止的定义

“完整”指：在本次声明的源码范围内，对每个已取body的可枚举调用点都查明了导航结果或明确停止原因，不保证找到所有动态调用，不保证每个库都有源码。

默认不设置一个为节省token而只走两层/几个方法的固定上限。整仓索引与代码取材不调用模型；输出仍遵守宿主内存、单次RPC超时和用户取消这些运行安全限制。若限制触发，保留已取结果及未展开调用点，明确`NOT_EXPANDED`影响的入口，不能称为完整。

未提供的外部依赖由用户或获授权的Agent在Maven导出环节处理，Java不会按仓库策略自行准备；生成代码也不会在Java分析中临时运行客户构建补齐，已知缺项则阻断导航。环境核对有效后普通库调用、代理Mapper或动态分派边界仍可作为局部限制，不能把外部调用边界等同缺依赖；非法来源或工具协议损坏仍阻断。

### 下游保证

Step05不再重跑JDT或解析器，只归属/保存指向索引的引用；重开后恢复完整视图。Builder无需再查“缺少的Service”——它应在本模块交付物中，或者有准确原因；若有body却没传到模型，是组包错误，不是导航缺口。技术文件去重不意味着给模型发送它无法读取的文件key，实际请求仍含必要完整方法。

已实现的旧[模型批次解耦](../model-job-execution.md)在已保存M10之后开始；新Activity目标在Step05材料之后开始，两者均不打开本引擎。模型失败不清空 JDT 缓存或索引、不触发再扫；本页语法/导航失败合同保持不变。新的业务过程发现同样只通过 `FrozenAnalysisCorpus` 读取保存的 Activity、SourceRef 和方法片段；过程 DRAFT 请求关键来源时是打开已有内容，不是重新导航。针对某个 JDT 超时的具名单入口复核是独立操作，其新结果不得就地覆盖旧材料或混入旧来源编号。

验收同时读取保存的context和实际模型请求：注册三段Service不能只存在技术索引中。直接业务callee优先完整进入核心包；若所有相关方法不能放进一次请求，按完整方法组合并如实记录未进入该包的方法，不切掉关键条件或假称完整。如何选择可读单元归Builder，不能由Collector按行业词猜哪些方法重要。

### Luna RED / Terra GREEN

测试重复方法、重复调用位置、循环、多入口共享、interface多候选、构造器、无body边界、部分取消与源码定位；再以真实注册/财务和一个不同结构的fixture验证。禁止为达到expected count人工加入Service路径，禁止把非空body全部压成label后宣称完成。

缓存增量测试直接统计query key的真实调用数；N个入口共享同一方法不会多发相同RPC。还要验证不同调用位置、工具/依赖配置改变、QUERY_FAILED、A完整/B未展开和源码冲突。真实JDT用显式集成测试，普通录制响应测试不启动LS；[CI分类规则](../../plans/navigation-reuse-and-readable-report-design.md#9-本地-ci一个测试只由一个阶段执行)保持本地单次测试，不删除有效断言。

## 5. 已验证范围与剩余边界

当前JDT独立Core helper、构造器/方法引用/循环、多候选、LS归一、query缓存、YAML、生产发现和module7 index-v2已实现。旧JDT路线曾经过context/Capsule/M10并生成326个已审Activity；这些是保留的历史结果，不再是新生产接线。

当前01–05为JDT→可选持久化→CodeReadingMaterialSet。2026-09-18固定仓库取得9,189方法、51,675入口调用，325包/326覆盖，1个definition超时；Step06与Provider未执行。详细版本、耗时与限制见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)。本轮未重跑JDT。

本步不证明动态代理、反射或外部系统效果。新目标的模块/依赖/平台准入属于Step02；未知环境不再仅以limitation继续导航。历史正文与调用保持历史来源/质量状态，不被本次依赖修正自动升级。新Activity的容量/补读/retry由[Activity目标](../activity-explanation/README.md)拥有，不能修改本步语法/导航的超时合同或自动再次collect。JavaParser生产已退役，[历史接入页](integration-and-javaparser.md)只保留严格读取说明。
