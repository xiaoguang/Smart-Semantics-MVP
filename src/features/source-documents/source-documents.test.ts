import assert from 'node:assert/strict';
import test from 'node:test';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import {
  createMemoryContentStore,
  createSourceDocumentRuntime,
} from './runtime.ts';
import type {
  ContentAddressedStore,
  ContentReference,
  SourceDocumentBlock,
  SourceDocumentMetadataStore,
  SourceDocumentSections,
} from './types.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  raw(key: string) { return this.values.get(key) ?? null; }
}

class AtomicMetadataBackend {
  private raw: string | null = null;
  private version = 0;
  createStore(): SourceDocumentMetadataStore {
    return {
      read: async () => ({ version: `memory:${this.version}`, raw: this.raw }),
      compareAndSet: async (expectedVersion, nextRaw) => {
        if (expectedVersion !== `memory:${this.version}`) return false;
        this.raw = nextRaw;
        this.version += 1;
        return true;
      },
    };
  }
}

const sections = Object.fromEntries(standardSectionOrder.map(({ key, heading }) => [
  key,
  `${heading}：零售交易库提供的可审阅内容。`,
])) as SourceDocumentSections;

const blocks: SourceDocumentBlock[] = [{
  blockId: 'gyj-block:rule.negative_stock',
  section: 'METRIC',
  semanticKind: 'RULE',
  stableCode: 'rule.negative_stock',
  label: '负库存制度条款',
  value: { normalized: 'ALWAYS_FORBIDDEN', text: '所有租户一律禁止负库存。' },
  evidenceStatus: 'CONFLICT',
  evidenceRefs: ['demo-policy://库存管理/负库存与库存时点.md#负库存'],
  affectedObjectRefs: [{
    scope: 'RESULT', documentVersion: 'guanyijia-five-source-work-standard',
    kind: 'RULE', objectId: 'negative_stock',
  }],
}];

const blockSections = Object.fromEntries(standardSectionOrder.map(({ key, heading }) => [
  key,
  key === 'METRIC'
    ? '- 负库存制度条款：所有租户一律禁止负库存。'
    : `本来源没有可直接支持“${heading}”的事实。`,
])) as SourceDocumentSections;

const blockAssertions = [{
  assertionId: blocks[0]!.blockId,
  section: blocks[0]!.section,
  statement: '负库存制度条款：所有租户一律禁止负库存。',
  provenance: 'INFERRED' as const,
  evidenceRefs: [...blocks[0]!.evidenceRefs],
}];

test('来源文档把Markdown与结构化内容放入内容寻址存储且元数据不复制正文', async () => {
  const metadata = new MemoryStorage();
  const content = createMemoryContentStore();
  const runtime = createSourceDocumentRuntime({
    metadataStorage: metadata,
    contentStore: content,
    now: () => '2026-08-14T12:00:00.000Z',
  });

  const created = await runtime.register({
    projectId: 'group_retail_ops',
    documentCode: 'retail-mysql',
    sourceSnapshotId: 'retail_mysql-snapshot-r2',
    sourceType: 'RELATIONAL_DATABASE',
    sourceName: '零售交易库',
    sections,
    assertions: [{
      assertionId: 'mysql:sales-order-line',
      section: 'ACTIVITY',
      statement: '销售订单行是交易事件粒度。',
      provenance: 'OBSERVED',
      evidenceRefs: ['GR001'],
    }],
    validation: { errors: [], warnings: [], gaps: [] },
    actorUserId: 'user_administer',
  });

  assert.equal(created.revision, 1);
  assert.equal(created.status, 'NEEDS_REVIEW');
  assert.equal(created.markdownRef.startsWith('sha256:'), true);
  assert.equal(created.assertionsRef.startsWith('sha256:'), true);
  assert.equal(created.sectionsRef.startsWith('sha256:'), true);
  assert.equal(created.markdownSha256, created.markdownRef.slice('sha256:'.length));
  assert.equal((await runtime.readMarkdown(created.documentId)).includes('## 9. 待确认事项'), true);
  assert.deepEqual(await runtime.readSections(created.documentId), sections);
  assert.match(metadata.getItem('linguan:source-documents:v1') ?? '', /retail-mysql/);
  assert.doesNotMatch(metadata.getItem('linguan:source-documents:v1') ?? '', /销售订单行是交易事件粒度/);
  assert.equal(content.size(), 3, 'Markdown、sections和assertions应各保存一次');
});

