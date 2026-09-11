# 分析步骤 06：Flow Interpretation（ACTIVE 目标设计）

> 本步骤采用已批准的业务优先简化路线：BusinessMaterialBuilder 准备少量连贯源码与观察，ActivityExplainer 用 Luna/high 解释并审阅完整局部活动。旧六模块、九 semantic payload、R0/finite-key 与逐 record 处置路线不再是目标；当前已有 scripted 垂直链。两个明确 opt-in 的固定 jshERP 小包（DepotHead 与 AccountHead）已各完成真实 Luna/high DRAFT+REVIEW；它们使用人工审阅过的干净技术观察，证明 Prompt、模型和活动 JSON 可以产生可审阅业务语言，但不证明自动 Builder 已能为整仓入口稳定产出同等质量的材料，更不等于正式整仓 Step 06 运行。

## 1. 为什么存在

Step 01–05 擅长回答“源码是什么、入口在哪里、调用/条件/数据如何连接、哪些技术 Fact 有 Proof、每个入口有哪些 Flow”。业务读者需要的却是“这个入口整体支持什么业务活动、涉及什么对象与条件、代码定义了什么结果”。

Step 06 在不削弱技术证据的前提下跨过这条 seam：

- Java 不再被迫从 class/method/Fact kind 推出行业业务；
- 模型不再接收完整 Proof/SHA/provider/control 或整仓源码；
- 一个活动以完整 material package 为阅读和审阅单位，不拆成逐业务原子状态机；
- 每个发现入口都获得已分析结果或清楚未分析原因；
- 没有 Flow 但仍有安全冻结源码时，业务理解可以继续并保留 technical Gap。

## 2. 本步骤的 Interface、输入与完成点

Step 06 对 Step 07 提供一个稳定结果：

~~~text
interpret(FlowInterpretationRequest) -> FlowInterpretationResult
~~~

`ExplainActivitiesRequest`在 material checkpoint 和单包 profile 之外，还带
`maxMaterialsToStart`：它是一次执行可启动的 material package 数，不是单个包的字节上限。
它可在直接 Explainer 测试中为 `0`，用于验证零调用覆盖；正式运行的零模型预检则不启动
Explainer，而是调用独立的 materials-only target。最终文档运行要求该值为正；设为 `1` 时只允许一个包执行 DRAFT+REVIEW，余下可读材料
必须在 coverage 中标为 `NOT_ANALYZED_EXECUTION_CAPACITY`，不会排队等待、偷偷调用模型或
伪装为 Provider 失败。

完整 Step 06 请求的运行绑定可表示为：

~~~json
{
  "runInputBinding": {
    "runId": "run:synthetic",
    "sourceSnapshotId": "snapshot:synthetic-v1",
    "sourceManifestSha256": "0000000000000000000000000000000000000000000000000000000000000001",
    "technicalPublicationRoot": "technical-root:synthetic-v1"
  },
  "materialProfile": {
    "maxSourceRefsPerMaterial": 8,
    "maxLinesPerRef": 24,
    "maxMaterialChars": 12000
  },
  "generationProfile": {
    "promptVersion": "business-activity-v1",
    "moduleVersion": "activity-explainer-v1",
    "maxResponseChars": 16000
  },
  "maxMaterialsToStart": 1
}
~~~

程序计算一个 inputFingerprint：对实际内容输入（不含新 runId）、实际 Prompt 内容及版本、有效模型与输出配置和两个 Module 版本求一次摘要。相同冻结内容可被新 run 显式复用；Prompt 改字或有效配置变化会拒绝旧结果。它只防止复用错误业务结果，不创建逐 Module hash 链。

成功检查点：

~~~text
steps/06-flow-interpretation/
  business-materials.jsonl
  activity-explanations.jsonl
  activity-coverage.json
~~~

同一进程中两个 Module 可直接传 immutable typed objects，无须在 M2 前重新打开。M1 完成整个 material set 后立即保存 business-materials.jsonl，且必须在首次 Provider 调用前可查看；每个 material 的 DRAFT+REVIEW 完成后立即把 activity 结果写入 activity-explanations.jsonl，并同步更新 activity-coverage.json。后续包或 Provider 失败不删除已完成检查点。完成不依赖固定文件总数，也不要求每个内部 Module 另建 payload + receipt。

## 3. 主流程

~~~text
Steps 01–05 typed/reopened views
          |
          v
 BusinessMaterialBuilder
   | materials + short SourceRefs + entry coverage
   v
 ActivityExplainer
   | Luna/high DRAFT + one REVIEW per bounded package
   v
 reviewed activities + unresolved questions + coverage
          |
          v
 ProcessExplainer in Step 07
~~~

BusinessMaterialBuilder 与 ActivityExplainer 是两个深 Module。Source selection、noFlow fallback、prompt injection isolation、model adapter、response parsing和coverage 都藏在它们后面；调用者只学习一次 build 和 explain。

## 4. M1 BusinessMaterialBuilder

### 4.1 为什么需要

让模型直接读整仓会超预算、混入无关技术细节，也让来源难以回查；只传 Fact/Proof 原子又会把完整活动切碎，使模型看不到条件、对象形成和结果之间的联系。BusinessMaterialBuilder 选择“足够理解一个完整局部活动”的少量源码，而不是创造业务结论。

