import type { AlignmentClaim, AlignmentObjectKind } from '../document-alignment/types.ts';
import {
  retailEvidenceClaims, retailEvidenceEntries, retailEvidenceSources,
} from '../evidence-registry/retail-evidence-registry.ts';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type {
  ModelingDocumentArtifact, ModelingDocumentSection, StructuredModelingAssertion,
} from '../modeling-document-bridge/types.ts';
import type { RegisterSourceDocumentInput, SourceDocumentSections, SourceModelingDocument } from './types.ts';

type FrozenSource = {
  snapshotId: string;
  connectionId: string;
  displayName: string;
  connectorType: string;
  role: string;
  authority: AlignmentClaim['authority'];
  summary: string;
  evidenceIds: string[];
  upstreamConnectionIds: string[];
};

type FrozenClaim = {
  claimId: string;
  sourceId: string;
  topic: string;
  assertion: string;
  authority: AlignmentClaim['authority'];
  evidenceRefs: string[];
  upstreamClaimIds: string[];
};

const authorityValues = new Set<AlignmentClaim['authority']>(['PRIMARY', 'CORROBORATING', 'DERIVED', 'AUXILIARY']);

function strings(value: unknown) {
  return Array.isArray(value) ? value.map(String) : [];
}

function sourceFromRecord(record: Record<string, unknown>): FrozenSource {
  const authority = String(record.authority ?? 'AUXILIARY') as AlignmentClaim['authority'];
  return {
    snapshotId: String(record.snapshotId ?? record.sourceSnapshotIdentity ?? ''),
    connectionId: String(record.connectionId ?? record.sourceId ?? ''),
    displayName: String(record.displayName ?? record.connectionId ?? '未命名来源'),
    connectorType: String(record.connectorType ?? 'UNKNOWN'),
    role: String(record.role ?? '补充建模证据'),
    authority: authorityValues.has(authority) ? authority : 'AUXILIARY',
    summary: String(record.summary ?? '该来源已冻结为不可变证据快照。'),
    evidenceIds: strings(record.evidenceIds),
    upstreamConnectionIds: strings(record.upstreamConnectionIds),
  };
}

function claimFromRecord(record: Record<string, unknown>): FrozenClaim {
  const authority = String(record.authority ?? 'AUXILIARY') as AlignmentClaim['authority'];
  return {
    claimId: String(record.claimId),
    sourceId: String(record.sourceId),
    topic: String(record.topic ?? record.claimId),
    assertion: String(record.assertion ?? '该来源提供了可定位的建模依据。'),
    authority: authorityValues.has(authority) ? authority : 'AUXILIARY',
    evidenceRefs: strings(record.evidenceRefs),
    upstreamClaimIds: strings(record.upstreamClaimIds),
  };
}

function sectionForTopic(topic: string, claimId: string): ModelingDocumentSection {
  const value = `${topic} ${claimId}`.toUpperCase();
  if (value.includes('ALIAS') || value.includes('CUSTOMER_TIER') || value.includes('PRODUCT_ATTRIBUTE')) return 'FIELD';
  if (value.includes('BUSINESS_DAY') || value.includes('TIME')) return 'FIELD';
  if (value.includes('NET_SALES') || value.includes('BOT_FILTER') || value.includes('INVENTORY')) return 'METRIC';
  if (value.includes('STATUS') || value.includes('WORKFLOW') || value.includes('ACTIVITY')) return 'ACTIVITY';
  if (value.includes('RELATION') || value.includes('JOIN')) return 'RELATION';
  return 'OBJECT';
}

function emptySections(source: FrozenSource): SourceDocumentSections {
  return Object.fromEntries(standardSectionOrder.map(({ key, heading }) => [key,
    key === 'OVERVIEW'
      ? `来源：${source.displayName}\n\n快照：${source.snapshotId}\n\n角色：${source.role}\n\n${source.summary}`
      : key === 'GOAL'
        ? `${source.displayName}用于${source.role}；本文只陈述该来源能够直接支持或明确推断的内容。`
        : `${source.displayName}未提供“${heading}”的直接依据。`,
  ])) as SourceDocumentSections;
}

