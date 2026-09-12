# 流程解释

> [总体设计](../DESIGN.md)；固定 key：flow-interpretation，目录：steps/06-flow-interpretation/。两个业务 Module：BusinessMaterialBuilder、ActivityExplainer。

## 1. 为什么存在

Step05 已经把每个入口的代码关系和相关代码片段放进连贯上下文。Step06 把这份上下文整理成模型可读的有界包，再解释一次业务活动。最重要的输入是“实际怎样调用、传参、分支和返回”的代码，而不是一串证明 ID。

BusinessMaterialBuilder 不再构造第二套调用链。它不丢弃 Step05 上下文，重新用字段类型、方法名和 arity 推导一个直接 callee；它只选择完整上下文、映射短 ref、检查预算并保存模型材料。业务解释全部由 ActivityExplainer 的 Luna/high 完成。

## 2. 输入：一个或多个已经连贯的入口上下文

正常输入是同源 Step05 entryContexts/Capsules 和全入口 coverage。每份上下文已直接保留持久化调用图/控制图中的入口参数、主要调用、actual→formal、guard/try/catch、返回/边界、代码片段与限制；严格 Fact/Proof 只是可选增强，不是代码关系出现的前提。严格 Flow ref 可以为空；安全可读的无 Flow 入口仍由 Step05 的同一个 context owner 整理，Step06 不另开源码发现路线。

一个模型材料包可以承载一个或多个**相关入口上下文**。相关只是一种减少重复阅读的程序分组，不是业务过程结论：当前最低可靠分组依据是同一 HTTP handler 类；同组可再保留相同直接调用目标作为阅读线索。不同 handler 类、泛化工具调用、名称相似、共享审计字段或共享租户字段都不足以强制合包。一个组受明确入口数、来源片段和实际序列化字节预算限制；加进下一个入口会使包超预算时，程序在该入口边界另起包，不能截断前一个入口的 guard、返回或调用关系。

配置包容量记为 `K`，只规定 Builder 一包最多接纳多少入口；每个实际包的 `N=material.entryIds().size()` 可以是任意正数且 `N≤K`，K 和 N 都不固定为 4。每包按稳定入口顺序独立生成 E1…EN，并保存 `(materialId, local key) → global entry ID` 映射；不同包可以都含 E1，不能用 key 前缀、substring 或词典序跨包连接。E10、E11、E12 与 E1 一样是完整枚举值。一个活动可覆盖多个 key，一个 key 也可由多个有依据的活动覆盖；活动数不等于入口数。

以下为阅读投影示意；当前 Activity v2 的程序产物已支持其 scope-local key 与完整未解释入口闭合，但例中的业务内容不是一次当前运行结果：

~~~json
{
  "entry": "E1",
  "context": {
    "flowRef": null,
    "callLinks": ["Controller.billId → Service.billId", "Service.billId → Mapper.billId"],
    "control": ["try: call Service; set code 200; put result in data", "catch: set code 500; put failure message in data", "return res"],
    "fragments": [
      {"ref": "S1", "kind": "ENTRY_METHOD", "text": "完整 Controller 方法片段由程序从固定源码取出"},
      {"ref": "S2", "kind": "CALLED_METHOD", "text": "完整 Service 短方法片段由程序从固定源码取出"}
    ],
    "limitations": ["No formal strict Flow publication", "Mapper execution outcome unknown"]
  }
}
~~~

上面 text 是形状说明，不能当证据发送给 Provider。真实包只能包含冻结源码原文；完整财务小例见 [walkthrough](../examples/semantic-framework-walkthrough.md)。

## 3. BusinessMaterialBuilder：封装已有上下文

