import { demoUsers, seedCollaborationState, workspaceDefinitions, workspaceMemberships } from './fixtures.ts';
import type {
  ChangeRequest, CollaborationCatalog, CollaborationSharedState, CollaborationTask,
  DemoSession, DraftIntegrityIssue, MaterializedCollaborationData, PersonalDraft, WorkspaceRole,
} from './types.ts';
import { modelProject, packageForLegacySystem } from '../model-projects/domain-registry.ts';
import { deriveDraftObjectChanges } from './draft-change-derivation.ts';
import { assertCatalogEvidenceSidecar, projectCatalogBrowser } from './catalog-browser-adapter.ts';

export { demoUsers, seedCollaborationState, workspaceDefinitions } from './fixtures.ts';
export type { CollaborationStorage };

const sharedKey = 'linguan:collaboration:shared:v4';
const legacySharedKeys = [
  'linguan:collaboration:shared:v3', 'linguan:collaboration:shared:v2', 'linguan:collaboration:shared:v1',
] as const;
const migrationKey = 'linguan:migration:collaboration-v4';
const draftKey = (userId: string) => `linguan:collaboration:drafts:v4:${userId}`;
const legacyDraftKeys = (userId: string) => [
  `linguan:collaboration:drafts:v3:${userId}`,
  `linguan:collaboration:drafts:v2:${userId}`,
  `linguan:collaboration:drafts:v1:${userId}`,
];

type CollaborationStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
type OpenDraftInput = { actorUserId: string; modelSpaceId: string; materialized: MaterializedCollaborationData; title?: string };

