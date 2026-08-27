import type { Metric2WorkspaceData } from '../../pages/metric2/mockData.ts';
import type { Catalog, HydratedResult, WorkspaceSnapshot } from './runtime-types.ts';
import type { LinguanModelSnapshot, ModelVersion, WorkspaceVersion } from './types.ts';
import { projectMetric2WorkspaceData } from './workspace-store-adapter.ts';

export type ActiveWorkspaceView = {
  selection: WorkspaceVersion;
  workflowMode: 'LIVE' | 'PUBLISHED' | 'EMPTY';
  result: HydratedResult | null;
  catalog: Catalog | null;
  model: LinguanModelSnapshot | null;
  metric2Data: Metric2WorkspaceData;
  assistantAccess: AssistantAccess;
  modelReadOnly: boolean;
};

export type AssistantAccess = {
  canAsk: boolean;
  canUpload: boolean;
  canReview: boolean;
  canPublish: boolean;
  recordReadOnly: boolean;
};

const emptyMetric2Data = (): Metric2WorkspaceData => ({
  events: [], entities: [], relations: [], metrics: [], rules: [], targets: [],
});

export function projectActiveWorkspaceView(
  snapshot: WorkspaceSnapshot,
  selection: WorkspaceVersion,
  readModel: (version: ModelVersion) => LinguanModelSnapshot | null,
): ActiveWorkspaceView {
  const catalog = selection === 'WORKSPACE'
    ? null
    : snapshot.catalogs.find((item) => item.modelVersion === selection) ?? null;
  const result = selection === 'WORKSPACE'
    ? snapshot.results.at(-1) ?? null
    : catalog
      ? snapshot.results.find((item) => item.documentVersion === catalog.documentVersion) ?? null
      : null;
  const modelVersion = selection === 'WORKSPACE' ? snapshot.currentCatalogVersion : catalog?.modelVersion ?? null;
  const model = modelVersion ? readModel(modelVersion) : null;
  const workflowMode = selection === 'WORKSPACE'
    ? result ? 'LIVE' : 'EMPTY'
    : catalog && result ? 'PUBLISHED' : 'EMPTY';
  const liveWorkflow = selection === 'WORKSPACE';
  return {
    selection, workflowMode, result, catalog, model,
    metric2Data: model ? projectMetric2WorkspaceData(model) : emptyMetric2Data(),
    assistantAccess: {
      canAsk: true,
      canUpload: liveWorkflow || selection === snapshot.currentCatalogVersion,
      canReview: liveWorkflow,
      canPublish: liveWorkflow,
      recordReadOnly: !liveWorkflow,
    },
    modelReadOnly: selection !== 'WORKSPACE' && selection !== snapshot.currentCatalogVersion,
  };
}

export function projectWorkspaceVersionOptions(snapshot: WorkspaceSnapshot) {
  const latest = snapshot.results.at(-1);
  const hasLiveWorkflow = !snapshot.catalogs.length
    || Boolean(latest && !snapshot.catalogs.some((catalog) => catalog.documentVersion === latest.documentVersion));
  const workflow = hasLiveWorkflow
    ? [{
      value: 'WORKSPACE' as const,
      label: latest ? `当前建模流程 · 资料 ${latest.documentVersion}` : '当前建模流程',
    }]
    : [];
  return [
    ...workflow,
    ...snapshot.catalogs.map((catalog) => ({
      value: catalog.modelVersion as WorkspaceVersion,
      label: `${catalog.modelVersion} · 资料 ${catalog.documentVersion}`,
    })),
  ];
}
