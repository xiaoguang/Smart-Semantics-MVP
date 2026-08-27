import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import { diffSourceDocumentLines } from '../source-documents/runtime.ts';
import { sourceDocumentBlockHumanText } from '../source-documents/structured-projection.ts';
import type {
  ConflictObjectDisposition,
  ConflictHunk,
  ConflictHunkSide,
  ConflictProvenanceSource,
  ConflictResolutionPreview,
  ConflictResolutionStrategy,
  ConflictSourceRevision,
  ConflictVariantDefinition,
  SemanticConflictDefinition,
  StorySource,
} from './types.ts';

function normalizedValue(block: ConflictHunkSide['block']) {
  if (typeof block.value !== 'object' || block.value === null || Array.isArray(block.value)) return null;
  return typeof block.value.normalized === 'string' ? block.value.normalized : null;
}

function sideFor(input: {
  role: ConflictHunkSide['role'];
  definition: ConflictVariantDefinition;
  sources: StorySource[];
  revisions: ConflictSourceRevision[];
}): ConflictHunkSide {
  const source = input.sources.find((candidate) => candidate.sourceId === input.definition.sourceId);
  const revision = input.revisions.find((candidate) => (
    candidate.compilation.sourceId === input.definition.sourceId
  ));
  if (!source || !revision) throw new Error(`冲突缺少已持久化来源revision：${input.definition.sourceId}`);
  if (!revision.documentId.trim() || !Number.isSafeInteger(revision.documentRevision)
    || revision.documentRevision < 1) {
    throw new Error(`冲突来源revision身份无效：${input.definition.sourceId}`);
  }
  const blocks = revision.compilation.blocks.filter((candidate) => (
    candidate.stableCode === input.definition.stableCode
    && normalizedValue(candidate) === input.definition.normalizedValue
    && canonicalModelingJson(candidate.evidenceRefs) === canonicalModelingJson(input.definition.evidenceRefs)
  ));
  if (blocks.length !== 1) {
    throw new Error(`冲突来源variant无法精确定位：${input.definition.sourceId}/${input.definition.stableCode}`);
  }
  const block = blocks[0]!;
  const assertions = revision.compilation.assertions.filter((candidate) => (
    candidate.assertionId === block.blockId
    && candidate.section === block.section
    && canonicalModelingJson(candidate.evidenceRefs) === canonicalModelingJson(block.evidenceRefs)
  ));
  if (assertions.length !== 1) {
    throw new Error(`冲突来源断言无法精确定位：${input.definition.sourceId}/${input.definition.stableCode}`);
  }
  return {
    role: input.role,
    sourceId: source.sourceId,
    sourceName: source.sourceName,
    sourceClass: source.sourceClass,
    authority: source.authority,
    documentId: revision.documentId,
    documentRevision: revision.documentRevision,
    block: structuredClone(block),
    assertion: structuredClone(assertions[0]!),
  };
}

export function buildConflictHunk(input: {
  conflictId: string;
  sourceRevisions: ConflictSourceRevision[];
  sources: StorySource[];
  definitions: SemanticConflictDefinition[];
}): ConflictHunk {
  const definition = input.definitions.find((candidate) => candidate.conflictId === input.conflictId);
  if (!definition) throw new Error(`未知管伊佳来源冲突：${input.conflictId}`);
  const current = sideFor({
    role: 'CURRENT', definition: definition.current,
    sources: input.sources, revisions: input.sourceRevisions,
  });
  const incoming = sideFor({
    role: 'INCOMING', definition: definition.incoming,
    sources: input.sources, revisions: input.sourceRevisions,
  });
  const availableSourceIds = new Set(input.sourceRevisions.map((revision) => revision.compilation.sourceId));
  const corroborating = definition.corroborating.flatMap((candidate) => (
    availableSourceIds.has(candidate.sourceId)
      ? [sideFor({
          role: 'CORROBORATING', definition: candidate,
          sources: input.sources, revisions: input.sourceRevisions,
        })]
      : []
  ));
  const content = {
    conflictId: definition.conflictId,
    title: definition.title,
    sections: [...definition.sections],
    current,
    incoming,
    corroborating,
    affectedObjectRefs: structuredClone(definition.affectedObjectRefs),
  };
  return {
    ...content,
    hunkSha256: `sha256:${sha256HexSync(canonicalModelingJson(content))}`,
  };
}

