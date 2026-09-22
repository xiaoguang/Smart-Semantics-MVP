# Java代码引擎：配置、数据与保存合同

本页拥有当前JDT Interface与java-code-index-v2字段。生产不创建JavaParser；历史合同精确读取见[接入页](integration-and-javaparser.md)。Step04/05的当前字段分别由[持久化材料](../../analysis-steps/04-proven-code-facts.md)和[阅读材料](../../analysis-steps/05-business-flows.md)维护。

## 1. 一次加载配置

唯一CLI/Java组合根从同一配置加载sourceAnalysis.javaEngine与jdt.installation/javaHome。新启动javaEngine只接受大小写精确的jdt；历史javaparser仅可解码既有state/hash，不可open或fallback。

工具路径在本地组合根，不能进入公共分析请求或模型材料。通过参数数组启动配置工具，不调用shell或PATH替代。未知/重复key、工具缺失、语言级别或classpath非法在工具启动前明确失败。客户POM/配置仅为分析文本，不控制宿主。

模型/Prompt/并发/retry/输出目录不是Java索引失效条件；源码、源码根/模块、language level、classpath内容、tool/adapter版本和有效导航配置才是。插件开关不使Java索引失效。模型配置的新v3与容量字段见[模型执行](../model-job-execution.md)，不得塞进引擎profile。

## 2. 工具Interface

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

这是内部Interface，不替换RepositoryAnalysisAgent，不泄漏Eclipse AST/LS handle。VerifiedJavaProject拥有固定snapshot、文件/源码根/语言级别、显式本地依赖及诊断。

catalog取声明供Step02识别入口；collect按精确入口收集方法/调用；descriptor记录实际工具/能力；close释放会话。当前同session顺序collect，不要求并发LS或分布式索引。

EntrySeed持有entryId、methodKey、完整methodRange与trigger。SourceRange为 `startOffsetUtf16,lengthUtf16,startLine,endLine`；offset零起半开，行号一起含末行。精确声明位置参与入口身份，不以handler名/FQN/路由替代重载位置。Step02当前entry schema v3保留UNRESTRICTED方法条件原义。

工具错误穿过CodeEngineException；单调用无目标是合法UNRESOLVED，RPC失败是QUERY_FAILED，无法继续的session/协议/来源错误是运行失败。三者不混为empty list。新工具失败不启用旧parser。

## 3. Catalog字段与消费者

| 记录 | 保存字段/作用 |
| --- | --- |
| JavaDeclarationCatalog | snapshotRef、files、types、methods、fileDiagnostics；每Java文件有实际处置 |
| TypeDeclaration | 来源、qualifiedName可空、kind、annotations、fields、methodKeys、supertypeTexts |
| MethodDeclarationView | methodKey、declaringType、name/kind/modifiers、parameters、returnTypeText、annotations、sourceLocation、hasBody |
| AnnotationView | nameText、确认后的qualifiedName、成员原文、可静态读取值、sourceLocation、独立nameSelection |
| FieldDeclarationView | name、typeText、annotations、initializerText可空、sourceLocation |

注解qualifiedName只接受非recovered binding或可确定的LS/显式import身份。通配import/缺依赖下未解析时保留null与诊断；不按simple name冒充Spring。自定义组合注解沿确定声明递归防环，未知规则保留限制；未确定mapping候选不能静默从来源候选清单消失。

## 4. MethodCode与CallSite

MethodCode是完整源码单元，不是几行摘要：

| 字段 | 约束 |
| --- | --- |
| methodKey | 同snapshot声明位置的framed identity，不用LS临时handle |
| kind | METHOD/CONSTRUCTOR/INITIALIZER/LAMBDA；不造隐式方法源码 |
| declaringType/name/signature | 工具/源码实际展示，未知可空；不猜全限定类型 |
| enclosingMethodKey | 嵌套/lambda的外层归属，nullable，不代表执行顺序 |
| parameters | 有序ordinal/name/typeText/varArgs/annotationTexts，重复值不去重 |
| returnTypeText | 原文类型，构造/initializer/无声明lambda可空 |
| source | 仓库相对path、行、UTF16范围、完整原文text |
| bodyPresent | false仍保留完整真实接口/abstract/native声明 |
| modifiers/annotations | 确定源码属性，不代表Spring实际bean选择 |
| controls | kind/expression/sourceRange/parentControlIndex |
| exits | RETURN/THROW、expression和sourceRange |

controls包含IF/ELSE/TRY/CATCH/FINALLY/LOOP/SWITCH/SYNCHRONIZED；它们是语法提示，不是CFG可达性Proof。缺少某结构化提示不删除完整正文。

