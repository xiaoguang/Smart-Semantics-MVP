import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import {
  projectSourceReviewVisibility,
  type AdmittedSource,
} from '../guanyijia-evidence-factory/source-review-visibility.ts';
import { createMemoryContentStore } from '../source-documents/runtime.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';

const actor = { userId: 'standardization-author' };

const sourceSnapshots = {
  guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
  guanyijia_github: '20260813032126Z-5821d0ece9b1',
  guanyijia_official_docs: 'gyjerp-official-docs-20260813T031656Z',
  guanyijia_demo_policy: 'guanyijia-demo-policy-f6c6d209ffe3fd53',
  guanyijia_semantica_demo: 'guanyijia-semantica-demo-ff948845dc5bd778',
} as const;

const v6SystemConfigService = resolve(
  process.cwd(),
  '../modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826'
    + '/sources/github/source/jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java',
);

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  private readonly values = new Map<string, string>();

  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

function visibilitySources(
  states: Partial<Record<keyof typeof sourceSnapshots, AdmittedSource['status']>>,
): AdmittedSource[] {
  return Object.entries(sourceSnapshots).map(([sourceId, snapshotId]) => ({
    sourceId,
    snapshotId,
    status: states[sourceId as keyof typeof sourceSnapshots] ?? 'PENDING',
  }));
}

test('GitHub DOCUMENT_READY immediately reveals the three exact stories and negative stock uses real code', () => {
  const projection = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    currentConflictId: 'gyj-conflict-debt-schema',
    sources: visibilitySources({
      guanyijia_mysql: 'REVIEWED',
      guanyijia_github: 'DOCUMENT_READY',
    }),
  });

  assert.deepEqual(projection.comparisonFindings.map((finding) => [finding.topic, finding.relation]), [
    ['NEGATIVE_STOCK', 'COMPLEMENTS'],
    ['DEBT_FIELDS', 'CONFLICTS'],
    ['DOCUMENT_STATUS', 'TEMPORAL_DRIFT'],
  ]);
  assert.equal(projection.actionableConflict?.topic, 'DEBT_FIELDS',
    'GitHub 文档就绪后，欠款字段差异应可进入正式决定；不必等待完成整份来源审阅');

  const negativeStock = projection.comparisonFindings.find((finding) => finding.topic === 'NEGATIVE_STOCK');
  assert.ok(negativeStock);
  const githubCode = negativeStock.evidence.filter((evidence) => (
    evidence.sourceId === 'guanyijia_github'
      && /(?:DepotHead|DepotItem|SystemConfig)Service\.java:L\d+(?:-L\d+)?$/.test(evidence.locationValue)
      && /(?:getMinusStockFlag|minusStockFlag)/.test(evidence.excerpt)
  ));
  assert.ok(githubCode.length > 0,
    '负库存互补资料必须包含冻结源码中读取或使用 minus_stock_flag 的真实 Java 行，而不能只有 schema DDL');
  const frozenSource = readFileSync(v6SystemConfigService, 'utf8');
  const expectedExcerpt = frozenSource.split(/(?<=\n)/u).slice(510, 520).join('');
  assert.equal(githubCode[0]?.locationValue,
    'jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:L511-L520');
  assert.equal(githubCode[0]?.excerpt, expectedExcerpt,
    '投影的 Java 摘录必须与 V6 冻结源码的精确行段保持一致');
  assert.equal(githubCode[0]?.artifactDigest,
    `sha256:${createHash('sha256').update(frozenSource).digest('hex')}`,
    '投影必须绑定 V6 冻结源码的完整文件摘要');

  const debt = projection.comparisonFindings.find((finding) => finding.topic === 'DEBT_FIELDS');
  assert.ok(debt?.evidence.some((evidence) => (
    evidence.sourceId === 'guanyijia_github' && /debt|last_debt/.test(evidence.excerpt)
  )));
  const status = projection.comparisonFindings.find((finding) => finding.topic === 'DOCUMENT_STATUS');
  assert.ok(status?.evidence.some((evidence) => (
    evidence.sourceId === 'guanyijia_github' && /0[^\n]*1[^\n]*2/.test(evidence.excerpt)
  )));
});

