# 冻结 jshERP：采购订单关联请购单的 Vue 到 SQL 走读

## 先看结论和来源

本例是采购订单弹窗**打开关联请购单选择列表**的查询：`PurchaseOrderModal.vue → LinkBillList.vue → JeecgListMixin.js → getAction → axios GET /jshERP-boot/depotHead/list → DepotHeadController.getList → DepotHeadService.select → DepotHeadMapperEx.selectByConditionDepotHead → XML <select>`。查询结果经 Controller 的 `getDataTable` 返回，再由 mixin 填入列表 `dataSource` 和分页总数[Controller:99–101][controller77]、[列表 mixin:99–105][mixin90]。页面文案提出“提交之后原来的请购单会对应的改变单据状态”[采购订单控件][order57]，但这条查询不包含采购订单提交、请购单状态改变或采购入库；采购入库的“关联订单”使用同一共享对话框的另一个 `show(...)` 分支[采购入库入口][in459]。

本页以 [manifest:592](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:592)、[manifest:606](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:606) 所属的固定 `snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c` 为源码。文件链接指向快照按 SHA-256 保存的 blob；下面的 Vue→HTTP→Java→XML 连线是**人工逐边核对**，不是现有程序自动生成的一条端到端链。已有技术产物来自 `analysis-run:5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac`；现有程序只分别保存后端入口、JDT 索引、持久化材料及后端阅读包。目标前端索引、跨步骤关联和三个新命令尚未运行或验收；本轮没有运行 JDT、客户构建、数据库或模型。

| 步骤 | 输入 | 处理与输出 | 实际下游／成熟度 |
| --- | --- | --- | --- |
| Step01 | 指定冻结源码 | 校验范围并保存 inventory、原文 blob；不赋业务含义 | 已有旧来源供 Step02、Step04 及 Step05 reader；新技术命令目标改读已准备的 `SelectedSourceBasis`，不重抓源码。 |
| Step02 | 准备版来源、构建选择/仓库策略或显式可信编译环境、前端配置 | 目标先准备并核验依赖/诊断，再用一套 JDT 会话发现 Java 入口与 Mapper；独立保存 Vue 请求、封装及 `ENTRY_LINK` 候选 | 旧产物只有后端入口；目标入口供 Step03，前端索引供 Step05，实际环境与就绪报告控制 Java 是否可继续。 |
| Step03 | 同次 Step02 入口、就绪的 Java project | JDT 保存完整方法正文、物理调用位点、实参与目标候选 | 旧索引供 Step04 绑定和 Step05 组包；目标与 Step02 同属 `collect-code` 一次会话。 |
| Step04 | 指定 Step02/03 publication、同源 XML | 无 JDT 地关联 Mapper/statement，保存 XML 原文、绑定及 `PARTIAL` SQL 分析 | 旧材料供 Step05；目标 `analyze-persistence` 另开运行，重开准确 Java 上游。 |
| Step05 | 指定 Step04 及其准确 Java 上游、Step02 前端索引和完整 source units | 无 parser 地按入口选择、计量、保存来源/未选/覆盖与可重开的 v2 Packet | 旧 v1 只有后端材料；目标 `assemble-materials` 供技术查询/Markdown。未适配的业务消费者不得读 v2 后丢 Vue 正文。 |

## Step01：原文来源，不赋业务含义

输入是固定 jshERP 源码；旧运行的 [source-inventory.jsonl:606](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/01-verified-source-inventory/source-inventory.jsonl:606) 确认 `PurchaseOrderModal.vue` 为可分析 UTF-8 文本、SHA-256 为 `e270a1c1…87b`。快照还保存 Java、XML、共享前端文件原文。处理是列出、保存与核验字节和有效范围；输出供 Step02/04/05 按同源读取。此步不会解释“请购→订单→入库”的业务关系。新的 `prepare-source` 则产出版本化准备结果及生效排除范围；未来三个技术命令必须明确绑定它，不能把旧运行直接说成准备版输出。[Step01 当前合同](../analysis-steps/01-verified-source-inventory.md)

## Step02：从页面动作到 HTTP 入口

目标 `collect-code` 先读已发布的准备版来源及生效排除，再由 Step02 按选定模式生成或读取 `java-compilation-environment-v1`：默认的 `AUTO_MAVEN` 用固定 Maven Model Builder/Resolver 解析普通声明式依赖，缓存不足时仅从配置允许的仓库取得文件；`PROVIDED` 则读取显式选择的受信任环境清单。两种模式都核对模块与 source set、目标 JDK、构建 profile、直接/传递编译依赖、二进制摘要与顺序、生成源码是否已在固定输入内；都不执行客户 Maven CLI、插件、扩展或生成器。

