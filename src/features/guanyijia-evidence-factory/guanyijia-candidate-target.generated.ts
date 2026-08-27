/**
 * Frozen generated target proposal for the Guanyijia real-evidence candidate.
 *
 * This asset is intentionally public and citation-first. It contains no raw
 * rows, private paths, or source material beyond the admitted public excerpts.
 * The digest fields are valid placeholders for the final validator's
 * canonical metadata pass.
 */

export const generatedCandidateTargetProposal = {
  schemaVersion: 1,
  kind: 'GUANYIJIA_GENERATED_TARGET_CANDIDATE',
  markdown: `# 管伊佳候选目标制度与知识提案

> 本文是候选目标制度，不是当前生产事实。所有结论均待人工确认；引用只说明可复核的公开片段，不代替业务审批。

## 候选目标制度：统一禁止负库存（待人工确认）

建议将“统一禁止负库存”作为待评审的目标政策。这个建议不是当前实际规则、现行制度或已经落地的生产政策。两份公开片段只共同表明系统存在负库存配置字段：**MySQL 已保存快照**的已保存 DDL 行（\`ddl/tables/jsh_system_config.sql:L12\`）记录了该配置字段；**GitHub 冻结结构化记录（未保留完整原文）**的冻结结构化记录行（\`schema/tables/jsh_system_config.sql:L12\`）也记录了同一配置字段。它们支持“存在配置控制”这一事实，但不足以证明统一禁止的目标政策已经实施。

## 候选知识：状态 9 的含义保持未知（待人工确认）

状态 9 的业务含义在本候选中保持未知，并作为资料缺口等待人工确认；不根据现有片段补写官方解释，也不把状态 9 发布为已确认的正式语义。**MySQL 已保存快照**的已保存 DDL 行（\`ddl/tables/jsh_depot_head.sql:L27\`）包含状态 9；**GitHub 冻结结构化记录（未保留完整原文）**的冻结迁移记录行（\`migration-history/数据库更新记录:L312\`）只保留较早的状态描述；同时，官方资料的保留状态明确为**完整官方文本未保留**。因此，状态 9 必须继续标记为未知并保留资料缺口，直到有可核验的官方或经人工确认的业务说明。

## 欠款字段：结构性决策分歧

欠款（debt）不是本提案的目标结论，而是需要人工决定的结构性分歧。**MySQL 已保存快照**的已保存 DDL 行（\`ddl/tables/jsh_depot_head.sql:L24-L27\`）展示了已部署字段边界；**GitHub 冻结结构化记录（未保留完整原文）**的冻结迁移记录行（\`migration-history/数据库更新记录:L1842-L1843\`）记录了欠款字段迁移。两者应进入评审与后续核对，不能把任一片段自动提升为现行规则或目标结论。

## 审阅边界

- 负库存是候选目标政策，仍待人工确认，且不代表当前实际政策。
- 状态 9 的含义保持未知并保留资料缺口；本文不替它创造解释。
- 欠款字段只记录结构性决策分歧，不作目标结论。
- GitHub 内容是冻结的结构化记录，未保留完整原文；引用位置仅用于公开复核。
`,
  targets: [
    {
      targetId: 'candidate-target-negative-stock-policy',
      topic: 'NEGATIVE_STOCK',
      title: '候选目标制度：统一禁止负库存（待人工确认）',
      statement: '建议将统一禁止负库存作为目标政策提交人工确认；该建议不是当前实际规则、现行制度或已落地政策。现有片段仅支持存在负库存配置控制，不能据此宣称目标政策已经实施。',
      status: 'PENDING_HUMAN_CONFIRMATION',
      evidenceClass: 'GENERATED_TARGET',
      citationRefs: [
        'mysql:jsh_system_config:minus_stock_flag',
        'github:jsh_system_config:minus_stock_flag',
      ],
      affectedClaimIds: [
        'mysql-negative-stock-control',
        'github-negative-stock-control',
      ],
    },
    {
      targetId: 'candidate-target-document-status-9-gap',
      topic: 'DOCUMENT_STATUS',
      title: '候选知识：状态 9 的含义保持未知（待人工确认）',
      statement: '状态 9 的业务含义继续保持未知并记录为资料缺口；不补写官方解释，不把状态 9 发布为已确认的正式语义，等待人工确认后再决定是否形成治理知识。',
      status: 'PENDING_HUMAN_CONFIRMATION',
      evidenceClass: 'GENERATED_TARGET',
      citationRefs: [
        'mysql:jsh_depot_head:document_status',
        'github:migration:jsh_depot_head:historical_status',
        'gap:official:document_status_9',
      ],
      affectedClaimIds: [
        'mysql-document-status-current',
        'github-document-status-historical',
        'official-document-status-9-gap',
      ],
    },
  ],
  generation: {
    provider: 'CODEX_CHATGPT_SESSION',
    model: 'gpt-5.6-luna',
    reasoningEffort: 'xhigh',
    sessionReference: 'codex-task:/root/real_evidence_task2_generation',
    promptVersion: 'guanyijia-candidate-target-v1',
    inputDigest: 'sha256:4f9fbefc56c15defed611d1fc28171fe2c50ca98f6fd79be0c9d7b26e27166a3',
    outputDigest: 'sha256:7ba61b63eb6acf2fafd67dfc3e75f266f078948074fa6b25557f1510e3eda252',
  },
} as const;
