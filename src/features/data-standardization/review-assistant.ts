export type ReviewAssistantIntent =
  | {
      kind: 'PROPOSE_BLOCK_CHANGE';
      field: 'label' | 'value';
      value: string;
      normalizedMessage: string;
    }
  | {
      kind: 'OPEN_TARGET' | 'AFFECTED_OBJECTS' | 'EVIDENCE' | 'CONFLICT'
        | 'SOURCE' | 'BOUNDARY' | 'SUPPORTED_SCOPE';
      normalizedMessage: string;
    };

export function reviewAssistantDiffWindow<T extends { type: string }>(
  lines: readonly T[],
  limit = 120,
) {
  const safeLimit = Math.max(1, Math.trunc(limit));
  const changedIndexes = lines.flatMap((line, index) => line.type === 'UNCHANGED' ? [] : [index]);
  const center = changedIndexes.length
    ? Math.floor((changedIndexes[0]! + changedIndexes.at(-1)!) / 2)
    : 0;
  const start = Math.max(0, Math.min(
    Math.max(0, lines.length - safeLimit),
    center - Math.floor(safeLimit / 2),
  ));
  const end = Math.min(lines.length, start + safeLimit);
  return {
    lines: lines.slice(start, end),
    omittedBefore: start,
    omittedAfter: lines.length - end,
  };
}

export function normalizeReviewAssistantMessage(message: string) {
  const normalized = message.normalize('NFKC').trim().replace(/\s+/gu, ' ');
  if (!normalized) throw new Error('审阅助手消息不能为空');
  if ([...normalized].length > 4000) throw new Error('审阅助手消息最多4000个字符');
  return normalized;
}

export function classifyReviewAssistantIntent(message: string): ReviewAssistantIntent {
  const normalizedMessage = normalizeReviewAssistantMessage(message);
  const proposal = /^建议把当前项(名称|识别结果)改为:\s*(.+)$/u.exec(normalizedMessage);
  if (proposal) {
    return {
      kind: 'PROPOSE_BLOCK_CHANGE',
      field: proposal[1] === '名称' ? 'label' : 'value',
      value: proposal[2]!.trim(),
      normalizedMessage,
    };
  }
  if (/打开|定位|跳到|查看/u.test(normalizedMessage)) return { kind: 'OPEN_TARGET', normalizedMessage };
  if (/直接修改|替我应用|解决冲突|冻结|交付|发布|触发\s*M4|确认|同意|应用/iu.test(normalizedMessage)) {
    return { kind: 'BOUNDARY', normalizedMessage };
  }
  if (/影响哪些对象|影响对象|影响什么/u.test(normalizedMessage)) {
    return { kind: 'AFFECTED_OBJECTS', normalizedMessage };
  }
  if (/依据|证据|为什么/u.test(normalizedMessage)) return { kind: 'EVIDENCE', normalizedMessage };
  if (/冲突|差异/u.test(normalizedMessage)) return { kind: 'CONFLICT', normalizedMessage };
  if (/来源|快照|Commit|读取范围/iu.test(normalizedMessage)) return { kind: 'SOURCE', normalizedMessage };
  return { kind: 'SUPPORTED_SCOPE', normalizedMessage };
}

import type {
  ReviewAssistantContextSelection,
  ReviewAssistantKnowledge,
  ReviewAssistantResponse,
  ReviewAssistantSourceFact,
  ReviewAssistantTarget,
} from './review-assistant-types.ts';

function sourceForSelection(
  knowledge: ReviewAssistantKnowledge,
  selection: ReviewAssistantContextSelection,
) {
  return knowledge.sources.find((source) => source.sourceId === selection.sourceId)
    ?? knowledge.sources.find((source) => source.documentId === selection.documentId)
    ?? knowledge.sources.find((source) => source.sourceId === knowledge.currentSourceId);
}

function sourceTarget(source: ReviewAssistantSourceFact): ReviewAssistantTarget | undefined {
  return source.documentId ? { kind: 'SOURCE_DOCUMENT', documentId: source.documentId } : undefined;
}

function boundedBody(values: string[]) {
  return values.slice(0, 8).map((value) => [...value].slice(0, 240).join(''));
}

