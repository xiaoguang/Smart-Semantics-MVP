import type { SourceDocumentBlock, StructuredValue } from '../source-documents/types.ts';
import type { SourceReviewDocument } from '../guanyijia-evidence-factory/source-review-document.ts';

export type ScriptedReviewEditableField = 'label' | 'text';
export type ScriptedReviewLockedField =
  | 'normalized'
  | 'evidenceRefs'
  | 'affectedObjectRefs'
  | 'stableCode';

export type ScriptedReviewEditDefinition = {
  editId: string;
  sourceId: string;
  sourceSnapshotId: string;
  reviewClaimId: string;
  formalBlockId: string;
  reviewEvidenceRefs: string[];
  formalEvidenceRefs: string[];
  editableFields: ScriptedReviewEditableField[];
  suggestedPatch: {
    label?: string;
    text?: string;
  };
  /**
   * Some formal labels include a database identifier.  The initial review task
   * may use a shorter business name, but a later reviewer-supplied title must
   * always remain verbatim.
   */
  initialDisplay?: {
    claimTitle: string;
    title: string;
  };
  lockedFields: ScriptedReviewLockedField[];
  expectedStableCode: string;
  expectedNormalized?: string;
};

export type ScriptedReviewDecision = 'KEEP_CURRENT' | 'APPLY_SUGGESTION';

export type ScriptedReviewPatch = {
  label?: string;
  text?: string;
};

export type ScriptedReviewBlockChange = {
  blockId: string;
  label?: string;
  value?: StructuredValue;
};

const definitions: readonly ScriptedReviewEditDefinition[] = [
  {
    editId: 'scripted:mysql:rename-depot-head',
    sourceId: 'guanyijia_mysql',
    sourceSnapshotId: '20260813T032528Z-abb0502c7d79',
    reviewClaimId: 'claim:guanyijia_mysql:curated-e005',
    formalBlockId: 'gyj-block:physical.table.jsh_depot_head',
    reviewEvidenceRefs: ['mysql:table:jsh_depot_head'],
    formalEvidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_depot_head'],
    editableFields: ['label'],
    suggestedPatch: { label: '库存单据表头（jsh_depot_head）' },
    initialDisplay: {
      claimTitle: '库存单据主表（jsh_depot_head）',
      title: '库存单据主表',
    },
    lockedFields: ['normalized', 'evidenceRefs', 'affectedObjectRefs', 'stableCode'],
    expectedStableCode: 'physical.table.jsh_depot_head',
    expectedNormalized: 'DEPLOYED_TABLE_PRESENT',
  },
  {
    editId: 'scripted:github:clarify-negative-stock',
    sourceId: 'guanyijia_github',
    sourceSnapshotId: '20260813032126Z-5821d0ece9b1',
    reviewClaimId: 'claim:guanyijia_github:github-v5-github-v5-003',
    formalBlockId: 'gyj-block:rule.negative_stock',
    reviewEvidenceRefs: ['github:v5:005', 'github:v5:006'],
    formalEvidenceRefs: [
      'github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:METHOD:getMinusStockFlag:511',
    ],
    editableFields: ['label', 'text'],
    suggestedPatch: {
      label: '租户级负库存控制',
      text: '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。',
    },
    lockedFields: ['normalized', 'evidenceRefs', 'affectedObjectRefs', 'stableCode'],
    expectedStableCode: 'rule.negative_stock',
    expectedNormalized: 'TENANT_CONFIG_CONTROLS',
  },
] as const;

function failBinding(): never {
  throw new Error('剧本编辑映射校验失败');
}

function isRecord(value: StructuredValue): value is Record<string, StructuredValue> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function sameStrings(left: readonly string[], right: readonly string[]) {
  return left.length === right.length && left.every((value) => right.includes(value));
}

