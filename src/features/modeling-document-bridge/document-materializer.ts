import type { MaterializedCollaborationData } from '../collaboration/types.ts';
import { deriveDraftObjectChanges } from '../collaboration/draft-change-derivation.ts';
import { adaptPublishedModel } from '../ai-modeling/adapter.ts';
import { semanticFixture } from '../ai-modeling/fixture.ts';
import { projectLinguanWorkspaceData, projectMetric2WorkspaceData } from '../ai-modeling/workspace-store-adapter.ts';
import type { DocumentVersion, ModelVersion } from '../ai-modeling/types.ts';
import { compileModelingDocument } from './compile-modeling-document.ts';
import type { CompiledModelingDocument, ModelingDocumentCandidateItem } from './compile-modeling-document.ts';
import type { ModelingDocumentArtifact } from './types.ts';
import { canonicalModelingJson } from './standard-markdown.ts';
import { buildRetailEvidenceSidecar } from '../collaboration/retail-evidence-fixture.ts';
import { defaultProjectionContext, projectModelingDocument } from '../modeling-document-projector/index.ts';

const clone = <T,>(value: T): T => structuredClone(value);
function stableId(value: string) {
  let result = 2166136261;
  for (const char of value) { result ^= char.charCodeAt(0); result = Math.imul(result, 16777619); }
  return Math.abs(result) || 1;
}

function code(item: ModelingDocumentCandidateItem) {
  return item.metadata?.code ?? `${item.kind.toLowerCase()}_${stableId(item.candidateId)}`;
}

function object(item: ModelingDocumentCandidateItem, objectCode = code(item)) {
  return {
    id: stableId(item.candidateId),
    ...(item.kind === 'ENTITY' ? { entityCode: objectCode, entityName: item.name } : { eventCode: objectCode, eventName: item.name }),
    description: item.summary || '由标准建模文档识别，需在个人草稿中继续确认。',
    owner: 'AI 建模', status: 'ACTIVE', attributes: [],
  };
}