| Interface 项 | 合同 |
| --- | --- |
| 输入 | 同源 Step05 EntryContext/Capsule、全入口 coverage、BusinessMaterialProfile |
| 输出 | `BusinessMaterialSet`、全局唯一 SourceRefs、每入口 material/disposition 和包内 local-key 映射 |
| 职责 | 选择完整上下文，按 K、片段与实际 bytes 在入口边界分包；不判断业务过程，不重建图或调用边 |
| 失败 | source/ref/identity 损坏 fatal；单入口无法安全成包则具体 NOT_ANALYZED，且 Provider 调用为 0 |
| 下游 | 完整 material 给 ActivityExplainer；必要原材料可供 ProcessExplainer 回查 |
| Luna RED / Terra GREEN | RED 覆盖任意 K、noFlow、零入口、global/local 映射与预算；GREEN 只修改现有 Builder seam，不新增 scanner/resolver |

程序先按全入口 ID 集读取 Step05 上下文，按上述结构线索进行有界分组，检查输入身份/ref/budget，将同一源码位置映射为全 BusinessMaterialSet 唯一短 ref。同一 ref 永远指一个位置，同一片段反复使用复用 ref。原始文件/行段/hash 只保存在程序侧 SourceRef 映射。

模型包按入口边界连续保留 Step05 已经组织的技术观察和代码段，并清楚标出每个 HTTP 入口。包内以 `E1`、`E2` 这类**只在当前请求有效**的短键标记入口顺序；模型可以且必须用它说明每项活动覆盖哪个入口，但看不到真实 entry ID、路径、行号或 hash。Java 依据这个短键再映射回持久化入口分母。必要时只在 Step05 提供的位置范围提取原文或调整展示顺序，不重新分析 graph、证明 Fact 或推导调用边。图 Gap 的安全定位以“此处行为尚未静态解析”的限制和原文片段呈现，不能由 Builder 猜一个 target。不能用空 generic 摘要替代 actual/formal、条件作用域、返回字段和错误分支，也不能把完整上下文截成几个孤立关键词。

材料预算以实际完整序列化的请求大小、代码片段和关系条目计算。Builder 形成材料时，一个组过大允许在**入口之间**分包；一个单独入口过大时，只能选择 Step05 已划定的完整子上下文并明确未覆盖范围，不静默截断条件或返回。若不能在预算内保留最低可读结构，则入口 NOT_ANALYZED_BUDGET，Provider 调用为 0。材料一旦保存，ActivityExplainer 不得为迁就自己的 profile 再拆包或裁源码；应由调用方选相容 Activity profile，或在新的材料规划中由 Builder 重新分包。

保存 business-materials.jsonl 后才可开始模型。入口无安全上下文记 NOT_ANALYZED_SOURCE；有已知代码但关系未完成则可 ANALYZED_WITH_GAPS，保留具体缺口。为控制长度而保留的“仅选择预算内片段”说明不是业务或技术缺口：完整 Flow 仍可记 ANALYZED。不能把无 strict Fact 或 FLOW_NOT_AVAILABLE 一律当作没有业务材料。

模型可见内容只有短 refs、代码、必要技术观察和限制；不可见本机路径、行号、hash、完整五图/Proof、run/artifact/publication identities、Provider 控制或预算配置。Java 不给材料贴采购、销售、财务等行业类型标签来决定模型答案。

## 4. ActivityExplainer：DRAFT 与完整 REVIEW

| Interface 项 | 合同 |
| --- | --- |
| 输入 | 一个完整 BusinessMaterial、实际 E1…EN/ref allowlists、ActivityExplanationProfile |
| 输出 | 完整 ReviewedActivity、global-entry `ActivityEntryCoverage`、程序侧 `unexplainedActivityEntries` |
| 职责 | 最多一次 DRAFT 加一次完整 REVIEW；业务解释交 Luna，程序拥有 identity、映射、预算和闭合 |
| 失败 | 调用前不相容零请求；非法 JSON/key/ref/ID/bytes、started 失败或 REVIEW 仍漏项 fatal，无第三次调用 |
| 下游 | ReviewedActivity、coverage 与具体未解释入口交 ProcessExplainer/Step08 |
| Luna RED / Terra GREEN | RED 覆盖 N=4 缺项、N≥12、跨包 E1、many-to-many、REVIEW 实际 bytes；GREEN 落地现有 Explainer 的 v2 Prompt/schema/result seam |