function sourcesAndClaims(artifact: ModelingDocumentArtifact) {
  const context = artifact.semanticPayload?.data.evidenceContext;
  if (!context && artifact.projectId === 'group_retail_ops') {
    const snapshotIds = artifact.sourceBatch?.snapshotIds ?? [];
    const matchedSources = retailEvidenceSources.flatMap((source): FrozenSource[] => {
      const snapshotId = snapshotIds.find((candidate) => {
        const normalized = candidate.toLowerCase();
        return normalized.includes(source.connectionId) || normalized.includes(source.connectionId.replace('retail_', ''));
      });
      if (!snapshotId) return [];
      return [{
        snapshotId,
        connectionId: source.connectionId,
        displayName: source.displayName,
        connectorType: source.connectorType,
        role: source.role,
        authority: source.authority,
        summary: source.summary,
        evidenceIds: retailEvidenceEntries.filter((entry) => entry.connectionId === source.connectionId)
          .map((entry) => entry.evidenceId),
        upstreamConnectionIds: [...(source.upstreamConnectionIds ?? [])],
      }];
    });
    const present = new Set(matchedSources.map((source) => source.connectionId));
    return {
      sources: matchedSources,
      claims: retailEvidenceClaims.filter((claim) => present.has(claim.sourceId)).map((claim) => ({
        claimId: claim.claimId,
        sourceId: claim.sourceId,
        topic: claim.topic,
        assertion: claim.assertion,
        authority: claim.authority,
        evidenceRefs: [...claim.evidenceRefs],
        upstreamClaimIds: [...(claim.upstreamClaimIds ?? [])],
      })),
    };
  }
  if (!context) return { sources: [] as FrozenSource[], claims: [] as FrozenClaim[] };
  const claims = context.claims.map(claimFromRecord).filter((claim) => claim.claimId && claim.sourceId);
  if (artifact.projectId === 'guanyijia_erp') {
    const debt = artifact.semanticPayload?.data.exclusions.find((item) => item.code === 'receivable_debt');
    if (debt) {
      claims.push({
        claimId: 'claim_guanyijia_debt_source_schema', sourceId: 'guanyijia_github',
        topic: 'GUANYIJIA_DEBT_SCHEMA', assertion: '源码定义 debt、last_debt、last_deposit 字段。',
        authority: 'CORROBORATING', evidenceRefs: debt.evidenceRefs.filter((item) => item.startsWith('github_')),
        upstreamClaimIds: [],
      }, {
        claimId: 'claim_guanyijia_debt_deployed_schema', sourceId: 'guanyijia_mysql',
        topic: 'GUANYIJIA_DEBT_SCHEMA', assertion: '当前部署表缺少 debt、last_debt、last_deposit 字段。',
        authority: 'PRIMARY', evidenceRefs: debt.evidenceRefs.filter((item) => item.startsWith('mysql_')),
        upstreamClaimIds: [],
      });
    }
  }
  return {
    sources: context.sources.map(sourceFromRecord).filter((source) => source.snapshotId && source.connectionId),
    claims,
  };
}

function uniqueAssertions(assertions: StructuredModelingAssertion[]) {
  const byId = new Map<string, StructuredModelingAssertion>();
  assertions.forEach((assertion) => byId.set(assertion.assertionId, assertion));
  return [...byId.values()];
}

export function projectArtifactSourceDocuments(
  artifact: ModelingDocumentArtifact,
  actorUserId: string,
): RegisterSourceDocumentInput[] {
  const { sources, claims } = sourcesAndClaims(artifact);
  return sources.map((source) => {
    const allowedEvidence = new Set(source.evidenceIds);
    const sourceClaims = claims.filter((claim) => claim.sourceId === source.connectionId
      && claim.evidenceRefs.every((evidenceId) => allowedEvidence.has(evidenceId)));
    const exclusiveAssertions = artifact.assertions.filter((assertion) => assertion.evidenceRefs.length > 0
      && assertion.evidenceRefs.every((evidenceId) => allowedEvidence.has(evidenceId)));
    const claimAssertions: StructuredModelingAssertion[] = sourceClaims.map((claim) => ({
      assertionId: `source:${source.connectionId}:${claim.claimId}`,
      section: sectionForTopic(claim.topic, claim.claimId),
      statement: claim.assertion,
      provenance: claim.authority === 'DERIVED' ? 'INFERRED' : 'OBSERVED',
      evidenceRefs: [...claim.evidenceRefs],
    }));
    const assertions = uniqueAssertions([...exclusiveAssertions, ...claimAssertions]);
    const sections = emptySections(source);
    for (const { key } of standardSectionOrder) {
      if (key === 'OVERVIEW' || key === 'GOAL') continue;
      const sectionAssertions = assertions.filter((assertion) => assertion.section === key);
      if (sectionAssertions.length) {
        sections[key] = sectionAssertions.map((assertion) =>
          `- ${assertion.statement}（依据：${assertion.evidenceRefs.join('、') || '待补充'}）`).join('\n');
      }
    }
    if (source.authority === 'DERIVED') {
      sections.UNRESOLVED = source.upstreamConnectionIds.length
        ? `该来源属于派生依据。对齐时必须核验上游来源：${source.upstreamConnectionIds.join('、')}。`
        : '该来源被标记为派生依据，但未登记上游来源，进入血缘缺口队列。';
    }
    return {
      projectId: artifact.projectId,
      documentCode: `${artifact.documentCode}--${source.connectionId}`,
      sourceSnapshotId: source.snapshotId,
      sourceType: source.connectorType,
      sourceName: source.displayName,
      sections,
      assertions,
      validation: {
        errors: [],
        warnings: source.authority === 'DERIVED' ? [{
          code: 'DERIVED_SOURCE', message: '派生来源必须与上游根来源一起审阅。',
        }] : [],
        gaps: assertions.length ? [] : [{ code: 'NO_MODELING_ASSERTION', message: '该来源尚未提取出可用于建模的断言。' }],
      },
      actorUserId,
    };
  });
}

