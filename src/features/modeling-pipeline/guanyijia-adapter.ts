import { guanyijiaCandidateModel, guanyijiaEvidenceLocators, createGuanyijiaFindings } from '../ai-modeling/guanyijia-fixture.ts';
import type { CandidateModelSnapshot } from '../ai-modeling/candidate-model.ts';
import type { MaterializedCollaborationData } from '../collaboration/types.ts';
import type {
  CatalogEvidenceSidecarV2, FrozenEvidenceClaim, FrozenReconciliationFinding,
} from '../collaboration/types.ts';
import type { ProjectModelingAdapter } from './types.ts';
import { guanyijiaEvidenceSources } from '../evidence-registry/retail-evidence-registry.ts';

const expected = guanyijiaEvidenceSources.map((source) => source.connectionId);
const requiredForCandidate = ['guanyijia_mysql', 'guanyijia_github'];
const clone = <T>(value: T): T => structuredClone(value);

function stableId(value: string) {
  let result = 2166136261;
  for (const char of value) { result ^= char.charCodeAt(0); result = Math.imul(result, 16777619); }
  return Math.abs(result) || 1;
}

function materializeCandidate(candidate: CandidateModelSnapshot): MaterializedCollaborationData {
  const object = (table: CandidateModelSnapshot['tables'][number]) => ({
    id: stableId(table.code),
    ...(table.kind === 'ENTITY'
      ? { entityCode: table.code, entityName: table.name }
      : { eventCode: table.code, eventName: table.name }),
    description: table.description, owner: 'AI 建模', status: 'ACTIVE',
    attributes: table.fields.map((field) => ({
      id: stableId(`${table.code}.${field.code}`), attributeCode: field.code, attributeName: field.name,
      dataType: field.dataType, semanticType: /amount|price|number|qty|quantity/i.test(field.code) ? 'NUMBER' : 'TEXT',
      isFilterable: true, isGroupable: true, isDisplay: true, isPrimaryTime: /time|date/i.test(field.code),
      isMetric: /amount|price|number|qty|quantity/i.test(field.code),
    })),
  });
  const entities = candidate.entities.map(object);
  const events = candidate.events.map(object);
  const tableIndex = new Map(candidate.tables.map((table) => [table.code, { table, id: stableId(table.code) }]));
  const semanticRelations = candidate.relations.flatMap((relation) => {
    const match = relation.description.match(/^([\w]+)\.([\w]+)\s*→\s*([\w]+)\.([\w]+)/);
    if (!match) return [];
    const source = tableIndex.get(match[1]); const target = tableIndex.get(match[3]);
    if (!source || !target) return [];
    return [{
      id: stableId(relation.code), relationCode: relation.code, relationName: relation.name,
      relationType: source.table.kind === 'EVENT' ? 'EVENT_ENTITY' : 'ENTITY_ENTITY',
      sourceType: source.table.kind, sourceId: source.id, targetType: target.table.kind, targetId: target.id,
      sourceAttributeId: stableId(`${match[1]}.${match[2]}`), targetAttributeId: stableId(`${match[3]}.${match[4]}`),
      relationRole: 'REFERENCE', cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1,
      joinType: 'LEFT', required: false, status: 'ACTIVE',
    }];
  });
  const metrics = candidate.metrics.map((metric) => ({
    id: stableId(metric.code), metricCode: metric.code, metricName: metric.name, metricType: 'COMPOSITE',
    description: metric.description, owner: 'AI 建模', version: 1, status: 'DRAFT', formula: metric.formula,
    eventCode: metric.target, eventName: tableIndex.get(metric.target ?? '')?.table.name, ruleIds: [],
  }));
  const ruleCandidates = candidate.ruleCandidates.map((rule) => {
    const target = tableIndex.get(rule.target ?? '');
    return {
      id: stableId(`RULE_CANDIDATE:${rule.code}`), ruleCode: rule.code, ruleName: rule.name,
      description: rule.description,
      targetObjectType: target?.table.kind,
      targetObjectId: target?.id,
      targetObjectName: target?.table.name,
      evidenceRefs: [...rule.evidenceIds],
      status: target ? 'NEEDS_STRUCTURE' : 'UNRESOLVED_TARGET',
    };
  });
  const workspaceData = {
    calendars: [], rules: [], holidayCalendars: [], holidayPeriods: [], entities, events,
    entityHierarchies: [], objectValues: [], semanticRelations,
    schemaMappings: candidate.tables.map((table) => ({
      id: stableId(`mapping:${table.code}`), objectType: table.kind, objectId: stableId(table.code),
      datasourceName: '管伊佳 MySQL · jsh_erp', schemaName: 'jsh_erp', tableName: table.code, tableAlias: table.name,
    })),
    fieldMappings: candidate.fields.map((field) => ({
      id: stableId(`mapping:${field.ownerTableCode}.${field.code}`),
      objectType: tableIndex.get(field.ownerTableCode)?.table.kind ?? 'ENTITY', objectId: stableId(field.ownerTableCode),
      attributeId: stableId(`${field.ownerTableCode}.${field.code}`), physicalFieldName: field.code,
      physicalDataType: field.dataType, fieldRole: /_id$|^id$/.test(field.code) ? 'JOIN_KEY' : 'TEXT',
    })),
    metrics, aliases: [], terms: [], unknownTerms: [], metricTargets: [], businessRules: [], ruleLinks: [],
  };
  return {
    workspaceData: workspaceData as unknown as Record<string, unknown>,
    metricData: { entities, events, relations: semanticRelations, metrics, rules: [], targets: [], ruleCandidates },
    workbenchState: { systemCode: 'guanyijia_erp', pipelineState: 'APPLIED_TO_DRAFT' },
  };
}

