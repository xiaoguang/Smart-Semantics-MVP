# Progress: business-first design rewrite

- Status: COMPLETE
- Agent role: 唯一设计文档作者（简化业务优先路线）
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: 仅修改 `backend-agents/sources/source-code/` 内当前设计、示例、提示词、导航与本进展文件；不修改 Java、测试、Schema、POM 或运行产物。
- Approved inputs: 用户批准的四个深职责、八步映射、短 ref 来源下限、九章与全仓覆盖约束、有限模型调用与简化持久化路线；现有 dirty worktree 中上游 1–5 技术能力和既有文档。
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- 已完整读取仓库根、`backend-agents/`、`sources/source-code/` 的 AGENTS.md。
- 已读取 `codebase-design` 技能及其 deepening 参考，采用深 Module、小 Interface、清晰 seam 的词汇与设计准则。
- 已确认 dirty worktree；后续只触碰批准的 source-code 文档，不覆盖既有实现改动。
- 已将总体设计收敛为 BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher 四个深 Module。
- 已重写 Step 06–08、中文 Prompt 与完整合成 walkthrough；已同步 Step 01–05 各现有 M 的业务路线 I/O 映射而未改稳定技术协议。
- 已按只读复核修正 inputFingerprint、SourceRef 全局唯一、材料/活动/过程检查点时机、Mapper/SQL 样例绑定、完整知识字段、业务语言、源码行为/运行事实与同文档 refs。
- 已同步 scoped `AGENTS.md`、`README.md`、两份持久化/公共合同参考的适用范围，并将旧十批实施计划明确标记为已被本设计替代、不可继续执行。
- 已将 Step 05 对旧 Step 06/07 重型职责的下游引用更新为 BusinessMaterialBuilder、ActivityExplainer 与 ProcessExplainer；稳定的 Step 01–05 技术能力和协议仍保留。
- walkthrough 的最终九章 `document.md` 在第 1 章内给 S1–S8 提供有效锚点、file、lines 与真实 snippet，不依赖未来 HTTP/前端查看器。

## Current state

- 本轮设计文档工作单元已完成。主 Agent 的最终只读检查已覆盖文档 JSON/JSONL、链接、九章、SourceRef/snippet/锚点、报告内容连续性、diff 与非文档文件哈希；全部通过。

## Changed files

- `progress/business-first-design-rewrite.md`
- `AGENTS.md`
- `README.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `docs/analysis-steps/02-application-discovery.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/examples/semantic-framework-walkthrough.md`
- `docs/references/semantic-interpretation-prompts.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `docs/references/canonical-persistence-identity-contracts.md`
- `docs/plans/semantic-framework-ten-batch-implementation-plan.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | 已确认大量既有 dirty 改动，均视为他人工作并保留 |
| `cat` applicable AGENTS/skill/template | PASS | 已读完三级规则、技能与进展模板 |
| 主 Agent 最终文档检查 | PASS | 16 份文档、43 个 JSON 围栏全部可解析；walkthrough 两个 JSONL 围栏共 11 行全部可解析；本地链接无缺失 |
| 主 Agent 来源与报告检查 | PASS | 8 个 SourceRef、8 个真实 snippet 区、8 个有效锚点闭合；完整 Markdown 恰好九章；报告 JSON 到 Markdown 正文无内容断链 |
| 主 Agent 范围与 diff 检查 | PASS | scoped `diff --check` 通过；`src/`、`.mvn/` 与 `pom.xml` 哈希和开始时一致 |

## Decisions

- 以 `BusinessMaterialBuilder`、`ActivityExplainer`、`ProcessExplainer`、`BusinessReportPublisher` 四个深 Module 为 6–8 的唯一目标路线，不建立第二套 POC/public seam。
- 1–5 只补业务作用与 I/O 投影，不删改已稳定的技术协议。
- 新 Step 06–08 只保存有意义的材料、活动、过程和报告检查点；进程内可以传 typed object，以覆盖实际输入、实际 Prompt、有效模型/输出配置和 Module 版本的简单 inputFingerprint 防止误复用。
- Java 只做 JSON、ref、coverage 与九章顺序等机械验证；业务语义真实性由整篇 Luna REVIEW 与经授权的真实小样本人工审阅负责，不建立 Java 中文蕴含证明器。

## Blockers

- 无。

## Exact next action

- 无。本进展交给主 Agent 汇总；后续实现需另行形成基于四个深 Module 的精简计划与明确授权。

## Resume checks

- 若未来重开，先确认本文件仍为 COMPLETE，且不要按已替代的十批计划恢复实施。
- 本轮未触碰 Java、测试、Schema、POM 或运行产物，也未发起产品模型、来源扫描、Maven、Git commit 或 push。
