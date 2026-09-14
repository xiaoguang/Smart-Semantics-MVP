# 用已有财务查询样本走完八步

> 本页保留此前图路径的实测与业务推演；新的JDT/JavaParser取材方向及实际注册Service全文见[新引擎贯穿例子](java-code-engine-walkthrough.md)。下面历史文件/计数不表示新插件已经实现。

本页先核对真实保存材料，再逐步推演目标链路能否把代码交给模型并形成业务报告。推演没有调用模型、重建图或生成新的 runtime 产物。历史财务 S567/S568 run、较新的真实四入口 S487/S722/S731/S898 包、目标修订结果和合成业务故事分别标注，不能拼成一次已完成运行。

## 1. 固定样本与查读位置

源码固定到 jshERP commit `8c30ce7861570458920175e200bb2a6442713580`，入口为 GET /accountHead/getFinancialBillNoByBillId，handler 为 AccountHeadController#getFinancialBillNoByBillId。它是一个小型查询，适合检验最基本的传参、边界与返回能否真正连起来。

已核对的两个保存位置：

- 图/Fact run：`.workspace/fixed-jsherp-offline-acceptance-retry-19.PaHsJ0/stores/runs/analysis-run--c10be6c086fec92ca85081bf9856e7ed1cd7f3ad617e1e90d7fc9ffaec89286d/`。有五图和选定 Facts，没有 Step05 正式 publication。
- 材料 run：`.workspace/fixed-material-context-plan.1YChkI/stores/runs/analysis-run--54d866fa27f7b4fef1e272a404b09e9b03ba104945caafe717c69ff9cdd79530/steps/06-flow-interpretation/modules/10-business-material-builder/business-materials.jsonl`，第 314 行是本文的 BUSINESS_MATERIAL。

这些都是 module 下已有本地忽略 workspace 的审计位置，不是 public request 路径。本页不创建它们、不改变它们，也不把不同 run 的 refs/roots 合并成一个已保存正式 Flow。第二个**历史** JSONL 总计 674 records，其中 337 BUSINESS_MATERIAL、337 ENTRY_COVERAGE；不是 674 个业务材料。更新后的独立全仓材料 run 为 107 个包覆盖 339 个入口，见 [Step06 当前实现](../analysis-steps/06-flow-interpretation.md#7-当前实现与最小修改)；两次计数来自不同运行，不互相改写。

第 314 行真实状态为 ENTRY_SOURCE_FALLBACK、flowRefs=[]、technicalProofRefs=[]，sourceRefs 是 S567/S568。其三条 technicalObservations 仅说明已定位入口、handler 和直接调用方法，尚未保存完整参数/控制/返回链。

## 2. 实际源码：已有材料确实包含什么

S567 来自 `jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java:181–196`。下面是已保存片段原文：

~~~java
    @GetMapping(value = "/getFinancialBillNoByBillId")
    @ApiOperation(value = "根据编号查询单据信息")
    public BaseResponseInfo getFinancialBillNoByBillId(@RequestParam("billId") Long billId,
                                              HttpServletRequest request)throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<AccountHead> list = accountHeadService.getFinancialBillNoByBillId(billId);
            res.code = 200;
            res.data = list;
        } catch(Exception e){
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }
~~~

S568 来自 `jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java:442–444`：

~~~java
    public List<AccountHead> getFinancialBillNoByBillId(Long billId) {
        return accountHeadMapperEx.getFinancialBillNoByBillId(billId);
    }
~~~

其他已知源码位置为 AccountHeadMapperEx.java:44–45 的 Mapper 声明/@Param("billId")，以及 AccountHeadMapperEx.xml:150–155 的静态 select。XML 关联 account_head/account_item 并按 bill_id 过滤；本页不把该静态代码说成查询已执行，也不伪造原始 XML 摘录。已有 S567/S568 材料并不包含那两处原文，目标 Step05 若需附加，应从同快照已有 locator 读取真实片段。

