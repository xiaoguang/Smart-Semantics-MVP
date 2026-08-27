import type { StorageLike } from '../ai-modeling/runtime.ts';
import type { WorkbenchRuntime } from '../ai-modeling/runtime-types.ts';
import { resolveModelingScenario, type ModelingScenario } from '../ai-modeling/scenario-registry.ts';
import {
  evidencePackagesFor,
  modelProject,
  type EvidencePackageDefinition,
  type ModelProjectDefinition,
} from './domain-registry.ts';

export type EvidencePackageScenario = {
  definition: EvidencePackageDefinition;
  legacyScenario: ModelingScenario;
};

export type ModelingProjectScenario = {
  project: ModelProjectDefinition;
  packages: EvidencePackageScenario[];
};

export function resolveModelingProjectScenario(projectId: string): ModelingProjectScenario {
  const project = modelProject(projectId);
  if (!project) throw new Error(`未知模型项目：${projectId}`);
  const packages = evidencePackagesFor(projectId).map((definition) => ({
    definition,
    legacyScenario: resolveModelingScenario(definition.legacySystemCode),
  }));
  return { project, packages };
}

export function createEvidencePackageRuntime(
  projectId: string,
  packageId: string,
  storage: StorageLike,
): WorkbenchRuntime {
  const resolved = resolveModelingProjectScenario(projectId);
  const selected = resolved.packages.find((item) => item.definition.packageId === packageId);
  if (!selected) throw new Error(`证据包 ${packageId} 不属于模型项目 ${projectId}`);
  return selected.legacyScenario.createRuntime(storage);
}
