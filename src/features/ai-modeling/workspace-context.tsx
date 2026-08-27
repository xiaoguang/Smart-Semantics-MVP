import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { semanticFixture } from './fixture.ts';
import { createModelCopyStore, type ModelCopyStore } from './model-copy-store.ts';
import type { WorkbenchRuntime, WorkspaceSnapshot } from './runtime-types.ts';
import type { LinguanModelSnapshot, WorkspaceSystemCode, WorkspaceVersion } from './types.ts';
import { projectLinguanWorkspaceData, type LinguanWorkspaceData } from './workspace-store-adapter.ts';
import { defaultStoreData, readStoreData, replaceStoreData, setWorkspaceWriteAccess, useStore } from '../../store/useStore.ts';
import { normalizeMetric2WorkspaceData, readMetric2WorkspaceData, replaceMetric2WorkspaceData, type Metric2WorkspaceData } from '../../pages/metric2/mockData.ts';
import { createWorkspaceRegistry, type WorkspaceDefinition } from './workspace-registry.ts';
import { projectActiveWorkspaceView, type ActiveWorkspaceView } from './active-workspace-view.ts';
import { resolveModelingScenario } from './scenario-registry.ts';
import { useCollaboration } from '../collaboration/collaboration-context.tsx';
import { useCurrentUser } from './current-user-context.tsx';
import type { MaterializedCollaborationData, PersonalDraft } from '../collaboration/types.ts';
import { workspaceForModelProject } from '../model-projects/domain-registry.ts';
import { useSourceManagement } from '../source-management/source-management-context.tsx';

const clone = <T,>(value: T): T => structuredClone(value);

function emptyWorkspaceData(): LinguanWorkspaceData {
  return Object.fromEntries(Object.keys(defaultStoreData).map((key) => [key, []])) as unknown as LinguanWorkspaceData;
}

function emptyMetricData(): Metric2WorkspaceData {
  return { events: [], entities: [], relations: [], metrics: [], rules: [], targets: [] };
}

function scopedStorage(prefix: string) {
  return {
    getItem(key: string) { return localStorage.getItem(`${prefix}:${key}`); },
    setItem(key: string, value: string) { localStorage.setItem(`${prefix}:${key}`, value); },
    removeItem(key: string) { localStorage.removeItem(`${prefix}:${key}`); },
  };
}

