# Backend Source Agents

这里维护五类冻结来源到未发布九章候选的离线过程。每类来源由自己的 Source Agent 负责；它们只通过语言无关的共享合同相遇，不共享来源解析实现。目录存在、历史 V7 已获批准或已有候选，都不等于授权新的来源访问、模型调用、生成、冻结、打包或运行时接入。

## 权威阅读顺序

1. [共享规则](AGENTS.md)：所有 Source Agent 的安全、所有权和两类轮次规则。
2. [共享语言](CONTEXT.md)：`ReaderCandidateRound`、`FlowInterpretationRound` 等术语。
3. [共享合同](../shared/source-agent-contracts/README.md)：固定 `NineSectionProfile` 与最小身份链。
4. 来源 Agent 的总体设计与分析步骤设计；Source Code Analysis Agent 从 [总体设计](sources/source-code/docs/DESIGN.md) 进入，再按 [八个分析步骤索引](sources/source-code/README.md#八个分析步骤) 阅读。
5. 对应实现、测试、归档 JSON 与只用于恢复的 progress。

[管伊佳下游审阅/合并设计](../docs/design/data-standardization-review-experience.md) 负责多来源审阅、决定叠加、Selection、冻结和浏览器阅读体验，不定义单个来源 Agent 的生成架构。

## 权威边界

| 位置 | 负责 | 不负责 |
| --- | --- | --- |
| `../shared/source-agent-contracts/` | 语言无关九章、候选、验证、选择概念 | Java 类型或任一来源的 JSON Schema |
| `sources/<source>/DESIGN.md` | 该来源的稳定总体架构和跨分析步骤不变量 | 某分析步骤实现流水账 |
| `sources/source-code/docs/analysis-steps/` | Source Code Analysis Agent 各分析步骤的当前设计、实现结果、缺口与退出条件 | 跨分析步骤合同的另一份副本 |
| `sources/<source>/README.md` | 入口、能力索引和复现命令 | 架构权威 |
| `sources/<source>/progress/` | 中断恢复状态 | 设计或产品合同 |

## 目录

```text
backend-agents/
├── AGENTS.md
├── CONTEXT.md
└── sources/
    ├── mysql/
    ├── source-code/
    ├── business-docs/
    ├── erp-policy/
    └── terminology-graph/

shared/
└── source-agent-contracts/
    └── README.md
```

## 生命周期

```text
Frozen Source Input
  -> source-owned Evidence preparation
  -> Nine-Section Candidate
  -> deterministic validation + reader review
  -> explicit Selection
  -> separately authorized freeze/package/activation
```

候选最多使用两次 `ReaderCandidateRound`。Source Code Analysis Agent 内部的语义 `DRAFT/REVIEW` 是解释职责，不产生第二份产品候选，也不改变共享候选轮上限；其目标合同仍由该来源自己的设计拥有。共享合同目前只定义概念，不新增 JSON Schema。
