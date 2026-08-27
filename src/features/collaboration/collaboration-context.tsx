import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { createCollaborationRuntime } from './runtime.ts';
import type {
  ChangeRequest, CollaborationCatalog, CollaborationTask, CollaborationWorkspace,
  DraftIntegrityIssue, MaterializedCollaborationData, PersonalDraft, WorkspaceRole,
} from './types.ts';
import { useCurrentUser } from '../ai-modeling/current-user-context.tsx';

export type CollaborationViewMode = 'FORMAL' | 'DRAFT';
type CollaborationAccess = {
  role: WorkspaceRole;
  canCreateDraft: boolean;
  canEditDraft: boolean;
  canReview: boolean;
  canPublish: boolean;
};

type CollaborationContextValue = {
  activeWorkspaceId: string;
  workspaces: CollaborationWorkspace[];
  modelSpaceIds: string[];
  viewMode: CollaborationViewMode;
  revision: number;
  tasks: CollaborationTask[];
  selectWorkspace(workspaceId: string): void;
  setViewMode(mode: CollaborationViewMode): void;
  accessFor(modelSpaceId: string): CollaborationAccess;
  catalogsFor(modelSpaceId: string): CollaborationCatalog[];
  catalogFor(modelSpaceId: string, catalogVersion?: string): CollaborationCatalog | null;
  draftFor(modelSpaceId: string): PersonalDraft | null;
  draftIssueFor(modelSpaceId: string): DraftIntegrityIssue | null;
  openDraft(modelSpaceId: string, materialized: MaterializedCollaborationData): PersonalDraft;
  saveDraft(draftId: string, expectedRevision: number, materialized: MaterializedCollaborationData, changes?: PersonalDraft['changes']): PersonalDraft;
  submitDraft(draft: PersonalDraft, summary: string): ChangeRequest;
  reviewRequest(requestId: string, expectedRevision: number, decision: 'APPROVE' | 'REQUEST_CHANGES' | 'REJECT', reason: string): ChangeRequest;
  publishRequest(requestId: string, expectedRevision: number, reason: string): CollaborationCatalog;
  getRequest(requestId: string): ChangeRequest | null;
  resetDemo(): void;
};

const CollaborationContext = createContext<CollaborationContextValue | null>(null);
const uiKey = (userId: string) => `linguan:collaboration:ui:v1:${userId}`;
const migrationKey = 'linguan:migration:collaboration-v1';

function readUi(userId: string, fallback: string) {
  try { return JSON.parse(localStorage.getItem(uiKey(userId)) ?? '{}').activeWorkspaceId ?? fallback; }
  catch { return fallback; }
}

function migrateLegacy(runtime: ReturnType<typeof createCollaborationRuntime>) {
  if (localStorage.getItem(migrationKey)) return;
  const warnings: string[] = [];
  try {
    const raw = localStorage.getItem('linguan:workspace-data:v3');
    if (raw) {
      const parsed = JSON.parse(raw) as { workspaces?: Record<string, Record<string, unknown>> };
      for (const [key, workspaceData] of Object.entries(parsed.workspaces ?? {})) {
        const [modelSpaceId, version] = key.split(':');
        if (!version || !modelSpaceId) continue;
        const data = workspaceData as Record<string, unknown>;
        const materialized = {
          workspaceData: data,
          metricData: {
            entities: data.entities ?? [], events: data.events ?? [],
            relations: data.semanticRelations ?? [], metrics: data.metrics ?? [],
            rules: data.businessRules ?? [], targets: data.metricTargets ?? [],
          },
          workbenchState: null,
        };
        try {
          if (/^V\d+$/.test(version)) runtime.migrateLegacyCatalog({ modelSpaceId, catalogVersion: version, data: materialized });
          if (version === 'WORKSPACE') runtime.openDraft({ actorUserId: 'user_administer', modelSpaceId, title: '迁移的旧工作副本', materialized });
        } catch (error) { warnings.push(error instanceof Error ? error.message : String(error)); }
      }
    }
  } catch { warnings.push('旧工作副本无法解析，原始键已保留'); }
  localStorage.setItem(migrationKey, JSON.stringify({ schemaVersion: 1, completedAt: new Date().toISOString(), warnings }));
}

