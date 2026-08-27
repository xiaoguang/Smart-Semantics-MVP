import { sha256HexSync } from './sha256.ts';
import type { DatabaseEvidenceManifest } from './database-evidence.ts';
import type { RepositoryEvidenceManifest } from './repository-evidence.ts';

export type SourceOrigin =
  | 'FILE_UPLOAD'
  | 'DATABASE_CONNECTOR'
  | 'DOCUMENT_DATABASE'
  | 'SEARCH_INDEX'
  | 'GRAPH_STORE'
  | 'OBJECT_STORAGE'
  | 'ENTERPRISE_CONTENT'
  | 'CODE_REPOSITORY'
  | 'EVENT_STREAM';
export type SourceRole = 'BUSINESS_DEFINITION' | 'PHYSICAL_STRUCTURE' | 'ACTUAL_USAGE' | 'AUXILIARY_EVIDENCE';
export type SourceFamily =
  | 'RELATIONAL'
  | 'WAREHOUSE_LAKEHOUSE'
  | 'DOCUMENT_DATABASE'
  | 'KEY_VALUE'
  | 'WIDE_COLUMN_TIME_SERIES'
  | 'GRAPH'
  | 'SEARCH_INDEX'
  | 'VECTOR_DATABASE'
  | 'OBJECT_STORAGE'
  | 'ENTERPRISE_CONTENT'
  | 'CODE_REPOSITORY'
  | 'EVENT_STREAM';
export type AccessProtocol =
  | 'JDBC_SQL' | 'MONGO_API' | 'ELASTIC_REST' | 'SPARQL' | 'S3_API'
  | 'GIT_REST' | 'MICROSOFT_GRAPH' | 'KAFKA' | 'SNOWFLAKE_SQL'
  | 'RESP' | 'MILVUS_API' | 'CQL';
export type EvidenceCapability =
  | 'BUSINESS_DEFINITION' | 'PHYSICAL_SCHEMA' | 'DATA_PROFILE' | 'ACTUAL_QUERY'
  | 'LINEAGE' | 'VOCABULARY' | 'EVENT_SCHEMA' | 'AUXILIARY_CONTENT';
export type EvidenceAuthority = 'PRIMARY' | 'CORROBORATING' | 'DERIVED' | 'AUXILIARY';

const sourceFamilyLabels: Record<SourceFamily, string> = {
  RELATIONAL: '关系型数据库',
  WAREHOUSE_LAKEHOUSE: '数仓与湖仓',
  DOCUMENT_DATABASE: '文档数据库',
  KEY_VALUE: '键值存储',
  WIDE_COLUMN_TIME_SERIES: '宽列与时序存储',
  GRAPH: '知识图谱',
  SEARCH_INDEX: '搜索索引',
  VECTOR_DATABASE: '向量数据库',
  OBJECT_STORAGE: '对象存储',
  ENTERPRISE_CONTENT: '企业内容库',
  CODE_REPOSITORY: '代码仓库',
  EVENT_STREAM: '事件流',
};

export function sourceFamilyLabel(family: SourceFamily) {
  return sourceFamilyLabels[family];
}

export type ConnectorDefinition = {
  connectorId: string;
  family: SourceFamily;
  vendor: string;
  label: string;
  protocol: AccessProtocol;
  capabilities: EvidenceCapability[];
  status: 'CONNECTED' | 'AVAILABLE';
  modes?: Array<'PROPERTY_GRAPH' | 'RDF_GRAPH'>;
};

export type EvidenceClaim = {
  claimId: string;
  sourceId?: string;
  topic?: string;
  subject: string;
  predicate: string;
  value: unknown;
  evidenceRefs: string[];
  authority: EvidenceAuthority;
  upstreamClaimIds?: string[];
};
export type SourceEvidenceDetail =
  | { kind: 'DATABASE'; manifest: DatabaseEvidenceManifest }
  | { kind: 'REPOSITORY'; manifest: RepositoryEvidenceManifest }
  | {
      kind: 'CONNECTOR'; connector: ConnectorDefinition; snapshotId: string; versionRef: string;
      connectionRevision?: number; capturedAt?: string; manifestRef?: string; objectCounts?: Record<string, number>;
    };

export type SourceEvidenceLocator =
  | { kind: 'FILE'; sourceFile: string; page?: number; section?: string; lineStart?: number; lineEnd?: number }
  | { kind: 'IMAGE'; sourceFile: string; region: { x: number; y: number; width: number; height: number } }
  | { kind: 'DATABASE'; datasource: string; schema: string; table?: string; field?: string; queryId?: string }
  | { kind: 'GITHUB'; repository: string; commit: string; path: string; lineStart?: number; lineEnd?: number }
  | { kind: 'MONGODB'; datasource: string; database: string; collection: string; jsonPath?: string }
  | { kind: 'ELASTICSEARCH'; cluster: string; index: string; mappingPath?: string; queryId?: string }
  | { kind: 'RDF'; endpoint: string; graphUri: string; subject: string; predicate: string; object: string }
  | { kind: 'OBJECT'; bucket: string; objectKey: string; versionId: string; mediaType: string }
  | { kind: 'SHAREPOINT'; site: string; driveItemId: string; path: string; section?: string }
  | { kind: 'KAFKA'; cluster: string; topic: string; schemaId: string; offsetRange: string };

