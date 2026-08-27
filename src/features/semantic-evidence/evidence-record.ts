import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { EvidenceRecord } from './types.ts';

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.entries(value as Record<string, unknown>).sort(([left], [right]) => left.localeCompare(right)).map(([key, item]) => `${JSON.stringify(key)}:${stable(item)}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

export function evidenceRecordChecksum(record: Omit<EvidenceRecord, 'checksum'>) {
  return sha256HexSync(stable(record));
}

export function semanticStatementId(record: Pick<EvidenceRecord, 'snapshotId' | 'statement'>) {
  return `stmt-${sha256HexSync(stable(record)).slice(0, 20)}`;
}

export function createEvidenceRecord(record: Omit<EvidenceRecord, 'checksum' | 'statementId'>): EvidenceRecord {
  const signed = { ...record, statementId: semanticStatementId(record) };
  return { ...signed, checksum: evidenceRecordChecksum(signed) };
}
