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

const reviewSupplementMarker = /(?:GAP\s*(\d+)?|资料缺口|待确认)\s*[：:]/giu;

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
      ? `待补充资料 ${segment.ordinal}：${segment.text}`
      : segment.text)
    .join('\n\n')
    .replace(/\bGAP\b/giu, '待补充资料');
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
