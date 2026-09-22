# 系统认识、聚焦阅读与三阶段成稿：已整合索引

此设计已经实施，其有效合同现由[Step07](../../analysis-steps/07-repository-knowledge.md)及[业务过程Modules](../../modules/business-process-discovery/README.md)维护。本页不再重复可漂移的调用/字段规则。

## 原决定与当前位置

| 原决定 | 详细owner |
| --- | --- |
| 同一次全局选材形成系统认识/可证伪假设，常识只提出问题 | [RepositoryBusinessCataloger](../../modules/business-process-discovery/repository-business-cataloger.md) |
| 实际R记录、明确保留/移出、一次补读、首读预览不是已读全文 | [ProcessMaterialAssembler](../../modules/business-process-discovery/process-material-assembler.md) |
| DRAFT事实→WRITE正文→最后RULE_REVIEW核对实际写作 | [CandidateProcessReconstructor](../../modules/business-process-discovery/candidate-process-reconstructor.md) |
| 完整input/三阶段/raw保存、容量失败与精确复用 | [Reconstructor](../../modules/business-process-discovery/candidate-process-reconstructor.md)、[执行合同](../../modules/model-job-execution.md) |
| 最后审阅之后无模型润色，producer v4可读正文/来源与历史字节 | [BusinessProcessPublisher](../../modules/business-process-discovery/business-process-publisher.md) |
| 不拼stage数组制造生命周期 | [RepositoryProcessConsolidator](../../modules/business-process-discovery/repository-process-consolidator.md) |
| 中文任务文本/响应边界 | [Prompt合同](../../references/semantic-interpretation-prompts.md) |

## 研究缘由与真实状态

用户认可可按办理顺序阅读的业务正文；将事实推理与写作分开改善可读性，但WRITE仍可能把已审核写成未审核、把订金/本次付款写成应付金额。因此最终核对必须同时看原文、事实稿与实际写作全文。

三阶段接线、私有结果v3、producer v4、系统认识和CHECK最终保留集均已完成。原三例12次请求没有全部通过：采购最后核对未发送，销售最后返回引用7个未定义用法，调拨完整保存仍有配置限定错误。历史原始结果见[三例记录](three-case-acceptance-result-20260916.md)，[实施状态](implementation-status.md)区分程序与质量。

新Step05→Activity、大包模型阅读和阶段重试属于[Activity设计](../../modules/activity-explanation/README.md)，未实现；不改变本页历史Step07无自动重试或原326Activity。此次仅逻辑设计确认，不自动继续三例或全仓。
