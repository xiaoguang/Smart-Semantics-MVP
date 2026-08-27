import assert from 'node:assert/strict';
import test from 'node:test';
import {
  projectSourceReviewRevision,
  projectSourceStandardDocumentRevision,
  projectSourceReviewWorkspace,
  readSourceReviewDocument,
  readSourceStandardDocument,
  projectSourceReviewDocument,
  type SourceReviewDocument,
  type SourceStandardDocument,
} from './source-review-document.ts';
import { bindDemoContentRun } from '../guanyijia-demo-content/demo-content-review.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';

const mysqlInput = {
  sourceId: 'guanyijia_mysql',
  snapshotId: '20260813T032528Z-abb0502c7d79',
} as const;

const githubInput = {
  sourceId: 'guanyijia_github',
  snapshotId: '20260813032126Z-5821d0ece9b1',
} as const;

const authoredInputs = [
  { sourceId: 'guanyijia_official_docs', snapshotId: 'gyjerp-official-docs-20260813T031656Z' },
  { sourceId: 'guanyijia_demo_policy', snapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53' },
  { sourceId: 'guanyijia_semantica_demo', snapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778' },
] as const;

const canonicalSectionTitles = [
  '文档说明',
  '业务目标',
  '业务对象',
  '业务活动',
  '字段与维度',
  '对象关系',
  '指标口径',
  '示例问题',
  '待确认事项',
] as const;

function canonicalNineSectionDocument(): SourceReviewDocument {
  // Reuse the GitHub source identity so all nine legacy section labels are
  // distinct. The first synthetic claim also fulfils the existing scripted
  // task binding; the review body itself remains entirely synthetic.
  const sourceId = 'guanyijia_github';
  const markdown = [
    '# 可读标准化文档',
    '',
    '本文件以统一章节组织可用于后续建模的业务内容；每条结论均有独立保存的来源材料。',
    '',
    ...canonicalSectionTitles.flatMap((sectionTitle, index) => [
      `## ${index + 1}. ${sectionTitle}`,
      '',
      `### ${sectionTitle}结论`,
      '',
      `这是第 ${index + 1} 章的可审阅业务说明。`,
      '',
    ]),
  ].join('\n');
  const evidence = canonicalSectionTitles.flatMap((sectionTitle, index) => {
    const references = index === 0 ? ['github:v5:005', 'github:v5:006'] : [`synthetic:e${index + 1}`];
    return references.map((evidenceRef, evidenceIndex) => ({
    evidenceRef,
    evidenceClass: 'DEMO_AUTHORED' as const,
    excerptKind: 'EXACT_EXCERPT' as const,
    title: `${sectionTitle}材料${evidenceIndex + 1}`,
    excerpt: `第 ${index + 1} 章的来源材料${evidenceIndex + 1}。`,
    locationLabel: '文档章节',
    locationValue: `第 ${index + 1} 章`,
    excerptSha256: 'sha256:synthetic' as `sha256:${string}`,
    affectedObjectRefs: [`object:${index + 1}`],
    }));
  });
  const traceLinks = canonicalSectionTitles.map((sectionTitle, index) => ({
    linePrefix: `### ${sectionTitle}结论`,
    markdownAnchor: index === 0 ? 'github-v5-github-v5-003' : `synthetic-section-${index + 1}`,
    evidenceRefs: index === 0 ? ['github:v5:005', 'github:v5:006'] : [`synthetic:e${index + 1}`],
    section: index + 1,
    sectionId: `${sourceId}:section:${index + 1}`,
    sectionPurpose: `说明${sectionTitle}。`,
    claim: {
      title: `${sectionTitle}结论`,
      statement: `这是第 ${index + 1} 章的可审阅业务说明。`,
      focusIdentifiers: [],
    },
  }));
  const items = canonicalSectionTitles.map((sectionTitle, index) => ({
    id: index === 0
      ? 'claim:guanyijia_github:github-v5-github-v5-003'
      : `claim:${sourceId}:synthetic-section-${index + 1}`,
    section: index + 1,
    title: `${sectionTitle}结论`,
    statement: `这是第 ${index + 1} 章的可审阅业务说明。`,
    summary: '这段演示资料说明了相关业务语境。',
    resultType: '演示资料' as const,
    evidenceRefs: index === 0 ? ['github:v5:005', 'github:v5:006'] : [`synthetic:e${index + 1}`],
    markdownAnchors: [index === 0 ? 'github-v5-github-v5-003' : `synthetic-section-${index + 1}`],
    affectedObjectRefs: [`object:${index + 1}`],
    ...(index === 0 ? {
      formalBlockId: 'gyj-block:rule.negative_stock',
      scriptedEditId: 'scripted:github:clarify-negative-stock',
    } : {}),
  }));
  return {
    schemaVersion: 1,
    sourceId,
    snapshotId: '20260813032126Z-5821d0ece9b1',
    title: '可读标准化文档',
    coverageLabel: '9 个章节',
    markdown,
    markdownSha256: 'sha256:synthetic' as `sha256:${string}`,
    evidence,
    traceLinks,
    items,
    contentOrigin: 'DEMO_AUTHORED',
standardSections: canonicalSectionTitles.map((title, index) => ({
  index: index + 1,
  sectionId: `SYNTHETIC_${index + 1}`,
  title,
      purpose: `说明${title}。`,
      markdownAnchor: `synthetic-standard-section-${index + 1}`,
    })),
  };
}

/**
 * V7 stores reader context separately from claim blocks.  Deliberately make
 * the frozen target block use stale typography: a revision must use the
 * stable claim/anchor mapping, rather than searching for an old title string.
 */
function v7StandardDocumentWithStaleClaimBlock(
  document: SourceStandardDocument,
  targetClaimId: string,
): SourceStandardDocument {
  const next = structuredClone(document);
  const contexts = Object.fromEntries(next.standardSections.map((section) => [
    section.sectionId,
    `本章阅读说明：${section.title}中的已保存材料按业务语义组织，结论另以独立段落列出。`,
  ]));
  const markdown = [
    `# ${next.title}`,
    '',
    ...next.standardSections.flatMap((section) => [
      `## ${section.index}. ${section.title}`,
      '',
      contexts[section.sectionId]!,
      '',
      ...next.claims
        .filter((claim) => claim.sectionId === section.sectionId)
        .flatMap((claim) => claim.claimId === targetClaimId
          ? ['### 冻结标题的旧排版', '', '冻结说明的旧排版。', '']
          : [`### ${claim.title}`, '', claim.statement, '']),
    ]),
  ].join('\n').trimEnd().concat('\n');
  return {
    ...next,
    sections: contexts,
    traceLinks: next.claims.map((claim) => ({
      linePrefix: `### ${claim.title}`,
      claimId: claim.claimId,
      markdownAnchor: claim.markdownAnchor,
      evidenceRefs: [...claim.evidenceRefs],
    })),
    markdown,
    markdownSha256: 'sha256:synthetic-v7-reader-shape' as `sha256:${string}`,
  };
}

test('a rich canonical nine-section document retains its full Markdown and canonical chapter coordinate system', () => {
  const sourceDocument = canonicalNineSectionDocument();
  const workspace = projectSourceReviewWorkspace({
    sourceDocument,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  assert.deepEqual(workspace.sections.map((section) => section.title), canonicalSectionTitles);
  assert.match(workspace.markdown.content, /本文件以统一章节组织可用于后续建模/u);
  assert.match(workspace.markdown.content, /### 待确认事项结论/u);
  assert.equal(workspace.claims.length, 9);
  assert.equal(workspace.markdown.sourceContent, workspace.markdown.content);
});

test('only an explicit rich section contract preserves a nine-section Markdown body verbatim', () => {
  const sourceDocument = canonicalNineSectionDocument();
  const legacyLookingDocument: SourceReviewDocument = {
    ...sourceDocument,
    standardSections: undefined,
  };

  const workspace = projectSourceReviewWorkspace({
    sourceDocument: legacyLookingDocument,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  assert.doesNotMatch(workspace.markdown.content, /本文件以统一章节组织可用于后续建模的业务内容/u);
});

test('review workspace makes claims primary and presents account DDL as a verified schema', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  assert.deepEqual(workspace.tabs, ['审阅清单', 'Markdown 文档']);
  assert.match(workspace.metadata.sourceDescription, /数据库结构、相关处理过程和查询条件/u);
  assert.match(workspace.metadata.sourceDescription, /没有实际查询结果/u);
  assert.equal(workspace.claims.some((claim) => claim.title.includes('文档说明')), false);
  assert.equal(workspace.claims.some((claim) => claim.title.includes('业务目标')), false);

  const account = workspace.claims.find((claim) => claim.claimId === 'claim:guanyijia_mysql:curated-e001');
  assert.deepEqual(account?.focusIdentifiers, ['name', 'initial_amount', 'current_amount', 'enabled', 'tenant_id']);
  assert.match(account?.statement ?? '', /账户名称、期初金额、当前余额、启用状态与租户归属/u);

  const schema = workspace.evidenceViews.find((view) => view.evidenceRef === 'mysql:table:jsh_account');
  assert.equal(schema?.kind, 'MYSQL_SCHEMA');
  if (schema?.kind === 'MYSQL_SCHEMA') {
    assert.equal(schema.columns.length, 11);
    assert.deepEqual(schema.columns.filter((column) => column.highlighted).map((column) => column.name),
      ['name', 'initial_amount', 'current_amount', 'enabled', 'tenant_id']);
  }
});

test('review workspace publishes one validated readable bundle with explicit claim sections', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  }) as ReturnType<typeof projectSourceReviewWorkspace> & {
    contentBundle?: {
      claims: Array<{ claimId: string; sectionId: string; sectionPurpose: string; markdownAnchor: string }>;
      documents: Array<{ sourceId: string; markdown: string }>;
      evidence: Array<{ evidenceRef: string; claimId: string }>;
      summaries: Array<{ conclusions: { claimIds: string[] } }>;
    };
  };

  const bundle = workspace.contentBundle;
  assert.ok(bundle, '审阅清单、Markdown 与依据必须来自同一份已校验阅读投影');
  assert.deepEqual(bundle.claims.map((claim) => claim.claimId), workspace.claims.map((claim) => claim.claimId));
  assert.equal(bundle.claims.every((claim) => (
    claim.sectionId.length > 0 && claim.sectionPurpose.length > 0 && claim.markdownAnchor.length > 0
  )), true);
  assert.equal(bundle.documents.length, 1);
  assert.equal(bundle.documents[0]?.sourceId, mysqlInput.sourceId);
  assert.equal(bundle.documents[0]?.markdown, workspace.markdown.content);
  const pendingTaskClaimIds = new Set(workspace.tasks.map((task) => task.claimId));
  assert.deepEqual(bundle.summaries[0]?.conclusions.claimIds,
    workspace.claims
      .filter((claim) => !pendingTaskClaimIds.has(claim.claimId))
      .map((claim) => claim.claimId));
  assert.deepEqual(bundle.summaries[0]?.pendingTasks.taskIds,
    workspace.tasks.map((task) => task.taskId));
  assert.deepEqual(new Set(bundle.evidence.map((item) => item.claimId)), new Set(workspace.claims.map((claim) => claim.claimId)));
});

test('MySQL human claims are projected from verified evidence instead of legacy Markdown neighbour lines', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  assert.equal(workspace.claims.some((claim) => (
    /参数化 DML 摘要只说明|该业务载体存在于当前部署结构|冻结 MySQL 快照的审阅文档/u.test(claim.statement)
  )), false);
  assert.ok(workspace.claims.every((claim) => claim.title.trim().length > 0));
  assert.ok(workspace.claims.every((claim) => claim.statement.trim().length > 0));
  assert.ok(workspace.claims.every((claim) => (
    claim.evidenceRefs.length > 0 && claim.markdownAnchor.length > 0
  )));

  const accountHead = workspace.claims.find((claim) => claim.markdownAnchor === 'curated-e002');
  assert.deepEqual(accountHead && {
    title: accountHead.title,
    statement: accountHead.statement,
  }, {
    title: '财务单据表头（jsh_account_head）',
    statement: '保存收付款类型、业务单据号、金额、状态和租户归属，可查看完整字段结构。',
  });
  assert.doesNotMatch(accountHead?.statement ?? '', /`jsh_account_head` 财务主表/u);
});

test('MySQL object summaries use business field names while the exact schema remains in the evidence view', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  const accountItem = workspace.claims.find((claim) => claim.markdownAnchor === 'curated-e003');
  assert.match(accountItem?.statement ?? '', /表头标识、账户标识、收支项目标识、单据标识和应收欠款/u);
  assert.doesNotMatch(accountItem?.statement ?? '', /表头Id|账户Id|收支项目Id|单据id/u);

  const tenant = workspace.claims.find((claim) => claim.title === '租户（jsh_tenant）');
  assert.match(tenant?.statement ?? '', /用户数上限、租户类型和启用状态/u);
  assert.doesNotMatch(tenant?.statement ?? '', /0免费租户|1付费租户|0-禁用/u);
});

test('review workspace separates actionable tasks, business conclusions, gaps, and object details', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  }) as ReturnType<typeof projectSourceReviewWorkspace> & {
    checklist?: {
      tasks: Array<{ claimId: string; title: string; statement: string }>;
      keyConclusions: Array<{ claimId: string; title: string; statement: string }>;
      gaps: Array<{ claimId: string; title: string; statement: string }>;
      objectDetails: Array<{ claimId: string; title: string; statement: string }>;
    };
  };

  assert.deepEqual(workspace.tabs, ['审阅清单', 'Markdown 文档']);
  assert.ok(workspace.checklist, '审阅页面必须使用有语义的清单投影');
  assert.ok(workspace.checklist.tasks.some((item) => item.claimId === 'claim:guanyijia_mysql:curated-e005'));
  assert.ok(workspace.checklist.keyConclusions.some((item) => item.title === '账户主数据（jsh_account）'));
  assert.equal(workspace.checklist.objectDetails.length, 31,
    '对象明细只保留有业务含义的可追踪对象；范围元信息、跨来源提示和重点结论不应伪装成对象');
  assert.equal(workspace.checklist.objectDetails.some((item) => item.section === 8), false,
    '示例问题不是可审阅对象，不能混入对象明细');
  for (const group of [workspace.checklist.tasks, workspace.checklist.keyConclusions, workspace.checklist.gaps]) {
    for (const item of group) {
      assert.doesNotMatch(item.title, /^#+\s*\d+\./u);
      assert.notEqual(item.title, item.statement, '标题不能只是重复 Markdown 元数据');
      assert.doesNotMatch(`${item.title} ${item.statement}`, /guanyijia_[a-z_]+:\d+/u);
    }
  }
  const visibleClaimIds = [
    ...workspace.checklist.tasks,
    ...workspace.checklist.keyConclusions,
    ...workspace.checklist.gaps,
    ...workspace.checklist.objectDetails,
  ].map((item) => item.claimId);
  assert.equal(new Set(visibleClaimIds).size, visibleClaimIds.length,
    '同一审阅结论只能出现在一个清单区域，避免任务、缺口和对象明细重复出现');
});

