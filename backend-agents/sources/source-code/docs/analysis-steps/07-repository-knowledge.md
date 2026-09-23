# 仓库业务过程

> 固定key：repository-knowledge；目录：steps/07-repository-knowledge/。详细职责见[业务过程Modules](../modules/business-process-discovery/README.md)，当前/目标边界见[主设计](../DESIGN.md)。

## 1. 目的与输入

Step07从完整Activity和同源原文发现可合读的业务用法，重建开始、条件、分支、对象变化与结束结果，发布business-processes.md。共享方法、同表或相近名称不是业务时序证明。

当前默认运行仍可重开旧Activity/coverage、M10和同源verified inventory；326条旧Activity原样使用。Step07内部协议、Corpus、Publisher和持久executor也已支持从M11 v3 Activity与保存的Step05 CodeReadingMaterialSet进入同一过程管线，且拒绝混合/缺失来源与必需范围未完成的Activity。正式CLI已按state-v4进入该分支，核对M11与Step05引用；不能把任意新取材state直接当作已有Activity。新来源完整CLI/store到Markdown的离线贯穿验收仍待补齐。

新来源由运行的readingMaterialCheckpoint和Activity行的materialId/materialSource/sliceKey/originalSourceRefs固定；coverage-v3没有嵌套materialSource。本次coverage-v4目标仅增加范围完成摘要。FrozenAnalysisCorpus按显式类型重开历史M10或新Step05，核对同源basis和sourceRunId/modelBatchId归属；不猜形状、不补写旧Activity、不运行03–06。见[Corpus](../modules/business-process-discovery/frozen-analysis-corpus.md)及[接入合同](../modules/activity-explanation/integration-contracts.md)。

## 2. 内部接力

BusinessProcessDiscovery.discover隐藏目录、取材、重建与归并；BusinessProcessPublisher.publish验证封闭结果并确定性发布。两者是Step07内部Interface，不增加公共Agent方法。

| 内部责任 | 输入→工作→输出 |
| --- | --- |
| FrozenAnalysisCorpus | 完整引用→同源验证/只读查询→Activity、statement、来源和冻结文本 |
| RepositoryBusinessCataloger | 全卡片或旧目录→首次目录或全局系统认识/选材→候选/问题/清单 |
| ProcessMaterialAssembler | 清单→实际读取→一次CHECK保留/移出及一次可空补读→完整包 |
| CandidateProcessReconstructor | 完整包→事实DRAFT→WRITE→最终RULE_REVIEW→完整已审过程 |
| RepositoryProcessConsolidator | 全过程业务完整视图→关系/无损归并裁决→封闭目录 |
| BusinessProcessPublisher | 目录/覆盖/来源→验证、排版→五文件 |

旧目录仅作资料时不重跑其分片/merge；无旧目录保留首次发现。系统类型来自模型读取项目说明及全仓导航，可混合/未知；常识只提出可证伪问题，Java不设行业路由。

每候选一次CHECK，首批保留集须明确；全文按ActivityId去重，variant用法保留，context不自动变成员。原文在DRAFT前到位；WRITE只见完整事实DRAFT，最终RULE_REVIEW见同包+实际DRAFT+WRITE，返回完整processResult与私有corrections。之后无模型润色。

## 3. 结果与保存

保留目的、范围、对象、参与者、ActivityUse、stage.narrative、进入/拒绝条件、动作、状态、结果、转移、规则适用用法、知识正文、来源和certainty。引用可以来自已读context/原文，规则仍须限定用途；Java不自动判断中文蕴含。

当前producer v4；公共catalog/coverage/业务Markdown v2，来源JSONL/Markdown v1；三阶段私有结果model-job-reviewed-result-v3。历史producer2/3原字节保留。见[Publisher](../modules/business-process-discovery/business-process-publisher.md)。

不同候选按全局/账户上限并行，同候选三阶段顺序、绑定不变，完整成功job立即保存，按原ordinal聚合。全部处置及归并闭合才正式发布，preview不声明全仓完成。

## 4. 失败、复用与当前状态

Step07没有自动重试；新Activity重试政策不扩展到本步。fatal停止新派发，已开始且前置合法的job排空保存，不归并/正式发布。显式新批次只复用完整匹配的三阶段job，旧pair/孤立DRAFT/WRITE不可冒充完成；不重跑上游。

合法未命中、材料不足或无法无损合并保留限制/原过程；未知ID/ref、错来源、坏Schema/Provider失败不能以PARTIAL隐藏。Activity/候选/已审过程三个分母保留，variant遗漏须语义审阅。

三阶段、系统认识、CHECK保留集、v4发布、保存/复用和preview均已接线；新Step05来源已接到Step07内部协议与持久executor，正式运行编排已接线。历史12次请求的三例未全通过：采购最终未发送、销售用法引用非法、调拨仍有条件错误，见[实测](../supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。可读性认可不等于准确性或全仓通过。

本次收尾设计见[端到端业务交付](../end-to-end-business-delivery-design.md)：核对Activity必需范围，新增同入口导航、候选局部编码及WRITE字段限制，再从正式CLI做离线贯穿和真实样例。测试Terra/xhigh、代码Sol/xhigh、设计/调试Astra/ultra；本轮仅文档，未运行测试/构建/JDT/业务模型。

## 5. 本次待评审的端到端增量

新418条必须拥有匹配自身的首次目录；当前新Step05分支拒绝旧catalogFromModelBatchId，不顺带扩建旧目录导入。全部原Activity身份保留，同packet/entry仅在导航中一起展示，模型可将多个切片放进同一业务阶段或作为支撑，不强制每条一个步骤。

Assembler一次生成可逆候选局部A/T映射，保持完整原文和两稿，复用已有$defs。WRITE只改展示字段，最终RULE_REVIEW对照原文同步核对正文与结构。新的Discovery/Prompt/私有结果升版，公共五文件、Publisher v4排版和三个owner保持。

原NOT_COLLECTED的技术缺口、普通语义未知、必需执行未完成分别处理；不是见PARTIAL就拒绝，也不是见activityBatchComplete=true就不核对范围。样例preview先交用户看，未经扩大同意不得全仓。三阶段失败政策不自动改为Activity的阶段retry。