### 4.2 Interface 与具体输入

~~~text
build(BuildBusinessMaterialsRequest) -> BusinessMaterialSet
~~~

BuildBusinessMaterialsRequest 接受：

- exact RunInputBinding；
- VerifiedSourceInventory 的安全 source reader 与完整文件 disposition；
- ApplicationDiscovery 的全部 entries、handler locators、route 和 entry dispositions；
- 五张 ProgramGraph 及 graph gaps；
- ProvenCodeFacts 的 Fact/Proof/Gap view；
- BusinessFlows 的 Flow/Capsule/entry disposition；
- material profile 上限。

DepotHead 等 bounded fixture 可用于局部测试，但必须继承 repositoryCompletionEligible=false，不能借 M1 成功变成全仓完成。

输入投影示例：

~~~json
{
  "entry": {
    "entryId": "entry:create-replenishment",
    "method": "POST",
    "route": "/replenishments",
    "handler": "example.SupplyWorkflowController#create"
  },
  "graphObservations": [
    "handler calls ReplenishmentService.create",
    "service has an empty-lines rejection branch",
    "service calls ReplenishmentOrderMapper.insert"
  ],
  "factObservations": [
    "JAVA_GUARD_CONDITION: empty lines enter rejection branch",
    "JAVA_EXACT_CALL: mapper insert is called with order"
  ],
  "flowRef": "flow:create-replenishment",
  "flowGap": null
}
~~~

### 4.3 程序动作

M1 对每个发现入口按确定性 entryId 顺序：

1. 验证 entry、source reader、graph/fact/flow view 属于同一 RunInputBinding。
2. 优先从 Flow/Capsule 取入口、关键 guard、对象构造/转换、持久化或边界调用、返回结果。一个已选方法超过短片段上限时，保留方法开头，并在剩余额度内增加一至两个晚出现的、带 receiver 的直接调用行；这样模型能同时看到输入/早期条件和后续动作，但不会为此扫描新的文件或扩展调用图。
3. 用五图补足理解完整活动所必需的直接 callee、条件和数据承接；不会为了完整而遍历无界调用图。
4. 若无 Flow，从精确 handler locator 和冻结源码构造 ENTRY_SOURCE_FALLBACK；保留 flowGap。
5. 在一次 BusinessMaterialSet 内统一分配 SourceRef；同一个 ref 只能指向一处固定 file/lines/snippet，多个活动引用同一片段时复用这个 ref，绝不让每个包各自从 S1 重新编号后再混合。
6. 为每个 ref 保存 file、startLine、endLine、snippet；重读验证一致。
7. 对已经选中的完整 Java 方法生成有上限的多样化语法观察：先保留输入和状态/持久化形态调用，再保留关键条件、条件后的调用和终止。即使一个中段 `set...` 或 `update...` 调用没有进入少量原文窗口，也可以作为“源码调用”短观察出现。原文引用仍只包含少量精确窗口，观察不扩大选取范围。随后生成只含 local ref、snippet、通用技术观察和面向业务阅读的 limitations 的 ModelActivityPacket；Flow、Gap、material 等内部编号仅保留在程序侧记录中。
8. 对入口写 ANALYZED_MATERIAL、MATERIAL_WITH_GAPS 或 NOT_MATERIALIZED；后两者 reason 必填。
9. 完整 material set 与 coverage 一次保存为 business-materials.jsonl，成功后才允许 M2 发起首次模型调用。

技术观察使用通用语法语义，例如“构造 ReplenishmentOrder”“条件为明细非空”“调用 insert”。Java 不根据 order、receipt、approval 等字符串建立业务词典，也不编 businessPurpose。

### 4.4 模型动作

无。M1 不调用任何 Provider，不生成自然业务结论，不把模型当 source locator。

### 4.5 输出示例

`business-materials.jsonl` 交错保存两类 canonical JSONL record：每个 `BUSINESS_MATERIAL`
record 同时保留程序侧来源映射与模型实际读取的精简包；每个 `ENTRY_COVERAGE` record
说明一个发现入口是否已有材料或为何没有。这样首次模型调用前可以直接审阅 exact model
packet，后续失败也不会丢失入口分母。

程序保存的 `BUSINESS_MATERIAL`：

~~~json
{
  "recordType": "BUSINESS_MATERIAL",
  "materialId": "material:create-replenishment",
  "entryIds": ["entry:create-replenishment"],
  "materialMode": "FLOW_PREFERRED",
  "context": "HTTP handler 接收 ReplenishmentCommand 并调用 service。",
  "technicalObservations": [
    "command.lines().isEmpty() 为 true 时抛出 IllegalArgumentException",
    "调用 ReplenishmentOrder.from(command.lines())",
    "调用 SupplyWorkflowMapper.insertReplenishmentOrder(order)",
    "返回 order.id()"
  ],
  "sourceRefs": [
    {
      "ref": "S1",
      "file": "src/main/java/example/ReplenishmentService.java",
      "startLine": 21,
      "endLine": 23,
      "snippet": "if (command.lines().isEmpty()) {\n    throw new IllegalArgumentException(\"lines required\");\n}"
    },
    {
      "ref": "S2",
      "file": "src/main/java/example/ReplenishmentService.java",
      "startLine": 24,
      "endLine": 26,
      "snippet": "ReplenishmentOrder order = ReplenishmentOrder.from(command.lines());\nreplenishmentOrderMapper.insert(order);\nreturn order.id();"
    }
  ],
  "flowRefs": ["flow:create-replenishment"],
  "technicalProofRefs": ["proof:guard", "proof:insert-call"],
  "limitations": [
    "源码没有说明调用者岗位",
    "静态源码不证明某次持久化成功"
  ],
  "modelPacket": {
    "context": "已发现 HTTP 入口 POST /replenishments。请仅依据本包片段和观察，解释其局部业务活动。",
    "technicalObservations": ["条件检查后构造订单对象", "调用静态可见的保存边界"],
    "allowlistedRefs": [
      {"ref":"S1","snippet":"if (command.lines().isEmpty()) { throw new IllegalArgumentException(\"lines required\"); }"},
      {"ref":"S2","snippet":"ReplenishmentOrder order = ReplenishmentOrder.from(command.lines()); replenishmentOrderMapper.insert(order); return order.id();"}
    ],
    "limitations": ["源码没有说明调用者岗位", "静态源码不证明某次持久化成功"]
  }
}
~~~