test('register保存结构化blocks并由blocksRef校验读取，旧文档显式保持只读', async () => {
  const metadata = new MemoryStorage();
  const values = new Map<ContentReference, string>();
  let damagedRef: ContentReference | undefined;
  const contentStore: ContentAddressedStore = {
    async put(value) {
      const ref = `sha256:${(await import('../ai-modeling/sha256.ts')).sha256HexSync(value)}` as ContentReference;
      values.set(ref, value);
      return ref;
    },
    async get(ref) {
      const value = values.get(ref) ?? null;
      return ref === damagedRef && value !== null ? `${value}\n损坏` : value;
    },
  };
  const runtime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const structured = await runtime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-policy',
    sourceSnapshotId: 'policy-r1', sourceType: 'DEMO_POLICY', sourceName: '演示制度',
    sections: blockSections, assertions: blockAssertions, blocks,
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });

  assert.match(structured.blocksRef ?? '', /^sha256:[a-f0-9]{64}$/);
  assert.deepEqual(await runtime.readBlocks(structured.documentId), blocks);
  damagedRef = structured.blocksRef;
  await assert.rejects(() => runtime.readBlocks(structured.documentId), /结构化块内容校验和不一致/);
  await assert.rejects(() => runtime.markReady({
    documentId: structured.documentId, expectedRevision: structured.revision, actorUserId: 'author',
  }), /结构化块内容校验和不一致/);
  damagedRef = undefined;

  const legacy = await runtime.register({
    projectId: 'group_retail_ops', documentCode: 'legacy-document', sourceSnapshotId: 'legacy-r1',
    sourceType: 'DOCUMENT', sourceName: '旧来源文档', sections, assertions: [],
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  assert.equal(legacy.blocksRef, undefined);
  await assert.rejects(() => runtime.readBlocks(legacy.documentId), /旧版来源文档没有结构化块，只能只读/);
  await assert.rejects(() => runtime.revise({
    documentId: legacy.documentId, expectedRevision: legacy.revision,
    sections, assertions: [], blocks: [], actorUserId: 'author',
  }), /旧版来源文档没有结构化块，只能只读/);
});

test('结构化文档markReady会复算Markdown与blocks/assertions/sections的关系', async () => {
  const metadata = new MemoryStorage();
  const content = createMemoryContentStore();
  const runtime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore: content });
  const document = await runtime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-policy', sourceSnapshotId: 'policy-r1',
    sourceType: 'DEMO_POLICY', sourceName: '演示制度', sections: blockSections,
    assertions: blockAssertions, blocks, validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const unrelatedRef = await content.put('这是另一份hash合法但不属于当前投影的Markdown。');
  const raw = JSON.parse(metadata.raw('linguan:source-documents:v1')!);
  raw.documents[0].markdownRef = unrelatedRef;
  raw.documents[0].markdownSha256 = unrelatedRef.slice('sha256:'.length);
  metadata.setItem('linguan:source-documents:v1', JSON.stringify(raw));

  await assert.rejects(() => runtime.markReady({
    documentId: document.documentId, expectedRevision: document.revision, actorUserId: 'author',
  }), /Markdown与章节断言投影不一致/);
});

