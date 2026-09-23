# ProcessMaterialAssembler

## 目的与内部Interface

把模型选择的Activity和同源原文变成自包含ProcessReadingPacket。内部collect/assemble执行确定性读取；一次PROCESS_READING_CHECK由Discovery使用现有Provider调度。不是公共检索接口，也不新增语义解析器。

当前旧Activity/M10/冻结文本取材已实现。[FrozenAnalysisCorpus](frozen-analysis-corpus.md)也能从验证后的新Activity provenance、Step05 packet和同snapshot冻结文本解析实际来源，并隔离packet-local短ref。M11 v3 reader、ProcessDiscoveryRequest/Result、Publisher和持久Step07 executor已有独立Step05分支；正式CLI选择已接入，完整新来源端到端回归仍需补齐。任何运行接线都不得把新Step05强转M10。

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

选择/检查为process-reading-decision-v2/producer v4；CHECK Prompt v4，历史v1/producer v3仅严格读。实际R、预览、导航、最终保留、背景、Schema/Prompt和绑定参与指纹。新Step05来源可从M11 v3重开后经显式ProcessDiscoveryRequest进入同一Assembler；其余上述取材合同已有生产接线。本次剩余缺口是同入口导航、局部编码及正式贯穿验证，不是另造Assembler或把Step05包装成M10。

定向验证应检查全文/公式/条件不丢、多variant共享正文、真实行段、空保留/空补读、context引用合法但非成员、未知R/ref拒绝、重开与零上游调用。来源/容量结构正确不表示真实语义充分。

## 本次目标：候选局部引用编码与完整包v2

在CHECK结束、实际补读完成后，对封闭包构造process-local-reference-map-v1：按全局稳定顺序建立A→真实Activity、T→(ActivityId,field,index)原statement、S→真实SourceRef。仅模型投影使用短ID；私有映射保留真实来源身份。F/R负责选材定位，process/use键另有命名空间；阶段沿现有(processLocalId,order)，不新增stageLocalId。

process-reading-packet-v2保存同入口关联和编码合同；完整Activity文本只保留一份，statement目录用T定位原字段，不再重复字段正文。该目录不是摘要，模型能从完整Activity找到对应文字。编码贯穿候选成员/context、完整Activity、statementDirectory、Schema、DRAFT/WRITE/RULE_REVIEW；只改结构化ID字段，绝不全局替换业务字符串。

现有根$defs和RULE_REVIEW包装提升已经实现，应继续复用。新枚举用短A/T，$ref仍指根；无来源时allowlist为空的引用必须被最终校验拒绝，不能放任任意字符串。三阶段共用一份映射，raw响应不改；保存前按字段解码，再执行原来源归一化、身份生成和parser。

未读导航继续全仓可召回，已读Activity附来源组/入口键，context与成员目录明确分开。全文按ID去重不删除variant，也不因同入口就只传一个切片。重复来源只合并完全相同字节与范围，所有读取purpose保留。

容量观测记录编码前后实际input、Prompt、Schema、两稿及输出余量，不能只算字符串ID节约率；最后仍超容量即保留已生成稿和明确原因。不得为适配模型删任一稿件或增加自动摘要/层层聚合模块。

直接测试：不同候选均有A1/T1不串源；A10不按前缀映射；业务正文恰好含A1/T1字符串不被改写；完整包编码/解码业务内容一致；多variant与候选拆分保留；改变映射或输入使相关复用失效；实际最终核对保留原文和两稿。
