# 02 发现应用类型和入口

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

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

目标 **DETERMINISTIC_CONCLUSION** 是发现一个 HTTP entry：

~~~json
{
  "entryId": "entry:<hex64>",
  "kind": "SPRING_MVC_HTTP",
  "httpMethod": "POST",
  "route": "/depotHead/batchSetStatus",
  "handler": "com.jsh.erp.controller.DepotHeadController#batchSetStatus"
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

DepotHead target application profile：

~~~json
{
  "language": "JAVA",
  "javaRelease": 8,
  "frameworks": ["SPRING_MVC", "MYBATIS"],
  "entryCount": 1,
  "scope": "BOUNDED_PATH_SET"
}
~~~

entryCount=1 仅指本文 walkthrough 的目标投影，不是全八文件中所有可能入口的实测 golden。

目标 DepotHead 正向出口必须至少包含上面的完整 HTTP entry、一个 application profile、相关 Mapper catalog candidates、全部发现 site 的 disposition 和非空 receipt；它只是全仓入口 inventory 中一项。若完整仓库没有可发现入口，仍要安装完整五文件 artifact set，并在 `capability-report.json` 中记录 `NO_ENTRY_DISCOVERED`、全 inventory 搜索证据与零分母依据，状态只能是 SUCCEEDED_WITH_GAPS；不得省略文件或假造入口。

Stage 02 completion 还要求 `repositoryEntryCoverage` 闭合：每个 discovery site 和每个形成的 `entryId` 都有唯一 disposition，所有 shard receipts 的 denominator union 与未分片 ID 集完全相同。一条 DepotHead entry 成功、其他 entry 未处置时，Stage 02 和整个 run 都不能完成。

### 4.1 人类 walkthrough：模块用什么文件接力

~~~jsonl
{"module":"ApplicationProfileDetector","artifact":"modules/01-application-profile/application-profile-draft.json","takesFrom":["Stage01Reference"],"says":{"snapshotId":"snapshot:depothead-demo-v1","language":"JAVA","javaRelease":8,"frameworkSignals":["SPRING_MVC","MYBATIS"]}}
{"module":"SpringHttpEntryDiscoverer","artifact":"modules/02-http-entry/http-entry-discovery.json","takesFrom":["application-profile-draft.json","verified Java bytes"],"says":{"entryId":"entry:post-depothead-batch-set-status","method":"POST","route":"/depotHead/batchSetStatus","handler":"com.jsh.erp.controller.DepotHeadController#batchSetStatus"}}
{"module":"MapperCapabilityCataloger","artifact":"modules/03-mapper-catalog/mapper-catalog-draft.json","takesFrom":["application-profile-draft.json","verified config/Mapper bytes"],"says":{"catalogEntryId":"mapper-catalog:depothead-update-by-example","javaMethod":"DepotHeadMapper#updateByExampleSelective","xmlStatement":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","bindingState":"CANDIDATE_NOT_YET_BOUND"}}
{"module":"Stage02ArtifactPublisher","artifact":"modules/04-publish/stage02-publication.json","takesFrom":["application-profile-draft.json","http-entry-discovery.json","mapper-catalog-draft.json"],"says":{"entryCount":1,"catalogEntryCount":1,"publicFiles":["application-profile.json","entry-points.jsonl","mapper-catalog.jsonl","capability-report.json","stage-receipt.json"],"nextStage":"03-build-five-program-graphs"}}
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

## 6. 成功、Gap、fatal 与恢复

- **成功**：完整仓库的应用 profile 可确定，全部 discovery site/entry 有唯一处置，shard union、entry/catalog/site accounting 闭合，阶段目录安装。
- **带 Gap 成功**：某 entry/site 为 UNSUPPORTED、AMBIGUOUS 或 OVER_LIMIT，但保留 locator、affected entries 和 reason。
- **fatal**：snapshot reopen 漂移、application profile 与声明 dependency 自相矛盾、catalog duplicate identity、XML parser 安全策略不可执行、coverage 方程不闭合或 artifact install 失败。
- **恢复**：只有 upstream artifact root、shard receipts 和 input/tool/profile/schema/prompt hashes 完全相等才复用；已完成 deterministic shard 保留，未开始 shard 可重跑，缺/重叠 shard 不能宣布完成；prompt hash 本阶段必须为 null。

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

模块执行顺序固定为 `ApplicationProfileDetector` → `SpringHttpEntryDiscoverer` 与 `MapperCapabilityCataloger`（二者只共享 M1 输出，可并行）→ `Stage02ArtifactPublisher`。发现模块不能互相补读文件；publisher 只验证和落盘。

#### M1 ApplicationProfileDetector

- **解决的问题**：在不运行构建的前提下确定应启用哪些静态 parser/profile，并把“依赖信号”与“已证明行为”分开。
- **精确上游输入及前置**：valid Stage01Reference、`verified-snapshot.json`、complete inventory/source handles、版本化 discovery profile/toolchain/budget；生产路径还要求 Stage01 `repositoryCompletionEligible=true`，bounded fixture只能诊断。
- **确定性顺序 / LLM**：按 profile 指定 path 解析 Maven/config → 提取 Java release、dependency/config signals → 交叉检查冲突 → 选择唯一 capability profile；0 LLM。
- **目标输出与 DepotHead 示例**：`ApplicationProfile{language=JAVA,languageVersion=8,frameworkSignals=[SPRING_MVC,MYBATIS],inventoryScope,repositoryCompletionEligible}` 加 signal dispositions；DepotHead例为 BOUNDED/false，只启用 parser，不声称运行时已部署；生产 complete profile继承true。
- **必须保持的不变量**：每个 signal 有 locator/disposition；dependency 只启用 capability；profile identity 绑定 snapshot/profile/toolchain，不能绑定显示名称或机器 root。
- **Gap / fatal / 恢复**：可隔离的未知 version/config site 是 Gap；profile 无法唯一确定、signal 冲突、source drift 或安全 parser 不可执行是 fatal；恢复重放相同 bytes/profile。
- **给下游的后置保证**：M2/M3 只使用该 profile 明示的 parser/rules，并知道 inventory 内哪些 config/Java/XML 资源可读。
- **明确非目标**：不发现 HTTP entry，不唯一绑定 Mapper，不运行 Maven、classloader、Spring 或 MyBatis。
- **公共测试 seam 与验收**：`detect(Stage01Reference, DiscoveryProfile)` 对 Java 8/Spring/MyBatis fixture 产生唯一 profile；version conflict、inventory 外 config、dependency decoy 和不同 root replay 有确定结果。
- **Luna/xhigh 测试指南**：创建 `Stage02ApplicationProfileDetectorTest`，冻结 Stage01 artifacts、pom/config和手写golden于 `src/test/resources/target/stage02/application-profile/`。一个行为一个RED：Java8/Spring/MyBatis正向、dependency仅signal、version conflict、inventory外config、budget Gap、root/order determinism；首RED应因public detector/schema缺失失败。只可fake只读source handle，禁止mock parser/canonical/identity。命令：`mvn -Dtest=Stage02ApplicationProfileDetectorTest test`；无网络/build/Provider。偏离按DESIGN 13.11交Sol/ultra。
- **Terra/xhigh 实现指南**：观察RED后仅改 `target/stage02/applicationprofile/`，实现 public `ApplicationProfileDetector/ApplicationProfile` 与 `stage02-application-profile-draft-v1`；只读Stage01 artifact/handles，按Maven/config→signals→conflict→profile，禁止运行Maven或把dependency当行为。逐RED GREEN，最终canonical/root replay稳定并更新审计；需新signal语义/跨阶段字段时MUST STOP。

#### M2 SpringHttpEntryDiscoverer

- **解决的问题**：固定 HTTP 入口分母、完整 route、handler 和参数身份，让图构建有明确根。
- **精确上游输入及前置**：M1 `ApplicationProfile`、Stage 01 complete verified Java handles、Spring route registry、stable file shards 与 entry budget；profile 必须声明 SPRING_MVC capability。
- **确定性顺序 / LLM**：先枚举全 inventory annotation-site denominator → 按 fileId shard解析 package/type/method/annotations → 静态求值 route parts → 确定性笛卡尔合并 method/path → 建 EntryPoint/dispositions → shard union/accounting；0 LLM。
- **目标输出与 DepotHead 示例**：`EntryPoint{kind=SPRING_MVC_HTTP,method=POST,route=/depotHead/batchSetStatus,handlerTypeId,handlerMethodId,parameterNodeIds,routeEvidenceNodeIds}`；route evidence 同时含 :43 和 :178-191。
- **必须保持的不变量**：每个 complete-capture candidate site 恰一 disposition；shard denominators不交叠且union精确等于总site IDs；完整 route 保留 parts；同 protocol/method/route/handler identity 不重复；动态表达式不猜值。
- **Gap / fatal / 恢复**：单 site dynamic/ambiguous/over-limit 是带 affected entry 的 Gap；route identity 冲突、accounting/reference/source drift 是 fatal；恢复只重放相同 profile/root。
- **给下游的后置保证**：M4/Stage 03 获得稳定 entryId、route parts、handler/param nodes 和完整入口 denominator，无需 reparse annotation。
- **明确非目标**：不沿调用链、不命名业务、不把 Controller 方法存在等同于完整 Flow。
- **公共测试 seam 与验收**：`discoverEntries(ApplicationProfile, SourceHandleSet)` 覆盖 DepotHead prefix+suffix、第二个非空 HTTP entry、动态 route、overload/decoy、annotation deletion、无入口 Gap、缺/重叠 shard；两入口在不同 shard/顺序下产生同 bytes，只有 prefix/suffix/handler refs 都在才 admitted。
- **Luna/xhigh 测试指南**：创建 `Stage02SpringHttpEntryDiscovererTest`，fixtures/goldens放 `src/test/resources/target/stage02/http-entry/`，冻结真实DepotHead route spans与decoys。RED依次为prefix+suffix正向golden、缺prefix、dynamic route Gap、overload/duplicate fatal、无入口Gap、order determinism；初始因seam/artifact未实现失败。只可fakeSourceHandle，不能mock annotation parser/route merge。命令：`mvn -Dtest=Stage02SpringHttpEntryDiscovererTest test`；禁网络/客户Maven/private耦合。偏离按DESIGN 13.11交`gpt-5.6-sol / ultra` Design Authority。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage02/httpentry/`，实现 public `SpringHttpEntryDiscoverer/EntryDiscovery` 与 `stage02-http-entry-discovery-v1`；输入只取M1 artifact+Stage01 handles，按parse→enumerate→static evaluate→route product→disposition。逐slice GREEN且golden route evidence同时含:43/:178-191；不得用文件名/模型补route。schema或上游不足即STOP交Sol/ultra，完成后更新审计。

#### M3 MapperCapabilityCataloger

- **解决的问题**：登记后续建图所需的 Mapper Java/XML/config candidates 和所有受支持/不支持 site，而不抢先做调用绑定。
- **精确上游输入及前置**：M1 profile、complete verified Mapper Java/XML/config handles、resource shards、MyBatis parser/security policy 与 budget；所有资源必须在 Stage 01 inventory且每个匹配/未匹配 resource都有处置。
- **确定性顺序 / LLM**：解析 config mapper resources → Java interface/method candidates → XML namespace/statements/includes → 建 catalog entries → 对全部 sites 分类/account；0 LLM。
- **目标输出与 DepotHead 示例**：`MapperCatalogEntry{javaInterfaceTypeId,javaMethodCandidateIds,xmlResourcePath,xmlNamespaceNodeId,xmlStatementCandidates}`；例子把 Mapper Java :23 与 XML :3/:385 作为 candidates，不在此声称唯一 binding。
- **必须保持的不变量**：catalog candidate 有 source identity；全 inventory相关resources/sites进入四种 disposition；resource shard union闭合；XML resolver 零外部访问；candidate 相同名称不自动合并。
- **Gap / fatal / 恢复**：局部 dynamic resource/ambiguous candidate 为 Gap；duplicate identity、broken resource ref、XML 安全失败、accounting 不闭合为 fatal；恢复重放 persisted source/profile。
- **给下游的后置保证**：Stage 03 可按 typed candidates 和 locators 做 receiver/method/namespace/statement 唯一 binding，不必重扫 config/XML。
- **明确非目标**：不产生 CALL edge、SQL data-flow、Fact 或 Flow；不因字符串相同宣布 Mapper binding。
- **公共测试 seam 与验收**：`catalogMappers(ApplicationProfile, SourceHandleSet)` 覆盖 DepotHead Java/XML、同名 decoy、namespace mismatch、standard DOCTYPE 零网络和 external entity fatal。
- **Luna/xhigh 测试指南**：创建 `Stage02MapperCapabilityCatalogerTest`，fixtures/goldens在 `src/test/resources/target/stage02/mapper-catalog/`。RED顺序：DepotHead candidate catalog、同名decoy不合并、namespace mismatch Gap、standard DOCTYPE零resolver、external entity fatal、site accounting/determinism；首RED应因cataloger/schema缺失。只可fake resolver/source boundary并断言零外呼，禁止mock XML/catalog core。命令：`mvn -Dtest=Stage02MapperCapabilityCatalogerTest test`；禁网络/客户runtime。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage02/mappercatalog/`，实现 public `MapperCapabilityCataloger/MapperCatalogDraft` 与 `stage02-mapper-catalog-draft-v1`；只用M1+Stage01，config→Java→XML→site disposition，external resolution fail closed。GREEN逐个覆盖candidate/Gap/security/accounting；不得提前绑定Java→XML或扩inventory。缺必要config/跨stage调整MUST STOP，审计同步。

#### M4 Stage02ArtifactPublisher

- **解决的问题**：合并 M1–M3 的独立发现结果，验证分母和引用后形成不可变 Stage 02 出口。
- **精确上游输入及前置**：一个 ApplicationProfile、M2 entries/sites/shard receipts/dispositions、M3 catalog/sites/shard receipts/dispositions、Stage01 complete root 和 Stage02 controls；各模块结果均已局部验证。
- **确定性顺序 / LLM**：合并全仓 site/catalog → 验证 shard disjoint-union → 重算 source/site/entry/catalog ID-set accounting → 校验所有 refs → canonical 排序/编码五文件 → staging 自验/atomic install；0 LLM。
- **目标输出与 DepotHead 示例**：五个 exact files；正向例 `entry-points.jsonl` 含一个 POST DepotHead entry，catalog 含 Mapper candidates；无入口例仍有 profile/capability/receipt 和 `NO_ENTRY_DISCOVERED`。
- **必须保持的不变量**：complete discovery denominator = dispositions；每个 entry恰一状态，任何 omitted/failed均有Gap/排除证据；一entry成功不关闭其余entry；artifact names/roots/controls 完整；publisher 不改变 M1–M3 semantic decision。
- **Gap / fatal / 恢复**：局部 Gap 进入 capability report；cross-module ref/accounting/canonical/install/collision 错误 fatal；恢复只认完整 stage receipt，不复用 staging。
- **给下游的后置保证**：Stage 03 得到 immutable Stage02Reference，所有图根与 Mapper candidates 都能仅从 persisted artifacts 解析。
- **明确非目标**：不补 entry/catalog、不重新解析源码、不构图或调用模型。
- **公共测试 seam 与验收**：`publish(profile, entries, catalog, capabilityReport)` 注入 artifact store；测试至少两entry、跨resource shard、缺/重叠 shard、单entry成功但另一entry omitted、broken ref、乱序、crash/collision，以及 DepotHead exact five-file projection。
- **Luna/xhigh 测试指南**：创建 `Stage02ArtifactPublisherTest`，模块artifacts/goldens放 `src/test/resources/target/stage02/artifact-publisher/`。RED依次exact five-file DepotHead set、NO_ENTRY_DISCOVERED set、missing disposition、broken ref/count spoof、乱序canonical、crash/collision/resume；首RED应因publisher缺失。只mock artifact-store故障，不能mockjoin/accounting/canonical。命令：`mvn -Dtest=Stage02ArtifactPublisherTest test`；无网络/Provider/客户Maven。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage02/artifactpublisher/`，实现 public `Stage02ArtifactPublisher/Stage02Reference` 与 `stage02-publication-v1`；只读M1–M3 module files，join→ID-set accounting→canonical five files→atomic install。每个RED单独GREEN，最终Stage03只凭reference可读；禁止reparse/修semantic decision。需要跨stage改变即MUST STOP交Sol/ultra并按规则升级用户。

### 8.0.1 模块 artifact wire schemas

全部文件使用 DESIGN 13.3 envelope；`!`=required non-null，`?`=required nullable。stage public schemas只能由M4从这些文件投影。

| module artifact | schemaVersion / artifactType | 精确 upstream | payload、来源、排序 |
| --- | --- | --- | --- |
| `modules/01-application-profile/application-profile-draft.json` | `stage02-application-profile-draft-v1` / `STAGE02_APPLICATION_PROFILE_DRAFT` | Stage01 publication/snapshot IDs+SHAs | `applicationProfileId!`、`snapshotId!`、`inventoryScopeKind!`、`repositoryCompletionEligible!`、`language!`、`languageVersion?`、`frameworkSignals[]!{kind!,locator!,disposition!}`、`configSignals[]!{kind!,locator!,value?,disposition!}`、`capabilityProfileRef!`；signals按kind+locator排序，unknown version用null+Gap |
| `modules/02-http-entry/http-entry-discovery.json` | `stage02-http-entry-discovery-v1` / `STAGE02_HTTP_ENTRY_DISCOVERY` | M1与Stage01 source IDs+SHAs | `applicationProfileId!`、`entries[]!{entryId!,kind!,protocol!,method!,route!,routeParts[]!,handlerFqn!,parameterNames[]!,routeLocators[]!}`、`sites[]!{siteId!,locator!,disposition!,entryId?,reasonCode?,evidenceRefs[]!}`、`shardReceipts[]!{shardId!,denominatorSiteIds[]!,dispositionSiteIds[]!,status!,gapIds[]!}`、`denominator!`；entries按entryId，sites/shards按ID，参数/routeParts保持源码语义顺序 |
| `modules/03-mapper-catalog/mapper-catalog-draft.json` | `stage02-mapper-catalog-draft-v1` / `STAGE02_MAPPER_CATALOG_DRAFT` | M1与Stage01 source IDs+SHAs | `applicationProfileId!`、`catalogEntries[]!{catalogEntryId!,javaInterfaceFqn!,javaMethodCandidates[]!,xmlResourcePath!,xmlNamespace!,xmlStatementCandidates[]!,bindingState!}`、`sites[]!`、`shardReceipts[]!`、`denominator!`；catalog/sites/shards按ID，candidate arrays按canonical signature/statement key |
| `modules/04-publish/stage02-publication.json` | `stage02-publication-v1` / `STAGE02_PUBLICATION` | M1/M2/M3 IDs+SHAs | `stageStatus!`、`applicationProfileId!`、`entryCount!`、`catalogEntryCount!`、`repositoryEntryCoverage!{sourceFileIds[]!,discoverySiteIds[]!,entryIds[]!,supportedEntryIds[]!,gappedEntryIds[]!,excludedEntryIds[]!,shardReceiptIds[]!,closed!}`、`stageArtifactRoot!`、`publishedArtifacts[]!`、`nextStage!`；ID arrays按canonical ID，publishedArtifacts按path且恰五项 |

resolved值只能来自静态parser/rules。动态route/config使用nullable field+site Gap；不能填故事值。`bindingState` v1只允许 `CANDIDATE_NOT_YET_BOUND`，唯一binding由Stage03产生。fatal写ModuleFailure；success failureRef=null。任何field/enum/source/sort/identity语义变化先改设计并升version，旧reader exact-version fail closed。

`repositoryEntryCoverage.closed` 当且仅当 Stage01 `COMPLETE_CAPTURE/repositoryCompletionEligible=true`、全部 discovery shards闭合且每site/entry有唯一处置；bounded walkthrough即使其局部ID集守恒也必须为false。

~~~jsonl
{"schemaVersion":"stage02-application-profile-draft-v1","artifactType":"STAGE02_APPLICATION_PROFILE_DRAFT","artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":2,"module":"ApplicationProfileDetector","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application:jsh-erp-java8-spring-mybatis","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","inventoryScopeKind":"BOUNDED_PATH_SET","repositoryCompletionEligible":false,"language":"JAVA","languageVersion":8,"frameworkSignals":[{"kind":"MYBATIS","locator":"jshERP-boot/pom.xml:1","disposition":"SUPPORTED"},{"kind":"SPRING_MVC","locator":"jshERP-boot/pom.xml:1","disposition":"SUPPORTED"}],"configSignals":[{"kind":"MYBATIS_MAPPER_LOCATION","locator":"jshERP-boot/src/main/resources/application.yml:1","value":"classpath:mapper_xml/*.xml","disposition":"SUPPORTED"}],"capabilityProfileRef":"profile:java8-spring-mvc-mybatis-v1"}}
{"schemaVersion":"stage02-http-entry-discovery-v1","artifactType":"STAGE02_HTTP_ENTRY_DISCOVERY","artifactId":"entry-discovery:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":2,"module":"SpringHttpEntryDiscoverer","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entries":[{"entryId":"entry:post-depothead-batch-set-status","kind":"SPRING_MVC_HTTP","protocol":"HTTP","method":"POST","route":"/depotHead/batchSetStatus","routeParts":["/depotHead","/batchSetStatus"],"handlerFqn":"com.jsh.erp.controller.DepotHeadController#batchSetStatus","parameterNames":["status","ids"],"routeLocators":["jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43","jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191"]}],"sites":[{"siteId":"site:depothead-batch-set-status","locator":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191","disposition":"SUPPORTED","entryId":"entry:post-depothead-batch-set-status","reasonCode":null,"evidenceRefs":["jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191"]}],"shardReceipts":[{"shardId":"entry-shard:depothead","denominatorSiteIds":["site:depothead-batch-set-status"],"dispositionSiteIds":["site:depothead-batch-set-status"],"status":"SUCCEEDED","gapIds":[]}],"denominator":{"candidateSites":1,"supported":1,"unsupported":0,"ambiguous":0,"overLimit":0}}}
{"schemaVersion":"stage02-mapper-catalog-draft-v1","artifactType":"STAGE02_MAPPER_CATALOG_DRAFT","artifactId":"mapper-catalog:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":2,"module":"MapperCapabilityCataloger","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"applicationProfileId":"application:jsh-erp-java8-spring-mybatis","catalogEntries":[{"catalogEntryId":"mapper-catalog:depothead-update-by-example","javaInterfaceFqn":"com.jsh.erp.datasource.mappers.DepotHeadMapper","javaMethodCandidates":["updateByExampleSelective(DepotHead,DepotHeadExample)"],"xmlResourcePath":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml","xmlNamespace":"com.jsh.erp.datasource.mappers.DepotHeadMapper","xmlStatementCandidates":["updateByExampleSelective"],"bindingState":"CANDIDATE_NOT_YET_BOUND"}],"sites":[{"siteId":"site:depothead-mapper-statement","locator":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:385","disposition":"SUPPORTED","entryId":"entry:post-depothead-batch-set-status","reasonCode":null}],"shardReceipts":[{"shardId":"mapper-shard:depothead","denominatorSiteIds":["site:depothead-mapper-statement"],"dispositionSiteIds":["site:depothead-mapper-statement"],"status":"SUCCEEDED","gapIds":[]}],"denominator":{"candidateSites":1,"supported":1,"unsupported":0,"ambiguous":0,"overLimit":0}}}
{"schemaVersion":"stage02-publication-v1","artifactType":"STAGE02_PUBLICATION","artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","producer":{"stage":2,"module":"Stage02ArtifactPublisher","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"entry-discovery:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"mapper-catalog:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","profileSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","schemaBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryCount":1,"catalogEntryCount":1,"repositoryEntryCoverage":{"sourceFileIds":["file:depothead-controller","file:depothead-mapper-xml"],"discoverySiteIds":["site:depothead-batch-set-status","site:depothead-mapper-statement"],"entryIds":["entry:post-depothead-batch-set-status"],"supportedEntryIds":["entry:post-depothead-batch-set-status"],"gappedEntryIds":[],"excludedEntryIds":[],"shardReceiptIds":["entry-shard:depothead","mapper-shard:depothead"],"closed":false},"stageArtifactRoot":"stage-root:0202020202020202020202020202020202020202020202020202020202020202","publishedArtifacts":[{"path":"application-profile.json","sizeBytes":1000,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"path":"capability-report.json","sizeBytes":1200,"sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},{"path":"entry-points.jsonl","sizeBytes":1400,"sha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},{"path":"mapper-catalog.jsonl","sizeBytes":1600,"sha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},{"path":"stage-receipt.json","sizeBytes":1800,"sha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"}],"nextStage":"03-build-five-program-graphs"}}
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
  language
  languageVersion
  frameworkSignals[]
  configSignals[]
  capabilityProfileRef
  inventoryScope
  repositoryCompletionEligible

EntryPoint
  entryId
  kind
  protocol
  method
  route
  handlerTypeId
  handlerMethodId
  parameterNodeIds[]
  returnNodeId
  routeEvidenceNodeIds[]

MapperCatalogEntry
  catalogEntryId
  javaInterfaceTypeId
  javaMethodCandidateIds[]
  xmlResourcePath
  xmlNamespaceNodeId
  xmlStatementCandidates[]

CapabilitySite
  siteId
  kind
  locator
  affectedEntryIds[]
  disposition
  reasonCode
  evidenceRefs[]

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

entryId 依赖 snapshotId、entry kind、protocol、route parts、handler type/method node identities 和 discovery rule version。它不依赖显示名称。

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
