import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  appendCursorPage, loadConclusion, loadEvidencePage, loadIssuePage, loadReviewWorkspace,
  prepareDecisionInputs, resetIssueQueryState, shouldGateDocumentDecision,
} from './review-workspace.ts';
import { buildAlignmentIssueQuery, defaultAlignmentIssueFilters } from './alignment-issue-filters.ts';
import {
  isLatestConflictPreviewRequest,
  reviseEditableStructuredValue,
  selectDataStandardizationExperience,
  structuredValueEditorModel,
} from './guanyijia-workbench-runtime.ts';
import type {
  BatchOverview, CursorPage, ReviewConclusion, ReviewEvidence, ReviewIssue, ReviewQueryService,
} from '../modeling-document-bridge/types.ts';

test('来源差异搜索和筛选始终查询完整索引并绑定游标', () => {
  assert.deepEqual(buildAlignmentIssueQuery('alignment-1', defaultAlignmentIssueFilters), {
    alignmentId: 'alignment-1', q: undefined, statuses: ['OPEN'], severities: undefined,
    first: 20, after: undefined,
  });
  assert.deepEqual(buildAlignmentIssueQuery('alignment-1', {
    q: ' 退款 ', status: 'DECIDED', severity: 'BLOCKER',
  }, 'query-digest:20'), {
    alignmentId: 'alignment-1', q: '退款', statuses: ['DECIDED'], severities: ['BLOCKER'],
    first: 20, after: 'query-digest:20',
  });
});

const issue: ReviewIssue = {
  issueId: 'ISSUE-01', topic: '退货口径', title: '退货日期字段冲突', severity: 'BLOCKER', status: 'OPEN',
  difference: 'ERP 与 POS 日期口径不一致', claimIds: ['claim-01'], evidenceRefs: ['ev-01'],
  affectedObjectCodes: ['return_order'], recommendedResolutionId: 'use-erp',
  options: [{ resolutionId: 'use-erp', label: '采用 ERP', conclusion: '以 ERP 审核日期为准' }],
};

test('审阅工作区初始化只读取总览与20条问题，展开后才读取结论和证据', async () => {
  const calls: string[] = [];
  const overview: BatchOverview = {
    artifactId: 'doc-01', revision: 8, sourceCount: 8, rootSourceCount: 4, evidenceCount: 337,
    claimCount: 54, issueCount: 3, openIssueCount: 3, decisionCount: 0, lineageGapCount: 0,
  };
  const issues: CursorPage<ReviewIssue> = { items: [issue], nextCursor: null, total: 1 };
  const conclusion: ReviewConclusion = {
    issue, decision: null, evidenceRoots: [{ rootSourceId: 'root-erp', evidenceCount: 25 }],
  };
  const evidence: CursorPage<ReviewEvidence> = {
    items: [{ evidenceId: 'ev-01', sourceId: 'erp', rootSourceIds: ['root-erp'], statement: '退货已审核', locator: { row: 9 }, checksum: 'hash' }],
    nextCursor: 'next-20', total: 25,
  };
  const service: ReviewQueryService = {
    async getOverview() { calls.push('overview'); return overview; },
    async listIssues(query) { calls.push(`issues:${query?.status}:${query?.limit}`); return issues; },
    async getConclusion(issueId) { calls.push(`conclusion:${issueId}`); return conclusion; },
    async listEvidence(query) { calls.push(`evidence:${query.issueId}:${query.rootSourceId}:${query.limit}:${query.cursor ?? '-'}`); return evidence; },
  };

  const workspace = await loadReviewWorkspace({ review: async (artifactId: string) => {
    calls.push(`review:${artifactId}`);
    return service;
  } }, 'doc-01');
  assert.deepEqual(calls, ['review:doc-01', 'overview', 'issues:OPEN:20']);
  assert.equal(workspace.issues.total, 1);

  await loadConclusion(workspace.service, 'ISSUE-01');
  assert.equal(calls.at(-1), 'conclusion:ISSUE-01');
  await loadEvidencePage(workspace.service, { issueId: 'ISSUE-01', rootSourceId: 'root-erp' });
  assert.equal(calls.at(-1), 'evidence:ISSUE-01:root-erp:20:-');
  await loadEvidencePage(workspace.service, { issueId: 'ISSUE-01', rootSourceId: 'root-erp', cursor: 'next-20' });
  assert.equal(calls.at(-1), 'evidence:ISSUE-01:root-erp:20:next-20');
});

