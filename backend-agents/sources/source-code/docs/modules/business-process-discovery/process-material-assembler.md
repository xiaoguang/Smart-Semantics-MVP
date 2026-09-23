# ProcessMaterialAssembler

## 目的与内部Interface

把模型选择的Activity和同源原文变成自包含ProcessReadingPacket。内部collect/assemble执行确定性读取；一次PROCESS_READING_CHECK由Discovery使用现有Provider调度。不是公共检索接口，也不新增语义解析器。

当前旧Activity/M10/冻结文本取材已实现。[FrozenAnalysisCorpus](frozen-analysis-corpus.md)也能从验证后的新Activity provenance、Step05 packet和同snapshot冻结文本解析实际来源，并隔离packet-local短ref。M11 v3 reader、ProcessDiscoveryRequest/Result、Publisher和持久Step07 executor已有独立Step05分支；正式CLI选择仍待运行编排层接入。任何运行接线都不得把新Step05强转M10。

## 首批实际读取

完整Activity按ID取一份，保留目的、对象、参与者、输入、条件、步骤、结果、规则、公式、问题、限制和全部原字段。所有(ActivityId,variant)用法及context身份分别保存。声明为context不自动成为成员。

读取请求可选已有SourceRef、文件范围、全文或字面量搜索；purpose说明调查问题。每个实际片段按请求/返回顺序分配R1…Rn，保存requestId、purpose、真实范围/文本、complete、preview、totalLineCount、遗漏/未命中原因。一次搜索多命中可分别选择；未命中不造R正文。

当前Step07仅INITIAL WHOLE_FILE超过120行时返回真实前120行导航预览，标preview=true/complete=false；小文件完整，SUPPLEMENTARY WHOLE_FILE仍完整。CHECK外层savedSourceLocators给出同文件已有M10来源ref/真实完整范围和前8行原文，只是定位目录，不混入已读sourceExcerpts。保留预览R只保留该预览，不偷偷恢复整文件。该既有导航约束不用于新Activity的语义slice，后者见[大材料阅读](../activity-explanation/large-material-reading.md)。

## 每候选一次CHECK与补读

每个进入重建的候选必须有一次模型CHECK；Java不判断是否充分。输入含调查背景、实际完整Activity/原文、R记录、未命中/预览说明、全仓可召回导航及完整文件目录。

CHECK输入不重复已完整提供Activity的导航卡；未读卡仅省terms，仍可由名称/目的/对象/来源召回。CHECK副本可省不用的statementDirectory；完整原Activity及最终DRAFT包仍保留它。实际已读Activity与未读导航ID并集必须是全体，不按候选成员名单缩小召回范围。

响应：

~~~text
name / purpose / scope                 required但可null；null沿用原值
activityUses                          完整最终非空集合，含activityId/variant/role
contextActivityIds                    完整最终集合，可空；空不表示沿用
retainedReadingRecordIds               首批实际R的完整保留集，可空；空表示全部移出
supplementaryRequests                  一次补读，可空
unresolvedQuestions / selectionNotes   具体未知与保留/移出/补读说明
changedActivityDispositions            仅必要增量，移出最后成员必须明确去向
~~~

先选择R，再按相同真实来源去重正文、合并所保留目的。首批原记录不删除、不重读；同一片段来自多请求时保留任一R即保留原文。新增完整Activity与context随补读取回。补读后不再第三次选材；仍缺则具体限制。空补读不表示材料充分。

## 最终包与调用

process-reading-packet-v1含candidate、完整reviewedActivities、唯一statementDirectory、实际sourceExcerpts与readingLimitations。包内来源来自实际已读allowlist，不来自旧候选/M10 owner边界。ActivityUse.activityId须在最终成员内，stage/rule用法须属于本过程；statement owner不改，context/新冻结文本可作为依据。

DRAFT外层实际输入另带investigationContext和readingSelections，分别保存问题/系统假设与实际用途/选择metadata；不重复片段或重新发送移出原文。最终RULE_REVIEW复用同一完整外层输入并加actualDraft/actualWriting；WRITE仅收完整actualDraft。

允许只去掉重复表示：完整Activity不再附一份逐字段statements文本；handle集中在statementDirectory；响应Schema相同allowlist用根$defs/$ref复用，required/enum/未知ID拒绝不放宽。不是有损业务摘要。

每阶段核对实际序列化输入、Prompt、Schema和输出空间。完整包或最终核对超容量时不静默截断、不删条件/公式、不自动补料循环；保存实际完成内容与明确未处理/失败原因。

## 保存、当前状态与测试

选择/检查为process-reading-decision-v2/producer v4；CHECK Prompt v4，历史v1/producer v3仅严格读。实际R、预览、导航、最终保留、背景、Schema/Prompt和绑定参与指纹。新Step05来源可从M11 v3重开后经显式ProcessDiscoveryRequest进入同一Assembler；其余上述取材合同已有生产接线。当前剩余缺口是正式运行入口选择，不是另造Assembler或把Step05包装成M10。

定向验证应检查全文/公式/条件不丢、多variant共享正文、真实行段、空保留/空补读、context引用合法但非成员、未知R/ref拒绝、重开与零上游调用。来源/容量结构正确不表示真实语义充分。