technicalProofRefs、materialId、flowRefs 和精确 Gap 留在程序侧，用于可选技术查看；ModelActivityPacket 不包含它们，也不包含 file、line、hash、run identity 或 Provider controls：

~~~json
{
  "context": "已发现 HTTP 入口 POST /replenishments。请仅依据本包片段和观察，解释其局部业务活动。",
  "technicalObservations": [
    "command.lines().isEmpty() 为 true 时抛出 IllegalArgumentException",
    "调用 ReplenishmentOrder.from(command.lines())",
    "调用 SupplyWorkflowMapper.insertReplenishmentOrder(order)",
    "返回 order.id()"
  ],
  "allowlistedRefs": [
    {"ref":"S1","snippet":"if (command.lines().isEmpty()) { throw new IllegalArgumentException(\"lines required\"); }"},
    {"ref":"S2","snippet":"ReplenishmentOrder order = ReplenishmentOrder.from(command.lines()); replenishmentOrderMapper.insert(order); return order.id();"}
  ],
  "limitations": [
    "源码没有说明调用者岗位",
    "静态源码不证明某次持久化成功"
  ]
}
~~~

同一文件中相应入口的 `ENTRY_COVERAGE` record：

~~~json
{
  "recordType": "ENTRY_COVERAGE",
  "entryId": "entry:create-replenishment",
  "disposition": "ANALYZED_MATERIAL",
  "materialId": "material:create-replenishment",
  "reasonCode": null
}
~~~

### 4.6 下游直接使用

| 输出 | ActivityExplainer 怎样直接使用 |
| --- | --- |
| persisted modelPacket | 精确重现模型读到的干净材料，不需要重读图 |
| allowlisted ref + snippet | 为业务活动和段落提供最低来源 |
| limitations | 形成集中 questions/scopeLimitations |
| entryIds/materialMode | 回写全入口 coverage，允许 noFlow |
| program-only SourceRef map | 校验模型 ref，并供最终报告点击 |
| technicalProofRefs | 只在技术详情查看；不作为业务解释强制门 |

### 4.7 Gap、fatal、停止与复用

- no Flow 但 handler 可安全读取：MATERIAL_WITH_GAPS + ENTRY_SOURCE_FALLBACK，可继续 M2。
- source locator 可读但超 material 上限：NOT_MATERIALIZED / SOURCE_MATERIAL_OVER_BUDGET，M2 对该入口 0 调用。
- unsupported syntax 影响一段但其余活动仍连贯：MATERIAL_WITH_GAPS，limitation 明确受影响语义。
- 找不到 handler、片段越出 inventory、重读不一致、run binding 混用或 coverage 漏入口：fatal，停止 Step 06。
- 同 inputFingerprint 可复用已完成 business-materials.jsonl；任何有效输入、Prompt/generation/module 版本变化都不复用。

M1 不把超大材料切成几个缺上下文片段后分别宣称完整活动。只有每个包都保留目的所需的入口、条件、对象形成和结果上下文时才允许分成多个 activity package；entry coverage 必须注明 ANALYZED_WITH_GAPS。

### 4.8 开发与测试

测试只跨 build Interface，不断言私有 selector：

1. 三入口合成样例各得到一份连贯 material，ref 行号/snippet 可重读。
2. noFlow 入口从同 snapshot handler + 图得到 ENTRY_SOURCE_FALLBACK，并保留 flowGap。
3. technical Proof 缺失不阻止 located business material；错误 technical Proof 声明仍由 Step 04 拒绝。
4. 模型视图不含 file/line/hash/Proof/provider/control。
5. 超预算入口 0 model packet 且 coverage 有具体原因。
6. 不同 inputFingerprint 拒绝旧 checkpoint。
7. 每个 discovered entry 恰有一个 coverage disposition。

自动化测试无 Provider、网络、客户构建或来源扫描。

## 5. M2 ActivityExplainer

### 5.1 为什么需要

BusinessMaterial 仍是技术材料。把业务目的、对象、完整步骤、代码定义结果和问题写成业务语言，需要语言理解；把这套推理硬编码到 Java 会产生行业词表、固定状态机和脆弱规则。ActivityExplainer 将这种变化集中在一个 Provider seam 后，并让一次完整 review 检查整项活动。

