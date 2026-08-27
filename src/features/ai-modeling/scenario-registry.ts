import { adaptPublishedModel } from './adapter.ts';
import { semanticFixture } from './fixture.ts';
import { adaptOmnichannelPublishedModel } from './omnichannel-adapter.ts';
import { omnichannelScenarioFixture } from './omnichannel-fixture.ts';
import { createOmnichannelWorkbenchRuntime } from './omnichannel-runtime.ts';
import { createSemanticWorkbenchRuntime, type StorageLike } from './runtime.ts';
import { guanyijiaScenarioFixture } from './guanyijia-fixture.ts';
import { createGuanyijiaEvidenceRuntime } from './guanyijia-runtime.ts';
import { adaptGroupRetailPublishedModel } from './group-retail-adapter.ts';
import { groupRetailScenarioFixture } from './group-retail-fixture.ts';
import { createGroupRetailWorkbenchRuntime } from './group-retail-runtime.ts';
import type { LinguanModelSnapshot, ModelVersion, SemanticFixture } from './types.ts';
import type { WorkbenchRuntime } from './runtime-types.ts';
import type { SourceManagementRuntime } from '../source-management/types.ts';
import type { ModelingPipelineScope, PipelineActor } from '../modeling-pipeline/types.ts';
import { createPipelineWorkbenchRuntime } from '../modeling-pipeline/workbench-adapter.ts';
import { retailModelingAdapter } from '../modeling-pipeline/retail-adapter.ts';
import { guanyijiaModelingAdapter } from '../modeling-pipeline/guanyijia-adapter.ts';

export type ScenarioRuntimeEnvironment = {
  sourceRuntime: SourceManagementRuntime;
  scope: ModelingPipelineScope;
  actor: PipelineActor;
};

export type ModelingScenario = {
  fixture: SemanticFixture;
  capabilities: {
    multiSource: boolean;
    lifecycle: 'EVIDENCE_ONLY' | 'CANDIDATE_ONLY' | 'FULL_LIFECYCLE';
    inspectorViews: Array<'SOURCES' | 'RECONCILIATION' | 'MODEL'>;
  };
  createRuntime(storage: StorageLike, environment?: ScenarioRuntimeEnvironment): WorkbenchRuntime;
  adaptPublished(documentVersion: string, modelVersion: ModelVersion): LinguanModelSnapshot;
};

const scenarios: Record<string, ModelingScenario> = {
  digital_sales_warehouse: {
    fixture: semanticFixture, capabilities: { multiSource: false, lifecycle: 'FULL_LIFECYCLE', inspectorViews: ['MODEL'] },
    createRuntime: (storage) => createSemanticWorkbenchRuntime({ fixture: semanticFixture, storage }),
    adaptPublished: (documentVersion, modelVersion) => adaptPublishedModel('digital_sales_warehouse', documentVersion as 'v1' | 'v2' | 'v4', modelVersion),
  },
  omnichannel_retail_ops: {
    fixture: omnichannelScenarioFixture, capabilities: { multiSource: true, lifecycle: 'FULL_LIFECYCLE', inspectorViews: ['SOURCES', 'RECONCILIATION', 'MODEL'] },
    createRuntime: (storage) => createOmnichannelWorkbenchRuntime({ storage }),
    adaptPublished: () => adaptOmnichannelPublishedModel(),
  },
  guanyijia_erp: {
    fixture: guanyijiaScenarioFixture,
    capabilities: { multiSource: true, lifecycle: 'FULL_LIFECYCLE', inspectorViews: ['SOURCES', 'RECONCILIATION', 'MODEL'] },
    createRuntime: (storage, environment) => environment
      ? createPipelineWorkbenchRuntime({ storage, fixture: guanyijiaScenarioFixture, adapter: guanyijiaModelingAdapter, ...environment })
      : createGuanyijiaEvidenceRuntime({ storage }),
    adaptPublished: () => { throw new Error('管伊佳尚未生成语义模型'); },
  },
  group_retail_ops: {
    fixture: groupRetailScenarioFixture,
    capabilities: { multiSource: true, lifecycle: 'FULL_LIFECYCLE', inspectorViews: ['SOURCES', 'RECONCILIATION', 'MODEL'] },
    createRuntime: (storage, environment) => environment
      ? createPipelineWorkbenchRuntime({ storage, fixture: groupRetailScenarioFixture, adapter: retailModelingAdapter, ...environment })
      : createGroupRetailWorkbenchRuntime({ storage }),
    adaptPublished: () => adaptGroupRetailPublishedModel(),
  },
};

export function resolveModelingScenario(systemCode: string): ModelingScenario {
  const scenario = scenarios[systemCode];
  if (scenario) return scenario;
  return {
    fixture: { schemaVersion: semanticFixture.schemaVersion, system: { code: systemCode, name: systemCode }, documents: [] },
    capabilities: { multiSource: false, lifecycle: 'FULL_LIFECYCLE', inspectorViews: ['MODEL'] },
    createRuntime: (storage) => createSemanticWorkbenchRuntime({ fixture: { schemaVersion: semanticFixture.schemaVersion, system: { code: systemCode, name: systemCode }, documents: [] }, storage }),
    adaptPublished: () => { throw new Error('该模型空间没有已发布的预生成模型'); },
  };
}