function gapBlock(hunk: ConflictHunk, strategy: 'MERGE' | 'DEFER_AS_GAP') {
  const evidenceRefs = [...new Set([
    ...hunk.current.block.evidenceRefs,
    ...hunk.incoming.block.evidenceRefs,
  ])];
  const details = hunk.conflictId === 'gyj-conflict-negative-stock' && strategy === 'MERGE'
    ? {
        stableCode: 'resolution.gap.negative_stock_policy_not_implemented',
        label: '统一禁止负库存制度尚未落地',
        normalized: 'POLICY_NOT_IMPLEMENTED_GAP',
        text: '保留按租户配置控制的实现事实；统一禁止负库存制度尚未落地，继续作为待确认缺口。',
      }
    : hunk.conflictId === 'gyj-conflict-status-nine'
      ? {
          stableCode: 'resolution.gap.status_nine_unconfirmed',
          label: '状态 9 正式业务含义待确认',
          normalized: 'STATUS_NINE_ENUM_MEMBER_DEFERRED',
          text: '保留状态字段，但不发布状态 9 正式枚举成员；审核规则继续作为候选。',
        }
      : {
          stableCode: `resolution.gap.${hunk.conflictId.replace(/^gyj-conflict-/, '').replaceAll('-', '_')}`,
          label: `${hunk.title}待确认`,
          normalized: strategy === 'MERGE' ? 'MERGED_AS_GAP' : 'DEFERRED_AS_GAP',
          text: strategy === 'MERGE'
            ? '保留当前实现事实，并把未采纳来源结论记录为待确认缺口。'
            : '当前差异暂不形成正式结论，保留为待确认缺口。',
        };
  return {
    blockId: `gyj-resolution-block:${hunk.conflictId}:gap`,
    section: 'UNRESOLVED' as const,
    semanticKind: 'GAP' as const,
    stableCode: details.stableCode,
    label: details.label,
    value: { normalized: details.normalized, text: details.text },
    evidenceStatus: 'GAP' as const,
    evidenceRefs,
    affectedObjectRefs: structuredClone(hunk.affectedObjectRefs),
  };
}

function objectDispositions(
  hunk: ConflictHunk,
  strategy: ConflictResolutionStrategy,
): ConflictObjectDisposition[] {
  const byObject = (values: Record<string, ConflictObjectDisposition['disposition']>) => (
    hunk.affectedObjectRefs.map((objectRef) => ({
      objectRef: structuredClone(objectRef),
      disposition: values[objectRef.objectId] ?? 'DEFERRED',
    }))
  );
  if (hunk.conflictId === 'gyj-conflict-debt-schema') {
    if (strategy === 'KEEP_CURRENT') return byObject({
      receivable_debt: 'EXCLUDED', deposit: 'CANDIDATE_ONLY',
    });
    if (strategy === 'ACCEPT_INCOMING') return byObject({
      receivable_debt: 'KEEP', deposit: 'CANDIDATE_ONLY',
    });
    if (strategy === 'MERGE') return byObject({
      receivable_debt: 'CANDIDATE_ONLY', deposit: 'CANDIDATE_ONLY',
    });
    return byObject({ receivable_debt: 'DEFERRED', deposit: 'DEFERRED' });
  }
  if (hunk.conflictId === 'gyj-conflict-negative-stock') {
    if (strategy === 'KEEP_CURRENT') return byObject({ negative_stock: 'KEEP' });
    if (strategy === 'DEFER_AS_GAP') return byObject({ negative_stock: 'DEFERRED' });
    return byObject({ negative_stock: 'CANDIDATE_ONLY' });
  }
  if (hunk.conflictId === 'gyj-conflict-status-nine') {
    if (strategy === 'ACCEPT_INCOMING') return byObject({
      document_status: 'CANDIDATE_ONLY', audit_status: 'CANDIDATE_ONLY',
    });
    if (strategy === 'DEFER_AS_GAP' || strategy === 'MERGE' || strategy === 'KEEP_CURRENT') {
      return byObject({
        document_status: 'KEEP_WITHOUT_ENUM_MEMBER', audit_status: 'CANDIDATE_ONLY',
      });
    }
  }
  return byObject({});
}

function renderHunkMarkdown(hunk: ConflictHunk) {
  const side = (value: ConflictHunkSide) => (
    `- ${value.sourceName}｜${value.block.label}：${sourceDocumentBlockHumanText(value.block.value)}`
  );
  return [
    `# ${hunk.title}`,
    '## Current',
    side(hunk.current),
    '## Incoming',
    side(hunk.incoming),
    ...(hunk.corroborating.length
      ? ['## Corroborating', ...hunk.corroborating.map(side)]
      : []),
  ].join('\n');
}