### 5.2 Interface 与输入示例

生产包固定为 `org.sourceanalysis.app.analysis.interpretation.activity`。调用者只看到一个深
Module Interface：

~~~java
public final class ActivityExplainer {
    ActivityExplanationResult explain(ExplainActivitiesRequest request);
}

public record ExplainActivitiesRequest(
    BusinessMaterialBuildResult materials,
    ActivityExplanationProfile profile) {}

public record ActivityExplanationProfile(
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxActivitiesPerMaterial,
    int maxValuesPerField,
    int maxTextCharsPerValue) {}
~~~

`materials` 必须同时包含 M1 已验证的 typed `BusinessMaterialSet` 和已经成功安装的
`business-materials.jsonl` checkpoint；没有 checkpoint 时不得调用 Provider。`profile`
只有确定性容量限制，不放 prompt、Provider 凭据、API key 或文件路径。Prompt 由版本化
classpath resource 提供，fingerprint 计算实际 prompt 字节而不只计算版本字符串。

模型 seam 位于 `org.sourceanalysis.app.adapter.provider`，供本步骤和后续业务 Module
共用；它之所以成立，是因为确有 scripted 与 Codex Subscription 两个 adapter：

~~~java
public interface StructuredModelProvider {
    StructuredModelResponse generate(StructuredModelRequest request);
}

public record StructuredModelRequest(
    String taskId,
    String taskKind,
    String systemInstructions,
    ImmutableBytes untrustedInputJson,
    ImmutableBytes outputJsonSchema,
    int maxOutputBytes) {}

public record StructuredModelResponse(
    ImmutableBytes responseJson,
    ModelRuntimeIdentity runtimeIdentity) {}
~~~

`generate` 被调用即表示本次请求已 started；抛异常就是 started 后失败。登录、容量与
Codex 状态目录 preflight 在调用前完成，不给 Interface 增加 retry/resume 方法。自动测试
的 `ScriptedStructuredModelProvider` 按 `(taskKind, materialId)` 的冻结顺序消费响应，保存
收到的 request 供断言；顺序不符、响应耗尽或还有未消费响应都以
`SCRIPTED_PROVIDER_MISMATCH` 失败，不访问网络或文件系统。

输入包含一个或多个 ModelActivityPacket、对应程序侧 ref allowlist 和 activity generation
profile。`materialId`、`entryIds`、Flow/Gap/Proof 只存在于外围 `BusinessMaterial`；
`ModelActivityPacket` 本身刻意不含这些程序身份。Task compiler 从外围取
`materialId` 作为任务 owner，但发给模型的业务材料仍只有 context、观察、短 ref/snippet
和限制。单包的 Provider data JSON 示例：

~~~json
{
  "context": "已发现 HTTP 入口 POST /goods-receipts。请仅依据本包片段和观察，解释其局部业务活动。",
  "technicalObservations": [
    "GoodsReceipt 由 replenishmentOrderId、receivedQuantity 和 unitPrice 构造",
    "调用具有明确 INSERT 语境的 mapper 方法"
  ],
  "allowlistedRefs": [
    {"ref":"S4","snippet":"GoodsReceipt receipt = GoodsReceipt.from(command.replenishmentOrderId(), command.receivedQuantity(), command.unitPrice()); goodsReceiptMapper.insert(receipt);"},
    {"ref":"S5","snippet":"insert into goods_receipt (id, replenishment_order_id, received_quantity, unit_price) values (#{id}, #{replenishmentOrderId}, #{receivedQuantity}, #{unitPrice})"}
  ],
  "limitations": [
    "源码没有说明实际货物是否到达",
    "源码没有说明岗位"
  ]
}
~~~

DRAFT 请求的 `taskKind` 固定为 `ACTIVITY_DRAFT`。REVIEW 固定为
`ACTIVITY_REVIEW`，其 data JSON 必须再次包含同一个 clean package，并在
`actualDraft` 中放入程序刚刚严格解析和 canonicalize 后的完整 DRAFT；不能只传摘要、
activity key 或差异。DRAFT 与 REVIEW 的响应都严格使用[中文提示词合同](../references/semantic-interpretation-prompts.md)
中相同的完整顶层结构：

每次请求同时携带由程序按本次 profile 和 allowlisted short ref 动态生成的 JSON Schema：它
列出唯一顶层 `activities`、每项活动的全部必填业务字段、三个 certainty 值、数组/文本容量，
并把 `sourceRefs` 限定为本材料包已有的短 ref。Schema 用来让模型在生成时就知道完整输出形状；
Java 仍在响应回来后重新执行同样的字段、容量、引用和身份检查，不能把外部 Schema 当作信任边界。

~~~json
{
  "activities": [
    {
      "activityLocalId": "activity-1",
      "name": "记录收货",
      "businessPurpose": "把与补货单关联的收货数量和单价保存为收货记录。",
      "participants": [],
      "businessObjects": ["补货单", "收货记录"],
      "triggerOrInput": ["补货单标识", "收货数量", "单价"],
      "conditions": [],
      "activitySteps": ["读取收货数据", "生成收货记录", "保存收货记录"],
      "codeDefinedResults": ["系统生成并保存与补货单标识关联的收货记录"],
      "businessRules": [],
      "formulasOrMetrics": [],
      "terms": ["收货记录"],
      "certainty": "DIRECT_CODE_BEHAVIOR",
      "sourceRefs": ["S4", "S5"],
      "questions": ["哪类岗位或系统负责确认收货？"],
      "scopeLimitations": ["静态源码不证明某次保存成功"]
    }
  ]
}
~~~

