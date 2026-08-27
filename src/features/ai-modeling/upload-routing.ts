import type { WorkspaceSnapshot } from './runtime-types.ts';
import type { DocumentVersion, SemanticFixture, WorkspaceVersion } from './types.ts';
import type { WorkspaceDefinition } from './workspace-registry.ts';

export type KnownDocumentContinuation = 'UPLOAD_AND_GENERATE' | 'GENERATE' | 'OPEN_EXISTING';

type KnownDocumentRoute = {
  systemCode: string;
  documentCode: string;
  documentVersion?: DocumentVersion;
  continuation?: KnownDocumentContinuation;
  selection?: WorkspaceVersion;
};

export type UploadRoutingDecision =
  | ({ kind: 'CURRENT' } & KnownDocumentRoute)
  | ({ kind: 'SWITCH_EXISTING' } & KnownDocumentRoute)
  | { kind: 'CHOOSE_DESTINATION'; sha256: string };

export function resolveUploadRouting(input: {
  currentSystemCode: string;
  sha256: string;
  workspaces: WorkspaceDefinition[];
  fixture: SemanticFixture;
  targetSnapshot?: WorkspaceSnapshot;
}): UploadRoutingDecision {
  const known = input.fixture.documents.find((item) => item.sha256 === input.sha256);
  if (!known) return { kind: 'CHOOSE_DESTINATION', sha256: input.sha256 };
  const systemCode = input.fixture.system.code;
  if (!input.workspaces.some((item) => item.systemCode === systemCode)) {
    return { kind: 'CHOOSE_DESTINATION', sha256: input.sha256 };
  }
  const result = input.targetSnapshot?.results.find((item) => item.documentVersion === known.documentVersion);
  const uploaded = input.targetSnapshot?.uploads.some((item) => item.sha256 === known.sha256) ?? false;
  const continuation: KnownDocumentContinuation = result ? 'OPEN_EXISTING' : uploaded ? 'GENERATE' : 'UPLOAD_AND_GENERATE';
  const knownState = input.targetSnapshot ? {
    documentVersion: known.documentVersion,
    continuation,
    selection: result?.status === 'PUBLISHED' && known.modelVersion ? known.modelVersion : 'WORKSPACE' as WorkspaceVersion,
  } : {};
  const route = { systemCode, documentCode: 'digital_sales_semantic_design', ...knownState };
  return input.currentSystemCode === systemCode
    ? { kind: 'CURRENT', ...route }
    : { kind: 'SWITCH_EXISTING', ...route };
}
