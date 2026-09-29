# 冻结 jshERP：采购订单关联请购单的 Vue 到 SQL 走读

## 先看结论和来源

本例是采购订单弹窗**打开关联请购单选择列表**的查询：`PurchaseOrderModal.vue → LinkBillList.vue → JeecgListMixin.js → getAction → axios GET /jshERP-boot/depotHead/list → DepotHeadController.getList → DepotHeadService.select → DepotHeadMapperEx.selectByConditionDepotHead → XML <select>`。查询结果经 Controller 的 `getDataTable` 返回，再由 mixin 填入列表 `dataSource` 和分页总数[Controller:99–101][controller77]、[列表 mixin:99–105][mixin90]。页面文案提出“提交之后原来的请购单会对应的改变单据状态”[采购订单控件][order57]，但这条查询不包含采购订单提交、请购单状态改变或采购入库；采购入库的“关联订单”使用同一共享对话框的另一个 `show(...)` 分支[采购入库入口][in459]。

本页以 [manifest:592](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:592)、[manifest:606](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:606) 的固定 Git 捕获为原文。下述逐边业务场景解释仍是**人工核对**，不是程序生成的业务结论。旧同运行 `analysis-run:5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac` 保存后端入口、JDT、持久化和 v1 后端包。此后另有**历史已生成**的同字节 DIRECTORY 准备版 R0 `analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`，旧技术 R1 `analysis-run:a4f749d142e233f0bf626821598d29822452f868da74674297022c4f32d19c26`（前端+后端）、R2 `analysis-run:549157410980d1837094def5abd5d398b7d3c83a44cd0206d2fda4784e74e5da`（SQL）及 R3 `analysis-run:4291ab98b5119c0853f61f4ac30f2d36c164b3238a017de8a1d6ad3fd3197359`（v2 包）。它们不重命名、不改写，也不等于下面拟议新 R1–R4 的产物。本次仅设计，未运行客户工具/测试/模型。

| 步骤 | 输入 | 处理与输出 | 实际下游／成熟度 |
| --- | --- | --- | --- |
| R0 `prepare-source` | 固定源码 | 保存来源、原文和生效排除，不赋业务含义 | 已历史生成；四个新技术运行都绑定同一准确 R0。 |
| 新 R1 `collect-frontend` | R0、静态前端配置 | 独立保存页面实例、请求、参数和完整前端源码单元；无 entryId | R4 匹配器消费；无需后端入口、Maven、JDT。 |
| 新 R2 `collect-code` | R0、官方 Maven 交接 | Step02 后端入口/Mapper + Step03 JDT 完整方法与物理调用 | R3 消费，R4 沿准确 R3 重开。 |
| 新 R3 `analyze-persistence --code-run R2` | 同 R0、准确 R2、XML | 保存 Mapper/动态 SQL 候选，零 JDT | R4 消费。 |
| 新 R4 `assemble-materials --frontend-run R1 --persistence-run R3` | 同 R0 的 R1/R3、R3 准确 R2 | 此时匹配请求到入口；每 entryId 一份完整 JSON，并保存全仓未匹配请求覆盖 | 技术查询/Markdown。没有业务 LLM；新命令尚未实施。 |

## Step01：原文来源，不赋业务含义

输入是固定 jshERP 源码；旧运行的 [source-inventory.jsonl:606](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/01-verified-source-inventory/source-inventory.jsonl:606) 确认 `PurchaseOrderModal.vue` 是可分析 UTF-8，SHA-256 为 `e270a1c1…87b`。Java、XML、共享前端原文同样冻结。新 `prepare-source` 的 R0 已历史生成，其 DIRECTORY sourceVersion 与原 Git 捕获的 719 文件逐一核对字节相同，但仍是不同来源身份；四个新技术命令均重开准确 R0 和排除，不从旧同运行自动冒充准备版。[Step01 合同](../analysis-steps/01-verified-source-inventory.md)

## 前端 R1：页面动作形成请求；R4 才关联入口

新 `collect-frontend` 只读 R0 有效 Vue/JS 和具名静态配置，使用自有 `vue-eslint-parser` helper；不用后端 entry list、JDT、Maven。既有 Maven classpath/effective POM 已交接并用于**历史**旧技术 R1 的后端收集；新 `collect-code` R2 才单独消费这类同源文件，仍须核对目标 JDK/模块和 JDT 绑定。此页不授权再次运行 Maven。