Luna/high 接收一个完整 material package，解释目的、参与者（有依据时）、对象、输入、条件、步骤、结果、规则、公式、可问问题及待确认项。它可以用业务语言解释清楚代码行为，不必每个词逐个配 Proof；不能发明角色、唯一单据、运行成功、外部效果或制度。多入口包的每项活动必须给出非空 `entryKeys`（如 `E1`）。理想完整结果中所有包内入口至少被一项活动覆盖；已批准的最终合同也允许 REVIEW 把确实没有形成活动解释的 key 放入 required `unexplainedEntries`，但这种结果的业务语义验收是 PARTIAL。一个活动可覆盖多个短键，但 Java 不得再把“同一材料包”误写成“每项活动都覆盖全部入口”。若正文提及 HTTP 触发，必须逐字保留 packet context 的完整方法与路径；除说明代码边界确有必要，不把 Java 类型、变量名或技术层名当业务对象。

每个包最多一次 DRAFT 和一次 REVIEW。REVIEW 输入必须包含原始材料及完整实际 DRAFT，输出完整修订后的活动；不是只返回“通过”或一个修订 patch。完整 purpose/conditions/steps/results/rules/formulas/questions、长段落及来源 refs 都进入持久化结果，供过程和报告继续消费。

在第一次调用前按实际 N 完成容量闭环：`maxActivitiesPerMaterial≥N`；单项活动的 key 数容量至少 N；用当前 schema 构造的 N 项最小合法 REVIEW 输出不超过 `maxModelOutputBytes`；实际 `cleanPacket` 不超过 `maxModelInputBytes`；并为 `cleanPacket + 最大 DRAFT + missingEntryKeys envelope(N)` 预留完整 REVIEW input budget。DRAFT 返回后再把真实 `actualDraft` 与 `missingEntryKeys` 装包并 canonical serialize，精确核对实际 REVIEW bytes。前置不相容时整份材料零请求并记 `NOT_ANALYZED_ACTIVITY_OUTPUT_CAPACITY`、`NOT_ANALYZED_BUDGET` 或 `NOT_ANALYZED_REVIEW_INPUT_BUDGET`；DRAFT 已开始后才发现预留不变量失效则 fatal，不能伪装为零请求。

目标 v2 把校验分成两层：DRAFT 的 JSON、字段、文本/数组、local ID、key/ref allowlist 和 output bytes 必须全部合法；只有 `draftCoveredKeys != expectedKeys` 不再在 REVIEW 前 fatal。程序计算 `missingEntryKeys=expectedKeys-draftCoveredKeys`，将它与完整实际 DRAFT 交给唯一 REVIEW。REVIEW 响应 shape 为 `{activities, unexplainedEntries}`；`unexplainedEntries` 字段 required，允许 `[]`，禁止 null/省略，只能包含本包唯一 key。最终要求 `coveredKeys ∪ unexplainedEntries = expectedKeys` 且交集为空；否则 `ACTIVITY_REVIEW_INVALID` fatal。

模型响应的 `unexplainedEntries` 只含 local keys。程序根据 material 映射生成 `ActivityExplanationResult.unexplainedActivityEntries: List<UnexplainedActivityEntry>`，并在 `activity-coverage.json` v2 顶层同名 required 数组保存 `{entryId, materialId, entryKey, materialContext, reasonCode}`；`reasonCode` 固定 `MODEL_NOT_EXPLAINED`。这两个数组名称和所属对象不同，不能把模型 key 数组反序列化为 sidecar record。`materialContext` 逐字来自现有 `modelPacket.context()`；不凭空添加 EntryDescriptor，不正则解析中文 context。记录保存完整列表，`missingEntryKeys` 与后续 `unexplainedEntryKeys` 按原材料入口映射排列，不能用词典序把 E10 放在 E2 前。

