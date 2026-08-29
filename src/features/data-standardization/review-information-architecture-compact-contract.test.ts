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
    /const sourceReviewMatters = sourceReviewVisibility\?\.matterProjection\.items \?\? \[\][\s\S]*?documentView === 'MATTERS'[\s\S]*?aria-label="待确认事项"[\s\S]*?<CrossSourceFindingList/u,
    '全部跨来源比较必须由运行级正式事项投影并在审阅事项中保持可见',
  );
  assert.doesNotMatch(workbenchSource, /crossSourceConclusionFindings/u,
    '审阅结论只能展示核心业务结论和对象目录，不能再承载跨来源比较');
});

test('当前第一项正式差异在审阅事项中自动展开，并成为来源页的唯一主操作', () => {
  assert.match(workbenchSource, /const currentActionableMatter = sourceReviewMatters\.find/u,
    '当前正式差异必须从运行级事项投影中选出，而不是从时间线反推');
  assert.match(workbenchSource, /autoExpandFindingId=\{currentActionableMatter\?\.stableId\}/u,
    '正式差异成立时必须自动展开其双方资料');
  assert.match(workbenchSource, /currentConflictId: currentActionableMatter\?\.conflictId/u,
    '来源页主操作必须指向当前第一项正式差异');
});

test('建议编辑直接显示本地修改效果，并在 250ms 后校验权威预览身份', () => {
  assert.match(workbenchSource, /setTimeout\(\(\) => \{[\s\S]*?250\)/u,
    '输入后应以有限防抖请求权威 Preview SHA 校验');
  assert.match(workbenchSource, /正在校验/u,
    '编辑器需要明确显示校验中的实时修改效果');
  assert.doesNotMatch(workbenchSource, />预览修改</u,
    '用户不应再额外点击“预览修改”才看见效果');
});

test('真实来源准备失败后保留读取中的来源，并提供唯一的阶段重试操作', () => {
  assert.match(workbenchSource, /sourcePreparationFailure/u);
  assert.match(workbenchSource, /runtime\.readReviewShell\(currentUser\.userId\)/u,
    '失败后必须先恢复持久化 READING 来源，不能继续沿用旧 revision');
  assert.match(workbenchSource, />重试当前阶段</u);
  assert.match(workbenchSource, /sourcePreparationFailure && nextAction\?\.type === 'NONE'/u,
    '重试只能在没有其他工作流 Primary 时出现');
});

test('待确认事项使用稳定 class 恢复宽屏三列卡片，而不是中文 aria-label 选择器', () => {
  assert.match(workbenchSource, /guanyijia-local-suggestions/u);
  assert.match(workbenchCss, /\.guanyijia-local-suggestions > article/u);
  assert.doesNotMatch(workbenchCss, /\[aria-label=['"]待核对任务['"]\]/u);
});

test('本来源建议与来源差异与比较使用两个同级 Box，不能再共享待确认事项的黄色外框', () => {
  assert.match(workbenchSource, /guanyijia-local-suggestions/u,
    '本来源建议需要稳定的独立容器');
  assert.match(workbenchSource, /guanyijia-cross-source-matters/u,
    '来源差异与比较需要稳定的独立容器');
  assert.match(workbenchCss, /\.guanyijia-local-suggestions/u);
  assert.match(workbenchCss, /\.guanyijia-cross-source-matters/u);
  assert.doesNotMatch(workbenchCss, /\.guanyijia-pending-matters\s*>\s*article/u,
    '三列卡片只属于各自 Box，父区域不得再直接承载两类不同事项');
});

test('当前来源第九章的逐项补充资料会进入审阅事项，而不是只停留在标准化文档阅读版', () => {
  assert.match(workbenchSource, /projectReviewSupplementMatters/u,
    '工作台必须使用结构化补充资料投影');
  assert.match(workbenchSource, /data-review-supplement=/u,
    '每个逐项补充资料必须有稳定的审阅事项身份');
});

test('五源审阅完成后，三个页签必须绑定同一份完整合并预览再允许生成', () => {
  assert.match(workbenchSource, /deliverables\.preview\(/u,
    'READY_FOR_OUTPUT 应先读取只读合并预览，不能直接写入交付物');
  assert.match(workbenchSource, /data-merged-preview-sha=/u,
    '三页签必须公开同一份预览身份，不能各自拼接内容');
  assert.match(workbenchSource, /Preview SHA256/u);
  assert.match(workbenchSource, /expectedPreviewSha256: currentPreview!\.previewSha256/u,
    '生成命令必须绑定用户刚刚核对过的预览 SHA');
  assert.match(workbenchSource, /deliverablePreviewTab === 'MATTERS'/u);
  assert.match(workbenchSource, /deliverablePreviewTab === 'CONCLUSIONS'/u);
  assert.match(workbenchSource, /deliverablePreviewTab === 'DOCUMENT'/u);
  assert.match(workbenchSource, /mergedDocument\.markdown/u,
    '最终预览必须使用同一份完整九章 Markdown，而不是逐章简化投影');
  assert.doesNotMatch(workbenchSource, /<SourceDocumentReadable content=\{source\.markdown\}/u);
});

test('最终三个页签均显示同一份五来源、九章节完整正文，并由一次确认完成生成和定版', () => {
  assert.match(workbenchSource, /<MergedPreviewChapters preview=\{deliverablePreviewForRun\} presentation="MATTERS"/u);
  assert.match(workbenchSource, /<MergedPreviewChapters preview=\{deliverablePreviewForRun\} presentation="CONCLUSIONS"/u);
  assert.match(workbenchSource, /<MergedPreviewChapters preview=\{deliverablePreviewForRun\} presentation="DOCUMENT"/u);
  assert.match(workbenchSource, /confirmAndFreezeDeliverable/u,
    '预览页必须将生成和作者定版串成一个受控操作');
  assert.match(workbenchSource, />确认并定版</u);
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
