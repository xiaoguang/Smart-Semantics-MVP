import rawEvidenceFixture from './fixtures/guanyijia-database-evidence.json' with { type: 'json' };
import rawRepositoryFixture from './fixtures/guanyijia-repository-evidence.json' with { type: 'json' };
import rawCandidateModel from './fixtures/guanyijia-candidate-model.json' with { type: 'json' };
import type { DatabaseEvidenceManifest } from './database-evidence.ts';
import type { CandidateModelSnapshot } from './candidate-model.ts';
import type { RepositoryEvidenceManifest } from './repository-evidence.ts';
import type { ReconciliationFinding, SourceAsset, SourceEvidenceLocator } from './source-bundle.ts';
import type { SemanticFixture } from './types.ts';

export type GuanyijiaEvidenceFixture = {
  schemaVersion: 1;
  manifest: DatabaseEvidenceManifest;
  source: SourceAsset;
};

const imported = rawEvidenceFixture as unknown as GuanyijiaEvidenceFixture;
export const guanyijiaEvidenceFixture: GuanyijiaEvidenceFixture = {
  ...imported,
  source: { ...imported.source, evidenceDetail: { kind: 'DATABASE', manifest: imported.manifest } },
};

export type GuanyijiaRepositoryEvidenceFixture = {
  schemaVersion: 1;
  manifest: RepositoryEvidenceManifest;
  source: SourceAsset;
};

const importedRepository = rawRepositoryFixture as unknown as GuanyijiaRepositoryEvidenceFixture;
export const guanyijiaRepositoryEvidenceFixture: GuanyijiaRepositoryEvidenceFixture = {
  ...importedRepository,
  source: { ...importedRepository.source, evidenceDetail: { kind: 'REPOSITORY', manifest: importedRepository.manifest } },
};

export const guanyijiaCandidateModel = rawCandidateModel as unknown as CandidateModelSnapshot;
export const guanyijiaSourceAssets = [guanyijiaEvidenceFixture.source, guanyijiaRepositoryEvidenceFixture.source];
const pendingAssetCount = guanyijiaCandidateModel.pendingAssets.length;

function databaseLocator(objectName: string): SourceEvidenceLocator {
  const [table, field] = objectName.split('.', 2);
  return { kind: 'DATABASE', datasource: '管伊佳 MySQL', schema: 'jsh_erp', table, field };
}

export const guanyijiaEvidenceLocators: Record<string, SourceEvidenceLocator> = Object.fromEntries([
  ...guanyijiaEvidenceFixture.manifest.evidenceFiles.map((record) => [record.evidenceId, databaseLocator(record.objectName)] as const),
  ...guanyijiaRepositoryEvidenceFixture.manifest.evidenceFiles.map((record) => [record.evidenceId, {
    kind: 'GITHUB' as const,
    repository: guanyijiaRepositoryEvidenceFixture.manifest.repository,
    commit: guanyijiaRepositoryEvidenceFixture.manifest.commit,
    path: record.sourcePath,
    lineStart: record.lineStart,
    lineEnd: record.lineEnd,
  }] as const),
]);

export function createGuanyijiaFindings(): ReconciliationFinding[] {
  const mysql = guanyijiaEvidenceFixture.source.sourceId;
  const github = guanyijiaRepositoryEvidenceFixture.source.sourceId;
  return [
    {
      findingId: 'finding-core-tables', title: '源码核心表与部署结构一致',
      conclusion: '源码定义的 32 张核心表全部存在于当前数据库。', severity: 'CONSISTENT',
      sourceIds: [mysql, github], evidenceIds: ['mysql_jsh_erp@v1:TABLE:jsh_material', 'github_jshERP@v1:TABLE:jsh_material'],
      affectedObjects: ['32 张源码核心表'], recommendationId: 'accept', options: [{ id: 'accept', label: '接受结论', description: '保留两类来源。' }],
    },
    {
      findingId: 'finding-mapper-relations', title: '代码补充数据库未声明的关联',
      conclusion: '数据库显式外键很少，但 Mapper Join 能确认商品、仓库、往来单位与单据之间的业务关联。', severity: 'CONSISTENT',
      sourceIds: [mysql, github], evidenceIds: ['mysql_jsh_erp@v1:TABLE:jsh_depot_item', 'github_jshERP@v1:TABLE:jsh_depot_item'],
      affectedObjects: ['18 条候选关系'], recommendationId: 'accept', options: [{ id: 'accept', label: '接受结论', description: '将代码关联标记为代码确认。' }],
    },
    {
      findingId: 'finding-extensions', title: `数据库包含 ${pendingAssetCount} 张源码外扩展表`,
      conclusion: '这些表只在当前数据库出现，缺少固定源码中的业务定义，全部保留为待归类资产。', severity: 'WEAK',
      sourceIds: [mysql], evidenceIds: ['mysql_jsh_erp@v1:TABLE:biz_bill_item_fact'], affectedObjects: [`${pendingAssetCount} 张待归类资产`],
      recommendationId: 'keep_pending', options: [{ id: 'keep_pending', label: '保留待归类', description: '不根据表名猜测业务含义。' }],
    },
    {
      findingId: 'finding-status-nine', title: '审核中状态的常量依据不完整',
      conclusion: 'DDL 与 Service 均使用 9 表示审核中，但业务常量没有完整覆盖该状态。', severity: 'WEAK',
      sourceIds: [mysql, github], evidenceIds: ['mysql_jsh_erp@v1:COLUMN:jsh_depot_head.status', 'github_jshERP@v1:TABLE:jsh_depot_head'],
      affectedObjects: ['单据状态维度', '单据审核规则'], recommendationId: 'keep_pending',
      options: [{ id: 'keep_pending', label: '标记待完善依据', description: '保留已确认含义，同时提示补齐常量依据。' }],
    },
    {
      findingId: 'finding-debt-columns', title: '欠款字段在源码与部署数据库之间不一致',
      conclusion: '源码 DDL 包含 debt、last_debt、last_deposit，当前数据库缺少这三个字段。', severity: 'BLOCKER',
      sourceIds: [mysql, github], evidenceIds: ['mysql_jsh_erp@v1:TABLE:jsh_depot_head', 'github_jshERP@v1:TABLE:jsh_depot_head'],
      affectedObjects: ['应收欠款指标', '订金处理规则'], recommendationId: 'use_deployed_schema',
      options: [
        { id: 'use_deployed_schema', label: '以当前部署结构为准', description: '欠款指标保持待确认，不读取不存在的字段。' },
        { id: 'await_database_upgrade', label: '等待数据库升级', description: '保留源码口径，升级后重新采集数据库。' },
        { id: 'keep_blocked', label: '保留阻断', description: '暂不决定，候选模型仍可阅读。' },
      ],
    },
  ];
}

export const guanyijiaScenarioFixture: SemanticFixture = {
  schemaVersion: 4,
  system: { code: 'guanyijia_erp', name: '管伊佳' },
  documents: [],
};