export function answerReviewAssistant(input: {
  intent: ReviewAssistantIntent;
  selection: ReviewAssistantContextSelection;
  knowledge: ReviewAssistantKnowledge;
}): ReviewAssistantResponse {
  const { intent, selection, knowledge } = input;
  if (intent.kind === 'BOUNDARY') {
    return {
      kind: 'BOUNDARY',
      title: '请在当前页面完成这一步',
      body: ['本演示中的可修改内容会显示在“审阅清单”的建议核对任务中；来源差异和定版也需要在对应页面确认。我可以解释结论、定位来源并说明影响。'],
    };
  }
  if (intent.kind === 'SUPPORTED_SCOPE') {
    return {
      kind: 'SUPPORTED_SCOPE',
      title: '我可以协助当前审阅',
      body: ['可以问我当前结论、来源依据、资料差异或业务影响，也可以让我定位当前内容。本演示可修改的项目会直接显示在审阅清单中。'],
    };
  }
  const source = sourceForSelection(knowledge, selection);
  if (intent.kind === 'SOURCE') {
    const candidates = source ? [source] : knowledge.sources.slice(0, 3);
    return {
      kind: 'SOURCE_EXPLANATION',
      title: source ? source.sourceName : '可解释的当前来源',
      body: boundedBody(candidates.map((candidate) => (
        `${candidate.sourceName}：${candidate.readSummary}。`
      ))),
      targets: candidates.flatMap((candidate) => sourceTarget(candidate) ?? []),
    };
  }
  const conflict = knowledge.conflicts.find((candidate) => candidate.conflictId === selection.conflictId)
    ?? knowledge.conflicts.find((candidate) => candidate.conflictId === knowledge.currentConflictId);
  if (intent.kind === 'CONFLICT') {
    if (!conflict) return answerReviewAssistant({ ...input, intent: { kind: 'SUPPORTED_SCOPE', normalizedMessage: intent.normalizedMessage } });
    const sides = conflict.hunk
      ? [`当前来源：${conflict.hunk.current.sourceName}；新来源：${conflict.hunk.incoming.sourceName}。`]
      : ['该差异已经记录在当前运行中，可从主区查看决定历史。'];
    return {
      kind: 'CONFLICT_EXPLANATION', title: conflict.title,
      body: boundedBody(sides), targets: [{ kind: 'CONFLICT', conflictId: conflict.conflictId }],
    };
  }
  const block = source?.blocks.find((candidate) => candidate.blockId === selection.blockId)
    ?? source?.blocks.find((candidate) => candidate.blockId === knowledge.currentBlockId);
  if (intent.kind === 'EVIDENCE') {
    const evidenceRef = selection.evidenceRef ?? block?.evidenceRefs[0];
    if (!source || !block || !evidenceRef || !source.evidenceLocators[evidenceRef]) {
      return answerReviewAssistant({ ...input, intent: { kind: 'SUPPORTED_SCOPE', normalizedMessage: intent.normalizedMessage } });
    }
    return {
      kind: 'EVIDENCE_EXPLANATION', title: `${block.label}的依据`,
      body: boundedBody([
        `这份材料来自${source.sourceName}，支持“${block.label}”这条审阅结论。`,
        '已在来源资料区定位到对应材料和位置。',
      ]),
      targets: [{ kind: 'EVIDENCE', sourceId: source.sourceId, blockId: block.blockId, evidenceRef }],
    };
  }
  if (intent.kind === 'AFFECTED_OBJECTS') {
    const refs = selection.objectRef ? [selection.objectRef]
      : block?.affectedObjectRefs ?? conflict?.affectedObjectRefs ?? [];
    return {
      kind: 'AFFECTED_OBJECTS', title: '影响对象',
      body: boundedBody(refs.length
        ? [`当前结论关联 ${refs.length} 个业务对象；已在来源资料区显示可继续查看的对象。`]
        : ['当前选择没有直接关联的业务对象。']),
      targets: refs.slice(0, 3).map((objectRef) => ({ kind: 'AFFECTED_OBJECT', objectRef })),
    };
  }
  if (intent.kind === 'OPEN_TARGET') {
    let target: ReviewAssistantTarget | undefined;
    if (selection.objectRef) target = { kind: 'AFFECTED_OBJECT', objectRef: selection.objectRef };
    else if (selection.evidenceRef && selection.sourceId && selection.blockId) {
      target = { kind: 'EVIDENCE', sourceId: selection.sourceId, blockId: selection.blockId, evidenceRef: selection.evidenceRef };
    } else if (selection.conflictId || knowledge.currentConflictId) {
      target = { kind: 'CONFLICT', conflictId: selection.conflictId ?? knowledge.currentConflictId! };
    } else if (source?.documentId) {
      target = {
        kind: 'SOURCE_DOCUMENT', documentId: source.documentId,
        ...(selection.section ? { section: selection.section } : {}),
        ...(selection.blockId ? { blockId: selection.blockId } : {}),
      };
    }
    if (!target) return answerReviewAssistant({ ...input, intent: { kind: 'SUPPORTED_SCOPE', normalizedMessage: intent.normalizedMessage } });
    if (target.kind === 'SOURCE_DOCUMENT' && target.documentId !== knowledge.currentDocumentId) {
      return {
        kind: 'OPEN_TARGET', title: '已定位来源文档回执',
        body: ['该来源已完成审阅；已在时间线定位其来源文档回执。'], target,
      };
    }
    return { kind: 'OPEN_TARGET', title: '已定位当前目标', body: ['目标已在当前工作台中打开并获得可见焦点。'], target };
  }
  return answerReviewAssistant({ ...input, intent: { kind: 'SUPPORTED_SCOPE', normalizedMessage: intent.normalizedMessage } });
}
