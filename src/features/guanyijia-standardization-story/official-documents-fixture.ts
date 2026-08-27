import type { StructuredValue } from './types.ts';

export type FrozenOfficialSource = {
  snapshotId: string;
  sourceSnapshotIdentity: string;
  fingerprint: string;
  manifestRef: string;
  connectionId: 'guanyijia_official_docs';
  displayName: string;
  connectorType: 'web_document';
  versionRef: string;
  role: string;
  authority: 'AUXILIARY';
  summary: string;
  objectCounts: Record<string, number>;
  evidenceIds: string[];
  status: 'READY';
};

export type FrozenOfficialEvidence = {
  evidenceId: string;
  sourceId: 'guanyijia_official_docs';
  checksum: string;
  locator: Record<string, StructuredValue>;
};

type FrozenOfficialDocumentsFixture = {
  schemaVersion: 1;
  source: FrozenOfficialSource;
  evidence: FrozenOfficialEvidence[];
};

const databaseChecksum = '0d1500b9c450426335c3c6a4cfc40780503e07fbaec35a06b61cd27d72b16c72';
const functionChecksum = '492471784a180c1bd8787c2e3b551b61d53a9176ebeb0a65a659f30117437ca5';

function evidence(
  evidenceId: string,
  page: 'database' | 'fun-list',
  anchor: string,
  title: string,
  checksum: string,
): FrozenOfficialEvidence {
  return {
    evidenceId,
    sourceId: 'guanyijia_official_docs',
    checksum,
    locator: {
      kind: 'WEB_DOCUMENT',
      url: `https://www.gyjerp.com/doc/archive/${page}.html#${anchor}`,
      title,
      checksum,
    },
  };
}

const records = [
  evidence('official:database:account-head-event-time', 'database', 'jsh-account-head财务主表', '管伊佳ERP-数据库设计', databaseChecksum),
  evidence('official:database:depot-head-event-time', 'database', 'jsh-depot-head单据主表', '管伊佳ERP-数据库设计', databaseChecksum),
  evidence('official:function:assembly-inventory-effect', 'fun-list', '仓库管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:counterparty-roles', 'fun-list', '基础资料', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:disassembly-inventory-effect', 'fun-list', '仓库管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:product-category-hierarchy', 'fun-list', '商品管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:purchase-metrics', 'fun-list', '报表查询', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:purchase-order-flow', 'fun-list', '采购管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:purchase-return', 'fun-list', '采购管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:sales-metrics', 'fun-list', '报表查询', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:sales-order-flow', 'fun-list', '销售管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:sales-return', 'fun-list', '销售管理', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:stocktake-review', 'fun-list', '盘点业务', '管伊佳ERP-功能清单', functionChecksum),
  evidence('official:function:transfer-inventory-effect', 'fun-list', '仓库管理', '管伊佳ERP-功能清单', functionChecksum),
];

export const guanyijiaOfficialDocumentsFixture: FrozenOfficialDocumentsFixture = {
  schemaVersion: 1,
  source: {
    snapshotId: 'gyjerp-official-docs-20260813T031656Z',
    sourceSnapshotIdentity: 'gyjerp-official-docs-20260813T031656Z',
    fingerprint: 'e749a1f8ce53e3f84e747dd553a7f41df34dce9ba83e0d7dcb2c7d1c5ced5522',
    manifestRef: 'web-docs://gyjerp-official-docs-20260813T031656Z/manifest',
    connectionId: 'guanyijia_official_docs',
    displayName: '管伊佳官方核心文档',
    connectorType: 'web_document',
    versionRef: '2026-08-13T03:16:56Z',
    role: '业务定义辅助证据',
    authority: 'AUXILIARY',
    summary: '数据库设计、功能清单、用户手册入口与发布说明',
    objectCounts: { pages: 4, claims: 20 },
    evidenceIds: records.map((record) => record.evidenceId),
    status: 'READY',
  },
  evidence: records,
};