准确顺序是：保存实际环境的私有阶段记录 → 建立 `javaAnalysisBasis` → 用该环境打开JDT并收集诊断 → 形成最终就绪报告 → module 5 一次安装 `java-compilation-environment.json` 和 `java-analysis-readiness.json`。只有就绪后才继续Java入口发现和Step03，它们共用**同一会话和已核验环境**。若依赖准备已经受阻，则不启动JDT，仍可安装明确标为部分环境的检查报告。诊断范围或最终完成条件未确定时只能报 `UNDETERMINED`；已知缺依赖/类型错误报 `BLOCKED`，不能将零条已收到的诊断当成 `READY`。前端独立结果可保存；Java入口发现未运行时，前端关联必须标 `BACKEND_DISCOVERY_NOT_RUN`，不能报告“零匹配”。[自动依赖准备](../modules/technical-analysis/dependency-preparation.md) · [Step02 依赖就绪合同](../modules/technical-analysis/java-readiness.md)

成功的完整 Step02 publication 目标共 **8 个步骤文件**：原 profile、entry、mapper、capability、step receipt，加上述 module 5 两份 JSON 和 module 6 的 `frontend-http-index.jsonl`；module receipts、缓存和私有诊断另计。这是拟实施的保存合同，不是旧运行已有 8 个文件。[运行保存合同](../modules/technical-analysis/cli-and-runtime.md)

固定快照只列有一个内部 Maven 文件 [`jshERP-boot/pom.xml`][manifestpom]：它声明外部 `spring-boot-starter-parent` 2.0.0.RELEASE 与 `java.version` 1.8[父POM与版本][pom13]，compiler source/target 也写成 1.8[编译声明][pom201]；这些不是已解析出的完整 classpath，也不能由文件声明断言实际编译成功。当前 `approvedClasspath` 只验证**配置中列出的**本地 JAR 是文件并计算 SHA-256，现技术运行器把 source level 传为硬编码 `17`[现有项目校验][verifiedclasspath]、[现有运行器][runner128]；它没有核实此快照所需的全部传递依赖、profile、生成内容和各文件诊断。此处并无补齐依赖后的真实 Step02/03 验收结果。本例能证明的是这一个冻结后端模块及页面代码的人工连线；多模块隔离是目标工具的通用设计边界，不能把它写成本快照已经出现的结构。

[PurchaseOrderList.vue:218][list218] 装配采购订单弹窗。弹窗的“关联请购单”搜索框绑定 `onSearchLinkApply`，而弹窗导入、注册并渲染 `LinkBillList`；后者的 `@ok` 才回到 `linkBillListOk`[弹窗控件][order57]、[组件装配][order181]。关键调用原文是 [PurchaseOrderModal.vue:420–422][order420]：

```js
onSearchLinkApply() {
  this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', "1,3")
  this.$refs.linkBillList.title = "请选择请购单"
}
```

`LinkBillList.purchaseShow(type, subType, organType, status, purchaseStatus)` 按位设置 `queryParam.type/subType/status/purchaseStatus`、`showType='purchase'`，然后 `loadData(1)`[共享组件:234–245][link234]。因此这次调用的前四参确定，第五参没有传入；`purchaseStatus` 被设为 `undefined`，序列化 JSON 时不会作为字段出现。`organType='客户'` 只调整列表列标题，本请购单分支又删去该列，不是 HTTP 筛选项[列配置][link247]。不能借隔壁 `onSearchLinkNumber` 的销售订单分支补上 `purchaseStatus='0,3'`。共享组件的 `url.list` 是 `/depotHead/list`[列表 URL][link209]，`disableMixinCreated=true`，打开时由显式 `loadData(1)` 触发，而非 mixin 的 created 自动加载[生命周期][mixin73]。

`JeecgListMixin.loadData` 组 `getQueryParams()` 后执行 `getAction(this.url.list, params)`；`getQueryParams` 把当前查询对象序列化为一个 `search` 字符串，并加排序、字段、分页[列表 mixin:90–143][mixin90]。`filterObj` 只删顶层空值，不会拆开已序列化的 `search`[过滤函数][filter33]。基于**新实例、尚无用户输入**的初始 `queryParam`[默认筛选项][link148]，请求的关键数据长这样，省略列字段；这不是抓包或某次运行参数：

```text
GET /depotHead/list
params.search = '{"number":"","materialParam":"","type":"其它","subType":"请购单","status":"1,3"}'
params.currentPage = 1
params.pageSize = 10
params.column = 'createTime'
params.order = 'desc'
```

