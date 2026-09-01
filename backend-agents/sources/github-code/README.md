# GitHub Code Agent

本 Agent 的目标是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，整理成可信、可追溯、明确写出未知项的**一份仓库级**九章 Markdown 候选。程序先盘点全入口、构建五张程序图、证明Fact，再为每个支持入口编译独立Flow/EvidenceCapsule；Stage06每Flow先用隔离`R0_REGISTRY_PROPOSAL`提出有basis的仓库特定词并由程序冻结唯一RepositoryInterpretationRegistry，再用同Flow finite-key R1/R2解释；程序把全部slice准入并合成唯一RepositoryKnowledge，再只生成一份NineSectionPlan/document.md、Trace和完整analysis run archive。

DepotHead 八文件只是贯穿讲解和局部fixture，不是产品分析范围。一个Flow成功、一个shard完成或一个局部slice可读都不能完成仓库分析；只有RepositoryCoverageLedger对完整仓库的文件、site、入口、图、Fact/atom、Outcome、Flow、解释、知识和section owner逐项闭合才可产生完整分析结果。禁止一Flow一Markdown，也禁止先渲染片段再拼接。

目标设计权威是 [docs/DESIGN.md](docs/DESIGN.md)。当前代码和测试只验证或反驳目标的一部分，不能反向降低设计。

## 五分钟阅读路线