test('human MySQL conclusions are source-pure and exclude unrelated constraint evidence', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  const documentItemRelation = workspace.claims.find((claim) => (
    claim.markdownAnchor === 'curated-g5'
  ));
  assert.deepEqual(documentItemRelation?.evidenceRefs, ['mysql:table:jsh_depot_item']);
  assert.equal(documentItemRelation?.title, '单据明细关联表头、商品和仓库');
  assert.match(documentItemRelation?.statement ?? '', /一行明细对应到具体单据、商品和仓库/u);
  assert.match(documentItemRelation?.statement ?? '', /header_id、material_id 和 depot_id/u);
  assert.doesNotMatch(documentItemRelation?.statement ?? '', /外键事实/u);

  const documentHeadFields = workspace.claims.find((claim) => (
    claim.markdownAnchor === 'curated-g4'
  ));
  assert.deepEqual(documentHeadFields?.evidenceRefs, ['mysql:table:jsh_depot_head']);
  assert.equal(workspace.claims.some((claim) => (
    ['curated-g6', 'curated-g7', 'curated-g8', 'curated-g9', 'curated-g10', 'curated-g11', 'curated-g12']
      .includes(claim.markdownAnchor)
  )), false, '跨来源、查询材料边界、提示语和范围元信息不应伪装为 MySQL 结论');
  assert.match(workspace.metadata.sourceDescription, /查询条件/u);
  assert.match(workspace.metadata.sourceDescription, /没有实际查询结果/u);

  for (const claim of workspace.claims) {
    assert.equal(claim.evidenceRefs.every((evidenceRef) => evidenceRef.startsWith('mysql:')), true,
      `${claim.title} must remain a MySQL-only conclusion`);
  }
});

