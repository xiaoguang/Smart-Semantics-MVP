import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ApartmentOutlined, CheckCircleOutlined, DatabaseOutlined, FileMarkdownOutlined,
  LeftOutlined, LinkOutlined, RightOutlined, RobotOutlined, SafetyCertificateOutlined,
} from '@ant-design/icons';
import { Button, Card, Space, Tag, Tooltip, Typography, message } from 'antd';
import { useCurrentUser } from '../ai-modeling/current-user-context.tsx';
import { useLinguanWorkspace } from '../ai-modeling/workspace-context.tsx';
import { modelProject } from '../model-projects/domain-registry.ts';
import { useSourceManagement } from '../source-management/source-management-context.tsx';
import SourceCenter from '../source-management/source-center.tsx';
import BackAction from '../../components/back-action.tsx';
import { createModelingDocumentRuntime } from '../modeling-document-bridge/runtime.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';
import type {
  StandardizationDocumentHandoffReceipt,
  ZeroDeltaModelingHandoffReceipt,
} from '../standardization-deliverable/types.ts';
import type { StandardizationModelingEligibilityProjection } from '../standardization-deliverable/modeling-eligibility.ts';
import { createIndexedDbContentStore } from '../source-documents/content-store.ts';
import { createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import { createDocumentAlignmentRuntime } from '../document-alignment/runtime.ts';
import SourceDocumentWorkspace, {
  type ReviewStage, type StandardizationReviewProgress,
} from './source-document-workspace.tsx';
import GuanyijiaStandardizationWorkbench from './guanyijia-standardization-workbench.tsx';
import { selectDataStandardizationExperience } from './guanyijia-workbench-runtime.ts';
import './data-standardization.css';

const agentStages = [
  { key: 'collect', title: '读取来源', detail: '确认资料范围和可读取状态。', icon: <DatabaseOutlined /> },
  { key: 'structure', title: '解析结构', detail: '从数据库、代码和文档中整理可读结构。', icon: <ApartmentOutlined /> },
  { key: 'semantic', title: '提取业务语义', detail: '识别业务对象、活动、字段和口径建议。', icon: <RobotOutlined /> },
  { key: 'reconcile', title: '互证来源', detail: '比对来源结论，找出一致、差异和待确认事项。', icon: <LinkOutlined /> },
  { key: 'document', title: '生成文档', detail: '生成可审阅的建模文档。', icon: <FileMarkdownOutlined /> },
  { key: 'quality', title: '质量检查', detail: '检查资料、结论和审阅文档是否对应。', icon: <SafetyCertificateOutlined /> },
] as const;

function presentGenerationError(error: unknown) {
  if (error instanceof Error && /保存|存储|quota|权限/iu.test(error.message)) {
    return '浏览器暂时无法保存本次资料整理，请检查存储权限后重试。';
  }
  return '来源文档生成失败，请检查资料选择后重试。';
}

const emptyProgress: StandardizationReviewProgress = {
  documentCount: 0,
  reviewedDocumentCount: 0,
  openIssueCount: 0,
  nextStage: 'documents',
};

type DataStandardizationPageProps = {
  onHandoff(input:
    | { kind: 'MODELING_DOCUMENT'; artifact: ModelingDocumentArtifact }
    | { kind: 'ZERO_DELTA_MODELING_HANDOFF'; artifact: ModelingDocumentArtifact; receipt: ZeroDeltaModelingHandoffReceipt }
    | {
        kind: 'STANDARDIZATION_DOCUMENT_HANDOFF';
        artifact: ModelingDocumentArtifact;
        receipt: StandardizationDocumentHandoffReceipt;
        eligibility: StandardizationModelingEligibilityProjection;
      }
  ): void;
};

export function LegacyDataStandardizationPage({ onHandoff }: DataStandardizationPageProps) {
  const { currentUser } = useCurrentUser();
  const { activeSystem } = useLinguanWorkspace();
  const sourceManagement = useSourceManagement();
  const runtime = useMemo(() => createModelingDocumentRuntime({ storage: localStorage }), []);
  const contentStore = useMemo(() => createIndexedDbContentStore(), []);
  const sourceDocuments = useMemo(() => createSourceDocumentRuntime({ metadataStorage: localStorage, contentStore }), [contentStore]);
  const alignmentRuntime = useMemo(() => createDocumentAlignmentRuntime({ metadataStorage: localStorage, contentStore, sourceDocuments }), [contentStore, sourceDocuments]);
  const workspaceRef = useRef<HTMLDivElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const [sourceCenter, setSourceCenter] = useState(false);
  const [compact, setCompact] = useState(false);
  const [desktopInspectorOpen, setDesktopInspectorOpen] = useState(true);
  const [overlayInspectorOpen, setOverlayInspectorOpen] = useState(false);
  const [artifact, setArtifact] = useState<ModelingDocumentArtifact | null>(null);
  const [, setArtifacts] = useState<ModelingDocumentArtifact[]>([]);
  const [activeStage, setActiveStage] = useState(-1);
  const [busy, setBusy] = useState(false);
  const [reviewProgress, setReviewProgress] = useState<StandardizationReviewProgress>(emptyProgress);
  const [reviewRequest, setReviewRequest] = useState<{ stage: ReviewStage; token: number }>();
  const sourceSnapshot = sourceManagement.snapshot;
  const projectBatch = sourceSnapshot?.defaultBatch.projectId === activeSystem ? sourceSnapshot.defaultBatch : undefined;
  const batchSources = sourceSnapshot?.snapshots.filter((item) => projectBatch?.snapshotIds.includes(item.snapshotId)) ?? [];
  const projectName = modelProject(activeSystem)?.displayName.replace('语义模型', '资料') ?? '当前项目资料';
  const reviewOpen = compact ? overlayInspectorOpen : desktopInspectorOpen;

  const refreshArtifacts = async (preferred?: ModelingDocumentArtifact) => {
    const items = await runtime.list(activeSystem);
    const ordered = items.sort((left, right) => left.revision - right.revision || left.createdAt.localeCompare(right.createdAt));
    setArtifacts(ordered);
    setArtifact(preferred ?? ordered.at(-1) ?? (activeSystem === 'guanyijia_erp' ? guanyijiaFrozenModelingArtifact : null));
  };

  useEffect(() => {
    const element = workspaceRef.current;
    if (!element) return;
    const update = (width: number) => setCompact(width <= 1040);
    update(element.getBoundingClientRect().width);
    const observer = new ResizeObserver(([entry]) => update(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!compact || !overlayInspectorOpen) return;
    const scrollY = window.scrollY;
    document.body.classList.add('standardization-overlay-active');
    return () => {
      document.body.classList.remove('standardization-overlay-active');
      window.scrollTo({ top: scrollY });
    };
  }, [compact, overlayInspectorOpen]);

  useEffect(() => {
    let cancelled = false;
    runtime.list(activeSystem).then((items) => {
      if (cancelled) return;
      const ordered = items.sort((left, right) => left.revision - right.revision || left.createdAt.localeCompare(right.createdAt));
      setArtifacts(ordered);
      setArtifact(ordered.at(-1) ?? (activeSystem === 'guanyijia_erp' ? guanyijiaFrozenModelingArtifact : null));
    });
    setActiveStage(-1);
    setReviewProgress(emptyProgress);
    setReviewRequest(undefined);
    setOverlayInspectorOpen(false);
    setDesktopInspectorOpen(true);
    return () => { cancelled = true; };
  }, [activeSystem, runtime]);

  const openReview = (stage: ReviewStage, trigger?: HTMLElement | null) => {
    returnFocusRef.current = trigger ?? null;
    setReviewRequest((current) => ({ stage, token: (current?.token ?? 0) + 1 }));
    if (compact) setOverlayInspectorOpen(true);
    else setDesktopInspectorOpen(true);
  };

  const reopenReview = (trigger?: HTMLElement | null) => {
    returnFocusRef.current = trigger ?? null;
    if (compact) setOverlayInspectorOpen(true);
    else setDesktopInspectorOpen(true);
  };

  const closeOverlay = () => {
    setOverlayInspectorOpen(false);
    window.requestAnimationFrame(() => returnFocusRef.current?.focus());
  };

  const generateDocument = async () => {
    if (!projectBatch || !projectBatch.snapshotIds.length) {
      message.info('请先完成来源设置，再选择至少一份可用资料。');
      return;
    }
    setBusy(true);
    try {
      for (let index = 0; index < agentStages.length; index += 1) {
        setActiveStage(index);
        await new Promise((resolve) => window.setTimeout(resolve, 90));
      }
      const isErp = activeSystem === 'guanyijia_erp';
      const result = await runtime.execute({
        type: 'GENERATE_DOCUMENT', projectId: activeSystem,
        documentCode: isErp ? 'guanyijia_standard_r1' : 'retail_standard_r2',
        title: isErp ? '管伊佳 ERP 标准建模资料' : '零售经营标准建模资料',
        sourceBatch: { batchId: projectBatch.batchId, fingerprint: projectBatch.fingerprint, snapshotIds: projectBatch.snapshotIds },
        actorUserId: currentUser.userId,
      });
      await refreshArtifacts(result.artifact);
      openReview('documents');
      message.success('来源文档已生成，请逐份完成审阅');
    } catch (error) {
      message.error(presentGenerationError(error));
    } finally {
      setBusy(false);
    }
  };

  const nextReviewLabel = (() => {
    if (artifact?.status === 'FROZEN') return '查看已定版文档';
    if (reviewProgress.documentCount > reviewProgress.reviewedDocumentCount) {
      return `审阅来源文档（${reviewProgress.reviewedDocumentCount}/${reviewProgress.documentCount}）`;
    }
    if (reviewProgress.openIssueCount > 0) return `处理 ${reviewProgress.openIssueCount} 项来源差异`;
    if (!reviewProgress.deliverableStatus) return '生成输出文档';
    if (reviewProgress.deliverableStatus === 'AWAITING_AUTHOR_CONFIRMATION') return '提交审核';
    if (reviewProgress.deliverableStatus === 'AWAITING_REVIEW') return reviewProgress.isDeliverableAuthor ? '查看审核状态' : '审核并定版';
    if (reviewProgress.deliverableStatus === 'FROZEN') return '交给 AI 建模';
    return '继续审阅';
  })();

  const mainAction = (trigger: HTMLElement) => {
    if (!projectBatch?.snapshotIds.length) {
      setSourceCenter(true);
      return;
    }
    if (!artifact) {
      void generateDocument();
      return;
    }
    if (!reviewOpen) {
      reopenReview(trigger);
      return;
    }
    openReview(reviewProgress.nextStage, trigger);
  };

  const primaryLabel = !projectBatch?.snapshotIds.length
    ? '设置来源'
    : !artifact
      ? '生成来源文档'
      : !reviewOpen
        ? '继续审阅'
        : nextReviewLabel;

  const processingComplete = Boolean(artifact) && !busy;
  const currentAgentStage = activeStage >= 0 ? agentStages[activeStage] : undefined;
  const remainingReviewCount = reviewProgress.openIssueCount
    || Math.max(0, reviewProgress.documentCount - reviewProgress.reviewedDocumentCount);

  return <div ref={workspaceRef} className="data-standardization-shell">
    <Card className="data-standardization-card" styles={{ body: { padding: 0 } }}>
      <header className="standardization-workspace-bar">
        <div className="standardization-batch-summary">
          <strong>{projectName}</strong>
          <span>{projectBatch ? `${batchSources.length}份资料 · ${reviewProgress.reviewedDocumentCount}/${reviewProgress.documentCount || batchSources.length}份文档已审阅` : '先选择资料，再开始整理'}</span>
          {projectBatch && <span>本次资料已选定，开始后不可替换</span>}
        </div>
        <Space wrap className="standardization-actions">
          {projectBatch?.snapshotIds.length ? <Button type="link" onClick={() => setSourceCenter(true)}>来源设置</Button> : null}
          {(compact || desktopInspectorOpen) && <Button type="primary" loading={busy} aria-label={primaryLabel} onClick={(event) => mainAction(event.currentTarget)}>{primaryLabel}</Button>}
        </Space>
      </header>

      <div className={`data-standardization-layout${desktopInspectorOpen ? '' : ' review-collapsed'}`}>
        <main className="standardization-thread">
          <header className="standardization-pane-header">
            <div><Typography.Title level={4}>标准化进度</Typography.Title><Typography.Text type="secondary">处理过程、审阅决定和下一步集中在这里。</Typography.Text></div>
          </header>
          <div className="standardization-progress-log" aria-live="polite">
            {!projectBatch && <section className="standardization-current-step"><Tag>第1步</Tag><h2>设置资料来源</h2><p>添加并读取需要参与本次标准化的资料。</p></section>}
            {projectBatch && !artifact && !busy && <section className="standardization-current-step"><Tag color="blue">准备就绪</Tag><h2>可以开始整理资料</h2><p>已选 {batchSources.length} 份资料。下一步生成各来源的审阅文档。</p></section>}
            {busy && currentAgentStage && <section className="standardization-current-step processing"><Tag color="blue">处理中</Tag><span className="standardization-current-icon">{currentAgentStage.icon}</span><h2>{currentAgentStage.title}</h2><p>{currentAgentStage.detail}</p><span>{activeStage + 1} / {agentStages.length}</span></section>}
            {artifact && !busy && <>
              <section className="standardization-current-step complete"><Tag color="green">{artifact.status === 'FROZEN' ? '已定版' : '进行中'}</Tag><h2>{nextReviewLabel}</h2><p>{artifact.status === 'FROZEN' ? '标准化文档已定版，可继续查看文档和审阅记录。' : '审阅文档已生成，可依次核对来源资料、差异和最终文档。'}</p></section>
              <button type="button" className="standardization-progress-link" onClick={(event) => openReview('documents', event.currentTarget)}><span>来源文档</span><strong>{reviewProgress.reviewedDocumentCount}/{reviewProgress.documentCount || batchSources.length}</strong></button>
              <button type="button" className="standardization-progress-link" onClick={(event) => openReview('alignment', event.currentTarget)}><span>来源差异</span><strong>{reviewProgress.openIssueCount}项待处理</strong></button>
            </>}
            {(busy || processingComplete) && <details className="standardization-processing-record"><summary>{processingComplete ? '资料处理完成 · 6/6' : `资料处理中 · ${activeStage + 1}/6`}</summary><ol>{agentStages.map((stage, index) => <li className={processingComplete || activeStage > index ? 'done' : activeStage === index ? 'active' : ''} key={stage.key}><span>{processingComplete || activeStage > index ? <CheckCircleOutlined /> : stage.icon}</span><div><strong>{stage.title}</strong><small>{stage.detail}</small></div></li>)}</ol></details>}
          </div>
        </main>

        <aside className={`standardization-inspector${compact && overlayInspectorOpen ? ' overlay-open' : ''}`} aria-label="审阅工作区">
          <header className="standardization-pane-header inspector-header">
            <div><Typography.Title level={4}>审阅工作区</Typography.Title><Typography.Text type="secondary">逐份审阅资料，处理差异并确认输出文档。</Typography.Text></div>
            {compact
              ? <BackAction destination="处理进度" onBack={closeOverlay} />
              : <Tooltip title="收起审阅区"><Button type="text" icon={<RightOutlined />} aria-label="收起审阅区" onClick={() => setDesktopInspectorOpen(false)} /></Tooltip>}
          </header>
          <SourceDocumentWorkspace
            artifact={artifact}
            currentUserId={currentUser.userId}
            documentRuntime={runtime}
            sourceDocuments={sourceDocuments}
            alignmentRuntime={alignmentRuntime}
            requestedStage={reviewRequest}
            onProgressChange={setReviewProgress}
            onArtifactChange={refreshArtifacts}
            onHandoff={async (handoffArtifact) => {
              onHandoff({ kind: 'MODELING_DOCUMENT', artifact: handoffArtifact });
              message.success('已交给AI建模，请确认开始建模');
            }}
          />
        </aside>
      </div>
      {!compact && !desktopInspectorOpen && <Tooltip title="展开审阅区"><Button className="standardization-review-restore" type="primary" icon={<LeftOutlined />} aria-label="继续审阅" onClick={(event) => reopenReview(event.currentTarget)}>继续审阅{remainingReviewCount ? ` · ${remainingReviewCount}` : ''}</Button></Tooltip>}
    </Card>
    {sourceCenter && <SourceCenter initialView="CONNECTIONS" attachedSnapshotIds={projectBatch?.snapshotIds ?? []} onClose={() => setSourceCenter(false)} />}
  </div>;
}

export default function DataStandardizationPage(props: DataStandardizationPageProps) {
  const { activeSystem } = useLinguanWorkspace();
  return selectDataStandardizationExperience(activeSystem) === 'GUANYIJIA_TIMELINE'
    ? <GuanyijiaStandardizationWorkbench onHandoff={(artifact, receipt, eligibility) => {
        if (receipt.kind === 'ZERO_DELTA_MODELING_HANDOFF') {
          props.onHandoff({ kind: 'ZERO_DELTA_MODELING_HANDOFF', artifact, receipt });
          return;
        }
        if (!eligibility) throw new Error('标准化文档缺少可建模结论清单');
        props.onHandoff({ kind: 'STANDARDIZATION_DOCUMENT_HANDOFF', artifact, receipt, eligibility });
      }} />
    : <LegacyDataStandardizationPage {...props} />;
}
