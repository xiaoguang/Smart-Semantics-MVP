import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';
import { semanticFixture } from './fixture.ts';
import { adaptPublishedModel } from './adapter.ts';
import { createSemanticWorkbenchRuntime } from './runtime.ts';
import { createModelCopyStore } from './model-copy-store.ts';
import { buildAnalysisTimeline } from './analysis-timeline.ts';
import { isAutoReviewEligible, projectReviewBatch } from './review-batch.ts';
import {
  modelingObjectRefKey, projectModelObjects,
} from './modeling-object-projection.ts';
import { projectStickyStatus, projectWorkflowVersions } from './workflow-projection.ts';
import { localCurrentUser } from './current-user.ts';
import {
  filterAndSortReviewItems,
  projectPendingReviewItems,
  transitionReviewInteraction,
  validateObjectionReason,
  type ReviewItemGuidance,
} from './review-interaction.ts';
import {
  interpretReviewInput, projectReviewPrompt, resolveObjectionInput,
} from './review-conversation.ts';
import { projectLinguanWorkspaceData, projectMetric2WorkspaceData } from './workspace-store-adapter.ts';
import { sha256Hex } from './sha256.ts';
import { existsSync, readFileSync } from 'node:fs';
import { projectPublicationChecks } from './publication-checks.ts';
import type { PublicationCheck } from './runtime-types.ts';
import { renderToStaticMarkup } from 'react-dom/server';
import { WorkflowStatusMark } from './workflow-status-mark.ts';
import {
  createWorkspaceRegistry, validateModelingCode,
} from './workspace-registry.ts';
import { resolveUploadRouting } from './upload-routing.ts';
import {
  projectCustomerDescription, projectEvidenceCitations,
} from './customer-object-presentation.ts';
import { projectReviewSections } from './review-sections.ts';
import { classifyClipboardAttachment, selectSingleMarkdown } from './attachment-adapter.ts';
import {
  semanticObjectVisual, semanticVisualKinds, SemanticObjectTag,
} from '../../components/semantic-object-visuals.ts';
import { projectActiveWorkspaceView, projectWorkspaceVersionOptions } from './active-workspace-view.ts';
import { projectAssistantToolMenu, selectDemoDocument } from './demo-document.ts';
import {
  omnichannelScenarioFixture, omnichannelSourceAssets, omnichannelSourcePack,
} from './omnichannel-fixture.ts';
import {
  computeSourceBundleFingerprint, validateSourceAttachments,
} from './source-bundle.ts';
import { createOmnichannelWorkbenchRuntime } from './omnichannel-runtime.ts';
import { adaptOmnichannelPublishedModel } from './omnichannel-adapter.ts';
import {
  guanyijiaCandidateModel, guanyijiaEvidenceFixture, guanyijiaEvidenceLocators,
  guanyijiaRepositoryEvidenceFixture, guanyijiaScenarioFixture, guanyijiaSourceAssets,
} from './guanyijia-fixture.ts';
import { createGuanyijiaEvidenceRuntime } from './guanyijia-runtime.ts';
import { projectDatabaseEvidenceSections } from './database-evidence.ts';
import { projectRepositoryEvidenceSections } from './repository-evidence.ts';
import { resolveModelingScenario } from './scenario-registry.ts';

class MemoryStorage {
  private values = new Map<string, string>();

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }

  removeItem(key: string) {
    this.values.delete(key);
  }
}

const expected = [
  ['v1', 'V1', 4, 2, 47, 9, 4, 6, 68],
  ['v2', 'V2', 5, 4, 78, 15, 7, 10, 112],
  ['v3', null, 6, 5, 96, 19, 9, 10, 136],
  ['v4', 'V3', 6, 5, 105, 19, 9, 14, 149],
] as const;

test('没有 Web Crypto 时仍生成标准 SHA-256', async () => {
  assert.equal(
    await sha256Hex('abc', null),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
  );
  const document = semanticFixture.documents[0];
  assert.equal(await sha256Hex(document.content, null), document.sha256);
});

test('演示资料在任意空间可从 v1 下载并在固定空间按版本推进', () => {
  assert.equal(selectDemoDocument(semanticFixture, 'default', null)?.documentVersion, 'v1');
  assert.equal(selectDemoDocument(semanticFixture, 'digital_sales_warehouse', 'v2')?.documentVersion, 'v2');
  assert.equal(selectDemoDocument(semanticFixture, 'digital_sales_warehouse', null), undefined);
});

test('普通地址提供示例下载和恢复而未知资料仅在显式开启时出现', () => {
  assert.deepEqual(projectAssistantToolMenu({
    canUpload: true, demoTools: false, nextExampleAvailable: true,
  }).map((item) => item.key), ['download', 'reset']);
  assert.deepEqual(projectAssistantToolMenu({
    canUpload: true, demoTools: true, nextExampleAvailable: true,
  }).map((item) => item.key), ['download', 'unknown', 'reset']);
  assert.deepEqual(projectAssistantToolMenu({
    canUpload: false, demoTools: true, nextExampleAvailable: true,
  }), []);
});

