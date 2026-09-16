# 跨对象业务过程：本次设计修改清单

> 2026-09-16 当前批准目标。**本次仅设计，尚未实施。** 本文是下一份实施计划的差异输入，不重新执行旧生命周期修正或清理计划。完整合同见[补充设计](../supplements/cross-object-process-reconstruction/README.md)。

## 1. 目标和真实起点

复用保存目录、326条已审Activity及同源冻结文本，先跨旧候选选材、补读必要原文，再重建业务过程。读者应能说清业务对象如何交接、具体条件、分支、回退和结果，不能只有接收参数/校验/执行的技术模板。

已完成基线：

- 原生命周期修正已实现多用法、stage.narrative、rule.activityUseIds、来源预览和五文件v2读写。
- 唯一source-analysis入口、历史读取/渲染、无法无损合并保留原过程等清理已完成。
- 326条Activity和M10完整保存，不重新生成。
- 本次选定的后续已审目录为24候选、138个不同成员，另外188条Activity仍可读。
- 当前DRAFT已经拿到完整Activity和少量源码预览；选中完整源码在REVIEW才加入。
- 当前corpus仍把阅读限制在候选成员的M10引用，尚未接通冻结文件定向读取。

历史v1的14候选/46过程和更早340 singleton仅为对照，不是新验收，也不是本轮起点目录。旧结果保留，不覆盖。

## 2. 本次明确替代的约束

| 原设计/现实现 | 新目标 |
| --- | --- |
| 只能读当前候选Activity允许的M10来源 | 可跨旧组读全部Activity、M10和同源冻结文本 |
| DRAFT请求源码、REVIEW才读完整片段 | 全局选材→首批读取→每候选阅读检查→可空补读→DRAFT |
| 目录作为完整已审job才能复用 | 旧目录可直接作新任务资料；精确已审任务复用仍单独校验 |
| 每种模型任务必须是DRAFT/REVIEW pair | 新增两类单次阅读decision，保存类型与内容pair分开 |
| 来源必须带旧Activity owner | 同源原文读入包即可；statement可来自context，不强迫成为成员 |

保持：两个深接口、八步key、公共Agent方法、五文件结构、ActivityUse、certainty、具体规则、两级并发、失败保存/显式复用、来源信任。没有新行业词表、SQL/Vue编译器、Proof图或恢复框架。

## 3. 模块差异和实际代码接点

| 已有位置 | 本次需要改 | 不重做 |
| --- | --- | --- |
| FrozenAnalysisCorpus / ProcessDiscoveryRequest | 增加同源verified文本依据、旧目录输入；接现有VerifiedSourceTextReader和字面量查询 | Activity/M10 reader、statement及存储 |
| RepositoryBusinessCataloger | 重开旧目录、全仓导航、一次增量选材；未触及处置继承 | 原目录规范化、新仓库首次分片/merge |
| ProcessMaterialAssembler | 首批取材、context、一次模型阅读检查决定后的补读、完整封包 | 完整Activity字段传递和正文去重 |
| CandidateProcessReconstructor / processInput | DRAFT前真实原文、同一包REVIEW、实际阅读包allowlist | 详细阶段/规则结构和pair执行 |
| Prompt catalog / schema | 新两类decision v1、过程Prompt/响应v3；删除DRAFT取材职责 | Activity Prompt、归并业务策略 |
| 私有job保存器/Provider journal | 保存单次完整decision、读取记录/packet和显式匹配 | 已审pair保存、任务池和认证 |
| Discovery result / source映射 | 新片段统一S编号，所有引用一致映射 | SourceReference结构和来源页 |
| SourceAnalysisCli / execution/config | 旧目录参数、可选问题、source basis派生、记录实际运行选择 | 唯一入口、start/execute生命周期、三owner |
| Consolidator / Publisher / reader | 消费新过程/来源及最终处置，producer更新 | 无损合并算法、五文件渲染和公开Schema |

具体内部合同见[模块设计](../supplements/cross-object-process-reconstruction/module-design.md)；逐项RED见[验收](../supplements/cross-object-process-reconstruction/acceptance.md)。不只是改提示词，也不是重建这些模块。

## 4. 明确执行序列

```text
原始旧目录 + 全部已审Activity + 冻结文本
→ 一次全局选材
→ 程序执行首批阅读
→ 每候选一次模型阅读检查（supplementaryRequests可空）
→ 程序执行一次实际补读或直接封包
→ PROCESS_DRAFT + 完整PROCESS_REVIEW
→ 既有归并
→ 五文件发布
```

Java不判断业务材料充分性。阅读检查固定一次；可选的是实际补读。仍有未知项留在包/过程，不以达到轮数推断完整。

未涉及Activity承接原处置；成员按最终候选更新。移出全部候选的旧成员必须有明确去向；只为理解而读取的context不自动改变处置。沿用原Activity/候选/过程三个分母。

## 5. 保存、版本和兼容范围

- 新私有reading-decision-v1和reading-packet-v1，单次decision与reviewed pair不可混读。
- 过程Prompt/响应目标v3，发现/发布producer目标v3。
- 当前catalog/coverage/业务Markdown v2、source JSONL/sources Markdown v1字段不变。
- 私有执行配置升model-job-execution-config-v3，保存目录/问题选择和完整verified inventory引用，保留旧v2严格读取；YAML v2、output v4、材料state v3不变，不重置JDT、M10或Activity。
- 新提示词/完整packet/source映射/问题/模型身份进入匹配；并发、日志目录、batch ID不制造语义失效。
- 已完成旧目录可作资料；旧过程不因题目相同就当新包结果复用。
- 旧来源编号可保留；新片段以包局部身份去重后统一映射，来源只读不覆盖。

## 6. 真实验收的范围

有提示和无提示分别从同一原始目录及326Activity独立启动。无提示不得继承有提示候选、阅读包或定向材料。原始旧目录中的真实领域词可以保留；通用Prompt/Java不预置销售/采购等答案。

先看少量实际包和已审正文是否讲清对象交接、条件、分支、数量口径，再扩大。销售、采购和调拨仅作为[纸面推演](../supplements/cross-object-process-reconstruction/walkthrough.md)与具名样例，不强行要求每仓都有这些业务。

真实新模型调用需要后续计划及明确运行授权。本次不运行。Activity若确实需重生成，必须先告知原因、范围和替代方案，获得新同意。不能用清理或过程修改暗中触发。

## 7. 不纳入和完成定义

不重跑JDT、Builder、Activity；不生成九章；不全仓分析Vue/SQL；不增加无限工具循环、自动重试、业务硬规则或证据闭环。受保护的[更多发现](../supplements/more-findings.md)保持原样，其未纳入事项继续留待讨论。

设计完成只表示合同、中文Prompt、输入输出、现有代码差异和三个例子已说明；不声称新路径已经实现或模型已识别完整长链。业务成功最终看读者能否复述过程及具体条件，不能以文件数量、coverage CLOSED或多阶段数量替代。
