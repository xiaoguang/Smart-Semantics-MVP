# Activity：从保存的代码材料解释局部业务

本组是[Step06](../../analysis-steps/06-flow-interpretation.md)的内部详细设计，状态为**2026-09-23分支实现已接通、325包历史真实运行结束；步骤1修复已定向验证，最终范围及业务质量仍未完整验收**；实测范围和限制见[全量验收记录](full-step05-acceptance-20260923.md)。当前已实现确定性`ActivityMaterialProjector`、packet-local短ref、保存XML依赖读取，以及`ActivityExplainer.explain(ExplainCodeReadingMaterialsRequest)`直接DRAFT/完整REVIEW接线；实际Provider输入可见去重后的完整Java方法、调用/候选/实参与形参、条件/返回和已保存持久化材料。大Packet已接入有界导航、完整片段选择和packet级并发任务池，不再立即预算拒绝。durable执行不可变保存阅读决策和DRAFT/REVIEW attempt；已知安全错误可按YAML `activityRetry`对单个阅读决定或阶段做有界重试，坏JSON/结构错误只在明确配置后重试。同批及显式来源批次的成功stage严格核验，完整大包的阅读和全部slice结果可跨批零模型调用复用。直接测试已证明普通包失败继续其它包、失效Provider只阻断该绑定的后续包、并发包互不串源。正式部分M11发布/读取、v6运行登记、正式CLI分派、精确包选择、失败批次选择和私有失败摘要均已接线。固定325包真实批次已结束并保存418条，另一个入口的上游JDT缺口仍未取材；Step07双来源读取通过离线测试，未重新生成业务过程。历史M10入口保持原行为。

上述是分支已实现及历史真实运行事实，不代表本轮已经合入main或范围/语义验收完整。[收口记录](post-review-handoff-20260923.md)中的未验证草稿已保存，步骤1后续修复在585c797通过79项定向测试；步骤2的最终阅读范围、历史重开和配置通过8类80项定向测试、Spotless及独立复查，尚未完成本轮完整CI。本次[端到端设计](../../end-to-end-business-delivery-design.md)继续补范围完成性、历史只复用核对和下游同入口关联；不是从头再建Activity模块。

目标保持一个ActivityExplainer业务Interface；内部仅补材料投影、有限阅读调度和阶段保存，不创建另一套Agent、线程池、取证系统或业务分类器。

| 模块 | 输入 → 程序工作 → 输出 | 模型职责 |
| --- | --- | --- |
| [ActivityMaterialProjector](material-projection.md) | 已重开Packet → 短ref、去重、完整单元、导航/正文投影 → ActivityReadingPacket | 无 |
| [ActivityReadingCoordinator](large-material-reading.md) | Packet投影与模型容量 → 直接/导航分页/有限补读/完整slice → 一组可独立解释的阅读包与未处理范围 | 提出读取问题、选择完整单元和局部业务范围 |
| ActivityExplainer | 一个包或其slice → DRAFT/REVIEW、结构与scope校验、阶段保存 → 完整ReviewedActivity集合 | 解释行为、判断规则适用范围、核对完整实际稿件 |
| 既有job pool/private store/publisher | 不可变结果 → 并发、retry、稳定聚合和来源/覆盖 → M11 Activity checkpoint | 无 |

业务生成默认配置为Codex登录服务 `Luna / high`；每次独立请求显式发送实际材料，不继承当前对话的隐藏上下文。Provider/model仍可通过唯一YAML显式配置；历史Luna及Terra/xhigh产物不改标签、不强制重跑。

开发分工：Astra/ultra方向、裁决、文档、debug；Luna/xhigh写直接测试；Terra/xhigh实现。当前按已批准端到端计划执行离线修改，不重新生成Activity；真实Step07小样必须等离线核对及本地验证完成，小样展示后的全仓扩大仍需用户确认。

## 阅读顺序与交付准则

1. [材料投影](material-projection.md)：保证模型实际看见完整方法与XML条件，短ref和技术原件各自有明确职责。
2. [大材料阅读](large-material-reading.md)：全范围导航分页、补读和独立slice；没有无限“摘要再摘要”。
3. [接入与版本](integration-contracts.md)：明确新旧来源、M11和下游，不以M10伪装Step05。
4. [模型执行](../model-job-execution.md)：每阶段可配置retry，成功阶段复用、包级失败隔离及手动新batch。

“全部入口都有某个Activity”只证明ID集合覆盖，不能证明每个slice和规则已解释。成功slice结果保存；必需范围未处理或阶段穷尽失败时整个batch非成功，不自动进入Step07。业务完整性仍需模型完整REVIEW与真实样例人工核对。
