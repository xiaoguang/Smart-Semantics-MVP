import { adaptGroupRetailPublishedModel } from '../ai-modeling/group-retail-adapter.ts';
import type { CandidateModelSnapshot } from '../ai-modeling/candidate-model.ts';
import { groupRetailDocument } from '../ai-modeling/group-retail-model-fixture.ts';
import { projectLinguanWorkspaceData, projectMetric2WorkspaceData } from '../ai-modeling/workspace-store-adapter.ts';
import { buildRetailEvidenceSidecar } from '../collaboration/retail-evidence-fixture.ts';
import type { CatalogEvidenceSidecarV2, FrozenReconciliationFinding } from '../collaboration/types.ts';
import { buildRetailV2Proposal } from '../collaboration/fixtures.ts';
import type { ProjectModelingAdapter } from './types.ts';
import type { PipelineEvidenceClaim } from './types.ts';
import { projectRetailObjectEvidence } from '../evidence-registry/retail-evidence-registry.ts';

const expected = ['retail_mysql', 'retail_mongodb', 'retail_elasticsearch', 'retail_semantica', 'retail_minio', 'retail_github', 'retail_sharepoint', 'retail_kafka'];
const clone = <T>(value: T): T => structuredClone(value);

function sidecar(revision: 1 | 2) {
  const model = adaptGroupRetailPublishedModel();
  return buildRetailEvidenceSidecar({
    systemCode: 'group_retail_ops', sourceFingerprint: model.sourceSha256,
    dimensions: clone(model.dimensions) as unknown as Array<Record<string, unknown>>,
    ruleCandidates: clone(model.ruleCandidates) as unknown as Array<Record<string, unknown>>,
    hierarchyDefinitions: clone(model.hierarchies) as unknown as Array<Record<string, unknown>>,
    generationNotes: clone(model.generationNotes) as unknown as Array<Record<string, unknown>>,
    compatibilityNotes: clone(model.compatibilityNotes),
  }, revision);
}

function fixtureRevision(sources: Parameters<ProjectModelingAdapter['normalize']>[0]['sources']): 1 | 2 | null {
  if (new Set(sources.map((item) => item.connectionId)).size !== expected.length) return null;
  const revisions = [...new Set(sources.map((item) => item.connectionRevision))];
  return revisions.length === 1 && (revisions[0] === 1 || revisions[0] === 2) ? revisions[0] : null;
}

function candidate(claims: PipelineEvidenceClaim[]): CandidateModelSnapshot {
  const tables = [...groupRetailDocument.artifacts.model.entity_tables.map((table) => ({ ...table, kind: 'ENTITY' as const })),
    ...groupRetailDocument.artifacts.model.event_tables.map((table) => ({ ...table, kind: 'EVENT' as const }))];
  const candidateTables = tables.map((table) => {
    const tableEvidence = projectRetailObjectEvidence(table.kind, table.table_id, undefined, claims);
    return {
      id: table.table_id, code: table.table_id, name: table.name, kind: table.kind,
      description: table.definition, evidenceIds: tableEvidence.evidenceRefs,
      supportClaimIds: tableEvidence.supportClaimIds, status: tableEvidence.status,
      fields: table.fields.map((field) => {
        const fieldEvidence = projectRetailObjectEvidence('FIELD', field.field_id, table.table_id, claims);
        return {
          id: `${table.table_id}:${field.field_id}`, code: field.field_id, name: field.name,
          description: field.description, dataType: field.data_type, ownerTableCode: table.table_id,
          evidenceIds: fieldEvidence.evidenceRefs, supportClaimIds: fieldEvidence.supportClaimIds,
          status: fieldEvidence.status,
        };
      }),
    };
  });
  const object = (
    objectKind: Parameters<typeof projectRetailObjectEvidence>[0],
    value: { id: string; code: string; name: string; description: string; target?: string; formula?: string },
  ) => {
    const projection = projectRetailObjectEvidence(objectKind, value.code, undefined, claims);
    return {
      ...value, evidenceIds: projection.evidenceRefs, supportClaimIds: projection.supportClaimIds,
      status: projection.status,
    };
  };
  return {
    status: 'READY', generatedAt: null,
    counts: { entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11, metrics: 12, hierarchies: 2, ruleCandidates: 7, pendingAssets: 0 },
    tables: candidateTables, entities: candidateTables.filter((item) => item.kind === 'ENTITY'),
    events: candidateTables.filter((item) => item.kind === 'EVENT'), fields: candidateTables.flatMap((item) => item.fields),
    relations: groupRetailDocument.artifacts.model.relationships.map((item) => object('RELATION', { id: item.relationship_id, code: item.relationship_id, name: item.name, description: `${item.from_table}.${item.from_field} → ${item.to_table}.${item.to_field}`, target: `${item.to_table}.${item.to_field}` })),
    dimensions: groupRetailDocument.artifacts.enrichment.dimensions.map((item) => object('DIMENSION', { id: item.dimension_id, code: item.dimension_id, name: item.name, description: item.definition, target: item.owner_id ? `${item.owner_id}.${item.target_id}` : item.target_id })),
    metrics: groupRetailDocument.artifacts.model.metrics.map((item) => object('METRIC', { id: item.metric_id, code: item.metric_id, name: item.name, description: item.definition, formula: item.formula, target: item.event_table })),
    hierarchies: groupRetailDocument.artifacts.enrichment.member_hierarchies.map((item) => object('HIERARCHY', { id: item.hierarchy_id, code: item.hierarchy_id, name: item.name, description: `${item.levels.map((level) => level.name).join(' → ')}成员层级`, target: `${item.owner_table_id}.${item.attribute_id}` })),
    ruleCandidates: groupRetailDocument.artifacts.enrichment.rule_candidates.map((item) => object('RULE', { id: item.rule_id, code: item.rule_id, name: item.name, description: item.definition, target: item.owner_id ? `${item.owner_id}.${item.target_id}` : item.target_id })),
    aliases: groupRetailDocument.artifacts.enrichment.synonym_groups.flatMap((item) => item.aliases.map((alias, index) => object('ALIAS', {
      id: `${item.group_id}:${index + 1}`, code: `${item.group_id}:${index + 1}`, name: alias.text,
      description: `${alias.text}指向${item.target_id}`, target: item.owner_id ? `${item.owner_id}.${item.target_id}` : item.target_id,
    }))),
    pendingAssets: [],
  };
}