test('review workspace projects each scripted task as an editable business change with its evidence and Markdown result', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  const task = workspace.tasks.find((candidate) => candidate.taskId === 'scripted:mysql:rename-depot-head');
  assert.deepEqual(task?.editableFields, [{
    field: 'label', label: '业务名称', value: '库存单据表头（jsh_depot_head）',
  }]);
  assert.equal(task?.current.title, '库存单据主表');
  assert.equal(task?.recommendation.title, '库存单据表头（jsh_depot_head）');
  assert.match(task?.reason ?? '', /统一术语/u);
  assert.match(task?.evidence[0]?.excerpt ?? '', /CREATE TABLE `jsh_depot_head`/u);
  assert.match(task?.evidence[0]?.location ?? '', /ddl\/tables\/jsh_depot_head\.sql/u);
  assert.deepEqual(task?.markdownChange, {
    sectionTitle: '业务对象',
    before: ['库存单据主表：保存出入库单据类型、分类、票据号、业务时间、状态、金额和租户归属。'],
    after: ['库存单据表头（jsh_depot_head）：保存出入库单据类型、分类、票据号、业务时间、状态、金额和租户归属。'],
  });
  assert.doesNotMatch(JSON.stringify(task), /规范值|稳定代码|evidenceRef|affectedObjectRef|DEPLOYED_TABLE_PRESENT/u);
});