function validateDefinition(definition: ScriptedReviewEditDefinition) {
  if (!definition.editId || !definition.sourceId || !definition.sourceSnapshotId
    || !definition.reviewClaimId || !definition.formalBlockId || !definition.expectedStableCode
    || definition.reviewEvidenceRefs.length === 0 || definition.formalEvidenceRefs.length === 0
    || definition.editableFields.length === 0 || definition.lockedFields.length === 0
    || new Set(definition.editableFields).size !== definition.editableFields.length
    || new Set(definition.lockedFields).size !== definition.lockedFields.length
    || definition.editableFields.some((field) => !['label', 'text'].includes(field))
    || definition.lockedFields.some((field) => !['normalized', 'evidenceRefs', 'affectedObjectRefs', 'stableCode'].includes(field))) {
    failBinding();
  }
  if (definition.editableFields.includes('label') && !definition.suggestedPatch.label?.trim()) failBinding();
  if (definition.editableFields.includes('text') && !definition.suggestedPatch.text?.trim()) failBinding();
  if (definition.initialDisplay
    && (!definition.initialDisplay.claimTitle.trim() || !definition.initialDisplay.title.trim())) failBinding();
}

/** Returns immutable Demo scenarios; no caller can mutate their evidence identity. */
export function listScriptedReviewEdits(input?: { sourceId?: string }) {
  const values = input?.sourceId
    ? definitions.filter((definition) => definition.sourceId === input.sourceId)
    : definitions;
  values.forEach(validateDefinition);
  return structuredClone(values);
}

export function findScriptedReviewEdit(editId: string) {
  const definition = definitions.find((candidate) => candidate.editId === editId);
  if (!definition) return undefined;
  validateDefinition(definition);
  return structuredClone(definition);
}

/** Fails closed unless the visible claim and formal block are the exact scenario pair. */
export function validateScriptedReviewEditBinding(input: {
  definition: ScriptedReviewEditDefinition;
  review: SourceReviewDocument;
  block: SourceDocumentBlock;
}) {
  const { definition, review, block } = input;
  validateDefinition(definition);
  if (review.sourceId !== definition.sourceId || review.snapshotId !== definition.sourceSnapshotId
    || block.blockId !== definition.formalBlockId
    || block.stableCode !== definition.expectedStableCode
    || !sameStrings(block.evidenceRefs, definition.formalEvidenceRefs)) {
    failBinding();
  }
  const claim = review.items.find((item) => item.id === definition.reviewClaimId);
  if (!claim || claim.formalBlockId !== definition.formalBlockId
    || claim.scriptedEditId !== definition.editId
    || !sameStrings(claim.evidenceRefs, definition.reviewEvidenceRefs)) {
    failBinding();
  }
  if (definition.expectedNormalized !== undefined) {
    if (!isRecord(block.value) || block.value.normalized !== definition.expectedNormalized) failBinding();
  }
}

function assertAllowedPatch(definition: ScriptedReviewEditDefinition, patch: ScriptedReviewPatch) {
  if (patch.label !== undefined && !definition.editableFields.includes('label')) {
    throw new Error('当前剧本不允许修改名称');
  }
  if (patch.text !== undefined && !definition.editableFields.includes('text')) {
    throw new Error('当前剧本不允许修改正文');
  }
  if (patch.label !== undefined && !patch.label.trim()) throw new Error('名称不能为空');
  if (patch.text !== undefined && !patch.text.trim()) throw new Error('说明不能为空');
}

/** Converts only the permitted text fields into the existing revision-chain change shape. */
export function buildScriptedReviewBlockChange(
  definition: ScriptedReviewEditDefinition,
  block: SourceDocumentBlock,
  patch: ScriptedReviewPatch,
): ScriptedReviewBlockChange {
  validateDefinition(definition);
  if (block.blockId !== definition.formalBlockId || block.stableCode !== definition.expectedStableCode
    || !sameStrings(block.evidenceRefs, definition.formalEvidenceRefs)) failBinding();
  assertAllowedPatch(definition, patch);
  const merged = {
    ...(definition.editableFields.includes('label') ? { label: patch.label ?? definition.suggestedPatch.label } : {}),
    ...(definition.editableFields.includes('text') ? { text: patch.text ?? definition.suggestedPatch.text } : {}),
  };
  if (merged.label !== undefined && !merged.label.trim()) failBinding();
  if (merged.text !== undefined && !merged.text.trim()) failBinding();

  if (merged.text === undefined) {
    return { blockId: block.blockId, ...(merged.label ? { label: merged.label.trim() } : {}) };
  }
  if (!isRecord(block.value) || (definition.expectedNormalized !== undefined
    && block.value.normalized !== definition.expectedNormalized)) failBinding();
  return {
    blockId: block.blockId,
    ...(merged.label ? { label: merged.label.trim() } : {}),
    value: { ...block.value, text: merged.text.trim() },
  };
}