function alignmentMeaning(claim: FrozenClaim) {
  const id = claim.claimId.toLowerCase();
  if (id === 'claim_guanyijia_debt_source_schema') return {
    topicRef: 'field:guanyijia:debt_schema', normalizedValue: 'SOURCE_SCHEMA_HAS_DEBT', objectKind: 'FIELD' as const,
  };
  if (id === 'claim_guanyijia_debt_deployed_schema') return {
    topicRef: 'field:guanyijia:debt_schema', normalizedValue: 'DEPLOYED_SCHEMA_NO_DEBT', objectKind: 'FIELD' as const,
  };
  if (id === 'claim_mysql_net_sales_legacy') return {
    topicRef: 'metric:net_sales:refund_timing', normalizedValue: 'RETURN_STARTED', objectKind: 'METRIC' as const,
  };
  if (['claim_github_net_sales', 'claim_sharepoint_net_sales', 'claim_minio_net_sales', 'claim_kafka_return_completed'].includes(id)) return {
    topicRef: 'metric:net_sales:refund_timing', normalizedValue: 'REFUND_COMPLETED', objectKind: 'METRIC' as const,
  };
  if (id === 'claim_mysql_refund_fields') return {
    topicRef: 'field:return:refund_times', normalizedValue: 'SEPARATE_INITIATED_AND_COMPLETED', objectKind: 'FIELD' as const,
  };
  if (['claim_github_bot_filter', 'claim_elasticsearch_bot_filter'].includes(id)) return {
    topicRef: 'metric:search_conversion:traffic_filter', normalizedValue: 'EXCLUDE_BOT_AND_INTERNAL_TEST', objectKind: 'RULE' as const,
  };
  if (id === 'claim_elasticsearch_bot_filter_legacy') return {
    topicRef: 'metric:search_conversion:traffic_filter', normalizedValue: 'EXCLUDE_BOT_ONLY', objectKind: 'RULE' as const,
  };
  if (id === 'claim_elasticsearch_traffic_mapping' || id === 'claim_kafka_bot_marker') return {
    topicRef: `field:search:${id}`, normalizedValue: 'TRAFFIC_MARKER_AVAILABLE', objectKind: 'FIELD' as const,
  };
  if (['claim_sharepoint_business_day', 'claim_minio_business_day'].includes(id)) return {
    topicRef: 'time:business_day:assignment', normalizedValue: 'STORE_BUSINESS_DAY', objectKind: 'TIME_RULE' as const,
  };
  if (id === 'claim_kafka_business_day_legacy') return {
    topicRef: 'time:business_day:assignment', normalizedValue: 'CALENDAR_DAY', objectKind: 'TIME_RULE' as const,
  };
  const kind: AlignmentObjectKind = claim.topic.includes('ALIAS') ? 'ALIAS'
    : claim.topic.includes('TIER') ? 'DIMENSION'
      : claim.topic.includes('INVENTORY') ? 'EVENT'
        : claim.topic.includes('STATUS') ? 'EVENT' : 'ENTITY';
  return { topicRef: `consensus:${claim.topic}`, normalizedValue: `CONSENSUS:${claim.topic}`, objectKind: kind };
}

export function projectArtifactAlignmentClaims(
  artifact: ModelingDocumentArtifact,
  documents: Array<Pick<SourceModelingDocument, 'documentId' | 'sourceSnapshotId'>>,
): AlignmentClaim[] {
  const { sources, claims } = sourcesAndClaims(artifact);
  const documentByConnection = new Map(sources.flatMap((source) => {
    const document = documents.find((item) => item.sourceSnapshotId === source.snapshotId);
    return document ? [[source.connectionId, document.documentId] as const] : [];
  }));
  const upstreamDocumentsByClaim = new Map(claims.map((claim) => [claim.claimId, documentByConnection.get(claim.sourceId)]));
  return claims.flatMap((claim) => {
    const sourceDocumentId = documentByConnection.get(claim.sourceId);
    if (!sourceDocumentId) return [];
    const meaning = alignmentMeaning(claim);
    return [{
      claimId: claim.claimId,
      sourceDocumentId,
      ...meaning,
      statement: claim.assertion,
      authority: claim.authority,
      evidenceRefs: [...claim.evidenceRefs],
      upstreamSourceDocumentIds: claim.upstreamClaimIds.flatMap((claimId) => {
        const documentId = upstreamDocumentsByClaim.get(claimId);
        return documentId ? [documentId] : [];
      }),
    }];
  });
}