test('all five sources expose the same two-tab source-review projection contract', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  const github = readSourceReviewDocument(githubInput);
  const authored = authoredInputs.map((input) => readSourceReviewDocument(input));
  assert.ok(mysql, 'MySQL review must be available');
  assert.ok(github, 'GitHub review must be available');
  assert.equal(authored.every(Boolean), true, 'each authored or derived source must be reviewable');

  for (const review of [mysql, github, ...authored]) {
    assert.ok(review);
    const projection = projectSourceReviewDocument(review);
    assert.deepEqual(projection.tabs, ['审阅清单', 'Markdown 文档']);
    assert.ok(projection.sections.length > 0, '只显示有实际审阅内容的章节');
    assert.equal(new Set(projection.sections.map((section) => section.title)).size, projection.sections.length);
    assert.ok(projection.items.length > 0);
    assert.ok(projection.traceLinks.length > 0);
    assert.ok(projection.items.every((item) => item.evidenceRefs.length > 0));
  }

  assert.equal(mysql.evidence.length, 39);
  assert.ok(github.evidence.length >= 10);
  assert.equal(github.evidence.every((evidence) => evidence.evidenceClass === 'SOURCE_NATIVE'), true);
});

test('human review chapters are explicit business groupings instead of transport positions', () => {
  const official = readSourceReviewDocument(authoredInputs[0]);
  const policy = readSourceReviewDocument(authoredInputs[1]);
  const terminology = readSourceReviewDocument(authoredInputs[2]);
  assert.ok(official && policy && terminology);

  const workspaceFor = (sourceDocument: NonNullable<typeof official>) => projectSourceReviewWorkspace({
    sourceDocument,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  assert.deepEqual(workspaceFor(official).sections.map((section) => section.title), [
    '系统边界与基础资料',
    '采购、销售与库存流程',
    '财务、状态与时间口径',
  ]);
  assert.deepEqual(workspaceFor(policy).sections.map((section) => section.title), [
    '基础资料与权限',
    '采购、销售与退货',
    '库存与负库存',
    '财务结算与欠款',
    '状态与统计时间',
  ]);
  assert.deepEqual(workspaceFor(terminology).sections.map((section) => section.title), [
    '术语与业务关系',
  ]);
});

test('authored and derived sources identify their material honestly at the source level', () => {
  const [officialInput, policyInput, terminologyInput] = authoredInputs;
  const workspaceFor = (input: typeof officialInput) => {
    const sourceDocument = readSourceReviewDocument(input);
    assert.ok(sourceDocument);
    return projectSourceReviewWorkspace({ sourceDocument, currentBlocks: [], scriptedDecisions: [] });
  };

  const official = workspaceFor(officialInput);
  const policy = workspaceFor(policyInput);
  const terminology = workspaceFor(terminologyInput);
  assert.equal(official.metadata.title, '业务说明');
  assert.equal(policy.metadata.title, 'ERP管理制度');
  assert.equal(terminology.metadata.title, '企业术语图');
  assert.match(official.markdown.content, /^# 业务说明$/mu);
  assert.match(policy.markdown.content, /^# ERP管理制度$/mu);
  assert.match(terminology.markdown.content, /^# 企业术语图$/mu);
  assert.match(official.metadata.sourceDescription, /系统边界、流程和业务口径/u);
  assert.match(policy.metadata.sourceDescription, /待讨论的目标规则/u);
  assert.match(terminology.metadata.sourceDescription, /统一业务术语/u);
});

test('official-document claims each get one short claim-specific document-section view', () => {
  const official = readSourceReviewDocument(authoredInputs[0]);
  assert.ok(official);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: official,
    currentBlocks: [],
    scriptedDecisions: [],
  });
  assert.ok(workspace.claims.length > 0);
  const documentViews = workspace.evidenceViews.filter((view) => view.kind === 'DOCUMENT_SECTION');
  assert.equal(documentViews.length, workspace.claims.length);
  for (const claim of workspace.claims) {
    const matches = documentViews.filter((view) => claim.evidenceRefs.includes(view.evidenceRef));
    assert.equal(matches.length, 1, `${claim.claimId} must have one matching document section`);
    assert.ok(matches[0]!.excerpt.length <= 140, `${claim.claimId} must be a short reviewer passage`);
    assert.ok((matches[0]!.excerpt.match(/[。！？!?]/gu)?.length ?? 0) <= 3,
      `${claim.claimId} must not turn a whole authored section into one dense reviewer paragraph`);
    assert.notEqual(matches[0]!.excerpt, official.evidence.find((evidence) => evidence.evidenceRef === matches[0]!.evidenceRef)?.excerpt);
  }
});

test('derived terminology evidence is projected as readable terms and relations, never raw graph JSON', () => {
  const terminology = readSourceReviewDocument(authoredInputs[2]);
  assert.ok(terminology);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: terminology,
    currentBlocks: [],
    scriptedDecisions: [],
  });

  const termViews = workspace.evidenceViews.filter((view) => view.kind === 'TERM_RELATION');
  assert.ok(termViews.length > 0, '术语结论必须有可读的术语材料');
  for (const view of termViews) {
    assert.ok(view.terms.length > 0, '术语材料必须列出相关术语');
    assert.ok(view.relations.length > 0, '术语材料必须列出相关关系');
    assert.equal(JSON.stringify(view).includes('graphId'), false, '原始 graph.json 不能进入审阅页面');
    assert.equal(view.terms.every((term) => term.name && term.definition && term.domain), true);
    assert.equal(view.relations.every((relation) => relation.subject && relation.predicate && relation.object && relation.description), true);
  }
});

test('human review markdown never exposes transport markers or internal source identities', () => {
  for (const input of [mysqlInput, githubInput, ...authoredInputs]) {
    const review = readSourceReviewDocument(input);
    assert.ok(review);
    const workspace = projectSourceReviewWorkspace({
      sourceDocument: review,
      currentBlocks: [],
      scriptedDecisions: [],
    });
    assert.doesNotMatch(workspace.markdown.content, /\[TRACE:|guanyijia_[a-z_]+:\d+|sha256:|FROZEN_RECORD/u);
    assert.doesNotMatch(workspace.markdown.content, /本次代码节选未覆盖该目录|不据此补造代码或SQL/u);
  }
});

test('packaged source reviews use explicit claim metadata instead of Markdown transport markers', () => {
  const github = readSourceReviewDocument(githubInput);
  assert.ok(github);
  assert.doesNotMatch(github.markdown, /\[TRACE:|<!--|[a-f0-9]{40}/u);
  assert.ok(github.traceLinks.every((trace) => (
    typeof trace.section === 'number'
    && Boolean(trace.sectionId)
    && Boolean(trace.sectionPurpose)
    && Boolean(trace.claim?.title)
    && Boolean(trace.claim?.statement)
  )));
});

test('GitHub review is an exact-source document, not a template code document', () => {
  const github = readSourceReviewDocument(githubInput);
  assert.ok(github);
  const workspace = projectSourceReviewWorkspace({
    sourceDocument: github,
    currentBlocks: [],
    scriptedDecisions: [],
  });
  assert.equal(github.title, '代码仓库审阅');
  assert.match(workspace.metadata.sourceDescription, /指定版本源码节选/u);
  assert.equal(workspace.metadata.coverageLabel, '69 个已保存代码片段',
    '审阅页应说明本次可阅读的代码材料，而不是暴露整个仓库文件计数');
  assert.doesNotMatch(github.markdown, /源码界面与工作流提供/);
  assert.doesNotMatch(github.markdown, /冻结扫描已识别/);
  assert.doesNotMatch(github.markdown, /已冻结的关联或汇总语句/);
  assert.doesNotMatch(github.markdown, /SELECT\s*\/\*/);

  const exactSql = github.evidence.find((evidence) => evidence.evidenceRef === 'github:v5:005');
  assert.ok(exactSql);
  assert.equal(exactSql.excerptKind, 'EXACT_EXCERPT');
  assert.match(exactSql.excerpt, /minus_stock_flag/u);
  assert.match(exactSql.locationValue, /jsh_erp\.sql:L\d+-L\d+/);

  assert.equal(github.traceLinks.length, 43);
  assert.equal(new Set(github.evidence.map((evidence) => evidence.locationValue.split(':L')[0])).size, 32);
});

test('each source-review item traces source evidence to exactly one or more document anchors', () => {
  const github = readSourceReviewDocument(githubInput);
  assert.ok(github);
  for (const item of github.items) {
    assert.ok(item.markdownAnchors.length > 0, item.title);
    assert.ok(item.evidenceRefs.length > 0, item.title);
    for (const evidenceRef of item.evidenceRefs) {
      assert.ok(github.evidence.some((evidence) => evidence.evidenceRef === evidenceRef), evidenceRef);
    }
    for (const anchor of item.markdownAnchors) {
      assert.equal(github.traceLinks.some((trace) => trace.markdownAnchor === anchor), true, anchor);
    }
  }
});

test('scripted review claims have exact formal mappings and keep narrative sections free of SQL evidence', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  const github = readSourceReviewDocument(githubInput);
  assert.ok(mysql && github);

  const depotHead = mysql.items.find((item) => item.evidenceRefs.includes('mysql:table:jsh_depot_head'));
  assert.equal(depotHead?.formalBlockId, 'gyj-block:physical.table.jsh_depot_head');
  assert.equal((depotHead as { scriptedEditId?: string } | undefined)?.scriptedEditId,
    'scripted:mysql:rename-depot-head');

  const minusStock = github.items.find((item) => item.evidenceRefs.includes('github:v5:005'));
  assert.equal(minusStock?.formalBlockId, 'gyj-block:rule.negative_stock');
  assert.equal((minusStock as { scriptedEditId?: string } | undefined)?.scriptedEditId,
    'scripted:github:clarify-negative-stock');

  assert.equal(mysql.traceLinks.some((trace) => ['[TRACE:G1]', '[TRACE:G2]', '[TRACE:G3]']
    .includes(trace.linePrefix)), false);
  assert.doesNotMatch(mysql.markdown, /只读审阅投影/u);
  assert.match(mysql.markdown, /针对冻结 MySQL 快照的审阅文档/u);
});

test('a scripted revision overlays only the permitted visible claim fields', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  const github = readSourceReviewDocument(githubInput);
  assert.ok(mysql && github);
  const story = createGuanyijiaStandardizationStory();
  const mysqlCompilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const githubCompilation = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [] });

  const renamedMysqlBlocks = mysqlCompilation.blocks.map((block) => block.blockId === 'gyj-block:physical.table.jsh_depot_head'
    ? { ...block, label: '库存单据表头（jsh_depot_head）' }
    : block);
  const mysqlOverlay = projectSourceReviewRevision(mysql, renamedMysqlBlocks);
  const mysqlClaim = mysqlOverlay.items.find((item) => item.scriptedEditId === 'scripted:mysql:rename-depot-head');
  const initialMysqlClaim = mysql.items.find((item) => item.scriptedEditId === 'scripted:mysql:rename-depot-head');
  assert.equal(mysqlClaim?.title, '库存单据表头（jsh_depot_head）');
  assert.equal(mysqlClaim?.statement, initialMysqlClaim?.statement);
  assert.deepEqual(mysqlClaim?.evidenceRefs, initialMysqlClaim?.evidenceRefs);
  const mysqlWorkspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: renamedMysqlBlocks,
    scriptedDecisions: [],
  });
  assert.match(mysqlWorkspace.markdown.content, /库存单据表头（jsh_depot_head）/u);
  assert.equal(mysqlWorkspace.traceRows.some((row) => row.claimId === 'claim:guanyijia_mysql:curated-e005'
    && row.markdownAnchor === 'curated-e005'), true);

  const clarifiedGithubBlocks = githubCompilation.blocks.map((block) => block.blockId === 'gyj-block:rule.negative_stock'
    ? { ...block, label: '租户级负库存控制（人工确认）', value: { ...block.value as object, text: '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。' } }
    : block);
  const githubOverlay = projectSourceReviewRevision(github, clarifiedGithubBlocks);
  const githubClaim = githubOverlay.items.find((item) => item.scriptedEditId === 'scripted:github:clarify-negative-stock');
  assert.equal(githubClaim?.title, '租户级负库存控制（人工确认）');
  assert.equal(githubClaim?.statement, '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。');
  assert.deepEqual(githubClaim?.evidenceRefs, ['github:v5:005', 'github:v5:006']);
  const githubWorkspace = projectSourceReviewWorkspace({
    sourceDocument: github,
    currentBlocks: clarifiedGithubBlocks,
    scriptedDecisions: [],
  });
  assert.match(githubWorkspace.markdown.content, /仍需结合运行配置和业务制度确认/u);
  assert.equal(githubWorkspace.claims.find((claim) => claim.scriptedEditId === 'scripted:github:clarify-negative-stock')?.title,
    '租户级负库存控制（人工确认）');
  assert.equal(githubWorkspace.tasks.find((task) => task.taskId === 'scripted:github:clarify-negative-stock')?.current.title,
    '租户级负库存控制（人工确认）');
});

