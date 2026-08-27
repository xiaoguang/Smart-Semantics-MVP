import type { SemanticFixture } from '../ai-modeling/types.ts';
import type { WorkbenchCommand, WorkbenchRuntime, WorkspaceSnapshot } from '../ai-modeling/runtime-types.ts';
import type { ConnectorDefinition, ModelingBatch, ReconciliationFinding, SourceEvidenceLocator } from '../ai-modeling/source-bundle.ts';
import { WorkbenchError, type StorageLike } from '../ai-modeling/runtime.ts';
import { projectSnapshotAsset } from '../source-management/projection.ts';
import type { SourceManagementRuntime } from '../source-management/types.ts';
import { createModelingPipelineRuntime } from './runtime.ts';
import type { ModelingPipelineScope, PipelineActor, ProjectModelingAdapter } from './types.ts';

const clone = <T>(value: T): T => structuredClone(value);

function connectorCatalog(input: Awaited<ReturnType<SourceManagementRuntime['read']>>): ConnectorDefinition[] {
  return input.connectorTypes.map((item) => ({
    connectorId: item.connectorTypeId, family: item.family, vendor: item.vendor,
    label: item.displayName, protocol: item.protocols[0], capabilities: [...item.capabilities],
    status: input.connections.some((connection) => connection.connectorTypeId === item.connectorTypeId && connection.state === 'READY') ? 'CONNECTED' : 'AVAILABLE',
    ...(item.connectorTypeId === 'semantica' ? { modes: ['PROPERTY_GRAPH', 'RDF_GRAPH'] as Array<'PROPERTY_GRAPH' | 'RDF_GRAPH'> } : {}),
  }));
}

function legacyFinding(state: Awaited<ReturnType<ReturnType<typeof createModelingPipelineRuntime>['read']>>, finding: typeof state.findings[number]): ReconciliationFinding {
  const claims = finding.claimIds.map((id) => state.claims.find((claim) => claim.claimId === id)).filter(Boolean);
  return {
    findingId: finding.findingId, title: finding.title,
    conclusion: finding.agreement ?? finding.difference ?? '该结论需要结合当前来源继续确认。',
    severity: finding.severity,
    sourceIds: [...new Set(claims.map((claim) => claim!.sourceId))],
    evidenceIds: [...new Set(claims.flatMap((claim) => claim!.evidenceRefs))],
    affectedObjects: [...finding.affectedObjectCodes], recommendationId: finding.recommendationId,
    options: clone(finding.options),
    ...(finding.decision ? { decision: clone(finding.decision) } : {}),
  };
}

