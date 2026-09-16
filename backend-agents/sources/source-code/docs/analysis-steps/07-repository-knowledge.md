# 仓库业务过程发现与知识发布

> 固定key为repository-knowledge；目录steps/07-repository-knowledge/。两个接口由[模块总览](../modules/business-process-discovery/README.md)拥有；本次按[跨对象补充设计](../supplements/cross-object-process-reconstruction/README.md)实施，旧“候选内M10、DRAFT后补源码”规则已替代。新增接线及直接测试已完成，完整验收见[交付记录](../supplements/cross-object-process-reconstruction/delivery.md)。

## 1. 目标和当前状态

本步已有目录、完整Activity、过程两轮、归并和五文件v2发布，不再是新建工作。14候选/46过程是历史v1；本轮复用的后续目录为24候选、138个不同Activity成员，全部326条已审Activity保留。旧340 singleton更早且已退出生产。

但真实库存过程仍按接收、校验、主动作和返回描述，目录偏维护分类，未达到用户要的对象生命周期。**本次修正语义发现、完整业务正文与来源可读性，不重建流水线。**

## 2. 上游与接口

输入为完整ReviewedActivity checkpoint、coverage、M10、同源冻结文本引用和可选旧目录。归属校验沿用现有存储，冻结文本复用VerifiedSourceTextReader。不导航、不查JavaCodeIndex、不组BusinessMaterial、不调用ActivityExplainer。

外部仍只有：
```java
ProcessDiscoveryResult BusinessProcessDiscovery.discover(ProcessDiscoveryRequest request);
BusinessProcessPublication BusinessProcessPublisher.publish(ProcessDiscoveryResult result);
```

## 3. 六个内部模块

| 模块 | 本步做什么 | 下游得到什么 |
| --- | --- | --- |
| M1 FrozenAnalysisCorpus | 重开Activity/M10/冻结文本，按ID、文件范围和字面量取材 | 完整同源材料及真实未命中说明 |
| M2 RepositoryBusinessCataloger | 有旧目录则重开并全局增量选材；新仓库保留首次目录 | 可跨旧组候选、首批请求、增量处置 |
| M3 ProcessMaterialAssembler | 执行首批请求；每候选一次模型阅读检查后执行可空补读 | DRAFT前自包含完整Activity/源码包 |
| M4 CandidateProcessReconstructor | DRAFT及完整REVIEW解释生命周期、具体条件、规则和不确定性 | 已审过程、拆分或具体不足处置 |
| M5 RepositoryProcessConsolidator | KEEP/MERGE_INTO/REJECT及关系；不生成新业务正文 | 无损归并的唯一catalog |
| M6 BusinessProcessPublisher | 结构/来源/覆盖校验，确定性正文和来源视图 | 五项正式输出 |

完整设计逐模块见[模块总览](../modules/business-process-discovery/README.md)，中文任务见[Prompt](../references/semantic-interpretation-prompts.md)。

## 4. 保留的合同与本次增量

- 同一候选允许同Activity的不同variant；唯一性是(ActivityId, variant)，不是ActivityId。Java不解释variant中文。
- 保留已实现stage.narrative及原条件、动作、状态变化、拒绝、结果和转移。
- 保留rule.activityUseIds（模型使用activityUseLocalIds），明确规则适用分支；它不表示证据归属。目标规则可使用实际阅读包中的来源，包括context Activity和新读同源原文，程序拒绝未知或未提供引用，不沿用旧候选范围过滤。来源存在不代表适用所有子类型，具体适用性仍由模型审阅。
- 保留coverage的ActivityDisposition原name用于可读范围表，程序投影，不增加模型判断。
- 选材可跨旧候选并读冻结文件。每候选恰一次模型阅读检查，补读清单可空；DRAFT/REVIEW见相同完整包。源码不必先绑定旧Activity，无第三次选材。
- 未涉及Activity承接原处置；移出全部候选须明确去向。只读过不必成为成员，沿用现有覆盖账。
- 正文按业务阶段写，不以API参数处理模板冒充流程。

CONFIRMED/INFERRED/UNRESOLVED保留。没有材料就留下具体未知；禁止以已知条件缩水、臆造岗位或强制审批填满生命周期。

## 5. 正式文件与重开

canonical module保持REPOSITORY_KNOWLEDGE/1/business-process-publisher；当前producer v2，本轮目标v3，公共五文件字段不变。

| 文件 | Schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

五文件合同已贯通，旧结果保留。新来源在result封闭前去重/统一编号，Publisher不回读源码。上游checkpoint不升版。正文、来源页样式继续复用，缺链接允许留空。

## 6. 运行、覆盖和失败

现有source-analysis过程入口、PROCESS_CATALOG、三个owner保持；目标增加--catalog-from-model-batch和可选--focus-question，不恢复RepositoryRunMain。report checkpoint为空，公共render仍属历史Step08。

Activity、候选、已审过程三个分母保持：
- Activity：PROCESS_MEMBER / SUPPORT_ONLY / STANDALONE / UNCLASSIFIED / NOT_PROCESSED_CAPACITY。
- 候选：RECONSTRUCTED / SPLIT / SUPPORT_ONLY / INSUFFICIENT_MATERIAL / NOT_PROCESSED_CAPACITY。
- 已审过程：PUBLISHED / MERGED_INTO / REJECTED。

PROCESS_MEMBER必须有实际候选关系，不能默默降级；所有分母均闭合才coverage CLOSED。全仓merge以完整DRAFT处置作为Activity分母基线：REVIEW长数组仅有重复/遗漏ID时，恢复DRAFT非成员处置并按最终候选重算成员；完整唯一REVIEW清单中的孤立PROCESS_MEMBER仍fatal。既有未分类、材料不足、容量未处理、上游遗漏导致semantic PARTIAL；过程内诚实UNRESOLVED不单独导致fatal。

坏响应、未知/未读引用、错owner或Provider失败仍fatal，保留已完成任务；新增范围须以实际阅读包校验，不再以旧候选白名单拒绝。合法未命中/不足保留UNRESOLVED。无损合并不满足时保留原过程和原因。新输入/Prompt不能误复用旧语义job，326条Activity原样复用。

## 7. 测试、真实验收和实施纪律

Luna/xhigh建立直接RED：多用法不去重、原规则保留、完整REVIEW、来源范围、字段保存重开、合并不丢条件、链接可移植、零上游调用。Terra/xhigh只实现明确合同；设计裁决Sol/ultra或Astra/ultra。

自动测试证明结构，不证明中文业务正确。真实语义验收应先小范围检查目录自己发现用法，再看具体生命周期与不同分支，最后扩大。不得只手工提供正确候选或统计多Stage数量。

[三例推演](../supplements/cross-object-process-reconstruction/walkthrough.md)和[验收](../supplements/cross-object-process-reconstruction/acceptance.md)规定有提示/无提示各从同一原始目录独立开始，不能互相喂选材结果。领域词只作样例输入，不进入通用Prompt或Java规则。此次仅文档，无代码和产品调用。
