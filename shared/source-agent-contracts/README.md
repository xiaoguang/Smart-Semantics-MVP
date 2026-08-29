# Shared Contract Seam

本目录是多个 Source Agent 共享的唯一 seam。它只定义语言无关的概念合同；来源专用的文件选择、解析、Evidence、模型任务和渲染实现留在各自 `sources/` 目录。本阶段不新增共享 JSON Schema。

## NineSectionProfile

`NineSectionProfile` 是九章名称、顺序和基数的唯一权威。每个候选必须按以下顺序恰好包含一次每个章节，不得改名、交换顺序或增加第十章：

1. 文档说明
2. 业务目标
3. 业务对象
4. 业务活动
5. 字段与维度
6. 对象关系
7. 指标口径
8. 示例问题
9. 待确认事项

Source Agent 可以拥有不同的章内知识类型和渲染策略，但不能派生另一套章节合同。

## 最小 Interface

```text
generateCandidate(FrozenSourceInput, NineSectionProfile)
  -> NineSectionCandidate

validateCandidate(candidateId)
  -> ValidationReceipt
```

共享身份链为：

```text
Frozen Source Input
  -> Evidence Pack
  -> Nine-Section Candidate
  -> Validation Receipt
  -> explicit Selection
  -> Frozen Source Document
```

`ReaderCandidateRound` 最多产生两份产品候选：Round 1 初始候选与唯一一次 Round 2 替换。来源内部同一候选的 `FlowInterpretationRound` R1/R2 不属于候选替换。候选、验证、选择与冻结保持不同身份和独立门禁。
