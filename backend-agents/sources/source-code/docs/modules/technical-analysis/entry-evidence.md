# 按后端入口组织的最终技术证据

状态：2026-09-29 工程及固定源码技术交付已完成；最终R4生成339份入口文件、51条前端请求处置，通过严格重开及三类artifact查询，可移植源码位置已修正。运行时地址、全量逐边准确性和长等待根因仍有限制，见[最终验收](../../supplements/technical-entry-evidence-acceptance-20260929.md)。本页拥有最终JSON合同；[Step05](../../analysis-steps/05-business-flows.md)拥有组装步骤，[运行合同](cli-and-runtime.md)拥有配置、版本与保存接线。历史 `code-reading-material-set-v1/v2` 不改写。

唯一拟新增本体消费者见[总体分支设计](../ontology-recognition/README.md)及[实施计划](../../plans/ontology-recognition-implementation-plan.md)：只接本页新R4与同R0，不接旧分析；目前未实现。已有技术证据和人工推导不是自动Ontology结果。

## 1. 目标与当前差距

目标不是生成业务文章，而是让程序取得某个后端入口的页面请求、Java 调用、Mapper/XML/SQL，以及实际缺口。证据生成没有 LLM。源码中的注释、原始文字和工具诊断仍保留；程序固定字段/原因文字不等于模型写作。

历史 `DefaultCodeReadingMaterialBuilder` 按入口索引顺序尝试合并多个入口，使用 `maxEntriesPerPacket` 和 Markdown UTF-8 字节上限，再追加前端材料；它的生产代码现已退出，历史读取保留。固定源码旧 R3 实际有47包、339条入口处置、51条前端处置；2条前端请求因容量未选。它不是339份入口文件，也不是每个 Vue 页面都有完整链。新 `EntryEvidenceAssembler` 取消该合包规则：**一条后端 entryId 对应一份 JSON；一个入口不能因为相邻入口占用了容量而丢材料。**

`entryId` 对应一个已发现的 HTTP 映射及处理方法，不是一个 Controller 类；一个类通常有多个入口。多个页面可以访问同一入口，一个页面也可以访问多个入口。入口排序只为确定性存储，不表达业务顺序。

## 2. 输出目录与唯一入口

以下是已实现并在固定源码最终R4核验过的目录形状；内容质量和工具限制另见最终验收，不由目录形状证明：

```text
R4 / steps/05-business-flows/
├─ entry-evidence-index.json       全部后端入口、文件位置、状态和准确上游
├─ entry-<entryId的完整十六进制部分>.json
├─ entry-<另一个entryId的完整十六进制部分>.json
├─ frontend-coverage.jsonl        全部前端请求的匹配和纳入结果
└─ 现有步骤发布回执
```

- `entry-evidence-index.json` 是唯一目录入口；不让消费者扫描文件夹猜哪些文件属于本次结果。
- 文件名由程序从合法完整 entryId 确定性生成，不能直接使用 URL、用户输入路径或截断 ID。JSON 内保存原完整 entryId，不修改历史身份。
- 每个已发现入口均有一份文件。Java 未收集时，该文件保存入口、可用前端请求、实际失败原因和明确空缺；不能伪造 Service/SQL 正文。此文件是失败范围的证据，不是成功的完整调用链。
- 每份成功入口 JSON 包含其实际选定范围的全部正文，不依赖上游文件才能阅读；上游引用供核验来源。导出整套目录后，读取不要求原开发机路径仍存在。
- Markdown 仅可作为按需生成的技术视图，不作为新产物的主数据、容量单位或下游输入；不默认再生成近200 MB的调用树与明细重复文档。
- 前端未匹配请求没有合法 entryId，不造一个后端入口。它们保留在 `frontend-coverage.jsonl`，带完整已保存请求、调用路径和相关源码单元，不能消失在入口目录之外。

## 3. 输入与关联依据

组装输入为同一 R0 的独立前端 R1、持久化 R3，以及 R3 保存的准确后端 R2。R2包含 Step02入口和Step03 Java索引；R3包含Step04。