test('revise原子生成r2并保留r1字节，四类内容SHA和真实revision diff同步变化', async () => {
  const metadata = new MemoryStorage();
  const content = createMemoryContentStore();
  const runtime = createSourceDocumentRuntime({
    metadataStorage: metadata, contentStore: content,
    now: () => '2026-08-17T12:00:00.000Z',
  });
  const first = await runtime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-policy',
    sourceSnapshotId: 'policy-r1', sourceType: 'DEMO_POLICY', sourceName: '演示制度',
    sections: blockSections, assertions: blockAssertions, blocks,
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const firstBytes = {
    sections: await runtime.readSections(first.documentId),
    assertions: await runtime.readAssertions(first.documentId),
    blocks: await runtime.readBlocks(first.documentId),
    markdown: await runtime.readMarkdown(first.documentId),
  };
  const nextBlocks = structuredClone(blocks);
  nextBlocks[0]!.label = '负库存规则';
  nextBlocks[0]!.value = { normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。' };
  const nextSections = {
    ...blockSections,
    METRIC: '- 负库存规则：演示制度允许负库存。',
  };
  const nextAssertions = [{
    ...blockAssertions[0]!,
    statement: '负库存规则：演示制度允许负库存。',
  }];

  const second = await runtime.revise({
    documentId: first.documentId, expectedRevision: 1,
    sections: nextSections, assertions: nextAssertions, blocks: nextBlocks,
    actorUserId: 'reviewer',
  });

  assert.equal(second.revision, 2);
  assert.equal(second.derivedFromDocumentId, first.documentId);
  assert.notEqual(second.documentId, first.documentId);
  for (const ref of ['sectionsRef', 'assertionsRef', 'blocksRef', 'markdownRef'] as const) {
    assert.notEqual(second[ref], first[ref], `${ref}必须随真实内容改变`);
  }
  assert.equal((await runtime.read(first.documentId))?.status, 'SUPERSEDED');
  await assert.rejects(() => runtime.markReady({
    documentId: first.documentId, expectedRevision: first.revision, actorUserId: 'reviewer',
  }), /历史revision不能重新标记为可对齐/);
  assert.equal((await runtime.read(first.documentId))?.status, 'SUPERSEDED');
  assert.deepEqual(await runtime.readSections(first.documentId), firstBytes.sections);
  assert.deepEqual(await runtime.readAssertions(first.documentId), firstBytes.assertions);
  assert.deepEqual(await runtime.readBlocks(first.documentId), firstBytes.blocks);
  assert.equal(await runtime.readMarkdown(first.documentId), firstBytes.markdown);

  const diff = await runtime.getRevisionDiff(first.documentId, second.documentId);
  assert.equal(diff.actorUserId, 'reviewer');
  assert.deepEqual(diff.blockChanges.map((change) => change.blockId), [blocks[0]!.blockId]);
  assert.equal(diff.blockChanges[0]?.before?.label, '负库存制度条款');
  assert.equal(diff.blockChanges[0]?.after?.label, '负库存规则');
  assert.deepEqual(diff.assertionChanges.map((change) => change.assertionId), [blocks[0]!.blockId]);
  const metricDiff = diff.sectionChanges.find((change) => change.section === 'METRIC');
  assert.deepEqual(metricDiff?.lines, [
    { type: 'REMOVED', line: '- 负库存制度条款：所有租户一律禁止负库存。' },
    { type: 'ADDED', line: '- 负库存规则：演示制度允许负库存。' },
  ]);
  assert.equal(diff.markdown.lines.some((line) => line.type === 'REMOVED'
    && line.line.includes('所有租户一律禁止负库存')), true);
  assert.equal(diff.markdown.lines.some((line) => line.type === 'ADDED'
    && line.line.includes('演示制度允许负库存')), true);
});

test('结构化投影损坏、同值、stale revision和内容写入失败均不提交半状态', async () => {
  const metadata = new MemoryStorage();
  const memoryContent = createMemoryContentStore();
  let failWrites = false;
  let returnWrongRef = false;
  const contentStore: ContentAddressedStore = {
    async put(value) {
      if (failWrites) throw new Error('模拟内容写入失败');
      if (returnWrongRef) return `sha256:${'0'.repeat(64)}`;
      return memoryContent.put(value);
    },
    get: (ref) => memoryContent.get(ref),
  };
  const runtime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const first = await runtime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-policy', sourceSnapshotId: 'policy-r1',
    sourceType: 'DEMO_POLICY', sourceName: '演示制度', sections: blockSections,
    assertions: blockAssertions, blocks, validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const metadataBefore = metadata.raw('linguan:source-documents:v1');
  const sizeBefore = memoryContent.size();

  const invalidCases = [
    {
      label: 'duplicate blockId',
      blocks: [...blocks, structuredClone(blocks[0]!)], assertions: blockAssertions,
      error: /blockId不能为空或重复/,
    },
    {
      label: 'duplicate assertionId',
      blocks, assertions: [...blockAssertions, structuredClone(blockAssertions[0]!)],
      error: /assertionId不能为空或重复/,
    },
    {
      label: 'section mismatch', blocks,
      assertions: [{ ...blockAssertions[0]!, section: 'FIELD' as const }],
      error: /章节不一致/,
    },
    {
      label: 'evidence tamper', blocks,
      assertions: [{ ...blockAssertions[0]!, evidenceRefs: ['fabricated-evidence'] }],
      error: /Evidence不一致/,
    },
    {
      label: 'section content mismatch', blocks, assertions: blockAssertions,
      sections: { ...blockSections, METRIC: '任意正文，没有结构化块投影。' },
      error: /章节与结构化块投影不一致/,
    },
  ];
  for (const invalid of invalidCases) {
    await assert.rejects(() => runtime.revise({
      documentId: first.documentId, expectedRevision: first.revision,
      sections: invalid.sections ?? blockSections, assertions: invalid.assertions, blocks: invalid.blocks,
      actorUserId: 'reviewer',
    }), invalid.error, invalid.label);
    assert.equal(metadata.raw('linguan:source-documents:v1'), metadataBefore, `${invalid.label}不能提交元数据`);
    assert.equal(memoryContent.size(), sizeBefore, `${invalid.label}不能先写内容`);
  }

  await assert.rejects(() => runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: blockSections, assertions: blockAssertions, blocks, actorUserId: 'reviewer',
  }), /修改没有产生任何变化/);
  assert.equal(metadata.raw('linguan:source-documents:v1'), metadataBefore);
  assert.equal(memoryContent.size(), sizeBefore);

  const changedBlocks = structuredClone(blocks);
  changedBlocks[0]!.value = { normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。' };
  const changedSections = { ...blockSections, METRIC: '- 负库存制度条款：演示制度允许负库存。' };
  const changedAssertions = [{ ...blockAssertions[0]!, statement: '负库存制度条款：演示制度允许负库存。' }];
  failWrites = true;
  await assert.rejects(() => runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: changedSections, assertions: changedAssertions, blocks: changedBlocks, actorUserId: 'reviewer',
  }), /模拟内容写入失败/);
  assert.equal(metadata.raw('linguan:source-documents:v1'), metadataBefore);
  failWrites = false;
  returnWrongRef = true;
  await assert.rejects(() => runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: changedSections, assertions: changedAssertions, blocks: changedBlocks, actorUserId: 'reviewer',
  }), /内容存储返回的引用与内容SHA不一致/);
  assert.equal(metadata.raw('linguan:source-documents:v1'), metadataBefore);
  returnWrongRef = false;
  const second = await runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: changedSections, assertions: changedAssertions, blocks: changedBlocks, actorUserId: 'reviewer',
  });
  const metadataAfterSecond = metadata.raw('linguan:source-documents:v1');
  const sizeAfterSecond = memoryContent.size();
  await assert.rejects(() => runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: changedSections, assertions: changedAssertions, blocks: changedBlocks, actorUserId: 'reviewer',
  }), /revision已变化|只能修改当前来源文档revision/);
  assert.equal((await runtime.list('guanyijia_erp')).length, 2);
  assert.equal((await runtime.read(second.documentId))?.revision, 2);
  assert.equal(metadata.raw('linguan:source-documents:v1'), metadataAfterSecond);
  assert.equal(memoryContent.size(), sizeAfterSecond);
});