| CallSite字段 | 约束 |
| --- | --- |
| callKey/callerMethodKey | 唯一物理调用位置与方法owner；同表达式不同位置不合并 |
| kind | METHOD/SUPER_METHOD/CONSTRUCTOR/THIS_CONSTRUCTOR/SUPER_CONSTRUCTOR/METHOD_REFERENCE/CONSTRUCTOR_REFERENCE |
| site/navigationSite | 完整expression与调用名/构造目标的独立UTF16位置；不能字符串indexOf重建 |
| expression/receiverExpression | 原文；无显式receiver为空 |
| actualArguments | 有序ordinal/expression；f(id,id)仍两项；引用无实际参数则空 |
| enclosingControlIndexes/deferred | 实际作用域和延迟语法，不能转成业务步骤顺序 |
| targets | 全部声明/实现候选，允许空或多项 |
| resolution/resolutionDetail | LOCATED/CANDIDATES/UNRESOLVED及实际原因；唯一定位也不证明实际分派 |

每个target含methodKey（无仓库方法时可空）、displayName、roles（DECLARATION/IMPLEMENTATION）、navigationKinds（CALL_HIERARCHY/DEFINITION/IMPLEMENTATION/ENGINE_BINDING）、expansion和reason，以及该候选有序argumentAssociations。

expansion为BODY_INCLUDED/DECLARATION_ONLY/EXTERNAL/UNRESOLVED/NOT_EXPANDED；除BODY_INCLUDED外需具体reason。参数association为actualOrdinals/formalOrdinal/kind(POSITIONAL/VARARGS/UNKNOWN)，不跨候选配形参，不推导运行值或SQL列值。

supportingSources与limitations保留当前EntryCodeContext合同；XML完整补全由Step04负责。technicalEnhancements既有AVAILABLE/NOT_PRODUCED字段按保存版本原义读取，新JDT只保留未生成描述、不产生图/Fact/Flow。不得为移除旧字段原地改变index v2语义。

## 5. 保存格式、位置与复用

Step03 module7 `java-code-index` 使用artifact `PROGRAM_GRAPHS_JAVA_CODE_INDEX`，JSONL schema `java-code-index-v2`。每行外壳为schemaVersion、recordType、key、payload，recordType固定ENGINE/TYPE/METHOD/CALL/ENTRY_MEMBERSHIP/DIAGNOSTIC。

- ENGINE恰一条，保存descriptor、snapshotRef和既有technicalEnhancements。
- METHOD按全仓methodKey共享，正文必须一致。
- CALL payload为 `{entryId,call}`；记录key为entry-call:加SHA256，framing依次为entry-call-record-v1、entryId、callKey三个UTF8字符串，各有8字节big-endian长度前缀。
- ENTRY_MEMBERSHIP保存seed、collectionStatus、reason、methodKeys、callKeys、supportingSources、limitations；reader用entryId+callKey定位CALL，不跨入口借用。
- 同一callKey的caller/kind/site/navigationSite/expression/receiver/actualArguments/controls/deferred在跨入口记录中一致；targets/resolution/expansion可因入口收集不同而不同。
- COLLECTED还原原方法/调用集合；NOT_COLLECTED保留原因，不构造假空成功。
- 外层按recordType固定顺序、key UTF8排序；成员列表保留既有顺序。

出版与重开检查身份、来源、重复/悬空ref、原文冲突和owner；不first-win/last-win掩盖差异，不重跑collect。历史index v1按其原schema单独读取，不暗中转成v2。

两个入口共享同一Service调用时，一个已展开、另一个未展开可以同时成立；共享正文不是共享最终CallSite。这个行为通过直接publisher/reader fixture验证，不用长跑大仓替代。

## 6. 当前材料消费者与历史版本

当前Step04消费索引和冻结XML，发布persistence-material-index-v1；当前Step05引用式保存code-reading-material-set-v1，reader一次恢复METHOD与Resource真实内容。Activity模型投影另外分配包内短键；不能给模型未解引用methodKey代替源码。

历史entry-code-context-v1、flow compilation v6、capsule projection v11、flow slices v6、evidence capsule v9、fact accounting v4各按旧wire只读。它们不是新生产输出，更不是JavaParser Adapter恢复要求。新材料/Activity精确版本由[接入合同](../activity-explanation/integration-contracts.md)维护，不在本页复制不一致表。

## 7. 直接测试与当前成熟度

实现已保留JDT独立helper、注解identity、准确位置、候选、query缓存、共享METHOD与入口CALL、保存重开。固定新仓325/326结果见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)；不声称所有动态行为已解析。

测试覆盖UTF16/CRLF/emoji、精确重载、无body声明、构造/延迟调用、hierarchy后必要implementation、空/错误区分、跨入口同call与源码冲突、来源越界、保存无重扫。Terra写直接测试，Sol实现，Astra裁决；不增加业务词表、第二个parser或新的公共Agent。
