# 当前证据的端到端人工走查

状态：2026-09-30 **人工只读核对与设计推导，不是Ontology模型输出**。未运行JDT、Activity、过程或本体生成。以下结论只用于检查材料是否足够支撑设计、以及PoC应该核对什么。

## 1. 固定输入及读取位置

- 源码准备R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。
- 固定提交：`8c30ce7861570458920175e200bb2a6442713580`。
- R4：`analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`。
- [实际入口目录](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-evidence-index.json)，sourceBasis在`header.sourceBasis`，不是根字段；snapshot为`snapshot:528e8d2811372396dab1954c02855c9cad0d958f4a16de5e12456de374cdd60f`。
- [同版本固定源码副本](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7)只用于本页人工定位；生产consumer须经R0 reader读取，不能直接把副本路径当来源许可。

本页本地链接便于复核，不是目标产物的绝对路径字段。正式sourceBindings保存源码相对路径和真实来源；目录搬移不改变内容身份。

### 1.1 实际规模与模型材料规模不同

| 人工盘点项目 | 保存结果 |
| --- | ---: |
| 入口文件 | 339 |
| 入口JSON总字节 | 153,083,646 |
| 方法出现次数 / 唯一methodKey | 6,695 / 1,788 |
| SQL statement出现次数 / 唯一statementRef | 650 / 260 |
| 调用出现次数 / 唯一callKey | 23,094 / 10,527 |
| 最大单份入口JSON | 3,530,376字节 |
| 最大方法正文 | 26,585 UTF-8字节 |

去重减少重复传输，但不删除每入口/页面用法。最大方法为`DepotItemService.saveDetials`，380–738行；它是长方法，不是26.6 KB完整业务模型。去重后的资料也不能一次全部送小上下文。

目录有6份ASSEMBLED、333份ASSEMBLED_WITH_LIMITATIONS；后者不是333份错误或不能使用，必须看具体限制。人工盘点不能替代逐调用准确性证明。

### 1.2 本次选择的真实入口

| 调查用途 | 完整entryId十六进制部分 / 保存文件 |
| --- | --- |
| 库存业务单据建单 | [52f92156…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-52f92156ebd5637227b8ed73282258ce9e2864167951baf8cb1bb2658ac2ae81.json)；`/depotHead/addDepotHeadAndDetail` |
| 审核/反审核 | [572a7d0a…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-572a7d0add93cece17cac58b6c67aa4578c4ee78a04aa0dec93b7fafe583804f.json)；`/depotHead/batchSetStatus` |
| 期间采购/销售统计 | [f3ab9282…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-f3ab9282894b23327a05e4c15431b2e1ab2a5d5812dc0a0bfb58e39a1e7f626f.json)；`/depotHead/getBuyAndSaleStatistics` |
| 财务建单 | [a59528c6…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-a59528c6d7a0b5bf89710f741d2bbd4d4f91e94b6410390ee611d32d56e53a81.json)；`/accountHead/addAccountHeadAndDetail` |
| 关联财务单据查询 | [de0f1f05…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-de0f1f05837c4a75d39ca9e8413cb6db15744c8509f2829543778f3adcd6ecca.json)；`/accountHead/getFinancialBillNoByBillId` |
| 选择来源单据 | [50b7073a…完整JSON](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe/steps/05-business-flows/entry-50b7073a3ca2bce8612df51fdaa2d22cb22e33c7d5b1bf28c23c532e661c65a7.json)；`/depotHead/list` |

以上完整ID用于程序定位；模型包中可映射E1等短键，不要求模型复写哈希。

## 2. DDL能提供什么，已经验证到哪里

