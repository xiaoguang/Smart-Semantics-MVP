# Step05 阅读材料接入 Activity、阶段重试与全量生成实施计划

本文件记录 2026-09-22 已批准的实施范围；详细行为以 [Activity 设计](../modules/activity-explanation/README.md)和[模型执行设计](../modules/model-job-execution.md)为准。

## 固定输入与边界

- 输入为现有 `jdt-persistence-acceptance-20260917/materials-state-v4.json`，包含 325 个 Step05 packet、326 条入口处置。一个入口没有 packet，保留导航缺口。
- 新 Activity 使用 `gpt-5.6-terra/xhigh`、已登录的 ChatGPT Codex、并发 4、单次请求超时 3600 秒；不回退 API key。
- 不重跑 JDT、Step01–05、Builder；不覆盖旧 326 条已审 Activity；不重新生成过程或九章。Step07 仅做新旧来源适配和离线验证。
- 所有源码修改只在正式 `source-code` 目录。旧运行材料、模型记录和 `more-findings.md` 保持不变。

## 实施任务和退出条件

| 序号 | 任务 | 退出条件 | 估算工程时间 |
| --- | --- | --- | ---: |
| 0 | 保护输入、提交设计基线、建立实现分支 | 旧产物指纹记录；独立设计提交 | 0.5–1 h |
| 1 | 直接投影 Step05 packet，建立完整小包阅读输入和容量预检 | 方法、调用、条件、返回、XML/SQL 与依赖进入真实请求；来源包内隔离 | 6–9 h |
| 2 | 每阶段及每次尝试的持久化，Provider 错误分类 | 成功 DRAFT 立即保存；失败、超时和不确定状态可读取 | 6–9 h |
| 3 | 小包 DRAFT→完整 REVIEW，按阶段安全重试 | REVIEW 失败只重试 REVIEW，实际草稿和材料不变 | 5–8 h |
| 4 | 大包导航、补读、语义切片 | 每片都有完整源码和独立两轮；未读范围显式保留 | 8–12 h |
| 5 | 包级隔离与两层并发 | 单包失败继续其它包；共享故障停止影响范围；名额不越限 | 4–6 h |
| 6 | Activity v2、coverage v3、M11 v3、运行输出 v6、CLI | 部分结果可读但不放行 Step07；显式新批次重试和复用 | 8–13 h |
| 7 | Step07 新 Step05 与旧 M10 来源读取 | 两种 Activity 的离线过程接力，零 JDT/Builder/真实过程模型 | 3–5 h |
| 8 | 正式 CLI 与本模块本地 CI | 真实 Agent、存储、历史读取、质量检查通过 | 3–5 h |
| 9 | 真实 logout 与 DepotHead 两样本；通过后全 325 包 | 保存新全量 Activity、覆盖和失败/耗时报告 | 1–2 h 核验＋模型运行 |

工程估算 45–70 个连续小时，模型等待另计。第 1 步展示真实投影，第 3 步展示阶段恢复记录。第 9 步先以精确packet选择运行小包并检查质量；通过后建立包含小包和大包的合并样本批次，显式复用小包已审结果，只调用大包。两个批次均保持packet原全量序号，不重新激活已结束运行，也不重复调用小包。正式全量运行以合并样本批次为唯一显式复用来源。没有 packet 的入口不进入模型重试。处理完全部现有包仍不等于 326 个入口全部完成解释。

## 验证与交付

开发先写直接失败测试，只运行新增或直接覆盖测试。最终执行本模块一次完整本地 CI（质量宿主 JDK21+，应用 Java17，禁用真实 JDT IT）：

```bash
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify
```

交付 Activity JSONL、coverage、完整阅读包、阅读决定、逐次请求/响应/校验、两个真实样本、全量覆盖和实际模型调用/复用/等待统计；实现经本地 CI 和真实验收后通过一个 PR 合入 main，不等待远端 CI，不强推。新增失败或必需阅读未完成不能包装为成功。真实样本未通过时不扩大调用，报告问题而非宣称计划完成。