新前端 R1 可先完成，无后端入口是正常状态，索引只有请求事实；不写 `BACKEND_DISCOVERY_NOT_RUN` 或虚构 entryId。新 R2 核对 Maven 交接、建立 `javaAnalysisBasis`，Step02/03 共用准确 JDT 会话；已知路径或绑定错误阻断，诊断覆盖未知须披露。R4 才通过具名地址依据及 R2 的完整 HTTP method/route/条件生成唯一、多候选、无匹配或未解析状态。[前端合同](../modules/technical-analysis/frontend-http-discovery.md) · [Java 就绪](../modules/technical-analysis/java-readiness.md)

旧 Step02 的 profile、entry、mapper、capability 是历史 Java 发现材料；旧前端 module 6 随旧 R1 保存。新前端 R1 `frontend-http-index-v2` 没有 `ENTRY_LINK`，新 R2 仍负责后端 entry；新 R4 才保存匹配与材料。新配置/request/output 目标为 v3/v5/v9；旧 finished run 按旧版本重开，不改写。

固定快照的 [`jshERP-boot/pom.xml`][manifestpom] 声明外部 Spring Boot 父 POM 2.0.0.RELEASE、Java 1.8[父 POM][pom13]、[编译声明][pom201]；POM 本身不等于依赖闭包。历史旧技术 R1 已消费另行取得的同源 classpath/effective POM 并达到环境 READY，保存 `diagnosticCoverage=UNCONFIRMED`；339 个入口中 332 个收集、7 个查询失败，部分调用候选仍有错归。READY 不代表全仓 JDT 边准确，新 R2 未运行。

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

旧同运行 [entry-points.jsonl:215][entry215] 保存 `GET /depotHead/list`、`DepotHeadController#getList`、methodKey 和 77–102 行；没有前端信息。**历史已生成**的旧技术 R1 module 6 则有 307 `FILE`、51 `HTTP_REQUEST`、51 `ENTRY_LINK`、68 `SOURCE_UNIT`。本例物理请求键 `jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue:20386` 保留四个 `PASSED` 绑定和第五个 `purchaseStatus: NOT_PASSED`，旧链接按 path/method 标 `MATCHED_UNIQUE` 至 `entry:50b7073a3ca2bce8612df51fdaa2d22cb22e33c7d5b1bf28c23c532e661c65a7`；旧 R3 的该请求覆盖为 `SELECTED`。这不是新 R4 的具名部署地址验收，也不证明浏览器实际派发。独立前端新 R1 将保留请求而无 `ENTRY_LINK`；R4 才生成新的匹配。[前端目标](../modules/technical-analysis/frontend-http-discovery.md)

## Step03：后端 Controller 与 Service 的实参

后端输入是 Step02 的精确 Java 入口和同一次 JDT 会话的 catalog。旧同运行 [java-code-index.jsonl 入口成员][membership61448] 对 `/depotHead/list` 标 `COLLECTED`，保存 282 方法、713 调用，其中包含本次点击未必执行的分支。新 R2 `collect-code` 仍以一套 JDT 环境执行 Step02/03，保存完整方法正文、物理位点、实参/形参和目标候选；新前端 R1 不导航 Java。旧数量不证明每条边准确或一次点击全部执行。

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

新 R3 `analyze-persistence --code-run R2` 精确重开 R2 Step02/03、同 R0 冻结 XML 与可选 MyBatis 配置；它不启动 JDT/Node，不执行 SQL。保存 Mapper 绑定候选、完整 XML Resource、statement、SQL 分析状态和限制，供 R4 使用。旧同运行 [Step04 JavaBinding:745][binding745] 对 `DepotHeadMapperEx.selectByConditionDepotHead` 给出 `EXACT_NAMESPACE_AND_METHOD_ID` **候选**，关联 [Statement:164][statement164]；接口 `@Param` 原文见 [MapperEx.java][mapper18]，不等于运行时参数绑定。历史旧技术 R2 也已另行保存 Step04 v2；新 R3 未运行。

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

## R4 组装：历史包与新每入口证据

新 `assemble-materials --frontend-run R1 --persistence-run R3` 先核同一 R0、有效排除和 R3→R2 的准确 lineage；此时用 R1 请求及 R2 后端入口作匹配，再选择完整 Vue/Java/XML 单元，零 parser/JDT/业务 LLM。R4 按每个真实 entryId 发布一个完整 `entry-<entryIdhex>.json`，外加 `entry-evidence-index.json` 与保存全部请求（包括无匹配）的 `frontend-coverage.jsonl`，不能把 12 个入口塞入一个共用 packet 才可读。旧同运行 [PACKET:215][packet215] 是 **v1 后端包**：本入口 282 方法、713 调用、17 XML Resource、19 statement/SQL 引用、299 packet 内 SourceRef、1,060,223 UTF-8 字节，未选单元 0；旧 [ENTRY_COVERAGE:541][coverage541] 为 `COLLECTED_WITH_LIMITATIONS`。这些数字属于旧运行，不是新 R4 预算。

