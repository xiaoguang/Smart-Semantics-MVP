import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';

export type ReviewedSourceChange = {
  changeId: string;
  sourceId: string;
  claimId: string;
  markdownAnchor: string;
  field: 'TITLE' | 'STATEMENT';
  before: string;
  after: string;
  section: ModelingDocumentSection;
  /** The immutable reviewed source-document revision that records the change. */
  decisionId: string;
};

export type ReviewedSourceSection = {
  section: ModelingDocumentSection;
  heading: string;
  markdownAnchor?: string;
  /** The complete reviewed body below this source's frozen chapter heading. */
  markdown: string;
};

export type ReviewedSourceDocument = {
  sourceId: string;
  sourceName: string;
  order: number;
  contentSnapshotId: string;
  documentId: string;
  documentRevision: number;
  originalMarkdownSha256: string;
  reviewedMarkdownSha256: string;
  sections: ReviewedSourceSection[];
  /** The complete reviewed source Markdown, retained for integrity and copy projections. */
  markdown: string;
  changes: ReviewedSourceChange[];
};

export type RichReviewedSourceContent = {
  markdown: string;
  markdownSha256: string;
  standardSections: readonly {
    index: number;
    title: string;
    markdownAnchor?: string;
  }[];
  claims: readonly {
    claimId: string;
    markdownAnchor: string;
    title: string;
    statement: string;
    section?: number;
  }[];
};

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
}

function chapterStarts(markdown: string, sections: RichReviewedSourceContent['standardSections']) {
  if (sections.length !== standardSectionOrder.length) {
    throw new Error('完整九章来源文档缺少固定章节声明');
  }
  return sections.map((section, index) => {
    if (section.index !== index + 1 || !section.title.trim()) {
      throw new Error('完整九章来源文档的固定章节顺序无效');
    }
    const heading = `## ${section.index}. ${section.title}`;
    const matches = [...markdown.matchAll(new RegExp(`^${escapeRegExp(heading)}\\s*$`, 'gmu'))];
    if (matches.length !== 1 || matches[0]?.index === undefined) {
      throw new Error(`完整九章来源文档缺少固定章节：${heading}`);
    }
    return { section, heading, index: matches[0].index, end: matches[0].index + matches[0][0].length };
  });
}

function reviewedSectionsFor(content: RichReviewedSourceContent): ReviewedSourceSection[] {
  const starts = chapterStarts(content.markdown, content.standardSections);
  return starts.map((start, index) => {
    const nextStart = starts[index + 1]?.index ?? content.markdown.length;
    const markdown = content.markdown.slice(start.end, nextStart).trim();
    if (!markdown) throw new Error(`完整九章来源文档章节没有正文：${start.heading}`);
    const standard = standardSectionOrder[index];
    if (!standard) throw new Error('完整九章来源文档章节映射无效');
    return {
      section: standard.key,
      heading: standard.heading,
      ...(start.section.markdownAnchor ? { markdownAnchor: start.section.markdownAnchor } : {}),
      markdown,
    };
  });
}

function changesFor(input: {
  sourceId: string;
  documentId: string;
  original: RichReviewedSourceContent;
  reviewed: RichReviewedSourceContent;
}): ReviewedSourceChange[] {
  const originalById = new Map(input.original.claims.map((claim) => [claim.claimId, claim]));
  const reviewedIds = new Set(input.reviewed.claims.map((claim) => claim.claimId));
  if (originalById.size !== reviewedIds.size || [...originalById.keys()].some((claimId) => !reviewedIds.has(claimId))) {
    throw new Error('完整审阅来源文档的 Claim 身份发生变化');
  }
  const changes: ReviewedSourceChange[] = [];
  for (const original of input.original.claims) {
    const reviewed = input.reviewed.claims.find((claim) => claim.claimId === original.claimId);
    if (!reviewed || reviewed.markdownAnchor !== original.markdownAnchor) {
      throw new Error('完整审阅来源文档的 Claim 锚点发生变化');
    }
    const section = standardSectionOrder[(original.section ?? 1) - 1]?.key;
    if (!section) throw new Error('完整审阅来源文档的 Claim 章节无效');
    const base = {
      sourceId: input.sourceId,
      claimId: original.claimId,
      markdownAnchor: original.markdownAnchor,
      decisionId: input.documentId,
      section,
    };
    if (original.title !== reviewed.title) {
      changes.push({
        ...base,
        changeId: `change:${input.sourceId}:${original.claimId}:TITLE`,
        field: 'TITLE', before: original.title, after: reviewed.title,
      });
    }
    if (original.statement !== reviewed.statement) {
      changes.push({
        ...base,
        changeId: `change:${input.sourceId}:${original.claimId}:STATEMENT`,
        field: 'STATEMENT', before: original.statement, after: reviewed.statement,
      });
    }
  }
  return changes;
}

/**
 * Builds a human-document identity from the frozen V6 source document and
 * the exact saved review revision.  It neither reads a source system nor
 * synthesizes prose: every chapter body is cut from the reviewed Markdown.
 */
export function projectReviewedSourceDocument(input: {
  sourceId: string;
  sourceName: string;
  order: number;
  contentSnapshotId: string;
  documentId: string;
  documentRevision: number;
  original: RichReviewedSourceContent;
  reviewed: RichReviewedSourceContent;
}): ReviewedSourceDocument {
  if (!input.sourceId.trim() || !input.sourceName.trim() || !input.contentSnapshotId.trim()
    || !input.documentId.trim() || !Number.isSafeInteger(input.order) || input.order < 1
    || !Number.isSafeInteger(input.documentRevision) || input.documentRevision < 1) {
    throw new Error('完整审阅来源文档身份无效');
  }
  const sections = reviewedSectionsFor(input.reviewed);
  return {
    sourceId: input.sourceId,
    sourceName: input.sourceName,
    order: input.order,
    contentSnapshotId: input.contentSnapshotId,
    documentId: input.documentId,
    documentRevision: input.documentRevision,
    originalMarkdownSha256: input.original.markdownSha256,
    reviewedMarkdownSha256: input.reviewed.markdownSha256,
    sections,
    markdown: input.reviewed.markdown,
    changes: changesFor(input),
  };
}
