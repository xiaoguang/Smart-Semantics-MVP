import type {
  BusinessDocumentEntry,
  StandardizedDocumentProjection,
} from './standardized-document-projection.ts';

export type StandardizedDocumentReadingEntry = {
  sectionId: string;
  sectionTitle: string;
  sectionPurpose: string;
  /** Saved chapter context is shown once before its individual conclusions. */
  sectionNarrative?: string;
  items: BusinessDocumentEntry[];
};

/**
 * A frozen Markdown source can retain its historical `GAP` vocabulary while
 * the reader uses the approved, itemised Chinese language.  The projection is
 * deliberately one-way: it is never used to derive or write the source
 * Markdown, its revision, or a candidate document.
 */
export type ReviewSupplementSegment =
  | { kind: 'PROSE'; text: string }
  | { kind: 'SUPPLEMENT'; ordinal: number; text: string };

/**
 * A reviewer-facing, source-bound item derived from one explicit marker in
 * chapter nine.  This is intentionally a read-only projection: its identity
 * lets the review surface link back to the exact frozen document claim
 * without changing that Markdown, its revision, or its digest.
 */
export type ReviewSupplementMatter = {
  stableId: string;
  sourceId: string;
  sourceName: string;
  documentId: string;
  documentRevision: number;
  section: 'UNRESOLVED';
  ordinal: number;
  text: string;
  markdownAnchor?: string;
  relatedConflictIds: string[];
};

export type ReviewSupplementSource = Pick<ReviewSupplementMatter,
  'sourceId' | 'sourceName' | 'documentId' | 'documentRevision'>;

const reviewSupplementMarker = /(?:GAP\s*(\d+)?|资料缺口|待确认)\s*[：:]/giu;

/**
 * User-facing review projections use the approved Chinese vocabulary even
 * when frozen prose refers to the historical internal `GAP` category inside
 * a sentence. The source Markdown is never changed; callers use this only
 * after they have selected a reader-facing segment.
 */
export function presentReviewSupplementText(value: string): string {
  return value
    .replace(/([\u3400-\u9fff])\s*\bGAP\b/giu, '$1待补充资料')
    .replace(/\bGAP\b\s*(?=[\u3400-\u9fff])/giu, '待补充资料')
    .replace(/\bGAP\b/giu, '待补充资料');
}

export function projectReviewSupplementSegments(value: string): ReviewSupplementSegment[] {
  const matches = [...value.matchAll(reviewSupplementMarker)];
  if (matches.length === 0) return value ? [{ kind: 'PROSE', text: value }] : [];

  const segments: ReviewSupplementSegment[] = [];
  let cursor = 0;
  let nextOrdinal = 1;

  for (let index = 0; index < matches.length; index += 1) {
    const marker = matches[index]!;
    const markerStart = marker.index ?? 0;
    const markerEnd = markerStart + marker[0].length;
    const prose = value.slice(cursor, markerStart).trim();
    if (prose) segments.push({ kind: 'PROSE', text: prose });

    const following = matches[index + 1];
    const text = value.slice(markerEnd, following?.index ?? value.length).trim();
    const suppliedOrdinal = marker[1] ? Number(marker[1]) : undefined;
    const ordinal = suppliedOrdinal ?? nextOrdinal;
    nextOrdinal = Math.max(nextOrdinal, ordinal + 1);
    if (text) {
      segments.push({ kind: 'SUPPLEMENT', ordinal, text });
    } else {
      // Preserve a malformed marker as readable text rather than silently
      // dropping part of an immutable source description.
      segments.push({ kind: 'SUPPLEMENT', ordinal, text: '尚未提供具体说明。' });
    }
    cursor = following?.index ?? value.length;
  }

  return segments;
}

export function projectReviewSupplementText(value: string): string {
  return projectReviewSupplementSegments(value)
    .map((segment) => segment.kind === 'SUPPLEMENT'
      ? `待补充资料 ${segment.ordinal}：${presentReviewSupplementText(segment.text)}`
      : presentReviewSupplementText(segment.text))
    .join('\n\n');
}

