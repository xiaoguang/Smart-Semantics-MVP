import { useEffect, useState } from 'react';
import {
  Alert, Button, Empty, Input, Segmented, Select, Spin, Tag, Typography, message,
} from 'antd';
import type {
  BatchOverview, ModelingDocumentArtifact, ModelingDocumentRuntime, ModelingDocumentSection, ReviewEvidence,
} from '../modeling-document-bridge/types.ts';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { SourceDocumentRuntime, SourceModelingDocument } from '../source-documents/types.ts';
import { projectArtifactAlignmentClaims, projectArtifactSourceDocuments } from '../source-documents/source-document-projection.ts';
import type { createDocumentAlignmentRuntime } from '../document-alignment/runtime.ts';
import type {
  AlignmentClaim, AlignmentObjectKind, DocumentAlignmentSession, StandardizationDeliverable,
} from '../document-alignment/types.ts';
import { prepareModelingArtifactForDeliverable } from '../document-alignment/modeling-handoff.ts';
import BackAction from '../../components/back-action.tsx';
import {
  buildAlignmentIssueQuery, defaultAlignmentIssueFilters, type AlignmentIssueFilters,
} from './alignment-issue-filters.ts';

type AlignmentRuntime = ReturnType<typeof createDocumentAlignmentRuntime>;
export type ReviewStage = 'documents' | 'alignment' | 'deliverable';
export type StandardizationReviewProgress = {
  documentCount: number;
  reviewedDocumentCount: number;
  openIssueCount: number;
  deliverableStatus?: StandardizationDeliverable['status'];
  isDeliverableAuthor?: boolean;
  nextStage: ReviewStage;
};
const pageSize = 20;

const statusLabel: Record<SourceModelingDocument['status'], string> = {
  GENERATED: '已生成', NEEDS_REVIEW: '待审阅', READY_FOR_ALIGNMENT: '已完成审阅', SUPERSEDED: '历史版本',
};

const authorityLabel: Record<AlignmentClaim['authority'], string> = {
  PRIMARY: '主证据', CORROBORATING: '交叉佐证', DERIVED: '派生依据', AUXILIARY: '辅助资料',
};

const deliverableStatusLabel: Record<StandardizationDeliverable['status'], string> = {
  DRAFT: '草稿',
  AWAITING_AUTHOR_CONFIRMATION: '等待作者确认',
  AWAITING_REVIEW: '等待独立审核',
  FREEZING: '正在定版',
  FROZEN: '已定版',
  REJECTED: '已退回',
};

const locatorLabels: Record<string, string> = {
  path: '来源位置', relativePath: '来源位置', file: '来源文件', table: '数据表',
  field: '字段', section: '文档章节', line: '行号', lineStart: '起始行', lineEnd: '结束行',
};

function readableLocatorEntries(locator: Record<string, unknown>) {
  return Object.entries(locator).filter(([key]) => !/(?:sha|ref|id|fingerprint|digest|kind|revision)/iu.test(key));
}

const objectKindLabel: Record<AlignmentObjectKind, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度',
  METRIC: '指标', RULE: '规则', ALIAS: '同义词', TIME_RULE: '时间语义',
};

const knownTopicLabel: Record<string, string> = {
  'metric.net-sales.refund-timing': '净销售额的退款扣减时点',
  'metric.search-conversion.traffic-filter': '搜索转化的流量排除范围',
  'time.business-day.assignment': '经营数据的营业日归属',
  'metric.receivable-debt.deployed-schema': '应收欠款字段与部署结构不一致',
};

function topicLabel(topicRef: string, kind: AlignmentObjectKind) {
  return knownTopicLabel[topicRef] ?? `${objectKindLabel[kind]}定义需要确认`;
}

/** Never expose a storage, integrity, or implementation error directly in the review UI. */
function presentWorkspaceError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : '';
  if (/正文|章节|证据.*读取/u.test(detail)) return '所需资料暂时无法读取，请重新打开当前资料后重试。';
  if (/保存|存储|quota|权限/iu.test(detail)) return '浏览器暂时无法保存本次操作，请检查存储权限后重试。';
  if (/revision|并发|另一.*标签/iu.test(detail)) return '内容已在其他位置更新，请刷新当前步骤后重试。';
  return fallback;
}