排序、分页默认值来自 [mixin:32–50][mixin32]；再次打开时保留的组件状态、用户输入编号/商品及表格操作会改变实参。`getAction` 以 `method:'get'`、`params: parameter` 调用已导入的 axios 实例[manage.js:43–49][manage43]。axios `baseURL` 来自 `window._CONFIG['domianURL'] || '/jshERP-boot'`[request.js:14–20][request14]；冻结 [public/index.html:258–259][html258] 将该值设置为 `/jshERP-boot`，后端 [application.yml:2–9][app9] 的 context path 相同，开发代理也转发此前缀[vue.config.js:50–57][vueconfig50]。在**这套冻结默认配置条件下**，完整路径静态对齐为 `/jshERP-boot/depotHead/list`；运行时 `_CONFIG` 覆盖、部署网关或代理改写仍未知，目标前端索引必须保留配置依据及未证范围。

旧 [Step02 entry-points.jsonl:215][entry215] 确实保存 `GET /depotHead/list`、`DepotHeadController#getList`、精确 methodKey 和 77–102 行范围；Mapper 目录也已保存[旧 Mapper 目录][mappercatalog]。它**没有** Vue 调用位置、封装链或基址。目标 Step02 module 6 才会以有效 `.vue/.js` 等文件为分母，解析模板事件、局部组件、import、mixin、实例 `this.url`、`getAction`、axios 和静态配置，保存 `frontend-http-index-v1` 的 `HEADER/FILE/SOURCE_UNIT/COMPONENT_USE/HTTP_REQUEST/ENTRY_LINK/DIAGNOSTIC`。被排除、无法解析或动态未知的文件/请求各有处置，不能只数已找到的请求。就本例，`ENTRY_LINK` 在冻结默认配置及静态已知条件下**预期** `MATCHED_UNIQUE`，指向该精确入口；这仍待有限原型和正式产物验证，不是当前程序输出或运行时分派证明。前端索引供 Step05 按入口反向取页面使用上下文，旧后端入口仍供 Step03 导航。[前端正式目标](../modules/technical-analysis/frontend-http-discovery.md)

## Step03：后端 Controller 与 Service 的实参

输入是 Step02 的精确 Java 入口和同一次 JDT 会话的 Java catalog。旧 [java-code-index.jsonl 的入口成员][membership61448] 对 `/depotHead/list` 标 `COLLECTED`，保存 282 个方法、713 个调用，包含不在本次点击上必执行的分支。Step03 保存完整方法正文、物理调用位置、按位实参/形参、声明及必要实现候选和入口自身的展开状态；Step04 读其中的 Mapper 调用/声明来做绑定，Step05 再读所选方法、调用、来源与限制来组包。目标 `collect-code` 复用现有 JDT 查询/缓存及此会话，不由前端 parser 新建第二个 Java 导航器。旧数量不证明所有 713 条边正确，更不证明一次 HTTP 点击会逐条执行。

后端 [DepotHeadController.java:77–101][controller77] 的核心源码是：

```java
@GetMapping(value = "/list")
public TableDataInfo getList(@RequestParam(value = Constants.SEARCH, required = false) String search,
                             HttpServletRequest request) throws Exception {
    String type = StringUtil.getInfo(search, "type");
    String subType = StringUtil.getInfo(search, "subType");
    String status = StringUtil.getInfo(search, "status");
    // 还读取 purchaseStatus、number、linkApply、linkNumber、日期、商品、人员等筛选项
    List<DepotHeadVo4List> list = depotHeadService.select(type, subType, hasDebt, hasLastDebt,
            status, purchaseStatus, number, linkApply, linkNumber, beginTime, endTime,
            materialParam, organId, creator, depotId, accountId, salesMan, remark);
    return getDataTable(list);
}
```

上面注释是**节录说明**，不是原文件字句；准确18个实参和 Controller→Service 的 `LOCATED` 目标已经在 [旧 Step03 CALL:32543][call32543] 保存。`DepotHeadService.select` 取得请求用户、角色 `priceLimit`、`getCreatorArray()`、`getOrganArray(subType,purchaseStatus)` 等，并将 `status.split(",")` 变为 `statusArray`，在 `PageUtils.startPage()` 后按21个实参调用 `depotHeadMapperEx.selectByConditionDepotHead(...)`[Service:108–130][service108]；旧 [CALL:47892][call47892] 有实参序列和 Mapper 声明候选。`StringUtil.getInfo` 把缺失键和空字符串归为 `null`[search取值][string350]：因此初始 JSON 中 `number:""`/`materialParam:""` 不会在这次初始分支形成非空筛选，未传的 `purchaseStatus` 也不会变为另一个页面的 `"0,3"`。`getCreatorArray` 依角色可返回当前人、部门人员或 `null`，只有非空时才约束创建人；紧接着 `creatorArray = StringUtil.isNotEmpty(purchaseStatus) ? null : creatorArray`，其它调用若传非空 `purchaseStatus` 就取消创建人数组过滤。本例的新实例请求未传该字段，不能以该分支推断总无创建人限制。`getDepotArray` 对请购单等类型不加仓库数组；`getOrganArray` 只对销售类子类型尝试客户过滤[Service:233–321][service233]。本请购单实例的具体权限值取决于请求用户，不能把 `creatorArray` 写成固定值，也不能将「无 `organArray`」误说成「无其它权限规则」。Service 后续还按返回 ID/单号查询押金、金额和商品摘要，并按 `priceLimit` 处理部分返回金额；这些后处理可能引出其它读取，但不能把整包713个调用都说成主 SQL 已执行[Service 后处理][service131]。

