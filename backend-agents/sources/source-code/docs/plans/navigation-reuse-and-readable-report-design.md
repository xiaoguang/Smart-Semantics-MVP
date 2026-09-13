# 导航复用、单次本地 CI 与业务正文减负

> 2026-09-13 用户已批准的优化设计。**本轮只更新设计，不实施 Java、测试或构建配置。** 整套设计/代码符合性审计按用户最新要求留到下次。代码核对基线是 `080a86db04c4917b27c5a48c88ca136d11bd0f2b`，不是正式目录较旧的工作树。

## 1. 要减少什么，不能减少什么

减少相同 JDT 查询、入口间重复保存的方法正文、质量阶段重复跑单元测试，以及业务 Markdown 中的大段源码。不减少实际实现候选、调用发生点、参数、条件、返回、未展开原因和模型实际看到的完整实现。

八步、四个业务模块、引擎选择、Java/模型分工不变。不新增解析器、缓存服务、复杂恢复、模型轮次或行业规则。JavaParser 保留已有能力；共享保存/阅读合同的改变要同时适配它，不要求它取得与 JDT 相同的解析结果。

### 正式目录与构建基线

- 后续编辑只在 `backend-agents/sources/source-code/` 的正式 checkout；不再把 `/private/tmp` 当开发目录。
- 可信基线由**提交和验证记录**决定，不由目录名决定。已交付基线为 `080a86d`。正式 checkout 的 `appmod/java-upgrade-20260912113141` 当前在 `db28f8d`，较旧。
- 该 checkout 未提交的 `pom.xml`、`.mvn/toolchains.xml` 把宿主 release/toolchain/Enforcer/PMD 整体改成 Java 25。没有证据表明这两项属于已通过 CI 的 JDT 交付，**不纳入本优化提交，也不在设计更新时擅自丢弃**。
- 宿主继续 Java 17；JDT LS/Core 用单独配置的工具 JVM。同步最新源码与处理旧分支属于后续明确的 Git 操作，本轮仅将当前设计同步到正式目录。

## 2. 用两个入口说明为什么可行

先看合成的共享方法例子，用来固定实现和测试行为，不冒充管伊佳实测调用：

```text
入口 A：申请详情 → RequestService.load(id) → RequestMapper.select(id)
入口 B：申请审核 → RequestService.load(id) → RequestMapper.select(id)
```

第一次处理 A，JDT 定位 `load` 和 `select`，Core 取得完整源码。处理 B 时只查询 B 自己尚未处理的调用位置；到达同一个 `load` 方法，复用其导航结果及正文。B 的 `id` 来源仍是 B 的实际调用，不能复用 A 的实参。

得到一个全仓 `METHOD(load)`、一个全仓 `METHOD(select)`，两个入口各自的成员关系和调用投影。若 B 被取消，A 是 `BODY_INCLUDED`、B 是 `NOT_EXPANDED` 可以同时成立，不能因为 A 完整就把 B 改成完整。

再对照真实入口 `UserController.registerUser`：已验证 JDT 能自动找到 `validateCaptcha`、`checkLoginName`、`registerUser` 的 Service 正文。优化以后仍把注册限制、默认属性、保存调用和租户/角色处理交给模型；其他入口再次涉及同一方法时，不再重复导航。**哪些方法在真实四入口间共享，要从已保存材料统计，不凭方法名预估。**

## 3. JdtProjectSession：一次会话拥有复用范围

输入仍为 `VerifiedJavaProject` 和有效工具配置。会话隐藏三份内部表：Core 语法结果、JDT 查询结果、完整方法记录；不新增公开接口或通用缓存框架。

复用基础包括固定源码、源码根及模块关系、语言级别、已批准 classpath 内容、JDT/Core 与 Adapter 版本、影响解析的配置。绝对 workspace 路径不是源码身份，但实际 URI 必须在本会话正确映射。更换任一基础输入就开新会话，不混用缓存。

先完成现有初始化和索引就绪检查，再进行可缓存查询。不能缓存索引未就绪期间的空结果并宣称“找不到”。本轮不设计热更新、索引恢复、跨进程复用 LS handle 或并发缓存失效协议。跨进程只复用已发布的规范化索引，不复活旧 JDT 会话。

Core 语法缓存仍以文件与有效解析配置为粒度。只有注解 binding 依赖的 classpath/sourcepath 变化也必须失效；不能仅比较文件文本。LS 导航与 Core 语法各司其职，不以缓存为名删除其中一种必要能力。

**输出保证：**任意入口使用同一冻结基础的查询结果；共享引用不会把源码位置或候选替换为另一仓库/配置的结果。关闭会话即释放内部缓存。失败沿现有诊断规则处理。

