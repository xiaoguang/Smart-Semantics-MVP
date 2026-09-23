# 当前实施与真实质量差异

本页只区分代码状态、目标接点和历史验收，不拥有生产合同。权威见[主设计](../../DESIGN.md)、[Activity](../../modules/activity-explanation/README.md)、[Step07 Modules](../../modules/business-process-discovery/README.md)。

## 已实现与尚未实现

| 范围 | 当前实际事实 | 剩余事项 |
| --- | --- | --- |
| Step01–05 | JDT-only/持久化/统一材料已保存325包、326处置、1导航失败 | 固定复用，不重跑；index/persistence/material set版本保持 |
| 新Activity | `51b6625`中Step05投影、有限阅读、阶段保存/retry、M11和正式CLI已接通；真实418条 | 13个生产/测试文件中的审查修复未验证；范围完成性需独立表达 |
| 切片 | 300包单条、25包多条；销售统计18条 | 保留全部结果，下游看见来源组；不按标题删并或把18条当18业务 |
| 保存/复用 | 阶段reviewed-result-v4、大包packet-result-v1、批次result-v1；output-v6 | 原有效plan接续、公开成功部分保留、coverage-v4及离线只复用核对 |
| 配置 | YAML-v2支持activityRetry，reading限制仍写死；execution-config-v4 | 接通设计中的activityReading写YAML-v3；execution-config-v5 |
| 系统认识/聚焦读取 | SELECT v2、CHECK v4已发送项目说明/完整导航，并保留/移出与一次补读 | 同入口关系进入全部导航；不新增行业分类调用 |
| 过程三阶段 | DRAFT v4→WRITE v1→RULE_REVIEW v1，完整包及两稿进入最后核对 | 候选A/T短ID、WRITE展示字段限制、最终成稿准确性 |
| Step07私有保存 | model-job-reviewed-result-v3、pipeline v1、producer v4已实现 | 新映射/合同记录用v5（v4是Activity），历史严格读 |
| 新Step07来源 | SourceAnalysisExecution已将Step05+M11接到Discovery/Publisher；不同owner保留 | 正式CLI→真实Agent/store的完整离线五文件接力及新样例验收 |
| 五文件/历史 | Publisher v4确定性正文/来源、历史producer2/3严格读 | 保持公共格式及renderer，不重新开发；原三例准确性尚未全过 |
| Step08 | 历史reader/query/renderer | 无新生成目标 |

当前分支为`codex/step05-activity-full-generation`，已有实现提交但未完成本轮实现PR交付；本页没有重新运行CI。新范围目标、跨模块影响、版本、推演和停止条件见[端到端收尾设计](../../end-to-end-business-delivery-design.md)。它与本页历史事实分开，不把目标字段宣称为已落地。

新批次`analysis-run:e00cf448733fc078fe460e84b2ea41f51aec85347a4240a49154087748dfa2f9`的418条都保留；`activityBatchComplete=true`是旧执行判断，不足以证明历史大包的必需scope已完整处置。325条ANALYZED_WITH_GAPS和1条上游未取材的语义PARTIAL也不等于当前CLI完全不能运行Step07。必须分别看范围、接线和真实业务质量。


## 历史三例：程序完成不等于质量通过

原SELECT与后续实际批次合计12次请求，全部返回：SELECT1、CHECK3、DRAFT3、WRITE3、RULE_REVIEW2。采购计划中的第13次请求未发送，不能计为已调用或已审。

| 样本 | 实际终态 | 已知限制 |
| --- | --- | --- |
| 采购 | DRAFT/WRITE已保存，最终RULE_REVIEW因真实容量未发送 | 原文+DRAFT+WRITE超窗；不能删稿强行调用或自动续接 |
| 销售 | 最终RULE_REVIEW有返回，但未保存合法已审job | 7个未定义查询用法引用；另有业务适用范围问题，原响应保留 |
| 调拨 | 完整合法三阶段job保存并可零模型导出 | 拒绝/结束规则仍有两处强审核配置限定遗漏，人工准确性未通过 |

用户接受可读性与业务联系；该意见和事实问题并存。未关闭全仓coverage，未执行全仓归并，不把三个样本或自动fixture说成全仓验收。原结果与详细位置见[不可变三例记录](three-case-acceptance-result-20260916.md)，旧研究见[实验比较](experiment-comparison.md)，既往交付见[delivery](delivery.md)。

历史596项质量检查及Activity实现提交的544项测试均只对应各自当时的代码，不是当前未验证补丁的测试结果。本轮只做文档与实际保存源码核对。旧输入、raw响应、FAILED状态、326 Activity及more-findings.md不变。

## 后续验证边界

后续实施先验证审查修复、原418条离线范围核对和正式新来源五文件接力；不重做已经存在的投影器、线程池或Step07入口。失败/无法判定范围要展示已有材料和最小补做选择，不自动让全部325包重新调用模型。

随后以核对后的输入进行具名小样，检查否定、配置例外、金额用途和对象联系。必须先给用户看，再由用户决定全仓；设计和自动化通过不授权模型执行。过程仍三次请求，不因一个失败自动补第四次。本次文档没有变更生产代码、测试、Prompt资源、配置或运行数据。
