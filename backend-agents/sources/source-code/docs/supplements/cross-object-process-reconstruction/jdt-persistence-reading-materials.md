# JDT、持久化补全与阅读材料：已整合设计索引

2026-09-17批准的Step01–05设计已经实施，其生产合同现由逐步设计与内部Modules维护。本页保存原方向与验收路由，不再是与主设计并列的上游权威。

| 原主题 | 唯一owner |
| --- | --- |
| 冻结来源/全入口 | [Step01](../../analysis-steps/01-verified-source-inventory.md)、[Step02](../../analysis-steps/02-application-discovery.md) |
| JDT-only、完整方法/调用/候选、共享索引 | [Step03](../../analysis-steps/03-program-graphs.md)、[JDT Modules](../../modules/java-code-engines/README.md) |
| 官方MyBatis部件/JSqlParser、完整XML、动态SQL限制 | [Step04](../../analysis-steps/04-proven-code-facts.md) |
| 唯一入口阅读材料、来源/覆盖/保存 | [Step05](../../analysis-steps/05-business-flows.md) |
| 新材料直接进入Activity、完整阅读与语义slice | [Step06](../../analysis-steps/06-flow-interpretation.md)、[Activity Modules](../../modules/activity-explanation/README.md) |
| 精确版本及历史读取 | [集成合同](../../modules/activity-explanation/integration-contracts.md) |

原决定是以JDT导航/Core完整语法为Java唯一生产路径，以可选持久化材料补全原文，把入口阅读材料统一交Step05所有。JavaParser、严格图/Fact/Proof/Flow/Capsule/M10 producer退出新生产；历史配置、receipt和wire按精确版本只读。SQL解析不支持不丢原XML；技术候选/边界不被伪造为唯一动态dispatch。

原实施顺序A工具实验、B集成、C完整01–05固定源验收均已结束；当前保存325包、326入口处置、1项明确导航失败。事实与限制见[交付](../jdt-persistence-reading-materials-delivery.md)和[完成计划](../../plans/jdt-persistence-reading-materials-implementation-plan.md)。不是新一轮JDT或工具实验授权。

新Step05→Activity现已另行详细设计，尚未实现。当前新材料仍只支持READING_MATERIALS_ONLY；旧326条Activity保持原解释，不补SQL、不重跑。真实材料逻辑推演见[新例子](../../examples/activity-material-end-to-end-walkthrough.md)。原三阶段业务质量问题见[三例历史实测](three-case-acceptance-result-20260916.md)，不会因上游取材完成变成已解决。

本轮只整合文档，不调用Provider、不运行代码/测试/构建/JDT、不修改快照或受保护more-findings.md。
