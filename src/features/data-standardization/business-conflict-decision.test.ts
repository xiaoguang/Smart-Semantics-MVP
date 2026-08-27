import assert from 'node:assert/strict';
import test from 'node:test';
import {
  businessConflictDecisionOptions,
  legacyStrategyForBusinessDecision,
  projectBusinessMarkdownDiff,
  resolutionSummaryForBusinessDecision,
} from './business-conflict-decision.ts';
import type { ConflictResolutionPreview } from '../guanyijia-standardization-story/types.ts';

function conflictPreviewForBusinessDiff(): ConflictResolutionPreview {
  const current = {
    blockId: 'current-debt',
    section: 'UNRESOLVED' as const,
    semanticKind: 'GAP' as const,
    stableCode: 'debt.current',
    label: '部署欠款字段基准',
    value: { normalized: 'DEPLOYED', text: '当前部署单据主表不包含欠款、期末欠款和期末订金字段。' },
    evidenceStatus: 'OBSERVED' as const,
    evidenceRefs: ['mysql:table:jsh_depot_head'],
    affectedObjectRefs: [],
  };
  const incoming = {
    ...current,
    blockId: 'incoming-debt',
    stableCode: 'debt.incoming',
    label: '源码欠款字段定义',
    value: { normalized: 'SOURCE', text: '固定源码记录包含欠款、期末欠款和期末订金字段。' },
    evidenceRefs: ['github:schema:debt-fields'],
  };
  const gap = {
    ...current,
    blockId: 'gap-debt',
    stableCode: 'debt.gap',
    label: '欠款字段结构冲突待确认',
    value: { normalized: 'GAP', text: '当前差异暂不形成正式结论，保留为待确认缺口。' },
    evidenceStatus: 'GAP' as const,
    evidenceRefs: ['mysql:table:jsh_depot_head', 'github:schema:debt-fields'],
  };
  return {
    hunk: {
      conflictId: 'gyj-conflict-debt-schema',
      title: '欠款字段结构冲突',
      sections: ['UNRESOLVED'],
      current: { role: 'CURRENT', sourceId: 'guanyijia_mysql', sourceName: '数据库', sourceClass: 'OBSERVED', authority: 'PRIMARY', documentId: 'mysql-r1', documentRevision: 1, block: current, assertion: { assertionId: current.blockId, section: 'UNRESOLVED', statement: current.value.text, provenance: 'OBSERVED', evidenceRefs: current.evidenceRefs } },
      incoming: { role: 'INCOMING', sourceId: 'guanyijia_github', sourceName: '代码仓库', sourceClass: 'FROZEN_RECORD', authority: 'SECONDARY', documentId: 'github-r1', documentRevision: 1, block: incoming, assertion: { assertionId: incoming.blockId, section: 'UNRESOLVED', statement: incoming.value.text, provenance: 'OBSERVED', evidenceRefs: incoming.evidenceRefs } },
      corroborating: [],
      affectedObjectRefs: [],
      hunkSha256: 'sha256:test',
    },
    strategy: 'DEFER_AS_GAP',
    result: { blocks: [current, gap], assertions: [], objectDispositions: [] },
    structuredPatch: { schemaVersion: 1, conflictId: 'gyj-conflict-debt-schema', blockOperations: [], assertionOperations: [], objectDispositionOperations: [] },
    markdownDiff: [],
    provenanceSources: [],
    previewSha256: 'sha256:test',
  };
}

test('registering a gap is the default business decision and keeps legacy mappings private', () => {
  for (const conflictId of [
    'gyj-conflict-negative-stock',
    'gyj-conflict-debt-schema',
    'gyj-conflict-status-nine',
  ]) {
    const options = businessConflictDecisionOptions({
      conflictId,
      allowedStrategies: ['KEEP_CURRENT', 'ACCEPT_INCOMING', 'MERGE', 'DEFER_AS_GAP'],
    });
    assert.deepEqual(options.map((option) => option.decision), [
      'KEEP_CURRENT', 'ACCEPT_INCOMING', 'REGISTER_GAP',
    ]);
    assert.equal(options.find((option) => option.decision === 'REGISTER_GAP')?.default, true);
  }

  assert.equal(legacyStrategyForBusinessDecision('gyj-conflict-negative-stock', 'REGISTER_GAP'), 'MERGE');
  assert.equal(legacyStrategyForBusinessDecision('gyj-conflict-debt-schema', 'REGISTER_GAP'), 'DEFER_AS_GAP');
  assert.equal(legacyStrategyForBusinessDecision('gyj-conflict-status-nine', 'REGISTER_GAP'), 'DEFER_AS_GAP');
});

test('conflict result summary is business-readable and never leaks a strategy enum or raw object id', () => {
  const summary = resolutionSummaryForBusinessDecision({
    conflictId: 'gyj-conflict-debt-schema',
    decision: 'REGISTER_GAP',
  });
  assert.match(summary.title, /当前结论继续有效/u);
  assert.match(summary.gap, /欠款字段/u);
  assert.match(summary.impact, /应收指标/u);
  assert.doesNotMatch(`${summary.title} ${summary.gap} ${summary.impact}`, /DEFER|MERGE|receivable_debt/u);
});

test('business Markdown diff previews each decision without leaking internal strategy or object values', () => {
  const preview = conflictPreviewForBusinessDiff();
  const keep = projectBusinessMarkdownDiff({ decision: 'KEEP_CURRENT', preview });
  const accept = projectBusinessMarkdownDiff({ decision: 'ACCEPT_INCOMING', preview: {
    ...preview,
    strategy: 'ACCEPT_INCOMING',
    result: { ...preview.result, blocks: [preview.hunk.incoming.block] },
  } });
  const gap = projectBusinessMarkdownDiff({ decision: 'REGISTER_GAP', preview });

  assert.equal(keep.changed, false);
  assert.equal(keep.lines.every((line) => line.kind === 'RETAINED'), true);
  assert.equal(accept.changed, true);
  assert.equal(accept.lines.some((line) => line.kind === 'REMOVED'), true);
  assert.equal(accept.lines.some((line) => line.kind === 'ADDED'), true);
  assert.equal(gap.lines.some((line) => line.kind === 'REMOVED'), false);
  assert.equal(gap.lines.some((line) => line.kind === 'ADDED' && /待确认缺口/u.test(line.line)), true);

  const output = [keep, accept, gap].flatMap((projection) => projection.lines.map((line) => line.line)).join('\n');
  assert.doesNotMatch(output, /sha256:|gyj-|METRIC:|RULE:|KEEP_CURRENT|ACCEPT_INCOMING|MERGE|DEFER_AS_GAP|DEFERRED|EXCLUDED|CANDIDATE_ONLY/u);
});

test('keeping the current conclusion still marks the exact retained Markdown paragraph for review', () => {
  const keep = projectBusinessMarkdownDiff({
    decision: 'KEEP_CURRENT',
    preview: conflictPreviewForBusinessDiff(),
  });

  assert.equal(keep.changed, false);
  assert.ok(keep.lines.some((line) => line.kind === 'RETAINED'
    && line.line.includes('当前部署单据主表不包含欠款')),
  '保留当前结论时必须展示继续生效的具体段落，而不是空白预览');
});