`ActivityExplanationResult` 的便利构造器可为新列表提供 `List.of()`；canonical v2 reader 必须要求 `unexplainedActivityEntries` 存在且为数组，空列表写 `[]`，不得将缺失/null 或旧 coverage v1 自动解释为空。ActivityExplainer 的 Module version 升到 v2，地址仍为 `FLOW_INTERPRETATION/11/activity-explainer`；DRAFT/REVIEW Prompt 切到 v2，`flow-interpretation-activity-coverage-v2` 与 engine、policy fixture、publisher/reader、版本测试同步。完整 ReviewedActivity shape 未变，`flow-interpretation-activity-explanations-v1` 保持 v1。

以下为**目标活动投影，不是当前产品生成结果**：

~~~json
{
  "activityId": "A1",
  "name": "查询业务单据关联的财务单号",
  "purpose": "按业务单据标识查找关联财务单号，供调用方使用。",
  "participants": [],
  "inputs": [{"name": "业务单据标识", "sourceRefs": ["S1", "S2"]}],
  "conditions": [],
  "steps": [
    {"text": "系统将业务单据标识传入查询服务，并进一步交给 Mapper 查询。", "sourceRefs": ["S1", "S2"]},
    {"text": "查询正常返回时，系统把结果放入响应；捕获异常时返回失败信息。", "sourceRefs": ["S1"]}
  ],
  "results": [{"text": "返回查询结果或失败信息。", "sourceRefs": ["S1"]}],
  "rules": [],
  "formulas": [],
  "questions": ["如何按业务单据查找关联财务单号？"],
  "confirmationTopics": ["实际是否存在关联记录需要运行时数据。"]
}
~~~

没有业务 guard 不强造条件；try/catch 属于错误处理，可解释在 steps/results。没有岗位依据 participants 为空，不能把 Controller、Service 当业务角色。关于代码定义行为、运行事实与合理推断的中文 Prompt 见 [模型解释附录](../references/semantic-interpretation-prompts.md)。

Java 只校验结构、scope-local IDs/refs、集合闭合、预算与保存约束。内容 review 判断必须体现在模型完整修订结果与诊断中；程序不通过关键词词表决定业务语义正确。上述 v2 output、Prompt 和 coverage sidecar 已在当前 Java、`activity-draft-v2.txt`、`activity-review-v2.txt` 与 coverage v2 中实现；旧 v1 资源和旧 coverage wire 不会被解释成新结果。Process/Report 对完整 sidecar 的按 material 聚合投影也已落地并经过 partial scripted 链验证。

## 5. 输出与下一消费者

| 文件 | 内容 | 下一消费者 |
| --- | --- | --- |
| business-materials.jsonl | 一至多个相关入口的完整技术上下文、源码短 ref 映射、材料/入口关系与限制 | ActivityExplainer、ProcessExplainer 必要回查 |
| activity-explanations.jsonl | v1，每份完成 REVIEW 的完整活动，不压缩为标题 | ProcessExplainer、报告章节材料 |
| activity-coverage.json | v2：全入口与材料的分析处置、未启动原因、容量限制，以及顶层 `unexplainedActivityEntries` 程序侧完整 records | Step07、Step08 与 inspect |

目标是完成一个包 REVIEW 随即保存，后续包失败不删除已有结果；当前 ActivityExplainer 实际在循环结束后才以固定地址聚合 publish。把 publisher 移进循环会用同一地址安装不同 bytes 并 collision，因此即时逐包保存仍是独立已知缺口，本次覆盖修复保持现有聚合 publication，不假称解决，也不新增分片/恢复协议。DRAFT/REVIEW 原始执行材料按现有私有审计策略保存，不作为额外产品候选，不新增逐记录状态机或修复账本。

Step07 可以按已保存 material/activity ID 读取必要内容，不回到扫描仓库或重构调用链。`RepositoryBusinessKnowledge.unexplainedActivityEntries` 已持有并保存完整记录。程序送给 Process 仓库总整理/Report 模型前按 `materialId` 把完整 sidecar records 聚合成一项 `{materialContext, unexplainedEntryKeys, reasonCode}`：同一 context 只发送一次，删除 material/global entry IDs，保持 E1…EN 的材料映射顺序。活动之间是否属于同一过程由模型阅读多个活动决定，不由 Step06 强设唯一 owner；process-group Prompt 不因这一仓库输入变化升版。

