import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { evidenceRecordChecksum } from './evidence-record.ts';
import type {
  EvidenceRecord, PredicateMapping, SemanticCandidate, SemanticCandidateKind,
  SemanticEvidenceClaim, SemanticEvidenceCompilationInput, SemanticEvidencePackage,
  SemanticReconciliationFinding, UnknownPredicate,
} from './types.ts';

const candidateKinds = new Set<SemanticCandidateKind>(['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'HIERARCHY', 'RULE', 'TIME_SEMANTIC']);
const authorityRank = { PRIMARY: 4, CORROBORATING: 3, DERIVED: 2, AUXILIARY: 1 } as const;

function termValue(value: { kind: string; value: string }) { return value.value; }
function localName(uri: string) { return decodeURIComponent(uri.split(/[\/#:]/).filter(Boolean).at(-1) ?? uri); }
function snake(value: string) { return value.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').toLowerCase(); }
function mappingFor(predicate: string, mappings: PredicateMapping[]) { return mappings.find((item) => item.predicate === predicate || item.semantic === predicate); }

function assertLocator(record: EvidenceRecord) {
  const locator = record.locator as unknown as Record<string, unknown>;
  if (!locator || typeof locator.kind !== 'string') throw new Error(`证据 ${record.evidenceId} 缺少有效Locator`);
  if (record.locator.kind === 'RDF' && (!record.locator.graphUri || !record.locator.subject || !record.locator.predicate)) throw new Error(`证据 ${record.evidenceId} 的RDF定位不完整`);
}

function validateRecords(records: EvidenceRecord[]) {
  const byId = new Map<string, EvidenceRecord>();
  for (const record of records) {
    if (byId.has(record.evidenceId)) throw new Error(`证据编码重复：${record.evidenceId}`);
    const { checksum: _checksum, ...unsigned } = record;
    if (record.checksum !== evidenceRecordChecksum(unsigned)) throw new Error(`证据校验和不一致：${record.evidenceId}`);
    assertLocator(record); byId.set(record.evidenceId, record);
  }
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const visit = (id: string) => {
    if (visiting.has(id)) throw new Error(`证据血缘存在循环：${id}`);
    if (visited.has(id)) return;
    const record = byId.get(id); if (!record) throw new Error(`上游证据不存在：${id}`);
    visiting.add(id); (record.upstreamEvidenceIds ?? []).forEach(visit); visiting.delete(id); visited.add(id);
  };
  records.forEach((record) => visit(record.evidenceId));
  return byId;
}

function normalized(record: EvidenceRecord, mappings: PredicateMapping[]) {
  if (record.statement.kind === 'ASSERTION') {
    const source = record.statement.assertion;
    const mapping = mappingFor(source.predicate, mappings);
    return mapping ? { subject: source.subject, predicate: mapping.semantic, predicateLabel: mapping.label, value: String(source.value), mapping } : null;
  }
  const quad = record.statement.quad;
  if (quad.subject.kind === 'LITERAL' || quad.predicate.kind !== 'IRI') return null;
  const mapping = mappingFor(quad.predicate.value, mappings);
  return mapping ? { subject: termValue(quad.subject), predicate: mapping.semantic, predicateLabel: mapping.label, value: termValue(quad.object), mapping } : null;
}

function roots(record: EvidenceRecord, byId: Map<string, EvidenceRecord>, path = new Set<string>()): string[] {
  if (path.has(record.evidenceId)) return [];
  if (!record.upstreamEvidenceIds?.length) return [record.connectionId];
  const next = new Set(path); next.add(record.evidenceId);
  return [...new Set(record.upstreamEvidenceIds.flatMap((id) => {
    const parent = byId.get(id); return parent ? roots(parent, byId, next) : [];
  }))].sort();
}

function buildClaims(records: EvidenceRecord[], byId: Map<string, EvidenceRecord>, mappings: PredicateMapping[]) {
  const groups = new Map<string, { subject: string; predicate: string; predicateLabel: string; value: string; records: EvidenceRecord[] }>();
  for (const record of records) {
    const value = normalized(record, mappings); if (!value) continue;
    const key = `${value.subject}\u0000${value.predicate}\u0000${value.value}`;
    const group = groups.get(key) ?? { ...value, records: [] }; group.records.push(record); groups.set(key, group);
  }
  return [...groups.values()].map<SemanticEvidenceClaim>((group) => {
    const rootConnectionIds = [...new Set(group.records.flatMap((record) => roots(record, byId)))].sort();
    const authority = [...group.records].sort((a, b) => authorityRank[b.authority] - authorityRank[a.authority])[0]!.authority;
    const derivedOnly = group.records.every((record) => ['DERIVED', 'AUXILIARY'].includes(record.authority));
    return {
      claimId: `claim-${sha256HexSync(`${group.subject}\n${group.predicate}\n${group.value}`).slice(0, 16)}`,
      subject: group.subject, predicate: group.predicate, predicateLabel: group.predicateLabel, value: group.value,
      authority, evidenceRefs: group.records.map((record) => record.evidenceId).sort(),
      connectionIds: [...new Set(group.records.map((record) => record.connectionId))].sort(), rootConnectionIds,
      independentSourceCount: rootConnectionIds.length,
      support: derivedOnly ? 'DERIVED_ONLY' : rootConnectionIds.length > 1 ? 'MULTI_SOURCE' : 'SINGLE_SOURCE',
    };
  }).sort((left, right) => `${left.subject}|${left.predicate}|${left.value}`.localeCompare(`${right.subject}|${right.predicate}|${right.value}`));
}

function buildFindings(claims: SemanticEvidenceClaim[], records: EvidenceRecord[], mappings: PredicateMapping[]): SemanticReconciliationFinding[] {
  const recordById = new Map(records.map((record) => [record.evidenceId, record]));
  const groups = new Map<string, SemanticEvidenceClaim[]>();
  claims.forEach((claim) => { const key = `${claim.subject}|${claim.predicate}`; groups.set(key, [...(groups.get(key) ?? []), claim]); });
  return [...groups.entries()].map(([semanticKey, items]) => {
    const mapping = mappingFor(items[0]!.predicate, mappings);
    const values = [...new Set(items.map((item) => item.value))];
    const recordGroups = new Map<string, EvidenceRecord[]>();
    items.forEach((claim) => claim.evidenceRefs.forEach((id) => {
      const record = recordById.get(id); if (record) recordGroups.set(record.connectionId, [...(recordGroups.get(record.connectionId) ?? []), record]);
    }));
    const participants = [...recordGroups.entries()].map(([connectionId, sourceRecords]) => ({
      connectionId, connectionName: sourceRecords[0]!.connectionName,
      authority: [...sourceRecords].sort((a, b) => authorityRank[b.authority] - authorityRank[a.authority])[0]!.authority,
      claimIds: items.filter((claim) => claim.connectionIds.includes(connectionId)).map((claim) => claim.claimId),
      assertion: [...new Set(items.filter((claim) => claim.connectionIds.includes(connectionId)).map((claim) => claim.value))].join('；'),
      evidenceRefs: sourceRecords.map((record) => record.evidenceId).sort(),
    })).sort((left, right) => left.connectionId.localeCompare(right.connectionId));
    const rootsForFinding = [...new Set(items.flatMap((item) => item.rootConnectionIds))];
    const derived = items.every((item) => item.support === 'DERIVED_ONLY');
    const status: SemanticReconciliationFinding['status'] = values.length > 1 ? 'CONFLICT' : derived ? 'DERIVED' : rootsForFinding.length > 1 ? 'CONSISTENT' : 'SINGLE_SOURCE';
    const severity: SemanticReconciliationFinding['severity'] = status === 'CONFLICT' ? mapping?.conflictSeverity ?? 'WEAK' : status === 'CONSISTENT' ? 'CONSISTENT' : 'WEAK';
    return {
      findingId: `finding-${sha256HexSync(semanticKey).slice(0, 16)}`, semanticKey,
      title: `${items[0]!.predicateLabel} · ${localName(items[0]!.subject)}`, status, severity, participants,
      ...(status === 'CONFLICT' ? { difference: values.join(' ↔ ') } : { agreement: values[0] }),
      affectedObjectRefs: [...new Set(mapping?.affectedObjectRefs ?? [])],
    };
  }).sort((left, right) => left.semanticKey.localeCompare(right.semanticKey));
}

function candidateSummary(kind: SemanticCandidateKind, details: Record<string, string>) {
  if (kind === 'FIELD') return `属于 ${details.DOMAIN ? localName(details.DOMAIN) : '待确认对象'}；值类型 ${details.RANGE ? localName(details.RANGE) : '待确认'}`;
  if (kind === 'RELATION') return `${details.DOMAIN ? localName(details.DOMAIN) : '待确认起点'} → ${details.RANGE ? localName(details.RANGE) : '待确认终点'}`;
  if (kind === 'METRIC') return `聚合方式：${details.AGGREGATION ?? '待确认'}`;
  if (kind === 'RULE') return '仅为业务语义规则候选，不可直接执行。';
  if (kind === 'HIERARCHY') return '由成员上下级关系形成的层级候选。';
  return '由当前证据主张生成的语义对象候选。';
}

function buildCandidates(claims: SemanticEvidenceClaim[], findings: SemanticReconciliationFinding[]): SemanticCandidate[] {
  const bySubject = new Map<string, SemanticEvidenceClaim[]>();
  claims.forEach((claim) => bySubject.set(claim.subject, [...(bySubject.get(claim.subject) ?? []), claim]));
  const candidates: SemanticCandidate[] = [];
  for (const [subject, subjectClaims] of bySubject) {
    const kindValue = subjectClaims.find((item) => item.predicate === 'CANDIDATE_KIND')?.value as SemanticCandidateKind | undefined;
    if (!kindValue || !candidateKinds.has(kindValue)) continue;
    const code = snake(localName(subject));
    const name = subjectClaims.find((item) => item.predicate === 'LABEL')?.value ?? localName(subject);
    const details = Object.fromEntries(subjectClaims.filter((item) => !['CANDIDATE_KIND', 'LABEL', 'ALIAS'].includes(item.predicate)).map((item) => [item.predicate, item.value]));
    const ownerCode = details.DOMAIN ? snake(localName(details.DOMAIN)) : undefined;
    const objectRef = kindValue === 'FIELD' && ownerCode ? `FIELD:${ownerCode}:${code}` : `${kindValue}:${code}`;
    const blocking = findings.filter((item) => item.severity === 'BLOCKER' && item.affectedObjectRefs.includes(objectRef));
    const weak = subjectClaims.some((item) => item.support !== 'MULTI_SOURCE');
    candidates.push({
      objectRef, kind: kindValue, code, name, summary: candidateSummary(kindValue, details),
      ...(ownerCode ? { ownerRef: `ENTITY:${ownerCode}` } : {}), status: blocking.length ? 'BLOCKED' : weak ? 'VERIFY' : 'RECOMMENDED',
      claimIds: subjectClaims.map((item) => item.claimId), evidenceRefs: [...new Set(subjectClaims.flatMap((item) => item.evidenceRefs))],
      affectedObjectRefs: [...new Set(blocking.flatMap((item) => item.affectedObjectRefs))], technicalDefinition: details,
    });
    subjectClaims.filter((item) => item.predicate === 'ALIAS').forEach((alias) => candidates.push({
      objectRef: `ALIAS:${code}:${sha256HexSync(alias.value).slice(0, 10)}`, kind: 'ALIAS', code: `${code}_alias`, name: alias.value,
      summary: `${name}的业务称呼。`, ownerRef: objectRef, status: alias.support === 'MULTI_SOURCE' ? 'RECOMMENDED' : 'VERIFY',
      claimIds: [alias.claimId], evidenceRefs: [...alias.evidenceRefs], affectedObjectRefs: [objectRef], technicalDefinition: {},
    }));
  }
  return candidates.sort((left, right) => left.objectRef.localeCompare(right.objectRef));
}

function unknownPredicates(input: SemanticEvidenceCompilationInput): UnknownPredicate[] {
  const mapped = new Set(input.profile.predicateMappings.flatMap((item) => [item.predicate, item.semantic]));
  const excluded = new Set(input.profile.excludedPredicates);
  const groups = new Map<string, { evidenceRefs: string[]; sampleValues: string[] }>();
  for (const record of input.records) {
    const predicate = record.statement.kind === 'RDF_QUAD' && record.statement.quad.predicate.kind === 'IRI'
      ? record.statement.quad.predicate.value : record.statement.kind === 'ASSERTION' ? record.statement.assertion.predicate : '';
    if (!predicate || mapped.has(predicate) || excluded.has(predicate)) continue;
    const value = record.statement.kind === 'RDF_QUAD' ? termValue(record.statement.quad.object) : String(record.statement.assertion.value);
    const group = groups.get(predicate) ?? { evidenceRefs: [], sampleValues: [] };
    group.evidenceRefs.push(record.evidenceId); group.sampleValues.push(value); groups.set(predicate, group);
  }
  return [...groups.entries()].map(([predicate, value]) => ({ predicate, evidenceRefs: [...new Set(value.evidenceRefs)].sort(), sampleValues: [...new Set(value.sampleValues)].slice(0, 3) })).sort((a, b) => a.predicate.localeCompare(b.predicate));
}

export function compileSemanticEvidence(input: SemanticEvidenceCompilationInput): SemanticEvidencePackage {
  const records = structuredClone(input.records);
  const byId = validateRecords(records);
  const claims = buildClaims(records, byId, input.profile.predicateMappings);
  const findings = buildFindings(claims, records, input.profile.predicateMappings);
  const candidates = buildCandidates(claims, findings);
  const fingerprint = sha256HexSync(records.map((record) => record.checksum).sort().join('\n'));
  return {
    schemaVersion: 1, packageId: `${input.exampleId}-${fingerprint.slice(0, 12)}`, exampleId: input.exampleId,
    projectId: input.projectId, title: input.title, fingerprint, records, claims, findings, candidates,
    unknownPredicates: unknownPredicates(input),
    validation: {
      valid: true, warnings: [], recordCount: records.length,
      independentRootSourceCount: new Set(claims.flatMap((claim) => claim.rootConnectionIds)).size,
    },
  };
}
