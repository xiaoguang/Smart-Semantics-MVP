# 当前实现与本次待修改清单

核对日期：2026-09-16。目标以[系统认识与三阶段成稿](business-reasoning-and-writing.md)为准。表内“已有”来自实际代码或保存产物；“研究实测”不等于生产接线完成。本文件不重新估算旧计划，也不以文档完成代表功能完成。

## 1. 已有能力直接复用

代码根目录为 `src/main/java/org/sourceanalysis/app/`。

| 能力 | 真实位置 | 状态与复用方式 |
| --- | --- | --- |
| 冻结 Java/XML/Vue 目录、范围与字面量读取 | `analysis/knowledge/FrozenProcessSourceCorpus.java`；`DefaultBusinessProcessDiscovery.FrozenCorpus` | 已实现，使用保存的文本；不扫描当前 checkout |
| 完整 Activity/M10/statement 重开 | `DefaultBusinessProcessDiscovery` 的 corpus/reopen 接线 | 已实现，326条数据继续复用，框架数量不写死 |
| 旧目录重开、全局选材、每候选阅读检查 | 同类 `materialSelectionInput`、`prepareReadingPackets`、`readingCheckInput` | 已实现；当前没有业务系统类型/可修正假设的明确输出字段 |
| 完整阅读包进入两轮过程 | 同类 `packetInput`、`reconstructRaw` | 已实现 DRAFT→REVIEW；未实现写作后的最终核对 |
| 成员/context、多用法、来源映射 | 同类 `finalizeCandidate` 和相关 parser | 已实现；不能把读过的 Activity 都算步骤 |
| 完整候选并行 | `runtime/modeljob/BoundedModelJobExecutor.java` | 接收 Callable，非硬编码两次调用；直接承载三阶段候选 job |
| 私有任务保存与显式复用 | `runtime/modeljob/PrivateModelJobResultStore.java`；Discovery `saveProcessPair/reopenProcessPair/inputFingerprint` | 当前只认完整 pair，三阶段记录需要真实接线 |
| 正式入口和冻结输入绑定 | `adapter/cli/SourceAnalysisExecution.java`；`analysis/knowledge/ProcessDiscoveryRequest.java` | 已有 Activity/目录/focus/inventory 选择；无须新增公开 Agent |
| 有限候选验收接缝 | `DefaultBusinessProcessDiscovery.reconstructSelected` | 已有内部选定集合执行，保留全目录 ordinal；不是正式全仓发布入口 |
| 无损归并 | Discovery `merge/isLosslessMerge` | 已实现；保留 target stages 并合并其它记录，不应声称整个对象字节完全不变 |
| 五文件、来源页和重开 | `analysis/knowledge/CanonicalBusinessProcessPublisher.java`、`BusinessProcessMarkdownRenderer.java` 及 checkpoint reader | 已实现；最终 narrative/规则仍用现有字段，公共文件格式无需扩展 |

`ApplicationProfileDetector` 判断的是 Java/Spring/MyBatis 等技术应用特征，不能把它描述为已能判断 ERP/CRM/WMS。业务系统判断属于模型的全局认识，不修改技术 discovery。

## 2. 本次应做的最小修改