export function CollaborationProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useCurrentUser();
  const runtime = useMemo(() => createCollaborationRuntime(localStorage), []);
  const workspaces = runtime.listWorkspacesFor(currentUser.userId);
  const initialWorkspace = workspaces[0]?.workspaceId ?? '';
  const [activeWorkspaceId, setActiveWorkspaceId] = useState(() => readUi(currentUser.userId, initialWorkspace));
  const [viewMode, setViewMode] = useState<CollaborationViewMode>('FORMAL');
  const [revision, setRevision] = useState(0);
  const activeWorkspace = workspaces.find((item) => item.workspaceId === activeWorkspaceId) ?? workspaces[0];
  const modelSpaceIds = activeWorkspace?.modelProjectIds ?? [];
  const refresh = () => setRevision((value) => value + 1);
  const notify = () => {
    refresh();
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('linguan:collaboration:v1'); channel.postMessage({ type: 'UPDATED' }); channel.close();
    }
  };

  useEffect(() => { migrateLegacy(runtime); refresh(); }, [runtime]);
  useEffect(() => {
    const nextWorkspaces = runtime.listWorkspacesFor(currentUser.userId);
    setActiveWorkspaceId(readUi(currentUser.userId, nextWorkspaces[0]?.workspaceId ?? ''));
    setViewMode('FORMAL');
  }, [currentUser.userId, runtime]);
  useEffect(() => {
    const onStorage = (event: StorageEvent) => { if (event.key?.startsWith('linguan:collaboration:')) refresh(); };
    window.addEventListener('storage', onStorage);
    const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('linguan:collaboration:v1') : null;
    if (channel) channel.onmessage = refresh;
    return () => { window.removeEventListener('storage', onStorage); channel?.close(); };
  }, []);

  const accessFor = (modelSpaceId: string): CollaborationAccess => {
    const workspace = workspaces.find((item) => item.modelProjectIds.includes(modelSpaceId));
    if (!workspace) throw new Error('当前用户不能访问此模型空间');
    const role = runtime.getRole(currentUser.userId, workspace.workspaceId);
    const draft = runtime.getActiveDraft(currentUser.userId, modelSpaceId);
    return {
      role,
      canCreateDraft: role === 'EDITOR' || role === 'ADMIN',
      canEditDraft: Boolean(draft) && (role === 'EDITOR' || role === 'ADMIN'),
      canReview: role === 'REVIEWER' || role === 'ADMIN',
      canPublish: role === 'PUBLISHER' || role === 'ADMIN',
    };
  };
  const value: CollaborationContextValue = {
    activeWorkspaceId: activeWorkspace?.workspaceId ?? '', workspaces, modelSpaceIds, viewMode, revision,
    tasks: runtime.listTasks(currentUser.userId),
    selectWorkspace(workspaceId) {
      if (!workspaces.some((item) => item.workspaceId === workspaceId)) throw new Error('无权访问此工作空间');
      setActiveWorkspaceId(workspaceId); setViewMode('FORMAL');
      localStorage.setItem(uiKey(currentUser.userId), JSON.stringify({ schemaVersion: 1, activeWorkspaceId: workspaceId }));
    },
    setViewMode,
    accessFor,
    catalogsFor(modelSpaceId) { void revision; return runtime.readShared().catalogs[modelSpaceId] ?? []; },
    catalogFor(modelSpaceId, catalogVersion) { void revision; return runtime.getCatalog(modelSpaceId, catalogVersion); },
    draftFor(modelSpaceId) { void revision; return runtime.getActiveDraft(currentUser.userId, modelSpaceId); },
    draftIssueFor(modelSpaceId) { void revision; return runtime.getDraftIssue(currentUser.userId, modelSpaceId); },
    openDraft(modelSpaceId, materialized) { const draft = runtime.openDraft({ actorUserId: currentUser.userId, modelSpaceId, materialized }); notify(); setViewMode('DRAFT'); return draft; },
    saveDraft(draftId, expectedRevision, materialized, changes) { const draft = runtime.saveDraft({ actorUserId: currentUser.userId, draftId, expectedRevision, materialized, changes }); notify(); return draft; },
    submitDraft(draft, summary) { const request = runtime.submitDraft({ actorUserId: currentUser.userId, draftId: draft.draftId, expectedDraftRevision: draft.revision, summary }); notify(); setViewMode('FORMAL'); return request; },
    reviewRequest(requestId, expectedRevision, decision, reason) { const request = runtime.reviewRequest({ actorUserId: currentUser.userId, requestId, expectedRevision, decision, reason }); notify(); return request; },
    publishRequest(requestId, expectedRevision, reason) { const { catalog } = runtime.publishRequest({ actorUserId: currentUser.userId, requestId, expectedRevision, reason }); notify(); return catalog; },
    getRequest: runtime.getRequest,
    resetDemo() { runtime.resetDemo(currentUser.userId); notify(); setViewMode('FORMAL'); },
  };
  return <CollaborationContext.Provider value={value} key={currentUser.userId}>{children}</CollaborationContext.Provider>;
}

export function useCollaboration() {
  const context = useContext(CollaborationContext);
  if (!context) throw new Error('CollaborationProvider 未初始化');
  return context;
}