function records<T>(items: T[]): Array<Record<string, unknown>> {
  return clone(items) as unknown as Array<Record<string, unknown>>;
}

function objectEvidence(candidate: CandidateModelSnapshot, available: Set<string>, claims: FrozenEvidenceClaim[]) {
  const bind = (objectKind: CatalogEvidenceSidecarV2['objectEvidence'][number]['objectKind'], objectCode: string, evidenceRefs: string[], status: string, ownerCode?: string) => {
    const validEvidenceRefs = [...new Set(evidenceRefs.filter((id) => available.has(id)))];
    if (!validEvidenceRefs.length && status !== 'NEEDS_CONFIRMATION') throw new Error(`管伊佳对象缺少证据且未明确待确认：${objectKind}:${ownerCode ?? ''}:${objectCode}`);
    const supportClaimIds = claims.filter((claim) => claim.evidenceRefs.some((id) => validEvidenceRefs.includes(id))).map((claim) => claim.claimId);
    return {
      objectKind, objectCode, ...(ownerCode ? { ownerCode } : {}),
      status: validEvidenceRefs.length ? 'VERIFIED' as const : 'NEEDS_CONFIRMATION' as const,
      evidenceRefs: validEvidenceRefs,
      supportClaimIds,
    };
  };
  return [
    ...candidate.entities.map((item) => bind('ENTITY', item.code, item.evidenceIds, item.status)),
    ...candidate.events.map((item) => bind('EVENT', item.code, item.evidenceIds, item.status)),
    ...candidate.fields.map((item) => bind('FIELD', item.code, item.evidenceIds, item.status, item.ownerTableCode)),
    ...candidate.relations.map((item) => bind('RELATION', item.code, item.evidenceIds, item.status)),
    ...candidate.dimensions.map((item) => bind('DIMENSION', item.id, item.evidenceIds, item.status)),
    ...candidate.metrics.map((item) => bind('METRIC', item.code, item.evidenceIds, item.status)),
    ...candidate.hierarchies.map((item) => bind('HIERARCHY', item.id, item.evidenceIds, item.status)),
    ...candidate.ruleCandidates.map((item) => bind('RULE', item.code, item.evidenceIds, item.status)),
  ];
}

