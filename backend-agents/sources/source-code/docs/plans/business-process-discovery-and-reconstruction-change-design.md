# 系统认识与三阶段业务成稿：本次设计修改清单

> 2026-09-16 当前批准目标。**系统认识、聚焦选材与三阶段尚未生产实施。** 本文是下一份实施计划的差异输入，不重新执行已完成的读取接线、生命周期修正或清理计划。详细合同只维护在[补充设计](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)，具体代码差异见[实施状态](../supplements/cross-object-process-reconstruction/implementation-status.md)。

## 1. 目标和真实起点

复用保存目录、326条已审Activity、M10及同源冻结Java/XML/Vue，在同一次全局选材中认识系统、提出可证伪业务假设和调查问题，聚焦取材后执行事实DRAFT→业务WRITE→最终RULE_REVIEW。最新采购研究正文可读性已获认可，但写作引入的已知事实错误要求最后核对实际全文；不再重开工具选型或基础可行性试验。本轮只做三个样本，审阅后再讨论全仓。

已完成基线：

- 原生命周期修正已实现多用法、stage.narrative、rule.activityUseIds及五文件v2/v1读写。
- 唯一source-analysis入口、历史读取/渲染、无法无损合并保留原过程等清理已完成。
- 326条Activity和M10完整保存，不重新生成。
- 本次选定的后续已审目录为24候选、138个不同成员，另外188条Activity仍可读。
- 跨候选全局选材、冻结文件读取、一次阅读检查、可空补读和DRAFT前完整封包已接通；旧目录可作输入，不要求原目录Prompt与新Prompt相同。
- 当前过程仍使用同一完整包上的DRAFT/REVIEW双轮，私有结果为v2 pair；全局选材无明确systemAssessment，CHECK只追加材料，尚未实现三阶段与首批保留/移出。

历史v1的14候选/46过程和更早340 singleton仅为对照，不是新验收，也不是本轮起点目录。旧结果保留，不覆盖。

## 2. 本次明确替代的约束

| 原设计/现实现 | 新目标 |
| --- | --- |
| 全局选材只返回候选及读清单 | 同一次请求增加系统认识、可证伪假设和调查问题，不加分类调用 |
| 阅读检查只追加首批之外的材料 | 明确首批保留/移出及一次补读；程序执行选择，不做语义评分 |
| 候选DRAFT→REVIEW同时承担事实与成文 | DRAFT事实→WRITE全文→RULE_REVIEW对实际全文局部修正 |
| 完整pair是所有内容任务的复用单位 | 新候选过程必须完整三阶段；Activity/目录/归并pair保持 |
| 再按旧有提示/无提示全仓批次推进 | 三例独立预览和准确性验收后停止，扩大范围另行讨论 |

保持：两个深接口、八步key、公共Agent方法、五文件结构、ActivityUse、certainty、具体规则、两级并发、失败保存/显式复用、来源信任。没有新行业词表、SQL/Vue编译器、Proof图或恢复框架。

## 3. 模块差异和实际代码接点

| 已有位置 | 本次需要改 | 不重做 |
| --- | --- | --- |
| FrozenAnalysisCorpus / ProcessDiscoveryRequest | 复用现有冻结材料；项目说明及调查背景进入适用任务 | Activity/M10 reader、冻结文本查询、statement及存储 |
| RepositoryBusinessCataloger | 同一次选材增加systemAssessment及候选investigationQuestions | 旧目录重开、增量处置、新仓库首次分片/merge |
| ProcessMaterialAssembler | 首批读取记录身份、CHECK明确保留/移出/补读，按问题封包 | 完整Activity、冻结读取器、正文去重和来源映射 |
| CandidateProcessReconstructor | 三阶段顺序；WRITE仅完整DRAFT，RULE_REVIEW完整包+DRAFT+WRITE | 现有详细阶段/规则类型、实际来源allowlist |
| Prompt catalog / schema | 新阶段Prompt及最后processResult/corrections响应；保留完整结构化规则 | Activity、首次目录及归并Prompt |
| 私有job保存器 / fingerprint | 过程v3完整三阶段记录、精确匹配、历史pair不误复用 | 原pair、job pool、Provider认证与journal |
| Publisher / reader / renderer | 接最终review，producer区分来源；样本不输出HTML折叠，正文/规则无损 | 五文件公开Schema、来源五字段、确定性渲染 |
| 有限候选验收接缝 | 独立保存三例预览及准确性记录，不安装全仓publication | 唯一正式CLI、start/execute、三owner及无损归并 |

