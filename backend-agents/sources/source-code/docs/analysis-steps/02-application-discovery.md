# 应用发现

> [总体设计](../DESIGN.md)；固定 key：application-discovery，目录：steps/02-application-discovery/。本步骤运行时模型调用为 0。

## 1. 为什么存在

业务分析必须先知道系统有哪些入口。Step02 从固定源码识别 Spring route/handler/参数及 Mapper 候选，建立全入口分母；入口后来没有 strict Flow 或 Fact，也不能从业务分析范围消失。

它回答技术触发点在哪里，不替方法起业务名称，也不把 Controller 当参与者。语义意义留给模型，已定位入口先交 Step03 建图和 Step05 组织代码上下文。

## 2. 输入与处理

输入为Step01 verified source view、静态工程画像和选定Java引擎的catalog。配置/POM/XML仍共用现有读取器；Java类型、方法、参数与注解来自[JDT/JavaParser统一Interface](../modules/java-code-engines/contracts-and-configuration.md)。先完成JDT接线，第二阶段再适配JavaParser现有能力。只读清单内文本，不执行客户构建或应用。

| 模块 | 输入 → 工作 → 输出 |
| --- | --- |
| ApplicationProfileDetector | POM/config静态画像 + 选定engine catalog → Java/Spring MVC/MyBatis signals → profile |
| SpringHttpEntryDiscoverer | profile + 中立AnnotationView/MethodDeclarationView → route/methodCondition/handler → 全入口及处置；不再自建JavaParser |
| MapperCapabilityCataloger | 同一catalog + 安全XML/config → Mapper/statement候选与定位；Java读取不得绕回未选引擎 |
| ApplicationDiscoveryPublicationSpecifier | 已完成不可变结果 → 合并完整分母、序列化并原子安装 → 五文件步骤 publication |

M2/M3共享immutable profile/catalog，不重复解析Java。Mapper catalog只记录静态候选；现有可证明的Java→XML绑定保留为技术增强，不能因为尚无SQL绑定就不交付Java方法。发布器不重跑发现。JDT仅提供Java语法/导航，Spring route规则仍是一份公共规则，不让两个插件自行解释不同HTTP合同。

## 3. Spring RequestMapping 的明确合同

@RequestMapping 省略 method 与 method={} 都是合法 Spring MVC 映射，不得仅因没有 HTTP verb 标 GAP。类级没有 method 限制表示 unrestricted，不是未知。HTTP method conditions 的 class/method combine 规则如下：

| 类级 condition | 方法级 condition | 有效 condition |
| --- | --- | --- |
| 空或未指定 | 空或未指定 | UNRESTRICTED |
| {GET} | 空或未指定 | EXPLICIT {GET} |
| 空或未指定 | {POST} | EXPLICIT {POST} |
| {GET} | {POST} | EXPLICIT {GET,POST}，并集 |
| {GET,POST} | {POST} | EXPLICIT {GET,POST}，并集 |

这是 Spring RequestMethodsRequestCondition.combine 的集合行为，不要盲目求交集。@GetMapping 等组合注解相应提供显式 method condition。GET 的 HEAD 支持和框架的 OPTIONS 处理是 HTTP 框架行为，不据此发明新的 handler、GET 默认值或多个业务活动。