test('两个Runtime共享原子metadata store并发revise只有一个CAS成功且不产生r3', async () => {
  const backend = new AtomicMetadataBackend();
  const contentStore = createMemoryContentStore();
  const firstRuntime = createSourceDocumentRuntime({ metadataStore: backend.createStore(), contentStore });
  const secondRuntime = createSourceDocumentRuntime({ metadataStore: backend.createStore(), contentStore });
  const first = await firstRuntime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-policy', sourceSnapshotId: 'policy-r1',
    sourceType: 'DEMO_POLICY', sourceName: '演示制度', sections: blockSections,
    assertions: blockAssertions, blocks, validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const nextBlocks = structuredClone(blocks);
  nextBlocks[0]!.value = { normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。' };
  const nextSections = { ...blockSections, METRIC: '- 负库存制度条款：演示制度允许负库存。' };
  const nextAssertions = [{ ...blockAssertions[0]!, statement: '负库存制度条款：演示制度允许负库存。' }];
  const revise = (runtime: ReturnType<typeof createSourceDocumentRuntime>) => runtime.revise({
    documentId: first.documentId, expectedRevision: 1, sections: nextSections,
    assertions: nextAssertions, blocks: nextBlocks, actorUserId: 'reviewer',
  });

  const results = await Promise.allSettled([revise(firstRuntime), revise(secondRuntime)]);
  assert.equal(results.filter((result) => result.status === 'fulfilled').length, 1);
  assert.equal(results.filter((result) => result.status === 'rejected'
    && /其他窗口更新/.test(String(result.reason))).length, 1);
  const documents = await firstRuntime.list('guanyijia_erp');
  assert.deepEqual(documents.map((document) => document.revision), [1, 2]);
  assert.equal(documents.filter((document) => document.status === 'SUPERSEDED').length, 1);
});

test('标记可对齐后不可覆盖旧revision，结构化修改生成新revision', async () => {
  const runtime = createSourceDocumentRuntime({
    metadataStorage: new MemoryStorage(),
    contentStore: createMemoryContentStore(),
    now: () => '2026-08-14T12:00:00.000Z',
  });
  const first = await runtime.register({
    projectId: 'group_retail_ops', documentCode: 'retail-mysql',
    sourceSnapshotId: 'retail_mysql-snapshot-r2', sourceType: 'RELATIONAL_DATABASE', sourceName: '零售交易库',
    sections: blockSections, assertions: blockAssertions, blocks,
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const ready = await runtime.markReady({ documentId: first.documentId, expectedRevision: 1, actorUserId: 'author' });
  assert.equal(ready.status, 'READY_FOR_ALIGNMENT');

  const nextBlocks = structuredClone(blocks);
  nextBlocks[0]!.label = '负库存规则';
  const next = await runtime.revise({
    documentId: ready.documentId, expectedRevision: ready.revision,
    sections: { ...blockSections, METRIC: '- 负库存规则：所有租户一律禁止负库存。' },
    assertions: [{ ...blockAssertions[0]!, statement: '负库存规则：所有租户一律禁止负库存。' }],
    blocks: nextBlocks, actorUserId: 'author',
  });
  assert.equal(next.revision, 2);
  assert.equal(next.derivedFromDocumentId, ready.documentId);
  assert.notEqual(next.documentId, ready.documentId);
  assert.equal(next.status, 'NEEDS_REVIEW');
  assert.equal((await runtime.readSections(ready.documentId)).METRIC, blockSections.METRIC);
  assert.equal((await runtime.readSections(next.documentId)).METRIC, '- 负库存规则：所有租户一律禁止负库存。');
  assert.equal((await runtime.list('group_retail_ops')).length, 2);
});

test('连续修订即使时钟未前进也保留可区分的创建时间', async () => {
  const runtime = createSourceDocumentRuntime({
    metadataStorage: new MemoryStorage(),
    contentStore: createMemoryContentStore(),
    now: () => '2026-08-25T22:17:12.433Z',
  });
  const first = await runtime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-mysql', sourceSnapshotId: 'mysql-r1',
    sourceType: 'RELATIONAL_DATABASE', sourceName: '数据库', sections: blockSections,
    assertions: blockAssertions, blocks, validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const changedBlocks = structuredClone(blocks);
  changedBlocks[0]!.label = '负库存控制规则';
  const second = await runtime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: { ...blockSections, METRIC: '- 负库存控制规则：所有租户一律禁止负库存。' },
    assertions: [{ ...blockAssertions[0]!, statement: '负库存控制规则：所有租户一律禁止负库存。' }],
    blocks: changedBlocks, actorUserId: 'author',
  });

  assert.notEqual(second.createdAt, first.createdAt);
  assert.ok(second.createdAt > first.createdAt);
});

test('运行时重建后修订仍晚于已保存版本的创建时间', async () => {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const fixedNow = () => '2026-08-25T22:17:12.433Z';
  const firstRuntime = createSourceDocumentRuntime({ metadataStorage, contentStore, now: fixedNow });
  const first = await firstRuntime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-mysql', sourceSnapshotId: 'mysql-r1',
    sourceType: 'RELATIONAL_DATABASE', sourceName: '数据库', sections: blockSections,
    assertions: blockAssertions, blocks, validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });

  const resumedRuntime = createSourceDocumentRuntime({ metadataStorage, contentStore, now: fixedNow });
  const changedBlocks = structuredClone(blocks);
  changedBlocks[0]!.label = '负库存控制规则';
  const second = await resumedRuntime.revise({
    documentId: first.documentId, expectedRevision: first.revision,
    sections: { ...blockSections, METRIC: '- 负库存控制规则：所有租户一律禁止负库存。' },
    assertions: [{ ...blockAssertions[0]!, statement: '负库存控制规则：所有租户一律禁止负库存。' }],
    blocks: changedBlocks, actorUserId: 'author',
  });

  assert.ok(second.createdAt > first.createdAt);
});