## 6. 覆盖、预算、失败与复用

所有发现入口必须进入 ANALYZED、ANALYZED_WITH_GAPS 或有具体原因的 NOT_ANALYZED。一个 material 可能支持多个活动，多个材料也可能共同解释活动，但入口分母不能减少。集合账中由 `MODEL_NOT_EXPLAINED` 闭合的入口仍是 NOT_ANALYZED；账闭合不等于业务内容完整，PARTIAL/INCOMPLETE 只是文档语义与验收结论，不新增 runtime/report enum。

maxMaterialsToStart 限制本执行实际启动的材料数；超限材料写 NOT_ANALYZED_EXECUTION_CAPACITY，零请求。材料数是预算后实际分组数，不等于 HTTP 入口数。materials-only 不调用 Provider，但仍必须先完成 Step05 并从其保存的 EntryContext 读取；Builder 不暗中补边、重新定位调用或扫描 Java。

只有结构与 scope 合法、但 coverage 不足的 DRAFT 能进入唯一 REVIEW。started 后 transport/schema/runtime 失败停止本执行，不重试或切 Provider。source/hash/ref 错误、模型使用 allowlist 外 key/ref、损坏 JSON、重复冲突 local ID 或超安全预算 fatal；REVIEW 两边仍漏 key 也 fatal且不发第三次调用。未知业务含义、局部 graph 缺口和无 strict Flow 限制对应结论；`MODEL_NOT_EXPLAINED` 只表示本次模型未形成活动解释，不自动升级成这些技术缺口。

同进程复用不可变对象；跨进程/磁盘/导入复用检查 identity/hash/schema/ref/basis 和 inputFingerprint。fingerprint 覆盖内容输入、实际 Prompt、模型/output 配置和 Module 版本，变动就不复用旧内容。已开始但结果不确定的 Provider 调用不得恢复或重放。

## 7. 当前实现与最小修改

当前 BusinessMaterialBuilder 已读取 flow-slices 的 EntryContext，并将已保存的调用、实参/形参、边界、条件、返回和固定 locator 组织成短引用材料。无 strict Flow 时仍消费同一 EntryContext；不再存在从源码盘点直接重扫 Java、按 field type/name/arity 猜测 callee 的业务材料路径。Capsule 的 span 仍保留技术增强；EntryContext 是连贯关系的唯一 owner。已实现按同一 handler 类和材料模式的有界分组：每个成员的完整已选上下文都保留，模型 context 以 `E1…En` 标明成员，模型的活动响应也必须用这些短键声明覆盖范围；程序再将短键映射回实际入口。该分组不改变 Step05、不命名业务过程，也不替代后续业务解释。

固定 jshERP commit `8c30ce7861570458920175e200bb2a6442713580` 已完成一次新的生产路径、零 Provider 全仓材料验证。该 run 盘点 719 个 tracked 文件、发现 339 个 HTTP 入口，并持久化 446 条 JSONL 记录：107 条 `BUSINESS_MATERIAL` 与 339 条入口覆盖记录。107 个材料包的入口数分布为 21 个单入口、10 个双入口、6 个三入口和 70 个四入口，恰好覆盖全部 339 个入口；12 个包是 `FLOW_PREFERRED`，95 个是 `ENTRY_SOURCE_FALLBACK`。两种包都来自 Step05 已保存入口上下文，后者明确携带技术限制而不伪装为完整 Flow。所有 339 个入口都有材料，Provider 调用数为零。这个实测 107 包计划才是后续 Luna 调用预算的分母，而不是旧的 339 个单入口包。

这项验证只回答“自动材料是否覆盖整仓、是否可在模型调用前观察”。它本身没有生成业务过程或九章报告。已有 ActivityExplainer DRAFT+完整 REVIEW、ProcessExplainer、BusinessReportPublisher 和 BusinessAnalysisWorkflow，不能写成尚未实现；这些存在与有界 scripted 测试也不能替代真实小包的语义质量验收。

