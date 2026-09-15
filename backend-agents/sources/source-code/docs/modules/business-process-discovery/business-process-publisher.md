# BusinessProcessPublisher

## 为什么存在与Interface

```java
BusinessProcessPublication publish(ProcessDiscoveryResult result);
```

零模型调用，输入是封闭的已审过程、所有处置、知识项、SourceReference和运行身份。不读Activity/JDT，不补业务正文；渲染问题不能成为新模型任务。

## 发布规则

校验现有ID/引用/三个分母、新增stage.narrative与rule.activityUseIds、直接Activity知识投影。覆盖完整与语义交付仍分别记录。新coverage的ActivityDisposition.name由原Activity确定性携带，避免未处理范围只有机器ID。

### 主Markdown

```text
# 仓库业务过程
## 业务目录
## <过程名称>
### 目的与适用范围
### 参与者与对象
### 步骤与分支
### 重要业务规则
### 结束结果
### 支撑活动与相关过程
### 待确认联系
### 来源引用
## 支撑、独立、未归类与未处理范围
```

每阶段主要显示业务标题、完整narrative、必要分支/转移和一个“查看依据”链接；不默认把技术Activity名称全列在括号里，不铺满裸S编号。

结构字段可在“条件与结果明细”折叠区全部保留，确保未被narrative重复的正文也不丢；核心允许/拒绝条件应在narrative可读，这是模型验收职责。规则显示适用用法及subject/when/otherwise/result，不能由Renderer拼出新的行业意义。

有直接sourceRefs的阶段，“查看依据”跳到本过程来源索引。索引按阶段/规则列所有依据，以“文件名：起止行”链接到sources.md中对应短ref锚点；不任意只保留前N条。原Activity名称可用于范围表，但不伪装为业务阶段。

来源项允许留空；不强制增加缺链接说明、补齐逻辑、反查或专项验收，不阻塞主业务交付。只复用现有来源，优先确保业务叙述、分支、规则和结果可读。

### 独立来源视图

sources.md只从result已有SourceReference生成，每项含S编号锚点、仓库内文件路径、原行范围、完整snippet。使用稳定顺序、安全Markdown围栏和转义，正文含反引号或特殊字符也不能破坏页面。它不包含运行凭据或宿主绝对路径，不访问客户文件。

与business-processes.md一起复制即可保持相对链接。source-refs.jsonl继续供程序查询，来源页只是其确定性可读视图，不是新证据层。

## 正式合同

canonical地址仍为REPOSITORY_KNOWLEDGE/1/business-process-publisher，producer v2：

| 文件 | schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

新artifact type为REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_SOURCES_MARKDOWN，其他type保留。详细版本和修改面见[变更清单](../../plans/business-process-discovery-and-reconstruction-change-design.md)。

## 保存与读取

五项在同一canonical交付安装；存储策略、schema registry、exact-file数量、reader、artifact闭集和fixture同批更新。Reader从catalog/coverage/refs可重渲染出两个逐字节一致的Markdown，无Provider和上游调用。

历史四文件v1产物原样保留；不能静默补narrative或把旧输出说成v2。上游读取策略不因此重置。PROCESS_CATALOG和三个owner不改，render()仍属Step08，来源页走artifact查询。

## 成功、失败和测试

所有短ref有真实来源，所有链接锚点存在，重要结构字段和narrative完整保存，PARTIAL可见。未知ref、错误owner、缺处置、坏schema或渲染丢字段明确失败。Publisher不把结构通过当语义优秀。

当前实现发布五文件v2：正文以已审narrative为主，阶段、规则与过程来源可链接到独立sources.md；source-refs.jsonl继续保持原v1结构。真实全仓的新版语义验收仍属于后续工作，发布器本身不改写业务内容。

Luna RED：五文件发布重开、正文不含源码块、来源页完整且围栏安全、复制后相对链接有效、规则用法名字正确、未处理名称可读、零模型渲染。Terra只实现格式与契约，不润色或猜测业务。