## Step04：Mapper 声明、条件 XML 与租户边界

目标 `analyze-persistence` 的输入是**指定** `collect-code` 的 Step02/03 publication、相同准备版来源内的冻结 XML 和可选 MyBatis 分析配置；它重开已存 Java 索引，不启动 JDT 或前端 parser，也不执行 SQL。输出是带准确来源/上游引用的 Mapper 绑定候选、完整 XML Resource、statement、SQL 分析状态及限制，由 Step05 再读取。现存 [Step04 JavaBinding:745][binding745] 对 `DepotHeadMapperEx.selectByConditionDepotHead` 给出 `EXACT_NAMESPACE_AND_METHOD_ID` **候选**，关联到 [Statement:164][statement164]。接口的 `@Param("type")`、`@Param("subType")`、`@Param("statusArray")` 等是[MapperEx.java:18–39][mapper18]的真实形参，不等于已运行 MyBatis 参数绑定。

冻结 [DepotHeadMapperEx.xml:66–181][xml66] 的关键查询片段如下。`<!-- 中间另有条件 -->` 是本页节录标记，并非原文；必须读链接中的完整 XML：

```xml
<select id="selectByConditionDepotHead" parameterType="com.jsh.erp.datasource.entities.DepotHeadExample" resultMap="ResultMapEx">
  select jdh.*, s.supplier OrganName, u.username userName, a.name AccountName
  from (select dh.id from jsh_depot_head dh
    left join jsh_depot_item di on dh.id = di.header_id
      and ifnull(di.delete_flag,'0') !='1'
    <!-- 中间另有 material / material_extend 左连接；where 1=1 -->
    <if test="type != null">and dh.type=#{type}</if>
    <if test="subType != null">and dh.sub_type=#{subType}</if>
    <if test="statusArray != null and statusArray !=''">
      and dh.status in (<foreach collection="statusArray" item="status" separator=",">#{status}</foreach>)
    </if>
    <if test="purchaseStatusArray != null and purchaseStatusArray !=''">
      and dh.purchase_status in (<foreach collection="purchaseStatusArray" item="purchaseStatus" separator=",">#{purchaseStatus}</foreach>)
    </if>
    <!-- 中间另有单号、关联号、日期、商品、往来单位、仓库等条件 -->
    <if test="creatorArray != null">
      and dh.creator in (<foreach collection="creatorArray" item="creator" separator=",">#{creator}</foreach>)
    </if>
    <!-- 中间另有账户、欠款、业务员、备注条件 -->
    and ifnull(dh.delete_flag,'0') !='1'
    group by dh.id order by dh.id desc) tb
  left join jsh_depot_head jdh on jdh.id=tb.id and ifnull(jdh.delete_flag,'0') !='1'
  left join jsh_supplier s on jdh.organ_id=s.id and ifnull(s.delete_flag,'0') !='1'
  <!-- 中间另有 user / account 左连接；最后 order by jdh.id desc -->
</select>
```

`status='1,3'` 被 Service 拆成 `statusArray` 后，XML 的 `foreach` 才可能成为 `IN` 列表[XML 状态条件][xml80]。初始请求中的空 `number` 和 `materialParam` 经 `getInfo` 归 `null`，对应 XML 条件在这个示例推导中不成立；用户后续输入则可能激活编号 `LIKE` 或商品字段匹配。`linkApply`、`linkNumber`、日期、往来单位、仓库、创建人、欠款、业务员和备注等 `<if>` 也仍在原脚本[XML 关联号条件][xml98]、[XML 其它条件][xml121]。主查询从 `jsh_depot_head` 选 ID，连接明细/物料用于可选筛选，按 ID 分组后再连接单据、往来单位、用户和账户以返回列表字段；它显式过滤单据及多张关联表的 `delete_flag`[XML 末端连接][xml173]。对本例重要的人员范围来自 Service 的 `creatorArray`；`organArray` 只在销售类等条件下有值，本请购单分支无该数组过滤，但这不等于没有其它人员或租户约束。不能把页面限定 `status='1,3'` 说成后端永远强制拒绝其它状态，也不能把这条查询说成创建采购订单的写入 SQL。