function evidenceSidecar(input: Parameters<ProjectModelingAdapter['materialize']>[0]): CatalogEvidenceSidecarV2 {
  const available = new Set(input.sources.flatMap((source) => source.evidenceIds));
  const normalized = guanyijiaModelingAdapter.normalize(input);
  const claims: FrozenEvidenceClaim[] = normalized.claims.map((claim) => ({
    claimId: claim.claimId,
    sourceId: claim.sourceId,
    assertion: claim.assertion,
    authority: claim.authority,
    evidenceRefs: [...claim.evidenceRefs],
    ...(claim.upstreamClaimIds ? { upstreamClaimIds: [...claim.upstreamClaimIds] } : {}),
  }));
  const findings: FrozenReconciliationFinding[] = input.findings.map((finding) => {
    const option = finding.decision
      ? finding.options.find((item) => item.id === finding.decision!.resolutionId)
      : undefined;
    return {
      findingId: finding.findingId,
      title: finding.title,
      status: finding.status,
      claimIds: [...finding.claimIds],
      agreement: finding.agreement,
      difference: finding.difference,
      decision: option?.label,
      affectedObjectCodes: [...finding.affectedObjectCodes],
      ...(finding.severity === 'BLOCKER' ? { originStatus: 'CONFLICT' as const } : {}),
      ...(finding.decision ? {
        resolution: {
          decidedByUserId: finding.decision.reviewer,
          decidedBy: finding.decision.reviewer,
          decidedAt: finding.decision.decidedAt,
          reason: finding.decision.reason,
        },
      } : {}),
    };
  });
  const locators = Object.fromEntries(Object.entries(normalized.locators)
    .filter(([evidenceId]) => available.has(evidenceId))
    .map(([evidenceId, locator]) => [evidenceId, clone(locator) as unknown as Record<string, unknown>]));
  return {
    schemaVersion: 2,
    systemCode: 'guanyijia_erp',
    sourceFingerprint: input.batch.fingerprint,
    dimensions: records(input.candidate.dimensions),
    ruleCandidates: records(input.candidate.ruleCandidates),
    hierarchyDefinitions: records(input.candidate.hierarchies),
    generationNotes: records([
      { noteId: 'guanyijia-database', title: '部署结构为物理真相', detail: '表、字段、索引和类型以 MySQL 证据快照为准。' },
      { noteId: 'guanyijia-code', title: '代码补充业务语义', detail: 'Mapper、Service 和常量用于解释关联、事件和状态。' },
      { noteId: 'guanyijia-pending', title: '扩展资产保持待归类', detail: `${guanyijiaCandidateModel.pendingAssets.length} 张仅数据库出现的表不按名称猜测业务含义。` },
    ]),
    compatibilityNotes: ['候选模型已使用统一来源、互证、草稿、审核和发布状态机。'],
    batch: {
      batchId: input.batch.batchId,
      revision: input.batch.revision,
      frozenAt: input.batch.frozenAt,
      fingerprint: input.batch.fingerprint,
      coverage: 'MySQL 部署结构与 GitHub 固定 Commit 双来源覆盖',
      snapshotIds: [...input.batch.snapshotIds],
    },
    sources: guanyijiaEvidenceSources.map((registered) => {
      const source = input.sources.find((item) => item.connectionId === registered.connectionId);
      if (!source) {
        const snapshotId = `${registered.connectionId}-pending`;
        return {
          snapshotId, connectionId: registered.connectionId, displayName: registered.displayName,
          connectorType: registered.connectorType, versionRef: 'NEEDS_CONFIRMATION', role: registered.role,
          authority: registered.connectionId === 'guanyijia_semantica' ? 'DERIVED' as const : 'PRIMARY' as const,
          summary: registered.summary, objectCounts: {}, evidenceIds: [], status: 'NEEDS_CONFIRMATION' as const,
          ...(registered.upstreamConnectionIds ? {
            upstreamSnapshotIds: registered.upstreamConnectionIds.map((id) => `${id}-pending`),
          } : {}),
        };
      }
      return {
        snapshotId: source.snapshotId,
        sourceSnapshotIdentity: source.sourceSnapshotIdentity,
        fingerprint: source.fingerprint,
        manifestRef: source.manifestRef,
        connectionId: source.connectionId,
        displayName: source.displayName,
        connectorType: source.connectorTypeId,
        versionRef: source.versionRef,
        role: registered.role,
        authority: source.authority,
        summary: source.summary,
        objectCounts: clone(source.objectCounts),
        upstreamSnapshotIds: source.upstreamSnapshotIds ? [...source.upstreamSnapshotIds] : undefined,
        evidenceIds: [...source.evidenceIds],
        status: 'READY' as const,
      };
    }),
    evidence: [...available].sort().map((evidenceId) => ({
      evidenceId,
      sourceId: input.sources.find((source) => source.evidenceIds.includes(evidenceId))?.connectionId,
      locator: locators[evidenceId],
    })),
    locators,
    claims,
    findings,
    objectEvidence: objectEvidence(input.candidate, available, claims),
  };
}

