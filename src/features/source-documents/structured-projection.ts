import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  canonicalModelingJson,
  renderStandardModelingMarkdown,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import type { StructuredModelingAssertion } from '../modeling-document-bridge/types.ts';
import type { SourceDocumentBlock, SourceDocumentSections } from './types.ts';

export function sourceDocumentBlockHumanText(value: SourceDocumentBlock['value']) {
  if (typeof value === 'object' && value !== null && !Array.isArray(value)
    && typeof value.text === 'string') return value.text;
  return typeof value === 'string' ? value : canonicalModelingJson(value);
}

export function projectSourceDocumentSections(blocks: SourceDocumentBlock[]): SourceDocumentSections {
  return Object.fromEntries(standardSectionOrder.map(({ key, heading }) => {
    const sectionBlocks = blocks.filter((candidate) => candidate.section === key);
    return [key, sectionBlocks.length
      ? sectionBlocks.map((candidate) => (
        `- ${candidate.label}：${sourceDocumentBlockHumanText(candidate.value)}`
      )).join('\n')
      : `本来源没有可直接支持“${heading}”的事实。`];
  })) as SourceDocumentSections;
}

export function projectSourceDocumentAssertions(
  blocks: SourceDocumentBlock[],
): StructuredModelingAssertion[] {
  return blocks.map((candidate) => ({
    assertionId: candidate.blockId,
    section: candidate.section,
    statement: `${candidate.label}：${sourceDocumentBlockHumanText(candidate.value)}`,
    provenance: candidate.evidenceStatus === 'FACT' ? 'OBSERVED' as const : 'INFERRED' as const,
    evidenceRefs: [...candidate.evidenceRefs],
  }));
}

export function assertStructuredSourceDocumentProjection(input: {
  blocks: SourceDocumentBlock[];
  assertions: StructuredModelingAssertion[];
  sections: SourceDocumentSections;
}) {
  const blockIds = input.blocks.map((block) => block.blockId);
  const assertionIds = input.assertions.map((assertion) => assertion.assertionId);
  if (blockIds.some((id) => !id.trim()) || new Set(blockIds).size !== blockIds.length) {
    throw new Error('来源文档blockId不能为空或重复');
  }
  if (assertionIds.some((id) => !id.trim()) || new Set(assertionIds).size !== assertionIds.length) {
    throw new Error('来源文档assertionId不能为空或重复');
  }
  if (input.blocks.length !== input.assertions.length) {
    throw new Error('每个结构化块必须有且仅有一个对应断言');
  }
  for (const block of input.blocks) {
    const assertion = input.assertions.find((candidate) => candidate.assertionId === block.blockId);
    if (!assertion) throw new Error(`结构化块缺少对应断言：${block.blockId}`);
    if (assertion.section !== block.section) throw new Error(`结构化块与断言章节不一致：${block.blockId}`);
    if (canonicalModelingJson(assertion.evidenceRefs) !== canonicalModelingJson(block.evidenceRefs)) {
      throw new Error(`结构化块与断言Evidence不一致：${block.blockId}`);
    }
    if (assertion.statement !== `${block.label}：${sourceDocumentBlockHumanText(block.value)}`) {
      throw new Error(`结构化块与断言正文不一致：${block.blockId}`);
    }
  }
  if (canonicalModelingJson(input.assertions)
    !== canonicalModelingJson(projectSourceDocumentAssertions(input.blocks))) {
    throw new Error('断言与结构化块投影不一致');
  }
  if (canonicalModelingJson(input.sections)
    !== canonicalModelingJson(projectSourceDocumentSections(input.blocks))) {
    throw new Error('章节与结构化块投影不一致');
  }
}

export function renderSourceDocumentMarkdown(input: {
  documentId: string;
  projectId: string;
  documentCode: string;
  sourceSnapshotId: string;
  sourceName: string;
  sections: SourceDocumentSections;
  assertions: StructuredModelingAssertion[];
}) {
  return renderStandardModelingMarkdown({
    artifactId: input.documentId,
    projectId: input.projectId,
    documentCode: input.documentCode,
    title: `${input.sourceName}建模资料`,
    sourceBatch: {
      batchId: `source:${input.sourceSnapshotId}`,
      fingerprint: sha256HexSync(input.sourceSnapshotId),
      snapshotIds: [input.sourceSnapshotId],
    },
    sections: input.sections,
    assertions: input.assertions,
  });
}
