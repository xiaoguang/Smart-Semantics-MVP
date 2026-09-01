# 同一运行自动恢复 TODO（DEFERRED）

> **状态：DEFERRED / TODO。** 本文不是 active v0 设计、实现合同、验收合同或编码依据。除非用户以后明确批准新的设计 work unit，任何实现 Agent 都不得据此新增 runtime recovery 代码、接口、schema、存储、测试或兼容层，也不得继续细化本文。

## 1. 为什么退出 active v0

本能力原本试图让一个 analysis run 在进程崩溃后，自动接回同一个 runId、队列、worker 与模型调用状态，并修补跨进程终态。它不直接增加完整仓库冻结、五图、Fact/Proof/Gap、Flow/EvidenceCapsule、逐 Flow R0/R1/R2、RepositoryKnowledge、九章、Trace 或归档的业务分析能力，却会显著扩大 runtime 状态机、持久化协议和故障矩阵。

active v0 只需要把核心八阶段做正确，并允许新执行显式消费已验证的上游 stage/module artifacts。因此，同一运行自动恢复整体延期。

## 2. 延期的能力意图

如果未来重新获批，能力意图包括：

- 进程崩溃或重启后，以同一 runId 恢复队列与 worker 所有权；
- 从最后一个安全边界继续同一 run，而不是由调用者启动新执行；
- 对 started Provider 调用区分“未开始、已开始、终态已写、终态未安装”等状态，避免不安全重放；
- 在产物已写但运行终态未写时自动完成终态修复；
- 对重复 owner、并发接管、torn write 与重启后状态折叠提供确定性裁决；
- 在 Java、CLI、loopback HTTP 中公开同一 run 的 resume 行为。

这些只是被保存的需求意图，不是 v0 承诺。

## 3. 已归档的主要机制范围

以下旧机制全部退出 active 设计，仅作为未来重新立项时的需求线索：

- durable run event journal、event hash-chain、committed head 与 event fold；
- `PAUSED_SAFE`、`BLOCKED_NEW_RUN_REQUIRED` 等同一 run 恢复状态；
- worker generation、claim/release、跨进程 owner lock、OS lock/CAS；
- `ResumeDecision`、run-resumer publication、public `resume` / HTTP resume route；
- `RoundSlotOutcome`、`OutcomeInstall`、`PendingTerminalRepair` 与 terminal-event repair；
- Provider exactly-once-like recovery、started/ambiguous call 判定、恢复时重试或切换策略；
- `RESUME_MODULE` / `RESUME` publication address；
- 重启后的队列恢复、同一 run 幂等接管、终态补写与相关 crash matrix。

这里不冻结字段、枚举、identity 公式、目录布局或算法。未来重新批准时必须基于当时的 active 业务合同重新设计，不能把旧提案直接搬回代码。

## 4. 未来可能覆盖与明确不覆盖

未来能力若重新立项，可能覆盖“同一 run 的跨进程自动续跑与终态修复”。它仍不应改变：

- 八阶段业务目标、N/E/I/R、逐 Flow R0/R1/R2 与九章结构；
- 52 个 reader-visible 正式输出；
- canonical JSON/JSONL、Evidence、Proof、Trace 与完整仓库覆盖；
- Stage/module artifacts 作为业务分析产物和上下游对话格式的地位；
- 开发 Agent 使用 Git + 独立 `progress/*.md` 的续接规则。

本文也不覆盖分布式 worker、跨机器高可用、外部消息队列、Provider 事务协议或任意 exactly-once 保证；这些若需要，必须另行立项。

## 5. 与 active v0 的边界

active v0 的规则是：

- 异步运行只有 `QUEUED | RUNNING | FINISHED | FAILED`；worker 是单进程语义；
- 进程中断时，未完成 run 失败；调用者启动新的 run 或新的 stage execution；
- 已完整安装的 stage/module JSON/JSONL/receipt 保留，可 inspect、诊断并作为新执行的显式输入；
- Stage07 可以在新执行中直接读取并验证 Stage06 publication references，不重扫源码、不重做有效 Stage01–06；
- Provider 调用一旦开始，不自动重试、不切换 Provider；当前 run 失败并保留已写诊断；
- capability manifest 只可声明 `RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`，不得暴露假 resume seam。

上述“新执行复用上游产物”不是同一 run 恢复，也不属于本文 TODO。

## 6. 粗略开发量（仅用于延期决策）

在不改变核心业务合同、已有 stage stores 可复用且由熟悉代码库的实现者执行的前提下，完整同一运行自动恢复预计 **56–96 个有效工程小时（约 7–12 人日）**。其中，模型终态自动修复、started/ambiguous 调用裁决及其故障矩阵约占 **32–56 小时（约 4–7 人日）**。

该估算不含需求重新确认、分布式化、Provider 事务支持、外部队列、兼容迁移、全仓回归等待或生产部署。它不是排期承诺，也不是开始实现的授权。

## 7. 重新启用门

只有用户未来明确批准“重新设计同一运行自动恢复”，Sol/ultra Design Authority 才能开启新的设计 work unit。届时必须先重新确认业务收益、失败语义、Provider 能力、运行环境与预算，再决定是否采用上述任一旧机制。在此之前：**保持 TODO，不设计，不编码。**
