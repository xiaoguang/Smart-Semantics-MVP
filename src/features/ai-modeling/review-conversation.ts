import type { ReviewBatchView, ReviewGroupView } from './review-batch.ts';

export type ReviewIntent =
  | { type: 'CONFIRM_GROUP'; groupId: string; reason: string }
  | { type: 'REJECT_ITEM'; itemId: string; reason: string }
  | { type: 'PUBLISH'; reason: string }
  | { type: 'NEEDS_CLARIFICATION'; candidates?: Array<{ itemId: string; name: string }> };

export type ReviewChoice = {
  id: string;
  label: string;
  description: string;
  recommended?: boolean;
  intent: ReviewIntent;
};

type PromptBase = {
  heading: string;
  description: string;
  choices: ReviewChoice[];
};

export type ReviewPrompt =
  | (PromptBase & { kind: 'GROUP'; recommendedReason: string })
  | (PromptBase & { kind: 'PUBLISH'; recommendedReason: string });

type PromptInput =
  | { kind: 'GROUP'; batch: ReviewBatchView; group: ReviewGroupView }
  | {
      kind: 'PUBLISH'; documentVersion: string; modelVersion: string; rejectedCount: number;
    };

export function projectReviewPrompt(input: PromptInput): ReviewPrompt {
  if (input.kind === 'PUBLISH') {
    const recommendedReason = `确认资料${input.documentVersion}的全部审核决定和异议汇总，发布不可变模型${input.modelVersion}`;
    return {
      kind: 'PUBLISH',
      heading: `发布模型 ${input.modelVersion}`,
      description: input.rejectedCount > 0
        ? `当前有 ${input.rejectedCount} 项异议会随版本保留。发布时将确认模型可以正常连接、计算和追溯。`
        : '发布时将确认模型可以正常连接、计算和追溯；全部通过后生成不可变模型版本。',
      recommendedReason,
      choices: [
        {
          id: 'publish', label: '确认发布', description: recommendedReason, recommended: true,
          intent: { type: 'PUBLISH', reason: recommendedReason },
        },
      ],
    };
  }
  const manualCount = input.group.items.length;
  const recommendedReason = `确认${input.group.name}中的${manualCount}项对象符合资料定义、字段归属和关系依赖，加入当前个人草稿`;
  return {
    kind: 'GROUP',
    heading: `${input.group.name} · ${manualCount} 项`,
    description: input.group.purpose,
    recommendedReason,
    choices: [
      {
        id: 'confirm-group',
        label: '将本组剩余项加入草稿',
        description: recommendedReason,
        recommended: true,
        intent: { type: 'CONFIRM_GROUP', groupId: input.group.id, reason: recommendedReason },
      },
    ],
  };
}

export function interpretReviewInput(input: string, prompt: ReviewPrompt): ReviewIntent {
  const normalized = input.trim();
  const customReason = normalized.match(/理由[:：](.+)$/)?.[1]?.trim();
  if (prompt.kind === 'PUBLISH') {
    if (/暂不|稍后|取消|检查|校验|详情/.test(normalized)) return { type: 'NEEDS_CLARIFICATION' };
    if (/确认发布|立即发布|发布/.test(normalized)) {
      return customReason ? { type: 'PUBLISH', reason: customReason } : prompt.choices[0].intent;
    }
    return { type: 'NEEDS_CLARIFICATION' };
  }
  if (/采纳|同意|确认本组|使用.*理由/.test(normalized)) {
    return customReason
      ? { type: 'CONFIRM_GROUP', groupId: prompt.choices[0].intent.type === 'CONFIRM_GROUP' ? prompt.choices[0].intent.groupId : '', reason: customReason }
      : prompt.choices[0].intent;
  }
  return { type: 'NEEDS_CLARIFICATION' };
}

export function resolveObjectionInput(input: string, batch: ReviewBatchView): ReviewIntent {
  const items = batch.groups.flatMap((group) => group.items);
  const matches = items.filter((item) => input.includes(item.itemId) || input.includes(item.name));
  if (matches.length !== 1) {
    return {
      type: 'NEEDS_CLARIFICATION',
      ...(matches.length > 1
        ? { candidates: matches.map((item) => ({ itemId: item.itemId, name: item.name })) }
        : {}),
    };
  }
  const reason = input.match(/(?:因为|理由[:：]?)(.+)$/)?.[1]?.trim();
  if (!reason || !/[\u3400-\u9fff]/.test(reason)) {
    return { type: 'NEEDS_CLARIFICATION', candidates: [{ itemId: matches[0].itemId, name: matches[0].name }] };
  }
  return { type: 'REJECT_ITEM', itemId: matches[0].itemId, reason };
}