function genericProposal(
  artifact: ModelingDocumentArtifact,
  candidate: CompiledModelingDocument,
  draftId: string,
  base: MaterializedCollaborationData,
) {
  const materializedData = clone(base);
  const workspace = materializedData.workspaceData;
  const entities = Array.isArray(workspace.entities) ? clone(workspace.entities) as Array<Record<string, unknown>> : [];
  const events = Array.isArray(workspace.events) ? clone(workspace.events) as Array<Record<string, unknown>> : [];
  const relations = Array.isArray(workspace.semanticRelations) ? clone(workspace.semanticRelations) as Array<Record<string, unknown>> : [];
  const workspaceMetrics = Array.isArray(workspace.metrics) ? clone(workspace.metrics) as Array<Record<string, unknown>> : [];
  const metricData = materializedData.metricData;
  const metrics = Array.isArray(metricData.metrics) ? clone(metricData.metrics) as Array<Record<string, unknown>> : [];
  const itemCode = (item: ModelingDocumentCandidateItem) => code(item);
  const retailR2SnapshotIds = [
    'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2',
    'retail_sharepoint-snapshot-r2', 'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2',
    'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
  ];
  const artifactSnapshotIds = [...(artifact.sourceBatch?.snapshotIds ?? [])].sort();
  if (artifact.projectId === 'group_retail_ops' && materializedData.semanticSidecar?.schemaVersion === 2
    && JSON.stringify(artifactSnapshotIds) === JSON.stringify([...retailR2SnapshotIds].sort())) {
    const current = materializedData.semanticSidecar;
    materializedData.semanticSidecar = buildRetailEvidenceSidecar({
      systemCode: current.systemCode, sourceFingerprint: current.sourceFingerprint,
      dimensions: current.dimensions, ruleCandidates: current.ruleCandidates,
      hierarchyDefinitions: current.hierarchyDefinitions, generationNotes: current.generationNotes,
      compatibilityNotes: current.compatibilityNotes,
    }, 2);
  }
  materializedData.semanticSidecar ??= {
    schemaVersion: 1, systemCode: artifact.projectId, sourceFingerprint: artifact.sourceBatch?.fingerprint ?? artifact.markdown.sha256,
    dimensions: [], ruleCandidates: [], hierarchyDefinitions: [], evidence: [], generationNotes: [],
    compatibilityNotes: ['标准文档未携带可验证来源 Sidecar；新增对象保持待确认。'],
  };
  const evidenceSidecar = materializedData.semanticSidecar?.schemaVersion === 2 ? materializedData.semanticSidecar : undefined;
  const addEvidenceBinding = (kind: 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC', objectCode: string,
    ownerCode?: string) => {
    if (!evidenceSidecar) return;
    if (evidenceSidecar.objectEvidence.some((binding) => binding.objectKind === kind
      && binding.objectCode === objectCode && binding.ownerCode === ownerCode)) return;
    evidenceSidecar.objectEvidence.push({
      objectKind: kind, objectCode, ...(ownerCode ? { ownerCode } : {}),
      status: 'NEEDS_CONFIRMATION', evidenceRefs: [], supportClaimIds: [],
    });
  };
  candidate.items.forEach((item) => {
    if (item.kind === 'ENTITY') {
      const existing = entities.find((value) => value.entityName === item.name);
      if (existing) existing.description = item.summary;
      else { const value = object(item, itemCode(item)); entities.push(value); addEvidenceBinding('ENTITY', itemCode(item)); }
    }
    if (item.kind === 'EVENT') {
      const existing = events.find((value) => value.eventName === item.name);
      if (existing) existing.description = item.summary;
      else { const value = object(item, itemCode(item)); events.push(value); addEvidenceBinding('EVENT', itemCode(item)); }
    }
    if (item.kind === 'FIELD') {
      const owner = [...entities, ...events].find((value) => value.entityName === item.metadata?.owner || value.eventName === item.metadata?.owner);
      if (owner) {
        const attributes = Array.isArray(owner.attributes) ? owner.attributes as Array<Record<string, unknown>> : [];
        const existing = attributes.find((value) => value.attributeName === item.name);
        if (existing) existing.description = item.summary;
        else attributes.push({ id: stableId(item.candidateId), attributeCode: itemCode(item), attributeName: item.name, dataType: item.metadata?.dataType || 'TEXT', semanticType: 'TEXT', isFilterable: true, isGroupable: false, isDisplay: true, isPrimaryTime: false, isMetric: false });
        owner.attributes = attributes;
        if (!existing) addEvidenceBinding('FIELD', itemCode(item), String(owner.entityCode ?? owner.eventCode));
      }
    }
    if (item.kind === 'RELATION') {
      const all: Array<Record<string, unknown> & { kind: 'ENTITY' | 'EVENT' }> = [
        ...entities.map((value) => ({ ...value, kind: 'ENTITY' as const })),
        ...events.map((value) => ({ ...value, kind: 'EVENT' as const })),
      ];
      const source = all.find((value) => value['entityName'] === item.metadata?.source || value['eventName'] === item.metadata?.source);
      const target = all.find((value) => value['entityName'] === item.metadata?.target || value['eventName'] === item.metadata?.target);
      const existing = relations.find((value) => value.relationName === item.name);
      if (existing) existing.description = item.summary;
      else if (source && target) {
        relations.push({ id: stableId(item.candidateId), relationCode: itemCode(item), relationName: item.name, relationType: source.kind === 'EVENT' ? 'EVENT_ENTITY' : 'ENTITY_ENTITY', sourceType: source.kind, sourceId: source['id'], targetType: target.kind, targetId: target['id'], relationRole: 'REFERENCE', cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'LEFT', required: false, status: 'ACTIVE' });
        addEvidenceBinding('RELATION', itemCode(item));
      }
    }
    if (item.kind === 'METRIC') {
      const existing = metrics.find((value) => value.metricName === item.name);
      const value = { id: stableId(item.candidateId), metricCode: itemCode(item), metricName: item.name, metricType: 'COMPOSITE', description: item.summary, owner: 'AI 建模', version: 1, status: 'DRAFT', formula: item.summary, eventName: item.metadata?.event, ruleIds: [] };
      if (existing) { existing.description = item.summary; existing.formula = item.summary; }
      else { metrics.push(value); workspaceMetrics.push(value); addEvidenceBinding('METRIC', itemCode(item)); }
    }
    if (item.kind === 'DIMENSION') {
      const dimensionSidecar = materializedData.semanticSidecar!;
      const existing = dimensionSidecar.dimensions.find((value) => value.name === item.name);
      if (existing) existing.description = item.summary;
      else {
        const dimensionCode = itemCode(item);
        dimensionSidecar.dimensions.push({ id: dimensionCode, name: item.name, description: item.summary });
        addEvidenceBinding('DIMENSION', dimensionCode);
      }
    }
  });
  workspace.entities = entities; workspace.events = events; workspace.semanticRelations = relations; workspace.metrics = workspaceMetrics;
  metricData.metrics = metrics;
  const proposedChanges = deriveDraftObjectChanges({
    draftId, modelSpaceId: artifact.projectId, base, next: materializedData,
  });
  materializedData.workbenchState = {
    ...(materializedData.workbenchState && typeof materializedData.workbenchState === 'object'
      ? clone(materializedData.workbenchState as Record<string, unknown>) : {}),
    modelingDocument: {
      artifactId: artifact.artifactId, revision: artifact.revision,
      markdownSha256: artifact.markdown.sha256, sourceBatch: clone(artifact.sourceBatch),
    },
    proposedChanges,
  };
  return { materializedData, changes: proposedChanges };
}

