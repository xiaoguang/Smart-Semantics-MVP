import React, { useEffect, useMemo, useState } from 'react';
import {
  Avatar, Badge, Button, Dropdown, Form, Input, Layout, Menu, Modal, Segmented,
  Select, Space, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  ApartmentOutlined, BarChartOutlined, ClockCircleOutlined, DoubleLeftOutlined,
  DoubleRightOutlined, FileSyncOutlined, LogoutOutlined, MoreOutlined, NodeIndexOutlined, RobotOutlined, SwapOutlined,
  TagOutlined, TeamOutlined, ThunderboltOutlined, WarningOutlined,
} from '@ant-design/icons';
import TimeSemanticPage from '../pages/time/TimeSemanticPage';
import AliasPage from '../pages/alias/AliasPage';
import SemanticModelingPage from '../pages/modeling/SemanticModelingPage';
import MetricConfig2Page from '../pages/metric2/MetricConfig2Page';
import BusinessRulePage from '../pages/metric2/BusinessRulePage';
import AiModelingPage from '../features/ai-modeling/ai-modeling-page';
import DataStandardizationPage from '../features/data-standardization/data-standardization-page.tsx';
import type { ModelingDocumentArtifact } from '../features/modeling-document-bridge/types.ts';
import type { CompiledModelingDocument } from '../features/modeling-document-bridge/compile-modeling-document.ts';
import type {
  StandardizationDocumentHandoffReceipt,
  ZeroDeltaModelingHandoffReceipt,
} from '../features/standardization-deliverable/types.ts';
import type { StandardizationModelingEligibilityProjection } from '../features/standardization-deliverable/modeling-eligibility.ts';
import AiWorkspaceErrorBoundary from '../features/ai-modeling/ai-workspace-error-boundary';
import { useLinguanWorkspace } from '../features/ai-modeling/workspace-context';
import type { WorkspaceSystemCode, WorkspaceVersion } from '../features/ai-modeling/types';
import { useCurrentUser } from '../features/ai-modeling/current-user-context';
import { projectWorkspaceVersionOptions } from '../features/ai-modeling/active-workspace-view';
import { transitionPublishedNavigationHint, type PublishedNavigationHint } from '../features/ai-modeling/publish-navigation';
import { useCollaboration } from '../features/collaboration/collaboration-context';
import CollaborationTaskDrawer from '../features/collaboration/task-drawer';
import type { CollaborationTask } from '../features/collaboration/types.ts';
import { accessibleModelProjects } from '../features/model-projects/domain-registry';
import { beginDemoRecovery } from '../features/demo-session/demo-recovery.ts';
import { presentBusinessError } from '../features/data-standardization/business-copy-quality.ts';
import '../features/collaboration/collaboration.css';

const { Sider, Content, Header } = Layout;
type PageKey = 'standardization' | 'ai' | 'modeling' | 'metric2' | 'businessRule' | 'alias' | 'time';
type PageDefinition = { title: string; description: string; icon: React.ReactNode; content: React.ReactNode };
const roleLabel = { VIEWER: '查看者', EDITOR: '编辑者', REVIEWER: '审核者', PUBLISHER: '发布者', ADMIN: '管理员' } as const;

const pages: Record<PageKey, PageDefinition> = {
  standardization: { title: '数据标准化', description: '整理多来源资料，解决口径差异，生成可审阅的建模文档', icon: <FileSyncOutlined />, content: null },
  ai: { title: 'AI 建模', description: '从业务资料形成个人草稿并发起协作', icon: <RobotOutlined />, content: null },
  modeling: { title: '本体建模', description: '定义实体、事件、属性关系及物理字段映射', icon: <NodeIndexOutlined />, content: <SemanticModelingPage /> },
  metric2: { title: '指标配置', description: '维护指标口径与计算公式', icon: <BarChartOutlined />, content: <MetricConfig2Page /> },
  businessRule: { title: '业务规则', description: '维护目标值并编排对象判定、过滤条件与组合规则', icon: <ThunderboltOutlined />, content: <BusinessRulePage /> },
  alias: { title: '同义词', description: '管理业务词汇映射并处理未识别表达', icon: <TagOutlined />, content: <AliasPage /> },
  time: { title: '时间语义', description: '配置业务日历、时间规则与节假日周期', icon: <ClockCircleOutlined />, content: <TimeSemanticPage /> },
};