function latestDocuments(items: SourceModelingDocument[]) {
  const latest = new Map<string, SourceModelingDocument>();
  items.forEach((item) => {
    const current = latest.get(item.documentCode);
    if (!current || item.revision > current.revision) latest.set(item.documentCode, item);
  });
  return [...latest.values()].sort((left, right) => left.sourceName.localeCompare(right.sourceName, 'zh-CN'));
}

export default function SourceDocumentWorkspace({
  artifact, currentUserId, documentRuntime, sourceDocuments, alignmentRuntime,
  requestedStage, onProgressChange, onArtifactChange, onHandoff,
}: {
  artifact: ModelingDocumentArtifact | null;
  currentUserId: string;
  documentRuntime: ModelingDocumentRuntime;
  sourceDocuments: SourceDocumentRuntime;
  alignmentRuntime: AlignmentRuntime;
  requestedStage?: { stage: ReviewStage; token: number };
  onProgressChange?(progress: StandardizationReviewProgress): void;
  onArtifactChange(artifact: ModelingDocumentArtifact): Promise<void> | void;
  onHandoff(artifact: ModelingDocumentArtifact, deliverable: StandardizationDeliverable): Promise<void> | void;
}) {
  const [view, setView] = useState<ReviewStage>('documents');
  const [documents, setDocuments] = useState<SourceModelingDocument[]>([]);
  const [selectedDocumentId, setSelectedDocumentId] = useState<string>();
  const [documentDetailOpen, setDocumentDetailOpen] = useState(false);
  const [documentPage, setDocumentPage] = useState(1);
  const [selectedSection, setSelectedSection] = useState<ModelingDocumentSection>('OVERVIEW');
  const [sectionContent, setSectionContent] = useState('');
  const [summary, setSummary] = useState<Awaited<ReturnType<SourceDocumentRuntime['getReviewSummary']>> | null>(null);
  const [reviewOverview, setReviewOverview] = useState<BatchOverview | null>(null);
  const [alignment, setAlignment] = useState<DocumentAlignmentSession | null>(null);
  const [issues, setIssues] = useState<Array<DocumentAlignmentSession['issues'][number]>>([]);
  const [issueTotal, setIssueTotal] = useState(0);
  const [issueCursor, setIssueCursor] = useState<string | null>(null);
  const [issueFilters, setIssueFilters] = useState<AlignmentIssueFilters>(defaultAlignmentIssueFilters);
  const [issueSearchDraft, setIssueSearchDraft] = useState('');
  const [selectedIssueId, setSelectedIssueId] = useState<string>();
  const [issueClaims, setIssueClaims] = useState<AlignmentClaim[]>([]);
  const [selectedClaimId, setSelectedClaimId] = useState<string>();
  const [evidence, setEvidence] = useState<ReviewEvidence | null>(null);
  const [evidenceQueue, setEvidenceQueue] = useState<string[]>([]);
  const [evidenceIndex, setEvidenceIndex] = useState(0);
  const [deliverableMode, setDeliverableMode] = useState<StandardizationDeliverable['mode']>('SOURCE_DOCUMENT_SET');
  const [deliverable, setDeliverable] = useState<StandardizationDeliverable | null>(null);
  const [loading, setLoading] = useState(false);

  const selectedDocument = documents.find((item) => item.documentId === selectedDocumentId) ?? null;
  const isFrozenBaseline = artifact?.status === 'FROZEN';
  const readyCount = isFrozenBaseline
    ? documents.length
    : documents.filter((item) => item.status === 'READY_FOR_ALIGNMENT').length;
  const visibleDocumentStatus = (document: SourceModelingDocument) => isFrozenBaseline
    ? '已定版基线'
    : statusLabel[document.status];

  useEffect(() => {
    if (!requestedStage) return;
    setView(requestedStage.stage);
    setEvidence(null);
  }, [requestedStage?.token]);

  useEffect(() => {
    const openIssueCount = alignment?.issues.filter((item) => item.status === 'OPEN').length
      ?? reviewOverview?.openIssueCount
      ?? 0;
    const nextStage: ReviewStage = readyCount < documents.length
      ? 'documents'
      : openIssueCount > 0
        ? 'alignment'
        : 'deliverable';
    onProgressChange?.({
      documentCount: documents.length,
      reviewedDocumentCount: readyCount,
      openIssueCount,
      deliverableStatus: deliverable?.status,
      isDeliverableAuthor: deliverable?.authorUserId === currentUserId,
      nextStage,
    });
  }, [alignment?.revision, deliverable?.revision, documents.length, readyCount, reviewOverview?.openIssueCount, currentUserId]);

  const readIssuePage = async (
    current: DocumentAlignmentSession,
    filters: AlignmentIssueFilters,
    after?: string | null,
  ) => alignmentRuntime.listAlignmentIssues(buildAlignmentIssueQuery(current.alignmentId, filters, after));

  const replaceIssuePage = async (current: DocumentAlignmentSession, filters: AlignmentIssueFilters) => {
    const page = await readIssuePage(current, filters);
    setIssueFilters(filters); setIssueSearchDraft(filters.q);
    setIssues(page.items); setIssueTotal(page.total); setIssueCursor(page.nextCursor);
  };

  const refreshDocuments = async () => {
    if (!artifact) { setDocuments([]); return []; }
    const items = latestDocuments(await sourceDocuments.list(artifact.projectId));
    setDocuments(items); setDocumentPage((page) => Math.min(page, Math.max(1, Math.ceil(items.length / pageSize))));
    setSelectedDocumentId((current) => current && items.some((item) => item.documentId === current)
      ? current : items[0]?.documentId);
    return items;
  };

  const refreshAlignment = async () => {
    if (!artifact) return;
    const alignments = await alignmentRuntime.listAlignments(artifact.projectId);
    const current = alignments.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))[0] ?? null;
    setAlignment(current);
    if (current) {
      await replaceIssuePage(current, defaultAlignmentIssueFilters);
    } else { setIssues([]); setIssueTotal(0); setIssueCursor(null); }
  };

  const refreshDeliverable = async () => {
    if (!artifact) return;
    const items = await alignmentRuntime.listDeliverables(artifact.projectId);
    setDeliverable(items.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))[0] ?? null);
  };

  useEffect(() => {
    let cancelled = false;
    if (!artifact) { setDocuments([]); setReviewOverview(null); setAlignment(null); setDeliverable(null); return () => { cancelled = true; }; }
    setDocumentDetailOpen(false); setDocumentPage(1);
    setIssueFilters(defaultAlignmentIssueFilters); setIssueSearchDraft('');
    setLoading(true);
    (async () => {
      const existing = await sourceDocuments.list(artifact.projectId);
      const drafts = projectArtifactSourceDocuments(artifact, currentUserId);
      for (const draft of drafts) {
        if (!existing.some((item) => item.documentCode === draft.documentCode && item.sourceSnapshotId === draft.sourceSnapshotId)) {
          await sourceDocuments.register(draft);
        }
      }
      if (cancelled) return;
      const review = await documentRuntime.review(artifact.artifactId);
      const overview = await review.getOverview();
      if (cancelled) return;
      setReviewOverview(overview);
      await Promise.all([refreshDocuments(), refreshAlignment(), refreshDeliverable()]);
    })().catch((error) => { if (!cancelled) message.error(presentWorkspaceError(error, '来源文档暂时无法打开，请稍后重试。')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [artifact?.artifactId, artifact?.updatedAt, currentUserId]);

  useEffect(() => {
    let cancelled = false;
    if (!selectedDocument) { setSummary(null); setSectionContent(''); return () => { cancelled = true; }; }
    Promise.all([
      sourceDocuments.getReviewSummary(selectedDocument.documentId),
      sourceDocuments.readSections(selectedDocument.documentId),
    ]).then(([nextSummary, sections]) => {
      if (cancelled) return;
      setSummary(nextSummary); setSectionContent(sections[selectedSection]);
    }).catch((error) => { if (!cancelled) message.error(presentWorkspaceError(error, '当前章节暂时无法打开，请稍后重试。')); });
    return () => { cancelled = true; };
  }, [selectedDocument?.documentId, selectedSection]);

  const markReady = async () => {
    if (!selectedDocument) return;
    setLoading(true);
    try {
      await sourceDocuments.markReady({ documentId: selectedDocument.documentId, expectedRevision: selectedDocument.revision, actorUserId: currentUserId });
      const nextDocuments = await refreshDocuments();
      const allReviewed = nextDocuments.length > 0 && nextDocuments.every((item) => item.status === 'READY_FOR_ALIGNMENT');
      if (allReviewed) {
        message.success('全部来源文档已完成审阅，正在比较来源差异');
        await beginAlignment(nextDocuments);
      } else {
        message.success('本份来源文档已完成审阅');
      }
    } catch (error) { message.error(presentWorkspaceError(error, '无法完成本份来源审阅，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const beginAlignment = async (sourceItems = documents) => {
    if (!artifact || !sourceItems.length || sourceItems.some((item) => item.status !== 'READY_FOR_ALIGNMENT')) return;
    setLoading(true);
    try {
      const claims = projectArtifactAlignmentClaims(artifact, sourceItems);
      const next = await alignmentRuntime.align({
        projectId: artifact.projectId, sourceDocumentIds: sourceItems.map((item) => item.documentId), claims, actorUserId: currentUserId,
      });
      setAlignment(next);
      await replaceIssuePage(next, defaultAlignmentIssueFilters); setView('alignment');
      message.success(`来源比较完成：${next.agreements.length}项一致，${next.issues.length}项需要处理`);
    } catch (error) { message.error(presentWorkspaceError(error, '来源比较暂时无法完成，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const openIssue = async (issueId: string) => {
    if (!alignment) return;
    setLoading(true);
    try {
      const detail = await alignmentRuntime.getAlignmentIssue(alignment.alignmentId, issueId);
      setSelectedIssueId(issueId); setIssueClaims(detail.claims);
      setSelectedClaimId(detail.issue.decision?.selectedClaimId);
      setEvidence(null); setView('alignment');
    } catch (error) { message.error(presentWorkspaceError(error, '这项来源差异暂时无法打开，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const decide = async () => {
    if (!alignment || !selectedIssueId || !selectedClaimId) return;
    setLoading(true);
    try {
      const next = await alignmentRuntime.decide({
        alignmentId: alignment.alignmentId, expectedRevision: alignment.revision, issueId: selectedIssueId,
        selectedClaimId, reason: '已记录用户选择的来源结论', actorUserId: currentUserId,
      });
      setAlignment(next); setSelectedIssueId(undefined); setIssueClaims([]);
      await replaceIssuePage(next, issueFilters);
      message.success('已保存本次决定，生成结果时会反映这项结论。');
    } catch (error) { message.error(presentWorkspaceError(error, '当前决定暂时无法保存，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const loadMoreIssues = async () => {
    if (!alignment || !issueCursor) return;
    const page = await readIssuePage(alignment, issueFilters, issueCursor);
    setIssues((current) => [...current, ...page.items]); setIssueCursor(page.nextCursor);
  };

  const changeIssueFilters = async (patch: Partial<AlignmentIssueFilters>) => {
    if (!alignment) return;
    setLoading(true);
    try {
      const next = { ...issueFilters, ...patch };
      await replaceIssuePage(alignment, next);
      setSelectedIssueId(undefined); setIssueClaims([]); setEvidence(null);
    } catch (error) { message.error(presentWorkspaceError(error, '来源差异暂时无法查询，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const openEvidence = async (evidenceId: string, queue: string[] = [evidenceId]) => {
    if (!artifact) return;
    setLoading(true);
    try {
      const service = await documentRuntime.review(artifact.artifactId);
      setEvidence(await service.getEvidence(evidenceId));
      setEvidenceQueue(queue);
      setEvidenceIndex(Math.max(0, queue.indexOf(evidenceId)));
      setView('alignment');
    } catch (error) { message.error(presentWorkspaceError(error, '来源材料暂时无法显示，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const createDeliverable = async () => {
    if (!artifact || !alignment) return;
    setLoading(true);
    try {
      if (alignment.issues.some((item) => item.severity === 'BLOCKER' && item.status === 'OPEN')) throw new Error('请先解决全部阻断问题');
      const handoffArtifact = await prepareModelingArtifactForDeliverable({
        runtime: documentRuntime, artifact, alignment, actorUserId: currentUserId,
      });
      if (handoffArtifact.artifactId !== artifact.artifactId) {
        await onArtifactChange(handoffArtifact);
      }
      const next = await alignmentRuntime.createDeliverable({
        alignmentId: alignment.alignmentId, expectedRevision: alignment.revision,
        mode: deliverableMode, actorUserId: currentUserId, modelingArtifact: handoffArtifact,
      });
      setDeliverable(next); setView('deliverable'); message.success('输出文档已生成，请作者确认');
    } catch (error) { message.error(presentWorkspaceError(error, '标准化文档暂时无法生成，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const confirmAuthor = async () => {
    if (!deliverable) return;
    setLoading(true);
    try {
      const next = await alignmentRuntime.confirmAuthor({ deliverableId: deliverable.deliverableId, expectedRevision: deliverable.revision, actorUserId: currentUserId, reason: '来源文档、来源差异决定和输出文档已确认' });
      setDeliverable(next); setView('deliverable'); message.success('已提交独立审核');
    } catch (error) { message.error(presentWorkspaceError(error, '作者确认暂时无法保存，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const approve = async () => {
    if (!deliverable) return;
    setLoading(true);
    try {
      const next = await alignmentRuntime.approveAndFreeze({ deliverableId: deliverable.deliverableId, expectedRevision: deliverable.revision, actorUserId: currentUserId });
      setDeliverable(next); message.success('审核通过，输出文档已定版');
    } catch (error) { message.error(presentWorkspaceError(error, '定版暂时无法完成，请稍后重试。')); }
    finally { setLoading(false); }
  };

  const handoff = async () => {
    if (!deliverable) return;
    try { await onHandoff(await alignmentRuntime.readModelingArtifact(deliverable.deliverableId), deliverable); }
    catch (error) { message.error(presentWorkspaceError(error, '暂时无法交给 AI 建模，请稍后重试。')); }
  };

  const documentPanel = selectedDocument ? <div className="source-document-detail">
    <BackAction className="source-document-mobile-back" destination="来源文档" onBack={() => setDocumentDetailOpen(false)} />
    <header><div><strong>{selectedDocument.sourceName}</strong><small>第 {selectedDocument.revision} 版 · {visibleDocumentStatus(selectedDocument)}</small></div>
      {!isFrozenBaseline && selectedDocument.status === 'NEEDS_REVIEW' && <Button type="primary" size="small" onClick={() => void markReady()}>完成本份审阅</Button>}</header>
    <div className="source-section-nav">{standardSectionOrder.map(({ key, heading }) => <button className={selectedSection === key ? 'active' : ''} key={key} onClick={() => setSelectedSection(key)}><span>{heading}</span><small>{summary?.sections.find((item) => item.section === key)?.assertionCount ?? 0}项识别结果</small></button>)}</div>
    <section className="source-section-readable"><header><strong>{standardSectionOrder.find((item) => item.key === selectedSection)?.heading}</strong></header>
      {!selectedDocument.blocksRef && !isFrozenBaseline && <Alert
        type="info"
        showIcon
        message="旧版来源文档仅支持只读审阅"
        description="该文档没有结构化识别块，不能直接修改章节。请在具备结构化块的新工作台中按识别项修订。"
      />}
      <Typography.Paragraph className="readable-document-section">{sectionContent}</Typography.Paragraph>
    </section>
    {selectedDocument.derivedFromDocumentId && <Button type="link" onClick={async () => {
      const diff = await sourceDocuments.getSectionDiff(selectedDocument.derivedFromDocumentId!, selectedDocument.documentId, selectedSection);
      message.info(diff.changed ? `本节已变化：修改前 ${diff.before.length} 字，修改后 ${diff.after.length} 字` : '本节与上一修订一致');
    }}>查看修改对比</Button>}
  </div> : <Empty description="选择一份来源文档" />;

  const evidencePanel = evidence ? <div className="evidence-readable-detail"><BackAction destination="来源差异" onBack={() => setEvidence(null)} /><h3>依据详情</h3><p>{evidence.statement}</p>{evidenceQueue.length > 1 && <div className="evidence-detail-pagination"><Button disabled={evidenceIndex === 0} onClick={() => void openEvidence(evidenceQueue[evidenceIndex - 1], evidenceQueue)}>上一条</Button><span>{evidenceIndex + 1} / {evidenceQueue.length}</span><Button disabled={evidenceIndex >= evidenceQueue.length - 1} onClick={() => void openEvidence(evidenceQueue[evidenceIndex + 1], evidenceQueue)}>下一条</Button></div>}{readableLocatorEntries(evidence.locator).length > 0 && <dl className="evidence-readable-location">{readableLocatorEntries(evidence.locator).map(([key, value]) => <div key={key}><dt>{locatorLabels[key] ?? '来源位置'}</dt><dd>{typeof value === 'string' || typeof value === 'number' ? String(value) : '已保留结构化位置'}</dd></div>)}</dl>}</div> : null;

  const alignmentPanel = evidencePanel ?? (alignment ? <div className="alignment-review-panel">
    <header><div><strong>来源差异</strong><small>{alignment.agreements.length}项一致内容已收起 · {issueTotal}项需要处理</small></div></header>
    {selectedIssueId ? <section className="alignment-issue-detail"><BackAction destination="来源差异" onBack={() => { setSelectedIssueId(undefined); setIssueClaims([]); }} />
      <div className="alignment-claim-grid" role="radiogroup" aria-label="来源结论">{issueClaims.map((claim) => <article className={selectedClaimId === claim.claimId ? 'selected' : ''} key={claim.claimId}><button type="button" className="alignment-claim-select" role="radio" aria-checked={selectedClaimId === claim.claimId} onClick={() => setSelectedClaimId(claim.claimId)}><Tag>{authorityLabel[claim.authority]}</Tag><strong>{documents.find((item) => item.documentId === claim.sourceDocumentId)?.sourceName}</strong><p>{claim.statement}</p></button>{claim.evidenceRefs.length > 0 && <Button className="claim-evidence-action" type="link" size="small" onClick={() => void openEvidence(claim.evidenceRefs[0], claim.evidenceRefs)}>查看依据（{claim.evidenceRefs.length}）</Button>}</article>)}</div>
      <Button type="primary" disabled={!selectedClaimId} onClick={() => void decide()}>保存决定</Button>
    </section> : <>{issueTotal > pageSize && <div className="alignment-issue-filters">
      <Input.Search value={issueSearchDraft} allowClear placeholder="搜索名称、结论或对象类型" onChange={(event) => setIssueSearchDraft(event.target.value)} onSearch={(q) => void changeIssueFilters({ q })} />
      <Select value={issueFilters.status} aria-label="问题状态" onChange={(status) => void changeIssueFilters({ status })} options={[{ value: 'OPEN', label: '待处理' }, { value: 'DECIDED', label: '已解决' }, { value: 'ALL', label: '全部状态' }]} />
      <Select value={issueFilters.severity} aria-label="问题级别" onChange={(severity) => void changeIssueFilters({ severity })} options={[{ value: 'ALL', label: '全部级别' }, { value: 'BLOCKER', label: '主要问题' }, { value: 'WARNING', label: '待确认' }, { value: 'INFO', label: '提示' }]} />
    </div>}<div className="alignment-result-count">显示 {issues.length} / {issueTotal} 项</div>
    <div className="alignment-issue-list">{issues.map((issue) => <button key={issue.issueId} onClick={() => void openIssue(issue.issueId)}><Tag color={issue.severity === 'BLOCKER' ? 'red' : undefined}>{issue.severity === 'BLOCKER' ? '主要问题' : '待确认'}</Tag><span>{topicLabel(issue.topicRef, issue.objectKind)}</span><small>{issue.status === 'DECIDED' ? '已解决' : `${issue.sourceClaimIds.length}个来源结论`}</small></button>)}{!issues.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的问题" />}{issueCursor && <Button onClick={() => void loadMoreIssues()}>加载下一页</Button>}</div></>}
  </div> : <Empty description={isFrozenBaseline
    ? '证据基线已定版，无需重新比较来源'
    : readyCount === documents.length && documents.length
      ? <Button type="primary" onClick={() => void beginAlignment()}>比较来源</Button>
      : `请先完成来源文档审阅（${readyCount}/${documents.length}）`} />);

  const deliverablePanel = <div className="deliverable-panel"><header><div><strong>输出文档</strong><small>两种方式都保留来源文档、来源差异和处理记录</small></div></header>
    {!deliverable && artifact?.status === 'FROZEN' ? <Alert type="success" showIcon title="证据基线已定版" description={`标准建模文档 r${artifact.revision} 已通过完整性校验；后续新证据从新的来源文档和个人草稿开始。`} />
      : !deliverable ? <><Segmented value={deliverableMode} onChange={(value) => setDeliverableMode(value as StandardizationDeliverable['mode'])} options={[{ value: 'SOURCE_DOCUMENT_SET', label: '来源文档集合' }, { value: 'MERGED_DOCUMENT', label: '合并文档' }]} /><Alert type="info" showIcon title={deliverableMode === 'SOURCE_DOCUMENT_SET' ? '保留多份来源文档，不再拼接大文档' : '生成一份九段式合并文档，同时保留来源文档'} /><Button type="primary" disabled={!alignment || alignment.issues.some((item) => item.severity === 'BLOCKER' && item.status === 'OPEN')} onClick={() => void createDeliverable()}>生成输出文档</Button></>
      : <><Tag color={deliverable.status === 'FROZEN' ? 'green' : 'blue'}>{deliverableStatusLabel[deliverable.status]}</Tag><p>{deliverable.mode === 'SOURCE_DOCUMENT_SET' ? `${deliverable.sourceDocumentRefs.length}份来源文档集合` : '一份合并文档'} · 第 {deliverable.revision} 版</p>{deliverable.status === 'AWAITING_AUTHOR_CONFIRMATION' && <Button type="primary" onClick={() => void confirmAuthor()}>提交审核</Button>}{deliverable.status === 'AWAITING_REVIEW' && (deliverable.authorUserId === currentUserId ? <Alert type="info" title="等待另一位有权限的审核者" /> : <Button type="primary" onClick={() => void approve()}>审核通过并定版</Button>)}{deliverable.status === 'FROZEN' && <Button type="primary" onClick={() => void handoff()}>交给 AI 建模</Button>}</>}
  </div>;

  const openIssueCount = alignment?.issues.filter((item) => item.status === 'OPEN').length
    ?? reviewOverview?.openIssueCount
    ?? 0;
  const stageContent = view === 'documents'
    ? <div className={`source-document-master-detail ${documentDetailOpen ? 'show-detail' : ''}`}><nav>{documents.slice((documentPage - 1) * pageSize, documentPage * pageSize).map((item) => <button className={selectedDocumentId === item.documentId ? 'active' : ''} key={item.documentId} onClick={() => { setSelectedDocumentId(item.documentId); setDocumentDetailOpen(true); }}><strong>{item.sourceName}</strong><small>r{item.revision} · {visibleDocumentStatus(item)}</small></button>)}{documents.length > pageSize && <div className="source-document-pagination"><Button disabled={documentPage === 1} onClick={() => setDocumentPage((page) => page - 1)}>上一页</Button><span>{documentPage} / {Math.ceil(documents.length / pageSize)}</span><Button disabled={documentPage >= Math.ceil(documents.length / pageSize)} onClick={() => setDocumentPage((page) => page + 1)}>下一页</Button></div>}</nav>{documentPanel}</div>
    : view === 'alignment'
      ? alignmentPanel
      : deliverablePanel;

  return <Spin spinning={loading} wrapperClassName="source-document-workspace-spinner">
    <section className="source-document-workspace">
      <nav className="standardization-stage-nav" aria-label="审阅阶段">
        <button type="button" className={view === 'documents' ? 'active' : ''} aria-current={view === 'documents' ? 'step' : undefined} onClick={() => { setView('documents'); setEvidence(null); }}><span>来源文档</span><small>{readyCount}/{documents.length}</small></button>
        <button type="button" className={view === 'alignment' ? 'active' : ''} aria-current={view === 'alignment' ? 'step' : undefined} onClick={() => { setView('alignment'); setEvidence(null); }}><span>来源差异</span><small>{openIssueCount}</small></button>
        <button type="button" className={view === 'deliverable' ? 'active' : ''} aria-current={view === 'deliverable' ? 'step' : undefined} onClick={() => { setView('deliverable'); setEvidence(null); }}><span>输出文档</span><small>{deliverable ? deliverableStatusLabel[deliverable.status] : '未生成'}</small></button>
      </nav>
      <details className="standardization-review-technical"><summary>审阅数据详情</summary><span>{reviewOverview?.rootSourceCount ?? 0}个独立来源 · {reviewOverview?.evidenceCount ?? 0}条证据</span></details>
      <div className="source-document-stage-content">{stageContent}</div>
    </section>
  </Spin>;
}