test('恢复当前空间会把危险确认定位到用户当前视野', () => {
  const source = readFileSync(new URL('./assistant-panel.tsx', import.meta.url), 'utf8');
  assert.match(source, /resetPromptRef/);
  assert.match(source, /scrollIntoView\(\{ block: 'nearest'/);
  assert.match(source, /querySelector<HTMLButtonElement>\('button'\)\?\.focus\(\)/);
  assert.match(source, /当前空间已恢复，可以重新上传资料。/);
});

test('层级定义使用摘要卡片而值定义使用紧凑列表', () => {
  const hierarchy = readFileSync(new URL('../../pages/modeling/HierarchyDefinitionTab.tsx', import.meta.url), 'utf8');
  const values = readFileSync(new URL('../../pages/modeling/ObjectValueTab.tsx', import.meta.url), 'utf8');
  const styles = readFileSync(new URL('../../App.css', import.meta.url), 'utf8');

  assert.match(hierarchy, /hierarchy-card-list/);
  assert.match(hierarchy, /hierarchy-card/);
  assert.match(hierarchy, /hierarchy-level-path/);
  assert.doesNotMatch(hierarchy, /<Table dataSource=\{shown\}/);

  assert.match(values, /value-definition-list/);
  assert.match(values, /value-definition-item/);
  assert.match(values, /value-definition-details/);
  assert.doesNotMatch(values, /const definitionColumns/);

  assert.match(styles, /\.definition-technical-code[^{]*\{[^}]*overflow-wrap: anywhere;[^}]*white-space: normal;/s);
  assert.doesNotMatch(styles, /\.definition-technical-code[^{]*\{[^}]*text-overflow: ellipsis;/s);
  assert.match(styles, /@media \(max-width: 720px\)[^{]*\{[\s\S]*\.value-definition-item/s);
});

test('没有 Web Crypto 时 Runtime 仍接受已知 Markdown', async () => {
  const cryptoDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: undefined });
  try {
    const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
    const initial = await runtime.read('digital_sales_warehouse');
    const document = semanticFixture.documents[0];
    const result = await runtime.execute({
      type: 'UPLOAD_DOCUMENT',
      systemCode: 'digital_sales_warehouse',
      expectedRevision: initial.lockVersion,
      fileName: document.fileName,
      content: document.content,
      size: new TextEncoder().encode(document.content).byteLength,
      sha256: document.sha256,
    });
    assert.equal(result.snapshot.uploads[0].sha256, document.sha256);
  } finally {
    if (cryptoDescriptor) Object.defineProperty(globalThis, 'crypto', cryptoDescriptor);
  }
});

test('保留四版真实资料、SHA 与业务计数', () => {
  assert.equal(semanticFixture.system.code, 'digital_sales_warehouse');
  assert.equal(semanticFixture.documents.length, 4);
  assert.equal(new Set(semanticFixture.documents.map((item) => item.sha256)).size, 4);
  semanticFixture.documents.forEach((document, index) => {
    const [documentVersion, modelVersion, entities, events, fields, relations, dimensions, metrics, reviews] = expected[index];
    assert.equal(document.documentVersion, documentVersion);
    assert.equal(document.modelVersion, modelVersion);
    assert.equal(document.artifacts.model.entity_tables.length, entities);
    assert.equal(document.artifacts.model.event_tables.length, events);
    assert.equal(document.artifacts.model.entity_tables.concat(document.artifacts.model.event_tables)
      .reduce((count, table) => count + table.fields.length, 0), fields);
    assert.equal(document.artifacts.model.relationships.length, relations);
    assert.equal(document.artifacts.enrichment.dimensions.length, dimensions);
    assert.equal(document.artifacts.model.metrics.length, metrics);
    assert.equal(document.review.items.length, reviews);
  });
  assert.equal(semanticFixture.documents[2].qualityGate.publishable, false);
  assert.equal(semanticFixture.documents[2].qualityGate.blockers.length, 4);
});

test('发布模型适配器保留字段归属、关系端点、复杂指标和规则候选', () => {
  const snapshot = adaptPublishedModel('digital_sales_warehouse', 'v4', 'V3');
  assert.equal(snapshot.tables.length, 11);
  assert.equal(snapshot.entities.length, 6);
  assert.equal(snapshot.events.length, 5);
  assert.equal(snapshot.fields.length, 105);
  assert.equal(snapshot.relations.length, 19);
  assert.equal(snapshot.dimensions.length, 9);
  assert.equal(snapshot.metrics.length, 14);
  assert.ok(snapshot.fields.every((field) => field.ownerTableCode));
  assert.ok(snapshot.relations.every((relation) => relation.from.tableCode && relation.to.tableCode));
  assert.ok(snapshot.relations.every((relation) => snapshot.tables.some((table) => table.code === relation.from.tableCode)));
  assert.ok(snapshot.metrics.some((metric) => metric.aggregation === 'weighted_avg' && metric.editability === 'READ_ONLY'));
  assert.ok(snapshot.metrics.some((metric) => metric.aggregation === 'semi_additive' && metric.formula));
  assert.equal(snapshot.metrics.find((metric) => metric.name === '维修响应时长')?.editability, 'READ_ONLY');
  assert.equal(snapshot.ruleCandidates.length, 4);
  assert.ok(snapshot.ruleCandidates.every((candidate) => candidate.sourceType === 'SEMANTIC_ENRICHMENT'));
  assert.ok(snapshot.ruleCandidates.every((candidate) => candidate.compatibility === 'NEEDS_STRUCTURE'));
  assert.equal(snapshot.hierarchies.length, 1);
  assert.deepEqual(snapshot.hierarchies[0].levels.map((level) => level.name), ['大区', '省', '市', '区县']);
  assert.equal(snapshot.hierarchies[0].members.length, 10);
  assert.equal(snapshot.aliases.length, 114);
  assert.ok(snapshot.aliases.some((alias) => alias.targetType === 'RULE'));
});

async function uploadAndGenerate(
  runtime: ReturnType<typeof createSemanticWorkbenchRuntime>,
  version: 'v1' | 'v2' | 'v3' | 'v4',
) {
  let snapshot = await runtime.read('digital_sales_warehouse');
  const document = semanticFixture.documents.find((item) => item.documentVersion === version)!;
  snapshot = (await runtime.execute({
    type: 'UPLOAD_DOCUMENT',
    systemCode: 'digital_sales_warehouse',
    expectedRevision: snapshot.lockVersion,
    fileName: document.fileName,
    content: document.content,
    size: new TextEncoder().encode(document.content).byteLength,
    sha256: document.sha256,
  })).snapshot;
  return (await runtime.execute({
    type: 'GENERATE_MODEL',
    systemCode: 'digital_sales_warehouse',
    expectedRevision: snapshot.lockVersion,
    documentVersion: version,
  })).snapshot;
}

async function approveAndPublish(
  runtime: ReturnType<typeof createSemanticWorkbenchRuntime>,
  version: 'v1' | 'v2' | 'v4',
) {
  let snapshot = await runtime.read('digital_sales_warehouse');
  const document = semanticFixture.documents.find((item) => item.documentVersion === version)!;
  for (const group of document.review.groups) {
    snapshot = (await runtime.execute({
      type: 'CONFIRM_BUSINESS_GROUP',
      systemCode: 'digital_sales_warehouse',
      expectedRevision: snapshot.lockVersion,
      documentVersion: version,
      groupId: group.id,
      reviewer: '产品审核人',
      reason: `确认${group.name}符合当前设计资料`,
    })).snapshot;
  }
  return (await runtime.execute({
    type: 'PUBLISH_MODEL',
    systemCode: 'digital_sales_warehouse',
    expectedRevision: snapshot.lockVersion,
    documentVersion: version,
    reviewer: '产品审核人',
    reason: '确认发布当前语义模型版本',
  })).snapshot;
}

test('独立 Runtime 走通 v1、v2、v3 阻断、v4，并保持乐观锁', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  let snapshot = await uploadAndGenerate(runtime, 'v1');
  await assert.rejects(() => runtime.execute({
    type: 'CONFIRM_BUSINESS_GROUP', systemCode: 'digital_sales_warehouse', expectedRevision: snapshot.lockVersion - 1,
    documentVersion: 'v1', groupId: 'shared', reviewer: '审核人', reason: '确认资料内容',
  }), /重新加载/);
  snapshot = await approveAndPublish(runtime, 'v1');
  assert.equal(snapshot.currentCatalogVersion, 'V1');
  await uploadAndGenerate(runtime, 'v2');
  snapshot = await approveAndPublish(runtime, 'v2');
  assert.equal(snapshot.currentCatalogVersion, 'V2');
  snapshot = await uploadAndGenerate(runtime, 'v3');
  assert.equal(snapshot.results.at(-1)?.status, 'NEEDS_SUPPLEMENT');
  assert.equal(snapshot.currentCatalogVersion, 'V2');
  await uploadAndGenerate(runtime, 'v4');
  snapshot = await approveAndPublish(runtime, 'v4');
  assert.equal(snapshot.currentCatalogVersion, 'V3');
  assert.deepEqual(snapshot.catalogs.map((catalog) => catalog.modelVersion), ['V1', 'V2', 'V3']);
  assert.ok(snapshot.catalogs.every((catalog) => catalog.immutable));
});

test('工作副本按系统和版本隔离，并在下一版发布后冻结旧修改', () => {
  const storage = new MemoryStorage();
  const copies = createModelCopyStore(storage);
  const v1 = adaptPublishedModel('digital_sales_warehouse', 'v1', 'V1');
  copies.publish({ ...structuredClone(v1), sourceSha256: 'legacy-source', hierarchies: [], aliases: [] });
  copies.updateMetric('digital_sales_warehouse', 'V1', v1.metrics[0].code, { name: '人工调整名称' });
  copies.addAlias('digital_sales_warehouse', 'V1', '现货量');
  copies.addManualRule('digital_sales_warehouse', 'V1', { name: '人工规则', description: '仅属于 V1 工作副本' });
  copies.publish(v1);
  assert.equal(copies.read('digital_sales_warehouse', 'V1')?.metrics[0].name, '人工调整名称');
  assert.ok(copies.read('digital_sales_warehouse', 'V1')?.aliases.some((alias) => alias.text === '现货量'));
  assert.equal(copies.read('digital_sales_warehouse', 'V1')?.executableRules[0].name, '人工规则');
  assert.equal(copies.read('digital_sales_warehouse', 'V1')?.hierarchies.length, 1);
  copies.publish(adaptPublishedModel('digital_sales_warehouse', 'v2', 'V2'));
  assert.equal(copies.read('digital_sales_warehouse', 'V1')?.readOnly, true);
  assert.throws(() => copies.updateMetric('digital_sales_warehouse', 'V1', v1.metrics[0].code, { name: '不应写入' }), /只读/);
  assert.equal(copies.read('default', 'WORKSPACE'), null);
});

test('同事五个页面在所有工作空间复用原组件', () => {
  const source = readFileSync(new URL('../../layout/AppLayout.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /DigitalOntologyPage|DigitalMetricPage|DigitalRulePage|DigitalDeferredPage/);
  assert.match(source, /current === 'ai' \? <AiWorkspaceErrorBoundary/);
  assert.match(source, /<AiModelingPage/);
  assert.match(source, /: activePage\.content/);
});

test('顶部只显示一个模型项目选择器且助手输入区不会被右栏挤出', () => {
  const layout = readFileSync(new URL('../../layout/AppLayout.tsx', import.meta.url), 'utf8');
  const styles = readFileSync(new URL('./ai-modeling.css', import.meta.url), 'utf8');
  const bodyBlock = styles.match(/\.ai-modeling-body\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';
  const assistantBlock = styles.match(/\.ai-assistant\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';
  const threadBlock = styles.match(/\.assistant-thread\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';

  assert.doesNotMatch(layout, /aria-label="协作空间"/);
  assert.equal(layout.match(/aria-label="模型项目"/g)?.length, 1);
  assert.match(bodyBlock, /grid-template-rows:\s*minmax\(0,\s*1fr\)/);
  assert.match(bodyBlock, /overflow:\s*hidden/);
  assert.doesNotMatch(assistantBlock, /height:\s*100%/);
  assert.match(assistantBlock, /min-height:\s*0/);
  assert.match(assistantBlock, /overflow:\s*hidden/);
  assert.match(threadBlock, /min-height:\s*0/);
});

test('AI 工作区使用内容区剩余高度，草稿条不会把输入框推到视口外', () => {
  const layout = readFileSync(new URL('../../layout/AppLayout.tsx', import.meta.url), 'utf8');
  const appStyles = readFileSync(new URL('../../App.css', import.meta.url), 'utf8');
  const aiStyles = readFileSync(new URL('./ai-modeling.css', import.meta.url), 'utf8');
  const aiContentBlock = appStyles.match(/\.app-content-ai\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';
  const aiPageBlock = appStyles.match(/\.app-content-ai\s*>\s*\.app-page-ai\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';
  const aiCardBlock = appStyles.match(/\.app-content-ai\s*>\s*\.app-page-ai\s*>\s*\.ant-card\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';
  const layoutBlock = aiStyles.match(/\.ai-modeling-layout\s*\{(?<body>[\s\S]*?)\}/)?.groups?.body ?? '';

  assert.match(layout, /app-content-ai/);
  assert.match(aiContentBlock, /display:\s*flex/);
  assert.match(aiContentBlock, /overflow:\s*hidden/);
  assert.match(aiPageBlock, /min-height:\s*0/);
  assert.match(aiPageBlock, /flex:\s*1/);
  assert.match(aiCardBlock, /height:\s*100%/);
  assert.match(aiCardBlock, /min-height:\s*0/);
  assert.match(layoutBlock, /height:\s*100%/);
  assert.match(layoutBlock, /min-height:\s*0/);
  assert.doesNotMatch(layoutBlock, /min-height:\s*620px/);
});

test('草稿审阅嵌入助手时间线且固定说明不会遮挡已有草稿', () => {
  const page = readFileSync(new URL('./ai-modeling-page.tsx', import.meta.url), 'utf8');
  const assistant = readFileSync(new URL('./assistant-panel.tsx', import.meta.url), 'utf8');
  const collaborationStyles = readFileSync(new URL('../collaboration/collaboration.css', import.meta.url), 'utf8');

  assert.match(page, /leadingContent=\{<>\{draftRecord\}\{documentRecord\}<\/>\}/);
  assert.match(assistant, /\{leadingContent\}/);
  assert.match(assistant, /showContextMessage\s*&&/);
  assert.doesNotMatch(collaborationStyles, /\.draft-review-record\s*\{[^}]*max-height:/s);
  assert.doesNotMatch(collaborationStyles, /\.draft-review-scroll\s*\{[^}]*overflow:\s*auto/s);
});

test('从正式 Catalog 创建个人草稿时保留不可变语义 Sidecar', () => {
  const context = readFileSync(new URL('./workspace-context.tsx', import.meta.url), 'utf8');
  assert.match(context, /collaboration\.catalogFor\(activeSystem, selectedCatalogVersion\)\?\.data\.semanticSidecar/);
  assert.match(context, /semanticSidecar: clone\(semanticSidecar\)/);
  assert.match(context, /draftRef\.current\.materializedData\.semanticSidecar/);
});

test('AI 工作区损坏时可以打开正式模型且不清除业务状态', () => {
  const layout = readFileSync(new URL('../../layout/AppLayout.tsx', import.meta.url), 'utf8');
  const app = readFileSync(new URL('../../App.tsx', import.meta.url), 'utf8');
  const boundary = readFileSync(new URL('./ai-workspace-error-boundary.tsx', import.meta.url), 'utf8');

  assert.match(layout, /<AiWorkspaceErrorBoundary/);
  assert.match(app, /function WorkspaceShell\(\)[\s\S]*<AiWorkspaceErrorBoundary[\s\S]*<LinguanWorkspaceProvider>/);
  assert.match(app, /<CollaborationProvider>[\s\S]*<WorkspaceShell \/>/);
  assert.match(boundary, /草稿内容暂时无法显示/);
  assert.match(boundary, /打开正式模型/);
  assert.doesNotMatch(boundary, /resetDemo|resetWorkspace|removeItem|clear\(/);
});

test('v2 审核批次说明完整模型范围、版本变化和分组目的', () => {
  const batch = projectReviewBatch(semanticFixture, 'v2');
  assert.equal(batch.systemName, '数码产品销售仓储语义模型');
  assert.equal(batch.sourceFile, '02-订单履约与发货设计-v2.md');
  assert.equal(batch.targetModelVersion, 'V2');
  assert.equal(batch.totalItems, 112);
  assert.equal(batch.groupCount, 4);
  assert.deepEqual(batch.changes, { inherited: 2, modified: 29, added: 81, removedFromPrevious: 37 });
  assert.equal(batch.groups[0].purpose, '商品、仓库、客户等会被多个业务流程共同使用；这里确认它们只有一套一致定义。');
  assert.equal(batch.groups[0].items.length, batch.groups[0].totalItems);
  assert.ok(batch.groups.flatMap((group) => group.items).some((item) => item.change === 'ADDED'));
  assert.ok(batch.groups.flatMap((group) => group.items).some((item) => item.change === 'MODIFIED'));
  assert.ok(batch.groups.flatMap((group) => group.items).some((item) => item.change === 'INHERITED'));
  assert.ok(batch.groups.flatMap((group) => group.items).every((item) => item.reason && item.target));
  assert.ok(batch.groups.flatMap((group) => group.items).every((item) => item.targetRef));
  assert.ok(batch.groups.flatMap((group) => group.items).filter((item) => item.type === 'FIELD')
    .every((item) => item.targetRef?.kind === 'FIELD' && item.targetRef.ownerId));
});

test('审核分类数字只统计人工待确认对象且合计与标题一致', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  const snapshot = await uploadAndGenerate(runtime, 'v1');
  const result = snapshot.results.at(-1)!;
  const group = projectReviewBatch(semanticFixture, 'v1').groups[0];
  const projection = projectPendingReviewItems(group, result.decisions);

  assert.equal(projection.items.length, 27);
  assert.deepEqual(projection.counts, {
    ENTITY: 0, EVENT: 0, FIELD: 24, RELATION: 3, METRIC: 0,
  });
  assert.equal(Object.values(projection.counts).reduce((sum, count) => sum + count, 0), projection.items.length);
  assert.ok(projection.items.every((item) => result.decisions[item.itemId] === undefined));
});

test('审核清单固定把重点确认放在修改、新增和继承之前', () => {
  const item = (overrides: Partial<ReviewItemGuidance>): ReviewItemGuidance => ({
    itemId: 'base', name: '基础对象', type: 'FIELD', change: 'INHERITED',
    recommendation: 'ADOPT', basis: 'EXPLICIT', reason: '明确资料依据', target: '目标位置',
    affectedRefs: [], evidenceCount: 1, ...overrides,
  });
  const sorted = filterAndSortReviewItems([
    item({ itemId: 'added', name: '新增项', change: 'ADDED' }),
    item({ itemId: 'inferred', name: '推断项', recommendation: 'VERIFY', basis: 'INFERRED', change: 'ADDED' }),
    item({ itemId: 'modified', name: '修改项', change: 'MODIFIED' }),
    item({ itemId: 'attention', name: '缺依据项', recommendation: 'VERIFY', basis: 'ATTENTION', evidenceCount: 0 }),
    item({ itemId: 'inherited', name: '继承项' }),
  ], 'ALL');
  assert.deepEqual(sorted.map((value) => value.itemId), ['attention', 'inferred', 'modified', 'added', 'inherited']);
  assert.deepEqual(filterAndSortReviewItems(sorted, 'FIELD').map((value) => value.itemId), sorted.map((value) => value.itemId));
  assert.deepEqual(filterAndSortReviewItems(sorted, 'PRIORITY').map((value) => value.itemId), ['attention', 'inferred']);
});

test('审核交互返回明确焦点并支持分类切换与内联异议', () => {
  const initial = { groupId: 'inventory', filter: 'ALL' as const, mode: 'BROWSE' as const };
  const filtered = transitionReviewInteraction(initial, { type: 'TOGGLE_FILTER', filter: 'FIELD' });
  assert.deepEqual(filtered, {
    state: { groupId: 'inventory', filter: 'FIELD', mode: 'BROWSE' },
    focus: { kind: 'REVIEW_LIST', groupId: 'inventory' },
  });
  assert.equal(transitionReviewInteraction(filtered.state, { type: 'TOGGLE_FILTER', filter: 'FIELD' }).state.filter, 'ALL');
  const priority = transitionReviewInteraction(initial, { type: 'SHOW_PRIORITY' });
  assert.equal(priority.state.filter, 'PRIORITY');
  assert.deepEqual(priority.focus, { kind: 'REVIEW_LIST', groupId: 'inventory' });
  const objection = transitionReviewInteraction(priority.state, { type: 'START_OBJECTION' });
  assert.equal(objection.state.mode, 'SELECT_OBJECTION');
  const editing = transitionReviewInteraction(objection.state, { type: 'SELECT_OBJECTION_ITEM', itemId: 'field_inventory_status' });
  assert.deepEqual(editing.focus, { kind: 'OBJECTION_REASON', itemId: 'field_inventory_status' });
  assert.equal(editing.state.mode, 'EDIT_OBJECTION');
  assert.equal(editing.state.selectedItemId, 'field_inventory_status');
});

test('内联异议原因必须包含中文且不能为空', () => {
  assert.equal(validateObjectionReason(''), '请输入中文异议原因');
  assert.equal(validateObjectionReason('use another field'), '请输入中文异议原因');
  assert.equal(validateObjectionReason('需要使用集团统一商品编码'), null);
});

test('模型对象投影关联审核状态、字段所属表与维度目标', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  const snapshot = await uploadAndGenerate(runtime, 'v1');
  const result = snapshot.results.at(-1)!;
  const objects = projectModelObjects(result.fixture, result);
  const byKey = new Map(objects.map((item) => [modelingObjectRefKey(item.ref), item]));

  const autoDecision = Object.values(result.decisions).find((item) => item.source === 'AUTO')!;
  const autoGuidance = projectReviewBatch(semanticFixture, 'v1').groups.flatMap((group) => group.items)
    .find((item) => item.itemId === autoDecision.itemId)!;
  assert.equal(byKey.get(modelingObjectRefKey(autoGuidance.targetRef!))?.reviewStatus, 'AUTO_APPROVED');

  const field = objects.find((item) => item.ref.kind === 'FIELD')!;
  assert.ok(field.ref.ownerId);
  const table = objects.find((item) => item.ref.kind === 'TABLE' && item.ref.objectId === field.ref.ownerId)!;
  assert.equal(table.childRefs.some((ref) => modelingObjectRefKey(ref) === modelingObjectRefKey(field.ref)), true);
  assert.equal(objects.find((item) => item.code === 'field_product_category')?.fieldRole, 'DIMENSION');
  assert.equal(objects.find((item) => item.code === 'field_inventory_on_hand_quantity')?.fieldRole, 'MEASURE');
  assert.equal(objects.find((item) => item.code === 'field_product_key')?.fieldRole, 'PLAIN');

  const dimension = objects.find((item) => item.ref.kind === 'DIMENSION' && item.ref.objectId === 'dimension_product_category')!;
  assert.equal(dimension.reviewMode, 'TARGET');
  assert.equal(dimension.reviewSourceRef?.kind, 'FIELD');
  assert.equal(dimension.reviewSourceRef?.ownerId, 'entity_product');
  assert.equal(dimension.details.flatMap((detail) => detail.links ?? []).at(0)?.ref.kind, 'FIELD');
  const relation = objects.find((item) => item.ref.kind === 'RELATION')!;
  assert.ok(relation.details.flatMap((detail) => detail.links ?? []).some((link) => link.ref.kind === 'TABLE'));
  const metric = objects.find((item) => item.ref.kind === 'METRIC')!;
  assert.ok(metric.details.flatMap((detail) => detail.links ?? []).some((link) => link.ref.kind === 'TABLE'));
});

test('工作流投影只展开当前版本并生成可悬浮状态', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  await uploadAndGenerate(runtime, 'v1');
  const snapshot = await uploadAndGenerate(runtime, 'v2');
  const versions = projectWorkflowVersions(snapshot);
  assert.deepEqual(versions.map((item) => [item.documentVersion, item.collapsed]), [['v1', true], ['v2', false]]);

  const status = projectStickyStatus(snapshot);
  assert.equal(status.documentVersion, 'v2');
  assert.equal(status.stage, 'REVIEW');
  assert.equal(status.autoApproved, 17);
  assert.equal(status.pending, 95);
  assert.equal(status.percent, 15);
});

test('当前原型用户为页头和审核共用的登录身份', () => {
  assert.deepEqual(localCurrentUser, {
    userId: 'linguan-local-user', displayName: '灵光用户', initials: 'LG',
  });
});

test('只自动采纳具有精确高置信度和明确资料依据的建议项', () => {
  const expectedAutoCounts = { v1: 12, v2: 17, v4: 17 } as const;
  for (const [version, expectedCount] of Object.entries(expectedAutoCounts)) {
    const batch = projectReviewBatch(semanticFixture, version as 'v1' | 'v2' | 'v4');
    const automatic = batch.groups.flatMap((group) => group.items).filter(isAutoReviewEligible);
    assert.equal(automatic.length, expectedCount, version);
    assert.ok(automatic.every((item) => item.confidence !== undefined && item.confidence >= 0.95));
    assert.ok(automatic.every((item) => item.basis === 'EXPLICIT' && item.recommendation === 'ADOPT'));
  }
});

test('生成模型真实写入自动决定，人工确认不覆盖且异议可以覆盖', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  let snapshot = await uploadAndGenerate(runtime, 'v1');
  let result = snapshot.results.at(-1)!;
  const automatic = Object.values(result.decisions).filter((decision) => decision.source === 'AUTO');
  assert.equal(automatic.length, 12);
  assert.equal(result.autoApprovedItemCount, 12);
  assert.equal(result.pendingItemCount, 56);
  assert.ok(automatic.every((decision) => decision.reviewer === 'AI建模助手'));
  assert.ok(automatic.every((decision) => decision.confidence !== undefined && decision.confidence >= 0.95));

  const automaticDecision = automatic[0];
  const item = semanticFixture.documents[0].review.items.find((value) => value.id === automaticDecision.itemId)!;
  snapshot = (await runtime.execute({
    type: 'CONFIRM_BUSINESS_GROUP', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', groupId: item.groupId, reviewer: '审核人', reason: '确认本业务组符合设计资料',
  })).snapshot;
  assert.deepEqual(snapshot.results.at(-1)!.decisions[item.id], automaticDecision);

  snapshot = (await runtime.execute({
    type: 'REJECT_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: item.id, reviewer: '审核人', reason: '对该对象的业务归属存在异议',
  })).snapshot;
  assert.equal(snapshot.results.at(-1)!.decisions[item.id].decision, 'REJECTED');
  assert.equal(snapshot.results.at(-1)!.decisions[item.id].source, 'USER');
});

test('模型发布后不允许再覆盖自动决定或人工决定', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  await uploadAndGenerate(runtime, 'v1');
  const published = await approveAndPublish(runtime, 'v1');
  const itemId = semanticFixture.documents[0].review.items[0].id;
  await assert.rejects(() => runtime.execute({
    type: 'REJECT_ITEM', systemCode: published.systemCode, expectedRevision: published.lockVersion,
    documentVersion: 'v1', itemId, reviewer: '审核人', reason: '发布后不应再修改决定',
  }), /已发布模型的审核决定不可修改/);
});

test('旧状态迁移保留人工决定并为未决定对象补充系统预审', async () => {
  const storage = new MemoryStorage();
  const document = semanticFixture.documents[0];
  const batch = projectReviewBatch(semanticFixture, 'v1');
  const manualItem = batch.groups.flatMap((group) => group.items).find((item) => !isAutoReviewEligible(item))!;
  storage.setItem('linguan:ai-modeling:digital_sales_warehouse:v2', JSON.stringify({
    schemaVersion: 2,
    systemCode: 'digital_sales_warehouse',
    lockVersion: 7,
    uploads: [{ documentVersion: 'v1', fileName: document.fileName, sha256: document.sha256, uploadedAt: '2026-08-05T10:00:00.000Z' }],
    unknownUploads: [],
    results: [{
      documentVersion: 'v1', status: 'GENERATED', generatedAt: '2026-08-05T10:01:00.000Z',
      decisions: { [manualItem.itemId]: { itemId: manualItem.itemId, decision: 'APPROVED', reviewer: '原审核人', reason: '保留原有人工审核意见', decidedAt: '2026-08-05T10:02:00.000Z' } },
      confirmedGroups: [], checks: [], publishedAt: null,
    }],
    catalogs: [], currentCatalogVersion: null,
  }));

  const runtime = createSemanticWorkbenchRuntime({ storage, fixture: semanticFixture });
  const snapshot = await runtime.read('digital_sales_warehouse');
  const migrated = snapshot.results[0];
  assert.equal(migrated.decisions[manualItem.itemId].source, 'USER');
  assert.equal(migrated.decisions[manualItem.itemId].reviewer, '原审核人');
  assert.equal(migrated.autoApprovedItemCount, 12);
  assert.ok(storage.getItem('linguan:ai-modeling:digital_sales_warehouse:v2'));
  assert.ok(storage.getItem('linguan:ai-modeling:digital_sales_warehouse:v4'));
});

test('审核问题只保留会改变状态的 Codex 式选项', () => {
  const batch = projectReviewBatch(semanticFixture, 'v1');
  const group = batch.groups[0];
  const prompt = projectReviewPrompt({ kind: 'GROUP', batch, group });
  assert.equal(prompt.choices.length, 1);
  assert.equal(prompt.choices[0].recommended, true);
  assert.equal(prompt.choices[0].intent.type, 'CONFIRM_GROUP');
  assert.deepEqual(interpretReviewInput('采纳并使用此理由', prompt), prompt.choices[0].intent);
  assert.deepEqual(interpretReviewInput('采纳本组，理由：已经核对字段归属和来源依据', prompt), {
    type: 'CONFIRM_GROUP', groupId: group.id, reason: '已经核对字段归属和来源依据',
  });
  assert.equal(interpretReviewInput('暂时保留', prompt).type, 'NEEDS_CLARIFICATION');

  const object = group.items[0];
  assert.deepEqual(resolveObjectionInput(`对${object.name}有异议，因为业务归属需要重新确认`, batch), {
    type: 'REJECT_ITEM', itemId: object.itemId, reason: '业务归属需要重新确认',
  });
  assert.equal(resolveObjectionInput('我有异议', batch).type, 'NEEDS_CLARIFICATION');
});

test('对话不再询问审核人，并在全部决定后提供发布选项', () => {
  const conversationSource = readFileSync(new URL('./review-conversation.ts', import.meta.url), 'utf8');
  assert.doesNotMatch(conversationSource, /REVIEWER|SET_REVIEWER|reviewer:/);
  const publishPrompt = projectReviewPrompt({
    kind: 'PUBLISH', documentVersion: 'v1', modelVersion: 'V1', rejectedCount: 1,
  });
  assert.equal(publishPrompt.choices.length, 1);
  assert.equal(publishPrompt.choices[0].recommended, true);
  assert.equal(publishPrompt.choices[0].intent.type, 'PUBLISH');
  assert.equal(publishPrompt.description, '当前有 1 项异议会随版本保留。发布时将确认模型可以正常连接、计算和追溯。');
  assert.deepEqual(interpretReviewInput('确认发布', publishPrompt), publishPrompt.choices[0].intent);
  assert.deepEqual(interpretReviewInput('确认发布，理由：本版本审核完成并同意发布', publishPrompt), {
    type: 'PUBLISH', reason: '本版本审核完成并同意发布',
  });
  assert.equal(interpretReviewInput('我先看看发布检查', publishPrompt).type, 'NEEDS_CLARIFICATION');
  assert.equal(interpretReviewInput('暂不发布', publishPrompt).type, 'NEEDS_CLARIFICATION');
});

test('六类语义对象使用唯一共享视觉并始终保留文字', () => {
  assert.deepEqual(semanticVisualKinds, ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC']);
  const visuals = semanticVisualKinds.map(semanticObjectVisual);
  assert.equal(new Set(visuals.map((visual) => visual.stroke)).size, 6);
  assert.deepEqual(visuals.map((visual) => visual.label), ['实体', '事件', '字段', '关系', '维度', '指标']);
  assert.match(renderToStaticMarkup(SemanticObjectTag({ kind: 'ENTITY', label: '实体表' })), /实体表/);
  assert.match(renderToStaticMarkup(SemanticObjectTag({ kind: 'EVENT', label: '事件表' })), /事件表/);
  assert.equal(semanticObjectVisual('ENTITY').stroke, '#1677ff');
  assert.equal(semanticObjectVisual('EVENT').stroke, '#722ed1');
  assert.equal(semanticObjectVisual('FIELD').stroke, '#64748b');
  assert.equal(semanticObjectVisual('RELATION').stroke, '#c41d7f');
  assert.equal(semanticObjectVisual('DIMENSION').stroke, '#08979c');
  assert.equal(semanticObjectVisual('METRIC').stroke, '#d46b08');
});

test('字段角色只显示一个与语义角色一致的共享标签', () => {
  const plain = renderToStaticMarkup(SemanticObjectTag({ kind: 'FIELD', role: 'PLAIN' }));
  const dimension = renderToStaticMarkup(SemanticObjectTag({ kind: 'FIELD', role: 'DIMENSION' }));
  const measure = renderToStaticMarkup(SemanticObjectTag({ kind: 'FIELD', role: 'MEASURE' }));
  assert.match(plain, /普通字段/);
  assert.match(plain, /#64748b/);
  assert.match(dimension, /维度字段/);
  assert.match(dimension, /#08979c/);
  assert.doesNotMatch(dimension, />字段</);
  assert.match(measure, /度量字段/);
  assert.match(measure, /#d46b08/);
  assert.doesNotMatch(measure, />字段</);
});

test('生成摘要分别提供实体、事件和全部字段入口', async () => {
  const { projectModelResultNavigation } = await import('./result-navigation.ts');
  const summary = projectModelResultNavigation(semanticFixture.documents[0]);
  assert.deepEqual(summary.items.map((item) => [item.label, item.count, item.target]), [
    ['实体', 4, { category: 'tables', tableKind: 'ENTITY' }],
    ['事件', 2, { category: 'tables', tableKind: 'EVENT' }],
    ['字段', 47, { category: 'tables', fieldMode: true }],
    ['关系', 9, { category: 'relations' }],
    ['维度', 4, { category: 'dimensions' }],
    ['指标', 6, { category: 'metrics' }],
  ]);
});

test('右侧结果把实体和事件分开并保留全部字段钻取', async () => {
  const { projectTableKindCounts, filterTableGroupsByKind } = await import('./result-navigation.ts');
  const document = semanticFixture.documents[0];
  assert.deepEqual(projectTableKindCounts(document), { ENTITY: 4, EVENT: 2, FIELD: 47 });
  assert.deepEqual(filterTableGroupsByKind(document, 'ENTITY').map((table) => table.table_id), [
    'entity_product', 'entity_warehouse', 'entity_storage_location', 'entity_administrative_region',
  ]);
  assert.deepEqual(filterTableGroupsByKind(document, 'EVENT').map((table) => table.table_id), [
    'event_inventory_snapshot', 'event_inventory_movement',
  ]);
  assert.equal(filterTableGroupsByKind(document).flatMap((table) => table.fields).length, 47);
});

test('本体属性展示把身份、单一角色和使用方式投影为紧凑信息', async () => {
  const { projectAttributePresentation } = await import('../../pages/modeling/attribute-presentation.ts');
  const workspace = projectLinguanWorkspaceData(adaptPublishedModel('digital_sales_warehouse', 'v1', 'V1'));
  const attribute = workspace.entities.flatMap((entity) => entity.attributes)
    .find((item) => item.attributeCode === 'field_product_category')!;
  assert.deepEqual(projectAttributePresentation(attribute, true), {
    name: '商品品类',
    code: 'field_product_category',
    fieldRole: 'DIMENSION',
    usageLabels: ['可筛选', '可归因'],
  });
});

test('六个业务页遵守单一主动作与响应式抽屉契约', () => {
  const modeling = readFileSync(new URL('../../pages/modeling/SemanticModelingPage.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(modeling, /key: 'learning', label: '学习所有本体'/);
  assert.match(modeling, />学习来源</);
  assert.match(modeling, /placeholder="搜索字段名称、编码或语义类型"/);
  assert.doesNotMatch(modeling, />刷新</);
  assert.match(modeling, /selectedRowKeys\.length > 0/);
  for (const relative of [
    '../../pages/metric2/MetricDefTab.tsx', '../../pages/metric2/TargetTab.tsx',
    '../../pages/metric2/BusinessRulePage.tsx', '../../pages/alias/AliasPage.tsx',
    '../../pages/time/CalendarTab.tsx', '../../pages/time/RuleTab.tsx', '../../pages/time/HolidayTab.tsx',
  ]) {
    const source = readFileSync(new URL(relative, import.meta.url), 'utf8');
    assert.match(source, /MoreOutlined/, relative);
    assert.match(source, /width="min\(/, relative);
  }
});

test('发布导航提示只响应真实发布并在进入本体页后清除', async () => {
  const { transitionPublishedNavigationHint } = await import('./publish-navigation.ts');
  const published = transitionPublishedNavigationHint(null, {
    type: 'PUBLISHED', systemCode: 'digital_sales_warehouse', catalogVersion: 'V2',
  });
  assert.deepEqual(published, { systemCode: 'digital_sales_warehouse', catalogVersion: 'V2' });
  assert.deepEqual(transitionPublishedNavigationHint(published, { type: 'VIEW_HISTORY' }), published);
  assert.equal(transitionPublishedNavigationHint(published, {
    type: 'ENTER_MODELING', systemCode: 'default', catalogVersion: 'V1',
  }), published);
  assert.equal(transitionPublishedNavigationHint(published, {
    type: 'ENTER_MODELING', systemCode: 'digital_sales_warehouse', catalogVersion: 'V2',
  }), null);
  assert.deepEqual(transitionPublishedNavigationHint(published, {
    type: 'ENTER_MODELING', systemCode: 'digital_sales_warehouse', catalogVersion: 'V1',
  }), published);
});

test('当前流程、最新发布版和旧历史版投影不同的助手权限', async () => {
  const storage = new MemoryStorage();
  const runtime = createSemanticWorkbenchRuntime({ storage, fixture: semanticFixture });
  const copies = createModelCopyStore(storage);
  await uploadAndGenerate(runtime, 'v1');
  let snapshot = await approveAndPublish(runtime, 'v1');
  copies.publish(adaptPublishedModel('digital_sales_warehouse', 'v1', 'V1'));
  await uploadAndGenerate(runtime, 'v2');
  snapshot = await approveAndPublish(runtime, 'v2');
  copies.publish(adaptPublishedModel('digital_sales_warehouse', 'v2', 'V2'));
  snapshot = await uploadAndGenerate(runtime, 'v3');

  const v1 = projectActiveWorkspaceView(snapshot, 'V1', (version) => copies.read(snapshot.systemCode, version));
  assert.equal(v1.workflowMode, 'PUBLISHED');
  assert.equal(v1.result?.documentVersion, 'v1');
  assert.equal(v1.catalog?.modelVersion, 'V1');
  assert.deepEqual(v1.assistantAccess, {
    canAsk: true, canUpload: false, canReview: false, canPublish: false, recordReadOnly: true,
  });
  assert.equal(v1.modelReadOnly, true);
  assert.deepEqual([
    v1.model?.tables.length,
    v1.model?.fields.length,
    v1.model?.relations.length,
    v1.model?.dimensions.length,
    v1.model?.metrics.length,
  ], [6, 47, 9, 4, 6]);

  const v2 = projectActiveWorkspaceView(snapshot, 'V2', (version) => copies.read(snapshot.systemCode, version));
  assert.equal(v2.result?.documentVersion, 'v2');
  assert.deepEqual(v2.assistantAccess, {
    canAsk: true, canUpload: true, canReview: false, canPublish: false, recordReadOnly: true,
  });
  assert.equal(v2.modelReadOnly, false);

  const workflow = projectActiveWorkspaceView(snapshot, 'WORKSPACE', (version) => copies.read(snapshot.systemCode, version));
  assert.equal(workflow.workflowMode, 'LIVE');
  assert.equal(workflow.result?.documentVersion, 'v3');
  assert.equal(workflow.model?.modelVersion, 'V2');
  assert.deepEqual(workflow.assistantAccess, {
    canAsk: true, canUpload: true, canReview: true, canPublish: true, recordReadOnly: false,
  });
  assert.deepEqual(projectWorkspaceVersionOptions(snapshot).map((option) => option.value), ['WORKSPACE', 'V1', 'V2']);
});

test('发布前检查只说明将确认什么，不伪装成通过结果', () => {
  const view = projectPublicationChecks(semanticFixture.documents[0], []);
  assert.equal(view.summary, '发布时将确认 4 项内容，确保模型可以正常连接、计算和追溯。');
  assert.equal(view.completed, false);
  assert.equal(view.autoExpand, false);
  assert.deepEqual(view.items.map((item) => [item.title, item.status]), [
    ['表之间可以正确关联', 'PENDING'],
    ['字段定义没有冲突', 'PENDING'],
    ['指标具备计算条件', 'PENDING'],
    ['结果可以追溯设计资料', 'PENDING'],
  ]);
});

test('发布后检查使用客户语言呈现真实数量和失败处理方向', () => {
  const checks: PublicationCheck[] = [
    { id: 'RELATION_ENDPOINTS', label: '内部关系检查', passed: true, detail: '内部详情' },
    { id: 'FIELD_UNIQUENESS', label: '内部字段检查', passed: true, detail: '内部详情' },
    { id: 'METRIC_DEPENDENCIES', label: '内部指标检查', passed: false, detail: '内部详情' },
    { id: 'SOURCE_EVIDENCE', label: '内部来源检查', passed: true, detail: '内部详情' },
  ];
  const view = projectPublicationChecks(semanticFixture.documents[0], checks);
  assert.equal(view.summary, '1 项需要处理');
  assert.equal(view.completed, true);
  assert.equal(view.passedCount, 3);
  assert.equal(view.failedCount, 1);
  assert.equal(view.autoExpand, true);
  assert.deepEqual(view.items.map((item) => item.detail), [
    '9 条关系均已连接到对应数据表',
    '6 张数据表中没有重复字段定义',
    '部分指标缺少计算字段，请返回指标清单处理',
    '表、关系和指标都能追溯到设计资料',
  ]);
  assert.ok(view.items.every((item) => !/端点|字段编码唯一|依赖字段存在/.test(`${item.title}${item.detail}`)));
});

test('发布检查失败时在能够确定的情况下指出具体业务对象', () => {
  const document = structuredClone(semanticFixture.documents[0]);
  document.artifacts.model.metrics[0].measure_fields = ['missing_measure_field'];
  const checks: PublicationCheck[] = [
    { id: 'RELATION_ENDPOINTS', label: '', passed: true, detail: '' },
    { id: 'FIELD_UNIQUENESS', label: '', passed: true, detail: '' },
    { id: 'METRIC_DEPENDENCIES', label: '', passed: false, detail: '' },
    { id: 'SOURCE_EVIDENCE', label: '', passed: true, detail: '' },
  ];
  const metricCheck = projectPublicationChecks(document, checks).items
    .find((item) => item.id === 'METRIC_DEPENDENCIES');
  assert.equal(metricCheck?.detail, '指标“在库数量”缺少计算字段，请返回指标清单处理');
});

test('成功、失败、进行中和等待状态复用同一个可访问标记', () => {
  const tones = ['SUCCESS', 'ERROR', 'ACTIVE', 'PENDING'] as const;
  const markup = tones.map((tone) => renderToStaticMarkup(WorkflowStatusMark({ tone, label: `状态${tone}` })));
  assert.ok(markup.every((value) => value.includes('workflow-status-mark')));
  assert.ok(markup.every((value, index) => value.includes(`workflow-status-mark-${tones[index].toLowerCase()}`)));
  assert.ok(markup.every((value, index) => value.includes(`aria-label="状态${tones[index]}"`)));
  assert.ok(markup.every((value) => !value.includes('CheckCircleOutlined') && !value.includes('CloseCircleOutlined')));
});

test('已发布状态使用独立语义和统一绿色状态标记', () => {
  const markup = renderToStaticMarkup(WorkflowStatusMark({ tone: 'PUBLISHED', label: '已发布' }));
  assert.match(markup, /workflow-status-mark-published/);
  assert.match(markup, /anticon-check/);
  assert.match(markup, /style="color:var\(--dh-success\)"/);
  assert.doesNotMatch(markup, /workflow-status-mark-success/);
});

test('AI 建模桌面使用顶部状态、中间工作流和可收回右侧检查器', () => {
  const page = readFileSync(new URL('./ai-modeling-page.tsx', import.meta.url), 'utf8');
  const assistant = readFileSync(new URL('./assistant-panel.tsx', import.meta.url), 'utf8');
  const styles = readFileSync(new URL('./ai-modeling.css', import.meta.url), 'utf8');
  assert.match(page, /ModelingDocumentInspector/);
  assert.match(page, /ModelResultInspector/);
  assert.doesNotMatch(page, /SourceContextInspector|SourceCenter/);
  assert.match(styles, /grid-template-columns:\s*minmax\(0,\s*1fr\)\s+clamp\(360px,\s*32vw,\s*480px\)/);
  assert.doesNotMatch(styles, /grid-template-rows:\s*minmax\(0,\s*3fr\)\s+minmax\(260px,\s*2fr\)/);
  assert.doesNotMatch(page, /assistant-mobile-trigger/);
  assert.doesNotMatch(assistant, />确认本组</);
  assert.doesNotMatch(assistant, />确认发布</);
  assert.doesNotMatch(assistant, /title="提出异议"/);
  assert.doesNotMatch(assistant, /输入本次审核人姓名/);
});

test('分析时间线使用当前资料真实数量且完成后可持久展示', () => {
  const v2 = semanticFixture.documents[1];
  const timeline = buildAnalysisTimeline(v2);
  assert.equal(timeline.length, 7);
  assert.equal(timeline[0].status, 'ACTIVE');
  assert.match(timeline[1].label, /02-订单履约与发货设计-v2\.md/);
  assert.match(timeline[2].label, /9 个业务章节/);
  assert.match(timeline[3].label, /5 个实体候选、5 个事件候选、29 个维度候选、16 个指标候选/);
  assert.match(timeline[4].label, /9 张数据表、78 个字段、15 条关系/);
  assert.match(timeline[5].label, /10 个最终指标和 112 个评审对象/);
  assert.match(timeline[6].label, /4 个业务审核组/);
  assert.deepEqual(buildAnalysisTimeline(v2, { completed: true }).map((line) => line.status), Array(7).fill('DONE'));
});

test('多来源时间线使用证据来源数量而不是空业务章节', () => {
  const document = resolveModelingScenario('group_retail_ops').fixture.documents[0];
  const timeline = buildAnalysisTimeline(document, { completed: true, sourceCount: 8 });
  assert.match(timeline[0].label, /正在读取来源结构/);
  assert.match(timeline[2].label, /8 个证据来源/);
  assert.doesNotMatch(timeline[2].label, /0 个业务章节/);
});

test('发布模型投影为同事原页面需要的完整工作空间数据', () => {
  const source = adaptPublishedModel('digital_sales_warehouse', 'v2', 'V2');
  const workspace = projectLinguanWorkspaceData(source);
  assert.equal(workspace.entities.length, source.entities.length);
  assert.equal(workspace.events.length, source.events.length);
  assert.equal(workspace.entities.concat(workspace.events).flatMap((item) => item.attributes).length, source.fields.length);
  assert.equal(workspace.semanticRelations.length, source.relations.length);
  assert.equal(workspace.metrics.length, source.metrics.length);
  assert.ok(workspace.semanticRelations.every((relation) =>
    workspace.entities.concat(workspace.events).some((item) => item.id === relation.sourceId)
    && workspace.entities.concat(workspace.events).some((item) => item.id === relation.targetId)));
  assert.ok(workspace.metrics.every((metric) => workspace.events.some((event) => event.id === metric.eventId)));
  assert.equal(workspace.entityHierarchies.length, 4);
  assert.equal(workspace.objectValues.length, 10);
  assert.equal(workspace.aliases.length, 166);
  assert.ok(workspace.aliases.some((alias) => alias.targetType === 'ATTRIBUTE'));
  assert.ok(workspace.aliases.some((alias) => alias.targetType === 'DIMENSION'));
  assert.ok(workspace.aliases.some((alias) => alias.targetType === 'RULE'));
  assert.deepEqual(workspace.calendars, []);
});

test('模型空间注册表保留固定空间并恢复用户创建空间', () => {
  const storage = new MemoryStorage();
  const registry = createWorkspaceRegistry(storage, () => '2026-08-05T12:00:00.000Z');
  assert.deepEqual(registry.list().map((item) => [item.systemCode, item.kind]), [
    ['default', 'DEFAULT'],
    ['digital_sales_warehouse', 'FIXTURE'],
    ['omnichannel_retail_ops', 'FIXTURE'],
    ['guanyijia_erp', 'FIXTURE'],
    ['group_retail_ops', 'FIXTURE'],
  ]);
  registry.create({ systemCode: 'retail_service', displayName: '零售服务模型' });
  assert.equal(createWorkspaceRegistry(storage).get('retail_service')?.displayName, '零售服务模型');
  assert.throws(() => registry.create({ systemCode: 'retail_service', displayName: '重复空间' }), /已存在/);
  assert.equal(validateModelingCode('retail_service'), null);
  assert.match(validateModelingCode('Retail-Service') ?? '', /小写字母/);
});

test('上传路由只用受信 SHA 归属，不让助手猜系统编码', () => {
  const registry = createWorkspaceRegistry(new MemoryStorage());
  const known = semanticFixture.documents[0];
  assert.deepEqual(resolveUploadRouting({
    currentSystemCode: 'default', sha256: known.sha256, workspaces: registry.list(), fixture: semanticFixture,
  }), {
    kind: 'SWITCH_EXISTING', systemCode: 'digital_sales_warehouse', documentCode: 'digital_sales_semantic_design',
  });
  assert.deepEqual(resolveUploadRouting({
    currentSystemCode: 'digital_sales_warehouse', sha256: known.sha256, workspaces: registry.list(), fixture: semanticFixture,
  }), {
    kind: 'CURRENT', systemCode: 'digital_sales_warehouse', documentCode: 'digital_sales_semantic_design',
  });
  assert.deepEqual(resolveUploadRouting({
    currentSystemCode: 'default', sha256: 'unknown-sha', workspaces: registry.list(), fixture: semanticFixture,
  }), { kind: 'CHOOSE_DESTINATION', sha256: 'unknown-sha' });
});

test('目标空间已有同一模型时切换动作直接打开现有版本而不重复上传', async () => {
  const registry = createWorkspaceRegistry(new MemoryStorage());
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  await uploadAndGenerate(runtime, 'v1');
  const published = await approveAndPublish(runtime, 'v1');
  const document = semanticFixture.documents[0];

  assert.deepEqual(resolveUploadRouting({
    currentSystemCode: 'default', sha256: document.sha256, workspaces: registry.list(),
    fixture: semanticFixture, targetSnapshot: published,
  }), {
    kind: 'SWITCH_EXISTING', systemCode: 'digital_sales_warehouse',
    documentCode: 'digital_sales_semantic_design', documentVersion: 'v1',
    continuation: 'OPEN_EXISTING', selection: 'V1',
  });
});

test('陌生资料只有确认资料编码后才进入目标空间且不生成模型', async () => {
  const fixture = {
    schemaVersion: semanticFixture.schemaVersion,
    system: { code: 'retail_service', name: '零售服务模型' },
    documents: [],
  };
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture });
  const initial = await runtime.read('retail_service');
  const content = '# 零售服务设计\n\n## 业务目标\n统一服务流程。';
  const sha256 = await sha256Hex(content, null);
  await assert.rejects(() => runtime.execute({
    type: 'UPLOAD_DOCUMENT', systemCode: 'retail_service', expectedRevision: initial.lockVersion,
    fileName: 'retail-service.md', content, size: new TextEncoder().encode(content).byteLength, sha256,
  }), /资料编码/);
  const uploaded = (await runtime.execute({
    type: 'UPLOAD_DOCUMENT', systemCode: 'retail_service', expectedRevision: initial.lockVersion,
    documentCode: 'retail_service_design', fileName: 'retail-service.md', content,
    size: new TextEncoder().encode(content).byteLength, sha256,
  })).snapshot;
  assert.equal(uploaded.unknownUploads[0].documentCode, 'retail_service_design');
  assert.equal(uploaded.results.length, 0);
  assert.equal(uploaded.currentCatalogVersion, null);
  assert.equal(uploaded.nextExpectedVersion, null);
});

test('单项审核可以采纳、否决并覆盖系统自动决定，但发布后不可改', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  let snapshot = await uploadAndGenerate(runtime, 'v1');
  const result = snapshot.results.at(-1)!;
  const automatic = Object.values(result.decisions).find((item) => item.source === 'AUTO')!;
  snapshot = (await runtime.execute({
    type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: automatic.itemId, decision: 'REJECTED', reviewer: '灵光用户',
    reason: '该字段归属需要业务负责人再次确认',
  })).snapshot;
  assert.equal(snapshot.results.at(-1)!.decisions[automatic.itemId].decision, 'REJECTED');
  assert.equal(snapshot.results.at(-1)!.decisions[automatic.itemId].source, 'USER');
  snapshot = (await runtime.execute({
    type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: automatic.itemId, decision: 'APPROVED', reviewer: '灵光用户',
    reason: '已核对设计资料并确认采纳该对象',
  })).snapshot;
  assert.equal(snapshot.results.at(-1)!.decisions[automatic.itemId].decision, 'APPROVED');
  assert.equal(snapshot.results.at(-1)!.decisions[automatic.itemId].source, 'USER');
  snapshot = await approveAndPublish(runtime, 'v1');
  await assert.rejects(() => runtime.execute({
    type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: automatic.itemId, decision: 'REJECTED', reviewer: '灵光用户',
    reason: '发布后不允许再次修改审核决定',
  }), /已发布模型的审核决定不可修改/);
});

test('审核投影完整分为待确认、已采纳和已提出意见', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  let snapshot = await uploadAndGenerate(runtime, 'v1');
  const batch = projectReviewBatch(semanticFixture, 'v1');
  const group = batch.groups[0];
  const pendingItem = group.items.find((item) => !snapshot.results.at(-1)!.decisions[item.itemId])!;
  snapshot = (await runtime.execute({
    type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: pendingItem.itemId, decision: 'REJECTED', reviewer: '灵光用户',
    reason: '该对象定义需要补充业务负责人意见',
  })).snapshot;
  const sections = projectReviewSections(group, snapshot.results.at(-1)!.decisions);
  assert.equal(sections.pending.priority.concat(sections.pending.other).length
    + sections.adopted.length + sections.rejected.length, group.items.length);
  assert.equal(sections.rejected[0].item.itemId, pendingItem.itemId);
  assert.ok(sections.adopted.some((item) => item.decision.source === 'AUTO'));
  assert.ok(sections.pending.priority.every((item) => item.recommendation === 'VERIFY'));
});

test('客户说明隐藏技术编码并解释复杂指标，证据按引用完整展开', async () => {
  const runtime = createSemanticWorkbenchRuntime({ storage: new MemoryStorage(), fixture: semanticFixture });
  const snapshot = await uploadAndGenerate(runtime, 'v1');
  const objects = projectModelObjects(snapshot.results[0].fixture, snapshot.results[0]);
  const movement = objects.find((item) => item.code === 'field_movement_quantity')!;
  const inventoryAmount = objects.find((item) => item.code === 'metric_inventory_amount')!;
  assert.equal(projectCustomerDescription(movement).summary, '记录一次库存增加或减少的数量。');
  assert.match(projectCustomerDescription(movement).usage ?? '', /移动方向/);
  assert.equal(projectCustomerDescription(inventoryAmount).summary, '表示当前库存的总价值。');
  assert.match(projectCustomerDescription(inventoryAmount).usage ?? '', /在库数量.*标准成本/);
  assert.match(projectCustomerDescription(inventoryAmount).technicalDefinition ?? '', /semi_additive/);
  const citations = projectEvidenceCitations(snapshot.results[0].fixture, inventoryAmount.evidenceIds);
  assert.equal(citations.length, inventoryAmount.evidenceIds.length);
  assert.ok(citations.every((item) => item.sourceFile && item.section && item.quote && !item.missing));
  assert.equal(projectEvidenceCitations(snapshot.results[0].fixture, ['E999'])[0].missing, true);
});

test('附件适配只接受一份真实 Markdown 并识别文件名粘贴降级', () => {
  const markdown = new File(['# 设计资料'], 'design.md', { type: 'text/markdown' });
  const another = new File(['# 下一份'], 'next.md', { type: 'text/markdown' });
  assert.deepEqual(selectSingleMarkdown(null, markdown), { file: markdown, error: null });
  assert.match(selectSingleMarkdown(markdown, another).error ?? '', /先删除/);
  assert.match(selectSingleMarkdown(null, new File(['x'], 'design.txt')).error ?? '', /Markdown/);
  assert.equal(classifyClipboardAttachment([markdown], ''), 'FILE');
  assert.equal(classifyClipboardAttachment([], '/Users/me/design.md'), 'FILE_NAME_ONLY');
  assert.equal(classifyClipboardAttachment([], '请解释库存模型'), 'TEXT');
});

test('全渠道空间由场景注册表提供且不改变数字销售场景', () => {
  const registry = createWorkspaceRegistry(new MemoryStorage());
  assert.ok(registry.list().some((item) => item.systemCode === 'omnichannel_retail_ops'
    && item.displayName === '全渠道零售经营语义模型'));
  assert.equal(resolveModelingScenario('digital_sales_warehouse').fixture.system.code, 'digital_sales_warehouse');
  assert.equal(resolveModelingScenario('omnichannel_retail_ops').fixture.system.code, 'omnichannel_retail_ops');
});

test('集团零售空间登记十二类连接器并选择八个确定性来源', async () => {
  const registry = createWorkspaceRegistry(new MemoryStorage());
  assert.ok(registry.list().some((item) => item.systemCode === 'group_retail_ops'
    && item.displayName === '零售经营语义模型'));

  const scenario = resolveModelingScenario('group_retail_ops');
  assert.equal(scenario.capabilities.multiSource, true);
  assert.equal(scenario.capabilities.lifecycle, 'FULL_LIFECYCLE');
  const runtime = scenario.createRuntime(new MemoryStorage());
  const snapshot = await runtime.read('group_retail_ops');
  const sourceWorkspace = snapshot.sourceWorkspace as NonNullable<typeof snapshot.sourceWorkspace> & {
    connectorCatalog: Array<{ family: string; status: string }>;
    evidenceClaims: Array<{ authority: string; evidenceRefs: string[] }>;
  };
  assert.equal(sourceWorkspace.connectorCatalog.length, 12);
  assert.equal(sourceWorkspace.connectorCatalog.filter((item) => item.status === 'CONNECTED').length, 8);
  assert.equal(sourceWorkspace.connectorCatalog.filter((item) => item.status === 'AVAILABLE').length, 4);
  assert.equal(sourceWorkspace.availableSources.length, 8);
  assert.deepEqual(sourceWorkspace.availableSources.map((item) => item.displayName), [
    'MySQL · 零售交易库',
    'GitHub · 零售分析仓库',
    'Semantica · 企业术语图',
    'SharePoint · 集团经营知识库',
    'MongoDB · 商品与客户画像',
    'Elasticsearch · 零售搜索索引',
    'MinIO · 集团经营资料库',
    'Kafka · 零售业务事件流',
  ]);
  assert.equal(sourceWorkspace.evidenceClaims.length, 0, '未加入来源时不得提前暴露来源主张');

  let selected = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    sourceIds: sourceWorkspace.availableSources.slice(0, 2).map((item) => item.sourceId),
  })).snapshot;
  const beforeRemoval = {
    sources: selected.sourceWorkspace!.selectedSources.length,
    claims: selected.sourceWorkspace!.evidenceClaims.length,
    locators: Object.keys(selected.sourceWorkspace!.evidenceLocators).length,
  };
  const removedSource = selected.sourceWorkspace!.selectedSources[1]!;
  const removedClaimCount = selected.sourceWorkspace!.evidenceClaims.filter((claim) => claim.sourceId === removedSource.sourceId).length;
  selected = (await runtime.execute({
    type: 'REMOVE_SOURCE', systemCode: selected.systemCode, expectedRevision: selected.lockVersion,
    sourceId: removedSource.sourceId,
  })).snapshot;
  const afterRemoval = {
    sources: selected.sourceWorkspace!.selectedSources.length,
    claims: selected.sourceWorkspace!.evidenceClaims.length,
    locators: Object.keys(selected.sourceWorkspace!.evidenceLocators).length,
  };
  assert.deepEqual({
    sources: afterRemoval.sources - beforeRemoval.sources,
    claims: afterRemoval.claims - beforeRemoval.claims,
    locators: afterRemoval.locators - beforeRemoval.locators,
  }, {
    sources: -1,
    claims: -removedClaimCount,
    locators: -removedSource.evidenceIds.length,
  }, '移除来源必须让该来源的主张与Locator形成反向消失delta');
  assert.ok(selected.sourceWorkspace!.evidenceClaims.every((claim) => claim.sourceId !== removedSource.sourceId));
  assert.ok(removedSource.evidenceIds.every((evidenceId) => !(evidenceId in selected.sourceWorkspace!.evidenceLocators)));
  await assert.rejects(() => runtime.execute({
    type: 'ADD_SOURCES', systemCode: selected.systemCode, expectedRevision: selected.lockVersion,
    sourceIds: ['source-unknown-combination'],
  }), /来源不存在/, '未知来源组合不得制造任何结果');
});

test('集团零售三项冲突处理后生成132项审核并发布V1', async () => {
  const scenario = resolveModelingScenario('group_retail_ops');
  const runtime = scenario.createRuntime(new MemoryStorage());
  let snapshot = await runtime.read('group_retail_ops');
  assert.ok(snapshot.sourceWorkspace);
  snapshot = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    sourceIds: snapshot.sourceWorkspace!.availableSources.map((item) => item.sourceId),
  })).snapshot;
  snapshot = (await runtime.execute({
    type: 'START_MODELING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  })).snapshot;
  const blockers = snapshot.sourceWorkspace!.batch.findings.filter((item) => item.severity === 'BLOCKER');
  assert.equal(blockers.length, 3);
  await assert.rejects(() => runtime.execute({
    type: 'GENERATE_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  }), /3 项来源冲突/);
  for (const finding of blockers) {
    snapshot = (await runtime.execute({
      type: 'RESOLVE_FINDING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
      findingId: finding.findingId, resolutionId: finding.recommendationId,
      reviewer: '灵光用户', reason: '已核对原始来源、派生来源和业务影响，采纳推荐口径',
    })).snapshot;
  }
  snapshot = (await runtime.execute({
    type: 'GENERATE_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  })).snapshot;
  assert.equal(snapshot.results[0].totalItemCount, 132);
  assert.equal(snapshot.results[0].autoApprovedItemCount, 108);
  assert.equal(snapshot.results[0].pendingItemCount, 24);
  const document = snapshot.fixture.documents[0];
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'CONFLICT').length, 3);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'WEAK_EVIDENCE').length, 6);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'SYSTEM_GENERATED').length, 15);
  for (const group of document.review.groups.filter((item) => item.id !== 'auto')) {
    snapshot = (await runtime.execute({
      type: 'CONFIRM_BUSINESS_GROUP', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
      documentVersion: 'v1', groupId: group.id, reviewer: '灵光用户',
      reason: '已逐项核对来源、目标位置和生成原因，确认本组剩余对象',
    })).snapshot;
  }
  snapshot = (await runtime.execute({
    type: 'PUBLISH_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', reviewer: '灵光用户', reason: '三项冲突和全部人工审核已经完成，确认发布模型',
  })).snapshot;
  assert.equal(snapshot.currentCatalogVersion, 'V1');
  const model = scenario.adaptPublished('v1', 'V1');
  assert.deepEqual({
    entities: model.entities.length, events: model.events.length, fields: model.fields.length,
    relations: model.relations.length, dimensions: model.dimensions.length, metrics: model.metrics.length,
    hierarchies: model.hierarchies.length, rules: model.ruleCandidates.length,
  }, { entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11, metrics: 12, hierarchies: 2, rules: 7 });
});

test('全渠道资料包包含五个真实字节文件和三个固定连接来源', async () => {
  assert.equal(omnichannelSourcePack.fileEntries.length, 5);
  assert.equal(omnichannelSourceAssets.length, 8);
  assert.equal(new Set(omnichannelSourcePack.fileEntries.map((item) => item.sha256)).size, 5);
  for (const file of omnichannelSourcePack.fileEntries) {
    assert.equal(await sha256Hex(file.bytes, null), file.sha256);
    assert.ok(file.bytes.byteLength > 0);
  }
  assert.equal(omnichannelSourceAssets.find((item) => item.sourceId === 'source-mysql')?.fingerprint, 'snapshot:mysql:retail_core_prod:2026-08-01');
  assert.equal(omnichannelSourceAssets.find((item) => item.sourceId === 'source-snowflake')?.fingerprint, 'snapshot:snowflake:retail_analytics_prod:2026-08-01');
  assert.equal(omnichannelSourceAssets.find((item) => item.sourceId === 'source-github')?.fingerprint, 'git:retail-platform/retail-analytics:a91f2c7');
});

test('资料包指纹与加入顺序无关且附件限制为十份和二十五 MiB', () => {
  const fingerprints = omnichannelSourceAssets.map((item) => item.fingerprint);
  assert.equal(computeSourceBundleFingerprint(fingerprints), computeSourceBundleFingerprint([...fingerprints].reverse()));
  const files = Array.from({ length: 10 }, (_, index) => new File(['x'], `${index + 1}.pdf`));
  assert.equal(validateSourceAttachments(files).error, null);
  assert.match(validateSourceAttachments([...files, new File(['x'], 'overflow.pdf')]).error ?? '', /10/);
  assert.match(validateSourceAttachments([new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.pdf')]).error ?? '', /10 MiB/);
});

test('全渠道互证冲突在决定前阻止生成，决定后形成 120 项审核和 96 项系统预审', async () => {
  const runtime = createOmnichannelWorkbenchRuntime({ storage: new MemoryStorage() });
  let snapshot = await runtime.read('omnichannel_retail_ops');
  snapshot = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    sourceIds: omnichannelSourceAssets.map((item) => item.sourceId),
  })).snapshot;
  snapshot = (await runtime.execute({
    type: 'REMOVE_SOURCE', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    sourceId: 'source-github',
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.selectedSources.length, 7);
  snapshot = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    sourceIds: ['source-github', 'source-github'],
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.selectedSources.length, 8);
  snapshot = (await runtime.execute({
    type: 'START_MODELING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.batch.state, 'AWAITING_DECISION');
  assert.equal(snapshot.sourceWorkspace?.batch.findings.filter((item) => item.severity === 'BLOCKER').length, 2);
  await assert.rejects(() => runtime.execute({
    type: 'GENERATE_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  }), /处理 2 项来源冲突/);
  for (const finding of snapshot.sourceWorkspace!.batch.findings.filter((item) => item.severity === 'BLOCKER')) {
    snapshot = (await runtime.execute({
      type: 'RESOLVE_FINDING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
      findingId: finding.findingId, resolutionId: finding.recommendationId,
      reviewer: '灵光用户', reason: '已核对业务定义和实际使用，采纳推荐口径',
    })).snapshot;
  }
  snapshot = (await runtime.execute({
    type: 'GENERATE_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
  })).snapshot;
  const result = snapshot.results[0];
  assert.equal(result.totalItemCount, 120);
  assert.equal(result.autoApprovedItemCount, 96);
  assert.equal(result.pendingItemCount, 24);
  assert.equal(snapshot.sourceWorkspace?.batch.state, 'MODEL_READY');
  for (const group of omnichannelScenarioFixture.documents[0].review.groups.filter((item) => item.id !== 'auto')) {
    snapshot = (await runtime.execute({
      type: 'CONFIRM_BUSINESS_GROUP', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
      documentVersion: 'v1', groupId: group.id, reviewer: '灵光用户', reason: '已逐项核对本组来源、目标位置和生成原因并确认采纳',
    })).snapshot;
  }
  snapshot = (await runtime.execute({
    type: 'PUBLISH_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', reviewer: '灵光用户', reason: '全部来源冲突和人工审核项已经完成，确认发布模型',
  })).snapshot;
  assert.equal(snapshot.currentCatalogVersion, 'V1');
  assert.equal(snapshot.sourceWorkspace?.batch.state, 'PUBLISHED');
  assert.equal(snapshot.results[0].checks.filter((item) => item.passed).length, 4);
  await assert.rejects(() => runtime.execute({
    type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', itemId: 'METRIC:metric_net_sales', decision: 'REJECTED', reviewer: '灵光用户', reason: '发布后不应再允许修改审核结果',
  }), /已发布模型不可修改/);
});

test('全渠道 V1 模型计数和发布页消费者来自同一适配结果', () => {
  const snapshot = adaptOmnichannelPublishedModel();
  assert.equal(snapshot.systemCode, 'omnichannel_retail_ops');
  assert.equal(snapshot.entities.length, 6);
  assert.equal(snapshot.events.length, 4);
  assert.equal(snapshot.fields.length, 72);
  assert.equal(snapshot.relations.length, 13);
  assert.equal(snapshot.dimensions.length, 9);
  assert.equal(snapshot.metrics.length, 10);
  assert.equal(snapshot.ruleCandidates.length, 6);
  assert.equal(snapshot.hierarchies.length, 1);
  assert.deepEqual(snapshot.hierarchies[0].levels.map((item) => item.name), ['大区', '省', '市', '区县']);
  assert.ok(snapshot.aliases.length > 100);
  const workspace = projectLinguanWorkspaceData(snapshot);
  assert.equal(workspace.entities.length, 6);
  assert.equal(workspace.events.length, 4);
  assert.equal(workspace.semanticRelations.length, 13);
  assert.equal(workspace.metrics.length, 10);
  assert.equal(workspace.entityHierarchies.length, 4);
  assert.ok(workspace.schemaMappings.some((item) => item.datasourceName === 'retail_core_prod'));
  assert.ok(workspace.schemaMappings.some((item) => item.datasourceName === 'retail_analytics_prod'));
  assert.ok(workspace.calendars.some((item) => item.calendarName === '零售营业日历'));
  const metricWorkspace = projectMetric2WorkspaceData(snapshot);
  assert.equal(metricWorkspace.rules.length, 0);
  assert.equal(metricWorkspace.ruleCandidates?.length, 6);
  assert.ok(metricWorkspace.ruleCandidates?.every((candidate) => candidate.status === 'NEEDS_STRUCTURE'));
  assert.ok(metricWorkspace.metrics.some((metric) => metric.editability === 'READ_ONLY'));
});

test('全渠道 Fixture 固定两项冲突并按冲突、弱依据、系统生成组织人工审核', () => {
  const document = omnichannelScenarioFixture.documents[0];
  assert.equal(document.review.items.length, 120);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'CONFLICT').length, 2);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'WEAK_EVIDENCE').length, 5);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'SYSTEM_GENERATED').length, 17);
  assert.equal(document.review.items.filter((item) => item.reviewClass === 'AUTO').length, 96);
});

test('多来源页面延续现有 AI 工作区并提供资料、互证、模型三个检查视角', () => {
  const inspector = readFileSync(new URL('./source-context-inspector.tsx', import.meta.url), 'utf8');
  const panel = readFileSync(new URL('./assistant-panel.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /资料/);
  assert.match(inspector, /互证/);
  assert.match(inspector, /模型/);
  assert.match(inspector, /业务定义/);
  assert.match(inspector, /物理结构/);
  assert.match(inspector, /实际使用/);
  assert.match(inspector, /辅助证据/);
  assert.match(panel, /连接数据源/);
  assert.match(panel, /连接代码仓库/);
  assert.match(panel, /下载本空间资料包/);
});

test('来源资料检查器使用业务名称，不显示图数据或批次术语', () => {
  const inspector = readFileSync(new URL('./source-context-inspector.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /来源资料/);
  assert.match(inspector, /已载入的资料与比较结果/);
  assert.match(inspector, /来源材料与结论/);
  assert.match(inspector, /术语图中的关联记录/);
  assert.match(inspector, /来源材料/);
  assert.match(inspector, /来源比较/);
  assert.match(inspector, /建模建议/);
  assert.match(inspector, /objectCountLabels/);
  assert.doesNotMatch(inspector, />建模检查器</u);
  assert.doesNotMatch(inspector, />多来源证据批次</u);
  assert.doesNotMatch(inspector, /参与来源与主张/u);
});

test('AI 建模状态与历史记录不向业务用户暴露内部版本编号', () => {
  const stickyStatus = readFileSync(new URL('./sticky-workflow-status.tsx', import.meta.url), 'utf8');
  const history = readFileSync(new URL('./workflow-records.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('./model-result-inspector.tsx', import.meta.url), 'utf8');

  assert.match(stickyStatus, /已选来源/u);
  assert.match(history, /历史资料记录/u);
  assert.match(inspector, /当前资料 ·/u);
  assert.doesNotMatch(stickyStatus, /多来源资料包第/u);
  assert.doesNotMatch(history, /资料 \{version\.documentVersion\}/u);
  assert.doesNotMatch(inspector, /资料 \{result\.documentVersion\}/u);
});

test('AI 建模来源展开以材料位置标识，不展示内部证据编号', () => {
  const inspector = readFileSync(new URL('./model-result-inspector.tsx', import.meta.url), 'utf8');

  assert.match(inspector, /function citationLabel/u);
  assert.match(inspector, /来源材料目录/u);
  assert.doesNotMatch(inspector, /`\$\{citation\.id\} · \$\{citation\.sourceFile\}/u);
  assert.doesNotMatch(inspector, /`\$\{evidence\.evidence_id\} · \$\{evidence\.source_file\}/u);
});

test('集团零售页面显示连接器目录、派生血缘和统一来源入口', () => {
  const inspector = readFileSync(new URL('./source-context-inspector.tsx', import.meta.url), 'utf8');
  const standardization = readFileSync(new URL('../data-standardization/data-standardization-page.tsx', import.meta.url), 'utf8');
  const panel = readFileSync(new URL('./assistant-panel.tsx', import.meta.url), 'utf8');
  const records = readFileSync(new URL('./source-workflow-records.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /更多可接入来源/);
  assert.match(inspector, /派生来源/);
  assert.match(inspector, /locator\.kind === 'RDF'/);
  assert.match(inspector, /locator\.kind === 'KAFKA'/);
  assert.match(panel, /配置新来源/);
  assert.match(panel, /导入 YAML 配置/);
  assert.match(panel, /管理已配置来源/);
  assert.doesNotMatch(inspector, /管理来源|onManageSources/);
  assert.match(standardization, /<SourceCenter/);
  assert.match(standardization, /来源/);
  assert.match(standardization, /互证/);
  assert.doesNotMatch(panel, /选择一个固定来源/);
  assert.doesNotMatch(panel, /activeSystem === 'group_retail_ops'.*sourceIds/s);
  assert.match(records, /当前证据覆盖有限，仍可先建模/);
  assert.doesNotMatch(records, /还需接入/);
});

test('集团零售建模Runtime可以挂接与移除来源中心快照', async () => {
  const runtime = resolveModelingScenario('group_retail_ops').createRuntime(new MemoryStorage());
  let snapshot = await runtime.read('group_retail_ops');
  snapshot = (await runtime.execute({
    type: 'ATTACH_SOURCE_SNAPSHOTS', systemCode: snapshot.systemCode,
    expectedRevision: snapshot.lockVersion, snapshotIds: ['retail-snowflake-snapshot-r1'],
  })).snapshot;
  assert.deepEqual(snapshot.sourceWorkspace?.batch.snapshotIds, ['retail-snowflake-snapshot-r1']);
  snapshot = (await runtime.execute({
    type: 'DETACH_SOURCE_SNAPSHOT', systemCode: snapshot.systemCode,
    expectedRevision: snapshot.lockVersion, snapshotId: 'retail-snowflake-snapshot-r1',
  })).snapshot;
  assert.deepEqual(snapshot.sourceWorkspace?.batch.snapshotIds, []);
});

test('多来源对话工作流 TSX 可以被 TypeScript 解析', () => {
  for (const relative of ['./source-workflow-records.tsx', './source-context-inspector.tsx', './candidate-model-inspector.tsx']) {
    const fileName = new URL(relative, import.meta.url);
    const source = readFileSync(fileName, 'utf8');
    const output = ts.transpileModule(source, {
      fileName: fileName.pathname,
      reportDiagnostics: true,
      compilerOptions: {
        jsx: ts.JsxEmit.ReactJSX,
        module: ts.ModuleKind.ESNext,
        target: ts.ScriptTarget.ES2022,
      },
    });
    const errors = (output.diagnostics ?? [])
      .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
      .map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
    assert.deepEqual(errors, [], relative);
  }
});

test('多来源对话工作流的相对导入文件都存在', () => {
  const fileName = new URL('./source-workflow-records.tsx', import.meta.url);
  const source = readFileSync(fileName, 'utf8');
  const missing = [...source.matchAll(/from\s+['"](\.[^'"]+)['"]/g)]
    .map((match) => match[1])
    .filter((specifier) => !existsSync(new URL(specifier, fileName)));
  assert.deepEqual(missing, []);
});

test('管伊佳固定空间读取已推广的真实数据库证据清单', () => {
  const registry = createWorkspaceRegistry(new MemoryStorage());
  const workspace = registry.get('guanyijia_erp');
  assert.equal(workspace?.displayName, '管伊佳 ERP 语义模型');
  assert.equal(workspace?.kind, 'FIXTURE');
  assert.equal(guanyijiaScenarioFixture.system.code, 'guanyijia_erp');
  assert.equal(guanyijiaScenarioFixture.documents.length, 0);
  assert.deepEqual({ ...guanyijiaEvidenceFixture.manifest.objectCounts, dmlDigests: undefined }, {
    tables: 95, views: 2, columns: 1130, procedures: 35, functions: 0, triggers: 0, events: 1,
    indexes: 270, foreignKeys: 2, tenantTables: 58, tenantViews: 2, dmlDigests: undefined,
  });
  assert.ok(guanyijiaEvidenceFixture.manifest.objectCounts.dmlDigests > 0);
  assert.equal(guanyijiaEvidenceFixture.manifest.warnings.length, 3);
  assert.equal(guanyijiaEvidenceFixture.source.origin, 'DATABASE_CONNECTOR');
  assert.ok(guanyijiaEvidenceFixture.manifest.evidenceFiles.length > 1400);
});

test('管伊佳同时读取固定数据库和 GitHub Commit 证据', () => {
  const repository = guanyijiaRepositoryEvidenceFixture.manifest;
  assert.equal(repository.repository, 'jishenghua/jshERP');
  assert.equal(repository.commit, 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1');
  assert.equal(repository.license, 'Apache-2.0');
  assert.equal(repository.objectCounts.tables, 32);
  assert.equal(repository.objectCounts.services, 31);
  assert.equal(repository.objectCounts.controllers, 30);
  assert.equal(repository.objectCounts.mapperXmlFiles, 61);
  assert.equal(repository.objectCounts.mapperStatements, 573);
  assert.equal(guanyijiaSourceAssets.length, 2);
  assert.deepEqual(guanyijiaSourceAssets.map((source) => source.origin), ['DATABASE_CONNECTOR', 'CODE_REPOSITORY']);
  assert.equal(resolveModelingScenario('guanyijia_erp').capabilities.lifecycle, 'FULL_LIFECYCLE');
});

test('管伊佳 Runtime 迁移旧数据库选择并生成带风险的双来源候选模型', async () => {
  const storage = new MemoryStorage();
  storage.setItem('linguan:ai-modeling:guanyijia_erp:evidence:v1', JSON.stringify({
    schemaVersion: 1, systemCode: 'guanyijia_erp', lockVersion: 4,
    selectedSourceIds: ['guanyijia-mysql-jsh-erp-v1'],
  }));
  const runtime = createGuanyijiaEvidenceRuntime({ storage, now: () => '2026-08-06T20:00:00Z' });
  let snapshot = await runtime.read('guanyijia_erp');
  assert.equal(snapshot.sourceWorkspace?.availableSources.length, 2);
  assert.equal(snapshot.sourceWorkspace?.selectedSources.length, 1);
  snapshot = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: 'guanyijia_erp', expectedRevision: snapshot.lockVersion,
    sourceIds: ['guanyijia-github-jsherp-v1'],
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.selectedSources.length, 2);
  snapshot = (await runtime.execute({
    type: 'START_MODELING', systemCode: 'guanyijia_erp', expectedRevision: snapshot.lockVersion,
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.batch.state, 'AWAITING_DECISION');
  assert.equal(snapshot.sourceWorkspace?.batch.findings.filter((finding) => finding.severity === 'BLOCKER').length, 1);
  snapshot = (await runtime.execute({
    type: 'GENERATE_MODEL', systemCode: 'guanyijia_erp', expectedRevision: snapshot.lockVersion,
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.batch.state, 'BLOCKED');
  assert.equal(snapshot.sourceWorkspace?.candidateModel?.status, 'DRAFT');
  assert.deepEqual(snapshot.sourceWorkspace?.candidateModel?.counts, {
    entities: 13, events: 6, fields: 265, relations: 18, dimensions: 14,
    metrics: 8, hierarchies: 2, ruleCandidates: 8, pendingAssets: 63,
  });
  assert.equal(snapshot.results.length, 0);
  assert.equal(snapshot.catalogs.length, 0);
  assert.equal(projectStickyStatus(snapshot, null, false).stage, 'SOURCE_READY');
  await assert.rejects(() => runtime.execute({
    type: 'PUBLISH_MODEL', systemCode: 'guanyijia_erp', expectedRevision: snapshot.lockVersion,
    documentVersion: 'v1', reviewer: '灵光用户', reason: '确认发布候选模型',
  }), /候选模型暂不支持审核或发布/);
});

test('管伊佳兼容候选模型只覆盖19张核心业务表并保留63张待归类资产', () => {
  assert.equal(guanyijiaCandidateModel.tables.length, 19);
  assert.equal(guanyijiaCandidateModel.entities.length, 13);
  assert.equal(guanyijiaCandidateModel.events.length, 6);
  assert.equal(guanyijiaCandidateModel.fields.length, 265);
  assert.equal(guanyijiaCandidateModel.pendingAssets.length, 63);
  assert.ok(guanyijiaCandidateModel.tables.some((table) => table.code === 'jsh_supplier' && table.name === '往来单位'));
  assert.ok(guanyijiaCandidateModel.metrics.some((metric) => metric.code === 'metric_receivable_debt' && metric.status === 'NEEDS_CONFIRMATION'));
  assert.ok(guanyijiaCandidateModel.hierarchies.some((hierarchy) => hierarchy.name === '商品分类层级'));
  const objectEvidence = [
    ...guanyijiaCandidateModel.tables, ...guanyijiaCandidateModel.relations,
    ...guanyijiaCandidateModel.dimensions, ...guanyijiaCandidateModel.metrics,
    ...guanyijiaCandidateModel.hierarchies, ...guanyijiaCandidateModel.ruleCandidates,
  ].flatMap((item) => item.evidenceIds);
  assert.ok(objectEvidence.length > 0);
  assert.ok(objectEvidence.every((evidenceId) => guanyijiaEvidenceLocators[evidenceId]));
});

test('管伊佳页面同时展示数据库、源码、互证和候选模型', () => {
  const workflow = readFileSync(new URL('./source-workflow-records.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('./source-context-inspector.tsx', import.meta.url), 'utf8');
  for (const text of ['校验资料范围和内容完整性', '读取表、视图和字段结构', '读取存储过程、事件、索引和外键', '读取查询语句结构', '本次范围不包含实际业务数据']) {
    assert.match(workflow, new RegExp(text));
  }
  assert.doesNotMatch(workflow, /Tenant 153|固定 Commit|master ·|finding\.decision\.reason/);
  assert.deepEqual(projectDatabaseEvidenceSections(guanyijiaEvidenceFixture.manifest).map((section) => section.label),
    ['物理结构', '程序逻辑', '实际使用', '数据形态']);
  assert.deepEqual(projectRepositoryEvidenceSections(guanyijiaRepositoryEvidenceFixture.manifest).map((section) => section.label),
    ['结构定义', '数据访问', '业务流程', '业务规则', '租户与权限', '用户术语', '结构演进']);
  assert.doesNotMatch(inspector, /if \(databaseSource\?\.databaseEvidence\) \{/);
  assert.doesNotMatch(inspector, /locator\.commit\.slice/);
  assert.doesNotMatch(inspector, /detail\.manifest\.branch/);
  assert.doesNotMatch(inspector, /displayName \?\? sourceId|displayName \?\? id/);
  assert.match(inspector, /CandidateModelInspector/);
  assert.match(workflow, /读取已保存版本的源码片段/);
  assert.match(workflow, /核心表结构与数据库完成对齐/);
  assert.match(workflow, /candidateModel\?\.pendingAssets\.length \?\? 63/);
});