test('超过20条问题时只通过ReviewQueryService合并游标页并在人工决定齐备后原子写回', async () => {
  const allIssues = Array.from({ length: 21 }, (_, index): ReviewIssue => ({
    ...issue, issueId: `ISSUE-${String(index + 1).padStart(2, '0')}`, title: `问题 ${index + 1}`,
  }));
  const cursors: Array<string | undefined> = [];
  const service: ReviewQueryService = {
    async getOverview() { throw new Error('本测试不读取总览'); },
    async listIssues(query) {
      cursors.push(query?.cursor);
      return query?.cursor
        ? { items: allIssues.slice(20), nextCursor: null, total: 21 }
        : { items: allIssues.slice(0, 20), nextCursor: 'issues-OPEN:20', total: 21 };
    },
    async getConclusion() { throw new Error('本测试不读取结论'); },
    async listEvidence() { throw new Error('本测试不读取证据'); },
  };
  const first = await service.listIssues({ status: 'OPEN', limit: 20 });
  const second = await loadIssuePage(service, 'OPEN', first.nextCursor!);
  const merged = appendCursorPage(first, second);
  assert.deepEqual(cursors, [undefined, 'issues-OPEN:20']);
  assert.equal(merged.items.length, 21);
  assert.equal(merged.nextCursor, null);

  const incomplete = prepareDecisionInputs(merged.items, {
    'ISSUE-01': { resolutionId: 'use-erp', reason: '采用 ERP 审核口径' },
  });
  assert.equal(incomplete.ready, false);
  assert.equal(incomplete.completed, 1);
  const drafts = Object.fromEntries(merged.items.map((item) => [item.issueId, {
    resolutionId: 'use-erp', reason: `人工理由 ${item.issueId}`,
  }]));
  const complete = prepareDecisionInputs(merged.items, drafts);
  assert.equal(complete.ready, true);
  assert.equal(complete.inputs.length, 21);
  assert.deepEqual(complete.inputs.at(-1), {
    issueId: 'ISSUE-21', resolutionId: 'use-erp', reason: '人工理由 ISSUE-21',
  });
});

test('新artifact始终重建OPEN缓存且Warning游标只在作者确认阶段触发决定门禁', () => {
  const openPage: CursorPage<ReviewIssue> = {
    items: [{ ...issue, issueId: 'WARNING-01', severity: 'WARNING' }],
    nextCursor: 'issues-OPEN:20', total: 21,
  };
  const reset = resetIssueQueryState(openPage);
  assert.equal(reset.issueStatus, 'OPEN');
  assert.equal(reset.issues, openPage);
  assert.equal(reset.openIssues, openPage);
  assert.equal(shouldGateDocumentDecision('AWAITING_CONFIRMATION', openPage, {}), true);
  assert.equal(shouldGateDocumentDecision('AWAITING_REVIEW', openPage, {}), false);
  assert.equal(shouldGateDocumentDecision('APPROVED', openPage, {}), false);
  assert.equal(shouldGateDocumentDecision('FROZEN', openPage, {}), false);
  assert.equal(shouldGateDocumentDecision('AWAITING_CONFIRMATION', { ...openPage, nextCursor: null }, {}), false);
});

test('only the Guanyijia project selects the timeline workbench while retail stays on legacy', () => {
  assert.equal(selectDataStandardizationExperience('guanyijia_erp'), 'GUANYIJIA_TIMELINE');
  assert.equal(selectDataStandardizationExperience('group_retail_ops'), 'LEGACY');
  assert.equal(selectDataStandardizationExperience('default'), 'LEGACY');
});

test('结构化块编辑只开放string与带text对象并保留对象的技术字段', () => {
  assert.deepEqual(structuredValueEditorModel('原始识别结果'), {
    kind: 'STRING', text: '原始识别结果', editable: true,
  });
  const value = {
    normalized: 'ALWAYS_FORBIDDEN', text: '所有租户一律禁止负库存。', tenantScoped: false,
  };
  assert.deepEqual(structuredValueEditorModel(value), {
    kind: 'TEXT_OBJECT', text: '所有租户一律禁止负库存。', editable: true,
    normalized: 'ALWAYS_FORBIDDEN', readonlyFields: { tenantScoped: false },
  });
  assert.deepEqual(reviseEditableStructuredValue(
    value, '演示制度允许负库存。', 'POLICY_ALLOWS_NEGATIVE_STOCK',
  ), {
    normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。', tenantScoped: false,
  });
  assert.deepEqual(structuredValueEditorModel(['未知', { nested: true }]), {
    kind: 'READ_ONLY', text: '未知结构，仅可查看', editable: false,
  });
  assert.throws(() => reviseEditableStructuredValue(9, '九'), /未知结构只读/);
});

