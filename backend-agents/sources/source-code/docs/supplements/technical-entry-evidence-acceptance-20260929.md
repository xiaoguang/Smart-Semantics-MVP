# 四命令按入口证据：固定源码验收记录

状态：2026-09-29 工程与固定源码技术验收通过，Git/PR收口中；运行时地址、全量逐边准确性及长等待根因仍有限制。本页按实际运行身份记录独立R1/R2/R3/R4、JDT具名复核及历次修正，不把早期有缺陷的完成运行当作最终结果。历史前后端合并的三命令验收见[旧结果](technical-analysis-fixed-source-acceptance.md)，不把旧运行改名为新运行。

## 固定输入与边界

- 源码准备 R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`，固定提交 `8c30ce7861570458920175e200bb2a6442713580`。
- 后端编译输入：此前获授权的官方 Maven classpath（106 个当前可读 JAR）、同源 effective POM 和目标 JDK8；本轮 Java 不运行 Maven、不下载依赖。
- 新验收只运行技术采集和组装，止于 Step05；不调用业务模型、不生成 Activity 或业务过程。
- 运行结果保存在本机被忽略的 `.workspace`；本文记录逻辑运行身份，不把本机路径、JAR 目录或凭据提交为产品证据。

## 当前实测结果

| 操作 | 运行或测试 | 已确认 | 尚未确认 |
| --- | --- | --- | --- |
| 固定源码 JDT 定向调用 | 扩展后的 `JdtFixedSourceNavigationIT`，1项通过、0失败、0错误，约493秒；完整 Maven 命令约8分41秒 | 同一 R0/JDK8/Maven 输出下，`AccountController#getList(search)`重载、`setPageSize(Convert.toInt(...))`嵌套调用、财务 Mapper、`logger.error`不误连`AjaxResult.error`；`AccountHead#getBillNo`不误连`GeneratedCriteria`；统计入口的已确认 Mapper 目标均为`DepotHeadMapperEx`；旧7个缺口本次均取得方法材料 | 固定源码中的合法多态具名案例、全量索引准确性，以及长等待的具体根因 |
| 新 R1 `collect-frontend` | `analysis-run:a8cc9b0ed52695102a0f7167812f7278ca0ea9e3cb48c9a64e4312f2d770d06b`，正式 CLI `COMPLETED/READY`，`inspect` 重开 `FINISHED/READY` | 保存 `frontend-http-index-v2`：1 HEADER、307 FILE、3 CONFIGURATION_FILE、68 SOURCE_UNIT、282 COMPONENT_USE、51 HTTP_REQUEST | 运行器重建索引时漏传辅助源码单元；请求仍有`getQueryParams`引用，但其第130–144行正文没有成为`SOURCE_UNIT`。修复后须新建R1；51也不是全仓请求总数证明 |
| 修复后 R1 `collect-frontend` | `analysis-run:fa28a3fd4ccf3005f1083640f6f203beed382dbace5bbe3953cd57cab518f28e`，正式 CLI `COMPLETED/READY`，`inspect` 重开 `FINISHED/READY` | 同源重跑保存 1 HEADER、307 FILE、3 CONFIGURATION_FILE、69 SOURCE_UNIT、282 COMPONENT_USE、51 HTTP_REQUEST；`JeecgListMixin.js` 第130–144行现在是实际`SOURCE_UNIT`，而非仅有请求引用 | 尚须由R4证明这一单元被正确纳入最终入口证据；51仍只是已发现请求数 |
| 新 R2 `collect-code` | `analysis-run:9cf771c2816b73d0072ee066bdc56b2a385a5a564ba5b1782fe90cab0a211ca3`，正式 CLI 失败，运行状态 `FAILED` | 编译环境报告已保存 `READY`、`diagnosticCoverage=UNCONFIRMED`；后端入口发现已发布339条；JDT循环处理了339/339入口 | Java索引在Step03发布前失败，没有可供R3消费的R2结果；底层发布不变量、调用准确性尚未确认 |
| 修复后 R2 `collect-code` | `analysis-run:278f1055c8693a2efe53ca8d2192fec6c9d61f479c40ad45c810cdf34603d4d7`，正式 CLI 退出0，`inspect`返回`FINISHED/READY` | 339/339入口完成采集并发布可严格重开的`JAVA_CODE_INDEX_V3`；339条`ENTRY_MEMBERSHIP`均为`COLLECTED`。财务入口的`logger.error`为外部SLF4J调用，Mapper目标为`AccountHeadMapperEx`；已知两条方法名筛查异常对应同一个合法覆盖方法，不是错边 | 其它入口的语义准确性不能由这些抽查推出；例如`getFinishDepositByNumberExceptCurrent`仍是未确认候选，未展开对应SQL |
| 新 R3 `analyze-persistence` | `analysis-run:bea6ae4a70d24c5dd5ae6ea819bf88e4e07500f9f4eae8f78fc0d6d21d40fb9a`，正式 CLI `COMPLETED/READY` | 从上述R2独立生成并保存`PERSISTENCE_MATERIAL_INDEX_V2`，1,780条JSONL、约4.5 MB；未重新启动JDT | 尚须核对所有动态SQL限制和历史读取 |
| 首次新 R4 `assemble-materials` | `analysis-run:6c23ab2237c766655c52b0dfeab834c696da82878e7ef5c459da7a8e31f3c1ff`，正式 CLI `COMPLETED/READY` | 339个入口目录项、339份入口JSON、51条前端请求覆盖；采购查询候选文件包含四个实参、第五个`NOT_PASSED`、`getQueryParams`完整第130–144行、Mapper XML动态条件及SQL AST的`ORDER_BY` | 当前51条前端请求均因运行时baseURL未确认而为`UNRESOLVED_REQUEST`候选，不能宣称部署连通；当前入口JSON仍泄露JDT临时工作区绝对URI，须修复并新建R4后验收 |
| 可移植位置复验 R4 | `analysis-run:279d270cce438c25f4e1d94b491bb6add1fd941ff309a159d42ddda69a528040`，正式CLI完成 | 入口目录339条、入口文件339份、前端覆盖51条；临时绝对URI已不出现在结构化观察/限制的抽查字段 | 由于JDT真实URI还含一段生成的项目目录，该版有135,243处已知源码位置被误投影为`UNCONFIRMED_JDT_WORKSPACE_SOURCE_IDENTITY`，不能作为最终证据 |
| 生成项目段修复后 R4 | `analysis-run:f911696acc427e76d6161b3f5ae9dedd36f35f3de881360337bbcd82012a4d88`，正式CLI `COMPLETED/READY`且`inspect`严格重开为`FINISHED/READY` | 339份入口文件、51条前端覆盖；入口文件扫描中上述未确认标记为0，JDT临时工作区绝对URI为0；具名观察已恢复为相对路径和行列位置 | 仍需完成独立代码审查发现项、最终artifact查询及质量检查；此运行是位置修正的实测，不先宣称整项验收完成 |
| 完整来源身份及覆盖格式修正后 R4 | `analysis-run:020fa267a6f4d86e4f054d14d23d63a5809512a8e22646df7fe9dbc1e1821256`，正式CLI `COMPLETED/READY`且`inspect`严格重开为`FINISHED/READY` | 索引339条、顶层入口文件339份、前端覆盖51条；目录和具名入口均保存`PREPARED_V1`、准确R0引用、源码版本及`effectiveScopeDigest`；入口文件中未确认位置标记0处、JDT临时绝对URI文件0份。采购候选仍含四个实参、未传第五参数、`getQueryParams`第130–144行、动态XML和SQL排序结构。正式`artifact`分别查询目录、该入口和前端覆盖，均退出0并返回预期版本、339条入口、该入口身份和51条覆盖 | 定向回归、静态质量与PR仍需完成；运行时baseURL未知，不能把候选关系写成已确认部署链 |
| 最终前端 R1 | `analysis-run:fcbd79f8f14f28c5c14aea1d98b9c415458b02cd4cbf407b58b06145cc951bf4`，正式 `collect-frontend` 为 `COMPLETED/READY` | 同源重新采集后，完整方法单元范围包含页面 `onSearchLinkApply()`、子组件 `purchaseShow(...)`、mixin `loadData(...)` 及 `getQueryParams()` 的方法名与正文；前端定向测试19项通过。它只保存前端事实，不提前绑定后端入口 | 运行时 baseURL 仍未确定，不能从静态路径声称真实部署连通 |
| 最终按入口 R4 | `analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`，正式 `assemble-materials` 为 `COMPLETED/READY`，正式 `inspect` 严格重开为 `FINISHED/READY` | 复用原 R2/R3，索引339条、入口文件339份，6条 `ASSEMBLED`、333条 `ASSEMBLED_WITH_LIMITATIONS`；前端覆盖51条。目录、具名采购查询入口、前端覆盖三类正式 `artifact` 查询均退出0；完整 `PREPARED_V1` 来源身份保留，入口文件中临时 JDT URI 与误投影标记扫描均为0份。具名入口正文实际含上述四段完整方法声明、四个已传参数、第五个未传参数及 XML/SQL 限制 | 51条前端请求仍为 `UNRESOLVED_REQUEST` 候选；长等待根因、其它入口逐边准确性和固定源码的业务多实现案例未证实 |