已对自动生成的 `POST /user/registerUser` `FLOW_PREFERRED` 材料完成一次授权的 Luna/high DRAFT+完整 REVIEW。9 个短片段使模型识别出“接收注册请求 → 将登录名写为用户名 → 校验验证码 → 检查登录名 → 调用注册服务 → 返回标准结果”的局部活动，并明确没有把调用服务写成一次已成功持久化。它同时暴露两项文字质量问题：HTTP 路径被截短、Java 类型/变量名泄漏进业务对象。因此 DRAFT/REVIEW Prompt 已增加完整 HTTP 方法与路径原样保留、业务语言优先的规则；这不重放已结束的产品候选。

另一个真实保存的四入口包使用 E1 `DELETE /user/delete`/S487、E2 `GET /user/getUserSession`/S722、E3 `POST /user/registerUser`/S731、E4 `GET /user/logout`/S898。历史 v1 候选以 `maxActivitiesPerMaterial=2` 约束四入口，实际 DRAFT 只返回 A1/E1、A2/E2，并在 coverage-before-REVIEW 验证处终止，REVIEW 没有启动。它证明了 v1 的缺陷；当前 v2 会对不相容 profile 在首次请求前失败，或用完整实际 DRAFT 与 `missingEntryKeys` 启动唯一 REVIEW。历史已开始候选仍不会被重放。

新的实测多入口质量点读取同一固定提交中的 `GET /account/getStatistics` 与 `GET /account/listWithBalance` 组包。一次 Luna/high DRAFT+完整 REVIEW 在 60.64 秒内产生两项完整活动，分别覆盖这两条入口：前者说明按名称和序列号查询结算账户统计、正常/异常返回分支及统计口径待确认；后者说明按相同输入查询带余额的账户报表、列表转表格返回及余额口径待确认。模型没有把同一 Controller 类误写成固定前后流程，也没有编造岗位、余额计算公式或一次实际运行成功。这证明组包可以降低调用数，同时用 scope-local key 保留入口级结果；它仍只验证局部活动，尚未验证跨活动过程或九章报告。

已用 scripted Luna/xhigh RED 与 Terra/xhigh GREEN 覆盖：N=4 DRAFT 漏 E3/E4 后 REVIEW 收到完整 actualDraft/missing keys、N≥12 的 E10–E12、跨包 E1 不串、many-to-many、真实 REVIEW 预算、非法 key/ref/JSON、REVIEW 仍漏项与 v2 reader required 字段。四入口 active Module 链进一步确认：REVIEW 可补齐 E3/E4；partial 时 E3/E4 只以具体 `MODEL_NOT_EXPLAINED` 范围进入第9章，不能泄漏为第2–8章业务结论；三包本地 E1…E3 映射到九个不同 global entry，零材料时 Activity/Process 调用均为零。保持已完成的 Step05 接力。

上述直接 scripted 测试通过后，已按批准启动一次新候选的真实 Activity 验证，最多两次 Luna/high 调用。精确 material 为 `material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb`，四个完整有序 entry IDs 和 S487/S722/S731/S898 见[实施计划 Task 7](../plans/coherent-code-context-implementation-plan.md#task-7脚本验收后执行已批准的单材料-live-验证)；选择器同时核对 material identity、完整 entry 集及顺序、refs，不用 context 子串查找。普通 Java/Maven 沙箱候选在 DRAFT 子进程失败后按 no-retry 规则终止；独立宿主会话诊断确认 Codex 只需更新本机登录会话状态，不需写客户仓库。新的宿主会话候选在同一 material、profile、Luna/high 与只读模型 sandbox 下，用一次 DRAFT 加一次 REVIEW 在 111.604 秒完成。它产生四项覆盖 E1–E4 的局部活动：删除指定用户账户、读取当前会话用户并清除密码字段、校验后发起账户注册、清理会话字段以处理退出。模型保留了注册服务实际保存、管理角色含义及会话制度等待确认项，也没有把四项活动写成必然生命周期。该结果只验证局部 Activity 解释，不验证过程、报告或整仓结论。