test('过期冲突预览的成功、失败与finally都不能覆盖当前请求状态', () => {
  assert.equal(isLatestConflictPreviewRequest(8, 8), true);
  assert.equal(isLatestConflictPreviewRequest(8, 7), false);
});

test('管伊佳审阅助手嵌入当前审阅页，且来源资料区不泄露技术权威标识', () => {
  const panel = readFileSync(new URL('./review-assistant-panel.tsx', import.meta.url), 'utf8');
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  const integrity = readFileSync(new URL('./standardization-technical-details.tsx', import.meta.url), 'utf8');
  const styles = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');
  assert.match(panel, /问我当前结论、来源依据、资料差异或业务影响。/u);
  assert.match(panel, /maxRows:\s*5/u);
  assert.match(panel, /应用此修改/u);
  assert.match(panel, /projectReviewAssistantPlacement/u);
  assert.doesNotMatch(panel, /ResizeObserver|modalPatch|onClosePreview/u);
  assert.doesNotMatch(panel, /conflict\.beforeVariant/u);
  assert.doesNotMatch(panel, /proposal\.preview\.affectedObjects\.slice\(0, 20\)/u);
  assert.doesNotMatch(panel, /dangerouslySetInnerHTML/u);
  assert.match(workbench, /<ReviewAssistantPanel/u);
  assert.equal([...workbench.matchAll(/<ReviewAssistantPanel/g)].length, 1);
  assert.match(styles, /guanyijia-review-assistant-panel/u);
  assert.match(styles, /\.guanyijia-review-assistant-panel\s*\{\s*position:\s*static/u);
  assert.doesNotMatch(styles, /--guanyijia-assistant-panel-height/u);
  assert.match(styles, /\.guanyijia-review-assistant-composer textarea\s*\{[^}]*min-height:\s*44px\s*!important;/u);
  assert.doesNotMatch(inspector, /应用|策略|理由|TextArea|type="primary"|<dt>sourceId<\/dt>|<dt>evidenceRef<\/dt>|<dt>sha256<\/dt>/u);
  assert.match(inspector, /aria-label="来源资料"/u);
  assert.match(inspector, /aria-label="来源进展"/u);
  assert.match(inspector, /aria-label="证据与支持信息"/u);
  assert.match(inspector, /仅支持已保存的来源材料与审阅结论。/u);
  assert.doesNotMatch(inspector, /支持的审阅结论/u);
  assert.match(integrity, /内容校验/u);
  assert.match(integrity, /来源差异记录暂时无法读取。请刷新当前步骤后重试。/u);
  assert.doesNotMatch(integrity, /<dt>sha256<\/dt>|<dt>Content Ref<\/dt>|<dt>Artifact<\/dt>|source\.sourceId<\/dd>|resolution\.hunkSha256/u);
  assert.match(workbench, /authority:\s*currentReviewSource\.authority/u);
});

test('来源资料区按结论展示文档章节或术语关系，而不是回退到整篇来源摘要', () => {
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /view\.kind === 'DOCUMENT_SECTION'/u);
  assert.match(inspector, /view\.kind === 'TERM_RELATION'/u);
  assert.match(inspector, /view\.excerpt/u);
  assert.doesNotMatch(inspector, /view\?\.kind === 'MYSQL_SCHEMA'\) \{[\s\S]*?return null;/u,
    '非 MySQL 来源不能因资料区只认识结构表而退回到整篇摘要');
});

test('来源材料只在对应结论内联展开，不再形成第二个材料阅读区', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(workbench, /data-review-summary-group="MATERIALS"/u);
  assert.doesNotMatch(workbench, /guanyijia-material-list/u);
  assert.doesNotMatch(workbench, /reviewSummary\.materials\.evidenceRefs/u);
  assert.match(workbench, /claim\.evidenceRefs\.map\(/u);
  assert.match(workbench, /guanyijia-inline-evidence/u);
});

