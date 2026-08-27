export type ProductDefinition = {
  productId: 'retail_semantic_modeling_product';
  displayName: '零售语义建模';
  workspaceIds: string[];
};

export type ModelProjectDefinition = {
  projectId: string;
  systemCode: string;
  displayName: string;
  workspaceId: string;
  evidencePackageIds: string[];
};

export type EvidencePackageDefinition = {
  packageId: string;
  projectId: string;
  displayName: string;
  packageType: 'MODEL_BASELINE' | 'DOCUMENTS' | 'MULTI_SOURCE' | 'MULTI_STORAGE';
  legacySystemCode: string;
  fixtureKey: string;
  packageVersion: string;
  status: 'AVAILABLE' | 'IN_BATCH' | 'ARCHIVED';
};

export const productDefinition: ProductDefinition = {
  productId: 'retail_semantic_modeling_product',
  displayName: '零售语义建模',
  workspaceIds: ['erp_data_governance', 'retail_semantic_modeling'],
};

export const evidencePackageDefinitions: EvidencePackageDefinition[] = [
  {
    packageId: 'guanyijia_mysql', projectId: 'guanyijia_erp', displayName: 'MySQL 数据库证据',
    packageType: 'MULTI_SOURCE', legacySystemCode: 'guanyijia_erp', fixtureKey: 'guanyijia_mysql',
    packageVersion: 'mysql_jsh_erp@v1', status: 'AVAILABLE',
  },
  {
    packageId: 'guanyijia_github', projectId: 'guanyijia_erp', displayName: 'GitHub 源码证据',
    packageType: 'MULTI_SOURCE', legacySystemCode: 'guanyijia_erp', fixtureKey: 'guanyijia_github',
    packageVersion: 'b3ab269b', status: 'AVAILABLE',
  },
  {
    packageId: 'retail_manual_baseline', projectId: 'group_retail_ops', displayName: '原始人工模型基线',
    packageType: 'MODEL_BASELINE', legacySystemCode: 'default', fixtureKey: 'default_workspace',
    packageVersion: 'workspace-v1', status: 'AVAILABLE',
  },
  {
    packageId: 'retail_digital_documents', projectId: 'group_retail_ops', displayName: '数码产品设计文档',
    packageType: 'DOCUMENTS', legacySystemCode: 'digital_sales_warehouse', fixtureKey: 'semantic_fixture',
    packageVersion: 'documents-v1-v4', status: 'AVAILABLE',
  },
  {
    packageId: 'retail_omnichannel_sources', projectId: 'group_retail_ops', displayName: '全渠道经营资料',
    packageType: 'MULTI_SOURCE', legacySystemCode: 'omnichannel_retail_ops', fixtureKey: 'omnichannel_fixture',
    packageVersion: 'source-pack-v1', status: 'AVAILABLE',
  },
  {
    packageId: 'retail_group_storage', projectId: 'group_retail_ops', displayName: '集团多存储证据',
    packageType: 'MULTI_STORAGE', legacySystemCode: 'group_retail_ops', fixtureKey: 'group_retail_fixture',
    packageVersion: 'storage-pack-v1', status: 'IN_BATCH',
  },
];

export const modelProjectDefinitions: ModelProjectDefinition[] = [
  {
    projectId: 'guanyijia_erp', systemCode: 'guanyijia_erp', displayName: '管伊佳 ERP 语义模型',
    workspaceId: 'erp_data_governance', evidencePackageIds: ['guanyijia_mysql', 'guanyijia_github'],
  },
  {
    projectId: 'group_retail_ops', systemCode: 'group_retail_ops', displayName: '零售经营语义模型',
    workspaceId: 'retail_semantic_modeling',
    evidencePackageIds: [
      'retail_manual_baseline', 'retail_digital_documents',
      'retail_omnichannel_sources', 'retail_group_storage',
    ],
  },
];

export function modelProject(projectId: string) {
  return modelProjectDefinitions.find((item) => item.projectId === projectId);
}

export function evidencePackagesFor(projectId: string) {
  return evidencePackageDefinitions.filter((item) => item.projectId === projectId);
}

export function packageForLegacySystem(legacySystemCode: string) {
  return evidencePackageDefinitions.find((item) => item.legacySystemCode === legacySystemCode);
}

type WorkspaceProjectAccess = {
  workspaceId: string;
  modelProjectIds: string[];
};

export function accessibleModelProjects(workspaces: WorkspaceProjectAccess[]) {
  const allowed = new Set(workspaces.flatMap((workspace) => workspace.modelProjectIds));
  return modelProjectDefinitions.filter((project) => allowed.has(project.projectId));
}

export function workspaceForModelProject(workspaces: WorkspaceProjectAccess[], projectId: string) {
  return workspaces.find((workspace) => workspace.modelProjectIds.includes(projectId));
}
