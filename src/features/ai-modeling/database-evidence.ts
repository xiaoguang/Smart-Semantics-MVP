export type EvidenceFileCategory = 'DDL' | 'PROGRAMMABILITY' | 'CONSTRAINT' | 'DML_DIGEST' | 'PROFILE' | 'SAMPLE';

export type EvidenceFileRecord = {
  evidenceId: string;
  category: EvidenceFileCategory;
  objectType: string;
  objectName: string;
  relativePath: string;
  sha256: string;
};

export type EvidenceWarning = {
  objectName: string;
  stage: 'COUNT' | 'PROFILE' | 'SAMPLE';
  status: 'TIMEOUT' | 'SKIPPED' | 'FAILED';
  reason: string;
};

export type DatabaseEvidenceManifest = {
  systemCode: 'guanyijia_erp';
  documentCode: 'mysql_jsh_erp';
  evidenceVersion: 'v1';
  snapshotId: string;
  sourceAlias: string;
  engine: 'mysql';
  engineVersion: string;
  databaseName: 'jsh_erp';
  tenantId: 153;
  objectCounts: Record<string, number>;
  evidenceFiles: EvidenceFileRecord[];
  warnings: EvidenceWarning[];
  fingerprint: string;
  exportedAt: string;
};

export type DatabaseEvidenceSection = {
  id: 'PHYSICAL' | 'PROGRAM' | 'USAGE' | 'SHAPE';
  label: '物理结构' | '程序逻辑' | '实际使用' | '数据形态';
  description: string;
  records: EvidenceFileRecord[];
};

const sectionFor = (category: EvidenceFileCategory): DatabaseEvidenceSection['id'] => {
  if (category === 'DDL' || category === 'CONSTRAINT') return 'PHYSICAL';
  if (category === 'PROGRAMMABILITY') return 'PROGRAM';
  if (category === 'DML_DIGEST') return 'USAGE';
  return 'SHAPE';
};

export function projectDatabaseEvidenceSections(manifest: DatabaseEvidenceManifest): DatabaseEvidenceSection[] {
  const definitions: Array<Omit<DatabaseEvidenceSection, 'records'>> = [
    { id: 'PHYSICAL', label: '物理结构', description: '表、视图、字段、索引和外键。' },
    { id: 'PROGRAM', label: '程序逻辑', description: '存储过程、函数、触发器和数据库事件。' },
    { id: 'USAGE', label: '实际使用', description: '参数化 DML Digest 与执行摘要。' },
    { id: 'SHAPE', label: '数据形态', description: `Tenant ${manifest.tenantId} 的统计与严格脱敏样例。` },
  ];
  return definitions.map((definition) => ({
    ...definition,
    records: manifest.evidenceFiles.filter((record) => sectionFor(record.category) === definition.id),
  }));
}