租户条件也不能凭 XML 里没有 `tenant_id` 就省略：冻结 [TenantConfig.java:26–96][tenant26] 配置 `PaginationInterceptor` + `TenantSqlParser`，从 `X-Access-Token` 求 tenant ID；非零 tenant 时使用 `tenant_id` 列，对若干系统表作例外，并对列出的若干 Mapper statement 过滤解析。本例 statement 不在那份列明的过滤清单中。可是本轮没有运行客户应用、拦截器或 MyBatis，不能写出本次查询最终插入了何种租户谓词、它作用于哪层子查询、是否与分页组合成功。此处把**配置代码事实**与**实际 SQL 未证明**并列保存。旧 [SQL_ANALYSIS:1309][sql1309] 是 `PARTIAL`，明确说动态 XML 只保留有序 DOM 投影、未执行。Step04 Resource 保存完整 XML 原文，但保存的 Statement 没有该例的精确 XML 块行范围；第 66–181 行是本页人工读 blob 找到的位置，不是旧产物自动证明的范围。

## Step05：旧包已有什么；目标包应怎样读

目标 `assemble-materials` 读取**指定 Step04 publication 所引用的准确 Step02/03**、同一个 Step02 的不可变 `FrontendHttpIndex` 与水化的完整 `FrontendSourceUnits`，沿已保存关系组织材料，不重新解析 Vue/Java/XML/SQL。组包前核对准备版来源、有效排除、`javaAnalysisBasis` 与各上游 owner，不能从同源码的另一次运行任选 Java 索引。输出在新 R3 run 保存 v2 Packet、后端入口 coverage 和独立 frontendCoverage；新版 reader 重开完整正文与来源，技术 Markdown/查询由 R3 的 `technicalOutput` 引用读取。现存 [PACKET:215][packet215] 是 **v1 后端包**：本入口的 282 个方法、713 个调用、17 份 XML Resource、19 条 statement/SQL 分析引用、299 个 packet 内 SourceRef；原有完整投影计量 1,060,223 UTF-8 字节，未选单元数为 0。旧 [ENTRY_COVERAGE:541][coverage541] 是 `COLLECTED_WITH_LIMITATIONS`，含未解析调用。包里有 Mapper 绑定和本例 statement，却**没有**页面、HTTP 调用位点、配置来源或 Vue→入口边。数字是旧固定运行的核查结果，不是未来 Step05 v2 的预算或预测数量。

旧 v1 JSONL 主要存 `methodKeys/callKeys/persistence/sourceReferences/unselectedUnits/limitations` 等引用；完整 Method 正文在 Step03，Resource 原文在 Step04。Reader 用准确 publication 重开并校验后，才交出完整只读 Packet；`source:1` 之类短 ref 必须连同所属 publication + packet 理解，不跨包裸连。目标 v2 同样不在 Packet 行复制整套前端 AST/原文：保存前端索引引用、所选 use/unit 与短 ref，reader 从已验证的同源原文恢复完整前端单元，投影字节计数包含这些单元。超过材料容量时保留原本完整后端入口，逐项记下未选的前端单元和理由，不把残缺页面链称为完整端到端材料。[当前 Step05 合同](../analysis-steps/05-business-flows.md)

目标 **`code-reading-material-set-v2` 的可读技术包目录**应按 [Step02 前端合同 §6](../modules/technical-analysis/frontend-http-discovery.md) 组织，以下仅示页面阅读顺序，不把并列节点充作业务时序：

```text
本入口技术材料（目标阅读投影；尚无实例文件）
├─ 页面触发与请求：PurchaseOrderModal、LinkBillList、mixin、getAction、baseURL
│  └─ 原始参数表达式、默认配置依据、ENTRY_LINK候选与未知条件
├─ 后端入口：GET /depotHead/list、Controller的search提取和返回
├─ Java调用：Service完整方法、实参/形参、人员与仓库范围、其它候选/边界
├─ Mapper/XML/SQL：声明、完整动态statement、Resource原文、PARTIAL分析
└─ 材料边界：limitations、未选单元、packet内SourceRef、入口及前端coverage
```

下面也是**拟议阅读投影 JSON，不是 `frontend-http-index-v1` 或 Step05 v2 的完整 wire、没有虚构的 key/ID，也不是程序输出**。字段组名对齐正式设计的记录与选择概念；真正 JSONL 每行还要有 `schemaVersion/recordType/key/payload`，其 key 只能由将来生产者计算：