这里的 res.code 是 BaseResponseInfo 响应体字段，不是 HTTP 状态码证明。List 返回也不能推出非空结果、唯一财务单号或某次查询成功。

## 3. Step01 与 Step02：来源、入口已经明确

Step01 已有完整 719 文件冻结来源。核对的输入是该 commit 的固定 bytes，不能换 master、工作树或再 fetch。为本文定位源码只需要复用现有清单/行索引和验证后的文件，不必为后面的每个对象重复验证整仓。

Step02 已发现该 GET 入口及 billId 参数。历史材料规划的全部 337 个发现入口都在该次 coverage 分母中；这个数只说明那次运行范围。当前 `methodCondition` 已正确表示省略/空 method 的 unrestricted RequestMapping，较新的独立运行发现 339 个入口；不能回写历史产物，也不能为了保持旧数而漏掉合法端点。

到此，Java 知道“入口在哪、参数叫什么”，还没有给它贴“财务查询”业务标签。

## 4. Step03：已有图的参数链和局限

实际调用图中，该入口有 2 CALL_SITE、2 CALL_TARGET、2 CALL_RETURN。参数关系跨数据图保存：

~~~text
Controller formal billId
  --DEF_USE--> Controller actual billId
  --ARGUMENT_TO_PARAMETER--> Service formal billId
  --DEF_USE--> Service actual billId
  --ARGUMENT_TO_BOUNDARY--> Mapper invocation argument 0: billId
~~~

这不是“Controller 文件后面放一个 Service 文件”的猜测。已有 graph refs 提供了前段绑定及后段边界，但最后一步不是进入 Mapper 具体 body 的 ARGUMENT_TO_PARAMETER。Mapper 声明中的 @Param 可以作为旁侧声明资料，不能代替该边界所缺的运行/SQL dataflow。

该入口当前 CFG 只有 8 nodes：3 个 Controller blocks、1 个 Service block、ENTRY 和 3 个 terminals；没有独立 TRY/CATCH/GUARD。原文明确显示 try/catch，目标上下文应同时保留这项 SOURCE_CONTEXT，而不是说图上已有细分异常路径，也不是因图不完整就删掉异常代码。

## 5. Step04：已有三条 Fact 增强什么

已有 2 条 JAVA_EXACT_CALL 分别描述 Controller→Service 与 Service→Mapper 静态 target，另 1 条 JAVA_BOUNDARY_INVOCATION 描述 Mapper 边界及参数。每条继续遵守完整 required atoms/Proof；不能为通过此例弱化 exact 规则。

但 Fact 并没有提供整段业务解释、完整 try/catch 或实际 SQL 执行。它们是可附着的技术增强；若某条 Fact 没有准入，Step05 仍可展示可安全定位的 S567/S568 和已知图关系。不能用这一小组 Facts 过滤掉所有其他有用信息。

普通 candidate reader/publisher 目前已经直接复用 owner 构建的结果，不再通过枚举、compile/project 重放来增加代码上下文；磁盘 identity/hash/schema/ref/basis 检查保留。这个已完成的减法不需要在本次清理中重做。

## 6. Step05：需要补齐的一个连贯对象