R1 中采购订单页面 `PurchaseOrderModal.vue#onSearchLinkApply` 的请求 `jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue:20386` 保存 `GET /depotHead/list`、页面实例和通过 `LinkBillList`、`JeecgListMixin#getQueryParams`、`getAction`、`axios` 的静态调用路径。四个实际参数为`'其它'`、`'请购单'`、`'客户'`、`"1,3"`；第五个 `purchaseStatus` 明确为 `NOT_PASSED`。旧R1的`supportingSourceUnits`仅引用 `JeecgListMixin.js` 第130–144行，没有把正文列为`SOURCE_UNIT`；新R1经修复后将该范围另存为第69个源码单元，并由正式`inspect`核对可重开。`window._CONFIG['domianURL']` 是运行时地址表达式，`/jshERP-boot` 只是保存的静态后备值；它们没有证明实际部署地址或后端连通。

旧 config-v2 新生产分支从当前运行器移除后，使用旧运行的原 config-v2 对旧 R3 `analysis-run:4291ab98b5119c0853f61f4ac30f2d36c164b3238a017de8a1d6ad3fd3197359` 执行正式 `inspect`，实际退出 0，返回 `FINISHED/READY`及旧六项产物。这核实了**该历史运行的查询**仍可用；旧格式全部历史案例不能由这一次查询证明。旧Markdown导出、Activity准入及直接受影响的历史读取由下述定向测试覆盖。运行器生产编译和最后代码状态的质量检查均已通过。
同一份清理后编译代码重新 `inspect` 本轮新 R1，也实际退出 0，返回 `FINISHED/READY` 与唯一 `FRONTEND_HTTP_INDEX_V2`；两种版本的已保存运行均未因生产入口退役而丢失可查询性。