1. 读 [总体设计 §1–2](docs/DESIGN.md#1-先说业务结果)：先理解业务/信任目标，以及真实 jshERP DepotHead status 路径。
2. 读 [八阶段主线](docs/DESIGN.md#3-八阶段纵向主线)及[仓库完成门禁](docs/DESIGN.md#37-repository-completion-gate)：确认每模块/阶段立即产生canonical JSON/JSONL，分片不降覆盖，单Flow PASS不能完成。
3. 读 [当前实现审计](docs/DESIGN.md#15-当前实现审计与目标设计分开)：记住当前 jshERP 诚实结果仍是 Gap、0 Flow、0 Capsule。
4. 需要细节时选择一份阶段文档；不要先从 Java 类名推断架构。
5. [Stage 00](docs/stages/00-mvp.md)只用于理解 POC 历史和被拒绝的 shortcut，不是当前主线。

## 八阶段路线

| 阶段 | 人类问题 | 详细设计 |
| ---: | --- | --- |
| 01 | 分析的是哪份不可变源码？ | [冻结来源](docs/stages/01-freeze-source.md) |
| 02 | 这是什么应用，入口在哪里？ | [发现应用类型和入口](docs/stages/02-discover-application-and-entries.md) |
| 03 | 结构、调用、控制、数据和证据怎样连接？ | [构建五张正式程序图](docs/stages/03-build-five-program-graphs.md) |
| 04 | 哪些代码事实逐原子可证明？ | [证明代码事实](docs/stages/04-prove-code-facts.md) |
| 05 | 每个入口的完整流程和模型阅读包是什么？ | [编译完整业务流程](docs/stages/05-compile-business-flows.md) |
| 06 | 新仓库业务词怎样由每Flow R0提出、程序冻结，再由R1/R2安全选择？ | [一次只解释一个流程](docs/stages/06-interpret-one-flow-at-a-time.md) |
| 07 | 谁决定解释能否进入仓库业务知识？ | [准入并合并业务知识](docs/stages/07-admit-and-merge-business-knowledge.md) |
| 08 | 九章、Markdown、Trace、验证和归档怎样闭合？ | [构建九章文档并归档整个运行](docs/stages/08-build-nine-section-document-and-archive.md) |

每份阶段文档都按同一顺序写：为什么存在、DepotHead 具体输入、步骤、可观察 artifacts、下游保证、成功/Gap/fatal、程序/模型角色，最后才是 records、identity、算法、预算、安全、failure codes、tests 和当前差距。

## 技术参考路线

- 已批准的实现依赖、project-local JDK 17 Toolchains、格式/质量门、direct selector前缀和代理分工：[目标实现标准与工具链实施计划](docs/plans/target-standards-and-toolchain-plan.md)。
- 跨阶段 records、canonical identity、artifact reuse、预算、安全、failure families 和测试目标：[总体设计 §12–13](docs/DESIGN.md#12-业务产物持久化与显式复用)。
- 九章名称、顺序和基数的唯一权威：[共享 NineSectionProfile](../../../shared/source-agent-contracts/README.md)。
- 所有 Source Agent 的中文共同语境：[backend-agents/CONTEXT.md](../../CONTEXT.md)。
- 事实/Proof/Gap 技术合同：[Stage 04](docs/stages/04-prove-code-facts.md#8-技术合同)。
- Flow/Capsule 技术合同：[Stage 05](docs/stages/05-compile-business-flows.md#8-技术合同)。
- 模型`R0_REGISTRY_PROPOSAL`、RepositoryInterpretationRegistry freeze、finite-key R1/R2、`E+2R` planned tasks/dispositions（R0/R1/R2 shard denominator为`E/R/R`；全调用且`R=E`时为`3E`调用）和Provider失败边界：[Stage 06](docs/stages/06-interpret-one-flow-at-a-time.md#8-技术合同)。
- run-centric `start/executeStage/inspect/artifact/render/validate/trace`、plan-only renderer、typed Trace、Candidate rounds和显式新stage执行：[Stage 08](docs/stages/08-build-nine-section-document-and-archive.md#8-技术合同)。同一run自动恢复已移入[补充TODO](docs/supplements/runtime-recovery-todo.md)，不是active v0合同。
- 模块 artifact wire schema、Luna/xhigh RED 与 Terra/xhigh GREEN brief：每份 stage 文档的 `8.0` 与 `8.0.1`。
- Sol/ultra唯一Design Authority、偏离STOP与用户升级规则：[总体设计 §13.10–13.11](docs/DESIGN.md#1310-agent-执行纪律)。

## 当前能力

以下只描述当前仓库，不代表目标已经实现：

| 能力 | 中文状态 | 诚实边界 |
| --- | --- | --- |
| 冻结输入、有限仓库理解、Fact/Proof | **已验证（有限、内存态）** | 现有 Stage01 core 有 bounded tests；成功中间态未按八阶段目标立即持久化 |
| Flow/Outcome/Capsule 编译 | **已验证（有限、内存态）** | 现有 Stage02 core 对受支持 fixture 工作；固定 jshERP 八文件是 Gap、0 Flow、0 Capsule |
| 五张 standalone 程序图 | **缺失目标产物** | 现有 model/view 只有部分结构/call/CFG；没有正式 data-flow/evidence graph 文件 |
| 逐 Flow 模型解释和程序准入 | **部分具备（scripted R1/R2）** | 尚无R0 proposal/registry freeze/`E+2R` task/disposition合同；DepotHead当前0 task，live Provider未在本次调用 |
| 九章、Trace、Candidate archive | **部分具备（final-only）** | archive-v2 有 bounded final preimage/validation；前七阶段还不是独立 production stage assets |
| run-centric Java/CLI/loopback HTTP | **部分具备（注入式 Adapter）** | 单一七方法RepositoryAnalysisAgent、显式stage执行、identity-only artifact query、三Adapter同义映射和默认composition尚未实现 |
| Stage 00 POC | **POC 历史记录** | 人工 Manifest、LockedFact、baseline 和旧 archive 不能绕过目标主线 |

真实 walkthrough 固定为：

~~~text
DepotHeadController.batchSetStatus
  -> DepotHeadService.batchSetStatus
  -> DepotHeadMapper.updateByExampleSelective
  -> DepotHeadMapper.xml#updateByExampleSelective
  -> jsh_depot_head.status
~~~

源码里能看到这条路径，不等于当前程序已经证明整条路径。缺少通用 DepotHead dataflow/Facts 是当前设计审计中的明确 Gap。

## 只读复验命令

以下命令不运行 Maven、不调用模型、不联网、不生成 Candidate，也不修改客户快照。

确认工作树和固定 jshERP identity：

~~~bash
git status --short
git -C .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 rev-parse HEAD
git -C .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 status --porcelain=v1
~~~

确认真实 DepotHead 主链：

~~~bash
rg -n "RequestMapping|batchSetStatus|setStatus|andIdIn|updateByExampleSelective" \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHead.java \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java

rg -n "Update_By_Example_Where_Clause|updateByExampleSelective|update jsh_depot_head|record.status|status =" \
  .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580/jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml
~~~

确认八阶段文件与九章标题：

~~~bash
for f in \
  docs/stages/01-freeze-source.md \
  docs/stages/02-discover-application-and-entries.md \
  docs/stages/03-build-five-program-graphs.md \
  docs/stages/04-prove-code-facts.md \
  docs/stages/05-compile-business-flows.md \
  docs/stages/06-interpret-one-flow-at-a-time.md \
  docs/stages/07-admit-and-merge-business-knowledge.md \
  docs/stages/08-build-nine-section-document-and-archive.md
do
  test -f "$f"
done

sed -n '/## NineSectionProfile/,/## 最小 Interface/p' \
  ../../../shared/source-agent-contracts/README.md
~~~

文档-only 变更不要求测试或 build。若未来实现目标 stages，只运行直接覆盖该变更的定向测试。
