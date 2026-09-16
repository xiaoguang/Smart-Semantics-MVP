# 仓库业务过程发现与知识发布

> 固定key为repository-knowledge；目录steps/07-repository-knowledge/。两个接口由[模块总览](../modules/business-process-discovery/README.md)拥有；跨候选阅读及DRAFT前封包已实现。本次目标按[系统认识与三阶段成稿](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)同步，尚未生产接线；当前状态见[实施清单](../supplements/cross-object-process-reconstruction/implementation-status.md)，历史验证见[交付记录](../supplements/cross-object-process-reconstruction/delivery.md)。

## 1. 目标和当前状态

本步已有目录、完整Activity、过程两轮、归并和五文件v2发布，不再是新建工作。14候选/46过程是历史v1；本轮复用的后续目录为24候选、138个不同Activity成员，全部326条已审Activity保留。旧340 singleton更早且已退出生产。

历史结果存在技术模板和业务联系不足；最新采购研究正文的可读性已获认可，但WRITE仍引入已知状态/金额错误。本次把系统认识、问题选材及DRAFT→WRITE→最终RULE_REVIEW接入现有流程，只做三个样本的最小验收，之后讨论全仓。

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
| M2 RepositoryBusinessCataloger | 复用旧/首次目录；同一次全局选材判断系统特征、提出可证伪假设和调查问题 | 可跨旧组候选、首批请求、增量处置 |
| M3 ProcessMaterialAssembler | 执行首批请求；一次CHECK明确保留/移出及可空补读 | DRAFT前自包含完整Activity/源码包 |
| M4 CandidateProcessReconstructor | 事实DRAFT→业务WRITE→对实际全文的最终RULE_REVIEW | 已审过程、拆分或具体不足处置 |
| M5 RepositoryProcessConsolidator | KEEP/MERGE_INTO/REJECT及关系；不生成新业务正文 | 无损归并的唯一catalog |
| M6 BusinessProcessPublisher | 结构/来源/覆盖校验，确定性正文和来源视图 | 五项正式输出 |

完整设计逐模块见[模块总览](../modules/business-process-discovery/README.md)，中文任务见[Prompt](../references/semantic-interpretation-prompts.md)。

## 4. 保留的合同与本次增量

- 同一候选允许同Activity的不同variant；唯一性是(ActivityId, variant)，不是ActivityId。Java不解释variant中文。
- 保留已实现stage.narrative及原条件、动作、状态变化、拒绝、结果和转移。
- 保留rule.activityUseIds（模型使用activityUseLocalIds），明确规则适用分支；它不表示证据归属。目标规则可使用实际阅读包中的来源，包括context Activity和新读同源原文，程序拒绝未知或未提供引用，不沿用旧候选范围过滤。来源存在不代表适用所有子类型，具体适用性仍由模型审阅。
- 保留coverage的ActivityDisposition原name用于可读范围表，程序投影，不增加模型判断。
- 选材可跨旧候选并读冻结文件。每候选恰一次模型阅读检查，明确首批保留集及可空补读；DRAFT与最终RULE_REVIEW见相同完整包，WRITE只读完整实际事实草稿，最终核对还须见实际DRAFT和WRITE。源码不必先绑定旧Activity，无第三次选材或最终核对后模型润色。
- 未涉及Activity承接原处置；移出全部候选须明确去向。只读过不必成为成员，沿用现有覆盖账。
- 正文按业务阶段写，不以API参数处理模板冒充流程。

CONFIRMED/INFERRED/UNRESOLVED保留。没有材料就留下具体未知；禁止以已知条件缩水、臆造岗位或强制审批填满生命周期。

## 5. 正式文件与重开

canonical module保持REPOSITORY_KNOWLEDGE/1/business-process-publisher；当前producer v3，本轮目标v4，公共五文件字段不变。只有新过程私有记录升为三阶段v3；Activity、首次目录及归并仍为原pair。详细版本由[补充合同](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护。

| 文件 | Schema |
| --- | --- |
| repository-business-process-catalog.json | repository-business-process-catalog-v2 |
| process-coverage.json | repository-business-process-coverage-v2 |
| business-processes.md | repository-business-process-markdown-v2 |
| source-refs.jsonl | repository-business-process-source-references-v1 |
| sources.md | repository-business-process-sources-markdown-v1 |

五文件合同已贯通，旧结果保留。新来源在result封闭前去重/统一编号，Publisher不回读源码。上游checkpoint不升版。正文、来源页样式继续复用，缺链接允许留空。

## 6. 运行、覆盖和失败

现有source-analysis过程入口、PROCESS_CATALOG、三个owner及已实现的--catalog-from-model-batch/--focus-question保持，不恢复RepositoryRunMain。report checkpoint为空，公共render仍属历史Step08。三例复用内部有限候选接缝，独立保存预览，不关闭326条全仓覆盖或安装正式全仓publication。

Activity、候选、已审过程三个分母保持：
- Activity：PROCESS_MEMBER / SUPPORT_ONLY / STANDALONE / UNCLASSIFIED / NOT_PROCESSED_CAPACITY。
- 候选：RECONSTRUCTED / SPLIT / SUPPORT_ONLY / INSUFFICIENT_MATERIAL / NOT_PROCESSED_CAPACITY。
- 已审过程：PUBLISHED / MERGED_INTO / REJECTED。

PROCESS_MEMBER必须有实际候选关系，不能默默降级；所有分母均闭合才coverage CLOSED。全仓merge以完整DRAFT处置作为Activity分母基线：REVIEW长数组仅有重复/遗漏ID时，恢复DRAFT非成员处置并按最终候选重算成员；完整唯一REVIEW清单中的孤立PROCESS_MEMBER仍fatal。既有未分类、材料不足、容量未处理、上游遗漏导致semantic PARTIAL；过程内诚实UNRESOLVED不单独导致fatal。

坏响应、未知/未读引用、错owner或Provider失败仍fatal，保留已完成任务；新增范围须以实际阅读包校验，不再以旧候选白名单拒绝。合法未命中/不足保留UNRESOLVED。无损合并不满足时保留原过程和原因。新输入/Prompt不能误复用旧语义job，326条Activity原样复用。

## 7. 测试、真实验收和实施纪律

直接RED覆盖最终材料保留集、三阶段顺序、WRITE完整事实输入、最终核对完整包+DRAFT+WRITE、最终正文/结构保存渲染及旧pair不误复用；沿用来源范围、多用法和零上游调用约束。生产实施只改变明确合同；设计裁决Sol/ultra或Astra/ultra。

自动测试证明结构，不证明中文业务正确。真实语义验收只检查本轮三个样本的最后正文、具体规则和未知；结束后先交付审阅，不自动扩大。历史人工/定向选材可验证成稿，但不能冒称自主选材通过。

[三例推演](../supplements/cross-object-process-reconstruction/walkthrough.md)和[验收](../supplements/cross-object-process-reconstruction/acceptance.md)拥有具体样本范围；不得把历史有提示/无提示全仓对照当成本轮授权。领域词只作样例输入，不进入通用Prompt或Java规则。此次仅文档，无代码和产品调用。