test('存在错误或缺少章节时来源文档不能标记为可对齐', async () => {
  const runtime = createSourceDocumentRuntime({
    metadataStorage: new MemoryStorage(), contentStore: createMemoryContentStore(),
  });
  const invalid = await runtime.register({
    projectId: 'group_retail_ops', documentCode: 'invalid-source',
    sourceSnapshotId: 'invalid-snapshot', sourceType: 'DOCUMENT', sourceName: '损坏资料',
    sections, assertions: [], validation: {
      errors: [{ code: 'MISSING_LOCATOR', message: '证据缺少定位' }], warnings: [], gaps: [],
    }, actorUserId: 'author',
  });
  await assert.rejects(
    () => runtime.markReady({ documentId: invalid.documentId, expectedRevision: 1, actorUserId: 'author' }),
    /来源文档仍有校验错误/,
  );
});

test('章节审阅只返回摘要和当前章节，修改时同步断言并生成可读Before After', async () => {
  const runtime = createSourceDocumentRuntime({
    metadataStorage: new MemoryStorage(), contentStore: createMemoryContentStore(),
    now: () => '2026-08-14T12:00:00.000Z',
  });
  const first = await runtime.register({
    projectId: 'group_retail_ops', documentCode: 'retail-github',
    sourceSnapshotId: 'retail_github-snapshot-r2', sourceType: 'CODE_REPOSITORY', sourceName: '零售分析代码仓库',
    sections: blockSections,
    assertions: blockAssertions,
    blocks,
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const summary = await runtime.getReviewSummary(first.documentId);
  assert.equal(summary.sections.length, 9);
  assert.equal(summary.sections.find((item) => item.section === 'METRIC')?.assertionCount, 1);
  assert.equal(summary.sections.find((item) => item.section === 'METRIC')?.evidenceCount, 1);
  assert.equal('content' in summary.sections[0], false, '总览不能内嵌所有章节正文');

  const nextBlocks = structuredClone(blocks);
  nextBlocks[0]!.value = { normalized: 'TENANT_CONFIG_CONTROLS', text: '租户配置控制是否允许负库存。' };
  const next = await runtime.revise({
    documentId: first.documentId, expectedRevision: 1,
    sections: { ...blockSections, METRIC: '- 负库存制度条款：租户配置控制是否允许负库存。' },
    assertions: [{ ...blockAssertions[0]!, statement: '负库存制度条款：租户配置控制是否允许负库存。' }],
    blocks: nextBlocks,
    actorUserId: 'author',
  });
  assert.equal((await runtime.readAssertions(next.documentId))[0]?.statement, '负库存制度条款：租户配置控制是否允许负库存。');
  assert.equal((await runtime.readAssertions(first.documentId))[0]?.statement, '负库存制度条款：所有租户一律禁止负库存。');
  const diff = await runtime.getSectionDiff(first.documentId, next.documentId, 'METRIC');
  assert.equal(diff.changed, true);
  assert.equal(diff.before, blockSections.METRIC);
  assert.equal(diff.after, '- 负库存制度条款：租户配置控制是否允许负库存。');
  assert.equal(diff.actorUserId, 'author');
});