export function createPipelineWorkbenchRuntime(options: {
  storage: StorageLike;
  sourceRuntime: SourceManagementRuntime;
  adapter: ProjectModelingAdapter;
  fixture: SemanticFixture;
  scope: ModelingPipelineScope;
  actor: PipelineActor;
  now?: () => string;
}): WorkbenchRuntime {
  const pipeline = createModelingPipelineRuntime({
    storage: options.storage, sourceRuntime: options.sourceRuntime, adapter: options.adapter, now: options.now,
  });
  async function hydrate(): Promise<WorkspaceSnapshot> {
    const [state, managed] = await Promise.all([
      pipeline.read(options.scope), options.sourceRuntime.read(options.scope.workspaceId),
    ]);
    const selectedSources = state.sources.flatMap((source) => {
      const asset = projectSnapshotAsset(managed, source.snapshotId);
      return asset ? [asset] : [];
    });
    const availableSources = managed.snapshots.flatMap((source) => {
      const asset = projectSnapshotAsset(managed, source.snapshotId);
      return asset ? [asset] : [];
    });
    const batch: ModelingBatch = {
      batchId: state.batch?.batchId ?? `${options.scope.projectId}-collecting`,
      systemCode: options.scope.projectId, revision: state.batch?.revision ?? 0,
      state: state.state === 'COLLECTING' ? 'COLLECTING'
        : state.state === 'FROZEN' ? 'FROZEN'
          : state.state === 'BLOCKED' ? 'BLOCKED'
            : state.state === 'CANDIDATE_READY' || state.state === 'APPLIED_TO_DRAFT' ? 'MODEL_READY'
              : 'AWAITING_DECISION',
      sourceIds: [], snapshotIds: [...state.attachedSnapshotIds], fingerprint: state.batch?.fingerprint ?? '',
      findings: state.findings.map((finding) => legacyFinding(state, finding)),
    };
    return {
      schemaVersion: 4, systemCode: options.scope.projectId, lockVersion: state.revision,
      uploads: [], unknownUploads: [], results: [], catalogs: [], currentCatalogVersion: null,
      fixture: options.fixture, nextExpectedVersion: null, recoveryRequired: false, recoveryMessage: null,
      sourceWorkspace: {
        availableSources, selectedSources,
        evidenceLocators: clone(state.evidenceLocators) as Record<string, SourceEvidenceLocator>,
        connectorCatalog: connectorCatalog(managed),
        evidenceClaims: state.claims.map((claim) => ({
          claimId: claim.claimId, subject: claim.sourceId, predicate: '主张', value: claim.assertion,
          evidenceRefs: [...claim.evidenceRefs], authority: claim.authority,
        })),
        batch, candidateModel: state.candidate ? clone(state.candidate) : undefined,
        materializedDraft: state.materialized ? clone(state.materialized) : undefined,
        history: [],
      },
    };
  }

  async function executePipeline(command: Parameters<typeof pipeline.execute>[0]) {
    await pipeline.execute(command);
  }
  const base = (expectedRevision: number) => ({ ...options.scope, expectedRevision, actor: options.actor });
  return {
    async read(systemCode) {
      if (systemCode !== options.scope.projectId) throw new WorkbenchError('SYSTEM_NOT_FOUND', '模型项目不存在');
      return hydrate();
    },
    async reset(systemCode) {
      if (systemCode !== options.scope.projectId) throw new WorkbenchError('SYSTEM_NOT_FOUND', '模型项目不存在');
      await pipeline.reset(options.scope);
      return hydrate();
    },
    async execute(command: WorkbenchCommand) {
      if (command.systemCode !== options.scope.projectId) throw new WorkbenchError('SYSTEM_NOT_FOUND', '模型项目不存在');
      if (command.type === 'ATTACH_SOURCE_SNAPSHOTS') {
        await executePipeline({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...base(command.expectedRevision), snapshotIds: command.snapshotIds });
      } else if (command.type === 'DETACH_SOURCE_SNAPSHOT') {
        await executePipeline({ type: 'DETACH_SOURCE_SNAPSHOT', ...base(command.expectedRevision), snapshotId: command.snapshotId });
      } else if (command.type === 'ADD_SOURCES') {
        const managed = await options.sourceRuntime.read(options.scope.workspaceId);
        const snapshotIds = command.sourceIds.flatMap((sourceId) => managed.snapshots.filter((snapshot) => snapshot.legacySourceId === sourceId).map((snapshot) => snapshot.snapshotId));
        if (snapshotIds.length !== command.sourceIds.length) throw new WorkbenchError('SOURCE_NOT_FOUND', '旧来源无法关联到不可变证据快照');
        await executePipeline({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...base(command.expectedRevision), snapshotIds });
      } else if (command.type === 'REMOVE_SOURCE') {
        const managed = await options.sourceRuntime.read(options.scope.workspaceId);
        const snapshot = managed.snapshots.find((item) => item.legacySourceId === command.sourceId || item.snapshotId === command.sourceId);
        if (!snapshot) throw new WorkbenchError('SOURCE_NOT_FOUND', '来源快照不存在');
        await executePipeline({ type: 'DETACH_SOURCE_SNAPSHOT', ...base(command.expectedRevision), snapshotId: snapshot.snapshotId });
      } else if (command.type === 'START_MODELING') {
        let state = await pipeline.read(options.scope);
        if (state.state === 'COLLECTING') {
          state = (await pipeline.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...base(command.expectedRevision) })).snapshot;
        }
        if (state.state === 'FROZEN') {
          await pipeline.execute({ type: 'RECONCILE_EVIDENCE', ...base(state.revision) });
        }
      } else if (command.type === 'RESOLVE_FINDING') {
        await executePipeline({
          type: 'RESOLVE_FINDING', ...base(command.expectedRevision), findingId: command.findingId,
          resolutionId: command.resolutionId, reason: command.reason,
        });
      } else if (command.type === 'GENERATE_MODEL') {
        let state = (await pipeline.execute({ type: 'GENERATE_CANDIDATE', ...base(command.expectedRevision) })).snapshot;
        if (state.candidate?.status === 'READY') {
          await pipeline.execute({ type: 'APPLY_CANDIDATE_TO_DRAFT', ...base(state.revision) });
        }
      } else {
        throw new WorkbenchError('COMMAND_NOT_SUPPORTED', '审核和发布请使用协作草稿流程');
      }
      return { snapshot: await hydrate() };
    },
  };
}