| 修改面 | 具体工作 | 不做什么 | 退出标志 |
| --- | --- | --- | --- |
| 全局选材 | 在 `materialSelectionSchema/parseMaterialSelection` 和内部结果保存 `systemAssessment`、候选 `investigationQuestions`；实际项目说明进入请求 | 不写行业枚举/词典，不额外增加系统分类调用 | 保存的选材响应能说明系统假设依据、问题及不确定性 |
| 选材收敛 | 保存实际读取purpose和包内ReadingRecord ID；搜索多片段分别编号；CHECK输出首批保留集合、一次补读和理由，选择后去重封包 | 不靠截断、固定文件上限或Java语义评分缩包 | 实际包对应具体问题，选中完整Activity与关键方法如实保留 |
| 模型上下文 | 原关注问题、模型细化问题和类型判断作为调查背景传入事实任务；不是CONFIRMED事实 | 不假设现有 `packetInput` 已经携带 focusQuestion | 保存请求能查到本次问题，写作不把类型假设当事实 |
| 三阶段过程 | `reconstructRaw/RawCandidateResult/finalizeCandidate` 接 DRAFT→WRITE→RULE_REVIEW，最终 parser 使用最后结果 | 不替换前六步，不另造业务运行框架 | 最终核对真实收到原文、实际事实草稿和实际写作 |
| Prompt 与 Schema | `BusinessProcessPromptCatalog` 增 WRITE/RULE_REVIEW 任务；DRAFT转事实职责；最后输出完整现有过程结构和私有纠正说明 | 不在Prompt注入采购链或验收正确答案 | 业务步骤与结构字段一致，关键规则修正在最终产物里 |
| 私有保存复用 | 新过程三阶段v3记录保存 draft/writing/review；历史v2 pair读取保留，三阶段指纹包含完整任务序列 | 不改Activity/目录/归并pair，不把半轮当可复用已审结果 | 重开能确认三阶段完整，旧pair不命中新协议 |
| 结果归并与发布 | 最后已审过程进入已有归并/renderer；producer区分新语义来源；调整renderer的`appendStageDetails`，不用HTML折叠承载主要条件 | 不加独立未审散文产物，不拼接不同阶段造顺序 | 五文件中文字来自最后review，之后零语义改写 |
| 小样执行 | 复用内部有限候选接缝或明确标为研究的薄驱动，3例独立保存预览与记录 | 不新增公共POC入口，不安装全仓coverage，不续跑旧全仓批次 | 三例真实原文及错误/未知清单可直接审阅 |

具体版本见[详细合同§7](business-reasoning-and-writing.md#7-保存并行与版本影响限制在过程任务)。如果实施中发现公共结构确实容不下文字，先指出具体丢失字段，再讨论协议；不能让新正文只留私有文件却宣称正式产物已具备。

## 3. 本轮有意不动的部分

JDT/Core、JavaParser算法、五图和Fact/Proof、Step05/Capsule、M10、Activity、源码快照、公共Agent方法、已有CLI名称、Provider认证、两层并发、材料state、run-output、五文件类型及来源五字段均直接复用。无需新增证据链、SQL/Vue解析器、行业规则引擎、向量库、恢复系统或第四覆盖账。

## 4. 小实验与生产状态分别记

| 事实 | 当前结论 |
| --- | --- |
| 旧跨候选阅读生产接线 | 已实现并有本地验证；历史交付记录保留 |
| 人工采购材料上的四请求研究 | 形成用户接受的可读正文；不能说零事实错误 |
| 一次自主系统认识/选材研究 | 自主提出业务方向并召回相关材料；包偏宽；没有用自动包完成后续成稿 |
| 独立三请求 DRAFT→规则核对→WRITE | 生成用户接受可读性的正文；WRITE引入两处已知错误 |
| 本设计 DRAFT→WRITE→最终RULE_REVIEW | 详细设计，尚未生产实现/实测；用户接受方向，不再要求额外可行性试验 |
| 三例最小验收 | 具体输入见[验收](acceptance.md)；未取得新三阶段结果前不得写“已通过” |
| 全仓 | 本轮不执行；三例审阅后再讨论 |

## 5. 后续实施顺序

先接全局认识与问题选材，再接三阶段和私有保存，最后贯通原Publisher及样本预览。每项用直接行为测试检查数据不丢、没有隐式上游调用；真实业务质量以三例最后正文为准。实施估算应在计划时基于这些明确差异给出，不再次把JDT或326Activity算作待开发。

设计记录、旧实测记录和原始模型结果都保留；当前交接只新增目标/差异，不改写旧实验的历史结论。用户对可读性的接受是新的验收意见，与当时记录的准确性问题并列保存。
