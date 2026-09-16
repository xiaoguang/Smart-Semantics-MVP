# 系统认识、聚焦选材与三阶段业务成稿实施计划

依据：[详细设计](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)。本计划已获用户实施授权，三个验收范围为采购、销售、调拨；它们只是关注问题，不是强制链路答案。

## 约束

- 复用原326条已审Activity、M10、冻结源码、旧目录；不运行JDT、Builder、ActivityExplainer、九章或全仓归并。
- 所有编辑在正式source-code目录；旧材料、原始记录、more-findings.md保持不变。
- 自动化使用scripted Provider；真实三例使用已有ChatGPT登录、Luna/high、并发4、无API-key回退。
- 一个候选按DRAFT→WRITE→RULE_REVIEW顺序，占同一名额/服务；其他双轮任务不变。
- 无自动重试；失败保留中间记录，停止新派发。不得静默截断材料。
- 三例交付后停止，不自动全仓。

## 实施步骤

### Task 0: 保存基线与接线约定

保存设计、核对main、建立codex分支，保护无关改动；历史renderer按producer选择，包装Schema保留根$defs；容量字节检查与实际模型上下文估算分别说明。估算0.5–1小时。

### Task 1: 系统认识与问题选材

全局选材加入冻结项目说明；同一次响应返回自由系统类型假设、可修正业务假设、依据/未知及候选investigationQuestions；不新增分类调用/行业规则。估算2–3小时。

### Task 2: 聚焦阅读与封包

读取purpose贯穿；实际首批片段R编号、搜索多个命中可分别保留；CHECK明确retainedReadingRecordIds、一次补读、最终成员/context、未知和说明。首批不重新读取；完整Activity去重但多用法保留；背景和实际选择进入DRAFT及指纹。估算3–5小时。

### Task 3: 三阶段过程生成

DRAFT收到完整阅读输入，WRITE收到实际完整DRAFT，RULE_REVIEW收到同一阅读输入及实际DRAFT/WRITE，返回processResult+corrections。最终只解析processResult，核对后零模型改写。Prompt/Schema和stage容量同步。估算4–6小时。

### Task 4: 完整保存与显式复用

仅新候选过程私有记录v3保存draft/writing/review、输入/选择/映射/修正；阅读决策v2。历史decision v1/pair v2严格读取。匹配全部三阶段Prompt/Schema/顺序/输入/身份；缺稿、旧pair、失败不可冒充完成。估算3–5小时。

### Task 5: 正文与历史读取

新producer v4无HTML折叠，主要规则可读；历史v2/v3保持原renderer逐字节验证。公共五文件版本不变。小样按实际候选清单导出最终processResult，支持多个片段，不发布伪全仓。估算3–4小时。

### Task 6: 离线贯穿与本地CI

Luna/xhigh RED→Terra/xhigh GREEN，直接回归后执行模块本地CI：`mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify`。最多两个工作Agent、一个重型构建；真实JDT/模型调用0。估算2–3小时。

### Task 7: 三例真实验收与交付

一次全局自动选材，从真实结果选采购/销售/调拨，保留原ordinal和服务绑定；每候选一次CHECK/补读及三阶段，总计正常13次调用。实际上下文计数含Prompt/input/Schema/输出余量。保存三例正文、来源、阅读包、所有输入输出回执及人工规则核对。不存在候选则如实说明，不注入答案。估算0.5–1小时核验＋实际模型时间。

## 交付

工程初估18–28个连续小时，步骤2结束据实际校准。设计先独立保存；实现经本地CI及三例核验后一个PR交付，不等待远端CI、不强推。代码通过与业务通过分别报告；不以文件齐全宣称质量通过。更多发现文档不改，运行数据/凭据/本机配置不提交。
