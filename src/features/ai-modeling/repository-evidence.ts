export type RepositoryEvidenceCategory =
  | 'PROVENANCE'
  | 'SCHEMA'
  | 'DATA_ACCESS'
  | 'DOMAIN_MODEL'
  | 'DOMAIN_ENUM'
  | 'WORKFLOW'
  | 'COMMAND_QUERY'
  | 'TENANCY'
  | 'VOCABULARY'
  | 'MIGRATION_HISTORY';

export type RepositoryEvidenceRecord = {
  evidenceId: string;
  category: RepositoryEvidenceCategory;
  objectType: string;
  objectName: string;
  relativePath: string;
  sourcePath: string;
  sourceSha256: string;
  sha256: string;
  lineStart?: number;
  lineEnd?: number;
};

export type RepositoryEvidenceManifest = {
  schemaVersion: 1;
  systemCode: 'guanyijia_erp';
  documentCode: 'github_jshERP';
  evidenceVersion: 'v1';
  snapshotId: string;
  sourceAlias: string;
  provider: 'github';
  repository: 'jishenghua/jshERP';
  branch: 'master';
  commit: string;
  committedAt: string;
  license: 'Apache-2.0';
  objectCounts: Record<string, number>;
  evidenceFiles: RepositoryEvidenceRecord[];
  warnings: Array<{ path: string; reason: string }>;
  fingerprint: string;
  exportedAt: string;
};

export type RepositoryEvidenceSection = {
  id: string;
  label: string;
  description: string;
  records: RepositoryEvidenceRecord[];
};

const sectionDefinitions = [
  { id: 'structure', label: '结构定义', description: '安装DDL、实体和数据对象定义。', categories: ['SCHEMA', 'DOMAIN_MODEL'] },
  { id: 'access', label: '数据访问', description: 'Mapper及查询定义中的关联、过滤、粒度与计算。', categories: ['DATA_ACCESS'] },
  { id: 'workflow', label: '业务流程', description: 'Service与Controller中的业务动作和状态变化。', categories: ['WORKFLOW', 'COMMAND_QUERY'] },
  { id: 'rules', label: '业务规则', description: '稳定枚举、校验规则和异常含义。', categories: ['DOMAIN_ENUM'] },
  { id: 'tenancy', label: '租户与权限', description: '租户隔离和数据访问范围。', categories: ['TENANCY'] },
  { id: 'vocabulary', label: '用户术语', description: '用户在页面中看到的业务名称。', categories: ['VOCABULARY'] },
  { id: 'history', label: '结构演进', description: '数据库升级记录，只解释演进，不覆盖当前部署事实。', categories: ['MIGRATION_HISTORY'] },
] as const;

export function projectRepositoryEvidenceSections(manifest: RepositoryEvidenceManifest): RepositoryEvidenceSection[] {
  return sectionDefinitions.map((section) => ({
    id: section.id,
    label: section.label,
    description: section.description,
    records: manifest.evidenceFiles.filter((record) => (section.categories as readonly string[]).includes(record.category)),
  }));
}
