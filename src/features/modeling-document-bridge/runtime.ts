import { modelingDocumentFixture } from './fixtures.ts';
import {
  buildGuanyijiaEvidenceStory, buildRetailRegistryBlockers, guanyijiaEvidenceSources,
  retailEvidenceClaims, retailEvidenceEntries, retailEvidenceSources,
} from '../evidence-registry/retail-evidence-registry.ts';
import {
  guanyijiaEvidenceFixture, guanyijiaEvidenceLocators, guanyijiaRepositoryEvidenceFixture,
} from '../ai-modeling/guanyijia-fixture.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { groupRetailDocument } from '../ai-modeling/group-retail-model-fixture.ts';
import { assertModelingDocumentIntegrity, assertStandardModelingMarkdown, parseNineSectionMarkdown, renderStandardModelingMarkdown } from './standard-markdown.ts';
import { createReviewQueryService } from './review-query-service.ts';
import { guanyijiaFrozenModelingArtifact } from './guanyijia-modeling-baseline.ts';
import type {
  ModelingDocumentArtifact, ModelingDocumentCommand, ModelingDocumentImportRecord,
  ModelingDocumentRuntime, ModelingDocumentSection, ReviewEvidence, ReviewIssue, ReviewSnapshot,
} from './types.ts';

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>;
type StoredState = { artifacts: ModelingDocumentArtifact[]; imports: ModelingDocumentImportRecord[]; sequence: number };
const storageKey = 'linguan:modeling-documents:v1';
const clone = <T,>(value: T): T => structuredClone(value);
const guanyijiaPendingAssetCount = guanyijiaEvidenceFixture.manifest.objectCounts.tables
  - guanyijiaRepositoryEvidenceFixture.manifest.objectCounts.tables;

function artifactsWithBaselines(state: StoredState) {
  const artifacts = [...state.artifacts];
  if (!artifacts.some((item) => item.artifactId === guanyijiaFrozenModelingArtifact.artifactId)) {
    artifacts.push(guanyijiaFrozenModelingArtifact);
  }
  return artifacts;
}

async function sha256(content: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(content));
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('');
}

function load(storage: StorageLike): StoredState {
  const raw = storage.getItem(storageKey);
  if (!raw) return { artifacts: [], imports: [], sequence: 0 };
  try { return JSON.parse(raw) as StoredState; }
  catch { return { artifacts: [], imports: [], sequence: 0 }; }
}

async function materialize(base: Omit<ModelingDocumentArtifact, 'markdown'>): Promise<ModelingDocumentArtifact> {
  const content = renderStandardModelingMarkdown(base);
  assertStandardModelingMarkdown(content);
  return { ...base, markdown: { fileName: `${base.documentCode}-r${base.revision}.md`, content, sha256: await sha256(content), byteLength: new TextEncoder().encode(content).byteLength } };
}

function retailConnectionIds(snapshotIds: string[]) {
  return retailEvidenceSources
    .filter((source) => snapshotIds.some((snapshotId) => {
      const normalized = snapshotId.toLowerCase();
      return normalized.includes(source.connectionId) || normalized.includes(source.connectionId.replace('retail_', ''));
    }))
    .map((source) => source.connectionId);
}

function rootSourceIds(connectionId: string, present: Set<string>, path = new Set<string>()): string[] {
  if (path.has(connectionId)) return [];
  const source = retailEvidenceSources.find((item) => item.connectionId === connectionId);
  if (!source?.upstreamConnectionIds?.length) return [connectionId];
  const next = new Set(path); next.add(connectionId);
  return source.upstreamConnectionIds.flatMap((upstreamId) =>
    present.has(upstreamId) ? rootSourceIds(upstreamId, present, next) : []);
}

const retailResolutionOptions: Record<string, ReviewIssue['options']> = {
  finding_net_sales_refund: [
    { resolutionId: 'adopt_completed_refund', label: '按退款完成时点', conclusion: '净销售额仅在退款完成后冲减。' },
    { resolutionId: 'adopt_return_started', label: '按退货发起时点', conclusion: '净销售额在退货发起后立即冲减。' },
  ],
  finding_bot_filter: [
    { resolutionId: 'exclude_bot_and_internal_test', label: '同时排除两类流量', conclusion: '搜索转化率同时排除机器人和内部压测流量。' },
    { resolutionId: 'exclude_bot_only', label: '仅排除机器人', conclusion: '搜索转化率仅排除机器人流量。' },
  ],
  finding_business_day: [
    { resolutionId: 'adopt_store_business_day', label: '采用门店营业日', conclusion: '业务日期按跨自然日的门店营业日规则归属。' },
    { resolutionId: 'adopt_calendar_day', label: '采用自然日', conclusion: '业务日期按事件发生的自然日归属。' },
  ],
};

const guanyijiaDebtOptions: ReviewIssue['options'] = [
  { resolutionId: 'keep_debt_metric_blocked', label: '按部署库阻断欠款指标', conclusion: '部署库补齐并验证欠款字段前，不生成欠款指标。' },
  { resolutionId: 'migrate_then_enable_debt', label: '先迁移再启用', conclusion: '先执行字段迁移并重新采集证据，再启用欠款指标。' },
];

const guanyijiaPendingOptions: ReviewIssue['options'] = [
  { resolutionId: 'keep_pending', label: '保留待确认', conclusion: '保留为显式待确认事项，不生成已确认对象或口径。' },
];

function guanyijiaReviewEvidence(evidenceId: string): ReviewEvidence {
  const databaseRecord = guanyijiaEvidenceFixture.manifest.evidenceFiles.find((item) => item.evidenceId === evidenceId);
  const repositoryRecord = guanyijiaRepositoryEvidenceFixture.manifest.evidenceFiles.find((item) => item.evidenceId === evidenceId);
  const record = databaseRecord ?? repositoryRecord;
  const locator = guanyijiaEvidenceLocators[evidenceId];
  if (!record || !locator) throw new Error(`证据不存在：${evidenceId}`);
  const sourceId = databaseRecord ? 'guanyijia_mysql' : 'guanyijia_github';
  const statement = `${databaseRecord ? '部署数据库' : '固定GitHub Commit'}确认 ${record.objectType} ${record.objectName}。`;
  const normalizedLocator = clone(locator) as unknown as Record<string, unknown>;
  return {
    evidenceId, sourceId, rootSourceIds: [sourceId], statement, locator: normalizedLocator,
    checksum: sha256HexSync(JSON.stringify({ statement, locator: normalizedLocator })),
  };
}