/**
 * Turns only the explicit, marked chapter-nine claims of an already exact
 * source document into individual review matters.  It never scans rendered
 * DOM text or tries to infer extra semantic boundaries from prose.  An
 * unmarked claim remains in the reader exactly as saved, while every marked
 * segment stays independently addressable in the review-items tab.
 */
export function projectReviewSupplementMatters(input: {
  source: ReviewSupplementSource;
  document: StandardizedDocumentProjection;
  /**
   * Callers may supply an explicit source/claim/ordinal association recorded
   * by the review domain.  The projection deliberately does not guess these
   * associations from wording.
   */
  relatedConflictIdsByLocator?: Readonly<Record<string, readonly string[]>>;
}): ReviewSupplementMatter[] {
  const unresolved = input.document.sections.find((section) => section.id === 'UNRESOLVED');
  if (!unresolved) return [];

  const matters: ReviewSupplementMatter[] = [];
  for (const entry of unresolved.entries) {
    const supplements = projectReviewSupplementSegments(entry.statement)
      .filter((segment): segment is Extract<ReviewSupplementSegment, { kind: 'SUPPLEMENT' }> => (
        segment.kind === 'SUPPLEMENT'
      ));
    for (const [segmentIndex, segment] of supplements.entries()) {
      const locator = `${input.source.sourceId}:${entry.claimId}:${segment.ordinal}`;
      matters.push({
        stableId: `supplement:${input.source.sourceId}:${input.source.documentId}:r${input.source.documentRevision}:${entry.claimId}:${segmentIndex + 1}`,
        sourceId: input.source.sourceId,
        sourceName: input.source.sourceName,
        documentId: input.source.documentId,
        documentRevision: input.source.documentRevision,
        section: 'UNRESOLVED',
        ordinal: segment.ordinal,
        text: presentReviewSupplementText(segment.text),
        ...(entry.markdownAnchor ? { markdownAnchor: entry.markdownAnchor } : {}),
        relatedConflictIds: [...(input.relatedConflictIdsByLocator?.[locator] ?? [])],
      });
    }
  }
  return matters;
}

function normalizedSectionHeading(value: string): string {
  return value
    .replace(/^\s*\d+\.\s*/u, '')
    .replace(/[：:]\s*$/u, '')
    .trim();
}

/**
 * Rich source documents may include one short overview conclusion per
 * standard chapter.  The reading view already renders the chapter heading,
 * so repeating an identical title adds visual noise without adding meaning.
 */
export function isRedundantSectionEntryTitle(sectionTitle: string, entryTitle: string): boolean {
  const normalizedSection = normalizedSectionHeading(sectionTitle);
  const normalizedEntry = normalizedSectionHeading(entryTitle);
  return normalizedSection.length > 0 && normalizedSection === normalizedEntry;
}

/**
 * The reading view intentionally consumes the same projection as the source
 * view. It never imports checklist tasks, evidence or finding state.
 */
export function buildStandardizedDocumentReadingEntries(
  document: StandardizedDocumentProjection,
): StandardizedDocumentReadingEntry[] {
  return document.sections
    .filter((section) => document.preserveEmptySections || section.entries.length > 0)
    .map((section) => ({
      sectionId: section.id,
      sectionTitle: section.title,
      sectionPurpose: projectReviewSupplementText(section.purpose),
      ...(section.narrative ? { sectionNarrative: projectReviewSupplementText(section.narrative) } : {}),
      items: section.entries.map((entry) => ({
        ...entry,
        title: projectReviewSupplementText(entry.title),
        statement: projectReviewSupplementText(entry.statement),
        ...(entry.nextStep ? { nextStep: projectReviewSupplementText(entry.nextStep) } : {}),
        ...(entry.fields ? { fields: [...entry.fields] } : {}),
        ...(entry.schemaEvidence ? {
          schemaEvidence: {
            ...entry.schemaEvidence,
            columns: entry.schemaEvidence.columns.map((column) => ({ ...column })),
          },
        } : {}),
        ...(entry.schemaEvidences?.length ? {
          schemaEvidences: entry.schemaEvidences.map((evidence) => ({
            ...evidence,
            columns: evidence.columns.map((column) => ({ ...column })),
          })),
        } : {}),
      })),
    }));
}