| 输入 | 取用规则 | 明确不做 |
| --- | --- | --- |
| R1 前端请求/源码单元 | 保留 requestId、页面实例、事件、调用实参/形参、URL/方法和未知值 | 不执行 JS，不从页面名称猜入口 |
| R2 入口清单 | 作为完整后端分母；使用 route 与 methodCondition 匹配请求 | 不按 Controller 类合并所有入口 |
| R2 Java索引 | 按 entryId 的成员关系读取方法、调用、绑定与展开结果 | 不把其它入口共享的方法自动变成本入口的调用 |
| R3 持久化索引 | 按已纳入的 Mapper 声明身份取绑定、statement、完整资源及依赖、SQL结果 | 不因表名相同添加其它SQL |
| R0 | 由组合根根据已存位置/hash恢复前端完整单元 | 不读取当前 checkout，不调用 parser 补推关系 |

所有 source basis 必须等于本次明确选定的 R0，包括有效排除范围；R3.navigation 必须指向所消费的 R2。只比较 snapshotId 不足以核对排除版本。来源损坏或错版本拒绝组装，不按普通局部未知处理。

## 4. HTTP 匹配放在组装中

历史 `FrontendHttpDiscoverer.link` 比较已解析路径和HTTP method；新 `EntryEvidenceAssembler`只消费已保存对象做R4匹配，包括显式地址映射和未知条件处置，离线及固定源码最终R4已经验证。固定源码51请求因运行时地址未确认保留为候选，不是已证实部署连通。语法读取留在前端发现器，组装不启动Node；匹配只保存R4，不回写R1。

顺序：请求方法/原URL → 已解析路径与已知地址条件 → 后端 route/methodCondition → 全候选集合。不得按尾部字符串、方法同名、页面词义或第一条结果强连。地址条件仍未知时保留条件；唯一路径候选也不能称为真实部署可达。

复用现有匹配枚举 `MATCHED_UNIQUE / MATCHED_MULTIPLE / NO_MATCH / UNRESOLVED_REQUEST`。`BACKEND_DISCOVERY_NOT_RUN`仅保留在历史v1读取，不成为新R1记录；正式R4缺少可重开的R2发现结果时阻断，不能生成一个假空后端目录。

Spring路由若有params、headers、consumes等条件，只有已有入口记录和请求记录足以核对时才判成立；未采集、未支持或请求值未知则保留候选和未确认条件，不标`MATCHED_UNIQUE`。多候选用`MATCHED_MULTIPLE`；仅一个候选但必需条件未知用`UNRESOLVED_REQUEST`并保留candidateEntryIds。此处不增加通用路由解释器。地址映射只按显式配置操作并记录依据，不能由文件存在、代理惯例或尾部路径相似补出baseURL/context-path。即使配置列表非空，只要当前请求没有适用映射，运行时baseURL仍未知；路径和方法唯一也只能保存为候选，不能误记为`MATCHED_UNIQUE`。

- 唯一匹配：在该入口记录中保存完整请求用法、成立条件和源码。
- 多候选或仅一个候选但条件未知：在每个候选入口的 `candidateRequestUses` 保存该请求及全部候选ID、未确认条件，明确不属于确认调用者；前端覆盖只有一条，不能按候选个数重复计数。
- 无匹配/请求未确定：前端覆盖保留完整材料和原因，不分配给任意入口。
- 前端明确关闭：R1仍保存 DISABLED 索引；每个入口标记前端未分析，而非“没有页面使用”。不要求 Node 或 Maven。
- 后端入口没有已发现的前端调用者：记录“在本次前端范围内未匹配到”，不是入口无用，也不是证明全仓不存在调用。

## 5. 每份入口 JSON 的字段合同

字段名是本轮目标，嵌套原记录沿用其准确版本；不能复制下表作为缺失数据的默认值。除明确 nullable 字段，键均必需；数组可以为空但必须与状态及原因一致。

