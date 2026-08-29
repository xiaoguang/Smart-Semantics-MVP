import assert from 'node:assert/strict';
import test from 'node:test';
import {
  locateSourceCheckpoint,
  projectBusinessJourneyTimeline,
  projectVisibleBusinessJourneyStages,
  projectStandardizationExperience,
  type SourceLifecycleEvent,
  type StandardizationSource,
} from './standardization-experience-projection.ts';

const sources: StandardizationSource[] = [
  { sourceId: 'guanyijia_mysql', displayName: '数据库' },
  { sourceId: 'guanyijia_github', displayName: 'GitHub代码仓库' },
  { sourceId: 'guanyijia_official_docs', displayName: '业务说明' },
  { sourceId: 'guanyijia_policy', displayName: 'ERP管理制度' },
  { sourceId: 'guanyijia_semantica', displayName: '企业术语图' },
];

test('每个来源都有一个稳定检查点，包括尚未读取的来源', () => {
  const projection = projectStandardizationExperience({ sources, timeline: [] });
  const sourceCheckpoints = projection.checkpoints.filter((checkpoint) => checkpoint.kind === 'SOURCE');

  assert.deepEqual(sourceCheckpoints.map((checkpoint) => checkpoint.checkpointId), sources.map((source) => `source:${source.sourceId}`));
  assert.deepEqual(sourceCheckpoints.map((checkpoint) => checkpoint.status), [
    'PENDING',
    'PENDING',
    'PENDING',
    'PENDING',
    'PENDING',
  ]);
});

test('同一来源的读取、生成和审阅事件折叠为一个来源检查点', () => {
  const timeline: SourceLifecycleEvent[] = [
    { eventId: 'mysql-read-started', sourceId: 'guanyijia_mysql', type: 'SOURCE_READ_STARTED' },
    { eventId: 'mysql-read-completed', sourceId: 'guanyijia_mysql', type: 'SOURCE_READ_COMPLETED' },
    { eventId: 'mysql-document-generated', sourceId: 'guanyijia_mysql', type: 'DOCUMENT_GENERATED' },
    { eventId: 'mysql-document-reviewed', sourceId: 'guanyijia_mysql', type: 'DOCUMENT_REVIEWED' },
    { eventId: 'github-read-started', sourceId: 'guanyijia_github', type: 'SOURCE_READ_STARTED' },
  ];

  const projection = projectStandardizationExperience({ sources, timeline });
  const sourceCheckpoints = projection.checkpoints.filter((checkpoint) => checkpoint.kind === 'SOURCE');
  const databaseCheckpoint = sourceCheckpoints.find((checkpoint) => checkpoint.sourceId === 'guanyijia_mysql');

  assert.equal(sourceCheckpoints.length, sources.length);
  assert.deepEqual(databaseCheckpoint, {
    kind: 'SOURCE',
    checkpointId: 'source:guanyijia_mysql',
    sourceId: 'guanyijia_mysql',
    status: 'REVIEWED',
    eventIds: [
      'mysql-read-started',
      'mysql-read-completed',
      'mysql-document-generated',
      'mysql-document-reviewed',
    ],
  });
  assert.equal(sourceCheckpoints.find((checkpoint) => checkpoint.sourceId === 'guanyijia_github')?.status, 'READING');
});

test('来源导航只定位右侧流程检查点，不打开来源文档', () => {
  const projection = projectStandardizationExperience({ sources, timeline: [] });
  const navigation = locateSourceCheckpoint({
    sourceId: 'guanyijia_mysql',
    checkpoints: projection.checkpoints,
  });

  assert.deepEqual(navigation, {
    effect: 'LOCATE_WORKFLOW_CHECKPOINT',
    checkpointId: 'source:guanyijia_mysql',
    sourceId: 'guanyijia_mysql',
    opensDocument: false,
  });
});