test('a decided scripted task moves into one visible confirmed-conclusion entry', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  assert.ok(mysql);
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const renamedBlocks = compilation.blocks.map((block) => block.blockId === 'gyj-block:physical.table.jsh_depot_head'
    ? { ...block, label: '库存单据表头（jsh_depot_head）' }
    : block);

  const workspace = projectSourceReviewWorkspace({
    sourceDocument: mysql,
    currentBlocks: renamedBlocks,
    scriptedDecisions: [{
      definition: { editId: 'scripted:mysql:rename-depot-head' },
      status: 'APPLIED',
    }],
  });
  const claimId = 'claim:guanyijia_mysql:curated-e005';
  assert.equal(workspace.checklist.tasks.some((item) => item.claimId === claimId), false,
    '已处理任务不应继续显示为待核对');
  assert.equal(workspace.checklist.keyConclusions.some((item) => (
    item.claimId === claimId && item.title === '库存单据表头（jsh_depot_head）'
  )), true, '已确认结论应在审阅清单中保留当前文字');
  const occurrences = [
    ...workspace.checklist.tasks,
    ...workspace.checklist.keyConclusions,
    ...workspace.checklist.gaps,
    ...workspace.checklist.objectDetails,
  ].filter((item) => item.claimId === claimId);
  assert.equal(occurrences.length, 1, '同一结论不能同时占据待核对和已确认区域');
});