type WorkspaceContextValue = {
  activeSystem: WorkspaceSystemCode;
  activeVersion: WorkspaceVersion;
  workspaces: WorkspaceDefinition[];
  snapshot: WorkspaceSnapshot | null;
  model: LinguanModelSnapshot | null;
  activeView: ActiveWorkspaceView | null;
  runtime: WorkbenchRuntime;
  copies: ModelCopyStore;
  workspaceReadOnly: boolean;
  personalDraft: PersonalDraft | null;
  metric2Data: Metric2WorkspaceData;
  semanticSidecar: MaterializedCollaborationData['semanticSidecar'];
  selectSystem(systemCode: WorkspaceSystemCode): void;
  selectVersion(version: WorkspaceVersion): void;
  createWorkspace(input: { systemCode: string; displayName: string }): WorkspaceDefinition;
  getRuntime(systemCode: string): WorkbenchRuntime;
  readSnapshot(systemCode: string): Promise<WorkspaceSnapshot>;
  acceptSnapshot(snapshot: WorkspaceSnapshot): void;
  refreshModel(): void;
  resetWorkspace(): Promise<void>;
  materializeCurrent(): MaterializedCollaborationData;
  updateMetric2Data(data: Metric2WorkspaceData): void;
};

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function LinguanWorkspaceProvider({ children }: { children: React.ReactNode }) {
  const { currentUser } = useCurrentUser();
  const collaboration = useCollaboration();
  const sourceManagement = useSourceManagement();
  const registry = useRef(createWorkspaceRegistry(localStorage)).current;
  const runtimeCache = useRef(new Map<string, WorkbenchRuntime>());
  const copies = useRef(createModelCopyStore(localStorage)).current;
  const initialSystem = (collaboration.modelSpaceIds.find((modelSpaceId) => collaboration.draftFor(modelSpaceId))
    ?? collaboration.modelSpaceIds[0] ?? 'default') as WorkspaceSystemCode;
  const [activeSystem, setActiveSystem] = useState<WorkspaceSystemCode>(initialSystem);
  const [activeVersion, setActiveVersion] = useState<WorkspaceVersion>('WORKSPACE');
  const [snapshot, setSnapshot] = useState<WorkspaceSnapshot | null>(null);
  const snapshots = useRef(new Map<string, WorkspaceSnapshot>());
  const [modelRevision, setModelRevision] = useState(0);
  const [metric2Data, setMetric2Data] = useState<Metric2WorkspaceData>(() => readMetric2WorkspaceData());
  const hydrating = useRef(false);
  const draftRef = useRef<PersonalDraft | null>(null);
  const personalDraft = collaboration.draftFor(activeSystem);
  draftRef.current = personalDraft;
  const workspaceReadOnly = collaboration.viewMode !== 'DRAFT' || !personalDraft || personalDraft.status !== 'ACTIVE';
  const accessibleProjectIds = new Set(collaboration.workspaces.flatMap((workspace) => workspace.modelProjectIds));
  const workspaces = registry.list().filter((workspace) => accessibleProjectIds.has(workspace.systemCode));

  const getRuntime = (systemCode: string) => {
    const mode = collaboration.viewMode;
    const targetWorkspace = workspaceForModelProject(collaboration.workspaces, systemCode);
    const scopedDraft = collaboration.draftFor(systemCode);
    const scopeId = mode === 'DRAFT' ? scopedDraft?.draftId ?? `draft-${currentUser.userId}-${systemCode}` : `formal-${systemCode}`;
    const key = `${currentUser.userId}:${mode}:${systemCode}:${scopeId}`;
    const existing = runtimeCache.current.get(key);
    if (existing) return existing;
    const workspace = registry.get(systemCode);
    if (!workspace) throw new Error('模型空间不存在');
    const storage = mode === 'DRAFT'
      ? scopedStorage(`linguan:collaboration:runtime:${currentUser.userId}`)
      : localStorage;
    if (!targetWorkspace) throw new Error('模型项目不存在或当前用户无权访问');
    const access = collaboration.accessFor(systemCode);
    const raw = resolveModelingScenario(workspace.systemCode).createRuntime(storage, {
      sourceRuntime: sourceManagement.runtime,
      scope: { workspaceId: targetWorkspace.workspaceId, projectId: systemCode, draftId: scopeId },
      actor: { userId: currentUser.userId, role: access.role },
    });
    const guarded: WorkbenchRuntime = {
      read: raw.read,
      reset: async (code) => {
        if (mode !== 'DRAFT') throw new Error('正式模型只读，请先进入我的草稿');
        return raw.reset(code);
      },
      execute: async (command) => {
        if (mode !== 'DRAFT') throw new Error('正式模型只读，请先进入我的草稿');
        if (command.type === 'PUBLISH_MODEL') throw new Error('请先提交个人草稿，由审核者审核后再由发布者发布');
        return raw.execute(command);
      },
    };
    runtimeCache.current.set(key, guarded);
    return guarded;
  };
  const runtime = getRuntime(activeSystem);
  const readSnapshot = async (systemCode: string) => {
    const next = await getRuntime(systemCode).read(systemCode); snapshots.current.set(systemCode, next); return next;
  };
  const ensureCopies = (next: WorkspaceSnapshot) => {
    const scenario = resolveModelingScenario(next.systemCode);
    if (!scenario.fixture.documents.length) return;
    next.catalogs.forEach((catalog) => {
      if (catalog.documentVersion === 'v3') return;
      const generated = scenario.adaptPublished(catalog.documentVersion, catalog.modelVersion);
      const current = copies.read(next.systemCode, catalog.modelVersion);
      if (current?.sourceSha256 !== generated.sourceSha256 || current.projectionRevision !== generated.projectionRevision) copies.publish(generated);
    });
  };
  const materializeCurrent = (): MaterializedCollaborationData => {
    const selectedCatalogVersion = collaboration.viewMode === 'FORMAL' && activeVersion !== 'WORKSPACE'
      ? activeVersion : undefined;
    const semanticSidecar = draftRef.current?.materializedData.semanticSidecar
      ?? collaboration.catalogFor(activeSystem, selectedCatalogVersion)?.data.semanticSidecar;
    return {
      workspaceData: readStoreData() as unknown as Record<string, unknown>,
      metricData: readMetric2WorkspaceData() as unknown as Record<string, unknown>,
      workbenchState: snapshots.current.get(activeSystem) ?? snapshot,
      ...(semanticSidecar ? { semanticSidecar: clone(semanticSidecar) } : {}),
    };
  };

  useEffect(() => {
    if (collaboration.modelSpaceIds.includes(activeSystem)) return;
    setActiveSystem((collaboration.modelSpaceIds[0] ?? 'default') as WorkspaceSystemCode); setActiveVersion('WORKSPACE');
  }, [activeSystem, collaboration.activeWorkspaceId, collaboration.modelSpaceIds]);

  useEffect(() => {
    let cancelled = false;
    readSnapshot(activeSystem).then((next) => {
      if (cancelled) return; ensureCopies(next); setSnapshot(next);
      const official = collaboration.catalogFor(activeSystem);
      if (collaboration.viewMode === 'FORMAL' && official) {
        setActiveVersion(official.catalogVersion as WorkspaceVersion); return;
      }
      const latest = next.results.at(-1);
      const live = latest && !next.catalogs.some((catalog) => catalog.documentVersion === latest.documentVersion);
      setActiveVersion(live ? 'WORKSPACE' : next.currentCatalogVersion ?? 'WORKSPACE');
    });
    return () => { cancelled = true; };
  }, [activeSystem, collaboration.viewMode, currentUser.userId]);

  const acceptSnapshot = (next: WorkspaceSnapshot) => {
    const previous = snapshots.current.get(next.systemCode); const published = next.catalogs.length > (previous?.catalogs.length ?? 0);
    snapshots.current.set(next.systemCode, next); ensureCopies(next); setSnapshot(next);
    const materialized = next.sourceWorkspace?.materializedDraft;
    if (materialized && !workspaceReadOnly && draftRef.current) {
      replaceStoreData(clone(materialized.workspaceData) as unknown as LinguanWorkspaceData);
      const nextMetricData = normalizeMetric2WorkspaceData(clone(materialized.metricData) as unknown as Metric2WorkspaceData);
      replaceMetric2WorkspaceData(nextMetricData); setMetric2Data(nextMetricData);
      const proposedChanges = materialized.workbenchState && typeof materialized.workbenchState === 'object'
        && Array.isArray((materialized.workbenchState as { proposedChanges?: unknown }).proposedChanges)
        ? clone((materialized.workbenchState as { proposedChanges: PersonalDraft['changes'] }).proposedChanges)
        : undefined;
      draftRef.current = collaboration.saveDraft(
        draftRef.current.draftId,
        draftRef.current.revision,
        clone(materialized),
        proposedChanges,
      );
    }
    if (published && next.currentCatalogVersion) setActiveVersion(next.currentCatalogVersion);
    else if (next.results.at(-1) && !next.catalogs.some((catalog) => catalog.documentVersion === next.results.at(-1)?.documentVersion)) setActiveVersion('WORKSPACE');
    setModelRevision((value) => value + 1);
    if (!materialized && !workspaceReadOnly && draftRef.current) draftRef.current = collaboration.saveDraft(draftRef.current.draftId, draftRef.current.revision, materializeCurrent());
  };

  const activeView = useMemo(() => {
    void modelRevision;
    if (!snapshot || snapshot.systemCode !== activeSystem) return null;
    return projectActiveWorkspaceView(snapshot, activeVersion, (version) => copies.read(activeSystem, version));
  }, [activeSystem, activeVersion, copies, modelRevision, snapshot]);
  const model = activeView?.model ?? null;

  useEffect(() => {
    hydrating.current = true; setWorkspaceWriteAccess(false);
    const draft = collaboration.viewMode === 'DRAFT' ? collaboration.draftFor(activeSystem) : null;
    const requestedCatalogVersion = collaboration.viewMode === 'FORMAL' && activeVersion !== 'WORKSPACE'
      ? activeVersion : undefined;
    const catalog = collaboration.catalogFor(activeSystem, requestedCatalogVersion);
    let workspaceData: LinguanWorkspaceData;
    let metricData: Metric2WorkspaceData;
    if (draft) {
      workspaceData = clone(draft.materializedData.workspaceData) as unknown as LinguanWorkspaceData;
      metricData = normalizeMetric2WorkspaceData(clone(draft.materializedData.metricData) as unknown as Metric2WorkspaceData);
    } else if (catalog) {
      workspaceData = clone(catalog.data.workspaceData) as unknown as LinguanWorkspaceData;
      metricData = normalizeMetric2WorkspaceData(clone(catalog.data.metricData) as unknown as Metric2WorkspaceData);
    } else if (activeView?.model) {
      workspaceData = projectLinguanWorkspaceData(activeView.model); metricData = activeView.metric2Data;
    } else {
      workspaceData = activeSystem === 'default' ? clone(defaultStoreData) : emptyWorkspaceData();
      metricData = activeSystem === 'default' ? readMetric2WorkspaceData() : emptyMetricData();
    }
    replaceStoreData(workspaceData); replaceMetric2WorkspaceData(metricData); setMetric2Data(clone(metricData));
    setWorkspaceWriteAccess(Boolean(draft)); hydrating.current = false;
  }, [activeSystem, activeVersion, activeView, collaboration.revision, collaboration.viewMode, modelRevision]);

  useEffect(() => useStore.subscribe(() => {
    if (hydrating.current || workspaceReadOnly || !draftRef.current) return;
    try { draftRef.current = collaboration.saveDraft(draftRef.current.draftId, draftRef.current.revision, materializeCurrent()); }
    catch { setWorkspaceWriteAccess(false); }
  }), [activeSystem, workspaceReadOnly]);

  const semanticSidecar = personalDraft?.materializedData.semanticSidecar
    ?? collaboration.catalogFor(activeSystem, activeVersion !== 'WORKSPACE' ? activeVersion : undefined)?.data.semanticSidecar;

  return <WorkspaceContext.Provider value={{
    activeSystem, activeVersion, workspaces, snapshot, model, activeView, runtime, copies,
    workspaceReadOnly, personalDraft, metric2Data, semanticSidecar,
    selectSystem(systemCode) {
      const targetWorkspace = workspaceForModelProject(collaboration.workspaces, systemCode);
      if (!targetWorkspace || !registry.get(systemCode)) throw new Error('模型项目不存在或当前用户无权访问');
      if (targetWorkspace.workspaceId !== collaboration.activeWorkspaceId) collaboration.selectWorkspace(targetWorkspace.workspaceId);
      setActiveSystem(systemCode); const known = snapshots.current.get(systemCode);
      const live = known?.results.at(-1) && !known.catalogs.some((catalog) => catalog.documentVersion === known.results.at(-1)?.documentVersion);
      const official = collaboration.catalogFor(systemCode);
      setActiveVersion((official?.catalogVersion as WorkspaceVersion | undefined)
        ?? (live ? 'WORKSPACE' : known?.currentCatalogVersion ?? 'WORKSPACE'));
    },
    selectVersion: setActiveVersion,
    createWorkspace(input) { const created = registry.create(input); return created; },
    getRuntime, readSnapshot, acceptSnapshot, materializeCurrent,
    updateMetric2Data(data) {
      if (workspaceReadOnly || !draftRef.current) throw new Error('正式模型只读，请先进入我的草稿');
      replaceMetric2WorkspaceData(data); setMetric2Data(clone(data));
      draftRef.current = collaboration.saveDraft(draftRef.current.draftId, draftRef.current.revision, {
        workspaceData: readStoreData() as unknown as Record<string, unknown>,
        metricData: data as unknown as Record<string, unknown>, workbenchState: snapshots.current.get(activeSystem) ?? snapshot,
        ...(draftRef.current.materializedData.semanticSidecar
          ? { semanticSidecar: clone(draftRef.current.materializedData.semanticSidecar) } : {}),
      });
    },
    refreshModel: () => setModelRevision((value) => value + 1),
    async resetWorkspace() {
      const next = await runtime.reset(activeSystem);
      if (activeSystem === semanticFixture.system.code) copies.reset();
      setActiveVersion('WORKSPACE'); acceptSnapshot(next);
    },
  }}>{children}</WorkspaceContext.Provider>;
}

export function useLinguanWorkspace() {
  const context = useContext(WorkspaceContext);
  if (!context) throw new Error('LinguanWorkspaceProvider 未初始化');
  return context;
}