export const guanyijiaModelingAdapter: ProjectModelingAdapter = {
  projectId: 'guanyijia_erp', workspaceId: 'erp_data_governance', expectedConnectionIds: expected,
  normalize(input) {
    const evidence = new Set(input.sources.flatMap((item) => item.evidenceIds));
    const claims = input.sourceWorkspace.claims
      .filter((item) => input.sources.some((source) => source.snapshotId === item.snapshotId))
      .map((item) => ({
        claimId: item.claimId, sourceId: input.sources.find((source) => source.snapshotId === item.snapshotId)!.connectionId,
        assertion: `支持证据 ${String(item.value)}`, authority: item.authority,
        evidenceRefs: item.evidenceRefs.filter((id) => evidence.has(id)), upstreamClaimIds: item.upstreamClaimIds,
      }));
    return { claims, locators: Object.fromEntries(Object.entries(guanyijiaEvidenceLocators).filter(([id]) => evidence.has(id))) };
  },
  reconcile(input) {
    const present = new Set(input.sources.map((item) => item.connectionId));
    const legacyToConnection: Record<string, string> = {
      'guanyijia-mysql-jsh-erp-v1': 'guanyijia_mysql', 'guanyijia-github-jsherp-v1': 'guanyijia_github',
    };
    return createGuanyijiaFindings().flatMap((value) => {
      const sourceIds = value.sourceIds.map((id) => legacyToConnection[id] ?? id).filter((id) => present.has(id));
      if (!sourceIds.length) return [];
      const sourceCount = new Set(sourceIds).size;
      return [{
        findingId: value.findingId, title: value.title,
        status: sourceCount === 1 ? 'SINGLE_SOURCE' as const : value.severity === 'BLOCKER' ? 'CONFLICT' as const : 'CONSISTENT' as const,
        severity: sourceCount === 1 ? 'WEAK' as const : value.severity,
        claimIds: input.claims.filter((claim) => sourceIds.includes(claim.sourceId)
          && claim.evidenceRefs.some((id) => value.evidenceIds.includes(id))).map((claim) => claim.claimId),
        agreement: value.severity === 'CONSISTENT' ? value.conclusion : undefined,
        difference: value.severity !== 'CONSISTENT' ? value.conclusion : undefined,
        recommendationId: value.recommendationId, options: clone(value.options),
        affectedObjectCodes: [...value.affectedObjects],
      }];
    });
  },
  generate(input) {
    const ids = new Set(input.sources.map((item) => item.connectionId));
    return requiredForCandidate.every((id) => ids.has(id)) ? clone(guanyijiaCandidateModel) : null;
  },
  materialize(input) {
    const result = materializeCandidate(input.candidate);
    result.workbenchState = { systemCode: 'guanyijia_erp', pipelineState: 'APPLIED_TO_DRAFT', batchId: input.batch.batchId };
    result.semanticSidecar = evidenceSidecar(input);
    return result;
  },
};
