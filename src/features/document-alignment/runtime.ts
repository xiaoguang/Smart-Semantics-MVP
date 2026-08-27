import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  canonicalModelingJson,
  renderStandardModelingMarkdown,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentSection, StructuredModelingAssertion } from '../modeling-document-bridge/types.ts';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';
import { assertModelingDocumentIntegrity } from '../modeling-document-bridge/standard-markdown.ts';
import type { ContentAddressedStore, ContentReference, SourceDocumentRuntime } from '../source-documents/types.ts';
import {
  alignmentSectionByKind,
  type AlignmentClaim,
  type AlignmentDecision,
  type DeliverableFreezeProgress,
  type DocumentAlignmentSession,
  type StandardizationDeliverable,
} from './types.ts';

type MetadataStorage = Pick<Storage, 'getItem' | 'setItem'>;
type StoredState = {
  schemaVersion: 1;
  alignmentSequence: number;
  deliverableSequence: number;
  alignments: DocumentAlignmentSession[];
  deliverables: StandardizationDeliverable[];
  freezeJobs: DeliverableFreezeProgress[];
};

const storageKey = 'linguan:document-alignment:v1';
const clone = <T,>(value: T): T => structuredClone(value);

function load(storage: MetadataStorage): StoredState {
  const raw = storage.getItem(storageKey);
  if (!raw) return { schemaVersion: 1, alignmentSequence: 0, deliverableSequence: 0, alignments: [], deliverables: [], freezeJobs: [] };
  try {
    const parsed = JSON.parse(raw) as StoredState;
    return {
      schemaVersion: 1,
      alignmentSequence: parsed.alignmentSequence ?? 0,
      deliverableSequence: parsed.deliverableSequence ?? 0,
      alignments: Array.isArray(parsed.alignments) ? parsed.alignments : [],
      deliverables: Array.isArray(parsed.deliverables) ? parsed.deliverables : [],
      freezeJobs: Array.isArray(parsed.freezeJobs) ? parsed.freezeJobs : [],
    };
  } catch {
    return { schemaVersion: 1, alignmentSequence: 0, deliverableSequence: 0, alignments: [], deliverables: [], freezeJobs: [] };
  }
}

function persist(storage: MetadataStorage, state: StoredState) {
  storage.setItem(storageKey, JSON.stringify(state));
}

function digest(value: unknown) {
  return sha256HexSync(canonicalModelingJson(value));
}

function groupClaims(claims: AlignmentClaim[]) {
  const byTopic = new Map<string, AlignmentClaim[]>();
  for (const claim of claims) byTopic.set(claim.topicRef, [...(byTopic.get(claim.topicRef) ?? []), claim]);
  return byTopic;
}

function alignmentProjection(session: DocumentAlignmentSession) {
  const decisions = session.issues.flatMap((issue) => issue.decision ? [issue.decision] : []);
  const selectedByIssue = new Map(session.issues.flatMap((issue) => issue.decision
    ? [[issue.issueId, issue.decision.selectedClaimId] as const] : []));
  const selectedClaimIds = new Set<string>();
  for (const agreement of session.agreements) agreement.sourceClaimIds.forEach((claimId) => selectedClaimIds.add(claimId));
  for (const issue of session.issues) {
    const selected = selectedByIssue.get(issue.issueId);
    if (selected) selectedClaimIds.add(selected);
  }
  return {
    sourceDocumentIds: session.sourceDocumentIds,
    claims: session.claims.filter((claim) => selectedClaimIds.has(claim.claimId)),
    decisions,
  };
}