| 字段 | 内容及不变量 |
| --- | --- |
| `schemaVersion` | `entry-evidence-v1` |
| `entryId`、`entry` | 完整身份及原 HTTP entry 记录；handler、路由条件、methodKey、参数、位置保持 |
| `sourceBasis`、`upstream` | R0与前端、后端、持久化准确逻辑引用；不内嵌宿主绝对路径 |
| `assemblyStatus` | `ASSEMBLED / ASSEMBLED_WITH_LIMITATIONS / NOT_ASSEMBLED`；不代表业务解释完成 |
| `coverage` | 分开保存后端收集状态、前端匹配状态、持久化启用/分析状态；不压成一个“有缺口”标签 |
| `frontend.requestUses` | 唯一匹配请求及页面实例；原实参、形参、NOT_PASSED、URL和地址条件不改 |
| `frontend.candidateRequestUses` | 多匹配的候选关系，附全部候选；不能当确定调用路径 |
| `frontend.units` | 上述用法所需完整Vue/JS单元及已保存位置，按unitId去重；不是只留事件附近几行 |
| `java.methods` | 本入口已收集方法的完整正文、参数、返回、控制结构、位置；按methodKey去重 |
| `java.calls` | 本入口每个调用位置、有序实参、目标声明、合法实现候选、展开状态；外部/未知/失败明确区分 |
| `java.observations` | 与确定调用分开保存的冲突候选/工具原因；不能沿被否定候选带入无关正文或SQL |
| `java.supportingSources` | 保留本入口 `EntryCodeContext` 已收集的字段、配置或声明等支持源码及其关联方法；旧分包器未投影此字段，新入口证据不得再次遗漏 |
| `java.technicalEnhancements` | 原样保存上游对可选技术增强的实际可用/未生成状态和引用；不因组装而虚构 Fact/Proof |
| `persistence.bindings` | Mapper声明到statement的静态绑定性质及有序参数信息；非运行时执行证明 |
| `persistence.statements` | 完整 XML 子树、动态条件、循环、include/resultMap等依赖引用 |
| `persistence.resources` | 从已纳入 statement 出发得到的完整 XML 原文及静态依赖资源闭包；每资源一份，不截列/值或分支 |
| `persistence.sqlAnalyses` | 原 analysisCopy、变换、解析AST、状态、排序及限制；不命名为实际执行SQL |
| `sourceRefs` | 单元到同源相对路径、真实范围和已有摘要的映射；不重复存一遍全部正文 |
| `limitations` | 局部未确定/未收集范围、原错误码及相应对象ID；不添加模型解释 |

完整工具观察保存在`java.calls[].observations`，附着于对应的物理调用；`java.observations[]`仅按`callKey`和稳定序号建立扁平定位清单，保存观察ID、代码和简要原因，不重复完整位置与身份。未确认观察不是`java.calls[].targets`中的确定仓库边，不能被持久化组装器用于关联Mapper或SQL。

Java目标身份、候选选择、Lombok来源区分的唯一规则在[JDT详细设计](../java-code-engines/jdt-engine.md)。同一入口的全部分支并列保存，不把调用数组或文件排序解释为某次请求执行时序。

### 5.1 完整与去重

“完整入口 JSON”表示该入口**已经收集并在覆盖中声明的范围**没有在组装时再次丢失，不表示静态分析找到了所有运行时行为。

1. 每个方法、支持源码、XML资源和前端单元在单份文件中只存一次，关系使用已有ID或源码路径与范围引用，不为缺失身份造ID。
2. 不另外保存展开调用树；环和递归通过调用边表示，不无限复制正文。
3. 同一方法被多个入口用到，会出现在各入口独立文件中，这是自包含输出的明确取舍；底层R2仍只有共享METHOD记录，不重采集。
4. 现有上游已登记的导航深度/数量/未解析限制全部继承，不称为组装补齐。
5. 不按12入口、5 MiB、Markdown长度或模型token数重分组。模型如何读大入口属于后续Activity设计，本轮不启动。
6. 持久化局部诊断按已纳入的 Mapper 方法、statement 和完整资源依赖闭包筛选；仅检查直接 statement 的 XML 路径会漏掉被 `include` 等关系带入的依赖资源问题。