test('审阅事项与审阅结论分开呈现，对象明细默认展开', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /documentView === 'MATTERS'[\s\S]*?aria-label="待确认事项"[\s\S]*?aria-label="待补充资料"[\s\S]*?aria-label="已处理事项"/u);
  assert.match(workbench, /documentView === 'CONCLUSIONS'[\s\S]*?aria-label="关键业务结论"[\s\S]*?<details\s+open[^>]*aria-label="对象明细"/u);
});

test('技术依据只在主阅读区呈现，并以中文显示数据库键类型', () => {
  const readable = readFileSync(new URL('./source-document-readable.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  const mysqlSchemaEvidenceView = readFileSync(new URL('./mysql-schema-evidence-view.tsx', import.meta.url), 'utf8');
  const mysqlSchemaEvidence = readFileSync(new URL('./mysql-schema-evidence.ts', import.meta.url), 'utf8');
  assert.doesNotMatch(readable, /supports\?: string/u);
  assert.doesNotMatch(readable, /supports=\{/u);
  assert.match(readable, /MysqlSchemaEvidenceView/u);
  assert.doesNotMatch(inspector, /MysqlSchemaEvidenceView/u);
  assert.match(inspector, /在正文查看技术依据/u);
  assert.match(mysqlSchemaEvidenceView, /projectMysqlSchemaEvidence/u);
  assert.match(mysqlSchemaEvidence, /case 'PRIMARY':\s*return '主键'/u);
  assert.match(mysqlSchemaEvidence, /case 'INDEX':\s*return '索引'/u);
  assert.match(mysqlSchemaEvidence, /case 'FOREIGN':\s*return '外键'/u);
});

test('文档、制度和术语资料缺少结构化展示时局部失败关闭，不回退整篇材料', () => {
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /const hasReadableReviewEvidence = Boolean\(reviewEvidenceView && model\?\.block\?\.readableEvidence\);/u);
  assert.match(inspector, /该事项的已声明依据暂时无法验证。请刷新当前步骤后重试。/u);
  assert.doesNotMatch(inspector, /compactEvidenceFallback/u);
});

test('固定源码节选留在主阅读区，来源资料区只提供可读摘要与跳转', () => {
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  const readable = readFileSync(new URL('./source-document-readable.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(inspector, /view\.rawExcerpt/u);
  assert.match(inspector, /技术内容请在正文中阅读。/u);
  assert.match(inspector, /在正文查看技术依据/u);
  assert.match(readable, /language === 'sql' \? 'SQL 摘录' : '来源代码摘录'/u);
  assert.match(readable, /来源位置：\{source\.locationValue\}/u);
});

test('来源资料区和修改提示使用业务化文案，不暴露剧本或重复的流程说明', () => {
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(inspector, /<dt>读取数量<\/dt>/u);
  assert.doesNotMatch(inspector, /\{model\.objectCount\} 个对象/u);
  assert.match(workbench, /请在审阅事项中处理已列出的建议/u);
  assert.doesNotMatch(workbench, /剧本/u);
  assert.match(inspector, /该资料已准备好，可在主阅读区查看其支持的结论。/u);
  assert.doesNotMatch(inspector, /按当前流程到达后即可阅读|完成前一份资料审阅后/u);
});

test('通用修改入口只说明本次可编辑内容和文档变化，不把内部结构约束当作用户说明', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /本次可修改：名称和结论说明。确认后，审阅文档会同步更新对应段落。/u);
  assert.doesNotMatch(workbench, /来源依据、影响对象和结构化字段会保持不变。/u);
});

test('来源资料区为文档、术语和源码摘录提供受限宽度的可读展示', () => {
  const styles = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');
  assert.match(styles, /\.guanyijia-inspector-document-evidence\s*\{/u);
  assert.match(styles, /\.guanyijia-inspector-term-evidence\s*\{/u);
  assert.match(styles, /\.guanyijia-inspector-source-excerpt\s*\{/u);
  assert.match(styles, /\.guanyijia-inspector-source-excerpt\s+small\s*\{/u);
});

test('来源资料只有工具栏入口，覆盖或全屏层使用资料区内的图标关闭', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /id="guanyijia-inspector-trigger"[\s\S]*?sourcePanelToggleLabel/u);
  assert.doesNotMatch(workbench, /guanyijia-document-inspector-trigger/u);
  assert.match(inspector, /modal && onClose && <div className="guanyijia-facts-inspector-close">/u);
  assert.doesNotMatch(inspector, /<header>\s*<div><span>来源资料<\/span>/u);
  assert.match(workbench, /reviewSurface\.open\(\{[\s\S]*?kind: 'INSPECTOR'[\s\S]*?stableId: `inspector:\$\{selectedBlockId \?\? selectedTimelineItemId \?\? 'current'\}`/u);
});

test('已使用可读依据视图时，资料区不再重复输出内部定位器和对象标签', () => {
  const inspector = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  assert.match(inspector, /const hasReadableReviewEvidence = Boolean\(reviewEvidenceView && model\?\.block\?\.readableEvidence\);/u);
  assert.match(inspector, /!hasReadableReviewEvidence && <dl className="guanyijia-fact-list">/u);
  assert.doesNotMatch(inspector, /model\.block\.locator/u);
  assert.doesNotMatch(inspector, /相关业务对象/u);
  assert.doesNotMatch(inspector, /guanyijia-impact-list/u);
  assert.match(inspector, /MYSQL_SCHEMA[\s\S]*?来源位置：\{view\.locationValue\}/u);
});

test('来源文档页头只说明当前可读审阅范围，不把旧结构块统计带回业务页面', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(workbench, /9 个章节 · \$\{snapshot\.current\.compilationSummary/u);
  assert.doesNotMatch(workbench, /\$\{snapshot\.current\.compilationSummary\.blockCount\} 项识别结果/u);
  assert.doesNotMatch(workbench, /\$\{currentDocumentBlocks\.length\} 项识别结果/u);
  assert.match(workbench, /来源范围：\$\{curatedWorkspace\.metadata\.coverageLabel\}/u);
  assert.doesNotMatch(workbench, /本次范围与限制/u);
  assert.match(workbench, /来源审阅文档/u);
});

test('剧本修改选中正式映射后，来源仍停留在可读审阅事项而非旧结构块墙', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(workbench, /showingMappedFormalBlocks/u);
  assert.match(workbench, /curatedReview \? curatedStructuredDiffOpen/u);
  assert.doesNotMatch(workbench, /curatedReview && !showingMappedFormalBlocks/u);
});

test('历史版本以可区分的创建时间呈现，连续修订不会显示为同一时刻', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /function formatRevisionCreatedAt/u);
  assert.match(workbench, /fractionalSecondDigits:\s*3/u);
  assert.match(workbench, /创建于 \{formatRevisionCreatedAt\(document\.createdAt\)\}/u);
  assert.doesNotMatch(workbench, /创建于 \{new Date\(document\.createdAt\)\.toLocaleString/u);
});

test('审阅修改摘要只说明用户能理解的结果，不把内部定位能力当作空状态', () => {
  const workbench = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /本次没有修改审阅结论。/u);
  assert.doesNotMatch(workbench, /当前版本没有可定位的识别结果差异。/u);
  assert.doesNotMatch(workbench, /本次没有需要展示的修改。/u);
  assert.doesNotMatch(workbench, /当前版本没有可定位的结构化修改。/u);
});

test('标准化流程说明不把权威等级和血缘等实现术语带入业务页面', () => {
  const page = readFileSync(new URL('./data-standardization-page.tsx', import.meta.url), 'utf8');
  assert.match(page, /比对来源结论，找出一致、差异和待确认事项。/u);
  assert.doesNotMatch(page, /权威等级、血缘/u);
});

test('旧入口也使用业务化的进度和错误文案，不直接透传底层错误', () => {
  const page = readFileSync(new URL('./data-standardization-page.tsx', import.meta.url), 'utf8');
  const workspace = readFileSync(new URL('./source-document-workspace.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(page, /快照身份、版本、范围与内容指纹|校验证据引用、章节、主要问题和内容指纹/u);
  assert.match(page, /确认资料范围和可读取状态。/u);
  assert.match(page, /检查资料、结论和审阅文档是否对应。/u);
  assert.doesNotMatch(page, /message\.error\(error/u);
  assert.doesNotMatch(workspace, /message\.error\(error/u);
  assert.match(workspace, /function presentWorkspaceError/u);
});
