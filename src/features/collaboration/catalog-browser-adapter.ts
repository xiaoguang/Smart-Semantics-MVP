import type {
  ObjectAttribute, SemanticEntity, SemanticEvent, SemanticMetric, SemanticRelation,
  TermAlias, TimeRule,
} from '../../types/index.ts';
import type { BusinessRule, ReadOnlyRuleCandidate } from '../../pages/metric2/mockData.ts';
import type { CollaborationCatalog } from './types.ts';
import {
  modelBrowserRefKey,
  type ModelBrowserObject,
  type ModelBrowserObjectKind,
  type ModelBrowserObjectRef,
  type ModelBrowserView,
} from '../model-browser/types.ts';
import { evidencePackagesFor } from '../model-projects/domain-registry.ts';

export { modelBrowserRefKey } from '../model-browser/types.ts';

type CatalogTable = SemanticEntity | SemanticEvent;
type SidecarDimension = {
  id: string; name: string; description?: string; target?: string;
  targetTableCode?: string; targetFieldCode?: string; evidenceIds?: string[];
};
type SidecarHierarchy = {
  id: string; name: string; ownerTableCode: string; attributeCode: string;
  levels?: Array<{ name: string }>; members?: unknown[]; evidenceIds?: string[];
};

const array = <T>(value: unknown) => Array.isArray(value) ? value as T[] : [];

export function assertCatalogEvidenceSidecar(sidecar: Extract<NonNullable<CollaborationCatalog['data']['semanticSidecar']>, { schemaVersion: 2 }>) {
  const sourceIds = new Set(sidecar.sources.map((source) => source.connectionId));
  const evidenceIds = new Set(sidecar.evidence.map((item) => String(item.evidence_id ?? item.evidenceId)));
  const claims = new Map(sidecar.claims.map((claim) => [claim.claimId, claim]));
  for (const claim of sidecar.claims) {
    if (!sourceIds.has(claim.sourceId)) throw new Error(`冻结Claim引用缺失来源：${claim.claimId}`);
    if (claim.evidenceRefs.some((id) => !evidenceIds.has(id))) throw new Error(`冻结Claim引用缺失证据：${claim.claimId}`);
    for (const upstreamClaimId of claim.upstreamClaimIds ?? []) {
      const upstream = claims.get(upstreamClaimId);
      if (!upstream) throw new Error(`冻结Claim引用缺失上游Claim：${claim.claimId}`);
      const source = sidecar.sources.find((item) => item.connectionId === claim.sourceId)!;
      const upstreamSource = sidecar.sources.find((item) => item.connectionId === upstream.sourceId)!;
      if (!source.upstreamSnapshotIds?.includes(upstreamSource.snapshotId)) {
        throw new Error(`冻结Claim上游未闭合：${claim.claimId}`);
      }
    }
  }
  return true;
}

