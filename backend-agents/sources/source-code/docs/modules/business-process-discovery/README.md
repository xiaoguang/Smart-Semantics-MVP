# 业务过程发现与重建：模块总览

本模块簇把完整ReviewedActivity与同源保存材料转成仓库业务过程。全仓目录发现负责“哪些活动值得一起读”，详细重建负责“具体怎样办理”。目录卡不能替代完整Activity和关键源码。

## 两个深Interface

`BusinessProcessDiscovery.discover(ProcessDiscoveryRequest)`返回封闭ProcessDiscoveryResult；`BusinessProcessPublisher.publish(ProcessDiscoveryResult)`验证并发布。它们是Step07内部Interface，唯一公共RepositoryAnalysisAgent方法不变。

| 内部Module | 输入→工作→输出 | 模型 |
| --- | --- | --- |
| [FrozenAnalysisCorpus](frozen-analysis-corpus.md) | Activity来源manifest→按旧M10/新Step05精确重开→同源只读查询 | 无 |
| [RepositoryBusinessCataloger](repository-business-cataloger.md) | 全部导航/旧目录/项目说明→候选、系统假设、问题与首批清单 | 首次目录；一次全局选材 |
| [ProcessMaterialAssembler](process-material-assembler.md) | 清单→实际读取→明确保留/移出与一次补读→完整包 | 每候选一次CHECK |
| [CandidateProcessReconstructor](candidate-process-reconstructor.md) | 完整包→事实/正文/最后核对→完整过程 | DRAFT→WRITE→RULE_REVIEW |
| [RepositoryProcessConsolidator](repository-process-consolidator.md) | 完整业务投影→无损归并与关系裁决 | 一次DRAFT+REVIEW |
| [BusinessProcessPublisher](business-process-publisher.md) | 封闭结果→catalog/coverage/正文/来源两文件 | 无 |

三阶段、systemAssessment、问题选材、CHECK最终保留集、v4发布与私有完整结果已实现。新Step05→Activity及其来源接入是未实现目标，由[Activity集成](../activity-explanation/integration-contracts.md)维护。当前Corpus/Publisher仍按旧Activity/M10引用重开。

## 共同不变量

- Activity全文按ID保存一份；(ActivityId,variant)不同用法分别保留，同一Activity可进入多个过程。
- context可供理解或规则引用，但不自动成为过程成员。
- 原文在DRAFT前到位；WRITE只见完整实际DRAFT；最终核对见完整包、DRAFT和WRITE。
- 模型返回最终完整过程，Java只校验、编号、保存与排版，不写行业规则或判断中文蕴含。
- 具体条件、状态、否定词、金额用途、公式和适用用法不能压成标题或泛泛条件。
- SUPPORT/STANDALONE/UNCLASSIFIED Activity既有知识由Discovery确定性投影，不制造假过程。
- 来源来自同一冻结basis，不因模型失败调用JDT/Step04/Step05/ActivityExplainer。
- 局部S只能经所属材料与packet映射后统一，不能裸编号跨包join。
- 所有Activity、候选、过程均有处置；结构闭合不能替代语义质量。
- Step07沿用无自动重试政策；新Activity的stage retry不扩展到本模块。

当前业务生成默认ChatGPT登录上下文Terra/xhigh；历史模型身份和326条Activity不改。历史三例12次请求未全通过：采购最终请求未发送、销售最终有7个未定义用法、调拨合法保存仍有条件限定错误。可读性获认可，全仓未执行。事实见[实测](../../supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)；本轮只用[真实材料推演](../../examples/activity-material-end-to-end-walkthrough.md)检查设计闭合。