export type SourceAsset = {
  sourceId: string;
  displayName: string;
  origin: SourceOrigin;
  contentKinds: string[];
  roles: SourceRole[];
  fingerprint: string;
  fixtureKey: string;
  status: 'READY' | 'PROCESSING' | 'FAILED' | 'NO_RESULT';
  summary: string;
  evidenceIds: string[];
  evidenceDetail?: SourceEvidenceDetail;
  connector?: ConnectorDefinition;
  authority?: EvidenceAuthority;
  upstreamSourceIds?: string[];
};

export type ReconciliationFinding = {
  findingId: string;
  title: string;
  conclusion: string;
  severity: 'CONSISTENT' | 'WEAK' | 'BLOCKER';
  sourceIds: string[];
  evidenceIds: string[];
  affectedObjects: string[];
  recommendationId: string;
  options: Array<{ id: string; label: string; description: string }>;
  decision?: { resolutionId: string; reviewer: string; reason: string; decidedAt: string };
};

export type ModelingBatch = {
  batchId: string;
  systemCode: string;
  revision: number;
  state: 'COLLECTING' | 'FROZEN' | 'RECONCILING' | 'AWAITING_DECISION' | 'MODEL_READY' | 'BLOCKED' | 'PUBLISHED';
  sourceIds: string[];
  snapshotIds?: string[];
  fingerprint: string;
  findings: ReconciliationFinding[];
};

export function computeSourceBundleFingerprint(fingerprints: string[]) {
  return sha256HexSync([...new Set(fingerprints)].sort().join('\n'));
}

export function validateSourceAttachments(files: File[]) {
  if (files.length > 10) return { files, error: '一次最多添加 10 个文件。' };
  const maxFileSize = 10 * 1024 * 1024;
  const maxTotalSize = 25 * 1024 * 1024;
  if (files.some((file) => file.size > maxFileSize)) return { files, error: '单个文件不能超过 10 MiB。' };
  if (files.reduce((sum, file) => sum + file.size, 0) > maxTotalSize) return { files, error: '文件总大小不能超过 25 MiB。' };
  return { files, error: null };
}

function crc32(bytes: Uint8Array) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let index = 0; index < 8; index += 1) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function write16(view: DataView, offset: number, value: number) { view.setUint16(offset, value, true); }
function write32(view: DataView, offset: number, value: number) { view.setUint32(offset, value, true); }

export function createStoredZip(entries: Array<{ name: string; bytes: Uint8Array }>) {
  const encoder = new TextEncoder();
  const localChunks: Uint8Array[] = [];
  const centralChunks: Uint8Array[] = [];
  let localOffset = 0;
  for (const entry of entries) {
    const name = encoder.encode(entry.name);
    const checksum = crc32(entry.bytes);
    const local = new Uint8Array(30 + name.length + entry.bytes.length);
    const localView = new DataView(local.buffer);
    write32(localView, 0, 0x04034b50); write16(localView, 4, 20); write16(localView, 6, 0x0800);
    write16(localView, 8, 0); write32(localView, 14, checksum); write32(localView, 18, entry.bytes.length);
    write32(localView, 22, entry.bytes.length); write16(localView, 26, name.length);
    local.set(name, 30); local.set(entry.bytes, 30 + name.length); localChunks.push(local);
    const central = new Uint8Array(46 + name.length);
    const centralView = new DataView(central.buffer);
    write32(centralView, 0, 0x02014b50); write16(centralView, 4, 20); write16(centralView, 6, 20);
    write16(centralView, 8, 0x0800); write16(centralView, 10, 0); write32(centralView, 16, checksum);
    write32(centralView, 20, entry.bytes.length); write32(centralView, 24, entry.bytes.length);
    write16(centralView, 28, name.length); write32(centralView, 42, localOffset);
    central.set(name, 46); centralChunks.push(central); localOffset += local.length;
  }
  const centralSize = centralChunks.reduce((sum, item) => sum + item.length, 0);
  const end = new Uint8Array(22);
  const endView = new DataView(end.buffer);
  write32(endView, 0, 0x06054b50); write16(endView, 8, entries.length); write16(endView, 10, entries.length);
  write32(endView, 12, centralSize); write32(endView, 16, localOffset);
  const output = new Uint8Array(localOffset + centralSize + end.length);
  let offset = 0;
  for (const chunk of [...localChunks, ...centralChunks, end]) { output.set(chunk, offset); offset += chunk.length; }
  return output;
}