同R0中有`jshERP-boot/docs/jsh_erp.sql`，91,947字节、1,063行；[准备清单第9条](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/stores/runs/analysis-run--73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7/steps/01-verified-source-inventory/source-inventory.jsonl:9)记录为VERIFIED_TEXT，SHA-256为`0a5fdc395b5a7b41d4729aa2a81d7e5d3948dfc83291283d58b4b48ab1e326f9`，固定副本核对相同。它没有装入本页所走查R4入口的sourceRefs，因此是**同源补读**，不是“现有入口已经包含DDL”。

[单据头DDL](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/docs/jsh_erp.sql:133)133–176行有：

```sql
`id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
`number` varchar(50) ... COMMENT '票据号',
`discount_last_money` decimal(24, 6) ... COMMENT '优惠后金额',
`deposit` decimal(24, 6) ... COMMENT '订金',
`last_debt` decimal(24, 6) ... COMMENT '最终欠款',
`link_number` varchar(50) ... COMMENT '关联订单号',
`link_apply` varchar(50) ... COMMENT '关联请购单',
PRIMARY KEY (`id`) USING BTREE,
INDEX `number`(`number`) USING BTREE
```

这是**省略其它列的实际片段**，省略号仅在本文展示，不是解析输入。单据明细182–215行有id主键、header_id、material_extend_id、oper_number、basic_number、link_id。形如`INDEX FK2A...`仍是索引，不是FOREIGN KEY声明。

本页做了一个只读成熟parser试验：

| 输入 | 工具 / 操作 | 实际结果 |
| --- | --- | --- |
| 整个SQL导出 | 已安装JSqlParser5.3，parseStatements；不连接数据库 | 在236行UNIQUE处解析失败 |
| 明确选择的133–215行完整定义范围 | 同一parser，含两份CREATE TABLE与中间DROP | 3条statement，2张表；head35列/7索引，item25列/7索引 |

诊断驱动位于`/private/tmp/ontology-ddl-read-probe.6GbQSZ/ReadDdlProbe.java`，只读解析，不安装依赖、不执行SQL。这证明**选定两张表能解析**，没有证明自动枚举全文件DDL已经实现或所有方言支持。

DDL支持“表主键id”和存储列解释；不证明所有业务关系、租户范围唯一性、真实业务行或完整状态机。没有DDL也可依代码/SQL提出对象，唯独DDL声明的约束不能补出来。

## 3. 对象、业务变体和采购关系怎样推导

### 3.1 页面说明不能单独代替后端规则

同R0额外读取：

- [PurchaseOrderModal.vue](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue:420)420–465：选择请购单用`purchaseShow('其它','请购单','客户',"1,3")`；选中明细保存`info.linkId = info.id`；来源subType为请购单时把单号填入linkApply，否则填linkNumber。
- [PurchaseInModal.vue](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-web/src/views/bill/modules/PurchaseInModal.vue:460)460–515：选择采购订单用`show('其它','采购订单','供应商',"1,3")`；保存linkId、linkNumber；已完成部分数量时用preNumber-finishNumber；优惠后金额减订金形成页面changeAmount初值。
- BusinessConstants.java71–74、85–88：审核状态常量0/1，办理状态2/3，请购单/采购订单/采购等subType字面值。

建单/审核/统计入口JSON的前端确定/候选数组均为空，不能说这些页面已被R4自动连到它们。列表入口有12条前端候选，但baseURL未知，未成为确定部署关联。**本次人审读到上述页面不等于自动选材已经做到。**

这也说明必须支持同R0字面搜索与完整原文补读：R4入口闭包不是所有业务材料的封闭边界。

### 3.2 后端真实写入与回写

建单入口已保存DepotHeadService1204–1312和DepotItemService380–738。核对得到：

| 实际实现 | 可支持的候选 | 不能推出 |
| --- | --- | --- |
| DepotHeadService1225–1229拒绝linkNumber与linkApply同时非空 | 本次建单的两种来源关联互斥规则 | 所有单据必须有来源，或所有历史数据都遵守 |
| 1240–1244：status为空才默认0；purchaseStatus赋0 | 两个字段的不同初始化行为 | “新增一律status=0”；传入非空status的行为不能省略 |
| 1281插头；1295–1302按number查id，传给saveDetials | 单据头与明细的标识交接 | 单号数据库唯一约束或全租户唯一 |
| DepotItemService保存headerId/linkId，XML batchInsert写header_id/link_id | 明细所属头及来源明细的映射 | 仅见字段名便宣布真实外键 |
| 694–708：指定subType且linkNumber非空才计算/回写原单状态 | 业务来源关联及分批推进候选 | 全部类型都回写，或每次都全部完成 |
| 709–732：采购订单linkNumber改变原销售订单purchaseStatus；linkApply改变请购status | 同公共实现的不同业务关系/字段用途 | 把两个状态和两个关联字段合成一种万能“流程状态” |

相关补读：DepotItemService747–792的getBillStatusByParam及DepotItemMapperEx.xml中的getBatchBillDetailMaterialSum，部分未在建单入口展开，须同R0补读；不能把源码可读误称R4已经包含。

候选对象至少可以是“业务单据头/明细”及其“请购单、采购订单、采购入库”角色或业务变体。首版无需预先规定必须建三个独立Object还是一个带变体的Object：模型比较业务身份与操作后提出方案，保存实际变体条件。两种方案都须能准确表达关系，不能仅复制表名。

可核对的关系机制：

```text
采购订单.linkApply → 来源请购单.number
采购入库.linkNumber → 来源采购订单.number
后续明细.linkId → 来源明细.id
业务明细.headerId → 本次单据头.id
```

这是**人工根据上述读写推导出的关系候选**，不是本轮已有自动本体输出。关系存在/支持分批不等于强制顺序；同源实现还支持其它用途，不得泛化成每笔采购都须先请购。

### 3.3 对象身份还缺什么

DDL明确id为头/明细各自主键；number是普通索引。代码还检查未删除单据号重复。可以提出“id为存储身份；number为程序业务查询键”，不能宣布number全局唯一。

tenant_id虽然存在，但本页未读完全部租户隔离机制；基数、跨租户唯一性和外部数据映射保留未知。字段注释仅为辅助，不覆盖实际代码分支。

## 4. 重点：跨Controller的业务联系

这不是让Controller直接互调，而是从**同一个业务标识如何被另一类对象使用**建立联系。

### 4.1 三个不同入口观察同一标识

| 入口/资料 | 实际观察 |
| --- | --- |
| DepotHeadController建单 → DepotHeadService | 保存业务单据头及明细；头id是后续更新键 |
| AccountHeadController建单 → AccountHeadService / AccountItemService | 财务明细可携带billNumber；用此号取得DepotHead.id，保存为billId |
| AccountHeadController财务关联查询 | 用billId查询关联财务单据bill_no |

财务建单R4确实保存AccountHeadService298–346、AccountItemService111–169、DepotHeadService1573–1586。下面关键代码来自[AccountItemService](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/java/com/jsh/erp/service/AccountItemService.java:128)：

```java
if (tempInsertedJson.get("billNumber") != null && !tempInsertedJson.get("billNumber").equals("")) {
    String billNo = tempInsertedJson.getString("billNumber");
    accountItem.setBillId(depotHeadService.getDepotHead(billNo).getId());
}
```

150–160行随后插财务明细；[AccountItemService79–87](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/java/com/jsh/erp/service/AccountItemService.java:79)实际调用accountItemMapper.insertSelective。该statement在财务建单R4中保存；[AccountItemMapper.xml119–135](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/resources/mapper_xml/AccountItemMapper.xml:119)在billId非空时加入bill_id列，[169–170](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/resources/mapper_xml/AccountItemMapper.xml:169)写入对应billId占位值。此statement的SQL分析为UNSUPPORTED，写入映射来自完整XML，而非执行SQL。

billId非空且能找到业务单据、欠款非零时调用updateLastDebtByBillId。这个读取/调用在正文中可见，但财务建单R4并未展开所有被调用正文；下面欠款计算是本次**同R0补读**：

[DepotHeadService1447–1455](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:1447)：

```java
BigDecimal financialBillPrice = accountHeadService.getFinancialBillPriceByBillId(billId);
if(debt!=null && financialBillPrice!=null) {
    DepotHead dh = new DepotHead();
    dh.setId(billId);
    dh.setLastDebt(debt.subtract(financialBillPrice));
    depotHeadMapper.updateByPrimaryKeySelective(dh);
}
```

AccountHeadService419–439调用按billId分组的财务金额查询，并取该billId汇总值的绝对值；[AccountHeadMapperEx.xml136–147](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml:136)是`sum(ai.each_amount)`，按bill_id分组、过滤已删除头/明细。不是“每个金额取绝对值再相加”；本条查询也没有显式付款类型过滤，不能私自添加。

财务关联查询R4已保存Controller175–196、Service442–444、Mapper方法以及[XML150–156](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml:150)：

```sql
select ah.bill_no from jsh_account_head ah
left join jsh_account_item ai on ah.id=ai.header_id and ifnull(ai.delete_flag,'0') !='1'
where ai.bill_id=#{billId}
and ifnull(ah.delete_flag,'0') !='1'
```

### 4.2 可以形成什么本体定义

人工推导候选：**财务单据包含财务明细；财务明细可关联业务单据；保存关联财务明细可能触发业务单据最终欠款回写**。

机制不是“两个Controller有关”：它是billNumber→业务头id→财务明细billId→SQL查询/金额汇总→以同id回写业务头lastDebt。由此可以形成Link和有条件Effect，不需要新做Java跨方法数据流分析。

仍不能推出银行已付款、财务已记账、所有财务明细均必须关联业务单据、整个供应商生命周期闭合或企业认可的余额口径。付款分支把eachAmount改为负数的实际实现也须保留，不能把订金、本次付款和应付额合成一种金额。

### 4.3 自动发现必须补的薄能力

O0反向导航从财务明细statement/读写方法找到不同Controller；如果已保存邻接不够，搜索真实billId、billNumber、lastDebt命中并读取上下文。O2让模型核对键转换和条件，而非Java批准中文关系。

共用日志/用户方法会形成技术邻居，但在RELATE/REVIEW中必须被拒绝为业务交接。本文证明有原文可以核对，**还未证明模型能自主找到这批原文**，这正是PoC关键验收项。

## 5. 操作与规则例子：审核/反审核

[DepotHeadService741–822](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:741)在审核入口R4中完整保存：

- 目标status=1：当前status必须0，否则拒绝。
- 目标status=0：当前status必须1且purchaseStatus=0；purchaseStatus=2/3各有具体拒绝分支，其它不满足也拒绝。
- 审核库存检查：forceApproval开启且minusStock关闭；inOutManage开启只校验“出库/其它”，关闭时校验“出库/销售、采购退货、其它”。
- 更新选中id的status；forceApproval开启时更新当前库存；记录审核/反审核日志。

可提出两个Action及各自Rule；不因为方法名是batchSetStatus就把任意状态值改动都命名为“审核”。同字段的办理状态与审核行为分开解释。程序不重建完美全生命周期状态机。

这里“当前库存更新”是实际调用/写入效果，不是仓库已经物理发货。权限和updateCurrentStock完整内部口径未核对处保留未知，不说所有规则已覆盖。

## 6. 指标、measure与dimension怎样得到

### 6.1 必须把Java和SQL一起读

统计R4包含DepotHeadController640–669、DepotHeadService1478–1570及XML getBuyAndSaleStatisticsList。原SQL：

```sql
select distinct dh.id, dh.discount_last_money, dh.total_price, dh.type, dh.sub_type, dh.oper_time
from jsh_depot_head dh
left join jsh_depot_item di on dh.id = di.header_id and ifnull(di.delete_flag,'0') !='1'
where di.id is not null
and (dh.type='入库' or dh.type='出库')
```

[完整XML1152–1175](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml:1152)另外有begin/end边界、forceFlag时status在1/2/3、creatorArray过滤、头删除过滤。SQL分析PARTIAL，但动态XML原文仍可读；不能以AST未覆盖为由省略条件。

[Java1478–1570](/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/.workspace/technical-analysis-cli-20260926/fixed-source-8c30ce7/jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:1478)取全年范围记录，再按今日/月/昨日/年窗口累加：

```java
// 实际两个分支的摘录；不是连续原方法
case "采购":
    statistics.put(period + "Buy", statistics.get(period + "Buy").add(discountLastMoney));
    break;