严格规则如下：顶层只允许 `activities`；activity 的上述 15 个字段必须全部
存在，不认识的字段失败；所有列表必须是数组，允许为空；`name/businessPurpose`、至少
一个 `activitySteps`、至少一个 `codeDefinedResults`、至少一个 `sourceRefs` 必须非空；
`certainty` 只能是 `DIRECT_CODE_BEHAVIOR/REASONABLE_INFERENCE/NEEDS_CONFIRMATION`；
`activityLocalId` 在本响应内非空且唯一；每个 ref 必须属于该材料的 allowlist。程序从外围
BusinessMaterial 复制 entryIds，并以
`materialId + activityLocalId + canonical reviewed activity` 分配稳定 `activityId`；模型
不能创建最终 ID 或修改 owner。

### 5.3 程序动作

1. 对每个 material 先做容量 preflight；失败则 0 call 并写 NOT_ANALYZED_BUDGET。
2. 生成 Activity DRAFT Prompt；将 material 当不可信数据分隔。
3. 发起恰一次 DRAFT，记录 started request 和原始 response。
4. 解析 JSON，拒绝 allowlist 外 ref、路径/行号/hash/Proof 等禁写字段。
5. 将完整 material 和完整实际 draft 交给一次 REVIEW。
6. 解析 review 返回的完整替换 Activity JSON；不处理 patch、逐 key decision 或第三轮建议。
7. 校验每个活动字段、sourceRefs、material ownership 与 entry coverage；分配稳定 activityId。
8. 每个 material REVIEW 完成即先原子保存该 material 的已审结果和两次调用 receipt，再开始下一个 material；后续包失败不回滚已完成包。
9. 全部分母闭合后安装聚合 `activity-explanations.jsonl` 和 `activity-coverage.json`，并返回 `ActivityExplanationResult`。聚合文件不保存 raw prompt/response；这些只在受保护的调用 receipt 中。

程序不证明自然语言是否逐原子被 Proof 蕴含，也不按行业词典修改内容。它只验证结构、来源、禁写字段和覆盖。

### 5.4 Luna/high 动作

DRAFT 将整个 material 解释为零个或多个完整活动，使用开放词汇给出：

- name、businessPurpose、participants、businessObjects；
- triggerOrInput、conditions、activitySteps、codeDefinedResults；
- businessRules、formulasOrMetrics、terms；
- certainty、sourceRefs、questions、scopeLimitations。

REVIEW 检查整个活动是否漏业务含义、是否把清楚保存行为过度收窄、是否把静态行为冒充某次运行成功，以及是否虚构岗位、制度、唯一性、库存/记账结果、次数或公式。它返回完整修订结果，不返回 KEEP/NARROW/DROP 状态机。

### 5.5 输出示例

~~~json
{
  "activityId": "activity:record-receipt",
  "materialId": "material:record-receipt",
  "entryIds": ["entry:record-receipt"],
  "name": "记录收货",
  "businessPurpose": "把与补货单关联的收货数量和单价保存为收货记录。",
  "participants": [],
  "businessObjects": ["补货单", "收货记录"],
  "triggerOrInput": ["补货单标识", "收货数量", "单价"],
  "conditions": [],
  "activitySteps": [
    "读取补货单标识和收货数据",
    "生成收货记录",
    "保存收货记录"
  ],
  "codeDefinedResults": [
    "系统生成并保存与补货单标识关联的收货记录"
  ],
  "businessRules": [],
  "formulasOrMetrics": [],
  "terms": ["收货记录"],
  "certainty": "DIRECT_CODE_BEHAVIOR",
  "sourceRefs": ["S4", "S5"],
  "questions": [
    "记录收货是否要求补货单处于特定状态？",
    "哪类岗位或系统负责确认收货？"
  ],
  "scopeLimitations": [
    "代码中的收货记录不证明物理货物在某次运行中实际到达"
  ]
}
~~~

### 5.6 下游直接使用

ProcessExplainer 直接使用完整 activities、对象、条件、步骤、结果、规则、公式、问题和 refs 做召回与过程理解；它不能只拿 name/summary。BusinessReportPublisher 最终也会收到这些完整字段，因而不需要靠方法名补意义。

activity-coverage.json：

~~~json
{
  "discoveredEntryCount": 3,
  "analyzedEntryIds": [
    "entry:create-replenishment",
    "entry:record-receipt",
    "entry:create-payable-bill"
  ],
  "analyzedWithGaps": [],
  "notAnalyzed": [],
  "semanticDeliveryStatus": "READY_FOR_PROCESS_EXPLANATION"
}
~~~

`ActivityExplanationResult` 只携带 `ReviewedActivitySet`、`ActivityCoverage` 和最终 checkpoint
reference。`activity-explanations.jsonl` 每行恰好一个 `REVIEWED_ACTIVITY`，包含程序分配的
activityId、materialId、entryIds 和上述完整已审字段；没有活动的 material 不制造空活动。
`activity-coverage.json` 是唯一分母账：每个 M1 entry 和 material 恰好出现一次，状态只为
`ANALYZED/ANALYZED_WITH_GAPS/NOT_ANALYZED`，并带 activityIds 与固定 reasonCodes。