## 6. 路径和编号

- 结构化 `sourcePath` 一律为相对 R0 根的仓库路径，使用 `/`；不能含宿主盘符、绝对路径、`..`越界或符号链接外目标。
- 目录索引中的 `file` 相对本次证据目录根，不相对调用进程CWD。比如 `entry-50b707...json`（正式文件用完整十六进制ID，示例省略不可照抄）。
- artifact/run ID保留既有逻辑身份，不把本机 `.workspace/...` 路径当关系键。
- 原始 Java/XML/JS 正文逐字保留，**不对源码字符串中的绝对路径做替换**；结构化路径可移植与改写客户源码是两回事。
- JDK/JAR工具路径只留在既有私有执行记录；最终证据记录外部类型/方法身份及环境逻辑引用，不将依赖缓存路径当客户源码。
- 原长ID继续作为持久唯一身份。短编号只在以后明确的模型投影中建立局部可逆映射；本轮不重命名历史ID，也不因为JSON里有长ID便调用LLM。
- 新发布的入口、目录及前端覆盖头使用同一完整`SelectedSourceBasis`：准备版身份、准确R0发布引用、`snapshotId`和`effectiveScopeDigest`均保存；仅有`VerifiedSourceInventoryReference`不足以说明排除范围。读取器核对这些重复字段及R0引用。此前本地调试R4缺少该新字段时按其明确旧形状读取，不反推为新格式身份。

## 7. 目录与覆盖

`entry-evidence-index-v1` 保存：schemaVersion、完整sourceBasis、R1/R2/R3逻辑引用、assembler版本、实际配置、entries和计数。每个 `entries[]` 含 entryId、methodCondition、route、handler、file、assemblyStatus、局部限制数量。文件字节数/hash复用canonical receipt descriptor，不再设计第二层校验账本。

`frontend-evidence-coverage-v1` JSONL：一条HEADER，随后按 requestId 排序的 REQUEST_COVERAGE；每条保存requestId、resolution、全部entryIds、实际纳入/未纳入情况及原因。无唯一入口的请求内嵌完整请求和所需sourceUnits；已唯一纳入者只保留entryId及准确的`entryFile`定位，不在总表复制请求或源码。读取器分别校验两种明确形状。

必须成立：

```text
R2 已发现 entryId 集合 = index.entries 的 entryId 集合 = 实际入口 JSON 的 entryId 集合
R1 请求 ID 集合 = frontend coverage 请求 ID 集合
入口内每个调用 owner = 当前 entryId
已输出所有结构化引用均在本文件或目录明确可定位
```

当 R1 DISABLED 时请求集合为0，但HEADER写关闭；未分析的前端文件范围和R1限制照常保留。HTTP请求数量不是Vue页面总数，更不是整个仓库实际请求的证明。

## 8. 容量、异常与保存

沿用现有 canonical module/step store、不可变receipt和原子安装，不增加证据数据库或新的恢复框架。Step05 module4新producer安装本索引、coverage及entry文件集合。

默认store仍对既有producer按精确固定文件名校验、单publication最多64个payload。Step05 module4的`entry-evidence`/v3是唯一例外：它已经使用受限变长集合合同，固定索引和覆盖文件＋由索引完整entryId集合推导的 `entry-[0-9a-f]{64}.json`，并在module和step重开时核对实际文件数和字节。它拒绝额外文件、重复entryId、文件名与正文身份不一致、路径越界和错artifact type；空后端分母合法地只保存索引（`entries=[]`）和覆盖。其它producer白名单与默认限制保持。R4 producer已经接通并在固定源码上实际生成入口文件；存储成功仍不等于内容准确性验收通过。

`evidence.maxEntryUtf8Bytes`、`evidence.maxPublicationUtf8Bytes`、`evidence.maxEntries`是显式正整数资源上限；按**实际canonical JSON**计量，不使用Markdown计数。组装前核对入口数量及store的文件数/字节预算能否满足；文件数预算由maxEntries＋2语义文件＋既有receipt开销推导并按现有store限制核验，不悄悄放宽其它步骤。共享后端方法跨入口复制所需容量应在实现验收实测，设计不虚报固定大小。