## 4. JdtNavigationResolver：查询一次，保存原始结果

“一个方法只查一次”准确含义是：**同一会话，每个不同导航操作及其目标位置最多执行一次**。不是每方法只允许一条 RPC，更不是有 hierarchy 就省略必要的 implementation。

| 操作 | 去重键的内容（都在同一会话内） | 缓存结果 |
| --- | --- | --- |
| prepareCallHierarchy | 方法声明键 + 查询位置 | 完整原始返回或明确错误 |
| outgoingCalls | prepare 返回的每个不同 hierarchy item 身份 | 完整原始返回或明确错误 |
| definition | 调用/注解的实际 navigationSite | Location/LocationLink 候选或空结果/错误 |
| implementation | 实际请求的声明或调用 navigationSite | 全部实现候选或空结果/错误 |

操作名属于 key，definition 结果不能充当 implementation。item 只在本次会话内有效；多个 hierarchy item 不得只留第一个。归一化后的目标仍按真实位置去重、合并来源，不猜包名或运行时 bean。

缓存必须区分合法空返回、QUERY_FAILED 和已定位候选。局部查询失败在本会话中按原失败复用，不因另一个入口经过而隐式重试；会话不可用/协议损坏仍按现有规则停止。不得为了更高命中率把错误当作空数组。

原始请求/响应只在第一次真实查询时记录一次；命中记录只需关联 query key，不再复制响应。内部 key/原始 payload 不交给模型。必要诊断沿现有私有运行目录保存，不进入公开 metadata artifact，不增加正式步骤、receipt 或新的证据链。已保存原始响应是排障与比较依据，不是重新运行 LS 的输入。

**直接验收：**两入口共享同一方法时，prepare/outgoing 计数不因入口数增加；同一调用位置的 definition/implementation 各最多一次。两个不同源码位置即使表达式相同，也不能合并成同一个查询。

## 5. EntryCodeCollector 与 Step03：共享方法，保留入口差异

Collector 仍从每个入口遍历，决定该入口涉及哪些方法、调用和边界。缓存减少工具工作，不取消每个入口的成员归属计算。

- `methodKey → MethodCode` 是会话级不可变表；完整正文、形参、条件、返回和源码范围保存一次。同键不同正文是错误，不 first-win。
- 调用的源码位置、原文与实参属于物理调用；声明/候选原始定位可以共享。
- 是否已在某入口展开、停止原因、目标正文是否属于该入口和参数对照，是入口投影。继续使用已实现的 `java-code-index-v2`：METHOD 全仓去重，CALL 的保存身份为 `(entryId, callKey)`。
- 多实现、递归和 deferred 回调仍完整保留。缓存不做运行时分派，不把多个入口串成顺序过程。

**不把 v2 再改回全仓唯一完整 CallSite。** 那会重现真实整仓运行中已经暴露的入口展开冲突。缓存的是工具定位结果，不是最终 `EntryCodeContext` 的一个全局副本。

Step03 继续只由现有 module 7 发布 `java-code-index.jsonl`。当前索引已有 METHOD 去重，不新增第二份方法文件。新增 query cache 是私有实现，索引 wire 不因此升版。

## 6. Step05/Capsule：用已保存索引引用代替正文副本

### 输入、输出与唯一所有者

Step05 输入还是同源 Step03 索引、全入口和可选严格增强。**规范化 Java 方法正文的生产权威是索引中的 METHOD**。同进程可以复用不可变对象；磁盘上下文不再把整份 `EntryCodeContext` 复制一遍。

持久化 `FlowCompilation.entryContexts[]` 和公开 `flow-slices.json` 中：保留现有入口、trigger、收集状态/原因、strict refs、Gap 和 limitation 字段；将内嵌 `codeContext` 替换为 `codeContextRef`。

| 字段 | 精确含义 |
| --- | --- |
| codeContextRef.indexArtifact | 现有 `ArtifactReference` 类型，指向同冻结基础的 `PROGRAM_GRAPHS_JAVA_CODE_INDEX`；不是宿主路径 |
| codeContextRef.entryId | 该索引中唯一 ENTRY_MEMBERSHIP 的入口 ID，必须等于外层 entryId |
| codeContextRef（整体） | COLLECTED 时必填；NOT_COLLECTED 时为 null，且保留非空 collectionReason |

METHOD/CALL 的成员集合直接从该索引 membership 读取，不在这个 ref 中再存一份可漂移清单。Capsule 原有 `entryContext` 内嵌对象改为 `entryContextRef`，字段为 `{compilationArtifact: ArtifactReference, entryContextId: string}`，指向已经发布的 compilation；其他 Capsule 范围、严格增强与处置不改变。

### 人类阅读例子