旧 v1 JSONL 存 `methodKeys/callKeys/persistence/sourceReferences/unselectedUnits/limitations` 等引用，完整 Method 与 Resource 在旧 Step03/04；短 ref 只能连同所属 publication/packet 解释。历史旧技术 R3 则已保存并重开 Step05 v2，前端索引沿旧 R1 `ENTRY_LINK` 选单元。本例旧 R3 把上述物理请求标为 `SELECTED`，但共享 mixin 的 `getQueryParams` 完整函数 130–144 行未被作为独立 SOURCE_UNIT 选入；修复后的新 R1 已补足，R4恢复尚待真实验收。新 R4 的容量超限是运行失败，不沿用旧包的逐单元削减策略。[Step05 合同](../analysis-steps/05-business-flows.md)

R4 对每个后端入口各写一个自含 JSON；未匹配请求留在独立全仓覆盖，不能塞进某个 `entryId`。唯一匹配的页面实例进确定 `requestUses`；多候选分别出现在候选入口的 `candidateRequestUses`，附全候选 ID，不能当确定调用者或推导该入口 SQL。每入口 JSON 保留完整已收集前端/Java/Mapper/XML、来源和上游限制；无页面或未收集代码也有明确空范围，正式 `DISABLED` 前端 R1 则标“前端未分析”，不能当作省略上游。索引按 entryId 指向平铺文件，字节数/摘要只由 canonical receipt descriptor 核验；任一入口或整体容量超限，R4 失败且不发布成功集合，不截断、不写伪完整 stub。旧 Step06/07 未适配时明确拒绝新版本。

### 目标 reader 打开后的完整可读示意

以下是**手工编排的新 R4 技术阅读样张**，不是已保存的新单入口 JSON 或运行结果。正式产物须附来源，并能按 R4 publication 重开前端/Java/XML 全文；这里的简述不是实际 reader 的截断规则。

> **入口与用途**：`GET /depotHead/list` 对应 `DepotHeadController#getList(search, request)`；本页使用点是 `PurchaseOrderModal` 的“关联请购单”搜索框。该用途是查询可供选择的单据，不能据此推断采购订单已建立或请购单状态已变。[弹窗控件][order57]、[后端入口][entry215]
>
> **页面触发与参数**：`onSearchLinkApply` 把 `('其它','请购单','客户','1,3')` 送入共享 `purchaseShow`。它设 `showType='purchase'`、更新 `queryParam` 并 `loadData(1)`；新实例缺第五个 `purchaseStatus`，序列化时该字段不出现。`organType` 只改列标题。默认 `number`、`materialParam` 是空字符串；之后用户输入可改变它们。[页面调用][order420]、[共享组件][link234]、[默认查询][link148]
>
> **请求封装与定位依据**：`JeecgListMixin.getQueryParams` 将筛选对象放入 JSON 字符串 `search`，附 `currentPage=1/pageSize=10/column=createTime/order=desc`；`getAction` 交给 axios GET。`this.url.list='/depotHead/list'`，冻结 `index.html` 设 `domianURL='/jshERP-boot'`，后端 context path 同值，故仅在该默认配置下静态对齐此入口。运行时覆盖、网关及页面复用状态未知；正式 R4 匹配应记录条件和所有候选，R1 不保存入口链接。[共享 URL][link209]、[列表 mixin][mixin90]、[GET 封装][manage43]、[默认配置][html258]
>
> **Java 接收、处理与返回**：Controller 从 `search` 取18个筛选实参，调用 Service。`getInfo` 将缺失/空值变 `null`；Service 生成 `statusArray=['1','3']` 的静态推导，按当前用户/角色生成可能的 `creatorArray`，对本“请购单”子类不生成仓库数组或销售类客户数组，分页后调用21参的 Mapper 方法。Mapper 返回后 Service 还可能读取押金、金额、商品摘要并按 `priceLimit` 调整部分金额；Controller 返回 `rows/total`，mixin 写入列表。具体用户权限、返回行和后续读取是否发生没有执行证据。[Controller][controller77]、[Service][service108]、[Service 后处理][service131]、[列表消费][mixin90]
>
> **Mapper/XML/SQL**：`DepotHeadMapperEx.selectByConditionDepotHead` 有对应 namespace/id 候选，`@Param` 和 XML `#{...}` 保留原文。XML 以 `jsh_depot_head` 为主，连接明细/物料作可选筛选，用 `statusArray` 的 `foreach` 形成条件 `IN`，可能按 `creatorArray` 约束创建人，过滤删除标记并连接返回字段。`purchaseStatusArray` 因本例未传值不作本次初始请求的状态条件；编号/商品空值也不激活对应 `<if>`。动态 XML 的旧 `SQL_ANALYSIS` 为 `PARTIAL`，不能展示为唯一最终 SQL；拦截器的租户/分页改写仍待运行时证据。[Mapper声明][mapper18]、[完整XML][xml66]、[旧SQL分析][sql1309]、[租户配置][tenant26]
>
> **范围与覆盖**：旧后端包为 `COLLECTED_WITH_LIMITATIONS`，有未解析调用；目标集合分别报告入口、确定/候选页面 use、未匹配请求、无页面入口和未检查文件。容量超限阻断正式 R4 集合，不产出残缺入口 JSON。`findBillDetailByNumber`、`/depotItem/getDetailList` 及选择后回填是同组件其它动作，不合并进这条主列表 SQL。历史短 ref 只在原 packet/publication 内有效。[旧覆盖][coverage541]、[详情请求][link347]

