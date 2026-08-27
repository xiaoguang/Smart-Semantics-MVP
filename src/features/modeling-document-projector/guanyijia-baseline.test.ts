import assert from 'node:assert/strict';
import test from 'node:test';
import { compileModelingDocument } from '../modeling-document-bridge/compile-modeling-document.ts';
import {
  guanyijiaFrozenModelingArtifact,
  guanyijiaModelingPackage,
} from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { projectCatalogBrowser } from '../collaboration/catalog-browser-adapter.ts';
import { projectModelingDocument } from './index.ts';

test('管伊佳冻结文档与签名语义载荷一一对应', () => {
  const candidate = compileModelingDocument(guanyijiaFrozenModelingArtifact);
  assert.deepEqual(candidate.counts, {
    entities: 14, events: 9, fields: 296, relations: 30, dimensions: 4, metrics: 5,
  });
  assert.equal(candidate.items.filter((item) => item.kind === 'HIERARCHY').length, 2);
  assert.equal(candidate.items.filter((item) => item.kind === 'RULE').length, 8);
  assert.equal(candidate.items.filter((item) => item.kind === 'ALIAS').length, 10);
  assert.equal(candidate.items.filter((item) => item.kind === 'TIME_RULE').length, 9);
  assert.equal(candidate.items.filter((item) => item.kind === 'PENDING_ASSET').length, 63);
  assert.equal(guanyijiaModelingPackage.classification.semanticModelTables.length, 18);
  assert.equal(guanyijiaModelingPackage.classification.technicalSupportTables.length, 14);
  assert.equal(guanyijiaModelingPackage.classification.pendingTables.length, 63);
});

test('管伊佳正式投影同时驱动五个业务页且严格证据浏览通过', () => {
  const projection = projectModelingDocument(guanyijiaFrozenModelingArtifact, null, {
    systemCode: 'guanyijia_erp', modelSpaceId: 'guanyijia_erp',
    datasourceName: 'guanyijia_mysql', schemaName: 'jsh_erp', ownerName: 'AI 建模',
  });
  const workspace = projection.materializedData.workspaceData as Record<string, unknown[]>;
  const metricData = projection.materializedData.metricData as Record<string, unknown[]>;
  assert.equal(workspace.entities.length, 14);
  assert.equal(workspace.events.length, 9);
  assert.equal(workspace.semanticRelations.length, 30);
  assert.equal(metricData.metrics.length, 5);
  assert.equal(metricData.ruleCandidates.length, 8);
  assert.equal(metricData.rules.length, 0);
  assert.equal(workspace.aliases.length, 10);
  assert.equal(workspace.rules.length, 9);
  assert.equal(workspace.entityHierarchies.length, 4);
  assert.equal(workspace.objectValues.length, 0);
  assert.equal(workspace.calendars.length, 0);
  assert.equal(workspace.holidayCalendars.length, 0);
  assert.equal(metricData.metrics.some((item) => String((item as Record<string, unknown>).metricCode) === 'receivable_debt'), false);
  const workbench = projection.materializedData.workbenchState as Record<string, unknown[]>;
  assert.equal(workbench.pendingAssets.length, 63);
  assert.equal(workbench.exclusions.length, 2);

  const catalog = {
    catalogId: 'catalog_guanyijia_v1', catalogVersion: 'V1', fingerprint: 'catalog_guanyijia_v1',
    modelSpaceId: 'guanyijia_erp', data: projection.materializedData,
    authorUserId: 'system_baseline', reviewerUserIds: [], publisherUserId: 'system_baseline',
    publishedAt: guanyijiaFrozenModelingArtifact.updatedAt, requestId: 'SYSTEM_BASELINE',
  };
  const browser = projectCatalogBrowser(catalog, { strictEvidence: true });
  assert.equal(browser.counts.entities, 14);
  assert.equal(browser.counts.events, 9);
  assert.equal(browser.counts.metrics, 5);
  assert.equal(browser.coverageNotices.some((notice) => notice.includes('来源明细')), false);
});