test('a packaged review refuses an active run pinned to another content publication', () => {
  const binding = bindDemoContentRun({
    runId: 'run-bound-source-review',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const mismatchedBinding = { ...binding, contentSha256: 'sha256:0000000000000000000000000000000000000000000000000000000000000000' as const };

  assert.throws(() => readSourceReviewDocument({
    ...githubInput,
    contentBinding: mismatchedBinding,
  }), /冻结内容快照校验失败/u);
});

test('an active V6 run keeps the outer review legacy while exposing a separate rich standard document', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v6-mysql-source-review',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const outerGithub = readSourceReviewDocument({ ...githubInput, contentBinding: binding });
  const standardMysql = readSourceStandardDocument({ ...mysqlInput, contentBinding: binding });

  assert.ok(outerGithub);
  assert.ok(standardMysql);
  assert.equal(binding.contentSnapshotId, 'guanyijia-demo-content-v6-20260826');
  assert.equal(outerGithub.traceLinks[0]?.markdownAnchor, 'github-v5-github-v5-001',
    'the outer review must stay on its existing V5 checklist material');
  assert.equal(outerGithub.standardSections, undefined);
  assert.deepEqual(standardMysql.standardSections.map((section) => section.sectionId), [
    'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
  ]);
  assert.equal(standardMysql.claims.length, 45);
  const tableClaims = standardMysql.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-table-'));
  assert.equal(tableClaims.length, 30);
  assert.equal(tableClaims.every((claim) => claim.schemaEvidences.length > 0), true,
    'each selected MySQL table must remain expandable in the standard-document reader');
  assert.match(standardMysql.markdown, /## 9\. 待确认事项/u);
});

test('rich standard-document claims keep the matching readable detail view for every source kind', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v6-standard-document-detail-views',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const mysql = readSourceStandardDocument({ ...mysqlInput, contentBinding: binding });
  const github = readSourceStandardDocument({ ...githubInput, contentBinding: binding });
  const official = readSourceStandardDocument({ ...authoredInputs[0]!, contentBinding: binding });
  const terminology = readSourceStandardDocument({ ...authoredInputs[2]!, contentBinding: binding });

  assert.equal(mysql?.claims.find((claim) => claim.claimId === 'v6-mysql-table-jsh-account')?.detailViews?.[0]?.kind, 'MYSQL_SCHEMA');
  assert.equal(github?.claims.find((claim) => claim.claimId === 'github-v5-001')?.detailViews?.[0]?.kind, 'SOURCE_EXCERPT');
  assert.equal(official?.claims.find((claim) => claim.claimId === 'v6-guanyijia-official-docs-overview')?.detailViews?.[0]?.kind, 'DOCUMENT_SECTION');
  assert.equal(terminology?.claims.find((claim) => claim.claimId === 'v6-guanyijia-semantica-demo-object')?.detailViews?.[0]?.kind, 'TERM_RELATION');
});

test('the V6 standard document applies only the scripted MySQL title revision', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v6-mysql-standard-revision',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const document = readSourceStandardDocument({ ...mysqlInput, contentBinding: binding });
  assert.ok(document);
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const blocks = compilation.blocks.map((block) => block.blockId === 'gyj-block:physical.table.jsh_depot_head'
    ? { ...block, label: '库存单据表头（jsh_depot_head）' }
    : block);
  const before = document.claims.find((claim) => claim.claimId === 'v6-mysql-table-jsh-depot-head');

  const revised = projectSourceStandardDocumentRevision(document, blocks);
  const after = revised.claims.find((claim) => claim.claimId === 'v6-mysql-table-jsh-depot-head');

  assert.equal(after?.title, '库存单据表头（jsh_depot_head）');
  assert.equal(after?.statement, before?.statement);
  assert.equal(after?.markdownAnchor, before?.markdownAnchor);
  assert.deepEqual(after?.evidenceRefs, before?.evidenceRefs);
  assert.deepEqual(after?.schemaEvidences, before?.schemaEvidences);
  assert.match(revised.markdown, /### 库存单据表头（jsh_depot_head）/u);
  assert.equal(document.claims.find((claim) => claim.claimId === 'v6-mysql-table-jsh-depot-head')?.title,
    '单据主表（jsh_depot_head）', 'the frozen reader input must not be mutated');
  assert.deepEqual(revised.claims.find((claim) => claim.claimId === 'v6-guanyijia-mysql-overview'),
    document.claims.find((claim) => claim.claimId === 'v6-guanyijia-mysql-overview'));
});

test('the V6 standard document applies only the scripted GitHub title and statement revision', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v6-github-standard-revision',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const document = readSourceStandardDocument({ ...githubInput, contentBinding: binding });
  assert.ok(document);
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [] });
  const revisedStatement = '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。';
  const blocks = compilation.blocks.map((block) => block.blockId === 'gyj-block:rule.negative_stock'
    ? { ...block, label: '租户级负库存控制（人工确认）', value: { ...block.value as object, text: revisedStatement } }
    : block);
  const before = document.claims.find((claim) => claim.claimId === 'github-v5-003');

  const revised = projectSourceStandardDocumentRevision(document, blocks);
  const after = revised.claims.find((claim) => claim.claimId === 'github-v5-003');

  assert.equal(after?.title, '租户级负库存控制（人工确认）');
  assert.equal(after?.statement, revisedStatement);
  assert.equal(after?.markdownAnchor, before?.markdownAnchor);
  assert.deepEqual(after?.evidenceRefs, ['github:v5:005', 'github:v5:006']);
  assert.match(revised.markdown, /### 租户级负库存控制（人工确认）\n\n固定版本的系统配置表定义了按租户保存的负库存启用标记/u);
  assert.equal(document.claims.find((claim) => claim.claimId === 'github-v5-003')?.title, '企业级业务开关');
  assert.deepEqual(revised.claims.find((claim) => claim.claimId === 'github-v5-001'),
    document.claims.find((claim) => claim.claimId === 'github-v5-001'));
});

