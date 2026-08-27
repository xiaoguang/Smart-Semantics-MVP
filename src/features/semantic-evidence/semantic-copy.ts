export const semanticCollectionNotice = {
  message: '示例在独立空间中运行',
  description: '结果仅用于查看资料如何整理，不会改动已保存的来源、已发布模型或个人草稿。',
} as const;

export const semanticCollectionCopy = {
  emptySteps: ['读取资料', '整理结论', '核对来源', '形成建模建议'],
  tabs: {
    evidence: '来源材料',
    claims: '审阅结论',
    findings: '来源比较',
    model: '建模建议',
    review: '待解释内容',
  },
  profile: {
    materialScope: '资料范围',
    startConcepts: '起始概念',
    mappedFields: '已确定字段对照',
    upstream: '上游资料',
    unknownField: '待补充的字段含义',
  },
} as const;
