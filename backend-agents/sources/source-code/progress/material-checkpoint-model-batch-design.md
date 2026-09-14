# Progress: 材料检查点与模型批次最小解耦设计

- Status: COMPLETE
- Agent role: 主设计者
- Model: gpt-6-astra / ultra
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: 正式 source-code 中受影响的总体、模块、运行指南、提示词执行约束及实施文档；仅文档。
- Approved inputs: 用户接受“合理的最小改法”：保留材料，以新模型批次执行必要任务，不因模型失败重扫 JDT。
- Current branch/worktree: linguan-prototype-v2 / main / df5e8d4edc66549774c9d1817fa870c31245d801；正式 source-code 目录。开始时 git status 仅有既有 progress/jsherp-full-parallel-business-report.md 未跟踪。

## Completed

- 读取模块设计和 scoped 指导；安排独立只读代码核对。
- 确定本轮不修改生产代码、测试、Schema、运行产物，不调用 JDT 或模型。
- 代码核对：generate 从已保存 Step05 再 build 材料，不重跑 JDT；FAILED 状态和同-run 输出约束阻断新执行。当前只有 private job writer，不能宣称已有跨批 reader。
- 唯一合同落在 docs/modules/model-job-execution.md §7：完整材料引用、sourceRunId、新 start 的 runId 兼作 modelBatchId；完整已审 job 复用；失败 pair 显式新批重做；双归属读写和候选轮次不变。
- 同步总体、八步详细设计、引擎模块、公共/持久化/Prompt附录、README、运行指南、活跃计划入口及 AGENTS.md。保留历史实测/授权与旧失败运行，不重新执行它们。
- 用第一份326材料/326入口、4个STARTED无已审结果，以及第二份325材料/326入口的真实记录推演；未声称当前JDT超时已复核或模型进程根因已经确定。

## Current state

设计同步与最终核验已完成：27份已跟踪Markdown文档及两份本任务progress；产品运行保持暂停，批次接线尚未编码。既有运行progress不变。

## Changed files

- AGENTS.md、README.md、docs/DESIGN.md。
- docs/analysis-steps/ 全部八份详细设计（01–05只标明复用边界，不改算法）。
- docs/modules/model-job-execution.md 与 docs/modules/java-code-engines/ 四份模块设计。
- docs/examples/semantic-framework-walkthrough.md、java-code-engine-walkthrough.md。
- docs/references/ 的公共接口、canonical身份、发布边界、中文Prompt四份附录。
- docs/plans/ 的coherent实施衔接、jshERP运行计划、navigation优化计划入口；docs/supplements/runtime-recovery-todo.md只澄清最小接线不属于延期恢复。
- tools/repository-run/README.md；本progress。消费者Agent另维护progress/model-batch-consumer-design.md。

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | 已读取 | 保留既有运行 progress |
| 新增/修改Markdown链接及锚点检查 | PASS | 最终36处本地链接/锚点全部存在 |
| git diff --check | PASS | 无空白错误 |
| git diff --name-only -- '*.java' '*.json' '*.yaml' '*.yml' '*.xml' | PASS | 空输出；未改代码、测试、Schema或配置 |
| 最终git diff路径检查 | PASS | 27份已跟踪改动均为正式source-code内Markdown |
| 独立消费者核对 | COMPLETE | 6份消费者同步，复读主§7未发现剩余核心闭环矛盾；不代表代码验收 |

## Decisions

- 不新增恢复服务、任务队列或公开 Agent 方法；业务模块、提示词内容及并发两层限制不变。
- 目标行为与当前已实现行为分开说明；不把过去失败推断当作根因证明。
- 私有state v3保存原material profile/basis，execution config v2隔离批次，analysis-run-output-v3精确允许材料归sourceRun、业务结果归batch，不放宽成任意跨run。
- 不跨批续孤立DRAFT；sample仅完成自身范围并保存已审job，不制造整仓完成。
- 不提交或推送；此次只更新设计文档，不扩大为代码实施。

## Blockers

无。

## Exact next action

交付用户讨论；后续仅在明确要求实施时按§7.6进入Luna RED/Terra GREEN。不自动启动模型或单入口JDT复核。

## Resume checks

先查看本文件、git diff 与 code audit 结果；不启动上次暂停的产品运行。
