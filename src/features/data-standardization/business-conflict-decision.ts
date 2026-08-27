import { diffSourceDocumentLines } from '../source-documents/runtime.ts';
import { sourceDocumentBlockHumanText } from '../source-documents/structured-projection.ts';
import type {
  ConflictResolutionPreview,
  ConflictResolutionStrategy,
} from '../guanyijia-standardization-story/types.ts';

/** The three choices a reviewer should understand. */
export type BusinessConflictDecision =
  | 'KEEP_CURRENT'
  | 'ACCEPT_INCOMING'
  | 'REGISTER_GAP';

export type BusinessConflictDecisionOption = {
  decision: BusinessConflictDecision;
  label: string;
  default: boolean;
};

/** A user-facing Git-style preview without artifact strategies or object IDs. */
export type BusinessMarkdownDiffProjection = {
  decision: BusinessConflictDecision;
  changed: boolean;
  lines: Array<{
    kind: 'RETAINED' | 'REMOVED' | 'ADDED' | 'UNCHANGED';
    line: string;
  }>;
};

function renderBusinessConflictMarkdown(input: {
  title: string;
  blocks: ConflictResolutionPreview['result']['blocks'];
}) {
  return [
    `# ${input.title}`,
    ...input.blocks.map((block) => `- ${block.label}：${sourceDocumentBlockHumanText(block.value)}`),
  ].join('\n');
}

export function projectBusinessMarkdownDiff(input: {
  decision: BusinessConflictDecision;
  preview: ConflictResolutionPreview;
}): BusinessMarkdownDiffProjection {
  const before = renderBusinessConflictMarkdown({
    title: input.preview.hunk.title,
    blocks: [input.preview.hunk.current.block],
  });
  const after = renderBusinessConflictMarkdown({
    title: input.preview.hunk.title,
    blocks: input.decision === 'KEEP_CURRENT'
      ? [input.preview.hunk.current.block]
      : input.preview.result.blocks,
  });
  const lines: BusinessMarkdownDiffProjection['lines'] = diffSourceDocumentLines(before, after).map((line) => ({
    kind: line.type === 'REMOVED'
      ? 'REMOVED'
      : line.type === 'ADDED'
        ? 'ADDED'
        : input.decision === 'KEEP_CURRENT' || input.decision === 'REGISTER_GAP'
          ? 'RETAINED'
          : 'UNCHANGED',
    line: line.line,
  }));
  return {
    decision: input.decision,
    changed: lines.some((line) => line.kind === 'ADDED' || line.kind === 'REMOVED'),
    lines,
  };
}

const visibleOptions: readonly BusinessConflictDecisionOption[] = [
  { decision: 'KEEP_CURRENT', label: '保留当前结论', default: false },
  { decision: 'ACCEPT_INCOMING', label: '采用新来源结论', default: false },
  { decision: 'REGISTER_GAP', label: '登记为缺口', default: true },
];

/**
 * The formal artifact has two historical "gap" encodings.  They remain
 * necessary for V1-compatible projection and zero-delta validation, but they
 * are not separate reviewer choices.
 */
export function legacyStrategyForBusinessDecision(
  conflictId: string,
  decision: BusinessConflictDecision,
): ConflictResolutionStrategy {
  if (decision === 'KEEP_CURRENT') return 'KEEP_CURRENT';
  if (decision === 'ACCEPT_INCOMING') return 'ACCEPT_INCOMING';
  return conflictId === 'gyj-conflict-negative-stock' ? 'MERGE' : 'DEFER_AS_GAP';
}

export function businessConflictDecisionOptions(input: {
  conflictId: string;
  allowedStrategies: readonly ConflictResolutionStrategy[];
}): BusinessConflictDecisionOption[] {
  return visibleOptions.filter((option) => input.allowedStrategies.includes(
    legacyStrategyForBusinessDecision(input.conflictId, option.decision),
  ));
}

export function auditReasonForBusinessDecision(input: {
  conflictId: string;
  decision: BusinessConflictDecision;
}) {
  const decision = input.decision === 'KEEP_CURRENT'
    ? '审阅者保留当前结论。'
    : input.decision === 'ACCEPT_INCOMING'
      ? '审阅者采用新来源结论。'
      : '审阅者将当前差异登记为待确认缺口。';
  const topic = input.conflictId === 'gyj-conflict-negative-stock'
    ? '负库存控制'
    : input.conflictId === 'gyj-conflict-debt-schema'
      ? '欠款字段'
      : input.conflictId === 'gyj-conflict-status-nine'
        ? '状态 9 含义'
        : '来源差异';
  return `${topic}：${decision}`;
}

export function resolutionSummaryForBusinessDecision(input: {
  conflictId: string;
  decision: BusinessConflictDecision;
}) {
  if (input.decision === 'KEEP_CURRENT') {
    return {
      title: '当前结论继续有效',
      gap: '新来源内容已保留为后续比对资料。',
      impact: '正式模型保持当前范围。',
    };
  }
  if (input.decision === 'ACCEPT_INCOMING') {
    return {
      title: '已采用新来源结论',
      gap: '原有结论保留在审计记录中，便于后续复核。',
      impact: '相关审阅文档会采用这条结论；正式模型本次未改变。',
    };
  }
  if (input.conflictId === 'gyj-conflict-debt-schema') {
    return {
      title: '当前结论继续有效',
      gap: '新增缺口：欠款字段尚未在部署结构中确认。',
      impact: '应收指标暂不进入正式模型。',
    };
  }
  if (input.conflictId === 'gyj-conflict-status-nine') {
    return {
      title: '当前结论继续有效',
      gap: '新增缺口：状态 9 的正式业务含义尚未确认。',
      impact: '状态 9 暂不作为正式状态口径发布。',
    };
  }
  return {
    title: '当前实现结论继续有效',
    gap: '新增缺口：统一禁止负库存的制度目标尚未确认落地范围。',
    impact: '现有租户级控制保持不变，制度目标继续等待确认。',
  };
}