该 R1 记录仅回答“页面中观察到什么请求及参数”。最终R4另取R2后端入口，并把采购页面请求保存到`GET /depotHead/list`入口的`candidateRequestUses`；由于运行时地址条件未确认，`resolution=UNRESOLVED_REQUEST`，不是确定的部署连通，更不是已执行的业务流程。

首次 R4 的入口状态为`ASSEMBLED` 6条、`ASSEMBLED_WITH_LIMITATIONS` 333条、`NOT_ASSEMBLED` 0条。339份入口JSON合计174,458,921字节；模块与步骤各保存一份，因此整个Step05目录占用约335 MiB。这是结构化证据大小，不是模型输入大小或业务过程数。`GET /depotHead/list`单入口文件约3.56 MB，含134个已收集Java方法、487处按入口记录的调用、12条关联XML statement及12份SQL分析；其1,939条限制中有1,528条`TARGET_LOCATION_IS_NOT_A_CALLABLE_DECLARATION`、231条`NAVIGATION_CONFLICT`、139条`NAVIGATION_CONFLICT_NOT_EXPANDED`、29条`BINDING_DECLARATION_MISMATCH`及12条前端地址未确认。限制数量不能解释成1,939个业务缺口。

首次 R4 的可移植性扫描发现，339份入口文件中322份含JDT临时工作区绝对URI。字段级统计为：`java.calls[].observations[].displayIdentity` 67,219处、`limitations[].detail` 67,219处、合法多态的`java.calls[].targets[].displayName` 2处；结构化`sourceRefs`仍为相对路径。随后只修改R4投影，保留原始R2/R3；最终R4的339份入口文件扫描中临时绝对URI为0，已知位置错误匿名标记为0，并以真实相对源码路径取代。不能把首次有缺陷R4冒充最终结果。