具体内部合同见[最新详细设计](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)；逐项RED与样本范围见[验收](../supplements/cross-object-process-reconstruction/acceptance.md)。不只是改提示词，也不重建这些模块。

## 4. 明确执行序列

```text
原始旧目录 + 全部已审Activity + 冻结文本
→ 一次系统认识、可证伪假设和问题选材
→ 程序执行首批阅读
→ 样本候选一次CHECK（明确首批保留/移出，supplementaryRequests可空）
→ 程序执行选择及一次补读后封包
→ PROCESS_DRAFT → PROCESS_WRITE → PROCESS_RULE_REVIEW
→ 三例确定性正文/来源预览与准确性记录
→ 停止，讨论全仓；不调用归并或安装全仓publication
```

Java不判断业务材料充分性。阅读检查固定一次；可选的是实际补读。仍有未知项留在包/过程，不以达到轮数推断完整。

未涉及Activity承接原处置；成员按最终候选更新。移出全部候选的旧成员必须有明确去向；只为理解而读取的context不自动改变处置。沿用原Activity/候选/过程三个分母。

## 5. 保存、版本和兼容范围

- 私有阅读decision目标v2，历史v1严格读取；reading-packet-v1保持，单次decision与内容job不可混读。
- 仅新候选过程保存model-job-reviewed-result-v3三阶段，保留完整draft/writing/review及原journal；旧过程pair不能复用成三阶段，Activity/目录/归并仍用原pair。
- DRAFT v4、WRITE v1、RULE_REVIEW v1，发现/发布producer目标v4；详细字段由补充合同维护。
- 当前catalog/coverage/业务Markdown v2、source JSONL/sources Markdown v1字段不变。
- 已有执行配置v3、历史v2严格读取、YAML v2、output v4、材料state v3均保持，不重置JDT、M10或Activity。
- 全部阶段Prompt/Schema/任务序列、完整packet/source映射、问题/模型身份进入匹配；并发、日志目录、batch ID不制造语义失效。
- 已完成旧目录可作资料；旧过程不因题目相同就当新包结果复用。
- 旧来源编号可保留；新片段以包局部身份去重后统一映射，来源只读不覆盖。

## 6. 真实验收的范围

本轮只做[三个样本](../supplements/cross-object-process-reconstruction/acceptance.md)，不重新执行旧有提示/无提示全仓方案。历史定向/人工包可用于核对成稿，必须说明选择来源，不能据此声称自主选材已通过。原目录真实领域词可保留；通用Prompt/Java不预置销售/采购等答案。

实际检查最后正文是否讲清对象交接、状态、允许/拒绝、数量/金额用途、分支及回退；具体样本输入和检查点由[三例推演](../supplements/cross-object-process-reconstruction/walkthrough.md)维护。三例结束即停止，不能以通过为由自动扩到全仓或强行要求每仓都有这些业务。

真实新模型调用需要后续计划及明确运行授权。本次不运行。Activity若确实需重生成，必须先告知原因、范围和替代方案，获得新同意。不能用清理或过程修改暗中触发。

## 7. 不纳入和完成定义

不重跑JDT、Builder、Activity；不生成九章、不执行全仓过程/归并；不全仓分析Vue/SQL；不增加无限工具循环、自动重试、业务硬规则或证据闭环。受保护的[更多发现](../supplements/more-findings.md)保持原样，其未纳入事项继续留待讨论。

设计完成只表示合同、中文Prompt、输入输出、现有代码差异和三个例子已说明；不声称新路径已经实现或模型已识别完整长链。业务成功最终看读者能否复述过程及具体条件，不能以文件数量、coverage CLOSED或多阶段数量替代。
