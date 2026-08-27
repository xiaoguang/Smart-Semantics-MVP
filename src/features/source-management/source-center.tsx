import { useEffect, useState } from 'react';
import {
  CheckCircleOutlined, ClockCircleOutlined, CloudDownloadOutlined, CloudUploadOutlined,
  DatabaseOutlined, DisconnectOutlined, MoreOutlined, PlayCircleOutlined, SettingOutlined,
} from '@ant-design/icons';
import { DndContext, KeyboardSensor, PointerSensor, TouchSensor, useDraggable, useDroppable, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import {
  Alert, Button, Collapse, Dropdown, Empty, Input, InputNumber, Modal, Segmented, Select, Space, Tabs,
  Spin, Tag, Typography, message,
} from 'antd';
import { connectorFamilyCatalog, connectorTypeById } from './connector-catalog.ts';
import { sourceFamilyLabel } from '../ai-modeling/source-bundle.ts';
import { useSourceManagement } from './source-management-context.tsx';
import type { EvidenceBatch, FormField, SourceConnection, SourceSnapshot } from './types.ts';
import SemanticCollectionPanel from '../semantic-evidence/semantic-collection-panel.tsx';
import ReadableTechnicalValue from './readable-technical-value.tsx';
import { sourceCenterMobileClass, sourceCenterMobileNavigation } from './source-center-navigation.ts';
import { paginateSourceHistory } from './history-pagination.ts';
import type { DemoContentSourceConfiguration } from '../guanyijia-demo-content/demo-content-review.ts';
import BackAction from '../../components/back-action.tsx';
import {
  type SourceBoardCommand, type SourceBoardModel, type SourceBoardSlot,
  type SourceCandidateInstance, type SourceBoardStatusTag,
} from './source-board.ts';
import './source-management.css';

type CenterView = 'CATALOG' | 'CONNECTIONS' | 'YAML';
export type SourceCenterMode = 'MANAGE' | 'SNAPSHOT_OVERVIEW' | 'RUN_CONFIGURATION';
const semanticExampleConnectionId = '__semantic_example__';
const sourceBoardCompactQuery = '(max-width: 899px)';
type EditorState = {
  connectionId?: string;
  connectorTypeId: string;
  displayName: string;
  environment: string;
  config: Record<string, unknown>;
  scope: Record<string, unknown>;
  credentialRef?: string;
};

type DemoSourceStatus = {
  sourceId: string;
  status: string;
};

function demoOriginLabel(origin: DemoContentSourceConfiguration['origin']) {
  if (origin === 'SNAPSHOT_REFERENCE') return '固定参考快照';
  if (origin === 'SOURCE_NATIVE') return '固定源码节选';
  if (origin === 'DEMO_AUTHORED') return '演示编写资料';
  return '派生演示资料';
}

function sourceNameForDemo(sourceId: string) {
  return {
    guanyijia_mysql: '数据库',
    guanyijia_github: '代码仓库',
    guanyijia_official_docs: '业务说明（演示资料）',
    guanyijia_demo_policy: 'ERP 管理制度（演示草案）',
    guanyijia_semantica_demo: '企业术语图（由已有资料整理）',
  }[sourceId] ?? sourceId;
}

function presentSourceCenterError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : '';
  if (/保存|存储|quota|权限/iu.test(detail)) return '浏览器暂时无法保存本次设置，请检查存储权限后重试。';
  if (/连接|网络|timeout|超时/iu.test(detail)) return '连接暂时不可用，请检查配置后重试。';
  if (/yaml|解析|格式/iu.test(detail)) return '配置格式无法识别，请检查内容后重试。';
  return fallback;
}

function demoSourceStatusLabel(status?: string) {
  if (status === 'READING') return '读取中';
  if (status === 'DOCUMENT_READY') return '待审阅';
  if (status === 'REVIEWED' || status === 'ALIGNED') return '已审阅';
  if (status === 'CONFLICT_BLOCKED') return '存在差异';
  return '待读取';
}

function sourceRoleLabel(role: SourceCandidateInstance['sourceRole']) {
  return {
    DATABASE: '数据库',
    CODE_REPOSITORY: '代码仓库',
    OFFICIAL_DOCUMENTS: '官方业务文档',
    ERP_POLICY: 'ERP 管理制度',
    TERMINOLOGY: '术语图',
  }[role];
}

function SourceStatusTag({ tag }: { tag: SourceBoardStatusTag }) {
  const Icon = tag.tone === 'SUCCESS' ? CheckCircleOutlined
    : tag.tone === 'INFO' ? ClockCircleOutlined
      : tag.tone === 'WARNING' ? SettingOutlined
        : tag.tone === 'DANGER' ? DisconnectOutlined
          : DisconnectOutlined;
  return <span className={`source-status-tag source-status-tag-${tag.tone.toLowerCase()}`}>
    <Icon aria-hidden="true" />{tag.label}
  </span>;
}

function SourceBoardSlotCard({ slot, canAcceptDrop, onCommand }: {
  slot: SourceBoardSlot;
  canAcceptDrop: boolean;
  onCommand(command: SourceBoardCommand): void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: `source-role:${slot.role}`, disabled: slot.locked || !canAcceptDrop });
  return <article
    ref={setNodeRef}
    className={`source-board-slot${isOver ? ' source-board-slot-drop-target' : ''}${slot.locked ? ' source-board-slot-locked' : ''}${canAcceptDrop ? '' : ' source-board-slot-drop-disabled'}`}
    data-source-role={slot.role}
  >
    <div className="source-board-slot-copy">
      <span className="source-board-slot-role">{slot.displayName}</span>
      <strong>{slot.instance?.businessName ?? slot.instance?.displayName ?? '尚未选择实例'}</strong>
      {slot.instance?.adapterLabel && <small className="source-board-slot-tech">{slot.instance.adapterLabel}{slot.instance.environmentLabel ? ` · ${slot.instance.environmentLabel}` : ''}</small>}
      <div className="source-status-tag-list">{slot.statusTags.map((tag) => <SourceStatusTag key={`${tag.kind}:${tag.label}`} tag={tag} />)}</div>
    </div>
    <div className="source-board-slot-actions">
      {slot.canRemove && <Button type="text" onClick={() => onCommand({ type: 'UNBIND_INSTANCE', role: slot.role })}>移除</Button>}
      {slot.locked && <small>运行中已锁定</small>}
    </div>
  </article>;
}

