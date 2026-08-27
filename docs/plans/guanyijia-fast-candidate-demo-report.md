# 管伊佳快速候选证据 Demo 报告

**状态：已完成（候选，不是正式 V2）**
**依据快照：** MySQL `20260813T032528Z-abb0502c7d79`；GitHub 冻结记录 `20260813032126Z-5821d0ece9b1`；官方资料记录 `gyjerp-official-docs-20260813T031656Z`

## 目的与边界

本报告记录快速候选证据 Demo 的实际交付：在不连接数据库、GitHub 或官网，也不读取 Tenant 153 业务行的前提下，用已保存材料展示可复核的数据库事实、跨源关系和资料缺口。

它是正式管伊佳 V1 的只读候选叠加层，不会写入正式模型、受信任发布注册表、正式 Markdown/Semantic SHA 或零售 V1/V2。它也不是私有证据库、正式 Candidate V2 发布包或正式 V2。

后续的 `guanyijia-demo-content-v1-20260821` 内容 sidecar 是独立的演示资料快照；它不改变本报告所述候选事实、关系或证据等级。

## 已准入材料

### MySQL：已观察到的零行业务逻辑证据

候选层基于固定 MySQL 快照的已保存原始字节，准入白名单仅包括 DDL（含约束、索引、视图）、程序对象和参数化 DML 摘要。快照的基础对象计数保持为：95 张表、2 个视图、35 个存储过程、0 个 Function、0 个 Trigger、1 个 Event、17 条 DML 摘要。

候选公开投影明确排除 `samples/**`、`profiles/**`、原始业务行、查询结果、绑定参数和示例值；并脱敏环境性的自增值、租户/用户/固定 ID 与 DML 执行统计。候选结论不能推断库存规模、异常率或任何业务行分布。

### GitHub：冻结结构化记录

当时已保存的是可校验的结构化归档及定位，不是完整源码树。因此 GitHub 片段一律为 `FROZEN_RECORD`：可以作为待审阅上下文，但不能称作当前源码原文，也不能与 MySQL 的 `OBSERVED` 片段合计为两份独立实证。

### 官方资料：明确缺口

状态 9 的官方正文未被保留，因此候选层将该项明确记为 `GAP`。系统不以模板文字、推测或二手说明补成官方定义。

## 候选包内容与关系

候选包包含 7 个公开证据片段、7 条标准化 Claim 与 3 条确定性关系。每个片段保留来源身份、快照身份、相对定位、摘录摘要和原始 artifact 摘要；加载时会校验其受信任完整性锚点，不一致即 fail closed。

| 议题 | 已保存依据 | 判定 | 人类应审阅的问题 |
| --- | --- | --- | --- |
| 负库存 | MySQL `jsh_system_config.minus_stock_flag` DDL：`0` 未启用、`1` 启用；GitHub 冻结记录有同名结构 | `COMPLEMENTS` | 两者都谈到同一配置；GitHub 记录只补充背景，不是第二份独立现状证据。制度性“统一禁止”不能由此自动得出。 |
| 欠款字段 | MySQL `jsh_depot_head` 已部署字段范围未见 `debt`、`last_debt`；GitHub 冻结迁移记录声明过这两个字段 | `CONFLICTS` | 当前部署与冻结迁移记录不一致；需要人工判断以部署为准、接受变更或保留缺口。 |
| 单据状态 | MySQL 当前 DDL 为 `0/1/2/3/9`；GitHub 历史迁移记录为 `0/1/2` | `TEMPORAL_DRIFT` | 两份记录的有效时点不同，不能直接当成同一时点的结构冲突；状态 9 的官方含义仍为 GAP。 |

可重新定位的 MySQL 示例：

- 负库存开关：`ddl/tables/jsh_system_config.sql:L12`
- 欠款字段边界：`ddl/tables/jsh_depot_head.sql:L24-L27`
- 单据状态：`ddl/tables/jsh_depot_head.sql:L27`

## 已实现的治理约束

- `OBSERVED` 必须对应保存的 MySQL 字节；`FROZEN_RECORD` 必须显示其冻结结构化记录属性；`GAP` 必须明确说明正文未保留。
- Claim 必须引用同一主题、同一证据等级的证据；非 GAP Claim 不能无依据。
- 比较器按对象/属性、范围、有效时间和值的顺序产出 `COMPLEMENTS`、`SCOPE_DIFFERENCE`、`TEMPORAL_DRIFT`、`CORROBORATES`、`CONFLICTS` 或 `UNSUPPORTED`。
- `FROZEN_RECORD` 不会因值相同而提升为第二份独立互证。
- 所有候选数据包及摘录摘要均受运行时校验保护；损坏时拒绝加载，不自动回源扫描或联网补全。

## 与原计划的实现对应

原计划中名为 `candidate-governance.ts` 的草案 seam，已由较完整的 [candidate-evidence.ts](../../src/features/guanyijia-evidence-factory/candidate-evidence.ts) 取代：它同时定义 Evidence Class、Fragment、Claim、关系比较器与完整性校验，而非拆出一个重复模块。

原计划的前端“Markdown-first”与来源可见性要求，后来由统一来源审阅投影、候选审阅投影和工作台实现继续演进。它们不改变本候选包的正式性边界：候选关系只供审阅，不会自动发布 V1 变更。

## 已验证命令

本报告完成时，以下直接覆盖候选证据的数据校验应通过：

```bash
node --test scripts/evidence/guanyijia-admit.test.mjs
node --experimental-strip-types --test \
  src/features/guanyijia-evidence-factory/candidate-evidence.test.ts \
  src/features/guanyijia-evidence-factory/candidate-review-projection.test.ts
```

另执行 `git diff --check` 检查已跟踪改动；本报告自身作为当前嵌套工作树中的新增文件，另以无索引 diff 的 whitespace 检查覆盖。

## 未包含的能力与下一步

- 没有私有 Codeup/LFS 原始证据仓、正式 publication receipt 或 registry pin。
- GitHub 完整源码与官方 HTML/PDF 正文没有接入本候选包；在它们按新 Manifest 准入前，相关等级不能升级为 `OBSERVED`。
- 没有业务行、数据质量分布或租户规模结论。
- 没有全对象/全来源的 Claim 覆盖，只有三个高信息密度治理议题。
- 没有多标签并发写正确性保证。

下一次升级应新增版本化 Manifest 和新候选包，保留本包、其摘要、人工决定与可复核定位；不得覆盖本候选包或自动改写正式 V1。
