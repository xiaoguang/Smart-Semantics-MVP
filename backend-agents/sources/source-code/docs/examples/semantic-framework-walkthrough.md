# 业务优先语义框架完整 walkthrough

> 本文是明确合成的设计样例，不是 jshERP、DepotHead 或任何客户仓库的事实，也不是已运行 golden。它完整展示“补货单 → 收货记录 → 应付账单”的三入口链路，目的是检查四个深 Module 的信息能否一直传到九章报告。示例行号与片段只在本页合成文件内自洽，不可复制成真实来源证据。

## 1. 合成冻结源码

### 1.1 三个 HTTP 入口

文件 src/main/java/example/SupplyWorkflowController.java：

~~~java
10 @PostMapping("/replenishments")
11 public String create(@RequestBody ReplenishmentCommand command) {
12     return replenishmentService.create(command);
13 }
14 @PostMapping("/receipts")
15 public String receive(@RequestBody ReceiptCommand command) {
16     return goodsReceiptService.record(command);
17 }
18 @PostMapping("/bills")
19 public String bill(@RequestParam String receiptId) {
20     return payableBillService.create(receiptId);
21 }
~~~

### 1.2 创建补货单

文件 src/main/java/example/ReplenishmentService.java：

~~~java
20 public String create(ReplenishmentCommand command) {
21     if (command.lines().isEmpty()) {
22         throw new IllegalArgumentException("lines required");
23     }
24     ReplenishmentOrder order = ReplenishmentOrder.from(command.lines());
25     supplyWorkflowMapper.insertReplenishmentOrder(order);
26     return order.id();
27 }
~~~

### 1.3 记录收货

文件 src/main/java/example/GoodsReceiptService.java：

~~~java
30 public String record(ReceiptCommand command) {
31     GoodsReceipt receipt = GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice());
32     supplyWorkflowMapper.insertGoodsReceipt(receipt);
33     return receipt.id();
34 }
~~~

### 1.4 创建应付账单

文件 src/main/java/example/PayableBillService.java：

~~~java
40 public String create(String receiptId) {
41     GoodsReceipt receipt = supplyWorkflowMapper.selectGoodsReceiptById(receiptId);
42     BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());
43     PayableBill bill = PayableBill.from(receipt.id(), amount);
44     supplyWorkflowMapper.insertPayableBill(bill);
45     return bill.id();
46 }
~~~

### 1.5 Mapper SQL

文件 src/main/resources/mapper/SupplyWorkflowMapper.xml：

