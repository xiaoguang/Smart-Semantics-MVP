# 固定源码技术分析验收结果

本页记录固定 jshERP 提交 `8c30ce7861570458920175e200bb2a6442713580` 的正式技术运行结果。它只评价源码准备后的 Step02–05，不评价 Activity、业务过程或九章生成。技术命令可运行、产物可重开；**Java 调用目标准确性尚未通过验收，不能把本次结果称为完整通过。**

## 输入与运行

源码准备 R0 为 `analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。外部官方 Maven 输出由获授权的命令取得：`maven-dependency-plugin:3.11.0:build-classpath` 给出 106 个可读 JAR；`maven-help-plugin:3.5.1:effective-pom` 给出同源已求值项目配置。Java 程序只读取和核验这些输出，不调用 Maven、不下载 JAR，也不运行客户编译或应用。目标平台为 Java 8。

| 操作 | 运行 ID | 已保存结果 |
| --- | --- | --- |
| `collect-code` R1 | `analysis-run:a4f749d142e233f0bf626821598d29822452f868da74674297022c4f32d19c26` | 就绪报告、后端入口、前端请求关联、Java 调用索引 |
| `analyze-persistence` R2 | `analysis-run:549157410980d1837094def5abd5d398b7d3c83a44cd0206d2fda4784e74e5da` | Mapper XML、Java 绑定和静态 SQL 分析 |
| `assemble-materials` R3 | `analysis-run:4291ab98b5119c0853f61f4ac30f2d36c164b3238a017de8a1d6ad3fd3197359` | 入口阅读包、前端与入口覆盖、技术 Markdown |

R1 的 `readiness=READY` 表示已知来源、JAR、目标 JDK及项目绑定检查通过；`diagnosticCoverage=UNCONFIRMED` 表示工具不能证明所有文件的诊断均已结束，不是“零诊断”证明。R1 共发现 339 个入口，332 个完成调用收集，7 个因两个物理 JDT 定义查询失败而未完成。该R1所用适配器把查询超时和服务器异常合并为 `JDT_QUERY_FAILED`，不能从这份结果进一步确定根因。

后续已修正查询等待超时的缓存处理，43项直接自动化测试通过，见[定向排查和修正](jdt-definition-query-failure-experiment.md)。2026-09-29使用新代码定向重验七入口：同一轮6个返回材料、1个首次等待超时；剩余入口在新会话单独重验成功，但最后一次诊断又超时。所有入口已有成功实验context，首入口稳定性仍未通过。两份JFR定位到定义解析前的文档生命周期等待，具体调度根因未确定。实验没有安装为正式运行，因此上述339/332/7覆盖和准确性结论不变。

7个未收集入口是`POST /material/batchSetStatus`、`DELETE /material/delete`、`POST /material/batchUpdate`、`POST /material/importExcel`、`GET /material/checkIsNameExist`、`DELETE /material/deleteBatch`和`GET /depotHead/getBuyAndSaleStatistics`；R3对应记录的`packetIds=[]`。保存的导航journal把它们关联到`MaterialExample.java#14:27`和`DepotHeadController.java#647:35`两处定义查询失败，前一失败经缓存影响6个入口。上述位置是查询键，不直接当源码行号解释。查询记录只保存通用错误，未保留足以区分超时与服务器异常的底层原因；不能据此承诺重试即成功。

[修复前的定向实验](jdt-definition-query-failure-experiment.md)使用与R1逐字节一致的编译输入，前两次直接查询均成功；随后通过生产收集器重测七入口，3个返回实验context、4个因同一超时及失败缓存中止。超时位于`StringUtil.java`：程序等待30秒后报错，但原请求51.404秒后正常返回，后续入口仍重复原缓存异常。修正后的重验在`LogService.java`再次观察到50.629秒迟到成功，此次后续入口能使用成功结果，不再继承旧超时。JFR表明新慢请求在定义查找前等待文档生命周期任务，不能倒推原R1两次失败的底层原因。首次超时入口仍须明确重验，未自动恢复；实验不改写历史覆盖。

此外，R1索引保存11个文件的错误诊断：8条是方法未找到（如`getCode()`、`getId()`、`setInitStock(...)`），3条是`log cannot be resolved`。`EntryCodeCollector`对每个文件仅保存第一条错误，所以11不是完整错误总数。这些诊断与错误调用候选、普通`UNRESOLVED_CALL`及导致7个入口缺包的查询失败须分开：现有记录没有证明它们具有同一根因。已收集入口里的未解析调用包含JDK/外部库调用；不能把每条都解释成仓库业务方法缺失。按最终R3覆盖，本轮后端入口未生成阅读包的具名原因只有上述查询失败；其他准确性问题仍影响已生成材料的可信程度。

R1 保存 51 条前端 HTTP 请求及 51 条后端入口关联。R2 保存 61 个 XML 资源、573 条 statement、572 条 Java 绑定及 573 条 SQL 分析。R3 保存 47 个阅读包、339 条入口处置和 51 条前端处置；其中 332 个入口标为 `COLLECTED_WITH_LIMITATIONS`、7 个 `NOT_COLLECTED`。49 条前端请求进入阅读包；`CustomerAccount.vue` 与 `VendorAccount.vue` 各有一条请求因 `FRONTEND_SOURCE_UNIT_EXCEEDS_PACKET_CAPACITY` 未选。限制均保留在正式覆盖记录中，不计作完整收集。

