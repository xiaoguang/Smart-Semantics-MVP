import type { ModelingDocumentArtifact, ModelingDocumentSection } from './types.ts';
import { semanticFixture } from '../ai-modeling/fixture.ts';
import type { FixtureDocument } from '../ai-modeling/types.ts';
import { assertModelingDocumentIntegrity } from './standard-markdown.ts';
import type { StandardizationModelingEligibilityProjection } from '../standardization-deliverable/modeling-eligibility.ts';

export type ModelingDocumentCandidateKind = 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC'
  | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_RULE' | 'PENDING_ASSET';

export type ModelingDocumentCandidateItem = {
  candidateId: string;
  kind: ModelingDocumentCandidateKind;
  name: string;
  summary: string;
  provenance: 'OBSERVED' | 'INFERRED' | 'USER_CONFIRMED';
  evidenceRefs: string[];
  metadata?: Record<string, string>;
};

export type CompiledModelingDocument = {
  candidateId: string;
  sourceArtifact: { artifactId: string; revision: number; markdownSha256: string };
  counts: { entities: number; events: number; fields: number; relations: number; dimensions: number; metrics: number };
  items: ModelingDocumentCandidateItem[];
  unresolved: string[];
};

const sectionKinds: Partial<Record<ModelingDocumentSection, ModelingDocumentCandidateKind>> = {
  OBJECT: 'ENTITY', ACTIVITY: 'EVENT', RELATION: 'RELATION', METRIC: 'METRIC',
};

const primaryCandidateKinds = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC'] as const;

function compileSemanticPayload(artifact: ModelingDocumentArtifact): CompiledModelingDocument {
  const payload = artifact.semanticPayload!.data;
  const tableName = new Map([...payload.entities, ...payload.events].map((item) => [item.code, item.name]));
  const collections: Array<{
    kind: ModelingDocumentCandidateKind;
    items: Array<Record<string, unknown> & { code: string; evidenceRefs: string[]; evidenceStatus?: string }>;
    name(item: Record<string, unknown>): string;
    summary(item: Record<string, unknown>): string;
    metadata(item: Record<string, unknown>): Record<string, string>;
  }> = [
    { kind: 'ENTITY', items: payload.entities, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), physicalTable: String(item.physicalTable ?? '') }) },
    { kind: 'EVENT', items: payload.events, name: (item) => String(item.name), summary: (item) => `${item.description}；粒度：${item.grain}`,
      metadata: (item) => ({ code: String(item.code), grain: String(item.grain), filter: String(item.businessFilter) }) },
    { kind: 'FIELD', items: payload.fields, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), ownerCode: String(item.ownerCode), owner: tableName.get(String(item.ownerCode)) ?? String(item.ownerCode), dataType: String(item.dataType) }) },
    { kind: 'RELATION', items: payload.relations, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), source: tableName.get(String(item.sourceCode)) ?? String(item.sourceCode), target: tableName.get(String(item.targetCode)) ?? String(item.targetCode) }) },
    { kind: 'DIMENSION', items: payload.dimensions, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), target: String(item.targetCode), field: String(item.targetFieldCode ?? '') }) },
    { kind: 'METRIC', items: payload.metrics, name: (item) => String(item.name), summary: (item) => `${item.description}；${item.formula}`,
      metadata: (item) => ({ code: String(item.code), event: tableName.get(String(item.eventCode)) ?? String(item.eventCode), formula: String(item.formula) }) },
    { kind: 'HIERARCHY', items: payload.hierarchies, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), ownerCode: String(item.ownerCode) }) },
    { kind: 'RULE', items: payload.ruleCandidates, name: (item) => String(item.name), summary: (item) => String(item.description),
      metadata: (item) => ({ code: String(item.code), target: String(item.targetCode), structureStatus: String(item.structureStatus) }) },
    { kind: 'ALIAS', items: payload.aliases, name: (item) => String(item.text), summary: (item) => `映射到 ${item.targetCode}`,
      metadata: (item) => ({ code: String(item.code), target: String(item.targetCode), targetType: String(item.targetType) }) },
    { kind: 'TIME_RULE', items: payload.timeRules, name: (item) => String(item.text), summary: (item) => `${item.targetCode}.${item.targetFieldCode} · ${item.granularity}`,
      metadata: (item) => ({ code: String(item.code), target: String(item.targetCode), field: String(item.targetFieldCode) }) },
    { kind: 'PENDING_ASSET', items: payload.pendingAssets.map((item) => ({ ...item, evidenceStatus: 'NEEDS_CONFIRMATION' })),
      name: (item) => String(item.name), summary: (item) => String(item.reason), metadata: (item) => ({ code: String(item.code) }) },
  ];
  const items = collections.flatMap((collection) => collection.items.map((item) => ({
    candidateId: `${artifact.artifactId}:${collection.kind}:${item.code}`,
    kind: collection.kind,
    name: collection.name(item),
    summary: collection.summary(item),
    provenance: item.evidenceStatus === 'VERIFIED' ? 'OBSERVED' as const : 'INFERRED' as const,
    evidenceRefs: [...item.evidenceRefs],
    metadata: collection.metadata(item),
  })));
  const count = (kind: typeof primaryCandidateKinds[number]) => items.filter((item) => item.kind === kind).length;
  return {
    candidateId: `candidate:${artifact.artifactId}:${artifact.markdown.sha256.slice(0, 12)}`,
    sourceArtifact: { artifactId: artifact.artifactId, revision: artifact.revision, markdownSha256: artifact.markdown.sha256 },
    counts: { entities: count('ENTITY'), events: count('EVENT'), fields: count('FIELD'), relations: count('RELATION'), dimensions: count('DIMENSION'), metrics: count('METRIC') },
    items,
    unresolved: [
      ...payload.exclusions.map((item) => `${item.name}：${item.reason}；决定：${item.decision}`),
      ...payload.pendingAssets.map((item) => `${item.name}：${item.reason}`),
    ],
  };
}