test('业务流程把差异嵌入其来源的审阅阶段，而不在流程末尾另建差异队列', () => {
  const journey = projectBusinessJourneyTimeline({
    sources: sources.map((source, index) => ({
      ...source,
      status: index === 0 ? 'DOCUMENT_READY' as const : 'PENDING' as const,
      ...(index === 0 ? { introducedConflictIds: ['debt'], resolvedConflictIds: [] } : {}),
    })),
    timeline: [
      {
        itemId: 'read', eventId: 'read', kind: 'SOURCE_READ_STARTED', state: 'RECEIPT',
        sourceId: 'guanyijia_mysql', title: '载入数据库快照', summary: '读取中',
      },
      {
        itemId: 'document', eventId: 'document', kind: 'DOCUMENT_GENERATED', state: 'CURRENT',
        sourceId: 'guanyijia_mysql', title: '数据库来源文档已生成', summary: '待审阅',
        action: { type: 'OPEN_DOCUMENT', documentId: 'mysql-document' },
      },
      {
        itemId: 'finding', eventId: 'finding', kind: 'CONFLICT_FOUND', state: 'RECEIPT',
        sourceId: 'guanyijia_github', title: '欠款字段', summary: '待决定',
        conflicts: [{ conflictId: 'debt', title: '欠款字段', affectedObjects: [] }],
        action: { type: 'OPEN_CONFLICT', conflictId: 'debt' },
      },
    ],
  });

  assert.deepEqual(journey.map((item) => item.checkpointId), [
    'source:guanyijia_mysql',
    'source:guanyijia_github',
    'source:guanyijia_official_docs',
    'source:guanyijia_policy',
    'source:guanyijia_semantica',
    'result:standardization',
  ]);
  assert.equal(journey.find((item) => item.checkpointId === 'source:guanyijia_mysql')?.action
    && (journey.find((item) => item.checkpointId === 'source:guanyijia_mysql')!.action as { type: string }).type, 'OPEN_DOCUMENT');
  assert.equal(journey.find((item) => item.checkpointId === 'source:guanyijia_github')?.action, undefined);
  const mysql = journey.find((item) => item.checkpointId === 'source:guanyijia_mysql');
  assert.deepEqual(mysql?.stages?.map((stage) => [stage.stage, stage.state]), [
    ['READ', 'COMPLETE'], ['ANALYZE', 'COMPLETE'], ['ORGANIZE', 'COMPLETE'], ['REVIEW', 'ACTIVE'],
  ]);
  assert.deepEqual(mysql?.stages?.find((stage) => stage.stage === 'REVIEW')?.conflicts, [{
    conflictId: 'debt', title: '欠款字段', state: 'ACTIVE', affectedObjects: [],
    action: { type: 'OPEN_CONFLICT', conflictId: 'debt' },
  }]);
});

test('已审阅的来源不会阻止流程定位到下一个待读取来源', () => {
  const journey = projectBusinessJourneyTimeline({
    sources: sources.map((source, index) => ({
      ...source,
      status: index === 0 ? 'REVIEWED' as const : 'PENDING' as const,
    })),
    timeline: [],
  });

  assert.equal(
    journey.find((checkpoint) => checkpoint.state === 'CURRENT')?.checkpointId,
    'source:guanyijia_github',
  );
});

test('真实读取阶段回执覆盖当前来源的默认阶段状态', () => {
  const journey = projectBusinessJourneyTimeline({
    sources: sources.map((source, index) => ({
      ...source,
      status: index === 0 ? 'READING' as const : 'PENDING' as const,
      ...(index === 0 ? {
        preparationStates: {
          READ: 'COMPLETE' as const,
          ANALYZE: 'ACTIVE' as const,
          ORGANIZE: 'PENDING' as const,
        },
      } : {}),
    })),
    timeline: [],
  });

  assert.deepEqual(
    journey.find((checkpoint) => checkpoint.checkpointId === 'source:guanyijia_mysql')?.stages
      ?.map((stage) => [stage.stage, stage.state]),
    [
      ['READ', 'COMPLETE'],
      ['ANALYZE', 'ACTIVE'],
      ['ORGANIZE', 'PENDING'],
      ['REVIEW', 'PENDING'],
    ],
  );
});

test('右侧来源过程只显示截至真实当前阶段的累计前缀', () => {
  const reading = projectBusinessJourneyTimeline({
    sources: sources.map((source, index) => ({
      ...source,
      status: index === 0 ? 'READING' as const : 'PENDING' as const,
      ...(index === 0 ? { preparationStates: { READ: 'COMPLETE' as const, ANALYZE: 'ACTIVE' as const } } : {}),
    })),
    timeline: [],
  }).find((checkpoint) => checkpoint.checkpointId === 'source:guanyijia_mysql');
  const pending = projectBusinessJourneyTimeline({
    sources: sources.map((source) => ({ ...source, status: 'PENDING' as const })),
    timeline: [],
  }).find((checkpoint) => checkpoint.checkpointId === 'source:guanyijia_mysql');
  const complete = projectBusinessJourneyTimeline({
    sources: sources.map((source, index) => ({ ...source, status: index === 0 ? 'ALIGNED' as const : 'PENDING' as const })),
    timeline: [],
  }).find((checkpoint) => checkpoint.checkpointId === 'source:guanyijia_mysql');

  assert.deepEqual(
    projectVisibleBusinessJourneyStages(reading?.stages).map((stage) => [stage.stage, stage.state]),
    [['READ', 'COMPLETE'], ['ANALYZE', 'ACTIVE']],
  );
  assert.deepEqual(projectVisibleBusinessJourneyStages(pending?.stages), []);
  assert.deepEqual(
    projectVisibleBusinessJourneyStages(complete?.stages).map((stage) => [stage.stage, stage.state]),
    [['READ', 'COMPLETE'], ['ANALYZE', 'COMPLETE'], ['ORGANIZE', 'COMPLETE'], ['REVIEW', 'COMPLETE']],
  );
});