正式 CLI 从已完成的 R3 导出 `CODE_READING_MATERIALS_V2` 技术 Markdown，已用具名测试和上述真实运行验证。真实导出文件为 199,007,072 字节、1,360,420行（约199.0MB／189.8MiB），适合检索，不适合逐页通读。它保留已选技术原文和来源；前端 `requestId` 后的数字目前是 UTF-16 偏移，不能当行号。例如 `PurchaseOrderModal.vue:20386` 的实际前端 `callRange.startLine` 是421，完整函数单元是420–423行。

### 实际文档结构与体积

文件为`.workspace/technical-analysis-cli-20260926/technical-r3-reading-materials.md`；其对应R3正式`code-reading-materials.jsonl`为13,175,255字节、438条记录（1条头、47个包、339条入口处置、51条前端处置）。JSONL主要保存组包选择及准确上游引用；Java方法和调用、XML/SQL在重开时从对应R1/R2恢复，因此这13.2MB并不是可脱离上游使用的全部源码副本。

Markdown逐包显示入口、调用树、Java方法全文和形参、入口所属调用及实参、前端请求与源码单元、Mapper XML、Java/XML匹配、SQL分析副本、来源、未选单元和限制，末尾显示入口覆盖。现有渲染器未单独列出完整前端覆盖表；两条未选请求应查JSONL，不能说Markdown已显示所有覆盖信息。

按Markdown三级标题统计，调用树约80.6MB、逐调用明细约93.9MB，二者合计约占88%；Java方法部分约6.18MB、XML原文约12.18MB。统计按行归组并统一换行为LF，仅用于解释体积，文件精确大小以前述字节数为准。当前输出把调用信息以树及列表重复展示，并按入口/包重复出现共享内容，确有体积及可读性问题；不能把199MB全部解释为不可省略的独有源码，也不代表它应整份送给LLM。

原文例子可直接查Markdown第456342行的采购订单页函数和第458052行的`selectByConditionDepotHead` XML。前者实际调用`purchaseShow('其它', '请购单', '客户', "1,3")`；后者保留`statusArray`的动态`foreach`过滤。读者可核对页面传参、后端调用和SQL条件，但这不是业务生命周期结论，也不能忽略本页列出的错误候选及缺失入口。

## 具名链路与准确性

采购订单页面的“关联请购单”查询，在 R1/R2/R3 中可沿 `PurchaseOrderModal.vue` 页面实例、`LinkBillList`、`JeecgListMixin`、`getAction/axios`、`GET /depotHead/list`、Controller、Service、`DepotHeadMapperEx.selectByConditionDepotHead`、同名 XML statement 和部分静态 SQL 查阅。页面传入 `status="1,3"`；第五个 `purchaseStatus` 参数未传。`window._CONFIG['domianURL']` 是动态地址表达式，仅保存了 `/jshERP-boot` 静态后备值，未证明实际部署地址。动态 XML 未执行，SQL 分析状态为 `PARTIAL`。这条链表示“查询可选的请购单”，不表示采购订单、入库或付款已经发生。

旧索引中 `logger.error` 被误连到 `AjaxResult.error` 的具名错边，在新 R1 未重现；具名 `depotHeadMapper` 调用未再混入其他 Mapper 目标。这说明补齐编译输入确实改善了这些案例，**不说明所有错边已消失**。

新 R1 仍有已确认的错归候选：源码 `pageDomain.setPageSize(Convert.toInt(ServletUtils.getParameter(PAGE_SIZE), 10))` 的内层 `Convert.toInt` 调用，同时保存正确的 `Convert.toInt(Object,Integer)` 和不属于该调用点的外层 `PageDomain.setPageSize(Integer)`。错误候选仅来自 `CALL_HIERARCHY`；正确候选还得到 `DEFINITION` 和 `IMPLEMENTATION`。`accountHead.getBillNo()` 也错误带入另外两个 `GeneratedCriteria` 方法，二者同样仅来自 `CALL_HIERARCHY`。现行 `JdtNavigationResolver` 依据 `fromRange` 包含 AST 导航位置就挂接调用层次候选，然后合并三类导航结果；此归属规则对嵌套或相邻调用不够精确。已确认的是保存结果与源码不符，不能仅凭保存索引断言 JDT 原始实现错误。重载的 `Convert.toInt(Object,Integer)` 正确目标存在，但嵌套调用准确性检查不通过；不能以其正确目标仍在为由放行整条调用边。

具名接口边界 `DepotHeadMapperEx.selectByConditionDepotHead` 在 Java 索引和同名 MyBatis XML 中均可定位，说明这条静态 Mapper 关联未因只保留具体类而丢失。该项目的这条 Mapper 关系不是“同一 Java 接口有多个仓库实现类”的多态案例；后者仅有直接工具测试，不能冒充固定源码的真实多态验收。

## 验证边界与当前结论

直接覆盖本次 Markdown CLI 的具名测试 1/1 通过，验证正式 R3 查询包含页面请求、Mapper XML、SQL，且不增加 JDT/Node 启动。格式、编译、SpotBugs 和 PMD 在本模块质量命令下通过；该命令显式跳过单元与集成测试，不能当作全套测试结果。旧历史产物没有改写，真实业务模型调用为零。

尚未通过的验收项是：新错边的 Step03 归属修正与受影响入口重验、两个定义查询失败的实际原因、其他重载/多态代表案例的完整人工核对，以及两条未选前端请求的容量缺口。现有计划明确禁止临时扩张 Step03 算法；若要修正，须先确定窄范围，不能在报告里把错误候选说成已解决。工程产物存在和业务/技术准确性通过是两项独立结论。
