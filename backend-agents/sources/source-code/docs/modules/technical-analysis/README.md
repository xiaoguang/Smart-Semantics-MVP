# 技术分析：前端、后端、持久化独立采集，按入口组装

状态：2026-09-29 工程实现与固定源码技术验收已完成并合入 `main`。四命令与按入口 JSON 已通过正式 CLI／Agent／存储的同源离线 fixture 接力；最终 R4 的339份入口文件、51条前端请求处置经严格重开及三类 artifact 查询。源码位置已投影为相对路径，页面、组件和 mixin 的完整方法声明已进入具名入口；旧合并路线仍可读。51条前端请求的运行时地址尚未确认，长等待根因和全量逐边语义准确性未得到证明；这些限制不能被“文件生成”覆盖。

## 1. 本次只解决什么

源码准备不变。将前端解析从现有 collect-code 拆出，形成四个独立操作；最终按后端 entryId 保存完整机器可读JSON，不再按编号顺序把12个入口混成容量包。修正已确认的错误调用归属、正常外部调用误报、重要前端单元遗漏、SQL排序投影遗漏和过期文档。

技术采集与组装零业务LLM调用，不解释“采购→入库→付款”的业务生命周期；不执行客户应用、JS、动态MyBatis/OGNL或数据库。保存静态关系和完整原文，不承诺所有运行时分支都被静态证明。

