import type {
  CatalogEvidenceSidecarV2, MaterializedCollaborationData,
} from '../collaboration/types.ts';
import { deriveDraftObjectChanges } from '../collaboration/draft-change-derivation.ts';
import { compileModelingDocument } from '../modeling-document-bridge/compile-modeling-document.ts';
import {
  assertModelingDocumentIntegrity,
  modelingDocumentSemanticPayloadSha256 as payloadSha256,
} from '../modeling-document-bridge/standard-markdown.ts';
import type {
  ModelingDocumentArtifact, ModelingDocumentSemanticPayload,
} from '../modeling-document-bridge/types.ts';

export type { ModelingDocumentSemanticPayload } from '../modeling-document-bridge/types.ts';
export const modelingDocumentSemanticPayloadSha256 = payloadSha256;

export type ProjectionContext = {
  systemCode: string;
  modelSpaceId: string;
  datasourceName: string;
  schemaName: string;
  ownerName: string;
};

export function defaultProjectionContext(artifact: ModelingDocumentArtifact): ProjectionContext {
  return {
    systemCode: artifact.projectId,
    modelSpaceId: artifact.projectId,
    datasourceName: artifact.sourceBatch?.snapshotIds[0] ?? 'standardization-source',
    schemaName: 'standardization',
    ownerName: 'AI 建模',
  };
}

const clone = <T,>(value: T): T => structuredClone(value);

function stableId(value: string) {
  let result = 2166136261;
  for (const char of value) {
    result ^= char.charCodeAt(0);
    result = Math.imul(result, 16777619);
  }
  return Math.abs(result) || 1;
}

function emptyData(): MaterializedCollaborationData {
  return {
    workspaceData: {
      calendars: [], rules: [], holidayCalendars: [], holidayPeriods: [], entities: [], events: [],
      entityHierarchies: [], objectValues: [], semanticRelations: [], schemaMappings: [], fieldMappings: [],
      metrics: [], aliases: [], terms: [], unknownTerms: [], metricTargets: [], businessRules: [], ruleLinks: [],
    },
    metricData: { entities: [], events: [], relations: [], metrics: [], rules: [], targets: [], ruleCandidates: [] },
    workbenchState: {},
  };
}

function fieldType(value: string) {
  const upper = value.toUpperCase();
  if (/INT|DECIMAL|NUMERIC|DOUBLE|FLOAT/.test(upper)) return 'NUMBER';
  if (/DATE|TIME/.test(upper)) return 'DATETIME';
  if (/BOOL|BIT/.test(upper)) return 'BOOLEAN';
  return 'TEXT';
}