### 5.7 Gap、fatal、停止与调用上界

每个 material package：

~~~text
capacity PASS -> one DRAFT + one REVIEW -> validate
capacity FAIL -> zero requests + NOT_ANALYZED_BUDGET
execution launch cap reached -> zero requests + NOT_ANALYZED_EXECUTION_CAPACITY
~~~

- DRAFT started 后 transport/schema/runtime failure：当前 Step 06 fatal，不 retry/switch/fallback。
- DRAFT JSON 非法：仍不伪造 draft；current execution fatal，不自动补修。
- REVIEW started 后失败或 review JSON 非法：fatal；保留安全调用诊断和 M1 checkpoint。
- 模型返回 activities=[]：程序记录固定原因 MODEL_NO_ACTIVITY 并把 entry 记 NOT_ANALYZED；response 不需要发明 reason 字段，Step 07 可继续但 repository completion 不可伪装。
- unknown ref、模型生成 path/line/hash、缺 material ownership 或遗漏 entry：fatal。
- 业务岗位、制度或运行事实未知：不是 fatal，进入 questions/limitations。

固定错误和非错误处置：

| 条件 | 结果 | Provider calls |
| --- | --- | ---: |
| M1 entry 无 material | coverage=`NOT_ANALYZED/UPSTREAM_MATERIAL_UNAVAILABLE` | 0 |
| material 超模型输入预算 | coverage=`NOT_ANALYZED/NOT_ANALYZED_BUDGET` | 0 |
| 本次 material 启动上限已达 | coverage=`NOT_ANALYZED/NOT_ANALYZED_EXECUTION_CAPACITY` | 0 |
| DRAFT 非法 JSON/Schema | fatal `ACTIVITY_DRAFT_INVALID` | 1 |
| REVIEW 非法 JSON/Schema | fatal `ACTIVITY_REVIEW_INVALID` | 2 |
| unknown ref 或 owner 不一致 | fatal `ACTIVITY_SOURCE_SCOPE_INVALID` | 1 或 2 |
| runtime identity 与配置不符 | fatal `ACTIVITY_PROVIDER_IDENTITY_MISMATCH` | 1 或 2 |
| Provider 抛异常 | fatal `ACTIVITY_PROVIDER_FAILED_AFTER_START` | 1 或 2 |
| REVIEW 最终 `activities=[]` | coverage=`NOT_ANALYZED/MODEL_NO_ACTIVITY` | 2 |
| 0 discovered entry | 空 explanations，coverage=`NO_DISCOVERED_ENTRY` | 0 |

fatal 时不安装最终聚合 checkpoint，但 M1 checkpoint、此前完整 activity 包和已 started 调用
receipt 保留。当前 execution 不重放不确定调用；新 execution 只有在完整 inputFingerprint
相等时才可显式复用此前**已完成 REVIEW 且已校验**的 material 包。

所有 Activity 调用属于同一个 Reader Candidate。ReaderCandidateRound 2 只有用户针对 Round 1 明确问题再次授权时才可产生；它不是 REVIEW，也不是自动 retry。

### 5.8 开发与测试

Luna/xhigh 先通过 ActivityExplainer 的 public Interface 写以下最小 RED，不测试私有 parser、
prompt builder 或 checkpoint writer：

1. 单 material：恰好 DRAFT→REVIEW 两次，REVIEW 的 `actualDraft` 等于完整、实际、已解析 DRAFT；最终只保留 REVIEW 全量内容。
2. REVIEW 返回两项活动：稳定分配两个 activityId，完整字段和 ref 在重开 JSONL 后逐字段不丢失。
3. 两个 materials：第一项完成、第二项 Provider 失败；最终 aggregate 不发布，但第一项 package checkpoint 仍可重开。
4. 两个可读 materials、启动上限为一：只发生一对 DRAFT→REVIEW，剩余入口以
   `NOT_ANALYZED_EXECUTION_CAPACITY` 留在 coverage；不得发送第二个包。
4. DRAFT 非法 JSON 时恰好一次调用；REVIEW 非法、漏字段、未知字段或 unknown ref 时恰好两次并 fail closed。
5. 超预算 material、上游无 material 和 0 entry 都是 0 call，coverage 分母闭合且 reason 固定。
6. REVIEW task 不把外围 entry/Flow/Gap/Proof、文件路径、行号或 hash 放回 clean package。
7. 补货 construct+insert 和不同领域实验室材料的 scripted 完整响应都能通过，无 Java 行业 registry。
8. 同 material、prompt、配置和响应产生相同 activityId 与聚合 bytes；改 prompt 实际字节后不能复用旧 package。
9. pure checkpoint reopen/inspect 为 0 call；unexpected scripted task/order/extra response 失败。

Terra/xhigh 只实现这些 RED：一个顺序循环、一个严格 JSON parser、一个 Provider seam、每
material 一个完成 checkpoint 和一次最终聚合。禁止引入 finite-key registry、patch
review、第三轮、自动 retry、Provider fallback、同 run worker recovery 或业务词典。若现有
store 不能表达“每个完成 material 先保存”，只允许增加 Step 06 私有的 append-only package
checkpoint writer；不得改变 public RepositoryAnalysisAgent、Step 01–05 或跨 Step 身份。