test('content gaps and pending formal differences do not block later sources; differences block finalization only', async () => {
  const deliveryCapability = {};
  const runtime = createStandardizationRunRuntime({
    metadataStorage: new MemoryStorage(),
    contentStore: createMemoryContentStore(),
    deliveryCapability,
    now: () => '2026-08-27T12:00:00.000Z',
  });
  let run = await runtime.execute({
    type: 'CREATE_RUN',
    commandId: 'create-gap-admission-run',
    expectedRevision: 0,
    actor,
    projectId: 'guanyijia-erp',
    scenarioKey: 'guanyijia-five-source-v1',
    batchId: 'batch-gap-admission',
    sources: [
      { sourceId: 'guanyijia_mysql', sourceName: '数据库', snapshotId: sourceSnapshots.guanyijia_mysql },
      { sourceId: 'guanyijia_github', sourceName: 'GitHub代码仓库', snapshotId: sourceSnapshots.guanyijia_github },
      { sourceId: 'guanyijia_official_docs', sourceName: '业务说明', snapshotId: sourceSnapshots.guanyijia_official_docs },
    ],
  });
  const runId = run.runId;

  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId,
    expectedRevision: run.revision, actor,
  });
  run = await runtime.execute({
    type: 'COMPLETE_SOURCE_DOCUMENT', commandId: 'complete-mysql-with-gap', runId,
    expectedRevision: run.revision, actor,
    sourceId: 'guanyijia_mysql',
    readSummary: { summary: '数据库结构已读取，业务行未保留为资料缺口', objectCount: 30, evidenceCount: 36 },
    documentId: 'document-mysql-gap', documentRevision: 1,
    introducedConflictIds: [],
    payload: [
      '# 数据库标准化文档',
      '## 待确认事项',
      '> 资料缺口：没有保存业务行，因此不能确认数据分布。影响：不生成分布类指标。下一步：后续补充脱敏样本。',
    ].join('\n'),
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-mysql-gap', runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_mysql',
  });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-github-after-gap', runId,
    expectedRevision: run.revision, actor,
  });
  assert.equal(run.sources.find((source) => source.sourceId === 'guanyijia_github')?.status, 'READING');

  run = await runtime.execute({
    type: 'COMPLETE_SOURCE_DOCUMENT', commandId: 'complete-github-with-difference', runId,
    expectedRevision: run.revision, actor,
    sourceId: 'guanyijia_github',
    readSummary: { summary: '代码仓库已读取，并发现欠款字段结构差异', objectCount: 43, evidenceCount: 69 },
    documentId: 'document-github-difference', documentRevision: 1,
    introducedConflictIds: ['gyj-conflict-debt-schema'],
    payload: '# GitHub标准化文档\n## 待确认事项\n欠款字段在迁移记录与部署结构之间存在差异。',
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-github-with-difference', runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_github',
  });

  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-official-before-difference-decision', runId,
    expectedRevision: run.revision, actor,
  });
  assert.equal(run.sources.find((source) => source.sourceId === 'guanyijia_official_docs')?.status, 'READING');
  assert.deepEqual(
    run.sources.find((source) => source.sourceId === 'guanyijia_github')?.introducedConflictIds,
    ['gyj-conflict-debt-schema'],
    '继续读取不能把尚未决定的正式差异悄悄清除',
  );
  assert.deepEqual(
    run.sources.find((source) => source.sourceId === 'guanyijia_github')?.resolvedConflictIds,
    [],
  );

  run = await runtime.execute({
    type: 'COMPLETE_SOURCE_DOCUMENT', commandId: 'complete-official', runId,
    expectedRevision: run.revision, actor,
    sourceId: 'guanyijia_official_docs',
    readSummary: { summary: '业务说明已读取', objectCount: 9, evidenceCount: 9 },
    documentId: 'document-official', documentRevision: 1,
    introducedConflictIds: [],
    payload: '# 业务说明标准化文档',
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-official', runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_official_docs',
  });

  await assert.rejects(runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'finalize-with-unresolved-difference', runId,
    expectedRevision: run.revision, actor,
    deliverableId: 'deliverable-with-unresolved-difference', payload: '{}', deliveryCapability,
  }), (error: unknown) => (
    error instanceof Error && /(?:差异|冲突|未决定|未解决)/.test(error.message)
  ));
});