**当前状态：此图/Fact run 没有 Step05 正式 publication。以下为目标 dry-run，短 ID 不属于保存 wire。** 最小 EntryContext 合同已在 [Step05](../analysis-steps/05-business-flows.md#41-可实现的最小-entrycontext-合同) 冻结；不是另一个 chain Module。

从上述已有材料组织出的阅读对象应包含：

~~~json
{
  "entry": "E1",
  "flowRef": null,
  "entrySignature": "getFinancialBillNoByBillId(Long billId, HttpServletRequest request)",
  "callSequence": [
    {"call": "Controller → Service", "actual": "billId", "formal": "Service.billId",
     "bindingKind": "ARGUMENT_TO_PARAMETER", "resolution": "EXACT", "sourceRefs": ["S567", "S568"]},
    {"call": "Service → Mapper declaration", "ordinal": 0, "actual": "billId", "formal": null,
     "bindingKind": "ARGUMENT_TO_BOUNDARY", "resolution": "EXACT", "sourceRefs": ["S568"]}
  ],
  "sourceControl": [
    {"kind": "TRY", "basisKind": "SOURCE_CONTEXT",
     "sequence": ["list=Service(billId)", "res.code=200", "res.data=list"], "sourceRefs": ["S567"]},
    {"kind": "CATCH", "basisKind": "SOURCE_CONTEXT",
     "sequence": ["log exception", "res.code=500", "res.data=获取数据失败"], "sourceRefs": ["S567"]}
  ],
  "sourceReturns": [
    {"expression": "return Mapper(billId)", "sourceRefs": ["S568"]},
    {"expression": "return res", "sourceRefs": ["S567"]}
  ],
  "codeFragments": ["the exact S567 shown above", "the exact S568 shown above"],
  "limitations": ["No formal strict Flow publication", "Current CFG does not distinguish try/catch",
                  "No data-flow proof through Mapper implementation or actual database execution"]
}
~~~

这段 JSON 的 codeFragments 是本页已经逐字展示片段的指示，不可直接发送 Provider。正式 EntryContext.fragments 必须内嵌实际 SourceExcerptV1 与原文；模型序列化包必须把两段完整代码一起放入，不能只传短 refs/图链接要求模型猜代码。

按现有图定位即可完成关系组织；原文语法只补充显示 try/catch 的结构范围，不制造新的 CFG edge。没有图的 materials-only 模式也应由 Step05 复用现有有界 source-location 能力，附 S567 与安全找到的 S568 候选，标 SOURCE_CONTEXT/UNRESOLVED，不能把 field type/name/arity 的唯一命中冒充 exact dispatch。

Capsule 只是同一对象的预算内序列化。普通流程 compile 一次、project 一次，publisher 保存，不重新推导出第二份等价链。即使严格 Flow 未成立，flowRef=null 的上下文仍有价值；Step06 不再承担 noFlow 调用链重构。

## 7. Step06：实际包与目标包差在哪

实际第 314 行只有“已定位入口/handler/直接方法”三条观察和两段完整代码。目标包保留这两段原文，增加 Step05 已组织的 actual/formal、边界、正常与异常返回观察；这项结构信息不由 Builder 再从源码重算。

下面是一份**完整的目标 modelPacket JSON**。snippets 与上述保存材料逐字一致；观察是根据现有图和原文整理的目标设计，不是新的 runtime 文件或已运行模型结果。正式 Step05/06 实现应产出这种可直接阅读的包，不让读者自行拼图。

~~~json
{
  "context": "请根据以下完整代码和观察，解释 GET /accountHead/getFinancialBillNoByBillId 定义的局部业务活动。",
  "allowlistedRefs": [
    {
      "ref": "S567",
      "snippet": "    @GetMapping(value = \"/getFinancialBillNoByBillId\")\n    @ApiOperation(value = \"根据编号查询单据信息\")\n    public BaseResponseInfo getFinancialBillNoByBillId(@RequestParam(\"billId\") Long billId,\n                                              HttpServletRequest request)throws Exception {\n        BaseResponseInfo res = new BaseResponseInfo();\n        try {\n            List<AccountHead> list = accountHeadService.getFinancialBillNoByBillId(billId);\n            res.code = 200;\n            res.data = list;\n        } catch(Exception e){\n            logger.error(e.getMessage(), e);\n            res.code = 500;\n            res.data = \"获取数据失败\";\n        }\n        return res;\n    }"
    },
    {
      "ref": "S568",
      "snippet": "    public List<AccountHead> getFinancialBillNoByBillId(Long billId) {\n        return accountHeadMapperEx.getFinancialBillNoByBillId(billId);\n    }"
    }
  ],
  "technicalObservations": [
    "AccountHeadController#getFinancialBillNoByBillId 接收 Long billId，并把这个 billId 传给 AccountHeadService#getFinancialBillNoByBillId 的 billId 参数。",
    "AccountHeadService#getFinancialBillNoByBillId 再把同一个 billId 作为第一个参数传给 AccountHeadMapperEx#getFinancialBillNoByBillId。本包能看到接口调用，未提供 Mapper 的执行实现。",
    "Service 方法直接返回 Mapper 调用的结果。Controller 在 try 中先把 Service 返回值赋给 List<AccountHead> list，再依次设置 res.code=200、res.data=list。",
    "Controller 捕获 Exception 后，先记录异常，再依次设置 res.code=500、res.data=\"获取数据失败\"；try/catch 之后返回 res。",
    "res.code 是返回对象中的字段；这些代码没有说明 HTTP 响应状态。"
  ],
  "limitations": [
    "本包没有 Mapper 的执行实现或运行时数据，不能据此确定查询是否成功、是否有匹配记录或财务单号是否唯一。",
    "本包没有使用岗位、组织制度或更大业务过程的依据，也没有过账、付款等实际效果的信息。"
  ]
}
~~~

BusinessMaterialBuilder 只检查预算、映射 refs 并保存这份包。ActivityExplainer 才做 DRAFT 和完整 REVIEW。一个合格的目标活动段落是：

> 系统根据业务单据标识查询关联的财务单号，并把查询结果返回给调用方。查询过程中捕获异常时，系统记录错误并返回失败信息。该活动提供的是单据关联信息查询；实际是否存在匹配记录取决于运行时数据。

这段是根据真实源码的**设计示例**，没有宣称本轮模型已生成并通过 REVIEW。DRAFT 与完整 REVIEW 后的目的、条件、步骤、结果、规则、公式和长段落均保存，不能只传递“查询财务单号”这个标题。

## 8. Step07：不要把小查询扩写成未经证明的财务过程

只有这一个活动，合理过程可以是“业务单据关联财务信息查询”。材料不支持“生成账单 → 过账 → 收款 → 结算”或具体岗位；ProcessExplainer 应保持独立查询活动，等待其他完整活动与标识关系提供跨入口依据。

有多个已审活动时，Java 的标识/对象/调用线索只生成候选组；Luna 在完整组材料上解释可能联系，再做完整 REVIEW。这个查询可被多个过程使用，但不能因名字含 AccountHead 就自动归属于某条付款流程。

## 9. Step08：同一小样本可形成的九章业务内容

以下是**单个样本范围的目标正文**，不是全仓报告或运行生成结果。简单来源标记指向上方S567/S568；已交付 renderer 在独立 source-refs.jsonl 保存对应片段，正文不再附源码，业务内容保持不变。

1. 文档说明：本说明依据固定源码中的一个查询入口及其服务方法，描述代码定义行为；不证明实际运行成功，也不代表全仓覆盖。
2. 业务目标：按业务单据标识查找关联财务单号，供调用方获取单据关联信息。
3. 业务对象：业务单据标识作为查询输入；返回的财务单据信息作为查询结果。具体记录内容与是否存在取决于运行时数据。
4. 业务活动：系统将业务单据标识传入查询服务，并交给 Mapper 处理。正常返回时，查询结果写入响应体；发生异常时记录错误并返回失败信息。[S567][S568]
5. 字段与维度：billId 为查询标识；响应体 code 表达该方法的正常或异常分支，data 承载结果或失败消息。不能把 code 等同 HTTP 状态。
6. 对象关系：此入口用于查询业务单据与财务单据信息的关联，不据此断言一对一、唯一性或一定存在关联。
7. 指标口径：本次未从源码识别到可定义指标。
8. 示例问题：如何按业务单据查找关联财务单号？查询异常时系统返回什么信息？
9. 待确认事项：实际匹配记录、使用岗位及更大业务流程中的调用场景，需要其他来源或运行资料确认；其余仓库入口不在本样本覆盖范围。

完整活动段落没有在出版时缩水为标题。Java 只把完整 reviewed paragraph JSON 排版为固定九个 H2，不能重写业务含义，也不再执行任何技术 compiler/projector。

## 10. 更复杂的真实边界与合成过程

DepotHead#batchSetStatus 的八文件范围保留为复杂 guard、配置条件、Java-local 参数与 Mapper 边界的局部说明。历史 pre-reset Gap/0 Flow 仅是历史；不改称本轮成功。该例需要保存条件、变量和返回以便阅读，不要求预先穷举所有分支组合才允许模型看代码。

以下明确为 **SYNTHETIC_ACCEPTANCE_SCENARIO，非 jshERP 行为**：

> 系统收到补货申请后，根据申请明细形成采购单；登记收货时引用对应采购单，并根据已登记的收货信息形成应付账单。各活动实际的准入条件和账单形成时点，以合成输入中明确记录的规则为准；未给出的岗位和制度不补写。

八步仍分别定位源码、发现入口、建图、补充技术 Facts、组织各入口代码、解释局部活动、解释跨活动过程、生成九章。Step05 不直接把四个入口合成一个业务流程；Step07 根据完整已审活动判断引用与先后关系。一个活动可以属于多个过程，不强制唯一 owner。

第二领域可用设备温度告警查询的合成源码，验证同一模型 Prompt 能识别不同业务目的，Java 不需新增“采购/财务/设备”分类表。该场景是验证开放词汇的办法，不是新增业务功能或实际来源证据。

## 11. 真实四入口包：DRAFT 缺项到九章的批准闭环

另一个已保存输入 `.workspace/live-luna-automatic-user-account-group-v1/01-ACTIVITY_DRAFT-input.json` 是独立运行中的真实四入口包；它与上面的财务 S567/S568 历史 run 没有共同 artifact identity：

| local key | HTTP 入口 | ref | 源码支持的窄表述 |
| --- | --- | --- | --- |
| E1 | `DELETE /user/delete` | S487 | 接收用户标识，发起删除处理并包装返回；不证明实际删除成功 |
| E2 | `GET /user/getUserSession` | S722 | 从会话取用户标识、查询用户并在返回前清空密码字段；保留异常返回 |
| E3 | `POST /user/registerUser` | S731 | 建立标准返回，复制登录名、校验验证码/登录名并发起注册调用；不证明注册持久化成功 |
| E4 | `GET /user/logout` | S898 | 在 try 中发起清理 userId/clientIp 会话项，异常时返回退出失败；不证明外部会话存储实际删除 |

真实 v1 DRAFT response 只有 A1/E1、A2/E2。它使用活动上限 2 处理 N=4 材料，并在当前 REVIEW 前全集检查处终止；REVIEW 没有发生。以下两个分支都是**已批准 v2 的未来 scripted 验收**，不是模型已经返回的修复结果，也不允许重放旧失败。

完整分支的唯一 REVIEW 收到原四入口材料、完整实际 A1/A2 DRAFT 与 `missingEntryKeys=[E3,E4]`，随后用有依据的活动补齐 E3/E4。Activity coverage 将四个 local keys 映射回四个 global entry IDs。ProcessExplainer 可把它们一起召回阅读，但可以保留为两个或四个独立局部过程；同一 Controller 和“用户”主题不证明删除→会话查询→注册→退出的先后。RepositoryBusinessKnowledge 保存全部完整已审活动，报告 DRAFT+REVIEW 再形成一份九章范围报告。

PARTIAL 分支的 REVIEW 返回 `unexplainedEntries=["E3","E4"]`。程序生成两条 `unexplainedActivityEntries` 完整 records；送给 Process/Report 模型时按 material 合并成一项 `{materialContext, unexplainedEntryKeys:["E3","E4"], reasonCode:"MODEL_NOT_EXPLAINED"}`，同一 context 只出现一次，global IDs 不暴露。集合账闭合，但 E3/E4 的真实业务语义仍未交付，因此该样本不能通过完整验收。

这一个四入口范围的九章目标链如下；它默认描述完整分支，规定信息去向但不声称模型已生成这些文字。PARTIAL 分支的第 2–8 章只消费已审 E1/E2 内容，不从未审 E3/E4 材料补业务解释；E3/E4 只作为覆盖范围和第 9 章的具体未解释入口披露。

1. 文档说明：说明固定四入口/四 ref 范围、源码行为不等于运行成功；若为 PARTIAL，明确有入口未形成活动解释。
2. 业务目标：分别支持用户删除请求、当前会话信息读取、注册输入校验/处理和退出时的会话清理；不编造统一生命周期顺序。
3. 业务对象：用户标识、会话中的用户信息、登录名/验证码、会话键与返回内容；不把 Java 类型或 `manageRoleId` 猜成岗位。
4. 业务活动：完整分支保留四项已审行为及正常/异常路径；PARTIAL 分支只写 E1/E2 已审活动，不替 E3/E4 编造正文。
5. 字段与维度：只写源码中可见的 userId、clientIp、loginName/username、code、uuid 等输入/会话字段及其用途，不发明枚举口径。
6. 对象关系：可说明这些入口都处理用户或会话信息，但没有材料就不写必然先后、唯一身份流或因果链。
7. 指标口径：本范围没有已审公式时明确未识别可定义指标。
8. 示例问题：如何读取当前会话用户信息？退出时会发起哪些会话清理？注册请求经过哪些校验？
9. 待确认事项：说明删除/注册/会话存储的实际外部效果；PARTIAL 时具体写 `POST /user/registerUser` 与 `GET /user/logout` 本次未形成活动解释，原因类别为 `MODEL_NOT_EXPLAINED`，不能只写“2 项未分析”或裸 E3/E4。

任意 N 的可扩展验收另含跨包 N=9、单包 N≥12 的 E10–E12、零入口和超预算，详见[已批准清理与覆盖设计](../plans/code-cleanup-and-scalable-activity-coverage-design.md#8-可扩展性与边界推演)。

## 12. 多个任务怎样排队并汇成一份报告

以下是 **SYNTHETIC_ACCEPTANCE_SCENARIO，非 jshERP 行为或实测运行**。五份冻结材料 A…E 均含完整来源，分别说明补货申请、独立查询、采购单形成、收货登记和应付账单形成。为看清排队，使用[同一 YAML 合同](../modules/model-job-execution.md#2-唯一配置入口与精确-yaml)的全局并发 3、Pro 2、API 1，activity/processGroup route 为 `[pro, api]`；默认 Pro 4 和 6/4/2 只是其他配置，不改变总 job 数。`maxMaterialsToStart≥5`。

按稳定顺序分配 A→Pro、B→API、C→Pro、D→API、E→Pro；每对 DRAFT/REVIEW 均固定该 Provider/model/effort。下表每次“完成”均指完整 REVIEW 校验并保存完成，而非仅 DRAFT 返回。

| 时点 | 在途 job | 等待项 | coordinator 动作 |
| --- | --- | --- | --- |
| 开始 | A(Pro)、B(API)、C(Pro) | D、E | 满足全局 3 / Pro 2 / API 1，各 job 独立读完整材料 |
| B 先完成 | A(Pro)、C(Pro)、D(API) | E | 立即保存 B，释放 API 名额并派发 D，不等较慢 A |
| C 完成 | A(Pro)、D(API)、E(Pro) | 无 | 立即保存 C，再派发 E；没有因最初只有三空位而跳过 D/E |
| E、D、A 依次完成 | 无 | 无 | 各自立即保存，最终按 A…E 稳定聚合活动与覆盖；完成先后不表示业务顺序 |
| 活动屏障后 | G1(Pro)、G2(API) | 无 | G1 读 A/C/D，G2 读 C/D/E；C/D 为只读共享成员。B 的单活动组按既有规则记 unmatched、零 Provider，B 仍作为独立已审活动保留 |
| 两个有效组全部完成 | summary(Pro) | report | 完整已审组、独立活动 B 与 coverage 进入唯一总结 DRAFT/REVIEW，不能用先完成 G1 宣告全仓完成 |
| 总结完成并发布知识 | report(Pro) | 无 | 完整知识/活动/过程进入唯一整篇九章 DRAFT，然后同绑定完整 REVIEW |
| 报告完成 | renderer（零模型） | 无 | 根据已审 JSON 排版九章与短 refs，源码保持独立保存 |

G1/G2 的联系仅在合成材料提供采购单/收货引用等实际依据时成立；一个活动属于多个组不表示两次执行，也不强制归给先完成组。所有 job 的 REVIEW 显式包含原完整材料和实际 DRAFT；A 若 DRAFT 漏 key，仍按原合同携带 missingEntryKeys，REVIEW 通过活动/显式 unexplainedEntries 闭合。不能为了让三个请求同时运行而缩短源码或活动长字段。

正常且总结准入时，这个例子是 5 个活动 job、2 个有效过程组 job、1 个总结 job、1 个报告 job，共最多 18 次调用；B 的单活动组不产生过程模型调用，独立活动与 unmatched 处置继续保留，不能据此声称全仓过程覆盖完整。这不是全仓固定调用数，也不是耗时预测。若既有总结配置关闭或输入容量不满足，保存明确 notConsolidated/范围记录后才发布知识，报告不能把缺失总结说成已完成。如果 A 的 DRAFT fatal，尚未开始的 D/E 不再派发；已开始且自身合法的 B/C 完成各自唯一 REVIEW 并保留，整个执行失败，G1/G2、总结和报告均不启动，不把 A 改派 API 重试。

## 13. 财务 dry-run 与后续实施验收

财务历史样本说明应怎样阅读真实来源；当前关系接力、旧链清理、任意 N/v2 覆盖、并行池与具体未解释入口下传均已实现。后续模型批次接线应：

- 保留真实 source identity、图边、Fact/Proof 与已有步骤产物。
- 保持已完成的 Step05 带代码 EntryContext、noFlow 供材及 ordinary publish 去重，不回头修改 Step03/04 稳定算法。
- 保持已完成的旧 interpretation 链退役、任意 N、唯一 REVIEW、v2 闭合和有界池；只补固定材料读取与完整已审 job 复用。
- 把具体未解释入口经 Step07 送到第9章；集合 PARTIAL 不冒充语义完成。
- 用 frozen/scripted 测试核验两级上限、超过 12 个 job/并发 1 不漏任务、重叠组、稳定聚合、完整审阅、认证隔离、失败保存和 renderer 零 Provider；一个小包通过不宣布整仓完成。

以上是设计验收目标。本文没有运行新 Maven、客户程序、capture、Provider 或最终报告生成。

## 14. 模型没读完，为什么不需要重新找代码

先沿上文财务例子取得Controller、Service、Mapper声明、billId传递和源码片段，再将它们连同其余入口保存成一份材料检查点M。模型运行B1用M解释活动；即使财务活动的REVIEW失败，代码文件、调用链和M里的短来源编号也没有变化。

用户显式启动B2并指定复用B1时，程序重新打开M，验证真实保存内容，不调用JDT或Builder。B1已完整审阅并保存的其他活动在内容/服务绑定一致时直接引用；财务活动只有DRAFT，没有完整已审job，因此B2为它重新做一对DRAFT/REVIEW。B1原日志和草稿仍保存，不能把B2的新答案填回B1。

活动聚合后，只有完整输入未变化的过程组结果才复用；发生变化的组、总结或报告按各自依赖重新判断。模型仍能看到完整业务内容及源码，不看到M/B1/B2、hash或调度诊断。最后新报告归B2，来源仍查M对应的文件和行号。纯重渲染读已审JSON，不调用模型。

这是目标推演，不是新模型实测。本次真实整仓情况是第一份326材料、326入口处置，之后4个DRAFT没有完整已审结果；后一次扫描325材料、326入口处置。选第一份有效材料并不需要把第二份混进去。准确身份、失败边界、目标私有格式及示例均见[唯一模型批次合同](../modules/model-job-execution.md#7-固定材料与独立模型批次已批准待实施)。这套规则不绑定财务、管伊佳、4包或326包，对任意数量材料相同。