test('the V7 MySQL scripted revision uses its stable claim and anchor when frozen Markdown typography changed', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v7-mysql-standard-revision',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const base = readSourceStandardDocument({ ...mysqlInput, contentBinding: binding });
  assert.ok(base);
  const document = v7StandardDocumentWithStaleClaimBlock(base, 'v6-mysql-table-jsh-depot-head');
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const blocks = compilation.blocks.map((block) => block.blockId === 'gyj-block:physical.table.jsh_depot_head'
    ? { ...block, label: '库存单据表头（jsh_depot_head）' }
    : block);

  const revised = projectSourceStandardDocumentRevision(document, blocks);
  const claim = revised.claims.find((candidate) => candidate.claimId === 'v6-mysql-table-jsh-depot-head');

  assert.equal(claim?.title, '库存单据表头（jsh_depot_head）');
  assert.deepEqual(claim?.evidenceRefs,
    document.claims.find((candidate) => candidate.claimId === 'v6-mysql-table-jsh-depot-head')?.evidenceRefs);
  assert.match(revised.markdown, /### 库存单据表头（jsh_depot_head）/u);
  assert.doesNotMatch(revised.markdown, /冻结标题的旧排版/u);
  assert.match(revised.markdown, /本章阅读说明：/u);
});

test('the V7 GitHub scripted revision uses its stable claim and anchor when frozen Markdown typography changed', () => {
  const binding = bindDemoContentRun({
    runId: 'run-v7-github-standard-revision',
    formalSources: [mysqlInput, githubInput, ...authoredInputs],
  });
  const base = readSourceStandardDocument({ ...githubInput, contentBinding: binding });
  assert.ok(base);
  const document = v7StandardDocumentWithStaleClaimBlock(base, 'github-v5-003');
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [] });
  const revisedStatement = '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。';
  const blocks = compilation.blocks.map((block) => block.blockId === 'gyj-block:rule.negative_stock'
    ? { ...block, label: '租户级负库存控制（人工确认）', value: { ...block.value as object, text: revisedStatement } }
    : block);

  const revised = projectSourceStandardDocumentRevision(document, blocks);
  const claim = revised.claims.find((candidate) => candidate.claimId === 'github-v5-003');

  assert.equal(claim?.title, '租户级负库存控制（人工确认）');
  assert.equal(claim?.statement, revisedStatement);
  assert.deepEqual(claim?.evidenceRefs, ['github:v5:005', 'github:v5:006']);
  assert.match(revised.markdown, /### 租户级负库存控制（人工确认）\n\n固定版本的系统配置表定义了按租户保存的负库存启用标记/u);
  assert.doesNotMatch(revised.markdown, /冻结标题的旧排版/u);
  assert.match(revised.markdown, /本章阅读说明：/u);
});