function payloadReviewEvidence(artifact: ModelingDocumentArtifact, evidenceId: string): ReviewEvidence {
  const context = artifact.semanticPayload?.data.evidenceContext;
  if (!context) throw new Error(`证据不存在：${evidenceId}`);
  const record = context.evidence.find((item) => item.evidenceId === evidenceId);
  const locator = context.locators[evidenceId];
  if (!record || !locator) throw new Error(`证据不存在：${evidenceId}`);
  const sourceId = String(record.sourceId ?? 'unknown_source');
  const supporting = context.claims
    .filter((claim) => Array.isArray(claim.evidenceRefs) && claim.evidenceRefs.includes(evidenceId))
    .map((claim) => String(claim.assertion ?? ''))
    .filter(Boolean);
  const statement = supporting.length
    ? supporting.slice(0, 3).join('；')
    : `${sourceId} 提供了可定位的结构化证据。`;
  const normalizedLocator = clone(locator);
  return {
    evidenceId, sourceId, rootSourceIds: [sourceId], statement, locator: normalizedLocator,
    checksum: sha256HexSync(JSON.stringify({ statement, locator: normalizedLocator })),
  };
}

function buildPayloadReviewSnapshot(artifact: ModelingDocumentArtifact): ReviewSnapshot | null {
  const context = artifact.semanticPayload?.data.evidenceContext;
  if (!context) return null;
  const evidenceIdsForFinding = (finding: Record<string, unknown>) => {
    const affected = Array.isArray(finding.affectedObjectCodes)
      ? finding.affectedObjectCodes.map(String) : [];
    const claimIds = Array.isArray(finding.claimIds) ? finding.claimIds.map(String) : [];
    const claimEvidence = context.claims
      .filter((claim) => claimIds.includes(String(claim.claimId))
        || affected.some((code) => String(claim.topic ?? '').includes(code)))
      .flatMap((claim) => Array.isArray(claim.evidenceRefs) ? claim.evidenceRefs.map(String) : []);
    const directlyMatched = context.evidence
      .map((item) => String(item.evidenceId))
      .filter((evidenceId) => affected.some((code) => evidenceId.toLowerCase().includes(code.toLowerCase())));
    return [...new Set([...claimEvidence, ...directlyMatched])];
  };
  const reviewFindings = context.findings.filter((finding) => String(finding.status) !== 'CONSISTENT');
  const issues: ReviewIssue[] = reviewFindings.map((finding) => {
    const decided = Boolean(finding.decision || finding.resolution);
    const conclusion = String(finding.decision ?? finding.difference ?? finding.title ?? '已记录结论');
    return {
      issueId: String(finding.findingId), topic: String(finding.findingId), title: String(finding.title),
      severity: String(finding.status) === 'CONFLICT' ? 'BLOCKER' : 'WARNING',
      status: decided ? 'DECIDED' : 'OPEN',
      difference: String(finding.difference ?? finding.agreement ?? ''),
      claimIds: Array.isArray(finding.claimIds) ? finding.claimIds.map(String) : [],
      evidenceRefs: evidenceIdsForFinding(finding),
      affectedObjectCodes: Array.isArray(finding.affectedObjectCodes) ? finding.affectedObjectCodes.map(String) : [],
      recommendedResolutionId: decided ? 'recorded_decision' : 'keep_pending',
      options: [{ resolutionId: decided ? 'recorded_decision' : 'keep_pending', label: decided ? '已记录决定' : '保留待确认', conclusion }],
    };
  });
  const decisions = issues.filter((issue) => issue.status === 'DECIDED').map((issue) => ({
    decisionId: `baseline:${issue.issueId}`, issueId: issue.issueId,
    resolutionId: issue.recommendedResolutionId, conclusion: issue.options[0].conclusion,
    reason: '证据基线决定', actorUserId: 'system_baseline', decidedAt: artifact.updatedAt,
    evidenceRefs: [...issue.evidenceRefs],
  }));
  const issueIdsByEvidence = new Map<string, string[]>();
  issues.forEach((issue) => issue.evidenceRefs.forEach((evidenceId) => {
    issueIdsByEvidence.set(evidenceId, [...(issueIdsByEvidence.get(evidenceId) ?? []), issue.issueId]);
  }));
  const evidenceIndex = context.evidence.map((record) => {
    const evidenceId = String(record.evidenceId);
    const evidence = payloadReviewEvidence(artifact, evidenceId);
    return {
      evidenceId, sourceId: evidence.sourceId, rootSourceIds: [...evidence.rootSourceIds],
      issueIds: issueIdsByEvidence.get(evidenceId) ?? [], checksum: evidence.checksum,
    };
  });
  return {
    overview: {
      sourceCount: context.sources.length, rootSourceCount: context.sources.length,
      evidenceCount: context.evidence.length, claimCount: context.claims.length,
      issueCount: issues.length, openIssueCount: issues.filter((item) => item.status === 'OPEN').length,
      decisionCount: decisions.length, lineageGapCount: 0,
    },
    issues, decisions, evidenceIndex,
  };
}

