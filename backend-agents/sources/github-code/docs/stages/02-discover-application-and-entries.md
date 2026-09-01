# 02 发现应用类型和入口

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

Stage 01 只证明“完整冻结仓库的文件没有变”，还没有回答“它们组成什么应用、全部入口在哪里、哪些框架构造能被可靠分析”。本阶段先固定应用形态、**全入口 inventory** 和能力边界，让后面的图以每个真实入口为根，而不是从文件名或模型猜测业务。DepotHead 只占仓库入口集合中的一项。

## 2. 具体输入与 DepotHead 例子

输入是 Stage 01 的 persisted verified **完整仓库** snapshot 与 inventory，加上 application-discovery profile、schema/toolchain hashes 和预算。生产前置要求 `scopeKind=COMPLETE_CAPTURE` 且 `repositoryCompletionEligible=true`；八文件 DepotHead `BOUNDED_PATH_SET` 只能运行局部 walkthrough/诊断，不能关闭 repository coverage。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用连贯目标值展示模块 artifact 接力，不声称当前 DepotHead 已闭合；技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

DepotHead 八文件中可核对的 **REAL_SOURCE**：

- pom.xml 和 application.yml 提供 Java/Spring/MyBatis 候选信号；
- DepotHeadController.java:43 是类级 /depotHead；
- DepotHeadController.java:178-191 是 POST /batchSetStatus 方法；
- DepotHeadMapper.java:8,23 声明 mapper interface 与 updateByExampleSelective；
- DepotHeadMapper.xml:3 声明相同 namespace，:385 声明 statement。

**示例分类：NARRATIVE_ILLUSTRATION。** 目标 **DETERMINISTIC_CONCLUSION** 是发现一个 HTTP entry；下列简写使用旧式locator显示，不是wire：

~~~json
{
  "entryId": "entry:2222222222222222222222222222222222222222222222222222222222222222",
  "kind": "SPRING_MVC_HTTP",
  "protocol": "HTTP",
  "method": "POST",
  "route": "/depotHead/batchSetStatus",
  "routeParts": ["/depotHead", "/batchSetStatus"],
  "handlerFqn": "com.jsh.erp.controller.DepotHeadController#batchSetStatus",
  "parameterNames": ["status", "ids"],
  "routeLocators": [
    "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43",
    "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191"
  ]
}
~~~

这不是现有 `Stage02Compiler`（旧四阶段编号）的 DepotHead success 声明。当前整体 slice 在本次目标 Stage 05 仍为 Gap、0 Flow、0 Capsule。

## 3. 程序怎样工作

1. 重验 Stage 01 receipt、snapshot root、source handles 和 complete-capture 资格。
2. 从完整 inventory 的 Maven/config signal 识别 Java release 和候选依赖；依赖只启用 parser。
3. 在完整 source denominator 上先枚举全部潜在 Spring HTTP site，再按稳定 fileId 分片解析；配置只能定位 inventory 内的 MyBatis mapper files。
4. 解析 package/type/method/annotation，并合并类级、方法级 Spring MVC route；每个 candidate site 恰一处置。
5. 记录 handler 参数、返回类型、注解与 locator，不在此解释业务意义。
6. 发现 MyBatis namespace、Mapper method 和 XML statement 候选，形成 catalog；唯一 binding 留给 Stage 03 call graph。
7. 对每个 site/entry记录 SUPPORTED、UNSUPPORTED、AMBIGUOUS 或 OVER_LIMIT；EXCLUDED 必须有 reasonCode/evidenceRefs，Gap 必须有 gapId。
8. 验证 shard denominator 不交叠且 union 等于全 inventory discovery denominator；禁止 sample、first-N 或因超限丢 site。
9. 重算 source/site/entry/catalog coverage，canonicalize、自验、原子安装 stages/02-discover-application-and-entries/。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| application-profile.json | 观察到的语言/框架/config signals 与有效 capability profile |
| entry-points.jsonl | 每行一个入口、route parts、handler、params/return 和 locator refs |
| mapper-catalog.jsonl | Mapper Java/XML 候选、namespace、statement 和配置来源 |
| capability-report.json | 全仓 source/site/entry/catalog dispositions、shard receipts 与 repositoryEntryCoverage |
| stage-receipt.json | upstream root、control hashes、artifact set 和 Gap 计数 |

**示例分类：NARRATIVE_ILLUSTRATION。** DepotHead target application profile投影如下；locator简写和signals不是可加载wire：