```json
{
  "HTTP_REQUEST": {
    "page": "jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue",
    "trigger": "onSearchLinkApply → LinkBillList.purchaseShow",
    "actualArguments": ["其它", "请购单", "客户", "1,3"],
    "wrapperPath": ["LinkBillList.loadData", "JeecgListMixin.getQueryParams", "getAction", "axios"],
    "method": "GET",
    "relativeUrl": "/depotHead/list",
    "queryExpression": "search=JSON.stringify(queryParam)"
  },
  "ENTRY_LINK": {
    "matchStatus": "MATCHED_UNIQUE",
    "condition": "冻结index.html默认baseURL与application.yml context-path均为/jshERP-boot",
    "backendRoute": "GET /depotHead/list",
    "runtimeOverride": "UNKNOWN"
  },
  "PACKET": {
    "frontendSelection": {
      "indexRefs": "同一Step02前端索引引用，示意",
      "linkUseUnitRefs": "所选link/use/unit及完整单元短ref，示意",
      "sourceRefs": "前端来源短映射，示意"
    },
    "methodKeys": "所选Step03方法引用，示意",
    "callKeys": "所选Step03调用引用，示意",
    "persistence": "所选Step04绑定、Resource、Statement、SQL分析引用，示意",
    "sourceReferences": "packet内前端/Java/XML来源映射，示意",
    "unselectedUnits": "容量未选单元及原因，示意",
    "limitations": "静态候选、未知配置/调用和动态SQL限制，示意"
  },
  "ENTRY_COVERAGE": "原有后端入口覆盖，示意",
  "frontendCoverage": "前端FILE/HTTP_REQUEST/ENTRY_LINK处置、无匹配及不支持范围，示意"
}
```

Step05 v2 应反向从后端 `entryId` 取所有前端 `ENTRY_LINK`，为每个 use 保存页面、事件、封装路径、条件和完整来源单元；多候选保留，前端容量不够时逐单元报告未选，并继续保住完整后端入口。`frontendCoverage` 要报告未匹配请求、无页面的后端入口与未检查范围。新 v2 reader 返回前端/后端完整不可变 Packet，技术 Markdown 从 `technicalOutput` 按 R3 重新打开；但当前 Step06 不能因此被说成已读新 Vue 正文，未适配消费者应明确拒绝版本，不悄悄丢弃前端字段。新目标包的数量和字节数尚未测量，**不等于上面的旧 1,060,223 字节包**。

### 目标 reader 打开后的完整可读示意

以下是**为这一入口手工编排的目标技术 Markdown 样张**，不是已保存的 v2 文件，也不是 Step02–05 已自动产出的结论。正式产物须在每项后附 packet 内短来源 ref，并能按所属 publication 重开下列前端/Java/XML 全文；样张的省略号只为避免把旧包的 1 MB 正文复制进设计文档，不能成为实际 reader 的截断规则。

> **入口与用途**：`GET /depotHead/list` 对应 `DepotHeadController#getList(search, request)`；本页使用点是 `PurchaseOrderModal` 的“关联请购单”搜索框。该用途是查询可供选择的单据，不能据此推断采购订单已建立或请购单状态已变。[弹窗控件][order57]、[后端入口][entry215]
>
> **页面触发与参数**：`onSearchLinkApply` 把 `('其它','请购单','客户','1,3')` 送入共享 `purchaseShow`。它设 `showType='purchase'`、更新 `queryParam` 并 `loadData(1)`；新实例缺第五个 `purchaseStatus`，序列化时该字段不出现。`organType` 只改列标题。默认 `number`、`materialParam` 是空字符串；之后用户输入可改变它们。[页面调用][order420]、[共享组件][link234]、[默认查询][link148]
>
> **请求封装与定位依据**：`JeecgListMixin.getQueryParams` 将筛选对象放入 JSON 字符串 `search`，附 `currentPage=1/pageSize=10/column=createTime/order=desc`；`getAction` 交给 axios GET。`this.url.list='/depotHead/list'`，冻结 `index.html` 设 `domianURL='/jshERP-boot'`，后端 context path 同值，故仅在该默认配置下静态对齐此入口。运行时覆盖、网关及页面复用状态未知；正式 `ENTRY_LINK` 应记录条件化匹配与所有候选。[共享 URL][link209]、[列表 mixin][mixin90]、[GET 封装][manage43]、[默认配置][html258]
>
> **Java 接收、处理与返回**：Controller 从 `search` 取18个筛选实参，调用 Service。`getInfo` 将缺失/空值变 `null`；Service 生成 `statusArray=['1','3']` 的静态推导，按当前用户/角色生成可能的 `creatorArray`，对本“请购单”子类不生成仓库数组或销售类客户数组，分页后调用21参的 Mapper 方法。Mapper 返回后 Service 还可能读取押金、金额、商品摘要并按 `priceLimit` 调整部分金额；Controller 返回 `rows/total`，mixin 写入列表。具体用户权限、返回行和后续读取是否发生没有执行证据。[Controller][controller77]、[Service][service108]、[Service 后处理][service131]、[列表消费][mixin90]
>
> **Mapper/XML/SQL**：`DepotHeadMapperEx.selectByConditionDepotHead` 有对应 namespace/id 候选，`@Param` 和 XML `#{...}` 保留原文。XML 以 `jsh_depot_head` 为主，连接明细/物料作可选筛选，用 `statusArray` 的 `foreach` 形成条件 `IN`，可能按 `creatorArray` 约束创建人，过滤删除标记并连接返回字段。`purchaseStatusArray` 因本例未传值不作本次初始请求的状态条件；编号/商品空值也不激活对应 `<if>`。动态 XML 的旧 `SQL_ANALYSIS` 为 `PARTIAL`，不能展示为唯一最终 SQL；拦截器的租户/分页改写仍待运行时证据。[Mapper声明][mapper18]、[完整XML][xml66]、[旧SQL分析][sql1309]、[租户配置][tenant26]
>
> **范围与覆盖**：旧后端包为 `COLLECTED_WITH_LIMITATIONS`，有未解析调用；目标包应分别报告入口、所有相关页面 use、未匹配请求、无页面入口、未检查文件和容量未选单元。`findBillDetailByNumber`、`/depotItem/getDetailList` 及选择后回填是同组件的其它动作，不合并进这条主列表 SQL。任何短 ref 只在本 packet/publication 内有效。[旧覆盖][coverage541]、[详情请求][link347]