function buildReviewSnapshot(artifact: ModelingDocumentArtifact): ReviewSnapshot {
  if (artifact.review) return clone(artifact.review);
  const payloadReview = buildPayloadReviewSnapshot(artifact);
  if (payloadReview) return payloadReview;
  if (artifact.projectId === 'guanyijia_erp') {
    const connectionIds = guanyijiaConnectionIds(artifact.sourceBatch?.snapshotIds ?? []);
    const present = new Set(connectionIds);
    const claims = buildGuanyijiaEvidenceStory().at(-1)!.claims.filter((claim) => present.has(claim.sourceId));
    const evidenceIds = [...new Set(claims.flatMap((claim) => claim.evidenceRefs))].sort();
    const hasStructureConflict = present.has('guanyijia_mysql') && present.has('guanyijia_github');
    const blocker: ReviewIssue | null = hasStructureConflict ? {
      issueId: 'finding_guanyijia_debt_fields', topic: 'DEBT_FIELDS', title: '欠款字段结构冲突',
      severity: 'BLOCKER', status: 'OPEN',
      difference: '固定源码包含 debt、last_debt、last_deposit，当前部署库缺少这些字段。',
      claimIds: ['claim_guanyijia_mysql_extensions', 'claim_guanyijia_github_depot_head'],
      evidenceRefs: ['mysql_jsh_erp@v1:TABLE:biz_bill_item_fact', 'github_jshERP@v1:TABLE:jsh_depot_head'],
      affectedObjectCodes: ['metric_debt_balance'], recommendedResolutionId: guanyijiaDebtOptions[0].resolutionId,
      options: clone(guanyijiaDebtOptions),
    } : null;
    const warnings: ReviewIssue[] = hasStructureConflict ? [
      {
        issueId: 'finding_guanyijia_extensions_pending', topic: 'EXTENSION_TABLES', title: `${guanyijiaPendingAssetCount}张扩展表待归类`,
        severity: 'WARNING', status: 'OPEN', difference: '这些表只在部署数据库出现，缺少固定源码中的业务定义。',
        claimIds: ['claim_guanyijia_mysql_extensions'], evidenceRefs: ['mysql_jsh_erp@v1:TABLE:biz_bill_item_fact'],
        affectedObjectCodes: ['pending_extension_tables'], recommendedResolutionId: 'keep_pending', options: clone(guanyijiaPendingOptions),
      },
      {
        issueId: 'finding_guanyijia_status_nine_basis', topic: 'STATUS_NINE', title: '状态9常量依据待完善',
        severity: 'WARNING', status: 'OPEN', difference: 'DDL与Service支持审核中含义，但业务常量没有完整覆盖。',
        claimIds: ['claim_guanyijia_github_depot_head'], evidenceRefs: ['github_jshERP@v1:TABLE:jsh_depot_head'],
        affectedObjectCodes: ['document_status_dimension'], recommendedResolutionId: 'keep_pending', options: clone(guanyijiaPendingOptions),
      },
    ] : [];
    const issues = [...(blocker ? [blocker] : []), ...warnings];
    return {
      overview: {
        sourceCount: connectionIds.length, rootSourceCount: connectionIds.length, evidenceCount: evidenceIds.length,
        claimCount: claims.length, issueCount: issues.length, openIssueCount: issues.length,
        decisionCount: 0, lineageGapCount: artifact.validation.gaps.filter((gap) => gap.code === 'LINEAGE_GAP').length,
      },
      issues, decisions: [],
      evidenceIndex: evidenceIds.map((evidenceId) => {
        const claim = claims.find((item) => item.evidenceRefs.includes(evidenceId))!;
        const reviewEvidence = guanyijiaReviewEvidence(evidenceId);
        return {
          evidenceId, sourceId: claim.sourceId, rootSourceIds: [claim.sourceId],
          issueIds: issues.filter((issue) => issue.evidenceRefs.includes(evidenceId)).map((issue) => issue.issueId),
          checksum: reviewEvidence.checksum,
        };
      }),
    };
  }
  const connectionIds = retailConnectionIds(artifact.sourceBatch?.snapshotIds ?? []);
  if (artifact.projectId !== 'group_retail_ops' || !connectionIds.length) {
    return {
      overview: {
        sourceCount: artifact.sourceBatch?.snapshotIds.length ?? 0, rootSourceCount: artifact.sourceBatch?.snapshotIds.length ?? 0,
        evidenceCount: 0, claimCount: artifact.assertions.length, issueCount: 0, openIssueCount: 0,
        decisionCount: 0, lineageGapCount: artifact.validation.gaps.length,
      },
      issues: [], decisions: [], evidenceIndex: [],
    };
  }
  const present = new Set(connectionIds);
  const evidence = retailEvidenceEntries.filter((item) => present.has(item.connectionId));
  const availableEvidenceIds = new Set(evidence.map((item) => item.evidenceId));
  const claims = retailEvidenceClaims.filter((claim) => present.has(claim.sourceId)
    && claim.evidenceRefs.every((evidenceId) => availableEvidenceIds.has(evidenceId)));
  const blockers = buildRetailRegistryBlockers(connectionIds, 2);
  const issues: ReviewIssue[] = blockers.map((blocker) => ({
    issueId: blocker.findingId, topic: blocker.topic, title: blocker.title, severity: 'BLOCKER', status: 'OPEN',
    difference: blocker.difference, claimIds: [...blocker.claimIds], evidenceRefs: [...blocker.evidenceIds],
    affectedObjectCodes: [...blocker.affectedObjectCodes],
    recommendedResolutionId: retailResolutionOptions[blocker.findingId][0].resolutionId,
    options: clone(retailResolutionOptions[blocker.findingId]),
  }));
  const evidenceIndex = evidence.map((item) => ({
    evidenceId: item.evidenceId,
    sourceId: item.connectionId,
    rootSourceIds: rootSourceIds(item.connectionId, present),
    issueIds: issues.filter((issue) => issue.evidenceRefs.includes(item.evidenceId)).map((issue) => issue.issueId),
    checksum: sha256HexSync(JSON.stringify({ statement: item.statement, locator: item.locator })),
  }));
  const rootIds = new Set(connectionIds.flatMap((connectionId) => rootSourceIds(connectionId, present)));
  const lineageGapCount = retailEvidenceSources.filter((source) => present.has(source.connectionId)
    && source.upstreamConnectionIds?.some((upstreamId) => !present.has(upstreamId))).length;
  return {
    overview: {
      sourceCount: connectionIds.length, rootSourceCount: rootIds.size, evidenceCount: evidence.length,
      claimCount: claims.length, issueCount: issues.length, openIssueCount: issues.length,
      decisionCount: 0, lineageGapCount,
    },
    issues, decisions: [], evidenceIndex,
  };
}

function retailSourceSteps(snapshotIds: string[]) {
  return retailEvidenceSources.flatMap((source) => {
    const matchingSnapshotIds = snapshotIds.filter((snapshotId) => {
      const normalized = snapshotId.toLowerCase();
      return normalized.includes(source.connectionId) || normalized.includes(source.connectionId.replace('retail_', ''));
    });
    return matchingSnapshotIds.length ? [{ sourceId: source.connectionId, snapshotIds: matchingSnapshotIds }] : [];
  });
}

function guanyijiaConnectionIds(snapshotIds: string[]) {
  return guanyijiaEvidenceSources
    .filter((source) => source.status === 'READY' && snapshotIds.some((snapshotId) => {
      const normalized = snapshotId.toLowerCase();
      return snapshotId === source.snapshotIdentity
        || normalized.includes(source.connectionId)
        || normalized.includes(source.connectionId.replace('guanyijia_', ''));
    }))
    .map((source) => source.connectionId);
}

function guanyijiaSourceSteps(snapshotIds: string[]) {
  return guanyijiaEvidenceSources.flatMap((source) => {
    if (source.status !== 'READY') return [];
    const matchingSnapshotIds = snapshotIds.filter((snapshotId) => {
      const normalized = snapshotId.toLowerCase();
      return snapshotId === source.snapshotIdentity
        || normalized.includes(source.connectionId)
        || normalized.includes(source.connectionId.replace('guanyijia_', ''));
    });
    return matchingSnapshotIds.length ? [{ sourceId: source.connectionId, snapshotIds: matchingSnapshotIds }] : [];
  });
}