依据：[Spring RequestMapping 文档](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)，[HEAD/OPTIONS 说明](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#requestmapping-head-options)，[Spring v5.3.39 RequestMethodsRequestCondition.combine](https://github.com/spring-projects/spring-framework/blob/v5.3.39/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/condition/RequestMethodsRequestCondition.java#L88-L106)。

### 3.1 最小目标字段

单值 `method` 不能完整表达 unrestricted/集合，不能把 ANY 或空串偷偷当成 HTTP verb。当前实现已在每个 EntryPoint 与 `entry-points.jsonl` 写入下面 required 的 `methodCondition`。现有 `method` 仅保留为由该对象导出的展示文本；下游必须读取 `methodCondition`，不能从展示文本推断条件。缺少该对象的输入会被新读取器拒绝：

~~~text
methodCondition: {
  kind: UNRESTRICTED | EXPLICIT,
  methods: RequestMethod[]
}
~~~

UNRESTRICTED 要求 methods=[]；EXPLICIT 要求非空、去重并按稳定枚举顺序 GET、HEAD、POST、PUT、PATCH、DELETE、OPTIONS、TRACE 排序。RequestMethod 只取这八个枚举值。class/method 原始注解 excerpts 与 route parts 继续保存，effective methodCondition 进入 entry identity；由 source/组合规则计算，不由模型修改。

同一 route/handler 的方法集合是一个入口条件，不按每个 verb 复制业务活动。一个 handler 关联多个真实 route 时仍按明确 route identity 记录，Step07 可解释多入口对应同一活动。该字段随新生成产物一同出现；读取旧单值、缺少 `methodCondition` 的输入不会被静默解释为 unrestricted。

入口还必须保存 `methodKey` 与 `methodRange`。前者是 catalog 的跨引擎稳定声明键；后者是完整声明的 `SourceRange(startOffsetUtf16,lengthUtf16,startLine,endLine)`。两者进入 `entryId` framed identity并共同使 Step03/05 在同名重载中选择准确方法；`handler`/FQN/方法名只供展示，不能代替选择键。M2 `http-entry` moduleVersion 与 `application-discovery-http-entry-discovery-v2` 升为 v3，M4 `publish` moduleVersion 升为 v3，公开 `application-discovery-entry-points-v2` 及逐行 `application-discovery-entry-point-v2` 升为 v3；profile/capability/mapper 保持 v2。所有直接 reader、artifact policy 和 fixture 同步拒绝旧 entry wire，不以缺字段回退。

### 3.2 真实待修复例子

UserController#getOrganizationUserTree 和 MaterialCategoryController#getMaterialCategoryTree 使用无 method 的 RequestMapping。此前它们会被误标限制；发现器现已将省略或空 method 的映射保存为准确 route/handler/UNRESTRICTED 入口，并继续保留相同完整 discovery site 分母。完整 jshERP 重新运行仍属于后续验收，不能由 fixture 代替。

## 4. 输出示例与下一消费者

以下是**目标阅读投影，不是现有 EntryPointV2 wire 或本轮运行产物**：

~~~json
{
  "handler": "UserController#getOrganizationUserTree",
  "methodCondition": {"kind": "UNRESTRICTED", "methods": []},
  "reason": "RequestMapping has no explicit method condition",
  "nextConsumer": "Step03 indexes the handler; Step05 assembles its source context"
}
~~~

正式 entry 还必须含准确 route、class/method route parts、handler signature、参数和原始注解 excerpts，不能用本例省略的 route 作为 fixture。财务例的 GET /accountHead/getFinancialBillNoByBillId 则为 EXPLICIT ["GET"]；这项来自 @GetMapping，不能推广成所有没有 method 的默认值。

| 文件 | 内容与用途 |
| --- | --- |
| application-profile.json | 语言/框架静态 signals、配置及 capability profile |
| entry-points.jsonl | v3；全部入口、route parts、method condition、handler、参数、`methodKey` 与完整声明 `methodRange` |
| mapper-catalog.jsonl | Mapper Java/XML 候选，尚不宣布唯一绑定 |
| capability-report.json | 每个发现 site 的处置、明确限制、全入口分母 |
| application-discovery-receipt.json | 同源 inputs/controls、四项 semantic descriptors 与状态 |

Step03 复用 entry/catalog identities 建图，不重新发现入口。Step05 接收同一分母，为每个安全入口整理代码；即使没有 strict Flow，也应有上下文或具体不可读原因。Step06 只封装 Step05 结果，不重新解析 route 或建立另一分母。

## 5. 技术、来源与完整性

route 必须依据能静态读取的 class/method 注解合并，保留每部分的准确 SourceExcerptV1；动态表达式或真正歧义的 mapping 写带定位 Gap，不用名称、注释或 LLM 猜 route。不存在类级 prefix 时按实际注解语义处理，不能凭空补 DepotHead 的类级路由。

Mapper XML 标准 DOCTYPE 允许声明，但外部 DTD、general/parameter entity、schema 与网络解析必须禁用；不能保证这些设置则 fatal。config/catalog 路径只解析到 Step01 inventory，禁止外部文件。源码定位、canonical 身份、public request 继承 [公共接口附录](../references/inherited-public-and-module-contracts.md)，存储继承 [Canonical 合同](../references/canonical-persistence-identity-contracts.md)。

完整分母守恒：

~~~text
reachableDiscoverySites = supported ⊎ unsupported ⊎ ambiguous ⊎ overLimit
discoveredEntries = supportedEntries ⊎ gappedEntries ⊎ reasonedExclusions
allSiteIds = exactDisjointUnion(shardSiteIds)
~~~

一个入口成功不能关闭其他未处置入口；0 入口也保存四项 semantic 文件和 receipt，用 NO_ENTRY_DISCOVERED 说明范围，不能省文件或伪造入口。hash/reference 正确但漏掉合法 unrestricted entry 仍是功能错误。

## 6. 保存、复用、预算与失败

同进程消费者复用已验证 immutable source/discovery view；各模块结果和步骤产物仍保存。跨磁盘、新进程、导入边界核验 identity/hash/schema/ref/basis；重新读取源码验证所读字节，不能每个模块都重新遍历并校验全仓。

预算覆盖 AST/XML nodes、配置条目、entries/catalog/site 数与深度。unsupported/动态 route 是局部 Gap；unrestricted method 不在此列。来源漂移、schema/ref 冲突、重复身份、catalog 引用断裂、XML 安全策略失败、coverage 不守恒或安装错误 fatal。保留上游完成产物，不补扫或改写旧 run。

## 7. 当前实现与最小测试

插件化尚未实施：下述是现有JavaParser路径的状态。第一阶段要把Java读取搬到JDT catalog；不能通过仅改YAML声明就称“全用JDT”。新增定向验收包括JDT模式不调用JavaParser、同一索引支持多入口、通配import及缺依赖的明确诊断，规则详见[JDT详细设计](../modules/java-code-engines/jdt-engine.md)。

ApplicationProfileDetector、SpringHttpEntryDiscoverer、MapperCapabilityCataloger、步骤 publisher/executor 已有实现，固定完整 jshERP 入口发现已有保存证据；不是仅 package 骨架。现有材料规划处理 337 个发现入口，但这只证明当前发现分母的处理，不证明所有合法 Spring 变体已正确发现。省略 method、`method={}`、显式方法集合及类/方法条件组合现在由直接回归覆盖；两个真实端点要在后续完整仓库重跑中确认进入新分母。

Luna/xhigh 已为省略 method、method={}、类有限制+方法空、类方法显式并集建立直接 RED；Terra/xhigh 已以 `HttpMethodCondition`、发现器、持久化输出和直接读取器完成最小 GREEN。GET 不增 HEAD/OPTIONS 活动。保留 route 定位、全入口分母和 XML 安全测试，普通发布不重新调用发现算法；真实源端点仍待整仓验收。
