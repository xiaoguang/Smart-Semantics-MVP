import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Collapse, Space, Tag, Typography, message } from 'antd';
import AssistantPanel, { type ReviewActionRequest } from './assistant-panel.tsx';
import ModelResultInspector, { type InspectorNavigation } from './model-result-inspector.tsx';
import ModelingDocumentInspector from './modeling-document-inspector.tsx';
import { useLinguanWorkspace } from './workspace-context.tsx';
import type { ModelingObjectRef } from './modeling-object-projection.ts';
import type { ModelSummaryTarget } from './workflow-records.tsx';
import { useCollaboration } from '../collaboration/collaboration-context.tsx';
import { CollaborationCatalogInspector, CollaborationCatalogRecord } from '../collaboration/catalog-view.tsx';
import { DraftReviewRecord, requestAsDraft } from '../collaboration/draft-review-record.tsx';
import { projectDraftBrowser } from '../collaboration/draft-comparison.ts';
import { ModelBrowser } from '../model-browser/model-browser.tsx';
import type { ModelBrowserComparison, ModelBrowserObjectRef } from '../model-browser/types.ts';
import type { CollaborationTask } from '../collaboration/types.ts';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';
import type {
  StandardizationDocumentHandoffReceipt,
  ZeroDeltaModelingHandoffReceipt,
} from '../standardization-deliverable/types.ts';
import type { StandardizationModelingEligibilityProjection } from '../standardization-deliverable/modeling-eligibility.ts';
import { projectZeroDeltaM4View } from './zero-delta-handoff.ts';
import { presentModelingDocumentSourceSummary, presentModelingDocumentVersion } from './modeling-document-presentation.ts';
import { compileModelingDocument, type CompiledModelingDocument } from '../modeling-document-bridge/compile-modeling-document.ts';
import { materializeModelingDocument } from '../modeling-document-bridge/document-materializer.ts';
import { createModelingDocumentRuntime } from '../modeling-document-bridge/runtime.ts';
import { useCurrentUser } from './current-user-context.tsx';
import { semanticFixture } from './fixture.ts';
import { presentBusinessError } from '../data-standardization/business-copy-quality.ts';
import './ai-modeling.css';