case "采购退货":
    statistics.put(period + "BuyBack", statistics.get(period + "BuyBack").add(discountLastMoney));
    break;
```

1529行返回Buy-BuyBack，并经过parseHomePriceByLimit，可能返回遮蔽值`"***"`。Controller651–659还限制非admin才调用，admin分支返回初始空map；不能省略成“所有用户都会收到数值”。

### 6.2 人工可推导的定义，不是已生成指标

| 定义项 | 可核对内容 | 必须保留的未知/限制 |
| --- | --- | --- |
| Measure：采购金额 | 入库/采购分支的discountLastMoney合计 | 原取数过滤和权限条件；不是total_price |
| Measure：采购退货金额 | 出库/采购退货分支的discountLastMoney合计 | 符号不得凭字段名重解释 |
| Metric：分期间净采购额 | 正常非空路径下前者减后者 | 只识别实现口径，非企业批准KPI，未执行数据库 |
| 粒度 | DISTINCT单据头字段，含id；不是商品明细逐行金额 | 以后换join维度不能沿用未经核对的聚合 |
| 时间dimension/窗口 | oper_time；Java日期边界闭区间，today/month/yesterday/year | 时区与企业业务日历未确认 |
| 分类/筛选 | type/subType选择计算分支；creatorArray仅筛选 | 不能把creator自动批准为所有指标GROUPING维度 |
| 数值单位 | 金额量纲 | 未确定币种，不填CNY；不输出真实金额 |

零售分支使用totalPrice的绝对值，与采购/销售使用discountLastMoney不同；不能复制同一金额规则到全部变体。这个反例应进入ANALYTIC_REVIEW。

NULL/失败边界也必须保留：DDL的total_price、discount_last_money允许NULL；Java1507–1508对每行直接读取discountLastMoney并无条件调用totalPrice.abs()，1546/1559直接BigDecimal.add。NULL行可能抛异常，Controller663–666返回500。本页未核实实际业务数据是否避免NULL，不能默认NULL=0，也不能宣称对所有合法DDL行稳定返回金额。

## 7. 设计可行性推导与尚未验证

本页沿实际资料完成了：存储身份→业务变体→采购来源关系→跨Controller财务关联/欠款效果→审核规则→净采购指标/维度。每个环节有具体正文；未知也有对应字段，不依赖重做JDT或Activity。

因此可以选择一条完整PoC验证路线，而不是只演示表名抽取。**材料存在、人工推导成立，并不能保证自动选材、小模型推理和全部候选归并通过。**缺的是待实现的薄consumer/任务合同及语义实测，不是更多泛化证据框架。

本次发现的实际边界：

1. 整份DDL方言解析失败；需保留有限支持，不造parser。
2. R4不是所有业务原文的闭包；同R0原文补读是必要能力。
3. 前端运行时地址未确认；自动匹配候选不能升格确定关联。
4. 全仓Ontology未生成；人工结果只定义验收检查，不送无人提示模型当答案。
5. 有限静态材料无法证明真实执行、企业政策或指标部署；输出明示NOT_EXECUTABLE。