最终交付目标是**可按入口与版本重新打开、附来源和局限的技术材料包**，不是已经判定业务语义或操作步骤的业务文档。旧 Step06/07 与旧状态恢复未适配 v2 时，应拒绝该版本，不得暗中降级或丢弃前端材料。

上述样张的消费链是：R3 `technicalOutput` → 具名技术 artifact/新版 reader → 完整只读 Packet → 技术 Markdown 或人工审阅。当前 Step06/07 的模型输入没有经过这一 v2 reader 适配验收；新材料不能借旧业务入口直通模型。跨运行顺序和替代旧线的清理条件以[依次清理设计 C0–C9](../plans/technical-analysis-cli-and-vue-cleanup-design.md)为准：先核Maven装配/模块隔离、JDT平台/诊断协议和Vue有限关联三项工具风险，再冻结来源/owner，接Step02依赖准备与前端、同会话Step03、独立Step04/05，最后在新读写链与历史读取均通过后核实消费者并删旧生产连接。该顺序是**待实施设计**，不表示任何新命令已验收。

## 未完成与未证明

- 三个拟议命令 `collect-code`（Step02+03 同 JDT 会话）、`analyze-persistence`（Step04）、`assemble-materials`（Step05）及新的 Step02 前端索引/Step05 v2 尚未实现；旧运行的行和 ID 不代表新命令结果。其运行与来源归属见[技术命令合同](../modules/technical-analysis/cli-and-runtime.md)。
- 本例没有抓包或执行证据。`MATCHED_UNIQUE` 只限冻结默认配置及可静态判定的 route/method；运行时 `_CONFIG`、网关、拦截器最终 SQL、用户身份/权限实际值、数据库返回行和保存/审核结果未证明。
- 同一共享组件还有其它请求：点击列表编号会调用 `findBillDetailByNumber`[对话框详情][link267]；选中后 `loadDetailData` 用所选 `headerId`、`mpList=''`、`linkType=this.showType` 请求 `GET /depotItem/getDetailList`[明细请求][link347]。选明细后 `@ok` 才回填采购订单的 `linkApply` 和 `linkId`[确定回调][link295]、[订单回填][order424]。这些分支未逐个走到 SQL，不得借本例的主列表 SQL 代表它们。
- [PurchaseInModal.vue:459–461][in459] 使用 `show('其它','采购订单','供应商','1,3')`，令共享组件 `showType='basic'`[共享组件 show][link222]；它与采购订单的 `purchaseShow('其它','请购单','客户','1,3')` 不是同一个前端使用上下文。需以此、多候选后端、动态 URL、不同 `this.url` 和排除来源验收不误连。
- 旧包中的方法/调用成员与 Mapper/XML 是静态候选和完整阅读材料，不证明每条边在某次点击都执行；`PARTIAL` SQL 不是唯一实际 SQL。Step04 没有精准 statement 行范围，不能将本页人工 XML 行号伪装为机读证据。未核对全部前端请求分母与全部业务链；本例不能宣称全仓 Vue→SQL 完整覆盖。
- 固定快照的 Maven 文件只证明这个后端模块及其外部父 POM 声明，不给出已解析依赖闭包，也不证明通用多模块投影能力。Step02 的诊断完成协议、前端 helper 的真实片段原型及 `MATCHED_UNIQUE` 机读结果都未验收。
- 这批旧 JDT 索引在其它具名位置已有误关联发现，涉及依赖不足；本页只人工核对所列主线，**没有验证这 282 个方法、713 条调用全部正确**。补齐依赖后的既有 Step03 复验是后续验收，本页未重跑，也不能把旧索引称作已洗净。

