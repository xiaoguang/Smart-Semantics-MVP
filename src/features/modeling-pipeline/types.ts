import type { CandidateModelSnapshot } from '../ai-modeling/candidate-model.ts';
import type { MaterializedCollaborationData } from '../collaboration/types.ts';
import type { EvidenceAuthority, SourceEvidenceLocator } from '../ai-modeling/source-bundle.ts';
import type { SourceManagementRuntime, SourceManagementSnapshot, SourceSnapshot } from '../source-management/types.ts';

export type ModelingPipelineScope = {
  workspaceId: string;
  projectId: string;
  draftId: string;
};

export type PipelineActor = {
  userId: string;
  role: 'VIEWER' | 'EDITOR' | 'REVIEWER' | 'PUBLISHER' | 'ADMIN';
};

export type PipelineSource = SourceSnapshot & {
  displayName: string;
  connectorTypeId: string;
  authority: EvidenceAuthority;
};

export type PipelineEvidenceClaim = {
  claimId: string;
  sourceId: string;
  assertion: string;
  authority: EvidenceAuthority;
  evidenceRefs: string[];
  upstreamClaimIds?: string[];
};

export type PipelineFinding = {
  findingId: string;
  title: string;
  status: 'CONSISTENT' | 'CONFLICT' | 'SINGLE_SOURCE' | 'DERIVED';
  severity: 'CONSISTENT' | 'WEAK' | 'BLOCKER';
  claimIds: string[];
  agreement?: string;
  difference?: string;
  recommendationId: string;
  options: Array<{ id: string; label: string; description: string }>;
  affectedObjectCodes: string[];
  decision?: {
    resolutionId: string;
    reviewer: string;
    reason: string;
    decidedAt: string;
  };
};

export type PipelineEvidenceBatch = {
  batchId: string;
  revision: number;
  snapshotIds: string[];
  fingerprint: string;
  frozenAt: string;
};

export type PipelineCoverage = {
  complete: boolean;
  expectedConnectionIds: string[];
  presentConnectionIds: string[];
  missingConnectionIds: string[];
  message: string;
};

export type ModelingPipelineSnapshot = {
  schemaVersion: 1;
  scope: ModelingPipelineScope;
  revision: number;
  state: 'COLLECTING' | 'FROZEN' | 'RECONCILED' | 'BLOCKED' | 'CANDIDATE_READY' | 'APPLIED_TO_DRAFT';
  attachedSnapshotIds: string[];
  batch?: PipelineEvidenceBatch;
  sources: PipelineSource[];
  claims: PipelineEvidenceClaim[];
  findings: PipelineFinding[];
  evidenceLocators: Record<string, SourceEvidenceLocator | Record<string, unknown>>;
  coverage: PipelineCoverage;
  candidate?: CandidateModelSnapshot;
  materialized?: MaterializedCollaborationData;
};

type BaseCommand = ModelingPipelineScope & { expectedRevision: number; actor: PipelineActor };
export type ModelingPipelineCommand =
  | (BaseCommand & { type: 'ATTACH_SOURCE_SNAPSHOTS'; snapshotIds: string[] })
  | (BaseCommand & { type: 'DETACH_SOURCE_SNAPSHOT'; snapshotId: string })
  | (BaseCommand & { type: 'FREEZE_EVIDENCE_BATCH' })
  | (BaseCommand & { type: 'RECONCILE_EVIDENCE' })
  | (BaseCommand & { type: 'RESOLVE_FINDING'; findingId: string; resolutionId: string; reason: string })
  | (BaseCommand & { type: 'GENERATE_CANDIDATE' })
  | (BaseCommand & { type: 'APPLY_CANDIDATE_TO_DRAFT' });

export type AdapterInput = {
  scope: ModelingPipelineScope;
  sourceWorkspace: SourceManagementSnapshot;
  sources: PipelineSource[];
};

export type ProjectModelingAdapter = {
  projectId: string;
  workspaceId: string;
  expectedConnectionIds: string[];
  normalize(input: AdapterInput): {
    claims: PipelineEvidenceClaim[];
    locators: ModelingPipelineSnapshot['evidenceLocators'];
  };
  reconcile(input: AdapterInput & { claims: PipelineEvidenceClaim[] }): PipelineFinding[];
  generate(input: AdapterInput & { claims: PipelineEvidenceClaim[]; findings: PipelineFinding[] }): CandidateModelSnapshot | null;
  materialize(input: AdapterInput & { candidate: CandidateModelSnapshot; findings: PipelineFinding[]; batch: PipelineEvidenceBatch }): MaterializedCollaborationData;
};

export type ModelingPipelineRuntime = {
  read(scope: ModelingPipelineScope): Promise<ModelingPipelineSnapshot>;
  execute(command: ModelingPipelineCommand): Promise<{ snapshot: ModelingPipelineSnapshot }>;
  reset(scope: ModelingPipelineScope): Promise<ModelingPipelineSnapshot>;
};

export type ModelingPipelineRuntimeOptions = {
  storage: Pick<Storage, 'getItem' | 'setItem'>;
  sourceRuntime: SourceManagementRuntime;
  adapter: ProjectModelingAdapter;
  now?: () => string;
};