- 上游局部未知/导航失败：保留实际入口结果文件，运行可完成但报告限制；不能声称全仓完整。
- 单入口实际JSON超限：不删正文、不改成看似完整的小包；本次组装返回具名容量问题，保留已安装上游，不发布成功证据集合。用户调整配置后新建R4，零解析器重跑。FAILED/BLOCKED R4 可通过inspect查询经严格R0/R1/R2/R3核验的具名问题，但不列任何`availableOutput`；没有receipt，artifact明确拒绝，不能返回正式入口文件引用或把上游publication伪装为R4输出。
- 整体超限、来源损坏、引用非法、写盘失败：同样不安装成功集合；保留实际报告和既有上游。
- 原子安装中断：沿用现有receipt为准的读取，临时文件不能冒充成功结果；不自动续写结束运行。

组装状态描述内容，运行状态描述操作，存储状态描述是否实际安装，三者不能互相代替。入口本身NOT_COLLECTED可形成有报告的证据集合；因存储超限无法安装集合则是本次R4失败。

## 9. 重开、导出与后续消费

新reader先核对R4 receipt/schema/文件集合，再按entryId打开指定文件及目录关系。对已安装的自包含文件重开，不要求Maven输出、JAR、JDK或客户checkout存在，也不重跑组装器。正式执行R4时的上游准入仍必须核验R0/R1/R2/R3；自包含阅读不是允许混用来源。

安装时使用的入口数量、单文件及总字节上限必须以具体值绑定并保存在 R4 request-v5 中。历史重开从该请求恢复限额，而不是读取当前配置；仅保存不可逆的预算摘要不足以在打开动态入口文件集合前恢复读取限额。缺少、损坏或与请求身份不符时拒绝读取，不以全局放宽限额代替。

`artifact`增加版本明确的 ENTRY_EVIDENCE_INDEX、ENTRY_EVIDENCE（要求完整entryId）、FRONTEND_EVIDENCE_COVERAGE查询键，保留既有public Agent方法。按入口ID查文件，不增加任意磁盘路径读取。目录导出是已保存payload逐字复制及既有回执，不新跑分析；未来消费者从index定位JSON，不必解析技术Markdown或手工关联五份上游索引。

旧Packet reader、旧技术Markdown、M10、326/418 Activity及业务结果保持独立历史读取。旧Activity/Process路由未适配R4时在Provider初始化前拒绝，不把入口JSON包装成旧Packet。新本体是直接证据consumer，不做这些兼容，不受旧业务路由门禁误拦；自身严格准入由本体合同拥有。

## 10. 直接验收

- 两个不相关Controller入口不能合成一包；相同方法可以被两份入口JSON引用/内嵌但不串用entry-owned calls。
- 一页多请求、多页共用入口、多候选、无匹配、关闭前端、空后端、失败后端均保持分母。
- getQueryParams等已保存必要单元、完整Java、XML条件/循环/列值、ORDER BY通过发布重开保留。
- 全字段和正文由已有上游投影，零LLM；找不到的数据不由Agent补进正式文件。
- 搬移导出目录后仍可按相对路径打开；非法路径/错owner/断引用/容量及写盘失败明确拒绝。
- 至少超过64个入口的fixture验证真实store变长集合，不只测试内存DTO；只跑本次直接测试。
- 已确认错边不再进入确定调用及其SQL；正常已确认外部调用不被误报项目缺失；真实未知、Lombok限制、超时仍可见。
- 对采购“关联请购单”检查自动数据链、实际四参数、第五个purchaseStatus保持NOT_PASSED，以及新R1已保存的JeecgListMixin.js第130–144行getQueryParams完整单元进入R4；不能把它验收为采购订单创建或入库付款流程。

本页规定机器材料，不增加LLM写作、SQL执行、跨版本增量复用、通用解析器或人工作证系统。