~~~xml
2  <mapper namespace="example.SupplyWorkflowMapper">
5  <insert id="insertReplenishmentOrder">
6    insert into replenishment_order (id, status)
7    values (#{id}, #{status})
8  </insert>
11 <insert id="insertGoodsReceipt">
12   insert into goods_receipt (id, replenishment_order_id, received_quantity, unit_price)
13   values (#{id}, #{replenishmentOrderId}, #{receivedQuantity}, #{unitPrice})
14 </insert>
17 <select id="selectGoodsReceiptById">
18   select id, replenishment_order_id, received_quantity, unit_price
19   from goods_receipt where id = #{receiptId}
20 </select>
22 <insert id="insertPayableBill">
23   insert into payable_bill (id, receipt_id, payable_amount)
24   values (#{id}, #{receiptId}, #{payableAmount})
25 </insert>
27 </mapper>
~~~

本合成样例约定 ReplenishmentOrder.from、GoodsReceipt.from 和 PayableBill.from 按展示参数创建相应对象；它不是一份可编译客户项目。三个 Java 调用与同一个 example.SupplyWorkflowMapper namespace 下的 statement id 一一对应，因此 insert/select 的持久化语境在样例内是清楚的。

这些源码足以说明代码定义了三类动作：生成并保存补货单、生成并保存收货记录、按“收货数量 × 单价”计算金额并生成及保存应付账单。它们不证明某次运行成功、物理货物实际到达、库存实际增加、总账已经过账或款项已经支付。

## 2. Step 01–05 的技术投影

Step 01–05 的真实 schema 与严格 Proof 合同仍以各步骤详细设计为准。下面只是 BusinessMaterialBuilder 所需视图的完整合成投影，不替换现有技术产物。

~~~json
{
  "runInputBinding": {
    "runId": "run:synthetic-supply-workflow",
    "sourceSnapshotId": "snapshot:synthetic-supply-workflow-v1",
    "sourceManifestSha256": "0000000000000000000000000000000000000000000000000000000000000001",
    "technicalPublicationRoot": "technical-root:synthetic-v1"
  },
  "inventory": {
    "scope": "COMPLETE_SYNTHETIC_FIXTURE",
    "regularFiles": [
      "src/main/java/example/SupplyWorkflowController.java",
      "src/main/java/example/ReplenishmentService.java",
      "src/main/java/example/GoodsReceiptService.java",
      "src/main/java/example/PayableBillService.java",
      "src/main/resources/mapper/SupplyWorkflowMapper.xml"
    ]
  },
  "entries": [
    {
      "entryId": "entry:create-replenishment",
      "method": "POST",
      "route": "/replenishments",
      "handler": "example.SupplyWorkflowController#create"
    },
    {
      "entryId": "entry:record-receipt",
      "method": "POST",
      "route": "/receipts",
      "handler": "example.SupplyWorkflowController#receive"
    },
    {
      "entryId": "entry:create-payable-bill",
      "method": "POST",
      "route": "/bills",
      "handler": "example.SupplyWorkflowController#bill"
    }
  ],
  "graphProjection": {
    "callEdges": [
      ["example.SupplyWorkflowController#create", "example.ReplenishmentService#create"],
      ["example.ReplenishmentService#create", "example.ReplenishmentOrder#from"],
      ["example.ReplenishmentService#create", "example.SupplyWorkflowMapper#insertReplenishmentOrder"],
      ["example.SupplyWorkflowController#receive", "example.GoodsReceiptService#record"],
      ["example.GoodsReceiptService#record", "example.GoodsReceipt#from"],
      ["example.GoodsReceiptService#record", "example.SupplyWorkflowMapper#insertGoodsReceipt"],
      ["example.SupplyWorkflowController#bill", "example.PayableBillService#create"],
      ["example.PayableBillService#create", "example.SupplyWorkflowMapper#selectGoodsReceiptById"],
      ["example.PayableBillService#create", "java.math.BigDecimal#multiply"],
      ["example.PayableBillService#create", "example.PayableBill#from"],
      ["example.PayableBillService#create", "example.SupplyWorkflowMapper#insertPayableBill"]
    ],
    "controlConditions": [
      {
        "entryId": "entry:create-replenishment",
        "condition": "command.lines().isEmpty() causes rejection"
      }
    ],
    "dataRelations": [
      "ReceiptCommand.replenishmentOrderId -> GoodsReceipt.replenishmentOrderId",
      "GoodsReceipt.id -> PayableBill.receiptId",
      "GoodsReceipt.receivedQuantity and unitPrice -> PayableBill.payableAmount"
    ]
  },
  "factProjection": [
    {
      "factKind": "JAVA_GUARD_CONDITION",
      "entryId": "entry:create-replenishment",
      "technicalStatement": "empty lines enter the rejection branch"
    },
    {
      "factKind": "JAVA_EXACT_CALL",
      "entryId": "entry:create-replenishment",
      "technicalStatement": "SupplyWorkflowMapper.insertReplenishmentOrder is called with order"
    },
    {
      "factKind": "JAVA_EXACT_CALL",
      "entryId": "entry:record-receipt",
      "technicalStatement": "SupplyWorkflowMapper.insertGoodsReceipt is called with receipt"
    },
    {
      "factKind": "JAVA_EXACT_CALL",
      "entryId": "entry:create-payable-bill",
      "technicalStatement": "SupplyWorkflowMapper.insertPayableBill is called with bill"
    }
  ],
  "flows": [
    {
      "flowId": "flow:create-replenishment",
      "entryId": "entry:create-replenishment",
      "outcome": "returns generated order id"
    },
    {
      "flowId": "flow:record-receipt",
      "entryId": "entry:record-receipt",
      "outcome": "returns generated receipt id"
    },
    {
      "flowId": "flow:create-payable-bill",
      "entryId": "entry:create-payable-bill",
      "outcome": "returns generated bill id"
    }
  ],
  "technicalGaps": []
}
~~~

这份投影保留了技术事实，但不会把 controller、service、mapper 名机械等同为业务参与者、活动或数据库运行结果。

## 3. BusinessMaterialBuilder

### 3.1 程序保存的 SourceRef reverse map

模型看不到路径、行号或 hash。程序保存完整、可重读的简单 ref：

~~~jsonl
{"ref":"S1","file":"src/main/java/example/ReplenishmentService.java","startLine":21,"endLine":23,"snippet":"if (command.lines().isEmpty()) {\n    throw new IllegalArgumentException(\"lines required\");\n}"}
{"ref":"S2","file":"src/main/java/example/ReplenishmentService.java","startLine":24,"endLine":26,"snippet":"ReplenishmentOrder order = ReplenishmentOrder.from(command.lines());\nsupplyWorkflowMapper.insertReplenishmentOrder(order);\nreturn order.id();"}
{"ref":"S3","file":"src/main/resources/mapper/SupplyWorkflowMapper.xml","startLine":5,"endLine":8,"snippet":"<insert id=\"insertReplenishmentOrder\">\n  insert into replenishment_order (id, status)\n  values (#{id}, #{status})\n</insert>"}
{"ref":"S4","file":"src/main/java/example/GoodsReceiptService.java","startLine":30,"endLine":33,"snippet":"public String record(ReceiptCommand command) {\n    GoodsReceipt receipt = GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice());\n    supplyWorkflowMapper.insertGoodsReceipt(receipt);\n    return receipt.id();"}
{"ref":"S5","file":"src/main/resources/mapper/SupplyWorkflowMapper.xml","startLine":11,"endLine":14,"snippet":"<insert id=\"insertGoodsReceipt\">\n  insert into goods_receipt (id, replenishment_order_id, received_quantity, unit_price)\n  values (#{id}, #{replenishmentOrderId}, #{receivedQuantity}, #{unitPrice})\n</insert>"}
{"ref":"S6","file":"src/main/java/example/PayableBillService.java","startLine":40,"endLine":45,"snippet":"public String create(String receiptId) {\n    GoodsReceipt receipt = supplyWorkflowMapper.selectGoodsReceiptById(receiptId);\n    BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());\n    PayableBill bill = PayableBill.from(receipt.id(), amount);\n    supplyWorkflowMapper.insertPayableBill(bill);\n    return bill.id();"}
{"ref":"S7","file":"src/main/java/example/PayableBillService.java","startLine":42,"endLine":42,"snippet":"BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());"}
{"ref":"S8","file":"src/main/resources/mapper/SupplyWorkflowMapper.xml","startLine":17,"endLine":25,"snippet":"<select id=\"selectGoodsReceiptById\">\n  select id, replenishment_order_id, received_quantity, unit_price\n  from goods_receipt where id = #{receiptId}\n</select>\n\n<insert id=\"insertPayableBill\">\n  insert into payable_bill (id, receipt_id, payable_amount)\n  values (#{id}, #{receiptId}, #{payableAmount})\n</insert>"}
~~~

### 3.2 模型看到的三个 clean package

每个包是一个完整局部活动，不是逐 Fact shard。第一个包：

~~~json
{
  "materialId": "material:create-replenishment",
  "entryIds": ["entry:create-replenishment"],
  "context": "HTTP handler 接收 ReplenishmentCommand 并调用 ReplenishmentService.create。",
  "technicalObservations": [
    "command.lines().isEmpty() 为 true 时抛出 IllegalArgumentException",
    "调用 ReplenishmentOrder.from(command.lines())",
    "调用 SupplyWorkflowMapper.insertReplenishmentOrder(order)",
    "返回 order.id()"
  ],
  "allowlistedRefs": [
    {"ref":"S1","snippet":"if (command.lines().isEmpty()) { throw new IllegalArgumentException(\"lines required\"); }"},
    {"ref":"S2","snippet":"ReplenishmentOrder order = ReplenishmentOrder.from(command.lines()); supplyWorkflowMapper.insertReplenishmentOrder(order); return order.id();"},
    {"ref":"S3","snippet":"insert into replenishment_order (id, status) values (#{id}, #{status})"}
  ],
  "limitations": [
    "源码没有说明调用者岗位",
    "静态源码不证明某次 INSERT 成功"
  ]
}
~~~

第二个包：

~~~json
{
  "materialId": "material:record-receipt",
  "entryIds": ["entry:record-receipt"],
  "context": "HTTP handler 接收 ReceiptCommand 并调用 GoodsReceiptService.record。",
  "technicalObservations": [
    "调用 GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice())",
    "调用 SupplyWorkflowMapper.insertGoodsReceipt(receipt)",
    "返回 receipt.id()"
  ],
  "allowlistedRefs": [
    {"ref":"S4","snippet":"GoodsReceipt receipt = GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice()); supplyWorkflowMapper.insertGoodsReceipt(receipt); return receipt.id();"},
    {"ref":"S5","snippet":"insert into goods_receipt (id, replenishment_order_id, received_quantity, unit_price) values (#{id}, #{replenishmentOrderId}, #{receivedQuantity}, #{unitPrice})"}
  ],
  "limitations": [
    "源码没有说明实际货物是否到达",
    "源码没有说明收货岗位或补货单状态前提"
  ]
}
~~~

第三个包：

~~~json
{
  "materialId": "material:create-payable-bill",
  "entryIds": ["entry:create-payable-bill"],
  "context": "HTTP handler 接收 receiptId 并调用 PayableBillService.create。",
  "technicalObservations": [
    "调用 SupplyWorkflowMapper.selectGoodsReceiptById(receiptId)",
    "调用 receipt.receivedQuantity().multiply(receipt.unitPrice())",
    "调用 PayableBill.from(receipt.id(), amount)",
    "调用 SupplyWorkflowMapper.insertPayableBill(bill)",
    "返回 bill.id()"
  ],
  "allowlistedRefs": [
    {"ref":"S6","snippet":"GoodsReceipt receipt = supplyWorkflowMapper.selectGoodsReceiptById(receiptId); BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice()); PayableBill bill = PayableBill.from(receipt.id(), amount); supplyWorkflowMapper.insertPayableBill(bill); return bill.id();"},
    {"ref":"S7","snippet":"BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());"},
    {"ref":"S8","snippet":"select id, replenishment_order_id, received_quantity, unit_price from goods_receipt where id = #{receiptId}; insert into payable_bill (id, receipt_id, payable_amount) values (#{id}, #{receiptId}, #{payableAmount})"}
  ],
  "limitations": [
    "保存应付账单不表示总账已经过账",
    "源码没有说明付款、币种或账单岗位"
  ]
}
~~~

程序同时保存入口覆盖：

~~~json
{
  "discoveredEntryCount": 3,
  "entries": [
    {"entryId":"entry:create-replenishment","materialId":"material:create-replenishment","status":"ANALYZED_MATERIAL","reason":null},
    {"entryId":"entry:record-receipt","materialId":"material:record-receipt","status":"ANALYZED_MATERIAL","reason":null},
    {"entryId":"entry:create-payable-bill","materialId":"material:create-payable-bill","status":"ANALYZED_MATERIAL","reason":null}
  ]
}
~~~

## 4. ActivityExplainer

Luna/high 对每个包先写 DRAFT，再对该包的完整实际草稿做一次 REVIEW。下面是 review 后的完整 activity-explanations.jsonl：

~~~jsonl
{"activityId":"activity:create-replenishment","materialId":"material:create-replenishment","entryIds":["entry:create-replenishment"],"name":"创建补货单","businessPurpose":"把提交的补货明细形成并保存为补货单，供后续业务处理。","participants":[],"businessObjects":["补货单","补货明细"],"triggerOrInput":["补货明细集合"],"conditions":["补货明细集合不能为空"],"activitySteps":["校验补货明细是否为空","根据明细生成补货单","保存补货单"],"codeDefinedResults":["系统生成并保存补货单"],"businessRules":["没有补货明细时不进入补货单生成"],"formulasOrMetrics":[],"terms":["补货单","补货明细"],"certainty":"DIRECT_CODE_BEHAVIOR","sourceRefs":["S1","S2","S3"],"questions":["哪类岗位或系统有权发起补货？"],"scopeLimitations":["静态源码说明系统设计行为，不证明某次保存成功"]}
{"activityId":"activity:record-receipt","materialId":"material:record-receipt","entryIds":["entry:record-receipt"],"name":"记录收货","businessPurpose":"把与补货单关联的收货数量和单价保存为收货记录。","participants":[],"businessObjects":["补货单","收货记录"],"triggerOrInput":["补货单标识","收货数量","单价"],"conditions":[],"activitySteps":["读取补货单标识和收货数据","生成收货记录","保存收货记录"],"codeDefinedResults":["系统生成并保存与补货单标识关联的收货记录"],"businessRules":[],"formulasOrMetrics":[],"terms":["收货记录"],"certainty":"DIRECT_CODE_BEHAVIOR","sourceRefs":["S4","S5"],"questions":["记录收货是否要求补货单处于特定状态？","哪类岗位或系统负责确认收货？"],"scopeLimitations":["代码中的收货记录不证明物理货物在某次运行中实际到达"]}
{"activityId":"activity:create-payable-bill","materialId":"material:create-payable-bill","entryIds":["entry:create-payable-bill"],"name":"创建应付账单","businessPurpose":"依据收货记录中的数量和单价形成并保存应付账单。","participants":[],"businessObjects":["收货记录","应付账单"],"triggerOrInput":["收货记录标识"],"conditions":[],"activitySteps":["按标识读取收货记录","用收货数量乘以单价计算应付金额","生成应付账单","保存应付账单"],"codeDefinedResults":["系统计算应付金额并生成及保存应付账单"],"businessRules":[],"formulasOrMetrics":["应付金额 = 收货数量 × 单价"],"terms":["应付账单","应付金额"],"certainty":"DIRECT_CODE_BEHAVIOR","sourceRefs":["S6","S7","S8"],"questions":["应付账单保存后是否另有审核、过账和付款过程？","币种和金额舍入规则是什么？"],"scopeLimitations":["账单记录写入不证明某次运行已完成过账或付款"]}
~~~

这一步没有把未知岗位填成“采购员”“仓库员”或“财务人员”。它也没有把清楚的 insert 行为一律降成“提交供后续处理”：正文可以说明代码定义的保存动作，同时在活动级 limitation 中一次性说明运行事实边界。

## 5. ProcessExplainer

### 5.1 程序宽松召回

程序形成一个重叠 process group，理由是显式标识和数据承接，而不是名字看起来相似：

~~~json
{
  "groupId": "group:replenishment-receipt-bill",
  "activityIds": [
    "activity:create-replenishment",
    "activity:record-receipt",
    "activity:create-payable-bill"
  ],
  "recallCues": [
    "GoodsReceipt.replenishmentOrderId 承接补货单标识",
    "PayableBill.receiptId 承接收货记录标识",
    "账单金额读取收货记录的 receivedQuantity 和 unitPrice"
  ],
  "candidateOnly": true,
  "sourceRefs": ["S2","S4","S5","S6","S7","S8"]
}
~~~

### 5.2 已审过程

Luna/high 对完整 group 做 DRAFT + 一次 REVIEW：

~~~json
{
  "processId": "process:replenishment-to-payable-bill",
  "groupId": "group:replenishment-receipt-bill",
  "name": "补货到应付账单形成",
  "businessPurpose": "从补货明细形成补货单，在记录与该补货单关联的收货后，依据收货数量和单价形成应付账单。",
  "activityIds": [
    "activity:create-replenishment",
    "activity:record-receipt",
    "activity:create-payable-bill"
  ],
  "stages": [
    {"order":1,"activityId":"activity:create-replenishment","description":"生成并保存补货单"},
    {"order":2,"activityId":"activity:record-receipt","description":"按补货单标识生成并保存收货记录"},
    {"order":3,"activityId":"activity:create-payable-bill","description":"按收货记录计算金额并生成及保存应付账单"}
  ],
  "branches": [
    "补货明细为空时，创建补货单活动在生成对象前停止"
  ],
  "sharedObjects": ["补货单","收货记录","应付账单"],
  "codeDefinedResults": [
    "系统可形成补货单、关联的收货记录和关联的应付账单",
    "应付金额按收货数量乘以单价计算"
  ],
  "certainty": "REASONABLE_INFERENCE",
  "sourceRefs": ["S1","S2","S3","S4","S5","S6","S7","S8"],
  "confirmationNotes": [
    "标识和数据承接支持该端到端串联，但源码不能说明组织制度是否强制三个入口依次执行",
    "源码没有说明执行岗位、补货单到收货的状态前提、币种、舍入、审核、过账或付款"
  ]
}
~~~

### 5.3 一份仓库知识

repositorySummary 是导航，不取代完整活动、条件、规则、公式和 refs：

~~~json
{
  "repositorySummary": {
    "text": "该合成仓库围绕补货单、收货记录和应付账单定义了三个可串联的业务活动。",
    "coverage": "COMPLETE_FOR_DISCOVERED_ENTRIES",
    "sourceRefs": ["S2","S4","S6"]
  },
  "businessGoals": [
    {"text":"把补货明细形成并保存为补货单","sourceRefs":["S1","S2","S3"]},
    {"text":"保存与补货单关联的收货记录","sourceRefs":["S4","S5"]},
    {"text":"按收货数据形成并保存应付账单","sourceRefs":["S6","S7","S8"]}
  ],
  "activities": [
    {
      "activityId":"activity:create-replenishment",
      "materialId":"material:create-replenishment",
      "entryIds":["entry:create-replenishment"],
      "name":"创建补货单",
      "businessPurpose":"把提交的补货明细形成并保存为补货单，供后续业务处理。",
      "participants":[],
      "businessObjects":["补货单","补货明细"],
      "triggerOrInput":["补货明细集合"],
      "conditions":["补货明细集合不能为空"],
      "activitySteps":["校验补货明细是否为空","根据明细生成补货单","保存补货单"],
      "codeDefinedResults":["系统生成并保存补货单"],
      "businessRules":["没有补货明细时不进入补货单生成"],
      "formulasOrMetrics":[],
      "terms":["补货单","补货明细"],
      "certainty":"DIRECT_CODE_BEHAVIOR",
      "questions":["哪类岗位或系统有权发起补货？"],
      "scopeLimitations":["静态源码说明系统设计行为，不证明某次保存成功"],
      "sourceRefs":["S1","S2","S3"]
    },
    {
      "activityId":"activity:record-receipt",
      "materialId":"material:record-receipt",
      "entryIds":["entry:record-receipt"],
      "name":"记录收货",
      "businessPurpose":"把与补货单关联的收货数量和单价保存为收货记录。",
      "participants":[],
      "businessObjects":["补货单","收货记录"],
      "triggerOrInput":["补货单标识","收货数量","单价"],
      "conditions":[],
      "activitySteps":["读取补货单标识和收货数据","生成收货记录","保存收货记录"],
      "codeDefinedResults":["系统生成并保存与补货单标识关联的收货记录"],
      "businessRules":[],
      "formulasOrMetrics":[],
      "terms":["收货记录"],
      "certainty":"DIRECT_CODE_BEHAVIOR",
      "questions":["记录收货是否要求补货单处于特定状态？","哪类岗位或系统负责确认收货？"],
      "scopeLimitations":["代码中的收货记录不证明物理货物在某次运行中实际到达"],
      "sourceRefs":["S4","S5"]
    },
    {
      "activityId":"activity:create-payable-bill",
      "materialId":"material:create-payable-bill",
      "entryIds":["entry:create-payable-bill"],
      "name":"创建应付账单",
      "businessPurpose":"依据收货记录中的数量和单价形成并保存应付账单。",
      "participants":[],
      "businessObjects":["收货记录","应付账单"],
      "triggerOrInput":["收货记录标识"],
      "conditions":[],
      "activitySteps":["按标识读取收货记录","计算应付金额","生成应付账单","保存应付账单"],
      "codeDefinedResults":["系统计算应付金额并生成及保存应付账单"],
      "businessRules":[],
      "formulasOrMetrics":["应付金额 = 收货数量 × 单价"],
      "terms":["应付账单","应付金额"],
      "certainty":"DIRECT_CODE_BEHAVIOR",
      "questions":["应付账单保存后是否另有审核、过账和付款过程？","币种和金额舍入规则是什么？"],
      "scopeLimitations":["账单记录写入不证明某次运行已完成过账或付款"],
      "sourceRefs":["S6","S7","S8"]
    }
  ],
  "processes": [
    {
      "processId":"process:replenishment-to-payable-bill",
      "groupId":"group:replenishment-receipt-bill",
      "name":"补货到应付账单形成",
      "businessPurpose":"从补货明细形成补货单，在记录与该补货单关联的收货后，依据收货数量和单价形成应付账单。",
      "activityIds":["activity:create-replenishment","activity:record-receipt","activity:create-payable-bill"],
      "stages":[
        {"order":1,"activityId":"activity:create-replenishment","description":"生成并保存补货单"},
        {"order":2,"activityId":"activity:record-receipt","description":"按补货单标识生成并保存收货记录"},
        {"order":3,"activityId":"activity:create-payable-bill","description":"按收货记录计算金额并生成及保存应付账单"}
      ],
      "branches":["补货明细为空时，创建补货单活动在生成对象前停止"],
      "sharedObjects":["补货单","收货记录","应付账单"],
      "codeDefinedResults":["系统可形成补货单、关联的收货记录和关联的应付账单","应付金额按收货数量乘以单价计算"],
      "certainty":"REASONABLE_INFERENCE",
      "confirmationNotes":["标识和数据承接支持该端到端串联，但源码不能说明组织制度是否强制三个入口依次执行","源码没有说明执行岗位、补货单到收货的状态前提、币种、舍入、审核、过账或付款"],
      "sourceRefs":["S1","S2","S3","S4","S5","S6","S7","S8"]
    }
  ],
  "manyToManyMemberships": [
    {"activityId":"activity:create-replenishment","processIds":["process:replenishment-to-payable-bill"]},
    {"activityId":"activity:record-receipt","processIds":["process:replenishment-to-payable-bill"]},
    {"activityId":"activity:create-payable-bill","processIds":["process:replenishment-to-payable-bill"]}
  ],
  "objectsAndRelations": [
    {"text":"收货记录通过 replenishmentOrderId 引用补货单","sourceRefs":["S4","S5"]},
    {"text":"应付账单通过 receiptId 引用收货记录","sourceRefs":["S6","S8"]}
  ],
  "formulasOrMetrics": [
    {"text":"应付金额 = 收货数量 × 单价","sourceRefs":["S7"]}
  ],
  "confirmationTopics": [
    "哪些岗位或系统可以执行三个入口？",
    "组织制度是否强制补货、收货和账单依次发生？",
    "记录收货是否要求补货单处于特定状态？",
    "币种和金额舍入规则是什么？",
    "应付账单保存后是否另有审核、过账和付款过程？"
  ],
  "coverage": {
    "discoveredEntryCount":3,
    "analyzedEntryIds":["entry:create-replenishment","entry:record-receipt","entry:create-payable-bill"],
    "analyzedWithGaps":[],
    "notAnalyzed":[],
    "processGroupCount":1,
    "notConsolidatedGroups":[],
    "repositorySummaryCoverage":"COMPLETE_FOR_DISCOVERED_ENTRIES",
    "semanticDeliveryStatus":"READY_FOR_REPORT"
  }
}
~~~

## 6. BusinessReportPublisher 的章节结构 JSON

报告 DRAFT 和一次 REVIEW 都返回完整九章 JSON。下面是 review 后输入给确定性 renderer 的完整 business-report.json：

~~~json
{
  "title": "合成补货仓库业务说明",
  "sections": [
    {
      "number":1,
      "title":"文档说明",
      "paragraphs":[
        {"text":"本文描述冻结源码定义的系统行为，不代表某次生产运行已经成功。三个发现入口均已纳入业务分析；跨入口顺序属于有源码承接支持的合理推测，组织制度仍需确认。","refs":[]}
      ],
      "items":[]
    },
    {
      "number":2,
      "title":"业务目标",
      "paragraphs":[
        {"text":"系统围绕补货、收货和应付账单形成三项目标：把补货明细保存为补货单，记录与补货单关联的收货，并依据收货数据形成应付账单。","refs":["S1","S2","S3","S4","S5","S6","S7","S8"]}
      ],
      "items":[]
    },
    {
      "number":3,
      "title":"业务对象",
      "paragraphs":[
        {"text":"主要对象是补货单、补货明细、收货记录和应付账单。收货记录保留补货单标识；应付账单保留收货记录标识和计算后的应付金额。","refs":["S2","S4","S5","S6","S8"]}
      ],
      "items":[]
    },
    {
      "number":4,
      "title":"业务活动",
      "paragraphs":[
        {"text":"创建补货单：系统先检查补货明细是否为空；明细存在时，根据明细生成补货单并保存。","refs":["S1","S2","S3"]},
        {"text":"记录收货：系统接收补货单标识、收货数量和单价，生成与补货单关联的收货记录并保存。","refs":["S4","S5"]},
        {"text":"创建应付账单：系统按收货记录标识读取数量和单价，计算应付金额，生成应付账单并保存。","refs":["S6","S7","S8"]},
        {"text":"三个活动可合理串联为“创建补货单 → 记录收货 → 创建应付账单”。标识和数据承接支持这项推测，但源码不能说明组织制度是否强制按该顺序执行。","refs":["S2","S4","S5","S6","S8"]}
      ],
      "items":[]
    },
    {
      "number":5,
      "title":"字段与维度",
      "paragraphs":[
        {"text":"补货入口使用补货明细集合；收货记录包含补货单编号、收货数量和单价；应付账单包含收货记录编号和账单金额。对应技术字段分别是 replenishmentOrderId、receivedQuantity、unitPrice、receiptId 和 payableAmount。源码没有给出币种、金额舍入规则或统计时间维度。","refs":["S1","S4","S5","S6","S8"]}
      ],
      "items":[]
    },
    {
      "number":6,
      "title":"对象关系",
      "paragraphs":[
        {"text":"收货记录通过补货单编号与补货单关联；应付账单通过收货记录编号与收货记录关联。源码中的对应字段是 replenishmentOrderId 和 receiptId，但没有说明两种关联是否具有业务上的唯一性。","refs":["S4","S5","S6","S8"]}
      ],
      "items":[]
    },
    {
      "number":7,
      "title":"指标口径",
      "paragraphs":[
        {"text":"本例按单条收货记录计算账单金额，公式是：应付金额 = 收货数量 × 单价。源码未定义按期间汇总、税费、币种或金额舍入，因此本章不增加这些口径。","refs":["S7"]}
      ],
      "items":[]
    },
    {
      "number":8,
      "title":"示例问题",
      "paragraphs":[],
      "items":[
        {"text":"没有补货明细时，系统是否会生成补货单？","refs":["S1"]},
        {"text":"一条收货记录怎样关联到补货单？","refs":["S4","S5"]},
        {"text":"应付金额按什么公式计算？","refs":["S7"]},
        {"text":"一张应付账单怎样关联到收货记录？","refs":["S6","S8"]}
      ]
    },
    {
      "number":9,
      "title":"待确认事项",
      "paragraphs":[],
      "items":[
        {"text":"需要确认哪些岗位或系统可以执行补货、收货和账单入口。","refs":[]},
        {"text":"需要确认组织制度是否要求三个活动依次发生，以及收货前是否有补货单状态要求。","refs":[]},
        {"text":"需要确认币种、金额舍入、税费、审核、过账和付款流程。","refs":[]},
        {"text":"静态源码不证明某次保存成功，也不提供成功次数。","refs":[]}
      ]
    }
  ]
}
~~~

程序验证 section 1..9、固定标题、非空文本、ref allowlist 和 coverage disclosure，然后负责 Markdown 标题、列表和 ref 链接。纯 rerender 不调用模型。

## 7. 完整且恰好九章的 Markdown

下面代码围栏里的内容代表最终 document.md。它只有九个 H2。程序默认在第 1 章末尾由 source-refs.jsonl 生成一个可折叠“技术依据”区，因此即使 HTTP/前端源码查看器尚未实现，正文里的 ref 也有同一 Markdown 内的有效锚点、file、lines 和 snippet。未来查看器只能作为可选增强。

~~~markdown
# 合成补货仓库业务说明

## 1. 文档说明

本文描述冻结源码定义的系统行为，不代表某次生产运行已经成功。三个发现入口均已纳入业务分析；跨入口顺序属于有源码承接支持的合理推测，组织制度仍需确认。

<details>
<summary>技术依据（可选）</summary>

<a id="source-ref-s1"></a>
- S1 — src/main/java/example/ReplenishmentService.java:21–23
  <pre><code>if (command.lines().isEmpty()) {
      throw new IllegalArgumentException("lines required");
  }</code></pre>
<a id="source-ref-s2"></a>
- S2 — src/main/java/example/ReplenishmentService.java:24–26
  <pre><code>ReplenishmentOrder order = ReplenishmentOrder.from(command.lines());
  supplyWorkflowMapper.insertReplenishmentOrder(order);
  return order.id();</code></pre>
<a id="source-ref-s3"></a>
- S3 — src/main/resources/mapper/SupplyWorkflowMapper.xml:5–8
  <pre><code>&lt;insert id="insertReplenishmentOrder"&gt;
    insert into replenishment_order (id, status)
    values (#{id}, #{status})
  &lt;/insert&gt;</code></pre>
<a id="source-ref-s4"></a>
- S4 — src/main/java/example/GoodsReceiptService.java:30–33
  <pre><code>public String record(ReceiptCommand command) {
      GoodsReceipt receipt = GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice());
      supplyWorkflowMapper.insertGoodsReceipt(receipt);
      return receipt.id();</code></pre>
<a id="source-ref-s5"></a>
- S5 — src/main/resources/mapper/SupplyWorkflowMapper.xml:11–14
  <pre><code>&lt;insert id="insertGoodsReceipt"&gt;
    insert into goods_receipt (id, replenishment_order_id, received_quantity, unit_price)
    values (#{id}, #{replenishmentOrderId}, #{receivedQuantity}, #{unitPrice})
  &lt;/insert&gt;</code></pre>
<a id="source-ref-s6"></a>
- S6 — src/main/java/example/PayableBillService.java:40–45
  <pre><code>public String create(String receiptId) {
      GoodsReceipt receipt = supplyWorkflowMapper.selectGoodsReceiptById(receiptId);
      BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());
      PayableBill bill = PayableBill.from(receipt.id(), amount);
      supplyWorkflowMapper.insertPayableBill(bill);
      return bill.id();</code></pre>
<a id="source-ref-s7"></a>
- S7 — src/main/java/example/PayableBillService.java:42
  <pre><code>BigDecimal amount = receipt.receivedQuantity().multiply(receipt.unitPrice());</code></pre>
<a id="source-ref-s8"></a>
- S8 — src/main/resources/mapper/SupplyWorkflowMapper.xml:17–25
  <pre><code>&lt;select id="selectGoodsReceiptById" resultType="example.GoodsReceipt"&gt;
    select id, replenishment_order_id, received_quantity, unit_price
    from goods_receipt where id = #{id}
  &lt;/select&gt;

  &lt;insert id="insertPayableBill"&gt;
    insert into payable_bill (id, receipt_id, payable_amount)
    values (#{id}, #{receiptId}, #{payableAmount})
  &lt;/insert&gt;</code></pre>

</details>

## 2. 业务目标

系统围绕补货、收货和应付账单形成三项目标：把补货明细保存为补货单，记录与补货单关联的收货，并依据收货数据形成应付账单。[S1](#source-ref-s1) [S2](#source-ref-s2) [S3](#source-ref-s3) [S4](#source-ref-s4) [S5](#source-ref-s5) [S6](#source-ref-s6) [S7](#source-ref-s7) [S8](#source-ref-s8)

## 3. 业务对象

主要对象是补货单、补货明细、收货记录和应付账单。收货记录保留补货单标识；应付账单保留收货记录标识和计算后的应付金额。[S2](#source-ref-s2) [S4](#source-ref-s4) [S5](#source-ref-s5) [S6](#source-ref-s6) [S8](#source-ref-s8)

## 4. 业务活动

创建补货单：系统先检查补货明细是否为空；明细存在时，根据明细生成补货单并保存。[S1](#source-ref-s1) [S2](#source-ref-s2) [S3](#source-ref-s3)

记录收货：系统接收补货单标识、收货数量和单价，生成与补货单关联的收货记录并保存。[S4](#source-ref-s4) [S5](#source-ref-s5)

创建应付账单：系统按收货记录标识读取数量和单价，计算应付金额，生成应付账单并保存。[S6](#source-ref-s6) [S7](#source-ref-s7) [S8](#source-ref-s8)

三个活动可合理串联为“创建补货单 → 记录收货 → 创建应付账单”。标识和数据承接支持这项推测，但源码不能说明组织制度是否强制按该顺序执行。[S2](#source-ref-s2) [S4](#source-ref-s4) [S5](#source-ref-s5) [S6](#source-ref-s6) [S8](#source-ref-s8)

## 5. 字段与维度

补货入口使用补货明细集合；收货记录包含补货单编号、收货数量和单价；应付账单包含收货记录编号和账单金额。对应技术字段分别是 replenishmentOrderId、receivedQuantity、unitPrice、receiptId 和 payableAmount。源码没有给出币种、金额舍入规则或统计时间维度。[S1](#source-ref-s1) [S4](#source-ref-s4) [S5](#source-ref-s5) [S6](#source-ref-s6) [S8](#source-ref-s8)

## 6. 对象关系

收货记录通过补货单编号与补货单关联；应付账单通过收货记录编号与收货记录关联。源码中的对应字段是 replenishmentOrderId 和 receiptId，但没有说明两种关联是否具有业务上的唯一性。[S4](#source-ref-s4) [S5](#source-ref-s5) [S6](#source-ref-s6) [S8](#source-ref-s8)

## 7. 指标口径

本例按单条收货记录计算账单金额，公式是：应付金额 = 收货数量 × 单价。源码未定义按期间汇总、税费、币种或金额舍入，因此本章不增加这些口径。[S7](#source-ref-s7)

## 8. 示例问题

- 没有补货明细时，系统是否会生成补货单？[S1](#source-ref-s1)
- 一条收货记录怎样关联到补货单？[S4](#source-ref-s4) [S5](#source-ref-s5)
- 应付金额按什么公式计算？[S7](#source-ref-s7)
- 一张应付账单怎样关联到收货记录？[S6](#source-ref-s6) [S8](#source-ref-s8)

## 9. 待确认事项

- 需要确认哪些岗位或系统可以执行补货、收货和账单入口。
- 需要确认组织制度是否要求三个活动依次发生，以及收货前是否有补货单状态要求。
- 需要确认币种、金额舍入、税费、审核、过账和付款流程。
- 静态源码不证明某次保存成功，也不提供成功次数。
~~~

## 8. 简单 refs 与可点击片段

### SourceRef S1

<a id="source-ref-s1"></a>

ReplenishmentService.java:21–23：明细为空时拒绝。

### SourceRef S2

<a id="source-ref-s2"></a>

ReplenishmentService.java:24–26：构造、保存补货单并返回标识。

### SourceRef S3

<a id="source-ref-s3"></a>

SupplyWorkflowMapper.xml:5–8：补货单 INSERT。

### SourceRef S4

<a id="source-ref-s4"></a>

GoodsReceiptService.java:30–33：用补货单标识、数量和单价构造并保存收货记录。

### SourceRef S5

<a id="source-ref-s5"></a>

SupplyWorkflowMapper.xml:11–14：收货记录 INSERT，包含 replenishment_order_id。

### SourceRef S6

<a id="source-ref-s6"></a>

PayableBillService.java:40–45：读取收货记录、计算金额、构造并保存应付账单。

### SourceRef S7

<a id="source-ref-s7"></a>

PayableBillService.java:42：应付金额等于收货数量乘以单价。

### SourceRef S8

<a id="source-ref-s8"></a>

SupplyWorkflowMapper.xml:17–25：读取收货记录并 INSERT 应付账单，保留 receipt_id 和 payable_amount。

真实 renderer 的默认行为是把这些条目作为第 1 章内部的折叠技术依据写进同一 document.md，生成有效锚点且不增加第十个 H2。它使用 repository-relative file、line 与 snippet，不写 host absolute path。若未来存在经过授权的源码查看器，可额外把同一锚点跳转增强为查看器链接，但它不是交付前置。ref 映射始终由程序生成，模型只能复用 S1–S8。

## 9. 信息连续性自检

| 最终用途 | 上游字段 | 直接来源 |
| --- | --- | --- |
| 第 2 章三项目标 | businessGoals + activities results | S1–S8 |
| 第 4 章补货条件和规则 | create activity conditions/businessRules | S1 |
| 第 4 章三个保存动作 | activities codeDefinedResults | S2/S3、S4/S5、S6/S8 |
| 第 4 章跨入口过程 | process stages + certainty + confirmationNotes | S2、S4/S5、S6/S8 |
| 第 5 章字段 | activity inputs + source snippets | S1、S4/S5、S6/S8 |
| 第 6 章对象关系 | objectsAndRelations | S4/S5、S6/S8 |
| 第 7 章唯一公式 | formulasOrMetrics | S7 |
| 第 8 章示例问题 | conditions、relations、formula | S1、S4/S5、S6/S7/S8 |
| 第 9 章未知岗位/制度/运行结果 | activity questions、process confirmationNotes、scopeLimitations | coverage 与限制字段 |

repositorySummary 只帮助导航；完整活动的条件、步骤、规则、结果、公式与 refs 一直保留到报告输入。因此这里没有靠名称重新补业务含义，也没有用摘要替代关键链路。

## 10. 从上游到九章的可行性推演

### 10.1 本合成例为何能走通

1. Step 02 给出三个完整入口分母，Step 03–05 给出局部调用、条件、数据承接与 Flow。
2. BusinessMaterialBuilder 为每个入口选到足以理解完整活动的少量连续源码，并生成 S1–S8；没有把完整 Proof/SHA/provider/control 传给模型。
3. ActivityExplainer 从 S1–S8 得到三个有目的、对象、条件、步骤和代码定义结果的活动；未知岗位集中进入 questions。
4. ProcessExplainer 以 replenishmentOrderId 和 receiptId 做宽松召回，Luna 才决定提出一个合理推测过程；程序没有从名称自动排序。
5. RepositoryBusinessKnowledge 保留完整活动、过程、对象关系、公式和 coverage；摘要只是导航。
6. BusinessReportPublisher 有足够业务字段创作九章，Java 只需检查顺序、来源、覆盖并排版。
7. 第 7 章公式直接来自 S7；没有杜撰币种、税费或成功率。

这个推演证明字段和职责在设计上连续，不代表跨入口过程、整仓九章或真实仓库已经验收。四个 Module 已有最小纵切；ActivityExplainer 还已在两个固定 jshERP 小包完成真实 Luna/high DRAFT+REVIEW，但合成故事仍不能冒充客户仓库行为。

### 10.2 反例与停止结果

| 情况 | 程序行为 | 模型调用 | 能否声称整仓业务九章完成 |
| --- | --- | ---: | --- |
| 0 个发现入口 | 保存 0-entry coverage，九章只可说明范围 | activity/process 为 0；报告可 0 或 2 | 否 |
| 有 entry 和安全材料但无 Flow | ENTRY_SOURCE_FALLBACK，并保留 technical Flow Gap | 该 material 最多 2 | 可能；取决于其余入口与知识质量 |
| 单个材料超预算 | 入口记 NOT_ANALYZED_BUDGET，不切碎后冒充完整活动 | 0 | 若它影响仓库业务，否 |
| 所有入口均 NOT_ANALYZED | coverage 账可闭合，但 semanticDeliveryStatus=INCOMPLETE | 0 | 否 |
| 同名活动出现在两个模块 | 保留两个 activityId；名称只是 recall cue | review 后才可能合并 | 未审合并时否 |
| 不同名活动共享同一字段 | 保留为候选关系，不自动合并 | process review 决定 | 未审合并时否 |
| 同一“校验订单”属于下单和退款两个过程 | 建两个 process membership | 各有界 group 审阅 | 可以，多对多不是冲突 |
| 源码只出现 remoteClient.execute | 材料写模糊边界调用 | 不允许编造记账/库存/成功 | 只能带明确限制 |
| 无法从源码知道岗位或制度 | questions/confirmationNotes 集中保留 | 不让模型用常识填空 | 可以带待确认；不能称已确认制度 |
| 大仓 group summaries 超出总整理预算 | 保存全部已审 group；列出 NOT_CONSOLIDATED_BUDGET | 总整理 0 | 否；局部结果可复用 |
| 报告包超预算或 review 后仍非法 | 保存上游知识与失败诊断，不产伪 document.md | 报告至多 2 | 否 |

每入口有处置只表示 coverage 可核对，不等于业务语义交付完成。最终报告可以按业务聚合而不逐方法展开，但任何未纳入聚合的入口、group 或条件必须在文档说明或待确认事项出现。

### 10.3 大仓摘要不是静默有损压缩

ProcessExplainer 的层次是“完整已审活动 → 有界重叠分组 → 已审过程摘要 → 有界仓库总整理”。最终总整理不吃整仓源码。若压缩会丢掉条件、规则、公式或未决问题，程序必须：

- 保留完整 group knowledge 作为可复用检查点；
- 在 process-coverage.json 列出未纳入 group；
- 把 repositorySummaryCoverage 标为 PARTIAL；
- 阻止 COMPLETE 语义交付声明。

## 11. 无关领域小对照

另一个合成实验室仓库只有如下材料：

~~~java
60 public String publish(String sampleId) {
61     TestResult result = resultMapper.selectBySampleId(sampleId);
62     if (result == null) { throw new MissingResultException(sampleId); }
63     LabReport report = LabReport.from(sampleId, result);
64     labReportMapper.insert(report);
65     return report.id();
66 }
~~~

同一 BusinessMaterialBuilder 只需形成“读取样本检测结果、无结果时拒绝、生成并保存报告”的观察；同一 ActivityExplainer 可用开放词汇得到“发布检测报告”活动。Java 不需要增加“样本、检测员、审核员”行业词表，Prompt 也不会假设检测员或审核制度。真实验证已经完成两个固定 jshERP 的不同领域局部活动小包；本合成第二领域仍只用于说明框架可迁移性，不能当作第三个客户验证结果。

## 12. DepotHead 的诚实复用边界

本次没有重新扫描或运行 jshERP。只复用既有文档中的有限说明：DepotHead 八文件是 BOUNDED_PATH_SET walkthrough/local fixture；曾用于说明 batchSetStatus 入口、guard、exact call、boundary invocation 与 Mapper XML 的技术路径。它不是仓库完成样本，不能从中推出业务岗位、制度或某次数据库效果。

当前四个深 Module 尚未对固定 jshERP 执行，所以不能声称产生了新的 DepotHead Activity、Process、Flow、Capsule 或九章结果。历史记录中的 Gap、0 Flow、0 Capsule 只属于其原审计上下文，也不能伪装成本设计的新实测。真实核心样本的质量、时间和预算要等后续单独授权运行后测量。
