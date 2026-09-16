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

有直接sourceRefs的过程、阶段或规则只显示一个本地“查看依据”链接；该链接跳到本过程的来源索引。索引按过程、阶段和规则列出全部依据，以“文件名：起止行”链接到sources.md中对应短ref锚点；不任意只保留前N条，也不在业务正文堆放S编号墙。原Activity名称可用于范围表，但不伪装为业务阶段。

来源项允许留空；不强制增加缺链接说明、补齐逻辑、反查或专项验收，不阻塞主业务交付。只复用现有来源，优先确保业务叙述、分支、规则和结果可读。

### 独立来源视图

sources.md只从result已有SourceReference生成，每项含S编号锚点、仓库内文件路径、原行范围、完整snippet。短ref只接受S加数字的闭合形式；使用稳定顺序、安全Markdown围栏和路径文本转义，正文、路径含反引号、换行或特殊字符也不能破坏页面。它不包含运行凭据或宿主绝对路径，不访问客户文件。

与business-processes.md一起复制即可保持相对链接。source-refs.jsonl继续供程序查询，来源页只是其确定性可读视图，不是新证据层。

## 正式合同

canonical地址仍为REPOSITORY_KNOWLEDGE/1/business-process-publisher，当前producer v2，本轮取材路径目标producer v3；公共五文件Schema不变：

| 文件 | schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

来源页artifact type已存在，不新增第六项正式文件。新增读取完整性/未命中信息留在私有ReadingRecord，SourceReference五字段不变。版本见[补充合同](../../supplements/cross-object-process-reconstruction/module-design.md#7-运行接线私有保存和版本)。

## 保存与读取

Discovery封闭result前，把补读的同源新片段按文件、范围和原文去重，统一分配不冲突的最终S编号，同步所有来源字段。不同包局部相同编号不可直接合并。原M10来源复用；Publisher仍只消费result，不打开文件、不补证据。

五项在同一canonical交付安装；存储策略、schema registry、exact-file数量、reader、artifact闭集和fixture同批更新。canonical store只接受完整四文件v1历史合同，或完整五文件v2当前合同，不能混用。当前Reader只重开五文件v2，并在渲染前核对catalog使用的全部短ref与source-refs.jsonl恰好一一对应；重复、缺失或非法短ref均失败。Reader从catalog/coverage/refs可重渲染出两个逐字节一致的Markdown，无Provider和上游调用。

历史四文件v1产物原样保留；不能静默补narrative或把旧输出说成v2。上游读取策略不因此重置。PROCESS_CATALOG和三个owner不改，render()仍属Step08，来源页走artifact查询。

## 成功、失败和测试

所有短ref有真实来源，所有链接锚点存在，重要结构字段和narrative完整保存，PARTIAL可见。未知ref、错误owner、缺处置、坏schema或渲染丢字段明确失败。Publisher不把结构通过当语义优秀。

当前五文件v2读写与确定性渲染已实现；新补读来源的汇总接线尚未实施。发布器不改业务正文，不因取材变化整体升级公共Schema，不把历史结果称为新语义验收。

Luna RED：五文件发布重开、正文不含源码块、来源页完整且围栏安全、复制后相对链接有效、规则用法名字正确、未处理名称可读、零模型渲染。Terra只实现格式与契约，不润色或猜测业务。
