import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';
import { renderStandardModelingMarkdown } from '../modeling-document-bridge/standard-markdown.ts';
import {
  modelingDocumentSemanticPayloadSha256,
  defaultProjectionContext,
  projectModelingDocument,
  type ModelingDocumentSemanticPayload,
} from './index.ts';

const payload: ModelingDocumentSemanticPayload = {
  schemaVersion: 1,
  projectId: 'guanyijia_erp',
  entities: [{
    code: 'jsh_supplier', name: '往来单位', description: '统一承载客户、供应商和会员角色。',
    physicalTable: 'jsh_supplier', evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:TABLE:jsh_supplier'],
  }],
  events: [{
    code: 'purchase_receipt', name: '采购入库', description: '采购订单审核后形成的入库活动。',
    physicalTable: 'jsh_depot_head', evidenceStatus: 'VERIFIED', evidenceRefs: ['DOC:purchase-receipt'],
  }],
  fields: [
    { code: 'id', ownerCode: 'jsh_supplier', name: '主键', description: '往来单位主键', dataType: 'bigint',
      semanticType: 'ID', role: 'PRIMARY_KEY', evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:COLUMN:jsh_supplier.id'] },
    { code: 'organ_id', ownerCode: 'purchase_receipt', name: '往来单位', description: '采购入库供应商', dataType: 'bigint',
      semanticType: 'FOREIGN_KEY', role: 'JOIN_KEY', evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:COLUMN:jsh_depot_head.organ_id'] },
    { code: 'oper_time', ownerCode: 'purchase_receipt', name: '业务时间', description: '出入库单据操作时间', dataType: 'datetime',
      semanticType: 'DATETIME', role: 'TIME', evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:COLUMN:jsh_depot_head.oper_time'] },
    { code: 'all_price', ownerCode: 'purchase_receipt', name: '采购金额', description: '采购入库明细金额', dataType: 'decimal(24,6)',
      semanticType: 'AMOUNT', role: 'MEASURE', evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:COLUMN:jsh_depot_item.all_price'] },
  ],
  relations: [{
    code: 'purchase_receipt_supplier', name: '采购入库关联供应商', description: '采购入库通过 organ_id 关联往来单位。',
    sourceCode: 'purchase_receipt', sourceFieldCode: 'organ_id', targetCode: 'jsh_supplier', targetFieldCode: 'id',
    cardinality: 'MANY_TO_ONE', evidenceStatus: 'VERIFIED', evidenceRefs: ['GH:MAPPER:purchaseSupplier'],
  }],
  dimensions: [{
    code: 'supplier_role', name: '往来单位角色', description: '客户、供应商或会员角色。',
    targetCode: 'jsh_supplier', targetFieldCode: 'type', evidenceStatus: 'VERIFIED', evidenceRefs: ['DOC:partner-role'],
  }],
  metrics: [{
    code: 'purchase_amount', name: '采购金额', description: '采购入库明细金额合计。', eventCode: 'purchase_receipt',
    measureFieldCode: 'all_price', formula: 'SUM(all_price)', filter: "sub_type = '采购'", timeFieldCode: 'oper_time',
    evidenceStatus: 'VERIFIED', evidenceRefs: ['GH:MAPPER:purchaseAmount'],
  }],
  hierarchies: [{
    code: 'organization_tree', name: '组织机构层级', description: '通过 parent_id 表达递归结构。', ownerCode: 'jsh_supplier',
    attributeCode: 'id', levels: [{ code: 'organization', name: '组织机构', depth: 1 }], members: [],
    evidenceStatus: 'VERIFIED', evidenceRefs: ['DB:COLUMN:jsh_organization.parent_id'],
  }],
  ruleCandidates: [{
    code: 'negative_stock_control', name: '负库存控制', description: '库存扣减受负库存配置约束。',
    targetType: 'EVENT', targetCode: 'purchase_receipt', structureStatus: 'NEEDS_STRUCTURE',
    evidenceStatus: 'VERIFIED', evidenceRefs: ['GH:SERVICE:negativeStock'],
  }],
  aliases: [
    { code: 'supplier_alias_customer', text: '客户', targetType: 'ENTITY', targetCode: 'jsh_supplier',
      evidenceStatus: 'VERIFIED', evidenceRefs: ['DOC:partner-role'] },
    { code: 'supplier_alias_vendor', text: '供应商', targetType: 'ENTITY', targetCode: 'jsh_supplier',
      evidenceStatus: 'VERIFIED', evidenceRefs: ['DOC:partner-role'] },
  ],
  timeRules: [{
    code: 'depot_oper_time', text: '出入库活动以 oper_time 作为业务时间。', targetCode: 'purchase_receipt',
    targetFieldCode: 'oper_time', granularity: 'DAY', evidenceStatus: 'VERIFIED',
    evidenceRefs: ['DB:COLUMN:jsh_depot_head.oper_time'],
  }],
  technicalAssets: [],
  pendingAssets: [{ code: 'biz_extension', name: 'biz_extension', reason: '只有部署表，缺少业务定义。',
    evidenceRefs: ['DB:TABLE:biz_extension'] }],
  exclusions: [{ code: 'receivable_debt', name: '应收欠款', reason: '源码字段在部署数据库不存在。',
    decision: '以部署结构为准', evidenceRefs: ['GH:COLUMN:debt', 'DB:TABLE:jsh_depot_head'] }],
  evidenceBindings: [],
};

function frozenArtifact(): ModelingDocumentArtifact {
  const semanticPayload = { data: structuredClone(payload), sha256: modelingDocumentSemanticPayloadSha256(payload) };
  const artifact = {
    artifactId: 'artifact-guanyijia-v1', revision: 1, projectId: 'guanyijia_erp', documentCode: 'guanyijia_model_v1',
    title: '管伊佳 ERP 标准建模资料', origin: 'STANDARDIZATION' as const,
    sourceBatch: { batchId: 'guanyijia-r1', fingerprint: 'fingerprint-r1', snapshotIds: ['mysql-r1', 'github-r1', 'docs-r1'] },
    sections: {
      OVERVIEW: '完整物理资产登记。', GOAL: '统一采购与库存语义。', OBJECT: '| 编码 | 名称 |\n| --- | --- |\n| jsh_supplier | 往来单位 |',
      ACTIVITY: '| 编码 | 名称 |\n| --- | --- |\n| purchase_receipt | 采购入库 |', FIELD: '包含字段、维度、层级、同义词和时间语义。',
      RELATION: '采购入库关联往来单位。', METRIC: '包含指标与只读规则候选。', QUESTION: '采购金额是多少？',
      UNRESOLVED: '扩展表待归类；应收欠款已排除。',
    },
    assertions: [], semanticPayload,
    review: undefined, generation: undefined, approval: undefined,
    validation: { errors: [], warnings: [], gaps: [] }, status: 'FROZEN' as const, audit: [],
    createdAt: '2026-08-13T00:00:00.000Z', updatedAt: '2026-08-13T00:00:00.000Z',
  };
  const content = renderStandardModelingMarkdown(artifact);
  return {
    ...artifact,
    markdown: { fileName: 'guanyijia-v1.md', content, sha256: sha256HexSync(content), byteLength: new TextEncoder().encode(content).byteLength },
  };
}

test('冻结语义载荷一次投影到五个业务页且不制造可执行规则或节假日', () => {
  const artifact = frozenArtifact();
  const result = projectModelingDocument(artifact, null, defaultProjectionContext(artifact));
  const workspace = result.materializedData.workspaceData as Record<string, unknown[]>;
  const metricData = result.materializedData.metricData as Record<string, unknown[]>;

  assert.equal(workspace.entities.length, 1);
  assert.equal(workspace.events.length, 1);
  assert.equal(workspace.semanticRelations.length, 1);
  assert.equal(workspace.entityHierarchies.length, 1);
  assert.equal(workspace.objectValues.length, 0);
  assert.equal(workspace.aliases.length, 2);
  assert.equal(workspace.rules.length, 1);
  assert.equal(workspace.calendars.length, 0);
  assert.equal(workspace.holidayCalendars.length, 0);
  assert.equal(workspace.businessRules.length, 0);
  assert.equal(metricData.metrics.length, 1);
  assert.equal(metricData.rules.length, 0);
  assert.equal(metricData.ruleCandidates.length, 1);
  assert.deepEqual(result.exclusions.map((item) => item.code), ['receivable_debt']);

  const sidecar = result.materializedData.semanticSidecar;
  assert.equal(sidecar?.schemaVersion, 2);
  assert.ok(sidecar && sidecar.schemaVersion === 2);
  const kinds = new Set(sidecar.objectEvidence.map((item) => item.objectKind));
  for (const kind of ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'HIERARCHY', 'RULE', 'ALIAS', 'TIME_RULE']) {
    assert.ok(kinds.has(kind as never), `缺少 ${kind} 的证据绑定`);
  }
});

test('语义载荷任一字节变化都会被完整性门禁拒绝', () => {
  const artifact = frozenArtifact();
  artifact.semanticPayload!.data.metrics[0].formula = 'SUM(fake_amount)';
  assert.throws(() => projectModelingDocument(artifact, null, defaultProjectionContext(artifact)), /语义载荷.*不一致/);
});
