import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  projectSourceReviewDocument,
  projectSourceReviewWorkspace,
  readSourceReviewDocument,
  type ReviewChecklistItem,
  type SourceReviewDocument,
} from '../guanyijia-evidence-factory/source-review-document.ts';

const mysqlInput = {
  sourceId: 'guanyijia_mysql',
  snapshotId: '20260813T032528Z-abb0502c7d79',
} as const;

const workbenchSource = readFileSync(
  new URL('./guanyijia-standardization-workbench.tsx', import.meta.url),
  'utf8',
);
const workbenchCss = readFileSync(
  new URL('./data-standardization.css', import.meta.url),
  'utf8',
);

type CompactReviewChecklist = {
  tasks: ReviewChecklistItem[];
  gaps: ReviewChecklistItem[];
  completedTasks: ReviewChecklistItem[];
  completedFormalDecisions?: Array<{ decisionId: string; title: string }>;
};

function readMysqlReview(): SourceReviewDocument {
  const document = readSourceReviewDocument(mysqlInput);
  assert.ok(document, '固定 MySQL 来源文档应可读取');
  return document;
}

function projectMysqlReview(input?: {
  sourceDocument?: SourceReviewDocument;
  scriptedDecisions?: Array<{
    definition: { editId: string };
    status: 'PENDING' | 'KEPT' | 'APPLIED';
  }>;
  formalDecisions?: Array<{
    decisionId: string;
    sourceId: string;
    title: string;
    statement: string;
  }>;
}) {
  return projectSourceReviewWorkspace({
    sourceDocument: input?.sourceDocument ?? readMysqlReview(),
    currentBlocks: [],
    scriptedDecisions: input?.scriptedDecisions ?? [],
    formalDecisions: input?.formalDecisions ?? [],
  });
}

test('来源审阅提供审阅事项、审阅结论和标准化文档三个职责明确的页签', () => {
  const document = readMysqlReview();
  const workspace = projectMysqlReview({ sourceDocument: document });
  const documentProjection = projectSourceReviewDocument(document);
  const expectedTabs = ['审阅事项', '审阅结论', '标准化文档'];

  assert.deepEqual(workspace.tabs, expectedTabs,
    '工作台投影必须把用户操作、全部结论和交付文档分开');
  assert.deepEqual(documentProjection.tabs, expectedTabs,
    '来源文档投影必须与工作台使用同一套三个页签');
});

test('来源审阅正文不再显示没有操作价值的本次范围与限制折叠区', () => {
  assert.equal(
    workbenchSource.includes('本次范围与限制'),
    false,
    '来源审阅正文不应继续渲染“本次范围与限制”折叠区',
  );
});

test('审阅事项分别保留待处理、资料缺口和已审阅内容', () => {
  const pending = projectMysqlReview();
  const pendingChecklist = pending.checklist as unknown as CompactReviewChecklist;
  assert.deepEqual(
    pendingChecklist.tasks.map((item) => item.claimId),
    ['claim:guanyijia_mysql:curated-e005'],
    '未决定的剧本项应留在待处理分组',
  );

  const gapDocument = structuredClone(readMysqlReview());
  const accountEvidence = gapDocument.evidence.find((evidence) => (
    evidence.evidenceRef === 'mysql:table:jsh_account'
  ));
  assert.ok(accountEvidence);
  accountEvidence.evidenceClass = 'GAP';
  const gapChecklist = projectMysqlReview({
    sourceDocument: gapDocument,
  }).checklist as unknown as CompactReviewChecklist;
  assert.deepEqual(
    gapChecklist.gaps.map((item) => item.claimId),
    ['claim:guanyijia_mysql:curated-e001'],
    '已识别资料缺口应在审阅事项中单独列出，而不是隐藏或阻塞读取下一来源',
  );

  const completedChecklist = projectMysqlReview({
    scriptedDecisions: [{
      definition: { editId: 'scripted:mysql:rename-depot-head' },
      status: 'KEPT',
    }],
  }).checklist as unknown as CompactReviewChecklist;
  assert.deepEqual(completedChecklist.tasks, [], '已保存的核对项不应继续显示为待处理');
  assert.ok(
    Array.isArray(completedChecklist.completedTasks),
    '清单投影必须提供已审阅分组',
  );
  assert.deepEqual(
    completedChecklist.completedTasks.map((item) => item.claimId),
    ['claim:guanyijia_mysql:curated-e005'],
    '完成审阅后必须保留用户刚刚确认过的内容，不能从页面消失',
  );

  const formalChecklist = projectMysqlReview({
    formalDecisions: [{
      decisionId: 'resolution:debt-fields',
    sourceId: 'guanyijia_github',
      title: '欠款字段结构冲突',
      statement: '已登记为资料缺口，等待进一步确认。',
    }],
  }).checklist as unknown as CompactReviewChecklist;
  assert.deepEqual(
    formalChecklist.completedFormalDecisions?.map((decision) => decision.decisionId),
    ['resolution:debt-fields'],
    '已保存的跨来源正式决定也必须作为已处理内容保留在审阅事项中',
  );
});

test('所有跨来源比较都留在审阅事项，审阅结论不再承担冲突或资料不足', () => {
  assert.match(
    workbenchSource,
    /const crossSourceMatters = sourceReviewVisibility\?\.comparisonFindings \?\? \[\][\s\S]*?documentView === 'MATTERS'[\s\S]*?aria-label="待确认事项"[\s\S]*?<CrossSourceFindingList/u,
    '全部跨来源比较必须在审阅事项的待确认事项分组中保持可见',
  );
  assert.doesNotMatch(workbenchSource, /crossSourceConclusionFindings/u,
    '审阅结论只能展示核心业务结论和对象目录，不能再承载跨来源比较');
});

test('待确认事项使用稳定 class 恢复宽屏三列卡片，而不是中文 aria-label 选择器', () => {
  assert.match(workbenchSource, /guanyijia-pending-matters/u);
  assert.match(workbenchCss, /\.guanyijia-pending-matters > article/u);
  assert.doesNotMatch(workbenchCss, /\[aria-label=['"]待核对任务['"]\]/u);
});

test('核心结论与对象目录互不重复，且对象目录默认展开', () => {
  const workspace = projectMysqlReview();
  const coreClaimIds = new Set(workspace.checklist.keyConclusions.map((item) => item.claimId));
  const directoryClaimIds = new Set(workspace.checklist.objectDetails.map((item) => item.claimId));

  assert.deepEqual(
    [...coreClaimIds].filter((claimId) => directoryClaimIds.has(claimId)),
    [],
    '核心结论解释值得关注的判断；对象目录提供完整可追踪清单，两者不能重复同一条结论',
  );
  const objectDirectoryTag = workbenchSource.match(
    /<details[^>]*aria-label="对象明细"[^>]*>/u,
  )?.[0];
  assert.ok(objectDirectoryTag, '对象目录应由明确的 details 区域承载');
  assert.match(
    objectDirectoryTag,
    /\bopen(?:\s|=|>)/u,
    '对象目录承载完整识别范围，应默认展开，而不是把主要内容藏在折叠区',
  );
});
