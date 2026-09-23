# BusinessProcessPublisher

## 目的与Interface

`BusinessProcessPublication publish(ProcessDiscoveryResult result)`消费封闭的已审过程、知识、处置、来源与运行身份。零模型；不生成业务意义或把中间WRITE当已审。当前实现会重开上游Activity/material receipt验证controls与依赖，不回读业务内容重新解释。

新来源接点已实现：按请求明确选择历史M10 ModulePublication或新Step05 StepPublication，验证完整引用与basis；不能把Step05传入仅支持旧M10的reader。由[集成合同](../activity-explanation/integration-contracts.md)与[FrozenAnalysisCorpus](frozen-analysis-corpus.md)维护。当前需补正式CLI使用真实store的全链验证，不重做Publisher。

## 正式版本

canonical地址仍为REPOSITORY_KNOWLEDGE/1/business-process-publisher，**当前producer v4已实现**。

| 文件 | schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

不新增第六个公共文件。新Activity来源/重试不把执行字段塞进五文件；只同步实际upstream引用、basis和对应读取。历史producer v2/v3严格保持其原渲染字节。

## 验证、来源与稳定发布

验证ID、真实ref、Activity/候选/过程三分母、stage.narrative、rule.activityUseIds、直接Activity知识。coverage名称来自原Activity，不能只留机器ID。coverage闭合和语义质量分别记录。

Discovery封闭result前按冻结文件/范围/原文去重来源，保存各材料/包局部编号到最终S编号映射，同步stage/rule/knowledge/relationship等所有字段。不能直接join不同包裸S或把新Step05 source:1当全仓唯一。原raw模型响应不改，只归一化副本。Publisher不再打开源码补证据。

五文件在同一canonical交付安装，store/registry/exact-set/reader/artifact同步。历史四文件v1与当前五文件v2精确区分；不能混装或静默补字段。现行reader按receipt选择producer2/3/4，并验证短ref唯一、缺失/非法引用和所需来源集合。按同版本从catalog/coverage/refs重渲染，两份Markdown逐字节一致，零Provider。

## 可读正文与来源页

business-processes.md按目录、过程目的/范围、参与者/对象、步骤/分支、规则、结果、支撑/相关、待确认和来源组织。producer v4与preview不输出details/summary折叠；stage.narrative与完整条件/动作/状态/拒绝/结果/转移、规则和知识正文直接可见。知识条目同一正文行显示原CONFIRMED/INFERRED/UNRESOLVED，不另行猜确定性。

规则显示适用用法与subject/when/actionOrDecision/otherwise/result。完整正确条件应在最终业务正文可读；renderer不能从结构猜行业意义。正文不堆技术Activity名称或裸S列表，不嵌大段源码。

已有直接ref显示“查看依据”，跳到过程来源索引，再以文件名/原行范围链接sources.md锚点。来源页由已有SourceReference确定性生成，保留完整snippet，安全围栏/路径转义，无宿主路径或凭据。source-refs.jsonl继续供程序查询。缺链接可空，不追加模型、取证或专项补齐流程。

## 预览、失败与直接验证

内部reconstructSelectedPreview返回选中候选的candidateId、原ordinal、处置/原因、所有typed process与归一化来源；renderPreview呈现全部片段。preview不调用全仓归并、不关闭全仓coverage、不伪造正式publication，不新增公共Agent/API。

未知ref、错owner、遗漏处置、坏Schema/来源或渲染丢字段失败；不得把结构通过称为语义优秀。当前v4五文件/reader、无折叠正文和preview已有实现。历史三例仅调拨有合法完整job且仍有条件错误，见[实测](../../supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

后续新来源接入仅定向检查Step05/历史M10 receipt和basis、来源不冲突、完整业务字段及历史重渲染字节。零模型排版不能成为再润色的理由。

## 本次目标：接收修正后的最终过程，排版不变

新的Discovery在发布前已经还原局部ID并关闭引用、范围与处置；Publisher不认识模型A/T键、不承担语义合并或再次核对中文。公共五文件格式和producer v4保持，source receipt按实际新Activity范围检查点引用。新版内容由现有身份算法自然产生新产物，不覆盖旧文档。

正式新来源验收必须用真实canonical store安装、查询五文件并重开，核对业务正文逐字来源于最终RULE_REVIEW，既有条件、公式及修正未被漏掉。小样走原preview出口，不调用仓库归并、不冒充完整publication；用户看过同意后再扩大。