function buildSidecar(
  artifact: ModelingDocumentArtifact,
  payload: ModelingDocumentSemanticPayload,
  projectionContext: ProjectionContext,
): CatalogEvidenceSidecarV2 {
  const evidenceContext = payload.evidenceContext;
  const refs = [...new Set([
    ...payload.entities.flatMap((item) => item.evidenceRefs),
    ...payload.events.flatMap((item) => item.evidenceRefs),
    ...payload.fields.flatMap((item) => item.evidenceRefs),
    ...payload.relations.flatMap((item) => item.evidenceRefs),
    ...payload.dimensions.flatMap((item) => item.evidenceRefs),
    ...payload.metrics.flatMap((item) => item.evidenceRefs),
    ...payload.hierarchies.flatMap((item) => item.evidenceRefs),
    ...payload.ruleCandidates.flatMap((item) => item.evidenceRefs),
    ...payload.aliases.flatMap((item) => item.evidenceRefs),
    ...payload.timeRules.flatMap((item) => item.evidenceRefs),
    ...payload.technicalAssets.flatMap((item) => item.evidenceRefs),
    ...payload.pendingAssets.flatMap((item) => item.evidenceRefs),
    ...payload.exclusions.flatMap((item) => item.evidenceRefs),
  ])].sort();
  const provided = new Map(payload.evidenceBindings.map((item) => [
    `${item.objectKind}:${item.ownerCode ?? ''}:${item.objectCode}`, clone(item),
  ]));
  const claims = (evidenceContext?.claims ?? []) as CatalogEvidenceSidecarV2['claims'];
  const bind = (
    objectKind: CatalogEvidenceSidecarV2['objectEvidence'][number]['objectKind'],
    objectCode: string,
    evidenceStatus: 'VERIFIED' | 'NEEDS_CONFIRMATION',
    evidenceRefs: string[],
    ownerCode?: string,
  ) => {
    const key = `${objectKind}:${ownerCode ?? ''}:${objectCode}`;
    const explicit = provided.get(key);
    if (explicit) return explicit;
    const supportClaimIds = claims.filter((claim) => claim.evidenceRefs.some((ref) => evidenceRefs.includes(ref)))
      .map((claim) => claim.claimId);
    const verified = evidenceStatus === 'VERIFIED' && supportClaimIds.length > 0;
    return {
      objectKind, objectCode, ...(ownerCode ? { ownerCode } : {}),
      status: verified ? 'VERIFIED' as const : 'NEEDS_CONFIRMATION' as const,
      evidenceRefs: verified ? [...evidenceRefs] : [],
      supportClaimIds: verified ? supportClaimIds : [],
    };
  };
  const objectEvidence = [
    ...payload.entities.map((item) => bind('ENTITY', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.events.map((item) => bind('EVENT', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.fields.map((item) => bind('FIELD', item.code, item.evidenceStatus, item.evidenceRefs, item.ownerCode)),
    ...payload.relations.map((item) => bind('RELATION', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.dimensions.map((item) => bind('DIMENSION', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.metrics.map((item) => bind('METRIC', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.hierarchies.map((item) => bind('HIERARCHY', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.ruleCandidates.map((item) => bind('RULE', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.aliases.map((item) => bind('ALIAS', item.code, item.evidenceStatus, item.evidenceRefs)),
    ...payload.timeRules.map((item) => bind('TIME_RULE', item.code, item.evidenceStatus, item.evidenceRefs)),
  ];
  const sources = evidenceContext?.sources as CatalogEvidenceSidecarV2['sources'] | undefined;
  return {
    schemaVersion: 2,
    systemCode: projectionContext.systemCode,
    sourceFingerprint: artifact.sourceBatch?.fingerprint ?? artifact.markdown.sha256,
    dimensions: payload.dimensions.map((item) => ({
      id: item.code, name: item.name, description: item.description,
      target: `${item.targetCode}${item.targetFieldCode ? `.${item.targetFieldCode}` : ''}`,
      targetTableCode: item.targetCode, targetFieldCode: item.targetFieldCode,
      evidenceIds: [...item.evidenceRefs],
    })),
    ruleCandidates: clone(payload.ruleCandidates) as unknown as Array<Record<string, unknown>>,
    hierarchyDefinitions: payload.hierarchies.map((item) => ({
      id: item.code, name: item.name, description: item.description,
      ownerTableCode: item.ownerCode, attributeCode: item.attributeCode,
      levels: clone(item.levels), members: clone(item.members), evidenceIds: [...item.evidenceRefs],
    })),
    generationNotes: [
      { noteId: 'modeling-document', title: '冻结建模文档', detail: `${artifact.documentCode} r${artifact.revision}` },
      { noteId: 'pending-assets', title: '待归类资产', detail: `${payload.pendingAssets.length} 项未被强行建模` },
      { noteId: 'exclusions', title: '排除项', detail: `${payload.exclusions.length} 项按审阅决定排除` },
    ],
    compatibilityNotes: ['正式对象只来自冻结 Markdown 的签名语义载荷。', `${payload.technicalAssets.length} 张表归为技术支撑资产。`],
    batch: {
      batchId: artifact.sourceBatch?.batchId ?? `artifact:${artifact.artifactId}`,
      revision: artifact.revision,
      frozenAt: artifact.updatedAt,
      fingerprint: artifact.sourceBatch?.fingerprint ?? artifact.markdown.sha256,
      coverage: `${sources?.length ?? artifact.sourceBatch?.snapshotIds.length ?? 0} 个冻结来源`,
      snapshotIds: [...(artifact.sourceBatch?.snapshotIds ?? [])],
    },
    sources: sources ?? (artifact.sourceBatch?.snapshotIds ?? []).map((snapshotId) => ({
      snapshotId, connectionId: snapshotId, displayName: snapshotId, connectorType: 'SNAPSHOT', versionRef: snapshotId,
      role: '冻结建模输入', authority: 'PRIMARY' as const, summary: '标准建模文档冻结来源', objectCounts: {},
      evidenceIds: refs, status: 'READY' as const,
    })),
    evidence: evidenceContext?.evidence ?? refs.map((evidenceId) => ({ evidenceId })),
    locators: evidenceContext?.locators ?? {},
    claims,
    findings: (evidenceContext?.findings ?? []) as CatalogEvidenceSidecarV2['findings'],
    objectEvidence,
  };
}

export function projectModelingDocument(
  artifact: ModelingDocumentArtifact,
  base: MaterializedCollaborationData | null,
  context: ProjectionContext,
) {
  if (artifact.status !== 'FROZEN') throw new Error('只有冻结文档可以投影正式模型');
  assertModelingDocumentIntegrity(artifact);
  const envelope = artifact.semanticPayload;
  if (!envelope) throw new Error('冻结文档缺少签名语义载荷');
  if (payloadSha256(envelope.data) !== envelope.sha256) throw new Error('Artifact语义载荷校验和不一致');
  if (envelope.data.projectId !== artifact.projectId) throw new Error('语义载荷项目与冻结文档不一致');

  const payload = envelope.data;
  const materializedData = clone(base ?? emptyData());
  const workspace = materializedData.workspaceData;
  const metricData = materializedData.metricData;
  const tableItems = [...payload.entities.map((item) => ({ ...item, kind: 'ENTITY' as const })),
    ...payload.events.map((item) => ({ ...item, kind: 'EVENT' as const }))];
  const tableIndex = new Map(tableItems.map((item) => [item.code, item]));
  const fieldIndex = new Map(payload.fields.map((item) => [`${item.ownerCode}.${item.code}`, item]));

  const semanticTables = tableItems.map((table) => {
    const attributes = payload.fields.filter((field) => field.ownerCode === table.code).map((field) => ({
      id: stableId(`FIELD:${field.ownerCode}.${field.code}`), attributeCode: field.code, attributeName: field.name,
      dataType: field.dataType, semanticType: field.semanticType || fieldType(field.dataType),
      isFilterable: true, isGroupable: field.role === 'DIMENSION' || field.role === 'JOIN_KEY', isDisplay: true,
      isPrimaryTime: field.role === 'TIME', isMetric: field.role === 'MEASURE',
      ...(field.role === 'MEASURE' ? { aggregateOperator: 'SUM' as const } : {}),
    }));
    return {
      id: stableId(`${table.kind}:${table.code}`),
      ...(table.kind === 'ENTITY' ? { entityCode: table.code, entityName: table.name } : { eventCode: table.code, eventName: table.name }),
      description: table.description, owner: context.ownerName, status: 'ACTIVE', attributes,
      uniqueIdentifierAttributeId: attributes.find((item) => payload.fields.find((field) => field.ownerCode === table.code
        && field.code === item.attributeCode)?.role === 'PRIMARY_KEY')?.id,
      ...(table.kind === 'EVENT' ? { defaultTimeAttributeId: attributes.find((item) => payload.fields.find((field) => field.ownerCode === table.code
        && field.code === item.attributeCode)?.role === 'TIME')?.id } : {}),
    };
  });
  const byCode = new Map(semanticTables.map((item) => [String('entityCode' in item ? item.entityCode : item.eventCode), item]));
  const relations = payload.relations.flatMap((relation) => {
    const source = byCode.get(relation.sourceCode); const target = byCode.get(relation.targetCode);
    if (!source || !target) return [];
    const sourceKind = tableIndex.get(relation.sourceCode)!.kind; const targetKind = tableIndex.get(relation.targetCode)!.kind;
    return [{
      id: stableId(`RELATION:${relation.code}`), relationCode: relation.code, relationName: relation.name,
      relationType: sourceKind === 'EVENT' && targetKind === 'ENTITY' ? 'EVENT_ENTITY'
        : sourceKind === 'ENTITY' && targetKind === 'ENTITY' ? 'ENTITY_ENTITY' : 'EVENT_EVENT',
      sourceType: sourceKind, sourceId: source.id, targetType: targetKind, targetId: target.id,
      sourceAttributeId: relation.sourceFieldCode ? stableId(`FIELD:${relation.sourceCode}.${relation.sourceFieldCode}`) : undefined,
      targetAttributeId: relation.targetFieldCode ? stableId(`FIELD:${relation.targetCode}.${relation.targetFieldCode}`) : undefined,
      relationRole: 'REFERENCE', cardinality: relation.cardinality, direction: 'FORWARD', priority: 1,
      joinType: 'LEFT', required: false, status: 'ACTIVE',
    }];
  });
  const metrics = payload.metrics.flatMap((metric) => {
    const event = byCode.get(metric.eventCode); const eventDef = tableIndex.get(metric.eventCode);
    if (!event || eventDef?.kind !== 'EVENT') return [];
    const measure = fieldIndex.get(`${metric.eventCode}.${metric.measureFieldCode}`);
    return [{
      id: stableId(`METRIC:${metric.code}`), metricCode: metric.code, metricName: metric.name, metricType: 'COMPOSITE',
      description: metric.description, unit: metric.unit, owner: context.ownerName, version: 1, status: 'PUBLISHED',
      eventId: event.id, eventCode: metric.eventCode, eventName: eventDef.name,
      baseAttributeId: measure ? stableId(`FIELD:${metric.eventCode}.${measure.code}`) : undefined,
      baseAttributeCode: metric.measureFieldCode, formula: metric.formula,
      editability: 'READ_ONLY', compatibilityReason: `口径过滤：${metric.filter}；时间字段：${metric.timeFieldCode}`, ruleIds: [],
    }];
  });
  const hierarchies = payload.hierarchies.flatMap((hierarchy) => hierarchy.levels.map((level) => ({
    id: stableId(`HIERARCHY_LEVEL:${hierarchy.code}:${level.code}`),
    objectType: tableIndex.get(hierarchy.ownerCode)?.kind ?? 'ENTITY', objectId: stableId(`${tableIndex.get(hierarchy.ownerCode)?.kind ?? 'ENTITY'}:${hierarchy.ownerCode}`),
    attributeId: stableId(`FIELD:${hierarchy.ownerCode}.${hierarchy.attributeCode}`), hierarchyCode: hierarchy.code,
    hierarchyName: hierarchy.name, levelCode: level.code, levelName: level.name, levelDepth: level.depth,
    ...(level.parentCode ? { parentLevelId: stableId(`HIERARCHY_LEVEL:${hierarchy.code}:${level.parentCode}`) } : {}), status: 'ACTIVE',
  })));
  const values = payload.hierarchies.flatMap((hierarchy) => hierarchy.members.map((member, index) => ({
    id: stableId(`HIERARCHY_MEMBER:${hierarchy.code}:${member.code}`), objectType: tableIndex.get(hierarchy.ownerCode)?.kind ?? 'ENTITY',
    objectId: stableId(`${tableIndex.get(hierarchy.ownerCode)?.kind ?? 'ENTITY'}:${hierarchy.ownerCode}`),
    attributeId: stableId(`FIELD:${hierarchy.ownerCode}.${hierarchy.attributeCode}`),
    hierarchyLevelId: stableId(`HIERARCHY_LEVEL:${hierarchy.code}:${member.levelCode}`), businessName: member.name,
    physicalCode: member.code, ...(member.parentCode ? { parentValueId: stableId(`HIERARCHY_MEMBER:${hierarchy.code}:${member.parentCode}`) } : {}),
    sortOrder: index + 1, sourceType: 'PHYSICAL_SYNC', sourceKey: member.code, status: 'ACTIVE',
  })));
  const aliases = payload.aliases.flatMap((alias) => {
    const target = alias.targetType === 'FIELD' ? fieldIndex.get(`${alias.ownerCode}.${alias.targetCode}`) : undefined;
    const objectTarget = alias.targetType === 'ENTITY' || alias.targetType === 'EVENT' ? byCode.get(alias.targetCode) : undefined;
    const dimension = alias.targetType === 'DIMENSION' ? payload.dimensions.find((item) => item.code === alias.targetCode) : undefined;
    const metric = alias.targetType === 'METRIC' ? payload.metrics.find((item) => item.code === alias.targetCode) : undefined;
    const rule = alias.targetType === 'RULE' ? payload.ruleCandidates.find((item) => item.code === alias.targetCode) : undefined;
    const targetId = objectTarget?.id ?? (target ? stableId(`FIELD:${alias.ownerCode}.${alias.targetCode}`) : stableId(`${alias.targetType}:${alias.targetCode}`));
    const targetName = objectTarget ? ('entityName' in objectTarget ? objectTarget.entityName : objectTarget.eventName)
      : target?.name ?? dimension?.name ?? metric?.name ?? rule?.name;
    if (!targetName) return [];
    return [{ id: stableId(`ALIAS:${alias.code}`), aliasText: alias.text, targetType: alias.targetType === 'FIELD' ? 'ATTRIBUTE' : alias.targetType,
      targetId, targetCode: alias.targetCode, targetName, priority: 100, source: 'IMPORT', isConfirmed: true,
      conflictStatus: 'NONE', sourceObjectCode: alias.code }];
  });
  const timeRules = payload.timeRules.map((rule, index) => ({
    id: stableId(`TIME_RULE:${rule.code}`), ruleCode: rule.code, ruleText: rule.text, calendarCode: 'SYSTEM_NATURAL_DATE',
    granularity: rule.granularity, rangeType: 'CURRENT', offsetValue: 0, priority: index + 1,
    participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE',
  }));
  const rules = payload.ruleCandidates.map((rule) => {
    const target = byCode.get(rule.targetCode); const targetDef = tableIndex.get(rule.targetCode);
    return {
      id: stableId(`RULE_CANDIDATE:${rule.code}`), ruleCode: rule.code, ruleName: rule.name, description: rule.description,
      targetObjectType: targetDef?.kind, targetObjectId: target?.id, targetObjectName: targetDef?.name,
      evidenceRefs: [...rule.evidenceRefs], status: target ? rule.structureStatus : 'UNRESOLVED_TARGET',
    };
  });
  const entities = semanticTables.filter((item) => 'entityCode' in item);
  const events = semanticTables.filter((item) => 'eventCode' in item);
  const schemaMappings = tableItems.flatMap((item) => [...new Set([...(item.physicalTables ?? []), ...(item.physicalTable ? [item.physicalTable] : [])])]
    .map((physicalTable, index) => ({
      id: stableId(`SCHEMA_MAPPING:${item.code}:${physicalTable}`), objectType: item.kind,
      objectId: stableId(`${item.kind}:${item.code}`), datasourceName: context.datasourceName, schemaName: context.schemaName,
      tableName: physicalTable, tableAlias: index ? `${item.name}（明细）` : item.name,
    })));
  const fieldMappings = payload.fields.map((field) => ({
    id: stableId(`FIELD_MAPPING:${field.ownerCode}.${field.code}`), objectType: tableIndex.get(field.ownerCode)?.kind ?? 'ENTITY',
    objectId: stableId(`${tableIndex.get(field.ownerCode)?.kind ?? 'ENTITY'}:${field.ownerCode}`),
    attributeId: stableId(`FIELD:${field.ownerCode}.${field.code}`), physicalFieldName: field.physicalField ?? field.code,
    physicalDataType: field.dataType, fieldRole: field.role,
  }));
  Object.assign(workspace, {
    calendars: [], rules: timeRules, holidayCalendars: [], holidayPeriods: [], entities, events,
    entityHierarchies: hierarchies, objectValues: values, semanticRelations: relations, schemaMappings, fieldMappings,
    metrics, aliases, terms: [], unknownTerms: [], metricTargets: [], businessRules: [], ruleLinks: [],
  });
  Object.assign(metricData, { entities, events, relations, metrics, rules: [], targets: [], ruleCandidates: rules });
  const semanticSidecar = buildSidecar(artifact, payload, context);
  materializedData.semanticSidecar = semanticSidecar;
  const changes = deriveDraftObjectChanges({
    draftId: `artifact:${artifact.artifactId}`, modelSpaceId: context.modelSpaceId,
    base: clone(base ?? emptyData()), next: materializedData,
  });
  materializedData.workbenchState = {
    modelingDocument: { artifactId: artifact.artifactId, revision: artifact.revision, markdownSha256: artifact.markdown.sha256,
      semanticPayloadSha256: envelope.sha256, sourceBatch: clone(artifact.sourceBatch) },
    proposedChanges: changes, technicalAssets: clone(payload.technicalAssets), pendingAssets: clone(payload.pendingAssets),
    exclusions: clone(payload.exclusions),
  };
  return { compiled: compileModelingDocument(artifact), materializedData, changes, exclusions: clone(payload.exclusions) };
}