扩展 JDT 定向复核的旧7个入口在**同一会话**中的耗时依次为：商品批量设状态 10,836 ms、删除资源 1,643 ms、批量更新 7,366 ms、导入 160,019 ms、名称检查 8,297 ms、批量删除 30,099 ms、库存单据统计 68,094 ms；`JDT_QUERY_FAILED` 为 0。这证明本次没有重现旧7个缺口，但也实测仍有长等待。此前约51秒等待的具体内部任务及根因仍未确认；不能把一次成功写成性能故障已修复。

在固定源码的 `jshERP-boot/src/main/java` 中，已找到的显式 `implements` 主要是 JDK/框架接口；没有找到可用于本次真实入口验收的“项目内业务接口有多个实现”案例。因此，合法多态保留由受控直接测试验证，不能记为这份固定源码的实际多态案例通过。Mapper 是接口与运行时代理的另一种关系，不能冒充多个仓库内实现。

首次正式 R2 在逐入口采集期间有大范围耗时差异。具名 `POST /depotHead/batchAddDepotHeadAndDetail` 报告141个方法、795处调用，单入口耗时约292秒；这不等于141个业务步骤。首次运行在`completedSelected=339 selectedTotal=339`后、Step03发布前抛`IllegalArgumentException`并以`FAILED`结束；外层`TECHNICAL_CONFIGURATION_INVALID`不能证明配置错误。两个聚焦直接测试复现了`LOCATED`目标被v3发布投影过滤后违反“已定位须有目标”的不变量。修正后的新R2实际发布并严格重开339条`COLLECTED`入口，说明该故障在新运行中不再出现；首次运行未保存底层异常明细，不能反推其唯一根因。

## 判定及剩余检查

1. 具名 Mapper、`getBillNo`、重载、嵌套及原7入口复核已执行；本固定源码没有可用作真实“项目内多个实现”验收的具名案例，合法多态只由受控直接测试覆盖。不能由这些样本推断全量每条调用边均正确。长等待的具体根因仍未确认。
2. R1/R2/R3/R4分别由正式命令保存，最终R4严格`inspect`以及目录、具名入口、前端覆盖三种`artifact`查询均通过。直接测试将含两个入口、目录和前端覆盖的已安装存储整体搬至新目录后，按原引用重开成功。该测试证明存储地址不依赖原目录；没有把本机整个295 MiB客户运行复制到别处。
3. 采购页面到后端列表查询及Mapper XML有已保存的**静态候选材料链**；动态baseURL、未传的第五参数和动态SQL限制均明确保留。它不是采购订单创建、入库或付款已发生的证明。
4. 四命令已在固定源码上全量运行；真实业务模型调用为0。最后代码的定向回归与静态质量均已通过；工程通过与未确认的运行时／全量语义范围必须分开报告。

## 最终产物与工程核验

最终 R4 索引是217,712字节，前端覆盖文件485,344字节，具名 `GET /depotHead/list` 入口文件3,053,567字节。Step05存储目录约295 MiB，原因是模块产物和步骤产物各安装一份；这是本机保存体积，不是单次模型输入。339份入口文件中，临时 JDT 工作区 URI 和 `UNCONFIRMED_JDT_WORKSPACE_SOURCE_IDENTITY` 标记的扫描命中均为0份。`more-findings.md` 的 SHA-256 保持 `59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`。

直接测试分组独立报告，不相加冒充不重复用例数：入口、CLI、JDT导航、SQL等第一组146项通过；历史读取、旧Markdown、Activity准入、前端和持久化第二组34项通过；JDT helper直接测试10项通过；搬移存储的测试所在类6项通过。前端真实源码范围的单独19项包含在第二组。最后一项测试加入后，使用JDK26质量宿主、Java17工具链执行`-Pquality -DskipUTs -DskipITs spotless:check verify`，退出0且`BUILD SUCCESS`；Spotless 698个文件均符合格式，SpotBugs 0告警，PMD通过。该质量命令按约定跳过重复的单测与集成测试，不能代替上述定向测试。