function renderResultMarkdown(input: {
  hunk: ConflictHunk;
  strategy: ConflictResolutionStrategy;
  blocks: ConflictResolutionPreview['result']['blocks'];
  objectDispositions: ConflictObjectDisposition[];
}) {
  return [
    `# ${input.hunk.title}`,
    '## Result',
    ...input.blocks.map((block) => `- ${block.label}：${sourceDocumentBlockHumanText(block.value)}`),
    '## Strategy',
    `- ${input.strategy}`,
    '## Object dispositions',
    ...input.objectDispositions.map(({ objectRef, disposition }) => (
      `- ${objectRef.kind}:${objectRef.objectId} → ${disposition}`
    )),
  ].join('\n');
}

function provenanceSources(
  hunk: ConflictHunk,
  strategy: ConflictResolutionStrategy,
): ConflictProvenanceSource[] {
  const project = (side: ConflictHunkSide): ConflictProvenanceSource => {
    const usage = side.role === 'CORROBORATING'
      ? 'CORROBORATION' as const
      : side.role === 'CURRENT'
        ? strategy === 'ACCEPT_INCOMING' ? 'PROVENANCE_ONLY' as const : 'CANONICAL' as const
        : strategy === 'ACCEPT_INCOMING' ? 'CANONICAL' as const
          : strategy === 'MERGE' || strategy === 'DEFER_AS_GAP' ? 'GAP_EVIDENCE' as const
            : 'PROVENANCE_ONLY' as const;
    return {
      role: side.role,
      sourceId: side.sourceId,
      sourceName: side.sourceName,
      sourceClass: side.sourceClass,
      authority: side.authority,
      documentId: side.documentId,
      documentRevision: side.documentRevision,
      evidenceRefs: [...side.block.evidenceRefs],
      usage,
    };
  };
  return [hunk.current, hunk.incoming, ...hunk.corroborating].map(project);
}

export function projectConflictResolution(input: {
  hunk: ConflictHunk;
  strategy: ConflictResolutionStrategy;
}): ConflictResolutionPreview {
  const hunk = structuredClone(input.hunk);
  const blocks = input.strategy === 'KEEP_CURRENT'
    ? [structuredClone(hunk.current.block)]
    : input.strategy === 'ACCEPT_INCOMING'
      ? [structuredClone(hunk.incoming.block)]
      : [structuredClone(hunk.current.block), gapBlock(hunk, input.strategy)];
  const assertions = blocks.map((block) => ({
    assertionId: `gyj-resolution-assertion:${hunk.conflictId}:${block.stableCode}`,
    section: block.section,
    statement: `${block.label}：${sourceDocumentBlockHumanText(block.value)}`,
    provenance: 'USER_CONFIRMED' as const,
    evidenceRefs: [...block.evidenceRefs],
  }));
  const dispositions = objectDispositions(hunk, input.strategy);
  const result = { blocks, assertions, objectDispositions: dispositions };
  const structuredPatch = {
    schemaVersion: 1 as const,
    conflictId: hunk.conflictId,
    blockOperations: blocks.map((block) => ({ op: 'UPSERT' as const, block: structuredClone(block) })),
    assertionOperations: assertions.map((assertion) => ({
      op: 'UPSERT' as const, assertion: structuredClone(assertion),
    })),
    objectDispositionOperations: dispositions.map((value) => ({
      op: 'SET' as const, value: structuredClone(value),
    })),
  };
  const markdownDiff = diffSourceDocumentLines(
    renderHunkMarkdown(hunk),
    renderResultMarkdown({ hunk, strategy: input.strategy, blocks, objectDispositions: dispositions }),
  );
  const previewContent = {
    hunk,
    strategy: input.strategy,
    result,
    structuredPatch,
    markdownDiff,
    provenanceSources: provenanceSources(hunk, input.strategy),
  };
  return {
    ...previewContent,
    previewSha256: `sha256:${sha256HexSync(canonicalModelingJson(previewContent))}`,
  };
}

export function previewConflictResolution(input: {
  conflictId: string;
  strategy: ConflictResolutionStrategy;
  sourceRevisions: ConflictSourceRevision[];
  sources: StorySource[];
  definitions: SemanticConflictDefinition[];
}): ConflictResolutionPreview {
  const definition = input.definitions.find((candidate) => candidate.conflictId === input.conflictId);
  if (!definition) throw new Error(`未知管伊佳来源冲突：${input.conflictId}`);
  if (!definition.allowedStrategies.includes(input.strategy)) {
    throw new Error(`当前冲突不允许策略：${input.strategy}`);
  }
  return projectConflictResolution({ hunk: buildConflictHunk(input), strategy: input.strategy });
}