真实 Luna/high 质量验证按小包、第二领域、整仓推进。固定 jshERP 已完成两个不同领域小包：DepotHead 的批量审核/反审核与 AccountHead 的财务主表及明细新增。两者都只向模型提供两个短 excerpt 和短 ref，不提供路径、行号、SHA 或 Proof；都完成 DRAFT+REVIEW，并输出了目的、对象、条件、步骤、代码定义结果、问题和范围限制。此前两次失败的根因是受限环境禁止 Codex CLI 写入本机登录状态数据库；在允许该已有本机状态运行后，同一 CLI、Luna/high 与 JSON Schema 路径均成功。小包证明 Prompt/输入/响应链可用，不等于第二领域后的跨入口过程或整仓质量验收；后续扩大前仍需先审阅每个小包。

## 6. SourceRef 与技术 Proof 的并存

SourceRef 是业务说明的最低可追溯来源：

~~~text
business paragraph/activity -> S7 -> frozen file + exact lines + snippet
~~~

技术查看仍可走：

~~~text
technical Fact -> Proof -> Evidence -> frozen bytes
~~~

两条路可引用同一源码，但目的不同。业务路线不把 SourceRef 伪称 Proof，也不要求每个自然语言原子都建立 Proof；技术 exact-call/guard 声明仍必须遵守 Step 04 现有 Proof 门槛。最终报告只展示简单 S7 链接，详细 Proof 保留在技术视图。

## 7. 入口覆盖与边界情况

每个 ApplicationDiscovery entry 必须落在以下一个结果：

| 状态 | 含义 | Step 07 |
| --- | --- | --- |
| ANALYZED | 至少一个完整 reviewed activity | 可进入 process grouping |
| ANALYZED_WITH_GAPS | 有完整 activity，同时有 noFlow/unsupported/局部材料缺口 | 可进入并保留 limitation |
| NOT_ANALYZED | 无法形成完整 activity，reason 必填 | 保留覆盖，不能用切片冒充 |

0 入口时 Activity 调用为 0，activity-coverage 说明 NO_DISCOVERED_ENTRY；有材料无 Flow 时允许 ANALYZED_WITH_GAPS；超预算不做碎片式局部通过。

## 8. 旧内部 Module 迁移

| 旧名 | 目标处置 |
| --- | --- |
| SemanticMaterialCompiler | 合入 BusinessMaterialBuilder |
| SemanticPacketCompiler | 合入 BusinessMaterialBuilder |
| LocalSemanticInterpreter | 由 ActivityExplainer 取代 |
| ProcessContextRetriever | 移到 Step 07 并合入 ProcessExplainer |
| ProcessSemanticInterpreter | 移到 Step 07 并合入 ProcessExplainer |
| FlowInterpretationPublisher | 退役为独立 Module；改写三个简单 checkpoint |
| R0 registry proposal、finite key、R1/R2/P1/P2、逐 record disposition | 退出目标；旧实现仅迁移输入 |

不创建 parallel POC package、alias reader、dual writer 或第二个 public Interface。

## 9. 当前实现成熟度

- Step 01–05 的技术能力按各自文档保留。
- 当前 interpretation package 仍包含 registry proposal、finite-key 和旧 process carrier；它们不是本设计目标。
- BusinessMaterialBuilder 为 PARTIAL：它可选择两种同一冻结运行的技术输入。已有 Step05 `BusinessFlows` 时，它重新打开同一冻结源码与发现入口，为已编译 Flow 投影 Capsule 片段、技术观察和全局短 SourceRef；尚未有 Step05 时，它直接重新打开匹配的 Step01 已验证源码与 Step02 已发现入口，从安全定位的 Handler 生成 `ENTRY_SOURCE_FALLBACK`，并保留 `FLOW_NOT_AVAILABLE`。两种输入都由同一个 Builder 生成同一份 `business-materials.jsonl`、全入口处置和干净的 nested `modelPacket`；后者没有路径/行号/hash/Proof/图内部 ID、Flow ID、Gap ID 或 material ID。Builder 现在还会仅从已经选中的完整 Java 方法投影参数、`if` 条件、有 receiver 的直接调用及 `return`/`throw`，例如“源码输入：方法 approve 接收参数 status”“源码条件：status == null”“源码调用：approvalClient.record(status)”。即使中段直接调用不在少量原文窗口中，它仍可以作为有上限的短观察保留；原文窗口只保留方法开头和按剩余额度选出的最多两个后续直接调用行。它不为这些语法事实命名业务活动、角色或行业对象。
- 已完成一次固定 jshERP 的 direct-entry 材料规划：337 个已发现 HTTP 入口对应 337 份
  `ENTRY_SOURCE_FALLBACK` 材料，全部以 `FLOW_NOT_AVAILABLE` 明示技术限制；其中 281 份有一个或多个
  唯一、直接的同仓库 Java 调用目标片段，最多四个来源片段；零 Provider 调用。多 Maven module 中的
  同名类型不会被混入上下文，而是保留 handler-only 材料。该测试还证明材料 policy 在 Source publication
  前进入 frozen bundle，且模型包不携带文件路径、行号、哈希或 Proof。它只验证可查看的整仓输入分母，
  不能代表活动解释质量或整仓业务结论。该次已保存材料在代码语法摘要加入前生成，不能作为当前 Builder
  质量验收；需要以新 Builder 对小包先验收，再重新规划整仓材料。
