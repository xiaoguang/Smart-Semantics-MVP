import rawPackage from '../ai-modeling/fixtures/guanyijia-modeling-package.json' with { type: 'json' };
import type { ModelingDocumentArtifact } from './types.ts';

type GuanyijiaModelingPackage = {
  schemaVersion: 1;
  artifact: ModelingDocumentArtifact;
  classification: {
    semanticModelTables: string[];
    technicalSupportTables: string[];
    pendingTables: string[];
  };
  sourceIdentities: Record<string, Record<string, string>>;
};

export const guanyijiaModelingPackage = rawPackage as unknown as GuanyijiaModelingPackage;
export const guanyijiaFrozenModelingArtifact = guanyijiaModelingPackage.artifact;