export default function AppLayout() {
  const { currentUser, logout, switchUser } = useCurrentUser();
  const collaboration = useCollaboration();
  // The demo starts at the evidence and review story. AI modeling remains the
  // explicit handoff destination after the standardization deliverable is
  // frozen, rather than the default landing page.
  const [current, setCurrent] = useState<PageKey>('standardization');
  const [collapsed, setCollapsed] = useState(false);
  const [publishedHint, setPublishedHint] = useState<PublishedNavigationHint>(null);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [switchOpen, setSwitchOpen] = useState(false);
  const [submitOpen, setSubmitOpen] = useState(false);
  const [mobileMoreOpen, setMobileMoreOpen] = useState(false);
  const [activeCollaborationTask, setActiveCollaborationTask] = useState<CollaborationTask>();
  const [modelingDocument, setModelingDocument] = useState<ModelingDocumentArtifact>();
  const [modelingDocumentCandidate, setModelingDocumentCandidate] = useState<CompiledModelingDocument>();
  const [zeroDeltaReceipt, setZeroDeltaReceipt] = useState<ZeroDeltaModelingHandoffReceipt>();
  const [standardizationReceipt, setStandardizationReceipt] = useState<StandardizationDocumentHandoffReceipt>();
  const [modelingEligibility, setModelingEligibility] = useState<StandardizationModelingEligibilityProjection>();
  const [pendingModelSpace, setPendingModelSpace] = useState<string>();
  const [switchForm] = Form.useForm();
  const [submitForm] = Form.useForm();
  const {
    activeSystem, activeVersion, snapshot, workspaceReadOnly, personalDraft,
    metric2Data, selectSystem, selectVersion, materializeCurrent, updateMetric2Data,
  } = useLinguanWorkspace();
  const activePage = pages[current];
  const access = collaboration.accessFor(activeSystem);
  const draftIssue = collaboration.draftIssueFor(activeSystem);
  const latestCatalog = collaboration.catalogFor(activeSystem);
  const activeCatalog = collaboration.catalogFor(
    activeSystem,
    collaboration.viewMode === 'FORMAL' && activeVersion !== 'WORKSPACE' ? activeVersion : undefined,
  );
  const role = access.role;
  const projectOptions = useMemo(() => accessibleModelProjects(collaboration.workspaces)
    .filter((project) => project.workspaceId !== 'retail_semantic_modeling')
    .map((project) => ({ value: project.systemCode, label: project.displayName })), [collaboration.workspaces]);
  useEffect(() => {
    setCurrent('standardization'); setTasksOpen(false); setSwitchOpen(false); setSubmitOpen(false);
    setMobileMoreOpen(false); setPendingModelSpace(undefined); setPublishedHint(null); setActiveCollaborationTask(undefined); setModelingDocument(undefined); setZeroDeltaReceipt(undefined); setStandardizationReceipt(undefined); setModelingEligibility(undefined);
  }, [currentUser.userId]);
  useEffect(() => {
    if (!pendingModelSpace || !collaboration.modelSpaceIds.includes(pendingModelSpace)) return;
    selectSystem(pendingModelSpace); setPendingModelSpace(undefined);
  }, [collaboration.modelSpaceIds, pendingModelSpace]);

  const menuItems = useMemo(() => (Object.entries(pages) as [PageKey, PageDefinition][]).map(([key, page]) => ({
    key, icon: page.icon, title: page.title,
    label: key === 'modeling' && publishedHint?.systemCode === activeSystem && publishedHint?.catalogVersion === activeVersion
      ? <Badge dot color="var(--dh-success)" offset={[8, 0]}>{page.title}</Badge> : page.title,
  })), [activeSystem, activeVersion, publishedHint]);

  const openModeling = () => {
    setMobileMoreOpen(false);
    setPublishedHint((hint) => transitionPublishedNavigationHint(hint, { type: 'ENTER_MODELING', systemCode: activeSystem, catalogVersion: activeVersion }));
    setCurrent('modeling');
  };
  const navigateTo = (page: PageKey) => {
    setMobileMoreOpen(false);
    if (page === 'modeling') openModeling();
    else setCurrent(page);
  };
  const pageContent = current === 'standardization' ? <DataStandardizationPage onHandoff={(handoff) => {
    setModelingDocument(handoff.artifact);
    setZeroDeltaReceipt(handoff.kind === 'ZERO_DELTA_MODELING_HANDOFF' ? handoff.receipt : undefined);
    setStandardizationReceipt(handoff.kind === 'STANDARDIZATION_DOCUMENT_HANDOFF' ? handoff.receipt : undefined);
    setModelingEligibility(handoff.kind === 'STANDARDIZATION_DOCUMENT_HANDOFF' ? handoff.eligibility : undefined);
    if (handoff.kind === 'ZERO_DELTA_MODELING_HANDOFF') {
      collaboration.setViewMode('FORMAL'); selectVersion('V1');
    }
    setModelingDocumentCandidate(undefined); setCurrent('ai'); setActiveCollaborationTask(undefined);
  }} /> : current === 'ai' && collaboration.viewMode === 'DRAFT' && draftIssue
    ? <section className="ai-workspace-error draft-integrity-error" role="alert">
      <WarningOutlined aria-hidden="true" />
      <div><strong>草稿内容暂时无法显示</strong><p>当前草稿未能通过完整性校验，暂时无法继续编辑。</p></div>
      <Space wrap>
        <Button type="primary" onClick={() => {
          collaboration.setViewMode('FORMAL');
          selectVersion((latestCatalog?.catalogVersion as WorkspaceVersion) ?? 'WORKSPACE');
        }}>打开正式模型</Button>
        {role === 'ADMIN' && <Button danger onClick={() => Modal.confirm({
          title: '恢复演示？',
          content: '将清理本次数据标准化运行、审阅和交付记录，并恢复来源配置与协作演示起点。当前登录和固定来源快照会保留，管伊佳正式 V1 不会改变。',
          okButtonProps: { danger: true }, okText: '确认恢复',
          onOk: () => recoverDemo(),
        })}>恢复演示</Button>}
      </Space>
    </section>
    : current === 'ai' ? <AiWorkspaceErrorBoundary
    resetKey={`${currentUser.userId}:${activeSystem}:${activeVersion}:${collaboration.viewMode}:${personalDraft?.revision ?? 0}:${activeCollaborationTask?.taskId ?? ''}`}
    onReturnFormal={() => {
      collaboration.setViewMode('FORMAL');
      selectVersion((latestCatalog?.catalogVersion as WorkspaceVersion) ?? 'WORKSPACE');
      setActiveCollaborationTask(undefined);
    }}
  ><AiModelingPage
      onPublished={(hint) => setPublishedHint((value) => transitionPublishedNavigationHint(value, { type: 'PUBLISHED', ...hint }))}
      showOntologyPrompt={publishedHint?.systemCode === activeSystem && publishedHint?.catalogVersion === activeVersion}
      onOpenOntology={openModeling}
      collaborationTask={activeCollaborationTask}
      modelingDocument={modelingDocument}
      modelingDocumentCandidate={modelingDocumentCandidate}
      zeroDeltaReceipt={zeroDeltaReceipt}
      standardizationReceipt={standardizationReceipt}
      modelingEligibility={modelingEligibility}
      onModelingDocumentChange={(artifact) => { setModelingDocument(artifact); setZeroDeltaReceipt(undefined); setStandardizationReceipt(undefined); setModelingEligibility(undefined); setModelingDocumentCandidate(undefined); }}
      onModelingDocumentCandidateChange={setModelingDocumentCandidate}
      onReturnStandardization={() => setCurrent('standardization')}
      onCollaborationPublished={(catalogVersion) => {
        collaboration.setViewMode('FORMAL'); selectVersion(catalogVersion as WorkspaceVersion);
        setActiveCollaborationTask(undefined);
      }}
    /></AiWorkspaceErrorBoundary> : current === 'metric2' ? <MetricConfig2Page data={metric2Data} onChange={updateMetric2Data} readOnly={workspaceReadOnly} />
    : current === 'businessRule' ? <BusinessRulePage data={metric2Data} onChange={updateMetric2Data} readOnly={workspaceReadOnly} /> : activePage.content;

  const rawVersionOptions = snapshot ? projectWorkspaceVersionOptions(snapshot) : [{ value: 'WORKSPACE' as const, label: '当前工作区' }];
  const versionOptions = [...rawVersionOptions];
  collaboration.catalogsFor(activeSystem).forEach((catalog) => {
    if (!versionOptions.some((item) => item.value === catalog.catalogVersion)) versionOptions.push({ value: catalog.catalogVersion as WorkspaceVersion, label: `${catalog.catalogVersion} · 正式发布` });
  });
  const status = collaboration.viewMode === 'DRAFT'
    ? draftIssue ? { status: 'error' as const, text: '草稿需要恢复' }
      : personalDraft ? { status: 'processing' as const, text: `我的草稿 · ${personalDraft.changes.length} 项变更` } : { status: 'default' as const, text: '尚未创建草稿' }
    : activeCatalog ? { status: 'success' as const, text: `已发布 ${activeCatalog.catalogVersion}` } : { status: 'default' as const, text: '尚无正式模型' };

  const enterDraft = () => {
    if (draftIssue) { collaboration.setViewMode('DRAFT'); selectVersion('WORKSPACE'); return; }
    if (personalDraft) { collaboration.setViewMode('DRAFT'); selectVersion('WORKSPACE'); return; }
    if (!access.canCreateDraft) { message.info('当前角色只能查看或处理协作任务，不能创建个人草稿'); return; }
    try { collaboration.openDraft(activeSystem, materializeCurrent()); selectVersion('WORKSPACE'); message.success('个人草稿已创建，仅自己可见'); }
    catch (error) { message.error(presentBusinessError(error, '创建草稿失败，请重新打开后重试。')); }
  };
  const enterFormal = () => {
    collaboration.setViewMode('FORMAL');
    selectVersion((latestCatalog?.catalogVersion as WorkspaceVersion) ?? 'WORKSPACE');
  };
  const submitDraft = (values: { summary: string }) => {
    if (!personalDraft) return;
    try {
      collaboration.submitDraft(personalDraft, values.summary); setSubmitOpen(false); submitForm.resetFields();
      message.success('草稿已提交，审核者会在待办中看到');
    } catch (error) { message.error(presentBusinessError(error, '提交失败，请重新打开后重试。')); }
  };
  const changeUser = (values: { username: string; password: string }) => {
    try { switchUser(values.username, values.password); setSwitchOpen(false); switchForm.resetFields(); }
    catch (error) { message.error(presentBusinessError(error, '切换失败，请重新选择模型项目。')); }
  };
  const recoverDemo = () => {
    try {
      beginDemoRecovery({ localStorage, actorUserId: currentUser.userId });
      window.location.reload();
    } catch (error) {
      message.error(presentBusinessError(error, '无法开始恢复演示，请刷新页面后重试。'));
    }
  };
  const stopFormalMutation = (event: React.MouseEvent<HTMLElement>) => {
    if (!workspaceReadOnly) return;
    const button = (event.target as HTMLElement).closest('button');
    if (!button) return;
    const label = `${button.textContent ?? ''} ${button.getAttribute('aria-label') ?? ''} ${button.getAttribute('title') ?? ''}`;
    if (!/新建|新增|编辑|删除|维护|配置|保存|启用|停用|复制|创建|添加/.test(label)) return;
    event.preventDefault(); event.stopPropagation();
    message.info('正式模型不可修改，请进入“我的草稿”操作');
  };
  const changeModelProject = (systemCode: WorkspaceSystemCode) => {
    try {
      setActiveCollaborationTask(undefined); setModelingDocument(undefined);
      setModelingDocumentCandidate(undefined); setZeroDeltaReceipt(undefined); setStandardizationReceipt(undefined); setModelingEligibility(undefined);
      selectSystem(systemCode);
    }
    catch (error) { message.error(presentBusinessError(error, '模型项目切换失败，请重新选择。')); }
  };

  return <Layout className="app-shell">
    <Sider className="app-sider" width={248} collapsedWidth={72} collapsed={collapsed} trigger={null} theme="light">
      <div className="brand"><div className="brand-mark"><ApartmentOutlined /></div>{!collapsed && <div className="brand-copy"><span className="brand-name">灵光</span><span className="brand-product">零售语义建模</span></div>}{!collapsed && <Tag className="brand-version">V2</Tag>}</div>
      <div className="nav-section-label">{collapsed ? '•••' : '语义知识'}</div>
      <Menu className="app-menu" mode="inline" inlineCollapsed={collapsed} selectedKeys={[current]}
        onClick={({ key }) => key === 'modeling' ? openModeling() : setCurrent(key as PageKey)} items={menuItems} />
      <div className="sider-footer"><Tooltip title={collapsed ? '展开导航' : '收起导航'} placement="right"><Button type="text" className="collapse-button" icon={collapsed ? <DoubleRightOutlined /> : <DoubleLeftOutlined />} onClick={() => setCollapsed((value) => !value)}>{!collapsed && '收起导航'}</Button></Tooltip></div>
    </Sider>
    <Layout className="workspace-shell">
      <Header className="app-header">
        <div className="page-heading"><Typography.Title level={4}>{activePage.title}</Typography.Title><Typography.Text type="secondary">{activePage.description}</Typography.Text></div>
        <div className="workspace-meta">
          <div className="workspace-context-group" aria-label="当前模型上下文">
            <Select<WorkspaceSystemCode> className="collaboration-header-select" aria-label="模型项目" value={activeSystem} onChange={changeModelProject}
              options={projectOptions} popupMatchSelectWidth={280} />
            {current !== 'standardization' && <Select<WorkspaceVersion> className="version-selector" aria-label="正式模型版本" value={activeVersion} onChange={selectVersion} options={versionOptions} />}
          </div>
          {current !== 'standardization' && <div className="workspace-mode-group">
            <Segmented className="collaboration-view-switch" value={collaboration.viewMode}
              onChange={(value) => value === 'DRAFT' ? enterDraft() : enterFormal()}
              options={[{ value: 'FORMAL', label: '正式模型' }, { value: 'DRAFT', label: '我的草稿' }]} />
            <Badge status={status.status} text={status.text} />
          </div>}
          {current !== 'standardization' && <Badge count={collaboration.tasks.length} size="small"><Button icon={<TeamOutlined />} onClick={() => setTasksOpen(true)}>待我处理</Button></Badge>}
          <Dropdown trigger={['click']} menu={{ items: [
            { key: 'identity', disabled: true, label: <div><strong>{currentUser.displayName}</strong><div>{collaboration.workspaces.find((item) => item.workspaceId === collaboration.activeWorkspaceId)?.displayName} · {roleLabel[role]}</div></div> },
            { type: 'divider' }, { key: 'switch', icon: <SwapOutlined />, label: '切换用户' },
            ...(role === 'ADMIN' ? [{ key: 'reset', danger: true, label: '恢复演示' } as const] : []),
            { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
          ], onClick: ({ key }) => {
            if (key === 'switch') setSwitchOpen(true);
            if (key === 'logout') logout();
            if (key === 'reset') Modal.confirm({ title: '恢复演示？', content: '将清理本次数据标准化运行、审阅和交付记录，并恢复来源配置与协作演示起点。当前登录和固定来源快照会保留，管伊佳正式 V1 不会改变。', okButtonProps: { danger: true }, okText: '确认恢复', onOk: recoverDemo });
          } }}><Avatar className="user-avatar" size={34}>{currentUser.initials}</Avatar></Dropdown>
        </div>
      </Header>
      <Content className={`app-content${current === 'ai' || current === 'standardization' ? ' app-content-ai' : ''}`}>
        {current !== 'standardization' && collaboration.viewMode === 'DRAFT' && personalDraft && <div className="collaboration-draft-bar">
          <div className="collaboration-draft-meta"><Tag color="blue">仅自己可见</Tag><strong>{personalDraft.title}</strong><span>基于 {personalDraft.baseCatalogVersion} · 第 {personalDraft.revision} 次修改</span></div>
          <Space><Button onClick={() => { setActiveCollaborationTask(undefined); setCurrent('ai'); }}>预览草稿</Button><Button type="primary" onClick={() => { submitForm.setFieldsValue({ summary: personalDraft.title }); setSubmitOpen(true); }}>提交审核</Button></Space>
        </div>}
        {current !== 'standardization' && collaboration.viewMode === 'FORMAL' && !activeCatalog && <div className="collaboration-viewer-note">当前空间尚无正式模型。编辑者可进入“我的草稿”开始工作。</div>}
        <section onClickCapture={current === 'standardization' ? undefined : stopFormalMutation} className={`app-page app-page-${current}${current !== 'ai' && current !== 'standardization' && workspaceReadOnly ? ' workspace-readonly' : ''}`} key={`${current}-${activeSystem}-${activeVersion}-${collaboration.viewMode}`}>
          {current !== 'ai' && current !== 'standardization' && workspaceReadOnly && <div className="workspace-readonly-notice">正式模型只读 · 修改请进入“我的草稿”</div>}
          {pageContent}
        </section>
      </Content>
    </Layout>
    <nav className="mobile-bottom-nav" aria-label="手机主导航">
      {([
        ['standardization', '数据标准化', <FileSyncOutlined key="standardization-icon" />],
        ['ai', 'AI建模', <RobotOutlined key="ai-icon" />],
        ['modeling', '本体', <NodeIndexOutlined key="modeling-icon" />],
        ['metric2', '指标', <BarChartOutlined key="metric-icon" />],
      ] as const).map(([key, label, icon]) => <button
        type="button"
        key={key}
        className={`mobile-bottom-nav-item${current === key ? ' is-active' : ''}`}
        aria-label={label}
        aria-current={current === key ? 'page' : undefined}
        onClick={() => navigateTo(key)}
      >{icon}<span>{label}</span></button>)}
      <button
        type="button"
        className={`mobile-bottom-nav-item${(['businessRule', 'alias', 'time'] as PageKey[]).includes(current) ? ' is-active' : ''}`}
        aria-label="更多"
        aria-expanded={mobileMoreOpen}
        aria-controls="mobile-more-menu"
        onClick={() => setMobileMoreOpen((value) => !value)}
      ><MoreOutlined /><span>更多</span></button>
      {mobileMoreOpen && <div id="mobile-more-menu" className="mobile-more-menu" role="menu" aria-label="更多功能">
        {([
          ['businessRule', '业务规则', <ThunderboltOutlined key="business-rule-icon" />],
          ['alias', '同义词', <TagOutlined key="alias-icon" />],
          ['time', '时间语义', <ClockCircleOutlined key="time-icon" />],
        ] as const).map(([key, label, icon]) => <button type="button" role="menuitem" key={key} onClick={() => navigateTo(key)}>{icon}<span>{label}</span></button>)}
      </div>}
    </nav>
    <CollaborationTaskDrawer open={tasksOpen} onClose={() => setTasksOpen(false)} onOpenTask={(task) => {
      const targetWorkspace = collaboration.workspaces.find((item) => item.modelProjectIds.includes(task.modelSpaceId));
      if (targetWorkspace) collaboration.selectWorkspace(targetWorkspace.workspaceId);
      setActiveCollaborationTask(task); setPendingModelSpace(task.modelSpaceId); collaboration.setViewMode(task.kind === 'EDIT' ? 'DRAFT' : 'FORMAL'); setTasksOpen(false); setCurrent('ai');
    }} />
    <Modal title="切换用户" open={switchOpen} onCancel={() => setSwitchOpen(false)} footer={null} destroyOnHidden>
      <Form form={switchForm} layout="vertical" onFinish={changeUser}><Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="password" label="密码" rules={[{ required: true }]}><Input.Password /></Form.Item><Button type="primary" block htmlType="submit">验证并切换</Button></Form>
    </Modal>
    <Modal title="提交个人草稿" open={submitOpen} onCancel={() => setSubmitOpen(false)} footer={null} destroyOnHidden>
      <Form form={submitForm} layout="vertical" onFinish={submitDraft}><Typography.Paragraph>提交后当前修订将冻结，等待其他成员审核。</Typography.Paragraph><Form.Item name="summary" label="变更说明" rules={[{ required: true, message: '请输入变更说明' }]}><Input.TextArea rows={3} /></Form.Item><Button type="primary" block htmlType="submit">提交审核</Button></Form>
    </Modal>
  </Layout>;
}