下面为省略 ArtifactReference 外壳的说明投影，不是实测 wire：

```json
{
  "entryContexts": [
    {"entryId":"A","codeContextRef":{"indexArtifact":"同一索引","entryId":"A"}},
    {"entryId":"B","codeContextRef":{"indexArtifact":"同一索引","entryId":"B"}}
  ]
}
```

reader 先打开同一索引一次，再按 A/B 各自 membership 解析共享方法和入口调用。业务消费者仍取得完整 `EntryCodeContext` 不可变视图，不能收到裸 key 后被迫自己调用 JDT。**引用是保存方式，不是删掉模型阅读内容。**

### 保存与读取修改范围

| owning wire | 已交付 | 本优化目标 |
| --- | --- | --- |
| module flow compilation | v5 | v6：codeContextRef |
| public flow slices | v5 | v6：相同上下文引用 |
| module capsule projection | v10 | v11：entryContextRef |
| public evidence capsule | v8 | v9：相同 context 引用 |

源索引保持 v2，`EntryCodeContext` 内存/方法合同保持 v1，Fact accounting 与业务内容 JSON 不因去重升版。生产者、identity 输入、reader、policy 和直接 fixture 在同一实现交付中更新；不能只改 writer。旧历史文件不覆盖，不静默把旧内嵌数据当新引用格式。

引用解开只做既有来源/版本/内容/owner 检查，无需重跑 Collector 或 projector。缺少索引、错误入口、源码漂移为错误；NOT_COLLECTED 仍是明确范围处置。搬移或导出运行必须同时携带已引用上游文件，不能只复制 Capsule 然后说它可独立重开。

## 7. BusinessMaterialBuilder：存储去重不变成模型缺料

Builder 通过上节 reader 获得完整视图，仍选择完整入口/Service 单元、展示调用与实参/形参、分配短引用。一个请求内同一方法只放一次源码，多个入口用该包内短编号指向它。实际序列化模型请求前必须解开引用；Luna 不会读取程序的索引文件。

短来源 ref 在同一个材料集合中稳定且唯一。同一源码范围复用同一 ref；来源不同不能因为文本相同就合并。允许不同请求再次携带必要的共享代码，不能假定模型记得另一次请求的内容。

“正文只保存一次”的范围是**全仓索引及后续普通技术检查点**。为复核实际发给模型的内容而保存的请求，以及最终独立可读的 `source-refs.jsonl`，是有意的传输/交付投影；不开展跨整个磁盘的去重系统。本轮保持现有 `business-materials.jsonl` 的自包含请求形状，不再连带重写四个业务模块协议。

验收必须同时比较保存重开的上下文和真实请求 bytes：注册关键 Service 正文、参数、条件、返回及 limitation 均不缺。只比较 ref 数量不能证明优化正确。本轮不改变模型提示词、分包策略或调用轮次，不以减少源码来换取速度。

## 8. BusinessReportPublisher：九章只讲业务，源码单独保存

输入、DRAFT/完整 REVIEW、九章 JSON 和来源检查不变。Java 排版时不再把源码附在第一章，也不移动到第九章或新增第十章。

| 文件 | 目标内容 |
| --- | --- |
| document.md | 恰好九章业务正文、范围说明和 `[S123]` 这样的短编号；没有方法正文、路径、行号、hash 或 `<details>` 来源块 |
| source-refs.jsonl | 现有完整 ref→冻结文件/行段/原始片段映射，独立于 Markdown；不因某 ref 未在正文出现就删除历史来源 |
| business-report.json | 已审九章内容及每段 refs，不变 |
| report-validation.json | 章节/来源/范围与审阅结果，沿用既有机制 |

正文示例：`系统检查注册限制、初始化用户属性，并保存用户及相关租户关系。[S731]`。这里的编号仅示意；真实 ref 必须来自该份报告的来源集合。读者需要核对时，在同目录 `source-refs.jsonl` 查对应编号即可，不要求前端、HTTP 或新的源码浏览器。

本轮使用纯短编号，不保留指向已删 `#source-ref-*` 锚点的坏链接。第一章可写一句“源码依据见同目录 source-refs.jsonl”，不列代码清单。可读 `sources.md` 等附加导出以后另议，不新增本轮正式文件。

移除的是 renderer 最后的展示附加物，不是 Activity/Process/Report 模型输入。纯重渲染不调用模型；保持原有业务段落、顺序和 refs。Markdown bytes/SHA 必然变化，只生成新的渲染产物，不覆盖用户保存的比较基线；JSON 无字段变化不强制升版，renderer/producer版本按既有身份机制更新。

**准确性边界：**ref存在只证明“可查”，不证明该片段支持这句话。外置源码不会修复引用选错。引用相关性仍归既有完整 REVIEW 与人工样本审阅；相关改进在下次整体审计讨论，不借此增加 Java 自然语言证明系统。