[manifestpom]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:9
[pom13]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/f7405eb7891ac979d7e94c663c1dba72b2467df352df0b4d1457e2de67f3e6c5:13
[pom201]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/f7405eb7891ac979d7e94c663c1dba72b2467df352df0b4d1457e2de67f3e6c5:201
[string350]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/6a2fe6d6d9ec36795023c86725927c1643cd6c106ecc797207968d2e293987da:350
[verifiedclasspath]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/code/VerifiedJavaProject.java:152
[runner128]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunExecutor.java:128
[xml121]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/073e626cebf9e3e9a8c4d958ed05eaf9ae39396c5e6b486b18b4ac49fba0af73:121
[list218]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/768a4fe766234f3a351d43c22991445f972ed106cf9be623dd144e8a1699b14c:218
[order57]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e270a1c1681f889dcad6ba5e649786b0eacf0a20fd76501d72c80a7759abd87b:57
[order181]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e270a1c1681f889dcad6ba5e649786b0eacf0a20fd76501d72c80a7759abd87b:181
[order420]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e270a1c1681f889dcad6ba5e649786b0eacf0a20fd76501d72c80a7759abd87b:420
[order424]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e270a1c1681f889dcad6ba5e649786b0eacf0a20fd76501d72c80a7759abd87b:424
[in459]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/68ced273e9d6dd24dcb9bd6150de277aba3f59c88399fcffcc295f1d01a5f5fb:459
[link148]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:148
[link209]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:209
[link222]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:222
[link234]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:234
[link247]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:247
[link267]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:267
[link295]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:295
[link347]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d74bc8a15fdb1ebd160b803401d571b7d4fb3db6af2a9ae8b78e8e03d9cfa149:347
[mixin32]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/eaec79c236ce05e3ddd314a54f0a765a4fcf0e8778dcde9508bd4afd37405398:32
[mixin73]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/eaec79c236ce05e3ddd314a54f0a765a4fcf0e8778dcde9508bd4afd37405398:73
[mixin90]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/eaec79c236ce05e3ddd314a54f0a765a4fcf0e8778dcde9508bd4afd37405398:90
[filter33]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/f36630460eb126146d190ce9d4c5301fdd297bf23288ae33b48ec9d7339e48b4:33
[manage43]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/2959df2299291e6dbba8555e4be74e79e09ac4fa467b67b45a53488c116ab05f:43
[request14]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/d8f23808bc9c43e52ccd39e8e54cb07a9e1d48ac4d7f4aa9933b86ff46651ee8:14
[html258]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/9e2be8820cdcd47b653f839fc1e03fc9ee750f8f425ef56c63eb0e37248fd304:258
[vueconfig50]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/4491e9b6cbc82d2bb3ab30c4c2963c8db4180973928a14d5a0165236fd702b8a:50
[app9]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/ccc029fe6b7440c43466e918a9608f1180f279ac553843efbf7ced4f1ef857f5:9
[controller77]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/1b45a173d5bd1a56888541da558db65c26c83e9758ed900bbada249dfd0bc55b:77
[service108]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e15d22b6aa8282b3af2719e1faa827cf8ccd5f65346b440f19f83f40e5e4fa49:108
[service131]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e15d22b6aa8282b3af2719e1faa827cf8ccd5f65346b440f19f83f40e5e4fa49:131
[service233]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e15d22b6aa8282b3af2719e1faa827cf8ccd5f65346b440f19f83f40e5e4fa49:233
[mapper18]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/709460a4dd062a3b7c5d9d64fb9facbf1a4f11a74190fc1b12e71d76428de0f5:18
[xml66]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/073e626cebf9e3e9a8c4d958ed05eaf9ae39396c5e6b486b18b4ac49fba0af73:66
[xml80]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/073e626cebf9e3e9a8c4d958ed05eaf9ae39396c5e6b486b18b4ac49fba0af73:80
[xml98]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/073e626cebf9e3e9a8c4d958ed05eaf9ae39396c5e6b486b18b4ac49fba0af73:98
[xml173]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/073e626cebf9e3e9a8c4d958ed05eaf9ae39396c5e6b486b18b4ac49fba0af73:173
[tenant26]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/e6f764cd4520ba712ea1ce1c9b7b719164c1ff2af0e1033861349c8ffcd53043:26
[entry215]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/02-application-discovery/entry-points.jsonl:215
[mappercatalog]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/02-application-discovery/mapper-catalog.jsonl
[membership61448]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/03-program-graphs/java-code-index.jsonl:61448
[call32543]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/03-program-graphs/java-code-index.jsonl:32543
[call47892]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/03-program-graphs/java-code-index.jsonl:47892
[binding745]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/04-proven-code-facts/persistence-material-index.jsonl:745
[statement164]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/04-proven-code-facts/persistence-material-index.jsonl:164
[sql1309]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/04-proven-code-facts/persistence-material-index.jsonl:1309
[packet215]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/05-business-flows/code-reading-materials.jsonl:215
[coverage541]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/05-business-flows/code-reading-materials.jsonl:541
