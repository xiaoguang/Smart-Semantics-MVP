import { useEffect, useMemo, useRef, useState } from 'react';
import type React from 'react';
import { Alert, Button, Collapse, Dropdown, Input, Typography, message } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { DeleteOutlined, NodeIndexOutlined, PaperClipOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons';
import { semanticFixture } from './fixture.ts';
import type { HydratedResult } from './runtime-types.ts';
import { useLinguanWorkspace } from './workspace-context';
import unknownDocument from './fixtures/documents/99-门店促销活动补充设计-未知.md?raw';
import { buildAnalysisTimeline } from './analysis-timeline.ts';
import { projectReviewBatch } from './review-batch.ts';
import {
  interpretReviewInput, projectReviewPrompt,
  type ReviewChoice, type ReviewIntent,
} from './review-conversation.ts';
import {
  AnalysisProgress, ConversationQuestion, PublicationChecksDetails, ReviewGroupSummary,
} from './assistant-review-ui.tsx';
import { sha256Hex } from './sha256.ts';
import { useCurrentUser } from './current-user-context.tsx';
import { useCollaboration } from '../collaboration/collaboration-context.tsx';
import type { ModelingObjectRef } from './modeling-object-projection.ts';
import type { WorkflowAnalysisState } from './workflow-projection.ts';
import { ModelGeneratedSummary, WorkflowHistory, type ModelSummaryTarget } from './workflow-records.tsx';
import { validateObjectionReason } from './review-interaction.ts';
import { projectPublicationChecks } from './publication-checks.ts';
import { WorkflowStatusMark } from './workflow-status-mark.ts';
import { classifyClipboardAttachment, selectSingleMarkdown } from './attachment-adapter.ts';
import { resolveUploadRouting } from './upload-routing.ts';
import { validateModelingCode } from './workspace-registry.ts';
import type { ReviewItemGuidance } from './review-interaction.ts';
import { projectAssistantToolMenu, selectDemoDocument } from './demo-document.ts';
import { omnichannelSourceAssets, omnichannelSourcePack } from './omnichannel-fixture.ts';
import { sourceFamilyLabel, validateSourceAttachments } from './source-bundle.ts';
import SourceWorkflowRecords from './source-workflow-records.tsx';
import { resolveModelingScenario } from './scenario-registry.ts';
import { connectorTypeCatalog } from '../source-management/connector-catalog.ts';
import { useSourceManagement } from '../source-management/source-management-context.tsx';
import { projectSnapshotAsset } from '../source-management/projection.ts';
import { presentBusinessError } from '../data-standardization/business-copy-quality.ts';

type ChatMessage = { id: number; role: 'USER' | 'ASSISTANT'; kind: 'TEXT' | 'RECEIPT'; content: string };
type InlineChoice = { id: string; label: string; description: string; danger?: boolean; action(): void | Promise<void> };
type PreparedFile = { file: File; content: string; sha256: string };
type AssistantToolMenuItem = {
  key: string;
  label: string;
  disabled?: boolean;
  danger?: boolean;
  children?: AssistantToolMenuItem[];
};
export type FindingActionRequest = { requestId: number; findingId: string; resolutionId: string };
type RoutingRequest =
  | ({ kind: 'SWITCH'; systemCode: string; documentCode: string } & PreparedFile)
  | ({ kind: 'CHOOSE' | 'JOIN' | 'CREATE' } & PreparedFile);

export type ReviewActionRequest = {
  requestId: number;
  itemId: string;
  objectName: string;
  decision: 'APPROVED' | 'REJECTED';
};

const wait = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

function downloadText(content: string, fileName: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/markdown;charset=utf-8' }));
  const anchor = window.document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function downloadBytes(content: Uint8Array, fileName: string, type = 'application/octet-stream') {
  const url = URL.createObjectURL(new Blob([content as BlobPart], { type }));
  const anchor = window.document.createElement('a');
  anchor.href = url; anchor.download = fileName; anchor.click(); URL.revokeObjectURL(url);
}

function answerQuestion(question: string, result?: HydratedResult, multiSource = false, evidenceOnly = false, publishedData?: Record<string, unknown>) {
  if (!result && publishedData) {
    const workspace = publishedData.workspaceData as { entities?: unknown[]; events?: unknown[]; semanticRelations?: unknown[]; aliases?: unknown[] } | undefined;
    const metrics = (publishedData.metricData as { metrics?: unknown[] } | undefined)?.metrics ?? [];
    if (/实体|事件|表/.test(question)) return `当前正式模型包含 ${workspace?.entities?.length ?? 0} 个实体和 ${workspace?.events?.length ?? 0} 个事件；字段都挂在所属对象下。`;
    if (/指标|公式/.test(question)) return `当前正式模型包含 ${metrics.length} 个指标，可以在“指标配置”查看口径和公式。`;
    if (/关系/.test(question)) return `当前正式模型包含 ${workspace?.semanticRelations?.length ?? 0} 条关系。`;
    if (/同义词|别名/.test(question)) return `当前正式模型包含 ${workspace?.aliases?.length ?? 0} 条同义词映射。`;
    return '我可以解释当前正式版本的实体、事件、关系、指标、同义词和版本记录；修改请进入“我的草稿”。';
  }
  if (!result) return evidenceOnly
    ? '当前已接入数据库证据快照。你可以在右侧查看物理结构、程序逻辑、实际使用和数据形态；目前只有单一来源，尚未生成语义模型。'
    : multiSource ? '请通过“＋”添加文件、数据源快照或代码仓库。我会先整理来源，再进行交叉验证。' : '请添加一份 Markdown 设计资料。我会先确认它属于哪个模型空间，再开始建模。';
  const model = result.fixture.artifacts.model;
  if (/实体|事件|表/.test(question)) return `当前资料生成 ${model.entity_tables.length} 张实体表、${model.event_tables.length} 张事件表；字段都挂在所属表下。`;
  if (/指标|公式/.test(question)) return `当前生成 ${model.metrics.length} 个指标。复杂计算会保留原始公式，并用业务语言解释。`;
  if (/关系/.test(question)) return `当前生成 ${model.relationships.length} 条关系，每条都保留关联的两张表和字段。`;
  if (/下一步|怎么做|审核/.test(question)) return result.status === 'NEEDS_SUPPLEMENT' ? '当前资料存在阻断项，请添加修正版 Markdown。' : result.allItemsDecided ? '审核已完成，可以发布模型。' : `系统已采纳 ${result.autoApprovedItemCount} 项；其余内容按业务组逐项确认。`;
  return '我可以解释当前版本的表、字段归属、关系、维度、指标、阻断原因和下一步操作。';
}

export default function AssistantPanel({
  onInspect, onOpenModelSummary, onAnalysisChange, reviewRequest, onReviewRequestHandled,
  onPublished, showOntologyPrompt, onOpenOntology,
  findingRequest, onFindingRequestHandled, onConfigureSource, onImportSourceYaml, onManageSources, leadingContent,
  documentOnly = false, onMarkdownDocument,
  documentMarkdownSha256,
}: {
  onInspect?(ref: ModelingObjectRef): void;
  onOpenModelSummary?(target: ModelSummaryTarget): void;
  onAnalysisChange?(state: WorkflowAnalysisState | null): void;
  reviewRequest?: ReviewActionRequest;
  onReviewRequestHandled?(): void;
  onPublished?(hint: { systemCode: string; catalogVersion: string }): void;
  showOntologyPrompt?: boolean;
  onOpenOntology?(): void;
  findingRequest?: FindingActionRequest;
  onFindingRequestHandled?(): void;
  onConfigureSource?(connectorTypeId: string): void;
  onImportSourceYaml?(): void;
  onManageSources?(): void;
  leadingContent?: React.ReactNode;
  documentOnly?: boolean;
  onMarkdownDocument?(input: { fileName: string; content: string; sha256: string }): void | Promise<void>;
  documentMarkdownSha256?: string;
}) {
  const {
    activeSystem, activeVersion, activeView, workspaces, snapshot, runtime, acceptSnapshot, resetWorkspace, selectSystem, selectVersion,
    createWorkspace, getRuntime, readSnapshot,
  } = useLinguanWorkspace();
  const { currentUser } = useCurrentUser();
  const collaboration = useCollaboration();
  const { snapshot: sourceManagementSnapshot } = useSourceManagement();
  const formalCatalog = collaboration.catalogFor(
    activeSystem,
    activeVersion !== 'WORKSPACE' ? activeVersion : undefined,
  );
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [attachmentSelected, setAttachmentSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [analysisState, setAnalysisState] = useState<WorkflowAnalysisState | null>(null);
  const [pendingReset, setPendingReset] = useState(false);
  const [publishChecksVisible, setPublishChecksVisible] = useState(false);
  const [feedbackTarget, setFeedbackTarget] = useState<ReviewItemGuidance | null>(null);
  const [routingRequest, setRoutingRequest] = useState<RoutingRequest | null>(null);
  const [sourceChoiceMode, setSourceChoiceMode] = useState<'DATABASE' | 'REPOSITORY' | 'CONNECTOR' | null>(null);
  const inputRef = useRef<TextAreaRef>(null);
  const resetPromptRef = useRef<HTMLDivElement>(null);
  const current = documentOnly ? undefined : activeView?.result ?? undefined;
  const assistantAccess = activeView?.assistantAccess ?? {
    canAsk: true, canUpload: true, canReview: true, canPublish: true, recordReadOnly: false,
  };
  const canAsk = assistantAccess.canAsk;
  const canUpload = collaboration.viewMode === 'DRAFT' && assistantAccess.canUpload;
  const canReview = collaboration.viewMode === 'DRAFT' && assistantAccess.canReview;
  const canPublish = false;
  const recordReadOnly = collaboration.viewMode === 'FORMAL' || assistantAccess.recordReadOnly;
  const currentFixture = documentOnly ? undefined : snapshot?.fixture;
  const multiSource = !documentOnly && Boolean(snapshot?.sourceWorkspace);
  const evidenceOnly = !documentOnly && snapshot ? resolveModelingScenario(snapshot.systemCode).capabilities.lifecycle === 'EVIDENCE_ONLY' : false;
  const reviewBatch = useMemo(() => current && current.status !== 'NEEDS_SUPPLEMENT' && currentFixture
    ? projectReviewBatch(currentFixture, current.documentVersion)
    : null, [current, currentFixture]);
  const nextGroup = reviewBatch?.groups.find((group) => group.items.some((item) => !current?.decisions[item.itemId]));
  const pendingGroupItems = nextGroup?.items.filter((item) => !current?.decisions[item.itemId]) ?? [];
  const activePrompt = useMemo(() => {
    if (!current || current.status !== 'GENERATED' || !reviewBatch) return null;
    if (canReview && !current.allItemsDecided && nextGroup) {
      return projectReviewPrompt({ kind: 'GROUP', batch: reviewBatch, group: { ...nextGroup, items: pendingGroupItems } });
    }
    if (canPublish && current.allItemsDecided) {
      const rejectedCount = Object.values(current.decisions).filter((decision) => decision.decision === 'REJECTED').length;
      return projectReviewPrompt({ kind: 'PUBLISH', documentVersion: current.documentVersion, modelVersion: current.fixture.modelVersion ?? '未发布', rejectedCount });
    }
    return null;
  }, [canPublish, canReview, current, nextGroup, pendingGroupItems, reviewBatch]);
  const analysisDocument = analysisState
    ? currentFixture?.documents.find((item) => item.documentVersion === analysisState.documentVersion)
    : current?.fixture;
  const managedBatchSources = useMemo(() => (snapshot?.sourceWorkspace?.batch.snapshotIds ?? []).flatMap((snapshotId) => {
    const asset = sourceManagementSnapshot ? projectSnapshotAsset(sourceManagementSnapshot, snapshotId) : null;
    return asset ? [{ asset, snapshotId }] : [];
  }), [snapshot?.sourceWorkspace?.batch.snapshotIds, sourceManagementSnapshot]);
  const sourceCount = multiSource
    ? new Set([...(snapshot?.sourceWorkspace?.selectedSources.map((item) => item.sourceId) ?? []), ...managedBatchSources.map((item) => item.asset.sourceId)]).size
    : undefined;
  const timeline = analysisDocument ? buildAnalysisTimeline(analysisDocument, analysisState
    ? { activeIndex: analysisState.activeIndex, completed: analysisState.completed, sourceCount }
    : { completed: true, sourceCount }) : null;
  const publishedChecksView = current?.status === 'PUBLISHED'
    ? projectPublicationChecks(current.fixture, current.checks)
    : null;
  const demoTools = new URLSearchParams(window.location.search).get('demoTools') === '1';

  useEffect(() => { onAnalysisChange?.(analysisState); }, [analysisState, onAnalysisChange]);
  useEffect(() => {
    setMessages([]); setQuestion(''); setPendingFiles([]); setAttachmentSelected(null);
    setFeedbackTarget(null); setRoutingRequest(null); setSourceChoiceMode(null); setPendingReset(false);
  }, [currentUser.userId]);
  useEffect(() => {
    setPublishChecksVisible(false);
    setFeedbackTarget(null);
    setRoutingRequest(null);
    if (recordReadOnly) setAnalysisState(null);
    if (!canUpload) {
      setPendingFiles([]);
      setAttachmentSelected(null);
    }
  }, [activeSystem, activeVersion, canUpload, recordReadOnly]);
  useEffect(() => {
    if (!pendingReset) return;
    const frame = window.requestAnimationFrame(() => {
      resetPromptRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      resetPromptRef.current?.querySelector<HTMLButtonElement>('button')?.focus();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [pendingReset]);

  const append = (role: ChatMessage['role'], content: string, kind: ChatMessage['kind'] = 'TEXT') => {
    setMessages((items) => [...items, { id: Date.now() + items.length, role, kind, content }]);
  };

  const executeItemDecision = async (item: ReviewItemGuidance, decision: 'APPROVED' | 'REJECTED', reason?: string) => {
    if (!snapshot || !current || !canReview) return;
    if (decision === 'REJECTED' && !reason) {
      setFeedbackTarget(item);
      setQuestion('');
      window.requestAnimationFrame(() => inputRef.current?.focus());
      return;
    }
    const finalReason = reason ?? `已核对设计资料和生成位置，确认采纳“${item.name}”`;
    append('USER', decision === 'APPROVED' ? `采纳“${item.name}”` : `不采纳“${item.name}”：${finalReason}`);
    try {
      const next = await runtime.execute({
        type: 'DECIDE_REVIEW_ITEM', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
        documentVersion: current.documentVersion, itemId: item.itemId, decision,
        reviewer: currentUser.displayName, reason: finalReason,
      });
      acceptSnapshot(next.snapshot);
      append('ASSISTANT', decision === 'APPROVED' ? `已人工采纳“${item.name}”。` : `已记录对“${item.name}”的意见；模型对象仍保留，发布时会带上这项决定。`, 'RECEIPT');
      setFeedbackTarget(null);
    } catch (error) {
      append('ASSISTANT', presentBusinessError(error, '审核操作暂时无法保存，请重新打开后重试。'));
    }
  };

  useEffect(() => {
    if (!reviewRequest || !reviewBatch) return;
    const item = reviewBatch.groups.flatMap((group) => group.items).find((value) => value.itemId === reviewRequest.itemId);
    if (item) void executeItemDecision(item, reviewRequest.decision);
    onReviewRequestHandled?.();
  }, [reviewRequest?.requestId]);

  const executeIntent = async (intent: ReviewIntent) => {
    if (intent.type === 'NEEDS_CLARIFICATION') {
      append('ASSISTANT', '我没有执行操作。请明确对象和中文理由，或使用当前对象旁的审核操作。');
      return;
    }
    if (!snapshot || !current) return;
    try {
      if (intent.type === 'CONFIRM_GROUP') {
        if (!canReview) return;
        const next = await runtime.execute({ type: 'CONFIRM_BUSINESS_GROUP', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion, documentVersion: current.documentVersion, groupId: intent.groupId, reviewer: currentUser.displayName, reason: intent.reason });
        acceptSnapshot(next.snapshot);
        append('ASSISTANT', '已采纳本组所有尚未决定的对象；已有意见保持不变。', 'RECEIPT');
      } else if (intent.type === 'REJECT_ITEM') {
        if (!canReview) return;
        const item = reviewBatch?.groups.flatMap((group) => group.items).find((value) => value.itemId === intent.itemId);
        if (item) await executeItemDecision(item, 'REJECTED', intent.reason);
      } else if (intent.type === 'PUBLISH') {
        if (!canPublish) return;
        const next = await runtime.execute({ type: 'PUBLISH_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion, documentVersion: current.documentVersion, reviewer: currentUser.displayName, reason: intent.reason });
        acceptSnapshot(next.snapshot);
        if (current.fixture.modelVersion) {
          onPublished?.({ systemCode: snapshot.systemCode, catalogVersion: current.fixture.modelVersion });
        }
        append('ASSISTANT', `发布完成：资料 ${current.documentVersion} 已生成不可变模型 ${current.fixture.modelVersion}。`, 'RECEIPT');
      }
    } catch (error) {
      append('ASSISTANT', presentBusinessError(error, '操作暂时无法完成，请重新打开后重试。'));
    }
  };

  const addSourceIds = async (sourceIds: string[], label: string) => {
    if (!snapshot?.sourceWorkspace || !sourceIds.length) return;
    const before = snapshot.sourceWorkspace.selectedSources.length;
    const next = await runtime.execute({ type: 'ADD_SOURCES', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion, sourceIds });
    const total = next.snapshot.sourceWorkspace?.selectedSources.length ?? before;
    acceptSnapshot(next.snapshot); append('USER', label); append('ASSISTANT', total === before ? '这些来源已经登记，本次没有重复加入。' : `已登记 ${total - before} 个新来源；当前共有 ${total} 个来源。`, 'RECEIPT');
  };

  const startSourceModeling = async () => {
    if (!snapshot?.sourceWorkspace) return;
    setBusy(true);
    try {
      append('USER', '使用当前来源开始建模');
      const next = await runtime.execute({ type: 'START_MODELING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion });
      const blockers = next.snapshot.sourceWorkspace?.batch.findings.filter((finding) => finding.severity === 'BLOCKER').length ?? 0;
      acceptSnapshot(next.snapshot); append('ASSISTANT', `资料包已冻结。我已完成独立整理和交叉验证，发现 ${blockers} 项结构冲突；处理完成后即可生成候选模型。`);
    } catch (error) { append('ASSISTANT', presentBusinessError(error, '无法开始建模，请重新打开资料后重试。')); }
    finally { setBusy(false); }
  };

  const removeSource = async (sourceId: string, snapshotId?: string) => {
    if (!snapshot?.sourceWorkspace) return;
    const source = snapshot.sourceWorkspace.selectedSources.find((item) => item.sourceId === sourceId)
      ?? managedBatchSources.find((item) => item.snapshotId === snapshotId)?.asset;
    try {
      const next = await runtime.execute(snapshotId
        ? { type: 'DETACH_SOURCE_SNAPSHOT', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion, snapshotId }
        : { type: 'REMOVE_SOURCE', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion, sourceId });
      acceptSnapshot(next.snapshot);
      append('USER', `移除来源：${source?.displayName ?? sourceId}`);
      append('ASSISTANT', '来源已从当前资料包移除。', 'RECEIPT');
    } catch (error) {
      append('ASSISTANT', presentBusinessError(error, '来源暂时无法移除，请重新打开后重试。'));
    }
  };

  const resolveFinding = async (findingId: string, resolutionId: string) => {
    if (!snapshot?.sourceWorkspace) return;
    const finding = snapshot.sourceWorkspace.batch.findings.find((item) => item.findingId === findingId);
    const option = finding?.options.find((item) => item.id === resolutionId);
    if (!finding || !option) return;
    setBusy(true);
    try {
      append('USER', option.label);
      const next = await runtime.execute({
        type: 'RESOLVE_FINDING', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion,
        findingId, resolutionId, reviewer: currentUser.displayName,
        reason: resolutionId === 'keep_blocked' ? '当前来源仍不足以支持明确决定，保留阻断并等待补充资料' : `已核对权威来源和影响对象，选择“${option.label}”作为本批次口径`,
      });
      acceptSnapshot(next.snapshot); append('ASSISTANT', resolutionId === 'keep_blocked' ? '已保留阻断，当前批次不会生成可发布模型。' : `已记录决定：“${option.label}”。`, 'RECEIPT');
    } catch (error) { append('ASSISTANT', presentBusinessError(error, '差异决定暂时无法保存，请重新打开后重试。')); }
    finally { setBusy(false); }
  };

  const generateSourceModel = async () => {
    if (!snapshot?.sourceWorkspace) return;
    setBusy(true);
    try {
      append('USER', '生成候选模型');
      const next = await runtime.execute({ type: 'GENERATE_MODEL', systemCode: snapshot.systemCode, expectedRevision: snapshot.lockVersion });
      const candidate = next.snapshot.sourceWorkspace?.candidateModel;
      acceptSnapshot(next.snapshot); append('ASSISTANT', candidate
        ? `候选模型已生成：${candidate.counts.entities} 个实体、${candidate.counts.events} 个事件、${candidate.counts.fields} 个字段、${candidate.counts.relations} 条关系、${candidate.counts.dimensions} 个维度、${candidate.counts.metrics} 个指标和 ${candidate.counts.ruleCandidates} 个规则候选。当前只供阅读，不进入审核或发布。`
        : '候选模型已生成，可以继续进行人工审核。', 'RECEIPT');
    } catch (error) { append('ASSISTANT', presentBusinessError(error, '候选模型暂时无法生成，请重新打开资料后重试。')); }
    finally { setBusy(false); }
  };

  useEffect(() => {
    if (!findingRequest || !snapshot?.sourceWorkspace) return;
    void resolveFinding(findingRequest.findingId, findingRequest.resolutionId);
    onFindingRequestHandled?.();
  }, [findingRequest?.requestId]);

  const processKnown = async (prepared: PreparedFile, systemCode: string, documentCode: string) => {
    const targetRuntime = getRuntime(systemCode);
    const targetSnapshot = await readSnapshot(systemCode);
    const known = semanticFixture.documents.find((item) => item.sha256 === prepared.sha256)!;
    const route = resolveUploadRouting({
      currentSystemCode: activeSystem, sha256: prepared.sha256, workspaces,
      fixture: semanticFixture, targetSnapshot,
    });
    if (route.kind === 'CHOOSE_DESTINATION') throw new Error('无法确认这份资料所属的模型空间');

    if (route.continuation === 'OPEN_EXISTING') {
      selectSystem(systemCode);
      acceptSnapshot(targetSnapshot);
      selectVersion(route.selection ?? 'WORKSPACE');
      setAnalysisState(null);
      append('ASSISTANT', route.selection === 'WORKSPACE'
        ? `资料 ${known.documentVersion} 已在该模型空间中，已切换到当前建模流程。`
        : `资料 ${known.documentVersion} 已发布，已切换到模型 ${route.selection}。`, 'RECEIPT');
      return;
    }

    let workingSnapshot = targetSnapshot;
    if (route.continuation === 'UPLOAD_AND_GENERATE') {
      const uploaded = await targetRuntime.execute({
        type: 'UPLOAD_DOCUMENT', systemCode, expectedRevision: targetSnapshot.lockVersion,
        documentCode, fileName: prepared.file.name, content: prepared.content, size: prepared.file.size, sha256: prepared.sha256,
      });
      workingSnapshot = uploaded.snapshot;
    }
    selectSystem(systemCode);
    acceptSnapshot(workingSnapshot);
    selectVersion('WORKSPACE');
    setAnalysisState({ documentVersion: known.documentVersion, activeIndex: 0, completed: false });
    for (let index = 1; index < 7; index += 1) {
      await wait(window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 360);
      setAnalysisState({ documentVersion: known.documentVersion, activeIndex: index, completed: false });
    }
    const generated = await targetRuntime.execute({
      type: 'GENERATE_MODEL', systemCode, expectedRevision: workingSnapshot.lockVersion,
      documentVersion: known.documentVersion,
    });
    acceptSnapshot(generated.snapshot);
    setAnalysisState({ documentVersion: known.documentVersion, activeIndex: 6, completed: true });
    if (!known.qualityGate.publishable) append('ASSISTANT', `资料 ${known.documentVersion} 已完成识别，但需要补充设计口径；当前正式模型保持不变。`);
  };

  const storeUnknown = async (prepared: PreparedFile, systemCode: string, documentCode: string) => {
    const targetRuntime = getRuntime(systemCode);
    const targetSnapshot = await readSnapshot(systemCode);
    const uploaded = await targetRuntime.execute({
      type: 'UPLOAD_DOCUMENT', systemCode, expectedRevision: targetSnapshot.lockVersion, documentCode,
      fileName: prepared.file.name, content: prepared.content, size: prepared.file.size, sha256: prepared.sha256,
    });
    selectSystem(systemCode);
    acceptSnapshot(uploaded.snapshot);
    append('ASSISTANT', `资料已加入“${workspaces.find((item) => item.systemCode === systemCode)?.displayName ?? systemCode}”，但尚无预生成分析结果；当前已发布模型没有变化。`);
  };

  const routePreparedFile = async (prepared: PreparedFile) => {
    const decision = resolveUploadRouting({ currentSystemCode: activeSystem, sha256: prepared.sha256, workspaces, fixture: semanticFixture });
    if (decision.kind === 'CURRENT') {
      await processKnown(prepared, decision.systemCode, decision.documentCode);
      return;
    }
    setRoutingRequest(decision.kind === 'SWITCH_EXISTING'
      ? { kind: 'SWITCH', ...prepared, systemCode: decision.systemCode, documentCode: decision.documentCode }
      : { kind: 'CHOOSE', ...prepared });
  };

  const send = async () => {
    if (busy) return;
    if (pendingFiles.length) {
      const files = pendingFiles;
      setPendingFiles([]);
      setAttachmentSelected(null);
      append('USER', `附件：${files.map((file) => file.name).join('、')}`);
      setBusy(true);
      try {
        if (multiSource && snapshot?.sourceWorkspace) {
          const packageSha = activeSystem === 'omnichannel_retail_ops' ? await sha256Hex(omnichannelSourcePack.bytes) : '';
          const sourceIds = new Set<string>();
          for (const file of files) {
            const bytes = new Uint8Array(await file.arrayBuffer());
            const fingerprint = `sha256:${await sha256Hex(bytes)}`;
            if (activeSystem === 'omnichannel_retail_ops' && file.name === omnichannelSourcePack.name && fingerprint === `sha256:${packageSha}`) {
              omnichannelSourceAssets.filter((item) => item.origin === 'FILE_UPLOAD').forEach((item) => sourceIds.add(item.sourceId));
              continue;
            }
            const match = snapshot.sourceWorkspace.availableSources.find((item) => item.fingerprint === fingerprint);
            if (match) sourceIds.add(match.sourceId);
          }
          if (!sourceIds.size) append('ASSISTANT', '这些文件尚无预生成处理结果，当前资料包没有变化。');
          else await addSourceIds([...sourceIds], `添加 ${sourceIds.size} 个文件来源`);
        } else {
          const file = files[0];
          const content = await file.text();
          const digest = await sha256Hex(content);
          if (documentOnly && onMarkdownDocument) await onMarkdownDocument({ fileName: file.name, content, sha256: digest });
          else await routePreparedFile({ file, content, sha256: digest });
        }
      } catch (error) {
        append('ASSISTANT', presentBusinessError(error, '资料处理失败，请重新打开后重试。'));
      } finally {
        setBusy(false);
      }
      return;
    }
    const text = question.trim();
    if (!text) return;
    setQuestion('');
    if (routingRequest?.kind === 'JOIN') {
      const error = validateModelingCode(text);
      if (error) { append('ASSISTANT', `资料编码无效：${error}`); return; }
      append('USER', text);
      await storeUnknown(routingRequest, activeSystem, text);
      setRoutingRequest(null);
      return;
    }
    if (routingRequest?.kind === 'CREATE') {
      const parts = text.split(/[／/，,]/).map((item) => item.trim()).filter(Boolean);
      if (parts.length !== 3) { append('ASSISTANT', '请按“空间名称 / system_code / document_code”填写三项。'); return; }
      const [displayName, systemCode, documentCode] = parts;
      const codeError = validateModelingCode(systemCode) ?? validateModelingCode(documentCode);
      if (codeError) { append('ASSISTANT', codeError); return; }
      append('USER', text);
      try {
        createWorkspace({ displayName, systemCode });
        await storeUnknown(routingRequest, systemCode, documentCode);
        setRoutingRequest(null);
      } catch (error) {
        append('ASSISTANT', presentBusinessError(error, '创建模型空间失败，请重新填写后重试。'));
      }
      return;
    }
    if (feedbackTarget) {
      const error = validateObjectionReason(text);
      if (error) { append('ASSISTANT', error); return; }
      await executeItemDecision(feedbackTarget, 'REJECTED', text);
      return;
    }
    append('USER', text);
    if (activePrompt) {
      const intent = interpretReviewInput(text, activePrompt);
      if (intent.type !== 'NEEDS_CLARIFICATION') {
        await executeIntent(intent);
        return;
      }
    }
    await wait(120);
    append('ASSISTANT', answerQuestion(text, current, multiSource, evidenceOnly, formalCatalog?.data as unknown as Record<string, unknown>));
  };

  const offerFiles = (files: File[]) => {
    if (!files.length) return;
    if (!canUpload) {
      void message.warning('正式模型只能提问；请进入“我的草稿”添加资料');
      return;
    }
    if (multiSource) {
      const next = validateSourceAttachments([...pendingFiles, ...files]);
      if (next.error) { void message.warning(next.error); return; }
      setPendingFiles(next.files); setAttachmentSelected(null); return;
    }
    const next = selectSingleMarkdown(pendingFiles[0] ?? null, files[0]);
    if (next.error) { void message.warning(next.error); return; }
    setPendingFiles(next.file ? [next.file] : []); setAttachmentSelected(null);
  };
  const handlePaste = (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const files = event.clipboardData.files;
    const text = event.clipboardData.getData('text/plain');
    const kind = classifyClipboardAttachment(files, text);
    if (!canUpload && kind !== 'TEXT') {
      event.preventDefault();
      void message.warning('正式模型只能提问；请进入“我的草稿”添加资料');
      return;
    }
    if (kind === 'FILE') {
      event.preventDefault();
      offerFiles(Array.from(files));
    } else if (kind === 'FILE_NAME_ONLY') {
      event.preventDefault();
      void message.warning('剪贴板中只有文件名或路径，无法读取文件内容；请拖拽文件或使用回形针。');
    }
  };

  const beginReset = () => {
    append('ASSISTANT', '恢复会清除当前模型空间的 AI 建模进度。请再次确认。');
    setPendingReset(true);
  };
  const confirmReset = async () => {
    await resetWorkspace();
    setPendingReset(false);
    append('ASSISTANT', '当前空间已恢复，可以重新上传资料。', 'RECEIPT');
  };
  const activeDocumentIndex = documentOnly && documentMarkdownSha256
    ? semanticFixture.documents.findIndex((item) => item.sha256 === documentMarkdownSha256) : -1;
  const nextExample = documentOnly
    ? semanticFixture.documents[activeDocumentIndex + 1]
    : selectDemoDocument(semanticFixture, activeSystem, snapshot?.nextExpectedVersion);
  const demoMenu = projectAssistantToolMenu({
    canUpload,
    demoTools,
    nextExampleAvailable: Boolean(nextExample),
  });
  const availableSources = snapshot?.sourceWorkspace?.availableSources ?? [];
  const selectedSourceIds = new Set(snapshot?.sourceWorkspace?.selectedSources.map((source) => source.sourceId) ?? []);
  const unselectedSources = availableSources.filter((source) => !selectedSourceIds.has(source.sourceId));
  const databaseSources = unselectedSources.filter((source) => source.origin === 'DATABASE_CONNECTOR');
  const repositorySources = unselectedSources.filter((source) => source.origin === 'CODE_REPOSITORY');
  const usesUnifiedConnectorCatalog = Boolean(snapshot?.sourceWorkspace?.connectorCatalog?.length);
  const sourceMenu: AssistantToolMenuItem = {
    key: 'source-root', label: '来源', children: [
      ...(availableSources.some((source) => source.origin === 'FILE_UPLOAD') ? [{ key: 'source-upload', label: '上传文件' }] : []),
      ...(usesUnifiedConnectorCatalog ? [
        { key: 'source-configure', label: '配置新来源' },
        { key: 'source-yaml', label: '导入 YAML 配置' },
        { key: 'source-manage', label: '管理已配置来源' },
      ] : [
        ...(databaseSources.length ? [{ key: 'source-database', label: '连接数据源' }] : []),
        ...(repositorySources.length ? [{ key: 'source-repository', label: '连接代码仓库' }] : []),
      ]),
      ...(activeSystem === 'omnichannel_retail_ops' ? [{ key: 'source-download', label: '下载本空间资料包' }] : []),
    ],
  };
  const toolMenu: AssistantToolMenuItem[] = documentOnly && canUpload ? [
    { key: 'source-upload', label: '上传 Markdown' },
    ...demoMenu,
  ] : multiSource && canUpload ? [
    sourceMenu,
    { key: 'reset', label: '恢复当前空间', danger: true },
  ] : demoMenu as AssistantToolMenuItem[];
  const sourceChoices: InlineChoice[] = sourceChoiceMode === 'DATABASE' ? [
    ...databaseSources.map((source) => ({
      id: source.sourceId, label: source.displayName, description: source.summary,
      action: async () => { await addSourceIds([source.sourceId], `连接 ${source.displayName}`); setSourceChoiceMode(null); },
    })),
    { id: 'cancel', label: '取消', description: '不添加数据源。', action: () => setSourceChoiceMode(null) },
  ] : sourceChoiceMode === 'REPOSITORY' ? [
    ...repositorySources.map((source) => ({
      id: source.sourceId, label: source.displayName, description: source.summary,
      action: async () => { await addSourceIds([source.sourceId], `连接 ${source.displayName}`); setSourceChoiceMode(null); },
    })),
    { id: 'cancel', label: '取消', description: '不添加代码仓库。', action: () => setSourceChoiceMode(null) },
  ] : sourceChoiceMode === 'CONNECTOR' ? [
    ...connectorTypeCatalog.map((connector) => ({
      id: connector.connectorTypeId, label: connector.displayName,
      description: `${connector.vendor} · ${sourceFamilyLabel(connector.family)} · ${connector.maturity}`,
      action: () => { onConfigureSource?.(connector.connectorTypeId); setSourceChoiceMode(null); },
    })),
    { id: 'cancel', label: '取消', description: '暂不配置来源。', action: () => setSourceChoiceMode(null) },
  ] : [];

  const routingChoices: InlineChoice[] = routingRequest?.kind === 'SWITCH' ? [
    { id: 'switch', label: '切换并开始建模', description: '这份资料属于数码产品销售仓储语义模型。', action: async () => {
      const pending = routingRequest;
      setBusy(true);
      try {
        await processKnown(pending, pending.systemCode, pending.documentCode);
        setRoutingRequest(null);
      } catch (error) {
        append('ASSISTANT', presentBusinessError(error, '切换模型空间失败，请重新选择后重试。'));
      } finally {
        setBusy(false);
      }
    } },
    { id: 'cancel', label: '取消', description: '当前空间和模型保持不变。', action: () => setRoutingRequest(null) },
  ] : routingRequest?.kind === 'CHOOSE' ? [
    { id: 'join', label: '加入当前空间', description: '下一步填写资料编码。', action: () => { setRoutingRequest({ ...routingRequest, kind: 'JOIN' }); setQuestion(''); inputRef.current?.focus(); } },
    { id: 'create', label: '创建新空间', description: '下一步填写空间名称和两个编码。', action: () => { setRoutingRequest({ ...routingRequest, kind: 'CREATE' }); setQuestion(''); inputRef.current?.focus(); } },
    { id: 'cancel', label: '取消', description: '不保存这份资料。', action: () => setRoutingRequest(null) },
  ] : [];

  const contextMessage = collaboration.viewMode === 'FORMAL'
    ? '正式模型 · 可以围绕当前版本提问；上传资料或修改模型请进入“我的草稿”。'
    : recordReadOnly
    ? '当前草稿记录只读 · 可以继续提问，不会修改已冻结内容'
    : evidenceOnly
      ? '连接已推广的数据库证据快照后，可以查看结构、程序逻辑、实际使用和数据形态。首批暂不生成语义模型。'
      : multiSource
      ? '添加来源后，我会先校验配置、发现读取范围并冻结证据快照，再用于交叉验证和建模。'
      : '添加 Markdown 设计资料后，我会确认模型空间、持续展示建模过程，并协助逐项审核。';
  const hasSources = !documentOnly && Boolean(
    snapshot?.sourceWorkspace?.selectedSources.length
    || snapshot?.sourceWorkspace?.batch.snapshotIds?.length,
  );
  const showContextMessage = canUpload && !leadingContent && !current && !hasSources
    && messages.length === 0 && !timeline && !routingRequest && !sourceChoiceMode && !pendingReset;

  return <section className="ai-assistant" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); offerFiles(Array.from(event.dataTransfer.files)); }}>
    <div className="assistant-thread">
      {leadingContent}
      {showContextMessage && <div id="workflow-document" className="assistant-message assistant-empty-guidance"><Typography.Paragraph>{contextMessage}</Typography.Paragraph></div>}
      {!documentOnly && !recordReadOnly && snapshot && <WorkflowHistory snapshot={snapshot} />}
      {messages.map((item) => <div key={item.id} className={`assistant-message ${item.role === 'USER' ? 'assistant-message-user' : ''} ${item.kind === 'RECEIPT' ? 'assistant-receipt' : ''}`}>{item.content}</div>)}
      {canUpload && routingRequest && <div className="assistant-message">{routingRequest.kind === 'SWITCH' ? '这份资料属于另一个已有模型空间。确认后才会切换并开始建模。' : routingRequest.kind === 'CHOOSE' ? '这份资料没有预生成结果。请选择保存位置，系统不会猜测编码。' : routingRequest.kind === 'JOIN' ? '请输入资料编码，例如 inventory_design。' : '请输入：空间名称 / system_code / document_code'}{routingChoices.length > 0 && <InlineChoices choices={routingChoices} />}</div>}
      {sourceChoiceMode && <div className="assistant-message"><strong>{sourceChoiceMode === 'DATABASE' ? '选择一个预置数据源' : sourceChoiceMode === 'REPOSITORY' ? '选择预置代码仓库' : '选择来源类型'}</strong><Typography.Paragraph type="secondary">配置连接、测试并确认读取范围后，生成不可变证据快照。</Typography.Paragraph><InlineChoices choices={sourceChoices} /></div>}
      {multiSource && snapshot?.sourceWorkspace && <SourceWorkflowRecords snapshot={snapshot} busy={busy} managedSources={managedBatchSources} onStart={() => void startSourceModeling()} onRemove={(sourceId, snapshotId) => void removeSource(sourceId, snapshotId)} onResolve={(findingId, resolutionId) => void resolveFinding(findingId, resolutionId)} onGenerate={() => void generateSourceModel()} />}
      {timeline && <div id="workflow-analysis"><AnalysisProgress lines={timeline} /></div>}
      {current && <ModelGeneratedSummary result={current} onNavigate={(target) => onOpenModelSummary?.(target)} />}
      {current?.status === 'NEEDS_SUPPLEMENT' && <Alert type="error" showIcon icon={<WorkflowStatusMark tone="ERROR" label="需要补充" />} message="设计资料需要补充" description={<div>{current.fixture.qualityGate.blockers.map((item) => <div key={item.title}><strong>{item.title}</strong><br /><span>{item.detail}</span></div>)}</div>} />}
      <div id="workflow-review">
        {current && reviewBatch && current.confirmedGroups.map((groupId) => {
          const group = reviewBatch.groups.find((item) => item.id === groupId);
          return group ? <div className="review-group-receipt" key={groupId}><WorkflowStatusMark compact tone="SUCCESS" label="已确认" /> <span>{group.name}已确认</span></div> : null;
        })}
        {canReview && current?.status === 'GENERATED' && nextGroup && <ReviewGroupSummary group={nextGroup} result={current} onInspect={onInspect} onDecide={executeItemDecision} />}
        {activePrompt && <ConversationQuestion prompt={activePrompt} document={current?.fixture} onSelect={(choice: ReviewChoice) => void executeIntent(choice.intent)} checksVisible={publishChecksVisible} onChecksVisibleChange={setPublishChecksVisible} />}
      </div>
      {current?.status === 'PUBLISHED' && publishedChecksView && <div className="assistant-success"><div className="assistant-success-header"><WorkflowStatusMark tone="PUBLISHED" label="已发布" /><strong>已发布 {current.fixture.modelVersion}</strong></div>{showOntologyPrompt && <div className="published-next-step"><span>模型已发布，可以在本体建模查看实体、事件和关系。</span><Button type="primary" size="small" icon={<NodeIndexOutlined />} onClick={onOpenOntology}>查看本体建模</Button></div>}<Collapse ghost size="small" defaultActiveKey={publishedChecksView.autoExpand ? ['checks'] : []} items={[{ key: 'checks', label: publishedChecksView.summary, children: <PublicationChecksDetails view={publishedChecksView} /> }]} /></div>}
      {snapshot?.recoveryRequired && <Alert type="warning" showIcon icon={<WorkflowStatusMark tone="PENDING" label="需要恢复" />} message={snapshot.recoveryMessage} />}
      {canUpload && pendingReset && <div ref={resetPromptRef} id="workflow-reset-confirmation" className="assistant-message assistant-reset-confirmation"><InlineChoices choices={[{ id: 'confirm-reset', label: '确认恢复', description: '清除当前空间的 AI 建模进度。', danger: true, action: confirmReset }, { id: 'cancel-reset', label: '取消', description: '保持当前状态。', action: () => setPendingReset(false) }]} /></div>}
    </div>
    {canAsk && <AssistantInput
      inputRef={inputRef}
      value={question}
      onChange={setQuestion}
      onSend={send}
      onFiles={offerFiles}
      onPaste={handlePaste}
      pendingFiles={pendingFiles}
      attachmentSelected={attachmentSelected}
      onAttachmentSelect={setAttachmentSelected}
      onAttachmentRemove={(index) => { setPendingFiles((items) => items.filter((_, itemIndex) => itemIndex !== index)); setAttachmentSelected(null); }}
      busy={busy}
      canUpload={canUpload}
      placeholder={feedbackTarget ? `正在反馈：${feedbackTarget.name}，请输入中文原因…` : routingRequest?.kind === 'JOIN' ? '输入 document_code…' : routingRequest?.kind === 'CREATE' ? '空间名称 / system_code / document_code' : canUpload ? documentOnly ? '提问，或添加 Markdown 到个人草稿…' : '提问，或添加资料到个人草稿…' : '询问当前正式模型…'}
      demoMenu={toolMenu}
      documentOnly={documentOnly}
      onDemoAction={(key) => {
        if (key === 'source-database') setSourceChoiceMode('DATABASE');
        if (key === 'source-repository') setSourceChoiceMode('REPOSITORY');
        if (key === 'source-configure') setSourceChoiceMode('CONNECTOR');
        if (key === 'source-yaml') onImportSourceYaml?.();
        if (key === 'source-manage') onManageSources?.();
        if (key === 'source-download') downloadBytes(omnichannelSourcePack.bytes, omnichannelSourcePack.name, 'application/zip');
        if (key === 'download' && nextExample) downloadText(nextExample.content, nextExample.fileName);
        if (key === 'unknown') downloadText(unknownDocument, '99-门店促销活动补充设计-未知.md');
        if (key === 'reset') beginReset();
      }}
    />}
  </section>;
}

function InlineChoices({ choices }: { choices: InlineChoice[] }) {
  return <div className="conversation-choices compact">{choices.map((choice) => <button type="button" className={choice.danger ? 'danger' : ''} key={choice.id} onClick={() => choice.action()}><span><strong>{choice.label}</strong></span><small>{choice.description}</small></button>)}</div>;
}

function AssistantInput({
  inputRef, value, onChange, onSend, onFiles, onPaste, pendingFiles, attachmentSelected,
  onAttachmentSelect, onAttachmentRemove, busy, canUpload, placeholder, demoMenu, onDemoAction, documentOnly,
}: {
  inputRef: React.RefObject<TextAreaRef | null>;
  value: string;
  onChange(value: string): void;
  onSend(): void;
  onFiles(files: File[]): void;
  onPaste(event: React.ClipboardEvent<HTMLTextAreaElement>): void;
  pendingFiles: File[];
  attachmentSelected: number | null;
  onAttachmentSelect(index: number): void;
  onAttachmentRemove(index: number): void;
  busy?: boolean;
  canUpload: boolean;
  placeholder?: string;
  demoMenu: AssistantToolMenuItem[];
  onDemoAction(key: string): void;
  documentOnly: boolean;
}) {
  const filePicker = useRef<HTMLInputElement>(null);
  return <div className="assistant-input"><div className="assistant-input-shell">
    {pendingFiles.length > 0 && <div className="assistant-attachment-list">{pendingFiles.map((file, index) => <button type="button" key={`${file.name}:${file.size}:${index}`} className={`assistant-attachment-chip ${attachmentSelected === index ? 'selected' : ''}`} onClick={() => onAttachmentSelect(index)} onKeyDown={(event) => { if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); onAttachmentRemove(index); } }}><PaperClipOutlined /><span>{file.name}</span>{attachmentSelected === index && <DeleteOutlined aria-hidden="true" />}</button>)}</div>}
    <Input.TextArea ref={inputRef} value={value} variant="borderless" placeholder={placeholder} autoSize={{ minRows: 2, maxRows: 6 }} onPaste={onPaste} onChange={(event) => onChange(event.target.value)} onPressEnter={(event) => { if (!event.shiftKey) { event.preventDefault(); onSend(); } }} />
    <div className="assistant-input-actions">
      {canUpload && <input ref={filePicker} hidden type="file" multiple={!documentOnly} accept={documentOnly ? '.md,text/markdown' : '.md,.pdf,.wps,.png,.jpeg,.jpg,.zip'} onChange={(event) => { onFiles(Array.from(event.target.files ?? [])); event.target.value = ''; }} />}
      {canUpload && demoMenu.length > 0 && <Dropdown menu={{ items: demoMenu, onClick: ({ key }) => key === 'source-upload' ? filePicker.current?.click() : onDemoAction(key) }} trigger={['click']}><Button type="text" icon={<PlusOutlined />} aria-label={documentOnly ? '添加 Markdown' : '资料与连接'} /></Dropdown>}
      <Button type="primary" shape="circle" icon={<SendOutlined />} loading={busy} onClick={onSend} aria-label="发送" />
    </div>
  </div></div>;
}