export default function AiModelingPage({
  onPublished, showOntologyPrompt, onOpenOntology, collaborationTask, onCollaborationPublished,
  modelingDocument, modelingDocumentCandidate: documentCandidate, zeroDeltaReceipt, standardizationReceipt, modelingEligibility, onModelingDocumentChange,
  onModelingDocumentCandidateChange, onReturnStandardization,
}: {
  onPublished?(hint: { systemCode: string; catalogVersion: string }): void;
  showOntologyPrompt?: boolean;
  onOpenOntology?(): void;
  collaborationTask?: CollaborationTask;
  onCollaborationPublished?(catalogVersion: string): void;
  modelingDocument?: ModelingDocumentArtifact;
  modelingDocumentCandidate?: CompiledModelingDocument;
  zeroDeltaReceipt?: ZeroDeltaModelingHandoffReceipt;
  standardizationReceipt?: StandardizationDocumentHandoffReceipt;
  modelingEligibility?: StandardizationModelingEligibilityProjection;
  onModelingDocumentChange?(artifact: ModelingDocumentArtifact): void;
  onModelingDocumentCandidateChange?(candidate: CompiledModelingDocument | undefined): void;
  onReturnStandardization?(): void;
}) {
  const {
    activeView, workspaceReadOnly, activeSystem, activeVersion,
    materializeCurrent, selectVersion,
  } = useLinguanWorkspace();
  const { currentUser } = useCurrentUser();
  const collaboration = useCollaboration();
  const documentRuntime = useRef(createModelingDocumentRuntime({ storage: localStorage })).current;
  const formalCatalog = collaboration.viewMode === 'FORMAL'
    ? collaboration.catalogFor(activeSystem, activeVersion !== 'WORKSPACE' ? activeVersion : undefined) : null;
  const formalRequest = formalCatalog ? collaboration.getRequest(formalCatalog.requestId) : null;
  const taskRequest = collaborationTask?.requestId ? collaboration.getRequest(collaborationTask.requestId) : null;
  const activeDraft = collaboration.viewMode === 'DRAFT' ? collaboration.draftFor(activeSystem) : null;
  const reviewDraft = collaborationTask?.kind === 'EDIT' ? activeDraft : taskRequest ? requestAsDraft(taskRequest) : activeDraft;
  const reviewBaseCatalog = reviewDraft && reviewDraft.baseCatalogVersion !== 'INITIAL'
    ? collaboration.catalogFor(activeSystem, reviewDraft.baseCatalogVersion) : null;
  const draftBrowser = reviewDraft && reviewBaseCatalog ? projectDraftBrowser(reviewDraft, reviewBaseCatalog) : null;
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [selectedRef, setSelectedRef] = useState<ModelingObjectRef>();
  const [inspectorNavigation, setInspectorNavigation] = useState<InspectorNavigation>();
  const [reviewRequest, setReviewRequest] = useState<ReviewActionRequest>();
  const [draftFocus, setDraftFocus] = useState<ModelBrowserObjectRef>();
  const [draftComparison, setDraftComparison] = useState<ModelBrowserComparison>();
  const [documentApplied, setDocumentApplied] = useState(false);
  const lastGeneratedVersion = useRef<string | undefined>(undefined);
  const result = activeView?.result ?? undefined;
  const knownDocument = modelingDocument
    ? semanticFixture.documents.find((item) => item.sha256 === modelingDocument.markdown.sha256) : undefined;
  const documentBlocked = Boolean(knownDocument && !knownDocument.qualityGate.publishable);
  const eligibleConclusionCount = modelingEligibility?.eligibleCandidateIds.length ?? 0;
  const excludedConclusionCount = modelingEligibility?.excludedCandidateIds.length ?? 0;
  const zeroDeltaView = zeroDeltaReceipt && formalCatalog
    ? projectZeroDeltaM4View({
        receipt: zeroDeltaReceipt,
        catalog: formalCatalog,
        draftCount: collaboration.draftFor(activeSystem) ? 1 : 0,
      })
    : undefined;

  useEffect(() => {
    setDocumentApplied(false);
    if (modelingDocument) setInspectorOpen(true);
  }, [modelingDocument?.artifactId, modelingDocument?.markdown.sha256]);
  useEffect(() => {
    if (!result || lastGeneratedVersion.current === result.documentVersion) return;
    lastGeneratedVersion.current = result.documentVersion; setSelectedRef(undefined); setInspectorOpen(true);
  }, [result]);
  useEffect(() => { if (formalCatalog || draftBrowser) setInspectorOpen(true); }, [formalCatalog?.catalogId, reviewDraft?.draftId]);

  const inspect = useCallback((ref: ModelingObjectRef) => { setSelectedRef(ref); setInspectorOpen(true); }, []);
  const openModelSummary = useCallback((target: ModelSummaryTarget) => {
    setSelectedRef(undefined); setInspectorNavigation({ ...target, requestId: Date.now() }); setInspectorOpen(true);
  }, []);

  const ensureDraft = () => {
    const existing = collaboration.draftFor(activeSystem);
    if (existing) { collaboration.setViewMode('DRAFT'); selectVersion('WORKSPACE'); return existing; }
    if (!collaboration.accessFor(activeSystem).canCreateDraft) throw new Error('当前角色不能创建个人草稿');
    const created = collaboration.openDraft(activeSystem, materializeCurrent());
    selectVersion('WORKSPACE'); return created;
  };
  const startModeling = () => {
    if (!modelingDocument) return;
    if (zeroDeltaReceipt) { message.info('本次为零变化交接，无需创建个人草稿或V2'); return; }
    if (modelingDocument.projectId !== activeSystem) { message.error('标准文档不属于当前模型项目'); return; }
    if (modelingDocument.status !== 'FROZEN') { message.error('标准文档尚未冻结'); return; }
    try {
      const candidate = compileModelingDocument(modelingDocument, { eligibility: modelingEligibility });
      if (candidate.items.length === 0) {
        message.info('本次没有可用于建模的结论；完整文档与缺口仍可查看。');
        return;
      }
      ensureDraft(); onModelingDocumentCandidateChange?.(candidate); setInspectorOpen(true);
      message.success('文档分析完成，请检查候选对象和待确认事项');
    } catch (error) { message.error(presentBusinessError(error, '无法开始建模，请重新打开标准化文档后重试。')); }
  };
  const applyCandidate = () => {
    if (!modelingDocument || !documentCandidate) return;
    try {
      const draft = ensureDraft();
      const baseCatalog = draft.baseCatalogVersion === 'INITIAL' ? null : collaboration.catalogFor(activeSystem, draft.baseCatalogVersion);
      const proposal = materializeModelingDocument(modelingDocument, documentCandidate, { draftId: draft.draftId, base: baseCatalog?.data ?? draft.materializedData });
      collaboration.saveDraft(draft.draftId, draft.revision, proposal.materializedData, proposal.changes);
      setDocumentApplied(true); message.success(`${proposal.changes.length} 项候选变化已加入个人草稿`);
    } catch (error) { message.error(presentBusinessError(error, '候选模型无法加入个人草稿，请重新打开后重试。')); }
  };

  const draftRecord = draftBrowser && reviewDraft && reviewBaseCatalog ? <DraftReviewRecord
    draft={reviewDraft} baseCatalog={reviewBaseCatalog} request={taskRequest}
    canReview={Boolean(taskRequest && collaboration.accessFor(activeSystem).canReview)}
    canPublish={Boolean(taskRequest && collaboration.accessFor(activeSystem).canPublish)}
    onSelect={(ref, comparison) => { setDraftFocus(ref); setDraftComparison(comparison); setInspectorOpen(true); }}
    onApprove={() => { if (!taskRequest) return; try { collaboration.reviewRequest(taskRequest.requestId, taskRequest.expectedRevision, 'APPROVE', ''); message.success('审核已通过，无补充意见'); } catch (error) { message.error(presentBusinessError(error, '审核暂时无法保存，请重新打开后重试。')); } }}
    onRequestChanges={(reason) => { if (!taskRequest) return; try { collaboration.reviewRequest(taskRequest.requestId, taskRequest.expectedRevision, 'REQUEST_CHANGES', reason); message.success('修改意见已发送给作者'); } catch (error) { message.error(presentBusinessError(error, '操作暂时无法保存，请重新打开后重试。')); } }}
    onReject={(reason) => { if (!taskRequest) return; try { collaboration.reviewRequest(taskRequest.requestId, taskRequest.expectedRevision, 'REJECT', reason); message.success('变更已拒绝'); } catch (error) { message.error(presentBusinessError(error, '操作暂时无法保存，请重新打开后重试。')); } }}
    onPublish={(reason) => { if (!taskRequest) return; try { const catalog = collaboration.publishRequest(taskRequest.requestId, taskRequest.expectedRevision, reason); message.success(`已发布 ${catalog.catalogVersion}`); onPublished?.({ systemCode: activeSystem, catalogVersion: catalog.catalogVersion }); onCollaborationPublished?.(catalog.catalogVersion); } catch (error) { message.error(presentBusinessError(error, '发布暂时无法完成，请重新打开后重试。')); } }}
  /> : formalCatalog ? <CollaborationCatalogRecord catalog={formalCatalog} request={formalRequest} /> : undefined;

  const documentRecord = modelingDocument && zeroDeltaReceipt ? <section className="assistant-message zero-delta-modeling-handoff">
    <div className="modeling-document-handoff-title"><Tag color="green">零变化交接</Tag><strong>{modelingDocument.title}</strong></div>
    <div className="zero-delta-summary">
      <strong>语义变化 {zeroDeltaView?.semanticChangeCount ?? 0}</strong>
      <p>{zeroDeltaView?.catalogMessage ?? '正式模型本次未改变'}</p>
      <p>{zeroDeltaView?.draftMessage ?? '本次未创建新的个人草稿'}</p>
      <p>{zeroDeltaView?.governanceMessage ?? '已保存资料审阅与差异决定记录'}</p>
    </div>
    <p>完整性校验已在后台完成；本次交接没有创建新的正式模型版本。</p>
    <Space wrap>
      <Button onClick={onReturnStandardization}>查看数据标准化证据</Button>
      <Button onClick={() => { collaboration.setViewMode('FORMAL'); selectVersion('V1'); setInspectorOpen(true); }}>查看正式模型</Button>
    </Space>
  </section> : modelingDocument && standardizationReceipt ? <section className="assistant-message modeling-document-handoff">
    <div className="modeling-document-handoff-title"><Tag color="green">标准化文档交接</Tag><strong>{modelingDocument.title}</strong></div>
    <Typography.Paragraph>完整文档已带入 AI 建模。{eligibleConclusionCount} 项结论可用于建模，{excludedConclusionCount} 项资料缺口或无法确定内容仅保留在文档中。</Typography.Paragraph>
    <Space wrap>
      <Button onClick={onReturnStandardization}>查看定版文档</Button>
      {!documentCandidate && eligibleConclusionCount > 0 && <Button type="primary" onClick={startModeling}>生成建模候选</Button>}
      {eligibleConclusionCount === 0 && <Tag color="gold">没有可建模候选</Tag>}
      {documentCandidate && !documentApplied && !documentBlocked && <Button type="primary" onClick={applyCandidate}>加入个人草稿</Button>}
      {documentApplied && <Tag color="green">已加入个人草稿</Tag>}
    </Space>
    {excludedConclusionCount > 0 && <Collapse ghost items={[{ key: 'exclusions', label: `已排除的资料缺口 · ${excludedConclusionCount}项`, children: <ul>{modelingEligibility?.conclusions.filter((item) => item.eligibility !== 'ELIGIBLE').map((item) => <li key={item.conclusionId}><strong>{item.title}</strong>：{item.exclusionReason ?? '当前无法确定，未生成建模候选。'}</li>)}</ul> }]} />}
  </section> : modelingDocument ? <section className="assistant-message modeling-document-handoff">
    <div className="modeling-document-handoff-title"><Tag color="blue">标准建模文档</Tag><strong>{modelingDocument.title}</strong><span>{presentModelingDocumentVersion(modelingDocument.revision)}</span></div>
    <Typography.Paragraph>{presentModelingDocumentSourceSummary(modelingDocument.sourceBatch?.snapshotIds ?? [])}</Typography.Paragraph>
    <Space wrap>
      <Button onClick={onReturnStandardization}>查看数据标准化证据</Button>
      {!documentCandidate && <Button type="primary" onClick={startModeling}>开始建模</Button>}
      {documentCandidate && !documentApplied && !documentBlocked && <Button type="primary" onClick={applyCandidate}>加入个人草稿</Button>}
      {documentBlocked && <Tag color="red">资料存在阻断项，请上传修正版</Tag>}
      {documentApplied && <Tag color="green">已加入个人草稿</Tag>}
    </Space>
    <Collapse ghost items={[{ key: 'validation', label: `文档验证 · ${modelingDocument.validation.warnings.length}项警告 · ${modelingDocument.validation.gaps.length}项缺口`, children: <>{[...modelingDocument.validation.warnings, ...modelingDocument.validation.gaps].map((issue) => <Alert key={issue.code} type="warning" showIcon message={issue.message} />)}</> }]} />
  </section> : null;

  return <Card className="ai-modeling-card" styles={{ body: { padding: 0 } }}>
    <div className={`ai-modeling-layout ${inspectorOpen ? 'inspector-open' : 'inspector-closed'}`}>
      <div className="ai-modeling-body">
        <main className="ai-modeling-conversation">
          <AssistantPanel
            documentOnly
            documentMarkdownSha256={modelingDocument?.markdown.sha256}
            onMarkdownDocument={async ({ fileName, content }) => {
              const artifact = (await documentRuntime.execute({
                type: 'REGISTER_MANUAL_DOCUMENT', projectId: activeSystem,
                documentCode: fileName.replace(/\.md$/i, '').replace(/[^a-zA-Z0-9_\u3400-\u9fff-]+/g, '_'),
                fileName, content, actorUserId: currentUser.userId,
              })).artifact;
              onModelingDocumentChange?.(artifact); setDocumentApplied(false); setInspectorOpen(true);
              message.success('Markdown结构已校验，请确认开始建模');
            }}
            leadingContent={<>{draftRecord}{documentRecord}</>}
            onInspect={inspect} onOpenModelSummary={openModelSummary}
            reviewRequest={reviewRequest} onReviewRequestHandled={() => setReviewRequest(undefined)}
            onPublished={onPublished} showOntologyPrompt={showOntologyPrompt} onOpenOntology={onOpenOntology}
          />
        </main>
        {zeroDeltaReceipt && formalCatalog
          ? <CollaborationCatalogInspector catalog={formalCatalog} open={inspectorOpen} onOpen={() => setInspectorOpen(true)} onClose={() => setInspectorOpen(false)} />
          : modelingDocument && !documentApplied
          ? <ModelingDocumentInspector artifact={modelingDocument} candidate={documentCandidate} open={inspectorOpen} onOpen={() => setInspectorOpen(true)} onClose={() => setInspectorOpen(false)} />
          : draftBrowser ? <ModelBrowser view={draftBrowser} open={inspectorOpen} focusRef={draftFocus} comparison={draftComparison} onOpen={() => setInspectorOpen(true)} onClose={() => setInspectorOpen(false)} />
          : modelingDocument ? <ModelingDocumentInspector artifact={modelingDocument} candidate={documentCandidate} open={inspectorOpen} onOpen={() => setInspectorOpen(true)} onClose={() => setInspectorOpen(false)} />
          : formalCatalog ? <CollaborationCatalogInspector catalog={formalCatalog} open={inspectorOpen} onOpen={() => setInspectorOpen(true)} onClose={() => setInspectorOpen(false)} />
          : <ModelResultInspector result={result} open={inspectorOpen} selectedRef={selectedRef} navigation={inspectorNavigation}
            onOpen={() => setInspectorOpen(true)} onClose={() => { setInspectorOpen(false); setSelectedRef(undefined); }}
            onSelect={setSelectedRef} onClearSelection={() => setSelectedRef(undefined)}
            onReviewAction={workspaceReadOnly ? undefined : (itemId, objectName, decision) => setReviewRequest({ requestId: Date.now(), itemId, objectName, decision })} />}
      </div>
    </div>
  </Card>;
}