const clone = <T>(value: T): T => structuredClone(value);
const now = () => new Date().toISOString();
const id = (prefix: string) => `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
const isRecord = (value: unknown): value is Record<string, unknown> => Boolean(value) && typeof value === 'object' && !Array.isArray(value);

function parse<T>(storage: CollaborationStorage, key: string, fallback: T): T {
  try { return storage.getItem(key) ? JSON.parse(storage.getItem(key)!) as T : clone(fallback); } catch { return clone(fallback); }
}

function workspaceForModel(modelSpaceId: string) {
  const workspace = workspaceDefinitions.find((item) => item.modelProjectIds.includes(modelSpaceId));
  if (!workspace) throw new Error('模型空间不属于任何协作工作空间');
  return workspace;
}

function user(userId: string) {
  const value = demoUsers.find((item) => item.userId === userId);
  if (!value) throw new Error('当前用户不存在');
  return value;
}

function roleFor(userId: string, workspaceId: string): WorkspaceRole {
  const membership = workspaceMemberships.find((item) => item.userId === userId
    && item.workspaceId === workspaceId && item.status === 'ACTIVE');
  if (!membership) throw new Error('当前用户不属于此工作空间');
  return membership.role;
}

function requireRole(userId: string, workspaceId: string, allowed: WorkspaceRole[]) {
  const role = roleFor(userId, workspaceId);
  if (!allowed.includes(role)) throw new Error('当前角色没有执行此操作的权限');
  return role;
}

function assertRevision(actual: number, expected: number) {
  if (actual !== expected) throw new Error('数据已更新，请刷新后重试');
}

function nextVersion(catalogs: CollaborationCatalog[]) {
  const current = catalogs.at(-1)?.catalogVersion ?? 'V0';
  return `V${Number(current.replace(/^V/, '')) + 1}`;
}

export function createCollaborationRuntime(storage: CollaborationStorage) {
  const seed = seedCollaborationState();
  if (!storage.getItem(sharedKey)) {
    const next = clone(seed.shared);
    const legacyRaw = legacySharedKeys.map((key) => storage.getItem(key)).find(Boolean);
    if (legacyRaw) {
      try {
        const legacy = JSON.parse(legacyRaw) as Omit<CollaborationSharedState, 'schemaVersion' | 'packageCatalogHistory' | 'migrationWarnings'> & { schemaVersion: number };
        if (legacy.schemaVersion >= 2 && 'packageCatalogHistory' in legacy) {
          next.catalogs = clone(legacy.catalogs ?? {});
          next.packageCatalogHistory = clone((legacy as unknown as CollaborationSharedState).packageCatalogHistory ?? {});
          next.requests = clone(legacy.requests ?? []);
          next.migrationWarnings = clone((legacy as unknown as CollaborationSharedState).migrationWarnings ?? []);
        } else {
          Object.entries(legacy.catalogs ?? {}).forEach(([legacySystemCode, catalogs]) => {
            const project = modelProject(legacySystemCode);
            if (project) next.catalogs[project.projectId] = clone(catalogs);
            else {
              const evidencePackage = packageForLegacySystem(legacySystemCode);
              if (evidencePackage) next.packageCatalogHistory[evidencePackage.packageId] = clone(catalogs);
              else next.migrationWarnings.push(`未识别的历史模型空间已保留在旧键：${legacySystemCode}`);
            }
          });
          next.requests.push(...(legacy.requests ?? []).map((request) => {
            const evidencePackage = packageForLegacySystem(request.modelSpaceId);
            if (!evidencePackage || modelProject(request.modelSpaceId)) return clone(request);
            return {
              ...clone(request), modelSpaceId: evidencePackage.projectId,
              evidencePackageId: evidencePackage.packageId,
              status: request.status === 'PUBLISHED' || request.status === 'REJECTED' ? request.status : 'CONFLICTED',
              migrationNotice: '旧证据包申请已保留，需在统一项目中重新确认。',
            } as ChangeRequest;
          }));
        }
        next.revision = Math.max(next.revision, legacy.revision ?? 1) + 1;
      } catch {
        next.migrationWarnings.push('旧协作状态无法解析，原始键已保留。');
      }
    }
    if (!(next.catalogs.guanyijia_erp?.length)) {
      next.catalogs.guanyijia_erp = clone(seed.shared.catalogs.guanyijia_erp);
      next.migrationWarnings.push('已补入管伊佳证据基线 V1；旧协作状态和历史键保持不变。');
    }
    next.schemaVersion = 4;
    storage.setItem(sharedKey, JSON.stringify(next));
    storage.setItem(migrationKey, JSON.stringify({ schemaVersion: 4, completed: true, warnings: next.migrationWarnings }));
  }
  Object.entries(seed.drafts).forEach(([userId, drafts]) => {
    if (storage.getItem(draftKey(userId))) return;
    const legacyRaw = legacyDraftKeys(userId).map((key) => storage.getItem(key)).find(Boolean);
    if (!legacyRaw) { storage.setItem(draftKey(userId), JSON.stringify(drafts)); return; }
    try {
      const legacy = JSON.parse(legacyRaw) as PersonalDraft[];
      const migrated = legacy.map((draft) => {
        const evidencePackage = packageForLegacySystem(draft.modelSpaceId);
        if (!evidencePackage || modelProject(draft.modelSpaceId)) return draft;
        return {
          ...draft, workspaceId: 'retail_semantic_modeling', modelSpaceId: evidencePackage.projectId,
          title: `${draft.title}（迁移待确认）`, baseCatalogVersion: 'V1', baseFingerprint: 'catalog_group_retail_v1',
          changes: [
            ...(Array.isArray(draft.changes) ? draft.changes : []),
            { changeId: 'migration_package_scope', category: '迁移', summary: `原内容来自证据包“${evidencePackage.displayName}”，需重新确认对象关联` },
          ],
        };
      });
      storage.setItem(draftKey(userId), JSON.stringify(migrated));
    } catch { storage.setItem(draftKey(userId), JSON.stringify(drafts)); }
  });
  const readShared = () => parse(storage, sharedKey, seed.shared);
  const writeShared = (state: CollaborationSharedState) => storage.setItem(sharedKey, JSON.stringify(state));
  const inspectDrafts = (userId: string): { drafts: PersonalDraft[]; issues: DraftIntegrityIssue[] } => {
    const raw = storage.getItem(draftKey(userId));
    if (!raw) return { drafts: [], issues: [] };
    let values: unknown;
    try { values = JSON.parse(raw); }
    catch {
      return { drafts: [], issues: [{ userId, message: '草稿内容暂时无法显示：草稿记录不是有效 JSON。' }] };
    }
    if (!Array.isArray(values)) {
      return { drafts: [], issues: [{ userId, message: '草稿内容暂时无法显示：草稿记录结构不完整。' }] };
    }
    const drafts: PersonalDraft[] = [];
    const issues: DraftIntegrityIssue[] = [];
    values.forEach((value) => {
      const record = isRecord(value) ? value : undefined;
      const draftId = typeof record?.draftId === 'string' ? record.draftId : undefined;
      const modelSpaceId = typeof record?.modelSpaceId === 'string' ? record.modelSpaceId : undefined;
      const materialized = isRecord(record?.materializedData) ? record.materializedData : null;
      const hasCoreIdentity = Boolean(
        draftId && modelSpaceId && typeof record?.workspaceId === 'string'
        && typeof record?.ownerUserId === 'string' && typeof record?.status === 'string',
      );
      if (!hasCoreIdentity || !materialized || !isRecord(materialized.workspaceData) || !isRecord(materialized.metricData)) {
        issues.push({
          draftId, modelSpaceId, userId,
          message: '草稿内容暂时无法显示：旧草稿缺少完整模型数据，原始记录已保留。',
        });
        return;
      }
      const safeRecord = record!;
      drafts.push(clone({
        ...safeRecord,
        materializedData: {
          ...materialized,
          workbenchState: 'workbenchState' in materialized ? materialized.workbenchState : null,
        },
        changes: Array.isArray(safeRecord.changes) ? safeRecord.changes : [],
      }) as PersonalDraft);
    });
    return { drafts, issues };
  };
  const readDrafts = (userId: string) => inspectDrafts(userId).drafts;
  const draftIssueFor = (userId: string, modelSpaceId: string) => inspectDrafts(userId).issues
    .find((issue) => !issue.modelSpaceId || issue.modelSpaceId === modelSpaceId) ?? null;
  const writeDrafts = (userId: string, drafts: PersonalDraft[]) => storage.setItem(draftKey(userId), JSON.stringify(drafts));
  const getRequest = (requestId: string) => readShared().requests.find((item) => item.requestId === requestId) ?? null;

  return {
    authenticate(username: string, password: string) {
      const match = demoUsers.find((item) => item.username === username && item.demoPassword === password);
      if (!match) throw new Error('用户名或密码错误');
      return clone(match);
    },
    createSession(username: string, password: string, authenticatedAt = now()): DemoSession {
      const match = this.authenticate(username, password);
      return { schemaVersion: 1, userId: match.userId, authenticatedAt };
    },
    getUser: user,
    getRole(userId: string, workspaceId: string) { return roleFor(userId, workspaceId); },
    listWorkspacesFor(userId: string) {
      return workspaceDefinitions.filter((workspace) => workspaceMemberships.some((membership) => (
        membership.userId === userId && membership.workspaceId === workspace.workspaceId && membership.status === 'ACTIVE'
      )));
    },
    readShared() { return clone(readShared()); },
    getCatalog(modelSpaceId: string, catalogVersion?: string) {
      const catalogs = readShared().catalogs[modelSpaceId] ?? [];
      return clone((catalogVersion
        ? catalogs.find((item) => item.catalogVersion === catalogVersion)
        : catalogs.at(-1)) ?? null);
    },
    getPackageCatalog(packageId: string, catalogVersion?: string) {
      const catalogs = readShared().packageCatalogHistory[packageId] ?? [];
      return clone((catalogVersion ? catalogs.find((item) => item.catalogVersion === catalogVersion) : catalogs.at(-1)) ?? null);
    },
    migrateLegacyCatalog(input: { modelSpaceId: string; catalogVersion: string; data: MaterializedCollaborationData }) {
      const shared = readShared();
      const evidencePackage = !modelProject(input.modelSpaceId) ? packageForLegacySystem(input.modelSpaceId) : undefined;
      const catalogs = evidencePackage
        ? shared.packageCatalogHistory[evidencePackage.packageId] ?? []
        : shared.catalogs[input.modelSpaceId] ?? [];
      const existing = catalogs.find((item) => item.catalogVersion === input.catalogVersion);
      if (existing) return clone(existing);
      const catalog: CollaborationCatalog = {
        catalogId: `legacy_${input.modelSpaceId}_${input.catalogVersion}`,
        catalogVersion: input.catalogVersion, fingerprint: `legacy:${input.modelSpaceId}:${input.catalogVersion}`,
        modelSpaceId: input.modelSpaceId, data: clone(input.data), authorUserId: 'LEGACY',
        reviewerUserIds: ['LEGACY'], publisherUserId: 'LEGACY',
        publishedAt: '1970-01-01T00:00:00.000Z', requestId: `legacy_${input.modelSpaceId}_${input.catalogVersion}`,
      };
      catalogs.push(catalog);
      catalogs.sort((left, right) => Number(left.catalogVersion.replace(/^V/, '')) - Number(right.catalogVersion.replace(/^V/, '')));
      if (evidencePackage) shared.packageCatalogHistory[evidencePackage.packageId] = catalogs;
      else shared.catalogs[input.modelSpaceId] = catalogs;
      shared.revision += 1; writeShared(shared);
      return clone(catalog);
    },
    listDrafts(userId: string) { user(userId); return clone(readDrafts(userId)); },
    getDraftIssue(userId: string, modelSpaceId: string) { user(userId); return clone(draftIssueFor(userId, modelSpaceId)); },
    getActiveDraft(userId: string, modelSpaceId: string) {
      return clone(readDrafts(userId).find((item) => item.modelSpaceId === modelSpaceId && item.status === 'ACTIVE') ?? null);
    },
    openDraft(input: OpenDraftInput) {
      const workspace = workspaceForModel(input.modelSpaceId);
      try { requireRole(input.actorUserId, workspace.workspaceId, ['EDITOR', 'ADMIN']); }
      catch { throw new Error('当前角色没有创建草稿权限'); }
      const issue = draftIssueFor(input.actorUserId, input.modelSpaceId);
      if (issue) throw new Error(issue.message);
      const drafts = readDrafts(input.actorUserId);
      const existing = drafts.find((item) => item.modelSpaceId === input.modelSpaceId && item.status === 'ACTIVE');
      if (existing) return clone(existing);
      const catalog = readShared().catalogs[input.modelSpaceId]?.at(-1);
      const draftId = id('draft');
      const created: PersonalDraft = {
        draftId, workspaceId: workspace.workspaceId, modelSpaceId: input.modelSpaceId,
        ownerUserId: input.actorUserId, title: input.title ?? '未命名个人草稿',
        baseCatalogVersion: catalog?.catalogVersion ?? 'INITIAL', baseFingerprint: catalog?.fingerprint ?? 'INITIAL',
        revision: 1, status: 'ACTIVE', materializedData: clone(input.materialized),
        changes: deriveDraftObjectChanges({
          draftId, modelSpaceId: input.modelSpaceId, base: catalog?.data, next: input.materialized,
        }),
        createdAt: now(), updatedAt: now(),
      };
      drafts.push(created); writeDrafts(input.actorUserId, drafts); return clone(created);
    },
    saveDraft(input: { actorUserId: string; draftId: string; expectedRevision: number; materialized: MaterializedCollaborationData; changes?: PersonalDraft['changes'] }) {
      const drafts = readDrafts(input.actorUserId);
      const draft = drafts.find((item) => item.draftId === input.draftId);
      if (!draft || draft.ownerUserId !== input.actorUserId || draft.status !== 'ACTIVE') throw new Error('个人草稿不可编辑');
      requireRole(input.actorUserId, draft.workspaceId, ['EDITOR', 'ADMIN']); assertRevision(draft.revision, input.expectedRevision);
      const base = readShared().catalogs[draft.modelSpaceId]?.find((item) => item.catalogVersion === draft.baseCatalogVersion)?.data;
      draft.materializedData = clone(input.materialized);
      draft.changes = clone(input.changes ?? deriveDraftObjectChanges({
        draftId: draft.draftId, modelSpaceId: draft.modelSpaceId, base, next: input.materialized,
      }));
      draft.revision += 1; draft.updatedAt = now(); writeDrafts(input.actorUserId, drafts); return clone(draft);
    },
    submitDraft(input: { actorUserId: string; draftId: string; expectedDraftRevision: number; summary: string }) {
      const drafts = readDrafts(input.actorUserId);
      const draft = drafts.find((item) => item.draftId === input.draftId);
      if (!draft || draft.ownerUserId !== input.actorUserId || draft.status !== 'ACTIVE') throw new Error('个人草稿不能提交');
      requireRole(input.actorUserId, draft.workspaceId, ['EDITOR', 'ADMIN']); assertRevision(draft.revision, input.expectedDraftRevision);
      if (draft.changes.length === 0) throw new Error('草稿没有可提交的修改');
      const shared = readShared();
      const request: ChangeRequest = {
        requestId: id('request'), draftId: draft.draftId, draftRevision: draft.revision,
        workspaceId: draft.workspaceId, modelSpaceId: draft.modelSpaceId, title: input.summary,
        authorUserId: input.actorUserId, baseCatalogVersion: draft.baseCatalogVersion,
        baseFingerprint: draft.baseFingerprint, proposedData: clone(draft.materializedData), changes: clone(draft.changes),
        evidenceRefs: [...new Set(draft.changes.flatMap((change) => change.evidenceRefs ?? []))], status: 'SUBMITTED', approvals: [], expectedRevision: 1, submittedAt: now(),
      };
      draft.status = 'SUBMITTED'; draft.updatedAt = now(); shared.requests.push(request); shared.revision += 1;
      writeDrafts(input.actorUserId, drafts); writeShared(shared); return clone(request);
    },
    getRequest,
    reviewRequest(input: { actorUserId: string; requestId: string; expectedRevision: number; decision: 'APPROVE' | 'REQUEST_CHANGES' | 'REJECT'; reason: string }) {
      const shared = readShared(); const request = shared.requests.find((item) => item.requestId === input.requestId);
      if (!request || !['SUBMITTED', 'IN_REVIEW'].includes(request.status)) throw new Error('变更申请不能审核');
      if (request.authorUserId === input.actorUserId) throw new Error('作者不能审核自己的变更');
      requireRole(input.actorUserId, request.workspaceId, ['REVIEWER', 'ADMIN']); assertRevision(request.expectedRevision, input.expectedRevision);
      if (input.decision !== 'APPROVE' && !/[\u3400-\u9fff]/.test(input.reason)) throw new Error('请填写中文审核理由');
      const decision = input.decision === 'APPROVE' ? 'APPROVED' : input.decision === 'REJECT' ? 'REJECTED' : 'CHANGES_REQUESTED';
      request.status = decision; request.expectedRevision += 1;
      request.approvals.push({ reviewerUserId: input.actorUserId, reviewerName: user(input.actorUserId).displayName, decision, reason: input.reason.trim() || '无补充意见', decidedAt: now() });
      if (decision === 'CHANGES_REQUESTED') {
        const drafts = readDrafts(request.authorUserId); const draft = drafts.find((item) => item.draftId === request.draftId);
        if (draft) { draft.status = 'ACTIVE'; draft.revision += 1; draft.updatedAt = now(); writeDrafts(request.authorUserId, drafts); }
      }
      shared.revision += 1; writeShared(shared); return clone(request);
    },
    publishRequest(input: { actorUserId: string; requestId: string; expectedRevision: number; reason: string }) {
      const shared = readShared(); const request = shared.requests.find((item) => item.requestId === input.requestId);
      if (!request || request.status !== 'APPROVED') throw new Error('变更申请不能发布');
      if (request.authorUserId === input.actorUserId) throw new Error('作者不能发布自己的变更');
      requireRole(input.actorUserId, request.workspaceId, ['PUBLISHER', 'ADMIN']); assertRevision(request.expectedRevision, input.expectedRevision);
      if (!/[\u3400-\u9fff]/.test(input.reason)) throw new Error('请填写中文发布理由');
      if (request.proposedData.semanticSidecar
        && request.proposedData.semanticSidecar.systemCode !== request.modelSpaceId) {
        throw new Error('证据 Sidecar 不属于当前模型项目');
      }
      if (request.proposedData.semanticSidecar?.schemaVersion === 2) {
        assertCatalogEvidenceSidecar(request.proposedData.semanticSidecar);
        projectCatalogBrowser({
          catalogId: 'publication-check', catalogVersion: 'CHECK', fingerprint: 'CHECK',
          modelSpaceId: request.modelSpaceId, data: request.proposedData,
          authorUserId: request.authorUserId, reviewerUserIds: [], publisherUserId: input.actorUserId,
          publishedAt: now(), requestId: request.requestId,
        });
      }
      const catalogs = shared.catalogs[request.modelSpaceId] ?? [];
      if ((catalogs.at(-1)?.fingerprint ?? 'INITIAL') !== request.baseFingerprint) {
        request.status = 'CONFLICTED'; request.expectedRevision += 1; shared.revision += 1; writeShared(shared);
        throw new Error('正式基线已更新，请作者基于新版本重新确认');
      }
      const catalogVersion = nextVersion(catalogs);
      const catalog: CollaborationCatalog = {
        catalogId: id('catalog'), catalogVersion, fingerprint: id('fingerprint'), modelSpaceId: request.modelSpaceId,
        data: clone(request.proposedData), authorUserId: request.authorUserId,
        reviewerUserIds: request.approvals.filter((item) => item.decision === 'APPROVED').map((item) => item.reviewerUserId),
        publisherUserId: input.actorUserId, publishedAt: now(), requestId: request.requestId,
        publicationReason: input.reason,
      };
      catalogs.push(catalog); shared.catalogs[request.modelSpaceId] = catalogs; request.status = 'PUBLISHED';
      request.publishedCatalogVersion = catalogVersion; request.expectedRevision += 1; shared.revision += 1;
      const drafts = readDrafts(request.authorUserId); const draft = drafts.find((item) => item.draftId === request.draftId);
      if (draft) { draft.status = 'ARCHIVED'; draft.updatedAt = now(); writeDrafts(request.authorUserId, drafts); }
      writeShared(shared); return { request: clone(request), catalog: clone(catalog) };
    },
    listTasks(actorUserId: string): CollaborationTask[] {
      const tasks = new Map<string, CollaborationTask>(); const allowed = this.listWorkspacesFor(actorUserId);
      allowed.forEach((workspace) => {
        const role = roleFor(actorUserId, workspace.workspaceId);
        const enrich = (request: ChangeRequest) => ({
          workspaceName: workspace.displayName,
          modelProjectName: modelProject(request.modelSpaceId)?.displayName ?? request.modelSpaceId,
          authorName: user(request.authorUserId).displayName,
          changeCount: request.changes.length,
          updatedAt: request.approvals.at(-1)?.decidedAt ?? request.submittedAt,
        });
        if (role === 'EDITOR' || role === 'ADMIN') readShared().requests
          .filter((request) => request.workspaceId === workspace.workspaceId && request.authorUserId === actorUserId && request.status === 'CHANGES_REQUESTED')
          .forEach((request) => tasks.set(`edit:${request.requestId}`, {
            taskId: `edit:${request.requestId}`, kind: 'EDIT', title: request.title,
            modelSpaceId: request.modelSpaceId, requestId: request.requestId,
            reason: request.approvals.at(-1)?.reason, ...enrich(request),
          }));
        if (role === 'REVIEWER' || role === 'ADMIN') readShared().requests
          .filter((request) => request.workspaceId === workspace.workspaceId && ['SUBMITTED', 'IN_REVIEW'].includes(request.status) && request.authorUserId !== actorUserId)
          .forEach((request) => tasks.set(`review:${request.requestId}`, {
            taskId: `review:${request.requestId}`, kind: 'REVIEW', title: request.title,
            modelSpaceId: request.modelSpaceId, requestId: request.requestId, ...enrich(request),
          }));
        if (role === 'PUBLISHER' || role === 'ADMIN') readShared().requests
          .filter((request) => request.workspaceId === workspace.workspaceId && request.status === 'APPROVED' && request.authorUserId !== actorUserId)
          .forEach((request) => tasks.set(`publish:${request.requestId}`, {
            taskId: `publish:${request.requestId}`, kind: 'PUBLISH', title: request.title,
            modelSpaceId: request.modelSpaceId, requestId: request.requestId, ...enrich(request),
          }));
      });
      return [...tasks.values()];
    },
    resetDemo(actorUserId: string) {
      const isAdmin = workspaceMemberships.some((item) => item.userId === actorUserId && item.role === 'ADMIN' && item.status === 'ACTIVE');
      if (!isAdmin) throw new Error('只有管理员可以恢复协作演示');
      storage.setItem(sharedKey, JSON.stringify(seed.shared));
      demoUsers.forEach((item) => storage.removeItem(draftKey(item.userId)));
      Object.entries(seed.drafts).forEach(([userId, drafts]) => storage.setItem(draftKey(userId), JSON.stringify(drafts)));
    },
  };
}