- Builder 当前尚未把五图作为 no-Flow 材料的补充选择器、没有完成显式 inputFingerprint/reuse 入口，也尚未由一个正式 Step 06 publisher 将材料与后续活动结果合成三个 checkpoint。固定 jshERP 的最新 materials-only 预检已生成 337 份 `ENTRY_SOURCE_FALLBACK` 材料；被检查的 DepotHead 包现在含请求输入、审核/反审核条件、状态更新、Mapper 更新、库存更新和日志调用等中性观察，但这只说明模型将读到什么，不等于业务质量验收。零入口已由独立持久化 fixture 验证：合法空分母产生空 `business-materials.jsonl`，而不产生虚构材料。
- ActivityExplainer 为 PARTIAL：已通过 injected scripted Provider 完成每个可读材料恰一次 DRAFT 加一次完整 REVIEW，严格保留完整活动字段、拒绝 allowlist 外来源和模型注入的路径/程序身份字段。DRAFT/REVIEW 使用不同的版本化 classpath 中文 Prompt，并有 Provider-boundary 测试保证它们包含不可信输入隔离、短 ref 限制和相应业务任务。模型输入继续没有路径、行号、哈希、Proof、Flow、Gap 或 material ID。超预算材料不会调用 Provider，而是以 `NOT_ANALYZED_BUDGET` 留在 entry coverage；空模型活动以 `MODEL_NO_ACTIVITY` 留在 coverage。全部分母闭合后，它会通过既有 receipt-last store 安装并可重开 `activity-explanations.jsonl` 和 `activity-coverage.json`；该 checkpoint 只含已审业务结果与覆盖，不含 Prompt 或原始模型响应。它尚未逐材料立即保存、保存调用 receipt 或形成 Step 06 最终 publication。
- 已实现：`ActivityExplanationCheckpointReader`从上述两个 checkpoint 文件 fresh reopen 全部
  `ReviewedActivity`和逐入口 coverage，重验模块地址、file/schema/type、JSON/JSONL shape、coverage
  与 activity 的双向闭合及 partial 状态。读取不调用 Provider、不读源码，也不改变模型已经审阅的
  业务文字；后续过程解释或运行入口可复用它，避免因“查看已完成活动”再次调用模型。
- 已实现一个共享的 `CodexSubscriptionStructuredProvider`：它只使用本机已经登录的 Codex CLI，预检登录状态，以 `gpt-5.6-luna`/`high`、read-only sandbox、ephemeral session 和输出 JSON Schema 发起一次请求；Java 不读取、保存或回退 API key。外部有效 JSON 会在 Provider boundary 严格解析并规范化为内部 canonical JSON，避免把模型键顺序误判为语义错误。失败的 CLI stdout/stderr 只在私有临时目录中被短暂读取并归入有限类别，随后删除；原始诊断、提示词和响应不会进入 checkpoint、receipt 或 progress。command seam 的自动测试与两个显式 gated、人工材料小包均已通过。当前一次自动 DepotHead 包的 DRAFT 在结构化响应前返回 `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`，按规则没有重试；因此它既不是自动材料质量通过，也不是 Prompt/Schema 兼容性证明。未来单独授权的新包需要先改善非秘密失败回执，再作一次新的质量任务。
- 已实现：`PersistedBusinessRunExecutor`以已持久化的 Step 05 `BusinessFlowsReference`为优先输入，
  或以匹配的 Step01 已验证源码与 Step02 已发现入口为直接输入，调用 Builder 与 Explainer 并把
  它们的 checkpoint 交给后续业务模块。后者保留 `ENTRY_SOURCE_FALLBACK` 与
  `FLOW_NOT_AVAILABLE`；它不接收文件路径，不重新扫描冻结源码，也不重建技术 Flow；自动测试仅使用
  scripted Provider。`RepositoryAnalysisRunCoordinator`当前已从一个内部 runId 调用这个 direct-entry
  continuation，因此普通报告不再被 Step03–05 阻塞；`SourceAnalysisApplication`现在可把同一 configured Agent
  与最小 CLI 接在一起：CLI 只接受已登记 sourceRegistrationId 排队、或按已有 run 执行最终目标，不接收源码路径。
- 已实现 materials-only target：它作为扩大模型调用前的独立预检，只执行当前 Step01/02 与
  `BusinessMaterialBuilder`，保存 `business-materials.jsonl` 和 per-entry coverage，不进入
  `ActivityExplainer`、`ProcessExplainer`或`BusinessReportPublisher`。它不是一份空报告；下游 checkpoint
  因此不存在，`render`和非材料 artifact 查询都会明确拒绝。CLI 的 `plan-materials --run <id>` 映射到该
  target。调用方检查材料后另起一个 final-document run，并选择正的 `maxMaterialsToStart`，不重放或续跑原
  preflight run。
- 本文合成补货故事只证明设计字段可连续，不是 jshERP 运行结果。

完整 Prompt 见 [业务解释与九章写作中文 Prompt](../references/semantic-interpretation-prompts.md)，完整端到端数据见 [业务优先 walkthrough](../examples/semantic-framework-walkthrough.md)。
