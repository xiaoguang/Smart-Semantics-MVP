import assert from 'node:assert/strict';
import test from 'node:test';

test('固定运行角色只显示读取状态；实例连接状态与可运行快照独立', async () => {
  const sourceBoard = await import('./source-board.ts').catch(() => undefined);
  assert.ok(sourceBoard, '缺少来源编排投影模块');

  const model = sourceBoard.projectSourceBoard({
    storyKey: 'guanyijia-five-source-v1',
    locked: false,
    sourceStatuses: [{ sourceId: 'mysql', status: 'PENDING' }],
    candidates: [
      { instanceId: 'mysql-main', sourceId: 'mysql', sourceRole: 'DATABASE', displayName: '数据库快照', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'mysql-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'github-main', sourceId: 'github', sourceRole: 'CODE_REPOSITORY', displayName: '代码仓库快照', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'github-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'official-main', sourceId: 'official', sourceRole: 'OFFICIAL_DOCUMENTS', displayName: '业务文档', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'official-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'policy-main', sourceId: 'policy', sourceRole: 'ERP_POLICY', displayName: 'ERP 制度', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'policy-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'terminology-main', sourceId: 'terminology', sourceRole: 'TERMINOLOGY', displayName: '术语图', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'terminology-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'official-draft', sourceId: 'official-draft', sourceRole: 'OFFICIAL_DOCUMENTS', displayName: '待配置业务文档连接', availability: 'NEEDS_CONFIGURATION', compatibleStoryKeys: ['guanyijia-five-source-v1'] },
    ],
    bindings: {
      DATABASE: 'mysql-main',
      CODE_REPOSITORY: 'github-main',
      OFFICIAL_DOCUMENTS: 'official-main',
      ERP_POLICY: 'policy-main',
      TERMINOLOGY: 'terminology-main',
    },
  });

  assert.deepEqual(model.slots.map((slot: { role: string }) => slot.role), [
    'DATABASE', 'CODE_REPOSITORY', 'OFFICIAL_DOCUMENTS', 'ERP_POLICY', 'TERMINOLOGY',
  ]);
  assert.deepEqual(model.slots[0].statusTags.map((tag: { kind: string; label: string }) => [tag.kind, tag.label]), [
    ['READ_STATE', '待读取'],
  ], '已选且正常的实例不在左栏重复展示“已连接”');
  assert.equal(model.slots[1].readState, 'PENDING', '已选且尚未开始的来源默认显示为待读取');
  assert.equal(model.slots[0].canRemove, true);
  assert.equal(sourceBoard.validateSourceBoardStart(model).ok, true);

  const unbound = sourceBoard.executeSourceBoardCommand({
    model,
    command: { type: 'UNBIND_INSTANCE', role: 'DATABASE' },
  });
  assert.equal(unbound.ok, true);
  assert.equal(unbound.model.slots[0].availability, 'DISCONNECTED');
  assert.equal(sourceBoard.validateSourceBoardStart(unbound.model).ok, false);
  assert.match(sourceBoard.validateSourceBoardStart(unbound.model).reason ?? '', /数据库/u);

  const draftBound = sourceBoard.executeSourceBoardCommand({
    model: unbound.model,
    command: { type: 'BIND_INSTANCE', role: 'OFFICIAL_DOCUMENTS', instanceId: 'official-draft' },
  });
  assert.equal(draftBound.ok, false, '待配置实例可以继续配置，但不能加入运行来源');
  assert.match(draftBound.reason ?? '', /尚未连接/u);

  const locked = sourceBoard.projectSourceBoard({ ...model, locked: true });
  const lockedResult = sourceBoard.executeSourceBoardCommand({
    model: locked,
    command: { type: 'UNBIND_INSTANCE', role: 'DATABASE' },
  });
  assert.equal(lockedResult.ok, false);
  assert.match(lockedResult.reason ?? '', /已经开始/u);

  const unavailable = sourceBoard.projectSourceBoard({
    ...model,
    locked: true,
    lockReason: 'SOURCE_MANAGEMENT_UNAVAILABLE',
  });
  const unavailableResult = sourceBoard.executeSourceBoardCommand({
    model: unavailable,
    command: { type: 'UNBIND_INSTANCE', role: 'DATABASE' },
  });
  assert.equal(unavailableResult.ok, false);
  assert.match(unavailableResult.reason ?? '', /暂时无法读取/u);
  assert.equal(sourceBoard.validateSourceBoardStart(unavailable).ok, false);
  assert.match(sourceBoard.validateSourceBoardStart(unavailable).reason ?? '', /暂时无法读取/u);

  const runUnavailable = sourceBoard.projectSourceBoard({
    ...model,
    locked: true,
    lockReason: 'RUN_STATE_UNAVAILABLE',
  });
  const runUnavailableResult = sourceBoard.executeSourceBoardCommand({
    model: runUnavailable,
    command: { type: 'UNBIND_INSTANCE', role: 'DATABASE' },
  });
  assert.equal(runUnavailableResult.ok, false);
  assert.match(runUnavailableResult.reason ?? '', /标准化运行暂时无法读取/u);
  const runUnavailableBind = sourceBoard.executeSourceBoardCommand({
    model: runUnavailable,
    command: { type: 'BIND_INSTANCE', role: 'DATABASE', instanceId: 'mysql-main' },
  });
  assert.equal(runUnavailableBind.ok, false, '运行状态无法读取时不得绕过锁定替换来源');
  assert.deepEqual(
    sourceBoard.sourceBoardBindings(runUnavailableBind.model),
    sourceBoard.sourceBoardBindings(runUnavailable),
    '读取失败不应改变任何已保存的来源绑定',
  );
  assert.equal(sourceBoard.validateSourceBoardStart(runUnavailable).ok, false);
  assert.match(sourceBoard.validateSourceBoardStart(runUnavailable).reason ?? '', /标准化运行暂时无法读取/u);

  const snapshotMissing = sourceBoard.projectSourceBoard({
    ...model,
    bindings: { ...sourceBoard.sourceBoardBindings(model), DATABASE: 'mysql-without-snapshot' },
    candidates: [...model.candidates, {
      instanceId: 'mysql-without-snapshot', sourceId: 'mysql-extra', sourceRole: 'DATABASE', displayName: 'ERP 备用交易库',
      businessName: 'ERP 备用交易库', adapterTypeId: 'postgresql', adapterLabel: 'PostgreSQL',
      availability: 'CONNECTED', snapshotReadiness: 'MISSING', compatibleStoryKeys: ['guanyijia-five-source-v1'],
    }],
  });
  assert.equal(snapshotMissing.slots[0].availability, 'CONNECTED');
  assert.equal(sourceBoard.validateSourceBoardStart(snapshotMissing).ok, false);
  assert.match(sourceBoard.validateSourceBoardStart(snapshotMissing).reason ?? '', /缺少可用快照/u);
});

test('来源编排拒绝跨角色或没有冻结资料的启动，并保留候选不可用原因', async () => {
  const sourceBoard = await import('./source-board.ts').catch(() => undefined);
  assert.ok(sourceBoard, '缺少来源编排投影模块');

  const model = sourceBoard.projectSourceBoard({
    storyKey: 'guanyijia-five-source-v1',
    locked: false,
    candidates: [
      { instanceId: 'mysql-main', sourceId: 'mysql', sourceRole: 'DATABASE', displayName: '数据库快照', availability: 'CONNECTED', compatibleStoryKeys: ['guanyijia-five-source-v1'], formalSnapshotId: 'mysql-r1', contentSnapshotId: 'content-r1' },
      { instanceId: 'github-foreign', sourceId: 'github', sourceRole: 'CODE_REPOSITORY', displayName: '其他故事的代码库', availability: 'CONNECTED', compatibleStoryKeys: ['other-story'], formalSnapshotId: 'github-r1', contentSnapshotId: 'content-r1' },
    ],
    bindings: { DATABASE: 'mysql-main' },
  });

  const invalidRole = sourceBoard.executeSourceBoardCommand({
    model,
    command: { type: 'BIND_INSTANCE', role: 'DATABASE', instanceId: 'github-foreign' },
  });
  assert.equal(invalidRole.ok, false);
  assert.match(invalidRole.reason ?? '', /不兼容/u);
  assert.equal(model.candidates.find((item: { instanceId: string }) => item.instanceId === 'github-foreign')?.incompatibilityReason, '不适用于当前标准化流程');
  assert.equal(sourceBoard.validateSourceBoardStart(model).ok, false);
  assert.match(sourceBoard.validateSourceBoardStart(model).reason ?? '', /代码仓库/u);
});