~~~json
{
  "applicationProfileId": "application-profile:1111111111111111111111111111111111111111111111111111111111111111",
  "snapshotId": "snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "inventoryScopeKind": "BOUNDED_PATH_SET",
  "repositoryCompletionEligible": false,
  "language": "JAVA",
  "languageVersion": 8,
  "frameworkSignals": [
    {"kind": "MYBATIS", "locator": "jshERP-boot/pom.xml:1", "disposition": "SUPPORTED"},
    {"kind": "SPRING_MVC", "locator": "jshERP-boot/pom.xml:1", "disposition": "SUPPORTED"}
  ],
  "configSignals": [
    {"kind": "MYBATIS_MAPPER_LOCATION", "locator": "jshERP-boot/src/main/resources/application.yml:1", "value": "classpath:mapper_xml/*.xml", "disposition": "SUPPORTED"}
  ],
  "capabilityProfileRef": {"artifactId": "capability-profile:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}
}
~~~

上面的profile是字段完整的目标record；“一个HTTP entry”只指本文 walkthrough 的entry投影，不是全八文件中所有可能入口的实测 golden，也不是profile内的额外计数字段。

目标 DepotHead 正向出口必须至少包含上面的完整 HTTP entry、一个 application profile、相关 Mapper catalog candidates、全部发现 site 的 disposition 和非空 receipt；它只是全仓入口 inventory 中一项。若完整仓库没有可发现入口，仍要安装完整五文件 artifact set，并在 `capability-report.json` 中记录 `NO_ENTRY_DISCOVERED`、全 inventory 搜索证据与零分母依据，状态只能是 SUCCEEDED_WITH_GAPS；不得省略文件或假造入口。

Stage 02 completion 还要求 `repositoryEntryCoverage` 闭合：每个 discovery site 和每个形成的 `entryId` 都有唯一 disposition，所有 shard receipts 的 denominator union 与未分片 ID 集完全相同。一条 DepotHead entry 成功、其他 entry 未处置时，Stage 02 和整个 run 都不能完成。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下列每行只解释接力，不是module envelope或跨stage replay fixture。

~~~jsonl
{"module":"application-profile","artifact":"modules/01-application-profile/application-profile-draft.json","takesFrom":["Stage01Reference"],"says":{"snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","language":"JAVA","languageVersion":8,"frameworkSignalKinds":["SPRING_MVC","MYBATIS"]}}
{"module":"http-entry","artifact":"modules/02-http-entry/http-entry-discovery.json","takesFrom":["application-profile-draft.json","verified Java bytes"],"says":{"entryId":"entry:2222222222222222222222222222222222222222222222222222222222222222","protocol":"HTTP","method":"POST","route":"/depotHead/batchSetStatus","handlerFqn":"com.jsh.erp.controller.DepotHeadController#batchSetStatus"}}
{"module":"mapper-catalog","artifact":"modules/03-mapper-catalog/mapper-catalog-draft.json","takesFrom":["application-profile-draft.json","verified config/Mapper bytes"],"says":{"catalogEntryId":"mapper-catalog-entry:4444444444444444444444444444444444444444444444444444444444444444","javaMethod":"DepotHeadMapper#updateByExampleSelective","xmlStatement":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","bindingState":"CANDIDATE_NOT_YET_BOUND"}}
{"module":"publish","artifacts":"modules/04-publish/<four-semantic-files>","receipt":"modules/04-publish/module-receipt.json","takesFrom":["application-profile-draft.json","http-entry-discovery.json","mapper-catalog-draft.json"],"says":{"entryCount":1,"catalogEntryCount":1,"semanticFileCount":4,"stageReceipt":null,"nextStep":"CanonicalStageArtifactStore"}}
~~~

M2 和 M3 共享同一个 applicationProfileId，但互不读取对方私有结果；M4 才把 HTTP entry 与 Mapper candidates放入同一个可交给 Stage 03 的 Stage02Reference。`CANDIDATE_NOT_YET_BOUND` 明确阻止 M3 提前宣布 Java→XML 唯一绑定。

## 5. 下游怎样消费而不返工

Stage 03 直接读取 application-profile.json、entry-points.jsonl、mapper-catalog.jsonl 和 capability-report.json。它按 entryId 建图根，按 catalog identity 寻找唯一 binding；不能重新解析 pom/config/annotation 来改变入口分母。

若 Stage 03 发现 catalog 不足，应产生 graph Gap 或要求新版本 Stage 02 schema/profile；不得在 Stage 03 私下“多读一个文件”。

### 下游前置条件与后置保证

| Stage 03 开始前必须成立 | Stage 02 成功后保证 |
| --- | --- |
| Stage 01 root 与 Stage 02 receipt/control hashes 重验一致 | 每个发现入口、Mapper candidate 和 capability site 都有 stable identity 与 disposition |
| application profile 唯一，四个数据文件及 receipt 的 artifact set/root 闭合 | route 同时保留 class/method parts；入口分母不会因 unsupported/ambiguous 而缩小 |
| `entry-points.jsonl` 非空，或 capability report 明确给出 `NO_ENTRY_DISCOVERED` Gap | Stage 03 可只靠 persisted entry/catalog identities 选择图根，无需重解析发现材料 |
| `repositoryEntryCoverage` 的 source/site/entry ID 集与 Stage01 complete inventory/shard receipts 精确闭合 | Stage03 获得全入口 graph roots；所有 unsupported/failed/omitted item都有 evidence-backed disposition而非孤儿 |

Stage 03 只能接受这些保证；发现缺项时必须 Gap/fatal 或触发新 run，不能局部重做 Stage 02。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：完整仓库的应用 profile 可确定，全部 discovery site/entry 有唯一处置，shard union、entry/catalog/site accounting 闭合，阶段目录安装。
- **带 Gap 成功**：某 entry/site 为 UNSUPPORTED、AMBIGUOUS 或 OVER_LIMIT，但保留 locator、affected entries 和 reason。
- **fatal**：snapshot reopen 漂移、application profile 与声明 dependency 自相矛盾、catalog duplicate identity、XML parser 安全策略不可执行、coverage 方程不闭合或 artifact install 失败。
- **显式复用**：新Stage03 execution只有在upstream artifact root、shard receipts和input/tool/profile/schema/prompt hashes完全相等时才读取Stage02；缺/重叠shard不能宣布完成，prompt hash本阶段必须为null。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 识别语言/框架候选 | 是 | 否 |
| 发现 route/handler/mapper catalog | 是 | 否 |
| 决定支持/歧义/范围外 | 是，按版本化 profile | 否 |
| 给入口起业务名称 | 否，留到 Stage 06 | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `ApplicationProfileDetector` → `SpringHttpEntryDiscoverer` 与 `MapperCapabilityCataloger`（二者只共享M1输出，可并行）→ `Stage02PublicationSpecifier` → `CanonicalStageArtifactStore`。specifier先安装四个semantic payload+module receipt；Stage store重开它并最后创建receipt，且不是第五个业务模块。

#### M1 ApplicationProfileDetector

- **解决的问题**：在不运行构建的前提下确定应启用哪些静态 parser/profile，并把“依赖信号”与“已证明行为”分开。
- **精确上游输入及前置**：valid Stage01Reference、`verified-snapshot.json`、complete inventory/source handles、版本化 discovery profile/toolchain/budget；生产路径还要求 Stage01 `repositoryCompletionEligible=true`，bounded fixture只能诊断。
- **确定性顺序 / LLM**：按 profile 指定 path 解析 Maven/config → 提取 Java release、dependency/config signals → 交叉检查冲突 → 选择唯一 capability profile；0 LLM。
- **目标输出与 DepotHead 示例**：`ApplicationProfile{language=JAVA,languageVersion=8,frameworkSignals=[SPRING_MVC,MYBATIS],inventoryScope,repositoryCompletionEligible}` 加 signal dispositions；DepotHead例为 BOUNDED/false，只启用 parser，不声称运行时已部署；生产 complete profile继承true。
- **必须保持的不变量**：每个 signal 有 locator/disposition；dependency 只启用 capability；profile identity 绑定 snapshot/profile/toolchain，不能绑定显示名称或机器 root。
- **Gap / fatal / artifact复用**：可隔离的未知 version/config site 是 Gap；profile 无法唯一确定、signal 冲突、source drift 或安全 parser 不可执行是 fatal；模块只读取相同bytes/profile的已验证上游。
- **给下游的后置保证**：M2/M3 只使用该 profile 明示的 parser/rules，并知道 inventory 内哪些 config/Java/XML 资源可读。
- **明确非目标**：不发现 HTTP entry，不唯一绑定 Mapper，不运行 Maven、classloader、Spring 或 MyBatis。
- **公共测试 seam 与验收**：`detect(Stage01Reference, DiscoveryProfile)` 对 Java 8/Spring/MyBatis fixture 产生唯一 profile；version conflict、inventory 外 config、dependency decoy 和不同 root replay 有确定结果。
- **Luna/xhigh 测试指南**：创建 `Stage02ApplicationProfileDetectorTest`，冻结 Stage01 artifacts、pom/config和手写golden于 `src/test/resources/target/stage02/application-profile/`。一个行为一个RED：Java8/Spring/MyBatis正向、dependency仅signal、version conflict、inventory外config、budget Gap、root/order determinism；首RED应因public detector/schema缺失失败。只可fake只读source handle，禁止mock parser/canonical/identity。命令：`mvn -Dtest=Stage02ApplicationProfileDetectorTest test`；无网络/build/Provider。偏离按DESIGN 13.11交Sol/ultra。
- **Terra/xhigh 实现指南**：观察RED后仅改 `target/stage02/application-profile/`，实现 public `ApplicationProfileDetector/ApplicationProfile` 与 `stage02-application-profile-draft-v2`；只读Stage01 artifact/handles，按Maven/config→typed source excerpts→closed signals→conflict→profile，禁止运行Maven或把dependency当行为。逐RED GREEN，最终canonical/root replay稳定并更新审计；需新signal语义/跨阶段字段时MUST STOP。

#### M2 SpringHttpEntryDiscoverer

- **解决的问题**：固定 HTTP 入口分母、完整 route、handler 和参数身份，让图构建有明确根。
- **精确上游输入及前置**：M1 `ApplicationProfile`、Stage 01 complete verified Java handles、Spring route registry、stable file shards 与 entry budget；profile 必须声明 SPRING_MVC capability。
- **确定性顺序 / LLM**：先枚举全 inventory annotation-site denominator → 按 fileId shard解析 package/type/method/annotations → 静态求值 route parts → 确定性笛卡尔合并 method/path → 建 EntryPoint/dispositions → shard union/accounting；0 LLM。
- **目标输出与 DepotHead 示例**：`EntryPointV2{entryId,kind=SPRING_MVC_HTTP,protocol=HTTP,method=POST,route=/depotHead/batchSetStatus,routeParts,handlerFqn,parameterNames,routeSourceExcerpts}`；两个连续`SourceExcerptV1`分别覆盖类级 :43 和方法级 :178-191，不能合成一个span。
- **必须保持的不变量**：每个 complete-capture candidate site 恰一 disposition；shard denominators不交叠且union精确等于总site IDs；完整 route 保留 parts；同 protocol/method/route/handler identity 不重复；动态表达式不猜值。
- **Gap / fatal / artifact复用**：单 site dynamic/ambiguous/over-limit 是带 affected entry 的 Gap；route identity 冲突、accounting/reference/source drift 是 fatal；模块只读取相同profile/root。
- **给下游的后置保证**：M4/Stage 03 获得稳定 entryId、route parts、handler/param nodes 和完整入口 denominator，无需 reparse annotation。
- **明确非目标**：不沿调用链、不命名业务、不把 Controller 方法存在等同于完整 Flow。
- **公共测试 seam 与验收**：`discoverEntries(ApplicationProfile, SourceHandleSet)` 覆盖 DepotHead prefix+suffix、第二个非空 HTTP entry、动态 route、overload/decoy、annotation deletion、无入口 Gap、缺/重叠 shard；两入口在不同 shard/顺序下产生同 bytes，只有 prefix/suffix/handler refs 都在才 admitted。
- **Luna/xhigh 测试指南**：创建 `Stage02SpringHttpEntryDiscovererTest`，fixtures/goldens放 `src/test/resources/target/stage02/http-entry/`，冻结真实DepotHead route spans与decoys。RED依次为prefix+suffix正向golden、缺prefix、dynamic route Gap、overload/duplicate fatal、无入口Gap、order determinism；初始因seam/artifact未实现失败。只可fakeSourceHandle，不能mock annotation parser/route merge。命令：`mvn -Dtest=Stage02SpringHttpEntryDiscovererTest test`；禁网络/客户Maven/private耦合。偏离按DESIGN 13.11交`gpt-5.6-sol / ultra` Design Authority。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage02/http-entry/`，实现 public `SpringHttpEntryDiscoverer/EntryDiscovery` 与 `stage02-http-entry-discovery-v2`；输入只取M1 artifact+Stage01 handles，按parse→enumerate→static evaluate→route product→typed excerpt/disposition。逐slice GREEN且golden route evidence同时含两个连续spans；不得用文件名/模型补route。schema或上游不足即STOP交Sol/ultra，完成后更新审计。

#### M3 MapperCapabilityCataloger

- **解决的问题**：登记后续建图所需的 Mapper Java/XML/config candidates 和所有受支持/不支持 site，而不抢先做调用绑定。
- **精确上游输入及前置**：M1 profile、complete verified Mapper Java/XML/config handles、resource shards、MyBatis parser/security policy 与 budget；所有资源必须在 Stage 01 inventory且每个匹配/未匹配 resource都有处置。
- **确定性顺序 / LLM**：解析 config mapper resources → Java interface/method candidates → XML namespace/statements/includes → 建 catalog entries → 对全部 sites 分类/account；0 LLM。
- **目标输出与 DepotHead 示例**：`MapperCatalogEntry{javaInterfaceTypeId,javaMethodCandidateIds,xmlResourcePath,xmlNamespaceNodeId,xmlStatementCandidates}`；例子把 Mapper Java :23 与 XML :3/:385 作为 candidates，不在此声称唯一 binding。
- **必须保持的不变量**：catalog candidate 有 source identity；全 inventory相关resources/sites进入四种 disposition；resource shard union闭合；XML resolver 零外部访问；candidate 相同名称不自动合并。
- **Gap / fatal / artifact复用**：局部 dynamic resource/ambiguous candidate 为 Gap；duplicate identity、broken resource ref、XML 安全失败、accounting 不闭合为 fatal；模块只读取已验证persisted source/profile。
- **给下游的后置保证**：Stage 03 可按 typed candidates 和 locators 做 receiver/method/namespace/statement 唯一 binding，不必重扫 config/XML。
- **明确非目标**：不产生 CALL edge、SQL data-flow、Fact 或 Flow；不因字符串相同宣布 Mapper binding。
- **公共测试 seam 与验收**：`catalogMappers(ApplicationProfile, SourceHandleSet)` 覆盖 DepotHead Java/XML、同名 decoy、namespace mismatch、standard DOCTYPE 零网络和 external entity fatal。
- **Luna/xhigh 测试指南**：创建 `Stage02MapperCapabilityCatalogerTest`，fixtures/goldens在 `src/test/resources/target/stage02/mapper-catalog/`。RED顺序：DepotHead candidate catalog、同名decoy不合并、namespace mismatch Gap、standard DOCTYPE零resolver、external entity fatal、site accounting/determinism；首RED应因cataloger/schema缺失。只可fake resolver/source boundary并断言零外呼，禁止mock XML/catalog core。命令：`mvn -Dtest=Stage02MapperCapabilityCatalogerTest test`；禁网络/客户runtime。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage02/mapper-catalog/`，实现 public `MapperCapabilityCataloger/MapperCatalogDraft` 与 `stage02-mapper-catalog-draft-v2`；只用M1+Stage01，config→Java→XML→typed site/evidence disposition，external resolution fail closed。GREEN逐个覆盖candidate/Gap/security/accounting；不得提前绑定Java→XML或扩inventory。缺必要config/跨stage调整MUST STOP，审计同步。

#### M4 Stage02PublicationSpecifier

- **解决的问题**：合并 M1–M3 的独立发现结果，验证分母和引用后形成不可变 Stage 02 出口。
- **精确上游输入及前置**：一个 ApplicationProfile、M2 entries/sites/shard receipts/dispositions、M3 catalog/sites/shard receipts/dispositions、Stage01 complete root 和 Stage02 controls；各模块结果均已局部验证。
- **确定性顺序 / LLM**：合并全仓site/catalog→验证shards/accounting/refs→canonical四个semantic payload→M4 module install/receipt→Stage store fresh reopen/root/store-last receipt；0 LLM。
- **目标输出与 DepotHead 示例**：M4恰四个semantic files；Stage store形成四项+receipt的五文件stage set。正向例含一个POST entry与Mapper candidates；无入口例仍有profile/capability和store receipt。
- **必须保持的不变量**：complete discovery denominator = dispositions；每个 entry恰一状态，任何 omitted/failed均有Gap/排除证据；一entry成功不关闭其余entry；artifact names/roots/controls 完整；publisher 不改变 M1–M3 semantic decision。
- **Gap / fatal / artifact复用**：局部 Gap 进入 capability report；cross-module ref/accounting/canonical/install/collision 错误 fatal；下游只认完整 stage receipt，不读取 staging。
- **给下游的后置保证**：Stage 03 得到 immutable Stage02Reference，所有图根与 Mapper candidates 都能仅从 persisted artifacts 解析。
- **明确非目标**：不补 entry/catalog、不重新解析源码、不构图或调用模型。
- **公共测试 seam 与验收**：`specify(profile, entries, catalog, capabilityReport)`覆盖相同accounting，并验证M4 exact-four、Stage store five-output、receipt-last/provenance和各partial-install/collision。
- **Luna/xhigh 测试指南**：创建 `Stage02PublicationSpecifierTest`，模块/goldens放 `src/test/resources/target/stage02/publish/`；RED依次four-semantic→five-stage-output→NO_ENTRY→accounting→receipt-last/partial-install/fresh-reopen，使用真实module/stage stores。命令：`mvn -Dtest=Stage02PublicationSpecifierTest test`；无网络/Provider/客户Maven。
- **Terra/xhigh 实现指南**：只读M1–M3，安装exact-four M4 payload+receipt，再用typed Stage store产生receipt/Stage02Reference；不得生成旧single publication summary或预报root/receipt。

### 8.0.1 模块 artifact wire schemas

M1–M3使用 DESIGN 13.3 `ModuleArtifact<T>` envelope；M4直接安装四个stage schema注册的JSON/JSONL semantic bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| module artifact | schemaVersion / artifactType | 精确 upstream | payload、来源、排序 |
| --- | --- | --- | --- |
| `modules/01-application-profile/application-profile-draft.json` | `stage02-application-profile-draft-v2` / `STAGE02_APPLICATION_PROFILE_DRAFT` | exact `capability-profile`、Stage01 `source-inventory`、`verified-snapshot` ArtifactReferences | `applicationProfileId!`、`snapshotId!`、`inventoryScopeKind!`、`repositoryCompletionEligible!`、`language!`、`languageVersion?`、`frameworkSignals[]!`FrameworkSignalV2`、`configSignals[]!`ConfigSignalV2`、`capabilityProfileRef!`；signals按kind+`sourceExcerpt.locator.(path,startByte,endByteExclusive)`排序，unknown version用null+Gap |
| `modules/02-http-entry/http-entry-discovery.json` | `stage02-http-entry-discovery-v2` / `STAGE02_HTTP_ENTRY_DISCOVERY` | exact M1 `application-profile`、Stage01 `source-inventory`、`verified-snapshot` ArtifactReferences | `applicationProfileId!`、`entries[]!{entryId!,kind!,protocol!,method!,route!,routeParts[]!,handlerFqn!,parameterNames[]!,routeSourceExcerpts[]!}`、`sites[]!`为下述exact `CapabilitySiteV2`、`shardReceipts[]!{shardId!,denominatorSiteIds[]!,dispositionSiteIds[]!,status!,gapIds[]!}`、`denominator!`；entries按entryId，sites/shards按ID，参数/routeParts保持源码语义顺序，excerpts按locator排序 |
| `modules/03-mapper-catalog/mapper-catalog-draft.json` | `stage02-mapper-catalog-draft-v2` / `STAGE02_MAPPER_CATALOG_DRAFT` | exact M1 `application-profile`、Stage01 `source-inventory`、`verified-snapshot` ArtifactReferences | `applicationProfileId!`、`catalogEntries[]!{catalogEntryId!,javaInterfaceFqn!,javaMethodCandidates[]!,xmlResourcePath!,xmlNamespace!,xmlStatementCandidates[]!,bindingState!}`、`sites[]!`为同一个exact `CapabilitySiteV2`、`shardReceipts[]!`、`denominator!`；catalog/sites/shards按ID，candidate arrays按canonical signature/statement key |
| `modules/04-publish/<four registered semantic filenames>` | `stage02-application-profile-v2`、`stage02-entry-points-v2`、`stage02-mapper-catalog-v2`、`stage02-capability-report-v2`；无summary envelope | M1/M2/M3 IDs+SHAs | 一次module install恰`application-profile.json/entry-points.jsonl/mapper-catalog.jsonl/capability-report.json`；各payload含自己的coverage/accounting，module receipt绑定四descriptors；禁止stage root/receipt或五项published list；Stage store绑定M4 reference |

Stage02 v2的closed value registry只有：`FrameworkSignalKindV2={SPRING_MVC,MYBATIS}`、`ConfigSignalKindV2={MYBATIS_MAPPER_LOCATION}`、`SignalDispositionV2={SUPPORTED,UNSUPPORTED,AMBIGUOUS,OVER_LIMIT}`、`CapabilitySiteKindV2={HTTP_ENTRY_DECLARATION,MAPPER_RESOURCE_DECLARATION}`。未知kind不得作为字符串透传；需新kind时先升schema/profile version。

exact records为：`FrameworkSignalV2(kind, sourceExcerpt, disposition, reasonCode)`；`ConfigSignalV2(kind, sourceExcerpt, value, disposition, reasonCode)`；其中`sourceExcerpt`必须是DESIGN §13.2的`SourceExcerptV1`，`value`与`reasonCode`是required-nullable。`CapabilityEvidenceRefV2(kind,sourceExcerpt,artifactEvidence)`是closed tagged union：`SOURCE_EXCERPT{sourceExcerpt!,artifactEvidence=null}`或`ARTIFACT_REFERENCE{sourceExcerpt=null,artifactEvidence!}`，两个required-nullable槽均必须出现且恰一非null；`VersionedArtifactEvidenceV2(artifactRef,artifactType,schemaVersion)`保存完整ArtifactReference与被policy registry验证的exact type/version，不得使用bare string ID或从artifactId prefix猜version。

`CapabilitySiteV2(siteId, kind, primaryLocator, affectedEntryIds, disposition, reasonCode, evidenceRefs)`在M2/M3只有这一个shape；`primaryLocator`是完整`SourceLocatorV1`，`reasonCode`是required-nullable，`evidenceRefs[]`是`CapabilityEvidenceRefV2`且至少有一个`SOURCE_EXCERPT`的locator逐字段等于`primaryLocator`。`affectedEntryIds[]`按UTF-8 ID，`evidenceRefs[]`按`kind`再按excerpt locator或artifactId/SHA排序。M2的HTTP site必须列出它生成或处置的entry，M3的Mapper site列出静态可关联的entry（无法关联时为空且有reason/Gap），二者都不能使用未声明的singular `entryId`、string locator或bare evidence ID。M4的完整module fixture用一次install request绑定M1 `application-profile`、M2 `entry-discovery`、M3 `mapper-catalog`三个ArtifactReferences，四个standalone payload本身不重复wrapper。

resolved值只能来自静态parser/rules。动态route/config使用nullable field+site Gap；不能填故事值。`bindingState` v2只允许 `CANDIDATE_NOT_YET_BOUND`，唯一binding由Stage03产生。fatal写ModuleFailure；success failureRef=null。任何field/enum/source/sort/identity语义变化先改设计并升version，旧reader exact-version fail closed。

`repositoryEntryCoverage.closed` 当且仅当 Stage01 `COMPLETE_CAPTURE/repositoryCompletionEligible=true`、全部 discovery shards闭合且每site/entry有唯一处置；bounded walkthrough即使其局部ID集守恒也必须为false。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（两个隔离的 CapabilitySiteV2 records，不可replay）。** 两个对象逐字段展示同一个authoritative record、closed enum、typed locator/excerpt和versioned artifact evidence；hex/offset/line-column未从本页展示的完整source bytes重算，所以不得直接复制为golden，也不与其他stage的显示ID建立映射。

~~~jsonl
{"siteId":"site:3333333333333333333333333333333333333333333333333333333333333333","kind":"HTTP_ENTRY_DECLARATION","primaryLocator":{"fileId":"source-file:1111111111111111111111111111111111111111111111111111111111111111","path":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","startByte":6400,"endByteExclusive":6431,"startLine":178,"startColumn":5,"endLine":178,"endColumn":36},"affectedEntryIds":["entry:2222222222222222222222222222222222222222222222222222222222222222"],"disposition":"SUPPORTED","reasonCode":null,"evidenceRefs":[{"kind":"SOURCE_EXCERPT","sourceExcerpt":{"locator":{"fileId":"source-file:1111111111111111111111111111111111111111111111111111111111111111","path":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java","startByte":6400,"endByteExclusive":6431,"startLine":178,"startColumn":5,"endLine":178,"endColumn":36},"rawUtf8":"@PostMapping(\"/batchSetStatus\")","rawUtf8Sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"artifactEvidence":null}]}
{"siteId":"site:5555555555555555555555555555555555555555555555555555555555555555","kind":"MAPPER_RESOURCE_DECLARATION","primaryLocator":{"fileId":"source-file:4444444444444444444444444444444444444444444444444444444444444444","path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","startByte":28,"endByteExclusive":86,"startLine":3,"startColumn":9,"endLine":3,"endColumn":67},"affectedEntryIds":["entry:2222222222222222222222222222222222222222222222222222222222222222"],"disposition":"SUPPORTED","reasonCode":null,"evidenceRefs":[{"kind":"ARTIFACT_REFERENCE","sourceExcerpt":null,"artifactEvidence":{"artifactRef":{"artifactId":"capability-profile:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},"artifactType":"CAPABILITY_PROFILE","schemaVersion":"capability-profile-v2"}},{"kind":"SOURCE_EXCERPT","sourceExcerpt":{"locator":{"fileId":"source-file:4444444444444444444444444444444444444444444444444444444444444444","path":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","startByte":28,"endByteExclusive":86,"startLine":3,"startColumn":9,"endLine":3,"endColumn":67},"rawUtf8":"namespace=\"com.jsh.erp.datasource.mappers.DepotHeadMapper\"","rawUtf8Sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},"artifactEvidence":null}]}
~~~

**示例分类：NARRATIVE_ILLUSTRATION（三个隔离投影）。** 下面保留DepotHead数据接力故事，其string locator/evidence是人类简写，不是v2 wire、schema-valid fixture或cross-stage replay chain。Luna fixture必须以上述单一权威records替换这些简写，并从实际bytes重算全部ID/SHA；不存在从下列story IDs到Stage03的隐式remap。

~~~jsonl
{"storyProjection":"application-profile","artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","producer":{"address":{"kind":"STAGE","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","stageNumber":2,"stageKey":"discover-application-and-entries","moduleNumber":1,"moduleKey":"application-profile"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"capability-profile:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},{"artifactId":"stage01-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","inventoryScopeKind":"BOUNDED_PATH_SET","repositoryCompletionEligible":false,"language":"JAVA","languageVersion":8,"frameworkSignals":[{"kind":"MYBATIS","locator":"jshERP-boot/pom.xml:1","disposition":"SUPPORTED"},{"kind":"SPRING_MVC","locator":"jshERP-boot/pom.xml:1","disposition":"SUPPORTED"}],"configSignals":[{"kind":"MYBATIS_MAPPER_LOCATION","locator":"jshERP-boot/src/main/resources/application.yml:1","value":"classpath:mapper_xml/*.xml","disposition":"SUPPORTED"}],"capabilityProfileRef":{"artifactId":"capability-profile:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}}}
{"storyProjection":"http-entry","artifactId":"entry-discovery:2222222222222222222222222222222222222222222222222222222222222222","producer":{"address":{"kind":"STAGE","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","stageNumber":2,"stageKey":"discover-application-and-entries","moduleNumber":2,"moduleKey":"http-entry"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","entries":[{"entryId":"entry:2222222222222222222222222222222222222222222222222222222222222222","kind":"SPRING_MVC_HTTP","protocol":"HTTP","method":"POST","route":"/depotHead/batchSetStatus","routeParts":["/depotHead","/batchSetStatus"],"handlerFqn":"com.jsh.erp.controller.DepotHeadController#batchSetStatus","parameterNames":["status","ids"],"routeLocators":["jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43","jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191"]}],"sites":[{"siteId":"site:3333333333333333333333333333333333333333333333333333333333333333","locator":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191","disposition":"SUPPORTED","reasonCode":null,"evidenceRefs":["jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191"],"kind":"HTTP_ENTRY_DECLARATION","affectedEntryIds":["entry:2222222222222222222222222222222222222222222222222222222222222222"]}],"shardReceipts":[{"shardId":"entry-shard:6666666666666666666666666666666666666666666666666666666666666666","denominatorSiteIds":["site:3333333333333333333333333333333333333333333333333333333333333333"],"dispositionSiteIds":["site:3333333333333333333333333333333333333333333333333333333333333333"],"status":"SUCCEEDED","gapIds":[]}],"denominator":{"candidateSites":1,"supported":1,"unsupported":0,"ambiguous":0,"overLimit":0}}}
{"storyProjection":"mapper-catalog","artifactId":"mapper-catalog:3333333333333333333333333333333333333333333333333333333333333333","producer":{"address":{"kind":"STAGE","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","stageNumber":2,"stageKey":"discover-application-and-entries","moduleNumber":3,"moduleKey":"mapper-catalog"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","catalogEntries":[{"catalogEntryId":"mapper-catalog-entry:4444444444444444444444444444444444444444444444444444444444444444","javaInterfaceFqn":"com.jsh.erp.datasource.mappers.DepotHeadMapper","javaMethodCandidates":["updateByExampleSelective(DepotHead,DepotHeadExample)"],"xmlResourcePath":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","xmlNamespace":"com.jsh.erp.datasource.mappers.DepotHeadMapper","xmlStatementCandidates":["updateByExampleSelective"],"bindingState":"CANDIDATE_NOT_YET_BOUND"}],"sites":[{"siteId":"site:5555555555555555555555555555555555555555555555555555555555555555","locator":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:385","disposition":"SUPPORTED","reasonCode":null,"kind":"MAPPER_RESOURCE_DECLARATION","affectedEntryIds":["entry:2222222222222222222222222222222222222222222222222222222222222222"],"evidenceRefs":["jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:385"]}],"shardReceipts":[{"shardId":"mapper-shard:7777777777777777777777777777777777777777777777777777777777777777","denominatorSiteIds":["site:5555555555555555555555555555555555555555555555555555555555555555"],"dispositionSiteIds":["site:5555555555555555555555555555555555555555555555555555555555555555"],"status":"SUCCEEDED","gapIds":[]}],"denominator":{"candidateSites":1,"supported":1,"unsupported":0,"ambiguous":0,"overLimit":0}}}
~~~

### 8.1 Interface 与 records

~~~java
interface ApplicationDiscoverer {
    Stage02Reference discover(Stage01Reference frozenSource);
}
~~~

~~~text
ApplicationProfile
  applicationProfileId
  snapshotId
  inventoryScopeKind
  repositoryCompletionEligible
  language
  languageVersion
  frameworkSignals[]
  configSignals[]
  capabilityProfileRef

EntryPointV2
  entryId
  kind
  protocol
  method
  route
  routeParts[]
  handlerFqn
  parameterNames[]
  routeSourceExcerpts[]: SourceExcerptV1

MapperCatalogEntry
  catalogEntryId
  javaInterfaceFqn
  javaMethodCandidates[]
  xmlResourcePath
  xmlNamespace
  xmlStatementCandidates[]
  bindingState

FrameworkSignalV2
  kind: SPRING_MVC | MYBATIS
  sourceExcerpt: SourceExcerptV1
  disposition: SUPPORTED | UNSUPPORTED | AMBIGUOUS | OVER_LIMIT
  reasonCode?

ConfigSignalV2
  kind: MYBATIS_MAPPER_LOCATION
  sourceExcerpt: SourceExcerptV1
  value?
  disposition: SUPPORTED | UNSUPPORTED | AMBIGUOUS | OVER_LIMIT
  reasonCode?

CapabilityEvidenceRefV2
  kind: SOURCE_EXCERPT | ARTIFACT_REFERENCE
  sourceExcerpt?: SourceExcerptV1
  artifactEvidence?: VersionedArtifactEvidenceV2

VersionedArtifactEvidenceV2
  artifactRef: ArtifactReference
  artifactType
  schemaVersion

CapabilitySiteV2
  siteId
  kind: HTTP_ENTRY_DECLARATION | MAPPER_RESOURCE_DECLARATION
  primaryLocator: SourceLocatorV1
  affectedEntryIds[]
  disposition: SUPPORTED | UNSUPPORTED | AMBIGUOUS | OVER_LIMIT
  reasonCode?
  evidenceRefs[]: CapabilityEvidenceRefV2

RepositoryEntryCoverage
  sourceFileIds[]
  discoverySiteIds[]
  entryIds[]
  supportedEntryIds[]
  gappedEntryIds[]
  excludedEntryIds[]
  shardReceiptIds[]
  closed
~~~

Entry route 必须同时保存 class/method route parts；完整 route 不得只引用 DepotHeadController.java:178 而遗漏 :43。

### 8.2 Identity 与 accounting

entryId 依赖 snapshotId、entry kind、protocol、route parts、handlerFqn、parameter names、排序`routeSourceExcerpts[]`的exact locator/rawUtf8/SHA 和 discovery rule version。它不依赖显示名称。

必须满足：

~~~text
reachableSemanticSites =
  supported + unsupported + ambiguous + overLimit

discoveredEntries =
  supportedEntries + gappedEntries + reasonedExclusions

allDiscoveryShardSiteIds = exactDisjointUnion(shardDenominatorSiteIds)
allDiscoveryShardSiteIds = reachableSemanticSiteIds
~~~

失败不能缩小分母。applicationProfileId、catalog IDs、capabilityReportId 和 Stage02 receipt root 按无环顺序计算。

### 8.3 算法、预算与安全

- package/type/method/annotation 由静态 parser 读取 verified bytes。
- Spring route 只在 annotation value 可静态确定时合并；动态表达式为 Gap。
- config path 只能解析到 Stage 01 inventory。
- MyBatis standard DOCTYPE 可以存在；external DTD/entity/schema/network resolver 必须禁用。
- 预算至少覆盖 AST/XML nodes、config entries、entries、catalog entries、semantic sites 和 recursion depth。

### 8.4 Gap 与稳定 failure codes

`NO_ENTRY_DISCOVERED` 是可计数 Gap reason，仅用于 profile 已确定但入口集合为空；应用 profile 无法确定仍是 fatal。

STAGE02_REQUEST_INVALID、SNAPSHOT_REOPEN_MISMATCH、APPLICATION_PROFILE_UNRESOLVED、APPLICATION_PROFILE_CONFLICT、ENTRY_ROUTE_INVALID、ENTRY_DISCOVERY_INVARIANT_BROKEN、MAPPER_CATALOG_AMBIGUOUS、MAPPER_CATALOG_REFERENCE_BROKEN、CAPABILITY_ACCOUNTING_BROKEN、XML_SECURITY_POLICY_UNENFORCEABLE、XML_EXTERNAL_RESOLUTION_ATTEMPT、STAGE02_RESOURCE_LIMIT_EXCEEDED。

局部动态 route 或 ambiguous mapper 是 Gap；全局 identity/reference/security failure 不得降级。

### 8.5 测试 seam 与验收

- DepotHead class prefix :43 与 method suffix :178-191 缺一时，完整 route 不能 admitted。
- decoy Controller/Mapper、同名 method、overload 和 inventory 外文件不能改变结果。
- application/config/catalog input 顺序和不同 root 不改变 canonical bytes。
- 每种 capability disposition 都进入 denominator；删除 unsupported site 必须触发 accounting failure。
- standard MyBatis DOCTYPE 不联网；external entity mutation fatal。
- Stage03 只能通过 persisted entry/catalog artifacts 建图，测试阻止重新 walk/reparse 的旁路。
- 非空 multi-entry fixture 至少含 DepotHead 与另一 HTTP entry；两者都必须进入 entry inventory，后续各自可成为Flow/GAP/EXCLUDED。改变 file shard size/order 输出bytes相同；缺/重叠 shard或仅DepotHead成功时 Stage02 不得完成。

验收 fixture 必须让 DepotHead 正向投影产生 `POST /depotHead/batchSetStatus`、handler identity 和 Mapper Java/XML candidates，同时至少有第二个非空入口证明 repository inventory 不等于 walkthrough；删除类级 route、制造同名 decoy、遗漏第二入口、缺/重叠 shard或移除全部入口时分别得到指定 Gap/fatal/accounting 结果。只有五个 exact output files 可独立重开、完整 entry ledger闭合且不同 root/input/shard order 的 canonical bytes 相同，Stage 02 才算可交付。

### 8.6 已冻结裁决：实现者不得自由推断

- Maven dependency/config 只启用版本化 parser capability，不直接证明运行时框架行为。
- Spring route 必须由 class/method parts 确定性合并；不能用文件名、注释、模型或约定补 route。
- Stage 02 只产生 Mapper candidates/catalog；唯一 Java→XML binding 属于 Stage 03，不能提前用字符串命中定案。
- 所有发现 site 必须进入 SUPPORTED、UNSUPPORTED、AMBIGUOUS 或 OVER_LIMIT；无入口时使用 `NO_ENTRY_DISCOVERED`，不能删除分母。
- 生产范围固定为 Stage01 COMPLETE_CAPTURE 的完整 site/entry denominator；DepotHead BOUNDED fixture和任意单入口成功都不能令 repository entry coverage closed。
- Stage 03 只能消费 persisted artifacts；缺 catalog 要新 profile/schema/run，不允许隐式 reparse。
- parser library、内部索引和并行策略可自行选择；入口语义、文件名、disposition、accounting、Gap/fatal 边界不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **部分具备（内存态）** | 现有 Stage01 RepositoryCompiler 能在有限 Java/Spring MVC/MyBatis profile 中发现 route、类型、部分 mapper/config site 和 capability report |
| **尚未符合目标** | 应用 profile、entry-points.jsonl、mapper-catalog.jsonl、capability-report.json 尚未作为独立 Stage 02 production assets 立即持久化 |
| **真实样例边界** | 当前代码能看到 DepotHead route 的相关字节，但后续数据流/Fact/Flow closure 未通过；不得把 entry discovery 写成当前 DepotHead complete Flow |

本阶段只固定入口和能力分母。调用、控制、数据、证据关系属于 Stage 03，不能为了复用当前类而继续混在一个不可观察的内存阶段中。