function assertionSection(topic: string): ModelingDocumentSection {
  if (/SALES|INVENTORY|CONVERSION|RATE|DAY/.test(topic)) return 'METRIC';
  if (/CUSTOMER|PRODUCT|STORE/.test(topic)) return 'OBJECT';
  return 'FIELD';
}

function renderClaimTable(claims: Array<{ sourceId: string; assertion: string; evidenceRefs: string[] }>, empty: string) {
  if (!claims.length) return empty;
  const sourceName = new Map(retailEvidenceSources.map((item) => [item.connectionId, item.displayName]));
  return [
    '| 来源 | 当前可确认陈述 | Evidence |', '| --- | --- | --- |',
    ...claims.map((claim) => `| ${sourceName.get(claim.sourceId) ?? claim.sourceId} | ${claim.assertion} | ${claim.evidenceRefs.join('、')} |`),
  ].join('\n');
}

function markdownTable(headers: string[], rows: string[][], empty: string) {
  if (!rows.length) return empty;
  const cell = (value: string) => value.replace(/\|/g, '\\|').replace(/\n/g, ' ');
  return [
    `| ${headers.join(' | ')} |`, `| ${headers.map(() => '---').join(' | ')} |`,
    ...rows.map((row) => `| ${row.map(cell).join(' | ')} |`),
  ].join('\n');
}

function projectRetailDocument(input: {
  revision: number;
  addedSourceId: string;
  connectionIds: string[];
  claims: typeof retailEvidenceClaims;
  evidenceIds: Set<string>;
  blockers: ReturnType<typeof buildRetailRegistryBlockers>;
  lineageGaps: Array<{ connectionId: string; displayName: string; upstreamConnectionIds?: string[] }>;
}): Pick<ModelingDocumentArtifact, 'sections' | 'assertions'> {
  const sourceNames = input.connectionIds.map((connectionId) =>
    retailEvidenceSources.find((item) => item.connectionId === connectionId)?.displayName ?? connectionId);
  const supported = (item: { evidence_ids: string[] }) => item.evidence_ids.length > 0
    && item.evidence_ids.every((evidenceId) => input.evidenceIds.has(evidenceId));
  const model = groupRetailDocument.artifacts.model;
  const entities = model.entity_tables.filter(supported);
  const events = model.event_tables.filter(supported);
  const tables = [...entities, ...events];
  const tableIds = new Set(tables.map((table) => table.table_id));
  const tableNames = new Map(tables.map((table) => [table.table_id, table.name]));
  const fields = tables.flatMap((table) => table.fields.filter(supported).map((field) => ({ table, field })));
  const relations = model.relationships.filter((relation) => supported(relation)
    && tableIds.has(relation.from_table) && tableIds.has(relation.to_table));
  const metrics = model.metrics.filter((metric) => supported(metric) && tableIds.has(metric.event_table));
  const dimensions = groupRetailDocument.artifacts.enrichment.dimensions.filter(supported);
  const questionTopics = [...new Set(input.claims.filter((claim) =>
    ['NET_SALES', 'INVENTORY', 'BOT_FILTER', 'BUSINESS_DAY'].includes(claim.topic)).map((claim) => claim.topic))];
  const unresolved = [
    ...input.blockers.map((item, index) => `${index + 1}. [阻断] ${item.title}：${item.difference}`),
    ...input.lineageGaps.map((item, index) => `${input.blockers.length + index + 1}. [血缘缺口] ${item.displayName}缺少上游${(item.upstreamConnectionIds ?? []).join('、')}`),
  ];
  const sections: ModelingDocumentArtifact['sections'] = {
    OVERVIEW: [
      `- 自动修订：r${input.revision}`, `- 本次新增：${retailEvidenceSources.find((item) => item.connectionId === input.addedSourceId)?.displayName ?? input.addedSourceId}`,
      `- 当前来源（${sourceNames.length}）：${sourceNames.join(' → ')}`, `- 当前Claim：${input.claims.length}项；开放阻断：${input.blockers.length}项。`,
      '- 派生来源只保留血缘，不重复计算为独立佐证。',
    ].join('\n'),
    GOAL: `只使用当前 revision 已接入来源的证据，逐步确认零售销售、库存、客户和时间口径；缺席来源不产生陈述。\n\n${renderClaimTable(input.claims, '当前尚无可用Claim。')}`,
    OBJECT: markdownTable(['业务对象', '定义', 'Evidence'], entities.map((table) => [table.name, table.definition, table.evidence_ids.join('、')]), '当前来源尚不足以确认业务对象；保留到后续 revision。'),
    ACTIVITY: markdownTable(['业务活动', '粒度', '定义', 'Evidence'], events.map((table) => [table.name, table.grain, table.definition, table.evidence_ids.join('、')]), '当前来源尚不足以确认业务活动；保留到后续 revision。'),
    FIELD: markdownTable(['所属对象', '字段', '数据类型', '说明', '字段角色', 'Evidence'], fields.map(({ table, field }) => [table.name, field.name, field.data_type, field.description, field.field_role, field.evidence_ids.join('、')]), '当前来源尚不足以确认字段或维度；保留到后续 revision。'),
    RELATION: markdownTable(['关系', '起点', '终点', '连接依据', 'Evidence'], relations.map((relation) => [
      relation.name, tableNames.get(relation.from_table) ?? relation.from_table,
      tableNames.get(relation.to_table) ?? relation.to_table,
      `${relation.from_field} → ${relation.to_field}`, relation.evidence_ids.join('、'),
    ]), '当前来源尚不足以确认对象关系；保留到后续 revision。'),
    METRIC: markdownTable(['指标', '业务定义', '公式', '聚合', '所属活动', 'Evidence'], metrics.map((metric) => [
      metric.name, metric.definition, metric.formula, metric.aggregation,
      tableNames.get(metric.event_table) ?? metric.event_table, metric.evidence_ids.join('、'),
    ]), '当前来源尚不足以确认指标口径；保留到后续 revision。'),
    QUESTION: questionTopics.length
      ? questionTopics.map((topic, index) => `${index + 1}. 围绕 ${topic} 的业务分析应如何使用当前已确认口径？`).join('\n')
      : '当前来源尚不足以生成可追溯的示例问题。',
    UNRESOLVED: unresolved.join('\n') || '当前来源组合没有跨来源阻断或血缘缺口；后续来源到达后继续复核。',
  };
  const assertions: ModelingDocumentArtifact['assertions'] = [
    ...entities.map((table) => ({ assertionId: `retail-entity-${table.table_id}`, section: 'OBJECT' as const,
      statement: `业务对象“${table.name}”：${table.definition}`, provenance: 'OBSERVED' as const, evidenceRefs: [...table.evidence_ids] })),
    ...events.map((table) => ({ assertionId: `retail-event-${table.table_id}`, section: 'ACTIVITY' as const,
      statement: `业务活动“${table.name}”：${table.definition}`, provenance: 'OBSERVED' as const, evidenceRefs: [...table.evidence_ids] })),
    ...fields.map(({ table, field }) => ({ assertionId: `retail-field-${table.table_id}-${field.field_id}`, section: 'FIELD' as const,
      statement: `${table.name}.${field.name}：${field.description}`, provenance: 'OBSERVED' as const, evidenceRefs: [...field.evidence_ids] })),
    ...relations.map((relation) => ({ assertionId: `retail-relation-${relation.relationship_id}`, section: 'RELATION' as const,
      statement: `关系“${relation.name}”连接${tableNames.get(relation.from_table)}与${tableNames.get(relation.to_table)}`, provenance: 'OBSERVED' as const, evidenceRefs: [...relation.evidence_ids] })),
    ...metrics.map((metric) => ({ assertionId: `retail-metric-${metric.metric_id}`, section: 'METRIC' as const,
      statement: `指标“${metric.name}”：${metric.definition}`, provenance: 'OBSERVED' as const, evidenceRefs: [...metric.evidence_ids] })),
    ...dimensions.map((dimension) => ({ assertionId: `retail-dimension-${dimension.dimension_id}`, section: 'FIELD' as const,
      statement: `维度候选“${dimension.name}”映射到${dimension.target_id}`, provenance: 'OBSERVED' as const, evidenceRefs: [...dimension.evidence_ids] })),
    ...input.claims.map((claim) => ({ assertionId: claim.claimId, section: assertionSection(claim.topic), statement: claim.assertion,
      provenance: claim.authority === 'DERIVED' ? 'INFERRED' as const : 'OBSERVED' as const, evidenceRefs: [...claim.evidenceRefs] })),
  ];
  return { sections, assertions };
}