function instanceAvailabilityTag(candidate: SourceCandidateInstance): SourceBoardStatusTag {
  if (candidate.availability === 'CONNECTED') return { kind: 'AVAILABILITY', tone: 'SUCCESS', label: '已连接' };
  if (candidate.availability === 'NEEDS_CONFIGURATION') return { kind: 'AVAILABILITY', tone: 'WARNING', label: '待配置' };
  if (candidate.availability === 'CONNECTION_FAILED') return { kind: 'AVAILABILITY', tone: 'DANGER', label: '连接失败' };
  return { kind: 'AVAILABILITY', tone: 'NEUTRAL', label: '未连接' };
}

function SourceBoardInstanceCard({ candidate, onCommand, onConfigure }: {
  candidate: SourceCandidateInstance;
  onCommand(command: SourceBoardCommand): void;
  onConfigure(candidate: SourceCandidateInstance): void;
}) {
  const canDrag = !candidate.selectedRole && !candidate.incompatibilityReason && candidate.availability === 'CONNECTED';
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: `source-instance:${candidate.instanceId}`, disabled: !canDrag });
  const style = transform ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` } : undefined;
  return <article ref={setNodeRef} style={style} className={`source-instance-card${isDragging ? ' source-instance-card-dragging' : ''}${canDrag ? '' : ' source-instance-card-disabled'}`} {...attributes}>
    <div className="source-instance-card-heading">
      <div><strong>{candidate.businessName ?? candidate.displayName}</strong><small>{candidate.adapterLabel ?? sourceRoleLabel(candidate.sourceRole)}{candidate.environmentLabel ? ` · ${candidate.environmentLabel}` : ''}</small></div>
      {candidate.selectedRole && <span className="source-instance-selected">已选</span>}
    </div>
    <div className="source-instance-card-summary">
      {candidate.locationPreview && <small>{candidate.locationPreview}</small>}
      {candidate.scopePreview && <small>{candidate.scopePreview}</small>}
    </div>
    <div className="source-status-tag-list"><SourceStatusTag tag={instanceAvailabilityTag(candidate)} />
      <SourceStatusTag tag={candidate.snapshotReadiness === 'MISSING'
        ? { kind: 'AVAILABILITY', tone: 'WARNING', label: '缺少可用快照' }
        : { kind: 'AVAILABILITY', tone: 'INFO', label: '快照可用' }} />
    </div>
    {candidate.incompatibilityReason && <small className="source-instance-reason">{candidate.incompatibilityReason}</small>}
    <div className="source-instance-card-actions">
      {candidate.connectionId && <Button type="link" onClick={() => onConfigure(candidate)}>{candidate.availability === 'CONNECTED' ? '重新配置' : candidate.availability === 'NEEDS_CONFIGURATION' ? '继续配置' : '配置实例'}</Button>}
      {canDrag && <Button type="primary" size="small" onClick={() => onCommand({ type: 'BIND_INSTANCE', role: candidate.sourceRole, instanceId: candidate.instanceId })}>添加到{sourceRoleLabel(candidate.sourceRole)}</Button>}
      {canDrag && <Button type="text" className="source-instance-drag-handle" {...listeners} aria-label={`拖拽 ${candidate.displayName} 到${sourceRoleLabel(candidate.sourceRole)}`}>拖拽</Button>}
    </div>
  </article>;
}

function fieldValue(field: FormField, editor: EditorState, group: 'config' | 'scope') {
  return field.key === 'credentialRef' ? editor.credentialRef : editor[group][field.key];
}

function ConfigField({ field, editor, group, onChange }: {
  field: FormField; editor: EditorState; group: 'config' | 'scope'; onChange(value: unknown): void;
}) {
  const value = fieldValue(field, editor, group);
  if (field.type === 'number') return <InputNumber value={typeof value === 'number' ? value : undefined} placeholder={field.placeholder} onChange={onChange} />;
  if (field.type === 'select') return <Select value={typeof value === 'string' ? value : undefined} options={field.options} placeholder={field.placeholder} onChange={onChange} />;
  if (field.type === 'tags') return <Select mode="tags" value={Array.isArray(value) ? value as string[] : []} tokenSeparators={[',']} placeholder={field.placeholder} onChange={onChange} />;
  return <Input value={typeof value === 'string' ? value : ''} placeholder={field.placeholder} onChange={(event) => onChange(event.target.value)} />;
}

function SourceHistoryPagination({ page, pageCount, total, onChange }: {
  page: number; pageCount: number; total: number; onChange(page: number): void;
}) {
  if (total <= 20) return null;
  return <div className="source-history-pagination"><Button size="small" disabled={page <= 1} onClick={() => onChange(page - 1)}>上一页</Button><span>{page} / {pageCount} · 共 {total} 条</span><Button size="small" disabled={page >= pageCount} onClick={() => onChange(page + 1)}>下一页</Button></div>;
}

export default function SourceCenter({ initialConnectorTypeId, initialView = 'CONNECTIONS', attachedSnapshotIds = [], mode = 'MANAGE', demoSourceConfiguration, demoSourceStatuses = [], demoSourceBoard, onDemoSourceBoardCommand, onClose, onSnapshotReady, onBatchReady }: {
  initialConnectorTypeId?: string;
  initialView?: CenterView;
  attachedSnapshotIds?: string[];
  mode?: SourceCenterMode;
  demoSourceConfiguration?: DemoContentSourceConfiguration[];
  demoSourceStatuses?: DemoSourceStatus[];
  demoSourceBoard?: SourceBoardModel;
  onDemoSourceBoardCommand?(command: SourceBoardCommand): void;
  onClose(): void;
  onSnapshotReady?(snapshot: SourceSnapshot): void;
  onBatchReady?(batch: EvidenceBatch, snapshots: SourceSnapshot[]): void;
}) {
  const { snapshot, loading, role, run, exportYaml } = useSourceManagement();
  const [view, setView] = useState<CenterView>(initialView);
  const [selectedConnectionId, setSelectedConnectionId] = useState<string>();
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [discovery, setDiscovery] = useState<string>();
  const [yaml, setYaml] = useState('');
  const [yamlPreview, setYamlPreview] = useState<{ created: string[]; updated: string[]; unchanged: string[]; conflicts: unknown[]; errors: unknown[] }>();
  const [readProgress, setReadProgress] = useState<string[]>([]);
  const [pendingSnapshot, setPendingSnapshot] = useState<SourceSnapshot>();
  const [historyPages, setHistoryPages] = useState({ revisions: 1, snapshots: 1, audit: 1 });
  const [busy, setBusy] = useState(false);
  const [sourceBoardTab, setSourceBoardTab] = useState<'CONNECTORS' | 'INSTANCES'>('INSTANCES');
  const [sourceBoardMobilePane, setSourceBoardMobilePane] = useState<'SELECTED' | 'CANDIDATES'>('SELECTED');
  const [adapterPickerFamilyId, setAdapterPickerFamilyId] = useState<string>();
  const [draggedBoardInstanceId, setDraggedBoardInstanceId] = useState<string>();
  const [sourceBoardCompact, setSourceBoardCompact] = useState(false);
  const canManage = role === 'ADMIN';
  const canCapture = role === 'ADMIN' || role === 'EDITOR';
  const selectedConnection = snapshot?.connections.find((item) => item.connectionId === selectedConnectionId);
  const semanticExampleSelected = selectedConnectionId === semanticExampleConnectionId;
  const showGuanyijiaSemanticExample = snapshot?.defaultBatch.projectId === 'guanyijia_erp'
    && !snapshot.connections.some((item) => item.connectorTypeId === 'semantica');
  const selectedRevisions = snapshot?.revisions.filter((item) => item.connectionId === selectedConnectionId).sort((a, b) => b.revision - a.revision) ?? [];
  const selectedSnapshots = snapshot?.snapshots.filter((item) => item.connectionId === selectedConnectionId).sort((a, b) => b.capturedAt.localeCompare(a.capturedAt)) ?? [];
  const selectedAudit = snapshot?.audit.filter((item) => item.targetId === selectedConnectionId || selectedSnapshots.some((source) => source.snapshotId === item.targetId)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)) ?? [];
  const revisionHistory = paginateSourceHistory(selectedRevisions, historyPages.revisions);
  const snapshotHistory = paginateSourceHistory(selectedSnapshots, historyPages.snapshots);
  const auditHistory = paginateSourceHistory(selectedAudit, historyPages.audit);
  const connectorType = editor ? connectorTypeById(editor.connectorTypeId) : selectedConnection ? connectorTypeById(selectedConnection.connectorTypeId) : undefined;
  const configLabels = Object.fromEntries(connectorType?.configSchema.sections.flatMap((section) => section.fields.map((field) => [field.key, field.label])) ?? []);
  const scopeLabels = Object.fromEntries(connectorType?.scopeSchema.sections.flatMap((section) => section.fields.map((field) => [field.key, field.label])) ?? []);
  const batchSnapshots = snapshot?.connections.flatMap((connection) => {
    const latest = snapshot.snapshots
      .filter((item) => item.connectionId === connection.connectionId && ['READY', 'PARTIAL'].includes(item.status))
      .sort((a, b) => b.connectionRevision - a.connectionRevision || b.capturedAt.localeCompare(a.capturedAt))[0];
    return latest ? [latest] : [];
  }) ?? [];
  const batchSnapshotIds = batchSnapshots.map((item) => item.snapshotId).sort();
  const batchAlreadyAttached = snapshot?.defaultBatch.snapshotIds.every((id) => attachedSnapshotIds.includes(id)) ?? false;
  const selectedRevision = selectedRevisions[0];
  const selectedDemoSource = demoSourceConfiguration?.find((source) => source.sourceId === selectedConnectionId);
  const displayedBoardCandidates = demoSourceBoard?.candidates ?? [];
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 8 } }),
    useSensor(KeyboardSensor),
  );

  const navigateToConnection = (connectionId: string) => {
    const next = sourceCenterMobileNavigation({ type: 'SELECT', connectionId });
    setSelectedConnectionId(next.selectedConnectionId); setEditor(null); setReadProgress([]); setPendingSnapshot(undefined);
  };
  const navigateBackToList = () => {
    const next = sourceCenterMobileNavigation({ type: 'BACK' });
    setSelectedConnectionId(next.selectedConnectionId); setEditor(null); setReadProgress([]); setPendingSnapshot(undefined);
  };

  useEffect(() => {
    setView(initialView);
    if (initialConnectorTypeId) beginCreate(initialConnectorTypeId);
  }, [initialConnectorTypeId, initialView]);

  useEffect(() => {
    setHistoryPages({ revisions: 1, snapshots: 1, audit: 1 });
  }, [selectedConnectionId]);

  useEffect(() => {
    if (!['SNAPSHOT_OVERVIEW', 'RUN_CONFIGURATION'].includes(mode) || !demoSourceConfiguration?.length) return;
    if (demoSourceConfiguration.some((source) => source.sourceId === selectedConnectionId)) return;
    setSelectedConnectionId(demoSourceConfiguration[0]!.sourceId);
  }, [demoSourceConfiguration, mode, selectedConnectionId]);

  useEffect(() => {
    if (mode !== 'RUN_CONFIGURATION') return;
    const mediaQuery = window.matchMedia(sourceBoardCompactQuery);
    const update = () => setSourceBoardCompact(mediaQuery.matches);
    update();
    mediaQuery.addEventListener('change', update);
    return () => mediaQuery.removeEventListener('change', update);
  }, [mode]);

  const beginCreate = (connectorTypeId: string) => {
    const type = connectorTypeById(connectorTypeId);
    if (!type || !snapshot) return;
    setView('CATALOG'); setSelectedConnectionId(undefined); setDiscovery(undefined); setReadProgress([]); setPendingSnapshot(undefined);
    setEditor({
      connectorTypeId,
      displayName: '', environment: '', config: {}, scope: {}, credentialRef: '',
    });
  };
  const beginEdit = (connection: SourceConnection) => {
    const revision = snapshot?.revisions.filter((item) => item.connectionId === connection.connectionId).sort((a, b) => b.revision - a.revision)[0];
    if (!revision) return;
    setSelectedConnectionId(connection.connectionId); setDiscovery(undefined); setReadProgress([]); setPendingSnapshot(undefined);
    setEditor({ connectionId: connection.connectionId, connectorTypeId: connection.connectorTypeId, displayName: connection.displayName, environment: connection.environment, config: structuredClone(revision.sanitizedConfig), scope: structuredClone(revision.defaultScope), credentialRef: revision.credentialRef });
  };
  const updateField = (group: 'config' | 'scope', field: FormField, value: unknown) => setEditor((current) => current ? field.key === 'credentialRef'
    ? { ...current, credentialRef: String(value ?? '') }
    : { ...current, [group]: { ...current[group], [field.key]: value } } : current);
  const saveEditor = async () => {
    if (!editor || !snapshot) return;
    if (!editor.displayName.trim()) { message.error('请填写业务实例名称'); return; }
    setBusy(true);
    try {
      if (editor.connectionId) {
        await run({ type: 'UPDATE_CONNECTION', connectionId: editor.connectionId, sanitizedConfig: editor.config, credentialRef: editor.credentialRef, defaultScope: editor.scope });
        message.success('已创建新的配置版本，旧快照未改变');
      } else {
        const base = editor.connectorTypeId.replace(/[^a-z0-9]+/g, '_');
        const connectionId = `${base}_${snapshot.connections.filter((item) => item.connectorTypeId === editor.connectorTypeId).length + 1}`;
        await run({ type: 'CREATE_CONNECTION', connectionId, connectorTypeId: editor.connectorTypeId, displayName: editor.displayName, environment: editor.environment, sanitizedConfig: editor.config, credentialRef: editor.credentialRef, defaultScope: editor.scope });
        setSelectedConnectionId(connectionId); setEditor((current) => current ? { ...current, connectionId } : current);
        message.success('共享连接草稿已保存');
      }
      setEditor(null);
      setView('CONNECTIONS');
      setSourceBoardTab('INSTANCES');
    } catch (error) { message.error(presentSourceCenterError(error, '保存配置失败，请检查必填项后重试。')); }
    finally { setBusy(false); }
  };
  const runOnRevision = async (action: 'TEST_CONNECTION' | 'DISCOVER_SCOPE' | 'ACTIVATE_CONNECTION' | 'CAPTURE_SNAPSHOT') => {
    if (!selectedConnectionId || !snapshot) return;
    const revision = snapshot.revisions.filter((item) => item.connectionId === selectedConnectionId).sort((a, b) => b.revision - a.revision)[0];
    if (!revision) return;
    setBusy(true);
    try {
      if (action === 'CAPTURE_SNAPSHOT') {
        const type = connectorTypeById(selectedConnection?.connectorTypeId ?? '');
        const steps = [
          '配置格式已检查',
          '连接验证通过',
          '已确认读取范围',
          `已读取${type ? ` ${type.displayName}` : ''}结构与使用摘要`,
          '已整理可查看的来源位置',
        ];
        setReadProgress([`正在读取 ${selectedConnection?.displayName ?? selectedConnectionId}…`]);
        const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
        for (const step of steps) {
          if (!reduced) await new Promise((resolve) => window.setTimeout(resolve, 220));
          setReadProgress((current) => [...current, `✓ ${step}`]);
        }
        const result = await run({ type: action, connectionId: selectedConnectionId, revision: revision.revision, scope: revision.defaultScope });
        const created = result.snapshot.snapshots.filter((item) => item.connectionId === selectedConnectionId).at(-1);
        if (created) {
          setReadProgress((current) => [...current, '✓ 固定资料已准备好']);
          setPendingSnapshot(created);
        }
        message.success('资料已准备好，可以加入当前建模。');
      } else if (action === 'DISCOVER_SCOPE') {
        const result = await run({ type: action, connectionId: selectedConnectionId, revision: revision.revision });
        setDiscovery(result.discovery ? `${result.discovery.summary} · ${Object.entries(result.discovery.objectCounts).map(([key, value]) => `${key} ${value}`).join('，')}` : undefined);
      } else {
        await run({ type: action, connectionId: selectedConnectionId, revision: revision.revision });
        message.success(action === 'TEST_CONNECTION' ? '模拟连接测试已完成' : '配置版本已激活');
      }
    } catch (error) { message.error(presentSourceCenterError(error, '本次操作没有完成，请检查配置后重试。')); }
    finally { setBusy(false); }
  };
  const downloadYaml = async () => {
    const artifact = await exportYaml();
    const url = URL.createObjectURL(new Blob([artifact.content], { type: artifact.mediaType }));
    const link = document.createElement('a'); link.href = url; link.download = artifact.fileName; link.click(); URL.revokeObjectURL(url);
  };
  const disableConnection = async () => {
    if (!selectedConnectionId) return;
    try { await run({ type: 'DISABLE_CONNECTION', connectionId: selectedConnectionId }); message.success('共享连接已停用；旧快照和Catalog保持可读'); }
    catch (error) { message.error(presentSourceCenterError(error, '停用连接失败，请稍后重试。')); }
  };
  const previewYaml = async () => {
    try { const result = await run({ type: 'PREVIEW_YAML_IMPORT', content: yaml }); setYamlPreview(result.importPreview); }
    catch (error) { message.error(presentSourceCenterError(error, '配置格式无法识别，请检查内容后重试。')); }
  };
  const applyYaml = async () => {
    try { const result = await run({ type: 'APPLY_YAML_IMPORT', content: yaml }); setYamlPreview(result.importPreview); message.success('YAML 配置已应用；新增和更新项保持为待测试草稿'); }
    catch (error) { message.error(presentSourceCenterError(error, '导入配置失败，请检查内容后重试。')); }
  };
  const freezeBatch = async () => {
    if (!snapshot || batchSnapshotIds.length === 0) return;
    setBusy(true);
    try {
      const projectId = snapshot.workspaceId === 'erp_data_governance' ? 'guanyijia_erp' : 'group_retail_ops';
      const revision = snapshot.defaultBatch.revision + 1;
      await run({ type: 'FREEZE_BATCH', projectId, revision, snapshotIds: batchSnapshotIds });
      message.success('资料版本已固定，可以在个人草稿中使用。');
    } catch (error) { message.error(presentSourceCenterError(error, '固定资料版本失败，请稍后重试。')); }
    finally { setBusy(false); }
  };

  const runSourceBoardCommand = (command: SourceBoardCommand) => onDemoSourceBoardCommand?.(command);
  const handleSourceBoardDragEnd = (event: DragEndEvent) => {
    setDraggedBoardInstanceId(undefined);
    const activeId = String(event.active.id);
    const overId = event.over ? String(event.over.id) : '';
    if (!activeId.startsWith('source-instance:') || !overId.startsWith('source-role:')) return;
    const instanceId = activeId.slice('source-instance:'.length);
    const role = overId.slice('source-role:'.length) as SourceCandidateInstance['sourceRole'];
    runSourceBoardCommand({ type: 'BIND_INSTANCE', role, instanceId });
  };

  if (mode === 'RUN_CONFIGURATION' && demoSourceBoard) {
    if (loading || !snapshot) return <section className="source-center source-center-demo-configuration"><Spin tip="正在读取运行来源" /></section>;
    const configureBoardCandidate = (candidate: SourceCandidateInstance) => {
      if (demoSourceBoard.locked) return;
      const connection = candidate.connectionId ? snapshot.connections.find((item) => item.connectionId === candidate.connectionId) : undefined;
      if (connection) beginEdit(connection);
    };
    const beginFamilyConfiguration = (familyId: string) => {
      if (demoSourceBoard.locked || !canManage) return;
      const family = connectorFamilyCatalog.find((item) => item.familyId === familyId);
      if (!family) return;
      if (family.adapterTypeIds.length === 1) { beginCreate(family.adapterTypeIds[0]!); return; }
      setAdapterPickerFamilyId(familyId);
    };
    const connectorPanel = <div className="connector-catalog-grid source-board-connector-grid">{connectorFamilyCatalog.map((family) => <button
      type="button"
      className="connector-catalog-card"
      key={family.familyId}
      draggable={false}
      disabled={!canManage || demoSourceBoard.locked}
      onClick={() => beginFamilyConfiguration(family.familyId)}
    ><span className="connector-catalog-title"><DatabaseOutlined /><strong>{family.displayName}</strong></span><small>支持 {family.adapterTypeIds.map((id) => connectorTypeById(id)?.displayName ?? id).join('／')}</small><small>{family.capabilitySummary}</small><span className="source-connector-action">创建实例</span></button>)}</div>;
    const instancePanel = <div className="source-board-candidates">
      {displayedBoardCandidates.length ? <div className="source-instance-grid">{displayedBoardCandidates.map((candidate) => <SourceBoardInstanceCard
        key={candidate.instanceId}
        candidate={candidate}
        onCommand={runSourceBoardCommand}
        onConfigure={configureBoardCandidate}
      />)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用实例" />}
    </div>;
    const candidates = <section className="source-board-candidates-panel" aria-label="可用实例">
      <Tabs activeKey={sourceBoardTab} onChange={(key) => setSourceBoardTab(key as 'CONNECTORS' | 'INSTANCES')} items={[
        { key: 'INSTANCES', label: '实例', children: instancePanel },
        { key: 'CONNECTORS', label: '连接器', children: connectorPanel },
      ]} />
    </section>;
    const selected = <section className="source-board-selected-panel" aria-label="已选来源">
      <div className="source-board-section-heading"><strong>已选来源</strong></div>
      <div className="source-board-slot-list">{demoSourceBoard.slots.map((slot) => <SourceBoardSlotCard
        key={slot.role}
        slot={slot}
        canAcceptDrop={!draggedBoardInstanceId || (() => {
          const candidate = displayedBoardCandidates.find((item) => item.instanceId === draggedBoardInstanceId);
          return Boolean(candidate && candidate.sourceRole === slot.role && !candidate.incompatibilityReason && candidate.availability === 'CONNECTED');
        })()}
        onCommand={runSourceBoardCommand}
      />)}</div>
    </section>;
    return <section className="source-center source-center-demo-configuration source-center-source-board" data-testid="guanyijia-source-board">
      <header className="source-center-header">
        <BackAction destination="标准化工作区" onBack={onClose} />
        <div><Typography.Title level={4}>运行来源</Typography.Title><Typography.Text type="secondary">选择本次运行的资料实例；开始后选择将锁定。</Typography.Text></div>
      </header>
      <div className="source-center-content source-board-content">
        <DndContext sensors={sensors} onDragStart={(event) => setDraggedBoardInstanceId(String(event.active.id).replace('source-instance:', ''))} onDragCancel={() => setDraggedBoardInstanceId(undefined)} onDragEnd={handleSourceBoardDragEnd}>
          {sourceBoardCompact
            ? <div className="source-board-mobile-tabs"><Tabs activeKey={sourceBoardMobilePane} onChange={(key) => setSourceBoardMobilePane(key as 'SELECTED' | 'CANDIDATES')} items={[
            { key: 'SELECTED', label: '已选来源', children: selected },
            { key: 'CANDIDATES', label: '待选来源', children: candidates },
            ]} /></div>
            : <div className="source-board-layout"><>{selected}</><>{candidates}</></div>}
        </DndContext>
      </div>
      <Modal title={adapterPickerFamilyId ? `选择 ${connectorFamilyCatalog.find((item) => item.familyId === adapterPickerFamilyId)?.displayName ?? '连接器'}技术` : '选择技术'} open={Boolean(adapterPickerFamilyId)} footer={null} onCancel={() => setAdapterPickerFamilyId(undefined)}>
        <div className="source-adapter-picker">{connectorFamilyCatalog.find((item) => item.familyId === adapterPickerFamilyId)?.adapterTypeIds.map((adapterTypeId) => <Button key={adapterTypeId} block onClick={() => { setAdapterPickerFamilyId(undefined); beginCreate(adapterTypeId); }}>{connectorTypeById(adapterTypeId)?.displayName ?? adapterTypeId}</Button>)}</div>
      </Modal>
      {editor && connectorType && <aside className="source-editor-panel"><div className="source-detail-heading"><div><Typography.Title level={4}>{editor.connectionId ? '重新配置实例' : `创建 ${connectorType.displayName} 实例`}</Typography.Title><Typography.Text type="secondary">先填写业务名称和脱敏连接信息；不会预填演示资料。</Typography.Text></div><Button type="text" aria-label="关闭实例配置" onClick={() => setEditor(null)}>关闭</Button></div><label>业务实例名称<Input placeholder="例如：ERP 交易库" value={editor.displayName} onChange={(event) => setEditor({ ...editor, displayName: event.target.value })} /></label><label>环境<Input placeholder="例如：生产" value={editor.environment} onChange={(event) => setEditor({ ...editor, environment: event.target.value })} /></label>{connectorType.configSchema.sections.map((section) => <section key={section.id}><h3>{section.title}</h3>{section.fields.map((field) => <label key={field.key}>{field.label}<ConfigField field={field} editor={editor} group="config" onChange={(value) => updateField('config', field, value)} />{field.help && <small>{field.help}</small>}</label>)}</section>)}{connectorType.scopeSchema.sections.map((section) => <section key={section.id}><h3>读取范围 · {section.title}</h3>{section.fields.map((field) => <label key={field.key}>{field.label}<ConfigField field={field} editor={editor} group="scope" onChange={(value) => updateField('scope', field, value)} /></label>)}</section>)}<Button type="primary" block loading={busy} disabled={!canManage} icon={<CheckCircleOutlined />} onClick={() => void saveEditor()}>保存实例配置</Button></aside>}
    </section>;
  }

  if (mode === 'SNAPSHOT_OVERVIEW' && demoSourceConfiguration) return <section className="source-center source-center-snapshot-overview source-center-demo-configuration">
    <header className="source-center-header">
      <BackAction destination="标准化工作区" onBack={onClose} />
        <div><Typography.Title level={4}>运行来源</Typography.Title><Typography.Text type="secondary">查看本次运行使用的资料。</Typography.Text></div>
    </header>
    <div className="source-center-content source-snapshot-overview-content">
      <div className={`source-connections-view ${sourceCenterMobileClass(selectedConnectionId)}`}>
        <div className="source-connection-layout">
          <aside className="source-connection-list" aria-label="运行来源">
            {demoSourceConfiguration.map((source) => {
              const status = demoSourceStatuses.find((candidate) => candidate.sourceId === source.sourceId)?.status;
              return <button type="button" key={source.sourceId} className={selectedConnectionId === source.sourceId ? 'active' : ''} onClick={() => navigateToConnection(source.sourceId)}>
                <span><strong>{sourceNameForDemo(source.sourceId)}</strong><small>{source.sourceType} · {demoOriginLabel(source.origin)}</small></span>
                <Tag color={status === 'CONFLICT_BLOCKED' ? 'orange' : status === 'REVIEWED' || status === 'ALIGNED' ? 'green' : status === 'DOCUMENT_READY' ? 'blue' : undefined}>{demoSourceStatusLabel(status)}</Tag>
              </button>;
            })}
          </aside>
          <main className={`source-connection-detail ${selectedDemoSource ? 'has-selection' : ''}`}>
            {selectedDemoSource && <BackAction className="source-mobile-back" destination="来源列表" onBack={navigateBackToList} />}
            {selectedDemoSource ? <>
              <div className="source-detail-heading"><div><Typography.Title level={4}>{sourceNameForDemo(selectedDemoSource.sourceId)}</Typography.Title><Typography.Text type="secondary">{selectedDemoSource.sourceType} · {demoOriginLabel(selectedDemoSource.origin)}</Typography.Text></div></div>
              <section><h3>读取位置</h3><Typography.Paragraph>{selectedDemoSource.location}</Typography.Paragraph></section>
              <section><h3>读取范围</h3><Typography.Paragraph>{selectedDemoSource.scope}</Typography.Paragraph></section>
              <section><h3>当前运行状态</h3><Typography.Paragraph>{demoSourceStatusLabel(demoSourceStatuses.find((candidate) => candidate.sourceId === selectedDemoSource.sourceId)?.status)}</Typography.Paragraph></section>
              <section><h3>凭据</h3><Typography.Paragraph>{selectedDemoSource.credentialLabel}</Typography.Paragraph></section>
            </> : <Empty description="选择一个来源查看固定资料配置" />}
          </main>
        </div>
      </div>
    </div>
  </section>;
  if (loading || !snapshot) return <div className="source-center"><Spin tip="正在读取来源中心" /></div>;
  if (mode === 'SNAPSHOT_OVERVIEW') return <section className="source-center source-center-snapshot-overview">
    <header className="source-center-header">
      <BackAction destination="标准化工作区" onBack={onClose} />
      <div><Typography.Title level={4}>来源快照</Typography.Title><Typography.Text type="secondary">查看本次演示使用的资料范围。</Typography.Text></div>
    </header>
    <div className="source-center-content source-snapshot-overview-content">
      <div className="source-connection-layout">
        <aside className="source-connection-list" aria-label="固定来源列表">
          {snapshot.connections.map((connection) => <button type="button" key={connection.connectionId} className={selectedConnectionId === connection.connectionId ? 'active' : ''} onClick={() => navigateToConnection(connection.connectionId)}>
            <span><strong>{connection.displayName}</strong><small>{connectorTypeById(connection.connectorTypeId)?.displayName} · 固定快照</small></span>
            <Tag color={connection.state === 'READY' ? 'green' : undefined}>{connection.state === 'READY' ? '已准备' : '资料缺口'}</Tag>
          </button>)}
          {showGuanyijiaSemanticExample && <button type="button" className={semanticExampleSelected ? 'active' : ''} onClick={() => navigateToConnection(semanticExampleConnectionId)}><span><strong>企业术语与本体图</strong><small>由已确认资料派生</small></span><Tag color="blue">派生</Tag></button>}
        </aside>
        <main className={`source-connection-detail ${selectedConnection || semanticExampleSelected ? 'has-selection' : ''}`}>
          {(selectedConnection || semanticExampleSelected) && <BackAction className="source-mobile-back" destination="来源列表" onBack={navigateBackToList} />}
          {selectedConnection ? <>
            <div className="source-detail-heading"><div><Typography.Title level={4}>{selectedConnection.displayName}</Typography.Title><Typography.Text type="secondary">固定快照范围与审计记录</Typography.Text></div></div>
            <section><h3>资料范围</h3><Typography.Paragraph>{selectedSnapshots[0]?.summary ?? '当前来源没有可展示的快照摘要。'}</Typography.Paragraph></section>
            <section><h3>审计记录</h3>{auditHistory.items.length ? auditHistory.items.map((item) => <div className="source-history-line" key={item.auditId}><span>{item.detail}</span><small>{item.createdAt}</small></div>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无审计记录" />}</section>
          </> : semanticExampleSelected ? <section>
            <h3>企业术语与本体图</h3>
            <Typography.Paragraph>该来源由已确认的制度资料确定性派生，用于辅助查看已确认关系，不单独增加证据数量。</Typography.Paragraph>
          </section>
            : <Empty description="选择一个来源查看资料" />}
        </main>
      </div>
    </div>
  </section>;
  return <section className="source-center">
    <header className="source-center-header"><BackAction destination="标准化工作区" onBack={onClose} /><div><Typography.Title level={4}>来源设置</Typography.Title><Typography.Text type="secondary">配置共享来源并生成不可变证据快照</Typography.Text></div>{canManage && <Dropdown trigger={['click']} menu={{ items: [
      { key: 'import', icon: <CloudUploadOutlined />, label: '导入 YAML' },
      { key: 'export', icon: <CloudDownloadOutlined />, label: '导出 YAML' },
    ], onClick: ({ key }) => key === 'import' ? setView('YAML') : void downloadYaml() }}><Button type="text" icon={<MoreOutlined />} aria-label="更多来源操作">更多</Button></Dropdown>}</header>
    {view !== 'YAML'
      ? <Segmented value={view} onChange={(value) => setView(value as CenterView)} options={[{ label: '已配置来源', value: 'CONNECTIONS' }, { label: '添加来源', value: 'CATALOG' }]} />
      : <div className="source-center-subflow-bar"><BackAction destination="已配置来源" onBack={() => setView('CONNECTIONS')} /><strong>导入 YAML</strong></div>}
    <div className="source-center-content">
      {view === 'CATALOG' && <div className="connector-catalog-grid">{snapshot.connectorTypes.map((item) => <button type="button" className="connector-catalog-card" key={item.connectorTypeId} disabled={!canManage} onClick={() => beginCreate(item.connectorTypeId)}><span className="connector-catalog-title"><DatabaseOutlined /> <strong>{item.displayName}</strong><Tag color={item.maturity === 'GA' ? 'green' : 'gold'}>{item.maturity}</Tag></span><span>{item.vendor}</span><small>{sourceFamilyLabel(item.family)} · {item.capabilities.length} 项证据能力</small></button>)}</div>}
      {view === 'CONNECTIONS' && <div className={`source-connections-view ${sourceCenterMobileClass(selectedConnectionId)}`}><section className="source-r2-progress">
        <div><strong>当前证据批次 · 第 {snapshot.defaultBatch.revision} 版</strong><small>{snapshot.defaultBatch.snapshotIds.length} 份固定资料</small></div>
        <div className="source-r2-counts"><span>连接 {snapshot.connections.length}</span><span>最新快照 {batchSnapshots.length}</span><Tag color="green">已冻结</Tag></div>
        <Space wrap>{onBatchReady && <Button type={batchAlreadyAttached ? 'default' : 'primary'} disabled={batchAlreadyAttached} onClick={() => onBatchReady(snapshot.defaultBatch, snapshot.snapshots.filter((item) => snapshot.defaultBatch.snapshotIds.includes(item.snapshotId)))}>{batchAlreadyAttached ? '已加入当前草稿' : '加入当前草稿'}</Button>}{canManage && <Button loading={busy} disabled={batchSnapshotIds.length === 0} onClick={() => void freezeBatch()}>冻结最新快照为 R{snapshot.defaultBatch.revision + 1}</Button>}</Space>
      </section><div className="source-connection-layout">
        <aside className="source-connection-list">{snapshot.connections.map((connection) => <button type="button" key={connection.connectionId} className={selectedConnectionId === connection.connectionId ? 'active' : ''} onClick={() => navigateToConnection(connection.connectionId)}><span><strong>{connection.displayName}</strong><small>{connectorTypeById(connection.connectorTypeId)?.displayName} · {connection.environment}</small></span><Tag color={connection.state === 'READY' ? 'green' : connection.state === 'ERROR' ? 'red' : undefined}>{connection.state === 'READY' ? '已就绪' : connection.state === 'DRAFT' ? '待配置' : connection.state === 'DISABLED' ? '已停用' : '异常'}</Tag></button>)}{showGuanyijiaSemanticExample && <button type="button" className={semanticExampleSelected ? 'active' : ''} onClick={() => navigateToConnection(semanticExampleConnectionId)}><span><strong>企业术语与本体图</strong><small>Semantica RDF/SPARQL · 内置编译示例</small></span><Tag color="blue">临时示例</Tag></button>}</aside>
        <main className={`source-connection-detail ${selectedConnection || semanticExampleSelected ? 'has-selection' : ''}`}>{(selectedConnection || semanticExampleSelected) && <BackAction className="source-mobile-back" destination="来源列表" onBack={navigateBackToList} />}{selectedConnection ? <>
          <div className="source-detail-heading"><div><Typography.Title level={4}>{selectedConnection.displayName}</Typography.Title><Typography.Text type="secondary">{connectorTypeById(selectedConnection.connectorTypeId)?.displayName} · {selectedConnection.environment}</Typography.Text></div>{canManage && <Button onClick={() => beginEdit(selectedConnection)}>编辑配置</Button>}</div>
          <section><h3>配置文档</h3><dl>{Object.entries(selectedRevisions[0]?.sanitizedConfig ?? {}).map(([key, value]) => <div key={key}><dt>{configLabels[key] ?? key}</dt><dd><ReadableTechnicalValue value={value} /></dd></div>)}{selectedRevisions[0]?.credentialRef && <div><dt>凭据</dt><dd>已配置</dd></div>}</dl></section>
          <section><h3>读取范围</h3><dl>{Object.entries(selectedRevisions[0]?.defaultScope ?? {}).map(([key, value]) => <div key={key}><dt>{scopeLabels[key] ?? key}</dt><dd><ReadableTechnicalValue value={value} /></dd></div>)}</dl></section>
          {selectedConnection.connectorTypeId === 'semantica' && selectedRevisions[0]?.sanitizedConfig.mode === 'RDF_GRAPH'
            && <SemanticCollectionPanel projectId={snapshot.defaultBatch.projectId} />}
          <Space wrap>{selectedRevision?.stage === 'DRAFT' && <Button type="primary" loading={busy} disabled={!canManage} icon={<PlayCircleOutlined />} onClick={() => void runOnRevision('TEST_CONNECTION')}>测试连接</Button>}{selectedRevision?.stage === 'TESTED' && <Button type="primary" loading={busy} disabled={!canCapture} onClick={() => void runOnRevision('DISCOVER_SCOPE')}>发现读取范围</Button>}{selectedRevision?.stage === 'DISCOVERED' && <Button type="primary" loading={busy} disabled={!canManage} onClick={() => void runOnRevision('ACTIVATE_CONNECTION')}>确认范围并激活</Button>}{selectedRevision?.stage === 'ACTIVE' && <Button type="primary" loading={busy} disabled={!canCapture} onClick={() => void runOnRevision('CAPTURE_SNAPSHOT')}>读取并生成快照</Button>}{canManage && <Dropdown trigger={['click']} menu={{ items: [{ key: 'disable', label: '停用连接', danger: true, disabled: selectedConnection.state === 'DISABLED' }], onClick: () => Modal.confirm({ title: `停用“${selectedConnection.displayName}”？`, content: '历史配置版本和已冻结快照仍会保留。', okText: '停用', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => disableConnection() }) }}><Button type="text" icon={<MoreOutlined />} aria-label={`更多操作：${selectedConnection.displayName}`} /></Dropdown>}</Space>
          {(discovery || selectedRevision?.discovery) && <Alert type="info" showIcon message="发现的对象与建议范围" description={discovery ?? `${selectedRevision?.discovery?.summary} · ${Object.entries(selectedRevision?.discovery?.objectCounts ?? {}).map(([key, value]) => `${key} ${value}`).join('，')}`} />}
          {readProgress.length > 0 && <section className="source-read-progress" aria-live="polite">{readProgress.map((line, index) => <div key={`${line}:${index}`}>{line}</div>)}</section>}
          {pendingSnapshot && <section className="source-snapshot-choice"><div><strong>证据快照已就绪</strong><small>快照不可变。可以立即用于当前草稿，也可以只保存在来源中心。</small></div><Space wrap><Button onClick={() => setPendingSnapshot(undefined)}>只保存来源</Button>{onSnapshotReady && <Button type="primary" onClick={() => { onSnapshotReady(pendingSnapshot); setPendingSnapshot(undefined); }}>加入当前建模批次</Button>}</Space></section>}
          <section><h3>使用位置</h3><Typography.Paragraph type="secondary">{selectedSnapshots.some((item) => attachedSnapshotIds.includes(item.snapshotId))
            ? '当前个人草稿正在引用此来源的不可变快照。'
            : selectedSnapshots.some((item) => snapshot.defaultBatch.snapshotIds.includes(item.snapshotId))
              ? '当前默认建模批次正在引用此来源的固定资料。'
              : '当前没有建模批次引用此来源；生成快照后可以加入个人草稿。'}</Typography.Paragraph></section>
          <Collapse items={[
            { key: 'revisions', label: `配置版本（${selectedRevisions.length}）`, children: <>{revisionHistory.items.map((item) => <div className="source-history-line" key={item.revision}><span>版本 {item.revision} · {item.createdAt}</span><Tag color={item.revision === selectedConnection.activeRevision ? 'green' : undefined}>{item.revision === selectedConnection.activeRevision ? '当前激活' : item.lastTest?.status ?? '未测试'}</Tag></div>)}<SourceHistoryPagination {...revisionHistory} onChange={(page) => setHistoryPages((current) => ({ ...current, revisions: page }))} /></> },
            { key: 'snapshots', label: `资料历史（${selectedSnapshots.length}）`, children: selectedSnapshots.length ? <>{snapshotHistory.items.map((item, index) => <div className="source-history-line source-snapshot-history" key={item.snapshotId}><div className="source-history-copy"><strong>第 {selectedSnapshots.length - index} 版固定资料</strong><small>{item.summary}</small><ReadableTechnicalValue label="资料位置" value={item.manifestRef} /></div><Space><Tag color="green">已固定</Tag>{onSnapshotReady && <Button size="small" disabled={attachedSnapshotIds.includes(item.snapshotId)} onClick={() => onSnapshotReady(item)}>{attachedSnapshotIds.includes(item.snapshotId) ? '已加入' : '加入草稿'}</Button>}</Space></div>)}<SourceHistoryPagination {...snapshotHistory} onChange={(page) => setHistoryPages((current) => ({ ...current, snapshots: page }))} /></> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无资料" /> },
            { key: 'audit', label: `审计记录（${selectedAudit.length}）`, children: <>{auditHistory.items.map((item) => <div className="source-history-line" key={item.auditId}><span>{item.detail}</span><small>{item.createdAt}</small></div>)}<SourceHistoryPagination {...auditHistory} onChange={(page) => setHistoryPages((current) => ({ ...current, audit: page }))} /></> },
          ]} />
        </> : semanticExampleSelected ? <SemanticCollectionPanel projectId={snapshot.defaultBatch.projectId} />
          : <Empty description="选择一个已配置来源查看配置文档" />}</main>
      </div></div>}
      {view === 'YAML' && <div className="source-yaml-view"><Alert type="info" showIcon message="导入来源配置" description="页面中的中文说明由同一结构生成。密码、Token和私钥不会导入、导出或保存在浏览器中。" /><label className="ant-btn"><CloudUploadOutlined /> 选择 YAML 文件<input hidden type="file" accept=".yaml,.yml" onChange={(event) => { const file = event.target.files?.[0]; if (file) void file.text().then(setYaml); }} /></label><Input.TextArea value={yaml} onChange={(event) => { setYaml(event.target.value); setYamlPreview(undefined); }} rows={18} placeholder="粘贴或导入来源配置 YAML" /><Space><Button onClick={() => void previewYaml()}>预览变化</Button><Button type="primary" disabled={!yamlPreview || Boolean(yamlPreview.errors.length || yamlPreview.conflicts.length)} onClick={() => void applyYaml()}>确认导入</Button></Space>{yamlPreview && <div className="yaml-import-preview"><Tag color="green">新增 {yamlPreview.created.length}</Tag><Tag color="blue">更新 {yamlPreview.updated.length}</Tag><Tag>跳过 {yamlPreview.unchanged.length}</Tag><Tag color="orange">冲突 {yamlPreview.conflicts.length}</Tag><Tag color="red">错误 {yamlPreview.errors.length}</Tag></div>}</div>}
      {editor && connectorType && <aside className="source-editor-panel"><div className="source-detail-heading"><div><Typography.Title level={4}>{editor.connectionId ? '新建配置版本' : `配置 ${connectorType.vendor}`}</Typography.Title><Typography.Text type="secondary">仅保存脱敏连接设置；密码和令牌不会保存在浏览器中。</Typography.Text></div><Button type="text" onClick={() => setEditor(null)}>关闭</Button></div><label>连接名称<Input value={editor.displayName} onChange={(event) => setEditor({ ...editor, displayName: event.target.value })} /></label><label>环境<Input value={editor.environment} onChange={(event) => setEditor({ ...editor, environment: event.target.value })} /></label>{connectorType.configSchema.sections.map((section) => <section key={section.id}><h3>{section.title}</h3>{section.fields.map((field) => <label key={field.key}>{field.label}<ConfigField field={field} editor={editor} group="config" onChange={(value) => updateField('config', field, value)} />{field.help && <small>{field.help}</small>}</label>)}</section>)}{connectorType.scopeSchema.sections.map((section) => <section key={section.id}><h3>读取范围 · {section.title}</h3>{section.fields.map((field) => <label key={field.key}>{field.label}<ConfigField field={field} editor={editor} group="scope" onChange={(value) => updateField('scope', field, value)} /></label>)}</section>)}<Button type="primary" block loading={busy} disabled={!canManage} icon={<CheckCircleOutlined />} onClick={() => void saveEditor()}>保存配置版本</Button></aside>}
    </div>
  </section>;
}