本体语义识别是[总体设计](../../DESIGN.md#3-总体接力与分支)的新分支，owner为[本体Modules](../ontology-recognition/README.md)，差距见[实施计划](../../plans/ontology-recognition-implementation-plan.md)。只消费新R4与同R0可选原文/DDL，不要求重跑技术链，也不兼容旧Activity/过程输入。表/HTTP请求不直接升为对象/Action；当前尚无本体生产实现。

## 2. 目标运行关系

```text
R0：prepare-source（已有；固定原文、有效范围、排除）
 ├── R1：collect-frontend
 │       Vue/JS页面、实例、请求和完整源码单元
 │       不需要后端入口、Maven、JDT
 │
 └── R2：collect-code（只负责后端）
         外部Maven输出→实际JDT环境→后端入口→Java调用
              ↓
         R3：analyze-persistence --code-run R2
             Mapper绑定、XML、SQL及限制
              │
 R1 ──────────┴──→ R4：assemble-materials
                       --frontend-run R1 --persistence-run R3
                       请求与后端入口匹配
                       → 每entryId一份JSON＋总目录＋前端覆盖
```

R1–R4是本文运行角色，不是重排现有Step01–08、不是固定真实runId。R1与R2可独立执行；R3只依赖R2；R4同时依赖R1和R3，并沿R3取得准确R2。操作顺序不代表业务步骤。保留唯一 source-analysis 可执行程序，共五项源码/技术操作（已有prepare-source＋本次四项）。

外部Maven仍由用户或获准Agent执行官方build-classpath、effective-pom；Java只读取输出及明确JDK选择，不负责下载、依赖求解、审批或猜配置。独立前端不被Maven缺项阻断。

## 3. 现有工具与我们需要写的最小适配

| 现成能力 | 本轮保留/薄适配 | 明确不造的系统 |
| --- | --- | --- |
| Maven官方导出 | 后端确定性读取classpath、有效POM和目标JDK | 下载器、仓库管理、父POM/BOM求值 |
| JDT LS/Core | 原声明与导航；读取逐调用真实绑定，纠正嵌套错归并区分外部边界 | Java编译器、调用分派器、全文件诊断完成证明 |
| vue-eslint-parser | 既有有限Vue2模式，独立运行及必要源码单元选择 | 通用JS解释器、完整Vue运行时、客户npm脚本 |
| MyBatis安全XML/JSqlParser | 原文/结构/静态SQL；补现有ORDER BY投影 | 动态SQL穷举、OGNL执行、SQL parser |
| 原canonical store/Agent/Skill | 多运行准确引用、每入口JSON、受限变长文件集合 | 第二套证据数据库、后台审批和恢复框架 |

已有工具依据见[工具复用决策](../../supplements/technical-tool-reuse-decision.md)、[前端parser研究](../../supplements/frontend-http-parser-research.md)、[SQL工具研究](../../supplements/mybatis-dynamic-sql-parser-tools-research.md)。JDT新增绑定适配与Lombok验证的具体依据见[JDT模块](../java-code-engines/jdt-engine.md)。复杂支持不足时报告或延期，不以“没找到工具”为由自行重造。

## 4. 设计责任唯一归属

| 文档 | 拥有的合同 |
| --- | --- |
| [运行、配置、版本与Skill](cli-and-runtime.md) | 四命令、运行关系、保存查询、历史兼容与版本 |
| [外部Maven交接](dependency-preparation.md) | 已有编译输入，不因前端拆分重新开发 |
| [Java就绪](java-readiness.md) | 后端来源/JDK/JAR检查、诊断未知政策 |
| [前端采集](frontend-http-discovery.md) | 独立源码→请求/完整单元，不在此匹配后端 |
| [JDT引擎](../java-code-engines/jdt-engine.md) | 逐调用绑定、错误归属修正、外部/未知/失败 |
| [Step04](../../analysis-steps/04-proven-code-facts.md) | XML/SQL及ORDER BY保存 |
| [Step05](../../analysis-steps/05-business-flows.md) | 组装步骤、前后端匹配及消费者 |
| [入口证据合同](entry-evidence.md) | 每entry JSON内容、路径、目录、范围和存储约束 |
| [修改/撤换清单](../../plans/technical-analysis-cli-and-vue-cleanup-design.md) | 当前代码到目标的差异、先后依赖、退出条件 |
| [真实例子与目标走读](../../supplements/vue-to-sql-walkthrough.md) | 历史已生成数据与目标输出逐步对照 |

## 5. 当前实现与本轮差距

| 能力 | 已核实当前事实 | 本轮差异 |
| --- | --- | --- |
| 源码准备、排除 | 已完成并有直接验证 | 不重做 |
| Maven交接 | 内嵌下载路线已删除；官方输出v2进入固定源码JDT环境 | 保留，不新增执行Maven代码 |
| 后端JDT | 旧正式结果339入口中332收集/7失败；本轮新R2为339/339收集，具名错边已定向复核，长等待仍存在 | 不把定向通过推断为所有边均正确；保留未确认候选 |
| 前端 | 旧合并R1有51请求；本轮独立R1保存51请求及69个源码单元，包括`getQueryParams` | 后端匹配移到R4；运行时地址未知时保留候选 |
| 持久化 | 旧R2已有573条statement；本轮独立R3保存1780条不同类型记录，含完整XML及部分SQL | 复用新Java基础；动态SQL不执行 |
| 组装 | 旧R3有47包；本轮R4已生成339份入口JSON和51条前端覆盖，最终格式仍复验 | 每entry独立文件，不以相邻入口容量删材料 |
| 超时缓存 | 等待超时不再永久缓存为失败；43项直接测试及迟到响应验证 | 保留，不重复开发 |
| JDT长等待 | 新R2的旧七入口同会话均完成，个别耗时160秒或68秒；此前约51秒等待的内部根因仍未确认 | 保存实测时序，不把一次成功说成性能问题解决 |
| Lombok | 已含依赖不等于LS/Core识别生成成员 | 仅成熟方案核对、具名验证；不自造处理器 |
| 旧路线 | plan-materials与内嵌Maven已退出 | 核对实际剩余消费者，保留必要历史reader |
| Activity/Step07 | 旧326/新418及历史来源保留 | 新入口材料未适配则模型启动前拒绝，不生成 |

原固定运行、数量及误关联详见[验收记录](../../supplements/technical-analysis-fixed-source-acceptance.md)；后续七入口实验见[查询失败实验](../../supplements/jdt-definition-query-failure-experiment.md)。这些旧身份绝不改名成新R1–R4输出。
本轮新四命令的固定源码实际进展另见[按入口证据验收记录](../../supplements/technical-entry-evidence-acceptance-20260929.md)，不能与上述历史三命令结果混同。

## 6. 本轮验收与停止范围

必须通过：四操作独立和准确来源、每入口材料发布重开、具名错边不进入确定调用、正常外部调用不冒充缺口、重要前端单元/排序不丢、历史结果不变、零业务模型调用。

Lombok与间歇长等待独立报告：通过、未通过、尚未确定原因。允许诚实的未解析/不支持；不允许已确认的错误边作为正确证据。不能以文件齐全宣布准确性全过，也不能为宣称全过而临时增加通用分析系统。

本轮止于第五步。业务质量、模型长材料策略、跨版本增量复用以及参数化SQL执行不纳入。实施范围与真实验收按已批准的[四命令计划](../../plans/technical-analysis-cli-and-vue-cleanup-design.md)及本轮用户授权执行；设计文档本身不扩大客户工具、模型或业务生成授权。