test('人类阅读投影用业务边界说明来源，而不暴露实现过程话术', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  const github = readSourceReviewDocument(githubInput);
  const official = readSourceReviewDocument(authoredInputs[0]);
  const policy = readSourceReviewDocument(authoredInputs[1]);
  const terminology = readSourceReviewDocument(authoredInputs[2]);
  assert.ok(mysql && github && official && policy && terminology);

  const mysqlWorkspace = projectSourceReviewWorkspace({
    sourceDocument: mysql, currentBlocks: [], scriptedDecisions: [],
  });
  const githubWorkspace = projectSourceReviewWorkspace({
    sourceDocument: github, currentBlocks: [], scriptedDecisions: [],
  });
  const officialWorkspace = projectSourceReviewWorkspace({
    sourceDocument: official, currentBlocks: [], scriptedDecisions: [],
  });
  const policyWorkspace = projectSourceReviewWorkspace({
    sourceDocument: policy, currentBlocks: [], scriptedDecisions: [],
  });
  const terminologyWorkspace = projectSourceReviewWorkspace({
    sourceDocument: terminology, currentBlocks: [], scriptedDecisions: [],
  });

  assert.match(mysqlWorkspace.metadata.sourceDescription, /相关处理过程和查询条件/u);
  assert.doesNotMatch(mysqlWorkspace.metadata.sourceDescription, /查询语句结构/u);
  assert.match(githubWorkspace.metadata.sourceDescription, /指定版本源码节选/u);
  assert.doesNotMatch(githubWorkspace.metadata.sourceDescription, /固定提交|页面中重建/u);
  assert.equal(githubWorkspace.claims.find((claim) => claim.markdownAnchor === 'github-v5-github-v5-003')?.title,
    '企业级业务开关');
  assert.match(githubWorkspace.claims.find((claim) => claim.markdownAnchor === 'github-v5-github-v5-003')!.statement,
    /负库存.*开关字段/u);
  const stockSummary = githubWorkspace.claims.find((claim) => claim.title === '库存查询的基础口径');
  assert.ok(stockSummary);
  assert.match(stockSummary.statement, /jsh_material_current_stock/u);
  const depositSummary = githubWorkspace.claims.find((claim) => claim.title === '零售预付款与关联订金汇总');
  assert.ok(depositSummary);
  assert.doesNotMatch(depositSummary.statement, /Mapper|口径/u);
  const timeClaim = officialWorkspace.claims.find((claim) => claim.title === '报表指标与统计时点');
  assert.ok(timeClaim);
  assert.match(timeClaim.statement, /采购入库.*销售出库/u);
  const stockRule = policyWorkspace.claims.find((claim) => claim.title === '库存数量与安全范围');
  assert.ok(stockRule);
  assert.match(stockRule.statement, /仓库/u);
  const terminologyIntroduction = terminologyWorkspace.claims.find((claim) => /术语图用于统一/u.test(claim.title));
  assert.ok(terminologyIntroduction);
  assert.match(terminologyIntroduction.statement,
    /帮助统一阅读方式/u);
  assert.doesNotMatch(terminologyIntroduction.statement,
    /独立证据数量/u);
});

test('内联依据说明使用人类可读的话术，不把提交、证据等级或独立事实计数带入审阅页面', () => {
  const mysql = readSourceReviewDocument(mysqlInput);
  const github = readSourceReviewDocument(githubInput);
  const terminology = readSourceReviewDocument(authoredInputs[2]);
  assert.ok(mysql && github && terminology);

  const visibleText = JSON.stringify([mysql.items, github.items, terminology.items]);

  assert.match(visibleText, /已保存对应源码节选，可核对具体实现/u);
  assert.match(visibleText, /这条术语关系由演示资料整理而来/u);
  assert.doesNotMatch(visibleText, /固定提交|独立事实证据|SOURCE_NATIVE|DERIVED_DEMO/u);
});

test('五份来源的可见审阅内容通过文字净化合同', () => {
  for (const input of [mysqlInput, githubInput, ...authoredInputs]) {
    const review = readSourceReviewDocument(input);
    assert.ok(review);
    const workspace = projectSourceReviewWorkspace({
      sourceDocument: review,
      currentBlocks: [],
      scriptedDecisions: [],
    });
    const visible = [
      workspace.metadata.title,
      workspace.metadata.coverageLabel,
      workspace.metadata.sourceDescription,
      ...workspace.sections.map((section) => section.title),
      ...workspace.claims.flatMap((claim) => [claim.title, claim.statement]),
    ].join('\n');

    assert.doesNotMatch(visible, /\uFFFD|&(?:amp|gt|lt);|\[TRACE:|sha256:|FROZEN_RECORD|SOURCE_NATIVE|DEMO_AUTHORED|DERIVED_DEMO/u);
    assert.doesNotMatch(visible, /该业务载体存在于当前部署结构|冻结扫描已识别|已冻结的关联或汇总语句/u);
    for (const claim of workspace.claims) {
      assert.notEqual(claim.title.trim(), claim.statement.trim(), `${input.sourceId}:${claim.claimId}`);
      assert.ok(claim.title.trim().length <= 28, `${input.sourceId}:${claim.title}`);
      assert.ok(claim.statement.trim().length <= 140, `${input.sourceId}:${claim.title}`);
    }
  }
});