新交付目标是**每个 entryId 一份可独立重开的完整技术 JSON**，另有入口索引和全仓前端请求覆盖；不是业务操作步骤文档。旧 Step06/07 与状态恢复未适配新格式时应拒绝，不得暗中丢字段。

目标消费链是：新 R4 `technicalOutput` → 具名入口索引/单入口 JSON/全仓请求覆盖 → 严格 reader → 技术 Markdown 或人工审阅。历史旧 R3 v2 Markdown 已导出，不能因其可读就宣称新 R4 或 Step06/07 已适配。技术材料只组织静态候选和限制；跨运行 owner 与 CLI 总合同以[技术运行设计](../modules/technical-analysis/cli-and-runtime.md)为准。

## 未完成与未证明

- 四个独立命令、`frontend-http-index-v2`、R4 matcher 和单入口 JSON 已在同源离线fixture中接通，但固定源码验收未完成。第一次真实新R1虽然保存了请求里的 `getQueryParams` 源码单元引用，运行器却漏传辅助单元，使正式`SOURCE_UNIT`缺少完整函数；须修复后新建R1并重开核对。第一次真实新R2在339入口导航后发布失败，没有可供R3/R4使用的Java索引。历史旧R1/R2/R3的行与ID不能代替这次新结果；R4仍须验证全仓未匹配请求被保留。
- 本例没有抓包或应用执行证据。历史 `MATCHED_UNIQUE` 只核 route/method；运行时 `_CONFIG`、网关、最终 SQL、用户权限、返回行和提交/状态变化都未证明。
- 同一共享组件还有其它请求：点击列表编号会调用 `findBillDetailByNumber`[对话框详情][link267]；选中后 `loadDetailData` 用所选 `headerId`、`mpList=''`、`linkType=this.showType` 请求 `GET /depotItem/getDetailList`[明细请求][link347]。选明细后 `@ok` 才回填采购订单的 `linkApply` 和 `linkId`[确定回调][link295]、[订单回填][order424]。这些分支未逐个走到 SQL，不得借本例的主列表 SQL 代表它们。
- [PurchaseInModal.vue:459–461][in459] 使用 `show('其它','采购订单','供应商','1,3')`，令共享组件 `showType='basic'`[共享组件 show][link222]；它与采购订单的 `purchaseShow('其它','请购单','客户','1,3')` 不是同一个前端使用上下文。需以此、多候选后端、动态 URL、不同 `this.url` 和排除来源验收不误连。
- 旧包中的方法/调用成员与 Mapper/XML 是静态候选和完整阅读材料，不证明每条边在某次点击都执行；`PARTIAL` SQL 不是唯一实际 SQL。Step04 没有精准 statement 行范围，不能将本页人工 XML 行号伪装为机读证据。未核对全部前端请求分母与全部业务链；本例不能宣称全仓 Vue→SQL 完整覆盖。
- 历史旧技术 R1 已用同源 classpath/effective POM 完成环境准备，仍披露 `diagnosticCoverage=UNCONFIRMED`，且 7 个入口查询失败、调用候选有错归；这不证明新 R2 的 JDT 准确性。有限 Vue2 旧索引不等于独立前端新 R1。
- 这批旧 JDT 索引在其它具名位置已有误关联发现，涉及依赖不足；本页只人工核对所列主线，**没有验证这 282 个方法、713 条调用全部正确**。补齐依赖后的既有 Step03 复验是后续验收，本页未重跑，也不能把旧索引称作已洗净。

[manifestpom]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl:9
[pom13]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/f7405eb7891ac979d7e94c663c1dba72b2467df352df0b4d1457e2de67f3e6c5:13
[pom201]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/f7405eb7891ac979d7e94c663c1dba72b2467df352df0b4d1457e2de67f3e6c5:201
[string350]: /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/6a2fe6d6d9ec36795023c86725927c1643cd6c106ecc797207968d2e293987da:350
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
