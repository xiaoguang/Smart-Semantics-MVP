# 管伊佳真实数据库审阅文档实施计划

**目标：** 用已冻结的本地零行业务证据替换数据库来源页面的可见模板 Markdown，使其成为可定位、可审阅的真实数据库节选，同时保持正式 V1、旧来源编译物和交付链不变。

## 固定约束

- 只读取 `modeling-evidence/guanyijia` 的已冻结 MySQL 和 GitHub 归档；不连接外部系统，不读取 samples、profiles 或业务行。
- 覆盖 30/95 张核心表、6 个当前部署过程、索引／外键、DML 摘要和三条跨源治理关系。
- 新文档是 read-only review projection；`SourceDocumentCompilation` 继续承担 revision、冲突、交付和 V1 golden chain。
- 默认正文不能显示模板化伪摘录；损坏的 curated bundle 必须 fail closed，不能回退旧模板 Markdown。

## 只读审阅投影契约

- 运行时只通过 `readCuratedSourceReview` 读取已提交的 MySQL 审阅对象；它逐次校验摘要、来源类别与定位、Markdown trace 覆盖和生成清单，然后返回深拷贝。它不读取归档目录、网络、浏览器存储、扫描器或旧编译器。
- 只有 `guanyijia_mysql` 与快照 `20260813T032528Z-abb0502c7d79` 使用该投影。该路径的可见正文固定为“数据库建模审阅（真实证据节选）”和 `30 / 95 张表`，不会读取或显示旧 `documentMarkdown`。
- 每个可定位 trace 在安全渲染的 Markdown 中提供“查看依据”操作；SQL 围栏仅以文本 `<pre><code>` 呈现。选择依据会打开当前资料区并在来源对照中显示精确摘录、位置、支持结论和影响对象；SHA、引用与内部枚举只在技术详情中显示。
- MySQL 的“已识别对象”只投影 curated evidence 与其 trace，不显示或编辑旧结构化块。当前 curated `relatedBlockIds` 没有与既有 formal block ID 的已验证映射，因此只读卡不提供虚构的结构化修改入口；三条治理关系继续在现有来源差异的保留、合并或暂缓决定流程中处理。
- 审阅对象校验失败时只显示“真实审阅文档校验失败”，隐藏旧模板，并禁止该文档的完成审阅与修改操作。正式 V1、冲突命令、编译物、交付链和其余来源不受影响。

## 交付顺序

1. Luna xhigh：先写 curated bundle 和 UI 行为的 RED 测试，并由冻结资料生成公开、脱敏、逐条引用的候选九段 Markdown 资产。
2. Terra xhigh：实现 `CuratedSourceReview` 验证与 clone read seam，接入 MySQL 的“审阅文档”页签、证据选择、右侧资料区和 revision summary。
3. Sol xhigh：对整合结果做只读 Spec／Quality 复审；任何发现均以直接测试修复。
4. 串行运行直接测试、tsc/build、document re-entry 和五源 story E2E；不部署。

## 已锁定内容

数据库主体使用主数据、库存、单据、财务、租户权限、配置与分析的 30 张表；当前部署过程只称为“当前部署扩展过程”。GitHub材料在没有完整原文时显示为“冻结结构化记录”。三条治理议题固定为：负库存配置字段与“支持负库存”词汇的互补资料＋目标制度、欠款字段结构差异、单据状态时间漂移风险＋官方 GAP；其中 GitHub 词汇不单独构成对 MySQL 租户配置的互证。