export function projectCatalogBrowser(catalog: CollaborationCatalog, options: { strictEvidence?: boolean } = {}): ModelBrowserView {
  const strictEvidence = options.strictEvidence ?? true;
  const workspace = catalog.data.workspaceData;
  const metricData = catalog.data.metricData;
  const entities = array<SemanticEntity>(workspace.entities);
  const events = array<SemanticEvent>(workspace.events);
  const relations = array<SemanticRelation>(workspace.semanticRelations);
  const metrics = array<SemanticMetric>(metricData.metrics ?? workspace.metrics);
  const aliases = array<TermAlias>(workspace.aliases);
  const timeRules = array<TimeRule>(workspace.rules);
  const rules = array<BusinessRule>(metricData.rules ?? workspace.businessRules);
  const ruleCandidates = array<ReadOnlyRuleCandidate>(metricData.ruleCandidates);
  const sidecar = catalog.data.semanticSidecar
    && catalog.data.semanticSidecar.systemCode === catalog.modelSpaceId
    ? catalog.data.semanticSidecar : undefined;
  const sidecarV2 = sidecar?.schemaVersion === 2 ? sidecar : undefined;
  if (sidecarV2) assertCatalogEvidenceSidecar(sidecarV2);
  const pendingEvidence = {
    evidenceIds: [] as string[],
    evidenceStatus: 'NEEDS_CONFIRMATION' as const,
    evidenceSupportClaimIds: [] as string[],
  };
  const dimensions = array<SidecarDimension>(sidecar?.dimensions);
  const hierarchies = array<SidecarHierarchy>(sidecar?.hierarchyDefinitions);
  const tables: Array<{ kind: 'ENTITY' | 'EVENT'; value: CatalogTable }> = [
    ...entities.map((value) => ({ kind: 'ENTITY' as const, value })),
    ...events.map((value) => ({ kind: 'EVENT' as const, value })),
  ];
  const tableById = new Map(tables.map((item) => [item.value.id, item]));
  const tableByCode = new Map(tables.map((item) => [item.kind === 'ENTITY'
    ? (item.value as SemanticEntity).entityCode : (item.value as SemanticEvent).eventCode, item]));
  const tableCode = (item: typeof tables[number]) => item.kind === 'ENTITY'
    ? (item.value as SemanticEntity).entityCode : (item.value as SemanticEvent).eventCode;
  const tableName = (item: typeof tables[number]) => item.kind === 'ENTITY'
    ? (item.value as SemanticEntity).entityName : (item.value as SemanticEvent).eventName;
  const ref = (kind: ModelBrowserObjectKind, objectId: string, ownerId?: string): ModelBrowserObjectRef => ({
    scope: 'CATALOG', catalogId: catalog.catalogId, kind, objectId, ...(ownerId ? { ownerId } : {}),
  });
  const objects: ModelBrowserObject[] = [];

  tables.forEach((item) => {
    const code = tableCode(item);
    const name = tableName(item);
    const childRefs = item.value.attributes.map((field) => ref('FIELD', field.attributeCode, code));
    objects.push({
      ref: ref(item.kind, code), name, code, semanticKind: item.kind,
      summary: item.value.description, childRefs,
      details: [
        { label: '类型', value: item.kind === 'ENTITY' ? '实体表' : '事件表' },
        { label: '字段', value: `${item.value.attributes.length} 个` },
      ], ...pendingEvidence,
    });
    item.value.attributes.forEach((field: ObjectAttribute) => {
      objects.push({
        ref: ref('FIELD', field.attributeCode, code), parentRef: ref(item.kind, code),
        name: field.attributeName, code: field.attributeCode, semanticKind: 'FIELD',
        summary: `${name}中的${field.attributeName}`, childRefs: [],
        details: [
          { label: '所属对象', value: name, links: [{ label: name, ref: ref(item.kind, code) }] },
          ...(field.unit ? [{ label: '单位', value: field.unit }] : []),
        ],
        technicalDetails: [{ label: '数据类型', value: field.dataType }, { label: '语义类型', value: field.semanticType }],
        ...pendingEvidence,
      });
    });
  });

  relations.forEach((relation) => {
    const source = tableById.get(relation.sourceId);
    const target = tableById.get(relation.targetId);
    if (!source || !target) return;
    const sourceCode = tableCode(source); const targetCode = tableCode(target);
    const sourceField = source.value.attributes.find((field) => field.id === relation.sourceAttributeId);
    const targetField = target.value.attributes.find((field) => field.id === relation.targetAttributeId);
    objects.push({
      ref: ref('RELATION', relation.relationCode), name: relation.relationName, code: relation.relationCode,
      semanticKind: 'RELATION', summary: `${tableName(source)} → ${tableName(target)}`, childRefs: [], ...pendingEvidence,
      details: [
        { label: '起点', value: tableName(source), links: [{ label: tableName(source), ref: ref(source.kind, sourceCode) }, ...(sourceField ? [{ label: sourceField.attributeName, ref: ref('FIELD', sourceField.attributeCode, sourceCode) }] : [])] },
        { label: '终点', value: tableName(target), links: [{ label: tableName(target), ref: ref(target.kind, targetCode) }, ...(targetField ? [{ label: targetField.attributeName, ref: ref('FIELD', targetField.attributeCode, targetCode) }] : [])] },
      ],
      technicalDetails: [{ label: '连接基数', value: relation.cardinality }],
    });
  });

  metrics.forEach((metric) => {
    const event = metric.eventId ? tableById.get(metric.eventId) : undefined;
    const eventCode = event ? tableCode(event) : metric.eventCode;
    const baseField = event?.value.attributes.find((field) => field.id === metric.baseAttributeId || field.attributeCode === metric.baseAttributeCode);
    objects.push({
      ref: ref('METRIC', metric.metricCode), name: metric.metricName, code: metric.metricCode,
      semanticKind: 'METRIC', summary: metric.description, childRefs: [], ...pendingEvidence,
      details: [
        { label: '所属事件', value: metric.eventName, links: event ? [{ label: tableName(event), ref: ref('EVENT', eventCode) }] : [] },
        ...(baseField && event ? [{ label: '依赖字段', value: baseField.attributeName, links: [{ label: baseField.attributeName, ref: ref('FIELD', baseField.attributeCode, eventCode) }] }] : []),
        ...(metric.unit ? [{ label: '单位', value: metric.unit }] : []),
      ],
      technicalDetails: [{ label: '公式', value: metric.formula ?? metric.defaultOperator ?? '待配置' }],
    });
  });

  dimensions.forEach((dimension) => {
    const targetTable = dimension.targetTableCode ? tableByCode.get(dimension.targetTableCode) : undefined;
    const targetField = targetTable?.value.attributes.find((field) => field.attributeCode === dimension.targetFieldCode);
    const links = targetTable
      ? [{ label: targetField?.attributeName ?? tableName(targetTable), ref: targetField
        ? ref('FIELD', targetField.attributeCode, tableCode(targetTable)) : ref(targetTable.kind, tableCode(targetTable)) }]
      : [];
    objects.push({
      ref: ref('DIMENSION', dimension.id), name: dimension.name, code: dimension.id,
      semanticKind: 'DIMENSION', summary: dimension.description, childRefs: [], ...pendingEvidence,
      details: [{ label: '最终映射', value: dimension.target ?? '待补充', links }],
    });
  });

  hierarchies.forEach((hierarchy) => objects.push({
    ref: ref('HIERARCHY', hierarchy.id), name: hierarchy.name, code: hierarchy.id,
    semanticKind: 'DIMENSION', childRefs: [], ...pendingEvidence,
    details: [
      { label: '层级路径', value: (hierarchy.levels ?? []).map((item) => item.name).join(' → ') },
      { label: '成员', value: `${hierarchy.members?.length ?? 0} 个` },
    ],
  }));
  rules.forEach((rule) => objects.push({
    ref: ref('RULE', rule.ruleCode), name: rule.ruleName, code: rule.ruleCode,
    semanticKind: 'RELATION', summary: rule.description, childRefs: [], ...pendingEvidence,
    details: [{ label: '目标对象', value: rule.targetObjectName }],
  }));
  const executableRuleCodes = new Set(rules.map((rule) => rule.ruleCode));
  ruleCandidates.filter((candidate) => !executableRuleCodes.has(candidate.ruleCode)).forEach((candidate) => objects.push({
    ref: ref('RULE', candidate.ruleCode), name: candidate.ruleName, code: candidate.ruleCode,
    summary: candidate.description, childRefs: [], ...pendingEvidence,
    details: [
      { label: '状态', value: candidate.status === 'UNRESOLVED_TARGET' ? '目标待确认' : '待结构化候选' },
      ...(candidate.targetObjectName ? [{ label: '建议目标', value: candidate.targetObjectName }] : []),
    ],
  }));
  aliases.forEach((alias) => objects.push({
    ref: ref('ALIAS', String(alias.id)), name: alias.aliasText, code: alias.sourceObjectCode ?? String(alias.id),
    summary: `${alias.targetName}的同义词`, childRefs: [], ...pendingEvidence,
    details: [{ label: '目标', value: alias.targetName }, { label: '目标类型', value: alias.targetType }],
  }));
  timeRules.forEach((rule) => objects.push({
    ref: ref('TIME_RULE', rule.ruleCode), name: rule.ruleText, code: rule.ruleCode,
    summary: rule.ruleText, childRefs: [], ...pendingEvidence,
    details: [{ label: '日历', value: rule.calendarCode }, { label: '粒度', value: rule.granularity }],
  }));

  const duplicate = new Set<string>();
  objects.forEach((object) => {
    const key = modelBrowserRefKey(object.ref);
    if (duplicate.has(key)) throw new Error(`正式模型对象引用重复：${key}`);
    duplicate.add(key);
  });
  if (sidecarV2) {
    const bindingKey = (kind: string, code: string, ownerCode?: string) => `${kind}:${ownerCode ?? ''}:${code}`;
    const bindings = new Map(sidecarV2.objectEvidence.map((item) => [
      bindingKey(item.objectKind, item.objectCode, item.ownerCode), item,
    ]));
    const visibleKeys = new Set<string>();
    const evidenceIds = new Set(sidecarV2.evidence.map((item) => String(item.evidence_id ?? item.evidenceId)));
    const claims = new Map(sidecarV2.claims.map((claim) => [claim.claimId, claim]));
    objects.forEach((object) => {
      const key = bindingKey(object.ref.kind, object.code, object.ref.ownerId);
      visibleKeys.add(key);
      const binding = bindings.get(key);
      if (!binding) {
        if (strictEvidence) throw new Error(`正式模型对象缺少持久证据状态：${key}`);
        return;
      }
      const hasEvidence = binding.evidenceRefs.length > 0;
      if (hasEvidence === (binding.status === 'NEEDS_CONFIRMATION')) throw new Error(`正式模型对象证据状态不满足 XOR：${key}`);
      if (binding.status === 'VERIFIED' && !binding.supportClaimIds.length) throw new Error(`正式模型对象缺少语义支持 Claim：${key}`);
      if (binding.evidenceRefs.some((evidenceId) => !evidenceIds.has(evidenceId))) throw new Error(`正式模型对象引用批次外证据：${key}`);
      if (binding.supportClaimIds.some((claimId) => !claims.has(claimId))) throw new Error(`正式模型对象引用缺失支持 Claim：${key}`);
      if (binding.evidenceRefs.some((evidenceId) => !binding.supportClaimIds.some((claimId) => claims.get(claimId)?.evidenceRefs.includes(evidenceId)))) {
        throw new Error(`正式模型对象证据不受绑定 Claim 支持：${key}`);
      }
      object.evidenceIds = [...binding.evidenceRefs];
      object.evidenceStatus = binding.status;
      object.evidenceSupportClaimIds = [...binding.supportClaimIds];
    });
    const orphan = [...bindings.keys()].find((key) => !visibleKeys.has(key));
    if (orphan && strictEvidence) throw new Error(`持久证据状态引用不可见对象：${orphan}`);
  }
  const activePackageId = (catalog.data.workbenchState as { evidencePackageId?: string } | null)?.evidencePackageId;
  const sourcePackages = evidencePackagesFor(catalog.modelSpaceId)
    .filter((item) => item.packageId === activePackageId)
    .map((item) => ({
      packageId: item.packageId, displayName: item.displayName, packageType: item.packageType,
      packageVersion: item.packageVersion, status: item.status, active: true,
    }));
  const objectByCode = new Map(objects.map((object) => [object.code, object]));
  const claimById = new Map((sidecarV2?.claims ?? []).map((claim) => [claim.claimId, claim]));
  const sourceById = new Map((sidecarV2?.sources ?? []).map((source) => [source.connectionId, source]));
  return {
    identity: {
      title: `${catalog.catalogVersion} 正式模型`, subtitle: '已发布、不可变',
      immutable: true, catalogId: catalog.catalogId,
    },
    counts: {
      entities: entities.length, events: events.length,
      fields: [...entities, ...events].reduce((count, item) => count + item.attributes.length, 0),
      relations: relations.length, dimensions: dimensions.length, metrics: metrics.length,
      hierarchies: hierarchies.length, rules: rules.length + ruleCandidates.filter((candidate) => !executableRuleCodes.has(candidate.ruleCode)).length,
      aliases: aliases.length, timeRules: timeRules.length,
    },
    objects,
    evidence: sidecar?.evidence ?? [],
    evidenceLocators: structuredClone(sidecarV2?.locators ?? {}),
    generationNotes: sidecar?.generationNotes ?? [],
    coverageNotices: sidecar ? [] : ['该历史版本未保留来源明细；以下仅展示已冻结的正式模型对象。'],
    sourcePackages,
    sourceBatch: sidecarV2 ? {
      batchId: sidecarV2.batch.batchId, revision: sidecarV2.batch.revision,
      frozenAt: sidecarV2.batch.frozenAt, fingerprint: sidecarV2.batch.fingerprint,
      coverage: sidecarV2.batch.coverage,
    } : undefined,
    sourceSnapshots: structuredClone(sidecarV2?.sources ?? []),
    reconciliationFindings: (sidecarV2?.findings ?? []).map((finding) => ({
      findingId: finding.findingId, title: finding.title, status: finding.status,
      claims: finding.claimIds.flatMap((claimId) => {
        const claim = claimById.get(claimId); if (!claim) return [];
        const source = sourceById.get(claim.sourceId);
        return [{ sourceId: claim.sourceId, sourceName: source?.displayName ?? claim.sourceId, assertion: claim.assertion, authority: claim.authority, evidenceRefs: claim.evidenceRefs }];
      }),
      agreement: finding.agreement, difference: finding.difference, decision: finding.decision,
      originStatus: finding.originStatus, resolution: finding.resolution ? structuredClone(finding.resolution) : undefined,
      affectedRefs: finding.affectedObjectCodes.flatMap((code) => objectByCode.get(code)?.ref ?? []),
    })),
  };
}