function knownFixtureProposal(
  artifact: ModelingDocumentArtifact,
  input: { draftId: string; base: MaterializedCollaborationData },
) {
  const document = semanticFixture.documents.find((item) => item.sha256 === artifact.markdown.sha256);
  if (!document?.modelVersion || !document.qualityGate.publishable || document.documentVersion === 'v3') {
    throw new Error('这份已知资料存在质量阻断，不能加入个人草稿');
  }
  const snapshot = adaptPublishedModel(
    'digital_sales_warehouse',
    document.documentVersion as Exclude<DocumentVersion, 'v3'>,
    document.modelVersion as ModelVersion,
  );
  const materializedData: MaterializedCollaborationData = {
    workspaceData: projectLinguanWorkspaceData(snapshot) as unknown as Record<string, unknown>,
    metricData: projectMetric2WorkspaceData(snapshot) as unknown as Record<string, unknown>,
    workbenchState: {
      modelingDocument: {
        artifactId: artifact.artifactId, revision: artifact.revision,
        markdownSha256: artifact.markdown.sha256, sourceBatch: clone(artifact.sourceBatch),
      },
      compatibilitySource: {
        legacySystemCode: snapshot.systemCode, documentVersion: snapshot.documentVersion, modelVersion: snapshot.modelVersion,
      },
    },
    semanticSidecar: {
      schemaVersion: 1, systemCode: artifact.projectId, documentVersion: snapshot.documentVersion,
      sourceFingerprint: snapshot.sourceSha256,
      dimensions: clone(snapshot.dimensions) as unknown as Array<Record<string, unknown>>,
      ruleCandidates: clone(snapshot.ruleCandidates) as unknown as Array<Record<string, unknown>>,
      hierarchyDefinitions: clone(snapshot.hierarchies) as unknown as Array<Record<string, unknown>>,
      evidence: clone(snapshot.evidence) as unknown as Array<Record<string, unknown>>,
      generationNotes: clone(snapshot.generationNotes) as unknown as Array<Record<string, unknown>>,
      compatibilityNotes: [...snapshot.compatibilityNotes, '本草稿由数码产品设计文档兼容适配器生成。'],
    },
  };
  const changes = deriveDraftObjectChanges({
    draftId: input.draftId, modelSpaceId: artifact.projectId, base: input.base, next: materializedData,
  });
  (materializedData.workbenchState as Record<string, unknown>).proposedChanges = changes;
  return { materializedData, changes };
}

export function materializeModelingDocument(
  artifact: ModelingDocumentArtifact,
  candidate: CompiledModelingDocument,
  input: { draftId: string; base: MaterializedCollaborationData },
) {
  if (artifact.status !== 'FROZEN') throw new Error('只有冻结文档可以进入M4 AI建模');
  if (candidate.sourceArtifact.artifactId !== artifact.artifactId
    || candidate.sourceArtifact.revision !== artifact.revision) {
    throw new Error('候选来源版本与冻结文档不一致');
  }
  if (candidate.sourceArtifact.markdownSha256 !== artifact.markdown.sha256) {
    throw new Error('候选来源SHA与冻结文档不一致');
  }
  if (canonicalModelingJson(candidate) !== canonicalModelingJson(compileModelingDocument(artifact))) {
    throw new Error('候选内容与冻结Markdown不一致');
  }
  const workbenchState = input.base.workbenchState && typeof input.base.workbenchState === 'object'
    ? input.base.workbenchState as Record<string, unknown> : {};
  const imported = workbenchState.modelingDocument;
  if (imported && typeof imported === 'object') {
    const identity = imported as Record<string, unknown>;
    if (identity.artifactId === artifact.artifactId && identity.revision === artifact.revision
      && identity.markdownSha256 === artifact.markdown.sha256) {
      return { materializedData: clone(input.base), changes: [] };
    }
  }
  if (artifact.origin === 'MANUAL_UPLOAD'
    && semanticFixture.documents.some((item) => item.sha256 === artifact.markdown.sha256)) {
    return knownFixtureProposal(artifact, input);
  }
  if (artifact.semanticPayload) {
    const projection = projectModelingDocument(artifact, input.base, defaultProjectionContext(artifact));
    return { materializedData: projection.materializedData, changes: projection.changes };
  }
  return genericProposal(artifact, candidate, input.draftId, input.base);
}