function presentSidecar(input: Parameters<ProjectModelingAdapter['normalize']>[0]): CatalogEvidenceSidecarV2 {
  const revision = fixtureRevision(input.sources) ?? (input.sources.some((item) => item.connectionRevision === 2) ? 2 : 1);
  return sidecar(revision);
}

function projectFinding(value: FrozenReconciliationFinding, availableClaims: PipelineEvidenceClaim[]) {
  const availableClaimIds = new Set(availableClaims.map((claim) => claim.claimId));
  const claimIds = value.claimIds.filter((id) => availableClaimIds.has(id));
  if (!claimIds.length) return null;
  const sourceCount = new Set(availableClaims.filter((claim) => claimIds.includes(claim.claimId))
    .map((claim) => claim.sourceId)).size;
  const conflict = value.originStatus === 'CONFLICT';
  return {
    findingId: value.findingId, title: value.title,
    status: conflict ? 'CONFLICT' as const : sourceCount === 1 ? 'SINGLE_SOURCE' as const : value.status,
    severity: conflict ? 'BLOCKER' as const : sourceCount === 1 ? 'WEAK' as const : 'CONSISTENT' as const,
    claimIds, agreement: value.agreement, difference: value.difference,
    recommendationId: 'accept_recommended',
    options: [
      { id: 'accept_recommended', label: '采用推荐结论', description: value.decision ?? '保留当前证据支持的结论。' },
      { id: 'keep_blocked', label: '保留阻断', description: '暂不将受影响对象应用到草稿。' },
    ],
    affectedObjectCodes: [...value.affectedObjectCodes],
  };
}

export const retailModelingAdapter: ProjectModelingAdapter = {
  projectId: 'group_retail_ops', workspaceId: 'retail_semantic_modeling', expectedConnectionIds: expected,
  normalize(input) {
    const data = presentSidecar(input);
    const present = new Set(input.sources.map((item) => item.connectionId));
    const evidence = new Set(input.sources.flatMap((item) => item.evidenceIds));
    const claims = data.claims.filter((item) => present.has(item.sourceId) && item.evidenceRefs.every((id) => evidence.has(id)))
      .map((item) => ({
        claimId: item.claimId, sourceId: item.sourceId, assertion: item.assertion,
        authority: item.authority, evidenceRefs: [...item.evidenceRefs],
        ...(item.upstreamClaimIds ? { upstreamClaimIds: [...item.upstreamClaimIds] } : {}),
      }));
    return { claims, locators: Object.fromEntries(Object.entries(data.locators).filter(([id]) => evidence.has(id))) };
  },
  reconcile(input) {
    const data = presentSidecar(input);
    return data.findings.map((item) => projectFinding(item, input.claims)).filter((item): item is NonNullable<typeof item> => Boolean(item));
  },
  generate(input) {
    return fixtureRevision(input.sources) ? candidate(input.claims) : null;
  },
  materialize(input) {
    const model = adaptGroupRetailPublishedModel();
    const revision = fixtureRevision(input.sources) ?? 1;
    const semanticSidecar = sidecar(revision);
    semanticSidecar.batch = {
      ...semanticSidecar.batch, batchId: input.batch.batchId, revision: input.batch.revision,
      frozenAt: input.batch.frozenAt, fingerprint: input.batch.fingerprint,
      snapshotIds: [...input.batch.snapshotIds],
    };
    const base = {
      workspaceData: projectLinguanWorkspaceData(model) as unknown as Record<string, unknown>,
      metricData: projectMetric2WorkspaceData(model) as unknown as Record<string, unknown>,
      workbenchState: { systemCode: 'group_retail_ops', pipelineState: 'APPLIED_TO_DRAFT', batchId: input.batch.batchId },
      semanticSidecar,
    };
    if (revision !== 2) return base;
    const proposal = buildRetailV2Proposal(base, input.scope.draftId);
    const sidecarV2 = proposal.materializedData.semanticSidecar;
    if (sidecarV2?.schemaVersion === 2) {
      sidecarV2.batch = clone(semanticSidecar.batch);
      sidecarV2.findings = sidecarV2.findings.map((finding) => {
        const pipelineFinding = input.findings.find((item) => item.findingId === finding.findingId);
        if (!pipelineFinding?.decision) {
          const { resolution: _resolution, ...withoutResolution } = finding;
          return withoutResolution;
        }
        return {
          ...finding,
          resolution: {
            decidedByUserId: pipelineFinding.decision.reviewer,
            decidedBy: pipelineFinding.decision.reviewer,
            decidedAt: pipelineFinding.decision.decidedAt,
            reason: pipelineFinding.decision.reason,
          },
        };
      });
    }
    proposal.materializedData.workbenchState = {
      systemCode: 'group_retail_ops', pipelineState: 'APPLIED_TO_DRAFT', batchId: input.batch.batchId,
      proposedChanges: proposal.changes,
    };
    return proposal.materializedData;
  },
};