export function createDocumentAlignmentRuntime(input: {
  metadataStorage: MetadataStorage;
  contentStore: ContentAddressedStore;
  sourceDocuments: SourceDocumentRuntime;
  now?: () => string;
  freezeBatchSize?: number;
}) {
  const now = input.now ?? (() => new Date().toISOString());
  const freezeBatchSize = Math.max(1, Math.floor(input.freezeBatchSize ?? 100));

  async function collectFreezeItems(deliverable: StandardizationDeliverable) {
    if (deliverable.hashes.sourceDocumentsSha256 !== digest(deliverable.sourceDocumentRefs)) {
      throw new Error('来源文档清单校验和不一致');
    }
    if (deliverable.hashes.semanticPayloadSha256 !== deliverable.semanticPayloadRef.slice('sha256:'.length)) {
      throw new Error('语义载荷引用校验和不一致');
    }
    if (deliverable.hashes.markdownSha256 !== digest(deliverable.markdownRefs)) {
      throw new Error('Markdown清单校验和不一致');
    }
    const documents = await Promise.all(deliverable.sourceDocumentRefs.map(async (documentId) => {
      const document = await input.sourceDocuments.read(documentId);
      if (!document) throw new Error(`来源文档不存在：${documentId}`);
      if (document.projectId !== deliverable.projectId) throw new Error(`来源文档项目不一致：${documentId}`);
      if (document.status !== 'READY_FOR_ALIGNMENT') throw new Error(`来源文档尚未完成审阅：${document.sourceName}`);
      return document;
    }));
    const provenance = await input.contentStore.get(deliverable.provenanceManifestRef);
    if (provenance === null) throw new Error('溯源清单内容不存在或已损坏');
    let parsedProvenance: { sourceDocumentIds?: string[]; decisions?: AlignmentDecision[] };
    try {
      parsedProvenance = JSON.parse(provenance) as typeof parsedProvenance;
    } catch {
      throw new Error('溯源清单不是合法JSON');
    }
    if (digest(parsedProvenance.sourceDocumentIds ?? []) !== deliverable.hashes.sourceDocumentsSha256) {
      throw new Error('溯源清单中的来源文档不一致');
    }
    if (digest(parsedProvenance.decisions ?? []) !== deliverable.hashes.alignmentDecisionsSha256) {
      throw new Error('溯源清单中的对齐决定不一致');
    }
    const byRef = new Map<ContentReference, string>();
    const add = (ref: ContentReference, label: string) => { if (!byRef.has(ref)) byRef.set(ref, label); };
    for (const document of documents) {
      add(document.sectionsRef, `${document.sourceName}章节`);
      add(document.assertionsRef, `${document.sourceName}结构化断言`);
      add(document.markdownRef, `${document.sourceName} Markdown`);
    }
    add(deliverable.semanticPayloadRef, '语义载荷');
    deliverable.markdownRefs.forEach((ref, index) => add(ref, `产物Markdown ${index + 1}`));
    add(deliverable.provenanceManifestRef, '溯源清单');
    if (deliverable.modelingArtifactRef) add(deliverable.modelingArtifactRef, 'AI建模交接文档');
    return [...byRef.entries()].map(([ref, label]) => ({ ref, label }));
  }

  async function verifyContent(ref: ContentReference, label: string) {
    const content = await input.contentStore.get(ref);
    if (content === null) throw new Error(`${label}内容不存在或已损坏`);
    if (`sha256:${sha256HexSync(content)}` !== ref) throw new Error(`${label}内容校验和不一致`);
  }

  return {
    async align(params: {
      projectId: string;
      sourceDocumentIds: string[];
      claims: AlignmentClaim[];
      actorUserId: string;
    }) {
      const uniqueDocumentIds = [...new Set(params.sourceDocumentIds)];
      if (!uniqueDocumentIds.length) throw new Error('至少选择一份来源文档');
      const documents = await Promise.all(uniqueDocumentIds.map((documentId) => input.sourceDocuments.read(documentId)));
      if (documents.some((document) => !document)) throw new Error('来源文档不存在');
      if (documents.some((document) => document!.projectId !== params.projectId)) throw new Error('来源文档不属于当前项目');
      if (documents.some((document) => document!.status !== 'READY_FOR_ALIGNMENT')) throw new Error('来源文档尚未标记为可对齐');
      if (params.claims.some((claim) => !uniqueDocumentIds.includes(claim.sourceDocumentId))) {
        throw new Error('对齐主张引用了批次外来源文档');
      }
      const agreements: DocumentAlignmentSession['agreements'] = [];
      const issues: DocumentAlignmentSession['issues'] = [];
      for (const [topicRef, claims] of groupClaims(params.claims)) {
        const values = new Set(claims.map((claim) => claim.normalizedValue));
        const derivedWithMissingUpstream = claims.some((claim) => claim.authority === 'DERIVED'
          && claim.upstreamSourceDocumentIds?.some((documentId) => !uniqueDocumentIds.includes(documentId)));
        if (derivedWithMissingUpstream) {
          issues.push({
            issueId: `alignment-issue-${digest([topicRef, 'lineage']).slice(0, 12)}`,
            topicRef, objectKind: claims[0]!.objectKind, kind: 'DERIVED_LINEAGE', severity: 'BLOCKER',
            status: 'OPEN', sourceClaimIds: claims.map((claim) => claim.claimId),
          });
        } else if (values.size > 1) {
          issues.push({
            issueId: `alignment-issue-${digest([topicRef, 'conflict']).slice(0, 12)}`,
            topicRef, objectKind: claims[0]!.objectKind, kind: 'CONFLICT', severity: 'BLOCKER',
            status: 'OPEN', sourceClaimIds: claims.map((claim) => claim.claimId),
          });
        } else {
          agreements.push({
            topicRef, objectKind: claims[0]!.objectKind, normalizedValue: claims[0]!.normalizedValue,
            sourceClaimIds: claims.map((claim) => claim.claimId),
          });
        }
      }
      const state = load(input.metadataStorage);
      state.alignmentSequence += 1;
      const createdAt = now();
      const session: DocumentAlignmentSession = {
        alignmentId: `document-alignment-${state.alignmentSequence}`,
        projectId: params.projectId,
        revision: 1,
        sourceDocumentIds: uniqueDocumentIds,
        claims: clone(params.claims),
        agreements,
        issues,
        createdBy: params.actorUserId,
        createdAt,
        updatedAt: createdAt,
      };
      state.alignments.push(session);
      persist(input.metadataStorage, state);
      return clone(session);
    },

    async decide(params: {
      alignmentId: string;
      expectedRevision: number;
      issueId: string;
      selectedClaimId: string;
      reason: string;
      actorUserId: string;
    }) {
      const state = load(input.metadataStorage);
      const index = state.alignments.findIndex((item) => item.alignmentId === params.alignmentId);
      if (index < 0) throw new Error('对齐任务不存在');
      const current = state.alignments[index];
      if (current.revision !== params.expectedRevision) throw new Error('对齐任务revision已变化');
      const issueIndex = current.issues.findIndex((issue) => issue.issueId === params.issueId);
      if (issueIndex < 0) throw new Error('对齐问题不存在');
      const issue = current.issues[issueIndex];
      if (!issue.sourceClaimIds.includes(params.selectedClaimId)) throw new Error('所选结论不属于当前对齐问题');
      if (!params.reason.trim()) throw new Error('冲突决定必须填写中文理由');
      const decidedAt = now();
      const decision: AlignmentDecision = {
        decisionId: `alignment-decision-${digest([params.issueId, params.selectedClaimId, decidedAt]).slice(0, 12)}`,
        issueId: params.issueId,
        selectedClaimId: params.selectedClaimId,
        reason: params.reason.trim(),
        actorUserId: params.actorUserId,
        decidedAt,
      };
      const next: DocumentAlignmentSession = {
        ...current,
        revision: current.revision + 1,
        issues: current.issues.map((item, candidateIndex) => candidateIndex === issueIndex
          ? { ...item, status: 'DECIDED', decision } : item),
        updatedAt: decidedAt,
      };
      state.alignments[index] = next;
      persist(input.metadataStorage, state);
      return clone(next);
    },

    async createDeliverable(params: {
      alignmentId: string;
      expectedRevision: number;
      mode: StandardizationDeliverable['mode'];
      actorUserId: string;
      modelingArtifact?: ModelingDocumentArtifact;
    }) {
      const state = load(input.metadataStorage);
      const session = state.alignments.find((item) => item.alignmentId === params.alignmentId);
      if (!session) throw new Error('对齐任务不存在');
      if (session.revision !== params.expectedRevision) throw new Error('对齐任务revision已变化');
      if (session.issues.some((issue) => issue.severity === 'BLOCKER' && issue.status !== 'DECIDED')) {
        throw new Error('仍有未解决的阻断问题，不能生成产物');
      }
      const documents = await Promise.all(session.sourceDocumentIds.map(async (documentId) => {
        const document = await input.sourceDocuments.read(documentId);
        if (!document || document.status !== 'READY_FOR_ALIGNMENT') throw new Error('来源文档不可用于生成产物');
        return document;
      }));
      const projection = alignmentProjection(session);
      const semanticPayloadRef = await input.contentStore.put(canonicalModelingJson(projection));
      let modelingArtifactRef: ContentReference | undefined;
      if (params.modelingArtifact) {
        if (params.modelingArtifact.projectId !== session.projectId) throw new Error('AI建模交接文档不属于当前项目');
        assertModelingDocumentIntegrity(params.modelingArtifact);
        const frozenArtifact: ModelingDocumentArtifact = {
          ...clone(params.modelingArtifact),
          status: 'FROZEN',
          audit: [...params.modelingArtifact.audit, {
            action: 'FROZEN_BY_STANDARDIZATION_DELIVERABLE', actorUserId: params.actorUserId,
            at: now(), detail: '由标准化产物的作者确认和独立审核统一冻结',
          }],
          updatedAt: now(),
        };
        modelingArtifactRef = await input.contentStore.put(canonicalModelingJson(frozenArtifact));
      }
      state.deliverableSequence += 1;
      const deliverableId = `standardization-deliverable-${state.deliverableSequence}`;
      let markdownRefs: ContentReference[];
      if (params.mode === 'SOURCE_DOCUMENT_SET') {
        markdownRefs = documents.map((document) => document.markdownRef);
      } else {
        const sourceSections = await Promise.all(documents.map(async (document) => ({
          document,
          sections: await input.sourceDocuments.readSections(document.documentId),
        })));
        const sections = Object.fromEntries(standardSectionOrder.map(({ key }) => [key, sourceSections
          .map(({ document, sections: documentSections }) => `### ${document.sourceName}\n\n${documentSections[key]}`)
          .join('\n\n')])) as Record<ModelingDocumentSection, string>;
        for (const issue of session.issues) {
          if (!issue.decision) continue;
          const selected = session.claims.find((claim) => claim.claimId === issue.decision!.selectedClaimId)!;
          const section = alignmentSectionByKind[selected.objectKind];
          sections[section] += `\n\n### 对齐结论\n\n- 采用结论：${selected.statement}\n- 决定理由：${issue.decision.reason}`;
        }
        const assertions: StructuredModelingAssertion[] = projection.claims.map((claim) => ({
          assertionId: `deliverable:${claim.claimId}`,
          section: alignmentSectionByKind[claim.objectKind],
          statement: claim.statement,
          provenance: session.issues.some((issue) => issue.decision?.selectedClaimId === claim.claimId)
            ? 'USER_CONFIRMED' : 'OBSERVED',
          evidenceRefs: claim.evidenceRefs,
        }));
        const markdown = renderStandardModelingMarkdown({
          artifactId: deliverableId,
          projectId: session.projectId,
          documentCode: `${session.projectId}-standardization`,
          title: '标准化建模产物',
          sections,
          assertions,
        });
        markdownRefs = [await input.contentStore.put(markdown)];
      }
      const decisions = session.issues.flatMap((issue) => issue.decision ? [issue.decision] : []);
      const provenanceManifest = {
        alignmentId: session.alignmentId,
        sourceDocumentIds: session.sourceDocumentIds,
        decisions,
        ...(modelingArtifactRef ? { modelingArtifactRef } : {}),
      };
      const provenanceManifestRef = await input.contentStore.put(canonicalModelingJson(provenanceManifest));
      const createdAt = now();
      const deliverable: StandardizationDeliverable = {
        deliverableId,
        projectId: session.projectId,
        alignmentId: session.alignmentId,
        revision: 1,
        mode: params.mode,
        sourceDocumentRefs: [...session.sourceDocumentIds],
        alignmentDecisionRefs: decisions.map((decision) => decision.decisionId),
        semanticPayloadRef,
        markdownRefs,
        provenanceManifestRef,
        modelingArtifactRef,
        hashes: {
          sourceDocumentsSha256: digest(session.sourceDocumentIds),
          alignmentDecisionsSha256: digest(decisions),
          semanticPayloadSha256: semanticPayloadRef.slice('sha256:'.length),
          markdownSha256: digest(markdownRefs),
        },
        status: 'AWAITING_AUTHOR_CONFIRMATION',
        authorUserId: params.actorUserId,
        createdAt,
        updatedAt: createdAt,
      };
      state.deliverables.push(deliverable);
      persist(input.metadataStorage, state);
      return clone(deliverable);
    },

    async readMarkdown(deliverableId: string) {
      const deliverable = load(input.metadataStorage).deliverables.find((item) => item.deliverableId === deliverableId);
      if (!deliverable) throw new Error('标准化产物不存在');
      if (deliverable.mode !== 'MERGED_DOCUMENT') throw new Error('来源文档集合没有单一合并Markdown');
      const content = await input.contentStore.get(deliverable.markdownRefs[0]!);
      if (content === null) throw new Error('合并Markdown不存在或已损坏');
      return content;
    },

    async readModelingArtifact(deliverableId: string) {
      const deliverable = load(input.metadataStorage).deliverables.find((item) => item.deliverableId === deliverableId);
      if (!deliverable) throw new Error('标准化产物不存在');
      if (deliverable.status !== 'FROZEN') throw new Error('只有冻结产物可以交给AI建模');
      if (!deliverable.modelingArtifactRef) throw new Error('标准化产物未绑定AI建模文档');
      const content = await input.contentStore.get(deliverable.modelingArtifactRef);
      if (content === null) throw new Error('AI建模交接文档不存在或已损坏');
      const artifact = JSON.parse(content) as ModelingDocumentArtifact;
      if (artifact.projectId !== deliverable.projectId || artifact.status !== 'FROZEN') {
        throw new Error('AI建模交接文档身份不一致');
      }
      assertModelingDocumentIntegrity(artifact);
      return clone(artifact);
    },

    async listDeliverables(projectId: string) {
      return clone(load(input.metadataStorage).deliverables.filter((item) => item.projectId === projectId));
    },

    async readDeliverable(deliverableId: string) {
      return clone(load(input.metadataStorage).deliverables.find((item) => item.deliverableId === deliverableId) ?? null);
    },

    async listAlignments(projectId: string) {
      return clone(load(input.metadataStorage).alignments.filter((item) => item.projectId === projectId));
    },

    async confirmAuthor(params: { deliverableId: string; expectedRevision: number; actorUserId: string; reason: string }) {
      const state = load(input.metadataStorage);
      const index = state.deliverables.findIndex((item) => item.deliverableId === params.deliverableId);
      if (index < 0) throw new Error('标准化产物不存在');
      const current = state.deliverables[index];
      if (current.revision !== params.expectedRevision) throw new Error('标准化产物revision已变化');
      if (current.authorUserId !== params.actorUserId) throw new Error('只有产物作者可以确认');
      const at = now();
      const next: StandardizationDeliverable = {
        ...current,
        revision: current.revision + 1,
        status: 'AWAITING_REVIEW',
        authorConfirmation: { actorUserId: params.actorUserId, reason: params.reason.trim(), at },
        updatedAt: at,
      };
      state.deliverables[index] = next;
      persist(input.metadataStorage, state);
      return clone(next);
    },

    async approveAndFreeze(params: { deliverableId: string; expectedRevision: number; actorUserId: string }) {
      const state = load(input.metadataStorage);
      const index = state.deliverables.findIndex((item) => item.deliverableId === params.deliverableId);
      if (index < 0) throw new Error('标准化产物不存在');
      const current = state.deliverables[index];
      if (current.revision !== params.expectedRevision) throw new Error('标准化产物revision已变化');
      if (current.status !== 'AWAITING_REVIEW') throw new Error('标准化产物尚未进入审核');
      if (current.authorUserId === params.actorUserId) throw new Error('作者不能审核自己的产物');
      const priorAttempt = state.freezeJobs
        .filter((job) => job.deliverableId === current.deliverableId)
        .reduce((maximum, job) => Math.max(maximum, job.attempt), 0);
      const startedAt = now();
      let items: Awaited<ReturnType<typeof collectFreezeItems>>;
      try {
        items = await collectFreezeItems(current);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        const failed: DeliverableFreezeProgress = {
          jobId: `freeze:${current.deliverableId}:${priorAttempt + 1}`,
          deliverableId: current.deliverableId,
          attempt: priorAttempt + 1,
          status: 'FAILED',
          totalItems: 0,
          completedItems: 0,
          totalBatches: 0,
          completedBatches: 0,
          error: message,
          startedAt,
          updatedAt: now(),
        };
        state.freezeJobs.push(failed);
        persist(input.metadataStorage, state);
        throw new Error(`冻结校验失败：${message}`);
      }
      const progress: DeliverableFreezeProgress = {
        jobId: `freeze:${current.deliverableId}:${priorAttempt + 1}`,
        deliverableId: current.deliverableId,
        attempt: priorAttempt + 1,
        status: 'RUNNING',
        totalItems: items.length,
        completedItems: 0,
        totalBatches: Math.ceil(items.length / freezeBatchSize),
        completedBatches: 0,
        startedAt,
        updatedAt: startedAt,
      };
      state.freezeJobs.push(progress);
      state.deliverables[index] = { ...current, status: 'FREEZING', updatedAt: startedAt };
      persist(input.metadataStorage, state);
      try {
        for (let offset = 0; offset < items.length; offset += freezeBatchSize) {
          const batch = items.slice(offset, offset + freezeBatchSize);
          for (const item of batch) {
            progress.currentLabel = item.label;
            await verifyContent(item.ref, item.label);
            progress.completedItems += 1;
          }
          progress.completedBatches += 1;
          progress.updatedAt = now();
          persist(input.metadataStorage, state);
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        progress.status = 'FAILED';
        progress.error = message;
        progress.updatedAt = now();
        delete progress.currentLabel;
        state.deliverables[index] = { ...current, status: 'AWAITING_REVIEW', updatedAt: progress.updatedAt };
        persist(input.metadataStorage, state);
        throw new Error(`冻结校验失败：${message}`);
      }
      const at = now();
      progress.status = 'COMPLETED';
      progress.completedItems = progress.totalItems;
      progress.completedBatches = progress.totalBatches;
      progress.updatedAt = at;
      delete progress.currentLabel;
      const next: StandardizationDeliverable = {
        ...current,
        revision: current.revision + 1,
        status: 'FROZEN',
        reviewerUserId: params.actorUserId,
        reviewerApproval: { actorUserId: params.actorUserId, at },
        updatedAt: at,
      };
      state.deliverables[index] = next;
      persist(input.metadataStorage, state);
      return clone(next);
    },

    async listAlignmentIssues(params: {
      alignmentId: string;
      q?: string;
      statuses?: Array<'OPEN' | 'DECIDED'>;
      severities?: Array<'BLOCKER' | 'WARNING' | 'INFO'>;
      first?: number;
      after?: string;
    }) {
      const session = load(input.metadataStorage).alignments.find((item) => item.alignmentId === params.alignmentId);
      if (!session) throw new Error('对齐任务不存在');
      const query = {
        q: params.q?.trim().toLowerCase() ?? '',
        statuses: [...(params.statuses ?? [])].sort(),
        severities: [...(params.severities ?? [])].sort(),
      };
      const queryDigest = digest(query).slice(0, 12);
      let offset = 0;
      if (params.after) {
        const [cursorDigest, cursorOffset] = params.after.split(':');
        if (cursorDigest !== queryDigest || !/^\d+$/.test(cursorOffset ?? '')) throw new Error('问题游标已过期，请重新读取');
        offset = Number(cursorOffset);
      }
      const claimsById = new Map(session.claims.map((claim) => [claim.claimId, claim]));
      const matches = session.issues.filter((issue) => {
        if (query.statuses.length && !query.statuses.includes(issue.status)) return false;
        if (query.severities.length && !query.severities.includes(issue.severity)) return false;
        if (!query.q) return true;
        const text = [issue.topicRef, issue.kind, issue.objectKind,
          ...issue.sourceClaimIds.flatMap((claimId) => {
            const claim = claimsById.get(claimId);
            return claim ? [claim.statement, claim.normalizedValue] : [];
          })].join(' ').toLowerCase();
        return text.includes(query.q);
      });
      const first = Math.min(100, Math.max(1, Math.floor(params.first ?? 20)));
      const items = matches.slice(offset, offset + first);
      const nextOffset = offset + items.length;
      return clone({
        items,
        total: matches.length,
        nextCursor: nextOffset < matches.length ? `${queryDigest}:${nextOffset}` : null,
      });
    },

    async getAlignmentIssue(alignmentId: string, issueId: string) {
      const session = load(input.metadataStorage).alignments.find((item) => item.alignmentId === alignmentId);
      if (!session) throw new Error('对齐任务不存在');
      const issue = session.issues.find((item) => item.issueId === issueId);
      if (!issue) throw new Error('对齐问题不存在');
      return clone({ issue, claims: session.claims.filter((claim) => issue.sourceClaimIds.includes(claim.claimId)) });
    },

    async getFreezeProgress(deliverableId: string) {
      const jobs = load(input.metadataStorage).freezeJobs.filter((job) => job.deliverableId === deliverableId);
      return clone(jobs.sort((left, right) => right.attempt - left.attempt)[0] ?? null);
    },
  };
}
