# 当前实施与真实质量差异

本页只区分代码状态、目标接点和历史验收，不拥有生产合同。权威见[主设计](../../DESIGN.md)、[Activity](../../modules/activity-explanation/README.md)、[Step07 Modules](../../modules/business-process-discovery/README.md)。

## 已实现与尚未实现

| 范围 | 当前事实 | 本次剩余目标 |
| --- | --- | --- |
| Step01–05 | JDT-only/Core、可选MyBatis/JSqlParser、统一阅读材料、保存重开与固定源验收完成；325包/326处置/1导航失败 | 不重跑、不重建；现行index v2、persistence v1、material set v1保持 |
| 新材料运行 | state v4/output v5只支持READING_MATERIALS_ONLY | repository-run-config-v3、execution-config-v4、run-output-v6的新Activity接入 |
| 历史Activity | ActivityExplainer依赖旧M10 BusinessMaterialBuildResult；326条已审结果可严格重开 | 新Step05直接投影、无损去重、分页模型阅读/完整语义slice、完整DRAFT/REVIEW及目标来源/覆盖 |
| 新Activity执行 | 旧执行池/Provider/独立批次基础存在；新阶段策略未接线 | 包间并行、阶段尝试/backoff、失败隔离与人工新批次精确复用；旧Step07无自动重试不变 |
| 全局系统认识/聚焦选材 | DefaultBusinessProcessDiscovery已发送冻结README/全体导航，selection v2返回assessment/问题；CHECK v4保留/移出R和一次补读 | 原样复用；不加行业分类器 |
| 过程三阶段 | reconstructRaw已执行DRAFT v4→WRITE v1→RULE_REVIEW v1；中间实际Schema门禁及最终processResult解析存在 | 原样复用，不把双轮当当前 |
| 私有保存/复用 | model-job-reviewed-result-v3/pipeline v1/producer v4保存完整input、packet、映射、三稿；历史v2 pair不冒充三阶段 | 新Activity私有v4独立，不整体升级过程格式 |
| 五文件/预览 | producer v4可读正文、无折叠、knowledge certainty、sources.md与历史2/3版本读取已实现 | 新来源receipt/basis接线尚未实现；公共五文件schema保持 |
| Step07材料来源 | Corpus/Publisher现按旧Activity+M10与同源inventory重开 | 按新Activity coverage materialSource精确选择新Step05，保留实际已读scope与原件来源 |
| Step08 | 历史reader/query/renderer | 无新生成目标 |

具体目标字段/版本以[Activity集成合同](../../modules/activity-explanation/integration-contracts.md)为准；文档里的目标不表示已经有可执行CLI或Schema资源。本轮没有改代码、测试、配置或生产Prompt。

## 历史三例：程序完成不等于质量通过

原SELECT与后续实际批次合计12次请求，全部返回：SELECT1、CHECK3、DRAFT3、WRITE3、RULE_REVIEW2。采购计划中的第13次请求未发送，不能计为已调用或已审。

| 样本 | 实际终态 | 已知限制 |
| --- | --- | --- |
| 采购 | DRAFT/WRITE已保存，最终RULE_REVIEW因真实容量未发送 | 原文+DRAFT+WRITE超窗；不能删稿强行调用或自动续接 |
| 销售 | 最终RULE_REVIEW有返回，但未保存合法已审job | 7个未定义查询用法引用；另有业务适用范围问题，原响应保留 |
| 调拨 | 完整合法三阶段job保存并可零模型导出 | 拒绝/结束规则仍有两处强审核配置限定遗漏，人工准确性未通过 |

用户接受可读性与业务联系；该意见和事实问题并存。未关闭全仓coverage，未执行全仓归并，不把三个样本或自动fixture说成全仓验收。原结果与详细位置见[不可变三例记录](three-case-acceptance-result-20260916.md)，旧研究见[实验比较](experiment-comparison.md)，既往交付见[delivery](delivery.md)。

原实现记录里的596项CI/SpotBugs/PMD属于当时执行证据，不是本轮重新运行。本轮只做文档与实际保存源码核对。旧输入、raw响应、FAILED状态、326 Activity及more-findings.md不变。

## 后续验证边界

当前只确认设计逻辑是否闭合：实际材料怎样进入模型、哪些原文真的读到、语义范围如何拆分、失败/未读范围怎样保留、成功阶段怎样复用、Step07怎样选准确来源。见[真实推演](../../examples/activity-material-end-to-end-walkthrough.md)。

后续实施才编写新增/直接覆盖的fixture，验证新来源/投影/阅读/coverage/重试/历史读取。真实Activity质量需在明确样本范围、完整输入和当前模型绑定下审阅；自动测试不能保证中文事实。现存三例优化、扩大样本或全仓执行需另行明确任务，不能由文档整合自动启动。