function tableRows(content: string) {
  return content.split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|') && line.endsWith('|'))
    .map((line) => line.slice(1, -1).split('|').map((cell) => cell.trim()))
    .filter((cells) => cells.length > 1 && !cells.every((cell) => /^:?-{3,}:?$/.test(cell)))
    .slice(1)
    .filter((cells) => cells[0] && !/^(无|暂无|待补充)$/.test(cells[0]));
}

function normalizedName(value: string) {
  return value.replace(/`/g, '').replace(/\*\*/g, '').trim();
}

function compileKnownFixture(artifact: ModelingDocumentArtifact, document: FixtureDocument): CompiledModelingDocument {
  const items: ModelingDocumentCandidateItem[] = [];
  const add = (item: ModelingDocumentCandidateItem) => items.push(item);
  document.artifacts.model.entity_tables.forEach((table) => {
    add({ candidateId: `${artifact.artifactId}:ENTITY:${table.table_id}`, kind: 'ENTITY', name: table.name,
      summary: table.definition, provenance: 'OBSERVED', evidenceRefs: table.evidence_ids, metadata: { code: table.table_id } });
    table.fields.forEach((field) => add({ candidateId: `${artifact.artifactId}:FIELD:${table.table_id}:${field.field_id}`,
      kind: 'FIELD', name: field.name, summary: field.description, provenance: 'OBSERVED', evidenceRefs: field.evidence_ids,
      metadata: { owner: table.name, ownerCode: table.table_id, code: field.field_id, dataType: field.data_type } }));
  });
  document.artifacts.model.event_tables.forEach((table) => {
    add({ candidateId: `${artifact.artifactId}:EVENT:${table.table_id}`, kind: 'EVENT', name: table.name,
      summary: `${table.definition}；粒度：${table.grain}`, provenance: 'OBSERVED', evidenceRefs: table.evidence_ids,
      metadata: { code: table.table_id, grain: table.grain } });
    table.fields.forEach((field) => add({ candidateId: `${artifact.artifactId}:FIELD:${table.table_id}:${field.field_id}`,
      kind: 'FIELD', name: field.name, summary: field.description, provenance: 'OBSERVED', evidenceRefs: field.evidence_ids,
      metadata: { owner: table.name, ownerCode: table.table_id, code: field.field_id, dataType: field.data_type } }));
  });
  const tableName = new Map([...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables]
    .map((table) => [table.table_id, table.name]));
  document.artifacts.model.relationships.forEach((relation) => add({
    candidateId: `${artifact.artifactId}:RELATION:${relation.relationship_id}`, kind: 'RELATION', name: relation.name,
    summary: relation.inference_reason ?? `${relation.cardinality} 关联`, provenance: 'OBSERVED', evidenceRefs: relation.evidence_ids,
    metadata: { code: relation.relationship_id, source: tableName.get(relation.from_table) ?? relation.from_table,
      target: tableName.get(relation.to_table) ?? relation.to_table },
  }));
  document.artifacts.enrichment.dimensions.forEach((dimension) => add({
    candidateId: `${artifact.artifactId}:DIMENSION:${dimension.dimension_id}`, kind: 'DIMENSION', name: dimension.name,
    summary: dimension.definition, provenance: 'OBSERVED', evidenceRefs: dimension.evidence_ids,
    metadata: { code: dimension.dimension_id, target: dimension.target_id },
  }));
  document.artifacts.model.metrics.forEach((metric) => add({
    candidateId: `${artifact.artifactId}:METRIC:${metric.metric_id}`, kind: 'METRIC', name: metric.name,
    summary: `${metric.definition}；${metric.formula}`, provenance: 'OBSERVED', evidenceRefs: metric.evidence_ids,
    metadata: { code: metric.metric_id, event: tableName.get(metric.event_table) ?? metric.event_table, formula: metric.formula },
  }));
  const unresolved = [
    ...document.qualityGate.blockers.map((item) => `${item.title}：${item.detail}`),
    ...document.artifacts.analysis.unresolved.map((item) => item.question ?? item.title ?? item.detail ?? '待确认事项'),
  ];
  const count = (kind: ModelingDocumentCandidateKind) => items.filter((item) => item.kind === kind).length;
  return {
    candidateId: `candidate:${artifact.artifactId}:${artifact.markdown.sha256.slice(0, 12)}`,
    sourceArtifact: { artifactId: artifact.artifactId, revision: artifact.revision, markdownSha256: artifact.markdown.sha256 },
    counts: { entities: count('ENTITY'), events: count('EVENT'), fields: count('FIELD'), relations: count('RELATION'), dimensions: count('DIMENSION'), metrics: count('METRIC') },
    items, unresolved,
  };
}

function applyEligibility(
  candidate: CompiledModelingDocument,
  eligibility?: StandardizationModelingEligibilityProjection,
): CompiledModelingDocument {
  if (!eligibility) return candidate;
  if (eligibility.artifactId !== candidate.sourceArtifact.artifactId
    || eligibility.artifactMarkdownSha256 !== candidate.sourceArtifact.markdownSha256) {
    throw new Error('建模资格不属于当前标准化文档');
  }
  const declared = new Set([...eligibility.eligibleCandidateIds, ...eligibility.excludedCandidateIds]);
  if (candidate.items.some((item) => !declared.has(item.candidateId))) {
    throw new Error('建模资格没有覆盖当前标准化结论');
  }
  const permitted = new Set(eligibility.eligibleCandidateIds);
  const items = candidate.items.filter((item) => permitted.has(item.candidateId));
  const count = (kind: typeof primaryCandidateKinds[number]) => items.filter((item) => item.kind === kind).length;
  return {
    ...candidate,
    items,
    counts: {
      entities: count('ENTITY'), events: count('EVENT'), fields: count('FIELD'),
      relations: count('RELATION'), dimensions: count('DIMENSION'), metrics: count('METRIC'),
    },
  };
}

export function compileModelingDocument(
  artifact: ModelingDocumentArtifact,
  options: { eligibility?: StandardizationModelingEligibilityProjection } = {},
): CompiledModelingDocument {
  assertModelingDocumentIntegrity(artifact);
  if (artifact.semanticPayload) return applyEligibility(compileSemanticPayload(artifact), options.eligibility);
  const knownDocument = semanticFixture.documents.find((document) => document.sha256 === artifact.markdown.sha256);
  if (knownDocument) return applyEligibility(compileKnownFixture(artifact, knownDocument), options.eligibility);
  const items: ModelingDocumentCandidateItem[] = [];
  (Object.entries(sectionKinds) as Array<[ModelingDocumentSection, ModelingDocumentCandidateKind]>).forEach(([section, kind]) => {
    tableRows(artifact.sections[section]).forEach((row, index) => {
      const name = normalizedName(row[0]);
      const linked = artifact.assertions.filter((assertion) => assertion.section === section
        && (assertion.statement.includes(name) || row.some((cell) => assertion.statement.includes(normalizedName(cell)))));
      items.push({
        candidateId: `${artifact.artifactId}:${section}:${index + 1}`,
        kind, name, summary: row.slice(1).filter(Boolean).join('；'),
        provenance: linked.some((item) => item.provenance === 'OBSERVED') ? 'OBSERVED'
          : linked.some((item) => item.provenance === 'USER_CONFIRMED') ? 'USER_CONFIRMED' : 'INFERRED',
        evidenceRefs: [...new Set(linked.flatMap((item) => item.evidenceRefs))],
        metadata: section === 'RELATION' ? { source: normalizedName(row[1] ?? ''), target: normalizedName(row[2] ?? '') }
          : section === 'METRIC' ? { event: normalizedName(row[4] ?? '') } : undefined,
      });
    });
  });
  tableRows(artifact.sections.FIELD).forEach((row, index) => {
    const owner = normalizedName(row[0] ?? ''); const name = normalizedName(row[1] ?? '');
    if (!name) return;
    const linked = artifact.assertions.filter((assertion) => assertion.section === 'FIELD'
      && (assertion.statement.includes(name) || assertion.statement.includes(owner)));
    const base = {
      provenance: (linked.some((item) => item.provenance === 'OBSERVED') ? 'OBSERVED'
        : linked.some((item) => item.provenance === 'USER_CONFIRMED') ? 'USER_CONFIRMED' : 'INFERRED') as ModelingDocumentCandidateItem['provenance'],
      evidenceRefs: [...new Set(linked.flatMap((item) => item.evidenceRefs))],
    };
    items.push({ candidateId: `${artifact.artifactId}:FIELD:${index + 1}`, kind: 'FIELD', name, summary: `${owner} · ${row.slice(2).filter(Boolean).join('；')}`, metadata: { owner, dataType: normalizedName(row[2] ?? '') }, ...base });
    if (/维度|分组|层级/.test(row[4] ?? '')) items.push({ candidateId: `${artifact.artifactId}:DIMENSION:${index + 1}`, kind: 'DIMENSION', name: `${owner}.${name}`, summary: row[4] ?? '分析维度', metadata: { owner, field: name }, ...base });
  });
  artifact.assertions.filter((assertion) => assertion.section === 'FIELD' && assertion.assertionId.includes('dimension-'))
    .forEach((assertion, index) => {
      const match = assertion.statement.match(/维度候选“([^”]+)”映射到(.+)$/);
      if (!match) return;
      items.push({
        candidateId: `${artifact.artifactId}:DIMENSION:ASSERTION:${index + 1}`, kind: 'DIMENSION', name: match[1],
        summary: `映射到${match[2]}`, provenance: assertion.provenance, evidenceRefs: [...assertion.evidenceRefs],
        metadata: { target: match[2] },
      });
    });
  const unresolved = artifact.sections.UNRESOLVED.split('\n')
    .map((line) => line.replace(/^\s*(?:[-*]|\d+[.)])\s*/, '').trim())
    .filter((line) => line && !/^\[已决定\]/.test(line));
  const count = (kind: ModelingDocumentCandidateKind) => items.filter((item) => item.kind === kind).length;
  return applyEligibility({
    candidateId: `candidate:${artifact.artifactId}:${artifact.markdown.sha256.slice(0, 12)}`,
    sourceArtifact: { artifactId: artifact.artifactId, revision: artifact.revision, markdownSha256: artifact.markdown.sha256 },
    counts: { entities: count('ENTITY'), events: count('EVENT'), fields: count('FIELD'), relations: count('RELATION'), dimensions: count('DIMENSION'), metrics: count('METRIC') },
    items, unresolved,
  }, options.eligibility);
}
