# 仓库业务过程发现与知识发布

> 固定key为repository-knowledge；目录steps/07-repository-knowledge/。两个接口及内部详细设计由[模块总览](../modules/business-process-discovery/README.md)拥有；[本次设计差异](../plans/business-process-discovery-and-reconstruction-change-design.md)是后续实施依据。

## 1. 目标和当前状态

本步已实现，不再是“替换旧ProcessExplainer”的新建工作。当前326条已审Activity形成14候选、46过程，21个含多个Activity，coverage CLOSED、semantic PARTIAL。旧340个singleton结果是历史前身。

但真实库存过程仍按接收、校验、主动作和返回描述，目录偏维护分类，未达到用户要的对象生命周期。**本次修正语义发现、完整业务正文与来源可读性，不重建流水线。**

## 2. 上游与接口

输入为完整ReviewedActivity checkpoint、Activity coverage和其M10 SourceReference，归属校验沿用现有存储。第一版不增加JavaCodeIndex/MethodKey查询；不导航、不重新组BusinessMaterial、不调用ActivityExplainer。

外部仍只有：
```java
ProcessDiscoveryResult BusinessProcessDiscovery.discover(ProcessDiscoveryRequest request);
BusinessProcessPublication BusinessProcessPublisher.publish(ProcessDiscoveryResult result);
```

## 3. 六个内部模块

| 模块 | 本步做什么 | 下游得到什么 |
| --- | --- | --- |
| M1 FrozenAnalysisCorpus | 重开原Activity/M10，建立statement和source只读查询 | 完整、同源材料 |
| M2 RepositoryBusinessCataloger | 原字段卡片含businessRules；模型发现领域和不同业务用法，分片后全仓合并 | 重叠候选及所有Activity处置 |
| M3 ProcessMaterialAssembler | 按候选取完整Activity，正文去重、用法保留；提供原文前8行的来源目录 | 自包含DRAFT输入；请求的完整片段供REVIEW |
| M4 CandidateProcessReconstructor | DRAFT及完整REVIEW解释生命周期、具体条件、规则和不确定性 | 已审过程、拆分或具体不足处置 |
| M5 RepositoryProcessConsolidator | KEEP/MERGE_INTO/REJECT及关系；不生成新业务正文 | 无损归并的唯一catalog |
| M6 BusinessProcessPublisher | 结构/来源/覆盖校验，确定性正文和来源视图 | 五项正式输出 |

完整设计逐模块见[模块总览](../modules/business-process-discovery/README.md)，中文任务见[Prompt](../references/semantic-interpretation-prompts.md)。

## 4. 本次合同增量

- 同一候选允许同Activity的不同variant；唯一性是(ActivityId, variant)，不是ActivityId。Java不解释variant中文。
- stage增加narrative；原条件、动作、状态变化、拒绝、结果和转移不删除。
- rule增加activityUseIds（模型使用activityUseLocalIds），明确规则适用分支；它不表示证据归属。规则可使用当前候选内已有来源，程序只拒绝未知或候选外引用。来源存在不代表适用所有子类型，具体适用性仍由模型审阅。
- coverage的ActivityDisposition增加原name用于可读范围表，程序投影，不增加模型判断。
- SOURCE目录不再只有盲编号；DRAFT选择已保存ref，REVIEW读其完整snippet。零请求可合法，不新建补料循环。
- 正文按业务阶段写，不以API参数处理模板冒充流程。

CONFIRMED/INFERRED/UNRESOLVED保留。没有材料就留下具体未知；禁止以已知条件缩水、臆造岗位或强制审批填满生命周期。

## 5. 正式文件与重开

canonical module保持REPOSITORY_KNOWLEDGE/1/business-process-publisher；producer v2。

| 文件 | Schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

五文件producer/reader/registry/fixture同步，旧四文件v1保留，不兼容补造新字段。上游checkpoint不升版。Markdown按阶段narrative展开；阶段“查看依据”跳过程来源索引，再以文件和行范围打开独立sources.md原文。结构字段可折叠无损保留，正文不堆源码和裸编号。

## 6. 运行、覆盖和失败

现有过程专用入口、PROCESS_CATALOG output kind、材料/Activity/过程三个owner保持。report checkpoint为空；hasCompletedProcesses与hasCompletedReport分离。公共render仍属Step08。

Activity、候选、已审过程三个分母保持：
- Activity：PROCESS_MEMBER / SUPPORT_ONLY / STANDALONE / UNCLASSIFIED / NOT_PROCESSED_CAPACITY。
- 候选：RECONSTRUCTED / SPLIT / SUPPORT_ONLY / INSUFFICIENT_MATERIAL / NOT_PROCESSED_CAPACITY。
- 已审过程：PUBLISHED / MERGED_INTO / REJECTED。

PROCESS_MEMBER必须有实际候选关系，不能默默降级；所有分母均闭合才coverage CLOSED。全仓merge以完整DRAFT处置作为Activity分母基线：REVIEW长数组仅有重复/遗漏ID时，恢复DRAFT非成员处置并按最终候选重算成员；完整唯一REVIEW清单中的孤立PROCESS_MEMBER仍fatal。既有未分类、材料不足、容量未处理、上游遗漏导致semantic PARTIAL；过程内诚实UNRESOLVED不单独导致fatal。

坏响应、非法引用/owner、缺字段/处置或Provider失败仍fatal，停止新派发，保留合法完成job，不安装正式产物。不自动重试、不重扫、不换模型。新v2输入/Prompt/结果版本改变后不能复用旧Step07 job冒充修正成果；326条上游Activity原样复用。

## 7. 测试、真实验收和实施纪律

Luna/xhigh建立直接RED：多用法不去重、原规则保留、完整REVIEW、来源范围、字段保存重开、合并不丢条件、链接可移植、零上游调用。Terra/xhigh只实现明确合同；设计裁决Sol/ultra或Astra/ultra。

自动测试证明结构，不证明中文业务正确。真实语义验收应先小范围检查目录自己发现用法，再看具体生命周期与不同分支，最后扩大。不得只手工提供正确候选或统计多Stage数量。

[完整例子](../examples/semantic-framework-walkthrough.md)用当前采购、销售片段演示允许/拒绝、关联字段和待确认部分。领域词只作输入/验收资料，不进入通用Prompt或Java规则。本次只做设计，不执行真实模型或修改代码。