async function loadRegistryEvidence(evidenceIds: string[]): Promise<ReviewEvidence[]> {
  return Promise.all(evidenceIds.map(async (evidenceId) => {
    const entry = retailEvidenceEntries.find((item) => item.evidenceId === evidenceId);
    if (entry) {
      const present = new Set(retailEvidenceSources.map((item) => item.connectionId));
      return {
        evidenceId, sourceId: entry.connectionId, rootSourceIds: rootSourceIds(entry.connectionId, present),
        statement: entry.statement, locator: clone(entry.locator) as unknown as Record<string, unknown>,
        checksum: await sha256(JSON.stringify({ statement: entry.statement, locator: entry.locator })),
      };
    }
    return guanyijiaReviewEvidence(evidenceId);
  }));
}

export function createModelingDocumentRuntime(options: {
  storage: StorageLike;
  now?: () => string;
  loadReviewEvidence?: (evidenceIds: string[]) => Promise<ReviewEvidence[]>;
}): ModelingDocumentRuntime {
  const now = options.now ?? (() => new Date().toISOString());
  const persist = (state: StoredState) => options.storage.setItem(storageKey, JSON.stringify(state));
  const find = (state: StoredState, artifactId: string) => {
    const artifact = artifactsWithBaselines(state).find((item) => item.artifactId === artifactId);
    if (!artifact) throw new Error('标准建模文档不存在');
    return artifact;
  };
  return {
    async list(projectId) { return clone(artifactsWithBaselines(load(options.storage)).filter((item) => item.projectId === projectId)); },
    async read(artifactId) { return clone(artifactsWithBaselines(load(options.storage)).find((item) => item.artifactId === artifactId) ?? null); },
    async review(artifactId) {
      const artifact = find(load(options.storage), artifactId);
      return createReviewQueryService({
        artifactId: artifact.artifactId, revision: artifact.revision, snapshot: buildReviewSnapshot(artifact),
        loadEvidence: options.loadReviewEvidence
          ?? (artifact.semanticPayload?.data.evidenceContext
            ? async (evidenceIds) => evidenceIds.map((evidenceId) => payloadReviewEvidence(artifact, evidenceId))
            : loadRegistryEvidence),
      });
    },
    async execute(command: ModelingDocumentCommand) {
      const state = load(options.storage);
      if (command.type === 'REGISTER_MANUAL_DOCUMENT') {
        const parsed = parseNineSectionMarkdown(command.content);
        const createdAt = now(); const sequence = state.sequence + 1;
        const digest = await sha256(command.content);
        const existing = state.artifacts.find((item) => item.projectId === command.projectId
          && item.origin === 'MANUAL_UPLOAD' && item.markdown.sha256 === digest);
        if (existing) return { artifact: clone(existing) };
        const artifact: ModelingDocumentArtifact = {
          artifactId: `modeling-document-${command.projectId}-${sequence}`,
          revision: 1, projectId: command.projectId, documentCode: command.documentCode,
          title: parsed.title, origin: 'MANUAL_UPLOAD', sections: parsed.sections, assertions: [],
          markdown: { fileName: command.fileName, content: command.content, sha256: digest, byteLength: new TextEncoder().encode(command.content).byteLength },
          validation: { errors: [], warnings: [], gaps: [] }, status: 'FROZEN',
          audit: [{ action: 'MANUAL_UPLOADED', actorUserId: command.actorUserId, at: createdAt, detail: '人工上传的九段Markdown已完成结构校验' }],
          createdAt, updatedAt: createdAt,
        };
        state.sequence = sequence; state.artifacts.push(artifact); persist(state);
        return { artifact: clone(artifact) };
      }
      if (command.type === 'GENERATE_DOCUMENT') {
        const retailSteps = command.projectId === 'group_retail_ops'
          ? retailSourceSteps(command.sourceBatch.snapshotIds) : [];
        const guanyijiaSteps = command.projectId === 'guanyijia_erp'
          ? guanyijiaSourceSteps(command.sourceBatch.snapshotIds) : [];
        const plannedSteps = retailSteps.length ? retailSteps : guanyijiaSteps;
        const steps = plannedSteps.length ? plannedSteps : [{ sourceId: command.projectId, snapshotIds: command.sourceBatch.snapshotIds }];
        const acceptedSnapshotIds = steps.flatMap((step) => step.snapshotIds);
        const acceptedFingerprint = guanyijiaSteps.length
          ? await sha256(JSON.stringify({ snapshotIds: acceptedSnapshotIds }))
          : acceptedSnapshotIds.length === command.sourceBatch.snapshotIds.length
          ? command.sourceBatch.fingerprint
          : await sha256(JSON.stringify({ batchFingerprint: command.sourceBatch.fingerprint, snapshotIds: acceptedSnapshotIds }));
        const existing = state.artifacts
          .filter((item) => item.projectId === command.projectId
            && item.documentCode === command.documentCode && item.origin === 'STANDARDIZATION'
            && item.sourceBatch?.fingerprint === acceptedFingerprint
            && item.sourceBatch?.snapshotIds.length === acceptedSnapshotIds.length
            && item.audit.at(-1)?.action === 'GENERATED')
          .sort((left, right) => right.revision - left.revision)[0];
        if (existing) return { artifact: clone(existing) };
        let previous: ModelingDocumentArtifact | undefined;
        const cumulativeSnapshotIds: string[] = [];
        let previousEvidenceIds = new Set<string>();
        let previousClaimIds = new Set<string>();
        for (const [index, step] of steps.entries()) {
          cumulativeSnapshotIds.push(...step.snapshotIds);
          const fixture = modelingDocumentFixture(command.projectId, cumulativeSnapshotIds);
          const connectionIds = command.projectId === 'group_retail_ops' ? retailConnectionIds(cumulativeSnapshotIds) : [];
          const present = new Set(connectionIds);
          const retailEvidence = retailEvidenceEntries.filter((item) => present.has(item.connectionId));
          const retailEvidenceIds = new Set(retailEvidence.map((item) => item.evidenceId));
          const retailClaims = retailEvidenceClaims.filter((claim) => present.has(claim.sourceId)
            && claim.evidenceRefs.every((evidenceId) => retailEvidenceIds.has(evidenceId)));
          const guanyijiaPresent = new Set(guanyijiaConnectionIds(cumulativeSnapshotIds));
          const guanyijiaClaims = buildGuanyijiaEvidenceStory().at(-1)!.claims.filter((claim) => guanyijiaPresent.has(claim.sourceId));
          const claims = retailSteps.length ? retailClaims : guanyijiaClaims;
          const evidenceIds = retailSteps.length ? retailEvidenceIds : new Set(claims.flatMap((claim) => claim.evidenceRefs));
          const claimIds = new Set(claims.map((claim) => claim.claimId));
          const blockers = buildRetailRegistryBlockers(connectionIds, 2);
          const lineageGaps = retailEvidenceSources.filter((source) => present.has(source.connectionId)
            && source.upstreamConnectionIds?.some((upstreamId) => !present.has(upstreamId)));
          const revision = index + 1;
          const createdAt = now(); const sequence = state.sequence + 1;
          const artifactId = `modeling-document-${command.projectId}-${sequence}`;
          const source = [...retailEvidenceSources, ...guanyijiaEvidenceSources].find((item) => item.connectionId === step.sourceId);
          const retailProjection = retailSteps.length ? projectRetailDocument({
            revision, addedSourceId: step.sourceId, connectionIds, claims: retailClaims, evidenceIds: retailEvidenceIds,
            blockers, lineageGaps,
          }) : null;
          const sections = retailProjection ? retailProjection.sections : clone(fixture.sections);
          if (!retailSteps.length && plannedSteps.length) {
            const openIssueCount = guanyijiaPresent.has('guanyijia_mysql') && guanyijiaPresent.has('guanyijia_github') ? 3 : 0;
            sections.OVERVIEW = `${sections.OVERVIEW}\n\n- 自动修订：r${revision}，本次新增来源“${source?.displayName ?? step.sourceId}”\n- 累积事实：${evidenceIds.size}条建模证据、${claims.length}项Claim、${openIssueCount}项开放问题。`;
          }
          const prefixFingerprint = revision === steps.length
            ? acceptedFingerprint
            : await sha256(JSON.stringify({ batchFingerprint: acceptedFingerprint, snapshotIds: cumulativeSnapshotIds }));
          const artifact = await materialize({
            artifactId, revision, ...(previous ? { derivedFromArtifactId: previous.artifactId } : {}),
            projectId: command.projectId, documentCode: command.documentCode,
            title: command.title, origin: 'STANDARDIZATION',
            sourceBatch: { batchId: command.sourceBatch.batchId, fingerprint: prefixFingerprint, snapshotIds: clone(cumulativeSnapshotIds) },
            delta: {
              kind: 'SOURCE_ACCUMULATION', addedSourceIds: [step.sourceId], addedSnapshotIds: clone(step.snapshotIds),
              addedEvidenceRefs: [...evidenceIds].filter((evidenceId) => !previousEvidenceIds.has(evidenceId)).sort(),
              addedClaimIds: [...claimIds].filter((claimId) => !previousClaimIds.has(claimId)).sort(), resolvedIssueIds: [],
            },
            sections,
            assertions: retailProjection?.assertions ?? fixture.assertions,
            generation: { runId: `agent-run-${sequence}`, agentVersion: 'standardization-agents/1', promptVersion: 'modeling-document/2', inputFingerprint: prefixFingerprint },
            approval: { authorUserId: command.actorUserId },
            validation: {
              errors: [],
              warnings: retailSteps.length ? blockers.map((item) => ({ code: 'SOURCE_CONFLICT', message: item.title, refs: item.evidenceIds })) : fixture.warnings,
              gaps: retailSteps.length ? lineageGaps.map((item) => ({
                code: 'LINEAGE_GAP', message: `${item.displayName} 缺少已接入的上游来源`, refs: item.upstreamConnectionIds,
              })) : fixture.gaps,
            },
            status: 'AWAITING_CONFIRMATION',
            audit: [{ action: 'GENERATED', actorUserId: command.actorUserId, at: createdAt, detail: `来源累积形成标准建模资料 r${revision}` }],
            createdAt, updatedAt: createdAt,
          });
          artifact.review = buildReviewSnapshot(artifact);
          state.sequence = sequence; state.artifacts.push(artifact);
          previous = artifact; previousEvidenceIds = evidenceIds; previousClaimIds = claimIds;
        }
        persist(state);
        return { artifact: clone(previous!) };
      }
      const current = find(state, command.artifactId);
      if (command.type === 'IMPORT_TO_AI') {
        if (current.status !== 'FROZEN') throw new Error('只有冻结文档可以交给AI建模');
        if (current.markdown.sha256 !== command.markdownSha256) throw new Error('Markdown校验和不一致');
        assertModelingDocumentIntegrity(current);
        let record = state.imports.find((item) => item.artifactId === current.artifactId && item.markdownSha256 === command.markdownSha256);
        if (!record) {
          record = { importId: `modeling-import-${state.imports.length + 1}`, artifactId: current.artifactId, markdownSha256: command.markdownSha256, importedAt: now() };
          state.imports.push(record); persist(state);
        }
        return { artifact: clone(current), importRecord: clone(record) };
      }
      if (current.status === 'FROZEN') throw new Error('冻结文档不可修改');
      if (current.revision !== command.expectedRevision) throw new Error('文档修订已变化，请刷新后重试');
      if (command.type === 'UPDATE_SECTION') {
        const createdAt = now(); const sequence = state.sequence + 1;
        const sections = { ...current.sections, [command.section as ModelingDocumentSection]: command.content };
        const next = await materialize({
          ...clone(current), artifactId: `modeling-document-${current.projectId}-${sequence}`,
          revision: current.revision + 1, derivedFromArtifactId: current.artifactId, sections,
          status: 'AWAITING_CONFIRMATION',
          approval: { authorUserId: current.approval?.authorUserId ?? command.actorUserId },
          audit: [...current.audit, { action: 'SECTION_UPDATED', actorUserId: command.actorUserId, at: createdAt, detail: `已修订章节 ${command.section}` }],
          updatedAt: createdAt,
        });
        state.sequence = sequence; state.artifacts.push(next); persist(state);
        return { artifact: clone(next) };
      }
      if (command.type === 'RECORD_DECISIONS') {
        if (current.status !== 'AWAITING_CONFIRMATION') throw new Error('只有待作者确认的修订可以记录决定');
        const authorUserId = current.approval?.authorUserId ?? current.audit.find((item) => item.action === 'GENERATED')?.actorUserId;
        if (command.actorUserId !== authorUserId) throw new Error('只有文档作者可以记录人工决定');
        if (!command.decisions.length) throw new Error('至少记录一项人工决定');
        const review = buildReviewSnapshot(current);
        const issueById = new Map(review.issues.map((item) => [item.issueId, item]));
        const openBlockingIssueIds = review.issues.filter((item) => item.status === 'OPEN'
          && ['BLOCKER', 'ERROR', 'GAP'].includes(item.severity)).map((item) => item.issueId).sort();
        const providedIssueIds = [...new Set(command.decisions.map((item) => item.issueId))];
        const missingBlockingIssueIds = openBlockingIssueIds.filter((issueId) => !providedIssueIds.includes(issueId));
        if (missingBlockingIssueIds.length) {
          throw new Error(`必须一次决定当前全部${openBlockingIssueIds.length}项阻断问题`);
        }
        const seen = new Set<string>();
        const decisions = command.decisions.map((input, index) => {
          if (seen.has(input.issueId)) throw new Error(`人工决定重复：${input.issueId}`);
          seen.add(input.issueId);
          const issue = issueById.get(input.issueId);
          if (!issue) throw new Error(`审核问题不存在：${input.issueId}`);
          const option = issue.options.find((item) => item.resolutionId === input.resolutionId);
          if (!option) throw new Error(`决定选项不属于审核问题：${input.issueId}`);
          if (!input.reason.trim()) throw new Error(`请填写决定理由：${input.issueId}`);
          return {
            decisionId: `decision:${current.artifactId}:${index + 1}`, issueId: issue.issueId,
            resolutionId: option.resolutionId, conclusion: option.conclusion, reason: input.reason.trim(),
            actorUserId: command.actorUserId, decidedAt: now(), evidenceRefs: clone(issue.evidenceRefs),
          };
        });
        const nextDecisions = [
          ...review.decisions.filter((item) => !seen.has(item.issueId)), ...decisions,
        ].sort((left, right) => left.issueId.localeCompare(right.issueId));
        const decidedIssueIds = new Set(nextDecisions.map((item) => item.issueId));
        const issues = review.issues.map((item) => ({ ...clone(item), status: decidedIssueIds.has(item.issueId) ? 'DECIDED' as const : item.status }));
        const updatedReview: ReviewSnapshot = {
          ...clone(review), issues, decisions: nextDecisions,
          overview: {
            ...review.overview,
            openIssueCount: issues.filter((item) => item.status === 'OPEN').length,
            decisionCount: nextDecisions.length,
          },
        };
        const sections = clone(current.sections);
        const decidedLines = issues.map((issue) => {
          const decision = nextDecisions.find((item) => item.issueId === issue.issueId);
          return decision
            ? `[已决定] ${issue.title}：${decision.conclusion} 理由：${decision.reason}`
            : `[待决定] ${issue.title}：${issue.difference}`;
        });
        const remainingPendingLines = current.sections.UNRESOLVED.split('\n')
          .map((line) => line.replace(/^\s*(?:[-*]|\d+[.)])\s*/, '').trim())
          .filter(Boolean)
          .filter((line) => !issues.some((issue) => line.includes(issue.title)
            || (issue.topic === 'DEBT_FIELDS' && /debt|欠款/i.test(line))
            || (issue.topic === 'EXTENSION_TABLES' && /扩展表/.test(line))
            || (issue.topic === 'STATUS_NINE' && /状态9|常量/.test(line))))
          .map((line) => `[待确认] ${line.replace(/^\[(?:待确认|待决定|已决定)\]\s*/, '')}`);
        sections.UNRESOLVED = [...decidedLines, ...remainingPendingLines]
          .map((line, index) => `${index + 1}. ${line}`).join('\n') || '当前没有待确认事项。';
        const createdAt = now(); const sequence = state.sequence + 1;
        const resolvedIssueIds = decisions.map((item) => item.issueId).sort();
        const next = await materialize({
          ...clone(current), artifactId: `modeling-document-${current.projectId}-${sequence}`,
          revision: current.revision + 1, derivedFromArtifactId: current.artifactId,
          delta: { kind: 'DECISION', addedSourceIds: [], addedSnapshotIds: [], addedEvidenceRefs: [], addedClaimIds: [], resolvedIssueIds },
          sections, review: updatedReview,
          assertions: [
            ...current.assertions,
            ...decisions.map((decision) => ({
              assertionId: `decision-${decision.issueId}`, section: 'UNRESOLVED' as const,
              statement: decision.conclusion, provenance: 'USER_CONFIRMED' as const, evidenceRefs: clone(decision.evidenceRefs),
            })),
          ],
          status: 'AWAITING_CONFIRMATION', approval: { authorUserId: authorUserId! },
          audit: [...current.audit, { action: 'DECISIONS_RECORDED', actorUserId: command.actorUserId, at: createdAt, detail: `记录${decisions.length}项人工决定` }],
          createdAt, updatedAt: createdAt,
        });
        state.sequence = sequence; state.artifacts.push(next); persist(state);
        return { artifact: clone(next) };
      }
      if (!command.reason.trim()) throw new Error('请填写决定理由');
      const transitionAt = now();
      const authorUserId = current.approval?.authorUserId ?? current.audit.find((item) => item.action === 'GENERATED')?.actorUserId;
      if (command.type === 'CONFIRM_AUTHOR') {
        if (current.status !== 'AWAITING_CONFIRMATION') throw new Error('当前文档不在作者确认阶段');
        if (command.actorUserId !== authorUserId) throw new Error('只有文档作者可以确认');
        const openBlockingIssues = buildReviewSnapshot(current).issues.filter((item) => item.status === 'OPEN'
          && ['BLOCKER', 'ERROR', 'GAP'].includes(item.severity));
        if (openBlockingIssues.length) throw new Error(`仍有${openBlockingIssues.length}项阻断问题未决定`);
        const updated = await materialize({
          ...clone(current), status: 'AWAITING_REVIEW', updatedAt: transitionAt,
          approval: {
            authorUserId: authorUserId!,
            authorConfirmation: { actorUserId: command.actorUserId, at: transitionAt, reason: command.reason.trim(), markdownSha256: current.markdown.sha256 },
          },
          audit: [...current.audit, { action: 'AUTHOR_CONFIRMED', actorUserId: command.actorUserId, at: transitionAt, detail: command.reason.trim() }],
        });
        state.artifacts = state.artifacts.map((item) => item.artifactId === updated.artifactId ? updated : item); persist(state);
        return { artifact: clone(updated) };
      }
      if (command.type === 'APPROVE_DOCUMENT') {
        if (current.status !== 'AWAITING_REVIEW' || !current.approval?.authorConfirmation) throw new Error('作者尚未确认');
        if (command.actorUserId === authorUserId) throw new Error('审核批准人与作者不能相同');
        if (current.approval.authorConfirmation.markdownSha256 !== current.markdown.sha256) throw new Error('作者确认的Markdown校验和已变化');
        const updated = await materialize({
          ...clone(current), status: 'APPROVED', updatedAt: transitionAt,
          approval: {
            ...clone(current.approval),
            reviewerApproval: { actorUserId: command.actorUserId, at: transitionAt, reason: command.reason.trim(), markdownSha256: current.markdown.sha256 },
          },
          audit: [...current.audit, { action: 'REVIEW_APPROVED', actorUserId: command.actorUserId, at: transitionAt, detail: command.reason.trim() }],
        });
        state.artifacts = state.artifacts.map((item) => item.artifactId === updated.artifactId ? updated : item); persist(state);
        return { artifact: clone(updated) };
      }
      if (command.type === 'FREEZE_DOCUMENT') {
        if (!current.approval?.authorConfirmation) throw new Error('作者尚未确认');
        if (!current.approval.reviewerApproval || current.status !== 'APPROVED') throw new Error('审核尚未批准');
        if (current.approval.authorConfirmation.markdownSha256 !== current.markdown.sha256
          || current.approval.reviewerApproval.markdownSha256 !== current.markdown.sha256) {
          throw new Error('确认或审核的Markdown校验和已变化');
        }
        if (await sha256(current.markdown.content) !== current.markdown.sha256) throw new Error('Markdown校验和不一致');
        assertStandardModelingMarkdown(current.markdown.content);
        const emptySection = Object.entries(current.sections).find(([, content]) => !content.trim());
        if (emptySection) throw new Error(`章节未就绪：${emptySection[0]}`);
        if (current.validation.errors.length) throw new Error(`仍有${current.validation.errors.length}项校验错误`);
        const review = buildReviewSnapshot(current);
        if (review.overview.lineageGapCount) throw new Error(`仍有${review.overview.lineageGapCount}项来源血缘缺口`);
        if (current.validation.gaps.length) throw new Error(`仍有${current.validation.gaps.length}项证据缺口`);
        const openBlockingIssues = review.issues.filter((item) => item.status === 'OPEN'
          && ['BLOCKER', 'ERROR', 'GAP'].includes(item.severity));
        if (openBlockingIssues.length) throw new Error(`仍有${openBlockingIssues.length}项阻断问题未决定`);
        const evidenceIds = review.evidenceIndex.map((item) => item.evidenceId);
        const evidence = evidenceIds.length
          ? await (options.loadReviewEvidence ?? loadRegistryEvidence)(evidenceIds) : [];
        const evidenceById = new Map(evidence.map((item) => [item.evidenceId, item]));
        for (const index of review.evidenceIndex) {
          const item = evidenceById.get(index.evidenceId);
          if (!item) throw new Error(`证据缺失：${index.evidenceId}`);
          if (!item.locator || Object.keys(item.locator).length === 0) throw new Error(`证据Locator为空：${index.evidenceId}`);
          if (!item.rootSourceIds.length) throw new Error(`证据血缘缺少根来源：${index.evidenceId}`);
          if (item.sourceId !== index.sourceId) throw new Error(`证据来源与索引不一致：${index.evidenceId}`);
          if (JSON.stringify([...item.rootSourceIds].sort()) !== JSON.stringify([...index.rootSourceIds].sort())) {
            throw new Error(`证据根来源与索引不一致：${index.evidenceId}`);
          }
          const contentChecksum = await sha256(JSON.stringify({ statement: item.statement, locator: item.locator }));
          if (item.checksum !== contentChecksum) throw new Error(`证据内容Checksum不一致：${index.evidenceId}`);
          if (item.checksum !== index.checksum) throw new Error(`证据Checksum与来源索引不一致：${index.evidenceId}`);
        }
        const updated = await materialize({
          ...clone(current), status: 'FROZEN', updatedAt: transitionAt,
          audit: [...current.audit, { action: 'FROZEN', actorUserId: command.actorUserId, at: transitionAt, detail: command.reason.trim() }],
        });
        state.artifacts = state.artifacts.map((item) => item.artifactId === updated.artifactId ? updated : item); persist(state);
        return { artifact: clone(updated) };
      }
      const updated = await materialize({
        ...clone(current), status: 'REJECTED', updatedAt: transitionAt,
        audit: [...current.audit, { action: 'REJECTED', actorUserId: command.actorUserId, at: transitionAt, detail: command.reason.trim() }],
      });
      state.artifacts = state.artifacts.map((item) => item.artifactId === updated.artifactId ? updated : item); persist(state);
      return { artifact: clone(updated) };
    },
  };
}
