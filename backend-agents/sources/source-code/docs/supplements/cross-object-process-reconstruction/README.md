# 跨对象过程研究与历史验收索引

本目录的有效设计已经整合到[主设计](../../DESIGN.md)、[Activity Modules](../../modules/activity-explanation/README.md)和[业务过程Modules](../../modules/business-process-discovery/README.md)。此处维护研究缘由、历史输入与验收状态，不再作为另一份活跃生产合同。2026-09-23新增的[端到端收尾详细设计](../../end-to-end-business-delivery-design.md)统一说明剩余工作、模块改动和验收门槛；具体字段仍由以下Module拥有。

## 当前事实

Step01–05已保存325包/326入口处置；新Step05→Activity已接通，真实生成418条，正式Step07新来源分支也已接线。另有1个上游导航失败入口无包。新范围完成性和13个生产/测试文件中的审查修复未完成验证，不能把运行结束等同全量语义验收。旧326条Activity仍来自历史M10，保持不变。Step07系统认识、聚焦阅读、DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存与producer v4已实现；旧三例未全通过。Step08仅历史读取/查询/重渲染。

Activity阶段重试不自动适用于Step07，也不授权重跑旧Activity、补写SQL、继续三例或全仓生成。more-findings.md保持原文。

## 合同现在在哪里

| 原补充主题 | 唯一详细owner |
| --- | --- |
| JDT/持久化/Step05材料 | [03](../../analysis-steps/03-program-graphs.md)、[04](../../analysis-steps/04-proven-code-facts.md)、[05](../../analysis-steps/05-business-flows.md) |
| 新材料Activity接入/大包阅读 | [06](../../analysis-steps/06-flow-interpretation.md)、[Activity Modules](../../modules/activity-explanation/README.md) |
| 系统认识/聚焦读取 | [Cataloger](../../modules/business-process-discovery/repository-business-cataloger.md)、[Assembler](../../modules/business-process-discovery/process-material-assembler.md) |
| 事实/写作/最终核对 | [Reconstructor](../../modules/business-process-discovery/candidate-process-reconstructor.md) |
| 来源/保存/发布 | [Corpus](../../modules/business-process-discovery/frozen-analysis-corpus.md)、[Publisher](../../modules/business-process-discovery/business-process-publisher.md) |
| Prompt/响应 | [中文Prompt合同](../../references/semantic-interpretation-prompts.md) |
| 执行/重试/配置版本 | [模型执行](../../modules/model-job-execution.md)、[新Activity集成](../../modules/activity-explanation/integration-contracts.md) |

## 历史输入与不可变记录

历史Activity run为analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e；源码/M10 run为analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b。旧目录run为analysis-run:4125a702ec8489a65792e93d5d77cd1630b7933c3e34f928b93c9dba85ac3224，process-catalog/business-catalog-merge COMPLETED。保存目录有24候选/138个不同成员，全部326Activity可读；更早14候选/46过程是另一历史运行。

这些ID仅用于定位；执行必须重开实际完整引用，不能凭ID伪造receipt。历史目录作资料与新任务精确复用不同。

- [原交付记录](delivery.md)、[研究比较](experiment-comparison.md)、[三例原始实测](three-case-acceptance-result-20260916.md)：保留历史结论。
- [当前实施差异](implementation-status.md)：分支已实现、未验证修复、设计目标和真实质量分开。
- [原三例材料推演](walkthrough.md)：历史人工材料核对，不是新模型结果。
- [验收边界](acceptance.md)：已发生请求与后续验收条件。
- [新的真实Step05→Activity→Process推演](../../examples/activity-material-end-to-end-walkthrough.md)：记录既有原文取材推演，不是本次新模型结果。

受保护more-findings.md的SHA256为59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e。本轮文档整合不执行代码、测试、构建、JDT、模型、commit或push。
