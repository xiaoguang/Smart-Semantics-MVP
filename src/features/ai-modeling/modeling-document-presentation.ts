export function presentModelingDocumentVersion(revision: number) {
  return `第 ${revision} 版`;
}

export function presentModelingDocumentSourceSummary(snapshotIds: readonly string[]) {
  return snapshotIds.length
    ? `已载入 ${snapshotIds.length} 份固定来源资料。`
    : '由人工上传并完成结构校验。';
}
