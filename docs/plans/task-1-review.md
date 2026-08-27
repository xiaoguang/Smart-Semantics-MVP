# Task 1 最终独立复审

复审日期：2026-08-12

复审范围仅限上一轮残留：真实零售候选对象状态、候选与 Catalog 的共享投影及重叠对象一致性、收紧后的 support edge 与逐条语义合同。本次只更新审查报告，没有修改生产代码。

## Verdicts

- **Spec verdict：PASS。** 真实零售候选不再全部 `VERIFIED`；候选与冻结 Catalog 共用唯一对象证据 projector；上一轮指出的过宽 support edge 已删除，没有直接 Claim 的对象保持 `NEEDS_CONFIRMATION`。
- **Quality verdict：PASS。** 新测试走真实 R2 modeling pipeline、草稿物化与 Catalog sidecar，逐对象检查 XOR 及候选/Catalog 重叠对象的 status、evidence 和 support Claim；19 条保留 edge 有完整表驱动语义合同和 Registry 负向 topic 校验。最小定向测试 24/24 通过，TypeScript 通过。

## 上一轮残留逐项结论

### 1. 真实零售候选不再全部 VERIFIED：PASS

- `src/features/modeling-pipeline/retail-adapter.ts:33-78` 不再信任 `groupRetailDocument` 的 raw `evidence_ids`；表、字段、关系、维度、指标、层级、规则和同义词均调用 Registry projector，持久化 `evidenceIds`、`supportClaimIds` 和状态。
- 独立调用生产 `retailModelingAdapter.generate()` 得到 334 个候选对象：17 个 `VERIFIED`、317 个 `NEEDS_CONFIRMATION`，334 个全部满足 evidence/pending XOR。
- 经营组织业务称呼、库存移动事件、退货率、搜索转化事件与搜索转化率均为无证据的 `NEEDS_CONFIRMATION`，不再显示旧 fixture 的错误 GR 绑定。
- `src/features/modeling-pipeline/modeling-pipeline.test.ts:100-131` 通过真实八来源 R2 runtime 固定上述数量、XOR、七个反例对象和 BOT_FILTER 正例规则。

### 2. 候选与 Catalog 共享 projector，重叠对象一致：PASS

- 唯一投影入口为 `src/features/evidence-registry/retail-evidence-registry.ts:212-229` 的 `projectRetailObjectEvidence()`。
- 候选在 `src/features/modeling-pipeline/retail-adapter.ts:37-60` 调用该入口；冻结 sidecar 在 `src/features/collaboration/retail-evidence-fixture.ts:79-98` 调用同一入口，没有第二套本地 binding 逻辑。
- 独立只读诊断将生产候选与 R2 frozen sidecar 按 `kind + owner + code` 全量比较：候选 334 个、Catalog binding 337 个、重叠 334 个，status/evidenceRefs/supportClaimIds 的 mismatch 为 0。Catalog 额外 3 个是候选模型不包含的时间规则。
- `src/features/modeling-pipeline/modeling-pipeline.test.ts:132-157` 还验证真实 R2 候选应用草稿后的 V2 sidecar；除 V2 明确删除的一个旧同义词外，所有重叠对象三项数据完全一致。

### 3. 过宽 support edge 收紧并具有逐条语义合同：PASS

- `src/features/evidence-registry/retail-evidence-registry.ts:181-201` 只保留 19 条具有直接支持关系的 edge；每条声明 `supportAspect`、`allowedTopics` 和精确 `supportClaimIds`。
- 上轮指出的库存移动事件/字段、退款金额、退货率、搜索转化事件和搜索转化率 edge 已移除。库存快照 Claim 只验证当前库存指标、最新快照规则和快照时间规则；BOT_FILTER Claim 只验证排除规则，不再验证转化率公式。
- `src/features/evidence-registry/retail-evidence-registry.ts:355-369` 的 Registry 自检拒绝缺失支持面、空 topic 范围、缺失 Claim 或 Claim topic 越界。
- `src/features/evidence-registry/evidence-registry.test.ts:287-336` 明列全部 19 条人工可审阅合同并逐条比较，同时固定七个不得升级为 `VERIFIED` 的反例；负向损坏用例在 `src/features/evidence-registry/evidence-registry.test.ts:367-372` 验证 topic 错配会失败。
- Catalog 消费测试也在 `src/features/collaboration/catalog-browser.test.ts:38-49` 明确区分“搜索转化率公式待确认”和“BOT_FILTER 排除规则已验证”。

## Findings

### Critical

无发现。

### Important

无发现。

### Minor

无发现。

## 独立验证

顺序执行：

```text
npm run test:evidence-registry    13/13
npm run test:modeling-pipeline     4/4
npm run test:catalog-browser       7/7
npx tsc -b --pretty false          exit 0
```

附加生产适配器诊断：334 candidate objects = 17 verified + 317 pending，XOR 全部成立；与 R2 frozen Catalog 的 334 个重叠 binding 全量比较为 0 mismatch；support edge 总数为 19，上一轮七类过度验证对象全部待确认。

## 审查限制

`linguan-prototype-v2/` 在当前仓库中整体未跟踪，无法取得可靠 fixed-point diff。本结论基于当前文件、上一轮报告作为基线、真实生产调用链、独立只读诊断和本轮新鲜测试结果。