## 9. 本地 CI：一个测试只由一个阶段执行

不删除有效断言，只消除相同交付里的重复执行。宿主保持 Java 17，工具 helper 使用已有独立工具链。详细配置归 Maven/POM，不新造构建系统。

| 类别 | 执行者 | 内容 | 禁止 |
| --- | --- | --- | --- |
| 普通测试 | Surefire，test 阶段 | 纯 Java、录制 LSP、替身工具进程、scripted Provider | 依赖真实 JDT 安装、客户仓库、网络或产品模型 |
| 真实工具集成 | Failsafe，integration-test/verify，显式 real-jdt-it profile | 已安装 LS/Core + 固定源码的真实导航和接线 | 再由 Surefire 执行同一测试；执行客户构建/产品模型 |
| 静态质量 | Enforcer、Spotless check、SpotBugs、PMD | 复用本次编译产物做检查 | profile 强行 skipTests=false；为每个扫描器重跑 test 生命周期 |

目标完整本地 CI 在格式化完成后，用**一次根工程 verify 生命周期**完成 unit→IT→quality。helper 的独立构建先做一次，不使宿主 unit 再执行。真实 JDT 用 `*IT` 命名及明确 Failsafe include/exclude；同类中若既有纯测试又有 live 方法，按性质拆到对应测试类，不仅给名字加后缀。

目标主命令为 `mvn -t .mvn/toolchains.xml -Pquality,real-jdt-it verify`；其中 `real-jdt-it` 是**待实现的显式 profile**，不是当前可直接运行的命令。工具与固定源码未配置时，必须报告 IT 未验证，不把跳过当成功。

默认不使用 `mvn test` 后再 `mvn -Pquality verify` 这种重复组合。当前POM的Surefire读取`skipUTs`，workflow却传`skipTests`，不能据此证明跳过。目标保留已有`skipUTs`作为unit开关，Failsafe改用独立的`skipITs`；默认都为false。仅对同一代码/配置/编译产物补跑静态质量时，用`-Pquality -DskipUTs -DskipITs verify`并核对已有测试证据。只跑IT时用`-DskipUTs`和显式IT profile/selector，不能同时把IT跳过。代码或测试变更后重新运行受影响测试是必要验证，不属于需要取消的重复工作。

验收从 Surefire/Failsafe XML核对类与方法：一次交付生命周期内无交集、每项只执行一次，质量报告仍生成；真实工具测试不混入普通测试默认路径。日常 RED/GREEN只运行当前直接 selector，不因此授权每次修改都跑全仓 CI。

## 10. 优化怎样验收，而不是凭感觉说快了

保留正式目录 `.workspace/comparison-baselines/jdt-four-entries-20260913.rVawqM/` 的旧材料、请求、报告草稿和校验记录，只读。四入口来源固定为原 manifest 精确入口集合，不用“取前四个”代替。

比较同一工具版本、同一classpath、同一源码、同一入口集合；先用录制/合成的共享方法场景保证正确，再对实际材料计数。不调用模型就可以检验本轮三类优化：

- 导航：独立 RPC 数量、命中次数、解析文件数、不同方法数、每入口收集耗时、总耗时。重复入口不会增加已处理 query key 的实际 RPC。
- 保存：索引每methodKey一份正文，Step05/Capsule无重复正文；各入口解开后完整内容与原语义相同，候选/停止原因没有被缓存提升。
- CI：测试执行次数、unit/IT/quality各阶段耗时，不只看 BUILD SUCCESS。
- 报告：同一已审 JSON 的第二至九章业务文本、顺序、refs完全相同；恰好九章，短ref都可从 sidecar查到，正文无源码块。历史 DRAFT-only 不得因重渲染改称 REVIEW完成。

已有长跑耗时包含导航、序列化、调用等待与失败后的重跑，尚无RPC命中统计，不能预先承诺整体加速几倍。模型调用耗时不因磁盘方法去重自动消失，本轮也不调整模型调用次数。

## 11. 实施边界与后续讨论

后续实现依次完成：会话query复用 → Step05/Capsule引用读写贯通 → 正文来源分离 → 本地CI分类及单次运行 → 同输入比较。每项先由 Luna/xhigh 写直接行为 RED，Terra/xhigh实现，Sol/xhigh定位故障；设计偏差由 Sol/ultra 或 Astra/ultra裁决。

本页规定的是已批准的优化目标，不把它写成已经完成的代码。整条设计/代码符合性审计、业务关系质量、报告引用相关性、检查点恢复与统一运行入口的其他差距，**留到下次集中讨论**，本次不顺带修复。
